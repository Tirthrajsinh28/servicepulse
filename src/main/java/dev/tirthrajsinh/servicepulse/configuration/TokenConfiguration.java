package dev.tirthrajsinh.servicepulse.configuration;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import dev.tirthrajsinh.servicepulse.identity.LoginThrottleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, LoginThrottleProperties.class})
class TokenConfiguration {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey key = secretKey(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(secretKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(required(properties.issuer(), "issuer")));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    private SecretKey secretKey(JwtProperties properties) {
        String encoded = required(properties.secretBase64(), "secret-base64");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be valid Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 decoded bytes");
        }
        if (properties.accessTtl() == null || properties.accessTtl().isNegative()
            || properties.accessTtl().isZero()) {
            throw new IllegalStateException("JWT access TTL must be positive");
        }
        if (properties.refreshTtl() == null || properties.refreshTtl().isNegative()
            || properties.refreshTtl().isZero()) {
            throw new IllegalStateException("JWT refresh TTL must be positive");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("JWT " + property + " is required");
        }
        return value;
    }
}
