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

    /** The sheets on disk that can be drawn, with their pixel sizes: a sheet past SheetSet's bound is left out (rc32 audit P3 #66). */
    fun sheetSizes(filesDir: File): Map<WalkingPals.Anim, Pair<Int, Int>> = headerSizes(filesDir).filterValues { SheetSet.fits(it) }

    /** A sheet on disk is past the bound: one imported before there was one, or put there by hand. */
    fun oversized(filesDir: File): Boolean = headerSizes(filesDir).values.any { !SheetSet.fits(it) }

    /** A picked sheet that saveSheets leaves out for its size, so the import can say why. */
    fun tooBig(sheets: Map<WalkingPals.Anim, ByteArray>): Boolean = sheets.values.any { b -> SheetSet.pngSize(b)?.let { !SheetSet.fits(it) } == true }

    /** Every sheet on disk with its pixel size, read from the PNG headers. */
    private fun headerSizes(filesDir: File): Map<WalkingPals.Anim, Pair<Int, Int>> {
        recover(filesDir)
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

    /** [saveSheets]' answer for sheets that were read and could not be written: the set in use stays as it was. */
    const val NOT_SAVED = -1

    /**
     * Keep these sheets (one PNG per animation) as the player's set and drop the picture. Refuses a set whose
     * sheets are not readable PNGs, or that has none (0), and a sheet past SheetSet's bound ([tooBig] says so).
     * Returns how many sheets were kept, or [NOT_SAVED].
     *
     * The new set is written into a folder beside the one in use and swapped in only once every sheet is written. The
     * set in use was deleted first: on a full phone every write then failed, the old sheets were gone, and the screen
     * said no sheets were found (rc32 audit P3 #67). [write] is SafeWrite's; a test hands in one that refuses.
     */
    fun saveSheets(filesDir: File, sheets: Map<WalkingPals.Anim, ByteArray>, write: (File, ByteArray) -> Boolean = SafeWrite::bytes): Int {
        val ok = sheets.filter { (_, b) -> b.size <= MAX_FILE && SheetSet.pngSize(b)?.let { SheetSet.fits(it) } == true }
        if (ok.isEmpty()) return 0
        recover(filesDir)
        val dir = sheetDir(filesDir)
        val fresh = File(dir.parentFile, dir.name + ".new").apply { deleteRecursively() }
        for ((a, b) in ok) if (!write(File(fresh, a.key + ".png"), b)) { fresh.deleteRecursively(); return NOT_SAVED }
        val old = File(dir.parentFile, dir.name + ".old").apply { deleteRecursively() }
        if (dir.exists() && !dir.renameTo(old)) { fresh.deleteRecursively(); return NOT_SAVED }
        if (!fresh.renameTo(dir)) { old.renameTo(dir); fresh.deleteRecursively(); return NOT_SAVED }
        old.deleteRecursively()
        pictureFile(filesDir).delete()
        SpriteIsMeSettings.own = SpriteIsMeSettings.Own.SHEET
        finish()
        return ok.size
    }

    /** A swap a kill cut between its two renames left the old set beside none: it is the set again. */
    private fun recover(filesDir: File) {
        val dir = sheetDir(filesDir)
        val old = File(dir.parentFile, dir.name + ".old")
        if (old.isDirectory && !dir.exists()) old.renameTo(dir)
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
