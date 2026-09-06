plugins {
    kotlin("jvm")
}

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
    // api, not implementation: RomIdentity returns RomKind, so consumers need it.
    api(project(":core-api"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
