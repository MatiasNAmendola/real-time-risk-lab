plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // ArchUnit needs the compiled classes of the modules under test on the classpath.
    // Declaring them as testImplementation makes the classfiles available at test runtime.
    testImplementation(project(":poc:no-vertx-clean-engine"))
    testImplementation(project(":poc:vertx-layer-as-pod-eventbus:shared"))
    testImplementation(project(":poc:vertx-layer-as-pod-eventbus:controller-app"))
    testImplementation(project(":poc:vertx-layer-as-pod-eventbus:usecase-app"))
    testImplementation(project(":poc:vertx-layer-as-pod-eventbus:repository-app"))
    testImplementation(project(":poc:vertx-layer-as-pod-eventbus:consumer-app"))

    testImplementation(libs.archunit.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Ensure shadowJar tasks for all Vert.x apps complete before tests run
// (parallel builds can otherwise race over the build/libs directory).
val vertxApps = listOf(
    ":poc:vertx-layer-as-pod-eventbus:controller-app",
    ":poc:vertx-layer-as-pod-eventbus:usecase-app",
    ":poc:vertx-layer-as-pod-eventbus:repository-app",
    ":poc:vertx-layer-as-pod-eventbus:consumer-app"
)
tasks.withType<Test>().configureEach {
    vertxApps.forEach { appPath ->
        mustRunAfter(project(appPath).tasks.named("shadowJar"))
    }
    // Pass Gradle build output paths as system properties so ArchUnit ClassFileImporter
    // can locate compiled classes for each module.
    fun classesDir(projectPath: String): String =
        project(projectPath).layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath

    systemProperty("risk.engine.classes.dir",   classesDir(":poc:no-vertx-clean-engine"))
    systemProperty("vertx.shared.classes",      classesDir(":poc:vertx-layer-as-pod-eventbus:shared"))
    systemProperty("vertx.controller.classes",  classesDir(":poc:vertx-layer-as-pod-eventbus:controller-app"))
    systemProperty("vertx.usecase.classes",     classesDir(":poc:vertx-layer-as-pod-eventbus:usecase-app"))
    systemProperty("vertx.repository.classes",  classesDir(":poc:vertx-layer-as-pod-eventbus:repository-app"))
    systemProperty("vertx.consumer.classes",    classesDir(":poc:vertx-layer-as-pod-eventbus:consumer-app"))
}
