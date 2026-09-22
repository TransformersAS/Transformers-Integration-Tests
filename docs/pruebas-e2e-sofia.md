# Pruebas E2E implementadas por Sofía

Este documento describe las pruebas E2E implementadas por Sofía para los casos de uso y flujos que trabajó. El alcance corresponde a los tres tests existentes en este repositorio y no representa la totalidad de los casos de uso del equipo.

## 1. Objetivo

Validar el sistema completo desde la perspectiva del usuario mediante Playwright Test y Chromium, interactuando con frontend y backend reales. Las pruebas comprueban comportamientos observables: contenido renderizado, autenticación, compra, consulta y cancelación definitiva de pedidos, roles y sesiones.

Las pruebas E2E conviven con Java/Maven, JUnit y REST Assured. Cada archivo descrito contiene un test: el bloque actual tiene tres pruebas E2E.

## 2. Arquitectura de pruebas

Los componentes se distribuyen entre dos repositorios:

```text
Transformers-AS (sistema bajo prueba)
  MySQL ← backend Spring Boot ← frontend Angular/Ionic servido por Nginx

Transformers-Integration-Tests (pruebas)
  Playwright → navegador Chromium → frontend real → /api → backend real → MySQL
```

El navegador interactúa con controles de la aplicación. El frontend envía sus solicitudes al backend real; Nginx sirve el frontend y dirige `/api` al backend en el despliegue Docker. No se usan mocks ni interceptaciones que sustituyan respuestas para estos flujos.

Playwright no levanta automáticamente la aplicación: localmente debe estar disponible antes de ejecutar los tests. En CI la levanta el workflow con Docker Compose. Observar respuestas con `waitForResponse` no equivale a invocar endpoints directamente: las peticiones observadas proceden de la aplicación.

La configuración reside en [playwright.config.ts](../playwright.config.ts). El helper [account.ts](../tests/helpers/account.ts) comparte el login por UI y la selección de COMPRADOR.

## 3. Configuración

| Variable | Default local | Uso real |
| --- | --- | --- |
| `FRONTEND_BASE_URL` | `http://localhost:4300` | `baseURL` usada por la navegación a `/`. Un valor vacío o con solo espacios vuelve al default. |
| `E2E_EMAIL` | `demo@marketplace.local` | Correo introducido en el formulario de login. |
| `E2E_PASSWORD` | `MarketplaceDemo123!` | Contraseña introducida en el formulario de login. |

Las credenciales pertenecen exclusivamente al entorno local/E2E, nunca a producción. El perfil `local` provisiona la cuenta demo, los roles COMPRADOR/VENDEDOR y el producto `Camiseta demo local`. Los tests no crean estos datos mediante endpoints ni borran la base de datos.

Las variables se leen del entorno del proceso; no hay carga automática de `.env` en Playwright. Si la contraseña local cambió, debe pasarse su valor vigente mediante `E2E_PASSWORD`. La validación local previa utilizó `MarketplaceDemo456!`; CI usa el valor inicial `MarketplaceDemo123!` porque parte de una BD limpia.

La configuración usa Chromium con el dispositivo `Desktop Chrome`, timeout general de 30 segundos, 10 segundos para aserciones y acciones y 15 segundos para navegación. Los tests de compra y seguridad amplían su timeout total a 90 segundos.

## 4. Prueba 1 — Smoke test

Archivo: [tests/e2e/smoke.spec.ts](../tests/e2e/smoke.spec.ts).

1. Registra los eventos `pageerror` para recoger excepciones JavaScript sin manejar.
2. Abre `/` mediante `page.goto('/')`: con la configuración local, `http://localhost:4300/`.
3. Observa en paralelo la respuesta a `GET /api/auth/me`, comprobando que su origen coincide con el de la página.
4. Exige una respuesta de navegación no nula y HTTP 2xx. Comprueba que la respuesta de sesión termina sin error de transferencia.
5. Acepta HTTP 200 o 401 para `/api/auth/me`. Un visitante sin sesión puede recibir 401: indica ausencia de autenticación y no un fallo fatal de carga.
6. Comprueba el título exacto `Mercado Integral`, el enlace visible `Marketplace Integral, inicio`, el encabezado `Objetos con buena historia.` y el campo de búsqueda `Buscar productos`.
7. Exige que no aparezcan los textos de error fatal definidos por el test: `error fatal`, `fatal error`, `internal server error`, `error interno del servidor`, `algo salió mal`, `something went wrong` o `application error`.
8. Comprueba que no existen alertas cuyo texto coincida con `error`, `fallo`/`falló` o `no se pudo`, y que la lista de excepciones JavaScript esté vacía.

