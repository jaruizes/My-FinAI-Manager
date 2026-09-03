package com.myfinaimanager.core.financialinstrument.infrastructure.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.business.ImportReferenceDataService;
import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.ImportReport;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end reference-data import over the committed test fixtures, on real PostgreSQL — the
 * catalog rows and the {@link ImportReport} counters match exactly (VC-002, VC-004, VC-005, VC-010,
 * VC-011). Proves the mapping-driven pipeline (source → normalize → upsert) and the
 * skip/quarantine accounting.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.reference-data.markets-file=classpath:reference-data/markets.sample.csv",
        "app.reference-data.instruments-file=classpath:reference-data/instruments.sample.csv"
})
class YahooCsvInstrumentSourceIT extends PostgresContainerSupport {

    @Autowired
    private ImportReferenceDataService importer;
    @Autowired
    private FinancialInstrumentCatalog catalog;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM financial_instrument").update();
        jdbc.sql("DELETE FROM market").update();
    }

    @Test
    void imports_the_supported_rows_and_reports_exact_counters() {
        ImportReport report = importer.run("YAHOO_CSV");

        assertThat(report.counters().processed()).isEqualTo(13);
        assertThat(report.counters().imported()).isEqualTo(8);
        assertThat(report.counters().updated()).isZero();
        assertThat(report.counters().skippedUnsupportedCurrency()).isEqualTo(1);   // VOD.L / LSE (GBP)
        assertThat(report.counters().skippedUnsupportedMarket()).isEqualTo(1);     // BAR / EUX (not supported)
        assertThat(report.counters().quarantinedAmbiguous()).isEqualTo(1);         // XYZ.NX / ENX
        assertThat(report.counters().quarantinedInvalid()).isEqualTo(2);           // FOO.XX/MCE, .MC/MCE
        assertThat(report.rejections()).hasSize(5);

        assertThat(count("market")).isEqualTo(8);
        assertThat(count("financial_instrument")).isEqualTo(8);
    }

    @Test
    void persists_provider_symbol_and_canonical_ticker_and_the_normalized_market() {
        importer.run("YAHOO_CSV");

        FinancialInstrumentListing san = catalog.search("SAN").get(0);
        assertThat(san.ticker().value()).isEqualTo("SAN");
        assertThat(san.market().value()).isEqualTo("XMAD");
        assertThat(san.currency().name()).isEqualTo("EUR");
        assertThat(san.providerSymbol()).hasValue("SAN.MC");
        assertThat(san.name()).isEqualTo("Banco Santander, S.A.");

        FinancialInstrumentListing aapl = catalog.search("AAPL").get(0);
        assertThat(aapl.market().value()).isEqualTo("XNAS");
        assertThat(aapl.currency().name()).isEqualTo("USD");
        assertThat(aapl.providerSymbol()).hasValue("AAPL");

        FinancialInstrumentListing ads = catalog.search("ADS").get(0);
        assertThat(ads.market().value()).isEqualTo("XETR");   // (FRA, DE) suffix override
        assertThat(ads.providerSymbol()).hasValue("ADS.DE");

        FinancialInstrumentListing spy = catalog.search("SPY").get(0);
        assertThat(spy.instrumentType()).hasValueSatisfying(t -> assertThat(t.name()).isEqualTo("ETF"));
        assertThat(aapl.instrumentType()).isEmpty();   // "Apple Inc." — no conclusive type keyword
    }

    @Test
    void running_the_import_twice_creates_no_duplicates() {
        importer.run("YAHOO_CSV");
        ImportReport second = importer.run("YAHOO_CSV");

        assertThat(second.counters().imported()).isZero();
        assertThat(second.counters().updated()).isEqualTo(8);
        assertThat(count("market")).isEqualTo(8);
        assertThat(count("financial_instrument")).isEqualTo(8);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
