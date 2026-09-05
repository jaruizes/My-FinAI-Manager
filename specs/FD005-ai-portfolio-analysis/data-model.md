# Phase 1 — Data Model: FD005 (AI Portfolio Analysis)

**Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Spec**: [spec.md](./spec.md)

All monetary/weight figures reused from FD004 stay `BigDecimal` (as text in the domain model,
matching FD004's own convention). No new Portfolio/Position/Valuation identity — FD005 reads FD004
via its own ACL (research D6), never redefines it.

---

## 1. Schema — `V5__portfolio_analysis.sql` (owned by `portfolioanalysis`)

```sql
CREATE TABLE portfolio_analysis (
    id                       UUID PRIMARY KEY,
    portfolio_id             UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    status                   VARCHAR(20) NOT NULL,   -- PENDING | RUNNING | COMPLETED | FAILED
    requested_at             TIMESTAMPTZ NOT NULL,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    summary                  TEXT,                    -- overall-diversification explanation
    overall_diversification  VARCHAR(20),             -- LOW | MODERATE | HIGH
    provider                 VARCHAR(50),
    model                    VARCHAR(100),
    prompt_id                VARCHAR(100),
    prompt_version           VARCHAR(20),
    input_tokens             INTEGER,
    output_tokens            INTEGER,
    total_tokens             INTEGER,
    estimated_cost           NUMERIC,
    failure_reason_code      VARCHAR(50),
    created_by_trigger       VARCHAR(20) NOT NULL     -- AUTOMATIC | MANUAL
);

-- FR-015: fast "latest for this Portfolio" lookup.
CREATE INDEX portfolio_analysis_portfolio_id_requested_at_idx
    ON portfolio_analysis (portfolio_id, requested_at DESC);

-- FR-013 / research D4: at most one OPEN (not-yet-terminal) request per Portfolio, enforced by
-- the database itself — not just application logic.
CREATE UNIQUE INDEX portfolio_analysis_one_open_per_portfolio_uk
    ON portfolio_analysis (portfolio_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE portfolio_analysis_insight (
    id                     UUID PRIMARY KEY,
    portfolio_analysis_id  UUID NOT NULL REFERENCES portfolio_analysis(id) ON DELETE CASCADE,
    type                   VARCHAR(50) NOT NULL,
    message                TEXT NOT NULL,
    display_order          INTEGER NOT NULL
);
CREATE INDEX portfolio_analysis_insight_analysis_id_idx
    ON portfolio_analysis_insight (portfolio_analysis_id);

CREATE TABLE portfolio_analysis_risk (
    id                     UUID PRIMARY KEY,
    portfolio_analysis_id  UUID NOT NULL REFERENCES portfolio_analysis(id) ON DELETE CASCADE,
    type                   VARCHAR(50) NOT NULL,
    severity               VARCHAR(20) NOT NULL,     -- HIGH | MEDIUM | LOW
    title                  VARCHAR(200) NOT NULL,
    explanation            TEXT NOT NULL,
    display_order          INTEGER NOT NULL
);
CREATE INDEX portfolio_analysis_risk_analysis_id_idx
    ON portfolio_analysis_risk (portfolio_analysis_id);
```

No FK from `portfolio_analysis` back into any FD004 table — a `PortfolioAnalysis` references FD004
data only transiently (at generation time, via the context builder), never by a persisted foreign
key (FD004's valuation snapshot is mutable/replaced-in-place — FD005 must not pin a dangling
reference to it).

---

## 2. Domain model (`portfolioanalysis.domain.model`)

| Model | Fields | Notes |
|---|---|---|
| `PortfolioAnalysis` | `id (AnalysisId)`, `portfolioId (UUID)`, `status`, `requestedAt`, `startedAt: Optional<Instant>`, `completedAt: Optional<Instant>`, `summary: Optional<String>`, `overallDiversification: Optional<DiversificationLevel>`, `insights: List<Insight>`, `risks: List<Risk>`, `provider/model/promptId/promptVersion: Optional<String>`, `inputTokens/outputTokens/totalTokens: Optional<Integer>`, `estimatedCost: Optional<BigDecimal>`, `failureReasonCode: Optional<FailureReason>`, `createdByTrigger` | Wither methods `withRunning(Instant)`, `withCompleted(PortfolioAnalysisResult, Instant)`, `withFailed(FailureReason, Instant)` — each returns a **new** instance; the persistence adapter updates the **same row** by id. |

> **Implementation correction (AR-062, applied during T025–T030 — a non-material wiring fix, no
> product/behavior change):** the original draft above reused `portfolio.domain.model.PortfolioId`
> as `PortfolioAnalysis.portfolioId`, and had `withCompleted` accept `ai.domain.model.AiUsage` /
> `PromptReference` directly. Both would let another module's type reach `portfolioanalysis.domain`,
> which AR-062 forbids ("must not let the other module's types reach its own domain or business
> packages") — the same rule already applied to `portfolio.domain.ports.MarketDataGateway`, whose
> javadoc states "Arguments are primitive Strings so no marketdata enum or value object reaches
> portfolio.domain." Corrected to match that precedent: `portfolioId` is a plain `java.util.UUID`
> everywhere in `portfolioanalysis` (domain, business, ports, REST) — only the ACL adapter
> (`PortfolioContextGatewayAdapter`, `portfolioanalysis.infrastructure.portfolio`) converts it to/from
> `portfolio.domain.model.PortfolioId` when calling `PortfolioQueryUseCase`/`PortfolioValuationQueryUseCase`.
> Likewise, `PortfolioAnalysisResult` (§2 below) itself now carries the provider/model/promptId/
> promptVersion/token/cost fields the AI adapter produces, so `withCompleted` needs only
> `(PortfolioAnalysisResult, Instant)` — no `ai.*` type ever reaches `portfolioanalysis.domain` either.
| `PortfolioAnalysis.Insight` | `type: String`, `message: String`, `order: int` | value object, nested |
| `PortfolioAnalysis.Risk` | `type: RiskType`, `severity: RiskSeverity`, `title: String`, `explanation: String`, `order: int` | value object, nested |
| `AnalysisStatus` | enum `PENDING, RUNNING, COMPLETED, FAILED` | |
| `CreationTrigger` | enum `AUTOMATIC, MANUAL` | |
| `DiversificationLevel` | enum `LOW, MODERATE, HIGH` | FR-028 |
| `RiskSeverity` | enum `HIGH, MEDIUM, LOW` | FR-030 |
| `RiskType` | enum `SECTOR_CONCENTRATION, POSITION_CONCENTRATION, CURRENCY_CONCENTRATION, LOW_DIVERSIFICATION, MISSING_SECTOR_EXPOSURE, OTHER` | FR-031 |
| `FailureReason` | enum `NOT_CONFIGURED, PROVIDER_UNAVAILABLE, GUARDRAIL_REJECTED, INVALID_OUTPUT, INSUFFICIENT_DATA, TIMEOUT, UNKNOWN` | normalized, never a raw provider message |
| `PortfolioContextSnapshot` | `portfolioName`, `valuationStatus` (local enum mirroring FD004's, `COMPLETED\|PARTIAL\|FAILED\|PENDING\|ABSENT`), `totalValueEur/Usd: Optional<BigDecimal>`, `positions: List<PositionSnapshot>`, `sectors: List<SectorSnapshot>`, `valuedAt: Optional<Instant>` | from `PortfolioContextGateway` — `portfolioanalysis`'s own vocabulary, not FD004's types |
| `PortfolioContextSnapshot.PositionSnapshot` | `ticker`, `weight: Optional<BigDecimal>`, `valueEur: Optional<BigDecimal>`, `sector: Optional<String>`, `currency` | |
| `PortfolioContextSnapshot.SectorSnapshot` | `sector`, `weight: BigDecimal` | |
| `PortfolioAnalysisContext` | `text: String` (the compact, deterministic prompt payload — research D6), `sufficient: boolean` | `sufficient=false` ⇒ the worker resolves straight to `FAILED(INSUFFICIENT_DATA)`, no provider call |
| `PortfolioAnalysisResult` | `overallDiversification: DiversificationLevel`, `explanation: String`, `insights: List<Insight>`, `risks: List<Risk>`, `provider: String`, `model: String`, `promptId: String`, `promptVersion: String`, `inputTokens/outputTokens/totalTokens: int`, `estimatedCost: BigDecimal` | the AI-produced, schema-validated content **plus** the invocation metadata the persisted row needs (AR-062 correction above) — pre-persistence |

## 3. Ports (`portfolioanalysis.domain.ports`)

| Port | Signature | Notes |
|---|---|---|
| `PortfolioAnalysisRepository` | `PortfolioAnalysis save(PortfolioAnalysis)`; `Optional<PortfolioAnalysis> findLatestByPortfolioId(UUID)`; `Optional<PortfolioAnalysis> findById(AnalysisId)` | throws `AnalysisAlreadyInProgressException` on a duplicate-open-request constraint violation (translated by the adapter) |
| `PortfolioContextGateway` | `PortfolioContextSnapshot fetch(UUID portfolioId)` | ACL — implemented by the **sole** adapter touching `portfolio.*`; converts to `portfolio.domain.model.PortfolioId` internally (AR-062 correction above) |
| `PortfolioAnalysisAiPort` | `PortfolioAnalysisResult analyze(PortfolioAnalysisContext, String correlationId)` | ACL — implemented by the **sole** adapter touching `ai.*`; throws `AnalysisFailedException(FailureReason)` |

## 4. Exceptions (`portfolioanalysis.domain.exceptions`)

| Exception | Raised when |
|---|---|
| `AnalysisAlreadyInProgressException` | a new request arrives while the latest is `PENDING`/`RUNNING` (FR-013) |
| `AnalysisFailedException` | `PortfolioAnalysisAiPort.analyze(...)` cannot produce a valid result; carries the normalized `FailureReason` |

## 5. Lifecycle

```text
requestAutomatic(portfolioId) / requestManual(portfolioId)
   │
   ▼
[DB: no open request for portfolioId?] ──no──► AnalysisAlreadyInProgressException (409)
   │ yes
   ▼
insert PortfolioAnalysis(PENDING, requestedAt=now, createdByTrigger)
   │  (transaction commits here — FR-056)
   ▼
worker.runAsync(analysisId)             [@Async — returns immediately to the caller]
   │
   ▼
update → RUNNING, startedAt=now
   │
   ▼
PortfolioContextGateway.fetch(portfolioId) → PortfolioContextSnapshot
   │
   ├── insufficient (FAILED/absent valuation, no valued positions)?
   │      └──► update → FAILED(INSUFFICIENT_DATA), completedAt=now  [no provider call]
   │
   ▼ sufficient
PortfolioAnalysisContextBuilder.build(snapshot) → PortfolioAnalysisContext
   │
   ▼
PortfolioAnalysisAiPort.analyze(context, correlationId)
   │
   ├── AnalysisFailedException(reason) ──► update → FAILED(reason), completedAt=now
   ├── any other unexpected Throwable  ──► update → FAILED(UNKNOWN), completedAt=now   (FR-057)
   │
   ▼ success
update → COMPLETED, completedAt=now, summary/overallDiversification/insights/risks/
         provider/model/promptId/promptVersion/tokens/cost populated
```

Exactly one `PortfolioAnalysis` row is ever "the latest" for a Portfolio — the one with the greatest
`requested_at` (FR-015). No row is ever deleted or mutated except its own single lifecycle above.

*(No `Portfolio`, `Position`, `PortfolioValuation`, or `FinancialInstrument` entity is created,
updated, or deleted anywhere in this model — FD005 only reads them, through its own ACL.)*
