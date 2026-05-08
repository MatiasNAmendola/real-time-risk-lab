/**
 * Cross-SDK contract test module.
 *
 * Validates that the Java, TypeScript, and Go SDKs all agree on the same
 * risk decision for identical inputs, ensuring behavioural parity across
 * language implementations.
 *
 * Run with: ./gradlew :sdks:contract-test:test
 */
plugins { id("riskplatform.library-conventions") }

group   = "io.riskplatform.poc"
version = "1.0.0-SNAPSHOT"

dependencies {
    testImplementation(project(":sdks:risk-client-java"))
    testImplementation(project(":sdks:risk-events"))
    testImplementation(libs.bundles.junit.testing)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.jackson.databind)
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Contract tests require compose infra; guard against accidental unit runs.
    // Capture values at configuration time to be compatible with configuration cache.
    val runContract = providers.gradleProperty("contract").isPresent ||
                      providers.environmentVariable("CI").isPresent ||
                      providers.environmentVariable("RISK_BASE_URL").isPresent
    onlyIf { runContract }
    // timeout is 10 minutes; expressed as seconds for Gradle compatibility
    systemProperty("test.timeout.seconds", "600")
}
