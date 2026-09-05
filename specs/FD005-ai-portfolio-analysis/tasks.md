---

description: "Task list for FD005 — AI Portfolio Analysis"
---

# Tasks: FD005 — AI Portfolio Analysis

**Input**: Design documents from `specs/FD005-ai-portfolio-analysis/`
(`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`)

**Prerequisites**: FD005 Approved (§48, jaruiz 2026-09-05); §47 Q1–Q3 resolved. FD001/FD003/FD004/
EN004/EN005/EN006 implemented and green.

**Tests**: Included — deterministic domain/business logic follows RED → GREEN → REFACTOR per
constitution VII. OpenAI is a true external provider (constitution VII carve-out) — tested with
`MockRestServiceServer`, never live, in CI.

**Organization**: Tasks are grouped by the 7 user stories in `spec.md` (US1–US6 = P1, US7 = P2),
after Setup and two Foundational checkpoints (EN006 extension, then `portfolioanalysis` domain
core). All backend paths relative to
`implementation/platform/backend/core-service/src/{main,test}/java/com/myfinaimanager/core/`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [x] T001 [P] Create the `portfolioanalysis` module package skeleton with `package-info.java` in:
  `portfolioanalysis/domain/{model,ports,exceptions}`, `portfolioanalysis/business`,
  `portfolioanalysis/infrastructure/{persistence/{entity,repository,mapper},portfolio,ai,api/rest/{dto,mapper},config}`.
- [x] T002 [P] Create `ai/infrastructure/provider/openai/{client,dto,mapper,config}` package
  skeleton with `package-info.java`.
- [x] T003 [P] `src/main/resources/db/migration/V5__portfolio_analysis.sql` — the three tables +
  the two indexes per data-model.md §1 (incl. the partial unique index for FR-013).
- [x] T004 [P] `src/main/resources/prompts/tasks/portfolio-analysis-v1.txt` — the task instructions
  per research.md D6 (use only supplied facts; never fabricate; never calculate deterministically;
  identify diversification/concentration; 2–5 insights; 1–4 risks; state uncertainty; JSON-only
  output in the exact required shape).

**Checkpoint**: skeleton + migration compile/apply; no behavior yet.

---

## Phase 2: Foundational A — EN006 extension (per-task provider routing + prompt versioning)

**⚠️ CRITICAL**: blocks every later phase — `portfolioanalysis` cannot reach a real provider without
this.

- [x] T005 `ai/domain/exceptions/AiProviderNotConfiguredException.java` (research D3).
- [x] T006 `ai/domain/ports/PromptRepositoryPort.java` — change `findTaskInstructions` to return
  `Optional<PromptReference>` (research D2).
- [x] T007 `ai/business/PromptService.java` — `compose(taskType)` uses the task's own
  `promptId`/`promptVersion` when task instructions exist, else the global prompt's (research D2).
- [x] T008 `ai/infrastructure/prompt/ClasspathPromptRepository.java` — widen `KNOWN_TASKS` to
  include `"portfolio-analysis"`; load `prompts/tasks/portfolio-analysis-v1.txt` into a
  `PromptReference("portfolio-analysis", "v1", ...)` in `TASK_INSTRUCTIONS` (now
  `Map<String, PromptReference>`).
- [x] T009 `ai/infrastructure/config/AiProperties.java` — add `Map<String, TaskOverride> tasks`
  (`TaskOverride(String provider)`).
- [x] T010 `ai/domain/model/AiInvocationSettings.java` — add `Map<String, String> taskProviders`.
- [x] T011 `ai/infrastructure/config/AiModuleConfiguration.java` — copy `properties.tasks()` into
  `taskProviders` when building the `AiInvocationSettings` bean.
- [x] T012 `ai/infrastructure/provider/local/LocalAiModelAdapter.java` — `@Component("local")`,
  remove `@ConditionalOnProperty` (research D1).
- [x] T013 `ai/business/AiInvocationPolicy.java` — constructor takes
  `Map<String, AiModelPort> modelPortsByProvider` instead of a single `AiModelPort`; resolve
  `providerId = settings.taskProviders().getOrDefault(taskType, settings.defaultProvider())` at the
  top of `generate(...)`; missing provider id → `AiConfigurationErrorException`.
- [x] T014 [P] `src/main/resources/application.yml` — `ai.tasks.portfolio-analysis.provider:
  openai` (no entry for `diagnostic` — research D1).
