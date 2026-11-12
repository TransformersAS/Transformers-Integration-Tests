# CU-07 — pruebas externas, 11 de noviembre de 2026

Este informe registra la implementación inicial. La [revisión final posterior](revision-final-cu07.md)
actualiza el diagnóstico, las correcciones y el estado del pipeline.

Se añadieron pruebas HTTP JUnit/REST Assured y E2E Playwright contra backend,
frontend, MySQL y Mailpit reales. No se simulan endpoints con `page.route()`.
Los datos son ficticios y únicos. La limpieza elimina exclusivamente el proyecto
Compose desechable; no se usa `.env` ni una base persistente.

## Implementación y trazabilidad

La aplicación se construyó desde `../Transformers-AS`, cuya referencia local
leída del archivo `.git/HEAD` es `feature/cu07-buyer-registration`. No se ejecutó
ningún comando Git. El commit publicado identificado es
`f77dade0c6168861779b297430bc5e42d343b1ae`. Se descargaron mediante HTTP los diez
archivos de contrato/implementación enumerados en [provenance.json](cu07/provenance.json)
y sus bytes coincidieron con los archivos locales. Esta comparación comprueba
esos archivos, no afirma que todo el árbol local esté limpio. Las imágenes se
construyeron desde las fuentes locales, no desde una imagen antigua de `main`.
Además, las pruebas comprobaron el contrato real de `/api/buyers/terms`, registro
y habilitación. El workflow nuevo hace checkout del SHA anterior.

Inspección: README, POM, package.json, configuración Playwright, suites Java y
E2E, helpers y los tres workflows previos; en la aplicación, CU07.md,
controlador/DTO/use cases de comprador, VerifyAccountEmail, SecurityConfiguration,
Compose y script E2E CU07, pruebas E2E reales, controladores de direcciones,
vendedor y sesiones y migraciones V31/V45.

El cliente compartido ya maneja cookie SESSION, login por formulario, selección
de rol y renovación CSRF. Se añadieron una sesión visitante con CSRF y login
observable para comprobar rechazos. Se conserva el comportamiento previo.
La API no expone perfiles personales: su persistencia, consentimientos, hashes
sin cambios y ausencia de tienda se comprueban mediante lecturas SQL autorizadas
en la base desechable. SQL solo prepara estados excepcionales (token vencido,
vendedor sin COMPRADOR); las operaciones bajo prueba pasan por HTTP. SMTP se
interrumpe y restaura con `finally`. Las suites usan correos UUID. Las nuevas
pruebas exigen activación explícita y URLs loopback; el proyecto debe tener
prefijo `cu07-external-` y Java comprueba `SELECT DATABASE() = cu08_e2e`.

## Matriz de cobertura

| Escenarios solicitados | Evidencia automatizada | Resultado CU07 |
|---|---|---|
| 1–5: 201, perfil, solo COMPRADOR, sin VENDEDOR ni tienda | `registrationPersistsOnlyBuyerAndProfile` + SQL de perfil/roles/tienda/consentimientos | Aprobado |
| 6, 10, 11: duplicado, repetición y dos altas simultáneas | `duplicateAndConcurrentRequestsHaveOneIdentity`: barrera de salida, 201 + 409 y una identidad | Aprobado |
| 7–9: vacíos, formatos, contraseña, confirmación, términos y privacidad | 8 invocaciones parametrizadas + `missingAndUnknownFieldsAndUtf8PasswordLimitAreRejected` | Aprobado |
| 12–13: pendiente rechazada y confirmación habilita login | `registrationPersistsOnlyBuyerAndProfile`, 403 y luego 204 | Aprobado |
| 14–16: inválido, usado, reemplazado y vencido | `verificationRejectsInvalidReplacedUsedAndExpiredTokens` | Aprobado |
| 17: fallo SMTP, persistencia y recuperación con reenvío | `smtpFailureIsRecoverable`, 201 con indicador de fallo; luego 202/204/login | Aprobado |
| 18–22: habilitar, misma identidad, roles/rol activo conservados, idempotencia | `enableUsesOwnIdentityPreservesRolesAndIsIdempotent`, comparación de perfil/hash/verificación y conteo de roles | Aprobado |
| 23–24: sin sesión y suplantación de otra identidad | `csrfAndAnonymousEnableAreEnforced` + rechazo 400 de `accountId` ajeno | Aprobado |
| 25: aislamiento de compradores | `buyerIsolationAndSellerRegistrationRegression`: direcciones propias invisibles a otra cuenta | Aprobado |
| CU08: login, verificación, selección explícita de rol, logout | Pruebas HTTP anteriores y E2E | Aprobado en CU07 |
| CU12: vendedor pendiente, verificación y concesión de VENDEDOR | `buyerIsolationAndSellerRegistrationRegression` | Aprobado |
| CU11: consulta de pedidos tras alta/selección de COMPRADOR | HTTP y E2E exitoso | Aprobado; compra/cancelación completa falla en la regresión existente |
| E2E 1: alta → SMTP → verificación → login → COMPRADOR | `buyer-registration.spec.ts`, primer caso | Aprobado |
| E2E 2: incompleto, contraseñas, consentimientos, datos conservados | mismo archivo, caso de validaciones | Aprobado |
| E2E 3: vendedor añade COMPRADOR y selecciona rol | mismo archivo, segundo caso; fixture de vendedor único | Aprobado |
| E2E 4: pendiente no accede, habilitación anónima y respuesta sin datos sensibles | mismo archivo, caso de seguridad | Aprobado |

