package com.ironmonone.tracker.nuzlocke

/** An area as the ledger keeps it: a key that stays the same for the whole run, and the name to show. */
data class AreaKey(val key: String, val name: String)

/**
 * Turns the map the tracker reports into the area a Nuzlocke counts (2026-09-29).
 *
 * The tracker knows a map by its layout id and the reference tracker's route name ("Route 3", "Mt. Moon B1F").
 * The rules want a place: a cave is one area whatever its floors, "Route 21 North" and "Route 21 South" are
 * one route, the Safari Zone is one area or one per zone as the player chose. The research prefers the game's
 * own map section (the "met at" place) for this, which the tracker does not read yet; until it does, the name
 * is grouped here. A grouping is only a key: the player can move an encounter by hand.
 */
object NuzlockeAreas {

    private val ROUTE = Regex("^(Route \\d+)\\b.*$")
    private val FLOOR = Regex("\\s+B?\\d+F(\\s+\\d+R)?$")
    private val TRAILING_NUMBER = Regex("\\s+\\d+$")
    private val HI_LO = Regex("\\s+(Lo|Hi)-\\d+$")

    /**
     * Places made of several maps. A map whose name starts with the prefix is that place, so every floor and
     * side room of a cave shares one encounter. The first prefix that matches wins.
     */
    private val PLACES: List<Pair<String, String>> = listOf(
        "Mt. Moon" to "Mt. Moon",
        "Rock Tunnel" to "Rock Tunnel",
        "Seafoam Islands" to "Seafoam Islands",
        "Victory Road" to "Victory Road",
        "Rocket Hideout" to "Rocket Hideout",
        "Silph Co." to "Silph Co.",
        "Pokémon Tower" to "Pokémon Tower",
        "Poké Mansion" to "Poké Mansion",
        "Cerulean Cave" to "Cerulean Cave",
        "Diglett's Cave" to "Diglett's Cave",
        "S.S. Anne" to "S.S. Anne",
        "Mt. Ember" to "Mt. Ember",
        "Summit Path" to "Mt. Ember",
        "Ruby Path" to "Mt. Ember",
        "Icefall Cave" to "Icefall Cave",
        "Lost Cave" to "Lost Cave",
        "Trainer Tower" to "Trainer Tower",
        "Meteor Falls" to "Meteor Falls",
        "Granite Cave" to "Granite Cave",
        "Mt. Pyre" to "Mt. Pyre",
        "Seafloor Cavern" to "Seafloor Cavern",
        "Cave of Origin" to "Cave of Origin",
        "Sky Pillar" to "Sky Pillar",
        "Mirage Tower" to "Mirage Tower",
        "Artisan Cave" to "Artisan Cave",
        "Magma Hideout" to "Magma Hideout",
        "Aqua Hideout" to "Aqua Hideout",
        "Abandoned Ship" to "Abandoned Ship",
        "Shoal Cave" to "Shoal Cave",
        "New Mauville" to "New Mauville",
        "S.S. Tidal" to "S.S. Tidal",
    )

    /** The name of a place with its floors and side maps folded in. */
    fun placeName(raw: String, floorsMerged: Boolean): String {
        var n = raw.trim()
        // "Route 21 North", "Route 126 Water": one route, always. The water map of a route is the same place.
        ROUTE.matchEntire(n)?.let { return it.groupValues[1] }
        if (!floorsMerged) return n
        PLACES.firstOrNull { n.startsWith(it.first) }?.let { return it.second }
        n = FLOOR.replace(n, "")
        n = HI_LO.replace(n, "")
        n = TRAILING_NUMBER.replace(n, "")
        return n.trim()
    }

    /**
     * The area for a map and, when it matters, the way the battle began. Water gets its own key only when the
     * rules say fishing and surfing are an area of their own. [system] picks how the map's name is read: the Game
     * Boy games name a whole place already, the DS tracker's names need their typos and buildings folded.
     */
    fun of(area: NzArea, method: Method?, rules: NuzlockeRules, system: NuzlockeSystem = NuzlockeSystem.GEN3): AreaKey {
        val raw = area.name?.takeIf { it.isNotBlank() }
        val base = when {
            raw == null && area.mapId != null -> return AreaKey("map#${area.mapId}", "Map ${area.mapId}")
            raw == null -> return AreaKey("unknown", "Unknown place")
            system == NuzlockeSystem.GEN1 || system == NuzlockeSystem.GEN2 -> gameBoyPlace(raw, area, rules)
            system == NuzlockeSystem.GEN4 || system == NuzlockeSystem.GEN5 -> dsPlace(raw, area, rules)
            raw.startsWith("Safari Zone") -> if (rules.safari == SafariRule.ONE_AREA) "Safari Zone" else raw.trim()
            else -> placeName(raw, rules.floorsMerged)
        }
        if (rules.waterSeparate && method?.isWater == true) return AreaKey("$base|water", "$base (water)")
        return AreaKey(base, base)
    }

