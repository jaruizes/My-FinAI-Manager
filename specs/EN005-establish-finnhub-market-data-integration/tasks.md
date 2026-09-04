---
description: "Task list for EN005 Revision 2 — External Market Data Capabilities"
---

# Tasks: Establish External Market Data Capabilities (EN005 — Revision 2)

**Input**: `specs/EN005-establish-finnhub-market-data-integration/` — [plan.md](./plan.md),
[spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md),
[contracts/](./contracts/), [quickstart.md](./quickstart.md). **Supersedes** the 2026-09-03 tasks.md.

**Tests**: **Included and required** — FR-035/FR-036/FR-037; constitution VII requires TDD RED-first
for `GetInstrumentProfile` (DB-first orchestration).

**Organization**: by the **5 green checkpoints** from plan OD-8 (this is a refactor — the platform
must build+test green at each checkpoint). User-story tags map: US1 (ports/adapters — arch,
verified C5), US2 (price provider-neutral — C1/C5), US3 (DB-first profile — C3), US4 (FX via
Frankfurter — C1), US5 (per-capability config — C2), US6 (errors/secrets/telemetry — C2), US7
(offline determinism — C4/C5).

**MVP = Checkpoint C1** (Phase 3) — the Finnhub→Frankfurter FX swap alone fixes the real defect
(free-tier FX) and makes an all-US-equity FD004 portfolio value `COMPLETED`.

**Confirmed 2026-09-04**: Q1 (`financialinstrument` owns DB-first profile), Q2 (keep port/exception
names), Q3 (keep price+FX cache, drop profile cache); the 8 plan ODs accepted as recommended.

## Path conventions

- **MD-M** `…/src/main/java/com/myfinaimanager/core/marketdata/` · **MD-T** `…/src/test/java/com/myfinaimanager/core/marketdata/`
- **FI-M** `…/financialinstrument/` (main) · **FI-T** `…/financialinstrument/` (test)
- **PF** `…/portfolio/infrastructure/marketdata/`
- Migrations `…/src/main/resources/db/migration/` · Arch test `…/architecture/StandardArchitectureRulesTest.java`
- Contract `…/contracts/openapi/openapi.yaml` (**not touched** — EN005 has no REST API)
- E2E `implementation/platform/e2e/` · Compose `implementation/platform/infrastructure/local/`

---

## Phase 1: Setup

- [x] T001 Record the pre-change green baseline: `./mvnw -q -DskipTests compile` (backend) + `npm run build` (frontend) both succeed. Note the current `StandardArchitectureRulesTest` rule count (21).
- [x] T002 [P] `MD-M/domain/model/DataSource.java` — add `FRANKFURTER` to the enum (used by `FxRate.source`).
- [x] T003 [P] Add the per-capability config block to `…/src/main/resources/application.yml`: `market-data: { price: { provider: finnhub }, profile: { provider: finnhub }, fx: { provider: frankfurter } }`. Leave `finnhub.*` as-is for now; add `frankfurter:` block (`base-url: ${FRANKFURTER_BASE_URL:https://api.frankfurter.dev}`, `connect-timeout: 2s`, `read-timeout: 5s`, `cache: { fx-ttl: 10m }`).
- [x] T004 [P] `…/pom.xml` — add JaCoCo `<excludes>`: `com/myfinaimanager/core/marketdata/infrastructure/frankfurter/dto/**`, `com/myfinaimanager/core/financialinstrument/infrastructure/finnhub/dto/**`, `com/myfinaimanager/core/financialinstrument/infrastructure/finnhub/config/**`.

---

## Phase 2: Checkpoint C1 — FX provider swap (Finnhub → Frankfurter) 🎯 MVP

**Goal**: `FxRatePort` is backed by Frankfurter (`/v1/latest`, no key); the Finnhub `/forex/rates`
path is gone. `./mvnw verify` green. FD004 needs no change (port unchanged).

**Independent test**: `FrankfurterFxRateAdapterTest` green; `grep -rn "forex/rates" src/` empty;
`FinnhubIntegrationIT` shows FX working with a blank Finnhub key.

### Tests for C1 (write first)

