package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Blake, 2026-10-04, Nat. Dex Emerald on rc34: a wild Oinkologne on Route 102 whose Sand Stream turned the weather to
 * Sandstorm as the battle began (over his Mightyena's Drizzle), and the enemy card's ability lines stayed "---" while
 * the PC tracker recorded it. Then: "missed at 8x, caught at 4x".
 *
 * The reference looks at the battle-script pointer inside the emulator's frame loop, every 30 emulated frames whatever
 * the speed (Battle.update and updateTrackedInfo, Battle.lua:116-139 and :504-519; Program.Frames, Program.lua:16-17
 * and :634-635). The play screen looked on a wall clock: 8 polls 31 ms apart and a full read while in a battle, 700 ms
 * between reads before one is ready. These tests play the real ROMs frame by frame, the battle laid out in RAM where
 * each game keeps it and the scripts holding the pointer as the ROM's own script bytes do, and drive the tracker on
 * the play screen's schedule at 1x, 4x, 8x and the app's top speed, 16x (Platform.GBA.maxTurbo). TriggerTapSim stands
 * for libretrodroid's per-frame tap (cpp/triggertap.cpp).
 */
class AbilityRevealSpeedTest {

    private val roms: File = Dumps.romsDir() ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")
    /** The two Emeralds are played many times and kept; the rest are read once each. */
    private fun rom(name: String): ByteArray? =
        if (name in KEPT) romCache.getOrPut(name) { Dumps.file(roms, name)?.readBytes() } else Dumps.file(roms, name)?.readBytes()

    private data class Mon(val species: Int, val abilitySlot: Int = 0, val liveAbility: Int = 0, val level: Int = 5, val hp: Int = 20)

    /** One game in memory: the ROM (with [patch] laid over it), RAM, the tracker, and the tap when [withTap]. */
    private class Rig(private val rom: ByteArray, private val patch: Map<Long, Byte>, withTap: Boolean) {
        val ram = HashMap<Long, Byte>()
        val reader = MemoryReader { a, n ->
            if (a >= 0x08000000L) {
                val off = a - 0x08000000L
                if (off < 0 || off + n > rom.size) ByteArray(0)
                else ByteArray(n) { patch[a + it] ?: rom[(off + it).toInt()] }
            } else ByteArray(n) { ram[a + it] ?: 0 }
        }
        val map = GameMap.resolve(reader)
        val tracker = GbaTracker(reader, map)
        val tap = TriggerTapSim(reader)
        init { if (withTap) tracker.triggerTap = tap }

        fun put8(a: Long, v: Int) { if (a != 0L) ram[a] = v.toByte() }
        fun put16(a: Long, v: Int) { if (a == 0L) return; put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { if (a == 0L) return; for (i in 0 until 4) put8(a + i, ((v shr (8 * i)) and 0xFF).toInt()) }
        fun romU32(a: Long): Long = reader.read(a, 4).u32(0)

        /** A party record with PID = OTID = 24: key 0, substructures in the order G, A, E, M. */
        fun stageParty(at: Long, mon: Mon) {
            val lay = map.monLayout
            put32(at, 24); put32(at + 4, 24)
            put16(at + lay.enc, mon.species)
            put32(at + lay.enc + 36 + 4, if (mon.abilitySlot == 1) 0x80000000L else 0L)
            put8(at + lay.level, mon.level); put16(at + lay.curHp, mon.hp); put16(at + lay.maxHp, mon.hp)
        }

        /** gBattleMons[i] as BattleIntroDrawTrainersOrMonsSprites fills it: species, IV word, live ability byte (vanilla). */
        fun stageBattler(i: Int, mon: Mon) {
            val b = map.battleMons + i.toLong() * map.battleMonSize
            put16(b, mon.species)
            put32(b + 0x14, if (mon.abilitySlot == 1) 0x80000000L else 0L)
            // Nat. Dex and MaxDex keep no u8 ability there: garbage on purpose, as NatDexAbilityTrackingTest does.
            put8(b + 0x20, if (map.abilitiesAreU16) 0x77 else mon.liveAbility)
            put16(b + map.battleMonHp, mon.hp); put8(b + map.battleMonHp + 2, mon.level); put16(b + map.battleMonHp + 4, mon.hp)
        }

        /** The address of the ability script row [trigger] for ability [id]. */
        fun anchor(trigger: String, id: Int): Long =
            tracker.abilityScripts.entries.firstOrNull { (_, rows) -> rows.any { it.first == trigger && id in it.second } }?.key
                ?: fail("${map.name} has no $trigger row for ability $id")
    }

    /** The base data of [species] with abilities [a1] and [a2], as a randomizer writes them (u16 at 0x16 or u8 at 22). */
    private fun abilityPatch(rom: ByteArray, species: Int, a1: Int, a2: Int): Map<Long, Byte> {
        val probe = Rig(rom, emptyMap(), withTap = false).map
        val at = probe.baseStats + species.toLong() * probe.baseStatsStride
        return if (probe.abilitiesAreU16) mapOf(
            at + 0x16 to a1.toByte(), at + 0x17 to (a1 shr 8).toByte(), at + 0x18 to a2.toByte(), at + 0x19 to (a2 shr 8).toByte())
        else mapOf(at + 22 to a1.toByte(), at + 23 to a2.toByte())
    }

    /** The game, one emulated frame at a time: what changes on each frame, then the tap (LibretroDroid::step). */
    private class Game(val rig: Rig) {
        private val events = java.util.TreeMap<Int, MutableList<Rig.() -> Unit>>()
        var frame = 0; private set
        fun at(f: Int, e: Rig.() -> Unit) { events.getOrPut(f) { mutableListOf() } += e }
        fun begin() { events[0]?.forEach { rig.it() } }
        fun runTo(target: Int) {
            while (frame < target) {
                frame++
                events[frame]?.forEach { rig.it() }
                rig.tap.frame()
            }
        }
    }

    /**
     * A wild battle from the overworld after an earlier one to the action menu, beginning at frame [start]: the outcome
     * reset and the new party, gBattleMons filled as BattleIntroDrawPartySummaryScreens begins ([intro] frames of the
     * slide), the intro's messages, then each switch-in ability (its scripting battler, then each pointer value and the
     * frames it holds), then HandleTurnActionSelectionState. Returns the frame the action menu opens.
     */
    private fun wildBattle(game: Game, start: Int, player: Mon, foe: Mon, abilities: List<Pair<Int, List<Pair<Long, Int>>>>, intro: Int = 120, midBattle: Int = 0): Int {
        val m = game.rig.map
        game.at(0) {
            put8(m.partyCount, 1); stageParty(m.party, player)
            put8(m.battlersCount, 2); put8(m.battleOutcome, 1)
            stageBattler(0, Mon(25, liveAbility = 9)); stageBattler(1, Mon(16, liveAbility = 51))
            put32(m.battleMainFunc, m.returnToOverworld); put32(m.scriptCurrInstr, STALE_SCRIPT)
        }
        var f = start
        game.at(f) {
            put8(m.battleOutcome, 0); put32(m.battleTypeFlags, 0)
            stageParty(m.enemyParty, foe)
            put16(m.battlerPartyIndexes, 0); put16(m.battlerPartyIndexes + 2, 0)
            put32(m.battleMainFunc, INTRO_GET_DATA)
        }
        f += 8
        game.at(f) { stageBattler(0, player); stageBattler(1, foe); put32(m.battleMainFunc, m.introDrawPartySummary) }
        f += intro
        game.at(f) { put32(m.battleMainFunc, INTRO_MESSAGES) }
        f += 150
        // A switch-in later in the battle: the action menu up for [midBattle] frames first, the battle long since ready.
        if (midBattle > 0) { game.at(f) { put32(m.battleMainFunc, m.handleTurnAction); put32(m.scriptCurrInstr, ACTION_MENU_SCRIPT) }; f += midBattle }
        for ((battler, script) in abilities) {
            game.at(f) { put32(m.battleMainFunc, RUN_SCRIPT); put8(m.scriptingBattler, battler) }
            for ((ptr, frames) in script) { game.at(f) { put32(m.scriptCurrInstr, ptr) }; f += frames }
        }
        game.at(f) { put32(m.battleMainFunc, m.handleTurnAction); put32(m.scriptCurrInstr, ACTION_MENU_SCRIPT) }
        return f
    }

    /**
     * PlayScreen's Gen 3 tracker loop (the GBA LaunchedEffect in PlayScreen.kt): while the last read was in a battle, 8
     * fast polls ([POLL_MS] apart) and then a read; otherwise 700 ms and then a read. Each read takes [READ_MS]. The
     * emulator runs [speed] frames per vsync meanwhile.
     */
    private fun play(game: Game, speed: Int, until: Int): List<TrackerState> {
        val t = game.rig.tracker
        val framesPerMs = GBA_HZ * speed / 1000.0
        var ms = 0.0
        fun wait(d: Double) { ms += d; game.runTo((ms * framesPerMs).toInt()) }
        val states = ArrayList<TrackerState>()
        var last: TrackerState? = null
        game.begin()
        while (game.frame < until) {
            if (last?.inBattle == true) repeat(8) { wait(POLL_MS); t.pollAbilityTrigger() } else wait(700.0)
            last = t.read()
            states += last
            wait(READ_MS)
        }
        return states
    }

    private fun revealed(states: List<TrackerState>) = states.flatMap { it.abilitiesRevealed }.toSet()

    /** The Nat. Dex ability scripts open with the pop-up (call) and hold the anchor at +9 (NatDexExtension.lua:17786-17803). */
    private fun natDexSwitchIn(r: Rig, anchor: Long, hold: Int): List<Pair<Long, Int>> {
        val start = anchor - 9
        val popup = r.romU32(start + 1)
        return listOf(popup to 40, start + 5 to 1, start + 8 to 50, anchor to hold, start + 0xC to 60)
    }

    /** Vanilla Emerald's opens on its anchor: pause, printstring, waitstate, playanimation (BattleScript_SandstreamActivates). */
    private fun vanillaSwitchIn(anchor: Long, hold: Int) = listOf(anchor to hold, anchor + 3 to 1, anchor + 6 to 50, anchor + 7 to 60)

    /**
     * Blake's battle on Nat. Dex Emerald 1.2.1: his Mightyena (Drizzle) against a wild Oinkologne (941) with Sand Stream,
     * both written into the base data as the randomizer would. Each script holds its anchor [hold] frames (the ROM's
     * own pause is 0x1F, 31). One run per battle start across a whole read cycle, so every phase of the polls is met.
     * Returns, per run, what the tracker revealed and the species its enemy card showed.
     */
    private fun blakesBattle(speed: Int, hold: Int, withTap: Boolean, midBattle: Int = 0): List<Pair<Set<Pair<Int, String>>, Set<Int>>>? {
        val rom = rom("emerald-natdex-121.gba") ?: return null
        val patch = abilityPatch(rom, OINKOLOGNE, SAND_STREAM, THICK_FAT) + abilityPatch(rom, MIGHTYENA, DRIZZLE, INTIMIDATE)
        val cycle = (700.0 * GBA_HZ * speed / 1000.0).toInt()
        val step = maxOf(1, cycle / 40)
        return (100 until 100 + cycle step step).map { start ->
            val r = Rig(rom, patch, withTap)
            val game = Game(r)
            val end = wildBattle(game, start, Mon(MIGHTYENA, level = 8, hp = 30), Mon(OINKOLOGNE), listOf(
                0 to natDexSwitchIn(r, r.anchor("BATTLER", DRIZZLE), hold),
                1 to natDexSwitchIn(r, r.anchor("BATTLER", SAND_STREAM), hold),
            ), midBattle = midBattle)
            val states = play(game, speed, end + 400)
            revealed(states) to states.mapNotNull { it.enemy?.species }.toSet()
        }
    }

    private fun assertBlakesSandStream(speed: Int, hold: Int) {
        val runs = blakesBattle(speed, hold, withTap = true) ?: return println("SKIP: Nat. Dex Emerald ROM missing")
        for ((i, run) in runs.withIndex()) {
            val (got, cards) = run
            assertTrue(OINKOLOGNE to "Sand Stream" in got, "${speed}x, run $i: Sand Stream not revealed: $got")
            // The card shows the same species the reveal is filed under (PlayScreen: statMarks.abilityFor(foe.species)).
            assertTrue(OINKOLOGNE in cards, "${speed}x, run $i: the enemy card never showed Oinkologne: $cards")
            // Each message is checked against its own battler: Drizzle is the Mightyena's, Sand Stream the Oinkologne's.
            assertTrue(OINKOLOGNE to "Drizzle" !in got && MIGHTYENA to "Sand Stream" !in got, "${speed}x, run $i: a reveal filed under the wrong battler: $got")
        }
    }

    @Test fun `Blake's Sand Stream is revealed at 1x`() = assertBlakesSandStream(1, ROM_PAUSE)
    @Test fun `Blake's Sand Stream is revealed at 4x`() = assertBlakesSandStream(4, ROM_PAUSE)
    @Test fun `Blake's Sand Stream is revealed at 8x`() = assertBlakesSandStream(8, ROM_PAUSE)
    @Test fun `Blake's Sand Stream is revealed at 16x, the top speed`() = assertBlakesSandStream(16, ROM_PAUSE)

    /**
     * A message that holds the pointer for fewer frames than one of the old polls spanned (32 ms is 15 frames at 8x) is
     * still caught on every run, and the polls alone (no tap, as before this fix) miss it on some: the scenario is one
     * the old code fails, so this test cannot pass by being easy.
     */
    @Test
    fun `a reveal shorter than one poll interval is caught at 8x, where the polls alone miss it`() {
        val framesPerPoll = POLL_MS * GBA_HZ * 8 / 1000.0
        assertTrue(SHORT_HOLD < framesPerPoll, "the hold must be shorter than a poll interval at 8x ($framesPerPoll frames)")
        assertBlakesSandStream(8, SHORT_HOLD)
        val old = blakesBattle(8, SHORT_HOLD, withTap = false) ?: return
        val missed = old.count { (got, _) -> OINKOLOGNE to "Sand Stream" !in got }
        println("8x, ${SHORT_HOLD}-frame hold: the polls alone missed $missed of ${old.size}")
        assertTrue(missed > 0, "the polls alone caught every run: the scenario does not show the bug")
    }

    @Test
    fun `a reveal shorter than one poll interval is caught at 16x`() {
        assertBlakesSandStream(16, SHORT_HOLD)
        val old = blakesBattle(16, SHORT_HOLD, withTap = false) ?: return
        assertTrue(old.any { (got, _) -> OINKOLOGNE to "Sand Stream" !in got }, "the polls alone caught every run at 16x")
    }

    /** A message on screen for a single emulated frame is still one arrival on the anchor. */
    @Test
    fun `even a one-frame hold is caught at 16x`() = assertBlakesSandStream(16, 1)

    /** Vanilla Emerald: the anchor is the script's own first pause, and the ability the live byte at gBattleMons + 0x20. */
    @Test
    fun `vanilla Emerald's Sand Stream is revealed at every speed`() {
        val rom = rom("emerald-u.gba") ?: return println("SKIP: Emerald ROM missing")
        for (speed in listOf(1, 4, 8, 16)) for (hold in listOf(ROM_PAUSE + 1, SHORT_HOLD)) {
            val cycle = (700.0 * GBA_HZ * speed / 1000.0).toInt()
            for (start in 100 until 100 + cycle step maxOf(1, cycle / 12)) {
                val r = Rig(rom, emptyMap(), withTap = true)
                val game = Game(r)
                val ss = r.anchor("BATTLER", SAND_STREAM)
                assertEquals(0x082DB470L, ss, "Emerald's BattleScript_SandstreamActivates (AbilityAddresses)")
                val dz = r.anchor("BATTLER", DRIZZLE)
                val end = wildBattle(game, start, Mon(MIGHTYENA, liveAbility = DRIZZLE, level = 8, hp = 30), Mon(TYRANITAR, liveAbility = SAND_STREAM), listOf(
                    0 to vanillaSwitchIn(dz, hold), 1 to vanillaSwitchIn(ss, hold)))
                val got = revealed(play(game, speed, end + 400))
                assertTrue(TYRANITAR to r.tracker.abilityName(SAND_STREAM) in got, "Emerald ${speed}x hold $hold start $start: $got")
                assertTrue(TYRANITAR to r.tracker.abilityName(DRIZZLE) !in got, "Emerald: Drizzle filed under the foe: $got")
            }
        }
    }

    /**
     * Every Gen 3 game the app reads, through its own ability table: a foe's switch-in Sand Stream holding its anchor for
     * a few frames at 16x is revealed, and Intimidate and Rough Skin (a contact ability, the TARGET's) the same way.
     */
    @Test
    fun `every Gen 3 game's own table, at 16x`() {
        var ran = 0
        for (name in GAMES) {
            val rom = rom(name) ?: continue
            for ((id, trigger) in listOf(SAND_STREAM to "BATTLER", INTIMIDATE to "BATTLER", ROUGH_SKIN to "ATTACKER")) {
                val patch = abilityPatch(rom, TYRANITAR, id, id)
                val r = Rig(rom, patch, withTap = true)
                val game = Game(r)
                val anchor = r.anchor(trigger, id)
                // ATTACKER: the target's ability (Battle.lua:665-671); the attacker is the player's battler 0.
                val script = listOf(PRE_SCRIPT to 30, anchor to SHORT_HOLD, POST_SCRIPT to 60)
                val m = r.map
                val end = wildBattle(game, 100, Mon(MIGHTYENA, liveAbility = KEEN_EYE, level = 8, hp = 30), Mon(TYRANITAR, liveAbility = id), listOf(1 to script))
                game.at(1) { put8(m.battlerAttacker, 0); put8(m.battlerTarget, 1) }
                val got = revealed(play(game, 16, end + 400))
                assertTrue(TYRANITAR to r.tracker.abilityName(id) in got, "$name (${m.name}): $trigger $id at 16x not revealed: $got")
                ran++
            }
        }
        println("every Gen 3 game: $ran reveals checked")
        if (Dumps.required) assertEquals(GAMES.size * 3, ran)
    }

    /** What the tap is armed with fits the emulator's bounds (cpp/triggertap.h) on every game, or it would be refused. */
    @Test
    fun `the tap plan fits the emulator's bounds on every game`() {
        for (name in GAMES) {
            val rom = rom(name) ?: continue
            val r = Rig(rom, emptyMap(), withTap = false)
            val p = r.tracker.tapPlan ?: fail("$name: no tap plan")
            assertEquals(r.map.scriptCurrInstr, p.watch)
            assertTrue(p.targets.size in 1..TriggerTapSim.MAX_TARGETS, "$name: ${p.targets.size} targets")
            assertTrue(p.addresses.size <= TriggerTapSim.MAX_RANGES && p.lengths.all { it in 1..TriggerTapSim.MAX_RANGE_LENGTH }, "$name: ranges")
            assertTrue(p.contextLength <= TriggerTapSim.MAX_CONTEXT, "$name: ${p.contextLength} bytes")
            // The tap reads only the GBA's work RAM, through the core's own buffer, bounds-checked every frame.
            for (a in p.addresses + p.watch) assertTrue(a in 0x02000000L until 0x02040000L, "$name: %08X is not in work RAM".format(a))
            assertTrue(TriggerTapSim(r.reader).arm(p.watch, p.targets, p.addresses, p.lengths), "$name: refused")
        }
    }

    /**
     * Two messages between two polls at 16x, the foe's Intimidate and then yours: each is checked against the battlers
     * as its own frame left them (the tap's copy), so the foe's is filed under the foe even though the scripting battler
     * is yours by the time anything reads it.
     */
    @Test
    fun `two messages between two polls are each filed under their own battler`() {
        val rom = rom("emerald-natdex-121.gba") ?: return
        val patch = abilityPatch(rom, OINKOLOGNE, INTIMIDATE, INTIMIDATE) + abilityPatch(rom, MIGHTYENA, INTIMIDATE, INTIMIDATE)
        for (start in 100 until 160 step 3) {
            val r = Rig(rom, patch, withTap = true)
            val game = Game(r)
            val anchor = r.anchor("BATTLER", INTIMIDATE)
            val end = wildBattle(game, start, Mon(MIGHTYENA, level = 8, hp = 30), Mon(OINKOLOGNE), listOf(
                1 to listOf(PRE_SCRIPT to 4, anchor to SHORT_HOLD, POST_SCRIPT to 2),
                0 to listOf(anchor to SHORT_HOLD, POST_SCRIPT to 60)), midBattle = 2000)
            val got = revealed(play(game, 16, end + 400))
            val name = r.tracker.abilityName(INTIMIDATE)
            assertTrue(OINKOLOGNE to name in got && MIGHTYENA to name in got, "start $start: $got")
        }
    }

    /**
     * A slow reader: one read after a whole battle's worth of catches. Each reveal comes out once, the same message
     * arriving a hundred times takes one place in the ring, and seventy different copies of one message never write past
     * the ring (64 places; the rest are counted) while the reveal still comes through.
     */
    @Test
    fun `a slow reader loses no reveal and doubles none, and the ring stays bounded`() {
        val rom = rom("emerald-natdex-121.gba") ?: return
        val patch = abilityPatch(rom, OINKOLOGNE, INTIMIDATE, INTIMIDATE) + abilityPatch(rom, MIGHTYENA, INTIMIDATE, INTIMIDATE)
        val r = Rig(rom, patch, withTap = true)
        val game = Game(r)
        val m = r.map
        val anchor = r.anchor("BATTLER", INTIMIDATE)
        val foe = (0 until 100).flatMap { listOf(anchor to 3, POST_SCRIPT to 2) }
        val end = wildBattle(game, 50, Mon(MIGHTYENA, level = 8, hp = 30), Mon(OINKOLOGNE), listOf(1 to foe, 0 to listOf(anchor to 3, POST_SCRIPT to 5)))
        game.begin()
        game.runTo(end + 10)
        val name = r.tracker.abilityName(INTIMIDATE)
        assertEquals(100 + 1, r.tap.caught, "every arrival caught")
        assertEquals(2, r.tap.mostHeld, "a repeated message takes one place")
        val first = r.tracker.read().abilitiesRevealed
        assertEquals(listOf(OINKOLOGNE to name, MIGHTYENA to name).sortedBy { it.first }, first.sortedBy { it.first }, "each once")
        assertEquals(emptyList(), r.tracker.read().abilitiesRevealed, "and never again")

        // Seventy different copies before the next read: the Trace message's battler byte, copied but no part of an
        // Intimidate, differs each time.
        var f = game.frame
        for (i in 0 until 70) {
            game.at(f + 1) { put8(m.battleTextBuff1 + 2, i); put8(m.scriptingBattler, 1); put32(m.scriptCurrInstr, anchor) }
            game.at(f + 3) { put32(m.scriptCurrInstr, POST_SCRIPT) }
            f += 4
        }
        game.runTo(f + 1)
        assertEquals(TriggerTapSim.CAPACITY, r.tap.mostHeld, "the ring never holds more than its places")
        assertTrue(r.tap.droppedTotal > 0, "the rest were counted, not written")
        assertEquals(listOf(OINKOLOGNE to name), r.tracker.read().abilitiesRevealed)
    }

    /** A frame caught after the battle ended (gBattleOutcome set) is not a reveal (Battle.update, Battle.lua:116-139). */
    @Test
    fun `a caught frame from an ended battle reveals nothing`() {
        val rom = rom("emerald-u.gba") ?: return
        val r = Rig(rom, emptyMap(), withTap = true)
        val m = r.map
        r.put8(m.battlersCount, 2); r.put8(m.battleOutcome, 1); r.put8(m.scriptingBattler, 1)
        r.stageBattler(1, Mon(TYRANITAR, liveAbility = SAND_STREAM))
        r.put32(m.scriptCurrInstr, r.anchor("BATTLER", SAND_STREAM))
        r.tap.frame()
        assertEquals(1, r.tap.caught)
        assertEquals(emptyList(), r.tracker.read().abilitiesRevealed)
    }

    companion object {
        private val romCache = HashMap<String, ByteArray?>()
        private val KEPT = setOf("emerald-natdex-121.gba", "emerald-u.gba")
        private const val GBA_HZ = 59.7275
        /** delay(31) and the poll itself. */
        private const val POLL_MS = 32.0
        private const val READ_MS = 40.0
        /** The Nat. Dex scripts' own pause after the message (39 1F 00). */
        private const val ROM_PAUSE = 31
        private const val SHORT_HOLD = 10

        private const val OINKOLOGNE = 941
        private const val MIGHTYENA = 262
        private const val TYRANITAR = 248
        private const val DRIZZLE = 2
        private const val INTIMIDATE = 22
        private const val ROUGH_SKIN = 24
        private const val SAND_STREAM = 45
        private const val THICK_FAT = 47
        private const val KEEN_EYE = 51

        // Stand-ins for the intro's other functions and scripts: values no game uses for a trigger or a timing symbol.
        private const val STALE_SCRIPT = 0x08000011L
        private const val PRE_SCRIPT = 0x08000013L
        private const val POST_SCRIPT = 0x08000015L
        private const val ACTION_MENU_SCRIPT = 0x08000017L
        private const val INTRO_GET_DATA = 0x08000021L
        private const val INTRO_MESSAGES = 0x08000023L
        private const val RUN_SCRIPT = 0x08000025L

        private val GAMES = listOf("ruby-u.gba", "sapphire-u.gba", "emerald-u.gba", "firered-u-v10.gba", "firered-u-v11.gba",
            "leafgreen-u.gba", "firered-natdex-121.gba", "emerald-natdex-121.gba", "firered-maxdex.gba")
    }
}
