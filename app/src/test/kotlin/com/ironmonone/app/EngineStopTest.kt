package com.ironmonone.app

import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The engines' shared random source (RandomSource, a local modification in
 * both vendored engines): a stage nobody will take is stopped by interrupting
 * its thread (NextRunJob), and that must not change what a seed makes.
 */
class EngineStopTest {
    private val zx = com.dabomstew.pkrandomzx.RandomSource::class.java
    private val natdex = com.dabomstew.pkrandom.RandomSource::class.java

    @Test
    fun `a seed makes the numbers java's Random makes for it, in both engines`() {
        val want = java.util.Random(42L).let { r -> List(12) { listOf(r.nextInt(1000), r.nextDouble(), r.nextBoolean(), r.nextLong()) } }
        com.dabomstew.pkrandomzx.RandomSource.seed(42L)
        val z = com.dabomstew.pkrandomzx.RandomSource.instance()
        assertEquals(want, List(12) { listOf(z.nextInt(1000), z.nextDouble(), z.nextBoolean(), z.nextLong()) }, zx.name)
        com.dabomstew.pkrandom.RandomSource.seed(42L)
        val n = com.dabomstew.pkrandom.RandomSource.instance()
        assertEquals(want, List(12) { listOf(n.nextInt(1000), n.nextDouble(), n.nextBoolean(), n.nextLong()) }, natdex.name)
    }

    @Test
    fun `a randomize on an interrupted thread stops at its next draw, and only there`() {
        com.dabomstew.pkrandomzx.RandomSource.seed(1L)
        com.dabomstew.pkrandom.RandomSource.seed(1L)
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<CancellationException> { com.dabomstew.pkrandomzx.RandomSource.nextInt(10) }
            assertFailsWith<CancellationException> { com.dabomstew.pkrandomzx.RandomSource.instance().nextDouble() }
            assertFailsWith<CancellationException> { com.dabomstew.pkrandom.RandomSource.nextInt(10) }
            assertFailsWith<CancellationException> { com.dabomstew.pkrandom.RandomSource.cosmeticInstance().nextInt(10) }
        } finally {
            Thread.interrupted()
        }
        // An uninterrupted thread, the foreground randomize's, draws as ever.
        com.dabomstew.pkrandomzx.RandomSource.nextInt(10)
        com.dabomstew.pkrandom.RandomSource.nextInt(10)
    }
}
