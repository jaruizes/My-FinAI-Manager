# Implementation Plan: AI Portfolio Analysis (FD005)

**Branch**: `FD005-ai-portfolio-analysis` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)

**Authoritative Feature**: `product/definition/features/FD005-ai-portfolio-analysis/FD005-ai-portfolio-analysis.md`
(**Approved**; §48 signed jaruiz 2026-09-05; §47 resolved same day: Q1 real OpenAI adapter, Q2
Spring `@Async`+`ThreadPoolTaskExecutor`, Q3 new sibling module `portfolioanalysis`).

## Summary

A new `portfolioanalysis` module (ADR-003 layout) adds: `PortfolioAnalysis` +
`PortfolioAnalysisInsight` + `PortfolioAnalysisRisk` persistence (new Flyway `V5`), a
`PortfolioAnalysisRequestService` that creates a `PENDING` record synchronously (DB-enforced
one-open-request-per-portfolio) and hands off to an `@Async` `PortfolioAnalysisWorker`, a
`PortfolioContextGateway` ACL reading `portfolio`'s existing query use-cases, a
`PortfolioAnalysisContextBuilder` (pure domain calculator) turning that into a compact AI context,
and a `PortfolioAnalysisAiPort` consuming EN006's `GenerateAiUseCase`. Two new REST endpoints
(`GET .../analysis/latest`, `POST .../analysis`) and a new "AI Portfolio Analysis" section in
Portfolio detail with polling.

**FD005 is EN006's first real consumer and first real external-provider adapter.** This requires a
small, anticipated extension to EN006 itself (documented below and in research.md D1) —
per-task provider routing (`ai.tasks.<task>.provider`), which EN006's own enabler text already
sketched as a future capability (§7) and is now implemented because FD005 genuinely needs it: the
EN006 diagnostic endpoint keeps using the local/stub adapter (`ai.default-provider=local`,
unaffected) while `portfolio-analysis` is routed to the new `OpenAiModelAdapter`
(`ai.tasks.portfolio-analysis.provider=openai`).

## Technical Context

**Language / Runtime**: Java 21, Spring Boot 3.5.6 (unchanged). Angular 20 for the frontend
addition (Portfolio detail extension + polling).

**Primary Dependencies**:
- Reused: Spring Boot Web/Actuator/Async, Spring Data JPA, Flyway, Jackson, JUnit 5, Mockito,
  ArchUnit, WireMock (already a transitive test dependency via `spring-cloud-contract-wiremock`? —
  **not yet present**; EN005's Finnhub/Frankfurter adapters were tested with
  `MockRestServiceServer`/`MockWebServer`-equivalent, not WireMock specifically — research D2
  resolves the exact mechanism, reusing the project's existing pattern (`MockRestServiceServer`, no
  new dependency) rather than adding WireMock).
