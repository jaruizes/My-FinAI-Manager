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
    │   └── exceptions/              domain-observable failures (PortfolioValidationException, PortfolioNotSavedException, PortfolioNotFoundException)
    ├── domain/events/              in-process domain events (PortfolioCreatedEvent — FD004)
    ├── business/                    use-case API + orchestration (CreatePortfolioUseCase/Service; PortfolioQueryUseCase/Service — FD003; ValuePortfolioUseCase + PortfolioValuationQueryUseCase / PortfolioValuationService — FD004)
    └── infrastructure/
        ├── api/
        │   └── rest/                @RestController + @RestControllerAdvice
        │       ├── dto/             request/response records (JSON shape = OpenAPI contract)
        │       └── mapper/          transport ⇄ business mapping (@Component)
        ├── marketdata/              EnMarketDataGatewayAdapter — the ONLY portfolio class importing ..core.marketdata.. (AR-062, FD004)
        ├── valuation/               PortfolioValuationOnCreationListener — synchronous @EventListener, catch-all (FD004)
        ├── persistence/             PortfolioPersistenceAdapter + PortfolioValuationPersistenceAdapter
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
  only (`findByIdempotencyKey`; FD003 adds `findAllByInvestorIdOrderByCreatedAtDescIdDesc` and
  `findByIdAndInvestorId`, all with `@EntityGraph`). No JPQL, no native SQL.
- **FD003 read path** (read-only): `PortfolioQueryService` resolves the Default Investor and calls
  `PortfolioRepository.findAllByInvestor` / `findByIdForInvestor` (scoping is in the query, never a
  post-filter; the adapter runs them on its `readOnly` transaction template — an IT asserts row
  counts are unchanged). `PortfolioQueryController` (`@RestController`, separate from
  `CreatePortfolioController`) serves `GET /api/portfolios` → `PortfolioSummaryResponse`
  `{id,name,positionCount}` and `GET /api/portfolios/{portfolioId}` → the existing
  `CreatePortfolioResponse` (`Portfolio` schema); the `{portfolioId}` path variable is typed `UUID`
  so a non-UUID segment is Spring's default `400`. `PortfolioNotFoundException` →
  `PortfolioExceptionHandler` `404` `/problems/portfolio-not-found` (the advice's `assignableTypes`
  now covers all three portfolio controllers; the FD001 `400`/`503` handlers are unchanged). No write path.
- **FD004 valuation** (`portfolio` module valuation area): on a genuine create, `CreatePortfolioService`
  publishes an in-process `PortfolioCreatedEvent`; `PortfolioValuationOnCreationListener` (a
  **synchronous** `@EventListener`) then runs `PortfolioValuationService.value(...)` inside a
  catch-all — a valuation failure is logged, a best-effort `FAILED` snapshot is written, and the
  create request still returns `201` (the persisted portfolio is never touched). The service reads
  market data only through `domain.ports.MarketDataGateway` (implemented by
  `infrastructure.marketdata.EnMarketDataGatewayAdapter`, the sole importer of `..core.marketdata..`
  — every `MarketDataException` → `Optional.empty()`), runs the **pure**
  `domain.model.PortfolioValuationCalculator` (`BigDecimal` only; native value, EUR/USD via FX,
  totals, weights, sector allocation, `PENDING`/`COMPLETED`/`PARTIAL`/`FAILED` status), and persists
  the latest snapshot via `PortfolioValuationPersistenceAdapter` (delete-then-insert in one
  transaction — `portfolio_id` is `UNIQUE`; zero writes to `portfolio`/`position`). Read path:
  `PortfolioValuationController` serves `GET /api/portfolios/{portfolioId}/valuation` (a Portfolio
  with no snapshot → an explicit `PENDING` body; unknown id → `404` `/problems/portfolio-not-found`).
  Flyway `V4__portfolio_valuation.sql` owns the three new tables. `+3` ArchUnit rules fence the
  boundary (portfolio domain/business free of `..marketdata..`; `..marketdata..` reached only from
  `infrastructure.marketdata`; no `double`/`float` field in `portfolio.domain`). The FD004
  Portfolio-detail UI (frontend `portfolio-detail.page` + a self-contained `pie-chart.component`,
  no charting library) — two mandatory *Allocation by Ticker* / *Allocation by Sector* pie charts,
  per-Position market price shown with its native currency, sector percentages taken from the
  sector chart's legend (no separate list) — consumes this same `PortfolioValuation` response
  (`nativeMarketValue` still returned, just not rendered); **no backend or API change** for the
  charts or the detail-table trim.
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

