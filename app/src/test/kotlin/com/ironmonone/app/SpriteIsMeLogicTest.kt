package com.ironmonone.app

import com.ironmonone.app.WalkingPals.Anim as A
import com.ironmonone.app.WalkingPals.Dex
import com.ironmonone.app.WalkingPals.Pack
import com.ironmonone.app.WalkingPals.Pal
import com.ironmonone.app.SpriteIsMeLogic.Choice
import com.ironmonone.app.SpriteIsMeLogic.Frame
import com.ironmonone.app.SpriteIsMeLogic.Lead
import com.ironmonone.app.SpriteIsMeLogic.Source
import com.ironmonone.app.SpriteIsMeSettings.Own
import com.ironmonone.app.SpriteIsMeSettings.Who
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.MemoryReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which sprite "Play as your Pokemon" shows and which frame of it: the choice, the animation rules, the frame clock, and
 * the lead read out of a party laid out as the game lays it out.
 */
class SpriteIsMeLogicTest {
    private val L = SpriteIsMeLogic
    private fun lead(species: Int = 25, hp: Int = 30, asleep: Boolean = false) = Lead(species, hp, asleep)
    /** Gen 1-3 only, by Gen 3's ids, whatever the numbering asked for: the rules below do not depend on the sets. */
    private val hasAll: (Int, Dex) -> Pal? = { id, _ -> if (id in 1..411 && id !in 252..276) gen3(id) else null }
    private fun gen3(id: Int) = Pal(Pack.GEN3, id.toString())
    /** The sets as shipped. */
    private val shipped: (Int, Dex) -> Pal? = ShippedPals.index::find

    // ------------------------------------------------------------------ who

    @Test
    fun `the lead is the default, and picks that cannot be honoured fall back to it`() {
        assertEquals(Choice(Source.PAL, gen3(25), lead(25)), L.choose(Who.LEAD, 0, Own.NONE, false, lead(25), hasAll))
        // "Always use" with nothing chosen, or a species with no sprite, is the lead.
        assertEquals(gen3(25), L.choose(Who.ALWAYS, 0, Own.NONE, false, lead(25), hasAll).pal)
        assertEquals(gen3(25), L.choose(Who.ALWAYS, 260, Own.NONE, false, lead(25), hasAll).pal, "260 is an unused slot: no sprite")
        // "Your own sprite" with nothing imported is the lead too.
        assertEquals(Source.PAL, L.choose(Who.OWN, 0, Own.PICTURE, false, lead(25), hasAll).source)
        assertEquals(Source.PAL, L.choose(Who.OWN, 0, Own.NONE, true, lead(25), hasAll).source)
    }

    @Test
    fun `always use, and the player's own sprite, win over the lead when they can be honoured`() {
        val a = L.choose(Who.ALWAYS, 94, Own.NONE, false, lead(25, hp = 0), hasAll)
        assertEquals(Choice(Source.PAL, gen3(94), lead(25, hp = 0)), a, "Gengar, and the lead's state still applies")
        assertEquals(Source.PICTURE, L.choose(Who.OWN, 94, Own.PICTURE, true, lead(), hasAll).source)
        val sheet = L.choose(Who.OWN, 94, Own.SHEET, true, lead(), hasAll)
        assertEquals(Source.SHEET, sheet.source); assertEquals(lead(), sheet.lead)
        // Nothing to mirror: a picked Pokemon or a sheet set is drawn all the same.
        assertEquals(Choice(Source.PAL, gen3(94), null), L.choose(Who.ALWAYS, 94, Own.NONE, false, null, hasAll))
        assertEquals(Choice(Source.SHEET, null, null), L.choose(Who.OWN, 0, Own.SHEET, true, null, hasAll))
    }

    /** Blake, 2026-09-30: "you will be the default sprite until you have a lead pokemon". */
    @Test
    fun `no lead, or a lead with no sprite, leaves the trainer alone`() {
        assertEquals(Choice(Source.NONE), L.choose(Who.LEAD, 0, Own.NONE, false, null, hasAll))
        // A lead with no sprite is the trainer too.
        assertEquals(Choice(Source.NONE), L.choose(Who.LEAD, 0, Own.NONE, false, lead(700), hasAll))
    }

