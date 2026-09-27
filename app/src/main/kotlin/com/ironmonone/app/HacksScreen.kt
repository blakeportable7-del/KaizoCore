package com.ironmonone.app

import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.clip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.app.gen3.Gen3Header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The HACK tab: make a ROM hack in three steps (Blake, 2026-09-27: "a rom hack
 * page where you can upload your roms and patch and patch in the app, like the
 * main menu section").
 *
 * 1. Pick the game you own. 2. Pick the patch for it. 3. PATCH IT. The result
 * is saved as its own game beside the original, which is never touched, and
 * lands in YOUR ROM HACKS with a PLAY button.
 *
 * Everything underneath is the ROMS tab's library: the same files, the same
 * identity matching (a patch is offered only for the exact ROM it was made
 * for), the same import code (LibraryImport). This page only puts the steps in
 * order. Nothing is downloaded; the player brings the patch file.
 */
@Composable
fun HacksScreen(modifier: Modifier = Modifier, onPlay: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PrepStore(context) }
    val progress = remember { FileProgress() }
    val importer = remember { LibraryImport(context, store, progress) }
    val roms = remember { mutableStateListOf<LibraryStore.Entry>() }
    val patches = remember { mutableStateListOf<LibraryStore.PatchEntry>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var gameName by remember { mutableStateOf<String?>(null) }
    var patchName by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<LibraryStore.Entry?>(null) }

    fun reload() {
        scope.launch {
            val (r, p) = withContext(Dispatchers.IO) { store.library.list() to store.library.listPatches() }
            roms.clear(); roms.addAll(r); patches.clear(); patches.addAll(p)
            if (roms.none { it.name == gameName }) gameName = null
            if (patches.none { it.name == patchName }) patchName = null
        }
    }
    LaunchedEffect(Unit) { reload() }

    // Games a patch can go on: anything that plays. Clean dumps first, since
    // almost every hack is made for one.
    val games = roms.filter { it.platform != null }
        .sortedBy { if (it.category == LibraryStore.Category.CLEAN) 0 else 1 }
    val game = games.firstOrNull { it.name == gameName }
    val patch = patches.firstOrNull { it.name == patchName }
    val fits = game != null && patch != null && patch.matches(game)
    val hacks = roms.filter { it.patchName != null || it.category == LibraryStore.Category.HACK || it.category == LibraryStore.Category.PATCHED }

    fun importPicked(uris: List<android.net.Uri>, ipsFor: LibraryStore.Entry?) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            status = withContext(Dispatchers.IO) { importer.importUris(uris, ipsFor) }
            reload(); progress.clear(); busy = false
        }
    }
    val addGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importPicked(it, null) }
    // An .ips names no game, so one added here is saved for the game picked in step 1.
    val addPatch = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importPicked(it, game) }

    fun patchIt(base: LibraryStore.Entry, p: LibraryStore.PatchEntry) {
        busy = true
        status = null
        scope.launch {
            progress.start("Patching ${base.name.substringBeforeLast('.')}", base.sizeBytes)
            val r = withContext(Dispatchers.IO) { runCatching { store.library.apply(base, p) { d, t -> progress.at(d); if (t > 0) progress.total = t } } }
            r.onSuccess { e ->
                made = e
                status = "Made ${e.name.substringBeforeLast('.')}. Your original ${base.name.substringBeforeLast('.')} is unchanged." +
                    if (e.verified) " It is also ready on the Run tab to randomize." else ""
            }.onFailure {
                made = null
                status = patchFailure(it)
            }
            reload(); progress.clear(); busy = false
        }
    }

    fun play(e: LibraryStore.Entry) { store.library.selectLibrary(e); onPlay() }

    LazyColumn(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "intro") {
            Gen3Box(Modifier.fillMaxWidth()) {
                Column {
                    Text("ROM hacks", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 16.sp, color = Gen3.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Turn a game you own into a ROM hack. Pick the game, pick the patch, tap Patch it. " +
                            "The hack is saved as its own game and your original stays as it was.",
                        style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nothing is downloaded. Bring the patch file from the hack's own page: " +
                            ".ips, .bps, .ups or .xdelta, or a zip holding one.",
                        style = MaterialTheme.typography.bodySmall, color = Shell.inkOnPaper,
                    )
                }
            }
        }
        if (busy) item(key = "busy") { if (progress.phase.isNotEmpty()) FileProgressPanel(progress) else ShellBusy() }
        status?.let { s ->
            item(key = "status") {
                Gen3Box(Modifier.fillMaxWidth()) {
                    Column {
                        Text(s, style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
                        made?.let { e ->
                            Spacer(Modifier.height(8.dp))
                            Gen3Button("PLAY IT NOW", accent = true, enabled = e.platform != null) { play(e) }
                        }
                    }
                }
            }
        }

        // ---- 1. the game ----
        item(key = "step1") {
            StepHeader("1", "Pick your game", "ADD A GAME", enabled = !busy) { addGame.launch(arrayOf("*/*")) }
        }
        if (games.isEmpty()) item(key = "nogames") {
            Hint("No games yet. Tap Add a game and pick your own dump (.gba, .gbc, .gb or .nds, or a zip).")
        }
        items(games, key = { "g-" + it.name }) { e ->
            ChoiceRow(
                title = e.name.substringBeforeLast('.'),
                detail = e.subtitle,
                selected = e.name == gameName,
                enabled = !busy,
            ) {
                gameName = e.name
                // Keep the patch only if it still fits the new game.
                if (patch != null && !patch.matches(e)) patchName = null
            }
        }

        // ---- where to find one: the hacks made for the picked game ----
        item(key = "find") { FindHacks(game?.kind, startOpen = patches.isEmpty()) }

        // ---- 2. the patch ----
        item(key = "step2") {
            StepHeader("2", "Pick the patch", "ADD A PATCH", enabled = !busy) { addPatch.launch(arrayOf("*/*")) }
        }
        if (game == null) item(key = "pickfirst") {
            Hint("Pick your game in step 1 first. Then the patches made for it light up here.")
        }
        if (patches.isEmpty()) item(key = "nopatches") {
            Hint("No patches yet. Tap Add a patch." +
                (if (game != null) " A patch that does not say which game it is for (.ips, .xdelta) is saved for ${game.name.substringBeforeLast('.')}." else ""))
        }
        items(patches, key = { "p-" + it.name }) { p ->
            val forThis = game != null && p.matches(game)
            ChoiceRow(
                title = p.name.substringBeforeLast('.'),
                detail = when {
                    forThis -> "Made for this game · ${p.format.name}"
                    else -> "Made for " + (p.forName?.substringBeforeLast('.')
                        ?: p.forCrc?.let { "a game you have not added (CRC %08x)".format(it) } ?: "an unknown game")
                },
                selected = p.name == patchName && forThis,
                enabled = !busy && forThis,
            ) { patchName = p.name }
        }

        // ---- 3. patch it ----
        item(key = "step3") {
            Column {
                StepTitle("3", "Patch it")
                Spacer(Modifier.height(6.dp))
                Gen3Button(if (busy) "WORKING..." else "PATCH IT", accent = fits, enabled = fits && !busy) {
                    if (game != null && patch != null) patchIt(game, patch)
                }
                if (!fits) Hint(
                    when {
                        game == null -> "Waiting for step 1."
                        patch == null -> "Waiting for step 2."
                        else -> "That patch was made for a different game."
                    },
                )
            }
        }

        // ---- the results ----
        item(key = "hacksHeader") {
            Column(Modifier.padding(top = 10.dp)) {
                Gen3Header("Your ROM hacks")
                Text(
                    "Every game you have patched, plus any finished hack you added. " +
                        "A finished hack file (already patched) goes in with Add a game and shows up here.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight,
                )
            }
        }
        if (hacks.isEmpty()) item(key = "nohacks") { Hint("None yet.") }
        items(hacks, key = { "h-" + it.name }) { e -> HackCard(e, onPlay = { play(e) }, onDelete = {
            scope.launch(Dispatchers.IO) { store.library.delete(e) }
            roms.remove(e)
            if (made?.name == e.name) made = null
            status = "Deleted ${e.name.substringBeforeLast('.')}. The game it was made from is still here."
        }) }
    }

    // An .ips added before a game was picked: ask, once, which game it is for.
    importer.pendingName?.let { name ->
        ShellDialog("Which game is $name for?", onDismiss = { importer.cancelPending() }) {
            Text("This patch does not say which game it was made for. Pick it; the patch will only be offered for that game.",
                style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            Spacer(Modifier.height(6.dp))
            if (games.isEmpty()) Text("Add the game first with Add a game.", style = MaterialTheme.typography.bodyMedium, color = Gen3.Ink)
            games.forEach { e ->
                ShellListRow(e.name.substringBeforeLast('.'), e.kind?.displayName ?: e.platform?.name ?: "", onClick = {
                    status = importer.declarePending(e)
                    gameName = e.name
                    reload()
                })
            }
            Spacer(Modifier.height(8.dp))
            Gen3Button("NOT NOW") { importer.cancelPending() }
        }
    }
}

