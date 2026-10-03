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

    /** The letter the game code ends in: the release the cartridge was made for (GBATEK: E US English, J Japan, D German...). */
    val region: Char get() = gameCode.lastOrNull() ?: ' '

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
    /** The letter the game code ends in: the release the cartridge was made for. */
    val region: Char get() = gameCode.lastOrNull() ?: ' '

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
 *
 * A header alone never makes a file the tracker's (2026-09-30, UX audit
 * P0-11): the game code of a DS cartridge names its release, so a German
 * HeartGold is not the US one whatever its title says, and [Result.exact] is
 * the one test for "this is the known build". Every other file gets a
 * [Verdict] that says which case it is, and a [Result.summary] that says what
 * does work and that it still plays.
 */
object RomIdentity {

    /**
     * What a file is to the tracker, so which words and which shelf it gets.
     * Everything below EXACT plays without a tracker; DAMAGED and NOT_A_GAME
     * do not play at all.
     */
    enum class Verdict {
        /** A known build by its checksum: the tracker reads it. */
        EXACT,

        /** A known game by its header whose checksum is not pinned yet (Black). */
        UNCHECKED,

        /** A real Pokémon game of another release: German, Japanese, European... */
        OTHER_LANGUAGE,

        /** A real Pokémon game of the release the tracker reads, but not a copy it reads: another revision, trimmed or changed. */
        OTHER_VERSION,

        /** A Pokémon game bigger than any real release: a ROM hack. */
        CHANGED,

        /** A game the tracker does not read at all. */
        OTHER_GAME,

        /** A DS file whose header fails its own check: it would crash the core. */
        DAMAGED,

        /** Nothing that looks like a game. */
        NOT_A_GAME,
    }

    data class Result(
        val crc: Long,
        val sizeBytes: Long,
        val header: GbaHeader?,
        val kind: RomKind?,
        val dsHeader: DsHeader? = null,
        val gbHeader: GbHeader? = null,
    ) {
        val recognised: Boolean get() = kind != null

        /**
         * The file IS the known build: its checksum is the one pinned for [kind]. Only such a file is read by the
         * tracker, offered as a base for a run or stored by Prepare. A header match is not enough.
         */
        val exact: Boolean get() = kind != null && kind.expectedCrc != RomKind.CRC_UNKNOWN && kind.expectedCrc == crc

        /** "POKEMON HG IPKE", "PM_CRYSTAL", "POKEMON FIRE BPRE" - whatever the header says. */
        val headerLine: String? get() = when {
            header != null -> "${header.title} ${header.gameCode}"
            dsHeader != null -> "${dsHeader.title} ${dsHeader.gameCode}"
            gbHeader != null -> gbHeader.title
            else -> null
        }

        /** Which case this file is. */
        val verdict: Verdict get() = verdictOf(this)

        /**
         * What this is, what does work, and that it still plays: one plain paragraph for the card under a picked file
         * and on the library shelf. The import adds "Added, but the tracker cannot read it." in front where it applies.
         */
        val summary: String get() = summaryOf(this)
    }

    // The ruleset and quality-of-life patches belong here too. Without them a
    // patched build (Smart AI, pseudo-fluctuating, Super Kaizo, Faster FireRed)
    // read as "FireRed, but modified": shelved as a hack, and the RUN tab, which
    // only offers verified ROMs, never listed it.
    private val known: List<RomKind> get() = RomKind.all

    fun identify(bytes: ByteArray): Result = identify(bytes, bytes.size.toLong(), Crc32.of(bytes))

    /** The first bytes any header parser needs; every header lives inside them. */
    const val HEAD = 64 * 1024

    /**
     * Identify a file without reading it into memory. A 512 MB DS dump does
     * not fit in the heap (Black 2 threw OutOfMemoryError on import); the CRC
     * streams over the file and only [HEAD] bytes are held for the headers.
     */
    fun identify(file: java.io.File, onProgress: ((Long, Long) -> Unit)? = null): Result {
        val size = file.length()
        val head = ByteArray(minOf(size, HEAD.toLong()).toInt())
        val crc = java.util.zip.CRC32()
        file.inputStream().buffered(1 shl 20).use { input ->
            var got = 0
            while (got < head.size) { val n = input.read(head, got, head.size - got); if (n < 0) break; got += n }
            crc.update(head, 0, got)
            val buf = ByteArray(1 shl 20)
            var read = got.toLong()
            while (true) { val n = input.read(buf); if (n < 0) break; crc.update(buf, 0, n); read += n; onProgress?.invoke(read, size) }
        }
        return identify(head, size, crc.value)
    }

    /**
     * [file] identified from its header and a checksum the caller already holds (the library's own sidecar), so
     * nothing is hashed: only [HEAD] bytes are read. A 512 MB DS game is seconds of hashing that every screen
     * listing the library would repeat at once.
     */
    fun identifyWithCrc(file: java.io.File, crc: Long): Result {
        val size = file.length()
        val head = ByteArray(minOf(size, HEAD.toLong()).toInt())
        file.inputStream().use { input ->
            var got = 0
            while (got < head.size) { val n = input.read(head, got, head.size - got); if (n < 0) break; got += n }
        }
        return identify(head, size, crc)
    }

