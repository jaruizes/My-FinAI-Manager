# Implementation Plan: Create Investment Portfolio (FD001)

**Branch**: `FD001-create-investment-portfolio` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/FD001-create-investment-portfolio/spec.md`

**Authoritative Feature Definition**: `product/definition/features/FD001_Create_portfolio/FD001_Create_portfolio.md` (Approved — jaruiz, 2026-09-01)
**Governing ADRs**: `ADR-001-initial-backend-topology.md`; `ADR-002-interim-unauthenticated-write-access.md` (Proposed — approve before merge)
**Extends**: the executable platform delivered by `EN001 — Bootstrap Executable Platform`

## Summary

FD001 adds the first product capability to the platform: an Investor creates one Portfolio with a
name and one or more Positions, the input is validated against the Feature Definition's business
rules, and the whole aggregate is persisted atomically under a single platform-seeded default
Investor. Delivered as one vertical slice — Angular creation screen → contract-first REST operation
→ hexagonal `portfolio` capability module inside `core-service` → PostgreSQL (new Flyway migration).
No listing, viewing, editing, valuation, or analysis (FD001 §3). No authentication (deferred).

Technical approach: a new `com.myfinaimanager.core.portfolio` capability package following the
Hexagonal Architecture convention EN001 established (framework-free `domain` + `application`, all
Spring in `adapter` / `bootstrap`). The `Portfolio` aggregate root (with `Position` children and
value objects for money/quantity/identifiers) enforces every business rule in the domain; the
outbound persistence adapter writes the aggregate in one transaction using `JdbcClient` (no ORM,
no new dependency); an idempotency key makes accidental double-submits create exactly one
Portfolio. One new REST operation `POST /api/portfolios` is added to the platform OpenAPI contract.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x / Angular 20 (frontend) — unchanged from EN001 (`research.md` OD-1…OD-4).

**Primary Dependencies**:
- Backend: Spring Boot 3.5.x (Web, Actuator, JDBC), **`JdbcClient`** (from `spring-boot-starter-jdbc`, already present) for persistence, Flyway (new `V2__portfolio.sql`), PostgreSQL driver. **No new runtime dependency.** No JPA / Hibernate / Spring Data.
- Backend tests: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL), ArchUnit, a JSON-schema / OpenAPI request-response validator for the contract test.
- Frontend: Angular 20 reactive forms, Angular Router, Angular `HttpClient`, SCSS design tokens — all already available.

**Storage**: PostgreSQL 16. New tables `investor`, `portfolio`, `position` owned by the `portfolio` module. New Flyway migration `V2__portfolio.sql` (seeds the one default Investor). `NUMERIC` for quantity and price (exact decimal — FR-025 / DR-011). Unique constraint `(portfolio_id, ticker, market)` on `position` as defense-in-depth for BR-004.

**Testing**: `mvn verify` (Surefire unit + Failsafe Testcontainers integration + ArchUnit + contract test), `npm test` (Angular component/service tests). TDD for the domain aggregate and value objects (deterministic business logic — DR-004). JaCoCo coverage gate enabled at ≥ 90% overall (DoD §5), effectively-full branch coverage on the `portfolio.domain` package.

**Target Platform**: Local developer workstation and the EN001 platform (`./implementation/platform/start.sh`). Backend = the existing single `core-service` deployable. Frontend = the existing `frontend/web` Angular app.

**Project Type**: Web application — vertical feature slice extending `implementation/platform/` (frontend + `core-service` + contracts + persistence). No new deployable component (ADR-001).

**Performance Goals**: `POST /api/portfolios` completes in < 1 s p95 under local conditions for a Portfolio of up to ~20 Positions (derived — the spec sets only the user-time target SC-001; not a release gate). No throughput target (single-investor, low volume).

**Constraints**:
- Extend the existing platform only; stay inside `core-service` (ADR-001, CLAUDE.md §5–§8).
- Hexagonal Architecture: `domain` and `application` free of Spring / JDBC / HTTP — enforced by ArchUnit.
- Contract-first: the OpenAPI contract is written/updated before the controller; a contract test guards drift (AR-011, DR-017).
- Deterministic validation lives in the domain, not the controller or SQL (AR-003, DR-004).
- Exact decimal money/quantity — `BigDecimal` end to end, `NUMERIC` in the DB (DR-011).
- Atomic persistence of the whole aggregate (FR-023); nothing partial on any failure.
- No authentication in FD001; the create operation is open, consistent with the EN001 platform (spec A11) — **accepted interim security posture, see Constitution Check**.
- No messaging infrastructure for PortfolioCreated / PositionAdded (FR-032) — recorded as structured log lines.

**Scale/Scope**: 1 new backend capability module (~1 aggregate, ~5 value objects, 1 use case, 1 inbound + 1 outbound adapter), 1 new REST operation, 1 Flyway migration (3 tables + 1 seed row), ~4 new Angular components + 1 frontend service, 1 new route + 1 sidebar entry.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.0.0** (principles I–VIII).

| # | Principle | Status | Evidence |
|---|---|---|---|
| I | Human-Governed Source of Truth | PASS | FD001 is Approved; plan honors ADR-001 and `technology-policy.md`. `ADR-002` (interim unauthenticated write access) was **drafted at the maintainer's request** and sits at **Status: Proposed** — it requires human approval before FD001 merges; no `product/` decision is treated as approved by this plan. |
| II | Definitions/Enablers Are Authoritative Intent | PASS | Every plan element traces to a FD001 business rule, acceptance criterion, explicit product decision, or a recorded spec clarification/assumption. Scope not expanded. |
| III | Derived Artifacts & Repository Layout | PASS | Design artifacts under `specs/FD001-create-investment-portfolio/`; all code under `implementation/platform/`; no new deployable, no alternative root trees. |
| IV | No Invention; Surface Material Ambiguity | PASS | The 3 material ambiguities were resolved via `/speckit-clarify`. Remaining choices (JdbcClient vs Spring Data JDBC, idempotency-key mechanism, no e2e framework) are safe, reversible implementation details recorded in `research.md`. |
| V | Technical Enablers Stay Technical | N/A | FD001 is a product feature, not an enabler. It is delivered as a vertical slice extending the shared platform (FR-035); `start.sh` / `stop.sh` remain valid. |
| VI | Hexagonal Architecture & Deterministic Logic | PASS | New `portfolio` module: `domain` (aggregate + value objects, pure) → `application` (use case + ports, pure) → `adapter` (web + persistence) → wired in `bootstrap`. All business rules are deterministic domain logic; no LLM. ArchUnit rules extended to the new package. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | PASS | Domain aggregate + value objects developed test-first (RED→GREEN→REFACTOR). Persistence, idempotency, migration, and atomic-rollback covered by a Testcontainers PostgreSQL integration test — never a mock. |
| VIII | Contract-First External APIs | PASS | `POST /api/portfolios` added to `implementation/platform/contracts/openapi/openapi.yaml` first, in business language (Portfolio, Position, ticker, market, quantity, currency), RFC 9457 `application/problem+json` errors, no persistence leakage. Contract test enforces conformance. Business events are semantic only — no Kafka/AsyncAPI (FR-032). |

Development-Workflow / Compliance gates:

| Gate | Status | Notes |
|---|---|---|
| Plan includes a Constitution Check | PASS | This section. |
| Completion measured by `definition-of-done.md` | PASS | Carried into `/speckit-tasks`. Coverage gate (≥90%) is **enabled** by this feature (EN001 deferred it until real logic existed). |
| ADR required for material architecture change | PASS (pending ADR-002 approval) | No new deployable / persistence tech / messaging / topology change — no ADR required for those. **`product/architecture/adrs/ADR-002-interim-unauthenticated-write-access.md`** is drafted (Status: Proposed) to record the accepted temporary security posture (spec A11; AR-035/AR-038). **It must be human-approved before the FD001 PR merges** (T065). Not a blocker for planning or implementation. |
| Structured logging; no secrets; externalized config | PASS | PortfolioCreated / PositionAdded emitted as structured (ECS JSON) log lines; DB coordinates via env (EN001 pattern); no secrets. |
| No speculative infrastructure | PASS | No new tech. `JdbcClient` (already present), Flyway (already present), Angular reactive forms (built-in). |

**Security posture — explicit risk (not a violation):** FD001 ships an unauthenticated `POST /api/portfolios` that writes private Portfolio data. This is consistent with the EN001 platform (open endpoints, local execution) and the spec's clarified decision A2 (single default Investor, auth deferred). It is recorded as **security debt** to be closed by the future identity/authentication feature; `ADR-002` (drafted, Status: Proposed) makes the acceptance explicit and time-bound and MUST be human-approved before the FD001 PR merges. FD001 introduces no design that would obstruct adding authentication + per-investor authorization later.

**Result: PASS. No unjustified violations. Complexity Tracking below records the one accepted risk.**

## Project Structure

### Documentation (this feature)

```text
specs/FD001-create-investment-portfolio/
├── plan.md              # This file
├── spec.md              # Feature specification (+ Clarifications)
├── research.md          # Phase 0 — technical decisions
├── data-model.md        # Phase 1 — domain model + persistence schema
├── quickstart.md        # Phase 1 — runnable validation scenarios
├── contracts/
│   └── openapi/
│       └── portfolios.create.yaml   # the POST /api/portfolios operation (merged into the platform contract)
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root) — additions to the existing platform

