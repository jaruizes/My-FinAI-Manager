package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Flyway owns the schema and Hibernate ({@code ddl-auto: none}) must never touch it (research.md
 * D7 / FR-022 / VC-011). Asserts the portfolio migration history, the constraints EN003 relies on,
 * and that the {@code portfolio} / {@code position} / {@code investor} column sets are exactly what
 * {@code V2__portfolio.sql} created — i.e. Hibernate added/altered nothing.
 *
 * <p>Sibling modules add their own forward migrations (EN004 adds {@code V3__financial_instrument.sql});
 * this test asserts the portfolio migrations/tables are present and unaltered, not that they are the
 * only ones. The {@code financialinstrument} module has its own {@code ReferenceDataSchemaIntegrityIT}.
 */
@SpringBootTest
class SchemaIntegrityIT extends PostgresContainerSupport {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void flyway_history_contains_the_baseline_and_the_portfolio_migration_and_all_migrations_succeeded() {
        List<String> versions = jdbc.sql(
                        "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")
                .query(String.class).list();
        assertThat(versions).contains("1", "2");
        assertThat(jdbc.sql("SELECT bool_and(success) FROM flyway_schema_history")
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void the_constraints_en003_depends_on_exist() {
        assertThat(constraintExists("portfolio_idem_key_uk")).isTrue();
        assertThat(constraintExists("position_instrument_uk")).isTrue();
        assertThat(constraintExists("position_quantity_chk")).isTrue();
        assertThat(constraintExists("position_price_positive_chk")).isTrue();
        assertThat(constraintExists("position_price_currency_chk")).isTrue();
        assertThat(constraintExists("position_price_pair_chk")).isTrue();
        assertThat(constraintExists("position_date_not_future_chk")).isTrue();
        assertThat(constraintExists("portfolio_status_chk")).isTrue();
        assertThat(constraintExists("portfolio_name_len_chk")).isTrue();
    }

    @Test
    void hibernate_did_not_create_or_alter_any_table() {
        assertThat(columns("portfolio")).containsExactlyInAnyOrder(
                "id", "investor_id", "name", "status", "idempotency_key", "created_at");
        assertThat(columns("position")).containsExactlyInAnyOrder(
                "id", "portfolio_id", "ticker", "market", "quantity", "currency",
                "initial_purchase_date", "average_purchase_price", "average_purchase_price_currency");
        assertThat(columns("investor")).containsExactlyInAnyOrder(
                "id", "display_name", "preferred_currency", "created_at");

        // The portfolio tables + Flyway's history are present; no Hibernate bookkeeping artifact
        // (e.g. a `hibernate_sequence`) exists. Sibling-module tables (EN004) are allowed.
        assertThat(tables()).contains("flyway_schema_history", "investor", "portfolio", "position");
        assertThat(tables()).noneMatch(t -> t.startsWith("hibernate_"));
    }

    private boolean constraintExists(String name) {
        return jdbc.sql("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name = :n")
                .param("n", name).query(Long.class).single() == 1;
    }

    private List<String> columns(String table) {
        return jdbc.sql("SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = :t")
                .param("t", table).query(String.class).list();
    }

    private List<String> tables() {
        return jdbc.sql("SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")
                .query(String.class).list();
    }
}
