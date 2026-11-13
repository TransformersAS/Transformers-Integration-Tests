# Pruebas externas CU-10 — Conversar sobre productos y pedidos

Responsable: Sofía. Revisión exclusiva: `3c12266316c86aa97097f0317507dd935af99286`, árbol `fa8a3b4000ef0d949eecc61fbf714171e6e9d6d4`. Contratos contrastados con el archivo descargado mediante GitHub API, especialmente `ConversationController`, `ConversationService`, V47, adaptadores de reportes y `docs/CU10.md`. No se importan clases productivas ni se copia su lógica.

## Arquitectura y reproducción

Requisitos: Docker Compose, Java 21, Node 22, Python 3, `gh` autenticado con lectura del repositorio de producción. En CI se usa `CU10_SOURCE_TOKEN` (o el token del workflow si tiene acceso). Solo se usan identidades `@example.test`, contraseñas de fixture y MySQL desechable.

```bash
npm ci
npm ci --prefix tools/cu10 --ignore-scripts
npx playwright install chromium
bash scripts/cu10-validate.sh
bash scripts/cu10-run.sh
```

`CU10_PORT` permite elegir otro puerto (predeterminado 14310). Cada ejecución crea `target/cu10/cu10-external-<fecha UTC>-<pid>`; rehúsa reutilizar una carpeta existente. Conserva el archivo fuente, su SHA-256, SHA/árbol declarados por GitHub, configuración resuelta, migraciones, logs, Surefire XML, trazas, capturas y reportes Playwright. No se sobrescriben evidencias anteriores.

El runner descarga exclusivamente el SHA fijado, crea un proyecto Compose propio y levanta MySQL 8.4.11, backend Java 21, frontend real y Mailpit. El Dockerfile externo compila el backend mediante `compile jar:jar spring-boot:repackage`, sin ejecutar suites productivas. El frontend usa su Dockerfile y lockfile descargados. MySQL no publica puertos y sus datos están en volúmenes eliminados con el proyecto. No se carga `.env` personal.

Tras comprobar readiness, se consulta Flyway y se exige V47 exitosa. Se ejecuta Maven dedicado y después Playwright aun cuando HTTP falle. El resultado combinado es distinto de cero si falla cualquiera. El trap conserva logs, ejecuta `down --volumes --remove-orphans` y consulta contenedores (incluidos detenidos), volúmenes y redes por etiqueta exacta del proyecto. Una consulta o limpieza fallida también hace fallar la ejecución.

## Aislamiento

- Java: `backend-tests/cu10/pom.xml`, fuente convencional local `src/test/java/cu10`, único patrón Surefire `MessagingExternalTest.java`, `failIfNoTests=true`. El POM general utiliza `src/test/java` de la raíz y no incorpora este módulo.
- Navegador: `tests/cu10-e2e`, fuera de `tests/e2e`; configuración exclusiva `playwright.cu10.config.ts`. Se registra el descubrimiento general mediante `--list` sin ejecutar otras pruebas.
- Las fixtures son diferentes por método HTTP y por recorrido de navegador. No dependen del orden ni de los datos generados por otra prueba.
- No se modifican POM/configuración generales, CU-09, CU-11, CU-12 ni el repositorio principal.
- El workflow `CU10 External Tests` solo responde a push en `feature/cu10-integration-tests` o activación manual, en Ubuntu 24.04 con Java 21 y Node 22. Publica evidencia con `always()`, sin convertir fallos en éxitos.

## Matriz funcional