- [x] T005 [P] [US4] `MD-T/infrastructure/frankfurter/FrankfurterFxRateAdapterTest.java` (`MockRestServiceServer`) — `GET /v1/latest?base=USD&symbols=EUR` with **no** auth header; `{ "amount":1.0, "base":"USD", "date":"2026-09-03", "rates": { "EUR": 0.85477 } }` → `FxRate(from=USD, to=EUR, rate=0.85477, observedAt=2026-09-03T00:00:00Z, observedAtSource=PROVIDER_TIMESTAMP, source=FRANKFURTER)`; mirror `EUR→USD`; missing `rates.EUR` → `FxRateUnavailableException`; malformed body → `FxRateUnavailableException`; `503` / timeout → `MarketDataUnavailableException`; `getRate(USD, USD)` → `IllegalArgumentException` (no HTTP). Per [contracts/frankfurter-provider-contract.md](./contracts/frankfurter-provider-contract.md).
- [x] T006 [P] [US4] `MD-T/infrastructure/frankfurter/FrankfurterFxRateMapperTest.java` — rate/observedAt/source mapping; absent target key → exception.
- [x] T007 [P] [US6] `MD-T/infrastructure/frankfurter/FrankfurterRestClientLoggingTest.java` — a call logs `event=ProviderCall provider=frankfurter capability=fx-rate operation=LATEST outcome=… httpStatusCategory=…`, format-agnostic, contains no secret.

### Implementation for C1

- [x] T008 [P] [US4] `MD-M/infrastructure/config/FrankfurterProperties.java` — `@ConfigurationProperties("frankfurter")` record (`baseUrl` URI, `connectTimeout`, `readTimeout`, `Cache(fxTtl)`), + `@EnableConfigurationProperties` wired in `MarketDataModuleConfiguration`.
- [x] T009 [P] [US4] `MD-M/infrastructure/frankfurter/dto/FrankfurterRatesResponse.java` — Jackson record `@JsonIgnoreProperties(ignoreUnknown=true)` (`BigDecimal amount`, `String base`, `String date`, `Map<String,BigDecimal> rates`).
- [x] T010 [US4] `MD-M/infrastructure/frankfurter/client/FrankfurterRestClient.java` (`@Component`) — own `RestClient` from `FrankfurterProperties` (timeouts, matching `FinnhubRestClient` construction); `latest(String base, String symbols)` → `GET /v1/latest?base=&symbols=`, no auth; status/transport failure translation ([contracts/frankfurter-provider-contract.md](./contracts/frankfurter-provider-contract.md)); structured `event=ProviderCall provider=frankfurter capability=fx-rate` log.
- [x] T011 [US4] `MD-M/infrastructure/frankfurter/mapper/FrankfurterFxRateMapper.java` — `(dto, SupportedCurrency from, SupportedCurrency to, Instant retrievedAt) → FxRate`; `rates.get(to.name())` absent → `FxRateUnavailableException`; `date` → `observedAt` (start-of-day UTC).
- [x] T012 [US4] `MD-M/infrastructure/frankfurter/FrankfurterFxRateAdapter.java` (`@Component implements FxRatePort`, `@ConditionalOnProperty(name="market-data.fx.provider", havingValue="frankfurter", matchIfMissing=true)`) — `getRate(from, to)`: reject `from==to`; call client; map; return.
- [x] T013 [US4] Rewire `MD-M/infrastructure/finnhub/cache/CachingFxRatePort.java` — it now decorates `FrankfurterFxRateAdapter` (injects `FxRatePort` by type; stays `@Primary`). Move the FX TTL source from `FinnhubProperties.cache.fxTtl` to `FrankfurterProperties.cache.fxTtl`.
- [x] T014 [US4] **Delete** `MD-M/infrastructure/finnhub/FinnhubFxRateAdapter.java`, `…finnhub/dto/FinnhubForexRatesResponse.java`, `…finnhub/mapper/FinnhubForexRatesMapper.java`; remove `getForexRates(...)` from `FinnhubRestClient.java` and `FOREX_RATES` from `FinnhubOperation.java`; remove `fxTtl` from `FinnhubProperties.java` (+ its `application.yml` line).
- [x] T015 [US4] Delete/adjust the corresponding tests: `MD-T/infrastructure/finnhub/FinnhubFxRateAdapterTest.java`, `…/mapper/FinnhubForexRatesMapperTest.java`; update `FinnhubRestClientTest` / `MarketDataModelTest` for the removed enum/DTO and `DataSource.FRANKFURTER`.
- [x] T016 [US4] Update `MD-T/FinnhubIntegrationIT.java` — FX now resolves via Frankfurter (assert with a stubbed/`MockRestServiceServer` Frankfurter or a test `frankfurter.base-url`); assert **FX works with a blank `FINNHUB_API_KEY`**.
- [x] T017 [US7] **Checkpoint C1**: `./mvnw -B clean verify` green (offline). `grep -rn "forex/rates\|FinnhubForexRates\|FinnhubFxRateAdapter" src/` → empty. FD004 suites green (no FD004 change yet).

