package com.myfinaimanager.core.financialinstrument.infrastructure.persistence;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import com.myfinaimanager.core.financialinstrument.domain.ports.MarketCatalog;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.mapper.ReferenceDataPersistenceMapper;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.repository.FinancialInstrumentJpaRepository;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.repository.MarketJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA read adapter for the local catalog — implements the {@code domain.ports} read
 * interfaces. The search runs entirely against local PostgreSQL; no external provider is contacted
 * (VC-006). See {@code contracts/catalog-ports.md} §1–§2 for the invariants.
 */
@Repository
public class FinancialInstrumentCatalogAdapter implements FinancialInstrumentCatalog, MarketCatalog {

    private final FinancialInstrumentJpaRepository instruments;
    private final MarketJpaRepository markets;
    private final ReferenceDataPersistenceMapper mapper;

    public FinancialInstrumentCatalogAdapter(FinancialInstrumentJpaRepository instruments,
                                             MarketJpaRepository markets,
                                             ReferenceDataPersistenceMapper mapper) {
        this.instruments = instruments;
        this.markets = markets;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialInstrumentListing> search(String query) {
        return instruments.search(query).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Market> findByMic(Mic mic) {
        return markets.findById(mic.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Mic, Market> findAllByMic(Set<Mic> mics) {
        List<String> codes = mics.stream().map(Mic::value).toList();
        return markets.findAllByMicIn(codes).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toMap(Market::mic, Function.identity()));
    }
}
