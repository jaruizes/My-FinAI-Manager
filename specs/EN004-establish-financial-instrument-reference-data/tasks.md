---
description: "Task list for EN004 — Establish Financial Instrument Reference Data"
---

# Tasks: Establish Financial Instrument Reference Data (EN004)

**Input**: Design documents from `/specs/EN004-establish-financial-instrument-reference-data/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md` (D1–D17 + OD-EN004-1…20), `data-model.md`,
`contracts/{catalog-ports.md, reference-mapping.md, openapi/financial-instruments.search.yaml}`,
`quickstart.md`

**Governance**: `.specify/memory/constitution.md` v1.0.0 (I–VIII); **ADR-003** (governing, amended
2026-09-02 for `infrastructure.api.rest.mapper`); ADR-001 (single `core-service` — unchanged);
`product/architecture/{architecture,architecture-rules,technology-policy}.md`;
`product/engineering/{development-rules,testing-strategy,definition-of-done}.md`;
`EN004-yahoo-normalization-decision.md`.

> **✅ Prerequisite gates cleared — human decisions 2026-09-03** (`research.md` §"Decision taken (human)"):
> 1. **Enabler approved** — authoritative `product/…/EN004-….md` synced: `Status: Approved`, §34
>    checklist signed (jaruiz, 2026-09-03).
> 2. **OD-EN004-3** → the curated `instruments.sample.csv` **is** the initial dataset (full Yahoo CSV
>    not committed/fetched under EN004).
> 3. **OD-EN004-20** → keep the `product/` mapping-CSV copies (do not delete).
>
> `/speckit-implement` is unblocked.

**Tests**: EN004's testing surface is mandated by the enabler (§26) and the constitution. The
**`YahooSymbolNormalizer`** is **new deterministic logic → strict TDD** (RED → GREEN → REFACTOR;
one test per worked example + per `RejectionReason` — `contracts/reference-mapping.md` §3).
Persistence, import idempotency, failure-safety and search run on **Testcontainers PostgreSQL**
(never a mocked DB). The `GET /api/financial-instruments` endpoint has a `swagger-request-validator`
contract test. The **FD002 Playwright E2E** is the downstream product proof (VC-020) — EN004
delivers the offline catalog data it needs and must not regress the existing FD001/smoke E2E.

> **How EN004 is sliced.** One capability, one vertical slice of `core-service`. **Setup** adds the
> dependency + config + empty module tree. **Foundational** adds the `V3` schema + module config.
> **US1** builds the domain model + JPA persistence + ports + ArchUnit. **US2** builds the
> provider-neutral ingestion + the TDD'd normalizer. **US3** builds the FD002-facing search + the
> one REST endpoint. **US4** makes it deterministic and containerized (fixtures + startup import).
> **US5** (P2) hardens idempotency / failure-safety / diagnostics. **No** Portfolio behavior, API,
> or schema change; **no** new deployable / messaging / scheduler / search engine.

**Path conventions** (all under `implementation/platform/backend/core-service/`)
- Module main: `src/main/java/com/myfinaimanager/core/financialinstrument/`
- Module test: `src/test/java/com/myfinaimanager/core/financialinstrument/`
- Resources: `src/main/resources/` (`db/migration/`, `application.yml`, `reference-data/`) · `src/test/resources/reference-data/`
- Shared: `pom.xml`, `src/test/java/com/myfinaimanager/core/architecture/StandardArchitectureRulesTest.java`
- Contract: `implementation/platform/contracts/openapi/openapi.yaml`

**Build command**: `cd implementation/platform/backend/core-service && export JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open && export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" && export TESTCONTAINERS_RYUK_DISABLED=true && ./mvnw -B clean verify`

---

## Phase 1: Setup

**Purpose**: additive dependency + config + empty module tree; build stays green.

- [X] T001 Update `pom.xml` — add `org.apache.commons:commons-csv` (explicit `<version>`, current 1.x); add JaCoCo `<excludes>`: `com/myfinaimanager/core/financialinstrument/infrastructure/config/**` and `com/myfinaimanager/core/financialinstrument/infrastructure/persistence/entity/**` (keep the existing `portfolio` excludes). (research.md D12, D15; OD-EN004-15, OD-EN004-19)
- [X] T002 [P] Update `src/main/resources/application.yml` — add the `app.reference-data` block (`import-on-startup: true`, `markets-file`, `instruments-file`, `exchange-mic-mapping-file`, `suffix-override-file` as `classpath:reference-data/...`). No secrets; `spring.*` blocks unchanged. (research.md D16)
- [X] T003 [P] Create the empty module package tree under `src/main/java/com/myfinaimanager/core/financialinstrument/`: `domain/{model,ports,exceptions}`, `business`, `business/normalization`, `infrastructure/{api/rest/dto, api/rest/mapper, persistence/{entity,repository,mapper}, reference/{csv,market,instrument,mapping}, config}` — each with a `package-info.java` stating its ADR-003 role and the inward-dependency rule. (research.md D1; OD-EN004-1)

**Checkpoint**: `./mvnw -B compile` green — `commons-csv` on the classpath, empty packages, nothing wired.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the schema and module config exist so US1+ have a home. **No user story can start until this is done.**

