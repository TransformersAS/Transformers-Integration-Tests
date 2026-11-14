# CU04 — Dar seguimiento a productos de interés

## Archivos de pruebas

- [Cu04AlertRequirementsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu04AlertRequirementsIntegrationTests.java) — paquete `com.transformersas.marketplace.favorites`.
- [Cu04ProductTrackingIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu04ProductTrackingIntegrationTests.java) — paquete `com.transformersas.marketplace.external`.

Las suites base usan MockMvc y MySQL Testcontainers. Las ampliadas combinan HTTP interno y servicios reales, con persistencia y transacciones reales. Los paquetes se conservan para acceder a los colaboradores de cada módulo.

## Qué comprueban

Favoritos actualizados y privados, alertas de precio/disponibilidad de la variante exacta, edición y estados, condición ya satisfecha, ausencia de avisos para alertas inactivas, notificación interna, fallo externo, rollback y evaluadores concurrentes sin duplicados.

Los requisitos y métodos exactos están en la [matriz de trazabilidad](trazabilidad-cu01-cu06.md#cu04).

## Ejecución individual

Desde PowerShell, en la raíz del repositorio, con Java 21 y Docker Desktop iniciado:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 4
```

El script actualiza la copia temporal de la aplicación y los tests, ejecuta **todas las suites de este CU** y genera JaCoCo. Para otro backend local o JDK:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 4 -BackendPath '../Transformers-AS/backend/demo' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

No se necesita iniciar manualmente el frontend, backend ni MySQL. El código de salida es distinto de cero si falla una prueba; el reporte de cobertura no convierte un fallo en aprobación.

## Evidencia

Resultados en `target/cu01-cu06-validation/app/target/surefire-reports/`, un TXT y XML por suite. El nombre del reporte incluye el paquete de la clase, que puede ser distinto de `external`. Consulta el [resultado de la validación ampliada](validacion-cu01-cu06.md).

La ejecución anterior de 23 pruebas era la suite inicial; no demuestra por sí sola que la ampliación está aprobada.

## Alcance

Se invoca explícitamente la evaluación real de alertas; no se espera el scheduler. Se usa el gateway externo simulado. Mover a carrito, comparar y compartir desde la interfaz siguen pendientes.

Consulta las [brechas documentadas](brechas-cu01-cu06.md) antes de afirmar cumplimiento total.
