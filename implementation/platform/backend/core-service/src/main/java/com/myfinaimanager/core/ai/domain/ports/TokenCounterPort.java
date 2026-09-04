package com.myfinaimanager.core.ai.domain.ports;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.TokenUsageEstimate;

/**
 * Pre-invocation token estimation (FR-027). The shipped implementation is a simple heuristic
 * (research D4) — this port is exactly the seam a future provider-specific tokenizer replaces it
 * with, without touching {@code AiInvocationPolicy}.
 */
public interface TokenCounterPort {

    TokenUsageEstimate estimate(AiRequest request);
}
