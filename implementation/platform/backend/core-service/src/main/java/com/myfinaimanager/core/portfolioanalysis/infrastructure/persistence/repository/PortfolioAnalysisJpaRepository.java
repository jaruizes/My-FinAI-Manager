package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence.entity.PortfolioAnalysisEntity;

/**
 * Spring Data repository for {@link PortfolioAnalysisEntity}. Derived queries only — no JPQL, no
 * native SQL. {@code PortfolioAnalysisPersistenceAdapter} is the only collaborator (ADR-003 §16).
 */
public interface PortfolioAnalysisJpaRepository extends JpaRepository<PortfolioAnalysisEntity, UUID> {

    /**
     * The latest (greatest {@code requestedAt}) analysis for a Portfolio. {@code insights} is
     * fetch-joined; {@code risks} lazy-loads within the adapter's read transaction (two bag
     * collections cannot be fetch-joined together — {@code MultipleBagFetchException}).
     */
    @EntityGraph(attributePaths = "insights")
    Optional<PortfolioAnalysisEntity> findFirstByPortfolioIdOrderByRequestedAtDesc(UUID portfolioId);
}
