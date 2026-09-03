# EN004 — Establish Financial Instrument Reference Data

> **Status:** Approved  
> **Enabler ID:** EN004  
> **Enabler Name:** Establish Financial Instrument Reference Data  
> **Supports:** FD002 — Select Financial Instrument from Catalog  
> **Last Updated:** 2026-09-03  

---

# 1. Purpose

Establish the reusable technical capability required by FD002 to provide controlled Financial Instrument, Market, and Currency reference data.

The platform must expose normalized local reference data to business and frontend capabilities without making the user-facing flow depend directly on an external provider at runtime.

EN004 creates the technical foundation for:

- Market reference data;
- Financial Instrument reference data;
- supported Currency reference data;
- ingestion/import of reference data;
- normalization;
- local persistence;
- querying/search;
- future synchronization with external providers.

This enabler must not introduce new Portfolio business behavior.

---

# 2. Motivation

FD002 requires the Investor to select known values instead of entering unrestricted free text for:

```text
ticker
market
currency
```

The user-facing application therefore needs an authoritative local catalog containing valid and normalized combinations such as:

```text
AAPL + XNAS + USD
SAN  + XMAD + EUR
```

The runtime user flow should remain available independently from temporary failures, latency, or rate limits of an external catalog provider.

The preferred model is therefore:

```text
External / controlled source
          ↓
Reference-data ingestion
          ↓
Normalization
          ↓
Local PostgreSQL catalog
          ↓
My-FinAI-Manager business capability
          ↓
Frontend
```

---

# 3. Scope

## In Scope

- Introduce a local Market Catalog.
- Introduce a local Financial Instrument Catalog.
- Define EUR and USD as the initial supported Currency reference values.
- Persist reference data in PostgreSQL.
- Represent Markets using ISO 10383 MIC where available.
- Support controlled loading of Market reference data.
- Support controlled loading of Financial Instrument reference data.
- Normalize external/source-specific data before persistence.
- Preserve source/provider independence in the canonical model.
- Provide a reusable reference-data ingestion mechanism.
- Provide a deterministic initial dataset suitable for local development and automated testing.
- Provide backend querying/search capabilities required by FD002.
- Support search by ticker.
- Support search by instrument name.
- Support filtering to active instruments/listings.
- Support filtering to initially supported currencies: EUR and USD.
- Store optional ISIN when available.
- Store optional external/reference identifiers when available.
- Record enough source metadata to understand where reference data originated when practical.
- Keep ingestion/synchronization concerns separate from Portfolio business logic.
- Use the standard Spring architecture defined by ADR-003.
- Use Spring Data JPA for relational persistence.
- Use Maven for the Spring backend.
- Use Flyway for schema evolution.
- Provide Testcontainers-based integration tests.
- Provide fixtures that make FD002 E2E execution deterministic.

## Out of Scope

- Live market prices.
- Historical prices.
- FX rates.
- Portfolio valuation.
- Market news.
- AI enrichment.
- Full global financial-instrument coverage.
- Guaranteed real-time synchronization.
- User-defined instruments.
- User-defined markets.
- Crypto assets.
- Derivatives unless explicitly included by an approved source.
- Production-grade exchange connectivity.
- Paid data-provider integration.
- Automatic trading.
- Portfolio business-rule changes.
- Changing FD001 Position identity.
- Direct frontend access to external reference-data providers.

---

# 4. Architectural Principle

The user-facing flow must consume the local My-FinAI-Manager catalog.

Conceptually:

```text
Frontend
   ↓
REST API
   ↓
Financial Instrument business capability
   ↓
domain port
   ↓
local persistence adapter
   ↓
PostgreSQL
```

Reference data enters the platform through a separate ingestion path:

```text
Reference Source
    ↓
infrastructure adapter
    ↓
normalization
    ↓
business ingestion operation
    ↓
domain port
    ↓
PostgreSQL
```

An external provider must not become the canonical domain model.

---

# 5. Functional Module

The preferred functional module is:

```text
financialinstrument
```

Following ADR-003:

```text
com.myfinaimanager.core.financialinstrument
│
├── domain/
│   ├── model/
│   ├── ports/
│   └── exceptions/
│
├── business/
│
└── infrastructure/
    ├── api/
    │   ├── rest/
    │   │   └── dto/
    │   └── mapper/
    ├── persistence/
    │   ├── entity/
    │   ├── repository/
    │   └── mapper/
    └── reference/
        ├── market/
        └── instrument/
```

