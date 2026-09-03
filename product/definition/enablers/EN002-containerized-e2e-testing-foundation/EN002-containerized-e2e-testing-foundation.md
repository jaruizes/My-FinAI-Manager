# EN002 — Establish Containerized End-to-End Testing Foundation

> **Status:** Draft  
> **Enabler ID:** EN002  
> **Enabler Name:** Establish Containerized End-to-End Testing Foundation  
> **Last Updated:** 2026-09-01  

---

# 1. Purpose

Establish a reusable, fully containerized End-to-End testing foundation for My-FinAI-Manager using Playwright.

The goal is to ensure that the complete local application stack can run through Docker Compose and that Playwright validates critical user journeys against that running system.

This enabler also completes the containerization baseline for the executable platform by adding container images for the frontend and backend and adapting the local lifecycle scripts to start and stop the platform entirely through Docker Compose.

This enabler does not define feature-specific E2E scenarios beyond the minimum smoke test required to validate the E2E infrastructure itself.

---

# 2. Motivation

Frontend, backend, persistence, contract, and integration tests may pass independently while a real user journey still fails because of an integration mismatch between layers.

Examples include:

- frontend calling a non-existing backend endpoint;
- incorrect API base path;
- incompatible request/response integration;
- frontend/backend runtime configuration mismatch;
- application components not being fully ready when the user interacts with them.

A browser-level E2E test against the real application stack would detect these failures.

The platform should therefore provide a reproducible containerized runtime that can be used consistently by developers, Playwright E2E tests, future CI pipelines, and future feature verification workflows.

---

# 3. Scope

## In Scope

- Introduce Playwright as the approved browser-based E2E testing framework.
- Create a dedicated E2E area under `implementation/platform/` called `e2e`.
- Add a Dockerfile for the Spring Boot backend.
- Add a Dockerfile for the Angular frontend.
- Add a Dockerfile for the Playwright E2E runner.
- Modify the existing local `compose.yaml` to orchestrate PostgreSQL, backend, frontend, and Playwright E2E runner when explicitly requested.
- Modify `implementation/platform/start.sh` so the complete application platform starts as containers through Docker Compose.
- Modify `implementation/platform/stop.sh` so the complete application platform is stopped through Docker Compose.
- Define explicit container readiness and dependency rules.
- Configure Playwright to test the already-running frontend application.
- Configure Playwright to use service/container DNS names where appropriate inside the Compose network.
- Add a reusable command or script for running E2E tests against the containerized platform.
- Define synthetic test-data principles and repeatability expectations.
- Add a minimal platform smoke test.
- Document local containerized platform and E2E execution.
- Establish conventions for future feature-specific E2E tests.

## Out of Scope

- Complete E2E coverage for FD001.
- Complete E2E coverage for any other Feature Definition.
- Performance testing.
- Load testing.
- Visual regression testing.
- Mobile-native testing.
- Production-environment E2E testing.
- Multi-browser execution beyond the initial browser baseline.
- CI/CD integration unless introduced by another Technical Enabler.
- Authentication-specific E2E infrastructure.
- External market-data, news, LLM, or SaaS integration testing.

---

# 4. Expected Repository Result

```text
implementation/
└── platform/
    ├── backend/
    │   └── core-service/
    │       ├── Dockerfile
    │       └── ...
    ├── frontend/
    │   └── web/
    │       ├── Dockerfile
    │       └── ...
    ├── contracts/
    ├── infrastructure/
    │   └── local/
    │       ├── compose.yaml
    │       └── .env.example
    ├── e2e/
    │   ├── Dockerfile
    │   ├── package.json
    │   ├── playwright.config.ts
    │   ├── tests/
    │   │   └── platform-smoke.spec.ts
    │   ├── support/
    │   └── README.md
    ├── start.sh
    ├── stop.sh
    └── e2e.sh
```

Exact generated framework files may differ where justified.

---

# 5. Containerized Platform Principle

The complete local executable platform must run as containers.

