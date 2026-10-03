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
        HackLink("MaxDex Kaizo IronMON", "Trip", "Nat. Dex Kaizo with moves and abilities through Gen 9. KaizoCore already includes it. Tap PATCH on your FireRed 1.1 in My games and pick MaxDex.",
            MaxDexInfo.REPO, setOf("firered-u-v11"), "FireRed 1.1 (US)"),
        // ---- Emerald ----
        HackLink("Faster Emerald", "DrMaple", "Quality of life for IronMON: shorter intro, instant healing, hidden items marked. KaizoCore already includes it.",
            "https://github.com/DrMaple/Faster-Emerald/releases", setOf("emerald-u"), "Emerald (US)"),
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
        HackLink("IronMON HGSS", "PyroMikeGit", "Quality of life for IronMON: faster animations, the intro skipped, less talk. KaizoCore already includes it.",
            "https://github.com/PyroMikeGit/IronMONHGSS/releases", setOf("heartgold-u"), "HeartGold (US)"),
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
        HackLink("Faster Black 2 / White 2", "SilverstarStream", "Skips most cutscenes, made for the randomizer. KaizoCore already includes it.",
            "https://github.com/SilverstarStream/faster_black2_white2/releases", setOf("black2-u", "white2-u"), "Black 2 or White 2 (US/EU)"),
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

    // ---------------------------------------------------- why none of the player's patches fits

    private fun gameOf(k: com.ironmonone.core.RomKind) = k.displayName.substringAfter(' ').substringBefore(" (")

    /** "1.1" for FireRed v1.1; null for a game with one release. */
    private fun revisionOf(k: com.ironmonone.core.RomKind): String? =
        k.displayName.substringAfterLast(" v", "").takeIf { it.isNotEmpty() && it[0].isDigit() }

    /** The base game as a hack's page says it: "FireRed 1.0 (US)", "Emerald (US)". */
    private fun needsOf(k: com.ironmonone.core.RomKind) = gameOf(k) + (revisionOf(k)?.let { " $it" } ?: "") + " (US)"

    /** The game a patch says it is for, by the checksum it carries or the game the player declared: null when that is no game this app knows. */
    private fun baseOf(p: LibraryStore.PatchEntry): com.ironmonone.core.RomKind? = p.forCrc?.let { crc ->
        com.ironmonone.core.RomKind.all.firstOrNull { it.expectedCrc != com.ironmonone.core.RomKind.CRC_UNKNOWN && it.expectedCrc == crc }
    }

    /**
     * The game as it comes, from a build made of it: a patched kind names its base, a Nat. Dex kind is the Nat. Dex
     * capable game of its family, any other kind is itself.
     */
    private fun cleanBaseOf(k: com.ironmonone.core.RomKind): com.ironmonone.core.RomKind? = when {
        k.baseId != null -> com.ironmonone.core.RomKind.byId(k.baseId)
        k.isNatDex -> com.ironmonone.core.RomKind.allV1.firstOrNull { it.natDexCapable && it.family == k.family && it.titleDetect == k.titleDetect }
        else -> k
    }

    /** A patch file's hack, named as its page names it when the file name says so ("Radical Red 4.1" is Radical Red), else the file's own name. */
    private fun hackNameOf(p: LibraryStore.PatchEntry): String =
        all.filter { p.name.contains(it.name, ignoreCase = true) }.maxByOrNull { it.name.length }?.name ?: stripKnownExt(p.name)

    /**
     * Why none of the player's patches fits [game], from the game each patch says it is for and the hack's own name
     * (2026-09-30, UX audit P1): "Radical Red needs FireRed 1.0 (US). Your FireRed is 1.1, so it will not fit. Add a
     * FireRed 1.0 file to use it." Null when no patch is for this game's family, or the game is one this app does not
     * know: that is a different problem, and the caller says "none is made for it".
     */
    fun noFitReason(game: LibraryStore.Entry, patches: List<LibraryStore.PatchEntry>): String? {
        val picked = game.kind ?: return null
        val alreadyPatched = picked.isNatDex || picked.baseId != null
        val mine = cleanBaseOf(picked) ?: return null
        for (p in patches) {
            val wanted = baseOf(p)?.let { cleanBaseOf(it) } ?: continue
            if (familyOf(wanted.id) != familyOf(mine.id)) continue
            val hack = hackNameOf(p)
            val needs = needsOf(wanted)
            val file = needs.substringBefore(" (")
            val a = if (file.first() in "AEIOU") "an" else "a"
            return when {
                // The patch is for the very game the picked one was made from: the picked one is already patched.
                wanted.id == mine.id && alreadyPatched ->
                    "$hack needs $needs. The game you picked is already patched, so it will not fit. Pick your original $file instead."
                wanted.id == mine.id ->
                    "$hack needs $needs. The file you picked is not an exact copy of it, so it will not fit. Add $a $file file to use it."
                else ->
                    "$hack needs $needs. Your ${gameOf(mine)} is ${revisionOf(mine) ?: "another release"}, so it will not fit. Add $a $file file to use it."
            }
        }
        return null
    }

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