@Composable
private fun StepTitle(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Shell.accent), contentAlignment = Alignment.Center) {
            Text(number, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 13.sp, color = Shell.onAccent)
        }
        Spacer(Modifier.width(8.dp))
        Gen3Header(title)
    }
}

@Composable
private fun StepHeader(number: String, title: String, action: String, enabled: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { StepTitle(number, title) }
        Gen3Button(action, enabled = enabled, onClick = onAction)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Shell.hintOnNight, modifier = Modifier.padding(vertical = 2.dp))
}

/** One choice in a step: a card with a round marker; the picked one carries the accent outline. At least 48dp tall. */
@Composable
private fun ChoiceRow(title: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ink = if (enabled) Shell.inkOnPaper else Shell.inkOnPaper.copy(alpha = 0.45f)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Shell.cardRadius)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape).background(Shell.paper)
            .border(if (selected) 2.dp else 1.dp, if (selected) Shell.accent else Shell.hairline, shape)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellRadio(selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (enabled) Shell.hintOnPaper else ink)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HackCard(e: LibraryStore.Entry, onPlay: () -> Unit, onDelete: () -> Unit) {
    var armed by remember(e.name) { mutableStateOf(false) }
    val tracked = GameSession.trackerKind(e.kind, e.crc) != null
    Gen3Box(Modifier.fillMaxWidth()) {
        Column {
            Text(e.name.substringBeforeLast('.'), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Gen3.Ink)
            Text(
                e.subtitle + if (tracked) " · the tracker works on it" else " · plays without the tracker",
                style = MaterialTheme.typography.bodySmall,
                color = if (tracked) Shell.goodOnPaper else Shell.inkOnPaper,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Gen3Button("PLAY", accent = true, enabled = e.platform != null, onClick = onPlay)
                Gen3Button(if (armed) "SURE?" else "DELETE", accent = armed, onClick = { if (armed) onDelete() else armed = true })
            }
        }
    }
}

