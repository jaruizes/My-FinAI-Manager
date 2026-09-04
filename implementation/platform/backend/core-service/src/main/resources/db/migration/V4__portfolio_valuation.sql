-- FD004 — Portfolio Valuation & Allocation.
-- Owned by the `portfolio` capability module (AR-020). No other module reads/writes these tables.
-- Flyway owns the schema; Hibernate never alters it (ddl-auto: none).
--
-- LATEST SNAPSHOT ONLY (FD004 §12; FR-020): exactly one `portfolio_valuation` row per portfolio,
-- enforced by the UNIQUE (portfolio_id). Re-valuation is a delete-then-insert in one transaction
-- (FR-021) — never an append. No historical valuation table.
--
-- The FD001 `portfolio` / `position` tables and the EN004 catalog tables are NOT modified (FR-023).
-- Monetary columns are NUMERIC with no precision/scale so the exact computed value round-trips
-- (matches FD001); the 2-decimal display rounding is a UI concern (FR-031). A Position that could
-- not be priced has NULL monetary columns and `valued = false` — never 0 (FR-017, FR-019).

CREATE TABLE portfolio_valuation (
    id                 UUID PRIMARY KEY,
    portfolio_id       UUID NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    status             TEXT NOT NULL,
    calculated_at      TIMESTAMPTZ NOT NULL,
    total_value_eur    NUMERIC,
    total_value_usd    NUMERIC,
    market_data_as_of  TIMESTAMPTZ,
    fx_data_as_of       TIMESTAMPTZ,
    CONSTRAINT portfolio_valuation_portfolio_uk UNIQUE (portfolio_id),
    CONSTRAINT portfolio_valuation_status_chk
        CHECK (status IN ('PENDING', 'COMPLETED', 'PARTIAL', 'FAILED'))
);

CREATE TABLE position_valuation (
    id                     UUID PRIMARY KEY,
    portfolio_valuation_id UUID NOT NULL REFERENCES portfolio_valuation (id) ON DELETE CASCADE,
    ticker                 TEXT NOT NULL,
    market                 TEXT NOT NULL,
    quantity               NUMERIC NOT NULL,
    valued                 BOOLEAN NOT NULL,
    native_currency        CHAR(3) NOT NULL,
    market_price           NUMERIC,
    native_market_value    NUMERIC,
    value_eur              NUMERIC,
    value_usd              NUMERIC,
    portfolio_weight       NUMERIC,
    sector                 TEXT NOT NULL,
    price_observed_at      TIMESTAMPTZ,
    CONSTRAINT position_valuation_unvalued_has_no_money_chk CHECK (
        valued
        OR (market_price IS NULL AND native_market_value IS NULL
            AND value_eur IS NULL AND value_usd IS NULL))
);

CREATE INDEX ix_position_valuation_parent ON position_valuation (portfolio_valuation_id);

CREATE TABLE sector_allocation (
    id                     UUID PRIMARY KEY,
    portfolio_valuation_id UUID NOT NULL REFERENCES portfolio_valuation (id) ON DELETE CASCADE,
    sector                 TEXT NOT NULL,
    sector_value_eur       NUMERIC NOT NULL,
    sector_weight          NUMERIC NOT NULL
);

CREATE INDEX ix_sector_allocation_parent ON sector_allocation (portfolio_valuation_id);