- [X] T004 Create `src/main/resources/db/migration/V3__financial_instrument.sql` — `market` + `financial_instrument` tables exactly per research.md D3 (natural-key `mic` PK; `financial_instrument.id UUID` PK; `market_mic CHAR(4) NOT NULL REFERENCES market(mic)`; `UNIQUE (ticker, market_mic)`; CHECKs for `mic` shape, `currency IN ('EUR','USD')`, `isin` shape, `ticker` length, `instrument_type`; indexes `upper(ticker)` and `(active, currency)`). Do **not** touch `V1`/`V2`. (data-model.md §3; VC-014)
- [X] T005 [P] Create `infrastructure/config/ReferenceDataProperties` (`@ConfigurationProperties("app.reference-data")` record) and `infrastructure/config/FinancialInstrumentModuleConfiguration` (`@Configuration`; holds any explicit module beans). (research.md D16; OD-EN004-1)
- [X] T006 Verify `@SpringBootApplication` component scan covers `com.myfinaimanager.core.financialinstrument` (it does — scan root is `com.myfinaimanager.core`); add a one-line note to `CoreServiceApplication` javadoc listing the new module (no code change beyond the comment).

**Checkpoint**: `./mvnw -B verify` green — `V3` applies on Testcontainers PostgreSQL; existing `portfolio` suites unaffected.

---

## Phase 3: US1 — The `financialinstrument` module with a persisted catalog (Priority: P1)

**Goal**: the ADR-003 module with a provider-neutral domain model, Spring Data JPA persistence of
Markets and Financial Instrument listings, domain ports, and an ArchUnit suite that fails the build
on a boundary violation.

**Independent Test**: quickstart §A + §D — `./mvnw verify` green with the schema-integrity IT, the
value-object unit tests, a persistence round-trip IT, and the extended `StandardArchitectureRulesTest`
(non-vacuous). Covers VC-001, VC-003, VC-013, VC-014, VC-017.

### Tests for US1 (write first, must fail before implementation)

- [X] T007 [P] [US1] `src/test/java/.../financialinstrument/domain/model/ReferenceDomainModelTest.java` (TDD, write first) — **(a) value objects**: `Mic` (`^[A-Z0-9]{4}$`, upper/trim, reject `xmad`/`XMA`/`XMADX`), `Ticker` (non-blank, ≤ 20, upper/trim), `Isin` (`^[A-Z]{2}[A-Z0-9]{9}[0-9]$`, reject bad checksum-shape), `SupportedCurrency` parse EUR/USD reject others, `ListingId.deterministic("SAN","XMAD")` stable across calls + differs for a different pair + `ListingId.of(uuid)` round-trips. **(b) reference-entity `fromRaw` validation**: `Market.fromRaw(NewMarket)` — rejects a blank `name`, a bad-shape `mic`, a bad `operatingMic`; accepts a minimal valid row; defaults `active` when the raw value is absent. `FinancialInstrumentListing.fromRaw(NewListing)` — rejects a blank `name`, an empty `ticker`, a bad `mic`, a currency outside `{EUR, USD}`, a malformed `isin`; accepts a valid row with and without the optionals; `null`/blank optionals map to `Optional.empty()`; `id` = `ListingId.deterministic(ticker, mic)`. (data-model.md §1; FR-034 "validation"; TDD)
- [X] T008 [P] [US1] `src/test/java/.../financialinstrument/infrastructure/persistence/ReferenceDataSchemaIntegrityIT.java` (`@SpringBootTest` + `PostgresContainerSupport`) — `flyway_schema_history` has version `3` successful; `fin_instr_identity_uk`, `fin_instr_currency_chk`, `fin_instr_isin_chk`, the `market_mic` FK, and `market_mic_shape_chk` exist in `information_schema`; `market` / `financial_instrument` column sets are exactly what `V3` created; only the expected tables exist (Hibernate `ddl-auto: none` altered nothing). (research.md D17; VC-014)
- [X] T009 [P] [US1] `src/test/java/.../financialinstrument/infrastructure/persistence/ReferenceDataPersistenceRoundTripIT.java` (Testcontainers) — persist a `Market` (with `operatingMic`, `country`) and a `FinancialInstrumentListing` (with ISIN, `instrumentType`, `providerSymbol`) via the writer adapter; read both back via the catalog adapter and assert every field round-trips exactly incl. `CHAR` trimming and `null` ⇔ `Optional.empty()`. (data-model.md §3–§4; VC-001, VC-003)

### Implementation for US1