Demuestra disponibilidad básica del frontend, renderizado identificable y comunicación real con la consulta de sesión del backend. No valida búsqueda de productos, todos los endpoints `/api`, ni todos los posibles mensajes de error de la aplicación.

## 5. Prueba 2 — Compra y cancelación de pedido

Archivo: [tests/e2e/checkout-order-cancellation.spec.ts](../tests/e2e/checkout-order-cancellation.spec.ts).

**Precondición:** cuenta del perfil local con correo verificado, producto demo disponible y carrito vacío. El perfil `local` crea la cuenta sin verificar; CI marca únicamente esa cuenta demo como verificada en su base efímera antes de ejecutar las pruebas. Esto prepara el usuario, sin simular login ni respuestas de negocio. Antes de agregar productos, el test abre el carrito, observa `GET /api/cart`, exige respuesta exitosa y terminada, comprueba el encabezado de carrito vacío y la ausencia de botones `Eliminar`. No vacía un carrito previo ni consume deliberadamente su contenido.

El flujo real es el siguiente:

1. **Login:** el helper abre `/`, pulsa `Ver cuenta`, completa `Correo` y `Contraseña` en `Acceso a tu cuenta` y pulsa `Iniciar sesión`. Observa el POST real a `/api/auth/login` y exige respuesta exitosa.
2. **Autenticación y COMPRADOR:** espera el diálogo `Seguridad de tu cuenta`, el botón `Cerrar sesión` y `Hola, <correo E2E>`. Si el rol no es COMPRADOR, abre el selector con teclado, elige COMPRADOR y confirma con `OK`. Comprueba `Rol activo: COMPRADOR`, cierra el panel, recarga el catálogo y espera `Mis pedidos`.
3. **Catálogo:** localiza un `article` que contiene el encabezado exacto `Camiseta demo local`. No escribe en el buscador ni prueba filtros.
4. **Agregado:** pulsa `Agregar` en ese artículo y observa la respuesta exitosa al POST `/api/cart/items` emitido por la UI.
5. **Carrito:** vuelve a abrirlo y comprueba el encabezado del producto, el texto exacto `1` y un único botón `Eliminar`. Pulsa `Continuar compra`.
6. **Checkout:** en `Finalizar compra`, completa `Escribe tu dirección de entrega` con `Calle 123 # 45-67, pruebas E2E`, selecciona envío `Estándar` y pulsa `Calcular total`. Espera `Resumen de compra`.
7. **Pago:** selecciona `Tarjeta` en el combobox y pulsa `Confirmar compra`. No introduce datos de una tarjeta real ni prueba otras opciones de pago.
8. **Creación del pedido:** espera `¡Compra confirmada!` y `Tu pedido fue creado correctamente.`.
9. **Captura del pedido:** lee el texto `#<número>` generado, admitiendo espacios alrededor. Conserva el número, el texto de transacción y el total pagado; exige un total mayor que cero.
10. **Mis pedidos:** cierra el checkout y abre `Mis pedidos`.
11. **Identificación:** utiliza el número recién leído en el nombre accesible `Ver detalle del pedido <número>`. No asume que sea el pedido #1 ni el primero de la lista.
12. **Detalle:** abre ese botón y comprueba el encabezado `Pedido #<número>`.
13. **Producto:** localiza un único elemento de lista con `Camiseta demo local` y exige que el nombre exacto esté visible.
14. **Cantidad:** comprueba el texto que empieza por `Cantidad: 1 · Precio unitario:`.
15. **Datos principales:** exige subtotal visible, la misma transacción capturada, una referencia de dirección registrada y `Envío: STANDARD`. Compara el total del detalle con el pagado: elimina caracteres no numéricos y contempla los dos decimales que muestra el detalle. No vuelve a calcular impuestos ni descuentos.
16. **Estado confirmado:** comprueba el texto exacto `Confirmado` en el detalle, correspondiente a `CONFIRMED`. En el código esta aserción se realiza inmediatamente después de abrir el detalle, antes de revisar sus productos.
17. **Cancelación directa:** pulsa `Cancelar pedido` desde `CONFIRMED`; exige que `Confirmar cancelación` esté deshabilitado antes de elegir motivo.
18. **Motivo Otro:** selecciona `Otro` en `Motivo de cancelación`. Comprueba que aparece la explicación obligatoria y que no puede confirmar con texto vacío ni solo espacios. Completa una explicación y verifica que se habilita la confirmación definitiva del pedido identificado.
19. **Cancelación y reembolso:** pulsa `Confirmar cancelación`, observa el POST real `/api/orders/<id>/cancellation` y exige HTTP 200, `CANCELLED`, `REFUNDED` y reembolso `COMPLETED`. La UI debe mostrar `Cancelado` y `Pedido cancelado. Reembolso completado`, sin botones para volver a cancelar.
20. **Persistencia:** recarga la página, abre `Mis pedidos` y consulta otra vez el mismo pedido. Exige que el GET real del detalle devuelva `CANCELLED` y que la UI siga mostrando `Cancelado`, sin botón de cancelación.

