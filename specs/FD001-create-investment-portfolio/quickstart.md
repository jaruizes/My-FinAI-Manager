# Quickstart & Validation: Create Investment Portfolio (FD001)

Runnable scenarios that prove FD001 works end to end. This is a **validation guide** — code lives
in `tasks.md` / the implementation phase. It extends the EN001 platform; no new prerequisites.

## Prerequisites

Same as EN001 (`specs/EN001-bootstrap-platform/quickstart.md`): Docker running, JDK 21, Node ≥
20.19 / 22.12, `curl` + `jq`. Then:

```bash
cd implementation/platform
cp infrastructure/local/.env.example infrastructure/local/.env   # if not already done
```

---

## A. Backend automated tests — `mvn verify`

```bash
cd implementation/platform/backend/core-service
mvn clean verify
```

Expected (all green; BUILD SUCCESS):

| Suite | Proves |
|---|---|
| `portfolio.domain.*Test` (TDD unit) | Every business rule: BR-001…BR-005, BR-007, BR-008, BR-009, BR-010; duplicate `ticker+market` (BR-004); price > 0 (A3); future date rejected (A4); missing value ≠ 0 (FR-019). Aggregate collects **all** violations, not fail-fast (FR-024). |
| `CreatePortfolioServiceTest` (unit, mocked ports) | Default-Investor resolution (FR-030); idempotency-key replay returns the existing portfolio, does not re-create (FR-031a); `PortfolioCreated` / `PositionAdded` recorded (FR-032). |
| `CreatePortfolioControllerContractTest` (`@WebMvcTest`) | Request/response and the `400` `ValidationProblem` body conform to `contracts/openapi/openapi.yaml` (AR-011). Field paths in `errors[]` match. |
| `JdbcPortfolioRepositoryIT` (Testcontainers PostgreSQL) | `V2__portfolio.sql` applies + seeds the default Investor; aggregate persists atomically; forced failure rolls back → **no** partial portfolio, **no** orphan positions (FR-023, SC-010); `UNIQUE(portfolio_id,ticker,market)` and `UNIQUE(idempotency_key)` enforced. |
| `CreatePortfolioIT` + per-story acceptance ITs (`CreatePortfolioAcceptanceIT`, `…MultiPositionIT`, `…ValidationIT`, `…OptionalDataIT`) (Testcontainers, full slice HTTP→DB) | AC-001…AC-008 through the real controller + real DB; `NUMERIC` round-trips quantity/price with no precision loss (SC-007). |
| `CreatePortfolioIdempotencyIT` (Testcontainers) | Same `Idempotency-Key` twice → `201` then `200` replay, exactly one `portfolio` row (FR-031a, SC-011). |
| `HexagonalArchitectureRulesTest` (ArchUnit) | `portfolio.domain` / `portfolio.application` depend on no Spring / JDBC / HTTP; inbound adapters don't depend on outbound; rules now apply to the `portfolio` package too. |
| JaCoCo `check` | ≥ 90% overall (DoD §5); `portfolio.domain` near 100% branch. |

---

## B. Frontend automated tests — `npm test`

```bash
cd implementation/platform/frontend/web
npm test -- --watch=false --browsers=ChromeHeadless
```

Expected: `CreatePortfolioPageComponent`, `AddPositionDialogComponent`,
`PositionDraftListComponent`, and `PortfolioApiService` specs pass — including: Save disabled while
a request is in flight (FR-031a); a `400` problem body maps `errors[]` onto the right form
controls (FR-024); a `503` shows the "couldn't save, try again" message and keeps the draft
(FR-023a); the confirmation message on success (FR-031); the draft list add / remove / edit
(FR-026…028).

---

## C. Manual end-to-end (running platform)

```bash
cd implementation/platform && ./start.sh
```

### C1 — Create a portfolio with one position  ·  AC-001, SC-001, SC-006, SC-012

- Open `http://localhost:4200`, use the **Portfolios** entry → the Create Portfolio screen.
- Name `Long-Term Growth`; **Add Position** → `ASML`, market `XAMS`, quantity `12`, currency `EUR`; confirm; **Save**.
- Expect a "Portfolio created successfully" confirmation.
- Verify persisted exactly as entered:

```bash
docker compose -f infrastructure/local/compose.yaml exec -T postgres \
  psql -U finai -d finai -c \
  "select p.name, p.status, i.display_name as owner,
          pos.ticker, pos.market, pos.quantity, pos.currency,
          pos.initial_purchase_date, pos.average_purchase_price
   from portfolio p join investor i on i.id=p.investor_id
   join position pos on pos.portfolio_id=p.id order by p.created_at desc limit 5;"
```

Expect: one `portfolio` (`status=ACTIVE`, `owner='Default Investor'`), one `position`
(`quantity=12`, `currency=EUR`, `initial_purchase_date` and `average_purchase_price` **NULL** —
not 0).

### C2 — Multiple positions, same ticker different market  ·  AC-002, BR-003

- New portfolio `Dividend`; add `ASML/XAMS qty 3 EUR` and `ASML/XNAS qty 5 USD`; Save.
- Both persist as distinct positions under one portfolio.

### C3 — Validation blocks the save  ·  AC-003, AC-004, AC-005, SC-003, SC-008

