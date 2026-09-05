# FD005 — AI Portfolio Analysis

> **Status:** Approved  
> **Feature ID:** FD005  
> **Feature Name:** AI Portfolio Analysis  
> **Depends on:** FD001, FD003, FD004, EN006  
> **Initial AI Provider:** OpenAI  
> **Execution Model:** Asynchronous / background  
> **Last Updated:** 2026-09-04  

---

# 1. Purpose

Allow My-FinAI-Manager to automatically generate an AI-assisted analysis of a Portfolio after the Portfolio is created and to allow the Investor to request a new analysis on demand.

Each analysis must be persisted as a new immutable analysis record with its own creation/execution date and status.

The Portfolio detail view must display only the most recent analysis for FD005.

Historical browsing of previous analyses is explicitly deferred to a future Feature Definition.

The initial AI provider used by FD005 is OpenAI, accessed through the provider-neutral AI architecture established by EN006.

---

# 2. User Value

As an Investor, I want My-FinAI-Manager to analyze my Portfolio automatically and explain its diversification, concentration, and relevant risks so that I can understand the Portfolio beyond raw valuation data.

I also want to be able to request a fresh analysis whenever I choose.

---

# 3. Main Behavior

After a Portfolio is successfully created:

```text
Create Portfolio
      ↓
Portfolio persisted
      ↓
existing deterministic valuation flow
      ↓
enqueue/request AI Portfolio Analysis
      ↓
return Portfolio response to frontend
      ↓
background AI analysis
      ↓
persist analysis result
```

The Portfolio creation HTTP request must **not wait for AI generation to finish**.

AI analysis is a secondary asynchronous operation.

A failure or delay in AI analysis must never:

- roll back Portfolio creation;
- prevent the Portfolio from being returned to the frontend;
- invalidate an already persisted Portfolio.

---

# 4. Scope

## In Scope

- Automatically trigger an AI Portfolio Analysis after successful Portfolio creation.
- Execute the analysis asynchronously/background.
- Use OpenAI as the initial configured provider through EN006.
- Build AI context from deterministic Portfolio/valuation data.
- Analyze Portfolio diversification.
- Identify key observations.
- Identify concentration risks.
- Identify individual Position concentration.
- Identify sector concentration.
- Identify currency-diversification observations where data supports them.
- Identify missing/absent sector exposure where meaningful.
- Produce structured AI output.
- Persist every requested analysis as a new database record.
- Persist analysis status.
- Persist requested/created date.
- Persist completion date when applicable.
- Persist provider/model/prompt metadata required by EN006 traceability.
- Persist safe token-usage/cost metadata where available.
- Expose the latest Portfolio analysis.
- Display the latest analysis in Portfolio detail.
- Show an "analysis in progress" state while the latest analysis is not complete.
- Allow the Investor to trigger a new analysis manually.
- Manual re-analysis must create a new database entry.
- Automatically update the Portfolio detail to the latest completed/requested analysis state.
- Preserve old analysis records for potential future historical functionality.
- Use EN006 guardrails, system prompts, token controls, structured output, observability, and sensitive-data policy.

## Out of Scope

- Historical analysis UI.
- Comparison between previous analyses.
- AI chat/conversation.
- "Ask My Portfolio".
- AI-generated buy/sell execution.
- Automatic trading.
- News retrieval.
- RAG.
- External web search by the model.
- Portfolio rebalancing.
- Stop-Loss recommendations.
- Price discovery through AI.
- AI calculation of Portfolio values.
- AI calculation of deterministic weights or sector percentages.
- Scheduled recurring Portfolio analysis.
- Push/email notifications when analysis finishes.
- User selection of AI provider/model.
- Automatic provider fallback.

---

# 5. AI Provider

FD005 uses:

```text
OpenAI
```

as the initial configured AI provider.

However, business logic must not depend directly on OpenAI.

Conceptually:

```text
PortfolioAnalysisUseCase
        ↓
PortfolioAnalysisAiPort
        ↓
EN006 AiModelPort
        ↓
configured provider adapter
        ↓
OpenAI
```

