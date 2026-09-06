package com.ironmonone.app

import com.dabomstew.pkrandomzx.newnds.NARCArchive
import com.dabomstew.pkrandomzx.newnds.NDSRom
import com.ironmonone.core.RomKind
import java.io.File

/**
 * Pokémon sprites decoded from the player's OWN DS ROM, so the app ships no
 * Nintendo art for Gen 4 and 5.
 *
 * The algorithm is the randomizer's own: Gen4RomHandler.getMascotImage and
 * Gen5RomHandler.getMascotImage in the vendored ZX source decode one sprite
 * each for the desktop GUI's mascot, using java.awt. This is the same read,
 * ported to plain int arrays (Android has no AWT) and run for every species
 * once per ROM, into a PNG cache the tracker card reads before it falls back
 * to anything bundled.
 *
 * Where the sprites live (gen4_offsets.ini / gen5_offsets.ini,
 * `File<PokemonGraphics>`): Platinum `poketool/pokegra/pl_pokegra.narc`,
 * HeartGold and SoulSilver `a/0/0/4`, Black, White, Black 2 and White 2
 * `a/0/0/4`. Gen 4 keeps six files per species (female back, male back,
 * female front, male front, palette, shiny palette), each 80x80 at 4 bpp
 * behind a 48-byte header and XOR-encrypted with the game's LCG. Gen 5 keeps
 * twenty per species; the front sprite is LZ-compressed, drawn as a 64x144
 * tiled strip and unscrambled onto 96x96; the palette is at +18 (+19 shiny).
 */
object RomSprites {

    data class Decoded(val width: Int, val height: Int, val argb: IntArray)

    /** The archive inside the ROM, per game; null for a console with no such archive. */
    fun narcPath(kind: RomKind): String? = when (kind.id) {
        "platinum-u" -> "poketool/pokegra/pl_pokegra.narc"
        "heartgold-u", "soulsilver-u" -> "a/0/0/4"
        "black-u", "white-u", "black2-u", "white2-u" -> "a/0/0/4"
        else -> null
    }

    fun isGen5(kind: RomKind): Boolean = kind.generation == com.ironmonone.core.Generation.NDS5

