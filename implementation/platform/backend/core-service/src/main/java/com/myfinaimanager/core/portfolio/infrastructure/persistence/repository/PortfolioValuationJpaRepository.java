package com.myfinaimanager.core.portfolio.infrastructure.persistence.repository;

import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PortfolioValuationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PortfolioValuationEntity}. Derived queries only — no JPQL, no
 * native SQL. {@code PortfolioValuationPersistenceAdapter} is the only collaborator (ADR-003 §16).
 */
public interface PortfolioValuationJpaRepository extends JpaRepository<PortfolioValuationEntity, UUID> {

    /**
     * The latest snapshot for a portfolio. {@code positions} is fetch-joined; {@code sectors}
     * lazy-loads within the adapter's read transaction (two bag collections cannot be fetch-joined
     * together — {@code MultipleBagFetchException}).
     */
    @EntityGraph(attributePaths = "positions")
    Optional<PortfolioValuationEntity> findByPortfolioId(UUID portfolioId);

    /** Delete the (single) snapshot for a portfolio, if any — used before re-inserting (FR-021). */
    void deleteByPortfolioId(UUID portfolioId);
}
