# Quickstart / Validation — EN004 Establish Financial Instrument Reference Data

Runnable scenarios that prove EN004 delivers a local, provider-neutral, offline-capable catalog
sufficient for FD002. Each maps to a Verification Criterion (`VC-001 … VC-020`). Design detail is
in [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), and
[contracts/](./contracts/) — not duplicated here.

## Prerequisites

- **Docker + Docker Compose v2** (the platform + E2E run in containers — EN002).
- JDK 21 to run the backend build locally (the Maven wrapper provides Maven).
- `TESTCONTAINERS_RYUK_DISABLED=true` and `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`
  on this machine (see the `local-dev-environment` note).
- `implementation/platform/infrastructure/local/.env` present (`cp .env.example .env`).
- **No Internet access is required** for any EN004 test or for the FD002 E2E — that is the point.

---

## A. Backend build & tests — `./mvnw verify`  →  VC-013, VC-014, VC-015, VC-017, and most others

```bash
cd implementation/platform/backend/core-service
export JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -B clean verify
```

**Expected**:

- **Surefire** (unit): `YahooSymbolNormalizerTest` (TDD — every worked example + every
  `RejectionReason`), `SearchFinancialInstrumentsServiceTest`, plus the extended
  `StandardArchitectureRulesTest`. All green.
- **`StandardArchitectureRulesTest`** now also enforces, for `financialinstrument`:
  `domain !→ business`, `domain !→ infrastructure`, `business !→ infrastructure`; `@Entity` only in
  `..infrastructure.persistence.entity..`; Spring Data repos only in `..persistence.repository..`;
  `@RestController` only in `..infrastructure.api.rest..`; `*Mapper` only in
  `..api.rest.mapper..`/`..persistence.mapper..`; `org.apache.commons.csv..` only under
  `..infrastructure..`; `financialinstrument.domain` depends on no `org.apache.commons..`. Rules
  are **non-vacuous** (the module contributes classes in all three areas). — **VC-017**
- **Failsafe** (Testcontainers PostgreSQL): `FinancialInstrumentCatalogAdapterIT`,
  `ReferenceDataUpsertAdapterIT`, `ReferenceDataSchemaIntegrityIT`, `YahooCsvInstrumentSourceIT`,
  `ReferenceDataFailureSafetyIT` — all green. The existing `portfolio` ITs stay green. — **VC-015**
- **`ReferenceDataSchemaIntegrityIT`**: `flyway_schema_history` contains version `3` (successful);
  `fin_instr_identity_uk`, the `currency`/`isin`/`ticker` CHECKs, and the `market_mic` FK exist in
  `information_schema`; the `market` / `financial_instrument` column sets are exactly what
  `V3__financial_instrument.sql` created — Hibernate (`ddl-auto: none`) altered nothing. — **VC-014**
- **JaCoCo `check`**: ≥ 90 % line **and** branch (bundle), with `financialinstrument` config +
  entity packages excluded (consistent with `portfolio`).
- **Contract test** `FinancialInstrumentSearchContractTest` (`@WebMvcTest` +
  `swagger-request-validator`) validates the live `GET /api/financial-instruments` payloads against
  the **updated** `openapi.yaml`. — **VC-020 (partial)**

---

## B. Deterministic normalization — no generic dot-strip  →  VC-005, VC-010 (SC-004)

`YahooSymbolNormalizerTest` asserts (contracts/reference-mapping.md §3 matrix):

| input | expected |
|---|---|
| `SAN.MC` + `MCE` | `SAN · XMAD · EUR`, `providerSymbol = SAN.MC` |
| `AAPL` + `NMS` | `AAPL · XNAS · USD` |
| `ADS.DE` + `FRA` | `ADS · XETR · EUR` (suffix override) |
| `VOD.L` + `LSE` | rejected `UNSUPPORTED_CURRENCY` |
| `XYZ.NX` + `ENX` | rejected `AMBIGUOUS_EXCHANGE` |
| `FOO.XX` + `MCE` | rejected `SUFFIX_MISMATCH` |
| `.MC` + `MCE` | rejected `EMPTY_TICKER` |
| `BAR` + `EUX` | rejected `NOT_SUPPORTED_FOR_FD002` |

