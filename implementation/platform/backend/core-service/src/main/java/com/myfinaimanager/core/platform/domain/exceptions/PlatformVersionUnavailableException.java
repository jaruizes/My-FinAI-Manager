package com.myfinaimanager.core.platform.domain.exceptions;

/**
 * Raised when the authoritative platform version cannot be retrieved from
 * persistence. EN001 BR-001 / AC-008: the bootstrap {@code hello} capability
 * must fail explicitly rather than fabricate a version.
 */
public class PlatformVersionUnavailableException extends RuntimeException {

    public PlatformVersionUnavailableException(String message) {
        super(message);
    }

    public PlatformVersionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
