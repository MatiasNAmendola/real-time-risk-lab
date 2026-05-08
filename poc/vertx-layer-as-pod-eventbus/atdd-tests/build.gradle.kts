plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(libs.karate.junit5)
    testImplementation(libs.kafka.clients)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jacoco.core)
    testImplementation(libs.bundles.logging)
}

tasks.withType<Test>().configureEach {
    // Run only the suite entry point to avoid double-execution of Karate features
    include("**/RiskAtddSuite.class")
    systemProperty("karate.env", System.getProperty("karate.env", "local"))
    systemProperty("karate.output.dir", layout.buildDirectory.dir("karate-reports").get().asFile.absolutePath)
    // Skip unless -Patdd flag is passed — these tests require the distributed cluster running
    val runAtdd = providers.gradleProperty("atdd").isPresent
    onlyIf { runAtdd }
}
