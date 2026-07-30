package dev.tirthrajsinh.servicepulse.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "dev"})
@SpringBootTest(properties = {
    "servicepulse.dev-seed.email=dev-admin@example.test",
    "servicepulse.dev-seed.password=local-test-password-only",
    "servicepulse.dev-seed.display-name=Development Administrator",
    "servicepulse.dev-seed.workspace-name=Northstar Labs",
    "servicepulse.dev-seed.workspace-slug=northstar-labs",
    "servicepulse.dev-seed.service-name=Checkout API",
    "servicepulse.dev-seed.service-slug=checkout-api"
})
class DevSeedIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DevSeedService seedService;

    @Autowired
    private DevSeedProperties properties;

    @Test
    void seedIsPresentAndIdempotent() {
        seedService.seed(properties);

        assertThat(count("select count(*) from users where email = 'dev-admin@example.test'"))
            .isEqualTo(1);
        assertThat(count("select count(*) from workspaces where slug = 'northstar-labs'"))
            .isEqualTo(1);
        assertThat(count(
            """
            select count(*)
            from workspace_members m
            join users u on u.id = m.user_id
            where u.email = 'dev-admin@example.test' and m.role = 'ADMIN'
            """
        )).isEqualTo(1);
        assertThat(count("select count(*) from services where slug = 'checkout-api'"))
            .isEqualTo(1);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
