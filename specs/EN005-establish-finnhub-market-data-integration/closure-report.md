# EN005 — Establish Finnhub Market Data Integration · Closure Report

**Verified:** 2026-09-03 · **Verifier:** `/project-verify` (evidence-based gate, no code modified)

## Final Result

**READY TO CLOSE WITH WARNINGS**

## Summary

EN005 delivers exactly its approved technical intent: a **provider-neutral** backend capability to
obtain latest price, company profile/sector, and USD↔EUR FX rate, with **Finnhub** as the initial
provider, isolated entirely behind three independent outbound ports and one adapter. It adds a new
`marketdata` module inside `core-service` (ADR-001/003) with `domain` + `infrastructure` only —
**no** persistence/migration, **no** external REST API, **no** frontend, **no** new Maven
dependency, **no** LLM, **no** valuation arithmetic, **no** `product/` edit.

All gates pass fresh: `./mvnw -B clean verify` **BUILD SUCCESS** (Surefire 181 + Failsafe 62, 0
failures; **3 skipped** = the opt-in smoke test); JaCoCo bundle **line 97.04 % / branch 91.81 %**
(gate met); ArchUnit **18/18** with a deliberate-violation check re-confirmed; runtime `./start.sh`
→ platform healthy with the module and the `finnhub:` config, `event=FinnhubIntegrationDisabled`
logged (no key present), FD001/FD002/FD003 endpoints still `200`, no EN005 endpoint exists →
`./stop.sh` (idempotent).

The only findings are non-blocking: the working tree is uncommitted (repo norm), and two small
implementation choices deviate from the plan's sketch (both safe, reversible, and recorded in
`pr-evidence.md`).

## Scope Compliance

| Enabler §3 item | Status | Evidence |
|---|---|---|
| Configure Finnhub via an API Key, key outside source control | PASS | `FinnhubProperties` bound from `finnhub.api-key: ${FINNHUB_API_KEY:}` (empty default); no key committed (repo + artifact scan clean) |
| Provider-neutral domain ports for price / profile / FX | PASS | `MarketDataPort`, `InstrumentProfilePort`, `FxRatePort` in `marketdata.domain.ports` — 3 independent interfaces |
| Finnhub adapters for those ports | PASS | `Finnhub{MarketData,InstrumentProfile,FxRate}Adapter` + `FinnhubRestClient` in `infrastructure.finnhub` |
| Use `/quote` for price | PASS | `FinnhubRestClient.quote` + `FinnhubQuoteMapper` (`price = c`) |
| Use `/stock/profile2` for profile incl. sector | PASS | `FinnhubProfileMapper` (`finnhubIndustry → Sector`, `name`, `currency`, `exchange`) |
| Use `/forex/rates` for FX; USD↔EUR both directions | PASS | `FinnhubFxRateAdapter` fetches each direction independently (`base=from`) |
| Preserve timestamp/source freshness metadata | PASS | `MarketPrice` / `FxRate` carry `observedAt` + `ObservedAtSource` + `source` |
| Explicit failure behavior when data unavailable/incomplete | PASS | 7-member neutral exception set; `c=0` / missing FX pair → `*Unavailable`, never fabricated |
| Deterministic tests without live Finnhub | PASS | `MockRestServiceServer` for every provider test; `./mvnw verify` green offline; smoke test opt-in |
| Preserve ADR-003 Spring architecture | PASS | ArchUnit 18/18; module-first `domain`/`infrastructure` |
| Use Maven | PASS | `./mvnw` wrapper; no Gradle |
| OpenTelemetry-compatible observability already established | PASS | one structured `event=FinnhubCall` log per call via the platform's ECS mechanism; no new observability infra (enabler §24 — platform has no outbound-HTTP tracing yet) |
| **Out of scope** — valuation rules/UI, sector-allocation UI, historical price/FX/valuation, intraday charts, AI valuation/classification, news, recommendations, Stop-Loss, direct frontend→Finnhub, replacing EN004, Finnhub exchange as canonical MIC | PASS (all absent) | no controller, no frontend, no schema; no LLM refs in `marketdata`; `providerExchange` is a metadata string, never mapped to a MIC (`FinnhubProfileMapperTest`); EN004 code untouched |
| Scope expansion | NONE | only additive: 1 module, 1 CSV, `application.yml` `finnhub:` block, 2 JaCoCo excludes, +4 ArchUnit rules, 2 README edits |

