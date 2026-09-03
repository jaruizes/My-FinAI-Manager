package com.myfinaimanager.core.financialinstrument.infrastructure.reference.instrument;

import com.myfinaimanager.core.financialinstrument.domain.model.RawInstrumentRow;
import com.myfinaimanager.core.financialinstrument.domain.ports.RawInstrumentSource;
import com.myfinaimanager.core.financialinstrument.infrastructure.config.ReferenceDataProperties;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv.CsvReferenceFileReader;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads the curated Yahoo-shape instrument CSV ({@code Ticker,Name,Exchange}) into raw
 * {@link RawInstrumentRow} rows. No normalization here — the business importer runs the
 * mapping-driven {@code YahooSymbolNormalizer} over {@code Ticker + Exchange} (EN004 §9A, §18).
 * The raw {@code Ticker} value is the provider symbol and is retained verbatim.
 */
@Component
public class YahooCsvInstrumentSource implements RawInstrumentSource {

    private final CsvReferenceFileReader reader;
    private final ReferenceDataProperties props;

    public YahooCsvInstrumentSource(CsvReferenceFileReader reader, ReferenceDataProperties props) {
        this.reader = reader;
        this.props = props;
    }

    @Override
    public List<RawInstrumentRow> readInstruments() {
        return reader.read(props.instrumentsFile()).stream()
                .map(YahooCsvInstrumentSource::toRow)
                .toList();
    }

    private static RawInstrumentRow toRow(Map<String, String> row) {
        return new RawInstrumentRow(
                firstNonNull(row, "Ticker", "ticker", "Symbol", "symbol"),
                firstNonNull(row, "Name", "name", "Company", "company"),
                firstNonNull(row, "Exchange", "exchange"));
    }

    private static String firstNonNull(Map<String, String> row, String... headers) {
        for (String h : headers) {
            String v = row.get(h);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
