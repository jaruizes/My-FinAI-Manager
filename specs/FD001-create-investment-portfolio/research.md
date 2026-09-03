# Phase 0 Research: Create Investment Portfolio (FD001)

Technology, versions, and platform topology are fixed by EN001 and its `research.md` (Java 21,
Spring Boot 3.5.x, Maven, Angular 20, PostgreSQL 16, Flyway, single `core-service`). This document
records the design decisions specific to FD001. All are safe, reversible implementation details
within the approved technology policy and ADR-001 — none trigger a new ADR (see D8 for the one
recommendation).

---

## D1 — Backend persistence approach for the Portfolio aggregate

- **Decision**: Use Spring's **`JdbcClient`** (available today via `spring-boot-starter-jdbc`,
  already on the classpath from EN001) with hand-written SQL and mapping in the outbound adapter
  `JdbcPortfolioRepository`. No ORM, no Spring Data, **no new dependency**. The whole aggregate
  (Portfolio row + N Position rows) is written inside one `@Transactional` adapter method.
- **Rationale**:
  - The aggregate is small and its shape is stable (Portfolio → Positions, no lazy graphs).
  - Keeps `domain` 100% framework-free (AR-002, DR-013): no `@Id`/`@Table`/`@MappedCollection` on
    domain types and no need for a parallel annotated persistence model. The adapter maps
    domain ⇄ rows explicitly.
  - Atomicity (FR-023) is a single local transaction — trivial and explicit with `JdbcClient`.
  - Minimal dependency surface (DR-007, DR-034).
- **Alternatives considered**:
  - *Spring Data JDBC* — models aggregates well, but couples the persisted model to Spring Data
    annotations or forces a separate mapping layer anyway; adds a dependency for little gain at
    this size. Revisit if the domain grows many aggregates.
  - *JPA/Hibernate* — heavier, lazy-loading/identity-map complexity, annotation invasion; EN001
    deliberately chose `starter-jdbc` over `starter-data-jpa`. Rejected.
  - *jOOQ* — not in the technology policy; would need review. Rejected.

## D2 — Where business validation lives

- **Decision**: **All** FD001 business rules are enforced in the `portfolio.domain` layer, in the
  `Portfolio.create(...)` factory and value-object constructors. The domain raises a single
  `PortfolioValidationException` carrying a list of `Violation(field, code, message)` so the web
  adapter can render field- / Position-level errors (FR-024). The database `CHECK` and `UNIQUE`
  constraints are **defense-in-depth**, not the primary enforcement. Controllers and SQL contain
  no business logic (AR-003, DR-004).
- **Rationale**: AR-003 / DR-004; makes the rules unit-testable without Spring or a DB; a single
  exception type keeps the adapter mapping simple; the violation list supports the design-system
  requirement to show messages near the affected field.
- **Alternatives considered**: Bean Validation (`jakarta.validation`) annotations on web DTOs —
  useful for shallow format checks but can't express BR-004 (cross-Position uniqueness) or BR-007
  (price currency = position currency) cleanly, and would split the rules between the DTO and the
  domain. We may still use lightweight `@NotBlank`-style checks on the *web DTO* purely for
  early 400s on malformed JSON, but the authoritative rules stay in the domain.

## D3 — Accidental double-submit → exactly one Portfolio (FR-031a)

- **Decision**: **Client-generated idempotency key.** The frontend creates a UUID when the
  Investor first triggers Save for a given draft and sends it as an `Idempotency-Key` request
  header (reused on retry). The backend persists it as a `NOT NULL UNIQUE` column on `portfolio`.
  On create: look up the key first; if a Portfolio already exists for it, return that Portfolio
  (HTTP `200` + `Idempotency-Replayed: true`) instead of creating a second one; otherwise create
  (HTTP `201`). The frontend also disables Save while a request is in flight (defense-in-depth).
- **Rationale**: Survives network retries and accidental refresh, not just double-clicks. Does not
  introduce a Portfolio-name uniqueness rule (FR-004 preserved). Standard, well-understood pattern;
  the unique constraint gives a hard guarantee even under a race.
- **Alternatives considered**:
  - *Disable-button only* — fails on client retry / proxy retry. Insufficient for FR-031a.
  - *Name+content hash as the key* — brittle (two legitimately-identical portfolios become
    impossible) and contradicts FR-004.
  - *DB advisory lock per investor+name* — heavier, still doesn't dedupe true retries cleanly.

