package com.myfinaimanager.core.financialinstrument.business.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.business.normalization.NormalizationResult.Accepted;
import com.myfinaimanager.core.financialinstrument.business.normalization.NormalizationResult.Rejected;
import com.myfinaimanager.core.financialinstrument.domain.model.RejectionReason;
import com.myfinaimanager.core.financialinstrument.domain.model.SupportedCurrency;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TDD unit tests for the deterministic mapping-driven Yahoo → canonical normalizer
 * ({@code EN004-yahoo-normalization-decision.md} §"Algorithm"; contracts/reference-mapping.md §3).
 * One test per worked example + per {@link RejectionReason}. <strong>No</strong> generic
 * "strip after the last dot".
 */
class YahooSymbolNormalizerTest {

    // Minimal tables mirroring the real mapping-CSV rows the matrix exercises.
    private static final ExchangeMicTable EXCHANGES = new ExchangeMicTable(Map.of(
            "MCE", new ExchangeRule("MCE", "MC", "XMAD", "EUR", true),
            "NMS", new ExchangeRule("NMS", "", "XNAS", "USD", true),
            "FRA", new ExchangeRule("FRA", "F", "XFRA", "EUR", true),
            "LSE", new ExchangeRule("LSE", "L", "XLON", "GBP", false),
            "ENX", new ExchangeRule("ENX", "", "", "", false),
            "EUX", new ExchangeRule("EUX", "EX", "", "EUR", false),
            "BME", new ExchangeRule("BME", "", "", "EUR", true),      // supported but MIC not resolvable
            "SGX", new ExchangeRule("SGX", "", "XSES", "SGD", true)));  // supported, resolvable, unsupported currency

    private static final SuffixOverrideTable OVERRIDES = new SuffixOverrideTable(Map.of(
            SuffixOverrideTable.key("FRA", "DE"), new SuffixOverride("XETR", "EUR"),
            SuffixOverrideTable.key("ENX", "PA"), new SuffixOverride("XPAR", "EUR"),
            SuffixOverrideTable.key("ENX", "NX"), new SuffixOverride("XEUR", "")));

    private final YahooSymbolNormalizer normalizer = new YahooSymbolNormalizer(EXCHANGES, OVERRIDES);

    private Accepted accepted(String symbol, String exchange) {
        NormalizationResult r = normalizer.normalize(symbol, exchange);
        assertThat(r).isInstanceOf(Accepted.class);
        return (Accepted) r;
    }

    private RejectionReason rejectedReason(String symbol, String exchange) {
        NormalizationResult r = normalizer.normalize(symbol, exchange);
        assertThat(r).isInstanceOf(Rejected.class);
        return ((Rejected) r).reason();
    }

    @Test
    void spanish_listing_strips_the_mapped_suffix() {
        Accepted a = accepted("SAN.MC", "MCE");
        assertThat(a.providerSymbol()).isEqualTo("SAN.MC");
        assertThat(a.ticker().value()).isEqualTo("SAN");
        assertThat(a.mic().value()).isEqualTo("XMAD");
        assertThat(a.currency()).isEqualTo(SupportedCurrency.EUR);

        Accepted ibe = accepted("IBE.MC", "MCE");
        assertThat(ibe.ticker().value()).isEqualTo("IBE");
        assertThat(ibe.mic().value()).isEqualTo("XMAD");
    }

    @Test
    void us_symbol_with_no_configured_suffix_is_left_intact() {
        Accepted a = accepted("AAPL", "NMS");
        assertThat(a.providerSymbol()).isEqualTo("AAPL");
        assertThat(a.ticker().value()).isEqualTo("AAPL");
        assertThat(a.mic().value()).isEqualTo("XNAS");
        assertThat(a.currency()).isEqualTo(SupportedCurrency.USD);
    }

