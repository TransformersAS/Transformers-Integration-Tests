# Pruebas externas CU-09: perfil y preferencias personales

## Objetivo y sistema probado

Validar desde HTTP y Chromium reales el perfil de una identidad, sus preferencias, el cambio seguro de correo y el cierre de cuenta. No se ejecutan pruebas internas ni suites de otros casos de uso.

Repositorio de producción: `TransformersAS/Transformers-AS`.
Revisión inmutable: `836c9d72377acc768380288260fc640532f47afd`.
Árbol informado por GitHub: `bd3754206e805ec81c4bc79a070ec2d1d1d8dec8`.

`scripts/cu09-source.sh` consulta la revisión por la API de GitHub y descarga su archivo tar; registra metadatos y SHA-256 en `target/cu09`. Extrae exclusivamente en `target/cu09/app`, reemplazando esa copia desechable en cada ejecución. No lee la copia vecina como fuente de compilación ni modifica el repositorio principal. No necesita comandos Git locales.

## Contratos comprobados en esa revisión

Prefijo: `/api/account/profile`. Todas las operaciones requieren una cuenta activa autenticada; las mutaciones requieren el token CSRF de la sesión.

| Método y ruta relativa | Contrato y respuesta |
| --- | --- |
| GET (prefijo sin sufijo) | 200: `fullName`, `birthDate`, `email`, `phone`, `promotions`, `marketplaceNews`, `essentialCommunications: true`, `version`, `pendingEmail`, `pendingEmailExpiresAt` |
| PUT (prefijo sin sufijo) | Recibe los seis campos editables, `currentPassword`, `version`, `confirmChanges: true`. 200: `{profile, verificationDeliveryFailed}` |
| POST `/email/resend` | `{currentPassword}`. 200: `{profile, verificationDeliveryFailed}`; sustituye el código anterior |
| POST `/email/confirm` | `{token}`. 204 y revocación de sesiones |
| GET `/closure` | 200: `{allowed, blockers: [{code,message,count}], consequences}` |
| POST `/closure` | `{currentPassword,version,confirmClosure:true}`. 204 y revocación de sesiones |

Los campos editables son `fullName`, `birthDate`, `email`, `phone`, `promotions` y `marketplaceNews`; `version` controla concurrencia. La contraseña se exige al modificar datos personales y cerrar; las preferencias pueden guardarse sin contraseña. El correo vigente permanece mientras el nuevo está pendiente. El código obtenido de Mailpit vence a los 30 minutos. Los campos ajenos al contrato se rechazan con 400, incluido `accountId` o un intento de cambiar `essentialCommunications`.

Estados negativos comprobados: 400 para datos/códigos inválidos o falta de consentimiento; 401 sin autenticación; 403 por CSRF o contraseña incorrecta; 409 por correo ocupado, versión obsoleta o procesos pendientes. Las pruebas comprueban también los códigos de negocio relevantes y que los rechazos no persistan cambios.

## Matriz de requisitos y casos

Suite Java: `src/test/java/com/transformersas/integration/profile/PersonalProfileExternalTest.java`.

| Prioridad solicitada | Requisito | Evidencia automatizada |
| --- | --- | --- |
| 1, 3 | RF-031 | `consultaAisladaYPerfilUnicoEntreRoles`: dos identidades, cambio de rol real, perfil único y ausencia de cambios en la otra cuenta |
| 2 | Seguridad | `anonimoNoAccedeNiModifica`: los seis endpoints rechazan acceso anónimo |
| 4 | RF-031 | `datosValidosPersistenEnOtraSesion`: nombre, fecha y teléfono; lectura con sesión nueva |
| 5 | RF-031 | `validacionesNoAlteranPerfil`: seis invocaciones, nombre vacío, fecha futura, teléfono y correo inválidos, versión negativa y confirmación ausente |
| 6, 7 | RF-034 | `preferenciasIndependientesSinClaveYEsencialesActivas`: promociones/novedades independientes, persistencia entre sesiones y rechazo de desactivación de esenciales |
| 8, 9 | Seguridad | `identidadAjenaYCsrfNoPermitenCambios`: identidad adicional rechazada y CSRF obligatorio en las cuatro mutaciones |
| 10, 11 | RF-032 | `claveIncorrectaYCorreoOcupadoNoCambianDatos`: 403/409 y perfil anterior intacto |
| 12–15 | RF-032 | `correoPendienteReenvioCaducidadConfirmacionYSesiones`: entrega SMTP real, dirección pendiente, rechazo de autenticación prematura, código ajeno/incorrecto/vencido/reemplazado/reutilizado, reenvío con contraseña, confirmación y revocación de dos sesiones |
| 16 | RF-031 | `versionObsoletaNoSobrescribe`: segundo guardado obsoleto rechazado y primera actualización conservada |
| 17, 18 | RF-035 | `obligacionesBloqueanCierre`: diez invocaciones, comprobación de impedimento y cierre rechazado sin desactivar |
| 19 | RF-035 | `cierreExigeClaveConsentimientoYVersionVigente` y `cierreConservaHistorialYRevocaTodasLasSesiones` |
| 20, 21 | RF-035 | Cierre: dos sesiones revocadas, nuevo login rechazado, cuenta/perfil conservados, pedidos, ítems, eventos, reembolsos, reclamaciones y devoluciones sin borrar; tienda suspendida y producto pausado |

