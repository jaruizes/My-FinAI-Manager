package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.model.Portfolio;

/**
 * Outcome of a successful create.
 *
 * @param portfolio the persisted portfolio (new, or the one already stored for the idempotency key)
 * @param replayed  {@code true} when the idempotency key had already been used — no second
 *                  portfolio was created (FR-031a)
 */
public record CreatePortfolioResult(Portfolio portfolio, boolean replayed) {
}
