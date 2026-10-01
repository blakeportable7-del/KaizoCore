package com.ironmonone.app

import android.content.Context
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import java.io.File

/**
 * The passes the official rules add around a mode's own settings, and the
 * player's say over them. Blake, 2026-09-29: "just give the user full control
 * over what mode and settings they choose. strict modes they select, or custom
 * modes or modified etc".
 *
 * The 60% levels (Randomizers.withPrePass): Emerald Kaizo, Survival and Super
 * Kaizo, and Gold, Silver and Crystal Kaizo and Survival, run at +60%, past
 * the randomizer's 50% cap. The settings page's workaround is to randomize
 * once with a string that raises trainer and wild levels 6% and nothing else,
 * then randomize that with the mode's string (1.06 x 1.5, about x1.59).
 *
 * Gen 1's PART 2 (Randomizers.gen1): the page's second way to play Red, Blue
 * and Yellow, every curve set to Slow over PART 1's output. Its first way is
 * the pseudo-fluctuating patch with PART 1 alone, and PART 2 would undo that
 * patch, so an official preset takes PART 2 on a vanilla build and not on a
 * patched one.
 *
 * Nothing here is forced. A pass is on by default only when the settings file
 * is an OFFICIAL preset, byte for byte one the app bundles (so an edited copy
 * counts as custom), of a mode whose rules call for it on this game. RUN shows
 * it and the player can switch it either way. The choice is kept per game and
 * settings file in prep/extra-passes.txt, so Play's NEW RUN repeats it.
 */
object ExtraPasses {

    /** Emerald's pre-pass: the settings page's string, trainer and wild levels +6%. */
    const val RSE_PRE_PASS = "RSE PRE-PASS.rnqs"

    /** Gold, Silver and Crystal's: the same, from the page's GSC section. */
    const val GSC_PRE_PASS = "GSC PRE-PASS.rnqs"

    /** The keys a choice is stored under. */
    const val PRE_60 = "prepass"
    const val PART_2 = "part2"

    /**
     * The pre-pass [kind] takes, or null where none does: never Nat. Dex (the
     * fork's own Emerald files are +60 already), never Ruby or Sapphire (the
     * rule is Emerald's), and never Faster Emerald 1.3.2, whose patch carries
     * the same 6%: its trainer and wild levels equal this pre-pass run on the
     * clean dump, slot for slot (PrePassLevelsTest), so a pre-pass there would
     * raise every level twice. Faster Emerald 1.2.1 has no increase and takes it.
     */
    fun prePassName(kind: RomKind): String? = when {
        kind.isNatDex -> null
        kind.id == RomKind.EMERALD_FASTER.id -> null
        kind.id == RomKind.EMERALD_U.id || kind.baseId == RomKind.EMERALD_U.id -> RSE_PRE_PASS
        kind.family == "GSC" -> GSC_PRE_PASS
        else -> null
    }

    /** Emerald's modes that take the 60% levels (callsFor60). */
    val RSE_60_MODES = setOf("kaizo", "survival", "superkaizo", "kaizodoubles", "chaoskaizo", "ironmonjourney")

    /** Whether a build already carries the 6% (Faster Emerald 1.3.2), so RUN can say why there is no switch. */
    fun carries60(kind: RomKind): Boolean = kind.id == RomKind.EMERALD_FASTER.id

    /**
     * The modes whose rules call for the 60% levels: Emerald's "Rules updates
     * to Kaizo/Survival" and the Super Kaizo README's "then do the normal 60%
     * level scaling"; Gold, Silver and Crystal's "Kaizo/Survival should use a
     * 60% level boost". Emerald's Kaizo Doubles, Chaos Kaizo and IronMON Journey
     * build on Kaizo and their books carry its Emerald section, and Blake ruled
     * they take the levels too (2026-10-01: "Boost on for all three").
     */
    fun callsFor60(kind: RomKind, ruleset: String?): Boolean = when (kind.family) {
        "RSE" -> ruleset in RSE_60_MODES
        "GSC" -> ruleset == "kaizo" || ruleset == "survival"
        else -> false
    }

