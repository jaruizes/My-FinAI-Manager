# Feature Specification: Establish External Market Data Capabilities (EN005 — Revision 2)

**Feature Branch**: `EN005-establish-finnhub-market-data-integration`

**Created**: 2026-09-03 · **Revised**: 2026-09-04

**Status**: Draft (spec) — Enabler **Approved — Reopened for revision** (§29 signed by jaruiz 2026-09-04)

**Input**: Technical Enabler (revised): "Establish *provider-neutral* market-data capabilities —
latest market price, instrument profile/sector, and currency exchange rate — where the core never
knows which external provider supplies any capability. **Each capability has its own port and its
own adapter.** Finnhub is the initial provider for **price** and **profile**; **Frankfurter** is
the initial provider for **FX rates**. Instrument-profile retrieval is **database-first**: check
local persistence, and only call the provider on a miss, persisting the normalised result. Initial
market coverage is **US equities only** (Finnhub plan), which is an infrastructure limitation and
must not become a core restriction. EN005 introduces no Portfolio valuation calculation or UI."

**Authoritative Source**:
`product/definition/enablers/EN005-establish-finnhub-market-data-integration/EN005-establish-finnhub-market-data-integration.md`
(**Status: Approved — Reopened for revision** — §29 signed by jaruiz 2026-09-04).

**Supports**: FD004 (Portfolio Valuation & Allocation) and future valuation/allocation features.
Those features are **not** part of EN005.

**Governing Architecture**: ADR-001 (single `core-service` deployable — unchanged), ADR-002, ADR-003
(Standard Spring Backend Architecture — module-first `domain` / `business` / `infrastructure`,
Spring Data JPA where persistence is used, Maven, ArchUnit), EN002 (containerized E2E). **A new
Flyway migration is required** for the database-first instrument-profile enrichment (§10–§13 of the
enabler). **No new ADR is anticipated** — the revision stays within the approved topology (no new
deployable, no broker, no scheduler). If planning surfaces a material architectural decision it
MUST be raised for human approval (constitution IV).

---

## Revision note — what this supersedes

This spec **supersedes** the 2026-09-03 EN005 spec (which is preserved in git history / the
`closure-report.md`). The 2026-09-03 revision was implemented and verified but **not** formally
closed or committed; the product owner reopened the enabler on 2026-09-04 and signed a **material
revision**. Net changes vs. the implemented 2026-09-03 state:

| # | Area | 2026-09-03 (implemented) | This revision |
|---|---|---|---|
| R1 | FX provider | `FinnhubFxRateAdapter` → Finnhub `/forex/rates` (a **premium** Finnhub endpoint — `403` on the free plan) | **Frankfurter** (`GET https://api.frankfurter.dev/v1/latest?base=&symbols=`) — ECB rates, **no API key** |
| R2 | Adapter separation | 3 ports; Finnhub adapters for all 3, sharing one `FinnhubRestClient` | Mandatory **one adapter per capability**; provider packages under `infrastructure/provider/{finnhub,frankfurter}/…`; **no** shared all-purpose Finnhub adapter |
| R3 | Business layer | none in the `marketdata` module — consumers call the ports directly | **`business` use cases** `GetMarketPrice`, `GetInstrumentProfile`, `GetFxRate` (provider-independent) |
| R4 | Instrument profile | provider call every time, short-lived in-process cache only | **Database-first**: `InstrumentProfileRepositoryPort` → local hit returns without a provider call; local miss calls the provider and **persists** the normalised profile (new Flyway table) |
| R5 | Provider selection | implicit "Finnhub for everything" | **Independent per-capability configuration** (`market-data.price.provider`, `.profile.provider`, `.fx.provider`), resolved by infrastructure wiring — **no `if/else` on provider identity in core/business** |
| R6 | Market coverage | implicit (Finnhub `403`/`404` for non-US surfaced as "unavailable") | **Explicit**: initial coverage = **US equities only** for price + profile; a provider-neutral "unavailable/unsupported" result for anything else; not encoded as a core rule |
| R7 | Naming | `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort`; `*Exception` neutral errors | **Kept** (Q2 → keep current names; they already satisfy §3's intent). A new profile-*provider* port for the DB-first flow is added in `financialinstrument`. |
| R8 | Consumers | FD004 `EnMarketDataGatewayAdapter` imports the 3 ports + neutral models directly | FD004's ACL adapter is updated: FX now via a `marketdata` business op / port backed by Frankfurter; sector now via `financialinstrument.business.GetInstrumentProfile`. **`portfolio.domain` / `portfolio.business` stay untouched** (the FD004 `MarketDataGateway` ACL absorbs the change) |

> **Governance.** The enabler's §26 "Open Technical Decisions" are **technical** decisions for
> `/speckit-plan` / `research.md`. This spec records the governed default for each in **Assumptions**;
> planning may refine them within the enabler's constraints. Planning MUST sequence this revision so
> the platform stays green at each checkpoint (the FX-provider swap, the DB-first profile layer, and
> the repackage are separable).

---

## Clarifications

### Session 2026-09-04

- Q1 — Which module owns the database-first instrument-profile enrichment (repository port, JPA
  adapter, Flyway table, `GetInstrumentProfile` use case)? → A: **The `financialinstrument`
  module.** Profile/sector is instrument reference data, same domain as EN004's catalog. The
  `marketdata` module stays purely "talk to external providers". FD004 obtains sector via
  `financialinstrument.business.GetInstrumentProfile`. The Finnhub *provider* adapter
  (`FinnhubInstrumentProfileAdapter`) implements a profile-*provider* port and lives with the other
  Finnhub code, but is consumed by the `financialinstrument` business use case on a local miss.
  *(→ FR-025, FR-042, Key Entities)*
- Q2 — Rename the ports/exceptions to the enabler's literal names, or keep the current ones? → A:
  **Keep the current names** — `MarketDataPort` / `InstrumentProfilePort` / `FxRatePort` and the
  existing `*Exception` neutral-error types. They already satisfy §3's intent (provider-neutral,
  separate, one per capability). This keeps the blast radius small: FD004's `EnMarketDataGatewayAdapter`
  changes only for the FX-provider swap + business-use-case routing, not renames. *(→ FR-004,
  FR-032)*
