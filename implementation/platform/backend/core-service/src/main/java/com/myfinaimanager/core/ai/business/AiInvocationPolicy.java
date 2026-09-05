package com.myfinaimanager.core.ai.business;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import com.myfinaimanager.core.ai.domain.exceptions.AiConfigurationErrorException;
import com.myfinaimanager.core.ai.domain.exceptions.AiCostBudgetExceededException;
import com.myfinaimanager.core.ai.domain.exceptions.AiException;
import com.myfinaimanager.core.ai.domain.exceptions.AiGuardrailRejectedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderRateLimitedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderUnavailableException;
import com.myfinaimanager.core.ai.domain.exceptions.AiStructuredOutputInvalidException;
import com.myfinaimanager.core.ai.domain.exceptions.AiTimeoutException;
import com.myfinaimanager.core.ai.domain.exceptions.AiTokenBudgetExceededException;
import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.GuardrailOutcome;
import com.myfinaimanager.core.ai.domain.model.InvocationTelemetry;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.domain.model.StructuredOutputValidator;
import com.myfinaimanager.core.ai.domain.model.TokenUsageEstimate;
import com.myfinaimanager.core.ai.domain.ports.AiModelPort;
import com.myfinaimanager.core.ai.domain.ports.InputGuardrailPort;
import com.myfinaimanager.core.ai.domain.ports.OutputGuardrailPort;
import com.myfinaimanager.core.ai.domain.ports.TelemetryPort;
import com.myfinaimanager.core.ai.domain.ports.TelemetryScope;
import com.myfinaimanager.core.ai.domain.ports.TokenCounterPort;

/**
 * The single orchestrator EN006 requires (enabler §29; FR-038): task validation, provider/model
 * resolution, prompt composition, context sanitization, token/cost budget enforcement, input
 * guardrails, provider invocation (bounded timeout + bounded retry for transient failures), output
 * guardrails, structured-output validation, and telemetry — in that exact order, every time.
 * Business features must not reimplement any of this independently.
 *
 * <p>No pricing table exists because EN006's own shipped adapter (local) is a stub (resolved Q1) —
 * the pre-invocation cost estimate below is a deliberately simple, documented placeholder (spec.md
 * Assumption A6), sufficient to prove the cost-budget-enforcement mechanism (FR-031) without
 * claiming real pricing for every possible provider.
 *
 * <p>Provider resolution is per-task (FD005 research D1): {@code settings.taskProviders()} is
 * checked first for {@code taskType}, falling back to {@code settings.defaultProvider()} — this is
 * how EN006's own {@code "diagnostic"} task keeps using the local/stub adapter while a real feature
 * (e.g. FD005's {@code "portfolio-analysis"}) is routed to a different, named {@code AiModelPort}
 * bean, with zero change to this orchestration logic.
 */
@Service
public class AiInvocationPolicy implements GenerateAiUseCase {

    /** Placeholder pricing (spec.md A6) — replaced once a real provider adapter exists. */
    private static final BigDecimal NOMINAL_COST_PER_TOKEN = new BigDecimal("0.00001");

    private final Map<String, AiModelPort> modelPortsByProvider;
    private final AiInvocationSettings settings;
    private final PromptService promptService;
    private final ContextBudgetService contextBudgetService;
    private final TokenCounterPort tokenCounter;
    private final InputGuardrailPort inputGuardrail;
    private final OutputGuardrailPort outputGuardrail;
    private final TelemetryPort telemetry;

    public AiInvocationPolicy(
            Map<String, AiModelPort> modelPortsByProvider,
            AiInvocationSettings settings,
            PromptService promptService,
            ContextBudgetService contextBudgetService,
            TokenCounterPort tokenCounter,
            InputGuardrailPort inputGuardrail,
            OutputGuardrailPort outputGuardrail,
            TelemetryPort telemetry) {
        this.modelPortsByProvider = modelPortsByProvider;
        this.settings = settings;
        this.promptService = promptService;
        this.contextBudgetService = contextBudgetService;
        this.tokenCounter = tokenCounter;
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
        this.telemetry = telemetry;
    }