Exact adapter package naming may evolve during planning provided it remains compliant with ADR-003.

---

# 6. Domain Model

The domain should introduce provider-neutral reference concepts.

## Market

Conceptually:

```text
Market
- mic
- name
- country?
- active
```

MIC should use ISO 10383 where available.

## Currency

Initial supported values:

```text
EUR
USD
```

Currency uses ISO 4217 representation.

Because the initial set is intentionally small, Currency may be modeled as an enum/value object instead of a database-managed master if that remains the simplest implementation.

## Financial Instrument Listing

Conceptually:

```text
FinancialInstrument
- id
- name
- ticker
- market
- currency
- isin?
- externalReference?
- active
```

The model represents a selectable listing, not a provider-specific payload.

A single economic instrument may have multiple listings.

---

# 7. Identity and Uniqueness

For the initial catalog, a Financial Instrument listing must be uniquely identifiable by:

```text
ticker + market
```

This remains aligned with FD001 Position identity.

The database must prevent duplicate active/reference rows for the same normalized identity where appropriate.

ISIN is additional reference information and is not required to replace `ticker + market` as the Portfolio Position identity in EN004.

---

# 8. Market Reference Data

Markets should be loaded from an authoritative ISO 10383-compatible source.

The ingestion capability must support a controlled file-based import for Market reference data.

Preferred initial format:

```text
CSV
```

Other formats may be supported later.

The source file must be normalized into the local Market model.

Source-specific column names must not propagate into domain or API contracts.

---

# 9. Financial Instrument Reference Data

Financial Instrument data must support controlled import into the local catalog.

The initial mechanism should favor reproducible data files over mandatory live provider calls.

Possible source categories include:

- official exchange listing files;
- curated project fixtures;
- public listing datasets;
- future external catalog APIs.

The specific production-grade provider is not mandated by EN004.

The first implementation must provide enough deterministic data to support FD002 development and testing.

---


# 9A. Initial Yahoo CSV Source and Normalization

For the initial implementation, the supplied `Yahoo-Finance-Ticker-Symbols.csv` is approved as an initial Financial Instrument **import source**, not as canonical domain data.

The adapter must use:

```text
reference-data/yahoo-exchange-to-mic-mapping.csv
reference-data/yahoo-exchange-suffix-overrides.csv
```

to translate Yahoo-specific exchange/symbol conventions into canonical reference data.

Only mappings explicitly marked as supported for FD002 and resolving to EUR or USD may enter the initial selectable catalog.

Unsupported, legacy, ambiguous, or unresolved rows must be skipped or quarantined with diagnostics.

## Canonical Market

The canonical Market persisted by My-FinAI-Manager is an ISO 10383 MIC.

Prefer a specific listing/segment MIC over only an operating MIC when the listing can be identified reliably.

For Spanish Yahoo `.MC` / `MCE` rows:

```text
canonical market = XMAD
operating MIC    = BMEX (optional reference metadata)
```

## Provider Symbol and Canonical Ticker

Both values must be distinguished:

```text
providerSymbol   raw Yahoo symbol, e.g. SAN.MC
ticker           normalized domain ticker, e.g. SAN
```

The provider-specific symbol must not become the canonical ticker.

## Mapping-Driven Suffix Removal

Suffix removal must be mapping-driven.

The implementation must **not** remove all content after the last period generically.

A suffix is stripped only when:

- the `(Yahoo Exchange, Yahoo suffix)` has an explicit override; or
- the exchange mapping declares that suffix as its expected Yahoo suffix.

If the exchange is configured without a suffix, the symbol is left intact.

If symbol suffix and mapping disagree, the row is quarantined rather than guessed.

Examples:

```text
SAN.MC + MCE → SAN + XMAD + EUR
AAPL + NMS   → AAPL + XNAS + USD
ADS.DE + FRA → ADS + XETR + EUR   (explicit suffix override)
```

The raw Yahoo symbol is always retained as source metadata.


# 10. Initial Dataset

The project must include or generate a deterministic initial dataset containing at least a small representative set of supported instruments.

The dataset should include both EUR and USD examples.

At minimum, test fixtures should include known examples equivalent to:

```text
AAPL · XNAS · USD
```

and at least one EUR-listed instrument.

The exact instruments used for automated tests are implementation data and must not become product requirements.

Test fixtures must not depend on live Internet access.

