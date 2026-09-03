package com.myfinaimanager.core.financialinstrument.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.business.ImportReferenceDataService;
import com.myfinaimanager.core.financialinstrument.business.normalization.YahooSymbolNormalizer;
import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.ImportReport;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import com.myfinaimanager.core.financialinstrument.infrastructure.config.ReferenceDataProperties;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.csv.CsvReferenceFileReader;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.instrument.YahooCsvInstrumentSource;
import com.myfinaimanager.core.financialinstrument.infrastructure.reference.market.CsvMarketSource;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The upsert writer is idempotent and never delists (VC-011; enabler §15, §21).
 *
 * <ul>
 *   <li>Running the full import twice inserts nothing the second time — every row is an
 *       {@code UPDATE}; row counts and the deterministic {@link
 *       com.myfinaimanager.core.financialinstrument.domain.model.ListingId} are stable.</li>
 *   <li>A later import that <em>omits</em> previously-seen rows leaves them present and unchanged —
 *       omission is not delisting.</li>
 * </ul>
 */
@SpringBootTest
class ReferenceDataUpsertAdapterIT extends PostgresContainerSupport {

    private static final String MARKETS = "classpath:reference-data/markets.sample.csv";
    private static final String MAPPING = "classpath:reference-data/yahoo-exchange-to-mic-mapping.csv";
    private static final String OVERRIDES = "classpath:reference-data/yahoo-exchange-suffix-overrides.csv";
    private static final String FULL = "classpath:reference-data/instruments.sample.csv";
    private static final String SUBSET = "classpath:reference-data/instruments.sample.subset.csv";

    @Autowired
    private CsvReferenceFileReader reader;
    @Autowired
    private YahooSymbolNormalizer normalizer;
    @Autowired
    private ReferenceCatalogWriter writer;
    @Autowired
    private FinancialInstrumentCatalog catalog;
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
    void a_second_full_import_inserts_nothing_and_keeps_ids_stable() {
        ImportReport first = importer(FULL).run("YAHOO_CSV");
        assertThat(first.counters().imported()).isEqualTo(8);

        UUID sanIdBefore = catalog.search("SAN").get(0).id().value();
        long marketsBefore = count("market");
        long instrumentsBefore = count("financial_instrument");

        ImportReport second = importer(FULL).run("YAHOO_CSV");

        assertThat(second.counters().imported()).isZero();
        assertThat(second.counters().updated()).isEqualTo(8);
        assertThat(count("market")).isEqualTo(marketsBefore);
        assertThat(count("financial_instrument")).isEqualTo(instrumentsBefore);
        assertThat(catalog.search("SAN").get(0).id().value()).isEqualTo(sanIdBefore);
    }

    @Test
    void an_import_that_omits_rows_does_not_delist_them() {
        importer(FULL).run("YAHOO_CSV");
        long instrumentsBefore = count("financial_instrument");

        ImportReport partial = importer(SUBSET).run("YAHOO_CSV");

        assertThat(partial.counters().imported()).isZero();
        assertThat(partial.counters().updated()).isEqualTo(6);
        assertThat(count("financial_instrument")).isEqualTo(instrumentsBefore);

        FinancialInstrumentListing aapl = catalog.search("AAPL").get(0);
        assertThat(aapl.active()).isTrue();
        assertThat(aapl.market().value()).isEqualTo("XNAS");

        FinancialInstrumentListing msft = catalog.search("MSFT").get(0);
        assertThat(msft.active()).isTrue();
    }

    @Test
    void provenance_is_written_on_insert_and_refreshed_on_update() {
        importer(FULL).run("YAHOO_CSV");
        assertThat(provenanceWritten()).isEqualTo(8L);

        importer(FULL).run("YAHOO_CSV");
        assertThat(provenanceWritten()).isEqualTo(8L);
        String source = jdbc.sql("SELECT DISTINCT source FROM financial_instrument")
                .query(String.class).single();
        assertThat(source).isEqualTo("YAHOO_CSV");
    }

    private long provenanceWritten() {
        return jdbc.sql("SELECT count(*) FROM financial_instrument "
                + "WHERE source IS NOT NULL AND last_imported_at IS NOT NULL").query(Long.class).single();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
