# Brechas respecto a CU_Marketplace.xlsx

Esta revisión contrasta el Excel con el backend `263bd0eaa5ddd585ebe3c398b1ec9f6fb004cf67`
y las suites de este repositorio. Una prueba aprobada no demuestra que todo un
caso de uso esté implementado. La [matriz](trazabilidad-cu01-cu06.md) distingue
lo comprobado de lo parcial o pendiente.

## Recomendaciones y visibilidad — RF-143

**Resuelto en la rama `fix/rf143-recommendation-visibility`, commit `bcfbac47`.**
Los dos contratos de aceptación aprobaron en la revalidación local. El texto
siguiente describe el defecto de la versión anterior `263bd0e`; no es una
brecha vigente de la rama corregida. La corrección debe integrarse en el backend
usado por CI para que ese entorno obtenga el mismo resultado.

`RecommendationService.recommend` obtiene candidatos con
`ProductRepository.findByActiveTrueAndStockGreaterThan(0)`. La consulta no aplica
la visibilidad de moderación ni el estado de la tienda. Se añadieron dos
contratos en `Cu02VisibilityContractIntegrationTests`:

- `moderatedProductsMustNotBeRecommended`: el detalle de un producto oculto debe
  responder 404 y ese ID tampoco debe estar en recomendaciones.
- `suspendedStoreProductsMustNotBeRecommended`: una tienda suspendida no debe
  aportar candidatos a recomendaciones.

Estas aserciones expresan el Excel y no el comportamiento incorrecto actual.
No están deshabilitadas y se incluyen en el workflow. Si fallan, corresponde
corregir la aplicación y repetirlas; eliminar esas aserciones no resuelve RF-143.

## Catálogo y comparación — CU01

El endpoint `GET /api/products` devuelve el catálogo, mientras que los filtros
y la comparación están en el frontend. Las pruebas MockMvc no demuestran sus
interacciones. La revisión del frontend identifica además puntos que deben
resolverse antes de afirmar cumplimiento total:

- `catalogo-filtros.ts` busca sobre nombre, descripción y categoría; no incluye
  la marca que exige RF-002.
- El mismo archivo implementa orden por precio, nombre y recientes; no prueba
  valoración/relevancia de RF-003.
- La comparación en `app.component.html` muestra filas fijas. RF-129 pide que
  el comprador seleccione los criterios; se necesita verificar e implementar
  ese control si no existe en otra vista.
- Paginación/carga progresiva, comparación vigente, selección de alternativas
  y estados de error necesitan pruebas del frontend con datos controlados.

No se añadieron falsos parámetros de búsqueda al endpoint para hacer pasar una
prueba de una funcionalidad que se ejecuta en el navegador.

## Variantes y ciclo financiero — CU03

`AddCartItemRequest` solo recibe `productId` y `quantity`. No existe un campo de
variante en ese contrato, aunque RF-006 pide comprar una variante específica.
Hace falta alinear carrito, reservas y pedido con ese requisito en la aplicación.

Las pruebas ampliadas verifican snapshots, idempotencia, webhook firmado,
consulta fallida y reintento, comprobante privado y cancelación del checkout.
La pasarela se sustituye en el límite externo: no certifican una transacción de
Mercado Pago sandbox real ni su disponibilidad.

La liberación por pago rechazado de la suite inicial usa el modo simulado
legado. No debe extrapolarse a Mercado Pago: `materialize` registra un rechazo,
pero esa rama no llama directamente a `releaseReservations`. La aprobación
tardía termina en `REVIEW`; eso evita crear un pedido, pero no demuestra una
resolución financiera ni el cumplimiento completo de RF-020.

Falta verificar con contratos específicos el error durante la creación inicial
en pasarela, el fallo interno posterior a la aprobación, su recuperación segura
y los mensajes visibles de cambios de precio, cupón inválido y resultado del pago.

## Límites de la evidencia ampliada

- IA: WireMock permite verificar contexto, respuestas y fallback; no demuestra
  calidad semántica de recomendaciones de un modelo real.
- Alertas: se evalúan servicios reales y un gateway externo simulado; no se
  comprueba la entrega real de correo/push ni el disparo del scheduler.
- Reseñas: las compras entregadas se preparan en MySQL; la suite de compra es
  independiente. Los reportes y la visibilidad sí se ejercitan en el backend.
- Direcciones: las restricciones históricas y la unicidad se prueban con MySQL,
  pero confirmaciones y conservación del formulario tras errores requieren UI.
- Los RNF del Excel necesitan mediciones y criterios específicos. JaCoCo no
  mide usabilidad, accesibilidad, calidad de IA ni cumplimiento funcional.

Este cambio modifica pruebas y documentación en este repositorio. No modifica
el código de producción de `Transformers-AS`. El resultado real de las suites
ampliadas se registra en [validacion-cu01-cu06.md](validacion-cu01-cu06.md).
