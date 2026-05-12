import java.time.Duration

plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.logging)

    // Lightweight HTTP / JSON for kubectl JSON parsing and assertions.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    // K8s deployment tests require a real cluster (k3d/OrbStack) + kubectl + helm.
    // They are skipped by default. Enable explicitly with:
    //   ./gradlew :tests:k8s-deployment:test -Pk8s
    //
    // Rationale: these are integration-level tests that mutate cluster state and
    // can take several minutes per scenario (canary rollouts, analysis runs).
    // Running them inside a normal `./gradlew test` would break dev loops.
    if (!project.hasProperty("k8s")) {
        exclude("**/*Test.class")
    }
    // Per project rule: integration/test modules target Java 21 (frameworks
    // do not yet support classfile 25 — see vault/02-Decisions/0001-java-25-lts.md).
    // The `riskplatform.testing-conventions` plugin already pins toolchain to 21.
    useJUnitPlatform()
    systemProperty("kubectl.namespace", System.getProperty("kubectl.namespace", "risk-test"))
    systemProperty("helm.chart.path", System.getProperty(
        "helm.chart.path",
        rootProject.projectDir.resolve("poc/k8s-local/apps/risk-engine").absolutePath
    ))
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    // Realistic timeouts: a full canary can take 5+ minutes.
    timeout.set(Duration.ofMinutes(15))
}