FD005 must not reference OpenAI SDK types, DTOs, authentication, model-specific response classes, or HTTP contracts outside infrastructure.

Changing the AI provider in the future must not require changing FD005 business logic.

---

# 6. Analysis Trigger After Portfolio Creation

A successful Portfolio creation must request a Portfolio Analysis.

The trigger occurs only after the Portfolio has been successfully persisted.

Conceptually:

```text
Portfolio persisted
      ↓
requestPortfolioAnalysis(portfolioId)
```

The analysis must be submitted for background execution.

The create operation must return without waiting for:

```text
OpenAI request
model inference
guardrails
analysis persistence completion
```

---

# 7. Asynchronous Execution

FD005 requires asynchronous execution.

The exact implementation mechanism is a technical decision, but it must satisfy:

```text
HTTP create Portfolio
     ↓
Portfolio persisted
     ↓
analysis request created
     ↓
HTTP response returned
     ↓
background analysis continues
```

Possible implementation mechanisms include Spring asynchronous execution, an application-managed executor, an internal background job, or an internal event + worker.

FD005 does not require Kafka or another external broker.

The selected implementation must preserve reliability and observability.

---

# 8. Analysis Record Lifecycle

Every analysis request creates a new persisted analysis record.

Suggested statuses:

```text
PENDING
RUNNING
COMPLETED
FAILED
```

- **PENDING**: request exists but processing has not started.
- **RUNNING**: the AI analysis is executing.
- **COMPLETED**: structured analysis passed validation/guardrails and was persisted.
- **FAILED**: the analysis could not complete successfully.

A failed analysis remains stored as an analysis attempt.

---

# 9. Persistence Model

Conceptually:

```text
PortfolioAnalysis
- id
- portfolioId
- status
- requestedAt
- startedAt?
- completedAt?
- summary?
- overallDiversification?
- provider?
- model?
- promptId?
- promptVersion?
- inputTokens?
- outputTokens?
- totalTokens?
- estimatedCost?
- failureReasonCode?
- createdByTrigger
```

And conceptually:

```text
PortfolioAnalysisInsight
- id
- portfolioAnalysisId
- type
- message
- order
```

```text
PortfolioAnalysisRisk
- id
- portfolioAnalysisId
- type
- severity
- title
- explanation
- order
```

Exact persistence schema belongs to specification/planning.

The database design must preserve multiple analyses for the same Portfolio.

---

# 10. Analysis History Preservation

FD005 stores all analysis executions.

Example:

```text
Portfolio P1

Analysis A1
2026-09-04 10:00
COMPLETED

Analysis A2
2026-09-05 18:30
COMPLETED

Analysis A3
2026-09-06 09:15
RUNNING
```

FD005 UI displays only A3 because it is the most recent analysis request.

A future Feature Definition may expose A1, A2, and A3 as historical analysis.

FD005 must not delete previous completed analyses when a new one is created.

---

# 11. Latest Analysis Rule

The Portfolio detail must display the most recently requested Portfolio Analysis according to persisted request time or equivalent deterministic ordering.

If the latest record is `PENDING` or `RUNNING`, the UI shows the analysis-in-progress state.

If it is `COMPLETED`, the UI shows the completed analysis.

If it is `FAILED`, the UI shows a controlled unavailable/failed state and allows re-analysis.

---

# 12. Manual Re-analysis

Portfolio detail must provide a button:

```text
Run analysis again
```

or equivalent approved UX wording.

When pressed:

```text
POST re-analysis
      ↓
new PortfolioAnalysis row
      ↓
status = PENDING
      ↓
background execution
```

The existing analysis must remain stored.

Manual re-analysis must never update the previous analysis record to represent a new execution.

---

# 13. Concurrent Re-analysis

The application must prevent uncontrolled duplicate concurrent analysis requests.

If the latest analysis is already `PENDING` or `RUNNING`, the UI should disable or otherwise prevent another immediate re-analysis request.

The backend must also protect against duplicate concurrent requests rather than relying only on frontend behavior.

