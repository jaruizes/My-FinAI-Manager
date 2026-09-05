package com.myfinaimanager.core.portfolioanalysis.business;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/**
 * {@link PortfolioAnalysisQueryUseCase} implementation (US2). Validates the portfolio exists
 * (via {@link PortfolioContextGateway#fetch}, discarding the snapshot) before reading the latest
 * analysis, so an unknown portfolio surfaces the same {@code PortfolioNotFoundException} → 404 the
 * rest of the platform already uses.
 */
@Service
public class PortfolioAnalysisQueryService implements PortfolioAnalysisQueryUseCase {

    private final PortfolioAnalysisRepository repository;
    private final PortfolioContextGateway contextGateway;

    public PortfolioAnalysisQueryService(PortfolioAnalysisRepository repository,
                                         PortfolioContextGateway contextGateway) {
        this.repository = repository;
        this.contextGateway = contextGateway;
    }

    @Override
    public Optional<PortfolioAnalysis> findLatest(UUID portfolioId) {
        contextGateway.fetch(portfolioId); // validates existence; propagates PortfolioNotFoundException
        return repository.findLatestByPortfolioId(portfolioId);
    }
}
