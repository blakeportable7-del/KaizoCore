package com.ironmonone.tracker.gachamon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A whole PC tracker collection into KaizoCore's. FullCollection.gccg was written by the PC tracker's own
 * GachaMonFileManager.saveCollectionToFile (tools/gachamon/collection_fixture.py, collection-fixture.txt): twelve cards,
 * three of them favorites, case-2 a second time as a favorite, then a version 9 record and a 7-byte tail.
 */
class GachaMonImportTest {
    private val file: ByteArray = javaClass.getResourceAsStream("/gachamon/FullCollection.gccg")!!.readBytes()

    private val codes: Map<String, String> by lazy {
        val lines = javaClass.getResourceAsStream("/gachamon/reference-cases.tsv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.first().split('\t')
        lines.drop(1).associate { l -> val f = header.zip(l.split('\t')).toMap(); f.getValue("name") to f.getValue("code") }
    }
    private fun card(name: String) = GachaMonCodec.fromShareCode(codes.getValue(name))!!

    @Test
    fun `an empty collection takes every card once, favorites kept, and counts what it could not read`() {
        val r = GachaMonImport.merge(emptyList(), file)
        assertEquals(12, r.added.size)
        assertEquals(1, r.already, "case-2 is in the file twice")
        assertEquals(2, r.unreadable, "the version 9 record and the short tail")
        assertEquals("12 added, 1 already in your collection, 2 couldn't be read", r.line)
        val favorites = r.added.filter { it.favorite == 1 }.map { GachaMonImport.identity(it) }.toSet()
        val expected = listOf("golden-charizard", "case-4", "case-7", "case-2").map { GachaMonImport.identity(card(it)) }.toSet()
        assertEquals(expected, favorites, "the three favorites, and case-2, which its second copy marks")
        assertTrue(r.added.all { it.keep == 1 })
    }

    @Test
    fun `cards already in the collection are skipped, and a favorite in the file marks the held copy`() {
        val held = listOf(card("case-1"), card("case-4").copy(favorite = 0, keep = 1, gameWinner = 1))
        val r = GachaMonImport.merge(held, file)
        assertEquals(10, r.added.size)
        assertEquals(3, r.already)
        assertEquals(listOf(held[1]), r.favorited, "case-4 is a favorite in the file")
        assertTrue(r.added.none { GachaMonImport.identity(it) == GachaMonImport.identity(held[0]) })
    }

    @Test
    fun `importing the same file twice adds nothing the second time`() {
        val first = GachaMonImport.merge(emptyList(), file).added
        val again = GachaMonImport.merge(first, file)
        assertEquals(0, again.added.size)
        assertEquals(13, again.already)
        assertEquals(2, again.unreadable)
    }

    @Test
    fun `every card reads back as the reference wrote it`() {
        val r = GachaMonImport.merge(emptyList(), file)
        val names = listOf("golden-charizard") + (1..11).map { "case-$it" }
        assertEquals(names.map { GachaMonImport.identity(card(it)) }, r.added.map { GachaMonImport.identity(it) })
    }

    @Test
    fun `nothing readable is nothing added`() {
        assertEquals(GachaMonImport.Result(emptyList(), 0, 1), GachaMonImport.merge(emptyList(), ByteArray(12)))
        assertEquals(GachaMonImport.Result(emptyList(), 0, 0), GachaMonImport.merge(emptyList(), ByteArray(0)))
    }
}
