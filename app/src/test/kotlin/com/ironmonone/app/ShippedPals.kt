package com.ironmonone.app

import java.io.File

/**
 * The two Walking Pals sets and the Nat. Dex map as the app ships them, read from the assets folder (the unit tests
 * run in app/), and the one lookup over them that the phone builds from the same files.
 */
object ShippedPals {
    private const val ASSETS = "src/main/assets"

    private fun lines(path: String) = File("$ASSETS/$path").readLines(Charsets.UTF_8).asSequence()

    val gen3: Map<String, Map<WalkingPals.Anim, WalkingPals.Sheet>> by lazy { WalkingPals.parse(lines("walkingpals/walkingpals.tsv")) }
    val national: Map<String, Map<WalkingPals.Anim, WalkingPals.Sheet>> by lazy { WalkingPals.parse(lines("walkingpals-nat/walkingpals-nat.tsv")) }
    val natDex: Map<Int, WalkingPals.Pal?> by lazy { WalkingPals.parseNatDexMap(lines("walkingpals-nat/natdex-map.tsv")) }
    val index: WalkingPals.Index by lazy { WalkingPals.Index(gen3, national, natDex) }

    /** The sheet file for one animation of [pal]. */
    fun file(pal: WalkingPals.Pal, anim: WalkingPals.Anim) = File("$ASSETS/${pal.path(anim)}")

    /**
     * A sheet's pixels as ARGB, by the JDK's own decoder (javax.imageio). The unit tests compile against android.jar,
     * which has no javax.imageio, so it is reached by reflection, as StreamTestSupport.decode does; the JVM they run
     * on has it.
     */
    fun pixels(f: File): ArtPixels {
        val img = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, f) ?: error("$f is not a picture")
        val cls = img.javaClass
        val w = cls.getMethod("getWidth").invoke(img) as Int
        val h = cls.getMethod("getHeight").invoke(img) as Int
        val int = Int::class.javaPrimitiveType
        val argb = IntArray(w * h)
        cls.getMethod("getRGB", int, int, int, int, IntArray::class.java, int, int).invoke(img, 0, 0, w, h, argb, 0, w)
        return ArtPixels(w, h, argb)
    }
}
