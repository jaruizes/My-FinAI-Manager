# EN001 — Establish Executable Platform Foundation

> **Status:** Ready  
> **ID:** EN001  
> **Type:** Enabler  
> **Name:** Establish Executable Platform Foundation  
> **Owner:** TBD  
> **Created:** 2026-09-07  
> **Approved by:**  
> **Approval date:**  

---

# 1. Purpose

## What

Establish the first complete executable version of My-FinAI-Manager as a fully integrated local platform.

The Enabler must prove, end to end, that the approved frontend, backend, relational persistence, automated testing, architecture validation, containerized runtime, and observability foundations work together before any product Feature is implemented.

The minimum vertical technical flow is:

```text
Browser
  ↓
Angular
  ↓
REST API
  ↓
Spring Boot
  ↓
PostgreSQL
```

The platform must expose a minimal `hello` capability that reads the current application version from PostgreSQL and returns it through the REST API. The Angular application must consume that capability so a Playwright test can prove the complete browser-to-database path.

The platform must also include the initial local observability stack and demonstrate that application telemetry reaches it successfully.

## Why

Future Feature Definitions must extend an executable, repeatable, observable, and testable platform rather than repeatedly creating or repairing technical foundations.

A frontend, backend, database, E2E framework, or observability component working independently is not sufficient evidence that the platform works as a system.

This Enabler consolidates the platform bootstrap, containerized E2E testing foundation, and standard Spring backend architecture into one coherent baseline.

## For what

After EN001 is delivered:

- the complete local platform can be started and stopped through canonical scripts;
- Angular, Spring Boot, and PostgreSQL communicate successfully;
- a browser-level Playwright test proves `frontend → backend → database`;
- backend integration with PostgreSQL is testable through Testcontainers;
- Spring architecture rules are mechanically verifiable with ArchUnit where applicable;
- the local observability platform is available and receives application telemetry;
- future Features can extend the existing executable platform without recreating its technical foundation.

---

# 2. Scope

## In Scope

- Create the initial Angular frontend application.
- Provide a minimal empty Home view/application shell.
- Create the initial Spring Boot backend deployable component.
- Use Maven as the Spring build tool.
- Apply the approved Spring backend architecture from the beginning:
  - functional-module-first organization;
  - `domain`;
  - `business`;
  - `infrastructure`;
  - dependency direction enforced where practical with ArchUnit.
- Configure PostgreSQL as the initial relational database.
- Use Spring Data JPA / Hibernate for relational persistence.
- Use Flyway for schema migration.
- Introduce the minimal platform metadata required to persist and retrieve the application version.
- Expose a REST `hello` endpoint through the external business API.
- Define the endpoint through OpenAPI.
- Make the Angular frontend invoke the `hello` endpoint.
- Return the application version read from PostgreSQL.
- Containerize:
  - PostgreSQL;
  - Spring Boot backend;
  - Angular frontend;
  - Playwright E2E runner;
  - OpenTelemetry Collector;
  - Jaeger;
  - Prometheus;
  - Grafana.
- Provide the canonical Docker Compose local runtime.
- Provide canonical lifecycle scripts:
  - `start.sh`;
  - `stop.sh`;
  - `e2e.sh`.
- Establish backend integration tests with PostgreSQL Testcontainers.
- Establish architecture tests with ArchUnit.
- Establish Playwright browser E2E testing.
- Provide at least one E2E test proving the full technical flow:
  - browser;
  - Angular;
  - REST;
  - Spring Boot;
  - PostgreSQL.
- Configure application observability using OpenTelemetry.
- Prove that traces and metrics from the backend can reach the local observability stack.
- Provide health/readiness checks sufficient for reliable local orchestration and E2E execution.
- Document the local developer execution and verification entry points.

## Out of Scope

- Portfolio business functionality.
- Financial Instrument functionality.
- Market-data integration.
- News integration.
- AI / LLM provider integration.
- MCP.
- Kafka.
- Neo4j.
- Redis.
- OpenSearch / Elasticsearch.
- Dedicated vector infrastructure.
- Authentication and authorization.
- Kubernetes.
- Production cloud infrastructure.
- Production deployment.
- Feature-specific E2E scenarios.
- Load or performance testing.
- Visual regression testing.
- Multi-browser E2E execution.
- AI-specific observability.
- Any product behavior beyond what is strictly necessary to prove the technical platform.

