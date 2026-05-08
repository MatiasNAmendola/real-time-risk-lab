plugins {
    id("riskplatform.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    // JMH requires annotation processing — declared as annotationProcessor
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.annprocess)
    implementation(libs.jackson.databind)

    // Risk-engine sources (replaces the legacy extra source-set wiring)
    implementation(project(":poc:java-risk-engine"))
    implementation(project(":pkg:risk-domain"))
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("inprocess-bench")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "org.openjdk.jmh.Main")
    }
}

// Convenience task: run the JMH benchmark directly
tasks.register<JavaExec>("runBench") {
    dependsOn(tasks.named("shadowJar"))
    classpath = files(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile)
    mainClass.set("org.openjdk.jmh.Main")
    args = listOf(".*InProcessBenchmark.*", "-wi", "3", "-i", "5", "-f", "1")
}
