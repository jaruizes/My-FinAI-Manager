# FD003 — Definition of Done checklist

Evaluated against `product/engineering/definition-of-done.md`. Evidence from the
`./mvnw -B clean verify` / `ng test` / `./e2e.sh` run recorded in `pr-evidence.md` and the
`quickstart.md` coverage tables. Change type: **Deterministic Domain Change + API Change**
(read-only; **not** a Persistence Change — no migration).

| # | Item | Status | Evidence / note |
|---|---|---|---|
| **1. Product & Specification** | | | |
| 1.1 | Traceable to an approved Feature Definition | PASS | FD003 §19 signed jaruiz 2026-09-03; `spec.md` Traceability table maps every FR to an FD003 §/BR/AC |
| 1.2 | Formal spec approved where SDD requires | PASS | `spec.md` + `checklists/requirements.md` 16/16; `/speckit.analyze` A1–A5 remediated |
| 1.3 | All behavior within approved scope | PASS | read list + read detail only; `quickstart.md` §G scope review |
| 1.4 | No new business requirement introduced | PASS | list order (A3), `404`/`400` semantics (A7) are the only decisions; all in OD table, human-confirmed |
| 1.5 | Acceptance scenarios implemented | PASS | AC-001…AC-007 → `quickstart.md` coverage table; E2E-001 + E2E-002 green |
| 1.6 | No speculative out-of-scope behavior | PASS | no sort/filter/search/pagination, no write, no new event (FR-032) |
| **2. Architecture** | | | |
| 2.1–2.2 | Complies with `architecture.md` / `architecture-rules.md` | PASS | vertical read slice of `portfolio`; ArchUnit 14/14 |
| 2.3 | Technology policy | PASS | no new dependency (`pom.xml` / `package.json` / e2e `package.json` untouched) |
| 2.4–2.5 | Hexagonal boundaries / domain free of infrastructure | PASS | read rule in `business` behind `domain.ports`; `domain` framework-free; ArchUnit `domain_has_no_framework_dependencies` |
| 2.6–2.7 | Module ownership clear / no cross-module persistence access | PASS | all FD003 code inside `portfolio`; reads its own `portfolio` / `position` tables only |
| 2.8 | No unapproved technology | PASS | Spring Data JPA / MockMvc / Playwright — all already in use |
| 2.9 | ADR for significant architecture change | N/A | two read endpoints + two views in the existing module — no topology/persistence/messaging/security change |
| 2.10 | Architecture diagrams updated | N/A | approved architecture unchanged |
| **3. Code Quality** | | | |
| 3.1–3.2 | Readable, domain terminology, cohesive | PASS | `PortfolioQueryService` / `…Controller` / `…SummaryMapper` single-purpose |
| 3.3 | No speculative abstraction | PASS | reused `PortfolioRepository` (no new port), reused `Portfolio` schema (no new DTO) |
| 3.4 | Safe numeric representation | PASS | decimals stay strings end-to-end (reused `PortfolioResponseMapper`, verbatim in the detail table) |
| 3.5 | Optional/unknown states intentional | PASS | `ListState` / `DetailState` unions; `initialPurchaseDate` / `averagePurchasePrice` shown only when present (`—` otherwise, never `0`) |
| 3.6 | Errors explicit | PASS | `PortfolioNotFoundException` → `404` problem; `null` → FE error+Retry; `'not-found'` classified separately |
| 3.7 | Unused code/deps removed | PASS | none introduced |
| 3.8 | No unrelated refactoring | PASS | only the sidebar link + `home.component` host changed outside new files (both required by FD003 §17.1) |
| **4. Testing** | | | |
| 4.1 | TDD for deterministic logic | PASS | `PortfolioQueryServiceTest` and `PortfolioQueryControllerContractTest` written RED-first (T006, T007) before their implementations (T008) |
| 4.2 | Unit/domain tests | PASS | `PortfolioQueryServiceTest` (3); FE `portfolio-list` / `portfolio-detail` / `portfolio-query.service` specs (21) |
| 4.3 | Integration tests where infrastructure matters | PASS | `PortfolioPersistenceAdapterIT` +4 Testcontainers cases (ordering, investor scoping, not-found, 0-writes) |
| 4.4 | Contract tests for external interfaces | PASS | `PortfolioQueryControllerContractTest` (5, `swagger-request-validator` vs `openapi.yaml`); `CreatePortfolioControllerContractTest` re-run |
| 4.5 | Architecture tests | PASS | `StandardArchitectureRulesTest` 14/14 (new classes placed so no new rule needed) |
| 4.6 | E2E for critical journeys | PASS | E2E-001 + E2E-002 (both FD003 §16 mandatory gates) green in `./e2e.sh` |
| 4.7 | AI evaluation | N/A | no probabilistic behavior |
| 4.8 | Failure/edge cases | PASS | empty list `200 []`, unknown id `404`, non-UUID `400`, HTTP error → FE Retry, foreign-investor id → empty |
| 4.9 | All required tests pass | PASS | Surefire 123 + Failsafe 60 + `ng test` 58 + `./e2e.sh` 6 — 0 failures |
| **5. Coverage** | | | |
| 5.1 | ≥ 90 % overall | PASS | JaCoCo bundle **line 96.41 % · branch 91.01 %** — `jacoco:check` gate passed |
| 5.2 | Stronger critical-domain coverage | PASS | `PortfolioQueryService` fully covered by unit tests; adapter reads by IT |
| 5.3 | Exclusions justified | PASS | **no new exclusion** added (T033); the existing `config/**` + `entity/**` excludes are unchanged |
| 5.4 | Meaningful assertions | PASS | tests assert order, counts, scoping, problem `type`/`instance`, 0 writes, no leaked fields |
| **6. APIs & Contracts** | | | |
| 6.1 | OpenAPI defined | PASS | `openapi.yaml` +`listPortfolios` +`getPortfolio` +`PortfolioSummary` (3.0.3) |
| 6.2 | Implementation matches contract | PASS | contract test validates both request & response against `openapi.yaml` |
| 6.3 | Request validation | PASS | `{portfolioId}` typed `UUID` → framework `400` on a non-UUID (before any lookup) |
| 6.4 | Response schemas correct | PASS | list → `PortfolioSummary[]`; detail → reused `Portfolio`; contract test green incl. empty `[]` |
| 6.5 | Stable machine-readable error contract | PASS | RFC 9457 `application/problem+json`, `type = /problems/portfolio-not-found` |
| 6.6 | No provider/persistence leakage | PASS | contract test asserts no `investorId` / `idempotencyKey` / entity field in any body (FR-021) |
| 6.7 | Breaking changes explicit/approved | PASS | purely additive — new operations + new schema; existing operations untouched |
| 6.8–6.10 | Async interfaces | N/A | no event, no broker |
| **7. Persistence** | | | |
| 7.1 | Ownership explicit | PASS | `portfolio` module owns `portfolio` / `position` (unchanged) |
| 7.2–7.3 | Migrations included & tested | N/A | **no schema change** — read-only; no `V4` |
| 7.4 | Correctness constraints present | PASS | unchanged; reads rely on existing FK + indexes (`portfolio_investor_id_idx`) |
| 7.5 | Transaction boundaries intentional | PASS | both new reads run on the adapter's `readOnly` `TransactionTemplate` |
| 7.6 | No dual-write | PASS | no write at all |
| 7.7 | Specialized stores | N/A | PostgreSQL only |
| **8. External Integrations** | N/A | | FD003 contacts no external provider (deterministic reads only) |
| **9. AI / LLM** | N/A | | no LLM |
| **10. Security & Privacy** | | | |
| 10.1–10.2 | AuthN/AuthZ | N/A (ADR-002) | single seeded Default Investor; interim unauthenticated access — unchanged from FD001 |
| 10.3 | Investor-owned resources isolated | PASS | every read scoped in the query to `DefaultInvestorProvider.get()`; foreign id → empty → `404` (no existence leak); IT covers it |
| 10.4–10.5 | No secrets in source / logs | PASS | none added; structured logging unchanged |
| 10.6 | External providers get only what's needed | N/A | no external call |
| 10.7 | Inputs validated at trust boundary | PASS | `{portfolioId}` parsed as `UUID` at the controller edge |
| **11. Observability** | | | |
| 11.x | Structured logs / metrics / traces | PASS (unchanged) | uses the existing platform logging/actuator; no new external call to observe |
| **12. Resilience** | | | |
| 12.x | Timeouts / retry / idempotency | N/A | no external call; reads are naturally idempotent; FE offers a manual Retry on a recoverable error |
| 12.4 | Failure doesn't corrupt domain state | PASS | read-only — no state to corrupt |
| **13. Documentation** | | | |
| 13.1 | Non-obvious reasoning documented | PASS | Javadoc on the port methods / exception / service; component doc-comments state the states + read-only intent |
| 13.2 | Public contracts documented | PASS | `openapi.yaml` operation descriptions; `contracts/portfolio-read-ports.md` |
| 13.3 | Feature docs reflect approved behavior | PASS | `backend/core-service/README.md` + `implementation/platform/README.md` updated |
| 13.4 | ADRs added/updated | N/A | no significant architecture decision |
| 13.5 | Architecture diagrams | N/A | unchanged |
| 13.6 | Product-level docs updated for global changes | N/A | no global definition changed; **no `product/` edit** |
| 13.7 | No generated doc contradicts product docs | PASS | specs trace to FD003; no contradiction |
| **14. Repository Hygiene** | | | |
| 14.1–14.2 | No build artifacts / IDE files committed | PASS | `dist/` / `target/` git-ignored; `git status` clean of those |
| 14.3–14.5 | No private data / credentials; synthetic test data | PASS | E2E uses `uniquePortfolioName()` + catalogued tickers; unit/IT use fixed synthetic ids |
| 14.6 | Dependency changes intentional | PASS | none |
| **15. CI/CD** | N/A (no CI) | | validated locally: `./mvnw -B clean verify` (build + tests + coverage gate + ArchUnit + contract), `ng test`, `ng build`, `./e2e.sh` (container build + run) all green |
| **16. Review** | | | |
| 16.1–16.2 | Reviewed vs spec / architecture rules | PASS | this checklist + `pr-evidence.md` |
| 16.3 | AI-generated code critically reviewed | PASS | each file inspected against ADR-003 placement, read-only invariant, and the contract |
| 16.4 | Known limitations explicit | PASS | `COUNT`-projection optimization deferred (OD-FD003-1); `CreatePortfolioResponse` rename deferred (OD-FD003-4) — both recorded |
| 16.5 | No hidden blocker/TODO | PASS | none in the diff |
| **17. Product Acceptance** | | | |
| 17.1 | Evidence for every acceptance scenario | PASS | `quickstart.md` coverage tables; E2E-001/E2E-002 green |
| 17.2 | Behavior matches human intent | PASS | Home lists portfolios (name + count, newest first), empty state, row → read-only detail — exactly FD003 §1/§17 |
| 17.3 | Owner can understand what was built | PASS | `pr-evidence.md` |
| 17.4 | No undocumented assumptions | PASS | Assumptions A1–A12 in `spec.md`; ODs confirmed |

**Result: all applicable items PASS; N/A items explained. FD003 meets the Definition of Done
(pending human closure approval).**
