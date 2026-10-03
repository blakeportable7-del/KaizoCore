package com.ironmonone.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dabomstew.pkrandomzx.constants.Gen5Constants
import com.dabomstew.pkrandomzx.newnds.NARCArchive
import com.dabomstew.pkrandomzx.newnds.NDSRom
import com.ironmonone.core.RomKind
import java.io.File

/**
 * The DS tracker's pictures for alternate forms, out of the player's own ROM the way [RomSprites] reads the species, so
 * no new art ships. The card asked for the species only, so every form showed its base: Unown's letters, Deoxys, Burmy
 * and Wormadam, Shellos and Gastrodon, Rotom, Giratina, Shaymin, Arceus's types, Basculin, Deerling and Sawsbuck, the
 * Therian forms, Kyurem, Keldeo. Now the tracked Pokemon's form (Gen4.Mon.form) picks the picture, and a form with none
 * here draws the species' own, as before.
 *
 * Only games it was proven on, on the test dumps (RomFormSpritesTest):
 * - Platinum keeps them in poketool/pokegra/pl_otherpoke.narc, beside pl_pokegra.narc, at places the game's code picks
 *   ([PLATINUM]: pret's pokeplatinum, BuildPokemonSpriteTemplate, front pictures only). For these twelve species the
 *   game draws even the first form from there: their own slot in pl_pokegra holds an older drawing, or for Cherrim its
 *   Sunshine form, so every Cherrim showed in the sun. Their form 0 is read from pl_otherpoke too.
 * - HeartGold and SoulSilver keep them in otherpoke.narc, a/1/1/4 in their file table (pokeheartgold's filesystem.mk;
 *   in the dump's file table it is the one archive of 261 files), at the places pokeheartgold's
 *   GetMonSpriteCharAndPlttNarcIdsEx picks ([HGSS]): Platinum's twelve where Platinum has them, then Pichu and its
 *   Spiky-eared form after Giratina, whose four pictures move every palette four on. The game draws all thirteen from
 *   there, Pichu's first form too. SoulSilver has no dump here, but it is proven the same: pokeheartgold's own build
 *   checks (heartgold.us and soulsilver.us filesystem.sha1) give a/0/0/4 and a/1/1/4 one SHA-1 in both games, the
 *   HeartGold dump matches it, and the code that picks the places is the same in both.
 * - Black 2 and White 2 keep them in the species' own archive (a/0/0/4), after Genesect and 35 pictures that are not
 *   Pokemon (Pokestar's props, the two eggs): entry [B2W2_FIRST_FORM] plus the species' form picture offset (its
 *   personal data at +0x1E, the randomizer's bsFormeSpriteOffset) plus the form less one, for each form its personal
 *   data counts (+0x20). Arceus's offset is 0 too, which is Unown's B: Gen 5 keeps no type pictures there, so Arceus
 *   keeps its Normal picture.
 * A patched build reads its base game's places where it was proven to keep that game's archives byte for byte (each is
 * built in RomFormSpritesTest): Platinum Super Kaizo, HeartGold Super Kaizo and IronMON HGSS.
 * Not read, for want of a dump to prove them on: Black and White (the randomizer's numbers would start them at 652),
 * Diamond and Pearl (no species pictures either), and the Faster Black 2 and White 2 builds.
 */
object RomFormSprites {

    /** One alternate form's front picture: the archive, the picture's file in it, and its two palettes' files. */
    data class Form(val species: Int, val form: Int, val narc: String, val picture: Int, val palette: Int, val shinyPalette: Int)

    /**
     * A Platinum species whose pictures are in pl_otherpoke.narc: its forms, the first front picture and the step to
     * the next form's, the first palette, how far on its shiny one is, and the step to the next form's palette.
     */
    private data class Gen4Forms(val species: Int, val forms: Int, val front: Int, val frontStep: Int, val palette: Int, val shinyStep: Int, val paletteStep: Int)

    const val PL_OTHERPOKE = "poketool/pokegra/pl_otherpoke.narc"

