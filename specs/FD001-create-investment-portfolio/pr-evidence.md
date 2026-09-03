# FD001 — Create Investment Portfolio · PR Evidence & Definition of Done

Prepared by the `/speckit-implement` run. Assembled per
`product/engineering/definition-of-done.md` → *Minimum Pull Request Evidence* and the
*Deterministic Domain Change + API Change + Persistence Change* checklists.

## What / why / trace

| Question | Answer |
|---|---|
| What requirement does this implement? | FD001 — Create Investment Portfolio (37 FRs, 12 SCs, US1–US5). One vertical slice over `POST /api/portfolios`. |
| Which spec / tasks does it trace to? | `specs/FD001-create-investment-portfolio/{spec,plan,research,data-model,quickstart,tasks}.md`; contract `contracts/openapi/portfolios.create.yaml` → merged into `implementation/platform/contracts/openapi/openapi.yaml`. **69** tasks in `tasks.md` (T001–T066 initial implementation; **T067–T068 = E2E-001**, added after FD001 §13 was revised once `EN002` landed). |
| What changed? | New `core-service` `portfolio` module (hexagonal: `domain` / `application` / `adapter.in.web` / `adapter.out.persistence` / `bootstrap`); Flyway `V2__portfolio.sql` (`investor` / `portfolio` / `position`); OpenAPI operation + `ValidationProblem`; Angular Create Portfolio screen + `Portfolios` nav entry + `provideHttpClient`. **Revision 2026-09-01**: browser E2E test `e2e/tests/FD001-create-portfolio.spec.ts` (**E2E-001**) + `src/app/portfolio/idempotency-key.ts` (non-secure-context fallback the E2E test surfaced). |
| Why this design? | research.md D1–D10: raw input carried to the domain so it owns validation and collects **all** violations in one pass; client-supplied idempotency key + `NOT NULL UNIQUE` column; RFC 9457 problem+json; value objects to avoid primitive obsession; business events as structured logs (no messaging); atomic aggregate write via `TransactionTemplate`. |
| Architecture boundaries affected? | `domain` + `application` stay framework-free (ArchUnit `HexagonalArchitectureRulesTest`, now generalized to every `com.myfinaimanager.core` capability package — 5 rules green). Single `core-service` deployable preserved (ADR-001). Data ownership: the `portfolio` module owns all three tables. |
| ADRs required? | **ADR-002 — Interim Unauthenticated Write Access** (drafted by AI; **Status must be human-approved before merge**). No other ADR: no new deployable, technology, messaging, or persistence tech. |

## Deviations from the plan (surface for review)

1. **OpenAPI dialect 3.0.3, not 3.1** (tasks.md T001 said 3.1). `swagger-request-validator` 2.44.x
   mis-validates a 3.1 `type: [...]` union on a `SIMPLE` header parameter (`Idempotency-Key`),
   treating the value as JSON. 3.0.3 (`nullable: true`, flat `ValidationProblem`) expresses
   everything FD001 needs and keeps the contract test meaningful. Recorded in research.md D9;
   the spec contract fragment was updated to match. Safe, reversible.
2. **`PostgresContainerSupport` → singleton container** (test infra only). One shared PostgreSQL
   for the whole test run instead of one container per `*IT` class — the per-class lifecycle
   exhausted the local 2-CPU/4-GiB Docker VM once six IT classes existed. Standard Testcontainers
   pattern.
3. **`containers.md` edit prepared but not committed** — human-governed `product/` file (T063,
   constitution I). The working-tree edit moves Portfolio (creation only) into "Current Realized
   State". Maintainer to confirm.

## How it was tested

### Backend — `mvn -B clean verify` (green)