```text
implementation/platform/
├── contracts/openapi/openapi.yaml         # EDIT: add POST /api/portfolios + Portfolio/Position/Problem schemas
│
├── backend/core-service/
│   ├── pom.xml                            # EDIT: enable JaCoCo coverage gate (≥90%); add OpenAPI-validator test dep
│   └── src/main/java/com/myfinaimanager/core/
│       ├── portfolio/                     # NEW capability module (sibling of the `platform` convention package)
│       │   ├── domain/
│       │   │   ├── Portfolio.java                 # aggregate root — enforces BR-001, BR-002, BR-004
│       │   │   ├── Position.java                  # entity — enforces BR-005, price>0 (A3), date≤today (A4), BR-007
│       │   │   ├── PortfolioName.java             # value object (BR-001, A7)
│       │   │   ├── InstrumentRef.java             # value object: ticker + market (BR-003)
│       │   │   ├── Ticker.java  Market.java       # value objects (Market = ISO 10383 MIC where supplied, FR-015)
│       │   │   ├── Currency.java                  # value object (ISO 4217 format — FR-016)
│       │   │   ├── Quantity.java                  # value object (BigDecimal > 0 — BR-005/FR-025)
│       │   │   ├── Money.java                     # value object (BigDecimal amount + Currency — BR-007/FR-025)
│       │   │   ├── PortfolioStatus.java           # enum { ACTIVE } (A12)
│       │   │   ├── PortfolioId.java  PositionId.java  InvestorId.java
│       │   │   └── PortfolioValidationException.java + Violation.java  # `code` = canonical enum in the OpenAPI contract (FR-024)
│       │   ├── application/
│       │   │   ├── port/in/CreatePortfolioUseCase.java + CreatePortfolioCommand.java + CreatePortfolioResult.java
│       │   │   ├── port/out/PortfolioRepository.java          # save(aggregate), findByIdempotencyKey(key)
│       │   │   ├── port/out/DefaultInvestorProvider.java      # the one seeded Investor
│       │   │   └── CreatePortfolioService.java                # implements the use case — framework-free
│       │   └── adapter/
│       │       ├── in/web/CreatePortfolioController.java      # implements the OpenAPI operation
│       │       ├── in/web/CreatePortfolioRequest.java / Response.java  # web DTOs (not domain)
│       │       ├── in/web/PortfolioExceptionHandler.java      # @RestControllerAdvice → RFC 9457 problem+json
│       │       └── out/persistence/JdbcPortfolioRepository.java  # @Transactional aggregate write via JdbcClient
│       │       └── out/persistence/JdbcDefaultInvestorProvider.java
│       ├── bootstrap/PortfolioBeanConfiguration.java          # wires CreatePortfolioService ↔ ports/adapters
│       └── platform/…                                         # unchanged (EN001 convention packages)
│   └── src/main/resources/db/migration/
│       └── V2__portfolio.sql              # NEW: investor, portfolio, position tables + seed default investor
│   └── src/test/java/com/myfinaimanager/core/
│       ├── portfolio/domain/…                                 # TDD unit tests (aggregate + value objects)
│       ├── portfolio/application/CreatePortfolioServiceTest.java
│       ├── portfolio/adapter/in/web/CreatePortfolioControllerContractTest.java
│       ├── portfolio/adapter/out/persistence/JdbcPortfolioRepositoryIT.java   # Testcontainers
│       ├── portfolio/CreatePortfolioIT.java                   # full-slice Testcontainers (HTTP → DB)
│       └── architecture/HexagonalArchitectureRulesTest.java   # EDIT: rules apply to all core-service capability packages
│
└── frontend/web/src/app/
    ├── app.routes.ts                     # EDIT: add route  portfolios/new → CreatePortfolioPageComponent
    ├── core/layout/sidebar.component.ts  # EDIT: add "Portfolios" nav entry → routes to create screen
    └── portfolio/                        # NEW feature area
        ├── create-portfolio.page.ts           # the screen: name field, positions list, Add Position, Save
        ├── add-position.dialog.ts             # focused dialog/drawer for entering a Position (design-system)
        ├── position-draft-list.component.ts   # draft list with remove / edit (FR-026..028)
        ├── portfolio-api.service.ts           # POST /api/portfolios, Idempotency-Key, maps field errors
        ├── portfolio-creation.models.ts       # frontend view models (mirrors the contract, not the domain)
        └── *.spec.ts                          # component + service tests
```

