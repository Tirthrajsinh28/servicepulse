package dev.tirthrajsinh.servicepulse.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=false",
    "info.app.version=0.0.1-test"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentsImplementedPathsAndBearerBoundary() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode document = objectMapper.readTree(body);

        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.at("/info/title").asText()).isEqualTo("ServicePulse API");
        assertThat(document.at("/info/version").asText()).isEqualTo("0.0.1-test");
        assertThat(document.at("/paths").size()).isEqualTo(20);
        assertThat(document.at(
            "/paths/~1api~1v1~1workspaces~1{workspaceId}~1notification-jobs~1failed"
        ).isObject()).isTrue();
        assertThat(document.at(
            "/paths/~1api~1v1~1workspaces~1{workspaceId}~1notification-jobs~1{jobId}~1replay/post"
        ).isObject()).isTrue();

        JsonNode scheme = document.at("/components/securitySchemes/bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(document.at("/security/0/bearerAuth").isArray()).isTrue();

        assertPublicOperation(document, "/paths/~1api~1v1~1auth~1register/post");
        assertPublicOperation(document, "/paths/~1api~1v1~1auth~1login/post");
        assertPublicOperation(document, "/paths/~1api~1v1~1auth~1refresh/post");
        assertPublicOperation(document, "/paths/~1api~1v1~1system~1status/get");
        assertThat(document.at("/paths/~1api~1v1~1auth~1me/get/security").isMissingNode())
            .isTrue();
    }

    private void assertPublicOperation(JsonNode document, String operationPath) {
        JsonNode security = document.at(operationPath + "/security");
        assertThat(security.isArray()).isTrue();
        assertThat(security).isEmpty();
    }
}
