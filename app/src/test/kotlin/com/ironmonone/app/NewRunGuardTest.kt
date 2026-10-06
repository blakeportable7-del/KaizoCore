package com.ironmonone.app

import java.io.File
import kotlinx.coroutines.launch
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P0-4: NEW RUN had no in-flight guard, so a second confirm while a run was being made ran a second
 * install. Play's newRun now claims NewRunGuard right before it launches and gives it back on the job's completion,
 * which also fires for a job cancelled before it started.
 */
class NewRunGuardTest {
    @AfterTest fun free() = NewRunGuard.release()

    @Test
    fun `one new run at a time, and the next may start once it ends`() {
        assertTrue(NewRunGuard.claim())
        assertTrue(NewRunGuard.inProgress)
        assertFalse(NewRunGuard.claim(), "a second confirm while the first is being made")
        NewRunGuard.release()
        assertTrue(NewRunGuard.claim(), "after it ended")
    }

    @Test
    fun `only one of many simultaneous confirms gets through`() {
        val wins = java.util.concurrent.atomic.AtomicInteger()
        val start = java.util.concurrent.CountDownLatch(1)
        val threads = List(16) { Thread { start.await(); if (NewRunGuard.claim()) wins.incrementAndGet() }.apply { start() } }
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, wins.get())
    }

    @Test
    fun `a job cancelled before it starts still gives the claim back`() = kotlinx.coroutines.runBlocking {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job())
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()      // the screen already gone
        assertTrue(NewRunGuard.claim())
        var ran = false
        scope.launch { ran = true }.invokeOnCompletion { NewRunGuard.release() }
        kotlinx.coroutines.delay(50)
        assertFalse(ran, "the body never ran")
        assertFalse(NewRunGuard.inProgress, "but the claim was given back")
    }

    @Test
    fun `Play's newRun claims before it launches and releases on completion`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        val body = play.substring(play.indexOf("    fun newRun(move: Boolean = false) {"), play.indexOf("    // Three save slots."))
        val claim = body.indexOf("if (!NewRunGuard.claim()) { status = NewRunGuard.BUSY; return }")
        val launch = body.indexOf("scope.launch {")
        assertTrue(claim in 0 until launch, "the claim comes right before the launch")
        assertTrue("}.invokeOnCompletion { NewRunGuard.release() }" in body)
        assertEquals(1, Regex("scope\\.launch").findAll(body).count(), "one job, so one completion releases it")
    }

    @Test
    fun `every way to start a new run takes the one guard`() {
        // rc33 audit P1 #22: the Run tab, Build your own, a run code and a randomized Nuzlocke checked only RunJob's own
        // flag, so one could start while Play's NEW RUN was making a run.
        fun src(name: String) = java.io.File("src/main/kotlin/com/ironmonone/app/$name").readText()
        val job = src("RunJob.kt")
        kotlin.test.assertTrue("if (busy || !NewRunGuard.claim()) return false" in job)
        kotlin.test.assertTrue("} finally {\n                NewRunGuard.release()" in job.replace("\r\n", "\n"), "given back however the job ends")
        kotlin.test.assertTrue("store.nextRun.makeAndClaim(recipe," in src("RunStart.kt"))
        kotlin.test.assertTrue("if (!RunJob.randomize(context, rom, s, seed = null)) RunJob.say(NewRunGuard.BUSY, true)" in src("RunScreen.kt"))
        kotlin.test.assertTrue("if (!RunJob.randomize(context, rom, file, seed = null)) { startedHere = false; RunJob.say(NewRunGuard.BUSY, true) }" in src("BuildYourGame.kt"))
    }

    @Test
    fun `a Play screen opened while a run is being made waits for it, and the run's condition is set either way`() {
        // rc33 audit P1 #22: Play's NEW RUN goes on after its screen is left; coming back booted the run being replaced.
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertTrue("var gameActive by remember { mutableStateOf(!NewRunGuard.inProgress) }" in play)
        kotlin.test.assertTrue("status = NewRunGuard.BUSY; NewRunGuard.awaitDone(); gameKeyForRom++; gameActive = true" in play)
        val job = play.substringAfter("if (!NewRunGuard.claim()) { status = NewRunGuard.BUSY; return }").substringBefore("ok.onSuccess")
        kotlin.test.assertTrue("TrackerOptions.startRunWith(settings.name)" in job, "inside the install, which runs to its end even after the screen is left")
    }
}
