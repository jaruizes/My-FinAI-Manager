# Contract — `AiModelPort` and `AiInvocationPolicy` (EN006)

**Port**: `ai.domain.ports.AiModelPort` · **Orchestrator**: `ai.business.AiInvocationPolicy`
(implements `ai.business.GenerateAiUseCase`) · **Shipped implementation**:
`ai.infrastructure.provider.local.LocalAiModelAdapter`

There is **no external HTTP contract** here (EN006 exposes no business REST API — FR-059). This
document is the **in-process** contract a future business feature (via a task-specific port layered
above `GenerateAiUseCase`, FR-002) or a future real provider adapter (implementing `AiModelPort`
directly, FR-005) must honor.

```text
future task-specific AI port (not part of EN006)
        │  depends on
        ▼
ai.business.GenerateAiUseCase  (AiInvocationPolicy)
        │  sequences (data-model.md §5)
        ▼
ai.domain.ports.{TokenCounterPort, InputGuardrailPort, OutputGuardrailPort, AiModelPort}
        ▲  implemented by
ai.infrastructure.{tokencount.HeuristicTokenCounter, guardrails.RuleBased{Input,Output}Guardrail,
                    provider.local.LocalAiModelAdapter}
```

---

## C1 — `AiModelPort.generate(AiRequest) → AiResponse`

| # | Invariant |
|---|---|
| P1 | Never receives a null `AiRequest`; `AiInvocationPolicy` validates required fields before calling it. |
| P2 | Returns a fully-populated `AiResponse` — `content` non-null, `usage` non-null with non-negative token counts and a non-negative `estimatedCost`, `generatedAt` set. |
| P3 | On any failure, throws one of the eleven provider-neutral exceptions (data-model.md §3) — **never** a provider-specific exception type, and never lets a checked/unchecked infrastructure exception (e.g. `IOException`, an HTTP client exception) escape unmapped. |
| P4 | Is a pure function of its input plus the implementation's own determinism contract — `LocalAiModelAdapter` (research D2) returns identical `content`/`usage`/`provider`/`model`/`finishReason` for an identical `AiRequest`, varying only `requestId` and `generatedAt`. |
| P5 | Never logs, traces, or includes in `AiResponse` the raw `AiRequest.userPrompt()` / `context()` / `systemPrompt().body()` beyond what `AiTelemetryRecorder` is explicitly allowed to record (FR-040/FR-043 — provider, model, task, prompt id/version, token counts, latency, success, guardrail result, correlation id; never the bodies). |
| P6 | Does not itself enforce token/cost budgets or guardrails — those are `AiInvocationPolicy`'s responsibility, invoked **before**/**after** this call (data-model.md §5). An adapter implementation must assume the request already passed those checks. |

## C2 — `AiInvocationPolicy.generate(AiRequest) → AiResponse` (via `GenerateAiUseCase`)

| # | Invariant |
|---|---|
| Q1 | Executes the full sequence in data-model.md §5, in that exact order, for every call — no step is optional or reorderable by a caller. |
| Q2 | A token-budget or cost-budget rejection (`AiTokenBudgetExceededException` / `AiCostBudgetExceededException`) or an input-guardrail rejection (`AiGuardrailRejectedException`) occurs **before** `AiModelPort.generate(...)` is ever called — verified in tests by asserting the injected adapter/port was never invoked. |
| Q3 | An output-guardrail rejection or a structured-output validation failure occurs **after** `AiModelPort.generate(...)` returns, and the offending `AiResponse` is never returned to the caller — only the exception is. |
| Q4 | Every returned `AiResponse` (and every thrown exception) is associated with an `InvocationIdentity` (`correlationId` + a freshly generated `invocationId`) recorded on the telemetry span (FR-010, FR-043). |
| Q5 | Retries (if any — `ai.retry.max-attempts`) apply **only** around the `AiModelPort.generate(...)` call itself, and **only** when the underlying failure is `AiProviderUnavailableException` or `AiProviderRateLimitedException` (transient). `AiGuardrailRejectedException`, `AiStructuredOutputInvalidException`, `AiProviderAuthenticationFailedException`, `AiTokenBudgetExceededException`, and `AiCostBudgetExceededException` are **never** retried (FR-037). |
| Q6 | A call exceeding `ai.timeout.read` (measured around the `AiModelPort.generate(...)` call) is translated to `AiTimeoutException` (FR-036). |
| Q7 | Is provider-agnostic: swapping `AiModelPort`'s bound implementation (a future real adapter, selected via `ai.default-provider`) requires **zero** change to `AiInvocationPolicy`, `PromptService`, `ContextBudgetService`, or either guardrail (FR-006, VC-003). |

## C3 — Determinism & testability

| # | Invariant |
|---|---|
| R1 | Every scenario in C1/C2 is exercisable in a JUnit test with `LocalAiModelAdapter` (happy paths) and `FailingLocalAiModelAdapter` (test-only, error-mapping paths) — no test in EN006's own suite requires network access (FR-055). |
| R2 | `AiInvocationPolicy`'s constructor accepts all four ports plus `PromptService`/`ContextBudgetService` as explicit dependencies (constructor injection) — no static/global provider lookup, no service-locator pattern — so a test can substitute any of them independently. |
