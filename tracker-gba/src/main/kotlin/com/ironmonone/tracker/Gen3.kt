package com.ironmonone.tracker

/** The tracker's only window into the game. The app backs this with the emulator's
 *  memory bridge; tests back it with byte arrays. Empty result = unmapped. */
fun interface MemoryReader {
    fun read(address: Long, length: Int): ByteArray
}

/**
 * Gen III text decoding.
 *
 * The table is GENERATED from the PC tracker's own charmap - see
 * Gen3Charmap.kt and tools/extract_charmap.py. It used to be a hand-written
 * subset (digits, A-Z, a-z, a little punctuation) and everything outside it
 * hit the '?' fallback, so the held item "Poke Doll", whose real name carries
 * an e-acute at byte 0x1B, rendered as "Pok? Doll" on screen.
 *
 * Values are Strings because some bytes expand to two characters ("LV", "PK").
 *
 * Note 0xB5/0xB6: this used to substitute ASCII 'M'/'F' for the gender glyphs.
 * It now emits the reference's own male/female signs.
 */
object Gen3Text {
    /** Decodes up to the 0xFF terminator; unknown glyphs become '?', as in the
     *  reference, which maps its own unprintables to Constants.HIDDEN_INFO. */
    fun decode(bytes: ByteArray): String = buildString {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0xFF) break
            append(Gen3Charmap.MAP[v] ?: "?")
        }
    }.trim()
}

/**
 * Gen III party structure decoder — the Kotlin port of tools/PokemonDecoder.java,
 * whose algorithm is verified by 8 JVM tests (permutation table generated from the
 * canonical GAEM ordering rule, shiny/IV/nature against hand-computed vectors).
 */
object PokemonDecoder {

    const val SIZE = 100

    /**
     * The shape of one Pokemon in memory.
     *
     * Vanilla Gen 3 is a fixed 100 bytes with the encrypted substructures at
     * 0x20 and the party block at 0x50. The Nat. Dex expansion is NOT: it
     * inserts four bytes before the substructures, so everything after shifts
     * by +4 and the struct is 104 bytes. Read against the vanilla layout, a
     * Nat. Dex party decodes into nonsense - which is exactly what "TRACKER
     * CANNOT READ THIS ROM" was reporting, on both Nat. Dex builds.
     *
     * The expansion publishes its real offsets in the ROM, so they are taken
     * from there rather than assumed.
     */
    data class Layout(
        val size: Int = 100,
        val enc: Int = 0x20,
        val status: Int = 0x50,
        val level: Int = 0x54,
        val curHp: Int = 0x56,
        val maxHp: Int = 0x58,
        /**
         * The nickname's bytes at +0x08: 10 in the games, 12 on Nat. Dex (sizeofPokemonNickname, the ROM's byte at
         * 0x08000176, NatDexExtension.lua:17600, which the reference reads at Program.lua:911). Read as 10 there,
         * FLETCHINDER came out FLETCHINDE, and the Nuzlocke ledger took the species' own name for a nickname
         * (rc32 audit P3 #112).
         */
        val nickLen: Int = 10,
    ) {
        companion object { val VANILLA = Layout() }
    }

    private const val PID = 0x00
    private const val OTID = 0x04
    private const val NICK = 0x08
    private const val ENC_LEN = 48

    /** Slot of G,A,E,M per PID%24 — generated, not transcribed. */
    internal val SLOT_OF: Array<IntArray> = run {
        val table = Array(24) { IntArray(4) }
        var row = 0
        for (a in 0..3) for (b in 0..3) {
            if (b == a) continue
            for (c in 0..3) {
                if (c == a || c == b) continue
                val d = 6 - a - b - c
                val bySlot = intArrayOf(a, b, c, d)
                for (slot in 0..3) table[row][bySlot[slot]] = slot
                row++
            }
        }
        table
    }

