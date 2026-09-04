package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * The response's structured content does not conform to the requested {@code OutputSchema}
 * (FR-019). Never retried — the schema mismatch will not resolve itself on a retry.
 */
public class AiStructuredOutputInvalidException extends AiException {

    public AiStructuredOutputInvalidException(String message) {
        super(message);
    }
}
