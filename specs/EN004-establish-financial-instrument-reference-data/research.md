# Research — EN004 Establish Financial Instrument Reference Data (Phase 0)

Resolves the open technical decisions (EN004 §33 + spec assumptions A3–A15, tracked as
OD-EN004-1…20 in [plan.md](./plan.md)) within the bounds of ADR-003 and the
`EN004-yahoo-normalization-decision.md` algorithm. Each item: **Decision · Rationale ·
Alternatives considered**.

---

## D1 — Module layout & adapter package naming (OD-EN004-1)

**Decision**: `com.myfinaimanager.core.financialinstrument` with
`domain.{model,ports,exceptions}` · `business` (+ `business.normalization` for the deterministic
normalizer) · `infrastructure.{api.rest, api.rest.dto, api.rest.mapper, persistence.{entity,repository,mapper}, reference.{csv,market,instrument,mapping}, config}`.

**Rationale**: ADR-003 "Modular Monolith Structure" explicitly names `financialinstrument` as a
peer module of `portfolio`; the three-area layout and the `api.rest.mapper` location (ADR-003
amendment 2026-09-02) are fixed. `infrastructure.reference` is the enabler's own suggested package
(§5) for the ingestion adapters; sub-splitting `reference.{csv,market,instrument,mapping}` keeps
file-format code, source-specific code, and the mapping-table loaders separable and independently
testable.

**Alternatives considered**:
- *`infrastructure.ingestion` / `infrastructure.import`* — `reference` is the enabler's word (§5,
  §16) and reads better next to a future `infrastructure.provider` HTTP adapter.
- *Put the normalizer in `infrastructure`* — rejected: the normalization **rules** (which suffix to
  strip, which MIC is canonical, when to quarantine) are deterministic business policy, not
  file/transport concerns (constitution VI; enabler §18 "business layer owns validation"). Only
  CSV parsing and classpath I/O live in `infrastructure`.

---

## D2 — Domain model (OD-EN004-5, and shape of Market / Listing)

**Decision**:

- `Market` (aggregate-ish reference entity): `mic: Mic`, `name: String`, `country: Optional<String>`
  (ISO 3166-1 alpha-2), `operatingMic: Optional<Mic>`, `active: boolean`. Identity: `mic`.
- `FinancialInstrumentListing`: `id: ListingId` (UUID), `name: String`, `identity: InstrumentIdentity`
  (`ticker: Ticker` + `market: Mic`), `currency: SupportedCurrency`, `isin: Optional<Isin>`,
  `externalReference: Optional<String>`, `instrumentType: Optional<InstrumentType>`,
  `providerSymbol: Optional<String>`, `active: boolean`, `provenance: Provenance`
  (`source`, `sourceReference?`, `lastImportedAt?`).
- Value objects: `Mic` (`^[A-Z0-9]{4}$`, upper/trim), `Ticker` (non-blank, ≤ 20, upper/trim),
  `Isin` (`^[A-Z]{2}[A-Z0-9]{9}[0-9]$`), `SupportedCurrency` **enum** `{ EUR, USD }`,
  `InstrumentType` enum `{ EQUITY, ETF, OTHER }` (best-effort from source), `InstrumentIdentity`
  record.
- Raw→normalized carriers: `NewMarket`, `NewListing` (all-String inputs the domain parses), matching
  the FD001 `NewPosition` pattern.

**Rationale**: mirrors the `portfolio` domain style (records + small value objects, `Optional` for
absent data, `reconstitute(...)` for persistence rehydration). `SupportedCurrency` as an enum
(enabler §6, §33.5) — the initial set is 2; a table would be speculative infrastructure (AR-046).
Adding a currency later is a one-line enum change + a migration to widen the CHECK.

**Alternatives considered**:
- *`Currency` master table now* — rejected (§33.5; AR-046).
- *One `FinancialInstrument` with a nested list of `Listing`s* — rejected: FD002 selects a
  **listing** (`ticker + market`), a single economic instrument with N listings is just N rows;
  there is no aggregate invariant spanning listings, so no aggregate root is warranted.
- *`instrumentType` required* — rejected: the Yahoo source's "Category Name" is unreliable for
  equity/ETF classification; keep it optional and descriptive (spec FR-040).

---

## D3 — `market` and `financial_instrument` schema (OD-EN004-6, OD-EN004-7, OD-EN004-8)

