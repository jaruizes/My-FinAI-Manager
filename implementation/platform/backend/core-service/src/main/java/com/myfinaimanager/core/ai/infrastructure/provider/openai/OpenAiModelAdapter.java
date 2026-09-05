package com.myfinaimanager.core.ai.infrastructure.provider.openai;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.ports.AiModelPort;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.client.OpenAiRestClient;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatRequest;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.dto.OpenAiChatResponse;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.mapper.OpenAiChatMapper;

/**
 * OpenAI implementation of {@link AiModelPort} (FD005 research D3), registered as the named bean
 * {@code "openai"} — selected per task via {@code ai.tasks.<task>.provider=openai} (research D1;
 * EN006's per-task routing), coexisting with the always-present {@code "local"} bean without
 * disturbing EN006's own {@code diagnostic} task. Blank {@code OPENAI_API_KEY} raises {@code
 * AiProviderNotConfiguredException} with no outbound call — see {@link OpenAiRestClient}. Every
 * other OpenAI-specific detail (request/response shape, error translation) stays inside {@link
 * OpenAiRestClient} and {@link OpenAiChatMapper} — nothing OpenAI-specific ever crosses this
 * {@code AiModelPort} boundary (contract {@code ai-model-port.md} P3).
 */
@Component("openai")
public class OpenAiModelAdapter implements AiModelPort {

    public static final String PROVIDER = "openai";

    private final OpenAiRestClient client;
    private final OpenAiChatMapper mapper;
    private final Clock clock;

    public OpenAiModelAdapter(OpenAiRestClient client, OpenAiChatMapper mapper, Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        OpenAiChatRequest chatRequest = mapper.toRequest(request);
        long startNanos = System.nanoTime();
        OpenAiChatResponse chatResponse = client.chatCompletion(chatRequest);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return mapper.toResponse(chatResponse, request, latencyMs, clock.instant());
    }
}
