package com.ironmonone.app

import java.io.ByteArrayOutputStream
import java.io.File
import com.ironmonone.tracker.nds.NdsRunOver
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The player's own game over lines (DeathQuotes, 2026-09-30): the pool they make with the built-in lines, the
 * shuffled deck that picks from it, the list's edits, and the file that keeps it. The screens that draw it are
 * read as source in DeathQuotesWiringTest, since a Compose screen cannot run here.
 */
class DeathQuotesTest {
    private val dir: File = Files.createTempDirectory("deathquotes").toFile()
    private val builtIn = listOf("Boom!", "That one hurt!", "Harsh blow!")

    @BeforeTest fun fresh() { DeathQuotes.reset() }
    @AfterTest fun clean() { DeathQuotes.reset(); dir.deleteRecursively() }

    // ------------------------------------------------------------------ the pool

    @Test
    fun `with no lines of its own the pool is the built-in lines, whatever the switches say`() {
        assertEquals(builtIn, DeathQuotes.pool(builtIn))
        DeathQuotes.useOwn(false); DeathQuotes.useBuiltIn(false)
        assertEquals(builtIn, DeathQuotes.pool(builtIn), "nothing of the player's to use, so never an empty pool")
        DeathQuotes.useOwn(true)
        assertEquals(builtIn, DeathQuotes.pool(builtIn))
    }

    @Test
    fun `the player's lines go on top of the built-in ones`() {
        assertEquals(DeathQuotes.Edit.OK, DeathQuotes.add("Never trust a Magikarp."))
        assertEquals(DeathQuotes.Edit.OK, DeathQuotes.add("Fair enough."))
        assertEquals(builtIn + listOf("Never trust a Magikarp.", "Fair enough."), DeathQuotes.pool(builtIn))
    }

    @Test
    fun `the first switch takes the player's lines out and the second takes the built-in ones out`() {
        DeathQuotes.add("Fair enough.")
        DeathQuotes.useOwn(false)
        assertEquals(builtIn, DeathQuotes.pool(builtIn), "switched off")
        DeathQuotes.useOwn(true); DeathQuotes.useBuiltIn(false)
        assertEquals(listOf("Fair enough."), DeathQuotes.pool(builtIn), "only the player's own")
        DeathQuotes.useBuiltIn(true)
        assertEquals(builtIn + "Fair enough.", DeathQuotes.pool(builtIn))
    }

    @Test
    fun `a win never gets the player's lines`() {
        DeathQuotes.add("Fair enough.")
        DeathQuotes.useBuiltIn(false)
        assertEquals(builtIn, DeathQuotes.pool(builtIn, forLoss = false))
    }

    @Test
    fun `a line that would be in the pool twice, capitals aside, is in it once`() {
        DeathQuotes.add("boom!")
        DeathQuotes.add("A new one.")
        val pool = DeathQuotes.pool(builtIn)
        assertEquals(builtIn + "A new one.", pool, "the built-in Boom! stays, the player's copy of it is dropped")
        assertEquals(pool.size, pool.map { it.lowercase() }.toSet().size)
    }

    // ---------------------------------------------------------------- the picking

    @Test
    fun `every line comes up once before any comes up again`() {
        val pool = (1..7).map { "line $it" }
        val deck = QuoteDeck(Random(11))
        val drawn = List(pool.size * 4) { deck.next(pool) }
        for (pass in drawn.chunked(pool.size)) assertEquals(pool.toSet(), pass.toSet(), "a pass is every line exactly once: $pass")
    }

    @Test
    fun `the passes are shuffled, not the same order each time`() {
        val pool = (1..8).map { "line $it" }
        val deck = QuoteDeck(Random(5))
        val passes = List(4) { List(pool.size) { deck.next(pool) } }
        assertEquals(4, passes.toSet().size, "four passes of eight lines, four different orders")
        assertNotEquals(pool, passes.first(), "and the first is not the pool's own order")
        // A different seed, a different first pass: it is the random that picks.
        fun firstPass(seed: Int) = QuoteDeck(Random(seed)).let { d -> List(pool.size) { d.next(pool) } }
        assertNotEquals(firstPass(1), firstPass(2))
    }

