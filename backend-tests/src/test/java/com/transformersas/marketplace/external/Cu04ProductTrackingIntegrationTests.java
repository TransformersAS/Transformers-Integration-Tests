package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-04: favorites and the lifecycle of price alerts through authenticated HTTP. */
class Cu04ProductTrackingIntegrationTests extends AbstractIntegrationTest {
    @Test
    void favoritesAreIdempotentPersistentAndPrivate() throws Exception {
        var buyer = sessionWithRole("cu04-buyer@example.test", "COMPRADOR");
        var other = sessionWithRole("cu04-other@example.test", "COMPRADOR");
        long product = seedProduct(1, "Favorito CU04", 5, "100");
        for (int i = 0; i < 2; i++)
            perform(buyer, put("/api/favorites/{id}", product)).andExpect(status().isOk());
        perform(buyer, get("/api/favorites/ids")).andExpect(content().json("[" + product + "]"));
        perform(other, get("/api/favorites")).andExpect(content().json("[]"));
        perform(other, delete("/api/favorites/{id}", product)).andExpect(status().isOk());
        perform(buyer, get("/api/favorites")).andExpect(jsonPath("$.length()").value(1));
        perform(buyer, delete("/api/favorites/{id}", product)).andExpect(status().isOk());
        perform(buyer, get("/api/favorites/ids")).andExpect(content().json("[]"));
    }

    @Test
    void priceAlertCanBePausedResumedAndDeletedOnlyByItsOwner() throws Exception {
        var buyer = sessionWithRole("cu04-alert@example.test", "COMPRADOR");
        var other = sessionWithRole("cu04-intruder@example.test", "COMPRADOR");
        long product = seedProduct(1, "Alerta CU04", 5, "100");
        String input = "{\"productId\":%d,\"kind\":\"PRICE\",\"targetPrice\":80}".formatted(product);
        var result = perform(buyer, post("/api/favorites/alerts").contentType("application/json").content(input))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alreadySatisfied").value(false))
                .andExpect(jsonPath("$.alert.status").value("ACTIVE")).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("alert").get("id").asString();
        perform(buyer, post("/api/favorites/alerts").contentType("application/json").content(input))
                .andExpect(status().isConflict());
        perform(other, get("/api/favorites/alerts")).andExpect(content().json("[]"));
        perform(other, delete("/api/favorites/alerts/{id}", id)).andExpect(status().isNotFound());
        for (String state : new String[]{"PAUSED", "ACTIVE"}) {
            perform(buyer, patch("/api/favorites/alerts/{id}", id).contentType("application/json")
                    .content("{\"status\":\"" + state + "\"}")).andExpect(status().isOk());
            perform(buyer, get("/api/favorites/alerts")).andExpect(jsonPath("$[0].status").value(state));
        }
        perform(buyer, delete("/api/favorites/alerts/{id}", id)).andExpect(status().isOk());
        perform(buyer, get("/api/favorites/alerts")).andExpect(content().json("[]"));
    }

    @Test
    void anonymousAndMissingProductRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/favorites")).andExpect(status().isUnauthorized());
        var buyer = sessionWithRole("cu04-invalid@example.test", "COMPRADOR");
        perform(buyer, put("/api/favorites/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        perform(buyer, get("/api/favorites/ids")).andExpect(content().json("[]"));
    }
}
