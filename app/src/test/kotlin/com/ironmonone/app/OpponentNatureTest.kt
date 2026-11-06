package com.ironmonone.app

import com.ironmonone.app.stream.StreamSnapshot
import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Blake, 2026-11-06: an opponent's Pokemon, wild or a trainer's, never shows its nature, and its stat rows carry no
 * nature mark or nature colour, in every game and on every view: the cards (phone, floating window, wide view, HUD), the
 * stream page and its chat answers, Calc Atk and the info a battle opens. Your own Pokemon keeps its nature's + and -.
 * The marks you put on an opponent's stats (+, -, =) are your guesses, not its nature, and stay.
 *
 * Checked on 2026-11-06 before rc40: no view showed it. These hold it: the stream is built from an opponent with a
 * nature that raises and lowers two stats (Adamant) and searched for it; the cards are Compose, so their sources are
 * read: an opponent's card draws no nature, and the nature reaches a stat row only in your own Pokemon's card.
 */
class OpponentNatureTest {
    private val adamant = 3   // + Attack, - Sp. Atk
    private val natureNames = listOf(
        "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish", "Lax", "Timid", "Hasty",
        "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky",
    )

    private fun gbaMon(nature: Int, species: Int) = PokemonDecoder.Mon(
        pid = nature.toLong(), level = 30, nickname = "", species = species, heldItem = 0, friendship = 0,
        moves = listOf(1, 2), pp = listOf(10, 20), ivs = List(6) { 0 }, evs = List(6) { 0 },
        ppUps = List(4) { 0 }, abilitySlot = 0, nature = nature, shiny = false, status = 0,
        curHp = 61, maxHp = 74, atk = 55, def = 40, spe = 52, spAtk = 61, spDef = 38,
    )
    private val base = BaseStats(70, 94, 50, 66, 94, 50, type1 = 6, type2 = 2, ability1 = 68, ability2 = 0)

    private fun gbaBattle(wild: Boolean): TrackerState {
        val own = TrackedMon(gbaMon(adamant, 414), "Mothim", listOf("Gust"), base)
        // The opponent's personality value makes it Adamant too (a Gen 3 nature is pid % 25).
        val enemy = EnemyInfo(551, "Sandile", 21, 33, 60, 4, 17, base, movesSeen = listOf("Bite"), pid = adamant.toLong() + 25 * 7)
        return TrackerState(1, listOf(own), inBattle = true, isWildBattle = wild, enemy = enemy, enemyTeam = listOf(true, true))
    }

