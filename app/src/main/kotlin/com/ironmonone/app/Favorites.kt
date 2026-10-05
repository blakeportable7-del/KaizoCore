package com.ironmonone.app

/**
 * The PC tracker's "Startup favorites" (StreamerScreen.lua: three Pokemon,
 * shown on the new-game screen; Options.lua defaults them to 1,4,7). Here
 * they are three names on the RUN screen, kept in PrepStore's favorites file
 * as a comma list, and shown as icons on the tracker's no-party card for the
 * generations whose reference tracker has the feature: Gen 1, 2 and 3; the DS
 * panel shows them beside its random ball row, as the NDS tracker keeps its
 * favorites frame beside the ball picker until the first Pokemon (FavoriteIcons). No PC
 * tracker says when a favorite is in a ball (read 2026-09-30); this one does
 * in a Kaizo IronMON run's lab since 2026-10-01, on Blake's word (FavoriteBall).
 */
object Favorites {
    const val SLOTS = 3

    /**
     * One of the tracker's species tables (tracker-gba resources) as it is written: id and name, in file order. [skip]
     * names rows that are no Pokemon.
     */
    private class SpeciesTable(resource: String, skip: Set<String> = emptySet()) {
        val rows: List<Pair<Int, String>> = ArrayList<Pair<Int, String>>().also { rows ->
            com.ironmonone.tracker.GbaTracker::class.java.getResourceAsStream(resource)
                ?.bufferedReader()?.useLines { lines ->
                    for (line in lines) {
                        val tab = line.indexOf('\t'); if (tab < 0) continue
                        val id = line.substring(0, tab).toIntOrNull() ?: continue
                        val name = line.substring(tab + 1).trim()
                        if (name.lowercase() !in skip) rows += id to name
                    }
                }
        }

        /** Name -> id (the Gen 3 build's internal ids past 251, which is what the sprite packs are keyed by). */
        val idByName: Map<String, Int> = rows.associate { (id, name) -> name.lowercase() to id }

        /** Every name in dex order, spelled as the table spells it; a name the table writes in lower case gets capitals. */
        val namesInOrder: List<Pair<Int, String>> by lazy {
            val spelled = rows.toMap()
            idByName.entries.sortedBy { it.value }.map { e ->
                e.value to (spelled[e.value]?.takeIf { it != it.lowercase() } ?: e.key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
            }
        }

        val spelledById: Map<Int, String> by lazy { namesInOrder.toMap() }
    }

    /**
     * The tracker's own species table, the Nat. Dex build's: every game but MaxDex names its favorites from it. Its 25
     * "none" rows, the Gen 3 ids 252 to 276, are no Pokemon either: typing "No" offered "None" (found 2026-10-03).
     */
    private val natDexTable by lazy { SpeciesTable("/natdex/species.tsv", skip = setOf("none")) }

    /**
     * MaxDex 1.0's own table (maxdex/species.tsv, GbaTracker.nameSet "maxdex"): its ids are Nat. Dex 1.2.1's up to
     * 1235 and part ways after. The 45 Legends Z-A Megas sit at 1236 to 1280 in another order (Dragonite-M is 1236
     * here and 1241 in Nat. Dex 1.2.1), and Greninja-B, Squawkabilly-W and Meowstic-F-M are not in it. Read from the
     * Nat. Dex table, a MaxDex favorite of those names drew another Pokemon's picture and matched another Pokemon's
     * ball (found 2026-10-03). Its 25 "none" rows, the Gen 3 ids 252 to 276, are no Pokemon to offer.
     */
    private val maxDexTable by lazy { SpeciesTable("/maxdex/species.tsv", skip = setOf("none")) }

    private fun tableOf(maxDex: Boolean) = if (maxDex) maxDexTable else natDexTable

    /** Name -> id from the Nat. Dex table, the one every game but MaxDex uses (see [idOf] with a game). */
    val idByName: Map<String, Int> get() = natDexTable.idByName

    fun idOf(name: String): Int? = idByName[name.trim().lowercase()]

