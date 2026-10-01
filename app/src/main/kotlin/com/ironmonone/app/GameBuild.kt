package com.ironmonone.app

import com.dabomstew.pkrandom.Settings as NdSettings
import com.dabomstew.pkrandomzx.Settings as ZxSettings
import com.ironmonone.app.engine.GameFacts
import com.ironmonone.core.RomKind
import com.ironmonone.editor.Option
import com.ironmonone.editor.SettingsReflector
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException

/**
 * "Build your own" (2026-09-29): a settings file made from plain-word choices
 * instead of the editor's 140 rows. Blake: "a full customization game where you
 * can choose any starter you want and other changes."
 *
 * This file is the whole of the builder that is not drawing (BuildYourGame.kt
 * draws it): what each choice is called, which of the randomizer's real Settings
 * fields each answer sets, how a plan becomes a settings file, and what the file
 * is called. It invents no field. Every field named below is one both engines
 * have (GameBuildTest reads each of them off both Settings classes), it is set
 * through the same reflective options the editor uses (SettingsReflector), and
 * the file is written and read back through the engine's own Settings class
 * before it is trusted, as the editor's Save As does.
 *
 * A plan is a starting point (an official preset, or the game as it is), the
 * starters, and the answers the player gave. Whatever the player did not answer
 * is not touched: it keeps the value the starting point has, the held items and
 * misc tweaks and everything else the builder does not cover included.
 */
object GameBuild {

    // ------------------------------------------------------------------ the choices

    /** One field of the engine's Settings, and the value an answer gives it, as the editor's options spell it (an enum's name, true or false, or a number). */
    data class Assign(val field: String, val value: String)

    /** One answer to a [Choice]: what it is called, one plain line about it, and the fields it sets. */
    class Pick(val id: String, val label: String, val detail: String, val sets: List<Assign>)

    /** Which page of the builder a choice sits on. */
    enum class Page { WORLD, POKEMON }

    /**
     * One question in plain words. [picks] are the answers, the first being "Unchanged", the
     * game as it is. [custom] says what a starting point's own value is when it is none of the
     * answers, in a few words ("Area 1-to-1 mapping").
     */
    class Choice(
        val id: String,
        val page: Page,
        val title: String,
        val blurb: String,
        val picks: List<Pick>,
        val custom: ((String) -> String) -> String,
    ) {
        fun pick(id: String): Pick = picks.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("\"$id\" is not an answer to \"$title\".")
    }

    private fun a(field: String, value: String) = Assign(field, value)

    /** The most the randomizer's own level slider goes to: 50 for ZX, 60 for the Nat. Dex fork (NewRandomizerGUI.form of each). */
    fun maxLevelBoost(kind: RomKind): Int = if (kind.isNatDex) 60 else 50

