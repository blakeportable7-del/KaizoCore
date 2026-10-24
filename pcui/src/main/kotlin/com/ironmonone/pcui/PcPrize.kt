package com.ironmonone.pcui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.Pc
import com.ironmonone.app.natureMark
import com.ironmonone.app.pcTypeColor
import com.ironmonone.app.referenceWidth
import com.ironmonone.app.rp
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.gachamon.GachaMonCard
import com.ironmonone.tracker.gachamon.GachaMonMaker
import com.ironmonone.tracker.gachamon.GachaMonMoves
import com.ironmonone.tracker.gachamon.GachaMonPrize
import com.ironmonone.tracker.gachamon.MoveCategory
import com.ironmonone.tracker.gachamon.MoveFacts
import com.ironmonone.tracker.gachamon.SixStats

/** A GachaMon card and the names it is shown with. */
data class PcGachaMon(
    val card: GachaMonCard,
    val name: String,
    val ability: String,
    /** The moves' names and powers, in slot order. */
    val moves: List<Pair<String, String>>,
    val trainerName: String?,
)

/**
 * GameOverScreen's "Prize card" (besteon's Ironmon-Tracker, MIT): once two common trainers are beaten, a GachaMon card
 * from the strongest Pokemon of a beaten trainer (GachaMonData.createPokemonDataFromDefeatedTrainers, the app's
 * GachaMonPrize and GachaMonMaker), shown on GachaMonOverlay's View tab over the game area.
 */
object PcPrize {
    /** GameSettings.game's number for the common trainers: Heart & Soul's own. */
    private fun gameNumber(t: GbaTracker, st: TrackerState?): Int = when {
        t.heartSoul -> GachaMonCard.HEART_SOUL
        else -> when (st?.routeVersion) { "ruby", "sapphire" -> 1; "emerald" -> 2; "firered", "leafgreen" -> 3; else -> 0 }
    }

    /** GameOverScreen.numDefeatedTrainers: the common trainers beaten this run. */
    fun defeated(t: GbaTracker, st: TrackerState?): Int = runCatching { GachaMonPrize.defeated(gameNumber(t, st), t::trainerDefeated).size }.getOrDefault(0)

    /** The prize card, or null when fewer than two common trainers are beaten or the pick cannot be read. */
    fun make(t: GbaTracker, st: TrackerState?, attempt: Int, random: kotlin.random.Random = kotlin.random.Random.Default): PcGachaMon? {
        val natDex = t.expandedSpeciesIds
        val source = object : GachaMonPrize.Source {
            override fun party(trainerId: Int) = t.trainer(trainerId)?.party.orEmpty().map { GachaMonPrize.TrainerMon(it.species, it.level, it.ivs, it.moves) }
            override fun listedBst(species: Int) = t.baseStats(species)?.bst ?: 0
            override fun valid(species: Int) = t.speciesExists(species)
            override fun learnset(species: Int) = t.learnset(species)
            override fun baseStats(species: Int) = t.baseStats(species)?.let { SixStats(it.hp, it.atk, it.def, it.spAtk, it.spDef, it.spe) }
            override fun firstAbility(species: Int) = t.baseStats(species)?.ability1 ?: 0
        }
        val pick = GachaMonPrize.pick(GachaMonPrize.defeated(gameNumber(t, st), t::trainerDefeated), source, random) ?: return null
        return cardFor(t, st, attempt, pick, random)
    }

    /**
     * The staged checks' prize (--panel-demo): the same card made from [species] at [level] as a trainer would hold it,
     * its last four level-up moves, IVs of 15, a neutral nature, credited to trainer [trainerId].
     */
    fun demo(t: GbaTracker, st: TrackerState?, attempt: Int, species: Int, level: Int, trainerId: Int): PcGachaMon? {
        val b = t.baseStats(species) ?: return null
        fun stat(base: Int, hp: Boolean) = if (hp) (2 * base + 15) * level / 100 + level + 10 else (2 * base + 15) * level / 100 + 5
        val moves = t.learnset(species).filter { it.first <= level }.map { it.second }.distinct().takeLast(4)
        val pick = GachaMonPrize.Pick(trainerId, species, level, moves,
            SixStats(stat(b.hp, true), stat(b.atk, false), stat(b.def, false), stat(b.spAtk, false), stat(b.spDef, false), stat(b.spe, false)), 0, b.ability1)
        return cardFor(t, st, attempt, pick, kotlin.random.Random(attempt))
    }