    /**
     * BuildPokemonSpriteTemplate's cases with face = front. Each species' pictures are a block of its backs and fronts
     * (in pairs, or all backs then all fronts), and the palettes follow all 154 pictures.
     */
    private val PLATINUM = listOf(
        Gen4Forms(386, 4, 1, 2, 154, 1, 0),     // Deoxys: Normal, Attack, Defense, Speed, one palette
        Gen4Forms(201, 28, 9, 2, 156, 1, 0),    // Unown: A to Z, !, ?
        Gen4Forms(351, 4, 68, 1, 158, 4, 1),    // Castform: Normal, Sunny, Rainy, Snowy
        Gen4Forms(412, 3, 73, 2, 166, 1, 2),    // Burmy: Plant, Sandy, Trash
        Gen4Forms(413, 3, 79, 2, 172, 1, 2),    // Wormadam: Plant, Sandy, Trash
        Gen4Forms(422, 2, 86, 1, 178, 1, 2),    // Shellos: West, East
        Gen4Forms(423, 2, 90, 1, 182, 1, 2),    // Gastrodon: West, East
        Gen4Forms(421, 2, 94, 1, 186, 2, 1),    // Cherrim: Overcast, Sunshine
        Gen4Forms(493, 18, 97, 2, 190, 1, 2),   // Arceus: its 17 types and ???
        Gen4Forms(492, 2, 135, 2, 228, 1, 2),   // Shaymin: Land, Sky
        Gen4Forms(479, 6, 139, 2, 232, 1, 2),   // Rotom: Rotom, Heat, Wash, Frost, Fan, Mow
        Gen4Forms(487, 2, 151, 2, 244, 1, 2),   // Giratina: Altered, Origin
    )

    /** HeartGold's and SoulSilver's otherpoke.narc (pokeheartgold: files/poketool/pokegra/otherpoke.narc is a/1/1/4). */
    const val HGSS_OTHERPOKE = "a/1/1/4"

    /**
     * GetMonSpriteCharAndPlttNarcIdsEx's cases with whichFacing front: Platinum's twelve at Platinum's pictures, and
     * Pichu's two forms at 154 to 157, which put every palette four further on than Platinum's.
     */
    private val HGSS = PLATINUM.map { it.copy(palette = it.palette + 4) } +
        Gen4Forms(172, 2, 155, 2, 252, 1, 2)    // Pichu: Pichu, Spiky-eared

    /** Black 2 and White 2: the species' archive and the personal data (gen5_offsets.ini, PokemonGraphics, PokemonStats). */
    const val B2W2_PICTURES = "a/0/0/4"
    const val B2W2_PERSONAL = "a/0/1/6"

    /** The first form's entry, 685: Genesect's 649 and the randomizer's count of the rest (Gen5Constants, formeSpriteIndex). */
    val B2W2_FIRST_FORM = Gen5Constants.pokemonCount + Gen5Constants.getNonPokemonBattleSpriteCount(Gen5Constants.Type_BW2)

    private const val ARCEUS = 493

    /** The games whose form pictures were proven: on a dump, and SoulSilver by its archives being HeartGold's. */
    private val GAMES = setOf(RomKind.PLATINUM_U.id, RomKind.HEARTGOLD_U.id, RomKind.SOULSILVER_U.id, RomKind.BLACK2_U.id, RomKind.WHITE2_U.id)

    /** The patched builds proven to keep their base game's archives byte for byte, built from the dumps in RomFormSpritesTest. */
    val PATCHED_PROVEN = setOf(RomKind.PLATINUM_SUPERKAIZO.id, RomKind.HEARTGOLD_SUPERKAIZO.id, RomKind.HEARTGOLD_IRONMON.id)

    /** The game whose places [kind] reads: its own, or for a proven patched build its base game's. */
    private fun game(kind: RomKind): String = if (kind.id in PATCHED_PROVEN) kind.baseId ?: kind.id else kind.id

    /** The games, and the patched builds, whose form pictures are read. */
    fun supports(kind: RomKind): Boolean = game(kind) in GAMES

    /**
     * Every form [kind] keeps a picture of, read from [nds]: Platinum's twelve species and HeartGold's and SoulSilver's
     * thirteen from their first form on, the alternate forms of Black 2 and White 2 (their first is the species' own
     * picture), none for a game not proven.
     */
    fun forms(nds: NDSRom, kind: RomKind): List<Form> = when (game(kind)) {
        RomKind.PLATINUM_U.id -> gen4(PLATINUM, PL_OTHERPOKE)
        RomKind.HEARTGOLD_U.id, RomKind.SOULSILVER_U.id -> gen4(HGSS, HGSS_OTHERPOKE)
        RomKind.BLACK2_U.id, RomKind.WHITE2_U.id -> b2w2(NARCArchive(nds.getFile(B2W2_PERSONAL)))
        else -> emptyList()
    }

