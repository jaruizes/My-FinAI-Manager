# Phase 0 — Research: EN006 (Establish Provider-Neutral AI Model Integration)

**Feature dir**: `specs/EN006-establish-ai-model-integration/` · **Plan**: [plan.md](./plan.md) ·
**Spec**: [spec.md](./spec.md)

Resolves the plan's Open Decisions (OD-1…OD-9) into concrete, implementable detail. No
`NEEDS CLARIFICATION` remains — Q1–Q3 are resolved in spec.md / enabler §46, and every item below is
a safe, reversible, non-material implementation detail (constitution IV carve-out).

---

## D1 — OpenTelemetry integration approach

**Decision.** Use Spring Boot 3.5's native Micrometer-based tracing/metrics path rather than wiring
the OpenTelemetry SDK directly:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

`application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # local dev — trace everything
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
    metrics:
      export:
        url: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/metrics
```

`AiTelemetryRecorder` (infrastructure) wraps a `io.micrometer.observation.ObservationRegistry` +
`io.micrometer.core.instrument.MeterRegistry` (both auto-configured by Actuator once the
dependencies above are present) — it does **not** touch the OpenTelemetry SDK API directly, keeping
`ai.infrastructure.observability` a thin adapter over Micrometer, consistent with how the rest of
`core-service` already uses Micrometer/Actuator (`spring-boot-starter-actuator` is already a
dependency).

**Rationale.** Enabler §19, §19.4; FR-044, FR-046, FR-047. Spring Boot's own OTLP support is the
standard, best-documented path for a Spring Boot 3.x service and avoids hand-wiring the
`OpenTelemetrySdk` builder, span processors, or exporters — less code, matches "reuse the platform's
existing facilities" (technology-policy conservatism), and keeps a future non-AI use of tracing
(if ever wanted) free to reuse the same Micrometer facade.

**Alternatives rejected.**
- Direct `io.opentelemetry:opentelemetry-sdk` + manual `SpanBuilder`/`Tracer` wiring — more code,
  bypasses Spring Boot's auto-configuration, no material benefit for this enabler's needs.
- `spring-boot-starter-actuator`'s Zipkin exporter (B3 propagation) — the enabler explicitly wants
  OTLP end-to-end into an OpenTelemetry Collector (§19.3), not a Zipkin-native format.

---

## D2 — The deterministic local/stub adapter (`LocalAiModelAdapter`)

**Decision.** `ai.infrastructure.provider.local.LocalAiModelAdapter implements AiModelPort`:

```java
public AiResponse generate(AiRequest request) {
    String content = "Local deterministic response for task '" + request.taskType()
        + "' (promptId=" + request.systemPrompt().promptId()
        + ", promptVersion=" + request.systemPrompt().promptVersion() + ").";
    AiUsage usage = new AiUsage(
        /* inputTokens */  tokenCounter.estimate(request).inputTokens(),
        /* outputTokens */ 12,             // fixed, deterministic
        /* totalTokens */  tokenCounter.estimate(request).inputTokens() + 12,
        /* provider */     "local",
        /* model */        "local-deterministic-v1",
        /* estimatedCost */BigDecimal.ZERO);   // no real cost — FR-033, spec A6
    return new AiResponse(content, /* structuredContent */ Optional.empty(), "local",
        "local-deterministic-v1", usage, /* latencyMs computed by the policy, not the adapter */
        "stop", UUID.randomUUID().toString(), clock.instant());
}
```

- No network call, no credential, no randomness in `content` (only `generatedAt`/`requestId` vary,
  and those are explicitly excluded from any equality/determinism assertion — tests assert on
  `content`, `usage`, `provider`, `model`, `finishReason`).
- If `request.outputSchema()` is present, the adapter returns a `structuredContent` JSON object that
  **conforms** to the requested schema (using default/placeholder values per field type) — so US3's
  "happy path" is exercisable without a live provider; a **separate, explicit test fixture** (not the
  adapter) is used to exercise the *invalid*-structured-output rejection path (`AiInvocationPolicy`
  test with a hand-built non-conforming `AiResponse`), since the adapter itself has no reason to ever
  produce invalid output.
