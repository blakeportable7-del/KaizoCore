package com.ironmonone.tracker

/**
 * Repel usage, the part every tracker needs: Ironmon-Tracker's
 * Program.ActiveRepel and the DS tracker's RepelDrawer.
 *
 * The games keep the steps the active repel has left in one variable, 0 when
 * none is running. How long that repel WAS is not stored, so the reference
 * infers it from the highest count it has seen (100, Super 200, Max 250) and
 * resets to 100 when the count reaches 0.
 *
 * This lives beside the Gen 3 tracker because the DS tracker builds on it, and
 * both read the value; the colours and the drawing are the app's (see Repel).
 *
 * Gen 1 and Gen 2 are not included: those trackers carry the drawing code but
 * never call their update (`if false and Options[...]`), so nothing is tracked.
 */
object RepelRules {
    /** The longest any repel lasts; a bigger byte is not a repel, so it is ignored. */
    const val MAX_STEPS = 250

    /** The default a repel starts at, and what an ended repel resets to. */
    const val DEFAULT_DURATION = 100

    /**
     * Program.updateRepelSteps: the duration only ever grows within one repel,
     * and resets when the repel ends. [current] is the duration so far.
     */
    fun duration(steps: Int, current: Int): Int = when {
        steps <= 0 -> DEFAULT_DURATION
        steps > MAX_STEPS -> current
        steps > current && steps <= 200 -> 200
        steps > current && steps <= MAX_STEPS -> MAX_STEPS
        else -> current
    }

    /** The share of the bar still filled, 0 to 1. */
    fun fraction(steps: Int, duration: Int): Float =
        if (duration <= 0) 0f else (steps.toFloat() / duration).coerceIn(0f, 1f)

    /** RepelDrawer.onRepelUsage: which of the DS tracker's three item icons to draw. */
    fun dsIcon(duration: Int): String = when {
        duration <= DEFAULT_DURATION -> "Repel"
        duration <= 200 -> "SuperRepel"
        else -> "MaxRepel"
    }

    /** A byte read from the repel variable, as steps: anything past a Max Repel is not one. */
    fun stepsOf(raw: Int): Int = if (raw in 1..MAX_STEPS) raw else 0
}
