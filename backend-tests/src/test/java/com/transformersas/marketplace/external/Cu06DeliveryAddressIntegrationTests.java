package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-06: persisted address book, defaults, validation and account isolation. */
class Cu06DeliveryAddressIntegrationTests extends AbstractIntegrationTest {
    private String input(String name) {
        return """
                {"recipientName":"%s","street":"Calle 1 # 2-3","city":"Bogota",
                 "department":"Cundinamarca","postalCode":"110111","phone":"3001234567"}
                """.formatted(name);
    }

    private long create(Session buyer, String name) throws Exception {
        var result = perform(buyer, post("/api/addresses").contentType("application/json").content(input(name)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void addressLifecyclePreservesExactlyOneDefault() throws Exception {
        var buyer = sessionWithRole("cu06-buyer@example.test", "COMPRADOR");
        long first = create(buyer, "Casa"), second = create(buyer, "Oficina");
        perform(buyer, get("/api/addresses")).andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].defaultAddress").value(true));
        perform(buyer, put("/api/addresses/{id}", second).contentType("application/json").content(input("Nueva oficina")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientName").value("Nueva oficina"));
        perform(buyer, put("/api/addresses/{id}/default", second)).andExpect(status().isOk());
        perform(buyer, get("/api/addresses")).andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].defaultAddress").value(true));
        perform(buyer, delete("/api/addresses/{id}", second)).andExpect(status().isNoContent());
        perform(buyer, get("/api/addresses")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first)).andExpect(jsonPath("$[0].defaultAddress").value(true));
        assertThat(jdbc.queryForObject("SELECT deleted FROM addresses WHERE id=?", Boolean.class, second)).isTrue();
    }

    @Test
    void foreignAccountsCannotReadEditDeleteOrChooseAddress() throws Exception {
        var buyer = sessionWithRole("cu06-owner@example.test", "COMPRADOR");
        var other = sessionWithRole("cu06-other@example.test", "COMPRADOR");
        long id = create(buyer, "Privada");
        perform(other, get("/api/addresses")).andExpect(content().json("[]"));
        perform(other, put("/api/addresses/{id}", id).contentType("application/json").content(input("Intruso")))
                .andExpect(status().isNotFound());
        perform(other, put("/api/addresses/{id}/default", id)).andExpect(status().isNotFound());
        perform(other, delete("/api/addresses/{id}", id)).andExpect(status().isNotFound());
        perform(buyer, get("/api/addresses")).andExpect(jsonPath("$[0].recipientName").value("Privada"));
    }

    @Test
    void anonymousWrongRoleInvalidFieldsAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
        var seller = sessionWithRole("cu06-seller@example.test", "VENDEDOR");
        perform(seller, get("/api/addresses")).andExpect(status().isForbidden());
        var buyer = sessionWithRole("cu06-invalid@example.test", "COMPRADOR");
        perform(buyer, post("/api/addresses").contentType("application/json").content(input(" ")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/addresses").cookie(buyer.cookie()).contentType("application/json").content(input("Casa")))
                .andExpect(status().isForbidden());
        perform(buyer, get("/api/addresses")).andExpect(content().json("[]"));
    }
}
