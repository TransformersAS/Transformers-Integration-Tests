package com.transformersas.integration.support;

import com.transformersas.integration.config.BackendConfiguration;
import io.restassured.response.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Hace de servicio logístico externo: envía al webhook del backend las actualizaciones de envíos y retornos firmadas con
 * HMAC-SHA256 sobre el cuerpo exacto, como lo haría el proveedor real. No usa sesión ni CSRF: el webhook se autentica
 * solo con la firma.
 */
public final class LogisticsProvider {

    private static final String SIGNATURE_HEADER = "X-Logistics-Signature";

    /**
     * Momento del último evento enviado. La línea de tiempo se ordena por {@code occurredAt}, así que cada evento debe
     * ser posterior al anterior y a los que registró el propio marketplace (por ejemplo el alta del retorno).
     */
    private Instant lastEvent = Instant.EPOCH;

    /** Actualización de un envío. */
    public Response shipment(String eventId, String providerShipmentId, String trackingCode, String type) {
        return post("/api/logistics/webhooks/shipments", body(eventId, "shipmentId", providerShipmentId, trackingCode,
                type, evidenceFor(type, eventId)));
    }

    public Response shipment(String providerShipmentId, String trackingCode, String type) {
        return shipment(newEventId(), providerShipmentId, trackingCode, type);
    }

    /** Actualización de un retorno: el retorno {@code N} se llama SIM-return-N y su guía TRK-RN en el proveedor simulado. */
    public Response returned(String eventId, long returnId, String type) {
        return post("/api/logistics/webhooks/returns", body(eventId, "returnId", "SIM-return-" + returnId,
                "TRK-R" + returnId, type, evidenceFor(type, eventId)));
    }

    public Response returned(long returnId, String type) {
        return returned(newEventId(), returnId, type);
    }

    /** Envía tal cual el cuerpo indicado con la firma indicada (para probar firmas ausentes o alteradas). */
    public Response raw(String path, String body, String signatureHeader) {
        var request = given().baseUri(BackendConfiguration.baseUrl()).contentType("application/json").body(body);
        if (signatureHeader != null) {
            request = request.header(SIGNATURE_HEADER, signatureHeader);
        }
        return request.post(path);
    }

    /** Firma válida para {@code body}: {@code sha256=<hex del HMAC-SHA256>}. */
    public static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String newEventId() {
        return "it-" + UUID.randomUUID();
    }

    private Response post(String path, String body) {
        return raw(path, body, sign(body, BackendConfiguration.webhookSecret()));
    }

    private String body(String eventId, String referenceField, String reference, String trackingCode, String type,
                        String evidence) {
        return "{\"eventId\":\"%s\",\"%s\":\"%s\",\"trackingCode\":\"%s\",\"type\":\"%s\",\"occurredAt\":\"%s\"%s}"
                .formatted(eventId, referenceField, reference, trackingCode, type, nextOccurredAt(), evidence);
    }

    /** Ahora mismo, pero siempre después del evento anterior aunque dos envíos caigan en el mismo milisegundo. */
    private synchronized Instant nextOccurredAt() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        lastEvent = now.isAfter(lastEvent) ? now : lastEvent.plusMillis(10);
        return lastEvent;
    }

    private static String evidenceFor(String type, String eventId) {
        return type.startsWith("DELIVERED")
                ? ",\"evidence\":{\"type\":\"SIGNATURE\",\"reference\":\"POD-" + eventId + "\"}"
                : "";
    }
}
