package com.myfinaimanager.core.ai.infrastructure.provider.openai.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinaimanager.core.ai.domain.exceptions.AiInvalidResponseException;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.AiUsage;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.config.OpenAiProperties;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatRequest;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatResponse;

/**
 * Maps a provider-neutral {@link AiRequest} to an {@link OpenAiChatRequest}, and an
 * {@link OpenAiChatResponse} back to a provider-neutral {@link AiResponse} (contract
 * {@code openai-provider-contract.md}). When {@link AiRequest#outputSchema()} is present, the
 * completion content is additionally parsed as JSON into {@code structuredContent} — EN006's own
 * {@code StructuredOutputValidator} then validates it, unchanged. A default temperature of
 * {@code 0.2} is used when the caller does not specify one (the contract's own illustrative
 * value) — favors grounded, low-variance output for factual analysis tasks.
 */
@Component
public class OpenAiChatMapper {

    private static final double DEFAULT_TEMPERATURE = 0.2;
    private static final int COST_SCALE = 6;

    private final ObjectMapper objectMapper;
    private final OpenAiProperties properties;

    public OpenAiChatMapper(ObjectMapper objectMapper, OpenAiProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public OpenAiChatRequest toRequest(AiRequest request) {
        List<OpenAiChatRequest.Message> messages = List.of(
                new OpenAiChatRequest.Message("system", request.systemPrompt().body()),
                new OpenAiChatRequest.Message("user", composeUserContent(request)));
        return new OpenAiChatRequest(
                properties.model(),
                messages,
                OpenAiChatRequest.ResponseFormat.jsonObject(),
                request.maxOutputTokens(),
                request.temperature().orElse(DEFAULT_TEMPERATURE));
    }

    private static String composeUserContent(AiRequest request) {
        return request.context().isBlank()
                ? request.userPrompt()
                : request.userPrompt() + "\n\n" + request.context();
    }

    public AiResponse toResponse(OpenAiChatResponse response, AiRequest request, long latencyMs, Instant generatedAt) {
        OpenAiChatResponse.Choice choice = response.choices().get(0);
        String content = choice.message() == null ? null : choice.message().content();
        if (content == null) {
            throw new AiInvalidResponseException("OpenAI completion had no message content");
        }

        Optional<Map<String, Object>> structuredContent =
                request.outputSchema().isPresent() ? Optional.of(parseStructuredContent(content)) : Optional.empty();

        int inputTokens = response.usage() == null ? 0 : response.usage().promptTokens();
        int outputTokens = response.usage() == null ? 0 : response.usage().completionTokens();
        int totalTokens = response.usage() == null ? inputTokens + outputTokens : response.usage().totalTokens();
        AiUsage usage = new AiUsage(
                inputTokens, outputTokens, totalTokens, "openai", properties.model(),
                estimateCost(inputTokens, outputTokens));

        String requestId = response.id() == null || response.id().isBlank()
                ? "openai-" + UUID.randomUUID()
                : response.id();

        return new AiResponse(
                content,
                structuredContent,
                "openai",
                properties.model(),
                usage,
                latencyMs,
                Optional.ofNullable(choice.finishReason()),
                requestId,
                generatedAt);
    }

    private Map<String, Object> parseStructuredContent(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new AiInvalidResponseException("OpenAI response content is not valid JSON");
        }
    }

    private BigDecimal estimateCost(int inputTokens, int outputTokens) {
        BigDecimal thousand = BigDecimal.valueOf(1000);
        BigDecimal inputCost = properties.pricing().inputPer1k()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(thousand, COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal outputCost = properties.pricing().outputPer1k()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(thousand, COST_SCALE, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }
}
