# Pruebas de integración y E2E de CU-23, CU-24 y CU-25 (Pacho)

Este documento describe las pruebas que Pacho implementó en este repositorio para los casos de uso que trabajó:
CU-23 «Preparar y despachar pedidos recibidos», CU-24 «Procesar el seguimiento logístico de pedidos» y CU-25 «Procesar
el seguimiento logístico de devoluciones». El alcance corresponde a las cuatro clases Java y los dos recorridos
Playwright que agregó, y no representa la totalidad de los casos de uso del equipo.

## 1. Objetivo

Verificar sobre el sistema desplegado —backend y frontend reales, MySQL real, proveedor logístico simulado— el contrato
externo de estos tres casos de uso:

- que el vendedor pueda preparar y despachar un pedido pagado, y que al despacharlo el marketplace le pida su envío al
  servicio logístico;
- que las novedades de transporte solo entren por el webhook firmado del proveedor, y que el marketplace las registre
  una sola vez, en orden, sin retroceder el estado del pedido ni de la devolución;
- que comprador y vendedor puedan consultar el seguimiento y pedir una nueva consulta, pero nunca fijar un estado;
- que la entrega del retorno al vendedor sea la señal con la que CU-19 abre la inspección de la devolución.

Las pruebas Java comprueban el contrato HTTP con JUnit 5 y REST Assured. Los recorridos Playwright comprueban lo mismo
desde la interfaz, con Chromium.

## 2. Arquitectura de pruebas

```text
Transformers-AS (sistema bajo prueba)
  MySQL ← backend Spring Boot ← frontend Angular/Ionic servido por Nginx

Transformers-Integration-Tests (pruebas)
  REST Assured  → /api → backend real → MySQL
  Playwright    → Chromium → frontend real → /api → backend real → MySQL
  Ambas, haciendo de proveedor logístico → POST /api/logistics/webhooks/... (firmado con HMAC-SHA256)
```

Hay una particularidad de estos casos de uso: **las novedades de transporte no se pueden provocar desde la interfaz**.
El caso de uso consiste precisamente en que las informa el proveedor logístico y el marketplace las procesa; no existe
ningún control en la aplicación que mueva un envío a «Entregado». Por eso las pruebas hacen de proveedor y firman las
novedades igual que él: calculan el HMAC-SHA256 del cuerpo exacto y lo envían en `X-Logistics-Signature`. No es un mock
que sustituya una respuesta del backend: es una llamada real a su webhook, que es una interfaz pública del sistema.

El resto se ejerce por donde lo haría una persona: los endpoints de sesión, carrito, pago y vendedor en las pruebas
Java, y los botones de la aplicación en los recorridos Playwright.

## 3. Configuración

| Variable | Valor por defecto | Uso real |
| --- | --- | --- |
| `BACKEND_BASE_URL` | `http://localhost:8080` | Backend que atacan las pruebas Java. |
| `FRONTEND_BASE_URL` | `http://localhost:4300` | Frontend que abre Playwright. |
| `E2E_EMAIL` | `demo@marketplace.local` | Cuenta del perfil local. Tiene los roles COMPRADOR y VENDEDOR, y es dueña de la tienda 1. |
| `E2E_PASSWORD` | `MarketplaceDemo123!` | Contraseña de esa cuenta. |
| `LOGISTICS_WEBHOOK_SECRET` | sin valor | Obligatoria. El mismo secreto con el que arrancó el backend. Sin él el webhook responde 401 `WEBHOOK_NOT_CONFIGURED` y estas pruebas no tienen nada que probar; por eso fallan explicando qué falta en vez de dar un error confuso. |

Las credenciales y el secreto pertenecen exclusivamente al entorno local o efímero de pruebas, nunca a producción.