| Suite | Count | Notes |
|---|---|---|
| `ValueObjectsTest` | 15 | value-object invariants (BR-001/003/005/006, FR-025) |
| `PortfolioTest` | 26 | every business rule + "collect all violations" + canonical codes (FR-024) |
| `CreatePortfolioServiceTest` | 7 | idempotency replay, validation/persistence propagation, structured `PortfolioCreated`/`PositionAdded` (log capture) |
| `HexagonalArchitectureRulesTest` | 5 | dependency direction / no framework in domain+application |
| `CreatePortfolioControllerContractTest` | 4 | 201 / 200-replay / 400 `ValidationProblem` / 503 all conform to `openapi.yaml` |
| `JdbcDefaultInvestorProviderIT` | 1 | Testcontainers — seed row |
| `JdbcPortfolioRepositoryIT` | 4 | Testcontainers — atomic aggregate, rollback (SC-010), idempotency race, constraints, `NUMERIC` precision (SC-007) |
| `CreatePortfolioIT` | 2 | Testcontainers — full slice, migration + seed |
| `CreatePortfolioAcceptanceIT` | 1 | AC-001 stored verbatim, optionals NULL (SC-006) |
| `CreatePortfolioMultiPositionIT` | 3 | AC-002, BR-003, SC-002 (≥10 positions in budget) |
| `CreatePortfolioValidationIT` | 7 | AC-003/004/005, BR-002, multi-violation (SC-003) |
| `CreatePortfolioOptionalDataIT` | 4 | AC-006/007/008 (SC-004/005), A3, A4 |
| `CreatePortfolioIdempotencyIT` | 2 | FR-031a / SC-011 |
| `PlatformIntegrationIT` | 3 | EN001 platform boot (regression) |
| **Total** | **84** | |

Coverage gate: **JaCoCo LINE 98.0 %, BRANCH 94.6 %** (bundle) — gate is ≥ 90 % line **and**
branch (DoD §5). `bootstrap/**` + `CoreServiceApplication` excluded (wiring only).

### Frontend — `ng build` + `ng test` (green)

`ng build` succeeds. `ng test --watch=false --browsers=ChromeHeadless` → **29 specs pass**
(`portfolio-api.service`, `add-position.dialog`, `position-draft-list`, `create-portfolio.page`
covering US1 happy path + in-flight Save disabled, US2 multi-submit, US3 error mapping + Save
block, US5 remove/edit draft, Polish 503 retains draft + reuses idempotency key; `idempotency-key`
util incl. the non-secure-context fallback; plus the updated `app.component` nav assertion).

### End-to-end — API level (running platform, `curl` + `psql`)

Local PostgreSQL (compose) + backend. Verified: `201` create returns the portfolio;
same `Idempotency-Key` replay → `200` + `Idempotency-Replayed: true`; blank name + `quantity=0`
→ `400` `application/problem+json` with `errors[]` = `name/REQUIRED` + `positions[0].quantity/NOT_POSITIVE`;
`averagePurchasePrice: "812.50"` stored exactly as `812.50` with currency `EUR`; all portfolios
owned by the seeded `Default Investor` (`00000000-0000-0000-0000-000000000001`). Structured ECS
JSON log line emitted: `"event":"PortfolioCreated"` + one `"event":"PositionAdded"` per position,
no secrets / amounts / PII.

### End-to-end — browser (**E2E-001** — mandatory closure gate · FR-036 / SC-013 · added 2026-09-01)

`implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts` (Playwright, Chromium) drives
the real journey **browser → nginx → `core-service` → PostgreSQL**, no mocks, against the
containerized platform via `EN002`'s `./e2e.sh`.

- `./e2e.sh` → **`2 passed`** (`FD001-create-portfolio.spec.ts` + `platform-smoke.spec.ts`), exit `0`.
- The test starts from `/portfolios/new`, enters a synthetic name, adds one Position (ASML/XAMS/1/EUR)
  via the dialog, Saves, and asserts the "Portfolio created successfully." confirmation and that
  no page-level runtime error fired.
