# Feature Specification: Establish Provider-Neutral AI Model Integration (EN006)

**Feature Branch**: `EN006-establish-ai-model-integration`

**Created**: 2026-09-04

**Status**: Draft (spec) — Enabler **Approved** (§47 signed by jaruiz 2026-09-04)

**Input**: Technical Enabler: "Establish a provider-neutral AI model integration capability for
My-FinAI-Manager so future business capabilities (Portfolio Analysis, Risk Explanation, Natural-
Language Portfolio Summary, News Interpretation, Recommendation Explanation, Ask My Portfolio,
Scenario Explanation, agentic workflows) can use LLMs and future AI models without coupling
core/business logic to a specific vendor, SDK, API contract, model family, or deployment platform —
including prompt governance/versioning, guardrails, structured output, token/context/cost control,
provider-neutral error handling, and OpenTelemetry-based observability with a local Docker Compose
observability stack (Collector, Jaeger, Prometheus, Grafana). EN006 does not define any specific AI
business feature."

**Authoritative Source**:
`product/definition/enablers/EN006-establish-ai-model-integration/EN006-establish-ai-model-integration.md`
(**Status: Approved** — §47 signed by jaruiz 2026-09-04; §46 open technical decisions resolved the
same day).

**Supports**: Future AI-assisted Portfolio analysis, explanations, risk insights, recommendations,
news intelligence, conversational portfolio interaction, and agentic capabilities. Those future
features are **not** part of EN006.

**Governing Architecture**: ADR-001 (single `core-service` deployable — unchanged), ADR-002
(unaffected), ADR-003 (Standard Spring Backend Architecture — the new `ai` module follows the same
`domain` / `business` / `infrastructure` layout as `portfolio`, `financialinstrument`, and
`marketdata`). **A new ADR is anticipated during `/speckit-plan`** —
`ADR-004-local-ai-observability-stack.md` — because adding OpenTelemetry Collector, Jaeger,
Prometheus, and Grafana to the local Docker Compose / `start.sh` / `stop.sh` lifecycle is a
significant local-runtime/operational change (CLAUDE.md §20), even though no backend topology,
persistence ownership, or public API changes. If planning surfaces any further material
architectural decision it MUST be raised for human approval (constitution IV).

---

## Clarifications

### Session 2026-09-04 (pre-specification — resolved on the enabler before formalization)

EN006 was `Status: Draft` with §47 "Human Approval" entirely unchecked and unsigned. Per CLAUDE.md
§26 (Ambiguity Policy) and `product/governance/ai-development-policy.md` ("Human Approval
Boundaries" — architecture topology, technology policy, security boundaries), specification did not
proceed until the product owner resolved the most scope-critical items among the enabler's 17 open
technical decisions (§46) and signed §47. All three were accepted as recommended:

- Q1 — Does EN006 ship a real external AI provider adapter, or only a deterministic local/stub
  adapter? → A: **Local/stub adapter only.** EN006 delivers the full port + policy + prompt +
  guardrail + observability skeleton plus **one** deterministic in-repo adapter with no outbound
  network call and no API key, proving `AiModelPort` is implementable (VC-002) and driving the
  observability verification (VC-024–031). A real provider (Anthropic/OpenAI/Bedrock/Vertex) is
  deferred to the first AI feature that needs one (enabler §6 already allows this reading; §46
  resolved it explicitly). *(→ FR-005, FR-033, FR-058; Key Entities)*
- Q2 — How sophisticated are the initial guardrails? → A: **Rule-based only.** Deterministic checks
  (size limits, prompt-injection/system-override heuristics, schema validation) implement the
  mandatory input/output guardrail extension points; no AI-based guardrail model in EN006;
  provider-native guardrails remain a possible future complement, never a replacement. *(→ FR-020…
  FR-025)*
- Q3 — Does the local AI observability stack (OTel Collector/Jaeger/Prometheus/Grafana in Docker
  Compose) require a new ADR? → A: **Yes — a new ADR-004**, drafted during `/speckit-plan`, because
  it materially changes local runtime/operational topology even though it doesn't touch backend
  topology or data ownership. *(→ Governing Architecture, Dependencies)*

The remaining 14 open items in §46 (exact HTTP/SDK strategy, structured-output mechanism, guardrail
mechanics, token-estimation implementation, pricing configuration source, prompt-template storage,
redaction implementation, OpenTelemetry semantic-convention version, retry backoff, timeout/cost
defaults, model-routing mechanism, response caching) are deferred to `/speckit-plan` — the enabler
itself allows this ("may be resolved during specification/planning").

---

## Enabler Nature *(mandatory)*

EN006 is a **Technical Enabler**, not a product Feature Definition. It introduces **no**
investor-facing behavior, no new Portfolio/Position capability, no AI-generated recommendation, and
no UI. Its purpose is a **provider-neutral backend capability** for invoking AI/LLM models, so a
future business feature depends only on provider-neutral ports, never on a vendor SDK.

Because this is an enabler:

- The scenarios below describe **backend-developer / maintainer / operator** workflows.
- "Acceptance" is expressed through the enabler's **Verification Criteria VC-001…VC-031** — see the
  Traceability table.
- No business domain entities or rules are added, removed, or reinterpreted. `AiRequest`,
  `AiResponse`, and `AiUsage` are **provider-neutral, non-persisted** invocation models — not
  Portfolio/Position/Investor business data.
- EN006 has **no application E2E of its own** and needs none — enabler §42 explicitly says so. Its
  observability chain (FR-054) is verified with the deterministic local/stub adapter, not a browser
  journey. A future AI feature that builds on EN006 must bring its own deterministic E2E with the AI
  provider boundary stubbed (§42).
- Deterministic financial logic (Portfolio valuation, FX conversion, position weights, sector
  allocation — FD004, EN005) is **untouched** and stays outside AI, per constitution VI and this
  enabler's §3.2, §34.
- `product/governance/ai-development-policy.md` is authoritative for how AI participates in this
  project's *development*; this enabler is authoritative for the *product's own* AI integration
  capability — the two are related but distinct, and this spec follows the latter while respecting
  the former's guardrails (provider neutrality, no fabricated facts, human approval boundaries).

---

## User Scenarios & Testing *(mandatory)*

Facets of one capability, ordered by importance. "Test" = the deterministic automated check that
proves the facet — none requires a live external AI provider.

### User Story 1 — One provider-neutral `AiModelPort`, proven by a deterministic local adapter (Priority: P1)

As a backend developer, I invoke AI generation through a single provider-neutral `AiModelPort`
(`AiRequest → AiResponse`), and the only concrete implementation EN006 ships is a **deterministic
local/stub adapter** — no vendor SDK, no API key, no outbound network call — that still exercises
the whole request/response contract so future provider adapters (Anthropic, OpenAI, Bedrock,
Vertex, or an on-prem model) can be added later without touching `ai.domain` or `ai.business`.

**Why P1**: The enabler's core architectural contract (§3, §5, §45.1–4; VC-001, VC-002, VC-003;
resolved Q1).

