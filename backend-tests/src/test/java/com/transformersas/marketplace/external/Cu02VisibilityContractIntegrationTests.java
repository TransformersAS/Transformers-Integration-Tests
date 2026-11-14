package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** RF-143 acceptance contracts. A failure exposes an application gap, not a provider outage. */
@Tag("acceptance-contract")
class Cu02VisibilityContractIntegrationTests extends AbstractIntegrationTest {
    @Test
    void suspendedStoreProductsMustNotBeRecommended() throws Exception {
        var buyer = sessionWithRole("cu02-suspension@example.test", "COMPRADOR");
        seedStore(2, "Tienda suspendida");
        long hidden = seedProduct(2, "No recomendar suspendido", 100, "100");
        seedProduct(1, "Disponible", 1, "100");
        jdbc.update("UPDATE stores SET status='SUSPENDED',status_reason='Suspension para verificar RF-143' WHERE id=2");
        perform(buyer, get("/api/recommendations")).andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].id", not(hasItem((int) hidden))));
    }

    @Test
    void moderatedProductsMustNotBeRecommended() throws Exception {
        var buyer = sessionWithRole("cu02-moderation@example.test", "COMPRADOR");
        long hidden = seedProduct(1, "No recomendar oculto", 100, "100");
        seedProduct(1, "Disponible", 1, "100");
        jdbc.update("INSERT INTO moderation_cases(content_type,content_id,status,opened_at) VALUES ('PUBLICACION',?,'PENDIENTE',NOW(6))", "" + hidden);
        long caseId = jdbc.queryForObject("SELECT id FROM moderation_cases WHERE content_type='PUBLICACION' AND content_id=?", Long.class, "" + hidden);
        jdbc.update("INSERT INTO content_moderation_state(content_type,content_id,state,case_id,updated_at) VALUES ('PUBLICACION',?,'OCULTO_TEMPORAL',?,NOW(6))", "" + hidden, caseId);
        try {
            perform(buyer, get("/api/products/{id}", hidden)).andExpect(status().isNotFound());
            perform(buyer, get("/api/recommendations")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.products[*].id", not(hasItem((int) hidden))));
        } finally {
            jdbc.update("DELETE FROM content_moderation_state WHERE case_id=?", caseId);
            jdbc.update("DELETE FROM moderation_cases WHERE id=?", caseId);
        }
    }
}