- [X] T010 [P] [US1] `domain/model/` value objects: `Mic`, `Ticker`, `Isin`, `SupportedCurrency` (enum), `InstrumentType` (enum EQUITY/ETF/OTHER), `InstrumentIdentity` (record `ticker+market`), `ListingId` (`deterministic(Ticker,Mic)` → UUID v5 over `"<TICKER>|<MIC>"` with a fixed namespace constant; `of(UUID)`), `Provenance` (record). (data-model.md §1; research.md D2, D13)
- [X] T011 [P] [US1] `domain/model/` reference entities: `Market` (`mic,name,country?,operatingMic?,active,provenance`; `fromRaw(NewMarket)` validating, `reconstitute(...)`), `FinancialInstrumentListing` (`id,name,identity,currency,isin?,externalReference?,instrumentType?,providerSymbol?,active,provenance`; `fromRaw(NewListing)`, `reconstitute(...)`), `NewMarket` / `NewListing` all-`String` carriers. Bodies parse + validate; no framework imports. **Makes the T007 (b) `fromRaw` validation cases green.** (data-model.md §1)
- [X] T012 [US1] `domain/exceptions/`: `ReferenceDataImportException` (carries a partial `ImportReport`), `InvalidSearchQueryException`. (data-model.md §1)
- [X] T013 [US1] `domain/ports/`: `FinancialInstrumentCatalog` (`List<FinancialInstrumentListing> search(String)`), `MarketCatalog` (`findByMic`, `findAllByMic(Set<Mic>)`), `ReferenceCatalogWriter` (`upsertMarket`, `upsertListing`, `enum UpsertResult{INSERTED,UPDATED}`). Provider-neutral types only. (contracts/catalog-ports.md §1–§3)
- [X] T014 [P] [US1] `infrastructure/persistence/entity/`: `MarketEntity` (`@Id String mic`), `FinancialInstrumentEntity` (`@Id UUID id`, plain `@Column market_mic` — **no `@ManyToOne`**), both with protected no-arg + all-args ctor, `equals`/`hashCode` on id. (data-model.md §3–§4; research.md D4)
- [X] T015 [P] [US1] `infrastructure/persistence/repository/`: `MarketJpaRepository extends JpaRepository<MarketEntity,String>` (`findAllByMicIn`); `FinancialInstrumentJpaRepository extends JpaRepository<FinancialInstrumentEntity,UUID>` with `findByTickerIgnoreCaseAndMarketMic(...)` and the search `@Query` from research.md D5 (active + EUR/USD + `upper(ticker)=upper(:q)` OR `name ilike %:q%`, ordered exact-ticker-first then `ticker asc`). Derived/JPQL only — no native SQL. (research.md D5)
- [X] T016 [US1] `infrastructure/persistence/mapper/ReferenceDataPersistenceMapper` (`@Component`) — `toEntity` / `toDomain` for both types; `toDomain` reconstitutes value objects and trims `CHAR` padding (the `PortfolioPersistenceMapper` pattern). (data-model.md §3–§4; research.md D4)
- [X] T017 [US1] `infrastructure/persistence/`: `FinancialInstrumentCatalogAdapter implements FinancialInstrumentCatalog, MarketCatalog` (`@Repository`; read-only `TransactionTemplate` for `search`); `ReferenceDataUpsertAdapter implements ReferenceCatalogWriter` (`@Repository`; `findBy… → update|insert`, `ListingId.deterministic` on insert). Preserve the contract invariants C1–C7 / W1–W6. (contracts/catalog-ports.md; research.md D5, D7)
- [X] T018 [US1] Extend `src/test/java/com/myfinaimanager/core/architecture/StandardArchitectureRulesTest.java` (research.md D14): confirm the shared `..core.(*).domain.. !→ ..business.. / ..infrastructure..` and `..business.. !→ ..infrastructure..` rules cover the new module (non-vacuous); **add** — `@Entity` classes in `..financialinstrument..` reside in `..infrastructure.persistence.entity..`; Spring Data `Repository` interfaces in `..infrastructure.persistence.repository..`; `@RestController` in `..infrastructure.api.rest..`; `*Mapper` in `..infrastructure.api.rest.mapper..` or `..infrastructure.persistence.mapper..`; classes using `org.apache.commons.csv..` reside in `..infrastructure..`; `..financialinstrument.domain..` depends on no `org.apache.commons..`.
- [X] T019 [US1] `./mvnw -B clean verify` — MUST be green: schema IT, `ReferenceDomainModelTest`, persistence round-trip IT, `StandardArchitectureRulesTest` (extended, non-vacuous), JaCoCo ≥ 90 % line+branch, existing `portfolio` suites unaffected. Record VC-001/VC-003/VC-013/VC-014/VC-017 evidence in `quickstart.md` §A/§D.

**Checkpoint**: the module, the schema, and the persistence adapters exist and are enforced; no ingestion, no API yet.

---

## Phase 4: US2 — Provider-neutral ingestion and mapping-driven normalization (Priority: P1)

**Goal**: a controlled import that reads source CSVs, normalizes each row through the committed
mapping files (canonical `ticker` / MIC / currency; `providerSymbol` retained), upserts the catalog,
and **skips or quarantines with diagnostics** every unsupported / ambiguous / mapping-mismatch row —
no generic dot-strip, no provider type in `domain` or contracts.

**Independent Test**: quickstart §B + §E — the normalizer unit suite passes every worked example +
rejection reason; `YahooCsvInstrumentSourceIT` imports the sample CSV and the `ImportReport`
counters match exactly; no `org.apache.commons.csv` / Yahoo type in `domain`. Covers VC-002, VC-004,
VC-005, VC-010; SC-003, SC-004.

### Tests for US2 (TDD — write first, must fail)

