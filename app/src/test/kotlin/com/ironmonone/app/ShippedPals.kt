package com.ironmonone.app

import java.io.File

/**
 * The two Walking Pals sets and the Nat. Dex map as the app ships them, read from the assets folder (the unit tests
 * run in app/), MaxDex's ids read from the tracker's species tables as the phone reads them, and the one lookup over
 * them that the phone builds from the same files.
 */
object ShippedPals {
    private const val ASSETS = "src/main/assets"

    private fun lines(path: String) = File("$ASSETS/$path").readLines(Charsets.UTF_8).asSequence()

    val gen3: Map<String, Map<WalkingPals.Anim, WalkingPals.Sheet>> by lazy { WalkingPals.parse(lines("walkingpals/walkingpals.tsv")) }
    val national: Map<String, Map<WalkingPals.Anim, WalkingPals.Sheet>> by lazy { WalkingPals.parse(lines("walkingpals-nat/walkingpals-nat.tsv")) }
    val natDex: Map<Int, WalkingPals.Pal?> by lazy { WalkingPals.parseNatDexMap(lines("walkingpals-nat/natdex-map.tsv")) }
    val maxDex: Map<Int, Int> by lazy { WalkingPals.maxDexIds() }
    /** Each set's shinies, as WalkingPals.index reads them. */
    val shinies: Map<WalkingPals.Pack, WalkingPals.Shinies> by lazy {
        WalkingPals.Pack.entries.associateWith { p ->
            WalkingPals.Shinies(WalkingPals.parseShinyColors(lines("${p.dir}/shiny-colors.tsv")), WalkingPals.parse(lines("${p.dir}/shiny.tsv")))
        }
    }
    val index: WalkingPals.Index by lazy { WalkingPals.Index(gen3, national, natDex, maxDex, shinies = shinies) }

    /** The sheet file for one animation of [pal]. */
    fun file(pal: WalkingPals.Pal, anim: WalkingPals.Anim) = File("$ASSETS/${pal.path(anim)}")

    /** A file in the assets folder, by its asset path. */
    fun asset(path: String) = File("$ASSETS/$path")

    /**
     * [anim] of [pal] as the phone draws it (WalkingPals.bitmap): the file [WalkingPals.Index.art] names, recolored with the
     * colors it names, if any.
     */
    fun drawn(pal: WalkingPals.Pal, anim: WalkingPals.Anim, ix: WalkingPals.Index = index): ArtPixels {
        val art = ix.art(pal, anim)
        return pixels(asset(art.path)).also { art.colors?.apply(it.argb) }
    }

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
