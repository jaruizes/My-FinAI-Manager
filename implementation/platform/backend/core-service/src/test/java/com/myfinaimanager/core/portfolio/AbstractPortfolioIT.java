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
 *
 * <p>Since FD002, {@code POST /api/portfolios} validates every position against the Financial
 * Instrument catalog (FR-011). Reference-data startup import is disabled in tests
 * ({@code PostgresContainerSupport}), so this base seeds the catalog rows the FD001 fixtures use
 * (ASML/XAMS/EUR, ASML/XNAS/USD, MSFT/XNAS/USD, SAP/XETR/EUR). Seeding is idempotent and the
 * catalog is left in place between tests (it is reference data, not per-test state).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractPortfolioIT extends PostgresContainerSupport {

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void cleanPortfolioTablesAndSeedCatalog() {
        jdbc.sql("DELETE FROM position").update();
        jdbc.sql("DELETE FROM portfolio").update();
        seedCatalog();
    }

    private void seedCatalog() {
        seedMarket("XAMS", "Euronext Amsterdam");
        seedMarket("XNAS", "Nasdaq Stock Market");
        seedMarket("XETR", "Deutsche Boerse Xetra");
        seedListing("ASML Holding N.V.", "ASML", "XAMS", "EUR");
        seedListing("ASML Holding N.V.", "ASML", "XNAS", "USD");
        seedListing("Microsoft Corporation", "MSFT", "XNAS", "USD");
        seedListing("SAP SE", "SAP", "XETR", "EUR");
    }

    private void seedMarket(String mic, String name) {
        jdbc.sql("INSERT INTO market (mic, name, active) VALUES (:mic, :name, true) "
                        + "ON CONFLICT (mic) DO NOTHING")
                .param("mic", mic).param("name", name).update();
    }

    private void seedListing(String name, String ticker, String mic, String currency) {
        jdbc.sql("INSERT INTO financial_instrument (id, name, ticker, market_mic, currency, active) "
                        + "VALUES (gen_random_uuid(), :name, :ticker, :mic, :ccy, true) "
                        + "ON CONFLICT (ticker, market_mic) DO NOTHING")
                .param("name", name).param("ticker", ticker).param("mic", mic).param("ccy", currency)
                .update();
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
