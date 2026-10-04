package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.gachamon.GachaMonBst
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.GachaMonCodec
import com.ironmonone.tracker.gachamon.GachaMonMaker
import com.ironmonone.tracker.gachamon.GachaMonMoves
import com.ironmonone.tracker.gachamon.GachaMonPrize
import com.ironmonone.tracker.gachamon.GachaMonRatingSystem
import com.ironmonone.tracker.gachamon.MoveCategory
import com.ironmonone.tracker.gachamon.MoveFacts
import com.ironmonone.tracker.gachamon.SixStats
import java.io.File

/**
 * What KaizoCore notes about a card beside the PC tracker's record, which has no room for them: the names it was made
 * with (a card from an earlier run is shown without that game), the move powers its View tab lists, ids a share code
 * cannot hold, when it was made, and a prize card's trainer. Saved on the card's line after its code.
 */
data class GachaMonNotes(
    /** How the card's ids are numbered: "" for the five games and Nat. Dex (one numbering), "maxdex" for MaxDex's own. */
    val dex: String = "",
    val name: String = "",
    val ability: String = "",
    val moves: List<String> = emptyList(),
    /** MoveData's power text for each move as the card was made ("90", ">HP"). */
    val powers: List<String> = emptyList(),
    /** The four move ids where one is past what a share code holds (a Nat. Dex move past 511); empty otherwise. */
    val moveIds: List<Int> = emptyList(),
    /** The ability id where it is past a byte; 0 otherwise. */
    val abilityId: Int = 0,
    /** When it was made, epoch milliseconds: the Captures tab's newest first. 0 for a card from a code. */
    val at: Long = 0,
    /** Trainer battles its Pokemon has won this run, towards the two that keep it (GachaMonData.TRAINERS_TO_DEFEAT). */
    val trainers: Int = 0,
    /** A prize card's trainer ("Leader Brock") and where they stand, as its reveal shows them. */
    val trainer: String = "",
    val place: String = "",
) {
    fun line(): String {
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('|', '/')
        val out = ArrayList<String>()
        if (dex.isNotEmpty()) out += "dex=$dex"
        if (name.isNotEmpty()) out += "name=${clean(name)}"
        if (ability.isNotEmpty()) out += "ability=${clean(ability)}"
        if (moves.isNotEmpty()) out += "moves=" + moves.joinToString("|") { clean(it) }
        if (powers.isNotEmpty()) out += "powers=" + powers.joinToString("|") { clean(it) }
        if (moveIds.isNotEmpty()) out += "moveIds=" + moveIds.joinToString(",")
        if (abilityId > 0) out += "abilityId=$abilityId"
        if (at > 0) out += "at=$at"
        if (trainers > 0) out += "trainers=$trainers"
        if (trainer.isNotEmpty()) out += "trainer=${clean(trainer)}"
        if (place.isNotEmpty()) out += "place=${clean(place)}"
        return out.joinToString("\t")
    }

    companion object {
        fun parse(fields: List<String>): GachaMonNotes {
            val m = fields.mapNotNull { f -> f.indexOf('=').takeIf { it > 0 }?.let { f.substring(0, it) to f.substring(it + 1) } }.toMap()
            fun list(k: String) = m[k]?.split('|')?.map { it.trim() } ?: emptyList()
            return GachaMonNotes(
                dex = m["dex"].orEmpty(), name = m["name"].orEmpty(), ability = m["ability"].orEmpty(),
                moves = list("moves"), powers = list("powers"),
                moveIds = m["moveIds"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                abilityId = m["abilityId"]?.toIntOrNull() ?: 0, at = m["at"]?.toLongOrNull() ?: 0,
                trainers = m["trainers"]?.toIntOrNull() ?: 0, trainer = m["trainer"].orEmpty(), place = m["place"].orEmpty(),
            )
        }
    }
}

/** One card as the app keeps it. [uid] tells two identical cards apart in a list; it is not saved. */
data class GachaMonEntry(val card: GachaMonCard, val notes: GachaMonNotes = GachaMonNotes(), val uid: Long = GachaMon.nextUid()) {
    /** The line of a GachaMon file: the PC tracker's share code, then the notes. */
    fun line(): String = listOf(GachaMonCodec.shareCode(card), notes.line()).filter { it.isNotEmpty() }.joinToString("\t")

    val stars: Int get() = card.stars()
    val moveIds: List<Int> get() = notes.moveIds.ifEmpty { card.moveIds }
    val abilityId: Int get() = if (notes.abilityId > 0) notes.abilityId else card.abilityId
    val speciesName: String get() = notes.name.ifBlank { GachaMonNames.species(card.pokemonId, notes.dex) }
    val abilityName: String get() = notes.ability.ifBlank { GachaMonNames.ability(abilityId, notes.dex) }
    fun moveName(i: Int): String = notes.moves.getOrNull(i)?.takeIf { it.isNotBlank() }
        ?: moveIds.getOrNull(i)?.takeIf { it > 0 }?.let { GachaMonNames.move(it, notes.dex) } ?: "---"
    /** IGachaMon.getAssociatedTrainerName: "Brock" for a prize card, else null. */
    val trainerName: String? get() = GachaMonPrize.trainerName(card)

    companion object {
        fun parse(line: String): GachaMonEntry? {
            val f = line.split('\t')
            val card = GachaMonCodec.fromShareCode(f.firstOrNull() ?: return null) ?: return null
            return GachaMonEntry(card, GachaMonNotes.parse(f.drop(1)))
        }
    }
}

/** The species, move and ability names a card from another game is shown with: the reference's own lists. */
internal object GachaMonNames {
    private fun load(resource: String, column: Int = 1): Map<Int, String> {
        val out = HashMap<Int, String>()
        GbaTracker::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            lines.filter { !it.startsWith("#") }.forEach { line ->
                val p = line.split('\t')
                p.getOrNull(0)?.trim()?.toIntOrNull()?.let { id -> p.getOrNull(column)?.trim()?.takeIf { it.isNotEmpty() }?.let { out[id] = it } }
            }
        }
        return out
    }
    private val gen3Species by lazy { load("/gen3/species-extra.tsv") }
    private val natDexSpecies by lazy { load("/natdex/species.tsv") }
    private val maxDexSpecies by lazy { load("/maxdex/species.tsv") }
    private val gen3Moves by lazy { load("/gen3/movedesc.tsv") }
    private val natDexMoves by lazy { load("/natdex/moves.tsv") }
    private val maxDexMoves by lazy { load("/maxdex/moves.tsv") }
    private val gen3Abilities by lazy { load("/gen3/abilitydesc.tsv") }
    private val maxDexAbilities by lazy { load("/maxdex/abilities.tsv") }

    fun species(id: Int, dex: String): String = (if (dex == "maxdex") maxDexSpecies[id] else null)
        ?: gen3Species[id] ?: natDexSpecies[id] ?: "#$id"
    fun move(id: Int, dex: String): String = (if (dex == "maxdex") maxDexMoves[id] else null)
        ?: gen3Moves[id] ?: natDexMoves[id] ?: "#$id"
    fun ability(id: Int, dex: String): String = if (id <= 0) "---" else (if (dex == "maxdex") maxDexAbilities[id] else null)
        ?: gen3Abilities[id] ?: "#$id"
}