    private fun identify(bytes: ByteArray, sizeBytes: Long, crc: Long): Result {
        val gba = GbaHeader.parse(bytes)
        val ds = if (gba == null) DsHeader.parse(bytes) else null
        val gb = if (gba == null && ds == null) GbHeader.parse(bytes) else null

        val byCrc = known.firstOrNull { it.expectedCrc == crc }
        val byHeader = when {
            byCrc != null -> null
            // A header match needs the header to check out; a damaged DS file
            // must never be handed to the tracker or the core as a known game.
            // And it needs the release to be the pinned one: another language
            // keeps the title and changes only the last letter of the code.
            ds != null && ds.checksumValid -> known.firstOrNull {
                it.platform == Platform.NDS && it.titleDetect == ds.title
            }?.takeIf { sameRelease(ds) }
            gb != null -> known.firstOrNull {
                it.platform == Platform.GBC && it.titleDetect == gb.title
            }
            else -> null
        }
        return Result(
            crc = crc, sizeBytes = sizeBytes, header = gba,
            kind = byCrc ?: byHeader, dsHeader = ds, gbHeader = gb,
        )
    }

    // ---------------------------------------------------------------- the words

    /** Every real GBA Pokémon release is exactly this big; anything larger has been expanded, which a hack does. */
    private const val GBA_RELEASE_SIZE = 16L * 1024 * 1024

    /** A DS game the tracker knows by the first three letters of its code, and the whole code of the release it reads. */
    private class DsGame(val name: String, val usCode: String)

    // Codes from the DS reference and Blake's dumps (RomKind: HG IPKE, SS IPGE, Platinum CPUE, Diamond ADAE, Pearl APAE,
    // Black IRBO, White IRAO, Black 2 IREO, White 2 IRDO). Gen 5's last letter is O for the one US and Europe cartridge.
    private val DS_GAMES = listOf(
        DsGame("Diamond", "ADAE"), DsGame("Pearl", "APAE"), DsGame("Platinum", "CPUE"),
        DsGame("HeartGold", "IPKE"), DsGame("SoulSilver", "IPGE"),
        DsGame("Black", "IRBO"), DsGame("White", "IRAO"), DsGame("Black 2", "IREO"), DsGame("White 2", "IRDO"),
    )

    private fun dsGameOf(ds: DsHeader): DsGame? = DS_GAMES.firstOrNull { it.usCode.take(3) == ds.gameCode.take(3) }

    /** True unless the game code names a known game of a release the tracker does not read. */
    private fun sameRelease(ds: DsHeader): Boolean = dsGameOf(ds)?.let { it.usCode == ds.gameCode } ?: true

    /** Gold and Silver carry their release in the last letter of the title (AAUE, AAXE: E is US English); null for anything else. */
    private fun gbLanguageGame(gb: GbHeader): Pair<String, Char>? {
        val t = gb.title
        if (t.length != 15) return null
        return when {
            t.startsWith("POKEMON_GLDAAU") -> "Gold" to t.last()
            t.startsWith("POKEMON_SLVAAX") -> "Silver" to t.last()
            else -> null
        }
    }

    /** The release a game code ends in, as an adjective; null for a letter that names no single release. Never a language guessed. */
    private fun releaseOf(letter: Char): String? = when (letter) {
        'J' -> "Japanese"
        'D' -> "German"
        'F' -> "French"
        'I' -> "Italian"
        'S' -> "Spanish"
        'K' -> "Korean"
        'P' -> "European"
        else -> null
    }

    private fun shortName(k: RomKind) = k.displayName.substringAfter(' ').substringBefore(" (")

    /** The header version byte each pinned GBA build has: v1.1 is 1, every other one is v1.0. */
    private fun revOf(k: RomKind) = if (k.id == RomKind.FIRERED_U_V11.id) 1 else 0

    /** The builds the tracker reads for [game], oldest first. */
    private fun pinned(game: String): List<RomKind> =
        RomKind.allV1.filter { it.expectedCrc != RomKind.CRC_UNKNOWN && shortName(it) == game }.sortedBy(::revOf)

    private fun Result.gameName(): String? = when {
        header != null -> header.game
        dsHeader != null -> dsGameOf(dsHeader)?.name ?: kind?.let(::shortName)
        gbHeader != null -> kind?.let(::shortName) ?: gbLanguageGame(gbHeader)?.first
        else -> null
    }

    /** How a game reads in a sentence: a GBA game bare ("FireRed"), the others with the series name ("Pokémon Platinum"). */
    private fun Result.said(game: String) = if (header != null) game else "Pokémon $game"

    private fun Result.region(): Char? = when {
        header != null -> header.region
        dsHeader != null -> dsHeader.region
        gbHeader != null -> gbLanguageGame(gbHeader)?.second
        else -> null
    }

