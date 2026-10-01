package com.ironmonone.app.bot

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File

/**
 * Starts [BotPort] when the app's process starts, if the PC armed it by writing files/bot/enabled (and
 * files/bot/token). A provider, because it runs before anything else without a line in the main code. Debug
 * builds only (src/debug); it answers no queries.
 */
class BotPortStarter : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return true
        val dir = File(ctx.filesDir, "bot")
        if (File(dir, "enabled").exists()) {
            runCatching { BotPort.start(File(dir, "token").readText().trim()) }
        }
        return true
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}