Out-of-scope behavior must not be introduced implicitly.

---

# 3. Domain & Interactions

## Primary Domain

```text
Domain: Platform Foundation
```

This is a technical Enabler and does not introduce a new product/business domain.

The minimal `hello` capability exists only to prove platform integration and must not become a general-purpose business module.

## Interacting Domains

| Domain | Relationship | Required / Provided Capability |
|---|---|---|
| Platform Runtime | Provides foundation | Executable frontend, backend, database, container lifecycle |
| Engineering Verification | Provides foundation | Unit/integration/architecture/E2E execution |
| Observability | Provides foundation | Traces, metrics, logs/diagnostics where applicable |
| Future Product Domains | Provides to | Stable executable platform to extend |

Conceptually:

```text
Platform Foundation
    │
    ├── proves frontend → backend → persistence
    ├── provides test execution foundations
    ├── provides observability foundations
    └── becomes the base for future FD / EN delivery
```

---

# 4. Information Model & Interactions

## 4.1 Business Entities / Information Objects

EN001 does not introduce business entities.

It introduces one technical information object:

| Entity / Object | Purpose |
|---|---|
| Platform Version | Identifies the current executable application version returned by the bootstrap `hello` capability |

## 4.2 Attributes and Constraints

| Entity | Attribute | Type | Required | Constraints / Rules |
|---|---|---|---:|---|
| Platform Version | version | Text | Yes | Non-empty version identifier |

The authoritative version value used by the `hello` capability must be persisted in PostgreSQL.

The exact relational table/column names are implementation details, but schema creation and initial data must be governed by Flyway.

## 4.3 Relationships

Not applicable.

## 4.4 State / Lifecycle

The platform has a technical runtime lifecycle:

```text
STOPPED
   ↓
STARTING
   ↓
READY
   ↓
STOPPING
   ↓
STOPPED
```

Failure to start a mandatory component results in an explicit startup failure rather than reporting the platform as Ready.

## 4.5 Inputs, Outputs and Business Events

| Direction | Interaction | Type | Sync / Async | Information Exchanged | Purpose |
|---|---|---|---|---|---|
| Input | Open frontend Home | User action | Sync | HTTP navigation | Enter running application |
| Input | Load platform version | REST API | Sync | Hello request | Prove frontend/backend integration |
| Output | Platform version | REST API / UI | Sync | Version from PostgreSQL | Prove full E2E path |
| Output | Telemetry | Observability | Async | Traces / metrics | Prove observability pipeline |

No business events are introduced by EN001.

## 4.6 Temporal Information

No product temporal requirements apply.

Telemetry timestamps must be preserved according to the selected observability tooling.

---

# 5. UX

## Views / Screens

```text
View: Home
Purpose: Provide the initial application shell and prove that the Angular application is reachable.
```

The Home view is intentionally empty of product functionality.

It may contain only minimal platform shell information necessary to identify the running application and expose the technical platform version used by the E2E smoke test.

## Information Displayed

- application identity;
- platform version returned by the backend.

No Portfolio, market, analysis, recommendation, or other product information is displayed.

## User Actions

- Open the application.

No product action is introduced.

## UX States

At minimum:

```text
loading
success
error
```

If the `hello` request fails, the frontend must not fabricate a version.

## Prototype / UX Reference

```text
Reference: Not applicable
```

---

# 6. Use Cases / Scenarios

## UC-001 — Start the Complete Local Platform

### Actors

- Primary actor: Developer / Validator
- Other actors/systems: Docker Compose runtime

### Preconditions

- Docker-compatible runtime is available.
- Required local ports are available.
- Repository configuration required for local execution is present.

### Main Flow

1. The actor executes the canonical platform start command.
2. Required images are built or resolved.
3. PostgreSQL starts and becomes healthy.
4. Backend starts and connects to PostgreSQL.
5. Observability services start.
6. Frontend starts and becomes reachable.
7. The platform reports its local entry points.
8. The complete environment is Ready.

### Postconditions / Result

- Frontend is reachable.
- Backend is healthy.
- PostgreSQL is healthy.
- Observability services are reachable.
- The application is ready for E2E verification.

