package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nat. Dex Heart & Soul through the tracker, every species, move, ability and item the build has (Blake, 2026-10-06:
 * "the Natl Dex needs tested by our tracker"). Each one goes through the tracker's own code paths (a party built with
 * the game's encryption and read by GbaTracker.read, a battle struct read as the opponent's card, the lookups the info
 * screens and the log viewer call) over the real comfort build, hns-kaizo.gba (CRC C218FD9E), and every field is held
 * to the ROM's own table read here, byte by byte through the build's layout (HnsLayout), and to the build's species
 * export (app/src/main/assets/hns/species-kaizo.json, made by tools/hns/layout.py from the build's symbols).
 *
 * Every check runs; each test fails with the whole list of what differs, and writes it to build/hns-natdex-sweep/.
 * Without the ROM the tests return (IRONMON_REQUIRE_DUMPS makes that a failure, as in HnsTrackerTest).
 */
class HnsNatDexSweepTest {
    private val rom: ByteArray? by lazy { Dumps.file(HnsTrackerTest.romDir(), "hns-kaizo.gba")?.readBytes() }

    // ---------------------------------------------------------------- memory and the party, as HnsTrackerTest has them

    private class Mem(val rom: ByteArray) : MemoryReader {
        val ewram = ByteArray(0x40000)
        val iwram = ByteArray(0x8000)
        private fun region(a: Long): Pair<ByteArray, Int>? = when (a) {
            in 0x08000000L..0x09FFFFFFL -> rom to (a - 0x08000000L).toInt()
            in 0x02000000L..0x0203FFFFL -> ewram to (a - 0x02000000L).toInt()
            in 0x03000000L..0x03007FFFL -> iwram to (a - 0x03000000L).toInt()
            else -> null
        }
        override fun read(address: Long, length: Int): ByteArray {
            val (b, o) = region(address) ?: return ByteArray(0)
            if (o < 0 || o >= b.size) return ByteArray(0)
            return b.copyOfRange(o, minOf(b.size, o + length))
        }
        fun write(address: Long, bytes: ByteArray) { val (b, o) = region(address)!!; bytes.copyInto(b, o) }
        fun w8(a: Long, v: Int) = write(a, byteArrayOf(v.toByte()))
        fun w16(a: Long, v: Int) = write(a, byteArrayOf(v.toByte(), (v shr 8).toByte()))
        fun w32(a: Long, v: Long) = write(a, ByteArray(4) { (v ushr (8 * it)).toByte() })
    }

    private fun put(f: HnsField, b: ByteArray, base: Int, v: Int, index: Int = 0) {
        val unit = if (f.count > 1) f.size / f.count else f.size
        val o = base + f.offset + index * unit
        var cur = 0L
        for (i in 0 until unit) cur = cur or ((b[o + i].toLong() and 0xFF) shl (8 * i))
        val (sh, w) = if (f.count > 1) 0 to unit * 8 else f.shift to f.width
        val mask = (if (w >= 64) -1L else (1L shl w) - 1) shl sh
        cur = (cur and mask.inv()) or ((v.toLong() shl sh) and mask)
        for (i in 0 until unit) b[o + i] = (cur ushr (8 * i)).toByte()
    }

    private val charOf: Map<Char, Int> by lazy { Gen3Charmap.MAP.entries.filter { it.value.length == 1 }.associate { it.value[0] to it.key } }
    private fun encode(s: String, len: Int) = ByteArray(len) { i -> (if (i < s.length) charOf.getValue(s[i]) else 0xFF).toByte() }

    private fun partyMon(pid: Long, species: Int, level: Int, moves: List<Int>, abilityNum: Int, heldItem: Int = 0,
                         isEgg: Boolean = false, hp: Int = 30, maxHp: Int = 40, friendship: Int = 70): ByteArray {
        val otId = 0x0BAD1DEAL
        val B = HnsLayout.BoxPokemon; val P = HnsLayout.Pokemon
        val mon = ByteArray(P.SIZE)
        put(B.personality, mon, 0, pid.toInt()); put(B.otId, mon, 0, otId.toInt())
        val nick = encode("MON", 12)
        for (i in 0 until 10) mon[B.nickname.offset + i] = nick[i]
        val plain = ByteArray(48)
        val slot = PokemonDecoder.SLOT_OF[(pid % 24).toInt()]
        val g = slot[0] * 12; val a = slot[1] * 12; val m = slot[3] * 12
        val S0 = HnsLayout.PokemonSubstruct0; val S1 = HnsLayout.PokemonSubstruct1; val S3 = HnsLayout.PokemonSubstruct3
        put(S0.species, plain, g, species); put(S0.heldItem, plain, g, heldItem); put(S0.friendship, plain, g, friendship)
        put(S0.nickname11, plain, g, 0xFF); put(S0.nickname12, plain, g, 0xFF)
        listOf(S1.move1, S1.move2, S1.move3, S1.move4).forEachIndexed { i, f -> put(f, plain, a, moves.getOrElse(i) { 0 }) }
        listOf(S1.pp1, S1.pp2, S1.pp3, S1.pp4).forEachIndexed { i, f -> put(f, plain, a, if (moves.getOrElse(i) { 0 } != 0) 5 else 0) }
        put(S3.abilityNum, plain, m, abilityNum); put(S3.isEgg, plain, m, if (isEgg) 1 else 0)
        var sum = 0
        for (i in 0 until 24) sum += plain.u16(i * 2)
        put(B.checksum, mon, 0, sum and 0xFFFF)
        val key = pid xor otId
        for (w in 0 until 12) mon.putU32(B.secure.offset + w * 4, plain.u32(w * 4) xor key)
        put(P.level, mon, 0, level); put(P.hp, mon, 0, hp); put(P.maxHP, mon, 0, maxHp)
        listOf(P.attack, P.defense, P.speed, P.spAttack, P.spDefense).forEach { put(it, mon, 0, 20) }
        return mon
    }

    private val sb1 = 0x02020000L
    private val sb2 = 0x02030000L

    private fun game(r: ByteArray): Pair<Mem, GbaTracker> {
        val mem = Mem(r)
        mem.w32(HnsLayout.gSaveBlock1Ptr, sb1); mem.w32(HnsLayout.gSaveBlock2Ptr, sb2)
        mem.w32(sb2 + HnsLayout.SaveBlock2.encryptionKey.offset, 0x1234ABCDL)
        return mem to GbaTracker(mem, GameMap.resolve(mem))
    }

    /** gBattleMons with the player's Pikachu and the opponent [species], whose battle types are [types] (the expansion's ids). */
    private fun battle(mem: Mem, species: Int, types: List<Int>, level: Int) {
        val bm = HnsLayout.BattlePokemon
        mem.w8(HnsLayout.gBattlersCount, 2)
        val mons = ByteArray(bm.SIZE * 4)
        put(bm.species, mons, 0, 25); put(bm.hp, mons, 0, 30); put(bm.level, mons, 0, 50); put(bm.maxHP, mons, 0, 40)
        put(bm.species, mons, bm.SIZE, species); put(bm.hp, mons, bm.SIZE, 20); put(bm.level, mons, bm.SIZE, level); put(bm.maxHP, mons, bm.SIZE, 25)
        put(bm.types, mons, bm.SIZE, types[0], 0); put(bm.types, mons, bm.SIZE, types[1], 1); put(bm.types, mons, bm.SIZE, 0, 2)
        put(bm.types, mons, 0, 14, 0); put(bm.types, mons, 0, 14, 1)
        for (i in 0 until 8) { put(bm.statStages, mons, 0, 6, i); put(bm.statStages, mons, bm.SIZE, 6, i) }
        mem.write(HnsLayout.gBattleMons, mons)
        mem.w16(HnsLayout.gBattlerPartyIndexes, 0); mem.w16(HnsLayout.gBattlerPartyIndexes + 2, 0)
        mem.w8(HnsLayout.gBattleOutcome, 0)
        mem.w32(HnsLayout.gBattleTypeFlags, 0L)
        mem.w32(HnsLayout.gBattleMainFunc, HnsLayout.HandleTurnActionSelectionState)
        mem.write(HnsLayout.gEnemyParty, partyMon(0x00C0FFEEL, species, level, listOf(33), 0, hp = 20, maxHp = 25))
    }

    private fun endBattle(mem: Mem) {
        mem.w8(HnsLayout.gBattleOutcome, 1)
        mem.w8(HnsLayout.gBattlersCount, 0)
        mem.w32(HnsLayout.gBattleMainFunc, 0L)
        mem.write(HnsLayout.gBattleMons, ByteArray(HnsLayout.BattlePokemon.SIZE * 4))
    }

    // ---------------------------------------------------------------- the ROM's own tables, read here

    private class Raw(val rom: ByteArray) {
        fun u8(a: Long) = rom[(a - 0x08000000L).toInt()].toInt() and 0xFF
        fun u16(a: Long) = u8(a) or (u8(a + 1) shl 8)
        fun u32(a: Long) = u16(a).toLong() or (u16(a + 2).toLong() shl 16)
        fun bytes(a: Long, n: Int) = rom.copyOfRange((a - 0x08000000L).toInt(), (a - 0x08000000L).toInt() + n)
        fun isRom(p: Long) = p in 0x08000000L..0x09FFFFFFL
        /** Gen 3 text at [p], to its 0xFF, the 0xFE breaks as spaces. */
        fun text(p: Long, max: Int): String {
            if (!isRom(p)) return ""
            val b = bytes(p, max)
            val end = b.indexOfFirst { it == 0xFF.toByte() }.let { if (it < 0) b.size else it }
            val cut = b.copyOfRange(0, end).map { if (it == 0xFE.toByte()) 0x00.toByte() else it }.toByteArray()
            return Gen3Text.decode(cut + 0xFF.toByte()).replace(Regex("\\s+"), " ").trim()
        }
        fun species(id: Int) = bytes(HnsLayout.gSpeciesInfo + id.toLong() * HnsLayout.SpeciesInfo.SIZE, HnsLayout.SpeciesInfo.SIZE)
        fun move(id: Int) = bytes(HnsLayout.gMovesInfo + id.toLong() * HnsLayout.MoveInfo.SIZE, HnsLayout.MoveInfo.SIZE)
        fun item(id: Int) = bytes(HnsLayout.gItemsInfo + id.toLong() * HnsLayout.ItemInfo.SIZE, HnsLayout.ItemInfo.SIZE)
        fun ability(id: Int) = bytes(HnsLayout.gAbilitiesInfo + id.toLong() * HnsLayout.AbilityInfo.SIZE, HnsLayout.AbilityInfo.SIZE)
        fun inline(b: ByteArray, f: HnsField) = Gen3Text.decode(b.copyOfRange(f.offset, f.offset + f.size))

        /** (level, move) to LEVEL_UP_MOVE_END. */
        fun learnset(id: Int): List<Pair<Int, Int>> {
            val p = HnsLayout.SpeciesInfo.levelUpLearnset.u32(species(id))
            if (!isRom(p)) return emptyList()
            val out = ArrayList<Pair<Int, Int>>()
            var i = 0
            while (true) {
                val move = u16(p + 4L * i); val level = u16(p + 4L * i + 2)
                if (move == HnsLayout.LEVEL_UP_MOVE_END) break
                out += level to move
                i++
            }
            return out
        }

        class Evo(val method: Int, val param: Int, val target: Int, val conds: List<Pair<Int, Int>>)
        fun evolutions(id: Int): List<Evo> {
            val p = HnsLayout.SpeciesInfo.evolutions.u32(species(id))
            if (!isRom(p)) return emptyList()
            val out = ArrayList<Evo>()
            var i = 0
            while (true) {
                val e = p + 12L * i
                val method = u16(e)
                if (method == HnsLayout.EVOLUTIONS_END) break
                val cp = u32(e + 8)
                val conds = ArrayList<Pair<Int, Int>>()
                if (isRom(cp)) { var k = 0; while (u16(cp + 8L * k) != HnsLayout.CONDITIONS_END) { conds += u16(cp + 8L * k) to u16(cp + 8L * k + 2); k++ } }
                out += Evo(method, u16(e + 2), u16(e + 4), conds)
                i++
            }
            return out
        }
    }

    /** One line of species-kaizo.json: what the build's own exporter says of a species. */
    private class JsonSpecies(val id: Int, val const: String, val name: String, val natDex: Int, val stats: List<Int>,
                              val types: List<String>, val abilities: List<Int>, val enabled: Boolean, val bst: Int)

    private val json: Map<Int, JsonSpecies> by lazy {
        val out = HashMap<Int, JsonSpecies>()
        val line = Regex("""\{"id":(\d+),"const":"(\w+)","name":"([^"]*)","natDexNum":(\d+),"baseStats":\{"hp":(\d+),"atk":(\d+),"def":(\d+),"spe":(\d+),"spa":(\d+),"spd":(\d+)\},"types":\["(\w+)","(\w+)"\],"abilities":\[\{"id":(\d+),"name":"[^"]*"\},\{"id":(\d+),"name":"[^"]*"\},\{"id":(\d+),"name":"[^"]*"\}\].*"enabled":(true|false).*"bst":(\d+)""")
        File("../app/src/main/assets/hns/species-kaizo.json").forEachLine { l ->
            val m = line.find(l) ?: return@forEachLine
            val g = m.groupValues
            out[g[1].toInt()] = JsonSpecies(g[1].toInt(), g[2], g[3], g[4].toInt(), (5..10).map { g[it].toInt() },
                listOf(g[11], g[12]), (13..15).map { g[it].toInt() }, g[16] == "true", g[17].toInt())
        }
        out
    }

    /** The bundled pack's names (natdex/species.tsv), by its id. */
    private val packNames: Map<Int, String> by lazy {
        val out = HashMap<Int, String>()
        javaClass.getResourceAsStream("/natdex/species.tsv")!!.bufferedReader().forEachLine { l ->
            val t = l.indexOf('\t'); if (t > 0) l.substring(0, t).toIntOrNull()?.let { out[it] = l.substring(t + 1).trim() }
        }
        out
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s.replace("♀", "F").replace("♂", "M"), java.text.Normalizer.Form.NFKD)
        .uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    private val typeIds: Map<String, Int> by lazy {
        mapOf("TYPE_NONE" to 0, "TYPE_NORMAL" to 1, "TYPE_FIGHTING" to 2, "TYPE_FLYING" to 3, "TYPE_POISON" to 4, "TYPE_GROUND" to 5,
            "TYPE_ROCK" to 6, "TYPE_BUG" to 7, "TYPE_GHOST" to 8, "TYPE_STEEL" to 9, "TYPE_MYSTERY" to 10, "TYPE_FIRE" to 11,
            "TYPE_WATER" to 12, "TYPE_GRASS" to 13, "TYPE_ELECTRIC" to 14, "TYPE_PSYCHIC" to 15, "TYPE_ICE" to 16,
            "TYPE_DRAGON" to 17, "TYPE_DARK" to 18, "TYPE_FAIRY" to 19, "TYPE_STELLAR" to 20)
    }

    private fun report(name: String, lines: List<String>) {
        val dir = File("build/hns-natdex-sweep").also { it.mkdirs() }
        File(dir, "$name.txt").writeText(lines.joinToString("\n") + "\n")
        println("HNSSWEEP $name: ${lines.size} line(s)")
        lines.take(400).forEach { println("HNSSWEEP $name | $it") }
    }

    /** GetAbilityBySpecies (src/pokemon.c): the slot's ability, a hidden slot's other hidden ones, then the first one set. */
    private fun gameAbility(ab: List<Int>, slot: Int): Int {
        var a = ab.getOrElse(slot) { 0 }
        if (slot >= 2) for (i in 2 until 3) if (a == 0) a = ab[i]
        for (i in 0 until 3) if (a == 0) a = ab[i]
        return a
    }

    // ---------------------------------------------------------------- 1. every species

    @Test
    fun `every species, on the card, in battle and in the lookups, is the ROM's`() {
        val r = rom ?: return
        val raw = Raw(r)
        val (mem, t) = game(r.copyOf())
        val SI = HnsLayout.SpeciesInfo
        val bad = ArrayList<String>()
        val notes = ArrayList<String>()
        val formsAsBase = ArrayList<String>()
        val evoMissing = ArrayList<String>()
        var checked = 0
        val placeholder = SI.frontPic.u32(raw.species(0))
        for (s in 1..HnsSpecies.TOTAL) {
            val rec = raw.species(s)
            val j = json[s]
            val tag = "$s ${j?.const ?: "?"}"
            val enabled = SI.baseHP.at(rec) > 0
            if (j == null) { bad += "$tag: not in species-kaizo.json"; continue }
            if (j.enabled != enabled) bad += "$tag: json enabled ${j.enabled}, ROM base HP ${SI.baseHP.at(rec)}"
            if (!enabled) {
                // Never placed (the randomizer takes enabled species only); the tracker must not call it a species either.
                if (t.speciesExists(s)) bad += "$tag: disabled in the ROM, the tracker says it exists"
                continue
            }
            checked++
            val name = raw.inline(rec, SI.speciesName)
            val stats = listOf(SI.baseHP, SI.baseAttack, SI.baseDefense, SI.baseSpeed, SI.baseSpAttack, SI.baseSpDefense).map { it.at(rec) }
            val types = List(2) { SI.types.at(rec, 0, it) }
            val ab = List(3) { SI.abilities.at(rec, 0, it) }
            val ratio = SI.genderRatio.at(rec)
            val natDex = SI.natDexNum.at(rec)
            // The export agrees with the ROM read here.
            if (norm(j.name) != norm(name)) bad += "$tag: name ROM '$name', export '${j.name}'"
            if (j.natDex != natDex) bad += "$tag: Dex number ROM $natDex, export ${j.natDex}"
            if (j.stats != stats) bad += "$tag: base stats ROM $stats, export ${j.stats}"
            // layout.py names each type by the first constant of its value, which for 6 is a side hazard's (an export
            // naming slip, nothing reads the names): compared by value.
            if (j.types.map { typeIds[it] ?: if (it == "TYPE_SIDE_HAZARD_POINTED_STONES") 6 else -1 } != types) bad += "$tag: types ROM $types, export ${j.types}"
            if (j.abilities != ab) bad += "$tag: abilities ROM $ab, export ${j.abilities}"
            if (j.bst != stats.sum()) bad += "$tag: BST export ${j.bst}, stats add to ${stats.sum()}"

            // Lookups (the info screen, the log viewer, the notebook).
            val tn = t.speciesName(s)
            if (tn != name || name.isBlank() || "?" in name || tn.startsWith("#")) bad += "$tag: name '$tn', ROM '$name'"
            if (!t.speciesExists(s)) bad += "$tag: enabled, the tracker says it does not exist"
            val b = t.baseStats(s)
            if (b == null) { bad += "$tag: no base stats"; continue }
            if (listOf(b.hp, b.atk, b.def, b.spe, b.spAtk, b.spDef) != stats) bad += "$tag: stats ${listOf(b.hp, b.atk, b.def, b.spe, b.spAtk, b.spDef)}, ROM $stats"
            if (b.bst != stats.sum()) bad += "$tag: BST ${b.bst}, ROM ${stats.sum()}"
            val gen3 = types.map { HnsMon.gen3Type(it) }
            if (listOf(b.type1, b.type2) != gen3) bad += "$tag: types ${b.type1}/${b.type2}, ROM $types"
            if (types.any { it !in 1..19 || it == HnsLayout.TYPE_MYSTERY }) bad += "$tag: a type the tracker cannot draw: $types"
            if (b.genderRatio != ratio) bad += "$tag: gender ratio ${b.genderRatio}, ROM $ratio"
            val possible = t.possibleAbilities(s)
            val romPossible = ab.filter { it != 0 }.distinct().map { raw.inline(raw.ability(it), HnsLayout.AbilityInfo.name) }
            if (possible != romPossible) bad += "$tag: abilities $possible, ROM $romPossible"
            if (ab[0] == 0) bad += "$tag: no first ability in the ROM ($ab)"
            val learn = raw.learnset(s)
            if (t.learnset(s) != learn) bad += "$tag: learnset ${t.learnset(s).size} moves, ROM ${learn.size}: ${t.learnset(s).take(3)} vs ${learn.take(3)}"
            val levels = learn.map { it.first }
            if (levels != levels.sorted()) notes += "$tag: learnset levels not in order: $levels"
            // "Moves 7/15 (42)" at every level: the next is the lowest level still to come, wherever it sits in the list (the
            // game finds a level's moves anywhere in it, MonTryLearningNewMove).
            val upLevels = levels.filter { it > 1 }
            for (lv in 1..100) {
                val h = LearnedMoves.of(levels, lv)
                if (h.next != upLevels.filter { it > lv }.minOrNull() || h.learned != upLevels.count { it <= lv } || h.total != upLevels.size) {
                    bad += "$tag: at Lv.$lv the header reads ${h.learned}/${h.total} (${h.next}), the learnset $levels"; break
                }
            }
            if (learn.any { it.second >= HnsLayout.MOVES_COUNT }) bad += "$tag: learns a move past MOVES_COUNT: ${learn.filter { it.second >= HnsLayout.MOVES_COUNT }}"
            // The pack (Walking Pals, the PC tracker, the log's picture fallback, GachaMon's listed BST).
            val pack = t.packSpriteId(s)
            if (natDex > 0 && pack == null) bad += "$tag: no pack picture"
            pack?.let { p ->
                val pn = packNames[p]
                val baseName = name.substringBefore('-')
                if (pn == null || !norm(pn).startsWith(norm(baseName).take(5))) bad += "$tag: pack $p is '$pn', the species is '$name'"
                val isForm = json.values.any { o -> o.natDex == natDex && o.id < s }
                if (isForm && pn != null && !pn.contains('-')) {
                    val forms = packNames.entries.filter { (_, n) -> n.startsWith("$pn-") }.map { "${it.key} ${it.value}" }
                    formsAsBase += "$tag drawn as pack $p '$pn'" + if (forms.isNotEmpty()) "; the pack has $forms" else ""
                }
            }
            // Its own front picture, never the build's question mark.
            val front = SI.frontPic.u32(rec)
            if (front == placeholder) bad += "$tag: its front picture is the build's question mark"
            val female = SI.frontPicFemale.u32(rec).let(raw::isRom)

            // The card: three of it, one per ability slot, female and male where it has both.
            val femalePid = (0..255).map { 0x10000L * 7 + it }.firstOrNull { Gender3.of(ratio, it) == Gender3.FEMALE } ?: 0x70000L
            val malePid = (0..255).map { 0x20000L * 3 + it }.firstOrNull { Gender3.of(ratio, it) == Gender3.MALE } ?: 0x60000L
            val pids = listOf(femalePid, malePid, femalePid + 0x1000000L)
            val level = 50
            val moves = learn.map { it.second }.filter { it in 1 until HnsLayout.MOVES_COUNT }.distinct().take(4)
            val party = ByteArray(HnsLayout.Pokemon.SIZE * 3)
            for (k in 0 until 3) partyMon(pids[k], s, level, moves, abilityNum = k).copyInto(party, k * HnsLayout.Pokemon.SIZE)
            mem.write(HnsLayout.gPlayerParty, party); mem.w8(HnsLayout.gPlayerPartyCount, 3)
            val st = t.read()
            if (st.party.size != 3) { bad += "$tag: the card read ${st.party.size} of 3 (${st.diagnostics})"; continue }
            for ((k, p) in st.party.withIndex()) {
                if (p.speciesName != name) bad += "$tag slot $k: card name '${p.speciesName}'"
                val want = raw.inline(raw.ability(gameAbility(ab, k)), HnsLayout.AbilityInfo.name)
                if (!p.abilityName.equals(want, true)) bad += "$tag ability slot $k: card '${p.abilityName}', game '$want' ($ab)"
                if (p.base?.bst != stats.sum()) bad += "$tag slot $k: card BST ${p.base?.bst}"
                val lv = levels.filter { it > 1 }
                if (p.movesTotal != lv.size || p.movesLearned != lv.count { it <= level } || p.nextMoveLevel != lv.filter { it > level }.minOrNull())
                    bad += "$tag: Moves ${p.movesLearned}/${p.movesTotal} (${p.nextMoveLevel}), ROM levels $levels"
                if (p.picture == null || p.picture!!.argb.none { it != 0 }) bad += "$tag slot $k: no picture on the card"
                if (p.moveNames.take(moves.size).any { it.startsWith("#") || it.isBlank() }) bad += "$tag: move names ${p.moveNames}"
            }
            val isFemale = Gender3.of(ratio, femalePid) == Gender3.FEMALE
            if (female && isFemale && st.party[0].picture == st.party[1].picture && Gender3.of(ratio, malePid) == Gender3.MALE)
                bad += "$tag: has its own female picture, the card drew the same for both"
            if (t.picture(s, true, malePid, true) == null) bad += "$tag: no shiny picture"
            if (t.frontPicture(s) == null) bad += "$tag: the log viewer's picture is missing"

            // Evolutions: does the card say it evolves wherever the ROM says it does?
            val evos = raw.evolutions(s).filter { e -> e.target in 1..HnsSpecies.TOTAL && SI.baseHP.at(raw.species(e.target)) > 0 }
            val text = t.evolution(s)
            if (evos.isNotEmpty() && text == null) evoMissing += "$tag: evolves (${evos.joinToString { "m${it.method} p${it.param} -> ${it.target} ${it.conds}" }}), the card shows nothing"
            if (evos.isEmpty() && text != null) bad += "$tag: does not evolve, the card shows '$text'"
            if (text != null && EvoText.abbreviation(text) == null) bad += "$tag: evolution '$text' has no abbreviation"
            val lvlEvo = evos.firstOrNull { it.method == HnsLayout.EVO_LEVEL && it.param > 0 && it.conds.isEmpty() }
            if (lvlEvo != null && text != null && text.all { it.isDigit() } && evos.size == 1 && text.toInt() != lvlEvo.param) bad += "$tag: evolves at ${lvlEvo.param}, the card says $text"

            // In battle: the opponent's card.
            battle(mem, s, types, 30)
            t.read(); val bs = t.read()
            val e = bs.enemy
            if (e == null) bad += "$tag: no opponent card in battle" else {
                if (e.speciesName != name) bad += "$tag: opponent card '${e.speciesName}'"
                if (listOf(e.type1, e.type2) != gen3) bad += "$tag: opponent types ${e.type1}/${e.type2}, ROM $types"
                val guess = e.abilityGuess.split(" / ")
                if (guess != romPossible.ifEmpty { listOf("-") }) bad += "$tag: opponent abilities '${e.abilityGuess}', ROM $romPossible"
                if (e.picture == null) bad += "$tag: no picture on the opponent card"
                if (e.base?.bst != stats.sum()) bad += "$tag: opponent BST ${e.base?.bst}"
            }
            endBattle(mem); t.read()
        }
        // The Egg: the pack's Egg, never a species by number.
        assertEquals(HnsSpecies.PACK_EGG, t.packSpriteId(HnsSpecies.EGG))
        // A form the pack draws is drawn as that form (Walking Pals, the PC tracker, the log's fallback, GachaMon's BST),
        // not as its base species: gen_tracker.py matches it by National Dex number and form name.
        fun id(c: String) = json.values.first { it.const == c }.id
        for ((c, pack) in listOf("SPECIES_ROTOM_HEAT" to "Rotom-Heat", "SPECIES_CASTFORM_SUNNY" to "Castform-F",
                "SPECIES_DARMANITAN_ZEN" to "Darmanitan-Z", "SPECIES_DARMANITAN_GALAR_ZEN" to "Darmanitan-GZ",
                "SPECIES_TAUROS_PALDEA_AQUA" to "Tauros-PW", "SPECIES_PUMPKABOO_SUPER" to "Pumpkaboo-J", "SPECIES_ORICORIO_PAU" to "Oricorio-P",
                "SPECIES_MINIOR_CORE_BLUE" to "Minior-C", "SPECIES_PIKACHU_STARTER" to "Pikachu-P", "SPECIES_INDEEDEE_F" to "Indeedee-F",
                "SPECIES_RATTATA_ALOLA" to "Rattata-A", "SPECIES_PIKACHU_COSPLAY" to "Pikachu-C", "SPECIES_UNOWN_B" to "Unown"))
            assertEquals(pack, packNames[t.packSpriteId(id(c))], c)
        report("species", bad); report("species-notes", notes); report("species-forms-as-base", formsAsBase); report("species-evo-missing", evoMissing)
        bad += evoMissing
        println("HNSSWEEP species checked: $checked")
        assertTrue(checked > 1400, "species checked: $checked")
        assertEquals(emptyList(), bad)
    }
    // ---------------------------------------------------------------- 2. every move, ability and item

    @Test
    fun `every move, ability and item is the ROM's`() {
        val r = rom ?: return
        val raw = Raw(r)
        val (mem, t) = game(r.copyOf())
        val MI = HnsLayout.MoveInfo
        val bad = ArrayList<String>()
        val powerOne = ArrayList<String>()
        // Moves: the move table's row, the info screen's description.
        for (id in 1 until HnsLayout.MOVES_COUNT) {
            val m = raw.move(id)
            val name = raw.text(MI.name.u32(m), 17)
            val tag = "move $id $name"
            if (name.isBlank()) { bad += "$tag: no name in the ROM"; continue }
            if (t.moveName(id) != name) bad += "$tag: tracker name '${t.moveName(id)}'"
            val row = t.moveRowFor(id)
            if (row == null) { bad += "$tag: no row"; continue }
            val type = MI.type.at(m)
            val cat = when (MI.category.at(m)) { 0 -> "PHY"; 1 -> "SPE"; else -> "STA" }
            val want = listOf(MI.power.at(m), HnsMon.gen3Type(type), MI.accuracy.at(m), MI.pp.at(m), MI.priority.at(m), MI.makesContact.at(m) == 1, cat)
            val got = listOf(row.power, row.type, row.acc, row.pp, row.priority, row.contact, row.category)
            if (want != got) bad += "$tag: row $got, ROM $want"
            if (type !in 1..19 || type == HnsLayout.TYPE_MYSTERY) bad += "$tag: type ${type} draws as ???"
            val desc = raw.text(MI.description.u32(m), 200)
            if (t.moveDescription(id) != desc.takeIf { it.isNotBlank() }) bad += "$tag: description '${t.moveDescription(id)}', ROM '$desc'"
            if (desc.isBlank()) bad += "$tag: no description in the ROM"
            // A damaging move whose power the game works out (the expansion stores 1): the card prints the label, not 1.
            if (cat != "STA" && MI.power.at(m) == 1 && id !in MoveRules.VARIABLE_POWER) {
                val shown = row.powerLabel ?: MoveRules.basePower(id, row.power)
                powerOne += "$tag: power column '$shown' (effect ${MI.effect.at(m)})"
                if (shown == "1") bad += "$tag: the game works its power out, the card prints the placeholder 1"
            }
        }
        // Abilities: the name the card and the reveal show, the description the info screen shows, and back by name.
        val AI = HnsLayout.AbilityInfo
        val byName = HashMap<String, Int>()
        val sharedNames = ArrayList<String>()
        for (id in 1 until HnsLayout.ABILITIES_COUNT) {
            val a = raw.ability(id)
            val name = raw.inline(a, AI.name)
            val tag = "ability $id $name"
            if (name.isBlank()) { bad += "$tag: no name"; continue }
            if (t.abilityName(id) != name) bad += "$tag: tracker name '${t.abilityName(id)}'"
            val desc = raw.text(AI.description.u32(a), 200)
            if (t.abilityDescription(id) != desc.takeIf { it.isNotBlank() }) bad += "$tag: description '${t.abilityDescription(id)}', ROM '$desc'"
            if (desc.isBlank()) bad += "$tag: no description in the ROM"
            val back = t.abilityIdOf(name)
            if (back != id) {
                val other = byName[name.uppercase()]
                if (other == null) bad += "$tag: by its name the info screen finds ability $back"
                else sharedNames += "$tag: shares its name with ability $other, whose description the info screen shows for it"
            }
            byName.putIfAbsent(name.uppercase(), id)
        }
        // Items: the name, the pocket the game puts it in, and the bag's lines (Heals in Bag, the balls, the evolution stones).
        val II = HnsLayout.ItemInfo
        val read = setOf(0, 1, 2, 4)   // POCKET_ITEMS, MEDICINE, POKE_BALLS, BERRIES: the pockets readBag reads
        val pocketField = mapOf(0 to HnsLayout.Bag.items, 1 to HnsLayout.Bag.medicine, 2 to HnsLayout.Bag.pokeBalls, 4 to HnsLayout.Bag.berries)
        val evoItems = HashSet<Int>()
        for (s in 1..HnsSpecies.TOTAL) for (e in raw.evolutions(s)) {
            if (e.method == HnsLayout.EVO_ITEM) evoItems += e.param
            e.conds.filter { it.first == HnsLayout.IF_HOLD_ITEM }.forEach { evoItems += it.second }
        }
        val notEvo = ArrayList<String>()
        val k = (0x1234ABCDL and 0xFFFF).toInt()
        val bagAt = sb1 + HnsLayout.SaveBlock1.bag.offset
        for (id in 1 until HnsLayout.ITEMS_COUNT) {
            val it = raw.item(id)
            val name = raw.text(II.name.u32(it), 21)
            val tag = "item $id $name"
            if (name.isBlank()) continue
            if (t.itemName(id) != name) bad += "$tag: tracker name '${t.itemName(id)}'"
            val pocket = II.pocket.at(it)
            if (pocket !in read) continue
            mem.write(bagAt, ByteArray(HnsLayout.Bag.SIZE))
            val f = pocketField.getValue(pocket)
            mem.w16(bagAt + f.offset, id); mem.w16(bagAt + f.offset + 2, 3 xor k)
            val bag = t.readBag()
            if (bag[id] != 3) bad += "$tag: in pocket $pocket, the bag reads $bag"
            val rows = t.healsInBag(null)
            val row = rows.singleOrNull()
            if (row == null) { bad += "$tag: Heals in Bag rows $rows"; continue }
            if (row.name != name) bad += "$tag: Heals in Bag names it '${row.name}'"
            if (id in evoItems && row.category != "Evo") notEvo += "$tag: an evolution item, listed under '${row.category}'"
        }
        mem.write(bagAt, ByteArray(HnsLayout.Bag.SIZE))
        report("moves-abilities-items", bad); report("moves-power-one", powerOne); report("items-evo-not-evo", notEvo)
        report("abilities-shared-names", sharedNames)
        bad += notEvo
        assertEquals(emptyList(), bad)
    }
}