    @Test
    fun `a new pass never starts with the line the last one ended on`() {
        for (size in 2..6) {
            val pool = (1..size).map { "line $it" }
            for (seed in 0 until 300) {
                val deck = QuoteDeck(Random(seed))
                val drawn = List(size * 5) { deck.next(pool) }
                for (i in 1 until drawn.size) assertNotEquals(drawn[i - 1], drawn[i], "size $size seed $seed at $i: $drawn")
            }
        }
    }

    @Test
    fun `one line comes up every time, two alternate, and nothing to pick from is refused`() {
        val one = QuoteDeck(Random(1))
        assertEquals(List(5) { "only" }, List(5) { one.next(listOf("only")) })
        val two = QuoteDeck(Random(1))
        val drawn = List(6) { two.next(listOf("a", "b")) }
        assertEquals(setOf("a", "b"), drawn.take(2).toSet())
        assertEquals(List(6) { i -> drawn[i % 2] }, drawn, "a pass may not start with the line the last one ended on, so two lines alternate")
        assertFailsWith<IllegalArgumentException> { QuoteDeck().next(emptyList()) }
    }

    @Test
    fun `a pool that changed starts a new pass and still does not repeat the line on screen`() {
        val pool = (1..4).map { "line $it" }
        for (seed in 0 until 200) {
            val deck = QuoteDeck(Random(seed))
            val on = deck.next(pool)
            val next = deck.next(pool + "a new line")
            assertNotEquals(on, next, "seed $seed")
        }
    }

    // ------------------------------------------------- one line per run, box and card alike

    @Test
    fun `a run gets one line, the same however often it is asked for, and the next run gets another`() {
        DeathQuotes.random = Random(3)
        val first = DeathQuotes.shown(DeathQuotes.PC_SOURCE, 7, builtIn)
        assertTrue(first in builtIn)
        repeat(5) { assertEquals(first, DeathQuotes.shown(DeathQuotes.PC_SOURCE, 7, builtIn), "the box and the card say one thing") }
        val second = DeathQuotes.shown(DeathQuotes.PC_SOURCE, 8, builtIn)
        val third = DeathQuotes.shown(DeathQuotes.PC_SOURCE, 9, builtIn)
        assertEquals(builtIn.toSet(), setOf(first, second, third), "three runs, three lines, before any comes round again")
    }

    @Test
    fun `a tap on the team draws another line, which is then the run's line for the card too`() {
        DeathQuotes.random = Random(4)
        val a = DeathQuotes.shown(DeathQuotes.PC_SOURCE, 1, builtIn)
        val b = DeathQuotes.reroll(DeathQuotes.PC_SOURCE, 1, builtIn)
        assertNotEquals(a, b)
        assertEquals(b, DeathQuotes.shown(DeathQuotes.PC_SOURCE, 1, builtIn))
        val c = DeathQuotes.reroll(DeathQuotes.PC_SOURCE, 1, builtIn)
        assertEquals(builtIn.toSet(), setOf(a, b, c), "the deck deals every line before it repeats one")
    }

    @Test
    fun `each way a DS run can end has its own deck, and the Game Boy and Game Boy Advance share one`() {
        DeathQuotes.random = Random(9)
        val shedinja = listOf("s1", "s2")
        val standard = listOf("t1", "t2", "t3")
        assertNotEquals(DeathQuotes.dsSource(NdsRunOver.SHEDINJA), DeathQuotes.dsSource(NdsRunOver.STANDARD))
        assertNotEquals(DeathQuotes.PC_SOURCE, DeathQuotes.dsSource(NdsRunOver.STANDARD))
        // The Game Boy list is dealt three lines over three runs, with DS runs of other causes in between.
        val pc = ArrayList<String>()
        pc += DeathQuotes.shown(DeathQuotes.PC_SOURCE, 1, builtIn)
        assertTrue(DeathQuotes.shown(DeathQuotes.dsSource(NdsRunOver.SHEDINJA), 11, shedinja) in shedinja)
        pc += DeathQuotes.shown(DeathQuotes.PC_SOURCE, 2, builtIn)
        assertTrue(DeathQuotes.shown(DeathQuotes.dsSource(NdsRunOver.STANDARD), 12, standard) in standard)
        pc += DeathQuotes.shown(DeathQuotes.PC_SOURCE, 3, builtIn)
        assertEquals(builtIn.toSet(), pc.toSet(), "the other decks did not take lines from this one: $pc")
    }

