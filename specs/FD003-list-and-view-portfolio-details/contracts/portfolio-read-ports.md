# Contract — portfolio read ports (FD003)

In-process interfaces for the FD003 read path. No new outbound port — the existing
`PortfolioRepository` gains read methods. No HTTP between modules.

```text
PortfolioQueryController (GET /api/portfolios [/ {portfolioId}])
  └─ business.PortfolioQueryUseCase / PortfolioQueryService
       ├─ domain.ports.PortfolioRepository            (+2 read methods)
       │    └─ infrastructure.persistence.PortfolioPersistenceAdapter (readOnly)
       │         └─ PortfolioJpaRepository            (+2 derived @EntityGraph queries)
       └─ domain.ports.DefaultInvestorProvider        (existing — the seeded Default Investor)
```

---

## C1 — `PortfolioRepository` (added read methods)

```java
/**
 * Every portfolio owned by {@code investorId}, ordered most-recently-created first
 * (createdAt desc, id desc as a deterministic tiebreak — FD003 A3). Each returned aggregate has
 * its Positions loaded. Read-only.
 */
List<Portfolio> findAllByInvestor(InvestorId investorId);

/**
 * The portfolio with this {@code id} <strong>iff it belongs to {@code investorId}</strong>, with
 * its Positions loaded; empty for an unknown id or another investor's portfolio. Read-only.
 */
Optional<Portfolio> findByIdForInvestor(PortfolioId id, InvestorId investorId);
```

**Invariants**

| # | Invariant |
|---|---|
| P1 | Pure reads — no INSERT/UPDATE/DELETE, no sequence/counter touch, no business event. |
| P2 | Executed in a **read-only** transaction (`PortfolioPersistenceAdapter.readOnlyTemplate`). |
| P3 | Investor scoping is in the query — a portfolio that is not `investorId`'s is never returned (not returned-then-filtered). |
| P4 | Each returned `Portfolio` is the full aggregate: name, status, createdAt, and **all and only** its own Positions. |
| P5 | `findAllByInvestor` order is total and stable for a fixed DB state. |
| P6 | Deterministic; never contacts an external provider. |

**Consumers**: `PortfolioQueryService.list()` / `.view(id)`.

---

## C2 — `PortfolioQueryUseCase` (business)

```java
public interface PortfolioQueryUseCase {

    /** The current Investor's portfolios, newest first (possibly empty). */
    List<Portfolio> list();

    /**
     * The current Investor's portfolio with this id.
     * @throws PortfolioNotFoundException if no such portfolio belongs to the current Investor
     */
    Portfolio view(PortfolioId id);
}
```

**Invariants**

| # | Invariant |
|---|---|
| U1 | `list()` resolves the Investor via `DefaultInvestorProvider` and delegates to `findAllByInvestor` — no filtering, sorting, or shaping in the service. |
| U2 | `view(id)` delegates to `findByIdForInvestor` and maps empty → `PortfolioNotFoundException(id)`. |
| U3 | Neither method calls `PortfolioRepository.save` or any mutator (SC-005). |
| U4 | No idempotency key, no clock — this is a query. |

---

## C3 — `PortfolioNotFoundException` → HTTP

| Aspect | Value |
|---|---|
| Java type | `com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException extends RuntimeException` |
| Carries | the requested portfolio id (String) — for the problem `instance` |
| HTTP status | `404` |
| `type` | `/problems/portfolio-not-found` |
| `title` | `Portfolio not found` |
| `instance` | `/api/portfolios/{id}` |
| Body media type | `application/problem+json` (RFC 9457) |
| Mapped by | `PortfolioExceptionHandler` — `assignableTypes` widened to include `PortfolioQueryController`; one new `@ExceptionHandler`. The FD001 `400` / `503` handlers are unchanged. |

### Malformed id (not a UUID)

A `{portfolioId}` path segment that does not parse as a UUID is **not** a `PortfolioNotFoundException`.
The `PortfolioQueryController` types the path variable as `java.util.UUID`, so Spring raises
`MethodArgumentTypeMismatchException` and returns its default **`400`** before the controller body
runs — no lookup, no custom handler, no guaranteed problem `type`. This keeps "unknown resource"
(`404`, a real id that isn't yours) distinct from "not a resource reference" (`400`).

---

## C4 — REST responses

| Operation | Success | Body |
|---|---|---|
| `GET /api/portfolios` | `200` | `array` of `PortfolioSummary` `{ id, name, positionCount }` — newest first; `[]` when none. `PortfolioSummaryMapper` builds each from the aggregate (`positions().size()`). **No** `status` / `createdAt` / provider / persistence field. |
| `GET /api/portfolios/{portfolioId}` | `200` | the existing **`Portfolio`** schema (`id, name, status, positions[], createdAt`) via the existing `PortfolioResponseMapper`. `404` `Problem` on a well-formed non-Investor id; **`400`** (Spring default, status only) on a `{portfolioId}` that is not a UUID. |

**Contract tests** (`@WebMvcTest(PortfolioQueryController.class)` + `swagger-request-validator`):
list `200` (+ empty `[]`), detail `200` conforms to `openapi.yaml`, unknown id `404` problem
conforms, a non-UUID `{portfolioId}` → `400` (assert status only); no persistence/provider field in
any body. Plus an FD001 `CreatePortfolioControllerContractTest`
re-run (the widened advice must not change the `400` / `503` bodies).
