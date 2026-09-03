# Data Model — EN003 (Phase 1)

EN003 introduces **no new business entities**. It (a) **relocates** the existing domain types to
the ADR-003 package layout without modifying them, and (b) adds **infrastructure-only JPA entities**
that map the unchanged `Portfolio` / `Position` aggregate to the existing `portfolio` / `position` /
`investor` tables. The Information Model under `product/definition/global/` is unaffected.

---

## 1. Domain-type relocation map (no code change beyond `package` + imports)

Base package `com.myfinaimanager.core.portfolio`.

| Type (current package) | New package | Notes |
|---|---|---|
| `Portfolio` · `Position` · `NewPosition` *(domain)* | `domain.model` | aggregate root, entity, raw input carrier — **unmodified** |
| `PortfolioId` · `PositionId` · `InvestorId` *(domain)* | `domain.model` | UUID identity value objects |
| `PortfolioName` · `PortfolioStatus` · `Ticker` · `Market` · `Currency` · `Money` · `Quantity` · `InstrumentRef` *(domain)* | `domain.model` | value objects / enum |
| `ValidationCode` · `Violation` *(domain)* | `domain.model` | validation vocabulary (mirrors the OpenAPI `code` enum — unchanged) |
| `PortfolioRepository` *(application.port.out)* | `domain.ports` | outbound persistence port — **signature unchanged** |
| `DefaultInvestorProvider` *(application.port.out)* | `domain.ports` | outbound port — **signature unchanged** |
| `PortfolioValidationException` *(domain)* | `domain.exceptions` | thrown by `Portfolio.create` |
| `PortfolioNotSavedException` *(application.port.out)* | `domain.exceptions` | domain/business-observable persistence-failure outcome (→ HTTP 503) |
| `CreatePortfolioUseCase` · `CreatePortfolioCommand` · `CreatePortfolioResult` *(application.port.in)* | `business` | driving use-case API (research.md D2) |
| `CreatePortfolioService` *(application)* | `business` | gains `@Service`; constructor unchanged |
| `CreatePortfolioController` · `PortfolioExceptionHandler` *(adapter.in.web)* | `infrastructure.api.rest` | controller: map-in → delegate → map-out only |
| `CreatePortfolioRequest` (+`PositionInput`) · `CreatePortfolioResponse` (+`PositionResponse`) *(adapter.in.web)* | `infrastructure.api.rest.dto` | records; JSON representation **byte-for-byte identical** |
| *(controller `toCommand` helper)* | `infrastructure.api.rest.mapper` → `CreatePortfolioRequestMapper` | request → `CreatePortfolioCommand` |
| *(`CreatePortfolioResponse.from`)* | `infrastructure.api.rest.mapper` → `PortfolioResponseMapper` | domain `Portfolio` → response DTO; same `toPlainString()` / `LocalDate::toString` / `null` |
| `JdbcPortfolioRepository` *(adapter.out.persistence)* | **replaced** by `infrastructure.persistence.PortfolioPersistenceAdapter` (JPA) | implements `domain.ports.PortfolioRepository` |
| `JdbcDefaultInvestorProvider` *(adapter.out.persistence)* | **replaced** by `infrastructure.persistence.JpaDefaultInvestorProvider` | implements `domain.ports.DefaultInvestorProvider` |
| `PortfolioBeanConfiguration` *(core.bootstrap)* | **replaced** by `infrastructure.config.PortfolioModuleConfiguration` | `@Bean Clock systemUTC` only |
| `com.myfinaimanager.core.platform.*` | **deleted** | EN001 empty convention placeholders (FR-006) |

**New infrastructure types**: `PortfolioEntity`, `PositionEntity`, `InvestorEntity`
(`infrastructure.persistence.entity`); `PortfolioJpaRepository`, `InvestorJpaRepository`
(`infrastructure.persistence.repository`); `PortfolioPersistenceMapper`
(`infrastructure.persistence.mapper`).

---