    private fun versions(kinds: List<RomKind>): String =
        kinds.filter { it.platform == Platform.GBA }.map { "v1.${revOf(it)}" }.joinToString(" or ")

    /** "The tracker reads the US English FireRed, v1.0 or v1.1." Versions only where there is more than one to tell apart, or [always]. */
    private fun Result.reads(game: String, always: Boolean = false): String {
        val kinds = pinned(game)
        if (kinds.isEmpty()) return "The tracker does not read ${said(game)} yet."
        val v = versions(kinds)
        return "The tracker reads the US English ${said(game)}" + (if (v.isNotEmpty() && (always || kinds.size > 1)) ", $v" else "") + "."
    }

    private fun consoleOf(r: Result) = when {
        r.header != null -> "GBA"
        r.dsHeader != null -> "DS"
        else -> "Game Boy"
    }

    private fun verdictOf(r: Result): Verdict {
        val ds = r.dsHeader
        return when {
            r.exact -> Verdict.EXACT
            ds != null && !ds.checksumValid -> Verdict.DAMAGED
            r.kind != null && r.kind.expectedCrc == RomKind.CRC_UNKNOWN -> Verdict.UNCHECKED
            r.header != null -> when {
                r.header.game == null -> Verdict.OTHER_GAME
                r.sizeBytes > GBA_RELEASE_SIZE -> Verdict.CHANGED
                r.header.region == 'E' -> Verdict.OTHER_VERSION
                else -> Verdict.OTHER_LANGUAGE
            }
            ds != null -> {
                val game = dsGameOf(ds)
                when {
                    r.kind != null -> Verdict.OTHER_VERSION
                    game == null -> Verdict.OTHER_GAME
                    game.usCode == ds.gameCode -> Verdict.OTHER_VERSION
                    else -> Verdict.OTHER_LANGUAGE
                }
            }
            r.gbHeader != null -> when {
                r.kind != null -> Verdict.OTHER_VERSION
                gbLanguageGame(r.gbHeader) != null -> Verdict.OTHER_LANGUAGE
                else -> Verdict.OTHER_GAME
            }
            else -> Verdict.NOT_A_GAME
        }
    }

    private const val WITHOUT_TRACKER = "It plays here without a tracker."

    private fun summaryOf(r: Result): String {
        val game = r.gameName()
        return when (verdictOf(r)) {
            Verdict.EXACT -> r.kind!!.displayName
            Verdict.UNCHECKED -> "This is ${r.kind!!.displayName}, a copy KaizoCore has not checked yet. $WITHOUT_TRACKER"
            Verdict.OTHER_LANGUAGE -> {
                val adjective = r.region()?.let(::releaseOf)
                val what = if (adjective != null) "the $adjective ${r.said(game!!)}" else "${r.said(game!!)} from another release"
                "This is $what. ${r.reads(game)} $WITHOUT_TRACKER"
            }
            Verdict.OTHER_VERSION -> otherVersion(r, game!!)
            Verdict.CHANGED -> "A changed Pokémon $game (a ROM hack). It plays without the tracker."
            Verdict.OTHER_GAME -> {
                val title = (r.header?.title ?: r.dsHeader?.title ?: r.gbHeader?.title).orEmpty()
                "A ${consoleOf(r)} game" + (if (title.isNotBlank()) " ($title)" else "") + ", but not one the tracker reads. It still plays, without a tracker."
            }
            Verdict.DAMAGED -> "This DS file looks damaged (${r.dsHeader!!.title}), so it will not play. Copy the file to the phone again, then add it again."
            Verdict.NOT_A_GAME -> "Not a Game Boy, Game Boy Advance or DS game file."
        }
    }

    /** A real game of the tracker's release that is not a copy it reads. Says the revision where the header tells it. */
    private fun otherVersion(r: Result, game: String): String {
        val h = r.header
        if (h != null) {
            val kinds = pinned(game)
            val rev = h.version
            // The version byte of a GBA game is its revision: 0 is v1.0, 1 is v1.1, and dumps name that "Rev 1".
            if (rev in 0..2) {
                val named = "$game v1.$rev" + if (rev >= 1) " (the file name may say Rev $rev)" else ""
                return if (kinds.any { revOf(it) == rev })
                    "This is $named, but this file is not an exact copy. It may be trimmed or changed. $WITHOUT_TRACKER"
                else "This is $named. ${r.reads(game, always = true)} $WITHOUT_TRACKER"
            }
            return "This is $game, but not ${versions(kinds)}. It may be trimmed, changed or another revision. $WITHOUT_TRACKER"
        }
        val said = r.said(game)
        return if (r.dsHeader != null)
            "This is $said, but not the copy the tracker reads. It may be trimmed, changed or another revision. $WITHOUT_TRACKER"
        // A Game Boy cartridge does not say its language, so another language is one of the things it may be.
        else "This is $said, but not the US English copy the tracker reads. It may be another language or revision, trimmed or changed. $WITHOUT_TRACKER"
    }
}