    private fun cardFor(t: GbaTracker, st: TrackerState?, attempt: Int, pick: GachaMonPrize.Pick, random: kotlin.random.Random): PcGachaMon? {
        val natDex = t.expandedSpeciesIds
        val base = t.baseStats(pick.species) ?: return null
        val rows = pick.moves.filter { it in 1..t.lastMoveId }.mapNotNull { t.moveRowFor(it) }
        val facts = rows.map { r ->
            val cat = when (r.category) { "PHY" -> MoveCategory.PHYSICAL; "SPE" -> MoveCategory.SPECIAL; else -> MoveCategory.STATUS }
            MoveFacts(r.id, r.type ?: 9, GachaMonMoves.powerText(r.id, r.power ?: 0), r.acc ?: 0, cat)
        }
        val mon = GachaMonMaker.Mon(
            personality = pick.trainerId.toLong(), species = pick.species, level = pick.level, abilityId = pick.abilityId,
            stats = pick.stats, moves = facts, nature = pick.nature, gender = 0, shiny = false,
            types = if (base.type2 != base.type1) listOf(base.type1, base.type2) else listOf(base.type1),
            baseStats = SixStats(base.hp, base.atk, base.def, base.spAtk, base.spDef, base.spe),
            listedBst = base.bst, evolves = t.evolution(pick.species) != null,
        )
        val date = java.time.LocalDate.now()
        val version = if (t.heartSoul) GachaMonCard.HEART_SOUL else GachaMonCard.gameVersionOf(st?.routeVersion ?: "")
        val card = GachaMonMaker.make(mon, natDex, version, attempt, date.year, date.monthValue, date.dayOfMonth, null, random = random)
        val trainer = t.trainer(pick.trainerId)?.let { listOf(it.className, it.name).filter { s -> s.isNotBlank() }.joinToString(" ") }?.takeIf { it.isNotBlank() }
        return PcGachaMon(
            card, PcLogOverlay.nice(t.speciesName(pick.species)), if (pick.abilityId > 0) PcLogOverlay.nice(t.abilityName(pick.abilityId)) else PcBesteon.BLANK,
            rows.map { r -> PcLogOverlay.nice(r.name) to (r.power?.takeIf { it > 0 }?.toString() ?: PcBesteon.BLANK) }, GachaMonPrize.trainerName(card) ?: trainer,
        )
    }

    private val NATURES = listOf(
        "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish", "Lax", "Timid", "Hasty", "Serious",
        "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky",
    )

