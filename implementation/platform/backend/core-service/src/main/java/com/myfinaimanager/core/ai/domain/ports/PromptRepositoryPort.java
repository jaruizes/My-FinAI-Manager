package com.myfinaimanager.core.ai.domain.ports;

import java.util.Optional;

import com.myfinaimanager.core.ai.domain.model.PromptReference;

/**
 * Resolves governed, versioned prompt text (FR-013). The shipped implementation loads classpath
 * resources (research D-plan OD-8/AI). Keeps {@code ai.business.PromptService}'s pure layering
 * logic free of any storage mechanism.
 */
public interface PromptRepositoryPort {

    /** The single governed global system prompt (FR-011, FR-015). */
    PromptReference findGlobalSystemPrompt();

    /**
     * Task-specific instructions layered on top of the global system prompt (FR-012), carrying
     * their <strong>own</strong> {@code promptId}/{@code promptVersion} (FD005 research D2 — a
     * task's persisted prompt version must identify the task's own prompt, not the global one it's
     * layered on). Absent for a task with no dedicated instructions — still a valid, known task,
     * distinct from an unknown one (which {@code PromptService} rejects with
     * {@code AiConfigurationErrorException}).
     */
    Optional<PromptReference> findTaskInstructions(String taskType);

    /** Whether {@code taskType} is a registered task at all (known tasks may still lack instructions). */
    boolean isKnownTask(String taskType);
}
