package dev.tirthrajsinh.servicepulse.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "servicepulse.dev-seed")
record DevSeedProperties(
    String email,
    String password,
    String displayName,
    String workspaceName,
    String workspaceSlug,
    String serviceName,
    String serviceSlug
) {
}
