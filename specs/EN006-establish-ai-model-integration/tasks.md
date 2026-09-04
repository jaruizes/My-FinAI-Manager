---

description: "Task list for EN006 — Establish Provider-Neutral AI Model Integration"
---

# Tasks: EN006 — Establish Provider-Neutral AI Model Integration

**Input**: Design documents from `specs/EN006-establish-ai-model-integration/`
(`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: EN006 enabler Approved (§47, jaruiz 2026-09-04); ADR-004 Approved (jaruiz
2026-09-04). No live AI provider is implemented (resolved Q1) — every task below is offline/
deterministic.

**Tests**: Included — deterministic domain/business logic in `ai.business`/`ai.domain` follows
RED → GREEN → REFACTOR per constitution VII / `product/engineering/testing-strategy.md`.

**Organization**: Tasks are grouped by the 8 user stories in `spec.md` (US1–US7 = P1, US8 = P2),
after a Setup and a Foundational phase. All source paths are relative to
`implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/` (main) or
`.../src/test/java/com/myfinaimanager/core/` (test) unless stated otherwise.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps the task to US1…US8; Setup/Foundational/Polish carry no story label

---

## Phase 1: Setup

- [x] T001 `pom.xml` — add `io.micrometer:micrometer-tracing-bridge-otel` and
  `io.opentelemetry:opentelemetry-exporter-otlp` (research D1). No LLM provider SDK, no JSON-schema
  library (resolved Q1; research D3).
- [x] T002 [P] Create the empty `ai` module package skeleton with `package-info.java` in each of:
  `ai/domain/model`, `ai/domain/ports`, `ai/domain/exceptions`, `ai/business`,
  `ai/infrastructure/provider/local`, `ai/infrastructure/prompt`, `ai/infrastructure/guardrails`,
  `ai/infrastructure/tokencount`, `ai/infrastructure/validation`, `ai/infrastructure/observability`,
  `ai/infrastructure/api`, `ai/infrastructure/config` — matching plan.md's Project Structure.
- [x] T003 [P] `src/main/resources/prompts/global-system-v1.txt` — the governed global system prompt
  text (FR-011, FR-015): never invent financial facts; use only supplied facts; never replace
  deterministic calculations; state uncertainty explicitly; never fabricate missing prices/sectors/
  currencies/Portfolio data; never claim an unexecuted action was executed; decision support, not
  autonomous execution.

**Checkpoint**: module skeleton compiles (`./mvnw -q -o compile`); no behavior yet.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the shared domain vocabulary every user story's tests and wiring depend on.

**⚠️ CRITICAL**: no user story task may start before this phase is green.

- [x] T004 [P] `ai/domain/model/{AiRequest,AiResponse,AiUsage,PromptReference,OutputSchema,GuardrailOutcome,InvocationIdentity,TokenUsageEstimate}.java` — immutable records per data-model.md §1. Unit test `AiDomainModelTest` — construction invariants (`maxOutputTokens > 0`, `estimatedCost ≥ 0`, `FxRate`-style non-null checks).
- [x] T005 [P] `ai/domain/ports/{AiModelPort,TokenCounterPort,InputGuardrailPort,OutputGuardrailPort}.java` — interfaces per data-model.md §2.
- [x] T006 [P] `ai/business/GenerateAiUseCase.java` — the one-method inbound port (data-model.md §2.1).
- [x] T007 [P] `ai/domain/exceptions/{AiException(base),AiProviderUnavailableException,AiProviderRateLimitedException,AiProviderAuthenticationFailedException,AiRequestTooLargeException,AiTokenBudgetExceededException,AiCostBudgetExceededException,AiTimeoutException,AiInvalidResponseException,AiGuardrailRejectedException,AiStructuredOutputInvalidException,AiConfigurationErrorException}.java` — the eleven provider-neutral failures + base type (data-model.md §3).
- [x] T008 `ai/infrastructure/config/AiProperties.java` — `@ConfigurationProperties("ai")` record (`default-provider`, `default-model`, `limits.{max-input-tokens,max-output-tokens,max-total-tokens,max-input-characters,max-estimated-cost}`, `timeout.{connect,read}`, `retry.{max-attempts,backoff}`) per data-model.md §4. `application.yml` gains the `ai:` block with the defaults from data-model.md §4. Test `AiPropertiesTest` (binds from a test `application.yml` fragment, asserts defaults).
- [x] T009 `ai/business/AiInvocationPolicy.java` (skeleton) — `implements GenerateAiUseCase`; constructor-injects `AiModelPort` + `AiProperties` only (the other four dependencies — `TokenCounterPort`, `InputGuardrailPort`, `OutputGuardrailPort`, `PromptService`, `ContextBudgetService` — are added incrementally in US2/US4/US5); `generate(AiRequest)` currently just calls `AiModelPort.generate(request)` and returns its result. This is intentionally incomplete — later stories extend it in place.

**Checkpoint**: `./mvnw -q -o test -Dtest=AiDomainModelTest,AiPropertiesTest` green; module compiles
with no adapter yet (US1 provides the first one).

---

## Phase 3: User Story 1 — One provider-neutral `AiModelPort`, proven by a deterministic local adapter (Priority: P1) 🎯 MVP

**Goal**: `AiModelPort` has exactly one, deterministic, network-free implementation; swapping it
requires no core change.

**Independent Test**: `AiInvocationPolicyTest` drives the skeleton policy against
`LocalAiModelAdapter` and gets a populated `AiResponse` with zero network calls.

### Tests for User Story 1 ⚠️

- [x] T010 [P] [US1] `LocalAiModelAdapterTest` (RED) — deterministic output for a fixed `AiRequest`
  (contract `local-ai-adapter.md` determinism contract); structured-output happy path when
  `outputSchema` is present; asserts no field on the class is an HTTP client/credential holder.
- [x] T011 [P] [US1] `StandardArchitectureRulesTest` — add (RED) `ai_domain_and_business_are_free_of_provider_types` and `ai_domain_and_business_use_no_provider_selection_annotations` (research D8); both non-vacuous.

### Implementation for User Story 1

- [x] T012 [US1] `ai/infrastructure/provider/local/LocalAiModelAdapter.java implements AiModelPort` — deterministic content/usage derivation per contract `local-ai-adapter.md` (research D2); `@Component`, `@ConditionalOnProperty(name = "ai.default-provider", havingValue = "local", matchIfMissing = true)`.
- [x] T013 [P] [US1] `ai/infrastructure/provider/local/FailingLocalAiModelAdapter.java` (**test sources**) — configurable-failure test double per contract `local-ai-adapter.md` (used starting in US6).
- [x] T014 [US1] `AiInvocationPolicyTest` (RED→GREEN) — happy-path invocation through the skeleton policy + `LocalAiModelAdapter`; asserts a populated `AiResponse`, an `invocationId` is present, zero network calls (no `RestClient`/`HttpClient` bean touched).
- [x] T015 [US1] Run T010, T011 GREEN; `./mvnw -q -o test -Dtest=LocalAiModelAdapterTest,StandardArchitectureRulesTest,AiInvocationPolicyTest`.

**Checkpoint**: `AiModelPort` proven implementable and provider-neutral (VC-001, VC-002, VC-003,
VC-023). MVP demonstrable.

---

## Phase 4: User Story 2 — Governed, versioned, layered prompts (Priority: P1)

**Goal**: every request's system prompt is composed centrally, layered, versioned, and traceable.

**Independent Test**: `PromptServiceTest` composes a known task and asserts the layered result +
`promptId`/`promptVersion`; an unknown task fails explicitly.

### Tests for User Story 2 ⚠️

- [x] T016 [P] [US2] `PromptServiceTest` (RED) — composing `taskType="diagnostic"` (and a second
  fixture task) layers Global System Prompt + Task Instructions + Business Context + User/Task
  Prompt in that order; an unknown `taskType` or `promptVersion` raises
  `AiConfigurationErrorException`; the global system prompt content contains the FR-015 principles
  (substring assertions against T003's file).
- [x] T017 [P] [US2] `ClasspathPromptRepositoryTest` (RED) — loads `global-system-v1.txt` by
  `promptId="global-system"`/`promptVersion="v1"`; a missing id/version raises
  `AiConfigurationErrorException`.

### Implementation for User Story 2

- [x] T018 [US2] `ai/infrastructure/prompt/ClasspathPromptRepository.java` — loads
  `src/main/resources/prompts/**` into `PromptReference`s keyed by id+version.
- [x] T019 [US2] `ai/business/PromptService.java` — `compose(String taskType, String businessContext,
  String userPrompt) → AiRequest` (layers Global System Prompt + Task Instructions + Business
  Context + User Prompt into one composed `AiRequest`, resolving `PromptReference` via
  `ClasspathPromptRepository`); centralizes composition so no controller/adapter builds a prompt
  string itself (FR-012).
- [x] T020 [US2] `AiRequest.diagnostic()` static factory (in `ai/domain/model/AiRequest.java`) —
  fixed diagnostic task/prompt/empty context, used later by T041 (US7's diagnostic endpoint).
- [x] T021 [US2] Extend `AiInvocationPolicy` to accept `PromptService` as a constructor dependency
  and to record the resolved `promptId`/`promptVersion` on the invocation (available for telemetry
  in US7). Update `AiInvocationPolicyTest` for the new constructor shape.
- [x] T022 [US2] Run T016, T017 GREEN.

**Checkpoint**: US1 + US2 both independently green (VC-004, VC-005).

---

## Phase 5: User Story 3 — Structured output, validated before it is trusted (Priority: P1)

**Goal**: a requested typed/JSON response is validated against its schema; non-conformance is
rejected explicitly, never partially trusted.

**Independent Test**: `StructuredOutputValidatorTest` — conforming/non-conforming fixtures.

### Tests for User Story 3 ⚠️

- [x] T023 [P] [US3] `StructuredOutputValidatorTest` (RED) — a conforming `JsonNode` against an
  `OutputSchema` passes; a missing required field, a wrong-typed field, and non-object JSON each
  fail with a `Rejected` outcome (research D3).
- [x] T024 [P] [US3] `AiInvocationPolicyTest` — add (RED) a case: `AiRequest.outputSchema()` present
  + a conforming `LocalAiModelAdapter` response ⇒ `AiResponse.structuredContent()` populated; a case
  with a hand-built non-conforming `AiResponse` (via a test double) ⇒
  `AiStructuredOutputInvalidException`, and no partial `structuredContent` is ever returned.

### Implementation for User Story 3

- [x] T025 [US3] `ai/infrastructure/validation/StructuredOutputValidator.java` — `GuardrailOutcome
  validate(OutputSchema, JsonNode)` per research D3 (Jackson `JsonNode` only, no new dependency).
- [x] T026 [US3] Extend `AiInvocationPolicy`: when `request.outputSchema()` is present, after the
  adapter call, validate the parsed structured content and either populate
  `AiResponse.structuredContent()` or throw `AiStructuredOutputInvalidException`.
- [x] T027 [US3] Run T023, T024 GREEN.

**Checkpoint**: US1–US3 green (VC-006).

---

## Phase 6: User Story 4 — Rule-based input/output guardrails (Priority: P1)

**Goal**: unsafe/out-of-policy input never reaches the adapter; a malformed/policy-violating
response never reaches the caller — both rule-based (resolved Q2).

**Independent Test**: guardrail unit tests reject their target patterns without invoking the
adapter (input) or before returning to the caller (output).

### Tests for User Story 4 ⚠️

- [x] T028 [P] [US4] `RuleBasedInputGuardrailTest` (RED) — oversized input rejected; each
  injection-pattern phrase (research D5) rejected; a clean input allowed.
- [x] T029 [P] [US4] `RuleBasedOutputGuardrailTest` (RED) — each prohibited-action phrase (research
  D5) rejected; a schema-conformance delegation case (calls T025); a clean response allowed.
- [x] T030 [US4] `AiInvocationPolicyTest` — add (RED) cases: an injection-pattern `AiRequest` is
  rejected with `AiGuardrailRejectedException` **and the adapter is never invoked** (spy/mock
  assertion, contract `ai-model-port.md` Q2); a response tripping the output guardrail is rejected
  with `AiGuardrailRejectedException` and never returned.

### Implementation for User Story 4

- [x] T031 [P] [US4] `ai/infrastructure/guardrails/RuleBasedInputGuardrail.java implements
  InputGuardrailPort` — size + injection-pattern rules (research D5).
- [x] T032 [P] [US4] `ai/infrastructure/guardrails/RuleBasedOutputGuardrail.java implements
  OutputGuardrailPort` — prohibited-action rule + delegates schema conformance to T025 (research
  D5).
- [x] T033 [US4] Extend `AiInvocationPolicy` to accept `InputGuardrailPort` + `OutputGuardrailPort`
  as constructor dependencies and to call the input guardrail **before** the adapter and the output
  guardrail **after** it, translating any `Rejected` outcome to `AiGuardrailRejectedException`
  (never retried — wired fully in US6).
- [x] T034 [US4] Run T028, T029, T030 GREEN.

**Checkpoint**: US1–US4 green (VC-007, VC-008).

---

## Phase 7: User Story 5 — Token, context, and cost budgets are enforced (Priority: P1)

**Goal**: every request is checked against configurable token/cost budgets **before** the model is
called; context is explicitly built, not blindly serialized.

**Independent Test**: a request estimated over budget is rejected without the adapter ever being
invoked; a context-budget test asserts irrelevant/sensitive fields are excluded.

### Tests for User Story 5 ⚠️

- [x] T035 [P] [US5] `HeuristicTokenCounterTest` (RED) — character-count-based estimate formula
  (research D4) for a range of input sizes.
- [x] T036 [P] [US5] `ContextBudgetServiceTest` (RED) — building context from a sample domain-like
  object graph excludes an irrelevant/sensitive field and stays within a configured size limit
  (FR-029).
- [x] T037 [US5] `AiInvocationPolicyTest` — add (RED) cases: an over-token-budget request ⇒
  `AiTokenBudgetExceededException`, adapter never invoked; an over-cost-budget request (via a
  configured low `ai.limits.max-estimated-cost`) ⇒ `AiCostBudgetExceededException`, adapter never
  invoked; a successful invocation's `AiResponse.usage()` carries token counts consistent with the
  estimate.

### Implementation for User Story 5

- [x] T038 [P] [US5] `ai/infrastructure/tokencount/HeuristicTokenCounter.java implements
  TokenCounterPort` — `ceil(characterCount / 4)` estimate (research D4).
- [x] T039 [US5] `ai/business/ContextBudgetService.java` — assembles a compact, provider-neutral
  context from supplied business data (a `Map<String,String>`-shaped input for now, since no
  business feature exists yet to supply a richer domain object), excluding metadata/sensitive keys
  and truncating at `ai.limits.max-input-characters` without changing meaning mid-token (FR-029).
- [x] T040 [US5] Extend `AiInvocationPolicy` to accept `TokenCounterPort` + `ContextBudgetService`
  as constructor dependencies; before invoking the adapter, build the context, estimate tokens, and
  enforce both budgets (token first, then cost), each producing its dedicated exception with **no**
  adapter call.
- [x] T041 [US5] Run T035, T036, T037 GREEN.

**Checkpoint**: US1–US5 green (VC-010…VC-015). `AiInvocationPolicy`'s full non-observability
sequencing (data-model.md §5, minus telemetry) is now complete and fully wired.

---

## Phase 8: User Story 6 — Provider-neutral errors, bounded timeout, bounded retry (Priority: P1)

**Goal**: every observable failure is provider-neutral; every call is time-bounded; only transient
failures retry, bounded.

**Independent Test**: `AiInvocationPolicyTest` error-mapping matrix using `FailingLocalAiModelAdapter`
(T013); retry-count assertions.

### Tests for User Story 6 ⚠️

- [x] T042 [US6] `AiInvocationPolicyTest` — add (RED), using `FailingLocalAiModelAdapter` (T013) with
  each of the eleven failure modes: transient failures (`AiProviderUnavailableException`,
  `AiProviderRateLimitedException`) are retried up to `ai.retry.max-attempts` then surfaced; every
  other failure (`AiProviderAuthenticationFailedException`, `AiRequestTooLargeException`,
  `AiGuardrailRejectedException`, `AiStructuredOutputInvalidException`,
  `AiTokenBudgetExceededException`, `AiCostBudgetExceededException`) is surfaced on the **first**
  attempt, never retried; a call exceeding `ai.timeout.read` maps to `AiTimeoutException`.

### Implementation for User Story 6

- [x] T043 [US6] Extend `AiInvocationPolicy` with: a bounded timeout around the
  `AiModelPort.generate(...)` call (`ai.timeout.read`), a bounded-retry wrapper that retries only
  `AiProviderUnavailableException`/`AiProviderRateLimitedException` up to `ai.retry.max-attempts`
  with `ai.retry.backoff` between attempts, and explicit exclusion of every non-transient
  exception type from that retry wrapper (contract `ai-model-port.md` C2 Q5/Q6).
- [x] T044 [US6] Run T042 GREEN.

**Checkpoint**: US1–US6 green (VC-018, VC-019). `AiInvocationPolicy` fully implements contract
`ai-model-port.md`.

---

## Phase 9: User Story 7 — Observable without exposing sensitive content, end to end (Priority: P1)

**Goal**: every invocation is traced/metriced per contract `ai-telemetry.md`; the local Compose
observability stack (ADR-004, approved) makes it inspectable; `start.sh`/`stop.sh` manage it.

**Independent Test**: a unit test asserts the emitted span/metric attributes contain no sensitive
content; `quickstart.md` §E–F is the manual/scripted proof against the running stack.

### Tests for User Story 7 ⚠️

- [x] T045 [P] [US7] `AiTelemetryRecorderTest` (RED) — recording a successful and a failed/rejected
  invocation produces the exact attribute set in contract `ai-telemetry.md` (`gen_ai.*` + `ai.*`);
  asserts by construction that no method/field can carry `userPrompt`/`context`/`systemPrompt.body`/
  `content`/`structuredContent`/credential values (a compile-time/reflection check that the recorder
  class has no such field, plus a runtime assertion over a captured span/meter recording).
- [x] T046 [P] [US7] `AiDiagnosticEndpointTest` (`@WebMvcTest` slice, RED) — `POST
  /actuator/ai-diagnostic` returns `200` with exactly `invocationId`/`latencyMs`/`totalTokens`;
  asserts the JSON body contains no prompt/response text.

### Implementation for User Story 7

- [x] T047 [US7] `ai/infrastructure/observability/AiTelemetryRecorder.java` — wraps
  `ObservationRegistry` + `MeterRegistry` per research D1/contract `ai-telemetry.md`: the
  `ai.usecase` → `ai.invocation` span pair, the `gen_ai.*`/`ai.*` attributes, and the seven
  `ai_*` meters (enabler §37 names).
- [x] T048 [US7] Extend `AiInvocationPolicy` to accept `AiTelemetryRecorder` and record telemetry
  around every invocation (success, guardrail rejection, budget rejection, provider error, timeout)
  — the recorder call itself must never throw or block the business result.
- [x] T049 [US7] `application.yml` — add `management.tracing.sampling.probability`,
  `management.otlp.tracing.endpoint`, `management.otlp.metrics.export.url`,
  `management.endpoints.web.exposure.include` (+`ai-diagnostic`),
  `management.endpoint.ai-diagnostic.enabled` per research D1/D6.
- [x] T050 [US7] `ai/infrastructure/api/AiDiagnosticEndpoint.java` — `@Endpoint(id="aiDiagnostic")`,
  `@WriteOperation` `run()` invoking `GenerateAiUseCase.generate(AiRequest.diagnostic())` (T020) and
  returning the 3-field summary, per contract `ai-diagnostic-endpoint.md`.
- [x] T051 [US7] Run T045, T046 GREEN.
- [x] T052 [P] `implementation/platform/infrastructure/observability/otel-collector-config.yaml` —
  OTLP receiver (4318), `batch` processor, `otlp` exporter → Jaeger, `prometheus` exporter → `:8889`;
  no payload-dumping exporter (research D7).
- [x] T053 [P] `implementation/platform/infrastructure/observability/prometheus.yml` — scrape config
  targeting `otel-collector:8889`.
- [x] T054 [P] `implementation/platform/infrastructure/observability/grafana/provisioning/datasources/prometheus.yaml`
  — auto-provisioned Prometheus data source (`http://prometheus:9090`).
- [x] T055 [US7] `implementation/platform/infrastructure/observability/grafana/provisioning/dashboards/ai.yaml`
  + `.../grafana/dashboards/ai-observability.json` — the AI dashboard (request volume, latency, token
  usage, estimated cost tolerating all-zero, errors, guardrail activity — FR-051; contract
  `ai-telemetry.md` "Grafana dashboard").
- [x] T056 `implementation/platform/infrastructure/local/compose.yaml` — add `otel-collector`,
  `jaeger`, `prometheus`, `grafana` services per research D7 (ADR-004, approved); mount the
  observability config/provisioning files read-only; `core-service` gains
  `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318` and `OTEL_SERVICE_NAME` env vars.
- [x] T057 `implementation/platform/start.sh` — extend the health-wait loop to also wait for
  `otel-collector`, `jaeger`, `prometheus`, `grafana`; print their URLs in the startup summary
  alongside the existing ones.
- [x] T058 `implementation/platform/stop.sh` — verify the existing `docker compose down` already
  covers the four new services (no separate teardown needed); add a comment noting they're included.
- [x] T059 `docker compose config` dry-run over the updated `compose.yaml` — validates syntax before
  a real `./start.sh`.

**Checkpoint**: US1–US7 green; `./start.sh` brings up all containers healthy including the four
observability services (VC-016, VC-017, VC-024…VC-031).

---

## Phase 10: User Story 8 — Deterministic tests; CI never depends on a live AI provider (Priority: P2)

**Goal**: the full offline gate is green; no regression to FD001–FD004/EN004/EN005.

**Independent Test**: `./mvnw -B clean verify` offline; existing suites unaffected.

- [x] T060 [US8] `./mvnw -B clean verify` — full offline run: Surefire (all `Ai*Test` + existing
  suites), JaCoCo bundle check ≥ 90 % line & branch, `StandardArchitectureRulesTest` (T011's two new
  rules + all existing ones) green.
- [x] T061 [US8] Regression check: FD001/FD002/FD003/FD004/EN004/EN005 backend suites and `ng test`
  are unaffected (`git diff --stat` shows no file under `portfolio/`, `financialinstrument/`,
  `marketdata/`, or `frontend/` touched by this work).
- [x] T062 [US8] Grep checks (quickstart.md §A): no provider-SDK import under `ai/`; no committed AI
  key; `openapi.yaml` untouched.

**Checkpoint**: full offline gate green (VC-020).

---

## Phase 11: Polish & cross-cutting concerns

- [x] T063 [P] `implementation/platform/README.md` — new capability row/paragraph for EN006: a
  provider-neutral `ai` module exists with one deterministic local/stub adapter (no live AI
  provider), governed/versioned prompts, rule-based guardrails, token/cost budgets, and full
  OpenTelemetry observability (Collector/Jaeger/Prometheus/Grafana) wired into `start.sh`/`stop.sh`;
  no business AI feature yet.
- [x] T064 [P] `implementation/platform/backend/core-service/README.md` — the `ai` module paragraph:
  package layout, `AiModelPort`/`AiInvocationPolicy`, the one local adapter, and the diagnostic
  endpoint (not a business API).
- [x] T065 `specs/EN006-establish-ai-model-integration/pr-evidence.md` — gate results
  (`./mvnw verify`, coverage numbers, ArchUnit count), VC-001…VC-031 evidence table cross-referenced
  against spec.md's Traceability table, and the scope review (SC-010: no new deployable, no business
  AI feature, no RAG/vector-db/agents, only the OTel exporter as a new dependency).
- [x] T066 Run `quickstart.md` §A–D (module-only) and §E–G (full observability stack, now unblocked
  by ADR-004's approval) end to end against a real `BUILD=1 ./start.sh`; capture the Jaeger trace
  screenshot / Prometheus query result / Grafana panel evidence referenced by T065.
- [x] T067 Scope review (SC-010): `git diff` shows **no** Flyway migration, **no** `openapi.yaml`
  change, **no** LLM provider SDK dependency, **no** new independently deployable service; the
  `product/` changes are exactly EN006's own governance edit (§46/§47) and the new
  `ADR-004-local-ai-observability-stack.md` — both already human-approved, not silently made.
- [ ] T068 `/project-verify EN006-establish-ai-model-integration` (user-triggered closure gate).

**Checkpoint**: all gates green; ready for human closure.

---

## Dependencies & execution order

- **Setup (T001–T003)** — no dependencies.
- **Foundational (T004–T009)** — depends on Setup; **blocks every user story**.
- **US1 (T010–T015)** — depends on Foundational only. 🎯 MVP.
- **US2 (T016–T022)** — depends on Foundational; extends the policy US1 built.
- **US3 (T023–T027)** — depends on US1 (adapter) for its `AiInvocationPolicy` test fixture; otherwise
  independent of US2.
- **US4 (T028–T034)** — depends on US1; independent of US2/US3's internals (calls the same policy
  object, extended in place).
- **US5 (T035–T041)** — depends on US1; independent of US2/US3/US4 internals.
- **US6 (T042–T044)** — depends on US1 + T013 (`FailingLocalAiModelAdapter`); benefits from US4/US5
  being wired first so the "never retry a non-transient failure" matrix has all exception types to
  exercise, but could be reordered if needed.
- **US7 (T045–T059)** — depends on US1–US6 (`AiInvocationPolicy` must be feature-complete before its
  telemetry wrapping is meaningful) **and** on ADR-004 (approved) for T052–T059.
- **US8 (T060–T062)** — depends on US1–US7 all being complete.
- **Polish (T063–T068)** — depends on US8.

Within `AiInvocationPolicy`, each user story phase **extends the same class in place** (T009's
skeleton) rather than creating parallel implementations — this is intentional: the class's final
shape is exactly contract `ai-model-port.md`'s C2, built up incrementally with a passing test suite
at every checkpoint.

## Parallel opportunities

- T004, T005, T006, T007 (Foundational domain types) — different files, fully parallel.
- Within each user story's "Tests" sub-phase, `[P]`-marked tests are independent files and may run in
  parallel; the RED tests must exist and fail before their matching implementation task.
- T052, T053, T054 (observability config files) — independent files, parallel; T055 depends on none
  of them but is sequenced after for clarity.
- T063, T064 (READMEs) — independent files, parallel.

## Implementation strategy

**MVP = Phases 1–3** (Setup + Foundational + US1): proves `AiModelPort` is a real, provider-neutral,
testable seam with one deterministic implementation — the architectural core of the enabler.

**Incremental delivery**: US2 → US3 → US4 → US5 → US6 each add one more concern to the same
`AiInvocationPolicy`, independently testable and always leaving the suite green. US7 (observability)
is deliberately last among the P1 stories because it wraps a *complete* policy rather than a partial
one. US8 is the final regression/offline-CI proof. Polish closes with evidence, docs, and the
verification gate.