The target runtime topology is:

```text
Docker Compose
    ├── PostgreSQL Container
    ├── Backend Container
    └── Frontend Container
```

For E2E execution:

```text
Docker Compose
    ├── PostgreSQL Container
    ├── Backend Container
    ├── Frontend Container
    └── Playwright Container
```

Local execution of the application must not require a locally running Spring Boot process, Angular development server, or PostgreSQL server.

Docker is the canonical runtime dependency for the complete local application.

Local build tools may still be used for development tasks such as compilation or unit testing where appropriate, but `start.sh` and `stop.sh` must manage runtime through Docker Compose.

---

# 6. Backend Container

The Spring Boot backend must provide a Dockerfile under:

```text
implementation/platform/backend/core-service/Dockerfile
```

The image must:

- build or package the backend reproducibly;
- run the existing `core-service`;
- expose its configured application port;
- obtain database configuration through environment variables;
- avoid embedding credentials;
- support Docker Compose health/readiness verification.

A multi-stage build should be preferred where it reduces the final image size and keeps build tooling out of the runtime image.

The Dockerfile must not introduce business behavior or change backend architecture.

---

# 7. Frontend Container

The Angular frontend must provide a Dockerfile under:

```text
implementation/platform/frontend/web/Dockerfile
```

The image must:

- build the Angular application reproducibly;
- serve the built static application from a containerized web server;
- expose the frontend HTTP port;
- support the API routing/configuration required to communicate with the backend;
- avoid requiring `ng serve` in the runtime container.

The runtime image should use a lightweight production-style static web server or equivalent approved mechanism.

Frontend API configuration must work correctly in the containerized Compose topology.

The implementation must avoid hard-coding environment-specific hostnames into business/frontend source where a runtime or build configuration mechanism is more appropriate.

---

# 8. Docker Compose Integration

The canonical local Compose file remains:

```text
implementation/platform/infrastructure/local/compose.yaml
```

It must orchestrate at least:

```text
postgres
backend
frontend
```

and optionally:

```text
e2e
```

through an explicit Compose profile or equivalent opt-in mechanism.

Conceptually:

```yaml
services:
  postgres:
    ...

  backend:
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    depends_on:
      backend:
        condition: service_healthy

  e2e:
    profiles:
      - e2e
    depends_on:
      frontend:
        condition: service_healthy
```

The default platform startup must not automatically execute E2E tests.

---

# 9. Container Networking

Services must communicate through the Docker Compose network.

Typical internal communication should use service names such as:

```text
postgres
backend
frontend
```

Browser-side frontend requests require special care because code executed in the browser does not resolve Docker Compose service DNS names.

The plan must define an explicit browser-to-backend routing strategy.

Preferred approaches include:

- frontend reverse proxy routing API requests to the backend container;
- a stable externally exposed backend URL configured for the frontend.

The chosen approach must avoid environment-specific ad-hoc URLs and must be documented.

---

# 10. Platform Lifecycle

The canonical lifecycle scripts remain:

```text
implementation/platform/start.sh
implementation/platform/stop.sh
```

## start.sh

`start.sh` must start the complete local platform through Docker Compose.

Conceptually:

```text
start.sh
    ↓
docker compose build / pull as required
    ↓
docker compose up -d postgres backend frontend
    ↓
wait for required health checks
    ↓
report frontend/backend URLs
```

It must not start Spring Boot or Angular as local host processes.

It must fail clearly when Docker is unavailable, a required port is unavailable, required Compose configuration is missing, or the platform does not become ready within a bounded timeout.

## stop.sh

`stop.sh` must stop the complete containerized platform through Docker Compose.

Conceptually:

```text
stop.sh
    ↓
docker compose down
```

It should be safe to invoke when the platform is already stopped.

The script must not rely on PID files for backend/frontend application processes after EN002.

---

# 11. E2E Execution Entry Point

EN002 should introduce:

```text
implementation/platform/e2e.sh
```

as the canonical developer entry point for browser-based E2E execution.

