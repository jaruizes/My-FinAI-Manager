package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * Base type for every EN006 failure (FR-034). The message is always provider-neutral and safe to
 * log/trace — no provider payload, no credential, no raw prompt/response text ever appears here.
 */
public abstract class AiException extends RuntimeException {

    protected AiException(String message) {
        super(message);
    }
}
