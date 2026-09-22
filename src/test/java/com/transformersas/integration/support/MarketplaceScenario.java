package com.transformersas.integration.support;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;

/**
 * Base de las pruebas que recorren el Marketplace como sus usuarios reales: la cuenta local actúa como comprador en una
 * sesión y como vendedor de la tienda 1 en otra, y el «proveedor logístico» habla con el webhook. Todo pasa por HTTP; no
 * se toca la base de datos ni se usan clases del backend.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MarketplaceScenario {

    protected MarketplaceClient buyer;
    protected MarketplaceClient seller;
    protected LogisticsProvider provider;

    /** Un pedido ya despachado: el vendedor lo preparó y el proveedor logístico le creó su envío. */
    public record Dispatched(long orderId, String providerShipmentId, String trackingCode) {
    }

    @BeforeAll
    void openSessions() {
        buyer = MarketplaceClient.loginAs("COMPRADOR");
        seller = MarketplaceClient.loginAs("VENDEDOR");
        provider = new LogisticsProvider();
    }

    /** El comprador compra una camiseta: el pedido queda CONFIRMED. */
    protected long confirmedOrder() {
        return Purchases.buyDemoShirt(buyer);
    }

    /** Compra, preparación y despacho: el pedido queda READY_FOR_DISPATCH con su envío creado. */
    protected Dispatched dispatchedOrder() {
        long orderId = confirmedOrder();
        seller.post("/api/seller/orders/" + orderId + "/start-preparation").then().statusCode(200);
        Response ready = seller.post("/api/seller/orders/" + orderId + "/ready-for-dispatch");
        ready.then().statusCode(200).body("status", equalTo("READY_FOR_DISPATCH"))
                .body("shipment.status", equalTo("CREATED"));
        return new Dispatched(orderId, ready.jsonPath().getString("shipment.shipmentId"),
                ready.jsonPath().getString("shipment.trackingCode"));
    }

    /** El proveedor informa cada tipo, en orden; todos deben aplicarse. */
    protected void report(Dispatched order, String... types) {
        for (String type : types) {
            provider.shipment(order.providerShipmentId(), order.trackingCode(), type)
                    .then().statusCode(200).body("result", equalTo("APPLIED"));
        }
    }

    protected String buyerOrderStatus(long orderId) {
        return buyer.get("/api/orders/" + orderId).then().statusCode(200).extract().path("status");
    }

    protected Response buyerTracking(long orderId) {
        Response response = buyer.get("/api/orders/" + orderId + "/tracking");
        response.then().statusCode(200);
        return response;
    }

    protected Response sellerTracking(long orderId) {
        Response response = seller.get("/api/seller/orders/" + orderId + "/tracking");
        response.then().statusCode(200);
        return response;
    }

    /** Tipos de la línea de tiempo, de la más antigua a la más reciente. */
    protected static List<String> timeline(Response tracking) {
        return tracking.jsonPath().getList("events.type");
    }
}
