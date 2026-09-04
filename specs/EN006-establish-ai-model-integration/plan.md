# Implementation Plan: Establish Provider-Neutral AI Model Integration (EN006)

**Branch**: `EN006-establish-ai-model-integration` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Authoritative enabler**: `product/definition/enablers/EN006-establish-ai-model-integration/EN006-establish-ai-model-integration.md`
(**Approved**; §47 signed by jaruiz 2026-09-04; §46 resolved the same day).

**Clarifications carried from spec.md**: **Q1** EN006 ships no real external AI provider — one
deterministic local/stub `AiModelPort` adapter only. **Q2** guardrails are rule-based only. **Q3**
the local observability stack proceeds through a new ADR —
[`ADR-004-local-ai-observability-stack.md`](../../product/architecture/adrs/ADR-004-local-ai-observability-stack.md),
**Status: Approved** (jaruiz, 2026-09-04, as drafted — no changes).

> **Gate cleared**: ADR-004 is approved. The observability-stack portion of this plan (Docker
> Compose / `start.sh` / `stop.sh` changes) may now proceed alongside the rest of the module.

## Summary

A new `ai` module inside the existing `core-service` deployable (ADR-001, ADR-003 layout),
providing a provider-neutral `AiModelPort` plus the invocation policy around it (prompt
composition/versioning, rule-based input/output guardrails, token/context/cost budget enforcement,
provider-neutral error mapping, bounded timeout/retry) — and exactly **one** concrete adapter: a
deterministic **local/stub** implementation with no network call and no credential (resolved Q1).
OpenTelemetry instrumentation wraps every invocation; a new local Docker Compose observability stack
(OTel Collector + Jaeger + Prometheus + Grafana, gated on ADR-004 approval) makes that telemetry
inspectable, wired into the existing `start.sh` / `stop.sh` lifecycle. EN006 defines **no** business
AI feature, persists nothing, and adds no public REST API — a small internal Actuator diagnostic
endpoint is the only way to trigger a deterministic invocation for observability verification
(OD-9 below).

## Technical Context

**Language / Runtime**: Java 21, Spring Boot 3.5.6 (unchanged — same as `portfolio` /
`financialinstrument` / `marketdata`). No frontend change (EN006 has no UI).

