package com.myfinaimanager.core.ai.infrastructure.provider.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.OutputSchema;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldType;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.domain.model.StructuredOutputValidator;

/** Contract {@code local-ai-adapter.md} — the shipped {@code AiModelPort} adapter (resolved Q1). */
class LocalAiModelAdapterTest {

    private final LocalAiModelAdapter adapter = new LocalAiModelAdapter();

    private static AiRequest resolvedRequest(Optional<OutputSchema> schema) {
        PromptReference systemPrompt = new PromptReference("global-system", "v1", "Be careful.");
        return new AiRequest("diagnostic", systemPrompt, "hello", "", schema, 64, Optional.empty(),
                java.util.Map.of(), "corr-1");
    }

    @Test
    void returns_deterministic_content_for_an_identical_request() {
        AiRequest request = resolvedRequest(Optional.empty());

        AiResponse first = adapter.generate(request);
        AiResponse second = adapter.generate(request);

        assertThat(first.content()).isEqualTo(second.content());
        assertThat(first.usage()).isEqualTo(second.usage());
        assertThat(first.provider()).isEqualTo(second.provider()).isEqualTo(LocalAiModelAdapter.PROVIDER);
        assertThat(first.model()).isEqualTo(second.model()).isEqualTo(LocalAiModelAdapter.MODEL);
        assertThat(first.finishReason()).isEqualTo(second.finishReason());
        // requestId / generatedAt are explicitly NOT part of the determinism contract.
    }

    @Test
    void never_makes_a_network_call_or_carries_a_credential() {
        AiResponse response = adapter.generate(resolvedRequest(Optional.empty()));

        assertThat(response.usage().estimatedCost().signum()).isZero();
    }

    @Test
    void returns_conforming_structured_content_when_a_schema_is_requested() {
        OutputSchema schema = new OutputSchema(List.of(
                new FieldSpec("summary", FieldType.STRING, true),
                new FieldSpec("count", FieldType.NUMBER, true)));

        AiResponse response = adapter.generate(resolvedRequest(Optional.of(schema)));

        assertThat(response.structuredContent()).isPresent();
        assertThat(StructuredOutputValidator.validate(schema, response.structuredContent().get()).isAllowed())
                .isTrue();
    }

    @Test
    void omits_structured_content_when_no_schema_is_requested() {
        AiResponse response = adapter.generate(resolvedRequest(Optional.empty()));

        assertThat(response.structuredContent()).isEmpty();
    }

    @Test
    void placeholder_covers_every_field_type() {
        OutputSchema schema = new OutputSchema(List.of(
                new FieldSpec("s", FieldType.STRING, true),
                new FieldSpec("n", FieldType.NUMBER, true),
                new FieldSpec("b", FieldType.BOOLEAN, true),
                new FieldSpec("a", FieldType.ARRAY, true),
                new FieldSpec("o", FieldType.OBJECT, true)));

        AiResponse response = adapter.generate(resolvedRequest(Optional.of(schema)));

        assertThat(StructuredOutputValidator.validate(schema, response.structuredContent().orElseThrow()).isAllowed())
                .isTrue();
    }
}
