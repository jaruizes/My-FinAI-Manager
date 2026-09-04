package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioValuationRepository;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link PortfolioValuationPersistenceAdapter} against a real PostgreSQL (Testcontainers). Proves
 * latest-only semantics (FR-021, SC-002), the exact NUMERIC round-trip (SC-001), the unvalued-row
 * shape (FR-017), and that {@code portfolio} / {@code position} are never written (SC-008).
 */
@SpringBootTest
class PortfolioValuationPersistenceAdapterIT extends PostgresContainerSupport {

    private static final Instant AT = Instant.parse("2026-09-04T12:00:00Z");
    private static final UUID INVESTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private PortfolioValuationRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private PortfolioId portfolioId;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM sector_allocation").update();
        jdbc.sql("DELETE FROM position_valuation").update();
        jdbc.sql("DELETE FROM portfolio_valuation").update();
        jdbc.sql("DELETE FROM position").update();
        jdbc.sql("DELETE FROM portfolio").update();
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO portfolio (id, investor_id, name, status, idempotency_key, created_at) "
                        + "VALUES (:id, :inv, 'Val', 'ACTIVE', :k, now())")
                .param("id", id).param("inv", INVESTOR).param("k", "val-" + id).update();
        portfolioId = PortfolioId.of(id);
    }

    private PortfolioValuation completedSnapshot() {
        PositionValuation aapl = new PositionValuation("AAPL", "XNAS", new BigDecimal("10"), "USD",
                true, Optional.of(new BigDecimal("200")), Optional.of(new BigDecimal("2000")),
                Optional.of(new BigDecimal("1600.00")), Optional.of(new BigDecimal("2000")),
                Optional.of(new BigDecimal("0.761904761905")), "Technology", Optional.of(AT));
        PositionValuation san = PositionValuation.unvalued("SAN", "XMAD", new BigDecimal("100"),
                "EUR", "Unclassified");
        return new PortfolioValuation(portfolioId, ValuationStatus.PARTIAL, AT,
                Optional.of(new BigDecimal("2100.00")), Optional.of(new BigDecimal("2625.00")),
                Optional.of(AT), Optional.of(AT), List.of(aapl, san),
                List.of(new SectorAllocation("Technology", new BigDecimal("1600"),
                        new BigDecimal("0.761904761905"))));
    }

    @Test
    void inserts_the_whole_graph_and_reads_it_back_exactly() {
        repository.upsertLatest(completedSnapshot());

        assertThat(count("portfolio_valuation")).isEqualTo(1);
        assertThat(count("position_valuation")).isEqualTo(2);
        assertThat(count("sector_allocation")).isEqualTo(1);

        PortfolioValuation loaded = repository.findByPortfolioId(portfolioId).orElseThrow();
        assertThat(loaded.totalValueEUR().orElseThrow()).isEqualByComparingTo("2100.00");
        assertThat(loaded.totalValueEUR().orElseThrow().toPlainString()).isEqualTo("2100.00"); // SC-001
        PositionValuation san = loaded.positions().stream()
                .filter(p -> p.ticker().equals("SAN")).findFirst().orElseThrow();
        assertThat(san.valued()).isFalse();
        assertThat(san.valueInEUR()).isEmpty(); // never 0
    }

    @Test
    void re_running_valuation_replaces_the_snapshot_with_no_duplicate_rows() { // FR-021, SC-002
        repository.upsertLatest(completedSnapshot());
        repository.upsertLatest(completedSnapshot());
        repository.upsertLatest(completedSnapshot());

        assertThat(count("portfolio_valuation")).isEqualTo(1);
        assertThat(count("position_valuation")).isEqualTo(2);
        assertThat(count("sector_allocation")).isEqualTo(1);
    }

    @Test
    void deleting_the_parent_cascades_to_children() {
        repository.upsertLatest(completedSnapshot());
        jdbc.sql("DELETE FROM portfolio_valuation WHERE portfolio_id = :p")
                .param("p", portfolioId.value()).update();
        assertThat(count("position_valuation")).isZero();
        assertThat(count("sector_allocation")).isZero();
    }

    @Test
    void valuation_writes_never_touch_the_portfolio_or_position_tables() { // SC-008
        long portfoliosBefore = count("portfolio");
        long positionsBefore = count("position");

        repository.upsertLatest(completedSnapshot());
        repository.upsertLatest(completedSnapshot());
        repository.findByPortfolioId(portfolioId);

        assertThat(count("portfolio")).isEqualTo(portfoliosBefore);
        assertThat(count("position")).isEqualTo(positionsBefore);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
