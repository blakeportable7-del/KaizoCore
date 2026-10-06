package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoritesTest {
    @Test
    fun `three slots round-trip through the file text and read back as the tracker's set`() {
        assertEquals(listOf("", "", ""), Favorites.slots(""))
        assertNull(Favorites.line(emptyList()))
        val text = Favorites.text(listOf("Scyther", " gengar", ""))
        assertEquals("Scyther,gengar,", text)
        assertEquals(listOf("Scyther", "gengar", ""), Favorites.slots(text))
        // PrepStore.loadFavorites splits on the same separators and lowercases.
        val names = text.split('\n', ',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        assertEquals(listOf("scyther", "gengar"), names)
        assertEquals("FAVORITES: SCYTHER / GENGAR", Favorites.line(names))
    }

    @Test
    fun `names resolve through the natdex table, case-insensitively, and nonsense does not`() {
        assertEquals(123, Favorites.idOf("scyther"))
        assertEquals(94, Favorites.idOf("GENGAR"))
        // The table is the Gen 3 build's internal numbering past 251, so Lucario is 473 here, not 448.
        assertEquals(473, Favorites.idOf(" Lucario "))
        assertNull(Favorites.idOf("Blake"))
    }

    /**
     * Typing narrows the list: "s" is many, "sno" puts the names that start with it first, "snorlax" exactly typed is
     * none. Since 2026-10-06 the log search's ranking (LogSuggest): after the names that start with what is typed come a
     * word that does, a name that contains it and one a slip away (HnsPoolFavoritesTest has those).
     */
    @Test
    fun `suggestions start with what was typed, in dex order, and narrow`() {
        val s = Favorites.suggest("s")
        kotlin.test.assertEquals(8, s.size)
        kotlin.test.assertTrue(s.all { it.lowercase().startsWith("s") })
        val sn = Favorites.suggest("sno", limit = 50)
        val starts = sn.takeWhile { it.lowercase().startsWith("sno") }
        kotlin.test.assertTrue(starts.size >= 3 && sn.drop(starts.size).none { it.lowercase().startsWith("sno") }, sn.toString())
        kotlin.test.assertEquals("Snorlax", Favorites.suggest("snorl").first())
        kotlin.test.assertTrue(Favorites.suggest("snorlax").isEmpty())
        kotlin.test.assertTrue(Favorites.suggest("").isEmpty())
        // Dex order, not alphabetical: Bulbasaur 1, Blastoise 9, Butterfree 12.
        kotlin.test.assertEquals(listOf("Bulbasaur", "Blastoise", "Butterfree"), Favorites.suggest("b").take(3))
    }

    /** Three on the Gen 1 to 3 trackers, four on a Gen 4 DS game, five on Gen 5; the list stops at the game's dex. */
    @Test
    fun `the table's empty none rows are never offered or named`() {
        assertEquals(false, Favorites.suggest("No").any { it.equals("None", ignoreCase = true) }, Favorites.suggest("No").toString())
        assertNull(Favorites.idOf("none"))
        assertEquals(true, Favorites.suggest("No").any { it == "Noctowl" })
    }

    @Test
    fun `slot count and dex cap follow the game`() {
        val K = com.ironmonone.core.RomKind
        kotlin.test.assertEquals(3, Favorites.slotCount(K.EMERALD_U)); kotlin.test.assertEquals(3, Favorites.slotCount(K.RED_U)); kotlin.test.assertEquals(3, Favorites.slotCount(null))
        kotlin.test.assertEquals(4, Favorites.slotCount(K.PLATINUM_U)); kotlin.test.assertEquals(5, Favorites.slotCount(K.BLACK2_U))
        kotlin.test.assertEquals(9, Favorites.slotCount(K.EMERALD_NATDEX_121)); kotlin.test.assertEquals(9, Favorites.slotCount(K.FIRERED_NATDEX_121))
        kotlin.test.assertEquals(151, Favorites.maxDex(K.RED_U)); kotlin.test.assertEquals(251, Favorites.maxDex(K.CRYSTAL_U))
        kotlin.test.assertEquals(386, Favorites.maxDex(K.FIRERED_U_V10)); kotlin.test.assertEquals(493, Favorites.maxDex(K.PLATINUM_U)); kotlin.test.assertEquals(649, Favorites.maxDex(K.WHITE2_U))
        kotlin.test.assertEquals(Int.MAX_VALUE, Favorites.maxDex(K.EMERALD_NATDEX_121))
        kotlin.test.assertEquals(listOf("Mew", "Mewtwo"), Favorites.suggest("mew", maxId = 151).take(2))
        kotlin.test.assertTrue(Favorites.suggest("mew", maxId = 151).all { Favorites.nationalOf(Favorites.idOf(it)!!)!! <= 151 })
        kotlin.test.assertFalse("Snivy" in Favorites.suggest("sni", maxId = 386))
        kotlin.test.assertEquals("Snivy", Favorites.suggest("sni", maxId = 649).first())
        kotlin.test.assertEquals(5, Favorites.slots("a,b", 5).size)
    }

    /**
     * The caps count National Dex numbers, and the table's own ids are not those past 251 (2026-09-30): 25 species of
     * every generation were marked not in the game, Ralts to Rayquaza on an Emerald among them.
     */
    @Test
    fun `every species a game has can be a favorite, and none it does not`() {
        val gen3 = 386; val gen4 = 493; val gen5 = 649
        for (n in listOf("Ralts", "Gardevoir", "Bagon", "Salamence", "Beldum", "Metagross", "Kyogre", "Groudon", "Rayquaza", "Jirachi", "Deoxys", "Chimecho", "Treecko"))
            kotlin.test.assertTrue(Favorites.inGame(n, gen3), "$n is in Emerald")
        kotlin.test.assertTrue("Rayquaza" in Favorites.suggest("rayq", maxId = gen3))
        kotlin.test.assertFalse(Favorites.inGame("Turtwig", gen3))
        for (n in listOf("Yanmega", "Gallade", "Dialga", "Arceus")) kotlin.test.assertTrue(Favorites.inGame(n, gen4), "$n is in Platinum")
        kotlin.test.assertFalse(Favorites.inGame("Victini", gen4))
        for (n in listOf("Bisharp", "Hydreigon", "Kyurem", "Genesect")) kotlin.test.assertTrue(Favorites.inGame(n, gen5), "$n is in Black 2")
        kotlin.test.assertFalse(Favorites.inGame("Chespin", gen5))
        kotlin.test.assertTrue(Favorites.inGame("Chespin", Int.MAX_VALUE), "a Nat. Dex build has every generation")
        kotlin.test.assertFalse(Favorites.inGame("Blake", Int.MAX_VALUE))
        // The table's ids against the national numbers, at each seam.
        val id = { n: String -> Favorites.idOf(n)!! }
        kotlin.test.assertEquals(252, Favorites.nationalOf(id("Treecko"))); kotlin.test.assertEquals(386, Favorites.nationalOf(id("Deoxys")))
        kotlin.test.assertEquals(358, Favorites.nationalOf(id("Chimecho"))); kotlin.test.assertEquals(387, Favorites.nationalOf(id("Turtwig")))
        kotlin.test.assertEquals(649, Favorites.nationalOf(id("Genesect"))); kotlin.test.assertEquals(1025, Favorites.nationalOf(id("Pecharunt")))
        // Every Hoenn id has its own national number, all of 252 to 386.
        val hoenn = (277..411).mapNotNull { Favorites.nationalOf(it) }
        kotlin.test.assertEquals((252..386).toList(), hoenn.sorted())
    }

    /**
     * The other way, for Walking Pals on a game that counts in national numbers (2026-09-30): each of 252 to 386 back to
     * its own Gen 3 id, the species of that name, and the ids either side of Hoenn by their offsets.
     */
    @Test
    fun `every Hoenn national number goes back to its own internal id`() {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val nationalNames = HashMap<Int, String>()
        com.ironmonone.tracker.nds.NdsLogData::class.java.getResourceAsStream("/nds/evo-methods.tsv")!!.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith("#")) continue
                val c = line.split('\t')
                val n = c.getOrNull(0)?.toIntOrNull() ?: continue
                c.getOrNull(1)?.let { nationalNames[n] = it }
            }
        }
        val nameOf = Favorites.namesInOrder.toMap()
        val ids = HashSet<Int>()
        for (n in 252..386) {
            val id = kotlin.test.assertNotNull(Favorites.fromNational(n), "national $n")
            kotlin.test.assertTrue(id in 277..411, "national $n went to $id")
            kotlin.test.assertEquals(key(nationalNames.getValue(n)), key(nameOf.getValue(id)), "national $n")
            kotlin.test.assertEquals(n, Favorites.nationalOf(id), "national $n, there and back")
            kotlin.test.assertTrue(ids.add(id), "national $n shares $id")
        }
        kotlin.test.assertEquals((277..411).toSet(), ids)
        // Known pairs, Gen 3's own order against the National Dex's.
        kotlin.test.assertEquals(277, Favorites.fromNational(252), "Treecko")
        kotlin.test.assertEquals(392, Favorites.fromNational(280), "Ralts")
        kotlin.test.assertEquals(411, Favorites.fromNational(358), "Chimecho")
        kotlin.test.assertEquals(410, Favorites.fromNational(386), "Deoxys")
        // Either side: Kanto and Johto as they are, from Turtwig 25 places on.
        kotlin.test.assertEquals(1, Favorites.fromNational(1)); kotlin.test.assertEquals(251, Favorites.fromNational(251))
        kotlin.test.assertEquals(Favorites.idOf("Turtwig"), Favorites.fromNational(387))
        kotlin.test.assertEquals(Favorites.idOf("Pecharunt"), Favorites.fromNational(1025))
        kotlin.test.assertNull(Favorites.fromNational(0)); kotlin.test.assertNull(Favorites.fromNational(1026))
    }

    /** The Play as your Pokemon list shows the Nat. Dex forms by these names, so they are spelled as the table spells them. */
    @Test
    fun `names are spelled as the table spells them`() {
        val name = Favorites.namesInOrder.toMap()
        kotlin.test.assertEquals("Ho-Oh", name[250])
        kotlin.test.assertEquals("Mr. Mime", name[122])
        kotlin.test.assertEquals("Porygon-Z", name[Favorites.idOf("porygon-z")])
        kotlin.test.assertEquals("Type: Null", name[Favorites.idOf("type: null")])
        kotlin.test.assertEquals("Charizard-X", name[1052])
        kotlin.test.assertEquals("Rotom-Wash", name[1174])
        kotlin.test.assertEquals("Bulbasaur", name[1])
    }
}
