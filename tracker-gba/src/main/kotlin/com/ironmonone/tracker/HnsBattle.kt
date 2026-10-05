package com.ironmonone.tracker

import com.ironmonone.tracker.GbaTracker.BattleDetail
import com.ironmonone.tracker.GbaTracker.BattleDetails

/**
 * Battle Details and the last-attack line for Heart & Soul (2026-10-05), which keeps none of the places the five games'
 * screen reads. pokeemerald-expansion folds status2, status3 and the disable struct into each battler's `volatiles`,
 * keeps 32-bit side statuses with their own timers (gSideStatuses, gSideTimers), adds the field statuses (rooms,
 * terrains, Gravity: gFieldStatuses, gFieldTimers), moves Future Sight and Wish into gBattleStruct, and has no
 * gTakenDmg at all. Every offset and bit is the build's own (HnsLayout); the lines are the five games' wording, so
 * the screen reads the same, with the newer effects added in the same style.
 */
internal object HnsBattle {
    /** What the screen needs from the tracker by name. */
    interface Names {
        fun move(id: Int): String
        /** The species in gBattleMons slot [battler], or null when it is empty. */
        fun battler(battler: Int): String?
        /** Whether Truant is tracked for gBattleMons slot [battler]'s species (the "Loafing" line never reveals it). */
        fun truantTracked(battler: Int): Boolean
    }

    /** gBattleEnvironment's names, BATTLE_ENVIRONMENT_* in the build's own order. */
    val ENVIRONMENTS = listOf(
        "Grass", "Long Grass", "Sand", "Underwater", "Water", "Pond", "Mountain", "Cave", "Building", "Plain", "Frontier",
        "Gym", "Leader", "Magma", "Aqua", "Sidney", "Phoebe", "Glacia", "Drake", "Champion", "Groudon", "Kyogre", "Rayquaza",
        "Blue Building", "Gray Cave", "Cave Water", "Gray Cave Water", "Will", "Koga", "Bruno", "Karen", "Rock Snow",
        "Mountain Snow", "Volcano Cave", "Snow Cave", "Soaring", "Sky Pillar", "Burial Ground", "Puddle", "Marsh", "Swamp",
        "Snow", "Ice", "Volcano", "Distortion World", "Space", "Ultra Space",
    )

    private fun turns(n: Int) = "$n Turn" + (if (n == 1) "" else "s")