**Structure Decision**: One new hexagonal capability package `com.myfinaimanager.core.portfolio`,
a sibling of the EN001 `platform` convention package exactly as its `package-info.java` prescribes.
Portfolio Management and the *referenced* Financial Instrument identity (ticker + market) both live
here — FD001 does not manage canonical instrument data, so no separate `financialinstrument`
module is created. The frontend gains a `portfolio/` feature area and one route. The single
`core-service` deployable and `start.sh` / `stop.sh` are unchanged.

## Complexity Tracking

| Item | Why accepted | Simpler alternative rejected because |
|---|---|---|
| Unauthenticated `POST /api/portfolios` for private data | Auth is explicitly out of FD001 scope (spec §Out of Scope, A2, A11); EN001 already runs open locally; blocking FD001 on a full identity feature would stall all product delivery | Adding minimal auth now = inventing an identity model the Feature Definition does not describe (constitution IV). Mitigation: single default Investor, documented security debt, **ADR-002 drafted (Proposed) — approve before merge**, and a design that does not obstruct adding auth later. |
| Server-side idempotency key on create | FR-031a requires that an accidental double-submit creates exactly one Portfolio, and a disabled button alone does not survive a network retry | Client-only guard fails on retry/refresh; a name-uniqueness rule (spec option C, rejected) would contradict FR-004. |
| Value objects (Money, Quantity, Ticker, Market, Currency) instead of raw primitives | DR-010 (avoid primitive obsession where domain meaning matters), DR-011 (safe decimal), and this is the first feature — it sets the domain pattern | Raw `String`/`BigDecimal` fields scatter validation and lose BR-007's "price is in the position currency" invariant-by-construction. |

