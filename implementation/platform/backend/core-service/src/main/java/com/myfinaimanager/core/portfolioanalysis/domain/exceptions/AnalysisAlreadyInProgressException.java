package com.myfinaimanager.core.portfolioanalysis.domain.exceptions;

/**
 * A new analysis request arrived while the latest analysis for that Portfolio is still
 * {@code PENDING} or {@code RUNNING} (FR-013, BR-008). The database's partial unique index is the
 * authoritative guard (research D4) — this exception is raised either by a fast-path pre-check or
 * by translating the resulting constraint violation.
 */
public class AnalysisAlreadyInProgressException extends RuntimeException {

    public AnalysisAlreadyInProgressException(String message) {
        super(message);
    }
}
