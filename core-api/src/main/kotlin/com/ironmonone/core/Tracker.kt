package com.ironmonone.core

enum class TrackerPanel { MY_MON, ENEMY, AREA }

/**
 * Result of locating the game's data structures in memory.
 *
 * NatDex 1.2.0 reorganized its internal layout and left older trackers blank. Addresses
 * are therefore DETECTED, never hardcoded, and detection failure is loud. A blank
 * tracker is a release blocker, not a degraded state.
 */
sealed interface AttachResult {
    data class Attached(val structuresFound: Int, val strategy: String) : AttachResult
    data class Failed(val reason: String, val structuresFound: Int) : AttachResult
}

data class MonView(
    val species: String,
    val level: Int,
    val nature: String,
    val natureUp: String?,
    val natureDown: String?,
    val ability: String,
    val heldItem: String?,
    val bst: Int,
    val types: List<String>,
    val hpCurrent: Int,
    val hpMax: Int,
    val moves: List<MoveView>,
    val gender: String?,
    val shiny: Boolean,
)

data class MoveView(
    val name: String,
    val type: String,
    val power: Int?,
    val accuracy: Int?,
    val ppCurrent: Int,
    val ppMax: Int,
)

interface Tracker {
    val id: String
    val panels: Set<TrackerPanel>

    fun supports(kind: RomKind): Boolean

    /** Locate structures. Must report honestly; see [AttachResult]. */
    fun attach(core: EmulatorCore, kind: RomKind): AttachResult

    fun detach()

    fun myMon(): MonView?
    fun enemyMon(): MonView?

    fun onNewRun()
}
