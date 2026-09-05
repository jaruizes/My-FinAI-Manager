package com.myfinaimanager.core.portfolioanalysis.domain.model;

import java.util.Objects;

/**
 * The compact, deterministic prompt payload rendered from a {@link PortfolioContextSnapshot}
 * (research D6). {@code sufficient=false} means the worker resolves straight to
 * {@code FAILED(INSUFFICIENT_DATA)} with <strong>zero</strong> calls to {@code PortfolioAnalysisAiPort}
 * — {@code text} is still populated in that case (for logging/traceability) but is never sent to a
 * provider.
 */
public record PortfolioAnalysisContext(String text, boolean sufficient) {

    public PortfolioAnalysisContext {
        Objects.requireNonNull(text, "text");
    }
}