Exact concurrency-control implementation belongs to planning.

---

# 14. Source Data for Analysis

AI analysis must use deterministic data already available inside My-FinAI-Manager.

The context may include:

```text
Portfolio name
latest valuation status
Portfolio total value
Position tickers
Position weights
Position values
sector classification
sector weights
currency exposure
valuation timestamp
```

The AI context must be prepared through a dedicated context builder.

Conceptually:

```text
Portfolio
+
latest PortfolioValuation
        ↓
PortfolioAnalysisContextBuilder
        ↓
compact deterministic context
        ↓
PortfolioAnalysisAiPort
```

The AI provider must not query or calculate current market prices itself.

---

# 15. Valuation Dependency

The Portfolio Analysis should use the latest available FD004 valuation snapshot.

A completed valuation provides the preferred analysis context.

If valuation is `PARTIAL`, AI analysis may proceed only with explicitly available facts and must be informed that the valuation is partial.

If valuation is `FAILED`, missing, or does not contain enough deterministic information for meaningful analysis, the AI analysis must not invent missing Portfolio facts.

The analysis attempt should fail or produce an explicit insufficient-data result according to specification rules.

---

# 16. Deterministic Facts vs AI Interpretation

The following remain deterministic facts:

```text
AAPL weight = 28.7%
Technology weight = 52.4%
USD exposure = 100%
Portfolio total = X
```

The LLM may transform those facts into interpretations such as:

```text
Technology represents a significant concentration.

AAPL is the largest individual Position.

The Portfolio has limited sector diversification.
```

The model must not recalculate or silently alter deterministic values.

---

# 17. Required Analysis Structure

The analysis must contain, at minimum:

```text
Overall Diversification
Key Insights
Risks
```

Example UX content:

```text
Portfolio Analysis

Overall Diversification
Moderate

Key insights
● Technology represents 52.4% of the Portfolio.
● AAPL represents 28.7%.
● No Healthcare exposure detected.

Risks
HIGH    Sector concentration
MEDIUM  Single-position concentration
LOW     Currency diversification
```

Exact wording is generated by AI, but the response structure is controlled.

---

# 18. Overall Diversification

The AI analysis must classify overall diversification using a small controlled vocabulary.

Suggested values:

```text
LOW
MODERATE
HIGH
```

or equivalent approved terms.

The model must provide a concise explanation based only on supplied Portfolio facts.

---

# 19. Key Insights

The model should identify a concise set of meaningful observations.

Examples:

```text
Technology represents 52.4% of the Portfolio.
AAPL represents 28.7%.
No Healthcare exposure detected.
The Portfolio is concentrated in four Positions.
```

Insights must be grounded in supplied Portfolio context.

The model must not invent market forecasts, company news, future returns, missing allocations, or external facts.

---

# 20. Risk Analysis

The analysis must support structured risks.

Conceptually:

```text
Risk
- type
- severity
- title
- explanation
- evidence?
```

Initial severity vocabulary:

```text
HIGH
MEDIUM
LOW
```

Potential risk types include:

```text
SECTOR_CONCENTRATION
POSITION_CONCENTRATION
CURRENCY_CONCENTRATION
LOW_DIVERSIFICATION
MISSING_SECTOR_EXPOSURE
OTHER
```

The exact set may be refined during specification.

---

# 21. Risk Evidence

Where practical, AI-generated risks should reference deterministic evidence.

Example:

```json
{
  "type": "SECTOR_CONCENTRATION",
  "severity": "HIGH",
  "title": "Technology concentration",
  "explanation": "More than half of the Portfolio is allocated to Technology.",
  "evidence": {
    "sector": "Technology",
    "weight": 52.4
  }
}
```

Evidence values must come from deterministic context, not model calculation.

---

# 22. Structured Output Contract

FD005 should use EN006 structured output.

Conceptually:

