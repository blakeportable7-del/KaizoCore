package com.ironmonone.tracker.nuzlocke

/**
 * What the tracker does for a game and what the player still does by hand (2026-09-30), in plain words for the rules
 * page. Each game family reads different things from the game, and a rule the tracker cannot check is not silently
 * left off: it is said here. Generation 3 keeps its own page and has no notes.
 */
object NuzlockeNotes {

    class Notes(val automatic: List<String>, val byHand: List<String>) {
        val isEmpty: Boolean get() = automatic.isEmpty() && byHand.isEmpty()
    }

    fun forGame(system: NuzlockeSystem, gameKey: String): Notes = when (system) {
        NuzlockeSystem.GEN1 -> gen1()
        NuzlockeSystem.GEN2 -> gen2(gameKey)
        NuzlockeSystem.GEN3 -> if (gameKey == HEART_SOUL) heartSoul() else Notes(emptyList(), emptyList())
        NuzlockeSystem.GEN4 -> gen4(gameKey)
        NuzlockeSystem.GEN5 -> gen5(gameKey)
    }

    /** Heart & Soul's key (NuzlockeStarts.gameKeyOf): a Gen 3 game, with Johto, Kanto and sixteen badges of its own. */
    const val HEART_SOUL = "hns"

    /**
     * Heart & Soul (2026-10-05): the Gen 3 tracker reads it through its own build's layout (tracker-gba Hns.kt), so most of
     * Gen 3 holds, and what it is told is said here: its areas, its sixteen badges and Red, its own Nuzlocke option.
     */
    private fun heartSoul() = Notes(
        automatic = listOf(
            "Each area is the place name the game shows on its map (Route 29, Union Cave), so every floor of a cave or tower is one area.",
            "Wild Pokemon come from the encounter tables in your copy of the game, for every time of day, walking, surfing, Rock Smash and the three rods.",
            "Level caps: the eight Johto leaders, the Elite Four and Lance, then the eight Kanto leaders and Red on Mt. Silver. The levels are the ones in your copy of the game, so a randomized game has its own caps.",
            "The dupes clause numbers Heart & Soul's Pokemon as the National Dex does, so a randomized species from any generation is matched to its own line.",
            "Faints and whiteouts are seen the moment they happen. Nicknames are read from the party, all twelve letters.",
            "Set battles (Sudowoodo, the red Gyarados, the legendaries) are statics, told apart from wild Pokemon by the way the game ends them. A Pokemon given to you, Elm's included, is a gift of its town.",
        ),
        byHand = listOf(
            "The dupes clause knows the evolution lines of Gens 1 to 3 and their later evolutions. A line from Gen 4 on counts each of its Pokemon on its own, so fix such a dupe by hand.",
            "Heart & Soul has a Nuzlocke option of its own in its challenge menu. KaizoCore's ledger does not read it. With both on, the game's own rules apply too, and with its hardcore setting a lost rival battle in Cherrygrove City deletes the save before the Nuzlocke has started.",
            "A catch with a full party goes to a box, which the tracker cannot see inside.",
        ),
    )

    private fun gen1() = Notes(
        automatic = listOf(
            "Each map is an area from the game's own map list. Every floor of a cave or tower is one area, and each Safari Zone section is its own area unless the switch makes the whole Zone one.",
            "A catch is seen when a Pokemon joins the party or the game says a ball caught the wild Pokemon. A wild Pokemon that fainted, ran off or was got away from is told apart from the game's battle result, the enemy's last HP and whose Teleport, Roar or Whirlwind ended the battle.",
            "Faints and whiteouts are seen the moment they happen. A Pokemon that fainted stays dead after a Pokemon Center heals it.",
            // What NuzlockeEngine.trainerStart checks: each leader, and the League once, at its first battle (rc32 audit P2 #142).
            "Level caps: each gym leader is checked when their battle starts. The first Elite Four battle is checked against the strongest Pokemon in the whole Elite Four, and after that there is no cap, the Champion included. The levels are the ones in your copy of the game, so a randomized game has its own caps.",
            "Items used in battle are found by comparing the bag before and after. Poke Balls are ignored.",
            "The battle style option is read. The Old Man's lesson and the Pokemon Tower ghosts are not encounters. Snorlax, the legendary birds, Mewtwo and the Power Plant's Voltorb and Electrode are set battles, found by place and level (Red and Blue's Route 12 Snorlax and Marowak also by species, so a randomized game misses those two).",
            "Nicknames are read from the party.",
        ),
        byHand = listOf(
            "Red, Blue and Yellow have no genders, so Wedlocke pairs go by the order of the catches: a new catch pairs with the oldest one still waiting.",
            "Fishing is not told apart from walking. The game does not say how a wild battle began, only that you are surfing, so 'Water is its own area' covers surfing.",
            "There are no shiny Pokemon in these games, so the shiny clause never comes up.",
            "A gift, a fossil, a Game Corner prize or an in-game trade shows up as a gift and is free unless 'Gifts count' is on. Count the Game Corner and the trades the way your table does.",
            "A catch with a full party goes to a box. The tracker sees the ball and writes the catch, but it cannot see inside the boxes.",
            "The dupes clause uses the games' own evolution lines. A randomizer that changes evolutions changes who belongs together, so fix a wrong dupe by hand.",
        ),
    )

