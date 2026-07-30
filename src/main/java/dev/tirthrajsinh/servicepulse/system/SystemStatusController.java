package dev.tirthrajsinh.servicepulse.system;

import java.time.Clock;
import java.time.Instant;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemStatusController {

    private final Clock clock;
    private final String version;

    SystemStatusController(Clock clock, @Value("${info.app.version:development}") String version) {
        this.clock = clock;
        this.version = version;
    }

    @GetMapping("/status")
    @SecurityRequirements
    SystemStatus status() {
        return new SystemStatus("servicepulse", "UP", version, clock.instant());
    }

    record SystemStatus(String service, String status, String version, Instant timestamp) {
    }
}

@Configuration
class TimeConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
