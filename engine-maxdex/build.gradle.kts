plugins {
    id("java-library")
}

/*
 * The MaxDex randomizer, vendored as source (GPL-3.0).
 *
 * Base: CyanSMP64/universal-pokemon-randomizer-zx, branch `natdex`, commit d53e0824
 * ("release v1.1.3"). Trip's MaxDex-Randomizer.jar (Tripc423/Maxdex, 2026-06-25) is that
 * release's jar with eight entries changed; this module is the same source with his changes
 * ported by hand, each marked "LOCAL MODIFICATION (KaizoCore, MaxDex)". See PINNED.txt.
 * The jar itself is never run, here or anywhere else: Android cannot run a jar, and the
 * engine in the APK is built from source like the other two.
 *
 * Version identity: VERSION = 904, VERSION_STRING = "4.6.0-END112", what Trip's jar
 * reports, so a seed, a settings string and a log match his.
 *
 * Three randomizers ship in one APK, so this one is renamed: com.dabomstew.pkrandommd and
 * md-prefixed sibling packages (mdcompressors, mdcuecompressors, mdlauncher, mdpptxt,
 * mdthenewpoketext), resource paths included, the way engine-zx was renamed in 133f0f25.
 * engine-natdex keeps com.dabomstew.pkrandom and engine-zx has com.dabomstew.pkrandomzx.
 *
 * Same vendoring rules as the other two: `src` is upstream plus the changes NOTICE lists,
 * our tests live in `test/`, nothing excluded (Swing is dexed dead weight).
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
    // The patched test ROM lives outside the build (IRONMON_ROMS, else the vendor folder) and
    // decides which tests run, so whether it is there is part of the cache key: a result
    // cached without it is never served once it is present.
    val rom = file((System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms") + "/firered-maxdex.gba")
    inputs.property("maxdexRom", if (rom.isFile) "${rom.length()}:${rom.lastModified()}" else "absent")
}
