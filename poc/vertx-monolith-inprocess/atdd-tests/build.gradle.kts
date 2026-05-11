plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(libs.karate.junit5)
    testImplementation(libs.kafka.clients)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.logging)

    // Self-launching lifecycle (Testcontainers + fat-jar fork).
    // Option A chosen over Option B (dockerCompose Gradle plugin) because the existing
    // shadowJar already produces a runnable artifact and we avoid a new compose service.
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
}

// Wire the monolith fat-jar so the in-test launcher can find it.
val monolithShadow = tasks.getByPath(":poc:vertx-monolith-inprocess:shadowJar")

tasks.withType<Test>().configureEach {
    dependsOn(monolithShadow)
    // Pass the freshly built fat-jar to the in-process MonolithStack launcher.
    systemProperty("monolith.jar",
        "${rootProject.projectDir}/poc/vertx-monolith-inprocess/build/libs/vertx-monolith-inprocess.jar")

    include("**/MonolithAtddSuite.class")
    systemProperty("karate.env", System.getProperty("karate.env", "local"))

    // OrbStack rejects Docker API < 1.40; align with :tests:integration (commit 7386afd).
    environment("DOCKER_API_VERSION", "1.43")
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    systemProperty("api.version", "1.43")
    systemProperty("docker.client.strategy", "org.testcontainers.dockerclient.UnixSocketClientProviderStrategy")

    // Keep -Patdd as an opt-in guard: the suite requires Docker and starts containers.
    val runAtdd = providers.gradleProperty("atdd").isPresent
    onlyIf { runAtdd }
}