| Requisito | Evidencia ejecutable |
|---|---|
| RF-037 | `contextosYConcurrencia`: producto, dos pedidos de dos líneas, titular real; ambos E2E abren el contacto desde la interfaz |
| RF-038 | `mensajesCursoresIdempotenciaYNotificaciones`, `imagenesYReportes`, `validaciones`: comprador/vendedor, 4000/4001, tres formatos reales, archivos falsos, 5 MiB, dimensiones y píxeles |
| RF-039 | Cursores exclusivos after/before, IDs crecientes, reintento idéntico, conflicto de clave, rechazo de PUT/DELETE y persistencia tras recargar |
| RF-040 | `lecturaArchivoYCierre`: lectura por participante, archivo y restauración, cierre/reapertura, envío rechazado durante cierre |
| RF-041 | Notificaciones obtenidas por HTTP; SQL observa estado externo fallido y deduplicación por evento; fallo SQL revierte mensaje y notificación |
| RF-100 | Archivo independiente, desarchivo del receptor por mensaje nuevo, bandeja privada y no leídos |
| RF-101 | Reutilización, ocho solicitudes concurrentes sobre un contexto aún inexistente y separación producto/pedido/producto seleccionado; pedido ajeno y producto inexistente rechazados |
| RF-102 | Texto, multipart real, clave 16–64; validación de contenido y límites de imagen |
| RF-103 | Cuenta ACTIVA con `communications_restricted=true` lee pero no envía; tienda RESTRICTED también; restauración en `finally` |
| RF-104 | Reporte CU-20 de mensaje recibido, rechazo propio/ajeno, imagen original para SOPORTE antes/después de ocultación, imagen moderada inaccesible al participante |

## Matriz de alternos A1–A10

Los significados se contrastaron con `docs/CU10.md` del SHA descargado.

| Alterno | Comprobación |
|---|---|
| A1 | Reutilización secuencial y concurrente del contexto |
| A2 | Distintos productos y pedidos generan distintos hilos |
| A3 | JPG/PNG/WebP válidos; adjunto falso rechazado; error visible y texto conservado en E2E de pedido |
| A4 | Vacío sin imagen produce 400 |
| A5 | Restricciones de cuenta activa y tienda permiten leer y bloquean enviar |
| A6 | Cerrar, intentar enviar, reabrir y enviar |
| A7 | Archivo por participante, restauración explícita y automática del destinatario |
| A8 | CU-20 y evidencia original después de ocultar contenido |
| A9 | Proveedor simulado UNAVAILABLE; mensaje/notificación persisten y reintento no duplica |
| A10 | Trigger temporal falla al insertar notificación; rollback del mensaje; navegador conserva borrador y no muestra entrega |

## Contratos HTTP y seguridad

Autenticación real: `GET /api/auth/csrf` devuelve `token` y `headerName`; `POST /api/auth/login` recibe formulario email/password y responde 204. Se conservan cookies y se renueva CSRF después del login. No se inventan endpoints auxiliares. Cada identidad tiene un único rol para evitar selección ambigua.

| Método y ruta | Entrada / resultado |
|---|---|
| POST `/api/conversations` | `{productId,orderId?}` → 200, contexto y participantes reales |
| GET `/api/conversations?archived=false&page=0` | Lista privada |
| GET `/api/conversations/{id}` | `id,buyerId,sellerId,storeId,productId,orderId,productName,storeName,closed,archived,unread,readThrough,updatedAt` |
| GET `/{id}/messages` bajo el mismo prefijo | `after=0,before=9223372036854775807,size=50` → lista cronológica de `id,conversationId,senderId,text,createdAt,image` |
| POST `/{id}/messages` | JSON `{text,clientKey}` o multipart `text,clientKey,image?` → 200 |
| POST `/{id}/read` | `{through}` |
| POST `/{id}/archive`, `/{id}/closed` | `{value}` |
| GET `/api/conversations/images/{messageId}` | Binario privado original visible |
| POST `/api/reports` | `{contentType:"MENSAJE",contentId,reason:"CONTENIDO_INAPROPIADO",description}` |
| GET `/api/support/messaging/images/{messageId}` | SOPORTE con reporte existente; conserva binario original |

Claves: `[A-Za-z0-9_-]{16,64}`. Un cambio de contenido con igual clave devuelve 409. Ausencia de CSRF devuelve 403; visitante 401; terceros no ven hilos/imágenes (404). Un senderId enviado por el cliente no sustituye la identidad autenticada. Las rutas de edición/borrado inexistentes devuelven 404: se comprueba además que el contenido permanece.

