package com.myfinaimanager.core.financialinstrument.infrastructure.reference.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.business.normalization.ExchangeMicTable;
import com.myfinaimanager.core.financialinstrument.business.normalization.ExchangeRule;
import com.myfinaimanager.core.financialinstrument.business.normalization.SuffixOverride;
import com.myfinaimanager.core.financialinstrument.business.normalization.SuffixOverrideTable;
import com.myfinaimanager.core.financialinstrument.infrastructure.config.ReferenceDataProperties;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv.CsvReferenceFileReader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Loads the committed {@code yahoo-exchange-*.csv} mapping files and asserts the key rows the
 * normalizer relies on (contracts/reference-mapping.md §1–§2). No Spring context — direct classpath
 * reads.
 */
class MappingTablesTest {

    private static final ReferenceDataProperties PROPS = new ReferenceDataProperties(
            false, "n/a", "n/a",
            "classpath:reference-data/yahoo-exchange-to-mic-mapping.csv",
            "classpath:reference-data/yahoo-exchange-suffix-overrides.csv");

    private final ReferenceMappingConfiguration config = new ReferenceMappingConfiguration();
    private final CsvReferenceFileReader reader = new CsvReferenceFileReader(new DefaultResourceLoader());

    @Test
    void exchange_mic_table_has_the_expected_key_rows() {
        ExchangeMicTable table = config.exchangeMicTable(reader, PROPS);

        ExchangeRule mce = table.ruleFor("MCE").orElseThrow();
        assertThat(mce.expectedYahooSuffix()).isEqualTo("MC");
        assertThat(mce.canonicalMic()).isEqualTo("XMAD");
        assertThat(mce.currency()).isEqualTo("EUR");
        assertThat(mce.supportedForFd002()).isTrue();

        ExchangeRule nms = table.ruleFor("NMS").orElseThrow();
        assertThat(nms.hasSuffix()).isFalse();
        assertThat(nms.canonicalMic()).isEqualTo("XNAS");
        assertThat(nms.currency()).isEqualTo("USD");

        ExchangeRule lse = table.ruleFor("LSE").orElseThrow();
        assertThat(lse.currency()).isEqualTo("GBP");
        assertThat(lse.supportedForFd002()).isFalse();

        assertThat(table.ruleFor("ENX").orElseThrow().hasMic()).isFalse();
        assertThat(table.ruleFor("ZZZ")).isEmpty();
    }

    @Test
    void suffix_override_table_has_the_expected_pairs() {
        SuffixOverrideTable table = config.suffixOverrideTable(reader, PROPS);

        SuffixOverride fraDe = table.overrideFor("FRA", "DE").orElseThrow();
        assertThat(fraDe.canonicalMic()).isEqualTo("XETR");
        assertThat(fraDe.currency()).isEqualTo("EUR");

        assertThat(table.overrideFor("ENX", "PA").orElseThrow().canonicalMic()).isEqualTo("XPAR");

        SuffixOverride enxNx = table.overrideFor("ENX", "NX").orElseThrow();
        assertThat(enxNx.canonicalMic()).isEqualTo("XEUR");
        assertThat(enxNx.hasCurrency()).isFalse();   // currency cannot be inferred -> quarantine

        assertThat(table.overrideFor("FRA", "ZZ")).isEmpty();
    }
}
