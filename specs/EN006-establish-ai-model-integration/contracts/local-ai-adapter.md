# Contract — `LocalAiModelAdapter` (the one shipped `AiModelPort` implementation)

**Class**: `ai.infrastructure.provider.local.LocalAiModelAdapter implements AiModelPort` ·
**Test double**: `ai.infrastructure.provider.local.FailingLocalAiModelAdapter` (test sources only)

This is EN006's **only** concrete `AiModelPort` implementation (resolved Q1 — spec.md
Clarifications). It exists to (a) prove the port is implementable end-to-end, and (b) drive the
observability chain (Jaeger/Prometheus/Grafana) deterministically, without any external network call
or credential.

---

## Request handling

| Input | Behavior |
|---|---|
| Any well-formed `AiRequest` (already past `AiInvocationPolicy`'s budget/guardrail checks) | Returns synchronously, in-process, in low-single-digit milliseconds. No thread sleep, no simulated network delay. |
| `AiRequest.outputSchema()` present | Returns `structuredContent` conforming to the schema — one deterministic placeholder value per `FieldSpec` (`STRING → "value"`, `NUMBER → 0`, `BOOLEAN → false`, `ARRAY → []`, `OBJECT → {}`), so US3's happy path is exercisable without a live provider. |
| `AiRequest.outputSchema()` absent | `structuredContent` is `Optional.empty()`. |

## Response content

```text
content = "Local deterministic response for task '{taskType}' "
        + "(promptId={promptId}, promptVersion={promptVersion})."
```

- `provider = "local"`, `model = "local-deterministic-v1"` — always these exact literals; used by
  telemetry (`gen_ai.system = local`) and by `quickstart.md`'s observability-proof assertions.
- `finishReason = "stop"` — always.
- `usage.inputTokens` = `HeuristicTokenCounter.estimate(request).inputTokens()` (so the input-token
  figure is internally consistent with the pre-flight budget check that already ran).
- `usage.outputTokens = 12` — a fixed constant.
- `usage.totalTokens = usage.inputTokens + 12`.
- `usage.estimatedCost = BigDecimal.ZERO` — always (no real provider, no real cost — FR-033, spec
  A6).
- `requestId` — a fresh random UUID per call (**not** part of the determinism contract below).
- `generatedAt` — the call instant from the injected `Clock` (**not** part of the determinism
  contract below).

## Determinism contract

Given two `AiRequest` values that are `equals()`, two calls to `LocalAiModelAdapter.generate(...)`
produce `AiResponse` values that are equal in **every field except** `requestId` and `generatedAt`.
Tests assert this explicitly (research D9) — it is the property the observability proof and the
unit-test suite both depend on.

## What it never does

- Never makes an outbound network call (asserted indirectly — no `RestClient`/`HttpClient` field
  exists on the class; enforced structurally, not just by convention).
- Never reads an environment variable, system property, or configuration key that looks like a
  credential.
- Never throws a provider-neutral exception in normal operation (see `FailingLocalAiModelAdapter`
  below for how the error-mapping paths are tested instead).
- Never logs or includes the raw `userPrompt`/`context`/`systemPrompt().body()` in its response or in
  any log line it emits.

---

## `FailingLocalAiModelAdapter` (test-only)

A second, **test-sources-only** `AiModelPort` implementation used exclusively by
`AiInvocationPolicyTest` to exercise `AiInvocationPolicy`'s error-mapping and retry behavior (US6)
without needing a real provider failure:

```java
class FailingLocalAiModelAdapter implements AiModelPort {
    private final Supplier<RuntimeException> failure;   // e.g. () -> new AiProviderRateLimitedException(...)
    // generate(...) always throws failure.get()
}
```

It is never wired in `application.yml` or any non-test Spring context — it exists purely to give
`AiInvocationPolicy`'s tests a deterministic way to simulate each of the eleven provider-neutral
failure modes (data-model.md §3) and the transient-vs-non-transient retry distinction (contract C2,
Q5) at the `AiModelPort` boundary.
