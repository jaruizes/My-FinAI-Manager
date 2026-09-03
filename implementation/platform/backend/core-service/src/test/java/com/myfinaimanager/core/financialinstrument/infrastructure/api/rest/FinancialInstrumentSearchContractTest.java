package com.myfinaimanager.core.financialinstrument.infrastructure.api.rest;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myfinaimanager.core.financialinstrument.business.SearchFinancialInstrumentsService;
import com.myfinaimanager.core.financialinstrument.domain.exceptions.InvalidSearchQueryException;
import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.NewListing;
import com.myfinaimanager.core.financialinstrument.domain.model.Provenance;
import com.myfinaimanager.core.financialinstrument.infrastructure.api.rest.mapper.FinancialInstrumentResponseMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test (AR-011): the live payloads of {@code GET /api/financial-instruments} conform to
 * {@code implementation/platform/contracts/openapi/openapi.yaml} (copied onto the test classpath by
 * the build). Guards against contract drift and against provider-field leakage (VC-010, VC-014).
 */
@WebMvcTest(controllers = FinancialInstrumentSearchController.class)
@Import({FinancialInstrumentExceptionHandler.class, FinancialInstrumentResponseMapper.class})
class FinancialInstrumentSearchContractTest {

    private static final String CONTRACT = "openapi.yaml";
    private static final Provenance PROV = Provenance.of("YAHOO_CSV", "AAPL", Instant.parse("2026-09-03T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchFinancialInstrumentsService search;

    private static FinancialInstrumentListing listing(String name, String ticker, String mic, String ccy, String isin) {
        return FinancialInstrumentListing.fromRaw(
                new NewListing(name, ticker, mic, ccy, isin, "OpenFIGI:X", "Common Stock", ticker + ".Y", "true"), PROV);
    }

    @Test
    void a_200_result_array_conforms_to_the_contract_and_leaks_no_provider_fields() throws Exception {
        when(search.search(any())).thenReturn(List.of(
                listing("Apple Inc.", "AAPL", "XNAS", "USD", null),
                listing("Banco Santander, S.A.", "SAN", "XMAD", "EUR", "ES0113900J37")));

        mockMvc.perform(get("/api/financial-instruments").param("query", "a"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].market").value("XNAS"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[1].isin").value("ES0113900J37"))
                .andExpect(jsonPath("$[0].providerSymbol").doesNotExist())
                .andExpect(jsonPath("$[0].instrumentType").doesNotExist())
                .andExpect(jsonPath("$[0].externalReference").doesNotExist())
                .andExpect(jsonPath("$[0].source").doesNotExist());
    }

    @Test
    void an_empty_result_is_200_with_an_empty_array() throws Exception {
        when(search.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/financial-instruments").param("query", "nope"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void a_blank_query_yields_a_400_problem_json_that_conforms_to_the_contract() throws Exception {
        when(search.search(any())).thenThrow(new InvalidSearchQueryException("blank"));

        mockMvc.perform(get("/api/financial-instruments").param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/invalid-search-query"))
                .andExpect(jsonPath("$.status").value(400));
    }
}
