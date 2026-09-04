package com.myfinaimanager.core.ai.domain.exceptions;

/** The adapter reports an authentication failure (FR-034). Never retried (FR-037). */
public class AiProviderAuthenticationFailedException extends AiException {

    public AiProviderAuthenticationFailedException(String message) {
        super(message);
    }
}
