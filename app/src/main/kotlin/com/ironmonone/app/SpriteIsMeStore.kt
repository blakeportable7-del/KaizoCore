package com.ironmonone.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * What the player imports for "Your own sprite", on disk under prep/spriteisme/ (Backup carries that folder, so a
 * restored phone has its sprite back): one picture, or a set of sheets, never both. Plain files and bytes, no
 * Android, so the rules (what is kept, what is refused, what a new import replaces) are tested on the JVM; the
 * decoding of a picked image is [SpriteIsMeImport]'s.
 */
object SpriteIsMeStore {
    const val ROOT = "prep/spriteisme"

    /** A picked file bigger than this is not read: a sprite sheet is small, and a zip bomb must stop somewhere. */
    const val MAX_FILE = 8 * 1024 * 1024
    const val MAX_ENTRIES = 64
    const val MAX_TOTAL = 32 * 1024 * 1024

    fun dir(filesDir: File) = File(filesDir, ROOT)
    fun pictureFile(filesDir: File) = File(dir(filesDir), "picture.png")
    fun sheetDir(filesDir: File) = File(dir(filesDir), "sheet")
    fun sheetFile(filesDir: File, a: WalkingPals.Anim) = File(sheetDir(filesDir), a.key + ".png")

    /** The sheets on disk with their pixel sizes, read from the PNG headers. */
    fun sheetSizes(filesDir: File): Map<WalkingPals.Anim, Pair<Int, Int>> {
        val out = LinkedHashMap<WalkingPals.Anim, Pair<Int, Int>>()
        for (a in SheetSet.ANIMS) {
            val f = sheetFile(filesDir, a)
            if (!f.isFile) continue
            // (readNBytes is Java 11: not on the Android 8 to 12 this app runs on.)
            val head = runCatching {
                f.inputStream().use { s ->
                    val b = ByteArray(32)
                    var n = 0
                    while (n < b.size) { val r = s.read(b, n, b.size - n); if (r < 0) break; n += r }
                    b.copyOf(n)
                }
            }.getOrNull() ?: continue
            SheetSet.pngSize(head)?.let { out[a] = it }
        }
        return out
    }

    /** True when the imported art the settings name is on disk. */
    fun ready(filesDir: File, own: SpriteIsMeSettings.Own): Boolean = when (own) {
        SpriteIsMeSettings.Own.NONE -> false
        SpriteIsMeSettings.Own.PICTURE -> pictureFile(filesDir).isFile
        SpriteIsMeSettings.Own.SHEET -> sheetSizes(filesDir).isNotEmpty()
    }

    /** Keep [png] as the player's picture and drop any sheet set. False when it could not be written. */
    fun savePicture(filesDir: File, png: ByteArray): Boolean {
        if (!SheetSet.isPng(png) || png.size > MAX_FILE) return false
        if (!SafeWrite.bytes(pictureFile(filesDir), png)) return false
        sheetDir(filesDir).deleteRecursively()
        SpriteIsMeSettings.own = SpriteIsMeSettings.Own.PICTURE
        finish()
        return true
    }

    /**
     * Keep these sheets (one PNG per animation) as the player's set and drop the picture. Refuses a set whose
     * sheets are not readable PNGs, or that has none. Returns how many sheets were kept.
     */
    fun saveSheets(filesDir: File, sheets: Map<WalkingPals.Anim, ByteArray>): Int {
        val ok = sheets.filter { (_, b) -> b.size <= MAX_FILE && SheetSet.pngSize(b) != null }
        if (ok.isEmpty()) return 0
        val dir = sheetDir(filesDir)
        dir.deleteRecursively()
        var kept = 0
        for ((a, b) in ok) if (SafeWrite.bytes(sheetFile(filesDir, a), b)) kept++
        if (kept == 0) return 0
        pictureFile(filesDir).delete()
        SpriteIsMeSettings.own = SpriteIsMeSettings.Own.SHEET
        finish()
        return kept
    }

    /** Take the imported sprite away: the picture, the sheets, and the setting that named them. */
    fun remove(filesDir: File) {
        pictureFile(filesDir).delete()
        sheetDir(filesDir).deleteRecursively()
        SpriteIsMeSettings.own = SpriteIsMeSettings.Own.NONE
        if (SpriteIsMeSettings.who == SpriteIsMeSettings.Who.OWN) SpriteIsMeSettings.who = SpriteIsMeSettings.Who.LEAD
        finish()
    }

    private fun finish() {
        SpriteIsMeSettings.artVersion++
        SpriteIsMeSettings.save()
    }

    /**
     * What a picked file holds: a zip's entries (name and bytes), or the file itself. Capped on entry count, on
     * each entry and on the total, so a zip bomb stops; nothing here writes to disk.
     */
    fun unpack(name: String, bytes: ByteArray): List<Pair<String, ByteArray>> {
        if (!ZipImport.isZip(name, bytes)) return listOf(name to bytes)
        val out = ArrayList<Pair<String, ByteArray>>()
        var total = 0L
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (out.size < MAX_ENTRIES) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || e.name.substringAfterLast('/').startsWith("._")) { zip.closeEntry(); continue }
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var n = 0L
                    while (true) {
                        val r = zip.read(chunk); if (r < 0) break
                        n += r; total += r
                        if (n > MAX_FILE || total > MAX_TOTAL) { n = -1; break }
                        buf.write(chunk, 0, r)
                    }
                    if (n < 0) break
                    out += e.name to buf.toByteArray()
                    zip.closeEntry()
                }
            }
        }
        return out
    }
}
