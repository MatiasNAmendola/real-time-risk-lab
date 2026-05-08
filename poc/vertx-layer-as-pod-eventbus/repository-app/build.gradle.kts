plugins {
    id("riskplatform.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:vertx-layer-as-pod-eventbus:shared"))
    implementation(libs.vertx.core)
    implementation(libs.vertx.hazelcast)
    implementation(libs.vertx.pg.client)
    implementation(libs.vertx.sql.client)
    implementation(libs.postgresql.driver)
    implementation(libs.jackson.databind)
    implementation(libs.aws.secretsmanager)
    implementation(libs.aws.url.connection.client)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("repository-app")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.distributed.repository.RepositoryMain")
    }
}
