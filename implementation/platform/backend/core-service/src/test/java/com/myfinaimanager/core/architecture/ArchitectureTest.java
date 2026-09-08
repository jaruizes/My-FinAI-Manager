package com.myfinaimanager.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforces the mandatory Spring architecture boundaries from
 * {@code reference/engineering/architecture-rules.md} (AAC-001..AAC-004, AAC-022)
 * for the {@code core-service} backend. EN001 AC-002.
 *
 * <p>Written as plain JUnit 5 tests (rules checked directly) rather than via the
 * ArchUnit JUnit engine, so the rules run as part of the standard Surefire build.
 */
class ArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.myfinaimanager.core");
    }

    @Test
    void domainDoesNotDependOnBusiness() {
        check(noClasses().that().resideInAPackage("..platform.domain..")
                .should().dependOnClassesThat().resideInAPackage("..platform.business.."));
    }

    @Test
    void domainDoesNotDependOnInfrastructure() {
        check(noClasses().that().resideInAPackage("..platform.domain..")
                .should().dependOnClassesThat().resideInAPackage("..platform.infrastructure.."));
    }

    @Test
    void businessDoesNotDependOnInfrastructure() {
        check(noClasses().that().resideInAPackage("..platform.business..")
                .should().dependOnClassesThat().resideInAPackage("..platform.infrastructure.."));
    }

    @Test
    void domainIsFreeOfFrameworkAndPersistenceTypes() {
        check(noClasses().that().resideInAPackage("..platform.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..",
                        "java.sql..",
                        "javax.sql..")
                .as("domain must not depend on Spring, JPA, HTTP, Hibernate, "
                        + "serialization, or database-client types"));
    }

    @Test
    void restControllersLiveUnderInfrastructureApiRest() {
        check(classes().that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..platform.infrastructure.api.rest.."));
    }

    @Test
    void springDataRepositoriesLiveUnderInfrastructurePersistence() {
        check(classes().that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .should().resideInAPackage("..platform.infrastructure.persistence.."));
    }

    @Test
    void jpaEntitiesLiveUnderInfrastructurePersistence() {
        check(classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage("..platform.infrastructure.persistence.."));
    }

    @Test
    void onlyThePlatformFunctionalModuleExists() {
        check(classes().that().resideInAPackage("com.myfinaimanager.core..")
                .should().resideInAnyPackage(
                        "com.myfinaimanager.core",
                        "com.myfinaimanager.core.platform..")
                .as("the bootstrap backend contains only the 'platform' functional module "
                        + "(no product modules — EN001 BR-002)"));
    }

    private static void check(ArchRule rule) {
        rule.check(productionClasses);
    }
}
