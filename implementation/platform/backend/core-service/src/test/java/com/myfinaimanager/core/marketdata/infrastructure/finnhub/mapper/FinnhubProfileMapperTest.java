package com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentProfileUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.model.Sector;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubCompanyProfileResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinnhubProfileMapperTest {

    private final FinnhubProfileMapper mapper = new FinnhubProfileMapper();
    private static final InstrumentIdentifier AAPL =
            new InstrumentIdentifier("AAPL", "XNAS", SupportedCurrency.USD);
    private static final Instant RETRIEVAL = Instant.parse("2026-09-03T10:00:00Z");

    @Test
    void maps_name_sector_and_currency_metadata() {
        var dto = new FinnhubCompanyProfileResponse("AAPL", "Apple Inc", "USD",
                "NASDAQ NMS - GLOBAL SELECT MARKET", "Technology");

        InstrumentProfile p = mapper.map(dto, AAPL, RETRIEVAL);

        assertThat(p.name()).isEqualTo("Apple Inc");
        assertThat(p.sector().isClassified()).isTrue();
        assertThat(p.sector().classification()).isEqualTo("Technology");
        assertThat(p.currency()).isEqualTo(SupportedCurrency.USD);
        assertThat(p.source()).isEqualTo(DataSource.FINNHUB);
    }

    @Test
    void the_provider_exchange_string_is_carried_verbatim_and_is_never_a_mic() {
        var dto = new FinnhubCompanyProfileResponse("AAPL", "Apple Inc", "USD",
                "NASDAQ NMS - GLOBAL SELECT MARKET", "Technology");
        InstrumentProfile p = mapper.map(dto, AAPL, RETRIEVAL);

        assertThat(p.providerExchange()).isEqualTo("NASDAQ NMS - GLOBAL SELECT MARKET");
        // never coerced to an ISO 10383 MIC
        assertThat(p.providerExchange()).isNotEqualTo("XNAS");
    }

    @Test
    void a_missing_industry_is_unclassified_never_inferred() {
        var dto = new FinnhubCompanyProfileResponse("AAPL", "Apple Inc", "USD", "NASDAQ", null);
        assertThat(mapper.map(dto, AAPL, RETRIEVAL).sector()).isEqualTo(Sector.UNCLASSIFIED);

        var blank = new FinnhubCompanyProfileResponse("AAPL", "Apple Inc", "USD", "NASDAQ", "   ");
        assertThat(mapper.map(blank, AAPL, RETRIEVAL).sector()).isEqualTo(Sector.UNCLASSIFIED);
    }

    @Test
    void a_non_eur_usd_currency_becomes_null_metadata() {
        var dto = new FinnhubCompanyProfileResponse("HSBA", "HSBC", "GBP", "LSE", "Financial Services");
        assertThat(mapper.map(dto, new InstrumentIdentifier("HSBA", "XLON", SupportedCurrency.EUR), RETRIEVAL)
                .currency()).isNull();
    }

    @Test
    void falls_back_to_the_instrument_ticker_when_the_provider_omits_ticker_and_name() {
        var dto = new FinnhubCompanyProfileResponse(null, null, "USD", "NASDAQ", "Technology");
        InstrumentProfile p = mapper.map(dto, AAPL, RETRIEVAL);
        assertThat(p.ticker()).isEqualTo("AAPL");
        assertThat(p.name()).isEqualTo("AAPL");
    }

    @Test
    void an_empty_profile_payload_is_unavailable() {
        assertThatThrownBy(() -> mapper.map(new FinnhubCompanyProfileResponse(null, null, null, null, null),
                AAPL, RETRIEVAL)).isInstanceOf(InstrumentProfileUnavailableException.class);
    }
}
