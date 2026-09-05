# Feature Specification: AI Portfolio Analysis (FD005)

**Feature Branch**: `FD005-ai-portfolio-analysis`

**Created**: 2026-09-05

**Status**: Draft (spec) — Feature **Approved** (§48 signed by jaruiz 2026-09-05)

**Input**: Feature Definition: "Automatically generate an AI-assisted analysis of a Portfolio after
it is created — diversification, key insights, and structured risks — computed from deterministic
Portfolio/valuation data via EN006's provider-neutral AI architecture (initial provider: OpenAI),
executed asynchronously so Portfolio creation never waits for or fails because of AI analysis. Every
analysis request is a new immutable record; only the latest is shown; the Investor can trigger a
fresh one on demand."

**Authoritative Source**:
`product/definition/features/FD005-ai-portfolio-analysis/FD005-ai-portfolio-analysis.md`
(**Status: Approved** — §48 signed by jaruiz 2026-09-05; §47 resolved the same day).

**Depends on**: FD001 (create Portfolio), FD003 (Portfolio detail), FD004 (Portfolio valuation —
the deterministic source of analysis context), EN006 (provider-neutral AI model integration — the
only path to any AI provider).

**Governing Architecture**: ADR-001 (single `core-service` deployable — unchanged), ADR-003
(Standard Spring Backend Architecture — a new sibling module `portfolioanalysis`, resolved
alongside `portfolio`/`financialinstrument`/`marketdata`/`ai`, per the module-boundary decision
below). **No new ADR** — the asynchronous execution mechanism (Spring `@Async` +
`ThreadPoolTaskExecutor`) is in-process, introduces no new deployable/container/dependency, and is
therefore a planning-level decision, not an architecture-topology one (unlike EN006's ADR-004).

---

## Clarifications

### Session 2026-09-05 (pre-specification — resolved on the Feature Definition before formalization)

FD005 was `Status: Draft` with §48 "Human Approval" entirely unchecked and unsigned. Per CLAUDE.md
§26 (Ambiguity Policy) and `product/governance/ai-development-policy.md` ("Human Approval
Boundaries" — architecture topology, persistence ownership, public API compatibility), formal
specification did not proceed until the product owner resolved the three most material open items
among the Feature Definition's 19 open technical decisions (§47). All three were accepted as
recommended:

- Q1 — Does FD005 build a real OpenAI adapter, or stay stub-only like EN006? → A: **A genuine
  `OpenAiModelAdapter`** (implementing EN006's `AiModelPort`), following the exact
  `FINNHUB_API_KEY` pattern from EN005: `OPENAI_API_KEY` supplied via environment/`.env`; blank ⇒
  the capability is disabled and analysis attempts fail to a controlled `FAILED` state (never
  blocking Portfolio creation). Tests use WireMock/deterministic stubs only; live OpenAI is
  opt-in-only, never CI. *(→ FR-006, FR-050…FR-054)*
- Q2 — How is the asynchronous background execution implemented? → A: **Spring `@Async` + a
  dedicated `ThreadPoolTaskExecutor` bean.** In-process, no new infrastructure/container/dependency,
  no ADR required. *(→ FR-005, FR-055…FR-058)*
- Q3 — Where does the new `PortfolioAnalysis` capability live architecturally? → A: **A new sibling
  module `portfolioanalysis`**, following the established one-module-per-bounded-capability pattern
  (`portfolio`/`financialinstrument`/`marketdata`/`ai`). It reads Portfolio/valuation data through
  `portfolio`'s published ports (AR-062 style) and consumes EN006's `GenerateAiUseCase` through its
  own `PortfolioAnalysisAiPort`. *(→ Key Entities, module structure)*

The remaining 16 (of 19) open technical decisions in the Feature Definition's §47 are explicitly
deferred to `/speckit-plan`/`/speckit-tasks` — the Feature Definition itself allows this ("may be
resolved during specification/planning"), and none of them affects the four invariants §47 itself
protects (asynchronous non-blocking execution, immutable analysis history, latest-only display,
provider-neutral EN006 usage).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — A new Portfolio is analyzed automatically, without delaying creation (Priority: P1)

As an Investor, when I create a Portfolio, I want My-FinAI-Manager to start analyzing it right away
— without making me wait — so the analysis is ready or in progress by the time I look at the
Portfolio.

**Why this priority**: The feature's core automatic-trigger behavior (FD005 §3, §6, §7; BR-001,
BR-002, BR-003). Without it, nothing else in FD005 has a starting point.

**Independent Test**: Create a Portfolio and measure the HTTP response time — it must not include
AI provider latency; a `PortfolioAnalysis` row with status `PENDING` (or already `RUNNING`) must
exist immediately after the response returns.

**Acceptance Scenarios**:

1. **Given** a Portfolio is successfully created, **When** creation completes, **Then** a new
   `PortfolioAnalysis` record is created/requested and processing begins asynchronously.
   *(AC-001)*
2. **Given** the configured AI provider takes several seconds, **When** the Portfolio is created,
   **Then** the create HTTP response returns without waiting for AI completion — its shape and
   timing are unaffected by FD005. *(AC-002; FD005 §36)*
3. **Given** a new analysis has just been requested, **When** background processing has not yet
   completed, **Then** the latest analysis for that Portfolio has status `PENDING` or `RUNNING`.
   *(AC-003)*
4. **Given** the AI provider fails or times out, **When** background processing terminates,
   **Then** the analysis is persisted as `FAILED` and the Portfolio itself remains valid, unchanged,
   and visible. *(AC-013; BR-003, BR-016)*

---

### User Story 2 — The Portfolio detail shows the latest completed analysis (Priority: P1)

As an Investor, I want to see my Portfolio's overall diversification, key insights, and risks in
plain language, grounded in my actual holdings, so I understand it beyond raw numbers.

**Why this priority**: The feature's user-visible payoff (FD005 §17, §31; BR-006).

**Independent Test**: With a `COMPLETED` analysis already persisted, open the Portfolio detail and
verify the AI Portfolio Analysis section renders that analysis's diversification, insights, and
risks with no additional wait.

**Acceptance Scenarios**:

1. **Given** a Portfolio has exactly one `COMPLETED` analysis, **When** the Investor opens Portfolio
   detail, **Then** the AI Portfolio Analysis section shows that analysis: Overall Diversification
   (a level + explanation), Key Insights (an ordered list), and Risks (each with severity, title,
   explanation). *(AC-005, AC-011)*
2. **Given** deterministic Portfolio data includes sector and Position allocations, **When** the
   analysis completes, **Then** its content is grounded in that data — no invented ticker, sector,
   price, or percentage appears. *(AC-011, AC-014; BR-011)*
3. **Given** the analysis includes a risk, **When** it is rendered, **Then** its severity is one of
   `HIGH` / `MEDIUM` / `LOW`, presented distinctly (e.g. visually ordered by severity). *(FD005 §20)*

---

### User Story 3 — Analysis-in-progress is explicit; a stale result is never shown as current (Priority: P1)

As an Investor, while a Portfolio analysis is running, I want to be told clearly that it's in
progress — not shown an old result as if it were current, and not left staring at nothing.

**Why this priority**: FD005 §32; BR-015; the closure gate explicitly forbids a silent/absent
in-progress state.

**Independent Test**: With the latest analysis `PENDING` or `RUNNING` (whether from auto-trigger or
manual re-analysis), open Portfolio detail and verify the in-progress message — never a previous
`COMPLETED` analysis presented as current.

**Acceptance Scenarios**:

1. **Given** the latest analysis is `PENDING` or `RUNNING`, **When** Portfolio detail is displayed,
   **Then** the UI shows "Your Portfolio is being analysed. The results will appear here when they
   are available." (or equivalent approved wording) and **no** numeric/insight content. *(AC-006)*
2. **Given** analysis A1 is `COMPLETED` and a newer A2 is `RUNNING`, **When** the Investor views
   Portfolio detail, **Then** the UI shows A2's in-progress state — **never** A1 presented as the
   current latest. *(AC-009)*
3. **Given** the UI is polling for the latest analysis, **When** A2 transitions to `COMPLETED`,
   **Then** the next poll renders A2's result and polling stops. *(AC-010; FD005 §34)*

---

### User Story 4 — The Investor can request a fresh analysis on demand (Priority: P1)

As an Investor, I want a "Run analysis again" action so I can get an updated read on my Portfolio
whenever I choose — without losing the previous one.

**Why this priority**: FD005 §12, §31; BR-007, BR-004, BR-005 — explicit product decision #9/#10.

**Independent Test**: With a `COMPLETED` analysis A1 displayed, click "Run analysis again"; verify a
new record A2 is created (`PENDING`), A1 is unchanged in storage, and the UI switches to A2's
in-progress state.

**Acceptance Scenarios**:

1. **Given** a completed analysis exists, **When** the Investor selects "Run analysis again",
   **Then** a new `PortfolioAnalysis` record is created and processed asynchronously — the existing
   record is never updated in place. *(AC-007; BR-004, BR-007)*
2. **Given** analysis A1 exists, **When** analysis A2 is requested, **Then** A1 remains stored,
   unchanged, and retrievable (even though not shown as "latest"). *(AC-008; BR-005)*
3. **Given** the latest analysis is already `PENDING` or `RUNNING`, **When** the Investor looks at
   Portfolio detail, **Then** "Run analysis again" is disabled/hidden — a duplicate concurrent
   request is not offered by the UI. *(BR-008; FD005 §13, §32)*
4. **Given** two re-analysis requests are nonetheless submitted concurrently for the same Portfolio
   (e.g. a race, a replayed request), **When** the backend receives the second one while the first
   is still `PENDING`/`RUNNING`, **Then** the backend rejects/no-ops the duplicate rather than
   creating a second concurrent record — the protection is **not** frontend-only. *(BR-008; FD005
   §13)*

---

### User Story 5 — A failed analysis is explicit, recoverable, and never exposes internals (Priority: P1)

As an Investor, if the AI analysis couldn't complete, I want to be told plainly and given a way to
try again — never shown a raw error, a stack trace, or a fabricated result.

**Why this priority**: FD005 §33; BR-016; the closure gate explicitly forbids fabricated missing
facts and exposed internals.

**Independent Test**: Force the AI provider boundary to fail, request an analysis, and verify the
Portfolio detail shows the controlled failed state with a working "Run analysis again" — no
provider payload, stack trace, or credential in the response or the UI.

**Acceptance Scenarios**:

1. **Given** the AI provider fails (timeout, error, guardrail rejection, invalid structured
   output), **When** background processing terminates, **Then** the analysis is persisted `FAILED`
   with a normalized failure reason — never a raw provider error. *(AC-013)*
2. **Given** the latest analysis is `FAILED`, **When** Portfolio detail is displayed, **Then** the
   UI shows "Portfolio analysis is currently unavailable." (or equivalent) with a working "Run
   analysis again" — no provider error payload, stack trace, authentication detail, or internal
   exception is ever shown. *(FD005 §33)*
3. **Given** required deterministic information is unavailable (no valuation, or a valuation with
   too little data), **When** analysis is attempted, **Then** the model is never asked to invent
   the missing facts — the attempt fails explicitly (or produces an explicit insufficient-data
   result) rather than fabricating Portfolio data. *(AC-014; BR-011; FD005 §15)*

---

### User Story 6 — OpenAI is used only through EN006's provider-neutral architecture (Priority: P1)

As a maintainer, I want FD005's business logic to know nothing about OpenAI specifically, so a
future provider change requires no change to `portfolioanalysis` business code.

**Why this priority**: FD005 §5; BR-009; AC-012; the closure gate explicitly forbids direct OpenAI
coupling in business/core code.

**Independent Test**: Grep `portfolioanalysis.domain`/`portfolioanalysis.business` for any OpenAI
SDK type, DTO, or HTTP contract — must find none. Architecture test enforces this automatically.

**Acceptance Scenarios**:

1. **Given** `portfolioanalysis.business.PortfolioAnalysisUseCase`, **When** it requests an
   analysis, **Then** it calls only `PortfolioAnalysisAiPort` (this feature's own port), never
   EN006's `AiModelPort` or any OpenAI type directly. *(AC-012; FD005 §5)*
2. **Given** the configured AI provider changes (a future adapter), **When** the change is made,
   **Then** no `portfolioanalysis.domain`/`.business` file requires modification. *(AC-012)*

---

### User Story 7 — Deterministic tests; CI never depends on live OpenAI (Priority: P2)

As a maintainer, I can run the full test suite and the E2E suite with **no** outbound call to
OpenAI: the provider boundary is stubbed/controlled at every level.

**Why this priority**: FD005 §40, §41; the closure gate explicitly forbids requiring live OpenAI in
CI.

**Independent Test**: `./mvnw -B clean verify` and `./e2e.sh` both pass with no network reachability
to `api.openai.com`.

**Acceptance Scenarios**:

1. **Given** CI, **When** the suite runs, **Then** no test reaches `api.openai.com` — the
   `OpenAiModelAdapter` is tested with WireMock; E2E uses a controlled provider-boundary stub.
   *(§40, §41)*
2. **Given** the full gate set, **When** it runs, **Then** FD001–FD004 / EN004–EN006 suites and
   E2Es stay green — FD005 is additive. *(closure gate)*

### Edge Cases

- **No valuation yet / valuation `FAILED`** — the analysis attempt fails explicitly (or produces an
  explicit insufficient-data `FAILED` result); the model is never asked to work from nothing (FD005
  §15).
- **Valuation `PARTIAL`** — analysis may proceed using only the explicitly available facts, and the
  context/output must make clear the valuation was partial (FD005 §15).
- **Portfolio deleted or unreachable mid-analysis** *(FD001 has no delete — noted for completeness;
  not reachable today)* — out of scope; no delete capability exists yet.
- **Concurrent manual re-analysis race** — see US4 AS4: the backend enforces the no-duplicate rule,
  not just the frontend.
- **Guardrail rejection** (EN006 input/output guardrail) — treated the same as any other provider
  failure: `FAILED`, normalized reason, no raw content (FD005 §25).
- **Structured output fails schema validation** — never persisted as `COMPLETED`; treated as
  `FAILED` (FD005 §22, BR-012).
- **Frontend navigates away / component disposed while polling** — polling must stop; no leaked
  timers/requests (FD005 §34).
- **`OPENAI_API_KEY` unset** — mirrors EN005/Finnhub: the capability is disabled, every analysis
  attempt fails to `FAILED` with a normalized reason, and every other capability keeps working.

---

## Requirements *(mandatory)*

> Each FR traces to a Feature Definition §/BR/AC. All three pre-specification clarifications
> (Q1–Q3) are resolved and folded in below.

### Automatic trigger & asynchronous execution

- **FR-001**: A successful Portfolio creation MUST request a new Portfolio Analysis. *(§3, §6;
  BR-001)*
- **FR-002**: The Portfolio Analysis request MUST be created only after the Portfolio has been
  successfully persisted — never before, never if creation itself fails. *(§6)*
- **FR-003**: The Portfolio creation HTTP response MUST NOT wait for AI provider invocation, model
  inference, guardrails, or analysis persistence to complete. *(§3, §6, §36; AC-002)*
- **FR-004**: A failure or delay in AI analysis MUST NOT roll back Portfolio creation, prevent the
  Portfolio from being returned to the frontend, or invalidate an already-persisted Portfolio.
  *(§3; BR-003)*
- **FR-005**: Analysis execution MUST run asynchronously via Spring `@Async` on a dedicated
  `ThreadPoolTaskExecutor` (resolved Q2) — not on the request thread, not via an external broker.
  *(§7; BR-002)*

### Analysis record lifecycle

- **FR-006**: Every automatic or manual analysis request MUST create a **new** `PortfolioAnalysis`
  record — never update/reuse a previous one. *(§8, §12; BR-004)*
- **FR-007**: A `PortfolioAnalysis` MUST have status `PENDING`, `RUNNING`, `COMPLETED`, or `FAILED`,
  transitioning `PENDING → RUNNING → {COMPLETED | FAILED}`. *(§8)*
- **FR-008**: A previous `PortfolioAnalysis` record MUST NEVER be overwritten, deleted, or mutated
  by a later request — all analyses for a Portfolio remain stored. *(§10, §12; BR-005, AC-008)*
- **FR-009**: Every `PortfolioAnalysis` MUST record its requested timestamp; a `COMPLETED` or
  `FAILED` record MUST additionally record its completion timestamp. *(§9, §28; BR-014)*
- **FR-010**: Every `PortfolioAnalysis` MUST record which trigger created it (automatic vs. manual)
  for traceability (`createdByTrigger`). *(§9)*

### Manual re-analysis & duplicate prevention

- **FR-011**: The Investor MUST be able to trigger a new analysis on demand via a
  "Run analysis again" action (or equivalently worded UX) in Portfolio detail. *(§12, §31; BR-007)*
- **FR-012**: A manual re-analysis request MUST create a new record exactly like the automatic
  trigger (FR-006) — the same lifecycle, the same persistence rules. *(§12)*
- **FR-013**: The backend MUST reject or no-op a new analysis request for a Portfolio whose latest
  analysis is already `PENDING` or `RUNNING` — duplicate-request protection MUST NOT rely solely on
  the frontend disabling the button. *(§13; BR-008)*
- **FR-014**: The frontend SHOULD disable/hide the "Run analysis again" action while the latest
  analysis is `PENDING`/`RUNNING`, as a UX complement to FR-013 — not a substitute for it. *(§13,
  §32)*

### Latest-analysis semantics

- **FR-015**: The system MUST expose exactly one "latest" Portfolio Analysis per Portfolio, ordered
  by requested time (or an equivalent deterministic ordering) — the most recently requested record,
  regardless of its status. *(§11; BR-006)*
- **FR-016**: When the latest analysis is `PENDING`/`RUNNING`, Portfolio detail MUST show the
  in-progress state — **never** a previous `COMPLETED` analysis presented as current. *(§11, §32;
  AC-009)*
- **FR-017**: When the latest analysis is `COMPLETED`, Portfolio detail MUST show its content
  (diversification, insights, risks). *(§11, §31; AC-005)*
- **FR-018**: When the latest analysis is `FAILED`, Portfolio detail MUST show the controlled
  failed state and offer re-analysis. *(§11, §33)*
- **FR-019**: Historical analyses (superseded by a newer one) MUST remain queryable in principle
  (preserved in storage) but MUST NOT be exposed by any FD005 UI or endpoint — historical browsing
  is explicitly deferred to a future feature. *(§10, §21; out of scope)*

### Deterministic context & AI provider independence

- **FR-020**: The AI context MUST be built by a dedicated context builder
  (`PortfolioAnalysisContextBuilder`) from the Portfolio and its latest `PortfolioValuation`
  (FD004) — never by serializing arbitrary domain/persistence entities. *(§14, §27; BR-010)*
- **FR-021**: The context MAY include: Portfolio name, latest valuation status, total value,
  Position tickers/weights/values, sector classification/weights, currency exposure, and valuation
  timestamp — nothing else from the Portfolio domain. *(§14)*
- **FR-022**: The AI provider MUST NOT be asked to query or calculate current market prices,
  Portfolio valuation, position weights, or sector percentages — those remain exclusively
  deterministic (FD004/EN005). *(§14, §16, §22 BR-017/18/22 of FD004; BR-010, BR-017 here)*
- **FR-023**: If the latest valuation is `PARTIAL`, analysis MAY proceed using only the explicitly
  available facts, and the context MUST make clear the valuation is partial. *(§15)*
- **FR-024**: If the latest valuation is `FAILED`, absent, or does not contain enough deterministic
  information for meaningful analysis, the analysis MUST NOT proceed by inventing Portfolio facts —
  the attempt MUST fail explicitly (`FAILED`) or produce an explicit insufficient-data outcome.
  *(§15; AC-014)*
- **FR-025**: `portfolioanalysis.domain` and `portfolioanalysis.business` MUST NOT reference OpenAI
  SDK types, DTOs, authentication, model-specific response classes, or HTTP contracts — only
  `PortfolioAnalysisAiPort` (this feature's own port, backed by EN006's `GenerateAiUseCase`).
  *(§5; BR-009; AC-012)*
- **FR-026**: Changing the configured AI provider in the future MUST NOT require changing
  `portfolioanalysis` business logic. *(§5; AC-012)*

### Structured analysis content

- **FR-027**: A `COMPLETED` analysis MUST contain, at minimum: an Overall Diversification level +
  explanation, an ordered list of Key Insights, and a list of structured Risks. *(§17; BR-012,
  AC-011)*
- **FR-028**: Overall Diversification MUST use a small controlled vocabulary: `LOW` / `MODERATE` /
  `HIGH`, with a concise explanation grounded only in supplied Portfolio facts. *(§18)*
- **FR-029**: Each Key Insight MUST be grounded in supplied Portfolio context — the model MUST NOT
  invent market forecasts, company news, future returns, missing allocations, or external facts.
  *(§19; AC-014)*
- **FR-030**: Each Risk MUST carry a type, a severity (`HIGH` / `MEDIUM` / `LOW`), a title, and an
  explanation; where practical, it SHOULD reference deterministic evidence (e.g. the sector and its
  weight). *(§20, §21)*
- **FR-031**: Risk types include at minimum: `SECTOR_CONCENTRATION`, `POSITION_CONCENTRATION`,
  `CURRENCY_CONCENTRATION`, `LOW_DIVERSIFICATION`, `MISSING_SECTOR_EXPOSURE`, `OTHER`. *(§20)*
- **FR-032**: The model's response MUST be validated against the approved structured-output schema
  (EN006 FR-017–019) before being persisted as `COMPLETED`; a malformed or non-conforming response
  MUST NOT be treated as a successful analysis — it becomes `FAILED`. *(§22; BR-012)*

### Prompting, guardrails, sensitive data, tokens (via EN006)

- **FR-033**: The analysis prompt MUST layer EN006's global system prompt with
  `portfolio-analysis`-specific task instructions, composed by EN006's `PromptService` — FD005 does
  not build its own prompt-composition mechanism. *(§23)*
- **FR-034**: The task instructions MUST direct the model to: use only supplied facts; never
  fabricate prices/returns/news/external information; never perform deterministic valuation
  calculations; identify diversification and concentration; produce meaningful key insights;
  classify risks; state uncertainty when data is incomplete; return only the required structured
  format. *(§23)*
- **FR-035**: The Portfolio Analysis prompt MUST have a stable `promptId` (`portfolio-analysis`) and
  an incrementing `promptVersion`; every persisted analysis MUST record the exact version used;
  changing the prompt text MUST create a new version, never mutate an existing one in place. *(§24;
  BR-013)*
- **FR-036**: FD005 MUST use EN006's input and output guardrails: context within configured token
  limits; no unnecessary sensitive information sent; output matches the structured schema;
  detectable fabricated/prohibited financial-action language rejected. A guardrail rejection MUST
  produce a controlled `FAILED` outcome — never a partial or unvalidated result. *(§25; BR-012)*
- **FR-037**: The analysis context MUST NOT include user name, email, authentication details, or
  unrelated personal information; the Portfolio identifier itself SHOULD be omitted from the model
  context unless technically required (a correlation id suffices for traceability). *(§26)*
- **FR-038**: The context builder MUST prioritize allocation/weights/sector/currency/valuation-status
  data and MUST NOT serialize arbitrary database entities; it MUST use EN006's max-input/context,
  max-output, and max-total-token controls, and MUST apply an explicit, deterministic compaction
  strategy if the context would otherwise exceed the configured limit (never silent truncation that
  changes meaning). *(§27)*

### Traceability, cost/usage metadata, observability

- **FR-039**: Every analysis MUST persist provider, model, `promptId`, `promptVersion` when the
  invocation reached the provider call (i.e. for every non-`PENDING` terminal/interim state where
  those are known). *(§9, §28; BR-013)*
- **FR-040**: Every analysis SHOULD persist input/output/total token counts and estimated cost when
  available from EN006's `AiUsage` — this metadata is not displayed to the Investor in FD005 but
  supports EN006's Grafana/Prometheus observability. *(§28)*
- **FR-041**: The background operation MUST be observable through EN006's telemetry with safe
  metadata (`ai.task=portfolio-analysis`, provider, model, prompt id/version, token counts, latency,
  success, guardrail result) — raw Portfolio context, prompts, and completions MUST NOT be
  logged/traced by default. *(§29, §30)*
- **FR-042**: At minimum, from telemetry/logs alone, a developer MUST be able to determine: was
  analysis requested; did background execution start; which provider/model was used; how long it
  took; how many tokens were used; did guardrails pass; was the result persisted; why it failed —
  without any sensitive business content. *(§30)*

### Portfolio detail UX

- **FR-043**: Portfolio detail MUST be extended (additively) with an "AI Portfolio Analysis"
  section showing only the **latest** analysis. *(§31; BR-006)*
- **FR-044**: The completed state MUST show: Overall Diversification (level + explanation), Key
  Insights (bulleted, ordered), Risks (severity + title + explanation, ordered by severity
  descending), and the analysis timestamp. *(§31; AC-005, AC-011)*
- **FR-045**: The in-progress state MUST show an explicit "being analysed" message and MUST NOT
  show numeric/insight/risk content from any other analysis. *(§32; AC-006, AC-009)*
- **FR-046**: The failed state MUST show a controlled unavailable message and a working
  "Run analysis again" action — MUST NOT show a provider error payload, a stack trace, an
  authentication detail, or an internal exception. *(§33)*
- **FR-047**: The frontend MUST discover analysis-state changes via polling `GET
  .../analysis/latest` at a configured interval; polling MUST stop on `COMPLETED`, `FAILED`, or
  component/page disposal — it MUST NOT poll indefinitely in the background after navigation away.
  *(§34)*

### API

- **FR-048**: `GET /api/portfolios/{portfolioId}/analysis/latest` MUST return the latest Portfolio
  Analysis for that Portfolio (its status and, when `COMPLETED`, its full content); a Portfolio with
  no analysis yet MUST return an explicit "no analysis" outcome — never a fabricated `PENDING` row
  that doesn't exist, and never a `404` for a Portfolio that legitimately has none yet. Unknown
  Portfolio id → `404` `/problems/portfolio-not-found` (matches FD003 precedent). *(§35)*
- **FR-049**: `POST /api/portfolios/{portfolioId}/analysis` MUST create a new analysis record and
  submit it for background execution, returning promptly (not waiting for AI completion) with an
  accepted/requested representation (`analysisId`, `status`, `requestedAt`). It MUST reject the
  request (without creating a new record) when the latest analysis for that Portfolio is already
  `PENDING`/`RUNNING` (FR-013) — with a clear, machine-readable conflict response. Unknown Portfolio
  id → `404`. *(§35, §36)*
- **FR-050**: Neither endpoint MUST leak persistence models, OpenAI DTOs, or internal exception
  detail into its response body. *(§35; constitution)*

### Provider adapter (OpenAI, via EN006 — resolved Q1)

- **FR-051**: `portfolioanalysis.infrastructure` (or a peer provider package under `ai.infrastructure`
  — a planning-level placement decision) MUST implement a real `OpenAiModelAdapter` satisfying
  EN006's `AiModelPort`, configured via `OPENAI_API_KEY` (environment/`.env`, never committed) and
  an overridable base URL — mirroring EN005's `FINNHUB_API_KEY`/`FINNHUB_BASE_URL` pattern exactly.
  *(§5; resolved Q1)*
- **FR-052**: A blank/unset `OPENAI_API_KEY` MUST disable the OpenAI capability: the adapter reports
  a provider-neutral "not configured" failure (EN006's error model), every analysis attempt fails to
  `FAILED` with a normalized reason, and every other platform capability keeps working. *(resolved
  Q1; mirrors EN005 §"disabled" behavior)*
- **FR-053**: Automated tests (unit and integration) MUST NOT call live OpenAI — the adapter is
  tested with WireMock/deterministic HTTP stubs. An opt-in-only smoke test MAY validate a real key
  locally, excluded from normal CI, bounded by EN006's token/cost limits. *(§40; resolved Q1)*
- **FR-054**: The containerized E2E environment MUST stub the OpenAI HTTP boundary (a deterministic
  fixture returning the FD005 §42 example content) — E2E MUST NOT require live OpenAI. *(§41;
  resolved Q1)*

### Concurrency & module boundary (resolved Q2/Q3)

- **FR-055**: Analysis execution MUST run on a dedicated `ThreadPoolTaskExecutor` bean, distinct
  from the HTTP request-handling pool, so a slow/stuck analysis cannot starve Portfolio
  creation/read traffic. *(resolved Q2)*
- **FR-056**: The transaction that creates the `PortfolioAnalysis` `PENDING` row MUST commit before
  the asynchronous analysis task starts (so a concurrently-arriving `GET latest` never sees a
  request that "doesn't exist yet"). *(§7 planning detail; resolved Q2)*
- **FR-057**: A failure inside the asynchronous task (including an unexpected/uncaught exception)
  MUST be caught and persisted as `FAILED` — an async task MUST NEVER silently disappear leaving a
  record stuck at `PENDING`/`RUNNING` forever under normal operation. *(§8, BR-016; resolved Q2)*
- **FR-058**: The new capability MUST live in its own sibling module `portfolioanalysis`
  (`domain`/`business`/`infrastructure`, ADR-003 layout) — it MUST read Portfolio/valuation data
  only through `portfolio`'s published domain ports (AR-062 style, from
  `portfolioanalysis.infrastructure` only) and MUST consume EN006 only through its own
  `PortfolioAnalysisAiPort` (backed by `ai.business.GenerateAiUseCase`). *(resolved Q3)*

### Key Entities *(include if feature involves data)*

- **PortfolioAnalysis** — one row per requested analysis (automatic or manual), immutable once
  terminal. Fields: `id`, `portfolioId`, `status` (`PENDING`/`RUNNING`/`COMPLETED`/`FAILED`),
  `requestedAt`, `startedAt?`, `completedAt?`, `summary?` (the diversification explanation),
  `overallDiversification?` (`LOW`/`MODERATE`/`HIGH`), `provider?`, `model?`, `promptId?`,
  `promptVersion?`, `inputTokens?`, `outputTokens?`, `totalTokens?`, `estimatedCost?`,
  `failureReasonCode?`, `createdByTrigger` (`AUTOMATIC`/`MANUAL`). Exactly one row per Portfolio is
  ever "the latest" (by `requestedAt` or an equivalent monotonic ordering key).
- **PortfolioAnalysisInsight** — belongs to one `PortfolioAnalysis`. Fields: `id`,
  `portfolioAnalysisId`, `type`, `message`, `order` (rendering order).
- **PortfolioAnalysisRisk** — belongs to one `PortfolioAnalysis`. Fields: `id`,
  `portfolioAnalysisId`, `type` (§20 vocabulary), `severity` (`HIGH`/`MEDIUM`/`LOW`), `title`,
  `explanation`, `order`.

*(No change to `Portfolio` / `Position` / `PortfolioValuation` / EN004 `FinancialInstrument`
identity — FD005 reads them, it does not redefine them.)*

---

## Success Criteria *(mandatory)*

- **SC-001**: Creating a Portfolio returns in the same time envelope as before FD005 (FD001/FD004
  timing unaffected) — verified by a test asserting the create endpoint's response does not await
  the AI call.
- **SC-002**: Immediately after a successful Portfolio creation, `GET .../analysis/latest` returns a
  `PENDING` (or already `RUNNING`) record for that Portfolio — 100% of the time in automated tests.
- **SC-003**: A completed analysis always contains a diversification level, at least one key
  insight, and a list of risks with valid severities — verified by schema validation before
  persistence and by an acceptance test.
- **SC-004**: Requesting a second analysis while the latest is `PENDING`/`RUNNING` is rejected by
  the backend 100% of the time in a concurrency test — never creates a second concurrent record.
- **SC-005**: A previous analysis is never mutated by a later request — verified by an integration
  test asserting byte-for-byte persistence stability of A1 after A2 is requested/completed.
- **SC-006**: `./mvnw -B clean verify` passes offline — unit + integration (Testcontainers
  PostgreSQL) + contract + architecture tests + ≥ 90% line & branch coverage; zero OpenAI SDK type
  in `portfolioanalysis.domain`/`.business` (ArchUnit).
- **SC-007**: `ng test` and `./e2e.sh` pass; E2E-001 (automatic analysis), E2E-002 (manual
  re-analysis), E2E-003 (provider failure) are all green, fully offline (stubbed OpenAI boundary).
- **SC-008**: A repository/log scan finds zero occurrences of the OpenAI key, a raw prompt body, or
  a raw completion body in logs, traces, metric labels, or any API response.
- **SC-009**: `git diff` scope review shows: one new Flyway migration (the three FD005 tables only);
  `openapi.yaml` gains exactly the two FD005 endpoints; no change to FD001/FD003/FD004 request/response
  contracts; no unapproved new dependency beyond an HTTP client for the OpenAI adapter (reusing
  Spring's existing `RestClient`, consistent with EN005's pattern — no new library).
- **SC-010**: 100% of FD005's BR-001…016 and AC-001…014 have associated executable or E2E evidence.

## Assumptions

> The feature is **approved** (§48 signed 2026-09-05). Assumptions below fill only non-material,
> planning-level gaps within its constraints (§47 items #3-6, #8-19), each reversible via
> configuration/implementation refinement, none changing product behavior.

- **A1 — OpenAI model**: a small, cost-efficient chat-completion model with structured-output
  support (exact model id is a planning/config detail — `openai.model` property, analogous to
  `ai.default-model`); default value chosen during `/speckit-plan`.
- **A2 — HTTP client**: `OpenAiModelAdapter` reuses Spring's `RestClient` (as EN005's Finnhub/
  Frankfurter adapters do) — no new HTTP library.
- **A3 — Executor sizing**: `ThreadPoolTaskExecutor` core/max pool size and queue capacity are
  planning-level defaults (small, e.g. 2–4 threads — this is a low-volume, personal-scale
  application), tunable via configuration without a code change.
- **A4 — Polling interval**: a short, fixed interval (e.g. 2–3s) with a maximum poll duration/backoff
  after which the UI shows a "taking longer than expected" hint but keeps the last known state —
  exact values are a frontend/UX planning detail (§47 items #8-9).
- **A5 — Persistence schema layout**: three tables (`portfolio_analysis`,
  `portfolio_analysis_insight`, `portfolio_analysis_risk`), owned by `portfolioanalysis`, new Flyway
  migration; FD001/FD003/FD004/EN004 tables untouched (§47 item #15).
- **A6 — Concurrency control mechanism**: a database-level uniqueness/locking strategy (e.g. a
  partial unique index or a `SELECT ... FOR UPDATE` check on "does an open request already exist")
  is a planning-level implementation detail satisfying FR-013 (§47 item #6).
- **A7 — Insufficient-data behavior**: when valuation is `FAILED`/absent/insufficient, the analysis
  request itself is still created (so the Investor sees explicit feedback) but the background task
  immediately resolves it to `FAILED` with a normalized "insufficient data" reason — no provider
  call is made in that case (§47 items #17-18).
- **A8 — Button wording**: "Run analysis again" (the Feature Definition's own suggested wording,
  §12) is adopted as-is (§47 item #19).
- **A9 — Risk/insight cardinality**: no hard cap is enforced by the schema; the prompt instructs the
  model toward a small, meaningful set (e.g. 2–5 insights, 1–4 risks) — enforced by prompt
  instructions and reviewed in the RED-first tests, not a persisted constraint (§47 items #13-14).

## Dependencies

- **Feature FD005** — the authoritative source; §48 signed 2026-09-05.
- **FD001** — Portfolio creation; the automatic-trigger point (`PortfolioCreatedEvent` — the same
  event FD004 already listens to synchronously; FD005 adds an independent asynchronous listener/
  trigger, not a change to FD001 itself).
- **FD003** — Portfolio detail; the AI Portfolio Analysis section extends it additively.
- **FD004** — Portfolio valuation; the deterministic source of analysis context
  (`PortfolioValuationQueryUseCase`, read through `portfolio`'s published port).
- **EN006** — provider-neutral AI model integration; FD005 is EN006's **first real consumer** —
  `PortfolioAnalysisAiPort` calls `ai.business.GenerateAiUseCase`, and FD005 supplies EN006's
  **first real external provider adapter** (`OpenAiModelAdapter implements ai.domain.ports.AiModelPort`).
- **ADR-001 / ADR-003** — governing architecture; unchanged. No new ADR (resolved Q2).
- **OpenAI public API** (`https://api.openai.com`) — a new outbound dependency (infrastructure
  only), approved by `technology-policy.md` ("LLM Provider | OpenAI | ALLOWED") and by the Feature
  Definition's own explicit choice (§5, Decision #14).
- **Governance**: `.specify/memory/constitution.md`; `product/architecture/{architecture,
  architecture-rules,technology-policy}.md`; `product/engineering/{development-rules,
  testing-strategy,definition-of-done}.md`; `product/governance/ai-development-policy.md`.

## Out of Scope

- Historical analysis browsing UI / comparison between analyses (§10, §21 — a future Feature
  Definition).
- AI chat/conversation, "Ask My Portfolio" (a future capability).
- AI-generated buy/sell execution, automatic trading, Portfolio rebalancing, Stop-Loss
  recommendations.
- News retrieval, RAG, external web search by the model.
- Price discovery or deterministic-value calculation by AI (owned exclusively by FD004/EN005).
- Scheduled/recurring Portfolio analysis (only automatic-on-create and manual-on-demand).
- Push/email notifications when analysis finishes (polling only).
- User selection of AI provider/model; automatic provider fallback.
- WebSockets/SSE for the frontend refresh (polling only, per the Feature Definition).
