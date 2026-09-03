/**
 * Adapter bridging the {@code portfolio} module to the {@code financialinstrument} catalog for
 * FD002 instrument-selection validation. The only {@code portfolio → financialinstrument} coupling
 * (AR-062); it lives here in {@code infrastructure}, behind {@code domain.ports.InstrumentCatalog}.
 */
package com.myfinaimanager.core.portfolio.infrastructure.catalog;
