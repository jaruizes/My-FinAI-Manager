package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-031a / SC-011 — an accidental double submit of the same creation draft creates exactly one
 * portfolio; the replay returns the already-created one.
 */
class CreatePortfolioIdempotencyIT extends AbstractPortfolioIT {

    private static final String BODY = """
            { "name": "Idem Test",
              "positions": [ { "ticker": "MSFT", "market": "XNAS", "quantity": "1", "currency": "USD" } ] }
            """;

    @Test
    void same_key_twice_creates_one_portfolio_and_replays_the_second() {
        String key = newKey();

        ResponseEntity<String> first = post(key, BODY);
        ResponseEntity<String> second = post(key, BODY);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");

        String firstId = extractId(first.getBody());
        String secondId = extractId(second.getBody());
        assertThat(secondId).isEqualTo(firstId);

        assertThat(portfolioCount()).isEqualTo(1);
        assertThat(positionCount()).isEqualTo(1);
    }

    @Test
    void different_keys_create_two_portfolios() {
        post(newKey(), BODY);
        post(newKey(), BODY);
        assertThat(portfolioCount()).isEqualTo(2);
    }

    private static String extractId(String json) {
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }
}
