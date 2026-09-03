package com.myfinaimanager.core.financialinstrument.business;

import com.myfinaimanager.core.financialinstrument.domain.exceptions.InvalidSearchQueryException;
import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The FD002-facing catalog search operation. Validates the raw query and delegates to the
 * {@link FinancialInstrumentCatalog} port — the port guarantees active + EUR/USD-only results,
 * exact-ticker-first ordering, and no external provider call (contracts/catalog-ports.md §1, §4).
 */
@Service
public class SearchFinancialInstrumentsService {

    private final FinancialInstrumentCatalog catalog;

    public SearchFinancialInstrumentsService(FinancialInstrumentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public List<FinancialInstrumentListing> search(String rawQuery) {
        if (rawQuery == null || rawQuery.strip().isEmpty()) {
            throw new InvalidSearchQueryException("a non-blank search query is required");
        }
        return catalog.search(rawQuery.strip());
    }
}
