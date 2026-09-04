package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * The estimated cost exceeds {@code ai.limits.max-estimated-cost} <strong>before</strong> the
 * adapter is invoked (FR-031). Never retried (FR-037).
 */
public class AiCostBudgetExceededException extends AiException {

    public AiCostBudgetExceededException(String message) {
        super(message);
    }
}
