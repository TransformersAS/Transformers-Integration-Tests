package com.transformersas.integration.config;

public final class BackendConfiguration {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    // Cuenta del perfil local del backend (COMPRADOR + VENDEDOR, dueña de la tienda 1). Mismas variables que Playwright.
    private static final String DEFAULT_EMAIL = "demo@marketplace.local";
    private static final String DEFAULT_PASSWORD = "MarketplaceDemo123!";

    private BackendConfiguration() {
    }

    public static String baseUrl() {
        String configuredUrl = System.getenv("BACKEND_BASE_URL");
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        return configuredUrl.trim().replaceAll("/+$", "");
    }

    public static String accountEmail() {
        return valueOrDefault("E2E_EMAIL", DEFAULT_EMAIL);
    }

    public static String accountPassword() {
        return valueOrDefault("E2E_PASSWORD", DEFAULT_PASSWORD);
    }

    /**
     * Secreto con el que el backend verifica la firma del webhook logístico (LOGISTICS_WEBHOOK_SECRET). Debe ser el
     * mismo con el que arrancó el backend: si no se define, el webhook está cerrado y no hay nada que probar.
     */
    public static String webhookSecret() {
        String secret = System.getenv("LOGISTICS_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Define LOGISTICS_WEBHOOK_SECRET con el mismo valor con el que arrancó el backend");
        }
        return secret.trim();
    }

    private static String valueOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
