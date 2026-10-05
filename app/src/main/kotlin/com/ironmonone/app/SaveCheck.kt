package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File

/**
 * Whether a game's save file holds a game in progress (2026-09-29).
 *
 * The Nuzlocke screen warned "This game already has a save" for an Emerald that had only ever been booted to its
 * title screen: KaizoCore writes an auto-save state whenever a game is left, and an emulator save file can exist
 * blank, so neither file's existence says anything. A Gen 3 save is judged by its sections' signature, which the
 * game writes only when it saves; any other save by not being blank (a never-used save is all 0x00 or all 0xFF).
 */
internal object SaveCheck {
    /** The word every Gen 3 save section carries at 0xFF8 once the game has written it. */
    private const val GEN3_SIGNATURE = 0x08012025L
    private const val GEN3_SECTION = 4096

    fun hasProgress(file: File, platform: Platform): Boolean {
        if (!file.isFile) return false
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        return hasProgress(bytes, platform)
    }

    fun hasProgress(bytes: ByteArray, platform: Platform): Boolean = when (platform) {
        Platform.GBA -> (0..bytes.size - GEN3_SECTION step GEN3_SECTION).any { u32(bytes, it + 0xFF8) == GEN3_SIGNATURE }
        else -> bytes.any { it != 0.toByte() && it != 0xFF.toByte() }
    }

    /**
     * How many Pokémon the party holds in a Gen 3 save's newest copy, or null when that cannot be read (2026-09-30,
     * UX audit P0-2). A new seed keeps a save with no Pokémon in it, the intro skip, and sets any other aside.
     *
     * The game keeps two copies of the save, 14 sections of 4 KB each, and writes the older one next; each section
     * ends with its id (0xFF4), the signature (0xFF8) and the copy's save counter (0xFFC). Section 1 opens with the
     * party: its count is SaveBlock1's playerPartyCount, 0x234 into it on Ruby, Sapphire and Emerald and 0x34 on
     * FireRed and LeafGreen, which have no map view before it (pokeemerald and pokefirered global.h).
     *
     * The Nat. Dex builds read the same, checked on their ROMs (2026-09-30, NatDexSaveLayoutTest): SavePlayerParty
     * stores the count at those offsets, only the party slots grew (104 bytes, not 100), and their sections hold 0xFEC
     * bytes of data instead of 0xF80 with SaveBlock1 still opening section 1 and the footer where vanilla has it.
     */
    fun gen3PartyCount(bytes: ByteArray, frlg: Boolean): Int? = gen3PartyCount(bytes, if (frlg) 0x34 else 0x234)

    /**
     * Heart & Soul's SaveBlock1.playerPartyCount (layout-kaizo.json, 2026-10-05): 4 bytes later than Emerald's. Its save
     * keeps Emerald's 14 sections and footer (include/save.h: 0xF80 bytes of data, 116 of SaveBlock3, then the id,
     * checksum, signature and counter), so the same reading finds its party.
     */
    const val HNS_PARTY_COUNT = 0x238

    /** [gen3PartyCount] with the party count [countOffset] bytes into SaveBlock1 (section 1). */
    fun gen3PartyCount(bytes: ByteArray, countOffset: Int): Int? {
        var newest = -1L
        var party: Int? = null
        for (copy in 0 until 2) {
            val base = copy * GEN3_SECTIONS * GEN3_SECTION
            for (s in 0 until GEN3_SECTIONS) {
                val at = base + s * GEN3_SECTION
                if (at + GEN3_SECTION > bytes.size || u32(bytes, at + 0xFF8) != GEN3_SIGNATURE) continue
                if (u16(bytes, at + 0xFF4) != 1) continue
                val counter = u32(bytes, at + 0xFFC)
                if (counter < newest) continue
                val count = u32(bytes, at + countOffset)
                newest = counter
                party = if (count in 0..6) count.toInt() else null
            }
        }
        return party
    }

    private const val GEN3_SECTIONS = 14

    private fun u16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
            ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
}