- [X] T020 [P] [US2] `src/test/java/.../financialinstrument/business/normalization/YahooSymbolNormalizerTest.java` — **RED first**, one test per row of the `contracts/reference-mapping.md` §3 matrix: `SAN.MC/MCE→SAN·XMAD·EUR` (+ `providerSymbol=SAN.MC`), `IBE.MC/MCE`, `AAPL/NMS→AAPL·XNAS·USD`, `ADS.DE/FRA→ADS·XETR·EUR` (override), `VOD.L/LSE→UNSUPPORTED_CURRENCY`, `XYZ.NX/ENX→AMBIGUOUS_EXCHANGE`, `FOO.XX/MCE→SUFFIX_MISMATCH`, `.MC/MCE→EMPTY_TICKER`, `BAR/EUX→NOT_SUPPORTED_FOR_FD002`, `WHATEVER/ZZZ→NO_MAPPING`; plus: an empty-suffix exchange (`NMS`) never truncates a symbol containing a period; a symbol is unchanged when the rule suffix is empty.
- [X] T021 [P] [US2] `src/test/java/.../financialinstrument/infrastructure/reference/mapping/MappingTablesTest.java` — load the two committed mapping CSVs; assert key rows: `MCE→XMAD/EUR/MC/supported`, `FRA→XFRA/EUR/F` + override `(FRA,DE)→XETR`, `NMS→XNAS/USD/empty-suffix`, `LSE→XLON/GBP/not-supported`, `ENX→empty` + overrides `(ENX,PA)→XPAR` and `(ENX,NX)→XEUR/empty-currency`.
- [X] T022 [P] [US2] `src/test/java/.../financialinstrument/infrastructure/reference/YahooCsvInstrumentSourceIT.java` (`@SpringBootTest` + Testcontainers) — run `ImportReferenceDataService` over `src/test/resources/reference-data/{markets.sample.csv, instruments.sample.csv}` + the mapping CSVs; assert the persisted rows (`AAPL·XNAS·USD`, `SAN·XMAD·EUR`, an ETF row) and the exact `ImportReport.counters` (`processed`, `imported`, `updated=0`, `skippedUnsupportedCurrency≥1`, `quarantinedAmbiguous≥1`, `quarantinedInvalid≥1`); assert no `provider_symbol` value leaks into any domain object beyond `FinancialInstrumentListing.providerSymbol`.

### Implementation for US2

- [X] T023 [P] [US2] Add reference fixtures: `src/test/resources/reference-data/markets.sample.csv`, `src/test/resources/reference-data/instruments.sample.csv` (≥ 15 rows: USD `NMS`/`NYQ`/`PCX`, EUR `MCE`/`FRA`(`.DE`)/`GER`/`PAR`/`AMS`/`MIL`, + deliberate rejects: a GBP `LSE` row, an `ENX`+`NX` row, a `.XX`-suffix-mismatch row, a `.MC`-only empty-ticker row, an `EUX` row), and `src/test/resources/reference-data/instruments.corrupt.csv` (one unparseable line) for US5. Also copy `yahoo-exchange-to-mic-mapping.csv` + `yahoo-exchange-suffix-overrides.csv` from `product/definition/enablers/EN004-.../reference-data/` into **both** `src/main/resources/reference-data/` and `src/test/resources/reference-data/`. (research.md D10; OD-EN004-20)
- [X] T024 [P] [US2] `business/normalization/` value types: `NormalizationResult` sealed (`Accepted(providerSymbol,ticker,mic,currency)` / `Rejected(reason,detail)`), `RejectionReason` enum (8 values), `ExchangeRule`, `SuffixOverride`, `ImportCounters` (record with the 7 counters from `EN004-yahoo-normalization-decision.md` §"Import Validation"), `ImportReport` / `Rejection` records. (contracts/catalog-ports.md §4; data-model.md §1)
- [X] T025 [US2] `business/normalization/YahooSymbolNormalizer` — implement the `EN004-yahoo-normalization-decision.md` §"Algorithm" steps 1–11 **verbatim** (contracts/reference-mapping.md §3): override lookup → exchange rule → `expected_yahoo_suffix` exact-match strip or `SUFFIX_MISMATCH` → empty-suffix never truncates → guards for `AMBIGUOUS_EXCHANGE` / `UNSUPPORTED_CURRENCY` / `EMPTY_TICKER` / `NOT_SUPPORTED_FOR_FD002` / `NO_MAPPING`. Pure, no Spring. Make T020 green; **no** generic "strip after last dot" anywhere. (SC-004)
- [X] T026 [P] [US2] `infrastructure/reference/csv/CsvReferenceFileReader` — generic classpath CSV reader over `commons-csv` (header-aware, quoted fields), returns `List<Map<String,String>>` or a typed row stream. `commons-csv` used **only** here. (research.md D12)
- [X] T027 [P] [US2] `infrastructure/reference/mapping/`: `ExchangeMicMapping` (loads `yahoo-exchange-to-mic-mapping.csv` → `Map<String,ExchangeRule>`), `SuffixOverrideTable` (loads `yahoo-exchange-suffix-overrides.csv` → `Map<(exchange,suffix),SuffixOverride>`). Loaded once (module bean). Make T021 green. (contracts/reference-mapping.md §1–§2)
- [X] T028 [P] [US2] `infrastructure/reference/market/CsvMarketSource` — reads the markets CSV → `Stream<NewMarket>`.
- [X] T029 [US2] `infrastructure/reference/instrument/YahooCsvInstrumentSource` — reads the Yahoo instrument CSV; per row calls `YahooSymbolNormalizer.normalize(rawSymbol, exchangeCode)`; `Accepted` → build `NewListing` (name from "Category Name", `instrumentType` best-effort, `providerSymbol` = raw); `Rejected` → emit a `Rejection`. Returns `(List<NewListing>, List<Rejection>)`. (data-model.md §2)
- [X] T030 [US2] `business/ImportReferenceDataService` (`@Service`) — a `TransactionTemplate` run: load Markets → `writer.upsertMarket` each (count) → load the Market MIC set → for each instrument row: if canonical MIC ∉ loaded Markets → `Rejected(MIC_UNRESOLVED)`; else `Accepted` → `writer.upsertListing` (count `imported`/`updated`); `Rejected` → increment the mapped counter (contracts/catalog-ports.md §4 table) + append `Rejection`; detect same-run `(ticker,MIC)` conflicts → `IDENTITY_CONFLICT`. Return `ImportReport`. Never persists a `Rejected` row. (research.md D6, D7)
- [X] T031 [US2] `./mvnw -B clean verify` — green: `YahooSymbolNormalizerTest` (all matrix rows), `MappingTablesTest`, `YahooCsvInstrumentSourceIT` (counters exact). Record VC-002/VC-004/VC-005/VC-010 + SC-003/SC-004 evidence in `quickstart.md` §B/§E.

