# Implementation Plan: Bootstrap Executable Platform (EN001)

**Branch**: `EN001-bootstrap-platform` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/EN001-bootstrap-platform/spec.md`

**Authoritative enabler**: `product/definition/enablers/EN001-bootstrap-platform/EN001-bootstrap-platform.md`
**Governing ADR**: `product/architecture/adrs/ADR-001-initial-backend-topology.md`

> **Revision note (2026-09-01)**: An earlier draft of this plan expanded scope (a
> `GET /api/platform/status` slice with domain/application/adapter classes and a contract test,
> Spring Security permit-all config, a GitHub Actions workflow) and hard-coded framework versions.
> Per maintainer direction those are removed. EN001 now aims for the **smallest executable
> platform** that leaves the repository ready for FD001. Version choices are **not** decided here —
> see Technical Context / research.md "Open questions".

## Summary

EN001 creates the minimum executable technical foundation for My-FinAI-Manager under
`implementation/platform/`. It delivers: one coarse-grained Spring Boot backend (`core-service`)
that boots and serves the framework health endpoint, a **documented Hexagonal Architecture package
convention** with an ArchUnit guardrail (no production business classes), an Angular application
shell, a local Docker Compose environment providing PostgreSQL, a contract-first OpenAPI location
holding an empty skeleton document, the Spring Boot Actuator health mechanism reflecting PostgreSQL
connectivity, one Testcontainers-backed PostgreSQL integration test, and canonical
`start.sh` / `stop.sh` lifecycle scripts that bring the whole platform up and down.

**No** portfolio/valuation/risk/recommendation behavior, **no** business schema, **no** business
API endpoint, **no** authentication, **no** CI/CD, **no** Kafka/Neo4j/Redis/Kubernetes/LLM. Real
domain modules emerge with FD001.

## Technical Context

**Language**: Java (backend), TypeScript (frontend), Bash (lifecycle scripts). **Versions are
unresolved** — see below.

**Primary Dependencies**:
- Backend: Spring Boot (Web, Actuator, JDBC), Flyway (`flyway-core`), PostgreSQL JDBC driver.
- Backend tests: Spring Boot test starter, Testcontainers (PostgreSQL module), ArchUnit.
- Frontend: Angular (standalone components), Angular CLI, SCSS.
- Local infra: Docker / Docker Compose (`compose.yaml`).
- **Not** included: Spring Security; any CI tooling; JPA/Hibernate; a custom web endpoint.

**Storage**: PostgreSQL (local via Docker Compose; disposable container via Testcontainers for the
integration test). No business schema; Flyway wired with an empty baseline migration.

**Testing**: backend `verify` build (build tool per OD-3) running a Testcontainers PostgreSQL
integration test and the ArchUnit conformance test; a minimal Angular shell test.

**Target Platform**: Local developer workstation (macOS / Linux). Deployable backend is an
executable JAR; container image build and CI are out of scope for EN001. Frontend runs via the
Angular dev server locally.

**Project Type**: Web application (decoupled Angular frontend + single Spring Boot backend) plus
local infrastructure — realized as the cumulative platform under `implementation/platform/`.

**Performance Goals**: none beyond the spec's Success Criteria (first successful local start
< 15 min for a new contributor; health healthy within 60 s of start).

**Unresolved decisions (reserved for a maintainer — research.md "Open questions", tasks OD-1…OD-6)**:

| ID | Decision | Why it is open |
|----|----------|----------------|
| OD-1 | Backend JDK / Java LTS version | technology-policy fixes "actively supported release" only |
| OD-2 | Spring Boot version line | not fixed by any authoritative doc |
| OD-3 | Backend build tool (Maven / Gradle) | not fixed by any authoritative doc |
| OD-4 | Angular major version + Node.js LTS version | technology-policy names Angular + TypeScript only |
| OD-5 | PostgreSQL major version | needs a pinned tag for compose + Testcontainers |
| OD-6 | Migration tool (confirm Flyway) | technology-policy names Flyway PREFERRED — treat as resolved unless a maintainer objects |

**Constraints**:
- Single backend deployable component `implementation/platform/backend/core-service/` (ADR-001).
- Hexagonal Architecture convention with inward dependency direction; enforced by ArchUnit.
- Only technologies already approved in `technology-policy.md`.
- No authentication/authorization of any kind in EN001.
- No secrets in source control; configuration externalized; local credentials synthetic.
- `start.sh` / `stop.sh` remain the canonical lifecycle entry points.

**Scale/Scope**: one backend module (no business classes), one frontend app (shell only), one
infrastructure service (PostgreSQL), one integration test, one ArchUnit test. Foundation only.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.0.0** (ratified 2026-08-31), principles I–VIII.

| # | Principle | Status | Evidence |
|---|---|---|---|
| I | Human-Governed Source of Truth (respect ADRs + technology-policy) | PASS | Honors ADR-001; uses only technology-policy-approved tech; version choices deferred to maintainers (not silently decided); no `product/` file modified. |
| II | Definitions/Enablers Are Authoritative Intent | PASS | Every element traces to EN001 VC-001…VC-009; scope reduced to the enabler minimum, not expanded. |
| III | Derived Artifacts & Repository Layout | PASS | Design artifacts under `specs/EN001-bootstrap-platform/`; executable code under `implementation/platform/` only; no CI tree, no alternative root trees. |
| IV | No Invention; Surface Material Ambiguity | PASS | No invented requirements. Unresolved version decisions are surfaced as OD-1…OD-6 for human approval rather than chosen. |
| V | Technical Enablers Stay Technical | PASS | No investor user stories; "US1–US4" are developer/operator capability groups; no product framing forced onto the enabler; platform stays executable via `start.sh`/`stop.sh`. |
| VI | Hexagonal Architecture & Deterministic Logic | PASS | Package convention documented; ArchUnit guardrail enforces inward dependency direction. **No production domain/application classes are created to demonstrate the architecture** — real modules arrive with FD001. No LLM usage. |
| VII | Test-First for Deterministic Logic; Testcontainers by Default | PASS | No deterministic domain logic exists yet (nothing to test-first). The PostgreSQL integration test uses a real disposable Testcontainers container, never a mock (FR-025, FR-026). |
| VIII | Contract-First External APIs | PASS | `contracts/openapi/openapi.yaml` skeleton fixes the contract-first location; EN001 exposes **no** business operation (operational health is a framework mechanism, not a business contract). FD001 adds the first paths. |

Development-Workflow / Compliance gates:

| Gate | Status | Notes |
|---|---|---|
| Plan includes a Constitution Check | PASS | This section. |
| Completion measured by `definition-of-done.md` | PASS | Carried into `/speckit-tasks` (T034); no numeric coverage gate until FD001 adds behavioral code (DoD §5 / testing-strategy §15). |
| ADR required for material architecture change | PASS | No change beyond ADR-001. |
| Structured logging; no secrets in source; externalized config | PASS | `logback-spring.xml` JSON profile (research.md D9); `.env.example` only, synthetic local credentials. |
| No speculative infrastructure | PASS | PostgreSQL only; no Kafka/Neo4j/Redis/K8s/Security/CI. |

**Result: PASS on all principles and gates. No violations. Complexity Tracking not required.**

## Project Structure

### Documentation (this feature)

```text
specs/EN001-bootstrap-platform/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output (+ Open questions OD-1…OD-6)
├── data-model.md        # Phase 1 output (no domain/business model)
├── quickstart.md        # Phase 1 output (validation scenarios)
├── contracts/
│   └── openapi/
│       ├── openapi.yaml   # empty skeleton (paths: {}) — FD001 adds operations
│       └── README.md      # contract-first convention
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks output
```

### Source Code (repository root)

```text
implementation/platform/
├── start.sh                     # Canonical: start infra + backend + frontend
├── stop.sh                      # Canonical: stop the whole local environment
├── README.md                    # Prerequisites + scripts + hexagon convention + how FD001 extends
│
├── backend/
│   └── core-service/
│       ├── <build descriptor>              # pom.xml or build.gradle (OD-3)
│       ├── src/main/java/com/myfinaimanager/core/
│       │   ├── CoreServiceApplication.java  # Spring Boot entry point (only production class)
│       │   ├── platform/                    # documented convention — NO production classes yet
│       │   │   ├── domain/package-info.java
│       │   │   ├── application/port/in/package-info.java
│       │   │   ├── application/port/out/package-info.java
│       │   │   ├── adapter/in/web/package-info.java
│       │   │   └── adapter/out/persistence/package-info.java
│       │   └── bootstrap/package-info.java  # reserved for future framework wiring
│       ├── src/main/resources/
│       │   ├── application.yml              # datasource (env) + Flyway + Actuator health
│       │   ├── logback-spring.xml           # structured JSON logging
│       │   └── db/migration/V1__baseline.sql  # empty baseline (no tables)
│       └── src/test/java/com/myfinaimanager/core/
│           ├── support/PostgresContainerSupport.java        # Testcontainers PostgreSQL
│           ├── bootstrap/PlatformIntegrationIT.java          # context + Flyway + SELECT 1 + Actuator health
│           └── architecture/HexagonalArchitectureRulesTest.java  # ArchUnit guardrail
│
├── contracts/
│   └── openapi/
│       ├── openapi.yaml         # skeleton, paths: {} (mirrors specs/.../contracts)
│       └── README.md
│
├── frontend/
│   └── web/
│       ├── package.json
│       ├── angular.json
│       ├── src/
│       │   ├── main.ts
│       │   ├── index.html
│       │   ├── styles/
│       │   │   ├── styles.scss
│       │   │   └── _tokens.scss             # design-token integration point (design-system.md)
│       │   └── app/
│       │       ├── app.config.ts
│       │       ├── app.routes.ts            # routing foundation (one default route)
│       │       ├── app.component.*          # app shell (dark layout)
│       │       └── core/layout/             # AppShell / Sidebar / TopBar (structure only, no product nav)
│       └── src/app/app.component.spec.ts    # minimal shell test
│
└── infrastructure/
    └── local/
        ├── compose.yaml         # PostgreSQL service only
        └── .env.example         # synthetic non-secret placeholders
