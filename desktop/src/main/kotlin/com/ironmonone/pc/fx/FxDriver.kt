package com.ironmonone.pc.fx

import androidx.compose.ui.graphics.toAwtImage
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackerState
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * The effects on the real tracker window, in developer mode (kaizocore-dev.txt beside the script; Blake, 2026-10-10: "I
 * want to run it tonight with animations in dev mode"). The sandbox plays a scenario through [FxLayer]; this plays the
 * game: each card the window is handed comes with the state it was drawn from, and the change since the last one is
 * what plays (FxEvents.between), the lasting effects are the game's own list (FxLasting.from the battle details), and the
 * shown Pokemon walks on the layer.
 *
 * The real hit chance and move power are the card's own numbers (PcReal, the "Show real hit chance" and "Show real move
 * power" options), so the layer's accuracy column stays off here.
 *
 * Swing's thread only.
 */
class FxDriver(font: Font, art: FxRomArt?, private val tracker: GbaTracker) {
    val layer = FxLayer(font, art)
    /** Whether the effects use this game's own art (its build's, FxRomArt), or the art drawn in code. */
    val romArt: Boolean = art?.ok == true
    private var prev: TrackerState? = null
    private var prevOwn = true
    private val items: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        for (i in 1 until 901) runCatching { tracker.itemName(i) }.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("?") }?.let { m.putIfAbsent(key(it), i) }
        m
    }

    private fun key(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    init {
        com.ironmonone.pcui.PcPictures.cardDrawsWalking = false
        layer.itemName = { runCatching { tracker.itemName(it) }.getOrDefault("Item $it") }
        layer.candyItem = { items[key("Rare Candy")] }
        layer.effortItem = { stat, d ->
            val name = when {
                d >= 10 -> mapOf("HP" to "HP Up", "ATK" to "Protein", "DEF" to "Iron", "SPA" to "Calcium", "SPD" to "Zinc", "SPE" to "Carbos")[stat]
                d > 0 -> mapOf("HP" to "Health Feather", "ATK" to "Muscle Feather", "DEF" to "Resist Feather", "SPA" to "Genius Feather", "SPD" to "Clever Feather", "SPE" to "Swift Feather")[stat]
                else -> mapOf("HP" to "Pomeg Berry", "ATK" to "Kelpsy Berry", "DEF" to "Qualot Berry", "SPA" to "Hondew Berry", "SPD" to "Grepa Berry", "SPE" to "Tamato Berry")[stat]
            }
            name?.let { items[key(it)] }
        }
        layer.badgeName = { i -> com.ironmonone.pcui.HNS_BADGES.getOrNull(i)?.first ?: "Badge ${i + 1}" }
        layer.leaderOf = { i -> com.ironmonone.pcui.HNS_BADGES.getOrNull(i)?.second?.substringBefore(",") }
        layer.badgeArt = { i, _ -> com.ironmonone.app.PcAssets.badge(com.ironmonone.app.pcDesktopCtx, com.ironmonone.app.PcAssets.HNS_BADGES, i + 1, true)?.let { b -> runCatching { b.toAwtImage() }.getOrNull() } }
    }

    /**
     * A new card: [st] the state it shows ([showOwn]: your Pokemon's side in a battle), [details] the battle's own list of
     * lasting effects (null out of battle), [onCard] false while a screen, the log or the run's end covers the card.
     */
    fun update(st: TrackerState?, showOwn: Boolean, details: GbaTracker.BattleDetails?, onCard: Boolean) {
        val now = System.nanoTime()
        layer.onCard = { onCard }
        if (st == null) { prev = null; layer.walker = { null }; return }
        val side = if (showOwn || !st.inBattle || st.enemy == null) FxEvent.Side.OWN else FxEvent.Side.FOE
        val lead = st.lead ?: st.party.firstOrNull()
        if (prev != null && prevOwn != (side == FxEvent.Side.OWN))
            layer.showing(side, if (side == FxEvent.Side.OWN) lead?.statusCondition.orEmpty() else st.enemy?.statusCondition.orEmpty(), now)
        layer.walker = {
            if (side == FxEvent.Side.OWN) lead?.let { m -> FxLayer.Walker(m.mon.species, true, if (m.mon.curHp <= 0) "FNT" else m.statusCondition, (m.statStages["EVA"] ?: 6) - 6, (m.statStages["ACC"] ?: 6) - 6) }
            else st.enemy?.let { e -> FxLayer.Walker(e.species, true, if (e.curHp <= 0) "FNT" else e.statusCondition, (e.statStages["EVA"] ?: 6) - 6, (e.statStages["ACC"] ?: 6) - 6) }
        }
        layer.rows = {
            val rows = if (side == FxEvent.Side.OWN) lead?.moveRows else st.enemy?.moveRows
            rows.orEmpty().map { it.name to (it.category == "STA") }
        }
        layer.accuracy = { null }
        layer.sync(if (st.inBattle) FxLasting.from(details) else emptyMap(), now)
        layer.play(FxEvents.between(prev, st), side, now)
        prev = st
        prevOwn = side == FxEvent.Side.OWN
    }

    /** The card [card] at its shake, then the effects over it, on [g] at [s] window pixels a card pixel. */
    fun paint(g: Graphics2D, card: BufferedImage, s: Double) {
        val now = System.nanoTime()
        val (ox, oy) = layer.cardOffset(now)
        g.drawImage(card, (ox * s).roundToInt(), (oy * s).roundToInt(), null)
        layer.draw(g, s, now, card)
    }

    /** True while something moves: the window runs its timer only then. */
    val active: Boolean get() = layer.active
}
