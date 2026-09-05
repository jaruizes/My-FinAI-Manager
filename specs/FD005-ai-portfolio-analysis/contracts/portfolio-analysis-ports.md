# Contract — `portfolioanalysis` domain ports

**Ports**: `PortfolioAnalysisRepository`, `PortfolioContextGateway`, `PortfolioAnalysisAiPort`
(`portfolioanalysis.domain.ports`)

```text
portfolioanalysis.business.{PortfolioAnalysisRequestService, PortfolioAnalysisWorker}
        │
        ├─ PortfolioAnalysisRepository            ──► infrastructure.persistence.PortfolioAnalysisPersistenceAdapter ──► PostgreSQL
        ├─ PortfolioContextGateway                 ──► infrastructure.portfolio.PortfolioContextGatewayAdapter
        │                                               └─ portfolio.business.{PortfolioQueryUseCase, PortfolioValuationQueryUseCase}
        └─ PortfolioAnalysisAiPort                 ──► infrastructure.ai.PortfolioAnalysisAiAdapter
                                                        └─ ai.business.GenerateAiUseCase (EN006)
```

## C1 — `PortfolioContextGateway.fetch(PortfolioId) → PortfolioContextSnapshot`

| # | Invariant |
|---|---|
| P1 | Calls `portfolio.business.PortfolioQueryUseCase.view(id)` then
`PortfolioValuationQueryUseCase.findLatest(id)` — **never** any other `portfolio.*` class (ArchUnit-enforced; this adapter is the sole importer). |
| P2 | Translates FD004's `ValuationStatus`/`PortfolioValuation`/`Position` types into
`portfolioanalysis`'s own `PortfolioContextSnapshot` — no FD004 type crosses this port. |
| P3 | `PortfolioNotFoundException` (from either use-case) propagates as-is — the caller (the
request service) maps it to the same 404 FD003/FD004 already use. |
| P4 | Never performs a write; a pure read composed from two existing read-only use-cases. |

## C2 — `PortfolioAnalysisAiPort.analyze(PortfolioAnalysisContext, correlationId) → PortfolioAnalysisResult`

| # | Invariant |
|---|---|
| Q1 | Builds one `AiRequest` (`taskType="portfolio-analysis"`, `userPrompt`=a short fixed
instruction, `context`=`PortfolioAnalysisContext.text()`, `outputSchema`=the fixed
Overall-Diversification/Key-Insights/Risks schema, `correlationId`) and calls
`ai.business.GenerateAiUseCase.generate(request)` exactly once per invocation — **never** any other
`ai.*` class (ArchUnit-enforced; this adapter is the sole importer). |
| Q2 | On success, maps `AiResponse.structuredContent()` (already schema-validated by EN006) into
`PortfolioAnalysisResult` — a missing/wrong-shaped field at this point would be an EN006 contract
violation, not something this adapter re-validates. |
| Q3 | Every `AiException` subtype is mapped to a `AnalysisFailedException` with a `FailureReason`:
`AiProviderNotConfiguredException→NOT_CONFIGURED`;
`AiProviderUnavailableException/AiProviderRateLimitedException/AiProviderAuthenticationFailedException/AiRequestTooLargeException/AiTimeoutException/AiInvalidResponseException→PROVIDER_UNAVAILABLE`
(or `TIMEOUT` for the timeout case specifically);
`AiGuardrailRejectedException→GUARDRAIL_REJECTED`;
`AiStructuredOutputInvalidException→INVALID_OUTPUT`;
`AiTokenBudgetExceededException/AiCostBudgetExceededException/AiConfigurationErrorException→UNKNOWN`
(budget/config issues are operator-facing, not meaningfully distinct to the Investor). |
| Q4 | Never retries — EN006's `AiInvocationPolicy` already applied its own bounded retry for
transient failures; this adapter does not add a second retry layer. |

## C3 — `PortfolioAnalysisRepository`

| # | Invariant |
|---|---|
| R1 | `save(analysis)` — an `insert` for a new `AnalysisId`; an `update` for one already persisted
(status transition). Never touches a row with a different id. |
| R2 | `save(...)` on a **new** analysis whose Portfolio already has an open (`PENDING`/`RUNNING`)
request throws `AnalysisAlreadyInProgressException` (translated from the DB unique-index violation —
data-model.md §1). |
| R3 | `findLatestByPortfolioId(id)` orders by `requestedAt DESC` and returns at most one row —
`Optional.empty()` only when the Portfolio has never had any analysis requested. |
