package com.ironmonone.app.stream

/**
 * The looks a stream page can take (streamer list item 6, 2026-10-05): `?theme=clean` (see-through, for over the
 * game) and `?theme=hud` (a ship's heads-up display), on /tracker, /attempts.html, /gameover and /timer. The CSS is
 * assets/stream/themes.css; the server puts it into the page as it sends it, with the look named on the `<html>` tag,
 * so the page needs no script for it and shows the right look from its first frame.
 *
 * A page whose address names no look, or one this list does not have, goes out exactly as it was: the default look
 * is the pages' own CSS, untouched.
 */
object StreamThemes {
    /** The looks there are, besides the pages' own. */
    val NAMES = listOf("clean", "hud")

    /** [asked] as a look's name, or null for the pages' own look (none asked, or one there is not). */
    fun name(asked: String?): String? = asked?.trim()?.lowercase()?.takeIf { it in NAMES }

    /**
     * [html] in the look [asked] for: [css] (the looks' CSS) put in just before `</head>`, and `data-theme` on the
     * `<html>` tag. With no look, [html] as it is.
     */
    fun apply(html: String, asked: String?, css: String): String {
        val look = name(asked) ?: return html
        var out = html
        val head = out.indexOf("</head>", ignoreCase = true)
        if (css.isNotBlank() && head >= 0) out = out.substring(0, head) + "<style id=\"kc-themes\">\n" + css + "\n</style>" + out.substring(head)
        val tag = out.indexOf("<html", ignoreCase = true)
        if (tag >= 0) out = out.substring(0, tag + 5) + " data-theme=\"" + look + "\"" + out.substring(tag + 5)
        return out
    }
}
