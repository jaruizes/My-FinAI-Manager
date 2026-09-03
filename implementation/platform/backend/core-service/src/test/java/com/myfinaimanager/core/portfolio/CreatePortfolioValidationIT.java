package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * US3 — a Save that breaks a business rule is rejected with a field-specific RFC 9457
 * {@code ValidationProblem} and nothing is persisted (SC-003).
 *
 * <ul>
 *   <li>AC-003 — blank / whitespace name.</li>
 *   <li>AC-004 — quantity 0, negative, non-numeric.</li>
 *   <li>AC-005 — duplicate {@code ticker + market}.</li>
 *   <li>BR-002 — a portfolio with no position.</li>
 * </ul>
 */
class CreatePortfolioValidationIT extends AbstractPortfolioIT {

    private void assertRejectedAndNothingPersisted(ResponseEntity<String> response, String expectedCode) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(response.getBody())
                .contains("\"type\":\"/problems/portfolio-validation\"")
                .contains("\"errors\":")
                .contains(expectedCode);
        assertThat(portfolioCount()).isZero();
        assertThat(positionCount()).isZero();
    }

    @Test
    void a_blank_name_is_rejected() { // AC-003
        String body = """
                { "name": "   ",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "1", "currency": "EUR" } ] }
                """;
        assertRejectedAndNothingPersisted(post(newKey(), body), "REQUIRED");
    }

    @Test
    void a_zero_quantity_is_rejected() { // AC-004
        String body = """
                { "name": "Zero Qty",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "0", "currency": "EUR" } ] }
                """;
        assertRejectedAndNothingPersisted(post(newKey(), body), "NOT_POSITIVE");
    }

    @Test
    void a_negative_quantity_is_rejected() { // AC-004
        String body = """
                { "name": "Neg Qty",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "-3", "currency": "EUR" } ] }
                """;
        assertRejectedAndNothingPersisted(post(newKey(), body), "NOT_POSITIVE");
    }

    @Test
    void a_non_numeric_quantity_is_rejected() { // AC-004
        String body = """
                { "name": "Bad Qty",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "twelve", "currency": "EUR" } ] }
                """;
        assertRejectedAndNothingPersisted(post(newKey(), body), "INVALID_NUMBER");
    }

    @Test
    void a_duplicate_instrument_is_rejected() { // AC-005
        String body = """
                { "name": "Dup",
                  "positions": [
                    { "ticker": "ASML", "market": "XAMS", "quantity": "1", "currency": "EUR" },
                    { "ticker": "ASML", "market": "XAMS", "quantity": "2", "currency": "EUR" } ] }
                """;
        ResponseEntity<String> response = post(newKey(), body);
        assertRejectedAndNothingPersisted(response, "DUPLICATE_INSTRUMENT");
        assertThat(response.getBody()).contains("positions[1]");
    }

    @Test
    void a_portfolio_with_no_position_is_rejected() { // BR-002
        String body = """
                { "name": "Empty", "positions": [] }
                """;
        assertRejectedAndNothingPersisted(post(newKey(), body), "AT_LEAST_ONE");
    }

    @Test
    void several_violations_are_reported_together() { // FR-024
        String body = """
                { "name": "",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "0", "currency": "EUR" } ] }
                """;
        ResponseEntity<String> response = post(newKey(), body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("REQUIRED").contains("NOT_POSITIVE");
        assertThat(portfolioCount()).isZero();
    }
}
