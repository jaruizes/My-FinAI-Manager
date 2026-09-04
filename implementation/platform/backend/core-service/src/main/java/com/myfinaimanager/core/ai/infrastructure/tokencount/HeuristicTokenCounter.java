package com.myfinaimanager.core.ai.infrastructure.tokencount;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.TokenUsageEstimate;
import com.myfinaimanager.core.ai.domain.ports.TokenCounterPort;

/**
 * A deliberately simple, provider-agnostic token estimate — {@code ceil(characters / 4)}, a common
 * English-text approximation (research D4). No real tokenizer is meaningful without a real provider
 * to match against (resolved Q1); {@link TokenCounterPort} is exactly the seam a future
 * provider-specific tokenizer replaces this with.
 */
@Component
public class HeuristicTokenCounter implements TokenCounterPort {

    private static final int CHARACTERS_PER_TOKEN = 4;

    @Override
    public TokenUsageEstimate estimate(AiRequest request) {
        int chars = request.systemPrompt().body().length() + request.userPrompt().length()
                + request.context().length();
        int inputTokens = Math.ceilDiv(chars, CHARACTERS_PER_TOKEN);
        return new TokenUsageEstimate(inputTokens, request.maxOutputTokens());
    }
}
