package com.ironmonone.app.engine.hns

/**
 * The Heart & Soul data the randomizer changes, read from the ROM through the layout into plain objects, then written
 * back by [write]. Nothing is written while it is read, so a failure part way through leaves the input untouched.
 */
class HnsGame(val rom: HnsRom, speciesFile: HnsSpeciesFile) {
    val L: HnsLayout = rom.layout

    // ---------------------------------------------------------------- species

    class LevelMove(var level: Int, var move: Int)

    /** One evolution; [conds] are its EvolutionParam rows (condition, arg1, arg2, arg3) without the terminator. */
    class Evo(
        var method: Int, var param: Int, var target: Int, var conds: MutableList<IntArray>,
        /** The build's EvolutionParam pointer, written back while [conds] are unchanged. */
        val paramsPtr: Int,
        /** Whether [conds] were read to their terminator; only then may they be rewritten. */
        val paramsOk: Boolean,
        var condsChanged: Boolean = false,
    ) {
        fun copy() = Evo(method, param, target, conds.map { it.copyOf() }.toMutableList(), paramsPtr, paramsOk, condsChanged)
    }

    class Mon(val id: Int) {
        var const = ""
        var name = ""
        var displayName = ""
        var natDex = 0
        var gen13 = false
        var enabled = false
        var randomizerMode = 0
        var restricted = false; var sub = false; var mythical = false; var ultraBeast = false; var paradox = false
        var battleForm = false
        var regional = false
        /** HP, ATK, DEF, SPE, SPA, SPD, the order SpeciesInfo stores them. */
        val stats = IntArray(6)
        val origStats = IntArray(6)
        val types = IntArray(2)
        val abilities = IntArray(3)
        var growth = 0
        var catchRate = 0
        var itemCommon = 0
        var itemRare = 0
        var learnsetPtr = 0
        var teachablePtr = 0
        var eggPtr = 0
        var evoPtr = 0
        var learnset: MutableList<LevelMove> = ArrayList()
        var teachable: MutableList<Int> = ArrayList()
        var eggMoves: MutableList<Int> = ArrayList()
        var evos: MutableList<Evo> = ArrayList()
        var evosChanged = false
        var learnsetChanged = false
        var teachableChanged = false
        var eggChanged = false
        /** The species this one is an exact copy of (same Dex number, stats, types and abilities): a cosmetic form. */
        var cosmeticOf = 0
        /** The lowest species id with this Dex number: the base its forms take their growth rate from. */
        var baseOf = 0
        /** Placeable by the randomizer: a real, showable species that is not a battle-only form. */
        var eligible = false

        val bst: Int get() = stats.sum()
        val origBst: Int get() = origStats.sum()
        val isLegendary: Boolean get() = restricted || sub || mythical
        val isStrongLegendary: Boolean get() = restricted || mythical
        fun hasType(t: Int) = types[0] == t || types[1] == t
    }

    val numSpecies = L.const("NUM_SPECIES")
    val speciesEgg = L.const("SPECIES_EGG")
    val levelUpEnd = L.const("LEVEL_UP_MOVE_END")
    val moveUnavailable = L.const("MOVE_UNAVAILABLE")
    val evolutionsEnd = L.const("EVOLUTIONS_END")
    /** CONDITIONS_END follows the last IF_ condition in the enum (include/constants/pokemon.h). */
    val conditionsEnd = (L.enum("IF").values.maxOrNull() ?: 38) + 1
    val maxLevelUpMoves = 40

    val mons: Array<Mon?> = arrayOfNulls(numSpecies)

    // ---------------------------------------------------------------- moves, items, abilities, types

    class MoveData(val id: Int) {
        var name = ""
        var power = 0; var type = 0; var accuracy = 0; var pp = 0; var category = 0; var effect = 0
        var strikeCount = 1; var multiHit = false; var randomizerInvalid = false
        var changed = false
    }

    val movesCount = L.const("MOVES_COUNT")
    val moves: Array<MoveData?> = arrayOfNulls(L.const("MOVES_COUNT_ALL"))

    class ItemData(val id: Int) {
        var name = ""; var pocket = 0; var holdEffect = 0; var price = 0; var importance = 0
    }

    val itemsCount = L.const("ITEMS_COUNT")
    val items: Array<ItemData?> = arrayOfNulls(itemsCount)
    val abilityNames: Array<String> = Array(L.const("ABILITIES_COUNT")) { "" }
    val typeNames: Map<Int, String>