Its responsibility is:

```text
ensure containerized platform is running
        ↓
verify readiness
        ↓
execute Playwright container
        ↓
propagate Playwright exit code
        ↓
collect/retain diagnostics according to policy
```

The script may either start the platform when it is not running and stop it afterwards, or require the platform to already be running.

The exact lifecycle policy must be explicitly decided during planning.

The script must delegate runtime orchestration to Docker Compose and must not start application components directly on the host.

---

# 12. Playwright Container

Playwright must run inside its own container.

The Dockerfile should live under:

```text
implementation/platform/e2e/Dockerfile
```

The image should use an official or otherwise approved Playwright-compatible base image where practical.

The Playwright container must include:

- required Node runtime;
- Playwright Test;
- required browser binaries;
- test sources;
- configuration;
- test-support utilities.

The developer should not need locally installed Playwright browsers to execute E2E tests.

---

# 13. Playwright Target

Playwright tests must interact with the already-running application.

Conceptually:

```text
Playwright
   ↓
Frontend HTTP endpoint
   ↓
User interaction
   ↓
Frontend REST request
   ↓
Backend
   ↓
PostgreSQL
```

The test must begin from the user-facing frontend entry point when validating a user journey.

It must not bypass the frontend and call backend APIs directly as the primary validation mechanism for an E2E user journey.

Backend API calls may be used only as secondary test-support mechanisms when explicitly justified.

---

# 14. Readiness

E2E execution must not rely on arbitrary fixed sleeps as the primary readiness mechanism.

At minimum:

- PostgreSQL uses `pg_isready` or equivalent.
- Backend uses the existing Spring Boot health endpoint.
- Frontend uses an HTTP readiness check.
- Playwright starts only after frontend and backend readiness is confirmed.

Readiness checks must have bounded timeouts and actionable failures.

---

# 15. Test Data

E2E tests must use synthetic test data.

They must not use real personal portfolio information.

Repeated executions must not become dependent on artifacts from earlier runs.

The chosen reset/isolation strategy may include:

- unique generated values;
- database reset before the test suite;
- disposable database volume for E2E execution;
- controlled SQL fixtures;
- dedicated test-support endpoints only if explicitly approved.

The simplest reliable strategy should be preferred.

E2E test-data management must not introduce production-only behavior.

---

# 16. Database Lifecycle for E2E

The E2E execution model must define whether PostgreSQL data persists between normal local platform runs and how E2E isolation is achieved.

The E2E flow may use:

- a dedicated Compose profile;
- an isolated Compose project name;
- a separate test database;
- disposable volumes.

The solution must avoid accidentally deleting normal developer data when running E2E tests.

This decision must be explicit in the plan.

---

# 17. Smoke Test

EN002 must include at least one platform-level E2E smoke test.

Example:

```text
Given the containerized platform is running
When Playwright opens the frontend
Then the application shell is rendered successfully
And no platform-level navigation/runtime error prevents interaction
```

The smoke test must not implement or verify Portfolio-specific business behavior.

Its purpose is to prove that:

```text
Playwright → frontend → containerized platform
```

works correctly.

---

# 18. Feature-Specific E2E Tests

EN002 establishes the infrastructure.

Feature Definitions remain responsible for defining which critical user journeys require E2E verification.

For example, after EN002:

```text
FD001 — Create Investment Portfolio
```

may add:

```text
implementation/platform/e2e/tests/FD001-create-portfolio.spec.ts
```

that verifies:

```text
Browser
   ↓
Create Portfolio screen
   ↓
Save
   ↓
Frontend HTTP request
   ↓
Backend
   ↓
PostgreSQL
   ↓
Success confirmation
```

The implementation of that FD001-specific scenario is not part of EN002.

---

# 19. Browser Baseline

The initial browser baseline should use Chromium unless another browser is explicitly approved.

Cross-browser execution may be introduced later.

EN002 should optimize for reliability, reproducibility, execution speed, and useful diagnostics.

