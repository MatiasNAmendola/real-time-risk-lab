plugins {
    id("riskplatform.fatjar-conventions")
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:vertx-layer-as-pod-eventbus:shared"))
    implementation(project(":pkg:risk-domain"))
    implementation(project(":sdks:risk-events"))
    implementation(project(":pkg:errors"))
    implementation(project(":pkg:resilience"))
    implementation(project(":pkg:events"))
    implementation(project(":pkg:observability"))
    implementation(project(":pkg:repositories"))

    implementation(libs.opentelemetry.api)
    implementation(libs.vertx.core)
    implementation(libs.vertx.web)
    implementation(libs.vertx.web.openapi.router)
    implementation(libs.vertx.kafka.client)
    implementation(libs.vertx.micrometer.metrics)
    implementation(libs.micrometer.registry.otlp)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)
    implementation(libs.kafka.clients)

    implementation(libs.postgresql.driver)
    implementation(libs.lettuce.core)
    implementation(libs.aws.s3)
    implementation(libs.aws.sqs)
    implementation(libs.aws.secretsmanager)
    implementation(libs.aws.url.connection.client)

    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Test>().configureEach {
    // Skip Testcontainers integration tests unless Docker is available (CI or explicit flag)
    val runIntegration = providers.gradleProperty("integration").isPresent ||
                         System.getenv("CI") != null
    if (!runIntegration) {
        exclude("**/*IT.class")
        exclude("**/integration/**")
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("vertx-monolith-inprocess")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.monolith.Application")
    }
}