## Resultados efectivos

| Ejecución | Aprobadas | Fallidas | Omitidas | Evidencia |
|---|---:|---:|---:|---|
| Compilación del harness | correcta | 0 | — | [compile.log](cu07/compile.log) |
| CU07 HTTP dedicado | 16 | 0 | 0 | [JUnit](cu07/http-reports/com.transformersas.integration.buyers.BuyerRegistrationIntegrationTest.txt) |
| CU07 navegador dedicado | 4 | 0 | 0 | [ejecución final](cu07/final-run.log) |
| Regresión HTTP final | 25 | 32 | 9 | [log](cu07/regression-http.log), [resumen por suite](cu07/regression-http-summary.json) |
| Regresión E2E completa | 2 | 10 | 5 | [log](cu07/regression-e2e.log) |
| YAML de workflows y scripts shell | correcta | 0 | — | [ci-validation.log](cu07/ci-validation.log) |
| GitHub Actions remoto | no ejecutado | — | — | No se activó remotamente |

La omisión HTTP corresponde a la clase CU07 desactivada intencionalmente en la
regresión general (JUnit contabiliza métodos/contenedores omitidos, no las ocho
invocaciones parametrizadas). Las 5 omisiones E2E son los cuatro casos optativos
CU07 y la demo optativa existente. La suite dedicada sí ejecuta sus casos.
El HTML y las trazas de regresión se preservan localmente bajo
`target/cu07-regression-e2e/`; esa ruta está ignorada por Git. Los logs y reportes
JUnit de `docs/cu07/` quedan como evidencia durable en el repositorio.

La repetición final desde cero terminó con 16 HTTP y 4 E2E aprobadas, sin fallos
ni omisiones. Su limpieza automática eliminó los cuatro contenedores y la red;
se comprobó que no quedaban contenedores de ninguno de los dos proyectos de
esta tarea. La evidencia está en [final-run.log](cu07/final-run.log).
Los conteos de ejecuciones diferentes se solapan y no deben sumarse como casos
únicos. Una suite omitida deliberadamente en un comando no acredita cobertura.

## Fallos y clasificación

No se detectó un defecto confirmado de producción en CU07 con las pruebas nuevas.
La regresión general **no está aprobada**:

1. **32 fallos HTTP, contratos antiguos de pruebas**: el helper compartido
   `Purchases.buyDemoShirt` llama a `POST /api/payments/process` esperando 200/201.
   El controlador real actual devuelve 410 y exige checkout Mercado Pago. Esto
   impide crear los pedidos necesarios para despacho, devoluciones, logística y
   reclamaciones. Reproducción: comando Maven de regresión en
   [commands.sh](cu07/commands.sh); primer punto común de fallo:
   `Purchases.java:55`. No se cambió ese helper ni se relajaron sus expectativas.
2. **E2E de sesión, supuesto incorrecto de la prueba**: `account-security.spec.ts:52`
   lee el primer `definition` como ID. Ambos navegadores muestran allí
   `Chrome · Windows`; comparar esos textos no demuestra identidad de sesión.
   Se dejó el test intacto y se conserva la evidencia del fallo.
3. **E2E de checkout y sus dependencias**: buscan un combobox “Tarjeta” y el botón
   de confirmación del flujo anterior. La interfaz actual usa Mercado Pago.
   El test CU03 siguiente también encuentra el carrito que dejó el recorrido
   fallido. Es un fallo en cascada de preparación, no evidencia de compra correcta.
