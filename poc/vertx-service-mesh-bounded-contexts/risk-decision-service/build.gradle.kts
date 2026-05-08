plugins { id("riskplatform.fatjar-conventions") }

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:vertx-service-mesh-bounded-contexts:shared"))
    implementation(libs.vertx.core)
    implementation(libs.vertx.web)
    implementation(libs.vertx.hazelcast)
    implementation(libs.vertx.micrometer.metrics)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)
    implementation(libs.opentelemetry.api)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("risk-decision-service")
    archiveClassifier.set("")
    manifest { attributes("Main-Class" to "io.riskplatform.servicemesh.riskdecision.cmd.RiskDecisionMain") }
}
