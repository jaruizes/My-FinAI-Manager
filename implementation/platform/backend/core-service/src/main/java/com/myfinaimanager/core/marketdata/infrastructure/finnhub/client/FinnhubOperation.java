package com.myfinaimanager.core.marketdata.infrastructure.finnhub.client;

/** The Finnhub operations EN005 invokes — used as a structured-log label and to pick the neutral failure. */
public enum FinnhubOperation {
    QUOTE,
    PROFILE
}
