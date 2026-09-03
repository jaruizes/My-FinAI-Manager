/**
 * REST transport mapping — converts between the {@code rest.dto} request/response records and the
 * business command / domain types. Part of the REST adapter (ADR-003, amended 2026-09-02:
 * {@code infrastructure.api.rest.mapper}, not a separate {@code infrastructure.api.mapper}).
 * Depends inward on {@code business} and {@code domain}; never referenced from either.
 */
package com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper;
