package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File

/**
 * Cheat codes, kept per game (session id) and handed to the core through
 * libretro's cheat interface. mGBA takes GameShark, CodeBreaker and Action
 * Replay codes; Gambatte takes GameShark and Game Genie; melonDS takes
 * Action Replay. All three read one code as lines joined by '+', which is
 * the RetroArch convention every core already parses.
 *
 * Cheats are never applied to a TRACKED game (Blake, 2026-09-05: off on an
 * IronMON run so a stream can never be questioned). That gate is [allowed],
 * a pure function the Play screen and the dialog both use, so there is one
 * answer.
 *
 * Nothing is bundled: no code database ships with the app. The player
 * types or pastes codes; the file is theirs.
 */
class CheatStore(private val dir: File) {

    init { dir.mkdirs() }

    data class Cheat(val name: String, val code: String, val enabled: Boolean)

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".tsv")

    fun load(id: String): List<Cheat> =
        runCatching { file(id).readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) null else Cheat(p[1], p[2].replace("\\n", "\n"), p[0] == "1")
        }

    /**
     * Keeps [cheats] for the game [id], whole or not at all (SafeWrite). False when the phone refused the write: it
     * threw out of the dialog's tap, and the app closed mid-game on a full phone (rc32 audit P2 #16). It is said
     * (SaveTrouble), since the dialog does not wait for the answer.
     *
     * A code goes in with plain line ends and a name on one line: a carriage return went into the file raw, and reading
     * it back ended the line there, so a code pasted with Windows line ends kept only its first line, and a name with a
     * line break lost the whole cheat (rc32 audit P3 #23).
     */
    fun save(id: String, cheats: List<Cheat>): Boolean {
        val f = file(id)
        if (cheats.isEmpty()) return f.delete() || !f.exists()
        val ok = SafeWrite.text(f, cheats.joinToString("\n") { c ->
            listOf(if (c.enabled) "1" else "0", oneLine(c.name), c.code.lines().joinToString("\\n")).joinToString("\t")
        })
        if (!ok) SaveTrouble.report(SaveTrouble.SETTING, SAVE_FAILED)
        return ok
    }

    /** A name as one line of the file: no tab, no line break of any kind. */
    private fun oneLine(name: String): String = name.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')

    companion object {
        const val SAVE_FAILED = "Could not save the cheats. If this phone is out of space, free some, then try again."

        /**
         * Whether cheats may run at all for this session: never in a Kaizo IronMON run or a Nuzlocke, and in plain
         * play, tracked or not (2026-09-30, UX audit P1: every tracked game used to refuse them).
         */
        fun allowed(session: GameSession, nuzlocke: Boolean = NuzlockeTracking.inPlay()): Boolean = !session.isRun && !nuzlocke

        /**
         * What the core is handed (GLRetroView.setCheats): every code that normalises, enabled or not, in the list's
         * order; nothing at all when cheats are not [allowed], which leaves the core with none.
         */
        fun forCore(cheats: List<Cheat>, platform: Platform, allowed: Boolean): List<Pair<Boolean, String>> =
            if (!allowed) emptyList() else cheats.mapNotNull { c -> normalise(c.code, platform)?.let { c.enabled to it } }

        /**
         * What the core is given: lines trimmed, blank lines dropped, hex
         * uppercased, joined with '+'. Returns null for a code with nothing
         * in it, so an empty cheat is never sent.
         */
        fun normalise(code: String, platform: Platform): String? {
            val raw = code.lines().map { it.trim() }.filter { it.isNotEmpty() }
            // A line with letters past F is not a code. Stripping them used to
            // turn "hello" into "E" and accept it (audit, 2026-09-27).
            val allowed = if (platform == Platform.GBC) Regex("[0-9A-Fa-f +:-]+") else Regex("[0-9A-Fa-f +:]+")
            if (raw.any { !allowed.matches(it) }) return null
            val lines = raw
                .map { line ->
                    // Game Genie codes for the Game Boy keep their dashes; everything
                    // else is hex pairs that cores compare case-insensitively but
                    // some print back, so uppercase for consistency.
                    if (platform == Platform.GBC && line.contains('-')) line.uppercase()
                    else line.uppercase().replace(Regex("[^0-9A-F +:]"), "")
                }
                .filter { it.isNotEmpty() }
            return lines.takeIf { it.isNotEmpty() }?.joinToString("+")
        }
    }
}