Que la misma cuenta tenga los dos roles es lo que permite un recorrido completo: compra como COMPRADORA, despacha como
VENDEDORA de su tienda y vuelve a consultar como COMPRADORA. Las pruebas Java abren **dos sesiones independientes** de
esa cuenta, una con cada rol activo, para no tener que alternarlo. Los recorridos Playwright, en cambio, lo alternan con
los mismos controles que usaría una persona, porque es una sola ventana.

## 4. Configuración compartida

### Java (`src/test/java/.../support/`)

| Clase | Qué aporta |
| --- | --- |
| `MarketplaceClient` | Una sesión del Marketplace: inicia sesión por `POST /api/auth/login`, conserva la cookie `SESSION`, pide el token CSRF y lo manda en cada escritura, y deja activo el rol pedido. Un POST de acción sin datos envía `{}`, porque el backend espera un objeto JSON. Tiene una variante de GET con `X-Store-Id` para comprobar qué ve un vendedor de una tienda ajena. |
| `MarketplaceScenario` | Base de las pruebas: abre la sesión de comprador, la de vendedor y el proveedor logístico una vez por clase. Ofrece `confirmedOrder()` (compra), `dispatchedOrder()` (compra, preparación y despacho) y `report(...)` (el proveedor informa una lista de tipos, todos aplicados). |
| `Purchases` | La compra completa con las llamadas que hace el frontend: vaciar el carrito, agregar el producto, crear la dirección, reservar el stock, calcular el total y pagar con `CARD`. |
| `Returns` | Una devolución real creada con los endpoints de CU-19: solicitarla, que el vendedor la revise y apruebe, y elegir el método de retorno. Ese último paso es el que crea el retorno en el servicio logístico y registra su seguimiento. |
| `LogisticsProvider` | Hace de proveedor: firma y envía las novedades, y permite enviar un cuerpo con una firma cualquiera para probar el rechazo. Cada novedad lleva un `occurredAt` posterior al de la anterior. |
| `BackendConfiguration` | Resuelve las variables de entorno, con los mismos valores por defecto que Playwright. |

### Playwright (`tests/helpers/`)

| Archivo | Qué aporta |
| --- | --- |
| `account.ts` | Login por interfaz y selección del rol COMPRADOR (de Sofía; se reutiliza sin cambios). |
| `roles.ts` | Cambio del rol activo a COMPRADOR o VENDEDOR y apertura de «Mis pedidos» y «Pedidos recibidos». |
| `purchase.ts` | La compra desde la interfaz: catálogo, carrito, dirección, envío y pago. Devuelve el número de pedido que muestra la confirmación. |
| `fulfilment.ts` | Preparación y despacho desde «Pedidos recibidos». |
| `logistics.ts` | El proveedor logístico: firma con `node:crypto` y envía las novedades por la misma ruta `/api` que usa la aplicación. |
| `returns.ts` | Prepara una devolución aprobada con el retorno en curso, usando la API de CU-19. |

## 5. Pruebas Java

Son 25 pruebas en cuatro clases, además de la del harness que ya existía. Todas usan la API pública; ninguna toca la
base de datos ni clases del backend.

### 5.1 CU-23 — `orders/SellerFulfillmentIntegrationTest` (5 pruebas)

1. **Preparar y despachar.** Compra un pedido y comprueba en el detalle del vendedor que llega `CONFIRMED`, con el pago
   aprobado, sin envío, con la dirección de entrega copiada al pedido y el inventario consistente. Luego lo prepara
   (`IN_PREPARATION`) y lo marca listo (`READY_FOR_DISPATCH`), y verifica que el servicio logístico creó el envío con su
   referencia `SIM-order-N` y su guía. Vuelve a leer el detalle para comprobar que el historial conserva los tres
   estados, con el vendedor como actor, y que el comprador ve el mismo estado desde su panel.
2. **Reintento del envío.** En un pedido que ya tiene envío, `POST /shipment` responde 200 —el 201 se reserva para el
   envío creado en ese momento— y devuelve la misma referencia y la misma guía, sin crear otro.
