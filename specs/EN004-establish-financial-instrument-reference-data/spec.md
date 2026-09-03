# Feature Specification: Establish Financial Instrument Reference Data (EN004)

**Feature Branch**: `EN004-establish-financial-instrument-reference-data`

**Created**: 2026-09-03

**Status**: Draft

**Input**: Technical Enabler: "Establish the reusable technical capability required by FD002 to
provide controlled Financial Instrument, Market, and Currency reference data. Expose normalized
local reference data to business and frontend capabilities without making the user-facing flow
depend on an external provider at runtime. Do not introduce new Portfolio business behavior."

**Authoritative Source**: `product/definition/enablers/EN004-establish-financial-instrument-reference-data/EN004-establish-financial-instrument-reference-data.md` (**Status: Approved** — §34 signed by jaruiz, 2026-09-03; decision record in [research.md](./research.md) §"Decision taken (human)"). *(The invoking prompt referenced `enabler-definition.md`; the actual authoritative file in the repository is `EN004-establish-financial-instrument-reference-data.md`. No `enabler-definition.md` exists.)*

**Supporting Technical Decision**: `product/definition/enablers/EN004-establish-financial-instrument-reference-data/EN004-yahoo-normalization-decision.md` (mapping-driven Yahoo → canonical normalization algorithm).

**Reference Mapping Data** (infrastructure configuration, not product master data):
`product/definition/enablers/EN004-establish-financial-instrument-reference-data/reference-data/yahoo-exchange-to-mic-mapping.csv`,
`product/definition/enablers/EN004-establish-financial-instrument-reference-data/reference-data/yahoo-exchange-suffix-overrides.csv`.

**Supports**: FD002 — Select Financial Instrument from Catalog.

**Governing Architecture**: ADR-003 — Standard Spring Backend Architecture (module-first
`domain` / `business` / `infrastructure`, Spring Data JPA, Maven, ArchUnit; REST mappers in
`infrastructure.api.rest.mapper` per the 2026-09-02 amendment). ADR-001 (single `core-service`
deployable) is unchanged. **No new ADR is required** — EN004 adds a functional module and a
persistence schema within the existing approved topology; if planning surfaces a material
architectural decision (a new deployable, messaging, a new persistence technology, a scheduler),
it MUST be raised for human approval, not chosen silently.

> **Governance note.** The enabler was **approved by the human on 2026-09-03** (`product/…/EN004-….md`
> §34 signed; decision record in `research.md` §"Decision taken (human)"), together with the two data
> decisions: the initial instrument dataset is the **curated `instruments.sample.csv`** (not the full
> Yahoo CSV), and the `product/` copies of the mapping CSVs are **kept** (not deleted). All
> prerequisite gates for `/speckit-implement` are **cleared**.

---

## Clarifications

### Session 2026-09-03

- Q: Does EN004 deliver the FD002-facing REST search endpoint(s) itself, or only the backend
  capability? → A: **EN004 delivers the instrument-search endpoint only** —
  `GET /api/financial-instruments?query=…`, contract-first (OpenAPI 3.0.3, RFC 9457 errors, a
  contract test). A standalone `GET /api/markets` is **deferred** to FD002 (or a follow-up) unless
  FD002 needs a separate Market selector.
- Q: Which Financial Instrument types are in the initial selectable catalog? → A: **Equities and
  ETFs.** Both are plain listed instruments identified by `ticker + market + currency`.
  `instrumentType` is stored as an optional descriptive attribute, **not** an FD002 search filter.
  Crypto and derivatives remain out of scope.

---

## Enabler Nature *(mandatory)*

EN004 is a **Technical Enabler**, not a product Feature Definition.

It introduces **no investor-facing behavior**, no new Portfolio capability, and no change to
Portfolio or Position business semantics. Its purpose is to give the platform a **local,
provider-neutral catalog** of Markets and Financial Instrument listings — persisted in PostgreSQL,
populated through a controlled ingestion path, and queryable by the backend — so that FD002 can
replace free-text `ticker` / `market` / `currency` entry with controlled selection **without the
user-facing flow depending on any external provider at request time**.

Because this is an enabler:

- The scenarios below describe **backend-developer / maintainer / operator workflows**, not
  investor journeys. They are facets of one capability, ordered by importance.
- "Acceptance" is expressed through the enabler's **Verification Criteria (VC-001 … VC-020)**.
- No business domain entities or rules are added, removed, or reinterpreted. "Market" and
  "Currency" as *managed reference concepts* are introduced by **FD002** (FD002 §10 Information
  Objects); EN004 supplies the technical mechanism, within the existing **Financial Instruments**
  functional domain (`product/definition/global/domains.md`).
- The **FD002 Playwright E2E critical journey** is the ultimate product-level proof that EN004 is
  sufficient (VC-020). EN004's own closure gates are its VC set plus a deterministic-offline
  catalog demonstration.

---

## User Scenarios & Testing *(mandatory)*

The beneficiaries are the **engineering team** (human and AI contributors) building FD002 and any
future reference-data work, plus the **operator** who loads and refreshes reference data.

EN004 is **one capability** delivered as a vertical slice of the existing platform. The "stories"
below are facets ordered by priority; none ships as a standalone product, but each is
independently *verifiable*.

### User Story 1 - The `financialinstrument` module with a persisted catalog (Priority: P1)

As a backend developer, I find a new `com.myfinaimanager.core.financialinstrument` module built to
ADR-003 (`domain/{model,ports,exceptions}` · `business` · `infrastructure/{api,persistence,reference}`),
with Market and Financial Instrument listing data persisted in PostgreSQL through Spring Data JPA
and a Flyway migration, and an ArchUnit suite that fails the build if the module breaks the ADR-003
dependency direction or places JPA / file-parsing / provider types outside `infrastructure`.

**Why this priority**: Every other facet needs the module and its schema to exist. It also proves
EN004 extends the existing `core-service` as a modular-monolith module (ADR-001/ADR-003), not a new
service.

**Independent Test**: Inspect
`implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/financialinstrument/`
and the new Flyway migration; run `./mvnw verify` and confirm the module's Testcontainers
persistence ITs pass and the ArchUnit suite enforces `domain !→ infrastructure`,
`business !→ infrastructure`, JPA `@Entity` only under `infrastructure.persistence.entity`, and
CSV/provider parsing types only under `infrastructure`.