## Fixtures, MySQL y fallos

`scripts/cu10-fixture.py` verifica `CU10_ISOLATED=true` y `DATABASE()=cu10_e2e` antes de escribir. Crea cuentas verificadas COMPRADOR, VENDEDOR, tercero y SOPORTE; tienda con dueño real, dos productos activos y dos pedidos propios con ambas líneas. Son inserciones de preparación, sin compras, pagos ni correo productivo.

Los comportamientos se ejercen por HTTP/navegador. SQL se usa para preparar restricciones y moderación y para observar atomicidad/deduplicación. La ocultación es fixture; no se ejecuta una suite CU-21. El trigger de fallo sobre `notifications` provoca un error después de intentar insertar el mensaje y se elimina en `finally`. MySQL permite el trigger con `log-bin-trust-function-creators=1` exclusivamente en este entorno.

`NOTIFICATIONS_PROVIDER=simulated` y `NOTIFICATIONS_SIMULATED_MODE=UNAVAILABLE` utilizan la capacidad configurable existente del SHA productivo. Se observa `external_status=FAILED,attempts=1` sin pérdida del mensaje ni de la notificación interna. La suscripción después del commit se audita en el código descargado; la prueba externa acredita el resultado durable, no mide por separado el instante de ejecución del callback. Mailpit evita correo real. No se habilitan pagos ni proveedores externos.

## Resultados y limitaciones

Ejecución final local del **12 de noviembre de 2026, 19:42–19:44 (America/Bogota)**: `target/cu10/cu10-external-20261113004257-72968`.

| Suite | Ejecutadas | Aprobadas | Fallidas/errores | Omitidas |
|---|---:|---:|---:|---:|
| HTTP JUnit/REST Assured | 9 | 9 | 0 | 0 |
| Playwright Chromium | 2 | 2 | 0 | 0 |

Evidencia directa: `http.log`, `http-reports/TEST-cu10.MessagingExternalTest.xml`, `e2e.log`, `e2e-results.json`, `summary.json` y `status.json` en esa carpeta. `exit-code.txt` contiene **0**; `remaining-container.txt`, `remaining-volume.txt` y `remaining-network.txt` están vacíos. El respaldo `scripts/cu10-cleanup.sh` también se ejecutó para verificar el mecanismo utilizado por CI.

`provenance.json` registra SHA/árbol; el prefijo del directorio raíz del archivo fuente también se valida antes de extraer. SHA-256 del archivo fuente final: `278d924ec5603b0f80673aa19ede90c11f9cad99b2c4ca403884359fa823a089`. `harness.sha256` permite identificar exactamente los scripts, pruebas y recursos ejecutados.

Compilación Maven con Java 21: **BUILD SUCCESS**, una clase de pruebas dedicada compilada y nueve métodos descubiertos/ejecutados. Imágenes Docker de backend y frontend construidas correctamente; frontend compilado con Node 22 según su Dockerfile inmutable. MySQL aplica migraciones hasta **47**, todas las entradas registradas con success=1. YAML, Bash, Python, XML Maven, TypeScript estricto y actionlint del workflow aprobados. Descubrimiento dedicado: dos E2E; descubrimiento general: ningún CU-10. No se ejecutó ninguna suite general.

Las ejecuciones exploratorias se conservaron:

| Carpeta bajo target/cu10 | HTTP | Navegador | Clasificación |
|---|---|---|---|
| cu10-external-20261113003116-65672 | 8 errores | Sin reporte | Arnés: serializador JSON ausente y archivos aún en preparación |
| cu10-external-20261113003347-66569 | 6 aprobadas, 2 fallidas | 2 fallidas | Arnés: contrato 404, privilegio del trigger, cierre del panel de login; edición del script mientras corría causó error de sintaxis y un código inicial incorrecto, corregido con validación explícita del estado de ambas suites |
| cu10-external-20261113003752-68988 | 6 aprobadas, 3 fallidas | 2 fallidas | Arnés: contrato público de notificaciones, salto final SQL, rechazo 413 y selector de pedido; verificación de limpieza incompatible con arrays vacíos en Bash 3.2 |
| cu10-external-20261113004019-70912 | 8 aprobadas, 1 error | 2 aprobadas | Arnés: endpoint del vendedor debe ser /api/seller/notifications; mismo problema Bash de limpieza |

