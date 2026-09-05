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
 * OD-8/AI). Registers two tasks: EN006's own internal {@code "diagnostic"} (no dedicated
 * instructions — FR-054) and FD005's {@code "portfolio-analysis"} (dedicated instructions,
 * {@code prompts/tasks/portfolio-analysis-v1.txt}, its own {@code promptId}/{@code promptVersion}
 * — FD005 research D2). A future AI feature registers its own task the same way, without changing
 * {@code PromptService}.
 */
@Component
public class ClasspathPromptRepository implements PromptRepositoryPort {

    private static final String GLOBAL_PROMPT_ID = "global-system";
    private static final String GLOBAL_PROMPT_VERSION = "v1";
    private static final String GLOBAL_PROMPT_RESOURCE = "prompts/global-system-v1.txt";

    private static final String PORTFOLIO_ANALYSIS_TASK = "portfolio-analysis";
    private static final String PORTFOLIO_ANALYSIS_PROMPT_VERSION = "v1";
    private static final String PORTFOLIO_ANALYSIS_RESOURCE = "prompts/tasks/portfolio-analysis-v1.txt";

    /** Known tasks and their (optional) task-specific instructions layered under the global prompt. */
    private static final Set<String> KNOWN_TASKS = Set.of("diagnostic", PORTFOLIO_ANALYSIS_TASK);

    private final PromptReference globalSystemPrompt;
    private final Map<String, PromptReference> taskInstructions;

    public ClasspathPromptRepository() {
        this.globalSystemPrompt =
                new PromptReference(GLOBAL_PROMPT_ID, GLOBAL_PROMPT_VERSION, readResource(GLOBAL_PROMPT_RESOURCE));
        this.taskInstructions = Map.of(
                PORTFOLIO_ANALYSIS_TASK,
                new PromptReference(
                        PORTFOLIO_ANALYSIS_TASK,
                        PORTFOLIO_ANALYSIS_PROMPT_VERSION,
                        readResource(PORTFOLIO_ANALYSIS_RESOURCE)));
    }

    @Override
    public PromptReference findGlobalSystemPrompt() {
        return globalSystemPrompt;
    }

    @Override
    public Optional<PromptReference> findTaskInstructions(String taskType) {
        return Optional.ofNullable(taskInstructions.get(taskType));
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