    /**
     * The bundled preset whose bytes [bytes] are, by name: the same-named one
     * first, then any other (a renamed copy of an official preset is still
     * official). Null for an edited, imported or missing file.
     */
    fun officialName(name: String, bytes: ByteArray?, bundled: Map<String, ByteArray>): String? {
        if (bytes == null) return null
        bundled[name]?.let { if (it.contentEquals(bytes)) return name }
        return bundled.entries.firstOrNull { it.value.contentEquals(bytes) }?.key
    }

    /** On by default: an official preset of a mode that calls for the 60% levels, on a game that takes the pre-pass. */
    fun prePassByDefault(kind: RomKind, settingsName: String, bytes: ByteArray?, bundled: Map<String, ByteArray>): Boolean {
        if (prePassName(kind) == null) return false
        val official = officialName(settingsName, bytes, bundled) ?: return false
        return callsFor60(kind, RnqsInfo.parse(official).ruleset)
    }

    /** The player's choice if there is one, else the default. */
    fun prePassOn(kind: RomKind, settingsName: String, bytes: ByteArray?, bundled: Map<String, ByteArray>, choices: Choices): Boolean =
        prePassName(kind) != null &&
            (choices.get(kind.id, settingsName, PRE_60) ?: prePassByDefault(kind, settingsName, bytes, bundled))

    /** Whether [kind] is Red, Blue or Yellow, the games PART 2 is for. */
    fun takesPart2(kind: RomKind): Boolean = kind.generation == Generation.GB1

    /** Whether [kind] carries the pseudo-fluctuating patch, the other way PART 2 is there for. */
    fun patched(kind: RomKind): Boolean = kind.patchTag == "pseudofluct"

    /**
     * What a run's record says when its official file ran without what the rules add (IronMON rules check R4,
     * 2026-09-30): the 60% levels switched off ("50% levels"), a Red, Blue or Yellow build with neither the patch nor
     * PART 2 ("no PART 2"), Super Kaizo on a build without smart AI ("no Smart AI"), and a mode that is not a 60% one on
     * Faster Emerald 1.3.2, which carries 6% of its own ("+6% levels"). Null when it played as its rules have it, and
     * for a custom file, which says "(custom)" instead (CustomRuns).
     */
    fun variant(kind: RomKind, settingsName: String, bytes: ByteArray?, bundled: Map<String, ByteArray>, prePassTaken: Boolean, part2Taken: Boolean): String? {
        val official = officialName(settingsName, bytes, bundled) ?: return null
        val ruleset = RnqsInfo.parse(official).ruleset
        val notes = buildList {
            if (prePassName(kind) != null && callsFor60(kind, ruleset) && !prePassTaken) add("50% levels")
            if (carries60(kind) && !callsFor60(kind, ruleset)) add("+6% levels")
            if (takesPart2(kind) && !patched(kind) && !part2Taken) add("no PART 2")
            if (ruleset == "superkaizo" && RulesetCatalog.superKaizoWarning(kind, "superkaizo") != null) add("no Smart AI")
        }
        return notes.joinToString(", ").ifEmpty { null }
    }

    /** On by default: an official preset on a Red, Blue or Yellow build without the patch. */
    fun part2ByDefault(kind: RomKind, settingsName: String, bytes: ByteArray?, bundled: Map<String, ByteArray>): Boolean =
        takesPart2(kind) && !patched(kind) && officialName(settingsName, bytes, bundled) != null

    fun part2On(kind: RomKind, settingsName: String, bytes: ByteArray?, bundled: Map<String, ByteArray>, choices: Choices): Boolean =
        takesPart2(kind) && (choices.get(kind.id, settingsName, PART_2) ?: part2ByDefault(kind, settingsName, bytes, bundled))

