package com.ironmonone.app

/**
 * Heart & Soul numbers its species its own way (Treecko is 252, Gen 9 runs to 1572 with the forms among them), and the
 * app's name lists, the bundled sprite pack and every Nat. Dex table number them the Nat. Dex build's way (Treecko 277).
 * The tracker's own map between the two is hns/packsprites.tsv (tools/hns/gen_tracker.py, HnsSprites): a Heart & Soul
 * species to the pack's id for the same Pokemon. This is that map both ways, for the screens that start from a name
 * (Favorites) or a pack id and must reach the game's own species, or the other way round.
 */
object HnsNumbers {
    /** Heart & Soul's id to the Nat. Dex table's (the pack's), or null where the pack has no such Pokemon. */
    fun toPack(hnsId: Int): Int? = com.ironmonone.tracker.HnsSpecies.natDexId(hnsId)

    /**
     * The Nat. Dex table's id to Heart & Soul's: the lowest Heart & Soul id drawn as that Pokemon, its base species where a
     * form the pack lacks is drawn as its base. Null for an id no Heart & Soul species has.
     */
    fun fromPack(packId: Int): Int? = reverse[packId]

    private val reverse: Map<Int, Int> by lazy {
        val out = HashMap<Int, Int>()
        HnsNumbers::class.java.getResourceAsStream("/hns/packsprites.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            for (l in lines) {
                if (l.startsWith("#")) continue
                val t = l.indexOf('\t')
                if (t <= 0) continue
                val hns = l.substring(0, t).toIntOrNull() ?: continue
                val pack = l.substring(t + 1).trim().toIntOrNull() ?: continue
                val had = out[pack]
                if (had == null || hns < had) out[pack] = hns
            }
        }
        out
    }
}
