package com.ironmonone.pc.fx

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Abilities, played the moment the game shows them (Blake, 2026-10-10: "add volt absorb, etc ability animations at the
 * same time they are revealed to the player"). The game marks the reveal itself: Heart & Soul's ability pop-up, which the
 * tracker reads as TrackerState.abilitiesRevealed, so nothing plays before the player has seen it.
 */
enum class AbilityKind { ABSORB, FLASH_FIRE, LEVITATE, INTIMIDATE, WEATHER, STURDY, PRESSURE, SHIELD, CONTACT, CURE, STAT, PLAIN }

object Abilities {
    /**
     * How an ability looks and what the game does with it when it shows: [art] the ROM sprite it plays with, [heal] a
     * quarter of max HP back (the absorbs), [stages] the holder's stat changes, [otherStages] the other side's
     * (Intimidate), [weather] the weather it starts, [toOneHp] Sturdy holding on, [cures] the holder's status gone (Shed
     * Skin), [otherStatus] the status it gives the Pokemon that touched it (Static PAR, Poison Point PSN).
     */
    data class Look(
        val kind: AbilityKind, val art: String? = null, val color: Color = Color.WHITE, val heal: Boolean = false,
        val stages: Map<String, Int> = emptyMap(), val otherStages: Map<String, Int> = emptyMap(),
        val weather: String? = null, val toOneHp: Boolean = false, val cures: Boolean = false, val otherStatus: String? = null,
    )

    private val YELLOW = Color(255, 230, 90)
    private val BLUE = Color(120, 190, 255)
    private val GREEN = Color(120, 230, 110)
    private val ORANGE = Color(255, 150, 60)

    private fun key(s: String) = s.lowercase().replace(Regex("[^a-z]"), "")

    /**
     * Abilities Heart & Soul never shows in a pop-up: they live only inside the damage, accuracy and critical-hit maths
     * (pokehns-expansion src/battle_util.c, GetTotalAccuracy 10483-10540, CalcCritChanceStage 8031, the damage
     * modifiers), with at most a RecordAbilityBattle for the AI and no script. The player cannot see them work, so an
     * opponent's can never be learned from its own activation; only another pop-up names one (your Trace, Role Play or
     * Skill Swap). All 112 of the game's, from docs/ability-popups.tsv (read off the source 2026-10-10; Blake: "compound
     * eye ability enemy gives no notification"); AbilitiesTest holds this list to that file.
     */
    val SILENT = setOf(
        "adaptability", "aerilate", "analytic", "arenatrap", "battery", "battlearmor", "blaze", "chlorophyll",
        "compoundeyes", "contrary", "corrosion", "defeatist", "dragonsmaw", "earlybird", "filter", "flareboost",
        "fluffy", "friendguard", "furcoat", "galewings", "galvanize", "gluttony", "gorillatactics", "grasspelt",
        "guts", "heatproof", "heavymetal", "honeygather", "hugepower", "hungerswitch", "hustle", "icescales",
        "illusion", "infiltrator", "ironfist", "klutz", "lightmetal", "liquidvoice", "longreach", "magicguard",
        "magnetpull", "marvelscale", "megalauncher", "merciless", "minus", "multiscale", "multitype",
        "myceliummight", "naturalcure", "neuroforce", "noguard", "normalize", "overgrow", "parentalbond", "pixilate",
        "plus", "powerspot", "prankster", "prismarmor", "propellertail", "punkrock", "purepower", "quickfeet",
        "reckless", "refrigerate", "regenerator", "rivalry", "rkssystem", "rockhead", "rockypayload", "runaway",
        "sandforce", "sandrush", "sandveil", "serenegrace", "shadowshield", "shadowtag", "sharpness", "sheerforce",
        "shellarmor", "shielddust", "simple", "skilllink", "slushrush", "sniper", "snowcloak", "solidrock",
        "stakeout", "stall", "stalwart", "steelworker", "steelyspirit", "stench", "strongjaw", "superluck",
        "surgesurfer", "swarm", "swiftswim", "tangledfeet", "technician", "thickfat", "tintedlens", "torrent",
        "toughclaws", "toxicboost", "transistor", "triage", "unaware", "unburden", "unseenfist", "victorystar",
        "wonderskin",
    )

    fun silent(name: String) = key(name) in SILENT

    fun of(name: String): Look = when (key(name)) {
        "voltabsorb" -> Look(AbilityKind.ABSORB, "spark", YELLOW, heal = true)
        "waterabsorb", "dryskin" -> Look(AbilityKind.ABSORB, "rain", BLUE, heal = true)
        // Lightning Rod in Emerald draws Electric moves in doubles; in Heart & Soul it also raises Sp. Atk.
        "lightningrod" -> Look(AbilityKind.ABSORB, "spark", YELLOW, stages = mapOf("SPA" to 1))
        // Rain Dish: a little HP back each turn in rain.
        "raindish" -> Look(AbilityKind.ABSORB, "rain", BLUE, heal = true)
        "flashfire" -> Look(AbilityKind.FLASH_FIRE, "ember", ORANGE)
        "levitate" -> Look(AbilityKind.LEVITATE, "mud_sand", Color(200, 180, 140))
        "intimidate" -> Look(AbilityKind.INTIMIDATE, "anger", Color(255, 80, 70), otherStages = mapOf("ATK" to -1))
        "drizzle" -> Look(AbilityKind.WEATHER, "rain", BLUE, weather = "Rain")
        // Cloud Nine and Air Lock: the weather stops counting while they are out (the falling weather stays: it is still up).
        "cloudnine", "airlock" -> Look(AbilityKind.STAT, null, Color(220, 235, 255))
        "drought" -> Look(AbilityKind.WEATHER, null, ORANGE, weather = "Sun")
        "sandstream" -> Look(AbilityKind.WEATHER, "mud_sand", Color(210, 180, 120), weather = "Sandstorm")
        "snowwarning" -> Look(AbilityKind.WEATHER, "hail", Color(200, 230, 255), weather = "Hail")
        "sturdy" -> Look(AbilityKind.STURDY, "protect", Color(255, 220, 120), toOneHp = true)
        "pressure" -> Look(AbilityKind.PRESSURE, null, Color(170, 90, 255))
        // Each shows its pop-up when it blocks something (a stat drop or a status from a status move, Intimidate, a 100%
        // flinch, Roar, Self-Destruct, a sound move, a stolen item): docs/ABILITY-POPUPS.md has each one's triggers.
        "keeneye", "clearbody", "whitesmoke", "hypercutter", "innerfocus", "owntempo", "oblivious", "insomnia",
        "vitalspirit", "limber", "immunity", "waterveil", "magmaarmor", "soundproof", "damp", "stickyhold", "suctioncups",
        "illuminate" -> Look(AbilityKind.SHIELD, "protect", Color(150, 220, 255))
        "roughskin" -> Look(AbilityKind.CONTACT, "spikes", Color(200, 200, 210))
        // Liquid Ooze: the drain hurts the drainer instead.
        "liquidooze" -> Look(AbilityKind.CONTACT, "poison_bubble", Color(150, 200, 90))
        "static" -> Look(AbilityKind.CONTACT, "spark", YELLOW, otherStatus = "PAR")
        "flamebody" -> Look(AbilityKind.CONTACT, "ember", ORANGE, otherStatus = "BRN")
        "poisonpoint" -> Look(AbilityKind.CONTACT, "poison_bubble", Color(200, 110, 255), otherStatus = "PSN")
        // Effect Spore gives sleep, poison or paralysis at random; the game's own Spore sprite either way.
        "effectspore" -> Look(AbilityKind.CONTACT, "spore", Color(190, 230, 140), otherStatus = "PSN")
        // Shed Skin clears the holder's status at the end of a turn. (Natural Cure, on switching out, never shows a pop-up.)
        "shedskin" -> Look(AbilityKind.CURE, "sparkle", Color(230, 240, 255), cures = true)
        "cutecharm" -> Look(AbilityKind.CONTACT, "pink_heart", Color(255, 140, 200), otherStatus = "ATTRACT")
        // Synchronize hands its status back; Color Change and Trace change the holder (a type, an ability); Pickup finds an item.
        "synchronize" -> Look(AbilityKind.CONTACT, "sparkle", Color(230, 150, 255))
        "colorchange", "trace" -> Look(AbilityKind.STAT, null, Color(200, 230, 255))
        "pickup" -> Look(AbilityKind.STAT, null, Color(255, 225, 150))
        "speedboost" -> Look(AbilityKind.STAT, stages = mapOf("SPE" to 1))
        else -> Look(AbilityKind.PLAIN)
    }
}

/** The sprite slot's columns: effects stay left of the words (x 36) and above the type badge (y 33). */
private val SLOT_CLIP = Rect(6.0, 6.0, 30.0, 27.0)

private inline fun clipped(g: Graphics2D, s: Double, r: Rect, draw: () -> Unit) {
    val before = g.clip
    g.clipRect((r.x * s).roundToInt(), (r.y * s).roundToInt(), (r.w * s).roundToInt(), (r.h * s).roundToInt())
    draw()
    g.clip = before
}

/**
 * The reveal on the holder's card: the ability's line lights up (a shine runs along it and the words pop), and the
 * ability's own effect plays in the sprite slot. [row] is where the card prints the ability (null: the line is off).
 */
class AbilityReveal(start: Long, val name: String, val look: Abilities.Look, val row: Rect?, val art: FxRomArt?, val font: Font,
                    val area: Rect = CardSpots.sprite) : Fx(start, 2000) {
    override val usesSlot get() = true
    private val seeds = Seeds(name.hashCode())

    override fun spriteMotion(now: Long): Triple<Double, Double, Double>? {
        if (!liveSprite) return null
        val t = t(now)
        return when (look.kind) {
            // Levitate: up off the ground with an overshoot, bobbing, and down again at the end.
            AbilityKind.LEVITATE -> {
                // Up clear of the ground (7 px, a gap the eye can see), bobbing, and down again at the end.
                val up = Ease.outBack(min(1.0, t / 0.25)) * (if (t > 0.8) 1 - (t - 0.8) / 0.2 else 1.0)
                Triple(0.0, -7 * up + sin(t * 18) * 1.0 * up, 1.0)
            }
            // Sturdy: braced, a hard jolt that settles.
            AbilityKind.STURDY -> if (t < 0.35) Triple(sin(t * 90) * 1.4 * (1 - t / 0.35), 1.0 * (1 - t / 0.35), 1.0) else null
            // Absorbs and Flash Fire: a swell as the power soaks in.
            AbilityKind.ABSORB, AbilityKind.FLASH_FIRE -> if (t in 0.45..0.6) Triple(0.0, -1.0, 1.0) else null
            else -> null
        }
    }

    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        row?.let { line(g, s, t, card, it) }
        clipped(g, s, SLOT_CLIP) {
            when (look.kind) {
                AbilityKind.ABSORB -> absorb(g, s, t)
                AbilityKind.FLASH_FIRE -> { absorb(g, s, t); flames(g, s, t) }
                AbilityKind.LEVITATE -> dust(g, s, t)
                AbilityKind.INTIMIDATE -> glare(g, s, t)
                AbilityKind.WEATHER -> weather(g, s, t)
                AbilityKind.STURDY -> shield(g, s, t, 0.6)
                AbilityKind.SHIELD -> shield(g, s, t, 0.5)
                AbilityKind.PRESSURE -> ripples(g, s, t)
                AbilityKind.CONTACT -> burst(g, s, t)
                AbilityKind.CURE -> molt(g, s, t)
                AbilityKind.STAT, AbilityKind.PLAIN -> glow(g, s, t, Color(255, 225, 120))
            }
        }
        if (look.kind == AbilityKind.PRESSURE) ppTint(g, s, t)
        if (look.kind == AbilityKind.STURDY) slotLabel(g, s, font, area, ((t - 0.2) / 0.8).coerceIn(0.0, 1.0), "STURDY", "HELD ON", Color(255, 225, 120), Color(70, 40, 0))
    }

    /** The ability's line: the words pop and a band of light runs along them, then a gold underline draws in. */
    private fun line(g: Graphics2D, s: Double, t: Double, card: BufferedImage?, r: Rect) {
        val pop = if (t < 0.35) 1 + 0.12 * sin(PI * t / 0.35) else 1.0
        // Grown from its left edge, so the words never push left over the type badge.
        if (card != null && pop > 1.001) Px.lift(g, s, card, r, dx = (pop - 1) * r.w / 2, k = pop)
        if (t < 0.7) Px.band(g, s, r, r.x - 2 + (r.w + 4) * Ease.inOutSine(t / 0.7), Color(255, 255, 230, 110))
        val u = Ease.outCubic(((t - 0.1) / 0.4).coerceIn(0.0, 1.0)) * Ease.window(t, 0.0, 0.85)
        if (u > 0) { g.color = Px.alpha(Color(255, 210, 80), u); g.fillRect((r.x * s).roundToInt(), ((r.y + r.h - 0.5) * s).roundToInt(), (r.w * 0.7 * Ease.outCubic(min(1.0, t * 2.5)) * s).roundToInt(), max1(s)) }
    }

    private fun max1(s: Double) = maxOf(1, (s * 0.6).roundToInt())

    private fun frames(name: String?): List<BufferedImage> = when (name) {
        null -> emptyList()
        "spark" -> art?.parts("spark")?.getOrNull(1)?.let { split(it, 16) }.orEmpty()
        "mud_sand" -> art?.parts("mud_sand").orEmpty()
        "ember" -> art?.frames("ember", 32).orEmpty()
        "sparkle" -> art?.frames("sparkle", 32).orEmpty()
        "protect" -> art?.parts("protect").orEmpty()
        else -> art?.frames(name, 16).orEmpty()
    }

    private fun split(img: BufferedImage, fh: Int) = (0 until img.height / fh).map { img.getSubimage(0, it * fh, img.width, fh) }

    /** Absorbs: the move's pieces stream in from the slot's edges to the sprite; it flashes and, for a heal, sparkles. */
    private fun absorb(g: Graphics2D, s: Double, t: Double) {
        val f = frames(look.art)
        for (i in 0 until 10) {
            val delay = seeds.r(i, 1) * 0.3
            val p = ((t - delay) / 0.3).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            val a = seeds.r(i, 2) * 2 * PI
            val rad = 17 * (1 - Ease.inCubic(p))
            val x = area.cx + cos(a) * rad; val y = area.cy + sin(a) * rad * 0.8
            if (f.isNotEmpty()) { val img = f[i % f.size]; val k = 8.0 / img.width.coerceAtLeast(1) * (1 - 0.4 * p); Px.image(g, s, img, x - img.width * k / 2, y - img.height * k / 2, k) }
            else Px.dot(g, s, x, y, look.color, 1.5)
        }
        glow(g, s, ((t - 0.4) / 0.45).coerceIn(0.0, 1.0), look.color)
        if (look.heal && t > 0.5) {
            val sp = art?.frames("green_sparkle", 16).orEmpty()
            val e = (t - 0.5) / 0.5
            for (i in 0 until 5) {
                val x = area.x + 5 + i * 5.5; val y = area.y + area.h - 4 - e * 22 - (i % 2) * 4
                if (sp.isNotEmpty()) Px.image(g, s, sp[(i + (e * 8).toInt()) % sp.size], x - 4, y - 4, 0.5, Ease.window(e, 0.1, 0.7))
                else Px.dot(g, s, x, y, Px.alpha(Color(120, 255, 140), Ease.window(e)), 1.0)
            }
        }
    }

    /** A soft flash over the sprite in [c], rising and falling over [t]. */
    private fun glow(g: Graphics2D, s: Double, t: Double, c: Color) {
        if (t <= 0.0 || t >= 1.0) return
        Px.disc(g, s, area.cx, area.cy, 12 + 2 * t, 11 + 2 * t, Px.alpha(c, sin(PI * t) * 0.5))
    }

    /** Flash Fire: once the fire is in, flames lick up round the sprite's feet for the rest of the moment. */
    private fun flames(g: Graphics2D, s: Double, t: Double) {
        if (t < 0.5) return
        val f = frames("ember")
        val e = (t - 0.5) / 0.5
        for (i in 0 until 4) {
            val x = area.x + 3 + i * 7.0; val y = area.y + area.h - 12 - sin(e * 12 + i) * 1.5
            if (f.isNotEmpty()) Px.image(g, s, f[((e * 14).toInt() + i) % f.size], x, y, 0.35, Ease.window(e, 0.1, 0.8))
        }
    }

    /** Levitate: a puff of dust where the Ground move hit nothing. */
    /** Levitate: the shadow left on the ground under the floating sprite, and a puff of dust where the move hit nothing. */
    private fun dust(g: Graphics2D, s: Double, t: Double) {
        val lift = Ease.outBack(min(1.0, t / 0.25)) * (if (t > 0.8) 1 - (t - 0.8) / 0.2 else 1.0)
        Px.disc(g, s, area.cx, area.y + area.h - 2, 9 - 3 * lift, 1.6, Color(0, 0, 0, (150 * Ease.window(t, 0.05, 0.9)).toInt()))
        val parts = art?.parts("mud_sand").orEmpty()
        val f = parts.getOrNull(1) ?: parts.firstOrNull()
        val e = (t / 0.6).coerceIn(0.0, 1.0)
        if (e >= 1.0) return
        for (i in 0 until 4) {
            val dir = if (i % 2 == 0) -1 else 1
            val x = area.cx + dir * (4 + e * 9 + i); val y = area.y + area.h - 4 - e * 4 - (i / 2)
            if (f != null) Px.image(g, s, f, x - 4, y - 4, 0.55, 1 - e) else Px.dot(g, s, x, y, Px.alpha(look.color, 1 - e), 1.5)
        }
    }

    /** Intimidate on the holder's card: a red glare and a shockwave out from the sprite. */
    private fun glare(g: Graphics2D, s: Double, t: Double) {
        val f = frames("anger")
        if (f.isNotEmpty()) Px.image(g, s, f[0], area.x + area.w - 15, area.y + 1, 0.7 * Ease.outBack(min(1.0, t * 4)), Ease.window(t, 0.05, 0.75))
        for (k in 0 until 2) {
            val p = ((t - k * 0.15) / 0.5).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            val r = 6 + p * 12
            Px.ring(g, s, area.cx, area.cy, r, r * 0.9, Px.alpha(look.color, 0.7 * (1 - p)))
        }
    }

    /** A weather setter: its weather gathers round the sprite (the falling weather itself is the lasting effect). */
    private fun weather(g: Graphics2D, s: Double, t: Double) {
        if (look.weather == "Sun") {
            // A sun flare rising behind the sprite: rays turning.
            val a = Ease.window(t, 0.15, 0.7)
            for (i in 0 until 10) {
                val ang = i * PI / 5 + t * 2
                val p = java.awt.geom.Path2D.Double()
                p.moveTo(area.cx * s, area.cy * s)
                p.lineTo((area.cx + cos(ang - 0.15) * 26) * s, (area.cy + sin(ang - 0.15) * 26) * s)
                p.lineTo((area.cx + cos(ang + 0.15) * 26) * s, (area.cy + sin(ang + 0.15) * 26) * s)
                p.closePath(); g.color = Px.alpha(Color(255, 200, 80), 0.35 * a); g.fill(p)
            }
            glow(g, s, t, Color(255, 210, 90))
            return
        }
        val f = frames(look.art)
        for (i in 0 until 12) {
            val a = seeds.r(i, 1) * 2 * PI + t * 6
            val rad = 6 + 12 * seeds.r(i, 2)
            val x = area.cx + cos(a) * rad; val y = area.cy + sin(a) * rad * 0.7
            val alpha = Ease.window(t, 0.1, 0.7)
            if (f.isNotEmpty()) { val img = f[i % f.size]; val k = 6.0 / img.width.coerceAtLeast(1); Px.image(g, s, img, x - 3, y - 3, k, alpha) }
            else Px.dot(g, s, x, y, Px.alpha(look.color, alpha), 1.2)
        }
    }

    /** Sturdy and the blocking abilities: the game's Protect shield flashes over the sprite. */
    /**
     * Sturdy and the blocking abilities: a bubble's edge closes round the sprite and a glint runs over it, the Pokemon
     * plain inside. The game's Protect sprite at half size was a teal blob over the Pokemon (design audit).
     */
    private fun shield(g: Graphics2D, s: Double, t: Double, peak: Double) {
        val a = Ease.window(t, 0.08, 0.6) * peak
        val k = Ease.outBack(min(1.0, t * 4))
        val rx = 13.0 * k; val ry = 12.0 * k
        Px.ring(g, s, area.cx, area.cy, rx, ry, Px.alpha(look.color, a))
        Px.ring(g, s, area.cx, area.cy, rx - 1, ry - 1, Px.alpha(look.color, a * 0.35))
        // The glint: a short bright arc travelling round the top of the bubble.
        val g0 = -PI * 0.85 + t * PI * 0.9
        for (i in 0 until 5) {
            val ang = g0 + i * 0.12
            Px.dot(g, s, kotlin.math.floor(area.cx + cos(ang) * rx), kotlin.math.floor(area.cy + sin(ang) * ry), Px.alpha(Color.WHITE, a))
        }
    }

    /** Pressure: dark ripples rolling out of the sprite. */
    private fun ripples(g: Graphics2D, s: Double, t: Double) {
        for (k in 0 until 3) {
            val p = ((t - k * 0.18) / 0.55).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            val r = 4 + p * 13
            Px.ring(g, s, area.cx, area.cy, r, r * 0.8, Px.alpha(look.color, 0.6 * (1 - p)))
        }
        glow(g, s, t, Color(60, 20, 90))
    }

    /** Pressure: the PP column tints twice: each move costs 2 PP against it. */
    private fun ppTint(g: Graphics2D, s: Double, t: Double) {
        val a = (sin(t * 4 * PI) * 0.5 + 0.5) * Ease.window(t, 0.1, 0.8) * 0.35
        val r = Rect(86.0, 94.0, 14.0, 39.0)
        g.color = Px.alpha(look.color, a); g.fillRect((r.x * s).roundToInt(), (r.y * s).roundToInt(), (r.w * s).roundToInt(), (r.h * s).roundToInt())
    }

    /**
     * Shed Skin and the curing abilities: a white ring bursts off the sprite as the status leaves it, flakes of the old
     * skin fall away, and sparkles settle on the clean sprite.
     */
    private fun molt(g: Graphics2D, s: Double, t: Double) {
        val ring = (t / 0.45).coerceIn(0.0, 1.0)
        if (ring < 1.0) {
            val r = 5 + ring * 12
            Px.ring(g, s, area.cx, area.cy, r, r * 0.85, Px.alpha(look.color, 0.8 * (1 - ring)))
        }
        for (i in 0 until 7) {
            val p = ((t - 0.1 - seeds.r(i, 1) * 0.2) / 0.6).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            val x = area.x + 6 + seeds.r(i, 2) * 20 + sin(p * 9 + i) * 1.5
            val y = area.y + 8 + p * 22
            Px.dot(g, s, x, y, Px.alpha(Color(200, 190, 160), 1 - p), 1.2)
        }
        val sp = frames("sparkle")
        if (sp.isNotEmpty() && t > 0.4) for (i in 0 until 3) {
            val p = ((t - 0.4 - i * 0.12) / 0.35).coerceIn(0.0, 1.0)
            if (p <= 0.0 || p >= 1.0) continue
            Px.image(g, s, sp[(p * sp.size).toInt().coerceAtMost(sp.size - 1)], area.x + 3 + i * 9.0, area.y + 4 + (i % 2) * 8.0, 0.35)
        }
    }

    /** Contact abilities: barbs, sparks, embers or spores bursting off the sprite. */
    private fun burst(g: Graphics2D, s: Double, t: Double) {
        val f = frames(look.art)
        val e = (t / 0.7).coerceIn(0.0, 1.0)
        if (e >= 1.0) return
        for (i in 0 until 8) {
            val a = i * PI / 4 + seeds.r(i, 1) * 0.4
            val rad = 6 + Ease.outCubic(e) * 14
            val x = area.cx + cos(a) * rad; val y = area.cy + sin(a) * rad * 0.8
            if (f.isNotEmpty()) { val img = f[i % f.size]; val k = 7.0 / img.width.coerceAtLeast(1); Px.image(g, s, img, x - 3.5, y - 3.5, k, 1 - e * e) }
            else Px.dot(g, s, x, y, Px.alpha(look.color, 1 - e), 1.5)
        }
    }
}

