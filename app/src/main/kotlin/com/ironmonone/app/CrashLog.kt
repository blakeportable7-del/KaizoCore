package com.ironmonone.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why the app died last time, collected from Android itself.
 *
 * A native crash - a SIGSEGV inside the emulator core - kills the process
 * outright. There is no Java exception, so no `Thread.setDefaultUncaught-
 * ExceptionHandler` ever runs, and nothing the app writes on its way down can
 * be trusted to land. The only record is the system tombstone, which normally
 * needs `adb logcat -b crash`.
 *
 * [ActivityManager.getHistoricalProcessExitReasons] gives the same information
 * to the app itself, with no adb, no root and no permission: reason, signal,
 * timestamp, and on API 31+ the tombstone trace for native crashes. That turns
 * "it crashed again" into something diagnosable by a user who cannot plug the
 * phone into a computer.
 *
 * Requires API 30. Below that this reports nothing rather than guessing.
 */
object CrashLog {

    private const val MARKER = "crash-seen.txt"
    const val REPORT = "crash-report.txt"

    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (unhandled Java exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (signal in native code)"
        ApplicationExitInfo.REASON_ANR -> "ANR (not responding)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        else -> "other($r)"
    }

    /** Exits worth telling the user about. A normal EXIT_SELF is not one. */
    private fun isFailure(r: Int): Boolean = r == ApplicationExitInfo.REASON_CRASH ||
        r == ApplicationExitInfo.REASON_CRASH_NATIVE ||
        r == ApplicationExitInfo.REASON_ANR ||
        r == ApplicationExitInfo.REASON_SIGNALED ||
        r == ApplicationExitInfo.REASON_LOW_MEMORY

    fun reportFile(context: Context): File = File(context.filesDir, REPORT)

    /**
     * Refresh the stored report from the system's exit history.
     *
     * Only exits NEWER than the last one already reported are written, so the
     * same crash is not re-announced on every launch. Returns the report text
     * if there is an unseen failure, else null.
     */
    fun collect(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@runCatching null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@runCatching null
        val history = am.getHistoricalProcessExitReasons(context.packageName, 0, 15)
        if (history.isEmpty()) return@runCatching null

        val marker = File(context.filesDir, MARKER)
        val seenUpTo = runCatching { marker.readText().trim().toLong() }.getOrNull() ?: 0L

        val fresh = history.filter { isFailure(it.reason) && it.timestamp > seenUpTo }
        if (fresh.isEmpty()) return@runCatching null

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("KaizoCore - crash report")
        sb.appendLine("app ${BuildConfigish.version(context)}")
        sb.appendLine("device ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        for (info in fresh.sortedByDescending { it.timestamp }) {
            sb.appendLine("---- ${stamp.format(Date(info.timestamp))} ----")
            sb.appendLine("reason      ${reasonName(info.reason)}")
            sb.appendLine("status      ${info.status}")
            sb.appendLine("importance  ${info.importance}")
            sb.appendLine("process     ${info.processName}")
            info.description?.let { sb.appendLine("description $it") }
            sb.appendLine("rss         ${info.rss} kB")

            // On API 31+ a native crash carries its tombstone here. The format
            // is protobuf, not text, so it is saved verbatim beside the report
            // rather than mangled into the summary - the raw bytes are what
            // can actually be decoded later.
            val trace = runCatching { info.traceInputStream }.getOrNull()
            if (trace != null) {
                val out = File(context.filesDir, "crash-trace-${info.timestamp}.bin")
                runCatching {
                    trace.use { input -> out.outputStream().use { input.copyTo(it) } }
                    sb.appendLine("tombstone   ${out.name} (${out.length()} bytes)")
                    // Any printable runs in the tombstone are usually enough to
                    // name the signal and the top frames.
                    sb.appendLine("            " + printableRuns(out).take(12)
                        .joinToString("\n            "))
                }
            }
            sb.appendLine()
        }

        val text = sb.toString()
        reportFile(context).writeText(text)
        marker.writeText(fresh.maxOf { it.timestamp }.toString())
        text
    }.getOrNull()

    /**
     * Readable strings inside a binary tombstone.
     *
     * The protobuf holds the signal name and the frame descriptors as plain
     * UTF-8, so pulling runs of printable bytes recovers the useful lines
     * without a protobuf parser on the phone.
     */
    private fun printableRuns(f: File, min: Int = 8): List<String> = runCatching {
        val bytes = f.readBytes()
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) cur.append(c.toChar())
            else {
                if (cur.length >= min) out.add(cur.toString())
                cur.setLength(0)
            }
        }
        if (cur.length >= min) out.add(cur.toString())
        // The interesting lines name the signal or a library frame.
        out.filter {
            it.contains("SIG") || it.contains(".so") || it.contains("libretro") ||
                it.contains("Cause") || it.contains("abort") || it.contains("oboe")
        }.distinct()
    }.getOrNull() ?: emptyList()

    fun clear(context: Context) {
        runCatching { reportFile(context).delete() }
        runCatching {
            context.filesDir.listFiles { f -> f.name.startsWith("crash-trace-") }
                ?.forEach { it.delete() }
        }
    }

    fun existing(context: Context): String? =
        reportFile(context).takeIf { it.exists() && it.length() > 0 }?.readText()
}

/** Version string without depending on a generated BuildConfig. */
private object BuildConfigish {
    fun version(context: Context): String = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        "${p.versionName} (${p.longVersionCode})"
    }.getOrNull() ?: "unknown"
}
