package com.ironmonone.tracker

/**
 * Game Boy text, the English games (Red, Blue, Yellow, Gold, Silver, Crystal) (2026-09-30). The tracker only needs it
 * for a Pokemon's nickname, which the Nuzlocke ledger reads to know whether a catch has been named.
 *
 * The charmap is the one pokered and pokecrystal share for names: capitals from 0x80, small letters from 0xA0, digits
 * from 0xF6, a handful of marks, and 0x50 ends the string. A byte that is not text stops the read.
 */
object GbText {

    private val marks = mapOf(
        0x7F to " ", 0x9A to "(", 0x9B to ")", 0x9C to ":", 0x9D to ";", 0x9E to "[", 0x9F to "]",
        0xBA to "e", 0xBB to "'d", 0xBC to "'l", 0xBD to "'s", 0xBE to "'t", 0xBF to "'v",
        0xE0 to "'", 0xE1 to "PK", 0xE2 to "MN", 0xE3 to "-", 0xE6 to "?", 0xE7 to "!", 0xE8 to ".",
        0xF3 to "/", 0xF4 to ",",
        // The male and female signs: dropped, so a Nidoran still called by its default name reads as that name.
        0xEF to "", 0xF5 to "",
    )

    /** The text in [raw] up to the terminator (0x50), as plain text; a byte that is not text ends it. */
    fun decode(raw: ByteArray, from: Int = 0, max: Int = 11): String {
        val sb = StringBuilder()
        var i = from
        val end = minOf(raw.size, from + max)
        while (i < end) {
            val b = raw[i].toInt() and 0xFF
            i++
            when {
                b == 0x50 -> break
                b in 0x80..0x99 -> sb.append('A' + (b - 0x80))
                b in 0xA0..0xB9 -> sb.append('a' + (b - 0xA0))
                b in 0xF6..0xFF -> sb.append('0' + (b - 0xF6))
                marks.containsKey(b) -> sb.append(marks.getValue(b))
                else -> break
            }
        }
        return sb.toString()
    }
}
