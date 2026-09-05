package com.myfinaimanager.core.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.myfinaimanager.core.portfolio.domain.model.FxConversion;
import com.myfinaimanager.core.portfolio.domain.model.PositionPricing;
import com.myfinaimanager.core.portfolio.domain.ports.MarketDataGateway;

/**
 * FD005 US1 — the post-creation automatic analysis trigger, full slice (real HTTP → Spring →
 * PostgreSQL → real async worker → real {@code OpenAiModelAdapter}, reached via {@code
 * ai.tasks.portfolio-analysis.provider=openai}). No live OpenAI call is ever made — the test
 * environment carries no {@code OPENAI_API_KEY}, so the chain deterministically resolves to
 * {@code FAILED(NOT_CONFIGURED)} (constitution VII — no CI dependency on a live provider), proving
 * the entire asynchronous chain end to end, offline, mirroring EN006's own VC-style proof.
 *
 * <p>Placed alongside {@link PortfolioValuationOnCreationIT} (same package) to reuse
 * {@link AbstractPortfolioIT}'s create-endpoint helper (test-organization choice only — production
 * code lives in {@code portfolioanalysis}).
 */
class PortfolioAnalysisOnCreationIT extends AbstractPortfolioIT {

    private static final Instant TS = Instant.parse("2026-09-05T11:00:00Z");

    @MockitoBean
    private MarketDataGateway marketData;

    private static final String ONE_POSITION = """
            { "name": "Growth",
              "positions": [
                { "ticker": "MSFT", "market": "XNAS", "quantity": "10", "currency": "USD" } ] }
            """;

    private void gatewayHealthy() {
        when(marketData.latestPrice("MSFT", "XNAS", "USD"))
                .thenReturn(Optional.of(new PositionPricing(new BigDecimal("200"), TS)));
        lenient().when(marketData.sector(any(), any(), any())).thenReturn(Optional.of("Technology"));
        lenient().when(marketData.fxRate(any(), any())).thenReturn(Optional.of(new FxConversion(BigDecimal.ONE, TS)));
    }

    private long analysisCount() {
        return jdbc.sql("SELECT count(*) FROM portfolio_analysis").query(Long.class).single();
    }

    private String analysisStatus() {
        return jdbc.sql("SELECT status FROM portfolio_analysis").query(String.class).single();
    }

    private void pollUntilTerminal() {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            String status = analysisStatus();
            if (status.equals("COMPLETED") || status.equals("FAILED")) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void a_created_portfolio_gets_an_analysis_request_without_affecting_the_create_response() { // SC-001, SC-002
        long start = System.currentTimeMillis();
        ResponseEntity<String> response = post(newKey(), ONE_POSITION);
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(elapsedMs).isLessThan(5000); // the request never waits on the AI call itself

        assertThat(analysisCount()).isEqualTo(1);
        String portfolioId = jdbc.sql("SELECT id::text FROM portfolio").query(String.class).single();
        assertThat(jdbc.sql("SELECT portfolio_id::text FROM portfolio_analysis").query(String.class).single())
                .isEqualTo(portfolioId);
        assertThat(jdbc.sql("SELECT created_by_trigger FROM portfolio_analysis").query(String.class).single())
                .isEqualTo("AUTOMATIC");
    }

    @Test
    void with_a_healthy_valuation_the_analysis_still_resolves_to_failed_not_configured_offline() {
        gatewayHealthy();

        post(newKey(), ONE_POSITION);
        pollUntilTerminal();

        assertThat(analysisStatus()).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT failure_reason_code FROM portfolio_analysis").query(String.class).single())
                .isEqualTo("NOT_CONFIGURED");
    }
}
