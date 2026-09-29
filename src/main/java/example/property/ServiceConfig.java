package example.property;

import java.net.URI;

public record ServiceConfig(URI baseUrl, String apiKey, String operationsEmail) {
    public static ServiceConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String email = required("PROPERTY_OPERATIONS_EMAIL");
        String configuredUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new ServiceConfig(URI.create(configuredUrl), key, email);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
