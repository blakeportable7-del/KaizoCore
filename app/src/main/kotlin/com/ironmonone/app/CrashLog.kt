package com.ironmonone.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
 * A Java exception is the other half. Android's record of one says CRASH and
 * nothing more, so its stack was never captured anywhere. [installHandler] puts
 * a handler in the process that saves the stack in the instant before the
 * system kills it, and [collect] joins the two when the app next starts
 * (2026-09-29).
 *
 * Requires API 30. Below that this reports nothing rather than guessing.
 */
object CrashLog {

    private const val MARKER = "crash-seen.txt"
    const val REPORT = "crash-report.txt"

    /**
     * The first line of every report. The site's crash endpoint checks for it
     * (2026-09-29), so it is not reworded and every report starts with it.
     */
    const val HEADER = "KaizoCore - crash report"

    /** The stack of the last uncaught Java exception, kept until the system's exit record joins it. */
    const val JAVA_FILE = "crash-java.txt"

    /** The system logs a crash moments after the handler ran; a stack further behind an exit than this is not its own. */
    const val JAVA_WINDOW_MS = 60_000L

    /** Lines of a saved stack printed under its exit. The rest of it stays out of the report. */
    private const val JAVA_LINES = 40

    /** Characters of a stack the handler writes: a StackOverflowError is a thousand copies of one frame. */
    const val JAVA_MAX_CHARS = 32 * 1024

    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (unhandled Java exception)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (signal in native code)"
        ApplicationExitInfo.REASON_ANR -> "ANR (not responding)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        else -> "other($r)"
    }

    /**
     * Exits worth telling the user about: a crash, a native crash or a freeze.
     * LOW_MEMORY and SIGNALED are Android ending a background app, which is
     * normal; they left a red "ended unexpectedly" card on INFO that never
     * went away (audit, 2026-09-27). Same filter as the launch dialog.
     */
    private fun isFailure(r: Int): Boolean = r == ApplicationExitInfo.REASON_CRASH ||
        r == ApplicationExitInfo.REASON_CRASH_NATIVE ||
        r == ApplicationExitInfo.REASON_ANR

    /** A report older than this is not shown or sent. */
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** True if [text] reports a crash or freeze (not a report of normal exits written before 2026-09-27). */
    fun isCrashReport(text: String): Boolean = "CRASH" in text || "ANR" in text

    fun reportFile(context: Context): File = File(context.filesDir, REPORT)

    /**
     * Refresh the stored report from the system's exit history.
     *
     * Only exits NEWER than the last one already reported are written, so the
     * same crash is not re-announced on every launch. Returns the report text
     * if there is an unseen failure, else null.
     */
    fun collect(context: Context): String? = runCatching {
        // A stack no exit record claimed in a week never will be, and API 26 to 29 have no exit records at all.
        pruneJava(context.filesDir)
        // Raw traces go after a week, card or no card (rc32 audit P3 #24).
        pruneTraces(context.filesDir)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@runCatching null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@runCatching null
        val history = am.getHistoricalProcessExitReasons(context.packageName, 0, 15)
        if (history.isEmpty()) return@runCatching null

        val marker = File(context.filesDir, MARKER)
        val seenUpTo = runCatching { marker.readText().trim().toLong() }.getOrNull() ?: 0L

        val weekAgo = System.currentTimeMillis() - MAX_AGE_MS
        val fresh = history.filter { isFailure(it.reason) && it.timestamp > seenUpTo && it.timestamp > weekAgo }
        if (fresh.isEmpty()) return@runCatching null

        val exits = fresh.map { info ->
            // On API 31+ a native crash carries its tombstone here. The format
            // is protobuf, not text, so it is saved verbatim beside the report
            // rather than mangled into the summary - the raw bytes are what
            // can actually be decoded later.
            val trace = runCatching { info.traceInputStream }.getOrNull()
            val tombstone = if (trace == null) null else {
                val out = File(context.filesDir, "crash-trace-${info.timestamp}.bin")
                runCatching {
                    trace.use { input -> out.outputStream().use { input.copyTo(it) } }
                    // A native crash's trace is the protobuf tombstone, read for its signal, cause and the
                    // crashing thread's frames (tombstoneLines). A freeze is different: its
                    // trace is text, every thread's stack, and the one that matters
                    // is the stuck main thread (2026-09-30: an AYN Thor's report kept
                    // only the dump thread's native frames and said nothing).
                    val lines = if (info.reason == ApplicationExitInfo.REASON_ANR) {
                        anrThreads(runCatching { out.readText() }.getOrDefault("")).ifEmpty { printableRuns(out).take(12) }
                    } else tombstoneLines(out)
                    Tombstone(out.name, out.length(), lines)
                }.getOrNull()
            }
            Exit(
                info.timestamp, info.reason, info.status, info.importance, info.processName,
                info.description, info.rss, tombstone,
            )
        }
        val saved = readJava(context.filesDir)
        val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val text = render(BuildConfigish.version(context), device, exits, saved)
        reportFile(context).writeText(text)
        marker.writeText(fresh.maxOf { it.timestamp }.toString())
        // The game Play had open when the app died, while its marker still names it (rc32 audit P3 #25).
        CrashReport.keepGame(context.filesDir, runCatching { CrashReport.gameAtExit(PrepStore(context)) }.getOrNull())
        // It is in the report now, so the file has done its job.
        if (saved != null && javaOwner(exits, saved) != null) File(context.filesDir, JAVA_FILE).delete()
        pruneTraces(context.filesDir)
        text
    }.getOrNull()

    /** One exit from the system's history, as much of it as the report prints. ApplicationExitInfo cannot be made in a test. */
    internal class Exit(
        val time: Long,
        val reason: Int,
        val status: Int,
        val importance: Int,
        val process: String?,
        val description: String?,
        val rss: Long,
        val tombstone: Tombstone? = null,
    )

    /** A tombstone saved beside the report: its file, its size and the readable lines pulled out of it. */
    internal class Tombstone(val file: String, val bytes: Long, val lines: List<String>)

    /**
     * The report text. The server reads it, so its shape is fixed: the first line, the "app"
     * and "device" lines, then one "---- yyyy-MM-dd HH:mm:ss ----" block per exit, newest
     * first, each with a "reason" line. A saved Java stack goes under the exit it belongs to.
     */
    internal fun render(app: String, device: String, exits: List<Exit>, saved: JavaCrash? = null): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val owner = saved?.let { javaOwner(exits, it) }
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        sb.appendLine("app $app")
        sb.appendLine("device $device")
        sb.appendLine()

        for (e in exits.sortedByDescending { it.time }) {
            sb.appendLine("---- ${stamp.format(Date(e.time))} ----")
            sb.appendLine("reason      ${reasonName(e.reason)}")
            sb.appendLine("status      ${e.status}")
            sb.appendLine("importance  ${e.importance}")
            sb.appendLine("process     ${e.process}")
            e.description?.let { sb.appendLine("description $it") }
            sb.appendLine("rss         ${e.rss} kB")
            e.tombstone?.let { t ->
                sb.appendLine("tombstone   ${t.file} (${t.bytes} bytes)")
                sb.appendLine("            " + t.lines.joinToString("\n            "))
            }
            if (saved != null && e === owner) {
                sb.appendLine("java        thread ${saved.thread}")
                for (line in saved.stack.trimEnd().lineSequence().take(JAVA_LINES)) sb.appendLine("            $line")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    /**
     * The exit a saved Java stack belongs to: the first CRASH the system logged from the
     * stack's own time to [JAVA_WINDOW_MS] after it. A native crash or a freeze never has a
     * Java stack, and an exit from before the stack was written is an older crash.
     */
    internal fun javaOwner(exits: List<Exit>, saved: JavaCrash): Exit? =
        exits.filter { it.reason == ApplicationExitInfo.REASON_CRASH && it.time - saved.time in 0..JAVA_WINDOW_MS }
            .minByOrNull { it.time }

    // ------------------------------------------------------- Java stacks

    /** An uncaught exception as the handler saved it: when, on which thread, and its stack. */
    data class JavaCrash(val time: Long, val thread: String, val stack: String)

    /** The file's text: the time in milliseconds, the thread's name, then the stack. */
    internal fun formatJava(j: JavaCrash): String =
        "${j.time}\n${j.thread.replace('\n', ' ').replace('\r', ' ')}\n${j.stack}"

    /** The saved stack in [text], or null when it is missing or is not one (a half-written file is never read: it is renamed into place whole). */
    internal fun parseJava(text: String?): JavaCrash? {
        if (text == null) return null
        val first = text.indexOf('\n')
        if (first <= 0) return null
        val second = text.indexOf('\n', first + 1)
        if (second < 0) return null
        val time = text.substring(0, first).trim().toLongOrNull()?.takeIf { it > 0 } ?: return null
        val stack = text.substring(second + 1)
        if (stack.isBlank()) return null
        return JavaCrash(time, text.substring(first + 1, second).trimEnd('\r'), stack)
    }

    private fun readJava(dir: File): JavaCrash? =
        parseJava(runCatching { File(dir, JAVA_FILE).takeIf { it.isFile }?.readText() }.getOrNull())

    /** The stack, cut to [JAVA_MAX_CHARS] without splitting a character in two. */
    internal fun capStack(stack: String): String {
        if (stack.length <= JAVA_MAX_CHARS) return stack
        val end = if (Character.isHighSurrogate(stack[JAVA_MAX_CHARS - 1])) JAVA_MAX_CHARS - 1 else JAVA_MAX_CHARS
        return stack.substring(0, end)
    }

    /** A saved stack that is a week old, or is not a stack, is deleted: nothing is going to claim it. */
    internal fun pruneJava(dir: File, now: Long = System.currentTimeMillis()) {
        val f = File(dir, JAVA_FILE)
        runCatching {
            if (!f.isFile) return
            val saved = parseJava(f.readText())
            if (saved == null || now - saved.time > MAX_AGE_MS) f.delete()
        }
    }

    /**
     * Saves the stack of an uncaught exception, then hands the exception on to the handler
     * that was there first, so Android still logs the crash, records its exit and kills the
     * process exactly as before. Only the saving is ours, and nothing in it may get in the
     * way: a failure there is swallowed and the exception is handed on regardless.
     * [previous] is null only in a bare JVM; then [kill] ends the process the standard way.
     */
    internal class JavaCrashHandler(
        private val dir: File,
        private val previous: Thread.UncaughtExceptionHandler?,
        private val now: () -> Long = { System.currentTimeMillis() },
        private val kill: () -> Unit = {
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        },
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try {
                // Synchronous, and renamed into place whole (SafeWrite): the process is about to die.
                SafeWrite.text(File(dir, JAVA_FILE), formatJava(JavaCrash(now(), thread.name, capStack(error.stackTraceToString()))))
            } catch (_: Throwable) {
                // An unreadable stack, no memory, no disk: none of it may stop the handoff below.
            }
            if (previous != null) previous.uncaughtException(thread, error) else kill()
        }
    }

    private val installed = AtomicBoolean(false)

    /** From MainActivity.onCreate, which can run more than once in a process: the first call installs, later ones do nothing. */
    fun installHandler(context: Context) {
        runCatching { install(context.filesDir) }
    }

    /** The install, with the process's handler slot and once-flag passed in so a test does not touch the real ones. */
    internal fun install(
        dir: File,
        once: AtomicBoolean = installed,
        get: () -> Thread.UncaughtExceptionHandler? = { Thread.getDefaultUncaughtExceptionHandler() },
        set: (Thread.UncaughtExceptionHandler?) -> Unit = { Thread.setDefaultUncaughtExceptionHandler(it) },
    ): Boolean {
        if (!once.compareAndSet(false, true)) return false
        val current = get()
        if (current is JavaCrashHandler) return false
        set(JavaCrashHandler(dir, current))
        return true
    }

    /**
     * From a freeze's trace (text, one block per thread, the name in quotes on its first line), the main
     * thread's stack and the emulation thread's: what was stuck and what it waited on. At most [ANR_LINES]
     * lines of each, the block's first line then only the frames and what they wait on or hold.
     */
    internal fun anrThreads(trace: String): List<String> {
        val out = ArrayList<String>()
        // A thread's block runs from its quoted name to the next blank line. The main thread follows the
        // "DALVIK THREADS (n):" line directly, with no blank line between, so blocks are found by their first
        // line, not by splitting on blank lines.
        val lines = trace.lines()
        fun block(first: Int) {
            out.add(lines[first].trim())
            var taken = 0
            var i = first + 1
            while (i < lines.size && lines[i].isNotBlank() && taken < ANR_LINES) {
                val l = lines[i].trim()
                if (l.startsWith("at ") || l.startsWith("- ") || l.startsWith("native:") || l.startsWith("| state=")) {
                    out.add("  $l")
                    taken++
                }
                i++
            }
        }
        lines.indexOfFirst { it.trimStart().startsWith("\"main\" ") }.takeIf { it >= 0 }?.let(::block)
        lines.indices.filter { lines[it].trimStart().startsWith("\"GLThread") }.take(2).forEach(::block)
        return out
    }

    private const val ANR_LINES = 30

    /**
     * What a native crash's trace says, in the text tombstone's lines: the signal, the cause and the crashing thread's
     * frames, read out of the protobuf (TombstoneProto, rc32 audit P2 #18). The printable runs that used to be all the
     * report kept were the memory map's library paths; they are what is left when the trace cannot be read.
     */
    internal fun tombstoneLines(f: File): List<String> =
        runCatching { TombstoneProto.lines(f.readBytes()) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: printableRuns(f).take(12)

    /**
     * Readable strings inside a binary tombstone: runs of printable bytes that name a signal or a library. What a
     * trace [TombstoneProto] cannot read still gives.
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

    fun clear(context: Context) = clearIn(context.filesDir)

    internal fun clearIn(dir: File) {
        runCatching { File(dir, REPORT).delete() }
        runCatching {
            dir.listFiles { f -> f.name.startsWith("crash-trace-") }
                ?.forEach { it.delete() }
        }
        runCatching { File(dir, JAVA_FILE).delete() }
        runCatching { File(dir, CrashReport.GAME_FILE).delete() }
    }

    /** Raw traces kept: the newest few, for a tester who is asked for one. */
    const val TRACES_KEPT = 3

    /**
     * Every crash and freeze copies its whole trace into filesDir, hundreds of KB to MBs, and only INFO's Dismiss
     * deleted them, so once the card had gone after a week nothing ever did (rc32 audit P3 #24). Nothing reads a trace
     * after [collect]: a week old goes, and past the newest [TRACES_KEPT] they go too. The time is the one in the name.
     */
    internal fun pruneTraces(dir: File, now: Long = System.currentTimeMillis()) {
        runCatching {
            val traces = (dir.listFiles { f -> f.isFile && f.name.startsWith("crash-trace-") } ?: emptyArray())
                .map { f -> f to (f.name.removePrefix("crash-trace-").substringBefore('.').toLongOrNull() ?: f.lastModified()) }
                .sortedByDescending { it.second }
            traces.forEachIndexed { i, (f, at) -> if (i >= TRACES_KEPT || now - at > MAX_AGE_MS) f.delete() }
        }
    }

    /** The stored report, if it is a real crash from the last 7 days. */
    fun existing(context: Context): String? =
        reportFile(context).takeIf {
            it.exists() && it.length() > 0 && System.currentTimeMillis() - it.lastModified() < MAX_AGE_MS
        }?.readText()?.takeIf { isCrashReport(it) }

    private val STAMP = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")

    /**
     * When the newest failure in [text] happened, as its "---- yyyy-MM-dd HH:mm:ss ----" line
     * writes it: the newest is the first, and this is also how a report is told apart from
     * one already sent. Null if there is no such line.
     */
    fun newestStamp(text: String): String? =
        text.lineSequence().firstOrNull { it.startsWith("---- ") }
            ?.trimEnd()?.removePrefix("---- ")?.removeSuffix(" ----")?.trim()
            ?.takeIf { STAMP.matches(it) }

    /**
     * When the newest failure in [text] happened, as a short local date, from
     * the report's "---- yyyy-MM-dd HH:mm:ss ----" lines. Null if none parses.
     */
    fun whenLabel(text: String): String? = runCatching {
        val stamp = newestStamp(text) ?: return@runCatching null
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(stamp)
            ?: return@runCatching null
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(date)
    }.getOrNull()
}

/** Version string without depending on a generated BuildConfig. */
internal object BuildConfigish {
    fun version(context: Context): String = runCatching {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        "${p.versionName} (${UpdateCheck.versionCode(p)})"
    }.getOrNull() ?: "unknown"
}
