package com.ironmonone.tracker

/**
 * Where the overworld lives in each Gen 3 game, for "Play as your Pokemon" (Sprite Is Me,
 * UTDZac, MIT). The emulator side (libretrodroid's sprite_core.h) reads the player's
 * sprite out of the game's RAM on every frame, hides the trainer and draws the
 * replacement; it is given these addresses through [OverworldAddresses.toConfig] and
 * knows nothing else about a game.
 *
 * EVERY VALUE HERE IS PROVEN TWICE, and the second proof does not depend on the first:
 *  - against the pret decompilations' symbol files (github.com/pret/pokefirered,
 *    pokeemerald, pokeruby, branch `symbols`: pokefirered.sym, pokefirered_rev1.sym,
 *    pokeleafgreen.sym, pokeemerald.sym, pokeruby.sym, pokesapphire.sym), read 2026-09-29;
 *  - against each game's own ROM, by OverworldAddressTest: the compiled code of LoadOam,
 *    UpdateOamCoords, GetPlayerFacingDirection, GetPlayerAvatarObjectId,
 *    CheckMovementInputNotOnBike, UpdatePlayerAvatarTransitionState, BlendPalette,
 *    TransferPlttBuffer, SetMainCallback2 and CB2_Overworld is byte for byte the same in all
 *    six retail dumps (five games, FireRed in two revisions) apart from its literal pool (the
 *    two shapes of SetMainCallback2 aside), so finding that code in a dump and reading the pool
 *    gives every address here (and the struct offsets the native code assumes) from the game itself.
 * FireRed v1.1 is checked the same way against its own retail dump (CRC32 84ee4776):
 * its RAM is the same as v1.0's, and only the two callback pointers move (0x14 higher,
 * as pokefirered_rev1.sym says and as CB2_Overworld's place in that ROM says).
 *
 * The Nat. Dex builds are NOT in a table. Their memory layout differs (their IWRAM variables
 * sit 0x430 lower than FireRed's) and their published address table (ROM 0x08000150 on) has
 * no gMain, no sprites and no player avatar, and GbaTracker says nothing there may be
 * hardcoded. [Overworld.resolve] reads their addresses out of the game's own code instead
 * ([OverworldScan]), and MaxDex 1.0's the same way: it is Nat. Dex 1.1.3 grown, its RAM moved
 * again (IWRAM 0x3D0 lower than FireRed's, the overworld's EWRAM 0x9E0 lower), and it publishes
 * no table at all. A hack of one of the five games is served by the base game's addresses
 * when its overworld code is where the base game's is ([OverworldScan.callbacksAt]); one that
 * moved its code is read out of that code the same way, and refused, with a line that says so,
 * when that fails (rc32 audit P2 #87). The native side's own checks (callback2 must be this
 * game's overworld callback) still stand behind both.
 */
data class OverworldAddresses(
    val name: String,
    /** gMain (IWRAM). */
    val main: Long,
    /** Where gMain.oamBuffer starts: 0x38, and 0x3C in Ruby and Sapphire, whose struct has objCount first. */
    val oamBufferOffset: Int,
    /** CB2_Overworld, Thumb bit set, the way gMain.callback2 holds it. */
    val cb2Overworld: Long,
    /** CB2_OverworldBasic, Thumb bit set. */
    val cb2OverworldBasic: Long,
    /** gPlayerAvatar: spriteId at +4, objectEventId at +5, runningState at +2, tileTransitionState at +3. */
    val playerAvatar: Long,
    /** gSprites, 64 of 0x44 bytes. */
    val sprites: Long,
    /** gSpriteCoordOffsetX and Y (s16; EWRAM in FireRed, LeafGreen and Emerald, IWRAM in Ruby and Sapphire). */
    val coordOffsetX: Long,
    val coordOffsetY: Long,
    val plttUnfaded: Long,
    val plttFaded: Long,
    /** gObjectEvents: 16 of 0x24 bytes (IWRAM in Ruby and Sapphire), facingDirection in the low nibble at +0x18. */
    val objectEvents: Long,
) {
    /** The table the emulator side takes, in SpriteOverlay.CFG_* order. */
    fun toConfig(): LongArray = longArrayOf(
        main, oamBufferOffset.toLong(), cb2Overworld, cb2OverworldBasic, playerAvatar,
        sprites, coordOffsetX, coordOffsetY, plttUnfaded, plttFaded, objectEvents,
    )
}

object Overworld {
    val FIRERED_U_V10 = OverworldAddresses(
        name = "FireRed (U) v1.0",
        main = 0x030030F0, oamBufferOffset = 0x38,
        cb2Overworld = 0x080565B5, cb2OverworldBasic = 0x080565A9,
        playerAvatar = 0x02037078, sprites = 0x0202063C,
        coordOffsetX = 0x02021BC8, coordOffsetY = 0x02021BCA,
        plttUnfaded = 0x020371F8, plttFaded = 0x020375F8,
        objectEvents = 0x02036E38,
    )