---

# 11. Runtime Independence

FD002 runtime behavior must not depend on the availability of an external reference-data provider.

Once reference data has been loaded, users must be able to search/select supported catalog entries using local PostgreSQL data.

An external source failure during refresh must not corrupt the previously valid local catalog.

---

# 12. Ingestion Model

The ingestion flow should be explicit:

```text
read source
    ↓
parse
    ↓
validate source record
    ↓
normalize
    ↓
map to canonical reference model
    ↓
upsert/update local catalog
    ↓
report result
```

Invalid source records must not silently become valid catalog entries.

Import failures must provide actionable diagnostics.

---

# 13. Idempotency

Reference-data loading must be safely repeatable.

Running the same import more than once must not create duplicate Markets or Financial Instrument listings.

The ingestion mechanism should support deterministic insert/update behavior.

---

# 14. Update Strategy

The initial implementation must support manual/on-demand reference-data loading.

Automatic periodic synchronization is optional and not required by EN004.

A future enabler may introduce scheduled refresh, provider API synchronization, incremental updates, delisting detection, provenance history, or provider failover.

EN004 should not introduce scheduling infrastructure without a concrete requirement.

---

# 15. Active / Inactive State

Markets and Financial Instrument listings must support active/inactive state.

Inactive Financial Instruments must remain representable because historical Portfolio Positions may reference them.

FD002 must only offer active/selectable entries for new Positions.

The ingestion process must not delete historical reference data merely because a source no longer returns it unless an explicit lifecycle rule is defined.

---

# 16. Persistence

Reference data must use PostgreSQL.

Spring Data JPA is the standard persistence abstraction.

Expected infrastructure structure:

```text
financialinstrument/
└── infrastructure/
    └── persistence/
        ├── entity/
        │   ├── MarketEntity.java
        │   └── FinancialInstrumentEntity.java
        ├── repository/
        │   ├── MarketJpaRepository.java
        │   └── FinancialInstrumentJpaRepository.java
        ├── mapper/
        └── ...
```

Domain models must not contain JPA annotations.

Flyway remains responsible for schema migration.

Hibernate schema auto-creation must not replace Flyway.

---

# 17. Ports

The `domain` package should expose ports required by business operations.

Conceptually:

```text
MarketCatalogPort
FinancialInstrumentCatalogPort
ReferenceDataImportPort
```

Exact naming is decided during specification/planning.

Ports must use provider-neutral domain types.

They must not expose JPA entities, CSV library records, OpenFIGI models, exchange-provider payloads, or HTTP DTOs.

---

# 18. Business Operations

Reusable business operations may include:

```text
searchFinancialInstruments
loadMarkets
loadFinancialInstruments
```

The business layer owns orchestration and validation.

Infrastructure adapters own file access, CSV parsing, provider-specific formats, HTTP provider access if introduced later, and JPA persistence details.

---

# 19. Search Capability

The backend must support the search needs of FD002.

At minimum:

```text
search by ticker
search by instrument name
```

Search should return only entries compatible with FD002 unless explicitly requested otherwise.

For the initial version this means:

```text
active = true
currency IN (EUR, USD)
```

Search behavior should support case-insensitive user input where practical.

The implementation must remain suitable for small-to-moderate reference datasets without introducing a dedicated search engine.

PostgreSQL should be used before considering OpenSearch/Elasticsearch.

---

# 20. API Support

EN004 may expose the backend reference-data endpoints required by FD002.

The exact external API contract belongs to the corresponding formal specification/plan.

Conceptually:

```text
GET /financial-instruments?query=AAPL
GET /markets
```

or an equivalent business-oriented contract.

REST APIs must follow the project's OpenAPI policy.

Provider-specific source fields must not leak into public DTOs.

---

# 21. Source Provenance

Where practical, reference data should retain minimal provenance information such as:

```text
source
sourceReference?
lastImportedAt?
```

This metadata is intended for operational traceability.

It must not force provider-specific concepts into the canonical business model.

---

# 22. Failure Handling

Reference-data ingestion must fail safely.

A malformed or unavailable source must not:

- delete the existing valid catalog;
- partially corrupt a batch without a deliberate consistency strategy;
- create invalid ticker/market/currency combinations.

The import transaction/batch strategy must be defined during planning.

---

# 23. Security

Reference-data imports must not require secrets when using local/public files.