    private fun gen2(game: String): Notes {
        val crystal = game.equals("crystal", ignoreCase = true)
        return Notes(
            automatic = listOf(
                "Each landmark is an area from the game's own tables, in Johto and in Kanto alike. Every floor of a cave is one area.",
                "A catch is seen when a Pokemon joins the party or the game's own catch flag was set. A fainted wild Pokemon is told apart from the game's battle result.",
                "Faints and whiteouts are seen the moment they happen. A Pokemon that fainted stays dead after a Pokemon Center heals it.",
                "Gender and shininess are worked out from the DVs the way the game does. Wedlocke pairs and the shiny clause work.",
                "Level caps: each Johto gym leader is checked when their battle starts, and the first Elite Four battle against the strongest Pokemon in the whole Elite Four. After that there is no cap, Lance included. The levels are the ones in your copy of the game. Kanto's gyms come in any order, and each is checked against its own leader.",
                "Fishing and Headbutt trees are told apart from walking by the battle's own type. The roamers, the Bug-Catching Contest, forced shinies, the Team Rocket traps and the Rocket base's Electrode are set battles, and so are Sudowoodo, Snorlax and the rest of the static table.",
                "Items used in battle are found by comparing the bag before and after. Poke Balls are ignored. The battle style option is read. Nicknames are read from the party.",
            ),
            byHand = listOf(
                "Every escape ends the same way in these games, as a draw, so who ran is not known. A wild Pokemon that ran and one you ran from both read 'you ran', and the escape clause is yours to apply.",
                "The run is complete when Lance is beaten. Kanto comes after: open the run again from its rules page to keep the ledger going there. A Genlocke leg ends at Lance.",
                "Rock Smash counts as walking, because the game gives it no battle type of its own. Swarms and the time of day are ordinary encounters of the area.",
                "A gift, an egg, a Game Corner prize or an in-game trade shows up as a gift and is free unless 'Gifts count' is on. The Bug-Catching Contest's catch arrives as a gift.",
                "A catch with a full party goes to a box, which the tracker cannot see inside.",
                (if (crystal) "Crystal's Odd Egg and the Mystery Egg are gifts when they hatch." else "Gold and Silver have no Odd Egg. The Mystery Egg is a gift when it hatches.") +
                    // The tracker follows an egg from the place it joined the party (rc32 audit P2 #140).
                    " With 'Gifts count' on, a hatched egg uses up the area where you got it, not the one it hatches in.",
                "The dupes clause uses the games' own evolution lines. A randomizer that changes evolutions changes who belongs together, so fix a wrong dupe by hand.",
            ),
        )
    }

