package com.myfinaimanager.core.platform.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException;
import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import com.myfinaimanager.core.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * AC-003: the platform version round-trips through Flyway + the real persistence
 * path against a disposable PostgreSQL. AC-008: a persistence failure surfaces as
 * the neutral domain exception, never a fabricated version.
 */
@SpringBootTest
class PlatformVersionRepositoryAdapterIT extends AbstractPostgresIT {

    @Autowired
    private PlatformVersionRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsTheVersionSeededByFlyway() {
        assertThat(adapter.find()).contains(PlatformVersion.of("0.1.0"));
    }

    @Test
    @Transactional
    void surfacesUnavailableWhenTheVersionCannotBeRead() {
        // PostgreSQL DDL is transactional; the test transaction is rolled back afterwards.
        jdbcTemplate.execute("DROP TABLE platform_version");

        assertThatThrownBy(adapter::find)
                .isInstanceOf(PlatformVersionUnavailableException.class);
    }
}
