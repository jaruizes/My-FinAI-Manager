package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisId;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.mapper.PortfolioAnalysisPersistenceMapper;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.repository.PortfolioAnalysisJpaRepository;

/**
 * Spring Data JPA persistence for {@link PortfolioAnalysis} (data-model.md §1, §3; contract
 * {@code portfolio-analysis-ports.md} C3). Owns only the {@code portfolio_analysis}/
 * {@code portfolio_analysis_insight}/{@code portfolio_analysis_risk} tables — no FK into, and no
 * query against, any FD004 table. Flyway ({@code V5}) owns the schema.
 *
 * <p>{@link #save} is a plain merge-by-id (insert for a new {@link AnalysisId}, update for one
 * already persisted — R1). The database's partial unique index
 * ({@code portfolio_analysis_one_open_per_portfolio_uk}) is the authoritative duplicate-open-request
 * guard (research D4): a violation on insert is translated to
 * {@link AnalysisAlreadyInProgressException} (R2) — the same pattern {@code PortfolioPersistenceAdapter}
 * already uses for its own {@code idempotency_key} constraint (FD001).
 */
@Repository
public class PortfolioAnalysisPersistenceAdapter implements PortfolioAnalysisRepository {

    private final PortfolioAnalysisJpaRepository analyses;
    private final PortfolioAnalysisPersistenceMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public PortfolioAnalysisPersistenceAdapter(PortfolioAnalysisJpaRepository analyses,
                                               PortfolioAnalysisPersistenceMapper mapper,
                                               PlatformTransactionManager transactionManager) {
        this.analyses = analyses;
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
    }

    @Override
    public PortfolioAnalysis save(PortfolioAnalysis analysis) {
        try {
            return transactionTemplate.execute(status ->
                    mapper.toDomain(analyses.saveAndFlush(mapper.toEntity(analysis))));

        } catch (DataIntegrityViolationException e) {
            // The write transaction has already rolled back. Only one constraint in this table can
            // fire on an insert (the partial unique index for "one open request per portfolio") —
            // any other violation would be a programming error, not a real concurrency race.
            throw new AnalysisAlreadyInProgressException(
                    "an analysis is already PENDING or RUNNING for portfolio " + analysis.portfolioId());
        }
    }

    @Override
    public Optional<PortfolioAnalysis> findLatestByPortfolioId(UUID portfolioId) {
        return readOnlyTemplate.execute(status ->
                analyses.findFirstByPortfolioIdOrderByRequestedAtDesc(portfolioId).map(mapper::toDomain));
    }

    @Override
    public Optional<PortfolioAnalysis> findById(AnalysisId id) {
        return readOnlyTemplate.execute(status ->
                analyses.findById(id.value()).map(mapper::toDomain));
    }
}