3. **Transición inválida.** Un pedido ya despachado no puede volver a preparación: 409 `ORDER_INVALID_TRANSITION`.
4. **Límites de la tienda.** Pedir una tienda ajena por `X-Store-Id` es 403 `STORE_NOT_AUTHORIZED` (lo comprueba CU-18),
   y un pedido que no existe en la propia tienda es 404 `ORDER_NOT_FOUND`.
5. **Rol activo.** La misma cuenta, pero con el rol activo COMPRADOR, no entra a los endpoints del vendedor:
   403 `SELLER_ROLE_REQUIRED`. El backend no se fía del rol que diga la interfaz.

### 5.2 CU-24 — `logistics/OrderTrackingIntegrationTest` (9 pruebas)

1. **Recorrido completo.** Antes de que el proveedor informe nada, el seguimiento existe pero está vacío. Después,
   `PICKED_UP`, `IN_TRANSIT` y `DELIVERED` mueven el estado del pedido uno por uno; la línea de tiempo queda en orden
   cronológico, con todos los eventos aplicados y con origen `WEBHOOK`; la entrega cierra el seguimiento, y aparecen la
   fecha de entrega y la constancia que trajo esa novedad. La línea de tiempo del vendedor es la misma que la del
   comprador.
2. **Idempotencia.** Reenviar la misma novedad (el mismo `eventId`, como haría un proveedor que no está seguro de que su
   envío llegó) responde `DUPLICATE` y no la registra dos veces.
3. **Novedad atrasada.** Una recogida que llega después de la entrega responde `OUT_OF_ORDER`: el pedido sigue
   entregado, y el evento se conserva marcado al final de la línea de tiempo, para trazabilidad.
4. **Novedad de entrega.** `DELIVERY_EXCEPTION`, `DELIVERY_ATTEMPT_FAILED` y `NEXT_ATTEMPT_SCHEDULED` quedan registrados
   y el seguimiento sigue abierto hasta la entrega final; los seis eventos quedan en orden.
5. **Actualizar seguimiento.** La primera consulta llega al proveedor; la inmediatamente siguiente se rechaza por
   frecuencia (`THROTTLED`) sin volver a llamarlo. El vendedor tiene su propio endpoint.
6. **Nadie fija el estado.** `POST .../tracking/refresh` con un estado en el cuerpo es 400; con el cuerpo vacío o `{}`
   es 200. El pedido no se mueve.
7. **Pedido ajeno.** El seguimiento de un pedido que no es del comprador, o que no existe en la tienda, es 404
   `ORDER_NOT_FOUND`; pedirlo a nombre de una tienda ajena es 403 `STORE_NOT_AUTHORIZED`.
8. **Pedido sin envío.** Responde el seguimiento vacío, sin guía y sin eventos, no un error.
9. **Retorno al vendedor.** `RETURNED_TO_SELLER` deja el pedido en ese estado y cierra el seguimiento.

### 5.3 Webhook — `logistics/LogisticsWebhookIntegrationTest` (5 pruebas)

Es la única puerta del marketplace abierta sin sesión, así que se prueba desde fuera que nadie pueda inventar una
novedad:

1. **Firma.** Sin firma, con una firma de ceros, con una firma calculada con otro secreto, y con el cuerpo alterado
   después de firmarlo (cambiando `PICKED_UP` por `DELIVERED`): las cuatro son 401. El pedido no cambia y su línea de
   tiempo sigue vacía.
2. **Envío inexistente.** Una novedad bien firmada de un envío que no existe es 404 `SHIPMENT_NOT_FOUND`, no un error
   del servidor.
3. **Guía que no corresponde.** 409 `SHIPMENT_MISMATCH`, y el pedido no se mueve.
4. **Tipo desconocido.** Un tipo que el marketplace no conoce responde 200 `IGNORED`: devolver un error solo haría que
   el proveedor reintentara para siempre, y el proveedor puede agregar tipos nuevos sin romper la integración.
