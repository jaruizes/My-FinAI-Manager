# EN004 — Establish Financial Instrument Reference Data · PR Evidence

## What requirement does this implement?

**EN004 — Establish Financial Instrument Reference Data** (Technical Enabler, Approved 2026-09-03).
A local, PostgreSQL-backed **Market + Financial Instrument catalog** with a deterministic,
mapping-driven ingestion pipeline that normalizes a Yahoo-shape source into canonical
`(ticker, MIC, currency)` listings, and a single read-only search operation
(`GET /api/financial-instruments`) that FD002 uses for controlled instrument selection. No external
reference-data provider is contacted at runtime.

## Which specification / tasks does it trace to?

- Enabler: `product/definition/enablers/EN004-establish-financial-instrument-reference-data/EN004-establish-financial-instrument-reference-data.md`
- Supporting decision: `…/EN004-yahoo-normalization-decision.md` + the two mapping CSVs under `…/reference-data/`
- Governing ADRs: **ADR-001** (one `core-service` deployable — unchanged), **ADR-003** (Standard
  Spring Backend Architecture). **No new ADR.**
- SDD artifacts: `specs/EN004-establish-financial-instrument-reference-data/` — `spec.md`
  (US1–US5, FR-001…FR-040, SC-001…SC-012, VC-001…VC-020, Clarifications 2026-09-03), `plan.md`
  (Constitution Check PASS, OD-EN004-1…20 all resolved, Risk Register), `research.md` (D1–D17 +
  human decisions), `data-model.md`, `contracts/` (`openapi/financial-instruments.search.yaml`,
  `catalog-ports.md`, `reference-mapping.md`), `quickstart.md` (A–G ⇒ VC-001…VC-020),
  `checklists/requirements.md` (16/16), `tasks.md` (T001–T059).

## What changed?

