# Pruebas externas CU-11 — consulta y cancelación de pedidos

## Objetivo y revisión probada

Validar CU-11 mediante HTTP y navegador contra Spring Boot, Angular/Ionic y MySQL reales, exclusivamente en un entorno desechable. No se modifica el repositorio principal ni se ejecutan suites de otros casos de uso.

Revisión de la aplicación: **`dd72119d55b36684474d1b9d07874ec19cb7ce11`**. La ejecución local utilizó el archivo de fuentes descargado de GitHub Codeload para ese SHA; el workflow utiliza la misma referencia inmutable. No se realizaron operaciones Git. El runner genera `target/cu11/provenance.json` con la revisión esperada y SHA-256 de los controladores/casos de uso y la plantilla de pedidos usados en la construcción.

## Auditoría inicial

Se inspeccionaron `tests/e2e/checkout-order-cancellation.spec.ts`, `tests/helpers/account.ts`, `purchase.ts`, `roles.ts` y `MarketplaceClient`, además de los siguientes archivos del SHA identificado:

- `OrderController`, `OrderReceiptController`, `RequestOrderCancellation`, `CancelOrderUseCase`, `CancelOrderCommand` y `BuyerOrderFilters`.
- `PaymentController`, `MercadoPagoController`, `MercadoPagoClient`, `SimulatedRefundGateway`, `MercadoPagoRefundGuard` y `JdbcBuyerRefundQuery`.
- La plantilla y el componente `mis-pedidos`, las migraciones de pedidos, historial, cancelaciones, reembolsos y procedencia histórica.
- Pruebas internas de propiedad de pedidos, cancelación persistida, filtros, historial y autorización de comprobantes. Estas pruebas internas se leyeron, no se ejecutaron.

Hallazgos:

| Elemento previo | Diagnóstico y tratamiento |
|---|---|
| Login y selección de COMPRADOR | Reutilizables; se conservó el helper compartido sin modificarlo. |
| Selector «Tarjeta» y confirmación de compra antigua | Obsoletos: la UI actual utiliza Mercado Pago. No se usan en CU-11. |
| `/api/payments/process` | Con `payments.mock-enabled=false`, el comprador recibe 410. Se verifica que permanezca deshabilitado. |
| `buyDemoShirt` en `purchase.ts` | También conserva el checkout antiguo; no se modifica porque lo utilizan otras suites. CU-11 ya no depende de él. |
| Detalle, motivo Otro, explicación y confirmación | Se conservó el recorrido válido y sus aserciones, adaptando datos a la fixture. |
| Dirección y mensajes de estado | Se actualizaron al encabezado histórico vigente y se acotó el mensaje de éxito, pues ahora coexiste con otros mensajes funcionales. |
| Recarga y cancelación persistida | Se preservaron y se añadió comprobación de pago y seguimiento del simulador. |

No se encontró un mecanismo de ejecución local configurable para completar el hosted checkout actual sin el proveedor externo. Los simuladores de reembolso existentes no crean una compra de Mercado Pago. Las pruebas internas que habilitan el pago antiguo no justifican habilitarlo en este entorno externo.

**Decisión:** sembrar pedidos sintéticos mediante SQL exclusivamente en `cu11_e2e` y comenzar el E2E desde «Mis pedidos». **Esta prueba no valida el checkout ni representa una compra completada desde la UI.**

## Contratos y alcance funcional

- `GET /api/orders`: lista exclusiva del comprador autenticado, ordenada por creación e identificador descendentes; acepta `from`, `to` y `status`.
- `GET /api/orders/{id}`: detalle propio, productos, cantidades, precios, subtotal, descuento, envío, destinatario, estado, historial y devoluciones. No expone costo interno ni correlaciones.
- `GET/HEAD /api/orders/{id}/receipt`: PDF propio, presentación inline o descarga con `download=true`, sin caché de datos personales.
- `POST /api/orders/{id}/cancellation`: exige sesión, rol activo COMPRADOR y CSRF. Admite motivos de comprador `CHANGED_MIND` y `OTHER`; OTHER requiere explicación y admite hasta 1000 caracteres.
- La cancelación es definitiva desde `CONFIRMED` o `IN_PREPARATION`, sin envío creado. No se inventa una transición a `CANCELLATION_REQUESTED`.
- Pedidos ajenos o inexistentes: 404. Sin autenticación: 401 con CSRF válido para POST. Rol activo VENDEDOR o CSRF ausente: 403. Datos inválidos: 400. Estados incompatibles, envío existente o repetición de cancelación: 409.