**Primary Dependencies**:
- Reused, no version change: Spring Boot Web/Actuator, Jackson, JUnit 5, Mockito, ArchUnit.
- **New**: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` + `micrometer-registry-otlp`
  (the third supplies the OTLP *metrics* registry — discovered necessary during implementation;
  see pr-evidence.md) (Spring Boot 3.5's
  standard OpenTelemetry tracing/metrics export path via Actuator/Micrometer — `OpenTelemetry` is
  `PREFERRED` per technology policy; this is the platform's first telemetry-export dependency,
  approved implicitly by the enabler's own §19 requirement and explicitly allowed by FR-046).
- **No LLM provider SDK** — resolved Q1 means no Anthropic/OpenAI/Bedrock/Vertex client library is
  added.
- **No new JSON-schema library** — structured-output validation (FR-017–019) uses a minimal internal
  `OutputSchema` value object (field name → required/type) validated with Jackson's existing
  `ObjectMapper`/`JsonNode`, not a third-party JSON Schema implementation. Sufficient because EN006
  has no real provider producing arbitrary schema-violating JSON; a future provider adapter can
  introduce a full JSON Schema validator then, if actually needed (research.md D3).

**Storage**: PostgreSQL 16 — **unchanged**. EN006 persists nothing (no Flyway migration; FR-016
defines a traceability *shape* for a future feature to persist, not a table EN006 owns).

**Testing**: `./mvnw -B clean verify` (Surefire unit + JaCoCo `check` + ArchUnit — **no new
Testcontainers IT**, since nothing is persisted). `AiInvocationPolicy` and its budget/guardrail/
prompt logic are RED-first TDD (deterministic domain/business logic — constitution VII). The
observability chain (FR-054) is verified by a documented `quickstart.md` procedure against the local
Compose stack (once ADR-004 is approved), not a JUnit test — matching the enabler's own "Observability
Infrastructure Tests" framing (§40) as an infrastructure-verification concern, not a unit-test one.

**Target Platform**: the existing `core-service` container (ADR-001) for the `ai` module. Four new
**local-only** containers for observability (gated on ADR-004): `otel-collector`, `jaeger`,
`prometheus`, `grafana`.

**Performance Goals**: not applicable — no live provider means no real-world latency to target; the
local/stub adapter must respond in low-single-digit milliseconds so unit tests stay fast.

**Constraints**:
- ADR-003 module layout; ArchUnit-enforced (`ai.domain` / `ai.business` free of provider SDK types,
  provider DTOs, and provider client packages — FR-004, FR-057).
- No provider-identity branch in `ai.domain` / `ai.business` (FR-006) — mirrors EN005's
  `@ConditionalOnProperty` pattern, trivial here since there is exactly one adapter.
- Secret safety: no AI provider key exists in EN006's own configuration (FR-042); the *pattern*
  (`AI_API_KEY` externalized, never logged) is established for a future adapter.
- Sensitive-content safety: no raw prompt/completion body in logs, traces, or metric labels by
  default (FR-040, FR-045).
- No new independently deployable service (FR-062) — the `ai` module lives inside `core-service`.
- No RAG / vector DB / embeddings / agent orchestration / MCP / fine-tuning (FR-060).
- No change to Portfolio valuation, FX conversion, position weights, or sector allocation (FR-061) —
  FD004 / EN005 are untouched by this work.
- `./start.sh` / `./stop.sh` / `./e2e.sh` interface unchanged in shape (new services join the same
  entry points — FR-053).
- CI never depends on a live AI provider or on the Compose observability stack being up (FR-055).

**Scale/Scope**: one new module (~25–30 classes: 4 ports, ~7 domain models, 11 exception types, 4
business classes, ~7 infrastructure classes, 1 config, 1 diagnostic endpoint) inside `core-service`;
zero Flyway migrations; zero OpenAPI changes; 4 new local-only Compose services (gated on ADR-004);
`start.sh`/`stop.sh` updated to include them; `pom.xml` gains the OTel exporter dependency (2–3
artifacts). No new module count beyond `ai`; no change to `portfolio`, `financialinstrument`, or
`marketdata`.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the approved enabler (§47 signed 2026-09-04). The one new architectural decision beyond the enabler's own text (the concrete local observability topology) is raised as ADR-004, **not** silently decided. |
| II | Definitions & Enablers Are Authoritative Intent | **PASS** | Every FR traces to an enabler §/VC (spec Traceability table). No scope expansion: no business AI feature, no live provider, no RAG/agents — all explicitly out of scope in spec.md. |
| III | Derived Artifacts & Repository Layout | **PASS** | Artifacts under `specs/EN006-…/`; code under `implementation/platform/`; the one architecture document produced (ADR-004) is correctly placed under `product/architecture/adrs/`, not `specs/`. |
| IV | No Invention; Surface Material Ambiguity | **PASS** | The pre-specification Draft/unsigned gate was surfaced and resolved (Q1–Q3) before any spec content was written. ADR-004 is explicitly flagged **Proposed, pending approval** — not treated as approved by drafting it. The diagnostic-endpoint decision (OD-9) is a safe, reversible, non-material implementation detail (internal Actuator endpoint, no business behavior) chosen without further escalation, per the ambiguity policy's own carve-out. |
| V | Technical Enablers Stay Technical | **PASS** | No investor-facing behavior, no Portfolio/valuation rule, no UI, no forced user stories. Scenarios are developer/operator facets tied to VCs (spec.md "Enabler Nature"). |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | `ai.domain` / `ai.business` stay framework- and provider-free (ArchUnit). `AiInvocationPolicy`'s sequencing (validate → resolve → compose → sanitize → budget → guardrail → invoke → guardrail → validate → collect) is deterministic orchestration behind ports. No LLM is used to compute anything Portfolio-financial (FR-061) — there is no live LLM at all in EN006. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | `AiInvocationPolicy`, `PromptService`, `ContextBudgetService`, the rule-based guardrails, and the token/cost budget checks are RED-first TDD. No persistence ⇒ no Testcontainers IT is needed (nothing to test against a real database) — consistent with the policy's "when a suitable containerized dependency exists / is needed," not a blanket requirement. |
| VIII | Contract-First External APIs | **PASS (N/A for a new business API)** | EN006 adds **no** external business REST API — `openapi.yaml` is untouched. The one new HTTP surface (an Actuator diagnostic endpoint, OD-9) is operational/management tooling, not a business contract, and is documented in `contracts/` for transparency even though it's not part of `openapi.yaml`. |

**Repository-structure / technology-policy quick check:**

| Check | Status | Evidence |
|---|---|---|
| One `core-service` deployable (ADR-001) | PASS | no new backend service; the `ai` module lives inside it |
| ADR-003 module layout in `ai` | PASS | `domain/{model,ports,exceptions}`, `business`, `infrastructure/{provider,prompt,guardrails,observability,config}` |
| LLM Provider Policy (`technology-policy.md` "AI and LLM Policy") | PASS (N/A this enabler) | Bedrock/OpenAI/Anthropic/Vertex are `ALLOWED` behind an abstraction — relevant to a *future* provider adapter; EN006 adds none |
| Observability (`OpenTelemetry` `PREFERRED`) | PASS | new `micrometer-tracing-bridge-otel` + OTLP exporter; first telemetry dependency in `core-service` |
| Local observability topology (Collector/Jaeger/Prometheus/Grafana) | PASS | ADR-004 approved by jaruiz 2026-09-04, as drafted |
| No unapproved new deployable / broker / scheduler | PASS | none introduced |
| Secret handling | PASS | FR-042; no AI provider key exists yet in EN006's own config |
| Financial determinism preserved | PASS | FR-061; FD004/EN005 untouched |

**Result: PASS.** ADR-004 is approved; no remaining gate. Re-checked post-design below.

## Open Decisions (technical) — recommended positions

| ID | Decision | Recommended position | Rejected |
|---|---|---|---|
| OD-1 | Module package layout | `ai/domain/{model,ports,exceptions}`, `ai/business/`, `ai/infrastructure/{provider/local, prompt, guardrails, tokencount, observability, config}` — matches the enabler's own §30 suggested structure almost verbatim. | A flatter `ai/{ports,adapters}` structure — rejected, deviates from ADR-003 and the enabler's own suggestion with no benefit. |
| OD-2 | `AiModelPort`'s one implementation | `ai.infrastructure.provider.local.LocalAiModelAdapter` — deterministic: derives `content` from a hash/template of the request (e.g. echoes task + a fixed deterministic sentence), fixed small token/cost figures, `finishReason = "stop"`, `provider = "local"`, `model = "local-deterministic-v1"`. No randomness, no clock-sensitive content beyond `generatedAt`. | A "randomized" fake (varying content) — rejected, breaks reproducible tests and the observability proof's determinism requirement (FR-005, FR-033, spec US1). |
| OD-3 | Provider/model configuration keys | `ai.default-provider` (default `local`), `ai.default-model`, `ai.limits.{max-input-tokens,max-output-tokens,max-total-tokens,max-estimated-cost}`, `ai.timeout.{connect,read}` — one `@ConfigurationProperties("ai")` record, mirroring `FinnhubProperties`/`FrankfurterProperties`'s style. | Per-task routing config (enabler §7 future) — explicitly deferred (spec A10); not implemented now. |
| OD-4 | Guardrail rule set (rule-based, resolved Q2) | Input: max character length; a small deny-list of system-override phrases ("ignore previous instructions", "you are now", "disregard the system prompt"). Output: structured-response schema conformance (delegates to OD-6); a small deny-list of prohibited-action phrases ("I have sold", "I have purchased", "I have executed", "transaction complete"). Simple, explicit, easy to extend — not a scoring/ML model. | A configurable regex-rule *engine* (externalized rule files) — rejected as over-engineering for a rule-based-only, no-live-provider enabler; a straightforward `List<GuardrailRule>` in code is enough and still satisfies the extension-point requirement (FR-020) since `InputGuardrailPort`/`OutputGuardrailPort` remain the seam a future rule engine would plug into. |
| OD-5 | Token estimation | `HeuristicTokenCounter implements TokenCounterPort` — `tokens ≈ ceil(characterCount / 4)` (a widely used English-text approximation), applied to the composed prompt for input and to `maxOutputTokens` for the output-side budget check. Deliberately conservative/simple since no real tokenizer is meaningful without a real provider (FR-027). | A provider-specific tokenizer (e.g. `tiktoken`) — rejected, there is no provider to match tokenization against; would be a speculative dependency (resolved Q1). |
| OD-6 | Structured-output validation mechanism | An internal `OutputSchema` value object (`List<FieldSpec(name, type, required)>`) plus a small `StructuredOutputValidator` that checks a parsed `JsonNode` against it — no new dependency (research.md D3). | `networknt/json-schema-validator` or `everit-org/json-schema` — rejected for now: EN006 has no real provider generating arbitrary JSON to validate against a *full* JSON Schema; introducing a dependency for a capability only unit-tested against fixtures is premature. A future provider adapter MAY introduce full JSON Schema support without changing `AiRequest.outputSchema`'s conceptual shape. |
| OD-7 | OpenTelemetry span/metric naming | `gen_ai.system`, `gen_ai.request.model`, `gen_ai.operation.name` (OTel Gen AI semantic conventions, 2025 stable subset) plus application attributes `ai.task`, `ai.prompt.id`, `ai.prompt.version`, `ai.guardrail.result`, `ai.success`, `ai.estimated.cost`, `ai.correlation.id`; Micrometer meters `ai_requests_total`, `ai_request_duration`, `ai_input_tokens_total`, `ai_output_tokens_total`, `ai_errors_total`, `ai_guardrail_rejections_total`, `ai_estimated_cost_total` (enabler §37, verbatim names). | Inventing project-specific attribute names instead of `gen_ai.*` — rejected; the enabler explicitly asks for the semantic-convention attributes "where compatible" (§19), and using the standard names keeps future non-EN006 telemetry (if any) consistent. |
| OD-8 | Observability stack images/config location | `implementation/platform/infrastructure/observability/{otel-collector-config.yaml, prometheus.yml, grafana/provisioning/{datasources,dashboards}/, grafana/dashboards/ai-observability.json}` — mirrors the enabler's own §19.2 suggested layout, placed under the existing `infrastructure/` root (consistent with `infrastructure/local/` for Compose). Pinned image tags recorded in research.md. **ADR-004 approved 2026-09-04 — implementation may proceed.** | A `docker-compose.yml` at the platform root (enabler §19.2's literal conceptual path) — rejected; the project's actual convention is `infrastructure/local/compose.yaml` (EN001/EN002), which this plan follows instead of the enabler's illustrative path, per CLAUDE.md "exact paths may be adapted to the existing repository conventions" (enabler §19.2 itself says the same). |
| OD-9 | How a deterministic AI invocation is triggered for the observability proof (FR-054) | A Spring Boot **custom Actuator endpoint** (`@Endpoint(id = "aiDiagnostic")`, exposed at `/actuator/ai-diagnostic`, `POST`) that runs one fixed diagnostic request through `AiInvocationPolicy` using the local adapter and returns a small JSON summary (`invocationId`, `latencyMs`, `tokens`). Operational tooling, not a business API — excluded from `openapi.yaml`, documented instead in `contracts/ai-diagnostic-endpoint.md`. Safe/reversible/non-material (constitution IV carve-out) — chosen without further escalation. | A dedicated business REST endpoint under `/api/...` — rejected, would look like a business capability EN006 explicitly does not define (FR-059). A JUnit-only trigger (no way to invoke it from a running `./start.sh` instance) — rejected, the enabler's verification (§40 "Observability Infrastructure Tests", §19.11) implies an operator can trigger it against the running local stack, not only in a test JVM. |

## Project Structure

### Documentation (this feature)

```text
specs/EN006-establish-ai-model-integration/
├── plan.md              # this file
├── research.md          # Phase 0 — D1…D9
├── data-model.md        # Phase 1 — AiRequest/AiResponse/AiUsage/PromptReference/OutputSchema + ports
├── contracts/
│   ├── ai-model-port.md              # AiModelPort invariants (the internal "contract")
│   ├── local-ai-adapter.md           # the shipped deterministic adapter's exact behavior
│   ├── ai-telemetry.md               # span/metric names + attributes (OD-7) — the OTLP "contract"
│   └── ai-diagnostic-endpoint.md     # the internal Actuator endpoint (OD-9) — not part of openapi.yaml
├── quickstart.md        # Phase 1 — validation incl. the observability proof (gated on ADR-004)
├── checklists/requirements.md
└── spec.md
```

### Source Code (repository)

```text
implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/

