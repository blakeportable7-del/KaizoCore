package com.ironmonone.patch

import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind

/** GBA cartridge header. Nothing on the JVM parses this; it is ~30 lines from GBATEK. */
data class GbaHeader(
    val title: String,
    val gameCode: String,
    val makerCode: String,
    val version: Int,
    val checksumValid: Boolean,
) {
    /** "Emerald", "FireRed", "LeafGreen", "Ruby", "Sapphire", or null. */
    val game: String? get() = when (gameCode.take(3)) {
        "BPE" -> "Emerald"
        "BPR" -> "FireRed"
        "BPG" -> "LeafGreen"
        "AXV" -> "Ruby"
        "AXP" -> "Sapphire"
        else -> null
    }

    companion object {
        const val TITLE = 0xA0
        const val CODE = 0xAC
        const val MAKER = 0xB0
        const val VERSION = 0xBC
        const val CHECK = 0xBD

        fun parse(rom: ByteArray): GbaHeader? {
            if (rom.size < 0xC0) return null
            // A GBA header carries a checksum over itself; a DS or GB file will
            // not pass it, which is what keeps the three parsers from
            // claiming each other's files.
            if (rom.u8(CHECK) != checksum(rom)) return null
            return GbaHeader(
                title = ascii(rom, TITLE, 12),
                gameCode = ascii(rom, CODE, 4),
                makerCode = ascii(rom, MAKER, 2),
                version = rom.u8(VERSION),
                checksumValid = true,
            )
        }

        /** GBATEK: chk = -(0x19 + sum of bytes 0xA0..0xBC), truncated to 8 bits. */
        fun checksum(rom: ByteArray): Int {
            var sum = 0
            for (i in TITLE..VERSION) sum += rom.u8(i)
            return (-(0x19 + sum)) and 0xFF
        }
    }
}

/**
 * DS cartridge header: 12-byte title at 0x00, 4-byte game code at 0x0C.
 * The code is what the DS tracker reference keys its memory maps by.
 */
data class DsHeader(val title: String, val gameCode: String, val checksumValid: Boolean = true) {
    companion object {
        /** CRC-16 (poly 0xA001, init 0xFFFF) over the header, as the DS BIOS checks it. */
        fun crc16(d: ByteArray, from: Int, to: Int): Int {
            var crc = 0xFFFF
            for (i in from until to) {
                crc = crc xor (d[i].toInt() and 0xFF)
                repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
            }
            return crc and 0xFFFF
        }

        fun parse(rom: ByteArray): DsHeader? {
            if (rom.size < 0x200) return null
            val title = ascii(rom, 0x00, 12)
            val code = ascii(rom, 0x0C, 4)
            // A real code is four upper-case letters or digits; a GBA file's
            // bytes here are ARM code and never look like one.
            if (code.length != 4 || !code.all { it.isLetterOrDigit() && !it.isLowerCase() }) return null
            if (title.isEmpty()) return null
            // The header carries its own CRC-16 at 0x15E. A file that fails it is
            // not a cartridge image; melonDS crashes on one, so it must not play.
            val declared = (rom[0x15E].toInt() and 0xFF) or ((rom[0x15F].toInt() and 0xFF) shl 8)
            return DsHeader(title, code, checksumValid = crc16(rom, 0, 0x15E) == declared)
        }
    }
}

/**
 * Game Boy cartridge header: Nintendo logo at 0x104, title at 0x134 (up to 16
 * bytes; on Color games the last is the CGB flag, 0x80 or 0xC0).
 */
data class GbHeader(val title: String, val colorFlag: Int) {
    val colorOnly: Boolean get() = colorFlag == 0xC0

    companion object {
        /** The first bytes of the logo every licensed cartridge carries. */
        private val LOGO = intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D)