**Acceptance Scenarios**:

1. **Given** the migrated backend, **When** the `financialinstrument` module is inspected, **Then** it is organized as `domain/{model,ports,exceptions}` · `business` · `infrastructure/{api/rest[/dto], api/rest/mapper, persistence/{entity,repository,mapper}, reference/...}` per ADR-003. *(VC-017; EN004 §5, §16)*
2. **Given** the platform database, **When** the Flyway migrations run, **Then** a new forward migration creates the Market and Financial Instrument listing tables (and any provenance table), Flyway history advances, and Hibernate `ddl-auto` is `none`/`validate` — Hibernate never creates or alters the schema. *(VC-001, VC-003, VC-014; EN004 §16)*
3. **Given** the module, **When** the domain model classes (`Market`, `FinancialInstrument`, value objects) are inspected, **Then** they contain no JPA / Hibernate / Spring / CSV-library / HTTP annotations or imports; persistence uses separate JPA entities with an explicit mapper. *(VC-013; EN004 §6, §16, §17)*
4. **Given** the persistence layer, **When** a Market and a Financial Instrument listing are saved and read back on Testcontainers PostgreSQL, **Then** identity, MIC, ticker, currency, active state, and optional ISIN / external reference round-trip exactly. *(VC-001, VC-002, VC-003, VC-015)*
5. **Given** the architecture test suite, **When** it runs, **Then** it fails on a deliberately introduced `domain → infrastructure` (or `business → infrastructure`) dependency in the module, and every rule is non-vacuous against the new packages. *(VC-017)*

---

### User Story 2 - Provider-neutral ingestion and mapping-driven normalization (Priority: P1)

As an operator, I run a controlled reference-data import that reads source files (an ISO 10383 –
compatible Market file and the Yahoo instrument CSV), **normalizes** each record through the
committed mapping files, and upserts canonical rows into the local catalog — retaining the raw
provider symbol as source metadata, resolving the canonical MIC and currency from the mapping (not
by guessing), and **quarantining** every unsupported / ambiguous / mapping-mismatch row with
diagnostics instead of letting it become a catalog entry.

**Why this priority**: This is the core of the enabler — turning provider/source data into a
canonical, source-independent catalog. It is the second thing ADR-003 and the enabler insist on
(no provider payloads in the domain).

**Independent Test**: Run the import against the committed fixtures; assert that
`AAPL + NMS → AAPL · XNAS · USD`, `SAN.MC + MCE → SAN · XMAD · EUR` (suffix stripped via mapping),
`ADS.DE + FRA → ADS · XETR · EUR` (explicit suffix override), a GBP/`LSE` row is skipped
(unsupported currency), an `ENX + NX` row is quarantined (currency not inferable), and the raw
symbol `SAN.MC` is stored as `providerSymbol` on the persisted row. Confirm no domain type or
public contract references a Yahoo/CSV/provider payload type.

**Acceptance Scenarios**:

1. **Given** a Yahoo source row `(symbol=SAN.MC, Exchange=MCE)`, **When** it is imported, **Then** the catalog stores `providerSymbol = SAN.MC`, canonical `ticker = SAN`, `market = XMAD` (ISO 10383 listing MIC), `currency = EUR`, `active = true`; `BMEX` may be retained only as optional operating-MIC metadata. *(VC-002, VC-005, VC-010; decision §"Canonical Market")*
2. **Given** a Yahoo source row `(symbol=AAPL, Exchange=NMS)`, **When** it is imported, **Then** the catalog stores `providerSymbol = AAPL`, `ticker = AAPL`, `market = XNAS`, `currency = USD`. *(VC-004, VC-005; §10)*
3. **Given** a Yahoo source row `(symbol=ADS.DE, Exchange=FRA)`, **When** it is imported, **Then** the `(FRA, DE)` suffix override applies: `ticker = ADS`, `market = XETR`, `currency = EUR`. *(decision §"Import Validation")*
4. **Given** a source row whose configured `expected_yahoo_suffix` does not match the symbol's actual suffix, **When** it is imported, **Then** the row is **quarantined as a mapping mismatch** — never guessed, never truncated generically. *(EN004 §9A; decision §"Mapping-Driven Suffix Removal")*
5. **Given** source rows resolving to a currency other than EUR or USD, or to an `Exchange` with `supported_for_fd002 = false`, or with no `Exchange` mapping, or where the canonical MIC / non-empty ticker cannot be resolved, **When** they are imported, **Then** none of them enters the selectable catalog; each is counted in the appropriate skip/quarantine category. *(VC-010; decision §"Import Validation")*
6. **Given** the mapping files, **When** the module is inspected, **Then** the exchange-to-MIC mapping and the suffix-override table live as infrastructure reference configuration inside the module (not in `domain`, not exposed through the public API). *(VC-010; EN004 §9A, §17, §20; decision §"Mapping Governance")*
7. **Given** the canonical model, **When** a domain type or public DTO is inspected, **Then** it contains **no** provider-specific source payload type (no Yahoo record, no CSV library row, no exchange-provider model). *(VC-010; EN004 §4, §17, §20)*

---

### User Story 3 - Local catalog search for FD002 (Priority: P1)

As a backend developer building FD002, I can search the local catalog by ticker or by instrument
name, case-insensitively, and get back only entries FD002 may offer for a new Position — `active =
true` and `currency ∈ {EUR, USD}` — each result carrying a valid `ticker + market(MIC) + currency`
combination, with the query executed entirely against local PostgreSQL data and never calling an
external provider at request time.

**Why this priority**: This is the capability FD002 consumes. Without it, FD002 cannot replace
free-text entry.

**Independent Test**: With the fixture catalog loaded, search `aapl` (lower case) → the Apple
listing on `XNAS`/`USD`; search a partial instrument name → matching listings; confirm an inactive
listing and a non-EUR/USD listing are excluded from default results; confirm the search path
touches only the database (no outbound provider call).

**Acceptance Scenarios**:

