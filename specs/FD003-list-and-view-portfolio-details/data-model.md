# Data Model — FD003 List and View Portfolio Details

FD003 introduces **no new persisted entity** and **no schema migration**. It adds **read-only**
access to the `Portfolio` aggregate already persisted by FD001. This document records the read
model, the response shapes, and where each rule lives.

---

## 1. Persistence — unchanged

| Table | Owner | FD003 impact |
|---|---|---|
| `portfolio` (`id`, `investor_id`, `name`, `status`, `idempotency_key`, `created_at`) | `portfolio` (FD001) | **read only** — 2 new derived queries; no column, no migration |
| `position` (`id`, `portfolio_id`, `ticker`, `market`, `quantity`, `currency`, `initial_purchase_date`, `average_purchase_price`, `average_purchase_price_currency`) | `portfolio` (FD001) | **read only** — loaded via `@EntityGraph` for the count and the detail |
| `investor` | `portfolio` (FD001) | read only — the seeded Default Investor id (via `DefaultInvestorProvider`) |

No `V4`. `spring.jpa.hibernate.ddl-auto: none` unchanged. All FD003 database work runs in
**read-only** transactions (`PortfolioPersistenceAdapter.readOnlyTemplate`).

---

## 2. Domain — read additions (`portfolio` module)

### 2.1 `portfolio.domain.ports.PortfolioRepository` — 2 new read methods

```java
/** Every portfolio owned by this investor, most-recently-created first (FD003 A3). */
List<Portfolio> findAllByInvestor(InvestorId investorId);

/** The investor's portfolio with this id, or empty (unknown id or another investor's). */
Optional<Portfolio> findByIdForInvestor(PortfolioId id, InvestorId investorId);
```

- Return the **existing** `Portfolio` aggregate (with its `Position`s) — no new domain type.
- Read-only, side-effect-free.

### 2.2 `portfolio.domain.exceptions.PortfolioNotFoundException` — NEW

```java
public final class PortfolioNotFoundException extends RuntimeException {
    private final String portfolioId;   // the requested id, for the problem `instance`
    // ...
}
```
Thrown by `PortfolioQueryService.view(id)` when `findByIdForInvestor` is empty. Mapped to
`404 application/problem+json` (`/problems/portfolio-not-found`) by `PortfolioExceptionHandler`.

### 2.3 `Portfolio` / `Position` aggregate — unchanged

FD003 reads `Portfolio.name()`, `Portfolio.positions()` (for the count and the detail),
`Position.instrument().ticker()/market()`, `Position.quantity()`, `Position.currency()`,
`Position.initialPurchaseDate()`, `Position.averagePurchasePrice()`. It calls no mutator.

---

## 3. Business — `PortfolioQueryService` (NEW)

```java
public interface PortfolioQueryUseCase {
    List<Portfolio> list();            // FD003 US1/US2
    Portfolio view(PortfolioId id);    // FD003 US3 — throws PortfolioNotFoundException
}
```

`PortfolioQueryService` (`@Service`) — constructor `(PortfolioRepository, DefaultInvestorProvider)`:
- `list()` → `repository.findAllByInvestor(defaultInvestorProvider.get())`
- `view(id)` → `repository.findByIdForInvestor(id, defaultInvestorProvider.get())
                 .orElseThrow(() -> new PortfolioNotFoundException(id))`

No idempotency, no clock, no write. Not `@Transactional` at the service (the adapter's read-only
template wraps each query).

---

## 4. API response shapes (`infrastructure.api.rest`)

### 4.1 `PortfolioSummaryResponse` — NEW (list item)

```java
public record PortfolioSummaryResponse(String id, String name, int positionCount) {}
```
Mirrors the OpenAPI `PortfolioSummary` schema. Built by `PortfolioSummaryMapper` (`@Component`):
`new PortfolioSummaryResponse(p.id().toString(), p.name().value(), p.positions().size())`.
**No** Position detail, **no** `status` / `createdAt` / `investorId` / persistence field.

### 4.2 Detail response — REUSED

