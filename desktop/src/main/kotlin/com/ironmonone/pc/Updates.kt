package com.ironmonone.pc

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The update notice: KaizoCore PC reads the site's kaizocore/latest.json once at start, the file the app's own update
 * check reads, and says so when a newer release is out. KaizoCore PC is built from the same commit as the APK and
 * carries its versionCode, so the APK's entry is the release. When latest.json gains a "pc" entry of its own (PLAN
 * section 8), its versionCode and page win. Nothing is downloaded or installed: the notice names the page.
 */
object Updates {
    const val URL = "https://willowcreek.group/kaizocore/latest.json"
    const val PAGE = "https://willowcreek.group/kaizocore"

    class Notice(val version: String, val versionCode: Int, val page: String) {
        val message: String get() = "KaizoCore $version is out. Get the new KaizoCore PC from $page"
    }

    /** The notice [json] gives a jar of [ownCode], or null when this one is current (or the file says nothing usable). */
    fun notice(json: String, ownCode: Int): Notice? {
        val pc = Regex(""""pc"\s*:\s*\{([^}]*)\}""").find(json)?.groupValues?.get(1)
        val scope = pc ?: json
        val code = Regex(""""versionCode"\s*:\s*(\d+)""").find(scope)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val version = Regex(""""version"\s*:\s*"([^"]+)"""").find(scope)?.groupValues?.get(1) ?: return null
        val page = Regex(""""page"\s*:\s*"([^"]+)"""").find(scope)?.groupValues?.get(1)
            ?: Regex(""""page"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: PAGE
        if (!page.startsWith("https://")) return null
        return if (ownCode in 1 until code) Notice(version, code, page) else null
    }

    /** Reads latest.json (5 seconds at most) and returns the notice, or null; never throws. */
    fun check(url: String = URL, ownCode: Int = PcVersion.versionCode): Notice? = runCatching {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build()
        val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(5))
            .header("User-Agent", "KaizoCore-PC/${PcVersion.version}").GET().build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) null else notice(res.body(), ownCode)
    }.getOrNull()
}
