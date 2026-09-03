package com.myfinaimanager.core.portfolio.infrastructure.catalog;

import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import com.myfinaimanager.core.portfolio.domain.model.Currency;
import com.myfinaimanager.core.portfolio.domain.model.Market;
import com.myfinaimanager.core.portfolio.domain.model.Ticker;
import com.myfinaimanager.core.portfolio.domain.ports.InstrumentCatalog;
import org.springframework.stereotype.Component;

/**
 * Implements the {@code portfolio} anti-corruption port {@link InstrumentCatalog} by delegating to
 * the {@code financialinstrument} module's published read port (AR-062 — the <strong>only</strong>
 * place {@code portfolio} references {@code financialinstrument}, and only its
 * {@code domain.ports} / {@code domain.model}).
 *
 * <p>{@code financialinstrument} resolves the listing by {@code ticker + market}; the
 * {@code ticker + market + currency} match — the FD002 guarantee (FR-011) — is completed here.
 */
@Component
class CatalogInstrumentCatalogAdapter implements InstrumentCatalog {

    private final FinancialInstrumentCatalog catalog;

    CatalogInstrumentCatalogAdapter(FinancialInstrumentCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean isSelectable(Ticker ticker, Market market, Currency currency) {
        return catalog.findSelectable(ticker.value(), market.value())
                .filter(listing -> listing.currency().name().equalsIgnoreCase(currency.code()))
                .isPresent();
    }
}
