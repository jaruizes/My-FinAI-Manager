/**
 * Provider-neutral AI model integration domain (EN006): invocation request/response models
 * ({@code model}), outbound ports ({@code ports}), and failure types ({@code exceptions}).
 * Framework-free — no Spring, HTTP, JSON, or AI-provider-SDK type (ADR-003; ArchUnit-enforced).
 * EN006 defines no business AI feature — this module is horizontal infrastructure a future feature
 * consumes through {@link com.myfinaimanager.core.ai.business.GenerateAiUseCase}.
 */
package com.myfinaimanager.core.ai.domain;
