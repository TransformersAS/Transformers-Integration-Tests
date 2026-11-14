# CU02 — Recibir recomendaciones personalizadas

## Archivo de pruebas

[Cu02RecommendationIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu02RecommendationIntegrationTests.java)
contiene **6 pruebas**. Utiliza Spring Boot, MockMvc, sesiones reales, CSRF,
Flyway y MySQL Testcontainers mediante el helper del backend. Las respuestas del
backend no se sustituyen por mocks.

## Qué comprueba

- Registrar una búsqueda y consultarla por HTTP.
- Registrar una visualización de un producto existente.
- Rechazar interacciones inválidas sin persistirlas.
- Sin historial: devolver cinco productos disponibles ordenados por stock.
- Con historial y sin clave Gemini: utilizar el fallback seguro.
- Sin productos disponibles: devolver la estrategia NO_PRODUCTS.

## Cómo ejecutar solamente CU02

Requiere Java 21 y Docker Desktop con contenedores Linux. Primero prepara la
copia temporal del backend con los pasos de la [guía compartida](pruebas-cu01-cu06.md#ejecución-local-en-windows).
Desde la raíz de este repositorio, copia la versión actual de esta suite y ejecútala:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot' # Ajustar a tu JDK 21.
Copy-Item backend-tests/src/test/java/com/transformersas/marketplace/external/Cu02RecommendationIntegrationTests.java target/cu01-cu06-validation/app/src/test/java/com/transformersas/marketplace/external/
Push-Location target/cu01-cu06-validation/app
try {
    .\mvnw.cmd test '-Dtest=Cu02RecommendationIntegrationTests'
} finally {
    Pop-Location
}
```

El resultado esperado es `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
y `BUILD SUCCESS`.

## Evidencia

El reporte de esta suite queda en
`target/cu01-cu06-validation/app/target/surefire-reports/com.transformersas.marketplace.external.Cu02RecommendationIntegrationTests.txt`.

En la ejecución conjunta del 14 de noviembre de 2026 esta suite aprobó sus
**6 pruebas**, sin fallos, errores ni omitidas, contra el backend
`263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`, Java 21 y MySQL 8.4.11.
Es evidencia local; no representa una ejecución de GitHub Actions.

## Alcance

Gemini se configura sin API key: se comprueba el fallback, sin invocar el proveedor real.

Los resultados demuestran los escenarios descritos, sin implicar cobertura total
del caso de uso. Consulta la [guía compartida](pruebas-cu01-cu06.md) para el workflow
y el reporte JaCoCo de la suite conjunta.
