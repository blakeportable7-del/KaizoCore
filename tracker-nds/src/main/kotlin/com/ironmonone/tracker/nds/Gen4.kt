package com.ironmonone.tracker.nds

/**
 * Gen 4 (DS) Pokémon decoding: Diamond/Pearl/Platinum/HGSS share this structure.
 *
 * A party entry is 236 bytes:
 *   0x00 PID (u32)
 *   0x06 checksum (u16) over the decrypted block area
 *   0x08 four 32-byte blocks A,B,C,D, SHUFFLED by the PID and encrypted with a
 *        PRNG seeded on the checksum
 *   0x88 100 bytes of party-only data (level, HP, stats), encrypted with the
 *        same PRNG seeded on the PID instead
 *
 * The checksum is what makes this tracker robust: a candidate that decrypts to a
 * matching checksum plus a sane species and level is a real Pokémon, so the
 * party can be FOUND by scanning rather than by hardcoding an address that
 * breaks on the next revision.
 */
object Gen4 {

    const val PARTY_ENTRY_SIZE = 236
    const val BLOCK_AREA = 128
    private const val PARTY_AREA = 100
    const val MAX_SPECIES = 493
    // Gen 5 (GameInfo.ENCRYPTED_POKEMON_SIZE = 220): same four shuffled
    // 32-byte blocks, an 84-byte party area instead of 100, 649 species,
    // and the nature stored as its own byte (block B + 0x19) rather than
    // derived from the PID (PokemonDataReader, the GEN == 5 branch).
    const val PARTY_ENTRY_SIZE_GEN5 = 220
    private const val PARTY_AREA_GEN5 = 84
    const val MAX_SPECIES_GEN5 = 649

    /** The 24 block orderings, generated from the same rule Gen 3 uses. */
    private val ORDER: Array<IntArray> = run {
        val rows = ArrayList<IntArray>(24)
        for (a in 0..3) for (b in 0..3) {
            if (b == a) continue
            for (c in 0..3) {
                if (c == a || c == b) continue
                val d = 6 - a - b - c
                // Position of block A,B,C,D within the shuffled area.
                val pos = IntArray(4)
                intArrayOf(a, b, c, d).forEachIndexed { slot, block -> pos[block] = slot }
                rows += pos
            }
        }
        rows.toTypedArray()
    }

