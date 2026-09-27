package com.ironmonone.app

/**
 * Where to find ROM hacks, per game (Blake, 2026-09-27: "click the game and
 * then you have a list of links rom hacks for that game").
 *
 * Every link is the hack's own home: the creator's forum thread, GitHub
 * releases or shop page, each opened and checked on 2026-09-27 (first post by
 * the creator, patch offered there, base game stated there). Never a mirror
 * site: those bundle pre-patched games and ads. The app opens the page in the
 * browser; it downloads nothing itself.
 *
 * [fullRomPage] marks a page that also offers finished game files next to the
 * patch. Blake asked for those to be listed with a warning rather than left
 * out (2026-09-27). Google Play rejects apps that point at such pages, so drop
 * them if the app ever goes to Play.
 *
 * [ids] are the clean RomKind ids the patch is made for; [needs] says the same
 * in words, revision included, because a patch on the wrong revision fails.
 */
data class HackLink(
    val name: String,
    val author: String,
    val blurb: String,
    val url: String,
    val ids: Set<String>,
    val needs: String,
    val fullRomPage: Boolean = false,
)

object HackLinks {

    /** A game in the browser: its chip label and the RomKind family key (the id up to the first dash). */
    data class Family(val key: String, val label: String)

    private const val PC = "https://www.pokecommunity.com/threads/"

    val all: List<HackLink> = listOf(
        // ---- FireRed ----
        HackLink("Radical Red", "soupercell", "Harder FireRed with modern Pokémon, moves and abilities.",
            PC + "437688/", setOf("firered-u-v10"), "FireRed 1.0 (US)"),
        HackLink("Pokémon Unbound", "Skeli", "A new region and story built on FireRed, with difficulty options.",
            PC + "382178/", setOf("firered-u-v10"), "FireRed 1.0 (US)"),
        HackLink("Pokémon Gaia", "Spherical Ice", "A new region and story. The beta ends at the Pokémon League.",
            PC + "326118/", setOf("firered-u-v10"), "FireRed 1.0 (US)"),
        HackLink("Faster FireRed", "DrMaple", "Quality of life for IronMON: faster, hidden items marked. KaizoCore already includes it.",
            "https://github.com/DrMaple/Faster-FireRed/releases", setOf("firered-u-v11"), "FireRed 1.1 (US)"),
        // ---- Emerald ----
        HackLink("Emerald Kaizo", "SinisterHoodedFigure", "A very hard Emerald. Every mistake counts.",
            PC + "395830/", setOf("emerald-u"), "Emerald (US)"),
        HackLink("Run & Bun", "dekzeh", "A difficulty hack with about 500 hand-built battles.",
            PC + "493223/", setOf("emerald-u"), "Emerald (US)"),
        HackLink("Inclement Emerald", "Buffel Saft", "A difficulty and catch-them-all hack in the style of Drayano's.",
            PC + "457039/", setOf("emerald-u"), "Emerald (US)"),
        HackLink("Emerald Imperium", "iriv24", "A difficulty hack with modern mechanics and reusable TMs.",
            PC + "534582/", setOf("emerald-u"), "Emerald (US)"),
        HackLink("Emerald Rogue", "Pokabbie", "A roguelite: every run through Hoenn is different.",
            PC + "479406/", setOf("emerald-u"), "Emerald (US)"),
        HackLink("Emerald Legacy", "TheSmithPlays", "Emerald polished and rebalanced, with better bosses and a postgame.",
            "https://github.com/cRz-Shadows/Pokemon_Emerald_Legacy/releases", setOf("emerald-u"), "Emerald (US)"),
        HackLink("Emerald Seaglass", "Nemo622", "New graphics, a bigger Pokédex and the Battle Frontier.",
            "https://ko-fi.com/s/4a1535f351", setOf("emerald-u"), "Emerald (US)"),
        // ---- Platinum ----
        HackLink("Renegade Platinum", "Drayano", "All 493 Pokémon, tougher trainers, no trade evolutions.",
            "https://projectpokemon.org/home/forums/topic/52294-pok%C3%A9mon-renegade-platinum/", setOf("platinum-u"), "Platinum (US)"),
        // ---- HeartGold / SoulSilver ----
        HackLink("Sacred Gold", "Drayano", "All 493 Pokémon and a steeper difficulty curve.",
            "https://gbatemp.net/threads/pokemon-sacred-gold-storm-silver.327567/", setOf("heartgold-u"), "HeartGold (US)"),
        HackLink("Storm Silver", "Drayano", "All 493 Pokémon and a steeper difficulty curve.",
            "https://gbatemp.net/threads/pokemon-sacred-gold-storm-silver.327567/", setOf("soulsilver-u"), "SoulSilver (US)"),
        // ---- Black / White ----
        HackLink("Blaze Black", "Drayano", "All 649 Pokémon and harder trainers.",
            PC + "247696/", setOf("black-u"), "Black (US)"),
        HackLink("Volt White", "Drayano", "All 649 Pokémon and harder trainers.",
            PC + "247696/", setOf("white-u"), "White (US)"),
        // ---- Black 2 / White 2 ----
        HackLink("Blaze Black 2 Redux", "Drayano and AphexCubed", "The modern remake of Blaze Black 2, with difficulty modes. Patches are on Drayano's Google Drive, linked here.",
            "https://x.com/Drayano60/status/1507817345103929344", setOf("black2-u"), "Black 2 (US)"),
        HackLink("Volt White 2 Redux", "Drayano and AphexCubed", "The modern remake of Volt White 2, with difficulty modes. Patches are on Drayano's Google Drive, linked here.",
            "https://x.com/Drayano60/status/1507817345103929344", setOf("white2-u"), "White 2 (US)"),
        HackLink("Blaze Black 2 (original)", "Drayano", "All 649 Pokémon and harder trainers.",
            "https://projectpokemon.org/home/forums/topic/24126-pok%C3%A9mon-blaze-black-2-pok%C3%A9mon-volt-white-2/", setOf("black2-u"), "Black 2 (US)"),
        HackLink("Volt White 2 (original)", "Drayano", "All 649 Pokémon and harder trainers.",
            "https://projectpokemon.org/home/forums/topic/24126-pok%C3%A9mon-blaze-black-2-pok%C3%A9mon-volt-white-2/", setOf("white2-u"), "White 2 (US)"),
        // ---- Crystal ----
        HackLink("Crystal Kaizo", "SinisterHoodedFigure", "A very hard Crystal. All 251 Pokémon can be caught.",
            PC + "334713/", setOf("crystal-u"), "Crystal (US)"),
        HackLink("Crystal Legacy", "TheSmithPlays", "Crystal fixed and polished, true to Gen 2.",
            "https://github.com/cRz-Shadows/Pokemon_Crystal_Legacy/releases", setOf("crystal-u"), "Crystal (US)"),
        HackLink("Polished Crystal", "Rangi42", "A big upgrade to Crystal with modern features and new content.",
            "https://github.com/Rangi42/polishedcrystal/releases", setOf("crystal-u"), "Crystal (US)", fullRomPage = true),
        // ---- Red / Blue / Yellow ----
        HackLink("Shin Pokémon Red", "jojobear13", "Bugs fixed, smarter trainers and an optional hard mode.",
            "https://github.com/jojobear13/shinpokered/releases", setOf("red-u"), "Red (US)"),
        HackLink("Shin Pokémon Blue", "jojobear13", "Bugs fixed, smarter trainers and an optional hard mode.",
            "https://github.com/jojobear13/shinpokered/releases", setOf("blue-u"), "Blue (US)"),
        HackLink("Blue Kaizo", "SinisterHoodedFigure", "A very hard Blue. All 151 Pokémon can be caught.",
            PC + "322127/", setOf("blue-u"), "Blue (US)"),
        HackLink("Yellow Legacy", "TheSmithPlays", "Yellow fixed and rebalanced, true to Gen 1.",
            "https://github.com/cRz-Shadows/Pokemon_Yellow_Legacy/releases", setOf("yellow-u"), "Yellow (US)"),
    )