1. **Given** the catalog contains an active `AAPL · XNAS · USD` listing, **When** a search for `AAPL` (any case) runs, **Then** that listing is returned with its ticker, MIC, currency, name, and active state. *(VC-006, VC-007, VC-009; EN004 §19)*
2. **Given** the catalog contains a known instrument name, **When** a search for part of that name (any case) runs, **Then** the matching listing(s) are returned. *(VC-008; EN004 §19)*
3. **Given** the catalog contains inactive listings and non-EUR/USD listings, **When** a default FD002 search runs, **Then** those entries are **not** returned. *(VC-006, VC-009; EN004 §15, §19)*
4. **Given** a search request, **When** it is served, **Then** it is answered from local platform data only — an external reference-data provider is not contacted, and provider unavailability has no effect on the result. *(VC-006; EN004 §11)*
5. **Given** the initial dataset size, **When** search is implemented, **Then** it uses PostgreSQL query capabilities and does **not** introduce a dedicated search engine (OpenSearch/Elasticsearch). *(EN004 §19; technology-policy)*

---

### User Story 4 - Deterministic, containerized, offline-capable reference data (Priority: P1)

As a maintainer, the reference data used by automated tests and by the FD002 E2E is deterministic
and committed (no live Internet), the catalog bootstrap/import runs inside the existing
containerized platform against the existing PostgreSQL container, and no developer needs a
locally installed database.

**Why this priority**: EN004's product-level value is realized only when FD002's mandatory E2E can
run offline against a known catalog. The enabler explicitly requires this (§25, §27, VC-016,
VC-018).

**Independent Test**: From a clean checkout, `./start.sh` (or the documented bootstrap step) brings
the platform up with a populated catalog; `./e2e.sh` runs FD002's journey with no outbound Internet
access; `./mvnw verify` runs the Testcontainers ITs with no network dependency beyond pulling
container images.

**Acceptance Scenarios**:

1. **Given** a clean checkout with no Internet access, **When** the automated backend tests run, **Then** they pass using committed deterministic fixtures — including at least an `AAPL · XNAS · USD` example and at least one EUR-listed example. *(VC-016; EN004 §10, §25, §26)*
2. **Given** the containerized platform (EN002), **When** it is started, **Then** the reference-data catalog is available (bootstrapped/imported as a documented, reproducible step) using the existing PostgreSQL container — no host database, no ad-hoc manual SQL. *(VC-018; EN004 §27)*
3. **Given** the FD002 browser E2E, **When** `./e2e.sh` runs, **Then** the catalog data required for the controlled-selection journey is present and the journey does not depend on any external reference-data provider. *(VC-016, VC-020; EN004 §26 "E2E Support")*
4. **Given** any file needed at runtime, **When** the platform is packaged, **Then** its packaging/mounting mechanism is explicit and reproducible (documented, part of the image or a mounted resource — not a manual copy). *(VC-018; EN004 §27)*

---

### User Story 5 - Safe, repeatable, observable imports (Priority: P2)

As an operator, I can run the same import repeatedly without creating duplicate Markets or
listings, a malformed or unavailable source never deletes or half-corrupts the existing valid
catalog, and every run emits structured diagnostics (source id, start/end, processed / imported /
updated / skipped / quarantined / failed counts) with no secrets in the logs.

**Why this priority**: Correctness and operability of the ingestion mechanism. Important for
real use, but FD002 can be developed against a once-loaded catalog, so it is P2 relative to the
search capability.

**Independent Test**: Run the import twice over the same fixtures → identical catalog, zero
duplicates, second run reported as all "updated"/"unchanged"; run the import over a fixture with a
deliberately corrupt record → the batch fails safely (or isolates the bad record per the planned
strategy), the previously valid catalog is intact, and the diagnostics name the offending record.

**Acceptance Scenarios**:

1. **Given** a completed import, **When** the identical import is run again, **Then** no duplicate Market or Financial Instrument listing rows are created; canonical identity (`ticker + market`) is the upsert key. *(VC-011; EN004 §13)*
2. **Given** a source that is malformed or unavailable, **When** an import is attempted, **Then** the existing valid catalog is not deleted and is not left partially corrupted; the batch/transaction strategy (defined in planning) is applied deliberately. *(VC-012; EN004 §22)*
3. **Given** any import run, **When** it completes or fails, **Then** structured diagnostics report the source identifier, start/end, and the counts `processed / imported / updated / skippedUnsupportedCurrency / skippedUnsupportedMarket / quarantinedAmbiguous / quarantinedInvalid / failed`; no credential or provider token is logged. *(EN004 §24; decision §"Import Validation")*
4. **Given** an import that omits an entry present from a previous run, **When** it completes, **Then** existing reference data is **not** deleted merely because the source no longer returns it (no lifecycle/delisting rule is defined by EN004); inactivation, if any, follows a documented rule decided in planning. *(EN004 §15, §33.9)*
5. **Given** minimal provenance is retained (`source`, optional `sourceReference`, optional `lastImportedAt`), **When** the catalog is inspected, **Then** that metadata is present for operational traceability and does not force provider-specific concepts into the canonical model. *(EN004 §21)*

---

### Edge Cases

- **Source CSV not present in the repository** — the upstream `Yahoo-Finance-Ticker-Symbols.csv` is not committed. Planning MUST resolve whether a curated deterministic subset is committed as a fixture and/or the full file is fetched by a controlled bootstrap step (EN004 §33.1–§33.3). Tests and the FD002 E2E MUST rely only on committed deterministic data (§25).
- **Listing/segment MIC vs operating MIC** — prefer the specific listing MIC (`XMAD`) over the operating MIC (`BMEX`) when the listing can be identified reliably; otherwise the operating MIC is metadata only (decision §"Canonical Market").
- **A period inside a legitimate symbol** — generic "strip everything after the last dot" is forbidden; a suffix is removed only when an explicit override or the exchange rule's `expected_yahoo_suffix` matches exactly; otherwise the symbol is left intact or the row is quarantined (EN004 §9A; decision §"Algorithm").
- **Generic Euronext (`ENX`) rows** — resolved only from the Yahoo suffix via the override table; `ENX + NX` cannot infer a currency and MUST be quarantined.
- **Ambiguous / legacy Yahoo exchange codes** (`EUX`, `MDD`, `MAD`, `OBB`, `PNK`) — excluded from the initial selectable universe (`supported_for_fd002 = false` or unresolved); counted as skipped/quarantined, never guessed.
- **`IOB` USD rows** — admitted only because the currency filter passes (`ILSE`, USD); GBP `LSE` rows are excluded by the EUR/USD filter even though the exchange is otherwise recognizable.
- **Duplicate normalized identity within one import** — if two source rows normalize to the same `(ticker, MIC)` with conflicting attributes, the row is quarantined as an unexpected conflict rather than silently overwritten (decision §"Import Validation").
- **Inactive instrument referenced by a historical Position** — inactive listings MUST remain representable (FD001 Positions may point to them); FD002 search MUST NOT offer them for new Positions (EN004 §15).
- **Empty canonical ticker after normalization** — the row is rejected/quarantined (decision §"Algorithm" step 10).
- **Currency modeling** — if Currency is a domain enum/value object rather than a table (EN004 §6, §33.5), adding a third currency later is a code change; the spec does not require a currency master table for the EUR/USD initial scope.
- **Refresh failure mid-run** — a partial/failed refresh MUST NOT corrupt the previously valid local catalog (EN004 §11, §22).

