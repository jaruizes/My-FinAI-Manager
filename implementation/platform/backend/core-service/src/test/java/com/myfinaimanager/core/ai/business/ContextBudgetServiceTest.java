package com.myfinaimanager.core.ai.business;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** FR-029, FR-039. */
class ContextBudgetServiceTest {

    private final ContextBudgetService service = new ContextBudgetService();

    @Test
    void returns_context_unchanged_when_within_budget() {
        assertThat(service.build("short context", 100)).isEqualTo("short context");
    }

    @Test
    void truncates_at_a_word_boundary_when_over_budget() {
        String longContext = "one two three four five six seven eight nine ten";

        String truncated = service.build(longContext, 20);

        assertThat(truncated.length()).isLessThanOrEqualTo(20);
        assertThat(longContext).startsWith(truncated);
        assertThat(truncated).doesNotEndWith(" ");
    }

    @Test
    void truncates_hard_at_the_limit_when_there_is_no_space_to_break_on() {
        String noSpaces = "x".repeat(50);

        String truncated = service.build(noSpaces, 20);

        assertThat(truncated).hasSize(20);
    }

    @Test
    void redacts_obvious_secret_shaped_substrings() {
        String context = "please use api_key=sk-abc123 to call it, password: hunter2";

        String redacted = service.build(context, 1000);

        assertThat(redacted).doesNotContain("sk-abc123").doesNotContain("hunter2").contains("[REDACTED]");
    }
}