- `LocalAiModelAdapter` never throws a provider-neutral error itself in normal operation; a
  **test-only** companion (`FailingLocalAiModelAdapter`, test sources only) exists purely to exercise
  `AiInvocationPolicy`'s error-mapping/retry behavior (US6) without needing a real provider failure.

**Rationale.** Resolved Q1; FR-005, FR-033; spec US1. A deterministic, reproducible adapter is both
the architectural proof (VC-002) and the observability-chain trigger (VC-024–031) — it must behave
identically on every call so tests and the manual quickstart proof are reliable.

**Alternatives rejected.** A "mostly deterministic" adapter with a random delay to simulate latency
— rejected, adds flakiness risk to unit tests for no benefit (latency is naturally >0 anyway from
in-process guardrail/budget work).

---

## D3 — Structured-output validation without a new dependency

**Decision.** `ai.domain.model.OutputSchema` — an immutable list of `FieldSpec(String name,
FieldType type, boolean required)`, `FieldType ∈ {STRING, NUMBER, BOOLEAN, ARRAY, OBJECT}`.
`ai.infrastructure.validation.StructuredOutputValidator`:

```java
GuardrailOutcome validate(OutputSchema schema, JsonNode candidate);
```

Walks `schema.fields()`; for each `required` field, asserts presence and a JSON-node-type match
(`isTextual()`/`isNumber()`/`isBoolean()`/`isArray()`/`isObject()`); on the first violation returns
`GuardrailOutcome.rejected(reason)`; otherwise `GuardrailOutcome.allowed()`. Built entirely on
Jackson's `JsonNode` (already a transitive dependency via `spring-boot-starter-web`) — no new
library.

**Rationale.** FR-017–019; spec A1/OD-6. EN006 has no live provider producing arbitrary
schema-violating JSON (Q1) — the only structured-output payloads it ever validates are the local
adapter's own conforming output and hand-built test fixtures. Full JSON Schema (`$ref`, `oneOf`,
regex patterns, …) would be unused complexity today.

**Alternatives rejected.** `networknt/json-schema-validator` (full JSON Schema draft support) —
rejected for now per the above; the port shape (`AiRequest.outputSchema()`) does not preclude
swapping in a full validator later without changing `AiModelPort`, `AiRequest`, or
`AiInvocationPolicy`'s call sequence — only `StructuredOutputValidator`'s internals would change.

---

## D4 — Heuristic token estimation

**Decision.** `ai.infrastructure.tokencount.HeuristicTokenCounter implements TokenCounterPort`:

```java
TokenUsageEstimate estimate(AiRequest request) {
    int chars = request.systemPrompt().body().length()
              + request.userPrompt().length()
              + request.context().length();
    int inputTokens = Math.ceilDiv(chars, 4);   // ≈ 4 chars/token, a common English-text heuristic
    return new TokenUsageEstimate(inputTokens, request.maxOutputTokens());
}
```

Used by `ContextBudgetService`/`AiInvocationPolicy` **before** invoking the adapter, to check against
`ai.limits.max-input-tokens` / `max-total-tokens` (FR-028).

**Rationale.** FR-027; spec A4. No live provider (Q1) means no real tokenizer to match; a simple,
documented, deterministic approximation is sufficient to prove the budget-enforcement mechanism
(US5) and is trivially unit-testable.

**Alternatives rejected.** A provider-specific tokenizer library (e.g. a `tiktoken`-compatible
Java port) — rejected as a speculative dependency for a provider that doesn't exist yet in this
enabler (Q1); `TokenCounterPort` is exactly the seam a future provider adapter replaces this with.

---

## D5 — Rule-based guardrails (resolved Q2)

**Decision.**

`ai.infrastructure.guardrails.RuleBasedInputGuardrail implements InputGuardrailPort`:
- **Size rule**: `userPrompt.length() + context.length() > ai.limits.max-input-characters` (default
  20 000) → rejected("input exceeds maximum size").
