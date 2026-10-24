plugins {
    kotlin("jvm")
    // Compose Multiplatform 1.6.11 is built on Jetpack Compose 1.6.7, the version the app's BOM 2024.05.00 gives it,
    // and takes Kotlin 1.9.24 with the Compose compiler 1.5.14, the pair the app already uses (PLAN section 6.1).
    id("org.jetbrains.compose") version "1.6.11"
}

/*
 * The PC tracker's screens for KaizoCore PC (PLAN row 3): the app's own PcTracker.kt and the files it needs
 * (app-files.txt), compiled here unchanged and read where they lie, so the phone and the PC draw from one source. The
 * app stays a plain Android module on Jetpack Compose; these files reach the platform only through the names in the
 * app's PcPlatformAndroid.kt, which src/main/kotlin/.../PcPlatformDesktop.kt declares for a desktop JVM. What they call
 * in the rest of the app is in PcSeamsDesktop.kt, and the small pieces they need from files that are not shared are
 * copied from the app at build time (copyAppPieces), so they cannot drift.
 *
 * PcPanel renders the panel offscreen at game-pixel size (150x160) for the desktop module (KaizoCore-PC.jar).
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val appKt = rootProject.file("app/src/main/kotlin/com/ironmonone/app")
val appFiles = file("app-files.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

val appSrc = tasks.register<Sync>("appSources") {
    inputs.file("app-files.txt")
    from(appKt) { include(appFiles) }
    into(layout.buildDirectory.dir("appsrc/com/ironmonone/app"))
}

/**
 * The declarations the shared files need from app files that are not shared, by name: (file, the line a declaration
 * starts with). Each is taken from its first line to the closing brace at column 0 (or its own line for a one-liner).
 */
val pieces = listOf(
    "TrackerGearDialog.kt" to "internal fun GearButton(",
    "TrackerGearDialog.kt" to "internal const val GEAR_LABEL_SP",
    "TrackerGearDialog.kt" to "internal fun GearToggle(",
    "TrackerGearDialog.kt" to "internal fun PcTap(",
    "NdsTrackerPanel.kt" to "internal fun hgssBadgeRows(",
    "LogSearchField.kt" to "object LogSuggest {",
    "LogTrainers.kt" to "internal fun refUpperEachWord(",
    "LogTrainers.kt" to "internal fun logTitle(",
    // The phone's own cards, so the PC panel draws your Pokemon and the opponent exactly as the phone does.
    "TrackerPanel.kt" to "private fun PartyCard(",
    "TrackerPanel.kt" to "private fun EnemyCard(",
    "TrackerPanel.kt" to "internal object EnemyView {",
    "TrackerPanel.kt" to "internal object HiddenCard {",
    "TrackerPanel.kt" to "internal object CardGender {",
    "TrackerPanel.kt" to "internal fun abilityTapName(",
    // The log viewer's data half (PLAN row 7): the search, the routes, the trainer rules and the TMs as the app's
    // screens read them; the screens themselves are the phone's, the PC draws besteon's LogOverlay (PcLogOverlay).
    "LogViewer.kt" to "enum class LogTab(",
    "LogSearch.kt" to "enum class LogSort(",
    "LogSearch.kt" to "enum class LogFilter(",
    "LogSearch.kt" to "class LogTmRow(",
    "LogSearch.kt" to "object LogSearch {",
    "LogRoutes.kt" to "enum class LogEncType(",
    "LogRoutes.kt" to "class LogWild(",
    "LogRoutes.kt" to "class LogRoute(",   // runs on to the end of object LogRoutes, which follows it
    "LogTrainers.kt" to "enum class LogTrainerFilter(",
    "LogTrainers.kt" to "class LogTrainerRules(",
    "LogTms.kt" to "enum class LogTmFilter(",
    "LogTms.kt" to "class LogGymTm(",
    "LogTms.kt" to "object LogTms {",
    // The tracker's themes, chosen on the PC panel's settings screen (the phone's theme editor dialog stays behind).
    "Theme.kt" to "data class WholeTheme(",
    "Theme.kt" to "object ThemeStore {",
    "ThemePresets.kt" to "object ThemeCodes {",
    "ThemePresets.kt" to "object ThemePresets {",
    // Grade my notes: the app's grading (StatMarkingScoreSheet's ranges and exceptions); the PC draws besteon's sheet.
    "ScoreSheet.kt" to "object ScoreSheet {",
    // The favorite in a starter ball (Blake's hand test 2026-10-07): the official rules mode by mode, as the phone has them.
    "FavoriteRules.kt" to "internal object FavoriteRules {",
    // GachaMon's stars beside the heals: the ruleset a run's settings name asks for (autoDetermineIronmonRuleset).
    "GachaMon.kt" to "object GachaMonRulesets {",
)

/**
 * Members of an app object whose file cannot be compiled here (it reaches the phone's stores), copied one by one into
 * an object of the given header: (file, the object's header here, the members' first lines). A member runs from its
 * first line to its closing line at its own indentation when that line opens a block or a list, else to the last line
 * indented under it, so the rules themselves are the app's text, never a second copy. "private val" becomes "val".
 */
val members = listOf(
    Triple("FavoriteBall.kt", "internal object FavoriteBall {", listOf(
        "    const val JOURNEY = ", "    data class Candidate(", "    fun hnsLines(", "    fun lines(", "    fun takeable(")),
    Triple("RnqsInfo.kt", "internal object RnqsRulesets {", listOf("        private val RULESETS = listOf(")),
)

