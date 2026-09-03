package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * US4 — optional acquisition information.
 *
 * <ul>
 *   <li>AC-006 — a position with no initial purchase date still saves.</li>
 *   <li>AC-007 — a position with no average purchase price saves; the column is NULL, never 0 or
 *       inferred (SC-004).</li>
 *   <li>AC-008 — a provided price is interpreted in the position's own currency (SC-005).</li>
 *   <li>A3 — a non-positive average purchase price is rejected ({@code NOT_POSITIVE}).</li>
 *   <li>A4 — a future initial purchase date is rejected ({@code FUTURE_DATE}).</li>
 * </ul>
 */
class CreatePortfolioOptionalDataIT extends AbstractPortfolioIT {

    @Test
    void a_position_without_optional_acquisition_data_saves_with_null_columns() { // AC-006 / AC-007
        String body = """
                { "name": "No Extras",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "4", "currency": "EUR" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var nulls = jdbc.sql("""
                        SELECT initial_purchase_date IS NULL AND average_purchase_price IS NULL
                               AND average_purchase_price_currency IS NULL
                        FROM position
                        """).query(Boolean.class).single();
        assertThat(nulls).isTrue();
    }

    @Test
    void a_provided_price_is_stored_as_is_in_the_position_currency() { // AC-008 / SC-005
        String body = """
                { "name": "With Price",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "3", "currency": "EUR",
                                   "initialPurchaseDate": "2024-05-14", "averagePurchasePrice": "812.50" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String row = jdbc.sql("""
                        SELECT average_purchase_price::text || '|' || average_purchase_price_currency
                               || '|' || initial_purchase_date::text
                        FROM position
                        """).query(String.class).single();
        assertThat(row).isEqualTo("812.50|EUR|2024-05-14");
    }

    @Test
    void a_non_positive_average_purchase_price_is_rejected() { // A3
        String body = """
                { "name": "Bad Price",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "3", "currency": "EUR",
                                   "averagePurchasePrice": "-5" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("NOT_POSITIVE");
        assertThat(portfolioCount()).isZero();
    }

    @Test
    void a_future_initial_purchase_date_is_rejected() { // A4
        String future = LocalDate.now().plusYears(1).toString();
        String body = """
                { "name": "Future Date",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "3", "currency": "EUR",
                                   "initialPurchaseDate": "%s" } ] }
                """.formatted(future);

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("FUTURE_DATE");
        assertThat(portfolioCount()).isZero();
    }
}
