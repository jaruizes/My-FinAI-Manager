package com.myfinaimanager.core.platform.infrastructure.api.rest;

import com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException;
import com.myfinaimanager.core.platform.infrastructure.api.rest.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates the neutral domain failure into an explicit {@code 503} with a
 * stable error code (EN001 AC-008, BR-001, DR-008). Internal detail stays in the
 * server log; the client only ever sees the code.
 */
@RestControllerAdvice(assignableTypes = HelloController.class)
public class HelloExceptionHandler {

    static final String PLATFORM_VERSION_UNAVAILABLE = "PLATFORM_VERSION_UNAVAILABLE";

    private static final Logger log = LoggerFactory.getLogger(HelloExceptionHandler.class);

    @ExceptionHandler(PlatformVersionUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(PlatformVersionUnavailableException ex) {
        log.error("Platform version unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(PLATFORM_VERSION_UNAVAILABLE));
    }
}
