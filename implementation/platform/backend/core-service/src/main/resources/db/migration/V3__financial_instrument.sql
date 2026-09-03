-- EN004 — Establish Financial Instrument Reference Data.
-- Owned by the `financialinstrument` capability module (AR-020). No other module reads/writes
-- these tables. Flyway owns the schema; Hibernate (`ddl-auto: none`) never alters it.
--
-- `market` and `financial_instrument` hold provider-neutral reference data populated through the
-- mapping-driven ingestion path (never hand-written INSERTs). See specs/EN004-.../research.md D3.

CREATE TABLE market (
    mic                CHAR(4)     PRIMARY KEY,
    name               TEXT        NOT NULL,
    country_iso2       CHAR(2),
    operating_mic      CHAR(4),
    active             BOOLEAN     NOT NULL DEFAULT true,
    source             TEXT,
    source_reference   TEXT,
    last_imported_at   TIMESTAMPTZ,
    CONSTRAINT market_mic_shape_chk CHECK (mic ~ '^[A-Z0-9]{4}$')
);

CREATE TABLE financial_instrument (
    id                 UUID        PRIMARY KEY,
    name               TEXT        NOT NULL,
    ticker             TEXT        NOT NULL,
    market_mic         CHAR(4)     NOT NULL REFERENCES market (mic),
    currency           CHAR(3)     NOT NULL,
    isin               CHAR(12),
    external_reference TEXT,
    instrument_type    TEXT,
    provider_symbol    TEXT,
    active             BOOLEAN     NOT NULL DEFAULT true,
    source             TEXT,
    source_reference   TEXT,
    last_imported_at   TIMESTAMPTZ,
    CONSTRAINT fin_instr_identity_uk    UNIQUE (ticker, market_mic),
    CONSTRAINT fin_instr_currency_chk   CHECK (currency IN ('EUR', 'USD')),
    CONSTRAINT fin_instr_isin_chk       CHECK (isin IS NULL OR isin ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'),
    CONSTRAINT fin_instr_ticker_len_chk CHECK (length(btrim(ticker)) BETWEEN 1 AND 20),
    CONSTRAINT fin_instr_type_chk       CHECK (instrument_type IS NULL OR instrument_type IN ('EQUITY', 'ETF', 'OTHER'))
);

-- Exact-ticker search (case-insensitive) and the FD002 default filter (active + supported currency).
CREATE INDEX fin_instr_ticker_idx     ON financial_instrument (upper(ticker));
CREATE INDEX fin_instr_active_ccy_idx ON financial_instrument (active, currency);
