package com.myfinaimanager.core.financialinstrument.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.InstrumentType;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import com.myfinaimanager.core.financialinstrument.domain.model.NewListing;
import com.myfinaimanager.core.financialinstrument.domain.model.NewMarket;
import com.myfinaimanager.core.financialinstrument.domain.model.Provenance;
import com.myfinaimanager.core.financialinstrument.domain.model.SupportedCurrency;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import com.myfinaimanager.core.financialinstrument.domain.ports.MarketCatalog;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@code Market} and {@code FinancialInstrumentListing} persist and read back exactly through the
 * catalog / writer adapters, on real PostgreSQL (VC-001, VC-003, VC-013). Also covers FR-008 — one
 * economic instrument with two listings on different markets.
 */
@SpringBootTest
class ReferenceDataPersistenceRoundTripIT extends PostgresContainerSupport {

    private static final Provenance PROV = Provenance.of("YAHOO_CSV", "SAN.MC", Instant.parse("2026-09-03T10:00:00Z"));

    @Autowired
    private ReferenceCatalogWriter writer;
    @Autowired
    private FinancialInstrumentCatalog catalog;
    @Autowired
    private MarketCatalog markets;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM financial_instrument").update();
        jdbc.sql("DELETE FROM market").update();
    }

    @Test
    void a_market_round_trips_every_field() {
        writer.upsertMarket(Market.fromRaw(new NewMarket("XMAD", "Bolsa de Madrid", "ES", "BMEX", "true"), PROV));

        Market loaded = markets.findByMic(new Mic("XMAD")).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Bolsa de Madrid");
        assertThat(loaded.country()).hasValue("ES");
        assertThat(loaded.operatingMic()).hasValueSatisfying(op -> assertThat(op.value()).isEqualTo("BMEX"));
        assertThat(loaded.active()).isTrue();
        assertThat(loaded.provenance().source()).isEqualTo("YAHOO_CSV");
    }

    @Test
    void a_listing_round_trips_every_field_including_optionals() {
        writer.upsertMarket(Market.fromRaw(new NewMarket("XMAD", "Madrid", "ES", null, "true"), PROV));
        writer.upsertListing(FinancialInstrumentListing.fromRaw(new NewListing(
                "Banco Santander, S.A.", "SAN", "XMAD", "EUR",
                "ES0113900J37", "OpenFIGI:BBG000BCKWT9", "Common Stock", "SAN.MC", "true"), PROV));

        List<FinancialInstrumentListing> hits = catalog.search("SAN");
        assertThat(hits).hasSize(1);
        FinancialInstrumentListing l = hits.get(0);
        assertThat(l.name()).isEqualTo("Banco Santander, S.A.");
        assertThat(l.ticker().value()).isEqualTo("SAN");
        assertThat(l.market().value()).isEqualTo("XMAD");
        assertThat(l.currency()).isEqualTo(SupportedCurrency.EUR);
        assertThat(l.isin()).hasValueSatisfying(i -> assertThat(i.value()).isEqualTo("ES0113900J37"));
        assertThat(l.externalReference()).hasValue("OpenFIGI:BBG000BCKWT9");
        assertThat(l.instrumentType()).hasValue(InstrumentType.EQUITY);
        assertThat(l.providerSymbol()).hasValue("SAN.MC");
        assertThat(l.active()).isTrue();
    }

    @Test
    void one_instrument_can_have_two_listings_on_different_markets() {
        writer.upsertMarket(Market.fromRaw(new NewMarket("XNYS", "NYSE", "US", null, "true"), PROV));
        writer.upsertMarket(Market.fromRaw(new NewMarket("XETR", "Xetra", "DE", null, "true"), PROV));
        writer.upsertListing(FinancialInstrumentListing.fromRaw(new NewListing(
                "Linde plc", "LIN", "XNYS", "USD", null, null, "Common Stock", "LIN", "true"), PROV));
        writer.upsertListing(FinancialInstrumentListing.fromRaw(new NewListing(
                "Linde plc", "LIN", "XETR", "EUR", null, null, "Common Stock", "LIN.DE", "true"), PROV));

        List<FinancialInstrumentListing> hits = catalog.search("LIN");
        assertThat(hits).hasSize(2);
        assertThat(hits).extracting(l -> l.market().value()).containsExactlyInAnyOrder("XNYS", "XETR");
        assertThat(hits).extracting(l -> l.currency().name()).containsExactlyInAnyOrder("USD", "EUR");
        assertThat(hits).extracting(l -> l.id().value()).doesNotHaveDuplicates();
    }

    @Test
    void find_all_by_mic_returns_only_present_markets() {
        writer.upsertMarket(Market.fromRaw(new NewMarket("XMAD", "Madrid", "ES", null, "true"), PROV));
        var found = markets.findAllByMic(Set.of(new Mic("XMAD"), new Mic("XNAS")));
        assertThat(found).containsOnlyKeys(new Mic("XMAD"));
    }
}
