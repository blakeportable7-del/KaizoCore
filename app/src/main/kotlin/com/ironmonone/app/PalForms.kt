package com.ironmonone.app

import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.EnemyPartyMon
import com.ironmonone.tracker.Gen12Nuzlocke
import com.ironmonone.tracker.PokemonDecoder

/**
 * How a Walking Pals sheet is picked past the species (Blake, 2026-10-03: "If they are shiny you should be able to play as
 * shiny", "And forms sprites"): whether the Pokemon is shiny, and the form its game draws it in where the species id does
 * not say. The formulas are the games' own, as pret's decompilations have them:
 *
 * - Unown's letter. Gen 3 (pokeemerald and pokefirered include/pokemon.h GET_UNOWN_LETTER, pokeruby
 *   src/pokemon_icon.c GetUnownLetterByPersonality): two bits from each byte of the personality, 0 to 27, A to Z then ! and
 *   ?. A Nat. Dex build keeps the vanilla rule (CFRU's GetUnownLetterFromPersonality). Gen 2 (pokecrystal
 *   engine/gfx/load_pics.asm GetUnownLetter): the middle two bits of the Attack, Defense, Speed and Special DVs, divided by
 *   10, A to Z. Unown A is the species' own sheet; the other letters are the second set's "201-b" to "201-question".
 * - Deoxys in a retail Gen 3 game, in its game's form: Normal in Ruby and Sapphire (pokeruby draws no other), Attack in
 *   FireRed, Defense in LeafGreen (pokefirered src/pokemon.c sDeoxysBaseStats, src/data/graphics/pokemon.h front_def),
 *   Speed in Emerald (pokeemerald src/pokemon.c sDeoxysBaseStats, src/decompress.c DuplicateDeoxysTiles). A Nat. Dex or
 *   MaxDex build numbers the forms as Pokemon of their own, so its Deoxys is the Normal one.
 * - Castform is Normal on the overworld: its weather forms are battle forms ([overworld]).
 *
 * A form or a shiny with no sheet is the species' own sheets, silently (WalkingPals.Index.look).
 */
object PalForms {
    /** Unown, in Gen 3's numbering and the national one alike. */
    const val UNOWN = 201
    /** Deoxys in Gen 3's own numbering. */
    const val DEOXYS_GEN3 = 410
    /** Castform in Gen 3's own numbering, and its weather forms in a Nat. Dex or MaxDex build (Castform-F, -W, -I). */
    const val CASTFORM_GEN3 = 385
    val CASTFORM_WEATHER = 1162..1164
    /** Heart & Soul's Castform and its weather forms (layout-kaizo.json SPECIES_CASTFORM, _SUNNY, _RAINY, _SNOWY). */
    const val CASTFORM_HNS = 351
    val CASTFORM_WEATHER_HNS = 1051..1053

    /** pokeemerald's GET_UNOWN_LETTER: 0 = A, 25 = Z, 26 = !, 27 = ?. */
    fun unownLetterGen3(personality: Long): Int = (
        ((personality and 0x03000000L) shr 18) or ((personality and 0x00030000L) shr 12) or
            ((personality and 0x00000300L) shr 6) or (personality and 0x00000003L)
        ).toInt() % 28

    /** pokecrystal's GetUnownLetter, from the four DVs: 0 = A to 25 = Z. */
    fun unownLetterGen2(atk: Int, def: Int, spd: Int, spc: Int): Int =
        ((((atk shr 1) and 3) shl 6) or (((def shr 1) and 3) shl 4) or (((spd shr 1) and 3) shl 2) or ((spc shr 1) and 3)) / 10

    /** The second set's key for a letter: null for A (the species' own sheet), "201-b" to "201-z", "201-exclamation", "201-question". */
    fun unownForm(letter: Int): String? = when (letter) {
        in 1..25 -> "$UNOWN-${'a' + letter}"
        26 -> "$UNOWN-exclamation"
        27 -> "$UNOWN-question"
        else -> null
    }

    /** The form a retail Gen 3 game draws Deoxys in, by GameMap.routeVersion; null for Normal (Ruby, Sapphire, unknown). */
    fun deoxysForm(routeVersion: String): String? = when (routeVersion) {
        "firered" -> "386-attack"
        "leafgreen" -> "386-defense"
        "emerald" -> "386-speed"
        else -> null
    }

    /**
     * A Gen 3 game's Pokemon: [species] as [dex] numbers it (Gen 3's own ids, or a Nat. Dex or MaxDex build's), its
     * personality, whether it is shiny, and the game ([routeVersion]: GameMap.routeVersion, a Nat. Dex build's base game).
     */
    fun gen3(species: Int, dex: WalkingPals.Dex, personality: Long, shiny: Boolean, routeVersion: String): WalkingPals.Look =
        WalkingPals.Look(
            form = when {
                species == UNOWN && dex != WalkingPals.Dex.NATIONAL -> unownForm(unownLetterGen3(personality))
                species == DEOXYS_GEN3 && dex == WalkingPals.Dex.GEN3 -> deoxysForm(routeVersion)
                else -> null
            },
            shiny = shiny,
        )

