package com.myfinaimanager.core.portfolio.infrastructure.api.rest;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myfinaimanager.core.portfolio.business.CreatePortfolioResult;
import com.myfinaimanager.core.portfolio.business.CreatePortfolioUseCase;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.CreatePortfolioRequestMapper;
import com.myfinaimanager.core.portfolio.infrastructure.api.rest.mapper.PortfolioResponseMapper;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotSavedException;
import com.myfinaimanager.core.portfolio.domain.model.InvestorId;
import com.myfinaimanager.core.portfolio.domain.model.NewPosition;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;
import com.myfinaimanager.core.portfolio.domain.model.Violation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test (AR-011, DR-017): the live request/response payloads of {@code POST /api/portfolios}
 * conform to {@code implementation/platform/contracts/openapi/openapi.yaml} (copied onto the test
 * classpath by the build). Guards against the contract drifting from the implementation.
 */
@WebMvcTest(controllers = CreatePortfolioController.class)
@org.springframework.context.annotation.Import({
        PortfolioExceptionHandler.class,
        CreatePortfolioRequestMapper.class,
        PortfolioResponseMapper.class})
class CreatePortfolioControllerContractTest {

    private static final String CONTRACT = "openapi.yaml"; // on the test classpath
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
    private static final InvestorId INVESTOR =
            InvestorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePortfolioUseCase createPortfolio;

    @Test
    void a_valid_request_and_201_response_conform_to_the_contract() throws Exception {
        Portfolio created = Portfolio.create(INVESTOR, "Long-Term Growth",
                List.of(new NewPosition("ASML", "XAMS", "12", "EUR", null, null)), CLOCK);
        when(createPortfolio.create(any())).thenReturn(new CreatePortfolioResult(created, false));

        mockMvc.perform(post("/api/portfolios")
                        .header("Idempotency-Key", "contract-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Long-Term Growth",
                                  "positions": [ { "ticker": "ASML", "market": "XAMS",
                                                   "quantity": "12", "currency": "EUR" } ] }
                                """))
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void a_replay_200_response_conforms_to_the_contract() throws Exception {
        Portfolio existing = Portfolio.create(INVESTOR, "Existing",
                List.of(new NewPosition("MSFT", "XNAS", "1", "USD", null, null)), CLOCK);
        when(createPortfolio.create(any())).thenReturn(new CreatePortfolioResult(existing, true));

        mockMvc.perform(post("/api/portfolios")
                        .header("Idempotency-Key", "contract-key-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Existing",
                                  "positions": [ { "ticker": "MSFT", "market": "XNAS",
                                                   "quantity": "1", "currency": "USD" } ] }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(openApi().isValid(CONTRACT));
    }

    @Test
    void a_validation_400_body_conforms_to_the_contract() throws Exception {
        // Schema-valid request (two well-formed positions); the broken rule is a business rule
        // (duplicate instrument) that the contract cannot express, so the use case rejects it.
        when(createPortfolio.create(any())).thenThrow(new PortfolioValidationException(List.of(
                new Violation("positions[1]", "DUPLICATE_INSTRUMENT",
                        "A position for ASML on XAMS already exists in this portfolio."))));

        mockMvc.perform(post("/api/portfolios")
                        .header("Idempotency-Key", "contract-key-400")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Growth",
                                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "1", "currency": "EUR" },
                                                 { "ticker": "ASML", "market": "XAMS", "quantity": "1", "currency": "EUR" } ] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-validation"))
                .andExpect(jsonPath("$.errors[0].field").value("positions[1]"))
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_INSTRUMENT"));
    }

    @Test
    void an_instrument_not_in_catalog_400_body_conforms_to_the_contract() throws Exception { // FD002 FR-020
        when(createPortfolio.create(any())).thenThrow(new PortfolioValidationException(List.of(
                new Violation("positions[0]", "INSTRUMENT_NOT_IN_CATALOG",
                        "AAPL on XMAD in EUR is not a selectable instrument."))));

        mockMvc.perform(post("/api/portfolios")
                        .header("Idempotency-Key", "contract-key-catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Growth",
                                  "positions": [ { "ticker": "AAPL", "market": "XMAD", "quantity": "1", "currency": "EUR" } ] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-validation"))
                .andExpect(jsonPath("$.errors[0].field").value("positions[0]"))
                .andExpect(jsonPath("$.errors[0].code").value("INSTRUMENT_NOT_IN_CATALOG"));
    }

    @Test
    void a_persistence_failure_503_body_conforms_to_the_contract() throws Exception {
        when(createPortfolio.create(any()))
                .thenThrow(new PortfolioNotSavedException("db down", new RuntimeException()));

        mockMvc.perform(post("/api/portfolios")
                        .header("Idempotency-Key", "contract-key-503")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "P",
                                  "positions": [ { "ticker": "ASML", "market": "XAMS", "quantity": "1", "currency": "EUR" } ] }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-not-saved"));
    }
}
