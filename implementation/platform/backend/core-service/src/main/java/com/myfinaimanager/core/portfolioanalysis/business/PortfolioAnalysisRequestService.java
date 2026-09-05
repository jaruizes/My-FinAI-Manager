package com.myfinaimanager.core.portfolioanalysis.business;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/**
 * {@link RequestPortfolioAnalysisUseCase} implementation (US1, US4; research D4, D5). Creates the
 * {@code PENDING} row synchronously, hands off to {@link PortfolioAnalysisWorker} (a
 * <strong>different</strong> bean, so its {@code @Async} proxy applies — research D5), and returns
 * immediately: the create/request transaction has already committed by the time the async task
 * starts (FR-056), because {@code PortfolioAnalysisRepository.save} commits its own transaction
 * before returning.
 */
@Service
public class PortfolioAnalysisRequestService implements RequestPortfolioAnalysisUseCase {

    private final PortfolioAnalysisRepository repository;
    private final PortfolioContextGateway contextGateway;
    private final PortfolioAnalysisWorker worker;
    private final Clock clock;

    public PortfolioAnalysisRequestService(PortfolioAnalysisRepository repository,
                                           PortfolioContextGateway contextGateway,
                                           PortfolioAnalysisWorker worker,
                                           Clock clock) {
        this.repository = repository;
        this.contextGateway = contextGateway;
        this.worker = worker;
        this.clock = clock;
    }

    @Override
    public PortfolioAnalysis requestAutomatic(UUID portfolioId) {
        return createAndTrigger(portfolioId, CreationTrigger.AUTOMATIC);
    }

    @Override
    public PortfolioAnalysis requestManual(UUID portfolioId) {
        // Validates the portfolio exists — PortfolioNotFoundException propagates as the 404 the
        // controller already maps (contract P3). The snapshot itself is discarded here; the worker
        // re-fetches a fresh one once RUNNING, so a stale read never leaks into the analysis (US3).
        contextGateway.fetch(portfolioId);

        repository.findLatestByPortfolioId(portfolioId).ifPresent(latest -> {
            if (latest.status() == AnalysisStatus.PENDING || latest.status() == AnalysisStatus.RUNNING) {
                throw new AnalysisAlreadyInProgressException(
                        "an analysis is already PENDING or RUNNING for portfolio " + portfolioId);
            }
        });

        // The database's partial unique index is the authoritative guard for a genuine concurrent
        // race between this pre-check and the insert below (research D4) — the persistence adapter
        // translates a constraint violation into the same AnalysisAlreadyInProgressException.
        return createAndTrigger(portfolioId, CreationTrigger.MANUAL);
    }

    private PortfolioAnalysis createAndTrigger(UUID portfolioId, CreationTrigger trigger) {
        PortfolioAnalysis requested = PortfolioAnalysis.requested(portfolioId, clock.instant(), trigger);
        PortfolioAnalysis saved = repository.save(requested);
        worker.runAsync(saved.id());
        return saved;
    }
}
