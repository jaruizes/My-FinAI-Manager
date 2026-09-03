package com.myfinaimanager.core.financialinstrument.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the {@code financialinstrument} module (EN004). Component scanning
 * ({@code @SpringBootApplication} root {@code com.myfinaimanager.core}) picks up the module's
 * {@code @Service} / {@code @Repository} / {@code @Component} / {@code @RestController} beans; this
 * class only enables {@link ReferenceDataProperties}. Any additional explicit bean for the module
 * belongs here (never in {@code domain}).
 */
@Configuration
@EnableConfigurationProperties(ReferenceDataProperties.class)
public class FinancialInstrumentModuleConfiguration {
}
