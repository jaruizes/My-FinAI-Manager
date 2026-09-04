package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioValuationRepository;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.mapper.PortfolioValuationPersistenceMapper;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.repository.PortfolioValuationJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Data JPA persistence for the FD004 valuation snapshot. Owns only the
 * {@code portfolio_valuation} / {@code position_valuation} / {@code sector_allocation} tables
 * (AR-020); it never touches {@code portfolio} / {@code position} (SC-008). Flyway owns the schema.
 *
 * <p>{@link #upsertLatest} keeps exactly one snapshot per portfolio: it deletes the existing
 * snapshot (children cascade) and inserts the new graph, in one transaction — idempotent, no
 * duplicate rows on re-valuation (FR-021, SC-002).
 */
@Repository
public class PortfolioValuationPersistenceAdapter implements PortfolioValuationRepository {

    private final PortfolioValuationJpaRepository valuations;
    private final PortfolioValuationPersistenceMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public PortfolioValuationPersistenceAdapter(PortfolioValuationJpaRepository valuations,
                                                PortfolioValuationPersistenceMapper mapper,
                                                PlatformTransactionManager transactionManager) {
        this.valuations = valuations;
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
    }

    @Override
    public void upsertLatest(PortfolioValuation valuation) {
        transactionTemplate.executeWithoutResult(status -> {
            valuations.deleteByPortfolioId(valuation.portfolioId().value());
            valuations.flush(); // delete must hit the DB before the insert (portfolio_id is UNIQUE)
            valuations.saveAndFlush(mapper.toEntity(valuation));
        });
    }

    @Override
    public Optional<PortfolioValuation> findByPortfolioId(PortfolioId portfolioId) {
        return readOnlyTemplate.execute(status ->
                valuations.findByPortfolioId(portfolioId.value()).map(mapper::toDomain));
    }
}