/**
 * Where to find a hack: the picked game's hacks first, chips to browse the
 * other games, and each hack opening its creator's own page (HackLinks).
 * Folded when the player already has patches, so steps 1 to 3 stay close.
 */
@Composable
private fun FindHacks(picked: com.ironmonone.core.RomKind?, startOpen: Boolean) {
    val context = LocalContext.current
    val pickedFamily = HackLinks.baseIdOf(picked)?.let(HackLinks::familyOf)
        ?.takeIf { f -> HackLinks.families.any { it.key == f } }
    var family by remember { mutableStateOf(pickedFamily ?: HackLinks.families.first().key) }
    LaunchedEffect(pickedFamily) { pickedFamily?.let { family = it } }
    var open by remember { mutableStateOf(startOpen) }
    val label = HackLinks.families.first { it.key == family }.label
    val links = HackLinks.forFamily(family)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Shell.cardRadius)

    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(shape).background(Shell.paper)
        .border(1.dp, Shell.hairline, shape)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Find a hack for $label", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
                Text("${links.size} hack${if (links.size == 1) "" else "s"}. Each opens its creator's page.",
                    style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            }
            androidx.compose.material3.Icon(
                if (open) androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp
                else androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Close" else "Open", tint = Shell.hintOnPaper,
            )
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            ShellSegmented(
                values = HackLinks.families.map { it.key },
                selected = family,
                label = { k -> HackLinks.families.first { it.key == k }.label },
                onSelect = { family = it },
            )
            Spacer(Modifier.height(10.dp))
            links.forEach { l ->
                HackLinkCard(l, HackLinks.mismatch(l, picked)) {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(l.url))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "No browser found to open the page.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Download the patch file on the hack's page, then add it in step 2. These pages belong to each " +
                    "hack's creator, not to KaizoCore. Bring your own game: KaizoCore never provides one.",
                style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper,
            )
        }
    }
}

/** One hack: name, creator, what it is, the game it needs, and a warning when it will not fit or its page carries game files. */
@Composable
private fun HackLinkCard(l: HackLink, mismatch: String?, onOpen: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Shell.controlRadius)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Shell.raised).clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(l.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Shell.inkOnPaper)
            Text("by ${l.author}", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            Spacer(Modifier.height(2.dp))
            Text(l.blurb, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
            Text("Needs ${l.needs}", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper)
            mismatch?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper) }
            if (l.fullRomPage) Text(
                "Heads up: this page also offers finished game files. Only play games you own. The patch file on the same page works here.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFF5C26B),
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight,
            contentDescription = "Open ${l.name}'s page", tint = Shell.hintOnPaper,
        )
    }
}
