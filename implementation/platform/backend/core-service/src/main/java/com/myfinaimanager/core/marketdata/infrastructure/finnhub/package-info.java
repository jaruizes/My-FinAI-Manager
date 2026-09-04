/**
 * Finnhub provider adapter (EN005). The <strong>only</strong> place Finnhub HTTP concerns live:
 * {@code client} (the {@code RestClient} wrapper — sole importer of {@code RestClient}),
 * {@code dto} (Jackson records), {@code mapper} (DTO → provider-neutral domain), {@code resolver}
 * (canonical identity → Finnhub symbol), {@code cache} (short-lived in-process TTL decorators).
 * ArchUnit forbids these Finnhub types anywhere outside this package tree (plan.md D11).
 */
package com.myfinaimanager.core.marketdata.infrastructure.finnhub;
