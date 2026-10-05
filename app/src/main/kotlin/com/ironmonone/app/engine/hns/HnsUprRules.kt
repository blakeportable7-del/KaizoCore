package com.ironmonone.app.engine.hns

/**
 * The move and ability pools the UPR fork that owns a settings file would draw from, read from that fork's own
 * constants (vendored engine-natdex and engine-zx) by name, so a Heart & Soul run picks from the same pools a Nat. Dex
 * or vanilla Emerald run of the same mode does:
 *
 * - Nat. Dex (the RSE NatDex v1.2 presets): GlobalConstants.bannedRandomMoves (Struggle and 470 later moves the fork
 *   never rolls) and Gen3Constants.uselessAbilities (202 abilities it never rolls).
 * - ZX (the vanilla RSE presets): vanilla Emerald has Gen 1-3 moves and abilities only, so those are the pools; less
 *   Struggle, Forecast and Cacophony as ZX has it.
 *
 * Both forks' bannedForDamagingMove list is the same. UPR's camelCase names map to pokeemerald-expansion's constants
 * (uTurn is MOVE_U_TURN), with the few the pattern misses named in [SPECIAL].
 */
internal class HnsUprRules(private val zx: Boolean) {
    /** HnS MOVE_ / ABILITY_ constant names (without prefix) the fork never rolls at random. */
    val bannedMoves: Set<String>
    val badDamagingMoves: Set<String>
    val bannedAbilities: Set<String>
    /** With ZX, the only moves and abilities a vanilla Emerald has; null with Nat. Dex (everything not banned). */
    val onlyMoves: Set<String>?
    val onlyAbilities: Set<String>?

    init {
        if (!zx) {
            val moves = constants(com.dabomstew.pkrandom.constants.Moves::class.java)
            val abilities = constants(com.dabomstew.pkrandom.constants.Abilities::class.java)
            val banned = com.dabomstew.pkrandom.constants.GlobalConstants.bannedRandomMoves
            val bad = com.dabomstew.pkrandom.constants.GlobalConstants.bannedForDamagingMove
            bannedMoves = moves.filter { (_, v) -> v in banned.indices && banned[v] }.keys
            badDamagingMoves = moves.filter { (_, v) -> v in bad.indices && bad[v] }.keys
            // Gen 3 kept Cacophony at 76 and Air Lock at 77; the fork's list bans 76 as Cacophony, which HnS has not, and
            // its Abilities constants call 76 Air Lock. So 76 is not carried over by name.
            val useless = com.dabomstew.pkrandom.constants.Gen3Constants.uselessAbilities.toSet() - com.dabomstew.pkrandom.constants.Gen3Constants.cacophonyIndex
            bannedAbilities = abilities.filter { (_, v) -> v in useless }.keys
            onlyMoves = null
            onlyAbilities = null
        } else {
            val moves = constants(com.dabomstew.pkrandomzx.constants.Moves::class.java)
            val abilities = constants(com.dabomstew.pkrandomzx.constants.Abilities::class.java)
            val banned = com.dabomstew.pkrandomzx.constants.GlobalConstants.bannedRandomMoves
            val bad = com.dabomstew.pkrandomzx.constants.GlobalConstants.bannedForDamagingMove
            bannedMoves = moves.filter { (_, v) -> v in banned.indices && banned[v] }.keys
            badDamagingMoves = moves.filter { (_, v) -> v in bad.indices && bad[v] }.keys
            bannedAbilities = setOf("FORECAST")
            val lastMove = com.dabomstew.pkrandomzx.constants.Moves.psychoBoost
            onlyMoves = moves.filter { (_, v) -> v in 1..lastMove }.keys
            val lastAbility = com.dabomstew.pkrandomzx.constants.Abilities.airLock
            onlyAbilities = abilities.filter { (_, v) -> v in 1..lastAbility }.keys
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