    private fun gen4(game: String): Notes {
        val hgss = game.equals("heartgold", ignoreCase = true) || game.equals("soulsilver", ignoreCase = true)
        return Notes(
            automatic = listOf(
                "Areas come from the game's own location names (Route 203, Mt. Coronet). A city's gym is the city. Cave floors are one area unless the switch keeps them apart.",
                "A catch is seen when a new Pokemon joins the party during a wild battle. A wild Pokemon that fainted is seen when its HP reaches 0.",
                "Faints and whiteouts are seen the moment they happen. A Pokemon that fainted stays dead after a Pokemon Center heals it.",
                "Gender, shininess and (on a game that was not randomized) types come from the Pokemon's own data and the game's species table. A randomized game's own types come from its randomizer file.",
                "Level caps: each gym leader (Platinum's in Platinum's order) is checked when their battle starts, and the first Elite Four battle against the strongest Pokemon in the whole Elite Four. After that there is no cap, the Champion included. The caps are the standard table for the game, from the game's own trainer data.",
                "A trainer battle is a win when its last Pokemon was down, or when it ended with one of yours standing (the Champion only by the first test). Legendaries and other set battles are a table of place and level, some by species too.",
                "The badge count moves the level cap on. Items in battle: the healing and status items in the bag are compared before and after. Nicknames are read: letters, digits and spaces, and any other mark shows as a question mark.",
            ),
            byHand = listOf(
                "The Poke Ball count is not read, so the rules begin as soon as you have a Pokemon, whatever 'Slow start' says. A wild Pokemon you meet before you hold any balls counts as that area's encounter: clear it on the Areas tab.",
                "The game does not say how a battle ended. A wild Pokemon that fainted (its HP is 0) or joined the party is told from the rest, and one you did not knock out or catch reads 'unknown', whether it ran or you did, so the escape clause is yours to apply. A catch into a full party's box is not seen.",
                "Surfing, fishing, honey trees, Poke Radar, swarms and Headbutt trees are all ordinary encounters of the area: the game does not say how a battle began, so 'Water is its own area' does nothing here.",
                "The battle style option is not read, so the Set reminder stays quiet. Only healing and status items in the bag are compared for 'no items in battle', not X items or held items.",
                "Double battles are not tracked. When a wild battle is a double, count the one you chose by hand.",
                if (hgss) "The run is complete when Lance is beaten. Kanto comes after: open the run again from its rules page. The Bug-Catching Contest is its own area, and the Safari Zone follows the Safari Zone switch."
                else "The Great Marsh's areas follow the Safari Zone switch. Team Galactic's commanders and Cyrus, and Barry's fights, are extra cap points when 'Rivals and team leaders are bosses' is on.",
                "The dupes clause uses the games' own evolution lines. A randomizer that changes evolutions changes who belongs together, so fix a wrong dupe by hand.",
            ),
        )
    }

    private fun gen5(game: String): Notes {
        val second = game.equals("black2", ignoreCase = true) || game.equals("white2", ignoreCase = true)
        return Notes(
            automatic = listOf(
                "Areas come from the game's own location names (Route 4, Pinwheel Exterior). A city's gym is the city. Cave floors are one area unless the switch keeps them apart.",
                "A catch is seen when a new Pokemon joins the party during a wild battle. A wild Pokemon that fainted is seen when its HP reaches 0.",
                "Faints and whiteouts are seen the moment they happen. A Pokemon that fainted stays dead after a Pokemon Center heals it.",
                "Gender, shininess and (on a game that was not randomized) types come from the Pokemon's own data and the game's species table. A randomized game's own types come from its randomizer file.",
                "Level caps: the eight gym leaders are checked when their battle starts, and whichever Elite Four member you fight first against the strongest Pokemon in the whole Elite Four. After that there is no cap, and none at the last fight. The caps are the standard table for the game.",
                "A trainer battle is a win when its last Pokemon was down, or when it ended with one of yours standing (the last fight only by the first test). Legendaries and other set battles are a table of place and level, some by species too. Nicknames are read.",
                "Items in battle: the healing and status items in the bag are compared before and after.",
            ),
            byHand = listOf(
                "The Poke Ball count is not read, so the rules begin as soon as you have a Pokemon, whatever 'Slow start' says. A wild Pokemon you meet before you hold any balls counts as that area's encounter: clear it on the Areas tab.",
                "The game does not say how a battle ended. A wild Pokemon that fainted (its HP is 0) or joined the party is told from the rest, and one you did not knock out or catch reads 'unknown', whether it ran or you did, so the escape clause is yours to apply. A catch into a full party's box is not seen.",
                "Rustling grass, dust clouds, rippling water, bridge shadows, Hidden Grottoes, surfing and seasons are all ordinary encounters of the area: the game does not say how a battle began.",
                "Double battles are not tracked. A dark grass double is one encounter: count the one you chose by hand.",
                "The battle style option is not read, so the Set reminder stays quiet. Only healing and status items in the bag are compared for 'no items in battle'.",
                if (second) "Black 2 and White 2's level caps are Normal Mode's. Challenge Mode's trainers are up to 5 levels stronger and Easy Mode's weaker, and the game does not say which mode it is in, so adjust the caps by hand."
                else "Alder is not fought on a first clear. N and then Ghetsis end the game, and the run is complete when Ghetsis is beaten.",
                "The dupes clause uses the games' own evolution lines. A randomizer that changes evolutions changes who belongs together, so fix a wrong dupe by hand.",
            ),
        )
    }
}