Los diez impedimentos comprobados son `ORDERS`, `PAYMENTS`, `REFUNDS`, `CLAIMS`, `RETURNS`, `CHECKOUTS`, `EXTERNAL_PAYMENTS`, `EXTERNAL_REFUNDS`, `RESERVATIONS` y `STORE_INFORMATION`. Son fixtures SQL sin compras ni llamadas a proveedores financieros. Los procesos automáticos de recuperación financiera permanecen desactivados; las fechas de recuperación de las fixtures también se dejan en el futuro.

Navegador: `tests/e2e/cu09-profile.spec.ts`, configuración exclusiva `playwright.cu09.config.ts`.

1. Inicio de sesión, consulta, edición con fecha futura visible, preservación del formulario, contraseña incorrecta, guardado correcto, preferencias persistidas, correo pendiente, código de Mailpit, confirmación, nueva autenticación y cierre permitido con consentimiento.
2. Pedido pendiente: impedimento visible, ausencia del botón de desactivación y cuenta todavía activa.

Cada prueba crea correos UUID propios bajo `@example.test`; no reutiliza cuentas de otras suites. No intercepta ni simula respuestas HTTP ni sustituye la autenticación. El hash de contraseña fijo corresponde exclusivamente a las cuentas desechables.

## Entorno y ejecución local

Requisitos: Java 21, Docker Compose, Node.js 22, npm, Python 3 y GitHub CLI autenticado con acceso de lectura al repositorio principal. La descarga se realiza con `gh api`, sin clonar ni cambiar ramas. Los puertos predeterminados, publicados solo en loopback, son 14309 para la aplicación y 18039 para Mailpit; se pueden cambiar con `CU09_E2E_PORT` y `CU09_MAIL_PORT`.

```bash
npm ci
npx playwright install chromium
# En macOS, seleccionar Java 21 si no es el predeterminado:
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./mvnw -f backend-tests/cu09/pom.xml -DskipTests test-compile
bash scripts/cu09-run.sh
```

El script crea un proyecto `cu09-external-*`, usa los Compose originales de la revisión con un override exclusivo de perfil/CORS y ejecuta únicamente:

```bash
./mvnw -f backend-tests/cu09/pom.xml -Dtest=PersonalProfileExternalTest test
npx playwright test --config=playwright.cu09.config.ts
```

El POM dedicado `backend-tests/cu09/pom.xml` usa las mismas versiones de dependencias existentes y compila exclusivamente las dos clases CU09 y sus dos clientes/configuraciones compartidos; no compila ni ejecuta otras suites. Las imágenes de producción compilan backend/frontend con sus Dockerfile originales; el empaquetado backend no ejecuta suites internas. MySQL arranca sin datos previos y Flyway aplica las migraciones originales; no se crean migraciones nuevas.

La fixture comprueba explícitamente `CU09_ISOLATED=true`, el prefijo del proyecto Compose y la base `cu09_e2e` antes de ejecutar SQL. SQL se utiliza para preparar procesos pendientes/historia, adelantar el vencimiento de un código y observar conservación; todas las acciones del usuario pasan por HTTP/UI reales. SMTP apunta únicamente a Mailpit.

El script conserva los códigos de salida HTTP/E2E y devuelve error si falla cualquiera. Un `trap` recoge logs y elimina contenedores, volúmenes y red aun ante errores; verifica después que no queden recursos etiquetados con su proyecto. Las imágenes de compilación y la caché Docker quedan disponibles para reutilización.

Validadores adicionales aislados, sin cambiar `package.json` ni el lockfile:

```bash
npm install --prefix target/cu09/tools --no-save typescript@5.9.3 @types/node@22 yaml@2
GOBIN="$PWD/target/cu09/bin" go install github.com/rhysd/actionlint/cmd/actionlint@v1.7.7
bash scripts/cu09-validate.sh
```

