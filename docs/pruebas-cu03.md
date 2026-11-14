# CU03 — Preparar el carrito y completar una compra

## Archivos de pruebas

- [Cu03CartRequirementsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu03CartRequirementsIntegrationTests.java) — paquete `com.transformersas.marketplace.external`.
- [Cu03CheckoutIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu03CheckoutIntegrationTests.java) — paquete `com.transformersas.marketplace.external`.
- [Cu03GatewayRequirementsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu03GatewayRequirementsIntegrationTests.java) — paquete `com.transformersas.marketplace.payments.infrastructure.mercadopago`.

Las suites base usan MockMvc y MySQL Testcontainers. Las ampliadas combinan HTTP interno y servicios reales, con persistencia y transacciones reales. Los paquetes se conservan para acceder a los colaboradores de cada módulo.

## Qué comprueban

Carrito y cantidades, persistencia entre sesiones, precio vigente, dirección propia, cupón, exclusión de productos inactivos, competencia por stock, vencimiento, pago aprobado/rechazado/pendiente, snapshots, webhook firmado, idempotencia, consulta de pago fallida y reintento, cancelación de checkout y comprobante PDF privado.

Los requisitos y métodos exactos están en la [matriz de trazabilidad](trazabilidad-cu01-cu06.md#cu03).

## Ejecución individual

Desde PowerShell, en la raíz del repositorio, con Java 21 y Docker Desktop iniciado:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 3
```

El script actualiza la copia temporal de la aplicación y los tests, ejecuta **todas las suites de este CU** y genera JaCoCo. Para otro backend local o JDK:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 3 -BackendPath '../Transformers-AS/backend/demo' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

No se necesita iniciar manualmente el frontend, backend ni MySQL. El código de salida es distinto de cero si falla una prueba; el reporte de cobertura no convierte un fallo en aprobación.

## Evidencia

Resultados en `target/cu01-cu06-validation/app/target/surefire-reports/`, un TXT y XML por suite. El nombre del reporte incluye el paquete de la clase, que puede ser distinto de `external`. Consulta el [resultado de la validación ampliada](validacion-cu01-cu06.md).

La ejecución anterior de 23 pruebas era la suite inicial; no demuestra por sí sola que la ampliación está aprobada.

## Alcance

Mercado Pago se sustituye en el límite externo. No se hacen cobros ni llamadas al proveedor real. No hay selección de variante en AddCartItemRequest. La devolución financiera tardía y recuperación de un fallo interno después del pago no quedan certificadas.

Consulta las [brechas documentadas](brechas-cu01-cu06.md) antes de afirmar cumplimiento total.