---

## Phase 3: Checkpoint C2 — Per-capability provider selection + telemetry

**Goal**: `market-data.{price,profile,fx}.provider` select the wired adapter; no provider-identity
branch in core; every adapter logs `capability=`.

**Independent test**: `ProviderSelectionTest` — flipping a property changes only the wired bean.

- [x] T018 [US5] `MD-M/infrastructure/finnhub/FinnhubMarketDataAdapter.java` — add `@ConditionalOnProperty(name="market-data.price.provider", havingValue="finnhub", matchIfMissing=true)`.
- [x] T019 [P] [US6] `MD-M/infrastructure/finnhub/client/FinnhubRestClient.java` — unify the structured log to `event=ProviderCall provider=finnhub capability=market-price operation=QUOTE …` (add `capability=`); keep the no-secret guarantees.
- [x] T020 [P] [US5] `MD-T/infrastructure/ProviderSelectionTest.java` (`@SpringBootTest` slice or `ApplicationContextRunner`) — default config ⇒ price bean = `FinnhubMarketDataAdapter`, fx bean (non-caching) = `FrankfurterFxRateAdapter`; `market-data.fx.provider=none` ⇒ context still starts (caching decorator handles absence) OR fails fast per FR-030 — assert the chosen behavior; a second dummy `@ConditionalOnProperty` fx adapter is selected when its value is set.
- [x] T021 [US5] `StandardArchitectureRulesTest.java` — add `no_provider_identity_branch_in_core`: no class in `..core..domain..` / `..core..business..` references a string literal matching a provider name via config — pragmatically, assert `..domain..`/`..business..` do not depend on `org.springframework.boot.autoconfigure.condition..` and contain no `@ConditionalOnProperty`. (Lightweight; the real guard is FR-006 review.)
- [x] T022 [US7] **Checkpoint C2**: `./mvnw -B clean verify` green; `ProviderSelectionTest` green.

---

## Phase 4: Checkpoint C3 — Database-first instrument profile in `financialinstrument` (US3)

**Goal**: the whole profile capability lives in `financialinstrument`; `GetInstrumentProfile` is
DB-first; `marketdata` has no profile code; FD004's `sector(...)` routes through the new
`InstrumentProfileLookup` domain port. `./mvnw verify` green.

**Independent test**: `GetInstrumentProfileTest` (RED-first) + `GetInstrumentProfileIT` (Testcontainers).

### C3.1 — `financialinstrument` domain + ports (write model tests alongside)

- [ ] T023 [P] [US3] `FI-M/domain/model/Sector.java` — final class: `UNCLASSIFIED` singleton, `of(String)`, `isClassified()`, `classification()`, `equals`/`hashCode`/`toString` (mirror the deleted `marketdata` one).
- [ ] T024 [P] [US3] `FI-M/domain/model/ProfileSource.java` — enum `{ FINNHUB }` (module-local).
- [ ] T025 [P] [US3] `FI-M/domain/model/InstrumentProfile.java` — record (`InstrumentIdentity instrument`, `String name?`, `Sector sector` non-null, `String industry?`, `SupportedCurrency currency?`, `Instant lastUpdatedAt` non-null, `ProfileSource source?`) + `withLastUpdatedAt(Instant)`.
- [ ] T026 [P] [US3] `FI-M/domain/exceptions/` — `ExternalProviderException` (base) + `ExternalProviderUnavailableException`, `ExternalProviderRateLimitedException`, `ExternalProviderAuthenticationFailedException`, `ProviderNotConfiguredException`, `InstrumentProfileUnavailableException`.
- [ ] T027 [P] [US3] `FI-M/domain/ports/InstrumentProfilePort.java` (`InstrumentProfile getProfile(InstrumentIdentity)`), `InstrumentProfileRepositoryPort.java` (`findByInstrument` / `save` upsert), `InstrumentProfileLookup.java` (`InstrumentProfile get(InstrumentIdentity)` — inbound; javadoc: consumed cross-module like `FinancialInstrumentCatalog`, AR-062).
- [ ] T028 [P] [US3] `FI-T/domain/model/InstrumentProfileModelTest.java` + `SectorTest.java` — guard clauses, `UNCLASSIFIED`, `withLastUpdatedAt`.