El requisito funcional comprobado es que un comprador autenticado puede comprar, consultar y cancelar definitivamente un pedido propio desde `CONFIRMED`, con motivo `OTHER` y explicación, conservando el estado al recargar. No hay aprobación del vendedor. El test observa el resultado del mecanismo de reembolso existente del backend (pasarela simulada del perfil de pruebas); no simula respuestas de la aplicación ni realiza una transacción bancaria de producción. La cancelación desde `IN_PREPARATION`, el aislamiento ante pedidos ajenos/inexistentes, la persistencia del motivo y la reposición del inventario se cubren en backend; este E2E mantiene un único recorrido de compra/cancelación.

## 6. Prueba 3 — Seguridad de cuenta

Archivo: [tests/e2e/account-security.spec.ts](../tests/e2e/account-security.spec.ts).

1. **Login:** reutiliza `loginAsBuyer` con las variables E2E.
2. **Cuenta autenticada:** el helper valida respuesta de login, diálogo autenticado, correo y botón de cierre de sesión. El test vuelve a abrir `Ver cuenta` y comprueba que `Roles disponibles:` contiene COMPRADOR y VENDEDOR.
3. **COMPRADOR → VENDEDOR:** abre `Cambiar rol activo` con la tecla Space, selecciona el radio `VENDEDOR` y pulsa `OK`.
4. **Rol activo:** espera a que `OK` desaparezca y comprueba visualmente `Rol activo: VENDEDOR`.
5. **VENDEDOR → COMPRADOR:** reutiliza `selectBuyerRole`, que selecciona COMPRADOR cuando corresponde y verifica su texto de rol activo.
6. **Segundo contexto:** crea `browser.newContext({ baseURL })` y una página dentro de él, conservando abierta la página principal.
7. **Segunda sesión:** inicia sesión con la misma cuenta en la segunda página y abre `Ver cuenta` → `Sesiones activas`.
8. **Dos sesiones:** desde la principal abre también `Sesiones activas` y espera con `expect.poll` al menos dos elementos de lista. No exige exactamente dos, porque pueden existir sesiones anteriores.
9. **Identificación dinámica:** en la segunda página identifica la única fila `Esta es tu sesión actual` y lee su identificador desde la primera definición de esa fila. En la principal identifica su propia fila actual, lee su ID y exige que sea distinto. Encuentra la fila del ID secundario y verifica que dice `Otra sesión`. No usa IDs hardcodeados ni el orden o fecha para elegir una sesión ajena.
10. **Revocación:** pulsa `Revocar sesión` únicamente en esa fila secundaria. En el grupo `Confirmar revocación` comprueba el ID leído y pulsa `Sí, revocar`.
11. **Confirmación visible:** espera el elemento con rol `status` cuyo texto es exactamente `Sesión revocada.`; exige que desaparezca la fila secundaria y siga visible el ID de la principal.
12. **Principal válida:** al recargar la principal, observa que la petición real `/api/auth/me` devuelve 200. Al abrir la cuenta, exige `Cerrar sesión` y `Rol activo: COMPRADOR`.
13. **Revocada inválida:** al recargar la secundaria, observa HTTP 401 en `/api/auth/me`; exige el diálogo `Acceso a tu cuenta` con `Iniciar sesión`, sin diálogo autenticado ni botón `Mis pedidos`. En el código esta verificación se ejecuta antes de recargar y verificar la principal del punto anterior.
14. **Logout principal:** pulsa `Cerrar sesión`, espera que desaparezca el diálogo autenticado y recarga la página.
15. **Estado anónimo:** observa 401 para `/api/auth/me`, abre `Ver cuenta` y exige `Iniciar sesión`; ya no deben existir `Mis pedidos` ni `Cerrar sesión`.