**Test**: ArchUnit — `ai.domain` / `ai.business` reference no provider SDK type, provider DTO, or
provider client package. A business-level test drives `AiInvocationPolicy` end-to-end against the
local adapter and asserts a populated `AiResponse` with deterministic content. A hypothetical
second adapter (added only as a test double) can implement `AiModelPort` with no change to any
existing class.

**Acceptance**:
1. **Given** the module, **When** its ports are enumerated, **Then** `AiModelPort` is the sole
   generic model-invocation port, and it is provider-neutral (no vendor type in its signature).
2. **Given** the shipped local/stub adapter, **When** it is inspected, **Then** it makes no network
   call, requires no credential, and returns deterministic, reproducible output for a given input.
3. **Given** a hypothetical new provider adapter, **When** it is added purely as an alternative
   `AiModelPort` implementation, **Then** `ai.domain`, `ai.business`, and any consumer need **no**
   change beyond configuration (VC-003, VC-013).

### User Story 2 — Governed, versioned, layered prompts (Priority: P1)

As a backend developer, I compose every AI request from a **centralized** prompt-layering service —
Global System Prompt + Task Instructions + Business Context + User/Task Prompt — never by
hand-building strings in a controller or adapter, and every invocation identifies the `promptId`
and `promptVersion` that produced it.

**Why P1**: Enabler §10–§12, §32, §45.6–9; VC-004, VC-005.

**Test**: `PromptService` unit test — composing a request for a given task yields the expected
layered prompt and an identified `promptId`/`promptVersion`; requesting an unknown task/version
fails explicitly (`AiConfigurationError`), never silently falls back to a different prompt.

**Acceptance**:
1. **Given** a task and its context, **When** a prompt is composed, **Then** the result layers the
   global system prompt, task instructions, business context, and the user/task prompt, in that
   order, from **one** composition service.
2. **Given** any AI invocation, **When** it completes, **Then** the `promptId` and `promptVersion`
   used are identifiable on the invocation record.
3. **Given** the global system prompt's content, **When** inspected, **Then** it reinforces: never
   invent financial facts; use only supplied facts; never replace deterministic calculations; state
   uncertainty explicitly; never fabricate missing prices/sectors/currencies/portfolio data; never
   claim an unexecuted action was executed; treat output as decision support, not autonomous
   execution (§10, §34).

### User Story 3 — Structured output, validated before it is trusted (Priority: P1)

As a backend developer, when a task needs a machine-consumable result, I request a typed/JSON
structured output with a schema, and an invalid or non-conforming response is **rejected
explicitly** — never silently accepted, coerced, or partially trusted.

**Why P1**: Enabler §13, §45.9; VC-006.

**Test**: A schema-validation unit test over a fixture response — a conforming payload returns
`structuredContent`; a payload missing a required field, with a wrong type, or that is not valid
JSON raises `AiStructuredOutputInvalid` and produces no partial `structuredContent`.

**Acceptance**:
1. **Given** an `AiRequest` with an `outputSchema`, **When** the response conforms, **Then**
   `AiResponse.structuredContent` is populated and typed per the schema.
2. **Given** a response that does not conform, **When** it is validated, **Then** the call fails
   explicitly with `AiStructuredOutputInvalid`, and `structuredContent` is absent.

### User Story 4 — Rule-based input/output guardrails (Priority: P1)

As an operator, unsafe or out-of-policy input never reaches the model, and a malformed or
policy-violating response never reaches the caller — both enforced by **deterministic, rule-based**
checks, with the extension point open for a future AI-based or provider-native guardrail without
requiring one today (resolved Q2).

**Why P1**: Enabler §14–§16, §45.10–11; VC-007, VC-008.

**Test**: `InputGuardrailPort` unit tests — an oversized input, and an input containing a
system-prompt-override pattern, are both rejected before any provider call (asserted via a spy on
the local adapter: it is never invoked). `OutputGuardrailPort` unit tests — a response that fails
schema validation or contains a prohibited-action phrase ("I have already sold your position…") is
rejected after the call, before it reaches the caller.

**Acceptance**:
1. **Given** an oversized or injection-pattern input, **When** it is submitted, **Then** it is
   rejected by an input guardrail with `AiGuardrailRejected` **before** the provider is invoked.
2. **Given** a response violating an output guardrail, **When** it is evaluated, **Then** it is
   rejected with `AiGuardrailRejected` **before** it reaches the caller — never returned partially.
3. **Given** either rejection, **When** inspected, **Then** the failure identifies which guardrail
   rejected and why (provider-neutral, no raw exception).

### User Story 5 — Token, context, and cost budgets are enforced, not just recorded (Priority: P1)

As an operator, every request is checked against configurable token and estimated-cost budgets
**before** the model is called; context is explicitly built from domain data rather than blindly
serialized; and actual usage is captured on the response when available.

**Why P1**: Enabler §20–§24, §45.15–20; VC-010, VC-011, VC-012, VC-014, VC-015.

