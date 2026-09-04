package com.myfinaimanager.core.ai.infrastructure.provider.local;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.AiUsage;
import com.myfinaimanager.core.ai.domain.model.OutputSchema;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.ports.AiModelPort;

/**
 * The one concrete {@link AiModelPort} implementation EN006 ships (resolved Q1; contract
 * {@code local-ai-adapter.md}). No network call, no credential, deterministic content for a given
 * request — proves the port is implementable and drives the observability chain end to end.
 */
@Component
@ConditionalOnProperty(name = "ai.default-provider", havingValue = "local", matchIfMissing = true)
public class LocalAiModelAdapter implements AiModelPort {

    public static final String PROVIDER = "local";
    public static final String MODEL = "local-deterministic-v1";

    @Override
    public AiResponse generate(AiRequest request) {
        long start = System.nanoTime();

        String content = "Local deterministic response for task '" + request.taskType()
                + "' (promptId=" + request.systemPrompt().promptId()
                + ", promptVersion=" + request.systemPrompt().promptVersion() + ").";

        Optional<Map<String, Object>> structuredContent = request.outputSchema().map(this::placeholderFor);

        int inputTokens = Math.max(1, Math.ceilDiv(
                request.systemPrompt().body().length() + request.userPrompt().length() + request.context().length(),
                4));
        int outputTokens = 12;
        AiUsage usage = new AiUsage(inputTokens, outputTokens, inputTokens + outputTokens, PROVIDER, MODEL,
                BigDecimal.ZERO);

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new AiResponse(
                content,
                structuredContent,
                PROVIDER,
                MODEL,
                usage,
                latencyMs,
                Optional.of("stop"),
                UUID.randomUUID().toString(),
                Instant.now());
    }

    /** One deterministic placeholder value per field, per its declared type (contract, "Request handling"). */
    private Map<String, Object> placeholderFor(OutputSchema schema) {
        Map<String, Object> content = new HashMap<>();
        for (FieldSpec field : schema.fields()) {
            Object placeholder = switch (field.type()) {
                case STRING -> "value";
                case NUMBER -> 0;
                case BOOLEAN -> false;
                case ARRAY -> List.of();
                case OBJECT -> Map.of();
            };
            content.put(field.name(), placeholder);
        }
        return content;
    }
}