5. **Cuerpo inválido.** Un cuerpo que no es JSON, o con una fecha que no es ISO-8601, es 400 `INVALID_WEBHOOK_PAYLOAD`.

### 5.4 CU-25 — `logistics/ReturnTrackingIntegrationTest` (6 pruebas)

Cada prueba parte de una devolución real: se compra un pedido, se despacha, el proveedor lo entrega, el comprador
solicita la devolución de su línea, el vendedor la aprueba y el comprador elige el método de retorno. Ese último paso es
el que crea el retorno en logística.

1. **Recorrido completo.** El retorno recién registrado está en recogida pendiente, con cero recogidas fallidas de un
   máximo de tres. `PICKED_UP` lo pasa a recogido con su fecha, `IN_TRANSIT` a en retorno y `DELIVERED_TO_SELLER` a
   entregado al vendedor, con fecha y cerrando el seguimiento. Además comprueba que **la devolución de CU-19 pasó a
   `IN_INSPECTION`**: es la integración entre los dos casos de uso. La tienda ve la misma línea de tiempo.
2. **Tres recogidas fallidas.** La primera deja el retorno en recogida fallida, sin detenerlo. La tercera lo detiene:
   `pickupStopped`, sin posibilidad de pedir otra recogida y sin volver a consultar al proveedor. Una novedad posterior
   ya no cambia nada: se anota como `OUT_OF_ORDER`.
3. **Repetidas y atrasadas.** El mismo `eventId` responde `DUPLICATE`; una recogida que llega cuando el retorno ya está
   en curso responde `OUT_OF_ORDER` y queda anotada sin mover el estado.
4. **Actualizar seguimiento.** Igual que en los pedidos: la segunda consulta seguida es `THROTTLED`.
5. **Retorno ajeno.** 404 `RETURN_NOT_FOUND`, tanto para el comprador como para la tienda.
6. **Retorno no registrado.** Una novedad de un retorno que nadie registró es 404 `RETURN_SHIPMENT_NOT_FOUND`.

## 6. Recorridos E2E

### 6.1 `tests/e2e/order-tracking.spec.ts` — CU-23 y CU-24 desde la interfaz

1. **Comprar como COMPRADOR.** Login, catálogo, carrito, dirección, envío estándar y pago con tarjeta. Se queda con el
   número de pedido que muestra la confirmación; no se asume que sea el primero ni el número 1.
2. **Despachar como VENDEDOR.** Cambia el rol activo, abre «Pedidos recibidos», abre el pedido por su número, comprueba
   «Confirmado» en el resumen, pulsa *Iniciar preparación* y *Listo para despacho*, y comprueba que la tarjeta de envío
   muestra la guía `TRK-N` con estado `CREATED`.
3. **El vendedor ve la recogida y el tránsito.** El proveedor informa `PICKED_UP` e `IN_TRANSIT`; *Actualizar
   seguimiento* muestra «Recogido» y «En camino», y la guía junto al estado.
4. **El comprador ve la misma línea de tiempo.** Cierra el panel del vendedor, vuelve a COMPRADOR, abre el detalle de su
   pedido y encuentra los mismos dos hitos y el botón de actualizar.
5. **Una novedad atrasada se marca.** Tras una novedad de entrega, el proveedor reenvía la recogida: la respuesta es
   `OUT_OF_ORDER` y la interfaz muestra «Recibida fuera de orden: no cambió el estado», con dos entradas «Recogido» en
   la línea de tiempo y el estado sin retroceder.
6. **La entrega cierra el seguimiento.** `DELIVERED` aplica y su reenvío responde `DUPLICATE`. La interfaz muestra
   «Entregado el …», la confirmación de entrega, y el aviso de que el seguimiento finalizó; el botón de actualizar
   desaparece.