    // ------------------------------------------------------------------ Gen 4 and later (Blake, 2026-09-30)

    /** "play as Gen 4+ Pokemon": a Nat. Dex build's lead by its own ids, on the second set. */
    @Test
    fun `a Nat Dex lead past Gen 3 is drawn from the second set, by the game's own numbering`() {
        val turtwig = Lead(412, 30, false, Dex.NAT_DEX)
        assertEquals(Choice(Source.PAL, Pal(Pack.NATIONAL, "387"), turtwig), L.choose(Who.LEAD, 0, Own.NONE, false, turtwig, shipped))
        // The same game's Gen 1-3 lead is the set it always was.
        val treecko = Lead(277, 30, false, Dex.NAT_DEX)
        assertEquals(Pal(Pack.GEN3, "277"), L.choose(Who.LEAD, 0, Own.NONE, false, treecko, shipped).pal)
        // 412 in a retail game is not a Pokemon there (it is the egg's id): the trainer stays.
        assertEquals(Choice(Source.NONE), L.choose(Who.LEAD, 0, Own.NONE, false, Lead(412, 30, false, Dex.GEN3), shipped))
        // One of the few nobody has drawn yet (Simisear) leaves the trainer too.
        assertEquals(Choice(Source.NONE), L.choose(Who.LEAD, 0, Own.NONE, false, Lead(539, 30, false, Dex.NAT_DEX), shipped))
    }

    /** "Always use" counts in the picker's numbering, the Nat. Dex table's, so a later Pokemon can be picked on any game. */
    @Test
    fun `always use picks by the Nat Dex table's ids whatever the game`() {
        val charmander = Lead(4, 30, false, Dex.GEN3)
        assertEquals(Pal(Pack.NATIONAL, "387"), L.choose(Who.ALWAYS, 412, Own.NONE, false, charmander, shipped).pal, "Turtwig on a FireRed")
        assertEquals(Pal(Pack.NATIONAL, "6-mega-x"), L.choose(Who.ALWAYS, 1052, Own.NONE, false, charmander, shipped).pal)
        assertEquals(Pal(Pack.GEN3, "277"), L.choose(Who.ALWAYS, 277, Own.NONE, false, charmander, shipped).pal, "a pick from before is what it was")
        // One with no sprite falls to the lead.
        assertEquals(Pal(Pack.GEN3, "4"), L.choose(Who.ALWAYS, 539, Own.NONE, false, charmander, shipped).pal)
    }

    /** "Always use lists every species that has a sheet": the list the picker shows. */
    @Test
    fun `the picker lists every Pokemon with a walking sprite, Gen 4 and later included`() {
        val list = L.choices(Favorites.namesInOrder, shipped)
        val ids = list.map { it.first }.toSet()
        assertTrue(412 in ids && list.first { it.first == 412 }.second == "Turtwig", "Turtwig is listed")
        assertTrue(1050 in ids, "Pecharunt")
        assertTrue(list.any { it.second == "Charizard-X" }, "a form, by the table's own spelling")
        assertTrue(1 in ids && 277 in ids && 411 in ids, "Gen 1-3 as before")
        assertEquals(386, ids.count { it <= 411 }, "every Gen 1-3 Pokemon, and no unused slot")
        // Only those with a sheet: Simisear and Charizard-Y have none.
        assertTrue(539 !in ids && 1053 !in ids)
        // Each listed one draws: its sheets are there.
        for (id in ids) assertTrue(ShippedPals.index.find(id, Dex.NAT_DEX) != null, "$id")
        assertTrue(list.size >= 1100, "only ${list.size}")
        assertEquals(list.map { it.first }, list.map { it.first }.sorted(), "the table's order")
    }

    // ------------------------------------------------------------------ which animation

