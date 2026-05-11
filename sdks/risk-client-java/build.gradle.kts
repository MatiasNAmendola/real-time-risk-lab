import java.time.Duration

plugins { id("riskplatform.library-conventions") }

group = "io.riskplatform.poc"
version = "1.0.0-SNAPSHOT"

// ---------------------------------------------------------------------------
// Integration-test source set
// ---------------------------------------------------------------------------
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output +
                configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests against real services in Testcontainers."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath      = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter("test")
    // Give Testcontainers time to pull images and boot services
    timeout.set(Duration.ofMinutes(10))
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------
dependencies {
    api(project(":sdks:risk-events"))
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)
    implementation(libs.kafka.clients)
    implementation(libs.aws.sqs)
    implementation(libs.opentelemetry.api)
    // Jackson JavaTime module (part of jackson-databind bundle already pulls it transitively
    // but we add it explicitly for clarity)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    testImplementation(libs.bundles.junit.testing)
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")

    // Testcontainers for integration tests
    integrationTestImplementation("org.testcontainers:testcontainers:1.20.4")
    integrationTestImplementation("org.testcontainers:junit-jupiter:1.20.4")
    integrationTestImplementation("org.testcontainers:postgresql:1.20.4")
    integrationTestImplementation(libs.bundles.junit.testing)
}
