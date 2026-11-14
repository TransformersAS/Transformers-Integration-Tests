# CU03 — Preparar el carrito y completar una compra

## Archivo de pruebas

[Cu03CheckoutIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu03CheckoutIntegrationTests.java)
contiene **6 pruebas**. Utiliza Spring Boot, MockMvc, sesiones reales, CSRF,
Flyway y MySQL Testcontainers mediante el helper del backend. Las respuestas del
backend no se sustituyen por mocks.

## Qué comprueba

- Agregar, actualizar, consultar y eliminar productos; calcular checkout con cupón.
- Pago aprobado: crear pedido, descontar stock, vaciar carrito y registrar PURCHASE.
- Pago rechazado: liberar reservas sin pedido ni cambio de stock.
- Pago pendiente: conservar reservas activas sin crear pedido.
- Rechazar carrito multitienda antes del pago.
- Rechazar cantidad superior al stock sin crear reservas ni pedidos.

## Cómo ejecutar solamente CU03

Requiere Java 21 y Docker Desktop con contenedores Linux. Primero prepara la
copia temporal del backend con los pasos de la [guía compartida](pruebas-cu01-cu06.md#ejecución-local-en-windows).
Desde la raíz de este repositorio, copia la versión actual de esta suite y ejecútala:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot' # Ajustar a tu JDK 21.
Copy-Item backend-tests/src/test/java/com/transformersas/marketplace/external/Cu03CheckoutIntegrationTests.java target/cu01-cu06-validation/app/src/test/java/com/transformersas/marketplace/external/
Push-Location target/cu01-cu06-validation/app
try {
    .\mvnw.cmd test '-Dtest=Cu03CheckoutIntegrationTests'
} finally {
    Pop-Location
}
```

El resultado esperado es `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`
y `BUILD SUCCESS`.

## Evidencia

El reporte de esta suite queda en
`target/cu01-cu06-validation/app/target/surefire-reports/com.transformersas.marketplace.external.Cu03CheckoutIntegrationTests.txt`.

En la ejecución conjunta del 14 de noviembre de 2026 esta suite aprobó sus
**6 pruebas**, sin fallos, errores ni omitidas, contra el backend
`263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`, Java 21 y MySQL 8.4.11.
Es evidencia local; no representa una ejecución de GitHub Actions.

## Alcance

Las direcciones de preparación pertenecen al comprador autenticado. Se prueban métodos de pago del entorno de pruebas, sin transacciones bancarias reales.

Los resultados demuestran los escenarios descritos, sin implicar cobertura total
del caso de uso. Consulta la [guía compartida](pruebas-cu01-cu06.md) para el workflow
y el reporte JaCoCo de la suite conjunta.