/**
 * GachaMon's seven options (Options.lua: "GachaMon Ratings Ruleset" and the six switches), at the reference's defaults.
 * Kept in prep/gachamon/options.txt as key=value lines.
 */
object GachaMonOptions {
    /** "GachaMon Ratings Ruleset": "AutoDetect", or a ruleset key (Standard, Kaizo, SuperKaizo...). */
    var ruleset by mutableStateOf("AutoDetect")
    /** "Add GachaMon to collection if its new": "It's a new Pokemon species". Off. */
    var addIfNew by mutableStateOf(false)
    /** "Add GachaMon to collection after defeating a trainer": "It defeats at least 2 trainers". On. */
    var addAfterTrainers by mutableStateOf(true)
    /** "Add to collection if prize from trainer victory": "Occasionally receive prize cards from Trainers". On. */
    var prizeCards by mutableStateOf(true)
    /** "Show GachaMon stars on main Tracker Screen": "Display stars next to heals". On. */
    var showStars by mutableStateOf(true)
    /** "Show card pack on screen after capturing a GachaMon": "Show card pack opening before Pokemon stats". Off. */
    var showPack by mutableStateOf(false)
    /** "Animate GachaMon pack opening": "Animate card pack opening". On. */
    var animatePack by mutableStateOf(true)

    fun text(): String = "ruleset=$ruleset\naddIfNew=$addIfNew\naddAfterTrainers=$addAfterTrainers\nprizeCards=$prizeCards\n" +
        "showStars=$showStars\nshowPack=$showPack\nanimatePack=$animatePack\n"

    fun load(text: String) {
        text.lineSequence().forEach { line ->
            val cut = line.indexOf('=')
            if (cut <= 0) return@forEach
            val k = line.substring(0, cut).trim(); val v = line.substring(cut + 1).trim()
            when (k) {
                "ruleset" -> if (v == "AutoDetect" || v in GachaMonRulesets.KEYS) ruleset = v
                "addIfNew" -> addIfNew = v == "true"
                "addAfterTrainers" -> addAfterTrainers = v == "true"
                "prizeCards" -> prizeCards = v == "true"
                "showStars" -> showStars = v == "true"
                "showPack" -> showPack = v == "true"
                "animatePack" -> animatePack = v == "true"
            }
        }
    }

    /**
     * "Display stars next to heals" on: "Track PC Heals" goes off, as both use that space (GachaMonOverlay.lua:1535-1543).
     * Turning PC heals on turns the stars off the same way (SetupScreen.lua:484-487, in Tracker Setup).
     */
    fun chooseShowStars(on: Boolean) {
        showStars = on
        if (on && TrackerOptions.trackPcHeals) { TrackerOptions.trackPcHeals = false; TrackerOptions.save() }
        GachaMon.saveOptions()
    }
}

/** Constants.IronmonRulesetNames and GachaMonData.autoDetermineIronmonRuleset. */
object GachaMonRulesets {
    /** Key to name, in the edit window's order; the three Ascension rulesets are offered only on Nat. Dex. */
    val NAMES: List<Pair<String, String>> = listOf(
        "Standard" to "Standard", "Ultimate" to "Ultimate", "Kaizo" to "Kaizo", "Survival" to "Survival",
        "SurvivalRevival" to "Survival Revival", "SuperKaizo" to "Super Kaizo", "Subpar" to "Subpar",
        "Ascension1" to "Ascension 1", "Ascension2" to "Ascension 2", "Ascension3" to "Ascension 3",
    )
    val KEYS: Set<String> = NAMES.map { it.first }.toSet()
    fun name(key: String): String = NAMES.firstOrNull { it.first == key }?.second ?: "Standard"

