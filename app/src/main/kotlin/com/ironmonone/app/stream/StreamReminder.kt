package com.ironmonone.app.stream

import android.Manifest
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
 * P0-19). The guide says to keep it in front; this puts one notification up the moment it goes to the background,
 * which brings the player straight back with a tap, and takes it down when they return. It is the only notification
 * the app makes, and from Android 13 the phone asks once, when the stream is first turned on (MainActivity).
 */
object StreamReminder {
    const val CHANNEL = "stream"
    const val CHANNEL_NAME = "Stream"
    const val TITLE = "KaizoCore is streaming"
    const val TEXT = "The game pauses while KaizoCore is in the background. Tap to go back."
    private const val ID = 7001

    /** KaizoCore went to the background (MainActivity.onStop): the notification, while the stream is on. */
    fun left(context: Context) {
        if (!StreamHub.running || !allowed(context)) return
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH))
            // What the launcher does: the task comes back as it was, with the game where it paused.
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
            val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_stream)
                .setContentTitle(TITLE)
                .setContentText(TEXT)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            NotificationManagerCompat.from(context).notify(ID, n)
        }
    }

    /** KaizoCore is in front again (MainActivity.onStart): the notification goes. */
    fun back(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }

    /** From Android 13 a notification needs the player's yes. */
    fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether to ask as the stream is turned on: Android 13 or later, and not allowed yet. */
    fun shouldAsk(context: Context): Boolean = !allowed(context)
}
