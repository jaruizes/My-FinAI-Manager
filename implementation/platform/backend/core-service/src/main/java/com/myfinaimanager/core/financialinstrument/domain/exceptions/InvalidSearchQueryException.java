package com.myfinaimanager.core.financialinstrument.domain.exceptions;

/**
 * The catalog search was called with a missing or blank query. The REST adapter turns this into a
 * {@code 400 application/problem+json} ({@code type: /problems/invalid-search-query}) — RFC 9457
 * (AR-012). This is a business-observable outcome, not an infrastructure error.
 */
public class InvalidSearchQueryException extends RuntimeException {

    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
