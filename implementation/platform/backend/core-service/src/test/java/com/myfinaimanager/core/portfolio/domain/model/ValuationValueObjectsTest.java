package com.myfinaimanager.core.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Guard-clause coverage for the FD004 valuation value objects (SC-006 — BigDecimal only). */
class ValuationValueObjectsTest {

    private static final Instant TS = Instant.parse("2026-09-04T12:00:00Z");
    private static final PortfolioId ID = PortfolioId.newId();

    @Test
    void position_pricing_rejects_null_and_non_positive_price() {
        assertThatThrownBy(() -> new PositionPricing(null, TS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PositionPricing(BigDecimal.ZERO, TS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PositionPricing(new BigDecimal("1"), null)).isInstanceOf(NullPointerException.class);
        assertThat(new PositionPricing(new BigDecimal("1.5"), TS).price()).isEqualByComparingTo("1.5");
    }

    @Test
    void fx_conversion_rejects_null_and_non_positive_rate() {
        assertThatThrownBy(() -> new FxConversion(null, TS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxConversion(new BigDecimal("-1"), TS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxConversion(new BigDecimal("1"), null)).isInstanceOf(NullPointerException.class);
        assertThat(new FxConversion(new BigDecimal("0.8"), TS).rate()).isEqualByComparingTo("0.8");
    }

    @Test
    void fx_context_none_is_all_empty_and_normalises_nulls() {
        FxContext none = FxContext.none();
        assertThat(none.usdToEur()).isEmpty();
        assertThat(none.eurToUsd()).isEmpty();
        FxContext nulls = new FxContext(null, null, null, null);
        assertThat(nulls.usdToEurObservedAt()).isEmpty();
        assertThat(nulls.eurToUsdObservedAt()).isEmpty();
    }

    @Test
    void position_input_validates_and_normalises() {
        assertThatThrownBy(() -> new PositionInput("A", "M", BigDecimal.ZERO, "EUR",
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PositionInput(null, "M", BigDecimal.ONE, "EUR",
                Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        PositionInput normalised = new PositionInput("A", "M", BigDecimal.ONE, "EUR", null, null, null);
        assertThat(normalised.price()).isEmpty();
        assertThat(normalised.priceObservedAt()).isEmpty();
        assertThat(normalised.sector()).isEmpty();
    }

    @Test
    void position_valuation_rejects_money_on_an_unvalued_position() {
        assertThatThrownBy(() -> new PositionValuation("A", "M", BigDecimal.ONE, "EUR", false,
                Optional.of(BigDecimal.ONE), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "Unclassified", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PositionValuation(null, "M", BigDecimal.ONE, "EUR", false,
                null, null, null, null, null, "Unclassified", null))
                .isInstanceOf(NullPointerException.class);
        PositionValuation unvalued = PositionValuation.unvalued("A", "M", BigDecimal.ONE, "EUR", "Unclassified");
        assertThat(unvalued.marketPrice()).isEmpty();
        assertThat(unvalued.valued()).isFalse();
    }

    @Test
    void sector_allocation_validates_its_fields() {
        assertThatThrownBy(() -> new SectorAllocation("  ", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectorAllocation("Tech", BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectorAllocation("Tech", BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectorAllocation(null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
        assertThat(new SectorAllocation("Tech", BigDecimal.TEN, BigDecimal.ONE).sector()).isEqualTo("Tech");
    }

    @Test
    void portfolio_valuation_normalises_null_optionals_and_lists() {
        PortfolioValuation v = new PortfolioValuation(ID, ValuationStatus.FAILED, TS,
                null, null, null, null, null, null);
        assertThat(v.totalValueEUR()).isEmpty();
        assertThat(v.marketDataAsOf()).isEmpty();
        assertThat(v.positions()).isEmpty();
        assertThat(v.sectors()).isEmpty();
        assertThatThrownBy(() -> new PortfolioValuation(null, ValuationStatus.FAILED, TS,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), List.of())).isInstanceOf(NullPointerException.class);
    }
}
