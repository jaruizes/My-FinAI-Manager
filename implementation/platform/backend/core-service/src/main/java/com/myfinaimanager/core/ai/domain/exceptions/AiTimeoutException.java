package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * A call exceeded {@code ai.timeout.read} (FR-036). Never retried — the call already consumed its
 * full allotted time; retrying blindly would make total latency unpredictable (contract
 * {@code ai-model-port.md} C2, Q5/Q6).
 */
public class AiTimeoutException extends AiException {

    public AiTimeoutException(String message) {
        super(message);
    }
}
