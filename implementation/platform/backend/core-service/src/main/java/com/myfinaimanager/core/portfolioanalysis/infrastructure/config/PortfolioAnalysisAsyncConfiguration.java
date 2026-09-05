package com.myfinaimanager.core.portfolioanalysis.infrastructure.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for {@code PortfolioAnalysisWorker} (resolved Q2; FR-055) — distinct from the
 * HTTP request-handling pool, so a slow/stuck analysis cannot starve Portfolio creation/read
 * traffic. In-process, no new deployable/container/dependency (spec's planning-level note) — no ADR
 * required, unlike EN006's own ADR-004.
 *
 * <p>Sizing is a small, planning-level default (spec A3) — this is a low-volume, personal-scale
 * feature; safe to tune later without any code change beyond this bean.
 */
@Configuration
@EnableAsync
public class PortfolioAnalysisAsyncConfiguration {

    @Bean("portfolioAnalysisExecutor")
    public Executor portfolioAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("portfolio-analysis-");
        executor.initialize();
        return executor;
    }
}
