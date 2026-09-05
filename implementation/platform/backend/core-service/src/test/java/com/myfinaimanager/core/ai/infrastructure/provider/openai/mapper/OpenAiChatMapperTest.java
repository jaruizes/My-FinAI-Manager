package com.myfinaimanager.core.ai.infrastructure.provider.openai.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinaimanager.core.ai.domain.exceptions.AiInvalidResponseException;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.OutputSchema;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldType;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.config.OpenAiProperties;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatRequest;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatResponse;

/** Pure mapping logic — no HTTP (contract {@code openai-provider-contract.md}). */
class OpenAiChatMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");

    private static OpenAiProperties props() {
        return new OpenAiProperties(
                "key", URI.create("https://api.openai.test/v1"), "gpt-4o-mini",
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new OpenAiProperties.Pricing(new BigDecimal("0.00015"), new BigDecimal("0.0006")));
    }

    private static OpenAiChatMapper mapper() {
        return new OpenAiChatMapper(new ObjectMapper(), props());
    }

    private static AiRequest request(Optional<OutputSchema> outputSchema, String context) {
        PromptReference systemPrompt = new PromptReference("portfolio-analysis", "v1", "Be careful.");
        return new AiRequest(
                "portfolio-analysis", systemPrompt, "Assess this portfolio.", context, outputSchema, 300,
                Optional.empty(), Map.of(), "corr-1");
    }

    // ---- toRequest --------------------------------------------------------------------------

    @Test
    void toRequest_uses_the_configured_model_and_a_fixed_json_object_response_format() {
        OpenAiChatRequest request = mapper().toRequest(request(Optional.empty(), ""));

        assertThat(request.model()).isEqualTo("gpt-4o-mini");
        assertThat(request.responseFormat().type()).isEqualTo("json_object");
        assertThat(request.maxTokens()).isEqualTo(300);
    }

    @Test
    void toRequest_carries_the_composed_system_prompt_and_the_user_prompt_plus_context() {
        OpenAiChatRequest request = mapper().toRequest(request(Optional.empty(), "Positions: AAPL 100%."));

        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo("system");
        assertThat(request.messages().get(0).content()).isEqualTo("Be careful.");
        assertThat(request.messages().get(1).role()).isEqualTo("user");
        assertThat(request.messages().get(1).content()).isEqualTo("Assess this portfolio.\n\nPositions: AAPL 100%.");
    }

    @Test
    void toRequest_omits_the_blank_line_separator_when_context_is_blank() {
        OpenAiChatRequest request = mapper().toRequest(request(Optional.empty(), ""));

        assertThat(request.messages().get(1).content()).isEqualTo("Assess this portfolio.");
    }

    @Test
    void toRequest_defaults_temperature_to_0_2_when_the_caller_did_not_specify_one() {
        OpenAiChatRequest request = mapper().toRequest(request(Optional.empty(), ""));

        assertThat(request.temperature()).isEqualTo(0.2);
    }

    // ---- toResponse --------------------------------------------------------------------------

    private static OpenAiChatResponse response(String id, String content, String finishReason,
                                                OpenAiChatResponse.Usage usage) {
        return new OpenAiChatResponse(
                id, List.of(new OpenAiChatResponse.Choice(new OpenAiChatResponse.Message("assistant", content),
                        finishReason)),
                usage);
    }

    @Test
    void toResponse_maps_usage_and_computes_estimated_cost() {
        OpenAiChatResponse.Usage usage = new OpenAiChatResponse.Usage(10, 5, 15);
        AiResponse result = mapper().toResponse(
                response("chatcmpl-1", "All good.", "stop", usage), request(Optional.empty(), ""), 42, NOW);

        assertThat(result.usage().inputTokens()).isEqualTo(10);
        assertThat(result.usage().outputTokens()).isEqualTo(5);
        assertThat(result.usage().totalTokens()).isEqualTo(15);
        // 10/1000*0.00015 = 0.0000015 -> rounds to 0.000002 at scale 6; 5/1000*0.0006 = 0.000003 (exact); sum 0.000005
        assertThat(result.usage().estimatedCost()).isEqualByComparingTo("0.000005");
        assertThat(result.provider()).isEqualTo("openai");
        assertThat(result.model()).isEqualTo("gpt-4o-mini");
        assertThat(result.latencyMs()).isEqualTo(42);
        assertThat(result.generatedAt()).isEqualTo(NOW);
        assertThat(result.requestId()).isEqualTo("chatcmpl-1");
        assertThat(result.finishReason()).contains("stop");
    }

    @Test
    void toResponse_falls_back_to_a_generated_requestId_when_the_provider_id_is_blank() {
        AiResponse result = mapper().toResponse(
                response("", "ok", "stop", new OpenAiChatResponse.Usage(1, 1, 2)),
                request(Optional.empty(), ""), 1, NOW);

        assertThat(result.requestId()).startsWith("openai-");
    }

    @Test
    void toResponse_treats_a_missing_usage_block_as_zero_tokens() {
        AiResponse result = mapper().toResponse(
                response("chatcmpl-2", "ok", "stop", null), request(Optional.empty(), ""), 1, NOW);

        assertThat(result.usage().inputTokens()).isZero();
        assertThat(result.usage().outputTokens()).isZero();
        assertThat(result.usage().totalTokens()).isZero();
        assertThat(result.usage().estimatedCost()).isEqualByComparingTo("0");
    }

    @Test
    void toResponse_parses_conforming_json_content_into_structuredContent_when_a_schema_was_requested() {
        OutputSchema schema = new OutputSchema(List.of(new FieldSpec("summary", FieldType.STRING, true)));
        AiResponse result = mapper().toResponse(
                response("chatcmpl-3", "{\"summary\":\"ok\"}", "stop", new OpenAiChatResponse.Usage(1, 1, 2)),
                request(Optional.of(schema), ""), 1, NOW);

        assertThat(result.structuredContent()).isPresent();
        assertThat(result.structuredContent().get()).containsEntry("summary", "ok");
        assertThat(result.content()).isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void toResponse_leaves_structuredContent_empty_when_no_schema_was_requested() {
        AiResponse result = mapper().toResponse(
                response("chatcmpl-4", "{\"summary\":\"ok\"}", "stop", new OpenAiChatResponse.Usage(1, 1, 2)),
                request(Optional.empty(), ""), 1, NOW);

        assertThat(result.structuredContent()).isEmpty();
    }

    @Test
    void toResponse_raises_invalid_response_when_content_is_not_json_but_a_schema_was_requested() {
        OutputSchema schema = new OutputSchema(List.of(new FieldSpec("summary", FieldType.STRING, true)));

        assertThatThrownBy(() -> mapper().toResponse(
                response("chatcmpl-5", "not json", "stop", new OpenAiChatResponse.Usage(1, 1, 2)),
                request(Optional.of(schema), ""), 1, NOW))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void toResponse_raises_invalid_response_when_the_message_content_is_null() {
        OpenAiChatResponse response = new OpenAiChatResponse(
                "chatcmpl-6",
                List.of(new OpenAiChatResponse.Choice(new OpenAiChatResponse.Message("assistant", null), "stop")),
                new OpenAiChatResponse.Usage(1, 1, 2));

        assertThatThrownBy(() -> mapper().toResponse(response, request(Optional.empty(), ""), 1, NOW))
                .isInstanceOf(AiInvalidResponseException.class);
    }
}