    /**
     * autoDetermineIronmonRuleset: the first ruleset whose name, spaces and underscores dropped, is in the run's mode
     * (here the settings file's mode, RunModeName: "RSE Super Kaizo"), Super Kaizo before Kaizo and Kaizo last; the
     * Ascension rulesets only on Nat. Dex; Standard when none is.
     */
    fun detect(mode: String, natDex: Boolean): String {
        fun bare(s: String) = s.replace(" ", "").replace("_", "")
        val ordered = listOf("Standard", "Ultimate", "SurvivalRevival", "Survival", "SuperKaizo", "Subpar") +
            (if (natDex) listOf("Ascension1", "Ascension2", "Ascension3") else emptyList()) + "Kaizo"
        val m = bare(mode)
        return ordered.firstOrNull { m.contains(bare(name(it)), ignoreCase = true) } ?: "Standard"
    }
}

/**
 * What GachaMon reads of the game in Play: the tracker's lookups (GbaTracker's, through [GbaGachaMonGame]). An
 * interface so the reads that make cards can be tested without a ROM.
 */
interface GachaMonGame {
    /** Nat. Dex numbering (CustomCode.RomHacks.isPlayingNatDex): Nat. Dex and MaxDex. */
    val expandedSpeciesIds: Boolean
    /** "maxdex" on MaxDex, whose numbering and move split are its own; "" otherwise. */
    val nameSet: String
    val lastMoveId: Int
    fun baseStats(species: Int): com.ironmonone.tracker.BaseStats?
    fun moveRowFor(id: Int): com.ironmonone.tracker.MoveRow?
    /** PokemonData's evolution, null for none. */
    fun evolution(species: Int): String?
    fun speciesName(species: Int): String
    fun abilityName(id: Int): String
    fun moveName(id: Int): String
    /** The game's own map id for the tracker's (Ruby and Sapphire are numbered as Emerald). */
    fun rawMapId(mapId: Int): Int
    fun trainerParty(trainerId: Int): List<GachaMonPrize.TrainerMon>
    /** "Leader Brock": the trainer's class and name; null when unreadable. */
    fun trainerTitle(trainerId: Int): String?
    fun trainerDefeated(trainerId: Int): Boolean
    fun learnset(species: Int): List<Pair<Int, Int>>
    fun speciesExists(species: Int): Boolean
    fun routeNameOfTrainer(trainerId: Int): String?
}

/** The GBA tracker as GachaMon reads it. */
class GbaGachaMonGame(val tracker: GbaTracker) : GachaMonGame {
    override val expandedSpeciesIds get() = tracker.expandedSpeciesIds
    override val nameSet get() = tracker.nameSet
    override val lastMoveId get() = tracker.lastMoveId
    override fun baseStats(species: Int) = tracker.baseStats(species)
    override fun moveRowFor(id: Int) = tracker.moveRowFor(id)
    override fun evolution(species: Int) = tracker.evolution(species)
    override fun speciesName(species: Int) = tracker.speciesName(species)
    override fun abilityName(id: Int) = tracker.abilityName(id)
    override fun moveName(id: Int) = tracker.moveName(id)
    override fun rawMapId(mapId: Int) = tracker.rawMapId(mapId)
    override fun trainerParty(trainerId: Int) = tracker.trainer(trainerId)?.party.orEmpty()
        .map { GachaMonPrize.TrainerMon(it.species, it.level, it.ivs, it.moves) }
    override fun trainerTitle(trainerId: Int) = tracker.trainer(trainerId)?.let { t ->
        listOf(t.className, t.name).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
    }
    override fun trainerDefeated(trainerId: Int) = tracker.trainerDefeated(trainerId)
    override fun learnset(species: Int) = tracker.learnset(species)
    override fun speciesExists(species: Int) = tracker.speciesExists(species)
    override fun routeNameOfTrainer(trainerId: Int) = tracker.routeNameOfTrainer(trainerId)
}

/**
 * GachaMon in Play (GachaMonData and GachaMonFileManager): the collection, this run's captures (RecentMons), the
 * GachaDex, the newest card, and the reads that make cards. Files under prep/gachamon/: collection.txt, recent.txt
 * (its first line names the run), dex.txt and options.txt, each a share code a line with KaizoCore's notes after it.
 *
 * Cards are made in a Kaizo IronMON run on a Game Boy Advance game, the PC tracker's own setting: the lead Pokemon,
 * each time a new one leads outside a battle (Program.lua:881-885), a new card for each species and personality.
 */
object GachaMon {
    const val DIR = "prep/gachamon"
    /** GachaMonData.TRAINERS_TO_DEFEAT. */
    const val TRAINERS_TO_DEFEAT = 2

    private var uidCounter = 0L
    @Synchronized fun nextUid(): Long = ++uidCounter

    /** The player's whole collection (GachaMonData.Collection), in the file's order. */
    val collection = mutableStateListOf<GachaMonEntry>()
    /** This run's captures (GachaMonData.RecentMons), oldest first. */
    val recent = mutableStateListOf<GachaMonEntry>()
    /** A card just made and not looked at yet (newestRecentMon); cleared when a battle starts or its pack is opened. */
    var newest by mutableStateOf<GachaMonEntry?>(null)
        private set
    /** The prize card made at this run's game over (createdTrainerPrizeCard). */
    var prizeCard by mutableStateOf<GachaMonEntry?>(null)
        private set
    /** Bumped when the GachaDex's seen list changes. */
    var dexVersion by mutableIntStateOf(0)
        private set
    /** A card pack is on screen: a battle starting does not take its card away (Battle.lua:794-798). */
    var packShowing = false
    /** The cards' own shiny chance and the prize card's draws (math.random); a test sets its own. */
    internal var random: kotlin.random.Random = kotlin.random.Random.Default

