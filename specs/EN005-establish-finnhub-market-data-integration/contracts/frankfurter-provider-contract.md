# Contract — Frankfurter FX provider (EN005 Revision 2)

**Adapter**: `marketdata.infrastructure.frankfurter.FrankfurterFxRateAdapter` (`implements
marketdata.domain.ports.FxRatePort`) · **Client**: `FrankfurterRestClient` · **No API key.**

Rationale: [plan.md](../plan.md) · [research.md](../research.md) D1. Frankfurter serves ECB
reference rates; public API `https://api.frankfurter.dev`; base URL overridable via
`FRANKFURTER_BASE_URL` (the E2E stub).

---

## Request

```
GET {base-url}/v1/latest?base={FROM}&symbols={TO}
```

- `{FROM}` / `{TO}` — ISO-4217 codes; EN005 initial scope `USD` / `EUR` only.
- **No** `Authorization` / token header. No query key.
- Timeouts: connect 2s, read 5s (`FrankfurterProperties`).

Examples:

```
GET /v1/latest?base=USD&symbols=EUR      # USD → EUR
GET /v1/latest?base=EUR&symbols=USD      # EUR → USD
```

## Response (200)

```json
{
  "amount": 1.0,
  "base": "USD",
  "date": "2026-09-03",
  "rates": { "EUR": 0.85477 }
}
```

| Provider field | → neutral `FxRate` field |
|---|---|
| `base` | `from` (must equal the requested `FROM`) |
| `rates[TO]` | `rate` (positive `BigDecimal`) |
| `date` | `observedAt` = `LocalDate.parse(date).atStartOfDay(UTC).toInstant()`; `observedAtSource = PROVIDER_TIMESTAMP` |
| *(call instant)* | `retrievedAt` |
| — | `to` = the requested `TO`; `source = DataSource.FRANKFURTER` |

## Failure translation (inside the adapter — nothing Frankfurter-specific crosses `FxRatePort`)

| Condition | Neutral outcome |
|---|---|
| `rates` present but has **no** `TO` key | `FxRateUnavailableException` (never a null / `1` / `0` rate) |
| malformed / non-JSON / missing `rates` body | `FxRateUnavailableException` |
| HTTP `5xx`, connect/read timeout, `IOException` | `MarketDataUnavailableException` (≡ `ExternalProviderUnavailable`) |
| HTTP `4xx` other than a business "unknown currency" | `MarketDataUnavailableException` |
| `from == to` (caller error) | `IllegalArgumentException` (per `FxRatePort` javadoc — not a provider call) |

Frankfurter needs no key, so `MarketDataNotConfiguredException` / `ProviderAuthenticationFailedException`
do **not** apply to this adapter.

## Telemetry (FR-033)

Structured log per call: `event=ProviderCall provider=frankfurter capability=fx-rate
operation=LATEST outcome={SUCCESS|UNAVAILABLE|MALFORMED} httpStatusCategory={2xx|4xx|5xx|none}
latencyMs=…`. No secret (there is none).

## Adapter tests (`FrankfurterFxRateAdapterTest`, `MockRestServiceServer`)

1. `getRate(USD, EUR)` → correct URL, **no** auth header, `rates.EUR` → `FxRate.rate`, `date` → `observedAt`.
2. `getRate(EUR, USD)` → mirror.
3. Response with `rates` missing `EUR` → `FxRateUnavailableException`.
4. Malformed body → `FxRateUnavailableException`.
5. `503` / socket timeout → `MarketDataUnavailableException`.
6. `getRate(USD, USD)` → `IllegalArgumentException`, no HTTP call.
7. Logging test — line carries `provider=frankfurter capability=fx-rate`, no secret, format-agnostic.

## E2E stub (`market-data-stub`)

Serves `/v1/latest?base=USD&symbols=EUR` → `{ "amount":1.0, "base":"USD", "date":"<today>", "rates": { "EUR": 0.80 } }`
and `base=EUR&symbols=USD` → `{ … "rates": { "USD": 1.25 } }` — the FD004 E2E-001 deterministic
rates (unchanged from the prior Finnhub-forex stub values).