If a future external API requires credentials, credentials must remain outside source control, provider secrets belong in infrastructure configuration, and secrets must not enter domain models or logs.

---

# 24. Observability

Import operations should provide useful structured diagnostics.

At minimum:

- source identifier;
- import start/end;
- number of records processed;
- number inserted;
- number updated;
- number rejected;
- failures.

Sensitive credentials or provider tokens must not be logged.

---

# 25. Test Data Strategy

Automated tests must use deterministic local fixtures.

Integration tests must not depend on Internet availability, third-party rate limits, external API credentials, or changing live provider responses.

Provider adapters, if later introduced, may be tested separately using mocks/stubs or recorded deterministic payloads according to the project's testing policy.

---

# 26. Testing Requirements

## Unit

- source-record normalization;
- validation;
- identifier normalization;
- supported currency rules;
- active/inactive filtering.

## Integration

Using PostgreSQL Testcontainers:

- Market persistence;
- Financial Instrument persistence;
- unique `ticker + market`;
- repeated import/idempotency;
- search by ticker;
- search by name;
- EUR/USD filtering;
- active/inactive behavior.

## Contract

- OpenAPI behavior for reference-data search endpoints required by FD002.

## Architecture

ArchUnit must preserve ADR-003:

```text
domain       -X-> infrastructure
business     -X-> infrastructure
```

JPA/provider/file parsing types must remain inside infrastructure.

## E2E Support

EN004 must provide deterministic catalog data that allows FD002's Playwright E2E test to run without external Internet access.

---

# 27. Containerization

EN004 must preserve the containerized runtime established by EN002.

Reference-data persistence uses the existing PostgreSQL container.

Reference-data bootstrap/import must work in the containerized platform.

The implementation must not require a developer-local PostgreSQL installation.

If files are required at runtime, their packaging/mounting mechanism must be explicit and reproducible.

---

# 28. Build and Architecture Standards

EN004 must follow:

- ADR-003 — Standard Spring Backend Architecture;
- Maven for the Spring backend;
- Spring Data JPA;
- Flyway;
- Testcontainers;
- ArchUnit;
- REST + OpenAPI where endpoints are exposed;
- existing containerized platform lifecycle.

No Gradle-based implementation should be introduced.

---

# 29. Expected Repository Impact

Conceptually:

```text
implementation/platform/backend/core-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/.../financialinstrument/
    │   │   ├── domain/
    │   │   ├── business/
    │   │   └── infrastructure/
    │   └── resources/
    │       ├── db/migration/
    │       └── reference-data/
    └── test/
        ├── java/
        └── resources/
            └── reference-data/
```

Exact paths must comply with the current repository structure.

---

# 30. Verification Criteria

## VC-001 — Local Market Catalog

A local Market Catalog exists and is persisted in PostgreSQL.

## VC-002 — MIC

Markets support ISO 10383 MIC representation.

## VC-003 — Local Instrument Catalog

A local Financial Instrument Catalog exists and is persisted in PostgreSQL.

## VC-004 — EUR/USD

The catalog supports EUR and USD as the initial currencies required by FD002.

## VC-005 — Normalized Identity

Financial Instrument listings are represented through normalized `ticker + market` identity.

## VC-006 — Local Runtime Search

Financial Instrument search executes against local platform data and does not require an external provider at request time.

## VC-007 — Search by Ticker

Known active instruments can be found by ticker.

## VC-008 — Search by Name

Known active instruments can be found by instrument name.

## VC-009 — Valid Listing Data

Search results provide a valid combination of ticker, Market, and Currency.

## VC-010 — Source Independence

Domain and public contracts contain no provider-specific source payload types.

## VC-011 — Repeatable Import

Executing the same reference-data import repeatedly does not create duplicate catalog entries.

## VC-012 — Failure Safety

An invalid or failed import does not corrupt previously valid catalog data.

## VC-013 — Spring Data JPA

Reference-data persistence uses Spring Data JPA and domain models remain JPA-free.

## VC-014 — Flyway

Reference-data database schema is managed through Flyway.

## VC-015 — Testcontainers

Applicable persistence/integration tests execute against PostgreSQL using Testcontainers.

## VC-016 — Deterministic Fixtures

Automated tests and FD002 E2E execution can use deterministic reference data without Internet access.

## VC-017 — Architecture Compliance

ArchUnit verifies relevant ADR-003 dependency/package rules.

## VC-018 — Containerized Runtime

Reference-data behavior works with the existing containerized platform.

