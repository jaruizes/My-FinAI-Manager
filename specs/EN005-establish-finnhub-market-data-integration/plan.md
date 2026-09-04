# Implementation Plan: Establish External Market Data Capabilities (EN005 — Revision 2)

**Branch**: `EN005-establish-finnhub-market-data-integration` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Authoritative enabler**: `product/definition/enablers/EN005-establish-finnhub-market-data-integration/EN005-establish-finnhub-market-data-integration.md`
(**Approved — Reopened for revision**; §29 signed by jaruiz 2026-09-04).

**Supersedes**: the 2026-09-03 EN005 plan/research/data-model/contracts/quickstart (implemented but
not closed/committed). This plan is a **delta** on the current tree.

**Clarifications (2026-09-04)**: **Q1** `financialinstrument` module owns the database-first
instrument-profile enrichment. **Q2** keep the current port + neutral-exception names
(`MarketDataPort` / `InstrumentProfilePort` / `FxRatePort`, `*Exception`). **Q3** keep the
in-process `TtlCache` + `CachingMarketDataPort` / `CachingFxRatePort`; remove
`CachingInstrumentProfilePort`.

## Summary

Three separable changes to the implemented EN005, each with its own green checkpoint:

1. **FX provider swap — Finnhub → Frankfurter** (fixes the real defect: Finnhub `/forex/rates` is a
   premium endpoint → `403` on the free plan). Add a `frankfurter` provider package in the
   `marketdata` module (own `RestClient`, `FrankfurterProperties`, `FrankfurterRatesResponse` DTO,
   `FrankfurterFxRateMapper`, `FrankfurterFxRateAdapter implements FxRatePort`). Add
   `DataSource.FRANKFURTER`. Wire `FxRatePort` → Frankfurter via per-capability config
   (`market-data.fx.provider=frankfurter`). **Delete** `FinnhubFxRateAdapter`,
   `FinnhubForexRatesResponse`, `FinnhubForexRatesMapper`, `FinnhubRestClient.getForexRates(...)`,
   `FinnhubOperation.FOREX_RATES`. `CachingFxRatePort` now decorates the Frankfurter adapter.
   **FD004 needs no change here** — it calls `FxRatePort.getRate(SupportedCurrency, SupportedCurrency)`,
   which is unchanged.
2. **Database-first instrument profile — move to `financialinstrument`** (Q1). New in
   `financialinstrument`: `domain.model.InstrumentProfile` + `Sector`; `domain.ports.InstrumentProfilePort`
   (external provider port — *name kept*, Q2) + `domain.ports.InstrumentProfileRepositoryPort`;
   `domain.exceptions.InstrumentProfileUnavailableException`;
   `business.GetInstrumentProfile(UseCase)` — **DB-first**: repo → local hit returns (no provider
   call) → miss calls the provider → normalise (`lastUpdatedAt = now`) → **upsert** → return;
   `infrastructure.persistence.{entity,repository,mapper}` + `JpaInstrumentProfileRepositoryAdapter`;
   `infrastructure.finnhub.{FinnhubInstrumentProfileAdapter, client, dto, mapper, config}` (moved
   from `marketdata`, gains its **own** `RestClient`). New Flyway `V5__instrument_profile.sql`
   (dedicated enrichment table keyed on `ticker + market_mic`; EN004 catalog tables untouched).
   **Delete** from `marketdata`: `InstrumentProfilePort`, `InstrumentProfile`, `Sector`,
   `FinnhubInstrumentProfileAdapter`, `FinnhubProfileMapper`, `FinnhubCompanyProfileResponse`,
   `FinnhubInstrumentProfileAdapter` config, `CachingInstrumentProfilePort`,
   `FinnhubRestClient.getCompanyProfile(...)`, `FinnhubOperation.PROFILE`.
