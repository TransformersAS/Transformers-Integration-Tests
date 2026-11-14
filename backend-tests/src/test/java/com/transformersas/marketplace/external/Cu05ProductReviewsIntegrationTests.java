package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-05: delivered purchase, buyer review, seller response and public rating. */
class Cu05ProductReviewsIntegrationTests extends AbstractIntegrationTest {
    private Session buyer, other, seller;
    private long product, order;

    @BeforeEach
    void purchase() throws Exception {
        seller = sellerOfStore("cu05-seller@example.test", 1);
        buyer = sessionWithRole("cu05-buyer@example.test", "COMPRADOR");
        other = sessionWithRole("cu05-other@example.test", "COMPRADOR");
        product = seedProduct(1, "Compra CU05", 10, "100");
        order = seedOrder(1, "DELIVERED", product, 1, "100");
        jdbc.update("UPDATE orders SET account_id=? WHERE id=?", accountIdOf("cu05-buyer@example.test"), order);
    }

    private String input(int rating) {
        return "{\"orderId\":%d,\"productId\":%d,\"rating\":%d,\"comment\":\"Compra verificada\"}"
                .formatted(order, product, rating);
    }

    @Test
    void buyerPublishesEditsAndDeletesReviewAndSellerAnswers() throws Exception {
        var result = perform(buyer, post("/api/reviews").contentType("application/json").content(input(5)))
                .andExpect(status().isOk()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        perform(seller, put("/api/seller/reviews/{id}/reply", id).contentType("application/json")
                .content("{\"text\":\"Gracias por tu compra\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews/products/{id}", product)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reply.text").value("Gracias por tu compra"))
                .andExpect(jsonPath("$[0].accountId").doesNotExist());
        perform(buyer, put("/api/reviews/{id}", id).contentType("application/json").content(input(2)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}", product)).andExpect(jsonPath("$.ratingAverage").value(2.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
        perform(other, delete("/api/reviews/{id}", id)).andExpect(status().isNotFound());
        perform(buyer, delete("/api/reviews/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/reviews/products/{id}", product)).andExpect(content().json("[]"));
        mvc.perform(get("/api/products/{id}", product)).andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void invalidRatingForeignPurchaseAndUndeliveredPurchaseLeaveNoReview() throws Exception {
        perform(buyer, post("/api/reviews").contentType("application/json").content(input(6)))
                .andExpect(status().isBadRequest());
        perform(other, post("/api/reviews").contentType("application/json").content(input(5)))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE orders SET status='CONFIRMED' WHERE id=?", order);
        perform(buyer, post("/api/reviews").contentType("application/json").content(input(5)))
                .andExpect(status().isConflict());
        assertThat(count("product_reviews")).isZero();
    }
}
