package com.myfinaimanager.core.platform.infrastructure.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Success payload of {@code GET /api/v1/hello}. Deliberately minimal (EN001
 * FR-005): the persisted platform version and nothing else.
 */
public record HelloResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.1.0")
        String version) {
}
