package com.transformersas.integration.claims;

import com.transformersas.integration.support.MarketplaceScenario;
import com.transformersas.integration.support.Purchases;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CU-13: el comprador tramita una reclamación de compra y el vendedor de la tienda la atiende. Verifica el contrato
 * externo de {@code /api/claims} y {@code /api/seller/claims} sobre el sistema desplegado.
 *
 * <p>El perfil local del backend no aprovisiona ninguna cuenta con el rol SOPORTE (solo la cuenta demo, con
 * COMPRADOR y VENDEDOR), así que la decisión de una reclamación escalada por un agente de soporte no se puede ejercer
 * por HTTP con este harness. Queda documentada como límite conocido en {@code docs/pruebas-cu13-cu15.md}.
 */
@DisplayName("CU-13 Tramitar una reclamación de compra")
class ClaimIntegrationTest extends MarketplaceScenario {

    private long demoProductId() {
        Response products = buyer.get("/api/products");
        products.then().statusCode(200);
        return products.jsonPath().getLong("find { it.name == '" + Purchases.DEMO_PRODUCT + "' }.id");
    }

    /** Compra el producto demo y abre una reclamación sobre esa compra; deja la reclamación en OPEN. */
    private Response openClaim() {
        long orderId = confirmedOrder();
        long productId = demoProductId();

        Response response = buyer.post("/api/claims",
                "{\"orderId\":%d,\"productId\":%d,\"description\":\"El producto llegó con un defecto\"}"
                        .formatted(orderId, productId));
        response.then().statusCode(201).body("status", equalTo("OPEN")).body("orderId", equalTo((int) orderId));
        return response;
    }

    @Test
    @DisplayName("El comprador abre una reclamación, el vendedor propone una solución y el comprador la acepta")
    void buyerOpensAClaimAndAcceptsTheSellersProposal() {
        Response claim = openClaim();
        long claimId = claim.jsonPath().getLong("id");

        seller.post("/api/seller/claims/" + claimId + "/request-info", "{\"message\":\"¿Puedes enviar una foto?\"}")
                .then().statusCode(200).body("status", equalTo("INFO_REQUESTED"));

        buyer.post("/api/claims/" + claimId + "/messages",
                "{\"message\":\"Aquí la foto: http://evidencia.example/1.jpg\"}")
                .then().statusCode(200).body("status", equalTo("OPEN"))
                .body("messages", hasItem(org.hamcrest.Matchers.hasEntry("kind", "MESSAGE")));

        BigDecimal itemTotal = claim.jsonPath().getObject("itemTotal", BigDecimal.class);
        seller.post("/api/seller/claims/" + claimId + "/propose",
                "{\"message\":\"Te devolvemos lo pagado\",\"refundAmount\":%s}".formatted(itemTotal))
                .then().statusCode(200).body("status", equalTo("SOLUTION_PROPOSED"))
                .body("proposalText", equalTo("Te devolvemos lo pagado"));

        buyer.post("/api/claims/" + claimId + "/accept")
                .then().statusCode(200).body("status", equalTo("RESOLVED"))
                .body("resolution", equalTo("SOLUTION_ACCEPTED"))
                .body("messages.kind", hasItem("DECISION"));
    }

    @Test
    @DisplayName("Sin acuerdo con el vendedor, el comprador escala la reclamación")
    void buyerEscalatesWhenThereIsNoAgreement() {
        Response claim = openClaim();
        long claimId = claim.jsonPath().getLong("id");

        buyer.post("/api/claims/" + claimId + "/escalate", "{\"reason\":\"No llegamos a un acuerdo\"}")
                .then().statusCode(200).body("status", equalTo("ESCALATED"))
                .body("messages", hasItem(org.hamcrest.Matchers.hasEntry("kind", "ESCALATION")));
    }

    @Test
    @DisplayName("Nadie puede proponer un reembolso mayor a lo pagado por el producto")
    void aProposalCannotOfferMoreThanWhatWasPaidForTheProduct() {
        Response claim = openClaim();
        long claimId = claim.jsonPath().getLong("id");
        BigDecimal itemTotal = claim.jsonPath().getObject("itemTotal", BigDecimal.class);
        BigDecimal tooMuch = itemTotal.add(BigDecimal.ONE);
        assertNotNull(itemTotal);

        seller.post("/api/seller/claims/" + claimId + "/propose",
                "{\"message\":\"Te devolvemos de más\",\"refundAmount\":%s}".formatted(tooMuch))
                .then().statusCode(400).body("message", containsString("no puede superar lo pagado"));
    }

    @Test
    @DisplayName("El vendedor solo atiende las reclamaciones de su propia tienda")
    void theSellerIsBoundToTheirOwnStore() {
        Response claim = openClaim();
        long claimId = claim.jsonPath().getLong("id");

        // La tienda se puede pedir por cabecera, pero CU-18 comprueba que la cuenta sea su dueña.
        seller.get("/api/seller/claims/" + claimId, 999L).then().statusCode(403)
                .body("code", equalTo("STORE_NOT_AUTHORIZED"));
        seller.get("/api/seller/claims/999999").then().statusCode(404)
                .body("message", equalTo("Reclamación no encontrada"));
    }

    @Test
    @DisplayName("Solo un comprador abre reclamaciones y solo soporte entra a su cola de trabajo")
    void onlyBuyersOpenClaimsAndOnlySupportSeesTheQueue() {
        // La misma cuenta local, pero con el rol activo de vendedor: /api/claims exige COMPRADOR (CU-13).
        seller.get("/api/claims").then().statusCode(403);
        // Ninguna cuenta local tiene el rol SOPORTE: /api/support/** lo exige a nivel de seguridad.
        buyer.get("/api/support/claims").then().statusCode(403);
    }
}