- Q3 — Keep or remove the in-process TTL cache? → A: **Keep it for price + FX; drop it for
  profile.** Price and FX are time-sensitive and rate-limit-sensitive (Finnhub free tier; Frankfurter
  updates once/day) — the existing `TtlCache` + `CachingMarketDataPort` / `CachingFxRatePort`
  decorators stay. `CachingInstrumentProfilePort` is **removed** — the database-first `GetInstrumentProfile`
  use case replaces it. *(→ FR-031)*

---

## Enabler Nature *(mandatory)*

EN005 is a **Technical Enabler**, not a product Feature Definition. It introduces **no**
investor-facing behavior, no new Portfolio/Position capability, no valuation or allocation
arithmetic, and no UI. Its purpose is a **provider-neutral backend capability** to obtain three
kinds of external data, each isolated behind its own port and its own adapter, so the business and
domain layers depend only on provider-neutral abstractions.

Because this is an enabler:

- The scenarios below describe **backend-developer / maintainer / operator** workflows.
- "Acceptance" is expressed through the enabler's **Verification Criteria VC-001 … VC-015** — see
  the Traceability table.
- No business domain entities or rules are added, removed, or reinterpreted. `MarketPrice`,
  `InstrumentProfile`, and `FxRate` are **provider-neutral read models**; the persisted profile
  (R4) is enrichment reference data, not a business aggregate.
- EN005 has **no application E2E of its own**. Provider integrations are verified deterministically
  at adapter/integration level with an HTTP stub (WireMock or equivalent); an opt-in real-provider
  smoke test MAY validate a real Finnhub key but never runs in CI. FD004's E2E (which consumes
  EN005) is updated separately — see Dependencies.
- EN004 remains the **canonical** source of Financial Instrument identity (`ticker + market(MIC)`).
  Provider data **enriches** it; a provider's `exchange` string is metadata only and MUST NOT
  replace the ISO 10383 MIC.

---

## User Scenarios & Testing *(mandatory)*

Facets of one capability, ordered by importance. "Test" = the deterministic automated check that
proves the facet.

### User Story 1 — Three independent provider ports, one adapter per capability (Priority: P1)

As a backend developer, I obtain price, profile, and FX through **three separate provider-neutral
ports**, each backed by **its own adapter** — never one monolithic provider class — so any one
capability can change provider without touching the others.

**Why P1**: The core architectural contract of the revision (enabler §3, §16; VC-002, VC-003).

**Test**: ArchUnit + package inspection — the three ports exist in `…marketdata.domain.ports…`;
`FinnhubMarketPriceAdapter`, `FinnhubInstrumentProfileAdapter`, `FrankfurterFxRateAdapter` are
distinct classes in distinct provider packages; no class implements more than one provider port;
provider DTOs/clients are confined to their provider package.

**Acceptance**:
1. **Given** the module, **When** its ports are enumerated, **Then** there are exactly three
   provider ports — one for price, one for profile, one for FX — each provider-neutral (no Finnhub
   or Frankfurter type in the signature).
2. **Given** the adapters, **When** their responsibilities are inspected, **Then** each adapter
   implements exactly one provider port and owns only that provider's symbol resolution,
   authentication, HTTP call, DTO mapping, and error translation.
3. **Given** a hypothetical new `AlphaVantageMarketPriceAdapter`, **When** it is added, **Then** the
   profile and FX adapters, the business use cases, and any consumer (FD004) need **no** change
   (VC-004, VC-013).

### User Story 2 — Latest market price via Finnhub (US equities), provider-neutral (Priority: P1)

As a backend developer, I call one business operation to get the latest price of a US-listed
instrument in its native currency, as a provider-neutral `MarketPrice` — and a non-US instrument,
a missing price, a rate-limit, or an auth failure each comes back as an explicit provider-neutral
outcome, never a fabricated number.

**Why P1**: The price capability FD004 depends on (enabler §6.1, §14, §20A; VC-001, VC-012).

**Test**: `FinnhubMarketPriceAdapter` unit test with an HTTP stub — correct `/quote` request +
`X-Finnhub-Token` header; `{ "c": 0 }` / empty body → `MarketPriceUnavailable`; `429` →
`ExternalProviderRateLimited`; `401/403` → `ExternalProviderAuthenticationFailed`; unresolved
symbol → `InstrumentNotResolved`; a non-US instrument → a provider-neutral unsupported/unavailable
result (no core rule about "US only").

**Acceptance**:
1. **Given** a US instrument with a valid provider symbol, **When** price is requested, **Then** the
   result is a `MarketPrice` with a positive decimal price, the instrument's currency, `observedAt`
   / `retrievedAt`, and `source = FINNHUB`.