    @Test
    fun `the player's lines are drawn from once they are added, and never for a win`() {
        DeathQuotes.random = Random(2)
        DeathQuotes.useBuiltIn(false)
        DeathQuotes.add("Mine.")
        assertEquals("Mine.", DeathQuotes.shown(DeathQuotes.PC_SOURCE, 1, builtIn), "only the player's own")
        assertTrue(DeathQuotes.shown(DeathQuotes.PC_SOURCE, 1, builtIn, forLoss = false) in builtIn, "a win keeps the game's own message")
    }

    // ---------------------------------------------------------------- the list

    @Test
    fun `lines are cleaned as they are kept`() {
        assertEquals("a b c", DeathQuotes.clean("  a\tb\n c  "))
        assertEquals("one line", DeathQuotes.clean("one\r\nline"))
        assertEquals("a b", DeathQuotes.clean("a\u00a0\u00a0b"), "a no-break space is a space")
        assertEquals(null, DeathQuotes.clean("   "))
        assertEquals(null, DeathQuotes.clean("\n\t\r"))
        assertEquals(null, DeathQuotes.clean(""))
        val long = DeathQuotes.clean("word ".repeat(40))!!
        assertTrue(long.length <= DeathQuotes.MAX_LENGTH && !long.endsWith(" "), "cut at ${DeathQuotes.MAX_LENGTH}, no space left on the end")
        assertEquals(DeathQuotes.MAX_LENGTH, DeathQuotes.clean("x".repeat(500))!!.length)
    }

    @Test
    fun `an add says what stopped it, capitals and spacing aside`() {
        assertEquals(DeathQuotes.Edit.EMPTY, DeathQuotes.add("   "))
        assertEquals(DeathQuotes.Edit.OK, DeathQuotes.add("Fair enough."))
        assertEquals(DeathQuotes.Edit.DUPLICATE, DeathQuotes.add("fair  ENOUGH."))
        assertEquals(listOf("Fair enough."), DeathQuotes.lines)
        for (i in 2..DeathQuotes.MAX_LINES) assertEquals(DeathQuotes.Edit.OK, DeathQuotes.add("line $i"))
        assertEquals(DeathQuotes.MAX_LINES, DeathQuotes.lines.size)
        assertEquals(DeathQuotes.Edit.FULL, DeathQuotes.add("one too many"))
        assertEquals(DeathQuotes.MAX_LINES, DeathQuotes.lines.size)
    }

    @Test
    fun `an edit replaces the line in place and refuses only a copy of another line`() {
        DeathQuotes.add("one"); DeathQuotes.add("two"); DeathQuotes.add("three")
        assertEquals(DeathQuotes.Edit.OK, DeathQuotes.replace(1, "  2nd  "))
        assertEquals(listOf("one", "2nd", "three"), DeathQuotes.lines)
        assertEquals(DeathQuotes.Edit.DUPLICATE, DeathQuotes.replace(1, "THREE"))
        assertEquals(DeathQuotes.Edit.OK, DeathQuotes.replace(2, "THREE"), "a line may change its own capitals")
        assertEquals(listOf("one", "2nd", "THREE"), DeathQuotes.lines)
        assertEquals(DeathQuotes.Edit.EMPTY, DeathQuotes.replace(0, "  "))
        assertEquals(DeathQuotes.Edit.EMPTY, DeathQuotes.replace(9, "x"), "no such line")
        assertEquals(listOf("one", "2nd", "THREE"), DeathQuotes.lines)
    }

    @Test
    fun `a remove takes the line out, the ones after it move up, and a wrong index does nothing`() {
        DeathQuotes.add("one"); DeathQuotes.add("two"); DeathQuotes.add("three")
        DeathQuotes.remove(1)
        assertEquals(listOf("one", "three"), DeathQuotes.lines)
        DeathQuotes.remove(5); DeathQuotes.remove(-1)
        assertEquals(listOf("one", "three"), DeathQuotes.lines)
    }

    // ---------------------------------------------------------------- the file

    private fun file() = File(dir, "prep/death-quotes.txt")

