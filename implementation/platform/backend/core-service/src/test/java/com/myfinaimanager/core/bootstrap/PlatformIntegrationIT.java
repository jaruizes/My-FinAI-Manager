package com.myfinaimanager.core.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.support.PostgresContainerSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EN001 integration baseline (VC-004, VC-006).
 *
 * <p>Boots the full application context against a real, disposable PostgreSQL container and proves:
 * <ul>
 *   <li>Flyway applied the baseline migration ({@code flyway_schema_history} exists);</li>
 *   <li>the backend can open a connection and run a trivial query ({@code SELECT 1});</li>
 *   <li>the Actuator health endpoint reports {@code UP} including the {@code db} component.</li>
 * </ul>
 *
 * <p>No manually installed database is required; the container is disposed automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformIntegrationIT extends PostgresContainerSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void flyway_baseline_is_applied() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer historyRows = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history", Integer.class);

        assertThat(historyRows).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    void backend_can_reach_postgres() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void actuator_health_is_up_with_db_component() {
        var response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).contains("\"db\"");
    }
}
