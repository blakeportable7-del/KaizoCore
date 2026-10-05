package com.ironmonone.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind

/**
 * One favorite as the no-party card draws it: [name] spelled as the species table spells it (as typed when it is no
 * Pokemon), and [species], the id the card's own sprite source takes, or null when this game has no such Pokemon.
 * The DS card draws by national number (PcAssets.dsSprite, the player's ROM first); the Game Boy and GBA cards by the
 * tracker's species id (Play's spriteFor: the bundled pack, the Nat. Dex pack on a Nat. Dex run).
 */
data class FavoriteIcon(val name: String, val species: Int?)

internal object FavoriteIconsCopy {
    /** The header over the icons: the DS tracker's title screen says "Favorites" (TitleScreen.lua favoritesHeading). */
    const val HEAD = "Favorites"
}

/**
 * The favorites as Pokemon icons (Blake, 2026-10-02, on the DS no-Pokemon card: "favorite sprites will go here"), as
 * the PC trackers draw them on their new-game screens: the Gen 3 tracker's StartupScreen PokemonFavorite1 to 3 under
 * its Favorites header, the Gen 1 and Gen 2 trackers' the same, and the NDS tracker's favorites frame beside its ball
 * picker (TitleScreen.lua). They were one gold line of names.
 */
internal object FavoriteIcons {
    /**
     * [names] as typed, in order, for a game of [kind], by that game's own table: on MaxDex 1.0, MaxDex's numbering
     * (Favorites.idOf with a game), so a Z-A Mega draws its own icon and Greninja-B, which MaxDex lacks, draws none.
     */
    fun of(names: List<String>, kind: RomKind?): List<FavoriteIcon> {
        val max = Favorites.maxDex(kind)
        val ds = kind?.platform == Platform.NDS
        val hns = kind?.isHns == true
        return names.map { it.trim() }.filter { it.isNotEmpty() }.map { typed ->
            val id = Favorites.idOf(typed, kind)?.takeIf { Favorites.inGame(typed, max, kind) }
            // Heart & Soul's card draws by the game's own species (PcAssets.gbaSprite "hns"): Treecko is 252 there, 277 here.
            FavoriteIcon(id?.let { Favorites.nameOf(it, kind) } ?: typed, if (ds) id?.let(Favorites::nationalOf) else if (hns) id?.let(HnsNumbers::fromPack) else id)
        }
    }
}

/**
 * The favorites' icons under their header, wrapping onto as many rows as the card needs (a Nat. Dex run may have
 * nine). Each icon is its Pokemon's name to a screen reader; a favorite with no icon shows its name instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoriteIconRow(icons: List<FavoriteIcon>, spriteOf: @Composable (Int) -> ImageBitmap?, modifier: Modifier = Modifier) {
    if (icons.isEmpty()) return
    Column(modifier) {
        PixText(FavoriteIconsCopy.HEAD, PcRef.FONT, Pc.Header, Modifier.semantics { heading() }, weight = FontWeight.Medium)
        Spacer(Modifier.height(2.rp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.rp), verticalArrangement = Arrangement.spacedBy(2.rp)) {
            for (f in icons) {
                val bmp = f.species?.let { spriteOf(it) }
                if (bmp != null) {
                    Image(bmp, contentDescription = f.name, modifier = Modifier.size(PcRef.ICON.rp), filterQuality = FilterQuality.None)
                } else Box(Modifier.heightIn(min = PcRef.ICON.rp), contentAlignment = Alignment.CenterStart) {
                    PixText(f.name, PcRef.FONT, Pc.Text)
                }
            }
        }
    }
}

/**
 * The favorites as the stream's pictures (StreamFavorites, /favorite/1.png to /favorite/9.png; Blake, 2026-10-03,
 * after UTDZac's Favorites As Sources), drawn the way the no-party card draws them, with no Play screen: StreamHub's
 * source (StreamFavoritesSource) asks [draw] for the game being streamed whenever its favorites or the game change, so
 * a favorite edited on the Kaizo IronMON screen reaches OBS at once. Play made them as it opened until then, and an
 * edit waited for Play to open again. The card's own sources, by [From]:
 *  - DS: PcAssets.dsSprite by national number, as DsBallAndFavorites;
 *  - Game Boy, Nat. Dex and MaxDex: the bundled pack, PcAssets.gbaSprite, as Play's spriteFor (MaxDex's own past 411);
 *  - any other GBA game with a tracker: the front picture in the game's own ROM, which the card reads through the
 *    tracker (GbaTracker.sprite, SpriteDecoder.frontSprite) and this reads from the ROM file ([RomFileReader]);
 *  - a game with no tracker: none, as the card has none to draw from.
 */
