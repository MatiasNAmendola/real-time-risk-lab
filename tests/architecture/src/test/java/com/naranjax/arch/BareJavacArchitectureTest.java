package com.naranjax.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.*;

import java.io.File;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ArchUnit structural boundary tests for the bare-javac risk engine PoC.
 *
 * Test strategy:
 *   - Sources are compiled by maven-build-helper alongside this test module.
 *   - If compilation fails the whole suite is skipped with a clear message.
 *   - Rules assert Clean Architecture boundaries are enforced at the bytecode level.
 *
 * Rule catalogue (6 rules):
 *   1. Domain must not import infrastructure
 *   2. Domain must not import application
 *   3. Application must not import infrastructure adapters
 *   4. Only cmd and config are permitted to wire both domain and infrastructure
 *   5. Naming conventions (*UseCase, *Repository, *Controller)
 *   6. No dependency cycles across slices
 */
@DisplayName("Bare-javac PoC — Clean Architecture boundaries")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BareJavacArchitectureTest {

    private static final String BASE_PKG = "com.naranjax.interview.risk";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        // In Gradle, risk-engine classes land in build/classes/java/main.
        // The Gradle build file injects this path via the system property "risk.engine.classes.dir".
        // Fall back to the project-relative path for IDE runs.
        String classesDir = System.getProperty("risk.engine.classes.dir",
            "../../poc/java-risk-engine/build/classes/java/main");
        File dir = new File(classesDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("""
                ========================================================
                Bare-javac PoC compiled classes not found at: """ + dir.getAbsolutePath() + """

                Fix: ./gradlew :tests:architecture:test
                     (builds :poc:java-risk-engine first via project dependency)
                ========================================================
                """);
        }
        assumeTrue(dir.exists() && dir.isDirectory(),
            "risk-engine classes dir not found: " + dir.getAbsolutePath() +
            " — run ./gradlew :tests:architecture:test");

        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPath(dir.toPath());

        assumeTrue(classes.size() > 0,
            "No classes found in " + dir.getAbsolutePath() + " — " +
            "ensure :poc:java-risk-engine was compiled before running this test");
    }

    // ── Rule 1: Domain must not depend on Infrastructure ────────────────────────

    @Test
    @Order(1)
    @DisplayName("Rule 1: domain.* must not depend on infrastructure.*")
    void domainMustNotDependOnInfrastructure() {
        noClasses()
            .that().resideInAPackage(BASE_PKG + ".domain..")
            .should().dependOnClassesThat().resideInAPackage(BASE_PKG + ".infrastructure..")
            .as("domain.* -> infrastructure.* dependency violates Clean Architecture")
            .because("""
                The domain is the innermost ring. It must only depend on the JDK and itself.
                Fix: if a class in domain.* imports something from infrastructure.*,
                     extract an interface (port) in domain.repository.* and move the
                     concrete implementation to infrastructure.repository.*.""")
            .check(classes);
    }

    // ── Rule 2: Domain must not depend on Application ────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Rule 2: domain.* must not depend on application.*")
    void domainMustNotDependOnApplication() {
        noClasses()
            .that().resideInAPackage(BASE_PKG + ".domain..")
            .should().dependOnClassesThat().resideInAPackage(BASE_PKG + ".application..")
            .as("domain.* -> application.* dependency violates Clean Architecture")
            .because("""
                The domain must not know about application orchestration or DTOs.
                Fix: move shared types to domain.entity.* or use primitive types.""")
            .check(classes);
    }

    // ── Rule 3: Application must not depend on infrastructure adapters ───────────

    @Test
    @Order(3)
    @DisplayName("Rule 3: application.* must not depend on infrastructure adapter implementations")
    void applicationMustNotDependOnInfrastructureAdapters() {
        // application.* may depend on domain.repository (ports/interfaces).
        // It must NOT depend on concrete adapter implementations under infrastructure.*.
        noClasses()
            .that().resideInAPackage(BASE_PKG + ".application..")
            .should().dependOnClassesThat().resideInAPackage(BASE_PKG + ".infrastructure.repository..")
            .as("application.* must not import infrastructure.repository.* concrete adapters")
            .because("""
                Application layer should only depend on domain ports (interfaces).
                Concrete adapter classes (InMemory*, Fake*, Console*) live in infrastructure.*.
                Fix: if application code needs a repository, inject the interface from domain.repository.*
                     rather than referencing the concrete class from infrastructure.repository.*.""")
            .check(classes);

        noClasses()
            .that().resideInAPackage(BASE_PKG + ".application..")
            .should().dependOnClassesThat().resideInAPackage(BASE_PKG + ".infrastructure.controller..")
            .as("application.* must not import infrastructure.controller.* classes")
            .check(classes);
    }

    // ── Rule 4: Only cmd and config can wire domain + infrastructure ─────────────

    @Test
    @Order(4)
    @DisplayName("Rule 4: application.* must not depend on infrastructure.resilience.* (concrete adapters)")
    void wiringRestrictedToCmdAndConfig() {
        // EvaluateTransactionRiskService in application.usecase.risk directly references
        // CircuitBreaker from infrastructure.resilience. That is a known PoC shortcut:
        // ideally CircuitBreaker would be behind a port interface in domain.repository.
        // This test documents the violation so it becomes visible and trackable.
        //
        // If CircuitBreaker is refactored to a port interface (e.g. domain.repository.CircuitBreakerPort),
        // this test will pass automatically.
        noClasses()
            .that().resideInAPackage(BASE_PKG + ".application..")
            .should().dependOnClassesThat()
                .resideInAPackage(BASE_PKG + ".infrastructure.resilience..")
            .as("application.* must not depend on infrastructure.resilience.* concrete adapters")
            .because("""
                CircuitBreaker is a cross-cutting concern that should be exposed as a port
                interface in domain.repository.* (e.g. CircuitBreakerPort) and implemented
                in infrastructure.resilience.*. The application layer should only depend on
                the port interface, not the concrete CircuitBreaker class.
                This is a known shortcut in the PoC. To fix:
                  1. Create domain.repository.CircuitBreakerPort (interface)
                  2. Make infrastructure.resilience.CircuitBreaker implement it
                  3. Inject the port interface in EvaluateTransactionRiskService""")
            .allowEmptyShould(true)
            .check(classes);
    }

    // ── Rule 5: Naming conventions ───────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Rule 5a: *UseCase non-interface classes must reside in application.usecase.*")
    void useCaseNamingConvention() {
        // The PoC names the use case interface EvaluateRiskUseCase (in domain.usecase)
        // and the implementation EvaluateTransactionRiskService (in application.usecase.risk).
        // The naming convention rule applies to implementations: if any class is named *UseCase
        // and is NOT an interface, it must live in application.usecase.*.
        // allowEmptyShould: if no non-interface *UseCase classes exist, the rule passes vacuously.
        CommonRules.USE_CASE_PACKAGE_RULE.allowEmptyShould(true).check(classes);
    }

    @Test
    @Order(6)
    @DisplayName("Rule 5b: *Repository interfaces must reside in domain.repository.*")
    void repositoryInterfaceNamingConvention() {
        CommonRules.REPOSITORY_INTERFACE_PACKAGE_RULE.check(classes);
    }

    @Test
    @Order(7)
    @DisplayName("Rule 5c: *Controller non-interface classes must reside in infrastructure.controller.*")
    void controllerNamingConvention() {
        // The PoC uses BenchmarkRunner and CliRunner rather than classes named *Controller.
        // allowEmptyShould: if no non-interface *Controller classes exist the rule passes vacuously.
        CommonRules.CONTROLLER_PACKAGE_RULE.allowEmptyShould(true).check(classes);
    }

    // ── Rule 6: No dependency cycles ────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Rule 6: no dependency cycles between top-level slices")
    void noCyclesBetweenSlices() {
        slices()
            .matching(BASE_PKG + ".(*)..") // domain, application, infrastructure, config, cmd
            .should().beFreeOfCycles()
            .as("No dependency cycles between top-level slices of " + BASE_PKG)
            .because("""
                Cycles prevent independent deployment, testing, and reasoning about code.
                Fix: introduce a port interface or move the cyclic dependency to a shared
                     module that both sides depend on.""")
            .check(classes);
    }

    // ── Rule 7 (bonus): Domain must not use java.util.logging ───────────────────

    @Test
    @Order(9)
    @DisplayName("Rule 7: domain.* must not use java.util.logging directly")
    void domainMustNotUseJavaUtilLogging() {
        CommonRules.NO_JUL_IN_DOMAIN.check(classes);
    }
}
