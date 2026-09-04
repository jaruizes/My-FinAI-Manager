package com.myfinaimanager.core.ai.domain.model;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A provider-neutral AI generation request (FR-007). No provider-specific type appears anywhere in
 * this shape.
 *
 * <p>A caller constructs an {@code AiRequest} with {@link #taskType()}, {@link #userPrompt()},
 * {@link #context()}, and the budget/metadata fields; {@code systemPrompt} starts
 * {@link PromptReference#promptId()} {@code = ""} (unresolved) and is filled in by
 * {@link com.myfinaimanager.core.ai.business.AiInvocationPolicy} via {@link #withSystemPrompt} once
 * {@code PromptService} has composed it — the caller never builds a system prompt string itself
 * (FR-012).
 *
 * @param taskType       identifies which task-specific instructions / guardrail behaviour apply
 * @param systemPrompt   the composed system prompt; unresolved ({@link PromptReference#promptId()}
 *                       {@code = ""}) until {@link #withSystemPrompt} is called
 * @param userPrompt     the caller-supplied task/user prompt text
 * @param context        business context text (already budgeted once {@link #withContext} runs)
 * @param outputSchema   present when the caller wants a structured/typed response
 * @param maxOutputTokens the maximum output tokens allowed (&gt;0)
 * @param temperature    optional generation temperature
 * @param metadata       free-form, non-sensitive metadata (never logged verbatim beyond keys)
 * @param correlationId  ties this invocation to a broader operation; generated if the caller omits one
 */
public record AiRequest(
        String taskType,
        PromptReference systemPrompt,
        String userPrompt,
        String context,
        Optional<OutputSchema> outputSchema,
        int maxOutputTokens,
        Optional<Double> temperature,
        Map<String, String> metadata,
        String correlationId) {

    private static final PromptReference UNRESOLVED = new PromptReference("unresolved", "n/a", "");

    public AiRequest {
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType must not be blank");
        }
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null (use the unresolved placeholder)");
        }
        if (userPrompt == null) {
            throw new IllegalArgumentException("userPrompt must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (outputSchema == null) {
            throw new IllegalArgumentException("outputSchema must not be null (use Optional.empty())");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0");
        }
        if (temperature == null) {
            throw new IllegalArgumentException("temperature must not be null (use Optional.empty())");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }

    /**
     * A caller-facing factory for a new request whose system prompt is not yet resolved.
     */
    public static AiRequest of(
            String taskType,
            String userPrompt,
            String context,
            Optional<OutputSchema> outputSchema,
            int maxOutputTokens,
            String correlationId) {
        return new AiRequest(
                taskType, UNRESOLVED, userPrompt, context, outputSchema, maxOutputTokens,
                Optional.empty(), Map.of(), correlationId);
    }

    /** The fixed diagnostic request used by the internal observability-verification endpoint. */
    public static AiRequest diagnostic() {
        return of(
                "diagnostic",
                "Produce a short, deterministic diagnostic acknowledgement.",
                "",
                Optional.empty(),
                64,
                "diagnostic-" + UUID.randomUUID());
    }

    public boolean isSystemPromptResolved() {
        return !UNRESOLVED.promptId().equals(systemPrompt.promptId());
    }

    /** Returns a copy with the composed system prompt attached (FR-012, FR-014). */
    public AiRequest withSystemPrompt(PromptReference resolved) {
        return new AiRequest(
                taskType, resolved, userPrompt, context, outputSchema, maxOutputTokens,
                temperature, metadata, correlationId);
    }

    /** Returns a copy with the context replaced by its budgeted form (FR-029). */
    public AiRequest withContext(String budgetedContext) {
        return new AiRequest(
                taskType, systemPrompt, userPrompt, budgetedContext, outputSchema, maxOutputTokens,
                temperature, metadata, correlationId);
    }
}
