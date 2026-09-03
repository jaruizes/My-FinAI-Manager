package com.myfinaimanager.core.financialinstrument.infrastructure.api.rest.mapper;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Isin;
import com.myfinaimanager.core.financialinstrument.infrastructure.api.rest.dto.FinancialInstrumentResponse;
import org.springframework.stereotype.Component;

/**
 * Maps a domain {@link FinancialInstrumentListing} to the REST {@link FinancialInstrumentResponse}.
 * Emits only business fields — the raw provider symbol, operating MIC, instrument type, external
 * reference and provenance are dropped here (spec FR-028; VC-010).
 */
@Component
public class FinancialInstrumentResponseMapper {

    public FinancialInstrumentResponse toResponse(FinancialInstrumentListing l) {
        return new FinancialInstrumentResponse(
                l.id().value().toString(),
                l.name(),
                l.ticker().value(),
                l.market().value(),
                l.currency().name(),
                l.active(),
                l.isin().map(Isin::value).orElse(null));
    }
}
