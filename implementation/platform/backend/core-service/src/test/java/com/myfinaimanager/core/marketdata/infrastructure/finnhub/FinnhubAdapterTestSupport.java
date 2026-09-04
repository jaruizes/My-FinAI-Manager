package com.myfinaimanager.core.marketdata.infrastructure.finnhub;

import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.client.FinnhubRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver.FinnhubSymbolResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Shared helpers for the Finnhub adapter tests: a {@code MockRestServiceServer}-bound client and a fixed clock. */
final class FinnhubAdapterTestSupport {

    static final String BASE_URL = "https://finnhub.io/api/v1";
    static final String KEY = "test-key-abc123";
    static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
    static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FinnhubAdapterTestSupport() {
    }

    record Bound(FinnhubRestClient client, MockRestServiceServer server) {
    }

    static Bound boundClient(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinnhubProperties props = new FinnhubProperties(apiKey, URI.create(BASE_URL),
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                new FinnhubProperties.Cache(Duration.ofSeconds(45), Duration.ofHours(24)));
        return new Bound(new FinnhubRestClient(builder.build(), props), server);
    }

    static FinnhubSymbolResolver realResolver() {
        return new FinnhubSymbolResolver(new DefaultResourceLoader(),
                "classpath:reference-data/finnhub-symbol-map.csv");
    }

    /** Load a synthetic Finnhub wire fixture from {@code src/test/resources/finnhub/} (contracts/finnhub-provider-contract.md §5). */
    static String fixture(String name) {
        try (var in = FinnhubAdapterTestSupport.class.getResourceAsStream("/finnhub/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing test fixture: finnhub/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