## Infraestructura y fixtures

`scripts/cu11-environment.yaml` define un proyecto Compose exclusivo: MySQL 8.4.11, backend y frontend construidos desde el SHA identificado. Solo el frontend expone un puerto en loopback, por defecto 14311. No utiliza `.env`, bases persistentes, volúmenes compartidos, credenciales de pago ni datos CU-07/CU-08.

El runner descarga las fuentes identificadas cuando no se proporciona `CU11_APP_DIR`. No escribe en el repositorio principal. Las verificaciones de SHA-256 antes/después confirmaron que sus fuentes permanecieron intactas.

`scripts/cu11-fixture.py` requiere `CU11_ISOLATED=true`, URL loopback, proyecto con prefijo `cu11-external-` y `SELECT DATABASE()='cu11_e2e'`. Utiliza el usuario limitado a esa base. Las identidades pertenecen a `cu11-*@example.test`; su contraseña pública de prueba es `Cu11ExternalPassword123!`, guardada como BCrypt en MySQL.

Cada fixture crea dirección, producto y pedido propios. Las cuentas HTTP son independientes; el navegador utiliza `cu11-ui@example.test`. Los datos comerciales son reproducibles: dos unidades a 12000, subtotal 24000, descuento 2000, envío 10000 y total 32000. El stock inicial sintético es 8 y se espera 10 después de cancelar una sola vez. La procedencia del pago permanece UNKNOWN: no se presenta la fixture como evidencia externa.

Se mantienen `PAYMENTS_MOCK_ENABLED=false` y `PAYMENTS_RECOVERY_ENABLED=false`; las credenciales de Mercado Pago permanecen vacías. Se reutiliza únicamente el simulador de reembolsos soportado por producción, en modos OK, PENDING y UNAVAILABLE. El cambio de modo recrea solo el backend desechable. No se realizan transacciones reales.

## Pruebas HTTP

`BuyerOrdersIntegrationTest` contiene 36 ejecuciones con el simulador OK:

- Propiedad, orden de resultados y rechazo de suplantación mediante parámetros.
- Detalle comercial, envío, historial y ausencia de costos internos.
- 404 para detalle, comprobante y cancelación ajenos o inexistentes; ausencia de efectos en la base.
- 401 sin sesión y 403 con rol activo incorrecto o sin CSRF.
- Filtros inclusivos por día, estado, resultado vacío y cinco entradas inválidas con códigos concretos del backend.
- Cancelación permitida en dos estados; rechazo en los otros nueve estados del enum.
- Rechazo cuando existe envío, con el estado de envío válido CREATED.
- Ocho variantes de datos inválidos de cancelación, incluidos motivo faltante, motivo de vendedor, detalles vacíos/largos y campo de identidad no permitido.
- Repetición y dos solicitudes concurrentes: un único efecto, una cancelación, un reembolso y una reposición de stock.
- PDF real, cabeceras, firma binaria, descarga y HEAD.
- Conservación del 410 del pago antiguo.
- Protección de pedidos con referencia MP: no reciben un reembolso simulado falsamente completado.

`BuyerOrderRefundIntegrationTest` se ejecuta una vez con PENDING y otra con UNAVAILABLE. Comprueba el resultado HTTP, estado financiero persistido, seguimiento y ausencia de un segundo reembolso al repetir la cancelación.

Todas las operaciones de negocio se realizan por HTTP. SQL se limita a crear fixtures, comprobar aislamiento y observar los registros reales de MySQL.

## Pruebas E2E

El archivo existente `tests/e2e/checkout-order-cancellation.spec.ts` se conserva y actualiza con cuatro recorridos reales, sin interceptar ni simular respuestas HTTP:

1. Login, detalle del pedido fixture, producto, cantidades, total y dirección; motivo obligatorio, explicación vacía/espacios, confirmación, cancelación, seguimiento simulado y persistencia tras recarga.
2. Pedido fixture con referencia MP: cancelación definitiva, pago REFUND_PENDING, devolución FAILED, ausencia de confirmación simulada y persistencia tras recarga.
3. Pedido entregado: explicación de estado no cancelable y ausencia del botón antes/después de recargar.
4. Filtros de fecha/estado, visualización de PDF generado por el backend y descarga del comprobante.

Fuera del entorno expresamente aislado estas pruebas se omiten para no sembrar datos en instalaciones ajenas. En la ejecución dedicada no hubo omisiones.

## Resultados reales — 12 de noviembre de 2026

Comando de ejecución final: `bash scripts/cu11-run.sh`.

| Grupo | Ejecutadas | Aprobadas | Fallidas/errores | Omitidas |
|---|---:|---:|---:|---:|
| HTTP: simulador OK | 36 | 36 | 0 | 0 |
| HTTP: simulador PENDING | 1 | 1 | 0 | 0 |
| HTTP: simulador UNAVAILABLE | 1 | 1 | 0 | 0 |
| **HTTP total** | **38** | **38** | **0** | **0** |
| **Playwright CU-11** | **4** | **4** | **0** | **0** |

Las dos ejecuciones del test de reembolso corresponden a condiciones ambientales distintas; no se presentan como métodos Java diferentes. No hubo tests E2E flaky ni reintentos.

Evidencia funcional observada:

| Condición | Pedido | Pago | Resultado de devolución |
|---|---|---|---|
| Fixture legacy, simulador OK | CANCELLED | REFUNDED | POST: COMPLETED; seguimiento: SIMULATED, sin confirmación externa |
| Simulador PENDING | CANCELLED | REFUND_PENDING | PENDING persistido |
| Simulador UNAVAILABLE | CANCELLED | REFUND_PENDING | FAILED persistido |
| Fixture MP, protección vigente | CANCELLED | REFUND_PENDING | FAILED; no se ejecuta reembolso simulado |
| Segunda cancelación | Permanece CANCELLED | Sin duplicar efectos | 409; un único registro de devolución/cancelación |
| Dos cancelaciones concurrentes | Un único cambio | Un único reembolso | Una respuesta 200 y otra 409 |

La cancelación no se revierte por un resultado pendiente o fallido del reembolso. Ninguno de estos resultados demuestra acreditación real de dinero.

En la primera ejecución HTTP hubo 35 aprobadas y una fallida: la fixture de envío utilizaba REQUESTED en lugar de CREATED, único valor del enum `ShipmentStatus`. Se corrigió la fixture, sin aceptar el 500 ni debilitar la expectativa 409. La ejecución final de las 36 pruebas pasó. No se identificó un defecto de producción que impidiera el alcance probado.

Validaciones adicionales correctas:

- Compilación Java mediante Maven, con selectores explícitos de los dos tests CU-11. Los builds de la aplicación usan `-DskipTests`: no ejecutan sus suites internas.
- TypeScript: `tsc --noEmit` para el E2E CU-11 y su helper, sin modificar dependencias ni emitir archivos.
- Parseo YAML de seis workflows y Compose; Actionlint **1.7.12**, sin errores.
- `bash -n` y compilación sintáctica de los scripts Python sin generar temporales versionables.
- `docker compose config --quiet`.
- Limpieza: `down --volumes --remove-orphans`, código 0, sin contenedores ni redes del proyecto después de finalizar.
- Comparación de fuentes: repositorio principal intacto; workflows CU-07/CU-08, cliente HTTP y helpers compartidos intactos; en los tres workflows compartidos solo cambió la exclusión de push.

## Evidencias generadas y CI

Los siguientes archivos se generan en `target/cu11/`, excluido del control de versiones:

- `results.json`: conteos efectivos HTTP y E2E.
- `provenance.json`: revisión esperada y hashes de fuentes.
- `http-OK/`, `http-PENDING/`, `http-UNAVAILABLE/`: XML/TXT de Surefire, comando y código de salida por modo.
- `e2e.json` y `e2e.log`: resultados Playwright.
- `cleanup.json` y `cleanup.log`: resultado de limpieza.
- `startup.log` y `restart-*.log`: diagnóstico de infraestructura.
- `ci-validation.json`: comprobación local de filtros de push.

