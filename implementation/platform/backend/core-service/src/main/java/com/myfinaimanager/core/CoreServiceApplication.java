package com.myfinaimanager.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the My-FinAI-Manager platform backend ({@code core-service}).
 *
 * <p>EN001 bootstrap baseline: this is the only production class in the module. It starts a Spring
 * Boot application that exposes the Actuator health endpoint and connects to PostgreSQL. It
 * introduces no business behaviour. Real domain modules are added by Feature Definitions, starting
 * with FD001 — Create Investment Portfolio, under {@code com.myfinaimanager.core.<capability>}
 * following the Hexagonal Architecture convention documented in the {@code platform} packages.
 */
@SpringBootApplication
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
