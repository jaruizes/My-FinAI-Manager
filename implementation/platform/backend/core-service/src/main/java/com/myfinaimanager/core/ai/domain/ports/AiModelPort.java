package com.myfinaimanager.core.ai.domain.ports;

import com.myfinaimanager.core.ai.domain.exceptions.AiException;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;

/**
 * The one generic, provider-neutral model-invocation port (FR-001; contract {@code ai-model-port.md}
 * C1). EN006 ships exactly one implementation — a deterministic local/stub adapter (resolved Q1,
 * FR-005) — but any future real provider adapter implements this same interface with zero change
 * to {@link com.myfinaimanager.core.ai.business.AiInvocationPolicy} (FR-006, VC-003).
 */
public interface AiModelPort {

    /**
     * @param request a fully-resolved request (system prompt composed, context budgeted) — the
     *                caller (business layer) has already run every pre-flight check
     * @return a fully-populated, provider-neutral response
     * @throws AiException one of the eleven provider-neutral failures — never a provider-specific
     *                      exception, never an unmapped infrastructure exception (contract C1, P3)
     */
    AiResponse generate(AiRequest request);
}
