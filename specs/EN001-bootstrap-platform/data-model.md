# Phase 1 Data Model: Bootstrap Executable Platform (EN001)

EN001 is a technical enabler. **It introduces no business domain entities, no domain/application
classes, and no business database schema.** Per governance correction #5, real domain modules
emerge with actual product capabilities (FD001 onward), not as demonstrations here.

The global Information Model under `product/definition/global/` is unaffected.

---

## 1. Domain / application model

None. No `PlatformStatus`, no ports, no services, no adapters. The `platform/` package namespace
exists only as a **documented convention** (`package-info.java` describing the `domain` /
`application` / `adapter` layering) with an ArchUnit guardrail — no production types.

Backend/database health is provided by the framework (Spring Boot Actuator `/actuator/health`
with its built-in `db` health indicator), which requires no application code.

---

## 2. Persistence model

### Flyway baseline — `db/migration/V1__baseline.sql`

- Contains **no tables** — a header comment only, establishing the migration baseline so future
  features add `V2__*`, `V3__*`, …
- Applied on startup and in the integration test.
- The only table that exists after migration is Flyway's own `flyway_schema_history`.

### Connectivity

- Verified by Spring Boot Actuator's `db` health indicator (a lightweight validation query) and,
  in the integration test, by an explicit `SELECT 1` through the configured `DataSource`.
- Owned by `core-service`. No business data, so cross-module ownership rules (AR-006, AR-020) are
  trivially satisfied.

---

## 3. Frontend model

None. The shell renders static structural chrome (sidebar, top bar, empty content area) and one
placeholder route. No API calls, no domain model.

---

## 4. Configuration model (externalized — not persisted)

| Key | Source | Local default | Secret? |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | env | `jdbc:postgresql://localhost:5432/<db>` (db name per `.env`) | no |
| `SPRING_DATASOURCE_USERNAME` | env | synthetic local value from `.env` | no |
| `SPRING_DATASOURCE_PASSWORD` | env | synthetic local value from `.env` | non-prod placeholder; never a real secret |

Committed as `infrastructure/local/.env.example`; the real `.env` is git-ignored. PostgreSQL major
version and other version choices are pending (see `research.md` Open questions / tasks OD-1…OD-6).