`GET /api/portfolios/{portfolioId}` → `CreatePortfolioResponse` (the OpenAPI **`Portfolio`**
schema: `id, name, status, positions[], createdAt`), produced by the existing
`PortfolioResponseMapper.toResponse(Portfolio)`. Decimals as plain strings, optionals as `null`
(unchanged FD001 formatting — FR-010, SC-004).

The `{portfolioId}` path variable is typed **`java.util.UUID`** on the controller method, so a
non-UUID segment yields Spring's default `MethodArgumentTypeMismatchException` → **`400`** (status
only, no problem `type`) *before* the service is called — distinct from the `404` a well-formed but
unknown id produces (FR-022; analyze A3). No custom handler for the mismatch.

### 4.3 `PortfolioExceptionHandler` — 1 new method + widened scope

- `@RestControllerAdvice(assignableTypes = { CreatePortfolioController.class, PortfolioQueryController.class })`
- `@ExceptionHandler(PortfolioNotFoundException.class)` → `ProblemDetail.forStatus(NOT_FOUND)`,
  `type = /problems/portfolio-not-found`, `title = "Portfolio not found"`,
  `instance = /api/portfolios/{id}`.
- The `PortfolioValidationException` (400) and `PortfolioNotSavedException` (503) handlers are
  **unchanged**.

---

## 5. Frontend view models (`portfolio` feature area)

### 5.1 `PortfolioSummary` — NEW (`portfolio.models.ts`)

```ts
interface PortfolioSummary {
  id: string;
  name: string;
  positionCount: number;
}
```

### 5.2 Detail — REUSED

`PortfolioView` / `PositionView` from `portfolio-creation.models.ts` (already the FD001
create-response shape: `{ id, name, status, positions: PositionView[], createdAt }`,
`PositionView = { id, ticker, market, quantity, currency, initialPurchaseDate: string|null,
averagePurchasePrice: string|null }`).

### 5.3 List / detail load state

```ts
type ListState =
  | { kind: 'loading' }
  | { kind: 'loaded'; portfolios: PortfolioSummary[] }   // may be empty -> empty-state view
  | { kind: 'error' };

type DetailState =
  | { kind: 'loading' }
  | { kind: 'loaded'; portfolio: PortfolioView }
  | { kind: 'not-found' }
  | { kind: 'error' };
```

---

## 6. Where each rule lives

| Rule | Layer | Note |
|---|---|---|
| Only the current Investor's portfolios (FR-003, BR-001) | `PortfolioJpaRepository` query (`…AndInvestorId`) + `PortfolioQueryService` (resolves the Investor) | scoping in the query, not a post-filter |
| Only persisted portfolios (FR-003, BR-002) | inherent — drafts never reach the DB (FD001) | nothing to enforce |
| Position count = persisted Positions (FR-024, BR-005) | `PortfolioSummaryMapper` (`positions().size()`) on the eagerly-loaded aggregate | OD-FD003-1 |
| Newest-first, deterministic order (FR-006, A3) | `findAllByInvestorIdOrderByCreatedAtDescIdDesc` | not user-sortable |
| Empty list → `200 []` (FR-022) | `PortfolioQueryController` returns the (possibly empty) list | never `404` |
| Unknown / other id → `404` problem (FR-013, FR-022) | `PortfolioNotFoundException` → `PortfolioExceptionHandler` | `/problems/portfolio-not-found` |
| Malformed id (non-UUID) → `400` (FR-022) | `UUID`-typed `@PathVariable` on `PortfolioQueryController` → Spring default | status only; no custom handler; analyze A3 |
| Detail shows only that Portfolio's Positions (FR-011) | the aggregate loaded by `findByIdForInvestor` contains only its own Positions (FK) | |
| Read-only — 0 writes, 0 events (FR-015, SC-005) | `PortfolioQueryService` never calls `save`; adapter uses `readOnlyTemplate` | IT asserts row counts unchanged |
| No provider/persistence field in responses (FR-021) | `PortfolioSummaryResponse` (3 fields) + reused `Portfolio` schema | contract test |