2. **Given** a non-US instrument (e.g. Bolsa de Madrid), **When** price is requested, **Then** the
   business operation returns an explicit **unavailable** outcome — no price, no exception leaking a
   Finnhub status, no fabricated value — and the core contract does not mention Finnhub coverage.
3. **Given** any Finnhub failure mode, **When** it occurs, **Then** it is translated inside the
   adapter to the matching provider-neutral error (§19); no Finnhub DTO/status crosses the port.

### User Story 3 — Database-first instrument profile / sector (Priority: P1)

As a backend developer, when I request an instrument's profile/sector, the platform returns a
**locally stored** profile if it has one, and only calls the external provider on a miss — then
**persists** the normalised result so the next request is a local hit.

**Why P1**: The revision's largest new behavior (enabler §10–§13; VC-005…VC-009). Reduces provider
calls and rate-limit pressure; keeps sector data stable.

**Test**: `GetInstrumentProfile` business test with a mocked repository port + provider port —
local hit ⇒ provider **not** called; local miss ⇒ provider called once, result normalised and
**saved**; provider failure on a miss ⇒ explicit unavailable, **nothing persisted**, **no sector
fabricated**. Persistence round-trip IT (Testcontainers PostgreSQL): a saved profile reloads with
provider-neutral fields and `lastUpdatedAt` / `source`.

**Acceptance**:
1. **Given** a profile already in local persistence, **When** it is requested, **Then** it is
   returned **without** any external provider call (BR-EN005-002; VC-006).
2. **Given** no local profile, **When** it is requested and the provider returns one, **Then** the
   normalised profile is persisted (provider-neutral fields only — Finnhub payload never becomes
   the canonical model) and returned (BR-EN005-003/004/005; VC-007, VC-008, VC-009).
3. **Given** no local profile and the provider cannot supply one, **When** it is requested, **Then**
   an explicit "profile unavailable" result is returned, **nothing is persisted**, and **no sector
   is fabricated** (BR-EN005-006).
4. **Given** a persisted profile, **When** it is inspected, **Then** it carries `lastUpdatedAt` and
   `source`; **no** automatic TTL refresh is performed by this revision (§13).

### User Story 4 — FX rate via Frankfurter (EUR ↔ USD), no API key (Priority: P1)

As a backend developer, I call one business operation for a currency-conversion rate and get a
provider-neutral `FxRate` sourced from **Frankfurter** (ECB data) — with **no** API key, no shared
application secret, and every Frankfurter payload translated inside the adapter.

**Why P1**: This is the capability the previous revision could not deliver on a free plan
(enabler §6.3, §8A, §15; VC-003, VC-004, VC-011).

**Test**: `FrankfurterFxRateAdapter` unit test with an HTTP stub — `GET /v1/latest?base=USD&symbols=EUR`
with **no** auth header; response `{ "amount":1.0, "base":"USD", "date":"…", "rates": { "EUR": 0.85 } }`
→ `FxRate(from=USD, to=EUR, rate=0.85, observedAt=date, retrievedAt=now, source=FRANKFURTER)`;
mirror for `EUR→USD`; a currency pair absent from `rates` → `FxRateUnavailable`; malformed body →
`FxRateUnavailable`; `5xx` / timeout → `ExternalProviderUnavailable`; an unsupported currency →
`FxRateUnavailable` (or `IllegalArgumentException` for `from == to`).

**Acceptance**:
1. **Given** `from = USD, to = EUR`, **When** the rate is requested, **Then** the adapter calls
   `GET {base}/v1/latest?base=USD&symbols=EUR` with no key and maps `rates.EUR` → `FxRate.rate`,
   `date` → `observedAt`.
2. **Given** `from = EUR, to = USD`, **When** the rate is requested, **Then** it maps
   `rates.USD` → `FxRate.rate` analogously.
3. **Given** any Frankfurter failure or a missing pair, **When** it occurs, **Then** it becomes a
   provider-neutral `FxRateUnavailable` / `ExternalProviderUnavailable`; **no** Frankfurter payload
   crosses the port; **no** default rate (`1` / `0`) is fabricated.
4. **Given** the running system, **When** the Finnhub key is absent, **Then** FX still works
   (Frankfurter needs no key) — the FX capability is independent of the Finnhub configuration
   (VC-004).

### User Story 5 — Independent per-capability provider configuration (Priority: P1)

As an operator, I select the provider for each capability independently through configuration, and
changing one never forces a change to the others, to the Portfolio domain, to valuation business
logic, or to public contracts.

**Why P1**: Enabler §7, §16, §25.9; VC-004.

**Test**: A configuration test — with `market-data.price.provider = finnhub`,
`market-data.profile.provider = finnhub`, `market-data.fx.provider = frankfurter` the wired
`MarketPriceProviderPort` bean is the Finnhub adapter and the `FxRateProviderPort` bean is the
Frankfurter adapter; there is **no** provider-identity `if`/`switch` in `domain` or `business`
(ArchUnit / review); an alternate value (e.g. a second registered price adapter) selects that
adapter with no other change.

**Acceptance**:
1. **Given** the three provider settings, **When** the context starts, **Then** each capability's
   port resolves to the configured adapter.
2. **Given** `market-data.fx.provider` is changed, **When** the context restarts, **Then** no price
   / profile code, no business use case, no consumer, and no contract changes.