    @Test
    fun `the switches and lines come back from the file, accents and all`() {
        DeathQuotes.load(file())
        DeathQuotes.add("Pok\u00e9mon are people too.")
        DeathQuotes.add("Second line")
        DeathQuotes.useBuiltIn(false)
        DeathQuotes.useOwn(false)
        DeathQuotes.reset()
        assertEquals(emptyList(), DeathQuotes.lines)
        DeathQuotes.load(file())
        assertEquals(listOf("Pok\u00e9mon are people too.", "Second line"), DeathQuotes.lines)
        assertFalse(DeathQuotes.enabled); assertFalse(DeathQuotes.keepBuiltIn)
        // Kept as UTF-8, one line= per line.
        assertEquals("KaizoCore game over lines\nenabled=false\nkeepBuiltIn=false\nline=Pok\u00e9mon are people too.\nline=Second line\n", file().readText(Charsets.UTF_8))
    }

    @Test
    fun `no file, an empty file and a file of junk all read as no lines of the player's own`() {
        DeathQuotes.load(file())
        assertTrue(DeathQuotes.enabled && DeathQuotes.keepBuiltIn); assertEquals(emptyList(), DeathQuotes.lines)
        file().parentFile.mkdirs(); file().writeText("")
        DeathQuotes.load(file())
        assertEquals(emptyList(), DeathQuotes.lines); assertTrue(DeathQuotes.enabled && DeathQuotes.keepBuiltIn)
        file().writeBytes(ByteArray(3000) { (it * 37 % 256).toByte() })
        DeathQuotes.load(file())
        assertEquals(emptyList(), DeathQuotes.lines)
        assertTrue(DeathQuotes.enabled && DeathQuotes.keepBuiltIn, "junk changes no setting")
        // A directory where the file should be: unreadable, and still no failure.
        val asDir = File(dir, "asdir"); asDir.mkdirs()
        DeathQuotes.load(asDir)
        assertEquals(emptyList(), DeathQuotes.lines)
    }

    @Test
    fun `corrupt lines are skipped and the good ones kept`() {
        file().parentFile.mkdirs()
        val long = "a".repeat(150)
        file().writeText(
            listOf(
                "KaizoCore game over lines", "enabled=maybe", "keepBuiltIn=false", "garbage with no equals", "line=", "line=    ",
                "line=Good one", "line=good ONE", "line=tab\tinside", "line=$long", "=line=x", "xline=nope", "enabled=false", "",
            ).joinToString("\r\n"),   // a file edited on a PC has CR LF
            Charsets.UTF_8,
        )
        DeathQuotes.load(file())
        assertFalse(DeathQuotes.enabled, "the last good value wins; maybe changed nothing")
        assertFalse(DeathQuotes.keepBuiltIn)
        assertEquals(listOf("Good one", "tab inside", "a".repeat(DeathQuotes.MAX_LENGTH)), DeathQuotes.lines)
    }

    @Test
    fun `a value that is not true or false leaves the switch where it was`() {
        file().parentFile.mkdirs()
        file().writeText("enabled=yes\nkeepBuiltIn=1\nline=Fine\n")
        DeathQuotes.load(file())
        assertTrue(DeathQuotes.enabled && DeathQuotes.keepBuiltIn)
        assertEquals(listOf("Fine"), DeathQuotes.lines)
    }

    @Test
    fun `a file with more lines than the list holds keeps the first ones`() {
        file().parentFile.mkdirs()
        file().writeText((1..250).joinToString("\n") { "line=line $it" } + "\n")
        DeathQuotes.load(file())
        assertEquals(DeathQuotes.MAX_LINES, DeathQuotes.lines.size)
        assertEquals("line 1", DeathQuotes.lines.first()); assertEquals("line ${DeathQuotes.MAX_LINES}", DeathQuotes.lines.last())
    }

    @Test
    fun `a file of hundreds of thousands of lines keeps only as many as the list holds`() {
        file().parentFile.mkdirs()
        val big = StringBuilder("enabled=true\n")
        var i = 0
        while (big.length < 400_000) { big.append("line=").append("entry ").append(i++).append('\n') }
        file().writeText(big.toString())
        DeathQuotes.load(file())
        assertEquals(DeathQuotes.MAX_LINES, DeathQuotes.lines.size)
        assertTrue(DeathQuotes.enabled)
    }

    @Test
    fun `a file is read as far as the limit and no further, whatever follows`() {
        file().parentFile.mkdirs()
        // 200 KB of nothing a line can be made of, then a good line: past the 128 KB the file is read to.
        file().writeText("junk\n".repeat(40_000) + "line=too far\n")
        DeathQuotes.load(file())
        assertEquals(emptyList(), DeathQuotes.lines)
        // And the same line with the junk cut to what fits is read.
        file().writeText("junk\n".repeat(1_000) + "line=near enough\n")
        DeathQuotes.load(file())
        assertEquals(listOf("near enough"), DeathQuotes.lines)
    }