    private fun gen4(table: List<Gen4Forms>, narc: String): List<Form> = table.flatMap { s ->
        (0 until s.forms).map { form ->
            val palette = s.palette + form * s.paletteStep
            Form(s.species, form, narc, s.front + form * s.frontStep, palette, palette + s.shinyStep)
        }
    }

    private fun b2w2(personal: NARCArchive): List<Form> {
        val out = ArrayList<Form>()
        for (species in 1..649) {
            val p = personal.files.getOrNull(species) ?: continue
            if (species == ARCEUS || p.size <= 0x20) continue
            val count = p[0x20].toInt() and 0xFF
            val offset = (p[0x1E].toInt() and 0xFF) or ((p[0x1F].toInt() and 0xFF) shl 8)
            for (f in 1 until count) {
                val entry = B2W2_FIRST_FORM + offset + f - 1
                out += Form(species, f, B2W2_PICTURES, entry * 20, entry * 20 + 18, entry * 20 + 19)
            }
        }
        return out
    }

    /** One form's picture, as RomSprites decodes a species'. */
    fun decode(narc: NARCArchive, form: Form, shiny: Boolean, gen5: Boolean): RomSprites.Decoded {
        val palette = narc.files[if (shiny) form.shinyPalette else form.palette]
        return if (gen5) RomSprites.decodeGen5(narc.files[form.picture], palette)
        else RomSprites.decodeGen4(narc.files[form.picture], palette, backwards = false)
    }

    /** The cache beside the species': sprites/<kind id>/<species>-<form>.png and <species>-<form>s.png. */
    fun cacheFile(filesDir: File, kind: RomKind, species: Int, form: Int, shiny: Boolean): File =
        File(RomSprites.cacheDir(filesDir, kind), "$species-$form${if (shiny) "s" else ""}.png")

    private fun doneFile(dir: File) = File(dir, "forms.done")

    /** Nothing left to read for [kind] in [dir]: its forms were read once already, or it has none this reads. */
    fun done(dir: File, kind: RomKind): Boolean = !supports(kind) || doneFile(dir).exists()

    /**
     * Decodes every form of [kind] out of [nds] into [dir], skipping what is there, then marks the kind done so a later
     * visit opens nothing. [write] is RomSprites' PNG writer. Returns the pictures written.
     */
    fun decodeAll(nds: NDSRom, dir: File, kind: RomKind, write: (RomSprites.Decoded, File) -> Boolean): Int {
        if (!supports(kind)) return 0
        val forms = forms(nds, kind)
        val narcs = HashMap<String, NARCArchive>()
        var written = 0
        for (f in forms) for (shiny in listOf(false, true)) {
            val out = File(dir, "${f.species}-${f.form}${if (shiny) "s" else ""}.png")
            if (out.exists()) continue
            val narc = narcs.getOrPut(f.narc) { NARCArchive(nds.getFile(f.narc)) }
            val d = runCatching { decode(narc, f, shiny, RomSprites.isGen5(kind)) }.getOrNull() ?: continue
            if (write(d, out)) written++
        }
        doneFile(dir).writeText("${forms.size}\n")
        return written
    }

    /** The file for [species] in [form] if the player's ROM gave one, else null. */
    fun cached(filesDir: File, kind: RomKind?, species: Int, form: Int, shiny: Boolean): File? {
        if (kind == null || species <= 0 || form < 0) return null
        return cacheFile(filesDir, kind, species, form, shiny).takeIf { it.isFile }
    }

    /**
     * What the DS card draws for [species] in [form]: its picture from the player's ROM, or null, and the card then
     * draws the species' own (PcAssets.dsSprite), as it did for every form before: for a game or a form with none read
     * here, and for the first form wherever it is the species' own picture.
     */
    fun sprite(context: android.content.Context, species: Int, form: Int, shiny: Boolean): ImageBitmap? {
        val f = cached(context.filesDir, RomSprites.activeKind, species, form, shiny) ?: return null
        return runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
    }
}