    private var dir: File? = null
    private var runId: String = ""
    private var attempt = 0
    /** Seen species by numbering ("" or "maxdex"): DexData.SeenMons. */
    private val seen = HashMap<String, MutableSet<Int>>()
    /** checkIfNewCollectionSpecies: cards found new once stay new (Temp.IsNewCollectionSpecies). */
    private val foundNew = HashSet<Long>()

    // What the last read of a run showed, for the changes the reference reacts to.
    private var wasInBattle = false
    private var battleWasWild = false
    private var lastBadges = -1
    private var winMarked = false
    private var gameRef: GachaMonGame? = null
    /** The tracker the adapter in [gameRef] reads, weakly: a new tracker gets a new adapter. */
    private var gameOf: java.lang.ref.WeakReference<GbaTracker>? = null
    /** The run's game and its last read, while a Kaizo IronMON run on a GBA game is in Play. */
    val game: GachaMonGame? get() = gameRef
    var liveState: TrackerState? = null
        private set
    private var ironmonFor: Pair<String, Boolean>? = null

    // ---------------------------------------------------------------- files

    private fun file(name: String): File? = dir?.let { File(it, name) }

    /** Reads the files once per process; the GachaMon screen and the reads both start here. */
    @Synchronized
    fun ensureLoaded(filesDir: File) {
        if (dir != null) return
        val d = File(filesDir, DIR).apply { mkdirs() }
        dir = d
        collection.clear(); recent.clear(); seen.clear()
        DiskWriter.read(File(d, "options.txt"))?.let { GachaMonOptions.load(it) }
        DiskWriter.read(File(d, "collection.txt"))?.lineSequence()?.forEach { l -> if (!l.startsWith("#")) GachaMonEntry.parse(l)?.let { collection += it } }
        val recentLines = DiskWriter.read(File(d, "recent.txt"))?.lines().orEmpty()
        runId = recentLines.firstOrNull()?.takeIf { it.startsWith("run ") }?.removePrefix("run ")?.trim().orEmpty()
        recentLines.drop(1).forEach { l -> if (!l.startsWith("#")) GachaMonEntry.parse(l)?.let { recent += it } }
        DiskWriter.read(File(d, "dex.txt"))?.lineSequence()?.forEach { l ->
            val cut = l.indexOf('=')
            if (l.startsWith("seen") && cut > 0) {
                val key = l.substring(4, cut).removePrefix(".")
                seen.getOrPut(key) { HashSet() } += l.substring(cut + 1).split(',').mapNotNull { it.trim().toIntOrNull() }
            }
        }
    }

    /** For tests and a restore: forget what was read, so the next use reads the files again. */
    @Synchronized
    fun reset() {
        dir = null; runId = ""; attempt = 0
        collection.clear(); recent.clear(); seen.clear(); foundNew.clear()
        newest = null; prizeCard = null; packShowing = false
        wasInBattle = false; battleWasWild = false; lastBadges = -1; winMarked = false
        gameRef = null; gameOf = null; liveState = null; ironmonFor = null; random = kotlin.random.Random.Default
    }

    fun saveCollection() {
        val f = file("collection.txt") ?: return
        DiskWriter.write(f, "# GachaMon collection: a card a line, the PC tracker's share code first\n" + collection.joinToString("") { it.line() + "\n" })
    }

    fun saveRecent() {
        val f = file("recent.txt") ?: return
        DiskWriter.write(f, "run $runId\n" + recent.joinToString("") { it.line() + "\n" })
    }

    fun saveDex() {
        val f = file("dex.txt") ?: return
        DiskWriter.write(f, seen.entries.joinToString("") { (k, ids) -> "seen${if (k.isEmpty()) "" else ".$k"}=" + ids.sorted().joinToString(",") + "\n" })
    }

    fun saveOptions() {
        val f = file("options.txt") ?: return
        DiskWriter.write(f, GachaMonOptions.text())
    }

    // ---------------------------------------------------------------- the collection

    /** checkIfNewCollectionSpecies: no card of its species in the collection, nor kept among this run's captures. */
    fun isNewSpecies(e: GachaMonEntry): Boolean {
        if (e.uid in foundNew) return true
        val sp = e.card.pokemonId
        if (collection.any { it.card.pokemonId == sp }) return false
        if (recent.any { it.card.pokemonId == sp && it.card.keep == 1 }) return false
        foundNew += e.uid
        return true
    }

    fun isRecent(e: GachaMonEntry): Boolean = recent.any { it.uid == e.uid }

    /** The card as it is now (after a favorite or a keep), by its uid. */
    fun current(e: GachaMonEntry): GachaMonEntry = recent.firstOrNull { it.uid == e.uid } ?: collection.firstOrNull { it.uid == e.uid } ?: e

