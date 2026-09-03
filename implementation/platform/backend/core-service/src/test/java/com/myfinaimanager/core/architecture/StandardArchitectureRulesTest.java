package com.myfinaimanager.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Standard Spring backend architecture conformance (ADR-003 §"Architecture Verification" / EN003 §16).
 *
 * <p>Every functional module under {@code com.myfinaimanager.core} uses the three areas
 * {@code domain} / {@code business} / {@code infrastructure} with dependencies pointing inward:
 * {@code infrastructure -> business -> domain} (infrastructure may also depend directly on domain).
 * The {@code portfolio} and {@code financialinstrument} modules both have classes in all three
 * areas, so the {@code ..core.(*)..} rules are substantive for each.
 */
@AnalyzeClasses(
        packages = "com.myfinaimanager.core",
        importOptions = ImportOption.DoNotIncludeTests.class)
class StandardArchitectureRulesTest {

    // ---- Mandatory dependency direction (ADR-003 / EN003 §16) --------------------------------

    @ArchTest
    static final ArchRule domain_does_not_depend_on_business =
            noClasses().that().resideInAPackage("..core.(*).domain..")
                    .should().dependOnClassesThat().resideInAPackage("..core.(*).business..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..core.(*).domain..")
                    .should().dependOnClassesThat().resideInAPackage("..core.(*).infrastructure..");

    @ArchTest
    static final ArchRule business_does_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("..core.(*).business..")
                    .should().dependOnClassesThat().resideInAPackage("..core.(*).infrastructure..");

    // ---- Domain stays framework-free (VC-004) -----------------------------------------------

    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
            noClasses().that().resideInAPackage("..core..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "com.fasterxml.jackson..",
                            "jakarta.servlet..",
                            "java.sql..",
                            "javax.sql..",
                            "org.apache.kafka..",
                            "org.apache.commons..");   // EN004: CSV parsing is an infrastructure concern

    // ---- Adapter placement (ADR-003 §16 — VC-007, VC-009) ----------------------------------

    @ArchTest
    static final ArchRule jpa_entities_live_in_infrastructure_persistence_entity =
            classes().that().areAnnotatedWith("jakarta.persistence.Entity")
                    .should().resideInAPackage("..infrastructure.persistence.entity..");

    @ArchTest
    static final ArchRule spring_data_repositories_live_in_infrastructure_persistence_repository =
            classes().that().areAssignableTo("org.springframework.data.repository.Repository")
                    .and().areInterfaces()
                    .should().resideInAPackage("..infrastructure.persistence.repository..");

    @ArchTest
    static final ArchRule rest_controllers_live_in_infrastructure_api_rest =
            classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().resideInAPackage("..infrastructure.api.rest..");

    // REST DTO <-> business/domain mapping is part of the REST adapter (ADR-003 amendment
    // 2026-09-02 / AR-058): it lives in ..infrastructure.api.rest.mapper.., not a separate
    // ..infrastructure.api.mapper.. sibling package.
    @ArchTest
    static final ArchRule rest_mappers_live_in_infrastructure_api_rest_mapper =
            classes().that().resideInAPackage("..infrastructure.api..")
                    .and().haveSimpleNameEndingWith("Mapper")
                    .should().resideInAPackage("..infrastructure.api.rest.mapper..");

    @ArchTest
    static final ArchRule rest_dtos_are_not_used_by_domain_or_business =
            noClasses().that().resideInAnyPackage("..core..domain..", "..core..business..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure.api.rest.dto..");

    @ArchTest
    static final ArchRule domain_does_not_use_spring_data_or_jpa =
            noClasses().that().resideInAPackage("..core..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.data..", "jakarta.persistence..", "org.hibernate..");

    // Messaging area is not present yet — declared so a future messaging adapter has a rule.
    @ArchTest
    static final ArchRule messaging_framework_classes_live_in_infrastructure_messaging =
            noClasses().that().resideOutsideOfPackage("..infrastructure.messaging..")
                    .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
                    .allowEmptyShould(true);

    // EN004: CSV parsing (Apache Commons CSV) is a leaf infrastructure utility — it must not leak
    // into domain or business (the normalizer works on already-parsed rows).
    @ArchTest
    static final ArchRule csv_parsing_is_confined_to_infrastructure =
            noClasses().that().resideOutsideOfPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("org.apache.commons.csv..");
}