**Decision**: one Flyway migration `V3__financial_instrument.sql` (module-owned — AR-020):

```sql
CREATE TABLE market (
    mic                CHAR(4) PRIMARY KEY,
    name               TEXT NOT NULL,
    country_iso2       CHAR(2),
    operating_mic      CHAR(4),
    active             BOOLEAN NOT NULL DEFAULT true,
    source             TEXT,
    source_reference   TEXT,
    last_imported_at   TIMESTAMPTZ,
    CONSTRAINT market_mic_shape_chk CHECK (mic ~ '^[A-Z0-9]{4}$')
);

CREATE TABLE financial_instrument (
    id                 UUID PRIMARY KEY,
    name               TEXT NOT NULL,
    ticker             TEXT NOT NULL,
    market_mic         CHAR(4) NOT NULL REFERENCES market (mic),
    currency           CHAR(3) NOT NULL,
    isin               CHAR(12),
    external_reference TEXT,
    instrument_type    TEXT,
    provider_symbol    TEXT,
    active             BOOLEAN NOT NULL DEFAULT true,
    source             TEXT,
    source_reference   TEXT,
    last_imported_at   TIMESTAMPTZ,
    CONSTRAINT fin_instr_identity_uk    UNIQUE (ticker, market_mic),
    CONSTRAINT fin_instr_currency_chk   CHECK (currency IN ('EUR','USD')),
    CONSTRAINT fin_instr_isin_chk       CHECK (isin IS NULL OR isin ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'),
    CONSTRAINT fin_instr_ticker_len_chk CHECK (length(btrim(ticker)) BETWEEN 1 AND 20),
    CONSTRAINT fin_instr_type_chk       CHECK (instrument_type IS NULL OR instrument_type IN ('EQUITY','ETF','OTHER'))
);

CREATE INDEX fin_instr_ticker_idx     ON financial_instrument (upper(ticker));
CREATE INDEX fin_instr_active_ccy_idx ON financial_instrument (active, currency);
```

