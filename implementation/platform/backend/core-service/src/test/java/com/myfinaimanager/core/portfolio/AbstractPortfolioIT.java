package com.myfinaimanager.core.portfolio;

import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base for full-slice FD001 integration tests: real HTTP -&gt; real Spring context -&gt; real
 * PostgreSQL (Testcontainers) -&gt; real Flyway migration. Cleans the portfolio tables between
 * tests (the seeded default investor is left in place).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractPortfolioIT extends PostgresContainerSupport {

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void cleanPortfolioTables() {
        jdbc.sql("DELETE FROM position").update();
        jdbc.sql("DELETE FROM portfolio").update();
    }

    protected ResponseEntity<String> post(String key, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        return rest.exchange("/api/portfolios", HttpMethod.POST,
                new HttpEntity<>(jsonBody, headers), String.class);
    }

    protected static String newKey() {
        return "it-" + UUID.randomUUID();
    }

    protected long portfolioCount() {
        return jdbc.sql("SELECT count(*) FROM portfolio").query(Long.class).single();
    }

    protected long positionCount() {
        return jdbc.sql("SELECT count(*) FROM position").query(Long.class).single();
    }
}
