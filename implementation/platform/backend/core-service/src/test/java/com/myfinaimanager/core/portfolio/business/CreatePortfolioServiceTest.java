package com.myfinaimanager.core.portfolio.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.portfolio.business.CreatePortfolioCommand;
import com.myfinaimanager.core.portfolio.business.CreatePortfolioResult;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotSavedException;
import com.myfinaimanager.core.portfolio.domain.ports.InstrumentCatalog;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.domain.model.Violation;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CreatePortfolioServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
    private static final InvestorId DEFAULT_INVESTOR =
            InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Mock private PortfolioRepository repository;
    @Mock private DefaultInvestorProvider defaultInvestorProvider;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    /** Fake catalog: every {@code (ticker, market, currency)} triple added here is selectable. */
    private final FakeCatalog catalog = new FakeCatalog();

    private CreatePortfolioService service;

    @BeforeEach
    void setUp() {
        catalog.selectable.clear();
        catalog.allow("ASML", "XAMS", "EUR");
        catalog.allow("MSFT", "XNAS", "USD");
        service = new CreatePortfolioService(repository, defaultInvestorProvider, catalog, CLOCK, events);
    }

    private static final class FakeCatalog implements InstrumentCatalog {
        final java.util.Set<String> selectable = new java.util.HashSet<>();

        void allow(String ticker, String market, String currency) {
            selectable.add(key(ticker, market, currency));
        }

        private static String key(String t, String m, String c) {
            return t.toUpperCase(java.util.Locale.ROOT) + "|" + m.toUpperCase(java.util.Locale.ROOT)
                    + "|" + c.toUpperCase(java.util.Locale.ROOT);
        }

        @Override
        public boolean isSelectable(Ticker ticker, Market market, Currency currency) {
            return selectable.contains(key(ticker.value(), market.value(), currency.code()));
        }
    }

    private static CreatePortfolioCommand validCommand(String key) {
        return new CreatePortfolioCommand("Long-Term Growth",
                List.of(new NewPosition("ASML", "XAMS", "12", "EUR", null, null)), key);
    }

    @Test
    void creates_a_portfolio_owned_by_the_default_investor() {
        when(repository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        when(repository.save(any(), eq("k1"))).thenAnswer(inv -> inv.getArgument(0));

        CreatePortfolioResult result = service.create(validCommand("k1"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.portfolio().investorId()).isEqualTo(DEFAULT_INVESTOR);
        verify(repository).save(any(Portfolio.class), eq("k1"));
    }

    @Test
    void replays_an_existing_portfolio_for_a_known_idempotency_key_without_re_creating() { // FR-031a
        Portfolio existing = Portfolio.create(DEFAULT_INVESTOR, "Existing",
                List.of(new NewPosition("MSFT", "XNAS", "1", "USD", null, null)), CLOCK);
        when(repository.findByIdempotencyKey("k-dup")).thenReturn(Optional.of(existing));

        CreatePortfolioResult result = service.create(validCommand("k-dup"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.portfolio()).isSameAs(existing);
        verify(repository, never()).save(any(), any());
        verifyNoInteractions(defaultInvestorProvider);
    }

    @Test
    void propagates_validation_failures_without_persisting() {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        CreatePortfolioCommand invalid = new CreatePortfolioCommand("  ", List.of(), "k2");

        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(PortfolioValidationException.class);
        verify(repository, never()).save(any(), any());
    }

    @Test
    void propagates_a_persistence_failure() {
        when(repository.findByIdempotencyKey("k3")).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        when(repository.save(any(), eq("k3")))
                .thenThrow(new PortfolioNotSavedException("db down", new RuntimeException()));

        assertThatThrownBy(() -> service.create(validCommand("k3")))
                .isInstanceOf(PortfolioNotSavedException.class);
    }

    @Test
    void records_PortfolioCreated_and_one_PositionAdded_per_position(CapturedOutput output) { // FR-032
        when(repository.findByIdempotencyKey("k4")).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        when(repository.save(any(), eq("k4"))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreatePortfolioCommand("Two", List.of(
                new NewPosition("ASML", "XAMS", "1", "EUR", null, null),
                new NewPosition("MSFT", "XNAS", "2", "USD", null, null)), "k4"));

        assertThat(output.getOut()).contains("PortfolioCreated");
        assertThat(countOccurrences(output.getOut(), "PositionAdded")).isEqualTo(2);
    }

    @Test
    void treats_a_save_that_returns_a_different_portfolio_as_a_replay(CapturedOutput output) {
        // The idempotency-key race: the pre-check found nothing, but by the time we saved, a
        // concurrent request had already created the portfolio; the repository returns that one.
        Portfolio raced = Portfolio.create(DEFAULT_INVESTOR, "Raced",
                List.of(new NewPosition("MSFT", "XNAS", "1", "USD", null, null)), CLOCK);
        when(repository.findByIdempotencyKey("k-race")).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        when(repository.save(any(), eq("k-race"))).thenReturn(raced);

        CreatePortfolioResult result = service.create(validCommand("k-race"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.portfolio()).isSameAs(raced);
        assertThat(output.getOut()).doesNotContain("PortfolioCreated");
    }

    @Test
    void does_not_record_business_outcomes_on_a_replay(CapturedOutput output) {
        Portfolio existing = Portfolio.create(DEFAULT_INVESTOR, "Existing",
                List.of(new NewPosition("MSFT", "XNAS", "1", "USD", null, null)), CLOCK);
        when(repository.findByIdempotencyKey("k5")).thenReturn(Optional.of(existing));

        service.create(validCommand("k5"));

        assertThat(output.getOut()).doesNotContain("PortfolioCreated");
    }

    // ---- FD002 — catalog validation (FR-011) --------------------------------------------

    @Test
    void rejects_a_position_whose_ticker_market_currency_is_not_a_catalogued_listing() {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        // AAPL/XMAD/EUR is a well-formed but non-catalogued combination.
        CreatePortfolioCommand cmd = new CreatePortfolioCommand("Bad",
                List.of(new NewPosition("AAPL", "XMAD", "1", "EUR", null, null)), "kc1");

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> {
                    var v = ((PortfolioValidationException) e).violations();
                    assertThat(v).extracting(Violation::field, Violation::code)
                            .containsExactly(tuple("positions[0]", "INSTRUMENT_NOT_IN_CATALOG"));
                });
        verify(repository, never()).save(any(), any());
    }

    @Test
    void rejects_a_position_whose_currency_differs_from_the_listing_currency() { // analyze A1
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        // ASML/XAMS is catalogued in EUR (see setUp); the request submits USD.
        CreatePortfolioCommand cmd = new CreatePortfolioCommand("Wrong currency",
                List.of(new NewPosition("ASML", "XAMS", "1", "USD", null, null)), "kc2");

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).violations())
                        .extracting(Violation::code).containsExactly("INSTRUMENT_NOT_IN_CATALOG"));
        verify(repository, never()).save(any(), any());
    }

    @Test
    void reports_a_structural_and_a_catalog_violation_together_in_one_exception() {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        CreatePortfolioCommand cmd = new CreatePortfolioCommand("Mixed", List.of(
                new NewPosition("ASML", "XAMS", "0", "EUR", null, null),        // NOT_POSITIVE
                new NewPosition("AAPL", "XMAD", "1", "EUR", null, null)), "kc3"); // INSTRUMENT_NOT_IN_CATALOG

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> assertThat(((PortfolioValidationException) e).violations())
                        .extracting(Violation::code)
                        .contains("NOT_POSITIVE", "INSTRUMENT_NOT_IN_CATALOG"));
        verify(repository, never()).save(any(), any());
    }

    @Test
    void does_not_catalog_check_a_position_with_a_malformed_ticker_market_or_currency() {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        CreatePortfolioCommand cmd = new CreatePortfolioCommand("Blank",
                List.of(new NewPosition("", "XAMS", "1", "eur", null, null)), "kc4");

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(PortfolioValidationException.class)
                .satisfies(e -> {
                    var codes = ((PortfolioValidationException) e).violations().stream()
                            .map(Violation::code).toList();
                    assertThat(codes).contains("REQUIRED", "CURRENCY_FORMAT");
                    assertThat(codes).doesNotContain("INSTRUMENT_NOT_IN_CATALOG");
                });
    }

    @Test
    void creates_the_portfolio_when_every_position_is_a_catalogued_listing() {
        when(repository.findByIdempotencyKey("kc5")).thenReturn(Optional.empty());
        when(defaultInvestorProvider.get()).thenReturn(DEFAULT_INVESTOR);
        when(repository.save(any(), eq("kc5"))).thenAnswer(inv -> inv.getArgument(0));

        CreatePortfolioResult result = service.create(new CreatePortfolioCommand("Good", List.of(
                new NewPosition("ASML", "XAMS", "1", "EUR", null, null),
                new NewPosition("MSFT", "XNAS", "2", "USD", null, null)), "kc5"));

        assertThat(result.replayed()).isFalse();
        verify(repository).save(any(Portfolio.class), eq("kc5"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int i = haystack.indexOf(needle);
        while (i != -1) {
            count++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return count;
    }
}
