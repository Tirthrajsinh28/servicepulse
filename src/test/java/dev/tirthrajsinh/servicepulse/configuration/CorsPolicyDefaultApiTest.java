package dev.tirthrajsinh.servicepulse.configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CorsPolicyDefaultApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void omitsCrossOriginHeadersWhenNoOriginAllowlistIsConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/system/status")
                .header("Origin", "https://portfolio.example.test"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
