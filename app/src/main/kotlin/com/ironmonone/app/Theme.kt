package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File

/**
 * ColorSchemeScreen.lua: the eight colours of the palette by the DS
 * tracker's names, edited as AARRGGBB hex and applied live, saved to
 * prep/theme.txt, with the reference's import and export of a theme string
 * (eight hex values, comma separated) and a reset to the default scheme.
 */
object ThemeStore {
    class Key(val name: String, val get: () -> Color, val set: (Color) -> Unit, val default: Color)

    val KEYS: List<Key> = listOf(
        Key("Main background color", { Pc.Page }, { Pc.Page = it }, Color(0xFF000000)),
        Key("Top box background color", { Pc.Ground }, { Pc.Ground = it }, Color(0xFF222222)),
        Key("Top box border color", { Pc.Border }, { Pc.Border = it }, Color(0xFFAAAAAA)),
        Key("Top box text color", { Pc.Text }, { Pc.Text = it }, Color(0xFFFFFFFF)),
        Key("Positive text color", { Pc.Positive }, { Pc.Positive = it }, Color(0xFF00FF00)),
        Key("Negative text color", { Pc.Negative }, { Pc.Negative = it }, Color(0xFFFF0000)),
        Key("Intermediate text color", { Pc.Gold }, { Pc.Gold = it }, Color(0xFFFFFF00)),
        Key("Bottom box text color", { Pc.Dim }, { Pc.Dim = it }, Color(0xFFAAAAAA)),
    )
    private var file: File? = null

    fun hex(c: Color): String = String.format("%08X", (c.value shr 32).toLong() and 0xFFFFFFFFL)
    fun parse(s: String): Color? = s.trim().removePrefix("#").takeIf { it.length == 8 || it.length == 6 }?.let { h ->
        h.toLongOrNull(16)?.let { v -> Color(if (h.length == 6) (0xFF000000L or v) else v) }
    }

    fun export(): String = KEYS.joinToString(",") { hex(it.get()) }
    fun import(s: String): Boolean {
        val parts = s.split(',').map { parse(it) ?: return false }
        if (parts.size != KEYS.size) return false
        KEYS.forEachIndexed { i, k -> k.set(parts[i]) }
        save(); return true
    }
    fun reset() { KEYS.forEach { it.set(it.default) }; save() }

    fun load(f: File) {
        file = f
        if (!f.isFile) return
        runCatching { import(f.readText().trim()) }
    }
    fun save() { file?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(export()) } } }
}

@Composable
fun ColorThemeDialog(onClose: () -> Unit) {
    var importText by remember { mutableStateOf("") }
    var showExport by remember { mutableStateOf(false) }
    var edits by remember { mutableStateOf(ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) }) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.width(300.dp).background(Pc.Page).border(1.dp, Pc.Border).padding(8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("EDIT COLOR THEME", 10, Pc.Text, Modifier.weight(1f))
                PixText("X", 9, Pc.Dim, Modifier.clickable { onClose() }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(6.dp))
            ThemeStore.KEYS.forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(k.get()).border(1.dp, Pc.Border))
                    PixText(k.name, 7, Pc.Text, Modifier.weight(1f).padding(start = 6.dp))
                    Box(Modifier.width(84.dp).border(1.dp, Pc.Border).padding(3.dp)) {
                        BasicTextField(edits[k.name] ?: "", { v ->
                            edits = edits + (k.name to v)
                            ThemeStore.parse(v)?.let { c -> k.set(c); ThemeStore.save() }
                        }, textStyle = TextStyle(color = Pc.Text, fontSize = 11.sp), singleLine = true)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row { com.ironmonone.app.gen3.Gen3Button("RESET") { ThemeStore.reset(); edits = ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) } }; Spacer(Modifier.width(6.dp)); com.ironmonone.app.gen3.Gen3Button("EXPORT THEME") { showExport = !showExport } }
            if (showExport) { Spacer(Modifier.height(4.dp)); PixText(ThemeStore.export(), 7, Pc.Gold, wrap = true) }
            Spacer(Modifier.height(8.dp))
            PixText("Import theme", 8, Pc.Text)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).border(1.dp, Pc.Border).padding(3.dp)) {
                    BasicTextField(importText, { importText = it }, textStyle = TextStyle(color = Pc.Text, fontSize = 11.sp), singleLine = true)
                }
                Spacer(Modifier.width(6.dp))
                com.ironmonone.app.gen3.Gen3Button("APPLY") { if (ThemeStore.import(importText)) { edits = ThemeStore.KEYS.associate { it.name to ThemeStore.hex(it.get()) }; importText = "" } else importText = "not a theme string" }
            }
        }
    }
}