ai/                                              # NEW module
├── domain/
│   ├── model/
│   │   ├── AiRequest.java
│   │   ├── AiResponse.java
│   │   ├── AiUsage.java
│   │   ├── PromptReference.java
│   │   ├── OutputSchema.java              # FieldSpec(name, type, required) list — OD-6
│   │   ├── GuardrailOutcome.java          # allowed / rejected(reason)
│   │   └── InvocationIdentity.java        # correlationId + invocationId
│   ├── ports/
│   │   ├── AiModelPort.java
│   │   ├── TokenCounterPort.java
│   │   ├── InputGuardrailPort.java
│   │   └── OutputGuardrailPort.java
│   └── exceptions/
│       ├── AiProviderUnavailableException.java
│       ├── AiProviderRateLimitedException.java
│       ├── AiProviderAuthenticationFailedException.java
│       ├── AiRequestTooLargeException.java
│       ├── AiTokenBudgetExceededException.java
│       ├── AiCostBudgetExceededException.java
│       ├── AiTimeoutException.java
│       ├── AiInvalidResponseException.java
│       ├── AiGuardrailRejectedException.java
│       ├── AiStructuredOutputInvalidException.java
│       └── AiConfigurationErrorException.java
│
├── business/
│   ├── AiInvocationPolicy.java            # the orchestrator (§29) — implements GenerateAiUseCase
│   ├── GenerateAiUseCase.java             # inbound port business exposes to (future) task-specific ports
│   ├── PromptService.java                 # layering + versioning (US2)
│   └── ContextBudgetService.java          # context building + token/cost budget checks (US5)
│
└── infrastructure/
    ├── provider/
    │   └── local/
    │       └── LocalAiModelAdapter.java   # implements AiModelPort — OD-2
    ├── prompt/
    │   └── ClasspathPromptRepository.java # loads src/main/resources/prompts/**
    ├── guardrails/
    │   ├── RuleBasedInputGuardrail.java   # implements InputGuardrailPort — OD-4
    │   └── RuleBasedOutputGuardrail.java  # implements OutputGuardrailPort — OD-4
    ├── tokencount/
    │   └── HeuristicTokenCounter.java     # implements TokenCounterPort — OD-5
    ├── validation/
    │   └── StructuredOutputValidator.java # OD-6
    ├── observability/
    │   └── AiTelemetryRecorder.java       # spans (Micrometer Observation) + meters — OD-7
    ├── api/
    │   └── AiDiagnosticEndpoint.java      # @Endpoint("aiDiagnostic") — OD-9
    └── config/
        └── AiProperties.java              # @ConfigurationProperties("ai") — OD-3

