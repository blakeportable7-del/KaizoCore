package com.ironmonone.tracker.nuzlocke

/**
 * What the tracker's Nuzlocke panel says (2026-09-29), worked out here so it can be tested and so a Game Boy
 * or DS tracker can show the same lines. The panel only draws them.
 */
object NuzlockeView {

    enum class Tone { NORMAL, GOOD, BAD, WARN, DIM }

    class Line(val text: String, val tone: Tone = Tone.NORMAL)

    class Panel(
        val title: String,
        val lines: List<Line>,
        val alive: Int,
        val graveyard: Int,
        val warnings: Int,
    )

    /** The level cap line for [snap], or null when the rules have no caps. */
    fun capLine(ledger: NuzlockeLedger, snap: Snapshot?): Line? {
        if (!ledger.meta.rules.levelCaps) return null
        val caps = snap?.caps ?: return Line("Level cap: no table for this game", Tone.DIM)
        // What the game says is beaten, and what this ledger watched being beaten (the DS games do not say).
        val beaten = snap.beaten + ledger.meta.beatenBosses
        val next = caps.next(beaten) ?: return Line("Level cap: no bosses left", Tone.DIM)
        val source = if (next.fromRom) "from the game" else "standard table"
        if (next.kind == "e4") {
            val inOrder = next.group.isBlank()
            val e4 = caps.bosses.filter { it.kind == "e4" }.map { it.key }.toSet()
            // In a fixed order only the first member is entered with the cap; in any order, whoever comes first.
            val inside = if (inOrder) next.key != "e4-1" else beaten.any { it in e4 }
            if (inside) return Line("No level cap inside the League", Tone.DIM)
            val cap = caps.leagueCap ?: return Line("No level cap inside the League", Tone.DIM)
            return Line("Cap Lv $cap: ${if (inOrder) placeOf(next) else next.group} ($source)", Tone.NORMAL)
        }
        val set = caps.nextSet(beaten)
        if (set.size > 1) {
            // Fights that may come in any order: the cap is the leader's own, known when the fight starts.
            val lo = set.minOf { it.cap }; val hi = set.maxOf { it.cap }
            return Line("Cap ${if (lo == hi) "Lv $lo" else "Lv $lo to $hi"}: ${next.group}, the one you fight ($source)", Tone.NORMAL)
        }
        val cap = caps.capAtStart(next.key)
            ?: return Line(if (next.kind == "champion") "No level cap at the Champion" else "No level cap inside the League", Tone.DIM)
        return Line("Cap Lv $cap: ${placeOf(next)} ($source)", Tone.NORMAL)
    }

    /** "Gym 2, Misty"; "Blue" for a boss whose place and name are the same word. */
    private fun placeOf(b: BossCap): String = if (b.place == b.label) b.label else "${b.place}, ${b.label}"

    /** "Route 3: Spearow caught", or "Route 3: first encounter open". */
    fun areaLine(ledger: NuzlockeLedger, snap: Snapshot?): Line {
        if (snap == null) return Line("Waiting for the game", Tone.DIM)
        val rules = ledger.meta.rules
        val method = if (snap.inBattle && snap.wild) snap.method else null
        val here = NuzlockeAreas.of(snap.area, method, rules, ledger.meta.system)
        val rec = ledger.areas[here.key]
        val enc = rec?.encounter
        if (enc != null) {
            val tone = when (enc.outcome) {
                Outcome.CAUGHT -> Tone.GOOD
                Outcome.FAINTED, Outcome.LOST -> Tone.BAD
                else -> Tone.NORMAL
            }
            return Line("${here.name}: ${enc.speciesName} ${enc.outcome.label}", tone)
        }
        val skipped = rec?.extras?.lastOrNull { it.kind == ExtraKind.DUPE || it.kind == ExtraKind.TYPE || it.kind == ExtraKind.GENDER }
        if (skipped != null) return Line("${here.name}: first encounter open (skipped ${skipped.speciesName}, ${skipped.kind.label})", Tone.WARN)
        return Line("${here.name}: first encounter open", Tone.NORMAL)
    }

    /**
     * Whether the area line has a place to talk about. The title screen and the intro have no place name (the
     * panel said "Map 0: first encounter open" there, emulator QA 2026-09-29), so they get no line; a map the
     * tracker cannot name still shows once the ledger holds an encounter there. No snapshot shows its own line.
     */
    internal fun placeToShow(ledger: NuzlockeLedger, snap: Snapshot?): Boolean {
        if (snap == null || !snap.area.name.isNullOrBlank()) return true
        return ledger.areas[NuzlockeAreas.of(snap.area, null, ledger.meta.rules, ledger.meta.system).key]?.encounter != null
    }

    fun panel(ledger: NuzlockeLedger, snap: Snapshot?): Panel {
        val meta = ledger.meta
        val lines = ArrayList<Line>()
        when (meta.status) {
            RunStatus.OVER -> lines += Line("Run over: ${meta.endReason}", Tone.BAD)
            RunStatus.COMPLETE -> lines += Line("Champion beaten. The run is complete.", Tone.GOOD)
            RunStatus.ABANDONED -> lines += Line("Replaced by a newer run", Tone.DIM)
            RunStatus.ACTIVE -> if (!meta.started) lines += Line(
                if (meta.system.readsBalls) "Waiting for Poke Balls: the rules have not begun" else "Waiting for your first Pokemon: the rules have not begun", Tone.DIM,
            )
        }
        if ((meta.status == RunStatus.ACTIVE || meta.status == RunStatus.OVER) && placeToShow(ledger, snap)) lines += areaLine(ledger, snap)
        capLine(ledger, snap)?.let { lines += it }
        if (meta.rules.setStyle && meta.styleShift) lines += Line("Battle style is Shift. The rules say Set.", Tone.WARN)
        val warnings = ledger.openWarnings.size
        val title = "NUZLOCKE  " + meta.rules.preset.label + (meta.rules.monotypeLabel?.let { " ($it)" } ?: "")
        return Panel(title, lines, ledger.alive.size, ledger.graveyard.size, warnings)
    }
}
