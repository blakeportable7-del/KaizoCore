package com.ironmonone.editor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Turns the official settings STRINGS into bundled .rnqs presets, through the
 * vanilla engine's own reader - so a preset is exactly what the string says
 * and nothing was retyped.
 *
 * A test class because the engine and the bundled-asset paths are on this
 * module's test classpath and nowhere more convenient. It only writes when
 * IRONMON_GENERATE_PRESETS=1 is in the environment; a plain `gradle test`
 * runs the read-only checks and touches nothing.
 *
 *   IRONMON_GENERATE_PRESETS=1 ./gradlew :editor:test --tests '*PresetGenerator*'
 *
 * Rules:
 *  - never overwrite a preset that already exists (Blake's copies stay his);
 *    report instead whether the gist's string agrees with it,
 *  - TWO_PART rows (Gen 1) are written to tools/upr-settings/generated/,
 *    NOT to the app, because the app cannot randomize twice yet,
 *  - every written file is read back and every option compared, or it is
 *    deleted and the run fails.
 */
class PresetGenerator {

    private val tsv = File("../tools/upr-settings/strings.tsv")
    private val presets = File("../app/src/main/assets/presets")
    private val sidelined = File("../tools/upr-settings/generated")
    private val cls: Class<*> = Class.forName("com.dabomstew.pkrandomzx.Settings")

    private data class Row(val tag: String, val mode: String, val flag: String, val string: String)

    private fun rows(): List<Row> = tsv.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }.map { Row(it[0], it[1], it[2], it[3]) }

    /**
     * The desktop GUI's reading of a settings string (NewRandomizerGUI ~1345):
     * three-digit version prefix, SettingsUpdater for an older one, refusal of
     * a newer one, then fromString on the rest. fromString alone chokes on
     * every real string, prefix and all - which is how the first run of this
     * generator failed on all 42.
     */
    private fun fromString(s: String): Any {
        val t = s.trim()
        require(t.length > 3 && t.take(3).all { it.isDigit() }) { "no version prefix: ${t.take(8)}" }
        val version = t.take(3).toInt()
        val current = Class.forName("com.dabomstew.pkrandomzx.Version").getField("VERSION").getInt(null)
        require(version <= current) { "string is from a newer randomizer ($version > $current)" }
        val body = if (version < current) {
            val upd = Class.forName("com.dabomstew.pkrandomzx.SettingsUpdater").getConstructor().newInstance()
            upd.javaClass.getMethod("update", Int::class.javaPrimitiveType, String::class.java)
                .invoke(upd, version, t.substring(3)) as String
        } else t.substring(3)
        return cls.getMethod("fromString", String::class.java).invoke(null, body)
    }

    private fun read(f: File): Any = FileInputStream(f).use {
        cls.getMethod("read", FileInputStream::class.java).invoke(null, it)
    }

    private fun write(settings: Any, f: File) = FileOutputStream(f).use {
        cls.getMethod("write", FileOutputStream::class.java).invoke(settings, it)
    }

    private fun valueOf(o: Option, on: Any): Any = when (o) {
        is Option.Bool -> o.get(on); is Option.IntValue -> o.get(on); is Option.Choice -> o.get(on)
    }

    private fun differing(a: Any, b: Any): List<String> =
        SettingsReflector.options(cls).filter { valueOf(it, a) != valueOf(it, b) }.map { it.label }

    @Test
    fun `every string on the page is one the vanilla engine can read`() {
        assertTrue(tsv.exists(), "run tools/upr-settings/parse_gist.py first")
        val bad = rows().mapNotNull { r ->
            runCatching { fromString(r.string) }.exceptionOrNull()?.let { e ->
                "${r.tag} ${r.mode}: ${e.cause?.message ?: e.message}"
            }
        }
        assertTrue(bad.isEmpty(), "unreadable strings:\n" + bad.joinToString("\n"))
    }

    @Test
    fun `generate the bundled presets when asked`() {
        if (System.getenv("IRONMON_GENERATE_PRESETS") != "1") return
        val report = StringBuilder()
        var written = 0
        for (r in rows()) {
            val settings = fromString(r.string)
            val dir = if (r.flag == "TWO_PART") sidelined.also { it.mkdirs() } else presets
            val name = if (r.mode.startsWith("PART")) "${r.tag} ${r.mode.replace(":", "")}.rnqs"
                       else "${r.tag} ${r.mode}.rnqs"
            val f = File(dir, name)
            if (f.exists()) {
                val diff = differing(read(f), settings)
                report.appendLine("kept    $name" +
                    if (diff.isEmpty()) "  (matches the page)" else "  DIFFERS from the page in ${diff.size}: ${diff.take(4)}")
                continue
            }
            write(settings, f)
            val back = differing(read(f), settings)
            if (back.isNotEmpty()) { f.delete(); error("$name did not round-trip: $back") }
            written++
            report.appendLine("written $name")
        }
        report.appendLine("$written written")
        println(report)
        File("../tools/upr-settings/last-run.txt").writeText(report.toString())
    }
}