    @Test
    void a_us_symbol_that_contains_a_period_is_not_truncated_when_the_exchange_has_no_suffix() {
        // BRK.A on Nasdaq-style listing: NMS rule has an empty suffix -> keep the whole symbol.
        Accepted a = accepted("BRK.A", "NMS");
        assertThat(a.ticker().value()).isEqualTo("BRK.A");
    }

    @Test
    void suffix_override_wins_over_the_exchange_rule() {
        Accepted a = accepted("ADS.DE", "FRA");
        assertThat(a.ticker().value()).isEqualTo("ADS");
        assertThat(a.mic().value()).isEqualTo("XETR");
        assertThat(a.currency()).isEqualTo(SupportedCurrency.EUR);
    }

    @Test
    void gbp_listing_is_rejected_as_unsupported_currency() {
        assertThat(rejectedReason("VOD.L", "LSE")).isEqualTo(RejectionReason.UNSUPPORTED_CURRENCY);
    }

    @Test
    void generic_euronext_without_a_resolving_override_is_ambiguous() {
        assertThat(rejectedReason("XYZ.NX", "ENX")).isEqualTo(RejectionReason.AMBIGUOUS_EXCHANGE);
    }

    @Test
    void a_symbol_whose_suffix_disagrees_with_the_mapping_is_a_mismatch_not_a_guess() {
        assertThat(rejectedReason("FOO.XX", "MCE")).isEqualTo(RejectionReason.SUFFIX_MISMATCH);
    }

    @Test
    void a_symbol_that_is_only_a_suffix_yields_an_empty_ticker() {
        assertThat(rejectedReason(".MC", "MCE")).isEqualTo(RejectionReason.EMPTY_TICKER);
    }

    @Test
    void an_exchange_not_supported_for_fd002_but_with_a_supported_currency_is_rejected_as_not_supported() {
        assertThat(rejectedReason("BAR", "EUX")).isEqualTo(RejectionReason.NOT_SUPPORTED_FOR_FD002);
    }

    @Test
    void an_unknown_exchange_has_no_mapping() {
        assertThat(rejectedReason("WHATEVER", "ZZZ")).isEqualTo(RejectionReason.NO_MAPPING);
    }

    @Test
    void resolving_a_generic_euronext_row_via_a_currency_bearing_override() {
        Accepted a = accepted("TTE.PA", "ENX");
        assertThat(a.ticker().value()).isEqualTo("TTE");
        assertThat(a.mic().value()).isEqualTo("XPAR");
        assertThat(a.currency()).isEqualTo(SupportedCurrency.EUR);
    }

    @Test
    void a_null_source_symbol_is_an_empty_ticker() {
        assertThat(rejectedReason(null, "NMS")).isEqualTo(RejectionReason.EMPTY_TICKER);
        assertThat(rejectedReason("   ", "NMS")).isEqualTo(RejectionReason.EMPTY_TICKER);
    }

    @Test
    void an_unsupported_exchange_with_no_resolvable_currency_is_not_supported_for_fd002() {
        assertThat(rejectedReason("FOO", "ENX")).isEqualTo(RejectionReason.NOT_SUPPORTED_FOR_FD002);
    }

    @Test
    void a_supported_exchange_whose_mic_cannot_be_resolved_is_ambiguous() {
        assertThat(rejectedReason("FOO", "BME")).isEqualTo(RejectionReason.AMBIGUOUS_EXCHANGE);
    }

    @Test
    void a_supported_exchange_that_resolves_to_a_non_eur_usd_currency_is_unsupported_currency() {
        assertThat(rejectedReason("FOO", "SGX")).isEqualTo(RejectionReason.UNSUPPORTED_CURRENCY);
    }

    @Test
    void input_is_trimmed_and_upper_cased() {
        Accepted a = accepted("  san.mc  ", " mce ");
        assertThat(a.providerSymbol()).isEqualTo("san.mc");   // provider symbol keeps original case, trimmed
        assertThat(a.ticker().value()).isEqualTo("SAN");
    }
}