    // ---------------------------------------------------------------- machines

    /** TM and HM moves in machine order (TM01.., then HM01..) as this ROM's gTMHMItemMoveIds holds them, and their items. */
    val machineMoves: IntArray = IntArray(L.machines.size) { rom.get("TmHmIndexKey", L.rec("gTMHMItemMoveIds", it + 1), "moveId") }
    val machineItems: IntArray = L.machines.map { it.item }.toIntArray()
    val tmCount = L.machines.count { it.kind == "TM" }
    val origMachineMoves: IntArray = machineMoves.copyOf()

    // ---------------------------------------------------------------- trainers

    class TrainerMonData(val raw: ByteArray) {
        var species = 0; var level = 0; var heldItem = 0
        var reset = false
        var shiny = false
        var origSpecies = 0
        var origLevel = 0
    }

    class TrainerData(val id: Int, val addr: Int) {
        var constName = ""
        var trainerClass = 0
        var name = ""
        var origName = ""
        var className = ""
        var origClassName = ""
        var partySize = 0
        var poolSize = 0
        var partyPtr = 0
        var battleType = 0
        var mons: MutableList<TrainerMonData> = ArrayList()
        var partyGrew = false
        var doubleSet = false
        var smartAi = false
        var tag = ""
        /** The party slot that carries the player's starter in a rival battle, or -1. */
        var carriedSlot = -1
    }

    val trainers = ArrayList<TrainerData>()
    val trainerClassNames = HashMap<Int, String>()
    val origTrainerClassNames = HashMap<Int, String>()

    // ---------------------------------------------------------------- wild

    class Slot(val addr: Int, var species: Int, var minLevel: Int, var maxLevel: Int)

    /** One encounter table: a header's encounter type at one time of day, read once even when two times share it. */
    class WildSet(val name: String, val rate: Int, val slots: List<Slot>, val areaKey: String, val arrayAddr: Int)

    val wildSets = ArrayList<WildSet>()

    // ---------------------------------------------------------------- trades

    class Trade(val addr: Int) {
        var nickname = ""; var species = 0; var requested = 0; var otName = ""; var otId = 0L
        var origNickname = ""; var origSpecies = 0; var origRequested = 0; var origOtName = ""; var origOtId = 0L
    }

    val trades = ArrayList<Trade>()

    // ---------------------------------------------------------------- field items

    /** A finditem script's item operand, or a map's hidden item ([hidden]: its item bits in a BgEvent's u32 at [addr]). */
    class FieldItem(val addr: Int, var item: Int, val origItem: Int, val hidden: HnsLayout.HiddenItem? = null)

    /** The item balls, then the hidden items. */
    val fieldItems = ArrayList<FieldItem>()

    // ---------------------------------------------------------------- Pickup, the lab trash can, the starter's item

    /** One row of sPickupTable: its item; the percentages per level band stay as the game has them (UPR keeps them). */
    class PickupRow(val addr: Int, var item: Int, val origItem: Int, val percentages: IntArray)

    val pickup = ArrayList<PickupRow>()

    /** gHnsLabTrashItem[0], the item in Elm's lab trash can (the PC's starting item), or null on a build without it. */
    val labTrashAddr: Int? = if (L.hasSym("gHnsLabTrashItem")) L.sym("gHnsLabTrashItem") else null
    var labTrashItem: Int = labTrashAddr?.let { rom.u16(it) } ?: 0
    val origLabTrashItem: Int = labTrashItem

    /** The item operands of the lab's starter givemons (every starter takes the same one), and the item they hold. */
    val starterItemOperands: List<HnsLayout.Operand> = L.scriptMons
        .filter { it.kind == "givemon" && it.label.contains("GiveStarter") }.mapNotNull { it.operands["item"] }
    var starterItem: Int = starterItemOperands.firstOrNull()?.let { rom.u16(it.addr) } ?: 0
    val origStarterItem: Int = starterItem

    // ---------------------------------------------------------------- reading

