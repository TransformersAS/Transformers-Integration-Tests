# Pruebas E2E del Marketplace

## Objetivo

Playwright valida el Marketplace completo desde la perspectiva del usuario,
interactuando con frontend y backend reales en los flujos descritos aquí.
Esta cobertura convive con las pruebas Java/Maven (JUnit y REST Assured);
no implica que todas las funcionalidades del Marketplace estén probadas.

## Tecnología

- Playwright Test con TypeScript y navegador Chromium.
- Aplicación levantada externamente: Playwright no inicia el Marketplace.
- URL del frontend configurable mediante `FRONTEND_BASE_URL`.
- Configuración en [playwright.config.ts](../playwright.config.ts), pruebas en
  `tests/e2e/` y login/selección de COMPRADOR compartidos en
  [tests/helpers/account.ts](../tests/helpers/account.ts).

Las interacciones funcionales se realizan desde la UI. Se observan respuestas
HTTP emitidas por la aplicación sin llamar endpoints para saltarse pasos.
Las esperas usan estados visibles y respuestas, sin sleeps fijos.

## Pruebas actuales

### Smoke

[smoke.spec.ts](../tests/e2e/smoke.spec.ts) comprueba:

- Carga del frontend con respuesta HTTP exitosa y disponibilidad básica.
- Título y contenido identificable de Mercado Integral.
- Comunicación de la aplicación con `/api/auth/me`, admitiendo 200 o 401:
  el 401 de una sesión de visitante no se considera un fallo fatal.
- Ausencia de excepciones JavaScript sin manejar y de los mensajes de error
  visibles comprobados por el test.

### Compra y cancelación

[checkout-order-cancellation.spec.ts](../tests/e2e/checkout-order-cancellation.spec.ts)
automatiza login, rol COMPRADOR, búsqueda del producto en el catálogo,
incorporación de una unidad al carrito y checkout/pago mediante controles visibles.
Usa el producto `Camiseta demo local`, una dirección de prueba, envío estándar
y la opción de pago `Tarjeta` del entorno local; no acredita pagos de producción.

Comprueba la confirmación de compra y creación del pedido. Lee su identificador
desde la pantalla de confirmación para abrir ese pedido en “Mis pedidos”, sin
asumir que sea el primero ni depender de pedidos anteriores. En el detalle valida
“Confirmado” (`CONFIRMED`), producto, cantidad 1, subtotal, total, envío,
referencia de dirección y transacción.

Solicita y confirma la cancelación; verifica “Cancelación solicitada”
(`CANCELLATION_REQUESTED`), la ausencia de una segunda solicitud y la persistencia
del estado al volver a abrir el detalle. No comprueba una cancelación definitiva.
El carrito debe estar vacío inicialmente; el test no borra datos previos.

### Seguridad de cuenta

[account-security.spec.ts](../tests/e2e/account-security.spec.ts) comprueba:

- Login, correo autenticado y logout con retorno al estado anónimo.
- Disponibilidad de COMPRADOR y VENDEDOR y cambio COMPRADOR → VENDEDOR → COMPRADOR.
- Dos sesiones simultáneas en dos BrowserContext aislados y consulta de
  “Sesiones activas”, con al menos dos entradas.
- Identificación de la sesión principal y de la segunda por el identificador
  leído desde la UI de esta última; no se usa un ID fijo ni una sesión ajena arbitraria.
- Revocación de esa segunda sesión desde la principal y confirmación de la acción.
- Tras recargar, `/api/auth/me` devuelve 401 para la revocada y la UI muestra
  acceso no autenticado; la principal conserva respuesta 200 y rol COMPRADOR.
- Después del logout principal, respuesta 401 y controles de inicio de sesión.

Playwright cierra el contexto principal mediante su fixture; el secundario se
cierra en `finally`, incluso si falla una aserción. No se cambia ni recupera
la contraseña.

## Configuración

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `FRONTEND_BASE_URL` | `http://localhost:4300` | Dirección del frontend levantado externamente. |
| `E2E_EMAIL` | `demo@marketplace.local` | Cuenta de pruebas para login desde la UI. |
| `E2E_PASSWORD` | `MarketplaceDemo123!` | Contraseña inicial de la cuenta del perfil local. |

Son credenciales exclusivas del entorno local/E2E, no credenciales de producción.
El perfil `local` del backend debe provisionar la cuenta, los roles COMPRADOR y
VENDEDOR y el producto `Camiseta demo local`; no se depende de datos creados
manualmente. Las variables se leen del entorno del proceso; la configuración
actual no carga automáticamente un archivo `.env`.

Si la contraseña de la cuenta local ya cambió, `E2E_PASSWORD` debe coincidir con
su valor actual. La última validación local utilizó `MarketplaceDemo456!`,
sobrescribiendo el valor inicial del helper.

## Ejecución local

Los pasos básicos `npm ci`, `npx playwright install chromium` y
`npm run test:e2e` ya están en el [README: E2E con Playwright](../README.md#e2e-con-playwright).
Se requiere Node.js/npm y el Marketplace disponible externamente.
Para reproducir la última ejecución con la contraseña local vigente:

```bash
E2E_PASSWORD='MarketplaceDemo456!' npm run test:e2e
```

**Estado actual registrado: 3 pruebas Playwright aprobadas** (smoke, compra y
cancelación, y seguridad de cuenta) en la última ejecución local validada.
Este documento registra ese resultado; su creación no supone una nueva ejecución.

## Evidencia

- `playwright-report/`: reporte HTML de los resultados; entrada `index.html`.
- `test-results/`: resultados auxiliares y artefactos de diagnóstico de las pruebas.
- Screenshot en fallo (`only-on-failure`).
- Video conservado en fallo (`retain-on-failure`).
- Trace conservado en fallo (`retain-on-failure`) para examinar acciones,
  estados de página y tráfico observado.

Estos directorios son generados, están ignorados por Git y no se versionan.
Los artefactos automáticos corresponden al contexto gestionado por Playwright;
el segundo BrowserContext creado manualmente en seguridad de cuenta no tiene
captura propia de video o trace configurada.

## Integración continua

GitHub Actions ejecuta automáticamente las pruebas mediante el workflow
[integration-tests.yml](../.github/workflows/integration-tests.yml).
Transformers-AS está fijado a un commit concreto para reproducibilidad.

En cada ejecución, el workflow levanta desde cero MySQL, backend y frontend
mediante Docker Compose. Un override temporal activa el perfil `local` del
backend únicamente en el entorno de CI, sin modificar el Compose de
Transformers-AS. La base de datos limpia provisiona automáticamente la cuenta
`demo@marketplace.local`, los roles COMPRADOR/VENDEDOR y el producto
`Camiseta demo local`.

Se ejecuta `./mvnw verify` y después los 3 Playwright E2E mediante
`npm run test:e2e`.

**Estado validado: 3/3 pruebas Playwright aprobadas en GitHub Actions.**

Se guardan `target/surefire-reports/`, `playwright-report/` y `test-results/`
como artifacts. Al finalizar, la limpieza configurada con `if: always()` elimina
los contenedores y volúmenes del entorno de pruebas.
