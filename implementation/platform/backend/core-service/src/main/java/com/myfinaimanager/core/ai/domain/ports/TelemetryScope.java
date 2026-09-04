package com.myfinaimanager.core.ai.domain.ports;

/**
 * A handle for one open telemetry span (FR-044). Business code opens it in a try-with-resources
 * block; the infrastructure {@link TelemetryPort} implementation closes the underlying span when
 * {@link #close()} runs. Carries no Micrometer/OpenTelemetry type — {@code ai.business} never
 * imports either.
 */
public interface TelemetryScope extends AutoCloseable {

    @Override
    void close();
}
