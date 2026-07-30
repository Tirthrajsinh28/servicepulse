package dev.tirthrajsinh.servicepulse.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.tirthrajsinh.servicepulse.configuration.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SystemStatusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesStatusWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("servicepulse"))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void exposesHealthWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectsMetricsFromAnonymousRequests() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsBusinessRoutesByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/" + java.util.UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void preservesValidRequestIdAndCleansMdc() throws Exception {
        mockMvc.perform(get("/api/v1/system/status")
                .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "client-request_123"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestCorrelationFilter.REQUEST_ID_HEADER,
                "client-request_123"
            ));

        org.assertj.core.api.Assertions.assertThat(
            MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)
        ).isNull();
    }

    @Test
    void replacesInvalidOrOversizedRequestIdsWithUuid() throws Exception {
        MvcResult invalid = mockMvc.perform(get("/api/v1/system/status")
                .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "invalid request id"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestCorrelationFilter.REQUEST_ID_HEADER,
                org.hamcrest.Matchers.matchesPattern(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                )
            ))
            .andReturn();
        MvcResult oversized = mockMvc.perform(get("/actuator/prometheus")
                .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "a".repeat(65)))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
            .andReturn();

        org.assertj.core.api.Assertions.assertThat(
            invalid.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)
        ).isNotEqualTo("invalid request id");
        org.assertj.core.api.Assertions.assertThat(
            oversized.getResponse().getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)
        ).isNotEqualTo("a".repeat(65));
        org.assertj.core.api.Assertions.assertThat(
            MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)
        ).isNull();
    }
}
