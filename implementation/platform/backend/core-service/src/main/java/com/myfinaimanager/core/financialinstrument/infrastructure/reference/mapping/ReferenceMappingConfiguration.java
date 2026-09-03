package com.myfinaimanager.core.financialinstrument.infrastructure.reference.mapping;

import com.myfinaimanager.core.financialinstrument.business.normalization.ExchangeMicTable;
import com.myfinaimanager.core.financialinstrument.business.normalization.ExchangeRule;
import com.myfinaimanager.core.financialinstrument.business.normalization.SuffixOverride;
import com.myfinaimanager.core.financialinstrument.business.normalization.SuffixOverrideTable;
import com.myfinaimanager.core.financialinstrument.business.normalization.YahooSymbolNormalizer;
import com.myfinaimanager.core.financialinstrument.infrastructure.config.ReferenceDataProperties;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv.CsvReferenceFileReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the two Yahoo mapping CSVs (infrastructure reference configuration — decision doc §"Mapping
 * Governance") into the plain-data {@code business.normalization} tables, and wires the pure
 * {@link YahooSymbolNormalizer} over them. The CSV format never crosses into {@code business}.
 */
@Configuration
public class ReferenceMappingConfiguration {

    @Bean
    public ExchangeMicTable exchangeMicTable(CsvReferenceFileReader reader, ReferenceDataProperties props) {
        List<Map<String, String>> rows = reader.read(props.exchangeMicMappingFile());
        Map<String, ExchangeRule> byCode = new LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            String code = value(r, "source_exchange_code");
            if (code.isEmpty()) {
                continue;
            }
            byCode.put(code.toUpperCase(java.util.Locale.ROOT), new ExchangeRule(
                    code.toUpperCase(java.util.Locale.ROOT),
                    value(r, "expected_yahoo_suffix"),
                    value(r, "canonical_mic"),
                    value(r, "currency"),
                    "true".equalsIgnoreCase(value(r, "supported_for_fd002"))));
        }
        return new ExchangeMicTable(byCode);
    }

    @Bean
    public SuffixOverrideTable suffixOverrideTable(CsvReferenceFileReader reader, ReferenceDataProperties props) {
        List<Map<String, String>> rows = reader.read(props.suffixOverrideFile());
        Map<String, SuffixOverride> byKey = new LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            String code = value(r, "source_exchange_code");
            String suffix = value(r, "yahoo_suffix");
            if (code.isEmpty() || suffix.isEmpty()) {
                continue;
            }
            byKey.put(SuffixOverrideTable.key(code, suffix),
                    new SuffixOverride(value(r, "canonical_mic"), value(r, "currency")));
        }
        return new SuffixOverrideTable(byKey);
    }

    @Bean
    public YahooSymbolNormalizer yahooSymbolNormalizer(ExchangeMicTable exchangeMicTable,
                                                      SuffixOverrideTable suffixOverrideTable) {
        return new YahooSymbolNormalizer(exchangeMicTable, suffixOverrideTable);
    }

    private static String value(Map<String, String> row, String header) {
        String v = row.get(header);
        return v == null ? "" : v.strip();
    }
}
