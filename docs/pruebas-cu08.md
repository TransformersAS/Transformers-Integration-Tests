# CU-08 — pruebas externas de acceso y seguridad

Se reutiliza `tests/e2e/account-security.spec.ts` para login, roles, revocación de
una sesión concreta y logout. Se conservan todas sus aserciones. Se corrige la
lectura del ID para localizar el `dd` asociado al `dt` “Identificador”; el primer
`dd` de la interfaz vigente corresponde al dispositivo y no identifica una sesión.

## Referencia probada y aislamiento

Se inspeccionó el código del commit inmutable
`fd5f62493f4936a44e7b122be1c8841a12a19572`, descargado como archivo HTTPS y extraído
en `/tmp`, sin operaciones Git. El código contiene SessionController,
PasswordRecoveryController, EmailVerificationController, ChangeAccountPassword,
RecoverAccountPassword, VerifyAccountEmail, ManageAccountSessions,
LoginAttemptGuard, AccountPrincipal, AccountUserDetailsService, PasswordPolicy,
SecurityConfiguration y la interfaz de acceso que las pruebas ejercitan.
No se eligió el SHA solo por su antigüedad o mensaje de commit: se comprobaron sus
contratos y se construyó esa fuente para ejecutar las pruebas.

[provenance.json](cu08/provenance.json) registra metadata del commit, hash del
archivo descargado, ubicación y hashes de los archivos inspeccionados. No se
modificó el repositorio principal. No se presupone que el repositorio hermano,
que todavía indica una rama CU07, corresponda al SHA requerido.

La infraestructura reutiliza únicamente `compose.e2e.yaml` de esa referencia para
MySQL/backend/frontend. El override propio `scripts/cu08-environment.yaml` añade
Mailpit, SMTP de pruebas, CORS del puerto aislado y la cadena de proxy necesaria.
No copia Dockerfiles ni adapta código de producción. No necesita archivos CU07.

Proyecto Compose exclusivo `cu08-external-*`, esquema `cu08_e2e`, sin volúmenes
persistentes ni puertos de MySQL/backend publicados. Frontend y Mailpit solo se
publican en loopback. Credenciales BCrypt y cuentas son fixtures públicos de
prueba; los escenarios nuevos usan correos UUID. La cuenta demo compartida solo
se usa para el recorrido existente. El esquema se comprueba antes de sembrar.

Para bloqueo temporal, `cu08-prepare.sh` arranca en observación, envía una solicitud
real a través de Nginx, lee la IP que recibió Tomcat con `forwarded=true`, valida
que sea una IP privada y activa el guard únicamente con ese origen observado.
Nginx reemplaza las cabeceras de IP del cliente: no agrega como origen una IP
aportada por el solicitante. El backend confía solo en el proxy `172.30.248.10`.
La red `172.30.248.0/24` se elimina junto con el proyecto. Los logs de observación
no incluyen URL, cookies ni credenciales. Véase `target/cu08/origin-observation.log`.

## Cobertura observable

La clase HTTP es `auth/AccountSecurityIntegrationTest`; el soporte `AuthFixture`
solo prepara datos y lee el buzón. Las operaciones de negocio se ejercitan por
HTTP real usando `MarketplaceClient`. No se importan clases de la aplicación.

| Alcance CU08 | Pruebas HTTP / E2E que lo acreditan |
|---|---|
| 1. Credenciales correctas e incorrectas | `credentialsAccountRolesAndLogout`; E2E credenciales y recorrido existente |
| 2. Logout e invalidación real | mismo HTTP y recorrido existente; se vuelve a consultar con la cookie anterior |
| 3. Cuenta y rol activo | mismo HTTP, respuesta email/accountId/roles/activeRole; E2E existente y login comprador |
| 4–5. Roles asignados/no asignados | cambio VENDEDOR/COMPRADOR, rol ADMIN 403 y cuerpo vacío 400; selección UI existente |
| 6. Sesiones activas | `sessionOwnershipSpecificAndOtherRevocations`: sesiones propias e indicador actual; UI de sesiones |
| 7. Revocación concreta | mismo HTTP, sesión revocada recibe 401 y sesión actual sigue 200; E2E existente |
| 8. Revocar las demás | mismo HTTP, preservación de sesión actual y revocación propia explícita; E2E específico de dos navegadores |
| 9. Cambio de contraseña | `passwordChangePreservesCurrentSessionAndRejectsOldCredentials`: errores 403/400, contraseña anterior rechazada, otras sesiones revocadas y actual conservada; E2E cambio de contraseña |
| 10. Recuperación | `recoveryIsGenericReplacesTokensAndRevokesAllSessions`: respuesta igual para correo existente/ausente, inválido/reemplazado/reutilizado, política, revocación de todas las sesiones; `expiredRecoveryAndVerificationTokensAreRejected`; E2E recuperación SMTP |
| 11. Verificación | `emailVerificationRequiresCredentialsAndLatestUnusedToken`: pendiente 403 EMAIL_NOT_VERIFIED, reenvío exige contraseña, códigos inválidos/reemplazados/usados, confirmación no autentica; expiración; E2E pendiente → reenvío SMTP → verificación → login |
| 12. Bloqueo temporal | `loginTemporarilyLocksOnlyTheTargetAndRecoversAfterExpiry`: cinco rechazos, siguiente 429/Retry-After, cuenta distinta no bloqueada, recuperación tras vencimiento; E2E bloqueo y mensaje visible |
| 13. Acceso anónimo | `anonymousAndCsrfProtection`: cuenta/sesiones/roles/contraseña/revocaciones protegidas |
| 14. CSRF y aislamiento | mismo HTTP sin CSRF 403; identificador de sesión de otra cuenta no listado y revocación 404 sin afectar al propietario |

