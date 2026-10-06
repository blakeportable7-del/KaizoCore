package com.ironmonone.app.stream

import com.ironmonone.app.Favorites
import com.ironmonone.app.GameSession
import com.ironmonone.app.HnsPool
import com.ironmonone.app.PrepStore

/**
 * Where the stream's favorite pictures come from (StreamFavorites, /favorite/1.png to /favorite/9.png).
 *
 * Until 2026-10-03 the Play screen made them as it opened and handed them to StreamHub, so a favorite edited on the
 * Kaizo IronMON screen reached OBS only once Play opened again (rc34's known issue; Blake wanted it fixed before rc34
 * shipped). Now the server asks this source on each request, and it reads what is saved: the game Play opens ([game],
 * PrepStore.session(), the game being streamed) and that game's favorite boxes ([boxes]). Whatever saves favorites,
 * the Kaizo IronMON screen or anything later, reaches OBS on the favorite page's next look, two seconds at most, with
 * Play closed.
 *
 * The pictures are drawn again only when the game, its file or its boxes changed since the last look; [picture] draws
 * one species as that game's tracker card does (StreamFavoritePictures). Reading the two small files on each request
 * costs a file read or two a second with every favorite on screen.
 */
class StreamFavoritesSource(
    private val game: () -> GameSession?,
    private val boxes: (GameSession) -> List<String>,
    private val picture: (GameSession, Int) -> ByteArray?,
) {
    private var drawnFor: String? = null
    private var drawn: List<ByteArray?> = emptyList()

    /** Box 1 first: a PNG per box, null for a box with no picture. Empty with no game. */
    @Synchronized
    fun pictures(): List<ByteArray?> {
        val s = runCatching { game() }.getOrNull()
        if (s == null) {
            drawnFor = null
            drawn = emptyList()
            return drawn
        }
        val slots = runCatching { boxes(s) }.getOrDefault(emptyList())
        // The game, its file (a new run rewrites it), and every box: any change draws the pictures again.
        val key = listOf(s.file.path, s.file.lastModified().toString(), s.kind?.id.orEmpty(), s.platform.name, slots.joinToString("\n"))
            .joinToString("\u0000")
        if (key != drawnFor) {
            drawn = StreamFavorites.pictures(StreamFavorites.icons(slots, s.kind)) { sp -> picture(s, sp) }
            drawnFor = key
        }
        return drawn
    }

    companion object {
        /** The app's source: the game [store] says Play opens, and the favorites saved for it, drawn by [picture]. */
        fun of(store: PrepStore, picture: (GameSession, Int) -> ByteArray?): StreamFavoritesSource = StreamFavoritesSource(
            game = { store.session() },
            // The boxes the run in play counts (HnsPool.favoritesScope): a Heart & Soul Vanilla run's three of its nine.
            boxes = { s -> HnsPool.favoritesScope(s.kind, store.files, nextRun = false).let { sc -> sc.used(Favorites.slots(store, s.kind?.id, sc.stored)) } },
            picture = picture,
        )
    }
}
