# Data Model — EN004 (Phase 1)

EN004 introduces the `financialinstrument` module's **reference** data. It adds:

- **domain** model types (provider-neutral, framework-free) under `financialinstrument.domain.model`;
- **infrastructure-only JPA entities** mapping those types to the new `market` / `financial_instrument`
  tables;
- the **normalization data flow** that turns provider/source rows into canonical domain objects.

No Portfolio/Position entity changes. `product/definition/global/information-model.md` is
unaffected (the "Financial Instrument" and "Market" *information concepts* it already lists are
implemented here as reference data — introduced by FD002 §10, not by EN004).

---

## 1. Domain model (`financialinstrument.domain.model`)

| Type | Kind | Fields | Rules |
|---|---|---|---|
| `Mic` | value object | `value: String` | `^[A-Z0-9]{4}$`; upper-cased + trimmed on construction |
| `Ticker` | value object | `value: String` | non-blank; ≤ 20 chars; upper-cased + trimmed |
| `Isin` | value object | `value: String` | `^[A-Z]{2}[A-Z0-9]{9}[0-9]$` |
| `SupportedCurrency` | enum | `EUR`, `USD` | the only values accepted by EN004 / FD002 |
| `InstrumentType` | enum | `EQUITY`, `ETF`, `OTHER` | best-effort from source; never a search filter |
| `InstrumentIdentity` | record | `ticker: Ticker`, `market: Mic` | value equality = FD001 Position identity (`ticker + market`) |
| `Provenance` | record | `source: String`, `sourceReference: Optional<String>`, `lastImportedAt: Optional<Instant>` | operational metadata only; never in a public DTO |
| `Market` | reference entity | `mic: Mic`, `name: String`, `country: Optional<String>` (ISO 3166-1 α-2), `operatingMic: Optional<Mic>`, `active: boolean`, `provenance: Provenance` | identity: `mic`; `create(...)` validates, `reconstitute(...)` rehydrates |
| `FinancialInstrumentListing` | reference entity | `id: ListingId` (UUID), `name: String` (non-blank, ≤ 200), `identity: InstrumentIdentity`, `currency: SupportedCurrency`, `isin: Optional<Isin>`, `externalReference: Optional<String>`, `instrumentType: Optional<InstrumentType>`, `providerSymbol: Optional<String>`, `active: boolean`, `provenance: Provenance` | identity: `identity` (`ticker + market`); a listing is a **selectable listing**, not a provider payload |
| `ListingId` | value object | `value: UUID` | `deterministic(ticker, mic)` → UUID v5 over `"<TICKER>\|<MIC>"`; `of(uuid)` for rehydration |
| `NewMarket` | raw carrier | all-`String` (`mic, name, countryIso2?, operatingMic?, active?`) | parsed + validated by `Market.fromRaw(...)` |
| `NewListing` | raw carrier | all-`String` (`name, ticker, mic, currency, isin?, externalReference?, instrumentType?, providerSymbol?, active?`) | parsed + validated by `FinancialInstrumentListing.fromRaw(...)` |
| `ImportReport` | record | `source: String`, `startedAt/finishedAt: Instant`, `counters: ImportCounters`, `rejections: List<Rejection>` | returned by the import business op; not persisted, not exposed |
| `ImportCounters` | record | `processed, imported, updated, skippedUnsupportedCurrency, skippedUnsupportedMarket, quarantinedAmbiguous, quarantinedInvalid: int` | exactly matches the decision doc §"Import Validation" counters |
| `Rejection` | record | `rawSymbol: String`, `sourceExchangeCode: String`, `reason: RejectionReason`, `detail: String` | for actionable diagnostics |
| `RejectionReason` | enum | `NO_MAPPING`, `NOT_SUPPORTED_FOR_FD002`, `UNSUPPORTED_CURRENCY`, `SUFFIX_MISMATCH`, `AMBIGUOUS_EXCHANGE`, `MIC_UNRESOLVED`, `EMPTY_TICKER`, `IDENTITY_CONFLICT` | maps to the counter to increment |

Domain exceptions (`financialinstrument.domain.exceptions`): `ReferenceDataImportException` (a hard
run failure — I/O, parser, DB — carries the partial `ImportReport`), `InvalidSearchQueryException`
(blank/missing search query → HTTP 400).

