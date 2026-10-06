package com.ironmonone.tracker.nds

import java.io.File
import java.io.RandomAccessFile

/**
 * The DS games' alternate forms, out of the player's own ROM (2026-10-06). A player's HeartGold run showed a Sandy Cloak
 * Wormadam as Bug/Grass, its Plant Cloak typing: the card looked the species up by its number only, and the form a
 * Pokemon carries (Gen4.Mon.form, block B +0x18) picked nothing but its picture.
 *
 * Every number here is the ROM's own, read from its personal data, so a randomized type, stat or ability is the one the
 * game uses. Nothing is typed in. Which entry a form reads is the game's own rule:
 * - Generation 4 has no form fields in its personal data; the game's code picks the entry
 *   (pokeplatinum and pokeheartgold Pokemon_GetFormNarcIndex, the randomizer's Gen4Constants.formeMappings):
 *   Deoxys's three forms from 496, Wormadam's two from 499, Giratina's Origin 501, Shaymin's Sky 502, Rotom's five from
 *   503, each only where the archive has that entry (Diamond and Pearl stop at 500). In these games Rotom's appliance
 *   forms keep Electric/Ghost: their entries say so.
 * - Generation 5 keeps, for each species, its first form entry at +0x1C and its number of forms at +0x20 (Gen5Constants
 *   bsFormeOffset and bsFormeCountOffset); a species whose forms look the same has offset 0 and reads its own entry.
 * - Arceus with Multitype is the type of the plate it holds, and Normal with none, in both generations (the games'
 *   MON_DATA_TYPE_1/2 read the held item's hold effect for it); with any other ability it is its own entry.
 * Castform, Cherrim, Darmanitan's Zen Mode and Meloetta's Pirouette change in battle only: there the card shows the
 * battle's own types (NdsTracker.withBattleTypes), never a guess from the weather.
 */
object NdsForms {

    /** One personal entry: its six base stats (HP, Attack, Defense, Speed, Sp. Atk, Sp. Def), its two types as the game numbers them, its first two abilities and growth rate. */
    data class Entry(val stats: List<Int>, val type1: Int, val type2: Int, val ability1: Int, val ability2: Int, val growthRate: Int) {
        val bst: Int get() = stats.sum()
    }

    /** A game's personal data: [base] by species, [forms] by species then form (1 and up), for the forms with entries of their own. */
    class Table(val generation: Int, val base: Map<Int, Entry>, val forms: Map<Int, Map<Int, Entry>>) {
        /** [species] in [form]'s own entry, or null when the form has none (the species' own entry is the game's then). */
        fun form(species: Int, form: Int): Entry? = if (form <= 0) null else forms[species]?.get(form)

        /**
         * The species with a form whose entry is not its first form's: other types, stats, abilities or growth. What the
         * card got wrong before this, read from the ROM itself.
         */
        fun differing(): Map<Int, List<Int>> = forms.mapNotNull { (sp, fs) ->
            val b = base[sp] ?: return@mapNotNull null
            fs.filterValues { it != b }.keys.sorted().takeIf { it.isNotEmpty() }?.let { sp to it }
        }.toMap().toSortedMap()

        /** The type name the sidecar and the move table use ("GRASS") for a type id of this game, blank for "???" or one the game does not have. */
        fun typeName(id: Int): String = typeName(generation, id)
    }

    const val ARCEUS = 493
    const val MULTITYPE = 121

    /** pokeplatinum Pokemon_GetFormNarcIndex: species to (its first form's entry, its number of forms past the first). */
    private val GEN4_FORM_ENTRIES = mapOf(
        386 to (496 to 3),   // Deoxys: Attack, Defense, Speed
        413 to (499 to 2),   // Wormadam: Sandy, Trash
        487 to (501 to 1),   // Giratina: Origin
        492 to (502 to 1),   // Shaymin: Sky
        479 to (503 to 5),   // Rotom: Heat, Wash, Frost, Fan, Mow
    )

    private val GEN4_TYPES = listOf("NORMAL", "FIGHTING", "FLYING", "POISON", "GROUND", "ROCK", "BUG", "GHOST", "STEEL", "",
        "FIRE", "WATER", "GRASS", "ELECTRIC", "PSYCHIC", "ICE", "DRAGON", "DARK")
    private val GEN5_TYPES = GEN4_TYPES.filter { it.isNotEmpty() }

    /** The name of type [id] as Generation [generation] numbers its types (Gen 5 has no "???" at 9), blank when it has no such type. */
    fun typeName(generation: Int, id: Int): String = (if (generation == 5) GEN5_TYPES else GEN4_TYPES).getOrNull(id) ?: ""

    /** Which personal archive a game keeps (the randomizer's gen4_offsets.ini and gen5_offsets.ini PokemonStats), by its game code first. */
    private fun personalPaths(generation: Int, code: String): List<String> {
        if (generation == 5) return listOf("a/0/1/6")
        val all = listOf("a/0/0/2", "poketool/personal/pl_personal.narc", "poketool/personal_pearl/personal.narc", "poketool/personal/personal.narc")
        val first = when {
            code.startsWith("IPK") || code.startsWith("IPG") -> "a/0/0/2"
            code.startsWith("CPU") -> "poketool/personal/pl_personal.narc"
            code.startsWith("APA") -> "poketool/personal_pearl/personal.narc"
            else -> "poketool/personal/personal.narc"
        }
        return listOf(first) + (all - first)
    }