**Checkpoint**: source CSVs normalize into a canonical catalog; every bad row is counted and quarantined; behavior is deterministic.

---

## Phase 5: US3 — Local catalog search for FD002 (Priority: P1)

**Goal**: case-insensitive search by ticker or name over local PostgreSQL, default filter
`active = true AND currency ∈ {EUR, USD}`, exposed as one contract-first REST endpoint carrying
only business fields.

**Independent Test**: quickstart §D + §F — `search("aapl")` → the Apple listing; partial-name
search works; inactive / non-EUR-USD rows excluded; the contract test passes against the updated
`openapi.yaml`; a blank query → 400 `application/problem+json`. Covers VC-006, VC-007, VC-008,
VC-009; VC-020 (partial).

### Tests for US3 (write first, must fail)

- [X] T032 [P] [US3] `src/test/java/.../financialinstrument/business/SearchFinancialInstrumentsServiceTest.java` (unit, mocked port) — `null`/`""`/`"   "` → `InvalidSearchQueryException`; `"  aapl  "` → delegates `"aapl"` to the port; passes results through unchanged.
- [X] T033 [P] [US3] `src/test/java/.../financialinstrument/infrastructure/persistence/FinancialInstrumentCatalogAdapterIT.java` (Testcontainers; seed rows via the writer adapter) — `search("AAPL")` and `search("aapl")` return the same active `XNAS`/`USD` row; `search("santan")` returns "Banco Santander…" by name; an `active=false` row and a (hypothetical) non-EUR/USD row are absent; exact-ticker match precedes a name-only match; `search("nomatch")` → empty list; assert the query issues no outbound HTTP.
- [X] T034 [P] [US3] `src/test/java/.../financialinstrument/infrastructure/api/rest/FinancialInstrumentSearchContractTest.java` (`@WebMvcTest(FinancialInstrumentSearchController.class)` + `@Import` the mapper + `swagger-request-validator`) — `200` array conforms to `openapi.yaml`; `200 []` for no results; `400 application/problem+json` `type:/problems/invalid-search-query` for a blank `query`; the response JSON has **no** `providerSymbol`/`source`/`operatingMic`/`instrumentType`/`externalReference` field.

### Implementation for US3

- [X] T035 [US3] **Contract-first — do this before the controller.** Merge `contracts/openapi/financial-instruments.search.yaml` into `implementation/platform/contracts/openapi/openapi.yaml`: add the `GET /api/financial-instruments` operation and the `FinancialInstrument` / `FinancialInstrumentList` schemas; **reuse** the existing `Problem` schema; keep the file **OpenAPI 3.0.3**; update `info.description`. The T034 contract test validates against this. (research.md D9; constitution VIII; VC-014)
- [X] T036 [US3] `business/SearchFinancialInstrumentsService` (`@Service`) — trim `query`; blank → `InvalidSearchQueryException`; else `catalog.search(trimmed)`. (contracts/catalog-ports.md §4)
- [X] T037 [P] [US3] `infrastructure/api/rest/dto/FinancialInstrumentResponse` — record `{ id, name, ticker, market, currency, active, isin }` (business fields only, matching the T035 schema). (contracts/openapi/financial-instruments.search.yaml)
- [X] T038 [P] [US3] `infrastructure/api/rest/mapper/FinancialInstrumentResponseMapper` (`@Component`) — `FinancialInstrumentListing` → `FinancialInstrumentResponse` (MIC string for `market`; `currency.name()`; `isin` `null` when absent). Drops all provider/provenance fields.
- [X] T039 [US3] `infrastructure/api/rest/FinancialInstrumentSearchController` — `@GetMapping("/api/financial-instruments")` `?query=` → `200` `List<FinancialInstrumentResponse>`; `FinancialInstrumentExceptionHandler` (`@RestControllerAdvice`) maps `InvalidSearchQueryException` → `ProblemDetail` `type=/problems/invalid-search-query`, status 400, `instance=/api/financial-instruments`. (research.md D9)
- [X] T040 [US3] `./mvnw -B clean verify` — green: search-service unit, catalog-search IT, contract test. `curl` sanity not required here (US4 §F). Record VC-006/VC-007/VC-008/VC-009 evidence in `quickstart.md` §D.

**Checkpoint**: FD002 can search the catalog through `GET /api/financial-instruments`; the contract is guarded.

---

