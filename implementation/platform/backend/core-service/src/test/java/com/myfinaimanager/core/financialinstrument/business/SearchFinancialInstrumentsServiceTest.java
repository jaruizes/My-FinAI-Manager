package com.myfinaimanager.core.financialinstrument.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.myfinaimanager.core.financialinstrument.domain.exceptions.InvalidSearchQueryException;
import com.myfinaimanager.core.financialinstrument.domain.ports.FinancialInstrumentCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchFinancialInstrumentsServiceTest {

    private final FinancialInstrumentCatalog catalog = mock(FinancialInstrumentCatalog.class);
    private final SearchFinancialInstrumentsService service = new SearchFinancialInstrumentsService(catalog);

    @Test
    void rejects_a_null_or_blank_query_without_touching_the_catalog() {
        assertThatThrownBy(() -> service.search(null)).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> service.search("")).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> service.search("   ")).isInstanceOf(InvalidSearchQueryException.class);
        verifyNoInteractions(catalog);
    }

    @Test
    void trims_the_query_and_passes_results_through_unchanged() {
        when(catalog.search(eq("aapl"))).thenReturn(List.of());
        assertThat(service.search("  aapl  ")).isEmpty();
    }
}
