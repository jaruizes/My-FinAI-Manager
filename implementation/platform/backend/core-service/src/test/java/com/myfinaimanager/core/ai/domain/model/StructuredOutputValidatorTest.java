package com.myfinaimanager.core.ai.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldType;

/** FR-017–FR-019; research D3. Pure, dependency-free structured-output validation. */
class StructuredOutputValidatorTest {

    private static final OutputSchema SCHEMA = new OutputSchema(List.of(
            new FieldSpec("summary", FieldType.STRING, true),
            new FieldSpec("count", FieldType.NUMBER, true),
            new FieldSpec("note", FieldType.STRING, false)));

    @Test
    void conforming_candidate_is_allowed() {
        Map<String, Object> candidate = Map.of("summary", "ok", "count", 3);

        assertThat(StructuredOutputValidator.validate(SCHEMA, candidate).isAllowed()).isTrue();
    }

    @Test
    void missing_required_field_is_rejected() {
        Map<String, Object> candidate = Map.of("count", 3);

        GuardrailOutcome outcome = StructuredOutputValidator.validate(SCHEMA, candidate);

        assertThat(outcome.isAllowed()).isFalse();
        assertThat(((GuardrailOutcome.Rejected) outcome).reason()).contains("summary");
    }

    @Test
    void wrong_typed_field_is_rejected() {
        Map<String, Object> candidate = Map.of("summary", "ok", "count", "not-a-number");

        GuardrailOutcome outcome = StructuredOutputValidator.validate(SCHEMA, candidate);

        assertThat(outcome.isAllowed()).isFalse();
        assertThat(((GuardrailOutcome.Rejected) outcome).reason()).contains("count");
    }

    @Test
    void missing_optional_field_still_allowed() {
        Map<String, Object> candidate = Map.of("summary", "ok", "count", 1);

        assertThat(StructuredOutputValidator.validate(SCHEMA, candidate).isAllowed()).isTrue();
    }

    @Test
    void null_candidate_is_rejected() {
        assertThat(StructuredOutputValidator.validate(SCHEMA, null).isAllowed()).isFalse();
    }

    @Test
    void array_and_object_typed_fields_are_validated() {
        OutputSchema schema = new OutputSchema(List.of(
                new FieldSpec("items", FieldType.ARRAY, true),
                new FieldSpec("meta", FieldType.OBJECT, true),
                new FieldSpec("flag", FieldType.BOOLEAN, true)));
        Map<String, Object> conforming = Map.of("items", List.of("a"), "meta", Map.of("k", "v"), "flag", true);

        assertThat(StructuredOutputValidator.validate(schema, conforming).isAllowed()).isTrue();
    }

    @Test
    void wrong_typed_array_field_is_rejected() {
        OutputSchema schema = new OutputSchema(List.of(new FieldSpec("items", FieldType.ARRAY, true)));

        assertThat(StructuredOutputValidator.validate(schema, Map.of("items", "not-a-list")).isAllowed())
                .isFalse();
    }

    @Test
    void wrong_typed_object_field_is_rejected() {
        OutputSchema schema = new OutputSchema(List.of(new FieldSpec("meta", FieldType.OBJECT, true)));

        assertThat(StructuredOutputValidator.validate(schema, Map.of("meta", "not-a-map")).isAllowed())
                .isFalse();
    }

    @Test
    void wrong_typed_boolean_field_is_rejected() {
        OutputSchema schema = new OutputSchema(List.of(new FieldSpec("flag", FieldType.BOOLEAN, true)));

        assertThat(StructuredOutputValidator.validate(schema, Map.of("flag", "yes")).isAllowed()).isFalse();
    }
}
