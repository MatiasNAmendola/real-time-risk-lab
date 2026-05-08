plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(libs.karate.junit5)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.logging)
}

tasks.withType<Test>().configureEach {
    include("**/VertxRiskPlatformAtddSuite.class")
    systemProperty("karate.env", System.getProperty("karate.env", "local"))
    // Skip unless -Patdd flag is passed — requires the 3 pods running on 8180/8181/8182
    val runAtdd = providers.gradleProperty("atdd").isPresent
    onlyIf { runAtdd }
}