3. **Per-capability provider selection + telemetry + E2E**. `application.yml` gains
   `market-data.{price,profile,fx}.provider` keys; adapter beans select via
   `@ConditionalOnProperty(matchIfMissing = true)` — **no** provider branch in `domain`/`business`
   (OD-2). Each adapter's structured log gains `capability=`. E2E: the `finnhub-stub` container
   becomes a combined **`market-data-stub`** serving `/quote`, `/stock/profile2` **and**
   `/v1/latest` (Frankfurter); `compose.e2e.yaml` sets `FINNHUB_BASE_URL` **and**
   `FRANKFURTER_BASE_URL` to it; `e2e.sh` builds it. FD004 E2E-001 / E2E-002 keep their current
   portfolios (A10 — the stub keeps faking the non-US quote).

**FD004 consumer delta** (the only consumer): `portfolio.infrastructure.marketdata.EnMarketDataGatewayAdapter`
— `latestPrice(...)` unchanged; `fxRate(...)` unchanged (port unchanged, now Frankfurter-backed);
`sector(...)` changes from `marketdata`'s `InstrumentProfilePort.getProfile` to
`financialinstrument`'s `GetInstrumentProfileUseCase.get(InstrumentIdentity)` + catch its neutral
exception → `Optional.empty()`. `EnMarketDataGatewayAdapterTest` updated. **`portfolio.domain` /
`portfolio.business` / the calculator / persistence / API / UI are byte-unchanged** (the FD004
`MarketDataGateway` ACL absorbs it).

## Technical Context

**Language / Runtime**: Java 21, Spring Boot 3.5.6 (unchanged). No frontend change.

**Primary Dependencies**: all reused — Spring `RestClient`, Jackson, Spring Data JPA + Flyway (one
new migration), the existing `TtlCache`, Testcontainers, `swagger-request-validator` (unaffected —
no contract change), ArchUnit. **No new Maven/npm dependency.** Frankfurter is a plain HTTPS API
(no SDK, no key).

**Storage**: PostgreSQL 16. **One new Flyway forward migration** `V5__instrument_profile.sql` adds
`instrument_profile` (dedicated enrichment table, owned by `financialinstrument`). EN004
(`V3`), FD001 (`V2`), FD004 (`V4`) schemas **unchanged**. `ddl-auto: none`.

**Testing**: `./mvnw -B clean verify` (Surefire unit + Failsafe/Testcontainers + JaCoCo `check` +
ArchUnit); `ng test` (unaffected); `./e2e.sh`. `GetInstrumentProfile` DB-first orchestration is
**RED-first** (deterministic logic). Provider adapters tested with `MockRestServiceServer` /
WireMock — no live provider. New Testcontainers ITs for the profile persistence + DB-first flow.

**Target Platform**: the existing `core-service` container (ADR-001). `compose.yaml` gains an
optional `FRANKFURTER_BASE_URL` passthrough (default fine); `compose.e2e.yaml` swaps the
`finnhub-stub` service for a combined `market-data-stub`.

**Performance Goals**: DB-first profile turns the steady-state sector lookup into a single indexed
`SELECT` (no provider round-trip). FX still cache-served (`CachingFxRatePort`, TTL unchanged).

**Constraints**:
- ADR-003 module layout; ArchUnit-enforced across `marketdata` **and** `financialinstrument`.
- Provider-neutral core: no Finnhub/Frankfurter type in any `domain`/`business`; no provider-identity
  branch in core (FR-006).
- Secret safety: Finnhub key only as `X-Finnhub-Token` header, never logged/URL'd; Frankfurter adds
  **no** secret (FR-034).
- Decimal-safe: `BigDecimal` for price + rate (FR-011).
- No new deployable / broker / scheduler / caching infrastructure (FR-041).
- EN004 catalog schema + canonical identity model unchanged (FR-042).
- FD001/FD002/FD003/FD004 behavior, contracts, tests, E2Es unchanged except FD004's ACL adapter +
  its test + the shared E2E stub (FR-038, FR-039, FR-040).
- `./start.sh` / `./stop.sh` / `./e2e.sh` interface unchanged.

