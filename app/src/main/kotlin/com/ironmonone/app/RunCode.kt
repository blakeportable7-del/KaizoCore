package com.ironmonone.app

import java.io.File
import java.util.zip.CRC32

/**
 * A run code: everything a friend's app needs to build the same run from their own dump, and
 * to check that it did (Blake, 2026-09-29, roadmap item 7: same-seed races and a seed of the
 * week without a server). No ROM, and nothing from one, ever travels: the code carries the
 * game, the settings file's name and checksum, the seed and the checksum of the ROM it made.
 *
 * Text form, one line, case-insensitive:
 * KC1-<game id>-<settings hash>-<seed>-<passes>-<rom crc>-<name>, for example
 * "KC1-emerald-u-3f2a9c1e-5261db990e333467-1-a1b2c3d4-RSE_Kaizo". The name is only for
 * people (spaces become "_"); the settings are matched by content: [settingsHash] is the first
 * 32 bits of the file's SHA-256, the hash a run's recipe keeps. [passes] is one hex digit:
 * the passes the rules add around the settings (ExtraPasses), on or off as the run was made,
 * because the same settings and seed make a different game with the 60% levels or PART 2.
 */
data class RunCode(
    val game: String,
    val settingsHash: Long,
    val seed: Long,
    /** [PRE_PASS] and [PART_2] as the run was made. */
    val passes: Int,
    /** CRC32 of the randomized ROM the sharer got; 0 when not known. */
    val romCrc: Long,
    val settingsName: String,
) {
    val prePass: Boolean get() = passes and PRE_PASS != 0
    val part2: Boolean get() = passes and PART_2 != 0
    /** A Heart & Soul run of the Nat. Dex pool ([HNS_NATDEX_POOL]). */
    val hnsNatDexPool: Boolean get() = passes and HNS_NATDEX_POOL != 0

    /** A pass this app does not know: the code came from a newer KaizoCore, and its run cannot be made here. */
    val unknownPasses: Boolean get() = passes and KNOWN_PASSES.inv() != 0

    fun text(): String = listOf(
        PREFIX, game, "%08x".format(settingsHash), "%016x".format(seed), "%x".format(passes and 0xf), "%08x".format(romCrc),
        settingsName.removeSuffix(".rnqs").replace(Regex("[^A-Za-z0-9.]+"), "_").trim('_').ifBlank { "settings" },
    ).joinToString("-")

    companion object {
        const val PREFIX = "KC1"
        /** The official 60% levels' pre-pass (Emerald, Gold, Silver, Crystal). */
        const val PRE_PASS = 1
        /** Gen 1's second pass, PART 2. */
        const val PART_2 = 2
        /**
         * Not a pass: a Heart & Soul run randomized from the Nat. Dex pool (HnsPool; without it, the Vanilla pool). The same
         * settings and seed make another game from the other pool, so the code says which (2026-10-05). An older app reads
         * it as a pass it does not know and asks for an update, which is right: it cannot make the run.
         */
        const val HNS_NATDEX_POOL = 4
        private const val KNOWN_PASSES = PRE_PASS or PART_2 or HNS_NATDEX_POOL

        fun passesOf(prePass: Boolean, part2: Boolean): Int = (if (prePass) PRE_PASS else 0) or (if (part2) PART_2 else 0)

        /** [settingsHash] from a SHA-256 in hex: its first 32 bits. */
        fun settingsHashOf(sha256Hex: String): Long = sha256Hex.take(8).toLong(16)

        /** A file's SHA-256 in hex, as NextRun's recipe writes it. */
        fun sha256(file: File): String = NextRun.sha256(file)

        /**
         * The code in [text], or null. Tolerant of what chat apps do to a pasted line: spaces,
         * a trailing period, upper case, text around it.
         */
        fun parse(text: String): RunCode? {
            val m = Regex("""(?i)\bKC1-([a-z0-9]+(?:-[a-z0-9]+)*?)-([0-9a-f]{8})-([0-9a-f]{16})-([0-9a-f])-([0-9a-f]{8})-([A-Za-z0-9._]+)""")
                .find(text.trim()) ?: return null
            val v = m.groupValues
            return RunCode(
                game = v[1].lowercase(),
                settingsHash = v[2].toLong(16),
                seed = java.lang.Long.parseUnsignedLong(v[3], 16),
                passes = v[4].toInt(16),
                romCrc = v[5].toLong(16),
                settingsName = v[6].trimEnd('.'),
            )
        }

        fun crc32(file: File): Long = CRC32().also { crc ->
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = input.read(buf); if (n <= 0) break; crc.update(buf, 0, n) }
            }
        }.value
    }
}
