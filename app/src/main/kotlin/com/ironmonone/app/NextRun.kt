package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.security.MessageDigest

/**
 * The next run, randomized before the player asks for it.
 *
 * Kaizo runs die in minutes, and NEW RUN used to randomize from scratch after
 * the confirm: about 40 seconds on a phone with the core already down. The
 * following run is made ahead instead, in the background while this one is
 * played (NextRunJob), into prep/next/; NEW RUN moves it into place and boots.
 * A run that has to be made there and then (nothing staged, or a seed the
 * player chose) is made here too and moved in the same way, so the current
 * run's files are only ever replaced whole, never written over in place.
 *
 * A stage is taken only by exactly the run it was made for. [Recipe] is what
 * it was made from: the game, the prepared ROM's identity, the settings file's
 * name and content hash, the passes the rules add around it (the 60% levels'
 * pre-pass and Gen 1's PART 2, as the player has them switched: ExtraPasses),
 * the engine and the app build. Any difference throws the stage away, so a
 * switch flipped on RUN after a stage was made is never handed a ROM built
 * the other way. The files are written first and
 * flushed to disk, and the meta that names them last, through a rename, so a
 * stage cut off by a kill or a dead battery has no meta and is deleted, never
 * taken. The meta also records each file's size, checked again on the way out.
 *
 * Every call holds one lock, so a stage being made for 40 seconds and a NEW
 * RUN taking it never meet halfway: [claim] waits for a stage in progress
 * rather than finding half of it. A claimed stage moves to its own folder
 * until [install], out of reach of the next stage being made or cleared.
 * None of this may run on the main thread.
 *
 * The run in play keeps its recipe and seed beside it (current.recipe, the
 * meta's own lines), written when it is installed: a run code has to say what
 * the run was made with, not what RUN's switches say by the time it is shared
 * ([currentRecipe]).
 */
class NextRun(val dir: File) {

    /** What a run is made from. Every field has to match for a stage to be taken. */
    data class Recipe(
        val kind: String,
        val ext: String,
        val engine: String,
        /** The app build that makes it: a new APK can randomize differently with the same engine. */
        val app: String,
        val rom: String,
        val romSize: Long,
        val romTime: Long,
        val romCrc: String,
        val settings: String,
        val settingsSha: String,
        /** Gen 1's PART 2 file, "name sha", when the run takes it; empty otherwise. */
        val secondPass: String,
        /** The 60% levels' pre-pass file, "name sha", when the run takes it; empty otherwise. */
        val prePass: String,
    ) {
        fun lines(): List<String> = listOf(
            "kind=$kind", "ext=$ext", "engine=$engine", "app=$app",
            "rom=$rom", "romSize=$romSize", "romTime=$romTime", "romCrc=$romCrc",
            "settings=$settings", "settingsSha=$settingsSha", "secondPass=$secondPass", "prePass=$prePass",
        )

        companion object {
            fun of(m: Map<String, String>): Recipe? = runCatching {
                Recipe(
                    m.getValue("kind"), m.getValue("ext"), m.getValue("engine"), m.getValue("app"),
                    m.getValue("rom"), m.getValue("romSize").toLong(), m.getValue("romTime").toLong(), m.getValue("romCrc"),
                    m.getValue("settings"), m.getValue("settingsSha"), m.getValue("secondPass"), m.getValue("prePass"),
                )
            }.getOrNull()
        }
    }

    /** A stage's files, the recipe and seed it was made with ([log] and [sidecar] when the engine wrote them). */
    class Staged(val recipe: Recipe, val seed: Long, val rom: File, val log: File?, val sidecar: File?)

