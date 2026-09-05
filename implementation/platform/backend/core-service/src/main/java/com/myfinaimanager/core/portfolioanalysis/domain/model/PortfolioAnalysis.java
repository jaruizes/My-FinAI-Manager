package com.myfinaimanager.core.portfolioanalysis.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One AI-generated Portfolio analysis request and its lifecycle (data-model.md §2, §5). Immutable
 * per state snapshot — a wither method returns a <strong>new</strong> instance; the persistence
 * adapter updates the same row by {@link #id()} (a different analysis's row is never touched —
 * FD005 §10).
 *
 * <p>{@code portfolioId} is a plain {@link UUID}, not {@code portfolio.domain.model.PortfolioId} —
 * AR-062 forbids another module's type reaching this domain package (see data-model.md's
 * "Implementation correction" note). Only the ACL adapter converts it back to a real
 * {@code PortfolioId} when calling into the {@code portfolio} module.
 */
public record PortfolioAnalysis(
        AnalysisId id,
        UUID portfolioId,
        AnalysisStatus status,
        Instant requestedAt,
        Optional<Instant> startedAt,
        Optional<Instant> completedAt,
        Optional<String> summary,
        Optional<DiversificationLevel> overallDiversification,
        List<Insight> insights,
        List<Risk> risks,
        Optional<String> provider,
        Optional<String> model,
        Optional<String> promptId,
        Optional<String> promptVersion,
        Optional<Integer> inputTokens,
        Optional<Integer> outputTokens,
        Optional<Integer> totalTokens,
        Optional<BigDecimal> estimatedCost,
        Optional<FailureReason> failureReasonCode,
        CreationTrigger createdByTrigger) {

    public PortfolioAnalysis {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedAt, "requestedAt");
        startedAt = orEmpty(startedAt);
        completedAt = orEmpty(completedAt);
        summary = orEmpty(summary);
        overallDiversification = orEmpty(overallDiversification);
        insights = insights == null ? List.of() : List.copyOf(insights);
        risks = risks == null ? List.of() : List.copyOf(risks);
        provider = orEmpty(provider);
        model = orEmpty(model);
        promptId = orEmpty(promptId);
        promptVersion = orEmpty(promptVersion);
        inputTokens = orEmpty(inputTokens);
        outputTokens = orEmpty(outputTokens);
        totalTokens = orEmpty(totalTokens);
        estimatedCost = orEmpty(estimatedCost);
        failureReasonCode = orEmpty(failureReasonCode);
        Objects.requireNonNull(createdByTrigger, "createdByTrigger");
    }

    private static <T> Optional<T> orEmpty(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    /** A brand-new, just-requested analysis (status {@code PENDING}). */
    public static PortfolioAnalysis requested(UUID portfolioId, Instant requestedAt, CreationTrigger trigger) {
        return new PortfolioAnalysis(
                AnalysisId.newId(), portfolioId, AnalysisStatus.PENDING, requestedAt, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), trigger);
    }

    /** @throws IllegalStateException if not currently {@code PENDING} */
    public PortfolioAnalysis withRunning(Instant startedAt) {
        requireStatus(AnalysisStatus.PENDING);
        return new PortfolioAnalysis(
                id, portfolioId, AnalysisStatus.RUNNING, requestedAt, Optional.of(startedAt), completedAt,
                summary, overallDiversification, insights, risks, provider, model, promptId, promptVersion,
                inputTokens, outputTokens, totalTokens, estimatedCost, failureReasonCode, createdByTrigger);
    }

    /** @throws IllegalStateException if not currently {@code RUNNING} */
    public PortfolioAnalysis withCompleted(PortfolioAnalysisResult result, Instant completedAt) {
        requireStatus(AnalysisStatus.RUNNING);
        Objects.requireNonNull(result, "result");
        return new PortfolioAnalysis(
                id, portfolioId, AnalysisStatus.COMPLETED, requestedAt, startedAt, Optional.of(completedAt),
                Optional.of(result.explanation()), Optional.of(result.overallDiversification()),
                result.insights(), result.risks(),
                Optional.of(result.provider()), Optional.of(result.model()), Optional.of(result.promptId()),
                Optional.of(result.promptVersion()), Optional.of(result.inputTokens()),
                Optional.of(result.outputTokens()), Optional.of(result.totalTokens()),
                Optional.of(result.estimatedCost()), Optional.empty(), createdByTrigger);
    }

    /** @throws IllegalStateException if already terminal ({@code COMPLETED}/{@code FAILED}) */
    public PortfolioAnalysis withFailed(FailureReason reason, Instant completedAt) {
        if (status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED) {
            throw new IllegalStateException("cannot fail an analysis already in a terminal state: " + status);
        }
        Objects.requireNonNull(reason, "reason");
        return new PortfolioAnalysis(
                id, portfolioId, AnalysisStatus.FAILED, requestedAt, startedAt, Optional.of(completedAt),
                Optional.empty(), Optional.empty(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(reason), createdByTrigger);
    }

    private void requireStatus(AnalysisStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("expected status " + expected + " but was " + status);
        }
    }

    /**
     * @param type    a free-form insight category (not a closed vocabulary — FR-029)
     * @param message the grounded, Investor-facing insight text
     * @param order   display order among the analysis's insights (0-based)
     */
    public record Insight(String type, String message, int order) {

        public Insight {
            Objects.requireNonNull(type, "type");
            if (type.isBlank()) {
                throw new IllegalArgumentException("type must not be blank");
            }
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
            if (order < 0) {
                throw new IllegalArgumentException("order must not be negative");
            }
        }
    }

    /**
     * @param type        the closed risk-type vocabulary (FR-031)
     * @param severity    the closed severity vocabulary (FR-030)
     * @param title       a short risk title
     * @param explanation the grounded explanation
     * @param order       display order among the analysis's risks (0-based)
     */
    public record Risk(RiskType type, RiskSeverity severity, String title, String explanation, int order) {

        public Risk {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(title, "title");
            if (title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            Objects.requireNonNull(explanation, "explanation");
            if (explanation.isBlank()) {
                throw new IllegalArgumentException("explanation must not be blank");
            }
            if (order < 0) {
                throw new IllegalArgumentException("order must not be negative");
            }
        }
    }
}
