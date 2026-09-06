plugins {
    kotlin("jvm")
}

// Pure Kotlin. No Android dependency belongs in this module, ever: it is what makes a
// second console a new adapter rather than a rewrite (brief section 15.5).
//
// Java 17 bytecode so the Android modules can consume it. AGP 8.3 targets 17; the
// toolchain running the build is JDK 21.
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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
