package com.ironmonone.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * The display name behind a Storage Access Framework uri.
 *
 * SAF uris carry no usable path, so the only way to show the user the filename they
 * picked is to query the provider for it.
 */
internal fun Context.displayNameOf(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.let { return it }
            }
        }
    return uri.lastPathSegment ?: "unknown"
}
