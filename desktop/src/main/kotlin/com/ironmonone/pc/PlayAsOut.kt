package com.ironmonone.pc

import com.ironmonone.app.SheetSet
import com.ironmonone.app.SpriteArt
import com.ironmonone.app.SpriteIsMeLogic
import com.ironmonone.app.SpriteIsMeSettings
import com.ironmonone.app.WalkingPals
import com.ironmonone.app.pcDesktopCtx
import com.ironmonone.tracker.OverworldAddresses
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * "Play as your Pokemon" on PC (Sprite Is Me by UTDZac, MIT, as the phone's SpriteIsMe has it): KaizoCore PC decides
 * WHO the player walks as and hands kaizocore-hns.lua the sheets, which does the per-frame part in BizHawk the way the
 * phone's sprite_core.h does it inside the emulator: read the player's sprite out of the game's RAM, hide the trainer
 * in the game's own OAM buffer, and draw the chosen frame on the game at the box the game itself places the player in.
 *
 * The choice is the phone's ([SpriteIsMeLogic.choose]): the lead (the first Pokemon that is not an egg, shiny and in
 * its form as the game draws it), "Always use" a picked Pokemon (its shiny if asked), or the player's own picture or
 * sheet set, kept in the KaizoCore PC folder ("play as/"). The settings are the phone's file format ("play as.txt"),
 * one for every run. The Walking Pals sheets are the tracker's (PMD Sprite Collab, credited in the README).
 *
 * playas.txt, read by the script about twice a second: on, the game's addresses (Overworld.HEARTSOUL_KAIZO, the
 * build's own symbols), the lead's HP and sleep, and one line an animation: file, frame w, h, offset x, y, rows,
 * the frame lengths. A sheet file is named by what is in it, so BizHawk's image cache never shows an old one.
 */
class PlayAsOut(private val dir: File, private val home: File, private val say: (String) -> Unit) {
    private var written: String? = null

    val ownDir: File get() = File(home, "play as")
    val pictureFile: File get() = File(ownDir, "picture.png")
    fun sheetFile(a: WalkingPals.Anim) = File(File(ownDir, "sheet"), a.key + ".png")

    fun ownReady(): Boolean = when (SpriteIsMeSettings.own) {
        SpriteIsMeSettings.Own.PICTURE -> pictureFile.isFile
        SpriteIsMeSettings.Own.SHEET -> WalkingPals.Anim.entries.any { sheetFile(it).isFile }
        SpriteIsMeSettings.Own.NONE -> false
    }

