package com.ironmonone.app.engine

import com.ironmonone.app.RandomizerLog
import com.ironmonone.app.engine.HnsEngineTest.Companion.read
import com.ironmonone.app.engine.HnsEngineTest.Companion.rom
import com.ironmonone.app.engine.HnsEngineTest.Companion.run
import com.ironmonone.app.engine.HnsEngineTest.Companion.vanilla
import com.ironmonone.app.engine.hns.HnsGame
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.app.engine.hns.HnsRandomizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Heart & Soul's item rules (docs/HNS-KAIZO.md, "Next comfort-patch rebuild"), on the real comfort build C993EB6E:
 * the PC item in Elm's lab trash can (randomNonTM, never a TM), the starter's held item (randomItem, TMs allowed),
 * hidden items with the field-item rules, Pickup as UPR does it, and item pools by the run's pool (VANILLA: vanilla
 * Emerald's items, NATDEX: Nat. Dex Emerald 1.2.1's). Without the ROM every test returns early, as HnsEngineTest's do.
 */
class HnsItemsTest {
    private val assets = HnsEngine.folderAssets(File("src/main/assets"))
    private val presets = File("src/main/assets/presets")

    private fun skip(): Boolean = (rom == null).also { if (it) println("HnsItemsTest skipped: no .vendor/hns/hns-kaizo.gba") }

    private val vanillaKeys by lazy { HnsEngine.poolItemKeys(HnsEngine.Pool.VANILLA, assets) }
    private val natDexKeys by lazy { HnsEngine.poolItemKeys(HnsEngine.Pool.NATDEX, assets) }

    private fun id(name: String): Int = vanilla.L.enumValue("ITEM", "ITEM_$name") ?: error("no ITEM_$name")
    private fun key(g: HnsGame, item: Int) = HnsRandomizer.itemKey(g.itemName(item))
    private val tmItems by lazy { vanilla.L.machines.filter { it.kind == "TM" }.map { it.item }.toSet() }
    private val keyPocket by lazy { vanilla.L.enumValue("POCKET", "POCKET_KEY_ITEMS")!! }

    /** Only the item steps, as the Kaizo presets set them: a run in about a second, so many seeds can be read. */
    private val itemsOnly = HnsOptions(
        modeName = "items only", settingsString = "",
        fieldItemsMod = "RANDOM", banBadFieldItems = true,
        randomizeStarterHeldItems = true, banBadStarterHeldItems = true,
        pickupItemsMod = "RANDOM", banBadPickupItems = true,
    )

    private fun itemRun(seed: Long, pool: HnsEngine.Pool, o: HnsOptions = itemsOnly) = HnsEngine.randomize(rom!!, o, seed, pool, assets)

    @Test
    fun `the comfort build carries the trash can's Potion, the starter's empty item slot, the hidden items and Pickup`() {
        if (skip()) return
        assertEquals(id("POTION"), vanilla.labTrashItem)
        assertEquals(2, vanilla.starterItemOperands.size)
        assertTrue(vanilla.starterItemOperands.all { it.value == 0 && it.size == 2 })
        val hidden = vanilla.fieldItems.filter { it.hidden != null }
        assertEquals(144, hidden.size)
        assertTrue(vanilla.fieldItems.count { it.hidden == null } > 100)
        assertEquals(30, vanilla.pickup.size)
        assertEquals(id("POTION"), vanilla.pickup.first().item)
        assertEquals(List(10) { 100 }, (0 until 10).map { b -> vanilla.pickup.sumOf { it.percentages[b] } })
    }

    @Test
    fun `the Kaizo presets randomize field items, Pickup and the starter's item, with bad items banned`() {
        if (skip()) return
        for (p in listOf("RSE NatDex v1.2 Kaizo", "RSE Kaizo")) {
            val o = HnsEngine.readSettings(File(presets, "$p.rnqs"))
            assertEquals("RANDOM", o.fieldItemsMod, p)
            assertTrue(o.banBadFieldItems && o.randomizeStarterHeldItems && o.banBadStarterHeldItems, p)
            assertEquals("RANDOM", o.pickupItemsMod, p)
            assertTrue(o.banBadPickupItems, p)
        }
    }

