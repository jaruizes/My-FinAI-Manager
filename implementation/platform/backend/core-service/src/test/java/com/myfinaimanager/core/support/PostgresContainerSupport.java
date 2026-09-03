package com.myfinaimanager.core.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real, disposable PostgreSQL instance.
 *
 * <p>Testcontainers policy (testing-strategy §3, FR-025/FR-026): integration tests must run against
 * real disposable infrastructure and must not depend on a manually installed database. The
 * container image is pinned to the PostgreSQL major version approved in
 * {@code specs/EN001-bootstrap-platform/research.md} (OD-5: PostgreSQL 16).
 *
 * <p><b>Singleton container pattern.</b> One PostgreSQL container is started on first use and
 * shared by every integration-test class in the JVM fork (the Testcontainers JUnit lifecycle would
 * otherwise start and stop a separate container per class, which exhausts a small local Docker VM
 * once several {@code *IT} classes exist). The container is torn down by the Testcontainers JVM
 * shutdown hook when the test run ends. Each test class is still responsible for cleaning the rows
 * it writes.
 */
public abstract class PostgresContainerSupport {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @SuppressWarnings("resource") // shared for the whole test run; closed by the Testcontainers shutdown hook
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("finai")
            .withUsername("finai")
            .withPassword("finai_test")
            .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // EN004: never auto-run the reference-data import in @SpringBootTest slices — the import
        // ITs drive ImportReferenceDataService explicitly (research.md D11 / task T044).
        registry.add("app.reference-data.import-on-startup", () -> "false");
    }
}
