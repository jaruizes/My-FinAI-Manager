package com.myfinaimanager.core.ai.domain.exceptions;

/** The adapter reports a rate-limit condition (FR-034). Retried, bounded (FR-037). */
public class AiProviderRateLimitedException extends AiException {

    public AiProviderRateLimitedException(String message) {
        super(message);
    }
}
