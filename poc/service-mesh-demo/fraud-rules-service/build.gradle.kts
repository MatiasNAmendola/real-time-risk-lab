plugins { id("riskplatform.fatjar-conventions") }

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":poc:service-mesh-demo:shared"))
    implementation(libs.vertx.core)
    implementation(libs.vertx.hazelcast)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.logging)
    implementation(libs.opentelemetry.api)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("fraud-rules-service")
    archiveClassifier.set("")
    manifest { attributes("Main-Class" to "io.riskplatform.servicemesh.fraudrules.cmd.FraudRulesMain") }
}