## `marketdata` module (EN005 — Finnhub market data integration)

A third functional module, sibling of `portfolio` / `financialinstrument`, ADR-003 layout —
**`domain` + `infrastructure` only** (no `business`: the ports are *outbound* and are consumed by a
future Portfolio-valuation feature, not by this module). It provides a **provider-neutral** backend
capability to obtain external market data, with **Finnhub** as the initial provider, fully isolated
behind adapters.

- **Three independent outbound ports** (`domain.ports`): `MarketDataPort.getLatestPrice`,
  `InstrumentProfilePort.getProfile`, `FxRatePort.getRate(from, to)`. Each is separately
  replaceable (e.g. a future ECB `FxRatePort`) without touching the others.
- **Provider-neutral read models** (`domain.model`): `MarketPrice`, `InstrumentProfile`, `FxRate`,
  `InstrumentIdentifier` (`ticker + MIC + currency`), `SupportedCurrency` (`EUR`/`USD`), `Sector`
  (a classification string or `UNCLASSIFIED` — **never inferred**), `ObservedAtSource`
  (`PROVIDER_TIMESTAMP` / `RETRIEVAL_TIME`). All prices and rates are `BigDecimal`; a missing or
  zero provider value is **never** a valid `0` — it is the capability's `*UnavailableException`.
- **Neutral error model** (`domain.exceptions`): `MarketData/InstrumentProfile/FxRate` `Unavailable`,
  `ProviderRateLimited` (HTTP 429), `ProviderAuthenticationFailed` (401/403),
  `InstrumentNotResolved` (no Finnhub symbol — no call is made), `MarketDataNotConfigured`
  (no API key). No Finnhub HTTP status or DTO ever crosses a port.