architecture/StandardArchitectureRulesTest.java  # + ai.domain/ai.business provider-neutrality rules

src/main/resources/
├── prompts/
│   └── global-system-v1.txt               # FR-011, FR-015
└── application.yml                         # + ai.* config block; + management.otlp.* / management.tracing.*

pom.xml   # + micrometer-tracing-bridge-otel, opentelemetry-exporter-otlp, micrometer-registry-otlp

implementation/platform/
├── infrastructure/local/compose.yaml            # (GATED ON ADR-004) + otel-collector, jaeger, prometheus, grafana
├── infrastructure/observability/                # (GATED ON ADR-004) — OD-8
│   ├── otel-collector-config.yaml
│   ├── prometheus.yml
│   └── grafana/provisioning/{datasources,dashboards}/ + grafana/dashboards/ai-observability.json
├── start.sh / stop.sh                            # (GATED ON ADR-004) health-wait for the 4 new services
└── README.md                                      # + EN006 capability paragraph (no live provider; local/stub only)

backend/core-service/README.md   # + ai module paragraph

frontend/   # NO CHANGE — EN006 has no UI
```

**Structure Decision**: a single new `ai` module inside `core-service`, ADR-003-shaped, with exactly
one concrete `AiModelPort` adapter (`LocalAiModelAdapter`, OD-2) and no persistence. The
observability-stack half of the plan (Compose services, `start.sh`/`stop.sh` changes,
`infrastructure/observability/`) is clearly separated and **gated on ADR-004 approval** so the
module's core logic can be implemented, tested, and merged independently of that approval.

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ADR-004 is rejected or materially changed after this plan is written | Med | Med | The plan explicitly separates "core `ai` module" tasks from "observability stack" tasks (Project Structure, above); `/speckit-tasks` will produce two independently completable checkpoints so a rejection only blocks the second. |
| The rule-based guardrails (OD-4) are too permissive/strict, discovered only once a real feature uses them | Med | Low | Deliberately simple and easy to extend (`List<GuardrailRule>` in code, not a rigid schema); a future AI feature can add task-specific rules without touching `AiModelPort` or `AiInvocationPolicy`'s sequencing. |
| Introducing `micrometer-tracing-bridge-otel` conflicts with an existing Actuator configuration | Low | Med | No existing tracing config exists (verified — no `opentelemetry`/`micrometer-tracing`/`otlp` reference in `pom.xml`/`application.yml` today); `FinnhubConfigurationTest`-style config test added for the new `ai.*` properties; `mvnw -q compile` sanity check after adding the dependency. |
| The internal Actuator diagnostic endpoint (OD-9) is mistaken for a business API by a future contributor | Low | Low | Documented explicitly as operational tooling in `contracts/ai-diagnostic-endpoint.md`; excluded from `openapi.yaml`; guarded by `management.endpoint.ai-diagnostic.enabled` (default `true` locally, can be disabled). |
| Heuristic token counting (OD-5) is wildly inaccurate once a real provider is wired later | Low | Low | Explicitly documented as a placeholder (spec A4); `TokenCounterPort` is the seam a provider-specific counter replaces without touching `AiInvocationPolicy`. |
| Structured-output validation (OD-6) is too weak to catch a real provider's malformed JSON later | Low | Low | `OutputSchema`/`StructuredOutputValidator` cover required-field + type checks — enough for EN006's own fixtures; documented as a placeholder a future adapter may upgrade (spec A1). |
| Grafana dashboard provisioning fails silently (empty dashboard, no error) | Med | Med | `quickstart.md`'s validation procedure explicitly checks the dashboard renders non-empty panels after a diagnostic invocation, not just that Grafana starts. |
| Coverage dip from a large new module with many small exception types | Med | Med | Exception types are simple value carriers (excluded-from-coverage candidates only if trivial — evaluated during implementation against the ≥ 90 % gate, consistent with FD004/EN005's JaCoCo-exclude precedent for genuinely trivial code only). |

## Phase 0 — Research

See [research.md](./research.md). Decisions **D1–D9**: D1 OpenTelemetry integration approach
(Micrometer Tracing bridge + OTLP exporter, Spring Boot native config — no direct OTel SDK
wiring); D2 the deterministic local adapter's exact output-derivation algorithm; D3 structured-output
validation without a new dependency; D4 heuristic token estimation formula; D5 rule-based guardrail
rule set; D6 Actuator diagnostic-endpoint mechanics; D7 Compose observability topology + pinned image
versions (gated on ADR-004); D8 ArchUnit rule additions; D9 test matrix (unit / architecture /
observability-infrastructure). No `NEEDS CLARIFICATION` remains — all resolved in spec.md /
enabler §46.

## Phase 1 — Design & Contracts

Outputs: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).

**Post-Design Constitution re-check: PASS** — the design keeps `ai.domain` / `ai.business`
framework- and provider-free (ArchUnit), adds no provider-identity branch to core, persists nothing
(no migration, no new table, no cross-module persistence access), TDD's all deterministic logic
(`AiInvocationPolicy`, `PromptService`, `ContextBudgetService`, guardrails, budgets), needs no live
provider or Testcontainers, adds no external business REST API (`openapi.yaml` untouched; the one
new HTTP surface is Actuator tooling, documented but out of the business contract), adds no new
independently deployable service, and introduces no LLM provider SDK. The **one** genuinely new
architectural decision (the local observability topology) is captured in ADR-004 and explicitly
gated on human approval rather than silently implemented — the design is otherwise ready for
`/speckit-tasks`, with tasks split so the `ai` module itself does not wait on that approval.