### C3.2 — `GetInstrumentProfile` business use case (RED-first)

- [ ] T029 [P] [US3] `FI-T/business/GetInstrumentProfileTest.java` (Mockito: `InstrumentProfileRepositoryPort`, `InstrumentProfilePort`, fixed `Clock`) — **RED**: local hit ⇒ provider **never** called, returns local; local miss + provider ok ⇒ provider once, `save` once with `lastUpdatedAt=clock.instant()`, returns the **saved** profile; local miss + provider throws `ExternalProviderException` ⇒ `InstrumentProfileUnavailableException`, `save` **never** called. *(BR-EN005-001…006; VC-005…VC-009)*
- [ ] T030 [US3] `FI-M/business/GetInstrumentProfile.java` (`@Service implements InstrumentProfileLookup`) — DB-first algorithm ([research.md](./research.md) D6). Make T029 green.

### C3.3 — persistence stack + migration

- [ ] T031 [US3] `…/src/main/resources/db/migration/V5__instrument_profile.sql` — `instrument_profile` table per [data-model.md](./data-model.md) §3 (`UNIQUE(ticker, market_mic)`, no FK). **No** change to `financial_instrument` / `market` / FD001/FD004 tables.
- [ ] T032 [P] [US3] `FI-M/infrastructure/persistence/entity/InstrumentProfileEntity.java` (`@Entity @Table("instrument_profile")`, all columns, getters, protected ctor, `equals/hashCode` on id).
- [ ] T033 [P] [US3] `FI-M/infrastructure/persistence/repository/InstrumentProfileJpaRepository.java` — `extends JpaRepository<…, UUID>`; derived `Optional<…> findByTickerAndMarketMic(String, String)`; `@Modifying @Query(nativeQuery=true)` `int upsert(...)` (`INSERT … ON CONFLICT (ticker, market_mic) DO UPDATE SET …`).
- [ ] T034 [US3] `FI-M/infrastructure/persistence/mapper/InstrumentProfilePersistenceMapper.java` — domain ⇄ entity; `Sector.UNCLASSIFIED` ⇄ `'Unclassified'`; `ProfileSource` ⇄ string.
- [ ] T035 [US3] `FI-M/infrastructure/persistence/JpaInstrumentProfileRepositoryAdapter.java` (`@Repository implements InstrumentProfileRepositoryPort`) — own `TransactionTemplate` (readonly for find, write for `save`→`upsert` then re-read).
- [ ] T036 [P] [US3] `FI-T/infrastructure/persistence/InstrumentProfilePersistenceMapperTest.java` — round-trip incl. `UNCLASSIFIED`.
- [ ] T037 [US3] `FI-T/infrastructure/persistence/JpaInstrumentProfileRepositoryAdapterIT.java` (Testcontainers `PostgresContainerSupport`) — insert + read back; **two `save`s for the same identity ⇒ one row** (upsert); `findByInstrument` miss ⇒ empty; `V5` in `flyway_schema_history`.

### C3.4 — Finnhub profile provider adapter (moved into `financialinstrument`)