    private fun ndsMon(pid: Long, species: Int, nature: Int) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(pid, species, 30, 60, 60, listOf(33, 0, 0, 0), gen5 = true, nature = nature), gen5 = true)!!,
        speciesName = "#$species", info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    /** Everything under [v], keys and values, as text: a nature by name or as a stat's + or - would be in it. */
    private fun flat(v: Any?): List<String> = when (v) {
        is Map<*, *> -> v.entries.flatMap { (k, x) -> listOf(k.toString()) + flat(x) }
        is Iterable<*> -> v.flatMap { flat(it) }
        is IntArray -> v.map { it.toString() }
        null -> emptyList()
        else -> listOf(v.toString())
    }

    private fun assertNoNature(enemy: Any?, where: String) {
        val words = flat(enemy)
        assertTrue(enemy != null, "$where: the opponent is on the stream")
        assertFalse(words.any { it.equals("nature", ignoreCase = true) }, "$where: no nature field for the opponent: $enemy")
        assertFalse(words.any { w -> natureNames.any { w.contains(it, ignoreCase = true) } }, "$where: no nature by name: $enemy")
        assertFalse(words.any { it == "+" || it == "-" }, "$where: no nature mark on a stat: $enemy")
    }

    @Test
    fun `the stream never sends an opponent's nature, wild or trainer, GBA`() {
        for (wild in listOf(true, false)) {
            val snap = StreamSnapshot.build(StreamSnapshot.Run("FireRed", "GBA", 3, true), gbaBattle(wild), null, StreamSnapshot.Notes())
            assertNoNature(snap["enemy"], if (wild) "wild" else "trainer")
            // Your own keeps its nature's marks (the control: the same nature does reach the stream for your Pokemon).
            @Suppress("UNCHECKED_CAST")
            val lead = (snap["party"] as List<Map<String, Any?>>).first()
            assertEquals(mapOf("atk" to "+", "spa" to "-"), lead["nature"])
        }
    }

    @Test
    fun `the stream never sends an opponent's nature, wild or trainer, DS`() {
        val mine = ndsMon(0x10, 7, adamant)
        for (wild in listOf(true, false)) {
            val state = NdsTrackerState(1, listOf(mine), located = true, inBattle = true, isWildBattle = wild,
                enemy = ndsMon(0x90, 16, adamant), healsPid = 0x10)
            assertEquals(adamant, state.enemy!!.mon.nature, "the opponent read as Adamant")
            val snap = StreamSnapshot.build(StreamSnapshot.Run("Black 2", "NDS", 3, true, generation = 5), null, state, StreamSnapshot.Notes())
            assertNoNature(snap["enemy"], if (wild) "DS wild" else "DS trainer")
            @Suppress("UNCHECKED_CAST")
            val lead = (snap["party"] as List<Map<String, Any?>>).first()
            assertEquals(mapOf("atk" to "+", "spa" to "-"), lead["nature"])
        }
    }

    private fun read(path: String) = File(path).readText().replace("\r\n", "\n")

    /** The body of `fun [name](`, from its line to the closing brace at that line's indentation. */
    private fun body(src: String, name: String): String {
        val at = Regex("""(?m)^( *)(?:@\w+\s+)*(?:private |internal |public )?fun $name\(""").find(src)
            ?: error("fun $name not found")
        val indent = at.groupValues[1]
        val end = Regex("(?m)^$indent}\\s*$").find(src, at.range.last) ?: error("end of $name not found")
        return src.substring(at.range.first, end.range.last + 1)
    }

    /** "nature" as a word of its own or a name's start (natureMark, NatureMark), not inside another word (signature). */
    private val natureWord = Regex("(?<![A-Za-z])[Nn]ature")

    /** The code of [text] without its comments, so a comment that explains the rule does not trip it. */
    private fun code(text: String) = text.lines().map { it.substringBefore("//") }
        .map { if (it.trimStart().startsWith("*") || it.trimStart().startsWith("/*")) "" else it }.joinToString("\n")

    @Test
    fun `an opponent's card draws no nature, on the phone, the DS and KaizoCore PC`() {
        val cards = listOf(
            "src/main/kotlin/com/ironmonone/app/TrackerPanel.kt" to "EnemyCard",
            "src/main/kotlin/com/ironmonone/app/NdsTrackerPanel.kt" to "NdsEnemyCard",
            "../pcui/src/main/kotlin/com/ironmonone/pcui/PcBesteon.kt" to "EnemyView",
            "src/main/kotlin/com/ironmonone/app/stream/StreamSnapshot.kt" to "enemy",
            "src/main/kotlin/com/ironmonone/app/stream/StreamSnapshot.kt" to "enemyNds",
        )
        for ((file, fn) in cards) {
            val b = code(body(read(file), fn))
            assertFalse(natureWord.containsMatchIn(b), "$fn in $file reads a nature: ${natureWord.find(b)?.let { b.substring((it.range.first - 60).coerceAtLeast(0), it.range.last + 40) }}")
            assertFalse("colorNumber" in b, "$fn colours numbers by nature")
            assertFalse("heldItemDescription" in b, "$fn describes a held item by the holder's nature")
        }
        // Views that show an opponent and never a nature at all.
        for (file in listOf("TrackerHud.kt", "CalcAtkScreen.kt", "stream/twitch/ChatCommands.kt")) {
            val b = code(read("src/main/kotlin/com/ironmonone/app/$file"))
            assertFalse(natureWord.containsMatchIn(b), "$file reads a nature")
        }
    }

    @Test
    fun `a nature reaches a stat row only in your own Pokemon's card`() {
        // Every place in the app and KaizoCore PC that turns a nature into a mark, a colour or a line of text.
        val allowed = mapOf(
            "TrackerPanel.kt" to setOf("PartyCard"),
            "NdsTrackerPanel.kt" to setOf("NdsPartyCard"),
            "PcTracker.kt" to setOf("PcStatRow"),
            "NatureMark.kt" to setOf("natureMark"),
            "StreamSnapshot.kt" to setOf("own", "ownNds", "natureMarks"),
            "PcBesteon.kt" to setOf("OwnView", "NatureMark"),
        )
        val files = (File("src/main/kotlin").walkTopDown() + File("../pcui/src/main/kotlin").walkTopDown()).filter { it.extension == "kt" }
        val uses = Regex("""natureMark\(|PcStatRow\(.*nature =|heldItemDescription\(|natureMarks\(""")
        for (f in files) {
            val src = read(f.path)
            val lines = code(src).lines()
            lines.forEachIndexed { i, line ->
                if (!uses.containsMatchIn(line)) return@forEachIndexed
                if (f.name.startsWith("GachaMon") || f.name.startsWith("PcPrize") || f.name.startsWith("PcGacha")) return@forEachIndexed   // your own cards
                val owner = (i downTo 0).firstNotNullOfOrNull { j -> Regex("""fun (\w+)\(""").find(lines[j])?.groupValues?.get(1) }
                assertTrue(owner != null && owner in allowed[f.name].orEmpty(),
                    "${f.name}:${i + 1} gives a nature to $owner, which is not your own Pokemon's card: ${line.trim()}")
            }
        }
    }
}
