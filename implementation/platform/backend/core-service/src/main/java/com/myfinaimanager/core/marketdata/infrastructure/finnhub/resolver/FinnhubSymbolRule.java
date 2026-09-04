package com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver;

/**
 * How to turn a canonical {@code ticker + MIC} into a Finnhub symbol for one market.
 *
 * @param mic      the ISO 10383 MIC this rule applies to
 * @param strategy {@code PASSTHROUGH} — use the ticker unchanged; {@code SUFFIX} — append {@code suffix}
 * @param suffix   the Yahoo/Finnhub-style suffix (e.g. {@code .MC}); empty for {@code PASSTHROUGH}
 */
public record FinnhubSymbolRule(String mic, Strategy strategy, String suffix) {

    public enum Strategy { PASSTHROUGH, SUFFIX }

    public String apply(String ticker) {
        return strategy == Strategy.SUFFIX ? ticker + suffix : ticker;
    }
}
