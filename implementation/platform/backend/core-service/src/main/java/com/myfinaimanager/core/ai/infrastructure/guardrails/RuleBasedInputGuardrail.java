package com.myfinaimanager.core.ai.infrastructure.guardrails;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.GuardrailOutcome;
import com.myfinaimanager.core.ai.domain.ports.InputGuardrailPort;

/**
 * Rule-based-only input guardrail (resolved Q2; research D5). A small, explicit, code-level rule
 * set — not a configurable rule engine (over-engineering for EN006's no-live-provider scope); a
 * future AI-based or provider-native guardrail complements, never replaces, this port (FR-021).
 */
@Component
public class RuleBasedInputGuardrail implements InputGuardrailPort {

    /** Case-insensitive system-prompt-override / injection patterns (research D5). */
    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore the system prompt",
            "disregard the above",
            "you are now",
            "new instructions:");

    private final AiInvocationSettings settings;

    public RuleBasedInputGuardrail(AiInvocationSettings settings) {
        this.settings = settings;
    }

    @Override
    public GuardrailOutcome check(AiRequest request) {
        int size = request.userPrompt().length() + request.context().length();
        if (size > settings.limits().maxInputCharacters()) {
            return GuardrailOutcome.rejected("input-size", "input exceeds the maximum allowed size");
        }
        String haystack = (request.userPrompt() + " " + request.context()).toLowerCase(Locale.ROOT);
        for (String pattern : INJECTION_PATTERNS) {
            if (haystack.contains(pattern)) {
                return GuardrailOutcome.rejected(
                        "input-injection", "possible prompt injection / system-prompt override detected");
            }
        }
        return GuardrailOutcome.allowed();
    }
}