**Test**: `ContextBudgetService` unit test — a domain object graph is reduced to a compact,
provider-neutral context that excludes irrelevant/sensitive fields and stays within a configured
size. `AiInvocationPolicy` unit test — a request whose estimated tokens exceed `max-input-tokens`
(or `max-total-tokens`) is rejected with `AiTokenBudgetExceeded` **without** invoking the adapter;
a request whose estimated cost exceeds a configured limit is rejected with `AiCostBudgetExceeded`
the same way. A successful invocation's `AiResponse`/`AiUsage` carries token counts (deterministic,
clearly-synthetic values from the local adapter, since no real provider is called).

**Acceptance**:
1. **Given** a request estimated to exceed the configured token budget, **When** it is submitted,
   **Then** it is rejected with `AiTokenBudgetExceeded` and the adapter is never invoked.
2. **Given** a request estimated to exceed the configured cost budget, **When** it is submitted,
   **Then** it is rejected with `AiCostBudgetExceeded` and the adapter is never invoked.
3. **Given** domain data destined for a prompt, **When** the context is built, **Then** it is
   explicitly assembled (not a blind serialization), excludes irrelevant/sensitive fields, and stays
   within the configured context budget.
4. **Given** a completed invocation, **When** its `AiResponse` is inspected, **Then** it carries
   input/output/total token counts and, where available, an estimated cost.

### User Story 6 — Provider-neutral errors, bounded timeout, bounded retry (Priority: P1)

As a backend developer, every failure I can observe from `AiModelPort` is one of a fixed,
provider-neutral set — never a raw provider exception — every call has a bounded timeout, and
retries happen only for genuinely transient failures, never for a guardrail rejection, a budget
violation, or an authentication failure.

**Why P1**: Enabler §26–§29, §45.22–23; VC-018, VC-019.

**Test**: Error-mapping unit test — each simulated provider-adapter failure mode (timeout,
unavailable, rate-limited, auth failure, oversized request) maps to its corresponding
provider-neutral type from §26. A retry-policy unit test — a transient failure is retried up to a
bounded count; a guardrail rejection, schema-validation failure, auth failure, or budget violation
is **never** retried.

**Acceptance**:
1. **Given** any adapter failure mode, **When** it is translated, **Then** the caller sees one of the
   provider-neutral failures (§26) — never a provider-specific type or payload.
2. **Given** any AI call, **When** it runs, **Then** it is bounded by a configurable timeout.
3. **Given** a transient failure, **When** it occurs, **Then** it may be retried, bounded in count;
   given a non-transient failure (guardrail/schema/auth/budget), **When** it occurs, **Then** it is
   **never** retried.

### User Story 7 — Observable without exposing sensitive content, end to end through the local stack (Priority: P1)

As an operator, I can inspect a deterministic AI invocation as a trace in Jaeger, its metrics in
Prometheus, and a pre-built AI dashboard in Grafana — brought up and torn down by the same
`start.sh` / `stop.sh` I already use for the rest of the platform — with **zero** raw prompt,
response, credential, or Portfolio content anywhere in that telemetry.

**Why P1**: Enabler §17–§19, §36–§37, §45.14, §45.29–37 (resolved Q3 — new ADR-004); VC-016, VC-017,
VC-024…VC-031.

**Test**: An observability integration check against the local Compose stack — invoke the
deterministic local/stub adapter, then assert: (a) a trace with the nested span chain (business
operation → AI use-case span → AI invocation span → provider-call span) is queryable in Jaeger; (b)
`ai_requests_total`, `ai_request_duration`, token, error, and guardrail-rejection metrics are
queryable in Prometheus; (c) the Grafana AI dashboard is auto-provisioned (Prometheus data source +
dashboard, no manual step) and renders those series. A content-safety check scans the emitted spans,
metric labels, and dashboard definition for raw prompt/response text, Portfolio identifiers, user
identifiers, and credentials, and finds none.

**Acceptance**:
1. **Given** `./start.sh`, **When** the platform comes up, **Then** the OpenTelemetry Collector,
   Jaeger, Prometheus, and Grafana are healthy alongside the existing services, with **no** separate
   manual startup step.
2. **Given** a deterministic AI invocation, **When** it completes, **Then** its trace is visible in
   Jaeger and its metrics are visible in Prometheus and on the Grafana AI dashboard.
3. **Given** any of that telemetry, **When** inspected, **Then** it contains provider, model, task,
   prompt id/version, token usage, latency, success, guardrail result, correlation id — and **never**
   a raw prompt/response body, Portfolio position detail, user identifier, or credential.
4. **Given** `./stop.sh`, **When** it runs, **Then** the four observability services stop with the
   rest of the platform, and a second `./stop.sh` is safe (idempotent).

### User Story 8 — Deterministic tests; CI never depends on a live AI provider (Priority: P2)

As a maintainer, I can run `./mvnw verify` with **no** outbound Internet call to any AI provider:
every guardrail, budget, prompt-composition, structured-output, and error-mapping behavior is
covered by fast unit tests against the local/stub adapter, and the observability proof (US7) runs
against the local Compose stack, never a live vendor.

**Why P2**: Enabler §40–§42, §45.24; VC-020.

**Test**: `./mvnw -B clean verify` green offline — unit tests for prompt composition/versioning,
context/token/cost budgets, guardrails, structured-output validation, sensitive-data sanitization,
error mapping, usage aggregation; ArchUnit enforcing the provider-neutral core; ≥ 90 % line & branch
coverage.

**Acceptance**:
1. **Given** CI, **When** the suite runs, **Then** no test reaches any external AI provider endpoint.
2. **Given** the full gate set, **When** it runs, **Then** FD001–FD004 / EN004 / EN005 suites and
   E2Es stay green — EN006 is purely additive.

### Edge Cases

- **Missing financial fact** — the AI layer must never invent a plausible value; the system prompt
  and guardrails reinforce "insufficient information," never a fabricated price/sector/currency
  (§10, §34; VC-022; `ai-development-policy.md` "Hallucination Handling").