---

# 20. Diagnostics

When an E2E test fails, useful diagnostic evidence should be available.

Where supported and practical:

- screenshots on failure;
- Playwright traces;
- browser console logs;
- failed network request information;
- container logs where relevant.

Successful runs should avoid retaining excessive artifacts unless explicitly configured.

Diagnostics must not expose secrets.

---

# 21. Naming and Organization

Feature-specific E2E tests should remain traceable to Feature Definitions.

Recommended naming:

```text
FD001-create-portfolio.spec.ts
FD002-list-portfolios.spec.ts
```

Platform smoke tests may use:

```text
platform-smoke.spec.ts
```

Tests should describe user-observable behavior rather than internal implementation details.

---

# 22. Testing Strategy Integration

The project testing model should distinguish:

```text
Unit
Integration
Contract
Architecture
E2E
```

The roles are complementary.

- Unit validates isolated deterministic logic.
- Integration validates application components against real application-managed infrastructure, using Testcontainers where required.
- Contract validates external API behavior against OpenAPI or other approved contracts.
- Architecture validates architectural rules and dependency boundaries.
- E2E validates critical user journeys through the real containerized application stack.

E2E tests do not replace lower-level tests.

The E2E suite should remain deliberately small and focused on critical journeys.

---

# 23. Definition of Done Integration

After EN002 is completed, a Feature Definition that introduces or materially changes a critical user journey across:

```text
frontend → backend → persistence
```

should normally require at least one applicable E2E test.

A feature should not be considered technically ready to close when a required E2E journey is missing or failing.

The project closure verifier should check this requirement.

---

# 24. Technology Constraints

EN002 must comply with:

```text
product/architecture/technology-policy.md
product/engineering/testing-strategy.md
product/engineering/development-rules.md
product/engineering/definition-of-done.md
product/architecture/architecture-rules.md
```

Playwright becomes the approved initial browser-based E2E framework.

Docker Compose remains the canonical local container orchestration mechanism.

No unrelated runtime infrastructure may be introduced.

---

# 25. Verification Criteria

## VC-001 — Backend Container

The Spring Boot backend has a reproducible Docker image and runs successfully as a Compose service.

## VC-002 — Frontend Container

The Angular frontend has a reproducible Docker image and is served successfully as a Compose service.

## VC-003 — PostgreSQL Container

PostgreSQL continues to run as part of the Compose topology and the backend connects to it successfully.

## VC-004 — Fully Containerized Start

Running:

```bash
./implementation/platform/start.sh
```

starts PostgreSQL, backend, and frontend as containers.

No backend/frontend application process is started directly on the host.

## VC-005 — Fully Containerized Stop

Running:

```bash
./implementation/platform/stop.sh
```

stops the complete application platform through Docker Compose.

The operation is safe when the platform is already stopped.

## VC-006 — Application Reachability

After `start.sh` completes:

- frontend is reachable;
- backend health is successful;
- PostgreSQL connectivity is healthy.

## VC-007 — Playwright Container

Playwright Test runs inside a container and does not require locally installed browser binaries.

## VC-008 — E2E Against Running System

Playwright executes against the actual running frontend and exercises the containerized application stack.

## VC-009 — Smoke Test

The platform smoke test passes against the fully containerized platform.

## VC-010 — Repeatability

The smoke test can run repeatedly without manual cleanup or contamination from previous E2E runs.

## VC-011 — E2E Entry Point

Running:

```bash
./implementation/platform/e2e.sh
```

provides a stable E2E execution entry point and returns a non-zero exit code when the E2E suite fails.

## VC-012 — Readiness

PostgreSQL, backend, and frontend use explicit readiness/health checks with bounded timeouts.

## VC-013 — Diagnostics

A failing Playwright test produces useful diagnostic evidence.

## VC-014 — Documentation

The repository documents how to build/start the containerized platform, stop it, run E2E tests, run a single E2E test, debug failures, and add a feature-specific E2E test.