---

## Requirements *(mandatory)*

### Module, architecture & build

- **FR-001**: EN004 MUST add a new functional module `com.myfinaimanager.core.financialinstrument` inside the existing `core-service` deployable (ADR-001 unchanged — no new service, no service extraction). The module MUST use the ADR-003 module-first three-area structure: `domain/{model,ports,exceptions}`, `business`, `infrastructure/{api/rest[/dto], api/rest/mapper, persistence/{entity,repository,mapper}, reference/...}`. *(EN004 §5, §28; VC-017)*
- **FR-002**: The dependency direction MUST be `infrastructure → business → domain` with no cycles. `domain` MUST NOT depend on `business` or `infrastructure`; `business` MUST NOT depend on `infrastructure`. `domain` MUST NOT depend on Spring, Spring Data, JPA/Hibernate, HTTP/servlet, messaging, JDBC, a CSV/parsing library, or any provider SDK. *(EN004 §17, §26; ADR-003; VC-013, VC-017)*
- **FR-003**: The backend MUST build and test with **Maven** via the existing `./mvnw` wrapper. No Gradle. No Java/Spring major-version change. *(EN004 §28)*
- **FR-004**: An **ArchUnit** suite MUST enforce, for the `financialinstrument` module: the three dependency-direction rules above; JPA `@Entity` classes reside under `..financialinstrument.infrastructure.persistence.entity..`; Spring Data repository interfaces reside under `..infrastructure.persistence.repository..`; REST controllers under `..infrastructure.api.rest..` and REST mappers under `..infrastructure.api.rest.mapper..`; CSV/file-parsing and provider-format types reside under `..infrastructure..` only. Rules MUST be non-vacuous against the new packages. The existing `portfolio`-module architecture test MUST remain green. *(EN004 §26; ADR-003 "Architecture Verification"; VC-017)*

### Domain model & identity

- **FR-005**: The `domain` package MUST introduce provider-neutral reference concepts: a **Market** (canonical `mic` as ISO 10383, `name`, optional `country`, `active`; optional `operatingMic` as reference metadata) and a **Financial Instrument listing** (`id`, `name`, `ticker`, `market` (MIC), `currency`, optional `isin`, optional `externalReference`, `active`; `providerSymbol` retained as source metadata). *(EN004 §6, §9A; FD002 §6; VC-002, VC-010)*
- **FR-006**: A Financial Instrument listing MUST be uniquely identifiable by normalized **`ticker + market`**, aligned with FD001 Position identity. The database MUST prevent duplicate reference rows for the same normalized `(ticker, market)` identity. ISIN is additional reference information and MUST NOT replace `ticker + market` as identity. *(EN004 §7; FD001; VC-005, VC-011)*
- **FR-007**: **Currency** MUST be constrained to the initial supported values **EUR** and **USD** (ISO 4217). Currency MAY be modeled as a domain enum / value object rather than a persisted master table if that remains the simplest compliant implementation. → **Resolved in planning:** domain enum `SupportedCurrency {EUR, USD}` + a `CHECK` constraint (research.md D2, OD-EN004-5). *(EN004 §6, §33.5; FD002 BR-003; VC-004)*
- **FR-008**: A single economic instrument MAY have multiple listings (distinct `ticker + market` rows). The model represents a **selectable listing**, not a provider payload. *(EN004 §6)*
- **FR-009**: Markets and Financial Instrument listings MUST support **active / inactive** state. Inactive listings MUST remain representable (historical FD001 Positions may reference them). The ingestion process MUST NOT delete historical reference data merely because a source stops returning it, unless a documented lifecycle rule is defined in planning. *(EN004 §15, §33.9; VC-012)*

### Market reference data

- **FR-010**: Markets MUST be represented using **ISO 10383 MIC** where available and MUST be loadable through a **controlled file-based import** (initial preferred format: CSV). Source-specific column names MUST NOT propagate into `domain` types or the public API. *(EN004 §8; VC-002, VC-010)*
- **FR-011**: The first implementation MUST provide enough deterministic Market data to cover every MIC referenced by the initial instrument dataset and by FD002 tests. → **Resolved in planning:** a committed curated ISO 10383 – compatible subset `src/main/resources/reference-data/markets.csv` (not the full ISO file), covering every MIC used by the instrument fixtures + FD002 (research.md D10, OD-EN004-2). *(EN004 §8, §32, §33.1)*

### Financial Instrument reference data & Yahoo normalization

