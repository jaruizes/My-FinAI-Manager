package com.myfinaimanager.core.ai.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.business.GenerateAiUseCase;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.AiUsage;

/**
 * Contract {@code ai-diagnostic-endpoint.md} — internal, non-business tooling (FR-054; VC-024).
 * Verified at the unit level (the endpoint's own logic — field selection, no leakage) rather than
 * through a full MockMvc/web slice, which adds no further guarantee for a bean this thin.
 */
class AiDiagnosticEndpointTest {

    @Test
    void returns_only_requestId_latency_and_totalTokens_never_the_prompt_or_content() {
        GenerateAiUseCase useCase = mock(GenerateAiUseCase.class);
        AiUsage usage = new AiUsage(20, 12, 32, "local", "local-deterministic-v1", BigDecimal.ZERO);
        AiResponse response = new AiResponse(
                "this is the full generated content and must never leak",
                Optional.empty(), "local", "local-deterministic-v1", usage, 7, Optional.of("stop"),
                "req-42", Instant.now());
        when(useCase.generate(any(AiRequest.class))).thenReturn(response);
        AiDiagnosticEndpoint endpoint = new AiDiagnosticEndpoint(useCase);

        AiDiagnosticResult result = endpoint.run();

        assertThat(result.requestId()).isEqualTo("req-42");
        assertThat(result.latencyMs()).isEqualTo(7);
        assertThat(result.totalTokens()).isEqualTo(32);
        assertThat(result.toString()).doesNotContain("full generated content");
    }

    @Test
    void invokes_the_use_case_with_the_fixed_diagnostic_request() {
        GenerateAiUseCase useCase = mock(GenerateAiUseCase.class);
        AiUsage usage = new AiUsage(1, 1, 2, "local", "m", BigDecimal.ZERO);
        when(useCase.generate(any(AiRequest.class))).thenReturn(new AiResponse(
                "x", Optional.empty(), "local", "m", usage, 1, Optional.of("stop"), "req-1", Instant.now()));
        AiDiagnosticEndpoint endpoint = new AiDiagnosticEndpoint(useCase);

        endpoint.run();

        org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        org.mockito.Mockito.verify(useCase).generate(captor.capture());
        assertThat(captor.getValue().taskType()).isEqualTo("diagnostic");
    }
}
