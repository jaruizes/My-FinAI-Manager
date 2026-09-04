# Contract — `PortfolioValuationCalculator` (pure deterministic domain service)

**Package**: `com.myfinaimanager.core.portfolio.domain.model` · **No** ports, Spring, I/O, or LLM
(constitution VI, VI; FR-008, FR-035). All values `BigDecimal`; **no `double`/`float`** (SC-006).

Rationale + the rejected alternatives: [research.md](../research.md) D4 + D5.

---

## Signature

```java
PortfolioValuation calculate(
        PortfolioId portfolioId,
        List<PositionInput> positions,
        FxContext fx,
        Instant calculatedAt);
```

```java
record PositionInput(String ticker, String market, BigDecimal quantity, String nativeCurrency,
                     Optional<BigDecimal> price, Optional<Instant> priceObservedAt,
                     Optional<String> sector) {}

record FxContext(Optional<BigDecimal> usdToEur, Optional<Instant> usdToEurObservedAt,
                 Optional<BigDecimal> eurToUsd, Optional<Instant> eurToUsdObservedAt) {}
```

`nativeCurrency` ∈ {`"EUR"`, `"USD"`}. `quantity > 0`. `price`, when present, `> 0`.

---

## Algorithm

### Step 1 — per-Position native value

For each `PositionInput p`:
- `p.price` **absent** ⇒ `PositionValuation` with `valued = false`, all monetary fields + `weight` +
  `priceObservedAt` absent, `sector = p.sector.orElse("Unclassified")`. Skip to next.
- `p.price` **present** ⇒ `valued = true`; `nativeMarketValue = p.quantity.multiply(price)` (exact,
  no rounding); `marketPrice = price`; `priceObservedAt = p.priceObservedAt`;
  `sector = p.sector.orElse("Unclassified")`.

### Step 2 — per-Position EUR & USD value

For a **valued** Position:
- `nativeCurrency == "USD"`: `valueInUSD = nativeMarketValue`;
  `valueInEUR = fx.usdToEur.map(r -> nativeMarketValue.multiply(r))` (absent if no `usdToEur`).
- `nativeCurrency == "EUR"`: `valueInEUR = nativeMarketValue`;
  `valueInUSD = fx.eurToUsd.map(r -> nativeMarketValue.multiply(r))` (absent if no `eurToUsd`).

### Step 3 — totals (over valued Positions)

- `totalValueEUR` = `Σ valueInEUR` **iff every valued Position has `valueInEUR` present**; else absent.
- `totalValueUSD` = `Σ valueInUSD` **iff every valued Position has `valueInUSD` present**; else absent.
- Sums are exact `BigDecimal.add`. (If there are **zero** valued Positions both totals are absent.)

### Step 4 — weights & sector allocation

Only when `totalValueEUR` is **present and `compareTo(ZERO) > 0`**; otherwise **no** Position gets a
`portfolioWeight` and **no** `SectorAllocation` rows are produced (spec Edge Case "Zero total EUR";
FR-019).

- Per valued Position that has `valueInEUR`:
  `portfolioWeight = valueInEUR.divide(totalValueEUR, 12, RoundingMode.HALF_UP)`.
  (A valued USD Position with no FX has no `valueInEUR` ⇒ no weight ⇒ excluded from allocation;
  its presence still forces `PARTIAL` in Step 5.)
- Group those Positions by `sector`; per group:
  `sectorValueEUR = Σ valueInEUR` (exact);
  `sectorWeight = sectorValueEUR.divide(totalValueEUR, 12, RoundingMode.HALF_UP)`.
- `SectorAllocation` list ordered by descending `sectorValueEUR`, ties broken by `sector` name.

### Step 5 — status (research D5)

Let `Vp` = `positions.size()`, `Vv` = count of valued Positions.

| Condition (first match wins) | `status` |
|---|---|
| `Vv == 0` | `FAILED` |
| `totalValueEUR` absent **and** `totalValueUSD` absent | `FAILED` |
| `Vv == Vp` **and** every valued Position has **both** `valueInEUR` and `valueInUSD` | `COMPLETED` |
| otherwise | `PARTIAL` |

A missing **sector only** never lowers `COMPLETED` (`sector` defaulting to `"Unclassified"` is not a
gap — FR-016). The calculator never returns `PENDING`.