**Scale/Scope**: ~1 new provider package (`frankfurter`, 5 classes) + deletions in `marketdata`;
~1 new capability area in `financialinstrument` (2 domain ports + 1 model + 1 use case + persistence
stack + migration + moved Finnhub profile adapter, ~15 classes) + deletions; ~3 lines of
per-capability config + `@ConditionalOnProperty` on 3 beans; FD004 adapter `sector(...)` rewrite +
test; E2E stub merge. ArchUnit ~+3 / ~±3 rules. No new module, no new deployable.

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 (below).*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | Human-Governed Source of Truth | **PASS** | Implements the human-approved revised enabler (§29 signed 2026-09-04, all boxes). No `product/` edit by this work. |
| II | Definitions & Enablers Are Authoritative Intent | **PASS** | Every FR traces to an enabler §/BR/VC (spec Traceability). One enabler, no scope expansion — Frankfurter, DB-first profile, adapter separation are all in the signed enabler. |
| III | Derived Artifacts & Repository Layout | **PASS** | Artifacts under `specs/EN005-…/`; code under `implementation/platform/`; no root `src/`/`apps/`. Prior EN005 artifacts revised in place (§28 — not formally closed). |
| IV | No Invention; Surface Material Ambiguity | **PASS** | The three structural ambiguities the enabler left open (§17 module, §3/§19 naming, §14/§26 caching) were **surfaced as Q1–Q3 and answered by the product owner**. Frankfurter (new external provider + outbound dependency) is explicitly approved in §29 — not invented. Remaining ODs (below) are technical, with recommended positions. |
| V | Technical Enablers Stay Technical | **PASS** | No investor-facing behavior, no Portfolio/valuation rule, no UI, no forced user stories. Scenarios are developer/operator facets tied to VCs. |
| VI | Hexagonal Architecture & Deterministic Logic | **PASS** | `domain`/`business` of both `marketdata` and `financialinstrument` stay framework- and provider-free (ArchUnit). `GetInstrumentProfile` DB-first orchestration is pure-ish business logic behind ports. Provider selection is infrastructure wiring, no core branch (FR-006). No LLM anywhere. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS** | `GetInstrumentProfile` (local-hit-no-call / miss-calls-and-persists / fail-persists-nothing) is TDD'd RED-first. Profile persistence + DB-first flow use Testcontainers PostgreSQL. Provider adapters stubbed (true external providers — constitution VII carve-out). No manually-installed infra. |
| VIII | Contract-First External APIs | **PASS (N/A for a new API)** | EN005 adds **no** external REST API — in-process ports + business operations only (unchanged from the 2026-09-03 clarification). `openapi.yaml` untouched. The provider HTTP contracts (Finnhub `/quote`, `/stock/profile2`; Frankfurter `/v1/latest`) are documented in `contracts/` and pinned by adapter stub tests. |

**Repository-structure / technology-policy quick check:**

| Check | Status | Evidence |
|---|---|---|
| One `core-service` deployable (ADR-001) | PASS | no new service; `compose.yaml` unchanged except an optional env passthrough |
| ADR-003 module layout in `marketdata` + `financialinstrument` | PASS | new code placed per `domain`/`business`/`infrastructure`; provider code confined to provider packages |
| No new technology / dependency | PASS | `RestClient` + Jackson + JPA + Flyway + `TtlCache`; `pom.xml` / `package.json` untouched |
| New external provider (Frankfurter) | PASS | approved in enabler §29; infrastructure-only; no key; no SDK |
| Schema change uses the approved migration mechanism | PASS | `V5__instrument_profile.sql` (Flyway forward migration); EN004/FD001/FD004 migrations untouched |
| No Portfolio business behavior; no LLM | PASS | FR-041; the enabler adds data-access capability only |
| Secret handling | PASS | FR-034; Finnhub key header-only; Frankfurter keyless; logging test |

**Result: PASS** (pre-design). Re-checked post-design below.

## Open Decisions (technical) — recommended positions

