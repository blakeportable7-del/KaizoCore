package com.ironmonone.pcui

import com.ironmonone.app.TrackerOptions

/**
 * What the bottom strip (TrackerScreen's carousel, the box at 5, 136) shows now: one item and its words. A data class,
 * so the panel draws again exactly when the item turns or its words change (PanelOut compares the input).
 */
data class PcStrip(
    val key: Key,
    /** The item's line: the last attack, the route's count, the trainers, the battle detail. */
    val text: String = "",
    /** The last attack would knock your Pokemon out: the sword turns red. */
    val lethal: Boolean = false,
    /** The pedometer's steps since its reset, and its goal (0 = none). */
    val steps: Int = 0,
    val goal: Int = 0,
    /** The pedometer's right button: "Reset", or "Total" when nothing is counted yet. */
    val resetLabel: String = "",
) {
    enum class Key { BADGES, TRAINERS, LAST_ATTACK, ROUTE, NOTES, BATTLE_DETAILS, PEDOMETER, NONE }
}

/**
 * TrackerScreen.getCurrentCarouselItem (Ironmon-Tracker, MIT) as the phone's PcCarousel has it, item for item and in its
 * order (BADGES, TRAINERS, LAST_ATTACK, ROUTE_INFO, NOTES, BATTLE_DETAILS, PEDOMETER): each shows only while its Setup
 * toggle is on and its own condition holds, the current one stays its frames (210, 420, 180 at 60 a second, times the
 * carousel speed; TRAINERS is locked) and then the next that can show takes over. Kept by the session for the run and
 * asked on its own clock, so the strip turns while nothing else on the panel changes (until 2026-10-07 the PC strip
 * never turned: badges out of battle, the last attack only on your own card).
 */
class PcCarousel {
    /** Everything the items' conditions read, from one tracker read. */
    data class Facts(
        val inBattle: Boolean,
        /** Battle.isViewingOwn: true outside battle, and in battle when your side is on the panel. */
        val viewingOwn: Boolean,
        val isWild: Boolean,
        val leadLevel: Int,
        /** The route line's area ("Walking", or the battle's area) and its counts; null area when the route has none. */
        val routeArea: String?,
        val routeSeen: Int,
        val routeTotal: Int,
        /** The map's trainers (RouteData.hasRouteTrainers) and the defeated count against the area's total. */
        val routeTrainers: Int,
        val trainersDefeated: Int,
        val trainersTotal: Int,
        /** "Wing Attack: 23 damage", or null while there is none to show. */
        val lastAttack: String?,
        val lethal: Boolean,
        /** BattleSummary.line for the viewed battler, or null. */
        val battleDetail: String?,
        val totalSteps: Int,
        val pedometerAllowed: Boolean,
    )

    private var index = 0
    private var shownSince = 0L
    /** The mouse is on the strip: the item shown stays (Blake, 2026-10-11), so a click opens what it shows. */
    @Volatile var paused = false
    private var trainersUntil = 0L
    private var wasTrainerBattle = false
    private var wasInBattle = false

    /** Back to the first item, as a new game starts. */
    fun reset() { index = 0; shownSince = 0L; trainersUntil = 0L; wasTrainerBattle = false; wasInBattle = false }