    @Test
    fun `the animation follows SpriteData's order, fainting and sleeping before walking`() {
        assertEquals(A.IDLE, L.wantedAnim(afk = false, lead = lead(), moving = false))
        assertEquals(A.WALK, L.wantedAnim(false, lead(), true))
        assertEquals(A.SLEEP, L.wantedAnim(true, lead(), true), "asleep after 55 seconds even mid-step")
        assertEquals(A.FAINT, L.wantedAnim(false, lead(hp = 0), true), "at 0 HP")
        assertEquals(A.SLEEP, L.wantedAnim(false, lead(asleep = true), true), "while the lead sleeps")
        assertEquals(A.SLEEP, L.wantedAnim(true, lead(hp = 0), false), "the idle sleep is first, as the tracker's icon has it")
        assertEquals(A.FAINT, L.wantedAnim(false, lead(hp = 0, asleep = true), false))
        assertEquals(A.IDLE, L.wantedAnim(false, null, false), "no lead: nothing to faint or sleep")
        assertEquals(A.WALK, L.wantedAnim(false, null, true))
    }

    private val sheets = mapOf(
        WalkingPals.Anim.IDLE to WalkingPals.Sheet(32, 40, 1, 4, intArrayOf(40, 6, 6)),
        WalkingPals.Anim.WALK to WalkingPals.Sheet(32, 32, 1, 7, intArrayOf(8, 10, 8, 10)),
        WalkingPals.Anim.SLEEP to WalkingPals.Sheet(24, 32, 5, 7, intArrayOf(30, 35)),
        WalkingPals.Anim.FAINT to WalkingPals.Sheet(40, 32, 0, 5, intArrayOf(8, 12, 4, 10)),
    )
    private val rows = { a: WalkingPals.Anim -> if (a == WalkingPals.Anim.IDLE || a == WalkingPals.Anim.WALK) 8 else 1 }

    @Test
    fun `frames advance with the emulator's frames and each animation begins on its first`() {
        val an = SpriteIsMeLogic.Animator()
        // Idle from frame 1000: 40 frames of the first, then 6, then 6, then round again.
        assertEquals(Frame(A.IDLE, 0, 0), an.frame(1000, false, 1, false, null, sheets, rows))
        assertEquals(Frame(A.IDLE, 0, 0), an.frame(1039, false, 1, false, null, sheets, rows))
        assertEquals(Frame(A.IDLE, 0, 1), an.frame(1040, false, 1, false, null, sheets, rows))
        assertEquals(Frame(A.IDLE, 0, 2), an.frame(1046, false, 1, false, null, sheets, rows))
        assertEquals(Frame(A.IDLE, 0, 0), an.frame(1052, false, 1, false, null, sheets, rows))
        // Walking starts from ITS first frame, whatever frame the emulator is on.
        assertEquals(Frame(A.WALK, 0, 0), an.frame(1100, true, 1, false, null, sheets, rows))
        assertEquals(Frame(A.WALK, 0, 1), an.frame(1108, true, 1, false, null, sheets, rows))
        assertEquals(Frame(A.WALK, 0, 2), an.frame(1118, true, 1, false, null, sheets, rows))
        assertEquals(Frame(A.WALK, 0, 3), an.frame(1134, true, 1, false, null, sheets, rows))
        assertEquals(Frame(A.WALK, 0, 0), an.frame(1136, true, 1, false, null, sheets, rows), "round again after the 36 frames of the four")
        // Standing again: idle begins over from its first frame.
        assertEquals(Frame(A.IDLE, 0, 0), an.frame(1140, false, 1, false, null, sheets, rows))
    }

