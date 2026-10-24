package com.ironmonone.pcui

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The game over card in the panel (PLAN row 6): drawn from the latched ending, its buttons answering clicks at besteon's rows. */
class PcGameOverCardTest {
    private val files = File(System.getProperty("java.io.tmpdir"), "pcui-over-${System.nanoTime()}")

    private fun over(won: Boolean = false, canRetry: Boolean = true, confirm: Boolean = false, save: PcSaveStatus = PcSaveStatus.NOT_CLICKED) =
        PcPanel.Input(Samples.over, 740, over = PcOver(won = won, team = Samples.over.party, quote = "What a turn of events! Who would have thought it would end this way?", canRetry = canRetry, retryConfirm = confirm, save = save))

    @Test
    fun `the card's buttons answer clicks at the reference's rows, and the win card offers no Retry`() {
        try {
            val got = ArrayList<PcOverAction>()
            PcPanel.Renderer(files, PcPanel.Input(null, 0)).use { r ->
                r.onOver = { got += it }
                r.render(over())
                r.render(over())
                // besteon's rows: Continue playing at y 65 to 81, Retry at 83 to 99, Save at 101 to 117, Inspect at 137 to 153; the icon at the top right.
                r.click(60, 73); r.render(over())
                r.click(60, 91); r.render(over(confirm = true))
                r.click(60, 109); r.render(over(save = PcSaveStatus.SUCCESS))
                r.click(60, 145); r.render(over())
                r.click(125, 18); r.render(over())
                // Grade my notes, at y 119 to 135.
                r.click(60, 127); r.render(over())
                assertEquals(listOf(PcOverAction.CONTINUE, PcOverAction.RETRY, PcOverAction.SAVE, PcOverAction.INSPECT_LOG, PcOverAction.CYCLE_TEAM, PcOverAction.GRADE), got)
                // A saved attempt's button is done: no second save.
                got.clear()
                r.render(over(save = PcSaveStatus.SUCCESS)); r.click(60, 109); r.render(over(save = PcSaveStatus.SUCCESS))
                assertEquals(emptyList(), got)
                // Won: no Retry, its row stays empty; Save and Inspect keep their rows.
                got.clear()
                r.render(over(won = true)); r.render(over(won = true))
                r.click(60, 91); r.render(over(won = true))
                r.click(60, 109); r.render(over(won = true))
                r.click(60, 145); r.render(over(won = true))
                assertEquals(listOf(PcOverAction.SAVE, PcOverAction.INSPECT_LOG), got)
                val img = ImageIO.read(r.render(over(won = true)).inputStream())
                assertEquals(150 to 160, img.width to img.height)
            }
        } finally { files.deleteRecursively() }
    }

    @Test
    fun `the quote wraps at thirty letters on whole words`() {
        assertEquals(listOf("What a turn of events! Who", "would have thought it would", "end this way?"),
            wrapWords("What a turn of events! Who would have thought it would end this way?", 30))
        assertEquals(listOf("CONGRATULATIONS!!"), wrapWords("CONGRATULATIONS!!", 30))
        assertTrue(wrapWords("", 30).isEmpty())
    }
}
