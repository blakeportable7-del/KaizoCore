package com.ironmonone.pc.fx

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode

/** The preview's and the sandbox's pictures: an animated GIF that loops, and a contact sheet of six moments. */
object FxGif {
    /** Six moments side by side, half size, for a look without playing the GIF. */
    fun sheet(frames: List<BufferedImage>, file: File) {
        val pick = (0 until 6).map { frames[(it * (frames.size - 1)) / 5] }
        val w = pick[0].width / 2; val h = pick[0].height / 2
        val out = BufferedImage(w * 6 + 5 * 4, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color.WHITE; g.fillRect(0, 0, out.width, out.height)
        pick.forEachIndexed { i, f -> g.drawImage(f, i * (w + 4), 0, w, h, null) }
        g.dispose()
        ImageIO.write(out, "png", file)
    }

    /** An animated GIF that loops, each frame [delay] hundredths of a second. */
    fun gif(file: File, frames: List<BufferedImage>, delay: Int) {
        val writer = ImageIO.getImageWritersBySuffix("gif").next()
        ImageIO.createImageOutputStream(file).use { ios ->
            writer.output = ios
            writer.prepareWriteSequence(null)
            for ((i, f) in frames.withIndex()) {
                val type = ImageTypeSpecifier.createFromRenderedImage(f)
                val meta = writer.getDefaultImageMetadata(type, writer.defaultWriteParam)
                val root = meta.getAsTree(meta.nativeMetadataFormatName) as IIOMetadataNode
                fun node(name: String): IIOMetadataNode = (0 until root.length).map { root.item(it) as IIOMetadataNode }.firstOrNull { it.nodeName == name }
                    ?: IIOMetadataNode(name).also { root.appendChild(it) }
                node("GraphicControlExtension").apply {
                    setAttribute("disposalMethod", "none"); setAttribute("userInputFlag", "FALSE"); setAttribute("transparentColorFlag", "FALSE")
                    setAttribute("delayTime", "$delay"); setAttribute("transparentColorIndex", "0")
                }
                if (i == 0) node("ApplicationExtensions").appendChild(IIOMetadataNode("ApplicationExtension").apply {
                    setAttribute("applicationID", "NETSCAPE"); setAttribute("authenticationCode", "2.0"); userObject = byteArrayOf(1, 0, 0)
                })
                meta.setFromTree(meta.nativeMetadataFormatName, root)
                writer.writeToSequence(IIOImage(f, null, meta), null)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }
}
