/**
 * vertx-risk-platform — 3-pod HTTP inter-pod PoC with token-based permission model.
 *
 * Architecture: controller (8080) -> usecase (8081) -> repository (8082).
 * All inter-pod calls use plain HTTP with x-pod-token / x-pod-scopes headers.
 * No Hazelcast, no event bus. The point is to contrast HTTP+tokens with event-bus+networks.
 *
 * Java release: 21 (consistent with the rest of the repo).
 * Vert.x:       5.0.12 (from catalog).
 * Shadow JAR:   one fat-jar; pod role selected via CLI arg (controller|usecase|repository).
 */
plugins {
    id("riskplatform.fatjar-conventions")
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.vertx.core)
    implementation(libs.vertx.web)
    // vertx-web-client is not in the catalog bundle — declare directly
    implementation("io.vertx:vertx-web-client:${libs.versions.vertx.get()}")
    implementation(libs.jackson.databind)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${libs.versions.jackson.get()}")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // vertx-junit5 — test support (deploy verticles in JUnit 5)
    testImplementation("io.vertx:vertx-junit5:${libs.versions.vertx.get()}")
    testImplementation("io.vertx:vertx-web-client:${libs.versions.vertx.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("vertx-risk-platform")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.vertx.common.PodMain")
    }
}
