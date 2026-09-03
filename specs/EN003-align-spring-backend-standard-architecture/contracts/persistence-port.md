# Contract — Portfolio persistence ports (EN003)

EN003 introduces **no new external API**. The external business contract remains
`implementation/platform/contracts/openapi/openapi.yaml` (`POST /api/portfolios`), **unchanged** —
the contract test (`swagger-request-validator`) stays green (FR-015, VC-014).

What this document pins is the **internal port contract** between `portfolio.business` and the
persistence adapter — the interfaces move to `portfolio.domain.ports` with **identical signatures**,
and the new Spring Data JPA adapter must reproduce the behavior the JdbcClient adapter has today.

---

## 1. `portfolio.domain.ports.PortfolioRepository`

```java
public interface PortfolioRepository {
    Optional<Portfolio> findByIdempotencyKey(String idempotencyKey);
    Portfolio save(Portfolio portfolio, String idempotencyKey);
}
```

Signatures are **unchanged** from the current `application.port.out.PortfolioRepository`. No
framework types appear (FR-008). The implementing bean is
`infrastructure.persistence.PortfolioPersistenceAdapter`.

### 1.1 `findByIdempotencyKey`

| Aspect | Contract |
|---|---|
| Input | the client-generated idempotency key string |
| Returns | `Optional.of(portfolio)` if a portfolio was stored for that key; `Optional.empty()` otherwise |
| Aggregate completeness | the returned `Portfolio` has **all** its `Position`s, ordered by position id ascending (deterministic reconstruction) |
| Transaction | read-only; the collection is fully initialized before the entity leaves the persistence context (`@EntityGraph`) — no `LazyInitializationException` for callers outside a tx |
| Side effects | none |

### 1.2 `save`

| Aspect | Contract |
|---|---|
| Input | a validated `Portfolio` aggregate (already passed `Portfolio.create`) + the idempotency key |
| Normal result | the whole aggregate (portfolio row + every position row) is persisted **atomically** — all rows or none; returns the given `portfolio` |
| Idempotency race | if the idempotency key is already used (a concurrent identical submission won), **no** second portfolio is created; returns the **already-stored** `Portfolio` (loaded via `findByIdempotencyKey`) |
| Transient / integrity failure (not an idempotency race) | throws `portfolio.domain.exceptions.PortfolioNotSavedException`; **nothing** is persisted (no portfolio row, no orphan position rows) |
| Never | partially persists; weakens or bypasses a DB constraint; mutates the input aggregate |
| Exact decimals | `quantity` and `averagePurchasePrice` are stored and re-read with the investor's exact input scale — no rounding, no binary-float drift |
| Constraints enforced (defense-in-depth for domain rules) | `portfolio.idempotency_key` UNIQUE; `position (portfolio_id, ticker, market)` UNIQUE; `quantity > 0`; `average_purchase_price > 0 OR NULL`; price/currency pair both-null-or-both-set; `average_purchase_price_currency = currency`; `initial_purchase_date <= current_date OR NULL`; `status IN ('ACTIVE')`; `name` length 1–120 |

### 1.3 Adapter implementation notes (research.md D6 — behavior-equivalent to today)

- `TransactionTemplate` (programmatic) wraps `portfolioJpaRepository.saveAndFlush(entity)` so the
  unique-constraint violation surfaces **inside** the transaction and rolls it back.
- `DataIntegrityViolationException` (Spring's translation of the Hibernate/JDBC constraint
  violation) → re-read by idempotency key in a **fresh** transaction → return the existing
  `Portfolio` or throw `PortfolioNotSavedException`.
- any other `DataAccessException` → `PortfolioNotSavedException`.

This is the exact shape of the current `JdbcPortfolioRepository.save` (which catches
`DuplicateKeyException` / `DataAccessException`); only the persistence calls change.

---

## 2. `portfolio.domain.ports.DefaultInvestorProvider`

```java
public interface DefaultInvestorProvider {
    InvestorId get();
}
```

| Aspect | Contract |
|---|---|
| Returns | the id of the single platform-seeded default investor (`V2__portfolio.sql` seed `00000000-0000-0000-0000-000000000001`) |
| Determinism | if more than one investor row somehow exists, the earliest by `(created_at, id)` is returned (unchanged from the JDBC `ORDER BY created_at, id LIMIT 1`) |
| Missing seed | throws `IllegalStateException` — the platform is misconfigured (unchanged) |

Implemented by `infrastructure.persistence.JpaDefaultInvestorProvider` via
`InvestorJpaRepository.findTopByOrderByCreatedAtAscIdAsc()`.

---

## 3. `portfolio.business.CreatePortfolioUseCase` (driving port — moves to `business`)

```java
public interface CreatePortfolioUseCase {
    CreatePortfolioResult create(CreatePortfolioCommand command);
}
```

Unchanged behavior (FD001): idempotency lookup → resolve default investor → `Portfolio.create`
(may throw `PortfolioValidationException`) → `repository.save` (may throw
`PortfolioNotSavedException`) → emit `PortfolioCreated` + one `PositionAdded` per position
(structured logs) → return `{portfolio, replayed}`. The `infrastructure.api.rest` controller
depends on this interface.

---

## 4. What EN003 must NOT change

- The `openapi.yaml` operation, schemas, `code` enum, status codes, `Location` / `Idempotency-Replayed`
  headers, RFC 9457 error bodies.
- The JSON field names, decimal-string formatting (`toPlainString()`), date formatting
  (`YYYY-MM-DD`), and `null` (not `0`, not omitted) for absent optionals.
- The `PortfolioCreated` / `PositionAdded` structured-log event names and key-value fields.
- The database schema and constraints (`V1` + `V2`).
- The 400 (validation) / 503 (not-saved) outcomes and "nothing partial persisted" guarantee.