## Phase 0 — Research

See [research.md](./research.md). All decisions resolved; no `NEEDS CLARIFICATION` remain. Key
decisions: persistence via `JdbcClient` (no ORM, no new dep); domain-owned validation with a
field-level violation list; idempotency key generated by the frontend and enforced by a unique DB
column; RFC 9457 problem details for errors; no browser e2e framework in FD001 (covered by
contract + integration + component tests); ArchUnit rules generalized to all capability packages.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — the `Portfolio` aggregate (root + `Position` children + value
  objects), every invariant mapped to a FD001 rule, and the `investor` / `portfolio` / `position`
  schema with the default-Investor seed.
- [contracts/openapi/portfolios.create.yaml](./contracts/openapi/portfolios.create.yaml) — the
  `POST /api/portfolios` operation: `201` created, `200` idempotency replay (returns the existing
  portfolio, no second one), `400` `ValidationProblem` (field-level `errors[]`), `503` persistence
  failure. RFC 9457 `application/problem+json`. To be merged into
  `implementation/platform/contracts/openapi/openapi.yaml`.
- [quickstart.md](./quickstart.md) — runnable validation scenarios mapped to AC-001…AC-008,
  SC-001…SC-012, and the spec's edge cases.

### Post-Design Constitution Re-Check

Re-evaluated after Phase 1: still **PASS**. The design keeps `domain`/`application` framework-free,
adds no technology beyond what EN001 already carries, keeps the contract in business language, and
localizes all business rules in the domain aggregate. The single accepted risk (unauthenticated
write) is unchanged and tracked; `ADR-002` is drafted (Proposed) and awaiting human approval — it
introduces no new dependency, only records the accepted posture.
