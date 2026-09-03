# core-service

The single Spring Boot deployable for My-FinAI-Manager (ADR-001 — one coarse-grained service with
explicit internal module boundaries, **not** one service per domain).

## Build & test

Use the bundled Maven wrapper — **no host Maven installation is required**:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"   # for the Testcontainers ITs

./mvnw test        # unit / component + ArchUnit (fast, no Docker)
./mvnw verify      # + Failsafe integration tests (Testcontainers PostgreSQL) + contract test + JaCoCo gate (≥ 90 % line & branch)
```

Java 21. The wrapper is pinned to Maven 3.9.x (`.mvn/wrapper/maven-wrapper.properties`). The
container image is built from this project by `implementation/platform/start.sh` / `e2e.sh` (the
`Dockerfile` build stage runs `./mvnw`).

## Architecture (ADR-003 — Standard Spring Backend Architecture)

Every functional module under `com.myfinaimanager.core` is organised into three areas with
dependencies pointing **inward** — `infrastructure → business → domain`:

```
com.myfinaimanager.core
└── portfolio/                       ← functional module (reference implementation)
    ├── domain/
    │   ├── model/                   value objects, entities, aggregate root (Portfolio, Position, Money, …)
    │   ├── ports/                   outbound port interfaces (PortfolioRepository, DefaultInvestorProvider)
    │   └── exceptions/              domain-observable failures (PortfolioValidationException, PortfolioNotSavedException)
    ├── business/                    use-case API + orchestration (CreatePortfolioUseCase, CreatePortfolioService @Service)
    └── infrastructure/
        ├── api/
        │   └── rest/                @RestController + @RestControllerAdvice
        │       ├── dto/             request/response records (JSON shape = OpenAPI contract)
        │       └── mapper/          transport ⇄ business mapping (@Component)
        ├── persistence/             PortfolioPersistenceAdapter implements domain.ports.PortfolioRepository
        │   ├── entity/              @Entity — JPA mapping only (infrastructure-only; the domain has no persistence annotations)
        │   ├── repository/          Spring Data repositories (derived queries only)
        │   └── mapper/              domain ⇄ entity mapping
        └── config/                  @Configuration (module beans, e.g. Clock)
```

Enforced by `StandardArchitectureRulesTest` (ArchUnit): the domain never depends on Spring, JPA,
Hibernate, Jackson, `java.sql`, or Kafka; `@Entity` classes stay in `infrastructure.persistence.entity`;
Spring Data repositories in `infrastructure.persistence.repository`; `@RestController` in
`infrastructure.api.rest`; REST DTOs and mappers are never referenced from `domain` / `business`;
Apache Commons CSV is confined to `infrastructure`. The rules are module-wildcarded, so a new
functional module (e.g. `financialinstrument`) inherits enforcement automatically.

### Relational persistence — Spring Data JPA

`product/architecture/technology-policy.md` makes Spring Data JPA the default relational-persistence
abstraction. The `portfolio` module:

- `domain.ports.PortfolioRepository` — the outbound port (domain-owned, framework-free).
- `infrastructure.persistence.PortfolioPersistenceAdapter` — implements the port; writes the whole
  aggregate in one transaction (`saveAndFlush`), resolves an idempotency-key race by re-reading in
  a fresh transaction, and translates any other integrity/transient failure to
  `PortfolioNotSavedException`.
- `infrastructure.persistence.repository.PortfolioJpaRepository` — `JpaRepository`, derived queries
  only (`findByIdempotencyKey` with `@EntityGraph`). No JPQL, no native SQL.
- `infrastructure.persistence.entity.{PortfolioEntity,PositionEntity,InvestorEntity}` — JPA mapping
  onto the **existing** tables. Decimal columns declare no `precision`/`scale` so the investor's
  exact input scale round-trips.
- `infrastructure.persistence.mapper.PortfolioPersistenceMapper` — explicit domain ⇄ entity mapping.

**Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto` is `none`; Hibernate never creates or
alters a table (`SchemaIntegrityIT` asserts this). Schema changes are forward Flyway migrations
under `src/main/resources/db/migration/` (`V3__financial_instrument.sql` adds the EN004 tables).

