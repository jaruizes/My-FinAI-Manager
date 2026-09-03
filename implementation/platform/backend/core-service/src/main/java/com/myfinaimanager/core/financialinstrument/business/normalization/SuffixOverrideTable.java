package com.myfinaimanager.core.financialinstrument.business.normalization;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The loaded {@code yahoo-exchange-suffix-overrides.csv}, keyed by {@code "EXCHANGE|SUFFIX"}
 * (both upper-cased). Plain business data.
 */
public record SuffixOverrideTable(Map<String, SuffixOverride> byKey) {

    public SuffixOverrideTable {
        byKey = Map.copyOf(byKey);
    }

    public static String key(String exchangeCode, String yahooSuffix) {
        return (exchangeCode == null ? "" : exchangeCode.strip().toUpperCase(Locale.ROOT))
                + "|" + (yahooSuffix == null ? "" : yahooSuffix.strip().toUpperCase(Locale.ROOT));
    }

    public Optional<SuffixOverride> overrideFor(String exchangeCode, String yahooSuffix) {
        if (yahooSuffix == null || yahooSuffix.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key(exchangeCode, yahooSuffix)));
    }
}
