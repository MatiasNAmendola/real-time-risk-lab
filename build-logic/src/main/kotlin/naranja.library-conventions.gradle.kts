plugins {
    id("naranja.java-conventions")
}

// JUnit + AssertJ test deps by default for every library module
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    testImplementation(platform(catalog.findLibrary("junit-bom").get()))
    testImplementation(catalog.findLibrary("junit-jupiter").get())
    testImplementation(catalog.findLibrary("assertj-core").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