**Ports** (`financialinstrument.domain.ports`) — see [contracts/catalog-ports.md](./contracts/catalog-ports.md):
`FinancialInstrumentCatalog` (search), `MarketCatalog` (lookup by MIC set), `ReferenceCatalogWriter`
(upsert Markets + listings). Provider-neutral types only — no JPA entity, no CSV record, no HTTP DTO.

---

## 2. Normalization data flow (Phase 0 D6)

```text
CSV row (Ticker, Category Name, Exchange)          ← infrastructure.reference.instrument
        │  CsvReferenceFileReader (commons-csv)
        ▼
RawInstrumentRow(rawSymbol, categoryName, exchangeCode)
        │  YahooSymbolNormalizer.normalize(rawSymbol, exchangeCode)     ← business.normalization (deterministic, TDD)
        │     uses ExchangeMicMapping + SuffixOverrideTable (loaded from the 2 mapping CSVs)
        ▼
NormalizationResult
  ├─ Accepted(providerSymbol, ticker, mic, currency)
  │        │  + name (from categoryName), instrumentType (best-effort)
  │        ▼
  │   NewListing  → FinancialInstrumentListing.fromRaw(...)  → ReferenceCatalogWriter.upsert(...)
  │
  └─ Rejected(reason, detail)  → ImportCounters[reason]++ , Rejection added to report  (never persisted)
```

Markets flow is simpler: `markets.csv` row → `NewMarket` → `Market.fromRaw(...)` →
`ReferenceCatalogWriter.upsertMarket(...)`. Markets are imported **before** instruments so the FK
and the `MIC_UNRESOLVED` / `skippedUnsupportedMarket` checks have data.

---

## 3. `Market` ⇄ `MarketEntity` ⇄ `market` table

| Domain (`Market`) | JPA (`MarketEntity`) | Column (`market`) | Preserve |
|---|---|---|---|
| `mic : Mic` | `@Id String mic` | `mic CHAR(4) PK`, `CHECK mic ~ '^[A-Z0-9]{4}$'` | natural key, assigned; trimmed on read |
| `name : String` | `@Column(nullable=false) String name` | `name TEXT NOT NULL` | verbatim |
| `country : Optional<String>` | `@Column(name="country_iso2") String countryIso2` | `country_iso2 CHAR(2)` | `null` ⇔ empty |
| `operatingMic : Optional<Mic>` | `@Column(name="operating_mic") String operatingMic` | `operating_mic CHAR(4)` | reference metadata only |
| `active : boolean` | `@Column(nullable=false) boolean active` | `active BOOLEAN NOT NULL DEFAULT true` | |
| `provenance.source` | `@Column String source` | `source TEXT` | e.g. `ISO10383_CSV` |
| `provenance.sourceReference` | `@Column(name="source_reference") String sourceReference` | `source_reference TEXT` | raw row key |
| `provenance.lastImportedAt` | `@Column(name="last_imported_at") Instant lastImportedAt` | `last_imported_at TIMESTAMPTZ` | run timestamp; UTC (`hibernate.jdbc.time_zone`) |

---

## 4. `FinancialInstrumentListing` ⇄ `FinancialInstrumentEntity` ⇄ `financial_instrument` table

| Domain (`FinancialInstrumentListing`) | JPA (`FinancialInstrumentEntity`) | Column (`financial_instrument`) | Preserve |
|---|---|---|---|
| `id : ListingId` (UUID) | `@Id UUID id` | `id UUID PK` | deterministic UUID v5 over `ticker\|mic` (D13) |
| `name : String` | `@Column(nullable=false) String name` | `name TEXT NOT NULL` | verbatim |
| `identity.ticker : Ticker` | `@Column(nullable=false) String ticker` | `ticker TEXT NOT NULL`, `CHECK length(btrim) 1..20` | upper/trim by the domain |
| `identity.market : Mic` | `@Column(name="market_mic", nullable=false) String marketMic` | `market_mic CHAR(4) NOT NULL REFERENCES market(mic)` | FK — every listed MIC is a known Market (VC-002/VC-009). **No `@ManyToOne`** (D3/D4) |
| `currency : SupportedCurrency` | `@Column(nullable=false) String currency` | `currency CHAR(3) NOT NULL`, `CHECK currency IN ('EUR','USD')` | `String` + mapper ↔ enum |
| `isin : Optional<Isin>` | `@Column String isin` | `isin CHAR(12)`, `CHECK isin IS NULL OR isin ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'` | `null` ⇔ `Optional.empty()` |
| `externalReference : Optional<String>` | `@Column(name="external_reference") String externalReference` | `external_reference TEXT` | `null` ⇔ empty |
| `instrumentType : Optional<InstrumentType>` | `@Column(name="instrument_type") String instrumentType` | `instrument_type TEXT`, `CHECK … IN ('EQUITY','ETF','OTHER')` | descriptive; **never** a search filter (FR-040) |
| `providerSymbol : Optional<String>` | `@Column(name="provider_symbol") String providerSymbol` | `provider_symbol TEXT` | raw Yahoo symbol, e.g. `SAN.MC` — **source metadata, never in the REST DTO** (FR-028) |
| `active : boolean` | `@Column(nullable=false) boolean active` | `active BOOLEAN NOT NULL DEFAULT true` | FD002 offers only `active = true` |
| `provenance.*` | `source` / `source_reference` / `last_imported_at` columns | same | e.g. `source = YAHOO_CSV`, `source_reference = SAN.MC` |

