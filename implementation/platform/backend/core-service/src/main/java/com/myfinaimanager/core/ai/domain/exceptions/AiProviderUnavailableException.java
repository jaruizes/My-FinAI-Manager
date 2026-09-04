package com.myfinaimanager.core.ai.domain.exceptions;

/** The adapter call failed for a transient/infrastructure reason (FR-034). Retried, bounded (FR-037). */
public class AiProviderUnavailableException extends AiException {

    public AiProviderUnavailableException(String message) {
        super(message);
    }
}
