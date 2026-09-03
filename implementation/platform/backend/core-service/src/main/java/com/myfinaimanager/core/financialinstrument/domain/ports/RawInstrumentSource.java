package com.myfinaimanager.core.financialinstrument.domain.ports;

import com.myfinaimanager.core.financialinstrument.domain.model.RawInstrumentRow;
import java.util.List;

/**
 * Outbound port — reads raw instrument rows from a source (the curated Yahoo-shape CSV, for the
 * first version). Implemented by an infrastructure adapter that does file I/O + CSV parsing only;
 * the mapping-driven normalization runs in the business importer (EN004 §18).
 */
public interface RawInstrumentSource {

    List<RawInstrumentRow> readInstruments();
}
