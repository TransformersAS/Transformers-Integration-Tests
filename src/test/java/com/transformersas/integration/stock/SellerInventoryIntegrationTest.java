package com.transformersas.integration.stock;

import com.transformersas.integration.support.MarketplaceClient;
import com.transformersas.integration.support.MarketplaceScenario;
import com.transformersas.integration.support.Purchases;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * CU-15: el vendedor controla el inventario de su tienda. Verifica el contrato externo de {@code /api/seller/inventory}
 * sobre el sistema desplegado, usando el único producto que aprovisiona el perfil local.
 *
 * <p>Todas las mutaciones sobre ese producto son aditivas o se revierten al final de su propio método: otras clases de
 * este repositorio lo compran repetidamente, así que ninguna prueba puede dejarlo con menos stock del que tenía al
 * empezar, ni con un mínimo distinto de 0.
 */
@DisplayName("CU-15 Controlar el inventario y el reabastecimiento")
class SellerInventoryIntegrationTest extends MarketplaceScenario {

    private static final String INVENTORY = "/api/seller/inventory";

    private long demoProductId() {
        Response products = seller.get("/api/products");
        products.then().statusCode(200);
        return products.jsonPath().getLong("find { it.name == '" + Purchases.DEMO_PRODUCT + "' }.id");
    }

    private int currentStock(long productId) {
        Response items = seller.get(INVENTORY);
        items.then().statusCode(200);
        return items.jsonPath().getInt("find { it.productId == " + productId + " }.stock");
    }

    @Test
    @DisplayName("Las existencias muestran el físico, lo reservado y lo disponible del producto demo")
    void inventoryListsTheDemoProductWithItsFigures() {
        long productId = demoProductId();

        seller.get(INVENTORY).then().statusCode(200)
                .body("find { it.productId == " + productId + " }.name", equalTo(Purchases.DEMO_PRODUCT))
                .body("find { it.productId == " + productId + " }.stock", notNullValue())
                .body("find { it.productId == " + productId + " }.reserved", notNullValue())
                .body("find { it.productId == " + productId + " }.available", notNullValue());
    }

    @Test
    @DisplayName("Una entrada suma unidades y queda como el primer movimiento del historial")
    void anEntryAddsStockAndIsRecordedInTheHistory() {
        long productId = demoProductId();
        int before = currentStock(productId);

        seller.post(INVENTORY + "/" + productId + "/entries",
                "{\"quantity\":3,\"reason\":\"Prueba de integración\"}")
                .then().statusCode(201).body("stock", equalTo(before + 3));

        seller.get(INVENTORY + "/" + productId + "/movements").then().statusCode(200)
                .body("[0].type", equalTo("ENTRY")).body("[0].quantity", equalTo(3))
                .body("[0].stockAfter", equalTo(before + 3))
                .body("[0].reason", equalTo("Prueba de integración"));
    }

    @Test
    @DisplayName("Un ajuste fija el conteo real y exige un motivo")
    void anAdjustmentSetsTheExactCountAndRequiresAReason() {
        long productId = demoProductId();
        int before = currentStock(productId);

        // Solo sube el conteo, para no dejar el producto con menos stock del que necesitan las demás pruebas.
        seller.post(INVENTORY + "/" + productId + "/adjustments",
                "{\"newStock\":%d,\"reason\":\"Conteo de prueba\"}".formatted(before + 5))
                .then().statusCode(201).body("stock", equalTo(before + 5));

        seller.post(INVENTORY + "/" + productId + "/adjustments", "{\"newStock\":%d}".formatted(before + 6))
                .then().statusCode(400).body("message", containsString("reason"));
    }

    @Test
    @DisplayName("El mínimo enciende la alerta de stock bajo y se puede apagar de nuevo")
    void theMinimumTurnsTheLowStockAlertOnAndOff() {
        long productId = demoProductId();
        int stock = currentStock(productId);

        seller.put(INVENTORY + "/" + productId + "/minimum", "{\"minStock\":%d}".formatted(stock + 1000))
                .then().statusCode(200).body("lowStock", equalTo(true));

        // Se restaura a "sin aviso": el mínimo queda compartido con las demás pruebas de este mismo producto.
        seller.put(INVENTORY + "/" + productId + "/minimum", "{\"minStock\":0}")
                .then().statusCode(200).body("lowStock", equalTo(false));
    }

    @Test
    @DisplayName("Solo un vendedor con el rol activo, dueño de su tienda, usa el inventario")
    void onlyASellerWithTheActiveRoleUsesTheInventory() {
        buyer.get(INVENTORY).then().statusCode(403).body("code", equalTo("SELLER_ROLE_REQUIRED"));
        seller.get(INVENTORY, 999L).then().statusCode(403).body("code", equalTo("STORE_NOT_AUTHORIZED"));
        MarketplaceClient.anonymous().get(INVENTORY).then().statusCode(401);
    }
}
