package com.myfinaimanager.core.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need a real, disposable PostgreSQL
 * instance (EN001 AC-003). No developer-installed database is required — the
 * container is started by Testcontainers and wired via {@code @ServiceConnection}.
 * The container is static so it is reused across the test class.
 */
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
