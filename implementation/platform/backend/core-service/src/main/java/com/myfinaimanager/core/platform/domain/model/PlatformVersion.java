package com.myfinaimanager.core.platform.domain.model;

/**
 * The current executable application version, as returned by the bootstrap
 * {@code hello} capability. A non-empty text identifier; see EN001 BR-001 —
 * this value must originate from persistence and never be fabricated.
 */
public record PlatformVersion(String value) {

    public PlatformVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Platform version must be a non-empty identifier");
        }
        value = value.strip();
    }

    public static PlatformVersion of(String value) {
        return new PlatformVersion(value);
    }
}