### Alternative / Corner / Error Scenarios

#### Scenario A — PostgreSQL does not become ready

**Condition**

PostgreSQL fails its readiness check or does not become ready within a bounded timeout.

**Expected behavior**

Backend-dependent readiness must not be reported as successful.

**Result**

Platform startup fails with actionable diagnostics.

#### Scenario B — Mandatory container fails

**Condition**

Any mandatory application or observability container cannot start.

**Expected behavior**

Startup reports the failing service and does not falsely report a healthy platform.

**Result**

Platform remains not Ready.

---

## UC-002 — Prove Browser-to-Database Integration

### Actors

- Primary actor: User / Playwright
- Other actors/systems: Angular, Spring Boot, PostgreSQL

### Preconditions

- The complete local platform is Ready.
- The platform version has been initialized in PostgreSQL by governed migration/data initialization.

### Main Flow

1. The actor opens the Angular Home page.
2. Angular requests the `hello` REST endpoint.
3. Spring Boot processes the request.
4. The backend retrieves the platform version from PostgreSQL.
5. The backend returns the version.
6. Angular renders the returned version.
7. Playwright verifies the expected value.

### Postconditions / Result

The complete flow is proven:

```text
Playwright
   ↓
Browser
   ↓
Angular
   ↓
REST
   ↓
Spring Boot
   ↓
PostgreSQL
```

### Alternative / Corner / Error Scenarios

#### Scenario A — Database version is unavailable

**Condition**

The backend cannot retrieve the required platform version.

**Expected behavior**

The endpoint fails explicitly or returns an explicit unavailable/error response according to the API contract.

**Result**

The frontend must not invent a version and the E2E test must fail.

#### Scenario B — Frontend cannot reach backend

**Condition**

The browser cannot successfully execute the configured REST request.

**Expected behavior**

The Home page enters an explicit error state.

**Result**

The E2E test fails with diagnostics.

---

## UC-003 — Execute Containerized E2E Verification

### Actors

- Primary actor: Developer / Validator
- Other actors/systems: Playwright container, application containers

### Preconditions

- Docker-compatible runtime is available.

### Main Flow

1. The actor executes the canonical E2E command.
2. The E2E runtime starts or verifies an isolated/running platform according to the implementation plan.
3. Playwright waits for application readiness using bounded health checks.
4. Playwright opens the frontend.
5. The browser-to-database smoke test runs.
6. Playwright returns its exit code.
7. Diagnostic artifacts are retained on failure where configured.

### Postconditions / Result

- A successful exit code proves the mandatory EN001 E2E scenario.
- A failure is actionable and does not depend on manually installed Playwright browser binaries.

---

## UC-004 — Verify Local Observability

### Actors

- Primary actor: Developer / Validator
- Other actors/systems: backend, OpenTelemetry Collector, Jaeger, Prometheus, Grafana

### Preconditions

- The complete local platform is running.

### Main Flow

1. The actor invokes the application through the Home/hello flow.
2. Backend telemetry is emitted using OpenTelemetry.
3. The OpenTelemetry Collector receives application telemetry.
4. Trace information is available in Jaeger.
5. Metrics are available to Prometheus.
6. Grafana is reachable and configured to access the required observability data source(s).

### Postconditions / Result

- The local observability platform is demonstrably operational.
- At least one application-generated trace associated with the hello request can be inspected.
- Backend metrics can be queried through the local metrics stack.

### Alternative / Corner / Error Scenarios

#### Scenario A — Telemetry backend is unavailable

**Condition**

An observability backend is unavailable.

**Expected behavior**

The application must remain capable of serving the hello request unless the unavailable observability component is deliberately configured as a runtime dependency.

**Result**

Observability failure is diagnosable without corrupting application behavior.

---

# 7. Functional Requirements & Business Rules

## 7.1 Business Rules

No product business rules are introduced.

### BR-001 — No Fabricated Platform Version

**Rule**

The version returned by the bootstrap `hello` capability must originate from PostgreSQL and must not be fabricated by the frontend or hard-coded as the endpoint result.

**Rationale**

The purpose of the capability is to prove the complete frontend-to-database path.