### Step 6 — freshness

- `marketDataAsOf` = min `priceObservedAt` over valued Positions (absent if none / none carry it).
- `fxDataAsOf` = min `observedAt` over the FX rates **actually used** (i.e. `usdToEurObservedAt`
  if any USD Position was converted, `eurToUsdObservedAt` if any EUR Position was; absent if no
  conversion happened).

### Determinism

Same inputs ⇒ byte-identical `PortfolioValuation` (pure function, `BigDecimal`, fixed rounding).
`calculate` twice / thrice ⇒ identical result (SC-002 idempotency at the calc level; persistence
idempotency is `upsertLatest`).

---

## Worked cases (become `PortfolioValuationCalculatorTest`)

### C1 — E2E-001 happy path (SC-001, SC-003)

Inputs: `AAPL`/`XNAS` qty `10` USD price `200` sector `Technology`; `SAN`/`XMAD` qty `100` EUR
price `5` sector `Financial Services`; `usdToEur = 0.80`, `eurToUsd = 1.25`.

| Output | Value |
|---|---|
| AAPL `nativeMarketValue` | `2000` USD |
| AAPL `valueInUSD` / `valueInEUR` | `2000` / `1600` |
| SAN `nativeMarketValue` | `500` EUR |
| SAN `valueInEUR` / `valueInUSD` | `500` / `625` |
| `totalValueEUR` / `totalValueUSD` | `2100` / `2625` (compareTo `2100.00` / `2625.00` == 0) |
| AAPL `portfolioWeight` | `0.761904761905` |
| SAN `portfolioWeight` | `0.238095238095` |
| `sectors` | `Technology 1600 / 0.761904761905`, `Financial Services 500 / 0.238095238095` |
| `status` | `COMPLETED` |

Display (asserted by the E2E / frontend spec, not the calculator): `€2,100.00`, `$2,625.00`,
`76.19 %`, `23.81 %`, Σ `100.00 %`.

### C2 — missing price → PARTIAL, never zero (SC-004)

AAPL as C1; SAN `price` **absent**. ⇒ SAN `valued = false`, all SAN monetary fields absent;
`totalValueEUR` = `1600` (only AAPL), `totalValueUSD` = `2000`; AAPL weight `1.000000000000`;
sectors = `Technology` only; `status = PARTIAL`. No `0` anywhere.

### C3 — missing FX for cross total → PARTIAL, native total stands

Both priced as C1 but `usdToEur` **absent** (`eurToUsd = 1.25` present). ⇒ AAPL `valueInEUR` absent;
SAN `valueInEUR = 500`. `totalValueEUR` **absent** (not every valued Position has EUR).
`totalValueUSD` = `2000 + 625 = 2625` present. No weights, no sector rows (`totalValueEUR` absent).
`status = PARTIAL` (a total is producible, some Position valued).

### C4 — no Position priced → FAILED

Both `price` absent ⇒ `Vv == 0` ⇒ `status = FAILED`; both totals absent; no weights/sectors; both
`PositionValuation` unvalued with absent monetary fields.

### C5 — single-currency Portfolio, other-direction FX missing → PARTIAL

One EUR Position priced; `eurToUsd` absent. ⇒ `valueInEUR` present, `valueInUSD` absent;
`totalValueEUR` present, `totalValueUSD` absent; weight computed; `status = PARTIAL`.

### C6 — missing sector only → still COMPLETED

C1 but AAPL `sector` absent ⇒ AAPL `sector = "Unclassified"`, fully valued; `status = COMPLETED`;
`sectors` = `Unclassified 1600`, `Financial Services 500`.

### C7 — zero total EUR guard

All Positions unvalued except one valued USD Position with **no** FX ⇒ `totalValueEUR` absent ⇒
no `portfolioWeight`, no `SectorAllocation`, no `NaN`/`0 %`. `status = PARTIAL` (that USD Position
still yields `valueInUSD` ⇒ `totalValueUSD` present) or `FAILED` if it too is unpriced.

### C8 — determinism

C1 inputs, call 3×, assert `.equals` on the whole `PortfolioValuation` (all `BigDecimal` fields
compared by value/scale as constructed).
