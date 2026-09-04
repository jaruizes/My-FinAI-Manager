# Consumer contract — the Finnhub HTTP surface EN005 depends on

> **Revision 2 (2026-09-04).** EN005 now calls Finnhub for **`/quote`** (price, in the `marketdata`
> module) and **`/stock/profile2`** (profile, in the **`financialinstrument`** module) only.
> **`/forex/rates` is removed** — FX moved to Frankfurter (a premium Finnhub endpoint → `403` on
> the free plan). Each Finnhub adapter now owns its **own** `RestClient` (no shared client).

EN005 is a **consumer** of the Finnhub REST API. This file pins the exact request/response shapes
EN005 relies on, so the deterministic stub tests (`MockRestServiceServer`) encode a real contract
and the dependency cannot drift silently (constitution VIII intent, applied to a consumed API).

- **Base URL**: `https://finnhub.io/api/v1` (configurable via `finnhub.base-url`).
- **Auth**: request header **`X-Finnhub-Token: <FINNHUB_API_KEY>`** on every call. **Never** the
  `?token=` query parameter (OD-EN005-2).
- **Method**: `GET` for all three operations.
- **Timeouts**: connect `2s`, read `5s` (configurable) — every call is bounded (FR-017).
- EN005 makes **no** other Finnhub call. No websockets, no `/stock/candle`, no `/news`, no
  `/scan`, nothing paid beyond what an operator's key allows at their own discretion via the
  opt-in smoke test.

---

## 1. `GET /quote?symbol={finnhubSymbol}` — latest price

`{finnhubSymbol}` is produced by `FinnhubSymbolResolver` from the canonical `InstrumentIdentifier`
(e.g. `AAPL` for `AAPL·XNAS`; `SAN.MC` for `SAN·XMAD`).

**Success `200`** (fields not listed are ignored):

```json
{ "c": 187.32, "d": 1.10, "dp": 0.59, "h": 188.05, "l": 185.90, "o": 186.10, "pc": 186.22, "t": 1725000000 }
```

| Finnhub field | EN005 use |
|---|---|
| `c` | `MarketPrice.price` — **must be `> 0`**; `c = 0` / missing / `null` → `MarketDataUnavailableException` (Finnhub returns `c = 0` for an unknown or non-tradable symbol) |
| `t` | unix **seconds**; `t > 0` → `observedAt = Instant.ofEpochSecond(t)`, `observedAtSource = PROVIDER_TIMESTAMP`; else retrieval time |
| `d`, `dp`, `h`, `l`, `o`, `pc` | parsed into the DTO, **not exposed** by any port (enabler §8) |

**Currency**: `/quote` does not return a currency. EN005 uses the `currency` from the caller's
`InstrumentIdentifier` context (the Position/instrument currency, EN004-canonical). *(If a future
feature needs the profile currency for a cross-check, it calls `InstrumentProfilePort` — EN005 does
not couple the two calls.)*

---

## 2. `GET /stock/profile2?symbol={finnhubSymbol}` — company profile

**Success `200`** (subset; other fields ignored):

```json
{
  "ticker": "AAPL",
  "name": "Apple Inc",
  "currency": "USD",
  "exchange": "NASDAQ NMS - GLOBAL SELECT MARKET",
  "finnhubIndustry": "Technology"
}
```

| Finnhub field | EN005 use |
|---|---|
| `ticker` | `InstrumentProfile.ticker` |
| `name` | `InstrumentProfile.name` |
| `currency` | `InstrumentProfile.currency` — **metadata only** (nullable `SupportedCurrency`; ignored if not EUR/USD) |
| `exchange` | `InstrumentProfile.providerExchange` — **metadata string only**; never parsed to a MIC, never overrides EN004 (FR-015, VC-006) |
| `finnhubIndustry` | `InstrumentProfile.sector` via `Sector.of(...)`; blank/absent → `Sector.UNCLASSIFIED` (FR-035) |

**Empty body `{}`** (Finnhub returns this for an unknown symbol) → `InstrumentProfileUnavailableException`.

---

## 3. `GET /forex/rates?base={USD|EUR}` — FX rates

**Success `200`**:

```json
{ "base": "USD", "quote": { "EUR": 0.9231, "GBP": 0.7912, "JPY": 155.02, "...": 0 } }
```

| Request | EN005 reads | Produces |
|---|---|---|
| `base=USD` | `quote["EUR"]` | `FxRate(USD, EUR, rate, now, RETRIEVAL_TIME, FINNHUB)` |
| `base=EUR` | `quote["USD"]` | `FxRate(EUR, USD, rate, now, RETRIEVAL_TIME, FINNHUB)` |

- `/forex/rates` carries **no** timestamp → `observedAtSource = RETRIEVAL_TIME` always.
- Target currency **absent** from `quote` (or `<= 0`) → `FxRateUnavailableException` (never `1` /
  `0` / a guess).
- All other `quote` entries are ignored (EN005 only supports EUR/USD — enabler §14).

---

## 4. Error responses (all operations)

| HTTP | Body (typical) | EN005 → |
|---|---|---|
| `401` | `{"error":"Invalid API key"}` | `ProviderAuthenticationFailedException` |
| `403` | `{"error":"You don't have access to this resource."}` | `ProviderAuthenticationFailedException` |
| `429` | `{"error":"API limit reached. ..."}` | `ProviderRateLimitedException` |
| `4xx` (other) / `404` | any | `<operation>Unavailable` |
| `5xx` | any | `<operation>Unavailable` |
| `200` + non-JSON / truncated | — | `<operation>Unavailable` |
| connect/read timeout, connection reset, DNS failure | — (`ResourceAccessException`) | `<operation>Unavailable` |

The Finnhub `error` string is **not** propagated (it is not sensitive, but keeping the neutral
boundary strict is simpler and safer — FR-012). It **may** be recorded in the structured log's
`detail` field only if it is verified key-free; default is to log the status category only (D9).

---

## 5. Stub fixtures (test resources)

`src/test/resources/finnhub/` (planning/tasks will finalize names):

- `quote-aapl.json` — healthy `c = 187.32`, `t` set
- `quote-zero.json` — `c = 0`
- `quote-missing-price.json` — `{}` / no `c`
- `profile-aapl.json` — full profile incl. `finnhubIndustry`
- `profile-no-industry.json` — profile without `finnhubIndustry`
- `profile-empty.json` — `{}`
- `forex-base-usd.json` — `quote.EUR` present
- `forex-base-eur.json` — `quote.USD` present
- `forex-missing-target.json` — `quote` without the wanted currency
- `error-401.json`, `error-429.json`, `error-500.txt` — error bodies

These are **synthetic** (no real key, no PII). They are the executable form of §§1–4.
