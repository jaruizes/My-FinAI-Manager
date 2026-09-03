package com.myfinaimanager.core.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the portfolio-domain value objects (FD001 — BR-001, BR-003, BR-005, BR-006, FR-025). */
class ValueObjectsTest {

    // --- PortfolioName (BR-001, A7) -------------------------------------------------------------

    @Test
    void portfolioName_trims_and_keeps_the_value() {
        assertThat(new PortfolioName("  Long-Term Growth  ").value()).isEqualTo("Long-Term Growth");
    }

    @Test
    void portfolioName_rejects_blank() {
        assertThatThrownBy(() -> new PortfolioName("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void portfolioName_rejects_over_120_chars() {
        assertThatThrownBy(() -> new PortfolioName("x".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Ticker / Market (BR-003, FR-015) -----------------------------------------------------

    @Test
    void ticker_is_trimmed_and_uppercased() {
        assertThat(new Ticker(" asml ").value()).isEqualTo("ASML");
    }

    @Test
    void market_recognises_a_mic_shape_but_keeps_others_verbatim() {
        assertThat(new Market("xams").isMicShaped()).isTrue();
        assertThat(new Market("NASDAQGS").isMicShaped()).isFalse();
        assertThat(new Market(" nyse ").value()).isEqualTo("NYSE");
    }

    @Test
    void ticker_and_market_reject_blank() {
        assertThatThrownBy(() -> new Ticker(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Market(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ticker_and_market_reject_values_over_20_chars() {
        assertThatThrownBy(() -> new Ticker("x".repeat(21))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Market("x".repeat(21))).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Currency (BR-006, FR-016) ----------------------------------------------------------

    @Test
    void currency_accepts_a_three_letter_uppercase_code() {
        assertThat(new Currency("EUR").code()).isEqualTo("EUR");
        assertThat(Currency.hasValidShape("USD")).isTrue();
    }

    @Test
    void currency_rejects_wrong_shape() {
        assertThat(Currency.hasValidShape("eur")).isFalse();
        assertThat(Currency.hasValidShape("EURO")).isFalse();
        assertThat(Currency.hasValidShape(null)).isFalse();
        assertThatThrownBy(() -> new Currency("eur")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Quantity (BR-005, FR-025, DR-011) --------------------------------------------------

    @Test
    void quantity_preserves_exact_decimal_scale() {
        assertThat(Quantity.parse("12.500").value()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(Quantity.parse("12.500").toString()).isEqualTo("12.500");
    }

    @Test
    void quantity_must_be_greater_than_zero() {
        assertThatThrownBy(() -> new Quantity(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quantity(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Money (BR-007, A3, FR-025) --------------------------------------------------------

    @Test
    void money_carries_an_amount_and_currency_exactly() {
        Money m = Money.parse("812.50", new Currency("EUR"));
        assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("812.50"));
        assertThat(m.currency().code()).isEqualTo("EUR");
    }

    @Test
    void money_amount_must_be_greater_than_zero() {
        assertThatThrownBy(() -> Money.parse("0", new Currency("EUR")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.parse("-5", new Currency("EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- InstrumentRef (BR-003, BR-004, FR-012, FR-014) -----------------------------------

    @Test
    void instrumentRef_value_equality_uses_ticker_and_market() {
        InstrumentRef a = new InstrumentRef(new Ticker("ASML"), new Market("XAMS"));
        InstrumentRef b = new InstrumentRef(new Ticker("asml"), new Market("xams"));
        InstrumentRef c = new InstrumentRef(new Ticker("ASML"), new Market("XNAS"));
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    // --- Identities ---------------------------------------------------------------------------

    @Test
    void identities_wrap_a_uuid() {
        UUID u = UUID.randomUUID();
        assertThat(PortfolioId.of(u).value()).isEqualTo(u);
        assertThat(PositionId.of(u).value()).isEqualTo(u);
        assertThat(InvestorId.of(u).value()).isEqualTo(u);
        assertThat(PortfolioId.newId()).isNotEqualTo(PortfolioId.newId());
    }
}
