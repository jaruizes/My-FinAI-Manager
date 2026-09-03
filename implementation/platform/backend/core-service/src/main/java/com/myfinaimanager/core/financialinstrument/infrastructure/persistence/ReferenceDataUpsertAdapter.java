package com.myfinaimanager.core.financialinstrument.infrastructure.persistence;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity.FinancialInstrumentEntity;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity.MarketEntity;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.mapper.ReferenceDataPersistenceMapper;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.repository.FinancialInstrumentJpaRepository;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.repository.MarketJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent upsert of reference data — implements {@code domain.ports.ReferenceCatalogWriter}.
 * Keyed on natural identity ({@code mic}; {@code (ticker, market_mic)}): a second call with the
 * same identity updates in place and creates no new row (VC-011). Joins the caller's transaction
 * ({@code REQUIRED}) so a rollback in the import run undoes every upsert (VC-012). Never deletes or
 * deactivates an existing row (EN004 §15).
 */
@Repository
public class ReferenceDataUpsertAdapter implements ReferenceCatalogWriter {

    private final MarketJpaRepository markets;
    private final FinancialInstrumentJpaRepository instruments;
    private final ReferenceDataPersistenceMapper mapper;

    public ReferenceDataUpsertAdapter(MarketJpaRepository markets,
                                      FinancialInstrumentJpaRepository instruments,
                                      ReferenceDataPersistenceMapper mapper) {
        this.markets = markets;
        this.instruments = instruments;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public UpsertResult upsertMarket(Market market) {
        MarketEntity incoming = mapper.toEntity(market);
        return markets.findById(incoming.getMic())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return UpsertResult.UPDATED;
                })
                .orElseGet(() -> {
                    markets.save(incoming);
                    return UpsertResult.INSERTED;
                });
    }

    @Override
    @Transactional
    public UpsertResult upsertListing(FinancialInstrumentListing listing) {
        FinancialInstrumentEntity incoming = mapper.toEntity(listing);
        return instruments.findByTickerIgnoreCaseAndMarketMic(incoming.getTicker(), incoming.getMarketMic())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return UpsertResult.UPDATED;
                })
                .orElseGet(() -> {
                    instruments.save(incoming);
                    return UpsertResult.INSERTED;
                });
    }
}