## D4 — External error contract

- **Decision**: **RFC 9457 `application/problem+json`** for all non-2xx responses of
  `POST /api/portfolios`, via Spring's `ProblemDetail` + a `@RestControllerAdvice`
  (`PortfolioExceptionHandler`) in `portfolio.adapter.in.web`. Validation failures → `400` with a
  stable `type` URI (e.g. `/problems/portfolio-validation`) and an `errors` array of
  `{ field, code, message }`. Idempotency replay → `200` (not an error). Persistence failure →
  `503` with `type` `/problems/portfolio-not-saved` and a generic non-technical `detail`
  (FR-023a). No stack traces, SQL, or framework exceptions leak (AR-012, DR-020).
- **Rationale**: AR-012 (stable machine-readable errors), design-system "recoverable operation
  errors" vs "validation errors", the spec's requirement for field-specific feedback.
- **Alternatives considered**: ad-hoc JSON error body — not standardized; rejected. Bean-Validation
  default error shape — leaks framework structure; rejected.

## D5 — Money, quantity, and identifier types

- **Decision**: Domain value objects — `Money(BigDecimal amount, Currency currency)`,
  `Quantity(BigDecimal value)`, `Ticker(String)`, `Market(String)` (ISO 10383 MIC shape where a
  code is supplied — FR-015), `Currency(String)` (ISO 4217 shape — three uppercase letters,
  FR-016), `PortfolioName(String)` (non-blank, trimmed, ≤ 120 chars — A7). `BigDecimal` everywhere
  for amounts; `NUMERIC` columns in PostgreSQL; JSON numbers serialized as strings is **not**
  needed (Jackson `BigDecimal` with `WRITE_BIGDECIMAL_AS_PLAIN` keeps precision). Optionality:
  `Optional<LocalDate>` / `Optional<Money>` on `Position` for the two optional fields, never
  null-as-magic-value (FR-019, DR-012).