## Phase 6: US4 — Deterministic, containerized, offline-capable reference data (Priority: P1)

**Goal**: committed deterministic fixtures (incl. `AAPL·XNAS·USD` + EUR examples), a flag-guarded
startup import that populates the catalog inside the existing container, and a green offline run of
the platform + the existing E2E.

**Independent Test**: quickstart §F — `BUILD=1 ./start.sh` → healthy platform, import summary in
the log, `curl .../api/financial-instruments?query=AAPL` → the Apple listing; `./stop.sh` clean;
`./e2e.sh` (FD001 + smoke) still green. Covers VC-016, VC-018, VC-020.

### Implementation for US4

- [X] T041 [P] [US4] `src/main/resources/reference-data/markets.csv` — curated ISO 10383 – compatible subset covering every MIC used by `instruments.sample.csv` and FD002 (`XNAS`, `XNYS`, `ARCX`, `XASE`, `XMAD`, `XETR`, `XFRA`, `XPAR`, `XAMS`, `MTAA`, `XLIS`, `XHEL`, `XVIE`, …) with `mic,name,country_iso2,operating_mic`. Not the full ISO file.
- [X] T042 [P] [US4] `src/main/resources/reference-data/instruments.sample.csv` — curated deterministic Yahoo-shape dataset (`Ticker,Category Name,Exchange`), ≳ 12 accepted rows spanning USD (`AAPL/NMS`, `MSFT/NMS`, `SPY/PCX` ETF) and EUR (`SAN.MC/MCE`, `IBE.MC/MCE`, `ADS.DE/FRA`, `MC.PA/PAR`, `ASML.AS/AMS`, …), plus a small number of rows that MUST be rejected so the startup log shows non-zero skip/quarantine counts. Include `AAPL · XNAS · USD` and ≥ 1 EUR listing (enabler §10). (research.md D10; OD-EN004-3)
- [X] T043 [US4] `infrastructure/config/ReferenceDataBootstrapRunner implements ApplicationRunner` — `@ConditionalOnProperty(name="app.reference-data.import-on-startup", havingValue="true", matchIfMissing=true)`; on run: resolve the four `classpath:` files from `ReferenceDataProperties`, call `ImportReferenceDataService.run(...)`, log a **structured** `event="ReferenceDataImportCompleted"` line with the counts. Catch every exception → log `event="ReferenceDataImportFailed"` with the source id and message → **return normally** (the app still starts and serves the existing catalog). (research.md D11; enabler §22)
- [X] T044 [US4] Test-profile wiring — ensure `@SpringBootTest` slices do **not** auto-import: add `app.reference-data.import-on-startup=false` to `src/test/resources/application.yml` (or a shared `@TestPropertySource`); the import ITs (T022, US5) drive `ImportReferenceDataService` explicitly. Confirm `ReferenceDomainModelTest` / contract test / `portfolio` suites unaffected.
- [X] T045 [US4] `cd implementation/platform && BUILD=1 ./start.sh` — platform healthy; `docker compose … logs backend | grep ReferenceDataImport` shows the completed summary with non-zero `imported` and non-zero skip/quarantine; `curl "http://localhost:4200/api/financial-instruments?query=AAPL"` → the Apple listing (no provider fields); a lower-case query and a partial-name query work; blank `query` → 400 problem+json; `NOSUCHTHING` → `200 []`; `./stop.sh` clean. Then `./start.sh` again → import re-runs with **0** new rows. Record VC-016/VC-018/VC-020 evidence in `quickstart.md` §F.
- [X] T046 [US4] `cd implementation/platform && ./e2e.sh` — `FD001-create-portfolio.spec.ts` + `platform-smoke.spec.ts` still pass (Chromium), exit 0 — EN004 adds no frontend and no regression. Confirm the backend container image builds from the Maven project (Dockerfile `./mvnw`).

**Checkpoint**: the catalog is populated offline inside the container; the existing E2E is green; FD002 has its data.

---

## Phase 7: US5 — Safe, repeatable, observable imports (Priority: P2)

**Goal**: re-running the import creates no duplicates; a malformed/unavailable source never deletes
or half-corrupts the catalog; every run emits structured diagnostics with no secrets.

**Independent Test**: quickstart §E — run the import twice → identical catalog, 0 duplicates;
run over a corrupt fixture → run rolls back, prior catalog intact, diagnostics name the record.
Covers VC-011, VC-012.

### Tests for US5 (write first, must fail)

- [X] T047 [P] [US5] `src/test/java/.../financialinstrument/infrastructure/persistence/ReferenceDataUpsertAdapterIT.java` (Testcontainers) — full import twice over `instruments.sample.csv` → 2nd run `imported=0`, all `updated`; `count(market)` and `count(financial_instrument)` unchanged; the unique `(ticker, market_mic)` and `mic` PK hold; `ListingId` is stable across runs. (VC-011)
- [X] T048 [P] [US5] `src/test/java/.../financialinstrument/infrastructure/reference/ReferenceDataFailureSafetyIT.java` (Testcontainers) — load `instruments.sample.csv` (catalog now has N rows); run an import over `instruments.corrupt.csv` (one unparseable line) → `ReferenceDataImportException`; assert `count(financial_instrument) == N` (whole run rolled back, prior catalog intact) and the exception's partial `ImportReport` / a logged `Rejection` names the offending line. (VC-012)
- [X] T049 [P] [US5] Add to `ReferenceDataUpsertAdapterIT` (or a sibling) — import `instruments.sample.csv`, then import a subset that **omits** two previously-seen rows → the omitted rows are still present and unchanged (`active` unchanged); no delisting. (enabler §15)

