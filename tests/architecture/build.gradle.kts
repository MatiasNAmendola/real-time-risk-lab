plugins {
    id("riskplatform.testing-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // ArchUnit needs the compiled classes of the modules under test on the classpath.
    // Declaring them as testImplementation makes the classfiles available at test runtime.
    testImplementation(project(":poc:java-risk-engine"))
    testImplementation(project(":poc:java-vertx-distributed:shared"))
    testImplementation(project(":poc:java-vertx-distributed:controller-app"))
    testImplementation(project(":poc:java-vertx-distributed:usecase-app"))
    testImplementation(project(":poc:java-vertx-distributed:repository-app"))
    testImplementation(project(":poc:java-vertx-distributed:consumer-app"))

    testImplementation(libs.archunit.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Ensure shadowJar tasks for all Vert.x apps complete before tests run
// (parallel builds can otherwise race over the build/libs directory).
val vertxApps = listOf(
    ":poc:java-vertx-distributed:controller-app",
    ":poc:java-vertx-distributed:usecase-app",
    ":poc:java-vertx-distributed:repository-app",
    ":poc:java-vertx-distributed:consumer-app"
)
tasks.withType<Test>().configureEach {
    vertxApps.forEach { appPath ->
        mustRunAfter(project(appPath).tasks.named("shadowJar"))
    }
    // Pass Gradle build output paths as system properties so ArchUnit ClassFileImporter
    // can locate compiled classes for each module.
    fun classesDir(projectPath: String): String =
        project(projectPath).layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath

    systemProperty("risk.engine.classes.dir",   classesDir(":poc:java-risk-engine"))
    systemProperty("vertx.shared.classes",      classesDir(":poc:java-vertx-distributed:shared"))
    systemProperty("vertx.controller.classes",  classesDir(":poc:java-vertx-distributed:controller-app"))
    systemProperty("vertx.usecase.classes",     classesDir(":poc:java-vertx-distributed:usecase-app"))
    systemProperty("vertx.repository.classes",  classesDir(":poc:java-vertx-distributed:repository-app"))
    systemProperty("vertx.consumer.classes",    classesDir(":poc:java-vertx-distributed:consumer-app"))
}
