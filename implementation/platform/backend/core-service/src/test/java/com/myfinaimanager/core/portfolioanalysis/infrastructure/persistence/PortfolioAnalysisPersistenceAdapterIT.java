package com.myfinaimanager.core.portfolioanalysis.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.support.PostgresContainerSupport;

/**
 * {@link PortfolioAnalysisPersistenceAdapter} against a real PostgreSQL (US4; contract C3, R1-R3).
 * The database's partial unique index is the real, authoritative duplicate-open-request guard
 * (research D4) — proven here with a genuine concurrent race, not just a mocked exception path.
 */
@SpringBootTest
class PortfolioAnalysisPersistenceAdapterIT extends PostgresContainerSupport {

    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");

    @Autowired
    private PortfolioAnalysisRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private UUID portfolioId;

    @BeforeEach
    void seedPortfolio() {
        jdbc.sql("DELETE FROM portfolio_analysis_risk").update();
        jdbc.sql("DELETE FROM portfolio_analysis_insight").update();
        jdbc.sql("DELETE FROM portfolio_analysis").update();
        jdbc.sql("DELETE FROM position").update();
        jdbc.sql("DELETE FROM portfolio").update();
        portfolioId = UUID.randomUUID();
        jdbc.sql("INSERT INTO portfolio (id, investor_id, name, status, created_at, idempotency_key) "
                        + "VALUES (:id, '00000000-0000-0000-0000-000000000001', 'IT Portfolio', 'ACTIVE', "
                        + ":createdAt, :key)")
                .param("id", portfolioId)
                .param("createdAt", java.sql.Timestamp.from(NOW))
                .param("key", "it-key-" + portfolioId)
                .update();
    }

    private long openAnalysisCount() {
        return jdbc.sql("SELECT count(*) FROM portfolio_analysis WHERE portfolio_id = :id "
                        + "AND status IN ('PENDING', 'RUNNING')")
                .param("id", portfolioId)
                .query(Long.class).single();
    }

    @Test
    void two_concurrent_saves_for_the_same_open_request_yield_exactly_one_success() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try {
            Future<Boolean> first = pool.submit(() -> attemptSave(ready, go));
            Future<Boolean> second = pool.submit(() -> attemptSave(ready, go));

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();

            boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);

            assertThat(firstSucceeded ^ secondSucceeded)
                    .as("exactly one of the two concurrent saves must succeed")
                    .isTrue();
            assertThat(openAnalysisCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean attemptSave(CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            repository.save(PortfolioAnalysis.requested(portfolioId, NOW, CreationTrigger.AUTOMATIC));
            return true;
        } catch (AnalysisAlreadyInProgressException e) {
            return false;
        }
    }

    @Test
    void a_prior_completed_row_is_untouched_byte_for_byte_after_a_new_request() {
        PortfolioAnalysisResult result = new PortfolioAnalysisResult(
                DiversificationLevel.HIGH, "Well diversified.", List.of(), List.of(), "openai",
                "gpt-4o-mini", "portfolio-analysis", "v1", 10, 5, 15, new BigDecimal("0.001"));
        PortfolioAnalysis completed = repository.save(
                PortfolioAnalysis.requested(portfolioId, NOW, CreationTrigger.AUTOMATIC)
                        .withRunning(NOW.plusSeconds(1)))
                .withCompleted(result, NOW.plusSeconds(2));
        PortfolioAnalysis savedCompleted = repository.save(completed);

        PortfolioAnalysis before = repository.findById(savedCompleted.id()).orElseThrow();

        PortfolioAnalysis newRequest =
                repository.save(PortfolioAnalysis.requested(portfolioId, NOW.plusSeconds(60), CreationTrigger.MANUAL));

        PortfolioAnalysis after = repository.findById(savedCompleted.id()).orElseThrow();
        assertThat(after).isEqualTo(before);
        assertThat(newRequest.id()).isNotEqualTo(savedCompleted.id());
        assertThat(repository.findLatestByPortfolioId(portfolioId)).contains(newRequest);
    }

    @Test
    void findLatestByPortfolioId_returns_empty_when_no_analysis_was_ever_requested() {
        assertThat(repository.findLatestByPortfolioId(UUID.randomUUID())).isEmpty();
    }
}
