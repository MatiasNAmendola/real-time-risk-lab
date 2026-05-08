plugins {
    id("riskplatform.java-conventions")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    testImplementation(platform(catalog.findLibrary("junit-bom").get()))
    testImplementation(catalog.findLibrary("junit-jupiter").get())
    testImplementation(catalog.findLibrary("assertj-core").get())
    testImplementation(catalog.findLibrary("mockito-core").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jacocoTestReport {
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

// jacocoMergedReport: aggregate all test suites' .exec files into a single HTML/XML
// report for this module. Modules with extra suites (Cucumber, Karate, integration)
// contribute their .exec files automatically because they are written under
// build/jacoco/ by JaCoCo's default configuration.
val jacocoMergedReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    description = "Aggregate report from all .exec files in this module"
    // Must run after all test tasks so .exec files are present.
    mustRunAfter(tasks.withType<Test>())

    executionData.from(
        fileTree(layout.buildDirectory.dir("jacoco")) {
            include("**/*.exec")
        }
    )
    sourceDirectories.from(sourceSets.main.get().allSource.srcDirs)
    classDirectories.from(sourceSets.main.get().output)

    reports {
        html.required.set(true)
        xml.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/merged"))
    }
}