## VC-015 — No Product Scope

EN002 introduces no Portfolio or other business behavior.

---

# 26. Expected Follow-Up

After EN002 is completed:

```text
FD001 — Create Investment Portfolio
```

should be updated with a feature-specific E2E test covering the critical creation journey.

Future Feature Definitions can reuse the same containerized E2E foundation.

---

# 27. Architecture Impact

EN002 changes the local runtime topology established by EN001.

Before EN002:

```text
PostgreSQL      container
Backend         host process
Frontend        host process
```

After EN002:

```text
PostgreSQL      container
Backend         container
Frontend        container
Playwright      container when E2E is requested
```

The stable developer entry points remain:

```text
start.sh
stop.sh
```

and a new testing entry point is introduced:

```text
e2e.sh
```

This change does not alter the backend service topology defined by ADR-001.

`core-service` remains the single backend deployable component.

---

# 28. Open Questions

The following technical decisions should be resolved during specification/planning if not already governed:

- backend container base image;
- frontend runtime web server;
- exact Playwright version and Playwright base image;
- Chromium-only initial execution or additional browsers;
- browser-to-backend routing strategy;
- whether frontend reverse-proxies `/api` to backend;
- E2E database isolation/reset strategy;
- whether `e2e.sh` starts/stops its own isolated Compose environment or targets the already-running default platform;
- artifact retention policy for screenshots and traces;
- whether Compose images are always rebuilt by `start.sh` or only when explicitly requested.

Material decisions must be recorded explicitly rather than silently assumed.

---

## Resolved Technical Decisions

### OD-1 — Backend Container Runtime
Java 21 using an Eclipse Temurin JRE image.
Use a multi-stage Docker build.
The final runtime image must not contain Maven/Gradle build tooling.

### OD-2 — Frontend Runtime
Use nginx to serve the Angular production build.

nginx will also reverse-proxy `/api/*` requests to the backend Compose service.

Angular must use relative `/api` URLs.

### OD-3 — Playwright Runtime
Use the official Playwright container image with an explicitly pinned version matching the project Playwright dependency.

### OD-4 — Browser Baseline
Chromium only for the initial E2E baseline.

### OD-5 — Browser-to-Backend Routing
The browser accesses the frontend only.

nginx routes:
- `/` to Angular static assets
- `/api/*` to `backend:8080`

The frontend must not depend on Docker service DNS names.

### OD-6 — E2E Environment Isolation
Each E2E execution uses an isolated Docker Compose project and disposable volumes.

Normal local development data must never be deleted by E2E execution.

### OD-7 — E2E Lifecycle
`e2e.sh` owns an isolated E2E environment.

It:
1. builds required images;
2. starts PostgreSQL, backend and frontend;
3. waits for readiness;
4. executes Playwright;
5. propagates the test exit code;
6. tears the environment down with volumes.

### OD-8 — E2E Diagnostics
- Screenshot: on failure
- Trace: retain on failure
- Video: disabled initially
- Generated artifacts: `implementation/platform/e2e/test-results/`
- Test artifacts must not be committed.

### OD-9 — Normal Platform Image Build
`start.sh` must not rebuild images unconditionally.

Normal startup uses existing images.

A deliberate build option or explicit Docker Compose build command is used when images must be rebuilt.

---

# 29. Human Approval

Before implementation:

- [X] Purpose is correct.
- [X] Full containerization of PostgreSQL, backend, and frontend is approved.
- [X] Dockerfiles for backend and frontend are approved.
- [X] Playwright container is approved.
- [X] Docker Compose remains the canonical local runtime orchestrator.
- [X] `start.sh` / `stop.sh` migration to container-only runtime is approved.
- [X] `e2e.sh` as the canonical E2E entry point is approved.
- [X] E2E data-isolation principles are approved.
- [X] Verification criteria are sufficient.
- [X] No product-specific behavior has been introduced.

**Approved by:*jaruiz*  
**Date:*2026-09-01*  
**Status:** Approved
