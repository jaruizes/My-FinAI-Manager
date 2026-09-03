package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import java.util.Optional;

/** Outbound port for persisting and looking up portfolios. Implemented by a persistence adapter. */
public interface PortfolioRepository {

    /** The portfolio already stored for this idempotency key, if any (FR-031a). */
    Optional<Portfolio> findByIdempotencyKey(String idempotencyKey);

    /**
     * Persist the whole aggregate (portfolio + every position) atomically — all or nothing
     * (FR-023). If {@code idempotencyKey} is already used (concurrent duplicate), returns the
     * portfolio already stored instead of creating a second one.
     *
     * @return the persisted portfolio (the given one, or the pre-existing one on an idempotency race)
     * @throws PortfolioNotSavedException on a transient persistence failure — nothing is persisted
     */
    Portfolio save(Portfolio portfolio, String idempotencyKey);
}
