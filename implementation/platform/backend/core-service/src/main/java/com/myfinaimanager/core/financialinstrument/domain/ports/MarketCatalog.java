package com.myfinaimanager.core.financialinstrument.domain.ports;

import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port — read access to the local Market catalog. Used by the importer (to detect
 * {@code MIC_UNRESOLVED}) and to enrich search results if needed. See
 * {@code contracts/catalog-ports.md} §2.
 */
public interface MarketCatalog {

    Optional<Market> findByMic(Mic mic);

    /** Bulk lookup — returns only the MICs that are present as Market rows. */
    Map<Mic, Market> findAllByMic(Set<Mic> mics);
}
