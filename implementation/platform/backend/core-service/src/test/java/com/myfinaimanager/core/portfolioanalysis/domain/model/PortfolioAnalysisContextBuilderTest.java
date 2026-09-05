package com.myfinaimanager.core.portfolioanalysis.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Full-COMPLETED, PARTIAL (explicit partial wording), FAILED/PENDING/ABSENT/no-valued-position →
 * {@code sufficient=false}, deterministic text rendering (research D6).
 */
class PortfolioAnalysisContextBuilderTest {

    private static final Instant VALUED_AT = Instant.parse("2026-09-05T10:00:00Z");

    @Test
    void a_completed_valuation_with_valued_positions_is_sufficient_and_renders_the_full_text() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "Long Term Investment",
                PortfolioValuationStatus.COMPLETED,
                Optional.of(new BigDecimal("2100.00")),
                Optional.of(new BigDecimal("2625.00")),
                List.of(
                        new PortfolioContextSnapshot.PositionSnapshot(
                                "AAPL", Optional.of(new BigDecimal("0.761904761905")),
                                Optional.of(new BigDecimal("1600.00")), Optional.of("Technology"), "USD"),
                        new PortfolioContextSnapshot.PositionSnapshot(
                                "SAN", Optional.of(new BigDecimal("0.238095238095")),
                                Optional.of(new BigDecimal("500.00")), Optional.of("Financial Services"), "EUR")),
                List.of(
                        new PortfolioContextSnapshot.SectorSnapshot("Technology", new BigDecimal("0.761904761905")),
                        new PortfolioContextSnapshot.SectorSnapshot("Financial Services", new BigDecimal("0.238095238095"))),
                Optional.of(VALUED_AT));

        PortfolioAnalysisContext context = PortfolioAnalysisContextBuilder.build(snapshot);

        assertThat(context.sufficient()).isTrue();
        assertThat(context.text()).contains("Portfolio \"Long Term Investment\"");
        assertThat(context.text()).contains("valuation COMPLETED");
        assertThat(context.text()).contains("Total value: €2100.00 / $2625.00.");
        assertThat(context.text()).contains("- AAPL: 76.19% (Technology, €1600.00)");
        assertThat(context.text()).contains("- SAN: 23.81% (Financial Services, €500.00)");
        assertThat(context.text()).contains("Sector allocation:");
        assertThat(context.text()).contains("- Technology: 76.19%");
        assertThat(context.text()).doesNotContain("PARTIAL");
    }

    @Test
    void positions_are_rendered_in_descending_weight_order() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.COMPLETED, Optional.of(BigDecimal.TEN), Optional.empty(),
                List.of(
                        new PortfolioContextSnapshot.PositionSnapshot(
                                "SMALL", Optional.of(new BigDecimal("0.2")), Optional.of(new BigDecimal("2")),
                                Optional.empty(), "EUR"),
                        new PortfolioContextSnapshot.PositionSnapshot(
                                "BIG", Optional.of(new BigDecimal("0.8")), Optional.of(new BigDecimal("8")),
                                Optional.empty(), "EUR")),
                List.of(), Optional.of(VALUED_AT));

        String text = PortfolioAnalysisContextBuilder.build(snapshot).text();

        assertThat(text.indexOf("BIG")).isLessThan(text.indexOf("SMALL"));
    }

    @Test
    void a_partial_valuation_states_the_partial_wording_and_the_unvalued_count() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.PARTIAL, Optional.of(BigDecimal.TEN), Optional.empty(),
                List.of(
                        new PortfolioContextSnapshot.PositionSnapshot(
                                "AAPL", Optional.of(BigDecimal.ONE), Optional.of(BigDecimal.TEN),
                                Optional.of("Technology"), "USD"),
                        new PortfolioContextSnapshot.PositionSnapshot(
                                "XYZ", Optional.empty(), Optional.empty(), Optional.empty(), "GBP")),
                List.of(), Optional.of(VALUED_AT));

        PortfolioAnalysisContext context = PortfolioAnalysisContextBuilder.build(snapshot);

        assertThat(context.sufficient()).isTrue(); // at least one valued position
        assertThat(context.text()).contains("Note: valuation is PARTIAL — 1 position(s) could not be valued.");
    }

    @Test
    void a_failed_valuation_is_never_sufficient_regardless_of_stale_position_data() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.FAILED, Optional.empty(), Optional.empty(), List.of(), List.of(),
                Optional.empty());

        assertThat(PortfolioAnalysisContextBuilder.build(snapshot).sufficient()).isFalse();
    }

    @Test
    void a_pending_valuation_is_not_sufficient() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.PENDING, Optional.empty(), Optional.empty(), List.of(), List.of(),
                Optional.empty());

        assertThat(PortfolioAnalysisContextBuilder.build(snapshot).sufficient()).isFalse();
    }

    @Test
    void an_absent_valuation_is_not_sufficient() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.ABSENT, Optional.empty(), Optional.empty(), List.of(), List.of(),
                Optional.empty());

        assertThat(PortfolioAnalysisContextBuilder.build(snapshot).sufficient()).isFalse();
    }

    @Test
    void a_completed_valuation_with_no_valued_position_is_not_sufficient() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.COMPLETED, Optional.empty(), Optional.empty(),
                List.of(new PortfolioContextSnapshot.PositionSnapshot(
                        "AAPL", Optional.empty(), Optional.empty(), Optional.empty(), "USD")),
                List.of(), Optional.empty());

        assertThat(PortfolioAnalysisContextBuilder.build(snapshot).sufficient()).isFalse();
    }

    @Test
    void rendering_is_deterministic_for_the_same_input() {
        PortfolioContextSnapshot snapshot = new PortfolioContextSnapshot(
                "P", PortfolioValuationStatus.COMPLETED, Optional.of(BigDecimal.TEN), Optional.empty(),
                List.of(new PortfolioContextSnapshot.PositionSnapshot(
                        "AAPL", Optional.of(BigDecimal.ONE), Optional.of(BigDecimal.TEN),
                        Optional.of("Technology"), "USD")),
                List.of(), Optional.of(VALUED_AT));

        String first = PortfolioAnalysisContextBuilder.build(snapshot).text();
        String second = PortfolioAnalysisContextBuilder.build(snapshot).text();

        assertThat(first).isEqualTo(second);
    }
}
