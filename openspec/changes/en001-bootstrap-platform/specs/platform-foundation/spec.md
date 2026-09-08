## Purpose

Defines the initial application shell and the minimal bootstrap `hello` capability whose only job is to prove the complete `browser → frontend → REST → backend → PostgreSQL` path by returning the persisted platform version. It carries no product behavior.

## ADDED Requirements

### Requirement: Angular application with an empty Home shell

The system SHALL provide an Angular web application, reachable through the canonical local runtime, that renders an initial Home view containing no product functionality (no Portfolio, Financial Instrument, market, analysis, or recommendation information).

The Home view MAY display only minimal platform shell information required to identify the running application and to expose the technical platform version used by the EN001 smoke test.

#### Scenario: Home shell renders without product functionality

- **WHEN** a browser opens the frontend root URL while the platform is Ready
- **THEN** the Angular application shell renders successfully
- **AND** no Portfolio or other product capability is present in the view
- **AND** the view exposes an element intended to display the technical platform version

### Requirement: Hello endpoint returns the persisted platform version

The backend SHALL expose a synchronous REST endpoint `GET /api/v1/hello` on the external business API that returns the platform version retrieved from PostgreSQL.

The successful response body SHALL be JSON containing at least:

```json
{ "version": "<persisted-version>" }
```

Additional technical fields MAY be included only if justified during design and MUST NOT weaken the acceptance test. The response MUST NOT expose environment secrets, database connection details, or internal stack traces.

#### Scenario: Hello returns the version stored in PostgreSQL

- **WHEN** `GET /api/v1/hello` is invoked while PostgreSQL contains the seeded platform-version record
- **THEN** the response status is `200 OK`
- **AND** the JSON body `version` field equals the exact value persisted in PostgreSQL

#### Scenario: Hello response does not leak internals

- **WHEN** `GET /api/v1/hello` is invoked
- **THEN** the response contains no stack trace, SQL text, framework exception name, connection string, or secret

### Requirement: No fabricated platform version

The version returned by the `hello` capability SHALL originate from PostgreSQL. It MUST NOT be fabricated by the frontend, nor hard-coded as the endpoint result, nor substituted with an invented default when persistence is unavailable.

When the backend cannot retrieve the platform version from PostgreSQL, the endpoint SHALL return an explicit failure or unavailable response defined by the OpenAPI contract, with a stable machine-readable error identifier and no successful `version` payload.

#### Scenario: Persistence unavailable produces an explicit failure

- **WHEN** `GET /api/v1/hello` is invoked and the platform version cannot be read from PostgreSQL
- **THEN** the response is an explicit error/unavailable status defined by the contract (not `200 OK`)
- **AND** the body carries a stable machine-readable error identifier
- **AND** no fabricated `version` value is returned

#### Scenario: Frontend does not invent a version on failure

- **WHEN** the Home page loads and the `hello` request fails or returns an error response
- **THEN** the Home view enters an explicit `error` state
- **AND** no version value is displayed

### Requirement: Frontend consumes the hello endpoint

When the Home page loads, the Angular application SHALL invoke `GET /api/v1/hello` and make the returned platform version observable in the rendered UI. The Home view SHALL represent at least the states `loading`, `success`, and `error`.

#### Scenario: Home page displays the backend-provided version

- **WHEN** the Home page loads and `hello` responds successfully
- **THEN** the Home view transitions from `loading` to `success`
- **AND** the exact version value from the response is rendered in the UI

### Requirement: Platform version persistence is governed by Flyway

The relational schema and the initial platform-version data required by the `hello` flow SHALL be created and seeded through Flyway-governed database migrations. No manual database preparation SHALL be required for the `hello` flow to work after a clean start.

The persisted platform version SHALL be a non-empty text identifier. Domain models used by the business core MUST remain independent from JPA entities.

#### Scenario: Clean start yields a queryable seeded version

- **WHEN** the backend starts against an empty PostgreSQL database
- **THEN** Flyway migrations run to completion
- **AND** a single non-empty platform-version record is present and retrievable through the application persistence path

### Requirement: Hello endpoint is described by the platform OpenAPI contract

The `GET /api/v1/hello` endpoint SHALL be represented in the platform OpenAPI contract under `implementation/platform/contracts/`, including its path, method, success response schema, and error/unavailable response schema. The implemented endpoint SHALL remain compatible with the declared contract.

#### Scenario: Implementation matches the declared contract

- **WHEN** contract verification runs against the running backend
- **THEN** the `hello` request and success response match the declared OpenAPI schema
- **AND** the declared error/unavailable response schema is present in the contract

### Requirement: No product behavior is introduced

The bootstrap capability SHALL NOT introduce Portfolio, Financial Instrument, market-data, news, AI/LLM recommendation, or any other product behavior. Out-of-scope behavior MUST NOT be introduced implicitly. The `platform` module exists solely to prove integration and MUST NOT become a general-purpose business module.

#### Scenario: Validator confirms absence of product behavior

- **WHEN** the Solution Validator compares the implementation with EN001
- **THEN** no Portfolio, Financial Instrument, market-data, news, or AI recommendation behavior is present
- **AND** the only persisted information object is the technical platform version