- **Defect caught & fixed by this test**: `crypto.randomUUID()` is `undefined` in a non-secure
  (plain-HTTP) browsing context, so the Save handler threw a `TypeError` before issuing the POST
  and the button hung on "Saving…". Fixed with `src/app/portfolio/idempotency-key.ts`
  (`newIdempotencyKey()` — `crypto.getRandomValues` / `Math.random` fallback, RFC 4122 v4 shape);
  `portfolio-api.service.ts` and `create-portfolio.page.ts` now use it. This is exactly the
  frontend↔backend integration class of bug E2E-001 exists to catch (the Karma tests pass because
  Karma runs on `localhost`, a secure context).

## DoD checklist

| Item | Status | Evidence |
|---|---|---|
| Approved requirement / scope | PASS | FD001; no listing/view/edit/valuation/risk/auth/messaging added (spec §3, tasks.md Notes) |
| TDD for deterministic domain | PASS | value objects, `Position`, `Portfolio` test-first; 41 domain unit tests |
| Unit / domain tests | PASS | 57 unit tests |
| ≥ 90 % coverage gate | PASS | JaCoCo 98.0 % line / 94.6 % branch, `check` bound to `verify` |
| Architecture compliance | PASS | ArchUnit 5 rules green; single `core-service`; module owns its tables |
| Integration tests for persistence | PASS | 8 Testcontainers IT classes (PostgreSQL), never mocked |
| **End-to-end test (FD001 §13 E2E-001 — closure gate)** | **PASS** | `e2e/tests/FD001-create-portfolio.spec.ts` passes via `./e2e.sh` (exit 0); real browser → nginx → `core-service` → PostgreSQL, no mocks (FR-036, FR-037, SC-013) |
| OpenAPI updated | PASS | `openapi.yaml` `POST /api/portfolios` + schemas, `info.version 0.1.0` |
| Contract validation | PASS | `CreatePortfolioControllerContractTest` (swagger-request-validator) |
| Error model | PASS | RFC 9457 `application/problem+json`; stable `type`; no stack traces / SQL / framework names (DR-020) |
| Migration + migration test | PASS | `V2__portfolio.sql`; `CreatePortfolioIT` asserts history row + seed; `JdbcPortfolioRepositoryIT` asserts constraints |
| Data ownership review | PASS | `portfolio` module is sole owner of `investor` / `portfolio` / `position` |
| Compatibility review | PASS | additive: first operation, `paths` was empty |
| Secrets / synthetic data | PASS | no secrets committed; `.env` git-ignored; synthetic tickers only; logs checked (T061) |
| Observability | PASS | structured ECS JSON `PortfolioCreated` / `PositionAdded`; no `System.out` / `printStackTrace` |
| Documentation current | PASS | `implementation/platform/README.md` capability row; research.md D9; **`containers.md` prepared, pending maintainer** |
| ADR | **PENDING HUMAN APPROVAL** | ADR-002 must be approved before merge |
| Platform lifecycle | PASS | `start.sh` / `stop.sh` / `e2e.sh` valid (containerized since `EN002`); no alternative entry point |

## Outstanding before FD001 can be closed

1. **ADR-002 — Interim Unauthenticated Write Access**: Status must be human-approved (its "Human Approval" checklist completed).
2. **`product/architecture/diagrams/containers.md`** "Current Realized State" edit: prepared in the working tree; maintainer must confirm before commit (constitution I).
3. **FD001 Feature Definition §16**: the item *"[ ] E2E-001 exists and passes against the containerized platform before final feature closure"* — E2E-001 now exists and passes (`./e2e.sh` exit 0), so this is **satisfiable**; the **maintainer checks that box** (human-governed file).

## Accepted security debt

`POST /api/portfolios` is unauthenticated (spec A11). Accepted for FD001 under
**ADR-002** — single default Investor, non-production only, `investor_id` non-null, no design
obstacle to adding auth later. **Supersede trigger:** the identity/auth enabler, or any
non-local deployment. ADR-002 requires human approval before this PR merges.
