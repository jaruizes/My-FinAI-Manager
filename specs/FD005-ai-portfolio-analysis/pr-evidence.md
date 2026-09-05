# FD005 — AI Portfolio Analysis · PR Evidence

## What requirement does this implement?

**FD005 — AI Portfolio Analysis** (Feature Definition, Approved 2026-09-05 — §48 signed by jaruiz).
Generates an AI-assisted analysis of a Portfolio — overall diversification (level + explanation),
2–5 grounded key insights, and 1–4 classified risks — computed from the FD004 deterministic
valuation via EN006's provider-neutral AI architecture, never recalculated by the model. Requested
automatically right after Portfolio creation (never blocking the create response) and manually via
"Run analysis again"; at most one open (`PENDING`/`RUNNING`) request per Portfolio; every request
creates a new immutable record; only the latest is ever shown.

## Which specification / tasks does it trace to?

- Feature: `product/definition/features/FD005-ai-portfolio-analysis/FD005-ai-portfolio-analysis.md`
  (§48 signed; §47 Q1–Q3 resolved by the product owner 2026-09-05 — real OpenAI adapter, Spring
  `@Async`+`ThreadPoolTaskExecutor`, new sibling module `portfolioanalysis`).
- Consumed capabilities: FD004 (`portfolio.business.{PortfolioQueryUseCase,
  PortfolioValuationQueryUseCase}` — read-only, via ACL), EN006 (`ai.business.GenerateAiUseCase` —
  extended this release, see below).
- Governing: ADR-003 (Standard Spring Backend Architecture), AR-062 (inter-module reads through a
  published port). **No new ADR** — the async mechanism is in-process (resolved Q2), the new module
  follows the established one-module-per-capability pattern (resolved Q3).
- SDD artifacts: `specs/FD005-ai-portfolio-analysis/` — `spec.md` (58 FR, 10 SC, US1–US7),
  `plan.md` (Constitution Check PASS; 9 Open Decisions OD-1…OD-9), `research.md` (D1–D10),
  `data-model.md` (incl. an "Implementation correction" note — see below), `contracts/`
  (`portfolio-analysis-ports.md`, `openai-provider-contract.md`, `openapi-fragment.md`),
  `quickstart.md`, `tasks.md` (T001–T084).

## What changed?