    /**
     * The choices that make sense for [kind], in the order the pages show them. [facts] leaves out
     * what the game does not have: abilities in Red, Blue, Yellow, Gold, Silver and Crystal, move
     * tutors in the games with none. With no facts (the ROM could not be read) they are all offered.
     */
    fun choices(kind: RomKind, facts: GameFacts.Facts?): List<Choice> {
        val out = ArrayList<Choice>()

        out += Choice(
            "wild", Page.WORLD, "Wild Pokémon", "What you meet in grass, caves and water.",
            listOf(
                Pick("unchanged", "Unchanged", "The game's own wild Pokémon.",
                    listOf(a("wildPokemonMod", "UNCHANGED"), a("wildPokemonRestrictionMod", "NONE"))),
                Pick("random", "Random", "Any Pokémon can turn up anywhere.",
                    listOf(a("wildPokemonMod", "RANDOM"), a("wildPokemonRestrictionMod", "NONE"))),
                Pick("similar", "Random, similar strength", "Random, but each one is close in strength to the one it replaces.",
                    listOf(a("wildPokemonMod", "RANDOM"), a("wildPokemonRestrictionMod", "SIMILAR_STRENGTH"))),
            ),
        ) { r ->
            SettingsReflector.valueLabel("wildPokemonMod", r("wildPokemonMod")) +
                if (r("wildPokemonRestrictionMod") == "SIMILAR_STRENGTH") ", similar strength" else ""
        }

        out += levelChoice("wildLevels", "Wild levels", "Raise every wild Pokémon's level by a percent.", "wildLevelsModified", "wildLevelModifier", kind)

        out += Choice(
            "trainers", Page.WORLD, "Trainers' Pokémon", "What the gym leaders, rivals and everyone else send out.",
            listOf(
                Pick("unchanged", "Unchanged", "The game's own trainer teams.",
                    listOf(a("trainersMod", "UNCHANGED"), a("trainersUsePokemonOfSimilarStrength", "false"))),
                Pick("random", "Random", "Every trainer gets random Pokémon.",
                    listOf(a("trainersMod", "RANDOM"), a("trainersUsePokemonOfSimilarStrength", "false"))),
                Pick("similar", "Random, similar strength", "Random, but each one is close in strength to the one it replaces.",
                    listOf(a("trainersMod", "RANDOM"), a("trainersUsePokemonOfSimilarStrength", "true"))),
            ),
        ) { r ->
            SettingsReflector.valueLabel("trainersMod", r("trainersMod")) +
                if (r("trainersUsePokemonOfSimilarStrength") == "true") ", similar strength" else ""
        }

        out += levelChoice("trainerLevels", "Trainer levels", "Raise every trainer Pokémon's level by a percent.", "trainersLevelModified", "trainersLevelModifier", kind)

        out += simple(
            "items", Page.WORLD, "Items on the ground", "Item balls and hidden items.", "fieldItemsMod",
            listOf(
                Triple("UNCHANGED", "Unchanged", "Item balls and hidden items stay where they are."),
                Triple("SHUFFLE", "Shuffle", "The same items, in new places."),
                Triple("RANDOM", "Random", "Every item ball and hidden item holds something new."),
                Triple("RANDOM_EVEN", "Random, spread evenly", "Random, with no item turning up much more often than the rest."),
            ),
        )

        out += simple(
            "types", Page.POKEMON, "Types", "The type or types of each Pokémon.", "typesMod",
            listOf(
                Triple("UNCHANGED", "Unchanged", "Every Pokémon keeps its own types."),
                Triple("RANDOM_FOLLOW_EVOLUTIONS", "Random, evolutions follow", "New types, and most evolutions copy the Pokémon they come from."),
                Triple("COMPLETELY_RANDOM", "Random for every Pokémon", "New types for every Pokémon, evolutions included."),
            ),
        )

        if (facts == null || facts.hasAbilities) out += simple(
            "abilities", Page.POKEMON, "Abilities", "The abilities each Pokémon can have.", "abilitiesMod",
            listOf(
                Triple("UNCHANGED", "Unchanged", "Every Pokémon keeps its own abilities."),
                Triple("RANDOMIZE", "Random", "Every Pokémon gets new abilities."),
            ),
        )

        out += simple(
            "evolutions", Page.POKEMON, "Evolutions", "What each Pokémon evolves into.", "evolutionsMod",
            listOf(
                Triple("UNCHANGED", "Unchanged", "Pokémon evolve into what they always did."),
                Triple("RANDOM", "Random", "Each Pokémon evolves into a different species."),
                Triple("RANDOM_EVERY_LEVEL", "Random, every level", "Each Pokémon evolves into a random species at every level. Loops are expected."),
            ),
        )

        out += simple(
            "movesets", Page.POKEMON, "Moves Pokémon learn", "The moves each Pokémon picks up as it levels.", "movesetsMod",
            listOf(
                Triple("UNCHANGED", "Unchanged", "Each Pokémon learns its own moves."),
                Triple("RANDOM_PREFER_SAME_TYPE", "Random, own types first", "New moves, leaning toward the Pokémon's own types."),
                Triple("COMPLETELY_RANDOM", "Random", "New moves, with no lean toward type."),
                Triple("METRONOME_ONLY", "Metronome only", "Every Pokémon, TM and move tutor teaches Metronome and nothing else."),
            ),
        )

        out += Choice(
            "moveStats", Page.POKEMON, "The moves themselves", "How hard, how accurate and how many uses.",
            listOf(
                Pick("unchanged", "Unchanged", "Every move keeps its power, accuracy, PP and type.",
                    listOf(a("randomizeMovePowers", "false"), a("randomizeMoveAccuracies", "false"), a("randomizeMovePPs", "false"), a("randomizeMoveTypes", "false"))),
                Pick("stats", "Random power, accuracy and PP", "Every move gets new numbers and keeps its type.",
                    listOf(a("randomizeMovePowers", "true"), a("randomizeMoveAccuracies", "true"), a("randomizeMovePPs", "true"), a("randomizeMoveTypes", "false"))),
                Pick("all", "Random power, accuracy, PP and type", "Every move gets new numbers and a new type.",
                    listOf(a("randomizeMovePowers", "true"), a("randomizeMoveAccuracies", "true"), a("randomizeMovePPs", "true"), a("randomizeMoveTypes", "true"))),
            ),
        ) { r ->
            val changed = listOf("randomizeMovePowers" to "power", "randomizeMoveAccuracies" to "accuracy", "randomizeMovePPs" to "PP", "randomizeMoveTypes" to "type")
                .filter { r(it.first) == "true" }.map { it.second }
            if (changed.isEmpty()) "Unchanged" else "Random " + changed.joinToString(", ")
        }

        out += simple(
            "tmMoves", Page.POKEMON, "TM moves", "The move each TM teaches. HMs are not touched.", "tmsMod",
            listOf(
                Triple("UNCHANGED", "Unchanged", "Each TM teaches its own move."),
                Triple("RANDOM", "Random", "Each TM teaches a new move."),
            ),
        )

        out += simple(
            "tmCompat", Page.POKEMON, "Who can learn TMs and HMs", "Which Pokémon are able to use each one.", "tmsHmsCompatibilityMod",
            compat("TMs and HMs"),
        )

        if (facts == null || facts.hasMoveTutors) {
            out += simple(
                "tutorMoves", Page.POKEMON, "Move tutor moves", "The move each tutor teaches.", "moveTutorMovesMod",
                listOf(
                    Triple("UNCHANGED", "Unchanged", "Each tutor teaches its own move."),
                    Triple("RANDOM", "Random", "Each tutor teaches a new move."),
                ),
            )
            out += simple(
                "tutorCompat", Page.POKEMON, "Who can learn from move tutors", "Which Pokémon are able to use each tutor.", "moveTutorsCompatibilityMod",
                compat("tutor moves"),
            )
        }
        return out
    }

