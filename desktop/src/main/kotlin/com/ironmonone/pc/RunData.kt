package com.ironmonone.pc

import com.ironmonone.app.EncounterBook
import com.ironmonone.app.StatMarks
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackerState
import java.io.File

/**
 * What the tracker learns about one run, kept in the run's own folder (PLAN row 5, section 1.3): the stat marks,
 * notes, encounters, seen moves and revealed abilities (the app's StatMarks, the same files the phone keeps, beside
 * marks.txt), counted the way the phone's Play screen counts them (EncounterBook, the battle's moves, the abilities a
 * battle reveals, the route a wild one was met on). The folder is found by the ROM's SHA-1, so loading a run's game
 * days later, after other games or seeds, brings its marks and notes back; New Run opens a new folder.
 */
class RunData(val folder: File) {
    val marks = StatMarks(File(folder, "marks.txt"))
    private val book = EncounterBook.of("pc/" + folder.absolutePath)
    private var lastCountedSpecies = -1

    /** Bumped whenever something the panel shows from the marks changed. */
    @Volatile var version = 0
        private set

    /** One tracker read: what the phone records on the same read (PlayScreen's LaunchedEffects, in order). */
    fun observe(st: TrackerState, tracker: GbaTracker?) {
        var changed = book.onGba(marks, EncounterBook.gbaUpdate(st), save = true)
        if (marks.addBattleMoves(st.enemyMovesThisBattle)) changed = true
        st.abilitiesRevealed.forEach { (sp, name) -> if (marks.revealAbility(sp, name)) changed = true }
        st.ownAbilities.forEach { (sp, name) -> if (marks.revealAbility(sp, name)) changed = true }
        val enemySpecies = st.enemy?.species ?: 0
        if (enemySpecies > 0 && enemySpecies != lastCountedSpecies) {
            if (st.isWildBattle && com.ironmonone.pcui.PcPanel.recordsEncounter(st)) st.mapId?.let { m ->
                if (marks.seeOnRoute(m, enemySpecies)) changed = true
                val area = st.encounterArea
                if (area != null && tracker != null && tracker.routeEncounters(m).containsKey(area) && marks.seeOnRouteArea(m, area, enemySpecies)) changed = true
            }
            lastCountedSpecies = enemySpecies
        } else if (enemySpecies <= 0) lastCountedSpecies = -1
        if (changed) version++
    }

    /** A click on a stat box: the next mark in besteon's cycle. */
    fun cycleMark(species: Int, stat: Int) { marks.cycle(species, stat); version++ }

    fun note(species: Int): String = marks.noteFor(species)

    fun setNote(species: Int, text: String) { marks.setNote(species, text); version++ }
}