| Area | Change |
|---|---|
| New module | `com.myfinaimanager.core.financialinstrument` — ADR-003 layout, sibling of `portfolio`: `domain/{model,ports,exceptions}` · `business` · `business/normalization` · `infrastructure/{api/rest[/dto,/mapper], persistence/{entity,repository,mapper}, reference/{csv,mapping,market,instrument}, config}`. |
| Domain | `Market` (identity = `Mic`), `FinancialInstrumentListing` (identity = `Ticker + Mic`; deterministic `ListingId`), `SupportedCurrency` enum (`EUR`,`USD`), `InstrumentType` (best-effort, descriptive only), value objects `Mic`/`Ticker`/`Isin`, `Provenance`, `ImportCounters` (7), `ImportReport`, `Rejection` + `RejectionReason` (8). Ports: `FinancialInstrumentCatalog`, `MarketCatalog`, `ReferenceCatalogWriter`, `RawMarketSource`, `RawInstrumentSource`. |
| Normalization | `YahooSymbolNormalizer` (plain, no Spring) resolves `(rawSymbol, exchangeCode)` **only** through `ExchangeMicTable` + `SuffixOverrideTable` — override wins, then exchange rule; no generic dot-strip. Every unmapped/ambiguous/unsupported/suffix-mismatch/empty row → a counted `Rejection`. |
| Ingestion | `ImportReferenceDataService` (`@Service`) — one run = one `TransactionTemplate`: upsert markets, then per instrument row normalize → verify MIC has a Market row → upsert on natural identity; same-run `(ticker,MIC)` conflict → `IDENTITY_CONFLICT`. Hard failure → rollback + `ReferenceDataImportException` with the partial `ImportReport`. Per-row rejections never fail the run; omission never delists. |
| Sources | `CsvReferenceFileReader` (Apache Commons CSV, confined to `infrastructure.reference.csv`), `CsvMarketSource`, `YahooCsvInstrumentSource`. `ReferenceMappingConfiguration` builds the mapping tables + the normalizer as beans. |
| Persistence | `MarketEntity` / `FinancialInstrumentEntity` (infra-only `@Entity`, assigned `@Id`, no `@ManyToOne` — `market_mic` is a plain column), `MarketJpaRepository` / `FinancialInstrumentJpaRepository` (derived queries + one `@Query` for search ordering), `ReferenceDataPersistenceMapper`, `FinancialInstrumentCatalogAdapter` (`FinancialInstrumentCatalog` + `MarketCatalog`, read-only), `ReferenceDataUpsertAdapter` (`ReferenceCatalogWriter`, find→`refreshFrom`|`save`, never deletes/deactivates). |
| API | `GET /api/financial-instruments?query=` → `FinancialInstrumentSearchController` → `SearchFinancialInstrumentsService` (trim; blank → `InvalidSearchQueryException`) → catalog. `FinancialInstrumentResponse` DTO + `FinancialInstrumentResponseMapper` drop every provider/provenance field. `FinancialInstrumentExceptionHandler` (`@RestControllerAdvice`) → RFC 9457 `400` `type:/problems/invalid-search-query`. |
| Startup | `ReferenceDataBootstrapRunner` (`ApplicationRunner`, `@ConditionalOnProperty app.reference-data.import-on-startup` default `true`) — runs one import, logs `event=ReferenceDataImportCompleted` with the 7 counters; any failure → `event=ReferenceDataImportFailed` + returns normally. |
| Schema | **`V3__financial_instrument.sql`** — `market` (MIC `CHAR(4)` PK + shape CHECK) and `financial_instrument` (assigned UUID PK, FK → `market`, `UNIQUE(ticker, market_mic)`, currency `IN ('EUR','USD')`, ISIN shape CHECK, ticker length CHECK, type CHECK, `upper(ticker)` + `(active,currency)` indexes). `V1`/`V2` untouched. |
| Contract | `openapi.yaml` (3.0.3) — **+** `GET /api/financial-instruments` operation, **+** `FinancialInstrument` / `FinancialInstrumentList` schemas, reuses `Problem`; `info.description` updated. `query` is `required: false` + `minLength: 1` so a blank value reaches the business `400` rather than a framework request-validation error. |
| Config / build | `application.yml` **+** `app.reference-data.*` (`import-on-startup` + 4 `classpath:` file locations). `pom.xml` **+** `org.apache.commons:commons-csv` 1.12.0; JaCoCo excludes **+** `financialinstrument/infrastructure/config/**` + `…/persistence/entity/**` (declarative wiring / JPA mapping structures — consistent with `portfolio`). |
| Reference data | `src/main/resources/reference-data/` — `markets.csv` (14 curated venues), `instruments.sample.csv` (21 accepted + 6 reject, Yahoo-shape `Ticker,Name,Exchange`), and the two mapping CSVs copied from `product/` (implementation copy = runtime authority; `product/` originals retained — OD-EN004-20). |
| Architecture test | `StandardArchitectureRulesTest` — **+** `org.apache.commons..` to the domain framework-free list, **+** `csv_parsing_is_confined_to_infrastructure`; now 12 rules, module-wildcarded so `financialinstrument` inherits every ADR-003 boundary. |
| Test support | `PostgresContainerSupport` **+** one `@DynamicPropertySource` line (`app.reference-data.import-on-startup=false`) so `@SpringBootTest` slices do not auto-import. `portfolio/infrastructure/persistence/SchemaIntegrityIT` made sibling-migration-tolerant (`contains` not `containsExactly`; `noneMatch(startsWith("hibernate_"))`) — a test adaptation, **not** a behavior change. |
| Docs | `backend/core-service/README.md` (+`financialinstrument` module section, corrected `api/rest/mapper` tree) and `implementation/platform/README.md` (+catalog-search capability, +reference-data note). |

**Not changed**: `V1__baseline.sql` / `V2__portfolio.sql`, any `portfolio` production code,
`frontend/`, `infrastructure/local/compose.yaml`, `start.sh` / `stop.sh` / `e2e.sh`, Java 21 /
Spring Boot 3.5.6. No new deployable, messaging, scheduler, search engine, or persistence
technology.