val copyPieces = tasks.register("copyAppPieces") {
    val out = layout.buildDirectory.file("pieces/com/ironmonone/app/PcAppPieces.kt")
    val sources = (pieces.map { it.first } + members.map { it.first }).distinct().map { File(appKt, it) }
    inputs.files(sources)
    outputs.file(out)
    doLast {
        val texts = sources.associate { it.name to it.readText().replace("\r\n", "\n") }
        // Only imports of what the desktop has: Compose, Kotlin, Java and the pure modules.
        val keep = listOf("import androidx.compose.", "import kotlin.", "import java.", "import com.ironmonone.tracker.", "import com.ironmonone.core.")
        val imports = sortedSetOf<String>()
        texts.values.forEach { t ->
            t.lineSequence().filter { l -> keep.any { l.startsWith(it) } && "ui.platform.LocalContext" !in l }.forEach { imports += it }
        }
        val body = StringBuilder()
        for ((file, start) in pieces) {
            val t = texts.getValue(file)
            val at = t.indexOf("\n$start").takeIf { it >= 0 }?.plus(1) ?: error("copyAppPieces: \"$start\" is not in $file")
            // Its annotations (@Composable) on the lines above.
            var from = at
            while (true) {
                val prev = t.lastIndexOf('\n', from - 2) + 1
                if (prev < from && t.substring(prev, from - 1).trim().startsWith("@")) from = prev else break
            }
            val firstLine = t.substring(at, t.indexOf('\n', at))
            val end = if (firstLine.trimEnd().endsWith("{") || firstLine.trimEnd().endsWith("(")) t.indexOf("\n}\n", at) + 2
                      else t.indexOf('\n', at)
            // A private card is called by the PC panel here; the phone's Android context is the platform's.
            val piece = t.substring(from, end).replaceFirst("private fun ", "internal fun ")
                .replace("androidx.compose.ui.platform.LocalContext.current", "LocalPcCtx.current")
            body.append("// from app/.../$file\n").append(piece).append("\n\n")
        }
        for ((file, header, starts) in members) {
            val lines = texts.getValue(file).lines()
            body.append("// members of app/.../$file\n").append(header).append("\n")
            for (start in starts) {
                val at = lines.indexOfFirst { it.startsWith(start) }.takeIf { it >= 0 } ?: error("copyAppPieces: \"$start\" is not in $file")
                val indent = start.takeWhile { it == ' ' }
                val first = lines[at].trimEnd()
                var end = at
                if (first.endsWith("{") || first.endsWith("(")) {
                    while (lines[end].trimEnd() != "$indent}" && lines[end].trimEnd() != "$indent)") end++
                } else {
                    while (end + 1 < lines.size && lines[end + 1].isNotBlank() && lines[end + 1].takeWhile { it == ' ' }.length > indent.length) end++
                }
                body.append(lines.subList(at, end + 1).joinToString("\n").replaceFirst("private val ", "val ")).append("\n\n")
            }
            body.append("}\n\n")
        }
        out.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "// Generated by pcui's copyAppPieces from the app's own files; do not edit.\n" +
                "@file:Suppress(\"unused\")\npackage com.ironmonone.app\n\n" + imports.joinToString("\n") + "\n\n" + body
        )
    }
}

// The assets the tracker's pictures come from, under /assets as in the APK.
val trackerAssets = tasks.register<Sync>("trackerAssets") {
    from(rootProject.file("app/src/main/assets")) { include("types/**", "badges/**", "status/**", "icons/**", "gbasprites/**",
        // Play as your Pokemon and the "Animated Pokemon" pictures (2026-10-07): the Walking Pals sets, PMD Sprite Collab's and DarkusShadow's.
        "walkingpals/**", "walkingpals-nat/**", "walkingpals-darkus/**", "walkingpals-expo/**", "walkingpals-ow/**") }
    into(layout.buildDirectory.dir("trackerres/assets"))
}

sourceSets {
    named("main") {
        kotlin.srcDir(appSrc.map { layout.buildDirectory.dir("appsrc").get() })
        kotlin.srcDir(copyPieces.map { layout.buildDirectory.dir("pieces").get() })
        resources.srcDir(trackerAssets.map { layout.buildDirectory.dir("trackerres").get() })
        resources.srcDir(rootProject.file("app/src/main/res"))   // font/press_start_2p.ttf
        resources.exclude("drawable*/**", "mipmap*/**", "values*/**", "xml/**", "layout/**", "raw/**")
    }
}

dependencies {
    // The Windows build of Skia: KaizoCore PC targets the streamer's Windows PC. api: the desktop module reads the
    // chat's StateFlow (kotlinx.coroutines, which Compose brings).
    api(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(project(":tracker-gba"))
    implementation(project(":tracker-nds"))
    implementation(project(":core-api"))
    implementation(project(":engine-hns"))   // BstRule's lines, the log names' engine tables
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
    systemProperty("kaizocore.repo", rootDir.absolutePath)
    // TextContrastTest checks the first CONTRAST_THEMES themes only when it is set: a run with it is not a run of all,
    // so it is an input, or a quick run left the full one "up to date" and skipped (2026-10-09).
    inputs.property("contrastThemes", System.getenv("CONTRAST_THEMES") ?: "all")
    inputs.property("contrastOnly", System.getenv("CONTRAST_ONLY") ?: "all")
}

// Renders the sample panels to build/png for a look by eye: ./gradlew :pcui:samples
tasks.register<JavaExec>("samples") {
    mainClass.set("com.ironmonone.pcui.SamplesKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args(layout.buildDirectory.dir("png").get().asFile.absolutePath + (providers.gradleProperty("pcuiOut").map { "-$it" }.getOrElse("")))
    providers.gradleProperty("pcuiText").orNull?.let { systemProperty("pcui.text", it) }
    providers.gradleProperty("pcuiHeight").orNull?.let { systemProperty("pcui.height", it) }
    providers.gradleProperty("pcuiCanvas").orNull?.let { systemProperty("pcui.canvas", it) }
}