No se borraron fallos ni se omitieron pruebas para obtener el resultado final. Se sustituyeron los arrays vacíos por ramas compatibles con Bash de macOS y se comprobó la limpieza mediante el script de respaldo para todas las ejecuciones. **No se confirmó un defecto productivo** dentro del alcance ejecutado.

Limitaciones verificadas: el workflow fue validado estáticamente, pero no se disparó GitHub Actions ni se hizo push. La ejecución local fue macOS ARM64 con Java 21.0.5, Node anfitrión 24.8.0 y Docker 29.8.0; CI está configurado para Ubuntu 24.04/Java 21/Node 22 y la compilación del frontend sí utiliza Node 22 en Docker. No se acreditó equivalencia por ejecución en Ubuntu. El fallo externo utiliza el simulador configurable existente; no se prueban proveedores de correo/push reales. El orden después del commit tiene auditoría de código y comprobación de persistencia/estado FAILED, sin instrumentación temporal independiente del callback. La moderación se prepara por SQL; se prueba su efecto externo y preservación, no el flujo administrativo CU-21.

## Inventario

Nuevos: `.github/workflows/cu10-external.yml`; `backend-tests/cu10/pom.xml`; `backend-tests/cu10/src/test/java/cu10/MessagingExternalTest.java`; `playwright.cu10.config.ts`; `tests/cu10-e2e/conversations.spec.ts`; imágenes en `tests/cu10-e2e/assets`; `scripts/cu10-source.sh`, `cu10-compose.sh`, `cu10-run.sh`, `cu10-cleanup.sh`, `cu10-validate.sh`, `cu10-fixture.py`, `cu10-results.py`, `cu10-environment.yaml`, `cu10-backend.Dockerfile`; validadores con lockfile en `tools/cu10`; este documento. Modificado: únicamente `README.md` para enlazar la documentación.

No se ejecutaron operaciones Git. No se modificó Transformers-AS. No se ejecutaron suites de otros integrantes.


## Correspondencia de los 30 escenarios solicitados

Todos los grupos siguientes aprobaron en la ejecución final; los números corresponden al encargo.

| Escenarios | Comprobación efectiva |
|---|---|
| 1–6 | `contextosYConcurrencia`: producto/pedido/producto seleccionado, reutilización, creación concurrente, dos pedidos propios y pedido ajeno/producto inexistente |
| 7–8 | `mensajesCursoresIdempotenciaYNotificaciones`: comprador envía, vendedor titular responde |
| 9–11 | `autorizacionYCsrf`: tercero, visitante, bandeja privada, CSRF e identidad del remitente |
| 12–14 | Historial ordenado, after/before, PUT/DELETE rechazados, contenido conservado; recarga E2E |
| 15–17 | `lecturaArchivoYCierre`: marcas independientes, archivo/restauración, cierre/bloqueo/reapertura |
| 18–19 | `validaciones`: vacío, clave inválida, límite de texto |
| 20–21 | Reintento con igual ID y conflicto por contenido diferente |
| 22 | `imagenesYReportes`: tres imágenes reales y bytes recibidos idénticos |
| 23 | `validaciones`: falso PNG, exceso de tamaño, dimensión y total de píxeles |
| 24 | Imagen privada ajena rechazada en `imagenesYReportes` |
| 25–26 | `restriccionesConLectura`: cuenta activa restringida y tienda RESTRICTED |
| 27 | Bandeja HTTP con conversationId y estado durable de notificación |
| 28–29 | Reportes recibidos/propios/ajenos, evidencia SOPORTE y ocultación sin destruir bytes |
| 30 | `falloPersistenciaAtomico`, `falloExternoNoRevierteNiDuplica` y fallo SQL visible en E2E |