```json
{
  "overallDiversification": {
    "level": "MODERATE",
    "explanation": "..."
  },
  "keyInsights": [
    {
      "type": "SECTOR_EXPOSURE",
      "message": "Technology represents 52.4% of the Portfolio."
    }
  ],
  "risks": [
    {
      "type": "SECTOR_CONCENTRATION",
      "severity": "HIGH",
      "title": "Sector concentration",
      "explanation": "..."
    }
  ]
}
```

The response must be schema validated before it is persisted as `COMPLETED`.

Malformed output must not be treated as successful analysis.

---

# 23. System Prompt and Task Prompt

FD005 must use EN006 prompt governance.

Conceptually:

```text
Global system prompt
      +
portfolio-analysis task instructions
      +
deterministic Portfolio context
```

The task prompt must instruct the model to:

- use only supplied Portfolio facts;
- not fabricate prices, returns, news, or external information;
- not perform deterministic valuation calculations;
- identify diversification characteristics;
- identify concentration;
- identify meaningful key insights;
- classify risks;
- state uncertainty when data is incomplete;
- return only the required structured format.

---

# 24. Prompt Versioning

The Portfolio Analysis prompt must have a stable ID and version.

Conceptually:

```text
promptId = portfolio-analysis
promptVersion = 1
```

Every persisted analysis must record the prompt version used.

Changing the prompt in the future must create a new prompt version.

---

# 25. Guardrails

FD005 must use EN006 input and output guardrails.

At minimum, guardrails must ensure:

- context is within configured token limits;
- sensitive information is not unnecessarily sent;
- model output matches the structured schema;
- prohibited/fabricated financial values are not accepted where detectable;
- unsafe or malformed output is rejected.

A guardrail rejection causes a controlled failure state.

---

# 26. Sensitive Data

The Portfolio Analysis context should contain only data needed for analysis.

Avoid sending user name, email, authentication details, account identifiers, or unrelated personal information.

Portfolio identifiers should be omitted from model context unless required.

Telemetry must follow EN006 privacy requirements.

---

# 27. Token Control

FD005 must define a bounded context.

The context builder should prioritize Portfolio allocation, Position weights, sector allocation, currency exposure, and valuation status.

It must not serialize arbitrary database entities.

The AI request must use EN006 max-input/context, max-output, and max-total-token controls.

If context exceeds limits, an explicit deterministic compaction strategy must be used.

---

# 28. Cost and Usage Metadata

Each provider invocation should retain safe usage metadata when available:

```text
provider
model
inputTokens
outputTokens
totalTokens
estimatedCost
```

This supports EN006 Grafana/Prometheus observability and future AI-cost analysis.

Token/cost metadata does not need to be displayed to the Investor in FD005.

---

# 29. Observability

The background operation must be observable through EN006.

Conceptual trace:

```text
Portfolio creation
      ↓
request Portfolio Analysis
      ↓
background execution
      ↓
PortfolioAnalysisUseCase
      ↓
AiInvocationService
      ↓
OpenAI adapter
      ↓
provider HTTP call
      ↓
guardrails
      ↓
persist result
```

Telemetry should include safe metadata such as:

```text
ai.task=portfolio-analysis
ai.provider=openai
ai.model
ai.prompt.id
ai.prompt.version
ai.input.tokens
ai.output.tokens
ai.total.tokens
ai.latency.ms
ai.success
ai.guardrail.result
```

Raw Portfolio context, prompts, and completions must not be logged/traced by default.

---

# 30. Background Execution Observability

The asynchronous boundary must preserve correlation/tracing where technically feasible.

At minimum, developers must be able to determine:

```text
Was analysis requested?
Did background execution start?
Which provider/model was used?
How long did it take?
How many tokens were used?
Did guardrails pass?
Was the result persisted?
Why did it fail?
```

without storing sensitive business content in telemetry.

---

# 31. Portfolio Detail UX

FD005 extends Portfolio detail with:

```text
AI Portfolio Analysis
```

The section displays the latest analysis only.

Example completed state:

```text
AI Portfolio Analysis

Overall Diversification
Moderate

Key insights
• Technology represents 52.4% of the Portfolio.
• AAPL represents 28.7%.
• No Healthcare exposure detected.

Risks
HIGH      Sector concentration
MEDIUM    Single-position concentration
LOW       Currency diversification

Analysed: 2026-09-04 20:30

[ Run analysis again ]
```