    @Test
    fun `the PC item is a random non-TM item of the pool, and a Potion where field items stay`() {
        if (skip()) return
        for (pool in HnsEngine.Pool.entries) {
            val keys = if (pool == HnsEngine.Pool.VANILLA) vanillaKeys else natDexKeys
            val seen = HashSet<Int>()
            for (seed in 1L..20L) {
                val g = read(itemRun(seed, pool).rom)
                val it = g.labTrashItem
                assertFalse(it in tmItems, "$pool seed $seed: the PC item is a TM (${g.itemName(it)})")
                assertTrue(it != 0 && g.items[it]!!.pocket != keyPocket, "$pool seed $seed: ${g.itemName(it)}")
                assertTrue(key(g, it) in keys, "$pool seed $seed: ${g.itemName(it)} is not in the pool's game")
                assertFalse(g.itemName(it).endsWith("MAIL") || g.itemName(it) in setOf("NUGGET", "PEARL", "STARDUST", "HEART SCALE"),
                    "$pool seed $seed: a bad item ${g.itemName(it)}")
                seen += it
            }
            assertTrue(seen.size >= 12, "$pool: only ${seen.size} PC items in 20 seeds")
        }
        val kept = read(itemRun(1L, HnsEngine.Pool.NATDEX, itemsOnly.copy(fieldItemsMod = "UNCHANGED")).rom)
        assertEquals(id("POTION"), kept.labTrashItem)
        // The rule reaches the lab's script: the special reads gHnsLabTrashItem, which is what changed.
        val k = read(run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        assertEquals(k.labTrashItem, k.rom.u16(k.L.sym("gHnsLabTrashItem")))
    }

    @Test
    fun `the starter's held item is a non-bad item of the pool, a TM in some seeds, in both lab givemons`() {
        if (skip()) return
        val items = ArrayList<Int>()
        for (seed in 1L..40L) {
            val g = read(itemRun(seed, HnsEngine.Pool.NATDEX).rom)
            val ops = g.starterItemOperands.map { g.rom.u16(it.addr) }
            assertEquals(1, ops.distinct().size, "seed $seed: the two givemons hold $ops")
            val it = ops[0]
            assertTrue(it != 0 && g.items[it]!!.pocket != keyPocket && key(g, it) in natDexKeys, "seed $seed: ${g.itemName(it)}")
            items += it
        }
        assertTrue(items.any { it in tmItems }, "no TM in 40 seeds: ${items.map { vanilla.itemName(it) }}")
        assertTrue(items.distinct().size >= 20, "only ${items.distinct().size} starter items in 40 seeds")
    }

    @Test
    fun `hidden items and item balls follow the field-item rules`() {
        if (skip()) return
        val out = read(run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val before = vanilla.fieldItems.associateBy { it.addr }
        var hiddenChanged = 0
        for (f in out.fieldItems) {
            val v = before.getValue(f.addr)
            val d = vanilla.items[v.item]!!
            if (d.pocket == keyPocket || v.item in vanilla.L.machines.filter { it.kind == "HM" }.map { it.item }) {
                assertEquals(v.item, f.item, "a key item or HM moved at ${f.addr}")
                continue
            }
            assertEquals(v.item in tmItems, f.item in tmItems, "${f.hidden?.map ?: "ball"}: ${vanilla.itemName(v.item)} -> ${out.itemName(f.item)}")
            if (f.hidden != null && f.item != v.item) hiddenChanged++
            // The rest of a hidden item's word (flag, quantity, underfoot) is untouched.
            if (f.hidden != null) assertEquals(vanilla.rom.read(f.addr, 4) and 0x7FFL.inv(), out.rom.read(f.addr, 4) and 0x7FFL.inv())
        }
        assertTrue(hiddenChanged > 100, "only $hiddenChanged hidden items changed")
    }

    @Test
    fun `Pickup gets random items with the game's own percentages`() {
        if (skip()) return
        val out = read(run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        assertEquals(vanilla.pickup.size, out.pickup.size)
        assertTrue(out.pickup.indices.count { out.pickup[it].item != vanilla.pickup[it].item } > 20)
        for (i in out.pickup.indices) assertTrue(out.pickup[i].percentages.contentEquals(vanilla.pickup[i].percentages))
        assertTrue(out.pickup.all { it.item != 0 && key(out, it.item) in natDexKeys })
    }

    /** Every item this run wrote where the input had another: (where, item). */
    private fun written(bytes: ByteArray): List<Pair<String, Int>> {
        val g = read(bytes)
        val out = ArrayList<Pair<String, Int>>()
        val before = vanilla.fieldItems.associateBy { it.addr }
        for (f in g.fieldItems) if (f.item != before[f.addr]?.item) out += "field ${f.addr}" to f.item
        for (i in g.pickup.indices) if (g.pickup[i].item != vanilla.pickup[i].item) out += "pickup $i" to g.pickup[i].item
        if (g.labTrashItem != vanilla.labTrashItem) out += "pc" to g.labTrashItem
        if (g.starterItem != vanilla.starterItem) out += "starter" to g.starterItem
        for (m in g.mons) {
            if (m == null || !m.enabled) continue
            val v = vanilla.mons[m.id]!!
            if (m.itemCommon != v.itemCommon && m.itemCommon != 0) out += "${m.const} common" to m.itemCommon
            if (m.itemRare != v.itemRare && m.itemRare != 0) out += "${m.const} rare" to m.itemRare
        }
        val vt = vanilla.trainers.associateBy { it.id }
        for (t in g.trainers) {
            val had = vt.getValue(t.id).mons.map { it.heldItem }.toSet()
            for (tm in t.mons) if (tm.heldItem != 0 && tm.heldItem !in had) out += "trainer ${t.constName}" to tm.heldItem
        }
        val st = g.L.struct("InGameTrade")
        for (t in g.trades) {
            val now = g.rom.get(st.f("heldItem"), t.addr)
            if (now != vanilla.rom.get(st.f("heldItem"), t.addr) && now != 0) out += "trade ${t.addr}" to now
        }
        return out
    }

    @Test
    fun `a Kaizo seed changes every kind of item, balls, hidden items, the PC item, the starter's, Pickup and wild held items`() {
        if (skip()) return
        val g = read(run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        val before = vanilla.fieldItems.associateBy { it.addr }
        val balls = g.fieldItems.filter { it.hidden == null }
        val hidden = g.fieldItems.filter { it.hidden != null }
        val counts = linkedMapOf(
            "item balls" to (balls.count { it.item != before.getValue(it.addr).item } to balls.size),
            "hidden items" to (hidden.count { it.item != before.getValue(it.addr).item } to hidden.size),
            "PC item" to ((if (g.labTrashItem != vanilla.labTrashItem) 1 else 0) to 1),
            "starter item" to ((if (g.starterItem != vanilla.starterItem) 1 else 0) to 1),
            "Pickup rows" to (g.pickup.indices.count { g.pickup[it].item != vanilla.pickup[it].item } to g.pickup.size),
            "wild held items" to (g.mons.filterNotNull().count { it.enabled && (it.itemCommon != vanilla.mons[it.id]!!.itemCommon || it.itemRare != vanilla.mons[it.id]!!.itemRare) }
                to g.mons.count { it != null && it.enabled }),
        )
        println("Kaizo NATDEX 1234, changed / total: $counts")
        for ((k, v) in counts) assertTrue(v.first > 0, "$k unchanged")
        assertTrue(counts.getValue("hidden items").first > 100 && counts.getValue("item balls").first > 100 && counts.getValue("Pickup rows").first > 20)
    }

    @Test
    fun `the log viewer reads every species' level-up moves and TM moves out of a Heart and Soul log`() {
        if (skip()) return
        val result = run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX)
        val g = read(result.rom)
        val log = RandomizerLog.parse(result.logText)
        val byName = log.pokemon.associateBy { it.name.trim().uppercase() }
        val missing = ArrayList<String>(); val wrong = ArrayList<String>(); val noTms = ArrayList<String>()
        for (m in g.mons) {
            if (m == null || !m.enabled || m.cosmeticOf != 0 || m.id == g.speciesEgg || m.learnset.isEmpty()) continue
            val p = byName[m.displayName.uppercase()] ?: run { missing += m.displayName; null } ?: continue
            val want = m.learnset.map { it.level to g.moveName(it.move) }
            if (p.moves != want) wrong += "${m.displayName}: ${p.moves.size} read, ${want.size} learned"
            if (m.teachable.isNotEmpty() && p.tmsLearnable.isEmpty() && L().machines.any { it.kind == "TM" && it.move in m.teachable }) noTms += m.displayName
        }
        assertTrue(missing.isEmpty(), "not in the log's table: ${missing.take(5)}")
        assertTrue(wrong.isEmpty(), "${wrong.size} species with the wrong level-up moves, e.g. ${wrong.take(5)}")
        val starter = g.mons[g.rom.u16(g.L.sym("sStarterMon"))]!!
        assertTrue(byName.getValue(starter.displayName.uppercase()).moves.isNotEmpty(), "the starter ${starter.displayName}")
        assertTrue(noTms.size < 10, "${noTms.size} species show no TM moves, e.g. ${noTms.take(5)}")
    }

    private fun L() = vanilla.L

    @Test
    fun `no Vanilla seed ever writes an item absent from vanilla Emerald`() {
        if (skip()) return
        for (p in listOf("RSE Kaizo", "RSE Super Kaizo", "RSE NatDex v1.2 Kaizo")) for (seed in listOf(3L, 11L)) {
            val w = written(run(p, seed, HnsEngine.Pool.VANILLA).rom)
            assertTrue(w.size > 300, "$p $seed wrote only ${w.size} items")
            val bad = w.filter { (_, item) -> key(vanilla, item) !in vanillaKeys }
            assertTrue(bad.isEmpty(), "$p seed $seed wrote items vanilla Emerald does not have: ${bad.take(8).map { it.first + " " + vanilla.itemName(it.second) }}")
        }
        assertFalse("XSPDEF" in vanillaKeys)
        assertTrue("XSPATK" in vanillaKeys && "PARALYZEHEAL" in vanillaKeys && "LEEK" in vanillaKeys)
    }

    @Test
    fun `Nat Dex seeds can roll X Sp Def and never a Life Orb`() {
        if (skip()) return
        assertTrue("XSPDEF" in natDexKeys)
        assertFalse("LIFEORB" in natDexKeys || "LIFEORB" in vanillaKeys)
        val life = id("LIFE_ORB")
        val xspdef = id("X_SP_DEF")
        var xs = 0
        for (seed in 1L..4L) {
            val w = written(itemRun(seed, HnsEngine.Pool.NATDEX).rom)
            assertTrue(w.none { it.second == life }, "seed $seed wrote a Life Orb")
            assertTrue(w.all { key(vanilla, it.second) in natDexKeys }, "seed $seed wrote an item outside Nat. Dex Emerald")
            xs += w.count { it.second == xspdef }
        }
        assertTrue(xs > 0, "no X Sp. Def in four Nat. Dex seeds")
        // The whole Kaizo preset too, trainer and wild held items included.
        val w = written(run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX).rom)
        assertTrue(w.none { it.second == life } && w.all { key(vanilla, it.second) in natDexKeys })
    }

    @Test
    fun `every Heart and Soul item the pools name is matched, so no pool item is lost to spelling`() {
        if (skip()) return
        val hnsKeys = vanilla.items.filterNotNull().filter { it.name.isNotEmpty() }.map { HnsRandomizer.itemKey(it.name) }.toSet()
        // Key items Heart & Soul does not have (Emerald's and FRLG's story items) are the only names left over.
        val keyOnly = setOf("ITEMFINDER", "DEVONGOODS", "OAKSPARCEL", "BICYCLE", "REGIONALMINERAL")
        for ((name, keys) in listOf("vanilla" to vanillaKeys, "natdex" to natDexKeys)) {
            val missing = keys - hnsKeys - keyOnly
            assertTrue(missing.isEmpty(), "$name names items Heart & Soul spells otherwise: $missing")
        }
    }

    @Test
    fun `the log lists the PC item, the starter's item, the field and hidden items and Pickup, and the viewer reads them`() {
        if (skip()) return
        val result = run(HnsEngineTest.KAIZO, 1234L, HnsEngine.Pool.NATDEX)
        val out = read(result.rom)
        val log = RandomizerLog.parse(result.logText)
        val items = log.items.toMap()
        assertEquals(listOf("Starter Held Item", "PC Item", "Field Items"), log.items.map { it.first })
        assertEquals(listOf("The starter holds ${out.itemName(out.starterItem)}."), items["Starter Held Item"])
        assertEquals(listOf("Elm's lab trash can: POTION => ${out.itemName(out.labTrashItem)}"), items["PC Item"])
        val field = items.getValue("Field Items")
        assertEquals(144, field.count { "hidden item" in it })
        assertNotNull(field.firstOrNull { it.startsWith("ROUTE 30, hidden item (24, 2): POTION => ") })
        assertTrue(log.pickup.first() == "Level 1-10" && log.pickup.any { it.startsWith("35%: ") })
        assertFalse(result.logText.contains("Hidden items are map data"))
        assertFalse(result.logText.contains("Pickup items were not randomized"))
        assertFalse(result.logText.contains("Starter held items were not set"))
    }
}
