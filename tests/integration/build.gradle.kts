plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.logging)

    // Testcontainers
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.redpanda)

    // Drivers and clients
    testImplementation(libs.postgresql.driver)
    testImplementation(libs.lettuce.core)
    testImplementation(libs.kafka.clients)

    // AWS SDK v2
    testImplementation(libs.aws.s3)
    testImplementation(libs.aws.secretsmanager)
    testImplementation(libs.aws.url.connection.client)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    // Integration tests require running Docker containers — skip by default.
    // Enable with: ./gradlew :tests:integration:test -Pintegration
    if (!project.hasProperty("integration")) {
        exclude("**/*IntegrationTest.class")
    }
    jvmArgs("--enable-preview")
    // OrbStack rejects Docker API < 1.40; docker-java defaults to 1.32.
    // Force a compatible API version for Testcontainers regardless of host runtime.
    environment("DOCKER_API_VERSION", "1.43")
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
    systemProperty("api.version", "1.43")
    systemProperty("docker.client.strategy", "org.testcontainers.dockerclient.UnixSocketClientProviderStrategy")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}
