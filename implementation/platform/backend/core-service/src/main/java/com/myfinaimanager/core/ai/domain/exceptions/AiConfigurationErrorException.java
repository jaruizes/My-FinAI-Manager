package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * An unknown {@code taskType}/{@code promptId}/{@code promptVersion} was requested, or {@code ai.*}
 * configuration is otherwise invalid (FR-014). Never falls back silently to a different prompt or
 * setting. Never retried.
 */
public class AiConfigurationErrorException extends AiException {

    public AiConfigurationErrorException(String message) {
        super(message);
    }
}
