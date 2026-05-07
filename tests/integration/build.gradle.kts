plugins {
    id("naranja.testing-conventions")
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
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}
