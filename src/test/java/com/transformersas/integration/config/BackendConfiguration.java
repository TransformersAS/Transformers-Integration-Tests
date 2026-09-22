package com.transformersas.integration.config;

public final class BackendConfiguration {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    // Cuenta del perfil local del backend (COMPRADOR + VENDEDOR, dueña de la tienda 1). Mismas variables que Playwright.
    private static final String DEFAULT_EMAIL = "demo@marketplace.local";
    private static final String DEFAULT_PASSWORD = "MarketplaceDemo123!";
    // Cuenta de soporte (rol SOPORTE) que crea scripts/cu20-demo-seed.sh en Transformers-AS: el rol no se autoregistra
    // por API, hay que sembrarla antes de correr estas pruebas. Misma contraseña que DEMO_PASSWORD por convención.
    private static final String DEFAULT_SUPPORT_EMAIL = "soporte.demo@example.com";
    // Cuenta VENDEDOR de la tienda vecina, sembrada por el mismo script: CU-20/CU-21 necesitan publicar contenido de
    // otra tienda para poder reportarlo (no se puede reportar contenido propio).
    private static final String DEFAULT_NEIGHBOR_EMAIL = "vecino.demo@example.com";

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

    public static String supportEmail() {
        return valueOrDefault("E2E_SUPPORT_EMAIL", DEFAULT_SUPPORT_EMAIL);
    }

    public static String supportPassword() {
        return valueOrDefault("E2E_SUPPORT_PASSWORD", DEFAULT_PASSWORD);
    }

    public static String neighborEmail() {
        return valueOrDefault("E2E_NEIGHBOR_EMAIL", DEFAULT_NEIGHBOR_EMAIL);
    }

    public static String neighborPassword() {
        return valueOrDefault("E2E_NEIGHBOR_PASSWORD", DEFAULT_PASSWORD);
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
