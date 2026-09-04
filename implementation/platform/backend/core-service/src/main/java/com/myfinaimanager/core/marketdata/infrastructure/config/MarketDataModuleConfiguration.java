package com.myfinaimanager.core.marketdata.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the {@code marketdata} module (EN005). ADR-003 — configuration is infrastructure; the
 * module's {@code @Component} adapters, the {@code FinnhubRestClient}, and the caching decorators are
 * component-scanned. This class only enables {@link FinnhubProperties} and, when no API key is
 * configured, logs {@code event=FinnhubIntegrationDisabled} once at startup so the disabled state is
 * obvious — the application still starts and unrelated capabilities are unaffected (FR-027; AR-045).
 *
 * <p>The adapters inject the platform's existing {@code java.time.Clock} bean; the module declares no
 * second {@code Clock} (OD-EN005-9 — a duplicate would clash under Spring's default no-bean-overriding).
 * All HTTP-client construction (base URL, explicit timeouts) lives in {@code FinnhubRestClient}.
 */
@Configuration
@EnableConfigurationProperties({FinnhubProperties.class, FrankfurterProperties.class})
public class MarketDataModuleConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MarketDataModuleConfiguration.class);

    public MarketDataModuleConfiguration(FinnhubProperties properties) {
        if (!properties.isConfigured()) {
            log.atInfo()
                    .addKeyValue("event", "FinnhubIntegrationDisabled")
                    .addKeyValue("reason", "no-api-key")
                    .log("Finnhub market data integration is disabled (FINNHUB_API_KEY not set); "
                            + "marketdata port calls will throw MarketDataNotConfiguredException");
        }
    }
}
