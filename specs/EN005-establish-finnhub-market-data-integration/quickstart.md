# Quickstart — Validate EN005 Revision 2

Run guide proving the revision end to end. Details: [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/). No implementation code here.
Supersedes the 2026-09-03 quickstart.

**Prerequisites**: colima/Docker (`DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock`,
`TESTCONTAINERS_RYUK_DISABLED=true`); Node 20.19.1 + `CHROME_BIN` for `ng test`; repo root =
`implementation/platform/`. FD001–FD004 + EN004 implemented + verified.

---

## A. Backend build, tests, coverage, architecture (SC-005)

```bash
cd implementation/platform/backend/core-service
./mvnw -B clean verify        # OFFLINE — no finnhub.io / api.frankfurter.dev
```

**Expect** BUILD SUCCESS; JaCoCo bundle ≥ 90 % line & branch; `StandardArchitectureRulesTest`
green with the Revision-2 rule delta ([research.md](./research.md) D10). New/updated tests all
green:

- `GetInstrumentProfileTest` — DB-first: local hit ⇒ provider **not** called; miss ⇒ provider once +
  `save` once; provider fail ⇒ `InstrumentProfileUnavailableException`, **no** `save` (SC-003).
- `FrankfurterFxRateAdapterTest` / `FrankfurterFxRateMapperTest` — `GET /v1/latest?base=USD&symbols=EUR`
  no auth; `rates.EUR` → `FxRate`; missing key → `FxRateUnavailable`; `5xx`/timeout → unavailable;
  `from==to` → `IllegalArgumentException` (SC-001).
- `FinnhubInstrumentProfileAdapterTest` / `FinnhubProfileMapperTest` — moved; `/stock/profile2` +
  `X-Finnhub-Token`; `{}` → unavailable; `exchange` not a MIC.
- `JpaInstrumentProfileRepositoryAdapterIT` (Testcontainers) — round-trip; **two `save`s ⇒ one row**
  (upsert); `V5` applied.
- `GetInstrumentProfileIT` (Testcontainers) — miss → provider → persisted → 2nd call is a local hit.
- `ProviderSelectionTest` — `market-data.fx.provider=frankfurter` ⇒ FX bean is Frankfurter; price /
  profile ⇒ Finnhub; **no** provider branch in `domain`/`business` (SC-004).
- `FinnhubIntegrationIT` — updated: price + profile need the Finnhub key; **FX works with no key**.
- FD004 `EnMarketDataGatewayAdapterTest` — `InstrumentProfileLookup` mock; same behavior.
- FD001/FD002/FD003/FD004/EN004 suites still green (SC-006).

**Grep checks**:
```bash
grep -rn "forex/rates\|FinnhubForexRates\|FinnhubFxRateAdapter" src/ && echo "LEAK" || echo "OK — Finnhub FX removed"
grep -rn "com.myfinaimanager.core.marketdata" src/main/java/com/myfinaimanager/core/financialinstrument/ && echo "LEAK" || echo "OK — financialinstrument ⊥ marketdata"
git diff --stat -- '**/openapi.yaml'   # empty — no contract change
```

## B. Frontend tests (SC-006)

```bash
cd implementation/platform/frontend/web
nvm use 20.19.1 && CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npm test
```
**Expect** unchanged — EN005 has no frontend; FD004 detail specs still green (71).

## C. Runtime smoke — local platform, no keys (SC-006)

```bash
cd implementation/platform && ./start.sh
curl -s localhost:8080/actuator/health              # UP
```
Create a portfolio (`AAPL`/`XNAS`/`USD`), then `GET /api/portfolios/<id>/valuation`:
- **Expect** `PARTIAL` — AAPL priced? no (no Finnhub key) ⇒ FAILED/PARTIAL; **but** FX (Frankfurter)
  needs no key, so with a Finnhub key set (next step) the EUR total appears.
- Startup log: `event=FinnhubIntegrationDisabled` once (price/profile disabled); **no** Frankfurter
  disable log.

```bash
./stop.sh && ./stop.sh    # idempotent
```

## D. Runtime — real Finnhub key + real Frankfurter (SC-010)