7. **El filtro del vendedor.** El pedido entregado aparece en «Entregados» de «Pedidos recibidos».

### 6.2 `tests/e2e/return-tracking.spec.ts` — CU-25 desde la interfaz

1. **Llegar a una devolución con retorno en curso.** Compra, despacha e informa el envío hasta la entrega por la
   interfaz y el webhook. La devolución se prepara con la **API de CU-19** —solicitud, revisión, aprobación y elección
   del método—, porque esa interfaz pertenece a CU-19 y a sus pruebas; aquí solo hace falta llegar a un retorno en
   curso. El rol activo se alterna con los controles de la aplicación, para que la sesión y la interfaz no se
   desincronicen.
2. **El comprador consulta por número.** En «Mis pedidos», el recuadro *Seguimiento de devolución* (que existe porque
   CU-19 todavía no ofrece un listado con el seguimiento) acepta el número y muestra «Recogida pendiente» con la guía
   `TRK-RN`.
3. **Recogida y retorno.** `PICKED_UP` y luego `IN_TRANSIT`: la interfaz muestra «Recogido» y «En retorno».
4. **Entrega al vendedor.** `DELIVERED_TO_SELLER` deja «Entregado al vendedor», la fecha de entrega y el aviso de
   seguimiento finalizado, y quita el botón de actualizar. Se comprueba además que la devolución de CU-19 quedó en
   inspección.
5. **El vendedor consulta el mismo retorno.** Desde «Pedidos recibidos», con el mismo número, ve el mismo estado.

## 7. Estrategias para estabilidad

- **Roles y nombres accesibles.** Botones, diálogos, encabezados, regiones y campos se localizan por su rol y su nombre
  accesible. Las secciones del seguimiento tienen `aria-label`, así que se usan como región y todas las aserciones se
  acotan dentro de ella.
- **Ámbito de los selectores.** El estado vigente de un pedido se lee en la región «Resumen del pedido», porque el
  historial repite los mismos textos más abajo. El producto se busca solo dentro del catálogo, porque las
  recomendaciones (CU-02) muestran el mismo producto en otra tarjeta.
- **Componentes de Ionic.** `ion-select` se expone como botón, no como combobox, y su etiqueta se superpone al control;
  se activa por teclado, igual que ya hacía el selector de rol. `ion-title` no expone el rol de encabezado, así que el
  título del panel del vendedor se localiza por su elemento.
- **Sin esperas fijas.** No hay `sleep` en ninguna prueba: se esperan estados visibles, botones ausentes y respuestas
  reales. En la interfaz se pulsa *Actualizar seguimiento* en lugar de esperar la relectura automática de 10 s.
- **Orden de la línea de tiempo.** El seguimiento se ordena por el momento en que ocurrió cada novedad, así que el
  proveedor de prueba garantiza que cada una sea posterior a la anterior y a las que registró el propio marketplace (por
  ejemplo el alta del retorno). Con fechas retrasadas, la línea de tiempo salía en otro orden.
- **Identificadores dinámicos.** El pedido y la devolución se leen de lo que devuelve el sistema; no hay números fijos.
  Los `eventId` son únicos salvo cuando la prueba quiere precisamente repetir uno.
- **Un solo trabajador.** `workers: 1` en Playwright y clases secuenciales en Maven, porque el backend comparte un único
  carrito y una única cuenta demo entre compradores: dos compras a la vez se pisarían. No es estilo, es un límite del
  sistema bajo prueba.
- **Sin limpieza de datos.** Las pruebas no borran nada: cada una crea su propio pedido y su propia devolución. Los
  datos quedan en la base, que en CI es efímera.

## 8. Ejecución local

Con el Marketplace levantado (backend con su secreto de webhook y frontend sirviendo `/api`):

