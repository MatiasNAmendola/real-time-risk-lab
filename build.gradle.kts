import org.gradle.api.plugins.JavaPluginExtension

plugins {
    base
    alias(libs.plugins.versions)
    jacoco
}

allprojects {
    group = "io.riskplatform.poc"
    version = "0.1.0-SNAPSHOT"
}

val jacocoAggregateReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    description = "Aggregate JaCoCo coverage report across all submodules"

    val sourceProjects = subprojects.filter { sub ->
        sub.plugins.hasPlugin("jacoco") &&
        sub.path !in listOf(":build-logic")
    }

    dependsOn(sourceProjects.map { "${it.path}:test" })

    executionData.from(sourceProjects.map { proj ->
        proj.fileTree(proj.layout.buildDirectory.dir("jacoco")) {
            include("**/*.exec")
        }
    })

    sourceDirectories.from(sourceProjects.mapNotNull { proj ->
        proj.extensions.findByType<JavaPluginExtension>()
            ?.sourceSets?.findByName("main")?.allSource?.srcDirs
    })

    classDirectories.from(sourceProjects.mapNotNull { proj ->
        proj.extensions.findByType<JavaPluginExtension>()
            ?.sourceSets?.findByName("main")?.output?.classesDirs
    })

    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregate"))
    }
}
