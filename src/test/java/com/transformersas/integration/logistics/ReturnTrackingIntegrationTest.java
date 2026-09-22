package com.transformersas.integration.logistics;

import com.transformersas.integration.support.LogisticsProvider;
import com.transformersas.integration.support.MarketplaceScenario;
import com.transformersas.integration.support.Returns;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CU-25: el marketplace procesa el seguimiento logístico del retorno de una devolución. La devolución la solicita y
 * aprueba CU-19; al elegir el método de retorno se crea el retorno en el servicio logístico y empieza su seguimiento.
 * Se comprueba el recorrido completo sobre el sistema desplegado, incluida la entrega al vendedor, que es la que hace
 * que CU-19 abra la inspección.
 */
@DisplayName("CU-25 Procesar el seguimiento logístico de devoluciones")
class ReturnTrackingIntegrationTest extends MarketplaceScenario {

    @Test
    @DisplayName("Recogida, retorno y entrega al vendedor avanzan la devolución y abren su inspección")
    void theProviderMovesTheReturnUntilItReachesTheSeller() {
        long returnId = approvedReturn();

        // Recién registrado el retorno: recogida pendiente y ninguna recogida fallida.
        buyerReturnTracking(returnId).then()
                .body("returnId", equalTo((int) returnId))
                .body("status", equalTo("PICKUP_PENDING"))
                .body("trackingCode", equalTo("TRK-R" + returnId))
                .body("failedPickups", equalTo(0))
                .body("maxFailedPickups", equalTo(3))
                .body("pickupStopped", equalTo(false))
                .body("tracking", equalTo(true));

        provider.returned(returnId, "PICKED_UP").then().statusCode(200).body("result", equalTo("APPLIED"));
        buyerReturnTracking(returnId).then()
                .body("status", equalTo("PICKED_UP"))
                .body("pickedUpAt", notNullValue());

        provider.returned(returnId, "IN_TRANSIT").then().statusCode(200).body("result", equalTo("APPLIED"));
        buyerReturnTracking(returnId).then().body("status", equalTo("IN_RETURN"));

        provider.returned(returnId, "DELIVERED_TO_SELLER").then().statusCode(200).body("result", equalTo("APPLIED"));

        Response tracking = buyerReturnTracking(returnId);
        tracking.then()
                .body("status", equalTo("DELIVERED_TO_SELLER"))
                // La entrega al vendedor cierra el retorno: ya no hay nada que consultar.
                .body("tracking", equalTo(false))
                .body("deliveredAt", notNullValue())
                .body("events.type", hasItem("DELIVERED_TO_SELLER"))
                .body("events.source", everyItem(oneOf("WEBHOOK", "POLLING", "SYSTEM")));

        // La entrega es la señal con la que CU-19 arranca sus 24 h de inspección: es la integración entre los dos.
        assertEquals("IN_INSPECTION", Returns.status(buyer, returnId));

        // La tienda ve el mismo retorno desde su propio endpoint.
        assertEquals(timeline(tracking), timeline(sellerReturnTracking(returnId)));
    }

    @Test
    @DisplayName("Tres recogidas fallidas detienen el retorno y rechazan lo que llegue después")
    void threeFailedPickupsStopTheReturn() {
        long returnId = approvedReturn();

        provider.returned(returnId, "PICKUP_FAILED").then().statusCode(200).body("result", equalTo("APPLIED"));
        buyerReturnTracking(returnId).then()
                .body("status", equalTo("PICKUP_FAILED"))
                .body("failedPickups", equalTo(1))
                .body("pickupStopped", equalTo(false))
                .body("tracking", equalTo(true));

        provider.returned(returnId, "PICKUP_FAILED").then().statusCode(200);
        provider.returned(returnId, "PICKUP_FAILED").then().statusCode(200);

        buyerReturnTracking(returnId).then()
                .body("failedPickups", equalTo(3))
                .body("pickupStopped", equalTo(true))
                // Se deja de consultar y ya no se puede pedir otra recogida por esta vía (A1 es de CU-19).
                .body("tracking", equalTo(false))
                .body("canRequestNewPickup", equalTo(false));

        // Cualquier novedad posterior no cambia nada: solo queda anotada, porque el retorno está detenido.
        provider.returned(returnId, "PICKED_UP").then().statusCode(200)
                .body("result", equalTo("OUT_OF_ORDER"));
        buyerReturnTracking(returnId).then()
                .body("status", equalTo("PICKUP_FAILED"))
                .body("failedPickups", equalTo(3))
                .body("events[-1].outcome", equalTo("OUT_OF_ORDER"));
    }

