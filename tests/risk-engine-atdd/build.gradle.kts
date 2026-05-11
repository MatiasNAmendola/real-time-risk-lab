plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // Pull the risk engine as a testImplementation dependency so its classes
    // are on the test classpath (replaces the legacy extra source-set wiring).
    testImplementation(project(":poc:no-vertx-clean-engine"))
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
    // SELF-LAUNCHING: this suite wires the risk engine as a library via RiskApplicationFixture
    // (in-process, no HTTP). No external services or running monolith required, so we let it
    // run on `./gradlew test` like any other suite. Set -Patdd=false to opt out if needed.
    val skipAtdd = providers.gradleProperty("atdd")
        .map { it.equals("false", ignoreCase = true) }
        .getOrElse(false)
    onlyIf { !skipAtdd }
}

// Expose feature files on the test classpath (Cucumber needs them)
sourceSets["test"].resources.srcDir("src/test/java")