- **New**: none beyond what's already in `pom.xml` — the OpenAI adapter reuses Spring's
  `RestClient` (like Finnhub/Frankfurter), and tests reuse `MockRestServiceServer` (like
  Finnhub/Frankfurter's adapter tests) — **no WireMock, no new HTTP/test library**.

**Storage**: PostgreSQL 16. **One new Flyway migration** `V5__portfolio_analysis.sql` —
`portfolio_analysis` (+ a partial unique index enforcing at most one open (`PENDING`/`RUNNING`)
request per Portfolio — the DB-level duplicate-prevention FR-013 requires), `portfolio_analysis_insight`,
`portfolio_analysis_risk`. FD001–FD004/EN004 tables untouched.

**Testing**: `./mvnw -B clean verify` (Surefire unit + Failsafe/Testcontainers IT + JaCoCo + ArchUnit).
`PortfolioAnalysisContextBuilder`, status-transition logic, and the OpenAI adapter's mapping are
RED-first TDD. A Testcontainers IT proves the DB-level duplicate-prevention constraint and the full
async round-trip. `ng test` for the new Angular section + polling. `./e2e.sh` for E2E-001/002/003
against a stubbed OpenAI HTTP boundary (a sibling to `finnhub-stub`, e.g. `openai-stub`, in the E2E
compose file only).

**Target Platform**: the existing `core-service` container (ADR-001); the `ai` module's existing
Docker Compose observability stack (EN006/ADR-004) picks up FD005's telemetry automatically — no
new container.

**Constraints**:
- ADR-003 module layout for `portfolioanalysis`; AR-062-style confinement for its two cross-module
  dependencies (`portfolio`, `ai`) — each touched from exactly one adapter package.
- Portfolio creation's response time and shape are unaffected (FR-003; SC-001).
- `portfolioanalysis.domain`/`.business` never reference OpenAI SDK types (there is none — Spring
  `RestClient` + Jackson only) — enforced by extending EN006's existing ArchUnit rule.
- No LLM computes deterministic values; the model only interprets supplied facts (FR-022, FR-029).
- CI never depends on live OpenAI (FR-053, FR-054).
- `./start.sh` / `./stop.sh` / `./e2e.sh` interface unchanged in shape.

**Scale/Scope**: one new module (~30 classes: 3 persisted entities + domain models/enums, 2 domain
ports + 2 adapters, business request/worker services, 1 pure context-builder calculator, REST
controller + DTOs); one new Flyway migration; two new OpenAPI paths; a bounded, well-scoped
extension to EN006 (per-task provider routing, one new provider adapter, one new exception type,
prompt-versioning fix — see research D1); a new Angular section + polling service; ~3 new E2E tests
+ one new stub service in the E2E compose override only.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the approved Feature (§48 signed 2026-09-05). No `product/` edit beyond the human-directed FD005 §47/§48 recording. |
| II | Definitions & Enablers Are Authoritative Intent | **PASS** | Every FR traces to a Feature Definition §/BR/AC (spec Traceability). No scope expansion — historical UI, chat, recommendations, RAG all explicitly out of scope and not touched. |
| III | Derived Artifacts & Repository Layout | **PASS** | Artifacts under `specs/FD005-…/`; code under `implementation/platform/`. |
| IV | No Invention; Surface Material Ambiguity | **PASS** | The pre-specification Draft/unsigned gate was surfaced and resolved (Q1–Q3) before spec content was written. The EN006 extension (per-task provider routing) is documented as an anticipated, mechanical, reversible enhancement — not a new architectural decision requiring fresh escalation (EN006's own text already sketched the `ai.tasks.*` shape). |
| V | Technical Enablers Stay Technical | N/A | FD005 is a Feature, not an Enabler — investor-facing behavior is exactly the point. |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | `portfolioanalysis.domain`/`.business` framework-free; `PortfolioAnalysisContextBuilder` is a pure calculator; the AI model never computes a deterministic value (FR-022). |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | Context builder, status transitions, duplicate-prevention are RED-first; Testcontainers PostgreSQL for the persistence + concurrency IT. OpenAI is a true external provider — `MockRestServiceServer`, no live call in CI (constitution VII carve-out). |
| VIII | Contract-First External APIs | **PASS** | Two new paths added to `contracts/openapi/openapi.yaml`; FD001/FD003/FD004 paths unchanged. |

**Repository-structure / technology-policy quick check:**

| Check | Status | Evidence |
|---|---|---|
| One `core-service` deployable (ADR-001) | PASS | no new service |
| ADR-003 module layout in `portfolioanalysis` | PASS | `domain/{model,ports,exceptions}`, `business`, `infrastructure/{persistence,portfolio,ai,api,config}` |
| LLM Provider Policy (OpenAI `ALLOWED`) | PASS | `technology-policy.md` — OpenAI already listed `ALLOWED` behind an abstraction |
| No new HTTP/test library | PASS | reuses `RestClient` + `MockRestServiceServer` (research D2) |
| Schema change uses the approved migration mechanism | PASS | `V5__portfolio_analysis.sql` |
| Secret handling (`OPENAI_API_KEY`) | PASS | mirrors `FINNHUB_API_KEY` exactly — header auth, never logged/committed |
| No new independently deployable service | PASS | none introduced |
| No new ADR | PASS | async mechanism is in-process (resolved Q2) |

**Result: PASS.** Re-checked post-design below.

## Open Decisions (technical) — recommended positions

| ID | Decision | Recommended position | Rejected |
|---|---|---|---|
| OD-1 | EN006 extension: per-task provider routing | `AiInvocationPolicy` resolves `AiModelPort` from a `Map<String, AiModelPort>` keyed by provider id (Spring bean qualifiers `"local"`/`"openai"`), using `ai.tasks.<taskType>.provider` if present, else `ai.default-provider`. Both adapters always registered (no more `@ConditionalOnProperty` gating one out); each independently reports "not configured" when unusable. EN006's diagnostic endpoint (`taskType="diagnostic"`) is unaffected — no task override for it, so it keeps using `ai.default-provider=local`. | A second, parallel `AiInvocationPolicy`/`GenerateAiUseCase` bean just for FD005 — duplicates all of EN006's guardrail/budget/telemetry orchestration; directly contradicts EN006's own "one policy" design (FR-038 of EN006). |
| OD-2 | EN006 extension: prompt versioning per task | `PromptRepositoryPort.findTaskInstructions` returns `Optional<PromptReference>` (its own `promptId`/`promptVersion`), not `Optional<String>`; `PromptService.compose()` uses the **task's own** id/version when task instructions exist (else the global prompt's). Fixes a real EN006 gap (FD005 FR-035 needs `promptId="portfolio-analysis"`, not `"global-system"`, recorded per analysis) that the "diagnostic" task never exercised (it has no dedicated instructions). | Leave `PromptService` as-is and persist `"global-system"`/`"v1"` for every analysis regardless of task — silently fails FD005 FR-035/BR-013 (prompt-version traceability meaningless). |
| OD-3 | `OpenAiModelAdapter` structured output mechanism | OpenAI Chat Completions with `response_format: {"type": "json_object"}` + prompt-instructed shape, parsed and validated by EN006's existing `StructuredOutputValidator` (already built, dependency-free). | OpenAI's native `json_schema` structured-output mode (translating `OutputSchema` → JSON Schema) — more precise but adds a translation layer EN006 deliberately avoided (research D3 in EN006); deferred until actually needed. |
| OD-4 | Async execution wiring | Reuse the **existing** `PortfolioCreatedEvent` (FD004 already publishes it) — add a **second**, independent `@EventListener` (`PortfolioAnalysisOnCreationListener`) that calls `@Async` `PortfolioAnalysisWorker.runAsync(id)` after the PENDING row commits. No new event type. Manual re-analysis calls the same worker directly from the controller's use-case. | A new `PortfolioAnalysisRequestedEvent` — unnecessary indirection; the existing event already fires exactly once, after commit, for every successful creation. |
| OD-5 | Duplicate-request prevention (FR-013) | A **partial unique index** `portfolio_analysis_one_open_per_portfolio_uk ON portfolio_analysis(portfolio_id) WHERE status IN ('PENDING','RUNNING')` — the database is the source of truth; the service pre-checks (for a fast, friendly 409) and also handles the constraint-violation race as a 409, never a 500. | Application-level locking only (e.g. a `synchronized` block or a distributed lock) — weaker (multiple app instances, restarts), and this project has no distributed-lock infrastructure to justify introducing one. |
| OD-6 | `PortfolioContextGateway` / `PortfolioAnalysisAiPort` confinement | Exactly one adapter package each: `portfolioanalysis.infrastructure.portfolio` (the only place touching `portfolio.*`) and `portfolioanalysis.infrastructure.ai` (the only place touching `ai.*`) — mirrors FD004's `EnMarketDataGatewayAdapter` "sole importer" pattern exactly, enforced by two new ArchUnit rule pairs. | Allowing `portfolioanalysis.business` to call `portfolio.business`/`ai.business` directly — breaks the established AR-062 confinement pattern this project has used for every prior cross-module read. |
| OD-7 | REST semantics | `POST /api/portfolios/{id}/analysis` → **`202 Accepted`** (request accepted, processing continues) with `{analysisId, status, requestedAt}`; a duplicate open request → **`409 Conflict`** (`ValidationProblem`-style RFC 9457 body, code `ANALYSIS_ALREADY_IN_PROGRESS`). `GET .../analysis/latest` → **`200`** always for a known Portfolio, with an explicit `status: "NONE"` body when no analysis has ever been requested (mirrors FD004's synthetic-`PENDING`-for-no-snapshot pattern) — never a `404` for "no analysis yet" (that's a normal, expected state, not an error). | `201 Created` for the POST — misleading, since the *analysis* isn't created-and-done, it's accepted for async work; a `404` for "no analysis yet" — conflates "resource never existed" with "no analysis requested yet," which is a normal, common state right after EN004/pre-FD005 data or a race just after Portfolio creation. |
| OD-8 | Frontend polling | A small `PortfolioAnalysisPollingService` (Angular) polls `GET .../analysis/latest` every 2s while status is `PENDING`/`RUNNING`, stops on `COMPLETED`/`FAILED`/component destroy (`takeUntilDestroyed`), with a soft cap (e.g. 60s) after which it stops and shows a "still processing" hint without erroring. | A fixed poll count with a hard error afterward — worse UX for a slightly slow provider; FD005 doesn't ask for a hard timeout in the UI, only a stopping condition. |
| OD-9 | Insufficient-data handling (spec A7) | The `PENDING` row is still created (so the Investor gets explicit feedback — never silence), but the async worker resolves it straight to `FAILED` with `failureReasonCode = INSUFFICIENT_DATA` **without** calling the AI provider at all when the latest valuation is `FAILED`/absent/has no valued Position. | Reject the analysis request outright (no row at all) — contradicts FD005 §15's framing ("the analysis attempt should fail... according to specification rules") and FR-057 (every request gets a terminal record). |

## Project Structure

### Documentation (this feature)

```text
specs/FD005-ai-portfolio-analysis/
├── plan.md              # this file
├── research.md          # Phase 0 — D1…D10
├── data-model.md         # Phase 1 — full schema + domain model
├── contracts/
│   ├── openapi/portfolio-analysis.yaml       # the 2 new paths (merged into contracts/openapi/openapi.yaml)
│   ├── portfolio-analysis-ports.md           # PortfolioContextGateway / PortfolioAnalysisAiPort contracts
│   └── openai-provider-contract.md           # OpenAI request/response/error-translation contract
├── quickstart.md         # Phase 1 — validation guide
├── checklists/requirements.md
└── spec.md
```

### Source Code (repository)

```text
implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/

portfolioanalysis/                                    # NEW module
├── domain/
│   ├── model/
│   │   ├── PortfolioAnalysis.java             # aggregate; id, portfolioId, status, timestamps,
│   │   │                                        # summary, overallDiversification, provider/model/
│   │   │                                        # prompt refs, usage, failureReasonCode, trigger,
│   │   │                                        # insights: List<Insight>, risks: List<Risk>
│   │   ├── AnalysisStatus.java                 # PENDING/RUNNING/COMPLETED/FAILED
│   │   ├── CreationTrigger.java                # AUTOMATIC/MANUAL
│   │   ├── DiversificationLevel.java           # LOW/MODERATE/HIGH
│   │   ├── RiskSeverity.java                   # HIGH/MEDIUM/LOW
│   │   ├── RiskType.java                       # SECTOR_CONCENTRATION/... /OTHER
│   │   ├── FailureReason.java                  # NOT_CONFIGURED/PROVIDER_UNAVAILABLE/GUARDRAIL_REJECTED/
│   │   │                                        # INVALID_OUTPUT/INSUFFICIENT_DATA/TIMEOUT/UNKNOWN
│   │   ├── PortfolioContextSnapshot.java       # from PortfolioContextGateway (own vocabulary)
│   │   ├── PortfolioAnalysisContext.java       # compact AI-ready payload (from the context builder)
│   │   ├── PortfolioAnalysisContextBuilder.java # PURE calculator: Snapshot -> Context (no ports)
│   │   └── PortfolioAnalysisResult.java        # AI-produced content, pre-persistence
│   ├── ports/
│   │   ├── PortfolioAnalysisRepository.java    # save / findLatestByPortfolioId / findById
│   │   ├── PortfolioContextGateway.java        # ACL -> portfolio module
│   │   └── PortfolioAnalysisAiPort.java        # ACL -> ai module (EN006)
│   └── exceptions/
│       ├── AnalysisAlreadyInProgressException.java
│       └── AnalysisFailedException.java        # carries a FailureReason
│
├── business/
│   ├── RequestPortfolioAnalysisUseCase.java    # inbound port: requestAutomatic / requestManual
│   ├── PortfolioAnalysisRequestService.java    # implements it; creates PENDING, triggers worker
│   ├── PortfolioAnalysisQueryUseCase.java      # inbound port: findLatest(portfolioId)
│   ├── PortfolioAnalysisQueryService.java      # implements it
│   ├── PortfolioAnalysisWorker.java            # @Async — RUNNING -> {COMPLETED|FAILED}
│   └── PortfolioAnalysisOnCreationListener.java # @EventListener(PortfolioCreatedEvent) -> requestAutomatic
│
└── infrastructure/
    ├── portfolio/
    │   └── PortfolioContextGatewayAdapter.java  # implements PortfolioContextGateway; ONLY class
    │                                             # touching portfolio.* (business query use-cases)
    ├── ai/
    │   └── PortfolioAnalysisAiAdapter.java      # implements PortfolioAnalysisAiPort; ONLY class
    │                                             # touching ai.* (GenerateAiUseCase)
    ├── persistence/
    │   ├── entity/{PortfolioAnalysisEntity,PortfolioAnalysisInsightEntity,PortfolioAnalysisRiskEntity}.java
    │   ├── repository/PortfolioAnalysisJpaRepository.java
    │   ├── mapper/PortfolioAnalysisPersistenceMapper.java
    │   └── PortfolioAnalysisPersistenceAdapter.java  # implements PortfolioAnalysisRepository
    ├── api/rest/
    │   ├── PortfolioAnalysisController.java
    │   ├── dto/{PortfolioAnalysisResponse,RequestAnalysisResponse}.java
    │   └── mapper/PortfolioAnalysisResponseMapper.java
    └── config/
        └── PortfolioAnalysisAsyncConfiguration.java  # @EnableAsync + ThreadPoolTaskExecutor bean

# EN006 extensions (existing module, touched — see research D1/D2)
ai/
├── domain/exceptions/AiProviderNotConfiguredException.java     # NEW
├── domain/ports/PromptRepositoryPort.java                      # CHANGED: findTaskInstructions -> Optional<PromptReference>
├── business/PromptService.java                                 # CHANGED: task's own id/version when present
├── business/AiInvocationPolicy.java                             # CHANGED: Map<String,AiModelPort> + task routing
├── infrastructure/provider/local/LocalAiModelAdapter.java       # CHANGED: @Component("local"), no @ConditionalOnProperty
├── infrastructure/provider/openai/                              # NEW package
│   ├── OpenAiModelAdapter.java                                  # implements AiModelPort; @Component("openai")
│   ├── client/OpenAiRestClient.java
│   ├── dto/{OpenAiChatRequest,OpenAiChatResponse}.java
│   ├── mapper/OpenAiChatMapper.java
│   └── config/OpenAiProperties.java
├── infrastructure/prompt/ClasspathPromptRepository.java         # CHANGED: +"portfolio-analysis" task
└── infrastructure/config/{AiProperties,AiModuleConfiguration}.java  # CHANGED: +tasks map, +Map<String,AiModelPort> bean

architecture/StandardArchitectureRulesTest.java   # + 4 confinement rules (portfolioanalysis<->portfolio,
                                                    #   portfolioanalysis<->ai) + widen only_provider_client_packages_use_restclient

src/main/resources/
├── db/migration/V5__portfolio_analysis.sql        # NEW
├── prompts/tasks/portfolio-analysis-v1.txt         # NEW — FD005 §23 task instructions
└── application.yml                                 # + openai.* ; + ai.tasks.portfolio-analysis.provider=openai

contracts/openapi/openapi.yaml   # + GET .../analysis/latest, POST .../analysis

implementation/platform/
├── frontend/web/src/app/portfolio/
│   ├── portfolio-analysis.models.ts                # NEW
│   ├── portfolio-analysis.service.ts                # NEW (HTTP + polling)
│   └── portfolio-detail.page.ts                     # CHANGED — new "AI Portfolio Analysis" section
├── e2e/openai-stub/{server.js,Dockerfile}           # NEW — deterministic OpenAI HTTP boundary
├── infrastructure/local/compose.e2e.yaml            # + openai-stub service, OPENAI_BASE_URL passthrough
└── e2e/tests/fd005-*.spec.ts                        # NEW — E2E-001/002/003
```

**Structure Decision**: a new sibling module `portfolioanalysis` (resolved Q3), consuming `portfolio`
and `ai` each through exactly one confined adapter (AR-062 style). EN006 gains a small, anticipated
extension (per-task provider routing + a real OpenAI adapter + a prompt-versioning fix) rather than
a parallel orchestration path. Frontend and E2E follow the FD003/FD004 precedent exactly (additive
Portfolio-detail section, a compose-only stub service for the new external provider boundary).

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| The EN006 extension (per-task routing) regresses the existing diagnostic endpoint / EN006 tests | Med | High | `taskType="diagnostic"` has no `ai.tasks.diagnostic.*` override, so it keeps resolving to `ai.default-provider=local` — behavior-identical; EN006's existing `PlatformIntegrationIT` case is re-run unchanged as a regression check. |
| DB unique-index race still allows two concurrent inserts under high contention | Low | Med | The partial unique index is the authoritative guard (not just an app-level check) — a Testcontainers IT fires two concurrent requests and asserts exactly one succeeds, the other gets 409. |
| `@Async` task throws and the exception is swallowed by the executor, leaving a row stuck at `RUNNING` | Med | High | The worker method wraps its entire body in try/catch(Throwable) and persists `FAILED` on any exception, including framework-level ones — matches FR-057. A dedicated test simulates a `RuntimeException` mid-worker. |
| OpenAI's actual JSON structured-output behavior drifts from the fixture used in tests | Low | Med | `openai-provider-contract.md` pins the exact request/response shape; adapter tests use `MockRestServiceServer` against that pinned shape; E2E stub returns the same shape. |
| Frontend polling leaks after navigating away | Med | Low | `takeUntilDestroyed()` (Angular's standard idiom) stops the interval on component destruction; a unit test asserts no further HTTP call after destroy. |
| Coverage dip from a large new module | Med | Med | Every domain model/enum/pure-calculator branch is unit-tested individually (EN006 precedent); JaCoCo gate is the check. |

## Phase 0 — Research

See [research.md](./research.md). Decisions D1–D10 cover: the EN006 per-task-provider-routing
extension and its exact wiring; the prompt-versioning fix; the OpenAI request/response/error
mapping; the DB-level duplicate-prevention mechanism; the async wiring reusing
`PortfolioCreatedEvent`; the context-builder's exact field selection/compaction; the REST
status-code choices; the frontend polling design; the E2E stub design; the full test matrix. No
`NEEDS CLARIFICATION` remains.

## Phase 1 — Design & Contracts

Outputs: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).

**Post-Design Constitution re-check: PASS** — `portfolioanalysis.domain`/`.business` stay
framework-free and confined to their two ACL adapters (ArchUnit); the AI model never computes a
deterministic value; the async mechanism is in-process, no ADR needed; the EN006 extension is
mechanical and regression-tested against the existing diagnostic-endpoint behavior; OpenAI is
consumed only through EN006's provider-neutral port, tested without any live call in CI; the schema
change uses Flyway; the API change is contract-first (`openapi.yaml`); no unapproved technology,
service, or business behavior is introduced.