## Why was this design chosen?

`EN004-yahoo-normalization-decision.md` is the authority for normalization. Key implementation
decisions (research.md):

- **Mapping-only normalization** (D6): a symbol is canonicalised strictly via the two CSVs — no
  heuristic suffix stripping — so every accepted listing is explainable and every other row is a
  typed, counted rejection.
- **Deterministic `ListingId`** (D13): `UUID.nameUUIDFromBytes("finai:financialinstrument:listing:<TICKER>|<MIC>")` — stable across re-imports, no DB round-trip to allocate an id, upsert is a pure function of the natural key.
- **Curated `instruments.sample.csv` is the initial dataset** (OD-EN004-3): a small deterministic
  sample with a `Name` column (the raw Yahoo mapping has none, and FD002 needs a searchable name),
  including deliberate reject rows so the startup log always shows non-zero skip/quarantine counts.
- **`instrumentType` is descriptive, never a filter** (FR-040): best-effort from the name; an
  inconclusive guess (`OTHER`) is stored as `null` rather than as noise. FD002 search does not
  filter by type.
- **One transaction per run** (D7): all-or-nothing on a hard failure protects the previously valid
  catalog (VC-012); per-row rejections are data, not failures.
- **Provider-neutral contract** (FR-028): the REST response never carries `providerSymbol`, the
  source exchange code, `operatingMic`, `instrumentType`, or any `source*` field — enforced by the
  mapper and the contract test.
- **Cross-module `Clock` bean reuse**: the importer autowires the single platform `Clock` bean
  (adding a second would be ambiguous); only a JDK type is referenced, so no ArchUnit violation.

## How was it tested?

No CI — validated locally (2026-09-03):

- `./mvnw -B clean verify` — **Surefire 107** + **Failsafe 51**, 0 failures / 0 errors. JaCoCo
  bundle gate: **line 96.25 % · branch 91.37 %** (≥ 90 % both) — passed.
- **TDD** for the deterministic core: `YahooSymbolNormalizerTest` (16 cases — one per
  `RejectionReason` + worked examples, inline mapping tables), `ReferenceDomainModelTest`
  (value objects + `fromRaw` validation), `NormalizationDataTest` (mapping-data predicates),
  `SearchFinancialInstrumentsServiceTest`.
- **Testcontainers ITs** (real PostgreSQL): `ReferenceDataSchemaIntegrityIT` (V3 in Flyway history,
  constraints + columns), `ReferenceDataPersistenceRoundTripIT`, `FinancialInstrumentCatalogAdapterIT`
  (ticker/name search, active + EUR/USD filter, ordering, empty result), `YahooCsvInstrumentSourceIT`
  (exact 7-counter tally over the fixtures), `ReferenceDataUpsertAdapterIT` (2nd run `imported=0` /
  all `updated`, `ListingId` stable, omission ≠ delisting, provenance on insert **and** update),
  `ReferenceDataFailureSafetyIT` (corrupt CSV → `ReferenceDataImportException`, row count unchanged;
  missing file → hard failure before any write).
- **Contract test**: `FinancialInstrumentSearchContractTest` (`@WebMvcTest` + swagger-request-validator)
  — `200` array + `200 []` + `400 problem+json` all conform to `openapi.yaml`; response JSON asserts
  **no** `providerSymbol` / `source` / `instrumentType` / `externalReference` field.
- **ArchUnit non-vacuous check** (T055): deliberate `financialinstrument.domain.model` →
  `infrastructure.config` reference **and** `org.apache.commons.csv.CSVRecord` in
  `business.normalization` → `StandardArchitectureRulesTest` fails 2/12 (`domain_does_not_depend_on_infrastructure`,
  `csv_parsing_is_confined_to_infrastructure`) → reverted → 12/12 green.
