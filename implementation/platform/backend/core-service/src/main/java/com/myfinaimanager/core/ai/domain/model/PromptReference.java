package com.myfinaimanager.core.ai.domain.model;

/**
 * A governed, versioned prompt (enabler §10–§12; FR-013, FR-014). {@code body} is the resolved
 * prompt text for this exact {@code promptId}/{@code promptVersion} pair — never an arbitrary
 * string literal built ad hoc by a caller.
 *
 * @param promptId      stable identifier, e.g. {@code "global-system"}
 * @param promptVersion version within that id, e.g. {@code "v1"}
 * @param body          the resolved prompt text
 */
public record PromptReference(String promptId, String promptVersion, String body) {

    public PromptReference {
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("promptId must not be blank");
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion must not be blank");
        }
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
    }
}
