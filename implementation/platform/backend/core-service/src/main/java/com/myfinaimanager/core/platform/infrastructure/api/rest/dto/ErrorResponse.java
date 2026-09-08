package com.myfinaimanager.core.platform.infrastructure.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable machine-readable error payload (DR-008): a code only, never a stack
 * trace, SQL text, or infrastructure detail.
 */
public record ErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATFORM_VERSION_UNAVAILABLE")
        String error) {
}
