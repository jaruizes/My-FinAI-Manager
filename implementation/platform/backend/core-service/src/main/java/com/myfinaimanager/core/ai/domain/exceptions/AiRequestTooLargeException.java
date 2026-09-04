package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * The request exceeds a provider-side size limit (FR-034) — distinct from the pre-flight
 * {@link AiTokenBudgetExceededException} check, which runs before any provider call. Never retried.
 */
public class AiRequestTooLargeException extends AiException {

    public AiRequestTooLargeException(String message) {
        super(message);
    }
}
