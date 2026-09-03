package com.myfinaimanager.core.portfolio.domain.exceptions;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import java.util.Objects;

/**
 * Thrown by {@code PortfolioQueryService.view(id)} when no portfolio with the requested id belongs
 * to the current investor — an unknown id or another investor's portfolio (FD003 FR-013, FR-022).
 *
 * <p>Carries the requested id (as a string) for the RFC 9457 problem {@code instance}. Mapped to
 * {@code 404 application/problem+json} ({@code type = /problems/portfolio-not-found}) by
 * {@code PortfolioExceptionHandler}. A malformed (non-UUID) path segment is a different case —
 * Spring rejects it with a {@code 400} before the service is reached.
 */
public final class PortfolioNotFoundException extends RuntimeException {

    private final String portfolioId;

    public PortfolioNotFoundException(PortfolioId id) {
        super("No portfolio found for id " + Objects.requireNonNull(id, "id"));
        this.portfolioId = id.toString();
    }

    /** The requested portfolio id, for the problem {@code instance}. */
    public String portfolioId() {
        return portfolioId;
    }
}