Los BrowserContext representan sesiones independientes reales, con almacenamiento y cookies de navegador aislados. No se simula la segunda sesión ni se reutilizan sus cookies. La principal pertenece a la fixture de Playwright, que gestiona su cierre incluso ante fallos; el contexto secundario se cierra explícitamente con `finally`.

Este test valida cambio de rol, consulta y revocación selectiva de sesiones, conservación de la sesión que revoca y logout. No cambia ni recupera contraseñas, ni demuestra todas las reglas de autorización asociadas al rol VENDEDOR.

## 7. Estrategias para estabilidad

- **`getByRole`:** botones, diálogos, encabezados, radios, combobox, artículos, listas, definiciones y mensajes de estado se localizan mediante sus roles y nombres accesibles.
- **`getByText`:** correo, roles, cantidades, importes, estados e identificadores visibles se comprueban por texto. Las expresiones de número de pedido y transacción admiten espacios del renderizado.
- **`getByPlaceholder`:** se utiliza para la dirección de entrega. Los campos de login y la explicación de cancelación se encuentran con `getByRole('textbox', ...)`; el motivo se selecciona con `getByRole('combobox', ...)`.
- **Ámbito de los selectores:** las búsquedas se restringen al diálogo, panel, artículo o fila correspondiente. Para `Total pagado` se navega al padre con `locator('..')` y se lee su `strong`; existe esa dependencia estructural concreta, pero no se usan clases CSS generadas.
- **Sin sleeps fijos en tests:** se esperan estados visibles, botones ocultos y respuestas reales. El polling de sesiones espera una condición observable. El bucle de health del workflow sí contiene pausas acotadas, fuera de las pruebas Playwright.
- **Transiciones Ionic:** el selector de rol se activa con teclado porque una etiqueta puede superponerse al botón; se espera que desaparezca `OK` antes de continuar.
- **Identificadores dinámicos:** pedido leído tras la compra y sesión secundaria leída desde su propia UI; no hay valores fijos para esos IDs.
- **Contextos independientes y cleanup:** el segundo contexto se cierra en `finally` y el principal mediante la fixture. Este cierre no equivale a borrar registros de la BD ni a deshacer pedidos creados.

## 8. Ejecución local

Se requiere Node.js/npm y el Marketplace levantado externamente, con cuenta E2E, roles, producto correo de la cuenta verificado y carrito vacío para la prueba de compra. Desde este repositorio:

```bash
npm ci
npx playwright install chromium
npm run test:e2e
```

Ejemplo explícito con datos iniciales del perfil local:

```bash
FRONTEND_BASE_URL=http://localhost:4300 \
E2E_EMAIL=demo@marketplace.local \
E2E_PASSWORD='MarketplaceDemo123!' \
npm run test:e2e
```

Si se utiliza la cuenta local cuya contraseña fue cambiada en la validación previa:

```bash
E2E_PASSWORD='MarketplaceDemo456!' npm run test:e2e
```

