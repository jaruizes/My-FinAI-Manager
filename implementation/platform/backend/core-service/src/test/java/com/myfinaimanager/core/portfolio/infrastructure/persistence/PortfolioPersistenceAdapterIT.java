package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.InstrumentRef;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioName;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioStatus;
import com.myfinaimanager.core.portfolio.domain.model.Position;
import com.myfinaimanager.core.portfolio.domain.model.PositionId;
import com.myfinaimanager.core.portfolio.domain.model.Quantity;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.domain.ports.PortfolioRepository;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link PortfolioPersistenceAdapter} (Spring Data JPA) against a real PostgreSQL. Same scenarios
 * and assertions as the previous {@code JdbcPortfolioRepositoryIT} — atomicity, constraints, exact
 * decimal round-trip, idempotency-race resolution — plus a detached-read check (no
 * {@code LazyInitializationException} outside a transaction). Proves the JDBC → JPA swap is
 * behavior-preserving (EN003 / VC-010, VC-013).
 */
@SpringBootTest
class PortfolioPersistenceAdapterIT extends PostgresContainerSupport {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
    private static final InvestorId INVESTOR =
            InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private PortfolioRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM position").update();
        jdbc.sql("DELETE FROM portfolio").update();
    }

    private static Portfolio portfolio(String name, NewPosition... positions) {
        return Portfolio.create(INVESTOR, name, List.of(positions), CLOCK);
    }

    private static Position rebuiltPosition(String ticker, String market, String quantity) {
        return Position.reconstitute(PositionId.newId(),
                new InstrumentRef(new Ticker(ticker), new Market(market)),
                new Quantity(new BigDecimal(quantity)), new Currency("EUR"),
                Optional.empty(), Optional.empty());
    }

    @Test
    void persists_the_whole_aggregate_and_reads_it_back_exactly() {
        Portfolio p = portfolio("Dividend",
                new NewPosition("ASML", "XAMS", "3.250", "EUR", "2024-05-14", "812.50"),
                new NewPosition("MSFT", "XNAS", "10", "USD", null, null));

        repository.save(p, "key-1");

        assertThat(count("portfolio")).isEqualTo(1);
        assertThat(count("position")).isEqualTo(2);

        Portfolio loaded = repository.findByIdempotencyKey("key-1").orElseThrow();
        assertThat(loaded.name().value()).isEqualTo("Dividend");
        assertThat(loaded.positions()).hasSize(2);
        Position asml = loaded.positions().stream()
                .filter(x -> x.instrument().ticker().value().equals("ASML")).findFirst().orElseThrow();
        assertThat(asml.quantity().value()).isEqualByComparingTo(new BigDecimal("3.250"));
        assertThat(asml.quantity().value().toPlainString()).isEqualTo("3.250"); // SC-007
        assertThat(asml.averagePurchasePrice()).hasValueSatisfying(m -> {
            assertThat(m.amount()).isEqualByComparingTo(new BigDecimal("812.50"));
            assertThat(m.currency().code()).isEqualTo("EUR");
        });
        assertThat(asml.initialPurchaseDate()).hasValue(LocalDate.parse("2024-05-14"));
    }

    @Test
    void rolls_back_entirely_when_a_position_insert_fails() { // FR-023 / SC-010
        // Two identical instruments slip past reconstitute (bypasses domain dedup); the DB
        // UNIQUE(portfolio_id, ticker, market) rejects the 2nd insert -> whole transaction rolls back.
        Portfolio broken = Portfolio.reconstitute(PortfolioId.newId(), INVESTOR,
                new PortfolioName("Broken"), PortfolioStatus.ACTIVE,
                List.of(rebuiltPosition("ASML", "XAMS", "1"), rebuiltPosition("ASML", "XAMS", "2")),
                Instant.now(CLOCK));

        assertThatThrownBy(() -> repository.save(broken, "key-rollback")).isInstanceOf(RuntimeException.class);

        assertThat(count("portfolio")).isEqualTo(0);
        assertThat(count("position")).isEqualTo(0);
    }

    @Test
    void a_repeated_idempotency_key_resolves_to_the_existing_portfolio() { // FR-031a
        Portfolio first = portfolio("Once", new NewPosition("ASML", "XAMS", "1", "EUR", null, null));
        repository.save(first, "same-key");

        Portfolio second = portfolio("Again", new NewPosition("MSFT", "XNAS", "9", "USD", null, null));
        Portfolio result = repository.save(second, "same-key");

        assertThat(result.id()).isEqualTo(first.id());
        assertThat(count("portfolio")).isEqualTo(1);
    }

    @Test
    void unique_constraints_are_present() {
        assertThat(constraintExists("portfolio_idem_key_uk")).isEqualTo(1);
        assertThat(constraintExists("position_instrument_uk")).isEqualTo(1);
    }

    @Test
    void a_loaded_portfolio_is_fully_usable_outside_a_transaction() { // no LazyInitializationException
        repository.save(portfolio("Detached",
                new NewPosition("ASML", "XAMS", "2", "EUR", null, null),
                new NewPosition("MSFT", "XNAS", "3", "USD", null, null)), "key-detached");

        Portfolio loaded = repository.findByIdempotencyKey("key-detached").orElseThrow();

        assertThatCode(() -> {
            for (Position position : loaded.positions()) {
                position.instrument().ticker().value();
                position.quantity().value().toPlainString();
                position.averagePurchasePrice().ifPresent(m -> m.amount().toPlainString());
            }
        }).doesNotThrowAnyException();
        assertThat(loaded.positions()).hasSize(2);
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private long constraintExists(String name) {
        return jdbc.sql("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name = :n")
                .param("n", name).query(Long.class).single();
    }
}
