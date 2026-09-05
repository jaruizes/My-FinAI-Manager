package com.myfinaimanager.core.portfolioanalysis.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.business.GenerateAiUseCase;
import com.myfinaimanager.core.ai.domain.exceptions.AiConfigurationErrorException;
import com.myfinaimanager.core.ai.domain.exceptions.AiCostBudgetExceededException;
import com.myfinaimanager.core.ai.domain.exceptions.AiException;
import com.myfinaimanager.core.ai.domain.exceptions.AiGuardrailRejectedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderNotConfiguredException;
import com.myfinaimanager.core.ai.domain.exceptions.AiStructuredOutputInvalidException;
import com.myfinaimanager.core.ai.domain.exceptions.AiTimeoutException;
import com.myfinaimanager.core.ai.domain.exceptions.AiTokenBudgetExceededException;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.OutputSchema;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldType;
import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisFailedException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisContext;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskSeverity;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskType;
import com.myfinaimanager.core.portfolioanalysis.domain.ports.PortfolioAnalysisAiPort;

/**
 * The <strong>sole</strong> class touching {@code com.myfinaimanager.core.ai.*} (ArchUnit-enforced
 * — AR-062; contract {@code portfolio-analysis-ports.md} C2). Builds one {@link AiRequest}
 * (task {@code "portfolio-analysis"}) per call, invokes {@link GenerateAiUseCase#generate} exactly
 * once (Q1, Q4 — never retries; EN006's own policy already did), and maps the result — or any
 * {@code AiException} — into this module's own vocabulary. No {@code ai.*} type ever crosses this
 * port (Q2).
 */
@Component
public class PortfolioAnalysisAiAdapter implements PortfolioAnalysisAiPort {

    private static final String TASK_TYPE = "portfolio-analysis";
    // The task's own promptId/promptVersion (research D2) — this adapter is dedicated to exactly
    // one task, so it statically knows which prompt resource it uses; AiResponse carries no
    // promptId/promptVersion field (EN006's response shape is provider metadata only).
    private static final String PROMPT_ID = "portfolio-analysis";
    private static final String PROMPT_VERSION = "v1";
    private static final String USER_PROMPT =
            "Analyze the Portfolio described below and produce the required JSON output.";
    private static final int MAX_OUTPUT_TOKENS = 800;

    private static final OutputSchema SCHEMA = new OutputSchema(List.of(
            new FieldSpec("overallDiversification", FieldType.OBJECT, true),
            new FieldSpec("keyInsights", FieldType.ARRAY, true),
            new FieldSpec("risks", FieldType.ARRAY, true)));

    private final GenerateAiUseCase generateAi;

    public PortfolioAnalysisAiAdapter(GenerateAiUseCase generateAi) {
        this.generateAi = generateAi;
    }

    @Override
    public PortfolioAnalysisResult analyze(PortfolioAnalysisContext context, String correlationId) {
        AiRequest request = AiRequest.of(
                TASK_TYPE, USER_PROMPT, context.text(), Optional.of(SCHEMA), MAX_OUTPUT_TOKENS, correlationId);

        AiResponse response;
        try {
            response = generateAi.generate(request);
        } catch (AiProviderNotConfiguredException e) {
            throw failed(FailureReason.NOT_CONFIGURED, e);
        } catch (AiTimeoutException e) {
            throw failed(FailureReason.TIMEOUT, e);
        } catch (AiGuardrailRejectedException e) {
            throw failed(FailureReason.GUARDRAIL_REJECTED, e);
        } catch (AiStructuredOutputInvalidException e) {
            throw failed(FailureReason.INVALID_OUTPUT, e);
        } catch (AiTokenBudgetExceededException | AiCostBudgetExceededException | AiConfigurationErrorException e) {
            throw failed(FailureReason.UNKNOWN, e);
        } catch (AiException e) {
            // AiProviderUnavailableException, AiProviderRateLimitedException,
            // AiProviderAuthenticationFailedException, AiRequestTooLargeException,
            // AiInvalidResponseException — all normalize to the same Investor-facing outcome (Q3).
            throw failed(FailureReason.PROVIDER_UNAVAILABLE, e);
        }

        try {
            return toResult(response);
        } catch (RuntimeException e) {
            // The top-level shape was already EN006-validated; a malformed *nested* field (an
            // insight/risk object missing a key, an unrecognized enum value) is still possible and
            // is this adapter's own responsibility to catch (Q2) — never let it escape as a raw
            // parsing exception.
            throw failed(FailureReason.INVALID_OUTPUT, e);
        }
    }

    private AnalysisFailedException failed(FailureReason reason, Exception cause) {
        return new AnalysisFailedException(reason, cause.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private PortfolioAnalysisResult toResult(AiResponse response) {
        Map<String, Object> content = response.structuredContent()
                .orElseThrow(() -> new IllegalStateException("structured content absent despite a requested schema"));

        Map<String, Object> overall = (Map<String, Object>) content.get("overallDiversification");
        DiversificationLevel level = DiversificationLevel.valueOf(String.valueOf(overall.get("level")));
        String explanation = String.valueOf(overall.get("explanation"));

        List<PortfolioAnalysis.Insight> insights = new ArrayList<>();
        List<Object> rawInsights = (List<Object>) content.get("keyInsights");
        for (int i = 0; i < rawInsights.size(); i++) {
            Map<String, Object> raw = (Map<String, Object>) rawInsights.get(i);
            insights.add(new PortfolioAnalysis.Insight(
                    String.valueOf(raw.get("type")), String.valueOf(raw.get("message")), i));
        }

        List<PortfolioAnalysis.Risk> risks = new ArrayList<>();
        List<Object> rawRisks = (List<Object>) content.get("risks");
        for (int i = 0; i < rawRisks.size(); i++) {
            Map<String, Object> raw = (Map<String, Object>) rawRisks.get(i);
            risks.add(new PortfolioAnalysis.Risk(
                    RiskType.valueOf(String.valueOf(raw.get("type"))),
                    RiskSeverity.valueOf(String.valueOf(raw.get("severity"))),
                    String.valueOf(raw.get("title")), String.valueOf(raw.get("explanation")), i));
        }

        return new PortfolioAnalysisResult(
                level, explanation, insights, risks, response.provider(), response.model(),
                PROMPT_ID, PROMPT_VERSION, response.usage().inputTokens(), response.usage().outputTokens(),
                response.usage().totalTokens(), response.usage().estimatedCost());
    }
}
