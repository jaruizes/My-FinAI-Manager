package com.myfinaimanager.core.ai.domain.ports;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.GuardrailOutcome;

/**
 * The mandatory input-guardrail extension point (FR-020), invoked <strong>before</strong>
 * {@link AiModelPort#generate}. The shipped implementation is rule-based only (resolved Q2,
 * research D5); a future AI-based or provider-native guardrail may complement it without replacing
 * this port (FR-021).
 */
public interface InputGuardrailPort {

    GuardrailOutcome check(AiRequest request);
}