    // ------------------------------------------------------------------ bits
    private fun word(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    /** GFXFunctions.conv16BitColorToARGB: BGR555 to opaque ARGB, 8.25 per step as the reference does. */
    fun argb555(v: Int): Int {
        val r = ((v and 0x1F) * 8.25).toInt(); val g = (((v and 0x3E0) shr 5) * 8.25).toInt(); val b = (((v and 0x7C00) shr 10) * 8.25).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** A 16-entry palette from the NCLR-style file: sixteen BGR555 words at +40; entry 0 stays transparent. */
    fun palette(raw: ByteArray): IntArray {
        val p = IntArray(16)
        for (i in 1 until 16) if (40 + i * 2 + 1 < raw.size) p[i] = argb555(word(raw, 40 + i * 2))
        return p
    }

    /** GFXFunctions.drawTiledImage: 8x8 tiles, [bpp] bits per pixel, low bits first. */
    fun drawTiled(data: ByteArray, palette: IntArray, offset: Int, width: Int, height: Int, bpp: Int): IntArray {
        val ppb = 8 / bpp
        require(width * height / ppb + offset <= data.size) { "image data too short" }
        val bytesPerTile = 64 / ppb
        val tiles = width * height / 64
        val tilesWide = width / 8
        val out = IntArray(width * height)
        for (t in 0 until tiles) {
            val tx = t % tilesWide; val ty = t / tilesWide
            for (y in 0 until 8) for (x in 0 until 8) {
                var v = data[t * bytesPerTile + y * 8 / ppb + x / ppb + offset].toInt() and 0xFF
                if (ppb != 1) v = (v ushr ((x % ppb) * bpp)) and ((1 shl bpp) - 1)
                out[(ty * 8 + y) * width + tx * 8 + x] = palette[v]
            }
        }
        return out
    }

    // ----------------------------------------------------------------- Gen 4
    /**
     * Gen4RomHandler.getMascotImage: 3200 words after a 48-byte header, XOR'd
     * with an LCG keyed from the first word (Diamond and Pearl: from the last
     * word, running backwards). Each word holds four 4-bit pixels; the rows
     * are 40 words wide but only the left 80 pixels are the sprite.
     */
    fun decodeGen4(rawSprite: ByteArray, rawPalette: ByteArray, backwards: Boolean): Decoded {
        require(rawSprite.size >= 48 + 6400) { "Gen 4 sprite file too short: ${rawSprite.size}" }
        val d = IntArray(3200) { word(rawSprite, it * 2 + 48) }
        if (!backwards) {
            var key = d[0]
            for (i in 0 until 3200) { d[i] = d[i] xor (key and 0xFFFF); key = key * 0x41C64E6D + 0x6073 }
        } else {
            var key = d[3199]
            for (i in 3199 downTo 0) { d[i] = d[i] xor (key and 0xFFFF); key = key * 0x41C64E6D + 0x6073 }
        }
        val pal = palette(rawPalette)
        val out = IntArray(80 * 80)
        for (y in 0 until 80) for (x in 0 until 80) {
            out[y * 80 + x] = pal[(d[y * 40 + x / 4] shr ((x % 4) * 4)) and 0x0F]
        }
        return Decoded(80, 80, out)
    }

    // ----------------------------------------------------------------- Gen 5
    /**
     * Gen5RomHandler.getMascotImage: the LZ-compressed front picture, drawn
     * as a 64x144 tiled strip (48 bytes of header), then the strip's lower
     * 80 rows are laid onto a 96x96 canvas in the order the reference draws
     * them. The (dst, src) rectangles below are its fourteen drawImage calls.
     */
    fun decodeGen5(compressedPic: ByteArray, rawPalette: ByteArray): Decoded {
        val pic = zxcompressors.DSDecmp.Decompress(compressedPic) ?: error("could not decompress the Gen 5 sprite")
        val strip = drawTiled(pic, palette(rawPalette), 48, 64, 144, 4)
        val out = IntArray(96 * 96)
        fun blit(dx: Int, dy: Int, dx2: Int, dy2: Int, sx: Int, sy: Int) {
            for (y in 0 until (dy2 - dy)) for (x in 0 until (dx2 - dx)) out[(dy + y) * 96 + dx + x] = strip[(sy + y) * 64 + sx + x]
        }
        blit(0, 0, 64, 64, 0, 0)
        blit(64, 0, 96, 8, 0, 64); blit(64, 8, 96, 16, 32, 64)
        blit(64, 16, 96, 24, 0, 72); blit(64, 24, 96, 32, 32, 72)
        blit(64, 32, 96, 40, 0, 80); blit(64, 40, 96, 48, 32, 80)
        blit(64, 48, 96, 56, 0, 88); blit(64, 56, 96, 64, 32, 88)
        blit(0, 64, 64, 96, 0, 96)
        blit(64, 64, 96, 72, 0, 128); blit(64, 72, 96, 80, 32, 128)
        blit(64, 80, 96, 88, 0, 136); blit(64, 88, 96, 96, 32, 136)
        return Decoded(96, 96, out)
    }

    // ------------------------------------------------------------ the cache
    /** PNGs per ROM kind: sprites/<kind id>/<species>.png and <species>s.png. */
    fun cacheDir(filesDir: File, kind: RomKind): File = File(filesDir, "sprites/${kind.id}")
    fun cacheFile(filesDir: File, kind: RomKind, species: Int, shiny: Boolean): File =
        File(cacheDir(filesDir, kind), "$species${if (shiny) "s" else ""}.png")

    /** The kind whose cache the tracker card reads from; set by the Play screen for a DS session. */
    @Volatile var activeKind: RomKind? = null

    /**
     * Decode every species of [kind] out of [rom] into the cache. Idempotent:
     * files that exist are skipped, so a second visit costs a directory
     * listing. Runs on the caller's thread; the Play screen puts it on IO.
     * Returns the number of sprites written, or -1 when the ROM has no
     * archive this decoder knows.
     */
    fun decodeAll(filesDir: File, rom: File, kind: RomKind, maxSpecies: Int, onProgress: ((Int) -> Unit)? = null): Int {
        val path = narcPath(kind) ?: return -1
        val dir = cacheDir(filesDir, kind).apply { mkdirs() }
        android.util.Log.i("KaizoCore", "RomSprites: ${kind.id} cache holds ${dir.list()?.size ?: 0} files before decoding")
        if (File(dir, "$maxSpecies.png").exists() && File(dir, "1.png").exists()) return 0
        val nds = NDSRom(rom.absolutePath)
        val narc = NARCArchive(nds.getFile(path))
        val gen5 = isGen5(kind)
        val backwards = false   // Diamond and Pearl are not kinds this app knows; Platinum and HGSS run forwards
        var written = 0
        for (species in 1..maxSpecies) {
            for (shiny in listOf(false, true)) {
                val out = File(dir, "$species${if (shiny) "s" else ""}.png")
                if (out.exists()) continue
                val decoded = runCatching {
                    if (gen5) {
                        val pal = narc.files[species * 20 + 18 + (if (shiny) 1 else 0)]
                        decodeGen5(narc.files[species * 20], pal)
                    } else {
                        var raw = narc.files[species * 6 + 3]                  // male front
                        if (raw.isEmpty()) raw = narc.files[species * 6 + 2]  // the other gender's
                        decodeGen4(raw, narc.files[species * 6 + 4 + (if (shiny) 1 else 0)], backwards)
                    }
                }.getOrNull() ?: continue
                if (writePng(decoded, out)) written++
                if (species == 1 && !shiny) android.util.Log.i("KaizoCore", "RomSprites: first sprite of ${kind.id} written to ${out.parentFile}")
            }
            onProgress?.invoke(species)
        }
        android.util.Log.i("KaizoCore", "RomSprites: ${kind.id} decoded $written new sprites; cache now ${dir.list()?.size ?: 0} files")
        return written
    }

    private fun writePng(d: Decoded, out: File): Boolean = runCatching {
        val bmp = android.graphics.Bitmap.createBitmap(d.argb, d.width, d.height, android.graphics.Bitmap.Config.ARGB_8888)
        val tmp = File(out.parentFile, out.name + ".tmp")
        tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(out)
    }.getOrDefault(false)
}
