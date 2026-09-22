package com.transformersas.integration.support;

import io.restassured.response.Response;

/**
 * Un caso de moderación real de CU-20 contra la publicación de otra tienda, para tener algo que CU-21 pueda moderar.
 * No se puede reportar contenido propio (REPORT_OWN_CONTENT) y la cuenta demo local es dueña de la tienda 1 tanto
 * como comprador que como vendedor, así que se publica en la tienda vecina que scripts/cu20-demo-seed.sh siembra en
 * el backend (ver README). Solo puede haber un caso abierto por publicación, y retirar un contenido lo deja
 * permanentemente no reportable: cada caso que se quiera abrir necesita su propia publicación. Publicar exige al
 * menos una imagen y una categoría activa (aquí, «Hogar»: hay que sembrarla, ver README) — no se puede crear desde
 * la API con las cuentas de prueba, es solo-ADMIN.
 */
public final class Reports {

    private Reports() {
    }

    /** Publica un producto nuevo en la tienda vecina, listo para reportar. */
    public static long newNeighborProduct(MarketplaceClient neighborSeller, String name) {
        Response created = neighborSeller.post("/api/seller/products", """
                {"name":"%s","description":"Producto de prueba para CU-20/CU-21","price":10000,"stock":5,\
                "category":"Hogar","imageUrls":["https://example.test/producto.jpg"]}""".formatted(name));
        created.then().statusCode(201);
        long productId = created.jsonPath().getLong("id");
        neighborSeller.post("/api/seller/products/" + productId + "/publish").then().statusCode(200);
        return productId;
    }

    /** El reportante radica un reporte real contra esa publicación (CU-20); trae el id del reporte y del caso de
     * moderación que abre o al que se suma. */
    public static Response openCase(MarketplaceClient reporter, long productId, String reason) {
        Response submitted = reporter.post("/api/reports", """
                {"contentType":"PUBLICACION","contentId":"%d","reason":"%s","description":"Reporte de prueba"}"""
                .formatted(productId, reason));
        submitted.then().statusCode(201);
        return submitted;
    }
}
