package com.myfinaimanager.core.financialinstrument.infrastructure.reference.market;

import com.myfinaimanager.core.financialinstrument.domain.model.NewMarket;
import com.myfinaimanager.core.financialinstrument.domain.ports.RawMarketSource;
import com.myfinaimanager.core.financialinstrument.infrastructure.config.ReferenceDataProperties;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv.CsvReferenceFileReader;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads the curated ISO 10383 – compatible Markets CSV ({@code mic,name,country_iso2,operating_mic}
 * [,active]) into raw {@link NewMarket} carriers. Validation/normalization is the domain's job
 * ({@code Market.fromRaw}). Source column names never propagate further (EN004 §8).
 */
@Component
public class CsvMarketSource implements RawMarketSource {

    private final CsvReferenceFileReader reader;
    private final ReferenceDataProperties props;

    public CsvMarketSource(CsvReferenceFileReader reader, ReferenceDataProperties props) {
        this.reader = reader;
        this.props = props;
    }

    @Override
    public List<NewMarket> readMarkets() {
        return reader.read(props.marketsFile()).stream()
                .map(CsvMarketSource::toNewMarket)
                .toList();
    }

    private static NewMarket toNewMarket(Map<String, String> row) {
        return new NewMarket(
                row.get("mic"),
                row.get("name"),
                row.get("country_iso2"),
                row.get("operating_mic"),
                row.get("active"));
    }
}
