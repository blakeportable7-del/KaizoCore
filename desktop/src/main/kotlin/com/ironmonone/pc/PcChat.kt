package com.ironmonone.pc

import com.ironmonone.app.stream.twitch.ChatCommand
import com.ironmonone.app.stream.twitch.ChatFacts
import com.ironmonone.app.stream.twitch.ChatLine
import com.ironmonone.app.stream.twitch.ChatSettings
import com.ironmonone.app.stream.twitch.GachaLookup
import com.ironmonone.app.stream.twitch.TwitchLink
import com.ironmonone.app.stream.twitch.TwitchTokenStore
import java.io.File

/**
 * `!fav <slot> <pokemon>` (PLAN row 9): a moderator or the broadcaster sets one of the favorites from chat, the name
 * held to one Pokemon (Session.setFavorite, PcFavorites.strict), and the line is answered; anyone else's gets no answer.
 * One a second for each chatter, so a mod pasting the same line twice spends one reply and two mods never drop each
 * other's. `!favorites` lists them for anyone, once every ten seconds as the shared commands are paced (ChatGate).
 * [shown] is how many boxes the runs count (nine, three for a Vanilla pool).
 */
class FavCommand(
    private val setFavorite: (slot: Int, name: String, by: String) -> String,
    private val list: () -> String = { "" },
    private val shown: () -> Int = { 9 },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastBy = HashMap<String, Long>()
    private var lastList = Long.MIN_VALUE / 2

    fun handle(line: ChatLine): String? {
        val text = line.text.trim()
        if (LIST.matches(text)) {
            synchronized(this) {
                val now = clock()
                if (now - lastList < 10_000) return null
                lastList = now
            }
            return list()
        }
        val mod = line.isModerator || line.isBroadcaster
        if (BARE.matches(text)) return if (mod) help() else null
        val m = PATTERN.matchEntire(text) ?: return null
        if (!mod) return null
        synchronized(this) {
            val now = clock()
            val who = line.chatterId.ifEmpty { line.chatterName }
            if (now - (lastBy[who] ?: Long.MIN_VALUE / 2) < 1000) return null
            lastBy[who] = now
        }
        return setFavorite(m.groupValues[1].toInt(), m.groupValues[2].trim().take(40), line.chatterName.ifBlank { line.chatterId })
    }

    /** The line `!help !fav` and a bare `!fav` answer, with the slots this run counts. */
    fun help(): String = "!fav <slot> <pokemon> (mods) > Sets favorite 1 to ${shown()} to that Pokemon. !favorites lists them."

    companion object {
        val PATTERN = Regex("""!fav(?:ou?rite)?\s+(\d{1,2})\s+(.{1,60})""", RegexOption.IGNORE_CASE)
        private val LIST = Regex("""!fav(?:ou?rite)?s""", RegexOption.IGNORE_CASE)
        private val BARE = Regex("""!fav(?:ou?rite)?""", RegexOption.IGNORE_CASE)
        const val LIST_HELP = "> Lists the streamer's favorites, the Pokemon they hope to find in a starter ball."
    }
}

/**
 * Twitch chat on the PC: the app's TwitchLink (device-code sign-in, EventSub, the shared commands !pokemon, !moves,
 * !attempts, !progress, !heals, !about, !help) with the tokens in [DpapiTokenStore], the answers from the session's
 * snapshot (PcSnapshot), and `!fav` in front of them. GachaMon is the phone's: !gachamon answers that cards are made
 * on the phone.
 */
class PcChat(dir: File, private val session: () -> Session?, private val version: String, private val say: (String) -> Unit, store: TwitchTokenStore = DpapiTokenStore(File(dir, DpapiTokenStore.FILE))) {
    val settings = ChatSettings(File(dir, "twitch-chat.txt"))
    val fav = FavCommand(
        setFavorite = { slot, name, by -> session()?.setFavorite(slot, name, by) ?: STARTING },
        list = { session()?.favoritesLine() ?: STARTING },
        shown = { session()?.favoritesShown ?: 9 },
    )

    private val facts = object : ChatFacts {
        override val version: String get() = this@PcChat.version
        override fun gacha(name: String): GachaLookup = GachaLookup.NotHere
        override fun enabled(c: ChatCommand): Boolean = c != ChatCommand.GACHAMON && settings.enabled(c)
        override val extraHelp: List<Pair<String, String>>
            get() = listOf("!favorites" to FavCommand.LIST_HELP, "!fav" to fav.help().removePrefix("!fav "))
    }

    val link = TwitchLink(store = store, settings = settings, facts = facts, state = { session()?.snapshotJson() ?: "{}" }, extra = { line -> fav.handle(line) })

    /** At start: a saved sign-in goes straight back to answering chat. */
    fun start() {
        Thread({ runCatching { link.start() }.onFailure { say("Twitch: $it") } }, "twitch-start").apply { isDaemon = true }.start()
    }

    /** The one line the window shows for chat. */
    fun statusLine(): String {
        val s = link.status.value
        return when (s.phase) {
            TwitchLink.Phase.SIGNED_OUT -> "Twitch chat: not connected." + (if (s.note.isNotEmpty()) " ${s.note}" else "")
            TwitchLink.Phase.GETTING_CODE -> "Twitch chat: asking Twitch for a code."
            TwitchLink.Phase.WAITING_FOR_CODE -> "Twitch chat: on any device go to ${s.link} and enter the code ${s.code}"
            TwitchLink.Phase.CONNECTING -> "Twitch chat: connecting as ${s.name}."
            TwitchLink.Phase.LISTENING -> "Twitch chat: answering as ${s.name} (!pokemon, !moves, !attempts, !progress, !heals, !favorites, !fav for mods)."
            TwitchLink.Phase.RECONNECTING -> "Twitch chat: ${s.note}"
            TwitchLink.Phase.PAUSED -> "Twitch chat: signed in as ${s.name}, not answering."
        }
    }

    val signedIn: Boolean get() = link.status.value.signedIn

    fun shutdown() = link.shutdown()

    companion object {
        const val STARTING = "KaizoCore PC is still starting. Try again in a moment."
    }
}