---

# 32. Analysis In Progress UX

If the latest analysis is `PENDING` or `RUNNING`, display:

```text
Your Portfolio is being analysed.
The results will appear here when they are available.
```

The UI must not display a previous completed analysis as though it were the current latest result while a newer analysis is running.

The re-analysis button should be disabled or hidden while the latest request is in progress.

---

# 33. Failed Analysis UX

If the latest analysis is `FAILED`, display a controlled state such as:

```text
Portfolio analysis is currently unavailable.

[ Run analysis again ]
```

Do not expose provider error payloads, stack traces, authentication details, or internal exceptions.

---

# 34. Frontend Refresh Strategy

Because generation is asynchronous, Portfolio detail must discover when the latest analysis changes state.

A simple initial approach is polling:

```text
GET latest analysis
      ↓
PENDING/RUNNING?
      ├── yes → poll again after configured interval
      └── no  → render result/failure
```

Exact polling interval is a technical/UX decision.

WebSockets/SSE are not required by FD005.

Polling must stop on `COMPLETED`, `FAILED`, or page/component disposal.

---

# 35. API Expectations

Conceptually:

```text
GET /api/portfolios/{portfolioId}/analysis/latest
```

returns the latest Portfolio Analysis.

Manual re-analysis:

```text
POST /api/portfolios/{portfolioId}/analysis
```

creates a new analysis record and starts background execution.

The POST should return promptly with an accepted/requested representation, conceptually:

```json
{
  "analysisId": "...",
  "status": "PENDING",
  "requestedAt": "..."
}
```

Exact HTTP status/contracts belong to specification.

---

# 36. Automatic Trigger API Semantics

Automatic analysis after Portfolio creation is internal application behavior.

The existing Portfolio creation public contract should not wait for or embed the completed AI analysis.

The create response may remain focused on successful Portfolio creation.

---

# 37. Business Rules

## BR-001 — Automatic Analysis
Successful Portfolio creation triggers a new AI Portfolio Analysis request.

## BR-002 — Async Execution
AI analysis executes in background and does not block Portfolio creation response.

## BR-003 — Creation Independence
AI failure must never roll back Portfolio creation.

## BR-004 — New Record per Analysis
Every automatic or manual analysis request creates a new persisted `PortfolioAnalysis`.

## BR-005 — Preserve Previous Analyses
Previous analyses are never overwritten by a new execution.

## BR-006 — Latest Analysis
FD005 UI exposes only the latest requested analysis.

## BR-007 — Manual Re-analysis
The Investor may request a new analysis on demand.

## BR-008 — No Concurrent Duplicate
A new manual analysis should not be started while the latest analysis is `PENDING` or `RUNNING`.

## BR-009 — OpenAI Through EN006
OpenAI is the initial provider but must be invoked through EN006 provider-neutral abstractions.

## BR-010 — Deterministic Input Facts
Portfolio numeric facts must come from deterministic application data.

## BR-011 — No Financial Fact Fabrication
The model must not invent missing prices, weights, sectors, or Portfolio values.

## BR-012 — Structured Output
Completed analysis must satisfy the approved structured-output schema.

## BR-013 — Traceability
Every analysis stores provider/model/prompt-version metadata.

## BR-014 — Timestamp
Every analysis stores request date and completion date when applicable.

## BR-015 — In-Progress UX
PENDING/RUNNING latest analysis must be represented explicitly in Portfolio detail.

## BR-016 — Failure Isolation
A failed AI analysis affects only that analysis record.

---

# 38. Acceptance Criteria

## AC-001 — Automatic Request
**Given** a Portfolio is successfully created  
**When** Portfolio creation completes  
**Then** a new Portfolio Analysis record is created/requested  
**And** AI processing begins asynchronously.

## AC-002 — Non-Blocking Creation
**Given** OpenAI analysis requires several seconds  
**When** the Portfolio is created  
**Then** the create operation returns without waiting for AI completion.

