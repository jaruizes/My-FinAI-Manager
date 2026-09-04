package com.myfinaimanager.core.ai.domain.model;

import java.util.List;
import java.util.Map;

import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;

/**
 * Validates a candidate structured response against an {@link OutputSchema} (FR-017–FR-019;
 * research D3). Pure, deterministic, dependency-free — no JSON library, no reflection — matching
 * the project's convention for pure domain calculators (e.g. {@code PortfolioValuationCalculator}
 * in the {@code portfolio} module).
 */
public final class StructuredOutputValidator {

    private StructuredOutputValidator() {
    }

    /**
     * @param schema    the required shape
     * @param candidate the structured content to check
     * @return {@link GuardrailOutcome#allowed()} when every required field is present with a
     *     matching type; otherwise a {@link GuardrailOutcome.Rejected} naming the first violation
     */
    public static GuardrailOutcome validate(OutputSchema schema, Map<String, Object> candidate) {
        if (candidate == null) {
            return GuardrailOutcome.rejected("structured-schema", "structured content is absent");
        }
        for (FieldSpec field : schema.fields()) {
            Object value = candidate.get(field.name());
            if (value == null) {
                if (field.required()) {
                    return GuardrailOutcome.rejected(
                            "structured-schema", "missing required field '" + field.name() + "'");
                }
                continue;
            }
            if (!matchesType(value, field.type())) {
                return GuardrailOutcome.rejected(
                        "structured-schema",
                        "field '" + field.name() + "' expected " + field.type() + " but was "
                                + value.getClass().getSimpleName());
            }
        }
        return GuardrailOutcome.allowed();
    }

    private static boolean matchesType(Object value, OutputSchema.FieldType type) {
        return switch (type) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof List<?>;
            case OBJECT -> value instanceof Map<?, ?>;
        };
    }
}
