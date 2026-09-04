package com.myfinaimanager.core.portfolio.domain.events;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import java.util.Objects;

/**
 * Raised once a brand-new {@code Portfolio} has been durably persisted (FD001) — never on an
 * idempotency replay. Consumed synchronously, after the create transaction has committed, to
 * trigger the FD004 valuation (FR-001, FR-004). Carries only the portfolio identity; the consumer
 * re-loads the aggregate.
 */
public record PortfolioCreatedEvent(PortfolioId portfolioId) {

    public PortfolioCreatedEvent {
        Objects.requireNonNull(portfolioId, "portfolioId");
    }
}