### BR-002 — No Product Behavior

**Rule**

The Enabler must not introduce Portfolio, financial, market-data, AI recommendation, or other product behavior.

## 7.2 Functional Requirements

### FR-001 — Angular Application

The system must provide an Angular application that can be opened locally through the canonical platform runtime.

### FR-002 — Empty Home

The Angular application must expose an initial Home view containing no product functionality.

### FR-003 — Hello REST API

The backend must expose a synchronous REST endpoint:

```text
GET /api/v1/hello
```

### FR-004 — Version from PostgreSQL

The `hello` endpoint must retrieve the application/platform version from PostgreSQL.

### FR-005 — Hello Response

The endpoint must return at least:

```json
{
  "version": "<persisted-version>"
}
```

Additional technical fields may be included only if justified during specification and must not weaken the acceptance test.

### FR-006 — Frontend Uses Hello API

When the Home page loads, the Angular application must invoke the `hello` endpoint and make the returned platform version observable in the rendered UI.

### FR-007 — OpenAPI Contract

The `hello` endpoint must be represented in the platform OpenAPI contract.

### FR-008 — PostgreSQL Initialization

The schema and initial platform-version data required by the hello flow must be created through Flyway-governed database evolution.

### FR-009 — Fully Containerized Local Runtime

The canonical local runtime must start the frontend, backend, PostgreSQL, and observability stack as containers.

### FR-010 — Platform Start

The repository must provide:

```text
implementation/platform/start.sh
```

as the canonical entry point for starting the complete local platform.

### FR-011 — Platform Stop

The repository must provide:

```text
implementation/platform/stop.sh
```

as the canonical entry point for stopping the complete local platform.

### FR-012 — E2E Execution

The repository must provide:

```text
implementation/platform/e2e.sh
```

as the canonical entry point for containerized Playwright E2E execution.

### FR-013 — Playwright Browser E2E

Playwright must validate the complete technical user journey from the browser through Angular, REST, Spring Boot, and PostgreSQL.

### FR-014 — Backend Integration Testing

The backend must include integration testing against PostgreSQL using Testcontainers.

### FR-015 — Architecture Verification

The backend must include automated architecture tests using ArchUnit where applicable to enforce mandatory Spring architecture boundaries.

### FR-016 — Observability Stack

The local platform must provide containers for:

```text
OpenTelemetry Collector
Jaeger
Prometheus
Grafana
```

### FR-017 — Application Telemetry

The Spring Boot backend must emit OpenTelemetry telemetry sufficient to prove application trace and metric flow through the local observability platform.

### FR-018 — Grafana Dashboard

Grafana must include at least one provisioned dashboard containing telemetry associated with the `hello` endpoint.

### FR-019 — Health and Readiness

Mandatory runtime components must expose or support bounded readiness checks sufficient for deterministic startup and E2E execution.

---

# 8. Non-Functional Requirements

## Performance

No explicit performance target is introduced by EN001.

The bootstrap `hello` flow must be responsive enough for reliable local development and automated E2E execution.

## Security & Privacy

- No real credentials or secrets may be committed.
- Local credentials must use non-sensitive development values or external configuration.
- The bootstrap endpoint must not expose environment secrets, database connection details, or internal stack traces.

Authentication and authorization are explicitly out of scope.

## Resilience & Reliability

- Startup and test execution must use readiness/health checks rather than arbitrary fixed sleeps as the primary synchronization mechanism.
- Startup failures must be explicit and actionable.
- E2E execution must be repeatable.
- Failure of observability backends must not corrupt authoritative application data.

## Availability

Production availability requirements are not applicable.

## Scalability

Not applicable.

## Observability

- Backend telemetry must use OpenTelemetry.
- At least one trace for the hello request must be inspectable in Jaeger.
- Backend metrics must be consumable by Prometheus.
- Grafana must be reachable and wired to the applicable observability data source(s).
- Grafana must include at least one provisioned dashboard containing telemetry related to the `hello` endpoint, so the observability path can be validated without manual dashboard creation.
- Diagnostic information must not leak secrets.

## Auditability / Traceability

- Acceptance tests must be traceable to EN001 acceptance criteria.
- Architecture tests must be traceable to applicable architecture rules.
- The hello E2E scenario must demonstrate the complete platform boundary being tested.

