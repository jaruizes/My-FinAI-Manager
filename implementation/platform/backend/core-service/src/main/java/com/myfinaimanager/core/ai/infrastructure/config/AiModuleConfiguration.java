package com.myfinaimanager.core.ai.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings;
import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings.TokenLimits;

/**
 * Maps the Spring-bound {@link AiProperties} to the domain-safe {@link AiInvocationSettings} bean
 * that {@code ai.business} depends on — {@code AiProperties} itself never crosses into
 * {@code ai.business} or {@code ai.domain} (ADR-003; research D-plan OD-3).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiModuleConfiguration {

    @Bean
    public AiInvocationSettings aiInvocationSettings(AiProperties properties) {
        return new AiInvocationSettings(
                properties.defaultProvider(),
                properties.defaultModel(),
                new TokenLimits(
                        properties.limits().maxInputTokens(),
                        properties.limits().maxOutputTokens(),
                        properties.limits().maxTotalTokens(),
                        properties.limits().maxInputCharacters()),
                properties.limits().maxEstimatedCost(),
                properties.timeout().read(),
                properties.retry().maxAttempts(),
                properties.retry().backoff());
    }
}