- **Prompt injection in (future) external/user content** — system instructions carry higher priority
  than retrieved/user content, which is always treated as untrusted data (§35). EN006 itself has no
  external content source yet; the extension point (input guardrails) is what a later feature will
  rely on.
- **Guardrail rejects a request** — a controlled, provider-neutral `AiGuardrailRejected`, never a
  retry, never a partial result.
- **Token or cost budget exceeded** — a controlled rejection before invocation; context is never
  silently truncated in a way that changes business meaning (§23).
- **Timeout** — `AiTimeout`, not retried indefinitely; bounded retry only for genuinely transient
  failures (§27, §37).
- **Malformed / non-conforming structured output** — rejected explicitly (`AiStructuredOutputInvalid`),
  never partially trusted (§13, §16).
- **No cost data available for the active provider/model** — usage/cost reporting and the Grafana
  dashboard must tolerate missing cost metrics without failing (§19.8, §24).
- **Observability stack unavailable** (Collector/Jaeger/Prometheus/Grafana down) — an AI invocation's
  business result must not depend on telemetry export succeeding; a failed/delayed export must not
  fail or block the caller's business operation (standard OpenTelemetry exporter behavior; not a new
  rule invented here — kept consistent with "AI must not be a single point of failure for unrelated
  business behavior").
- **Two invocations sharing a `correlationId` via transport retry** — the `invocationId` remains the
  distinguishing key so a business generation is not confused with a network-level retry (§28).
- **Prompt id/version requested but not found** — explicit `AiConfigurationError`, never a silent
  fallback to a different prompt (US2 test above).

---

## Requirements *(mandatory)*

> Each FR traces to an enabler section / VC. All three pre-specification clarifications (Q1–Q3) are
> resolved and folded in below.

### Provider-neutral core & module structure

- **FR-001**: The system MUST expose exactly one generic, provider-neutral model-invocation port,
  `AiModelPort` (`AiRequest → AiResponse`), in `ai.domain.ports`. *(§5; VC-001)*
- **FR-002**: Business features SHOULD depend on higher-level, task-specific AI ports layered above
  `AiModelPort` rather than calling it directly; EN006 defines the pattern but no task-specific port
  itself, since it introduces no business AI feature. *(§5, §45.5)*
- **FR-003**: The new `ai` module MUST follow ADR-003's layout — `domain/{model,ports,exceptions}`,
  `business`, `infrastructure/{provider,prompt,guardrails,observability,config}` — with dependencies
  pointing inward. *(§30; VC-023)*
- **FR-004**: `ai.domain` and `ai.business` MUST NOT reference provider SDK types, provider
  request/response DTOs, provider-specific message types, provider token counters, provider
  authentication, or provider-specific tool-calling schemas. Enforced by an architecture test.
  *(§3.1, §43; VC-001)*
- **FR-005**: EN006 MUST ship exactly **one** concrete `AiModelPort` implementation — a
  deterministic **local/stub adapter** with no outbound network call, no API key, and reproducible
  output for a given input — sufficient to prove the port is implementable (VC-002) and to drive
  the observability verification (FR-054). EN006 MUST NOT implement a real external provider
  adapter (Anthropic/OpenAI/Bedrock/Vertex/local-model-server); that is deferred to the first AI
  feature that needs one. *(resolved Q1; §6, §45.1; VC-002)*
- **FR-006**: Provider/model selection MUST be configuration-driven (e.g. `ai.default-provider`,
  `ai.default-model`), resolved entirely in infrastructure; `ai.domain` and `ai.business` MUST
  contain no provider-identity branching. Enforced by an architecture test, mirroring EN005's
  per-capability provider-selection pattern. *(§7, §45.4; VC-003, VC-004)*

### Provider-neutral request / response model

- **FR-007**: `AiRequest` MUST carry: task type, system prompt reference, user prompt, context,
  optional output schema, maximum output tokens, optional temperature, metadata, and a correlation
  id — with no provider-specific type anywhere in its shape. *(§8)*
- **FR-008**: `AiResponse` MUST carry: content, optional structured content, provider, model,
  optional input/output/total tokens, optional estimated cost, latency, optional finish reason,
  optional request id, and a generation timestamp. *(§9)*
- **FR-009**: Only normalized, provider-neutral metadata MAY cross the adapter boundary into
  `AiResponse`; provider payload types MUST stay inside `infrastructure`. *(§9, §3.1)*
- **FR-010**: Every invocation MUST carry a `correlationId` and an `invocationId`, so a transport
  retry can be distinguished from a new business generation. *(§28)*

### Prompt governance

- **FR-011**: The system MUST support a governed, configurable, **versioned global system prompt**
  whose content is a governance/configuration artifact, not a string literal scattered through code.
  *(§10; VC-004 wording via §45.6)*
- **FR-012**: Prompt composition MUST layer Global System Prompt + Task Instructions + Business
  Context + User/Task Prompt, centralized in one composition service — never duplicated across
  controllers or adapters. *(§11, §45.7)*
- **FR-013**: Prompts MUST be organized as identifiable, versioned units (`promptId` +
  `promptVersion`); the initial storage mechanism is classpath resources (exact layout is a planning
  detail — §46). *(§12, §31)*
- **FR-014**: Every AI invocation MUST identify the `promptId` and `promptVersion` that produced it.
  *(§12, §32; VC-005)*
- **FR-015**: The initial global system prompt MUST reinforce, at minimum: never invent financial
  facts; use only supplied facts unless a task explicitly enables external retrieval; never replace
  deterministic financial calculations; state uncertainty explicitly; never fabricate missing
  prices/sectors/currencies/Portfolio data; never claim an action was executed unless it actually
  was; treat output as decision support, not autonomous execution. *(§10)*
- **FR-016**: The shape for recording AI-generation provenance on a future persisted business
  artifact (provider, model, promptId, promptVersion, generatedAt, token counts, correlationId,
  optional source-snapshot id) MUST be defined by EN006; EN006 itself persists no such artifact.
  *(§32, §33)*

### Structured output

