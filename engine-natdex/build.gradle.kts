plugins {
    id("java-library")
}

/*
 * Nat. Dex randomizer, vendored as source (GPL-3.0).
 * CyanSMP64/universal-pokemon-randomizer-zx, branch `natdex`, commit 71e50737
 * ("release v1.2.1") — see PINNED.txt. This is the source of randomizer_natdex_1.2.1.jar.
 *
 * Version identity: VERSION = 908, VERSION_STRING = "4.6.1-END121". 908 > mainline's
 * 322, which is why vanilla ZX rejects NatDex .rnqs files as "from a newer version".
 *
 * v0.2 ships this as the ONLY engine in the APK, so the com.dabomstew.pkrandom package
 * collision with engine-zx never arises there. engine-zx stays a JVM-side module for
 * tests and the future relocated build. NEVER add both to the app without relocation.
 *
 * Same vendoring rules as engine-zx: `src` is pristine upstream, our tests live in
 * `test/`, nothing excluded (Swing is dexed dead weight; the only user outside the GUI
 * is the desktop launcher, which nothing calls on Android).
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src"))
        resources {
            setSrcDirs(listOf("src"))
            exclude("**/*.java")
        }
    }
    named("test") {
        java.setSrcDirs(listOf("test"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:none"))
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}