Debe usarse el valor que tenga la cuenta del entorno. El test no restablece la contraseña. `npm run test:e2e:headed` ejecuta la suite con el navegador visible. Los comandos básicos también están en el [README](../README.md#e2e-con-playwright).

Para ejecutar únicamente CU-11 contra el SUT levantado:

```bash
FRONTEND_BASE_URL=http://localhost:4300 npm run test:e2e -- tests/e2e/checkout-order-cancellation.spec.ts
```

## 9. Evidencia

| Ubicación o artefacto | Comportamiento y utilidad |
| --- | --- |
| `playwright-report/` | Reporte HTML, con entrada `index.html`, para consultar resultados y evidencias de la ejecución. No se abre automáticamente. |
| `test-results/` | Directorio de salida para resultados auxiliares y artefactos de diagnóstico. |
| Screenshot en fallo | `only-on-failure`: permite inspeccionar el estado visual al fallar una prueba. |
| Video en fallo | `retain-on-failure`: conserva la secuencia visual para entender cómo se alcanzó el fallo. |
| Trace en fallo | `retain-on-failure`: permite revisar acciones, snapshots y tráfico registrado durante la prueba. |

Ambos directorios son generados, están ignorados por Git y no se versionan. Las ejecuciones exitosas no conservan screenshots, videos ni traces de fallo. La captura automática corresponde al contexto gestionado por Playwright: el segundo contexto creado manualmente no configura su propia grabación de video o trace.

## 10. Integración continua

El workflow real [integration-tests.yml](../.github/workflows/integration-tests.yml) ejecuta automáticamente las pruebas en GitHub Actions ante push a cualquier rama y pull requests.

1. Usa Ubuntu 24.04, permisos `contents: read` y timeout de 45 minutos para builds y descargas.
2. Hace checkout de este repositorio y de Transformers-AS en `backend-source`, fijado al commit `8c4d58129bd80f2bd10b31c49087f9d69c1fe90a`. No depende de una rama móvil del sistema bajo prueba.
3. Configura Java 21 y prepara el `.env` de Compose con contraseñas exclusivas del entorno efímero.
4. Genera `$RUNNER_TEMP/compose.e2e.yaml`, que añade `SPRING_PROFILES_ACTIVE: local` al backend. El override pertenece al runner y no modifica el Compose de Transformers-AS.
5. Configura Node 22 con cache npm, ejecuta `npm ci` e instala Chromium y dependencias del sistema con `npx playwright install --with-deps chromium`.
6. Levanta MySQL, backend y frontend con el Compose principal y el override: `up -d --build --wait --wait-timeout 300`. El proyecto usa el ID de ejecución y el número de intento para aislar sus volúmenes.
7. En la máquina limpia, el perfil local provisiona `demo@marketplace.local` / `MarketplaceDemo123!`, roles COMPRADOR/VENDEDOR y `Camiseta demo local` en la BD nueva.
8. Además del health de Compose, espera backend `/actuator/health/readiness` con estado UP y respuesta exitosa del frontend en `http://localhost:4300/`, mediante un bucle limitado a 30 intentos.
9. Marca `email_verified_at` únicamente para `demo@marketplace.local` en la base efímera y ejecuta `./mvnw verify` con `BACKEND_BASE_URL=http://localhost:8080`, conservando las pruebas Java y los reportes Surefire.
10. Ejecuta después `npm run test:e2e` para la suite E2E, con frontend local y las credenciales iniciales del perfil. Puede ejecutarlos aunque Maven falle, siempre que readiness e instalación de Chromium hayan terminado correctamente y el job no esté cancelado.
11. Captura logs Docker ante fallo. Con `if: always()` publica `target/surefire-reports/`, `playwright-report/`, `test-results/` y los logs disponibles como artifacts, con retención de 7 días.
12. Con `if: always()` ejecuta `down --volumes --remove-orphans`, usando el mismo `.env`, Compose principal, override y proyecto del arranque. La limpieza comprueba antes que existan los archivos de configuración.

**Validación actual de CU-11 (2026-09-22):** `npm run test:e2e -- tests/e2e/checkout-order-cancellation.spec.ts`: **1/1 aprobado en Chromium (4,3 s)**, contra frontend, backend y MySQL reales del commit `8c4d58129bd80f2bd10b31c49087f9d69c1fe90a`. Ejecución local en `http://127.0.0.1:14311`, usando el Compose existente con proyecto/base aislados y la cuenta demo verificada como en CI. No se ejecutaron otros E2E ni GitHub Actions en esta validación.

**Evidencia histórica:** las tres pruebas de Sofía fueron aprobadas previamente en GitHub Actions. Ese resultado no valida por sí solo el cambio actual de CU-11 ni el nuevo ref del SUT.

## 11. Alcance

Estas pruebas cubren específicamente los flujos implementados por Sofía: disponibilidad inicial, compra con consulta y cancelación definitiva de pedido, y seguridad de cuenta con roles y sesiones. No pretenden representar todas las pruebas funcionales de todos los casos de uso del equipo.

La cobertura descrita se limita a las aserciones de los tres tests reales. No incluye uso funcional del buscador, filtros de catálogo, otras cantidades o productos, cupones, pagos rechazados o pendientes, reembolsos bancarios de producción, cambio/recuperación de contraseña ni las operaciones de negocio del vendedor. Tampoco constituye una medición de cobertura de código o una validación de pagos de producción.
