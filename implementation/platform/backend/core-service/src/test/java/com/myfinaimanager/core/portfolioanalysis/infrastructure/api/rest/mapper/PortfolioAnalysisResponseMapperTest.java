package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinaimanager.core.portfolioanalysis.domain.model.CreationTrigger;
import com.myfinaimanager.core.portfolioanalysis.domain.model.DiversificationLevel;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskSeverity;
import com.myfinaimanager.core.portfolioanalysis.domain.model.RiskType;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.PortfolioAnalysisResponse;

/**
 * Completed content maps fully; no {@code provider}/{@code model}/{@code promptId}/token/cost
 * field ever serialized (FD005 §28).
 */
class PortfolioAnalysisResponseMapperTest {

    private final PortfolioAnalysisResponseMapper mapper = new PortfolioAnalysisResponseMapper();
    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-05T20:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-05T20:00:07Z");

    private static PortfolioAnalysis completed() {
        PortfolioAnalysisResult result = new PortfolioAnalysisResult(
                DiversificationLevel.MODERATE, "Concentrated in Technology.",
                List.of(new PortfolioAnalysis.Insight("SECTOR_EXPOSURE",
                        "Technology represents 76.19% of the Portfolio.", 0)),
                List.of(new PortfolioAnalysis.Risk(RiskType.SECTOR_CONCENTRATION, RiskSeverity.HIGH,
                        "Sector concentration", "Technology dominates the portfolio.", 0)),
                "openai", "gpt-4o-mini", "portfolio-analysis", "v1", 512, 180, 692, new BigDecimal("0.002"));
        return PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT)
                .withCompleted(result, COMPLETED_AT);
    }

    @Test
    void empty_maps_to_the_explicit_none_placeholder() {
        PortfolioAnalysisResponse response = mapper.toResponse(Optional.empty());

        assertThat(response.status()).isEqualTo("NONE");
        assertThat(response.requestedAt()).isNull();
        assertThat(response.overallDiversification()).isNull();
        assertThat(response.keyInsights()).isNull();
        assertThat(response.risks()).isNull();
    }

    @Test
    void pending_has_no_content_fields() {
        PortfolioAnalysis pending = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL);

        PortfolioAnalysisResponse response = mapper.toResponse(Optional.of(pending));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.requestedAt()).isEqualTo(REQUESTED_AT.toString());
        assertThat(response.completedAt()).isNull();
        assertThat(response.overallDiversification()).isNull();
        assertThat(response.keyInsights()).isNull();
        assertThat(response.risks()).isNull();
    }

    @Test
    void completed_maps_every_content_field() {
        PortfolioAnalysisResponse response = mapper.toResponse(Optional.of(completed()));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.requestedAt()).isEqualTo(REQUESTED_AT.toString());
        assertThat(response.completedAt()).isEqualTo(COMPLETED_AT.toString());
        assertThat(response.overallDiversification().level()).isEqualTo("MODERATE");
        assertThat(response.overallDiversification().explanation()).isEqualTo("Concentrated in Technology.");
        assertThat(response.keyInsights()).hasSize(1);
        assertThat(response.keyInsights().get(0).type()).isEqualTo("SECTOR_EXPOSURE");
        assertThat(response.risks()).hasSize(1);
        assertThat(response.risks().get(0).severity()).isEqualTo("HIGH");
    }

    @Test
    void a_failed_analysis_has_no_content_fields() {
        PortfolioAnalysis failed = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.AUTOMATIC)
                .withRunning(REQUESTED_AT)
                .withFailed(com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason.TIMEOUT,
                        COMPLETED_AT);

        PortfolioAnalysisResponse response = mapper.toResponse(Optional.of(failed));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.overallDiversification()).isNull();
        assertThat(response.keyInsights()).isNull();
        assertThat(response.risks()).isNull();
    }

    @Test
    void no_provider_metadata_field_ever_serializes_even_for_a_completed_analysis() throws Exception {
        String json = new ObjectMapper().writeValueAsString(mapper.toResponse(Optional.of(completed())));

        assertThat(json).doesNotContain("provider", "model", "promptId", "promptVersion", "inputTokens",
                "outputTokens", "totalTokens", "estimatedCost");
    }

    @Test
    void toRequested_maps_the_id_status_and_requestedAt() {
        PortfolioAnalysis pending = PortfolioAnalysis.requested(PORTFOLIO_ID, REQUESTED_AT, CreationTrigger.MANUAL);

        var requested = mapper.toRequested(pending);

        assertThat(requested.analysisId()).isEqualTo(pending.id().toString());
        assertThat(requested.status()).isEqualTo("PENDING");
        assertThat(requested.requestedAt()).isEqualTo(REQUESTED_AT.toString());
    }
}
