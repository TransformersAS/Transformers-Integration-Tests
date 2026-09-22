package com.transformersas.integration.support;

import io.restassured.response.Response;

import java.util.List;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Compra del producto que aprovisiona el perfil local, con las mismas llamadas que hace el frontend: carrito,
 * dirección, reserva de stock, cálculo del total y pago con la tarjeta simulada que el backend aprueba.
 *
 * <p>El backend todavía tiene un único carrito compartido por todos los compradores, así que se vacía antes de comprar
 * y no se pueden encadenar compras en paralelo.
 */
public final class Purchases {

    public static final String DEMO_PRODUCT = "Camiseta demo local";

    private Purchases() {
    }

    /** Compra una unidad del producto demo y devuelve el id del pedido creado (queda CONFIRMED). */
    public static long buyDemoShirt(MarketplaceClient buyer) {
        emptyCart(buyer);

        Response products = buyer.get("/api/products");
        products.then().statusCode(200);
        Integer productId = products.jsonPath().getInt("find { it.name == '" + DEMO_PRODUCT + "' }.id");
        if (productId == null) {
            throw new IllegalStateException("El perfil local debe aprovisionar «" + DEMO_PRODUCT + "»");
        }

        buyer.post("/api/cart/items", "{\"productId\":%d,\"quantity\":1}".formatted(productId))
                .then().statusCode(anyOf(is(200), is(201)));

        Response address = buyer.post("/api/addresses", """
                {"recipientName":"Ana Integracion","street":"Calle 123 # 45-67","city":"Bogotá",\
                "department":"Cundinamarca","postalCode":"110111","phone":"3000000000"}""");
        address.then().statusCode(anyOf(is(200), is(201)));
        long addressId = address.jsonPath().getLong("id");

        Response reservations = buyer.post("/api/reservations/cart");
        reservations.then().statusCode(anyOf(is(200), is(201)));
        List<Integer> reservationIds = reservations.jsonPath().getList("id");

        // El backend recalcula el total por su cuenta; esta llamada es la que hace el frontend antes de pagar.
        buyer.post("/api/checkout/preview", "{\"addressId\":%d,\"shippingMethod\":\"STANDARD\"}".formatted(addressId))
                .then().statusCode(200);

        Response payment = buyer.post("/api/payments/process", """
                {"paymentMethod":"CARD","reservationIds":%s,"addressId":%d,"shippingMethod":"STANDARD"}"""
                .formatted(reservationIds, addressId));
        payment.then().statusCode(anyOf(is(200), is(201)));
        Long orderId = payment.jsonPath().getLong("orderId");
        if (orderId == null || orderId == 0) {
            throw new IllegalStateException("El pago no creó ningún pedido: " + payment.asString());
        }
        return orderId;
    }

    private static void emptyCart(MarketplaceClient buyer) {
        Response cart = buyer.get("/api/cart");
        cart.then().statusCode(200);
        List<Integer> itemIds = cart.jsonPath().getList("items.id");
        if (itemIds != null) {
            itemIds.forEach(id -> buyer.delete("/api/cart/items/" + id));
        }
    }
}
