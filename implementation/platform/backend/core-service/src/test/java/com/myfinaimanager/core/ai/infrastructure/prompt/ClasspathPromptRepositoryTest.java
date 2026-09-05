package com.myfinaimanager.core.ai.infrastructure.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.PromptReference;

/** FR-011, FR-013; enabler §31. */
class ClasspathPromptRepositoryTest {

    private final ClasspathPromptRepository repository = new ClasspathPromptRepository();

    @Test
    void loads_the_global_system_prompt_from_the_classpath() {
        PromptReference global = repository.findGlobalSystemPrompt();

        assertThat(global.promptId()).isEqualTo("global-system");
        assertThat(global.promptVersion()).isEqualTo("v1");
        assertThat(global.body()).contains("Do not invent financial facts");
    }

    @Test
    void diagnostic_is_a_known_task_with_no_dedicated_instructions() {
        assertThat(repository.isKnownTask("diagnostic")).isTrue();
        assertThat(repository.findTaskInstructions("diagnostic")).isEqualTo(Optional.empty());
    }

    @Test
    void an_unregistered_task_is_not_known() {
        assertThat(repository.isKnownTask("unregistered-task")).isFalse();
    }

    @Test
    void portfolio_analysis_is_a_known_task_with_its_own_versioned_instructions() {
        assertThat(repository.isKnownTask("portfolio-analysis")).isTrue();
        Optional<PromptReference> instructions = repository.findTaskInstructions("portfolio-analysis");

        assertThat(instructions).isPresent();
        assertThat(instructions.get().promptId()).isEqualTo("portfolio-analysis");
        assertThat(instructions.get().promptVersion()).isEqualTo("v1");
        assertThat(instructions.get().body()).contains("diversification");
    }
}
