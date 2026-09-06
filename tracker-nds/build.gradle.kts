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
    // For Gen3Types' effectiveness chart. Gen 3 and Gen 4 share an identical
    // type chart - nothing changed between them - so the DS tracker reuses the
    // one table that has been diffed cell-for-cell against the reference
    // tracker rather than keeping a second copy that can drift.
    implementation(project(":tracker-gba"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}
