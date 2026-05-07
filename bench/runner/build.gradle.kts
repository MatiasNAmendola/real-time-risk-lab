plugins {
    id("naranja.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.jackson.databind)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("runner")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "com.naranjax.bench.runner.ComparisonRunner")
    }
}

tasks.register<JavaExec>("runComparison") {
    dependsOn(tasks.named("shadowJar"))
    classpath = files(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile)
    mainClass.set("com.naranjax.bench.runner.ComparisonRunner")
}