- **FR-012**: Financial Instrument data MUST support **controlled import** into the local catalog, favouring reproducible data files over mandatory live provider calls. The first implementation MUST provide enough deterministic data to support FD002 development, automated tests, and the FD002 E2E. *(EN004 §9, §10, §25; VC-016)*
- **FR-013**: For the initial implementation, the supplied Yahoo instrument CSV is an approved **import source** (never canonical domain data). The import adapter MUST translate the Yahoo `Exchange` value and symbol through the committed `yahoo-exchange-to-mic-mapping.csv` and `yahoo-exchange-suffix-overrides.csv`. *(EN004 §9A, §35; decision)*
- **FR-014**: The import MUST persist **both** the raw provider symbol as `providerSymbol` **and** the normalized canonical `ticker`. The provider symbol MUST NOT become the canonical ticker. *(EN004 §9A; decision §"Provider Symbol and Canonical Ticker"; VC-005, VC-010)*
- **FR-015**: Canonical `market` MUST be an ISO 10383 MIC. When a venue has an operating MIC and a more specific listing/segment MIC that can be determined reliably, the **listing MIC** MUST be used as canonical (e.g. Yahoo `MCE`/`.MC` → `XMAD`; `BMEX` optional operating-MIC metadata). *(EN004 §9A; decision §"Canonical Market"; VC-002)*
- **FR-016**: Suffix removal MUST be **mapping-driven**, following the algorithm in the normalization decision. Generic "strip everything after the last period" MUST NOT be implemented. A suffix is stripped only when `(exchange, suffix)` has an explicit override, or the exchange rule declares that suffix as `expected_yahoo_suffix` and the symbol ends exactly with it. If the exchange rule has an empty suffix, the symbol is left intact. If the symbol suffix and the mapping disagree, the row is **quarantined**, not guessed. The raw symbol is always retained. *(EN004 §9A; decision §"Ticker Suffix Normalization"; SC — no generic strip)*
- **FR-017**: Only rows whose mapping has `supported_for_fd002 = true` **and** whose normalized currency is **EUR or USD** MAY enter the initial selectable catalog. Every other row (no mapping; not approved for FD002; unsupported currency; suffix/mapping mismatch; unresolvable MIC; empty ticker; unexpected `(ticker, MIC)` conflict) MUST be **skipped or quarantined with diagnostics** — never silently accepted. *(EN004 §9A, §12; decision §"Import Validation"; VC-010)*
- **FR-018**: Ticker and MIC casing normalization MUST be applied consistently across ingestion, storage, and search. → **Resolved in planning:** canonical `ticker` and `mic` are stored **uppercase + trimmed**; the search normalizes user input the same way (research.md D2, spec A6, OD-EN004-8). *(EN004 §33.6)*

### Ingestion model, idempotency & failure handling

- **FR-019**: The ingestion flow MUST be explicit: read source → parse → validate source record → normalize → map to canonical model → upsert local catalog → report result. Invalid source records MUST NOT silently become valid catalog entries. Infrastructure adapters own file access, CSV parsing, and provider formats; the **business** layer owns orchestration and validation; ports use provider-neutral domain types only. *(EN004 §4, §12, §17, §18)*
- **FR-020**: Reference-data loading MUST be **safely repeatable** — running the same import more than once MUST NOT create duplicate Markets or listings. The upsert key is the canonical normalized identity (`mic` for Markets, `ticker + market` for listings). *(EN004 §13; VC-011)*
- **FR-021**: Ingestion MUST **fail safely**. A malformed or unavailable source MUST NOT delete the existing valid catalog, MUST NOT partially corrupt a batch without a deliberate consistency strategy, and MUST NOT create invalid `ticker/market/currency` combinations. → **Resolved in planning:** one transaction per import run (all-or-nothing on a hard failure → the previous catalog is untouched); expected per-row skips/quarantines do not fail the run (research.md D7, OD-EN004-10). *(EN004 §22, §33.8; VC-012)*
- **FR-022**: EN004 MUST support **manual / on-demand** reference-data loading. Scheduled/automatic synchronization, provider-API sync, incremental updates, delisting detection, and provenance history are **out of scope**; EN004 MUST NOT introduce scheduling infrastructure. *(EN004 §14)*
- **FR-023**: The reference-data import MUST be runnable inside the containerized platform (FR-030) and MUST NOT require a host database or manual SQL. → **Resolved in planning:** a flag-guarded Spring `ApplicationRunner` (`app.reference-data.import-on-startup`, default on; off in test slices) that runs the import from the classpath fixtures on boot; the same business operation is callable directly from tests. No CLI, no scheduler (research.md D11, OD-EN004-14). *(EN004 §33.12)*

### Search capability

- **FR-024**: The backend MUST support **search by ticker** and **search by instrument name**, **case-insensitive** where practical, executed entirely against local PostgreSQL data with **no external provider call at request time**. *(EN004 §11, §19; VC-006, VC-007, VC-008)*
- **FR-025**: Default search results MUST be limited to entries compatible with FD002: `active = true` **and** `currency ∈ {EUR, USD}`. Each result MUST carry a valid `ticker + market(MIC) + currency` combination plus the instrument name and active state. *(EN004 §15, §19; FD002 BR-003, BR-006; VC-009)*
- **FR-026**: The implementation MUST remain suitable for a small-to-moderate reference dataset using PostgreSQL only — **no dedicated search engine** (OpenSearch/Elasticsearch). → **Resolved in planning:** one Spring Data `@Query` — exact-ticker (case-insensitive) OR `name ILIKE '%q%'`, filtered `active = true AND currency ∈ {EUR, USD}`, exact-ticker matches ranked first; a `pg_trgm` index is added only if the committed dataset shows plain `ILIKE` is too slow (research.md D5, OD-EN004-9). *(EN004 §19, §33.7; technology-policy AR-022, §"Search Technologies")*

### API support

- **FR-027**: EN004 MUST deliver **exactly one** FD002-facing REST endpoint — instrument search, `GET /api/financial-instruments?query=…` (or an equivalent business-oriented path decided in planning) — **contract-first**: an OpenAPI operation under `implementation/platform/contracts/openapi/` plus a contract test. A standalone `GET /api/markets` endpoint is **out of scope for EN004** (deferred to FD002 or a follow-up per EN004 §33.11); Market data reaches the frontend through instrument-search results. *(EN004 §20, §26, §33.11; clarified 2026-09-03; VC-020)*
- **FR-028**: The instrument-search endpoint MUST follow the project's OpenAPI policy: contract-first, **OpenAPI 3.0.3** (to match the `swagger-request-validator` 2.44.x used by the existing contract tests), business-oriented resources, stable machine-readable errors — RFC 9457 `application/problem+json`. The response DTO MUST expose only business fields (instrument `name`, `ticker`, `market` MIC, `currency`, `active`, optional `isin`). **Provider-specific source fields — `providerSymbol`, Yahoo `Exchange`, mapping internals, operating-MIC metadata — MUST NOT appear in the public DTO.** Default results follow FR-025 (`active = true`, `currency ∈ {EUR, USD}`). *(EN004 §20; ADR-011, ADR-012; VC-010; VC-006)*

