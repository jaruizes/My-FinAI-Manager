package com.myfinaimanager.core.financialinstrument.infrastructure.config;

import com.myfinaimanager.core.financialinstrument.business.ImportReferenceDataService;
import com.myfinaimanager.core.financialinstrument.domain.exceptions.ReferenceDataImportException;
import com.myfinaimanager.core.financialinstrument.domain.model.ImportCounters;
import com.myfinaimanager.core.financialinstrument.domain.model.ImportReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs one reference-data import when the application starts (EN004 §22; research.md D11).
 *
 * <p>Guarded by {@code app.reference-data.import-on-startup} ({@code true} by default; {@code false}
 * in test slices). The import is idempotent — re-running on every boot converges the catalog with
 * zero new rows once stable. Any failure is logged as {@code event=ReferenceDataImportFailed} and
 * swallowed: the application still starts and serves whatever catalog is already persisted, so a
 * bad source file can never take the platform down.
 */
@Component
@ConditionalOnProperty(name = "app.reference-data.import-on-startup", havingValue = "true", matchIfMissing = true)
public class ReferenceDataBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataBootstrapRunner.class);

    private static final String SOURCE = "YAHOO_CSV";

    private final ImportReferenceDataService importReferenceData;

    public ReferenceDataBootstrapRunner(ImportReferenceDataService importReferenceData) {
        this.importReferenceData = importReferenceData;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            logCompleted(importReferenceData.run(SOURCE));
        } catch (ReferenceDataImportException e) {
            logFailed(e, e.partialReport());
        } catch (RuntimeException e) {
            logFailed(e, null);
        }
    }

    private void logCompleted(ImportReport report) {
        ImportCounters c = report.counters();
        log.atInfo()
                .addKeyValue("event", "ReferenceDataImportCompleted")
                .addKeyValue("source", report.source())
                .addKeyValue("startedAt", report.startedAt())
                .addKeyValue("finishedAt", report.finishedAt())
                .addKeyValue("processed", c.processed())
                .addKeyValue("imported", c.imported())
                .addKeyValue("updated", c.updated())
                .addKeyValue("skippedUnsupportedCurrency", c.skippedUnsupportedCurrency())
                .addKeyValue("skippedUnsupportedMarket", c.skippedUnsupportedMarket())
                .addKeyValue("quarantinedAmbiguous", c.quarantinedAmbiguous())
                .addKeyValue("quarantinedInvalid", c.quarantinedInvalid())
                .log("Reference-data import completed");
        report.rejections().forEach(r -> log.atDebug()
                .addKeyValue("event", "ReferenceDataRowRejected")
                .addKeyValue("rawSymbol", r.rawSymbol())
                .addKeyValue("sourceExchangeCode", r.sourceExchangeCode())
                .addKeyValue("reason", r.reason())
                .addKeyValue("detail", r.detail())
                .log("Reference-data row rejected"));
    }

    private void logFailed(RuntimeException e, ImportReport partial) {
        var entry = log.atError()
                .addKeyValue("event", "ReferenceDataImportFailed")
                .addKeyValue("source", SOURCE)
                .addKeyValue("error", e.getMessage());
        if (partial != null) {
            entry = entry
                    .addKeyValue("processed", partial.counters().processed())
                    .addKeyValue("imported", partial.counters().imported())
                    .addKeyValue("updated", partial.counters().updated());
        }
        entry.log("Reference-data import failed; the application will continue with the existing catalog");
    }
}
