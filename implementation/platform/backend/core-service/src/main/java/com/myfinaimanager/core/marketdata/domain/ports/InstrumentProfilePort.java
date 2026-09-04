package com.myfinaimanager.core.marketdata.domain.ports;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentNotResolvedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;

/**
 * Outbound port: obtain provider profile / sector classification for a financial instrument,
 * provider-neutral (EN005). Independently replaceable.
 *
 * <p>Contract: {@code specs/EN005-establish-finnhub-market-data-integration/contracts/market-data-ports.md} §C2.
 * A missing sector is {@code Sector.UNCLASSIFIED} (never inferred); the provider exchange string is
 * metadata only and never replaces the canonical ISO 10383 MIC.
 */
public interface InstrumentProfilePort {

    /**
     * @return an {@link InstrumentProfile} with a non-null {@code sector}
     * @throws InstrumentProfileUnavailableException no usable profile right now
     * @throws ProviderRateLimitedException          the provider throttled the call (HTTP 429)
     * @throws ProviderAuthenticationFailedException the provider rejected the credentials (401/403)
     * @throws InstrumentNotResolvedException        the instrument has no valid provider symbol
     * @throws MarketDataNotConfiguredException      no API key is configured in this environment
     */
    InstrumentProfile getProfile(InstrumentIdentifier instrument);
}