    /** v1.1: every RAM address as in v1.0, the two callbacks 0x14 higher (pokefirered_rev1.sym). */
    val FIRERED_U_V11 = FIRERED_U_V10.copy(
        name = "FireRed (U) v1.1",
        cb2Overworld = 0x080565C9, cb2OverworldBasic = 0x080565BD,
    )

    /** LeafGreen v1.0 has FireRed v1.0's RAM and code addresses here (pokeleafgreen.sym). */
    val LEAFGREEN_U = FIRERED_U_V10.copy(name = "LeafGreen (U) v1.0")

    val EMERALD_U = OverworldAddresses(
        name = "Emerald (U)",
        main = 0x030022C0, oamBufferOffset = 0x38,
        cb2Overworld = 0x08085E5D, cb2OverworldBasic = 0x08085E51,
        playerAvatar = 0x02037590, sprites = 0x02020630,
        coordOffsetX = 0x02021BBC, coordOffsetY = 0x02021BBE,
        plttUnfaded = 0x02037714, plttFaded = 0x02037B14,
        objectEvents = 0x02037350,
    )

    val RUBY_U = OverworldAddresses(
        name = "Ruby (U) v1.0",
        main = 0x03001770, oamBufferOffset = 0x3C,
        cb2Overworld = 0x080543A5, cb2OverworldBasic = 0x08054399,
        playerAvatar = 0x0202E858, sprites = 0x02020004,
        coordOffsetX = 0x030024D0, coordOffsetY = 0x030027E0,
        plttUnfaded = 0x0202EAC8, plttFaded = 0x0202EEC8,
        objectEvents = 0x030048A0,
    )

    /** Sapphire v1.0 shares Ruby's RAM; its two callbacks are 4 bytes higher (pokesapphire.sym). */
    val SAPPHIRE_U = RUBY_U.copy(
        name = "Sapphire (U) v1.0",
        cb2Overworld = 0x080543A9, cb2OverworldBasic = 0x0805439D,
    )

    val ALL: List<OverworldAddresses> = listOf(FIRERED_U_V10, FIRERED_U_V11, LEAFGREEN_U, EMERALD_U, RUBY_U, SAPPHIRE_U)

    /**
     * The table's addresses for the game [map] identifies, or null: Nat. Dex (see [resolve]) and
     * anything else the tracker cannot name. The tracker's [GameMap.resolve] picks the map from the
     * ROM's header, so a hack that keeps its base game's header code gets the base game's overworld.
     */
    fun forMap(map: GameMap): OverworldAddresses? = ALL.firstOrNull { it.name == map.name }

    fun isNatDex(map: GameMap): Boolean = map.expandedSpeciesIds || map.name == "Nat. Dex"

    /**
     * The addresses for the game that is running. A game the tracker names by its header gets its table when its own
     * code agrees: CB2_OverworldBasic and CB2_Overworld where the table has them ([OverworldScan.callbacksAt], one small
     * read). A hack built from the decompilations keeps its base game's header and moves the code; given the table
     * anyway, the native side never saw its overworld callback and drew nothing, with no word anywhere (rc32 audit
     * P2 #87). Such a build, and Nat. Dex and MaxDex, whose layouts are in no table and move between versions, are read
     * out of the game's own code through [read] ([OverworldScan]). Null when the game is none of these, or the scan
     * refuses it.
     */
    fun resolve(map: GameMap, read: MemoryReader): OverworldAddresses? {
        val table = forMap(map) ?: return if (isNatDex(map)) OverworldScan.find(read, scanName(map)) else null
        if (OverworldScan.callbacksAt(read, table)) return table
        return OverworldScan.find(read, "${table.name}, read from the game")
    }

    /** What a table read out of an expanded build's code is called: MaxDex by its map's name, any other as Nat. Dex. */
    private fun scanName(map: GameMap): String = if (map.nameSet == "maxdex") "${map.name} (read from the game)" else OverworldScan.NAME

    /** A game this has a table for: one of the retail games, or a hack that kept one's header. */
    fun hasTable(map: GameMap): Boolean = forMap(map) != null

    /** Why [resolve] has nothing for [map], in a line a player can read. */
    fun whyNot(map: GameMap): String = when {
        map.nameSet == "maxdex" -> "Play as your Pokemon could not find its way around this MaxDex build."
        isNatDex(map) -> "Play as your Pokemon could not find its way around this Nat. Dex build."
        hasTable(map) -> "Play as your Pokemon could not find its way around this game, so you stay the trainer."
        else -> "This game is not one Play as your Pokemon knows."
    }
}

