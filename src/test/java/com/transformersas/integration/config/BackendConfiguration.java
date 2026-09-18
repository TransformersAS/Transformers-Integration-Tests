package com.transformersas.integration.config;

public final class BackendConfiguration {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private BackendConfiguration() {
    }

    public static String baseUrl() {
        String configuredUrl = System.getenv("BACKEND_BASE_URL");
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        return configuredUrl.trim().replaceAll("/+$", "");
    }
}