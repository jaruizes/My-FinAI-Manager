package com.myfinaimanager.core.ai.domain.ports;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.GuardrailOutcome;

/**
 * The mandatory output-guardrail extension point (FR-020), invoked <strong>after</strong>
 * {@link AiModelPort#generate} but before the response reaches the caller. The shipped
 * implementation is rule-based only (resolved Q2, research D5).
 */
public interface OutputGuardrailPort {

    GuardrailOutcome check(AiRequest request, AiResponse response);
}
