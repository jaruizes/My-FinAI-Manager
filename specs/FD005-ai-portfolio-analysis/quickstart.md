# Quickstart — Validate FD005 (AI Portfolio Analysis)

Run guide proving the feature end to end. Details: [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/). No implementation code here.

**Prerequisites**: colima/Docker (`DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock`,
`TESTCONTAINERS_RYUK_DISABLED=true`); Node 20.19.1 + `CHROME_BIN` for `ng test`; repo root =
`implementation/platform/`. FD001–FD004/EN004–EN006 implemented + verified.

---

## A. Backend build, tests, coverage, architecture (SC-006)

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify        # OFFLINE — no api.openai.com
```

**Expect** BUILD SUCCESS; JaCoCo bundle ≥ 90% line & branch; `StandardArchitectureRulesTest` green
with the 4 new `portfolioanalysis`↔`portfolio`/`ai` confinement rules + the widened
`only_provider_client_packages_use_restclient`. New/updated tests all green:

- `PortfolioAnalysisContextBuilderTest` — full/partial/insufficient valuation renders correct
  deterministic text; `sufficient=false` for `FAILED`/absent/no-valued-position snapshots.
- `PortfolioAnalysisRequestServiceTest` — creates `PENDING`; rejects a duplicate open request
  (pre-check **and** the DB-constraint-violation race path); triggers the worker exactly once.
- `PortfolioAnalysisWorkerTest` — `RUNNING→COMPLETED` happy path; `RUNNING→FAILED` for every
  `AnalysisFailedException` reason **and** for an unexpected `RuntimeException` (never stuck).
- `PortfolioContextGatewayAdapterTest` — maps FD004 valuation states correctly; the sole class
  touching `portfolio.*`.
- `PortfolioAnalysisAiAdapterTest` — builds the right `AiRequest`; maps every EN006 exception to a
  `FailureReason`; the sole class touching `ai.*`.
- `OpenAiModelAdapterTest`/`OpenAiChatMapperTest` — `MockRestServiceServer`: auth header, model,
  `response_format`; usage/cost mapping; every HTTP error status; blank-key no-call path.
- EN006 regression: `AiInvocationPolicyTest`, `PromptServiceTest`, `ClasspathPromptRepositoryTest`
  — per-task routing + prompt-versioning changes; **`diagnostic` task behavior unchanged**.
- `PortfolioAnalysisPersistenceAdapterIT` (Testcontainers) — round-trip incl. insights/risks;
  **two concurrent inserts for the same portfolio ⇒ exactly one succeeds**.
- `PortfolioAnalysisControllerContractTest` — both endpoints validated against `openapi.yaml`.
- `PortfolioAnalysisOnCreationIT` (Testcontainers, full context) — Portfolio creation → a `PENDING`
  row exists immediately; create-response timing/shape unaffected.

**Grep checks**:
```bash
grep -rln "com.openai\|OpenAiClient\b" src/main/java/com/myfinaimanager/core/portfolioanalysis/ \
  && echo "LEAK — OpenAI type outside ai module" || echo "OK"
grep -rln "OPENAI_API_KEY" src/ .env* 2>/dev/null | grep -v ".env.example" && echo "CHECK" || echo "OK — no committed key"
git diff --stat -- '**/openapi.yaml'   # non-empty — exactly the 2 new FD005 paths/schemas expected
```

## B. Frontend tests (SC-007)

```bash
cd implementation/platform/frontend/web
nvm use 20.19.1 && CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npm test
```
**Expect** all prior suites green + new: `portfolio-analysis.service.spec.ts` (polling
start/stop/timeout), `portfolio-detail.page.spec.ts` additions (NONE/PENDING/RUNNING/COMPLETED/FAILED
rendering, "Run analysis again" enabled/disabled).

## C. Runtime smoke — local platform (no OpenAI key)

```bash
cd implementation/platform
./start.sh
# create a portfolio via the UI or curl (see FD001 quickstart), then:
curl -s http://localhost:8080/api/portfolios/{id}/analysis/latest | python3 -m json.tool
# Expect: status PENDING, briefly, then FAILED (NOT_CONFIGURED) once the async worker runs —
# OPENAI_API_KEY is blank by default; Portfolio itself remains fully valid and visible.
./stop.sh
```

## D. Runtime — real OpenAI key (optional)

```bash
OPENAI_API_KEY=sk-... ./start.sh
# create a portfolio, poll GET .../analysis/latest until COMPLETED, inspect the real analysis content.
```
`compose.yaml` forwards `OPENAI_API_KEY`/`OPENAI_BASE_URL`/`OPENAI_MODEL` exactly like
`FINNHUB_API_KEY`. Never put a real key in `.env.example` or commit it.

## E. E2E — stubbed OpenAI boundary (SC-007)

```bash
./e2e.sh
```
**Expect** all suites green, incl.:
- `fd005-automatic-analysis.spec.ts` (E2E-001) — create → "being analysed" → stubbed OpenAI
  response completes → polling detects `COMPLETED` → content rendered.
- `fd005-manual-reanalysis.spec.ts` (E2E-002) — A1 displayed → "Run analysis again" → in-progress →
  A2 replaces A1 in the view → A1 still exists in persistence (verified via the API) → A2 has newer
  timestamps.
- `fd005-provider-failure.spec.ts` (E2E-003) — stub configured to fail → Portfolio remains valid →
  latest analysis reaches `FAILED` → controlled failed state shown → re-analysis still possible →
  no provider payload/sensitive data displayed.

`e2e/openai-stub/` (new, compose-e2e-only) serves a deterministic `/v1/chat/completions` fixture
matching FD005 §42's example content; `OPENAI_BASE_URL` in `compose.e2e.yaml` points the real
`OpenAiModelAdapter` at it — the real adapter/mapper/error-translation code runs, only the network
boundary is controlled.

## F. Definition-of-Done quick gate

- [ ] Sections A–E all pass.
- [ ] `git diff` shows exactly: one new Flyway migration (`V5__portfolio_analysis.sql`), the 2 new
  `openapi.yaml` paths + `PortfolioAnalysis`/`RequestedPortfolioAnalysis` schemas, the documented
  EN006 extension files, the new `portfolioanalysis` module, the frontend additions, and the new
  `openai-stub` E2E service — nothing else.
- [ ] No `product/` file was silently edited (FD005's own governance edit was explicitly
  human-directed — see spec.md Clarifications).
- [ ] 100% of FD005's BR-001…016 and AC-001…014 have executable or E2E evidence (spec.md
  Traceability cross-checked against this quickstart).
