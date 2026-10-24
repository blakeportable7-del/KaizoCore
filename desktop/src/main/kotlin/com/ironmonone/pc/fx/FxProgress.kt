package com.ironmonone.pc.fx

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The card growing up, in battle or out (Blake, 2026-10-10: "a learned move animation when you learn a move or teach a
 * new move", "'New Move' pops up briefly", "if you find vitamins and use them the stat has a reaction, and rare candy
 * animation"). Each plays on your own card, inside the sprite slot and the row it is about, never across the words.
 */

private val GOLD = Color(255, 215, 90)
private val GOLD_EDGE = Color(70, 40, 0)

/** The moves box (MovesArea, x 5 to 145): a row slides inside it, not out over the card's edge. */
private val MOVES_BOX = Rect(5.0, 93.0, 140.0, 42.0)

/**
 * A new move in slot [slot]: the row slides in from the right, a band of gold light runs along it and its outline
 * glows, and "NEW MOVE!" pops in the sprite slot. [levelUp] learned at a level; otherwise taught (a TM, an HM, a tutor).
 */
class NewMove(start: Long, val slot: Int, val levelUp: Boolean, val font: Font, val art: FxRomArt?, val area: Rect = CardSpots.sprite,
              /** The card before the move was learned: its row shows until this move's turn comes (an empty row read as a bug). */
              val oldCard: BufferedImage? = null) : Fx(start, 1900) {
    override val usesSlot get() = true

    override fun drawWaiting(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val r = moveRow(slot)
        if (card != null) Px.cover(g, s, card, r, r.x + r.w - 1, r.y + 1)
        if (oldCard != null) Px.lift(g, s, oldCard, r)
    }

    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val r = moveRow(slot)
        val t = t(now)
        val before = g.clip
        g.clipRect((MOVES_BOX.x * s).roundToInt(), (MOVES_BOX.y * s).roundToInt(), (MOVES_BOX.w * s).roundToInt(), (MOVES_BOX.h * s).roundToInt())
        // The slide: the row's own pixels, from the right, over the box's ground.
        val slide = Ease.outCubic(min(1.0, t / 0.35))
        if (card != null && slide < 1.0) {
            Px.cover(g, s, card, r, r.x + r.w - 1, r.y + 1)
            Px.lift(g, s, card, r, dx = (1 - slide) * 40)
        }
        // The shine, once it has landed.
        if (t in 0.35..0.85) Px.band(g, s, r, r.x - 2 + (r.w + 4) * Ease.inOutSine((t - 0.35) / 0.5), Color(255, 240, 170, 120))
        // The outline, glowing and fading.
        val glow = Ease.window(t, 0.3, 0.75)
        if (glow > 0) Px.box(g, s, r, Px.alpha(GOLD, glow * (0.55 + 0.35 * sin(t * 18))))
        g.clip = before
        // "NEW MOVE!" in the sprite slot, with two glints.
        slotLabel(g, s, font, area, ((t - 0.05) / 0.85).coerceIn(0.0, 1.0), "NEW", "MOVE!", GOLD, GOLD_EDGE)
        val sp = art?.frames("sparkle", 32).orEmpty()
        if (sp.isNotEmpty()) for (i in 0 until 2) {
            val p = ((t - 0.15 - i * 0.2) / 0.4).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            Px.image(g, s, sp[(p * sp.size).toInt().coerceAtMost(sp.size - 1)], area.x + 2 + i * 18.0, area.y + 2 + i * 4.0, 0.35)
        }
    }
}

/**
 * Effort values moved outside battle: a vitamin (+10), a feather (+1) or an effort-lowering berry (minus). The item's
 * own icon (from the ROM) rises in the sprite slot with its name, then the stat's row reacts: a gold wash for a gain
 * (pale blue for a loss), the number popping, and little sparks rising inside the row.
 */