- **Rationale**: DR-010 (primitive obsession), DR-011 / FR-025 (safe decimal), BR-007 becomes an
  invariant-by-construction (a `Position`'s `averagePurchasePrice` `Money` is built with the
  Position's own `Currency`). ISO checks are **format-level only** — no external reference-data
  lookup (spec A5).
- **Alternatives considered**: raw `String` + `double`/`BigDecimal` fields — scatters validation,
  risks float drift, loses the currency-coupling invariant. `javax.money` (JSR-354) — extra
  dependency, more than FD001 needs; a small `Money` value object suffices.

## D6 — Business events (PortfolioCreated, PositionAdded)

- **Decision**: Record them as **structured (ECS JSON) log entries** emitted by
  `CreatePortfolioService` after a successful save (`event=PortfolioCreated portfolioId=… investorId=…`
  and one `event=PositionAdded …` per Position). No outbound event port, no table, no broker
  (FR-032, AR-016, AR-017). A real event-publication port is introduced only when a consumer
  exists.
- **Rationale**: FR-032 explicitly forbids implying messaging; AR-016 requires a concrete reason
  for Kafka (none here); structured logs give auditability (AR-041) at zero infrastructure cost.
- **Alternatives considered**: a `domain_event` outbox table — speculative (no consumer,
  AR-046). Spring `ApplicationEventPublisher` — in-process only, adds a Spring dependency into the
  application layer for no current benefit.

## D7 — Frontend creation flow

- **Decision**:
  - Route `portfolios/new` → `CreatePortfolioPageComponent` (Angular standalone, reactive forms).
  - Sidebar gains a **"Portfolios"** entry routing to `portfolios/new` (the only Portfolio
    capability today; becomes the list + "Create" button when a listing feature lands). This is
    allowed now because a Portfolio capability exists (design-system: no nav for *non-existent*
    capabilities).
  - `AddPositionDialogComponent` — a focused dialog/drawer for one Position (design-system:
    "Dialogs and Drawers … Add Position"). `PositionDraftListComponent` — the in-progress list
    with remove / edit (FR-026…028).
  - `PortfolioApiService` — `POST /api/portfolios` with the `Idempotency-Key` header (UUID via
    `crypto.randomUUID()`), maps a `400` problem body's `errors[]` back onto form controls,
    surfaces a `503` as a non-blocking "couldn't save, try again" message with the draft intact
    (FR-023a).
  - Frontend validation = **format / required only** for immediate feedback; the backend is
    authoritative for every business rule (AR-013 — the frontend must not replicate critical
    backend rules; it renders backend field errors).
  - Save disabled while the request is in flight; success confirmation is a message (no link to a
    Portfolio detail view — that's out of scope, A9).
  - All visual values via `src/styles/_tokens.scss` (design-system).
- **Alternatives considered**: a multi-step wizard — design-system says "avoid unnecessary
  multi-step workflows when a single screen … is sufficient". A single page with an Add-Position
  dialog matches FD001 §4 and §11.

## D8 — Architecture-governance items

- **ArchUnit**: generalize `HexagonalArchitectureRulesTest` so the layering rules apply to
  **every** capability package under `com.myfinaimanager.core` (`..domain..`, `..application..`),
  not only `..platform..`. The rules stop being vacuous once `portfolio` exists (SC-008 / FR-027 /
  EN001 note). Add a rule: web adapters may not be referenced by `application` or `domain`;
  persistence adapters may not be referenced by inbound adapters.
- **Data ownership**: the `portfolio` module owns `investor`, `portfolio`, `position` (AR-020).
  Recorded in `data-model.md`. No other module accesses these tables. The `investor` table is a
  placeholder for the future identity capability, which will take ownership then.
- **Coverage gate**: enable the JaCoCo `check` goal in `core-service/pom.xml` at **90% line /
  branch overall** (DoD §5), with `portfolio.domain` expected near 100% branch (DR-004,
  testing-strategy §15). Keep the EN001 exclusions (`CoreServiceApplication`, `bootstrap/**`).
- **ADR-002 (drafted 2026-09-01 at the maintainer's request; Status: Proposed)**:
  `product/architecture/adrs/ADR-002-interim-unauthenticated-write-access.md` records that FD001
  (and any feature before the identity/auth enabler) exposes writes without authentication, the
  conditions on that acceptance (single default Investor, non-production only, non-null
  `investor_id`, no design obstacle to adding auth, per-feature acknowledgement), and the
  supersede trigger (the identity enabler, or any non-local deployment). Constitution I: AI
  drafted it; **a human must approve it before the FD001 PR merges** (T065).

## D9 — Contract test mechanism

- **Decision**: `CreatePortfolioControllerContractTest` (`@WebMvcTest` + `MockMvc`, use case
  mocked) validates real request/response payloads against the operation schema in
  `contracts/openapi/openapi.yaml` using an OpenAPI request/response validator
  (`com.atlassian.oai:swagger-request-validator-mockmvc` or `org.openapi4j`) — test scope only.
- **Rationale**: AR-011 / DR-017 / testing-strategy §4 — the contract must not drift silently.
- **Alternatives considered**: hand-asserting the JSON shape — drifts easily; generating server
  stubs from the spec (openapi-generator) — more build machinery than one operation warrants
  (can adopt later as the contract grows).
- **Implementation note (contract dialect)**: `openapi.yaml` is authored in **OpenAPI 3.0.3**, not
  3.1. `swagger-request-validator` 2.44.x validates request *parameters* unreliably against a 3.1
  `type: [ ... ]` union (it treats a `SIMPLE`-style header value such as `Idempotency-Key` as a
  JSON document and fails to parse it). 3.0.3 expresses everything FD001 needs — `nullable: true`
  replaces the `["string","null"]` unions on the optional `Position` fields — and keeps the
  contract test meaningful. Revisit if a later feature needs a genuine 3.1-only construct.

## D10 — Default Investor seed

- **Decision**: `V2__portfolio.sql` inserts exactly one `investor` row with a fixed, well-known
  UUID and a display name like `"Default Investor"`, preferred currency `EUR`, locale/time-zone
  left null (not used by FD001). `JdbcDefaultInvestorProvider` reads it (single row; fail fast if
  absent). No API, no UI for Investors.
- **Rationale**: spec A2 / FR-030 — the record must exist before a Portfolio can be created;
  seeding via the migration is the platform's approved mechanism and keeps the fixed id stable
  across environments for tests and the future identity migration.
- **Alternatives considered**: create-on-first-use in the service — hidden write, harder to test,
  race-prone. `application.yml` property for the id — fine but the row still has to be seeded;
  the migration is the single source.

---

## Open questions

None. The spec's one Outstanding item (a hard latency SLO for `POST /api/portfolios`) is set as a
non-gating derived target in the plan's Technical Context (< 1 s p95 local); a formal SLO belongs
to a future operability/observability enabler, not FD001.
