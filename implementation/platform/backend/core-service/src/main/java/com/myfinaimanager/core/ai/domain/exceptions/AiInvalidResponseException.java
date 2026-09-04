package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * The adapter returned a structurally invalid {@code AiResponse} — a defensive check, not expected
 * from {@code LocalAiModelAdapter} in normal operation (FR-034). Never retried.
 */
public class AiInvalidResponseException extends AiException {

    public AiInvalidResponseException(String message) {
        super(message);
    }
}
