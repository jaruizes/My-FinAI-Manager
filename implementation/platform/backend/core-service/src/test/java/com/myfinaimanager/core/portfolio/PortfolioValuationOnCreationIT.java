package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import com.myfinaimanager.core.portfolio.domain.ports.MarketDataGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * FD004 US1 — the post-creation valuation trigger, full slice (real HTTP → Spring → PostgreSQL).
 * The EN005 boundary is a mocked {@link MarketDataGateway} (a true external provider is stubbed —
 * constitution VII). Proves: a created Portfolio gets exactly one snapshot; a market-data outage
 * still leaves the Portfolio persisted and returns {@code 201} with a {@code FAILED} snapshot
 * (FR-002; SC-005, SC-008); a replay does not re-value.
 */
class PortfolioValuationOnCreationIT extends AbstractPortfolioIT {

    private static final Instant TS = Instant.parse("2026-09-04T11:00:00Z");

    @MockitoBean
    private MarketDataGateway marketData;

    private static final String TWO_POSITIONS = """
            { "name": "Growth",
              "positions": [
                { "ticker": "MSFT", "market": "XNAS", "quantity": "10", "currency": "USD" },
                { "ticker": "SAP",  "market": "XETR", "quantity": "100", "currency": "EUR" } ] }
            """;

    private void gatewayHealthy() {
        when(marketData.latestPrice("MSFT", "XNAS", "USD"))
                .thenReturn(Optional.of(new PositionPricing(new BigDecimal("200"), TS)));
        when(marketData.latestPrice("SAP", "XETR", "EUR"))
                .thenReturn(Optional.of(new PositionPricing(new BigDecimal("5"), TS)));
        lenient().when(marketData.sector(any(), any(), any())).thenReturn(Optional.of("Technology"));
        when(marketData.fxRate("USD", "EUR"))
                .thenReturn(Optional.of(new FxConversion(new BigDecimal("0.80"), TS)));
        when(marketData.fxRate("EUR", "USD"))
                .thenReturn(Optional.of(new FxConversion(new BigDecimal("1.25"), TS)));
    }

    private void gatewayOutage() {
        when(marketData.latestPrice(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(marketData.sector(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(marketData.fxRate(any(), any())).thenReturn(Optional.empty());
    }

    private long valuationCount() {
        return jdbc.sql("SELECT count(*) FROM portfolio_valuation").query(Long.class).single();
    }

    private String valuationStatus() {
        return jdbc.sql("SELECT status FROM portfolio_valuation").query(String.class).single();
    }

    @Test
    void a_created_portfolio_gets_exactly_one_valuation_snapshot() {
        gatewayHealthy();

        ResponseEntity<String> response = post(newKey(), TWO_POSITIONS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(valuationCount()).isEqualTo(1);
        assertThat(valuationStatus()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT total_value_eur::text FROM portfolio_valuation")
                .query(String.class).single()).isEqualTo("2100.00");
    }

    @Test
    void a_market_data_outage_never_rolls_back_or_hides_the_created_portfolio() { // FR-002, SC-005, SC-008
        gatewayOutage();

        ResponseEntity<String> response = post(newKey(), TWO_POSITIONS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(portfolioCount()).isEqualTo(1);
        assertThat(positionCount()).isEqualTo(2);
        assertThat(valuationCount()).isEqualTo(1);
        assertThat(valuationStatus()).isEqualTo("FAILED");
        // the FD001 rows are exactly what was posted — the valuation wrote nothing to them
        assertThat(jdbc.sql("SELECT count(*) FROM position WHERE portfolio_id = "
                        + "(SELECT id FROM portfolio)").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void a_replayed_creation_does_not_value_again() {
        gatewayHealthy();
        String key = newKey();

        post(key, TWO_POSITIONS);
        long afterFirst = jdbc.sql("SELECT count(*) FROM portfolio_valuation")
                .query(Long.class).single();
        String calculatedAtAfterFirst = jdbc.sql("SELECT calculated_at::text FROM portfolio_valuation")
                .query(String.class).single();

        post(key, TWO_POSITIONS); // same idempotency key → replay

        assertThat(portfolioCount()).isEqualTo(1);
        assertThat(valuationCount()).isEqualTo(afterFirst);
        assertThat(jdbc.sql("SELECT calculated_at::text FROM portfolio_valuation")
                .query(String.class).single()).isEqualTo(calculatedAtAfterFirst);
    }
}
