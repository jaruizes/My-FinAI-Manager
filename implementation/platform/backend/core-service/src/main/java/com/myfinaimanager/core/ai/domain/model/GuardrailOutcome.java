package com.myfinaimanager.core.ai.domain.model;

/**
 * The result of one guardrail / structured-output check (FR-020, FR-024). A {@link Rejected}
 * outcome always identifies which check rejected and why — never a raw exception, never a partial
 * result.
 */
public sealed interface GuardrailOutcome {

    static GuardrailOutcome allowed() {
        return Allowed.INSTANCE;
    }

    static GuardrailOutcome rejected(String guardrailName, String reason) {
        return new Rejected(guardrailName, reason);
    }

    boolean isAllowed();

    /** The check passed. */
    record Allowed() implements GuardrailOutcome {
        static final Allowed INSTANCE = new Allowed();

        @Override
        public boolean isAllowed() {
            return true;
        }
    }

    /**
     * The check rejected the input/output.
     *
     * @param guardrailName which check rejected (e.g. {@code "input-size"}, {@code "structured-schema"})
     * @param reason        a human-readable, provider-neutral explanation
     */
    record Rejected(String guardrailName, String reason) implements GuardrailOutcome {

        public Rejected {
            if (guardrailName == null || guardrailName.isBlank()) {
                throw new IllegalArgumentException("guardrailName must not be blank");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }

        @Override
        public boolean isAllowed() {
            return false;
        }
    }
}
