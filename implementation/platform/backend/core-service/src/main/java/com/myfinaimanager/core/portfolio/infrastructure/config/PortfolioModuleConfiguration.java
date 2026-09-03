package com.myfinaimanager.core.portfolio.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Module configuration for the Portfolio capability (ADR-003 — configuration is infrastructure).
 *
 * <p>The only bean here is the {@link Clock} the business layer uses for {@code createdAt} and
 * "no future date" checks; a bean (not {@code Clock.systemUTC()} inline) keeps the business logic
 * testable with a fixed clock. Everything else is component-scanned: {@code CreatePortfolioService}
 * ({@code @Service}), the persistence adapters ({@code @Repository}), and the REST controller.
 */
@Configuration
public class PortfolioModuleConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