```bash
cd implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/financialinstrument
# no generic "strip after last dot" anywhere:
! grep -rInE 'substring\([^)]*lastIndexOf\("\."\)|split\("\\\\."\)|replaceAll\("\\\\\..*"' .
```

**Expected**: the grep guard passes (no generic suffix truncation); the normalizer only strips a
suffix present in an override or an exchange rule's `expected_yahoo_suffix`. — **VC-005; SC-004**

---

## C. ArchUnit fails on a real violation  →  VC-017 (SC-005)

```bash
cd implementation/platform/backend/core-service
# (a) add an infrastructure import to a financialinstrument.domain class -> verify -> MUST fail
# (b) add `import org.apache.commons.csv.CSVRecord;` to a business.normalization class -> MUST fail
./mvnw -B -q -Dtest=StandardArchitectureRulesTest test        # fails on each
git checkout -- src/main/java/.../financialinstrument/         # revert
./mvnw -B -q -Dtest=StandardArchitectureRulesTest test        # green again
```

**Expected**: a clear `domain → infrastructure` violation and a `commons-csv outside infrastructure`
violation, each green after revert. — **VC-017**

---

## D. Local catalog persistence & search  →  VC-001, VC-003, VC-006, VC-007, VC-008, VC-009, VC-013

Covered by `FinancialInstrumentCatalogAdapterIT` + `ReferenceDataUpsertAdapterIT` in step A, on
real PostgreSQL:

- a `Market` and a `FinancialInstrumentListing` persist and read back exactly (MIC, ticker,
  currency, `active`, optional ISIN) — **VC-001, VC-003**;
- `search("aapl")` and `search("AAPL")` both return the Apple `XNAS`/`USD` listing — **VC-006, VC-007**;
- `search("santan")` returns the Santander listing(s) by name (case-insensitive substring) — **VC-008**;
- an **inactive** fixture listing and a **non-EUR/USD** fixture listing are **absent** from default
  search results — **VC-006, VC-009**;
- every result has a `ticker`, a `market` MIC that exists in the `market` table, and an EUR/USD
  `currency` — **VC-009**;
- the search path issues no outbound HTTP (no provider adapter on the request path) — **VC-006, SC-007**;
- the domain `Market` / `FinancialInstrumentListing` types carry no JPA annotation — **VC-013**.

---

## E. Provider-neutral ingestion, idempotency & failure safety  →  VC-002, VC-004, VC-010, VC-011, VC-012, VC-016

`YahooCsvInstrumentSourceIT` runs the full import over
`src/test/resources/reference-data/instruments.sample.csv` (+ the two mapping CSVs):

- Markets load first from `markets.csv`; MICs are ISO 10383 — **VC-002**;
- accepted listings resolve to EUR or USD only — **VC-004**;
- the persisted `providerSymbol` retains the raw Yahoo symbol; no Yahoo/CSV type appears in
  `domain` or the REST DTO — **VC-010**;
- the returned `ImportReport.counters` match the fixture exactly, e.g.
  `processed = 15, imported = 10, updated = 0, skippedUnsupportedCurrency = 1 (a GBP LSE row),
  quarantinedAmbiguous = 1 (an ENX/NX row), quarantinedInvalid = 1 (a suffix-mismatch row)`;
- running the import **again** → `imported = 0`, all `updated`, **zero** duplicate `market` or
  `financial_instrument` rows (unique `(ticker, market_mic)`) — **VC-011**;
- `ReferenceDataFailureSafetyIT`: an import over a fixture with one corrupt record (unparseable
  line / DB error injected) → `ReferenceDataImportException`, the whole run rolls back, the
  previously loaded catalog is **fully intact**, and the diagnostics name the offending record — **VC-012**;
- all fixtures are committed; the ITs need no Internet — **VC-016**.

---

## F. Containerized runtime + the FD002-facing endpoint  →  VC-006, VC-018, VC-020