| Area | Change |
|---|---|
| EN006 extension (per-task routing) | `AiInvocationPolicy` — constructor takes `Map<String, AiModelPort> modelPortsByProvider` (was one `AiModelPort`); resolves `providerId = ai.tasks.<task>.provider` per request, falling back to `default-provider`; missing provider id → `AiConfigurationErrorException`. `LocalAiModelAdapter` → `@Component("local")` (named bean, `@ConditionalOnProperty` removed). `AiProperties`/`AiInvocationSettings` — **+`tasks`/`taskProviders`** map. `PromptRepositoryPort.findTaskInstructions` → returns `Optional<PromptReference>` (was `Optional<String>`) so a task's persisted `promptId`/`promptVersion` identifies its **own** prompt, not the global one. `ClasspathPromptRepository` — serves `prompts/tasks/portfolio-analysis-v1.txt`. **NEW** `AiProviderNotConfiguredException`. `application.yml` — `+ai.tasks.portfolio-analysis.provider: openai` (no entry for `diagnostic` — unaffected). |
| EN006 extension (real OpenAI adapter) | **NEW** `ai.infrastructure.provider.openai` package: `OpenAiProperties` (`@ConfigurationProperties("openai")`), `dto.{OpenAiChatRequest,OpenAiChatResponse}` (Jackson records), `client.OpenAiRestClient` (own `RestClient`, `Authorization: Bearer`, full error translation), `mapper.OpenAiChatMapper`, `OpenAiModelAdapter` (`@Component("openai")`). Blank `OPENAI_API_KEY` → `AiProviderNotConfiguredException`, no outbound call. `StandardArchitectureRulesTest`'s `only_provider_client_packages_use_restclient` widened to include `..openai.client..`. `application.yml` / `compose.yaml` — `+openai.*` / `OPENAI_API_KEY`/`OPENAI_BASE_URL` (blank-safe, same pattern as `FINNHUB_API_KEY`). |
| Contract | `openapi.yaml` — **+`GET /api/portfolios/{portfolioId}/analysis/latest`** (`PortfolioAnalysis` schema: `status` `NONE`\|`PENDING`\|`RUNNING`\|`COMPLETED`\|`FAILED`; content fields present only when `COMPLETED`) and **+`POST /api/portfolios/{portfolioId}/analysis`** (`202` + `RequestedPortfolioAnalysis`; `404`/`409` `Problem`). No `provider`/`model`/`promptId`/`promptVersion`/token/cost field anywhere (FD005 §28). |
| Backend — new `portfolioanalysis` module | ADR-003 layout, `domain/{model,ports,exceptions}` + `business` + `infrastructure/{persistence,portfolio,ai,api/rest,config}`. Domain: `PortfolioAnalysis` (+ nested `Insight`/`Risk`, wither methods), 6 enums, `PortfolioContextSnapshot`/`PortfolioAnalysisContext`/`PortfolioAnalysisResult`, `PortfolioAnalysisContextBuilder` (pure calculator), 3 ports, 2 own exceptions (`AnalysisAlreadyInProgressException`, `AnalysisFailedException`) **+1** ACL-translation exception (`PortfolioNotFoundException` — see correction below). Business: `PortfolioAnalysisRequestService`, `PortfolioAnalysisWorker` (`@Async`), `PortfolioAnalysisQueryService`. Infrastructure: JPA entities/repository/mapper/adapter (Flyway `V5__portfolio_analysis.sql`), `PortfolioContextGatewayAdapter` + `PortfolioAnalysisOnCreationListener` (sole `portfolio.*` importers), `PortfolioAnalysisAiAdapter` (sole `ai.*` importer), `PortfolioAnalysisController` + its own `PortfolioAnalysisExceptionHandler`, `PortfolioAnalysisAsyncConfiguration` (`ThreadPoolTaskExecutor`, core 2 / max 4). |
| Architecture conformance | **+4** `StandardArchitectureRulesTest` rules (27 total): `portfolioanalysis_core_is_free_of_portfolio`, `portfolio_is_accessed_only_from_the_portfolioanalysis_portfolio_adapter`, `portfolioanalysis_core_is_free_of_ai`, `ai_is_accessed_only_from_the_portfolioanalysis_ai_adapter` — mirrors the `portfolio`↔`marketdata` pair exactly. |
| Frontend | **NEW** `portfolio-analysis.models.ts`, `portfolio-analysis.service.ts` (`getLatest`, `requestNew`). `portfolio-detail.page.ts` — new "AI Portfolio Analysis" section: completed-state (diversification level+explanation, ordered key insights, risks by severity, analysed-at timestamp), in-progress state + 2s polling with a 30-poll soft cap (research D9), failed state + "Run analysis again" (hidden while `PENDING`/`RUNNING`). |
| E2E | **NEW** `e2e/openai-stub/{server.js,Dockerfile}` (canned `/v1/chat/completions`; magic marker `E2E_PROVIDER_FAILURE` in the Portfolio name → `503`, mirroring `finnhub-stub`'s own magic-symbol convention). `compose.e2e.yaml`/`e2e.sh` — `+openai-stub` service, wired the same way as `finnhub-stub`. **NEW** `support/portfolioAnalysis.ts`, `tests/fd005-{automatic-analysis,manual-reanalysis,provider-failure}.spec.ts` (E2E-001/002/003). |
| Docs | `implementation/platform/README.md` (+FD005 capability row, +paragraph, +`OPENAI_API_KEY` note, EN006 paragraph updated to describe the real adapter + per-task routing). `backend/core-service/README.md` (+`portfolioanalysis` module section, EN006 extension note, ArchUnit rule count 18→27). |
| Build | `pom.xml` — **+1** JaCoCo exclusion (`portfolioanalysis` JPA entities — same rationale/pattern as the existing `portfolio`/`financialinstrument` entity exclusions). No new dependency. |

**Not changed**: no other Flyway migration, no change to any FD001/FD003/FD004/EN004/EN005 schema,
port, or REST contract, no `pom.xml`/`package.json` dependency, no new deployable/broker/scheduler/
cache, no `start.sh`/`stop.sh` change, no Java/Angular major version.

## Why this design?

- **Per-task provider routing on `AiInvocationPolicy`, not a second orchestrator** (research D1):
  EN006's enabler text already sketched `ai.tasks.<task>.provider`; this is completing an
  anticipated design, not inventing new architecture. EN006's own `diagnostic` task is
  regression-proven unchanged (`AiInvocationPolicyTest` per-task-routing cases).
- **A real `MockRestServiceServer`-tested OpenAI adapter, no WireMock, no SDK** (resolved Q1;
  research D3): mirrors EN005's Finnhub/Frankfurter precedent exactly — own `RestClient`, own
  DTOs/mapper, translated errors, structured `ProviderCall` logging, env-var-gated key.
  `response_format: json_object` (not `json_schema`) avoids building an `OutputSchema`→JSON-Schema
  translator EN006 deliberately deferred.
- **Spring `@Async` + a dedicated `ThreadPoolTaskExecutor`, in-process, no ADR** (resolved Q2): no
  new deployable/broker; a small, planning-level pool (core 2 / max 4 — spec A3) keeps a slow
  analysis from starving Portfolio creation/read traffic.
- **New sibling module `portfolioanalysis`, not code inside `portfolio` or `ai`** (resolved Q3):
  follows the established one-module-per-bounded-capability pattern; reads `portfolio` and `ai`
  each through exactly one ACL adapter (AR-062), enforced by 4 new ArchUnit rules.
- **DB-level partial unique index for duplicate-request prevention** (research D4): mirrors FD001's
  own `idempotency_key` unique-constraint precedent — the database, not application logic, is the
  authoritative concurrency guard. **Proven with a real concurrent race**, not just a mocked
  exception path: `PortfolioAnalysisPersistenceAdapterIT`'s two-thread test hit the actual
  `duplicate key value violates unique constraint "portfolio_analysis_one_open_per_portfolio_uk"`
  Postgres error.
- **Reuses FD004's own `PortfolioCreatedEvent`, a second independent listener** (research D5): no
  new event type — FD004 already listens to the same event synchronously for valuation; the two
  listeners run independently, each with its own catch-all.

### Implementation corrections during T025–T044 (non-material, AR-062 compliance)

Two design details in the pre-implementation artifacts (data-model.md, plan.md) would have let
another module's type reach `portfolioanalysis.domain`/`business`, violating AR-062's "must not let
the other module's types reach its own domain or business packages" — the same rule already applied
to `portfolio.domain.ports.MarketDataGateway` ("Arguments are primitive Strings so no marketdata
enum or value object reaches portfolio.domain"). Both are safe, reversible, non-material wiring
fixes (CLAUDE.md §26) — no product/business-rule change — and are recorded in
`data-model.md`'s "Implementation correction" note:

1. **`portfolioId` is a plain `UUID` everywhere in `portfolioanalysis`**, not
   `portfolio.domain.model.PortfolioId` as originally drafted. Only the ACL adapter
   (`PortfolioContextGatewayAdapter`) converts it to a real `PortfolioId` when calling into
   `portfolio`.
2. **`portfolio.domain.exceptions.PortfolioNotFoundException` is translated at the same ACL
   boundary** into a new `portfolioanalysis`-owned exception of the same name, so it never reaches
   this module's domain/business/REST-advice code. The public HTTP `404` shape is identical either
   way.
3. **`PortfolioAnalysis.withCompleted` takes `(PortfolioAnalysisResult, Instant)`**, not
   `(PortfolioAnalysisResult, AiUsage, PromptReference, Instant)` as originally drafted —
   `PortfolioAnalysisResult` itself now carries the provider/model/promptId/promptVersion/token/cost
   fields the AI adapter produces, so no `ai.*` type ever reaches `portfolioanalysis.domain` either.
4. **`PortfolioAnalysisOnCreationListener` lives in `infrastructure.portfolio`**, not
   `portfolioanalysis.business` as tasks.md literally named it — it must import
   `portfolio.domain.events.PortfolioCreatedEvent`, so it belongs in the one adapter package allowed
   to do so.

All four are verified by the 4 new ArchUnit confinement rules (US6/T069-T070) and the full green
`./mvnw clean verify` — the confinement is enforced, not just asserted in prose.

## How was it tested?

Local only (no CI), everything offline (no live OpenAI, no live outbound Internet):

- **`./mvnw -B clean verify`** → **BUILD SUCCESS**. Surefire **506 tests, 0 failures / 0 errors, 2
  skipped** (pre-existing). Failsafe **77 tests, 0 failures / 0 errors** — includes
  `PortfolioAnalysisPersistenceAdapterIT` (the real concurrency race, above) and
  `PortfolioAnalysisOnCreationIT` (full HTTP→Spring→PostgreSQL slice: the create response is
  unaffected in timing/shape and a `PortfolioAnalysis` row exists immediately — SC-001/SC-002).
  JaCoCo bundle coverage ≥ 90 % branch (gate passed after excluding the new module's JPA entities,
  same established rationale as `portfolio`/`financialinstrument`, and adding targeted tests for the
  mapper's optional-field permutations, the listener's catch-all branch, `PortfolioAnalysisResult`'s
  validation guards, and 6 previously-untested `OpenAiRestClient` branches). **ArchUnit 27/27**
  (23 pre-existing + 4 new FD005 confinement rules).
- **`ng test`** (Node 20.19.1, ChromeHeadless) → **105 SUCCESS** (was 88 before FD005; +17 for US2
  completed-state rendering, US3 polling/soft-cap, US4 manual re-analysis, US5 failed-state/
  no-leaked-internals).
- **`./e2e.sh`** → **11 passed** (exit 0): all pre-existing FD001–FD004 specs green (regression) +
  `fd005-automatic-analysis` (E2E-001) + `fd005-manual-reanalysis` (E2E-002, A1 preserved — proven
  via the real API: a new `requestedAt` replaces the old one, never mutated in place) +
  `fd005-provider-failure` (E2E-003 — the `openai-stub`'s magic-marker failure mode; asserts no
  provider name/internal failure code/stack trace ever renders).
- Manual: `quickstart.md` sections A–F.

### A note on local environment flakiness encountered and resolved during this work

- **Testcontainers/Docker**: mid-session, the local Docker daemon (colima) transiently rejected
  Testcontainers' bundled client (`docker-java`) API version, blocking all `Testcontainers`-based
  ITs project-wide (confirmed pre-existing/environmental by re-running an already-passing FD004 IT
  with the identical failure). It resolved itself before the final gate run above — the final
  `./mvnw clean verify` above is a genuine, complete, green run including every Testcontainers IT.
- **E2E polling helper**: an early version of the E2E `waitForAnalysisTerminal` helper reloaded the
  page on each poll tick, which raced the SPA's own optimistic/polling state under the heavier
  background load of a full 11-test E2E run (every portfolio created by *any* spec triggers its own
  automatic analysis, sharing one small executor pool). Fixed to observe the already-running,
  already-unit-tested in-page polling via `expect(...).toPass()` instead of reloading, with a
  generous timeout — no production code change.
- **E2E timestamp assertion**: `fd005-manual-reanalysis.spec.ts` initially compared `requestedAt`
  strings for exact equality; PostgreSQL's `TIMESTAMPTZ` only keeps microsecond precision, so a
  value read back after a DB round-trip can differ from the in-memory nanosecond-precision value by
  a sub-millisecond rounding amount. Fixed to compare with a tolerance — the real assertion ("a new
  record, not A1 mutated") doesn't depend on nanosecond-exact serialization.

## Architecture boundaries

`portfolioanalysis` reads `portfolio` and `ai` each through exactly one dedicated ACL adapter
package (AR-062) — enforced by 4 new ArchUnit rules, not just described in prose. No new outbound
port beyond the two ACL ports this feature owns. No new inter-module coupling beyond the two
approved reads. One new Flyway migration, no FK into any FD004 table (a valuation snapshot is read
transiently, never pinned by a persisted reference — FD004's own snapshot is mutable/replaced-in-
place).

## ADRs

No new ADR — the async mechanism is in-process (no new deployable), the new module follows the
already-established one-module-per-capability repository pattern, and the real OpenAI adapter
follows the already-established provider-adapter pattern (EN005 Finnhub/Frankfurter).

## Risk-Register outcome (plan.md)

| Risk | Outcome |
|---|---|
| Per-task provider routing regresses EN006's own `diagnostic` task | **Avoided** — dedicated regression test cases in `AiInvocationPolicyTest`; full EN006 test suite re-run green before proceeding (Foundational A checkpoint). |
| A slow/stuck analysis starves Portfolio creation/read traffic | **Avoided** — dedicated `ThreadPoolTaskExecutor`, distinct from the HTTP request pool. |
| Concurrent manual re-analysis requests create two open records | **Avoided** — proven with a real two-thread race against PostgreSQL, not a mock (see above). |
| Another module's exception/identity type leaks into `portfolioanalysis`'s domain | **Found and fixed** — see "Implementation corrections" above; now ArchUnit-enforced. |
| OpenAI cost estimate needs live pricing data | **Accepted** — placeholder per-1k-token pricing (spec A1), documented, trivially replaceable. |
| JaCoCo coverage regresses with the new module's boilerplate JPA entities | **Avoided** — excluded with the same established rationale as `portfolio`/`financialinstrument`; real gaps (mapper, listener, domain guards, adapter error paths) closed with targeted tests instead of over-excluding. |

## OD outcomes

OD-1…OD-9 (plan.md) all implemented as planned; no deviation beyond the 4 AR-062 corrections above
(themselves resolving an internal inconsistency between the drafted OD-6 confinement intent and a
separately-drafted domain-model detail — not a change of intent).

## `product/` change

**One human-directed edit, already recorded as such**: FD005's own status `Draft`→`Approved`, §47
Q1–Q3 resolution recorded, §48 checkboxes checked, footer signed `jaruiz`, 2026-09-05 — per the
CLAUDE.md ambiguity-gate workflow (the same pattern used for EN006). No other `product/` document
was touched.

## What evidence shows acceptance criteria pass?

`quickstart.md` sections A–F, cross-referenced against `spec.md`'s AC-001…AC-015 and SC-001…SC-010 —
every criterion maps to a green result from the gate run above, including the three mandatory E2E
closure-gate scenarios (spec §45): E2E-001/002/003 all green.
