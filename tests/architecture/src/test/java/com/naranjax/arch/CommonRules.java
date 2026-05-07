package com.naranjax.arch;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Shared naming-convention and stereotype rules used by both PoC test suites.
 *
 * Each constant is a self-contained ArchUnit rule that can be re-used
 * in any test class via rule.check(importedClasses).
 */
public final class CommonRules {

    private CommonRules() {}

    // ─── Bare-javac naming conventions ──────────────────────────────────────────

    /**
     * Classes named *UseCase must live in an application.usecase sub-package.
     * This prevents accidental proliferation of use-case classes in domain or
     * infrastructure packages.
     */
    public static final ArchRule USE_CASE_PACKAGE_RULE =
        classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .and().areNotInterfaces()  // allow port interfaces named UseCase
            .should().resideInAPackage("..application.usecase..")
            .as("Classes named *UseCase (non-interface) must reside in application.usecase.*")
            .because("application.usecase.* is the designated home for use-case implementations");

    /**
     * Interface *Repository must live in domain.repository.
     * Concrete *Repository implementations must live in infrastructure.repository.
     */
    public static final ArchRule REPOSITORY_INTERFACE_PACKAGE_RULE =
        classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .should().resideInAPackage("..domain.repository..")
            .as("Repository port interfaces must reside in domain.repository.*")
            .because("domain.repository.* holds ports; infrastructure.repository.* holds adapters");

    /**
     * *Controller implementations must live in infrastructure.controller.
     */
    public static final ArchRule CONTROLLER_PACKAGE_RULE =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .and().areNotInterfaces()
            .should().resideInAPackage("..infrastructure.controller..")
            .as("*Controller classes must reside in infrastructure.controller.*");

    // ─── General coding rules (no System.exit in domain/application) ────────────

    /**
     * Domain and application code must not use java.util.logging directly.
     * They should depend on port interfaces (ClockPort, structured logger, etc.)
     */
    public static final ArchRule NO_JUL_IN_DOMAIN =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().accessClassesThat().resideInAPackage("java.util.logging..")
            .as("Domain must not use java.util.logging — use port interfaces")
            .because("logging is an infrastructure concern");

    // ─── Vert.x module naming conventions ───────────────────────────────────────

    /**
     * Vert.x Main entry points must be in the module's root package.
     * Prevents wiring code from leaking into inner packages.
     */
    public static final ArchRule VERTX_MAIN_IN_ROOT_PACKAGE =
        classes()
            .that().haveSimpleNameEndingWith("Main")
            .should().resideInAPackage("com.naranjax.distributed.*")
            .as("*Main classes must be in the module root package (com.naranjax.distributed.*)")
            .because("entry points belong at the root of each module's namespace");
}
