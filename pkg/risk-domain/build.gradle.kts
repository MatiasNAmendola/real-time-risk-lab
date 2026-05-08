plugins { id("riskplatform.library-conventions") }

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    api(project(":sdks:risk-events"))
    implementation(libs.bundles.jackson)
    testImplementation(libs.bundles.junit.testing)
}
