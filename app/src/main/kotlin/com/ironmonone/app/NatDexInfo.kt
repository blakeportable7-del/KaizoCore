package com.ironmonone.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironmonone.core.RomKind

/**
 * What the Nat. Dex version of a game is, in the player's words, for every place that offers to make it (Blake,
 * 2026-09-30: "there should be a notice on the national dex that the national dex has speed improvements and hidden
 * items marked etc... find a description of the national dex for kaizo patch and add its info"). Read off the Nat. Dex
 * Extension's own wiki (github.com/CyanSMP64/NatDexExtension/wiki: "Complete list of changes from vanilla" and
 * "Quality of Life features"), for the release KaizoCore bundles, 1.2.1; the 1.2.2 Accelerator is not in it.
 */
internal object NatDexInfo {
    const val TITLE = "Make the Nat. Dex version"
    const val WHAT = "What the Nat. Dex version adds"
    const val CREDIT = "From the Nat. Dex Extension by CyanSMP64, version 1.2.1."

    /** One line for a list row: what it is and why to pick it. */
    const val SHORT = "Pokémon from every generation, a faster battle engine, shorter cutscenes and hidden items that sparkle. For the Nat. Dex modes."

    private val everyGame = listOf(
        "Pokémon from every generation up to Legends Z-A, with their forms, and the Fairy type.",
        "Base stats, moves and experience updated to Scarlet and Violet.",
        "A faster battle engine, and several cutscenes removed or sped up.",
        "Hidden items sparkle while they are on screen.",
        "Run from a wild battle with B (not from a shiny one).",
        "Modern HMs: use a field move with just the HM and its badge, and forget HM moves at any time.",
        "A 120-item bag, faster fishing, and a prompt to use another Repel when one runs out.",
    )

    const val BUTTON = "MAKE THE NAT. DEX VERSION"

    /** The Nat. Dex build made from [base], for the two games the Nat. Dex Extension has a patch for; null for any other. */
    fun buildOf(base: RomKind): RomKind? = when (base.id) {
        RomKind.FIRERED_U_V11.id -> RomKind.FIRERED_NATDEX_121
        RomKind.EMERALD_U.id -> RomKind.EMERALD_NATDEX_121
        else -> null
    }

    /** Everything the Nat. Dex version of [base] adds, every game's lines first, then that game's own. */
    fun lines(base: RomKind): List<String> = everyGame + when (base.id) {
        RomKind.FIRERED_U_V11.id -> listOf(
            "Everything Faster FireRed has: instant healing at the PC and shorter errands.",
            "The Move Reminder is in Cinnabar Lab; S.S. Anne rooms are colored by who is inside; an NPC outside Rock Tunnel hands out Flash.",
        )
        RomKind.EMERALD_U.id -> listOf(
            "Everything Faster Emerald has: a shorter intro and instant healing.",
            "Nurses on Route 104 and Route 116, and running in Pacifidlog Town and on Fortree's bridges.",
        )
        else -> emptyList()
    }
}

/**
 * On the Kaizo IronMON screen, under a FireRed 1.1 or Emerald with no Nat. Dex version made yet: what that version is,
 * and the button that makes it from this copy (Blake, 2026-09-30: "make it easier to see the natl dex options for all
 * games that have it"), as GrowthPatchNotice does for Gold, Silver and Crystal.
 */
@Composable
internal fun NatDexNotice(busy: Boolean, onMake: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("This game also has a Nat. Dex version, for the Nat. Dex modes: " + NatDexInfo.SHORT.removeSuffix(" For the Nat. Dex modes."),
            style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight)
        Spacer(Modifier.height(6.dp))
        com.ironmonone.app.gen3.Gen3Button(NatDexInfo.BUTTON, enabled = !busy, onClick = onMake)
    }
}