- [ ] T038 [P] [US3] `FI-M/infrastructure/finnhub/config/FinnhubProfileProperties.java` — `@ConfigurationProperties("finnhub")` read-only view of `api-key` + `base-url` (+ own timeouts).
- [ ] T039 [P] [US3] `FI-M/infrastructure/finnhub/dto/FinnhubCompanyProfileResponse.java` — moved verbatim from `marketdata` (`ticker`, `name`, `currency`, `exchange`, `finnhubIndustry`, `isEmpty()`).
- [ ] T040 [P] [US3] `FI-M/infrastructure/finnhub/resolver/{FinnhubSymbolResolver,FinnhubSymbolRule}.java` + `src/main/resources/reference-data/finnhub-symbol-map.csv` — **duplicated** into `financialinstrument` (research D9); adjust package + resource path.
- [ ] T041 [US3] `FI-M/infrastructure/finnhub/client/FinnhubProfileClient.java` (`@Component`) — own `RestClient`; `GET {base}/stock/profile2?symbol={symbol}` + `X-Finnhub-Token` header; `401/403`→auth, `429`→rate-limited, `5xx`/timeout→unavailable, `{}`→`InstrumentProfileUnavailableException`; `event=ProviderCall provider=finnhub capability=instrument-profile` log, no secret.
- [ ] T042 [US3] `FI-M/infrastructure/finnhub/mapper/FinnhubProfileMapper.java` — `(dto, InstrumentIdentity, Instant retrievedAt) → InstrumentProfile`; `finnhubIndustry` → `Sector.of(...)` verbatim (A6); `exchange` **not** a MIC (VC-010).
- [ ] T043 [US3] `FI-M/infrastructure/finnhub/FinnhubInstrumentProfileAdapter.java` (`@Component implements InstrumentProfilePort`, `@ConditionalOnProperty(name="market-data.profile.provider", havingValue="finnhub", matchIfMissing=true)`) — resolve symbol, call client, map.
- [ ] T044 [P] [US3] `FI-T/infrastructure/finnhub/FinnhubInstrumentProfileAdapterTest.java` + `FinnhubProfileMapperTest.java` + `FinnhubSymbolResolverTest.java` — moved/adapted from `marketdata`; `MockRestServiceServer`; `{}`→unavailable, `401/403`→auth, `429`→rate-limited; `exchange` ≠ MIC.
- [ ] T045 [US3] `FI-T/…/GetInstrumentProfileIT.java` (Testcontainers + `MockRestServiceServer`-backed Finnhub or a `@TestConfiguration` fake `InstrumentProfilePort`) — miss → provider → persisted → **2nd call is a local hit** (provider not called again).

### C3.5 — delete `marketdata` profile + wire FD004

- [ ] T046 [US3] **Delete** from `marketdata`: `domain/model/{InstrumentProfile,Sector}.java`, `domain/ports/InstrumentProfilePort.java`, `domain/exceptions/InstrumentProfileUnavailableException.java`, `infrastructure/finnhub/{FinnhubInstrumentProfileAdapter,mapper/FinnhubProfileMapper,dto/FinnhubCompanyProfileResponse}.java`, `infrastructure/finnhub/cache/CachingInstrumentProfilePort.java`, `getCompanyProfile(...)` from `FinnhubRestClient`, `PROFILE` from `FinnhubOperation`, `profile-ttl` from `FinnhubProperties` + `application.yml`. Delete the matching `MD-T` tests.
- [ ] T047 [US2] `PF/EnMarketDataGatewayAdapter.java` — `sector(ticker, market, currencyCode)` now calls `financialinstrument.domain.ports.InstrumentProfileLookup.get(new InstrumentIdentity(new Ticker(ticker), new Mic(market)))` → `profile.sector()`; `catch (RuntimeException)` → `Optional.empty()`. Drop the `marketdata` `InstrumentProfilePort` / `InstrumentProfile` / `Sector` imports; add the `financialinstrument` ones. `latestPrice(...)` / `fxRate(...)` unchanged.
- [ ] T048 [US2] `portfolio/.../EnMarketDataGatewayAdapterTest.java` — replace the `InstrumentProfilePort` mock with an `InstrumentProfileLookup` mock; keep assertions (classified→value, unclassified→empty, exception→empty). Add a bad-identity (blank ticker) → empty case.
- [ ] T049 [US7] **Checkpoint C3**: `./mvnw -B clean verify` green. `grep -rn "com.myfinaimanager.core.marketdata" FI-M/` → empty. FD004 + FD001/FD002/FD003 + EN004 suites green.

---

## Phase 5: Checkpoint C4 — E2E: combined `market-data-stub` (US7)