Las expiraciones de tokens y del bloqueo se preparan mediante SQL dirigido
únicamente a la cuenta UUID en esta base desechable, después de probar emisión y
bloqueo reales. No se esperan 15/30 minutos de reloj ni se cambia el reloj del
backend. La comprobación posterior pasa por los endpoints reales. Esto no es una
medición de la duración efectiva del timeout en tiempo de pared.

No se simulan respuestas con `page.route()`, no se ejecutan suites ajenas ni se
rebajan expectativas para declarar éxito. El guard está activo durante HTTP y
E2E; un test que omita ese entorno no acredita cobertura.

## Ejecución reproducible

Requisitos: Java 21+, Docker Compose, dependencias npm instaladas y Chromium.
Usar las fuentes identificadas de la aplicación. La usuaria puede proporcionar
una carpeta con ese SHA; no se necesita ninguna operación Git del agente.

```bash
curl --fail --location --silent --show-error --max-time 180 https://codeload.github.com/TransformersAS/Transformers-AS/tar.gz/fd5f62493f4936a44e7b122be1c8841a12a19572 -o /tmp/cu08-source.tar.gz
mkdir -p /tmp/cu08-source
tar -xzf /tmp/cu08-source.tar.gz -C /tmp/cu08-source
CU08_APP_DIR=/tmp/cu08-source/Transformers-AS-fd5f62493f4936a44e7b122be1c8841a12a19572 bash scripts/cu08-run.sh
```

El runner configura origen/CORS/correo, ejecuta **solo**:

```bash
./mvnw -Dtest=AccountSecurityIntegrationTest verify
npx playwright test tests/e2e/account-security.spec.ts tests/e2e/cu08-access.spec.ts
```

Conserva los dos códigos de salida en `target/cu08/status.json`, intenta E2E aunque
HTTP falle, devuelve error si cualquiera falla y elimina solo su propio proyecto
al salir. Reportes estándar: `target/surefire-reports/`, `playwright-report/`,
`test-results/`. No hay omisiones convertidas en éxitos. Se activa la clase HTTP y
los escenarios nuevos con `CU08_ISOLATED=true`, dentro del runner/CI.

Variables: `CU08_APP_DIR`, `CU08_PROJECT` (prefijo obligatorio), `CU08_E2E_PORT`
(14308), `CU08_MAIL_PORT` (18028). Solo se admite una instancia simultánea con
la subred fija actual; no ejecutar otro proyecto que use esa subred o los puertos.

## CI

`cu08-external.yml` se activa en push a `feature/cu08-integration-tests` y permite
workflow_dispatch. Fija el SHA inspeccionado, instala Java 21, Node 22 y Chromium,
usa el runner aislado y sube reportes HTTP/E2E/origen como artefactos durante 14
días, incluso si hay fallos. No ejecuta regresiones generales ni CU02/CU03/CU07.
No usa secretos productivos ni contiene `continue-on-error`.

Los tres workflows compartidos solo cambian el filtro de push para excluir la
rama CU08. Sus jobs, opciones manuales, PR y comportamiento de main/otras ramas
se conservan. Un PR abierto aún puede activar aquellos workflows por el evento
`pull_request`; se preserva ese comportamiento expresamente, sin editar sus jobs.
No hay workflow CU07 en esta rama y no se copió desde otra rama.

## Resultados y evidencias

