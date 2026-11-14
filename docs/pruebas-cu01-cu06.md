# Pruebas de integración CU01 a CU06

Las suites viven en `backend-tests/src/test/java/com/transformersas/marketplace/external/`.
Se ejecutan contra Spring Boot con MockMvc, sesiones reales, CSRF, Flyway y MySQL
Testcontainers. Heredan `AbstractIntegrationTest` del backend; no usan mocks de
controllers, servicios ni repositorios de negocio. Solo se simulan proveedores
externos. Los datos se preparan en MySQL efímero y se ejercitan peticiones MockMvc
y llamadas a servicios reales según el escenario.

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
Descarga `TransformersAS/Transformers-AS` desde `main`, copia las suites al
directorio de pruebas de la aplicación y ejecuta Maven. La implementación y el
helper compartido deben existir en ese `main`.

El artifact `cu01-cu06-integration-<sha>` conserva los resultados Surefire y el
reporte JaCoCo. El reporte mide la ejecución de estas suites; no implica por sí
solo 100 % de cobertura ni cobertura completa de las reglas de cada caso.

## Ejecución local en Windows

Requisitos: Java 21 y Docker Desktop con contenedores Linux.
El script prepara una copia temporal del backend y siempre copia los tests actuales.
Desde la raíz del repositorio:

```powershell
# Todas las suites, incluidos los contratos de aceptación.
.\scripts\test-cu01-cu06.ps1

# Solo un caso, o varios casos.
.\scripts\test-cu01-cu06.ps1 -Case 5
.\scripts\test-cu01-cu06.ps1 -Case 2,3
```

Usa `-BackendPath` si la aplicación no está en `../Transformers-AS/backend/demo`,
y `-JavaHome` si necesitas indicar explícitamente tu JDK 21. El backend original
no se modifica. MySQL es temporal; no se usa una base de producción.

Los resultados están en `target/cu01-cu06-validation/app/target/surefire-reports/`
y JaCoCo en `target/cu01-cu06-validation/app/target/site/jacoco/index.html`.
Los contratos que revelen incumplimientos fallan normalmente: no se omiten ni
se convierten en aprobados. El código de salida de Maven y el script preservan ese fallo.

El Maven de la raíz ejecuta otra suite y no descubre `backend-tests/`.
El workflow usa `Cu01*IntegrationTests` hasta `Cu06*IntegrationTests`, de modo que
incluye automáticamente las suites ampliadas. Genera JaCoCo también si fallan
los contratos y conserva Surefire como evidencia. JaCoCo describe código ejecutado,
no cumplimiento funcional.

## Revisión contra el Excel

- [Trazabilidad de los 52 requisitos](trazabilidad-cu01-cu06.md).
- [Brechas y límites](brechas-cu01-cu06.md).
- [Resultado de la validación ampliada](validacion-cu01-cu06.md).

## Límites

Estas pruebas no recorren el navegador. CU01 valida el listado y detalle del backend;
los controles de búsqueda, filtros y comparación siguen fuera de esta suite.
CU04 evalúa alertas reales con un gateway externo simulado. CU05 prepara una
compra entregada en MySQL; CU03 cubre el flujo de compra. Las suites de IA y
pasarela simulan únicamente el proveedor externo. El alcance está en la matriz;
no se afirma 100 % de cumplimiento.

## Validación histórica de la suite inicial

El 14 de noviembre de 2026 se ejecutaron las seis suites contra una copia sin
cambios del backend en el commit `263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`, con
Java 21, Spring Boot y MySQL 8.4.11 Testcontainers. Resultado: **23 pruebas,
0 fallos, 0 errores, 0 omitidas** (CU01: 3, CU02: 6, CU03: 6, CU04: 3,
CU05: 2 y CU06: 3). `jacoco:report` también terminó con `BUILD SUCCESS`.
Los reportes se generaron en las rutas locales indicadas arriba, ignoradas por
Git. Esta validación no equivale a una ejecución de GitHub Actions.