    /** The four ways the randomizer can deal out TM, HM or tutor moves, for [what]. */
    private fun compat(what: String) = listOf(
        Triple("UNCHANGED", "Unchanged", "Every Pokémon can learn the $what it could before."),
        Triple("RANDOM_PREFER_TYPE", "Random, own types first", "New sets, more likely to include moves of the Pokémon's own types."),
        Triple("COMPLETELY_RANDOM", "Random", "Each of the $what is learnable by about half the Pokémon, whatever their type."),
        Triple("FULL", "Everyone learns everything", "Every Pokémon can learn every one of the $what."),
    )

    /** A choice that is one enum field: [values] are the enum's names, each with its label and line. The first is the game as it is. */
    private fun simple(id: String, page: Page, title: String, blurb: String, field: String, values: List<Triple<String, String, String>>) = Choice(
        id, page, title, blurb,
        values.map { (v, label, detail) -> Pick(v.lowercase(), label, detail, listOf(a(field, v))) },
    ) { r -> SettingsReflector.valueLabel(field, r(field)) }

    /** A level boost in steps of ten, up to what the randomizer's own slider offers for [kind]. 0 turns the modifier off. */
    private fun levelChoice(id: String, title: String, blurb: String, onField: String, percentField: String, kind: RomKind): Choice {
        val max = maxLevelBoost(kind)
        return Choice(
            id, Page.WORLD, title, "$blurb The randomizer stops at $max%.",
            (0..max step 10).map { n ->
                Pick(
                    "$n", if (n == 0) "0%" else "+$n%",
                    if (n == 0) "Levels stay as the game has them." else "Every level goes up by $n%.",
                    listOf(a(onField, (n > 0).toString()), a(percentField, n.toString())),
                )
            },
        ) { r -> if (r(onField) == "true") "+${r(percentField)}%" else "0%" }
    }

    // ------------------------------------------------------------------ the starters

    /** What the player wants done with the starters. */
    sealed interface Starters {
        /** Not touched: whatever the starting point has. */
        data object Keep : Starters
        /** The game's own three (two in Yellow). */
        data object Own : Starters
        /** Three random ones. */
        data object Random : Starters
        /** The player's own: one entry per starter slot, a species number or null for a random one. */
        data class Pick(val slots: List<Int?>) : Starters
    }

