package com.ironmonone.app

import android.content.Context
import android.os.Build

/**
 * Beta feedback, composed on the phone and handed to the share sheet.
 *
 * No account and no server: the tester picks Discord, email, GitHub or
 * anything else from the sheet. What goes in is what makes a report
 * actionable and nothing that could identify a ROM: device, Android
 * version, app version, the game FAMILY in play (never a file name), the
 * tester's own words, and the tail of the app's log.
 */
object Feedback {

    /** The one place the outward links live. A blank one hides its button. */
    object Links {
        /** Latest build, changelog and checksum. Set when the repository is public. */
        const val RELEASES = "https://github.com/blakeportable7-del/KaizoCore/releases"
        /** Ko-fi / GitHub Sponsors. Set when Blake has created the account. */
        const val SUPPORT = "https://buy.stripe.com/8x2cN7b1Q8AG2VLczE67S00"
        /** The bug form on the site. Set when the site is up. */
        const val BUG_FORM = "https://willowcreek.group/kaizocore#report"
        /** Blake's inbox for bug reports, 2026-09-07: the EMAIL A BUG button addresses this. */
        const val EMAIL = "blake@willowcreek.group"
    }

    /**
     * A mail to [Links.EMAIL] with the report in the body, for whatever mail
     * app the phone has. ACTION_SENDTO with a mailto: URI is the one intent
     * shape every mail client answers and no non-mail app claims.
     */
    fun emailIntent(subject: String, body: String): android.content.Intent =
        android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:" + Links.EMAIL)
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(Links.EMAIL))
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            putExtra(android.content.Intent.EXTRA_TEXT, body)
        }

    data class Device(val model: String, val android: String, val appVersion: String)

    fun device(context: Context): Device = Device(
        model = (Build.MANUFACTURER + " " + Build.MODEL).trim(),
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?",
    )

    /**
     * The report text. [family] is the game family of the last run ("FRLG",
     * "GSC"), or null when there is none. [log] is the log tail, already
     * captured. Deterministic, so a test can pin the shape.
     */
    fun compose(device: Device, family: String?, words: String, log: String, crash: String? = null, dump: String? = null): String = buildString {
        if (!dump.isNullOrBlank()) { appendLine("DS tracker raw read:"); appendLine(dump.trim()); appendLine() }
        appendLine("KaizoCore beta feedback")
        appendLine("App: ${device.appVersion}")
        appendLine("Device: ${device.model}, ${device.android}")
        appendLine("Game family: ${family ?: "none"}")
        appendLine()
        appendLine("What happened:")
        appendLine(words.trim().ifBlank { "(no description)" })
        appendLine()
        if (!crash.isNullOrBlank()) {
            appendLine("Last crash:")
            appendLine(crash.trim().lines().take(60).joinToString(System.lineSeparator()))
            appendLine()
        }
        appendLine("Log tail:")
        append(log.trim().ifBlank { "(none)" })
        appendLine()
    }

    /** The last [lines] of this process's logcat, or empty when it cannot be read. */
    fun logTail(lines: Int = 150): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", lines.toString(), "-v", "brief").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor()
        // Only this app's lines: the tag or the package name, never other apps.
        text.lineSequence().filter { "KaizoCore" in it || "ironmonone" in it || "libretrodroid" in it || "rcheevos" in it }
            .toList().takeLast(lines).joinToString("\n")
    }.getOrDefault("")
}