    /**
     * Game Boy: the tracker's name is the place ("Mt. Moon", "Route 3"), from the game's own map tables, so every floor
     * of a cave already shares it. The specific map ("Mt. Moon B1F") is the area only when floors are kept apart. The
     * Kanto Safari Zone's four zones are "Safari Zone East" and so on.
     */
    private fun gameBoyPlace(raw: String, area: NzArea, rules: NuzlockeRules): String {
        val name = raw.trim()
        if (name.startsWith("Safari Zone")) return if (rules.safari == SafariRule.ONE_AREA) "Safari Zone" else name
        if (!rules.floorsMerged) area.detail?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return name
    }

    /** Places of the DS games the tracker names once for every floor or room, so per-map areas are only possible by map id. */
    private val DS_MULTI_MAP = setOf(
        "Mt. Coronet", "Solaceon Ruins", "Turnback Cave", "Old Chateau", "Iron Island", "Victory Road", "Snowpoint Temple",
        "Distortion World", "Oreburgh Mine", "Wayward Cave", "Ruin Maniac Cave", "Stark Mountain", "Eterna Forest", "Galactic HQ",
        "Mt. Silver Cave", "Seafoam Islands", "Ruins of Alph", "Union Cave", "Mt. Mortar", "Ice Path", "Whirl Islands", "Mt. Moon",
        "Cerulean Cave", "Bell Tower", "Burned Tower", "Dark Cave", "Slowpoke Well", "Rock Tunnel", "Tin Tower", "Team Rocket HQ",
        "Relic Castle", "Chargestone Cave", "Twist Mountain", "Mistralton Cave", "Wellspring Cave", "Celestial Tower",
        "Giant Chasm", "Dragonspiral Tower", "Reversal Mountain", "Seaside Cave", "Clay Tunnel", "Lostlorn Forest",
        "Pinwheel Forest", "Strange House", "Plasma Frigate", "Castelia Sewers", "Relic Passage", "Challenger's Cave",
    )

    /** The DS tracker's own spellings that are wrong or split one place in two (its location tables are the reference tracker's). */
    private val DS_FIXES = mapOf(
        "Rpck Peak Ruins" to "Rock Peak Ruins", "Mr. Mortar" to "Mt. Mortar", "Cinnarbar Island" to "Cinnabar Island",
        "Diglett Cave" to "Diglett's Cave", "Sprout Tower 1F" to "Sprout Tower", "Sprout Tower 2F" to "Sprout Tower",
    )

    private val GYM_SUFFIX = "'s gym"

    /**
     * DS: the tracker's LocationData name. A city's gym is the city, the Bug-Catching Contest ("Tues Bug Catching") is
     * one area of its own, the Great Marsh and the Safari Zone follow the Safari Zone rule (their zones are told
     * apart by map id), and floors are told apart by map id only when floors are kept apart.
     */
    private fun dsPlace(raw: String, area: NzArea, rules: NuzlockeRules): String {
        var n = raw.trim()
        n = DS_FIXES[n] ?: n
        if (n.endsWith(GYM_SUFFIX)) n = n.removeSuffix(GYM_SUFFIX).trim()
        if (n.endsWith("Bug Catching")) return "Bug-Catching Contest"
        val zones = n == "Great Marsh" || n == "Safari Zone"
        if (n.startsWith("Safari Zone") && rules.safari == SafariRule.ONE_AREA) return "Safari Zone"
        val split = (zones && rules.safari == SafariRule.PER_ZONE) || (!rules.floorsMerged && n in DS_MULTI_MAP)
        return if (split && area.mapId != null && area.mapId != 0) "$n, map ${area.mapId}" else n
    }
}