## Requirement Coverage — Verification Criteria (EN005 §28)

| VC | Status | Evidence |
|---|---|---|
| VC-001 API Key configuration | PASS | `FinnhubProperties` + `application.yml`; `FinnhubConfigurationTest`; runtime `FinnhubIntegrationDisabled` log |
| VC-002 Secret protection (not committed / returned / traced / logged) | PASS | key sent as `X-Finnhub-Token` **header**; `FinnhubRestClientLoggingTest` + `FinnhubRestClientTest` assert no key in any message/log even when the provider error body echoes it; repo/artifact grep clean |
| VC-003 Quote obtained + normalized | PASS | `FinnhubQuoteMapperTest`, `FinnhubMarketDataAdapterTest` (`MockRestServiceServer`) |
| VC-004 Profile obtained + normalized | PASS | `FinnhubProfileMapperTest`, `FinnhubInstrumentProfileAdapterTest` |
| VC-005 Sector/industry exposed when available | PASS | `FinnhubProfileMapperTest` — present → classified `Sector`; absent/blank → `Sector.UNCLASSIFIED` (zero inference) |
| VC-006 Exchange independence (Finnhub `exchange` ≠ canonical MIC) | PASS | mapper carries `exchange` verbatim as `providerExchange`; `assertThat(providerExchange).isNotEqualTo("XNAS")`; ArchUnit + code review |
| VC-007 USD → EUR rate | PASS | `FinnhubForexRatesMapperTest`, `FinnhubFxRateAdapterTest` (`base=USD` → `quote.EUR`) |
| VC-008 EUR → USD rate | PASS | same, independent fetch (`base=EUR` → `quote.USD`) |
| VC-009 Business/domain depend on provider-neutral ports | PASS | ArchUnit rules: Finnhub `dto`/`client` confined; `marketdata.domain` free of `web`/`http`/`jackson`; only `…finnhub.client` uses `RestClient` |
| VC-010 Decimal-safe values | PASS | `BigDecimal` for every price/rate; `grep` finds no `double`/`float` in `marketdata`; `MarketDataModelTest` rejects null/`≤0` |
| VC-011 Freshness/source metadata on price + FX | PASS | `observedAt` + `ObservedAtSource` (`PROVIDER_TIMESTAMP` vs `RETRIEVAL_TIME`) + `source` on `MarketPrice`/`FxRate`; mapper tests; `CachingPortsTest` (not rewritten by the cache) |
| VC-012 Missing price/sector/FX not zeroed/fabricated | PASS | `c=0`/absent → `MarketDataUnavailableException`; missing FX pair / `0` → `FxRateUnavailableException`; missing sector → `UNCLASSIFIED` |
| VC-013 Rate limits recognized explicitly | PASS | HTTP `429` → `ProviderRateLimitedException` (distinct from auth/other); tests on all 3 paths |
| VC-014 Authentication failure explicit | PASS | `401`/`403` → `ProviderAuthenticationFailedException`; blank key → `MarketDataNotConfiguredException`; `FinnhubConfigurationTest` + `FinnhubIntegrationIT` |
| VC-015 No frontend Finnhub calls | PASS | EN005 adds no frontend code; no `frontend/**` in the diff; FR-025 |
| VC-016 Tests run without live Finnhub | PASS | `./mvnw verify` green with **no** `FINNHUB_API_KEY`, no network beyond image pulls; `FinnhubProviderSmokeTest` skipped |
| VC-017 ADR-003 compliance | PASS | `StandardArchitectureRulesTest` **18/18**; deliberate-violation check |
| VC-018 Future EUR/USD Portfolio valuation ready | PASS | price (decimal, dated, currency) + both FX directions behind ports (`contracts/market-data-ports.md` C1/C3) |
| VC-019 Future sector allocation ready | PASS | `InstrumentProfile.sector` — classified string or explicit `UNCLASSIFIED` (contract C2) |