```bash
echo 'FINNHUB_API_KEY=<your real key>' >> infrastructure/local/.env
./start.sh
```
Create a portfolio with **US equities in USD** (e.g. `AAPL` ×10, `MSFT` ×5), then
`GET /api/portfolios/<id>/valuation`:
- **Expect `COMPLETED`** — real prices (Finnhub `/quote`), real sector (Finnhub `/stock/profile2`,
  persisted to `instrument_profile` on first lookup), real **USD↔EUR** (Frankfurter `/v1/latest`,
  **no key**), weights + sector allocation populated.
- Second call for the same portfolio: check the backend log — **no** `provider=finnhub
  capability=instrument-profile` call the second time (DB-first local hit).
- `psql … -c 'SELECT ticker, market_mic, sector, source FROM instrument_profile'` — one row per
  looked-up instrument, `source = FINNHUB`.
- A EUR position (e.g. `SAN`/`XMAD`) ⇒ that position stays **unvalued** (Finnhub free = US only) ⇒
  `PARTIAL` — an explicit provider-neutral outcome, **no** fabricated value (SC-002).

```bash
./stop.sh
```

## E. E2E — both provider boundaries stubbed (SC-006)

```bash
cd implementation/platform && ./e2e.sh
```
`compose.e2e.yaml` starts **`market-data-stub`** (serves `/quote`, `/stock/profile2`, `/v1/latest`);
backend gets `FINNHUB_BASE_URL` **and** `FRANKFURTER_BASE_URL` → the stub.

**Expect**: all existing specs green, incl. `fd004-valuation.spec.ts` (E2E-001 — totals
`€2,100.00` / `$2,625.00`, weights `76.19 %` / `23.81 %` — **unchanged**, FX now from the stub's
`/v1/latest`) and `fd004-provider-failure.spec.ts` (E2E-002). No request reaches `finnhub.io` or
`api.frankfurter.dev`.

## F. VC coverage (SC-009)

| VC | Proven by |
|---|---|
| VC-001 provider-neutral core | `ProviderSelectionTest`; ArchUnit; `GetInstrumentProfileTest` |
| VC-002 / VC-003 separate ports / adapters | package inspection; `FrankfurterFxRateAdapterTest`, `FinnhubInstrumentProfileAdapterTest`, `FinnhubMarketDataAdapterTest` |
| VC-004 independent selection | `ProviderSelectionTest`; §D `git diff` review |
| VC-005 / VC-006 / VC-007 DB-first | `GetInstrumentProfileTest`, `GetInstrumentProfileIT` |
| VC-008 / VC-009 persistence + neutral fields | `JpaInstrumentProfileRepositoryAdapterIT`, `InstrumentProfilePersistenceMapperTest` |
| VC-010 canonical identity | `FinnhubProfileMapperTest` (`exchange` ≠ MIC); `V5` has no EN004 FK |
| VC-011 secret / no fake secret | logging tests (Finnhub + Frankfurter); `frankfurter` has no key |
| VC-012 limitations hidden | §D EUR position → `PARTIAL`; no US-only rule in core (ArchUnit + review) |
| VC-013 multiple adapters possible | `@ConditionalOnProperty` pattern; `ProviderSelectionTest` |
| VC-014 deterministic tests | `./mvnw verify` + `./e2e.sh` offline |
| VC-015 ADR-003 | `StandardArchitectureRulesTest` |

## G. Definition-of-Done quick gate

- [ ] `./mvnw verify` green offline — unit + Testcontainers IT + ArchUnit + coverage ≥ 90 %
- [ ] `ng test` green
- [ ] `./e2e.sh` green offline (`market-data-stub`; FD004 E2E-001/002)
- [ ] `V5__instrument_profile.sql` the only schema change; EN004/FD001/FD004 tables untouched
- [ ] Finnhub `/forex/rates` path fully removed; `financialinstrument` ⊥ `marketdata`
- [ ] No Finnhub key in any log/URL/exception/response; Frankfurter adds no secret
- [ ] No new deployable / broker / scheduler / caching infra / dependency / `product/` edit; `openapi.yaml` unchanged
- [ ] `implementation/platform/README.md` + `backend/core-service/README.md` updated (Frankfurter, DB-first profile, per-capability config)
- [ ] `/project-verify EN005-establish-finnhub-market-data-integration`
