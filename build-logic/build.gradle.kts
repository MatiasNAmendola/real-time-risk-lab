plugins { `kotlin-dsl` }
repositories { gradlePluginPortal() }

dependencies {
    // Make shadow plugin available in convention scripts
    // Using the GradleUp fork (com.gradleup.shadow) 8.3.6 which is on the Gradle Plugin Portal
    implementation("com.gradleup.shadow:shadow-gradle-plugin:8.3.6")
}
