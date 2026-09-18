package com.transformersas.integration;

import com.transformersas.integration.config.BackendConfiguration;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

class HealthIntegrationTest {

    @Test
    void backendHealthEndpointConfirmsTheIntegrationHarness() {
        given()
                .baseUri(BackendConfiguration.baseUrl())
        .when()
                .get("/actuator/health")
        .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}