# Phase 0 — Research: FD005 (AI Portfolio Analysis)

**Feature dir**: `specs/FD005-ai-portfolio-analysis/` · **Plan**: [plan.md](./plan.md) · **Spec**:
[spec.md](./spec.md)

Resolves the plan's Open Decisions (OD-1…OD-9) into concrete, implementable detail. No
`NEEDS CLARIFICATION` remains.

---

## D1 — EN006 extension: per-task provider routing

**Decision.** `ai.infrastructure.config.AiProperties` gains:

```java
@ConfigurationProperties("ai")
public record AiProperties(
        String defaultProvider, String defaultModel, Limits limits, Timeout timeout, Retry retry,
        Map<String, TaskOverride> tasks) {   // NEW — key = taskType
    public record TaskOverride(String provider) { }
    // Limits/Timeout/Retry unchanged
}
```

`AiInvocationSettings` (domain) gains `Map<String, String> taskProviders` (task → provider id).
`AiModuleConfiguration.aiInvocationSettings(...)` copies `properties.tasks()` into it (empty map
default). `LocalAiModelAdapter` and the new `OpenAiModelAdapter` become named beans —
`@Component("local")` / `@Component("openai")` — and **lose** their `@ConditionalOnProperty` (both
are always registered; each independently reports unavailability when unusable — `local` never is;
`openai` reports `AiProviderNotConfiguredException` when `OPENAI_API_KEY` is blank). Spring injects
`Map<String, AiModelPort> modelPortsByProvider` into `AiInvocationPolicy` (Spring auto-populates a
`Map<String, T>` constructor parameter with every `T` bean keyed by bean name — no custom wiring
code needed).

`AiInvocationPolicy.generate(...)` resolves the provider once, at the top of the method:

```java
String providerId = settings.taskProviders().getOrDefault(request.taskType(), settings.defaultProvider());
AiModelPort modelPort = modelPortsByProvider.get(providerId);
if (modelPort == null) {
    throw new AiConfigurationErrorException("no AiModelPort registered for provider '" + providerId + "'");
}
```

`application.yml`: `ai.tasks.portfolio-analysis.provider: openai`. **No entry for `diagnostic`** —
it keeps resolving to `ai.default-provider` (`local`), so EN006's own diagnostic endpoint and its
existing `PlatformIntegrationIT` case are behavior-unchanged (regression-tested explicitly).

**Rationale.** Resolved Q1; plan OD-1. EN006's own enabler text (§7) already sketched exactly this
`ai.tasks.<task>.provider` shape as a "future task-specific routing" extension point — implementing
it now, driven by FD005's genuine need, is completing an anticipated design, not inventing a new
one (constitution IV carve-out).

