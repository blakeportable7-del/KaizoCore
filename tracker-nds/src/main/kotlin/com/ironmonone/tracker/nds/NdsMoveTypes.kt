package com.ironmonone.tracker.nds

import java.io.File

/**
 * The type a DS move takes in battle where the game changes it (2026-10-06), for the card's colour, STAB and
 * effectiveness marks (NdsMoveRules reads the row's type). The battlers' own types already come from the battle's data
 * (NdsTracker.withBattleTypes), so Conversion, Conversion 2, Color Change, Camouflage, Soak, Reflect Type and Transform
 * show on the next read.
 *
 * - Normalize (Gen 4 and 5): every move but Struggle is Normal.
 * - Natural Gift (Gen 5): the held berry's type, from the ROM's own item data (a/0/2/4, an entry per item id: Natural
 *   Gift power at +7, its type in the low five bits of +8, Gen 5's type numbers; checked on Black 2 and White 2: Cheri
 *   Fire, Chesto Water, Pecha Electric, Chilan Normal, Liechi Grass, Enigma Bug). Generation 4 keeps its item data by an
 *   index of its own, not the item id, so Natural Gift stays as the table has it there.
 * - Techno Blast (Gen 5): the Drive's type (NDS-Ironmon-Tracker ItemData.lua: Douse Drive Water, Shock Drive Electric,
 *   Burn Drive Fire, Chill Drive Ice).
 * Judgment and Hidden Power keep NdsMoveRules' own rules. Weather Ball is not changed: the DS tracker reads no weather.
 *
 * The hidden information fence: an opponent's ability counts only once the game has shown it, its held item never.
 */
object NdsMoveTypes {
    fun norm(s: String?): String = s.orEmpty().uppercase().filter { it in 'A'..'Z' }

    val DRIVES: Map<String, String> = mapOf("DOUSEDRIVE" to "WATER", "SHOCKDRIVE" to "ELECTRIC", "BURNDRIVE" to "FIRE", "CHILLDRIVE" to "ICE")

    /** Generation 5's berries' Natural Gift types out of [rom]'s item data, by item id; empty for Generation 4 or an unreadable ROM. */
    fun naturalGiftTypes(rom: File, generation: Int): Map<Int, String> = if (generation != 5) emptyMap() else runCatching {
        NdsRomFile(rom).use { r ->
            val files = NdsForms.narcFiles(r.file("a/0/2/4") ?: return@use emptyMap<Int, String>())
            files.withIndex().mapNotNull { (id, b) ->
                if (b.size < 9 || b.u8(7) == 0) null
                else NdsForms.typeName(5, b.u8(8) and 0x1F).takeIf { it.isNotEmpty() }?.let { id to it }
            }.toMap()
        }
    }.getOrDefault(emptyMap())

    /**
     * [moves] of a battler with [abilityName] (counting only when [abilityKnown]), holding [heldItem] named [itemName]
     * (counting only when [own]), each with the type the game gives it.
     */
    fun moves(
        moves: List<NdsMoveInfo>, abilityName: String, abilityKnown: Boolean, own: Boolean, heldItem: Int, itemName: String,
        naturalGift: Map<Int, String>,
    ): List<NdsMoveInfo> {
        val normalize = abilityKnown && norm(abilityName) == "NORMALIZE"
        return moves.map { m ->
            val t = when {
                normalize && m.name != "Struggle" -> "NORMAL"
                own && m.name == "Natural Gift" -> naturalGift[heldItem]
                own && m.name == "Techno Blast" -> DRIVES[norm(itemName)]
                else -> null
            }
            if (t == null || t.equals(m.type, ignoreCase = true)) m else m.copy(type = t)
        }
    }
}
