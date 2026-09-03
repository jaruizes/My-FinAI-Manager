package com.myfinaimanager.core.financialinstrument.domain.exceptions;

import com.myfinaimanager.core.financialinstrument.domain.model.ImportReport;

/**
 * A <strong>hard</strong> failure of a reference-data import run — an unreadable source, a parser
 * error, or a database error (EN004 §22). The whole run is rolled back (fail-safe: the previously
 * valid catalog is untouched — VC-012). Carries the partial {@link ImportReport} accumulated up to
 * the failure so diagnostics can identify the offending record(s).
 */
public class ReferenceDataImportException extends RuntimeException {

    private final transient ImportReport partialReport;

    public ReferenceDataImportException(String message, ImportReport partialReport, Throwable cause) {
        super(message, cause);
        this.partialReport = partialReport;
    }

    public ReferenceDataImportException(String message, ImportReport partialReport) {
        this(message, partialReport, null);
    }

    /** The counts + rejections accumulated before the run aborted; may be {@code null}. */
    public ImportReport partialReport() {
        return partialReport;
    }
}