**Goal**: `./e2e.sh` runs offline with both provider boundaries stubbed; FD004 E2E-001/002 pass
unchanged (A10).

- [ ] T050 [US7] Rename `implementation/platform/e2e/finnhub-stub/` → `market-data-stub/`. `server.js`: keep `/quote`, `/stock/profile2`; **add** `/v1/latest?base=&symbols=` → `{ "amount":1.0, "base":"<BASE>", "date":"<today>", "rates": { "<SYM>": <rate> } }` with `USD→EUR = 0.80`, `EUR→USD = 1.25`; **remove** `/forex/rates`. Update `Dockerfile` (rename only).
- [ ] T051 [US7] `implementation/platform/infrastructure/local/compose.e2e.yaml` — service `finnhub-stub` → `market-data-stub` (image `finai/market-data-stub:local`); backend env `FINNHUB_BASE_URL: http://market-data-stub:8080` **and** `FRANKFURTER_BASE_URL: http://market-data-stub:8080`, `FINNHUB_API_KEY: e2e-stub`; `depends_on` + healthcheck renamed.
- [ ] T052 [US7] `implementation/platform/e2e.sh` — `FINNHUB_STUB_*` → `MARKET_DATA_STUB_*` (image `finai/market-data-stub:local`, context `e2e/market-data-stub`); update the build step + the health-wait service name.
- [ ] T053 [P] [US7] `implementation/platform/infrastructure/local/compose.yaml` — add `FRANKFURTER_BASE_URL: ${FRANKFURTER_BASE_URL:-https://api.frankfurter.dev}` to `backend.environment` (parity with `FINNHUB_BASE_URL`).
- [ ] T054 [US7] Run `./e2e.sh` — all specs green incl. `fd004-valuation.spec.ts` (totals `€2,100.00` / `$2,625.00`, weights `76.19 %` / `23.81 %` — **unchanged**) and `fd004-provider-failure.spec.ts`; no request to `finnhub.io` / `api.frankfurter.dev`.

**Checkpoint C4**: `./e2e.sh` green.

---

## Phase 6: Checkpoint C5 — Architecture, coverage, docs, runtime (US1, US7)

- [ ] T055 [US1] `StandardArchitectureRulesTest.java` — apply the [research.md](./research.md) D10 delta: rename `only_the_finnhub_client_package_uses_restclient` → `only_provider_client_packages_use_restclient` (allow `marketdata…finnhub.client`, `marketdata…frankfurter.client`, `financialinstrument…finnhub.client`); **add** `frankfurter_dto_and_client_types_are_confined_to_the_frankfurter_adapter`, `financialinstrument_domain_and_business_are_free_of_provider_http_json_types`, `financialinstrument_does_not_depend_on_marketdata`, `finnhub_profile_types_are_confined_to_the_financialinstrument_finnhub_adapter`. Keep the FD004 rules + `portfolio_touches_financialinstrument_only_via_its_domain_ports` (green — FD004 uses the `domain.ports.InstrumentProfileLookup`). Verify each new rule **non-vacuous** (temp violating import → fail → revert).
- [ ] T056 [US7] `./mvnw -B clean verify` — JaCoCo bundle ≥ 90 % line & branch; fix gaps with behavior tests (`FrankfurterFxRateAdapter` error paths, `JpaInstrumentProfileRepositoryAdapter`, mappers), **not** new excludes beyond T004.
- [ ] T057 [P] [US7] `implementation/platform/backend/core-service/README.md` — update the `marketdata` section (price Finnhub + FX **Frankfurter**, no key for FX, per-capability `market-data.*.provider` config, `TtlCache` for price+FX); add a `financialinstrument` "instrument-profile enrichment" section (DB-first, `V5`, `InstrumentProfileLookup`, moved Finnhub profile adapter).
- [ ] T058 [P] [US7] `implementation/platform/README.md` — "Provider integrations": FX now via Frankfurter (keyless); DB-first sector enrichment; `FRANKFURTER_BASE_URL`; the FD004 free-tier note updated (US equities → COMPLETED; EUR positions → PARTIAL).
- [ ] T059 [P] [US7] `infrastructure/local/.env.example` — add optional `# FRANKFURTER_BASE_URL=` (commented; default fine); keep the `FINNHUB_API_KEY` real-secret note.
- [ ] T060 [US7] Runtime smoke ([quickstart.md](./quickstart.md) §C + §D): `./start.sh` (no key) → health UP, `FinnhubIntegrationDisabled` logged, **no** Frankfurter-disabled log; create an all-US portfolio; `GET …/valuation` → `PARTIAL` (no Finnhub key) — **but** with `FINNHUB_API_KEY` in `.env`, an all-US portfolio → `COMPLETED` with a real EUR total (Frankfurter, no key), one `instrument_profile` row per instrument, and the 2nd valuation makes **no** profile provider call. `./stop.sh` ×2 idempotent.

