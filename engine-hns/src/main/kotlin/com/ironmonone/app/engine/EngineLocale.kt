package com.ironmonone.app.engine

import java.util.Locale

/**
 * Runs a randomizer with the JVM's default locale set to Locale.ROOT, and puts the old one back after (rc32 audit P2
 * #118). Both engines case-convert and format with the default locale: on a Turkish phone "FIRE" lower-cased to "fıre"
 * and a HeartGold starter's text lost a letter, so a seed made other ROM bytes than on any other phone and a run code no
 * longer matched; on a phone with Arabic or Persian digits the log's numbers were written in them. The default is the
 * whole process's, so the rest of the program formats in ROOT for the seconds a randomize takes. Shared by the app
 * (Randomizers.inEngineLocale) and KaizoCore PC, so a seed makes the same bytes on both.
 */
object EngineLocale {
    fun <T> inEngineLocale(block: () -> T): T {
        val default = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.ROOT)
        try {
            return block()
        } finally {
            Locale.setDefault(default)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            Locale.setDefault(Locale.Category.FORMAT, format)
        }
    }
}
