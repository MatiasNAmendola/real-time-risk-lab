plugins {
    id("riskplatform.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:vertx-layer-as-pod-eventbus:shared"))
    implementation(project(":pkg:risk-domain"))
    implementation(libs.vertx.core)
    implementation(libs.vertx.web)
    implementation(libs.vertx.hazelcast)
    implementation(libs.vertx.micrometer.metrics)
    implementation(libs.opentelemetry.api)
    implementation(libs.micrometer.registry.otlp)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("controller-app")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.distributed.controller.ControllerMain")
    }
}
