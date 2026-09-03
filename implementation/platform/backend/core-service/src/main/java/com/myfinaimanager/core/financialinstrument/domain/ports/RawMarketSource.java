package com.myfinaimanager.core.financialinstrument.domain.ports;

import com.myfinaimanager.core.financialinstrument.domain.model.NewMarket;
import java.util.List;

/**
 * Outbound port — reads raw Market rows from a source (a curated ISO 10383 – compatible CSV, for
 * the first version). Implemented by an infrastructure adapter that does file I/O only; parsing
 * into the domain {@code Market} is the business/domain's job.
 */
public interface RawMarketSource {

    List<NewMarket> readMarkets();
}
