# Revisión final CU07: diagnóstico de las 42 fallas y CI

Esta revisión complementa el [informe de implementación](pruebas-cu07.md).
Clasifica las **32 fallas HTTP y 10 E2E originales**; conserva esos reportes y
separa sus resultados de las ejecuciones nuevas. No se realizaron operaciones
Git, PR ni activaciones remotas de Actions; no se modificó producción.

## Dictamen y alcance de la evidencia

No se encontró una falla de categoría A (defecto introducido por CU07) entre las
42 analizadas, ni un defecto de producción confirmado de categoría E. Las causas
del primer fallo observable quedan demostradas: **B = 32, C = 9, D = 1**.
Esto no acredita los comportamientos de pedidos/reclamaciones/logística que las
pruebas no alcanzaron a ejecutar. No se declara verde la regresión completa.

La atribución ya no depende de que un endpoint sea ajeno a CU07. Se consultó por
HTTP la metadata del commit `f77dade0c6168861779b297430bc5e42d343b1ae` y su padre
inmediato `5ada1a36260e96357dcc6f3db4b37a47239ef345`. Se compararon archivos de ambas
revisiones, sin usar Git:

- `PaymentController.java` y `application.properties` son **idénticos**. El guard
  `if (!mockEnabled) ... GONE` y `payments.mock-enabled=false` ya existen en el padre.
- `acceso.component.html` es **idéntico**. El primer `dd` ya describe navegador y
  sistema; el ID corresponde al `dt` “Identificador” y su `dd` siguiente.
- Los diffs de `app.component.html` y `app.component.ts` solo añaden el registro
  de comprador, sus imports/estado y espacios. Checkout Mercado Pago y menú de
  inventario ya existen en el padre, sin cambios en estos bloques.
- La comparación adicional con la revisión vieja del pipeline (`8c4d581…`)
  **sí** muestra cambios de pago/UI entre esa revisión y CU07; por sí sola no
  permitía atribuirlos a CU07. La comparación con su padre inmediato los acota.

Evidencia: [metadata](cu07/revision-final/commit-metadata.json),
[comparación con el padre](cu07/revision-final/parent-comparison/comparison.json),
[diff HTML](cu07/revision-final/parent-comparison/app.component.html.diff) y
[diff TS](cu07/revision-final/parent-comparison/app.component.ts.diff).

**Limitación:** no se arrancó ni ejecutó la suite en el commit padre. La prueba de
antecedentes es de código/contratos, acompañada de ejecuciones en CU07; no es un
experimento completo A/B ni demuestra ausencia de otras regresiones latentes.

## Las 42 fallas: tabla exhaustiva

Cada prueba original aparece exactamente una vez abajo. Los archivos
[CSV](cu07/revision-final/failures-42.csv) y
[JSON](cu07/revision-final/failures-42.json) contienen, por prueba, categoría,
error, causa, evidencia, corrección necesaria y efecto sobre la validación CU07.

