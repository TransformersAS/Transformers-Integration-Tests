package com.transformersas.integration.stock;

import com.transformersas.integration.support.MarketplaceScenario;
import com.transformersas.integration.support.MinimalXlsx;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CU-15: cargas masivas con plantillas de Excel. Verifica el contrato externo de {@code /api/seller/inventory/templates/*}
 * y {@code /api/seller/inventory/imports/*} sin depender de datos sembrados por fuera del perfil local.
 *
 * <p>El perfil local no aprovisiona ninguna categoría activa (las crea el rol ADMIN, sin cuenta demo en este
 * entorno), así que la creación EXITOSA de un producto por Excel no se prueba aquí; se prueba en cambio su rechazo
 * «todo o nada», que no depende de ningún dato sembrado. Queda documentado en {@code docs/pruebas-cu13-cu15.md}.
 */
@DisplayName("CU-15 Cargas masivas con Excel")
class SellerExcelIntegrationTest extends MarketplaceScenario {

    private static final String INVENTORY = "/api/seller/inventory";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final List<String> PRODUCT_HEADERS =
            List.of("Nombre", "Descripción", "Precio", "Inventario", "Categoría", "Marca", "Imágenes", "Publicar");
    private static final List<String> STOCK_HEADERS =
            List.of("ID producto", "Producto", "Stock actual", "Reservado", "Tipo", "Cantidad", "Motivo");

    @Test
    @DisplayName("La plantilla de productos se descarga como un .xlsx real")
    void theProductsTemplateDownloadsAsARealWorkbook() {
        Response response = seller.get(INVENTORY + "/templates/products");
        response.then().statusCode(200).header("Content-Type", containsString(XLSX))
                .header("Content-Disposition", containsString("plantilla-productos.xlsx"));

        byte[] body = response.asByteArray();
        assertTrue(body.length > 0, "la plantilla no debería venir vacía");
        assertEquals('P', (char) body[0]); // firma ZIP: todo .xlsx es un .zip
        assertEquals('K', (char) body[1]);
    }

    @Test
    @DisplayName("La plantilla de inventario se descarga como un .xlsx real")
    void theStockTemplateDownloadsAsARealWorkbook() {
        seller.get(INVENTORY + "/templates/stock").then().statusCode(200)
                .header("Content-Type", containsString(XLSX))
                .header("Content-Disposition", containsString("plantilla-inventario.xlsx"));
    }

    @Test
    @DisplayName("Una fila con una categoría que no existe rechaza todo el archivo, con su número de fila")
    void aRowWithAnUnknownCategoryRejectsTheWholeFile() {
        byte[] file = MinimalXlsx.build(PRODUCT_HEADERS, List.of(
                List.of("Producto de prueba", "", "1000", "1", "CategoríaQueNoExisteJamas")));

        seller.postFile(INVENTORY + "/imports/products", "file", "productos.xlsx", file, XLSX)
                .then().statusCode(400).body("code", equalTo("EXCEL_ROW_ERRORS"))
                .body("details", hasSize(1)).body("details[0].row", equalTo(2))
                .body("details[0].message", containsString("categoría"));
    }

    @Test
    @DisplayName("Un archivo que no es un Excel real se rechaza")
    void aFileThatIsNotARealExcelWorkbookIsRejected() {
        seller.postFile(INVENTORY + "/imports/products", "file", "falso.xlsx",
                "esto no es un excel".getBytes(), XLSX)
                .then().statusCode(400).body("code", equalTo("EXCEL_INVALID_FILE"));
    }

    @Test
    @DisplayName("Un archivo con encabezados distintos a la plantilla se rechaza")
    void aFileWithTheWrongHeadersIsRejected() {
        byte[] file = MinimalXlsx.build(List.of("A", "B"), List.of(List.of("1", "2")));

        seller.postFile(INVENTORY + "/imports/products", "file", "otra-cosa.xlsx", file, XLSX)
                .then().statusCode(400).body("code", equalTo("EXCEL_WRONG_TEMPLATE"));
    }

    @Test
    @DisplayName("Un archivo de inventario sin ninguna fila de datos se rechaza")
    void aStockFileWithNoDataRowsIsRejected() {
        byte[] file = MinimalXlsx.build(STOCK_HEADERS, List.of());

        seller.postFile(INVENTORY + "/imports/stock", "file", "inventario.xlsx", file, XLSX)
                .then().statusCode(400).body("code", equalTo("EXCEL_EMPTY"));
    }

    @Test
    @DisplayName("Solo un vendedor con el rol activo usa las plantillas y las cargas")
    void onlyASellerUsesTheExcelEndpoints() {
        buyer.get(INVENTORY + "/templates/products").then().statusCode(403)
                .body("code", equalTo("SELLER_ROLE_REQUIRED"));
    }
}
