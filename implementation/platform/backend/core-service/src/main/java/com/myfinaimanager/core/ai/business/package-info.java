/**
 * EN006 business orchestration: {@link com.myfinaimanager.core.ai.business.AiInvocationPolicy}
 * centralizes prompt composition, context budgeting, token/cost budget enforcement, guardrails,
 * provider invocation, and telemetry recording (enabler §29) behind the single inbound
 * {@link com.myfinaimanager.core.ai.business.GenerateAiUseCase} port. Depends only on
 * {@code ai.domain}; never on {@code ai.infrastructure} (ADR-003; ArchUnit-enforced).
 */
package com.myfinaimanager.core.ai.business;
