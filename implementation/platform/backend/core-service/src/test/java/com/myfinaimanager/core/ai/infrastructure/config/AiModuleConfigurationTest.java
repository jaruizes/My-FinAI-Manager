package com.myfinaimanager.core.ai.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.myfinaimanager.core.ai.domain.model.AiInvocationSettings;

/** research D-plan OD-3 — {@code AiProperties} (Spring) → {@code AiInvocationSettings} (domain). */
class AiModuleConfigurationTest {

    @Test
    void maps_every_property_to_the_domain_safe_settings_record() {
        AiProperties properties = new AiProperties(
                "local",
                "local-deterministic-v1",
                new AiProperties.Limits(8000, 1000, 9000, 20000, new BigDecimal("0.50")),
                new AiProperties.Timeout(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                new AiProperties.Retry(2, Duration.ofMillis(200)));

        AiInvocationSettings settings = new AiModuleConfiguration().aiInvocationSettings(properties);

        assertThat(settings.defaultProvider()).isEqualTo("local");
        assertThat(settings.defaultModel()).isEqualTo("local-deterministic-v1");
        assertThat(settings.limits().maxInputTokens()).isEqualTo(8000);
        assertThat(settings.limits().maxOutputTokens()).isEqualTo(1000);
        assertThat(settings.limits().maxTotalTokens()).isEqualTo(9000);
        assertThat(settings.limits().maxInputCharacters()).isEqualTo(20000);
        assertThat(settings.maxEstimatedCost()).isEqualByComparingTo("0.50");
        assertThat(settings.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.maxRetryAttempts()).isEqualTo(2);
        assertThat(settings.retryBackoff()).isEqualTo(Duration.ofMillis(200));
    }
}