    /** GachaMonOverlay's View tab over the game area (240 by 160): the facts on the left, the card at the right, the stats and moves under. */
    @Composable
    fun View(g: PcGachaMon, sprite: androidx.compose.ui.graphics.ImageBitmap?, onClose: () -> Unit) {
        val c = g.card
        val text = Pc.Text; val hi = Pc.Gold; val border = Pc.Border
        Box(Modifier.size(240.rp, 160.rp).background(Pc.Page)) {
            // The header and its tab, then CANVAS (2, 14, 236, 143).
            BText("View", 6, 1, hi)
            Box(Modifier.offset(4.rp, 11.rp).size((referenceWidth("View") + 4).rp, 1.rp).background(hi))
            BImage(PcLogOverlay.CLOSE, 230, 2, Pc.Header)
            // The card has nothing else to click, so a click anywhere on it closes it: it covers the whole game.
            BHit(0, 0, 240, 160, onClose)
            BRect(2, 14, 236, 143, border, Pc.Ground)
            val x = 6; var y = 16; val x2 = x + 61
            val name = (if (g.trainerName != null) "${g.trainerName}'s ${g.name}" else g.name).uppercase()
            BText(name, x, y, hi); y += 11
            NATURES.getOrNull(c.nature)?.let { BText(it, x, y, text) }
            BText("Lv. ${c.level}", x2, y, text); y += 11
            val stars = c.stars().let { if (it > 5) "5+" else "$it" }
            BText("Rating:", x, y, text); BText("${c.ratingScore} points  ($stars stars)", x2, y, text); y += 11
            BText("Battle Power:", x, y, text); BText("${c.battlePower} BP", x2, y, text); y += 11
            BText("Collected on:", x, y, text); BText("%d/%d/%02d".format(c.month, c.day, c.year % 100), x2, y, text); y += 11
            BText(c.gameName, x2, y, text); y += 11
            BText("Seed %,d".format(c.seedNumber), x2, y, text)
            // The card, in its containment border, at (CANVAS.X + CANVAS.W - 77, CANVAS.Y + 1).
            val cx = 2 + 236 - 77; val cy = 15
            BRect(cx - 1, cy - 1, 78, 78, border, Color.Black)
            Card(g, sprite, cx, cy, 4)
            // Stats and moves.
            BText("Stats", 6, 14 + 69, hi)
            Box(Modifier.offset(6.rp, (14 + 80).rp).size(44.rp, 1.rp).background(border))
            val s = c.stats
            val vals = listOf(s.hp, s.atk, s.def, s.spa, s.spd, s.spe)
            listOf("HP", "ATK", "DEF", "SPA", "SPD", "SPE").forEachIndexed { i, l ->
                val iy = 14 + 71 + 10 * (i + 1) - 10
                val mark = natureMark(c.nature, l)
                val col = when (mark) { '+' -> Pc.Positive; '-' -> Pc.Negative; else -> text }
                BText(l, 6, iy + 10, col)
                // Centred on the label's letters, as on the tracker card (PcBesteon).
                if (mark != null) NatureMark(mark, 22, iy + 12)
                BNumber(if (vals[i] == 0) PcBesteon.BLANK else "${vals[i]}", 31, iy + 10, 3, if (com.ironmonone.app.TrackerOptions.colorStatNumbers) col else text)
            }
            BText("Moves", 58, 14 + 88, hi)
            Box(Modifier.offset(58.rp, (14 + 99).rp).size(83.rp, 1.rp).background(border))
            g.moves.take(4).forEachIndexed { i, (n, p) ->
                val iy = 14 + 90 + 10 * (i + 1)
                BText(n, 58, iy, text)
                BNumber(p, 122, iy, 3, text)
            }
        }
    }

