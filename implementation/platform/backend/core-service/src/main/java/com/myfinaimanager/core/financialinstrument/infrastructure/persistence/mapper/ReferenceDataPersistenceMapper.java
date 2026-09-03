package com.myfinaimanager.core.financialinstrument.infrastructure.persistence.mapper;

import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.InstrumentIdentity;
import com.myfinaimanager.core.financialinstrument.domain.model.InstrumentType;
import com.myfinaimanager.core.financialinstrument.domain.model.Isin;
import com.myfinaimanager.core.financialinstrument.domain.model.ListingId;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import com.myfinaimanager.core.financialinstrument.domain.model.Provenance;
import com.myfinaimanager.core.financialinstrument.domain.model.SupportedCurrency;
import com.myfinaimanager.core.financialinstrument.domain.model.Ticker;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity.FinancialInstrumentEntity;
import com.myfinaimanager.core.financialinstrument.infrastructure.persistence.entity.MarketEntity;
import org.springframework.stereotype.Component;

/**
 * Explicit domain &lt;-&gt; JPA-entity mapping for the reference data. The domain types stay
 * persistence-agnostic (ADR-003, constitution VI); this is the only place that knows both sides.
 * {@code CHAR} columns are trimmed on read (the domain value objects also trim).
 */
@Component
public class ReferenceDataPersistenceMapper {

    // ---- Market -----------------------------------------------------------------------------

    public MarketEntity toEntity(Market m) {
        Provenance p = m.provenance();
        return new MarketEntity(
                m.mic().value(),
                m.name(),
                m.country().orElse(null),
                m.operatingMic().map(Mic::value).orElse(null),
                m.active(),
                p.source(),
                p.sourceReference(),
                p.lastImportedAt());
    }

    public Market toDomain(MarketEntity e) {
        return Market.reconstitute(
                new Mic(trim(e.getMic())),
                e.getName(),
                trimToNull(e.getCountryIso2()),
                e.getOperatingMic() == null ? null : new Mic(trim(e.getOperatingMic())),
                e.isActive(),
                provenance(e.getSource(), e.getSourceReference(), e.getLastImportedAt()));
    }

    // ---- Financial Instrument listing -----------------------------------------------------

    public FinancialInstrumentEntity toEntity(FinancialInstrumentListing l) {
        Provenance p = l.provenance();
        return new FinancialInstrumentEntity(
                l.id().value(),
                l.name(),
                l.ticker().value(),
                l.market().value(),
                l.currency().name(),
                l.isin().map(Isin::value).orElse(null),
                l.externalReference().orElse(null),
                l.instrumentType().map(Enum::name).orElse(null),
                l.providerSymbol().orElse(null),
                l.active(),
                p.source(),
                p.sourceReference(),
                p.lastImportedAt());
    }

    public FinancialInstrumentListing toDomain(FinancialInstrumentEntity e) {
        InstrumentIdentity identity = new InstrumentIdentity(
                new Ticker(trim(e.getTicker())),
                new Mic(trim(e.getMarketMic())));
        return FinancialInstrumentListing.reconstitute(
                ListingId.of(e.getId()),
                e.getName(),
                identity,
                SupportedCurrency.parse(trim(e.getCurrency())),
                e.getIsin() == null ? null : new Isin(trim(e.getIsin())),
                trimToNull(e.getExternalReference()),
                e.getInstrumentType() == null ? null : InstrumentType.valueOf(trim(e.getInstrumentType())),
                trimToNull(e.getProviderSymbol()),
                e.isActive(),
                provenance(e.getSource(), e.getSourceReference(), e.getLastImportedAt()));
    }

    // ---- helpers -------------------------------------------------------------------------

    private static Provenance provenance(String source, String reference, java.time.Instant at) {
        return Provenance.of(source == null || source.isBlank() ? "UNKNOWN" : source.strip(),
                trimToNull(reference), at);
    }

    private static String trim(String v) {
        return v == null ? null : v.strip();
    }

    private static String trimToNull(String v) {
        String t = trim(v);
        return (t == null || t.isEmpty()) ? null : t;
    }
}
