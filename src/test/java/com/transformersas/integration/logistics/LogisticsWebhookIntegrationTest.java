package com.transformersas.integration.logistics;

import com.transformersas.integration.config.BackendConfiguration;
import com.transformersas.integration.support.LogisticsProvider;
import com.transformersas.integration.support.MarketplaceScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El webhook por el que el servicio logístico informa (CU-24 y CU-25) es la única puerta abierta sin sesión del
 * marketplace: se autentica con la firma HMAC-SHA256 del cuerpo exacto. Estas pruebas comprueban desde fuera que nadie
 * puede inventar una actualización y que un cuerpo inesperado no bloquea al proveedor con reintentos.
 */
@DisplayName("Webhook logístico: firma y contrato")
class LogisticsWebhookIntegrationTest extends MarketplaceScenario {

    private static final String SHIPMENTS = "/api/logistics/webhooks/shipments";

    @Test
    @DisplayName("Sin firma, con firma ajena o con el cuerpo alterado después de firmar: 401")
    void onlyACorrectSignatureIsAccepted() {
        Dispatched order = dispatchedOrder();
        String body = event(order, LogisticsProvider.newEventId());

        provider.raw(SHIPMENTS, body, null).then().statusCode(401);
        provider.raw(SHIPMENTS, body, "sha256=" + "0".repeat(64)).then().statusCode(401);
        provider.raw(SHIPMENTS, body, LogisticsProvider.sign(body, "otro-secreto")).then().statusCode(401);

        // Firma válida de otro cuerpo: el atacante cambia el estado después de firmar.
        String signature = LogisticsProvider.sign(body, BackendConfiguration.webhookSecret());
        provider.raw(SHIPMENTS, body.replace("PICKED_UP", "DELIVERED"), signature).then().statusCode(401);

        // Nada de lo anterior tocó el pedido.
        assertEquals("READY_FOR_DISPATCH", buyerOrderStatus(order.orderId()));
        buyerTracking(order.orderId()).then().body("events", org.hamcrest.Matchers.empty());
    }

    @Test
    @DisplayName("Una actualización firmada de un envío que no existe es 404, no un error del servidor")
    void anUnknownShipmentIsNotFound() {
        provider.shipment("SIM-order-999999", "TRK-999999", "PICKED_UP").then().statusCode(404)
                .body("code", equalTo("SHIPMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("La guía tiene que corresponder al envío: 409 SHIPMENT_MISMATCH")
    void theTrackingCodeMustMatchTheShipment() {
        Dispatched order = dispatchedOrder();

        provider.shipment(order.providerShipmentId(), "TRK-OTRA-GUIA", "PICKED_UP").then().statusCode(409)
                .body("code", equalTo("SHIPMENT_MISMATCH"));

        assertEquals("READY_FOR_DISPATCH", buyerOrderStatus(order.orderId()));
    }

    @Test
    @DisplayName("Un tipo de evento que el marketplace no conoce se ignora sin error")
    void anUnknownEventTypeIsIgnored() {
        Dispatched order = dispatchedOrder();
        report(order, "PICKED_UP");

        // El proveedor puede añadir tipos nuevos: responder con un error solo lo haría reintentar para siempre.
        provider.shipment(order.providerShipmentId(), order.trackingCode(), "CUSTOMS_HOLD")
                .then().statusCode(200).body("result", equalTo("IGNORED"));

        assertEquals("PICKED_UP", buyerOrderStatus(order.orderId()));
        buyerTracking(order.orderId()).then().body("events.type", contains("PICKED_UP"));
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON válido o con una fecha inválida es 400")
    void aMalformedBodyIsRejected() {
        String secret = BackendConfiguration.webhookSecret();

        provider.raw(SHIPMENTS, "no soy json", LogisticsProvider.sign("no soy json", secret))
                .then().statusCode(400).body("code", equalTo("INVALID_WEBHOOK_PAYLOAD"));

        String badDate = """
                {"eventId":"it-fecha","shipmentId":"SIM-order-1","trackingCode":"TRK-1","type":"PICKED_UP",\
                "occurredAt":"ayer por la tarde"}""";
        provider.raw(SHIPMENTS, badDate, LogisticsProvider.sign(badDate, secret))
                .then().statusCode(400).body("code", equalTo("INVALID_WEBHOOK_PAYLOAD"));
    }

    private String event(Dispatched order, String eventId) {
        return """
                {"eventId":"%s","shipmentId":"%s","trackingCode":"%s","type":"PICKED_UP",\
                "occurredAt":"2026-09-21T10:00:00Z"}"""
                .formatted(eventId, order.providerShipmentId(), order.trackingCode());
    }
}
