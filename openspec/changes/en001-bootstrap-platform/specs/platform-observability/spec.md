## Purpose

Defines the local observability baseline for My-FinAI-Manager: backend telemetry emitted with OpenTelemetry and proven to reach a local stack (Collector, Jaeger, Prometheus, Grafana), so future capabilities extend an observable platform.

## ADDED Requirements

### Requirement: Backend emits OpenTelemetry telemetry

The Spring Boot backend SHALL emit application telemetry using OpenTelemetry, sufficient to demonstrate both trace and metric flow through the local observability platform. Application code SHALL treat OpenTelemetry as the application-facing observability standard. Telemetry and diagnostic output MUST NOT expose secrets, credentials, tokens, or database connection details.

#### Scenario: A hello request produces backend telemetry

- **WHEN** the `hello` request is executed through the running backend
- **THEN** the backend emits at least one trace associated with the request via OTLP
- **AND** the backend exposes/export metrics consumable by the local metrics stack

### Requirement: Telemetry reaches the local collector and backends

The local runtime SHALL include an OpenTelemetry Collector that receives backend telemetry and routes traces to Jaeger and metrics to Prometheus. Trace information for the `hello` request SHALL be inspectable in Jaeger, and backend metrics SHALL be queryable through Prometheus.

#### Scenario: Trace visible in Jaeger

- **WHEN** the observability stack is running and a `hello` request has been executed
- **THEN** an application trace associated with the backend request can be found and inspected in Jaeger

#### Scenario: Metrics visible in Prometheus

- **WHEN** the observability stack is running and the backend has received requests
- **THEN** backend metrics are queryable through Prometheus

### Requirement: Grafana is provisioned with a hello dashboard

Grafana SHALL be reachable from the local runtime with its required datasource configuration provisioned automatically at startup. At least one dashboard SHALL be provisioned automatically and SHALL expose telemetry related to calls to the `hello` endpoint. No manual datasource or dashboard configuration SHALL be required after platform startup.

#### Scenario: Provisioned dashboard exposes hello telemetry

- **WHEN** Grafana is opened after the platform has started and at least one `hello` request has been executed
- **THEN** Grafana is reachable
- **AND** the required datasource is already provisioned
- **AND** at least one dashboard is already provisioned that exposes telemetry related to the `hello` endpoint
- **AND** no manual dashboard or datasource setup was performed after startup

### Requirement: Observability failure does not corrupt application behavior

Failure or unavailability of an observability backend SHALL NOT corrupt authoritative application data, and SHALL NOT prevent the backend from serving the `hello` request unless that observability component is deliberately configured as a runtime dependency. Observability failures SHALL remain diagnosable.

#### Scenario: Telemetry backend down, hello still served

- **WHEN** an observability backend is unavailable and it is not configured as a hard runtime dependency
- **THEN** `GET /api/v1/hello` still returns the persisted version
- **AND** the observability failure is diagnosable without altering the hello result