    // Lists, not IntArrays: data-class equality must be structural so Compose can
    // skip recomposition when a tracker poll reads an unchanged party. An IntArray
    // field compares by reference and makes every poll tick look like a change.
    data class Mon(
        val pid: Long, val level: Int, val nickname: String,
        val species: Int, val heldItem: Int, val friendship: Int,
        val moves: List<Int>, val pp: List<Int>, val ivs: List<Int>, val evs: List<Int>,
        /** PP Ups applied per move (0-3). Max PP is base + base/5 per Up. */
        val ppUps: List<Int>,
        val abilitySlot: Int, val nature: Int, val shiny: Boolean,
        val status: Long, val curHp: Int, val maxHp: Int,
        val atk: Int, val def: Int, val spe: Int, val spAtk: Int, val spDef: Int,
        /** Total experience, growth substructure +4. TeamViewArea's EXP bar reads it. */
        val exp: Long = 0,
        /** IV word bit 30 (Program.lua:983 isEgg). */
        val isEgg: Boolean = false,
    )

    fun isEmpty(mon: ByteArray): Boolean = mon.u32(PID) == 0L

    fun decode(mon: ByteArray, layout: Layout = Layout.VANILLA): Mon {
        val pid = mon.u32(PID)
        val key = pid xor mon.u32(OTID)
        val plain = ByteArray(ENC_LEN)
        for (w in 0 until ENC_LEN / 4) plain.putU32(w * 4, mon.u32(layout.enc + w * 4) xor key)

        val slot = SLOT_OF[(pid % 24).toInt()]
        val g = slot[0] * 12; val a = slot[1] * 12; val e = slot[2] * 12; val m = slot[3] * 12

        val ivWord = plain.u32(m + 4)
        return Mon(
            pid = pid,
            level = mon.u8(layout.level),
            nickname = Gen3Text.decode(mon.copyOfRange(NICK, NICK + layout.nickLen)),
            species = plain.u16(g),
            heldItem = plain.u16(g + 2),
            friendship = plain.u8(g + 9),
            moves = List(4) { plain.u16(a + it * 2) },
            pp = List(4) { plain.u8(a + 8 + it) },
            // Growth substructure carries PP Ups packed 2 bits per move at +9.
            ppUps = List(4) { ((plain.u8(g + 8) shr (it * 2)) and 0x3) },
            ivs = List(6) { ((ivWord shr (it * 5)) and 0x1F).toInt() },
            evs = List(6) { plain.u8(e + it) },
            abilitySlot = ((ivWord shr 31) and 1).toInt(),
            isEgg = ((ivWord shr 30) and 1) == 1L,
            nature = (pid % 25).toInt(),
            shiny = ((mon.u32(OTID) xor pid xor (pid ushr 16) xor (mon.u32(OTID) ushr 16)) and 0xFFFF) < 8,
            status = mon.u32(layout.status),
            curHp = mon.u16(layout.curHp), maxHp = mon.u16(layout.maxHp),
            // The five stats follow max HP in the game's own order: Attack,
            // Defense, Speed, Sp. Atk, Sp. Def. They were pinned at the vanilla
            // 0x5A..0x62, so on Nat. Dex (max HP at 0x5C, a 104-byte record)
            // every stat read 4 bytes early: HP landed in ATK and DEF and each
            // real stat slid two places down the card (Blake, 2026-09-27).
            atk = mon.u16(layout.maxHp + 2), def = mon.u16(layout.maxHp + 4), spe = mon.u16(layout.maxHp + 6),
            spAtk = mon.u16(layout.maxHp + 8), spDef = mon.u16(layout.maxHp + 10),
            exp = plain.u32(g + 4),
        )
    }
}

internal fun ByteArray.u8(o: Int) = this[o].toInt() and 0xFF
internal fun ByteArray.u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
internal fun ByteArray.u32(o: Int): Long =
    u8(o).toLong() or (u8(o + 1).toLong() shl 8) or
        (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)
internal fun ByteArray.putU32(o: Int, v: Long) {
    this[o] = v.toByte(); this[o + 1] = (v ushr 8).toByte()
    this[o + 2] = (v ushr 16).toByte(); this[o + 3] = (v ushr 24).toByte()
}
