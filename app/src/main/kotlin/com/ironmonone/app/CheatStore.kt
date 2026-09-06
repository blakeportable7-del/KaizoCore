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

    fun save(id: String, cheats: List<Cheat>) {
        val f = file(id)
        if (cheats.isEmpty()) { f.delete(); return }
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(cheats.joinToString("\n") { c ->
            listOf(if (c.enabled) "1" else "0", c.name.replace('\t', ' '), c.code.replace("\n", "\\n")).joinToString("\t")
        })
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    companion object {
        /** Whether cheats may run at all for this session. Tracked games: never. */
        fun allowed(session: GameSession): Boolean = !session.tracked

        /**
         * What the core is given: lines trimmed, blank lines dropped, hex
         * uppercased, joined with '+'. Returns null for a code with nothing
         * in it, so an empty cheat is never sent.
         */
        fun normalise(code: String, platform: Platform): String? {
            val lines = code.lines().map { it.trim() }.filter { it.isNotEmpty() }
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