    /**
     * The player's choices, one line per game, settings file and pass:
     * "<kind id> TAB <settings file> TAB <key> TAB on|off". Only a choice that
     * was made is stored; everything else follows the default.
     */
    class Choices(private val file: File) {
        private fun read(): List<List<String>> = runCatching {
            file.takeIf { it.isFile }?.readLines()?.map { it.split('\t') }?.filter { it.size == 4 }
        }.getOrNull() ?: emptyList()

        @Synchronized
        fun get(kindId: String, settingsName: String, key: String): Boolean? =
            read().lastOrNull { it[0] == kindId && it[1] == settingsName && it[2] == key }?.let { it[3] == "on" }

        @Synchronized
        fun set(kindId: String, settingsName: String, key: String, on: Boolean) {
            val kept = read().filterNot { it[0] == kindId && it[1] == settingsName && it[2] == key }
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText((kept + listOf(listOf(kindId, settingsName, key, if (on) "on" else "off")))
                    .joinToString("") { it.joinToString("\t") + "\n" })
            }
        }
    }

    // ------------------------------------------------------------ on the phone

    @Volatile private var bundledCache: Map<String, ByteArray>? = null

    /** Every bundled preset's bytes, by name, read once from the APK. */
    fun bundled(context: Context): Map<String, ByteArray> = bundledCache ?: runCatching {
        val a = context.assets
        (a.list("presets") ?: emptyArray()).filter { it.endsWith(".rnqs", true) }
            .associateWith { n -> a.open("presets/$n").use { it.readBytes() } }
    }.getOrDefault(emptyMap()).also { bundledCache = it }

    fun choices(context: Context): Choices = Choices(File(context.filesDir, "prep/extra-passes.txt"))

    private fun bytesOf(f: File): ByteArray? = runCatching { f.takeIf { it.isFile }?.readBytes() }.getOrNull()

    /** RUN's switch: whether a run of [settings] on [kind] takes the 60% levels. */
    fun prePassOn(context: Context, kind: RomKind, settings: File): Boolean =
        prePassOn(kind, settings.name, bytesOf(settings), bundled(context), choices(context))

    /** Whether [settings] is an official preset, for RUN's line under the switch. */
    fun isOfficial(context: Context, settings: File): Boolean =
        officialName(settings.name, bytesOf(settings), bundled(context)) != null

    fun choosePrePass(context: Context, kind: RomKind, settings: File, on: Boolean) =
        choices(context).set(kind.id, settings.name, PRE_60, on)

    /** RUN's PART 2 switch: whether a Red, Blue or Yellow run of [settings] takes PART 2. */
    fun part2On(context: Context, kind: RomKind, settings: File): Boolean =
        part2On(kind, settings.name, bytesOf(settings), bundled(context), choices(context))

    fun choosePart2(context: Context, kind: RomKind, settings: File, on: Boolean) =
        choices(context).set(kind.id, settings.name, PART_2, on)

    /** PART 2 for a run, or null when the run takes none; put back from the APK when missing, as [prePassFor] does. */
    /** [on], when given, is a run code's own choice for this one build (R4): the player's switch is left as it is. */
    fun secondPassFor(context: Context, store: PrepStore, kind: RomKind, settings: File, on: Boolean? = null): File? {
        val f = store.secondPassSettings(kind) ?: return null
        if (!(on ?: part2On(context, kind, settings))) return null
        if (!f.isFile) runCatching {
            context.assets.open("presets/${f.name}").use { input -> f.outputStream().use { input.copyTo(it) } }
        }
        return f
    }

    /**
     * The pre-pass file for a run, or null when the run takes none. Put back
     * from the APK when missing: presets are copied in by RUN, and Play's NEW
     * RUN can come first after an update.
     */
    fun prePassFor(context: Context, store: PrepStore, kind: RomKind, settings: File, on: Boolean? = null): File? {
        val name = prePassName(kind) ?: return null
        if (!(on ?: prePassOn(context, kind, settings))) return null
        val f = store.settingsFile(name)
        if (!f.isFile) runCatching {
            context.assets.open("presets/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
        }
        return f
    }
}
