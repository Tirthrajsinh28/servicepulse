package dev.tirthrajsinh.servicepulse.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.configuration.JwtProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthenticationService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final LoginAttemptLimiter loginAttempts;
    private final String dummyPasswordHash;

    AuthenticationService(
        UserAccountRepository users,
        RefreshTokenRepository refreshTokens,
        PasswordEncoder passwordEncoder,
        JwtEncoder jwtEncoder,
        JwtProperties properties,
        SecureRandom secureRandom,
        Clock clock,
        LoginAttemptLimiter loginAttempts
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.loginAttempts = loginAttempts;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    TokenPair login(String email, String password) {
        String normalizedEmail = normalize(email);
        loginAttempts.assertAllowed(normalizedEmail);
        UserAccount user = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            loginAttempts.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException();
        }
        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        if (!user.isEnabled() || !passwordMatches) {
            loginAttempts.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException();
        }
        loginAttempts.recordSuccess(normalizedEmail);
        return issue(user);
    }

    @Transactional
    TokenPair refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
            .filter(token -> token.isUsableAt(now))
            .orElseThrow(InvalidRefreshTokenException::new);
        UserAccount user = users.findById(current.getUserId())
            .filter(UserAccount::isEnabled)
            .orElseThrow(InvalidRefreshTokenException::new);

        UUID replacementId = UUID.randomUUID();
        current.rotateTo(replacementId, now);
        return issue(user, replacementId, now);
    }

    @Transactional
    void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(hash(rawRefreshToken))
            .ifPresent(token -> token.revoke(clock.instant()));
    }

    private TokenPair issue(UserAccount user) {
        return issue(user, UUID.randomUUID(), clock.instant());
    }

    private TokenPair issue(UserAccount user, UUID refreshTokenId, Instant issuedAt) {
        Instant accessExpiry = issuedAt.plus(properties.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(accessExpiry)
            .claim("email", user.getEmail())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();

        String rawRefreshToken = randomRefreshToken();
        RefreshToken refreshToken = new RefreshToken(
            refreshTokenId,
            user.getId(),
            hash(rawRefreshToken),
            issuedAt.plus(properties.refreshTtl()),
            issuedAt
        );
        refreshTokens.save(refreshToken);
        return new TokenPair(
            accessToken,
            rawRefreshToken,
            properties.accessTtl().toSeconds()
        );
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
