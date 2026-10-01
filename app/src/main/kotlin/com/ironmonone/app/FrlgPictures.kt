package com.ironmonone.app

/**
 * The FireRed and LeafGreen dungeon maps, and which place each one goes with (2026-09-29).
 *
 * The maps are Bill Greenwald's (doctrDNA), from IronMon Emu, used with permission: NOTICE and the About
 * screen credit him, and assets/frlg/SOURCE.txt names every file's origin. This table is KaizoCore's own.
 * His picture-to-place list was read for which picture belongs where (those are facts about the game); none
 * of his code is used.
 *
 * Seven maps ship since 2026-09-30 (the IronMON rules check, Blake's call): Mt. Moon, the S.S. Anne, Victory
 * Road, the Power Plant, the Pokemon Mansion, Rock Tunnel and the Seafoam Islands. Each draws its floors with
 * routes, item spots and trainer counts, the same in every game. Silph Co., the Rocket Hideout and the Safari
 * Zone were step by step plans ("Direct Route To Giovanni"), Saffron Gym's was the warp puzzle's solution, and
 * the hidden item screenshots were taken in another randomized game, whose items a run does not have: none of
 * those ship. All of it is off until the player turns it on (TrackerOptions.frlgGuidePictures).
 *
 * A place is a map layout id, which is how the Gen 3 tracker already knows where the player is:
 * TrackerState.mapId is the word at gMapHeader + 0x12, and the tracker's own name tables (RouteData.Info,
 * routeinfo-firered.tsv) are keyed the same way. The ids are the game's layout numbers (pokefirered's
 * data/layouts/layouts.json, first entry 1). A picture never depends on a display name. A dungeon's map is
 * one picture for the whole building, so every floor shows it.
 */
object FrlgPictures {
    /** Where the files are, under assets/. A picture's file name has no folder and no extension. */
    const val MAPS_DIR = "frlg/maps"
    private const val EXT = ".webp"

    fun mapAsset(file: String) = "$MAPS_DIR/$file$EXT"

    /** One place with a map: its layout id, a name to say aloud, and its maps in the order they show. */
    class Place(
        val mapId: Int,
        /** The tracker's own name for the map where it has one (routeinfo-firered.tsv), else ours. */
        val name: String,
        val maps: List<String>,
    ) {
        /** What the map mark says to a screen reader. */
        fun spoken(): String = "Show the map for $name"
    }

    /**
     * FireRed and LeafGreen, and the Nat. Dex and Faster FireRed builds of them, report the badge set
     * "FRLG"; Ruby, Sapphire and Emerald report "RSE", the Game Boy games "RBY" and "GSC".
     */
    fun isFrlg(badgeSet: String?): Boolean = badgeSet == "FRLG"

    /**
     * The map for the place the player is on, or null for any other game or a place with none, and null while
     * the player has not turned them on (TrackerOptions.frlgGuidePictures, off by default, 2026-09-30): every
     * mark and viewer asks here, so the switch covers all of them.
     */
    fun placeFor(badgeSet: String?, mapId: Int?): Place? =
        if (TrackerOptions.frlgGuidePictures && isFrlg(badgeSet) && mapId != null) BY_ID[mapId] else null

    /** The same for a map the player picked (the route info screen's look-up), or null for any other game. */
    fun lookupFor(badgeSet: String?): ((Int) -> Place?)? =
        if (TrackerOptions.frlgGuidePictures && isFrlg(badgeSet)) { id -> BY_ID[id] } else null

    /** Every place, for the tests. */
    val places: List<Place> get() = PLACES

    private val PLACES: List<Place> = buildList {
        fun place(id: Int, name: String, map: String) { add(Place(id, name, listOf(map))) }

        for ((id, floor) in listOf(114 to "1F", 115 to "B1F", 116 to "B2F")) place(id, "Mt. Moon $floor", "mt_moon")

        // The S.S. Anne's kitchen and captain's office have no name in the tracker's tables.
        for ((id, name) in listOf(118 to "Ext.", 119 to "1F", 120 to "2F", 121 to "3F", 122 to "B1F", 123 to "Deck",
            170 to "Kitchen", 171 to "Captain's Office", 177 to "Rooms", 178 to "Rooms")) place(id, "S.S. Anne $name", "ss_anne")

        for ((id, floor) in listOf(125 to "1F", 126 to "2F", 127 to "3F")) place(id, "Victory Road $floor", "victory_road")

        place(168, "Power Plant", "power_plant")

        // The names are the tracker's own (Poke with an e acute).
        for ((id, floor) in listOf(143 to "1F", 144 to "2F", 145 to "3F", 146 to "B1F")) place(id, "Poké Mansion $floor", "pokemon_mansion")

        for ((id, floor) in listOf(154 to "1F", 155 to "B1F")) place(id, "Rock Tunnel $floor", "rock_tunnel")

        for ((id, floor) in listOf(156 to "1F", 157 to "B1F", 158 to "B2F", 159 to "B3F", 160 to "B4F"))
            place(id, "Seafoam Islands $floor", "seafoam_islands")
    }

    private val BY_ID: Map<Int, Place> = PLACES.associateBy { it.mapId }.also {
        // A layout id listed twice would silently keep the last one.
        check(it.size == PLACES.size) { "a map id appears twice in FrlgPictures" }
    }
}
