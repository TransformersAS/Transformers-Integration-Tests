# CU05 — Publicar y atender reseñas de productos comprados

## Archivos de pruebas

- [Cu05ProductReviewsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu05ProductReviewsIntegrationTests.java) — paquete `com.transformersas.marketplace.external`.
- [Cu05ReviewRequirementsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu05ReviewRequirementsIntegrationTests.java) — paquete `com.transformersas.marketplace.reviews`.

Las suites base usan MockMvc y MySQL Testcontainers. Las ampliadas combinan HTTP interno y servicios reales, con persistencia y transacciones reales. Los paquetes se conservan para acceder a los colaboradores de cada módulo.

## Qué comprueban

Elegibilidad, solo calificación, límites del comentario, imágenes multipart válidas/inválidas/excesivas/por encima del tamaño máximo, promedio y distribución, edición/eliminación, publicación concurrente, filtros del vendedor, privacidad de la compra, respuestas y reportes del vendedor vinculados a moderación.

Los requisitos y métodos exactos están en la [matriz de trazabilidad](trazabilidad-cu01-cu06.md#cu05).

## Ejecución individual

Desde PowerShell, en la raíz del repositorio, con Java 21 y Docker Desktop iniciado:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 5
```

El script actualiza la copia temporal de la aplicación y los tests, ejecuta **todas las suites de este CU** y genera JaCoCo. Para otro backend local o JDK:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 5 -BackendPath '../Transformers-AS/backend/demo' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

No se necesita iniciar manualmente el frontend, backend ni MySQL. El código de salida es distinto de cero si falla una prueba; el reporte de cobertura no convierte un fallo en aprobación.

## Evidencia

Resultados en `target/cu01-cu06-validation/app/target/surefire-reports/`, un TXT y XML por suite. El nombre del reporte incluye el paquete de la clase, que puede ser distinto de `external`. Consulta el [resultado de la validación ampliada](validacion-cu01-cu06.md).

La ejecución anterior de 23 pruebas era la suite inicial; no demuestra por sí sola que la ampliación está aprobada.

## Alcance

Se prepara un pedido entregado en MySQL. Hay llamadas HTTP y a servicios reales con MySQL; no se recorre el formulario en un navegador. La compra real por HTTP se comprueba en CU03.

Consulta las [brechas documentadas](brechas-cu01-cu06.md) antes de afirmar cumplimiento total.