    /** The item for [now] (milliseconds, the session's clock) and [f]. */
    fun pick(now: Long, f: Facts): PcStrip {
        // Battle.lua:836: a trainer battle ending shows TRAINERS for 300 frames (5 s).
        if (f.inBattle && !wasInBattle) wasTrainerBattle = !f.isWild
        if (!f.inBattle && wasInBattle && wasTrainerBattle) {
            wasTrainerBattle = false
            trainersUntil = now + 5000
            if (TrackerOptions.carouselShows("Trainers")) { index = 1; shownSince = now }
        }
        wasInBattle = f.inBattle
        val earlyRoute = (!f.inBattle || f.isWild) && f.leadLevel in 1..12 && f.routeTotal > 0 && f.routeArea != null
        // Walking on a route with wild Pokemon, at any level: "Walking: seen/total" takes its turn with the badges (Blake,
        // 2026-10-11: "as i am walking the tracker doesn't say walking seen/total, it pops up when i enter a battle").
        // The reference shows it out of battle only while the lead is level 12 or under, which a Kaizo run passes at once.
        val walkingRoute = !f.inBattle && f.routeTotal > 0 && f.routeArea != null
        val pedometerShows = TrackerOptions.carouselShows("Pedometer") && f.viewingOwn && !f.inBattle &&
            TrackerOptions.displayPedometer && f.pedometerAllowed
        val items = listOf(
            PcStrip.Key.BADGES to (210 to (TrackerOptions.carouselShows("Badges") && f.viewingOwn && !earlyRoute &&
                (TrackerOptions.allowCarouselRotation || !pedometerShows))),
            // Five seconds after a trainer battle (Battle.lua:836), and also its turn whenever you walk a map with trainers
            // (Blake, 2026-10-11: "tracker isn't showing trainers battled either").
            PcStrip.Key.TRAINERS to (420 to (TrackerOptions.carouselShows("Trainers") && !f.inBattle && f.routeTrainers > 0)),
            PcStrip.Key.LAST_ATTACK to (180 to (TrackerOptions.carouselShows("LastAttack") && f.inBattle && !f.lastAttack.isNullOrBlank())),
            PcStrip.Key.ROUTE to (180 to (TrackerOptions.carouselShows("RouteInfo") &&
                (earlyRoute || walkingRoute || (f.inBattle && f.isWild && f.routeArea != null && f.routeTotal > 0)))),
            PcStrip.Key.NOTES to (180 to (TrackerOptions.carouselShows("Notes") && !f.viewingOwn)),
            PcStrip.Key.BATTLE_DETAILS to (180 to (TrackerOptions.carouselShows("BattleDetails") && f.inBattle && f.battleDetail != null)),
            PcStrip.Key.PEDOMETER to (210 to pedometerShows),
        )
        fun rotate() {
            for (step in 1..items.size) {
                val i = (index + step) % items.size
                if (items[i].second.second) { index = i; break }
            }
            shownSince = now
        }
        val speed = mapOf("1/2" to 2.0, "1" to 1.0, "2" to 0.5, "3" to 1.0 / 3, "4" to 0.25)[TrackerOptions.carouselSpeed] ?: 1.0
        val cur = items[index]
        val ms = (cur.second.first * 1000L / 60 * (if (cur.first == PcStrip.Key.TRAINERS) 1.0 else speed)).toLong()
        if (!cur.second.second) { if (items.any { it.second.second }) rotate() }
        else if (paused) shownSince = now
        else if (TrackerOptions.allowCarouselRotation && shownSince != 0L && now - shownSince > ms) rotate()
        if (shownSince == 0L) shownSince = now
        val shown = items[index]
        if (!shown.second.second) return PcStrip(PcStrip.Key.NONE)
        return when (shown.first) {
            PcStrip.Key.TRAINERS -> PcStrip(shown.first, "Trainers defeated: ${f.trainersDefeated}/${if (f.trainersTotal > 0) f.trainersTotal else f.routeTrainers}")
            PcStrip.Key.LAST_ATTACK -> PcStrip(shown.first, f.lastAttack.orEmpty(), lethal = f.lethal)
            PcStrip.Key.ROUTE -> PcStrip(shown.first, "${f.routeArea ?: "Walking"}: " +
                (if (f.routeSeen > f.routeTotal) "${f.routeSeen}" else "${f.routeSeen}/${f.routeTotal}") + " Seen Pokémon")
            PcStrip.Key.BATTLE_DETAILS -> PcStrip(shown.first, f.battleDetail.orEmpty())
            PcStrip.Key.PEDOMETER -> {
                val steps = PcPedometer.current(f.totalSteps).coerceAtMost(999999)
                PcStrip(shown.first, steps = steps, goal = PcPedometer.goalSteps, resetLabel = PcPedometer.buttonLabel(f.totalSteps))
            }
            else -> PcStrip(shown.first)
        }
    }
}

/** The phone's Pedometer (Program.Pedometer), reached from the desktop module, which cannot see the app's internal object. */
object PcPedometer {
    fun current(total: Int): Int = com.ironmonone.app.Pedometer.current(total)
    fun buttonLabel(total: Int): String = com.ironmonone.app.Pedometer.buttonLabel(total)
    fun press(total: Int) = com.ironmonone.app.Pedometer.press(total)
    val goalSteps: Int get() = com.ironmonone.app.Pedometer.goalSteps
    /** PedometerGoal's window: the goal in steps, 0 for none. */
    fun setGoal(steps: Int) { com.ironmonone.app.Pedometer.goalSteps = steps.coerceIn(0, 999_999) }
}

/** BattleSummary.line (the viewed battler's first battle detail), reached from the desktop module. */
object PcBattleLine {
    fun line(summaries: List<String>, viewingOwn: Boolean): String? = com.ironmonone.app.BattleSummary.line(summaries, viewingOwn)
}