    /**
     * updateGachaMonAndSave: the favorite, keep and game-winner marks are all a card can change. A capture is saved to
     * this run's file; a collection card to the collection.
     */
    fun update(e: GachaMonEntry, favorite: Boolean? = null, keep: Boolean? = null, winner: Boolean? = null): GachaMonEntry {
        val now = current(e)
        var c = now.card
        favorite?.let { c = c.copy(favorite = if (it) 1 else 0) }
        keep?.let { c = c.copy(keep = if (it) 1 else 0) }
        winner?.let { c = c.copy(gameWinner = if (it) 1 else 0) }
        if (c == now.card) return now
        val next = now.copy(card = c)
        val ri = recent.indexOfFirst { it.uid == e.uid }
        if (ri >= 0) { recent[ri] = next; saveRecent() } else {
            val ci = collection.indexOfFirst { it.uid == e.uid }
            if (ci >= 0) { collection[ci] = next; saveCollection() }
        }
        if (newest?.uid == e.uid) newest = next
        if (prizeCard?.uid == e.uid) prizeCard = next
        return next
    }

    /** tryRemoveFromCollection: a capture is only unmarked (and unfavored); a collection card goes, once confirmed. */
    fun removeFromCollection(e: GachaMonEntry) {
        if (isRecent(e)) { update(e, favorite = false, keep = false); return }
        val i = collection.indexOfFirst { it.uid == e.uid }
        if (i >= 0) { collection.removeAt(i); saveCollection() }
    }

    /** openCleanupCollectionWindow's removal: every collection card [matches] says, never a favorite. */
    fun removeAll(matches: (GachaMonEntry) -> Boolean): Int {
        val before = collection.size
        collection.removeAll { it.card.favorite != 1 && matches(it) }
        val removed = before - collection.size
        if (removed > 0) saveCollection()
        return removed
    }

    /** A card from a share code, into the collection (the PC tracker's codes too). It is not a favorite. */
    fun import(card: GachaMonCard): GachaMonEntry {
        val e = GachaMonEntry(card.copy(favorite = 0, keep = 1))
        collection += e
        saveCollection()
        markSeen(e)
        return e
    }

    /**
     * The PC tracker's whole collection file (FullCollection.gccg) into this one (GachaMonImport): new cards added with
     * their favorite marks, a card already here skipped (and marked a favorite if the file says so), unreadable records
     * counted. Returns the line that says what happened.
     */
    fun importCollection(bytes: ByteArray): String {
        val r = com.ironmonone.tracker.gachamon.GachaMonImport.merge(collection.map { it.card }, bytes)
        r.favorited.forEach { held ->
            val i = collection.indexOfFirst { it.card == held }
            if (i >= 0) collection[i] = collection[i].copy(card = held.copy(favorite = 1))
        }
        r.added.forEach { c -> GachaMonEntry(c).also { collection += it; markSeen(it) } }
        if (r.added.isNotEmpty() || r.favorited.isNotEmpty()) saveCollection()
        return r.line + "."
    }

    fun seen(dex: String): Set<Int> = seen[dex].orEmpty()

    private fun markSeen(e: GachaMonEntry) {
        val set = seen.getOrPut(e.notes.dex) { HashSet() }
        if (set.add(e.card.pokemonId)) { saveDex(); dexVersion++ }
    }

    /** The ruleset the ratings use now: the option, or the run's, detected from its settings file's mode. */
    fun rulesetKey(filesDir: File, natDex: Boolean): String {
        if (GachaMonOptions.ruleset != "AutoDetect") return GachaMonOptions.ruleset
        return detectedRuleset(filesDir, natDex)
    }

    /** The ruleset the run's settings file names (autoDetermineIronmonRuleset), whatever the option says. */
    fun detectedRuleset(filesDir: File, natDex: Boolean): String = GachaMonRulesets.detect(runMode(filesDir), natDex)

    private var modeCache: Pair<Long, String>? = null
    private fun runMode(filesDir: File): String {
        val f = File(File(filesDir, "prep"), "lastrun.txt")
        val stamp = f.lastModified()
        modeCache?.takeIf { it.first == stamp }?.let { return it.second }
        val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
        val name = lines.getOrNull(1).orEmpty()
        val mode = if (name.isBlank()) "" else runCatching { RunModeName.ofPrep(f.parentFile, lines.getOrNull(0), name) }.getOrDefault(name)
        modeCache = stamp to mode
        return mode
    }

    // ---------------------------------------------------------------- reads

    /**
     * One read of the game in Play, from Play's poll (one line there): the run's captures follow the run, then a new
     * lead outside a battle becomes a card, a trainer battle won counts towards keeping the lead's card, a badge or the
     * final win marks the party's cards.
     */
    internal fun observe(filesDir: File, session: GameSession, state: TrackerState?, tracker: GbaTracker?, run: RunIds) {
        if (Demo.mode != null || !session.isRun || session.kind?.platform != com.ironmonone.core.Platform.GBA) return
        if (tracker == null || state == null || state.unreadable) return
        ensureLoaded(filesDir)
        val id = "${session.kind.id} ${run.attempt} ${run.seed}"
        // Kaizo IronMON runs: a Nuzlocke ends and counts by its own rules (PlayRules). Read once per run.
        val ironmon = ironmonFor?.takeIf { it.first == id }?.second
            ?: (runCatching { PlayRules.kind(session, filesDir) == PlayRules.Kind.IRONMON }.getOrDefault(false)).also { ironmonFor = id to it }
        if (!ironmon) return
        val game = gameRef?.takeIf { gameOf?.get() === tracker } ?: GbaGachaMonGame(tracker).also { gameOf = java.lang.ref.WeakReference(tracker) }
        observeGame(filesDir, id, run.attempt, state, game)
    }

