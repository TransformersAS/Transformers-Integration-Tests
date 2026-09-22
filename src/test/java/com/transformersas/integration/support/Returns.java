package com.transformersas.integration.support;

import io.restassured.response.Response;

import static org.hamcrest.Matchers.equalTo;

/**
 * Devolución real creada con los endpoints de CU-19, que es quien la solicita, la aprueba y elige el método de retorno.
 * Al elegir el método, CU-19 crea el retorno en el servicio logístico y registra su seguimiento (CU-25): solo desde ese
 * momento existe algo que seguir en {@code /api/returns/{id}/tracking}.
 */
public final class Returns {

    private Returns() {
    }

    /** La devolución de una línea de un pedido entregado, ya aprobada y con el método de retorno elegido. */
    public static long approvedWithPickup(MarketplaceClient buyer, MarketplaceClient seller, long orderId) {
        long orderItemId = eligibleLine(buyer, orderId);

        Response requested = buyer.post("/api/return-requests", """
                {"orderId":%d,"orderItemId":%d,"reason":"DEFECTIVE","description":"Llegó con una costura abierta"}"""
                .formatted(orderId, orderItemId));
        requested.then().statusCode(201).body("status", equalTo("REQUESTED"));
        long returnId = requested.jsonPath().getLong("id");

        seller.post("/api/seller/return-requests/" + returnId + "/review")
                .then().statusCode(200).body("status", equalTo("IN_REVIEW"));
        seller.post("/api/seller/return-requests/" + returnId + "/approve", "{\"note\":\"Aprobada en la prueba\"}")
                .then().statusCode(200).body("status", equalTo("APPROVED"));

        buyer.get("/api/return-requests/" + returnId + "/return-methods")
                .then().statusCode(200).body("code", org.hamcrest.Matchers.hasItem("PICKUP"));
        buyer.post("/api/return-requests/" + returnId + "/return-method", "{\"method\":\"PICKUP\"}")
                .then().statusCode(200).body("returnMethodCode", equalTo("PICKUP"));
        return returnId;
    }

    /** La línea devolvible del pedido entregado, tal como la ofrece CU-19 al comprador. */
    private static long eligibleLine(MarketplaceClient buyer, long orderId) {
        Response eligible = buyer.get("/api/return-requests/eligible-orders");
        eligible.then().statusCode(200);
        Long orderItemId = eligible.jsonPath().getLong(
                "find { it.orderId == %d }.lines.find { it.eligible == true }.orderItemId".formatted(orderId));
        if (orderItemId == null) {
            throw new IllegalStateException("El pedido " + orderId + " no ofrece ninguna línea devolvible: "
                    + eligible.asString());
        }
        return orderItemId;
    }

    public static String status(MarketplaceClient buyer, long returnId) {
        return buyer.get("/api/return-requests/" + returnId).then().statusCode(200).extract().path("status");
    }
}
