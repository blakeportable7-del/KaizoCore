package com.ironmonone.tracker.nds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Alternate forms on the DS tracker (2026-10-06). A player's report from a HeartGold run: "Ironmon Tracker showed Sandy
 * Cloak Wormadam as being Bug/Grass - it only has that typing as Plant Cloak." The card looked the species up by number
 * and never read the form. Now the form bits in block B pick the form's own entry in the ROM's personal data (NdsForms),
 * and in battle the battle's own type bytes stand (Castform and the other in-battle changes).
 *
 * The form lists are read from the real dumps where they lie (IRONMON_ROMS, or the main checkout's .vendor/roms), not
 * typed in; a dump that is not on this machine skips its part with a line saying so.
 */
class NdsFormsTest {

    private val roms: File = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun rom(name: String): File? = Dumps.file(roms, name).also { if (it == null) println("SKIP: $name is not on this machine") }

    private fun table(name: String, gen: Int): NdsForms.Table? = rom(name)?.let { assertNotNull(NdsForms.read(it, gen), "$name's personal data reads") }

    private fun NdsForms.Table.types(species: Int, form: Int): String {
        val e = if (form == 0) base.getValue(species) else assertNotNull(form(species, form), "$species form $form")
        return typeName(e.type1) + "/" + typeName(e.type2)
    }

    // ------------------------------------------------------------------ the lists, from the ROMs

    /** Generation 4: the five species whose forms the game's code gives entries of their own, every one of them differing. */
    private val gen4Differing = sortedMapOf(386 to listOf(1, 2, 3), 413 to listOf(1, 2), 479 to listOf(1, 2, 3, 4, 5), 487 to listOf(1), 492 to listOf(1))

    @Test
    fun `Platinum and HeartGold give Wormadam's cloaks, Rotom, Giratina, Shaymin and Deoxys entries of their own`() {
        for (name in listOf("platinum-u.nds", "heartgold-u.nds")) {
            val t = table(name, 4) ?: continue
            assertEquals(gen4Differing, t.differing(), "$name: the forms that differ from their first")
            assertEquals("BUG/GRASS", t.types(413, 0))
            assertEquals("BUG/GROUND", t.types(413, 1), "$name: Sandy Cloak")
            assertEquals("BUG/STEEL", t.types(413, 2), "$name: Trash Cloak")
            // Generation 4's rule: the appliance forms keep Electric/Ghost, with their own stats (440 to 520).
            for (f in 1..5) assertEquals("ELECTRIC/GHOST", t.types(479, f), "$name: Rotom form $f")
            assertEquals(440, t.base.getValue(479).bst); assertEquals(520, t.form(479, 1)?.bst)
            assertEquals("GRASS/FLYING", t.types(492, 1), "$name: Shaymin Sky")
            assertEquals("GHOST/DRAGON", t.types(487, 1), "$name: Giratina Origin")
            assertEquals(26, t.form(487, 1)?.ability1, "$name: Origin Giratina's Levitate")
            // Castform, Cherrim and Arceus have no entries: their changes are the game's code, in battle or by the plate.
            for (sp in listOf(351, 421, 493)) assertNull(t.forms[sp], "$name: $sp")
            println("FORMS $name: ${t.differing()}")
        }
    }

    @Test
    fun `Black 2 and White 2 list their forms in the personal data, Rotom's appliances with their second types`() {
        val expected = sortedMapOf(
            351 to listOf(1, 2, 3), 386 to listOf(1, 2, 3), 413 to listOf(1, 2), 479 to listOf(1, 2, 3, 4, 5), 487 to listOf(1),
            492 to listOf(1), 550 to listOf(1), 555 to listOf(1), 641 to listOf(1), 642 to listOf(1), 645 to listOf(1),
            646 to listOf(1, 2), 648 to listOf(1),
        )
        for (name in listOf("black2-u.nds", "white2-u.nds")) {
            val t = table(name, 5) ?: continue
            assertEquals(expected, t.differing(), "$name: the forms that differ from their first")
            assertEquals("BUG/GROUND", t.types(413, 1)); assertEquals("BUG/STEEL", t.types(413, 2))
            assertEquals(listOf("ELECTRIC/FIRE", "ELECTRIC/WATER", "ELECTRIC/ICE", "ELECTRIC/FLYING", "ELECTRIC/GRASS"), (1..5).map { t.types(479, it) })
            assertEquals(listOf("FIRE/FIRE", "WATER/WATER", "ICE/ICE"), (1..3).map { t.types(351, it) }, "$name: Castform")
            assertEquals("FIRE/PSYCHIC", t.types(555, 1), "$name: Zen Mode"); assertEquals(540, t.form(555, 1)?.bst)
            assertEquals("NORMAL/FIGHTING", t.types(648, 1), "$name: Pirouette")
            // Keldeo's Resolute form has an entry that is its first's, so it is not on the list; Arceus has none at all.
            assertEquals(t.base[647], t.form(647, 1)); assertNull(t.forms[493])
            println("FORMS $name: ${t.differing()}")
        }
    }

    // ------------------------------------------------------------------ the card, on a constructed HeartGold party

    private val ram = ByteArray(0x400000)
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L
    private val m = NdsGameMap.HGSS

    private fun putU32(off: Long, v: Long) { val i = off.toInt(); for (k in 0..3) ram[i + k] = (v ushr (8 * k)).toByte() }
    private fun putU16(off: Long, v: Int) { val i = off.toInt(); ram[i] = v.toByte(); ram[i + 1] = (v ushr 8).toByte() }
    private fun put(off: Long, bytes: ByteArray) = bytes.copyInto(ram, off.toInt())

    private fun mon(pid: Long, species: Int, form: Int = 0, ability: Int = 107, item: Int = 0) =
        Gen4.encodeParty(pid, species = species, level = 20, curHp = 40, maxHp = 40, moves = listOf(33, 0, 0, 0), form = form, abilityId = ability, heldItem = item)

    /** A run's sidecar as the engine writes it: these rows, the species' first form only. */
    private fun sidecar(vararg rows: String): File =
        File.createTempFile("forms", ".species.tsv").apply { deleteOnExit(); writeText(rows.joinToString("\n") + "\n") }

    private val rows = arrayOf(
        "413\tWORMADAM\tBUG\tGRASS\t424\tAnticipation\t\t0",
        "479\tROTOM\tELECTRIC\tGHOST\t440\tLevitate\t\t0",
        "493\tARCEUS\tFIRE\tROCK\t720\tMultitype\t\t5",
        "351\tCASTFORM\tNORMAL\tNORMAL\t420\tForecast\t\t0",
    )

    private fun party(vararg mons: ByteArray) {
        putU32(m.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        mons.forEachIndexed { i, b -> put(versionRel + m.playerBase + i * 236L, b); put(versionRel + m.playerBattleBase + i * 236L, b) }
        putU16(m.battleStatus, 0x2800)
    }

    private fun reader() = NdsMemoryReader { addr, len ->
        val off = (addr - 0x02000000L).toInt()
        if (off >= 0 && off + len <= ram.size) ram.copyOfRange(off, off + len) else ByteArray(0)
    }

    private fun NdsTrackedMon.types() = "${info?.type1}/${info?.type2}"

    @Test
    fun `a Sandy Cloak and a Trash Cloak Wormadam show Bug-Ground and Bug-Steel, read from the ROM`() {
        val hg = rom("heartgold-u.nds") ?: return
        party(mon(0x100L, 413, form = 0), mon(0x101L, 413, form = 1), mon(0x102L, 413, form = 2))
        val s = NdsTracker(reader(), sidecar(*rows), m, hg).read()
        assertEquals(listOf(0, 1, 2), s.party.map { it.mon.form }, "the form bits decode")
        assertEquals(listOf("BUG/GRASS", "BUG/GROUND", "BUG/STEEL"), s.party.map { it.types() })
        assertEquals(listOf(424, 424, 424), s.party.map { it.info?.bst })
        assertEquals(listOf("WORMADAM", "WORMADAM", "WORMADAM"), s.party.map { it.speciesName })
        // Without the ROM every cloak reads its species' row: the bug as reported.
        val old = NdsTracker(reader(), sidecar(*rows), m).read()
        assertEquals(listOf("BUG/GRASS", "BUG/GRASS", "BUG/GRASS"), old.party.map { it.types() })
    }

    @Test
    fun `a randomized form entry is shown as the ROM has it, never a typing kept in the app`() {
        val hg = rom("heartgold-u.nds") ?: return
        party(mon(0x100L, 413, form = 1), mon(0x101L, 479, form = 1))
        val t = NdsTracker(reader(), sidecar(*rows), m, hg)
        // The ROM's own entries, as a randomizer may have left them: Sandy Cloak Fire/Water, Heat Rotom Dragon/Dark.
        val files = NdsRomFile(hg).use { NdsForms.narcFiles(it.file("a/0/0/2")!!) }.map { it.copyOf() }
        files[499][6] = 10; files[499][7] = 11
        files[503][6] = 16; files[503][7] = 17
        files[503][0] = 100
        t.forms = NdsForms.table(files, 4)
        val s = t.read()
        assertEquals("FIRE/WATER", s.party[0].types())
        assertEquals("DRAGON/DARK", s.party[1].types())
        assertEquals(520 - 50 + 100, s.party[1].info?.bst)
    }

    @Test
    fun `Arceus with Multitype is its plate's type, Normal without one, and its own row with another ability`() {
        val hg = rom("heartgold-u.nds") ?: return
        party(mon(0x100L, 493, form = 10, ability = 121, item = 298), mon(0x101L, 493, ability = 121), mon(0x102L, 493, ability = 46, item = 298))
        val s = NdsTracker(reader(), sidecar(*rows), m, hg).read()
        assertEquals(listOf("FIRE/FIRE", "NORMAL/NORMAL", "FIRE/ROCK"), s.party.map { it.types() })
    }

    /** A battle on the field: your [own] and the enemy's [foe] in both parties, each BattleMon holding [ownBattle] and [foeBattle] (species, type, type). */
    private fun battle(own: ByteArray, foe: ByteArray, ownBattle: Triple<Int, Int, Int>, foeBattle: Triple<Int, Int, Int>) {
        party(own)
        put(versionRel + m.enemyBase, foe)
        putU32(versionRel + m.playerBattleMonPid, Gen4.decodeParty(own)!!.pid)
        putU32(versionRel + m.enemyBattleMonPid, Gen4.decodeParty(foe)!!.pid)
        for ((stages, b) in listOf(m.statStagesPlayer to ownBattle, m.statStagesEnemy to foeBattle)) {
            val mon = versionRel + stages - 0x18
            putU16(mon, b.first)
            byteArrayOf(6, 6, 6, 6, 6, 6, 6, 6).copyInto(ram, (versionRel + stages).toInt())
            ram[(mon + 0x24).toInt()] = b.second.toByte(); ram[(mon + 0x25).toInt()] = b.third.toByte()
        }
        putU16(m.battleStatus, 0x2100)
    }

    @Test
    fun `in a Gen 4 battle each side shows the types its BattleMon holds, Castform in the sun as Fire`() {
        val hg = rom("heartgold-u.nds")
        battle(mon(0x100L, 351, ability = 59), mon(0x9000L, 351, ability = 59), Triple(351, 10, 10), Triple(351, 11, 11))
        val s = NdsTracker(reader(), sidecar(*rows), m, hg).read()
        assertEquals("WATER/WATER", s.enemy?.types(), "the opponent's Castform in the rain")
        assertEquals("FIRE/FIRE", s.playerActive?.types())
        assertEquals("FIRE/FIRE", s.party[0].types(), "the card of the Pokemon on the field")
        // A BattleMon holding another species (a Transform, or the last battle's) is not this Pokemon's: its row stands.
        putU16(versionRel + m.statStagesEnemy - 0x18, 132)
        assertEquals("NORMAL/NORMAL", NdsTracker(reader(), sidecar(*rows), m, hg).read().enemy?.types())
    }

    @Test
    fun `in a Gen 4 battle a Sandy Cloak Wormadam keeps Bug-Ground`() {
        val hg = rom("heartgold-u.nds") ?: return
        battle(mon(0x100L, 413, form = 1), mon(0x9000L, 413, form = 2), Triple(413, 6, 4), Triple(413, 6, 8))
        val s = NdsTracker(reader(), sidecar(*rows), m, hg).read()
        assertEquals("BUG/GROUND", s.party[0].types())
        assertEquals("BUG/STEEL", s.enemy?.types())
        assertEquals(424, s.enemy?.info?.bst)
    }

    // ------------------------------------------------------------------ Gen 5, on the Black 2 rival battle dump

    @Test
    fun `in a Gen 5 battle the battle data's types stand, at +0xF8 of each battler's block`() {
        val f = Dumps.dump("b2-rand-rival-battle.bin") ?: return
        val ram = f.readBytes()
        val r = NdsMemoryReader { addr, len ->
            val off = addr - 0x02000000L
            if (off < 0 || off >= ram.size) ByteArray(0) else ram.copyOfRange(off.toInt(), minOf(ram.size, (off + len).toInt()))
        }
        // A sidecar that says otherwise, to show where the card's types come from in battle.
        val side = sidecar("483\tDIALGA\tNORMAL\tNORMAL\t680\tPressure\t\t5", "246\tLARVITAR\tNORMAL\tNORMAL\t300\tGuts\t\t5")
        val map = assertNotNull(NdsGameMap.detect(r))
        val s = NdsTracker(r, side, map).read()
        assertEquals(246, s.enemy?.mon?.species)
        assertEquals("ROCK/GROUND", s.enemy?.types(), "Larvitar's battle data")
        assertEquals("STEEL/DRAGON", s.playerActive?.types(), "Dialga's battle data")
        assertEquals("STEEL/DRAGON", s.party[0].types())
        // The opponent's type bytes, changed in memory only (the dump holds two copies of its block): the card follows them.
        for (larvitar in listOf(0x225B408 - 0x02000000, 0x225B850 - 0x02000000)) { ram[larvitar + 0xF8] = 9; ram[larvitar + 0xF9] = 9 }
        assertEquals("FIRE/FIRE", NdsTracker(r, side, map).read().enemy?.types())
    }
}
