package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * rc32 audit P3 #51: the lookup caches are filled from the poll and the stream's dex walk on background threads and
 * from the panel on the main one. Plain HashMaps filled with getOrPut from two threads at once can lose entries or
 * corrupt the map; each is a concurrent map now.
 */
class GbaTrackerCachesTest {
    @Test
    fun `every lookup cache is safe to fill from two threads`() {
        val names = listOf("speciesNameCache", "moveNameCache", "baseStatsCache", "abilityNameCache", "itemNameCache", "moveDataCache", "learnsetCache")
        for (n in names) {
            val f = GbaTracker::class.java.getDeclaredField(n)
            assertEquals(java.util.concurrent.ConcurrentHashMap::class.java, f.type, n)
        }
    }
}
