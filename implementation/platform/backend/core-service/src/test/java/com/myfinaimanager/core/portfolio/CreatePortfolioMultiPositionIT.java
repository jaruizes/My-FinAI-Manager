package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * US2 — several positions in one portfolio, created with a single Save.
 *
 * <ul>
 *   <li>AC-002 — multiple distinct instruments persist under one portfolio.</li>
 *   <li>BR-003 — the same ticker on two different markets is two valid positions.</li>
 *   <li>SC-002 — a portfolio with ≥ 10 positions is created within the local performance budget.</li>
 * </ul>
 */
class CreatePortfolioMultiPositionIT extends AbstractPortfolioIT {

    @Test
    void several_distinct_positions_persist_under_one_portfolio() { // AC-002
        String body = """
                { "name": "Diversified",
                  "positions": [
                    { "ticker": "ASML", "market": "XAMS", "quantity": "3", "currency": "EUR" },
                    { "ticker": "MSFT", "market": "XNAS", "quantity": "10", "currency": "USD" },
                    { "ticker": "SAP",  "market": "XETR", "quantity": "5", "currency": "EUR" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(portfolioCount()).isEqualTo(1);
        assertThat(positionCount()).isEqualTo(3);
    }

    @Test
    void the_same_ticker_on_two_markets_is_two_positions() { // BR-003
        String body = """
                { "name": "Cross-Listed",
                  "positions": [
                    { "ticker": "ASML", "market": "XAMS", "quantity": "3", "currency": "EUR" },
                    { "ticker": "ASML", "market": "XNAS", "quantity": "2", "currency": "USD" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(positionCount()).isEqualTo(2);
        long markets = jdbc.sql("SELECT count(distinct market) FROM position WHERE ticker = 'ASML'")
                .query(Long.class).single();
        assertThat(markets).isEqualTo(2);
    }

    @Test
    void a_portfolio_with_at_least_ten_positions_is_created_quickly() { // SC-002
        String positions = IntStream.range(0, 12)
                .mapToObj(i -> "{ \"ticker\": \"TIC" + i + "\", \"market\": \"XAMS\", "
                        + "\"quantity\": \"1\", \"currency\": \"EUR\" }")
                .collect(Collectors.joining(",\n"));
        String body = "{ \"name\": \"Wide\", \"positions\": [" + positions + "] }";

        Instant start = Instant.now();
        ResponseEntity<String> response = post(newKey(), body);
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(positionCount()).isEqualTo(12);
        // Non-gating: creation is well within the "instant" expectation locally.
        assertThat(elapsed).as("create with 12 positions took %s", elapsed)
                .isLessThan(Duration.ofSeconds(2));
    }
}