    /** The id [name] has in the game [kind]: MaxDex's own numbering on MaxDex 1.0, the Nat. Dex table's otherwise. */
    fun idOf(name: String, kind: com.ironmonone.core.RomKind?): Int? = idOf(name, maxDex = kind?.isMaxDex == true)

    fun idOf(name: String, maxDex: Boolean): Int? = tableOf(maxDex).idByName[name.trim().lowercase()]

    /**
     * Every known name in dex order, spelled for display as the table spells it (Bulbasaur, Mr. Mime, Ho-Oh, and the
     * Nat. Dex forms the Play as your Pokemon list shows: Charizard-X, Rotom-Heat); a name the table writes in lower
     * case gets capitals.
     */
    val namesInOrder: List<Pair<Int, String>> get() = natDexTable.namesInOrder

    /** The same for the game [kind]: MaxDex's own names and ids on MaxDex 1.0. */
    fun namesInOrder(kind: com.ironmonone.core.RomKind?): List<Pair<Int, String>> = tableOf(kind?.isMaxDex == true).namesInOrder

    /** How the game [kind] spells the species [id], or null when it has no such id. */
    fun nameOf(id: Int, kind: com.ironmonone.core.RomKind?): String? = tableOf(kind?.isMaxDex == true).spelledById[id]

    /**
     * Blake, 2026-09-07: "as you type the first letter, the name of the pokemon
     * comes up, and the list narrows as you type more." Names that START with
     * what was typed, in dex order, at most [limit]; nothing for an empty box
     * or an exact match already typed. [kind]: the game's own names (MaxDex's
     * on MaxDex 1.0), the Nat. Dex table's when none is given.
     */
    fun suggest(typed: String, limit: Int = 8, maxId: Int = Int.MAX_VALUE, kind: com.ironmonone.core.RomKind? = null): List<String> {
        val q = typed.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val hits = namesInOrder(kind).filter { (id, n) -> fits(id, maxId) && n.lowercase().startsWith(q) }.map { it.second }
        if (hits.size == 1 && hits[0].lowercase() == q) return emptyList()
        return hits.take(limit)
    }

    /**
     * How many favorites the game's PC tracker keeps (read 2026-09-07). The
     * Gen 1, 2 and 3 trackers have three (StreamerScreen's PokemonFavorite1..3).
     * The DS tracker's TitleScreen reads `for i = 1, GEN` from its .faves file
     * and rotates them: four on a Gen 4 game, five on a Gen 5 game.
     */
    fun slotCount(kind: com.ironmonone.core.RomKind?): Int = when {
        // Every Nat. Dex rulebook: "You may have up to 9 favourites" (Blake, 2026-10-01: the game's own rules).
        kind?.isNatDex == true -> NAT_DEX_SLOTS
        kind?.generation == com.ironmonone.core.Generation.NDS5 -> 5
        kind?.generation == com.ironmonone.core.Generation.NDS4 -> 4
        else -> SLOTS
    }

    const val NAT_DEX_SLOTS = 9

    /**
     * The last dex number the game knows, for the suggestion list: 151, 251,
     * 386, 493, 649 by generation; a Nat. Dex build knows the whole table.
     */
    fun maxDex(kind: com.ironmonone.core.RomKind?): Int = when {
        kind == null -> Int.MAX_VALUE
        kind.isNatDex -> Int.MAX_VALUE
        else -> when (kind.generation) {
            com.ironmonone.core.Generation.GB1 -> 151
            com.ironmonone.core.Generation.GBC2 -> 251
            com.ironmonone.core.Generation.GBA3 -> 386
            com.ironmonone.core.Generation.NDS4 -> 493
            com.ironmonone.core.Generation.NDS5 -> 649
        }
    }