- **FR-017**: The system MUST support requesting a typed/JSON structured response via an
  `outputSchema` on `AiRequest`. *(§13; VC-006)*
- **FR-018**: A structured response MUST be validated against the requested schema before being
  exposed as `AiResponse.structuredContent`. *(§13; VC-006)*
- **FR-019**: An invalid or non-conforming structured response MUST be rejected explicitly
  (`AiStructuredOutputInvalid`) — never silently coerced, truncated, or partially accepted. *(§13,
  §26)*

### Guardrails (rule-based)

- **FR-020**: The system MUST provide an `InputGuardrailPort` (invoked before the provider call) and
  an `OutputGuardrailPort` (invoked after it) as mandatory extension points. *(§14; VC-007, VC-008)*
- **FR-021**: The initial guardrail implementation MUST be **rule-based / deterministic only** — no
  AI-based guardrail model in EN006. Provider-native guardrails MAY complement but MUST NOT replace
  the rule-based checks. *(resolved Q2; §14, §45.10–11)*
- **FR-022**: Input guardrails MUST, at minimum, enforce an input-size limit and detect basic
  prompt-injection / system-prompt-override patterns before the provider is invoked. *(§15)*
- **FR-023**: Output guardrails MUST, at minimum, validate structured-response schema conformance
  and detect prohibited-action language (e.g. a claim that a financial action was executed) before
  the response reaches the caller. *(§16)*
- **FR-024**: A guardrail rejection (input or output) MUST produce a controlled, provider-neutral
  `AiGuardrailRejected` failure identifying which guardrail rejected and why — never a raw exception
  or a partial result. *(§16, §26)*
- **FR-025**: Guardrail checks MUST be unit-testable without a live provider call. *(§40; VC-020)*

### Token, context & cost control

- **FR-026**: The system MUST support configurable maximum input, output, and total token limits.
  *(§20; VC-011)*
- **FR-027**: The system MUST support pre-invocation token counting/estimation via a
  `TokenCounterPort`; conservative estimation is acceptable when exact counting is unavailable.
  *(§22)*
- **FR-028**: Before invoking a provider, estimated token usage MUST be checked against the
  configured budget; exceeding it MUST produce a controlled `AiTokenBudgetExceeded` rejection — never
  a silent truncation that changes business meaning. *(§23; VC-011)*
- **FR-029**: The system MUST provide a context-building layer (`AiContextBuilder` /
  `ContextBudgetService`) that assembles a compact, provider-neutral context from domain data —
  removing irrelevant data, avoiding provider metadata, avoiding sensitive data, and staying inside
  the configured budget — rather than blindly serializing arbitrary domain objects. *(§21; VC-013)*
- **FR-030**: Actual provider-reported token usage MUST be collected and recorded on
  `AiResponse`/`AiUsage` when available. *(§22, §24; VC-010)*
- **FR-031**: The system MUST support estimated-cost metadata (`AiUsage.estimatedCost`) and a
  configurable maximum estimated cost per request; exceeding it MUST produce a controlled
  `AiCostBudgetExceeded` rejection. *(§24; VC-014, VC-015)*
- **FR-032**: Common generation parameters (temperature, maximum output tokens, optionally top-p)
  MUST be configurable centrally per task, not scattered through business code. *(§12, §25; VC-012)*
- **FR-033**: Because EN006 ships no live provider (FR-005), the local/stub adapter MUST still
  populate `AiUsage` with deterministic, clearly-synthetic token/cost figures, so metrics,
  dashboards, and any future consumer can be exercised end-to-end without a real provider. *(resolved
  Q1)*

### Error model, timeout & retry

- **FR-034**: Provider-specific errors MUST be translated into the provider-neutral failure set:
  `AiProviderUnavailable`, `AiProviderRateLimited`, `AiProviderAuthenticationFailed`,
  `AiRequestTooLarge`, `AiTokenBudgetExceeded`, `AiCostBudgetExceeded`, `AiTimeout`,
  `AiInvalidResponse`, `AiGuardrailRejected`, `AiStructuredOutputInvalid`, `AiConfigurationError`.
  *(§26; VC-019)*
- **FR-035**: Provider error payloads MUST NOT leak through any public business API. *(§26)*
- **FR-036**: Every AI call MUST have a bounded, configurable timeout. *(§27; VC-018)*
- **FR-037**: Retries MUST be allowed only for transient failures (temporary provider failure,
  connection reset, rate-limit retry-after) and MUST be bounded; invalid prompt, guardrail
  rejection, schema-validation failure, authentication failure, and budget violations MUST NEVER be
  retried. *(§27)*
- **FR-038**: An `AiInvocationPolicy` MUST centralize: task validation, provider/model resolution,
  prompt composition/versioning, context sanitization, token-budget enforcement, cost-budget
  enforcement, input guardrails, provider invocation, output guardrails, structured-output
  validation, and usage-metadata collection. Business features MUST NOT reimplement these concerns
  independently. *(§29)*

### Sensitive data & logging

- **FR-039**: The system MUST support an optional sanitization/redaction stage between business
  context and the outgoing `AiRequest`. *(§17)*
- **FR-040**: By default, production logs and traces MUST NOT contain: system-prompt body,
  user-prompt body, full context, full completion, API keys/provider credentials, user identity /
  email / account identifiers, or raw Portfolio payloads. *(§17, §36; VC-009)*
- **FR-041**: Prompt-content debug logging, if ever supported, MUST be local-only, explicit, and
  disabled by default. *(§36)*
- **FR-042**: Provider secrets/configuration (e.g. `AI_API_KEY`) MUST be externalized — never
  hardcoded, committed, returned, logged, traced, or exposed to frontend code — mirroring EN005's
  `FINNHUB_API_KEY` pattern. Since EN006 ships no real provider (FR-005), no real AI provider key
  exists in EN006's own configuration; the pattern is established for the adapter(s) a future feature
  adds. *(§38)*

### Observability — application-level & OpenTelemetry