    /** The player's [lead] now (null: no Pokemon yet); writes playas.txt when what it says changed. */
    fun update(lead: SpriteIsMeLogic.Lead?, a: OverworldAddresses) {
        val text = runCatching { describe(lead, a) }.getOrElse { say("play as: $it"); "on=0\n" }
        if (text == written) return
        written = text
        val tmp = File(dir, "playas.txt.tmp")
        tmp.writeText(text)
        val f = File(dir, "playas.txt")
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun clear() { written = null; File(dir, "playas.txt").delete() }

    private fun describe(lead: SpriteIsMeLogic.Lead?, a: OverworldAddresses): String {
        val s = SpriteIsMeSettings
        if (!s.on) return "on=0\n"
        val index = WalkingPals.index(pcDesktopCtx)
        val choice = SpriteIsMeLogic.choose(s.who, s.always, s.own, ownReady(), lead, { id, dex -> index.find(id, dex) }, s.alwaysShiny) { p, l -> index.look(p, l) }
        val head = StringBuilder()
        head.append("on=1\n")
        head.append("main=${a.main}\noam=${a.oamBufferOffset}\ncb2=${a.cb2Overworld}\ncb2basic=${a.cb2OverworldBasic}\n")
        head.append("avatar=${a.playerAvatar}\nsprites=${a.sprites}\noffx=${a.coordOffsetX}\noffy=${a.coordOffsetY}\n")
        head.append("unfaded=${a.plttUnfaded}\nfaded=${a.plttFaded}\nobjects=${a.objectEvents}\n")
        val l = choice.lead
        head.append("hp=${l?.hp ?: 1}\nasleep=${if (l?.asleep == true) 1 else 0}\n")
        when (choice.source) {
            SpriteIsMeLogic.Source.NONE -> return "on=0\n"
            SpriteIsMeLogic.Source.PAL -> {
                val pal = choice.pal ?: return "on=0\n"
                val sheets = index.sheets(pal) ?: return "on=0\n"
                head.append("source=pal\nwho=${pal.key}${if (pal.shiny) " shiny" else ""}\n")
                for ((anim, sheet) in sheets) {
                    val art = index.art(pal, anim)
                    val name = "playas-${pal.pack.name.lowercase()}-${pal.key}${if (pal.shiny) "-s" else ""}-${anim.key}.png"
                    val out = File(dir, name)
                    if (!out.isFile) {
                        val img = com.ironmonone.app.PcDesktopCtx::class.java.getResourceAsStream("/assets/${art.path}")?.use { ImageIO.read(it) } ?: continue
                        val argb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
                        art.colors?.let { c ->
                            val px = IntArray(argb.width * argb.height)
                            argb.getRGB(0, 0, argb.width, argb.height, px, 0, argb.width)
                            c.apply(px)
                            argb.setRGB(0, 0, argb.width, argb.height, px, 0, argb.width)
                        }
                        ImageIO.write(argb, "png", out)
                    }
                    val rows = runCatching { ImageIO.createImageInputStream(out).use { iis -> ImageIO.getImageReaders(iis).next().let { r -> r.input = iis; r.getHeight(0) } } }.getOrDefault(sheet.h) / sheet.h
                    head.append(line(anim, name, sheet, rows))
                }
            }
            SpriteIsMeLogic.Source.PICTURE -> {
                val img = ImageIO.read(pictureFile) ?: return "on=0\n"
                val px = IntArray(img.width * img.height)
                val argbImg = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
                argbImg.getRGB(0, 0, img.width, img.height, px, 0, img.width)
                val fitted = SpriteArt.fit(com.ironmonone.app.ArtPixels(img.width, img.height, px))
                val name = "playas-own-${pictureFile.lastModified()}-${pictureFile.length()}.png"
                val out = File(dir, name)
                if (!out.isFile) {
                    val o = BufferedImage(fitted.pixels.w * 2, fitted.pixels.h, BufferedImage.TYPE_INT_ARGB)
                    o.setRGB(0, 0, fitted.pixels.w, fitted.pixels.h, fitted.pixels.argb, 0, fitted.pixels.w)
                    // The same picture flipped beside it: facing left (the phone flips it, SpriteArt.flipH).
                    val flipped = SpriteArt.flipH(fitted.pixels)
                    o.setRGB(fitted.pixels.w, 0, flipped.w, flipped.h, flipped.argb, 0, flipped.w)
                    ImageIO.write(o, "png", out)
                }
                head.append("source=picture\npicture=$name,${fitted.pixels.w},${fitted.pixels.h},${fitted.ox},${fitted.oy}\n")
            }
            SpriteIsMeLogic.Source.SHEET -> {
                val sizes = WalkingPals.Anim.entries.mapNotNull { an -> sheetFile(an).takeIf { it.isFile }?.let { f -> ImageIO.read(f)?.let { an to (it.width to it.height) } } }.toMap()
                val sheets = SheetSet.sheets(sizes, s.spec) ?: return "on=0\n"
                head.append("source=sheet\n")
                for ((anim, sheet) in sheets) {
                    val f = sheetFile(anim)
                    val name = "playas-sheet-${anim.key}-${f.lastModified()}-${f.length()}.png"
                    val out = File(dir, name)
                    if (!out.isFile) f.copyTo(out, overwrite = true)
                    head.append(line(anim, name, sheet, (sizes[anim]?.second ?: sheet.h) / sheet.h))
                }
            }
        }
        return head.toString()
    }

    private fun line(anim: WalkingPals.Anim, file: String, s: WalkingPals.Sheet, rows: Int) =
        "${anim.key}=$file,${s.w},${s.h},${s.x},${s.y},${rows.coerceAtLeast(1)},${s.durations.joinToString(" ")}\n"
}
