package com.myfinaimanager.core.financialinstrument.infrastructure.api.rest;

import com.myfinaimanager.core.financialinstrument.business.SearchFinancialInstrumentsService;
import com.myfinaimanager.core.financialinstrument.infrastructure.api.rest.dto.FinancialInstrumentResponse;
import com.myfinaimanager.core.financialinstrument.infrastructure.api.rest.mapper.FinancialInstrumentResponseMapper;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter — {@code GET /api/financial-instruments?query=…} exactly as defined in
 * {@code implementation/platform/contracts/openapi/openapi.yaml}. No business logic: it delegates
 * to {@link SearchFinancialInstrumentsService} and maps the result to the business-field-only DTO
 * (spec FR-027, FR-028). A blank/missing {@code query} becomes an RFC 9457 problem via
 * {@link FinancialInstrumentExceptionHandler}.
 */
@RestController
public class FinancialInstrumentSearchController {

    private final SearchFinancialInstrumentsService search;
    private final FinancialInstrumentResponseMapper mapper;

    public FinancialInstrumentSearchController(SearchFinancialInstrumentsService search,
                                               FinancialInstrumentResponseMapper mapper) {
        this.search = search;
        this.mapper = mapper;
    }

    @GetMapping(path = "/api/financial-instruments", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<FinancialInstrumentResponse> searchFinancialInstruments(
            @RequestParam(name = "query", required = false) String query) {
        return search.search(query).stream().map(mapper::toResponse).toList();
    }
}
