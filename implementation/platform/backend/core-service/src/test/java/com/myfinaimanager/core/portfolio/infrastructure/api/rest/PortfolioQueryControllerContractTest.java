package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myfinaimanager.core.portfolio.business.PortfolioQueryUseCase;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioResponseMapper;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioSummaryMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test (AR-011, DR-017): the live request/response payloads of the FD003 read operations
 * ({@code GET /api/portfolios} and {@code GET /api/portfolios/{portfolioId}}) conform to
 * {@code implementation/platform/contracts/openapi/openapi.yaml} (copied onto the test classpath by
 * the build). Also guards that the widened {@link PortfolioExceptionHandler} advice keeps the
 * portfolio {@code /problems/*} family consistent, and that no persistence/provider field leaks.
 */
@WebMvcTest(controllers = PortfolioQueryController.class)
@Import({
        PortfolioExceptionHandler.class,
        PortfolioSummaryMapper.class,
        PortfolioResponseMapper.class})
class PortfolioQueryControllerContractTest {

    private static final String CONTRACT = "openapi.yaml"; // on the test classpath
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
    private static final InvestorId INVESTOR =
            InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioQueryUseCase portfolios;

    private static Portfolio portfolio(String name, NewPosition... positions) {
        return Portfolio.create(INVESTOR, name, List.of(positions), CLOCK);
    }

    @Test
    void the_list_200_response_conforms_to_the_contract() throws Exception {
        when(portfolios.list()).thenReturn(List.of(
                portfolio("Long Term Investment",
                        new NewPosition("ASML", "XAMS", "3", "EUR", null, null),
                        new NewPosition("MSFT", "XNAS", "10", "USD", null, null)),
                portfolio("Technology",
                        new NewPosition("ASML", "XAMS", "1", "EUR", null, null))));

        mockMvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$[0].name").value("Long Term Investment"))
                .andExpect(jsonPath("$[0].positionCount").value(2))
                .andExpect(jsonPath("$[1].positionCount").value(1))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].investorId").doesNotExist())
                .andExpect(jsonPath("$[0].positions").doesNotExist());
    }

    @Test
    void an_empty_list_is_200_with_an_empty_array() throws Exception {
        when(portfolios.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void the_detail_200_response_conforms_to_the_contract() throws Exception {
        Portfolio p = portfolio("Dividend",
                new NewPosition("ASML", "XAMS", "3.250", "EUR", "2024-05-14", "812.50"),
                new NewPosition("MSFT", "XNAS", "10", "USD", null, null));
        when(portfolios.view(any(PortfolioId.class))).thenReturn(p);

        mockMvc.perform(get("/api/portfolios/{id}", p.id().toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.name").value("Dividend"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.investorId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @Test
    void an_unknown_id_is_a_404_problem_that_conforms_to_the_contract() throws Exception {
        UUID id = UUID.fromString("6f9619ff-8b86-4d11-b42d-00c04fc964ff");
        when(portfolios.view(any(PortfolioId.class)))
                .thenThrow(new PortfolioNotFoundException(PortfolioId.of(id)));

        mockMvc.perform(get("/api/portfolios/{id}", id.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-not-found"))
                .andExpect(jsonPath("$.instance").value("/api/portfolios/" + id));
    }

    @Test
    void a_malformed_id_is_rejected_with_400() throws Exception {
        mockMvc.perform(get("/api/portfolios/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
