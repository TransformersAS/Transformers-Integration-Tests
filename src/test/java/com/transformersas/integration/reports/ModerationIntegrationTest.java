package com.transformersas.integration.reports;

import com.transformersas.integration.config.BackendConfiguration;
import com.transformersas.integration.support.MarketplaceClient;
import com.transformersas.integration.support.Reports;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * CU-21: un agente de soporte modera los casos que abre CU-20. Requiere una cuenta con rol SOPORTE sembrada de
 * antemano (scripts/cu20-demo-seed.sh en Transformers-AS, ver README): ese rol no se autoregistra por API como
 * comprador o vendedor. Solo puede haber un caso abierto por publicación, y retirar un contenido lo deja
 * permanentemente no reportable, así que cada prueba publica y reporta su propia publicación en vez de compartir
 * una sola entre todas.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CU-21 Moderar reportes de contenido")
class ModerationIntegrationTest {

    private MarketplaceClient buyer;
    private MarketplaceClient support;
    private MarketplaceClient neighborSeller;

    @BeforeAll
    void openSessions() {
        buyer = MarketplaceClient.loginAs("COMPRADOR");
        support = MarketplaceClient.loginAs(BackendConfiguration.supportEmail(), BackendConfiguration.supportPassword(),
                "SOPORTE");
        neighborSeller = MarketplaceClient.loginAs(BackendConfiguration.neighborEmail(),
                BackendConfiguration.neighborPassword(), "VENDEDOR");
    }

    @Test
    @DisplayName("El agente ve el caso en su cola, lo reclama y decide RETIRAR: oculta el contenido y cierra el caso")
    void agentClaimsAndDecidesToRemoveTheContent() {
        long productId = Reports.newNeighborProduct(neighborSeller, "Lámpara del vecino — caso RETIRAR");
        long caseId = Reports.openCase(buyer, productId, "SPAM").jsonPath().getLong("caseId");

        support.get("/api/support/moderation/cases?status=PENDIENTE").then().statusCode(200)
                .body("items.id", hasItem((int) caseId));

        support.post("/api/support/moderation/cases/" + caseId + "/claim").then().statusCode(200)
                .body("status", equalTo("EN_REVISION"))
                .body("assignedAgentId", notNullValue());

        Response decision = support.post("/api/support/moderation/cases/" + caseId + "/decisions", """
                {"decision":"RETIRAR","justification":"Publicidad repetida sin relación con el producto."}""");
        decision.then().statusCode(201)
                .body("decision", equalTo("RETIRAR"))
                .body("caseStatus", equalTo("RESUELTO"))
                .body("contentState", equalTo("RETIRADO"));

        // El catálogo público ya no muestra lo que soporte retiró.
        buyer.get("/api/products/" + productId).then().statusCode(404);

        support.get("/api/support/moderation/cases/" + caseId + "/audit").then().statusCode(200)
                .body("action", hasItem("MODERATION_DECISION"));
    }

    @Test
    @DisplayName("El agente pide información al reportante y decide MANTENER tras su respuesta: el contenido sigue")
    void agentRequestsInformationAndDecidesToKeepTheContentAfterTheReporterAnswers() {
        long productId = Reports.newNeighborProduct(neighborSeller, "Lámpara del vecino — caso MANTENER");
        Response report = Reports.openCase(buyer, productId, "PRODUCTO_PROHIBIDO");
        long caseId = report.jsonPath().getLong("caseId");
        long reportId = report.jsonPath().getLong("id");

        support.post("/api/support/moderation/cases/" + caseId + "/claim").then().statusCode(200);

        Response infoRequest = support.post("/api/support/moderation/cases/" + caseId + "/information-requests", """
                {"target":"REPORTADOR","message":"¿Puedes precisar por qué crees que el producto está prohibido?"}""");
        infoRequest.then().statusCode(201).body("status", equalTo("ABIERTA"));
        long requestId = infoRequest.jsonPath().getLong("id");

        buyer.post("/api/reports/" + reportId + "/information-requests/" + requestId + "/response", """
                {"text":"Es una lámpara común, no encuentro por qué estaría prohibida; puede que me equivoqué de motivo."}""")
                .then().statusCode(200).body("status", equalTo("RESPONDIDA"));

        support.post("/api/support/moderation/cases/" + caseId + "/decisions", """
                {"decision":"MANTENER","justification":"El reportante aclaró que el motivo no aplica al producto."}""")
                .then().statusCode(201)
                .body("decision", equalTo("MANTENER"))
                .body("caseStatus", equalTo("RESUELTO"))
                .body("contentState", equalTo("VISIBLE"));

        buyer.get("/api/products/" + productId).then().statusCode(200);
    }

    @Test
    @DisplayName("No se puede decidir mientras hay una solicitud de información vigente: 422")
    void cannotDecideWhileAnInformationRequestIsPending() {
        long productId = Reports.newNeighborProduct(neighborSeller, "Lámpara del vecino — caso info pendiente");
        long caseId = Reports.openCase(buyer, productId, "INFORMACION_ENGANOSA").jsonPath().getLong("caseId");
        support.post("/api/support/moderation/cases/" + caseId + "/claim").then().statusCode(200);
        support.post("/api/support/moderation/cases/" + caseId + "/information-requests", """
                {"target":"REPORTADOR","message":"¿Qué información específica es engañosa?"}""")
                .then().statusCode(201);

        support.post("/api/support/moderation/cases/" + caseId + "/decisions", """
                {"decision":"MANTENER","justification":"Se decide sin esperar la respuesta del reportante."}""")
                .then().statusCode(422);
    }

    @Test
    @DisplayName("Decidir con una versión desactualizada del caso responde 409")
    void decidingWithAStaleVersionIsRejected() {
        long productId = Reports.newNeighborProduct(neighborSeller, "Lámpara del vecino — caso versión desactualizada");
        Response report = Reports.openCase(buyer, productId, "POSIBLE_FRAUDE");
        long caseId = report.jsonPath().getLong("caseId");
        long openedVersion = support.get("/api/support/moderation/cases/" + caseId).then().statusCode(200)
                .extract().jsonPath().getLong("version");

        // Reclamar el caso lo modifica: la versión que se tenía antes queda desactualizada.
        support.post("/api/support/moderation/cases/" + caseId + "/claim").then().statusCode(200);

        support.post("/api/support/moderation/cases/" + caseId + "/decisions", """
                {"decision":"MANTENER","justification":"Decisión con una vista del caso ya desactualizada.",
                 "expectedVersion":%d}""".formatted(openedVersion))
                .then().statusCode(409);
    }

    @Test
    @DisplayName("Un caso que no existe responde 404 en cada acción de soporte")
    void aCaseThatDoesNotExistIsNotFound() {
        support.get("/api/support/moderation/cases/999999").then().statusCode(404);
        support.post("/api/support/moderation/cases/999999/claim").then().statusCode(404);
    }
}
