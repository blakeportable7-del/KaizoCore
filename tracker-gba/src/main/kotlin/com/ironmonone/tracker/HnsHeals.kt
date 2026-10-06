package com.ironmonone.tracker

import java.util.concurrent.atomic.AtomicInteger

/**
 * Heart & Soul's free heals outside a Pokemon Center, for "Track PC Heals" and Survival's limit: "You may only recover
 * at a PokeCenter (or equivalent NPC) 10 times", "No Healing Stations in Dungeons" (a dungeon: any hideout, any cave,
 * any building with trainers; a forest is not). The PC tracker counts game statistics 15 and 16 (a nurse's heal, a rest
 * at home), and Heart & Soul's nurse and New Bark mom bump them as Emerald's do; nothing else does. Elm's lab heal
 * machine, the National Park teacher and every other bed or healer call Common_EventScript_OutOfCenterPartyHeal and leave
 * no statistic, so the counter never moved for them (found 2026-10-06; the Heart & Soul rules page counts Elm's machine).
 *
 * So the tracker watches the field script engine (sGlobalScriptContext, src/script.c): a heal is counted when the script
 * running is the shared heal called from one of [CALLERS], or one of the Alola benches' own heal ([DIRECT]). Each lasts
 * about three seconds at normal speed (a fade, the heal jingle, a fade back), against the tracker's 700 ms reads. The
 * mom's call ([Common_EventScript_OutOfCenterPartyHeal] from her own script) is left out, as statistic 16 counts her.
 *
 * Not counted (the source read 2026-10-06, branch kaizocore-speed2, the rc36.1 build):
 *  - in a dungeon: Lance in the Rocket Hideout (RocketHideout_B2F), the Saffron Fighting Dojo's nurse (its VIP room is
 *    a building with 32 trainer battles), the Battle Frontier, Battle Tents and Trainer Hill rooms;
 *  - part of the story, not a station: the aide's heal after the egg (NewBarkTown_Lab KaizoCoreHeal), Mr. Pokemon's
 *    house, the heals after Samson Oak's and Steven's battles, the Bug-Catching Contest's end (it heals the contest
 *    party, then gives yours back as it was), the heal after a lost early rival battle;
 *  - a link room (the Colosseum in Goldenrod's Pokemon Center), and a whiteout (the run is over).
 */
internal object HnsHeals {
    /** The scripts that call the shared heal and count as one heal each. */
    val CALLERS: List<LongRange> = listOf(
        HnsLayout.NewBarkTown_Lab_EventScript_HealingMachine2,    // Elm's lab heal machine, New Bark Town
        HnsLayout.NationalPark_Normal_EventScript_Teacher2,       // the teacher in the National Park (outdoors)
        HnsLayout.Route26_House1_EventScript_HealWoman,           // the house on Route 26, before Victory Road
        HnsLayout.SSAquaRooms_EventScript_Bed,                    // your cabin's bed on the S.S. Aqua
        HnsLayout.Route111_OldLadysRestStop_EventScript_Rest,     // Hoenn: the old lady's rest stop on Route 111
        HnsLayout.SSTidalRooms_EventScript_Bed,                   // Hoenn: the S.S. Tidal's bed
    )

    /** Scripts that heal on their own: the benches on Akala, Poni and Ula'ula (outdoors), from the heal on. */
    val DIRECT: List<LongRange> = listOf(
        HnsLayout.Alola_Akala_Bench_Heal_2, HnsLayout.Alola_Poni_Bench_Heal_2, HnsLayout.Alola_Ulaula_Bench_Heal_2,
    )

    private val ctx = HnsLayout.sGlobalScriptContext
    private val F = HnsLayout.ScriptContext

    /** Whether a counted heal is running in the game now, from the script engine's context in [memory]. */
    fun healing(memory: MemoryReader): Boolean {
        val b = memory.read(ctx, HnsLayout.ScriptContext.SIZE)
        if (b.size < HnsLayout.ScriptContext.SIZE) return false
        val ptr = F.scriptPtr.u32(b)
        if (DIRECT.any { ptr in it }) return true
        if (ptr !in HnsLayout.Common_EventScript_OutOfCenterPartyHeal) return false
        val depth = F.stackDepth.at(b)
        if (depth !in 1..F.stack.count) return false
        val returnTo = F.stack.at(b, index = depth - 1).toLong() and 0xFFFFFFFFL
        return CALLERS.any { returnTo in it }
    }

    /**
     * The heals counted since the app started, added to the heal statistics (TrackerState.centerHealsStat). Kept for the
     * whole process, not per tracker: the play screen builds a new tracker after an unreadable read, and a count that went
     * back to 0 then would be taken for a lower statistic and the next heal missed. The counter reads only rises (PcHeals,
     * the first read of an attempt its baseline), so a count kept across runs is never counted twice.
     */
    private val counted = AtomicInteger()

    val total: Int get() = counted.get()

    /** One read's look: a heal [Watch] sees begin is counted once. */
    class Watch {
        private var inside = false

        /** Counts a heal that began since the last poll; returns [total]. */
        fun poll(memory: MemoryReader): Int {
            val now = runCatching { healing(memory) }.getOrDefault(false)
            if (now && !inside) counted.incrementAndGet()
            inside = now
            return total
        }
    }
}
