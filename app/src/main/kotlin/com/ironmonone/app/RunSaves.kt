package com.ironmonone.app

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import java.io.File

/**
 * What a new seed does with the run's in-game save: nothing. It stays, in every game (Blake, 2026-09-30).
 *
 * A run's battery save is one file per game, not per seed (SessionPaths.sram; a DS run's melonDS writes
 * current.sav beside current.nds), so a new seed's title screen offers Continue on the last run's save: save in front
 * of the starters once and every later attempt skips the intro, and New Game on that same title screen is the clean
 * start. rc32's first builds set the save aside instead unless it held no Pokémon (UX audit P0-2, IronMON rules check
 * R5), and a Nat. Dex save, whose party they did not read, went every time. Blake: "Should be able to open a save on a
 * new seed for all games", and "If the player wants a clean start, they just select new game from the in game menu".
 *
 * So nothing here moves a save, but one: a copy those builds set aside comes back when there is no save ([onNewSeed]).
 * [plan] says what the save holds, for the new-run dialog's words.
 */
internal object RunSaves {

    enum class Plan {
        /** No save, or one the game never wrote. */
        NONE,
        /** A Gen 3 save with no Pokémon in it: Continue skips the intro. */
        NO_TEAM,
        /** A Gen 3 save with Pokémon in it: Continue brings that team into the new seed, New Game does not. */
        HOLDS_TEAM,
        /** A save whose party this app does not read: a Game Boy or DS game's. */
        UNREAD,
    }

    /** The run's battery save for [kind], whose randomized ROM is [runRom]. */
    fun file(filesDir: File, kind: RomKind, runRom: File): File =
        if (kind.platform == Platform.NDS) File(File(filesDir, "saves"), runRom.nameWithoutExtension + ".sav")
        else File(filesDir, "saves/${kind.id}.srm")

    /** Where rc32's first builds set a save aside on a new seed. */
    fun setAside(save: File): File = File(save.parentFile, save.name + ".last-run")

    /** A DS game's own run save while another DS game's run holds current.sav. */
    fun dsParked(filesDir: File, gameId: String): File = File(filesDir, "saves/ds/$gameId.sav")

    /** Which DS game the run save beside current.nds belongs to. */
    fun dsOwner(save: File): File = File(save.parentFile, save.name + ".game")

    /**
     * Before a run of [kind] moves in (rc33 audit P1). melonDS names the save after the ROM, and every DS run's ROM is
     * current.nds, so every DS game shared saves/current.sav: a HeartGold run opened on the last Platinum run's save.
     * A DS run's save is now one file per game, as the Game Boy and GBA ones always were: the other game's is parked
     * under its own name and [kind]'s comes back. A new seed of the same game keeps its save, as before. [previous]
     * is the run being replaced, which owns a save written before rc33 recorded owners.
     */
    fun dsSwap(filesDir: File, kind: RomKind, previous: RomKind?) {
        if (kind.platform != Platform.NDS) return
        val save = File(filesDir, "saves/current.sav")
        val marker = dsOwner(save)
        val owner = runCatching { marker.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: previous?.takeIf { it.platform == Platform.NDS }?.id
        if (owner == kind.id) { runCatching { marker.writeText(kind.id) }; return }
        if (save.isFile) {
            // A save whose game is not known is kept under a name of its own rather than handed to this game.
            val parked = dsParked(filesDir, owner ?: "unknown-" + save.lastModified())
            parked.parentFile?.mkdirs()
            runCatching { StateSlots.replace(save, parked) }
        }
        val mine = dsParked(filesDir, kind.id)
        if (mine.isFile && !save.exists()) runCatching { StateSlots.replace(mine, save) }
        runCatching { marker.parentFile?.mkdirs(); marker.writeText(kind.id) }
    }

    fun plan(save: File, kind: RomKind): Plan {
        val bytes = runCatching { save.takeIf { it.isFile }?.readBytes() }.getOrNull() ?: return Plan.NONE
        if (!SaveCheck.hasProgress(bytes, kind.platform)) return Plan.NONE
        // Nat. Dex builds too: their save keeps vanilla's sections and party count (SaveCheck.gen3PartyCount).
        val party = when {
            kind.platform != Platform.GBA -> null
            kind.family in GEN3_FAMILIES -> SaveCheck.gen3PartyCount(bytes, frlg = kind.family == "FRLG")
            // Heart & Soul's save keeps Emerald's sections; its party count sits a little later (SaveCheck.HNS_PARTY_COUNT).
            kind.isHns -> SaveCheck.gen3PartyCount(bytes, SaveCheck.HNS_PARTY_COUNT)
            else -> null
        }
        return when {
            party == 0 -> Plan.NO_TEAM
            party != null -> Plan.HOLDS_TEAM
            else -> Plan.UNREAD
        }
    }

    /** What the new seed opens with: the run's save, or with none, the set-aside copy [onNewSeed] brings back. */
    fun planOnNewSeed(save: File, kind: RomKind): Plan =
        plan(save, kind).takeIf { it != Plan.NONE } ?: plan(setAside(save), kind)

    /**
     * What a new run of [kind] would open with, before it is made (the Run tab's confirm): its own save, which for a DS
     * game is parked under saves/ds/ while another DS game's run holds current.sav ([dsSwap]).
     */
    fun planForNewRun(filesDir: File, kind: RomKind, runRom: File): Plan {
        if (kind.platform == Platform.NDS) {
            val owner = runCatching { dsOwner(File(filesDir, "saves/current.sav")).readText().trim() }.getOrNull()
            if (!owner.isNullOrEmpty() && owner != kind.id) return plan(dsParked(filesDir, kind.id), kind)
        }
        return planOnNewSeed(file(filesDir, kind, runRom), kind)
    }

    /**
     * As a new seed moves in. The save stays where it is. With no save, or one the game never wrote, a copy rc32's
     * first builds set aside comes back: Blake's Nat. Dex FireRed save in front of the three Poké Balls was one.
     * Returns what the new seed opens with.
     */
    fun onNewSeed(save: File, kind: RomKind): Plan {
        val now = plan(save, kind)
        if (now != Plan.NONE) return now
        val copy = setAside(save)
        val old = plan(copy, kind)
        if (old == Plan.NONE) return now
        val back = runCatching {
            save.delete()   // nothing in it: SaveCheck.hasProgress said so
            copy.renameTo(save) || (runCatching { copy.copyTo(save, overwrite = true) }.isSuccess && copy.delete())
        }.getOrDefault(false)
        return if (back) old else now
    }

    private val GEN3_FAMILIES = setOf("FRLG", "RSE")
}