/**
 * The maths the emulator side does each frame, in Kotlin: the reference the tests hold the
 * native code to (SpriteCoreWasmTest runs libretrodroid's sprite_core.h under WebAssembly
 * and compares it with this) and the readable statement of what the native code means.
 * Anything changed in sprite_core.h changes here, or the parity test goes red.
 */
object OverworldMath {
    const val SCREEN_W = 240
    const val SCREEN_H = 160
    /** The box the Walking Pals art is drawn against: 32x32, centred on the player, resting on its feet. */
    const val BOX = 32
    const val OAM_ENTRIES = 128
    const val FADE_COLORS = 15

    const val FORMAT_0RGB1555 = 0
    const val FORMAT_XRGB8888 = 1
    const val FORMAT_RGB565 = 2

    /** x >> 4 toward minus infinity, which is what the game's `>> 4` on a negative int does. */
    fun floorDiv16(v: Int): Int = v shr 4

    private val TILES = arrayOf(
        intArrayOf(1, 4, 16, 64),   // square:     8x8 16x16 32x32 64x64
        intArrayOf(2, 4, 8, 32),    // horizontal: 16x8 32x8 32x16 64x32
        intArrayOf(2, 4, 8, 32),    // vertical:   8x16 8x32 16x32 32x64
        intArrayOf(0, 0, 0, 0),     // prohibited
    )

    /** Tiles in a sprite of OAM shape [shape] and size [size] (two bits each); 0 for the prohibited shape. */
    fun tileCount(shape: Int, size: Int): Int = TILES[shape and 3][size and 3]

    /** One 15-bit colour through the game's BlendPalette: c + (((blend - c) * coeff) >> 4), each channel. */
    fun gameBlend(c: Int, coeff: Int, blend: Int): Int {
        var out = 0
        for (ch in 0 until 3) {
            var v = (c shr (5 * ch)) and 31
            val b = (blend shr (5 * ch)) and 31
            v += floorDiv16((b - v) * coeff)
            v = v.coerceIn(0, 31)
            out = out or (v shl (5 * ch))
        }
        return out
    }

    /** A fade: coefficient 0-16 towards a 15-bit colour. Coefficient 0 is no fade. */
    data class Fade(val coeff: Int, val color: Int) {
        companion object { val NONE = Fade(0, 0) }
    }

    /**
     * The fade that turned [unfaded] into [shown] (parallel 15-bit colour lists): every
     * coefficient 1-16 with, per channel, every blend value 0-31; the least squared error
     * wins. [Fade.NONE] for identical palettes, null when nothing fits well (a mean squared
     * error over 8 a channel), so a palette that changed some other way is not read as a fade.
     */
    fun estimateFade(unfaded: IntArray, shown: IntArray): Fade? {
        val n = unfaded.size
        if (n == 0 || (0 until n).all { unfaded[it] == shown[it] }) return Fade.NONE
        var best = Int.MAX_VALUE
        var bestCoeff = 0
        var bestColor = 0
        for (coeff in 1..16) {
            var total = 0
            var color = 0
            for (ch in 0 until 3) {
                var bestErr = Int.MAX_VALUE
                var bestC = 0
                for (c in 0 until 32) {
                    var err = 0
                    for (i in 0 until n) {
                        val u = (unfaded[i] shr (5 * ch)) and 31
                        val d = (shown[i] shr (5 * ch)) and 31
                        val p = u + floorDiv16((c - u) * coeff)
                        val e = d - p
                        err += e * e
                    }
                    if (err < bestErr) { bestErr = err; bestC = c }
                }
                total += bestErr
                color = color or (bestC shl (5 * ch))
            }
            if (total < best) { best = total; bestCoeff = coeff; bestColor = color }
        }
        if (best > n * 3 * 8) return null
        return Fade(bestCoeff, bestColor)
    }

    /**
     * The 32x32 box's top-left on the screen, from the player sprite's fields: the game's
     * UpdateOamCoords (x + x2 + centerToCornerVecX, plus the camera offset when the sprite
     * has coordOffsetEnabled), then the feet line (y + y2 - centerToCornerVecY) minus the box height.
     */
    fun box(x: Int, y: Int, x2: Int, y2: Int, vecY: Int, coordOffsetEnabled: Boolean, offX: Int, offY: Int): IntArray {
        val ox = if (coordOffsetEnabled) offX else 0
        val oy = if (coordOffsetEnabled) offY else 0
        val cx = x + x2 + ox
        val feet = y + y2 + oy - vecY
        return intArrayOf(cx - BOX / 2, feet - BOX)
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)