## AC-003 — Initial Pending State
**Given** a new analysis has been requested  
**When** background processing has not completed  
**Then** the latest analysis has status `PENDING` or `RUNNING`.

## AC-004 — Completed Persistence
**Given** OpenAI returns a valid response  
**And** guardrails/schema validation pass  
**When** processing completes  
**Then** the analysis is stored as `COMPLETED`  
**And** completion date is persisted.

## AC-005 — Latest Analysis in Detail
**Given** a Portfolio has one completed analysis  
**When** the Investor opens Portfolio detail  
**Then** the AI Portfolio Analysis section displays that analysis.

## AC-006 — In-Progress Message
**Given** the latest analysis is `PENDING` or `RUNNING`  
**When** Portfolio detail is displayed  
**Then** the UI indicates that the Portfolio is being analysed  
**And** indicates results will appear when available.

## AC-007 — Manual Re-analysis
**Given** a completed analysis exists  
**When** the Investor selects `Run analysis again`  
**Then** a new Portfolio Analysis record is created  
**And** it is processed asynchronously.

## AC-008 — Previous Record Preserved
**Given** analysis A1 exists  
**When** analysis A2 is requested  
**Then** A1 remains stored unchanged.

## AC-009 — Latest Request Wins UI
**Given** A1 is completed  
**And** newer A2 is running  
**When** the Investor views Portfolio detail  
**Then** the UI shows A2's in-progress state  
**And** does not present A1 as the current latest analysis.

## AC-010 — Latest Completed Result
**Given** A2 completes successfully  
**When** the UI refreshes/polls the latest-analysis endpoint  
**Then** A2 is displayed.

## AC-011 — Analysis Content
**Given** deterministic Portfolio data includes sector and Position allocations  
**When** AI analysis completes  
**Then** output contains overall diversification, key insights, and structured risks.

## AC-012 — Provider Independence
**Given** FD005 uses OpenAI initially  
**Then** business code does not depend directly on OpenAI-specific types/contracts.

## AC-013 — Failed Analysis
**Given** provider execution fails  
**When** background processing terminates  
**Then** the analysis is persisted as `FAILED`  
**And** Portfolio creation/state remains valid.

## AC-014 — No Hallucinated Missing Facts
**Given** required deterministic information is unavailable  
**When** the model is invoked or output validated  
**Then** missing financial facts are not invented as known values.

---

# 39. Testing Expectations

At minimum, verify:

- automatic analysis trigger after Portfolio creation;
- non-blocking create response;
- persisted `PENDING` analysis record;
- transition to `RUNNING`;
- transition to `COMPLETED`;
- transition to `FAILED`;
- request/completion timestamps;
- provider/model/prompt metadata;
- structured-output validation;
- deterministic context construction;
- guardrail handling;
- manual re-analysis creates a new row;
- previous analysis is preserved;
- latest-analysis query;
- concurrent duplicate prevention;
- Portfolio detail completed state;
- Portfolio detail in-progress state;
- Portfolio detail failed state;
- frontend polling termination;
- AI failure does not affect Portfolio state.

---

# 40. AI Provider Testing

Normal tests must not call live OpenAI.

Use deterministic mocks/stubs at the EN006/OpenAI adapter boundary.

Live OpenAI access may exist only as an opt-in smoke test governed by EN006 token/cost controls.

---

# 41. E2E Testing

FD005 requires deterministic browser E2E verification.

The E2E environment must use:

```text
real frontend
real backend
real PostgreSQL
real asynchronous execution
controlled/stubbed AI provider boundary
```

It must not require live OpenAI.

---

# 42. E2E-001 — Automatic Portfolio Analysis

Test flow:

```text
Playwright
    ↓
Create Portfolio
    ↓
Portfolio creation response returns
    ↓
background analysis pending/running
    ↓
open Portfolio detail
    ↓
verify "being analysed" state
    ↓
controlled AI response completes
    ↓
frontend polling detects COMPLETED
    ↓
latest analysis rendered
```

