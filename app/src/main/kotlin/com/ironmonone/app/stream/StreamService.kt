package com.ironmonone.app.stream

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * The stream as a foreground service (rc32 audit P3 #80; Blake, 2026-10-02: streaming runs as a foreground service).
 * It runs exactly as long as the server, which starts it (StreamHub.start) and stops it (StreamHub.stop), and its
 * notification is the stream's (StreamReminder), so the notification goes when the stream does. It used to be a plain
 * notification, which outlives its process on Android: after Home, swiping KaizoCore away in Recents ended the server
 * and left "KaizoCore is streaming" up with nothing streaming, and a tap on it opened the app with the stream off. Now
 * that swipe ends the stream and the service ([onTaskRemoved]), and a process Android ends takes the service's
 * notification with it.
 */
class StreamService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The stream can be off again before Android gets here (STREAM ON tapped at once): then there is nothing to show.
        val n = if (StreamHub.running) StreamReminder.build(this, service = true) else null
        up = n != null && runCatching {
            ServiceCompat.startForeground(this, StreamReminder.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }.isSuccess
        // Refused (it never should be from the player's tap): the stream goes on without the service, as it did before.
        if (!up) stopSelf()
        // Not made again after Android ends the process: the server went with it, and a new service would say streaming.
        return START_NOT_STICKY
    }

    /** KaizoCore swiped away in Recents: the stream ends, and the service and its notification with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        StreamHub.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        up = false
        super.onDestroy()
    }

    companion object {
        /** In the foreground, its notification up: StreamReminder changes that notification's words rather than its own. */
        @Volatile var up = false
            private set

        /**
         * Starts or stops the service with the server. startService rather than startForegroundService: it is only ever
         * started from the player's tap on STREAM, with KaizoCore in front, where Android allows it, and a service started
         * that way may be stopped before it reaches startForeground (STREAM ON tapped at once) without Android ending the
         * app for it, as it does a startForegroundService one.
         */
        fun follow(context: Context, on: Boolean) {
            val intent = Intent(context, StreamService::class.java)
            runCatching { if (on) context.startService(intent) else context.stopService(intent) }
        }
    }
}
