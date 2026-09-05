package com.myfinaimanager.core.ai.infrastructure.provider.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI {@code POST /chat/completions} response body (contract {@code openai-provider-contract.md}).
 * Infrastructure-only — confined to the {@code openai} adapter by ArchUnit. Unknown fields are
 * ignored — this record reads only what {@code OpenAiChatMapper} needs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(String id, List<Choice> choices, Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }
}