Ese script verifica TypeScript estricto de los archivos CU09, sintaxis Python/Bash, YAML, disparadores, revisión fijada y Actionlint. El runner valida además `docker compose config --quiet`.

## GitHub Actions

Workflow: `.github/workflows/cu09-external.yml`.

- `push` exclusivamente a `feature/cu09-integration-tests`.
- `workflow_dispatch` manual.
- Descarga de producción fijada a la revisión completa anterior, sin referencias flotantes.
- Únicamente HTTP/E2E CU09, con validadores y compilación previa.
- Limpieza y publicación de evidencias con `if: always()`.
- Si el repositorio principal es privado y `github.token` no permite leerlo desde este repositorio, configurar `CU09_SOURCE_TOKEN` con permiso de lectura sobre aquel. No se imprimen credenciales.

Los workflows generales y los de otros CU no se modificaron. La ejecución remota no se inicia desde esta tarea.

## Archivos entregados

- `backend-tests/cu09/pom.xml`: compilación e informes aislados.
- `src/test/java/com/transformersas/integration/profile/ProfileFixture.java`: cuentas, correo, procesos e historial.
- `src/test/java/com/transformersas/integration/profile/PersonalProfileExternalTest.java`: contratos HTTP.
- `tests/e2e/cu09-profile.spec.ts` y `playwright.cu09.config.ts`: recorridos Chromium exclusivos.
- `scripts/cu09-source.sh`, `cu09-compose.sh`, `cu09-environment.yaml`, `cu09-fixture.py`, `cu09-run.sh`, `cu09-validate.sh`: fuente fijada, entorno, fixtures, ejecución, limpieza y validación.
- `.github/workflows/cu09-external.yml`: CI exclusivo.
- `docs/pruebas-cu09.md`, `docs/cu09/`: informe y evidencias locales.
- `README.md`: únicamente una referencia adicional a CU09.

## Resultados y evidencias

Ejecución local final del 12 de noviembre de 2026 contra la revisión fijada:

| Comprobación | Resultado |
| --- | --- |
| HTTP JUnit/REST Assured | 26 aprobadas, 0 fallidas, 0 errores, 0 omitidas |
| E2E Chromium | 2 aprobadas, 0 fallidas, 0 omitidas, 0 inestables |
| Compilación Java 21 | Correcta; cuatro archivos, exclusivamente CU09 y sus dos clases compartidas |
| Compilación backend/frontend | Imágenes construidas con los Dockerfile originales; servicios saludables |
| Migraciones originales | 46 aplicadas con éxito, incluida V46; sin migraciones nuevas |
| TypeScript estricto, Python, Bash, YAML, Actionlint y Compose | Correctos |
| Salida del runner | 0; HTTP=0 y E2E=0 |
| Recursos restantes del proyecto | 0 contenedores, 0 volúmenes, 0 redes |

Evidencia permanente: [resumen JSON](cu09/resultados.json), [informe HTTP](cu09/http-reports/com.transformersas.integration.profile.PersonalProfileExternalTest.txt), [log E2E](cu09/e2e.log), [compilación](cu09/compile.log), [validadores](cu09/validation.log), [migraciones](cu09/migrations.log), [construcción y arranque](cu09/startup.log), [limpieza](cu09/cleanup.log) y [procedencia](cu09/provenance.json).

Los informes completos de navegador se conservan en `target/cu09/browser-report/`; CI publica sus artefactos. Durante la preparación hubo un intento del runner interrumpido por una edición en curso; su [registro](cu09/intento-interrumpido.log) se conserva separado. La ejecución final anterior se realizó después de corregir y validar el script, con compilación e informes aislados.

## Límites de la comprobación

- Se comprueban los diez tipos de impedimento, sin recorrer todas las combinaciones de estados internos, roles y carreras concurrentes. No se vuelve a ejecutar la regresión interna ni las suites de otros integrantes.
- El vencimiento se fuerza en la base aislada, sin esperar 30 minutos ni cambiar el reloj de producción.
- Se verifica el contrato de comunicaciones esenciales y la imposibilidad de desactivarlas; no se prueba la entrega de todas las notificaciones esenciales del marketplace.
- Solo Chromium, con frontend y backend de la revisión indicada. No se incluye matriz de navegadores ni evaluación de carga.
- CI se valida estáticamente y se ejecuta localmente su runner. No se atribuye un resultado remoto a este nuevo workflow hasta que GitHub Actions lo ejecute.