3. **Given** `domain` and `business`, **When** inspected, **Then** they contain **no** branch on
   provider name and **no** provider technology type.

### User Story 6 — Provider-neutral errors, secret safety, freshness & telemetry (Priority: P1)

As an operator, every failure the business layer sees is provider-neutral; the Finnhub key never
appears in a URL / log / metric / trace / API response / frontend bundle; Frankfurter introduces
no fake secret; every result carries freshness metadata; each adapter emits
`provider` + `capability` + `operation` + `latency` + `outcome` + HTTP-status-category telemetry.

**Why P1**: Enabler §8, §8A, §19, §21; VC-011, VC-014.

**Test**: Logging test — the `X-Finnhub-Token` value never appears in any log line; the Finnhub
key is not in any URL; Frankfurter calls carry no auth; each adapter logs a structured event with
`provider=` and `capability=`. Error-translation test — every provider failure mode maps to the
provider-neutral error set (§19).

**Acceptance**:
1. **Given** any adapter call, **When** it logs, **Then** the line has `provider`, `capability`,
   `operation`, `latency`, `outcome`, `httpStatusCategory` and **no** secret.
2. **Given** a Finnhub auth failure, **When** it is logged / surfaced, **Then** the key value is
   absent everywhere.
3. **Given** any result, **When** it is returned, **Then** it carries `observedAt` and/or
   `retrievedAt` and `source` so a consumer can judge freshness.

### User Story 7 — Deterministic offline tests; the platform stays green through the revision (Priority: P2)

As a maintainer, I can run `./mvnw verify` (+ `ng test`, `./e2e.sh`) with **no** outbound Internet:
provider boundaries are stubbed, the DB-first profile layer uses Testcontainers PostgreSQL, and
every existing FD001–FD004 / EN004 suite and E2E stays green.

**Why P2**: Enabler §22, §23; VC-014, VC-015.

**Test**: `./mvnw -B clean verify` green offline (unit + Testcontainers IT + contract + ArchUnit +
≥ 90 % coverage); `ng test` green; `./e2e.sh` green with **both** provider boundaries controlled
(Finnhub stub + Frankfurter stub).

**Acceptance**:
1. **Given** CI, **When** the suite runs, **Then** no test reaches `finnhub.io` or
   `api.frankfurter.dev`.
2. **Given** the revision is applied, **When** the full gates run, **Then** FD001 / FD002 / FD003 /
   FD004 / EN004 automated suites and E2Es are green (FD004's ACL adapter + its E2E stub are
   updated as part of this work).

### Edge Cases

- **Non-US instrument for price/profile** — Finnhub returns `403`/`404`/empty ⇒ provider-neutral
  "unsupported/unavailable"; the business API contract says nothing about US-only (§20A, §20).
- **Frankfurter pair absent** (`rates` has no target key) ⇒ `FxRateUnavailable` — not a `null` rate.
- **`from == to`** for FX ⇒ rejected (`IllegalArgumentException`) — EN005 supplies rates, it does
  not special-case identity conversion.
- **Local profile stale** — accepted this revision (no TTL); freshness metadata is retained so a
  later refresh feature can act on it (§13).
- **Provider-selection value names an unregistered provider** ⇒ startup fails fast with a clear
  message (misconfiguration, not a silent fallback — §16 "no automatic fallback").
- **DB-first profile: concurrent miss** for the same instrument ⇒ both may call the provider; the
  persist step is idempotent (upsert on canonical identity) — no duplicate rows, no error.
- **Finnhub key absent** ⇒ price + profile-on-miss are unavailable (`MarketDataNotConfigured` /
  `ExternalProviderAuthenticationFailed` per planning); FX (Frankfurter) is unaffected; the app
  still starts and logs the disabled state once.
- **Finnhub `/forex/rates` removal** — the previous `FinnhubFxRateAdapter`, its `/forex/rates`
  client path, DTO and mapper are removed; no consumer may reference them.

---

## Requirements *(mandatory)*

> Each FR traces to an enabler section / BR / VC. All clarifications (Q1–Q3) are resolved — see
> the Clarifications section.

### Ports, adapters & module structure

- **FR-001**: EN005 MUST expose **three independent provider-neutral ports**, one per capability —
  latest market price, instrument profile/sector, currency exchange rate. No port signature may
  reference a Finnhub or Frankfurter type. *(§3, §6; VC-002)*
- **FR-002**: Each capability MUST be implemented by **its own adapter**. There MUST NOT be a single
  class implementing more than one provider port, nor a shared "all-purpose" provider adapter.
  *(§3, §6, §16; VC-003)*
- **FR-003**: Provider-specific code (HTTP client, DTOs, symbol resolution, error translation) MUST
  be confined to that provider's infrastructure package: `marketdata` keeps a
  `…infrastructure…finnhub…` (price) and gains a `…infrastructure…frankfurter…` (FX);
  `financialinstrument` gains a `…infrastructure…provider.finnhub…` (profile). Exact package names
  refined in planning within ADR-003. Provider DTOs MUST NOT appear in `domain` or `business` of any
  module. *(§17, §18, §23; VC-009, VC-015)*
- **FR-004**: The existing provider-neutral port names are **kept** — `MarketDataPort` (price),
  `InstrumentProfilePort` (external provider profile), `FxRatePort` (FX) in
  `…marketdata.domain.ports…`, and the existing `*Exception` neutral-error types. They already
  satisfy §3 (provider-neutral, one per capability). The database-first flow adds a separate
  profile-*provider* port + a repository port in the `financialinstrument` module (FR-020, FR-025).
  *(Q2)*

