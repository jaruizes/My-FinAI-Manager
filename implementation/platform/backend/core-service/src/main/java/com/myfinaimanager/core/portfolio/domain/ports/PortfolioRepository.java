package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import java.util.List;
import java.util.Optional;

/** Outbound port for persisting and looking up portfolios. Implemented by a persistence adapter. */
public interface PortfolioRepository {

    /** The portfolio already stored for this idempotency key, if any (FR-031a). */
    Optional<Portfolio> findByIdempotencyKey(String idempotencyKey);

    /**
     * Every portfolio owned by {@code investorId}, ordered most-recently-created first (createdAt
     * desc, id desc as a deterministic tiebreak — FD003 A3). Each returned aggregate has all and
     * only its own positions loaded. Pure read — no write, no business event, no external call;
     * runs in a read-only transaction; investor scoping is in the query, never a post-filter
     * (FD003 C1 P1–P6).
     */
    List<Portfolio> findAllByInvestor(InvestorId investorId);

    /**
     * The portfolio with this {@code id} <strong>iff it belongs to {@code investorId}</strong>,
     * with its positions loaded; empty for an unknown id or another investor's portfolio. Pure
     * read, read-only transaction (FD003 C1 P1–P6).
     */
    Optional<Portfolio> findByIdForInvestor(PortfolioId id, InvestorId investorId);

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