    /** [observe] past the session's checks: one read of run [runId], attempt [attempt], of [game]. Tests start here. */
    internal fun observeGame(filesDir: File, runId: String, attempt: Int, state: TrackerState, game: GachaMonGame) {
        ensureLoaded(filesDir)
        gameRef = game
        liveState = state
        startRun(runId, attempt)
        if (state.partyCount == 0) return
        watchBattle(state)
        watchBadges(state, game)
        watchWin(state)
        if (!state.inBattle) state.party.firstOrNull()?.let { capture(it, game, state, filesDir) }
    }

    /**
     * importRecentMons on a new game: the last run's captures marked to keep go into the collection, the rest go, and
     * this run starts empty. The same run again (the app restarted) keeps its captures.
     */
    private fun startRun(id: String, attempt: Int) {
        this.attempt = attempt
        if (id == runId) return
        val kept = recent.filter { it.card.keep == 1 }
        if (kept.isNotEmpty()) { collection += kept; saveCollection() }
        recent.clear()
        runId = id
        saveRecent()
        newest = null; prizeCard = null
        wasInBattle = false; lastBadges = -1; winMarked = false
        foundNew.clear()
    }

    private fun watchBattle(state: TrackerState) {
        if (state.inBattle && !wasInBattle) {
            battleWasWild = state.isWildBattle
            // Battle.beginNewBattle: the newest card is let go, unless its pack is on screen
            if (!packShowing) newest = null
        } else if (!state.inBattle && wasInBattle && !battleWasWild && GachaMonOptions.addAfterTrainers) {
            // Battle.endCurrentBattle: a trainer beaten by a lead still standing counts towards keeping its card
            state.party.firstOrNull()?.takeIf { it.mon.curHp > 0 }?.let { autoKeep(it) }
        }
        wasInBattle = state.inBattle
    }

    /** tryAutoKeepInCollection: the second trainer its Pokemon beats keeps the card. */
    private fun autoKeep(p: TrackedMon) {
        val key = p.mon.pid + p.mon.species
        val i = recent.indexOfFirst { it.card.pidIndex == key }
        if (i < 0 || recent[i].card.keep == 1) return
        val e = recent[i]
        val wins = e.notes.trainers + 1
        val next = e.copy(notes = e.notes.copy(trainers = wins), card = if (wins >= TRAINERS_TO_DEFEAT) e.card.copy(keep = 1) else e.card)
        recent[i] = next
        if (newest?.uid == e.uid) newest = next
        saveRecent()
    }

    /** markTeamForGymBadgeObtained: a badge won on a gym's map goes on the party's cards. */
    private fun watchBadges(state: TrackerState, tracker: GachaMonGame) {
        val b = state.badges
        val last = lastBadges
        lastBadges = b
        if (last < 0 || b == last) return
        val changed = b xor last
        // updateBadgesObtained: the highest badge whose state changed
        val badge = (7 downTo 0).firstOrNull { (changed shr it) and 1 == 1 } ?: return
        val map = state.mapId ?: return
        val frlg = state.badgeSet == "FRLG"
        val canObtain = if (frlg) map in FRLG_BADGE_MAPS else tracker.rawMapId(map) in RSE_BADGE_MAPS
        if (!canObtain) return
        markParty(state) { it.copy(badges = it.badges or (1 shl badge)) }
    }

    /** markTeamForGameWin: the party that wins the game is marked on its cards. */
    private fun watchWin(state: TrackerState) {
        if (winMarked || state.gameOver != com.ironmonone.tracker.GameOver.WON) return
        winMarked = true
        markParty(state) { it.copy(gameWinner = 1) }
    }

    private fun markParty(state: TrackerState, change: (GachaMonCard) -> GachaMonCard) {
        var any = false
        for (p in state.party) {
            val key = p.mon.pid + p.mon.species
            val i = recent.indexOfFirst { it.card.pidIndex == key }
            if (i < 0) continue
            val next = recent[i].copy(card = change(recent[i].card))
            if (next.card != recent[i].card) { recent[i] = next; any = true }
        }
        if (any) saveRecent()
    }

    /** tryAddToRecentMons for the lead: a card the first time this Pokemon, as this species, leads. */
    private fun capture(p: TrackedMon, tracker: GachaMonGame, state: TrackerState, filesDir: File) {
        val m = p.mon
        if (m.isEgg || m.species <= 0 || m.pid == 0L) return
        val key = m.pid + m.species
        if (recent.any { it.card.pidIndex == key }) return
        val e = runCatching { make(p, tracker, state, filesDir) }.getOrNull() ?: return
        add(e, fromPrize = false)
    }

    /** The rest of tryAddToRecentMons: kept if it is a new species (option), or a prize (option); saved; seen. */
    private fun add(made: GachaMonEntry, fromPrize: Boolean): GachaMonEntry {
        recent += made
        newest = made
        var e = made
        if (GachaMonOptions.addIfNew && isNewSpecies(e)) e = e.copy(card = e.card.copy(keep = 1))
        else if (fromPrize && GachaMonOptions.prizeCards) e = e.copy(card = e.card.copy(keep = 1))
        if (e != made) { recent[recent.lastIndex] = e; newest = e }
        saveRecent()
        markSeen(e)
        return e
    }

