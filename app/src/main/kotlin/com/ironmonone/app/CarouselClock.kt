package com.ironmonone.app

/**
 * When the tracker's carousel (PcCarousel) next needs the time. Only two of its rules depend on it: the item showing
 * has had its frames (the rotation), and TRAINERS has had its five seconds after a trainer battle. Everything else it
 * shows follows the game's state, which recomposes it by itself. A 100 ms ticker recomposed it ten times a second for
 * the whole session instead (rc32 audit P3 #45).
 */
internal object CarouselClock {
    /**
     * How long to sleep from [now] (ms) before the next rule can change: the rotation is due once more than [ms] has
     * passed since [shownSince] (the carousel's own test, `now - shownSince > ms`), and TRAINERS ends at [trainersUntil].
     * Null when nothing is due: a deadline already passed changes nothing by waking again, and the carousel looks at
     * it on its next draw.
     */
    fun wait(now: Long, shownSince: Long, ms: Long, trainersUntil: Long, rotation: Boolean): Long? = listOfNotNull(
        (shownSince + ms + 1 - now).takeIf { rotation && shownSince != 0L && it > 0 },
        (trainersUntil - now).takeIf { it > 0 },
    ).minOrNull()
}
