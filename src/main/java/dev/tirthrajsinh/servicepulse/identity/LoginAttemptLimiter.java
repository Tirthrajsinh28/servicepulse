package dev.tirthrajsinh.servicepulse.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
class LoginAttemptLimiter {

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    LoginAttemptLimiter(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void assertAllowed(String key) {
        if (!properties.enabled()) {
            return;
        }
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (state) {
            if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
                throw new InvalidCredentialsException();
            }
            if (state.lockedUntil != null) {
                attempts.remove(key, state);
            }
        }
    }

    void recordFailure(String key) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        attempts.compute(key, (ignored, existing) -> {
            AttemptState state = existing == null ? new AttemptState(now) : existing;
            synchronized (state) {
                if (state.windowStartedAt.plus(properties.failureWindow()).isBefore(now)) {
                    state.windowStartedAt = now;
                    state.failureCount = 0;
                    state.lockedUntil = null;
                }
                state.failureCount++;
                if (state.failureCount >= properties.maxFailures()) {
                    state.lockedUntil = now.plus(properties.lockout());
                }
                return state;
            }
        });
    }

    void recordSuccess(String key) {
        if (properties.enabled()) {
            attempts.remove(key);
        }
    }

    private static final class AttemptState {

        private Instant windowStartedAt;
        private int failureCount;
        private Instant lockedUntil;

        private AttemptState(Instant windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
