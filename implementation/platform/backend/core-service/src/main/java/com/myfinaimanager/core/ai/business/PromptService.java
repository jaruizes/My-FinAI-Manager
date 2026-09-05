package com.myfinaimanager.core.ai.business;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.myfinaimanager.core.ai.domain.exceptions.AiConfigurationErrorException;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.domain.ports.PromptRepositoryPort;

/**
 * Centralizes prompt layering (FR-012): Global System Prompt + Task Instructions. Business/user
 * context and the user prompt itself stay on {@code AiRequest.context()}/{@code .userPrompt()} —
 * this service composes only the <em>system</em> layer, so no controller or adapter ever builds a
 * prompt string by hand.
 */
@Service
public class PromptService {

    private final PromptRepositoryPort promptRepository;

    public PromptService(PromptRepositoryPort promptRepository) {
        this.promptRepository = promptRepository;
    }

    /**
     * @param taskType the request's task
     * @return the composed system-prompt layer. When the task has its own dedicated instructions,
     *     the result is identified by <strong>that task's own</strong> {@code promptId}/
     *     {@code promptVersion} (FD005 research D2) — otherwise by the global prompt's (FR-013,
     *     FR-014).
     * @throws AiConfigurationErrorException when {@code taskType} is not a registered task
     */
    public PromptReference compose(String taskType) {
        if (!promptRepository.isKnownTask(taskType)) {
            throw new AiConfigurationErrorException("unknown task type '" + taskType + "'");
        }
        PromptReference global = promptRepository.findGlobalSystemPrompt();
        Optional<PromptReference> task = promptRepository.findTaskInstructions(taskType);
        return task
                .map(t -> new PromptReference(t.promptId(), t.promptVersion(), global.body() + "\n\n" + t.body()))
                .orElse(global);
    }
}
