package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * The Position/trading currencies FD002 supports initially: EUR and USD (ISO 4217; enabler §6,
 * §33.5; FD002 BR-003; VC-004). Modeled as an enum rather than a persisted master table — the
 * initial set is small (research.md D2). Adding a currency later is a one-line change plus a
 * migration widening the {@code financial_instrument.currency} CHECK.
 */
public enum SupportedCurrency {
    EUR,
    USD;

    /** Parse a raw currency string (trimmed, upper-cased). Throws if it is not EUR or USD. */
    public static SupportedCurrency parse(String raw) {
        Objects.requireNonNull(raw, "currency");
        String code = raw.strip().toUpperCase(Locale.ROOT);
        for (SupportedCurrency c : values()) {
            if (c.name().equals(code)) {
                return c;
            }
        }
        throw new IllegalArgumentException("unsupported currency (only EUR and USD): " + raw);
    }

    public static boolean isSupported(String raw) {
        if (raw == null) {
            return false;
        }
        String code = raw.strip().toUpperCase(Locale.ROOT);
        return "EUR".equals(code) || "USD".equals(code);
    }
}