## Cost

The complete local baseline must use locally runnable/open-source or otherwise project-approved components and must not require paid external services.

## Accessibility

No product accessibility requirement beyond the project-wide UX guardrails is introduced by this technical bootstrap.

## Maintainability / Operability

- The complete runtime must be startable/stoppable through canonical scripts.
- Application components must be independently diagnosable through logs/health/observability.
- Infrastructure must remain minimal and must not introduce speculative technologies.

---

# 9. Integrations

## 9.1 API Integrations

### Integration — Platform Hello API

```text
System / Provider: My-FinAI-Manager core-service
Protocol: HTTP REST
Base capability: Platform bootstrap verification
Operation: Retrieve persisted platform version
Authentication: None for EN001
Sync / Async: Sync
```

### Request

```text
Method: GET
Path: /api/v1/hello
Headers: Standard HTTP headers only
Query parameters: None
Request body: None
```

### Required Response Information

- platform/application version retrieved from PostgreSQL.

Minimum successful representation:

```json
{
  "version": "0.1.0"
}
```

The concrete initial version value may be selected during specification as long as the same value is initialized in PostgreSQL and used by deterministic acceptance tests.

### Failure / Timeout Behavior

- Database access failure must not return a fabricated successful version.
- Technical failures must be translated to an appropriate API failure without exposing internal stack traces.

## 9.2 Events / Messaging

```text
Not applicable
```

No Kafka or business messaging is introduced.

## 9.3 Other Integrations

### PostgreSQL

The backend must persist/read the platform version through the approved relational persistence stack.

### OpenTelemetry Collector

The backend must export application telemetry using OpenTelemetry-compatible protocols/configuration.

### Jaeger

Must provide local trace inspection.

### Prometheus

Must provide local metrics collection/query capability.

### Grafana

Must provide a local observability UI with the applicable data source configuration available.

At least one dashboard must be provisioned and must expose telemetry related to calls to the `hello` endpoint.

### Playwright

Runs as a containerized E2E test runner against the already-running containerized frontend.

---

# 10. Technical Constraints

This Enabler defines the initial platform baseline directly. No prior architecture decision is required to interpret these constraints.

The following are part of the approved EN001 scope:

## Backend Topology

- The first backend is a single coarse Spring Boot deployable component (`core-service` or equivalent approved name).
- EN001 must not introduce additional backend microservices.

## Spring Backend Structure

The backend must use a module-first structure.

Inside a functional module, the standard structure is:

```text
<functional-module>/
├── domain/
├── business/
└── infrastructure/
```

The dependency direction is:

```text
infrastructure → business → domain
```

Mandatory restrictions include:

```text
domain       -X-> business
domain       -X-> infrastructure
business     -X-> infrastructure
```

Domain code must not depend on Spring Data, JPA, HTTP, messaging, database clients, or provider SDKs.

## Persistence

- PostgreSQL is the relational database.
- Spring Data JPA / Hibernate is the standard Spring persistence abstraction.
- Flyway owns schema evolution.
- Domain models must remain independent from JPA entities.
- Direct JDBC/native SQL must not be introduced unless a concrete implementation need is identified and explicitly approved.

## Local Runtime

The canonical complete local runtime is Docker Compose based.

`start.sh` and `stop.sh` must manage the runtime through containers rather than host-running Angular, Spring Boot, or PostgreSQL processes.

## Browser E2E

- Playwright is the browser E2E framework.
- Chromium is the initial browser baseline.
- Playwright runs in its own container.
- The E2E test begins at the user-facing frontend.
- It must not bypass the frontend as the primary verification of the EN001 E2E journey.

## Browser-to-Backend Routing

The frontend-to-backend route in the containerized topology must be explicit and stable.

The solution must avoid browser code depending on Docker-internal service DNS names.

A frontend reverse proxy or equivalent stable routing mechanism may be used.

## Observability

The local observability baseline is:

```text
Spring Boot
   ↓ OTLP
OpenTelemetry Collector
   ├── traces  → Jaeger
   └── metrics → Prometheus

Grafana
   ↓
Prometheus
```

Application code must use OpenTelemetry as the application-facing observability standard.

## Hello Capability