    /**
     * A DS Pokemon by the form its data carries (Gen4.Mon.form, the byte at +0x18 shifted down three, 0 its first form),
     * for the walking sheets a form has of its own (RC35-NOTICED row 42): it walked as its species on DS. The form
     * numbers are the games' own, Gen 4's and Gen 5's alike (pokeplatinum and pokeheartgold's FORM constants; Gen 5
     * keeps Gen 4's and numbers its own new forms from 1). A form with no sheet of its own walks as its species
     * (WalkingPals.Index.find), as Burmy's cloaks and Shellos's East Sea do.
     */
    fun ds(species: Int, form: Int, shiny: Boolean): WalkingPals.Look =
        WalkingPals.Look(form = if (form <= 0) null else DS_FORMS[species]?.getOrNull(form - 1) ?: if (species == UNOWN) unownForm(form) else null, shiny = shiny)

    /** Each species' forms past its first, in the games' order: form 1 is the first entry. */
    private val DS_FORMS: Map<Int, List<String>> = mapOf(
        351 to listOf("351-sunny", "351-rainy", "351-snowy"),          // Castform
        386 to listOf("386-attack", "386-defense", "386-speed"),       // Deoxys
        412 to listOf("412-sand", "412-trash"),                        // Burmy
        413 to listOf("413-sand", "413-trash"),                        // Wormadam
        421 to listOf("421-sunshine"),                                 // Cherrim
        479 to listOf("479-heat", "479-wash", "479-frost", "479-fan", "479-mow"),   // Rotom
        487 to listOf("487-origin"),                                   // Giratina
        492 to listOf("492-sky"),                                      // Shaymin
        550 to listOf("550-blue"),                                     // Basculin
        555 to listOf("555-zen"),                                      // Darmanitan
        645 to listOf("645-therian"),                                  // Landorus
        646 to listOf("646-white", "646-black"),                       // Kyurem
        648 to listOf("648-pirouette"),                                // Meloetta
    )

    /** A Gold, Silver or Crystal Pokemon by its 16-bit DV word (Attack, Defense, Speed, Special, high nibble first). */
    fun gen2(species: Int, dvs: Int): WalkingPals.Look {
        if (dvs < 0) return WalkingPals.Look()
        val atk = (dvs shr 12) and 15; val def = (dvs shr 8) and 15; val spd = (dvs shr 4) and 15; val spc = dvs and 15
        return WalkingPals.Look(
            form = if (species == UNOWN) unownForm(unownLetterGen2(atk, def, spd, spc)) else null,
            shiny = Gen12Nuzlocke.shiny(2, dvs),
        )
    }

    /** The DV word of a Game Boy party Pokemon as the tracker keeps it ([PokemonDecoder.Mon.ivs]: HP, Attack, Defense, Speed, Special). */
    fun dvsOf(m: PokemonDecoder.Mon): Int =
        if (m.ivs.size < 5) -1 else (m.ivs[1] shl 12) or (m.ivs[2] shl 8) or (m.ivs[3] shl 4) or m.ivs[4]

    /** One of your Pokemon on the GBA and Game Boy tracker card, by the game's generation, numbering and version. */
    fun ofMon(m: PokemonDecoder.Mon, generation: Int, dex: WalkingPals.Dex, routeVersion: String): WalkingPals.Look = when (generation) {
        3 -> gen3(m.species, dex, m.pid, m.shiny, routeVersion)
        2 -> gen2(m.species, dvsOf(m))
        else -> WalkingPals.Look()   // Gen 1 has no shinies and no forms
    }

    /**
     * The opponent on the same card: Gen 3 by its personality, shiny when its party slot is (found as the Nuzlocke reads
     * it, by personality); Gen 2 by its DVs (EnemyInfo.dvs). An opponent's Deoxys walks in its Normal form, as the battle
     * draws it whatever the game, so no game is passed on for it ([routeVersion] stays in the signature for the callers).
     */
    @Suppress("UNUSED_PARAMETER")
    fun ofEnemy(e: EnemyInfo, party: List<EnemyPartyMon>, generation: Int, dex: WalkingPals.Dex, routeVersion: String): WalkingPals.Look = when (generation) {
        3 -> gen3(e.species, dex, e.pid, party.firstOrNull { it.pid != 0L && it.pid == e.pid }?.shiny == true, routeVersion = "")
        2 -> gen2(e.species, e.dvs)
        else -> WalkingPals.Look()
    }

    /**
     * The species to walk as on the overworld: Castform's weather forms, where a Nat. Dex or MaxDex build numbers them as
     * Pokemon of their own, are Castform; every other species is itself. A retail game's Castform is always Normal there.
     */
    fun overworld(species: Int, dex: WalkingPals.Dex): Int = when {
        (dex == WalkingPals.Dex.NAT_DEX || dex == WalkingPals.Dex.MAX_DEX) && species in CASTFORM_WEATHER -> CASTFORM_GEN3
        dex == WalkingPals.Dex.HNS && species in CASTFORM_WEATHER_HNS -> CASTFORM_HNS
        else -> species
    }
}
