# CU01 — Explorar el catálogo y descubrir productos

## Archivo de pruebas

[Cu01CatalogIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu01CatalogIntegrationTests.java)
contiene **3 pruebas**. Utiliza Spring Boot, MockMvc, sesiones reales, CSRF,
Flyway y MySQL Testcontainers mediante el helper del backend. Las respuestas del
backend no se sustituyen por mocks.

## Qué comprueba

- Catálogo público y ficha con descripción, imagen, variante y tienda.
- Productos retirados e inexistentes devuelven 404 y no aparecen en el catálogo.
- Catálogo vacío devuelve una lista vacía correctamente.

## Cómo ejecutar solamente CU01

Requiere Java 21 y Docker Desktop con contenedores Linux. Primero prepara la
copia temporal del backend con los pasos de la [guía compartida](pruebas-cu01-cu06.md#ejecución-local-en-windows).
Desde la raíz de este repositorio, copia la versión actual de esta suite y ejecútala:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot' # Ajustar a tu JDK 21.
Copy-Item backend-tests/src/test/java/com/transformersas/marketplace/external/Cu01CatalogIntegrationTests.java target/cu01-cu06-validation/app/src/test/java/com/transformersas/marketplace/external/
Push-Location target/cu01-cu06-validation/app
try {
    .\mvnw.cmd test '-Dtest=Cu01CatalogIntegrationTests'
} finally {
    Pop-Location
}
```

El resultado esperado es `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
y `BUILD SUCCESS`.

## Evidencia

El reporte de esta suite queda en
`target/cu01-cu06-validation/app/target/surefire-reports/com.transformersas.marketplace.external.Cu01CatalogIntegrationTests.txt`.

En la ejecución conjunta del 14 de noviembre de 2026 esta suite aprobó sus
**3 pruebas**, sin fallos, errores ni omitidas, contra el backend
`263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`, Java 21 y MySQL 8.4.11.
Es evidencia local; no representa una ejecución de GitHub Actions.

## Alcance

No cubre búsqueda, filtros ni paginación de la interfaz.

Los resultados demuestran los escenarios descritos, sin implicar cobertura total
del caso de uso. Consulta la [guía compartida](pruebas-cu01-cu06.md) para el workflow
y el reporte JaCoCo de la suite conjunta.
