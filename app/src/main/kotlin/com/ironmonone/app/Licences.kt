package com.ironmonone.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The licences of what the APK carries, with their full texts (rc32 audit P3 #4). The APK shipped one licence text,
 * the font's, while it carries GPL-3.0 and GPL-2.0 cores and code, the MPL-2.0 mGBA core, Apache-2.0 libraries and the
 * MIT trackers and libraries whose notices must go with them. The texts are in assets/licenses, each copied from a copy
 * on the build machine (the repository's own LICENSE files, the reference checkouts, mGBA's tree), never fetched.
 * NOTICE is the record; this is the page that ships.
 */
internal object Licences {
    /** A licence's full text in the app's assets. */
    data class Text(val id: String, val name: String, val asset: String)

    /** What the APK carries under the licence [licence] (an id of [texts]), and where its source is when the licence asks. */
    data class Part(val name: String, val what: String, val licence: String, val source: String? = null)

    val texts = listOf(
        Text("GPL-3.0", "GNU General Public License, version 3", "licenses/GPL-3.0.txt"),
        Text("GPL-2.0", "GNU General Public License, version 2", "licenses/GPL-2.0.txt"),
        Text("MPL-2.0", "Mozilla Public License 2.0", "licenses/MPL-2.0.txt"),
        Text("BSD-3-Clause-inih", "BSD 3-Clause license (inih)", "licenses/BSD-3-Clause-inih.txt"),
        Text("PublicDomain-mGBA", "Public domain (MurmurHash3, the LZMA SDK)", "licenses/PublicDomain-mGBA.txt"),
        Text("Apache-2.0", "Apache License 2.0", "licenses/Apache-2.0.txt"),
        Text("MIT-rcheevos", "MIT license (rcheevos)", "licenses/MIT-rcheevos.txt"),
        Text("MIT-libretro-common", "MIT license (libretro-common)", "licenses/MIT-libretro-common.txt"),
        Text("MIT-Ironmon-Tracker", "MIT license (IronMON Tracker)", "licenses/MIT-Ironmon-Tracker.txt"),
        Text("MIT-Ironmon-gen-tracker", "MIT license (Gen 1 IronMON Tracker)", "licenses/MIT-Ironmon-gen-tracker.txt"),
        Text("MIT-Ironmon-gen-2-tracker", "MIT license (Gen 2 IronMON Tracker)", "licenses/MIT-Ironmon-gen-2-tracker.txt"),
        Text("MIT-CalcAtk", "MIT license (Calc Atk)", "licenses/MIT-CalcAtk.txt"),
        Text("MIT-SpriteIsMe", "MIT license (Sprite Is Me)", "licenses/MIT-SpriteIsMe.txt"),
        Text("MIT-DeathQuotes", "MIT license (Death Quotes)", "licenses/MIT-DeathQuotes.txt"),
        Text("MIT-AutoPokemonThemes", "MIT license (Auto Pokémon Themes)", "licenses/MIT-AutoPokemonThemes.txt"),
        Text("MIT-FavoritesAsSources", "MIT license (Favorites As Sources)", "licenses/MIT-FavoritesAsSources.txt"),
        Text("MIT-PokemonShowdown", "MIT license (Pokémon Showdown)", "licenses/MIT-PokemonShowdown.txt"),
        Text("OFL-1.1", "SIL Open Font License 1.1", "OFL_press_start_2p.txt"),
        Text("CC-BY-NC-4.0", "Creative Commons Attribution-NonCommercial 4.0", "licenses/CC-BY-NC-4.0.txt"),
    )

    val parts = listOf(
        Part("KaizoCore", "This app, with its changes to the randomizers, to LibretroDroid and to the melonDS core.", "GPL-3.0",
            "github.com/blakeportable7-del/KaizoCore"),
        Part("Universal Pokémon Randomizer ZX", "By Ajarmar, on Dabomstew's Universal Pokémon Randomizer.", "GPL-3.0",
            "github.com/Ajarmar/universal-pokemon-randomizer-zx"),
        Part("The Nat. Dex randomizer", "CyanSixFour's fork of the randomizer.", "GPL-3.0",
            "github.com/CyanSMP64/universal-pokemon-randomizer-zx"),
        Part("LibretroDroid 0.13.2", "By Swordfish90, the emulators' host.", "GPL-3.0", "github.com/Swordfish90/LibretroDroid"),
        Part("melonDS libretro core", "The DS emulator, built from its source with four fixes: ours to how a game is closed " +
            "and to a saved state loaded in the middle of a 3D scene, and melonDS's own to its 3D drawing and to the Wi-Fi " +
            "settings it gives a game. All are in KaizoCore's source, beside the commit it is built from.", "GPL-3.0",
            "github.com/libretro/melonDS"),
        Part("NDS IronMON Tracker", "By Brian0255: the DS trackers' rules, and the badges, icons, sprites and type symbols taken from it.",
            "GPL-3.0", "github.com/Brian0255/NDS-Ironmon-Tracker"),
        Part("Gambatte libretro core", "The Game Boy emulator, unchanged.", "GPL-2.0", "github.com/libretro/gambatte-libretro"),
        Part("mGBA libretro core", "The GBA emulator, by Jeffrey Pfau and contributors, unchanged.", "MPL-2.0", "github.com/mgba-emu/mgba"),
        Part("inih", "By Ben Hoyt, inside the mGBA core.", "BSD-3-Clause-inih"),
        Part("MurmurHash3 and the LZMA SDK", "Inside the mGBA core.", "PublicDomain-mGBA"),
        Part("AndroidX, Jetpack Compose and CameraX", "By Google, CameraX's image library included.", "Apache-2.0"),
        Part("Kotlin and kotlinx.coroutines", "By JetBrains.", "Apache-2.0"),
        Part("Oboe", "By Google, inside LibretroDroid.", "Apache-2.0"),
        Part("rcheevos", "By RetroAchievements.org.", "MIT-rcheevos"),
        Part("libretro-common", "By the RetroArch team, inside LibretroDroid.", "MIT-libretro-common"),
        Part("IronMON Tracker", "By besteon and contributors: the Gen 3 tracker's rules, and its badges and sprites.", "MIT-Ironmon-Tracker"),
        Part("Gen 1 IronMON Tracker", "By Sannji (mollo010).", "MIT-Ironmon-gen-tracker"),
        Part("Gen 2 IronMON Tracker", "By seadogstingray, and its badges.", "MIT-Ironmon-gen-2-tracker"),
        Part("Calc Atk", "By UTDZac.", "MIT-CalcAtk"),
        Part("Sprite Is Me", "By UTDZac.", "MIT-SpriteIsMe"),
        Part("Death Quotes", "By UTDZac.", "MIT-DeathQuotes"),
        Part("Auto Pokémon Themes", "By Fellshadow.", "MIT-AutoPokemonThemes"),
        Part("Favorites As Sources", "By UTDZac.", "MIT-FavoritesAsSources"),
        Part("Pokémon Showdown", "By Guangcong Luo and contributors: most move descriptions of the Nat. Dex and MaxDex " +
            "moves from X and Y on.", "MIT-PokemonShowdown"),
        Part("Press Start 2P", "The pixel font, by CodeMan38.", "OFL-1.1"),
        Part("PMD Sprite Collab", "The Walking Pals sprites, by the collab's artists, each under the terms its artist gave.",
            "CC-BY-NC-4.0", "sprites.pmdcollab.org"),
    )

    const val INTRO = "What KaizoCore carries, and the license each is under. Tap a license to read it whole."
    const val SOURCE = "Source: "

    /** [raw] as the page shows it: the markdown marks of a licence written in markdown dropped, the words kept. */
    fun shown(raw: String): String = raw.replace("\r\n", "\n").lines().joinToString("\n") { line ->
        line.replace("**", "").let { if (it.startsWith("#")) it.trimStart('#').trimStart() else it }
    }

    /** The text of [t] from the app's assets. Reads a file: off the main thread. */
    fun read(context: android.content.Context, t: Text): String? =
        runCatching { context.assets.open(t.asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()?.let(::shown)
}

/** About's Licences page: each licence, what is under it, and its full text a tap away. */
@Composable
fun LicencesDialog(onClose: () -> Unit) {
    var open by remember { mutableStateOf<Licences.Text?>(null) }
    open?.let { t -> LicenceTextDialog(t) { open = null }; return }
    ShellDialog("Licenses", onDismiss = onClose) {
        Text(Licences.INTRO, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        for (t in Licences.texts) {
            val parts = Licences.parts.filter { it.licence == t.id }
            if (parts.isEmpty()) continue
            Spacer(Modifier.height(14.dp))
            Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper,
                modifier = Modifier.semantics { heading() })
            for (p in parts) {
                Spacer(Modifier.height(4.dp))
                Text(p.name + ". " + p.what + (p.source?.let { " " + Licences.SOURCE + it } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            }
            Text(
                "Read the " + t.name, style = MaterialTheme.typography.bodySmall, color = Shell.linkOnPaper,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.heightIn(min = Shell.touchTarget).clickable(role = Role.Button) { open = t }
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

/** One licence's whole text, read off the main thread, in a list so a long one scrolls smoothly. */
@Composable
private fun LicenceTextDialog(t: Licences.Text, onBack: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null, t) { value = withContext(Dispatchers.IO) { Licences.read(context, t) } ?: "" }
    androidx.compose.ui.window.Dialog(onDismissRequest = onBack) {
        com.ironmonone.app.gen3.Gen3Box(Modifier.fillMaxWidth().fillMaxHeight(0.9f), paper = Shell.paper) {
            Column(Modifier.padding(4.dp)) {
                DialogTitle(t.name, onBack)
                Spacer(Modifier.height(8.dp))
                val paragraphs = remember(text) { text?.split("\n\n")?.filter { it.isNotBlank() } ?: emptyList() }
                if (text == null) Text("Reading the license.", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
                LazyColumn(Modifier.weight(1f)) {
                    items(paragraphs) { para ->
                        Text(para.trim('\n'), style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }
    }
}
