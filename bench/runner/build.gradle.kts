plugins {
    id("riskplatform.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.jackson.databind)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("runner")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.bench.runner.ComparisonRunner")
    }
}

tasks.register<JavaExec>("runComparison") {
    dependsOn(tasks.named("shadowJar"))
    classpath = files(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile)
    mainClass.set("io.riskplatform.bench.runner.ComparisonRunner")
}