## VC-019 — No Portfolio Behavior Change

EN004 does not change Portfolio business semantics.

## VC-020 — FD002 Ready

The resulting technical capability is sufficient for FD002 to implement controlled Financial Instrument selection.

---

# 31. Relationship with FD002

The responsibilities are deliberately separated.

```text
FD002
  WHAT:
  Investor selects valid instrument/listing
  instead of entering arbitrary values.

EN004
  HOW:
  Platform obtains, normalizes, persists,
  and serves reference data.
```

FD002 must not know whether catalog data originated from ISO files, exchange listing files, OpenFIGI, or another provider.

EN004 ensures that this distinction remains internal to the platform.

---

# 32. Recommended Initial Data Strategy

For the first iteration:

```text
Markets
    → controlled ISO 10383-compatible file import

Currencies
    → EUR / USD defined locally

Financial Instruments
    → deterministic local/imported dataset sufficient
      for supported initial markets and FD002 tests
```

A live external provider is not required for EN004 completion.

The architecture must, however, allow a future provider adapter to be introduced without changing FD002's product contract.

---

# 33. Open Technical Decisions

The following should be resolved during specification/planning:

1. Exact Market source file and redistribution/storage constraints.
2. Exact initial Financial Instrument dataset/source.
3. Whether initial reference files are committed to the repository or downloaded by a controlled bootstrap command.
4. Exact database schema for Market and Financial Instrument.
5. Whether Currency is persisted or represented as a domain enum/value object.
6. Exact normalization rules for ticker and MIC casing.
7. Search matching strategy for instrument names.
8. Batch transaction strategy for imports.
9. How existing entries are marked inactive when a newer import omits them.
10. Whether import provenance is stored in the same tables or separate metadata.
11. Whether the first version exposes Market listing independently or only through instrument search.
12. Exact command/entry point used to execute reference-data import.

These are technical decisions and must not change FD002 business behavior.

---

# 34. Human Approval

Before formal specification:

- [X] Purpose is correct.
- [X] Local reference-data catalog strategy is approved.
- [X] Runtime independence from external APIs is approved.
- [X] ISO 10383 MIC-based Market representation is approved.
- [X] EUR/USD initial Currency scope is approved.
- [X] Local PostgreSQL persistence is approved.
- [X] Spring Data JPA is approved.
- [X] Provider-neutral normalization is approved.
- [X] File/import-first initial strategy is approved.
- [X] Live API integration is not required for EN004 completion.
- [X] Deterministic E2E/test fixtures are approved.
- [X] No Portfolio business behavior is introduced.

**Approved by:** jaruiz  
**Date:** 2026-09-03  
**Status:** Approved

### Reference-data provisioning decisions (2026-09-03)

- The initial Financial Instrument import source is a **curated deterministic `instruments.sample.csv`**
  committed under the implementation `reference-data/` resources. The full
  `Yahoo-Finance-Ticker-Symbols.csv` (§35.1) is **not** committed or bootstrap-fetched by EN004;
  the ingestion adapter is format-driven so the full file can be added later without a contract change.
- The `yahoo-exchange-to-mic-mapping.csv` / `yahoo-exchange-suffix-overrides.csv` files are **copied**
  into the implementation as infrastructure configuration; the copies here are **retained** as the
  enabler's reference attachment.

---

# 35. Resolved Initial Reference-Data Decisions

The following EN004 decisions are approved by the current design direction:

1. `reference-data/Yahoo-Finance-Ticker-Symbols.csv` is the initial Financial Instrument import source.
2. Yahoo data is treated as provider/source data, never as the canonical domain model.
3. Market is normalized to ISO 10383 MIC.
4. The initial selectable universe is limited to mappings approved for FD002 with EUR or USD currency.
5. Yahoo raw symbols are retained as `providerSymbol`.
6. Canonical `ticker` removes provider suffixes only through explicit mapping rules.
7. Generic "strip everything after the final dot" normalization is forbidden.
8. `MCE/.MC` is normalized to the specific Spanish listing MIC `XMAD`; `BMEX` may be retained as operating-MIC metadata.
9. Ambiguous, unsupported, and mapping-mismatch records are quarantined/skipped with diagnostics rather than guessed.
10. Runtime FD002 searches continue to use PostgreSQL and never depend on Yahoo at request time.

The mapping files are infrastructure configuration/reference artifacts and may evolve independently of the FD002 public contract.
