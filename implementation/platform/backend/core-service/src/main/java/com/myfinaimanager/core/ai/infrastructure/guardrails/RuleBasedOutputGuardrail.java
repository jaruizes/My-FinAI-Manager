package com.myfinaimanager.core.ai.infrastructure.guardrails;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.GuardrailOutcome;
import com.myfinaimanager.core.ai.domain.ports.OutputGuardrailPort;

/**
 * Rule-based-only output guardrail (resolved Q2; research D5). Structured-schema conformance is
 * validated separately by {@code AiInvocationPolicy} via {@code StructuredOutputValidator} — this
 * guardrail covers the prohibited-action-language rule from enabler §16.
 */
@Component
public class RuleBasedOutputGuardrail implements OutputGuardrailPort {

    /** Case-insensitive prohibited-action phrases (research D5). */
    private static final List<String> PROHIBITED_ACTION_PHRASES = List.of(
            "i have sold",
            "i have purchased",
            "i have executed",
            "transaction complete",
            "order placed");

    @Override
    public GuardrailOutcome check(AiRequest request, AiResponse response) {
        String content = response.content().toLowerCase(Locale.ROOT);
        for (String phrase : PROHIBITED_ACTION_PHRASES) {
            if (content.contains(phrase)) {
                return GuardrailOutcome.rejected(
                        "output-prohibited-action", "response claims an executed financial action");
            }
        }
        return GuardrailOutcome.allowed();
    }
}