| Categoría / causa | Nº | Pruebas originales afectadas | Error y corrección |
|---|---:|---|---|
| B: ClaimIntegrationTest | 4 | `ClaimIntegrationTest.buyerOpensAClaimAndAcceptsTheSellersProposal`<br>`ClaimIntegrationTest.buyerEscalatesWhenThereIsNoAgreement`<br>`ClaimIntegrationTest.aProposalCannotOfferMoreThanWhatWasPaidForTheProduct`<br>`ClaimIntegrationTest.theSellerIsBoundToTheirOwnStore` | 1 expectation failed. Expected status code (is <200> or is <201>) but was <410>.. Migrate purchase fixture to real Mercado Pago sandbox; do not enable mock payments. |
| B: LogisticsWebhookIntegrationTest | 3 | `LogisticsWebhookIntegrationTest.theTrackingCodeMustMatchTheShipment`<br>`LogisticsWebhookIntegrationTest.onlyACorrectSignatureIsAccepted`<br>`LogisticsWebhookIntegrationTest.anUnknownEventTypeIsIgnored` | 1 expectation failed. Expected status code (is <200> or is <201>) but was <410>.. Migrate purchase fixture to real Mercado Pago sandbox; do not enable mock payments. |
| B: OrderTrackingIntegrationTest | 9 | `OrderTrackingIntegrationTest.aLateUpdateNeverMovesTheOrderBackwards`<br>`OrderTrackingIntegrationTest.anOrderReturnedToTheSellerClosesTheTracking`<br>`OrderTrackingIntegrationTest.trackingOfSomebodyElsesOrderIsNotFound`<br>`OrderTrackingIntegrationTest.anOrderWithoutShipmentHasAnEmptyTracking`<br>`OrderTrackingIntegrationTest.theProviderMovesTheOrderFromPickupToDelivery`<br>`OrderTrackingIntegrationTest.nobodyCanSetTheStateByHand`<br>`OrderTrackingIntegrationTest.refreshAsksTheProviderAndIsThrottled`<br>`OrderTrackingIntegrationTest.resendingTheSameEventChangesNothing`<br>`OrderTrackingIntegrationTest.aDeliveryExceptionIsRecordedUntilTheOrderArrives` | 1 expectation failed. Expected status code (is <200> or is <201>) but was <410>.. Migrate purchase fixture to real Mercado Pago sandbox; do not enable mock payments. |
| B: ReturnTrackingIntegrationTest | 4 | `ReturnTrackingIntegrationTest.repeatedAndLateUpdatesDoNotChangeTheReturn`<br>`ReturnTrackingIntegrationTest.refreshingTheReturnIsThrottled`<br>`ReturnTrackingIntegrationTest.threeFailedPickupsStopTheReturn`<br>`ReturnTrackingIntegrationTest.theProviderMovesTheReturnUntilItReachesTheSeller` | 1 expectation failed. Expected status code (is <200> or is <201>) but was <410>.. Migrate purchase fixture to real Mercado Pago sandbox; do not enable mock payments. |
| B: SellerFulfillmentIntegrationTest | 5 | `SellerFulfillmentIntegrationTest.retryingTheShipmentReusesTheExistingReference`<br>`SellerFulfillmentIntegrationTest.anAlreadyDispatchedOrderCannotGoBackToPreparation`<br>`SellerFulfillmentIntegrationTest.theSellerEndpointsRequireTheSellerRole`<br>`SellerFulfillmentIntegrationTest.sellerPreparesAndDispatchesAPaidOrder`<br>`SellerFulfillmentIntegrationTest.theSellerIsBoundToTheirOwnStore` | 1 expectation failed. Expected status code (is <200> or is <201>) but was <410>.. Migrate purchase fixture to real Mercado Pago sandbox; do not enable mock payments. |
| B: ReturnRequestIntegrationTest | 7 | `ReturnRequestIntegrationTest.theReturnMethodOnlyWorksOnceApproved`<br>`ReturnRequestIntegrationTest.aLineThatDoesNotBelongToTheOrderIsRejected`<br>`ReturnRequestIntegrationTest.invalidDataIsRejectedWithTheFieldItComesFrom`<br>`ReturnRequestIntegrationTest.theSellerAsksForInformationAndTheBuyerAnswersAndItGoesBackToReview`<br>`ReturnRequestIntegrationTest.theBuyerRequestsAReturnAndTheSellerApprovesItAndTheBuyerChoosesTheMethod`<br>`ReturnRequestIntegrationTest.rejectingRequiresAJustificationAndClosesTheReturn`<br>`ReturnRequestIntegrationTest.requestingTheSameLineAgainReturnsTheExistingRequest` | 1 expectation failed. Expected status code (is <200> or is <201>) but was <410>.. Migrate purchase fixture to real Mercado Pago sandbox; do not enable mock payments. |
| C: Reads first dd (device) instead of Identificador | 1 | `tests/e2e/account-security.spec.ts:8:5 › cuenta cambia roles, revoca otra sesión y cierra su sesión principal` | Expected not Chrome · Windows. Select dt Identificador and its dd; preserve revoke/logout assertions. |
| C: Old card combobox removed in favor of Mercado Pago | 5 | `tests/e2e/checkout-order-cancellation.spec.ts:4:5 › CU-11: comprador compra una camiseta y cancela definitivamente su pedido`<br>`tests/e2e/claims.spec.ts:17:5 › el comprador abre una reclamación, el vendedor propone una solución y el comprador la acepta`<br>`tests/e2e/claims.spec.ts:101:5 › sin acuerdo con el vendedor, el comprador escala la reclamación a soporte`<br>`tests/e2e/order-tracking.spec.ts:13:5 › el pedido se despacha y el seguimiento avanza con lo que informa el servicio logístico`<br>`tests/e2e/return-tracking.spec.ts:14:5 › el retorno de una devolución se sigue hasta que llega al vendedor` | Timeout locator.selectOption combobox Tarjeta. Migrate complete purchase journey to actual sandbox; outside CU07 scope. |
| D: Shared demo cart left populated by preceding failed checkout | 1 | `tests/e2e/cu03-checkout.spec.ts:4:5 › CU-03 comprador agrega un producto y completa el checkout ` | La cuenta E2E debe comenzar con el carrito vacío. Reset disposable demo cart between runners; migrate checkout before fixing intra-suite cascade. |
| C: Inventory button lives inside collapsed role menu | 3 | `tests/e2e/inventory.spec.ts:32:5 › el vendedor registra una entrada y un ajuste, y quedan en el historial `<br>`tests/e2e/inventory.spec.ts:89:5 › el mínimo enciende la alerta de stock bajo y se puede apagar de nuevo `<br>`tests/e2e/inventory.spec.ts:120:5 › las plantillas de Excel se descargan y una carga con un error rechaza todo el archivo ` | Timeout waiting for button Inventario. Open Opciones de mi negocio then select Inventario; support previous direct button. |