## 2. Aggregate ⇄ JPA entity ⇄ table mapping

### 2.1 `Portfolio` (domain aggregate) ⇄ `PortfolioEntity` ⇄ `portfolio` table

| Domain (`Portfolio`) | JPA (`PortfolioEntity`) | Column (`portfolio`) | Preserve |
|---|---|---|---|
| `id : PortfolioId` (UUID) | `@Id UUID id` | `id UUID PK` | identity, assigned (not generated) |
| `investorId : InvestorId` (UUID) | `@Column(name="investor_id", nullable=false) UUID investorId` | `investor_id UUID NOT NULL REFERENCES investor(id)` | FK integrity; **not** a `@ManyToOne` (research D3) |
| `name : PortfolioName` | `@Column(nullable=false) String name` | `name TEXT NOT NULL`, `CHECK length(btrim(name)) BETWEEN 1 AND 120` | stored trimmed (domain already trims); CHECK unchanged |
| `status : PortfolioStatus` (`ACTIVE`) | `@Column(nullable=false) String status` | `status TEXT NOT NULL DEFAULT 'ACTIVE'`, `CHECK status IN ('ACTIVE')` | `String` + mapper `↔` enum; CHECK unchanged |
| *(idempotency key — not on the domain aggregate; passed to `save`)* | `@Column(name="idempotency_key", nullable=false, unique=true) String idempotencyKey` | `idempotency_key TEXT NOT NULL`, `UNIQUE (idempotency_key)` | **NOT NULL UNIQUE**; race handling via `DataIntegrityViolationException` + re-read (research D6) |
| `createdAt : Instant` | `@Column(name="created_at", nullable=false) Instant createdAt` | `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` | set from `clock.instant()` by the domain; entity provides it (insertable); UTC via `hibernate.jdbc.time_zone` |
| `positions : List<Position>` | `@OneToMany(mappedBy="portfolio", cascade=ALL, orphanRemoval=true) @OrderBy("id ASC") List<PositionEntity> positions` | — | aggregate write = one `saveAndFlush`; reconstruction order = `id ASC` (matches JDBC `ORDER BY id`); loaded via `@EntityGraph` |

### 2.2 `Position` (domain) ⇄ `PositionEntity` ⇄ `position` table

| Domain (`Position`) | JPA (`PositionEntity`) | Column (`position`) | Preserve |
|---|---|---|---|
| `id : PositionId` (UUID) | `@Id UUID id` | `id UUID PK` | identity, assigned |
| *(parent)* | `@ManyToOne(fetch=LAZY, optional=false) @JoinColumn(name="portfolio_id", nullable=false) PortfolioEntity portfolio` | `portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE` | back-reference set by the mapper |
| `instrument.ticker : Ticker` | `@Column(nullable=false) String ticker` | `ticker TEXT NOT NULL` | upper-cased/trimmed by the domain |
| `instrument.market : Market` | `@Column(nullable=false) String market` | `market TEXT NOT NULL` | `UNIQUE (portfolio_id, ticker, market)` — defense-in-depth for BR-004, unchanged |
| `quantity : Quantity` (`BigDecimal > 0`) | `@Column(nullable=false) BigDecimal quantity` | `quantity NUMERIC NOT NULL`, `CHECK quantity > 0` | **no `precision`/`scale`** → exact input scale (SC-007); CHECK unchanged |
| `currency : Currency` (3 letters) | `@Column(nullable=false) String currency` | `currency CHAR(3) NOT NULL` | trimmed on read (domain `Currency` trims) |
| `initialPurchaseDate : Optional<LocalDate>` | `@Column(name="initial_purchase_date") LocalDate initialPurchaseDate` (nullable) | `initial_purchase_date DATE`, `CHECK (… IS NULL OR … <= current_date)` | `null` ⇔ `Optional.empty()`; never inferred (FR-019); CHECK unchanged |
| `averagePurchasePrice : Optional<Money>` (amount) | `@Column(name="average_purchase_price") BigDecimal averagePurchasePrice` (nullable) | `average_purchase_price NUMERIC`, `CHECK (… IS NULL OR … > 0)` | `null` ⇔ `Optional.empty()`; exact scale |
| `averagePurchasePrice.currency` (== position currency) | `@Column(name="average_purchase_price_currency") String averagePurchasePriceCurrency` (nullable) | `average_purchase_price_currency CHAR(3)`, `CHECK (… IS NULL OR … = currency)`, `CHECK (price/currency both null or both set)` | mapper sets it to `position.currency().code()` when a price is present, else `null` — keeps both CHECKs satisfied |

