package com.transformersas.integration.returns;

import com.transformersas.integration.support.MarketplaceScenario;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * CU-19: la compradora solicita la devolución de una línea de un pedido entregado, y la tienda la revisa, pide
 * información si le hace falta y decide aprobarla o rechazarla. Al aprobarla, la compradora elige el método de
 * retorno (CU-19 se lo entrega listo a CU-25). Se recorre sobre el sistema desplegado, por HTTP, igual que el
 * frontend.
 */
@DisplayName("CU-19 Solicitar y gestionar devoluciones")
class ReturnRequestIntegrationTest extends MarketplaceScenario {

    private static final String RETURNS = "/api/return-requests";
    private static final String SELLER_RETURNS = "/api/seller/return-requests";

    @Test
    @DisplayName("La compradora solicita la devolución, la tienda la aprueba y la compradora elige el método")
    void theBuyerRequestsAReturnAndTheSellerApprovesItAndTheBuyerChoosesTheMethod() {
        long orderId = deliveredOrder();
        long orderItemId = eligibleLine(orderId);

        Response requested = buyer.post(RETURNS, """
                {"orderId":%d,"orderItemId":%d,"reason":"DEFECTIVE","description":"Llegó con una costura abierta"}"""
                .formatted(orderId, orderItemId));
        requested.then().statusCode(201).body("status", equalTo("REQUESTED")).body("duplicate", equalTo(false));
        long returnId = requested.jsonPath().getLong("id");

        seller.post(SELLER_RETURNS + "/" + returnId + "/review").then().statusCode(200)
                .body("status", equalTo("IN_REVIEW"));

        seller.post(SELLER_RETURNS + "/" + returnId + "/approve", "{\"note\":\"Aprobada, se revisó la evidencia\"}")
                .then().statusCode(200).body("status", equalTo("APPROVED"));

        buyer.get(RETURNS + "/" + returnId + "/return-methods").then().statusCode(200)
                .body("code", org.hamcrest.Matchers.hasItem("PICKUP"));
        buyer.post(RETURNS + "/" + returnId + "/return-method", "{\"method\":\"PICKUP\"}")
                .then().statusCode(200).body("returnMethodCode", equalTo("PICKUP"));
    }

    @Test
    @DisplayName("Una línea que no pertenece al pedido es 422 RETURN_LINE_NOT_IN_ORDER, sin crear nada")
    void aLineThatDoesNotBelongToTheOrderIsRejected() {
        long orderId = deliveredOrder();

        buyer.post(RETURNS, """
                {"orderId":%d,"orderItemId":999999,"reason":"DEFECTIVE","description":"No corresponde a este pedido"}"""
                .formatted(orderId))
                .then().statusCode(422).body("code", equalTo("RETURN_LINE_NOT_IN_ORDER"));
    }

