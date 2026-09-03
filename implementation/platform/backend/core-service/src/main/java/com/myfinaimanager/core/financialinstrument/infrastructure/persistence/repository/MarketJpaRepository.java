package com.myfinaimanager.core.financialinstrument.infrastructure.persistence.repository;

import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity.MarketEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link MarketEntity} (id = MIC). Derived queries only — no JPQL, no
 * native SQL. Used only by the {@code financialinstrument} persistence adapters (ADR-003 §16).
 */
public interface MarketJpaRepository extends JpaRepository<MarketEntity, String> {

    List<MarketEntity> findAllByMicIn(Collection<String> mics);
}