### Core-facing business operations

- **FR-005**: The `business` layer MUST expose provider-independent operations equivalent to
  `GetMarketPrice(instrument)`, `GetInstrumentProfile(instrument)`, `GetFxRate(from, to)`. Core /
  business code MUST NOT call a provider client or provider-specific API directly. *(§4, §18;
  VC-001)*
- **FR-006**: Core / business code MUST NOT branch on provider identity (no `if provider == "finnhub"`).
  Provider selection is resolved by infrastructure wiring only. *(§16, §25.8; VC-004)*
- **FR-007**: The consumer pattern for FD004 MUST route through these business operations (directly
  or via FD004's existing `MarketDataGateway` ACL) — **not** through the provider ports or provider
  clients. `portfolio.domain` / `portfolio.business` MUST remain free of any `marketdata` type
  (unchanged from FD004). *(AR-062; FD004 FR-026, FR-035)*

### Provider-neutral read models

- **FR-008**: `MarketPrice` MUST carry `instrumentIdentifier`, `price` (positive decimal),
  `currency`, `observedAt`, `retrievedAt`, `source`. A missing/zero provider price MUST be surfaced
  as unavailable — never a `MarketPrice` of `0`. *(§5, §6.1)*
- **FR-009**: `InstrumentProfile` MUST carry `instrumentIdentifier`, `name`, `sector` (explicit
  "unclassified" when the provider gives none — never inferred), optional `industry` / `currency`,
  `lastUpdatedAt`, optional `source`. *(§5, §6.2; BR-EN005-006)*
- **FR-010**: `FxRate` MUST carry `fromCurrency`, `toCurrency`, `rate` (positive decimal),
  `observedAt`, `retrievedAt`, `source`. A missing provider rate MUST be surfaced as unavailable —
  never a default of `1` or `0`. *(§5, §6.3)*
- **FR-011**: All monetary / rate values MUST be decimal-safe (no binary floating point). *(project
  DR-011; carried from the 2026-09-03 spec)*

### Finnhub — price & profile adapters

- **FR-012**: `FinnhubMarketPriceAdapter` MUST implement the price port against Finnhub `/quote`,
  send the key as the `X-Finnhub-Token` **header** (never a URL parameter), resolve the provider
  symbol from EN004 canonical identity inside the adapter, and translate every Finnhub failure to
  the provider-neutral error set. *(§6.1, §8, §19; VC-011)*
- **FR-013**: `FinnhubInstrumentProfileAdapter` MUST implement the profile *provider* port against
  Finnhub `/stock/profile2`, normalise sector/industry, own its own HTTP invocation, and MUST NOT
  decide whether local persistence is consulted first (that is the `GetInstrumentProfile` use
  case's job — FR-020). Per Q1 + enabler §17, this adapter and the profile *provider* port move to
  the **`financialinstrument`** module (`…financialinstrument.infrastructure…provider.finnhub…` /
  `…financialinstrument.domain.ports…`); the `marketdata` module's current
  `InstrumentProfilePort` + `FinnhubInstrumentProfileAdapter` + `CachingInstrumentProfilePort` are
  **removed**. *(§6.2, §10, §17)*
- **FR-014**: Provider symbol resolution MUST NOT redefine or persist over EN004 canonical identity;
  a provider `exchange` string MUST NOT replace the ISO 10383 MIC. *(§9; VC-010)*
- **FR-015**: Initial price/profile coverage is **US equities only**. A non-US instrument MUST yield
  a provider-neutral unsupported/unavailable result. This limitation MUST NOT appear as a rule in
  `domain` / `business` or in any public contract. *(§20A, §20, §25.12–13; VC-012)*

### Frankfurter — FX adapter

- **FR-016**: `FrankfurterFxRateAdapter` MUST implement the FX port against
  `GET {base-url}/v1/latest?base={from}&symbols={to}` with **no** authentication, mapping
  `base → fromCurrency`, `rates[to] → rate`, `date → observedAt`, retrieval time → `retrievedAt`,
  `source → FRANKFURTER`. *(§6.3, §8A)*
- **FR-017**: Frankfurter MUST NOT be a `domain` / `business` dependency, MUST NOT introduce a
  shared/fake application secret, and its base URL MUST be externally overridable (for the
  containerized E2E stub) with the public default `https://api.frankfurter.dev`. *(§8A; VC-011)*
- **FR-018**: The initial FX scope is **EUR ↔ USD**. Other pairs MAY be requested but MAY return
  unavailable. *(§7, §25.20)*
- **FR-019**: The previous Finnhub FX integration (`/forex/rates` client path, its DTO, its mapper,
  `FinnhubFxRateAdapter`) MUST be removed; no code may reference it after the revision. *(R1, §27)*

### Database-first instrument-profile enrichment

- **FR-020**: `GetInstrumentProfile` MUST query a provider-neutral `InstrumentProfileRepositoryPort`
  **first**. A local hit MUST be returned **without** any external provider call. *(§10–§12;
  BR-EN005-001/002; VC-005, VC-006)*
- **FR-021**: On a local miss, `GetInstrumentProfile` MUST call the configured
  `InstrumentProfileProviderPort`; a successfully normalised result MUST be **persisted** (upsert on
  canonical identity) and returned. *(BR-EN005-003/004; VC-007, VC-008)*
- **FR-022**: Persisted profile data MUST use **provider-neutral fields only** — a Finnhub payload
  MUST NOT become the canonical persistence model. *(BR-EN005-005; VC-009)*
- **FR-023**: If neither local persistence nor the provider can supply a profile, `GetInstrumentProfile`
  MUST return an explicit unavailable result, persist **nothing**, and fabricate **no** sector.
  *(BR-EN005-006)*
- **FR-024**: The profile persistence schema MUST be created by a **new Flyway forward migration**,
  owned by exactly one module, using provider-neutral columns + `last_updated_at` + `source`.
  Hibernate MUST NOT own the schema (`ddl-auto: none`). *(§10–§13; constitution VII)*
- **FR-025**: The **`financialinstrument` module** owns the database-first instrument-profile
  enrichment (Q1): `GetInstrumentProfile` (business), the provider-neutral
  `InstrumentProfileRepositoryPort`, the profile-*provider* port, the `JpaInstrumentProfileRepositoryAdapter`,
  the `FinnhubInstrumentProfileAdapter`, and the new Flyway migration. The `marketdata` module is
  left with **price (Finnhub) + FX (Frankfurter) only**. `financialinstrument` MUST NOT depend on
  `marketdata` for profile (its own ports own the capability). *(Q1; §17)*
- **FR-026**: Business code MUST NOT use a Spring Data repository directly — only the
  `InstrumentProfileRepositoryPort`. *(§11, §18; VC-015)*
- **FR-027**: No automatic TTL / background refresh of persisted profiles is introduced by this
  revision. *(§13, §26.7)*

### Provider selection / configuration

- **FR-028**: Provider selection MUST be independently configurable per capability
  (`market-data.price.provider`, `market-data.profile.provider`, `market-data.fx.provider` or an
  equivalent refined in planning). *(§7, §16; VC-004)*
- **FR-029**: Changing one capability's provider MUST NOT require changes to the other adapters, the
  business use cases, the Portfolio domain, valuation business logic, or any API contract. *(§7,
  §25.9; VC-004)*
- **FR-030**: A configuration value naming an unregistered provider MUST fail fast at startup with a
  clear message. No silent fallback. *(§16)*

### Caching boundary

- **FR-031**: The existing in-process short-lived `TtlCache` + `CachingMarketDataPort` /
  `CachingFxRatePort` decorators are **kept** for price and FX (Q3 — rate-limit hygiene; Frankfurter
  updates daily). `CachingInstrumentProfilePort` is **removed** (the DB-first `GetInstrumentProfile`
  replaces it). Caching MUST NOT hide staleness (freshness metadata always carried) and MUST NOT
  introduce caching *infrastructure* (Redis etc.). *(Q3; §14, §26.8–26.9)*

### Errors, resilience & telemetry

- **FR-032**: The business layer MUST see only provider-neutral errors. The existing neutral-error
  types are **kept** (Q2): `MarketDataUnavailableException` / `InstrumentProfileUnavailableException`
  / `FxRateUnavailableException` / `MarketDataNotConfiguredException` / `ProviderRateLimitedException`
  / `ProviderAuthenticationFailedException` / `InstrumentNotResolvedException` (they map 1:1 to the
  enabler §19 concepts). Every provider HTTP status / DTO / error code MUST be translated **inside**
  the adapter; the Frankfurter adapter translates Frankfurter failures into the same set. *(§19)*
- **FR-033**: Each adapter MUST emit provider-aware infrastructure telemetry with at least
  `provider`, `capability`, `operation`, `latency`, `outcome`, `httpStatusCategory`. *(§21)*
- **FR-034**: The Finnhub API key MUST NOT appear in any URL, log, metric, trace, exception
  message, API response, or frontend bundle. Frankfurter MUST NOT add a shared secret. *(§8, §8A,
  §21; VC-011)*

### Testing & determinism

- **FR-035**: Automated tests MUST NOT depend on a live provider. Each adapter is tested
  independently with an HTTP stub (WireMock or Spring `MockRestServiceServer`). *(§22; VC-014)*
- **FR-036**: The DB-first profile layer MUST be covered by Testcontainers PostgreSQL integration
  tests (persistence round-trip, local-hit-no-call, miss-calls-and-persists,
  failure-persists-nothing). *(§22; constitution VII)*
- **FR-037**: ArchUnit MUST verify: `domain`/`business` free of `infrastructure`; Finnhub +
  Frankfurter packages confined to `infrastructure`; provider DTOs absent from `domain`/`business`;
  JPA entities/repositories in `infrastructure`; no provider-identity branch in core. *(§23; VC-015)*

### Non-regression & consumers

- **FR-038**: FD004's `EnMarketDataGatewayAdapter` and its tests MUST be updated to the revised
  ports / business operations. `portfolio.domain` / `portfolio.business` MUST remain unchanged
  (the FD004 `MarketDataGateway` ACL absorbs the change). FD004's calculator, persistence, API and
  UI MUST be unchanged. *(FD004 non-regression)*
- **FR-039**: FD004's E2E boundary MUST be updated: the `finnhub-stub` no longer serves `/forex/rates`;
  a **Frankfurter stub** (`/v1/latest`) is added (a new service, or the existing stub extended); the
  E2E backend gets `FRANKFURTER_BASE_URL` (and keeps `FINNHUB_BASE_URL`). FD004 E2E-001 / E2E-002
  MUST still pass. Governed default (A10): keep the current E2E-001 portfolio (AAPL + SAN) with the
  Finnhub stub continuing to fake the non-US quote, so FD004's approved E2E-001 numbers are
  unchanged; an all-US E2E-001 is an acceptable planning alternative.
- **FR-040**: FD001 / FD002 / FD003 / EN004 automated suites and E2Es MUST stay green. *(consumed
  capabilities)*

### Scope guardrails

- **FR-041**: EN005 MUST NOT introduce Portfolio valuation calculations, allocation, any
  investor-facing endpoint, any frontend, a new deployable, a message broker, a scheduler, or
  caching infrastructure (Redis etc.). *(§1, §24; constitution IV, AR-046)*
- **FR-042**: EN005 MUST NOT modify EN004's `financial_instrument` / `market` catalog schema or the
  canonical identity model. Profile enrichment is stored in a **dedicated enrichment table** (owned
  by `financialinstrument`, keyed on canonical identity), created by a new Flyway migration — not by
  adding columns to EN004's catalog tables. *(A5; §9, §10)*
- **FR-043**: EN005 MUST NOT silently edit a human-governed `product/` document. The enabler §29
  approval is the product owner's action.

---

## Key Entities *(include if feature involves data)*

- **MarketPrice** *(provider-neutral read model, not persisted)* — `instrumentIdentifier`, `price`,
  `currency`, `observedAt`, `retrievedAt`, `source`.
- **InstrumentProfile** *(provider-neutral read model; **persisted** as enrichment reference data —
  R4; owned by the **`financialinstrument`** module — Q1)* — `instrumentIdentifier` (canonical
  `ticker + MIC + currency`), `name`, `sector` (or explicit unclassified), `industry?`, `currency?`,
  `lastUpdatedAt`, `source?`. Exactly one persisted row per canonical identity (upsert), in a
  dedicated enrichment table (new Flyway migration; EN004 catalog tables untouched).
- **FxRate** *(provider-neutral read model, not persisted)* — `fromCurrency`, `toCurrency`, `rate`,
  `observedAt` (Frankfurter `date`), `retrievedAt`, `source = FRANKFURTER`.
- **InstrumentProfileRepositoryPort** *(provider-neutral persistence port)* — `findByInstrument` /
  `save` (upsert). Implemented by a JPA adapter over a new Flyway table.
- **Provider selection** *(configuration)* — `price → finnhub`, `profile → finnhub`,
  `fx → frankfurter` initially; each independently overridable.

*(No change to the `Portfolio` / `Position` / EN004 `FinancialInstrument` identity model — EN005
reads/enriches, it does not redefine.)*

---

## Traceability to Enabler Verification Criteria (revised §24)

| VC | Covered by |
|---|---|
| VC-001 Provider-neutral core | US1, US2, US5; FR-001, FR-005, FR-006 |
| VC-002 Separate provider ports | US1; FR-001 |
| VC-003 Separate provider adapters | US1, US4; FR-002 |
| VC-004 Independent provider selection | US5; FR-028, FR-029 |
| VC-005 Database-first profile | US3; FR-020 |
| VC-006 No external call on local hit | US3 (AS1); FR-020 |
| VC-007 External fetch on miss | US3 (AS2); FR-021 |
| VC-008 Profile persistence | US3 (AS2); FR-021, FR-024 |
| VC-009 Provider-neutral persistence | US3; FR-022 |
| VC-010 Canonical identity | US2; FR-014 |
| VC-011 Secret protection (Finnhub key; no Frankfurter fake secret) | US6; FR-034, FR-017 |
| VC-012 Provider limitations hidden | US2 (AS2); FR-015 |
| VC-013 Multiple adapters possible | US1 (AS3); FR-002 |
| VC-014 Deterministic tests | US7; FR-035 |
| VC-015 ADR-003 compliance | US1, US7; FR-003, FR-037 |

---

## Success Criteria *(mandatory)*

- **SC-001**: FX rates resolve on the **free** provider tier — `GET /v1/latest?base=USD&symbols=EUR`
  and `base=EUR&symbols=USD` both return a positive decimal `FxRate` with **no** API key, verified
  by an adapter test and a runtime check.
- **SC-002**: A US instrument (e.g. `AAPL`) yields a real `MarketPrice` and a real
  `InstrumentProfile` sector through the business operations; a non-US instrument (e.g. `SAN` on
  `XMAD`) yields an explicit provider-neutral **unavailable** — verified by tests.
- **SC-003**: For a profile already in local persistence, `GetInstrumentProfile` performs **zero**
  external provider calls (verified by a business test asserting the provider port is never
  invoked); for a miss it performs **exactly one** and persists **one** row.
- **SC-004**: Changing `market-data.fx.provider` (or `.price` / `.profile`) in configuration changes
  the wired adapter with **zero** changes to the other adapters, the business use cases, the
  Portfolio domain, valuation logic, or any contract — verified by a configuration test + `git diff`
  review.
- **SC-005**: `./mvnw -B clean verify` passes **offline** — unit + Testcontainers IT + contract +
  ArchUnit + ≥ 90 % line & branch coverage; **zero** provider DTO types in `domain` / `business`;
  **zero** provider-identity branches in core (ArchUnit).
- **SC-006**: `ng test` passes; `./e2e.sh` passes **offline** with both the Finnhub stub and the
  Frankfurter stub; FD001 / FD002 / FD003 / FD004 / EN004 suites and E2Es stay green.
- **SC-007**: **Zero** occurrences of the Finnhub key in any log / metric / trace / URL / exception
  / API response / frontend bundle; Frankfurter adds **no** application secret — verified by a
  logging test and a repo/secret scan.
- **SC-008**: `git diff` scope review shows: **one** new Flyway migration (profile enrichment only,
  no EN004 table altered); the Finnhub `/forex/rates` code path fully removed; **no** new deployable
  / broker / scheduler / caching infrastructure; **no** Portfolio business behavior; **no**
  unapproved `product/` edit.
- **SC-009**: **100 %** of the revised enabler's VC-001…VC-015 have associated executable evidence.
- **SC-010**: With a **real** Finnhub key set via `./start.sh`, creating an all-US-equity Portfolio
  produces a `COMPLETED` FD004 valuation (real prices, real Frankfurter USD↔EUR, weights, sector
  allocation) — the end-to-end outcome the revision exists to enable (manually verified; not a CI
  gate).

---

## Assumptions

> The enabler is **approved** (§29 signed 2026-09-04). Assumptions fill only non-material,
> planning-level gaps within its constraints.

- **A1 — Consumed capabilities in place**: FD001/FD002/FD003/FD004 and EN004 are implemented and
  verified (this session). The 2026-09-03 EN005 implementation exists in the tree (uncommitted) and
  is the starting point for the revision.
- **A2 — Single deployable** (ADR-001): the revision is additive within `core-service`. One new
  Flyway migration (profile enrichment). No new ADR anticipated.
- **A3 — HTTP client**: reuse the platform's existing Spring `RestClient` approach for both Finnhub
  and Frankfurter adapters, with explicit connect/read timeouts (enabler §26.1).
- **A4 — Adapter selection mechanism** (§26.2–26.3): a configuration-driven bean selection (e.g. a
  provider registry / `@ConditionalOnProperty` / a small factory) — the exact mechanism is a
  planning decision; it must not put provider `if/else` in core.
- **A5 — Profile schema** (§26.4–26.5): governed default = a **dedicated enrichment table** keyed on
  canonical identity, not new columns on EN004's `financial_instrument` — final within Q1's answer.
- **A6 — Finnhub industry normalisation** (§26.6): the provider `finnhubIndustry` string is stored
  as-is as the `sector` classification (matches FD004's "provider string shown directly"); no
  canonical taxonomy.
- **A7 — Frankfurter base URL** default `https://api.frankfurter.dev`, overridable via
  `FRANKFURTER_BASE_URL` (mirrors the `FINNHUB_BASE_URL` pattern added for FD004's E2E).
- **A8 — Deterministic decimal type**: `BigDecimal` end to end for price and rate.
- **A9 — E2E stubs**: the existing `finnhub-stub` container is extended (drop `/forex/rates`) and a
  sibling `frankfurter-stub` (or one combined stub service) serves `/v1/latest`; both driven by
  `*_BASE_URL`. `./start.sh` (local, real providers) is unaffected by the E2E override.
- **A10 — FD004 E2E-001 portfolio**: governed default = keep the current deterministic portfolio
  (AAPL + SAN) and have the Finnhub stub keep faking the non-US quote, so FD004's approved E2E-001
  numbers are unchanged; switching E2E-001 to all-US is an acceptable planning alternative.
- **A11 — No formal closure of the prior EN005**: this revision replaces the prior EN005 artifacts
  in place (enabler §28 — the prior EN005 was implemented but not closed/committed).

## Dependencies

- **Enabler EN005 (revised)** — the authoritative source; §29 signed 2026-09-04.
- **EN004** — canonical `ticker + market(MIC)` identity; unchanged. Profile enrichment references
  it, never mutates it.
- **FD004 — Portfolio Valuation & Allocation** — the primary consumer. Its `EnMarketDataGatewayAdapter`,
  the adapter's tests, and its E2E boundary (`finnhub-stub` + a new Frankfurter stub, `compose.e2e.yaml`,
  `e2e.sh`) are updated by this work; its domain/business/calculator/persistence/API/UI are **not**.
- **EN002** — the containerized platform + `./e2e.sh`, where FD004's two mandatory E2E scenarios run
  with both provider boundaries controlled.
- **ADR-001 / ADR-002 / ADR-003** — governing architecture; unchanged.
- **Frankfurter public API** (`https://api.frankfurter.dev`) — ECB-sourced FX, no key, no rate
  limit; new outbound dependency (infrastructure only). **This is a new external provider** — the
  enabler §29 approves it explicitly.
- **Governance**: `.specify/memory/constitution.md`; `product/architecture/{architecture,
  architecture-rules,technology-policy}.md`; `product/engineering/{development-rules,
  testing-strategy,definition-of-done}.md`.

## Out of Scope

- Portfolio valuation / allocation arithmetic or UI (FD004 owns those).
- Any investor-facing REST endpoint or frontend (EN005 remains in-process ports + business
  operations).
- Automatic multi-provider fallback chains (§16 — "not required now").
- TTL / scheduled refresh of persisted profiles (§13, §26.7).
- Caching **infrastructure** (Redis, Memcached, …) — an ADR-gated decision if ever needed.
- A third currency beyond EUR/USD; non-US market coverage for price/profile (a future adapter/provider).
- Changing EN004's catalog schema or the canonical instrument-identity model.
- Persisting `MarketPrice` or `FxRate` (time-sensitive; not database-first).
- Renaming the enabler (it stays "Establish Finnhub Market Data Integration" per §25.1).