- [x] T015 Update EN006 tests for the new shapes: `AiInvocationPolicyTest` (constructor + a new
  per-task-routing test case), `PromptServiceTest` (`Optional<PromptReference>`),
  `ClasspathPromptRepositoryTest`, `AiModuleConfigurationTest` — every existing assertion keeps
  passing (the `diagnostic` task's behavior is provably unchanged).
- [x] T016 Run `./mvnw -q -o test -Dtest='com.myfinaimanager.core.ai.**'` GREEN — EN006 regression
  gate before proceeding.

**Checkpoint A**: EN006 extended; `diagnostic` behavior regression-proven unchanged.

---

## Phase 2: Foundational B — the real OpenAI adapter

- [x] T017 [P] `ai/infrastructure/provider/openai/config/OpenAiProperties.java` —
  `@ConfigurationProperties("openai")`: `apiKey`, `baseUrl`, `model`, timeouts, `pricing.{inputPer1k,outputPer1k}`.
- [x] T018 [P] `ai/infrastructure/provider/openai/dto/{OpenAiChatRequest,OpenAiChatResponse}.java`
  (Jackson records) per contract `openai-provider-contract.md`.
- [x] T019 `ai/infrastructure/provider/openai/client/OpenAiRestClient.java` — own `RestClient`,
  `Authorization: Bearer` header, `POST /chat/completions`, structured `event=ProviderCall
  provider=openai capability=chat-completion` logging (no key/body).
- [x] T020 `ai/infrastructure/provider/openai/mapper/OpenAiChatMapper.java` — `AiRequest →
  OpenAiChatRequest`; `OpenAiChatResponse → AiResponse` (incl. structured-content JSON parse when
  `outputSchema` present; usage/cost mapping).
- [x] T021 [US6] `ai/infrastructure/provider/openai/OpenAiModelAdapter.java implements AiModelPort`
  — `@Component("openai")`; blank key → `AiProviderNotConfiguredException` no-call; full error
  translation table per the contract.
- [x] T022 [P] [US6] `OpenAiModelAdapterTest` + `OpenAiChatMapperTest` (RED first) —
  `MockRestServiceServer`: request shape, response mapping, every HTTP error status, blank-key path.
  Run GREEN.
- [x] T023 `StandardArchitectureRulesTest` — widen `only_provider_client_packages_use_restclient` to
  include `..infrastructure..openai.client..`.
- [x] T024 [P] `application.yml` — `openai.*` block (`api-key: ${OPENAI_API_KEY:}`, `base-url:
  ${OPENAI_BASE_URL:https://api.openai.com/v1}`, `model: ${OPENAI_MODEL:gpt-4o-mini}`, timeouts,
  placeholder pricing).

**Checkpoint B**: a real, tested `OpenAiModelAdapter` exists and is reachable via
`ai.tasks.portfolio-analysis.provider=openai`, fully offline in tests.

---

## Phase 2: Foundational C — `portfolioanalysis` domain core

- [x] T025 [P] `portfolioanalysis/domain/model/{AnalysisStatus,CreationTrigger,
  DiversificationLevel,RiskSeverity,RiskType,FailureReason}.java` — the six enums (data-model.md §2).
- [x] T026 [P] `portfolioanalysis/domain/model/PortfolioAnalysis.java` (+ nested `Insight`/`Risk`) —
  wither methods `withRunning`, `withCompleted`, `withFailed` (research D7). Unit test
  `PortfolioAnalysisTest` — every field/guard, each transition.
- [x] T027 [P] `portfolioanalysis/domain/model/{PortfolioContextSnapshot,PortfolioAnalysisContext,
  PortfolioAnalysisResult}.java` (+ nested `PositionSnapshot`/`SectorSnapshot`).
- [x] T028 [P] `portfolioanalysis/domain/ports/{PortfolioAnalysisRepository,PortfolioContextGateway,
  PortfolioAnalysisAiPort}.java`.
- [x] T029 [P] `portfolioanalysis/domain/exceptions/{AnalysisAlreadyInProgressException,
  AnalysisFailedException}.java`.
- [x] T030 [US1] `portfolioanalysis/domain/model/PortfolioAnalysisContextBuilder.java` (RED-first,
  pure calculator, no ports) — `PortfolioAnalysisContextBuilderTest`: full-COMPLETED, PARTIAL
  (explicit partial wording), FAILED/absent/no-valued-position → `sufficient=false`, deterministic
  text rendering.

**Checkpoint C**: `./mvnw -q -o compile` green; `portfolioanalysis` domain fully modeled, zero ports
implemented yet.

---

## Phase 3: User Story 1 — Automatic, non-blocking analysis after Portfolio creation (Priority: P1) 🎯 MVP

**Goal**: creating a Portfolio always results in a `PENDING`→ eventually terminal
`PortfolioAnalysis`, without the create request waiting for it.

**Independent Test**: `PortfolioAnalysisOnCreationIT` — create a Portfolio, assert the create
response is unaffected in timing/shape, and a `PortfolioAnalysis` row for it exists immediately.

### Tests for User Story 1 ⚠️

- [x] T031 [P] [US1] `PortfolioAnalysisRequestServiceTest` (RED) — `requestAutomatic`/`requestManual`
  create a `PENDING` row and trigger the worker exactly once; `requestManual` rejects
  (`AnalysisAlreadyInProgressException`) when the latest is `PENDING`/`RUNNING`.
- [x] T032 [P] [US1] `PortfolioContextGatewayAdapterTest` (RED, fakes
  `PortfolioQueryUseCase`/`PortfolioValuationQueryUseCase`) — maps COMPLETED/PARTIAL/FAILED/absent
  valuation correctly into `PortfolioContextSnapshot`; the sole class touching `portfolio.*`.
- [x] T033 [P] [US1] `PortfolioAnalysisAiAdapterTest` (RED, fakes `GenerateAiUseCase`) — builds the
  `AiRequest` correctly (`taskType="portfolio-analysis"`); maps a conforming response to
  `PortfolioAnalysisResult`; maps every EN006 `AiException` subtype to its `FailureReason`.
- [x] T034 [P] [US1] `PortfolioAnalysisWorkerTest` (RED) — happy path `RUNNING→COMPLETED`; every
  `AnalysisFailedException` reason → `FAILED`; an unexpected `RuntimeException` mid-flow → `FAILED`
  (never stuck at `RUNNING`, FR-057); an insufficient-data snapshot → `FAILED(INSUFFICIENT_DATA)`
  with **zero** calls to `PortfolioAnalysisAiPort`.

### Implementation for User Story 1

- [x] T035 [US1] `portfolioanalysis/infrastructure/persistence/entity/{PortfolioAnalysisEntity,
  PortfolioAnalysisInsightEntity,PortfolioAnalysisRiskEntity}.java` — JPA mapping onto `V5`'s tables.
- [x] T036 [US1] `portfolioanalysis/infrastructure/persistence/{repository/PortfolioAnalysisJpaRepository,
  mapper/PortfolioAnalysisPersistenceMapper,PortfolioAnalysisPersistenceAdapter}.java` — implements
  `PortfolioAnalysisRepository`; translates the unique-index violation into
  `AnalysisAlreadyInProgressException`.
- [x] T037 [US1] `portfolioanalysis/infrastructure/portfolio/PortfolioContextGatewayAdapter.java`
  implements `PortfolioContextGateway` — depends only on
  `portfolio.business.{PortfolioQueryUseCase,PortfolioValuationQueryUseCase}` (contract C1).
- [x] T038 [US1] `portfolioanalysis/infrastructure/ai/PortfolioAnalysisAiAdapter.java` implements
  `PortfolioAnalysisAiPort` — depends only on `ai.business.GenerateAiUseCase` (contract C2).
- [x] T039 [US1] `portfolioanalysis/business/{RequestPortfolioAnalysisUseCase,
  PortfolioAnalysisRequestService}.java` — `requestAutomatic(portfolioId)` /
  `requestManual(portfolioId)`; pre-check + DB-constraint-race handling (research D4).
- [x] T040 [US1] `portfolioanalysis/business/PortfolioAnalysisWorker.java` — `@Async(
  "portfolioAnalysisExecutor")` `runAsync(AnalysisId)`: `RUNNING` → fetch snapshot → build context
  → (insufficient? `FAILED` : call AI port) → terminal state; the whole body wrapped in
  `try/catch(Throwable)` (FR-057).
- [x] T041 [US1] `portfolioanalysis/infrastructure/config/PortfolioAnalysisAsyncConfiguration.java`
  — `@EnableAsync` + `portfolioAnalysisExecutor` `ThreadPoolTaskExecutor` bean (research D5; spec A3
  sizing).
- [x] T042 [US1] `portfolioanalysis/business/PortfolioAnalysisOnCreationListener.java` —
  `@EventListener(PortfolioCreatedEvent)` → `requestAutomatic(event.portfolioId())` (research D5;
  reuses FD004's existing event, a second independent listener).
- [x] T043 [US1] Run T031–034 GREEN.
- [x] T044 [US1] `portfolioanalysis/PortfolioAnalysisOnCreationIT` (Testcontainers, full Spring
  context) — create a Portfolio via the real create endpoint; assert response timing/shape
  unaffected (SC-001) and a `PortfolioAnalysis` row exists immediately after (SC-002).

**Checkpoint**: an automatic analysis request is created and (with `OPENAI_API_KEY` unset in tests)
resolves to `FAILED(NOT_CONFIGURED)` end to end — proving the whole async chain, offline (VC-style
proof mirroring EN006's own pattern).

---

## Phase 4: User Story 2 — Portfolio detail shows the latest completed analysis (Priority: P1)

**Goal**: the Investor can see a completed analysis's diversification/insights/risks.

**Independent Test**: with a `COMPLETED` row seeded, `GET .../analysis/latest` returns it; Portfolio
detail renders it.

### Tests for User Story 2 ⚠️

- [x] T045 [P] [US2] `PortfolioAnalysisQueryServiceTest` (RED) — `findLatest` returns `NONE` when
  absent, else the latest row regardless of status.
- [x] T046 [P] [US2] `PortfolioAnalysisResponseMapperTest` (RED) — completed content maps fully;
  no `provider`/`model`/`promptId`/token/cost field ever serialized (FD005 §28).

### Implementation for User Story 2

- [x] T047 [US2] `portfolioanalysis/business/{PortfolioAnalysisQueryUseCase,
  PortfolioAnalysisQueryService}.java`.
- [x] T048 [US2] `portfolioanalysis/infrastructure/api/rest/dto/{PortfolioAnalysisResponse,
  RequestedPortfolioAnalysisResponse}.java` + `mapper/PortfolioAnalysisResponseMapper.java`.
- [x] T049 [US2] `portfolioanalysis/infrastructure/api/rest/PortfolioAnalysisController.java` —
  `GET /api/portfolios/{portfolioId}/analysis/latest` (404 via the existing `PortfolioNotFoundException`
  → widen the shared exception handler's `assignableTypes`, mirroring FD004's own controller
  widening).
- [x] T050 [US2] `contracts/openapi/openapi.yaml` — merge contract `openapi-fragment.md`'s `GET`
  path + `PortfolioAnalysis` schema (POST path added in US4, T057).
- [x] T051 [US2] `PortfolioAnalysisControllerContractTest` (swagger-request-validator-mockmvc, RED
  first for the GET path) — NONE/PENDING/RUNNING/COMPLETED/FAILED response shapes all validate.
- [x] T052 [US2] Run T045, T046, T051 GREEN.
- [x] T053 [P] [US2] Frontend `frontend/web/src/app/portfolio/portfolio-analysis.models.ts` +
  `portfolio-analysis.service.ts` (`getLatest(portfolioId)`).
- [x] T054 [US2] Frontend `portfolio-detail.page.ts` — new "AI Portfolio Analysis" section
  (completed-state rendering: diversification level+explanation, ordered key insights, risks by
  severity, analysed-at timestamp).
- [x] T055 [US2] Frontend spec additions — completed-state rendering test.

**Checkpoint**: US1+US2 independently green — a seeded completed analysis renders correctly.

---

## Phase 5: User Story 3 — Analysis-in-progress is explicit; stale results never shown as current (Priority: P1)

**Goal**: PENDING/RUNNING shows the in-progress message; polling detects the transition to terminal.

### Tests for User Story 3 ⚠️

- [x] T056 [US3] Frontend spec (RED) — in-progress message rendered for `PENDING`/`RUNNING`; a
  previous `COMPLETED` analysis is never shown while a newer one is in progress; polling stops on
  `COMPLETED`/`FAILED`/component destroy; a soft cap after N polls stops without erroring.

### Implementation for User Story 3

- [x] T057 [US3] Frontend `portfolio-analysis.service.ts` — polling mechanism (`interval(2000)` +
  `switchMap` + `takeWhile` on open status + `takeUntilDestroyed`, research D9; spec A4 soft cap).
- [x] T058 [US3] Frontend `portfolio-detail.page.ts` — in-progress state template + wiring to the
  polling observable.
- [x] T059 [US3] Run T056 GREEN.

**Checkpoint**: US1–US3 green.

---

## Phase 6: User Story 4 — Manual re-analysis (Priority: P1)

**Goal**: "Run analysis again" creates a new record without touching the previous one; duplicate
concurrent requests are rejected by the backend.

### Tests for User Story 4 ⚠️

- [x] T060 [US4] `PortfolioAnalysisPersistenceAdapterIT` (Testcontainers, RED first for the new
  case) — **two concurrent `save`s for the same portfolio's open request ⇒ exactly one succeeds**,
  the other surfaces `AnalysisAlreadyInProgressException`; a prior `COMPLETED` row is provably
  untouched (byte-for-byte) after a new one is requested (SC-005). Verified green against a real
  PostgreSQL — the actual `duplicate key value violates unique constraint
  "portfolio_analysis_one_open_per_portfolio_uk"` error was observed (a genuine race, not a mock).
  (A transient local Testcontainers/Docker-API-version issue seen earlier in this session had
  resolved itself by the time of this run — see pr-evidence.md.)
- [x] T061 [US4] Frontend spec (RED) — "Run analysis again" triggers `requestNew`; disabled/hidden
  while latest is `PENDING`/`RUNNING`.

### Implementation for User Story 4

- [x] T062 [US4] `PortfolioAnalysisController` — `POST /api/portfolios/{portfolioId}/analysis` →
  `202` with `RequestedPortfolioAnalysisResponse`, `409` (`Problem`, code
  `analysis-already-in-progress`) on `AnalysisAlreadyInProgressException`.
- [x] T063 [US4] `contracts/openapi/openapi.yaml` — merge the `POST` path + `RequestedPortfolioAnalysis`
  schema from `openapi-fragment.md`; extend `PortfolioAnalysisControllerContractTest` for 202/409.
- [x] T064 [US4] Frontend `portfolio-analysis.service.ts` — `requestNew(portfolioId)`; "Run analysis
  again" button wiring + disabled state.
- [x] T065 [US4] Run T060, T061 GREEN.

**Checkpoint**: US1–US4 green (BR-004, BR-005, BR-007, BR-008, AC-007, AC-008, SC-004, SC-005).

---

## Phase 7: User Story 5 — Failed analysis is explicit, recoverable, never leaks internals (Priority: P1)

### Tests for User Story 5 ⚠️

- [x] T066 [US5] Frontend spec (RED) — `FAILED` renders the controlled unavailable message + a
  working "Run analysis again"; asserts no provider payload/stack trace/internal text ever appears
  in the rendered DOM for any failure reason.

### Implementation for User Story 5

- [x] T067 [US5] Frontend `portfolio-detail.page.ts` — failed-state template.
- [x] T068 [US5] Run T066 GREEN.

**Checkpoint**: US1–US5 green.

---

## Phase 8: User Story 6 — OpenAI used only through EN006 (Priority: P1)

*(The adapter itself was built in Foundational B — this phase is the architecture-conformance
proof.)*

- [x] T069 [US6] `StandardArchitectureRulesTest` — add the 4 confinement rules (research/plan OD-6):
  `portfolioanalysis_core_is_free_of_portfolio`,
  `portfolio_is_accessed_only_from_the_portfolioanalysis_portfolio_adapter`,
  `portfolioanalysis_core_is_free_of_ai`, `ai_is_accessed_only_from_the_portfolioanalysis_ai_adapter`
  — mirroring the existing `portfolio`↔`marketdata` confinement pair exactly.
- [x] T070 [US6] Run the full `StandardArchitectureRulesTest` GREEN; grep-confirm zero OpenAI SDK
  type outside `ai.infrastructure.provider.openai`.

**Checkpoint**: US1–US6 green (AC-012).

---

## Phase 9: User Story 7 — Deterministic E2E; CI never depends on live OpenAI (Priority: P2)

- [x] T071 [P] [US7] `e2e/openai-stub/{server.js,Dockerfile}` — deterministic
  `POST /v1/chat/completions` fixture matching FD005 §42's example content; a second mode/flag to
  simulate a provider failure (E2E-003).
- [x] T072 [US7] `infrastructure/local/compose.e2e.yaml` — add `openai-stub` service;
  `OPENAI_BASE_URL` passthrough to `backend`, mirroring `FINNHUB_BASE_URL`.
- [x] T073 [US7] `e2e.sh` — build `openai-stub`.
- [x] T074 [P] [US7] `e2e/support/portfolioAnalysis.ts` — helpers (analysis section locator,
  in-progress/completed/failed state locators, "Run analysis again" locator, poll-until-terminal).
- [x] T075 [US7] `e2e/tests/fd005-automatic-analysis.spec.ts` (E2E-001, FD005 §42).
- [x] T076 [US7] `e2e/tests/fd005-manual-reanalysis.spec.ts` (E2E-002, FD005 §43) — asserts A1
  preserved via the API after A2 completes.
- [x] T077 [US7] `e2e/tests/fd005-provider-failure.spec.ts` (E2E-003, FD005 §44).
- [x] T078 [US7] Run `./e2e.sh` GREEN — all suites, incl. FD001–FD004 regression.

**Checkpoint**: full E2E gate green, offline.

---

## Phase 10: Polish & cross-cutting concerns

- [x] T079 [P] `implementation/platform/README.md` — FD005 capability row/paragraph.
- [x] T080 [P] `implementation/platform/backend/core-service/README.md` — `portfolioanalysis`
  module section + the EN006 extension note (per-task routing, OpenAI adapter, prompt-versioning
  fix).
- [x] T081 Full gate: `./mvnw -B clean verify`; `ng test`; `./e2e.sh` — all green, offline.
- [x] T082 `specs/FD005-ai-portfolio-analysis/pr-evidence.md` — gate results, BR/AC traceability
  table, scope review (SC-009), and any implementation-time corrections (mirroring EN006's
  pr-evidence.md style).
- [x] T083 Scope review: `git diff` shows exactly one new Flyway migration, the 2 new `openapi.yaml`
  paths, the documented EN006 extension files, the new `portfolioanalysis` module, the frontend
  additions, and the new `openai-stub` E2E service — nothing else; no unapproved `product/` edit.
- [ ] T084 `/project-verify FD005-ai-portfolio-analysis` (user-triggered closure gate).

**Checkpoint**: all gates green; ready for human closure.

---

## Dependencies & execution order

- **Setup (T001–T004)** — no dependencies.
- **Foundational A (T005–T016)** — depends on Setup; **blocks** Foundational B and C (a real
  provider needs per-task routing to be reachable at all without disturbing `diagnostic`).
- **Foundational B (T017–T024)** — depends on Foundational A.
- **Foundational C (T025–T030)** — independent of A/B; may run in parallel with them.
- **US1 (T031–T044)** — depends on all of Foundational A/B/C.
- **US2 (T045–T055)** — depends on US1 (needs a real, persisted analysis to query).
- **US3 (T056–T059)** — depends on US2 (needs the frontend section US2 built).
- **US4 (T060–T065)** — depends on US1 (repository/worker) and US2 (controller); independent of US3.
- **US5 (T066–T068)** — depends on US2/US3's frontend section.
- **US6 (T069–T070)** — depends on Foundational B + US1 (both adapters must exist to confine).
- **US7 (T071–T078)** — depends on US1–US6 all being complete.
- **Polish (T079–T084)** — depends on US7.

## Parallel opportunities

- T001–T004 (Setup) — independent files, parallel.
- T017, T018, T024 (OpenAI config/DTOs) — independent files, parallel; T019–T022 sequential
  (client → mapper → adapter → tests).
- T025–T029 (Foundational C domain types) — independent files, fully parallel.
- Within each user story's "Tests" sub-phase, `[P]`-marked tests are independent and parallel.
- T071, T074 (E2E stub + support helpers) — independent, parallel.
- T079, T080 (READMEs) — independent, parallel.

## Implementation strategy

**MVP = Setup + Foundational A/B/C + US1** — proves the entire asynchronous chain (creation →
`PENDING` → real OpenAI-routed worker → terminal state) end to end, offline, before any UI exists.

**Incremental delivery**: US2 (read/render) → US3 (in-progress UX) → US4 (manual trigger +
concurrency) → US5 (failure UX) → US6 (architecture proof) → US7 (E2E) → Polish. Each checkpoint
leaves the full existing suite green (FD001–FD004/EN004–EN006 regression-checked at Foundational A
and again at the final Polish gate).