    @Test
    fun `nothing is written before a file is set, and every change is kept at once`() {
        DeathQuotes.add("before any file")             // no file yet: kept in memory, and nothing to write
        assertFalse(file().exists())
        DeathQuotes.load(file())
        DeathQuotes.add("kept at once")
        assertTrue(file().isFile)
        assertTrue("line=kept at once" in file().readText())
        DeathQuotes.remove(0)
        assertFalse("line=kept at once" in file().readText())
        DeathQuotes.useOwn(false)
        assertTrue("enabled=false" in file().readText())
    }

    // ---------------------------------------------------------------- Backup carries it

    @Test
    fun `Backup takes the file and a restore gives the player's lines back`() {
        val src = Files.createTempDirectory("dqsrc").toFile()
        val dst = Files.createTempDirectory("dqdst").toFile()
        try {
            DeathQuotes.load(File(src, DeathQuotes.FILE))
            DeathQuotes.add("Survives a backup.")
            DeathQuotes.useBuiltIn(false)
            assertTrue(Backup.admits(DeathQuotes.FILE), "the file is on Backup's list")
            assertTrue(DeathQuotes.FILE in Backup.collect(src))
            val zip = ByteArrayOutputStream().also { Backup.write(src, it) }.toByteArray()
            assertTrue(Backup.read(dst, zip.inputStream()) >= 1)
            DeathQuotes.reset()
            DeathQuotes.load(File(dst, DeathQuotes.FILE))
            assertEquals(listOf("Survives a backup."), DeathQuotes.lines)
            assertFalse(DeathQuotes.keepBuiltIn)
        } finally {
            src.deleteRecursively(); dst.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------- the words

    @Test
    fun `every word the screen says follows the copy rules`() {
        val em = 0x2014.toChar()
        assertTrue(DeathQuotesCopy.all.size >= 20, "the list of what the screen says is not empty: ${DeathQuotesCopy.all.size}")
        for (s in DeathQuotesCopy.all) {
            assertTrue(s.isNotBlank() && s == s.trim(), "\"$s\" is plain text with no stray space")
            assertFalse(em in s || 0x2013.toChar() in s, "\"$s\" has a dash that is not a hyphen")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(s) || Regex("(?i)artificial intelligence|machine learning|\\bGPT\\b|\\bLLM\\b").containsMatchIn(s), "\"$s\" says how the work is made")
            assertFalse('!' in s, "\"$s\" shouts: the voice is dry")
        }
    }

    @Test
    fun `the notes under the switches say what the pool is`() {
        assertEquals(DeathQuotesCopy.SWITCHED_OFF, DeathQuotesCopy.poolNote(enabled = false, keepBuiltIn = true, own = 3))
        assertEquals(DeathQuotesCopy.BUILT_IN_USED, DeathQuotesCopy.poolNote(enabled = true, keepBuiltIn = true, own = 0))
        assertEquals(DeathQuotesCopy.BUILT_IN_USED, DeathQuotesCopy.poolNote(enabled = true, keepBuiltIn = false, own = 0), "no lines of your own: the built-in ones are used whatever the second switch says")
        assertEquals(DeathQuotesCopy.ONLY_MINE, DeathQuotesCopy.poolNote(enabled = true, keepBuiltIn = false, own = 2))
        assertEquals(null, DeathQuotesCopy.poolNote(enabled = true, keepBuiltIn = true, own = 2), "the ordinary case needs no note")
        assertEquals("No lines of your own yet.", DeathQuotesCopy.count(0))
        assertEquals("1 line of your own.", DeathQuotesCopy.count(1))
        assertEquals("12 lines of your own.", DeathQuotesCopy.count(12))
        assertEquals(null, DeathQuotesCopy.message(DeathQuotes.Edit.OK))
        assertEquals(DeathQuotesCopy.DUPLICATE, DeathQuotesCopy.message(DeathQuotes.Edit.DUPLICATE))
        assertTrue("${DeathQuotes.MAX_LINES}" in DeathQuotesCopy.FULL && "${DeathQuotes.MAX_LENGTH}" in DeathQuotesCopy.LIMIT, "the limits the screen states are the real ones")
    }
}
