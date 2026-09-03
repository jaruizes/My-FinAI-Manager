package com.myfinaimanager.core.financialinstrument.business;

import com.myfinaimanager.core.financialinstrument.business.normalization.NormalizationResult;
import com.myfinaimanager.core.financialinstrument.business.normalization.NormalizationResult.Accepted;
import com.myfinaimanager.core.financialinstrument.business.normalization.NormalizationResult.Rejected;
import com.myfinaimanager.core.financialinstrument.business.normalization.YahooSymbolNormalizer;
import com.myfinaimanager.core.financialinstrument.domain.exceptions.ReferenceDataImportException;
import com.myfinaimanager.core.financialinstrument.domain.model.FinancialInstrumentListing;
import com.myfinaimanager.core.financialinstrument.domain.model.ImportCounters;
import com.myfinaimanager.core.financialinstrument.domain.model.ImportReport;
import com.myfinaimanager.core.financialinstrument.domain.model.InstrumentIdentity;
import com.myfinaimanager.core.financialinstrument.domain.model.InstrumentType;
import com.myfinaimanager.core.financialinstrument.domain.model.Market;
import com.myfinaimanager.core.financialinstrument.domain.model.Mic;
import com.myfinaimanager.core.financialinstrument.domain.model.NewListing;
import com.myfinaimanager.core.financialinstrument.domain.model.NewMarket;
import com.myfinaimanager.core.financialinstrument.domain.model.Provenance;
import com.myfinaimanager.core.financialinstrument.domain.model.RawInstrumentRow;
import com.myfinaimanager.core.financialinstrument.domain.model.Rejection;
import com.myfinaimanager.core.financialinstrument.domain.model.RejectionReason;
import com.myfinaimanager.core.financialinstrument.domain.ports.RawInstrumentSource;
import com.myfinaimanager.core.financialinstrument.domain.ports.RawMarketSource;
import com.myfinaimanager.core.financialinstrument.domain.ports.ReferenceCatalogWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates one reference-data import (EN004 §12; research.md D6, D7). The <strong>whole run</strong>
 * is a single transaction: a hard failure (unreadable source, parser error, DB error) rolls it back
 * so the previously valid catalog is untouched (VC-012), and raises
 * {@link ReferenceDataImportException} carrying the partial {@link ImportReport}. Expected per-row
 * rejections are counted and reported, never persisted, and never fail the run.
 *
 * <p>Flow: load + upsert Markets → for each raw instrument row: normalize
 * ({@link YahooSymbolNormalizer}) → {@code Accepted} ⇒ verify the MIC has a Market row, guard
 * against a same-run {@code (ticker, MIC)} conflict, upsert; {@code Rejected} ⇒ increment the
 * matching counter + append a {@link Rejection}.
 */
@Service
public class ImportReferenceDataService {

    private static final Logger log = LoggerFactory.getLogger(ImportReferenceDataService.class);

    private static final String MARKETS_SOURCE = "MARKETS_CSV";