Se registran abajo los resultados finales efectivos, separados de los intentos
iniciales y sin sumar ejecuciones solapadas como casos nuevos.
| Ejecución final desde cero | Aprobadas | Fallidas | Omitidas | Evidencia |
|---|---:|---:|---:|---|
| Compilación del harness | Correcta | 0 | — | [compile.log](cu08/compile.log), release 21 |
| HTTP CU08 | 8 | 0 | 0 | [JUnit](cu08/http-reports/com.transformersas.integration.auth.AccountSecurityIntegrationTest.txt) |
| E2E CU08, incluido recorrido existente | 7 | 0 | 0 | [final-run.log](cu08/final-run.log) |
| Runner HTTP/E2E | salida 0 / 0 | 0 | — | [runner-status.json](cu08/runner-status.json) |
| YAML, shell, Compose y Actionlint | Correctos | 0 | — | [ci-validation.log](cu08/ci-validation.log), [actionlint.log](cu08/actionlint.log) |
| GitHub Actions remoto | No ejecutado | — | — | No se activaron workflows |

**15/15 casos externos aprobados** en el entorno real, con los 14 puntos de alcance
cubiertos por los pasos/aserciones indicados en la matriz. La ejecución final
incluyó preparación desde cero, observación del proxy, activación del guard,
siembra, ambas suites y limpieza. No son resultados deducidos de pruebas internas
ni de pruebas omitidas. [results.json](cu08/results.json) contiene los conteos y
la comprobación de ausencia de contenedores de los proyectos de esta tarea.

La prueba existente volvió a pasar junto a las seis nuevas E2E. Los logs
[http.log](cu08/http.log) y [e2e.log](cu08/e2e.log) corresponden a la selección
previa ya corregida; se solapan con la repetición final, no deben sumarse como
casos adicionales. El HTML final está en `playwright-report/index.html`; las trazas
se conservan automáticamente si hay fallos. Estas rutas están ignoradas por Git y
CI las sube como artefactos. No se publican códigos SMTP en logs de aserciones.

Correcciones realizadas durante los intentos iniciales, exclusivamente del harness:

- La subred inicialmente elegida se solapaba con una red existente; se eligió
  `172.30.248.0/24` sin detener ni modificar los demás proyectos Docker.
- Se añadió el origen exacto del frontend de pruebas a CORS. Los 403 anteriores
  de navegador no demostraban credenciales incorrectas; HTTP ya alcanzaba la API.
- El endpoint real de validación de rol devuelve 204, no 200; se ajustó esa
  expectativa tras comprobar la anotación del controlador en el SHA probado.
- Se ajustó el mensaje de credenciales al texto vigente de `acceso.component.ts`
  y se restringieron los estados al componente de acceso para excluir el contador
  de resultados del catálogo. No se eliminaron aserciones.

No se detectó un defecto confirmado de producción después de estas correcciones.
No quedan escenarios obligatorios del alcance enumerado sin ejecutar. Pendiente:
ejecución remota de Actions (no solicitada/activada), mediciones de duración de
expiración con tiempo real de pared, pruebas de carga y SMTP productivo. Estas
últimas no se presentan como parte de la validación funcional obtenida.

## Archivos creados y modificados

Creados:

- `src/test/java/com/transformersas/integration/auth/AccountSecurityIntegrationTest.java`.
- `src/test/java/com/transformersas/integration/auth/AuthFixture.java`.
- `tests/e2e/cu08-access.spec.ts` y `tests/helpers/cu08-auth.ts`.
- `scripts/cu08-compose.sh`, `scripts/cu08-prepare.sh`, `scripts/cu08-run.sh`.
- `scripts/cu08-environment.yaml` y `scripts/cu08-nginx.conf`.
- `.github/workflows/cu08-external.yml`.
- Este informe y evidencia bajo `docs/cu08/`, incluyendo [comandos](cu08/commands.sh).

Modificados:

- `support/MarketplaceClient.java`: visitante con CSRF, login observable,
  renovación y solicitudes negativas sin CSRF. Se reutilizan las cookies y
  operaciones anteriores; no se modifica la lógica de otros casos de uso.
- `tests/e2e/account-security.spec.ts`: selector semántico del ID de sesión,
  conservando todas las aserciones del recorrido.
- Los tres workflows compartidos: únicamente filtro de rama del evento push.
- `README.md`: instrucciones y enlace al informe.

No se cambiaron dependencias, POM, pruebas de otros CU, jobs ajenos, configuración
Git, ni archivos del repositorio principal. No se realizaron operaciones Git,
creación/cambio de ramas, commit, push/pull, merge, rebase, reset ni PR. La rama
CU08 ya existía y solo se leyó su referencia como archivo para confirmarla.
