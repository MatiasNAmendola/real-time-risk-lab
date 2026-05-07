plugins {
    id("naranja.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(libs.karate.junit5)
    testImplementation(libs.kafka.clients)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.logging)
}

tasks.withType<Test>().configureEach {
    include("**/MonolithAtddSuite.class")
    systemProperty("karate.env", System.getProperty("karate.env", "local"))
    val runAtdd = providers.gradleProperty("atdd").isPresent
    onlyIf { runAtdd }
}
