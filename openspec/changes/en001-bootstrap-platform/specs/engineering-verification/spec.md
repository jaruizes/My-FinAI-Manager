## Purpose

Defines the automated verification foundations for My-FinAI-Manager: a Maven backend build, PostgreSQL integration testing with Testcontainers, ArchUnit architecture tests, and containerized Playwright browser E2E — so every future change extends a testable platform.

## ADDED Requirements

### Requirement: Maven backend build and test baseline

The backend SHALL be built with Maven and provide a single verification command that compiles the code and executes the mandatory unit, integration, and architecture tests. The command SHALL return a success exit code only when compilation and all mandatory tests pass.

#### Scenario: Backend verification succeeds on a clean checkout

- **WHEN** the backend Maven verification command runs on a checkout with supported build prerequisites
- **THEN** compilation succeeds
- **AND** the mandatory unit, integration, and architecture tests execute
- **AND** the command exits with success

### Requirement: PostgreSQL integration testing with Testcontainers

The backend SHALL include at least one integration test that exercises the platform-version persistence path against a disposable PostgreSQL instance started via Testcontainers, running Flyway migrations. The test MUST NOT require a manually installed or developer-managed database.

#### Scenario: Persistence verified against a disposable database

- **WHEN** the backend PostgreSQL integration test runs
- **THEN** a disposable PostgreSQL Testcontainer starts
- **AND** Flyway migrations execute against it
- **AND** the platform version can be persisted and retrieved through the application persistence path
- **AND** the test passes without any manually installed database

### Requirement: Architecture tests enforce the mandatory Spring boundaries

The backend SHALL include automated ArchUnit tests that enforce the mandatory architecture boundaries from `reference/engineering/architecture-rules.md`, at minimum equivalent to:

```text
..domain..   must not depend on ..business..
..domain..   must not depend on ..infrastructure..
..business.. must not depend on ..infrastructure..
```

and that framework/persistence types (Spring, Spring Data, JPA, HTTP, database clients) do not leak into `domain` where covered by project rules. These tests SHALL run as part of the Maven verification command.

#### Scenario: Forbidden dependency fails the build

- **WHEN** architecture tests execute against the backend
- **THEN** the mandatory dependency-direction rules pass for compliant code
- **AND** introducing a dependency from `domain` to `business` or `infrastructure`, or a framework/persistence import into `domain`, causes an architecture test failure

### Requirement: Containerized Playwright browser E2E

The repository SHALL provide `implementation/platform/e2e.sh` as the canonical entry point for containerized browser E2E execution. Playwright SHALL run inside its own container, use Chromium as the browser baseline, target the containerized frontend, and MUST NOT depend on manually installed Playwright browser binaries. The runner SHALL wait for application readiness using bounded health checks, propagate Playwright's exit status to the caller, and retain diagnostic artifacts on failure where configured.

#### Scenario: E2E command runs containerized Playwright and propagates the result

- **WHEN** `./implementation/platform/e2e.sh` is executed with a Docker-compatible runtime available
- **THEN** Playwright runs in a container against the containerized application using Chromium
- **AND** the runner waits for readiness via bounded health checks, not fixed sleeps
- **AND** the Playwright exit status is propagated to the caller
- **AND** diagnostic artifacts are retained on failure where configured

### Requirement: Mandatory end-to-end browser-to-database scenario

The E2E suite SHALL include at least one Playwright test that proves the complete technical journey `browser → Angular → REST → Spring Boot → PostgreSQL` by opening the Angular Home page and asserting that the exact platform version seeded in PostgreSQL is observable in the UI. The test SHALL fail if the database-backed value cannot traverse the complete chain.

#### Scenario: Full-path smoke test passes

- **WHEN** the platform is running with the known EN001 platform-version fixture in PostgreSQL and the mandatory E2E test runs
- **THEN** Playwright opens the Angular Home page
- **AND** Angular calls the `hello` API, which reads the version from PostgreSQL
- **AND** the exact persisted version is asserted visible in the UI
- **AND** the test passes

#### Scenario: Broken chain fails the test

- **WHEN** the database-backed version cannot traverse the full chain (e.g. backend unreachable or persistence unavailable)
- **THEN** the mandatory E2E test fails with diagnostics
- **AND** the Home page shows an explicit error state rather than a fabricated version
