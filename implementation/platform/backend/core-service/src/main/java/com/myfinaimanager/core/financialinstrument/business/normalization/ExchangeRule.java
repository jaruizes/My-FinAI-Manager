package com.myfinaimanager.core.financialinstrument.business.normalization;

/**
 * One row of {@code yahoo-exchange-to-mic-mapping.csv} — how a Yahoo {@code Exchange} value maps to
 * canonical reference data (contracts/reference-mapping.md §1). Loaded by an infrastructure adapter
 * and handed to {@link YahooSymbolNormalizer} as plain business data (no CSV type crosses in).
 *
 * @param exchangeCode        the Yahoo {@code Exchange} value (the lookup key), e.g. {@code MCE}
 * @param expectedYahooSuffix the suffix (without the dot) this exchange's symbols carry; blank = none
 * @param canonicalMic        ISO 10383 MIC to store; blank = unresolved (generic/ambiguous code)
 * @param currency            candidate ISO 4217 currency; blank = unresolved
 * @param supportedForFd002   {@code true} if this exchange is eligible for the selectable catalog
 */
public record ExchangeRule(
        String exchangeCode,
        String expectedYahooSuffix,
        String canonicalMic,
        String currency,
        boolean supportedForFd002) {

    public boolean hasSuffix() {
        return expectedYahooSuffix != null && !expectedYahooSuffix.isBlank();
    }

    public boolean hasMic() {
        return canonicalMic != null && !canonicalMic.isBlank();
    }

    public boolean hasCurrency() {
        return currency != null && !currency.isBlank();
    }
}