4. **E2E de inventario**: los tres recorridos esperan un botón con nombre exacto
   “Inventario”, que no aparece en la interfaz probada. Requiere actualizar el
   contrato de UI en una tarea posterior, comprobando el nombre real del control.

Hubo una corrección de fixture en la primera regresión HTTP: la tienda 2 ya estaba
ocupada por una cuenta ficticia creada en CU12 y el seed de CU20 no asignaba tienda
al vecino. Se creó otra tienda para ese vecino en la base desechable; después,
las cinco pruebas de moderación pasaron. Ver
[fixture-correction.md](cu07/fixture-correction.md). No se alteró producción.

Los primeros intentos también corrigieron dos errores del harness nuevo: lectura
SQL sin UTF-8 y expectativa 401 donde SecurityConfiguration especifica
`403 EMAIL_NOT_VERIFIED`. Un intento intermedio se interrumpió por recrear el
backend; se descartó como evidencia funcional y se repitió con servicios saludables.
Las regresiones finales se ejecutaron secuencialmente para evitar interferencia
entre las cuentas compartidas. Los resultados de la tabla son las ejecuciones
finales por suite, no se presentan los intentos iniciales como aprobados.

CU11 completo queda sin acreditar por esos fallos; CU07 solo acredita acceso a
consulta de pedidos y aislamiento de direcciones. No se probaron pagos reales
ni SMTP productivo. No se deduce cobertura de código ni se modificaron umbrales.

## Archivos

- Nuevos: `src/test/java/com/transformersas/integration/buyers/BuyerRegistrationIntegrationTest.java`.
- Nuevos: `tests/e2e/buyer-registration.spec.ts` y `tests/helpers/buyer-registration.ts`.
- Nuevos: `scripts/cu07-compose.sh`, `scripts/cu07-run.sh`, `scripts/cu07-regression.yaml`.
- Nuevo: `.github/workflows/cu07-external.yml`.
- Modificados: `MarketplaceClient.java` y `README.md`.
- Documentación/evidencia: este informe y `docs/cu07/`.

No se modificaron pruebas existentes ni archivos del repositorio principal.

## Repetición externa

Los comandos exactos de las ejecuciones están en [commands.sh](cu07/commands.sh).

Con la aplicación CU07 disponible en el repositorio hermano, dependencias npm
instaladas y Chromium disponible:

```bash
./mvnw -q -DskipTests test-compile
npx playwright install chromium
bash scripts/cu07-run.sh
```

El runner crea un nombre de proyecto único, levanta la infraestructura reutilizando
los dos Compose de la aplicación, ejecuta HTTP y después navegador y elimina su
propio proyecto al salir. Las pruebas HTTP fallidas detienen el runner; en CI el
paso E2E también se intenta cuando la infraestructura pudo iniciarse.
Para rutas/puertos distintos: `CU07_APP_DIR`, `CU08_E2E_PORT` y `CU07_MAIL_PORT`.
No ejecutar dos runners simultáneos con los mismos puertos. Cada ejecución exige
Java 21 o superior, Docker Compose, npm y Chromium.

## GitHub Actions

Se conservan sin cambios los workflows previos y sus suites. El job dedicado
`cu07-external.yml` se activa para la rama de pruebas, PR con rutas relevantes o
workflow_dispatch. No se activó ningún workflow remoto. Usa el SHA inmutable
confirmado y conserva Surefire, HTML Playwright y trazas/capturas de fallos durante
7 días. No despliega ni usa credenciales productivas. Los fixtures SQL son
públicos y exclusivos de MySQL desechable. El backend antiguo fijado en
`integration-tests.yml` no acredita CU07: la validación nueva corresponde al job
separado. La usuaria realiza manualmente las operaciones Git para publicar estos
archivos y permitir la ejecución remota.

La validación local de YAML está en [ci-validation.log](cu07/ci-validation.log).
Es validación sintáctica, no una ejecución de Actions ni una garantía del
resultado remoto.

## Restricciones respetadas

No se ejecutaron operaciones Git (incluyendo commit, checkout, switch, pull,
push o cambios de configuración), ni se activaron workflows remotos. Solo se
leyó metadata local y código de la aplicación; no se modificó backend ni frontend
de producción. No se alteraron bases persistentes ni datos personales reales.
