plugins {
    kotlin("jvm")
}

// The randomizer-settings editor's model layer: pure JVM so it is testable headless.
// The Compose screens in :app render what this module reflects.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":engine-natdex"))
    testImplementation(kotlin("test"))
    // Test-only: a vanilla .rnqs is unreadable by the Nat. Dex fork and vice
    // versa, so exercising the editor against BOTH real engines needs both on
    // the test classpath. Production wiring is unchanged.
    testImplementation(project(":engine-zx"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}
