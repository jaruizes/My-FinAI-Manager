package com.myfinaimanager.core.financialinstrument.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.NewListing;
import com.myfinaimanager.core.financialinstrument.domain.model.NewMarket;
import com.myfinaimanager.core.financialinstrument.domain.model.Provenance;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link FinancialInstrumentCatalogAdapter} search against real PostgreSQL — by ticker
 * (case-insensitive), by name (substring, case-insensitive), the FD002 default filter
 * (active + EUR/USD), ordering (exact-ticker first), and empty result when nothing matches
 * (VC-006 – VC-009).
 */
@SpringBootTest
class FinancialInstrumentCatalogAdapterIT extends PostgresContainerSupport {

    private static final Provenance PROV = Provenance.of("TEST", null, Instant.parse("2026-09-03T00:00:00Z"));

    @Autowired
    private ReferenceCatalogWriter writer;
    @Autowired
    private FinancialInstrumentCatalog catalog;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("DELETE FROM financial_instrument").update();
        jdbc.sql("DELETE FROM market").update();
        writer.upsertMarket(market("XNAS", "Nasdaq"));
        writer.upsertMarket(market("XMAD", "Bolsa de Madrid"));
        writer.upsertMarket(market("XLON", "London Stock Exchange"));
        listing("Apple Inc.", "AAPL", "XNAS", "USD", true);
        listing("Applied Materials, Inc.", "AMAT", "XNAS", "USD", true);
        listing("Banco Santander, S.A.", "SAN", "XMAD", "EUR", true);
        listing("Santander Consumer USA", "SC", "XNAS", "USD", false);      // inactive -> excluded
        listing("HSBC Holdings plc", "HSBA", "XLON", "USD", true);          // XLON but priced USD -> allowed by currency
    }

    @Test
    void search_by_ticker_is_case_insensitive() {
        assertThat(catalog.search("AAPL")).extracting(l -> l.ticker().value()).containsExactly("AAPL");
        assertThat(catalog.search("aapl")).extracting(l -> l.ticker().value()).containsExactly("AAPL");
    }

    @Test
    void search_by_name_is_a_case_insensitive_substring() {
        assertThat(catalog.search("santan")).extracting(l -> l.ticker().value()).contains("SAN");
        assertThat(catalog.search("APPLIED")).extracting(l -> l.ticker().value()).containsExactly("AMAT");
    }

    @Test
    void inactive_listings_are_never_returned() {
        assertThat(catalog.search("santander")).extracting(l -> l.ticker().value())
                .contains("SAN").doesNotContain("SC");
        assertThat(catalog.search("SC")).isEmpty();
    }

    @Test
    void exact_ticker_matches_are_ranked_before_name_matches() {
        // "SAN" is an exact ticker AND a substring of "Santander Consumer USA" (which is inactive)
        // and "Banco Santander". Exact-ticker SAN must come first.
        List<FinancialInstrumentListing> hits = catalog.search("SAN");
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).ticker().value()).isEqualTo("SAN");
    }

    @Test
    void an_unmatched_query_returns_an_empty_list() {
        assertThat(catalog.search("NOSUCHINSTRUMENT")).isEmpty();
    }

    // ---- findSelectable (FD002) ----------------------------------------------------------

    @Test
    void find_selectable_matches_ticker_case_insensitively_on_the_exact_mic() {
        assertThat(catalog.findSelectable("AAPL", "XNAS")).hasValueSatisfying(l -> {
            assertThat(l.ticker().value()).isEqualTo("AAPL");
            assertThat(l.market().value()).isEqualTo("XNAS");
            assertThat(l.currency().name()).isEqualTo("USD");
        });
        assertThat(catalog.findSelectable("aapl", "XNAS")).isPresent();
        assertThat(catalog.findSelectable("SAN", "XMAD")).hasValueSatisfying(
                l -> assertThat(l.currency().name()).isEqualTo("EUR"));
    }

    @Test
    void find_selectable_is_empty_for_wrong_market_inactive_or_unknown() {
        assertThat(catalog.findSelectable("AAPL", "XMAD")).isEmpty();   // right ticker, wrong market
        assertThat(catalog.findSelectable("SC", "XNAS")).isEmpty();     // seeded but inactive
        assertThat(catalog.findSelectable("NOSUCH", "XNAS")).isEmpty();
    }

    private Market market(String mic, String name) {
        return Market.fromRaw(new NewMarket(mic, name, null, null, "true"), PROV);
    }

    private void listing(String name, String ticker, String mic, String ccy, boolean active) {
        writer.upsertListing(FinancialInstrumentListing.fromRaw(
                new NewListing(name, ticker, mic, ccy, null, null, null, null, String.valueOf(active)), PROV));
    }
}
