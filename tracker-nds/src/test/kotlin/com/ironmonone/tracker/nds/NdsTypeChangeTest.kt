package com.ironmonone.tracker.nds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Type changes in DS battles (Blake, 2026-10-06): a battler's types come from the battle's own data on every read, so
 * Conversion, Conversion 2, Color Change, Soak and the rest show on the next read, and the move rows' types follow
 * Normalize, Natural Gift and Techno Blast (NdsMoveTypes) behind the hidden information fence. The constructed
 * HeartGold battle is NdsFormsTest's.
 */
class NdsTypeChangeTest {
    private val roms: File = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

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

    private val rows = arrayOf(
        "137	PORYGON	NORMAL	NORMAL	395	Trace		0",
        "352	KECLEON	NORMAL	NORMAL	440	Color Change		0",
    )

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

    private fun NdsTrackedMon.types() = "${info?.type1}/${info?.type2}"

    /** pokeplatinum and pokeheartgold write every type change into the BattleMon's two type bytes (+0x24, +0x25). */
    @Test
    fun `Gen 4 type changes show on the next read, on the side that changed`() {
        // (mechanic, your side?, type id, its name)
        val cases = listOf(
            listOf("Conversion makes your Porygon Fire", true, 10, "FIRE"),
            listOf("Conversion 2 makes your Porygon Steel", true, 8, "STEEL"),
            listOf("Camouflage makes your Porygon Ground", true, 4, "GROUND"),
            listOf("Color Change makes the Kecleon Electric", false, 13, "ELECTRIC"),
        )
        for (c in cases) {
            val what = c[0] as String; val mine = c[1] as Boolean; val id = c[2] as Int; val name = c[3] as String
            ram.fill(0)
            battle(mon(0x100L, 137), mon(0x9000L, 352), Triple(137, 0, 0), Triple(352, 0, 0))
            val t = NdsTracker(reader(), sidecar(*rows), m)
            val before = t.read()
            assertEquals("NORMAL/NORMAL", before.party[0].types(), what); assertEquals("NORMAL/NORMAL", before.enemy?.types(), what)
            val at = versionRel + (if (mine) m.statStagesPlayer else m.statStagesEnemy) - 0x18
            ram[(at + 0x24).toInt()] = id.toByte(); ram[(at + 0x25).toInt()] = id.toByte()
            val s = t.read()
            assertEquals("$name/$name", if (mine) s.party[0].types() else s.enemy?.types(), what)
            assertEquals("NORMAL/NORMAL", if (mine) s.enemy?.types() else s.party[0].types(), "$what: the other side stays")
        }
    }

    private fun card(ability: String, item: Int = 0, itemName: String = "-", vararg moves: Pair<String, String>) = NdsTrackedMon(
        mon = Gen4.decodeParty(mon(0x100L, 137, item = item))!!, speciesName = "PORYGON", info = null, abilityName = ability, itemName = itemName,
        moves = moves.map { (n, t) -> NdsMoveInfo(n, 90, 100, t, 15, "SPECIAL") },
    )

    /** The fence (hard rule): an opponent's Normalize changes nothing shown until the game has shown it. */
    @Test
    fun `Normalize makes every move Normal, and an opponent's only once it has been shown`() {
        val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) }, null, NdsGameMap.HGSS)
        val c = card("Normalize", 0, "-", "Thunderbolt" to "ELECTRIC", "Struggle" to "NORMAL")
        assertEquals(listOf("NORMAL", "NORMAL"), t.withMoveTypes(c, own = true).moves.map { it.type }, "yours: always known")
        assertEquals(listOf("ELECTRIC", "NORMAL"), t.withMoveTypes(c, own = false).moves.map { it.type }, "the opponent's, not shown yet")
        t.noteShownAbility(137, "Normalize")
        assertEquals(listOf("NORMAL", "NORMAL"), t.withMoveTypes(c, own = false).moves.map { it.type }, "shown")
    }

    @Test
    fun `Techno Blast takes your Drive's type, an opponent's never`() {
        val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) }, null, NdsGameMap.B2W2)
        val c = card("Download", 116, "Douse Drive", "Techno Blast" to "NORMAL")
        assertEquals("WATER", t.withMoveTypes(c, own = true).moves.single().type)
        assertEquals("NORMAL", t.withMoveTypes(c, own = false).moves.single().type, "its held item is never known")
    }

    @Test
    fun `Natural Gift takes your berry's type from the Gen 5 ROM's item data`() {
        for (name in listOf("black2-u.nds", "white2-u.nds")) {
            val rom = Dumps.file(roms, name) ?: continue
            val ng = NdsMoveTypes.naturalGiftTypes(rom, 5)
            assertEquals(listOf("FIRE", "WATER", "ELECTRIC", "NORMAL", "GRASS", "BUG"), listOf(149, 150, 151, 200, 201, 208).map { ng[it] }, name)
            val t = NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) }, null, NdsGameMap.B2W2, rom)
            val c = card("Download", 149, "Cheri Berry", "Natural Gift" to "NORMAL")
            assertEquals("FIRE", t.withMoveTypes(c, own = true).moves.single().type, name)
            assertEquals("NORMAL", t.withMoveTypes(c, own = false).moves.single().type, "$name: an opponent's berry is never known")
        }
        roms.resolve("heartgold-u.nds").takeIf { it.isFile }?.let { assertEquals(emptyMap(), NdsMoveTypes.naturalGiftTypes(it, 4)) }
    }
}
