# Pruebas de integración CU01 a CU06

Las seis suites viven en `backend-tests/src/test/java/com/transformersas/marketplace/external/`.
Se ejecutan contra Spring Boot con MockMvc, sesiones reales, CSRF, Flyway y MySQL
Testcontainers. Heredan `AbstractIntegrationTest` del backend; no usan mocks de
controllers, servicios ni repositorios. Los datos de preparación se insertan en
la base efímera y las operaciones probadas se realizan por HTTP con MockMvc.

## Documentación por caso de uso

Cada caso tiene su propio archivo con escenarios, comando individual y evidencia:

1. [CU01 — Explorar el catálogo y descubrir productos](pruebas-cu01.md).
2. [CU02 — Recibir recomendaciones personalizadas](pruebas-cu02.md).
3. [CU03 — Preparar el carrito y completar una compra](pruebas-cu03.md).
4. [CU04 — Dar seguimiento a productos de interés](pruebas-cu04.md).
5. [CU05 — Publicar y atender reseñas de productos comprados](pruebas-cu05.md).
6. [CU06 — Configurar direcciones de entrega](pruebas-cu06.md).

Esta guía conserva la preparación del entorno y la ejecución conjunta.

## GitHub Actions

El workflow `.github/workflows/cu02-cu03-integration.yml` conserva su nombre de
archivo y ahora aparece como **CU01-CU06 Backend Integration**. Se activa con
push a `feature/cu01-*` hasta `feature/cu06-*` o `test/cu01-cu06-*`, pull request
o Actions → CU01-CU06 Backend Integration → Run workflow.
Descarga `TransformersAS/Transformers-AS` desde `main`, copia las seis suites al
directorio de pruebas de la aplicación y ejecuta Maven. La implementación y el
helper compartido deben existir en ese `main`.

El artifact `cu01-cu06-integration-<sha>` conserva los resultados Surefire y el
reporte JaCoCo. El reporte mide la ejecución de estas suites; no implica por sí
solo 100 % de cobertura ni cobertura completa de las reglas de cada caso.

## Ejecución local en Windows

Requisitos: Java 21 y Docker Desktop iniciado con contenedores Linux.
Desde este repositorio, suponiendo el backend en la carpeta vecina, prepara una
copia temporal para no modificar el repositorio de la aplicación:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot' # Ajustar a tu JDK 21.
$app = Join-Path $PWD 'target/cu01-cu06-validation/app'
New-Item -ItemType Directory -Force $app | Out-Null
Copy-Item ../Transformers-AS/backend/demo/src,../Transformers-AS/backend/demo/.mvn -Destination $app -Recurse -Force
Copy-Item ../Transformers-AS/backend/demo/pom.xml,../Transformers-AS/backend/demo/mvnw.cmd -Destination $app
$external = Join-Path $app 'src/test/java/com/transformersas/marketplace/external'
New-Item -ItemType Directory -Force $external | Out-Null
Copy-Item backend-tests/src/test/java/com/transformersas/marketplace/external/*.java $external
Push-Location $app
try {
    .\mvnw.cmd test '-Dtest=Cu01CatalogIntegrationTests,Cu02RecommendationIntegrationTests,Cu03CheckoutIntegrationTests,Cu04ProductTrackingIntegrationTests,Cu05ProductReviewsIntegrationTests,Cu06DeliveryAddressIntegrationTests'
    if ($LASTEXITCODE -eq 0) { .\mvnw.cmd jacoco:report }
} finally {
    Pop-Location
}
```

Los resultados locales quedan en
`target/cu01-cu06-validation/app/target/surefire-reports/` y el reporte de
cobertura en `target/cu01-cu06-validation/app/target/site/jacoco/index.html`.
No hace falta arrancar manualmente MySQL, backend ni frontend.
El Maven de la raíz de este repositorio ejecuta otra suite y no descubre
las clases de `backend-tests/`.

## Límites

Estas pruebas no recorren el navegador. CU01 valida el listado y detalle que
expone el backend; búsqueda, filtros y paginación de la interfaz requieren sus
pruebas específicas. CU04 no fuerza el disparo asíncrono de notificaciones ni
comprueba proveedores externos. CU05 prepara una compra entregada directamente
en MySQL para aislar las reglas de reseñas; la compra por HTTP se prueba en CU03.

## Validación local

El 14 de noviembre de 2026 se ejecutaron las seis suites contra una copia sin
cambios del backend en el commit `263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`, con
Java 21, Spring Boot y MySQL 8.4.11 Testcontainers. Resultado: **23 pruebas,
0 fallos, 0 errores, 0 omitidas** (CU01: 3, CU02: 6, CU03: 6, CU04: 3,
CU05: 2 y CU06: 3). `jacoco:report` también terminó con `BUILD SUCCESS`.
Los reportes se generaron en las rutas locales indicadas arriba, ignoradas por
Git. Esta validación no equivale a una ejecución de GitHub Actions.
