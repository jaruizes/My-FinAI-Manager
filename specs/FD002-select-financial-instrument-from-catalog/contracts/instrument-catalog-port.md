# Contract — instrument-catalog ports (FD002)

Two in-process interfaces. No HTTP. Backing the FR-011 server-side guarantee.

```text
CreatePortfolioService  ──uses──▶  portfolio.domain.ports.InstrumentCatalog   (ACL — portfolio owns it)
                                          ▲
                       implements         │
CatalogInstrumentCatalogAdapter ──calls──▶ financialinstrument.domain.ports.FinancialInstrumentCatalog.findSelectable
(portfolio.infrastructure.catalog)               (financialinstrument's published read port)
                                          │
                                   FinancialInstrumentCatalogAdapter ──▶ FinancialInstrumentJpaRepository ──▶ PostgreSQL
```

---

## C1 — `portfolio.domain.ports.InstrumentCatalog`

```java
package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;

/**
 * Read access, for portfolio creation, to whether an instrument selection is valid. Provider- and
 * module-neutral: only {@code portfolio} value objects cross this boundary (FD002 FR-011).
 */
public interface InstrumentCatalog {

    /**
     * @return {@code true} iff the platform catalog holds an <strong>active</strong> Financial
     *         Instrument listing for exactly this {@code ticker} on exactly this {@code market}
     *         <strong>whose currency equals {@code currency}</strong> (and is therefore EUR or USD —
     *         EN004's catalog holds no other). The full {@code ticker + market + currency}
     *         combination must exist as one catalogued listing (FD002 BR-004).
     */
    boolean isSelectable(Ticker ticker, Market market, Currency currency);
}
```

**Invariants**

| # | Invariant |
|---|---|
| P1 | Pure query — no side effects, no mutation. |
| P2 | Case-insensitive on ticker; exact on MIC (matches EN004's `search` / DB key); currency compared by ISO code (case-insensitive). |
| P3 | `false` for: unknown instrument; known but `active = false`; ticker on a different market than requested; **known active listing but its currency ≠ the submitted `currency`** (e.g. `AAPL + XNAS + EUR` when the listing is USD); the submitted currency is not EUR/USD (no such listing can exist). |
| P4 | Never contacts an external provider (delegates to EN004's local-only catalog). |
| P5 | Deterministic for a fixed catalog state. |
| P6 | Total — never throws for well-formed value objects; a catalog/infrastructure failure surfaces as a `PortfolioNotSavedException` path (503), **not** a validation `false` (a transient catalog read failure must not masquerade as "not selectable"). See T017/T018 — the adapter lets the underlying `DataAccessException` propagate; the service does not swallow it. |

**Consumer**: `CreatePortfolioService` calls `isSelectable` once per structurally-valid position
(one with a non-blank ticker **and** market **and** currency) and raises `INSTRUMENT_NOT_IN_CATALOG`
on `false` (see `portfolio-validation.delta.md`, research D1). A position missing any of the three is
already covered by the FD001 `REQUIRED` / `CURRENCY_FORMAT` structural checks and is **not**
catalog-checked.

---

## C2 — `financialinstrument.domain.ports.FinancialInstrumentCatalog.findSelectable` (added method)

```java
/**
 * The single active, EUR/USD listing with this exact {@code ticker} (case-insensitive) on this
 * exact {@code marketMic}, or empty. FD002 uses this to validate a Position's instrument
 * selection (it is the exact-match counterpart of {@link #search(String)}).
 *
 * @param ticker    normalized trading ticker; matched case-insensitively
 * @param marketMic ISO 10383 MIC; matched exactly
 * @return the listing, or {@code Optional.empty()} when none is selectable
 */
Optional<FinancialInstrumentListing> findSelectable(String ticker, String marketMic);
```

**Invariants** (extend `contracts/catalog-ports.md` from EN004)

| # | Invariant |
|---|---|
| C-S1 | Returns a listing only if `active == true` AND `currency ∈ {EUR, USD}` (same selectability filter as `search`). |
| C-S2 | At most one result — `(ticker, market_mic)` is unique in `financial_instrument`. |
| C-S3 | Local PostgreSQL only; no outbound call (EN004 VC-006). |
| C-S4 | `@Transactional(readOnly = true)`. |
| C-S5 | Does not change `search`, ingestion, or `GET /api/financial-instruments`. |

**Implementation note**: `FinancialInstrumentJpaRepository.findByTickerIgnoreCaseAndMarketMic(ticker, marketMic)`
already exists; add the active/currency guard in the adapter, or add a derived query
`findByTickerIgnoreCaseAndMarketMicAndActiveTrueAndCurrencyIn(ticker, mic, {"EUR","USD"})`.

---

## C3 — `CatalogInstrumentCatalogAdapter` (portfolio.infrastructure.catalog)

```java
@Component
class CatalogInstrumentCatalogAdapter implements InstrumentCatalog {
    private final FinancialInstrumentCatalog catalog;   // financialinstrument.domain.ports — the ONLY cross-module reference

    // isSelectable(t, m, ccy) ->            // portfolio VOs: Ticker.value(), Market.value(), Currency.code()
    //   catalog.findSelectable(t.value(), m.value())
    //          .filter(listing -> listing.currency().name().equalsIgnoreCase(ccy.code()))
    //          .isPresent();
}
```

- The **only** place `portfolio` code references `financialinstrument`. Reference is limited to
  `..financialinstrument.domain.ports..` (interface) and `..domain.model..` (return type) — never
  `..infrastructure..` or `..business..` (ArchUnit rule, research D11).
- No `@Transactional` needed here (the delegate is read-only transactional); portfolio creation's
  own transaction wraps the write.

---

## C4 — ArchUnit rule (added to `StandardArchitectureRulesTest`)

```java
@ArchTest
static final ArchRule portfolio_touches_financialinstrument_only_via_its_domain_ports =
    noClasses().that().resideInAPackage("..core.portfolio..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..core.financialinstrument.infrastructure..",
            "..core.financialinstrument.business..");

@ArchTest
static final ArchRule portfolio_core_is_free_of_financialinstrument =
    noClasses().that().resideInAnyPackage("..core.portfolio.domain..", "..core.portfolio.business..")
        .should().dependOnClassesThat().resideInAPackage("..core.financialinstrument..");
```

Non-vacuous: `CatalogInstrumentCatalogAdapter` exercises the allowed
`portfolio.infrastructure → financialinstrument.domain.ports` path.
