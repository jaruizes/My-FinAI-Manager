package com.myfinaimanager.core.ai.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.myfinaimanager.core.ai.domain.exceptions.AiConfigurationErrorException;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.domain.ports.PromptRepositoryPort;

/** FR-012–FR-015; VC-004, VC-005. */
@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    private static final PromptReference GLOBAL = new PromptReference("global-system", "v1", "Be careful.");

    @Mock private PromptRepositoryPort promptRepository;

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService(promptRepository);
    }

    @Test
    void composes_global_prompt_alone_when_no_task_instructions_exist() {
        when(promptRepository.isKnownTask("diagnostic")).thenReturn(true);
        when(promptRepository.findGlobalSystemPrompt()).thenReturn(GLOBAL);
        when(promptRepository.findTaskInstructions("diagnostic")).thenReturn(Optional.empty());

        PromptReference composed = promptService.compose("diagnostic");

        assertThat(composed.promptId()).isEqualTo("global-system");
        assertThat(composed.promptVersion()).isEqualTo("v1");
        assertThat(composed.body()).isEqualTo("Be careful.");
    }

    @Test
    void layers_task_instructions_under_the_global_prompt() {
        when(promptRepository.isKnownTask("analysis")).thenReturn(true);
        when(promptRepository.findGlobalSystemPrompt()).thenReturn(GLOBAL);
        when(promptRepository.findTaskInstructions("analysis")).thenReturn(Optional.of("Focus on risk."));

        PromptReference composed = promptService.compose("analysis");

        assertThat(composed.body()).isEqualTo("Be careful.\n\nFocus on risk.");
    }

    @Test
    void unknown_task_raises_configuration_error_without_a_silent_fallback() {
        when(promptRepository.isKnownTask("unknown-task")).thenReturn(false);

        assertThatThrownBy(() -> promptService.compose("unknown-task"))
                .isInstanceOf(AiConfigurationErrorException.class)
                .hasMessageContaining("unknown-task");
    }
}