        fun parse(rom: ByteArray): GbHeader? {
            if (rom.size < 0x150) return null
            for (i in LOGO.indices) if (rom.u8(0x104 + i) != LOGO[i]) return null
            val flag = rom.u8(0x143)
            val titleLen = if (flag == 0x80 || flag == 0xC0) 15 else 16
            return GbHeader(ascii(rom, 0x134, titleLen), flag)
        }
    }
}

private fun ascii(d: ByteArray, off: Int, len: Int) = buildString {
    for (i in 0 until len) {
        val c = d.u8(off + i)
        if (c == 0) break
        append(if (c in 32..126) c.toChar() else '.')
    }
}.trim()

/**
 * What a file on disk actually is.
 *
 * CRC-32 first: the Nat. Dex patch leaves the GBA header untouched, so a
 * patched Emerald still reports "POKEMON EMER" and identifying it from the
 * title would silently classify it as vanilla.
 *
 * Then the HEADER, for a kind whose CRC this app does not know yet (every
 * DS and Game Boy game added before Blake's own dump was read). The title
 * must match the kind's [RomKind.titleDetect] exactly, on the kind's own
 * platform - "POKEMON B" must not claim "POKEMON B2". The CRC is carried in
 * the result so it can be pinned once seen.
 */
object RomIdentity {

    data class Result(
        val crc: Long,
        val sizeBytes: Int,
        val header: GbaHeader?,
        val kind: RomKind?,
        val dsHeader: DsHeader? = null,
        val gbHeader: GbHeader? = null,
    ) {
        val recognised: Boolean get() = kind != null

        /** "POKEMON HG IPKE", "PM_CRYSTAL", "POKEMON FIRE BPRE" - whatever the header says. */
        val headerLine: String? get() = when {
            header != null -> "${header.title} ${header.gameCode}"
            dsHeader != null -> "${dsHeader.title} ${dsHeader.gameCode}"
            gbHeader != null -> gbHeader.title
            else -> null
        }

        /** One line for the user, saying what this is or why it was rejected. */
        val summary: String get() = when {
            kind != null && kind.expectedCrc == RomKind.CRC_UNKNOWN ->
                "${kind.displayName} (by header; CRC ${"%08x".format(crc)} not yet pinned)"
            kind != null -> kind.displayName
            header?.game != null && sizeBytes > 16 * 1024 * 1024 ->
                "${header.game}, but modified. This is not a clean dump."
            header?.game != null -> "${header.game}, but not a revision this app knows."
            header != null -> "A GBA ROM, but not a Pokémon game this app supports."
            dsHeader != null && !dsHeader.checksumValid -> "A DS file with a damaged header (${dsHeader.title}). It would crash the core, so it will not play."
            dsHeader != null -> "A DS ROM (${dsHeader.title}), but not one this app supports."
            gbHeader != null -> "A Game Boy ROM (${gbHeader.title}), but not one this app supports."
            else -> "Not a GBA, DS or Game Boy ROM."
        }
    }

    private val known: List<RomKind> get() = RomKind.allV1 + RomKind.allNatDex

    fun identify(bytes: ByteArray): Result {
        val crc = Crc32.of(bytes)
        val gba = GbaHeader.parse(bytes)
        val ds = if (gba == null) DsHeader.parse(bytes) else null
        val gb = if (gba == null && ds == null) GbHeader.parse(bytes) else null

        val byCrc = known.firstOrNull { it.expectedCrc == crc }
        val byHeader = when {
            byCrc != null -> null
            // A header match needs the header to check out; a damaged DS file
            // must never be handed to the tracker or the core as a known game.
            ds != null && ds.checksumValid -> known.firstOrNull {
                it.platform == Platform.NDS && it.titleDetect == ds.title
            }
            gb != null -> known.firstOrNull {
                it.platform == Platform.GBC && it.titleDetect == gb.title
            }
            else -> null
        }
        return Result(
            crc = crc, sizeBytes = bytes.size, header = gba,
            kind = byCrc ?: byHeader, dsHeader = ds, gbHeader = gb,
        )
    }
}
