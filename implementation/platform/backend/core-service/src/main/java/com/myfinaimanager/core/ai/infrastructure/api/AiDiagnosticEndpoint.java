package com.myfinaimanager.core.ai.infrastructure.api;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.business.GenerateAiUseCase;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;

/**
 * Internal, non-business Actuator endpoint (contract {@code ai-diagnostic-endpoint.md}; research D6;
 * plan.md OD-9) — triggers one deterministic AI invocation so a human/script can prove the
 * observability chain (FR-054) against a running {@code ./start.sh} instance. NOT part of
 * {@code openapi.yaml}; not a business capability (FR-059).
 */
@Component
@Endpoint(id = "aidiagnostic")
public class AiDiagnosticEndpoint {

    private final GenerateAiUseCase generateAiUseCase;

    public AiDiagnosticEndpoint(GenerateAiUseCase generateAiUseCase) {
        this.generateAiUseCase = generateAiUseCase;
    }

    @WriteOperation
    public AiDiagnosticResult run() {
        AiResponse response = generateAiUseCase.generate(AiRequest.diagnostic());
        return new AiDiagnosticResult(response.requestId(), response.latencyMs(), response.usage().totalTokens());
    }
}
