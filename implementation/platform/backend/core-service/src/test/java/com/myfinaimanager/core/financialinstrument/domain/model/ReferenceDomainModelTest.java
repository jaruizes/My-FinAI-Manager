package com.myfinaimanager.core.financialinstrument.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests (TDD) for the {@code financialinstrument} domain model — value objects and the
 * {@code fromRaw} validation on the reference entities (spec FR-034 "validation" / "identifier
 * normalization"). Framework-free.
 */
class ReferenceDomainModelTest {

    private static final Provenance PROV = Provenance.of("YAHOO_CSV", "SAN.MC", Instant.parse("2026-09-03T10:00:00Z"));

    @Nested
    class ValueObjects {

        @Test
        void mic_trims_upper_cases_and_validates_the_iso10383_shape() {
            assertThat(new Mic("  xmad ").value()).isEqualTo("XMAD");
            assertThatThrownBy(() -> new Mic("XMA")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Mic("XMADX")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Mic("XM D")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void ticker_trims_upper_cases_rejects_blank_and_over_20() {
            assertThat(new Ticker(" san ").value()).isEqualTo("SAN");
            assertThatThrownBy(() -> new Ticker("   ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Ticker("A".repeat(21))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void isin_validates_the_iso6166_shape() {
            assertThat(new Isin(" es0113900j37 ").value()).isEqualTo("ES0113900J37");
            assertThatThrownBy(() -> new Isin("ES0113900J3")).isInstanceOf(IllegalArgumentException.class);   // too short
            assertThatThrownBy(() -> new Isin("1S0113900J37")).isInstanceOf(IllegalArgumentException.class);  // bad country prefix
        }

        @Test
        void supported_currency_parses_only_eur_and_usd() {
            assertThat(SupportedCurrency.parse(" eur ")).isEqualTo(SupportedCurrency.EUR);
            assertThat(SupportedCurrency.parse("USD")).isEqualTo(SupportedCurrency.USD);
            assertThatThrownBy(() -> SupportedCurrency.parse("GBP")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void instrument_type_from_source_is_best_effort_never_throws() {
            assertThat(InstrumentType.fromSource("Common Stock")).isEqualTo(InstrumentType.EQUITY);
            assertThat(InstrumentType.fromSource("Exchange Traded Fund")).isEqualTo(InstrumentType.ETF);
            assertThat(InstrumentType.fromSource("mystery")).isEqualTo(InstrumentType.OTHER);
            assertThat(InstrumentType.fromSource(null)).isEqualTo(InstrumentType.OTHER);
            assertThat(InstrumentType.fromSource("   ")).isEqualTo(InstrumentType.OTHER);
            assertThat(InstrumentType.fromSource("EQUITY")).isEqualTo(InstrumentType.EQUITY);
            assertThat(InstrumentType.fromSource("Ordinary Shares")).isEqualTo(InstrumentType.EQUITY);
            assertThat(InstrumentType.fromSource("Preferred Stock")).isEqualTo(InstrumentType.EQUITY);
            assertThat(InstrumentType.fromSource("Exchange-Traded Note")).isEqualTo(InstrumentType.ETF);
            assertThat(InstrumentType.fromSource("Government Bond")).isEqualTo(InstrumentType.OTHER);
        }

        @Test
        void listing_id_is_deterministic_for_the_same_ticker_and_mic() {
            ListingId a = ListingId.deterministic(new Ticker("SAN"), new Mic("XMAD"));
            ListingId b = ListingId.deterministic(new Ticker("san"), new Mic("xmad"));
            ListingId other = ListingId.deterministic(new Ticker("SAN"), new Mic("XETR"));
            assertThat(a).isEqualTo(b);
            assertThat(a).isNotEqualTo(other);
            UUID u = a.value();
            assertThat(ListingId.of(u).value()).isEqualTo(u);
        }

        @Test
        void factory_and_shape_helpers() {
            assertThat(Mic.of("xnas").value()).isEqualTo("XNAS");
            assertThat(Mic.hasValidShape(" xnas ")).isTrue();
            assertThat(Mic.hasValidShape("xn")).isFalse();
            assertThat(Mic.hasValidShape(null)).isFalse();
            assertThat(Ticker.of(" aapl ").value()).isEqualTo("AAPL");
            assertThat(Isin.of(" us0378331005 ").value()).isEqualTo("US0378331005");
            assertThat(Isin.hasValidShape("US0378331005")).isTrue();
            assertThat(Isin.hasValidShape("nope")).isFalse();
            assertThat(Isin.hasValidShape(null)).isFalse();
            assertThat(SupportedCurrency.isSupported(" usd ")).isTrue();
            assertThat(SupportedCurrency.isSupported("gbp")).isFalse();
            assertThat(SupportedCurrency.isSupported(null)).isFalse();
            assertThat(InstrumentType.fromSource("REIT")).isEqualTo(InstrumentType.OTHER);
            assertThat(InstrumentType.fromSource("ETF Trust")).isEqualTo(InstrumentType.ETF);
        }

        @Test
        void provenance_accessors_and_validation() {
            Provenance p = Provenance.of("YAHOO_CSV");
            assertThat(p.sourceReferenceValue()).isEmpty();
            assertThat(p.lastImportedAtValue()).isEmpty();
            Provenance q = Provenance.of("YAHOO_CSV", "AAPL", Instant.EPOCH);
            assertThat(q.sourceReferenceValue()).hasValue("AAPL");
            assertThat(q.lastImportedAtValue()).hasValue(Instant.EPOCH);
            assertThatThrownBy(() -> Provenance.of("  ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void import_report_and_counters_are_defensive() {
            ImportCounters c = new ImportCounters(10, 6, 1, 1, 1, 0, 1);
            assertThat(c.rejected()).isEqualTo(3);
            assertThat(ImportCounters.zero().processed()).isZero();
            ImportReport r = new ImportReport("YAHOO_CSV", Instant.EPOCH, Instant.EPOCH, c, null);
            assertThat(r.rejections()).isEmpty();
            Rejection rej = Rejection.of(null, null, RejectionReason.NO_MAPPING, null);
            assertThat(rej.rawSymbol()).isEmpty();
            assertThat(rej.reason()).isEqualTo(RejectionReason.NO_MAPPING);
        }
    }

    @Nested
    class MarketFromRaw {

        @Test
        void builds_a_valid_market_and_defaults_active_when_absent() {
            Market m = Market.fromRaw(new NewMarket("XMAD", "Bolsa de Madrid", "ES", "BMEX", null), PROV);
            assertThat(m.mic().value()).isEqualTo("XMAD");
            assertThat(m.name()).isEqualTo("Bolsa de Madrid");
            assertThat(m.country()).hasValue("ES");
            assertThat(m.operatingMic()).hasValueSatisfying(op -> assertThat(op.value()).isEqualTo("BMEX"));
            assertThat(m.active()).isTrue();
        }

        @Test
        void rejects_blank_name_bad_mic_and_bad_operating_mic() {
            assertThatThrownBy(() -> Market.fromRaw(new NewMarket("XMAD", "  ", "ES", null, "true"), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Market.fromRaw(new NewMarket("XM", "Madrid", "ES", null, "true"), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Market.fromRaw(new NewMarket("XMAD", "Madrid", "ES", "BME", "true"), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parses_an_explicit_inactive_flag() {
            assertThat(Market.fromRaw(new NewMarket("XMAD", "Madrid", "ES", null, "false"), PROV).active()).isFalse();
        }
    }

    @Nested
    class ListingFromRaw {

        private NewListing valid() {
            return new NewListing("Banco Santander, S.A.", "SAN", "XMAD", "EUR",
                    "ES0113900J37", null, "Common Stock", "SAN.MC", null);
        }

        @Test
        void builds_a_valid_listing_with_deterministic_id_and_all_optionals() {
            FinancialInstrumentListing l = FinancialInstrumentListing.fromRaw(valid(), PROV);
            assertThat(l.ticker().value()).isEqualTo("SAN");
            assertThat(l.market().value()).isEqualTo("XMAD");
            assertThat(l.currency()).isEqualTo(SupportedCurrency.EUR);
            assertThat(l.isin()).hasValueSatisfying(i -> assertThat(i.value()).isEqualTo("ES0113900J37"));
            assertThat(l.instrumentType()).hasValue(InstrumentType.EQUITY);
            assertThat(l.providerSymbol()).hasValue("SAN.MC");
            assertThat(l.active()).isTrue();
            assertThat(l.id()).isEqualTo(ListingId.deterministic(new Ticker("SAN"), new Mic("XMAD")));
        }

        @Test
        void builds_a_valid_listing_with_no_optionals() {
            FinancialInstrumentListing l = FinancialInstrumentListing.fromRaw(
                    new NewListing("Apple Inc.", "AAPL", "XNAS", "USD", null, null, null, null, null), PROV);
            assertThat(l.isin()).isEmpty();
            assertThat(l.instrumentType()).isEmpty();
            assertThat(l.providerSymbol()).isEmpty();
            assertThat(l.externalReference()).isEmpty();
        }

        @Test
        void rejects_blank_name_empty_ticker_bad_mic_bad_currency_and_malformed_isin() {
            assertThatThrownBy(() -> FinancialInstrumentListing.fromRaw(
                    new NewListing("  ", "AAPL", "XNAS", "USD", null, null, null, null, null), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FinancialInstrumentListing.fromRaw(
                    new NewListing("Apple", "  ", "XNAS", "USD", null, null, null, null, null), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FinancialInstrumentListing.fromRaw(
                    new NewListing("Apple", "AAPL", "XNA", "USD", null, null, null, null, null), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FinancialInstrumentListing.fromRaw(
                    new NewListing("Voda", "VOD", "XLON", "GBP", null, null, null, null, null), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FinancialInstrumentListing.fromRaw(
                    new NewListing("Apple", "AAPL", "XNAS", "USD", "NOTANISIN", null, null, null, null), PROV))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blank_optionals_map_to_optional_empty() {
            FinancialInstrumentListing l = FinancialInstrumentListing.fromRaw(
                    new NewListing("Apple", "AAPL", "XNAS", "USD", "  ", "  ", "  ", "  ", " "), PROV);
            assertThat(l.isin()).isEmpty();
            assertThat(l.externalReference()).isEmpty();
            assertThat(l.providerSymbol()).isEmpty();
            assertThat(l.active()).isTrue();
        }
    }
}
