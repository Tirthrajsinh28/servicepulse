package dev.tirthrajsinh.servicepulse.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "servicepulse.security.jwt")
public record JwtProperties(
    String issuer,
    String secretBase64,
    Duration accessTtl,
    Duration refreshTtl
) {
}