- **`infrastructure.finnhub`** — the only place Finnhub concerns live: `FinnhubRestClient` (the
  **sole** `RestClient` user in the module — builds the client with explicit connect/read timeouts,
  sends the API key as the **`X-Finnhub-Token` request header** — never a URL parameter —, calls
  `/quote` · `/stock/profile2` · `/forex/rates`, and translates every outcome to a neutral
  exception); `dto/` (Jackson records); `mapper/`; `resolver/` (`FinnhubSymbolResolver` +
  `finnhub-symbol-map.csv` — US MICs pass through, mapped non-US MICs get a suffix, anything else →
  `InstrumentNotResolved`); `cache/` (`TtlCache` + three `@Primary` decorator ports — cache
  *successful* results only, TTLs `finnhub.cache.*`, and never rewrite a result's `observedAt`).
- **Observability** — one structured `event=FinnhubCall` log record per outbound call
  (`provider`, `operation`, `outcome`, `httpStatusCategory`, `latencyMs`); **no** key, URL, or body.
- **Configuration** — `finnhub.*` in `application.yml`; `api-key: ${FINNHUB_API_KEY:}` (empty
  default), `base-url: ${FINNHUB_BASE_URL:https://finnhub.io/api/v1}`. A blank key ⇒ the integration
  is **disabled**: the app starts normally, logs `event=FinnhubIntegrationDisabled` once, and every
  port call throws `MarketDataNotConfigured`. Unrelated capabilities are unaffected. `compose.yaml`
  forwards `FINNHUB_API_KEY` / `FINNHUB_BASE_URL` from the shell or `infrastructure/local/.env` to
  the backend container, so `./start.sh` (with a real key in `.env`) exercises live Finnhub;
  `./e2e.sh` overrides `FINNHUB_BASE_URL` to a local stub container.
- **No** persistence / Flyway migration, **no** external REST API (`openapi.yaml` untouched), **no**
  frontend, **no** new Maven dependency, **no** LLM, **no** valuation arithmetic (EN005 supplies
  data only). Tests use Spring `MockRestServiceServer` — **no** live Finnhub, no key, no network;
  an opt-in `FinnhubProviderSmokeTest` (`FINNHUB_SMOKE=1`) validates a real key locally.

## `ai` module (EN006 — provider-neutral AI model integration)

A fourth functional module, ADR-003 layout — `domain/{model,ports,exceptions}`, `business`,
`infrastructure/{provider/{local,openai},prompt,guardrails,tokencount,observability,api,config}`.
Provides a **provider-neutral** capability for invoking AI/LLM models. EN006 shipped with **no live
AI provider** (resolved Q1) — FD005 is the first real consumer and added the first real provider
adapter (below); the module remains horizontal infrastructure, no business AI logic of its own.

- **`AiModelPort.generate(AiRequest) → AiResponse`** — the one generic port, now with **two**
  named-bean implementations, resolved **per task** (FD005 research D1): `LocalAiModelAdapter`
  (`@Component("local")` — deterministic, in-process, no network call, no credential; still the
  default for every task) and `OpenAiModelAdapter` (`@Component("openai")` —
  `infrastructure/provider/openai`: `OpenAiRestClient` (own `RestClient`, `Authorization: Bearer`
  header, never a query parameter), `OpenAiChatMapper`, full error translation; blank
  `OPENAI_API_KEY` → `AiProviderNotConfiguredException`, no outbound call). `AiInvocationPolicy`
  resolves `providerId = ai.tasks.<task>.provider` (falling back to `ai.default-provider`) and picks
  the matching bean from a Spring-injected `Map<String, AiModelPort>` — `ai.tasks.portfolio-analysis
  .provider: openai` routes FD005's task there; EN006's own `diagnostic` task is unaffected.
  `PromptService`/`PromptRepositoryPort` were extended the same release so a task's persisted
  `promptId`/`promptVersion` identifies **its own** prompt, not the global one it's layered on
  (`ClasspathPromptRepository` now serves `prompts/tasks/portfolio-analysis-v1.txt` alongside the
  global system prompt).
- **`AiInvocationPolicy`** (`business`, `implements GenerateAiUseCase`) — the single orchestrator:
  task validation → prompt composition (`PromptService` + `PromptRepositoryPort` →
  `ClasspathPromptRepository`, `src/main/resources/prompts/global-system-v1.txt`) → context
  budgeting/redaction (`ContextBudgetService`) → token/cost budget enforcement
  (`TokenCounterPort` → `HeuristicTokenCounter`, `ceil(chars/4)`) → input guardrail
  (`InputGuardrailPort` → `RuleBasedInputGuardrail`) → `AiModelPort.generate` (bounded timeout via
  `CompletableFuture.get`, bounded retry for `AiProviderUnavailable`/`RateLimited` only) → output
  guardrail (`OutputGuardrailPort` → `RuleBasedOutputGuardrail`) → structured-output validation
  (`StructuredOutputValidator`, a **pure domain calculator** — no Jackson, no JSON-schema library) →
  telemetry (`TelemetryPort`/`TelemetryScope` → `AiTelemetryRecorder`, Micrometer Observation +
  MeterRegistry). Every step is unit-tested (RED-first); no live provider is ever required.
- **Eleven provider-neutral exceptions** (`AiException` base) — `AiProviderUnavailable`,
  `RateLimited`, `AuthenticationFailed`, `RequestTooLarge`, `TokenBudgetExceeded`,
  `CostBudgetExceeded`, `Timeout`, `InvalidResponse`, `GuardrailRejected`,
  `StructuredOutputInvalid`, `ConfigurationError`.
- **`AiDiagnosticEndpoint`** — an internal Actuator endpoint (`@Endpoint(id = "aidiagnostic")` →
  `POST /actuator/aidiagnostic`, note the URL is the id lowercased with no separator, per Spring's
  own convention e.g. `threadDump`→`/actuator/threaddump`) that triggers one deterministic
  invocation. **Not** a business API — excluded from `openapi.yaml`, documented in
  `specs/EN006-…/contracts/ai-diagnostic-endpoint.md`.
- **Observability (ADR-004)** — OpenTelemetry export via Spring Boot's native path
  (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` for traces,
  `micrometer-registry-otlp` for metrics — all three are required; the metrics registry is easy to
  miss since traces work without it). `AiTelemetryRecorder` emits the `ai.usecase` → `ai.invocation`
  nested span pair (`gen_ai.*` + `ai.*` attributes) and seven `ai_*` Prometheus metrics — never a raw
  prompt/response body, credential, or user/Portfolio identifier. The local Compose stack
  (`otel-collector`, `jaeger`, `prometheus`, `grafana`) is wired into `./start.sh` / `./stop.sh`;
  Grafana's Prometheus data source and the AI observability dashboard are auto-provisioned
  (`infrastructure/observability/`), zero manual setup.
- **Configuration** — `ai.*` in `application.yml` (`default-provider: local`, token/character/cost
  limits, timeout, retry policy) bound to `AiProperties` (`@ConfigurationProperties`), mapped by
  `AiModuleConfiguration` to the plain `AiInvocationSettings` record `ai.business`/`ai.domain`
  actually depend on (they never see the Spring-annotated type).
- **No** persistence / Flyway migration, **no** external business REST API, **no** frontend, **no**
  LLM provider SDK, **no** valuation arithmetic. Tests: 124 unit tests (incl. every domain-model
  validation branch, the full `AiInvocationPolicy` sequencing/retry/timeout matrix using a
  deterministic local adapter and a test-only failing double, and `AiTelemetryRecorder` against
  Micrometer's `TestObservationRegistry`) plus one full-context `PlatformIntegrationIT` case proving
  real DI wiring end to end — none require network access or a live AI provider.

## `portfolioanalysis` module (FD005 — AI Portfolio Analysis)

A fifth functional module, ADR-003 layout — `domain/{model,ports,exceptions}`, `business`,
`infrastructure/{persistence/{entity,repository,mapper},portfolio,ai,api/rest/{dto,mapper},config}`.
Generates an AI-assisted analysis of a Portfolio from its FD004 valuation, via EN006.

- **Lifecycle** — `PortfolioAnalysis` (`domain.model`): `PENDING → RUNNING → COMPLETED|FAILED`,
  immutable per state snapshot (wither methods `withRunning`/`withCompleted`/`withFailed`); a
  different analysis's row is never touched. `portfolioId` is a plain `UUID` — no `portfolio` type
  reaches this module's domain (see AR-062 below).
- **`PortfolioAnalysisRequestService`** (`implements RequestPortfolioAnalysisUseCase`) —
  `requestAutomatic`/`requestManual` create the `PENDING` row, then hand off to
  `PortfolioAnalysisWorker.runAsync` (a **different** Spring bean, so the `@Async` proxy applies —
  self-invocation would otherwise bypass it). `requestManual` pre-checks for an open request, but
  the **database** is the real guard: `portfolio_analysis_one_open_per_portfolio_uk`, a **partial
  unique index** (`WHERE status IN ('PENDING','RUNNING')`) on `portfolio_id` — a constraint
  violation on insert is translated to `AnalysisAlreadyInProgressException` (409).
- **`PortfolioAnalysisWorker`** (`@Async("portfolioAnalysisExecutor")`) — the whole body is a
  last-resort `catch (Throwable)` (never stuck `RUNNING`): fetch the Portfolio context → build the
  deterministic prompt text (`PortfolioAnalysisContextBuilder`, a pure calculator — no port, no
  network) → if insufficient (no valued position, or the valuation never completed), `FAILED
  (INSUFFICIENT_DATA)` with **zero** AI calls → otherwise call the AI port → terminal state.
  `PortfolioAnalysisAsyncConfiguration` provides the dedicated `ThreadPoolTaskExecutor` (small,
  planning-level sizing — distinct from the HTTP request pool, so a slow analysis never starves
  Portfolio creation/read traffic).
- **Two ACL adapters, each the sole importer of one other module (AR-062):**
  - `infrastructure.portfolio.PortfolioContextGatewayAdapter` — calls
    `portfolio.business.{PortfolioQueryUseCase,PortfolioValuationQueryUseCase}`; translates FD004's
    `Portfolio`/`PortfolioValuation`/`PositionValuation`/`SectorAllocation` into this module's own
    `PortfolioContextSnapshot`. Also the sole home of `PortfolioAnalysisOnCreationListener`
    (`@EventListener(PortfolioCreatedEvent)` — reuses FD004's own creation event; a second,
    independent listener) — it must import `portfolio.domain.events.PortfolioCreatedEvent`, so it
    lives here rather than in `business`.
  - `infrastructure.ai.PortfolioAnalysisAiAdapter` — calls `ai.business.GenerateAiUseCase.generate`
    exactly once (task `portfolio-analysis`, a fixed structured-output schema); maps every
    `AiException` subtype to a normalized `FailureReason` (never a raw provider message).
- **Persistence** — Flyway `V5__portfolio_analysis.sql`: `portfolio_analysis` /
  `portfolio_analysis_insight` / `portfolio_analysis_risk`. No FK into any FD004 table — a snapshot
  is read transiently at generation time, never pinned by a persisted reference (FD004's own
  valuation snapshot is mutable/replaced-in-place).
- **REST** — `GET /api/portfolios/{portfolioId}/analysis/latest` (status `NONE`/`PENDING`/
  `RUNNING`/`COMPLETED`/`FAILED`) and `POST /api/portfolios/{portfolioId}/analysis` (`202` +
  `RequestedPortfolioAnalysisResponse`; `409` on a duplicate open request). Neither response ever
  serializes `provider`/`model`/`promptId`/`promptVersion`/token/cost — that metadata stays in the
  row and in EN006 telemetry only. `PortfolioAnalysisExceptionHandler` is its own
  `@RestControllerAdvice` (scoped to `PortfolioAnalysisController`) rather than widening
  `portfolio`'s own handler — that would make `portfolio` depend on `portfolioanalysis`, backwards.

### Consumed by `portfolio` for FD002 (AR-062)

The `portfolio` module validates that every Position on `POST /api/portfolios` references an active
catalogued listing whose `ticker + market + currency` all match — otherwise the Position is rejected
with `ValidationProblem` code `INSTRUMENT_NOT_IN_CATALOG` and nothing is persisted (FD002 FR-011).

This is the one inter-module dependency (**AR-062** — inter-module reads go through a published
port):

```
portfolio.business.CreatePortfolioService
  └─ portfolio.domain.ports.InstrumentCatalog            (anti-corruption port, portfolio VOs only)
       └─ portfolio.infrastructure.catalog.CatalogInstrumentCatalogAdapter   (the ONLY portfolio→financialinstrument reference)
            └─ financialinstrument.domain.ports.FinancialInstrumentCatalog.findSelectable(ticker, mic)
```

`portfolio.domain` / `portfolio.business` never see a `financialinstrument` type; the currency
match is completed in the adapter. Enforced by `StandardArchitectureRulesTest` (**27 rules** as of
FD005): `portfolio` may reference only `..financialinstrument.domain.ports..` / `..domain.model..`,
only from `portfolio.infrastructure`; Finnhub/Frankfurter/OpenAI `dto` / `client` / `RestClient` /
HTTP / Jackson types are confined to their own provider package (the owning module's domain stays
free of them, and no business module depends on another via anything but its published ports); and
`portfolioanalysis` reads `portfolio` and `ai` each through exactly one dedicated adapter package,
mirroring the `portfolio`↔`marketdata` confinement pair exactly.

## Tests

| Location | Kind |
|----------|------|
| `…/domain/model/*Test`, `…/business/*Test` | unit — deterministic domain / orchestration logic |
| `architecture/StandardArchitectureRulesTest` | ArchUnit — ADR-003 boundaries |
| `…/infrastructure/api/rest/*ContractTest` | contract — live payloads vs `openapi.yaml` (`@WebMvcTest`) |
| `…/infrastructure/persistence/*IT` | integration — Spring Data JPA adapter vs real PostgreSQL (Testcontainers) |
| `portfolio/*IT`, `financialinstrument/*IT`, `bootstrap/PlatformIntegrationIT` | full-slice integration (`@SpringBootTest` + Testcontainers) |
| `financialinstrument/…/ReferenceDataFailureSafetyIT`, `ReferenceDataUpsertAdapterIT` | import rollback-safety + idempotency (Testcontainers) |
| `marketdata/…/finnhub/**Test` | Finnhub adapter/mapper/client/resolver/cache — Spring `MockRestServiceServer` stub (**no** live provider, no key, no network) |
| `marketdata/FinnhubIntegrationIT` | full-context wiring: the `@Primary` caching ports; no key ⇒ `MarketDataNotConfigured` from every port |
| `marketdata/FinnhubProviderSmokeTest` | **opt-in** (`FINNHUB_SMOKE=1` + `FINNHUB_API_KEY`) — live Finnhub; skipped by default / in CI |

Integration tests use Testcontainers (singleton container — `support/PostgresContainerSupport`);
no manually installed database is needed. The `marketdata` provider tests deliberately do **not**
use Testcontainers — Finnhub is a true external provider, stubbed with `MockRestServiceServer`.