**Checkpoint C5**: all gates green.

---

## Phase 7: Polish & closure evidence

- [ ] T061 [US7] `specs/EN005-…/pr-evidence.md` — record all gate results (mvnw verify, ng test, e2e.sh, runtime), the VC-001…VC-015 evidence map (quickstart §F), and the scope review (SC-008): one `V5` migration; Finnhub `/forex/rates` removed; `financialinstrument` ⊥ `marketdata`; no `openapi.yaml` change; no new deployable/broker/scheduler/caching-infra/dependency; no `product/` edit.
- [ ] T062 [US7] Scope review `git diff`: `V5` only migration; `pom.xml` change = JaCoCo excludes only; `compose.yaml` change = `FRANKFURTER_BASE_URL` passthrough only; `openapi.yaml` untouched; FD004 change limited to `EnMarketDataGatewayAdapter` + its test + the shared stub; **no `product/` or `.specify/` edit** (the enabler §29 approval is the product owner's).
- [ ] T063 [US7] Confirm the 2026-09-03 EN005 `closure-report.md` / `dod-checklist.md` are noted as **superseded** by Revision 2 (a one-line header note; do not delete history).

---

## Dependencies & execution order

- **Phase 1 (Setup)** → then the checkpoints **in order** (C1 → C2 → C3 → C4 → C5) — each must be
  `./mvnw verify` (or `./e2e.sh`) green before the next.
- **C1** is independent of C3 (FX vs profile). **C2** is light plumbing on top of C1.
- **C3** is the largest; T023–T028 (`[P]` domain) → T029 RED → T030 → T031–T037 persistence →
  T038–T045 provider adapter → T046 delete → T047–T048 FD004 wiring → T049 checkpoint.
- **C4** needs C1 (Frankfurter) + C3 (profile) done (the stub serves both).
- **C5** last.
- **Phase 7** after C5.

### Within a checkpoint

- Test tasks (RED) before implementation for `GetInstrumentProfile` (T029 before T030).
- Domain model/ports → business → infrastructure adapter/persistence → deletions → consumer wiring.
- `[P]` = different files, no ordering dependency.

## Parallel opportunities

- **Phase 1**: T002 + T003 + T004 `[P]`.
- **C1**: T005 + T006 + T007 (tests) `[P]`; T008 + T009 `[P]`.
- **C3.1**: T023–T028 all `[P]`. **C3.3**: T032 + T033 `[P]`; T036 `[P]`. **C3.4**: T038 + T039 + T040 `[P]`; T044 `[P]`.
- **C5**: T057 + T058 + T059 `[P]`.

## Implementation strategy

1. **MVP** = Phase 1 → **C1** (Frankfurter FX). Stop, run `./mvnw verify` + a manual `./start.sh`
   with a real key + an all-US portfolio → `COMPLETED` valuation with a real EUR total. This alone
   resolves the reported defect.
2. **C2** — formalize per-capability provider config (needed for VC-004 evidence).
3. **C3** — DB-first profile in `financialinstrument` (the enabler's largest new behavior).
4. **C4** — E2E offline with both boundaries stubbed.
5. **C5 + Phase 7** — architecture conformance, coverage, docs, closure evidence →
   `/project-verify EN005-establish-finnhub-market-data-integration`.

## Notes

- No SDD extension hooks (`.specify/extensions.yml` absent).
- Do not commit/push — the repo is intentionally uncommitted; closure is human-governed.
- EN005 adds **no** REST API — `openapi.yaml` and all contract tests are untouched.
- Every VC-001…VC-015 has an owning task — see [quickstart.md](./quickstart.md) §F.
