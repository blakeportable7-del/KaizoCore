package com.ironmonone.editor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Turns the official settings STRINGS into bundled .rnqs presets, through the
 * vanilla engine's own reader - so a preset is exactly what the string says
 * and nothing was retyped. The community rulesets' published strings
 * (tools/upr-settings/community.tsv: IronMON Journey, Chaos Kaizo, Evo Kaizo,
 * Survival Revival) go through the same path.
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
 *  - TWO_PART rows (Gen 1) and PRE_PASS rows (the 60% workaround) go to the
 *    app like the rest: it runs Gen 1's PART 2 and the pre-pass itself
 *    (Randomizers), and their names keep them out of the Mode row,
 *  - every written file is read back and every option compared, or it is
 *    deleted and the run fails.
 */
class PresetGenerator {

    private val tsv = File("../tools/upr-settings/strings.tsv")
    private val community = File("../tools/upr-settings/community.tsv")
    private val presets = File("../app/src/main/assets/presets")
    private val cls: Class<*> = Class.forName("com.dabomstew.pkrandomzx.Settings")

    private data class Row(val tag: String, val mode: String, val flag: String, val string: String)

    private fun rows(): List<Row> = (tsv.readLines() + community.readLines()).filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }.map { Row(it[0], it[1], it[2], it[3]) }

    /**
     * The ROM name of a string written by something-smart/ironmon-randomizer
     * ("IM-2.3", which calls itself version 321 but keeps 59 bytes of settings
     * where ZX has 51): ZX's 51, then its 64-bit misc tweaks again as two ints
     * (low word, high word), then the ROM name. Survival Revival's README string
     * is one (flag EXTRA_TWEAKS). ZX 4.6.1 reads byte 51, the first of the eight,
     * as the name's length, so every setting loads and the name comes out empty.
     * The name is read where that fork keeps it, after checking the eight bytes
     * hold nothing ZX lacks: the low word is ZX's own misc tweaks int (bytes 32
     * to 35), the high word is zero, and no bit past 21 is set (bit 22 is that
     * fork's "revert berries" and ZX's "disable low HP music").
     */
    private fun imForkRomName(s: String): String {
        val d = java.util.Base64.getDecoder().decode(s.trim().substring(3))
        fun int(o: Int) = java.nio.ByteBuffer.wrap(d, o, 4).int
        require(int(51) == int(32)) { "the eight bytes are not the misc tweaks again" }
        require(int(55) == 0 && (int(51).toLong() and 0xFFFFFFFFL) shr 22 == 0L) { "the string uses tweaks ZX does not have" }
        val len = d[59].toInt() and 0xFF
        require(60 + len + 8 == d.size) { "not the ironmon-randomizer layout" }
        return String(d, 60, len, Charsets.US_ASCII)
    }

    private fun romName(settings: Any): String = cls.getMethod("getRomName").invoke(settings) as String

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
    fun `the Survival Revival string's extra bytes are that fork's misc tweaks, carrying nothing ZX lacks`() {
        val r = rows().single { it.flag == "EXTRA_TWEAKS" }
        assertTrue(r.tag == "FRLG" && r.mode == "Survival Revival", r.toString())
        assertTrue(romName(fromString(r.string)).isEmpty(), "ZX 4.6.1 reads no ROM name out of it")
        assertTrue(imForkRomName(r.string) == "Fire Red (U) 1.1", imForkRomName(r.string))
    }

    @Test
    fun `generate the bundled presets when asked`() {
        if (System.getenv("IRONMON_GENERATE_PRESETS") != "1") return
        val report = StringBuilder()
        var written = 0
        for (r in rows()) {
            val settings = fromString(r.string)
            if (r.flag == "EXTRA_TWEAKS") cls.getMethod("setRomName", String::class.java).invoke(settings, imForkRomName(r.string))
            val dir = presets
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
            val back = differing(read(f), settings) + listOfNotNull("ROM name".takeIf { romName(read(f)) != romName(settings) })
            if (back.isNotEmpty()) { f.delete(); error("$name did not round-trip: $back") }
            written++
            report.appendLine("written $name")
        }
        report.appendLine("$written written")
        println(report)
        File("../tools/upr-settings/last-run.txt").writeText(report.toString())
    }
}
