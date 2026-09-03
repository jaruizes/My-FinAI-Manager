package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * US1 / AC-001 — an Investor creates a portfolio with a name and one valid position; the portfolio
 * and its position are stored exactly as entered, with the optional acquisition fields left NULL
 * (SC-006 — never 0, never inferred).
 */
class CreatePortfolioAcceptanceIT extends AbstractPortfolioIT {

    @Test
    void a_valid_name_and_one_valid_position_are_created_and_stored_verbatim() {
        String body = """
                { "name": "Long-Term Growth",
                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "12", "currency": "EUR" } ] }
                """;

        ResponseEntity<String> response = post(newKey(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .contains("\"status\":\"ACTIVE\"", "\"name\":\"Long-Term Growth\"");

        assertThat(portfolioCount()).isEqualTo(1);
        assertThat(positionCount()).isEqualTo(1);

        String portfolioRow = jdbc.sql("SELECT name || '|' || status FROM portfolio")
                .query(String.class).single();
        assertThat(portfolioRow).isEqualTo("Long-Term Growth|ACTIVE");

        String positionRow = jdbc.sql("""
                        SELECT ticker || '|' || market || '|' || quantity::text || '|' || currency || '|'
                               || coalesce(initial_purchase_date::text, 'null') || '|'
                               || coalesce(average_purchase_price::text, 'null')
                        FROM position
                        """)
                .query(String.class).single();
        assertThat(positionRow).isEqualTo("ASML|XAMS|12|EUR|null|null");
    }
}
