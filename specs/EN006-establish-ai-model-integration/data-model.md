# Phase 1 — Data Model: EN006 (Establish Provider-Neutral AI Model Integration)

**Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Spec**: [spec.md](./spec.md)

None of this is persisted (EN006 owns no table, no Flyway migration). Every model below is a
provider-neutral, non-persisted invocation value object, held only for the duration of one
`AiInvocationPolicy.generate(...)` call and its telemetry.

---

## 1. Domain models (`ai.domain.model`)

| Model | Fields | Notes |
|---|---|---|
| `AiRequest` | `String taskType`, `PromptReference systemPrompt`, `String userPrompt`, `String context`, `Optional<OutputSchema> outputSchema`, `int maxOutputTokens` (>0), `Optional<Double> temperature`, `Map<String,String> metadata`, `String correlationId` | FR-007. No provider-specific type anywhere. `AiRequest.diagnostic()` static factory used only by `AiDiagnosticEndpoint` (D6) — `taskType = "diagnostic"`, a fixed prompt reference, empty context. |
| `AiResponse` | `String content`, `Optional<JsonNode> structuredContent`, `String provider`, `String model`, `AiUsage usage`, `long latencyMs`, `Optional<String> finishReason`, `String requestId`, `Instant generatedAt` | FR-008. `structuredContent` present only when `AiRequest.outputSchema()` was set and validation passed. |
| `AiUsage` | `int inputTokens`, `int outputTokens`, `int totalTokens`, `String provider`, `String model`, `BigDecimal estimatedCost` (≥0) | FR-008/FR-030/FR-031. Always populated by `LocalAiModelAdapter` with deterministic/synthetic values (FR-033) since no live provider exists. |
| `PromptReference` | `String promptId`, `String promptVersion`, `String body` | FR-013/FR-014. Resolved by `PromptService` from `ClasspathPromptRepository`; `body` is the raw prompt text for the given id+version. |
| `OutputSchema` | `List<FieldSpec> fields` — `FieldSpec(String name, FieldType type, boolean required)`, `FieldType ∈ {STRING, NUMBER, BOOLEAN, ARRAY, OBJECT}` | FR-017. The internal, dependency-free structured-output contract (research D3/D6 — OD-6). |
| `GuardrailOutcome` | sealed: `Allowed` \| `Rejected(String guardrailName, String reason)` | FR-024. Returned by `InputGuardrailPort`/`OutputGuardrailPort`; a `Rejected` outcome becomes `AiGuardrailRejectedException`. |
| `InvocationIdentity` | `String correlationId`, `String invocationId` | FR-010/§28. `invocationId` is generated fresh per call; `correlationId` may be supplied by the (future) business caller or generated if absent, so a transport-level retry can share it while `invocationId` still distinguishes the attempt. |
| `TokenUsageEstimate` | `int inputTokens`, `int maxOutputTokens` | FR-027/FR-028. Produced by `TokenCounterPort.estimate(AiRequest)` **before** invocation. |

All fields are immutable (Java records); no field is a provider SDK type (enforced by D8's ArchUnit
rule).

---

## 2. Ports (`ai.domain.ports`)

| Port | Signature | Notes |
|---|---|---|
| `AiModelPort` | `AiResponse generate(AiRequest request)` | FR-001. Exactly one implementation shipped by EN006 (`LocalAiModelAdapter`, research D2). |
| `TokenCounterPort` | `TokenUsageEstimate estimate(AiRequest request)` | FR-027. Implemented by `HeuristicTokenCounter` (research D4). |
| `InputGuardrailPort` | `GuardrailOutcome check(AiRequest request)` | FR-020. Implemented by `RuleBasedInputGuardrail` (research D5). |
| `OutputGuardrailPort` | `GuardrailOutcome check(AiRequest request, AiResponse response)` | FR-020. Implemented by `RuleBasedOutputGuardrail` (research D5). |

`AiInvocationPolicy` (business) depends on all four ports plus `PromptService` and
`ContextBudgetService`; it is the **only** class that sequences them (FR-038) — no other class calls
`AiModelPort` directly.

### 2.1 Inbound port (business → future consumers)

| Port | Signature | Notes |
|---|---|---|
| `GenerateAiUseCase` | `AiResponse generate(AiRequest request)` | The seam a future task-specific AI port (e.g. a hypothetical `PortfolioAnalysisAiPort`) would call instead of reaching for `AiModelPort` directly (FR-002). EN006 defines this inbound port and its one implementation (`AiInvocationPolicy`); it defines **no** task-specific port itself. |

---

## 3. Neutral exceptions (`ai.domain.exceptions`)