- **Containerized runtime** (`BUILD=1 ./start.sh`): backend healthy; startup log
  `ReferenceDataImportCompleted processed=27 imported=21 updated=0 skippedUnsupportedCurrency=1
  skippedUnsupportedMarket=2 quarantinedAmbiguous=1 quarantinedInvalid=2`. `curl` via the nginx
  proxy: `?query=AAPL` and `?query=aapl` → the Apple listing (no provider fields); `?query=santander`
  → Banco Santander by name; blank query → `400 /problems/invalid-search-query`; `?query=NOSUCHTHING`
  → `200 []`. Restart → `imported=0 updated=21` (idempotent). `./stop.sh` clean.
- **`./e2e.sh` → 2 passed (Chromium), exit 0** — FD001 + platform smoke unaffected; backend image
  builds from the Maven project.

## What architecture boundaries are affected?

Internal only. ADR-001 intact — still one `core-service` deployable, no new service or deployment
boundary. ADR-003 `domain → business → infrastructure` (inward) enforced for the new module by the
module-wildcarded `StandardArchitectureRulesTest`. New data ownership: the `financialinstrument`
module owns `market` + `financial_instrument`; it does not touch `portfolio`/`position`/`investor`.
One new external operation only: `GET /api/financial-instruments`.

## Were any ADRs required?

No. EN004 extends the platform within ADR-001 + ADR-003. `commons-csv` is a small leaf utility
(RFC 4180 parsing), ArchUnit-confined to one infrastructure package — not a framework, database,
messaging, or cloud technology, so no technology-policy change and no ADR.

## Risk Register outcome (plan.md)

| Risk | Outcome |
|---|---|
| Heuristic normalization produces wrong tickers | Avoided — mapping-only; `YahooSymbolNormalizerTest` covers every `RejectionReason`; no generic dot-strip path exists. |
| Yahoo `Exchange` alone is ambiguous (generic `ENX`, `EUX`) | Handled — suffix-override table resolves `ENX/PA` etc.; `ENX/NX` (currency unresolvable) → `AMBIGUOUS_EXCHANGE`; `EUX` → `NOT_SUPPORTED_FOR_FD002`. |
| Partial import corrupts the catalog | Avoided — one transaction per run; `ReferenceDataFailureSafetyIT` proves rollback leaves N rows intact. |
| Re-import creates duplicates / churns ids | Avoided — deterministic `ListingId` + upsert on natural key; `ReferenceDataUpsertAdapterIT` proves `imported=0` and stable id on the 2nd run. |
| Omitting a row silently delists it | Avoided — no auto-inactivation; dedicated IT proves omitted rows stay `active` and unchanged. |
| Provider fields leak into the API | Avoided — mapper drops them; contract test asserts their absence; ArchUnit keeps provider concepts out of `domain`/`business` transport. |
| OpenAPI 3.1 / validator friction | Avoided — contract authored in 3.0.3; `query` `required:false`+`minLength:1` lets the business `400` be exercised. |
| Startup import failure takes the platform down | Avoided — `ReferenceDataBootstrapRunner` logs and swallows; the app starts and serves the existing catalog. |
| Coverage gate regression from the new module | Handled — targeted unit tests for the normalizer/mapping-data/`InstrumentType` branches; bundle branch 91.37 % (excludes limited to config + `@Entity`, as `portfolio`). |

## OD-EN004-3 outcome

The curated `instruments.sample.csv` **is** the initial dataset (not a placeholder for a later full
Yahoo import). It ships in `src/main/resources/reference-data/`, is imported on every backend start,
and is what FD002 will search. A larger/live dataset is a future decision outside EN004.

## `product/` change

The **only** `product/` change is the pre-approved 2026-09-03 enabler-header sync
(`EN004-establish-financial-instrument-reference-data.md` — `Status: Approved`, §34 checkboxes,
`Approved by: jaruiz`, plus the "Reference-data provisioning decisions" subsection). **No** change
to any `product/` intent — business rules, scope, acceptance criteria, or any other `product/`
document.

## What evidence shows acceptance criteria pass?

`quickstart.md` → "Verification Criteria coverage" — every `VC-001 … VC-020` mapped to a concrete,
green result from the run above.
