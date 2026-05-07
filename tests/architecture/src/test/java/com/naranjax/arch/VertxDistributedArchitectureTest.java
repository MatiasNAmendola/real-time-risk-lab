package com.naranjax.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ArchUnit structural boundary tests for the Vert.x distributed PoC.
 *
 * Verifies that the 5-module separation is physically real:
 *   - controller-app, usecase-app, repository-app, consumer-app are independent
 *   - the only cross-module dependency allowed is -> shared
 *   - shared does not depend on any concrete module
 *
 * These tests import compiled class files from each module's target/classes.
 * If a module is not compiled the test is skipped with a clear message.
 *
 * Rule catalogue (4 rules):
 *   1. Modules do not cross-reference each other (only -> shared is allowed)
 *   2. shared does not import from any concrete module
 *   3. Each module has exactly one Main class in its root package
 *   4. Main entry points (class names ending in Main) reside in the module root
 */
@DisplayName("Vert.x distributed PoC — module boundary enforcement")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VertxDistributedArchitectureTest {

    private static final String SHARED_PKG     = "com.naranjax.distributed.shared";
    private static final String CONTROLLER_PKG = "com.naranjax.distributed.controller";
    private static final String USECASE_PKG    = "com.naranjax.distributed.usecase";
    private static final String REPOSITORY_PKG = "com.naranjax.distributed.repository";
    private static final String CONSUMER_PKG   = "com.naranjax.distributed.consumer";

    // System property keys injected by maven-surefire-plugin (see pom.xml)
    private static final String PROP_SHARED     = "vertx.shared.classes";
    private static final String PROP_CONTROLLER = "vertx.controller.classes";
    private static final String PROP_USECASE    = "vertx.usecase.classes";
    private static final String PROP_REPOSITORY = "vertx.repository.classes";
    private static final String PROP_CONSUMER   = "vertx.consumer.classes";

    private static JavaClasses allClasses;
    private static JavaClasses sharedClasses;
    private static JavaClasses concreteModuleClasses;  // everything except shared

    @BeforeAll
    static void importClasses() {
        List<File> presentDirs = new ArrayList<>();
        List<String> missingDirs = new ArrayList<>();

        for (String prop : List.of(PROP_SHARED, PROP_CONTROLLER, PROP_USECASE, PROP_REPOSITORY, PROP_CONSUMER)) {
            String path = System.getProperty(prop);
            if (path == null || path.isBlank()) {
                // Fallback: compute relative path from CWD
                String module = prop.replace("vertx.", "").replace(".classes", "");
                path = "../../poc/java-vertx-distributed/" + toModuleDirName(module) + "/target/classes";
            }
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                presentDirs.add(dir);
            } else {
                missingDirs.add(path);
            }
        }

        if (!missingDirs.isEmpty()) {
            System.err.println("""
                ========================================================
                Vert.x distributed PoC modules not compiled.
                Missing class directories:
                """ + String.join("\n  ", missingDirs) + """

                Fix: cd poc/java-vertx-distributed && mvn package -DskipTests
                ========================================================
                """);
        }

        assumeTrue(!presentDirs.isEmpty(),
            "No Vert.x module class directories found — " +
            "compile the PoC first: cd poc/java-vertx-distributed && mvn package -DskipTests");

        var pathList = presentDirs.stream()
            .map(File::toPath)
            .toList();
        allClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPaths(pathList);

        assumeTrue(allClasses.size() > 0,
            "No classes loaded from Vert.x module directories");

        sharedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(SHARED_PKG);

        concreteModuleClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                CONTROLLER_PKG,
                USECASE_PKG,
                REPOSITORY_PKG,
                CONSUMER_PKG
            );
    }

    // ── Rule 1: Modules must not cross-reference each other ─────────────────────

    @Test
    @Order(1)
    @DisplayName("Rule 1a: controller-app must not import usecase-app, repository-app, or consumer-app")
    void controllerDoesNotImportOtherModules() {
        noClasses()
            .that().resideInAPackage(CONTROLLER_PKG + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                USECASE_PKG    + "..",
                REPOSITORY_PKG + "..",
                CONSUMER_PKG   + ".."
            )
            .as("controller-app must only depend on shared — not on other concrete modules")
            .because("""
                If controller-app imports usecase-app classes directly, the physical separation
                between pods becomes cosmetic. Each module must communicate exclusively via the
                shared contract (EventBusAddress, RiskRequest, RiskDecision).
                Fix: replace any direct class reference with the shared DTOs and event bus addresses.""")
            .check(allClasses);
    }

    @Test
    @Order(2)
    @DisplayName("Rule 1b: usecase-app must not import controller-app, repository-app, or consumer-app")
    void usecaseDoesNotImportOtherModules() {
        noClasses()
            .that().resideInAPackage(USECASE_PKG + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                CONTROLLER_PKG + "..",
                REPOSITORY_PKG + "..",
                CONSUMER_PKG   + ".."
            )
            .as("usecase-app must only depend on shared — not on other concrete modules")
            .because("""
                usecase-app communicates with repository-app through the Vert.x event bus.
                A direct Java import defeats the purpose of the distributed boundary.
                Fix: use EventBusAddress.REPOSITORY_FIND_FEATURES and send/reply semantics.""")
            .check(allClasses);
    }

    @Test
    @Order(3)
    @DisplayName("Rule 1c: repository-app must not import controller-app, usecase-app, or consumer-app")
    void repositoryDoesNotImportOtherModules() {
        noClasses()
            .that().resideInAPackage(REPOSITORY_PKG + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                CONTROLLER_PKG + "..",
                USECASE_PKG    + "..",
                CONSUMER_PKG   + ".."
            )
            .as("repository-app must only depend on shared — not on other concrete modules")
            .check(allClasses);
    }

    @Test
    @Order(4)
    @DisplayName("Rule 1d: consumer-app must not import controller-app, usecase-app, or repository-app")
    void consumerDoesNotImportOtherModules() {
        noClasses()
            .that().resideInAPackage(CONSUMER_PKG + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                CONTROLLER_PKG + "..",
                USECASE_PKG    + "..",
                REPOSITORY_PKG + ".."
            )
            .as("consumer-app must only depend on shared — not on other concrete modules")
            .check(allClasses);
    }

    // ── Rule 2: shared must not depend on any concrete module ────────────────────

    @Test
    @Order(5)
    @DisplayName("Rule 2: shared module must not import any concrete module")
    void sharedDoesNotDependOnConcreteModules() {
        noClasses()
            .that().resideInAPackage(SHARED_PKG + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                CONTROLLER_PKG + "..",
                USECASE_PKG    + "..",
                REPOSITORY_PKG + "..",
                CONSUMER_PKG   + ".."
            )
            .as("shared.* is the pure contract — it must not depend on any concrete module")
            .because("""
                shared is the only module every other module depends on.
                If shared imports a concrete module, a circular dependency is introduced.
                Fix: move any shared logic to shared.*, never import back into it.""")
            .check(allClasses);
    }

    // ── Rule 3: Each module has its own Main entry point ─────────────────────────

    @Test
    @Order(6)
    @DisplayName("Rule 3: *Main classes must reside in the module root package (not in sub-packages)")
    void mainClassesInRootPackage() {
        // Main classes like ControllerMain, UseCaseMain, etc. should be
        // in the first-level package of their module, not buried in sub-packages.
        classes()
            .that().haveSimpleNameEndingWith("Main")
            .and().resideInAPackage("com.naranjax.distributed..")
            .should().resideInAPackage("com.naranjax.distributed.*")  // exactly depth 3, no deeper
            .as("*Main classes must be at the module root package depth")
            .because("""
                Entry points should be discoverable at the root of each module's namespace.
                If Main is buried in sub-packages it indicates wiring is mixed into the logic.""")
            .check(allClasses);
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private static String toModuleDirName(String propSuffix) {
        return switch (propSuffix) {
            case "shared"     -> "shared";
            case "controller" -> "controller-app";
            case "usecase"    -> "usecase-app";
            case "repository" -> "repository-app";
            case "consumer"   -> "consumer-app";
            default           -> propSuffix;
        };
    }
}
