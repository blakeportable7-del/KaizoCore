package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A DS Pokemon's picture: its form's from the player's ROM (RomFormSprites), else its species' (PcAssets.dsSprite), the
 * way the DS card, the game over screen and the DS log draw it.
 *
 * Decoded off the main thread (RC35-NOTICED row 41): each of those used to decode a PNG with BitmapFactory inside
 * remember, during composition, and the ROM's pictures were decoded again on every call. A small cache keeps the last
 * ones, so a card drawn again (a swap, a scroll back) shows its picture on its first frame.
 */
internal object DsPictures {
    private const val KEEP = 96
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > KEEP
    }

    /** The cache key: the ROM in play too, as its pictures are that ROM's. */
    fun key(species: Int, form: Int, shiny: Boolean): String = "${RomSprites.activeKind?.id}/$species/$form/${if (shiny) 1 else 0}"

    /** The picture if it was decoded already, else null; never decodes. */
    fun peek(species: Int, form: Int, shiny: Boolean): ImageBitmap? = synchronized(cache) { cache[key(species, form, shiny)] }

    /** Decodes, or takes from the cache: call it off the main thread. */
    fun load(context: android.content.Context, species: Int, form: Int, shiny: Boolean): ImageBitmap? {
        val k = key(species, form, shiny)
        synchronized(cache) { if (cache.containsKey(k)) return cache[k] }
        val picture = RomFormSprites.sprite(context, species, form, shiny) ?: PcAssets.dsSprite(context, species, shiny)
        synchronized(cache) { cache[k] = picture }
        return picture
    }
}

/**
 * [DsPictures] for a composable: the cached picture at once, else null until it is decoded on the IO thread. A new
 * Pokemon never shows the last one's picture meanwhile.
 */
@Composable
internal fun rememberDsPicture(species: Int, form: Int, shiny: Boolean): ImageBitmap? {
    val context = LocalContext.current
    return produceState(DsPictures.peek(species, form, shiny), species, form, shiny, RomSprites.activeKind) {
        value = DsPictures.peek(species, form, shiny)
        if (value == null && species > 0) value = withContext(Dispatchers.IO) { DsPictures.load(context, species, form, shiny) }
    }.value
}
