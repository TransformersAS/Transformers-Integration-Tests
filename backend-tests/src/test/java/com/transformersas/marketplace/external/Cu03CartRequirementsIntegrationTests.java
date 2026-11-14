package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import com.transformersas.marketplace.reservation.InventoryReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** RF-007, RF-010, RF-015 to RF-018 and RF-020: real sessions and persisted checkout state. */
class Cu03CartRequirementsIntegrationTests extends AbstractIntegrationTest {
    @Autowired InventoryReservationService reservations;

    private void add(Session buyer, long product, int quantity) throws Exception {
        perform(buyer, post("/api/cart/items").contentType("application/json")
                .content("{\"productId\":%d,\"quantity\":%d}".formatted(product, quantity)))
                .andExpect(status().isCreated());
    }

    private String preview(long address, String coupon) {
        return "{\"addressId\":%d,\"shippingMethod\":\"STANDARD\",\"couponCode\":\"%s\"}".formatted(address, coupon);
    }

    @Test
    void cartSurvivesNewSessionAndUsesCurrentPricesWithoutLeakingToOtherBuyers() throws Exception {
        var buyer = sessionWithRole("cu03-session@example.test", "COMPRADOR");
        long product = seedProduct(1, "Precio vigente", 5, "100");
        add(buyer, product, 2);
        jdbc.update("UPDATE products SET price=125 WHERE id=?", product);
        var freshSession = login("cu03-session@example.test");
        perform(freshSession, get("/api/cart")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(product))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(250));
        var other = sessionWithRole("cu03-private@example.test", "COMPRADOR");
        perform(other, get("/api/cart")).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void invalidQuantitiesLeaveStoredCartUnchanged() throws Exception {
        var buyer = sessionWithRole("cu03-quantities@example.test", "COMPRADOR");
        long product = seedProduct(1, "Limites", 3, "100");
        add(buyer, product, 1);
        long item = jdbc.queryForObject("SELECT id FROM cart_items", Long.class);
        for (int quantity : new int[]{0, -1, 4}) {
            perform(buyer, patch("/api/cart/items/{id}", item).contentType("application/json")
                    .content("{\"quantity\":" + quantity + "}")).andExpect(status().isBadRequest());
        }
        perform(buyer, get("/api/cart")).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void emptyCartForeignAddressInvalidCouponAndInactiveProductCannotStartPurchase() throws Exception {
        var buyer = sessionWithRole("cu03-preview-errors@example.test", "COMPRADOR");
        long address = seedAddress(buyer);
        perform(buyer, post("/api/checkout/preview").contentType("application/json").content(preview(address, "")))
                .andExpect(status().isBadRequest());
        long product = seedProduct(1, "Revalidacion", 5, "100");
        add(buyer, product, 1);
        var other = sessionWithRole("cu03-address-owner@example.test", "COMPRADOR");
        long foreign = seedAddress(other);
        perform(buyer, post("/api/checkout/preview").contentType("application/json").content(preview(foreign, "")))
                .andExpect(status().isNotFound());
        perform(buyer, post("/api/checkout/preview").contentType("application/json").content(preview(address, "INVALID")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.couponValid").value(false))
                .andExpect(jsonPath("$.discount").value(0));
        perform(buyer, post("/api/checkout/preview").contentType("application/json").content(preview(address, "")))
                .andExpect(status().isOk());
        jdbc.update("UPDATE products SET active=FALSE,status='PAUSED' WHERE id=?", product);
        perform(buyer, post("/api/checkout/preview").contentType("application/json").content(preview(address, "")))
                .andExpect(status().isBadRequest());
        assertThat(count("orders")).isZero();
        assertThat(count("inventory_reservations")).isZero();
    }

    @Test
    void heldStockCannotBeReservedByAnotherBuyerUntilItExpires() throws Exception {
        var buyer = sessionWithRole("cu03-reserving@example.test", "COMPRADOR");
        var other = sessionWithRole("cu03-competing@example.test", "COMPRADOR");
        long product = seedProduct(1, "Ultima unidad", 1, "100");
        add(buyer, product, 1);
        add(other, product, 1);
        perform(buyer, post("/api/reservations/cart")).andExpect(status().isOk());
        long first = jdbc.queryForObject("SELECT id FROM inventory_reservations", Long.class);
        perform(other, post("/api/reservations/cart")).andExpect(status().isConflict());
        assertThat(count("inventory_reservations")).isEqualTo(1);
        jdbc.update("UPDATE inventory_reservations SET expires_at=? WHERE id=?",
                java.time.LocalDateTime.now().minusMinutes(1), first);
        reservations.expireOldReservations();
        perform(other, post("/api/reservations/cart")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE id=?", String.class, first))
                .isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, product)).isEqualTo(1);
        assertThat(count("orders")).isZero();
    }
}
