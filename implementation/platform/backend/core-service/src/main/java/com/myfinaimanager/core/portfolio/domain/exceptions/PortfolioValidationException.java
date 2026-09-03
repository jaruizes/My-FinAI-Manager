package com.myfinaimanager.core.portfolio.domain.exceptions;

import com.myfinaimanager.core.portfolio.domain.model.Violation;

import java.util.List;

/**
 * Thrown by {@link Portfolio#create} when the portfolio or any of its positions breaks a business
 * rule. Carries <strong>every</strong> {@link Violation} found in one pass (not fail-fast), so the
 * investor can see all problems at once (FR-024). Nothing is persisted when this is thrown.
 */
public final class PortfolioValidationException extends RuntimeException {

    private final transient List<Violation> violations;

    public PortfolioValidationException(List<Violation> violations) {
        super(violations.size() + " portfolio validation problem(s)");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("a validation exception needs at least one violation");
        }
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }
}