- Empty name → Save blocked, "name is required" near the name field; DB row count unchanged.
- Position with quantity `0` → not accepted, "valid quantity required".
- Add `ASML/XAMS` twice → second rejected, "already exists in this portfolio".
- Confirm via `psql` that **no** new `portfolio` / `position` rows were written for these attempts.

### C4 — Optional acquisition data  ·  AC-006, AC-007, AC-008, SC-004, SC-005

- Position with a blank purchase date and blank price → portfolio saves; both columns NULL.
- Position with `averagePurchasePrice = 812.50`, currency `EUR` → stored `812.50`, price currency `EUR` (= position currency).
- Position with `averagePurchasePrice = -5` → rejected, "must be greater than zero" (A3).
- Position with a future `initialPurchaseDate` → rejected (A4).

### C5 — Persistence failure  ·  FR-023a, SC-010

```bash
docker compose -f infrastructure/local/compose.yaml stop postgres
# In the UI: fill a valid portfolio, Save.
```

Expect: a non-technical "couldn't save, please try again" message; the name + positions stay on
screen; no automatic retry.

```bash
docker compose -f infrastructure/local/compose.yaml start postgres
# Save again → succeeds; exactly one portfolio persisted (psql count).
```

### C6 — Accidental double submit  ·  FR-031a, SC-011

```bash
KEY=$(uuidgen)
BODY='{"name":"Idem Test","positions":[{"ticker":"MSFT","market":"XNAS","quantity":"1","currency":"USD"}]}'
curl -s -o /dev/null -w '%{http_code}\n' -XPOST localhost:8080/api/portfolios \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "$BODY"   # 201
curl -s -o /dev/null -w '%{http_code}\n' -XPOST localhost:8080/api/portfolios \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "$BODY"   # 200 (replayed)
docker compose -f infrastructure/local/compose.yaml exec -T postgres \
  psql -U finai -d finai -tAc "select count(*) from portfolio where name='Idem Test';"   # 1
```

### C7 — Contract shape  ·  AR-010/AR-011

```bash
curl -s -XPOST localhost:8080/api/portfolios -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"name":"","positions":[]}' | jq
```

Expect `application/problem+json`, `type: /problems/portfolio-validation`, `status: 400`, and an
`errors[]` with `{field, code, message}` for `name` (REQUIRED) and `positions` (AT_LEAST_ONE).
No stack trace, no SQL, no framework class names.

```bash
cd implementation/platform && ./stop.sh
```

---

## D. Browser end-to-end — E2E-001  ·  FR-036, FR-037, SC-013, FD001 §13 (mandatory closure gate)

Runs the critical creation journey through a real browser against the fully containerized platform,
using the `EN002` foundation. No mocked frontend↔backend, no mocked persistence.

```bash
cd implementation/platform
./e2e.sh                       # builds images, isolated stack, Playwright (Chromium), tears down
./e2e.sh -g "create"           # just FD001-create-portfolio.spec.ts
```

**Expected**: `implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts` passes —
`goto('/portfolios/new')` → enter name → Add position (ASML/XAMS/1/EUR) → Save → the
"Portfolio created successfully." confirmation is shown, and no page-level runtime error fired.
`./e2e.sh` exits `0`.

> **Result (2026-09-01)**: `./e2e.sh` → `2 passed` (`FD001-create-portfolio.spec.ts` +
> `platform-smoke.spec.ts`), exit `0`. This run also caught and fixed a real defect the unit tests
> missed: `crypto.randomUUID()` is undefined in a non-secure (plain-HTTP) context, so the Save
> handler threw before issuing the request — replaced with a `newIdempotencyKey()` fallback
> (`src/app/portfolio/idempotency-key.ts`). Exactly the class of integration bug E2E-001 exists to
> catch.

---

## Acceptance / Success Criteria coverage

| Item | Covered by |
|---|---|
| AC-001…AC-002 | A (`CreatePortfolioIT`), C1, C2 |
| AC-003…AC-005 | A (domain tests, contract test), C3, C7 |
| AC-006…AC-008 | A (domain + IT), C4 |
| BR-001…BR-010 | A (`portfolio.domain` unit tests) |
| FR-023 / FR-023a (atomic + failure UX) | A (`JdbcPortfolioRepositoryIT`), B, C5, SC-010 |
| FR-031a / SC-011 (double submit → one portfolio) | A (`CreatePortfolioServiceTest`, `CreatePortfolioIdempotencyIT`), B, C6 |
| FR-030 / SC-012 (owner) | A (IT), C1 |
| SC-001 | C1 (timed) |
| SC-002 | A (`CreatePortfolioIT` with 10+ positions) |
| SC-003, SC-008 | A, C3, C7 |
| SC-004, SC-005 | A, C4 |
| SC-006, SC-007 | A (`CreatePortfolioIT`), C1 |
| SC-009 | A — each AC has a named automated test; **D** — critical journey has E2E evidence |
| **SC-013 / FR-036 / FR-037 (E2E-001)** | **D** — `FD001-create-portfolio.spec.ts` passes via `./e2e.sh` (exit 0); real browser → nginx → `core-service` → PostgreSQL, no mocks |
| VC-009-style "no business behaviour beyond scope" | ArchUnit + review: no list/view/edit/delete endpoint, no valuation/risk code |
