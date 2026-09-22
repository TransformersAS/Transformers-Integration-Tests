package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU-03: flujo HTTP real contra Spring + MySQL Testcontainers. */
class Cu03CheckoutIntegrationTests extends AbstractIntegrationTest {

    @BeforeEach
    void clearRecommendationDataCreatedByPurchases() {
        jdbc.update("DELETE FROM user_interactions");
    }

    @Test
    void cartCrudAndCheckoutPreviewWorkThroughHttp() throws Exception {
        Session buyer = sessionWithRole("cu03-cart@example.com", "COMPRADOR");
        long productId = seedProduct(1, "Mouse Pro", 10, "100000.00");
        long addressId = seedAddress();

        String added = perform(buyer, post("/api/cart/items")
                .contentType(APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(200000.00))
                .andReturn().getResponse().getContentAsString();

        long itemId = json.readTree(added).get("id").asLong();

        perform(buyer, patch("/api/cart/items/{id}", itemId)
                .contentType(APPLICATION_JSON)
                .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(300000.00));

        perform(buyer, get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(300000.00));

        perform(buyer, post("/api/checkout/preview")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "addressId": %d,
                          "shippingMethod": "STANDARD",
                          "couponCode": "DESC10"
                        }
                        """.formatted(addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(300000.00))
                .andExpect(jsonPath("$.discount").value(30000.00))
                .andExpect(jsonPath("$.shippingCost").value(10000.00))
                .andExpect(jsonPath("$.total").value(280000.00))
                .andExpect(jsonPath("$.couponValid").value(true));

        perform(buyer, delete("/api/cart/items/{id}", itemId))
                .andExpect(status().isNoContent());

        perform(buyer, get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();
    }

    @Test
    void approvedPaymentCreatesOrderDiscountsStockAndEmptiesCart() throws Exception {
        Session buyer = sessionWithRole("cu03-approved@example.com", "COMPRADOR");
        long userId = accountIdOf("cu03-approved@example.com");
        long productId = seedProduct(1, "Teclado", 10, "100000.00");
        long addressId = seedAddress();

        addToCart(buyer, productId, 2);
        List<Long> reservationIds = reserveCart(buyer);

        perform(buyer, post("/api/payments/process")
                .contentType(APPLICATION_JSON)
                .content(paymentBody("CARD", reservationIds, addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.total").value(210000.00));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM inventory_reservations", String.class))
                .containsExactly("CONFIRMED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_interactions WHERE user_id = ? AND product_id = ? AND interaction_type = 'PURCHASE'",
                Integer.class, userId, productId)).isEqualTo(1);
    }

    @Test
    void rejectedPaymentReleasesReservationsWithoutOrderOrStockChange() throws Exception {
        Session buyer = sessionWithRole("cu03-rejected@example.com", "COMPRADOR");
        long productId = seedProduct(1, "Monitor", 5, "750000.00");
        long addressId = seedAddress();

        addToCart(buyer, productId, 1);
        List<Long> reservationIds = reserveCart(buyer);

        perform(buyer, post("/api/payments/process")
                .contentType(APPLICATION_JSON)
                .content(paymentBody("TEST_REJECT", reservationIds, addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.orderId").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM inventory_reservations", String.class))
                .containsExactly("RELEASED");
    }

    @Test
    void pendingPaymentKeepsReservationActiveAndDoesNotCreateOrder() throws Exception {
        Session buyer = sessionWithRole("cu03-pending@example.com", "COMPRADOR");
        long productId = seedProduct(1, "Webcam", 7, "160000.00");
        long addressId = seedAddress();

        addToCart(buyer, productId, 1);
        List<Long> reservationIds = reserveCart(buyer);

        perform(buyer, post("/api/payments/process")
                .contentType(APPLICATION_JSON)
                .content(paymentBody("TEST_PENDING", reservationIds, addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderId").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM inventory_reservations", String.class))
                .containsExactly("ACTIVE");
    }

    @Test
    void multistoreCartIsRejectedDuringPreviewBeforePayment() throws Exception {
        Session buyer = sessionWithRole("cu03-multistore@example.com", "COMPRADOR");
        seedStore(2, "Tienda secundaria");
        long first = seedProduct(1, "Producto tienda 1", 10, "100000.00");
        long second = seedProduct(2, "Producto tienda 2", 10, "50000.00");
        long addressId = seedAddress();

        addToCart(buyer, first, 1);
        addToCart(buyer, second, 1);

        perform(buyer, post("/api/checkout/preview")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "addressId": %d,
                          "shippingMethod": "STANDARD",
                          "couponCode": null
                        }
                        """.formatted(addressId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MULTI_STORE_CART"))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT stock FROM products ORDER BY id", Integer.class))
                .containsExactly(10, 10);
    }

    @Test
    void insufficientStockIsRejectedBeforeCreatingReservationsOrOrders() throws Exception {
        Session buyer = sessionWithRole("cu03-stock@example.com", "COMPRADOR");
        long productId = seedProduct(1, "Poco stock", 1, "50000.00");

        perform(buyer, post("/api/cart/items")
                .contentType(APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    private void addToCart(Session buyer, long productId, int quantity) throws Exception {
        perform(buyer, post("/api/cart/items")
                .contentType(APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated());
    }

    private List<Long> reserveCart(Session buyer) throws Exception {
        String body = perform(buyer, post("/api/reservations/cart"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(body);
        return root.valueStream().map(node -> node.get("id").asLong()).toList();
    }

    private String paymentBody(String method, List<Long> reservationIds, long addressId) throws Exception {
        return json.writeValueAsString(java.util.Map.of(
                "paymentMethod", method,
                "reservationIds", reservationIds,
                "addressId", addressId,
                "shippingMethod", "STANDARD"
        ));
    }
}