### Implementation for US5

- [X] T050 [US5] Harden `ImportReferenceDataService` — the whole run in **one** `TransactionTemplate`; a hard failure (I/O, parse blow-up, `DataAccessException`) → rollback + `ReferenceDataImportException` carrying the partial `ImportReport`; expected per-row `Rejected` outcomes do **not** fail the run; emit the structured diagnostics (`event`, `source`, `startedAt/finishedAt`, all 7 counters, and the `Rejection` list at DEBUG) with **no** credential/token in any field. (research.md D7; enabler §22, §24)
- [X] T051 [US5] Confirm & test: `upsertMarket`/`upsertListing` never deactivate/delete; provenance (`source`, `source_reference`, `last_imported_at`) is written on every insert **and** update. (enabler §15, §21; contracts/catalog-ports.md W5–W6)
- [X] T052 [US5] `./mvnw -B clean verify` — green: `ReferenceDataUpsertAdapterIT` (idempotency + omission), `ReferenceDataFailureSafetyIT`. Record VC-011/VC-012 evidence in `quickstart.md` §E.

**Checkpoint**: the ingestion mechanism is idempotent, fail-safe, and observable.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T053 [P] `git diff --stat` scope review (VC-019 / SC-011): the **only** `product/` change is the pre-approval enabler-header sync already made 2026-09-03 (`EN004-….md` §34 / Status) — **no** other `product/` edit, and **no** change to any `product/` *intent* (business rules, scope, acceptance criteria, other docs); **no** change to `db/migration/V1__baseline.sql` or `V2__portfolio.sql`; **no** change under `…/core/portfolio/`; **no** change to `frontend/`, `infrastructure/local/compose.yaml`, `start.sh`, `stop.sh`, `e2e.sh`; **no** new API operation beyond `GET /api/financial-instruments`; **no** new deployable / messaging / scheduler / search engine; **no** Java/Spring major-version bump; the only new dependency is `commons-csv`.
- [X] T054 [P] Update `implementation/platform/backend/core-service/README.md` and the backend section of `implementation/platform/README.md` — add the `financialinstrument` module (ADR-003 layout, sibling of `portfolio`); the reference-data model (Market + Financial Instrument listing, `ticker+market` identity, EUR/USD, `active`); the ingestion pipeline (source CSV → mapping-driven normalizer → upsert; `app.reference-data.*`; the flag-guarded startup runner; the committed fixtures); and the `GET /api/financial-instruments` search capability. No `product/` edit.
- [X] T055 ArchUnit deliberate-violation check (VC-017 / SC-005): (a) add a `financialinstrument.domain.model` → `infrastructure` import → `-Dtest=StandardArchitectureRulesTest` MUST fail → revert; (b) add `import org.apache.commons.csv.CSVRecord;` to a `business.normalization` class → MUST fail → revert; re-run → green. Record in `quickstart.md` §C.
- [X] T056 Assemble PR evidence in `specs/EN004-establish-financial-instrument-reference-data/pr-evidence.md` per `definition-of-done.md` "Minimum Pull Request Evidence": what/why; trace to `VC-001…VC-020`; the new dependency (`commons-csv`) justification; how each risk-register item turned out; how validated (no CI — `./mvnw verify` + `quickstart.md` §A–§G + `./start.sh` curl + `./e2e.sh`); architecture boundaries (ADR-001 & ADR-003 intact, **no new ADR**); the OD-EN004-3 outcome; confirmation no `product/` file was edited.
- [X] T057 Run the `definition-of-done.md` checklist against the change → `specs/EN004-establish-financial-instrument-reference-data/dod-checklist.md`: product/spec (traceable to EN004, scope), architecture (ArchUnit green + non-vacuous, ADR-003), code quality (`SupportedCurrency` enum, explicit optionals, no generic dot-strip), tests (unit TDD + Testcontainers ITs + contract + architecture), coverage ≥ 90 %, API/OpenAPI (contract-first, RFC 9457, no provider leakage), persistence (Flyway `V3`, Testcontainers, FK + constraints, single-tx import), secrets/hygiene (no secrets, `commons-csv` intentional), observability (structured import diagnostics), platform lifecycle (`start.sh`/`stop.sh`/`e2e.sh` unchanged), documentation (READMEs).
- [X] T058 Run the full `quickstart.md` (§A–§G) from a clean state; complete the "Verification Criteria coverage" table with concrete evidence for `VC-001 … VC-020`.
- [X] T059 Confirm the JaCoCo bundle gate (≥ 90 % line **and** branch) is met with the new module; the `financialinstrument` `config/**` + `persistence/entity/**` exclusions are the only additions and are justified (declarative wiring / mapping structures — `testing-strategy.md`); `business.normalization`, mappers, adapters, sources, and services stay **in** coverage.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)**: T001 sequential (pom) → T002 ‖ T003. Checkpoint before Foundational.
- **Foundational (P2)**: after Setup. T004 (migration) → T005 ‖ T006. **Blocks all user stories.**
- **US1 (P1)**: after Foundational. Tests T007 ‖ T008 ‖ T009 → impl T010 ‖ T011 → T012 → T013 → T014 ‖ T015 → T016 → T017 → T018 → T019 (gate).
- **US2 (P1)**: after **US1** (needs `domain.model`, `domain.ports`, the writer adapter). Tests T020 ‖ T021 ‖ T022 → T023 ‖ T024 → T025 (make T020 green) → T026 ‖ T027 (make T021 green) ‖ T028 → T029 → T030 → T031 (gate).
- **US3 (P1)**: after **US1** (catalog port + persistence); independent of US2 (seeds rows via the writer). Tests T032 ‖ T033 ‖ T034 → **T035 (merge OpenAPI — contract-first)** → T036 → T037 ‖ T038 → T039 → T040 (gate). *(T034 is written RED first but only turns green once T035 lands.)*
- **US4 (P1)**: after **US2 + US3** (needs the importer + the endpoint). T041 ‖ T042 → T043 → T044 → T045 → T046.
- **US5 (P2)**: after **US2** (hardens the importer). Tests T047 ‖ T048 ‖ T049 → T050 → T051 → T052 (gate).
- **Polish (P8)**: after US1–US5. T053 ‖ T054 → T055 → T056 → T057 → T058 → T059.