    /**
     * Why species [number] cannot be a custom starter in this game, or null. Only the last one in
     * the game's list, Mew, Celebi, Deoxys, Arceus, Genesect or the fork's last entry, which the
     * engine reads as out of range and swaps for the game's own starter, wrongly (see
     * [GameFacts.Facts.lastNumber]). A pick the engine would swap is refused here instead.
     */
    fun starterProblem(facts: GameFacts.Facts, number: Int): String? = when {
        facts.byNumber(number) == null -> "That is not a Pokémon this game has."
        number >= facts.lastNumber ->
            "${facts.byNumber(number)!!.name.ifBlank { "#$number" }} cannot be a custom starter. " +
                "The randomizer swaps the last Pokémon in its list for the game's own starter."
        else -> null
    }

    // ------------------------------------------------------------------ the plan

    /**
     * Everything the player has decided. [base] is the official preset the game starts from, or null
     * for the game as it is. [picks] maps a choice's id to the id of the answer given; a choice with
     * no entry is left as the starting point has it.
     */
    data class Plan(
        val base: File? = null,
        val starters: Starters = Starters.Keep,
        val picks: Map<String, String> = emptyMap(),
    ) {
        /** Whether the player has changed anything at all. */
        val touched: Boolean get() = base != null || starters != Starters.Keep || picks.isNotEmpty()
    }

    /** What a plan comes to. [settings] is the engine's Settings object, [bytes] the .rnqs it writes. */
    class Built(val cls: Class<*>, val settings: Any, val bytes: ByteArray)

    /** The engine's Settings class for a game: the Nat. Dex fork's for a Nat. Dex build, ZX's for the rest. */
    fun settingsClass(kind: RomKind): Class<*> = if (kind.isNatDex) NdSettings::class.java else ZxSettings::class.java

    private val optionCache = HashMap<Class<*>, Map<String, Option>>()

    private fun option(cls: Class<*>, field: String): Option {
        val all = synchronized(optionCache) { optionCache.getOrPut(cls) { SettingsReflector.options(cls).associateBy { it.id } } }
        return all[field] ?: throw IllegalStateException("The randomizer has no setting called \"$field\".")
    }

    /** A field's value as text, whatever its type. */
    fun get(cls: Class<*>, settings: Any, field: String): String = when (val o = option(cls, field)) {
        is Option.Bool -> o.get(settings).toString()
        is Option.IntValue -> o.get(settings).toString()
        is Option.Choice -> o.get(settings)
    }

    /** Sets a field from text: an enum's constant name (any of them, hidden or not), true or false, or a number. */
    fun set(cls: Class<*>, settings: Any, field: String, value: String) {
        when (val o = option(cls, field)) {
            is Option.Bool -> o.set(settings, value.toBooleanStrict())
            is Option.IntValue -> o.set(settings, value.toInt())
            is Option.Choice -> o.set(settings, value)
        }
    }

    /** The answer of [choice] the settings already have, or null when they match none. */
    fun matching(cls: Class<*>, settings: Any, choice: Choice): Pick? =
        choice.picks.firstOrNull { p -> p.sets.all { get(cls, settings, it.field) == it.value } }

    /** What the settings have for [choice], in a few words: the matching answer's label, else the choice's own wording. */
    fun currentText(cls: Class<*>, settings: Any, choice: Choice): String =
        matching(cls, settings, choice)?.label ?: choice.custom { get(cls, settings, it) }

    /** The custom starters as the engine holds them in memory: a species number plus one, or one for random. */
    fun customStarters(cls: Class<*>, settings: Any): IntArray = cls.getMethod("getCustomStarters").invoke(settings) as IntArray

    private fun setCustomStarters(cls: Class<*>, settings: Any, v: IntArray) {
        cls.getMethod("setCustomStarters", IntArray::class.java).invoke(settings, v)
    }

    /** What the engine stores for a starter slot: the species number plus one, or one for a random slot (the desktop's dropdown puts "Random" first). */
    fun storedStarter(number: Int?): Int = if (number == null) 1 else number + 1

