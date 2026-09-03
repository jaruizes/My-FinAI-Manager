# Implementation Plan: Establish Financial Instrument Reference Data (EN004)

**Branch**: `EN004-establish-financial-instrument-reference-data` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/EN004-establish-financial-instrument-reference-data/spec.md`

**Authoritative enabler**: `product/definition/enablers/EN004-establish-financial-instrument-reference-data/EN004-establish-financial-instrument-reference-data.md` (**Status: Approved** — §34 signed by jaruiz, 2026-09-03; decision record in `research.md` §"Decision taken (human)").
**Supporting decision**: `product/definition/enablers/EN004-establish-financial-instrument-reference-data/EN004-yahoo-normalization-decision.md`.
**Governing ADR**: `product/architecture/adrs/ADR-003-standard-spring-backend-architecture.md` (Approved; amended 2026-09-02 for `infrastructure.api.rest.mapper`). ADR-001 (single `core-service` deployable) unchanged. **No new ADR** — EN004 adds a functional module and a Flyway schema inside the existing approved topology (ADR-003 explicitly names `financialinstrument` as an example module).

> **Approval status — cleared 2026-09-03.** The human approved the enabler (`product/…/EN004-….md`
> `Status: Approved`, §34 signed) and both data decisions (`research.md` §"Decision taken (human)"):
> (a) enabler approved; (b) OD-EN004-3 → the curated `instruments.sample.csv` is the initial dataset;
> (c) OD-EN004-20 → keep the `product/` mapping-CSV copies. `/speckit-implement` is unblocked.

## Summary

EN004 adds a new **`com.myfinaimanager.core.financialinstrument`** module to `core-service`, built
to ADR-003, that gives the platform a **local, provider-neutral catalog** of Markets and Financial
Instrument listings so **FD002** can replace free-text `ticker` / `market` / `currency` entry with
controlled selection — with the user-facing flow never touching an external provider at request
time.

1. **Module + schema** — `financialinstrument/{domain/{model,ports,exceptions}, business, infrastructure/{api/rest[/dto,/mapper], persistence/{entity,repository,mapper}, reference/{market,instrument}, config}}`; a new Flyway migration `V3__financial_instrument.sql` creating `market` and `financial_instrument` (Spring Data JPA, domain JPA-free, `ddl-auto: none`).
2. **Provider-neutral ingestion** — an `infrastructure.reference` adapter reads committed CSV fixtures (an ISO 10383 – compatible Markets file and a Yahoo-format instrument file), a **deterministic mapping-driven normalizer** (TDD'd — this is new domain/business logic) resolves canonical `ticker` + MIC + currency through the two committed mapping CSVs, and a business `ImportReferenceData` operation upserts the catalog. Every unsupported / ambiguous / mapping-mismatch row is **skipped or quarantined and counted** — never guessed, never generically dot-stripped.
3. **Local search** — a `FinancialInstrumentCatalog` business operation + a Spring Data query over local PostgreSQL: case-insensitive by ticker or name, default filter `active = true AND currency ∈ {EUR, USD}`.
4. **One REST endpoint** — `GET /api/financial-instruments?query=…` added contract-first to `openapi.yaml` (OpenAPI 3.0.3, RFC 9457 errors), business-field DTO only (no `providerSymbol` / provider `Exchange` / mapping internals). `GET /api/markets` is **out of scope** (spec FR-027).
5. **Deterministic & containerized** — reference CSVs packaged as classpath resources; import runs on container startup behind a flag (idempotent); Testcontainers ITs; committed fixtures cover `AAPL·XNAS·USD` + EUR examples; FD002's E2E can run offline.
6. **Enforcement** — ArchUnit rules extended for the new module (`domain !→ infrastructure`, `business !→ infrastructure`, JPA/CSV/provider types only in `infrastructure`, adapter placement); non-vacuous; deliberate-violation check. `./mvnw verify` green incl. the existing `portfolio` suites and the ≥ 90 % coverage gate.

**No** Portfolio/Position behavior change, no FD001 identity change, no new deployable / messaging /
scheduler / search engine / persistence technology, no Java/Spring major-version change, no edit to
a human-governed `product/` document.

## Technical Context

**Language / Runtime**: Java 21, Spring Boot **3.5.6** (unchanged). Bash (lifecycle scripts, unchanged).

**Primary Dependencies**:
- **Unchanged / reused**: `spring-boot-starter-web`, `-actuator`, `-data-jpa` (Hibernate ORM 6.x), `flyway-core` + `flyway-database-postgresql`, `postgresql` driver, `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers` (junit-jupiter + postgresql), `archunit-junit5` **1.3.0**, `swagger-request-validator-mockmvc` **2.44.1**, JaCoCo gate (≥ 90 % line + branch), the Maven wrapper (`./mvnw`, Maven 3.9.11).
- **Added (minor, `infrastructure`-only)**: `org.apache.commons:commons-csv` (Apache-2.0, zero transitive deps) for CSV parsing — OD-EN004-15. JDK-only parsing is the fallback if a zero-new-dependency stance is preferred; the choice is isolated behind the reference adapter and reversible.

**Storage**: PostgreSQL 16. **New** Flyway migration `V3__financial_instrument.sql` → `market` + `financial_instrument` tables (owned by the `financialinstrument` module — AR-020). The `portfolio` schema (`V1`, `V2`) and its migrations are **not touched**. Hibernate `ddl-auto: none` (unchanged global setting).

**Testing**: `./mvnw verify` — Surefire (`*Test`) + Failsafe (`*IT`, Testcontainers PostgreSQL, `api.version=1.44`) + JaCoCo `check` + ArchUnit. New: normalization unit tests (TDD), catalog + import Testcontainers ITs, a `GET /api/financial-instruments` contract test (`@WebMvcTest` + `swagger-request-validator`), a schema-integrity IT. The **FD002** Playwright E2E is the downstream product proof (VC-020) — EN004 delivers the offline catalog data it needs.

**Target Platform**: Local developer workstation + the EN002 containerized platform. `core-service` stays one Spring Boot deployable (ADR-001). No cloud, no CI, no scheduler.

**Project Type**: Cumulative platform under `implementation/platform/` — a new backend functional module inside the existing `core-service`, plus one OpenAPI operation. No frontend change (nginx already proxies `/api/` to the backend, path preserved).

**Performance Goals**: none quantified. Search is over a small-to-moderate reference dataset (hundreds–low thousands of rows) — plain PostgreSQL `ILIKE`; a `pg_trgm` index is added only if the committed dataset shows it is needed (OD-EN004-9). Container startup import must complete in a few seconds over the committed fixtures.

**Constraints**:
- ADR-003 is prescriptive (module-first three-area layout, `infrastructure → business → domain`, Spring Data JPA, Maven, ArchUnit). Only EN004 §33 items are open (OD table below).
- `domain` free of Spring / Spring Data / JPA / Hibernate / HTTP-servlet / JDBC / CSV-library / provider SDKs (FR-002; ArchUnit).
- No provider-specific type (`providerSymbol`, Yahoo `Exchange`, mapping row, operating-MIC metadata) in `domain` or in any public REST DTO (FR-028; VC-010).
- Suffix stripping is **mapping-driven only** — a generic "remove everything after the last period" rule is forbidden and must not exist in the codebase (FR-016; SC-004).
- Flyway owns the schema; `spring.jpa.hibernate.ddl-auto: none`; the `portfolio` schema is untouched.
- Runtime search touches only local PostgreSQL — no outbound provider call on the request path (FR-024; VC-006).
- Idempotent import — re-running (incl. on container restart) creates zero duplicates (FR-020; VC-011).
- Fail-safe import — a malformed/unavailable source never deletes or half-corrupts the existing catalog (FR-021; VC-012).
- Deterministic offline tests + FD002 E2E data — no Internet dependency beyond container-image pulls (FR-033, FR-035; VC-016).
- `start.sh` / `stop.sh` / `e2e.sh` / `compose.yaml` interface unchanged (FR-030).
- No edit to a human-governed `product/` document (FR-039).

**Open technical decisions (EN004 §33 + spec A3–A15 — resolved in research.md within ADR-003 bounds; recommended defaults below)**:

| ID | Decision | Recommended default |
|----|----------|---------------------|
| OD-EN004-1 | Module base package + adapter package naming | `com.myfinaimanager.core.financialinstrument.{domain.{model,ports,exceptions}, business, infrastructure.{api.rest, api.rest.dto, api.rest.mapper, persistence.{entity,repository,mapper}, reference.{csv, market, instrument}, config}}` |
| OD-EN004-2 | Market source file | a **committed curated ISO 10383 – compatible subset** `src/main/resources/reference-data/markets.csv` covering every MIC used by the instrument fixtures + FD002 (`XNAS`, `XNYS`, `ARCX`, `XMAD`, `XETR`, `XPAR`, `XAMS`, `XMIL`/`MTAA`, `XLIS`, …). The full official ISO 10383 file is **not** redistributed. |
| OD-EN004-3 | Yahoo instrument source provisioning | **✅ Decided 2026-09-03 (human):** commit a curated deterministic `src/main/resources/reference-data/instruments.sample.csv` (Yahoo `Ticker,Category Name,Exchange` shape) sufficient for FD002 + tests. The full `Yahoo-Finance-Ticker-Symbols.csv` is **not** committed or bootstrap-fetched under EN004; the format-driven ingestion handles it later if provisioned. |
| OD-EN004-4 | Files at build vs runtime | reference CSVs (`markets.csv`, `instruments.sample.csv`, `yahoo-exchange-to-mic-mapping.csv`, `yahoo-exchange-suffix-overrides.csv`) packaged as **classpath resources** under `src/main/resources/reference-data/` — inside the jar/image, no runtime mount. Test-only variants under `src/test/resources/reference-data/`. |
| OD-EN004-5 | Currency modeling | domain **enum / value object** `SupportedCurrency { EUR, USD }` — no currency master table (enabler §6, §33.5). Stored as `CHAR(3)` with a `CHECK (currency IN ('EUR','USD'))` constraint. |
| OD-EN004-6 | `market` table | `market(mic CHAR(4) PK, name TEXT NOT NULL, country_iso2 CHAR(2), operating_mic CHAR(4), active BOOLEAN NOT NULL DEFAULT true, source TEXT, source_reference TEXT, last_imported_at TIMESTAMPTZ)` + `CHECK (mic ~ '^[A-Z0-9]{4}$')`. |
| OD-EN004-7 | `financial_instrument` table | `financial_instrument(id UUID PK, name TEXT NOT NULL, ticker TEXT NOT NULL, market_mic CHAR(4) NOT NULL REFERENCES market(mic), currency CHAR(3) NOT NULL, isin CHAR(12), external_reference TEXT, instrument_type TEXT, provider_symbol TEXT, active BOOLEAN NOT NULL DEFAULT true, source TEXT, source_reference TEXT, last_imported_at TIMESTAMPTZ, CONSTRAINT fin_instr_identity_uk UNIQUE (ticker, market_mic), CONSTRAINT fin_instr_currency_chk CHECK (currency IN ('EUR','USD')), CONSTRAINT fin_instr_isin_chk CHECK (isin IS NULL OR isin ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'), CONSTRAINT fin_instr_ticker_len_chk CHECK (length(btrim(ticker)) BETWEEN 1 AND 20))`. |
| OD-EN004-8 | Instrument ↔ Market association | plain `String marketMic` column on the instrument + DB **FK** to `market(mic)` (referential integrity — every listing's MIC exists as a Market; VC-002/VC-009). JPA: no `@ManyToOne` navigation (no aggregate to traverse — same reasoning as EN003's `investor_id`). The catalog mapper resolves the `Market` when a result needs it. |
| OD-EN004-9 | Search query & name matching | one Spring Data `@Query`: `WHERE active = true AND currency IN ('EUR','USD') AND (upper(ticker) = upper(:q) OR name ILIKE '%' || :q || '%')` ordered by `(upper(ticker) = upper(:q)) DESC, ticker ASC`; plain `ILIKE` (no search engine). Add a `pg_trgm` GIN index on `name` **only if** the committed dataset makes plain `ILIKE` too slow (unlikely at this scale). |
| OD-EN004-10 | Import batch/transaction strategy | **one transaction per import run** (all-or-nothing on a hard failure → previous catalog fully intact). Expected per-row skips/quarantines do **not** fail the run. No staging table for this dataset size. |
| OD-EN004-11 | Idempotency / upsert | Markets upsert on `mic`; instruments upsert on `(ticker, market_mic)` — adapter does `findBy… → update|insert`. Re-run → 0 inserted, all `updated`/`unchanged`. |
| OD-EN004-12 | Inactivation on omission | **none** — an import that omits a previously seen row leaves it unchanged (enabler §15, §33.9). No delisting rule in EN004. |
| OD-EN004-13 | Provenance placement | on the same rows (`source`, `source_reference`, `last_imported_at` columns) — no separate history table (spec A10; enabler §33.10). |
| OD-EN004-14 | Import entry point | a Spring `ApplicationRunner` (`ReferenceDataBootstrapRunner`) guarded by `app.reference-data.import-on-startup` (**true** in the default/local/`docker` profile, **false** under `@SpringBootTest` unless a test opts in). Reads the classpath fixtures, runs the business import operation, logs the diagnostics summary. Import failure logs an error and leaves the catalog unchanged — it does **not** crash the application. No CLI, no scheduler (enabler §14, §33.12). |
| OD-EN004-15 | CSV parsing | `org.apache.commons:commons-csv` in `infrastructure.reference.csv` only. Fallback: JDK `BufferedReader` + a small hand-written splitter (quoted-field aware) if zero new deps is required. |
| OD-EN004-16 | REST contract | `GET /api/financial-instruments?query=<string, required, minLen 1>` → `200 application/json` array of `{ id, name, ticker, market, currency, active, isin? }` (empty array, not 404, when nothing matches); `400 application/problem+json` (`type: /problems/invalid-search-query`) for missing/blank `query`. Added to `openapi.yaml` (3.0.3) + a mirror fragment `contracts/openapi/financial-instruments.search.yaml`. |
| OD-EN004-17 | Fixture identifiers | listing `id` = **deterministic UUID v5** over `"<TICKER>|<MIC>"` (namespace constant in the fixture loader) so the sample CSV stays human-editable and IDs are stable across runs and machines. |
| OD-EN004-18 | ArchUnit | **extend** `StandardArchitectureRulesTest` (it already scans `com.myfinaimanager.core` with `..core.(*)..` module patterns, so the new module inherits the 3 dependency-direction rules + placement rules automatically). **Add**: `*Mapper` under `..financialinstrument.infrastructure..` in `..api.rest.mapper..` or `..persistence.mapper..`; CSV/`org.apache.commons.csv..` types only under `..infrastructure..`; no `..financialinstrument.domain..` dependency on `org.apache.commons..`. Non-vacuous; deliberate-violation check in tasks. |
| OD-EN004-19 | JaCoCo excludes | add `com/myfinaimanager/core/financialinstrument/infrastructure/config/**` and `…/infrastructure/persistence/entity/**` (consistent with the `portfolio` module). Normalization, mappers, adapters, business ops stay **in** coverage. |
| OD-EN004-20 | Mapping-file governance | **copy** `yahoo-exchange-to-mic-mapping.csv` + `yahoo-exchange-suffix-overrides.csv` from `product/definition/enablers/EN004-.../reference-data/` into `src/{main,test}/resources/reference-data/` (the implementation copy is authoritative for runtime). **✅ Decided 2026-09-03 (human):** keep the `product/` originals — do **not** delete them. |

All are safe, reversible, ADR-003-compliant implementation details. All three human decisions
(enabler approval, OD-EN004-3, OD-EN004-20) are **resolved** — see `research.md` §"Decision taken
(human)".

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.0.0**, principles I–VIII.

| # | Principle | Status | Evidence |
|---|---|---|---|
| I | Human-Governed Source of Truth (respect ADRs + technology-policy) | **PASS** | Implements the **human-approved** enabler EN004 (approved 2026-09-03) + the Yahoo normalization decision, within **ADR-003** (governing) and ADR-001 (intact — no new service). Tech is policy-approved: Spring Data JPA (REQUIRED-by-default), Maven (REQUIRED), PostgreSQL (PREFERRED), Flyway (PREFERRED), Testcontainers (REQUIRED for ITs), OpenAPI (REQUIRED for REST), ArchUnit. `commons-csv` is a **minor** `infrastructure` library (CLAUDE.md §9 targets *major* libraries/infra) — recorded as OD-EN004-15, reversible, no policy entry needed. EN004's *implementation* edits no `product/` doc (FR-039); the enabler's §34 approval header was synced by explicit human direction (constitution — "update the authoritative artifact after approval"). |
| II | Definitions/Enablers Are Authoritative Intent | PASS | Every plan element traces to EN004 `FR-001…FR-040` / `VC-001…VC-020`. Scope is the enabler's §3; the 2 spec clarifications (REST surface, instrument types) are recorded in spec §Clarifications. Nothing added beyond the enabler + FD002's stated need. |
| III | Derived Artifacts & Repository Layout | PASS | Design artifacts under `specs/EN004-…/`. All code under `implementation/platform/backend/core-service/`. No new root trees, no CI. Reference CSVs live in `src/main/resources/reference-data/` (implementation), not `product/`. |
| IV | No Invention; Surface Material Ambiguity | PASS | ADR-003 is the authority for structure; the normalization decision is the authority for the algorithm. Residual choices are **technical** (EN004 §33) — OD-EN004-1…20 with defaults, resolved in `research.md`. The items needing a *human* answer (enabler approval; OD-EN004-3 source-file provisioning; OD-EN004-20) were **surfaced, not silently chosen**, and answered by the human 2026-09-03. Any material deviation from ADR-003 / the decision doc → stop and surface (FR-038). |
| V | Technical Enablers Stay Technical | PASS | No investor user stories; US1–US5 are backend-developer / operator / maintainer workflows. No Portfolio/Position behavior added or changed (FR-037; VC-019). The new module is a **vertical slice of the existing platform**, not an isolated app; platform stays executable via `start.sh` / `stop.sh` (EN002 containerized; import runs inside the container). |
| VI | Hexagonal Architecture & Deterministic Logic | PASS | New module follows ADR-003: `infrastructure → business → domain`, ArchUnit-enforced (OD-EN004-18), `domain` framework-free (no CSV/JPA/HTTP/provider types). The **normalization** (suffix stripping, MIC resolution, currency/`supported_for_fd002` filtering, quarantine classification) is **deterministic domain/business logic** — no LLM, fully specified by the decision doc's algorithm. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | **PASS (TDD required)** | The normalization algorithm is **new deterministic logic** ⇒ developed **RED → GREEN → REFACTOR** with behavioral tests per decision-doc example (`SAN.MC/MCE→SAN·XMAD·EUR`, `AAPL/NMS`, `ADS.DE/FRA` override, GBP `LSE` skip, `ENX/NX` quarantine, empty-ticker reject, suffix-mismatch quarantine, `(ticker,MIC)` conflict). Persistence + catalog-search + import-idempotency + failure-safety are **Testcontainers-PostgreSQL** ITs — **never a mocked database** (FR-034; VC-015). |
| VIII | Contract-First External APIs | PASS | `GET /api/financial-instruments` is added to `implementation/platform/contracts/openapi/openapi.yaml` (OpenAPI 3.0.3) **before** the controller; a `swagger-request-validator` contract test guards it. Business-oriented resource; RFC 9457 errors; **no** persistence/provider fields in the DTO (FR-028; VC-010). Business events: none — selecting/searching an instrument does not imply an event (FD002 §11). |

### Development-Workflow / Compliance gates

| Gate | Status | Notes |
|---|---|---|
| Plan includes a Constitution Check | PASS | This section. |
| Completion measured by `definition-of-done.md` | PASS | Carried into `/speckit-tasks`. Coverage gate ≥ 90 % line **and** branch preserved (incl. the `portfolio` module). |
| ADR required for material architecture change | PASS | Adding a functional module + a Flyway schema **within** ADR-001/ADR-003 is explicitly anticipated (ADR-003 "Modular Monolith Structure" shows `financialinstrument`). **No new ADR.** If planning/implementation surfaces a new deployable, messaging, a scheduler, or a new persistence technology → **stop and raise an ADR** (FR-038). |
| Enabler approved | **PASS** | `product/…/EN004-….md` `Status: Approved`, §34 signed by jaruiz 2026-09-03, with OD-EN004-3 and OD-EN004-20 also decided (`research.md` §"Decision taken (human)"). `/speckit-implement` is unblocked. |
| Structured logging; no secrets; externalized config | PASS | Import diagnostics are structured ECS logs (source id, counts) — **no** credentials/tokens (local/public files, no secrets — FR-036). `application.yml` gains only `app.reference-data.*` flags (no secrets). |
| No speculative infrastructure | PASS | Adds one module, one Flyway migration, one OpenAPI operation, and (optionally) `commons-csv`. **No** new service / broker / scheduler / cache / search engine / persistence technology (FR-037; AR-046). |

**Result: PASS.** No constitution violations; the enabler-approval and data-provisioning decisions
are resolved (human, 2026-09-03). Complexity Tracking not required — see the risk register below for
non-blocking watch-items.

### Post-Design Constitution Re-Check (after Phase 1)

Re-evaluated after `research.md`, `data-model.md`, `contracts/`, and `quickstart.md`. **Still PASS**,
no new violations:

- **VI/VII** — the design confirms the normalizer is a pure `business.normalization` unit
  (framework-free, one test per decision branch, TDD); persistence + import + failure-safety are
  Testcontainers ITs; `domain` has zero framework/CSV imports (ArchUnit D14). `commons-csv` is
  confirmed a leaf utility used only in `infrastructure.reference.csv` (D12).
- **VIII** — the one endpoint is fully specified as an OpenAPI 3.0.3 fragment
  (`contracts/openapi/financial-instruments.search.yaml`) with a `swagger-request-validator`
  contract test; the DTO (`data-model.md` §4, `contracts/openapi`) carries only business fields.
- **I/IV** — no new ADR needed (module + `V3` schema inside ADR-001/ADR-003). All human decisions
  (enabler approval; OD-EN004-3 source-file provisioning; OD-EN004-20) are **resolved** (2026-09-03,
  `research.md` §"Decision taken (human)") — surfaced, not silently chosen.
- **III/V** — all artifacts under `specs/EN004-…/`; all code under
  `implementation/platform/backend/core-service/`; the module is a vertical slice; platform stays
  executable and containerized (import runs in the container — D11).

Design gate cleared and the enabler-approval / data-provisioning decisions are resolved → ready for
`/speckit-tasks` and `/speckit-implement`.

## Risk Register *(non-blocking — mitigations in research.md / tasks)*

| Risk | Mitigation |
|---|---|
| The full Yahoo instrument CSV is not in the repo; the real import path is only exercised against a curated sample | OD-EN004-3 (decided 2026-09-03): the curated deterministic `instruments.sample.csv` **is** the initial dataset; the normalizer + adapter are format-driven, so the same code handles the full file if it is provisioned later. Tests + FD002 E2E use only committed data (FR-033). |
| Normalization edge cases (suffix mismatch, `ENX`, ambiguous legacy codes, empty ticker, `(ticker,MIC)` conflict) silently produce wrong catalog rows | TDD every quarantine/skip category from the decision doc; the two mapping CSVs are the **single** source of MIC/currency/`supported_for_fd002`; a generic dot-strip rule is forbidden and checked (SC-004). |
| A listing references a MIC with no `market` row → orphan / broken FK | Import loads **Markets first**, then instruments; an instrument whose canonical MIC has no Market is **quarantined** (`quarantinedInvalid`); DB FK is defense-in-depth. |
| Startup `ApplicationRunner` import slows or breaks container boot | Guarded by `app.reference-data.import-on-startup`; import failure logs + returns (does **not** crash the app — catalog stays as-is); committed fixtures are test-covered so a release build can't ship a malformed fixture; import is fast at this scale. |
| Container restart re-runs the startup import → duplicates | Upsert on natural identity (`mic`, `(ticker, market_mic)`); idempotency IT asserts re-run = 0 inserts (VC-011). |
| ArchUnit rules vacuous for a brand-new module | Rules target `..financialinstrument..` packages explicitly where module-specific; the shared `..core.(*)..` rules already cover it; deliberate-violation check for `domain → infrastructure` **and** a misplaced CSV type. |
| OpenAPI 3.0.3 + `swagger-request-validator` 2.44 quirks (FD001 hit `type:[...]` unions, `allOf`) | Reuse the FD001-settled 3.0.3 patterns: `nullable: true` not `["string","null"]`; flat error schema (no `allOf` + `additionalProperties`); `Idempotency-Key`-style header pitfalls N/A (no header here). |
| Coverage gate ≥ 90 % branch with branch-heavy normalization code | TDD gives natural branch coverage of the quarantine paths; entities + config excluded (OD-EN004-19); the normalizer's every decision branch has a named test. |
| Reference mapping CSVs now exist in two places (`product/` + implementation) | OD-EN004-20 (decided 2026-09-03): implementation copy is authoritative for runtime; the `product/` copies are **kept** as the enabler attachment. |
| `financialinstrument` module beans not component-scanned | `@SpringBootApplication` scan root is `com.myfinaimanager.core` — already covers the new module; a small `FinancialInstrumentModuleConfiguration` (`@Configuration`) holds any explicit beans. |

## Project Structure

### Documentation (this feature)

```text
specs/EN004-establish-financial-instrument-reference-data/
├── plan.md              # This file
├── spec.md              # Feature specification (+ §Clarifications)
├── research.md          # Phase 0 — D1…Dn resolving OD-EN004-1…20 + normalization/JPA/import/ArchUnit patterns
├── data-model.md        # Phase 1 — domain model + domain↔JPA-entity↔table mapping + normalization data flow
├── contracts/
│   ├── openapi/
│   │   └── financial-instruments.search.yaml   # mirror of the GET /api/financial-instruments operation added to openapi.yaml
│   ├── catalog-ports.md                        # domain ports (search / import) + invariants the adapters must preserve
│   └── reference-mapping.md                    # the two Yahoo mapping-CSV schemas + normalization contract (from the decision doc)
├── quickstart.md        # Phase 1 — validation scenarios mapped to VC-001…VC-020
├── checklists/
│   └── requirements.md  # spec quality checklist (16/16)
└── tasks.md             # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root) — target after implementation

```text
implementation/platform/
├── contracts/openapi/openapi.yaml                 # + GET /api/financial-instruments operation + schemas (OpenAPI 3.0.3)
└── backend/core-service/
    ├── pom.xml                                    # + org.apache.commons:commons-csv ; JaCoCo <excludes> += financialinstrument config/entity
    └── src/
        ├── main/
        │   ├── java/com/myfinaimanager/core/financialinstrument/
        │   │   ├── domain/
        │   │   │   ├── model/                      # Market, FinancialInstrumentListing, InstrumentIdentity(ticker+MIC),
        │   │   │   │                               #   Mic, Ticker, SupportedCurrency (enum), IsinRef?, InstrumentType?,
        │   │   │   │                               #   NewMarket / NewListing (raw-normalized carriers), ImportOutcome/ImportReport
        │   │   │   ├── ports/                      # FinancialInstrumentCatalog (search), MarketCatalog,
        │   │   │   │                               #   ReferenceDataImporter (business op) — provider-neutral types only
        │   │   │   └── exceptions/                 # ReferenceDataImportException, InvalidSearchQueryException
        │   │   ├── business/
        │   │   │   ├── SearchFinancialInstrumentsService.java    # @Service — validates query, delegates to catalog port
        │   │   │   ├── ImportReferenceDataService.java           # @Service — orchestrates: parse → normalize → upsert → report
        │   │   │   └── normalization/                            # DETERMINISTIC (TDD): YahooSymbolNormalizer,
        │   │   │                                                 #   MicResolver, SuffixRule, QuarantineReason, ImportCounters
        │   │   └── infrastructure/
        │   │       ├── api/rest/
        │   │       │   ├── FinancialInstrumentSearchController.java   # GET /api/financial-instruments?query=
        │   │       │   ├── FinancialInstrumentExceptionHandler.java   # RFC 9457 problems (or reuse a shared advice)
        │   │       │   ├── dto/FinancialInstrumentResponse.java       # business fields only
        │   │       │   └── mapper/FinancialInstrumentResponseMapper.java
        │   │       ├── persistence/
        │   │       │   ├── entity/{MarketEntity.java, FinancialInstrumentEntity.java}
        │   │       │   ├── repository/{MarketJpaRepository.java, FinancialInstrumentJpaRepository.java}
        │   │       │   ├── mapper/ReferenceDataPersistenceMapper.java
        │   │       │   ├── FinancialInstrumentCatalogAdapter.java     # implements domain.ports.FinancialInstrumentCatalog + MarketCatalog
        │   │       │   └── ReferenceDataUpsertAdapter.java            # implements the persistence side of the importer port
        │   │       ├── reference/
        │   │       │   ├── csv/CsvReferenceFileReader.java            # commons-csv, generic
        │   │       │   ├── market/CsvMarketSource.java                # markets.csv → NewMarket
        │   │       │   ├── instrument/YahooCsvInstrumentSource.java   # instruments.sample.csv + mapping CSVs → normalized NewListing
        │   │       │   └── mapping/{ExchangeMicMapping.java, SuffixOverrideTable.java}   # load the two mapping CSVs (classpath)
        │   │       └── config/
        │   │           ├── FinancialInstrumentModuleConfiguration.java
        │   │           └── ReferenceDataBootstrapRunner.java         # ApplicationRunner, flag-guarded
        │   └── resources/
        │       ├── db/migration/V3__financial_instrument.sql
        │       ├── application.yml                                   # + app.reference-data.{import-on-startup,markets-file,instruments-file}
        │       └── reference-data/
        │           ├── markets.csv
        │           ├── instruments.sample.csv
        │           ├── yahoo-exchange-to-mic-mapping.csv             # copied from product/… (OD-EN004-20)
        │           └── yahoo-exchange-suffix-overrides.csv
        └── test/
            ├── java/com/myfinaimanager/core/financialinstrument/
            │   ├── business/normalization/YahooSymbolNormalizerTest.java  # TDD unit — every example + quarantine category
            │   ├── business/SearchFinancialInstrumentsServiceTest.java
            │   ├── infrastructure/api/rest/FinancialInstrumentSearchContractTest.java  # @WebMvcTest + swagger-request-validator
            │   ├── infrastructure/persistence/
            │   │   ├── FinancialInstrumentCatalogAdapterIT.java       # Testcontainers — search by ticker/name, EUR/USD, active
            │   │   ├── ReferenceDataUpsertAdapterIT.java              # Testcontainers — idempotency, FK, unique (ticker,MIC)
            │   │   └── ReferenceDataSchemaIntegrityIT.java            # Flyway history has V3; constraints exist; Hibernate altered nothing
            │   └── reference/
            │       ├── YahooCsvInstrumentSourceIT.java                # end-to-end import over the sample CSV → catalog + ImportReport counts
            │       └── ReferenceDataFailureSafetyIT.java              # corrupt fixture → run rolls back, prior catalog intact
            └── resources/reference-data/                             # deterministic test fixtures (sample + malformed variants)
```

**Structure Decision**: extend the existing `core-service` module tree established by EN003. The
`financialinstrument` module is a sibling of `portfolio` under `com.myfinaimanager.core`,
module-first with the ADR-003 three areas. The only shared-file touches are: `pom.xml` (add
`commons-csv`, JaCoCo excludes), `application.yml` (add `app.reference-data.*`), `openapi.yaml`
(add one operation), and the shared `StandardArchitectureRulesTest` (extend rules). `V3__financial_instrument.sql`
is a **new** migration file — `V1`/`V2` are untouched. No frontend, `compose.yaml`, or lifecycle-script
change.

## Complexity Tracking

*No constitution violations — this section is not required.* The human decisions it flagged
(enabler approval; OD-EN004-3 source-file provisioning; OD-EN004-20) were resolved by the human on
2026-09-03 (`research.md` §"Decision taken (human)").
