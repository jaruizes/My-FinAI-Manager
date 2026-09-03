package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;

/**
 * Outbound port — read access, during portfolio creation, to whether an instrument selection is a
 * valid catalogued listing (FD002 FR-011). Provider- and module-neutral: only {@code portfolio}
 * value objects cross this boundary. Implemented in {@code portfolio.infrastructure} by delegating
 * to the {@code financialinstrument} module's published catalog port (AR-062).
 *
 * <p>See {@code specs/FD002-select-financial-instrument-from-catalog/contracts/instrument-catalog-port.md}
 * C1 for the full invariants.
 */
public interface InstrumentCatalog {

    /**
     * @return {@code true} iff the platform catalog holds exactly one <strong>active</strong>
     *         Financial Instrument listing whose {@code ticker + market + currency} all match — so
     *         a listing on a different market, an inactive listing, an unknown instrument, or a
     *         submitted {@code currency} that differs from the listing's currency all yield
     *         {@code false}. Never contacts an external provider. Does not throw for well-formed
     *         value objects; an infrastructure failure propagates (it must not be reported as
     *         "not selectable").
     */
    boolean isSelectable(Ticker ticker, Market market, Currency currency);
}
