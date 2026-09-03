package com.myfinaimanager.core.financialinstrument.business.normalization;

import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import com.myfinaimanager.core.financialinstrument.domain.model.RejectionReason;
import com.myfinaimanager.core.financialinstrument.domain.model.SupportedCurrency;
import com.myfinaimanager.core.financialinstrument.domain.model.Ticker;

/**
 * The outcome of normalizing one Yahoo source row (contracts/reference-mapping.md §3). Either the
 * row resolves to canonical reference data ({@link Accepted}) or it is {@link Rejected} with a
 * reason — never guessed.
 */
public sealed interface NormalizationResult permits NormalizationResult.Accepted, NormalizationResult.Rejected {

    record Accepted(String providerSymbol, Ticker ticker, Mic mic, SupportedCurrency currency)
            implements NormalizationResult {
    }

    record Rejected(RejectionReason reason, String detail) implements NormalizationResult {
    }

    static Accepted accepted(String providerSymbol, Ticker ticker, Mic mic, SupportedCurrency currency) {
        return new Accepted(providerSymbol, ticker, mic, currency);
    }

    static Rejected rejected(RejectionReason reason, String detail) {
        return new Rejected(reason, detail);
    }
}