    /** GachaMonOverlay.drawGachaCard: 68 by 68 inside [pad] of black, the frame in the two types' colours, stars, power, the Pokemon, ability, name, stat bars. */
    @Composable
    fun Card(g: PcGachaMon, sprite: androidx.compose.ui.graphics.ImageBitmap?, x0: Int, y0: Int, pad: Int) {
        val c = g.card
        val W = 68; val H = 68; val TOP_W = 40; val TOP_H = 10; val BOT_H = 15
        val b1 = pcTypeColor(c.type1); val b2 = if (c.type2 != c.type1) pcTypeColor(c.type2) else b1
        fun fade(col: Color, a: Int) = col.copy(alpha = a / 255f)
        val bg1 = fade(b1, 0x2F); val bg2 = fade(b2, 0x2F); val bg1b = fade(b1, 0x46); val bg2b = fade(b2, 0x46)
        val trainer = g.trainerName != null
        val bars = listOf(c.stats.hp - 10, c.stats.atk, c.stats.def, c.stats.spa, c.stats.spd, c.stats.spe).map { v ->
            val value = if (v <= 6) 0 else v
            if (c.level <= 0) 0 else minOf(value / c.level, 5)
        }
        Canvas(Modifier.offset(x0.rp, y0.rp).size((W + pad * 2 + 1).rp, (H + pad * 2 + 1).rp)) {
            val u = size.width / (W + pad * 2 + 1)
            fun rect(x: Int, y: Int, w: Int, h: Int, col: Color) = drawRect(col, Offset(x * u, y * u), Size((w + 1) * u, (h + 1) * u))
            fun line(ax: Int, ay: Int, bx: Int, by: Int, col: Color) {
                val n = maxOf(kotlin.math.abs(bx - ax), kotlin.math.abs(by - ay))
                for (k in 0..n) {
                    val px = ax + if (n == 0) 0 else Math.round((bx - ax) * k / n.toFloat())
                    val py = ay + if (n == 0) 0 else Math.round((by - ay) * k / n.toFloat())
                    drawRect(col, Offset(px * u, py * u), Size(u, u))
                }
            }
            rect(0, 0, W + pad * 2, H + pad * 2, Color.Black)
            val x = pad; val y = pad
            rect(x + 1, y + 1, W / 2 - 1, H - 2 - BOT_H, bg1)
            rect(x + 1 + W / 2, y + 2, 7, 8, bg2)
            rect(x + 1 + W / 2, y + 1 + TOP_H, W / 2 - 2, H - TOP_H - 2 - BOT_H, bg2)
            rect(x + 1, y + 1 + H - BOT_H, W / 2 - 1, BOT_H - 2, bg1b)
            rect(x + 1 + W / 2, y + 1 + H - BOT_H, W / 2 - 2, BOT_H - 2, bg2b)
            // Stars: up to four in a row, five as three over two, platinum past five.
            val n = c.stars()
            if (n >= 1) {
                val draw = minOf(n, 5)
                val two = draw >= 5
                val iconSize = if (draw == 5) 10 else 9
                val colors = when {
                    trainer -> listOf(0xFF8BD6F5L, 0xFF67B8E5L, 0xFFBDECFEL, 0x40000000L)
                    n > 5 -> listOf(0xFFEEEEEEL, 0xFFCCCCCCL, 0xFFFFFFFFL, 0x40000000L)
                    else -> listOf(0xFFFCED86L, 0xFFEACA1DL, 0xFFFFFFFFL, 0x40000000L)
                }.map { Color(it.toInt()) }
                val sx = x + if (two) 3 else 0
                for (i in 1..draw) {
                    var ix = sx + 1 + iconSize * (i - 1); var iy = y + 1
                    if (i >= 4 && two) { ix = ix + 5 - 3 * iconSize; iy = iy + iconSize - 4 }
                    BImg.STAR.forEachIndexed { ry, row -> row.forEachIndexed { rx, ch -> val d = Character.digit(ch, 36); if (d > 0) drawRect(colors[d - 1], Offset((ix + rx) * u, (iy + ry) * u), Size(u, u)) } }
                }
            }
            line(x + 1, y + 1, x + 1 + TOP_W - 7, y + 1, b1)
            line(x + 1, y + 1, x + 1, y + H - 1, b1)
            line(x + 1, y + H - 1, x + W / 2, y + H - 1, b1)
            val bot = y + H - BOT_H
            line(x + 1, bot, x + W / 2, bot, b1)
            line(x + W / 2 + 1, bot, x + W - 1, bot, b2)
            line(x + TOP_W, y + 1, x + TOP_W + 4, y + 1 + TOP_H, b2)
            line(x + TOP_W + 1, y + 1, x + TOP_W + 5, y + 1 + TOP_H, b2)
            line(x + 1 + TOP_W - 6, y + 1, x + 1 + TOP_W, y + 1, b2)
            line(x + 1 + TOP_W + 4, y + 1 + TOP_H, x + W - 1, y + 1 + TOP_H, b2)
            line(x + W - 1, y + H - 1, x + W - 1, y + 1 + TOP_H, b2)
            line(x + W / 2 + 1, y + H - 1, x + W - 1, y + H - 1, b2)
            bars.forEachIndexed { i, w ->
                val sy = bot + 2 * (i + 1)
                line(x + 1, sy, x + 1 + w, sy, b1)
                line(x + W - 1 - w, sy, x + W - 1, sy, b2)
            }
        }
        val x = x0 + pad; val y = y0 + pad
        val power = "${c.battlePower}"
        BText(power, x + W - 3 - referenceWidth(power) + (if (c.battlePower > 9999) 1 else 0), y, Color.White)
        if (sprite != null) Image(sprite, null, filterQuality = FilterQuality.None, modifier = Modifier.offset((x + W / 2 - 16).rp, (y + 8).rp).size(32.rp))
        val shadow = Color(0xC0000000.toInt())
        BText(g.ability, x + 1, y + H - 26, shadow, w = W, align = TextAlign.Center)
        BText(g.ability, x, y + H - 27, Color(0xFFFCED86.toInt()), w = W, align = TextAlign.Center)
        BText(g.name, x + 1, y + H - 13, shadow, w = W, align = TextAlign.Center)
        BText(g.name, x, y + H - 14, Color.White, w = W, align = TextAlign.Center)
    }
}
