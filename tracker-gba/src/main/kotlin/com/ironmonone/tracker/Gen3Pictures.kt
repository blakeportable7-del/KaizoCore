package com.ironmonone.tracker

/**
 * The game's own picture of a Pokemon where it is not its species' plain one: in its shiny colors, Unown in its letter,
 * Deoxys in its game's form. Read out of the ROM being played, as SpriteDecoder reads the plain pictures the GBA card
 * already draws (GbaTracker.sprite), so no art ships.
 *
 * Where the tables are. pret's pokefirered and pokeemerald (src/rom_header_gf.c) put a Game Freak header at 0x08000100:
 * the version, the language, the game's name ("pokemon red version"), then pointers to the front picture, back
 * picture, normal palette and shiny palette tables (0x08000128 on), each table 8 bytes an entry: {data, size, tag} for a
 * picture, {data, tag} for a palette. The randomizer reads the same words (Gen3Constants.efrlgFrontSpritesPointer 0x128,
 * efrlgPokemonPalettesPointer 0x130). Ruby and Sapphire (U) carry no header there (pokeruby's crt0.s writes it for the
 * German release only); their shiny table follows the normal one 440 entries on, as it does in FireRed, LeafGreen and
 * Emerald (pokefirered's and pokeemerald's data.c include palette_table.h, then shiny_palette_table.h), and the Ruby
 * and Sapphire dumps carry it there, every tag in place. Every entry is tagged: a picture or a normal
 * palette with its slot, a shiny palette with its slot plus 500 (SPECIES_SHINY_TAG), so a table read in the wrong
 * place is refused rather than drawn in wrong colors.
 *
 * Proven on the six dumps (Gen3PicturesRomTest): FireRed 1.0 and 1.1, LeafGreen, Emerald, Ruby and Sapphire. The Nat.
 * Dex and MaxDex builds keep the header current too (the same test reads it), but their card draws the shipped menu
 * icons, and a Gen 3 game keeps no shiny icon (its icons share three palettes, gMonIconPaletteIndices), so this is not
 * asked there.
 */
object Gen3Pictures {
    const val UNOWN = 201
    const val DEOXYS = 410
    /** SPECIES_UNOWN_B: Unown's B to Z, ! and ? follow the Egg (412), sharing Unown's palette. */
    const val UNOWN_B = 413
    /** gMonFrontPicTable's length in the five games: species 0 to 411, the Egg, Unown's other 27 letters. */
    const val TABLE_ENTRIES = 440
    /** SPECIES_SHINY_TAG: a shiny palette's tag is its slot plus this. */
    const val SHINY_TAG = 500
    /** sGFRomHeader, right after the cartridge header. */
    const val HEADER = 0x08000100L
    /** MON_PIC_SIZE: one 64x64 frame of a front picture, 4 bits a pixel. */
    const val FRAME = 0x800

    private val ROM = 0x08000000L..0x09FFFFFFL

    /** [width] x [height] ARGB pixels, color 0 clear. Equal by its pixels, so a poll that reads the same picture is no change. */
    class Picture(val width: Int, val height: Int, val argb: IntArray) {
        override fun equals(other: Any?) = this === other || other is Picture && other.width == width && other.height == height && other.argb.contentEquals(argb)
        override fun hashCode() = 31 * (31 * width + height) + argb.contentHashCode()
    }

    /** The header's game name and its picture and palette tables (rom_header_gf.c, monFrontPics to monShinyPalettes). */
    class Header(val gameName: String, val frontPics: Long, val backPics: Long, val palettes: Long, val shinyPalettes: Long)

    /** The Game Freak header, or null where the ROM has none (Ruby and Sapphire) or it does not read as one. */
    fun header(memory: MemoryReader): Header? {
        val b = memory.read(HEADER, 0x38)
        if (b.size < 0x38) return null
        val name = String(b, 8, 32, Charsets.ISO_8859_1).substringBefore('\u0000')
        if (!Regex("pokemon [a-z]+ version").matches(name)) return null
        val tables = LongArray(4) { b.u32(0x28 + it * 4) }
        if (tables.any { it !in ROM }) return null
        return Header(name, tables[0], tables[1], tables[2], tables[3])
    }

    /** Where one game keeps the pictures the card draws. */
    class Tables(
        /** The front pictures the card draws (the map's, SpriteDecoder's), and their normal and shiny palettes. */
        val front: Long,
        val palettes: Long,
        val shinyPalettes: Long,
        /**
         * The game's own front table (the header's), which the battle and the summary screen draw from. On Emerald the
         * map's table is the one Pokemon Jump draws (gMonStillFrontPicTable), whose Deoxys has its Normal form only; the
         * game's own has the Speed form too. Ruby and Sapphire have no header: the map's.
         */
        val ownFront: Long,
    )