The deterministic AI fixture should return conceptually:

```text
Overall Diversification
MODERATE

Key Insights
- Technology represents 52.4% of the Portfolio.
- AAPL represents 28.7%.
- No Healthcare exposure detected.

Risks
HIGH   Sector concentration
MEDIUM Single-position concentration
LOW    Currency diversification
```

---

# 43. E2E-002 — Manual Re-analysis

Precondition:

```text
Analysis A1 = COMPLETED
```

Then:

1. Open Portfolio detail.
2. Verify A1 is displayed.
3. Click `Run analysis again`.
4. Verify analysis-in-progress state.
5. Complete deterministic provider response for A2.
6. Verify A2 replaces A1 in the latest-analysis view.
7. Verify A1 still exists in persistence.
8. Verify A2 has a newer request/completion timestamp.

The feature must not update A1 in place.

---

# 44. E2E-003 — Provider Failure

1. Configure controlled AI boundary to fail.
2. Create or re-analyse a Portfolio.
3. Verify Portfolio remains valid.
4. Verify latest analysis reaches `FAILED`.
5. Verify Portfolio detail shows controlled failure state.
6. Verify re-analysis remains possible afterward.
7. Verify no provider error payload or sensitive data is displayed.

---

# 45. Closure Gate

FD005 must not be accepted, closed, or marked Completed if:

- Portfolio creation waits for AI completion;
- Portfolio creation can fail because AI analysis fails;
- analyses overwrite previous rows;
- manual re-analysis does not create a new record;
- latest-analysis semantics are incorrect;
- pending/running state is not visible in the UI;
- OpenAI is directly coupled to business/core code;
- raw AI output bypasses schema validation/guardrails;
- live OpenAI is required by CI;
- missing financial facts can be fabricated and accepted;
- E2E-001 is missing/failing;
- E2E-002 is missing/failing;
- E2E-003 is missing/failing.

---

# 46. Explicit Product Decisions

1. Portfolio Analysis is automatically requested after Portfolio creation.
2. Analysis execution is asynchronous/background.
3. Portfolio creation does not wait for analysis completion.
4. Analysis failure never invalidates Portfolio creation.
5. Every analysis request creates a new persistent record.
6. Previous analyses are preserved.
7. FD005 displays only the latest requested analysis.
8. Analysis history UI is deferred.
9. Investor can manually request a new analysis.
10. Manual re-analysis creates a new database record.
11. Concurrent duplicate re-analysis is prevented.
12. Latest `PENDING`/`RUNNING` analysis replaces older completed output in current UI state.
13. The UI displays an explicit analysing message while processing.
14. OpenAI is the initial provider.
15. OpenAI is accessed only through EN006 provider-neutral architecture.
16. Analysis is based on deterministic Portfolio/valuation data.
17. AI does not calculate Portfolio valuation.
18. AI does not retrieve market prices.
19. AI output uses structured response validation.
20. Analysis contains Overall Diversification, Key Insights, and Risks.
21. Risks use controlled severity values.
22. Analysis request/completion dates are persisted.
23. Provider/model/prompt version are persisted.
24. Token/cost metadata is persisted when available.
25. EN006 guardrails and privacy rules apply.
26. Normal CI does not call live OpenAI.
27. Historical browsing is explicitly deferred to a future FD.

---

# 47. Open Technical Decisions

The following may be resolved during specification/planning:

1. Exact background execution implementation.
2. Executor/thread-pool configuration.
3. Transaction boundary between analysis request creation and background execution.
4. Exact status transition mechanism.
5. Exact latest-analysis ordering key.
6. Exact concurrency/duplicate-request protection.
7. Exact OpenAI model.
8. Exact polling interval.
9. Exact maximum polling duration/front behavior.
10. Exact Portfolio Analysis structured schema.
11. Exact diversification vocabulary/cutoffs.
12. Exact risk types.
13. Exact number/maximum number of key insights.
14. Exact number/maximum number of risks.
15. Persistence schema layout.
16. Whether failed records persist normalized failure codes only or additional safe diagnostic metadata.
17. Whether an analysis is allowed when FD004 valuation is `PARTIAL`.
18. Exact insufficient-data behavior.
19. Whether the manual button label is `Run analysis again`, `Refresh analysis`, or equivalent UX wording.

