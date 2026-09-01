package com.myfinaimanager.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hexagonal Architecture conformance guardrail for {@code core-service} (FR-027, AR-001..AR-003).
 *
 * <p>EN001 creates no production domain/application classes, so these rules are currently
 * satisfied vacuously ({@code allowEmptyShould(true)}). They are active guardrails: the first time
 * FD001 (or anyone) adds a class under {@code ..platform..domain..} or {@code ..platform..application..}
 * that imports Spring, JDBC, or an adapter/bootstrap package, the build fails here.
 */
@AnalyzeClasses(
        packages = "com.myfinaimanager.core",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_depends_on_nothing_outward =
            noClasses()
                    .that().resideInAPackage("..platform..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..platform..application..",
                            "..platform..adapter..",
                            "com.myfinaimanager.core.bootstrap..",
                            "org.springframework..",
                            "jakarta.persistence..",
                            "java.sql..",
                            "javax.sql..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters_or_framework =
            noClasses()
                    .that().resideInAPackage("..platform..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..platform..adapter..",
                            "com.myfinaimanager.core.bootstrap..",
                            "org.springframework..",
                            "java.sql..",
                            "javax.sql..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule inbound_adapters_do_not_depend_on_outbound_adapters =
            noClasses()
                    .that().resideInAPackage("..platform..adapter.in..")
                    .should().dependOnClassesThat().resideInAPackage("..platform..adapter.out..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_does_not_depend_on_adapters =
            noClasses()
                    .that().resideInAPackage("..platform..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..platform..adapter..")
                    .allowEmptyShould(true);
}
