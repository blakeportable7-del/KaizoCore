package com.ironmonone.app.engine

import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import java.io.File

/**
 * What the builder (BuildYourGame) needs to know about one game, read from the
 * game's own ROM by the same randomizer engine that will randomize it
 * (2026-09-29): the species the game's dex has, the starters it ships with, and
 * whether it has abilities and move tutors.
 *
 * Read from the ROM and not typed in a table because the two engines disagree
 * about what "the dex" is: ZX lists 151, 251, 386, 493 or 649 by generation, and
 * the Nat. Dex fork lists 1258, its alternate forms (-M, -S) after the 1025
 * species. The number of each species here is the one the engine's own starter
 * dropdown uses, so a pick made from this list is the Pokemon the engine puts in
 * the ROM. Loading a handler takes a fraction of a second on GBA and DS dumps and
 * changes no ROM: nothing is randomized and nothing is written.
 */
object GameFacts {

    /** One entry of the game's dex: [number] is the engine's own index, the one a custom starter is stored as. */
    data class Species(val number: Int, val name: String)

    class Facts(
        /** Every species in dex order, numbers 1 to N with no gaps. */
        val species: List<Species>,
        /** The game's own starters as species numbers, one per starter slot (two in Yellow, three elsewhere). */
        val ownStarters: List<Int>,
        val hasAbilities: Boolean,
        val hasMoveTutors: Boolean,
    ) {
        val starterCount: Int get() = ownStarters.size

        /**
         * The last number in the list, which the engine cannot take as a custom
         * starter: Settings.tweakForRom rejects a stored value of size or more and
         * a starter is stored as its number plus one, so number N (Mew, Celebi,
         * Deoxys, Arceus, Genesect) reads as out of range and is swapped for the
         * game's own starter. Measured on a real FireRed, 2026-09-29.
         */
        val lastNumber: Int get() = species.lastOrNull()?.number ?: 0

        fun byNumber(number: Int): Species? = species.getOrNull(number - 1)?.takeIf { it.number == number }
    }

    private val cache = LinkedHashMap<String, Facts>()

    /**
     * The facts of [kind] as [rom] holds them. Blocking, so never on the main thread. Kept for
     * the next call on the same file, since RUN opens the builder again and again.
     */
    fun read(kind: RomKind, rom: File): Facts {
        val key = "${kind.id}|${rom.absolutePath}|${rom.length()}|${rom.lastModified()}"
        synchronized(cache) { cache[key]?.let { return it } }
        val facts = when (kind.engine) {
            com.ironmonone.core.Engine.NATDEX -> readNatDex(rom)
            com.ironmonone.core.Engine.MAXDEX -> readMaxDex(rom)
            com.ironmonone.core.Engine.ZX -> readZx(kind, rom)
            // Build your own reads and writes the UPR settings formats for UPR's own games; Heart & Soul is not offered it yet.
            com.ironmonone.core.Engine.HNS -> throw IllegalArgumentException("Build your own is not offered for Heart & Soul yet.")
        }
        synchronized(cache) {
            cache[key] = facts
            while (cache.size > 4) cache.remove(cache.keys.first())
        }
        return facts
    }

    private fun readZx(kind: RomKind, rom: File): Facts {
        // The handler follows the game's generation, as ZxEngine.randomize does.
        val factory: com.dabomstew.pkrandomzx.romhandlers.RomHandler.Factory = when (kind.generation) {
            Generation.NDS5 -> com.dabomstew.pkrandomzx.romhandlers.Gen5RomHandler.Factory()
            Generation.NDS4 -> com.dabomstew.pkrandomzx.romhandlers.Gen4RomHandler.Factory()
            Generation.GBC2 -> com.dabomstew.pkrandomzx.romhandlers.Gen2RomHandler.Factory()
            Generation.GB1 -> com.dabomstew.pkrandomzx.romhandlers.Gen1RomHandler.Factory()
            Generation.GBA3 -> com.dabomstew.pkrandomzx.romhandlers.Gen3RomHandler.Factory()
        }
        require(factory.isLoadable(rom.absolutePath)) { "\"${rom.name}\" is not a Pokémon ROM this engine can open." }
        val h = factory.create(com.dabomstew.pkrandomzx.RandomSource.instance())
        h.loadRom(rom.absolutePath)
        // The list the desktop's own starter dropdown offers (NewRandomizerGUI.populateDropdowns): the base
        // species up to Gen 5, where a Platinum list of 505 has a dozen alternate formes after Arceus. The
        // engine swaps a stored value past the base list (Settings.tweakForRom), so a forme is not a pick.
        val list = if (h.generationOfPokemon() >= 6) h.pokemonInclFormes.filter { it == null || !it.actuallyCosmetic } else h.pokemon
        return Facts(
            species = named(list.map { it?.fullName() }),
            ownStarters = h.starters.map { list.indexOf(it) },
            hasAbilities = h.abilitiesPerPokemon() > 0,
            hasMoveTutors = h.hasMoveTutors(),
        )
    }

    private fun readNatDex(rom: File): Facts {
        // The Nat. Dex fork is Gen 3 only (NatDexEngine).
        val factory = com.dabomstew.pkrandom.romhandlers.Gen3RomHandler.Factory()
        require(factory.isLoadable(rom.absolutePath)) { "\"${rom.name}\" is not a GBA Pokémon ROM this engine can open." }
        val h = factory.create(com.dabomstew.pkrandom.RandomSource.instance())
        h.loadRom(rom.absolutePath)
        val list = if (h.generationOfPokemon() >= 6) h.pokemonInclFormes.filter { it == null || !it.actuallyCosmetic } else h.pokemon
        return Facts(
            species = named(list.map { it?.fullName() }),
            ownStarters = h.starters.map { list.indexOf(it) },
            hasAbilities = h.abilitiesPerPokemon() > 0,
            hasMoveTutors = h.hasMoveTutors(),
        )
    }

    /**
     * The same for MaxDex, read by its own engine. Build your own is not offered on MaxDex in its first version; this
     * is here so every engine answers, and reads the ROM the way the MaxDex randomizer will.
     */
    private fun readMaxDex(rom: File): Facts {
        MaxDexEngine.refusal(rom)?.let { throw IllegalArgumentException(it) }
        val factory = com.dabomstew.pkrandommd.romhandlers.Gen3RomHandler.Factory()
        val h = factory.create(com.dabomstew.pkrandommd.RandomSource.instance())
        h.loadRom(rom.absolutePath)
        val list = h.pokemon
        return Facts(
            species = named(list.map { it?.fullName() }),
            ownStarters = h.starters.map { list.indexOf(it) },
            hasAbilities = h.abilitiesPerPokemon() > 0,
            hasMoveTutors = h.hasMoveTutors(),
        )
    }

    /** Entry 0 is the engine's "Random" slot (null); the rest are the species, numbered by position. */
    private fun named(names: List<String?>): List<Species> =
        names.drop(1).mapIndexed { i, n -> Species(i + 1, n.orEmpty()) }
}