    companion object {
        /** The meta's own layout; a stage from another layout is not read, only deleted. 2: the pre-pass. */
        const val FORMAT = "2"

        /** One per process: every NextRun on the same folder waits on the same stage. */
        private val LOCK = Any()

        /**
         * A claimed run this process has not installed yet. While it is, [clear] leaves [taken] alone: it is the
         * install in progress's (rc33 audit P0-12). Without a claim held, a [taken] folder is what a killed install
         * left behind, and goes.
         */
        @Volatile private var claimHeld = false

        /**
         * The recipe for randomizing [prepared] (a ROM of [kind]) with [settings]
         * and the passes the run takes around it, [secondPass] and [prePass]
         * (null for a pass it does not take: ExtraPasses), by the app build
         * [app]. Hashes the settings files, which are a few hundred bytes.
         */
        fun recipe(
            kind: RomKind, prepared: File, settings: File, secondPass: File?, prePass: File?, app: String,
            /** Heart & Soul's pool, null for the one chosen now (Randomizers.engineId). */
            pool: com.ironmonone.app.engine.HnsEngine.Pool? = null,
        ): Recipe = Recipe(
            kind = kind.id,
            ext = kind.fileExtension,
            engine = Randomizers.engineId(kind, pool),
            app = app,
            rom = prepared.absolutePath,
            romSize = prepared.length(),
            romTime = prepared.lastModified(),
            romCrc = "%08x".format(kind.expectedCrc),
            settings = settings.name,
            settingsSha = sha256(settings),
            secondPass = passOf(secondPass),
            prePass = passOf(prePass),
        )

        private fun passOf(f: File?): String = f?.let { it.name + " " + (if (it.isFile) sha256(it) else "missing") } ?: ""

        /** Where the recipe of the run installed as [rom] is kept: `<rom without extension>.recipe`. */
        fun recipeFileFor(rom: File): File = File(rom.parentFile, rom.nameWithoutExtension + ".recipe")

        /**
         * The recipe and seed the run in play was made with, from its
         * current.recipe ([install]), or null. Read only while it still names
         * that run: the game and the seed must be lastrun.txt's and
         * lastseed.txt's, so the recipe of a NEW RUN cut off halfway is never
         * taken for the run's.
         */
        fun currentRecipe(store: PrepStore): Pair<Recipe, Long>? {
            val kindId = store.loadLastRun()?.first ?: return null
            val kind = RomKind.byId(kindId) ?: return null
            val m = readLines(recipeFileFor(store.currentRunFor(kind))) ?: return null
            if (m["format"] != FORMAT) return null
            val recipe = Recipe.of(m) ?: return null
            val seed = m["seed"]?.toULongOrNull(16)?.toLong() ?: return null
            if (recipe.kind != kindId || "%016x".format(seed) != store.lastSeedText()) return null
            return recipe to seed
        }

        private fun readLines(f: File): Map<String, String>? = runCatching {
            if (!f.isFile) return null
            f.readLines().mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else null
            }.toMap()
        }.getOrNull()

        /** Through a flushed .tmp and a rename: [f] is there whole or not at all. */
        private fun writeLines(f: File, lines: List<String>) {
            val tmp = File(f.parentFile, f.name + ".tmp")
            java.io.FileOutputStream(tmp).use { out ->
                out.write(lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) throw java.io.IOException("Could not write " + f.name + ".")
            }
        }

        fun sha256(f: File): String =
            MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private val meta get() = File(dir, "next.meta")
    /** Where a claimed stage waits for [install]. */
    private val taken get() = File(dir, "taken")

    /** The stage for [recipe] when a whole one is on disk. Anything else there is deleted: another recipe's, or a stage cut off. */
    fun ready(recipe: Recipe): Staged? = synchronized(LOCK) { readyLocked(recipe) }

    /**
     * Randomizes the stage for [recipe] with [seed]. [randomize] writes the
     * ROM to the file it is given, the engine's log and sidecar beside it
     * (Randomizers.randomize does both). The meta is written only after all
     * of them are on disk; a failure deletes the stage and is rethrown.
     * [proceed] is asked once the lock is held: false, and nothing is made.
     */
    fun make(recipe: Recipe, seed: Long, randomize: (dest: File, seed: Long) -> Unit, proceed: () -> Boolean = { true }): Staged? = synchronized(LOCK) {
        if (!proceed()) return null
        clearLocked()
        dir.mkdirs()
        val rom = File(dir, "next." + recipe.ext)
        try {
            randomize(rom, seed)
            if (!rom.isFile || rom.length() == 0L) throw java.io.IOException("The randomizer wrote no ROM.")
            val log = Randomizers.logFor(rom).takeIf { it.isFile }
            val sidecar = Randomizers.sidecarFor(rom).takeIf { it.isFile }
            listOfNotNull(rom, log, sidecar).forEach(::flush)
            writeMeta(listOf("format=$FORMAT") + recipe.lines() + listOfNotNull(
                "seed=%016x".format(seed),
                "made=${System.currentTimeMillis()}",
                "romFile=${rom.name} ${rom.length()}",
                log?.let { "logFile=${it.name} ${it.length()}" },
                sidecar?.let { "sidecarFile=${it.name} ${it.length()}" },
            ))
            Staged(recipe, seed, rom, log, sidecar)
        } catch (t: Throwable) {
            clearLocked()
            throw t
        }
    }

    /**
     * [make] then [claim] in one hold of the lock (RunStart). As two holds, the background worker's [ready] or [clear]
     * could land between them and delete the stage just made (rc33 audit P1 #22).
     */
    fun makeAndClaim(recipe: Recipe, seed: Long, randomize: (dest: File, seed: Long) -> Unit): Staged? = synchronized(LOCK) {
        make(recipe, seed, randomize)
        claim(recipe)
    }

