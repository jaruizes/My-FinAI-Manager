package com.myfinaimanager.core.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link Portfolio#create} — every FD001 business rule and the "collect all violations" behaviour.
 * Covers BR-001..BR-010, AC-003..AC-008, and the derived rules A3 (price &gt; 0) / A4 (no future
 * date). All rejection cases assert that nothing would be persisted (an exception is thrown).
 */
class PortfolioTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
    private static final InvestorId INVESTOR = InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static NewPosition validPosition(String ticker, String market) {
        return new NewPosition(ticker, market, "10", "EUR", null, null);
    }

    // --- happy paths -------------------------------------------------------------------------

    @Test
    void creates_a_portfolio_with_one_valid_position() { // AC-001
        Portfolio p = Portfolio.create(INVESTOR, "Long-Term Growth",
                List.of(validPosition("ASML", "XAMS")), CLOCK);

        assertThat(p.name().value()).isEqualTo("Long-Term Growth");
        assertThat(p.status()).isEqualTo(PortfolioStatus.ACTIVE);
        assertThat(p.investorId()).isEqualTo(INVESTOR);
        assertThat(p.positions()).hasSize(1);
        assertThat(p.createdAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
        Position pos = p.positions().get(0);
        assertThat(pos.instrument()).isEqualTo(new InstrumentRef(new Ticker("ASML"), new Market("XAMS")));
        assertThat(pos.quantity().value()).isEqualByComparingTo("10");
        assertThat(pos.currency().code()).isEqualTo("EUR");
        assertThat(pos.initialPurchaseDate()).isEmpty();
        assertThat(pos.averagePurchasePrice()).isEmpty();
    }

    @Test
    void creates_a_portfolio_with_several_positions() { // AC-002
        Portfolio p = Portfolio.create(INVESTOR, "Dividend",
                List.of(validPosition("ASML", "XAMS"), validPosition("MSFT", "XNAS"),
                        validPosition("IBE", "XMAD")), CLOCK);
        assertThat(p.positions()).hasSize(3);
    }

    @Test
    void same_ticker_on_different_markets_is_allowed() { // BR-003
        Portfolio p = Portfolio.create(INVESTOR, "Multi-listed",
                List.of(validPosition("ASML", "XAMS"), validPosition("ASML", "XNAS")), CLOCK);
        assertThat(p.positions()).hasSize(2);
    }

    @Test
    void optional_acquisition_fields_may_be_absent() { // AC-006, AC-007, BR-008, BR-009
        Portfolio p = Portfolio.create(INVESTOR, "Sparse",
                List.of(new NewPosition("ASML", "XAMS", "5", "EUR", null, null)), CLOCK);
        Position pos = p.positions().get(0);
        assertThat(pos.initialPurchaseDate()).isEmpty();
        assertThat(pos.averagePurchasePrice()).isEmpty();
    }

    @Test
    void a_provided_price_is_kept_in_the_position_currency() { // AC-008, BR-007, FR-020
        Portfolio p = Portfolio.create(INVESTOR, "Priced",
                List.of(new NewPosition("ASML", "XAMS", "3", "EUR", "2024-05-14", "812.50")), CLOCK);
        Position pos = p.positions().get(0);
        assertThat(pos.averagePurchasePrice()).hasValueSatisfying(m -> {
            assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("812.50"));
            assertThat(m.currency().code()).isEqualTo("EUR");
        });
        assertThat(pos.initialPurchaseDate()).hasValue(java.time.LocalDate.parse("2024-05-14"));
    }

    @Test
    void a_purchase_date_of_today_is_accepted() { // A4 boundary
        Portfolio p = Portfolio.create(INVESTOR, "Today",
                List.of(new NewPosition("ASML", "XAMS", "1", "EUR", "2026-09-01", null)), CLOCK);
        assertThat(p.positions().get(0).initialPurchaseDate()).hasValue(java.time.LocalDate.parse("2026-09-01"));
    }

    // --- single-rule rejections -------------------------------------------------------------

    @Test
    void rejects_a_blank_name() { // AC-003, BR-001
        assertThatValidation("   ", List.of(validPosition("ASML", "XAMS")))
                .containsExactly(v("name", "REQUIRED"));
    }

    @Test
    void rejects_a_name_over_120_chars() { // A7
        assertThatValidation("x".repeat(121), List.of(validPosition("ASML", "XAMS")))
                .containsExactly(v("name", "NAME_TOO_LONG"));
    }

    @Test
    void rejects_a_portfolio_with_no_positions() { // BR-002
        assertThatValidation("Empty", List.of())
                .containsExactly(v("positions", "AT_LEAST_ONE"));
    }

    @Test
    void rejects_zero_negative_and_non_numeric_quantity() { // AC-004, BR-005
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "0", "EUR", null, null)))
                .containsExactly(v("positions[0].quantity", "NOT_POSITIVE"));
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "-3", "EUR", null, null)))
                .containsExactly(v("positions[0].quantity", "NOT_POSITIVE"));
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "abc", "EUR", null, null)))
                .containsExactly(v("positions[0].quantity", "INVALID_NUMBER"));
    }

    @Test
    void rejects_missing_required_position_fields() { // FD001 §5
        assertThatValidation("P", List.of(new NewPosition(" ", " ", " ", " ", null, null)))
                .contains(v("positions[0].ticker", "REQUIRED"),
                        v("positions[0].market", "REQUIRED"),
                        v("positions[0].currency", "REQUIRED"),
                        v("positions[0].quantity", "REQUIRED"));
    }

    @Test
    void rejects_a_currency_with_the_wrong_shape() { // BR-006, FR-016
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "1", "eur", null, null)))
                .containsExactly(v("positions[0].currency", "CURRENCY_FORMAT"));
    }

    @Test
    void rejects_a_duplicate_ticker_and_market() { // AC-005, BR-004
        assertThatValidation("P",
                List.of(validPosition("ASML", "XAMS"), validPosition("asml", "xams")))
                .containsExactly(v("positions[1]", "DUPLICATE_INSTRUMENT"));
    }

    @Test
    void rejects_a_non_positive_average_purchase_price() { // A3, FR-010
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "1", "EUR", null, "0")))
                .containsExactly(v("positions[0].averagePurchasePrice", "NOT_POSITIVE"));
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "1", "EUR", null, "-9")))
                .containsExactly(v("positions[0].averagePurchasePrice", "NOT_POSITIVE"));
    }

    @Test
    void rejects_a_future_purchase_date() { // A4, FR-011
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "1", "EUR", "2026-09-02", null)))
                .containsExactly(v("positions[0].initialPurchaseDate", "FUTURE_DATE"));
    }

    @Test
    void rejects_a_malformed_purchase_date() {
        assertThatValidation("P", List.of(new NewPosition("ASML", "XAMS", "1", "EUR", "14-05-2024", null)))
                .containsExactly(v("positions[0].initialPurchaseDate", "INVALID_DATE"));
    }

    // --- collect ALL violations in one pass (FR-024) ---------------------------------------

    @Test
    void collects_every_violation_in_a_single_pass() {
        var violations = assertThatValidation("   ", List.of(
                new NewPosition("ASML", "XAMS", "0", "EUR", null, null),   // NOT_POSITIVE
                validPosition("ASML", "XAMS")));                            // DUPLICATE (of the first, which parsed ok? no — first failed)
        // first position fails quantity, so it never enters the "seen" set;
        // the second is therefore NOT a duplicate — expect name + quantity only.
        violations.containsExactlyInAnyOrder(
                v("name", "REQUIRED"),
                v("positions[0].quantity", "NOT_POSITIVE"));
    }

    @Test
    void reports_name_and_duplicate_together() {
        assertThatValidation("   ", List.of(validPosition("ASML", "XAMS"), validPosition("ASML", "XAMS")))
                .containsExactlyInAnyOrder(
                        v("name", "REQUIRED"),
                        v("positions[1]", "DUPLICATE_INSTRUMENT"));
    }

    @Test
    void every_violation_code_is_a_canonical_token() {
        var ex = catchThrowableOfType(
                () -> Portfolio.create(INVESTOR, "x".repeat(200),
                        List.of(new NewPosition("", "", "", "", "9999-01-01", "-1")), CLOCK),
                PortfolioValidationException.class);
        assertThat(ex.violations()).allSatisfy(vi ->
                assertThat(java.util.Arrays.stream(ValidationCode.values()).map(Enum::name))
                        .contains(vi.code()));
    }

    // --- helpers -------------------------------------------------------------------------------

    private static org.assertj.core.api.ListAssert<String> assertThatValidation(
            String name, List<NewPosition> positions) {
        PortfolioValidationException ex = catchThrowableOfType(
                () -> Portfolio.create(INVESTOR, name, positions, CLOCK),
                PortfolioValidationException.class);
        assertThat(ex).as("expected a PortfolioValidationException").isNotNull();
        return assertThat(ex.violations().stream().map(vi -> vi.field() + "|" + vi.code()).toList());
    }

    private static String v(String field, String code) {
        return field + "|" + code;
    }

    @Test
    void reconstitute_rebuilds_without_re_validating_user_input() {
        Portfolio p = Portfolio.reconstitute(PortfolioId.newId(), INVESTOR, new PortfolioName("Loaded"),
                PortfolioStatus.ACTIVE,
                List.of(Position.reconstitute(PositionId.newId(),
                        new InstrumentRef(new Ticker("ASML"), new Market("XAMS")),
                        Quantity.parse("2"), new Currency("EUR"),
                        java.util.Optional.empty(), java.util.Optional.empty())),
                Instant.now());
        assertThat(p.positions()).hasSize(1);
    }

    @Test
    void position_rejects_a_price_in_a_different_currency() { // guard for BR-007
        assertThatThrownBy(() -> Position.reconstitute(PositionId.newId(),
                new InstrumentRef(new Ticker("ASML"), new Market("XAMS")),
                Quantity.parse("1"), new Currency("EUR"),
                java.util.Optional.empty(),
                java.util.Optional.of(Money.parse("10", new Currency("USD")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_name() { // validateName null branch (BR-001)
        assertThatValidation(null, List.of(validPosition("ASML", "XAMS")))
                .contains(v("name", "REQUIRED"));
    }

    @Test
    void rejects_a_null_position_list() { // BR-002 — no positions supplied at all
        assertThatValidation("Nulls", null).contains(v("positions", "AT_LEAST_ONE"));
    }

    @Test
    void a_bad_currency_and_a_price_together_do_not_crash_and_report_the_currency() {
        // Exercises parsePosition's "price given but currency unusable" path: the price is dropped,
        // only the CURRENCY_FORMAT violation is reported.
        NewPosition p = new NewPosition("ASML", "XAMS", "1", "EU", null, "10.00");
        assertThatValidation("Bad ccy", List.of(p))
                .contains(v("positions[0].currency", "CURRENCY_FORMAT"))
                .doesNotContain(v("positions[0].averagePurchasePrice", "NOT_POSITIVE"));
    }

    @Test
    void a_non_numeric_average_purchase_price_is_reported() { // parsePosition INVALID_NUMBER (price)
        NewPosition p = new NewPosition("ASML", "XAMS", "1", "EUR", null, "free");
        assertThatValidation("Bad price", List.of(p))
                .contains(v("positions[0].averagePurchasePrice", "INVALID_NUMBER"));
    }

    @Test
    void a_validation_exception_needs_at_least_one_violation() {
        assertThatThrownBy(() -> new PortfolioValidationException(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