    @Test
    @DisplayName("Reenviar la misma novedad responde DUPLICATE y una atrasada queda como OUT_OF_ORDER")
    void repeatedAndLateUpdatesDoNotChangeTheReturn() {
        long returnId = approvedReturn();
        String eventId = LogisticsProvider.newEventId();

        provider.returned(eventId, returnId, "PICKED_UP").then().statusCode(200).body("result", equalTo("APPLIED"));
        provider.returned(eventId, returnId, "PICKED_UP").then().statusCode(200).body("result", equalTo("DUPLICATE"));

        provider.returned(returnId, "IN_TRANSIT").then().statusCode(200).body("result", equalTo("APPLIED"));
        // Una recogida que llega después del retorno en curso no puede hacerlo retroceder.
        provider.returned(returnId, "PICKED_UP").then().statusCode(200).body("result", equalTo("OUT_OF_ORDER"));

        buyerReturnTracking(returnId).then()
                .body("status", equalTo("IN_RETURN"))
                .body("events.type", contains("PICKUP_SCHEDULED", "PICKED_UP", "IN_TRANSIT", "PICKED_UP"))
                .body("events[3].outcome", equalTo("OUT_OF_ORDER"));
    }

    @Test
    @DisplayName("«Actualizar seguimiento» del retorno consulta al proveedor y limita la frecuencia")
    void refreshingTheReturnIsThrottled() {
        long returnId = approvedReturn();

        buyer.post("/api/returns/" + returnId + "/tracking/refresh").then().statusCode(200)
                .body("refresh", oneOf("UPDATED", "NO_CHANGES", "UNAVAILABLE"));
        buyer.post("/api/returns/" + returnId + "/tracking/refresh").then().statusCode(200)
                .body("refresh", equalTo("THROTTLED"));
    }

    @Test
    @DisplayName("El retorno de otra persona no existe: 404 RETURN_NOT_FOUND")
    void aReturnOfSomebodyElseIsNotFound() {
        buyer.get("/api/returns/999999/tracking").then().statusCode(404)
                .body("code", equalTo("RETURN_NOT_FOUND"));
        seller.get("/api/seller/returns/999999/tracking").then().statusCode(404)
                .body("code", equalTo("RETURN_NOT_FOUND"));
    }

    @Test
    @DisplayName("Una novedad de un retorno que nadie registró es 404")
    void anUnregisteredReturnIsNotFound() {
        provider.returned(999999L, "PICKED_UP").then().statusCode(404)
                .body("code", equalTo("RETURN_SHIPMENT_NOT_FOUND"));
    }

    /** Una devolución aprobada con su retorno ya creado en logística: es lo que CU-19 deja listo para CU-25. */
    private long approvedReturn() {
        Dispatched order = dispatchedOrder();
        report(order, "PICKED_UP", "IN_TRANSIT", "DELIVERED");
        return Returns.approvedWithPickup(buyer, seller, order.orderId());
    }

    private Response buyerReturnTracking(long returnId) {
        Response response = buyer.get("/api/returns/" + returnId + "/tracking");
        response.then().statusCode(200);
        return response;
    }

    private Response sellerReturnTracking(long returnId) {
        Response response = seller.get("/api/seller/returns/" + returnId + "/tracking");
        response.then().statusCode(200);
        return response;
    }
}