## Explicit Technical Decisions (EN005 §30) — all honored

1–15 all PASS: Finnhub as initial provider; API-Key auth; `/quote` price; `/stock/profile2`
profile+sector; `/forex/rates` EUR/USD; three preserved ports; EN004 stays canonical; Finnhub
exchange is metadata only; valuation arithmetic stays outside the adapter (`FinnhubFxRateAdapter`
returns rates only); `BigDecimal` throughout; missing data never fabricated; frontend never accesses
Finnhub; automated tests need no live Finnhub; freshness visible; caching preserves freshness
(`CachingPortsTest`).

## Architecture

| Check | Status | Evidence |
|---|---|---|
| ADR-001 one `core-service` deployable | PASS | no new service; `compose.yaml` unchanged |
| ADR-003 module-first (`domain`/`business`/`infrastructure`, deps inward) | PASS | `marketdata` = `domain` + `infrastructure` (no `business` — outbound ports only); ArchUnit direction rules (module-wildcarded) apply |
| Provider isolation | PASS | Finnhub SDK-free adapter; DTOs/client/`RestClient`/HTTP/Jackson confined to `..marketdata.infrastructure.finnhub..` — 4 new ArchUnit rules, non-vacuous, deliberate-violation-checked |
| Module boundaries / no cross-module coupling | PASS | ArchUnit rule 4 — `marketdata` depends on neither `portfolio` nor `financialinstrument`; verified by grep |
| Persistence ownership | N/A | EN005 owns no data; no schema |
| Public API boundary | N/A | no external REST API added |
| Sync/async decision | PASS | synchronous outbound calls; no event, no broker |
| Repository structure | PASS | under `implementation/platform/backend/core-service/`; no root `src/`/`apps/`/`services/` |
| `architecture.md` alignment | PASS | `architecture.md` already documents a "Market Data Port → Provider A / Provider B" pattern (§"Market Data Port") and an illustrative module tree incl. `valuation/` — EN005 realizes that pattern; no drift introduced |

## Technology Policy

| Technology | Policy | Used | Result |
|---|---|---:|---|
| Spring `RestClient` (`spring-web`) | ALLOWED (Spring HTTP client) | Yes (existing) | PASS |
| In-process TTL cache (`TtlCache`, hand-rolled) | Cache — CONDITIONAL | Yes | PASS — introduced for a concrete need (Finnhub rate limits, enabler §19/§20), in-process, **no Redis / external cache** (AR-022, AR-046) |
| Spring `MockRestServiceServer` (`spring-test`) | Testing — mocks/stubs for true external providers | Yes (existing) | PASS — constitution VII: Testcontainers is N/A for an external provider |
| `PostgreSQL` / Testcontainers (`FinnhubIntegrationIT`) | REQUIRED for app-managed infra ITs | Yes (existing) | PASS — the one full-context IT reuses the singleton PG container |
| New Maven dependency (main or test) | — | **None** | PASS — `pom.xml` diff = 2 JaCoCo excludes only |
| WireMock / Caffeine | (would need a policy note) | **No** | PASS — recommended positions (OD-EN005-3/4) taken |
| New deployable / broker / scheduler / search engine / persistence tech / Finnhub SDK | — | **None** | PASS (FR-036) |
| LLM | Deterministic boundary | **No** | PASS (FR-035; grep clean) |
| Secrets — external, never committed | REQUIRED | `${FINNHUB_API_KEY}` env → typed props → header | PASS |

## Tests

