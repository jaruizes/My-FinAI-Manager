package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myfinaimanager.core.portfolio.business.PortfolioValuationQueryUseCase;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import com.myfinaimanager.core.portfolio.domain.model.PositionValuation;
import com.myfinaimanager.core.portfolio.domain.model.SectorAllocation;
import com.myfinaimanager.core.portfolio.domain.model.ValuationStatus;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioValuationResponseMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test (AR-011, DR-017): the live payloads of {@code GET /api/portfolios/{id}/valuation}
 * conform to {@code implementation/platform/contracts/openapi/openapi.yaml}. Also guards the widened
 * {@link PortfolioExceptionHandler} advice and that no persistence/provider field leaks.
 */
@WebMvcTest(controllers = PortfolioValuationController.class)
@Import({PortfolioExceptionHandler.class, PortfolioValuationResponseMapper.class})
class PortfolioValuationControllerContractTest {

    private static final String CONTRACT = "openapi.yaml";
    private static final Instant AT = Instant.parse("2026-09-04T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioValuationQueryUseCase valuations;

    @Test
    void a_completed_valuation_conforms_to_the_contract() throws Exception {
        UUID id = UUID.fromString("6f9619ff-8b86-4d11-b42d-00c04fc964ff");
        PositionValuation aapl = new PositionValuation("AAPL", "XNAS", new BigDecimal("10"), "USD",
                true, Optional.of(new BigDecimal("200")), Optional.of(new BigDecimal("2000")),
                Optional.of(new BigDecimal("1600.00")), Optional.of(new BigDecimal("2000")),
                Optional.of(new BigDecimal("0.761904761905")), "Technology", Optional.of(AT));
        PositionValuation san = PositionValuation.unvalued("SAN", "XMAD", new BigDecimal("100"),
                "EUR", "Unclassified");
        PortfolioValuation v = new PortfolioValuation(PortfolioId.of(id), ValuationStatus.PARTIAL, AT,
                Optional.of(new BigDecimal("1600.00")), Optional.of(new BigDecimal("2000")),
                Optional.of(AT), Optional.of(AT), List.of(aapl, san),
                List.of(new SectorAllocation("Technology", new BigDecimal("1600"),
                        new BigDecimal("1.000000000000"))));
        when(valuations.findLatest(any(PortfolioId.class))).thenReturn(Optional.of(v));

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.totalValueEUR").value("1600.00"))
                .andExpect(jsonPath("$.positions[1].valued").value(false))
                .andExpect(jsonPath("$.positions[1].marketPrice").doesNotExist())
                .andExpect(jsonPath("$.sectors[0].sector").value("Technology"));
    }

    @Test
    void a_portfolio_with_no_snapshot_is_200_pending() throws Exception {
        UUID id = UUID.fromString("6f9619ff-8b86-4d11-b42d-00c04fc964ff");
        when(valuations.findLatest(any(PortfolioId.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.calculatedAt").doesNotExist())
                .andExpect(jsonPath("$.positions").isArray());
    }

    @Test
    void an_unknown_id_is_a_404_problem_that_conforms_to_the_contract() throws Exception {
        UUID id = UUID.fromString("6f9619ff-8b86-4d11-b42d-00c04fc964ff");
        when(valuations.findLatest(any(PortfolioId.class)))
                .thenThrow(new PortfolioNotFoundException(PortfolioId.of(id)));

        mockMvc.perform(get("/api/portfolios/{id}/valuation", id.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-not-found"));
    }

    @Test
    void a_malformed_id_is_rejected_with_400() throws Exception {
        mockMvc.perform(get("/api/portfolios/{id}/valuation", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