    init {
        val sp = L.struct("SpeciesInfo")
        val byId = speciesFile.species.associateBy { it.id }
        val firstOfDex = HashMap<Int, Int>()
        for (id in 0 until numSpecies) {
            val r = L.rec("gSpeciesInfo", id)
            val m = Mon(id)
            m.stats[0] = rom.get(sp.f("baseHP"), r); m.stats[1] = rom.get(sp.f("baseAttack"), r)
            m.stats[2] = rom.get(sp.f("baseDefense"), r); m.stats[3] = rom.get(sp.f("baseSpeed"), r)
            m.stats[4] = rom.get(sp.f("baseSpAttack"), r); m.stats[5] = rom.get(sp.f("baseSpDefense"), r)
            m.stats.copyInto(m.origStats)
            m.enabled = m.stats[0] > 0 && id != 0
            m.types[0] = rom.elem("SpeciesInfo", r, "types", 0); m.types[1] = rom.elem("SpeciesInfo", r, "types", 1)
            for (k in 0 until 3) m.abilities[k] = rom.elem("SpeciesInfo", r, "abilities", k)
            m.growth = rom.get(sp.f("growthRate"), r)
            m.catchRate = rom.get(sp.f("catchRate"), r)
            m.itemCommon = rom.get(sp.f("itemCommon"), r)
            m.itemRare = rom.get(sp.f("itemRare"), r)
            m.natDex = rom.get(sp.f("natDexNum"), r)
            m.randomizerMode = rom.get(sp.f("randomizerMode"), r)
            m.restricted = rom.get(sp.f("isRestrictedLegendary"), r) != 0
            m.sub = rom.get(sp.f("isSubLegendary"), r) != 0
            m.mythical = rom.get(sp.f("isMythical"), r) != 0
            m.ultraBeast = rom.get(sp.f("isUltraBeast"), r) != 0
            m.paradox = rom.get(sp.f("isParadox"), r) != 0
            m.battleForm = listOf("isMegaEvolution", "isGigantamax", "isPrimalReversion", "isUltraBurst", "isTotem", "isTeraForm")
                .any { sp.fields[it]?.let { f -> rom.get(f, r) != 0 } == true }
            m.regional = listOf("isAlolanForm", "isGalarianForm", "isHisuianForm", "isPaldeanForm")
                .any { sp.fields[it]?.let { f -> rom.get(f, r) != 0 } == true }
            m.name = rom.inlineText("SpeciesInfo", r, "speciesName").trim()
            m.const = byId[id]?.const ?: ""
            m.gen13 = byId[id]?.gen13Scope == true
            m.learnsetPtr = rom.get(sp.f("levelUpLearnset"), r)
            m.teachablePtr = rom.get(sp.f("teachableLearnset"), r)
            m.eggPtr = rom.get(sp.f("eggMoveLearnset"), r)
            m.evoPtr = rom.get(sp.f("evolutions"), r)
            if (m.enabled) {
                m.learnset = readLearnset(m.learnsetPtr)
                m.teachable = readMoveList(m.teachablePtr, 1000)
                m.eggMoves = readMoveList(m.eggPtr, 200)
                m.evos = readEvos(m.evoPtr)
                val base = firstOfDex.getOrPut(if (m.natDex == 0) -id else m.natDex) { id }
                m.baseOf = base
            }
            mons[id] = m
        }
        // Cosmetic forms: an exact copy of an earlier species of the same Dex number.
        val seen = HashMap<String, Int>()
        for (m in mons) {
            if (m == null || !m.enabled || m.natDex == 0) continue
            val key = "${m.natDex}|${m.stats.joinToString(",")}|${m.types.joinToString(",")}|${m.abilities.joinToString(",")}"
            val prior = seen[key]
            if (prior != null) m.cosmeticOf = prior else seen[key] = m.id
        }
        // Placeable: shown in the game and not a battle-only form. HnS marks some base species MON_RANDOMIZER_INVALID by
        // hand (Salamence, Gumshoos, Appletun), so that mark only counts against a form, never the first of its Dex number.
        val invalid = L.constants["MON_RANDOMIZER_INVALID"]?.toInt() ?: 3
        for (m in mons) {
            if (m == null || !m.enabled || m.id == speciesEgg || m.natDex == 0) continue
            val isForm = m.baseOf != m.id && !m.regional
            m.eligible = !m.battleForm && m.cosmeticOf == 0 && !(m.randomizerMode == invalid && (isForm || m.regional))
        }
        // Unique names for the log: a form that shares its species' name takes the end of its constant.
        val nameSeen = HashMap<String, Mon>()
        for (m in mons) {
            if (m == null || !m.enabled) continue
            val first = nameSeen[m.name.uppercase()]
            if (first == null) {
                m.displayName = m.name; nameSeen[m.name.uppercase()] = m
            } else {
                // The constant's tail past the species' own name: SPECIES_LYCANROC_DUSK reads LYCANROC-DUSK.
                val norm = { s: String -> s.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_') }
                var tail = m.const.removePrefix("SPECIES_")
                listOf(first.const.removePrefix("SPECIES_") + "_", norm(m.name) + "_", norm(m.name.substringBefore('-')) + "_")
                    .filter { it.length > 1 && tail.startsWith(it) }.maxByOrNull { it.length }?.let { tail = tail.removePrefix(it) }
                m.displayName = m.name + "-" + tail.replace('_', ' ')
            }
        }

        // Moves.
        val mi = L.struct("MoveInfo")
        for (id in 1 until moves.size) {
            val r = L.rec("gMovesInfo", id)
            val d = MoveData(id)
            d.name = rom.text(rom.get(mi.f("name"), r), 24).trim().uppercase()
            d.power = rom.get(mi.f("power"), r); d.type = rom.get(mi.f("type"), r)
            d.accuracy = rom.get(mi.f("accuracy"), r); d.pp = rom.get(mi.f("pp"), r)
            d.category = rom.get(mi.f("category"), r); d.effect = rom.get(mi.f("effect"), r)
            d.strikeCount = maxOf(1, rom.get(mi.f("strikeCount"), r)); d.multiHit = rom.get(mi.f("multiHit"), r) != 0
            d.randomizerInvalid = mi.fields["randomizerInvalid"]?.let { rom.get(it, r) != 0 } ?: false
            moves[id] = d
        }
        // Items.
        val ii = L.struct("ItemInfo")
        for (id in 0 until itemsCount) {
            val r = L.rec("gItemsInfo", id)
            val d = ItemData(id)
            val np = rom.get(ii.f("name"), r)
            d.name = if (rom.isRomPtr(np)) rom.text(np, 24).trim().uppercase() else ""
            d.pocket = rom.get(ii.f("pocket"), r); d.holdEffect = rom.get(ii.f("holdEffect"), r)
            d.price = rom.get(ii.f("price"), r); d.importance = rom.get(ii.f("importance"), r)
            items[id] = d
        }
        for (id in abilityNames.indices) abilityNames[id] = rom.inlineText("AbilityInfo", L.rec("gAbilitiesInfo", id), "name").trim().uppercase()
        typeNames = L.enum("TYPE").entries.filter { !it.key.startsWith("TYPE_SIDE_") }.associate { it.value to it.key.removePrefix("TYPE_") }

        // Trainer classes and trainers (normal difficulty; HnS fills no other).
        for (c in 0 until L.tables.getValue("gTrainerClasses").count) {
            val n = rom.inlineText("TrainerClass", L.rec("gTrainerClasses", c), "name").trim()
            trainerClassNames[c] = n; origTrainerClassNames[c] = n
        }
        val tNames = L.enum("TRAINER").entries.associate { it.value to it.key }
        val count = L.const("TRAINERS_COUNT")
        val normal = L.const("DIFFICULTY_NORMAL")
        val ts = L.struct("Trainer")
        val ms = L.struct("TrainerMon")
        for (tid in 1 until count) {
            val a = L.rec("gTrainers", normal * count + tid)
            val t = TrainerData(tid, a)
            t.partySize = rom.get(ts.f("partySize"), a)
            t.partyPtr = rom.get(ts.f("party"), a)
            if (t.partySize == 0 || !rom.isRomPtr(t.partyPtr)) continue
            t.poolSize = rom.get(ts.f("poolSize"), a)
            t.trainerClass = rom.get(ts.f("trainerClass"), a)
            t.battleType = rom.get(ts.f("battleType"), a)
            t.constName = tNames[tid] ?: ""
            t.name = rom.inlineText("Trainer", a, "trainerName"); t.origName = t.name
            val n = maxOf(t.partySize, t.poolSize)
            for (k in 0 until n) {
                val ma = t.partyPtr + k * ms.size
                val raw = ByteArray(ms.size) { rom.u8(ma + it).toByte() }
                val tm = TrainerMonData(raw)
                tm.species = rom.get(ms.f("species"), ma); tm.level = rom.get(ms.f("lvl"), ma)
                tm.heldItem = rom.get(ms.f("heldItem"), ma); tm.shiny = rom.get(ms.f("isShiny"), ma) != 0
                tm.origSpecies = tm.species; tm.origLevel = tm.level
                t.mons.add(tm)
            }
            trainers.add(t)
        }

        readWild()
        readTrades()
        readFieldItems()
        readHiddenItems()
        readPickup()
    }

    private fun readLearnset(p: Int): MutableList<LevelMove> {
        val out = ArrayList<LevelMove>()
        if (!rom.isRomPtr(p)) return out
        val st = L.struct("LevelUpMove")
        for (k in 0 until 200) {
            val a = p + k * st.size
            val mv = rom.get(st.f("move"), a)
            if (mv == levelUpEnd) break
            out.add(LevelMove(rom.get(st.f("level"), a), mv))
        }
        return out
    }

    private fun readMoveList(p: Int, max: Int): MutableList<Int> {
        val out = ArrayList<Int>()
        if (!rom.isRomPtr(p)) return out
        for (k in 0 until max) {
            val v = rom.u16(p + 2 * k)
            if (v == moveUnavailable) break
            out.add(v)
        }
        return out
    }

    private fun readEvos(p: Int): MutableList<Evo> {
        val out = ArrayList<Evo>()
        if (!rom.isRomPtr(p)) return out
        val st = L.struct("Evolution")
        for (k in 0 until 100) {
            val a = p + k * st.size
            val method = rom.get(st.f("method"), a)
            if (method == evolutionsEnd) break
            val params = rom.get(st.f("params"), a)
            val conds = ArrayList<IntArray>()
            var ok = true
            if (rom.isRomPtr(params)) {
                ok = false
                for (j in 0 until 16) {
                    val c = rom.u16(params + 8 * j)
                    if (c == conditionsEnd) { ok = true; break }
                    conds.add(intArrayOf(c, rom.u16(params + 8 * j + 2), rom.u16(params + 8 * j + 4), rom.u16(params + 8 * j + 6)))
                }
            }
            // A params array with no terminator where we expect one is not ours to rewrite: keep it as it is.
            out.add(Evo(method, rom.get(st.f("param"), a), rom.get(st.f("targetSpecies"), a), conds, params, ok))
        }
        return out
    }

    private fun readWild() {
        val hs = L.struct("WildPokemonHeader")
        val et = L.struct("WildEncounterTypes")
        val wp = L.struct("WildPokemon")
        val wi = L.struct("WildPokemonInfo")
        val times = listOf("Morning", "Day", "Evening", "Night")
        val types = listOf(
            Triple("landMonsInfo", "Grass/Cave", "LAND_WILD_COUNT"), Triple("waterMonsInfo", "Surfing", "WATER_WILD_COUNT"),
            Triple("rockSmashMonsInfo", "Rock Smash", "ROCK_WILD_COUNT"), Triple("fishingMonsInfo", "Fishing", "FISH_WILD_COUNT"),
            Triple("hiddenMonsInfo", "Hidden", "HIDDEN_WILD_COUNT"),
        )
        val mapNames = L.enum("MAP").entries.groupBy({ it.value }, { it.key })
        val seenArrays = HashSet<Int>()
        val nTimes = L.const("TIMES_OF_DAY_COUNT")
        for (i in 0 until L.tables.getValue("gWildMonHeaders").count) {
            val h = L.rec("gWildMonHeaders", i)
            val g = rom.get(hs.f("mapGroup"), h)
            val n = rom.get(hs.f("mapNum"), h)
            if (g == 0xFF) break
            val mapName = mapNames[(g shl 8) or n]?.minByOrNull { it.length }?.let { prettyMap(it) } ?: "MAP $g.$n"
            for (t in 0 until nTimes) {
                val e = h + hs.f("encounterTypes").offset + t * et.size
                for ((field, label, countConst) in types) {
                    val info = rom.get(et.f(field), e)
                    if (!rom.isRomPtr(info)) continue
                    val arr = rom.get(wi.f("wildPokemon"), info)
                    if (!rom.isRomPtr(arr) || !seenArrays.add(arr)) continue
                    val rate = rom.get(wi.f("encounterRate"), info)
                    val slots = (0 until L.const(countConst)).map { k ->
                        val a = arr + k * wp.size
                        Slot(a, rom.get(wp.f("species"), a), rom.get(wp.f("minLevel"), a), rom.get(wp.f("maxLevel"), a))
                    }
                    wildSets.add(WildSet("$mapName $label" + (if (nTimes > 1) ", " + times.getOrElse(t) { "Time $t" } else ""),
                        rate, slots, "$i|$field", arr))
                }
            }
        }
    }

    private fun readTrades() {
        val st = L.struct("InGameTrade")
        for (i in 0 until (L.tables["sIngameTrades"]?.count ?: 0)) {
            val a = L.rec("sIngameTrades", i)
            val t = Trade(a)
            t.nickname = rom.inlineText("InGameTrade", a, "nickname"); t.origNickname = t.nickname
            t.species = rom.get(st.f("species"), a); t.origSpecies = t.species
            t.requested = rom.get(st.f("requestedSpecies"), a); t.origRequested = t.requested
            t.otName = rom.inlineText("InGameTrade", a, "otName"); t.origOtName = t.otName
            t.otId = rom.read(a + st.f("otId").offset, 4); t.origOtId = t.otId
            if (t.species in 1 until numSpecies) trades.add(t)
        }
    }

    /**
     * The item balls: every `finditem ITEM, amount` in the scripts, which assembles to setorcopyvar VAR_0x8000, item;
     * setorcopyvar VAR_0x8001, amount; callstd STD_FIND_ITEM (asm/macros/event.inc). Eight fixed bytes and an item id
     * in range; a finditem that takes its item from a variable (the item-ball template script) has no constant to
     * change and is left out. Hidden items are map data the layout does not export (docs/HNS-KAIZO.md).
     */
    private fun readFieldItems() {
        val b = rom.bytes
        var o = (L.scriptDataBase - L.romBase).coerceAtLeast(0)
        while (o + 12 <= b.size) {
            if (b[o] == 0x1A.toByte() && b[o + 1] == 0x00.toByte() && b[o + 2] == 0x80.toByte() &&
                b[o + 5] == 0x1A.toByte() && b[o + 6] == 0x01.toByte() && b[o + 7] == 0x80.toByte() &&
                b[o + 10] == 0x09.toByte() && b[o + 11] == 0x01.toByte()
            ) {
                val item = (b[o + 3].toInt() and 0xFF) or ((b[o + 4].toInt() and 0xFF) shl 8)
                val amount = (b[o + 8].toInt() and 0xFF) or ((b[o + 9].toInt() and 0xFF) shl 8)
                if (item in 1 until itemsCount && amount in 1..99) fieldItems.add(FieldItem(L.romBase + o + 3, item, item))
                o += 12
            } else o++
        }
    }

    private fun readHiddenItems() {
        val (shift, width) = L.hiddenItemBits
        for (h in L.hiddenItems) {
            val item = ((rom.read(h.addr, 4) ushr shift) and ((1L shl width) - 1)).toInt()
            if (item in 1 until itemsCount) fieldItems.add(FieldItem(h.addr, item, item, h))
        }
    }

    private fun readPickup() {
        if (!L.hasSym("sPickupTable")) return
        val st = L.struct("PickupItem")
        val pct = st.f("percentage")
        for (i in 0 until (L.tables["sPickupTable"]?.count ?: 0)) {
            val a = L.rec("sPickupTable", i)
            val item = rom.get(st.f("itemId"), a)
            pickup.add(PickupRow(a, item, item, IntArray(pct.count) { rom.u8(a + pct.offset + it) }))
        }
    }

    // ---------------------------------------------------------------- writing

    /** Writes everything the randomizer changed. */
    fun write() {
        val sp = L.struct("SpeciesInfo")
        // Lists shared by several species are written once and shared again.
        val learnsetDone = HashMap<List<Any>, Int>()
        val listDone = HashMap<List<Int>, Int>()
        for (m in mons) {
            if (m == null || !m.enabled) continue
            val r = L.rec("gSpeciesInfo", m.id)
            rom.set(sp.f("baseHP"), r, m.stats[0]); rom.set(sp.f("baseAttack"), r, m.stats[1])
            rom.set(sp.f("baseDefense"), r, m.stats[2]); rom.set(sp.f("baseSpeed"), r, m.stats[3])
            rom.set(sp.f("baseSpAttack"), r, m.stats[4]); rom.set(sp.f("baseSpDefense"), r, m.stats[5])
            rom.setElem("SpeciesInfo", r, "types", 0, m.types[0]); rom.setElem("SpeciesInfo", r, "types", 1, m.types[1])
            for (k in 0 until 3) rom.setElem("SpeciesInfo", r, "abilities", k, m.abilities[k])
            rom.set(sp.f("growthRate"), r, m.growth)
            rom.set(sp.f("catchRate"), r, m.catchRate)
            rom.set(sp.f("itemCommon"), r, m.itemCommon)
            rom.set(sp.f("itemRare"), r, m.itemRare)
            if (m.learnsetChanged) {
                val key: List<Any> = m.learnset.flatMap { listOf(it.level, it.move) }
                val p = learnsetDone.getOrPut(key) {
                    val st = L.struct("LevelUpMove")
                    val a = rom.alloc((m.learnset.size + 1) * st.size)
                    m.learnset.forEachIndexed { k, lm -> rom.set(st.f("move"), a + k * st.size, lm.move); rom.set(st.f("level"), a + k * st.size, lm.level) }
                    rom.set(st.f("move"), a + m.learnset.size * st.size, levelUpEnd)
                    rom.set(st.f("level"), a + m.learnset.size * st.size, 0)
                    a
                }
                rom.set(sp.f("levelUpLearnset"), r, p)
            }
            if (m.teachableChanged) rom.set(sp.f("teachableLearnset"), r, writeList(m.teachable, listDone))
            if (m.eggChanged && m.eggMoves.isNotEmpty()) rom.set(sp.f("eggMoveLearnset"), r, writeList(m.eggMoves, listDone))
            if (m.evosChanged) rom.set(sp.f("evolutions"), r, writeEvos(m.evos))
        }
        // Moves.
        val mi = L.struct("MoveInfo")
        for (d in moves) {
            if (d == null || !d.changed) continue
            val r = L.rec("gMovesInfo", d.id)
            rom.set(mi.f("power"), r, d.power); rom.set(mi.f("type"), r, d.type)
            rom.set(mi.f("accuracy"), r, d.accuracy); rom.set(mi.f("pp"), r, d.pp)
            rom.set(mi.f("category"), r, d.category)
        }
    }

    private fun writeList(list: List<Int>, done: HashMap<List<Int>, Int>): Int = done.getOrPut(list.toList()) {
        val a = rom.alloc((list.size + 1) * 2, 2)
        list.forEachIndexed { k, v -> rom.w16(a + 2 * k, v) }
        rom.w16(a + 2 * list.size, moveUnavailable)
        a
    }

    private fun writeEvos(evos: List<Evo>): Int {
        val st = L.struct("Evolution")
        val a = rom.alloc((evos.size + 1) * st.size)
        evos.forEachIndexed { k, e ->
            val r = a + k * st.size
            rom.set(st.f("method"), r, e.method); rom.set(st.f("param"), r, e.param); rom.set(st.f("targetSpecies"), r, e.target)
            val params = when {
                !e.condsChanged || !e.paramsOk -> e.paramsPtr
                e.conds.isEmpty() -> 0
                else -> {
                    val pa = rom.alloc((e.conds.size + 1) * 8)
                    e.conds.forEachIndexed { j, c -> for (x in 0 until 4) rom.w16(pa + 8 * j + 2 * x, c[x]) }
                    rom.w16(pa + 8 * e.conds.size, conditionsEnd)
                    pa
                }
            }
            rom.set(st.f("params"), r, params)
        }
        val end = a + evos.size * st.size
        rom.set(st.f("method"), end, evolutionsEnd); rom.set(st.f("param"), end, 0); rom.set(st.f("targetSpecies"), end, 0)
        rom.set(st.f("params"), end, 0)
        return a
    }

    /**
     * The normal-difficulty parties. A party that grew is written to new space and repointed; the others in place. A
     * randomized Pokemon's moves are cleared (the game then gives it its level-up moves, CustomTrainerPartyAssignMoves),
     * and so are its ability (one not in the new species' list trips an assert in CreateNPCTrainerPartyFromTrainer), its
     * gender and its Dynamax data.
     */
    fun writeTrainers(aiFlagsField: HnsLayout.Field?) {
        val ts = L.struct("Trainer")
        val ms = L.struct("TrainerMon")
        for (t in trainers) {
            val capacity = maxOf(t.partySize, t.poolSize)
            val ptr = if (t.mons.size > capacity) rom.alloc(t.mons.size * ms.size) else t.partyPtr
            t.mons.forEachIndexed { k, tm ->
                val a = ptr + k * ms.size
                for (x in tm.raw.indices) rom.w8(a + x, tm.raw[x].toInt())
                rom.set(ms.f("species"), a, tm.species)
                rom.set(ms.f("lvl"), a, tm.level)
                rom.set(ms.f("heldItem"), a, tm.heldItem)
                rom.set(ms.f("isShiny"), a, if (tm.shiny) 1 else 0)
                if (tm.reset) {
                    for (j in 0 until 4) rom.setElem("TrainerMon", a, "moves", j, 0)
                    rom.set(ms.f("ability"), a, 0)
                    rom.set(ms.f("gender"), a, 0)
                    for (f in listOf("dynamaxLevel", "gigantamaxFactor", "shouldUseDynamax")) ms.fields[f]?.let { rom.set(it, a, 0) }
                }
            }
            if (ptr != t.partyPtr) rom.set(ts.f("party"), t.addr, ptr)
            if (t.poolSize == 0) rom.set(ts.f("partySize"), t.addr, t.mons.size)
            if (t.doubleSet) rom.set(ts.f("battleType"), t.addr, L.enumValue("TRAINER_BATTLE_TYPE", "TRAINER_BATTLE_TYPE_DOUBLES") ?: 1)
            // aiFlags is a u64: read and written whole, so flags above bit 31 stay as they were.
            if (t.smartAi && aiFlagsField != null) {
                val a = t.addr + aiFlagsField.offset
                rom.write(a, aiFlagsField.size, rom.read(a, aiFlagsField.size) or 0x07L)
            }
            if (t.name != t.origName) rom.writeFixedText(t.addr + ts.f("trainerName").offset, ts.f("trainerName").size, t.name)
        }
        val cs = L.struct("TrainerClass")
        for ((c, n) in trainerClassNames) {
            if (n != origTrainerClassNames[c]) rom.writeFixedText(L.rec("gTrainerClasses", c) + cs.f("name").offset, cs.f("name").size, n)
        }
    }

    fun writeWild() {
        val wp = L.struct("WildPokemon")
        for (set in wildSets) for (s in set.slots) {
            rom.set(wp.f("species"), s.addr, s.species)
            rom.set(wp.f("minLevel"), s.addr, s.minLevel)
            rom.set(wp.f("maxLevel"), s.addr, s.maxLevel)
        }
    }

    fun writeTrades() {
        val st = L.struct("InGameTrade")
        for (t in trades) {
            rom.set(st.f("species"), t.addr, t.species)
            rom.set(st.f("requestedSpecies"), t.addr, t.requested)
            if (t.nickname != t.origNickname) rom.writeFixedText(t.addr + st.f("nickname").offset, st.f("nickname").size, t.nickname)
            if (t.otName != t.origOtName) rom.writeFixedText(t.addr + st.f("otName").offset, st.f("otName").size, t.otName)
            if (t.otId != t.origOtId) rom.write(t.addr + st.f("otId").offset, 4, t.otId)
        }
    }

    fun writeFieldItems() {
        val (shift, width) = L.hiddenItemBits
        val mask = ((1L shl width) - 1) shl shift
        for (f in fieldItems) {
            if (f.item == f.origItem) continue
            if (f.hidden == null) rom.w16(f.addr, f.item)
            else rom.write(f.addr, 4, (rom.read(f.addr, 4) and mask.inv()) or ((f.item.toLong() shl shift) and mask))
        }
    }

    /** Pickup's items, the lab trash can's item and the starter's held item, where they changed. */
    fun writeItems() {
        val st = L.struct("PickupItem").takeIf { pickup.isNotEmpty() }
        for (p in pickup) if (p.item != p.origItem) rom.set(st!!.f("itemId"), p.addr, p.item)
        labTrashAddr?.let { if (labTrashItem != origLabTrashItem) rom.w16(it, labTrashItem) }
        if (starterItem != origStarterItem) for (op in starterItemOperands) rom.w16(op.addr, starterItem)
    }

    fun mon(id: Int): Mon? = mons.getOrNull(id)
    fun moveName(id: Int): String = moves.getOrNull(id)?.name ?: "MOVE $id"
    fun itemName(id: Int): String = items.getOrNull(id)?.name?.takeIf { it.isNotEmpty() } ?: "ITEM $id"
    fun speciesName(id: Int): String = mons.getOrNull(id)?.displayName?.takeIf { it.isNotEmpty() } ?: "SPECIES $id"
    fun typeName(t: Int): String = typeNames[t] ?: "???"

    companion object {
        /** "MAP_ROUTE29_HNS" reads "ROUTE 29", "MAP_VIOLET_CITY_HNS" reads "VIOLET CITY". */
        fun prettyMap(const: String): String = const.removePrefix("MAP_").removeSuffix("_HNS").replace('_', ' ')
            .replace(Regex("([A-Z])(\\d)"), "$1 $2").trim()
    }
}
