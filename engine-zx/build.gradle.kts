plugins {
    id("java-library")
}

/*
 * Universal Pokémon Randomizer ZX v4.6.1, vendored as source (GPL-3.0).
 *
 * WHY SOURCE AND NOT THE JAR: Android runs ART on DEX bytecode. There is no JVM and no
 * `java` binary, so PokeRandoZX.jar cannot be executed. The engine is portable plain
 * Java, so it is compiled into the app instead. The JAR in the drop folder is a version
 * pin, not an executable. See docs and map, decision D1.
 *
 * WHY NOTHING IS EXCLUDED: `Utils.java` imports `newgui.NewRandomizerGUI` purely to
 * locate the running jar, so excluding the GUI would not compile. Swing and awt classes
 * are dexed but never loaded, which is exactly what UPR-Android ships. The only
 * `java.awt` use outside the GUI is `getMascotImage()`, the desktop window's decorative
 * sprite. It is not on the randomization path and must never be called on Android.
 *
 * ZX keeps sources and resources in one flat `src` tree rather than Maven layout.
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
    // `src` is vendored and must stay untouched. `test` is ours.
    named("test") {
        java.setSrcDirs(listOf("test"))
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}

tasks.withType<JavaCompile>().configureEach {
    // ZX is Java 8-era source; its warnings are not ours to fix.
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:none"))
    options.encoding = "UTF-8"
}
