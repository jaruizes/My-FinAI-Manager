package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.myfinaimanager.core.portfolioanalysis.business.PortfolioAnalysisQueryUseCase;
import com.myfinaimanager.core.portfolioanalysis.business.RequestPortfolioAnalysisUseCase;
import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskSeverity;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskType;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.mapper.PortfolioAnalysisResponseMapper;

/**
 * Contract test (AR-011, DR-017): the live payloads of {@code GET
 * /api/portfolios/{id}/analysis/latest} conform to
 * {@code implementation/platform/contracts/openapi/openapi.yaml} — NONE/PENDING/RUNNING/COMPLETED/
 * FAILED response shapes all validate; no persistence/provider field leaks.
 */
@WebMvcTest(controllers = PortfolioAnalysisController.class)
@Import({PortfolioAnalysisExceptionHandler.class, PortfolioAnalysisResponseMapper.class})
class PortfolioAnalysisControllerContractTest {

    private static final String CONTRACT = "openapi.yaml";
    private static final UUID PORTFOLIO_ID = UUID.fromString("6f9619ff-8b86-4d11-b42d-00c04fc964ff");
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-05T20:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-05T20:00:07Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioAnalysisQueryUseCase queries;

    @MockitoBean
    private RequestPortfolioAnalysisUseCase requests;

    @Test
    void none_conforms_to_the_contract() throws Exception {
        when(queries.findLatest(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", PORTFOLIO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.requestedAt").doesNotExist());
    }

    @Test
    void pending_conforms_to_the_contract() throws Exception {
        PortfolioAnalysis pending =
                PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC);
        when(queries.findLatest(any())).thenReturn(Optional.of(pending));

        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", PORTFOLIO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.overallDiversification").doesNotExist());
    }

    @Test
    void running_conforms_to_the_contract() throws Exception {
        PortfolioAnalysis running = PortfolioAnalysis
                .requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL)
                .withRunning(REQUESTED_AT.plusSeconds(1));
        when(queries.findLatest(any())).thenReturn(Optional.of(running));

        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", PORTFOLIO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void completed_conforms_to_the_contract_and_leaks_no_provider_metadata() throws Exception {
        PortfolioAnalysisResult result = new PortfolioAnalysisResult(
                DiversificationLevel.MODERATE, "Concentrated in Technology.",
                java.util.List.of(new PortfolioAnalysis.Insight(
                        "SECTOR_EXPOSURE", "Technology represents 76.19% of the Portfolio.", 0)),
                java.util.List.of(new PortfolioAnalysis.Risk(RiskType.SECTOR_CONCENTRATION, RiskSeverity.HIGH,
                        "Sector concentration", "Technology dominates.", 0)),
                "openai", "gpt-4o-mini", "portfolio-analysis", "v1", 512, 180, 692, new BigDecimal("0.002"));
        PortfolioAnalysis completed = PortfolioAnalysis
                .requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT)
                .withCompleted(result, COMPLETED_AT);
        when(queries.findLatest(any())).thenReturn(Optional.of(completed));

        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", PORTFOLIO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overallDiversification.level").value("MODERATE"))
                .andExpect(jsonPath("$.keyInsights[0].type").value("SECTOR_EXPOSURE"))
                .andExpect(jsonPath("$.risks[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.model").doesNotExist())
                .andExpect(jsonPath("$.promptId").doesNotExist());
    }

    @Test
    void failed_conforms_to_the_contract() throws Exception {
        PortfolioAnalysis failed = PortfolioAnalysis
                .requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT)
                .withFailed(FailureReason.PROVIDER_UNAVAILABLE, COMPLETED_AT);
        when(queries.findLatest(any())).thenReturn(Optional.of(failed));

        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", PORTFOLIO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.overallDiversification").doesNotExist());
    }

    @Test
    void an_unknown_portfolio_is_a_404_problem_that_conforms_to_the_contract() throws Exception {
        when(queries.findLatest(any())).thenThrow(new PortfolioNotFoundException(PORTFOLIO_ID));

        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", PORTFOLIO_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-not-found"));
    }

    @Test
    void a_malformed_id_is_rejected_with_400() throws Exception {
        mockMvc.perform(get("/api/portfolios/{id}/analysis/latest", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requesting_a_new_analysis_conforms_to_the_202_contract() throws Exception {
        PortfolioAnalysis requested =
                PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL);
        when(requests.requestManual(PORTFOLIO_ID)).thenReturn(requested);

        mockMvc.perform(post("/api/portfolios/{id}/analysis", PORTFOLIO_ID.toString()))
                .andExpect(status().isAccepted())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.analysisId").value(requested.id().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedAt").value(REQUESTED_AT.toString()));
    }

    @Test
    void a_duplicate_request_is_a_409_problem_that_conforms_to_the_contract() throws Exception {
        when(requests.requestManual(PORTFOLIO_ID))
                .thenThrow(new AnalysisAlreadyInProgressException("already running"));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", PORTFOLIO_ID.toString()))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/analysis-already-in-progress"));
    }

    @Test
    void requesting_analysis_for_an_unknown_portfolio_is_a_404_problem() throws Exception {
        when(requests.requestManual(PORTFOLIO_ID)).thenThrow(new PortfolioNotFoundException(PORTFOLIO_ID));

        mockMvc.perform(post("/api/portfolios/{id}/analysis", PORTFOLIO_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(CONTRACT))
                .andExpect(jsonPath("$.type").value("/problems/portfolio-not-found"));
    }

    @Test
    void a_malformed_id_on_post_is_rejected_with_400() throws Exception {
        mockMvc.perform(post("/api/portfolios/{id}/analysis", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