```bash
cd implementation/platform
BUILD=1 ./start.sh
# the backend logs the reference-data import summary on startup, e.g.:
#   event="ReferenceDataImportCompleted" source="YAHOO_CSV" processed=15 imported=10 updated=0 ...
docker compose -f infrastructure/local/compose.yaml logs backend | grep ReferenceDataImport

curl -s "http://localhost:4200/api/financial-instruments?query=AAPL" | jq .
curl -s "http://localhost:4200/api/financial-instruments?query=aapl" | jq '.[0].ticker'   # -> "AAPL"
curl -s "http://localhost:4200/api/financial-instruments?query=santan" | jq '.[].ticker'
curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:4200/api/financial-instruments?query=%20%20"   # -> 400
curl -s "http://localhost:4200/api/financial-instruments?query=NOSUCHTHING"                # -> []
```

**Expected**:

- `./start.sh` brings the platform up **healthy** (existing behavior) with the catalog populated by
  `ReferenceDataBootstrapRunner` using the **existing PostgreSQL container** — no host DB, no manual
  SQL. — **VC-018**
- `GET /api/financial-instruments?query=AAPL` → `200` `[{ id, name, ticker: "AAPL", market: "XNAS",
  currency: "USD", active: true }]` — **no** `providerSymbol` / `source` / `operatingMic` /
  `instrumentType` / `externalReference` in the JSON. — **VC-010, VC-020**
- lower-case and partial-name queries work; a blank query → `400 application/problem+json`
  (`type: /problems/invalid-search-query`); no match → `200 []`.
- container restart (`./stop.sh` then `./start.sh`) re-runs the import with **zero** new rows. — **VC-011**
- `./stop.sh` clean.

```bash
./e2e.sh    # FD001 + smoke still green — EN004 adds no frontend and no regression
```

FD002 adds its own Playwright journey (search → select → controlled ticker/market/currency → save),
which consumes this catalog **offline**. — **VC-020**

---

## G. No product / Portfolio change  →  VC-019 (SC-011)

```bash
git diff --stat
git diff -- product/          # → only the 2026-09-03 EN004 enabler-header approval sync; no other product/ change
git diff -- implementation/platform/backend/core-service/src/main/resources/db/migration/V1__baseline.sql \
            implementation/platform/backend/core-service/src/main/resources/db/migration/V2__portfolio.sql   # → empty
git diff -- implementation/platform/frontend/ implementation/platform/infrastructure/local/compose.yaml \
            implementation/platform/start.sh implementation/platform/stop.sh implementation/platform/e2e.sh   # → empty
grep -rn "financialinstrument" implementation/platform/backend/core-service/src/main/java/com/myfinaimanager/core/portfolio/   # → empty
```

**Expected**: changes are limited to the new `financialinstrument` module, `V3__financial_instrument.sql`,
`pom.xml` (+`commons-csv`, JaCoCo excludes), `application.yml` (`app.reference-data.*`),
`openapi.yaml` (+one operation), `StandardArchitectureRulesTest` (+rules), the new reference CSVs,
and the backend `README`. **No** Portfolio/Position code change, **no** `V1`/`V2` edit, **no**
FD001 identity change, **no** frontend / compose / lifecycle-script change, **no** new deployable /
messaging / scheduler / search engine / persistence technology. The only `product/` change is the
2026-09-03 enabler-header approval sync (no change to any `product/` intent). — **VC-019**

---

## Verification Criteria coverage

| VC | Scenario(s) |
|---|---|
| VC-001 Local Market Catalog in PostgreSQL | A, D |
| VC-002 ISO 10383 MIC | A (schema IT), E |
| VC-003 Local Instrument Catalog in PostgreSQL | A, D |
| VC-004 EUR/USD | E |
| VC-005 Normalized `ticker + market` identity | B, E |
| VC-006 Local runtime search, no external provider | D, F |
| VC-007 Search by ticker | D, F |
| VC-008 Search by name | D, F |
| VC-009 Valid ticker + Market + Currency in results | D |
| VC-010 No provider payload types in domain / contracts | B, E, F (curl), ArchUnit (A) |
| VC-011 Repeatable import, no duplicates | E, F (restart) |
| VC-012 Failure safety | E (`ReferenceDataFailureSafetyIT`) |
| VC-013 Spring Data JPA, domain JPA-free | A, D |
| VC-014 Flyway | A (`ReferenceDataSchemaIntegrityIT`) |
| VC-015 Testcontainers | A |
| VC-016 Deterministic offline fixtures | A, E, F |
| VC-017 ArchUnit | A, C |
| VC-018 Containerized runtime | F |
| VC-019 No Portfolio change | G |
| VC-020 FD002 ready | A (contract), D, F, and ultimately FD002's E2E |

