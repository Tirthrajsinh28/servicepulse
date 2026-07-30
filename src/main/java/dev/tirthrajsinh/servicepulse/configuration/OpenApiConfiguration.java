package dev.tirthrajsinh.servicepulse.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI servicePulseOpenApi(@Value("${info.app.version:development}") String version) {
        return new OpenAPI()
            .info(new Info()
                .title("ServicePulse API")
                .version(version)
                .description(
                    "Workspace-scoped incident operations API for the "
                    + "independent ServicePulse portfolio project."
                ))
            .components(new Components().addSecuritySchemes(
                BEARER_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