| Suite | Command | Result |
|---|---|---|
| Backend unit + ArchUnit | `./mvnw -B test` | Surefire **181 run, 0 failures, 0 errors, 3 skipped** (the skips = `FinnhubProviderSmokeTest`, `@EnabledIfEnvironmentVariable(FINNHUB_SMOKE=1)`) |
| Backend integration (Testcontainers PostgreSQL) | `./mvnw -B verify` (Failsafe) | **62 run, 0 failures, 0 errors** — incl. `FinnhubIntegrationIT` (full context, no key → all 3 ports report `MarketDataNotConfigured`; `@Primary` beans are the caching decorators) |
| — Finnhub provider boundary | Surefire (`MockRestServiceServer`) | `FinnhubRestClientTest` (11) + `FinnhubRestClientLoggingTest` (2) + `Finnhub{MarketData,InstrumentProfile,FxRate}AdapterTest` — endpoint/params/`X-Finnhub-Token` header; `200`→DTO; `401/403`/`429`/`4xx`/`5xx`/malformed/network/empty-body → the right neutral error; blank key → not-configured with 0 calls; no key in any message/log |
| — Deterministic mapping / resolver / cache | Surefire (RED-first) | `Finnhub{Quote,Profile,ForexRates}MapperTest`, `FinnhubSymbolResolverTest`, `TtlCacheTest`, `CachingPortsTest`, `MarketDataModelTest`, `SectorTest`, `SupportedCurrencyTest`, `InstrumentIdentifierTest` |
| Architecture conformance | `StandardArchitectureRulesTest` | **18/18**; deliberate `RestClient` import in `marketdata.domain` → rules fail → reverted → 18/18 |
| Coverage gate | JaCoCo `check` | **line 97.04 % · branch 91.81 %** — "All coverage checks have been met" |

No required test is failing. The 3 skips are by design (opt-in live-provider smoke test).

## Build

| Build | Command | Result |
|---|---|---|
| Backend | `./mvnw -B clean verify` | **BUILD SUCCESS** |
| Contract validation | N/A | EN005 adds no OpenAPI operation; the consumed Finnhub contract is stub-tested |
| Container image | `./start.sh` (BUILD=1) | backend + frontend images rebuilt; platform healthy |

## Runtime Verification

`./start.sh` (BUILD=1) → all containers healthy. Observed:

| Check | Result |
|---|---|
| `GET /actuator/health` | `{"status":"UP"}` (db UP) — app boots with the `marketdata` module + `finnhub:` config, **no `FINNHUB_API_KEY`** |
| Backend log | `event=FinnhubIntegrationDisabled reason=no-api-key` (structured ECS; **no key value** — there is none) |
| `GET /api/portfolios` · `GET /api/financial-instruments?query=aapl` | `200` — FD001/FD002/FD003 unaffected (AR-045) |
| `GET /api/market-data` (any EN005 endpoint) | `404` — EN005 exposes no HTTP surface (FR-024) |
| `./stop.sh` then `./stop.sh` again | both safe (idempotent); platform stopped; data volume kept |

Platform left **stopped** after verification.

## API and Contract Verification

N/A — EN005 introduces **no** external REST behavior (FR-024); `contracts/openapi/openapi.yaml` is
untouched (verified). The *consumed* Finnhub HTTP surface is pinned in
`specs/EN005-…/contracts/finnhub-provider-contract.md` + typed DTOs + 12 synthetic wire fixtures +
`MockRestServiceServer` request assertions, so the dependency cannot drift silently.

## Persistence Verification

N/A — **no schema change, no Flyway migration, no table, no write** (FR-021). Verified: no `V4*` in
`db/migration/`, no `@Entity` under `marketdata`. Results live only in the short-lived in-process
`TtlCache`.

## Security and Repository Hygiene