- **FR-043**: Each AI invocation MUST be observable via structured logs/metrics/traces capturing at
  minimum: provider, model, task, promptId, promptVersion, input/output/total tokens, latency,
  success, finish reason, guardrail result, and correlation id (plus estimated cost and provider
  request id where available) — without the sensitive content excluded by FR-040. *(§18; VC-016)*
- **FR-044**: AI invocations MUST participate in the platform's OpenTelemetry tracing as nested spans
  (business operation → AI use-case span → AI invocation span → provider-call span), using `gen_ai.*`
  semantic-convention attributes where applicable plus the `ai.*` application attributes above.
  *(§19; VC-017)*
- **FR-045**: Metrics MUST be emitted at minimum for: request count, request duration, input/output
  token totals, error count, guardrail-rejection count, and estimated cost total. Metric labels MUST
  remain low-cardinality — never a user id, Portfolio id, or raw prompt/response text. *(§19.6, §37)*
- **FR-046**: This is the first OpenTelemetry integration in `core-service`; introducing the
  OpenTelemetry SDK/exporter dependency is within technology policy (`OpenTelemetry` is
  `PREFERRED`) and requires no additional approval beyond this specification.
- **FR-047**: Telemetry export MUST be configurable via environment/runtime configuration (e.g.
  `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME`) and MUST NOT carry AI provider credentials.
  *(§19.4)*

### Local observability stack & platform lifecycle

- **FR-048**: The local `implementation/platform` Docker Compose environment MUST include an
  **OpenTelemetry Collector** that receives OTLP traces/metrics from `core-service` and forwards
  traces to Jaeger and metrics to a Prometheus-compatible path; its configuration MUST NOT export
  raw prompt/completion bodies. *(§19.3; VC-024, VC-025)*
- **FR-049**: The local Compose environment MUST include **Jaeger** for trace inspection, so a
  developer can see the nested span chain (FR-044) without sensitive content. *(§19.5; VC-026)*
- **FR-050**: The local Compose environment MUST include **Prometheus**, configured to scrape/query
  the metrics from FR-045 over time. *(§19.6; VC-027)*
- **FR-051**: The local Compose environment MUST include **Grafana**, automatically provisioned
  (no manual post-start setup) with a Prometheus data source and a version-controlled AI
  observability dashboard showing at minimum: request volume (by task, by provider/model); latency
  (avg/p95, by provider/model); token usage (input/output/total, over time, by task, by model);
  estimated cost (tolerating missing cost data without failing); errors (provider errors, timeouts,
  rate-limit failures, invalid structured output); and guardrail activity (input/output rejections,
  rejection rate). *(§19.7, §19.8; VC-028, VC-029)*
- **FR-052**: The dashboard and all telemetry MUST NOT expose raw prompts, raw responses, Portfolio
  position details, user names/identifiers, account data, API keys, or provider credentials. *(§19.9;
  VC-030)*
- **FR-053**: The four observability services (Collector, Jaeger, Prometheus, Grafana) MUST start and
  stop through the existing canonical `start.sh` / `stop.sh` lifecycle — no separate, undocumented
  manual startup procedure. *(§19.10; VC-031)*
- **FR-054**: EN006 verification MUST demonstrate the full chain — a deterministic AI invocation
  (via the local/stub adapter, FR-005) produces a trace visible in Jaeger, and its metrics are
  queryable in Prometheus and visible on the Grafana dashboard — without requiring a live external
  AI provider. *(§19.11)*

### Testing & determinism

- **FR-055**: Automated tests MUST NOT depend on a live AI provider; CI MUST run fully offline with
  respect to AI. *(§40; VC-020)*
- **FR-056**: Unit tests MUST cover: prompt composition, prompt version selection, context-budget
  enforcement, token-budget enforcement, cost-budget enforcement, input-guardrail rejection,
  output-guardrail rejection, structured-output validation, sensitive-data sanitization,
  provider-neutral error mapping, and AI usage aggregation. *(§40)*
- **FR-057**: An architecture test (ArchUnit) MUST enforce that `ai.domain` and `ai.business` do not
  depend on provider SDKs, provider DTOs, or provider client packages. *(§43; VC-001)*
- **FR-058**: An opt-in real-provider smoke test MAY be added later — outside EN006, only once a
  real provider adapter exists — but MUST be excluded from normal CI, safe for credentials, and
  bounded by token/cost limits. EN006 itself needs none, since it ships no real provider adapter
  (FR-005). *(§41)*

### Scope guardrails

- **FR-059**: EN006 MUST NOT define any specific AI business feature, use case, or investor-facing
  behavior (e.g. Portfolio Analysis, Risk Explanation, Ask My Portfolio, News Interpretation).
- **FR-060**: EN006 MUST NOT implement RAG, vector databases, embeddings, agent orchestration,
  tool-calling as a business capability, MCP, human-in-the-loop workflows, autonomous trading, or
  model fine-tuning/training.
- **FR-061**: EN006 MUST NOT replace or reimplement any existing deterministic financial calculation
  (Portfolio valuation, FX conversion, position weights, sector percentages) — those remain
  exclusively owned by the `portfolio` module (FD004) and EN005. *(§3.2, §34; VC-021, VC-022)*
- **FR-062**: EN006 MUST NOT introduce a new independently deployable service — the `ai` module lives
  inside the existing single `core-service` deployable (ADR-001).
- **FR-063**: EN006 MUST NOT introduce automatic multi-provider fallback — deferred per §45.28/§46.

### Key Entities *(include if feature involves data)*

- **AiRequest** *(provider-neutral, not persisted)* — taskType, systemPrompt reference, userPrompt,
  context, optional outputSchema, maxOutputTokens, optional temperature, metadata, correlationId.
- **AiResponse** *(provider-neutral, not persisted)* — content, optional structuredContent,
  provider, model, optional inputTokens/outputTokens/totalTokens, optional estimatedCost,
  latencyMs, optional finishReason, optional requestId, generatedAt.
- **AiUsage** *(provider-neutral, embedded in `AiResponse` / recorded for telemetry)* —
  inputTokens, outputTokens, totalTokens, provider, model, estimatedCost.
