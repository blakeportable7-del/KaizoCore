package com.ironmonone.app

import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.PokemonDecoder

/**
 * Which sprite "Play as your Pokemon" shows and which frame of it, decided from plain values so it is tested
 * on the JVM. Nothing here touches the emulator: the engine (SpriteIsMe.kt) feeds it the party and the
 * native side's facing and stepping, and draws what it answers.
 */
object SpriteIsMeLogic {
    /**
     * The lead: the first Pokemon in the party that is not an egg. [dex] is how the game numbers [species]: Gen 3's own
     * ids in a retail game, the Nat. Dex Extension's in a Nat. Dex build (Turtwig is 412 there), MaxDex's own in a
     * MaxDex build (SpriteLead.dexOf). [look] is how the game draws it past its species: shiny, Unown's letter, the
     * game's Deoxys (PalForms).
     */
    data class Lead(
        val species: Int, val hp: Int, val asleep: Boolean, val dex: WalkingPals.Dex = WalkingPals.Dex.GEN3,
        val look: WalkingPals.Look = WalkingPals.Look(),
    )

    enum class Source { PAL, PICTURE, SHEET, NONE }

    /**
     * What to draw. [pal] is the Walking Pals sheets for [Source.PAL]. [lead] is set when the lead's own state
     * (fainted, asleep) applies to the sprite: the lead's sprite, or a picked or imported one while the party has a
     * lead to mirror.
     */
    data class Choice(val source: Source, val pal: WalkingPals.Pal? = null, val lead: Lead? = null)

    /**
     * The extension's order: a Pokemon picked ("Always use"), else the player's own sprite, else the lead.
     * Here the player picks which of the three applies ([who]); a pick that cannot be honoured (no species
     * chosen, nothing imported, no sprite for that species) falls to the lead. With no lead to be (no Pokemon
     * yet, or one of the few later ones nobody has drawn a walking sprite for) there is nothing to draw, so the
     * trainer is left alone. The extension's "If no Pokemon" pick is gone (Blake, 2026-09-30: "the game should know
     * your lead pokemon, so there is actually no point in choosing it from the list").
     *
     * [always] is numbered as the picker's list is, the Nat. Dex table's ids, whatever the game: Turtwig is 412 on a
     * FireRed too, and every id up to 411 is Gen 3's own, as it always was. MaxDex included: a pick is the same Pokemon
     * on every game, and only the lead is numbered its game's way. [palOf] is SpriteIsMeArt.pal.
     *
     * Blake, 2026-10-03: "If they are shiny you should be able to play as shiny". The lead is drawn as its [Lead.look]
     * says, a picked Pokemon as its shiny when [alwaysShiny] is on; [lookOf] is SpriteIsMeArt.look, which falls back to
     * the plain sheets where no shiny ships.
     */
    fun choose(
        who: SpriteIsMeSettings.Who, always: Int,
        own: SpriteIsMeSettings.Own, ownReady: Boolean,
        lead: Lead?, palOf: (Int, WalkingPals.Dex) -> WalkingPals.Pal?,
        alwaysShiny: Boolean = false,
        lookOf: (WalkingPals.Pal, WalkingPals.Look) -> WalkingPals.Pal = { pal, _ -> pal },
    ): Choice {
        if (who == SpriteIsMeSettings.Who.OWN && ownReady) {
            when (own) {
                SpriteIsMeSettings.Own.PICTURE -> return Choice(Source.PICTURE)
                SpriteIsMeSettings.Own.SHEET -> return Choice(Source.SHEET, lead = lead)
                SpriteIsMeSettings.Own.NONE -> {}
            }
        }
        if (who == SpriteIsMeSettings.Who.ALWAYS && always > 0) palOf(always, WalkingPals.Dex.NAT_DEX)?.let { return Choice(Source.PAL, lookOf(it, WalkingPals.Look(shiny = alwaysShiny)), lead) }
        if (lead != null) palOf(lead.species, lead.dex)?.let { return Choice(Source.PAL, lookOf(it, lead.look), lead) }
        return Choice(Source.NONE)
    }

    /**
     * "Always use"'s list: every name in the Nat. Dex table (Favorites.namesInOrder) whose Pokemon has a walking
     * sprite, in the table's order, Gen 1 to 9 and the forms.
     */
    fun choices(names: List<Pair<Int, String>>, palOf: (Int, WalkingPals.Dex) -> WalkingPals.Pal?): List<Pair<Int, String>> =
        names.filter { (id, _) -> palOf(id, WalkingPals.Dex.NAT_DEX) != null }

    /**
     * The animation to show, as SpriteData decides it for the tracker's own icons: asleep after 55 seconds without
     * input; fainted at 0 HP; asleep while the lead is; walking while the player steps; idle otherwise.
     */
    fun wantedAnim(afk: Boolean, lead: Lead?, moving: Boolean): WalkingPals.Anim = when {
        afk -> WalkingPals.Anim.SLEEP
        lead != null && lead.hp <= 0 -> WalkingPals.Anim.FAINT
        lead != null && lead.asleep -> WalkingPals.Anim.SLEEP
        moving -> WalkingPals.Anim.WALK
        else -> WalkingPals.Anim.IDLE
    }