```bash
# Pruebas Java
BACKEND_BASE_URL=http://localhost:8080 \
LOGISTICS_WEBHOOK_SECRET='el-mismo-del-backend' \
./mvnw verify

# Recorridos E2E
npm ci
npx playwright install chromium
FRONTEND_BASE_URL=http://localhost:4300 \
LOGISTICS_WEBHOOK_SECRET='el-mismo-del-backend' \
npm run test:e2e
```

`npm run test:e2e:headed` los ejecuta con el navegador visible.

Dos avisos sobre el entorno:

- **El secreto del webhook.** El backend tiene que arrancar con `LOGISTICS_WEBHOOK_SECRET` y las pruebas tienen que
  recibir el mismo valor. Con un secreto vacío el webhook queda cerrado a propósito.
- **El origen del frontend.** Si el frontend no se sirve en `http://localhost:4300`, hay que agregar su origen a
  `APP_CORS_ALLOWED_ORIGINS` del backend; si no, el navegador recibe 403 `Invalid CORS request` al iniciar sesión,
  porque el proxy reenvía el `Origin` real.

## 9. Evidencia

| Ubicación o artefacto | Qué contiene |
| --- | --- |
| `target/surefire-reports/` | Resultado de cada prueba Java, con su salida. |
| `playwright-report/` | Reporte HTML de los recorridos E2E (`index.html`). No se abre automáticamente. |
| `test-results/` | Salida auxiliar de Playwright y artefactos de diagnóstico. |
| Captura, video y traza en fallo | `only-on-failure` y `retain-on-failure`: permiten ver el estado visual, la secuencia y las acciones y peticiones de una prueba que falló. |
| Artifacts del workflow | El CI publica los cuatro anteriores más los logs de Docker, con 7 días de retención. |

Ninguno de esos directorios se versiona: están ignorados por Git. Las ejecuciones que pasan no conservan capturas,
videos ni trazas.

**Resultado de la validación local del bloque actual:** 26/26 pruebas Java y 5/5 recorridos Playwright en verde, contra
el backend en el commit `87cff7ef` de `main` con MySQL y el proveedor logístico simulado. Esta ejecución fue local; el
resultado en GitHub Actions se ve en la pestaña *Actions* del repositorio, que es la que publica los artifacts.

## 10. Integración continua

El workflow [integration-tests.yml](../.github/workflows/integration-tests.yml) ya existía; para estas pruebas se le
cambiaron dos cosas:

1. **El commit del sistema bajo prueba.** Estaba fijado a `710ce468`, anterior a CU-23, CU-24, CU-25 y CU-19, donde
   estos endpoints y estas pantallas no existen. Ahora apunta a `87cff7ef`, el `main` que los incluye. Sigue fijado a un
   commit y no a una rama móvil: al agregar pruebas de un caso de uso nuevo hay que moverlo a un commit que ya lo
   incluya.
2. **El secreto del webhook.** El job define `LOGISTICS_WEBHOOK_SECRET` para su ejecución efímera y lo agrega al `.env`
   con el que arranca el Compose, de modo que el backend y las pruebas comparten el mismo valor. Sin esto el webhook
   queda cerrado y las pruebas de CU-24 y CU-25 no podrían informar ninguna novedad.

## 11. Alcance

Estas pruebas cubren el despacho de un pedido, el seguimiento de su envío y el seguimiento del retorno de una
devolución, con sus flujos alternos de novedad de entrega, novedad atrasada, novedad repetida, recogidas fallidas,
consulta limitada por frecuencia y firma inválida.

No cubren: la consulta periódica automática al proveedor (el barrido cada 30 s, que en estas pruebas se provoca con
*Actualizar seguimiento*); la caída del proveedor (`LOGISTICS_SIMULATED_MODE=UNAVAILABLE`, que exige arrancar el backend
de otra manera); la concurrencia de dos novedades simultáneas sobre el mismo envío; ni las pantallas y decisiones de
CU-19, que solo se usan como punto de partida. Tampoco son una medición de cobertura del código del backend: eso lo hace
la suite del repositorio principal.
