package com.myfinaimanager.core.portfolioanalysis.business;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisFailedException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisId;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisContext;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisContextBuilder;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisAiPort;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisRepository;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioContextGateway;

/**
 * Runs one analysis end to end off the request thread (US1; data-model.md §5). {@link #runAsync}
 * is {@code @Async("portfolioAnalysisExecutor")} — it must be called on a <strong>different</strong>
 * Spring bean than the caller, never {@code this} (Spring's well-known self-invocation limitation
 * would otherwise bypass the proxy and run this synchronously — research D5).
 *
 * <p>The entire body is wrapped in a last-resort {@code catch (Throwable)} (FR-057) so an analysis
 * is <strong>never</strong> left stuck at {@code RUNNING} — every code path reaches a terminal
 * state, or (in the rare case persistence itself is failing) is logged loudly instead of silently
 * disappearing.
 */
@Component
public class PortfolioAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAnalysisWorker.class);

    private final PortfolioAnalysisRepository repository;
    private final PortfolioContextGateway contextGateway;
    private final PortfolioAnalysisAiPort aiPort;
    private final Clock clock;

    public PortfolioAnalysisWorker(PortfolioAnalysisRepository repository,
                                   PortfolioContextGateway contextGateway,
                                   PortfolioAnalysisAiPort aiPort,
                                   Clock clock) {
        this.repository = repository;
        this.contextGateway = contextGateway;
        this.aiPort = aiPort;
        this.clock = clock;
    }

    @Async("portfolioAnalysisExecutor")
    public void runAsync(AnalysisId analysisId) {
        try {
            run(analysisId);
        } catch (Throwable t) {
            log.atError()
                    .addKeyValue("event", "PortfolioAnalysisWorkerCrashed")
                    .addKeyValue("analysisId", analysisId.toString())
                    .addKeyValue("reason", t.getClass().getSimpleName())
                    .log("the analysis worker failed outside its own guarded path; the analysis "
                            + "may remain RUNNING and require manual investigation");
        }
    }

    private void run(AnalysisId analysisId) {
        PortfolioAnalysis pending = repository.findById(analysisId).orElse(null);
        if (pending == null) {
            log.atError()
                    .addKeyValue("event", "PortfolioAnalysisNotFound")
                    .addKeyValue("analysisId", analysisId.toString())
                    .log("worker invoked for an analysis id that no longer exists");
            return;
        }

        PortfolioAnalysis running;
        try {
            running = repository.save(pending.withRunning(clock.instant()));
        } catch (RuntimeException e) {
            log.atError()
                    .addKeyValue("event", "PortfolioAnalysisRunningTransitionFailed")
                    .addKeyValue("analysisId", analysisId.toString())
                    .addKeyValue("reason", e.getClass().getSimpleName())
                    .log("could not transition the analysis to RUNNING");
            return;
        }

        try {
            PortfolioContextSnapshot snapshot = contextGateway.fetch(running.portfolioId());
            PortfolioAnalysisContext context = PortfolioAnalysisContextBuilder.build(snapshot);

            if (!context.sufficient()) {
                fail(running, FailureReason.INSUFFICIENT_DATA);
                return;
            }

            PortfolioAnalysisResult result = aiPort.analyze(context, analysisId.value().toString());
            repository.save(running.withCompleted(result, clock.instant()));

        } catch (AnalysisFailedException e) {
            fail(running, e.reason());
        } catch (RuntimeException e) {
            log.atError()
                    .addKeyValue("event", "PortfolioAnalysisUnexpectedFailure")
                    .addKeyValue("analysisId", analysisId.toString())
                    .addKeyValue("reason", e.getClass().getSimpleName())
                    .log("unexpected failure during analysis; marking FAILED(UNKNOWN)");
            fail(running, FailureReason.UNKNOWN);
        }
    }

    private void fail(PortfolioAnalysis running, FailureReason reason) {
        repository.save(running.withFailed(reason, clock.instant()));
    }
}
