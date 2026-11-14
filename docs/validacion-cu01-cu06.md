# Validacion ampliada de CU01-CU06

Fecha: **14 de noviembre de 2026**, hora de Bogota.
Backend: commit `bcfbac4741fd7d8cb72d7932e37fa7703bb52a89`, rama **`fix/rf143-recommendation-visibility`**, con árbol de trabajo limpio al iniciar la revalidación independiente.
Entorno: JDK 21, Maven, Spring Boot, Flyway y MySQL 8.4.11 Testcontainers en Docker Desktop. La IA se prueba con WireMock, sin credenciales reales.

## Resultados obtenidos tras corregir RF-143

- CU02 independiente: **17 aprobadas, 0 fallos, 0 errores y 0 omitidas**.
- CU01-CU06 en una unica ejecucion conjunta: **109 aprobadas, 0 fallos, 0 errores y 0 omitidas**.
- Regresiones seleccionadas del backend: **107 aprobadas, 0 fallos, 0 errores y 0 omitidas**.

Ahora pasan los dos contratos que fallaban, tanto en CU02 independiente como en la suite conjunta:

- `Cu02VisibilityContractIntegrationTests.suspendedStoreProductsMustNotBeRecommended`.
- `Cu02VisibilityContractIntegrationTests.moderatedProductsMustNotBeRecommended`.

| Caso | Ejecuciones | Aprobadas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: | ---: |
| CU01 | 4 | 4 | 0 | 0 | 0 |
| CU02 | 17 | 17 | 0 | 0 | 0 |
| CU03 | 23 | 23 | 0 | 0 | 0 |
| CU04 | 22 | 22 | 0 | 0 | 0 |
| CU05 | 25 | 25 | 0 | 0 | 0 |
| CU06 | 18 | 18 | 0 | 0 | 0 |

La tabla corresponde a una unica ejecucion conjunta completada, no a una consolidacion de intentos parciales. El resultado previo de 107 aprobadas y dos fallos de RF-143 queda superado por esta ejecucion.

## Cambios y alcance

`ProductService.visibleCatalog()` concentra la seleccion que ya usaba el listado: estados permitidos, exclusiones por moderacion y tiendas que no operan comercialmente. `getAllProducts()` conserva los mismos criterios y la misma consulta `findCatalog`; no se cambiaron las reglas del catalogo.

`ProductService.getRecommendationCandidates()` reutiliza esa seleccion y conserva la condicion adicional de recomendaciones: producto activo y stock mayor que cero. `RecommendationService.recommend()` obtiene esos candidatos antes de resolver recomendaciones generales, personalizadas o fallback. No se envian candidatos excluidos al proveedor.

Se conserva la comprobacion de los IDs devueltos por IA contra el mapa de candidatos permitidos. Un ID excluido no se muestra aunque exista en la base. Se mantienen `GENERAL_NO_HISTORY`, `GENERAL_NO_API_KEY`, `GENERAL_AI_EMPTY`, `GENERAL_AI_UNAVAILABLE` y `NO_PRODUCTS`.

Los contratos externos no se modificaron ni deshabilitaron. En `RecommendationServiceTests` solo se adaptaron constructor y mocks a la nueva dependencia, manteniendo las aserciones. Se agregaron seis escenarios a `RecommendationFlowIntegrationTests`: tienda suspendida, ocultacion temporal, retiro por moderacion, retiro por vendedor, borrador y falta de stock. Cada escenario comprueba recomendaciones generales, la peticion a WireMock sin el candidato excluido, IDs excluidos devueltos por IA, fallback por respuesta no permitida, fallo del proveedor y `NO_PRODUCTS` sin llamada adicional.

## Regresiones ejecutadas

| Suite | Pruebas aprobadas |
| --- | ---: |
| `BusinessApiIntegrationTests` | 39 |
| `ProductDetailIntegrationTests` | 3 |
| `SellerProductTests` | 17 |
| `RecommendationServiceTests` | 6 |
| `GeminiRecommendationClientTests` | 10 |
| `RecommendationFlowIntegrationTests` | 13 |
| `RecommendationWithoutKeyIntegrationTests` | 1 |
| `ContentModerationStateServiceTests` | 3 |
| `StoreSuspensionIntegrationTests` | 15 |

