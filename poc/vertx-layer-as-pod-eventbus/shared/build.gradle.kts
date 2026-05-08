plugins {
    id("riskplatform.library-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.vertx.core)
    implementation(libs.jackson.databind)
}