class EffortChange(start: Long, val stat: String, val delta: Int, val icon: BufferedImage?, val name: String, val font: Font, val art: FxRomArt?,
                   val area: Rect = CardSpots.sprite) : Fx(start, 1900) {
    override val usesSlot get() = true
    private val seeds = Seeds(stat.hashCode() + delta)
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        val up = delta > 0
        val c = if (up) GOLD else Color(150, 200, 255)
        // The item drops into the slot's top-right corner and pops, clear of the Pokemon's face; its name under it.
        val drop = Ease.outBack(min(1.0, t / 0.3))
        val pop = if (t < 0.3) 1.0 else 1 + 0.25 * sin(PI * min(1.0, (t - 0.3) / 0.25))
        val ik = 0.55 * pop
        if (icon != null) Px.image(g, s, icon, area.x + area.w - 1 - 24 * ik, area.y + 1 + (drop - 1) * 6, ik, Ease.window(t, 0.05, 0.8))
        if (t > 0.25) Px.text(g, s, font, name, area.cx, area.y + area.h - 4, 6.0, Px.alpha(Color.WHITE, Ease.window((t - 0.25) / 0.75, 0.1, 0.8)), maxW = area.w - 7)
        // The stat's row, once the item has gone in.
        val row = CardSpots.statRow(stat) ?: return
        val e = ((t - 0.35) / 0.65).coerceIn(0.0, 1.0)
        if (e <= 0.0) return
        g.color = Px.alpha(c, 0.35 * Ease.window(e, 0.1, 0.6))
        g.fillRect((row.x * s).roundToInt(), (row.y * s).roundToInt(), (row.w * s).roundToInt(), (row.h * s).roundToInt())
        val num = CardSpots.statNumber(stat)
        val k = if (e < 0.35) 1 + 0.15 * sin(PI * e / 0.35) else 1.0
        if (card != null && num != null && k > 1.001) Px.lift(g, s, card, num, k = k)
        // Sparks rising inside the row (up for a gain, falling for a loss).
        for (i in 0 until 5) {
            val p = ((e - seeds.r(i, 1) * 0.3) / 0.5).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            val x = row.x + 2 + seeds.r(i, 2) * (row.w - 4)
            val y = if (up) row.y + row.h - 1 - p * (row.h - 1) else row.y + p * (row.h - 1)
            Px.dot(g, s, x, y, Px.alpha(c, 1 - p), 1.0)
        }
    }
}

/**
 * A Rare Candy (or an Exp. Candy) outside battle: the candy pops in the sprite slot in a burst of sweet sparkles before
 * the level-up plays (the layer starts the LevelUp after it).
 */
class CandyPop(start: Long, val icon: BufferedImage?, val name: String, val font: Font, val art: FxRomArt?, val area: Rect = CardSpots.sprite) : Fx(start, 900) {
    override val usesSlot get() = true
    private val seeds = Seeds(name.hashCode())
    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        val k = 0.6 * Ease.outBack(min(1.0, t / 0.3)) * (if (t > 0.75) 1 - (t - 0.75) / 0.25 * 0.5 else 1.0)
        val a = Ease.window(t, 0.02, 0.8)
        if (icon != null && k > 0.01) Px.image(g, s, icon, area.cx - 12 * k, area.cy - 12 * k, k, a)
        if (t > 0.2) Px.text(g, s, font, name, area.cx, area.y + area.h - 4, 6.0, Px.alpha(Color.WHITE, Ease.window((t - 0.2) / 0.8, 0.1, 0.8)), maxW = area.w - 7)
        // The sparkle burst, round the candy, inside the slot.
        val stars = art?.frames("gold_stars", 16).orEmpty()
        val p = ((t - 0.2) / 0.6).coerceIn(0.0, 1.0)
        if (p <= 0.0 || p >= 1.0) return
        for (i in 0 until 6) {
            val ang = i * PI / 3 + seeds.r(i, 1) * 0.5
            val rad = 4 + Ease.outCubic(p) * 12
            val x = area.cx + kotlin.math.cos(ang) * rad; val y = area.cy + sin(ang) * rad * 0.8
            if (x > 35 || y > 32) continue
            if (stars.isNotEmpty()) Px.image(g, s, stars[i % stars.size], x - 3, y - 3, 0.4, 1 - p) else Px.dot(g, s, x, y, Px.alpha(GOLD, 1 - p), 1.2)
        }
    }
}