    /**
     * The game as it is: a Settings with nothing randomized. A bare `new Settings()` cannot be written
     * (its ROM name and EXP curve are null), so those two are filled as the desktop fills them; the
     * update-to generations get the values the desktop's own dropdowns start on.
     */
    fun blank(kind: RomKind, facts: GameFacts.Facts?): Any {
        val cls = settingsClass(kind)
        val s = cls.getConstructor().newInstance()
        cls.getMethod("setRomName", String::class.java).invoke(s, "")
        val setCurve = cls.methods.first { it.name == "setSelectedEXPCurve" }
        setCurve.invoke(s, setCurve.parameterTypes[0].enumConstants.first { (it as Enum<*>).name == "MEDIUM_FAST" })
        val gen = kind.generation.number
        set(cls, s, "updateBaseStatsToGeneration", maxOf(6, gen + 1).toString())
        set(cls, s, "updateMovesToGeneration", (gen + 1).toString())
        // Custom starters start on the game's own, as the desktop dropdowns do, so a later switch to Custom in the editor shows them.
        setCustomStarters(cls, s, IntArray(3) { i -> facts?.ownStarters?.getOrNull(i)?.let { storedStarter(it) } ?: 1 })
        return s
    }

    /** The settings of an existing .rnqs, read by the engine that owns it. */
    fun read(cls: Class<*>, file: File): Any = try {
        FileInputStream(file).use { cls.getMethod("read", FileInputStream::class.java).invoke(null, it) }
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    private fun write(cls: Class<*>, settings: Any, tmpDir: File?): ByteArray {
        val tmp = File.createTempFile("build", ".rnqs", tmpDir)
        try {
            try {
                FileOutputStream(tmp).use { out -> cls.getMethod("write", FileOutputStream::class.java).invoke(settings, out) }
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
            return tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }

    /** Applies the plan's starters to [settings]. */
    fun applyStarters(cls: Class<*>, settings: Any, starters: Starters, facts: GameFacts.Facts?) {
        when (starters) {
            Starters.Keep -> Unit
            Starters.Own -> set(cls, settings, "startersMod", "UNCHANGED")
            Starters.Random -> set(cls, settings, "startersMod", "COMPLETELY_RANDOM")
            is Starters.Pick -> {
                val f = facts ?: throw IllegalArgumentException("The game's Pokémon list could not be read, so starters cannot be picked.")
                require(starters.slots.size == f.starterCount) { "This game has ${f.starterCount} starters, not ${starters.slots.size}." }
                starters.slots.forEach { n -> if (n != null) starterProblem(f, n)?.let { throw IllegalArgumentException(it) } }
                set(cls, settings, "startersMod", "CUSTOM")
                // A game with two starters (Yellow) leaves the third slot random; the engine ignores it.
                setCustomStarters(cls, settings, IntArray(3) { i -> storedStarter(starters.slots.getOrNull(i)) })
            }
        }
    }

    /**
     * The settings a plan comes to, and the file they write. The starting point is loaded fresh, the
     * answers are set over it in the order the pages show them, then the starters. Written and read
     * back through the engine before it returns: a file the engine reads differently is refused.
     */
    fun build(kind: RomKind, plan: Plan, facts: GameFacts.Facts?, tmpDir: File? = null): Built {
        val cls = settingsClass(kind)
        val s = plan.base?.let { read(cls, it) } ?: blank(kind, facts)
        for (c in choices(kind, facts)) plan.picks[c.id]?.let { id -> c.pick(id).sets.forEach { set(cls, s, it.field, it.value) } }
        applyStarters(cls, s, plan.starters, facts)
        val bytes = write(cls, s, tmpDir)
        val again = File.createTempFile("verify", ".rnqs", tmpDir)
        try {
            again.writeBytes(bytes)
            val back = read(cls, again)
            check(back.toString() == s.toString() && customStarters(cls, back).contentEquals(customStarters(cls, s))) {
                "the saved file did not read back the same"
            }
        } finally {
            again.delete()
        }
        return Built(cls, s, bytes)
    }

    // ------------------------------------------------------------------ the file

    const val NAME_LIMIT = 48
    const val DEFAULT_NAME = "my build"

    /**
     * What the player typed as a name a file can carry: letters and digits, spaces, and the odd
     * apostrophe or hyphen. Anything a file name cannot hold on a phone or a PC (slashes, colons,
     * quotes, control characters) becomes a space, brackets replace the parentheses the file name
     * puts around it, runs of spaces collapse, leading and trailing dots and spaces go and it is cut
     * at [NAME_LIMIT]. Null when nothing is left.
     */
    fun cleanName(typed: String): String? {
        var t = typed.trim().let { if (it.endsWith(".rnqs", ignoreCase = true)) it.dropLast(5) else it }
        t = buildString {
            for (c in t) append(
                when {
                    c.isISOControl() || c in "\\/:*?\"<>|" -> ' '
                    c == '(' -> '['
                    c == ')' -> ']'
                    else -> c
                },
            )
        }
        // Dots and spaces at either end go, however many and in whatever mix: "../../x" is "x", and a
        // name is never a run of dots.
        fun ends(c: Char) = c == '.' || c.isWhitespace()
        t = t.replace(Regex("\\s+"), " ").trim(::ends)
        if (t.length > NAME_LIMIT) {
            // Never cut a surrogate pair in half.
            val cut = if (Character.isHighSurrogate(t[NAME_LIMIT - 1])) NAME_LIMIT - 1 else NAME_LIMIT
            t = t.take(cut).trim(::ends)
        }
        return t.ifEmpty { null }
    }

    /**
     * The file's name: the game's tag, "NatDex" for a Nat. Dex build and the starting preset's mode in
     * front, then the player's name in parentheses, as the editor names its copies
     * ("FRLG Kaizo (my edit)"). The front is what the Run tab pairs the file with its game by, and
     * it survives the file being sent to another phone, which the sidecar does not. The parentheses
     * keep the file behind the official preset when the Mode row picks a file for a mode
     * (RulesetCatalog ranks a name with "(" last), so a build named "Kaizo" never replaces Kaizo.
     * Null when the player's name is empty.
     */
    fun fileName(kind: RomKind, baseRuleset: String?, typed: String): String? {
        val name = cleanName(typed) ?: return null
        val front = listOfNotNull(kind.family, "NatDex".takeIf { kind.isNatDex }, baseRuleset?.let { RnqsInfo.rulesetLabel(it) }).joinToString(" ")
        return "$front ($name).rnqs"
    }

    /**
     * Builds the plan and keeps it as a settings file in the store's settings folder, where the Run
     * tab lists them, beside its sidecar (family, Nat. Dex, and the mode it started from). Never over
     * another file: a second "FRLG (my build)" is "FRLG (my build) (2)" (PrepStore.addSettingsFile).
     * The failure's message is one a player can read.
     */
    fun save(store: PrepStore, kind: RomKind, plan: Plan, facts: GameFacts.Facts?, typedName: String): Result<File> = runCatching {
        val baseRuleset = plan.base?.let { RnqsInfo.of(it).ruleset }
        val name = fileName(kind, baseRuleset, typedName) ?: throw IllegalArgumentException("Give your game a name.")
        val built = build(kind, plan, facts, store.cacheDirFor())
        val saved = store.addSettingsFile(name, built.bytes).getOrThrow()
        RnqsInfo.writeMeta(saved, kind.family, kind.isNatDex, baseRuleset)
        saved
    }

    /**
     * What RUN selects once a save has made it read its lists again: the prepared game the builder was
     * for and the file it saved, each found in the fresh lists (null where it is not there). RUN works
     * its selection out afresh from the last run on every refresh, so without this a save could leave
     * a different game picked than the one just built for. Games are matched by kind id, as RUN
     * matches them everywhere else.
     */
    fun selectionAfterSave(
        prepared: List<Pair<RomKind, File>>,
        settings: List<File>,
        game: RomKind?,
        savedName: String,
    ): Pair<Pair<RomKind, File>?, File?> =
        prepared.firstOrNull { it.first.id == game?.id } to settings.firstOrNull { it.name == savedName }

    // ------------------------------------------------------------------ the words

    /** Plain lines for what the plan changes, for the last page. */
    fun summary(kind: RomKind, plan: Plan, facts: GameFacts.Facts?, baseLabel: String?): List<String> {
        val lines = ArrayList<String>()
        lines += if (baseLabel != null) "Starts from $baseLabel. Whatever you did not change stays as $baseLabel has it."
        else "Starts from the game as it is. Whatever you did not change stays as the game has it."
        starterText(plan.starters, facts)?.let { lines += "Starters: $it" }
        for (c in choices(kind, facts)) plan.picks[c.id]?.let { lines += "${c.title}: ${c.pick(it).label}" }
        return lines
    }

    /** The starters in a few words ("Pikachu, random, Chikorita"), or null when they are left alone. */
    fun starterText(starters: Starters, facts: GameFacts.Facts?): String? = when (starters) {
        Starters.Keep -> null
        Starters.Own -> "the game's own"
        Starters.Random -> "three random ones"
        is Starters.Pick -> starters.slots.joinToString(", ") { n -> if (n == null) "random" else SpeciesText.display(n, facts?.byNumber(n)?.name ?: "#$n") }
    }

    // ------------------------------------------------------------------ the species list

    /** One row of the species picker. [blocked] says why it cannot be chosen, or is null. */
    class Entry(val number: Int, val label: String, val iconId: Int?, val blocked: String?)

    /** Every species of the game as a picker row, in dex order. */
    fun entries(facts: GameFacts.Facts): List<Entry> {
        val shown = facts.species.map { SpeciesText.display(it.number, it.name) }
        val twice = shown.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return facts.species.mapIndexed { i, s ->
            Entry(
                s.number,
                if (shown[i] in twice) "${shown[i]} (#${s.number})" else shown[i],
                SpeciesText.iconId(s.number, shown[i]),
                starterProblem(facts, s.number),
            )
        }
    }

    /**
     * The rows that match what was typed: a number (with or without "#") finds that species first,
     * then names that start with it, then names that contain it. Dex order within each group.
     * Nothing typed is every row.
     */
    fun search(rows: List<Entry>, typed: String): List<Entry> {
        val q = typed.trim()
        if (q.isEmpty()) return rows
        val number = q.removePrefix("#").trim().toIntOrNull()
        val key = SpeciesText.normalize(q)
        fun rank(e: Entry): Int {
            val n = SpeciesText.normalize(e.label)
            return when {
                number != null && e.number == number -> 0
                key.isNotEmpty() && n.startsWith(key) -> 1
                key.isNotEmpty() && n.contains(key) -> 2
                number != null && e.number.toString().startsWith(number.toString()) -> 3
                else -> 4
            }
        }
        return rows.filter { rank(it) < 4 }.sortedBy { rank(it) }
    }
}

/**
 * Species names as a player reads them, and the picture for each. The randomizer hands over Gen 1 to
 * Gen 4 names in capitals ("MR. MIME", "HO-OH") and Gen 2's without the space; the tracker's own table
 * has them spelled (Favorites reads it too) and is keyed by the national number up to 251, which is
 * also the number of the picture in the bundled sprite pack. Past 251 the pack is keyed by the
 * Nat. Dex expansion's own ids (SpritePackTest), so a picture is found by name.
 */
internal object SpeciesText {

    private val table: List<Pair<Int, String>> by lazy {
        val out = ArrayList<Pair<Int, String>>()
        com.ironmonone.tracker.GbaTracker::class.java.getResourceAsStream("/natdex/species.tsv")
            ?.bufferedReader()?.useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t'); if (tab < 0) continue
                    val id = line.substring(0, tab).toIntOrNull() ?: continue
                    val name = line.substring(tab + 1).trim()
                    if (name.isNotEmpty() && name != "none") out += id to name
                }
            }
        out
    }

    private val spelledById: Map<Int, String> by lazy { table.filter { it.first <= 251 }.toMap() }

    private val idByKey: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for ((id, name) in table) m.putIfAbsent(normalize(name), id)
        m
    }

    /** Lower case, letters and digits only: "Mime Jr." and "MIME JR." meet. */
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** The name to show for species [number], which the engine calls [raw]. */
    fun display(number: Int, raw: String): String {
        val n = raw.trim()
        if (n.any { it.isLowerCase() }) return n
        spelledById[number]?.let { return it }
        return n.lowercase().split(' ').joinToString(" ") { w -> w.split('-').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } } }
    }

    /** The bundled sprite for species [number] called [shown], or null. */
    fun iconId(number: Int, shown: String): Int? = if (number in 1..251) number else idByKey[normalize(shown)]
}
