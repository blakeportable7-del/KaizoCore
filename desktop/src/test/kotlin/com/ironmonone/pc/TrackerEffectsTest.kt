package com.ironmonone.pc

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tracker window's battle effects strip, drawn under a card at 2×, as the window draws it. */
class TrackerEffectsTest {
    private val k = 2
    private val font = TrackerWindow.cardFont()

    /** The strip drawn under a plain card of [ground]; saved beside the build for a look. */
    private var pages = 0

    private fun strip(ground: Color, e: TrackerWindow.Effects?, name: String, page: Int = 0): BufferedImage {
        val card = BufferedImage(TrackerWindow.CARD_W * k, TrackerWindow.CARD_H * k, BufferedImage.TYPE_INT_RGB)
        card.createGraphics().apply { color = ground; fillRect(0, 0, card.width, card.height); dispose() }
        val out = BufferedImage(card.width, (TrackerWindow.CARD_H + TrackerWindow.EFFECTS_H) * k, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.drawImage(card, 0, 0, null)
        pages = TrackerWindow.paintEffects(g, card, out.width, e, font, page)
        g.dispose()
        File("build/effects").mkdirs()
        ImageIO.write(out.getSubimage(0, card.height - 4 * k, out.width, out.height - card.height + 4 * k), "png", File("build/effects/$name.png"))
        return out
    }

    /** Pixels in the strip's [line] (0 first) that are not its ground. */
    private fun inked(img: BufferedImage, ground: Color, line: Int): Int {
        val top = TrackerWindow.CARD_H * k + (1 + line * 8) * k
        var n = 0
        for (y in top until minOf(img.height, top + 8 * k)) for (x in 0 until img.width) if (img.getRGB(x, y) != ground.rgb) n++
        return n
    }

    /** The wording HnsBattle gives these effects. */
    private val battle = TrackerWindow.Effects(
        you = listOf("Confused (1- 4 Turns)", "Leech Seed (JIGGLYPUFF)", "Substitute", "Reflect: 4 Turns Left", "Spikes: 2"),
        foe = listOf("Attract (PIKACHU)", "Confused (1- 4 Turns)", "Taunt: 2 Turns Left", "Future: 2 Turns Left (PIKACHU)", "Tailwind: 3 Turns Left"),
        field = listOf("Weather: Heavy Rain", "Trick Room: 5 Turns Left", "Electric Terrain: 2 Turns Left"),
    )

    private fun lines(e: TrackerWindow.Effects): List<TrackerWindow.EffectLine> {
        val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()
        g.font = font.deriveFont(7f * k)
        val fm = g.fontMetrics
        val width = (TrackerWindow.CARD_W * k - 6 * k - listOf("You: ", "Foe: ", "Field: ").maxOf { fm.stringWidth(it) }).toFloat()
        return TrackerWindow.effectLines(e, width) { fm.stringWidth(it).toFloat() }.also { g.dispose() }
    }

    @Test
    fun `a battle's effects are written row by row under the card`() {
        for ((name, ground) in listOf("dark" to Color(0x1E, 0x21, 0x27), "light" to Color(0xF2, 0xF0, 0xE8))) {
            val img = strip(ground, battle, "battle-$name")
            for (line in 0 until TrackerWindow.EFFECT_LINES) assertTrue(inked(img, ground, line) > 200, "$name: line $line has text")
            for (y in 0 until TrackerWindow.CARD_H * k) assertTrue(img.getRGB(5, y) == ground.rgb, "$name: the card is untouched at row $y")
        }
    }

    @Test
    fun `a crowded battle keeps every effect, two lines a page`() {
        // Blake, 2026-10-11: "shrink it, and just have the battle info scroll"; nothing is cut any more.
        val l = lines(battle)
        assertEquals(listOf("You", "Foe", "Field"), l.mapNotNull { it.label }, "$l")
        assertTrue(l.none { it.text.endsWith("…") }, "$l")
        val written = l.joinToString(" ") { it.text }
        for (eff in battle.you + battle.foe + battle.field) assertTrue(eff in written, "$eff in $l")
        val ground = Color(0x1E, 0x21, 0x27)
        strip(ground, battle, "crowded-1")
        assertEquals((l.size + 1) / 2, pages, "the page count")
        // Every page draws text on its first line, the last page included.
        for (p in 0 until pages) assertTrue(inked(strip(ground, battle, "crowded-p$p", p), ground, 0) > 200, "page $p")
    }

    @Test
    fun `a short battle shows each effect in full`() {
        val l = lines(TrackerWindow.Effects(listOf("Confused (1- 4 Turns)"), listOf("Attract (PIKACHU)"), emptyList()))
        assertEquals(listOf(TrackerWindow.EffectLine("You", "Confused (1- 4 Turns)"), TrackerWindow.EffectLine("Foe", "Attract (PIKACHU)")), l)
    }

    @Test
    fun `many effects turn as pages inside the two-line strip`() {
        val many = TrackerWindow.Effects(List(30) { "EFFECT NUMBER $it (5 turns)" }, emptyList(), emptyList())
        val ground = Color(0x1E, 0x21, 0x27)
        val img = strip(ground, many, "overflow")
        assertTrue(inked(img, ground, TrackerWindow.EFFECT_LINES - 1) > 200, "the second line is used")
        assertTrue(lines(many).last().text.endsWith("EFFECT NUMBER 29 (5 turns)"), "the last effect is kept")
        assertTrue(pages > 1)
        assertTrue(img.height == (TrackerWindow.CARD_H + TrackerWindow.EFFECTS_H) * k)
    }

    @Test
    fun `the strip's text is the card theme's text and gold, unless they would not read on its ground`() {
        val ground = Color(0x10, 0x20, 0x30)
        val was = Triple(com.ironmonone.app.Pc.Text, com.ironmonone.app.Pc.Gold, com.ironmonone.app.Pc.Dim)
        try {
            com.ironmonone.app.Pc.Text = androidx.compose.ui.graphics.Color(0xFF66FFCC)
            com.ironmonone.app.Pc.Gold = androidx.compose.ui.graphics.Color(0xFFFF66AA)
            val img = strip(ground, TrackerWindow.Effects(listOf("Substitute"), emptyList(), emptyList()), "themed")
            val seen = HashSet<Int>()
            for (y in TrackerWindow.CARD_H * k until img.height) for (x in 0 until img.width) seen += img.getRGB(x, y) and 0xFFFFFF
            assertTrue(0x66FFCC in seen, "the theme's text colour is drawn")
            assertTrue(0xFF66AA in seen, "the theme's gold names the row")
            assertEquals(Color.WHITE, TrackerWindow.themed(androidx.compose.ui.graphics.Color(0xFF111111), Color(0x10, 0x10, 0x10), Color.WHITE),
                "a theme colour lost on the ground gives way")
        } finally {
            com.ironmonone.app.Pc.Text = was.first; com.ironmonone.app.Pc.Gold = was.second; com.ironmonone.app.Pc.Dim = was.third
        }
    }

    @Test
    fun `out of battle and a quiet battle each say so`() {
        val ground = Color(0x1E, 0x21, 0x27)
        assertTrue(inked(strip(ground, null, "idle"), ground, 0) > 100, "the out-of-battle hint")
        assertTrue(inked(strip(ground, TrackerWindow.Effects(emptyList(), emptyList(), emptyList()), "quiet"), ground, 0) > 100, "no effects")
    }
}