Instrument↔Market is a **plain `market_mic` column + FK** (OD-EN004-8), not a JPA `@ManyToOne`
navigation — there is no aggregate to traverse from a listing (same call as EN003's `investor_id`).
The catalog adapter joins to `market` only when a search result needs the market name.

**Rationale**: `ticker + market` unique = FD001 Position identity + enabler §7. The FK guarantees
VC-002/VC-009 ("results provide a valid combination"). CHECK constraints are defense-in-depth for
the domain rules (matches the `portfolio` schema style). `CHAR(4)`/`CHAR(3)` mirror the existing
`position.currency CHAR(3)` choice; the domain trims on read.

**Alternatives considered**:
- *Surrogate `market_id` FK* — rejected: MIC is a stable natural key (ISO 10383); a surrogate adds
  a join with no benefit.
- *No FK, just a column* — rejected: loses the guarantee that every listed MIC is a known Market;
  the import already loads Markets first, the FK makes it enforceable.
- *`NUMERIC`/typed ISIN column* — N/A; ISIN is `CHAR(12)` text with a shape CHECK.

---

## D4 — JPA entities & mapper (persistence adapter pattern)

**Decision**: `MarketEntity` / `FinancialInstrumentEntity` in
`infrastructure.persistence.entity` (JPA only; `@Id` assigned, not generated; `equals`/`hashCode`
on id/mic; protected no-arg ctor + all-args ctor — the EN003 pattern). `market_mic` is a plain
`@Column`. `ReferenceDataPersistenceMapper` (`@Component`, hand-written) does
`toEntity` / `toDomain` both ways; `toDomain` reconstitutes value objects (`Mic`, `Ticker`, `Isin`,
`SupportedCurrency.valueOf`, trims `CHAR` padding) exactly like `PortfolioPersistenceMapper`.
`FinancialInstrumentCatalogAdapter` implements the `domain.ports` catalog interfaces;
`ReferenceDataUpsertAdapter` implements the persistence half of the importer port.

**Rationale**: identical to the EN003 Spring Data JPA pattern already in the codebase — domain
JPA-free (VC-013), explicit mapping, no MapStruct dependency.

**Alternatives considered**: MapStruct — rejected (extra build dependency; the mappings are
trivial; consistent with `portfolio`).

---

## D5 — Spring Data repositories & search query (OD-EN004-9)

**Decision**:
- `MarketJpaRepository extends JpaRepository<MarketEntity, String>` — derived `findByMic`,
  `findAllByMicIn`.
- `FinancialInstrumentJpaRepository extends JpaRepository<FinancialInstrumentEntity, UUID>`:
  - `Optional<FinancialInstrumentEntity> findByTickerIgnoreCaseAndMarketMic(String ticker, String mic)` (upsert lookup).
  - one `@Query` for search:
    ```java
    @Query("""
        select fi from FinancialInstrumentEntity fi
        where fi.active = true
          and fi.currency in ('EUR','USD')
          and ( upper(fi.ticker) = upper(:q) or lower(fi.name) like lower(concat('%', :q, '%')) )
        order by case when upper(fi.ticker) = upper(:q) then 0 else 1 end, fi.ticker asc
        """)
    List<FinancialInstrumentEntity> search(@Param("q") String q);
    ```

**Rationale**: one round-trip; case-insensitive; exact-ticker matches rank first; PostgreSQL
`like`/`ilike` over hundreds–low-thousands of rows needs no engine (enabler §19; AR-022; technology
policy "PostgreSQL before OpenSearch"). `active` + currency filter is the FD002 default (FR-025).

**Alternatives considered**:
- *`pg_trgm` GIN index now* — deferred: add only if the committed dataset shows plain `ilike` is
  too slow (unlikely). Documented as a follow-up index migration if needed (OD-EN004-9).
- *Full-text `tsvector`* — over-engineered for prefix/substring name search at this scale.
- *Two queries (ticker then name) merged in Java* — the single `@Query` with an `order by case` is
  simpler and keeps ranking in SQL.

---

## D6 — Mapping-driven normalization algorithm (the deterministic core — TDD)

**Decision**: implement `EN004-yahoo-normalization-decision.md` §"Algorithm" verbatim as
`business.normalization.YahooSymbolNormalizer` (pure, no Spring), with:

- `ExchangeMicMapping` — loaded from `yahoo-exchange-to-mic-mapping.csv`: keyed by
  `source_exchange_code` → `{ expectedYahooSuffix?, canonicalMic?, currency?, supportedForFd002 }`.
- `SuffixOverrideTable` — loaded from `yahoo-exchange-suffix-overrides.csv`: keyed by
  `(source_exchange_code, yahoo_suffix)` → `{ canonicalMic, currency?, country? }`.
- `normalize(rawSymbol, sourceExchangeCode) -> NormalizationResult` = either
  `Accepted(providerSymbol, ticker, mic, currency)` or
  `Rejected(reason ∈ { NO_MAPPING, NOT_SUPPORTED_FOR_FD002, UNSUPPORTED_CURRENCY, SUFFIX_MISMATCH, AMBIGUOUS_EXCHANGE, MIC_UNRESOLVED, EMPTY_TICKER, IDENTITY_CONFLICT })`.

Algorithm (decision doc steps 1–10):
1. trim; 2. upper-case the provider symbol; 3. extract trailing `.<suffix>` candidate (for lookup
only); 4. if `(exchange, suffix)` override exists → take its MIC/currency; 5. else load the exchange
rule; 6. if the rule has `expected_yahoo_suffix` → strip it **only if** the symbol ends exactly
with `.<that suffix>`, else `SUFFIX_MISMATCH`; 7. if the rule's suffix is empty → do not truncate;
8. keep the original as `providerSymbol`; 9. stripped value = canonical `ticker`; 10. if `ticker`
empty or MIC/currency unresolved → reject. Then: currency ∉ {EUR,USD} → `UNSUPPORTED_CURRENCY`;
`supported_for_fd002 = false` → `NOT_SUPPORTED_FOR_FD002`; generic exchange (`ENX`) with no
resolving override → `AMBIGUOUS_EXCHANGE`.

`ImportReferenceDataService` runs the normalizer per row, accumulates `ImportCounters`
(`processed / imported / updated / skippedUnsupportedCurrency / skippedUnsupportedMarket /
quarantinedAmbiguous / quarantinedInvalid`), maps `Rejected.reason` → the right counter, and never
lets a `Rejected` row reach persistence.

**Rationale**: the decision doc is prescriptive and example-rich; a faithful transliteration is the
safest path and directly TDD-able (one test per worked example + one per rejection reason). No
generic dot-strip anywhere (SC-004).

**Alternatives considered**: a regex-per-exchange table — rejected: the decision doc's
suffix-equality check is explicit and simpler; regex invites the forbidden generic behavior.

Worked cases the unit suite MUST cover (from the decision doc): `SAN.MC + MCE → SAN·XMAD·EUR`;
`IBE.MC + MCE → IBE·XMAD·EUR`; `AAPL + NMS → AAPL·XNAS·USD`; `ADS.DE + FRA → ADS·XETR·EUR`
(override); `<sym>.L + LSE` → `UNSUPPORTED_CURRENCY` (GBP); `<sym>.NX + ENX` → `AMBIGUOUS_EXCHANGE`
(currency not inferable); `FOO.XX + MCE` → `SUFFIX_MISMATCH`; `.MC + MCE` → `EMPTY_TICKER`;
`<x> + EUX` → `NOT_SUPPORTED_FOR_FD002`; two rows → same `(ticker, MIC)` conflicting name →
`IDENTITY_CONFLICT`.

---

## D7 — Import model, transaction & idempotency (OD-EN004-10, OD-EN004-11, OD-EN004-12)

**Decision**:
- **One transaction per import run** via a programmatic `TransactionTemplate` in
  `ImportReferenceDataService` (Markets then instruments). A hard failure (I/O, parser blow-up,
  DB error) rolls the whole run back → the previously valid catalog is untouched (VC-012).
- Per-row `Rejected` outcomes are **expected** and do not fail the run — they are counted and
  (optionally) logged at DEBUG with the raw row.
- **Upsert**: Markets by `mic` (`findByMic → update fields | insert`); instruments by
  `(ticker, market_mic)` (`findByTickerIgnoreCaseAndMarketMic → update | insert`, `id` from
  OD-EN004-17). Re-run ⇒ every row `updated`/unchanged, `imported = 0`, zero duplicates (VC-011).
- **No inactivation on omission** — a row absent from a later import is left as-is (enabler §15,
  §33.9). `active` only changes if a source row explicitly says so.
- Markets are imported first; an instrument whose canonical MIC has no `market` row →
  `skippedUnsupportedMarket` (and the FK would reject it anyway).

**Rationale**: the dataset is small; a single tx is the simplest fail-safe strategy and matches the
"all Positions or none" instinct from FD001. Natural-key upsert is deterministic and needs no
`ON CONFLICT` native SQL (stay in Spring Data — AR-060).

**Alternatives considered**:
- *Per-row autonomous transactions* — rejected: a mid-batch failure would leave a partial catalog
  (VC-012 risk); revisit only if the full Yahoo file makes one tx impractical (then: staging table
  + swap, documented as a new decision).
- *`ON CONFLICT DO UPDATE` native upsert* — faster but native SQL is CONDITIONAL (AR-060) and
  unjustified at this volume.
- *Soft-delete rows missing from a new import* — rejected explicitly by enabler §15.

---

## D8 — Provenance (OD-EN004-13)

**Decision**: `source` / `source_reference` / `last_imported_at` columns **on** `market` and
`financial_instrument` (no separate history table). `source` = a stable token
(`YAHOO_CSV`, `ISO10383_CSV`); `source_reference` = the raw provider symbol / raw MIC row key;
`last_imported_at` = the run timestamp. Never exposed by the REST DTO.

**Rationale**: spec A10 / enabler §21, §33.10 — minimal operational traceability, first version.
A history/audit table is a later-enabler concern (§14).

**Alternatives considered**: separate `reference_data_import` + `..._import_row` tables — deferred
(no requirement; AR-046).

---

## D9 — REST contract (OD-EN004-16) & OpenAPI mechanics

**Decision**: add to `implementation/platform/contracts/openapi/openapi.yaml` (stays **3.0.3**):

```
GET /api/financial-instruments?query=<string, required, minLength 1>
  200 application/json  -> array of FinancialInstrument { id, name, ticker, market, currency, active, isin? }   (empty array when no match)
  400 application/problem+json  -> ValidationProblem (type: /problems/invalid-search-query)  when query missing/blank
```

`market` is the MIC string. `currency` is `EUR`|`USD` (enum). **No** `providerSymbol`,
`externalReference`, `operatingMic`, `instrumentType`, `source*` in the schema. Mirror fragment at
`contracts/openapi/financial-instruments.search.yaml`. Contract test = `@WebMvcTest` +
`swagger-request-validator` `openApi().isValid("openapi.yaml")` (the file is copied to the test
classpath by the existing `maven-resources-plugin` execution — no pom change needed for that).

**Rationale**: FD002 needs exactly instrument search (spec FR-027). 3.0.3 avoids the
`swagger-request-validator` 2.44 `type:[...]`/`allOf` bugs FD001 already hit
(`local-dev-environment` memory). `application/problem+json` (RFC 9457) matches the platform error
model (AR-012). Empty array (not 404) for "no results" is the correct REST semantic and matches
FD002 AC-008 (frontend shows a no-results state).

**Alternatives considered**:
- *`GET /api/markets` too* — deferred to FD002 (spec FR-027; enabler §33.11); market data reaches
  the UI via search results.
- *`POST /search`* — unnecessary; a `query` string param is idiomatic and cacheable.
- *Pagination* — not required at this scale; a documented `limit` default (e.g. 50) is a safe
  addition decided in tasks, not a contract necessity for FD002's typeahead.

---

## D10 — Reference-file provisioning & packaging (OD-EN004-2, OD-EN004-3, OD-EN004-4, OD-EN004-20)

**Decision**:
- All four reference CSVs are **classpath resources** under `src/main/resources/reference-data/`
  (in the jar, in the image — no runtime mount; FR-030 "explicit and reproducible").
- `markets.csv` — a **committed curated** ISO 10383 – compatible subset (columns:
  `mic,name,country_iso2,operating_mic`) covering every MIC referenced by `instruments.sample.csv`
  and FD002. Not the full ISO file.
- `instruments.sample.csv` — a **committed curated** Yahoo-shape subset
  (`Ticker,Category Name,Exchange`) with ≳ 12 rows spanning `XNAS`/`XNYS` (USD) and
  `XMAD`/`XETR`/`XPAR`/`XAMS`/`XMIL` (EUR), plus a few rows that MUST be rejected (a GBP `LSE` row,
  an `ENX/NX` row, a suffix-mismatch row) so the import ITs assert the counters.
- The two mapping CSVs are **copied** from
  `product/definition/enablers/EN004-.../reference-data/` — the implementation copy is the runtime
  authority; the `product/` copies remain the enabler's attachment. **Resolved 2026-09-03 (human)**:
  keep both copies — do **not** delete the `product/` originals (§"Decision taken (human)").
- `src/test/resources/reference-data/` holds the same sample plus deliberately malformed variants
  for the failure-safety IT.
- **OD-EN004-3 resolved 2026-09-03 (human)**: the **curated `instruments.sample.csv`** is the
  intended initial dataset. The full `Yahoo-Finance-Ticker-Symbols.csv` is not committed or
  bootstrap-fetched under EN004; the format-driven ingestion code handles it later if provisioned
  (§"Decision taken (human)").

**Rationale**: enabler §25 (deterministic, offline), §27 (explicit packaging, no host DB), §10
(small representative set incl. `AAPL·XNAS·USD` + an EUR example), §33.1–3 (source & redistribution
open). Classpath resources are the most reproducible mechanism and need no `compose.yaml` change.

**Alternatives considered**:
- *Mount a host directory into the container* — rejected: not reproducible across machines/CI;
  fights EN002's "no host dependency".
- *Download on startup* — rejected for EN004: violates "no Internet in tests/E2E" and adds failure
  modes; a future enabler can add a controlled bootstrap fetch.
- *Seed via a Flyway `V3.x` data migration* — rejected: the enabler wants a **normalizing
  ingestion path** (§12), not hand-written INSERTs; Flyway owns *schema*, the importer owns *data*.

---

## D11 — Import entry point (OD-EN004-14)

**Decision**: `infrastructure.config.ReferenceDataBootstrapRunner implements ApplicationRunner`,
guarded by `@ConditionalOnProperty("app.reference-data.import-on-startup", havingValue="true",
matchIfMissing=true)`. On boot it calls `ImportReferenceDataService.run(...)` with the configured
classpath files, then logs the `ImportReport` summary. Any exception is caught, logged as an error
with the source id, and swallowed — **the application still starts** and serves whatever catalog
already exists. `@SpringBootTest` slices set `app.reference-data.import-on-startup=false` and drive
the import explicitly (or via a small `@TestConfiguration`).

**Rationale**: enabler §14 (manual/on-demand, no scheduler), §27 (works in the container),
§22 (fail safe — a bad bootstrap must not take the platform down). An `ApplicationRunner` is the
lightest "on-demand at deploy time" mechanism; the same business op is unit/IT-testable directly.

**Alternatives considered**:
- *Spring Boot CLI sub-command / separate `main`* — heavier; the container would need a second
  entrypoint or a one-shot job service in `compose.yaml` (scope creep — FR-030).
- *Actuator custom endpoint to trigger import* — a nice future addition but adds a management
  surface EN004 doesn't need; revisit if operators ask for re-import without a restart.
- *`@PostConstruct` on a bean* — runs too early (before the datasource/Flyway are guaranteed
  ready); `ApplicationRunner` runs after context refresh.

---

## D12 — CSV parsing (OD-EN004-15)

**Decision**: `org.apache.commons:commons-csv` (Apache-2.0, no transitive deps), used **only** in
`infrastructure.reference.csv.CsvReferenceFileReader`. Version via an explicit `<dependency>`
(not BOM-managed) pinned to the current 1.x. ArchUnit forbids `org.apache.commons.csv..` outside
`..financialinstrument.infrastructure..`.

**Rationale**: the mapping CSVs and the Yahoo file have quoted fields, embedded commas
(`market_name_or_note`), and a header row — hand-rolling a correct RFC 4180 parser is error-prone.
`commons-csv` is tiny and universally vetted. CLAUDE.md §9 governs *major* libraries; this is a
leaf utility, isolated and reversible.

**Alternatives considered**:
- *JDK-only `String.split(",")`* — rejected: breaks on the quoted note fields already present in
  `yahoo-exchange-to-mic-mapping.csv`.
- *Jackson `jackson-dataformat-csv`* — heavier, pulls a Jackson module; no upside here.
- *Keep it JDK-only with a hand-written quoted-field splitter* — viable fallback if the team wants
  zero new deps; ~40 lines, must be unit-tested for quotes/escapes. Recorded as the fallback.

---

## D13 — Fixture identifiers (OD-EN004-17)

**Decision**: `ListingId` = deterministic **UUID v5** (SHA-1 name-based) over the string
`"<TICKER>|<MIC>"` with a fixed namespace UUID constant in the fixture/import code. Markets use the
MIC as the id directly.

**Rationale**: the sample CSV stays human-editable (no id column); the same `(ticker, MIC)` always
yields the same UUID on every machine and run → reproducible fixtures, stable FD002 test
assertions, and a natural idempotency key that also survives a DB wipe + re-import.

**Alternatives considered**:
- *Random `UUID.randomUUID()` at insert* — non-reproducible; fixture tests would have to query by
  `(ticker, MIC)` anyway.
- *An explicit `id` column in the sample CSV* — makes the CSV noisier and invites copy-paste id
  collisions.
- *Sequential surrogate* — not portable across environments.

---

## D14 — ArchUnit additions (OD-EN004-18)

**Decision**: extend `com.myfinaimanager.core.architecture.StandardArchitectureRulesTest`:

- The existing `..core.(*).domain.. !→ ..core.(*).business..` / `!→ ..infrastructure..` and
  `..core.(*).business.. !→ ..infrastructure..` rules already cover `financialinstrument` (the
  `(*)` module wildcard) — verify non-vacuous by asserting the module contributes classes.
- Add `no classes in ..financialinstrument.domain.. should depend on org.apache.commons.. or
  com.opencsv.. or ..infrastructure..`.
- Add: `@Entity` classes in `..financialinstrument..` reside in
  `..infrastructure.persistence.entity..`; Spring Data `Repository` interfaces in
  `..infrastructure.persistence.repository..`; `@RestController` in `..infrastructure.api.rest..`;
  `*Mapper` classes in `..infrastructure.api.rest.mapper..` or `..infrastructure.persistence.mapper..`;
  classes using `org.apache.commons.csv..` reside in `..infrastructure..`.
- Deliberate-violation check in tasks: (a) a `domain → infrastructure` import, (b) a
  `commons-csv` import in `business.normalization` → both fail → revert → green.

**Rationale**: AR-061 / ADR-003 "Architecture Verification"; keeps one architecture-test file for
the whole `core-service` (the module wildcard was designed for exactly this).

**Alternatives considered**: a separate `FinancialInstrumentArchitectureRulesTest` — rejected:
duplicates the shared rules; the `(*)` pattern already generalizes.

---

## D15 — JaCoCo, coverage & the branch-heavy normalizer (OD-EN004-19)

**Decision**: add `com/myfinaimanager/core/financialinstrument/infrastructure/config/**` and
`…/infrastructure/persistence/entity/**` to the JaCoCo `<excludes>` (consistent with `portfolio`).
`business.normalization.**`, mappers, adapters, sources, and business services stay **in** coverage.
The normalizer is developed test-first with one test per decision branch, which naturally clears
the ≥ 90 % line **and** branch gate for the module; the bundle gate (existing) stays green.

**Rationale**: entities and `@Configuration`/`ApplicationRunner` wiring are declarative
(testing-strategy "trivial framework bootstrap / pure configuration wiring" are the sanctioned
exclusion categories). The normalization logic is the opposite — it is the thing under test.

**Alternatives considered**: exclude the `reference` adapters — rejected: they are exercised by the
`YahooCsvInstrumentSourceIT` and carry real branching (quarantine mapping); keep them measured.

---

## D16 — application.yml additions

**Decision**: under a new `app:` root (no `spring.*` pollution):

```yaml
app:
  reference-data:
    import-on-startup: true          # false in test slices
    markets-file: "classpath:reference-data/markets.csv"
    instruments-file: "classpath:reference-data/instruments.sample.csv"
    exchange-mic-mapping-file: "classpath:reference-data/yahoo-exchange-to-mic-mapping.csv"
    suffix-override-file: "classpath:reference-data/yahoo-exchange-suffix-overrides.csv"
```

Bound via a `@ConfigurationProperties("app.reference-data")` record in `infrastructure.config`.
No secrets. The `spring.jpa` / datasource / flyway / logging blocks are unchanged.

**Rationale**: externalized configuration (DR-032); lets a future real Yahoo file be pointed at
without code change; `classpath:` keeps it reproducible.

---

## D17 — Verification approach (feeds quickstart.md)

**Decision**: EN004's own gates (the FD002 E2E is the downstream product gate, VC-020):
1. `./mvnw -B clean verify` — normalizer unit tests (TDD, all worked cases + rejection reasons),
   Testcontainers ITs (catalog search, upsert idempotency, FK, schema integrity, end-to-end import
   over the sample CSV with asserted `ImportReport` counts, failure-safety rollback), the
   `GET /api/financial-instruments` contract test, ArchUnit (extended, non-vacuous), JaCoCo ≥ 90 %.
2. ArchUnit deliberate-violation red→green (domain→infra; commons-csv in business).
3. `./start.sh` → container boots, `ReferenceDataBootstrapRunner` logs the import summary,
   `curl "localhost:4200/api/financial-instruments?query=AAPL"` returns the Apple listing; a
   lower-case query and a partial-name query work; an inactive / non-EUR-USD fixture row is absent;
   a blank `query` → 400 `application/problem+json`. `./stop.sh` clean.
4. `./e2e.sh` — the existing FD001/smoke E2E still green (no regression); FD002's E2E is added by
   FD002 and consumes this catalog offline.
5. `git diff` scope check — no `product/` edit, no `V1`/`V2` change, no `portfolio` behavior change,
   no frontend/compose/lifecycle change, no new deployable.

---

## Open items

| Item | Owner | Status |
|---|---|---|
| Enabler §34 Human Approval | **Human** | ✅ **Approved 2026-09-03** (§"Decision taken (human)"); authoritative `product/…/EN004-….md` synced — `Status: Approved`, §34 signed (jaruiz). |
| OD-EN004-3 — Yahoo source-file provisioning | **Human** | ✅ **Curated `instruments.sample.csv`** (2026-09-03). Full CSV not committed/fetched under EN004. |
| OD-EN004-20 — delete the `product/` mapping-CSV copies? | **Human** | ✅ **Keep both** (2026-09-03). |
| `pg_trgm` index for name search | Tasks | Add only if the committed dataset shows plain `ilike` is too slow. |
| `limit`/pagination on search | Tasks | Add a sane default (`50`) in the contract if FD002 typeahead needs it; not a blocker. |

### Decision taken (human)
- enabler approval checklist: approved.
- full Yahoo CSV vs curated `instruments.sample.csv` vs bootstrap fetch: curated sample.
- delete the `product/` mapping-CSV copies after the implementation copies land: not deleting.
