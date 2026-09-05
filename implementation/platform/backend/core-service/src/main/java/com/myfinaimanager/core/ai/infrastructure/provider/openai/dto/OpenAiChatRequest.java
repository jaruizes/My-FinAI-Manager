package com.myfinaimanager.core.ai.infrastructure.provider.openai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI {@code POST /chat/completions} request body (contract {@code openai-provider-contract.md}).
 * {@code responseFormat} is always {@code {"type":"json_object"}} — never OpenAI's native
 * {@code json_schema} mode (resolved OD-3; research D3). Infrastructure-only — confined to the
 * {@code openai} adapter by ArchUnit.
 */
public record OpenAiChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        @JsonProperty("max_tokens") int maxTokens,
        Double temperature) {

    public record Message(String role, String content) {
    }

    public record ResponseFormat(String type) {

        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }
}
