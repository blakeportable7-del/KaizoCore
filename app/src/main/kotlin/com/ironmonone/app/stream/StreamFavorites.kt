package com.ironmonone.app.stream

import com.ironmonone.app.FavoriteIcon
import com.ironmonone.app.FavoriteIcons
import com.ironmonone.app.Favorites
import com.ironmonone.core.RomKind
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * The favorites as OBS sources (Blake, 2026-10-03, linking UTDZac's Favorites As Sources, the Ironmon Tracker
 * extension at github.com/UTDZac/FavoritesAsSources-IronmonExtension, v1.0, MIT). On the PC the extension copies each
 * favorite's icon to a file, Favorite1.png to Favorite9.png, and copies again when the favorites change; a streamer adds
 * each file to OBS as an image source, which reloads it when the file changes.
 *
 * Here the same pictures come from the stream server, the KaizoCore way: /favorite/1.png to /favorite/9.png, one per
 * favorite box, each the picture the tracker's no-party card draws for that favorite, drawn from the saved favorites of
 * the game being streamed as soon as they change, Play open or not (StreamFavoritesSource, StreamFavoritePictures in
 * FavoriteIcons.kt). OBS's image source reads files, not addresses, so
 * each favorite also has a page, /favorite/1 to /favorite/9, for a browser source: it shows the picture on a see-through
 * background, scaled to the source with hard pixel edges, and asks for it again every two seconds, so it changes when
 * the favorites do. The server answers 304 to a picture the page already has (its [etag]).
 *
 * Numbering follows the boxes on the Kaizo IronMON screen, as the extension's follows its slots: emptying box 2 leaves
 * favorite 3 where it was. A box with no favorite, a name that is no Pokemon of this game, a number past the game's own
 * boxes and a picture not made yet all serve [EMPTY], a 1 by 1 PNG with nothing in it, with a 200: OBS and a browser
 * show nothing at all, where a 404 is an error box in OBS and a broken picture in a page. (The extension leaves the old
 * file in place for an emptied slot, so OBS keeps showing the Pokemon that was there.)
 */
object StreamFavorites {
    /** As many as the app keeps for any game, a Nat. Dex game's nine (Favorites.NAT_DEX_SLOTS), and the extension's nine. */
    const val SLOTS = Favorites.NAT_DEX_SLOTS

    /** Each of the game's favorite [slots] (the boxes, in order): its icon as the card spells and draws it, or null when the box is empty. */
    fun icons(slots: List<String>, kind: RomKind?): List<FavoriteIcon?> =
        slots.take(SLOTS).map { s -> if (s.isBlank()) null else FavoriteIcons.of(listOf(s), kind).firstOrNull() }

    /** The picture for each of [icons]: [picture] makes a species' PNG; null where a box is empty or has no picture. */
    fun pictures(icons: List<FavoriteIcon?>, picture: (Int) -> ByteArray?): List<ByteArray?> =
        icons.map { f -> f?.species?.let { runCatching { picture(it) }.getOrNull() } }

    /** A 1 by 1 PNG, red, green, blue and alpha all 0: what an empty favorite serves. */
    val EMPTY: ByteArray = blankPng()

    /** The picture's validator, quoted as HTTP has it: its checksum and length, so a new picture is a new tag. */
    fun etag(png: ByteArray): String = "\"" + "%08x".format(CRC32().apply { update(png) }.value) + "-" + png.size + "\""

    /** The favorite a path names, and whether it asks for the picture (".png") or the page; null for any other path. */
    fun route(path: String): Pair<Int, Boolean>? {
        val m = ROUTE.matchEntire(path) ?: return null
        val n = m.groupValues[1].toInt()
        return if (n in 1..SLOTS) n to m.groupValues[2].isNotEmpty() else null
    }

    private val ROUTE = Regex("/favorite/([1-9][0-9]?)(\\.png)?")

    /** The page for favorite [n] at [base] (the phone's origin), with the key: what a browser source in OBS opens. */
    fun pageUrl(base: String, token: String, n: Int): String = "$base/favorite/$n?k=" + java.net.URLEncoder.encode(token, "UTF-8")

    /** The page itself: the same for every favorite, which reads its number from its own address. */
    fun page(): String = PAGE

    private fun blankPng(): ByteArray {
        // One row: filter byte 0, then one pixel of four zero bytes.
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val packed = ByteArray(64)
        val n = try {
            deflater.setInput(ByteArray(5))
            deflater.finish()
            deflater.deflate(packed)
        } finally { deflater.end() }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        // 1 by 1, 8 bits a channel, color type 6 (red, green, blue, alpha), no interlace.
        chunk(out, "IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0))
        chunk(out, "IDAT", packed.copyOf(n))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val t = type.toByteArray(Charsets.US_ASCII)
        fun int(v: Int) = out.write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
        int(data.size)
        out.write(t)
        out.write(data)
        int(CRC32().apply { update(t); update(data) }.value.toInt())
    }

    /**
     * The browser source's page. Like the other stream pages it holds no dollar sign and uses single quotes, so a raw
     * string holds it untouched. It never writes words: it is on a live stream. While the phone cannot be reached it
     * keeps the last picture it had.
     */
    private val PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KaizoCore favorite</title>
<style>
html,body{margin:0;height:100%;overflow:hidden;background:transparent}
#f{position:absolute;left:0;top:0;width:100%;height:100%;object-fit:contain;image-rendering:crisp-edges;image-rendering:pixelated;visibility:hidden}
</style></head><body>
<img id="f" alt="">
<script>
(function () {
  'use strict';
  var q = new URLSearchParams(location.search);
  var k = q.get('k') || '';
  var parts = location.pathname.split('/');
  var n = parts[parts.length - 1];
  var img = document.getElementById('f');
  if (q.get('smooth') === '1') img.style.imageRendering = 'auto';
  var tag = null, shown = null;
  // A new picture replaces the old one only once it has loaded, so the source never blinks.
  function poll() {
    fetch('/favorite/' + n + '.png?k=' + encodeURIComponent(k), { cache: 'no-cache' }).then(function (r) {
      if (!r.ok) return null;
      var t = r.headers.get('ETag') || '';
      if (t && t === tag) return null;
      return r.blob().then(function (b) {
        var u = URL.createObjectURL(b);
        img.onload = function () {
          img.style.visibility = 'visible';
          if (shown) URL.revokeObjectURL(shown);
          shown = u; tag = t;
        };
        img.src = u;
      });
    }).catch(function () { });
  }
  poll();
  setInterval(poll, 2000);
})();
</script></body></html>
"""
}
