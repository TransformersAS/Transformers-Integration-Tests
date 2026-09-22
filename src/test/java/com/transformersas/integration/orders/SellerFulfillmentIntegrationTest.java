package com.transformersas.integration.orders;

import com.transformersas.integration.support.MarketplaceClient;
import com.transformersas.integration.support.MarketplaceScenario;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CU-23: el vendedor prepara y despacha un pedido recibido. Verifica el contrato externo de los endpoints del vendedor
 * sobre el sistema desplegado: el estado que queda, el historial, el envío creado en el servicio logístico y los
 * códigos de error estables.
 */
@DisplayName("CU-23 Preparar y despachar pedidos recibidos")
class SellerFulfillmentIntegrationTest extends MarketplaceScenario {

    @Test
    @DisplayName("El pedido pagado se prepara, se marca listo y el servicio logístico le crea su envío")
    void sellerPreparesAndDispatchesAPaidOrder() {
        long orderId = confirmedOrder();

        seller.get("/api/seller/orders/" + orderId).then().statusCode(200)
                .body("status", equalTo("CONFIRMED"))
                .body("paymentStatus", equalTo("APPROVED"))
                .body("shipment", org.hamcrest.Matchers.nullValue())
                .body("delivery.city", equalTo("Bogotá"))
                .body("items[0].name", equalTo("Camiseta demo local"))
                .body("items[0].inventoryConsistent", equalTo(true));

        seller.post("/api/seller/orders/" + orderId + "/start-preparation").then().statusCode(200)
                .body("orderId", equalTo((int) orderId))
                .body("status", equalTo("IN_PREPARATION"));

        Response ready = seller.post("/api/seller/orders/" + orderId + "/ready-for-dispatch");
        ready.then().statusCode(200)
                .body("status", equalTo("READY_FOR_DISPATCH"))
                .body("shipment.status", equalTo("CREATED"))
                .body("shipment.shipmentId", startsWith("SIM-order-"))
                .body("shipment.trackingCode", notNullValue());

        // El detalle conserva cada paso, con la referencia del envío y el actor de cada transición.
        seller.get("/api/seller/orders/" + orderId).then().statusCode(200)
                .body("status", equalTo("READY_FOR_DISPATCH"))
                .body("shipment.shipmentId", equalTo(ready.jsonPath().getString("shipment.shipmentId")))
                .body("history.toStatus", contains("CONFIRMED", "IN_PREPARATION", "READY_FOR_DISPATCH"))
                .body("history.actorType", hasItem("SELLER"));

        // El comprador ve el mismo estado desde su propio panel.
        assertEquals("READY_FOR_DISPATCH", buyerOrderStatus(orderId));
    }

    @Test
    @DisplayName("Reintentar el envío de un pedido que ya lo tiene devuelve 200 con la misma referencia")
    void retryingTheShipmentReusesTheExistingReference() {
        Dispatched order = dispatchedOrder();

        // 201 se reserva para el envío creado ahora; este ya existía, así que es 200 y la referencia no cambia (A6).
        seller.post("/api/seller/orders/" + order.orderId() + "/shipment").then().statusCode(200)
                .body("shipmentId", equalTo(order.providerShipmentId()))
                .body("trackingCode", equalTo(order.trackingCode()));
    }

    @Test
    @DisplayName("Un pedido ya listo no se puede volver a preparar: 409 ORDER_INVALID_TRANSITION")
    void anAlreadyDispatchedOrderCannotGoBackToPreparation() {
        Dispatched order = dispatchedOrder();

        seller.post("/api/seller/orders/" + order.orderId() + "/start-preparation").then().statusCode(409)
                .body("code", equalTo("ORDER_INVALID_TRANSITION"));
    }

    @Test
    @DisplayName("El vendedor solo puede actuar sobre su tienda y solo ve los pedidos que existen en ella")
    void theSellerIsBoundToTheirOwnStore() {
        long orderId = confirmedOrder();

        // La tienda se puede pedir por cabecera, pero CU-18 comprueba que la cuenta sea su dueña.
        seller.get("/api/seller/orders/" + orderId, 999L).then().statusCode(403)
                .body("code", equalTo("STORE_NOT_AUTHORIZED"));

        seller.get("/api/seller/orders/999999").then().statusCode(404)
                .body("code", equalTo("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Sin el rol activo VENDEDOR los endpoints del vendedor responden 403 SELLER_ROLE_REQUIRED")
    void theSellerEndpointsRequireTheSellerRole() {
        long orderId = confirmedOrder();

        // La misma cuenta, pero con el rol activo de comprador: el backend no se fía del rol, lo comprueba.
        MarketplaceClient asBuyer = MarketplaceClient.loginAs("COMPRADOR");
        asBuyer.get("/api/seller/orders/" + orderId).then().statusCode(403)
                .body("code", equalTo("SELLER_ROLE_REQUIRED"));
    }
}
