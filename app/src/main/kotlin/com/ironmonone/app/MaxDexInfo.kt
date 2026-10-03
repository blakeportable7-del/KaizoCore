package com.ironmonone.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Bps
import java.io.File

/**
 * MaxDex Kaizo IronMON (Trip, Tripc423/Maxdex) in the app: what the player is told about it, and the one question the
 * tracker panel asks of the game in Play.
 *
 * MaxDex 1.0 is FireRed 1.1 patched with Trip's MaxDex.bps (2026-06-25): Nat. Dex 1.1.3 with the 45 Legends Z-A megas,
 * moves and abilities through Gen 9, and a physical or special split by move. The patch is built into the app since
 * rc34, as assets/patches/maxdex-firered-u-v11.bps (PrepOptions names it): Blake, 2026-10-03, asked "can MaxDex.bps be
 * in the apk?", was told it adds about 26 MB and that Trip's repository has no license file, and said "yes". So a
 * FireRed 1.1 is all MaxDex needs. KaizoCore knows the patch by the two checksums it carries, whatever its name, and a
 * copy a player added to the library before then is the same file ([patchFile]). The lines below are written from
 * Trip's wiki (Home and Installation) and from the build itself.
 */
object MaxDexInfo {
    const val TITLE = "MaxDex Kaizo IronMON"
    const val WHAT = "What MaxDex adds"
    const val REPO = "https://github.com/Tripc423/Maxdex"
    const val LABEL = "MaxDex (Nat. Dex with moves and abilities to Gen 9)"
    const val CREDIT = "MaxDex is by Trip (Tripc423), on CyanSixFour's Nat. Dex. KaizoCore makes it from your FireRed with his patch, unchanged."

    /** The patch MaxDex 1.0 is made with: FireRed (USA) 1.1 in, MaxDex 1.0 out (Tripc423/Maxdex, 2026-06-25). */
    const val PATCH_FROM = 0x84EE4776L
    const val PATCH_TO = 0x28C12926L

    /** One line for a list row: what it is, and that nothing has to be added for it. */
    const val SHORT = "Nat. Dex Kaizo with moves and abilities through Gen 9. Trip's patch is built in."

    /** What the MaxDex build is, for the Prepare screen. */
    val lines: List<String> = listOf(
        "1255 Pokémon: the Nat. Dex's 1210 and 45 Mega Evolutions from Legends Z-A.",
        "Moves and abilities from later games, through Gen 9 (not all of them), with their mechanics, and physical or special set by each move.",
        "One mode, Kaizo, on Trip's own settings file, with the rules of Nat. Dex 1.1.3, which MaxDex is built on: a 600 BST starter is allowed.",
        "Made from your FireRed 1.1 with MaxDex.bps from github.com/Tripc423/Maxdex, which is built into KaizoCore.",
        "In this first version, Build your own and Nuzlocke are not on MaxDex.",
    )

    /**
     * Said when the MaxDex version is asked for and Trip's patch cannot be had: the app's own copy could not be unpacked,
     * which on a release build is a phone out of space, and the library holds no copy of its own.
     */
    const val NEED_PATCH = "KaizoCore could not unpack Trip's MaxDex patch, which needs about 26 MB free. " +
        "Free some space on this phone, then make the MaxDex version again. Nothing was changed."

    /** Added to the line a library import says, when the patch added is Trip's. */
    const val PATCH_ADDED = "It is the MaxDex patch KaizoCore has built in: tap PATCH on your FireRed 1.1 in My games and pick MaxDex."

    /** Whether [p] is Trip's MaxDex 1.0 patch, by the two checksums it carries; its file name does not count. */
    fun isPatch(p: LibraryStore.PatchEntry): Boolean = p.forCrc == PATCH_FROM && p.makesCrc == PATCH_TO

    /** The MaxDex build made from [base]: FireRed 1.1 only. */
    fun buildOf(base: RomKind): RomKind? = if (base.id == RomKind.FIRERED_U_V11.id) RomKind.FIRERED_MAXDEX_10 else null

    /**
     * Trip's patch to make MaxDex with. A copy the player added to the library, from before it was built in, is the
     * same file: it is used while it is there and whole (its own checksum holds), so the app's copy is never unpacked
     * beside it, and either way the same MaxDex 1.0 is made, in the one place. Otherwise the app's own, [asset] in
     * assets/patches, unpacked on first use and kept (PrepStore.bundledPatch). Null when neither can be had: the
     * unpack failed (a phone out of space), or a build without the patches folder (-Pironmon.lite, -Pironmon.phone)
     * and nothing in the library.
     */
    fun patchFile(context: Context, store: PrepStore, asset: String): File? =
        store.library.listPatches().firstOrNull { isPatch(it) && whole(it.file) }?.file ?: store.bundledPatch(context, asset)

    /** Whether the BPS at [f] is whole: its own checksum holds. Nothing is written beside it (it is the player's file). */
    private fun whole(f: File): Boolean = runCatching { Bps.info(f.readBytes()).patchIntact }.getOrDefault(false)

    /**
     * The library's [patches] that fit [game], less Trip's patch where the game offers MaxDex as a built-in: there it
     * is the MaxDex option, and it was listed, and counted on the game's card, a second time as a patch of its own.
     */
    fun fitting(patches: List<LibraryStore.PatchEntry>, game: LibraryStore.Entry): List<LibraryStore.PatchEntry> {
        val builtIn = PrepRun.builtIns(game).any { it.mode == PrepOptions.Mode.MAXDEX }
        return patches.filter { it.matches(game) && !(builtIn && isPatch(it)) }
    }
}

/**
 * Whether the game in Play is MaxDex 1.0, read from the session the way the panel's rule helpers read it
 * (bstLinesInPlay). The Walking Pals icons' numbering (WalkingPals.trackerDex) and the Freeze-Dry matchup follow it;
 * false wherever there is no session.
 */
@Composable
fun maxDexInPlay(attempt: Int): Boolean {
    val context = LocalContext.current
    return remember(attempt) {
        runCatching { PrepStore(context.applicationContext.filesDir).session().kind?.isMaxDex == true }.getOrDefault(false)
    }
}