| Exception | Raised when |
|---|---|
| `AiProviderUnavailableException` | The adapter call fails for a transient/infrastructure reason (FR-034). Never thrown by `LocalAiModelAdapter` in normal operation — exercised via the test-only `FailingLocalAiModelAdapter` (research D2). |
| `AiProviderRateLimitedException` | The adapter reports a rate-limit condition. |
| `AiProviderAuthenticationFailedException` | The adapter reports an authentication failure. |
| `AiRequestTooLargeException` | The request exceeds a provider-side size limit (distinct from the pre-flight token-budget check below). |
| `AiTokenBudgetExceededException` | `TokenCounterPort.estimate(...)` exceeds `ai.limits.max-input-tokens` / `max-total-tokens` **before** invocation (FR-028). |
| `AiCostBudgetExceededException` | The estimated cost exceeds `ai.limits.max-estimated-cost` **before** invocation (FR-031). |
| `AiTimeoutException` | The bounded per-call timeout elapses (FR-036). |
| `AiInvalidResponseException` | The adapter returns a structurally invalid `AiResponse` (should not happen with `LocalAiModelAdapter`; a defensive check). |
| `AiGuardrailRejectedException` | An input or output guardrail returns `GuardrailOutcome.Rejected` (FR-024). |
| `AiStructuredOutputInvalidException` | `StructuredOutputValidator` rejects the structured content against `OutputSchema` (FR-019). |
| `AiConfigurationErrorException` | An unknown `taskType`/`promptId`/`promptVersion` is requested, or `ai.*` configuration is invalid at startup (FR-014's "never falls back silently" edge case). |

All extend a common `AiException` base (mirrors `MarketDataException` in `marketdata` /
`FinancialInstrumentException`-style bases elsewhere in the codebase) so `AiInvocationPolicy`'s
callers can catch broadly when they only care "did AI fail" and narrowly when they need the reason.

---

## 4. Configuration (`ai.infrastructure.config`)

`AiProperties` — `@ConfigurationProperties("ai")` record:

```yaml
ai:
  default-provider: local
  default-model: local-deterministic-v1
  limits:
    max-input-tokens: 8000
    max-output-tokens: 1000
    max-total-tokens: 9000
    max-input-characters: 20000
    max-estimated-cost: 0.50
  timeout:
    connect: 2s
    read: 5s
  retry:
    max-attempts: 2
    backoff: 200ms
```

Exact default values are planning-level (research.md), not architectural — reversible via
configuration, no code change. `management.endpoint.ai-diagnostic.enabled` (default `true`) is a
separate, standard Actuator property (research D6), not part of `AiProperties`.

---

## 5. State / lifecycle

There is no persisted state and no state machine. Each `AiRequest` → `AiInvocationPolicy.generate(...)`
→ `AiResponse` (or a thrown `AiException`) is a single, stateless, synchronous operation:

```text
AiRequest
   │
   ▼
validate task / resolve provider+model (AiProperties)
   │
   ▼
PromptService.compose(taskType) ──► PromptReference (global system prompt + task instructions
   │                                 + business context + user prompt, layered — FR-012)
   ▼
ContextBudgetService.build(...) ──► compact, sanitized context (FR-029)
   │
   ▼
TokenCounterPort.estimate(...) ──► TokenUsageEstimate
   │
   ├── exceeds ai.limits.max-*-tokens? ──► AiTokenBudgetExceededException (no invocation)
   ├── exceeds ai.limits.max-estimated-cost? ──► AiCostBudgetExceededException (no invocation)
   ▼
InputGuardrailPort.check(request)
   │
   ├── Rejected? ──► AiGuardrailRejectedException (no invocation)
   ▼
AiModelPort.generate(request)  ──► AiResponse   (or a provider-neutral AiException, FR-034)
   │
   ▼
OutputGuardrailPort.check(request, response)
   │
   ├── Rejected? ──► AiGuardrailRejectedException
   ▼
[if outputSchema present] StructuredOutputValidator.validate(...)
   │
   ├── invalid? ──► AiStructuredOutputInvalidException
   ▼
AiTelemetryRecorder records the span + metrics (FR-043–045)
   │
   ▼
AiResponse returned to the caller
```

This is `AiInvocationPolicy`'s exact sequencing (FR-038) — every arrow above is a single method
call inside one orchestrator class; no step is duplicated elsewhere.

*(No `Portfolio`, `Position`, `Investor`, `FinancialInstrument`, or `MarketPrice` entity is read,
created, or changed anywhere in this model — EN006 is fully decoupled from every existing module's
data.)*