- **PromptReference** *(governance/configuration artifact)* — promptId, promptVersion, purpose,
  owner, status; resolved by `PromptService` from classpath resources initially.
- **AiModelPort** *(domain port)* — `AiResponse generate(AiRequest)`; exactly one implementation in
  EN006, the deterministic local/stub adapter.
- **TokenCounterPort / InputGuardrailPort / OutputGuardrailPort** *(domain ports)* — pre-invocation
  estimation and rule-based input/output validation extension points.
- **AiInvocationPolicy** *(business orchestrator)* — the single place that sequences validation,
  provider/model resolution, prompt composition, sanitization, budget enforcement, guardrails,
  invocation, and usage collection (§29).
- **Local/stub AI adapter** *(the one shipped `infrastructure` provider)* — deterministic, no
  network call, no credential; used both to prove `AiModelPort` and to drive the observability
  chain end-to-end.
- **Provider/model configuration** *(infrastructure configuration, not a domain entity)* —
  `ai.default-provider`, `ai.default-model`, per-task overrides (future), token/cost limits,
  timeouts.

*(No Portfolio/Position/Investor entity is created, read, or changed by EN006. No AI-generated
business artifact is persisted by EN006 — that belongs to a future feature that consumes this
enabler.)*

---

## Traceability to Enabler Verification Criteria (§44)

| VC | Covered by |
|---|---|
| VC-001 Provider-neutral core | US1; FR-001, FR-004, FR-006 |
| VC-002 Provider adapter | US1; FR-005 |
| VC-003 Provider replaceability | US1 (AS3); FR-006 |
| VC-004 System prompt | US2; FR-011 |
| VC-005 Prompt versioning | US2; FR-013, FR-014 |
| VC-006 Structured output | US3; FR-017, FR-018, FR-019 |
| VC-007 Input guardrails | US4 (AS1); FR-020, FR-022 |
| VC-008 Output guardrails | US4 (AS2); FR-020, FR-023 |
| VC-009 Sensitive data | US7 (AS3); FR-040 |
| VC-010 Token usage | US5 (AS4); FR-030 |
| VC-011 Token budget | US5 (AS1); FR-026, FR-028 |
| VC-012 Output limit | US5; FR-026, FR-032 |
| VC-013 Context control | US5 (AS3); FR-029 |
| VC-014 Cost metadata | US5 (AS4); FR-031 |
| VC-015 Cost guardrail | US5 (AS2); FR-031 |
| VC-016 Observability | US7 (AS3); FR-043 |
| VC-017 OpenTelemetry | US7; FR-044 |
| VC-018 Timeout | US6 (AS2); FR-036 |
| VC-019 Provider-neutral errors | US6 (AS1); FR-034, FR-035 |
| VC-020 Deterministic tests | US8; FR-055 |
| VC-021 Financial determinism | FR-061 |
| VC-022 Hallucination constraint | Edge Cases; FR-015, FR-061 |
| VC-023 ADR-003 compliance | US1; FR-003 |
| VC-024 Local observability stack | US7 (AS1); FR-048 |
| VC-025 Collector integration | US7; FR-048 |
| VC-026 Trace visualization | US7 (AS2); FR-049 |
| VC-027 Metrics availability | US7 (AS2); FR-050 |
| VC-028 Grafana provisioning | US7 (AS2); FR-051 |
| VC-029 AI dashboard | US7 (AS2); FR-051 |
| VC-030 Observability privacy | US7 (AS3); FR-052 |
| VC-031 Canonical lifecycle | US7 (AS1, AS4); FR-053 |

---

## Success Criteria *(mandatory)*

- **SC-001**: A deterministic invocation through the local/stub adapter, driven end-to-end through
  `AiInvocationPolicy`, returns a valid `AiResponse` with populated timing/usage metadata and zero
  outbound network calls — verified by a business-level test.
- **SC-002**: Changing `ai.default-provider` / `ai.default-model` configuration requires **zero**
  change to `ai.domain`, `ai.business`, or any consumer — verified by a configuration test + `git
  diff` review.
- **SC-003**: `./mvnw -B clean verify` passes **offline** — unit tests for prompt
  composition/versioning, guardrails, token/context/cost budgets, structured-output validation,
  sensitive-data sanitization, error mapping; ArchUnit enforcing the provider-neutral core; ≥ 90 %
  line & branch coverage.
- **SC-004**: A request whose estimated tokens (or estimated cost) exceed the configured budget is
  rejected **before** the adapter is invoked — verified by a test asserting the adapter is never
  called.
- **SC-005**: A structured response that fails schema validation is rejected explicitly and never
  partially trusted — verified by a test.
- **SC-006**: An input guardrail rejects a prompt-injection-pattern input before invocation; an
  output guardrail rejects a malformed/non-conforming response after invocation — both verified by
  tests, without any live provider.
- **SC-007**: A deterministic AI invocation is observable end-to-end against the local Compose
  stack: its trace (with the full nested span chain) is visible in Jaeger, its metrics are queryable
  in Prometheus, and the Grafana AI dashboard renders them — without a live external AI provider.
- **SC-008**: `./start.sh` brings the four observability services up alongside the existing platform
  services; `./stop.sh` stops them; both remain idempotent (a second `./stop.sh` is a no-op, not an
  error).
- **SC-009**: A scan of logs, traces, metric labels, and the Grafana dashboard definition finds
  **zero** occurrences of a raw prompt body, raw completion body, or a provider API key.
- **SC-010**: `git diff` scope review confirms: no new independently deployable service; no business
  AI feature/endpoint; no RAG/vector-database/embeddings/agent-orchestration code; the only new
  backend dependency category is the OpenTelemetry SDK/exporter (and, if used, a JSON-schema
  validation library) — no LLM provider SDK is added, since EN006 ships no real provider adapter.
- **SC-011**: **100 %** of VC-001…VC-031 have associated executable or documented verification
  evidence.
