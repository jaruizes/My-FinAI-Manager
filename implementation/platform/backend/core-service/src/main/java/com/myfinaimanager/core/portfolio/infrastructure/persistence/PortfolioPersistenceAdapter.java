package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotSavedException;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PortfolioEntity;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.mapper.PortfolioPersistenceMapper;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.repository.PortfolioJpaRepository;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Data JPA persistence for the {@code Portfolio} aggregate (replaces the previous
 * JDBC adapter). Owns the {@code portfolio} / {@code position} tables (AR-020);
 * Flyway owns the schema, Hibernate never alters it ({@code ddl-auto: none}).
 *
 * <p>Behavior is identical to the JDBC adapter it replaces (EN003 — behavior-preserving migration):
 * the whole aggregate is written in one transaction (all or nothing, FR-023); a concurrent
 * duplicate {@code idempotency_key} resolves to the already-stored portfolio (200 replay); any
 * other integrity violation persists nothing and surfaces as {@link PortfolioNotSavedException}
 * (HTTP 503); a transient failure likewise persists nothing.
 */
@Repository
public class PortfolioPersistenceAdapter implements PortfolioRepository {

    private final PortfolioJpaRepository portfolios;
    private final PortfolioPersistenceMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public PortfolioPersistenceAdapter(PortfolioJpaRepository portfolios,
                                       PortfolioPersistenceMapper mapper,
                                       PlatformTransactionManager transactionManager) {
        this.portfolios = portfolios;
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
    }

    @Override
    public Optional<Portfolio> findByIdempotencyKey(String idempotencyKey) {
        return readOnlyTemplate.execute(status ->
                portfolios.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain));
    }

    @Override
    public Portfolio save(Portfolio portfolio, String idempotencyKey) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                PortfolioEntity entity = mapper.toEntity(portfolio, idempotencyKey);
                portfolios.saveAndFlush(entity);
            });
            return portfolio;

        } catch (DataIntegrityViolationException e) {
            // The write transaction has already rolled back; this re-read runs on a fresh
            // transaction. A duplicate idempotency key means a concurrent submission of the same
            // creation attempt won the race — return that portfolio. Any other constraint
            // violation (e.g. a duplicate instrument that slipped past domain validation) leaves
            // nothing persisted and is reported as not-saved.
            return findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PortfolioNotSavedException(
                            "portfolio could not be saved (constraint violation)", e));
        } catch (DataAccessException e) {
            throw new PortfolioNotSavedException("portfolio could not be saved", e);
        }
    }
}
