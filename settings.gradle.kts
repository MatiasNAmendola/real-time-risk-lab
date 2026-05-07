rootProject.name = "naranjax-practica-entrevista"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Foojay toolchain resolver — auto-provisions JDK 25 if not locally installed.
    // This is the canonical Gradle solution for "Java X not found" in CI/dev environments.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // mavenLocal()  // uncomment to resolve locally published artifacts
    }
}

// Phase 1 — shared libraries
include(
    "pkg:errors",
    "pkg:config",
    "pkg:resilience",
    "pkg:events",
    "pkg:kafka",
    "pkg:observability",
    "pkg:repositories",
    "pkg:integration-audit",
    "pkg:testing",
    "pkg:risk-domain",
    "sdks:risk-events",
    "sdks:risk-client-java",
    "sdks:contract-test"
)

// Phase 2 — application modules
include(
    "poc:java-risk-engine",
    "poc:java-monolith",
    "poc:java-monolith:atdd-tests",
    "poc:java-vertx-distributed:shared",
    "poc:java-vertx-distributed:controller-app",
    "poc:java-vertx-distributed:usecase-app",
    "poc:java-vertx-distributed:repository-app",
    "poc:java-vertx-distributed:consumer-app",
    "poc:java-vertx-distributed:atdd-tests",
    "poc:vertx-risk-platform",
    "poc:vertx-risk-platform:atdd-tests",
    "tests:risk-engine-atdd",
    "tests:architecture",
    "tests:integration",
    "bench:inprocess-bench",
    "bench:distributed-bench",
    "bench:runner"
)