### Story dependency summary

```text
Setup → Foundational → US1 (module + persistence) ─┬─→ US2 (ingestion + normalizer) ─┬─→ US4 (fixtures + containerized) ─→ Polish
                                                   └─→ US3 (search + REST endpoint) ─┘   US5 (safe/idempotent/observable) ─┘
```

US2 and US3 can proceed in parallel once US1 is done (different files; US3 seeds test data via the
writer port, it does not need the importer). US4 needs both. US5 hardens US2.

### Parallel opportunities

- Setup: T002 ‖ T003.
- US1: T007 ‖ T008 ‖ T009 (tests); T010 ‖ T011; T014 ‖ T015.
- US2: T020 ‖ T021 ‖ T022 (tests); T023 ‖ T024; T026 ‖ T027 ‖ T028.
- US3: T032 ‖ T033 ‖ T034 (tests); T037 ‖ T038.
- US4: T041 ‖ T042.
- US5: T047 ‖ T048 ‖ T049 (tests).
- Polish: T053 ‖ T054.
- **US2 and US3 as whole tracks** once US1's gate (T019) is green.

### Within each user story

- Tests are written **first** and must fail before implementation (strict TDD for T020 —
  `YahooSymbolNormalizerTest`).
- Domain model → ports → JPA entities → repositories → mappers → adapters → (US3) controller/DTO.
- Each story's `verify` gate (T019 / T031 / T040 / T045+T046 / T052) must be green before the next
  priority.

---

## Implementation Strategy

### One capability, verified in slices

1. Setup → `commons-csv` + config + empty module, build green.
2. Foundational → `V3` schema on Testcontainers, module config.
3. **US1** → domain model + JPA persistence + ports + ArchUnit. **STOP & VALIDATE** (quickstart
   §A/§D) — the module exists and is enforced.
4. **US2** → mapping-driven normalizer (TDD) + ingestion; the sample CSV imports with exact
   `ImportReport` counts.
5. **US3** → search service + `GET /api/financial-instruments` contract-first + contract test.
6. **US4** → committed `main` fixtures + the flag-guarded startup runner; `./start.sh` curl +
   `./e2e.sh` green.
7. **US5** → idempotency + failure-safety + structured diagnostics.
8. Polish → scope check, READMEs, deliberate-violation check, PR evidence, DoD, full quickstart.

### Constitution / DoD checkpoints

- `financialinstrument.domain` stays framework/CSV-free — `StandardArchitectureRulesTest` from
  T018 (constitution VI; VC-010/VC-013/VC-017).
- The normalizer is **TDD** — T020 written and failing before T025 (constitution VII; DR-004).
- Persistence / import / search ITs run on **real Testcontainers PostgreSQL**, never mocked
  (constitution VII; VC-015).
- `GET /api/financial-instruments` is **contract-first** — the `openapi.yaml` operation is merged
  (T035) as the **first** US3 implementation task, before the controller; the contract test (T034)
  guards it (constitution VIII; VC-014).
- Coverage gate ≥ 90 % line + branch preserved (T019, T059).
- No Portfolio/API/schema change; no new deployable / messaging / scheduler / search engine; no
  `product/` edit (T053; VC-019).
- Any material deviation from ADR-003 or the normalization decision doc → **stop and surface**
  (constitution IV; FR-038).

---

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- `[US#]` labels map tasks to the spec's user stories for traceability.
- Invoke the build as `./mvnw` (the wrapper); set `DOCKER_HOST` + `TESTCONTAINERS_RYUK_DISABLED=true`
  for the Testcontainers ITs (`local-dev-environment` note).
- The reference mapping CSVs are **copied** from `product/…` into `src/{main,test}/resources/reference-data/`
  (OD-EN004-20); do **not** delete the `product/` originals under EN004.
- `openapi.yaml` stays **OpenAPI 3.0.3** (the `swagger-request-validator` 2.44.x constraint from
  FD001).
- No `.specify/extensions.yml` → no post-execution hooks.
- Total: **59 tasks** — Setup 3, Foundational 3, US1 13, US2 12, US3 9, US4 6, US5 6, Polish 7.
