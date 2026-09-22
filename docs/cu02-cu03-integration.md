# CU-02 y CU-03 — pruebas de integración

Estas pruebas **viven en este repositorio separado**, pero durante GitHub Actions se copian temporalmente al `src/test/java` del backend real para poder usar el contexto de Spring, Testcontainers MySQL y JaCoCo del Marketplace.

No se copia código de producción a este repositorio.

## Flujo automático

```text
push al repositorio de pruebas
        ↓
GitHub Actions
        ↓
checkout Transformers-Integration-Tests
        ↓
checkout Transformers-AS (main)
        ↓
copia temporal de los tests CU-02/CU-03
        ↓
Spring Boot Test + MockMvc
        ↓
MySQL 8.4.11 Testcontainers + Flyway
        ↓
HTTP → Controller → Service/UseCase → Repository → MySQL
        ↓
JaCoCo + reportes Surefire
        ↓
PASS / FAIL
```

No hay que abrir Workbench, iniciar MySQL, levantar Spring ni presionar Run manualmente para que el pipeline valide estas pruebas.

## CU-02

`Cu02RecommendationIntegrationTests` cubre:

- registrar y consultar una búsqueda;
- registrar una visualización de producto;
- rechazar interacciones inválidas sin persistir datos;
- recomendaciones generales para usuario sin historial;
- fallback cuando Gemini no tiene API key;
- respuesta `NO_PRODUCTS` cuando no existen productos disponibles.

## CU-03

`Cu03CheckoutIntegrationTests` cubre:

- CRUD del carrito por HTTP;
- preview de checkout y cupón;
- pago aprobado, creación de pedido, descuento de stock y vaciado del carrito;
- registro de interacción `PURCHASE`;
- pago rechazado sin pedido ni descuento de stock;
- pago pendiente conservando la reserva;
- rechazo de carrito multitienda antes del pago;
- rechazo por stock insuficiente.

## Cobertura 100 %

El workflow genera un reporte JaCoCo a partir de esta suite para que el porcentaje se pueda demostrar con evidencia. No se debe afirmar que existe 100 % hasta revisar el reporte resultante. El `pom.xml` del Marketplace actualmente mantiene además su gate global de cobertura para la suite completa.
