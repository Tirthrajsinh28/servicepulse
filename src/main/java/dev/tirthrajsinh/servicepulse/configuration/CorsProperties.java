package dev.tirthrajsinh.servicepulse.configuration;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "servicepulse.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toList();
        for (String origin : allowedOrigins) {
            if ("*".equals(origin)) {
                throw new IllegalStateException("CORS allowed origins must be explicit");
            }
            if (!origin.startsWith("https://")
                && !origin.startsWith("http://localhost")
                && !origin.startsWith("http://127.0.0.1")) {
                throw new IllegalStateException(
                    "CORS allowed origins must be HTTPS or local development origins"
                );
            }
            if (origin.endsWith("/")) {
                throw new IllegalStateException("CORS allowed origins must omit trailing slash");
            }
        }
    }

    boolean hasAllowedOrigins() {
        return !allowedOrigins.isEmpty();
    }
}
