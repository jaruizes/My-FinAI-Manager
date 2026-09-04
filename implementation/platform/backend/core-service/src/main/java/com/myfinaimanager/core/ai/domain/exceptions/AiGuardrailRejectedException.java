package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * An input or output guardrail rejected the request/response (FR-024). Never retried — a
 * guardrail rejection is a policy decision, not a transient failure (FR-037).
 */
public class AiGuardrailRejectedException extends AiException {

    private final String guardrailName;

    public AiGuardrailRejectedException(String guardrailName, String reason) {
        super("guardrail '" + guardrailName + "' rejected: " + reason);
        this.guardrailName = guardrailName;
    }

    public String guardrailName() {
        return guardrailName;
    }
}
