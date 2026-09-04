package com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentNotResolvedException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver.FinnhubSymbolRule.Strategy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Provider-specific symbol resolution (enabler §12; FR-014). Turns a canonical
 * {@link InstrumentIdentifier} ({@code ticker + MIC}) into the string Finnhub expects, using the
 * committed {@code finnhub-symbol-map.csv} rules. A MIC with no rule, or a result that would be
 * blank, raises {@link InstrumentNotResolvedException} — <strong>no</strong> Finnhub call is ever
 * made with a guessed symbol.
 *
 * <p>The tiny CSV ({@code mic,strategy,suffix}) is parsed with a plain reader — no CSV library.
 */
@Component
public class FinnhubSymbolResolver {

    private final Map<String, FinnhubSymbolRule> rulesByMic;

    public FinnhubSymbolResolver(
            ResourceLoader resourceLoader,
            @Value("${finnhub.symbol-map-file:classpath:reference-data/finnhub-symbol-map.csv}") String location) {
        this.rulesByMic = load(resourceLoader.getResource(location), location);
    }

    /** @throws InstrumentNotResolvedException when the MIC has no symbol rule */
    public String resolve(InstrumentIdentifier id) {
        FinnhubSymbolRule rule = rulesByMic.get(id.market());
        if (rule == null) {
            throw new InstrumentNotResolvedException(
                    "no Finnhub symbol rule for market " + id.market());
        }
        return rule.apply(id.ticker()); // ticker is guaranteed non-blank by InstrumentIdentifier
    }

    private static Map<String, FinnhubSymbolRule> load(Resource resource, String location) {
        if (!resource.exists()) {
            throw new IllegalStateException("Finnhub symbol map not found: " + location);
        }
        Map<String, FinnhubSymbolRule> rules = new HashMap<>();
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split(",", 3); // mic,strategy,suffix — always three fields
                String mic = parts[0].strip().toUpperCase(Locale.ROOT);
                Strategy strategy = Strategy.valueOf(parts[1].strip().toUpperCase(Locale.ROOT));
                String suffix = parts[2].strip();
                rules.put(mic, new FinnhubSymbolRule(mic, strategy, suffix));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("failed to load Finnhub symbol map: " + location, e);
        }
        return Map.copyOf(rules);
    }
}