| Check | Status | Evidence |
|---|---|---|
| No committed secret / `.env` / key / token | PASS | `api-key: ${FINNHUB_API_KEY:}` (env placeholder); repo + built-artifact scan for a key value — none; `infrastructure/local/.env` has no `FINNHUB` entry |
| Key never logged / traced / returned | PASS | header transport; `FinnhubRestClient` logs no URL/body; `FinnhubRestClientLoggingTest` asserts no key even when the provider error body contains it; no port returns it |
| Inputs validated at trust boundary | PASS | `InstrumentIdentifier` normalizes/validates; `FinnhubSymbolResolver` rejects unmapped MICs before any call |
| Synthetic test data | PASS | 12 synthetic JSON fixtures; smoke test reads the key from the env and never prints it |
| No build output / `node_modules` / `target` committed | PASS | `git status` shows only source/test/CSV/fixtures/specs; `target/` git-ignored |
| Implementation in approved location | PASS | all under `implementation/platform/backend/core-service/` |
| Unrelated files | NONE | diff limited to EN005 |

## Documentation

| Item | Status | Evidence |
|---|---|---|
| Non-obvious reasoning documented | PASS | Javadoc on every port / mapper / client / resolver / cache / exception; `research.md` D1–D12 |
| Public contract documented | PASS | `contracts/market-data-ports.md` + `contracts/finnhub-provider-contract.md` |
| Feature docs reflect approved behavior | PASS | `backend/core-service/README.md` (+the `marketdata` module, +18-rule note, +test rows) and `implementation/platform/README.md` (+"Provider integrations" — backend-only, not user-facing) updated |
| ADRs added/updated | N/A | none required |
| Architecture diagrams | N/A | `marketdata` has no container/schema/endpoint footprint — nothing to add to `containers.md`; `architecture.md` already describes the Market Data Port pattern |
| `product/` docs updated for global changes | N/A | no global definition changed; **no `product/` edit by this enabler** |
| No generated doc contradicts product docs | PASS | specs trace to EN005; consistent |

## Definition of Done

| Item | Applicable | Status | Evidence |
|---|---|---|---|
| 1 Product & Specification (traceable, approved, in-scope, no invented rule, VC implemented) | Yes | PASS | EN005 §32 signed; `spec.md` Traceability; scope table above; VC-001…VC-019 all PASS |
| 2 Architecture (rules, tech policy, hexagonal, ownership, ADR) | Yes | PASS | ArchUnit 18/18; no new tech/dependency; ADR-001/003 respected |
| 3 Code Quality (readable, cohesive, no speculative abstraction, safe numerics, explicit errors) | Yes | PASS | `BigDecimal` only; 7-member neutral error set; no `business` layer; hand-rolled cache; dead branches trimmed |
| 4 Testing (TDD, unit, integration, contract, arch, edge cases, all pass) | Yes | PASS | RED-first mappers/resolver/cache; `MockRestServiceServer` adapter tests; consumed-contract fixtures; 18/18 arch; every failure mode tested |
| 5 Coverage (≥ 90 %, meaningful assertions, exclusions justified) | Yes | PASS | line 97.04 % / branch 91.81 %; excludes = no-logic config + record DTOs only (T004) |
| 6 APIs & Contracts | Partial | PASS | **no external REST API** (FR-024); no persistence/provider leakage on any port (ArchUnit + FR-012); the consumed Finnhub contract is documented + stub-tested |
| 7 Persistence | No | N/A | no schema, no migration, no write (FR-021) |
| 8 External Integrations (ports/adapters, provider models inside adapters, timeouts, retry, rate-limit, safe translation, deterministic stubs) | Yes | PASS | 3 ports; Finnhub types confined; explicit 2s/5s timeouts; no-retry v1 (documented, AR-044); `429` distinct; every failure → neutral error; `MockRestServiceServer` |
| 9 AI / LLM | No | N/A | no LLM (FR-035) |
| 10 Security & Privacy (no secrets, logs clean, inputs validated, minimal external data) | Yes | PASS | key via env → header; never logged/returned; `InstrumentIdentifier` validates; one `GET` per op |
| 11 Observability | Partial | PASS | structured `event=FinnhubCall` per call; no new observability infra (enabler §24) |
| 12 Resilience (timeouts, retry, idempotency, no partial-state corruption, graceful degradation) | Yes | PASS | explicit timeouts; no-retry; reads idempotent; EN005 writes nothing; blank key / outage doesn't stop the app (`FinnhubIntegrationIT`; AR-045) |
| 13 Documentation | Yes | PASS | Javadoc + 2 contract docs + 2 READMEs; no ADR needed |
| 14 Repository Hygiene | Yes | PASS | no artifacts committed; synthetic test data; no dependency change |
| 15 CI/CD | No CI | N/A | validated locally: `./mvnw -B clean verify` offline — build + tests + coverage gate + ArchUnit all green |
| 16 Review (vs spec, vs arch rules, AI code reviewed, limitations explicit) | Yes | PASS | this report + `pr-evidence.md` + `dod-checklist.md`; deviations recorded |
| 17 Product Acceptance (enabler — evidence per VC, matches intent, understandable, no undocumented assumptions) | Yes | PASS | `quickstart.md` execution record; VC-001…VC-019; Assumptions A1–A20; deviations in `pr-evidence.md` |

