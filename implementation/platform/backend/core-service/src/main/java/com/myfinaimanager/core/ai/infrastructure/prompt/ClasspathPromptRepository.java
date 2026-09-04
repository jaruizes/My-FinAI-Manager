package com.myfinaimanager.core.ai.infrastructure.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.domain.ports.PromptRepositoryPort;

/**
 * Loads governed prompts from {@code src/main/resources/prompts/} (enabler §31; research D-plan
 * OD-8/AI). EN006 registers no business task beyond {@code "diagnostic"} (its own internal
 * observability-verification task, FR-054) — a future AI feature registers its own task ids and
 * instructions here or via an equivalent adapter, without changing {@code PromptService}.
 */
@Component
public class ClasspathPromptRepository implements PromptRepositoryPort {

    private static final String GLOBAL_PROMPT_ID = "global-system";
    private static final String GLOBAL_PROMPT_VERSION = "v1";
    private static final String GLOBAL_PROMPT_RESOURCE = "prompts/global-system-v1.txt";

    /** Known tasks and their (optional) task-specific instructions layered under the global prompt. */
    private static final Set<String> KNOWN_TASKS = Set.of("diagnostic");
    private static final Map<String, String> TASK_INSTRUCTIONS = Map.of();

    private final PromptReference globalSystemPrompt;

    public ClasspathPromptRepository() {
        this.globalSystemPrompt =
                new PromptReference(GLOBAL_PROMPT_ID, GLOBAL_PROMPT_VERSION, readResource(GLOBAL_PROMPT_RESOURCE));
    }

    @Override
    public PromptReference findGlobalSystemPrompt() {
        return globalSystemPrompt;
    }

    @Override
    public Optional<String> findTaskInstructions(String taskType) {
        return Optional.ofNullable(TASK_INSTRUCTIONS.get(taskType));
    }

    @Override
    public boolean isKnownTask(String taskType) {
        return KNOWN_TASKS.contains(taskType);
    }

    private static String readResource(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("missing bundled prompt resource: " + path, e);
        }
    }
}