### Persistence & schema

- **FR-029**: Reference data MUST be persisted in **PostgreSQL** using **Spring Data JPA**; domain models MUST remain JPA-free with an explicit domain↔entity mapper. **Flyway** MUST own schema evolution through a new forward migration; Hibernate schema auto-generation MUST NOT be used (`ddl-auto: none` or `validate`). The existing `portfolio` schema and its migrations MUST NOT be edited. *(EN004 §16; ADR-003; constitution VII; VC-013, VC-014)*
- **FR-030**: EN004 MUST preserve the EN002 containerized runtime. Reference-data persistence uses the existing PostgreSQL container; import/bootstrap MUST work in the containerized platform with no host PostgreSQL. If files are needed at runtime, their packaging/mounting MUST be explicit and reproducible. The canonical `start.sh` / `stop.sh` / `e2e.sh` interface MUST NOT be broken. *(EN004 §27; VC-018)*

### Provenance & observability

- **FR-031**: Reference rows MUST retain minimal provenance (`source`, optional `sourceReference`, optional `lastImportedAt`) for operational traceability, without forcing provider-specific concepts into the canonical model. → **Resolved in planning:** stored as `source` / `source_reference` / `last_imported_at` columns **on** the `market` and `financial_instrument` rows — no separate history table for the first version (research.md D8, OD-EN004-13). *(EN004 §21, §33.10)*
- **FR-032**: Import operations MUST emit **structured** diagnostics: source identifier, start/end, and the seven counts `processed / imported / updated / skippedUnsupportedCurrency / skippedUnsupportedMarket / quarantinedAmbiguous / quarantinedInvalid` (matching the decision doc §"Import Validation"). Secrets, credentials, and provider tokens MUST NOT be logged. A **hard** import failure (unreadable source, parser error, database error) MUST abort the run, roll it back, and raise an actionable error identifying the offending record(s) — it is not represented as a counter. *(EN004 §12, §24; decision §"Import Validation")*

### Testing & determinism

- **FR-033**: Automated tests MUST use **deterministic committed fixtures** and MUST NOT depend on Internet availability, third-party rate limits, external API credentials, or live provider responses. Fixtures MUST include at least an `AAPL · XNAS · USD` equivalent and at least one EUR-listed instrument; the exact instruments are implementation data and MUST NOT become product requirements. *(EN004 §10, §25; VC-016)*
- **FR-034**: EN004 MUST provide: **unit** tests (source-record normalization, validation, identifier normalization, supported-currency rules, active/inactive filtering, mapping-driven suffix rules incl. the quarantine cases); **integration** tests on **Testcontainers PostgreSQL** (Market persistence, Financial Instrument persistence, unique `ticker + market`, repeated-import idempotency, search by ticker, search by name, EUR/USD filtering, active/inactive behavior); **contract** tests for any exposed reference-data endpoint; **architecture** tests (FR-004). *(EN004 §26; constitution VII; VC-015, VC-017)*
- **FR-035**: EN004 MUST provide catalog data that allows **FD002's Playwright E2E** to run against the containerized platform **without external Internet access**. *(EN004 §26 "E2E Support"; VC-016, VC-020)*

### Security

- **FR-036**: Reference-data imports using local/public files MUST NOT require secrets. No credentials, tokens, or private data may be committed. Synthetic/public test data only. If a future external API needs credentials, they remain outside source control and outside `domain` models and logs (future work, not EN004). *(EN004 §23; constitution; AR-037)*

### Scope guardrails

- **FR-037**: EN004 MUST NOT change Portfolio or Position business semantics, MUST NOT change the FD001 `ticker + market` Position identity, MUST NOT add live prices / historical prices / FX / valuation / news / AI enrichment, MUST NOT add user-defined instruments or markets, crypto, or derivatives (beyond what an approved source explicitly includes), MUST NOT add a new deployable service, messaging, a scheduler, a new persistence technology, a dedicated search engine, or paid-provider integration, and MUST NOT let the frontend access an external reference-data provider directly. *(EN004 §3 Out of Scope; VC-019)*
- **FR-038**: Any material deviation from the authoritative enabler or from ADR-003 discovered during planning/implementation MUST be surfaced for human approval before proceeding. The EN004 §33 open items are **technical** decisions for planning; they MUST NOT change FD002 business behavior. *(EN004 §33; constitution IV)*
- **FR-039**: EN004 MUST NOT silently edit human-governed `product/` documents (`information-model.md`, `domains.md`, `architecture.md`, `architecture-rules.md`, `technology-policy.md`, FD002, the enabler). If implementation reveals a needed change (e.g. adding "Market" / "Currency" as first-class information objects), it MUST be raised for human approval. *(constitution I; EN004 §33)*

### Instrument-type scope

- **FR-040**: The initial selectable catalog MUST contain **equities and ETFs** — both plain
  exchange-listed instruments identified by `ticker + market + currency`. `instrumentType` MUST be
  stored as an **optional descriptive attribute** on the listing (best-effort from the source) and
  MUST NOT be an FD002 search filter. Crypto assets and derivatives remain out of scope (FR-037).
  *(FD002 Open Question 3; clarified 2026-09-03; VC-020)*

---

### Key Entities

