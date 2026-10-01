package com.ironmonone.app.engine

import com.ironmonone.tracker.Gen1Map
import java.io.File

/**
 * Repel in the Viridian Mart, as both Game Boy reference trackers put it
 * there: on startup they write 0x1E (REPEL) into the ROM over the Mart's
 * second item, 0x2445 in Red and Blue and 0x233E in Yellow (Main.lua:279-285
 * in Ironmon-gen-tracker and in Ironmon-gen-2-tracker, which skips it on
 * Crystal). That item is the Antidote in Red and Blue (script_mart POKE_BALL,
 * ANTIDOTE, ...) and the Potion in Yellow, whose Mart lists a Potion first
 * (POKE_BALL, POTION, ANTIDOTE, ...; pokered and pokeyellow
 * data/items/marts.asm). Both are what the references replace.
 *
 * The core's ROM cannot be written through memory here, so the byte goes into
 * the ROM a Gen 1 run plays, as the randomizer finishes it (Randomizers,
 * after PART 2). Nothing else is touched:
 * - the prepared ROM keeps its pinned CRC. RomIdentity knows a prepared build
 *   by CRC, and a changed file would fall back to a header match and read as
 *   the clean dump (Red + Pseudo-fluctuating would turn into Red);
 * - a randomized ROM was never identified by CRC (the run's kind is stored
 *   with it, PrepStore.loadLastRun), and the tracker names the game from the
 *   header title, which this does not change;
 * - the header's global checksum is left as the randomizer leaves it: stale,
 *   since ZX writes the ROM back without recomputing it
 *   (AbstractGBRomHandler.saveRomFile), and Gambatte does not check it.
 *
 * The write is guarded where the reference's is blind: only a Mart list laid
 * out as the game's own (its 0xFE list marker, the game's item in that slot,
 * or a Repel already there) is changed, so a build with the Mart elsewhere is
 * left alone rather than having a byte of its code overwritten.
 */
object ViridianRepel {
    const val REPEL = 0x1E
    const val ANTIDOTE = 0x0B
    const val POTION = 0x14

    /** A game's Mart: its list's 0xFE marker, the second item's offset (the references'), and the item the game has there. */
    private data class Mart(val list: Int, val slot: Int, val item: Int)

    private fun martOf(map: Gen1Map): Mart =
        if (map == Gen1Map.YELLOW) Mart(0x233B, 0x233E, POTION) else Mart(0x2442, 0x2445, ANTIDOTE)

    /**
     * Puts the Repel in [rom] in place. True when the Mart now sells it (it
     * already did counts); false when this is not a Red, Blue or Yellow whose
     * Mart is where the references write.
     */
    fun apply(rom: ByteArray): Boolean {
        val map = Gen1Map.forRom(rom) ?: return false
        val m = martOf(map)
        if (m.slot >= rom.size || rom[m.list].toInt() and 0xFF != 0xFE) return false
        return when (rom[m.slot].toInt() and 0xFF) {
            REPEL -> true
            m.item -> { rom[m.slot] = REPEL.toByte(); true }
            else -> false
        }
    }

    /** The same on a ROM file, rewritten only when the byte changes. */
    fun apply(file: File): Boolean {
        val rom = file.readBytes()
        val before = rom.copyOf()
        val ok = apply(rom)
        if (ok && !rom.contentEquals(before)) file.writeBytes(rom)
        return ok
    }
}