    /** convertPokemonToGachaMon for a party Pokemon, from the tracker's read of the game. */
    fun make(p: TrackedMon, tracker: GachaMonGame, state: TrackerState, filesDir: File, at: Long = System.currentTimeMillis()): GachaMonEntry? {
        val natDex = tracker.expandedSpeciesIds
        val mon = monOf(p, tracker, natDex) ?: return null
        val date = java.time.LocalDate.now()
        val card = GachaMonMaker.make(mon, natDex, GachaMonCard.gameVersionOf(state.routeVersion), attempt,
            date.year, date.monthValue, date.dayOfMonth, rulesetKey(filesDir, natDex), random = random)
        return GachaMonEntry(card, notesFor(mon, card, p.speciesName, tracker, at))
    }

    /** The current stars of a Pokemon (GachaMonData.updateMainScreenViewedGachaMon), for the tracker's heals box. */
    private var viewedCache: Pair<List<Any>, Int>? = null
    fun currentStars(p: TrackedMon, tracker: GachaMonGame, filesDir: File): Int? {
        val natDex = tracker.expandedSpeciesIds
        val ruleset = rulesetKey(filesDir, natDex)
        val m = p.mon
        val key = listOf(m.pid, m.species, m.level, m.moves, m.maxHp, m.atk, m.spAtk, ruleset)
        viewedCache?.takeIf { it.first == key }?.let { return it.second }
        val mon = monOf(p, tracker, natDex) ?: return null
        val rs = GachaMonRatingSystem.default
        val stars = rs.stars(rs.ratingScore(GachaMonMaker.ratingInput(mon, natDex), ruleset), GachaMonCodec.CURRENT_VERSION)
        viewedCache = key to stars
        return stars
    }

    /** This run's card for [p] (getAssociatedRecentMon), or null. */
    fun cardFor(p: TrackedMon): GachaMonEntry? {
        val key = p.mon.pid + p.mon.species
        return recent.firstOrNull { it.card.pidIndex == key }
    }

    /** The heals box's stars for the Pokemon on view: (now, as the card was made), or null when it has no card. */
    fun viewedStars(p: TrackedMon, filesDir: File): Pair<Int, Int>? {
        val e = cardFor(p) ?: return null
        val t = game ?: return e.stars to e.stars
        return (currentStars(p, t, filesDir) ?: e.stars) to e.stars
    }

    /**
     * The View tab's up and down arrows (RecalculateAsTemp): the card made again from the lead as it is now, when the
     * card is the lead's and its level has changed. Never saved; it keeps the card's own shine.
     */
    fun recalculated(e: GachaMonEntry, filesDir: File): GachaMonEntry? {
        val t = game ?: return null
        val s = liveState ?: return null
        val lead = s.party.firstOrNull() ?: return null
        if (lead.mon.pid != e.card.personality || lead.mon.species != e.card.pokemonId || lead.mon.level == e.card.level) return null
        val made = make(lead, t, s, filesDir) ?: return null
        return made.copy(card = made.card.copy(isShiny = e.card.isShiny, keep = e.card.keep, favorite = e.card.favorite,
            badges = e.card.badges, gameWinner = e.card.gameWinner))
    }

    /** The game's Pokemon as convertPokemonToGachaMon reads it. */
    private fun monOf(p: TrackedMon, tracker: GachaMonGame, natDex: Boolean): GachaMonMaker.Mon? {
        val m = p.mon
        val base = p.base ?: tracker.baseStats(m.species) ?: return null
        // PokemonData.getAbilityId: the ability in the Pokemon's slot, none where the species has no second
        val abilityId = if (m.abilitySlot == 1) base.ability2 else base.ability1
        val split = tracker.nameSet == "maxdex"
        val moves = m.moves.filter { it in 1..tracker.lastMoveId }.mapNotNull { id -> tracker.moveRowFor(id)?.let { factsOf(it, split) } }
        val types = if (base.type2 != base.type1) listOf(base.type1, base.type2) else listOf(base.type1)
        return GachaMonMaker.Mon(
            personality = m.pid, species = m.species, level = m.level, abilityId = abilityId,
            stats = SixStats(m.maxHp, m.atk, m.def, m.spAtk, m.spDef, m.spe),
            moves = moves, nature = m.nature,
            gender = when (com.ironmonone.tracker.Gender3.of(base.genderRatio, m.pid)) {
                com.ironmonone.tracker.Gender3.MALE -> 1; com.ironmonone.tracker.Gender3.FEMALE -> 2; else -> 0
            },
            shiny = m.shiny, types = types,
            baseStats = SixStats(base.hp, base.atk, base.def, base.spAtk, base.spDef, base.spe),
            listedBst = GachaMonBst.listed(m.species, natDex).takeIf { tracker.nameSet != "maxdex" } ?: base.bst,
            evolves = tracker.evolution(m.species) != null,
        )
    }

    /** One move as the reference's MoveData holds it, from the ROM's move table. */
    internal fun factsOf(row: com.ironmonone.tracker.MoveRow, split: Boolean): MoveFacts {
        val type = row.type ?: 9
        var cat = when (row.category) { "PHY" -> MoveCategory.PHYSICAL; "SPE" -> MoveCategory.SPECIAL; else -> MoveCategory.STATUS }
        // MoveData.TypeToCategory: the Mystery type is neither physical nor special (not on a build with the split)
        if (!split && type == 9 && cat != MoveCategory.STATUS) cat = MoveCategory.NONE
        return MoveFacts(row.id, type, GachaMonMoves.powerText(row.id, row.power ?: 0), row.acc ?: 0, cat)
    }

