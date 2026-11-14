# CU02 — Recibir recomendaciones personalizadas

## Archivos de pruebas

- [Cu02AiRequirementsIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu02AiRequirementsIntegrationTests.java) — paquete `com.transformersas.marketplace.recommendation.integration`.
- [Cu02RecommendationIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu02RecommendationIntegrationTests.java) — paquete `com.transformersas.marketplace.external`.
- [Cu02VisibilityContractIntegrationTests.java](../backend-tests/src/test/java/com/transformersas/marketplace/external/Cu02VisibilityContractIntegrationTests.java) — paquete `com.transformersas.marketplace.external`.

Las suites base usan MockMvc y MySQL Testcontainers. Las ampliadas combinan HTTP interno y servicios reales, con persistencia y transacciones reales. Los paquetes se conservan para acceder a los colaboradores de cada módulo.

## Qué comprueban

Interacciones persistidas y privadas, contexto enviado al adaptador de IA, nuevas interacciones, fallback sin historial/sin clave/con errores 429 y 500, IDs desconocidos y duplicados, y contratos de exclusión por moderación y suspensión.

Los requisitos y métodos exactos están en la [matriz de trazabilidad](trazabilidad-cu01-cu06.md#cu02).

## Ejecución individual

Desde PowerShell, en la raíz del repositorio, con Java 21 y Docker Desktop iniciado:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 2
```

El script actualiza la copia temporal de la aplicación y los tests, ejecuta **todas las suites de este CU** y genera JaCoCo. Para otro backend local o JDK:

```powershell
.\scripts\test-cu01-cu06.ps1 -Case 2 -BackendPath '../Transformers-AS/backend/demo' -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
```

No se necesita iniciar manualmente el frontend, backend ni MySQL. El código de salida es distinto de cero si falla una prueba; el reporte de cobertura no convierte un fallo en aprobación.

## Evidencia

Resultados en `target/cu01-cu06-validation/app/target/surefire-reports/`, un TXT y XML por suite. El nombre del reporte incluye el paquete de la clase, que puede ser distinto de `external`. Consulta el [resultado de la validación ampliada](validacion-cu01-cu06.md).

La ejecución anterior de 23 pruebas era la suite inicial; no demuestra por sí sola que la ampliación está aprobada.

## Alcance

Gemini se sustituye por WireMock: se comprueba el protocolo y el contexto, no la calidad de un modelo real. Los contratos de visibilidad pueden fallar por incumplimientos del backend.

Consulta las [brechas documentadas](brechas-cu01-cu06.md) antes de afirmar cumplimiento total.
