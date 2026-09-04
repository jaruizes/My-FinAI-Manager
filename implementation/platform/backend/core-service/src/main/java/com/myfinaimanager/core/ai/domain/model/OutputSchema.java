package com.myfinaimanager.core.ai.domain.model;

import java.util.List;

/**
 * A minimal, dependency-free structured-output contract (FR-017; research D3/OD-6). Deliberately
 * not a full JSON Schema implementation — EN006 ships no live AI provider (resolved Q1), so the
 * only structured content it ever validates is the local/stub adapter's own conforming output and
 * hand-built test fixtures. A future real provider adapter may introduce a full JSON Schema
 * validator without changing this type's shape.
 *
 * @param fields the required/optional field specifications a conforming response must satisfy
 */
public record OutputSchema(List<FieldSpec> fields) {

    public OutputSchema {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        fields = List.copyOf(fields);
    }

    /**
     * One field of a structured response.
     *
     * @param name     the field's key in the response map
     * @param type     the expected value type
     * @param required whether the field must be present
     */
    public record FieldSpec(String name, FieldType type, boolean required) {

        public FieldSpec {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
        }
    }

    /** The value types a structured response field may take. */
    public enum FieldType {
        STRING, NUMBER, BOOLEAN, ARRAY, OBJECT
    }
}