    /**
     * The tables for [map]'s game, or null where they cannot be read: no picture table on the map, a header naming
     * other palettes than the map's, or a shiny table that does not carry the shiny tags.
     */
    fun tables(memory: MemoryReader, map: GameMap): Tables? {
        if (map.frontPics == 0L || map.palettes == 0L) return null
        val h = header(memory)
        if (h != null && h.palettes != map.palettes) return null
        val shiny = h?.shinyPalettes ?: (map.palettes + TABLE_ENTRIES * 8L)
        if (entry(memory, shiny, 1, picture = false)?.tag != 1 + SHINY_TAG) return null
        return Tables(map.frontPics, map.palettes, shiny, h?.frontPics ?: map.frontPics)
    }

    /**
     * GetUnownLetterByPersonality: 0 for A, then B to Z, ! and ?, from the low two bits of each byte of the personality
     * (pret's LoadSpecialPokePic).
     */
    fun unownLetter(personality: Long): Int {
        val p = personality and 0xFFFFFFFFL
        return ((((p and 0x03000000L) ushr 18) or ((p and 0x030000L) ushr 12) or ((p and 0x0300L) ushr 6) or (p and 0x03L)) % 28).toInt()
    }

    /**
     * The game's 64x64 picture of [species] where it is not the plain one, else null (the card draws the species'
     * picture, as before), and null where the ROM read fails:
     * - [shiny]: in the shiny palette (GetMonSpritePalFromSpeciesAndPersonality);
     * - Unown: its letter's picture, from [personality] (LoadSpecialPokePic); A is Unown's own;
     * - Deoxys where [gameForm]: the last frame of its picture in the game's own table, which DuplicateDeoxysTiles copies
     *   over the first: FireRed's Attack form, LeafGreen's Defense, Emerald's Speed; Ruby and Sapphire keep one frame. The
     *   battle draws an opponent's without it (HandleLoadSpecialPokePic_DontHandleDeoxys), in its Normal form.
     */
    fun picture(memory: MemoryReader, t: Tables, species: Int, shiny: Boolean, personality: Long, gameForm: Boolean): Picture? {
        val letter = if (species == UNOWN) unownLetter(personality) else 0
        val form = species == DEOXYS && gameForm
        if (!shiny && letter == 0 && !form) return null
        val slot = if (letter > 0) UNOWN_B + letter - 1 else species
        if (slot !in 1 until TABLE_ENTRIES) return null
        val pic = entry(memory, if (form) t.ownFront else t.front, slot, picture = true)?.takeIf { it.tag == slot } ?: return null
        val pal = entry(memory, if (shiny) t.shinyPalettes else t.palettes, slot, picture = false)
            ?.takeIf { it.tag == slot + if (shiny) SHINY_TAG else 0 } ?: return null
        val pixels = lz77At(memory, pic.data)?.takeIf { it.size >= FRAME } ?: return null
        val colors = lz77At(memory, pal.data)?.takeIf { it.size >= 32 } ?: return null
        val frame = if (form) pixels.size / FRAME - 1 else 0
        if (frame == 0 && !shiny && letter == 0) return null
        return Picture(64, 64, SpriteDecoder.frame(pixels, SpriteDecoder.palette(colors), frame))
    }

    /** One table entry: where its data is, and its tag (+6 in a picture's {data, size, tag}, +4 in a palette's {data, tag}). */
    internal class Entry(val data: Long, val tag: Int)

    internal fun entry(memory: MemoryReader, table: Long, slot: Int, picture: Boolean): Entry? {
        val b = memory.read(table + slot * 8L, 8)
        if (b.size < 8) return null
        val data = b.u32(0)
        if (data !in ROM) return null
        return Entry(data, b.u16(if (picture) 6 else 4))
    }

    /** The LZ77 data at [at], reading as much as its header says it can take: its size, and a flag byte every eight. */
    internal fun lz77At(memory: MemoryReader, at: Long): ByteArray? {
        val head = memory.read(at, 4)
        if (head.size < 4 || head.u8(0) != 0x10) return null
        val size = head.u8(1) or (head.u8(2) shl 8) or (head.u8(3) shl 16)
        if (size !in 0x10..0x4000) return null
        return SpriteDecoder.lz77(memory.read(at, 4 + size + (size + 7) / 8))
    }
}
