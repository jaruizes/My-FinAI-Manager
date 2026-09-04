package com.myfinaimanager.core.ai.business;

import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;

/**
 * The inbound port a future task-specific AI port (e.g. a hypothetical
 * {@code PortfolioAnalysisAiPort}) would call instead of reaching for {@code AiModelPort} directly
 * (FR-002). EN006 defines this port and its one implementation, {@link AiInvocationPolicy}; it
 * defines no task-specific port itself (no business AI feature — FR-059).
 */
public interface GenerateAiUseCase {

    AiResponse generate(AiRequest request);
}
