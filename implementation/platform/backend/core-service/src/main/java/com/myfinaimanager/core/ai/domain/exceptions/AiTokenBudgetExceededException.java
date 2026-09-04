package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * The estimated token usage exceeds {@code ai.limits.max-*-tokens} <strong>before</strong> the
 * adapter is invoked (FR-028). Never retried (FR-037).
 */
public class AiTokenBudgetExceededException extends AiException {

    public AiTokenBudgetExceededException(String message) {
        super(message);
    }
}
