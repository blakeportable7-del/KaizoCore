package com.ironmonone.app.stream.twitch

import android.content.Context
import com.ironmonone.app.GachaMon
import com.ironmonone.app.GachaMonEntry
import com.ironmonone.app.stream.StreamHub

/**
 * The app's one [TwitchLink]: tokens in the Keystore-sealed file, the switches in prep/settings/twitch-chat.txt (the
 * player's own, so they ride along in a backup; the sign-in never does), answers from the stream snapshot
 * (StreamHub.state) and GachaMon. MainActivity calls [init] once; the Stream page in More draws [link]'s status.
 */
object TwitchChat {
    const val SETTINGS_FILE = "prep/settings/twitch-chat.txt"

    @Volatile var link: TwitchLink? = null
        private set

    @Synchronized
    fun init(context: Context) {
        if (link != null) return
        val app = context.applicationContext
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
        val settings = ChatSettings(java.io.File(app.filesDir, SETTINGS_FILE))
        val l = TwitchLink(
            store = KeystoreTokenStore.inFilesDir(app.filesDir),
            settings = settings,
            facts = AppFacts(version, settings),
            state = { StreamHub.state },
        )
        link = l
        // Off the main thread: the Keystore read is a disk read and a crypto call.
        Thread({ runCatching { l.start() } }, "twitch-start").apply { isDaemon = true }.start()
    }

    /** GachaMon as !gachamon reads it. Every read is guarded: GachaMon belongs to Play, and chat must never disturb it. */
    private class AppFacts(override val version: String, private val settings: ChatSettings) : ChatFacts {
        override fun enabled(c: ChatCommand) = settings.enabled(c)

        override fun gacha(name: String): GachaLookup = runCatching {
            if (GachaMon.packShowing) return@runCatching GachaLookup.PackOpening
            if (GachaMon.game == null) return@runCatching GachaLookup.NotHere
            val entry: GachaMonEntry? = if (name.isBlank()) {
                GachaMon.liveState?.party?.firstOrNull()?.let { GachaMon.cardFor(it) }
            } else {
                val want = name.lowercase().filter { it.isLetterOrDigit() }
                GachaMon.recent.toList().lastOrNull { it.speciesName.lowercase().filter { c -> c.isLetterOrDigit() } == want }
            }
            if (entry == null) GachaLookup.NoCard else GachaLookup.Card(facts(entry))
        }.getOrDefault(GachaLookup.NoCard)

        private fun facts(e: GachaMonEntry): GachaFacts {
            val s = e.card.stats
            return GachaFacts(e.speciesName, e.abilityName, e.stars, e.card.ratingScore, e.card.battlePower, e.card.level,
                listOf(s.hp, s.atk, s.def, s.spa, s.spd, s.spe), (0 until 4).map { e.moveName(it) }, e.card.isShiny == 1)
        }
    }
}
