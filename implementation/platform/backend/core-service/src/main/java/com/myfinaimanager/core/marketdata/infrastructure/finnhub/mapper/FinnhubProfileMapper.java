package com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.Sector;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubCompanyProfileResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Maps a Finnhub {@code /stock/profile2} response to a provider-neutral {@link InstrumentProfile}.
 * {@code finnhubIndustry} → {@code sector} ({@link Sector#UNCLASSIFIED} when absent — never inferred,
 * FR-035); Finnhub {@code exchange} → {@code providerExchange} <strong>metadata string only</strong>
 * (never a MIC — FR-015); {@code currency} → EUR/USD metadata or {@code null}. An empty payload
 * ({@code {}}, Finnhub's answer for an unknown symbol) raises
 * {@link InstrumentProfileUnavailableException}.
 */
@Component
public class FinnhubProfileMapper {

    public InstrumentProfile map(FinnhubCompanyProfileResponse response,
                                 InstrumentIdentifier instrument,
                                 Instant retrievalInstant) {
        if (response.isEmpty()) {
            throw new InstrumentProfileUnavailableException(
                    "provider returned no profile for " + instrument.ticker());
        }
        String ticker = (response.ticker() == null || response.ticker().isBlank())
                ? instrument.ticker()
                : response.ticker();
        String name = (response.name() == null || response.name().isBlank())
                ? instrument.ticker()
                : response.name();
        return new InstrumentProfile(
                ticker,
                name,
                Sector.of(response.finnhubIndustry()),
                null,
                SupportedCurrency.parseOrNull(response.currency()),
                response.exchange(),
                DataSource.FINNHUB,
                retrievalInstant);
    }
}
