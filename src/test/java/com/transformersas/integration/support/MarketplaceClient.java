package com.transformersas.integration.support;

import com.transformersas.integration.config.BackendConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Cliente HTTP de una sesión del Marketplace: la cookie de sesión y el token CSRF que el backend exige en las
 * peticiones que modifican datos. Solo usa la API pública, igual que el frontend. Cada instancia es una sesión
 * independiente, así que una misma cuenta puede actuar como comprador en una y como vendedor en otra.
 */
public final class MarketplaceClient {

    /** Cabecera con la que el vendedor dice sobre qué tienda actúa; sin ella se usa la tienda de su cuenta. */
    private static final String STORE_HEADER = "X-Store-Id";

    private final String baseUrl;
    private String sessionCookie;
    private String csrfHeader;
    private String csrfToken;

    private MarketplaceClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Inicia sesión con la cuenta configurada (por defecto la del perfil local) y deja activo el rol indicado. */
    public static MarketplaceClient loginAs(String role) {
        return loginAs(BackendConfiguration.accountEmail(), BackendConfiguration.accountPassword(), role);
    }

    /** Inicia sesión con una cuenta distinta a la configurada por defecto (por ejemplo, la de soporte). */
    public static MarketplaceClient loginAs(String email, String password, String role) {
        MarketplaceClient client = new MarketplaceClient(BackendConfiguration.baseUrl());
        client.login(email, password);
        client.selectActiveRole(role);
        return client;
    }

    /** Cliente sin sesión, para comprobar lo que el backend responde a un visitante. */
    public static MarketplaceClient anonymous() {
        return new MarketplaceClient(BackendConfiguration.baseUrl());
    }

    private void login(String email, String password) {
        Response anonymous = given().baseUri(baseUrl).get("/api/auth/csrf");
        anonymous.then().statusCode(200);

        Response login = given().baseUri(baseUrl).redirects().follow(false)
                .cookie("SESSION", anonymous.getCookie("SESSION"))
                .header(anonymous.jsonPath().getString("headerName"), anonymous.jsonPath().getString("token"))
                .formParam("email", email).formParam("password", password)
                .post("/api/auth/login");
        if (login.statusCode() != 204) {
            throw new IllegalStateException("El login de " + email + " respondió HTTP " + login.statusCode()
                    + ". Verificar el perfil local del backend y E2E_EMAIL / E2E_PASSWORD.");
        }
        sessionCookie = login.getCookie("SESSION");
        refreshCsrf();
    }

    private void selectActiveRole(String role) {
        Response response = put("/api/auth/active-role", "{\"role\":\"" + role + "\"}");
        if (response.statusCode() != 200) {
            throw new IllegalStateException("No se pudo activar el rol " + role + ": HTTP " + response.statusCode()
                    + " " + response.asString());
        }
        // El token CSRF cambia al cambiar de rol: hay que pedir el nuevo, como hace el frontend.
        refreshCsrf();
    }

    private void refreshCsrf() {
        Response response = given().baseUri(baseUrl).cookie("SESSION", sessionCookie).get("/api/auth/csrf");
        response.then().statusCode(200);
        csrfHeader = response.jsonPath().getString("headerName");
        csrfToken = response.jsonPath().getString("token");
    }

    public Response get(String path) {
        return request().get(path);
    }

    /** GET de vendedor sobre una tienda concreta (X-Store-Id), para comprobar qué ve de una tienda ajena. */
    public Response get(String path, long storeId) {
        return request().header(STORE_HEADER, storeId).get(path);
    }

    public Response post(String path, String jsonBody) {
        return withCsrf(request()).contentType(ContentType.JSON).body(jsonBody).post(path);
    }

    /** POST de una acción sin datos, como los botones del frontend: el backend espera un objeto JSON vacío. */
    public Response post(String path) {
        return post(path, "{}");
    }

    public Response put(String path, String jsonBody) {
        return withCsrf(request()).contentType(ContentType.JSON).body(jsonBody).put(path);
    }

    public Response delete(String path) {
        return withCsrf(request()).delete(path);
    }

    /** POST con un archivo adjunto (multipart), como el que sube el panel de inventario al cargar un Excel. */
    public Response postFile(String path, String fieldName, String fileName, byte[] content, String contentType) {
        return withCsrf(request()).multiPart(fieldName, fileName, content, contentType).post(path);
    }

    private RequestSpecification request() {
        RequestSpecification spec = given().baseUri(baseUrl).redirects().follow(false);
        return sessionCookie == null ? spec : spec.cookie("SESSION", sessionCookie);
    }

    private RequestSpecification withCsrf(RequestSpecification spec) {
        return csrfHeader == null ? spec : spec.header(csrfHeader, csrfToken);
    }
}
