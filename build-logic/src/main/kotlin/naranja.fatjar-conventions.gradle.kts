/**
 * Convention plugin for fat-jar applications (Vert.x services).
 * Applies the Shadow plugin and configures manifest exclusions for signed JARs.
 * Decision (2026-05-07): using Shadow (com.gradleup.shadow) rather than the application
 * distribution plugin because Vert.x's ServiceLoader entries in META-INF/services must be
 * merged — the application plugin does not do this, causing Vert.x to fail at runtime.
 */
plugins {
    id("naranja.java-conventions")
    id("com.gradleup.shadow")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    mergeServiceFiles()
    // Prevent duplicate Netty properties from causing checksum warnings
    append("META-INF/io.netty.versions.properties")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Wire shadowJar into the 'assemble' lifecycle so ./gradlew build produces the fat jar
tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