## Comandos ejecutados

Desde el repositorio de pruebas, indicando el JDK 21 instalado:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 2 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
.\scripts\test-cu01-cu06.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

Los scripts se invocaron con PowerShell `-NoProfile -ExecutionPolicy Bypass -File`. Se uso Windows-ROOT como almacen de confianza Java para Maven, sin desactivar la validacion TLS.

Desde `Transformers-AS/backend/demo`, con `JAVA_HOME` apuntando a JDK 21:

```powershell
mvn.cmd '-Dtest=RecommendationServiceTests,RecommendationFlowIntegrationTests,RecommendationWithoutKeyIntegrationTests,GeminiRecommendationClientTests,ProductDetailIntegrationTests,SellerProductTests,StoreSuspensionIntegrationTests,ContentModerationStateServiceTests,BusinessApiIntegrationTests' test
```

## Incidencias y evidencias

El primer intento conjunto no pudo detectar Docker desde Testcontainers y termino con **13 errores de inicializacion de suites**, antes de ejecutar los 109 escenarios. No se considero aprobado. Las regresiones tambien necesitaron un reintento automatico de arranque de MySQL temporal. Se dejo terminar la regresion y se repitio la suite completa sin otra suite concurrente. Los resultados finales anteriores pertenecen a ejecuciones completadas correctamente; no se modificaron pruebas para evitar los errores.

Evidencias locales en `target/cu01-cu06-validation/evidence-rf143/`:

- `cu02/`: log y XML/TXT de las 17 pruebas independientes.
- `cu01-cu06/`: log y XML/TXT de las 109 pruebas conjuntas.
- `backend/`: log y reportes de las nueve suites de regresion (107 pruebas).
- `initial-docker-error/`: log y reportes del intento fallido de infraestructura.
- `resultados.json`: resultados por suite, base/rama y SHA-256 de los dos archivos de produccion corregidos.

El script tambien genero JaCoCo en `target/cu01-cu06-validation/app/target/site/jacoco/`. Generar ese reporte no certifica un porcentaje ni sustituye el resultado funcional. No se ejecuto una nueva medicion de cobertura global ni E2E de navegador.

`target/` esta ignorado por Git. Este informe documenta resultados locales, no una ejecucion de GitHub Actions.

## Estado y pendientes

### Revalidación independiente del commit corregido

El 14 de noviembre de 2026 se repitieron las 13 suites conjuntamente contra
`bcfbac47`: **109 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**.
Maven terminó con `BUILD SUCCESS` a las **17:03:44, hora de Bogotá**.
`jacoco:report` también terminó correctamente. No se modificaron las pruebas
ni el backend durante esta ejecución.

En esta revalidación Windows bloqueó la ejecución del archivo `.ps1` por su
política de scripts. Se copiaron las fuentes y pruebas a la carpeta temporal
y se ejecutaron directamente `mvnw.cmd clean test` con los seis patrones
`Cu01*IntegrationTests` a `Cu06*IntegrationTests`, y `mvnw.cmd jacoco:report`,
con JDK 21. No se modificó la política de Windows.

La evidencia de esta ejecución está en
`target/cu01-cu06-validation/evidence-rf143-commit-bcfbac47/`: reportes Surefire,
`rf143-revalidation.log`, `rf143-jacoco.log` y `resultados.json`.
Las regresiones adicionales indicadas anteriormente pertenecen a la validación
previa; no se volvieron a ejecutar en esta revalidación de las 109 pruebas.

La matriz y el documento de brechas ahora registran RF-143 como corregido en
esta rama. El workflow sigue descargando `main` de `Transformers-AS`: la
corrección debe fusionarse primero en el backend para que CI use esa versión.

Los dos fallos de RF-143 estan resueltos en las pruebas ejecutadas. Esto **no completa todos los pendientes del Excel**, ni equivale a ejecutar toda la suite global del backend. Los pendientes ajenos a esta correccion siguen en la [matriz](trazabilidad-cu01-cu06.md) y las [brechas](brechas-cu01-cu06.md); se actualizo el estado de RF-143 sin dar por resueltos los demas pendientes.

Los cambios quedan locales para revision, sin push ni merge a main. Se conservaron los cambios existentes del repositorio de pruebas.
