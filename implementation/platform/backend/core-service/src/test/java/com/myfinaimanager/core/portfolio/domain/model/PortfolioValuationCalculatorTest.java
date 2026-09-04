package com.myfinaimanager.core.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The deterministic FD004 valuation maths
 * ({@code specs/FD004-portfolio-valuation-and-allocation/contracts/valuation-calculation.md}
 * cases C1–C8). Pure function — no Spring, no I/O. Covers FR-005…FR-013, FR-016…FR-018, and
 * SC-001 / SC-003 / SC-004 / SC-006 at the unit level.
 */
class PortfolioValuationCalculatorTest {

    private static final PortfolioId PORTFOLIO = PortfolioId.newId();
    private static final Instant AT = Instant.parse("2026-09-04T12:00:00Z");
    private static final Instant PRICE_TS = Instant.parse("2026-09-04T11:59:00Z");
    private static final Instant FX_TS = Instant.parse("2026-09-04T11:58:00Z");

    private final PortfolioValuationCalculator calculator = new PortfolioValuationCalculator();

    private static PositionInput priced(String ticker, String market, String qty, String currency,
                                        String price, String sector) {
        return new PositionInput(ticker, market, new BigDecimal(qty), currency,
                Optional.of(new BigDecimal(price)), Optional.of(PRICE_TS),
                sector == null ? Optional.empty() : Optional.of(sector));
    }