    private String resolveProviderId(String taskType) {
        return settings.taskProviders().getOrDefault(taskType, settings.defaultProvider());
    }

    private AiModelPort resolveModelPort(String taskType, String providerId) {
        AiModelPort modelPort = modelPortsByProvider.get(providerId);
        if (modelPort == null) {
            throw new AiConfigurationErrorException(
                    "no AiModelPort registered for provider '" + providerId + "' (task '" + taskType + "')");
        }
        return modelPort;
    }

    @Override
    public AiResponse generate(AiRequest callerRequest) {
        String taskType = callerRequest.taskType();
        String providerId = resolveProviderId(taskType);
        String invocationId = UUID.randomUUID().toString();
        long usecaseStart = System.nanoTime();
        PromptReference composedPrompt = null;

        try (TelemetryScope usecase = telemetry.startUsecase(taskType)) {
            try {
                AiModelPort modelPort = resolveModelPort(taskType, providerId);
                composedPrompt = promptService.compose(taskType);
                String budgetedContext =
                        contextBudgetService.build(callerRequest.context(), settings.limits().maxInputCharacters());
                AiRequest resolved = callerRequest.withSystemPrompt(composedPrompt).withContext(budgetedContext);

                enforceTokenBudget(resolved);
                enforceCostBudget(resolved);
                enforceInputGuardrail(resolved);

                AiResponse response = invokeWithRetryAndTimeout(resolved, modelPort);

                enforceOutputGuardrail(resolved, response);
                validateStructuredOutput(resolved, response);

                long latencyMs = (System.nanoTime() - usecaseStart) / 1_000_000;
                telemetry.recordOutcome(successTelemetry(
                        response, taskType, composedPrompt, invocationId, latencyMs, callerRequest.correlationId()));
                return response;
            } catch (AiException failure) {
                long latencyMs = (System.nanoTime() - usecaseStart) / 1_000_000;
                telemetry.recordOutcome(failureTelemetry(
                        failure, taskType, providerId, composedPrompt, invocationId, latencyMs,
                        callerRequest.correlationId()));
                throw failure;
            }
        }
    }

    private void enforceTokenBudget(AiRequest request) {
        TokenUsageEstimate estimate = tokenCounter.estimate(request);
        AiInvocationSettings.TokenLimits limits = settings.limits();
        if (estimate.inputTokens() > limits.maxInputTokens()) {
            throw new AiTokenBudgetExceededException(
                    "estimated input tokens " + estimate.inputTokens() + " exceed max-input-tokens "
                            + limits.maxInputTokens());
        }
        if (request.maxOutputTokens() > limits.maxOutputTokens()) {
            throw new AiTokenBudgetExceededException(
                    "requested output tokens " + request.maxOutputTokens() + " exceed max-output-tokens "
                            + limits.maxOutputTokens());
        }
        if (estimate.estimatedTotalTokens() > limits.maxTotalTokens()) {
            throw new AiTokenBudgetExceededException(
                    "estimated total tokens " + estimate.estimatedTotalTokens() + " exceed max-total-tokens "
                            + limits.maxTotalTokens());
        }
    }

    private void enforceCostBudget(AiRequest request) {
        TokenUsageEstimate estimate = tokenCounter.estimate(request);
        BigDecimal preInvocationCost =
                NOMINAL_COST_PER_TOKEN.multiply(BigDecimal.valueOf(estimate.estimatedTotalTokens()));
        if (preInvocationCost.compareTo(settings.maxEstimatedCost()) > 0) {
            throw new AiCostBudgetExceededException(
                    "estimated cost " + preInvocationCost + " exceeds max-estimated-cost "
                            + settings.maxEstimatedCost());
        }
    }

    private void enforceInputGuardrail(AiRequest request) {
        GuardrailOutcome outcome = inputGuardrail.check(request);
        rejectIfNotAllowed(outcome);
    }

