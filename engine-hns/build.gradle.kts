plugins {
    kotlin("jvm")
}

// The Heart & Soul randomizer (HnsEngine, engine/hns), the Nat. Dex engine's entry point and its two types, the rule
// sheet's BST lines (BstLines.kt), the locale every engine runs in (EngineLocale) and HnsSetup's checks, words and
// patching (HnsSetupCore.kt): pure JVM, so the app and KaizoCore PC (the desktop module) build the same bytes from the
// same files. The packages are the ones these files had in the app, so nothing that calls them changed.
//
// No Android dependency belongs here: the desktop module compiles it on a plain JVM.
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
    // api: HnsEngine's settings are the engines' Settings classes, and HnsSetup's patching throws core-patch's errors.
    api(project(":core-patch"))
    api(project(":engine-natdex"))
    api(project(":engine-zx"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