    private static PositionInput unpriced(String ticker, String market, String qty, String currency) {
        return new PositionInput(ticker, market, new BigDecimal(qty), currency,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static FxContext fx(String usdToEur, String eurToUsd) {
        return new FxContext(
                usdToEur == null ? Optional.empty() : Optional.of(new BigDecimal(usdToEur)),
                usdToEur == null ? Optional.empty() : Optional.of(FX_TS),
                eurToUsd == null ? Optional.empty() : Optional.of(new BigDecimal(eurToUsd)),
                eurToUsd == null ? Optional.empty() : Optional.of(FX_TS));
    }

    private PositionValuation position(PortfolioValuation v, String ticker) {
        return v.positions().stream().filter(p -> p.ticker().equals(ticker)).findFirst().orElseThrow();
    }

    @Nested
    class C1_HappyPath {

        private PortfolioValuation valuation() {
            return calculator.calculate(PORTFOLIO, List.of(
                    priced("AAPL", "XNAS", "10", "USD", "200", "Technology"),
                    priced("SAN", "XMAD", "100", "EUR", "5", "Financial Services")),
                    fx("0.80", "1.25"), AT);
        }

        @Test
        void native_value_is_quantity_times_price() { // AC-002
            assertThat(position(valuation(), "AAPL").nativeMarketValue()).hasValue(new BigDecimal("2000"));
            assertThat(position(valuation(), "SAN").nativeMarketValue()).hasValue(new BigDecimal("500"));
        }

        @Test
        void usd_position_converts_to_eur_and_keeps_usd() { // AC-003
            PositionValuation aapl = position(valuation(), "AAPL");
            assertThat(aapl.valueInUSD()).hasValue(new BigDecimal("2000"));
            assertThat(aapl.valueInEUR().orElseThrow()).isEqualByComparingTo("1600.00");
        }

        @Test
        void eur_position_converts_to_usd_and_keeps_eur() { // AC-004
            PositionValuation san = position(valuation(), "SAN");
            assertThat(san.valueInEUR()).hasValue(new BigDecimal("500"));
            assertThat(san.valueInUSD().orElseThrow()).isEqualByComparingTo("625.00");
        }

        @Test
        void totals_are_exact_sums() { // AC-005, AC-006, SC-001
            assertThat(valuation().totalValueEUR().orElseThrow()).isEqualByComparingTo("2100.00");
            assertThat(valuation().totalValueUSD().orElseThrow()).isEqualByComparingTo("2625.00");
        }

        @Test
        void weights_are_eur_fractions() { // AC-007, SC-003
            assertThat(position(valuation(), "AAPL").portfolioWeight().orElseThrow())
                    .isEqualByComparingTo("0.761904761905");
            assertThat(position(valuation(), "SAN").portfolioWeight().orElseThrow())
                    .isEqualByComparingTo("0.238095238095");
        }

        @Test
        void sector_allocation_groups_by_sector_in_eur() { // AC-008, SC-003
            List<SectorAllocation> sectors = valuation().sectors();
            assertThat(sectors).extracting(SectorAllocation::sector)
                    .containsExactly("Technology", "Financial Services");
            assertThat(sectors.get(0).sectorValueEUR()).isEqualByComparingTo("1600");
            assertThat(sectors.get(0).sectorWeight()).isEqualByComparingTo("0.761904761905");
            assertThat(sectors.get(1).sectorWeight()).isEqualByComparingTo("0.238095238095");
        }

        @Test
        void weights_sum_to_one() { // FR-013
            BigDecimal sum = valuation().positions().stream()
                    .map(p -> p.portfolioWeight().orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo("1.000000000000");
        }

        @Test
        void status_is_completed() {
            assertThat(valuation().status()).isEqualTo(ValuationStatus.COMPLETED);
        }

        @Test
        void freshness_carries_the_oldest_observed_instants() { // §13 BR-013
            assertThat(valuation().marketDataAsOf()).hasValue(PRICE_TS);
            assertThat(valuation().fxDataAsOf()).hasValue(FX_TS);
        }

        @Test
        void running_it_three_times_gives_an_identical_result() { // C8, SC-002 (calc level)
            assertThat(valuation()).isEqualTo(valuation()).isEqualTo(valuation());
        }
    }

    @Test
    void c2_missing_price_makes_the_position_unvalued_and_the_valuation_partial() { // C2, SC-004
        PortfolioValuation v = calculator.calculate(PORTFOLIO, List.of(
                priced("AAPL", "XNAS", "10", "USD", "200", "Technology"),
                unpriced("SAN", "XMAD", "100", "EUR")),
                fx("0.80", "1.25"), AT);

        PositionValuation san = position(v, "SAN");
        assertThat(san.valued()).isFalse();
        assertThat(san.marketPrice()).isEmpty();
        assertThat(san.nativeMarketValue()).isEmpty();
        assertThat(san.valueInEUR()).isEmpty();
        assertThat(san.valueInUSD()).isEmpty();
        assertThat(san.portfolioWeight()).isEmpty();
        assertThat(v.status()).isEqualTo(ValuationStatus.PARTIAL);
        assertThat(v.totalValueEUR().orElseThrow()).isEqualByComparingTo("1600.00");
        assertThat(position(v, "AAPL").portfolioWeight().orElseThrow()).isEqualByComparingTo("1.000000000000");
        assertThat(v.sectors()).extracting(SectorAllocation::sector).containsExactly("Technology");
    }

    @Test
    void c3_missing_usd_to_eur_fx_drops_the_eur_total_but_keeps_the_usd_total_partial() { // C3, FR-018
        PortfolioValuation v = calculator.calculate(PORTFOLIO, List.of(
                priced("AAPL", "XNAS", "10", "USD", "200", "Technology"),
                priced("SAN", "XMAD", "100", "EUR", "5", "Financial Services")),
                fx(null, "1.25"), AT);

        assertThat(position(v, "AAPL").valueInEUR()).isEmpty();
        assertThat(v.totalValueEUR()).isEmpty();
        assertThat(v.totalValueUSD().orElseThrow()).isEqualByComparingTo("2625.00");
        assertThat(v.positions()).allSatisfy(p -> assertThat(p.portfolioWeight()).isEmpty());
        assertThat(v.sectors()).isEmpty();
        assertThat(v.status()).isEqualTo(ValuationStatus.PARTIAL);
    }

    @Test
    void c4_no_position_priced_is_failed() { // C4
        PortfolioValuation v = calculator.calculate(PORTFOLIO, List.of(
                unpriced("AAPL", "XNAS", "10", "USD"),
                unpriced("SAN", "XMAD", "100", "EUR")),
                fx("0.80", "1.25"), AT);

        assertThat(v.status()).isEqualTo(ValuationStatus.FAILED);
        assertThat(v.totalValueEUR()).isEmpty();
        assertThat(v.totalValueUSD()).isEmpty();
        assertThat(v.positions()).allSatisfy(p -> {
            assertThat(p.valued()).isFalse();
            assertThat(p.nativeMarketValue()).isEmpty();
        });
        assertThat(v.sectors()).isEmpty();
    }

    @Test
    void c5_single_currency_portfolio_missing_the_other_direction_fx_is_partial() { // C5
        PortfolioValuation v = calculator.calculate(PORTFOLIO, List.of(
                priced("SAN", "XMAD", "100", "EUR", "5", "Financial Services")),
                fx(null, null), AT);

        assertThat(position(v, "SAN").valueInEUR()).hasValue(new BigDecimal("500"));
        assertThat(position(v, "SAN").valueInUSD()).isEmpty();
        assertThat(v.totalValueEUR().orElseThrow()).isEqualByComparingTo("500");
        assertThat(v.totalValueUSD()).isEmpty();
        assertThat(position(v, "SAN").portfolioWeight().orElseThrow()).isEqualByComparingTo("1.000000000000");
        assertThat(v.status()).isEqualTo(ValuationStatus.PARTIAL);
    }

    @Test
    void c6_missing_sector_only_is_unclassified_and_still_completed() { // C6, FR-011, FR-016
        PortfolioValuation v = calculator.calculate(PORTFOLIO, List.of(
                priced("AAPL", "XNAS", "10", "USD", "200", null),
                priced("SAN", "XMAD", "100", "EUR", "5", "Financial Services")),
                fx("0.80", "1.25"), AT);

        assertThat(position(v, "AAPL").sector()).isEqualTo("Unclassified");
        assertThat(v.status()).isEqualTo(ValuationStatus.COMPLETED);
        assertThat(v.sectors()).extracting(SectorAllocation::sector)
                .containsExactly("Unclassified", "Financial Services");
    }

    @Test
    void c7_zero_eur_basis_produces_no_weights_and_no_nan() { // C7, FR-019
        // one valued USD position, no USD->EUR fx  -> no EUR total  -> no weights / no sectors
        PortfolioValuation v = calculator.calculate(PORTFOLIO, List.of(
                priced("AAPL", "XNAS", "10", "USD", "200", "Technology")),
                fx(null, null), AT);

        assertThat(v.totalValueEUR()).isEmpty();
        assertThat(v.totalValueUSD().orElseThrow()).isEqualByComparingTo("2000");
        assertThat(position(v, "AAPL").portfolioWeight()).isEmpty();
        assertThat(v.sectors()).isEmpty();
        assertThat(v.status()).isEqualTo(ValuationStatus.PARTIAL);
    }
}
