package com.ironmonone.app.stream

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ironmonone.app.R

/**
 * While the stream is on, KaizoCore in the background pauses the game and freezes the picture in OBS (UX audit
 * P0-19). The guide says to keep it in front; this notification brings the player straight back with a tap. It is the
 * stream's own, StreamService's (rc32 audit P3 #80): up for as long as the stream, quiet while KaizoCore is in front,
 * and popping up the moment it goes to the background. It is the only notification the app makes, and from Android 13
 * the phone asks once, when the stream is first turned on (MainActivity).
 */
object StreamReminder {
    const val CHANNEL = "stream"
    const val CHANNEL_NAME = "Stream"
    const val TITLE = "KaizoCore is streaming"
    const val TEXT = "The game pauses while KaizoCore is in the background. Tap to go back."
    /** The words while KaizoCore is in front: how to end the stream. */
    const val TEXT_ON = "Turn it off with STREAM ON in the File menu."
    const val ID = 7001

    /** KaizoCore is in the background, from MainActivity.onStop to onStart: the notification's words follow it. */
    @Volatile var away = false
        private set

    /** What the notification says, and whether it pops up: the reminder in the background, a quiet line in front. */
    internal data class Words(val text: String, val alerts: Boolean)

    internal fun words(away: Boolean): Words = if (away) Words(TEXT, alerts = true) else Words(TEXT_ON, alerts = false)

    /** KaizoCore went to the background (MainActivity.onStop): the reminder, while the stream is on. */
    fun left(context: Context) {
        away = true
        if (!StreamHub.running || !allowed(context)) return
        runCatching { build(context, service = StreamService.up)?.let { NotificationManagerCompat.from(context).notify(ID, it) } }
    }

    /**
     * KaizoCore is in front again (MainActivity.onStart): the service's notification goes back to its quiet words, and
     * one no service holds goes.
     */
    fun back(context: Context) {
        away = false
        runCatching {
            val n = if (StreamService.up && StreamHub.running && allowed(context)) build(context, service = true) else null
            if (n != null) NotificationManagerCompat.from(context).notify(ID, n) else NotificationManagerCompat.from(context).cancel(ID)
        }
    }

    /**
     * The notification as things stand ([away]). With [service] it is StreamService's, which Android keeps up while the
     * service runs and takes down with it; without, a plain one that a tap clears. Null without a launcher intent.
     */
    fun build(context: Context, service: Boolean): Notification? {
        val w = words(away)
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH))
        // What the launcher does: the task comes back as it was, with the game where it paused.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return null
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle(TITLE)
            .setContentText(w.text)
            .setContentIntent(tap)
            .setOngoing(service)
            .setAutoCancel(!service)
            // In front it sits in the shade with no sound and no pop-up; the channel's alert is for the reminder.
            .setSilent(!w.alerts)
            .setPriority(if (w.alerts) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(if (w.alerts) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_SERVICE)
            // At once, not after the ten seconds Android 12 and later may hold a new foreground service's notification.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** From Android 13 a notification needs the player's yes. */
    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether to ask as the stream is turned on: Android 13 or later, and not allowed yet. */
    fun shouldAsk(context: Context): Boolean = !allowed(context)
}
