plugins {
    id("naranja.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:java-vertx-distributed:shared"))
    implementation(libs.vertx.core)
    implementation(libs.vertx.kafka.client)
    implementation(libs.jackson.databind)
    implementation(libs.bundles.logging)
    implementation(libs.opentelemetry.api)
    implementation(libs.aws.s3)
    implementation(libs.aws.url.connection.client)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("consumer-app")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "com.naranjax.distributed.consumer.ConsumerMain")
    }
}