    /**
     * The LCG every Gen 4 structure is encrypted with. Each 16-bit word is XORed
     * with the high half of the advancing state.
     */
    private fun decrypt(data: ByteArray, from: Int, length: Int, seed0: Int): ByteArray {
        val out = ByteArray(length)
        var seed = seed0
        var i = 0
        while (i < length) {
            seed = seed * 0x41C64E6D + 0x6073
            val key = (seed ushr 16) and 0xFFFF
            val v = (data.u16(from + i)) xor key
            out[i] = (v and 0xFF).toByte()
            out[i + 1] = ((v shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    /** Where block [block] (0=A,1=B,2=C,3=D) sits for this PID. */
    private fun blockOffset(pid: Long, block: Int): Int {
        val shift = (((pid and 0x3E000L) shr 13) % 24).toInt()
        return ORDER[shift][block] * 32
    }

    data class Mon(
        val pid: Long,
        val species: Int,
        val heldItem: Int,
        val abilityId: Int,
        val level: Int,
        val curHp: Int,
        val maxHp: Int,
        val atk: Int,
        val def: Int,
        val spe: Int,
        val spAtk: Int,
        val spDef: Int,
        val moves: List<Int>,
        val pp: List<Int>,
        /** PP Ups per move (0-3). Max PP is base + base/5 per Up. */
        val ppUps: List<Int>,
        val ivs: List<Int>,
        val shiny: Boolean,
        val nature: Int,
        val isEgg: Boolean,
        /** Raw status word from party +0x00: sleep turns in bits 0-2, then
         *  PSN/BRN/FRZ/PAR/toxic bits - the reference reads it at
         *  PokemonDataReader.lua:75. */
        val status: Long = 0,
        /** Block A +0x0C (PokemonDataReader IMPORTANT_BLOCK_DATA A {12, friendship}). */
        val friendship: Int = 0,
        /** Block B +0x18 bit 1 (alternateForm byte; getBits(byte, 1, 1)): picks a gendered evolution. */
        val isFemale: Boolean = false,
        /**
         * Block B +0x18 bits 3-7: the form index (Program.checkForAlternateForm,
         * Program.lua:465-468: alternateForm AND 0xF8, divided by 0x08). 0 is the base form.
         */
        val form: Int = 0,
        /**
         * Block A +0x08, u32: experience points (PokemonDataReader experience1 and
         * experience2, joined in formatData, PokemonDataReader.lua:269-271).
         */
        val experience: Long = 0,
        /** Block B's IV word, bit 31: the Pokemon has been given a nickname of its own (the Nuzlocke reminder reads it). */
        val nicknamed: Boolean = false,
        /** Block C's nickname: Gen 5 keeps it as UTF-16, Gen 4 in its own 16-bit codes (letters, digits and the space are read, any other mark is a "?"). */
        val nickname: String = "",
        /** The original trainer's id and secret id (Block A +0x04 and +0x06): what a wild Pokemon's shininess is judged against. */
        val otId: Int = 0,
        val otSid: Int = 0,
    )

    /**
     * Decodes one 236-byte party entry, or null if it is not a valid Pokémon.
     * Validation is the checksum first, then ranges — so this doubles as the
     * test used when scanning memory for the party.
     */
    /**
     * Why decodeParty refused an entry, for the tracker card. Blake's Black 2
     * battle (2026-09-08) read the battle flag and no party, and nothing on
     * screen said which step refused the bytes; this does.
     */
    fun probe(entry: ByteArray, gen5: Boolean = false): String {
        val need = if (gen5) PARTY_ENTRY_SIZE_GEN5 else PARTY_ENTRY_SIZE
        if (entry.size < need) return "read ${entry.size} of $need bytes"
        val pid = entry.u32(0)
        if (pid == 0L) return "pid 0 (empty slot)"
        val checksum = entry.u16(6)
        val blocks = decrypt(entry, 8, BLOCK_AREA, checksum)
        var sum = 0
        for (i in 0 until BLOCK_AREA step 2) sum = (sum + blocks.u16(i)) and 0xFFFF
        val a = blockOffset(pid, 0)
        val species = blocks.u16(a + 0x00)
        val party = decrypt(entry, 8 + BLOCK_AREA, if (gen5) PARTY_AREA_GEN5 else PARTY_AREA, pid.toInt())
        val head = entry.take(8).joinToString("") { "%02X".format(it) }
        return "pid %08X sum %04X/%04X species %d lv %d hp %d/%d head %s".format(
            pid, sum, checksum, species, party.u8(0x04), party.u16(0x06), party.u16(0x08), head)
    }

    fun decodeParty(entry: ByteArray, gen5: Boolean = false): Mon? {
        if (entry.size < (if (gen5) PARTY_ENTRY_SIZE_GEN5 else PARTY_ENTRY_SIZE)) return null
        val pid = entry.u32(0)
        if (pid == 0L) return null
        val checksum = entry.u16(6)

        val blocks = decrypt(entry, 8, BLOCK_AREA, checksum)

        // The checksum is the plain 16-bit sum of the decrypted block area.
        var sum = 0
        for (i in 0 until BLOCK_AREA step 2) sum = (sum + blocks.u16(i)) and 0xFFFF
        if (sum != checksum) return null

        val a = blockOffset(pid, 0)
        val b = blockOffset(pid, 1)

        val species = blocks.u16(a + 0x00)
        if (species !in 1..(if (gen5) MAX_SPECIES_GEN5 else MAX_SPECIES)) return null

        val ivWord = blocks.u32(b + 0x10)
        val isEgg = ((ivWord shr 30) and 1L) == 1L

        val party = decrypt(entry, 8 + BLOCK_AREA, if (gen5) PARTY_AREA_GEN5 else PARTY_AREA, pid.toInt())
        val status = party.u32(0x00)
        val level = party.u8(0x04)
        if (level !in 1..100) return null
        val curHp = party.u16(0x06)
        val maxHp = party.u16(0x08)
        if (maxHp !in 1..999 || curHp > maxHp) return null

        val otId = blocks.u16(a + 0x04)
        val otSid = blocks.u16(a + 0x06)
        val shinyValue = otId xor otSid xor
            ((pid and 0xFFFFL).toInt()) xor ((pid ushr 16).toInt())

        return Mon(
            pid = pid,
            species = species,
            heldItem = blocks.u16(a + 0x02),
            abilityId = blocks.u8(a + 0x0D),
            level = level,
            status = status,
            curHp = curHp,
            maxHp = maxHp,
            atk = party.u16(0x0A),
            def = party.u16(0x0C),
            spe = party.u16(0x0E),
            spAtk = party.u16(0x10),
            spDef = party.u16(0x12),
            moves = List(4) { blocks.u16(b + it * 2) },
            pp = List(4) { blocks.u8(b + 0x08 + it) },
            ppUps = List(4) { blocks.u8(b + 0x0C + it).coerceIn(0, 3) },
            ivs = List(6) { ((ivWord shr (it * 5)) and 0x1FL).toInt() },
            shiny = shinyValue < 8,
            nature = if (gen5) blocks.u8(b + 0x19) % 25 else (pid % 25).toInt(),
            isEgg = isEgg,
            friendship = blocks.u8(a + 0x0C),
            experience = blocks.u32(a + 0x08),
            isFemale = ((blocks.u8(b + 0x18) shr 1) and 1) == 1,
            form = blocks.u8(b + 0x18) shr 3,
            nicknamed = ((ivWord shr 31) and 1L) == 1L,
            nickname = blocks.name(blockOffset(pid, 2), 11, gen5),
            otId = otId,
            otSid = otSid,
        )
    }

    /** Up to [max] characters of a name from [at], ended by a zero or 0xFFFF: UTF-16 in Gen 5, the game's own codes in Gen 4. */
    private fun ByteArray.name(at: Int, max: Int, gen5: Boolean): String {
        val sb = StringBuilder()
        for (i in 0 until max) {
            val ch = u16(at + i * 2)
            if (ch == 0 || ch == 0xFFFF) break
            sb.append(if (gen5) ch.toChar() else gen4Char(ch))
        }
        return sb.toString()
    }

    /** Generation 4's character codes: the digits from 0x0121, the capitals from 0x012B, the small letters from 0x0145 and the space at 0x01DE. */
    private fun gen4Char(code: Int): Char = when (code) {
        in 0x0121..0x012A -> '0' + (code - 0x0121)
        in 0x012B..0x0144 -> 'A' + (code - 0x012B)
        in 0x0145..0x015E -> 'a' + (code - 0x0145)
        0x01DE -> ' '
        else -> '?'
    }

    /** The code of a character in a Gen 4 name, the inverse of [gen4Char] (the debug encoder and the tests). */
    private fun gen4Code(c: Char): Int = when (c) {
        in '0'..'9' -> 0x0121 + (c - '0')
        in 'A'..'Z' -> 0x012B + (c - 'A')
        in 'a'..'z' -> 0x0145 + (c - 'a')
        ' ' -> 0x01DE
        else -> 0x01AC
    }

    /**
     * Builds a valid, correctly encrypted 236-byte party entry.
     *
     * This exists for the debug "inject" path, which writes a known Pokémon into
     * the resolved party slot so the read/decode/render chain can be proven
     * without playing an hour of intro. It is the game's own format: shuffle by
     * PID, 16-bit checksum over the decrypted blocks, then the two LCG passes.
     * Gen4Test checks it byte-for-byte against an independent encoder, so this
     * cannot drift into agreeing with a wrong decoder.
     */
    fun encodeParty(
        pid: Long,
        species: Int,
        level: Int,
        curHp: Int,
        maxHp: Int,
        moves: List<Int>,
        pp: List<Int> = List(4) { 10 },
        ppUps: List<Int> = List(4) { 0 },
        abilityId: Int = 0,
        heldItem: Int = 0,
        otId: Int = 0x1234,
        otSid: Int = 0x5678,
        stats: List<Int> = listOf(50, 51, 52, 53, 54),   // atk, def, spe, spAtk, spDef
        gen5: Boolean = false,
        nature: Int = 0,
        friendship: Int = 0,
        female: Boolean = false,
        experience: Long = 0,
        form: Int = 0,
        egg: Boolean = false,
        /** A nickname of the Pokemon's own: written into block C, and bit 31 of the IV word says it has one. */
        nickname: String? = null,
    ): ByteArray {
        val blocks = ByteArray(BLOCK_AREA)
        val a = blockOffset(pid, 0)
        val b = blockOffset(pid, 1)

        blocks.putU16(a + 0x00, species)
        blocks.putU16(a + 0x02, heldItem)
        blocks.putU16(a + 0x04, otId)
        blocks.putU16(a + 0x06, otSid)
        blocks[a + 0x0D] = abilityId.toByte()
        blocks[a + 0x0C] = friendship.toByte()
        blocks[b + 0x18] = ((if (female) 0x02 else 0) or (form shl 3)).toByte()
        blocks.putU32(a + 0x08, experience)
        moves.forEachIndexed { i, m -> if (i < 4) blocks.putU16(b + i * 2, m) }
        pp.forEachIndexed { i, v -> if (i < 4) blocks[b + 0x08 + i] = v.toByte() }
        ppUps.forEachIndexed { i, v -> if (i < 4) blocks[b + 0x0C + i] = v.toByte() }
        // IVs set, egg bit clear (bit 30) so the tracker does not skip it, and the nickname bit (31) only for a named one.
        blocks.putU32(b + 0x10, (if (nickname != null) 0xBFFFFFFFL else 0x3FFFFFFFL) or (if (egg) 0x40000000L else 0L))
        nickname?.let { n ->
            val c = blockOffset(pid, 2)
            n.take(10).forEachIndexed { i, ch -> blocks.putU16(c + i * 2, if (gen5) ch.code else gen4Code(ch)) }
            blocks.putU16(c + minOf(n.length, 10) * 2, 0xFFFF)
        }
        if (gen5) blocks[b + 0x19] = nature.toByte()

        var checksum = 0
        for (i in 0 until BLOCK_AREA step 2) {
            checksum = (checksum + blocks.u16(i)) and 0xFFFF
        }

        val party = ByteArray(if (gen5) PARTY_AREA_GEN5 else 100)
        party[0x04] = level.toByte()
        party.putU16(0x06, curHp)
        party.putU16(0x08, maxHp)
        stats.forEachIndexed { i, v -> if (i < 5) party.putU16(0x0A + i * 2, v) }

        val out = ByteArray(if (gen5) PARTY_ENTRY_SIZE_GEN5 else PARTY_ENTRY_SIZE)
        out.putU32(0, pid)
        out.putU16(6, checksum)
        crypt(out, 8, blocks, checksum)
        crypt(out, 8 + BLOCK_AREA, party, pid.toInt())
        return out
    }

    /** XOR with the LCG stream; encryption and decryption are the same op. */
    private fun crypt(dest: ByteArray, at: Int, plain: ByteArray, seed0: Int) {
        var seed = seed0
        var i = 0
        while (i < plain.size) {
            seed = seed * 0x41C64E6D + 0x6073
            val key = (seed ushr 16) and 0xFFFF
            val v = plain.u16(i) xor key
            dest[at + i] = (v and 0xFF).toByte()
            dest[at + i + 1] = ((v shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    /**
     * Cheap pre-filter for scanning: decrypts only the species word. Runs a few
     * dozen LCG steps instead of decrypting 228 bytes, which is what makes a
     * full main-RAM sweep practical.
     */
    fun quickSpecies(entry: ByteArray, at: Int): Int {
        if (at + 10 > entry.size) return 0
        val pid = entry.u32(at)
        if (pid == 0L) return 0
        val checksum = entry.u16(at + 6)
        val wordIndex = blockOffset(pid, 0) / 2      // species is the block's first word
        var seed = checksum
        repeat(wordIndex + 1) { seed = seed * 0x41C64E6D + 0x6073 }
        val key = (seed ushr 16) and 0xFFFF
        val off = at + 8 + wordIndex * 2
        if (off + 2 > entry.size) return 0
        return entry.u16(off) xor key
    }
}

internal fun ByteArray.u8(o: Int) = this[o].toInt() and 0xFF
internal fun ByteArray.u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
internal fun ByteArray.u32(o: Int): Long =
    u8(o).toLong() or (u8(o + 1).toLong() shl 8) or
        (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)

internal fun ByteArray.putU16(o: Int, v: Int) {
    this[o] = (v and 0xFF).toByte(); this[o + 1] = ((v shr 8) and 0xFF).toByte()
}

internal fun ByteArray.putU32(o: Int, v: Long) {
    this[o] = v.toByte(); this[o + 1] = (v ushr 8).toByte()
    this[o + 2] = (v ushr 16).toByte(); this[o + 3] = (v ushr 24).toByte()
}

/**
 * Gen 5's in-battle status. The battle data holds one 4-byte word per condition id from +0x20
 * (id 1 at +0x20, id 2 at +0x24, ...), non-zero while the condition holds. The ids are the
 * game's own, the u16 at +8 of each move in a/0/2/1 (Gen5RomHandler.loadMoves reads it):
 * 1 paralysis, 2 sleep, 3 freeze, 4 burn, 5 poison (Toxic too), 6 confusion, 17 Foresight.
 * Checked 2026-09-29 against the clean Black 2 ROM's move data and the rival-battle dump, where
 * Odor Sleuth (condition 17) left the only non-zero word, at +0x60. The reference loops over
 * ids 1-7 but reads +0x20 every time (BattleHandlerGen5.lua:160-166), so any condition came out
 * as 7, which has no icon; Blake, 2026-09-29: read it properly. [words] is the 20 bytes from
 * +0x20; the result is in the Gen 4 status bits the panel's name lookup reads.
 */
internal fun gen5StatusBits(words: ByteArray): Long {
    if (words.size < 20) return 0L
    val bits = longArrayOf(0x40L, 0x01L, 0x20L, 0x10L, 0x08L)   // PAR, SLP, FRZ, BRN, PSN
    return (0 until 5).firstOrNull { words.u32(it * 4) != 0L }?.let { bits[it] } ?: 0L
}