    private final RawMarketSource marketSource;
    private final RawInstrumentSource instrumentSource;
    private final YahooSymbolNormalizer normalizer;
    private final ReferenceCatalogWriter writer;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ImportReferenceDataService(RawMarketSource marketSource,
                                      RawInstrumentSource instrumentSource,
                                      YahooSymbolNormalizer normalizer,
                                      ReferenceCatalogWriter writer,
                                      PlatformTransactionManager transactionManager,
                                      Clock clock) {
        this.marketSource = marketSource;
        this.instrumentSource = instrumentSource;
        this.normalizer = normalizer;
        this.writer = writer;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @param source the source identifier for the instrument import (e.g. {@code YAHOO_CSV})
     * @return the run outcome (counts + rejections)
     * @throws ReferenceDataImportException on a hard failure — the whole run is rolled back
     */
    public ImportReport run(String source) {
        Instant startedAt = clock.instant();
        Accumulator acc = new Accumulator();
        try {
            tx.executeWithoutResult(status -> importAll(source, startedAt, acc));
        } catch (ReferenceDataImportException e) {
            throw e;
        } catch (RuntimeException e) {
            ImportReport partial = new ImportReport(source, startedAt, clock.instant(),
                    acc.counters(), acc.rejections);
            throw new ReferenceDataImportException("reference-data import failed and was rolled back", partial, e);
        }
        return new ImportReport(source, startedAt, clock.instant(), acc.counters(), acc.rejections);
    }

    private void importAll(String source, Instant runAt, Accumulator acc) {
        Set<String> knownMics = new HashSet<>();
        for (NewMarket nm : marketSource.readMarkets()) {
            Market market = Market.fromRaw(nm, Provenance.of(MARKETS_SOURCE, nm.mic(), runAt));
            writer.upsertMarket(market);
            knownMics.add(market.mic().value());
        }

        Map<InstrumentIdentity, String> seenThisRun = new HashMap<>();
        for (RawInstrumentRow row : instrumentSource.readInstruments()) {
            acc.processed++;
            NormalizationResult result = normalizer.normalize(row.rawSymbol(), row.exchangeCode());

            if (result instanceof Rejected r) {
                acc.count(r.reason());
                acc.rejections.add(new Rejection(row.rawSymbol(), row.exchangeCode(), r.reason(), r.detail()));
                continue;
            }
            Accepted a = (Accepted) result;

            if (!knownMics.contains(a.mic().value())) {
                acc.count(RejectionReason.MIC_UNRESOLVED);
                acc.rejections.add(new Rejection(row.rawSymbol(), row.exchangeCode(),
                        RejectionReason.MIC_UNRESOLVED, "no Market row for MIC " + a.mic().value()));
                continue;
            }

            // instrumentType is best-effort from the name (spec FR-040 — descriptive, not a filter);
            // an inconclusive guess (OTHER) is stored as absent rather than as noise.
            InstrumentType guessed = InstrumentType.fromSource(row.name());
            String typeStr = guessed == InstrumentType.OTHER ? null : guessed.name();

            NewListing nl = new NewListing(row.name(), a.ticker().value(), a.mic().value(),
                    a.currency().name(), null, null, typeStr, a.providerSymbol(), "true");
            FinancialInstrumentListing listing =
                    FinancialInstrumentListing.fromRaw(nl, Provenance.of(source, a.providerSymbol(), runAt));

            String previousName = seenThisRun.putIfAbsent(listing.identity(), listing.name());
            if (previousName != null && !previousName.equals(listing.name())) {
                acc.count(RejectionReason.IDENTITY_CONFLICT);
                acc.rejections.add(new Rejection(row.rawSymbol(), row.exchangeCode(),
                        RejectionReason.IDENTITY_CONFLICT,
                        "identity " + listing.identity() + " already seen this run as '" + previousName + "'"));
                continue;
            }

            switch (writer.upsertListing(listing)) {
                case INSERTED -> acc.imported++;
                case UPDATED -> acc.updated++;
            }
        }
    }

    /** Mutable per-run tally; lives outside the transaction so a partial report survives a rollback. */
    private static final class Accumulator {
        int processed;
        int imported;
        int updated;
        int skippedUnsupportedCurrency;
        int skippedUnsupportedMarket;
        int quarantinedAmbiguous;
        int quarantinedInvalid;
        final List<Rejection> rejections = new ArrayList<>();

        void count(RejectionReason reason) {
            switch (reason) {
                case UNSUPPORTED_CURRENCY -> skippedUnsupportedCurrency++;
                case NO_MAPPING, NOT_SUPPORTED_FOR_FD002, MIC_UNRESOLVED -> skippedUnsupportedMarket++;
                case AMBIGUOUS_EXCHANGE -> quarantinedAmbiguous++;
                case SUFFIX_MISMATCH, EMPTY_TICKER, IDENTITY_CONFLICT -> quarantinedInvalid++;
            }
        }

        ImportCounters counters() {
            return new ImportCounters(processed, imported, updated, skippedUnsupportedCurrency,
                    skippedUnsupportedMarket, quarantinedAmbiguous, quarantinedInvalid);
        }
    }
}
