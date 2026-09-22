package com.transformersas.integration.logistics;

import com.transformersas.integration.support.LogisticsProvider;
import com.transformersas.integration.support.MarketplaceScenario;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CU-24: el marketplace procesa el seguimiento logístico de un pedido. El servicio logístico informa por el webhook
 * firmado y solo él mueve los estados de transporte; comprador y vendedor únicamente consultan. Se comprueba sobre el
 * sistema desplegado que cada actualización queda registrada una sola vez, en orden, y que el estado del pedido la
 * sigue.
 */
@DisplayName("CU-24 Procesar el seguimiento logístico de pedidos")
class OrderTrackingIntegrationTest extends MarketplaceScenario {

    @Test
    @DisplayName("Recogida, tránsito y entrega avanzan el pedido y quedan en su línea de tiempo")
    void theProviderMovesTheOrderFromPickupToDelivery() {
        Dispatched order = dispatchedOrder();

        // Antes de que el proveedor informe nada, hay envío que seguir pero ninguna novedad de transporte.
        buyerTracking(order.orderId()).then()
                .body("status", equalTo("READY_FOR_DISPATCH"))
                .body("trackingCode", equalTo(order.trackingCode()))
                .body("tracking", equalTo(true))
                .body("lastPollFailed", equalTo(false))
                .body("events", org.hamcrest.Matchers.empty());

        report(order, "PICKED_UP");
        assertEquals("PICKED_UP", buyerOrderStatus(order.orderId()));

        report(order, "IN_TRANSIT");
        assertEquals("IN_TRANSIT", buyerOrderStatus(order.orderId()));

        report(order, "DELIVERED");

        Response tracking = buyerTracking(order.orderId());
        tracking.then()
                .body("status", equalTo("DELIVERED"))
                // La entrega cierra el seguimiento: el marketplace deja de consultar al servicio logístico.
                .body("tracking", equalTo(false))
                .body("events.type", contains("PICKED_UP", "IN_TRANSIT", "DELIVERED"))
                .body("events.outcome", everyItem(equalTo("APPLIED")))
                .body("events.source", everyItem(equalTo("WEBHOOK")))
                .body("deliveredAt", notNullValue())
                // Paso 13: la constancia de entrega llega con la actualización que cerró el envío.
                .body("deliveryEvidence.type", equalTo("SIGNATURE"))
                .body("deliveryEvidence.reference", notNullValue());

        // Los eventos van en orden cronológico, no en el de llegada.
        assertEquals(timeline(tracking), timeline(sellerTracking(order.orderId())));
    }

    @Test
    @DisplayName("Reenviar la misma actualización responde DUPLICATE y no la registra dos veces")
    void resendingTheSameEventChangesNothing() {
        Dispatched order = dispatchedOrder();
        String eventId = LogisticsProvider.newEventId();

        provider.shipment(eventId, order.providerShipmentId(), order.trackingCode(), "PICKED_UP")
                .then().statusCode(200).body("result", equalTo("APPLIED"));

        // El proveedor real reintenta cuando no está seguro de que su envío llegó: el mismo id no debe aplicarse otra vez.
        provider.shipment(eventId, order.providerShipmentId(), order.trackingCode(), "PICKED_UP")
                .then().statusCode(200).body("result", equalTo("DUPLICATE"));

        buyerTracking(order.orderId()).then()
                .body("status", equalTo("PICKED_UP"))
                .body("events.type", contains("PICKED_UP"));
    }

    @Test
    @DisplayName("Una actualización atrasada no retrocede el estado: queda como OUT_OF_ORDER")
    void aLateUpdateNeverMovesTheOrderBackwards() {
        Dispatched order = dispatchedOrder();
        report(order, "PICKED_UP", "IN_TRANSIT", "DELIVERED");

        // La red del proveedor entrega tarde un evento anterior; el pedido ya está entregado.
        provider.shipment(order.providerShipmentId(), order.trackingCode(), "PICKED_UP")
                .then().statusCode(200).body("result", equalTo("OUT_OF_ORDER"));

        assertEquals("DELIVERED", buyerOrderStatus(order.orderId()));
        // Se conserva para trazabilidad, marcada, al final de la línea de tiempo.
        buyerTracking(order.orderId()).then()
                .body("status", equalTo("DELIVERED"))
                .body("events.size()", equalTo(4))
                .body("events[3].type", equalTo("PICKED_UP"))
                .body("events[3].outcome", equalTo("OUT_OF_ORDER"));
    }

