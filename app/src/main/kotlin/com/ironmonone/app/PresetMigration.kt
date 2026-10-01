package com.ironmonone.app

import java.io.File
import java.security.MessageDigest

/**
 * Putting the bundled presets on disk, and bringing an older install's copies
 * up to date. Seeding only ever adds a missing file, so a preset replaced in a
 * later release never reached anyone who had run the app before. Now a file is
 * replaced once, and only when it is byte for byte a copy the app shipped
 * (its SHA-256 in [REPLACED]); an edited copy has other bytes and is never
 * touched, and neither is a file the player imported under that name.
 */
object PresetMigration {

    /** Bundled preset name to the SHA-256 of every earlier copy of it the app shipped. */
    val REPLACED: Map<String, Set<String>> = mapOf(
        // Shipped 2026-08-31 to 2026-09-29: the DS tracker's DiamondPearlPlatinum_Kaizo.rnqs,
        // which is the settings page's 4.4.0 "no extra berries" Kaizo. Under ZX 4.6.1 it matched
        // neither official string. Now the page's 4.6.0+ Kaizo, which gives the starter a random
        // held item as DPPt Standard, Ultimate and Survival do.
        "DPPt Kaizo.rnqs" to setOf("8ac58edacf88c20920cb7ef1b1925cb37fec45be1d5d2f329448955672c661d5"),
        // Shipped 2026-08-30 to 2026-09-29: the Gen 3 tracker's RSE Kaizo.rnqs, the page's Kaizo
        // plus "randomize catching tutorial". Now the page's string itself.
        "RSE Kaizo.rnqs" to setOf("7af2e62b8020d69fc92b5ff7ff0a54203429d72e412a311fbe5e411a6853cb2e"),
    )

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Replaces each preset in [dir] that is an earlier bundled copy with the current one. Returns the names replaced. */
    fun migrate(dir: File, bundled: (String) -> ByteArray?): List<String> = REPLACED.mapNotNull { (name, old) ->
        val f = File(dir, name)
        val now = f.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() } ?: return@mapNotNull null
        if (sha256(now) !in old) return@mapNotNull null
        val fresh = bundled(name)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        runCatching { f.writeBytes(fresh) }.getOrNull()?.let { name }
    }

    /**
     * What PrepStore.seedBundledPresets does: the migration, then every bundled
     * preset in [names] that is missing from [dir]. Returns how many were added.
     */
    fun seed(dir: File, names: List<String>, bundled: (String) -> ByteArray?): Int {
        migrate(dir, bundled)
        var added = 0
        for (name in names) {
            val dest = File(dir, name)
            if (dest.exists()) continue
            val bytes = bundled(name) ?: continue
            if (runCatching { dest.writeBytes(bytes) }.isSuccess) added++
        }
        return added
    }
}