    /**
     * The National Dex number of a species in the table, which is what [maxDex] counts in (Bulbasaur 1 to Genesect
     * 649, Pecharunt 1025). The table numbers Hoenn in the Gen 3 build's internal order (277 Treecko to 411 Chimecho,
     * after 25 empty ids) and from 412 runs in national order 25 places on, its forms after 1050. So its own id past
     * 251 is not a dex number: compared with the caps, it marked 25 species of every generation as not in the game
     * (Ralts, Salamence, Rayquaza on an Emerald; Yanmega to Arceus on a Platinum; Bisharp to Genesect on a Black 2),
     * found 2026-09-30. Null for a form, which only a Nat. Dex build, with no cap, has.
     */
    fun nationalOf(id: Int): Int? = when (id) {
        in 1..251 -> id
        in 277..411 -> hoennNational[id]
        in 412..1050 -> id - 25
        else -> null
    }

    /**
     * The reverse of [nationalOf]: the table's id for National Dex number [national] (Treecko 252 is 277, Turtwig 387 is
     * 412, Pecharunt 1025 is 1050). Hoenn goes through the same name match. Null outside 1-1025. Walking Pals turns a DS
     * game's national numbers into the Gen 3 set's keys with it (WalkingPals.Index).
     */
    fun fromNational(national: Int): Int? = when (national) {
        in 1..251 -> national
        in 252..386 -> hoennInternal[national]
        in 387..1025 -> national + 25
        else -> null
    }

    /** Whether [name] is a Pokemon that a game whose dex ends at [maxDex] has: any name in the table with no cap. */
    fun inGame(name: String, maxDex: Int): Boolean = idOf(name)?.let { fits(it, maxDex) } == true

    /** The same, by the game [kind]'s own table: on MaxDex 1.0 a name MaxDex has (no Greninja-B, no Squawkabilly-W). */
    fun inGame(name: String, maxDex: Int, kind: com.ironmonone.core.RomKind?): Boolean =
        idOf(name, kind)?.let { fits(it, maxDex) } == true

    private fun fits(id: Int, maxDex: Int): Boolean = maxDex == Int.MAX_VALUE || nationalOf(id)?.let { it <= maxDex } == true

    /** Hoenn's internal ids (277-411) to their national numbers (252-386), by name, from the DS tracker's national list. */
    private val hoennNational: Map<Int, Int> by lazy {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val national = HashMap<String, Int>()
        com.ironmonone.tracker.nds.NdsLogData::class.java.getResourceAsStream("/nds/evo-methods.tsv")
            ?.bufferedReader()?.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#")) continue
                    val c = line.split('\t')
                    val n = c.getOrNull(0)?.toIntOrNull() ?: continue
                    if (n in 252..386) c.getOrNull(1)?.let { national[key(it)] = n }
                }
            }
        idByName.entries.filter { it.value in 277..411 }.mapNotNull { e -> national[key(e.key)]?.let { e.value to it } }.toMap()
    }

    /** The same pairs the other way: Hoenn's national numbers (252-386) to their internal ids (277-411). */
    private val hoennInternal: Map<Int, Int> by lazy { hoennNational.entries.associate { (id, n) -> n to id } }

    fun slots(text: String, count: Int = SLOTS): List<String> {
        val typed = text.split(',', '\n').map { it.trim() }
        return List(count) { typed.getOrElse(it) { "" } }
    }
    fun slots(store: PrepStore, romId: String?, count: Int = SLOTS): List<String> = slots(store.favoritesText(romId), count)

    fun text(slots: List<String>): String = slots.joinToString(",") { it.trim() }
    fun save(store: PrepStore, romId: String?, slots: List<String>) {
        store.saveFavorites(romId, text(slots))
        edits.intValue++
    }

    /**
     * Counts every [save]. The tracker's favorites row and its ball line read it (FavoritesShown), so an edit made in
     * Tracker Setup during a run (FavoritesEditor) shows there at once. The stream's pictures read the saved file on each
     * look anyway (StreamFavoritesSource).
     */
    val edits = androidx.compose.runtime.mutableIntStateOf(0)

    /** "FAVORITES: SCYTHER / GENGAR", or null when none are set. */
    fun line(names: Collection<String>): String? =
        if (names.isEmpty()) null else "FAVORITES: " + names.joinToString(" / ") { it.uppercase() }
}
