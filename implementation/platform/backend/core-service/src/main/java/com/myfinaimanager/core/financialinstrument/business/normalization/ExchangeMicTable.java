package com.myfinaimanager.core.financialinstrument.business.normalization;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The loaded {@code yahoo-exchange-to-mic-mapping.csv}, keyed by (upper-cased) Yahoo exchange code.
 * Plain business data — an infrastructure adapter builds it from the CSV.
 */
public record ExchangeMicTable(Map<String, ExchangeRule> byCode) {

    public ExchangeMicTable {
        byCode = Map.copyOf(byCode);
    }

    public Optional<ExchangeRule> ruleFor(String exchangeCode) {
        if (exchangeCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(exchangeCode.strip().toUpperCase(Locale.ROOT)));
    }
}