    /** The games with at least one link, in the order they are offered. */
    val families: List<Family> = listOf(
        Family("firered", "FireRed"), Family("emerald", "Emerald"), Family("platinum", "Platinum"),
        Family("heartgold", "HeartGold"), Family("soulsilver", "SoulSilver"), Family("black", "Black"),
        Family("white", "White"), Family("black2", "Black 2"), Family("white2", "White 2"),
        Family("crystal", "Crystal"), Family("red", "Red"), Family("blue", "Blue"), Family("yellow", "Yellow"),
    ).filter { f -> all.any { l -> l.ids.any { familyOf(it) == f.key } } }

    /** "firered-u-v10" -> "firered", "black2-u" -> "black2", "emerald-natdex-121" -> "emerald". */
    fun familyOf(romKindId: String): String = romKindId.substringBefore('-')

    fun forFamily(key: String): List<HackLink> = all.filter { l -> l.ids.any { familyOf(it) == key } }

    /** The clean game a picked library entry comes from: a patched kind names its base. */
    fun baseIdOf(kind: com.ironmonone.core.RomKind?): String? = kind?.let { it.baseId ?: it.id }

    /**
     * What is wrong, if anything, with patching [picked] with [link]: a
     * different revision, or a copy that is already patched. Null when it fits.
     */
    fun mismatch(link: HackLink, picked: com.ironmonone.core.RomKind?): String? {
        if (picked == null) return null
        val base = baseIdOf(picked) ?: return null
        if (familyOf(base) != familyOf(link.ids.first())) return null
        return when {
            base !in link.ids -> "Needs ${link.needs}. The game you picked is ${picked.displayName}, so this patch will not fit it."
            picked.baseId != null || picked.isNatDex -> "Patch your original ${link.needs}, not this already patched copy."
            else -> null
        }
    }
}