    @Test
    fun `faint plays once and holds its last frame`() {
        val an = SpriteIsMeLogic.Animator()
        val dead = lead(hp = 0)
        val f0 = an.frame(50, false, 1, false, dead, sheets, rows)
        assertEquals(WalkingPals.Anim.FAINT, f0!!.anim); assertEquals(0, f0.index)
        assertEquals(1, an.frame(58, false, 1, false, dead, sheets, rows)!!.index)
        assertEquals(3, an.frame(50 + 24, false, 1, false, dead, sheets, rows)!!.index)
        assertEquals(3, an.frame(5000, false, 1, false, dead, sheets, rows)!!.index, "does not loop")
        assertEquals(0, an.frame(5000, false, 1, false, dead, sheets, rows)!!.row, "faint and sleep have one row")
    }

    @Test
    fun `the row is the game's facing, the last known one kept when it is unknown`() {
        val an = SpriteIsMeLogic.Animator()
        assertEquals(0, an.frame(10, true, 1, false, null, sheets, rows)!!.row, "down")
        assertEquals(4, an.frame(11, true, 2, false, null, sheets, rows)!!.row, "up")
        assertEquals(6, an.frame(12, true, 3, false, null, sheets, rows)!!.row, "left")
        assertEquals(2, an.frame(13, true, 4, false, null, sheets, rows)!!.row, "right")
        assertEquals(2, an.frame(14, false, 0, false, null, sheets, rows)!!.row, "unknown keeps right, and idle faces it too")
        assertEquals(2, an.frame(15, false, 9, false, null, sheets, rows)!!.row)
        // A sheet with one row shows it whatever the facing.
        assertEquals(0, an.frame(16, true, 3, false, null, sheets) { 1 }!!.row)
    }

    @Test
    fun `an animation the sprite lacks is borrowed, and a sprite with none shows nothing`() {
        val an = SpriteIsMeLogic.Animator()
        val onlyIdle = mapOf(WalkingPals.Anim.IDLE to sheets.getValue(WalkingPals.Anim.IDLE))
        assertEquals(WalkingPals.Anim.IDLE, an.frame(0, true, 1, false, null, onlyIdle, rows)!!.anim, "no walk sheet: idle")
        assertEquals(WalkingPals.Anim.IDLE, an.frame(0, false, 1, true, null, onlyIdle, rows)!!.anim, "no sleep sheet: idle")
        assertNull(an.frame(0, false, 1, false, null, emptyMap(), rows))
    }

    @Test
    fun `a counter that went backwards starts the animation again`() {
        val an = SpriteIsMeLogic.Animator()
        an.frame(5000, false, 1, false, null, sheets, rows)
        assertEquals(0, an.frame(3, false, 1, false, null, sheets, rows)!!.index)
        assertEquals(1, an.frame(45, false, 1, false, null, sheets, rows)!!.index, "counted from the new start")
    }

    // ------------------------------------------------------------------ the lead, out of the party

    private class Memory(val regions: MutableMap<Long, ByteArray> = HashMap()) {
        fun reader() = MemoryReader { addr, len ->
            val hit = regions.entries.firstOrNull { (base, b) -> addr >= base && addr + len <= base + b.size }
            if (hit == null) ByteArray(0) else hit.value.copyOfRange((addr - hit.key).toInt(), (addr - hit.key).toInt() + len)
        }
    }

    private fun putU32(b: ByteArray, o: Int, v: Long) { for (i in 0 until 4) b[o + i] = (v shr (8 * i)).toByte() }
    private fun putU16(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte() }

    /** One party member as the game keeps it (pid % 24 == 0, so the substructure order is growth, attacks, EVs, misc). */
    private fun mon(species: Int, hp: Int, status: Long = 0, egg: Boolean = false, pid: Long = 24 * 1000): ByteArray {
        val plain = ByteArray(48)
        putU16(plain, 0, species)
        if (egg) putU32(plain, 36 + 4, 1L shl 30)
        val mon = ByteArray(100)
        putU32(mon, 0, pid); putU32(mon, 4, 0)
        for (w in 0 until 12) {
            val v = (plain[w * 4].toLong() and 255) or ((plain[w * 4 + 1].toLong() and 255) shl 8) or ((plain[w * 4 + 2].toLong() and 255) shl 16) or ((plain[w * 4 + 3].toLong() and 255) shl 24)
            putU32(mon, 0x20 + w * 4, v xor pid)
        }
        putU32(mon, 0x50, status)
        mon[0x54] = 30
        putU16(mon, 0x56, hp); putU16(mon, 0x58, 50)
        return mon
    }