internal object StreamFavoritePictures {
    enum class From { DS_SPRITE, PACK, MAXDEX_PACK, HNS_PACK, ROM_SPRITE, NONE }

    /** Where the card of a [platform] game of [kind] draws its favorites from. */
    fun from(platform: Platform, kind: RomKind?): From = when {
        kind?.platform == Platform.NDS -> From.DS_SPRITE   // FavoriteIcons.of numbers a DS game's favorites nationally
        platform == Platform.GBC -> From.PACK
        kind?.isMaxDex == true -> From.MAXDEX_PACK
        kind?.isNatDex == true -> From.PACK                 // the tracker's expandedSpeciesIds
        // Heart & Soul's ROM pictures are its own compressed format; the card draws the pack by the game's species (nameSet "hns").
        kind?.isHns == true -> From.HNS_PACK
        kind != null && platform == Platform.GBA -> From.ROM_SPRITE
        else -> From.NONE
    }

    /** The source StreamHub reads (MainActivity sets it): the game Play opens and its saved favorites, drawn here. */
    fun source(context: android.content.Context): com.ironmonone.app.stream.StreamFavoritesSource {
        val app = context.applicationContext
        return com.ironmonone.app.stream.StreamFavoritesSource.of(PrepStore(app.filesDir)) { s, sp -> draw(app, s, sp)?.let(::streamPng) }
    }

    private fun draw(context: android.content.Context, s: GameSession, species: Int): ImageBitmap? = when (from(s.platform, s.kind)) {
        From.DS_SPRITE -> PcAssets.dsSprite(context, species, false)
        From.PACK -> PcAssets.gbaSprite(context, species, null)
        From.MAXDEX_PACK -> PcAssets.gbaSprite(context, species, "maxdex")
        From.HNS_PACK -> PcAssets.gbaSprite(context, species, "hns")
        From.ROM_SPRITE -> romSprite(s.file, species)?.let { px ->
            android.graphics.Bitmap.createBitmap(px, 64, 64, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        From.NONE -> null
    }

    /** [species]' front picture, 64 by 64, out of the GBA [rom] file, as the tracker reads it from the running game. */
    fun romSprite(rom: java.io.File, species: Int): IntArray? = runCatching {
        java.io.RandomAccessFile(rom, "r").use { raf ->
            val reader = RomFileReader(raf)
            com.ironmonone.tracker.SpriteDecoder.frontSprite(reader, com.ironmonone.tracker.GameMap.resolve(reader), species)
        }
    }.getOrNull()
}

/**
 * A GBA ROM file read as the game sees its cartridge, from 0x08000000: the ROM tables the tracker reads its pictures
 * from. Nothing else is mapped, and a read past the end of the file gives what is there.
 */
internal class RomFileReader(private val raf: java.io.RandomAccessFile) : com.ironmonone.tracker.MemoryReader {
    private val size = raf.length()

    override fun read(address: Long, length: Int): ByteArray {
        val off = address - 0x08000000L
        if (off < 0 || off >= size || length <= 0) return ByteArray(0)
        val n = minOf(length.toLong(), size - off).toInt()
        raf.seek(off)
        return ByteArray(n).also { raf.readFully(it) }
    }
}

/** [b] as a PNG with its see-through parts kept; null if Android cannot write it. */
private fun streamPng(b: ImageBitmap): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    return if (b.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
}

/**
 * The DS no-Pokemon card's lower half: the random ball row, and the favorites beside it where the card is wide enough,
 * else under it, as the NDS tracker keeps its favorites frame beside the ball picker until the first Pokemon. Their
 * icons come by national number, from the player's own ROM first (PcAssets.dsSprite).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DsBallAndFavorites(randomBall: Int?, hgss: Boolean, icons: List<FavoriteIcon>) {
    if (randomBall == null && icons.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        randomBall?.let { RandomBallRow(it, hgss) }
        FavoriteIconRow(icons, { n -> val c = LocalContext.current; remember(n) { PcAssets.dsSprite(c, n, false) } })
    }
}