None of these decisions may change the core requirements of asynchronous non-blocking execution, immutable analysis history, latest-only display, or provider-neutral EN006 usage.

**Resolved by the product owner on 2026-09-05 (before formal specification):**

- **#7 Exact OpenAI model / real adapter scope** — a genuine `OpenAiModelAdapter` (implementing
  EN006's `AiModelPort`) is built now, following the exact `FINNHUB_API_KEY` pattern from EN005:
  `OPENAI_API_KEY` supplied via environment/`.env`; blank ⇒ the capability is disabled and analysis
  attempts fail to a controlled `FAILED` state (never blocking Portfolio creation). Tests use
  WireMock/deterministic stubs only; an opt-in-only smoke test may hit live OpenAI, never CI.
- **#1/#2 Background execution mechanism** — Spring `@Async` + a dedicated
  `ThreadPoolTaskExecutor` bean. In-process, no new infrastructure/container/dependency, no ADR
  required (unlike EN006's observability stack, this introduces no new deployable or runtime
  topology).
- **Module boundary (not in the original §47 list, added as material)** — a new sibling module
  `portfolioanalysis` (alongside `portfolio`/`financialinstrument`/`marketdata`/`ai`), following the
  established one-module-per-bounded-capability pattern. It owns `PortfolioAnalysis` +
  `PortfolioAnalysisInsight` + `PortfolioAnalysisRisk`, reads Portfolio/valuation data through
  `portfolio`'s published ports (AR-062 style), and consumes EN006's `GenerateAiUseCase` through its
  own `PortfolioAnalysisAiPort`.

The remaining items in this list (#3-6, #8-19) are deferred to `/speckit-plan`/`/speckit-tasks` as
this section itself allows.

---

# 48. Human Approval

Before formal specification:

- [X] Automatic Portfolio Analysis after creation is approved.
- [X] Asynchronous/background execution is approved.
- [X] Portfolio create response does not wait for AI completion.
- [X] AI failure does not affect Portfolio creation.
- [X] OpenAI is approved as the initial provider.
- [X] EN006 provider-neutral integration is mandatory.
- [X] Each analysis request creates a new database record.
- [X] Previous analyses are preserved.
- [X] Latest requested analysis is displayed in FD005.
- [X] Historical browsing is deferred.
- [X] Manual re-analysis button is approved.
- [X] Duplicate concurrent re-analysis prevention is approved.
- [X] PENDING/RUNNING message is approved.
- [X] FAILED state and retry are approved.
- [X] Overall Diversification section is approved.
- [X] Key Insights section is approved.
- [X] Risks section is approved.
- [X] HIGH/MEDIUM/LOW risk severity is approved.
- [X] Structured AI output is approved.
- [X] Deterministic Portfolio context is approved.
- [X] No AI price discovery/calculation is approved.
- [X] Prompt version/provider/model traceability is approved.
- [X] Token/cost metadata collection is approved.
- [X] EN006 guardrails/privacy/observability are approved.
- [X] Frontend polling is acceptable for the initial asynchronous UX.
- [X] Live OpenAI is not required by CI.
- [X] E2E-001 automatic analysis is approved.
- [X] E2E-002 manual re-analysis is approved.
- [X] E2E-003 provider-failure behavior is approved.
- [X] No unapproved historical, news, recommendation, or agent behavior is introduced.

The §47 open technical decisions were resolved by the product owner on 2026-09-05: a real
`OpenAiModelAdapter` is built now (env-key-gated, WireMock-tested, no live provider in CI);
background execution uses Spring `@Async` + a dedicated `ThreadPoolTaskExecutor` (in-process, no
ADR); the new capability lives in its own sibling module `portfolioanalysis`.

**Approved by:** jaruiz  
**Date:** 2026-09-05  
**Status:** Approved
