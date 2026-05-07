plugins {
    java
    `java-library`
    jacoco
}

java {
    toolchain {
        // Java 21 LTS is the baseline. The toolchain downloads JDK 21 via Foojay if not
        // locally installed. Using 21 (not 25) keeps ArchUnit ASM compatibility for tests.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    // Target Java 21 bytecode (class file version 65) so ArchUnit 1.4.0 (ASM 9.6) can
    // analyze the compiled classes. All language features used (records, text blocks,
    // virtual threads) were finalized in Java 21. The JDK 25 toolchain still provides
    // the compiler and runtime; only the bytecode target is pinned to 21.
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }
}
