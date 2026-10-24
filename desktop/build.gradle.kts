plugins {
    kotlin("jvm")
}

/*
 * KaizoCore PC (KaizoCore-PC.jar): Heart & Soul Kaizo IronMON in BizHawk 2.11.1 on a desktop JVM. The Heart & Soul
 * randomizer is engine-hns and the tracker is tracker-gba, the same files the app builds; the assets (layout, species,
 * item lists, the comfort patch, the presets) are copied from app/src/main/assets at build time, and the version is the
 * app's own versionCode and versionName from app/build.gradle.kts, so the jar and the APK of a commit are one release.
 *
 *   ./gradlew :desktop:kaizocorePcJar   desktop/build/libs/KaizoCore-PC.jar
 *   ./gradlew :desktop:kaizocorePcDist  desktop/build/dist/KaizoCore-PC/ (the folder the streamer unzips) and its .zip
 *
 * Never a ROM, a save or the Heart & Soul team's patch in either: only KaizoCore's own comfort patch, as in the APK.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val appAssets = rootProject.file("app/src/main/assets")

/** The app's version, read from app/build.gradle.kts, so the jar says the release it belongs to. */
val appGradle = rootProject.file("app/build.gradle.kts").readText()
val versionCode = Regex("""versionCode\s*=\s*(\d+)""").find(appGradle)?.groupValues?.get(1)
    ?: error("desktop: no versionCode in app/build.gradle.kts")
val baseVersion = Regex("""val baseVersion\s*=\s*"([^"]+)"""").find(appGradle)?.groupValues?.get(1)
    ?: error("desktop: no baseVersion in app/build.gradle.kts")
val commit: String = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
    workingDir = rootDir
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.getOrElse("unknown")

val pcResources = layout.buildDirectory.dir("pcres")

// The assets the Heart & Soul randomizer and the setup read, under /assets in the jar as in the APK. The comfort build
// is the PC's own (desktop/assets: the client's easter eggs, patch 0061): its patch, layout, species and battle art
// replace the app's, which ship only in the APK.
val pcAssets = project.file("assets")
val copyAssets = tasks.register<Sync>("copyAppAssets") {
    from(appAssets) {
        include("hns/**", "presets/RSE*.rnqs")
        exclude("hns/layout-kaizo.json", "hns/species-kaizo.json")
    }
    from(pcAssets) { include("hns/**", "patches/hns-kaizo-heartsoul-206.bps") }
    into(pcResources.map { it.dir("assets") })
}

val versionFile = tasks.register("kaizocorePcVersion") {
    val out = pcResources.map { it.file("kaizocore-pc.properties") }
    inputs.property("versionCode", versionCode)
    inputs.property("baseVersion", baseVersion)
    inputs.property("commit", commit)
    outputs.file(out)
    doLast {
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "version=$baseVersion\nversionCode=$versionCode\ncommit=$commit\n"
        )
    }
}

sourceSets {
    named("main") {
        resources.srcDir(pcResources)
    }
}
tasks.named("processResources") { dependsOn(copyAssets, versionFile) }

dependencies {
    implementation(project(":engine-hns"))
    implementation(project(":tracker-gba"))
    // The PC panel: the app's PC-tracker screens on the desktop (Compose Multiplatform), and the run's StatMarks.
    implementation(project(":pcui"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
    // The phone/PC pin reads the app's assets and engine-hns/pinned-seeds.tsv where they lie.
    systemProperty("kaizocore.repo", rootDir.absolutePath)
    providers.gradleProperty("logShots").orNull?.let { systemProperty("kaizocore.logShots", it) }
    // LogContrastTest checks the first CONTRAST_THEMES themes only when it is set: an input, so a quick run never leaves
    // the full one "up to date" (pcui's TextContrastTest the same).
    inputs.property("contrastThemes", System.getenv("CONTRAST_THEMES") ?: "all")
    inputs.property("contrastOnly", System.getenv("CONTRAST_ONLY") ?: "all")
    maxHeapSize = "2g"
}

val fatJar = tasks.register<Jar>("kaizocorePcJar") {
    group = "build"
    description = "KaizoCore-PC.jar: everything in one jar, run with java -jar."
    archiveFileName.set("KaizoCore-PC.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes(
            "Main-Class" to "com.ironmonone.pc.MainKt",
            "Implementation-Title" to "KaizoCore PC",
            "Implementation-Version" to "$baseVersion ($versionCode, $commit)",
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
    // The licence the whole jar is under (the engines are GPL-3.0) and the third-party notices.
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
}

// The folder the streamer unzips: the jar, the BizHawk script, the launcher, the settings file, the README.
val distDir = layout.buildDirectory.dir("dist/KaizoCore-PC")
val dist = tasks.register<Sync>("kaizocorePcFolder") {
    group = "build"
    from(fatJar)
    from("package") { include("KaizoCore PC.bat", "kaizocore-pc.ini", "kaizocore-hns.lua") }
    from("package/README.txt")
    from(rootProject.file("LICENSE")) { rename { "LICENSE.txt" } }
    into(distDir)
}
tasks.register<Zip>("kaizocorePcDist") {
    group = "build"
    description = "The KaizoCore-PC folder and KaizoCore-PC-<version>.zip."
    dependsOn(dist)
    from(distDir) { into("KaizoCore-PC") }
    archiveFileName.set("KaizoCore-PC-$baseVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

// The animation exploration (copy only): every effect over real cards as GIFs and contact sheets in build/fx.
tasks.register<JavaExec>("fxPreview") {
    mainClass.set("com.ironmonone.pc.fx.FxPreviewKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args(listOf(layout.buildDirectory.dir("fx").get().asFile.absolutePath) +
        (providers.gradleProperty("art").orNull?.let { listOf("--art", it) } ?: emptyList()))
    systemProperty("java.awt.headless", "true")
}

// The scenario sandbox (copy only): ./gradlew :desktop:sandbox -Pscenario=sandbox/scenarios/showcase.txt
// writes build/sandbox/<name>.gif, -sheet.png and -end.png. -Prom= names the official 2.0.6 (default: .vendor's).
tasks.register<JavaExec>("sandbox") {
    mainClass.set("com.ironmonone.pc.fx.SandboxKt")
    classpath = sourceSets.main.get().runtimeClasspath
    val scenario = providers.gradleProperty("scenario").orNull ?: "sandbox/scenarios/showcase.txt"
    val rom = providers.gradleProperty("rom").orNull ?: "C:/Users/bepor/IronMonOne/.vendor/roms/hns-206.gba"
    args(rootProject.file(scenario).absolutePath, layout.buildDirectory.dir("sandbox").get().asFile.absolutePath, rom)
    systemProperty("java.awt.headless", "true")
    // -Ppng: a PNG per frame for a video (Sandbox.kt, SANDBOX_PNG); -Ppng=bare leaves the captions off.
    providers.gradleProperty("png").orNull?.let { environment("SANDBOX_PNG", it.ifBlank { "1" }) }
    // -Pautotheme: each card in the colours of the Pokemon it shows (Sandbox.frames, autoTheme).
    providers.gradleProperty("autotheme").orNull?.let { environment("SANDBOX_AUTOTHEME", "1") }
}