- **Injection-pattern rule**: a small, explicit deny-list of case-insensitive substrings —
  `"ignore previous instructions"`, `"ignore the system prompt"`, `"disregard the above"`,
  `"you are now"`, `"new instructions:"` — found in `userPrompt` or `context` → rejected("possible
  prompt injection / system-prompt override detected").

`ai.infrastructure.guardrails.RuleBasedOutputGuardrail implements OutputGuardrailPort`:
- **Structured-schema rule**: delegates to `StructuredOutputValidator` (D3) when
  `request.outputSchema()` is present.
- **Prohibited-action-language rule**: a small deny-list of case-insensitive substrings in
  `response.content()` — `"i have sold"`, `"i have purchased"`, `"i have executed"`, `"transaction
  complete"`, `"order placed"` — → rejected("response claims an executed financial action").

Both guardrails are plain `List<GuardrailRule>` evaluated in order inside each `Port`
implementation; `AiInvocationPolicy` calls the input guardrail **before** `AiModelPort.generate(...)`
and the output guardrail **after**, translating a `rejected` outcome to
`AiGuardrailRejectedException` (never retried — FR-037).

**Rationale.** Resolved Q2; FR-020–024; enabler §15–16 (explicitly listed as *potential* guardrails,
not an exhaustive/mandatory list — EN006 implements "a subset," per §15: "The initial implementation
may support a subset, but the extension point is mandatory").

**Alternatives rejected.** A configurable/externalized rule-file mechanism — over-engineering for a
small, code-level deny-list that's easy to review and extend; the *ports* (not the rule storage) are
the mandated extension point.

---

## D6 — Actuator diagnostic endpoint (OD-9)

**Decision.** `ai.infrastructure.api.AiDiagnosticEndpoint`:

```java
@Component
@Endpoint(id = "aiDiagnostic")
public class AiDiagnosticEndpoint {

    @WriteOperation
    public AiDiagnosticResult run() {
        AiRequest request = AiRequest.diagnostic();   // fixed prompt, taskType = "diagnostic"
        AiResponse response = generateAiUseCase.generate(request);
        return new AiDiagnosticResult(response.requestId(), response.latencyMs(),
            response.usage().totalTokens());
    }
}
```

Exposed at `POST /actuator/ai-diagnostic` (Actuator's standard `web-exposure.include` mechanism;
added to `management.endpoints.web.exposure.include` alongside `health`). Guarded by
`management.endpoint.ai-diagnostic.enabled` (Spring Boot's standard per-endpoint toggle, default
`true`). Returns only non-sensitive summary fields — never the prompt or the full response content
(consistent with FR-040 even though the content here is itself synthetic/non-sensitive).

**Rationale.** OD-9; FR-054; enabler §19.11, §40 ("Observability Infrastructure Tests" — a
deterministic AI invocation must be triggerable against the running local stack, not only inside a
JUnit process). Actuator endpoints are the project's existing convention for operational,
non-business HTTP surfaces (`/actuator/health` already exists); this keeps the diagnostic trigger
consistent with that convention rather than inventing a new one.

**Alternatives rejected.** A dedicated `/api/ai/diagnostic` business endpoint — would read as a
business capability EN006 explicitly does not define (FR-059) and would require an `openapi.yaml`
entry for what is purely verification tooling. A CLI-only trigger (e.g. a Spring Boot CLI runner
argument) — harder to invoke against an already-running `./start.sh` instance from `quickstart.md`
without restarting the container.

---

## D7 — Local observability Compose topology (gated on ADR-004 approval)

**Decision** (implementation blocked until ADR-004 is approved — see plan.md gate note):

```yaml
# implementation/platform/infrastructure/local/compose.yaml (additive services)
otel-collector:
  image: otel/opentelemetry-collector-contrib:0.110.0
  volumes: [ "../observability/otel-collector-config.yaml:/etc/otelcol/config.yaml:ro" ]
  ports: [ "4318:4318" ]        # OTLP HTTP — core-service exports here

jaeger:
  image: jaegertracing/all-in-one:1.62
  ports: [ "16686:16686" ]      # Jaeger UI

prometheus:
  image: prom/prometheus:v2.55.1
  volumes: [ "../observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro" ]
  ports: [ "9090:9090" ]

grafana:
  image: grafana/grafana:11.3.0
  volumes: [ "../observability/grafana/provisioning:/etc/grafana/provisioning:ro",
             "../observability/grafana/dashboards:/var/lib/grafana/dashboards:ro" ]
  ports: [ "3000:3000" ]
  depends_on: [ prometheus ]
```

`otel-collector-config.yaml` — an OTLP receiver (`4318` HTTP), a `batch` processor, an `otlp` exporter
to Jaeger (`jaeger:4317`) for traces, and a `prometheus` exporter (`:8889/metrics`) for metrics;
**no** `logging`/`debug` exporter with full payload dumping enabled by default (would risk exporting
raw content — FR-048). `prometheus.yml` scrapes the Collector's `:8889/metrics` endpoint. Grafana
provisioning: one YAML datasource pointing at `http://prometheus:9090`, one dashboard-provider
pointing at `/var/lib/grafana/dashboards`, and `ai-observability.json` (panels per enabler §19.8 /
spec FR-051).

`start.sh` / `stop.sh`: extend the existing health-wait loop (already used for
`postgres`/`backend`/`frontend`) to also wait for `otel-collector`, `jaeger`, `prometheus`, and
`grafana` to report healthy before printing the "platform is up" summary; `stop.sh`'s
`docker compose down` already tears down every service in the compose file, so no separate logic is
needed there.

**Rationale.** ADR-004; enabler §19.1–§19.10; FR-048–053.

**Alternatives rejected.** See ADR-004's own "Alternatives Considered" (Zipkin, hosted SaaS, an
all-in-one agent) — not repeated here.

---

## D8 — ArchUnit rule additions

**Decision.** Two new rules in `StandardArchitectureRulesTest`, mirroring the existing
`portfolio`/`marketdata` confinement rules:

```java
ai_domain_and_business_are_free_of_provider_types   // no class in ai.infrastructure.provider..*
                                                      // referenced from ai.domain.. / ai.business..
ai_domain_and_business_use_no_provider_selection_annotations
                                                      // no org.springframework.boot.autoconfigure
                                                      // .condition.. / org.springframework.context
                                                      // .annotation.. in ai.domain.. / ai.business..
                                                      // (mirrors EN005's provider-selection rule)
```

Both non-vacuous by construction (the module has classes on both sides).

**Rationale.** FR-004, FR-006, FR-057; VC-001.

---

## D9 — Test matrix

| Layer | Tool | Covers |
|---|---|---|
| Domain/value objects | JUnit 5 | `AiRequest`/`AiResponse`/`AiUsage`/`OutputSchema` construction invariants (e.g. `maxOutputTokens > 0`) |
| `PromptService` | JUnit 5, RED-first | layering order, `promptId`/`promptVersion` resolution, unknown task/version → `AiConfigurationErrorException` |
| `ContextBudgetService` / `HeuristicTokenCounter` | JUnit 5, RED-first | context assembly excludes irrelevant/sensitive fields; token estimate formula; budget-exceeded detection |
| `RuleBasedInputGuardrail` / `RuleBasedOutputGuardrail` | JUnit 5, RED-first | each rule's accept/reject cases (D5) |
| `StructuredOutputValidator` | JUnit 5, RED-first | conforming / missing-required-field / wrong-type fixtures |
| `AiInvocationPolicy` | JUnit 5, RED-first, `LocalAiModelAdapter` + `FailingLocalAiModelAdapter` | full sequencing (US1–US6): happy path; token-budget rejection (adapter never invoked); cost-budget rejection; input-guardrail rejection (adapter never invoked); output-guardrail rejection; each provider-neutral error mapping; timeout; bounded retry vs never-retry; `correlationId`/`invocationId` propagation |
| `LocalAiModelAdapter` | JUnit 5 | deterministic output for a fixed input; structured-output happy path |
| `AiDiagnosticEndpoint` | `@WebMvcTest`/`@SpringBootTest` slice | returns a summary with no prompt/response body leakage |
| Architecture | ArchUnit | D8's two rules; ≥ 90 % line & branch coverage (JaCoCo) |
| Observability infrastructure (gated on ADR-004) | documented `quickstart.md` procedure | Collector receives OTLP; trace visible in Jaeger; metrics visible in Prometheus; Grafana dashboard renders; content-safety scan of the above finds nothing sensitive |

No test requires network access to any external AI provider or to the observability stack (the
Compose-based check is a separate, explicitly local-only procedure) — CI runs `./mvnw -B clean
verify` fully offline (FR-055).
