package com.myfinaimanager.core.financialinstrument.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Flyway owns the {@code financialinstrument} schema; Hibernate ({@code ddl-auto: none}) must never
 * touch it (EN004 §16 / FR-029 / VC-014). Asserts the {@code V3} migration ran, the constraints
 * EN004 relies on exist, and the {@code market} / {@code financial_instrument} column sets are
 * exactly what {@code V3__financial_instrument.sql} created.
 */
@SpringBootTest
class ReferenceDataSchemaIntegrityIT extends PostgresContainerSupport {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void flyway_history_contains_the_v3_migration_and_it_succeeded() {
        List<String> versions = jdbc.sql(
                        "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank")
                .query(String.class).list();
        assertThat(versions).contains("3");
        assertThat(jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '3'")
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void the_constraints_en004_depends_on_exist() {
        assertThat(constraintExists("financial_instrument", "fin_instr_identity_uk")).isTrue();
        assertThat(constraintExists("financial_instrument", "fin_instr_currency_chk")).isTrue();
        assertThat(constraintExists("financial_instrument", "fin_instr_isin_chk")).isTrue();
        assertThat(constraintExists("financial_instrument", "fin_instr_ticker_len_chk")).isTrue();
        assertThat(constraintExists("financial_instrument", "fin_instr_type_chk")).isTrue();
        assertThat(constraintExists("market", "market_mic_shape_chk")).isTrue();
        // the FK from financial_instrument.market_mic -> market.mic
        Long fk = jdbc.sql("""
                        SELECT count(*) FROM information_schema.table_constraints
                        WHERE constraint_type = 'FOREIGN KEY' AND table_name = 'financial_instrument'
                        """).query(Long.class).single();
        assertThat(fk).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void hibernate_did_not_create_or_alter_the_reference_tables() {
        assertThat(columns("market")).containsExactlyInAnyOrder(
                "mic", "name", "country_iso2", "operating_mic", "active",
                "source", "source_reference", "last_imported_at");
        assertThat(columns("financial_instrument")).containsExactlyInAnyOrder(
                "id", "name", "ticker", "market_mic", "currency", "isin", "external_reference",
                "instrument_type", "provider_symbol", "active", "source", "source_reference", "last_imported_at");
        assertThat(tables()).contains("market", "financial_instrument", "flyway_schema_history");
        assertThat(tables()).noneMatch(t -> t.startsWith("hibernate_"));
    }

    private boolean constraintExists(String table, String name) {
        return jdbc.sql("""
                        SELECT count(*) FROM information_schema.table_constraints
                        WHERE table_name = :t AND constraint_name = :n
                        """)
                .param("t", table).param("n", name).query(Long.class).single() == 1;
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
