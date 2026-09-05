package com.myfinaimanager.core.portfolioanalysis.domain.exceptions;

import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;

/**
 * {@code PortfolioAnalysisAiPort.analyze(...)} could not produce a valid result — carries the
 * normalized {@link FailureReason} the worker persists (contract {@code portfolio-analysis-ports.md}
 * Q3).
 */
public class AnalysisFailedException extends RuntimeException {

    private final FailureReason reason;

    public AnalysisFailedException(FailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FailureReason reason() {
        return reason;
    }
}
