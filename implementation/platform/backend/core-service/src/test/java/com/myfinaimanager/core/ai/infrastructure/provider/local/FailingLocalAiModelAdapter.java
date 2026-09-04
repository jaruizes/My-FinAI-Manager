package com.myfinaimanager.core.ai.infrastructure.provider.local;

import java.util.function.Supplier;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.ports.AiModelPort;

/**
 * Test-only {@link AiModelPort} double that always fails, used to exercise
 * {@code AiInvocationPolicy}'s error-mapping and retry behavior (contract {@code local-ai-adapter.md}
 * "FailingLocalAiModelAdapter") without a live provider. Never wired in any Spring context.
 */
public final class FailingLocalAiModelAdapter implements AiModelPort {

    private final Supplier<RuntimeException> failure;
    private int invocationCount = 0;

    public FailingLocalAiModelAdapter(Supplier<RuntimeException> failure) {
        this.failure = failure;
    }

    @Override
    public AiResponse generate(AiRequest request) {
        invocationCount++;
        throw failure.get();
    }

    public int invocationCount() {
        return invocationCount;
    }
}
