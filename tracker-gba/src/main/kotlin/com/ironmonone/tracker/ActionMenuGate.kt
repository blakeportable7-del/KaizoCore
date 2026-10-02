package com.ironmonone.tracker

/**
 * B-to-Run's one question to a tracker (2026-10-01, Blake: "it is making the game locked into the bag"): is the
 * battle's action menu (FIGHT, BAG or ITEM, POKEMON, RUN) on screen and taking input, in a wild battle? Read live from
 * memory at the moment of asking, never from the last poll. False in the Bag, the party screen, the move menu and the
 * battle's text, where B means something to the game and the run's RIGHT, DOWN, A would land on the wrong screen.
 */
interface ActionMenuGate {
    fun isChoosingActionInWild(): Boolean
}
