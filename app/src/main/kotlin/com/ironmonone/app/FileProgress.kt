package com.ironmonone.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box

/**
 * Progress of one file job (ADD FILES, PREP): the phase it is in and how far
 * through it is, in bytes. Blake, 2026-09-07: "how does the user know when
 * it is ready?" A spinner over a 512 MB game read as a hang. The bar is
 * real: every phase counts the bytes it moves against a total it knows
 * (the pick's size, the zip's size, the file's length).
 */
class FileProgress {
    var phase by mutableStateOf("")
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)
    fun start(phase: String, total: Long) { this.phase = phase; this.done = 0L; this.total = total }
    fun at(done: Long) { this.done = done }
    fun clear() { phase = ""; done = 0L; total = 0L }
    val fraction: Float? get() = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

private fun mb(b: Long) = "%.0f MB".format(b / 1048576.0)

@Composable
fun FileProgressPanel(p: FileProgress, modifier: Modifier = Modifier) {
    Gen3Box(modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(p.phase.uppercase(), fontFamily = Gen3.PixelFont, fontSize = 10.sp, color = Shell.inkOnPaper)
                Text(
                    if (p.total > 0L) "${mb(p.done)} of ${mb(p.total)}" else if (p.done > 0L) mb(p.done) else "",
                    fontFamily = Gen3.PixelFont, fontSize = 10.sp, color = Shell.hintOnPaper,
                )
            }
            Spacer(Modifier.height(8.dp))
            val f = p.fraction
            if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Copies [input] to [out] in 1 MB chunks, reporting bytes done. */
fun copyWithProgress(input: java.io.InputStream, out: java.io.OutputStream, onDone: (Long) -> Unit) {
    val buf = ByteArray(1 shl 20); var done = 0L
    while (true) {
        val n = input.read(buf); if (n < 0) break
        out.write(buf, 0, n); done += n; onDone(done)
    }
    out.flush()
}
