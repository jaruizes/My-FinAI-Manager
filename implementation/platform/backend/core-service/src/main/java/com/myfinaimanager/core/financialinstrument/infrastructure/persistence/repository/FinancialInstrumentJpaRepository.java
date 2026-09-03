package com.myfinaimanager.core.financialinstrument.infrastructure.persistence.repository;

import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity.FinancialInstrumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link FinancialInstrumentEntity}. The upsert lookup is a derived
 * method; {@link #search} is a single JPQL query (no native SQL — ADR-003 / AR-060). Nothing
 * outside {@code infrastructure.persistence} touches this interface.
 */
public interface FinancialInstrumentJpaRepository extends JpaRepository<FinancialInstrumentEntity, UUID> {

    Optional<FinancialInstrumentEntity> findByTickerIgnoreCaseAndMarketMic(String ticker, String marketMic);

    /**
     * FD002 catalog search (research.md D5): active + EUR/USD only; exact ticker (case-insensitive)
     * OR name-contains (case-insensitive); exact-ticker matches ranked first, then by ticker.
     */
    @Query("""
            select fi from FinancialInstrumentEntity fi
            where fi.active = true
              and fi.currency in ('EUR', 'USD')
              and ( upper(fi.ticker) = upper(:q)
                    or lower(fi.name) like lower(concat('%', :q, '%')) )
            order by case when upper(fi.ticker) = upper(:q) then 0 else 1 end, fi.ticker asc
            """)
    List<FinancialInstrumentEntity> search(@Param("q") String query);
}
