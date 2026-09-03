package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FD001 full-slice smoke test (VC-006 for this feature): {@code POST /api/portfolios} through the
 * real controller, service, JDBC adapter, Testcontainers PostgreSQL and Flyway migration
 * {@code V2__portfolio.sql}. Also proves the migration + default-investor seed are in place.
 */
class CreatePortfolioIT extends AbstractPortfolioIT {

    @Test
    void migration_v2_and_the_default_investor_seed_are_applied() {
        Long historyRows = jdbc.sql(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2'").query(Long.class).single();
        assertThat(historyRows).isEqualTo(1);

        String owner = jdbc.sql("SELECT display_name FROM investor "
                + "WHERE id = '00000000-0000-0000-0000-000000000001'").query(String.class).single();
        assertThat(owner).isEqualTo("Default Investor");
    }

    @Test
    void creates_a_portfolio_and_persists_it_owned_by_the_default_investor() {
        String body = """
                { "name": "Long-Term Growth",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "12", "currency": "EUR" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getBody()).contains("\"status\":\"ACTIVE\"", "\"name\":\"Long-Term Growth\"", "ASML");

        assertThat(portfolioCount()).isEqualTo(1);
        assertThat(positionCount()).isEqualTo(1);

        String ownerId = jdbc.sql("SELECT investor_id::text FROM portfolio").query(String.class).single();
        assertThat(ownerId).isEqualTo("00000000-0000-0000-0000-000000000001"); // FR-030, SC-012

        // stored exactly as entered, optionals NULL (SC-004, SC-006, BR-009)
        var row = jdbc.sql("""
                        SELECT ticker, market, quantity::text q, currency,
                               initial_purchase_date, average_purchase_price
                        FROM position
                        """)
                .query((rs, n) -> rs.getString("ticker") + "|" + rs.getString("market") + "|"
                        + rs.getString("q") + "|" + rs.getString("currency") + "|"
                        + rs.getObject("initial_purchase_date") + "|" + rs.getObject("average_purchase_price"))
                .single();
        assertThat(row).isEqualTo("ASML|XAMS|12|EUR|null|null");
    }
}
