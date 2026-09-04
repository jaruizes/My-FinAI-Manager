package com.myfinaimanager.core.marketdata.domain.model;

/**
 * Whether a result's {@code observedAt} is the provider's own observation timestamp or the time the
 * adapter retrieved the data (the provider gave no timestamp). Lets a caller judge data freshness
 * without guessing (enabler §18).
 */
public enum ObservedAtSource {
    PROVIDER_TIMESTAMP,
    RETRIEVAL_TIME
}