**Identity / uniqueness**: `UNIQUE (ticker, market_mic)` = the FD001 `ticker + market` Position
identity (enabler §7; FR-006). ISIN is additional info, **not** identity.

**Indexes**: `upper(ticker)` (exact-ticker search); `(active, currency)` (FD002 default filter).
A `pg_trgm` GIN index on `name` is added later only if needed (D5).

---

## 5. Persistence adapter behavior (contract — see contracts/catalog-ports.md)

| Port method | Adapter behavior | Preserved outcome |
|---|---|---|
| `List<FinancialInstrumentListing> search(String query)` | `FinancialInstrumentJpaRepository.search(query)` → `mapper.toDomain` per row | active + EUR/USD only; exact-ticker first, then name matches, `ticker ASC`; empty list when nothing matches (VC-006–VC-009) |
| `Map<Mic,Market> marketsByMic(Set<Mic>)` | `MarketJpaRepository.findAllByMicIn` → `toDomain` | used by the import (`MIC_UNRESOLVED`) and to enrich results if needed |
| `UpsertResult upsertMarket(Market)` | `findByMic` → update mutable fields \| insert | idempotent on `mic`; re-run ⇒ `updated` |
| `UpsertResult upsertListing(FinancialInstrumentListing)` | `findByTickerIgnoreCaseAndMarketMic` → update \| insert (`id` from D13) | idempotent on `(ticker, market_mic)`; re-run ⇒ `updated`, 0 duplicates (VC-011) |
| whole `run(...)` | wrapped in one `TransactionTemplate` | hard failure ⇒ full rollback, prior catalog intact (VC-012); per-row rejections don't fail the run |

---

## 6. Configuration model (deltas only)

| File | Change |
|---|---|
| `pom.xml` | **+** `org.apache.commons:commons-csv` (explicit version); JaCoCo `<excludes>` **+=** `…/financialinstrument/infrastructure/config/**`, `…/infrastructure/persistence/entity/**` |
| `application.yml` | **+** `app.reference-data.{import-on-startup, markets-file, instruments-file, exchange-mic-mapping-file, suffix-override-file}` (no secrets). `spring.*` blocks unchanged |
| `db/migration/V3__financial_instrument.sql` | **NEW** — `market` + `financial_instrument` (D3). `V1`/`V2` untouched |
| `src/main/resources/reference-data/*.csv` | **NEW** — `markets.csv`, `instruments.sample.csv`, `yahoo-exchange-to-mic-mapping.csv` (copied), `yahoo-exchange-suffix-overrides.csv` (copied) |
| `openapi.yaml` | **+** `GET /api/financial-instruments` operation + `FinancialInstrument` / `FinancialInstrumentList` / (reused) `ValidationProblem` schemas — OpenAPI **3.0.3** |
| `StandardArchitectureRulesTest` | **+** module placement + `commons-csv`-containment rules (D14) |
| frontend / `compose.yaml` / `start.sh` / `stop.sh` / `e2e.sh` | **UNCHANGED** (nginx already proxies `/api/`) |

**Invariant**: no secret in any file; the datasource stays env-driven; Hibernate never creates or
alters a table (`ddl-auto: none`); `domain` imports no framework/CSV/provider type.

---

## 7. State / behavior

No new state machine. A `Market` and a `FinancialInstrumentListing` are `active` or `inactive`;
EN004 only ever creates `active` rows from the supported source rows and **never** auto-inactivates
on omission (enabler §15). FD001 Position semantics and the `ticker + market` identity are
unchanged (VC-019). The only new observable surface is `GET /api/financial-instruments?query=…`
(read-only) and the startup import log line.
