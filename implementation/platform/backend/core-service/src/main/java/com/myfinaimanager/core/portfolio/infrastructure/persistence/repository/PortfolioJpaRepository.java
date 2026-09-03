package com.myfinaimanager.core.portfolio.infrastructure.persistence.repository;

import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.PortfolioEntity;
import java.util.List;
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

    /**
     * FD003 — every portfolio of one investor, newest first (createdAt desc, id desc as a
     * deterministic tiebreak), each with its positions loaded in one query. Derived query, no JPQL.
     */
    @EntityGraph(attributePaths = "positions")
    List<PortfolioEntity> findAllByInvestorIdOrderByCreatedAtDescIdDesc(UUID investorId);

    /**
     * FD003 — one investor's portfolio by id, with its positions loaded; empty for an unknown id or
     * another investor's portfolio (scoping is in the query). Derived query, no JPQL.
     */
    @EntityGraph(attributePaths = "positions")
    Optional<PortfolioEntity> findByIdAndInvestorId(UUID id, UUID investorId);
}
