package com.myfinaimanager.core.ai.infrastructure.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings;
import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings.TokenLimits;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.config.OpenAiProperties;

/**
 * Maps the Spring-bound {@link AiProperties} to the domain-safe {@link AiInvocationSettings} bean
 * that {@code ai.business} depends on — {@code AiProperties} itself never crosses into
 * {@code ai.business} or {@code ai.domain} (ADR-003; research D-plan OD-3). Also registers {@link
 * OpenAiProperties} (FD005 research D3) — bound here so the {@code openai} provider package stays
 * free of its own {@code @EnableConfigurationProperties} declaration.
 */
@Configuration
@EnableConfigurationProperties({AiProperties.class, OpenAiProperties.class})
public class AiModuleConfiguration {

    @Bean
    public AiInvocationSettings aiInvocationSettings(AiProperties properties) {
        Map<String, String> taskProviders = properties.tasks().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().provider()));
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
                properties.retry().backoff(),
                taskProviders);
    }
}