```

**Structure Decision**: The single cumulative platform under `implementation/platform/`, matching
the enabler's "Expected Repository Result". The backend is one build module (`core-service`); the
hexagon layers exist as an empty documented package convention plus an ArchUnit guardrail — no
production classes until FD001. No Spring Security, no `.github/` CI tree. `start.sh` / `stop.sh`
are the only supported lifecycle entry points.

## Complexity Tracking

No Constitution Check violations. Section intentionally empty.

## Phase 0 — Research

See [research.md](./research.md). Integration patterns for the approved technologies are resolved
(D1–D10). Version/tool choices are **not** decided — they are recorded under "Open questions"
(OD-1…OD-6) for maintainer approval before implementation. No `NEEDS CLARIFICATION` on product
behavior remain.

## Phase 1 — Design & Contracts

- [data-model.md](./data-model.md) — no business entities, no domain/application classes; only the
  externalized configuration model and the empty Flyway baseline.
- [contracts/openapi/openapi.yaml](./contracts/openapi/openapi.yaml) — OpenAPI 3.1 skeleton with
  `paths: {}`; FD001 adds the first operations. No business or operational endpoints.
- [quickstart.md](./quickstart.md) — six validation scenarios mapped to VC-001…VC-009.

### Post-Design Constitution Re-Check

Re-evaluated after Phase 1: still **PASS**. The design creates no production business classes,
adds no endpoint, no schema, no auth, no CI, and no technology beyond the approved set; version
decisions remain open for humans. No new ADR triggered.
