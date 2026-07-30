package dev.tirthrajsinh.servicepulse.identity;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
@EnableConfigurationProperties(DevSeedProperties.class)
class DevSeedConfiguration {

    @Bean
    ApplicationRunner seedDevelopmentData(
        DevSeedService seedService,
        DevSeedProperties properties
    ) {
        return arguments -> seedService.seed(properties);
    }
}
