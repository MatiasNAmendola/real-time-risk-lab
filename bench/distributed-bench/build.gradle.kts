plugins {
    id("riskplatform.fatjar-conventions")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    implementation(libs.jackson.databind)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("distributed-bench")
    archiveClassifier.set("")
    manifest {
        attributes("Main-Class" to "io.riskplatform.bench.distributed.DistributedBenchmark")
    }
}

tasks.register<JavaExec>("runBench") {
    dependsOn(tasks.named("shadowJar"))
    classpath = files(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile)
    mainClass.set("io.riskplatform.bench.distributed.DistributedBenchmark")
}
