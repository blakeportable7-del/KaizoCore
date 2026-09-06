plugins {
    kotlin("jvm")
}

// The tracker's logic layer: pure JVM, driven through a MemoryReader function, so the
// whole thing is testable headless against synthetic memory and real ROM files. The
// app hands it GLRetroView::readMemory; the tests hand it a byte array.
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
    testLogging { showStandardStreams = true }
}
