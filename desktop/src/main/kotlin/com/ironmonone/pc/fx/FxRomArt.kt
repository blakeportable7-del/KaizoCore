package com.ironmonone.pc.fx

import com.ironmonone.tracker.HnsPics
import com.ironmonone.tracker.MemoryReader
import java.awt.image.BufferedImage

/**
 * The game's own battle animation art and item icons, read out of the player's ROM (Blake, 2026-10-10: "use the rom
 * assets when you can"), so nothing Nintendo-made ships in the jar. Where each sprite sits comes from
 * assets/hns/fx-art-kaizo.json (tools/hns/fx_art.py, from the build's ELF). It names the build's CRC and a CRC of the
 * art's own bytes ("artCrc"): a run plays a randomized ROM, whose CRC is new every seed while the randomizer leaves the
 * art where the build put it, so the art is trusted when either matches. On any other ROM [ok] is false and every
 * effect falls back to the art drawn in code. Until 2026-10-10 only the build's CRC counted, so a real run never got the
 * ROM's art (nor the items' battle use, which is read from the same build).
 */
class FxRomArt(private val memory: MemoryReader, romCrc: Long, json: String?) {
    private class Entry(val gfx: Long, val gfxSize: Int, val pal: Long, val palSize: Int, val parts: List<Pair<Int, Int>>)

    private val entries: Map<String, Entry>
    val ok: Boolean

    init {
        fun hex(key: String) = json?.let { Regex("\"$key\":\\s*\"([0-9A-F]{8})\"").find(it)?.groupValues?.get(1)?.toLong(16) }
        // One block per sprite: "name": { ... "gfx": "0x..", "gfxSize": n, "pal": "0x..", "palSize": n, "parts": [[w, h], ...] ... }
        val all = if (json == null) emptyMap() else Regex("\"(\\w+)\":\\s*\\{([^{}]*)\\}").findAll(json.substringAfter("\"art\"")).mapNotNull { m ->
            val body = m.groupValues[2]
            fun num(k: String) = Regex("\"$k\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: 0
            val gfx = Regex("\"gfx\":\\s*\"0x([0-9A-F]+)\"").find(body)?.groupValues?.get(1)?.toLong(16) ?: return@mapNotNull null
            val pal = Regex("\"pal\":\\s*\"0x([0-9A-F]+)\"").find(body)?.groupValues?.get(1)?.toLong(16) ?: return@mapNotNull null
            val parts = Regex("\\[\\s*(\\d+),\\s*(\\d+)\\s*\\]").findAll(body.substringAfter("\"parts\"", "")).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
            if (parts.isEmpty()) null else m.groupValues[1] to Entry(gfx, num("gfxSize"), pal, num("palSize"), parts)
        }.toMap()
        ok = all.isNotEmpty() && (hex("crc") == romCrc || hex("artCrc")?.let { it == artCrc(all) } == true)
        entries = if (ok) all else emptyMap()
    }

    /** fx_art.py's artCrc over this ROM: each sprite's gfx bytes then its palette's, by name. */
    private fun artCrc(all: Map<String, Entry>): Long = runCatching {
        val c = java.util.zip.CRC32()
        for (name in all.keys.sorted()) {
            val e = all.getValue(name)
            for ((at, n) in listOf(e.gfx to e.gfxSize, e.pal to e.palSize)) c.update(memory.read(at, n).takeIf { it.size == n } ?: return 0L)
        }
        c.value
    }.getOrDefault(0L)

    private val partCache = HashMap<String, List<BufferedImage>?>()
    private val iconCache = HashMap<Int, BufferedImage?>()

    /** The sheet's parts as pictures (one for most sprites; several for one made of parts, as the ice crystals). */
    fun parts(name: String): List<BufferedImage>? = synchronized(partCache) {
        partCache.getOrPut(name) {
            val e = entries[name] ?: return@getOrPut null
            runCatching { HnsPics.sheet(memory, e.gfx, e.pal, e.parts)?.map { it.toImage() } }.getOrNull()
        }
    }

    /** The first part cut into frames [fw] by [fh], row by row (a sheet of frames stacked down, as the game's are). */
    fun frames(name: String, fw: Int, fh: Int = fw): List<BufferedImage> {
        val sheet = parts(name)?.firstOrNull() ?: return emptyList()
        val out = ArrayList<BufferedImage>()
        var y = 0
        while (y + fh <= sheet.height) { var x = 0; while (x + fw <= sheet.width) { out += sheet.getSubimage(x, y, fw, fh); x += fw }; y += fh }
        return out
    }

    /** [item]'s bag icon, 24 by 24. */
    fun itemIcon(item: Int): BufferedImage? = synchronized(iconCache) {
        iconCache.getOrPut(item) { if (!ok) null else runCatching { HnsPics.itemIcon(memory, item)?.toImage() }.getOrNull() }
    }

    /** [item]'s use in battle (HnsPics.itemBattleUsage): 1 HP, 2 status, 3 both, 4 a stat, 5 Mist, 6 Focus Energy, 9 revive, 10 PP. */
    fun itemUsage(item: Int): Int = if (!ok) 0 else runCatching { HnsPics.itemBattleUsage(memory, item) }.getOrDefault(0)

    private fun com.ironmonone.tracker.Gen3Pictures.Picture.toImage(): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, width, height, argb, 0, width) }

    companion object {
        /** The art for the build KaizoCore PC makes ([rom], the build's bytes), or one that has none when they do not match. */
        fun forBuild(rom: ByteArray): FxRomArt {
            val crc = java.util.zip.CRC32().also { it.update(rom) }.value
            val json = runCatching { com.ironmonone.pc.PcHns.resource("hns/fx-art-kaizo.json").toString(Charsets.UTF_8) }.getOrNull()
            return FxRomArt(com.ironmonone.pc.BridgeReader(rom), crc, json)
        }
    }
}
