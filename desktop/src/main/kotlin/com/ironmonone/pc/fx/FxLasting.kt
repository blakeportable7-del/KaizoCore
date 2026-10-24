package com.ironmonone.pc.fx

import com.ironmonone.tracker.GbaTracker
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lasting battle effects, for exactly as long as the game keeps them (Blake, 2026-10-10: "can the status effects continue
 * until they are gone or cured? ... and like leech seed or spikes"). The card does not remember them from events: each
 * read, the game's own list (GbaTracker.battleDetails: the field, each side, each battler) says what is on, and the layer
 * starts what is new, keeps what stays (a count changing does not restart it) and stops what the game dropped: cured,
 * worn off, switched out, or the battle over. Each side keeps its own, so flipping the card does not lose them.
 *
 * [name] is the effect's canonical name, [count] its turns left or layers, [move] the move it holds (Disable, Encore).
 *
 * Only what the Kaizo pool can bring has a look (Blake, 2026-10-10: "same rules as natl dex kaizo emerald"; read off
 * HnsUprRules' Nat. Dex pool that day). Toxic Spikes, Stealth Rock, Sticky Web, Aurora Veil, Tailwind, Lucky Chant,
 * Heal Block, Throat Chop, Embargo, Gravity, Trick Room, the terrains, Aqua Ring, Magnet Rise and Laser Focus are never
 * rolled there, so they have none.
 */
data class LastingSpec(val name: String, val count: Int? = null, val move: String? = null)

object FxLasting {
    /** On a side of the field, not on one Pokemon: they stay when the Pokemon switches. */
    val SIDE = setOf("Spikes", "Reflect", "Light Screen", "Safeguard", "Mist")

    /** On the whole field: both cards show them. */
    val FIELD = setOf("Rain", "Hail", "Sun", "Sandstorm")

    /** The battle-details lines with a move id, by that id (the reference's BattleDetailsScreen names them after the move). */
    private val BY_MOVE = mapOf(
        73 to "Leech Seed", 191 to "Spikes", 115 to "Reflect", 113 to "Light Screen", 219 to "Safeguard", 54 to "Mist",
        213 to "Infatuated", 116 to "Focus Energy", 164 to "Substitute", 194 to "Destiny Bond", 171 to "Nightmare",
        174 to "Curse", 193 to "Foresight", 259 to "Torment", 107 to "Minimize", 268 to "Charge", 275 to "Ingrain",
        281 to "Drowsy", 50 to "Disable", 227 to "Encore", 254 to "Stockpile", 195 to "Perish Song", 269 to "Taunt",
        199 to "Lock-On", 273 to "Wish", 99 to "Rage", 117 to "Bide", 253 to "Uproar",
    )

    /** The lines without a move id, by how they start (HnsBattle.details' own words). */
    private val BY_TEXT = listOf(
        "Confused" to "Confused", "Trapped" to "Trapped", "Future" to "Future Sight", "Recharging" to "Recharging",
        "Must Attack" to "Must Attack",
    )

    private fun spec(d: GbaTracker.BattleDetail): LastingSpec? {
        val name = BY_MOVE[d.moveId] ?: BY_TEXT.firstOrNull { d.text.startsWith(it.first, ignoreCase = true) }?.second ?: return null
        val count = Regex("""(\d+)""").find(d.text.substringAfter(":", ""))?.groupValues?.get(1)?.toIntOrNull()
        val move = if (name == "Disable" || name == "Encore") d.text.substringAfter("(", "").substringBefore(")").ifBlank { null } else null
        return LastingSpec(name, count, move)
    }

    private fun weather(w: String): String? = when {
        w.startsWith("Rain", true) -> "Rain"; w.startsWith("Sand", true) -> "Sandstorm"; w.startsWith("Sun", true) -> "Sun"
        w.startsWith("Hail", true) -> "Hail"; else -> null
    }

    /**
     * What the game lists for your lead ([ownBattler]) and the opponent shown ([foeBattler]): each side's own effects,
     * its Pokemon's, and the field's on both. Null details (outside a battle) is nothing on either side.
     */
    fun from(details: GbaTracker.BattleDetails?, ownBattler: Int = 0, foeBattler: Int = 1): Map<FxEvent.Side, List<LastingSpec>> {
        if (details == null) return emptyMap()
        val field = details.field.mapNotNull(::spec) + listOfNotNull(weather(details.weather)?.let { LastingSpec(it) })
        fun side(i: Int, battler: Int) = (details.sides.getOrNull(i).orEmpty().mapNotNull(::spec) +
            details.mons.getOrNull(battler).orEmpty().mapNotNull(::spec) + field).distinctBy { it.name }
        return mapOf(FxEvent.Side.OWN to side(0, ownBattler), FxEvent.Side.FOE to side(1, foeBattler))
    }
}

/** The sprite slot's zones, so effects that last share the slot without piling up: feet, body, head and its two edges. */
private object Zones {
    val slot = CardSpots.sprite
    /** The ground under the sprite: hazards, roots, terrain. */
    val ground = Rect(6.0, 27.0, 29.0, 6.0)
    /** Over the head: confusion's stars, Yawn, Future Sight's orb, Wish's star, Lock-On, Stockpile. */
    val head = Rect(6.0, 6.0, 29.0, 8.0)
}

private inline fun inSlot(g: Graphics2D, s: Double, draw: () -> Unit) {
    val before = g.clip
    g.clipRect((6 * s).roundToInt(), (6 * s).roundToInt(), (30 * s).roundToInt(), (27 * s).roundToInt())
    draw()
    g.clip = before
}

/**
 * One lasting effect on the shown card while the game keeps it. Disable, Taunt and Encore mark the move rows; the
 * weather falls over the card; the rest live in the sprite slot, each in its own zone. Pixel by pixel on the card's own
 * grid, in the game's own sprites where the ROM has one.
 */
class Lasting(start: Long, spec: LastingSpec, val art: FxRomArt?, val font: Font, val rows: () -> List<Pair<String, Boolean>>) :
    Fx(start, 2000, loops = true) {
    /** Updated in place when the game's count changes, so the look does not restart. */
    var spec: LastingSpec = spec
    /** A moment has the sprite slot: the looks over the body and head step back (the ground and the edges stay). */
    var quiet = false
    /** This look's turn over the head (FxLayer turns them every 2 s, one at a time). */
    var headTurn = true

    companion object {
        /** The looks that live over the head: one at a time (design audit: a nail, a Z and an orb piled up there). */
        val HEAD = setOf("Perish Song", "Drowsy", "Future Sight", "Wish", "Lock-On", "Stockpile", "Nightmare", "Curse", "Confused", "Infatuated")
        /** The looks over the body: they step back while a moment has the slot. */
        val BODY = setOf("Trapped", "Charge", "Destiny Bond", "Focus Energy")
    }
    val name: String get() = spec.name
    private val seeds = Seeds(spec.name.hashCode())
    private val aura: StatusAura? = when (spec.name) { "Confused" -> StatusAura(start, "CONFUSE", art); "Infatuated" -> StatusAura(start, "ATTRACT", art); else -> null }

    override fun spriteMotion(now: Long): Triple<Double, Double, Double>? {
        if (!liveSprite) return null
        val sec = seconds(now)
        return when (name) {
            // Yawn: the sprite sways, heavy-eyed.
            "Drowsy" -> Triple(sin(sec * 1.6) * 0.8, 0.0, 1.0)
            else -> null
        }
    }

    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val sec = seconds(now)
        val area = Zones.slot
        if (name in HEAD && (quiet || !headTurn)) return
        if (name in BODY && quiet) return
        aura?.let { it.liveSprite = liveSprite; it.draw(g, s, now, card); return }
        when (name) {
            // The move locks (Disable, Encore, Taunt, Heal Block ...) are drawn by FxLayer.drawLocks, all together.
            "Disable", "Encore", "Taunt", "Recharging", "Torment", "Must Attack" -> Unit
            // The weather falls inside the sprite slot, a window on the field: over the whole card it drifted across every
            // word on it, the one look that broke the no-crossing rule (design audit).
            "Rain", "Hail" -> inSlot(g, s) {
                val f = art?.frames(if (name == "Rain") "rain" else "hail", 16).orEmpty()
                for (i in 0 until 5) {
                    val speed = if (name == "Rain") 1.4 else 0.7
                    val age = ((sec * speed + seeds.r(i, 1)) % 1.0)
                    val x = Zones.slot.x + seeds.r(i, 2) * Zones.slot.w + age * (if (name == "Rain") -4 else 2); val y = Zones.slot.y - 6 + age * (Zones.slot.h + 6)
                    if (f.isNotEmpty()) Px.image(g, s, f[i % f.size], x, y, 0.4, 0.6) else Px.dot(g, s, x, y, Color(160, 200, 255, 160), 1.0)
                }
            }
            "Sun" -> inSlot(g, s) {
                Px.disc(g, s, Zones.slot.x + Zones.slot.w - 3, Zones.slot.y + 2, 9.0, 8.0, Color(255, 205, 90, 70))
                for (i in 0 until 4) {
                    val age = ((sec * 0.3 + seeds.r(i, 1)) % 1.0)
                    Px.dot(g, s, Zones.slot.x + 4 + seeds.r(i, 2) * 24 - age * 4, Zones.slot.y + age * Zones.slot.h, Px.alpha(Color(255, 230, 150), 0.6 * Ease.window(age)), 1.0)
                }
            }
            "Sandstorm" -> inSlot(g, s) {
                val f = art?.parts("mud_sand").orEmpty()
                for (i in 0 until 6) {
                    val age = ((sec * 0.9 + seeds.r(i, 1)) % 1.0)
                    val x = Zones.slot.x - 4 + age * (Zones.slot.w + 8); val y = Zones.slot.y + seeds.r(i, 2) * Zones.slot.h + sin(sec * 3 + i) * 1.5
                    if (f.isNotEmpty()) Px.image(g, s, f[0], x, y, 0.4, 0.6) else Px.dot(g, s, x, y, Color(210, 180, 120, 160), 1.0)
                }
            }
            else -> inSlot(g, s) { slotLook(g, s, sec, area) }
        }
    }

    /** The looks that live in the sprite slot, each in its zone. */
    private fun slotLook(g: Graphics2D, s: Double, sec: Double, area: Rect) {
        val n = spec.count
        when (name) {
            "Leech Seed" -> {
                val f = art?.frames("leech_seed", 16).orEmpty()
                for (i in 0 until 3) {
                    val x = area.x + 6 + i * 8.0; val y = area.y + area.h - 10 + sin(sec * 4 + i) * 1.0
                    if (f.isNotEmpty()) Px.image(g, s, f[((sec * 4).toInt() + i) % f.size], x, y, 0.5) else Px.dot(g, s, x, y, Color(110, 220, 90), 2.0)
                }
                // The drain: green motes leaving the sprite each beat.
                val beat = (sec % 1.2) / 1.2
                for (i in 0 until 5) Px.dot(g, s, area.cx + (seeds.r(i, 1) - 0.5) * 16, area.cy - beat * 16 - i, Px.alpha(Color(140, 255, 120), 1 - beat), 1.0)
            }
            "Perish Song" -> {
                val f = art?.frames("music_notes", 16).orEmpty()
                for (i in 0 until 3) {
                    val age = ((sec / 1.6 + i / 3.0) % 1.0)
                    val x = area.x + 4 + i * 9.0 + sin(age * 8) * 2; val y = area.y + area.h - age * (area.h + 6)
                    if (f.isNotEmpty()) Px.image(g, s, f[i % f.size], x, y, 0.5, Ease.window(age, 0.1, 0.7))
                }
                // The count in the slot's head corner, not out over the name.
                n?.let { Px.text(g, s, font, "$it", Zones.head.x + Zones.head.w - 3, Zones.head.cy + 1, 9 + sin(sec * 4), Color(230, 120, 255), maxW = 8.0) }
            }
            "Spikes" -> {
                // One spike a layer, side by side on the ground under the sprite.
                val f = art?.parts("spikes")?.firstOrNull() ?: return
                for (i in 0 until (n ?: 1).coerceIn(1, 3)) Px.image(g, s, f, Zones.ground.x + 3 + i * 8.0, Zones.ground.y - 2, 0.45)
            }
            "Trapped" -> {
                // Bands round the sprite, turning: an ellipse of pixels, on the card's grid.
                for (k in 0 until 2) for (i in 0 until 18) {
                    val a = i * 2 * PI / 18 + sec * (if (k == 0) 2.0 else -1.6)
                    val x = area.cx + cos(a) * 12; val y = area.cy + 4 * k - 2 + sin(a) * 3
                    if (sin(a) > -0.2) Px.dot(g, s, x, y, Px.alpha(Color(230, 170, 90), 0.85), 1.0)
                }
            }
            "Curse" -> {
                // The nail in the head corner; the ghostly spirit over the face smeared the Pokemon (design audit).
                val nail = art?.frames("nail", 32).orEmpty()
                if (nail.isNotEmpty()) Px.image(g, s, nail[0], Zones.head.x + Zones.head.w - 10, Zones.head.y, 0.28)
            }
            "Nightmare" -> {
                val smoke = art?.frames("black_smoke", 32, 16).orEmpty()
                if (smoke.isNotEmpty()) Px.image(g, s, smoke[0], area.cx - 9 + sin(sec * 1.5) * 1.5, Zones.head.y, 0.55, 0.7)
            }
            "Ingrain" -> {
                val roots = art?.frames("roots", 32).orEmpty()
                if (roots.isNotEmpty()) Px.image(g, s, roots[((sec * 1.5).toInt()) % roots.size], area.cx - 8, Zones.ground.y - 6, 0.5, 0.85)
            }
            "Focus Energy" -> {
                val f = art?.frames("focus_energy", 16).orEmpty()
                for (i in 0 until 2) {
                    val age = ((sec * 0.9 + i * 0.5) % 1.0)
                    if (f.isNotEmpty()) Px.image(g, s, f[(age * f.size).toInt().coerceAtMost(f.size - 1)], area.x + 2 + i * 20.0, area.y + area.h - 14 - age * 6, 0.45, 0.5 * Ease.window(age))
                }
            }
            "Drowsy" -> {
                val z = art?.frames("letter_z", 32).orEmpty()
                val age = (sec % 1.6) / 1.6
                if (z.isNotEmpty()) Px.image(g, s, z[0], Zones.head.x + Zones.head.w - 9 + age * 2, Zones.head.y + 4 - age * 3, 0.22, Ease.window(age))
            }
            "Reflect", "Light Screen", "Safeguard", "Mist" -> pane(g, s, sec)
            "Stockpile" -> {
                // One orb a stockpile, over the head.
                val orb = art?.frames("orb", 16).orEmpty()
                for (i in 0 until (n ?: 1).coerceIn(1, 3)) {
                    val x = Zones.head.x + 4 + i * 7.0; val y = Zones.head.y + sin(sec * 2 + i) * 0.8
                    if (orb.isNotEmpty()) Px.image(g, s, Px.tinted(orb[(i + (sec * 4).toInt()) % orb.size], Color(255, 200, 120)), x, y, 0.4, 0.85) else Px.dot(g, s, x + 3, y + 3, Color(255, 200, 120), 2.0)
                }
            }
            "Charge" -> for (i in 0 until 3) {
                // Electricity crackling round the body: short zigzags, here and gone.
                val age = ((sec * 2.2 + seeds.r(i, 1)) % 1.0)
                if (age > 0.4) continue
                val x0 = area.x + 4 + seeds.r(i + (sec * 2.2).toInt(), 2) * 22; val y0 = area.y + 8 + seeds.r(i, 3) * 14
                for (k in 0 until 4) Px.dot(g, s, x0 + k * 1.5, y0 + (if (k % 2 == 0) 0.0 else 1.5), Px.alpha(Color(255, 235, 90), 1 - age / 0.4), 1.0)
            }
            "Destiny Bond" -> {
                // A ghostly chain round the sprite, faint and breathing: if it faints now, the other goes with it.
                for (i in 0 until 14) {
                    val a = i * 2 * PI / 14 + sec * 0.8
                    if (i % 2 == 0) Px.dot(g, s, area.cx + cos(a) * 13, area.cy + sin(a) * 9, Px.alpha(Color(150, 120, 200), 0.7), 1.0)
                }
            }
            "Future Sight" -> {
                val orb = art?.frames("orb", 16).orEmpty()
                val x = Zones.head.x + Zones.head.w - 9; val y = Zones.head.y + sin(sec * 2.5)
                // Violet, as the move's own animation tints it; the sprite's own grey read as a blob (design audit).
                if (orb.isNotEmpty()) Px.image(g, s, Px.tinted(orb[((sec * 6).toInt()) % orb.size], Color(190, 120, 255)), x + 1, y, 0.4, 0.95) else Px.dot(g, s, x + 4, y + 4, Color(200, 120, 255), 3.0)
                pips(g, s, x, y + 7, n, Color(210, 150, 255))
            }
            "Wish" -> {
                val sp = art?.frames("sparkle", 32).orEmpty()
                if (sp.isNotEmpty()) Px.image(g, s, sp[((sec * 8).toInt()) % sp.size], Zones.head.x, Zones.head.y, 0.3)
                pips(g, s, Zones.head.x + 1, Zones.head.y + 7, n, Color(255, 230, 140))
            }
            "Lock-On" -> {
                val f = art?.frames("reticle", 16).orEmpty()
                if (f.size >= 5) Px.image(g, s, f[4], Zones.head.x + Zones.head.w - 10, Zones.head.y, 0.6, 0.8)
            }
            else -> Unit
        }
    }

    /** Screens and veils: a thin shimmering pane down each edge of the slot, its turns as pips at the foot. */
    private fun pane(g: Graphics2D, s: Double, sec: Double) {
        val c = when (name) { "Reflect" -> Color(255, 170, 200); "Light Screen" -> Color(255, 235, 120); "Safeguard" -> Color(170, 255, 170); else -> Color(235, 235, 255) }
        val area = Zones.slot
        // Each kind its own column pair, so two screens stand side by side.
        val inset = when (name) { "Reflect" -> 0.0; "Light Screen" -> 2.0; "Safeguard" -> 4.0; else -> 6.0 }
        for (y in 1 until 26) {
            val a = 0.25 + 0.2 * sin(sec * 3 + y * 0.5)
            Px.dot(g, s, area.x + 1 + inset, area.y + y, Px.alpha(c, a), 1.0)
            Px.dot(g, s, 35 - 1 - inset, area.y + y, Px.alpha(c, a), 1.0)
        }
        pips(g, s, area.x + 1 + inset, area.y + 26.5, spec.count, c)
    }

    private fun pips(g: Graphics2D, s: Double, x: Double, y: Double, n: Int?, c: Color) {
        for (k in 0 until (n ?: 0).coerceAtMost(8)) Px.dot(g, s, x + k * 2.0, y, c, 1.0)
    }

}