### Evidencia y decisión por causa

**B, 32 HTTP.** Todas las trazas terminan en `Purchases.buyDemoShirt`, en la
llamada a `POST /api/payments/process`; la respuesta es 410 en lugar de 200/201.
El controlador real permite ese flujo únicamente con `payments.mock-enabled`;
la configuración del entorno mantiene el valor real `false`. La API vigente
requiere checkout Mercado Pago. Fuente:
`backend/demo/.../payments/infrastructure/web/controller/PaymentController.java:40`,
`backend/demo/src/main/resources/application.properties:4`,
`src/test/java/.../support/Purchases.java:55` y
[reportes originales](cu07/regression-http-reports/).

La corrección funcional requiere preparar la compra mediante el flujo real de
Mercado Pago y sus credenciales/eventos de sandbox. No es cambiar una expectativa
a 410: eso eliminaría la preparación de los pedidos y su cobertura. No se activa
el gateway simulado, no se crean pedidos mediante SQL ni se sustituyen llamadas
por mocks. Se deja la migración fuera de alcance. Estos fallos impiden acreditar
la regresión integral de pedidos, devoluciones, despacho, logística y reclamaciones,
aunque no bloquean las pruebas directas de alta/habilitación CU07.

**C, 5 checkout E2E.** Buscan un combobox “Tarjeta”, eliminado del contrato visual
actual. `app.component.html:661–683` muestra el texto y botón “Continuar a Mercado
Pago”; no existe un selector equivalente que permita completar sin más el flujo
antiguo. La corrección necesita la misma migración integral de pagos y queda
pendiente. No se cambian las aserciones de compra aprobada ni se reemplazan por
comprobar que apareció un botón. Evidencia: log E2E original y diff con el padre.

**C, 1 sesiones.** Se corrigieron las dos lecturas de ID para seleccionar el `dd`
asociado a “Identificador”. Se conservan todas las comprobaciones de IDs distintos,
revocación de la segunda sesión, rechazo HTTP 401, supervivencia de la sesión
principal, roles y logout. Pasó la prueba completa contra la aplicación real.

**C, 3 inventario.** El menú “Opciones de mi negocio” estaba cerrado. Se añadió
su apertura y se selecciona “Inventario” dentro de la navegación “Mi negocio”.
Se conserva el botón directo si la interfaz anterior lo expone, porque el workflow
heredado sigue fijado a `8c4d581…`. Se conservan los rechazos, historial, cantidades,
alertas, descargas y rechazo atómico de Excel. Las tres pruebas completas pasaron.
No se ejecutó la variante antigua durante esta revisión; su botón directo se
comprobó en el código de aquella revisión.

