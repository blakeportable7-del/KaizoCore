package com.ironmonone.app.engine.hns

/**
 * The move pools the UPR fork that owns a settings file would draw from, read from that fork's own
 * constants (vendored engine-natdex and engine-zx) by name, so a Heart & Soul run picks from the same pools a Nat. Dex
 * or vanilla Emerald run of the same mode does:
 *
 * - Nat. Dex (the RSE NatDex v1.2 presets): the Nat. Dex fork's own list, GlobalConstants.bannedRandomMoves (Struggle
 *   and the later moves that fork never rolls). Fairy-type moves are put back in HnsRandomizer.validMoves (Blake,
 *   2026-10-08). The species list is Heart & Soul's own: every enabled entry, Gen 1 to 9, including forms.
 * - ZX (the vanilla RSE presets): vanilla Emerald has Gen 1-3 moves only, so those are the pool, less Struggle.
 *
 * Abilities are Emerald's on both pools (Blake, 2026-10-08): HnsRandomizer.lastAbility stops at Air Lock. Not Black 2's
 * 164.
 *
 * HM moves are never rolled in either pool: HnsRandomizer.validMoves leaves them out (Blake, 2026-10-07: "No hms").
 *
 * Both forks' bannedForDamagingMove list is the same. UPR's camelCase names map to pokeemerald-expansion's constants
 * (uTurn is MOVE_U_TURN), with the few the pattern misses named in [SPECIAL].
 */
class HnsUprRules(private val zx: Boolean) {
    /** HnS MOVE_ constant names (without prefix) the fork never rolls at random. */
    val bannedMoves: Set<String>
    val badDamagingMoves: Set<String>
    /** With ZX, the only moves a vanilla Emerald has. Null with Nat. Dex: everything the fork does not ban, plus Fairy. */
    val onlyMoves: Set<String>?

    init {
        if (!zx) {
            val moves = constants(com.dabomstew.pkrandom.constants.Moves::class.java)
            val banned = com.dabomstew.pkrandom.constants.GlobalConstants.bannedRandomMoves
            val bad = com.dabomstew.pkrandom.constants.GlobalConstants.bannedForDamagingMove
            bannedMoves = moves.filter { (_, v) -> v in banned.indices && banned[v] }.keys
            badDamagingMoves = moves.filter { (_, v) -> v in bad.indices && bad[v] }.keys
            onlyMoves = null
        } else {
            val moves = constants(com.dabomstew.pkrandomzx.constants.Moves::class.java)
            val banned = com.dabomstew.pkrandomzx.constants.GlobalConstants.bannedRandomMoves
            val bad = com.dabomstew.pkrandomzx.constants.GlobalConstants.bannedForDamagingMove
            bannedMoves = moves.filter { (_, v) -> v in banned.indices && banned[v] }.keys
            badDamagingMoves = moves.filter { (_, v) -> v in bad.indices && bad[v] }.keys
            val lastMove = com.dabomstew.pkrandomzx.constants.Moves.psychoBoost
            onlyMoves = moves.filter { (_, v) -> v in 1..lastMove }.keys
        }
    }

    companion object {
        private val SPECIAL = mapOf(
            "conversion2" to "CONVERSION_2", "returnTheMoveNotTheKeyword" to "RETURN", "willOWisp" to "WILL_O_WISP",
            "staticTheAbilityNotTheKeyword" to "STATIC", "asOneChillingNeigh" to "AS_ONE_ICE_RIDER",
            "asOneGrimNeigh" to "AS_ONE_SHADOW_RIDER",
        )

        fun snake(camel: String): String = SPECIAL[camel] ?: camel.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

        /** A UPR constants class's int fields, as HnS constant names (without prefix) to the fork's numbers. */
        private fun constants(c: Class<*>): Map<String, Int> = c.fields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .associate { snake(it.name) to it.getInt(null) }
    }
}
