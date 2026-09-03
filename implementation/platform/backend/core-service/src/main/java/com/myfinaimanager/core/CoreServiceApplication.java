package com.myfinaimanager.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the My-FinAI-Manager platform backend ({@code core-service}).
 *
 * <p>Starts a Spring Boot application that exposes the Actuator health endpoint and connects to
 * PostgreSQL. It introduces no business behaviour. Functional modules live under
 * {@code com.myfinaimanager.core.<module>} and follow the standard Spring architecture of ADR-003:
 * {@code domain} / {@code business} / {@code infrastructure} with dependencies pointing inward,
 * enforced by {@code StandardArchitectureRulesTest}. Current modules:
 * {@code portfolio} (FD001 — create investment portfolio) and
 * {@code financialinstrument} (EN004 — Financial Instrument / Market reference data for FD002).
 */
@SpringBootApplication
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
