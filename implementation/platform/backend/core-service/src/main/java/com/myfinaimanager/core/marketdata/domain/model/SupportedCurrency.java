package com.myfinaimanager.core.marketdata.domain.model;

/**
 * The currencies EN005 supports for market data and FX (ISO 4217). Mirrors the initial supported set
 * used elsewhere on the platform; a third currency is a later, deliberate change (enabler §3).
 */
public enum SupportedCurrency {
    EUR,
    USD;

    /** {@code true} iff {@code code} (any case, trimmed) names a supported currency. */
    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        String c = code.trim().toUpperCase(java.util.Locale.ROOT);
        return c.equals("EUR") || c.equals("USD");
    }

    /** Parse a currency code, or {@code null} when it is absent/blank/unsupported (used for provider metadata). */
    public static SupportedCurrency parseOrNull(String code) {
        return isSupported(code) ? valueOf(code.trim().toUpperCase(java.util.Locale.ROOT)) : null;
    }
}