    @Test
    @DisplayName("Un pedido inexistente o ajeno es 404 RETURN_ORDER_NOT_FOUND")
    void anOrderThatDoesNotExistIsNotFound() {
        buyer.post(RETURNS, """
                {"orderId":999999,"orderItemId":1,"reason":"DEFECTIVE","description":"Pedido que no existe"}""")
                .then().statusCode(404).body("code", equalTo("RETURN_ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Un motivo inválido o sin descripción es 400, con el campo señalado")
    void invalidDataIsRejectedWithTheFieldItComesFrom() {
        long orderId = deliveredOrder();
        long orderItemId = eligibleLine(orderId);

        buyer.post(RETURNS, """
                {"orderId":%d,"orderItemId":%d,"reason":"NO_ES_UN_MOTIVO_VALIDO","description":"Algo"}"""
                .formatted(orderId, orderItemId))
                .then().statusCode(400).body("code", equalTo("RETURN_REASON_INVALID"));

        buyer.post(RETURNS, """
                {"orderId":%d,"orderItemId":%d,"reason":"DEFECTIVE","description":""}"""
                .formatted(orderId, orderItemId))
                .then().statusCode(400).body("code", equalTo("RETURN_DESCRIPTION_REQUIRED"))
                .body("details.field", equalTo("description"));
    }

    @Test
    @DisplayName("Pedir de nuevo la misma línea devuelve la solicitud existente, sin crear otra")
    void requestingTheSameLineAgainReturnsTheExistingRequest() {
        long orderId = deliveredOrder();
        long orderItemId = eligibleLine(orderId);
        String body = """
                {"orderId":%d,"orderItemId":%d,"reason":"WRONG_ITEM","description":"No era lo que pedí"}"""
                .formatted(orderId, orderItemId);

        long firstId = buyer.post(RETURNS, body).then().statusCode(201).body("duplicate", equalTo(false))
                .extract().jsonPath().getLong("id");

        Response second = buyer.post(RETURNS, body);
        second.then().statusCode(200).body("duplicate", equalTo(true)).body("id", equalTo((int) firstId));
    }

    @Test
    @DisplayName("Rechazar sin justificación es 400; con ella, cierra la devolución y bloquea cualquier acción luego")
    void rejectingRequiresAJustificationAndClosesTheReturn() {
        long returnId = requestedReturn();
        seller.post(SELLER_RETURNS + "/" + returnId + "/review").then().statusCode(200);

        seller.post(SELLER_RETURNS + "/" + returnId + "/reject", "{}")
                .then().statusCode(400).body("code", equalTo("RETURN_JUSTIFICATION_REQUIRED"));

        seller.post(SELLER_RETURNS + "/" + returnId + "/reject", "{\"note\":\"No corresponde a un defecto real\"}")
                .then().statusCode(200).body("status", equalTo("REJECTED"));

        seller.post(SELLER_RETURNS + "/" + returnId + "/approve", "{}")
                .then().statusCode(409).body("code", equalTo("RETURN_INVALID_STATE"));
    }

    @Test
    @DisplayName("La tienda pide información, la compradora responde y la solicitud vuelve a revisión")
    void theSellerAsksForInformationAndTheBuyerAnswersAndItGoesBackToReview() {
        long returnId = requestedReturn();
        seller.post(SELLER_RETURNS + "/" + returnId + "/review").then().statusCode(200);

        seller.post(SELLER_RETURNS + "/" + returnId + "/information-requests",
                "{\"message\":\"¿Podés mandar una foto del defecto?\"}")
                .then().statusCode(201).body("status", equalTo("INFO_REQUIRED"));

        buyer.post(RETURNS + "/" + returnId + "/information-response", "{\"text\":\"Claro, ya la envié al correo\"}")
                .then().statusCode(200).body("status", equalTo("IN_REVIEW"));

        seller.post(SELLER_RETURNS + "/" + returnId + "/approve", "{}")
                .then().statusCode(200).body("status", equalTo("APPROVED"));
    }

    @Test
    @DisplayName("El método de retorno solo se puede pedir o elegir con la devolución aprobada")
    void theReturnMethodOnlyWorksOnceApproved() {
        long returnId = requestedReturn();

        buyer.get(RETURNS + "/" + returnId + "/return-methods").then().statusCode(409)
                .body("code", equalTo("RETURN_INVALID_STATE"));
        buyer.post(RETURNS + "/" + returnId + "/return-method", "{\"method\":\"PICKUP\"}")
                .then().statusCode(409).body("code", equalTo("RETURN_INVALID_STATE"));

        seller.post(SELLER_RETURNS + "/" + returnId + "/review").then().statusCode(200);
        seller.post(SELLER_RETURNS + "/" + returnId + "/approve", "{}").then().statusCode(200);

        buyer.post(RETURNS + "/" + returnId + "/return-method", "{\"method\":\"\"}")
                .then().statusCode(400).body("code", equalTo("RETURN_METHOD_REQUIRED"));

        buyer.post(RETURNS + "/" + returnId + "/return-method", "{\"method\":\"PICKUP\"}")
                .then().statusCode(200).body("returnMethodCode", equalTo("PICKUP"));
        // Elegir el mismo otra vez no cambia nada.
        buyer.post(RETURNS + "/" + returnId + "/return-method", "{\"method\":\"PICKUP\"}")
                .then().statusCode(200).body("returnMethodCode", equalTo("PICKUP"));
    }

    @Test
    @DisplayName("Una devolución que no existe es 404 tanto para la compradora como para la tienda")
    void aReturnThatDoesNotExistIsNotFound() {
        buyer.get(RETURNS + "/999999").then().statusCode(404).body("code", equalTo("RETURN_NOT_FOUND"));
        seller.get(SELLER_RETURNS + "/999999").then().statusCode(404).body("code", equalTo("RETURN_NOT_FOUND"));
        seller.post(SELLER_RETURNS + "/999999/review").then().statusCode(404).body("code", equalTo("RETURN_NOT_FOUND"));
    }

    /** Un pedido entregado, listo para que CU-19 ofrezca devolver alguna de sus líneas. */
    private long deliveredOrder() {
        Dispatched order = dispatchedOrder();
        report(order, "PICKED_UP", "IN_TRANSIT", "DELIVERED");
        return order.orderId();
    }

    /** La línea devolvible de ese pedido, tal como la ofrece CU-19 a la compradora. */
    private long eligibleLine(long orderId) {
        Response eligible = buyer.get(RETURNS + "/eligible-orders");
        eligible.then().statusCode(200);
        Long orderItemId = eligible.jsonPath().getLong(
                "find { it.orderId == %d }.lines.find { it.eligible == true }.orderItemId".formatted(orderId));
        if (orderItemId == null) {
            throw new IllegalStateException("El pedido " + orderId + " no ofrece ninguna línea devolvible: "
                    + eligible.asString());
        }
        return orderItemId;
    }

    /** Una devolución recién solicitada (REQUESTED), sin revisar todavía. */
    private long requestedReturn() {
        long orderId = deliveredOrder();
        long orderItemId = eligibleLine(orderId);
        Response requested = buyer.post(RETURNS, """
                {"orderId":%d,"orderItemId":%d,"reason":"DAMAGED_IN_TRANSIT","description":"Llegó golpeada"}"""
                .formatted(orderId, orderItemId));
        requested.then().statusCode(201).body("id", notNullValue());
        return requested.jsonPath().getLong("id");
    }
}