    /** gBattleWeather's name for the screen: the three Heart & Soul weathers by name, else the five games' words. */
    fun weatherName(raw: Int): String = HnsWeather.name(raw)?.lowercase()?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } ?: when {
        raw and HnsLayout.B_WEATHER_RAIN != 0 -> "Rain"
        raw and HnsLayout.B_WEATHER_SANDSTORM != 0 -> "Sandstorm"
        raw and HnsLayout.B_WEATHER_SUN != 0 -> "Sunlight"
        raw and HnsLayout.B_WEATHER_HAIL != 0 -> "Hail"
        raw and HnsLayout.B_WEATHER_SNOW != 0 -> "Snow"
        else -> "None"
    }

    fun details(mem: MemoryReader, names: Names, battlersCount: Int, turn: Int): BattleDetails {
        fun bytes(a: Long, n: Int): ByteArray = mem.read(a, n).let { if (it.size == n) it else ByteArray(n) }
        val battlers = battlersCount.coerceIn(2, 4)
        val field = ArrayList<BattleDetail>()
        val sides = List(2) { ArrayList<BattleDetail>() }
        val mons = List(4) { ArrayList<BattleDetail>() }

        val terrain = ENVIRONMENTS.getOrElse(bytes(HnsLayout.gBattleEnvironment, 1)[0].toInt() and 0xFF) { "Building" }
        val raw = bytes(HnsLayout.gBattleWeather, 4).u32(0).toInt()
        val bsPtr = bytes(HnsLayout.gBattleStruct, 4).u32(0)
        val bs = if (bsPtr in 0x02000000L..0x0203FFFFL) bytes(bsPtr, 256) else ByteArray(256)
        val BS = HnsLayout.BattleStruct
        val weather = weatherName(raw)
        if (raw != 0 && weather != "None") {
            val left = BS.weatherDuration.at(bs)
            field += if (left > 0) BattleDetail("Weather turns Left: $left") else BattleDetail("Weather: $weather")
        }
        val payday = bytes(HnsLayout.gPaydayMoney, 2).u16(0)
        if (payday != 0) field += BattleDetail("${names.move(6)} ($payday)", moveId = 6)

        // Field statuses and their timers (gFieldStatuses, gFieldTimers).
        val fs = bytes(HnsLayout.gFieldStatuses, 4).u32(0)
        val ft = bytes(HnsLayout.gFieldTimers, HnsLayout.FieldTimer.SIZE)
        val FT = HnsLayout.FieldTimer
        fun fieldLine(bit: Int, name: String, timer: HnsField?, moveId: Int = 0) {
            if (fs and bit.toLong() == 0L) return
            val t = timer?.at(ft) ?: 0
            field += BattleDetail(if (t > 0) "$name: ${turns(t)} Left" else name, moveId)
        }
        fieldLine(HnsLayout.STATUS_FIELD_TRICK_ROOM, "Trick Room", FT.trickRoomTimer)
        fieldLine(HnsLayout.STATUS_FIELD_GRAVITY, "Gravity", FT.gravityTimer)
        fieldLine(HnsLayout.STATUS_FIELD_MAGIC_ROOM, "Magic Room", FT.magicRoomTimer)
        fieldLine(HnsLayout.STATUS_FIELD_WONDER_ROOM, "Wonder Room", FT.wonderRoomTimer)
        fieldLine(HnsLayout.STATUS_FIELD_GRASSY_TERRAIN, "Grassy Terrain", FT.terrainTimer)
        fieldLine(HnsLayout.STATUS_FIELD_MISTY_TERRAIN, "Misty Terrain", FT.terrainTimer)
        fieldLine(HnsLayout.STATUS_FIELD_ELECTRIC_TERRAIN, "Electric Terrain", FT.terrainTimer)
        fieldLine(HnsLayout.STATUS_FIELD_PSYCHIC_TERRAIN, "Psychic Terrain", FT.terrainTimer)
        fieldLine(HnsLayout.STATUS_FIELD_MUDSPORT, names.move(300), FT.mudSportTimer, 300)
        fieldLine(HnsLayout.STATUS_FIELD_WATERSPORT, names.move(346), FT.waterSportTimer, 346)
        fieldLine(HnsLayout.STATUS_FIELD_FAIRY_LOCK, "Fairy Lock", FT.fairyLockTimer)
        fieldLine(HnsLayout.STATUS_FIELD_ION_DELUGE, "Ion Deluge", null)

        // Each side: gSideStatuses (u32 each) and gSideTimers.
        val ST = HnsLayout.SideTimer
        for (side in 0..1) {
            val ss = bytes(HnsLayout.gSideStatuses + side * 4L, 4).u32(0)
            val tb = bytes(HnsLayout.gSideTimers + side.toLong() * ST.SIZE, ST.SIZE)
            val out = sides[side]
            fun on(bit: Int) = ss and bit.toLong() != 0L
            if (on(HnsLayout.SIDE_STATUS_REFLECT)) out += BattleDetail("${names.move(115)}: ${turns(ST.reflectTimer.at(tb))} Left", 115)
            if (on(HnsLayout.SIDE_STATUS_LIGHTSCREEN)) out += BattleDetail("${names.move(113)}: ${turns(ST.lightscreenTimer.at(tb))} Left", 113)
            if (on(HnsLayout.SIDE_STATUS_AURORA_VEIL)) out += BattleDetail("Aurora Veil: ${turns(ST.auroraVeilTimer.at(tb))} Left")
            val spikes = ST.spikesAmount.at(tb)
            if (spikes != 0) out += BattleDetail("${names.move(191)}: $spikes", 191)
            val toxic = ST.toxicSpikesAmount.at(tb)
            if (toxic != 0) out += BattleDetail("Toxic Spikes: $toxic")
            if (on(HnsLayout.SIDE_STATUS_SAFEGUARD)) out += BattleDetail("${names.move(219)}: ${turns(ST.safeguardTimer.at(tb))} Left", 219)
            if (on(HnsLayout.SIDE_STATUS_MIST)) out += BattleDetail("${names.move(54)}: ${turns(ST.mistTimer.at(tb))} Left", 54)
            if (on(HnsLayout.SIDE_STATUS_TAILWIND)) out += BattleDetail("Tailwind: ${turns(ST.tailwindTimer.at(tb))} Left")
            if (on(HnsLayout.SIDE_STATUS_LUCKY_CHANT)) out += BattleDetail("Lucky Chant: ${turns(ST.luckyChantTimer.at(tb))} Left")
        }

        // Each battler's volatiles, the expansion's status2, status3 and disable struct in one.
        val BM = HnsLayout.BattlePokemon; val V = HnsLayout.Volatiles
        val all = bytes(HnsLayout.gBattleMons, BM.SIZE * 4)
        for (i in 0 until battlers) {
            val m = mons[i]
            val v = all.copyOfRange(i * BM.SIZE + BM.volatiles.offset, i * BM.SIZE + BM.volatiles.offset + V.SIZE)
            fun f(x: HnsField) = x.at(v)
            if (f(V.confusionTurns) != 0) m += BattleDetail("Confused (1- 4 Turns)")
            if (f(V.uproarTurns) != 0) m += BattleDetail(names.move(253), 253)
            if (f(V.bideTurns) != 0) m += BattleDetail("${names.move(117)}: ${turns(f(V.bideTurns))}", 117)
            if (f(V.rampageTurns) != 0 || (f(V.multipleTurns) != 0 && f(V.bideTurns) == 0)) m += BattleDetail("Must Attack")
            if (f(V.wrapped) != 0) names.battler(f(V.wrappedBy))?.let { m += BattleDetail("Trapped ($it)") }
            val love = f(V.infatuation)
            if (love != 0) names.battler(love - 1)?.let { m += BattleDetail("${names.move(213)} ($it)", 213) }
            if (f(V.focusEnergy) != 0) m += BattleDetail(names.move(116), 116)
            if (f(V.transformed) != 0) m += BattleDetail(names.move(144), 144)
            if (f(V.rechargeTimer) != 0) m += BattleDetail("Recharging")
            if (f(V.rage) != 0) m += BattleDetail(names.move(99), 99)
            if (f(V.substitute) != 0) m += BattleDetail(names.move(164), 164)
            if (f(V.destinyBond) != 0) m += BattleDetail(names.move(194), 194)
            if (f(V.escapePrevention) != 0) m += BattleDetail("Can't Escape")
            if (f(V.nightmare) != 0) m += BattleDetail(names.move(171), 171)
            if (f(V.cursed) != 0) m += BattleDetail(names.move(174), 174)
            if (f(V.foresight) != 0) m += BattleDetail(names.move(193), 193)
            if (f(V.defenseCurl) != 0) m += BattleDetail(names.move(111), 111)
            if (f(V.torment) != 0) m += BattleDetail(names.move(259), 259)
            val seed = f(V.leechSeed)
            if (seed != 0) names.battler(seed - 1)?.let { m += BattleDetail("${names.move(73)} ($it)", 73) }
            when (f(V.semiInvulnerable)) { 1 -> "Underground"; 2 -> "Underwater"; 3, 5 -> "Airborne"; 4 -> "Vanished"; else -> null }
                ?.let { m += BattleDetail(it) }
            if (f(V.minimize) != 0) m += BattleDetail(names.move(107), 107)
            if (f(V.chargeTimer) != 0) m += BattleDetail(names.move(268), 268)
            if (f(V.root) != 0) m += BattleDetail(names.move(275), 275)
            if (f(V.yawn) != 0) m += BattleDetail("Drowsy", 281)
            if (f(V.imprison) != 0) m += BattleDetail(names.move(286), 286)
            if (f(V.grudge) != 0) m += BattleDetail(names.move(288), 288)
            if (f(V.aquaRing) != 0) m += BattleDetail("Aqua Ring")
            if (f(V.magnetRise) != 0) m += BattleDetail("Magnet Rise: ${turns(f(V.magnetRiseTimer))} Left")
            if (f(V.embargo) != 0) m += BattleDetail("Embargo: ${turns(f(V.embargoTimer))} Left")
            if (f(V.healBlock) != 0) m += BattleDetail("Heal Block: ${turns(f(V.healBlockTimer))} Left")
            if (f(V.telekinesis) != 0) m += BattleDetail("Telekinesis: ${turns(f(V.telekinesisTimer))} Left")
            if (f(V.smackDown) != 0) m += BattleDetail("Grounded")
            if (f(V.gastroAcid) != 0) m += BattleDetail("Ability Suppressed")
            if (f(V.saltCure) != 0) m += BattleDetail("Salt Cure")
            if (f(V.syrupBomb) != 0) m += BattleDetail("Syrup Bomb: ${turns(f(V.syrupBombTimer))} Left")
            if (f(V.throatChopTimer) != 0) m += BattleDetail("Throat Chop: ${turns(f(V.throatChopTimer))} Left")
            if (f(V.tarShot) != 0) m += BattleDetail("Tar Shot")
            if (f(V.octolock) != 0) m += BattleDetail("Octolock")
            if (f(V.laserFocus) != 0) m += BattleDetail("Laser Focus")
            if (f(V.noRetreat) != 0) m += BattleDetail("No Retreat")
            val disabled = f(V.disabledMove)
            if (disabled != 0) m += BattleDetail("${names.move(50)} (${names.move(disabled)})", 50)
            val encored = f(V.encoredMove)
            if (encored != 0) m += BattleDetail("${names.move(227)} (${names.move(encored)})", 227)
            val stockpile = f(V.stockpileCounter)
            if (stockpile != 0) m += BattleDetail("${names.move(254)}: $stockpile", 254)
            if (f(V.perishSong) != 0) m += BattleDetail("Perish Count: ${1 + f(V.perishSongTimer)}", 195)
            val fury = f(V.furyCutterCounter)
            if (fury != 0) m += BattleDetail("${names.move(210)}: $fury", 210)
            val rollout = f(V.rolloutTimer)
            if (rollout != 0) m += BattleDetail("${names.move(205)}: ${turns(rollout)} Left", 205)
            val taunt = f(V.tauntTimer)
            if (taunt != 0) m += BattleDetail("${names.move(269)}: ${turns(taunt)} Left", 269)
            if (f(V.lockOn) != 0) names.battler(f(V.battlerWithSureHit))?.let { m += BattleDetail("${names.move(199)} ($it)", 199) }
            if (f(V.truantCounter) != 0 && names.truantTracked(i)) m += BattleDetail("Loafing")

            // Future Sight and Wish, in gBattleStruct (struct FutureSight { u16 move; u16 counter:10, battlerIndex:3 },
            // struct Wish { u16 counter; u8 partyId }).
            val fsAt = BS.futureSight.offset + i * 4
            val fsWord = (bs[fsAt + 2].toInt() and 0xFF) or ((bs[fsAt + 3].toInt() and 0xFF) shl 8)
            val fsLeft = fsWord and 0x3FF
            if (fsLeft != 0) names.battler((fsWord shr 10) and 7)?.let { m += BattleDetail("Future: ${turns(fsLeft)} Left ($it)") }
            val wishAt = BS.wish.offset + i * 4
            val wishLeft = (bs[wishAt].toInt() and 0xFF) or ((bs[wishAt + 1].toInt() and 0xFF) shl 8)
            if (wishLeft != 0) m += BattleDetail("${names.move(273)}: ${turns(wishLeft)} Left", 273)
        }
        return BattleDetails(terrain, weather, turn, battlers, field, sides, mons)
    }

    /**
     * The last-attack line's damage total for Heart & Soul, which has no gTakenDmg: everything the player's battler 0
     * has lost to a hit, counted from its HP in gBattleMons. A switch (another personality in the slot) starts a fresh
     * count rather than reading the new Pokemon's HP as damage. Fed to DamageWatch in gTakenDmg's place, which counts
     * only what lands while the foe is the attacker, as the five games' total does.
     */
    class Taken {
        private var pid = -1L
        private var hp = -1
        var total = 0; private set

        fun reset() { pid = -1L; hp = -1; total = 0 }

        fun tick(mem: MemoryReader): Int {
            val BM = HnsLayout.BattlePokemon
            val b = mem.read(HnsLayout.gBattleMons, BM.SIZE)
            if (b.size < BM.SIZE) return total
            val p = BM.personality.u32(b)
            val now = BM.hp.at(b)
            if (p != pid) { pid = p; hp = now; return total }
            if (now < hp) total += hp - now
            hp = now
            return total
        }
    }
}
