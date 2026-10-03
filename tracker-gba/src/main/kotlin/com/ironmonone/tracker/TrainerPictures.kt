package com.ironmonone.tracker

/**
 * A trainer's picture, out of the ROM being played (the log viewer's portraits, rc34). The trainer's gTrainers entry
 * names its slot (trainerPic, TrainerLayout.picOffset) in pret's gTrainerFrontPicTable, {LZ77 data, size, tag} entries,
 * and gTrainerFrontPicPaletteTable, {LZ77 data, tag}: a 64x64 picture of 8x8 tiles at four bits a pixel, and its
 * sixteen colors. Nothing ships with the app.
 *
 * Where the tables are is on each GameMap (trainerPics), found on the vendored dumps by their shape (a run of entries
 * tagged 0, 1, 2 ... whose data unpacks to one 64x64 picture or to sixteen colors, the palettes right after the
 * pictures) and checked by eye: Brock, Misty, Lt. Surge and Giovanni on FireRed 1.0, Roxanne, Brawly and Wattson on
 * Emerald, the first two leaders on FireRed 1.1, LeafGreen, Ruby, Sapphire, both Nat. Dex 1.2.1 builds and MaxDex 1.0.
 * Each entry carries its slot as its tag, so a table read in the wrong place, or a slot past its end, gives no picture
 * rather than a wrong one. The entries, the LZ77 and the tiles are read as Gen3Pictures reads a Pokemon's.
 */
object TrainerPictures {
    /** A picture's side, in pixels. */
    const val SIZE = 64

    /** The 64x64 ARGB picture in [slot] of [map]'s tables, color 0 clear; null where the build has none or a read fails. */
    fun picture(memory: MemoryReader, map: GameMap, slot: Int): IntArray? {
        if (map.trainerPics == 0L || map.trainerPicPalettes == 0L || slot !in 0 until map.trainerPicCount) return null
        val pic = Gen3Pictures.entry(memory, map.trainerPics, slot, picture = true)?.takeIf { it.tag == slot } ?: return null
        val pal = Gen3Pictures.entry(memory, map.trainerPicPalettes, slot, picture = false)?.takeIf { it.tag == slot } ?: return null
        val pixels = Gen3Pictures.lz77At(memory, pic.data)?.takeIf { it.size >= Gen3Pictures.FRAME } ?: return null
        val colors = Gen3Pictures.lz77At(memory, pal.data)?.takeIf { it.size >= 32 } ?: return null
        return SpriteDecoder.frame(pixels, SpriteDecoder.palette(colors), 0)
    }

    /**
     * The head of a 64x64 picture: [w] x [h] from its first colored row down, centered on that row's first twelve rows
     * (the hat and face, which a raised arm below does not pull aside). The Trainers tab's icon, the reference's player
     * head (LogTabTrainers.TabIcons), cut from the player's own picture. Clear where the picture has nothing.
     */
    fun head(px: IntArray, w: Int = 24, h: Int = 22): IntArray {
        val out = IntArray(w * h)
        val top = (0 until SIZE).firstOrNull { y -> (0 until SIZE).any { x -> px[y * SIZE + x] != 0 } } ?: return out
        var sum = 0L; var n = 0
        for (y in top until minOf(SIZE, top + 12)) for (x in 0 until SIZE) if (px[y * SIZE + x] != 0) { sum += x; n++ }
        val cx = if (n == 0) SIZE / 2 else Math.round(sum.toDouble() / n).toInt()
        for (y in 0 until h) for (x in 0 until w) {
            val sx = cx - w / 2 + x; val sy = top + y
            if (sx in 0 until SIZE && sy in 0 until SIZE) out[y * w + x] = px[sy * SIZE + sx]
        }
        return out
    }
}
