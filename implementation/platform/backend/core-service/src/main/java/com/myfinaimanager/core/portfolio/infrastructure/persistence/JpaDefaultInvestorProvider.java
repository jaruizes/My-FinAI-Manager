package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.portfolio.infrastructure.persistence.repository.InvestorJpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Reads the single default investor seeded by {@code V2__portfolio.sql} (ADR-002 / spec A2), now
 * via Spring Data JPA. Fails fast if the seed row is missing — the platform is misconfigured in
 * that case (behavior unchanged from the previous JDBC implementation).
 */
@Repository
public class JpaDefaultInvestorProvider implements DefaultInvestorProvider {

    private final InvestorJpaRepository investors;

    public JpaDefaultInvestorProvider(InvestorJpaRepository investors) {
        this.investors = investors;
    }

    @Override
    public InvestorId get() {
        return investors.findTopByOrderByCreatedAtAscIdAsc()
                .map(entity -> InvestorId.of(entity.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "No default investor is seeded — check migration V2__portfolio.sql"));
    }
}
