/**
 * The small, closed set of provider-neutral failures the market-data ports raise. No subtype exposes
 * a Finnhub HTTP status, DTO, or field name (FR-012, FR-016). Every message is key-free and safe to
 * log or surface internally; a cause, when present, is for the adapter's structured log only and is
 * never formatted into an outward message.
 */
package com.myfinaimanager.core.marketdata.domain.exceptions;