/**
 * Intimidate on the other card (the side it lowers): the anger mark snaps over the sprite, the sprite cowers, a sweat
 * drop slides down. The Attack drop itself pops on the stat row (StatPop), from the game's own stage change.
 */
class Intimidated(start: Long, val art: FxRomArt?, val area: Rect = CardSpots.sprite) : Fx(start, 1400) {
    override val usesSlot get() = true
    override fun spriteMotion(now: Long): Triple<Double, Double, Double>? {
        if (!liveSprite) return null
        val t = t(now)
        return if (t < 0.5) Triple(sin(t * 70) * 1.2 * (1 - t / 0.5), 1.5 * Ease.window(t, 0.1, 0.6), 1.0) else null
    }

    override fun draw(g: Graphics2D, s: Double, now: Long, card: BufferedImage?) {
        val t = t(now)
        clipped(g, s, SLOT_CLIP) {
            val anger = art?.frames("anger", 16).orEmpty()
            val k = 0.6 * Ease.outBack(min(1.0, t * 5))
            if (anger.isNotEmpty() && k > 0.01) Px.image(g, s, anger[0], area.x + area.w - 14, area.y + 1, k, Ease.window(t, 0.02, 0.7))
            val drop = art?.frames("sweat_drop", 8).orEmpty()
            val d = ((t - 0.2) / 0.6).coerceIn(0.0, 1.0)
            if (drop.isNotEmpty() && d > 0 && d < 1) Px.image(g, s, drop[0], area.x + 6, area.y + 6 + d * 8, 0.8, Ease.window(d, 0.1, 0.7))
        }
    }
}