    /**
     * Hide the trainer in a copy of gMain.oamBuffer (1024 bytes): every live entry whose
     * tile is in [base, base + count) gets attr0 bits 8-9 set to 0b10 (OBJ disabled),
     * clearing bit 8 if it was set (an affine entry, which the reflection is). Entries
     * already disabled and the game's dummy entry are left alone. Returns how many changed.
     */
    fun hideTileRange(oam: ByteArray, base: Int, count: Int): Int {
        var n = 0
        for (i in 0 until OAM_ENTRIES) {
            val o = i * 8
            var attr0 = le16(oam, o)
            val tile = le16(oam, o + 4) and 0x3FF
            if (((attr0 shr 8) and 3) == 2) continue
            if (attr0 == 0x00A0 && le16(oam, o + 2) == 0 && le16(oam, o + 4) == 0) continue
            if (tile < base || tile >= base + count) continue
            attr0 = (attr0 and 0x0300.inv()) or 0x0200
            oam[o] = attr0.toByte()
            oam[o + 1] = (attr0 shr 8).toByte()
            n++
        }
        return n
    }

    private fun expand5(v: Int) = (v shl 3) or (v shr 2)

    /** 5-bit channels to the frame's pixel format. */
    fun packPixel(format: Int, r5: Int, g5: Int, b5: Int): Int = when (format) {
        FORMAT_XRGB8888 -> (expand5(r5) shl 16) or (expand5(g5) shl 8) or expand5(b5)
        FORMAT_0RGB1555 -> (r5 shl 10) or (g5 shl 5) or b5
        else -> (r5 shl 11) or (((g5 shl 1) or (g5 shr 4)) shl 5) or b5
    }

    private fun unpack(format: Int, px: Int): IntArray = when (format) {
        FORMAT_XRGB8888 -> intArrayOf((px shr 16) and 255, (px shr 8) and 255, px and 255)
        FORMAT_0RGB1555 -> intArrayOf(expand5((px shr 10) and 31), expand5((px shr 5) and 31), expand5(px and 31))
        else -> {
            val g6 = (px shr 5) and 63
            intArrayOf(expand5((px shr 11) and 31), (g6 shl 2) or (g6 shr 4), expand5(px and 31))
        }
    }

    private fun pack8(format: Int, r: Int, g: Int, b: Int): Int = when (format) {
        FORMAT_XRGB8888 -> (r shl 16) or (g shl 8) or b
        FORMAT_0RGB1555 -> ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
        else -> ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
    }

    fun bytesPerPixel(format: Int) = if (format == FORMAT_XRGB8888) 4 else 2

    /**
     * Draw [sprite] (ARGB ints, [sw] x [sh]) into [frame] with its top-left at ([dx], [dy]),
     * clipped, each colour cut to 15 bits and run through [fade]; a partly transparent
     * pixel is mixed with what is there. The mirror of sprite_core.h's drawSprite.
     */
    fun draw(
        frame: ByteArray, width: Int, height: Int, pitch: Int, format: Int,
        sprite: IntArray, sw: Int, sh: Int, dx: Int, dy: Int, fade: Fade,
    ) {
        val bpp = bytesPerPixel(format)
        val lut = Array(3) { ch ->
            val blend = (fade.color shr (5 * ch)) and 31
            IntArray(32) { q -> (q + floorDiv16((blend - q) * fade.coeff)).coerceIn(0, 31) }
        }
        for (y in 0 until sh) {
            val py = dy + y
            if (py < 0 || py >= height) continue
            for (x in 0 until sw) {
                val px = dx + x
                if (px < 0 || px >= width) continue
                val c = sprite[y * sw + x]
                val a = (c ushr 24) and 255
                if (a == 0) continue
                val r5 = lut[0][((c shr 16) and 255) shr 3]
                val g5 = lut[1][((c shr 8) and 255) shr 3]
                val b5 = lut[2][(c and 255) shr 3]
                val at = py * pitch + px * bpp
                val out: Int
                if (a == 255) {
                    out = packPixel(format, r5, g5, b5)
                } else {
                    var cur = le16(frame, at)
                    if (bpp == 4) cur = cur or ((frame[at + 2].toInt() and 255) shl 16) or ((frame[at + 3].toInt() and 255) shl 24)
                    val d = unpack(format, cur)
                    val r = (expand5(r5) * a + d[0] * (255 - a) + 127) / 255
                    val g = (expand5(g5) * a + d[1] * (255 - a) + 127) / 255
                    val b = (expand5(b5) * a + d[2] * (255 - a) + 127) / 255
                    out = pack8(format, r, g, b)
                }
                frame[at] = out.toByte()
                frame[at + 1] = (out shr 8).toByte()
                if (bpp == 4) { frame[at + 2] = (out shr 16).toByte(); frame[at + 3] = 0 }
            }
        }
    }
}
