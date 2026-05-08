plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // Pull the risk engine as a testImplementation dependency so its classes
    // are on the test classpath (replaces the legacy extra source-set wiring).
    testImplementation(project(":poc:java-risk-engine"))
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit.platform.engine)
    testImplementation(libs.cucumber.picocontainer)
    testImplementation(libs.junit.platform.suite)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.logging)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    include("**/RunCucumberTest.class")
    systemProperty("cucumber.filter.tags", System.getProperty("cucumber.filter.tags", "not @wip and not @karate-only"))
    // Skip unless -Patdd flag is passed — these tests require the risk engine process running
    val runAtdd = providers.gradleProperty("atdd").isPresent
    onlyIf { runAtdd }
}

// Expose feature files on the test classpath (Cucumber needs them)
sourceSets["test"].resources.srcDir("src/test/java")
