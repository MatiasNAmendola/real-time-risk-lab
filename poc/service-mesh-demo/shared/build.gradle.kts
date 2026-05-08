plugins { id("riskplatform.library-conventions") }

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.vertx.core)
    implementation(libs.vertx.hazelcast)
    implementation(libs.vertx.micrometer.metrics)
    implementation(libs.hazelcast)
}