## `financialinstrument` module (EN004 — Financial Instrument reference data)

A second functional module, sibling of `portfolio`, following the same ADR-003 layout. It owns the
local **Financial Instrument catalog** that FD002 searches for controlled instrument selection.

- **Domain** — `Market` (identity = ISO 10383 MIC) and `FinancialInstrumentListing` (identity =
  `ticker + market`; stable `ListingId` derived deterministically from that pair). `SupportedCurrency`
  is an enum (`EUR`, `USD`). Value objects `Mic` / `Ticker` / `Isin` validate shape only.
- **Mapping-driven normalization** (`business.normalization`) — `YahooSymbolNormalizer` turns a raw
  provider symbol + exchange code into a canonical `(ticker, MIC, currency)` **only** via the two
  mapping tables (`ExchangeMicTable`, `SuffixOverrideTable`); there is **no** generic "strip after
  the last dot". Every unmapped / ambiguous / unsupported row is rejected with one of eight
  `RejectionReason`s and counted, never guessed.
- **Ingestion** (`business.ImportReferenceDataService`) — one run = one transaction: markets first,
  then each instrument row (normalize → verify the MIC has a Market row → upsert on natural
  identity). A hard failure (unreadable/corrupt source, DB error) rolls the whole run back and
  raises `ReferenceDataImportException` with the partial report; per-row rejections never fail the
  run; an omitted row is never delisted. Seven counters:
  `processed, imported, updated, skippedUnsupportedCurrency, skippedUnsupportedMarket,
  quarantinedAmbiguous, quarantinedInvalid`.
- **Sources** (`infrastructure.reference.*`) — read the committed CSVs under
  `src/main/resources/reference-data/`: `markets.csv`, the curated Yahoo-shape
  `instruments.sample.csv`, and the two mapping CSVs. CSV parsing (Apache Commons CSV) is confined
  to `infrastructure.reference.csv` and ArchUnit-fenced.
- **Startup import** — `ReferenceDataBootstrapRunner` (an `ApplicationRunner`) runs one import on
  boot, guarded by `app.reference-data.import-on-startup` (`true` by default; `false` in test
  slices). It is idempotent — re-running on every boot converges with zero new rows. A failure is
  logged (`event=ReferenceDataImportFailed`) and swallowed: the app still starts and serves the
  existing catalog.
- **Search API** — `GET /api/financial-instruments?query=` returns active EUR/USD listings whose
  ticker matches exactly (case-insensitive) or whose name contains the query; exact-ticker hits
  first. A blank query is `400 application/problem+json` (`/problems/invalid-search-query`). The
  response is provider-neutral — no `providerSymbol`, source exchange, `operatingMic`,
  `instrumentType`, or `source*` field is ever exposed.

Configuration lives under `app.reference-data.*` in `application.yml` — all values are
`classpath:` locations of committed files; no secret, no host path.

## Tests

| Location | Kind |
|----------|------|
| `…/domain/model/*Test`, `…/business/*Test` | unit — deterministic domain / orchestration logic |
| `architecture/StandardArchitectureRulesTest` | ArchUnit — ADR-003 boundaries |
| `…/infrastructure/api/rest/*ContractTest` | contract — live payloads vs `openapi.yaml` (`@WebMvcTest`) |
| `…/infrastructure/persistence/*IT` | integration — Spring Data JPA adapter vs real PostgreSQL (Testcontainers) |
| `portfolio/*IT`, `financialinstrument/*IT`, `bootstrap/PlatformIntegrationIT` | full-slice integration (`@SpringBootTest` + Testcontainers) |
| `financialinstrument/…/ReferenceDataFailureSafetyIT`, `ReferenceDataUpsertAdapterIT` | import rollback-safety + idempotency (Testcontainers) |

Integration tests use Testcontainers (singleton container — `support/PostgresContainerSupport`);
no manually installed database is needed.
