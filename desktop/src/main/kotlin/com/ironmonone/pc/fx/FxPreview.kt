package com.ironmonone.pc.fx

import androidx.compose.ui.graphics.toAwtImage
import com.ironmonone.pcui.PcPanel
import com.ironmonone.pcui.Samples
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.roundToInt

/**
 * The effects drawn over real cards, to look at without a game (the animation exploration on the copy): each one as an
 * animated GIF at 25 frames a second over the card at 3 window pixels a card pixel, and a contact sheet of six moments.
 *   ./gradlew :desktop:fxPreview    (writes build/fx)
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/fx").apply { mkdirs() }
    // --art <official 2.0.6>: every sprite of fx-art-kaizo.json out of the ROM, 4 times its size, into <out>/art.
    args.indexOf("--art").takeIf { it >= 0 }?.let { i ->
        val art = FxRomArt.forBuild(com.ironmonone.pc.PcHns.build(File(args[i + 1]).readBytes()))
        val dir = File(out, "art").apply { mkdirs() }
        val names = Regex(""""(\w+)":\s*\{""").findAll(com.ironmonone.pc.PcHns.resource("hns/fx-art-kaizo.json").toString(Charsets.UTF_8).substringAfter("\"art\"")).map { it.groupValues[1] }.toList()
        for (n in names) art.parts(n)?.forEachIndexed { k, img ->
            val big = BufferedImage(img.width * 4, img.height * 4, BufferedImage.TYPE_INT_ARGB)
            big.createGraphics().apply { color = Color(60, 60, 70); fillRect(0, 0, big.width, big.height); drawImage(img, 0, 0, big.width, big.height, null); dispose() }
            ImageIO.write(big, "png", File(dir, "$n-$k.png"))
        }
        println("fx art: ${names.size} sprites into $dir")
        return
    }
    val scale = 3.0
    val font = com.ironmonone.pc.TrackerWindow.cardFont()
    val files = File(out, "files").apply { mkdirs() }
    PcPanel.Renderer(files, PcPanel.Input(null, 0), scale.toFloat()).use { r ->
        val own = r.renderImage(PcPanel.Input(Samples.party, 740))
        val foe = r.renderImage(PcPanel.Input(Samples.enemy, 740, routeName = "Route 24", moveLevels = Samples.levels))
        val start = 0L
        fun badge(i: Int): BufferedImage? = com.ironmonone.app.PcAssets.badge(com.ironmonone.app.pcDesktopCtx, com.ironmonone.app.PcAssets.HNS_BADGES, i + 1, true)?.toAwtImage()
        val (zephyr, falkner) = com.ironmonone.pcui.HNS_BADGES[0]
        val shows = listOf(
            Triple("burn", own, listOf(StatusOnset(start, "BRN", font), StatusAura(start, "BRN"))),
            Triple("poison", own, listOf(StatusOnset(start, "PSN", font), StatusAura(start, "PSN"))),
            Triple("paralysis", foe, listOf(StatusOnset(start, "PAR", font), StatusAura(start, "PAR"))),
            Triple("freeze", foe, listOf(StatusOnset(start, "FRZ", font), StatusAura(start, "FRZ"))),
            Triple("sleep", own, listOf(StatusOnset(start, "SLP", font), StatusAura(start, "SLP"))),
            Triple("confusion", foe, listOf(StatusOnset(start, "CONFUSE", font), StatusAura(start, "CONFUSE"))),
            Triple("attract", own, listOf(StatusOnset(start, "ATTRACT", font), StatusAura(start, "ATTRACT"))),
            Triple("stat-up", own, listOf(StatPop(start, "ATK", 2, font), StatPop(start + 250_000_000L, "SPE", 1, font))),
            Triple("stat-down", foe, listOf(StatPop(start, "DEF", -1, font), StatPop(start + 250_000_000L, "SPA", -2, font))),
            Triple("hit", foe, listOf(Hit(start, 12, false, font))),
            Triple("big-hit", foe, listOf(Hit(start, 41, true, font))),
            Triple("level-up", own, listOf(LevelUp(start, 7, font))),
            Triple("faint", foe, listOf(Faint(start))),
            Triple("badge", own, listOf(BadgeWin(start, badge(0), zephyr, falkner.substringBefore(","), font))),
        )
        for ((name, card, fx) in shows) {
            val ms = fx.maxOf { if (it.loops) 2400L else it.ms + (it.start - start) / 1_000_000 }.coerceAtLeast(1200)
            val frames = ArrayList<BufferedImage>()
            var tMs = 0L
            while (tMs <= ms) {
                frames += frame(card, fx, start + tMs * 1_000_000, scale)
                tMs += 40
            }
            FxGif.gif(File(out, "$name.gif"), frames, 4)
            FxGif.sheet(frames, File(out, "$name-sheet.png"))
            println("fx preview: $name, ${frames.size} frames")
        }
    }
}

/** One moment: the card, moved by any shake, then every effect over it. */
private fun frame(card: BufferedImage, fx: List<Fx>, now: Long, s: Double): BufferedImage {
    val img = BufferedImage(card.width, card.height, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color.BLACK; g.fillRect(0, 0, img.width, img.height)
    val (ox, oy) = fx.map { if (now >= it.start) it.cardOffset(now) else 0.0 to 0.0 }.maxByOrNull { kotlin.math.abs(it.first) } ?: (0.0 to 0.0)
    g.drawImage(card, (ox * s).roundToInt(), (oy * s).roundToInt(), null)
    for (f in fx) if (now >= f.start && !f.done(now)) f.draw(g, s, now, card)
    g.dispose()
    return img
}