## Findings

### FAILURES

None.

### WARNINGS

**W001 — Working tree is uncommitted.** All EN005 changes (5 modified files + the new `marketdata`
source/test trees + `finnhub-symbol-map.csv` + 12 test fixtures + `specs/EN005-…/`) are unstaged /
untracked. This is the established state of this repository (FD001–FD003, EN002–EN004 are likewise
uncommitted per project memory), so it is **non-blocking** for the technical closure decision, but
the enabler is not yet recorded in version control. *Remediation:* branch + commit the EN005 change
set with a message tracing to EN005.

**W002 — Two implementation choices deviate from the plan's sketch (accepted, recorded).**
(a) `MarketDataModuleConfiguration` declares **no `Clock` bean** — the adapters inject the platform's
existing `Clock` (from `PortfolioModuleConfiguration`); a second unconditional bean clashes under
Spring's default no-bean-overriding and `@ConditionalOnMissingBean` ordering between two user
`@Configuration` classes is fragile (plan OD-EN005-9). (b) The exception hierarchy is **message-only**
(no `(String, Throwable)` constructor) because `FinnhubRestClient` deliberately does not chain the
provider exception (whose message can echo provider detail); it logs the status category instead.
(c) `FinnhubSymbolResolver` parses its 9-row CSV with a plain `BufferedReader` rather than the
`commons-csv` mentioned in `tasks.md` T013. All three are safe, reversible, behavior-neutral, and
documented in `pr-evidence.md` §"Deviations". *Remediation:* none required.

**W003 — Finnhub free-tier reach is an external limitation (informational).** Finnhub's free plan
covers US equities for `/quote` and `/stock/profile2`; `/forex/rates` and most non-US real-time data
need a paid plan. This does **not** affect EN005's deliverable — every automated test uses a
deterministic stub (VC-016), the symbol resolver returns `InstrumentNotResolved` cleanly for
unmapped markets, and `FinnhubProviderSmokeTest` *aborts* (not fails) when the operator's key lacks
a capability. Recorded so a future valuation feature plans provider coverage deliberately.

## Required Remediation

None for the technical closure decision. Recommended before/at merge:

1. Commit the EN005 change set to version control (W001).

## Final Decision

**READY TO CLOSE WITH WARNINGS.**

EN005 fully satisfies its approved scope and every verification criterion (VC-001 … VC-019), all
applicable architecture rules (ArchUnit 18/18) and Definition-of-Done items, and the quality gates
(build, tests, ≥ 90 % coverage, runtime). No FAIL finding, no unapproved material decision, no scope
expansion, no new technology or dependency, no `product/` edit, no secret committed. The warnings
are non-blocking (uncommitted tree — the repo norm; two recorded safe deviations; an external
provider-plan limitation that the design already handles).

Final closure remains a human decision. Recommended human action: approve closure and commit the
change set.