El workflow `.github/workflows/cu11-external.yml` conserva resultados y diagnóstico como artefactos de GitHub Actions durante 14 días. No se incluyen logs voluminosos ni fuentes descargadas en los entregables versionables.

Para push a `feature/cu11-integration-tests`, la evaluación local de los seis workflows seleccionó **únicamente `cu11-external.yml`**. Los tres compartidos excluyen también esta rama, manteniendo sus jobs, main, otras ramas y eventos previos. Los dedicados CU-07/CU-08 conservan sus filtros originales. Los eventos pull_request existentes no se restringen: pueden mantener su comportamiento anterior cuando la usuaria abra un PR.

El runner mantiene el error si falla cualquier grupo HTTP, E2E, preparación o limpieza; ejecuta E2E aunque falle el primer grupo HTTP. No utiliza continue-on-error. No se activó ningún workflow remoto durante esta validación.

## Ejecución exclusiva de CU-11

Requisitos: Java 21+, Node compatible con Playwright, dependencias npm instaladas, Chromium instalado, Python 3, curl y Docker Compose.

Desde la raíz de Transformers-Integration-Tests:

```bash
bash scripts/cu11-run.sh
```

Esto descarga el archivo de fuentes del SHA fijo cuando es necesario, levanta un proyecto exclusivo, ejecuta únicamente CU-11 y limpia sus recursos. Si ya se dispone de esas fuentes:

```bash
CU11_APP_DIR=/ruta/a/fuentes/dd72119d55b36684474d1b9d07874ec19cb7ce11 bash scripts/cu11-run.sh
```

Se puede cambiar el puerto con `CU11_PORT`. No apuntar `CU11_APP_DIR` a otra revisión si se quiere reproducir esta evidencia. El workflow fija directamente el SHA.

Los comandos internos seleccionan únicamente:

```bash
./mvnw -Dtest=BuyerOrdersIntegrationTest verify
npx playwright test tests/e2e/checkout-order-cancellation.spec.ts
# Después de cambiar solo el modo del backend desechable:
./mvnw -Dtest=BuyerOrderRefundIntegrationTest verify
```

No ejecutar esos comandos contra una instalación compartida; usar el runner, que prepara sus variables, base y modo correspondientes.

## Archivos de la entrega

Nuevos:

- `.github/workflows/cu11-external.yml`.
- `scripts/cu11-environment.yaml`, `cu11-compose.sh`, `cu11-run.sh`, `cu11-fixture.py`, `cu11-results.py`.
- `src/test/java/com/transformersas/integration/orders/Cu11Fixture.java`, `BuyerOrdersIntegrationTest.java`, `BuyerOrderRefundIntegrationTest.java`.
- `tests/helpers/cu11-orders.ts`.
- `docs/pruebas-cu11.md`.

Modificados:

- `tests/e2e/checkout-order-cancellation.spec.ts`: únicamente el recorrido CU-11 y sus escenarios adicionales.
- `.github/workflows/cu02-cu03-e2e.yml`, `cu02-cu03-integration.yml`, `integration-tests.yml`: únicamente filtro de push para excluir feature/cu11-integration-tests.

## Limitaciones

- No se validó la creación de una compra desde el checkout actual ni una transacción o devolución monetaria real de Mercado Pago.
- Las fixtures MP comprueban la protección y el seguimiento local; no simulan confirmaciones externas, webhooks financieros ni conciliación automática.
- No se ejecutaron las regresiones generales, las suites internas de la aplicación ni pruebas de CU-02, CU-03, CU-07, CU-08 u otros casos.
- El helper compartido de compra antigua permanece sin cambios por estar fuera del alcance. No se afirma que las suites ajenas que todavía lo utilicen estén actualizadas.
- No se mide cobertura JaCoCo del proceso backend desde estas pruebas externas.

No se realizaron operaciones Git, cambios en el repositorio principal, pagos reales ni activaciones remotas de workflows.