- **Market** *(reference data, new — implemented by EN004; introduced as an information concept by FD002 §10)*: a trading venue identified by an ISO 10383 **MIC**. Attributes: `mic`, `name`, optional `country`, `active`, optional `operatingMic` (reference metadata only). Identity: `mic`.
- **Financial Instrument listing** *(reference data, new)*: a selectable listing of an investable asset (equity or ETF for the initial scope). Attributes: `id`, `name`, `ticker`, `market` (MIC), `currency` (EUR/USD initially), optional `isin` (ISO 6166), optional `externalReference`, optional `instrumentType` (descriptive, not a filter), `active`, `providerSymbol` (source metadata), optional provenance (`source`, `sourceReference`, `lastImportedAt`). Identity: normalized `ticker + market`. A single economic instrument may have several listings.
- **Currency** *(constrained value set)*: ISO 4217 code restricted to `{EUR, USD}` for the initial scope. Modeled as a domain enum / value object, not a persisted master table (FR-007 → research.md D2).
- **Import Report / diagnostics** *(operational, transient)*: source identifier, start/end, and the processed / imported / updated / skipped / quarantined / failed counts. Not part of the public API.
- **Yahoo exchange→MIC mapping** and **suffix-override table** *(infrastructure reference configuration, committed)*: translate provider `Exchange` + symbol suffix to canonical `MIC` + `currency` + `supported_for_fd002`. Governed as infrastructure config; **never exposed through the public API**.
- **Reference source file(s)** *(import input)*: a committed curated ISO 10383 – compatible `markets.csv` and a committed curated Yahoo-shape `instruments.sample.csv` (source, not canonical), packaged as classpath resources (FR-011, FR-012 → research.md D10; human decision 2026-09-03).

*No new Portfolio/Position entities. FD001 identity and the Information Model relationships are
unchanged.*

---

### Traceability to Enabler Verification Criteria

| Enabler VC | Description | Covered by |
|---|---|---|
| VC-001 | Local Market Catalog persisted in PostgreSQL | US1 (AS2, AS4); FR-005, FR-029 |
| VC-002 | Markets support ISO 10383 MIC | US1 (AS4), US2 (AS1); FR-005, FR-010, FR-015 |
| VC-003 | Local Financial Instrument Catalog persisted in PostgreSQL | US1 (AS2, AS4); FR-005, FR-029 |
| VC-004 | EUR and USD supported | US2 (AS2); FR-007, FR-017 |
| VC-005 | Normalized `ticker + market` identity | US1 (AS4), US2 (AS1–AS2); FR-006, FR-014, FR-016 |
| VC-006 | Search runs against local data, no external provider at request time | US3 (AS1, AS3, AS4); FR-024, FR-025 |
| VC-007 | Search by ticker | US3 (AS1); FR-024 |
| VC-008 | Search by name | US3 (AS2); FR-024 |
| VC-009 | Results provide valid ticker + Market + Currency | US3 (AS1, AS3); FR-025 |
| VC-010 | No provider-specific payload types in domain / public contracts | US2 (AS6, AS7); FR-002, FR-010, FR-013, FR-017, FR-028 |
| VC-011 | Repeatable import creates no duplicates | US5 (AS1); FR-006, FR-020 |
| VC-012 | Invalid/failed import does not corrupt the valid catalog | US5 (AS2, AS4); FR-009, FR-021 |
| VC-013 | Spring Data JPA persistence; domain JPA-free | US1 (AS3); FR-002, FR-029 |
| VC-014 | Schema managed through Flyway | US1 (AS2); FR-029 |
| VC-015 | Persistence/integration tests on Testcontainers PostgreSQL | US1 (AS4), US5; FR-034 |
| VC-016 | Deterministic fixtures; tests + FD002 E2E run without Internet | US4 (AS1–AS3); FR-012, FR-033, FR-035 |
| VC-017 | ArchUnit verifies ADR-003 rules | US1 (AS1, AS5); FR-004 |
| VC-018 | Works with the existing containerized platform | US4 (AS2, AS4); FR-030 |
| VC-019 | No Portfolio business-semantics change | US1–US5 (all); FR-037 |
| VC-020 | Capability sufficient for FD002 controlled selection | US3 + US4 (AS3); FR-024–FR-028, FR-035 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a single reference-data import over the committed fixtures, 100 % of catalog rows have a valid `ticker + market(MIC) + currency` combination, currency ∈ {EUR, USD}, and a resolvable ISO 10383 MIC — verified by an integration test.
- **SC-002**: Running the identical import a second (and third) time produces **zero** additional Market or Financial Instrument listing rows and **zero** duplicates — verified by an idempotency integration test.
- **SC-003**: For every source row that is unsupported, ambiguous, has a suffix/mapping mismatch, an unresolvable MIC, an empty ticker, an unsupported currency, or an unexpected identity conflict, the row does **not** appear in the catalog and **is** counted in exactly one skip/quarantine diagnostic category — verified by unit + integration tests covering each category, including `SAN.MC/MCE`, `AAPL/NMS`, `ADS.DE/FRA`, a GBP `LSE` row, and an `ENX/NX` row.
- **SC-004**: **Zero** occurrences of a generic "remove everything after the last period" ticker rule in the codebase; suffix stripping is exercised only through the mapping files — verified by unit tests and code review.
- **SC-005**: **Zero** provider-specific source types (Yahoo record, CSV library row, exchange-provider payload) appear in `…financialinstrument.domain..` or in any public REST DTO — verified by ArchUnit and by inspection of the OpenAPI contract.
- **SC-006**: A case-insensitive search for a known active ticker and for a partial known instrument name each return the expected listing(s); an inactive listing and a non-EUR/USD listing are absent from default results — verified by integration tests, 100 % of the defined search scenarios.
- **SC-007**: The instrument-search request path performs **zero** outbound network calls to an external reference-data provider — verified by test (no provider adapter on the request path) and by design review.
- **SC-008**: `./mvnw verify` passes with the module's unit, Testcontainers integration, contract (if an endpoint is exposed), and ArchUnit tests all green, with no Internet dependency beyond container-image pulls; the existing `portfolio`-module suites and coverage gate (≥ 90 % line **and** branch) remain green.
- **SC-009**: From a clean checkout with no Internet access, the containerized platform starts with a populated catalog through a single documented step, and `./e2e.sh` (FD002 journey, once FD002 is implemented) has the reference data it needs — verified by the offline bootstrap demonstration and, ultimately, by FD002's E2E.
- **SC-010**: A deliberately corrupted fixture record causes the import to fail safely (per the planned batch strategy) with the previously valid catalog **fully intact** and the diagnostics naming the offending record — verified by a failure-safety integration test.
- **SC-011**: The change set contains **no** modification to Portfolio/Position business logic, **no** change to the FD001 `ticker + market` identity, **no** new deployable/messaging/scheduler/search-engine/persistence technology, and **no** edit to a human-governed `product/` document — verified by `git diff` review (VC-019).
- **SC-012**: 100 % of the enabler's verification criteria (VC-001 … VC-020) have associated executable or inspectable evidence.