## Verification run — 2026-09-03

| VC | Result | Concrete evidence |
|---|---|---|
| VC-001 | PASS | `V3` `market` table; `ReferenceDataSchemaIntegrityIT`, `ReferenceDataPersistenceRoundTripIT` green; container start-up populates 14 markets |
| VC-002 | PASS | `Mic` shape validation + `market_mic_shape_chk`; `the_constraints_en004_depends_on_exist` green |
| VC-003 | PASS | `V3` `financial_instrument` table; container start-up log `imported=21` |
| VC-004 | PASS | `SupportedCurrency` enum + `fin_instr_currency_chk CHECK (currency IN ('EUR','USD'))`; `VOD.L/LSE` → `skippedUnsupportedCurrency` |
| VC-005 | PASS | `FinancialInstrumentListing.identity()` = `Ticker+Mic`; `ListingId.deterministic`; `a_second_full_import…keeps_ids_stable` green |
| VC-006 | PASS | `curl ?query=AAPL` via nginx returns from local DB; no outbound call — search runs against `FinancialInstrumentJpaRepository` only |
| VC-007 | PASS | `search("AAPL")`==`search("aapl")`; `FinancialInstrumentCatalogAdapterIT.search_by_ticker_is_case_insensitive` |
| VC-008 | PASS | `curl ?query=santander` → Banco Santander; `search_by_name_is_a_case_insensitive_substring` |
| VC-009 | PASS | every result carries `ticker` + `market` (MIC) + `currency`; contract test + curl |
| VC-010 | PASS | contract test asserts no `providerSymbol`/`source`/`instrumentType`/`externalReference`; ArchUnit keeps provider concepts out of `domain`/`business` transport; curl body confirms |
| VC-011 | PASS | 2nd container boot log `imported=0 updated=21`; `ReferenceDataUpsertAdapterIT` |
| VC-012 | PASS | `ReferenceDataFailureSafetyIT` — corrupt CSV → `ReferenceDataImportException`, row count unchanged; missing file → hard failure, nothing written |
| VC-013 | PASS | Spring Data JPA adapters; `domain_has_no_framework_dependencies` + `domain_does_not_use_spring_data_or_jpa` green |
| VC-014 | PASS | `flyway_history_contains_the_v3_migration_and_it_succeeded` |
| VC-015 | PASS | all `*IT` extend `PostgresContainerSupport` (Testcontainers singleton PostgreSQL 16) |
| VC-016 | PASS | committed `markets.csv` + `instruments.sample.csv` + mapping CSVs; same counters on every run (`processed=27 imported=21 skip 1/2 quarantine 1/2`) |
| VC-017 | PASS | `StandardArchitectureRulesTest` 12/12; T055 deliberate-violation check fails 2 rules → revert → green |
| VC-018 | PASS | `BUILD=1 ./start.sh` healthy; import runs inside the backend container; `./stop.sh` clean |
| VC-019 | PASS | no `portfolio` production-code change; `V1`/`V2` untouched; frontend / compose / lifecycle scripts untouched; only `product/` change = the pre-approved enabler-header sync |
| VC-020 | PASS | `GET /api/financial-instruments` in `openapi.yaml`; contract test green; curl returns selectable listings; `./e2e.sh` → 2 passed exit 0 (no FD001 regression) |

`./mvnw -B clean verify` — Surefire **107** + Failsafe **51**, 0F/0E; JaCoCo bundle
**line 96.25 % · branch 91.37 %**.