**D, 1 CU03.** El fallo original es un carrito ficticio compartido con artículos
pendientes tras compras fallidas previas. Se ejecutó CU03 por separado con el
carrito inicialmente vacío: avanzó por esa precondición y falló en el combobox
“Tarjeta”. Esto confirma una primera falla de fixture y una incompatibilidad de
flujo posterior. Evidencia: [ejecución aislada](cu07/revision-final/cu03-clean-cart.log).
El runner limpia únicamente el carrito de `demo@marketplace.local` **entre Maven
y Playwright**, en el esquema desechable validado. La cascada dentro de E2E seguirá
si las compras antiguas fallan; resolverla exige migrar ese flujo, fuera de alcance.
No se elimina la aserción de carrito vacío.

A/E/F no tienen casos en la clasificación del **primer error observado**. No se
clasifican como “correctas” las operaciones que permanecen detrás de ese error.

## Infraestructura y correcciones mínimas

La ejecución de esta revisión construyó la aplicación desde un **archivo del SHA
exacto descargado por HTTPS**, extraído en `/tmp`, sin depender de cambios locales
del repositorio hermano. URL, hash del archivo, imágenes, salud de los servicios
y rutas Compose se conservan en
[runtime-provenance.json](cu07/revision-final/runtime-provenance.json).
No se cambió ningún archivo de backend o frontend.

Se reutilizan `compose.e2e.yaml` y `compose.cu07-e2e.yaml`, con el override local
ya existente `scripts/cu07-regression.yaml`: MySQL `cu08_e2e` desechable, backend,
frontend real y Mailpit. No se activa el gateway de pagos simulado. Las cuentas
son fixtures públicos y se verifica únicamente su correo ficticio.

Se añadió `cu07-seed-regression.sh`: valida el esquema, reutiliza el seed CU20,
prepara categoría/verificación y garantiza tienda propia para el vecino sin
presuponer que la tienda 2 está disponible. Se siembra antes de ejecutar CU07.

`cu07-regression-run.sh` ejecuta todas las suites con CU07 activado, captura
**ambos** códigos de salida, intenta E2E aunque Maven falle, escribe el estado
en `target/cu07-regression/status.json` y devuelve error si cualquiera falla.
La limpieza se limita al proyecto con prefijo `cu07-external-` y se ejecuta al salir.
Los datos originales de fallos no se sobrescriben en `docs/cu07/`.

## Pipeline

El workflow CU07 ahora tiene dos jobs independientes:

| Job | Revisión de la aplicación | Ejecución y resultado esperado |
|---|---|---|
| `cu07` | `f77dade0c6168861779b297430bc5e42d343b1ae` | Las suites dedicadas de alta/habilitación HTTP y E2E |
| `cu07-regression` | el mismo SHA exacto | Todas las pruebas HTTP y E2E; sus fallos mantienen rojo este job |

Se ejecutan automáticamente con push a `feature/cu07-integration-tests`, y en PR
hacia cualquier rama cuando cambian pruebas, helpers, POM, paquetes npm, configuración
Playwright, scripts CU07 o el workflow. También admiten workflow_dispatch, sin que
se haya invocado. Se ampliaron las rutas de PR para no perder cambios del cliente
compartido o dependencias.

Ambos jobs levantan infraestructura real desechable, usan Java 21 y Node 22,
instalan Chromium y conservan reportes Surefire, HTML Playwright y evidencia de
fallos con `if: always()`. El job de regresión conserva además los códigos de salida.
No hay `continue-on-error`, filtros para ocultar fallas ni omisiones añadidas.
Solo permanece la demo E2E optativa que ya existía. No se usan secretos productivos.

No se modifican los tres workflows heredados: `integration-tests.yml` sigue en
`8c4d581…` y los dos workflows CU02/CU03 siguen en `main`. Por ello, su resultado
no acredita por sí solo CU07; el nuevo job de regresión sí usa CU07 y expone sus
fallas, además de conservar las ejecuciones anteriores del equipo.

Validación local: actionlint **1.7.12** sin hallazgos para todos los workflows,
parseo YAML, `bash -n` de los cuatro scripts y `docker compose config --quiet`.
[Auditoría](cu07/revision-final/ci-audit.json). Actions está preparado para ejecutarse,
pero **no se ha ejecutado remotamente** y el job integral todavía puede fallar por
las incompatibilidades documentadas. No se declara exitoso el pipeline remoto.

