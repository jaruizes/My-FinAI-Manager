-- FD001 — Create Investment Portfolio.
-- Owned by the `portfolio` capability module (AR-020). No other module reads/writes these tables.
--
-- `investor` is a placeholder for the future identity capability; FD001 only seeds one row and
-- reads it (ADR-002 — interim unauthenticated write access, single default Investor).

CREATE TABLE investor (
    id                  UUID PRIMARY KEY,
    display_name        TEXT NOT NULL,
    preferred_currency  CHAR(3),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The one default Investor. Fixed, well-known id so tests and the future identity migration are stable.
INSERT INTO investor (id, display_name, preferred_currency)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Investor', 'EUR');

CREATE TABLE portfolio (
    id               UUID PRIMARY KEY,
    investor_id      UUID NOT NULL REFERENCES investor (id),
    name             TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'ACTIVE',
    idempotency_key  TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT portfolio_name_len_chk    CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    CONSTRAINT portfolio_status_chk      CHECK (status IN ('ACTIVE')),
    CONSTRAINT portfolio_idem_key_uk     UNIQUE (idempotency_key)
);

CREATE INDEX portfolio_investor_id_idx ON portfolio (investor_id);

CREATE TABLE position (
    id                               UUID PRIMARY KEY,
    portfolio_id                     UUID NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    ticker                           TEXT NOT NULL,
    market                           TEXT NOT NULL,
    quantity                         NUMERIC NOT NULL,
    currency                         CHAR(3) NOT NULL,
    initial_purchase_date            DATE,
    average_purchase_price           NUMERIC,
    average_purchase_price_currency  CHAR(3),
    CONSTRAINT position_quantity_chk        CHECK (quantity > 0),
    CONSTRAINT position_price_positive_chk  CHECK (average_purchase_price IS NULL OR average_purchase_price > 0),
    CONSTRAINT position_price_currency_chk  CHECK (
        average_purchase_price_currency IS NULL
        OR average_purchase_price_currency = currency),
    CONSTRAINT position_price_pair_chk      CHECK (
        (average_purchase_price IS NULL AND average_purchase_price_currency IS NULL)
        OR (average_purchase_price IS NOT NULL AND average_purchase_price_currency IS NOT NULL)),
    CONSTRAINT position_date_not_future_chk CHECK (initial_purchase_date IS NULL OR initial_purchase_date <= current_date),
    -- Defense-in-depth for BR-004; primary enforcement is in the domain aggregate.
    CONSTRAINT position_instrument_uk       UNIQUE (portfolio_id, ticker, market)
);
