package com.myfinaimanager.core.portfolio.infrastructure.persistence.repository;

import com.myfinaimanager.core.portfolio.infrastructure.persistence.entity.InvestorEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link InvestorEntity}. Used only to read the single seeded default
 * investor (ADR-002). Derived query only.
 */
public interface InvestorJpaRepository extends JpaRepository<InvestorEntity, UUID> {

    /** The earliest-seeded investor — deterministic tiebreak on id, mirroring the old SQL. */
    Optional<InvestorEntity> findTopByOrderByCreatedAtAscIdAsc();
}