**Alternatives rejected.** A second `AiInvocationPolicy`/`GenerateAiUseCase` bean pair just for
FD005 — duplicates all guardrail/budget/telemetry orchestration EN006 already centralizes (directly
contradicts EN006's own FR-038 "business features must not reimplement these concerns
independently").

---

## D2 — EN006 extension: prompt versioning per task

**Decision.** `PromptRepositoryPort`:

```java
public interface PromptRepositoryPort {
    PromptReference findGlobalSystemPrompt();
    Optional<PromptReference> findTaskInstructions(String taskType);   // CHANGED: was Optional<String>
    boolean isKnownTask(String taskType);
}
```

`ClasspathPromptRepository` loads `prompts/tasks/portfolio-analysis-v1.txt` into a
`PromptReference("portfolio-analysis", "v1", <text>)`; `KNOWN_TASKS` becomes
`Set.of("diagnostic", "portfolio-analysis")`; `TASK_INSTRUCTIONS` becomes
`Map<String, PromptReference>` with one entry.

`PromptService.compose(taskType)`:

```java
PromptReference global = promptRepository.findGlobalSystemPrompt();
Optional<PromptReference> task = promptRepository.findTaskInstructions(taskType);
return task
    .map(t -> new PromptReference(t.promptId(), t.promptVersion(), global.body() + "\n\n" + t.body()))
    .orElse(global);
```

So a `portfolio-analysis` invocation now persists `promptId="portfolio-analysis"`,
`promptVersion="v1"` (FD005 FR-035); `diagnostic` (no dedicated instructions) keeps persisting
`"global-system"`/`"v1"` exactly as before — **zero behavior change** for EN006's own test.

**Rationale.** FD005 FR-035, BR-013 — the persisted prompt version must identify the *task's own*
prompt, not the global one it's layered on. EN006's `PromptServiceTest`/`ClasspathPromptRepositoryTest`
already exercise the "no dedicated instructions" path (the `diagnostic` task) — updated to the new
`Optional<PromptReference>` shape but asserting the identical outcome.

**Alternatives rejected.** Leave it persisting `"global-system"` for every task — passes EN006's own
FR-014 ("identifies the prompt used") only loosely; fails FD005 FR-035 outright.

---

## D3 — `OpenAiModelAdapter`

**Decision.** New package `ai.infrastructure.provider.openai`:

- `OpenAiProperties` — `@ConfigurationProperties("openai")`: `apiKey` (`${OPENAI_API_KEY:}`),
  `baseUrl` (`${OPENAI_BASE_URL:https://api.openai.com/v1}`), `model`
  (`${OPENAI_MODEL:gpt-4o-mini}` — spec A1), `connectTimeout`/`readTimeout`,
  `pricing.inputPer1k`/`pricing.outputPer1k` (`BigDecimal`, small documented placeholder defaults —
  same spirit as FD005 spec A1, replaced with real pricing if/when it matters).
- `OpenAiRestClient` — own `RestClient` (no shared client with Finnhub/Frankfurter), `Authorization:
  Bearer <key>` header (never a query parameter), `POST {baseUrl}/chat/completions`. Structured log
  `event=ProviderCall provider=openai capability=chat-completion outcome=… httpStatusCategory=…
  latencyMs=…` — no key, no prompt/completion body.
- `dto.OpenAiChatRequest` — `model`, `messages: [{role, content}]` (`system` = composed system
  prompt, `user` = user prompt + context), `response_format: {"type":"json_object"}` (resolved
  OD-3 — no JSON-schema translation layer), `max_tokens`, `temperature?`.
- `dto.OpenAiChatResponse` — `choices: [{message: {content}, finish_reason}]`, `usage:
  {prompt_tokens, completion_tokens, total_tokens}`, `id` (→ `AiResponse.requestId`).
- `mapper.OpenAiChatMapper` — `AiRequest → OpenAiChatRequest`; `OpenAiChatResponse → AiResponse`
  (parses `choices[0].message.content` as JSON into `Map<String,Object>` via Jackson when
  `AiRequest.outputSchema()` is present — EN006's own `StructuredOutputValidator` then validates it,
  unchanged; the SAME text is also `AiResponse.content()`); `usage` → `AiUsage` (`estimatedCost =
  inputTokens/1000 * pricing.inputPer1k + outputTokens/1000 * pricing.outputPer1k`).
- `OpenAiModelAdapter implements AiModelPort`, `@Component("openai")`:
  - blank `apiKey` → `AiProviderNotConfiguredException`, **no outbound call** (mirrors Finnhub's
    blank-key behavior exactly).
  - `401`/`403` → `AiProviderAuthenticationFailedException`; `429` → `AiProviderRateLimitedException`;
    other `4xx`/`5xx`/timeout/`IOException` → `AiProviderUnavailableException`; a response whose
    `content` isn't valid JSON when structured output was requested → `AiInvalidResponseException`.

**New EN006 exception**: `AiProviderNotConfiguredException extends AiException` (mirrors
`MarketDataNotConfiguredException`) — never retried (contract `ai-model-port.md`'s existing "never
retry a non-transient failure" rule already covers any new exception added to the base type).

**Rationale.** Resolved Q1; mirrors EN005's Finnhub/Frankfurter adapter pattern exactly (own
`RestClient`, own DTOs/mapper, translated errors, structured `ProviderCall` logging, env-var-gated
key). `response_format: json_object` (not OpenAI's native `json_schema` mode) avoids building an
`OutputSchema → JSON Schema` translator EN006 deliberately deferred (EN006 research D3) — the prompt
instructs the exact shape (D6 below), and EN006's `StructuredOutputValidator` still enforces it
before persistence, so nothing is less safe.

**Alternatives rejected.** An official OpenAI Java SDK — adds a real new dependency + provider-
specific types that would need containing entirely within this one package anyway; a plain
`RestClient` is simpler, matches EN005's precedent, and this project's technology-policy conservatism.

---

## D4 — Database-level duplicate-request prevention

**Decision.** `V5__portfolio_analysis.sql`:

```sql
CREATE UNIQUE INDEX portfolio_analysis_one_open_per_portfolio_uk
    ON portfolio_analysis (portfolio_id)
    WHERE status IN ('PENDING', 'RUNNING');
```

`PortfolioAnalysisRequestService.requestManual(portfolioId)`:
1. pre-check `repository.findLatestByPortfolioId(portfolioId)` — if `PENDING`/`RUNNING`, throw
   `AnalysisAlreadyInProgressException` immediately (fast path, no DB round-trip to the constraint).
2. Otherwise insert; if the **unique-index violation** still occurs (a genuine race — two requests
   arriving concurrently), catch the persistence exception and translate it to the same
   `AnalysisAlreadyInProgressException` (the database is authoritative; the pre-check is a UX
   nicety, not the real guard).

**Rationale.** Resolved Q2/plan OD-5; FR-013, BR-008. Mirrors `portfolio`'s own
`idempotency_key` unique-constraint pattern (FD001) — DB constraints are this project's established
mechanism for correctness under concurrency, not application-level locks.

**Alternatives rejected.** `SELECT ... FOR UPDATE` — needs a row to lock in the first place (no row
exists for a *new* Portfolio's first request) and doesn't prevent a genuinely concurrent *insert*
race the way a unique index does.

---

## D5 — Async trigger wiring

**Decision.** Reuse `portfolio.domain.events.PortfolioCreatedEvent` (already published by
`CreatePortfolioService` after every successful, non-replayed creation — FD004 already listens to
it synchronously for valuation). Add a **second**, independent listener:

```java
@Component
public class PortfolioAnalysisOnCreationListener {
    @EventListener
    public void on(PortfolioCreatedEvent event) {
        requestPortfolioAnalysisUseCase.requestAutomatic(event.portfolioId());
    }
}
```

`requestAutomatic(...)` creates the `PENDING` row (same path as manual, `createdByTrigger=AUTOMATIC`,
**no** duplicate-check needed — a brand-new Portfolio can have no prior analysis) and then calls
`portfolioAnalysisWorker.runAsync(analysisId)` — a **separate Spring bean**, so the `@Async` proxy
applies (Spring's well-known self-invocation limitation: calling `@Async` on `this` bypasses the
proxy and runs synchronously — avoided here by construction, since the listener and the worker are
different beans).

**Rationale.** Resolved Q2; FR-001, FR-002, FR-005. No new event type — `PortfolioCreatedEvent`
already fires at exactly the right point (after commit, catch-all-safe per FD004's own listener
pattern). Two listeners on the same event (FD004's valuation listener + FD005's analysis listener)
run independently; neither's failure affects the other (each is its own `@EventListener`, Spring
invokes them independently and a listener's own internal try/catch — mirroring
`PortfolioValuationOnCreationListener`'s catch-all pattern — prevents one from ever seeing the
other's exception).

**Alternatives rejected.** A new `PortfolioAnalysisRequestedEvent` — pure indirection; nothing
needs to distinguish "a Portfolio was created" from "an analysis should be requested for it" at the
event level, since FD005 always wants both, always, for every creation.

---

## D6 — Context builder & task prompt

**Decision.** `PortfolioContextGateway.fetch(PortfolioId) → PortfolioContextSnapshot` (domain
model, `portfolioanalysis`-owned): `portfolioName`, `totalValueEur?`, `totalValueUsd?`,
`valuationStatus` (mirrors FD004's `ValuationStatus`, re-declared locally — no cross-module domain
type reuse beyond `PortfolioId`, keeping the ACL clean), `positions: List<PositionSnapshot>`
(`ticker`, `weight?`, `valueEur?`, `sector?`, `currency`), `sectors: List<SectorSnapshot>` (`sector`,
`weight?`), `valuedAt?`.

`PortfolioAnalysisContextBuilder.build(PortfolioContextSnapshot) → PortfolioAnalysisContext` (pure,
`domain.model`, no ports — mirrors `PortfolioValuationCalculator`/`StructuredOutputValidator`):
renders a compact, deterministic text block (not raw JSON dump) e.g.:

```text
Portfolio "Long Term Investment" — valuation COMPLETED as of 2026-09-05T10:00:00Z.
Total value: €2,100.00 / $2,625.00.
Positions (by weight):
- AAPL: 76.19% (Technology, €1,600.00)
- SAN: 23.81% (Financial Services, €500.00)
Sector allocation:
- Technology: 76.19%
- Financial Services: 23.81%
```

For a `PARTIAL` valuation, the rendered text explicitly states "valuation is PARTIAL — N position(s)
could not be valued" so the model never treats a partial snapshot as complete (FR-023). Returns an
explicit "insufficient" marker (checked by the worker, D9 in plan.md) when `valuationStatus ∈
{FAILED, PENDING}` or there are zero valued positions — **no provider call is made** in that case.

**Task prompt** (`prompts/tasks/portfolio-analysis-v1.txt`, `promptId="portfolio-analysis"`,
`promptVersion="v1"`): instructs the model, per FD005 §23, to use only supplied facts; never
fabricate prices/returns/news/external information; never perform deterministic calculations;
identify diversification and concentration; produce 2–5 grounded key insights; classify 1–4 risks
(type/severity/title/explanation) using exactly the vocabularies in FR-028/FR-031; state uncertainty
when data is incomplete; and return **only** a JSON object shaped
`{"overallDiversification":{"level":...,"explanation":...},"keyInsights":[{"type":...,"message":...}],"risks":[{"type":...,"severity":...,"title":...,"explanation":...}]}`
— no prose outside the JSON.

**Rationale.** FR-020, FR-021, FR-029; spec A9. Text (not raw JSON) keeps the prompt compact and
matches EN006's `ContextBudgetService` truncation semantics (a whole-word-boundary string, not a
data-structure that would need re-serialization mid-budget-cut).

---

## D7 — Persistence & domain mapping

**Decision.** See [data-model.md](./data-model.md) for the full schema. `PortfolioAnalysis`
(`domain.model`) is a single immutable-per-state-snapshot record with wither methods
(`withRunning()`, `withCompleted(result, usage, completedAt)`, `withFailed(reason, completedAt)`),
mirroring `AiRequest`'s `withX` pattern from EN006. The persistence adapter's `save(...)` is a plain
JPA `save` (insert for a new id, update for the same id transitioning state) — "immutable" (FD005
§10) means a **different** analysis's row is never touched, not that a single analysis's own row
never transitions.

**Rationale.** FR-006–FR-010; matches the project's established domain-model style (`AiRequest`,
`Portfolio` in FD001).

---

## D8 — REST contract

**Decision.**

```yaml
GET /api/portfolios/{portfolioId}/analysis/latest:
  200: PortfolioAnalysisResponse (status NONE|PENDING|RUNNING|COMPLETED|FAILED; content fields
       present only when COMPLETED)
  400: malformed portfolioId
  404: unknown portfolioId (/problems/portfolio-not-found)

POST /api/portfolios/{portfolioId}/analysis:
  202: RequestAnalysisResponse {analysisId, status: "PENDING", requestedAt}
  400: malformed portfolioId
  404: unknown portfolioId
  409: /problems/analysis-already-in-progress (ValidationProblem-style, RFC 9457)
```

Neither response includes `provider`/`model`/`promptId`/`promptVersion`/token/cost fields (FD005
§28: "does not need to be displayed to the Investor") — those stay in the persisted row and in
telemetry only, never serialized to the API.

**Rationale.** OD-7; FR-048–FR-050; matches FD003/FD004's `problem+json` and `ValidationProblem`
conventions exactly (no new error-shape invented).

---

## D9 — Frontend polling

**Decision.** `PortfolioAnalysisService` (Angular, mirrors `PortfolioValuationService`'s shape):
`getLatest(portfolioId): Observable<PortfolioAnalysisView>`, `requestNew(portfolioId):
Observable<RequestedAnalysisView>`. `portfolio-detail.page.ts` adds a `pollAnalysis()` mechanism:
`interval(2000).pipe(switchMap(() => service.getLatest(id)), takeWhile(isOpen, true),
takeUntilDestroyed())` — stops the moment status is `COMPLETED`/`FAILED`/`NONE`, and always on
component destruction (Angular's standard `DestroyRef`-based idiom, already available in Angular
20). A soft 60s cap: after 30 polls with no terminal state, stop polling and show "still processing,
check back soon" without treating it as an error (spec A4).

**Rationale.** FD005 §34; plan OD-8.

---

## D10 — Test matrix

| Layer | Tool | Covers |
|---|---|---|
| `PortfolioAnalysis` domain model + wither methods | JUnit 5 | every field/guard, state transitions |
| `PortfolioAnalysisContextBuilder` | JUnit 5, RED-first | full/partial/insufficient valuation rendering; deterministic text output |
| `PortfolioAnalysisRequestService` | JUnit 5 + Mockito | creates PENDING; rejects duplicate (pre-check + constraint-race path); triggers the worker exactly once |
| `PortfolioAnalysisWorker` | JUnit 5 + Mockito | RUNNING→COMPLETED happy path; RUNNING→FAILED on every `AiException` type; RUNNING→FAILED on an unexpected `RuntimeException` (never stuck) |
| `PortfolioContextGatewayAdapter` | JUnit 5 + Mockito (fakes `PortfolioQueryUseCase`/`PortfolioValuationQueryUseCase`) | maps Portfolio+Valuation → Snapshot correctly for COMPLETED/PARTIAL/FAILED/absent |
| `PortfolioAnalysisAiAdapter` | JUnit 5 + Mockito (fakes `GenerateAiUseCase`) | builds the right `AiRequest`; maps a conforming response to `PortfolioAnalysisResult`; translates every EN006 exception to a `FailureReason` |
| `OpenAiModelAdapter`/`OpenAiChatMapper` | JUnit 5, `MockRestServiceServer` | request shape (auth header, model, `response_format`); response mapping incl. usage/cost; every error status; blank-key no-call path |
| EN006 `AiInvocationPolicyTest`/`PromptServiceTest`/`ClasspathPromptRepositoryTest` | updated | per-task routing regression; prompt-versioning regression — **diagnostic behavior unchanged** |
| `PortfolioAnalysisPersistenceAdapterIT` | Testcontainers PostgreSQL | round-trip incl. insights/risks; **two concurrent inserts ⇒ exactly one succeeds** (the DB constraint, for real) |
| `PortfolioAnalysisControllerContractTest` | swagger-request-validator-mockmvc | both endpoints against `openapi.yaml`; 202/404/409 shapes |
| `PortfolioAnalysisOnCreationIT` | Testcontainers, full context | Portfolio creation → PENDING row exists immediately, create response unaffected in shape/timing |
| Frontend `portfolio-analysis.service.spec.ts` + `portfolio-detail.page.spec.ts` | Karma/Jasmine | polling start/stop, all 4 UI states (`NONE` treated as not-yet-analysed / `PENDING`/`RUNNING` / `COMPLETED` / `FAILED`) |
| E2E-001/002/003 | Playwright, `openai-stub` | automatic analysis, manual re-analysis (A1 preserved), provider failure — exactly FD005 §42–44 |

No test requires network access to `api.openai.com`; `./mvnw -B clean verify` and `./e2e.sh` both
run fully offline (FR-053, FR-054).
