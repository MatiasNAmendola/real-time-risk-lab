rootProject.name = "real-time-risk-lab"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Foojay toolchain resolver — can provision JDKs if not locally installed; build target remains --release 21.
    // This is the canonical Gradle solution for "Java X not found" in CI/dev environments.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // local artifact repository  // uncomment to resolve locally published artifacts
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
    "poc:no-vertx-clean-engine",
    "poc:vertx-monolith-inprocess",
    "poc:vertx-monolith-inprocess:atdd-tests",
    "poc:vertx-layer-as-pod-eventbus:shared",
    "poc:vertx-layer-as-pod-eventbus:controller-app",
    "poc:vertx-layer-as-pod-eventbus:usecase-app",
    "poc:vertx-layer-as-pod-eventbus:repository-app",
    "poc:vertx-layer-as-pod-eventbus:consumer-app",
    "poc:vertx-layer-as-pod-eventbus:atdd-tests",
    "poc:vertx-service-mesh-bounded-contexts:shared",
    "poc:vertx-service-mesh-bounded-contexts:risk-decision-service",
    "poc:vertx-service-mesh-bounded-contexts:fraud-rules-service",
    "poc:vertx-service-mesh-bounded-contexts:ml-scorer-service",
    "poc:vertx-service-mesh-bounded-contexts:audit-service",
    "poc:vertx-layer-as-pod-http",
    "poc:vertx-layer-as-pod-http:atdd-tests",
    "tests:risk-engine-atdd",
    "tests:architecture",
    "tests:integration",
    "tests:k8s-deployment",
    "bench:inprocess-bench",
    "bench:distributed-bench",
    "bench:runner"
)