---

## Assumptions

- **A1 — Enabler approved 2026-09-03**: the human approved the enabler and its two data decisions; `product/…/EN004-….md` is `Status: Approved` with §34 signed (`research.md` §"Decision taken (human)").
- **A2 — Same deployable**: EN004 is a new module inside the existing `core-service` (ADR-001/ADR-003 modular monolith). No new ADR is required for a module + a Flyway schema within the current topology.
- **A3 — Source CSV provisioning (decided 2026-09-03)**: the initial instrument dataset is a **curated deterministic `instruments.sample.csv`** committed under `src/main/resources/reference-data/`. The full upstream `Yahoo-Finance-Ticker-Symbols.csv` is **not** committed or bootstrap-fetched under EN004; the format-driven ingestion code handles it later if provisioned. All tests and the FD002 E2E rely only on committed deterministic data.
- **A4 — Market source** *(resolved — research.md D10)*: a committed curated ISO 10383 – compatible `markets.csv` covering the MICs used; the full ISO file is not redistributed.
- **A5 — Currency modeling** *(resolved — research.md D2)*: EUR/USD modeled as a domain enum `SupportedCurrency`, not a master table.
- **A6 — Casing** *(resolved — research.md D2)*: canonical `ticker` and `mic` stored uppercase + trimmed; search normalizes user input the same way.
- **A7 — Name search** *(resolved — research.md D5)*: PostgreSQL `ILIKE '%q%'` on `name` + exact `upper(ticker)` match, one `@Query`. No search engine. `pg_trgm` index only if the committed dataset needs it.
- **A8 — Batch strategy** *(resolved — research.md D7)*: one transaction per import run (all-or-nothing on a hard failure). No staging table at this dataset size.
- **A9 — Delisting**: EN004 defines **no** automatic inactivation/delisting rule; an import that omits a previously seen entry leaves it unchanged (EN004 §15, §33.9). Any inactivation rule is added deliberately in planning.
- **A10 — Provenance**: minimal provenance (`source`, `sourceReference?`, `lastImportedAt?`) stored on the reference rows themselves (not a separate history table) for the first version (EN004 §33.10).
- **A11 — REST surface** *(clarified 2026-09-03 → FR-027)*: EN004 exposes exactly the FD002-critical **instrument search** endpoint contract-first (`GET /api/financial-instruments?query=…`, OpenAPI 3.0.3, RFC 9457 errors, contract test); a standalone Market-listing endpoint is out of scope for EN004.
- **A12 — Instrument types** *(clarified 2026-09-03 → FR-040)*: initial catalog = **equities + ETFs**; `instrumentType` stored as optional descriptive metadata, not an FD002 filter; crypto/derivatives out of scope.
- **A13 — Import entry point** *(resolved — research.md D11)*: a flag-guarded Spring `ApplicationRunner` (`app.reference-data.import-on-startup`), not a scheduled job or CLI. Idempotent and safe to re-run; failure logs and does not crash the app.
- **A14 — OpenAPI version**: any new contract is authored in **OpenAPI 3.0.3** to match the `swagger-request-validator` 2.44.x used by the existing contract tests (established by FD001).
- **A15 — Deterministic fixture identifiers** *(resolved — research.md D13)*: `ListingId` = deterministic UUID v5 over `"<TICKER>|<MIC>"`; Markets use the MIC as id.

## Dependencies

- **EN001 — Bootstrap Executable Platform**: provides `core-service`, PostgreSQL + Flyway, and the ArchUnit setup EN004 extends.
- **EN002 — Establish Containerized End-to-End Testing Foundation**: provides the containerized platform, `e2e.sh`, and the offline-E2E requirement EN004 must satisfy (VC-016, VC-018).
- **EN003 — Align Spring Backend with Standard Architecture**: establishes the ADR-003 module layout, Spring Data JPA pattern, Maven wrapper, and `StandardArchitectureRulesTest` that EN004's new module must follow and extend.
- **FD001 — Create Investment Portfolio**: defines the `ticker + market` Position identity EN004 must stay aligned with; FD001 Positions may reference (later-inactive) listings.
- **FD002 — Select Financial Instrument from Catalog**: the consuming Feature Definition. EN004 must be sufficient for FD002 (VC-020); FD002 owns the frontend, the Add-Position integration, and the mandatory browser E2E.
- **ADR-001 / ADR-003**: governing architecture — unchanged; EN004 implements within them.
- **Governance**: `product/definition/global/` (domains, glossary, information model — Financial Instruments domain, ISO 10383 / ISO 6166 / ISO 4217), `product/architecture/{architecture,architecture-rules,technology-policy}.md`, `product/engineering/{development-rules,testing-strategy,definition-of-done}.md`, `.specify/memory/constitution.md`.
- **Reference mapping files**: `yahoo-exchange-to-mic-mapping.csv`, `yahoo-exchange-suffix-overrides.csv` — must be relocated into the implementation as infrastructure config during planning/implementation (they currently live under `product/definition/enablers/EN004-.../reference-data/`).

## Out of Scope

Carried from EN004 §3 "Out of Scope" plus derived boundaries:

- Live market prices, historical prices, FX rates, Portfolio valuation, market news, AI enrichment.
- Full global financial-instrument coverage; guaranteed real-time synchronization; scheduled/automatic refresh; provider-API synchronization; incremental updates; delisting detection; provenance history; provider failover.
- User-defined instruments or markets; crypto assets; derivatives beyond what an approved source explicitly includes; production-grade exchange connectivity; paid data-provider integration; automatic trading.
- Portfolio business-rule changes; changing FD001 Position identity; any change to Portfolio/Position semantics.
- Direct frontend access to external reference-data providers.
- The **FD002 frontend, the Add-Position UI integration, and the FD002 browser E2E test** — owned by FD002 (EN004 only guarantees the backend capability and the offline catalog data).
- A new deployable service, service extraction, messaging/Kafka, a scheduler, a new persistence technology, a dedicated search engine, or a Java/Spring major-version upgrade.
- Editing or reinterpreting human-governed architecture / product documents.
