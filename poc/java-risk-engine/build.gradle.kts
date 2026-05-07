plugins {
    id("naranja.app-conventions")
}

application {
    mainClass.set("com.naranjax.interview.risk.cmd.RiskApplication")
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
