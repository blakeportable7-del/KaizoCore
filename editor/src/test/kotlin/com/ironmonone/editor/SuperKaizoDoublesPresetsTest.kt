package com.ironmonone.editor

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KaizoCore's "Super Kaizo Doubles" presets (Blake, 2026-11-06: "give the player the options"). There is no official
 * Super Kaizo Doubles ruleset: each file is that game's Super Kaizo preset with exactly the four randomizer changes the
 * official Kaizo Doubles rules list (tools/upr-settings/official-settings.md, "KAIZO DOUBLES RULES", rule 6):
 *
 *  - Standardize EXP Curves to Medium Slow,
 *  - Trainer Pokemon: Double Battle Mode on,
 *  - Additional Pokemon for Important Trainers +1 and Regular Trainers +1 (Boss Trainers keep Super Kaizo's).
 *
 * Made for every game whose presets have both a Super Kaizo and a Kaizo Doubles file (FRLG, RSE, DPPt, HGSS, and the
 * FireRed and Emerald Nat. Dex v1.2 files, which Heart & Soul runs too). The check below runs in every build: each
 * bundled file is its Super Kaizo file plus those four and nothing else, and each official Kaizo Doubles file makes the
 * same four changes to its Kaizo file. It writes the files only with IRONMON_GENERATE_PRESETS=1, never over one that
 * exists:
 *
 *   IRONMON_GENERATE_PRESETS=1 ./gradlew :editor:test --tests '*SuperKaizoDoublesPresetsTest*'
 */
class SuperKaizoDoublesPresetsTest {
    private val presets = File("../app/src/main/assets/presets")
    private val tags = listOf("FRLG", "RSE", "DPPt", "HGSS", "FRLG NatDex v1.2", "RSE NatDex v1.2")

    private fun cls(name: String): Class<*> =
        Class.forName(if ("NatDex" in name) "com.dabomstew.pkrandom.Settings" else "com.dabomstew.pkrandomzx.Settings")

    private fun read(f: File): Any = FileInputStream(f).use { cls(f.name).getMethod("read", FileInputStream::class.java).invoke(null, it) }

    private fun valueOf(o: Option, s: Any): String = when (o) {
        is Option.Bool -> o.get(s).toString(); is Option.IntValue -> o.get(s).toString(); is Option.Choice -> o.get(s)
    }

    private fun opts(name: String) = SettingsReflector.options(cls(name)).associateBy { it.id }

    /** The official Kaizo Doubles changes, made to [s] in place. */
    private fun doubles(name: String, s: Any) {
        val o = opts(name)
        (o.getValue("standardizeEXPCurves") as Option.Bool).set(s, true)
        (o.getValue("selectedEXPCurve") as Option.Choice).set(s, "MEDIUM_SLOW")
        (o.getValue("doubleBattleMode") as Option.Bool).set(s, true)
        for (id in listOf("additionalImportantTrainerPokemon", "additionalRegularTrainerPokemon")) {
            val n = o.getValue(id) as Option.IntValue
            n.set(s, n.get(s) + 1)
        }
    }

    private val changed = setOf("standardizeEXPCurves", "selectedEXPCurve", "doubleBattleMode",
        "additionalImportantTrainerPokemon", "additionalRegularTrainerPokemon")

    private fun differing(name: String, a: Any, b: Any): Map<String, Pair<String, String>> =
        opts(name).values.filter { valueOf(it, a) != valueOf(it, b) }.associate { it.id to (valueOf(it, a) to valueOf(it, b)) }

    @Test
    fun `each official Kaizo Doubles file makes the four changes to its Kaizo file`() {
        for (tag in tags) {
            val name = "$tag Kaizo Doubles.rnqs"
            val kaizo = read(File(presets, "$tag Kaizo.rnqs")); val official = read(File(presets, name))
            doubles(name, kaizo)
            // The four agree; anything else the two files differ in is not one of the rule's changes.
            assertEquals(emptySet(), differing(name, kaizo, official).keys.intersect(changed), tag)
        }
    }

    @Test
    fun `each Super Kaizo Doubles file is its Super Kaizo file with the four changes and nothing else`() {
        val write = System.getenv("IRONMON_GENERATE_PRESETS") == "1"
        for (tag in tags) {
            val name = "$tag Super Kaizo Doubles.rnqs"
            val sk = File(presets, "$tag Super Kaizo.rnqs")
            val want = read(sk).also { doubles(name, it) }
            val out = File(presets, name)
            if (!out.isFile && write) {
                FileOutputStream(out).use { cls(name).getMethod("write", FileOutputStream::class.java).invoke(want, it) }
                println("wrote $name")
            }
            assertTrue(out.isFile, "$name is missing: run with IRONMON_GENERATE_PRESETS=1")
            val got = read(out)
            assertEquals(emptyMap(), differing(name, want, got), name)
            assertEquals(changed - setOf("standardizeEXPCurves"), differing(name, read(sk), got).keys, "$name differs from Super Kaizo in")
            assertEquals("MEDIUM_SLOW", (opts(name).getValue("selectedEXPCurve") as Option.Choice).get(got))
        }
    }
}