## Ejecuciones finales

Los conteos se completan con los reportes de esta revisión; las ejecuciones se
solapan y no representan casos únicos adicionales.

| Ejecución de esta revisión | Aprobadas | Fallidas | Omitidas | Evidencia |
|---|---:|---:|---:|---|
| CU07 HTTP dedicado | 16 | 0 | 0 | [log](cu07/revision-final/cu07-http.log), [JUnit](cu07/revision-final/cu07-http-reports/com.transformersas.integration.buyers.BuyerRegistrationIntegrationTest.txt) |
| CU07 E2E, dentro de selección de 8 | 4 | 0 | 0 | [JSON de los 8 casos](cu07/revision-final/affected-e2e/results.json) |
| Regresiones corregidas: sesiones e inventario | 4 | 0 | 0 | [log de la selección](cu07/revision-final/affected-e2e.log) |
| CU03 aislado, carrito inicialmente vacío | 0 | 1 | 0 | [log](cu07/revision-final/cu03-clean-cart.log), falla en Tarjeta |
| Todas HTTP, incluidas las 16 CU07 | 41 | 32 | 0 | [JUnit por clase](cu07/revision-final/full-http-reports/), [log completo](cu07/revision-final/full-run.log) |
| Todas E2E, incluidas las 4 CU07 | 10 | 6 | 1 | [log completo](cu07/revision-final/full-run.log) |

[Resumen legible por máquina](cu07/revision-final/results-summary.json).
De las suites previas, HTTP queda en **25 aprobadas / 32 fallidas / 0 omitidas**;
E2E queda en **6 aprobadas / 6 fallidas / 1 omitida**. La única omisión final es
la demo optativa preexistente, no una prueba CU07 ni una falla ocultada. El conteo
HTTP pasó de 66 a 73 al activar CU07 y expandir sus invocaciones parametrizadas;
no se agregaron ni eliminaron escenarios para alterar el resultado.

El runner devolvió **1**, con `http_exit_code=1` y `e2e_exit_code=1`: se verificó
que la regresión fallida mantiene el fallo global. Los cuatro E2E corregidos
aprobaron también en la ejecución completa. No hay pruebas artificialmente verdes.
El HTML y las trazas de esta ejecución completa quedan en `playwright-report/`
y `test-results/` (ignorados por Git); el CI los subirá como artefactos. Los logs,
JSON y JUnit de esta revisión quedan en `docs/cu07/revision-final/`.

Se verificaron las migraciones Flyway 31 y 45 con `success=1`:
[flyway.log](cu07/revision-final/flyway.log). El runner eliminó los cuatro
contenedores y su red al salir; no se conservaron bases de datos persistentes.

## Repetición y comandos

Se usó una descarga HTTPS del archivo exacto de la aplicación; puede repetirse
sin operaciones Git con `curl` y `tar`, conservando el SHA de la URL:

```bash
curl --fail --location --silent --show-error --max-time 180 https://codeload.github.com/TransformersAS/Transformers-AS/tar.gz/f77dade0c6168861779b297430bc5e42d343b1ae -o /tmp/cu07-review-f77dade.tar.gz
mkdir -p /tmp/cu07-review-source
tar -xzf /tmp/cu07-review-f77dade.tar.gz -C /tmp/cu07-review-source
```

Comandos utilizados para iniciar y preparar el entorno y ejecutar las suites:

```bash
export CU07_APP_DIR=/tmp/cu07-review-source/Transformers-AS-f77dade0c6168861779b297430bc5e42d343b1ae
export CU07_PROJECT=cu07-external-review
bash scripts/cu07-compose.sh -f "$PWD/scripts/cu07-regression.yaml" up -d --build --wait --wait-timeout 300
bash scripts/cu07-seed-regression.sh
CU07_ISOLATED=true BACKEND_BASE_URL=http://127.0.0.1:18087 CU07_MAIL_URL=http://127.0.0.1:18025 ./mvnw -Dtest=BuyerRegistrationIntegrationTest verify
CU07_ISOLATED=true FRONTEND_BASE_URL=http://127.0.0.1:14300 CU07_MAIL_URL=http://127.0.0.1:18025 PLAYWRIGHT_JSON_OUTPUT_FILE=docs/cu07/revision-final/affected-e2e/results.json npx playwright test tests/e2e/account-security.spec.ts tests/e2e/inventory.spec.ts tests/e2e/buyer-registration.spec.ts --reporter=list,json
FRONTEND_BASE_URL=http://127.0.0.1:14300 npx playwright test tests/e2e/cu03-checkout.spec.ts
PLAYWRIGHT_JSON_OUTPUT_FILE=docs/cu07/revision-final/full-e2e-results.json PLAYWRIGHT_JSON_OUTPUT_NAME=results.json bash scripts/cu07-regression-run.sh
```