| ID | Decision | Recommended position | Rejected |
|---|---|---|---|
| OD-1 | Provider package layout | **Minimal**: `marketdata/infrastructure/finnhub/` (now price-only) + new `marketdata/infrastructure/frankfurter/`; `financialinstrument/infrastructure/finnhub/` for the moved profile adapter. Each is already a provider-scoped package satisfying FR-003; a full `infrastructure/provider/{…}/` rename is churn with no ArchUnit benefit. | Full `infrastructure/provider/finnhub|frankfurter/` restructure across both modules — large diff, no conformance gain (the enabler §17 says "package names may be refined during planning"). |
| OD-2 | Provider-selection mechanism | **`@ConditionalOnProperty(prefix + name, havingValue, matchIfMissing = true)`** on each provider adapter bean, keyed on `market-data.<capability>.provider`. One provider per capability today ⇒ `matchIfMissing` keeps it working with no config. The caching decorators stay `@Primary` and inject the single active provider bean. No registry, no factory, no profiles. | A `Map<String, XxxProviderPort>` registry + a selector bean — more machinery than one-per-capability needs; revisit when a second provider is actually added (VC-013 stays satisfiable — a second `@ConditionalOnProperty` bean drops in). Spring profiles — couples provider choice to the deployment profile. |
| OD-3 | E2E stub | **Rename `finnhub-stub` → `market-data-stub`**, one container serving `/quote`, `/stock/profile2`, `/v1/latest`. `FINNHUB_BASE_URL` and `FRANKFURTER_BASE_URL` both point at it. | A separate `frankfurter-stub` service — a second tiny container + a second compose service + a second `e2e.sh` build step for ~15 lines of routing. |
| OD-4 | Profile identity type in `financialinstrument` | **Reuse the existing `financialinstrument.domain.model.InstrumentIdentity(Ticker, Mic)`** for the profile ports (ticker + MIC is already the canonical, DB-unique key). Currency is carried on the resulting `InstrumentProfile`, not the key. | A new identity VO — duplication; `InstrumentIdentity` already exists and matches EN004 §7. Importing `marketdata`'s `InstrumentIdentifier` — cross-module domain coupling. |
| OD-5 | `GetInstrumentProfile` outcome shape | **Return `InstrumentProfile`; throw `InstrumentProfileUnavailableException` (neutral) only when neither local nor provider can supply one.** A local hit always returns. Matches FD004's adapter which already catches a neutral exception → `Optional.empty()`. | `Optional<InstrumentProfile>` / a result record — FD004's `EnMarketDataGatewayAdapter.sector` already has a try/catch shape; a second style is inconsistent. |
| OD-6 | `instrument_profile` table FK | **Standalone identity columns** `ticker` + `market_mic` (+ `currency` for display), `UNIQUE(ticker, market_mic)`, **no** FK to `financial_instrument`. Keeps the enrichment table decoupled (enabler §20A: EN004 may hold instruments EN005 can't enrich; the reverse — an enrichment row for a de-catalogued instrument — is harmless). | `financial_instrument_id UUID REFERENCES financial_instrument(id) ON DELETE CASCADE` — couples the enrichment lifecycle to catalog rows and forces a join for every lookup (lookup is by ticker+MIC). |
| OD-7 | `FrankfurterFxRateAdapter` HTTP | **Own `RestClient` built inside the frankfurter package** from `FrankfurterProperties` (base URL + timeouts), mirroring `FinnhubRestClient`'s construction. Matches enabler §6.1 ("the adapter owns HTTP invocation"). | Share `FinnhubRestClient` / a generic client bean — couples two providers; the enabler forbids a shared all-purpose provider adapter. |
| OD-8 | Sequencing (green checkpoints) | **C1** FX swap (Frankfurter in, Finnhub FX out) → `mvnw verify` green. **C2** per-capability config + telemetry `capability=` → green. **C3** DB-first profile in `financialinstrument` + FD004 `sector(...)` rewrite + delete `marketdata` profile → green. **C4** E2E stub merge + FD004 E2E → `e2e.sh` green. **C5** ArchUnit/JaCoCo/docs/runtime. | Big-bang — no intermediate green state; a mid-way failure is hard to localize. |

## Project Structure

### Documentation (this feature)

```text
specs/EN005-establish-finnhub-market-data-integration/
├── plan.md              # this file (Revision 2 — supersedes the 2026-09-03 plan)
├── research.md          # Phase 0 — D1…D13 (rewritten)
├── data-model.md        # Phase 1 — provider-neutral models + V5 schema + profile persistence (rewritten)
├── contracts/
│   ├── market-data-ports.md            # UPDATED — 3 ports; profile port now in financialinstrument; FX semantics
│   ├── finnhub-provider-contract.md    # UPDATED — /quote + /stock/profile2 only (no /forex/rates)
│   └── frankfurter-provider-contract.md # NEW — GET /v1/latest?base=&symbols= request/response + mapping + failure table
├── quickstart.md        # Phase 1 — A…H validation (rewritten: FX via Frankfurter, DB-first profile, both stubs)
├── checklists/requirements.md   # 16/16 (passing after Q1–Q3)
├── spec.md              # Revision 2 (done)
└── tasks.md             # Phase 2 — /speckit-tasks (NOT this command; supersedes the 2026-09-03 tasks)
```

### Source Code (repository)

```text
implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/

marketdata/                                    # AFTER: price (Finnhub) + FX (Frankfurter) only
├── domain/
│   ├── model/  DataSource.java                # + FRANKFURTER
│   │           MarketPrice.java · FxRate.java · InstrumentIdentifier.java · SupportedCurrency.java
│   │           ObservedAtSource.java
│   │           ── InstrumentProfile.java · Sector.java            (DELETE — move to financialinstrument)
│   ├── ports/  MarketDataPort.java · FxRatePort.java
│   │           ── InstrumentProfilePort.java                       (DELETE — move)
│   └── exceptions/  (keep MarketData*/FxRate*/Provider*/InstrumentNotResolved;
│                     ── InstrumentProfileUnavailableException.java (DELETE — move))
└── infrastructure/
    ├── config/  FinnhubProperties.java (price only) · MarketDataModuleConfiguration.java
    │            + FrankfurterProperties.java
    ├── finnhub/                               # price only now
    │   ├── FinnhubMarketDataAdapter.java   (@ConditionalOnProperty market-data.price.provider)
    │   ├── client/FinnhubRestClient.java   (── getCompanyProfile / getForexRates removed)
    │   ├── client/FinnhubOperation.java    (QUOTE only)
    │   ├── dto/FinnhubQuoteResponse.java
    │   ├── mapper/FinnhubQuoteMapper.java
    │   ├── resolver/FinnhubSymbolResolver.java · FinnhubSymbolRule.java
    │   ├── ── FinnhubInstrumentProfileAdapter / FinnhubProfileMapper / FinnhubCompanyProfileResponse  (DELETE — move)
    │   └── ── FinnhubFxRateAdapter / FinnhubForexRatesResponse / FinnhubForexRatesMapper             (DELETE)
    ├── frankfurter/                           # NEW
    │   ├── FrankfurterFxRateAdapter.java   (implements FxRatePort; @ConditionalOnProperty market-data.fx.provider, matchIfMissing)
    │   ├── client/FrankfurterRestClient.java
    │   ├── dto/FrankfurterRatesResponse.java   (amount, base, date, Map<String,BigDecimal> rates)
    │   └── mapper/FrankfurterFxRateMapper.java
    └── finnhub/cache/  TtlCache.java · CachingMarketDataPort.java · CachingFxRatePort.java
                        ── CachingInstrumentProfilePort.java        (DELETE)
        (CachingFxRatePort now decorates FrankfurterFxRateAdapter)

financialinstrument/                           # AFTER: catalog (EN004) + instrument-profile enrichment (EN005 R2)
├── domain/
│   ├── model/  + InstrumentProfile.java · Sector.java
│   ├── ports/  + InstrumentProfilePort.java            (external provider port — name kept, Q2)
│   │           + InstrumentProfileRepositoryPort.java  (findByInstrument / save[upsert])
│   └── exceptions/  + InstrumentProfileUnavailableException.java
├── business/  + GetInstrumentProfileUseCase.java · GetInstrumentProfile.java   (@Service; DB-first)
└── infrastructure/
    ├── persistence/
    │   ├── entity/InstrumentProfileEntity.java
    │   ├── repository/InstrumentProfileJpaRepository.java   (derived; findByTickerAndMarketMic)
    │   ├── mapper/InstrumentProfilePersistenceMapper.java
    │   └── JpaInstrumentProfileRepositoryAdapter.java       (implements InstrumentProfileRepositoryPort; own tx templates)
    ├── finnhub/
    │   ├── FinnhubInstrumentProfileAdapter.java   (implements InstrumentProfilePort; @ConditionalOnProperty market-data.profile.provider; own RestClient)
    │   ├── client/FinnhubProfileClient.java
    │   ├── dto/FinnhubCompanyProfileResponse.java
    │   ├── mapper/FinnhubProfileMapper.java
    │   └── config/FinnhubProfileProperties.java   (@ConfigurationProperties — reuses FINNHUB_API_KEY / FINNHUB_BASE_URL)
    └── config/  (module @Configuration if needed)

portfolio/infrastructure/marketdata/EnMarketDataGatewayAdapter.java   # sector(...) → financialinstrument GetInstrumentProfileUseCase

architecture/StandardArchitectureRulesTest.java   # ± rules (see research D10)

src/main/resources/
├── db/migration/V5__instrument_profile.sql        # NEW
└── application.yml                                # + market-data.{price,profile,fx}.provider ; + frankfurter.base-url ${FRANKFURTER_BASE_URL:https://api.frankfurter.dev}

pom.xml   # + JaCoCo excludes: financialinstrument/infrastructure/finnhub/{dto,config}/** ; marketdata/infrastructure/frankfurter/dto/**

implementation/platform/
├── infrastructure/local/compose.yaml       # + FRANKFURTER_BASE_URL passthrough (optional)
├── infrastructure/local/compose.e2e.yaml   # finnhub-stub → market-data-stub ; + FRANKFURTER_BASE_URL
├── e2e.sh                                   # build market-data-stub (was finnhub-stub)
└── e2e/
    ├── finnhub-stub/  → renamed  market-data-stub/  (server.js serves /quote, /stock/profile2, /v1/latest ; drop /forex/rates)
    └── tests/fd004-*.spec.ts                # unchanged (A10) ; support/valuation.ts unchanged

frontend/   # NO CHANGE
```

**Structure Decision**: `marketdata` keeps price + FX (Finnhub price adapter, new Frankfurter FX
adapter, shared `TtlCache` for both); the whole instrument-profile capability (provider port + repo
port + use case + persistence + Finnhub profile adapter) **moves to `financialinstrument`** per Q1.
FD004's `EnMarketDataGatewayAdapter` gains a dependency on `financialinstrument.business` for
`sector(...)` and keeps `marketdata` for price/FX. One new Flyway migration. No new module, no new
deployable.

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Deleting `marketdata` profile code breaks FD004 build mid-refactor | High | Med | Sequenced (OD-8 C3): move + wire FD004 in the same step; `mvnw -q compile` after each sub-step; the `MarketDataGateway` ACL means only one FD004 class changes. |
| `CachingFxRatePort` / `CachingMarketDataPort` bean wiring breaks when the delegate changes | Med | Med | The decorators inject `FxRatePort` / `MarketDataPort` by type; with exactly one non-`@Primary` provider bean (guarded by `@ConditionalOnProperty matchIfMissing`) resolution is unambiguous. `FinnhubIntegrationIT` + a new `ProviderSelectionTest` assert the wired beans. |
| `financialinstrument` accidentally depends on `marketdata` (profile moved but an import lingers) | Med | Med | ArchUnit: `financialinstrument` must not depend on `marketdata`; non-vacuous (both modules have classes). Verified by a deliberate temp import. |
| Frankfurter response shape drift (`rates` map, `date`) | Low | Med | `frankfurter-provider-contract.md` pins the shape from the enabler §8A example; `FrankfurterFxRateAdapterTest` (MockRestServiceServer) covers happy + missing-pair + malformed + 5xx; the E2E stub uses the same shape. |
| Profile DB-first: concurrent miss double-fetch + duplicate insert | Low | Med | `save` is an **upsert** on `(ticker, market_mic)` (`INSERT … ON CONFLICT DO UPDATE`); `JpaInstrumentProfileRepositoryAdapterIT` asserts two saves ⇒ one row. |
| `V5` migration collides with a parallel schema change | Low | Med | `V5` is the next free version; only a new table; no EN004/FD001/FD004 table touched; a `SchemaIntegrityIT`-style check that Hibernate does not alter it. |
| E2E stub rename churn breaks `./e2e.sh` | Med | Med | `e2e.sh` + `compose.e2e.yaml` updated together; `docker compose config` dry-run; the rename is E2E-only (no `./start.sh` impact). |
| ArchUnit rule churn (profile types moved) leaves a stale/vacuous rule | Med | Low | Rewrite the `marketdata` Finnhub-confinement rule to price-only scope; add `frankfurter` confinement + `financialinstrument` provider confinement; each checked non-vacuous. |
| Losing the opt-in Finnhub smoke test's forex coverage | Low | Low | `FinnhubProviderSmokeTest` drops the forex probe, adds a Frankfurter live probe (still opt-in, still skipped in CI). |
| Coverage dip from moved/new code | Med | Med | New deterministic code (`GetInstrumentProfile`, `FrankfurterFxRateMapper`, persistence adapter) is TDD'd; JaCoCo excludes only DTO/config packages; `mvnw verify` is the gate. |

## Phase 0 — Research

See [research.md](./research.md). Decisions **D1–D13**: D1 Frankfurter adapter + client + DTO +
mapper + `DataSource.FRANKFURTER`; D2 removal of Finnhub FX (adapter/DTO/mapper/client method/enum);
D3 per-capability provider config + `@ConditionalOnProperty matchIfMissing` selection (OD-2); D4
`financialinstrument` profile domain model (`InstrumentProfile`, `Sector`, reuse `InstrumentIdentity`
— OD-4); D5 `InstrumentProfilePort` (provider) + `InstrumentProfileRepositoryPort` in
`financialinstrument`; D6 `GetInstrumentProfile` DB-first orchestration + outcome shape (OD-5); D7
`V5__instrument_profile.sql` (standalone identity, upsert — OD-6); D8 `JpaInstrumentProfileRepositoryAdapter`
+ persistence stack; D9 move `FinnhubInstrumentProfileAdapter` to `financialinstrument` with its own
`RestClient`; D10 ArchUnit rule delta; D11 FD004 `EnMarketDataGatewayAdapter.sector(...)` rewrite +
test; D12 E2E `market-data-stub` merge + `FRANKFURTER_BASE_URL` + FD004 E2E unchanged (A10); D13
telemetry `capability=` + JaCoCo excludes + the full test matrix + sequencing (OD-8). No
`NEEDS CLARIFICATION`.

## Phase 1 — Design & Contracts

Outputs: [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md).

**Post-Design Constitution re-check: PASS** — the design keeps `domain`/`business` of both modules
framework- and provider-free (ArchUnit), adds no provider-identity branch to core, uses one Flyway
migration in the approved mechanism, keeps EN004's schema and canonical identity intact, TDD's the
DB-first orchestration, uses Testcontainers for the profile persistence, stubs both external
providers for CI, adds no external REST API, no LLM, no scheduler, no broker, no new deployable, no
new dependency. The only human-material decision (adopting Frankfurter as a second external
provider) is explicitly approved in the enabler §29.
