package com.ironmonone.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import java.io.File

/**
 * Gold, Silver and Crystal before a Kaizo IronMON run (2026-09-30, IronMON rules check R3). The rules pages say:
 * "To properly randomize Pokemon Crystal for Ironmon, you must first apply the pseudo-fluctuating growth patch. This
 * is necessary to prevent Pokemon from evolving into legendaries." For Gold and Silver they say the settings "may not
 * work completely" without one (GSC/kaizo.md, GSC/standard.md). Since a game added under My games is ready to run as
 * it is, an unpatched copy reached the Kaizo IronMON screen and ran with nothing said. The screen now says so, and
 * makes the patched copy from the game already on the phone with one tap: no file to find again.
 */
internal object GrowthPatch {
    /** The growth-patched build of [kind], or null when it needs none: already patched, Nat. Dex, or not Gen 2. */
    fun buildFor(kind: RomKind): RomKind? =
        if (kind.generation != Generation.GBC2 || kind.patchTag != null || kind.isNatDex) null
        else RomKind.allPatched.firstOrNull { it.baseId == kind.id && it.patchTag == "pseudofluct" }

    /** What the Kaizo IronMON screen says under an unpatched Gold, Silver or Crystal; null for any other game. */
    fun warning(kind: RomKind): String? {
        buildFor(kind) ?: return null
        return if (kind.id == RomKind.CRYSTAL_U.id)
            "The IronMON rules need the pseudo-fluctuating growth patch on Crystal, to keep Pokémon from evolving into legendaries. This copy does not have it."
        else "The IronMON settings may not work completely on ${kind.displayName.removePrefix("Pokémon ")} without the pseudo-fluctuating growth patch. This copy does not have it."
    }

    const val BUTTON = "Make the patched copy"
    const val FAILED = "Could not make the patched copy"

    /** What the screen says once the copy is made: it is picked, with the mode that was picked. */
    fun made(out: RomKind) = "Made ${out.displayName} and picked it. The mode stays the same."

    /**
     * Applies the bundled patch to [base], the copy of [kind] already on the phone, and stores the result as the
     * patched build. [base] is left as it is: it still plays from My games. A failure is a [PrepFailure], worded for
     * the player, which the job's status shows as it is; as a plain exception it read "Try again" (rc32 audit P3 #62).
     */
    fun make(context: Context, store: PrepStore, kind: RomKind, base: File): RomKind {
        val out = buildFor(kind) ?: throw PrepFailure("This game needs no growth patch.")
        val asset = PrepOptions.forKind(kind).firstOrNull { it.out?.id == out.id }?.asset
            ?: throw PrepFailure("This version of KaizoCore does not carry the growth patch for this game.")
        val patch = store.bundledPatch(context, asset)
            ?: throw PrepFailure("This version of KaizoCore does not carry the growth patch for this game.")
        val tmp = File(context.cacheDir, "growth-${out.id}.${out.fileExtension}")
        // A patch that throws part way leaves nothing behind in the cache (rc32 audit P3 #57).
        val crc = patchInto(tmp) { Patcher.applyFiles(patch, base, tmp, kind.displayName) }
        if (crc != out.expectedCrc) {
            tmp.delete()
            throw PrepFailure("The patch applied, but the result is not a version this app knows. Your copy is probably a different version of the game. Nothing was changed.")
        }
        store.savePrepared(out, tmp, crc)
        return out
    }
}

/** The notice and its one button, under the mode on the Kaizo IronMON screen. */
@Composable
internal fun GrowthPatchNotice(text: String, busy: Boolean, onMake: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper)
        Spacer(Modifier.height(6.dp))
        com.ironmonone.app.gen3.Gen3Button(GrowthPatch.BUTTON, enabled = !busy, onClick = onMake)
    }
}
