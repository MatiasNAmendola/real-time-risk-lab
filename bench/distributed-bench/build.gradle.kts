plugins {
    id("naranja.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.jackson.databind)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("distributed-bench")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "com.naranjax.bench.distributed.DistributedBenchmark")
    }
}

tasks.register<JavaExec>("runBench") {
    dependsOn(tasks.named("shadowJar"))
    classpath = files(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile)
    mainClass.set("com.naranjax.bench.distributed.DistributedBenchmark")
}