El runner completo usa el reporter habitual (lista/HTML); esas dos variables JSON
de su invocación no activan un reporter adicional. El resultado completo se obtiene
del log y JUnit, no de un archivo JSON Playwright inexistente.

Para repetir únicamente CU07 con limpieza automática:
`CU07_APP_DIR=/ruta/al/codigo-del-SHA bash scripts/cu07-run.sh`.
Para repetir todo en un entorno nuevo, quitar `CU07_PROJECT` de la sesión y usar
`CU07_APP_DIR=/ruta/al/codigo-del-SHA bash scripts/cu07-regression-run.sh`.
Requiere Java 21+, Docker, npm/dependencias instaladas y Chromium.

Comprobaciones locales de configuración ejecutadas:

```bash
/tmp/cu07-actionlint .github/workflows/*.yml
ruby -e 'require "yaml"; Dir[".github/workflows/*.yml", "scripts/cu07-regression.yaml"].each { |p| YAML.load_file(p); puts "YAML OK: #{p}" }'
bash -n scripts/cu07-compose.sh scripts/cu07-run.sh scripts/cu07-regression-run.sh scripts/cu07-seed-regression.sh
bash scripts/cu07-compose.sh -f "$PWD/scripts/cu07-regression.yaml" config --quiet
```

`/tmp/cu07-actionlint` es la versión 1.7.12 descargada del release oficial durante
esta revisión; no se instaló ni cambió configuración global. No hay hallazgos en
[actionlint-all.log](cu07/revision-final/actionlint-all.log).

## Archivos de esta revisión

Modificados:

- `tests/e2e/account-security.spec.ts`: lectura semántica del ID.
- `tests/e2e/inventory.spec.ts`: apertura del menú vigente, compatible con el botón previo.
- `.github/workflows/cu07-external.yml`: rutas de PR y job integral explícito sobre CU07.
- `README.md` y `docs/pruebas-cu07.md`: enlaces e instrucciones actualizadas.

Creados:

- `scripts/cu07-seed-regression.sh` y `scripts/cu07-regression-run.sh`.
- Este informe y `docs/cu07/revision-final/` con diagnóstico, comparación, reportes y resultados.

No se modificaron pruebas HTTP, aserciones de negocio, umbrales, configuración
productiva, código de la aplicación ni los workflows del resto del equipo.

## Preparación para integrar y pendientes

**CU07 está listo funcionalmente dentro del alcance externo ejecutado**: alta,
validaciones, concurrencia, perfil/consentimientos, verificación/reenvío/códigos,
fallo SMTP recuperable, habilitación, identidad/roles/rol activo, aislamiento y
regresión CU08/CU12 observada. Sus 16 HTTP y 4 E2E pasan sobre el SHA exacto.
No se identificó entre las 42 fallas una regresión atribuible a su implementación.

**La integración global permanece condicionada** por los 32 fallos HTTP y 6 E2E
restantes. No se puede certificar todo CU11 ni las operaciones de pedidos que
requieren compra aprobada. Pendiente: migrar fixtures/recorridos al checkout real
con sandbox, reparar el aislamiento del carrito entre esos casos y volver a
validar lo que actualmente no se alcanza. Podrían aparecer errores posteriores
al desbloquear la compra; no se presupone que todos pasarán.

No hay defecto de producción confirmado que reportar en esta revisión. No se
asegura ausencia de defectos fuera de la cobertura ni éxito de GitHub Actions.
No se verificaron políticas remotas de checks requeridos para integrar. La usuaria
realiza las operaciones Git manualmente; esta tarea no ejecutó ninguna.