The `hello` capability exists solely to prove:

```text
frontend → backend → database
```

It must remain minimal and must not become a container for unrelated business behavior.

---

# 11. Acceptance Criteria & Tests

Acceptance Criteria are mandatory for EN001 and must be translated into executable tests where the behavior is deterministic.

## Acceptance Matrix

| ID | Scenario | Expected Result | Test Level | Mandatory |
|---|---|---|---|---:|
| AC-001 | Build backend | Maven build and tests succeed | Build / Integration | Yes |
| AC-002 | Verify Spring architecture | Mandatory dependency/package rules pass | Architecture | Yes |
| AC-003 | Verify PostgreSQL integration | Backend integration test succeeds against disposable PostgreSQL | Integration / Testcontainers | Yes |
| AC-004 | Start complete platform | Required application and observability containers become ready | E2E / Manual automation | Yes |
| AC-005 | Open Angular Home | Home application shell is reachable and contains no product functionality | E2E | Yes |
| AC-006 | Browser-to-database hello | UI displays the platform version persisted in PostgreSQL | E2E / Playwright | Yes |
| AC-007 | Verify OpenAPI contract | Hello endpoint matches declared OpenAPI contract | Contract | Yes |
| AC-008 | Database failure behavior | Hello does not fabricate a version if persistence is unavailable | Integration | Yes |
| AC-009 | Observability trace | Hello request produces an inspectable backend trace in Jaeger | E2E / Integration / Manual | Yes |
| AC-010 | Observability metrics | Backend metrics are available to Prometheus | Integration / Manual | Yes |
| AC-011 | Grafana dashboard | Grafana is reachable and a provisioned dashboard exposes telemetry related to the `hello` endpoint | E2E / Manual | Yes |
| AC-012 | E2E command | Canonical E2E command executes containerized Playwright and propagates result | E2E | Yes |
| AC-013 | Platform stop | Canonical stop command stops the local platform safely | E2E / Manual automation | Yes |
| AC-014 | No product behavior | No Portfolio/market/AI business behavior is introduced | Manual / Validation | Yes |

## AC-001 — Maven Backend Baseline

**Given**

The repository is checked out with supported build prerequisites.

**When**

The backend Maven verification command is executed.

**Then**

- compilation succeeds;
- mandatory unit/integration/architecture tests execute;
- the command returns success.

## AC-002 — Standard Architecture Is Enforced

**Given**

The initial Spring backend exists.

**When**

Architecture tests execute.

**Then**

At minimum, enforceable rules equivalent to the following pass:

```text
..domain..   must not depend on ..business..
..domain..   must not depend on ..infrastructure..
..business.. must not depend on ..infrastructure..
```

and framework/persistence types do not leak into the domain where covered by project architecture rules.

## AC-003 — PostgreSQL Integration Through Testcontainers

**Given**

No developer PostgreSQL instance is required.

**When**

The backend PostgreSQL integration test runs.

**Then**

- a disposable PostgreSQL Testcontainer is started;
- Flyway migrations execute;
- the platform version can be persisted/retrieved using the application persistence path;
- the test passes independently from a manually installed database.

## AC-004 — Complete Local Platform Starts

**Given**

Docker-compatible tooling is available.

**When**

```bash
./implementation/platform/start.sh
```

is executed.

**Then**

the required containers become healthy/reachable, including:

```text
PostgreSQL
Backend
Frontend
OpenTelemetry Collector
Jaeger
Prometheus
Grafana
```

and startup reports usable local endpoints.

## AC-005 — Empty Angular Home

**Given**

The platform is Ready.

**When**

the browser opens the frontend root URL.

**Then**

- the Angular application shell renders successfully;
- no Portfolio or other product functionality is present;
- the shell exposes the technical platform version used to validate EN001.

## AC-006 — Full Browser-to-Database E2E

**Given**

- the platform is running;
- PostgreSQL contains the known EN001 platform-version fixture.

**When**

Playwright opens the Angular Home page.

**Then**

- Angular calls the hello API;
- the backend reads the version from PostgreSQL;
- the response reaches the browser;
- the exact known persisted version is observable in the UI;
- the Playwright assertion passes.

The test must fail if the database-backed value cannot traverse the complete chain.

