# Contract — AI telemetry (spans, metrics, attributes) (EN006)

**Recorder**: `ai.infrastructure.observability.AiTelemetryRecorder` · **Transport**: OTLP/HTTP to the
OpenTelemetry Collector (research D1) · **Consumers**: Jaeger (traces), Prometheus (metrics), Grafana
(dashboard) — the last three are gated on ADR-004 approval (see plan.md).

This is the "external interface" EN006 exposes to the observability stack — the OTLP wire format
itself is standard (Micrometer/OpenTelemetry produce it); what EN006 controls, and what this
document pins, is **which** spans/metrics/attributes are emitted and **what they must never
contain**.

---

## Span chain (FR-044)

```text
[business operation span]            (only when a future caller already has one open — EN006
  │                                    never fabricates a business-level parent span itself)
  └─ ai.usecase                       (GenerateAiUseCase.generate — one per AiInvocationPolicy call)
      └─ ai.invocation                (AiModelPort.generate — one per adapter call, incl. each retry)
```

- `ai.usecase` and `ai.invocation` are always created, regardless of whether a business parent span
  exists (so the diagnostic-endpoint trigger, which has no business parent, still produces a
  complete, inspectable two-span chain).
- A retried invocation (contract C2, Q5) produces **one `ai.invocation` span per attempt**, all
  children of the same `ai.usecase` span, so a retry sequence is visible in Jaeger as siblings, not
  hidden inside a single span.

## Span & log attributes (FR-043, FR-044)

| Attribute | Source | Notes |
|---|---|---|
| `gen_ai.system` | `AiResponse.provider` (or the configured provider before a failure) | e.g. `local` |
| `gen_ai.request.model` | `AiProperties.defaultModel` / `AiResponse.model` | e.g. `local-deterministic-v1` |
| `gen_ai.operation.name` | fixed `"chat"` (generic — EN006 does not distinguish operation kinds) | |
| `gen_ai.usage.input_tokens` | `AiResponse.usage.inputTokens` | only on success |
| `gen_ai.usage.output_tokens` | `AiResponse.usage.outputTokens` | only on success |
| `ai.task` | `AiRequest.taskType` | |
| `ai.prompt.id` | `AiRequest.systemPrompt.promptId` | |
| `ai.prompt.version` | `AiRequest.systemPrompt.promptVersion` | |
| `ai.guardrail.result` | `"allowed"` or `"rejected:<guardrailName>"` | set on both the input and output guardrail checks |
| `ai.success` | `true` / `false` | |
| `ai.estimated.cost` | `AiResponse.usage.estimatedCost` | present even when `0` (FR-033) — dashboards must tolerate a real future provider where this is absent (spec Edge Cases) |
| `ai.correlation.id` | `InvocationIdentity.correlationId` | |
| `ai.invocation.id` | `InvocationIdentity.invocationId` | |
| `ai.provider.request.id` | `AiResponse.requestId` | when available |

**Never present, on any span, log line, or metric label** (FR-040, FR-045, FR-052): the raw
`AiRequest.userPrompt()` / `context()` / `systemPrompt().body()`, the raw `AiResponse.content()` /
`structuredContent()`, any `AI_API_KEY`/credential value, any user/account identifier, any raw
Portfolio position detail. `AiTelemetryRecorderTest` asserts this by construction (the recorder's
attribute-building code has no reference to those fields) and `quickstart.md`'s observability proof
re-asserts it by scanning the actually-emitted spans/dashboard.

## Metrics (FR-045; enabler §37, verbatim names)

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `ai_requests_total` | counter | `task`, `provider`, `model`, `outcome` (`success`/`error`/`rejected`) | |
| `ai_request_duration` | timer/histogram | `task`, `provider`, `model` | milliseconds |
| `ai_input_tokens_total` | counter | `task`, `provider`, `model` | |
| `ai_output_tokens_total` | counter | `task`, `provider`, `model` | |
| `ai_errors_total` | counter | `task`, `provider`, `model`, `error` (the provider-neutral exception's simple class name) | |
| `ai_guardrail_rejections_total` | counter | `direction` (`input`/`output`), `guardrail` | |
| `ai_estimated_cost_total` | counter | `task`, `provider`, `model` | always incremented (by `0` when no cost — FR-033) so the series exists even before a real provider is wired |

**Label cardinality rule** (FR-045, enabler §37): labels are drawn only from the closed sets above
(`task` is the finite set of known `taskType` values; `provider`/`model` are configuration-bound, not
free text from a response). **Never** a user id, Portfolio id, correlation id, invocation id, or any
raw text as a label — those go on span attributes (which Jaeger indexes per-trace, not as a
high-cardinality Prometheus label).

## Grafana dashboard (FR-051; enabler §19.8)

`implementation/platform/infrastructure/observability/grafana/dashboards/ai-observability.json`
(version-controlled, auto-provisioned — research D7) renders, from the metrics above: request volume
(by task, by provider/model), latency (avg/p95, by provider/model), token usage (input/output/total,
over time, by task, by model), estimated cost (a panel that renders `0`/empty gracefully — never
errors — when `ai_estimated_cost_total` is all zero, matching the spec's "no cost data available"
edge case), errors (by `error` label), and guardrail activity (by `direction`/`guardrail`).
