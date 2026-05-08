plugins {
    id("riskplatform.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:vertx-layer-as-pod-eventbus:shared"))
    implementation(project(":pkg:risk-domain"))
    implementation(libs.vertx.core)
    implementation(libs.vertx.hazelcast)
    implementation(libs.vertx.kafka.client)
    implementation(libs.jackson.databind)
    implementation(libs.bundles.logging)
    implementation(libs.opentelemetry.api)
    implementation(libs.aws.s3)
    implementation(libs.aws.sqs)
    implementation(libs.aws.url.connection.client)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("usecase-app")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.distributed.usecase.UseCaseMain")
    }
}