## AC-007 — REST Contract

**Given**

the OpenAPI contract is defined.

**When**

contract verification executes.

**Then**

the implemented hello endpoint is compatible with the declared request/response contract.

## AC-008 — No Fabricated Version on Persistence Failure

**Given**

the backend cannot retrieve the platform version from PostgreSQL.

**When**

the hello endpoint is invoked.

**Then**

it returns an explicit failure/unavailable result according to the contract and does not return a hard-coded successful version.

## AC-009 — Trace Visibility

**Given**

the observability stack is running.

**When**

the hello request is executed through the running application.

**Then**

an application trace associated with the backend request is available for inspection in Jaeger.

## AC-010 — Metrics Visibility

**Given**

the observability stack is running.

**When**

the backend is running and receives requests.

**Then**

backend metrics are consumable/queryable through Prometheus.

## AC-011 — Grafana Dashboard

**Given**

the observability stack is running and at least one `hello` request has been executed.

**When**

Grafana is opened.

**Then**

- Grafana is reachable;
- the required datasource configuration is provisioned;
- at least one dashboard is already provisioned;
- that dashboard exposes telemetry related to the `hello` endpoint;
- no manual dashboard or datasource configuration is required after platform startup.

## AC-012 — Canonical E2E Command

**Given**

Docker-compatible tooling is available.

**When**

```bash
./implementation/platform/e2e.sh
```

is executed.

**Then**

- Playwright runs in a container;
- it targets the containerized application;
- Chromium is the browser baseline;
- the EN001 E2E scenario executes;
- Playwright exit status is propagated to the caller;
- useful diagnostics are retained on failure where configured.

## AC-013 — Complete Platform Stops

**Given**

the platform is running.

**When**

```bash
./implementation/platform/stop.sh
```

is executed.

**Then**

the complete local platform is stopped cleanly.

Invoking the command when the platform is already stopped must be safe.

## AC-014 — No Product Functionality Invented

**Given**

the completed EN001 implementation.

**When**

the Solution Validator compares implementation with this Definition.

**Then**

no Portfolio, Financial Instrument, market-data, AI recommendation, or other product behavior has been introduced.

## Test Data / Fixtures

A deterministic platform version must be initialized through the governed database bootstrap/migration.

Example fixture:

```text
version = 0.1.0
```

The exact value may be changed during specification, but the E2E test must know the expected persisted value and must verify the value returned through the complete browser-to-database path.

For EN001, no additional E2E data-loading step is required because Flyway initializes the technical platform-version data used by the test.

For future E2E scenarios that require test-specific data, setup should use one of these approaches:

- a deterministic script that loads the required tables/data; or
- approved application API operations that create the required state.

E2E setup must remain deterministic and must not depend on manually prepared database state.

---

# 12. Dependencies & Assumptions

## Dependencies

| ID / Reference | Type | Dependency / Reason |
|---|---|---|
| `reference/engineering/architecture.md` | Guardrail | Platform architecture |
| `reference/engineering/architecture-rules.md` | Guardrail | Architecture conformance |
| `reference/engineering/technology-policy.md` | Guardrail | Approved technology stack |
| `reference/engineering/development-rules.md` | Guardrail | Implementation discipline |
| `reference/engineering/testing.md` | Guardrail | Testing approach |
| `reference/governance/delivery-policy.md` | Governance | Delivery lifecycle and validation |
| `reference/ux/design-system.md` | Guardrail | Applicable frontend UX constraints |

## Assumptions

| ID | Assumption |
|---|---|
| A-001 | Docker-compatible local container execution is available to developers/validators. |
| A-002 | The approved current reference guardrails remain valid for EN001 implementation. |
| A-003 | A small technical platform-version record is sufficient to prove relational persistence without introducing product data. |
| A-004 | The observability components can run locally together within reasonable developer-machine resources. |
| A-005 | Authentication is not required for this initial local bootstrap capability. |

---

# 13. References

```text
- product/model/vision.md
- reference/engineering/architecture.md
- reference/engineering/architecture-rules.md
- reference/engineering/technology-policy.md
- reference/engineering/development-rules.md
- reference/engineering/testing.md
- reference/governance/delivery-policy.md
- reference/ux/design-system.md
```
