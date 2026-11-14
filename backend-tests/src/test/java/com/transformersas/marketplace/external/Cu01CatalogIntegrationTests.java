package com.transformersas.marketplace.external;

import com.transformersas.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-01: public catalog and persisted product details, without replacing backend responses. */
class Cu01CatalogIntegrationTests extends AbstractIntegrationTest {
    @Test
    void visitorCanBrowseAndReadStoredProductDetails() throws Exception {
        long id = seedProduct(1, "Lampara CU01", 8, "55000");
        jdbc.update("UPDATE products SET description='Para lectura' WHERE id=?", id);
        jdbc.update("INSERT INTO product_images(product_id,position,url) VALUES (?,0,'https://example.test/lamp.png')", id);
        jdbc.update("INSERT INTO product_variants(product_id,position,name,price,stock) VALUES (?,0,'Negra',55000,8)", id);
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Lampara CU01"));
        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Para lectura"))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://example.test/lamp.png"))
                .andExpect(jsonPath("$.variants[0].name").value("Negra"))
                .andExpect(jsonPath("$.store.id").value(1))
                .andExpect(jsonPath("$.store.ownerAccountId").doesNotExist());
    }

    @Test
    void retiredAndMissingProductsCannotBeRead() throws Exception {
        long id = seedProduct(1, "Retirado CU01", 2, "10000");
        jdbc.update("UPDATE products SET active=FALSE,status='RETIRED' WHERE id=?", id);
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void emptyCatalogIsSuccessful() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