    @Test
    @DisplayName("Una novedad de entrega y su reintento quedan registrados hasta la entrega final")
    void aDeliveryExceptionIsRecordedUntilTheOrderArrives() {
        Dispatched order = dispatchedOrder();

        report(order, "PICKED_UP", "IN_TRANSIT", "DELIVERY_EXCEPTION");
        buyerTracking(order.orderId()).then()
                .body("status", equalTo("DELIVERY_EXCEPTION"))
                .body("tracking", equalTo(true));

        report(order, "DELIVERY_ATTEMPT_FAILED");
        // El nuevo intento programado informa sin cambiar el estado: se guarda como información adicional.
        provider.shipment(order.providerShipmentId(), order.trackingCode(), "NEXT_ATTEMPT_SCHEDULED")
                .then().statusCode(200).body("result", oneOf("APPLIED", "RECORDED"));

        report(order, "DELIVERED");
        buyerTracking(order.orderId()).then()
                .body("status", equalTo("DELIVERED"))
                .body("tracking", equalTo(false))
                .body("events.type", contains("PICKED_UP", "IN_TRANSIT", "DELIVERY_EXCEPTION",
                        "DELIVERY_ATTEMPT_FAILED", "NEXT_ATTEMPT_SCHEDULED", "DELIVERED"));
    }

    @Test
    @DisplayName("«Actualizar seguimiento» consulta al proveedor y limita la frecuencia de las consultas")
    void refreshAsksTheProviderAndIsThrottled() {
        Dispatched order = dispatchedOrder();
        report(order, "PICKED_UP");

        // El proveedor simulado no tiene novedades pendientes: la consulta se hace y no cambia nada.
        buyer.post("/api/orders/" + order.orderId() + "/tracking/refresh").then().statusCode(200)
                .body("refresh", oneOf("UPDATED", "NO_CHANGES", "UNAVAILABLE"))
                .body("status", equalTo("PICKED_UP"));

        // Inmediatamente después se rechaza por frecuencia, sin volver a llamar al servicio logístico.
        buyer.post("/api/orders/" + order.orderId() + "/tracking/refresh").then().statusCode(200)
                .body("refresh", equalTo("THROTTLED"))
                .body("status", equalTo("PICKED_UP"));

        // El vendedor tiene su propio endpoint y ve el mismo seguimiento.
        seller.post("/api/seller/orders/" + order.orderId() + "/tracking/refresh").then().statusCode(200)
                .body("refresh", notNullValue());
    }

    @Test
    @DisplayName("El seguimiento no permite fijar un estado a mano: un cuerpo con estado es 400")
    void nobodyCanSetTheStateByHand() {
        Dispatched order = dispatchedOrder();

        // Solo el servicio logístico mueve el transporte: «actualizar» admite el cuerpo vacío o {}, nada más.
        buyer.post("/api/orders/" + order.orderId() + "/tracking/refresh", "{\"status\":\"DELIVERED\"}")
                .then().statusCode(400);
        buyer.post("/api/orders/" + order.orderId() + "/tracking/refresh", "{}").then().statusCode(200);

        assertEquals("READY_FOR_DISPATCH", buyerOrderStatus(order.orderId()));
    }

    @Test
    @DisplayName("El seguimiento de un pedido ajeno no existe: 404 ORDER_NOT_FOUND")
    void trackingOfSomebodyElsesOrderIsNotFound() {
        Dispatched order = dispatchedOrder();

        buyer.get("/api/orders/999999/tracking").then().statusCode(404)
                .body("code", equalTo("ORDER_NOT_FOUND"));
        seller.get("/api/seller/orders/999999/tracking").then().statusCode(404)
                .body("code", equalTo("ORDER_NOT_FOUND"));
        // Un vendedor no puede consultar el seguimiento a nombre de una tienda que no es suya (CU-18).
        seller.get("/api/seller/orders/" + order.orderId() + "/tracking", 999L).then().statusCode(403)
                .body("code", equalTo("STORE_NOT_AUTHORIZED"));
    }

    @Test
    @DisplayName("Un pedido sin envío responde el seguimiento vacío, no un error")
    void anOrderWithoutShipmentHasAnEmptyTracking() {
        long orderId = confirmedOrder();

        buyerTracking(orderId).then()
                .body("status", equalTo("CONFIRMED"))
                .body("trackingCode", nullValue())
                .body("tracking", equalTo(false))
                .body("events", org.hamcrest.Matchers.empty());
    }

    @Test
    @DisplayName("El pedido devuelto al vendedor cierra el seguimiento")
    void anOrderReturnedToTheSellerClosesTheTracking() {
        Dispatched order = dispatchedOrder();
        report(order, "PICKED_UP", "IN_TRANSIT", "DELIVERY_EXCEPTION", "RETURNED_TO_SELLER");

        assertEquals("RETURNED_TO_SELLER", buyerOrderStatus(order.orderId()));
        sellerTracking(order.orderId()).then()
                .body("status", equalTo("RETURNED_TO_SELLER"))
                .body("tracking", equalTo(false))
                .body("events.size()", greaterThan(0));
    }
}
