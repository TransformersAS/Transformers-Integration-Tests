# CU05 — Publicar y atender reseñas de productos comprados

## Archivo de pruebas

[Cu05ProductReviewsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu05ProductReviewsIntegrationTests.java)
contiene **2 pruebas**. Utiliza Spring Boot, MockMvc, sesiones reales, CSRF,
Flyway y MySQL Testcontainers mediante el helper del backend. Las respuestas del
backend no se sustituyen por mocks.

## Qué comprueba

- Publicar reseña de compra entregada, responder como vendedor, editar, verificar promedio público y eliminar; impedir eliminación ajena.
- Rechazar calificación inválida, compra ajena y pedido sin entregar, sin guardar reseñas.

## Cómo ejecutar solamente CU05

Requiere Java 21 y Docker Desktop con contenedores Linux. Primero prepara la
copia temporal del backend con los pasos de la [guía compartida](pruebas-cu01-cu06.md#ejecución-local-en-windows).
Desde la raíz de este repositorio, copia la versión actual de esta suite y ejecútala:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot' # Ajustar a tu JDK 21.
Copy-Item backend-tests/src/test/java/com/transformersas/marketplace/external/Cu05ProductReviewsIntegrationTests.java target/cu01-cu06-validation/app/src/test/java/com/transformersas/marketplace/external/
Push-Location target/cu01-cu06-validation/app
try {
    .\mvnw.cmd test '-Dtest=Cu05ProductReviewsIntegrationTests'
} finally {
    Pop-Location
}
```

El resultado esperado es `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
y `BUILD SUCCESS`.

## Evidencia

El reporte de esta suite queda en
`target/cu01-cu06-validation/app/target/surefire-reports/com.transformersas.marketplace.external.Cu05ProductReviewsIntegrationTests.txt`.

En la ejecución conjunta del 14 de noviembre de 2026 esta suite aprobó sus
**2 pruebas**, sin fallos, errores ni omitidas, contra el backend
`263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`, Java 21 y MySQL 8.4.11.
Es evidencia local; no representa una ejecución de GitHub Actions.

## Alcance

La compra entregada se prepara en MySQL para aislar las reglas de reseñas. La compra por HTTP se prueba en CU03. No cubre imágenes de reseñas ni todas las reglas de moderación.

Los resultados demuestran los escenarios descritos, sin implicar cobertura total
del caso de uso. Consulta la [guía compartida](pruebas-cu01-cu06.md) para el workflow
y el reporte JaCoCo de la suite conjunta.
