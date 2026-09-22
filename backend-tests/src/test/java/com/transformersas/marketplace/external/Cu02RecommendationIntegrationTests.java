package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CU-02: pruebas de integración HTTP -> Controller -> Service -> Repository -> MySQL Testcontainers.
 * La API de Gemini se deja sin key para comprobar el fallback sin depender de Internet.
 */
@TestPropertySource(properties = "gemini.api-key=")
class Cu02RecommendationIntegrationTests extends AbstractIntegrationTest {

    @BeforeEach
    void clearRecommendationData() {
        jdbc.update("DELETE FROM user_interactions");
    }

    @Test
    void searchInteractionIsPersistedAndReturnedThroughHttp() throws Exception {
        Session buyer = sessionWithRole("cu02-search@example.com", "COMPRADOR");
        long userId = accountIdOf("cu02-search@example.com");

        perform(buyer, post("/api/interactions")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userId": %d,
                          "productId": null,
                          "interactionType": "SEARCH",
                          "searchTerm": "audifonos"
                        }
                        """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.interactionType").value("SEARCH"))
                .andExpect(jsonPath("$.searchTerm").value("audifonos"));

        perform(buyer, get("/api/interactions").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].interactionType").value("SEARCH"))
                .andExpect(jsonPath("$[0].searchTerm").value("audifonos"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_interactions WHERE user_id = ? AND interaction_type = 'SEARCH'",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void viewInteractionRequiresARealProductAndPersistsIt() throws Exception {
        Session buyer = sessionWithRole("cu02-view@example.com", "COMPRADOR");
        long userId = accountIdOf("cu02-view@example.com");
        long productId = seedProduct(1, "Audifonos Bluetooth", 20, "150000.00");

        perform(buyer, post("/api/interactions")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userId": %d,
                          "productId": %d,
                          "interactionType": "VIEW",
                          "searchTerm": null
                        }
                        """.formatted(userId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.interactionType").value("VIEW"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_interactions WHERE user_id = ? AND product_id = ? AND interaction_type = 'VIEW'",
                Integer.class, userId, productId)).isEqualTo(1);
    }

    @Test
    void invalidInteractionsAreRejectedWithoutWritingToDatabase() throws Exception {
        Session buyer = sessionWithRole("cu02-invalid@example.com", "COMPRADOR");
        long userId = accountIdOf("cu02-invalid@example.com");

        perform(buyer, post("/api/interactions")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userId": %d,
                          "productId": 999999999,
                          "interactionType": "VIEW",
                          "searchTerm": null
                        }
                        """.formatted(userId)))
                .andExpect(status().isNotFound());

        perform(buyer, post("/api/interactions")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userId": %d,
                          "productId": null,
                          "interactionType": "SEARCH",
                          "searchTerm": "   "
                        }
                        """.formatted(userId)))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_interactions WHERE user_id = ?",
                Integer.class, userId)).isZero();
    }

    @Test
    void userWithoutHistoryGetsFiveAvailableProductsOrderedByStock() throws Exception {
        Session buyer = sessionWithRole("cu02-general@example.com", "COMPRADOR");
        long userId = accountIdOf("cu02-general@example.com");

        seedProduct(1, "Stock 10", 10, "10000.00");
        seedProduct(1, "Stock 50", 50, "20000.00");
        seedProduct(1, "Stock 30", 30, "30000.00");
        seedProduct(1, "Stock 40", 40, "40000.00");
        seedProduct(1, "Stock 20", 20, "50000.00");
        seedProduct(1, "Stock 60", 60, "60000.00");
        long inactive = seedProduct(1, "Inactivo", 100, "70000.00");
        jdbc.update("UPDATE products SET active = FALSE, status = 'PAUSED' WHERE id = ?", inactive);
        seedProduct(1, "Sin stock", 0, "80000.00");

        perform(buyer, get("/api/recommendations").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.strategy").value("GENERAL_NO_HISTORY"))
                .andExpect(jsonPath("$.products.length()").value(5))
                .andExpect(jsonPath("$.products[0].name").value("Stock 60"))
                .andExpect(jsonPath("$.products[1].name").value("Stock 50"))
                .andExpect(jsonPath("$.products[2].name").value("Stock 40"))
                .andExpect(jsonPath("$.products[3].name").value("Stock 30"))
                .andExpect(jsonPath("$.products[4].name").value("Stock 20"));
    }

    @Test
    void historyWithNoGeminiKeyUsesSafeFallback() throws Exception {
        Session buyer = sessionWithRole("cu02-history@example.com", "COMPRADOR");
        long userId = accountIdOf("cu02-history@example.com");
        long productId = seedProduct(1, "Teclado", 15, "219900.00");
        seedProduct(1, "Mouse", 25, "89900.00");

        perform(buyer, post("/api/interactions")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "userId": %d,
                          "productId": %d,
                          "interactionType": "VIEW",
                          "searchTerm": null
                        }
                        """.formatted(userId, productId)))
                .andExpect(status().isCreated());

        perform(buyer, get("/api/recommendations").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("GENERAL_NO_API_KEY"))
                .andExpect(jsonPath("$.products[0].name").value("Mouse"));
    }

    @Test
    void noAvailableProductsReturnsNoProductsStrategy() throws Exception {
        Session buyer = sessionWithRole("cu02-empty@example.com", "COMPRADOR");
        long userId = accountIdOf("cu02-empty@example.com");

        perform(buyer, get("/api/recommendations").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("NO_PRODUCTS"))
                .andExpect(jsonPath("$.products.length()").value(0));
    }
}
