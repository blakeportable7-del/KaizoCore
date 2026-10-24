// The desktop's side of what the PC tracker's files call in the rest of the app (KaizoCore PC, PLAN row 3). The app's
// own versions live in files that are not shared (they reach Android or the rest of the app), so KaizoCore PC declares
// the same names here: the run storage seams as KaizoCore PC has them, the phone-only features off (GachaMon, the DS
// view, the HUD, Nuzlocke, ROM sprite caches), and the small pieces the tracker needs from files it does not share
// (GearButton, GearToggle, PcTap, hgssBadgeRows, LogSuggest, logTitle) are copied from the app's files when pcui is built
// (pcui/build.gradle.kts, copyAppPieces), so they cannot drift.
@file:Suppress("unused", "UNUSED_PARAMETER")
package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import java.text.Normalizer

object SaveTrouble {
    const val NOTES = "notes"
    const val SETTING = "setting"
    const val NOTES_FAILED = "Could not save your tracker notes and settings. If this computer is out of space, free some; they save again with your next change."
    /** KaizoCore PC's log hears a failed save (the phone shows a line); set by the session. */
    @Volatile var onReport: (String, String) -> Unit = { _, s -> System.err.println(s) }
    fun report(kind: String, sentence: String, now: Long = System.currentTimeMillis()) = onReport(kind, sentence)
}

internal object StateSlots {
    fun replace(tmp: File, target: File) {
        java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}

object RunModeName {
    fun ofPrep(prepDir: File, kindId: String?, settingsName: String): String = settingsName
}

internal class DsViewState {
    var locked by mutableStateOf<NdsTrackedMon?>(null)
    fun shownEnemy(state: NdsTrackerState): NdsTrackedMon? = null
}
internal val dsView = DsViewState()

object TrackerBackground { fun boxFill(c: Color): Color = c }

class HudPortal { var left by mutableStateOf<(@Composable () -> Unit)?>(null) }
val LocalHudPortal = androidx.compose.runtime.compositionLocalOf<HudPortal?> { null }
object TrackerHud { const val ENABLED = true }

object NuzlockeTracking { fun inPlay(): Boolean = false }

object GachaMonShown {
    fun carousel(itemOn: Boolean, packOnTracker: Boolean, newestWaiting: Boolean, inBattle: Boolean): Boolean = false
}
object GachaMonOptions { var showPack by mutableStateOf(false) }
object GachaMon { var newest by mutableStateOf<Any?>(null) }
@Composable fun GachaMonCarouselLine(onTap: () -> Unit) {}

object RomSprites {
    @Volatile var activeKind: RomKind? = null
    fun cacheFile(filesDir: File, kind: RomKind, species: Int, shiny: Boolean): File = File(filesDir, "romsprites/$kind/$species.png")
}

/**
 * The phone's Keystore-sealed file (SealedFile.kt), which TwitchAuth.kt's KeystoreTokenStore names: never used on the
 * desktop, whose sign-in lives in KaizoCore PC's DpapiTokenStore (Windows' own Data Protection API). Here it holds nothing.
 */
class KeystoreSealedFile(private val file: File, private val alias: String) {
    fun read(): ByteArray? = null
    fun write(bytes: ByteArray) { throw java.io.IOException("the Keystore is the phone's; KaizoCore PC keeps its sign-in with Windows (DpapiTokenStore)") }
    fun clear() { file.delete() }
}

/** The favorites of a run, in KaizoCore PC's run folder (favorites.txt); [romId] is not needed there, one run a folder. */
class PrepStore(val filesDir: File) {
    private val file get() = File(filesDir, "favorites.txt")
    fun favoritesText(romId: String?): String = runCatching { file.readText() }.getOrDefault("")
    fun saveFavorites(romId: String?, text: String) { filesDir.mkdirs(); file.writeText(text) }
}
