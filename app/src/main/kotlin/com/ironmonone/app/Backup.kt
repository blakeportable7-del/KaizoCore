package com.ironmonone.app

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup is one zip of everything the player made and nothing they can
 * re-add: save states and their screenshots, battery saves, the auto-save,
 * locks, run notes, favourites, the current and previous run, attempt
 * counts, presets, key bindings, layouts, skins, cheats, core options.
 *
 * Deliberately NOT in it: library ROMs and patches, prepared bases, the
 * Nat. Dex patch files. Those are the player's own dumps, they are big,
 * and re-adding them takes a tap. A backup with them would be a ROM
 * bundle, and it would also stop fitting in an email.
 *
 * Restore writes only paths the allowlist admits, relative to filesDir,
 * and refuses anything that tries to escape it. Existing files are
 * overwritten: a restore is "put my saves back", not a merge.
 */
object Backup {

    /** Relative-path prefixes and exact files, under filesDir. */
    private val PREFIXES = listOf(
        "saves/",
        "prep/attempts/", "prep/games/", "prep/cheats/", "prep/layouts/", "prep/coreopts/",
        "prep/library/notes/", "prep/runs/", "prep/settings/",
    )
    private val FILES = setOf(
        "prep/marks.txt", "prep/notes.txt", "prep/routes.txt", "prep/moves.txt", "prep/abilities.txt",
        "prep/favorites.txt", "prep/lastrun.txt", "prep/lastseed.txt", "prep/keys.txt", "prep/skin.txt",
        "prep/padforce.txt", "prep/playspeed.txt", "prep/playmute.txt", "prep/library/session.txt",
    )

    fun admits(rel: String): Boolean {
        val r = rel.replace('\\', '/')
        if (r.startsWith("/") || r.split('/').any { it == ".." || it.isEmpty() && r.endsWith("/") }) return false
        if (r.contains("/../") || r.startsWith("../")) return false
        if (r.endsWith(".tmp")) return false
        return r in FILES || PREFIXES.any { r.startsWith(it) }
    }

    /** Every file under filesDir the backup takes, as relative paths. */
    fun collect(filesDir: File): List<String> =
        filesDir.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(filesDir).path.replace('\\', '/') }
            .filter { admits(it) }.sorted().toList()

    /** Write the backup. Returns the number of files written. */
    fun write(filesDir: File, out: OutputStream): Int {
        var n = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("KAIZOCORE-BACKUP.txt"))
            zip.write("KaizoCore backup v1\n".toByteArray()); zip.closeEntry()
            for (rel in collect(filesDir)) {
                val f = File(filesDir, rel)
                zip.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry(); n++
            }
        }
        return n
    }

    /** Restore from a backup. Returns files written; -1 when it is not a KaizoCore backup. */
    fun read(filesDir: File, input: InputStream): Int {
        var n = 0
        var marker = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "KAIZOCORE-BACKUP.txt") { marker = true; zip.closeEntry(); continue }
                if (e.isDirectory || !admits(e.name)) { zip.closeEntry(); continue }
                val target = File(filesDir, e.name)
                if (!target.canonicalPath.startsWith(filesDir.canonicalPath)) { zip.closeEntry(); continue }
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.outputStream().use { zip.copyTo(it) }
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                if (e.time > 0) target.setLastModified(e.time)
                n++; zip.closeEntry()
            }
        }
        return if (marker) n else -1
    }

    fun suggestedName(): String =
        "KaizoCore-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
}

/**
 * ROMs and patches inside a zip. The picker hands the app a .zip; this
 * pulls out every entry with a known extension so the library can import
 * each one as if it had been picked on its own. Anything else in the
 * archive (readmes, art) is ignored. Entries are capped at 256 MB, which
 * is bigger than any DS cartridge, so a zip bomb stops there.
 */
object ZipImport {
    private val ROM_EXT = setOf("gba", "gbc", "gb", "nds", "bps", "ips", "ups")
    /** Bigger than any DS cartridge (512 MB), so a zip bomb still stops. */
    private const val CAP = 600L * 1024 * 1024

    fun isZip(name: String, bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            (name.lowercase().endsWith(".zip") || (bytes[2].toInt() == 3 && bytes[3].toInt() == 4))

    /**
     * Stream each ROM or patch inside [input] to its own file under [dir],
     * never holding an entry in memory: a 512 MB DS dump inside a zip is the
     * normal case, not the edge. Returns (entry name, file) pairs.
     */
    fun extractToFiles(input: java.io.InputStream, dir: java.io.File, onProgress: ((Long) -> Unit)? = null): List<Pair<String, java.io.File>> {
        val out = ArrayList<Pair<String, java.io.File>>()
        dir.mkdirs()
        // Progress is measured on the COMPRESSED side, whose total the caller knows.
        var consumed = 0L
        val counted = object : java.io.FilterInputStream(input) {
            override fun read(): Int = super.read().also { if (it >= 0) { consumed++; onProgress?.invoke(consumed) } }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { consumed += it; onProgress?.invoke(consumed) } }
        }
        ZipInputStream(counted.buffered(1 shl 20)).use { zip ->
            var i = 0
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (e.isDirectory || ext !in ROM_EXT || name.startsWith("._")) { zip.closeEntry(); continue }
                val f = java.io.File(dir, "zip${i++}-$name")
                var total = 0L; var over = false
                f.outputStream().buffered(1 shl 20).use { o ->
                    val chunk = ByteArray(1 shl 16)
                    while (true) {
                        val r = zip.read(chunk); if (r < 0) break
                        total += r; if (total > CAP) { over = true; break }
                        o.write(chunk, 0, r)
                    }
                }
                if (over || total == 0L) f.delete() else out += name to f
                zip.closeEntry()
            }
        }
        return out
    }

    /** (file name, bytes) for each ROM or patch inside. Small archives only; see [extractToFiles]. */
    fun extract(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (e.isDirectory || ext !in ROM_EXT || name.startsWith("._")) { zip.closeEntry(); continue }
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val r = zip.read(chunk); if (r < 0) break
                    total += r; if (total > CAP) break
                    buf.write(chunk, 0, r)
                }
                if (total <= CAP && total > 0) out += name to buf.toByteArray()
                zip.closeEntry()
            }
        }
        return out
    }
}