    private fun party(map: GameMap, vararg mons: ByteArray?): MemoryReader {
        val m = Memory()
        m.regions[map.partyCount] = byteArrayOf(mons.count { it != null }.toByte())
        val block = ByteArray(600)
        mons.forEachIndexed { i, b -> if (b != null) b.copyInto(block, i * 100) }
        m.regions[map.party] = block
        return m.reader()
    }

    private val map = GameMap.FIRERED_U_V10

    @Test
    fun `the lead is the first Pokemon that is not an egg, with its HP and whether it sleeps`() {
        val r = SpriteLead.read(party(map, mon(25, 30), mon(1, 20)), map)
        assertEquals(SpriteLead.Reading.Found(Lead(25, 30, false)), r)
        // An egg in slot 1: the next slot.
        val e = SpriteLead.read(party(map, mon(0x19C, 10, egg = true), mon(7, 22)), map)
        assertEquals(SpriteLead.Reading.Found(Lead(7, 22, false)), e)
        // Fainted, and asleep (status bits 0-2 are sleep turns).
        assertEquals(SpriteLead.Reading.Found(Lead(25, 0, false)), SpriteLead.read(party(map, mon(25, 0)), map))
        assertEquals(SpriteLead.Reading.Found(Lead(25, 30, true)), SpriteLead.read(party(map, mon(25, 30, status = 3)), map))
        assertEquals(SpriteLead.Reading.Found(Lead(25, 30, false)), SpriteLead.read(party(map, mon(25, 30, status = 8)), map), "poison is not sleep")
        // Only eggs, or nobody yet: none.
        assertEquals(SpriteLead.Reading.None, SpriteLead.read(party(map, mon(0x19C, 10, egg = true)), map))
        assertEquals(SpriteLead.Reading.None, SpriteLead.read(party(map), map))
    }

    @Test
    fun `a party that cannot be read is not a party with nobody in it`() {
        assertEquals(SpriteLead.Reading.Unreadable, SpriteLead.read(MemoryReader { _, _ -> ByteArray(0) }, map))
        val m = Memory(); m.regions[map.partyCount] = byteArrayOf(9)
        assertEquals(SpriteLead.Reading.Unreadable, SpriteLead.read(m.reader(), map), "a count over six is garbage")
        val short = Memory(); short.regions[map.partyCount] = byteArrayOf(2); short.regions[map.party] = ByteArray(100)
        assertEquals(SpriteLead.Reading.Unreadable, SpriteLead.read(short.reader(), map), "the party block is cut short")
    }

    @Test
    fun `the lead says how its game numbers species`() {
        assertEquals(SpriteLead.Reading.Found(Lead(277, 18, false, Dex.GEN3)), SpriteLead.read(party(map, mon(277, 18)), map))
        // A Nat. Dex build keeps the party where FireRed does and numbers on past 411 (GameMap.expandedSpeciesIds).
        val natDex = map.copy(name = "Nat. Dex", expandedSpeciesIds = true)
        assertEquals(SpriteLead.Reading.Found(Lead(412, 18, false, Dex.NAT_DEX)), SpriteLead.read(party(natDex, mon(412, 18)), natDex))
    }

    @Test
    fun `it reads Ruby's IWRAM party and Emerald's the same way, and needs no run`() {
        for (m in listOf(GameMap.RUBY_U, GameMap.EMERALD_U, GameMap.SAPPHIRE_U, GameMap.LEAFGREEN_U, GameMap.FIRERED_U_V11)) {
            val r = SpriteLead.read(party(m, mon(277, 18)), m)
            assertEquals(SpriteLead.Reading.Found(Lead(277, 18, false)), r, m.name)
        }
        assertNotNull(SpriteIsMeSettings.spec)
    }
}