    private fun notesFor(mon: GachaMonMaker.Mon, card: GachaMonCard, name: String, tracker: GachaMonGame, at: Long): GachaMonNotes {
        val ids = List(4) { mon.moves.getOrNull(it)?.id ?: 0 }
        return GachaMonNotes(
            dex = if (tracker.nameSet == "maxdex") "maxdex" else "",
            name = name,
            ability = if (mon.abilityId > 0) tracker.abilityName(mon.abilityId) else "---",
            moves = mon.moves.map { tracker.moveName(it.id) },
            powers = mon.moves.map { it.power },
            moveIds = if (ids.any { it > GachaMonCodec.MAX_MOVE }) ids else emptyList(),
            abilityId = mon.abilityId.takeIf { it > GachaMonCodec.MAX_ABILITY } ?: 0,
            at = at,
        )
    }

    /** The newest card has been looked at: its pack opened and closed (clearNewestMonToShow). */
    fun newestSeen() { newest = null }

    // ---------------------------------------------------------------- the prize card

    /** The game's number for its common trainers (GameSettings.game): 1 Ruby/Sapphire, 2 Emerald, 3 FireRed/LeafGreen. */
    private fun gameNumber(state: TrackerState): Int = when (state.routeVersion) {
        "ruby", "sapphire" -> 1; "emerald" -> 2; "firered", "leafgreen" -> 3; else -> 0
    }

    /** GameOverScreen.numDefeatedTrainers: the common trainers beaten this run. */
    fun defeatedCommonTrainers(): Int {
        val t = game ?: return 0
        val s = liveState ?: return 0
        return GachaMonPrize.defeated(gameNumber(s), t::trainerDefeated).size
    }

    /** The game over screen's "Prize card" shows: the option is on and two common trainers are beaten. */
    fun prizeOffered(): Boolean = GachaMonOptions.prizeCards && game != null && defeatedCommonTrainers() >= 2

    /**
     * GameOverScreen's prize card: made once a run, from a beaten common trainer's strongest Pokemon, and kept when
     * the option is on. Null when it cannot be made.
     */
    fun makePrize(filesDir: File, random: kotlin.random.Random = this.random): GachaMonEntry? {
        prizeCard?.let { return it }
        val t = game ?: return null
        val s = liveState ?: return null
        val natDex = t.expandedSpeciesIds
        val source = object : GachaMonPrize.Source {
            override fun party(trainerId: Int) = t.trainerParty(trainerId)
            override fun listedBst(species: Int) = GachaMonBst.listed(species, natDex)?.takeIf { t.nameSet != "maxdex" } ?: t.baseStats(species)?.bst ?: 0
            override fun valid(species: Int) = t.speciesExists(species)
            override fun learnset(species: Int) = t.learnset(species)
            override fun baseStats(species: Int) = t.baseStats(species)?.let { SixStats(it.hp, it.atk, it.def, it.spAtk, it.spDef, it.spe) }
            override fun firstAbility(species: Int) = t.baseStats(species)?.ability1 ?: 0
        }
        val pick = GachaMonPrize.pick(GachaMonPrize.defeated(gameNumber(s), t::trainerDefeated), source, random) ?: return null
        // Its key is the trainer's id plus the species: the same prize twice is not made twice
        recent.firstOrNull { it.card.pidIndex == pick.trainerId.toLong() + pick.species }?.let { prizeCard = it; return it }
        val base = t.baseStats(pick.species) ?: return null
        val split = t.nameSet == "maxdex"
        val moves = pick.moves.filter { it in 1..t.lastMoveId }.mapNotNull { id -> t.moveRowFor(id)?.let { factsOf(it, split) } }
        val mon = GachaMonMaker.Mon(
            personality = pick.trainerId.toLong(), species = pick.species, level = pick.level, abilityId = pick.abilityId,
            stats = pick.stats, moves = moves, nature = pick.nature, gender = 0, shiny = false,
            types = if (base.type2 != base.type1) listOf(base.type1, base.type2) else listOf(base.type1),
            baseStats = SixStats(base.hp, base.atk, base.def, base.spAtk, base.spDef, base.spe),
            listedBst = source.listedBst(pick.species), evolves = t.evolution(pick.species) != null,
        )
        val date = java.time.LocalDate.now()
        val card = GachaMonMaker.make(mon, natDex, GachaMonCard.gameVersionOf(s.routeVersion), attempt,
            date.year, date.monthValue, date.dayOfMonth, rulesetKey(filesDir, natDex), random = random)
        val trainerName = t.trainerTitle(pick.trainerId) ?: "???"
        val notes = notesFor(mon, card, t.speciesName(pick.species), t, System.currentTimeMillis())
            .copy(trainer = trainerName, place = t.routeNameOfTrainer(pick.trainerId) ?: "???")
        val e = add(GachaMonEntry(card, notes), fromPrize = true)
        prizeCard = e
        return e
    }

    /** RouteData.Locations.CanObtainBadge for FireRed and LeafGreen (RouteData.lua:432-441). */
    private val FRLG_BADGE_MAPS = setOf(12, 15, 20, 25, 28, 34, 36, 37)
    /** The same for Ruby, Sapphire and Emerald, by the game's own map id (RouteData.lua:3214-3225). */
    private val RSE_BADGE_MAPS = setOf(65, 69, 70, 79, 89, 94, 100, 108, 109, 110)
}
