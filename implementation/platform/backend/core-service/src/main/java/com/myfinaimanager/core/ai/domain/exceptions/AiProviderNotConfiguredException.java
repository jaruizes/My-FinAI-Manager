package com.myfinaimanager.core.ai.domain.exceptions;

/**
 * The configured provider adapter is not usable because its required credential/configuration is
 * absent (e.g. a blank API key) — mirrors {@code MarketDataNotConfiguredException} (EN005). No
 * outbound call is made; never retried (FR-034, FR-037; FD005 research D3).
 */
public class AiProviderNotConfiguredException extends AiException {

    public AiProviderNotConfiguredException(String message) {
        super(message);
    }
}