    /** The personal data of the DS ROM [rom] for a Generation [generation] game, or null when it cannot be read as one. */
    fun read(rom: File, generation: Int): Table? = runCatching {
        NdsRomFile(rom).use { r ->
            personalPaths(generation, r.code).firstNotNullOfOrNull { path ->
                r.file(path)?.let { blob -> runCatching { table(narcFiles(blob), generation) }.getOrNull() }
            }
        }
    }.getOrNull()

    /** The table out of a personal archive's files, or an error when they are not one of [generation]'s. */
    fun table(files: List<ByteArray>, generation: Int): Table {
        val species = if (generation == 5) Gen4.MAX_SPECIES_GEN5 else Gen4.MAX_SPECIES
        val size = files.getOrNull(1)?.size ?: 0
        require(files.size > species && (if (generation == 5) size >= 0x24 else size == 44)) { "not a Gen $generation personal archive" }
        fun entry(b: ByteArray): Entry = Entry(
            stats = (0 until 6).map { b.u8(it) },
            type1 = b.u8(6), type2 = b.u8(7),
            ability1 = b.u8(if (generation == 5) 0x18 else 0x16),
            ability2 = b.u8(if (generation == 5) 0x19 else 0x17),
            growthRate = b.u8(if (generation == 5) 0x15 else 0x13),
        )
        val base = (1..species).associateWith { entry(files[it]) }
        val forms = HashMap<Int, Map<Int, Entry>>()
        if (generation == 5) {
            for (sp in 1..species) {
                val b = files[sp]
                val count = b.u8(0x20); val first = b.u16(0x1C)
                if (count < 2 || first == 0) continue
                val fs = (1 until count).mapNotNull { f -> files.getOrNull(first + f - 1)?.takeIf { it.size == b.size }?.let { f to entry(it) } }.toMap()
                if (fs.isNotEmpty()) forms[sp] = fs
            }
        } else {
            for ((sp, at) in GEN4_FORM_ENTRIES) {
                val (first, count) = at
                val fs = (1..count).mapNotNull { f -> files.getOrNull(first + f - 1)?.takeIf { it.size == 44 }?.let { f to entry(it) } }.toMap()
                if (fs.isNotEmpty()) forms[sp] = fs
            }
        }
        return Table(generation, base, forms)
    }

    /** The files of a NARC archive (its BTAF table, then the GMIF data they point into). */
    fun narcFiles(blob: ByteArray): List<ByteArray> {
        require(String(blob, 0, 4, Charsets.US_ASCII) == "NARC") { "not a NARC" }
        var pos = blob.u16(12)
        require(String(blob, pos, 4, Charsets.US_ASCII) == "BTAF")
        val btafSize = blob.u32(pos + 4).toInt()
        val count = blob.u16(pos + 8)
        val entries = (0 until count).map { blob.u32(pos + 12 + 8 * it).toInt() to blob.u32(pos + 16 + 8 * it).toInt() }
        pos += btafSize
        require(String(blob, pos, 4, Charsets.US_ASCII) == "BTNF")
        pos += blob.u32(pos + 4).toInt()
        require(String(blob, pos, 4, Charsets.US_ASCII) == "GMIF")
        val data = pos + 8
        return entries.map { (s, e) -> blob.copyOfRange(data + s, data + e) }
    }

    /**
     * The types the game gives Arceus: with Multitype, its plate's (NdsMoveRules.PLATE_TO_TYPE), Normal with none; null
     * for any other Pokemon or ability, which keep their entry's.
     */
    fun arceusType(species: Int, abilityId: Int, heldItem: Int): String? =
        if (species != ARCEUS || abilityId != MULTITYPE) null else NdsMoveRules.PLATE_TO_TYPE[heldItem] ?: "NORMAL"
}

/**
 * A DS ROM read where it lies: its header's game code, its file name table and its file allocation table, a file read
 * only when asked for. Nothing is copied.
 */
internal class NdsRomFile(file: File) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    private val fat: ByteArray
    private val paths = HashMap<String, Int>()
    val code: String

    init {
        val head = bytes(0, 0x200)
        code = String(head, 12, 4, Charsets.US_ASCII)
        val fntOff = head.u32(0x40); val fntSize = head.u32(0x44).toInt()
        val fatOff = head.u32(0x48); val fatSize = head.u32(0x4C).toInt()
        require(fntSize in 8..0x400000 && fatSize in 8..0x400000) { "not a DS ROM" }
        val fnt = bytes(fntOff, fntSize)
        fat = bytes(fatOff, fatSize)
        walk(fnt, 0xF000, "", 0)
    }

    private fun bytes(at: Long, n: Int): ByteArray { raf.seek(at); return ByteArray(n).also { raf.readFully(it) } }

    private fun walk(fnt: ByteArray, dirId: Int, prefix: String, depth: Int) {
        if (depth > 16) return
        val idx = dirId and 0xFFF
        var pos = fnt.u32(8 * idx).toInt()
        var fid = fnt.u16(8 * idx + 4)
        while (pos < fnt.size) {
            val b = fnt.u8(pos++)
            if (b == 0) break
            val n = b and 0x7F
            val name = String(fnt, pos, n, Charsets.ISO_8859_1)
            pos += n
            if (b and 0x80 != 0) {
                val child = fnt.u16(pos); pos += 2
                walk(fnt, child, "$prefix$name/", depth + 1)
            } else paths[prefix + name] = fid++
        }
    }

    /** The file at [path], or null when the ROM has none. */
    fun file(path: String): ByteArray? {
        val id = paths[path] ?: return null
        if (8 * id + 8 > fat.size) return null
        val start = fat.u32(8 * id); val end = fat.u32(8 * id + 4)
        if (end < start || end - start > 0x1000000) return null
        return bytes(start, (end - start).toInt())
    }

    override fun close() = raf.close()
}