    /**
     * Takes the stage for [recipe]. Its meta is deleted before anything moves,
     * so from here on nothing can take it a second time, and whatever a crash
     * leaves behind has no meta and is never taken. The files then move to
     * their own folder, where making or clearing the next stage cannot reach
     * them before [install] does.
     */
    fun claim(recipe: Recipe): Staged? = synchronized(LOCK) {
        val staged = readyLocked(recipe) ?: return null
        if (!meta.delete()) { clearLocked(); return null }
        taken.deleteRecursively()
        taken.mkdirs()
        fun away(f: File): File = File(taken, f.name).also { move(f, it) }
        Staged(staged.recipe, staged.seed, away(staged.rom), staged.log?.let(::away), staged.sidecar?.let(::away))
            .also { claimHeld = true }
    }

    /**
     * Moves a claimed stage in as [dest], its log and species sidecar beside
     * it under the names the rest of the app reads (Randomizers.logFor,
     * Randomizers.sidecarFor), and writes its recipe and seed there last
     * ([recipeFileFor]). What the stage does not have is deleted on [dest]'s
     * side, so a log or a sidecar left by the run before never describes
     * this one.
     */
    fun install(staged: Staged, dest: File) = synchronized(LOCK) {
        dest.parentFile?.mkdirs()
        // First: never a recipe beside a ROM it did not make.
        val recipe = recipeFileFor(dest)
        recipe.delete()
        move(staged.rom, dest)
        val log = Randomizers.logFor(dest)
        val sidecar = Randomizers.sidecarFor(dest)
        if (staged.log != null) move(staged.log, log) else log.delete()
        if (staged.sidecar != null) move(staged.sidecar, sidecar) else sidecar.delete()
        writeLines(recipe, listOf("format=$FORMAT") + staged.recipe.lines() + "seed=%016x".format(staged.seed))
        taken.deleteRecursively()
        claimHeld = false
    }

    /**
     * Deletes whatever is staged, and a claimed run only when no install holds it. Turning off "Get the next run
     * ready" while a NEW RUN was between claim and install used to delete the claimed run, and the install then
     * failed after the run in play had been filed as ended and rotated away (rc33 audit P0-12).
     */
    fun clear() = synchronized(LOCK) {
        clearLocked()
        if (!claimHeld) taken.deleteRecursively()
    }

    private fun readyLocked(recipe: Recipe): Staged? {
        val m = readMeta()
        // No meta: nothing there, or a stage that never finished.
        if (m == null) { clearLocked(); return null }
        if (m["format"] != FORMAT || Recipe.of(m) != recipe) { clearLocked(); return null }
        val seed = m["seed"]?.toULongOrNull(16)?.toLong()
        val rom = sized(m["romFile"])
        val log = m["logFile"]?.let { sized(it) ?: return run { clearLocked(); null } }
        val sidecar = m["sidecarFile"]?.let { sized(it) ?: return run { clearLocked(); null } }
        if (seed == null || rom == null) { clearLocked(); return null }
        return Staged(recipe, seed, rom, log, sidecar)
    }

    /** A meta entry "name size": the file in [dir], only when it is there at exactly that size. */
    private fun sized(entry: String?): File? {
        val cut = entry?.lastIndexOf(' ') ?: return null
        if (cut <= 0) return null
        val name = entry.substring(0, cut)
        val size = entry.substring(cut + 1).toLongOrNull() ?: return null
        if ('/' in name || '\\' in name) return null
        return File(dir, name).takeIf { it.isFile && it.length() == size }
    }

    private fun readMeta(): Map<String, String>? = readLines(meta)

    /** Through a flushed .tmp and a rename: a meta is there whole or not at all. */
    private fun writeMeta(lines: List<String>) = writeLines(meta, lines)

    /** Onto the disk itself, not only the page cache: a dead battery must not leave a meta naming a file that is not all there. */
    private fun flush(f: File) {
        java.io.RandomAccessFile(f, "rw").use { it.fd.sync() }
    }

    private fun move(from: File, to: File) {
        if (from.renameTo(to)) return
        to.delete()
        if (from.renameTo(to)) return
        from.copyTo(to, overwrite = true)
        from.delete()
    }

    /** The stage's own files; a claimed one in [taken] is left for [install]. */
    private fun clearLocked() {
        dir.listFiles()?.filter { it.name != taken.name }?.forEach { it.deleteRecursively() }
    }
}
