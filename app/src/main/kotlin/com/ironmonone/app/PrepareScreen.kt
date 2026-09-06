package com.ironmonone.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box
import com.ironmonone.app.gen3.Gen3Button
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Blake's flow, verbatim: "i upload the vanilla rom, then select nationaldex or
 * standard dex, and it patches for me."
 *
 * The Nat. Dex .bps is imported ONCE and remembered; after that the choice is just a
 * radio button. An already-patched Nat. Dex ROM is accepted too and skips the patch.
 */
@Composable
fun PrepareScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { PrepStore(context) }
    val scope = rememberCoroutineScope()

    var romName by remember { mutableStateOf<String?>(null) }
    var romBytes by remember { mutableStateOf<ByteArray?>(null) }
    var romId by remember { mutableStateOf<RomIdentity.Result?>(null) }
    var wantNatDex by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var needPatchImport by remember { mutableStateOf(false) }

    fun say(text: String, error: Boolean = false) { message = text; messageIsError = error }

    val pickRom = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true; message = null; needPatchImport = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val b = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Triple(context.displayNameOf(uri), b, RomIdentity.identify(b))
                }
            }.onSuccess { (n, b, id) ->
                romName = n; romBytes = b; romId = id
                if (!id.recognised) say(id.summary, error = true)
            }.onFailure { say("Could not read that file: ${it.message}", error = true) }
            busy = false
        }
    }

    val pickPatch = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val b = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    store.importPatch(b).getOrThrow()
                }
            }.onSuccess {
                needPatchImport = false
                say("Patch saved. It will be found automatically from now on.")
            }.onFailure { say(it.message ?: "Could not import that patch.", error = true) }
            busy = false
        }
    }

    fun prepare() {
        val bytes = romBytes ?: return
        val id = romId ?: return
        val kind = id.kind ?: return
        busy = true; message = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when {
                        kind.isNatDex ->
                            "Already Nat. Dex. Stored as ready to randomize." to
                                store.savePrepared(kind, bytes)

                        // A ROM that cannot take Nat. Dex (e.g. FireRed v1.0) is always
                        // a standard base - the hidden radio's default must never route
                        // it into the patch path.
                        !wantNatDex || !kind.natDexCapable ->
                            "Stored as a standard (vanilla) base." to
                                store.savePrepared(kind, bytes)

                        else -> {
                            // Bundled patch is used unless the user imported one.
                            val patchFile = store.patchFileOrBundled(context, kind)
                                ?: throw NeedPatch()
                            val out = Patcher.apply(patchFile.readBytes(), bytes, kind.displayName)
                            val outKind = RomKind.allNatDex.firstOrNull {
                                it.expectedCrc == com.ironmonone.patch.Crc32.of(out)
                            } ?: error(
                                "The patch applied, but the result is not a " +
                                "build this app knows (CRC mismatch). A newer " +
                                "Nat. Dex release needs an app update that " +
                                "records its CRC - importing just the .bps is " +
                                "not enough, because the tracker's addresses " +
                                "are resolved per known build.")
                            "Patched to ${outKind.displayName}. Ready to randomize." to
                                store.savePrepared(outKind, out)
                        }
                    }
                }
            }.onSuccess { (msg, _) -> say("$msg Go to the Run tab.") }
                .onFailure {
                    if (it is NeedPatch) {
                        needPatchImport = true
                        say(
                            "One-time setup: pick the pokeemerald/pokefirered " +
                                "natdex .bps file. The app will keep it.", error = true
                        )
                    } else say(it.message ?: "Failed.", error = true)
                }
            busy = false
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
        Text(
            "Add a vanilla ROM, choose Nat. Dex or Standard, and it is patched and " +
                "stored, ready to randomize.",
            style = MaterialTheme.typography.bodyMedium,
            color = Gen3.Ink,
        )
        Spacer(Modifier.height(14.dp))

        Gen3Button(romName ?: "CHOOSE ROM", enabled = !busy) {
            pickRom.launch(arrayOf("*/*"))
        }

        romId?.let { id ->
            Spacer(Modifier.height(8.dp))
            Text(
                id.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (id.recognised) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }

        if (romId?.kind?.natDexCapable == true) {
            Spacer(Modifier.height(14.dp))
            // The whole row is the touch target, not just the radio circle. A label
            // that ignores taps is the bug the emulator test caught on 2026-08-30.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clickable { wantNatDex = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellRadio(wantNatDex)
                    Spacer(Modifier.width(10.dp))
                    Text("Nat. Dex", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(16.dp))
                Row(
                    Modifier.clickable { wantNatDex = false },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShellRadio(!wantNatDex)
                    Spacer(Modifier.width(10.dp))
                    Text("Standard")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (busy) {
            // Patching a 16MB ROM is not instant either; same panel as the
            // randomizer so the app has one way of saying "working".
            ProgressPanel(RunPhase.PATCHING)
            Spacer(Modifier.height(14.dp))
        }

        Gen3Button("PREPARE", enabled = !busy && romId?.recognised == true, accent = true) {
            prepare()
        }

        if (needPatchImport) {
            Spacer(Modifier.height(10.dp))
            Gen3Button("IMPORT NAT.DEX PATCH", enabled = !busy) {
                pickPatch.launch(arrayOf("*/*"))
            }
        }
            }
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Gen3Box(Modifier.fillMaxWidth()) {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium,
                    color = if (messageIsError) Gen3.HpRed else Gen3.Ink,
                )
            }
        }

        // What is already prepared. A ROM is patched and stored ONCE, but the
        // screen showed no sign of that, so it read as though every session had
        // to go and find the file again. These are the ones the Run tab will
        // offer; nothing here needs re-picking.
        val prepared = remember(message, busy) { store.listPrepared() }
        Spacer(Modifier.height(14.dp))
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    "Already prepared",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                if (prepared.isEmpty()) {
                    Text(
                        "Nothing yet. Add a ROM above and it is kept for good.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gen3.InkShadow,
                    )
                } else {
                    prepared.forEach { (kind, file) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                kind.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "%.0f MB".format(file.length() / 1048576.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = Gen3.InkShadow,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick one on the Run tab to randomize it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gen3.InkShadow,
                    )
                }
            }
        }
    }
}

private class NeedPatch : Exception()
