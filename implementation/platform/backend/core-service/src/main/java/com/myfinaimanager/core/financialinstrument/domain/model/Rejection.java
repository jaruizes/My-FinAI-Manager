package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Objects;

/**
 * One source row that did not enter the catalog, with enough context for actionable diagnostics
 * (EN004 §12, §24). Carries the <em>raw</em> provider values — the raw symbol as-supplied and the
 * source exchange code — so an operator can find the offending line.
 */
public record Rejection(String rawSymbol, String sourceExchangeCode, RejectionReason reason, String detail) {

    public Rejection {
        Objects.requireNonNull(reason, "reason");
        rawSymbol = rawSymbol == null ? "" : rawSymbol;
        sourceExchangeCode = sourceExchangeCode == null ? "" : sourceExchangeCode;
        detail = detail == null ? "" : detail;
    }

    public static Rejection of(String rawSymbol, String sourceExchangeCode, RejectionReason reason, String detail) {
        return new Rejection(rawSymbol, sourceExchangeCode, reason, detail);
    }
}
