package com.myfinaimanager.core.platform.infrastructure.api.rest;

import com.myfinaimanager.core.platform.business.GetPlatformVersion;
import com.myfinaimanager.core.platform.infrastructure.api.rest.dto.ErrorResponse;
import com.myfinaimanager.core.platform.infrastructure.api.rest.dto.HelloResponse;
import com.myfinaimanager.core.platform.infrastructure.api.rest.mapper.HelloResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External business API bootstrap endpoint (EN001 FR-003). Proves the
 * {@code frontend → backend → PostgreSQL} path by returning the persisted
 * platform version.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "platform", description = "Technical platform bootstrap and metadata")
public class HelloController {

    private final GetPlatformVersion getPlatformVersion;

    public HelloController(GetPlatformVersion getPlatformVersion) {
        this.getPlatformVersion = getPlatformVersion;
    }

    @GetMapping(value = "/hello", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getHello",
            summary = "Return the persisted platform version")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The platform version was read from persistence.",
                content = @Content(schema = @Schema(implementation = HelloResponse.class))),
        @ApiResponse(
                responseCode = "503",
                description = "The platform version could not be retrieved from persistence.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public HelloResponse hello() {
        return HelloResponseMapper.toResponse(getPlatformVersion.execute());
    }
}
