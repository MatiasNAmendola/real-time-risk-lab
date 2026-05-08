plugins {
    id("riskplatform.app-conventions")
    id("riskplatform.fatjar-conventions")
}

application {
    mainClass.set("io.riskplatform.engine.cmd.RiskApplication")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("no-vertx-clean-engine")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.engine.cmd.HttpRunner")
    }
    mergeServiceFiles()
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(project(":pkg:errors"))
    implementation(project(":pkg:resilience"))
    implementation(project(":pkg:events"))
    implementation(project(":pkg:observability"))
    implementation(project(":pkg:repositories"))
    implementation(project(":pkg:risk-domain"))
    implementation(project(":sdks:risk-events"))
    implementation(libs.bundles.logging)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.junit.testing)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
