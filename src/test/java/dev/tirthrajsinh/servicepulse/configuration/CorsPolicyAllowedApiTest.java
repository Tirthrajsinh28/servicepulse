package dev.tirthrajsinh.servicepulse.configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "servicepulse.security.cors.allowed-origins=https://portfolio.example.test,http://localhost:5173"
})
@AutoConfigureMockMvc
class CorsPolicyAllowedApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsPreflightFromExplicitlyAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/system/status")
                .header("Origin", "https://portfolio.example.test")
                .header("Access-Control-Request-Method", "GET")
                .header(
                    "Access-Control-Request-Headers",
                    "Authorization, Content-Type, X-Request-ID"
                ))
            .andExpect(status().isOk())
            .andExpect(header().string(
                "Access-Control-Allow-Origin",
                "https://portfolio.example.test"
            ))
            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
            .andExpect(header().string(
                "Access-Control-Allow-Headers",
                org.hamcrest.Matchers.containsString("X-Request-ID")
            ));
    }

    @Test
    void rejectsPreflightFromUnlistedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/system/status")
                .header("Origin", "https://attacker.example.test")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
