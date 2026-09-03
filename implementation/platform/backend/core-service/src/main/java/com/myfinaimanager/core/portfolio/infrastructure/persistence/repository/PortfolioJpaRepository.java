package com.myfinaimanager.core.portfolio.infrastructure.persistence.repository;

import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PortfolioEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PortfolioEntity}. Derived queries only — no JPQL, no native
 * SQL. The persistence adapter ({@code PortfolioPersistenceAdapter}) is the only collaborator;
 * nothing outside {@code infrastructure.persistence} touches this interface (ADR-003 §16).
 */
public interface PortfolioJpaRepository extends JpaRepository<PortfolioEntity, UUID> {

    /** Fetch the portfolio for an idempotency key with its positions eagerly loaded. */
    @EntityGraph(attributePaths = "positions")
    Optional<PortfolioEntity> findByIdempotencyKey(String idempotencyKey);
}