- **SC-012**: `product/architecture/adrs/ADR-004-local-ai-observability-stack.md` exists, is
  referenced by `plan.md`, and documents the decision to add the four observability services to the
  local Compose lifecycle.

---

## Assumptions

> The enabler is **approved** (§47 signed 2026-09-04; §46 resolved the same day). Assumptions below
> fill only non-material, planning-level gaps within its constraints — each may be refined during
> `/speckit-plan` without requiring a further human decision, since none changes product behavior,
> architecture topology, or technology selection beyond what's already approved.

- **A1 — No live provider, no new HTTP/SDK dependency for AI itself**: because EN006 ships only the
  local/stub adapter (resolved Q1), it needs no HTTP client, no vendor SDK, and no API-key handling
  for an AI provider. The only genuinely new backend dependency category is the OpenTelemetry
  SDK/exporter (already `PREFERRED`) and, if structured-output schema validation needs one, a
  lightweight JSON-schema validator — both are planning-level library choices, not new technology
  categories requiring a fresh policy decision.
- **A2 — Single deployable** (ADR-001): the `ai` module is additive inside `core-service`. No new
  Flyway migration is anticipated (EN006 persists nothing) — a genuine new persisted business
  artifact would only arrive with a future AI feature.
- **A3 — Guardrail mechanics**: the exact rule-based checks (which patterns, which limits) are a
  planning/implementation detail within "rule-based only" (resolved Q2); they must remain unit-
  testable without a live provider.
- **A4 — Token-estimation implementation**: since no real tokenizer is needed without a real
  provider, a simple deterministic heuristic (e.g. character/word-count-based estimation) is
  sufficient for EN006; a provider-specific tokenizer adapter is deferred to whichever future
  provider adapter needs it (§22).
- **A5 — Prompt storage**: classpath resources under `src/main/resources/prompts/`, per enabler §31
  — exact file/versioning layout is a planning detail.
- **A6 — Pricing configuration**: since no real cost is incurred (no live provider), `estimatedCost`
  in the local/stub adapter is a deterministic placeholder value; a real pricing table arrives with
  a real provider adapter.
- **A7 — OpenTelemetry semantic-convention version and exact metric/span names**: a planning detail,
  constrained by FR-043–045 (`gen_ai.*` where applicable, `ai.*` application attributes, the
  specific Prometheus metric names in enabler §37).
- **A8 — Retry backoff policy, timeout defaults, cost-limit defaults**: planning-level numeric
  defaults, safe/reversible per constitution IV, recorded in `research.md`.
- **A9 — Observability image versions/ports** (OTel Collector, Jaeger, Prometheus, Grafana): a
  planning detail; consistent with EN001/EN002's approach of pinning versions in `research.md`.
- **A10 — Model-routing mechanism**: a single default provider/model configuration is sufficient
  while no real provider is wired (§46 item 16); per-task routing config (enabler §7) remains a
  documented future extension point, not implemented by EN006.
- **A11 — AI response caching**: not introduced by EN006 (§46 item 17, §46 "Whether AI response
  caching is introduced later" — deferred, out of scope).
- **A12 — This is the first OpenTelemetry integration in `core-service`**: no existing tracing/metrics
  configuration conflicts with it (verified — no `opentelemetry`/`micrometer-tracing`/`otlp`
  reference currently exists in `pom.xml` or `application.yml`).

## Dependencies

- **Enabler EN006** — the authoritative source; §47 signed 2026-09-04, §46 resolved 2026-09-04.
- **ADR-001** — single `core-service` deployable; unchanged, the `ai` module lives inside it.
- **ADR-003** — Standard Spring Backend Architecture; the `ai` module follows the same
  `domain`/`business`/`infrastructure` layout as `portfolio`/`financialinstrument`/`marketdata`.
- **A new ADR-004** (local AI observability stack) — to be drafted during `/speckit-plan` per the
  resolved Q3; required before the Docker Compose / `start.sh` / `stop.sh` changes are implemented.
- **EN001 / EN002** — the containerized platform + canonical `start.sh` / `stop.sh` / `e2e.sh`
  lifecycle that the observability stack must integrate into.
- **`product/architecture/technology-policy.md`** — AI/LLM Policy section (Bedrock/OpenAI/
  Anthropic/Vertex `ALLOWED` behind an abstraction — relevant to a *future* provider adapter, not
  EN006 itself) and Observability section (`OpenTelemetry` `PREFERRED`).
- **`product/governance/ai-development-policy.md`** — governs how *AI participates in developing*
  My-FinAI-Manager; distinct from, but consistent with, this enabler (which governs the *product's
  own* AI integration capability). Both share the hallucination-handling and provider-neutrality
  principles reflected in FR-015/FR-061 and the Edge Cases above.
- **No dependency on FD001–FD004 or EN004/EN005 business logic** — EN006 is a horizontal,
  standalone capability; a future AI feature will depend on it, not the reverse.

## Out of Scope

- Any specific AI business feature (Portfolio Analysis, Risk Explanation, Natural-Language Portfolio
  Summary, News Interpretation, Recommendation Explanation, Ask My Portfolio, Scenario Explanation,
  agentic workflows) — future enablers/features.
- A real external AI provider adapter (Anthropic/OpenAI/Bedrock/Vertex/local model) — deferred to
  the first AI feature that needs one (resolved Q1).
- RAG, vector databases, embeddings, agent orchestration, tool-calling as a business capability,
  MCP, human-in-the-loop workflows, autonomous trading, model fine-tuning/training, AI conversation
  memory.
- AI-based or provider-native guardrails (resolved Q2 — rule-based only for now; the extension point
  is what a later change would use).
- Automatic multi-provider fallback and per-task provider routing beyond a single default
  (§46 items 16–17).
- Any change to Portfolio valuation, FX conversion, position weights, or sector allocation (FD004 /
  EN005 own these exclusively).
- An opt-in live-provider smoke test (only relevant once a real adapter exists — §41).
- Production-grade AI cost/usage reporting UI (a future feature; EN006 only makes the metadata
  exist and be observable in the local Grafana dashboard).
