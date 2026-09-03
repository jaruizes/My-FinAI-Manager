package com.myfinaimanager.core.portfolio.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.NewListing;
import com.myfinaimanager.core.financialinstrument.domain.model.NewMarket;
import com.myfinaimanager.core.financialinstrument.domain.model.Provenance;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.domain.ports.InstrumentCatalog;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link CatalogInstrumentCatalogAdapter} against real PostgreSQL — the {@code portfolio → }
 * {@code financialinstrument} seam (AR-062). Proves the FD002 FR-011 rule: a position is selectable
 * only when the full {@code ticker + market + currency} is one active catalogued listing.
 */
@SpringBootTest
class CatalogInstrumentCatalogAdapterIT extends PostgresContainerSupport {

    private static final Provenance PROV = Provenance.of("TEST", null, Instant.parse("2026-09-03T00:00:00Z"));

    @Autowired
    private ReferenceCatalogWriter writer;
    @Autowired
    private InstrumentCatalog catalog;
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
        listing("Banco Santander, S.A.", "SAN", "XMAD", "EUR", true);
        listing("Santander Consumer USA", "SC", "XNAS", "USD", false);   // inactive
        // A non-EUR/USD listing cannot be persisted at all — SupportedCurrency is {EUR, USD} — so
        // there is nothing to seed for the "unsupported currency" case; it is impossible by
        // construction, which is a stronger guarantee than a runtime filter.
    }

    @Test
    void a_full_ticker_market_currency_match_is_selectable_case_insensitively() {
        assertThat(isSelectable("AAPL", "XNAS", "USD")).isTrue();
        assertThat(isSelectable("aapl", "XNAS", "USD")).isTrue();
        assertThat(isSelectable("SAN", "XMAD", "EUR")).isTrue();   // EUR path
    }

    @Test
    void a_currency_that_differs_from_the_listing_is_not_selectable() { // analyze A1
        assertThat(isSelectable("AAPL", "XNAS", "EUR")).isFalse();
        assertThat(isSelectable("SAN", "XMAD", "USD")).isFalse();
    }

    @Test
    void a_wrong_market_inactive_or_unknown_is_not_selectable() {
        assertThat(isSelectable("AAPL", "XMAD", "USD")).isFalse();   // wrong market
        assertThat(isSelectable("SC", "XNAS", "USD")).isFalse();     // inactive
        assertThat(isSelectable("NOSUCH", "XNAS", "USD")).isFalse();
    }

    private boolean isSelectable(String ticker, String market, String currency) {
        return catalog.isSelectable(
                new Ticker(ticker),
                new com.myfinaimanager.core.portfolio.domain.model.Market(market),
                new Currency(currency));
    }

    private Market market(String mic, String name) {
        return Market.fromRaw(new NewMarket(mic, name, null, null, "true"), PROV);
    }

    private void listing(String name, String ticker, String mic, String ccy, boolean active) {
        writer.upsertListing(FinancialInstrumentListing.fromRaw(
                new NewListing(name, ticker, mic, ccy, null, null, null, null, String.valueOf(active)), PROV));
    }
}
