package com.myfinaimanager.core.financialinstrument.business.normalization;

import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import com.myfinaimanager.core.financialinstrument.domain.model.RejectionReason;
import com.myfinaimanager.core.financialinstrument.domain.model.SupportedCurrency;
import com.myfinaimanager.core.financialinstrument.domain.model.Ticker;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic mapping-driven normalization of a Yahoo {@code (symbol, exchange)} pair into
 * canonical reference data — {@code EN004-yahoo-normalization-decision.md} §"Algorithm" /
 * contracts/reference-mapping.md §3. Pure: constructed with the two loaded mapping tables, no Spring
 * dependency in the logic. TDD.
 *
 * <p><strong>Suffix removal is mapping-driven only.</strong> A suffix is stripped only when an
 * explicit {@code (exchange, suffix)} override matches, or the exchange rule declares that exact
 * suffix. A symbol on an exchange configured without a suffix is never truncated, even if it
 * contains a period. A symbol/mapping disagreement is quarantined, never guessed.
 */
public final class YahooSymbolNormalizer {

    private final ExchangeMicTable exchanges;
    private final SuffixOverrideTable overrides;

    public YahooSymbolNormalizer(ExchangeMicTable exchanges, SuffixOverrideTable overrides) {
        this.exchanges = exchanges;
        this.overrides = overrides;
    }

    public NormalizationResult normalize(String rawSymbol, String exchangeCode) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return NormalizationResult.rejected(RejectionReason.EMPTY_TICKER, "blank source symbol");
        }
        String providerSymbol = rawSymbol.strip();
        String sym = providerSymbol.toUpperCase(Locale.ROOT);
        String suffixCandidate = suffixAfterLastDot(sym);

        String mic;
        String currency;
        boolean strip;

        Optional<SuffixOverride> override = overrides.overrideFor(exchangeCode, suffixCandidate);
        if (override.isPresent()) {
            mic = override.get().canonicalMic();
            currency = override.get().currency();
            strip = suffixCandidate != null;
        } else {
            Optional<ExchangeRule> maybeRule = exchanges.ruleFor(exchangeCode);
            if (maybeRule.isEmpty()) {
                return NormalizationResult.rejected(RejectionReason.NO_MAPPING,
                        "no exchange mapping for '" + exchangeCode + "'");
            }
            ExchangeRule rule = maybeRule.get();
            if (!rule.supportedForFd002()) {
                if (rule.hasCurrency() && !SupportedCurrency.isSupported(rule.currency())) {
                    return NormalizationResult.rejected(RejectionReason.UNSUPPORTED_CURRENCY,
                            "exchange '" + exchangeCode + "' trades in " + rule.currency());
                }
                return NormalizationResult.rejected(RejectionReason.NOT_SUPPORTED_FOR_FD002,
                        "exchange '" + exchangeCode + "' is not enabled for FD002");
            }
            mic = rule.canonicalMic();
            currency = rule.currency();
            if (rule.hasSuffix()) {
                String expected = "." + rule.expectedYahooSuffix().strip().toUpperCase(Locale.ROOT);
                if (sym.endsWith(expected)) {
                    strip = true;
                } else {
                    return NormalizationResult.rejected(RejectionReason.SUFFIX_MISMATCH,
                            "symbol '" + providerSymbol + "' does not carry the expected '" + expected
                                    + "' suffix for exchange '" + exchangeCode + "'");
                }
            } else {
                strip = false;
            }
        }

        String tickerText = strip ? beforeLastDot(sym) : sym;

        if (isBlank(mic) || isBlank(currency)) {
            return NormalizationResult.rejected(RejectionReason.AMBIGUOUS_EXCHANGE,
                    "cannot resolve MIC/currency for '" + exchangeCode + "' from symbol '" + providerSymbol + "'");
        }
        if (!SupportedCurrency.isSupported(currency)) {
            return NormalizationResult.rejected(RejectionReason.UNSUPPORTED_CURRENCY,
                    "resolved currency " + currency + " is not EUR/USD");
        }
        if (tickerText.isBlank()) {
            return NormalizationResult.rejected(RejectionReason.EMPTY_TICKER,
                    "canonical ticker is empty after suffix removal from '" + providerSymbol + "'");
        }

        try {
            return NormalizationResult.accepted(
                    providerSymbol,
                    new Ticker(tickerText),
                    new Mic(mic),
                    SupportedCurrency.parse(currency));
        } catch (IllegalArgumentException e) {
            return NormalizationResult.rejected(RejectionReason.EMPTY_TICKER,
                    "invalid canonical value: " + e.getMessage());
        }
    }

    private static String suffixAfterLastDot(String sym) {
        int i = sym.lastIndexOf('.');
        return i < 0 ? null : sym.substring(i + 1);
    }

    private static String beforeLastDot(String sym) {
        int i = sym.lastIndexOf('.');
        return i <= 0 ? "" : sym.substring(0, i);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
