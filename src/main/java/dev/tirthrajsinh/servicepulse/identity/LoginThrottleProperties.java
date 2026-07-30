package dev.tirthrajsinh.servicepulse.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "servicepulse.security.login-throttle")
public record LoginThrottleProperties(
    boolean enabled,
    int maxFailures,
    Duration failureWindow,
    Duration lockout
) {

    public LoginThrottleProperties {
        if (maxFailures < 1) {
            throw new IllegalStateException("Login throttle max failures must be positive");
        }
        if (failureWindow == null || failureWindow.isNegative() || failureWindow.isZero()) {
            throw new IllegalStateException("Login throttle failure window must be positive");
        }
        if (lockout == null || lockout.isNegative() || lockout.isZero()) {
            throw new IllegalStateException("Login throttle lockout must be positive");
        }
    }
}
