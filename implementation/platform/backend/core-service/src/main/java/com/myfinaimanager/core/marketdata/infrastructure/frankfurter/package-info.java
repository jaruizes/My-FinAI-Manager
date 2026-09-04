/**
 * Frankfurter FX-rate provider adapter (EN005 Revision 2). Frankfurter serves ECB reference rates
 * via a keyless public API. {@code FrankfurterFxRateAdapter} implements
 * {@code marketdata.domain.ports.FxRatePort}; all Frankfurter HTTP / DTO / mapping is confined here
 * (ArchUnit-enforced). No secret is introduced (VC-011).
 */
package com.myfinaimanager.core.marketdata.infrastructure.frankfurter;
