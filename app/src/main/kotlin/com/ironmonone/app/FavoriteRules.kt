package com.ironmonone.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The Favorites Clause as the run's own rulebook writes it (Blake, 2026-10-01: "make sure all kaizo runs have the
 * right rules, and base it on whatever game is doing a run because different kaizo's have different rules, and
 * different kaizo modes have different rules").
 *
 * Every bundled rulebook has the clause (three favorites, one more for each generation after the third) and allows one
 * legendary among them. Kaizo and the modes past it cap a legendary favorite under 600 BST, Survival allows no
 * legendary and nothing at 580 BST or more, Super Kaizo no legendary, and the Nat. Dex books allow nine favourites, with
 * their own BST line and a favourite counting for its own form only. So the Kaizo IronMON screen shows the lines of the
 * book for the game and mode picked, and flags what it can know without the game running: two legendaries, or a
 * legendary where the mode allows none. The BST limits need the game's own stats, so they are shown, not checked.
 */
internal object FavoriteRules {
    const val HEAD = "This mode's rules for favorites"

    /** Modes whose books allow no legendary favorite: Super Kaizo ("No Legendary favorites") and Survival. */
    val NO_LEGENDARY_MODES = setOf("superkaizo", "survival")

    /**
     * Legendary, sub-legendary and mythical Pokemon by National Dex number: the Nat. Dex engine's list (engine-natdex
     * Pokemon.legendaries, after Serebii's, which every book cites), base species only. A form counts as its species.
     */
    val LEGENDARY: Set<Int> = setOf(
        144, 145, 146, 150, 151, 243, 244, 245, 249, 250, 251, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386,
        480, 481, 482, 483, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 494, 638, 639, 640, 641, 642, 643, 644,
        645, 646, 647, 648, 649, 716, 717, 718, 719, 720, 721, 772, 773, 785, 786, 787, 788, 789, 790, 791, 792, 800,
        801, 802, 807, 808, 809, 888, 889, 890, 891, 892, 893, 894, 895, 896, 897, 898, 905, 1001, 1002, 1003, 1004,
        1007, 1008, 1014, 1015, 1016, 1017, 1024, 1025,
    )

    /**
     * Strong Legendary and Mythical Pokemon by National Dex number, the two groups the Nat. Dex rules single out
     * ("any Pokemon above 600 BST that is not categorized as Strong Legendary or Mythical" may be a favourite in Standard
     * and Ultimate; Evo Kaizo takes no such starter): the Nat. Dex engine's list (Pokemon.strongLegendaries), base
     * species only. Cosmoem ([COSMOEM]) is one of them.
     */
    val STRONG_OR_MYTHICAL: Set<Int> = setOf(
        150, 151, 249, 250, 251, 382, 383, 384, 385, 386, 483, 484, 487, 489, 490, 491, 492, 493, 494, 643, 644, 646,
        647, 648, 649, 716, 717, 718, 719, 720, 721, 789, 790, 791, 792, 800, 801, 802, 807, 808, 809, 888, 889, 890,
        893, 898, 1007, 1008, 1024, 1025,
    )

    /** Cosmoem, which the Nat. Dex rules let no one start with outside Standard ("except Cosmoem"). */
    const val COSMOEM = 790

    private val FAVORITE = Regex("favou?rite", RegexOption.IGNORE_CASE)
    private val NUMBERED = Regex("^[0-9]+[.)] ")
    // Red, Blue and Yellow Kaizo and Survival: "All Pokemon greater than 480 BST are banned. This includes: Dragonite, ..."
    private val BANNED_BY_NAME = Regex("BST are banned[.] This includes: ([^.]*)[.]")
    private val LINK = Regex("[(]https?://[^)]*[)]")

    /** The book's lines about favorites, as rules: a list item or a numbered rule that names them, links removed. */
    fun lines(book: String): List<String> = book.lines().asSequence()
        .map { it.trim() }
        .filter { l ->
            (l.startsWith("- ") || l.startsWith("* ") || l.startsWith("*:") || NUMBERED.containsMatchIn(l)) &&
                (FAVORITE.containsMatchIn(l) || BANNED_BY_NAME.containsMatchIn(l))
        }
        .map { l ->
            l.removePrefix("- ").removePrefix("*:").removePrefix("* ").replace(NUMBERED, "")
                .replace(LINK, "").replace("**", "").replace(Regex(" +"), " ").replace(" ,", ",").replace(" .", ".").trim()
        }
        .distinct()
        .toList()

    /** Whether [name] is a legendary, sub-legendary or mythical Pokemon, a form by its species (Kyogre-P, Articuno-G). */
    fun isLegendary(name: String): Boolean = nationalOfName(name)?.let { it in LEGENDARY } == true

    /** Whether [name] is a Strong Legendary or a Mythical Pokemon, a form by its species (Kyogre-P is Kyogre). */
    fun isStrongOrMythical(name: String): Boolean = nationalOfName(name)?.let { it in STRONG_OR_MYTHICAL } == true

    /** The National Dex number of [name], a form by its species (Kyogre-P, 382); null for a name the table lacks. */
    fun nationalOfName(name: String): Int? {
        val id = Favorites.idOf(name) ?: return null
        Favorites.nationalOf(id)?.let { return it }
        // A form past the national range: the longest name before a "-" that is a species of its own.
        val parts = name.trim().split('-')
        for (n in parts.size - 1 downTo 1) {
            val base = Favorites.idOf(parts.take(n).joinToString("-")) ?: continue
            Favorites.nationalOf(base)?.let { return it }   // else itself a form ("Meowstic-F"): shorter still
        }
        return null
    }

    /** The Pokemon [book] bans by name: Red, Blue and Yellow's six over 480 BST in Kaizo and Survival. */
    fun bannedByName(book: String?): Set<String> = book?.let { b ->
        BANNED_BY_NAME.findAll(b).flatMap { m -> m.groupValues[1].replace(" and ", ",").split(',').asSequence() }
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }.orEmpty()

    /**
     * What the run's mode does not allow among [favorites]: a Pokemon its [book] bans by name, any legendary in Super
     * Kaizo and Survival, else a second legendary.
     */
    fun problems(favorites: List<String>, mode: String?, book: String? = null): List<String> {
        val names = favorites.map { it.trim() }.filter { it.isNotEmpty() }
        val banned = bannedByName(book)
        val out = names.filter { it.lowercase() in banned }.map { "$it is banned in this mode, so it cannot be a favorite." }.toMutableList()
        val legends = names.filter { it.lowercase() !in banned && isLegendary(it) }
        when {
            legends.isEmpty() -> Unit
            mode in NO_LEGENDARY_MODES -> legends.forEach { out += "$it is a legendary, and this mode allows no legendary favorite." }
            legends.size > 1 -> out += "Only one favorite may be a legendary: ${legends.joinToString(" and ")} are."
        }
        return out
    }
}

/** Under the favorites on the Kaizo IronMON screen: what the mode's book says about them, and what breaks it. */
@Composable
internal fun FavoriteRulesBlock(book: String?, mode: String?, slots: List<String>) {
    val lines = remember(book) { book?.let { FavoriteRules.lines(it) }.orEmpty() }
    FavoriteRules.problems(slots, mode, book).forEach {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Shell.dangerOnPaper, modifier = Modifier.padding(top = 4.dp))
    }
    if (lines.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(FavoriteRules.HEAD, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    lines.forEach {
        Text("${Char(0x2022)} $it", style = MaterialTheme.typography.bodySmall, color = Shell.hintOnPaper, modifier = Modifier.padding(top = 2.dp))
    }
}