    /** One frame of a sheet: which animation, which row (facing) and which column. */
    data class Frame(val anim: WalkingPals.Anim, val row: Int, val index: Int)

    /**
     * Keeps the time an animation began, in the emulator's own frames, so frames advance as the game does (faster in
     * fast-forward, still when it is paused) and each animation starts from its first frame. Faint does not loop.
     */
    class Animator {
        private var anim: WalkingPals.Anim? = null
        private var start = 0L
        private var facing = 1

        fun reset() { anim = null; start = 0; facing = 1 }

        /**
         * [frames] is the native side's frame counter, [facingNow] the game's direction (1 down, 2 up, 3 left,
         * 4 right, else unknown: the last one is kept), [sheets] what the sprite has and [rows] how many rows each sheet has.
         * Null when the sprite has nothing to show.
         */
        fun frame(
            frames: Long, moving: Boolean, facingNow: Int, afk: Boolean, lead: Lead?,
            sheets: Map<WalkingPals.Anim, WalkingPals.Sheet>, rows: (WalkingPals.Anim) -> Int,
        ): Frame? {
            if (facingNow in 1..4) facing = facingNow
            val want = wantedAnim(afk, lead, moving)
            val a = SheetSet.substitute(want, sheets.keys) ?: return null
            val sheet = sheets[a] ?: return null
            if (anim != a || frames < start) { anim = a; start = frames }
            val index = sheet.frameAt(frames - start, loop = a != WalkingPals.Anim.FAINT)
            val row = if (a == WalkingPals.Anim.IDLE || a == WalkingPals.Anim.WALK) SheetSet.rowFor(facing, rows(a)) else 0
            return Frame(a, row, index)
        }
    }

    /** Emulated frames one step of the bob lasts: a foot every eight, the length of half a tile at walking pace. */
    const val BOB_FRAMES = 8

    /**
     * A walk frame shown longer than this (two tiles at walking pace) bobs while it shows, so the walk still shows steps.
     * Every shipped walk frame is 20 game frames or less but Silcoon's and Cascoon's, which twitch and then hold one for 120.
     */
    const val HELD_FRAMES = 4 * BOB_FRAMES

    /** How long the sprite waits, with no key and no step or turn in the game, before it falls asleep (SpriteData's 55 seconds). */
    const val IDLE_NANOS = WalkingPals.IDLE_SECONDS_UNTIL_SLEEP * 1_000_000_000L
}

/**
 * The lead Pokemon, read out of the game's party the way the tracker reads it (tracker-gba's PokemonDecoder), and
 * for every mode: nothing here needs a verified ROM or a run.
 */
object SpriteLead {
    sealed interface Reading {
        /** The party could not be read (the core is still starting, or the map is wrong for this game). */
        object Unreadable : Reading
        /** Readable, but no Pokemon that is not an egg. */
        object None : Reading
        data class Found(val lead: SpriteIsMeLogic.Lead) : Reading
    }

    /** Status bits 0-2 are sleep turns. */
    const val SLEEP_MASK = 7L

    fun read(reader: MemoryReader, map: GameMap): Reading {
        val countBytes = reader.read(map.partyCount, 1)
        if (countBytes.isEmpty()) return Reading.Unreadable
        val count = (countBytes[0].toInt() and 255)
        if (count > 6) return Reading.Unreadable
        if (count == 0) return Reading.None
        val size = map.monLayout.size
        val bytes = reader.read(map.party, count * size)
        if (bytes.size < count * size) return Reading.Unreadable
        for (i in 0 until count) {
            val slot = bytes.copyOfRange(i * size, (i + 1) * size)
            if (PokemonDecoder.isEmpty(slot)) continue
            val mon = runCatching { PokemonDecoder.decode(slot, map.monLayout) }.getOrNull() ?: continue
            if (mon.isEgg) continue
            if (mon.species <= 0) continue
            return Reading.Found(lead(mon, dexOf(map), map))
        }
        return Reading.None
    }

    /**
     * How [map]'s game numbers its party: MaxDex 1.0 its own way (its Legends Z-A Megas, 1236 on, are not Nat. Dex's
     * Pokemon at those ids), a Nat. Dex build the Nat. Dex Extension's way, any other Gen 3 game Gen 3's.
     */
    fun dexOf(map: GameMap): WalkingPals.Dex = when {
        map.hns -> WalkingPals.Dex.HNS
        map.nameSet == "maxdex" -> WalkingPals.Dex.MAX_DEX
        map.expandedSpeciesIds -> WalkingPals.Dex.NAT_DEX
        else -> WalkingPals.Dex.GEN3
    }

    /**
     * [mon] as the lead to walk as: its species (Castform out of battle in its Normal form), its HP and sleep, and how the
     * game draws it, shiny and in its form (PalForms: Unown's letter from its personality, the game's Deoxys).
     */
    fun lead(mon: PokemonDecoder.Mon, dex: WalkingPals.Dex, map: GameMap): SpriteIsMeLogic.Lead {
        val species = PalForms.overworld(mon.species, dex)
        return SpriteIsMeLogic.Lead(species, mon.curHp, (mon.status and SLEEP_MASK) != 0L, dex, PalForms.gen3(species, dex, mon.pid, mon.shiny, map.routeVersion))
    }
}
