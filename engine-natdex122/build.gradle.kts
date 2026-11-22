plugins {
    id("java-library")
}

/*
 * Nat. Dex randomizer 1.2.2, vendored as source (GPL-3.0), beside engine-natdex's 1.2.1.
 * CyanSMP64/universal-pokemon-randomizer-zx, branch `natdex`, commit 82be84aa ("release v1.2.2"):
 * engine-natdex's tree (71e50737, with the two changes NOTICE lists) plus upstream's own diff
 * 71e50737...82be84aa, six files. See PINNED.txt. The source of randomizer_natdex_1.2.2.jar.
 *
 * Version identity: VERSION = 909, VERSION_STRING = "4.6.1-END122"; 1.2 settings files (908) read.
 *
 * Side by side (Blake, 2026-11-20: "Don't hurt the players who are on the old system"): a 1.2.1
 * game keeps engine-natdex, unchanged, and a 1.2.2 game is randomized here, each by its own
 * offsets. So this copy is renamed, the way engine-zx and engine-maxdex were, by
 * tools/rename_engine.py: com.dabomstew.pkrandom122 and nd122-prefixed sibling packages,
 * resource paths included. engine-natdex keeps com.dabomstew.pkrandom, which Heart & Soul
 * reads too, so nothing of Heart & Soul moves with this.
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