### 2.3 `Investor` ⇄ `InvestorEntity` ⇄ `investor` table

| Domain | JPA (`InvestorEntity`) | Column (`investor`) | Preserve |
|---|---|---|---|
| `InvestorId` (read only; FD001 has no Investor aggregate) | `@Id UUID id` | `id UUID PK` | the single seeded row `00000000-…-0001` |
| — | `@Column(name="display_name", nullable=false) String displayName` | `display_name TEXT NOT NULL` | `'Default Investor'` (seed) |
| — | `@Column(name="preferred_currency") String preferredCurrency` (nullable) | `preferred_currency CHAR(3)` | `'EUR'` (seed) |
| — | `@Column(name="created_at", nullable=false) Instant createdAt` | `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` | used for deterministic `findTopByOrderByCreatedAtAscIdAsc` |

`JpaDefaultInvestorProvider.get()` → `investorJpaRepository.findTopByOrderByCreatedAtAscIdAsc()`
→ `InvestorId.of(entity.getId())`, or `IllegalStateException` if the seed is missing (unchanged
behavior).

---

## 3. Persistence adapter behavior (unchanged contract — see `contracts/persistence-port.md`)

| Port method | JPA adapter behavior | Preserved outcome |
|---|---|---|
| `Optional<Portfolio> findByIdempotencyKey(String)` | read-only tx → `portfolioJpaRepository.findByIdempotencyKey(key)` (`@EntityGraph` positions) → `PortfolioPersistenceMapper.toDomain` | same `Optional<Portfolio>` with positions ordered by id |
| `Portfolio save(Portfolio, String key)` | `TransactionTemplate` → `saveAndFlush(mapper.toEntity(portfolio, key))`; on `DataIntegrityViolationException` → re-read by key (fresh tx) → return existing **or** throw `PortfolioNotSavedException`; on other `DataAccessException` → `PortfolioNotSavedException` | 201 create · 200 replay on idempotency race · nothing partial on any failure · 503 on transient failure |

---

## 4. Configuration model (deltas only)

| File | Change |
|---|---|
| `pom.xml` | **+** `spring-boot-starter-data-jpa`; **−** explicit `spring-boot-starter-jdbc`; JaCoCo `<excludes>`: `bootstrap/**` → `portfolio/infrastructure/config/**` |
| `application.yml` | **+** `spring.jpa.{open-in-view: false, hibernate.ddl-auto: none, properties.hibernate.jdbc.time_zone: UTC}` |
| `Dockerfile` | build stage: `COPY .mvn/ mvnw` → `RUN ./mvnw …` (was `mvn …`) |
| `mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.properties` | **NEW** — pinned Maven 3.9.x |
| `db/migration/V1__*.sql`, `V2__portfolio.sql` | **UNCHANGED** (no `V3` expected) |
| `openapi.yaml` | **UNCHANGED** |

**Invariant**: no secret enters any file; the datasource stays env-driven; Hibernate never
creates or alters a table.

---

## 5. State / behavior

No new state machine. The `Portfolio` lifecycle is still "created → ACTIVE" (FD001 A12) — EN003
does not add transitions. The *only* behavioral surface that changes shape is the persistence
**mechanism** (JdbcClient → JPA), and its observable behavior (identity, constraints, atomicity,
idempotency, exact decimals, ordering) is asserted equivalent by the rewritten integration tests
and the FD001 E2E.