    private void enforceOutputGuardrail(AiRequest request, AiResponse response) {
        GuardrailOutcome outcome = outputGuardrail.check(request, response);
        rejectIfNotAllowed(outcome);
    }

    private void validateStructuredOutput(AiRequest request, AiResponse response) {
        if (request.outputSchema().isEmpty()) {
            return;
        }
        Map<String, Object> candidate = response.structuredContent().orElse(null);
        GuardrailOutcome outcome = StructuredOutputValidator.validate(request.outputSchema().get(), candidate);
        if (!outcome.isAllowed()) {
            GuardrailOutcome.Rejected rejected = (GuardrailOutcome.Rejected) outcome;
            throw new AiStructuredOutputInvalidException(rejected.reason());
        }
    }

    private void rejectIfNotAllowed(GuardrailOutcome outcome) {
        if (!outcome.isAllowed()) {
            GuardrailOutcome.Rejected rejected = (GuardrailOutcome.Rejected) outcome;
            throw new AiGuardrailRejectedException(rejected.guardrailName(), rejected.reason());
        }
    }

    /**
     * Retries only {@link AiProviderUnavailableException}/{@link AiProviderRateLimitedException}
     * (transient), bounded by {@code settings.maxRetryAttempts()}; every other failure — including
     * a timeout — surfaces on the first attempt (contract {@code ai-model-port.md} C2, Q5/Q6).
     */
    private AiResponse invokeWithRetryAndTimeout(AiRequest request, AiModelPort modelPort) {
        int attempt = 0;
        while (true) {
            attempt++;
            try (TelemetryScope invocation = telemetry.startInvocation()) {
                return callWithTimeout(request, modelPort);
            } catch (AiProviderUnavailableException | AiProviderRateLimitedException transient_) {
                if (attempt >= settings.maxRetryAttempts()) {
                    throw transient_;
                }
                sleepBackoff();
            }
        }
    }

    private AiResponse callWithTimeout(AiRequest request, AiModelPort modelPort) {
        CompletableFuture<AiResponse> future = CompletableFuture.supplyAsync(() -> modelPort.generate(request));
        try {
            return future.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiTimeoutException("AI call exceeded the configured timeout of " + settings.timeout());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AiException aiException) {
                throw aiException;
            }
            throw new AiProviderUnavailableException(
                    "unexpected adapter failure: " + cause.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderUnavailableException("invocation interrupted");
        }
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(settings.retryBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private InvocationTelemetry successTelemetry(
            AiResponse response,
            String taskType,
            PromptReference prompt,
            String invocationId,
            long latencyMs,
            String correlationId) {
        return new InvocationTelemetry(
                response.provider(),
                response.model(),
                taskType,
                prompt.promptId(),
                prompt.promptVersion(),
                response.usage().inputTokens(),
                response.usage().outputTokens(),
                response.usage().totalTokens(),
                response.usage().estimatedCost(),
                latencyMs,
                true,
                response.finishReason(),
                "allowed",
                correlationId,
                invocationId,
                Optional.of(response.requestId()),
                Optional.empty());
    }

    private InvocationTelemetry failureTelemetry(
            AiException failure,
            String taskType,
            String providerId,
            PromptReference prompt,
            String invocationId,
            long latencyMs,
            String correlationId) {
        String guardrailResult = failure instanceof AiGuardrailRejectedException rejected
                ? "rejected:" + rejected.guardrailName()
                : "not-evaluated";
        String promptId = prompt != null ? prompt.promptId() : "unresolved";
        String promptVersion = prompt != null ? prompt.promptVersion() : "n/a";
        return new InvocationTelemetry(
                providerId,
                settings.defaultModel(),
                taskType,
                promptId,
                promptVersion,
                0,
                0,
                0,
                BigDecimal.ZERO,
                latencyMs,
                false,
                Optional.empty(),
                guardrailResult,
                correlationId,
                invocationId,
                Optional.empty(),
                Optional.of(failure.getClass().getSimpleName()));
    }
}
