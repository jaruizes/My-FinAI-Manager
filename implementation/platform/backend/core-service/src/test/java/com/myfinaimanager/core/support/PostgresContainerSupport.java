package com.myfinaimanager.core.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that need a real, disposable PostgreSQL instance.
 *
 * <p>Testcontainers policy (testing-strategy §3, FR-025/FR-026): integration tests must run against
 * real disposable infrastructure and must not depend on a manually installed database. The
 * container image is pinned to the PostgreSQL major version approved in
 * {@code specs/EN001-bootstrap-platform/research.md} (OD-5: PostgreSQL 16).
 */
@Testcontainers
public abstract class PostgresContainerSupport {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");

    @Container
    @SuppressWarnings("resource") // lifecycle managed by the Testcontainers JUnit extension
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("finai")
            .withUsername("finai")
            .withPassword("finai_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
