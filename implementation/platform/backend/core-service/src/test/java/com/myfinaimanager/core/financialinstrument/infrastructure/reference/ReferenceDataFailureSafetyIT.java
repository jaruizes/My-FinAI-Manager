package com.myfinaimanager.core.financialinstrument.infrastructure.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.financialinstrument.business.ImportReferenceDataService;
import com.myfinaimanager.core.financialinstrument.business.normalization.YahooSymbolNormalizer;
import com.myfinaimanager.core.financialinstrument.domain.exceptions.ReferenceDataImportException;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import com.myfinaimanager.core.financialinstrument.infrastructure.config.ReferenceDataProperties;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv.CsvReferenceFileReader;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.instrument.YahooCsvInstrumentSource;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.market.CsvMarketSource;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A hard failure mid-import rolls the whole run back — the previously valid catalog is untouched
 * (VC-012; enabler §22). Loads the good fixture, then runs an import over a corrupt CSV (one
 * unparseable line) and asserts the row count is unchanged and the failure is explicit.
 */
@SpringBootTest
class ReferenceDataFailureSafetyIT extends PostgresContainerSupport {

    private static final String MARKETS = "classpath:reference-data/markets.sample.csv";
    private static final String MAPPING = "classpath:reference-data/yahoo-exchange-to-mic-mapping.csv";
    private static final String OVERRIDES = "classpath:reference-data/yahoo-exchange-suffix-overrides.csv";
    private static final String GOOD = "classpath:reference-data/instruments.sample.csv";
    private static final String CORRUPT = "classpath:reference-data/instruments.corrupt.csv";

    @Autowired
    private CsvReferenceFileReader reader;
    @Autowired
    private YahooSymbolNormalizer normalizer;
    @Autowired
    private ReferenceCatalogWriter writer;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM financial_instrument").update();
        jdbc.sql("DELETE FROM market").update();
    }

    private ImportReferenceDataService importer(String instrumentsFile) {
        var props = new ReferenceDataProperties(false, MARKETS, instrumentsFile, MAPPING, OVERRIDES);
        return new ImportReferenceDataService(
                new CsvMarketSource(reader, props),
                new YahooCsvInstrumentSource(reader, props),
                normalizer, writer, txManager, clock);
    }

    @Test
    void a_corrupt_source_rolls_the_run_back_and_leaves_the_prior_catalog_intact() {
        importer(GOOD).run("YAHOO_CSV");
        long instrumentsBefore = count("financial_instrument");
        long marketsBefore = count("market");
        assertThat(instrumentsBefore).isEqualTo(8L);

        assertThatThrownBy(() -> importer(CORRUPT).run("YAHOO_CSV"))
                .isInstanceOf(ReferenceDataImportException.class)
                .hasMessageContaining("parse");

        assertThat(count("financial_instrument")).isEqualTo(instrumentsBefore);
        assertThat(count("market")).isEqualTo(marketsBefore);
    }

    @Test
    void a_missing_source_file_is_a_hard_failure_before_anything_is_written() {
        assertThatThrownBy(() -> importer("classpath:reference-data/does-not-exist.csv").run("YAHOO_CSV"))
                .isInstanceOf(ReferenceDataImportException.class)
                .hasMessageContaining("not found");

        assertThat(count("financial_instrument")).isZero();
        assertThat(count("market")).isZero();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
