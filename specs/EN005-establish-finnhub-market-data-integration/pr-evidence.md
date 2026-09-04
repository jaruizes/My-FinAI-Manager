# EN005 Revision 2 — Implementation Evidence (in progress)

**Date**: 2026-09-04 · **Branch**: `EN005-establish-finnhub-market-data-integration` · Spec-Kit `/speckit-implement`

Supersedes the 2026-09-03 EN005 closure evidence. Repo remains uncommitted (established state).

## Checkpoint status (plan OD-8 sequencing)

| Checkpoint | Scope | Status |
|---|---|---|
| **C1 — FX provider swap (Finnhub → Frankfurter)** 🎯 MVP | `FxRatePort` backed by Frankfurter `/v1/latest` (keyless); Finnhub `/forex/rates` path fully removed | ✅ **DONE, green** |
| **C2 — Per-capability provider selection + telemetry** | `market-data.{price,profile,fx}.provider` config + `@ConditionalOnProperty` selection; `event=ProviderCall provider= capability=` telemetry | ✅ **DONE, green** |
| C3 — Database-first instrument profile in `financialinstrument` | new port + `GetInstrumentProfile` + JPA + `V5` migration + move `FinnhubInstrumentProfileAdapter`; FD004 consumer rewrite; delete `marketdata` profile | ⏳ pending (T023–T049) |
| C4 — E2E combined `market-data-stub` | `finnhub-stub` → `market-data-stub` (+ `/v1/latest`, − `/forex/rates`); `FRANKFURTER_BASE_URL` | ⏳ pending (T050–T054) |
| C5 — Architecture / coverage / docs / runtime | ArchUnit rule delta, JaCoCo, README updates | ⏳ pending (T055–T063) |

## C1 + C2 gate results

| Gate | Result |
|---|---|
| `./mvnw -o clean verify` (offline) | **BUILD SUCCESS** — Surefire 261 (2 skipped = opt-in smoke) + Failsafe 71, 0 failures; `StandardArchitectureRulesTest` **22 rules** (was 21: `only_the_finnhub_client_package_uses_restclient` → `only_provider_client_packages_use_restclient`; + `provider_selection_wiring_stays_out_of_domain_and_business`); JaCoCo bundle gate ≥ 90 % line & branch met |
| Finnhub FX removal | `grep -rn "forex/rates\|FinnhubForexRates\|FinnhubFxRateAdapter\|forexRates" src/` → **no code matches** (2 stale javadoc mentions fixed) |
| Runtime — real Finnhub key + real Frankfurter, all-US portfolio | `./start.sh` (key in `.env`), create AAPL ×10 + MSFT ×5 (USD), `GET …/valuation` → **`COMPLETED`**: `totalValueEUR 5021.72…`, `totalValueUSD 5832.70`, both positions valued, `sector "Technology"`, weights + sector allocation populated. Provider log: `provider=finnhub capability=market-price QUOTE SUCCESS`, `provider=finnhub capability=instrument-profile PROFILE SUCCESS`, **`provider=frankfurter capability=fx-rate LATEST SUCCESS`** (no key). `fxDataAsOf` = the ECB publication date. |

**This resolves the reported defect** — an all-US-equity portfolio now gets a full `COMPLETED`
valuation with a real USD↔EUR conversion, entirely on free API tiers (Finnhub free = US quotes +
profile; Frankfurter = keyless ECB FX).

## What changed (C1 + C2)

- **New** `marketdata.infrastructure.frankfurter` package: `FrankfurterProperties`,
  `dto.FrankfurterRatesResponse`, `client.FrankfurterRestClient` (own `RestClient`, no auth),
  `mapper.FrankfurterFxRateMapper` (missing rate → `FxRateUnavailableException`, never a default),
  `FrankfurterFxRateAdapter implements FxRatePort` (`@ConditionalOnProperty market-data.fx.provider`).
- `DataSource.FRANKFURTER` added. `CachingFxRatePort` now decorates the Frankfurter adapter; FX TTL
  moved to `frankfurter.cache.fx-ttl`.
- **Deleted**: `FinnhubFxRateAdapter`, `FinnhubForexRatesResponse`, `FinnhubForexRatesMapper`,
  `FinnhubRestClient.forexRates(...)`, `FinnhubOperation.FOREX_RATES`, `FinnhubProperties.Cache.fxTtl`.
- `application.yml`: `market-data.{price,profile,fx}.provider` block; `frankfurter.*` block
  (`base-url: ${FRANKFURTER_BASE_URL:https://api.frankfurter.dev}`). `finnhub.cache.fx-ttl` removed;
  `profile-ttl` retained (removed in C3).
- `FinnhubMarketDataAdapter` gains `@ConditionalOnProperty market-data.price.provider`.
- Telemetry unified: Finnhub + Frankfurter clients log `event=ProviderCall` with `provider=` +
  `capability=` (`market-price` / `instrument-profile` / `fx-rate`).
- `pom.xml`: 3 JaCoCo excludes added (frankfurter dto; financialinstrument finnhub dto + config — for C3).
- Tests: `FrankfurterFxRateAdapterTest`, `FrankfurterFxRateMapperTest`, `FrankfurterRestClientLoggingTest`
  (new); `FinnhubIntegrationIT` rewritten (FX independent of the Finnhub key — VC-004; provider-per-capability
  wiring — VC-004); `CachingPortsTest` / `FinnhubConfigurationTest` / `FinnhubRestClientTest` /
  `FinnhubProviderSmokeTest` updated for the removed Finnhub FX + the 2-arg `Cache`.

**FD004 is untouched by C1 + C2** — it calls `FxRatePort.getRate(...)`, whose signature is unchanged.

## Scope so far (SC-008 partial)

- `openapi.yaml` unchanged. No new deployable / broker / scheduler / caching infrastructure / Maven
  dependency. No `product/` or `.specify/` edit. No migration yet (`V5` is C3). `compose.yaml` /
  `compose.e2e.yaml` unchanged (C4). FD001/FD002/FD003/FD004/EN004 suites + E2Es green.
