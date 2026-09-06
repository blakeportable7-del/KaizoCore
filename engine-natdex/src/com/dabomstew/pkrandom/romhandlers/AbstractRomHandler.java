package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  AbstractRomHandler.java - a base class for all rom handlers which     --*/
/*--                            implements the majority of the actual       --*/
/*--                            randomizer logic by building on the base    --*/
/*--                            getters & setters provided by each concrete --*/
/*--                            handler.                                    --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.io.PrintStream;
import java.security.KeyStore.TrustedCertificateEntry;
import java.util.*;
import java.util.stream.Collectors;

import com.dabomstew.pkrandom.*;
import com.dabomstew.pkrandom.constants.*;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.*;

public abstract class AbstractRomHandler implements RomHandler {

    private boolean restrictionsSet;
    protected List<Pokemon> mainPokemonList;
    protected List<Pokemon> mainPokemonListInclFormes;
    private List<Pokemon> altFormesList;
    private List<MegaEvolution> megaEvolutionsList;
    private List<Pokemon> noLegendaryList, onlyLegendaryList, ultraBeastList;
    private List<Pokemon> noLegendaryListInclFormes, onlyLegendaryListInclFormes;
    private List<Pokemon> noLegendaryAltsList, onlyLegendaryAltsList;
    private List<Pokemon> pickedStarters;
    protected final Random random;
    private final Random cosmeticRandom;
    protected PrintStream logStream;
    private List<Pokemon> alreadyPicked = new ArrayList<>();
    private Map<Pokemon, Integer> placementHistory = new HashMap<>();
    private Map<Integer, Integer> itemPlacementHistory = new HashMap<>();
    private int fullyEvolvedRandomSeed;
    private Map<Integer, Double> speciesWeights;
    private Map<Integer, Integer> preRandomizationBstForPowerLevels = new HashMap<>();
    boolean isORAS = false;
    boolean isSM = false;
    int perfectAccuracy = 100;

    /* Constructor */

    public AbstractRomHandler(Random random, PrintStream logStream) {
        this.random = random;
        this.cosmeticRandom = RandomSource.cosmeticInstance();
        this.fullyEvolvedRandomSeed = -1;
        this.logStream = logStream;
        initializeSpeciesWeights(null);
    }

    /*
     * Public Methods, implemented here for all gens. Unlikely to be overridden.
     */

    private void initializeSpeciesWeights(Settings settings) {
        boolean abilitiesAreRandomized = settings != null && settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        speciesWeights = new HashMap<>();
        // all mon forms with same bst as base (except regional forms) have a weighted probability of being rolled
        // pumpkaboo, gourgeist, gimmighoul, aegislash, toxtricity, eiscue and gendered forms except oinkologne have lower probability, with the weightings of all forms summing to 1
        speciesWeights.put(Species.taurosP, 0.5);
        speciesWeights.put(Species.taurosPF, 0.5);
        speciesWeights.put(Species.taurosPW, 0.5);

        speciesWeights.put(Species.deoxys, 0.5);
        speciesWeights.put(Species.deoxysA, 0.5);
        speciesWeights.put(Species.deoxysD, 0.5);
        speciesWeights.put(Species.deoxysS, 0.5);

        speciesWeights.put(Species.wormadam, 0.5);
        speciesWeights.put(Species.wormadamS, 0.5);
        speciesWeights.put(Species.wormadamT, 0.5);

        speciesWeights.put(Species.rotomH, 0.5);
        speciesWeights.put(Species.rotomW, 0.5);
        speciesWeights.put(Species.rotomFr, 0.5);
        speciesWeights.put(Species.rotomFa, 0.5);
        speciesWeights.put(Species.rotomM, 0.5);

        speciesWeights.put(Species.dialga, 0.75);
        speciesWeights.put(Species.dialgaO, 0.75);

        speciesWeights.put(Species.palkia, 0.75);
        speciesWeights.put(Species.palkiaO, 0.75);

        speciesWeights.put(Species.giratina, 0.75);
        speciesWeights.put(Species.giratinaO, 0.75);

        // white striped basculin is not weighted since it evolves
        speciesWeights.put(Species.basculin, 0.5);
        speciesWeights.put(Species.basculinB, 0.5);

        speciesWeights.put(Species.tornadus, 0.75);
        speciesWeights.put(Species.tornadusT, 0.75);

        speciesWeights.put(Species.thundurus, 0.75);
        speciesWeights.put(Species.thundurusT, 0.75);

        speciesWeights.put(Species.landorus, 0.75);
        speciesWeights.put(Species.landorusT, 0.75);

        speciesWeights.put(Species.kyuremW, 0.75);
        speciesWeights.put(Species.kyuremB, 0.75);

        // remove when relic song is implemented
        speciesWeights.put(Species.meloetta, 0.75);
        speciesWeights.put(Species.meloettaP, 0.75);

        speciesWeights.put(Species.meowstic, 0.5);
        speciesWeights.put(Species.meowsticF, 0.5);

        speciesWeights.put(Species.meowsticMega, 0.5);
        speciesWeights.put(Species.meowsticFMega, 0.5);

        // move to `if (abilitiesAreRandomized)` when stance change is implemented
        speciesWeights.put(Species.aegislash, 0.5);
        speciesWeights.put(Species.aegislashB, 0.5);

        speciesWeights.put(Species.pumpkaboo, 0.25);
        speciesWeights.put(Species.pumpkabooSmall, 0.25);
        speciesWeights.put(Species.pumpkabooLarge, 0.25);
        speciesWeights.put(Species.pumpkabooSuper, 0.25);

        speciesWeights.put(Species.gourgeist, 0.25);
        speciesWeights.put(Species.gourgeistSmall, 0.25);
        speciesWeights.put(Species.gourgeistLarge, 0.25);
        speciesWeights.put(Species.gourgeistSuper, 0.25);

        speciesWeights.put(Species.oricorio, 0.5);
        speciesWeights.put(Species.oricorioE, 0.5);
        speciesWeights.put(Species.oricorioP, 0.5);
        speciesWeights.put(Species.oricorioG, 0.5);

        speciesWeights.put(Species.lycanroc, 0.5);
        speciesWeights.put(Species.lycanrocM, 0.5);
        speciesWeights.put(Species.lycanrocD, 0.5);

        speciesWeights.put(Species.toxtricity, 0.5);
        speciesWeights.put(Species.toxtricityL, 0.5);

        // move to `if (abilitiesAreRandomized)` when ice face is implemented
        speciesWeights.put(Species.eiscue, 0.5);
        speciesWeights.put(Species.eiscueN, 0.5);

        speciesWeights.put(Species.indeedee, 0.5);
        speciesWeights.put(Species.indeedeeF, 0.5);

        speciesWeights.put(Species.urshifu, 0.75);
        speciesWeights.put(Species.urshifuR, 0.75);

        speciesWeights.put(Species.calyrexI, 0.75);
        speciesWeights.put(Species.calyrexS, 0.75);

        speciesWeights.put(Species.basculegion, 0.5);
        speciesWeights.put(Species.basculegionF, 0.5);

        speciesWeights.put(Species.enamorus, 0.75);
        speciesWeights.put(Species.enamorusT, 0.75);

        speciesWeights.put(Species.oinkologne, 0.75);
        speciesWeights.put(Species.oinkologneF, 0.75);

        speciesWeights.put(Species.squawkabilly, 0.5);
        speciesWeights.put(Species.squawkabillyW, 0.5);

        speciesWeights.put(Species.gimmighoul, 0.5);
        speciesWeights.put(Species.gimmighoulR, 0.5);

        speciesWeights.put(Species.ogerpon, 0.5);
        speciesWeights.put(Species.ogerponW, 0.5);
        speciesWeights.put(Species.ogerponF, 0.5);
        speciesWeights.put(Species.ogerponR, 0.5);

        if (abilitiesAreRandomized) {
            speciesWeights.put(Species.castform, 0.5);
            speciesWeights.put(Species.castformF, 0.5);
            speciesWeights.put(Species.castformW, 0.5);
            speciesWeights.put(Species.castformI, 0.5);
        }
    }

    private Pokemon weightedRandomPokemon(List<Pokemon> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        
        double totalWeight = 0.0;
        for (Pokemon pk : pool) {
            double weight = speciesWeights.getOrDefault(pk.number, 1.0);
            totalWeight += weight;
        }
        
        double randomValue = random.nextDouble() * totalWeight;
        
        double currentWeight = 0.0;
        for (Pokemon pk : pool) {
            double weight = speciesWeights.getOrDefault(pk.number, 1.0);
            currentWeight += weight;
            if (randomValue < currentWeight) {
                return pk;
            }
        }
        
        return pool.get(pool.size() - 1);
    }

    public void setLog(PrintStream logStream) {
        this.logStream = logStream;
    }

    public void setPokemonPool(Settings settings) {
        // Reinitialize species weights based on current settings
        initializeSpeciesWeights(settings);
        ensurePreRandomizationBstSnapshot();
        
        GenRestrictions restrictions = null;
        if (settings != null) {
            restrictions = settings.getCurrentRestrictions();

            // restrictions should already be null if "Limit Pokemon" is disabled, but this is a safeguard
            if (!settings.isLimitPokemon()) {
                restrictions = null;
            }
        }

        restrictionsSet = true;
        mainPokemonList = this.allPokemonWithoutNull();
        mainPokemonListInclFormes = this.allPokemonInclFormesWithoutNull();
        altFormesList = this.getAltFormes();
        megaEvolutionsList = this.getMegaEvolutions();
        if (restrictions != null) {
            mainPokemonList = new ArrayList<>();
            mainPokemonListInclFormes = new ArrayList<>();
            megaEvolutionsList = new ArrayList<>();
            List<Pokemon> allPokemon = this.getPokemon();

            if (restrictions.allow_gen1) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.bulbasaur, Species.mew);
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.venusaurMega, Species.mewtwoMegaY);
                }
                if (restrictions.allow_regional_forms) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.rattataA, Species.marowakA);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.meowthG, Species.moltresG);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.growlitheH, Species.electrodeH);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.taurosP, Species.taurosP);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.taurosPF, Species.taurosPW);
                }
            }

            if (restrictions.allow_gen2 && allPokemon.size() > Gen2Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.chikorita, Species.celebi);
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.meganiumMega, Species.tyranitarMega);
                }
                if (restrictions.allow_regional_forms) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.slowkingG, Species.corsolaG);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.typhlosionH, Species.sneaselH);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.wooperP, Species.wooperP);
                }
            }

            if (restrictions.allow_gen3 && allPokemon.size() > Gen3Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.treecko, Species.deoxys);
                addPokesFromRange(mainPokemonList, allPokemon, Species.castformF, Species.deoxysS);
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.sceptileMega, Species.latiosMega);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.rayquazaMega, Species.groudonP);
                }
                if (restrictions.allow_regional_forms) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.zigzagoonG, Species.linooneG);
                }
            }

            if (restrictions.allow_gen4 && allPokemon.size() > Gen4Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.turtwig, Species.arceus);
                addPokesFromRange(mainPokemonList, allPokemon, Species.burmyS, Species.rotomM);
                addPokesFromRange(mainPokemonList, allPokemon, Species.giratinaO, Species.shayminS);
                if (restrictions.allow_gen2) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.pichuSpiky, Species.pichuSpiky);
                }
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.staraptorMega, Species.darkraiMega);
                }
            }

            if (restrictions.allow_gen5 && allPokemon.size() > Gen5Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.victini, Species.genesect);
                addPokesFromRange(mainPokemonList, allPokemon, Species.basculinB, Species.basculinB);
                addPokesFromRange(mainPokemonList, allPokemon, Species.darmanitanZ, Species.darmanitanZ);
                addPokesFromRange(mainPokemonList, allPokemon, Species.tornadusT, Species.meloettaP);
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.emboarMega, Species.golurkMega);
                }
                if (restrictions.allow_regional_forms) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.darumakaG, Species.stunfiskG);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.samurottH, Species.braviaryH);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.darmanitanZG, Species.darmanitanZG);
                }
            }

            if (restrictions.allow_gen6 && allPokemon.size() > Gen6Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.chespin, Species.volcanion);
                addPokesFromRange(mainPokemonList, allPokemon, Species.floetteE, Species.gourgeistSuper);
                addPokesFromRange(mainPokemonList, allPokemon, Species.hoopaU, Species.hoopaU);
                if (restrictions.allow_gen1) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.pikachuCap, Species.pikachuCap);
                }
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.chesnaughtMega, Species.diancieMega);
                }
                if (restrictions.allow_regional_forms) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.sliggooH, Species.avaluggH);
                }
            }

            if (restrictions.allow_gen7 && allPokemon.size() > 809) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.rowlet, Species.melmetal);
                addPokesFromRange(mainPokemonList, allPokemon, Species.oricorioE, Species.necrozmaU);
                if (restrictions.allow_gen1) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.pikachuPartner, Species.pikachuPartner);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.eeveePartner, Species.eeveePartner);
                }
                if (restrictions.allow_gen6) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.greninjaB, Species.greninjaA);
                    addPokesFromRange(mainPokemonList, allPokemon, Species.zygarde10, Species.zygardeC);
                }
                if (restrictions.allow_regional_forms) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.decidueyeH, Species.decidueyeH);
                }
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.crabominableMega, Species.zeraoraMega);
                }
            }

            if (restrictions.allow_gen8 && allPokemon.size() > 905) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.grookey, Species.enamorus);
                addPokesFromRange(mainPokemonList, allPokemon, Species.toxtricityL, Species.zamazentaC);
                addPokesFromRange(mainPokemonList, allPokemon, Species.urshifuR, Species.calyrexS);
                addPokesFromRange(mainPokemonList, allPokemon, Species.basculegionF, Species.enamorusT);
                if (restrictions.allow_gen4) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.dialgaO, Species.palkiaO);
                }
                if (restrictions.allow_gen5) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.basculinW, Species.basculinW);
                }
                if(restrictions.allow_eternamax) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.eternatusE, Species.eternatusE);
                }
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.falinksMega, Species.falinksMega);
                }
            }

            if (restrictions.allow_gen9 && allPokemon.size() > 1025) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.sprigatito, Species.pecharunt);
                addPokesFromRange(mainPokemonList, allPokemon, Species.oinkologneF, Species.terapagosS);
                if (restrictions.allow_gen8) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.ursalunaB, Species.ursalunaB);
                }
                if (restrictions.allow_mega) {
                    addPokesFromRange(mainPokemonList, allPokemon, Species.scovillainMega, Species.baxcaliburMega);
                }
            }

            // If the user specified it, add all the evolutionary relatives for everything in the mainPokemonList
            if (restrictions.allow_evolutionary_relatives) {
                addEvolutionaryRelatives(mainPokemonList);
            }

            // Now that mainPokemonList has all the selected Pokemon, update mainPokemonListInclFormes too
            addAllPokesInclFormes(mainPokemonList, mainPokemonListInclFormes);

            // Populate megaEvolutionsList with all of the mega evolutions that exist in the pool
            List<MegaEvolution> allMegaEvolutions = this.getMegaEvolutions();
            for (MegaEvolution megaEvo : allMegaEvolutions) {
                if (mainPokemonListInclFormes.contains(megaEvo.to)) {
                    megaEvolutionsList.add(megaEvo);
                }
            }
        }

        noLegendaryList = new ArrayList<>();
        noLegendaryListInclFormes = new ArrayList<>();
        onlyLegendaryList = new ArrayList<>();
        onlyLegendaryListInclFormes = new ArrayList<>();
        noLegendaryAltsList = new ArrayList<>();
        onlyLegendaryAltsList = new ArrayList<>();
        ultraBeastList = new ArrayList<>();

        int bstLimit = settings.getWildBSTLimit();
        int wildPokemonBSTLimit = settings.getWildPokemonBSTLimit();
        for (Pokemon p : mainPokemonList) {
            if (wildPokemonBSTLimit > 0 && getPreRandomizationBstForPowerLevels(p) > wildPokemonBSTLimit) {
                continue;
            }
            // ban only strong legendaries and mythicals
            if (bstLimit == 2) {
                if (p.isStrongLegendary()) {
                    onlyLegendaryList.add(p);
                } else {
                    noLegendaryList.add(p);
                }
            // ban all legendaries and mythicals
            } else if (bstLimit == 1) {
                if (p.isLegendary()) {
                    onlyLegendaryList.add(p);
                } else {
                    noLegendaryList.add(p);
                }
            // ban legendaries, mythicals, ub and paradox
            } else {
                if (p.isLegendary()) {
                    onlyLegendaryList.add(p);
                } else if (p.isUBorParadox()) {
                    onlyLegendaryList.add(p);
                } else {
                    noLegendaryList.add(p);
                }
            }
        }
        for (Pokemon p : mainPokemonListInclFormes) {
            if (wildPokemonBSTLimit > 0 && getPreRandomizationBstForPowerLevels(p) > wildPokemonBSTLimit) {
                continue;
            }
            if (p.isLegendary()) {
                onlyLegendaryListInclFormes.add(p);
            } else if (!ultraBeastList.contains(p)) {
                noLegendaryListInclFormes.add(p);
            }
        }
        for (Pokemon f : altFormesList) {
            if (wildPokemonBSTLimit > 0 && getPreRandomizationBstForPowerLevels(f) > wildPokemonBSTLimit) {
                continue;
            }
            if (f.isLegendary()) {
                onlyLegendaryAltsList.add(f);
            } else {
                noLegendaryAltsList.add(f);
            }
        }
    }

    private void ensurePreRandomizationBstSnapshot() {
        if (!preRandomizationBstForPowerLevels.isEmpty()) {
            return;
        }
        for (Pokemon pk : allPokemonInclFormesWithoutNull()) {
            preRandomizationBstForPowerLevels.put(pk.number, pk.bstForPowerLevels());
        }
    }

    private int getPreRandomizationBstForPowerLevels(Pokemon pk) {
        Integer snapshot = preRandomizationBstForPowerLevels.get(pk.number);
        if (snapshot != null) {
            return snapshot;
        }
        if (pk.baseForme != null) {
            snapshot = preRandomizationBstForPowerLevels.get(pk.baseForme.number);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return pk.bstForPowerLevels();
    }

    private void addPokesFromRange(List<Pokemon> pokemonPool, List<Pokemon> allPokemon, int range_min, int range_max) {
        for (int i = range_min; i <= range_max; i++) {
            if (!pokemonPool.contains(allPokemon.get(i))) {
                pokemonPool.add(allPokemon.get(i));
            }
        }
    }

    private void addEvolutionaryRelatives(List<Pokemon> pokemonPool) {
        Set<Pokemon> newPokemon = new TreeSet<>();
        for (Pokemon pk : pokemonPool) {
            List<Pokemon> evolutionaryRelatives = getEvolutionaryRelatives(pk);
            for (Pokemon relative : evolutionaryRelatives) {
                if (!pokemonPool.contains(relative) && !newPokemon.contains(relative)) {
                    newPokemon.add(relative);
                }
            }
        }

        pokemonPool.addAll(newPokemon);
    }

    private void addAllPokesInclFormes(List<Pokemon> pokemonPool, List<Pokemon> pokemonPoolInclFormes) {
        List<Pokemon> altFormes = this.getAltFormes();
        for (int i = 0; i < pokemonPool.size(); i++) {
            Pokemon currentPokemon = pokemonPool.get(i);
            if (!pokemonPoolInclFormes.contains(currentPokemon)) {
                pokemonPoolInclFormes.add(currentPokemon);
            }
            for (int j = 0; j < altFormes.size(); j++) {
                Pokemon potentialAltForme = altFormes.get(j);
                if (potentialAltForme.baseForme != null && potentialAltForme.baseForme.number == currentPokemon.number) {
                    pokemonPoolInclFormes.add(potentialAltForme);
                }
            }
        }
    }

    @Override
    public void shufflePokemonStats(Settings settings) {
        boolean evolutionSanity = settings.isBaseStatsFollowEvolutions();
        boolean megaEvolutionSanity = settings.isBaseStatsFollowMegaEvolutions();

        if (evolutionSanity) {
            copyUpEvolutionsHelper(pk -> pk.shuffleStats(AbstractRomHandler.this.random),
                    (evFrom, evTo, toMonIsFinalEvo) -> evTo.copyShuffledStatsUpEvolution(evFrom)
            );
        } else {
            List<Pokemon> allPokes = this.getPokemonInclFormes();
            for (Pokemon pk : allPokes) {
                if (pk != null) {
                    pk.shuffleStats(this.random);
                }
            }
        }

        List<Pokemon> allPokes = this.getPokemonInclFormes();
        for (Pokemon pk : allPokes) {
            if (pk != null && pk.actuallyCosmetic) {
                pk.copyBaseFormeBaseStats(pk.baseForme);
            }
        }

        if (megaEvolutionSanity) {
            List<MegaEvolution> allMegaEvos = getMegaEvolutions();
            for (MegaEvolution megaEvo: allMegaEvos) {
                if (megaEvo.from.megaEvolutionsFrom.size() > 1) continue;
                megaEvo.to.copyShuffledStatsUpEvolution(megaEvo.from);
            }
        }
    }

    @Override
    public void randomizePokemonStats(Settings settings) {
        boolean evolutionSanity = settings.isBaseStatsFollowEvolutions();
        boolean megaEvolutionSanity = settings.isBaseStatsFollowMegaEvolutions();
        boolean assignEvoStatsRandomly = settings.isAssignEvoStatsRandomly();

        if (evolutionSanity) {
            if (assignEvoStatsRandomly) {
                copyUpEvolutionsHelper(pk -> pk.randomizeStatsWithinBST(AbstractRomHandler.this.random),
                        (evFrom, evTo, toMonIsFinalEvo) -> evTo.assignNewStatsForEvolution(evFrom, this.random),
                        (evFrom, evTo, toMonIsFinalEvo) -> evTo.assignNewStatsForEvolution(evFrom, this.random),
                        true
                );
            } else {
                copyUpEvolutionsHelper(pk -> pk.randomizeStatsWithinBST(AbstractRomHandler.this.random),
                        (evFrom, evTo, toMonIsFinalEvo) -> evTo.copyRandomizedStatsUpEvolution(evFrom),
                        (evFrom, evTo, toMonIsFinalEvo) -> evTo.assignNewStatsForEvolution(evFrom, this.random),
                        true
                );
            }
        } else {
            List<Pokemon> allPokes = this.getPokemonInclFormes();
            for (Pokemon pk : allPokes) {
                if (pk != null) {
                    pk.randomizeStatsWithinBST(this.random);
                }
            }
        }

        List<Pokemon> allPokes = this.getPokemonInclFormes();
        for (Pokemon pk : allPokes) {
            if (pk != null && pk.actuallyCosmetic) {
                pk.copyBaseFormeBaseStats(pk.baseForme);
            }
        }

        if (megaEvolutionSanity) {
            List<MegaEvolution> allMegaEvos = getMegaEvolutions();
            for (MegaEvolution megaEvo: allMegaEvos) {
                if (megaEvo.from.megaEvolutionsFrom.size() > 1 || assignEvoStatsRandomly) {
                    megaEvo.to.assignNewStatsForEvolution(megaEvo.from, this.random);
                } else {
                    megaEvo.to.copyRandomizedStatsUpEvolution(megaEvo.from);
                }
            }
        }
        // cases for certain alt forms
        List<Pokemon> pokemonList = this.getPokemon();
        // copy HP stat if the pokemon can change form
        pokemonList.get(Species.venusaurMega).copyBaseHpStat(pokemonList.get(Species.venusaur));
        pokemonList.get(Species.charizardMegaX).copyBaseHpStat(pokemonList.get(Species.charizard));
        pokemonList.get(Species.charizardMegaY).copyBaseHpStat(pokemonList.get(Species.charizard));
        pokemonList.get(Species.blastoiseMega).copyBaseHpStat(pokemonList.get(Species.blastoise));
        pokemonList.get(Species.beedrillMega).copyBaseHpStat(pokemonList.get(Species.beedrill));
        pokemonList.get(Species.pidgeotMega).copyBaseHpStat(pokemonList.get(Species.pidgeot));
        pokemonList.get(Species.alakazamMega).copyBaseHpStat(pokemonList.get(Species.alakazam));
        pokemonList.get(Species.slowbroMega).copyBaseHpStat(pokemonList.get(Species.slowbro));
        pokemonList.get(Species.gengarMega).copyBaseHpStat(pokemonList.get(Species.gengar));
        pokemonList.get(Species.kangaskhanMega).copyBaseHpStat(pokemonList.get(Species.kangaskhan));
        pokemonList.get(Species.pinsirMega).copyBaseHpStat(pokemonList.get(Species.pinsir));
        pokemonList.get(Species.gyaradosMega).copyBaseHpStat(pokemonList.get(Species.gyarados));
        pokemonList.get(Species.aerodactylMega).copyBaseHpStat(pokemonList.get(Species.aerodactyl));
        pokemonList.get(Species.mewtwoMegaX).copyBaseHpStat(pokemonList.get(Species.mewtwo));
        pokemonList.get(Species.mewtwoMegaY).copyBaseHpStat(pokemonList.get(Species.mewtwo));
        pokemonList.get(Species.ampharosMega).copyBaseHpStat(pokemonList.get(Species.ampharos));
        pokemonList.get(Species.steelixMega).copyBaseHpStat(pokemonList.get(Species.steelix));
        pokemonList.get(Species.scizorMega).copyBaseHpStat(pokemonList.get(Species.scizor));
        pokemonList.get(Species.heracrossMega).copyBaseHpStat(pokemonList.get(Species.heracross));
        pokemonList.get(Species.houndoomMega).copyBaseHpStat(pokemonList.get(Species.houndoom));
        pokemonList.get(Species.tyranitarMega).copyBaseHpStat(pokemonList.get(Species.tyranitar));
        pokemonList.get(Species.sceptileMega).copyBaseHpStat(pokemonList.get(Species.sceptile));
        pokemonList.get(Species.blazikenMega).copyBaseHpStat(pokemonList.get(Species.blaziken));
        pokemonList.get(Species.swampertMega).copyBaseHpStat(pokemonList.get(Species.swampert));
        pokemonList.get(Species.gardevoirMega).copyBaseHpStat(pokemonList.get(Species.gardevoir));
        pokemonList.get(Species.sableyeMega).copyBaseHpStat(pokemonList.get(Species.sableye));
        pokemonList.get(Species.mawileMega).copyBaseHpStat(pokemonList.get(Species.mawile));
        pokemonList.get(Species.aggronMega).copyBaseHpStat(pokemonList.get(Species.aggron));
        pokemonList.get(Species.medichamMega).copyBaseHpStat(pokemonList.get(Species.medicham));
        pokemonList.get(Species.manectricMega).copyBaseHpStat(pokemonList.get(Species.manectric));
        pokemonList.get(Species.sharpedoMega).copyBaseHpStat(pokemonList.get(Species.sharpedo));
        pokemonList.get(Species.cameruptMega).copyBaseHpStat(pokemonList.get(Species.camerupt));
        pokemonList.get(Species.altariaMega).copyBaseHpStat(pokemonList.get(Species.altaria));
        pokemonList.get(Species.banetteMega).copyBaseHpStat(pokemonList.get(Species.banette));
        pokemonList.get(Species.absolMega).copyBaseHpStat(pokemonList.get(Species.absol));
        pokemonList.get(Species.glalieMega).copyBaseHpStat(pokemonList.get(Species.glalie));
        pokemonList.get(Species.salamenceMega).copyBaseHpStat(pokemonList.get(Species.salamence));
        pokemonList.get(Species.metagrossMega).copyBaseHpStat(pokemonList.get(Species.metagross));
        pokemonList.get(Species.latiasMega).copyBaseHpStat(pokemonList.get(Species.latias));
        pokemonList.get(Species.latiosMega).copyBaseHpStat(pokemonList.get(Species.latios));
        pokemonList.get(Species.rayquazaMega).copyBaseHpStat(pokemonList.get(Species.rayquaza));
        pokemonList.get(Species.lopunnyMega).copyBaseHpStat(pokemonList.get(Species.lopunny));
        pokemonList.get(Species.garchompMega).copyBaseHpStat(pokemonList.get(Species.garchomp));
        pokemonList.get(Species.lucarioMega).copyBaseHpStat(pokemonList.get(Species.lucario));
        pokemonList.get(Species.abomasnowMega).copyBaseHpStat(pokemonList.get(Species.abomasnow));
        pokemonList.get(Species.galladeMega).copyBaseHpStat(pokemonList.get(Species.gallade));
        pokemonList.get(Species.audinoMega).copyBaseHpStat(pokemonList.get(Species.audino));
        pokemonList.get(Species.diancieMega).copyBaseHpStat(pokemonList.get(Species.diancie));
        pokemonList.get(Species.kyogreP).copyBaseHpStat(pokemonList.get(Species.kyogre));
        pokemonList.get(Species.groudonP).copyBaseHpStat(pokemonList.get(Species.groudon));
        pokemonList.get(Species.clefableMega).copyBaseHpStat(pokemonList.get(Species.clefable));
        pokemonList.get(Species.victreebelMega).copyBaseHpStat(pokemonList.get(Species.victreebel));
        pokemonList.get(Species.starmieMega).copyBaseHpStat(pokemonList.get(Species.starmie));
        pokemonList.get(Species.dragoniteMega).copyBaseHpStat(pokemonList.get(Species.dragonite));
        pokemonList.get(Species.meganiumMega).copyBaseHpStat(pokemonList.get(Species.meganium));
        pokemonList.get(Species.feraligatrMega).copyBaseHpStat(pokemonList.get(Species.feraligatr));
        pokemonList.get(Species.skarmoryMega).copyBaseHpStat(pokemonList.get(Species.skarmory));
        pokemonList.get(Species.froslassMega).copyBaseHpStat(pokemonList.get(Species.froslass));
        pokemonList.get(Species.emboarMega).copyBaseHpStat(pokemonList.get(Species.emboar));
        pokemonList.get(Species.excadrillMega).copyBaseHpStat(pokemonList.get(Species.excadrill));
        pokemonList.get(Species.scolipedeMega).copyBaseHpStat(pokemonList.get(Species.scolipede));
        pokemonList.get(Species.scraftyMega).copyBaseHpStat(pokemonList.get(Species.scrafty));
        pokemonList.get(Species.eelektrossMega).copyBaseHpStat(pokemonList.get(Species.eelektross));
        pokemonList.get(Species.chandelureMega).copyBaseHpStat(pokemonList.get(Species.chandelure));
        pokemonList.get(Species.chesnaughtMega).copyBaseHpStat(pokemonList.get(Species.chesnaught));
        pokemonList.get(Species.delphoxMega).copyBaseHpStat(pokemonList.get(Species.delphox));
        pokemonList.get(Species.greninjaMega).copyBaseHpStat(pokemonList.get(Species.greninja));
        pokemonList.get(Species.pyroarMega).copyBaseHpStat(pokemonList.get(Species.pyroar));
        pokemonList.get(Species.floetteMega).copyBaseHpStat(pokemonList.get(Species.floetteE));
        pokemonList.get(Species.malamarMega).copyBaseHpStat(pokemonList.get(Species.malamar));
        pokemonList.get(Species.barbaracleMega).copyBaseHpStat(pokemonList.get(Species.barbaracle));
        pokemonList.get(Species.dragalgeMega).copyBaseHpStat(pokemonList.get(Species.dragalge));
        pokemonList.get(Species.hawluchaMega).copyBaseHpStat(pokemonList.get(Species.hawlucha));
        pokemonList.get(Species.zygardeMega).copyBaseHpStat(pokemonList.get(Species.zygardeC));
        pokemonList.get(Species.drampaMega).copyBaseHpStat(pokemonList.get(Species.drampa));
        pokemonList.get(Species.falinksMega).copyBaseHpStat(pokemonList.get(Species.falinks));
        pokemonList.get(Species.raichuMegaX).copyBaseHpStat(pokemonList.get(Species.raichu));
        pokemonList.get(Species.raichuMegaY).copyBaseHpStat(pokemonList.get(Species.raichu));
        pokemonList.get(Species.chimechoMega).copyBaseHpStat(pokemonList.get(Species.chimecho));
        pokemonList.get(Species.absolMegaZ).copyBaseHpStat(pokemonList.get(Species.absol));
        pokemonList.get(Species.staraptorMega).copyBaseHpStat(pokemonList.get(Species.staraptor));
        pokemonList.get(Species.garchompMegaZ).copyBaseHpStat(pokemonList.get(Species.garchomp));
        pokemonList.get(Species.lucarioMegaZ).copyBaseHpStat(pokemonList.get(Species.lucario));
        pokemonList.get(Species.heatranMega).copyBaseHpStat(pokemonList.get(Species.heatran));
        pokemonList.get(Species.darkraiMega).copyBaseHpStat(pokemonList.get(Species.darkrai));
        pokemonList.get(Species.golurkMega).copyBaseHpStat(pokemonList.get(Species.golurk));
        pokemonList.get(Species.meowsticMega).copyBaseHpStat(pokemonList.get(Species.meowstic));
        pokemonList.get(Species.crabominableMega).copyBaseHpStat(pokemonList.get(Species.crabominable));
        pokemonList.get(Species.golisopodMega).copyBaseHpStat(pokemonList.get(Species.golisopod));
        pokemonList.get(Species.magearnaMega).copyBaseHpStat(pokemonList.get(Species.magearna));
        pokemonList.get(Species.zeraoraMega).copyBaseHpStat(pokemonList.get(Species.zeraora));
        pokemonList.get(Species.scovillainMega).copyBaseHpStat(pokemonList.get(Species.scovillain));
        pokemonList.get(Species.glimmoraMega).copyBaseHpStat(pokemonList.get(Species.glimmora));
        pokemonList.get(Species.tatsugiriMega).copyBaseHpStat(pokemonList.get(Species.tatsugiri));
        pokemonList.get(Species.baxcaliburMega).copyBaseHpStat(pokemonList.get(Species.baxcalibur));

        pokemonList.get(Species.pikachuCap).copyBaseStats(pokemonList.get(Species.pikachu));
        pokemonList.get(Species.pichuSpiky).copyBaseStats(pokemonList.get(Species.pichu));
        pokemonList.get(Species.castformF).copyBaseStats(pokemonList.get(Species.castform));
        pokemonList.get(Species.castformW).copyBaseStats(pokemonList.get(Species.castform));
        pokemonList.get(Species.castformI).copyBaseStats(pokemonList.get(Species.castform));
        pokemonList.get(Species.deoxysA).copyBaseHpStat(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.deoxysD).copyBaseHpStat(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.deoxysS).copyBaseHpStat(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.burmyS).copyBaseStats(pokemonList.get(Species.burmy));
        pokemonList.get(Species.burmyT).copyBaseStats(pokemonList.get(Species.burmy));
        pokemonList.get(Species.cherrimS).copyBaseStats(pokemonList.get(Species.cherrim));
        pokemonList.get(Species.rotomH).copyBaseHpStat(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomW).copyBaseHpStat(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomFr).copyBaseHpStat(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomFa).copyBaseHpStat(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomM).copyBaseHpStat(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomW).copyBaseStats(pokemonList.get(Species.rotomH));
        pokemonList.get(Species.rotomFr).copyBaseStats(pokemonList.get(Species.rotomH));
        pokemonList.get(Species.rotomFa).copyBaseStats(pokemonList.get(Species.rotomH));
        pokemonList.get(Species.rotomM).copyBaseStats(pokemonList.get(Species.rotomH));
        pokemonList.get(Species.dialgaO).copyBaseHpStat(pokemonList.get(Species.dialga));
        pokemonList.get(Species.palkiaO).copyBaseHpStat(pokemonList.get(Species.palkia));
        pokemonList.get(Species.giratinaO).copyBaseHpStat(pokemonList.get(Species.giratina));
        pokemonList.get(Species.shayminS).copyBaseHpStat(pokemonList.get(Species.shaymin));
        pokemonList.get(Species.basculinB).copyBaseStats(pokemonList.get(Species.basculin));
        pokemonList.get(Species.basculinW).copyBaseStats(pokemonList.get(Species.basculin));
        pokemonList.get(Species.darmanitanZ).copyBaseHpStat(pokemonList.get(Species.darmanitan));
        pokemonList.get(Species.darmanitanZG).copyBaseHpStat(pokemonList.get(Species.darmanitanG));
        pokemonList.get(Species.tornadusT).copyBaseHpStat(pokemonList.get(Species.tornadus));
        pokemonList.get(Species.thundurusT).copyBaseHpStat(pokemonList.get(Species.thundurus));
        pokemonList.get(Species.landorusT).copyBaseHpStat(pokemonList.get(Species.landorus));
        pokemonList.get(Species.kyuremB).copyBaseHpStat(pokemonList.get(Species.kyurem));
        pokemonList.get(Species.kyuremW).copyBaseHpStat(pokemonList.get(Species.kyurem));
        pokemonList.get(Species.meloettaP).copyBaseHpStat(pokemonList.get(Species.meloetta));
        pokemonList.get(Species.greninjaB).copyBaseStats(pokemonList.get(Species.greninja));
        pokemonList.get(Species.greninjaA).copyBaseHpStat(pokemonList.get(Species.greninja));
        pokemonList.get(Species.meowsticF).copyBaseStats(pokemonList.get(Species.meowstic));
        pokemonList.get(Species.meowsticFMega).copyBaseStats(pokemonList.get(Species.meowsticMega));
        pokemonList.get(Species.aegislashB).copyBaseHpStat(pokemonList.get(Species.aegislash));
        pokemonList.get(Species.hoopaU).copyBaseHpStat(pokemonList.get(Species.hoopa));
        pokemonList.get(Species.oricorioE).copyBaseStats(pokemonList.get(Species.oricorio));
        pokemonList.get(Species.oricorioP).copyBaseStats(pokemonList.get(Species.oricorio));
        pokemonList.get(Species.oricorioG).copyBaseStats(pokemonList.get(Species.oricorio));
        pokemonList.get(Species.wishiwashiS).copyBaseHpStat(pokemonList.get(Species.wishiwashi));
        pokemonList.get(Species.miniorC).copyBaseHpStat(pokemonList.get(Species.minior));
        pokemonList.get(Species.necrozmaDM).copyBaseHpStat(pokemonList.get(Species.necrozma));
        pokemonList.get(Species.necrozmaDW).copyBaseHpStat(pokemonList.get(Species.necrozma));
        pokemonList.get(Species.necrozmaU).copyBaseHpStat(pokemonList.get(Species.necrozma));
        pokemonList.get(Species.toxtricityL).copyBaseStats(pokemonList.get(Species.toxtricity));
        pokemonList.get(Species.eiscueN).copyBaseHpStat(pokemonList.get(Species.eiscue));
        pokemonList.get(Species.morpekoH).copyBaseStats(pokemonList.get(Species.morpeko));
        pokemonList.get(Species.zacianC).copyBaseHpStat(pokemonList.get(Species.zacian));
        pokemonList.get(Species.zamazentaC).copyBaseHpStat(pokemonList.get(Species.zamazenta));
        pokemonList.get(Species.urshifuR).copyBaseStats(pokemonList.get(Species.urshifu));
        pokemonList.get(Species.enamorusT).copyBaseHpStat(pokemonList.get(Species.enamorus));
        pokemonList.get(Species.squawkabillyW).copyBaseStats(pokemonList.get(Species.squawkabilly));
        pokemonList.get(Species.palafinH).copyBaseHpStat(pokemonList.get(Species.palafin));
        pokemonList.get(Species.ogerponW).copyBaseStats(pokemonList.get(Species.ogerpon));
        pokemonList.get(Species.ogerponF).copyBaseStats(pokemonList.get(Species.ogerpon));
        pokemonList.get(Species.ogerponR).copyBaseStats(pokemonList.get(Species.ogerpon));
    }

    @Override
    public void updatePokemonStats(Settings settings) {
        int generation = settings.getUpdateBaseStatsToGeneration();

        List<Pokemon> pokes = getPokemonInclFormes();

        for (int gen = 6; gen <= generation; gen++) {
            Map<Integer,StatChange> statChanges = getUpdatedPokemonStats(gen);

            for (int i = 1; i < pokes.size(); i++) {
                StatChange changedStats = statChanges.get(i);
                if (changedStats != null) {
                    int statNum = 0;
                    if ((changedStats.stat & Stat.HP.val) != 0) {
                        pokes.get(i).hp = changedStats.values[statNum];
                        statNum++;
                    }
                    if ((changedStats.stat & Stat.ATK.val) != 0) {
                        pokes.get(i).attack = changedStats.values[statNum];
                        statNum++;
                    }
                    if ((changedStats.stat & Stat.DEF.val) != 0) {
                        pokes.get(i).defense = changedStats.values[statNum];
                        statNum++;
                    }
                    if ((changedStats.stat & Stat.SPATK.val) != 0) {
                        if (generationOfPokemon() != 1) {
                            pokes.get(i).spatk = changedStats.values[statNum];
                        }
                        statNum++;
                    }
                    if ((changedStats.stat & Stat.SPDEF.val) != 0) {
                        if (generationOfPokemon() != 1) {
                            pokes.get(i).spdef = changedStats.values[statNum];
                        }
                        statNum++;
                    }
                    if ((changedStats.stat & Stat.SPEED.val) != 0) {
                        pokes.get(i).speed = changedStats.values[statNum];
                        statNum++;
                    }
                    if ((changedStats.stat & Stat.SPECIAL.val) != 0) {
                        pokes.get(i).special = changedStats.values[statNum];
                    }
                }
            }
        }
    }

    public Pokemon randomPokemon() {
        checkPokemonRestrictions();
        return weightedRandomPokemon(mainPokemonList);
    }

    @Override
    public Pokemon randomPokemonInclFormes() {
        checkPokemonRestrictions();
        return weightedRandomPokemon(mainPokemonListInclFormes);
    }

    @Override
    public Pokemon randomNonLegendaryPokemon() {
        checkPokemonRestrictions();
        return weightedRandomPokemon(noLegendaryList);
    }

    private Pokemon randomNonLegendaryPokemonInclFormes() {
        checkPokemonRestrictions();
        return weightedRandomPokemon(noLegendaryListInclFormes);
    }

    @Override
    public Pokemon randomLegendaryPokemon() {
        checkPokemonRestrictions();
        return weightedRandomPokemon(onlyLegendaryList);
    }

    private List<Pokemon> twoEvoPokes;

    @Override
    public Pokemon random2EvosPokemon(boolean allowAltFormes) {
        if (twoEvoPokes == null) {
            // Prepare the list
            twoEvoPokes = new ArrayList<>();
            List<Pokemon> allPokes =
                    allowAltFormes ?
                            this.getPokemonInclFormes()
                                    .stream()
                                    .filter(pk -> pk == null || !pk.actuallyCosmetic)
                                    .collect(Collectors.toList()) :
                            this.getPokemon();
            for (Pokemon pk : allPokes) {
                if (pk != null && pk.evolutionsTo.size() == 0 && pk.evolutionsFrom.size() > 0) {
                    // Potential candidate
                    for (Evolution ev : pk.evolutionsFrom) {
                        // If any of the targets here evolve, the original
                        // Pokemon has 2+ stages.
                        if (ev.to.evolutionsFrom.size() > 0) {
                            twoEvoPokes.add(pk);
                            break;
                        }
                    }
                }
            }
        }
        return weightedRandomPokemon(twoEvoPokes);
    }

    @Override
    public Type randomType() {
        Type t = Type.randomType(this.random);
        while (!typeInGame(t)) {
            t = Type.randomType(this.random);
        }
        return t;
    }

    @Override
    public void randomizePokemonTypes(Settings settings) {
        boolean evolutionSanity = settings.getTypesMod() == Settings.TypesMod.RANDOM_FOLLOW_EVOLUTIONS;
        boolean megaEvolutionSanity = settings.isTypesFollowMegaEvolutions();
        boolean dualTypeOnly = settings.isDualTypeOnly();

        List<Pokemon> allPokes = this.getPokemonInclFormes();
        if (evolutionSanity) {
            // Type randomization with evolution sanity
            copyUpEvolutionsHelper(pk -> {
                // Step 1: Basic or Excluded From Copying Pokemon
                // A Basic/EFC pokemon has a 35% chance of a second type if
                // it has an evolution that copies type/stats, a 50% chance
                // otherwise
                pk.primaryType = randomType();
                pk.secondaryType = null;
                if (pk.evolutionsFrom.size() == 1 && pk.evolutionsFrom.get(0).carryStats) {
                    if (AbstractRomHandler.this.random.nextDouble() < 0.35 || dualTypeOnly) {
                        pk.secondaryType = randomType();
                        while (pk.secondaryType == pk.primaryType) {
                            pk.secondaryType = randomType();
                        }
                    }
                } else {
                    if (AbstractRomHandler.this.random.nextDouble() < 0.5 || dualTypeOnly) {
                        pk.secondaryType = randomType();
                        while (pk.secondaryType == pk.primaryType) {
                            pk.secondaryType = randomType();
                        }
                    }
                }
            }, (evFrom, evTo, toMonIsFinalEvo) -> {
                evTo.primaryType = evFrom.primaryType;
                evTo.secondaryType = evFrom.secondaryType;

                if (evTo.secondaryType == null) {
                    double chance = toMonIsFinalEvo ? 0.25 : 0.15;
                    if (AbstractRomHandler.this.random.nextDouble() < chance || dualTypeOnly) {
                        evTo.secondaryType = randomType();
                        while (evTo.secondaryType == evTo.primaryType) {
                            evTo.secondaryType = randomType();
                        }
                    }
                }
            });
        } else {
            // Entirely random types
            for (Pokemon pkmn : allPokes) {
                if (pkmn != null) {
                    pkmn.primaryType = randomType();
                    pkmn.secondaryType = null;
                    if (this.random.nextDouble() < 0.5||settings.isDualTypeOnly()) {
                        pkmn.secondaryType = randomType();
                        while (pkmn.secondaryType == pkmn.primaryType) {
                            pkmn.secondaryType = randomType();
                        }
                    }
                }
            }
        }

        for (Pokemon pk : allPokes) {
            if (pk != null && pk.actuallyCosmetic) {
                pk.primaryType = pk.baseForme.primaryType;
                pk.secondaryType = pk.baseForme.secondaryType;
            }
        }

        if (megaEvolutionSanity) {
            List<MegaEvolution> allMegaEvos = getMegaEvolutions();
            for (MegaEvolution megaEvo: allMegaEvos) {
                if (megaEvo.from.megaEvolutionsFrom.size() > 1) continue;
                megaEvo.to.primaryType = megaEvo.from.primaryType;
                megaEvo.to.secondaryType = megaEvo.from.secondaryType;

                if (megaEvo.to.secondaryType == null) {
                    if (this.random.nextDouble() < 0.25) {
                        megaEvo.to.secondaryType = randomType();
                        while (megaEvo.to.secondaryType == megaEvo.to.primaryType) {
                            megaEvo.to.secondaryType = randomType();
                        }
                    }
                }
            }
        }
        // cases for certain alt forms
        List<Pokemon> pokemonList = this.getPokemon();
        pokemonList.get(Species.pikachuCap).copyTypes(pokemonList.get(Species.pikachu));
        pokemonList.get(Species.pichuSpiky).copyTypes(pokemonList.get(Species.pichu));
        pokemonList.get(Species.deoxysA).copyTypes(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.deoxysD).copyTypes(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.deoxysS).copyTypes(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.burmyS).copyTypes(pokemonList.get(Species.burmy));
        pokemonList.get(Species.burmyT).copyTypes(pokemonList.get(Species.burmy));
        pokemonList.get(Species.cherrimS).copyTypes(pokemonList.get(Species.cherrim));
//        pokemonList.get(Species.dialgaO).copyTypes(pokemonList.get(Species.dialga));
//        pokemonList.get(Species.palkiaO).copyTypes(pokemonList.get(Species.palkia));
//        pokemonList.get(Species.giratinaO).copyTypes(pokemonList.get(Species.giratina));
//        pokemonList.get(Species.basculinB).copyTypes(pokemonList.get(Species.basculin));
//        pokemonList.get(Species.basculinW).copyTypes(pokemonList.get(Species.basculin));
        pokemonList.get(Species.tornadusT).copyTypes(pokemonList.get(Species.tornadus));
        pokemonList.get(Species.thundurusT).copyTypes(pokemonList.get(Species.thundurus));
        pokemonList.get(Species.landorusT).copyTypes(pokemonList.get(Species.landorus));
//        pokemonList.get(Species.kyuremB).copyTypes(pokemonList.get(Species.kyurem));
//        pokemonList.get(Species.kyuremW).copyTypes(pokemonList.get(Species.kyurem));
        pokemonList.get(Species.greninjaB).copyTypes(pokemonList.get(Species.greninja));
//        pokemonList.get(Species.greninjaA).copyTypes(pokemonList.get(Species.greninja));
//        pokemonList.get(Species.floetteE).copyTypes(pokemonList.get(Species.floette));
        pokemonList.get(Species.meowsticF).copyTypes(pokemonList.get(Species.meowstic));
        pokemonList.get(Species.meowsticFMega).copyTypes(pokemonList.get(Species.meowsticMega));
        pokemonList.get(Species.aegislashB).copyTypes(pokemonList.get(Species.aegislash));
        pokemonList.get(Species.pumpkabooSmall).copyTypes(pokemonList.get(Species.pumpkaboo));
        pokemonList.get(Species.pumpkabooLarge).copyTypes(pokemonList.get(Species.pumpkaboo));
        pokemonList.get(Species.pumpkabooSuper).copyTypes(pokemonList.get(Species.pumpkaboo));
        pokemonList.get(Species.gourgeistSmall).copyTypes(pokemonList.get(Species.gourgeist));
        pokemonList.get(Species.gourgeistLarge).copyTypes(pokemonList.get(Species.gourgeist));
        pokemonList.get(Species.gourgeistSuper).copyTypes(pokemonList.get(Species.gourgeist));
//        pokemonList.get(Species.zygarde10).copyTypes(pokemonList.get(Species.zygarde));
//        pokemonList.get(Species.zygardeC).copyTypes(pokemonList.get(Species.zygarde));
//        pokemonList.get(Species.lycanrocM).copyTypes(pokemonList.get(Species.lycanroc));
//        pokemonList.get(Species.lycanrocD).copyTypes(pokemonList.get(Species.lycanroc));
        pokemonList.get(Species.wishiwashiS).copyTypes(pokemonList.get(Species.wishiwashi));
        pokemonList.get(Species.miniorC).copyTypes(pokemonList.get(Species.minior));
        pokemonList.get(Species.toxtricityL).copyTypes(pokemonList.get(Species.toxtricity));
        pokemonList.get(Species.eiscueN).copyTypes(pokemonList.get(Species.eiscue));
        pokemonList.get(Species.indeedeeF).copyTypes(pokemonList.get(Species.indeedee));
        pokemonList.get(Species.morpekoH).copyTypes(pokemonList.get(Species.morpeko));
        pokemonList.get(Species.eternatusE).copyTypes(pokemonList.get(Species.eternatus));
        pokemonList.get(Species.basculegionF).copyTypes(pokemonList.get(Species.basculegion));
        pokemonList.get(Species.enamorusT).copyTypes(pokemonList.get(Species.enamorus));
        pokemonList.get(Species.oinkologneF).copyTypes(pokemonList.get(Species.oinkologne));
        pokemonList.get(Species.squawkabillyW).copyTypes(pokemonList.get(Species.squawkabilly));
        pokemonList.get(Species.palafinH).copyTypes(pokemonList.get(Species.palafin));
        pokemonList.get(Species.gimmighoulR).copyTypes(pokemonList.get(Species.gimmighoul));
        pokemonList.get(Species.terapagosT).copyTypes(pokemonList.get(Species.terapagos));
        pokemonList.get(Species.terapagosS).copyTypes(pokemonList.get(Species.terapagos));
    }

    @Override
    public void randomizeAbilities(Settings settings) {
        boolean evolutionSanity = settings.isAbilitiesFollowEvolutions();
        boolean allowWonderGuard = settings.isAllowWonderGuard();
        boolean banTrappingAbilities = settings.isBanTrappingAbilities();
        boolean banNegativeAbilities = settings.isBanNegativeAbilities();
        boolean banBadAbilities = settings.isBanBadAbilities();
        boolean megaEvolutionSanity = settings.isAbilitiesFollowMegaEvolutions();
        boolean weighDuplicatesTogether = settings.isWeighDuplicateAbilitiesTogether();
        boolean ensureTwoAbilities = settings.isEnsureTwoAbilities();
        boolean doubleBattleMode = settings.isDoubleBattleMode();

        // Abilities don't exist in some games...
        if (this.abilitiesPerPokemon() == 0) {
            return;
        }

        final boolean hasDWAbilities = (this.abilitiesPerPokemon() == 3);

        final List<Integer> bannedAbilities = this.getUselessAbilities();

        if (!allowWonderGuard) {
            bannedAbilities.add(Abilities.wonderGuard);
        }

        if (banTrappingAbilities) {
            bannedAbilities.addAll(GlobalConstants.battleTrappingAbilities);
        }

        if (banNegativeAbilities) {
            bannedAbilities.addAll(GlobalConstants.negativeAbilities);
        }

        if (banBadAbilities) {
            bannedAbilities.addAll(GlobalConstants.badAbilities);
            if (!doubleBattleMode) {
                bannedAbilities.addAll(GlobalConstants.doubleBattleAbilities);
            }
        }

        if (weighDuplicatesTogether) {
            bannedAbilities.addAll(GlobalConstants.duplicateAbilities);
            if (generationOfPokemon() == 3) {
                bannedAbilities.add(Gen3Constants.airLockIndex); // Special case for Air Lock in Gen 3
            }
        }

        final int maxAbility = this.highestAbilityIndex();

        if (evolutionSanity) {
            // copy abilities straight up evolution lines
            // still keep WG as an exception, though

            copyUpEvolutionsHelper(pk -> {
                if (pk.ability1 != Abilities.wonderGuard
                        && pk.ability2 != Abilities.wonderGuard
                        && pk.ability3 != Abilities.wonderGuard) {
                    // Pick first ability
                    pk.ability1 = pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether);

                    // Second ability?
                    if (ensureTwoAbilities || AbstractRomHandler.this.random.nextDouble() < 0.5) {
                        // Yes, second ability
                        pk.ability2 = pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether,
                                pk.ability1);
                    } else {
                        // Nope
                        pk.ability2 = 0;
                    }

                    // Third ability?
                    if (hasDWAbilities) {
                        pk.ability3 = pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether,
                                pk.ability1, pk.ability2);
                    }
                }
            }, (evFrom, evTo, toMonIsFinalEvo) -> {
                if (evTo.ability1 != Abilities.wonderGuard
                        && evTo.ability2 != Abilities.wonderGuard
                        && evTo.ability3 != Abilities.wonderGuard) {
                    evTo.ability1 = evFrom.ability1;
                    evTo.ability2 = evFrom.ability2;
                    evTo.ability3 = evFrom.ability3;
                }
            });
        } else {
            List<Pokemon> allPokes = this.getPokemonInclFormes();
            for (Pokemon pk : allPokes) {
                if (pk == null) {
                    continue;
                }

                // Don't remove WG if already in place.
                if (pk.ability1 != Abilities.wonderGuard
                        && pk.ability2 != Abilities.wonderGuard
                        && pk.ability3 != Abilities.wonderGuard) {
                    // Pick first ability
                    pk.ability1 = this.pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether);

                    // Second ability?
                    if (ensureTwoAbilities || this.random.nextDouble() < 0.5) {
                        // Yes, second ability
                        pk.ability2 = this.pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether,
                                pk.ability1);
                    } else {
                        // Nope
                        pk.ability2 = 0;
                    }

                    // Third ability?
                    if (hasDWAbilities) {
                        pk.ability3 = pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether,
                                pk.ability1, pk.ability2);
                    }
                }
            }
        }

        List<Pokemon> allPokes = this.getPokemonInclFormes();
        for (Pokemon pk : allPokes) {
            if (pk != null && pk.actuallyCosmetic) {
                pk.copyBaseFormeAbilities(pk.baseForme);
            }
        }

        if (megaEvolutionSanity) {
            List<MegaEvolution> allMegaEvos = this.getMegaEvolutions();
            for (MegaEvolution megaEvo: allMegaEvos) {
                if (megaEvo.from.megaEvolutionsFrom.size() > 1) continue;
                megaEvo.to.ability1 = megaEvo.from.ability1;
                megaEvo.to.ability2 = megaEvo.from.ability2;
                megaEvo.to.ability3 = megaEvo.from.ability3;
            }
        }
        // cases for certain alt forms
        List<Pokemon> pokemonList = this.getPokemon();
        pokemonList.get(Species.pikachuCap).copyAbilities(pokemonList.get(Species.pikachu));
        pokemonList.get(Species.pichuSpiky).copyAbilities(pokemonList.get(Species.pichu));
        pokemonList.get(Species.castformF).copyAbilities(pokemonList.get(Species.castform));
        pokemonList.get(Species.castformW).copyAbilities(pokemonList.get(Species.castform));
        pokemonList.get(Species.castformI).copyAbilities(pokemonList.get(Species.castform));
        pokemonList.get(Species.deoxysA).copyAbilities(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.deoxysD).copyAbilities(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.deoxysS).copyAbilities(pokemonList.get(Species.deoxys));
        pokemonList.get(Species.burmyS).copyAbilities(pokemonList.get(Species.burmy));
        pokemonList.get(Species.burmyT).copyAbilities(pokemonList.get(Species.burmy));
        pokemonList.get(Species.wormadamS).copyAbilities(pokemonList.get(Species.wormadam));
        pokemonList.get(Species.wormadamT).copyAbilities(pokemonList.get(Species.wormadam));
        pokemonList.get(Species.cherrimS).copyAbilities(pokemonList.get(Species.cherrim));
        pokemonList.get(Species.rotomH).copyAbilities(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomW).copyAbilities(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomFr).copyAbilities(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomFa).copyAbilities(pokemonList.get(Species.rotom));
        pokemonList.get(Species.rotomM).copyAbilities(pokemonList.get(Species.rotom));
        pokemonList.get(Species.dialgaO).copyAbilities(pokemonList.get(Species.dialga));
        pokemonList.get(Species.palkiaO).copyAbilities(pokemonList.get(Species.palkia));
        pokemonList.get(Species.darmanitanZ).copyAbilities(pokemonList.get(Species.darmanitan));
        pokemonList.get(Species.darmanitanZG).copyAbilities(pokemonList.get(Species.darmanitanG));
        pokemonList.get(Species.meloettaP).copyAbilities(pokemonList.get(Species.meloetta));
        pokemonList.get(Species.greninjaA).copyAbilities(pokemonList.get(Species.greninjaB));
        pokemonList.get(Species.floetteE).copyAbilities(pokemonList.get(Species.floette));
        pokemonList.get(Species.meowsticFMega).copyAbilities(pokemonList.get(Species.meowsticMega));
        pokemonList.get(Species.aegislashB).copyAbilities(pokemonList.get(Species.aegislash));
        pokemonList.get(Species.pumpkabooSmall).copyAbilities(pokemonList.get(Species.pumpkaboo));
        pokemonList.get(Species.pumpkabooLarge).copyAbilities(pokemonList.get(Species.pumpkaboo));
        pokemonList.get(Species.pumpkabooSuper).copyAbilities(pokemonList.get(Species.pumpkaboo));
        pokemonList.get(Species.gourgeistSmall).copyAbilities(pokemonList.get(Species.gourgeist));
        pokemonList.get(Species.gourgeistLarge).copyAbilities(pokemonList.get(Species.gourgeist));
        pokemonList.get(Species.gourgeistSuper).copyAbilities(pokemonList.get(Species.gourgeist));
        pokemonList.get(Species.zygarde10).copyAbilities(pokemonList.get(Species.zygarde));
        pokemonList.get(Species.hoopaU).copyAbilities(pokemonList.get(Species.hoopa));
        pokemonList.get(Species.oricorioE).copyAbilities(pokemonList.get(Species.oricorio));
        pokemonList.get(Species.oricorioP).copyAbilities(pokemonList.get(Species.oricorio));
        pokemonList.get(Species.oricorioG).copyAbilities(pokemonList.get(Species.oricorio));
        pokemonList.get(Species.wishiwashiS).copyAbilities(pokemonList.get(Species.wishiwashi));
        pokemonList.get(Species.miniorC).copyAbilities(pokemonList.get(Species.minior));
        pokemonList.get(Species.necrozmaDM).copyAbilities(pokemonList.get(Species.necrozma));
        pokemonList.get(Species.necrozmaDW).copyAbilities(pokemonList.get(Species.necrozma));
        pokemonList.get(Species.eiscueN).copyAbilities(pokemonList.get(Species.eiscue));
        pokemonList.get(Species.morpekoH).copyAbilities(pokemonList.get(Species.morpeko));
        pokemonList.get(Species.zacianC).copyAbilities(pokemonList.get(Species.zacian));
        pokemonList.get(Species.zamazentaC).copyAbilities(pokemonList.get(Species.zamazenta));
        pokemonList.get(Species.eternatusE).copyAbilities(pokemonList.get(Species.eternatus));
        pokemonList.get(Species.basculegionF).copyAbilities(pokemonList.get(Species.basculegion));
        pokemonList.get(Species.urshifuR).copyAbilities(pokemonList.get(Species.urshifu));
        pokemonList.get(Species.palafinH).copyAbilities(pokemonList.get(Species.palafin));
    }

    private int pickRandomAbility(int maxAbility, List<Integer> bannedAbilities, boolean useVariations,
                                  int... alreadySetAbilities) {
        int newAbility;

        while (true) {
            newAbility = this.random.nextInt(maxAbility) + 1;

            if (bannedAbilities.contains(newAbility)) {
                continue;
            }

            boolean repeat = false;
            for (int alreadySetAbility : alreadySetAbilities) {
                if (alreadySetAbility == newAbility) {
                    repeat = true;
                    break;
                }
            }

            if (!repeat) {
                if (useVariations) {
                    Map<Integer,List<Integer>> abilityVariations = getAbilityVariations();
                    for (int baseAbility: abilityVariations.keySet()) {
                        if (newAbility == baseAbility) {
                            List<Integer> variationsForThisAbility = abilityVariations.get(newAbility);
                            newAbility = variationsForThisAbility.get(this.random.nextInt(variationsForThisAbility.size()));
                            break;
                        }
                    }
                }
                break;
            }
        }

        return newAbility;
    }

    private List<Pokemon> getWildPool(boolean allowAltFormes, boolean noLegendaries, int wildPokemonBSTLimit) {
        List<Pokemon> pool;
        if (allowAltFormes) {
            pool = noLegendaries ? new ArrayList<>(noLegendaryListInclFormes) : new ArrayList<>(mainPokemonListInclFormes);
        } else {
            pool = noLegendaries ? new ArrayList<>(noLegendaryList) : new ArrayList<>(mainPokemonList);
        }
        if (wildPokemonBSTLimit > 0) {
            pool.removeIf(pk -> getPreRandomizationBstForPowerLevels(pk) > wildPokemonBSTLimit);
        }
        return pool;
    }

    @Override
    public void randomEncounters(Settings settings) {
        boolean useTimeOfDay = settings.isUseTimeBasedEncounters();
        boolean catchEmAll = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL;
        boolean typeThemed = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS;
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        int wildPokemonBSTLimit = settings.getWildPokemonBSTLimit();
        boolean balanceShakingGrass = settings.isBalanceShakingGrass();
        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowWildAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        List<EncounterSet> currentEncounters = this.getEncounters(useTimeOfDay);

        if (isORAS) {
            List<EncounterSet> collapsedEncounters = collapseAreasORAS(currentEncounters);
            area1to1EncountersImpl(collapsedEncounters, settings);
            enhanceRandomEncountersORAS(collapsedEncounters, settings);
            setEncounters(useTimeOfDay, currentEncounters);
            return;
        }

        checkPokemonRestrictions();

        // New: randomize the order encounter sets are randomized in.
        // Leads to less predictable results for various modifiers.
        // Need to keep the original ordering around for saving though.
        List<EncounterSet> scrambledEncounters = new ArrayList<>(currentEncounters);
        Collections.shuffle(scrambledEncounters, this.random);

        List<Pokemon> banned = this.bannedForWildEncounters(settings);
        banned.addAll(this.getBannedFormesForPlayerPokemon());
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        // Assume EITHER catch em all OR type themed OR match strength for now
        if (catchEmAll) {
            List<Pokemon> allPokes;
            if (allowAltFormes) {
                allPokes = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
            } else {
                allPokes = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
            }
            allPokes.removeAll(banned);

            for (EncounterSet area : scrambledEncounters) {
                List<Pokemon> pickablePokemon = allPokes;
                if (area.bannedPokemon.size() > 0) {
                    pickablePokemon = new ArrayList<>(allPokes);
                    pickablePokemon.removeAll(area.bannedPokemon);
                }
                for (Encounter enc : area.encounters) {
                    // In Catch 'Em All mode, don't randomize encounters for Pokemon that are banned for
                    // wild encounters. Otherwise, it may be impossible to obtain this Pokemon unless it
                    // randomly appears as a static or unless it becomes a random evolution.
                    if (banned.contains(enc.pokemon)) {
                        continue;
                    }

                    // Pick a random pokemon
                    if (pickablePokemon.size() == 0) {
                        // Only banned pokes are left, ignore them and pick
                        // something else for now.
                        List<Pokemon> tempPickable;
                        if (allowAltFormes) {
                            tempPickable = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                            tempPickable.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                        } else {
                            tempPickable = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
                        }
                        tempPickable.removeAll(banned);
                        tempPickable.removeAll(area.bannedPokemon);
                        if (tempPickable.size() == 0) {
                            throw new RandomizationException("ERROR: Couldn't replace a wild Pokemon!");
                        }
                        int picked = this.random.nextInt(tempPickable.size());
                        enc.pokemon = tempPickable.get(picked);
                        setFormeForEncounter(enc, enc.pokemon);
                    } else {
                        // Picked this Pokemon, remove it
                        int picked = this.random.nextInt(pickablePokemon.size());
                        enc.pokemon = pickablePokemon.get(picked);
                        pickablePokemon.remove(picked);
                        if (allPokes != pickablePokemon) {
                            allPokes.remove(enc.pokemon);
                        }
                        setFormeForEncounter(enc, enc.pokemon);
                        if (allPokes.size() == 0) {
                            // Start again
                            if (allowAltFormes) {
                                allPokes.addAll(getWildPool(true, noLegendaries, wildPokemonBSTLimit));
                                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                            } else {
                                allPokes.addAll(getWildPool(false, noLegendaries, wildPokemonBSTLimit));
                            }
                            allPokes.removeAll(banned);
                            if (pickablePokemon != allPokes) {
                                pickablePokemon.addAll(allPokes);
                                pickablePokemon.removeAll(area.bannedPokemon);
                            }
                        }
                    }
                }
            }
        } else if (typeThemed) {
            Map<Type, List<Pokemon>> cachedPokeLists = new TreeMap<>();
            for (EncounterSet area : scrambledEncounters) {
                List<Pokemon> possiblePokemon = null;
                int iterLoops = 0;
                while (possiblePokemon == null && iterLoops < 10000) {
                    Type areaTheme = randomType();
                    if (!cachedPokeLists.containsKey(areaTheme)) {
                        List<Pokemon> pType = allowAltFormes ? pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                pokemonOfType(areaTheme, noLegendaries);
                        pType.removeAll(banned);
                        cachedPokeLists.put(areaTheme, pType);
                    }
                    possiblePokemon = cachedPokeLists.get(areaTheme);
                    if (area.bannedPokemon.size() > 0) {
                        possiblePokemon = new ArrayList<>(possiblePokemon);
                        possiblePokemon.removeAll(area.bannedPokemon);
                    }
                    if (possiblePokemon.size() == 0) {
                        // Can't use this type for this area
                        possiblePokemon = null;
                    }
                    iterLoops++;
                }
                if (possiblePokemon == null) {
                    throw new RandomizationException("Could not randomize an area in a reasonable amount of attempts.");
                }
                for (Encounter enc : area.encounters) {
                    // Pick a random themed pokemon
                    enc.pokemon = weightedRandomPokemon(possiblePokemon);
                    while (enc.pokemon.actuallyCosmetic) {
                        enc.pokemon = weightedRandomPokemon(possiblePokemon);
                    }
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        } else if (usePowerLevels) {
            List<Pokemon> allowedPokes;
            if (allowAltFormes) {
                allowedPokes = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
            } else {
                allowedPokes = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
            }
            allowedPokes.removeAll(banned);
            for (EncounterSet area : scrambledEncounters) {
                List<Pokemon> localAllowed = allowedPokes;
                if (area.bannedPokemon.size() > 0) {
                    localAllowed = new ArrayList<>(allowedPokes);
                    localAllowed.removeAll(area.bannedPokemon);
                }
                for (Encounter enc : area.encounters) {
                    if (balanceShakingGrass) {
                        if (area.displayName.contains("Shaking")) {
                            enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, (enc.level + enc.maxLevel) / 2);
                            while (enc.pokemon.actuallyCosmetic) {
                                enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, (enc.level + enc.maxLevel) / 2);
                            }
                            setFormeForEncounter(enc, enc.pokemon);
                        } else {
                            enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                            while (enc.pokemon.actuallyCosmetic) {
                                enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                            }
                            setFormeForEncounter(enc, enc.pokemon);
                        }
                    } else {
                        enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                        while (enc.pokemon.actuallyCosmetic) {
                            enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                        }
                        setFormeForEncounter(enc, enc.pokemon);
                    }
                }
            }
        } else {
            // Entirely random
            for (EncounterSet area : scrambledEncounters) {
                for (Encounter enc : area.encounters) {
                    enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, banned);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        }
        if (levelModifier != 0) {
            for (EncounterSet area : currentEncounters) {
                for (Encounter enc : area.encounters) {
                    enc.level = Math.min(100, (int) Math.round(enc.level * (1 + levelModifier / 100.0)));
                    enc.maxLevel = Math.min(100, (int) Math.round(enc.maxLevel * (1 + levelModifier / 100.0)));
                }
            }
        }

        setEncounters(useTimeOfDay, currentEncounters);
    }

    @Override
    public void area1to1Encounters(Settings settings) {
        boolean useTimeOfDay = settings.isUseTimeBasedEncounters();

        List<EncounterSet> currentEncounters = this.getEncounters(useTimeOfDay);
        if (isORAS) {
            List<EncounterSet> collapsedEncounters = collapseAreasORAS(currentEncounters);
            area1to1EncountersImpl(collapsedEncounters, settings);
            setEncounters(useTimeOfDay, currentEncounters);
            return;
        } else {
            area1to1EncountersImpl(currentEncounters, settings);
            setEncounters(useTimeOfDay, currentEncounters);
        }
    }

    private void area1to1EncountersImpl(List<EncounterSet> currentEncounters, Settings settings) {
        boolean catchEmAll = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL;
        boolean typeThemed = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS;
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        int wildPokemonBSTLimit = settings.getWildPokemonBSTLimit();
        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowWildAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        checkPokemonRestrictions();
        List<Pokemon> banned = this.bannedForWildEncounters(settings);
        banned.addAll(this.getBannedFormesForPlayerPokemon());
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }

        // New: randomize the order encounter sets are randomized in.
        // Leads to less predictable results for various modifiers.
        // Need to keep the original ordering around for saving though.
        List<EncounterSet> scrambledEncounters = new ArrayList<>(currentEncounters);
        Collections.shuffle(scrambledEncounters, this.random);

        // Assume EITHER catch em all OR type themed for now
        if (catchEmAll) {
            List<Pokemon> allPokes;
            if (allowAltFormes) {
                allPokes = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
            } else {
                allPokes = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
            }
            allPokes.removeAll(banned);
            for (EncounterSet area : scrambledEncounters) {
                Set<Pokemon> inArea = pokemonInArea(area);
                // Build area map using catch em all
                Map<Pokemon, Pokemon> areaMap = new TreeMap<>();
                List<Pokemon> pickablePokemon = allPokes;
                if (area.bannedPokemon.size() > 0) {
                    pickablePokemon = new ArrayList<>(allPokes);
                    pickablePokemon.removeAll(area.bannedPokemon);
                }
                for (Pokemon areaPk : inArea) {
                    if (pickablePokemon.size() == 0) {
                        // No more pickable pokes left, take a random one
                        List<Pokemon> tempPickable;
                        if (allowAltFormes) {
                            tempPickable = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                            tempPickable.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                        } else {
                            tempPickable = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
                        }
                        tempPickable.removeAll(banned);
                        tempPickable.removeAll(area.bannedPokemon);
                        if (tempPickable.size() == 0) {
                            throw new RandomizationException("ERROR: Couldn't replace a wild Pokemon!");
                        }
                        int picked = this.random.nextInt(tempPickable.size());
                        Pokemon pickedMN = tempPickable.get(picked);
                        areaMap.put(areaPk, pickedMN);
                    } else {
                        int picked = this.random.nextInt(allPokes.size());
                        Pokemon pickedMN = allPokes.get(picked);
                        areaMap.put(areaPk, pickedMN);
                        pickablePokemon.remove(pickedMN);
                        if (allPokes != pickablePokemon) {
                            allPokes.remove(pickedMN);
                        }
                        if (allPokes.size() == 0) {
                            // Start again
                            if (allowAltFormes) {
                                allPokes.addAll(getWildPool(true, noLegendaries, wildPokemonBSTLimit));
                                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                            } else {
                                allPokes.addAll(getWildPool(false, noLegendaries, wildPokemonBSTLimit));
                            }
                            allPokes.removeAll(banned);
                            if (pickablePokemon != allPokes) {
                                pickablePokemon.addAll(allPokes);
                                pickablePokemon.removeAll(area.bannedPokemon);
                            }
                        }
                    }
                }
                Map<Pokemon, Integer> maxLevel = new TreeMap<>();
                Map<Pokemon, List<Integer>> levelsAvailable = new TreeMap<>();
                List<Pokemon> pokemonAvailable = new ArrayList<>();
                for (Encounter enc : area.encounters) {
                    if(maxLevel.containsKey(enc.pokemon)){
                        if(maxLevel.get(enc.pokemon) < Math.max(enc.level, enc.maxLevel)){
                            maxLevel.put(enc.pokemon, Math.max(enc.level, enc.maxLevel));
                        }
                    }
                    else{
                        maxLevel.put(enc.pokemon, Math.max(enc.level, enc.maxLevel));
                    }
                    if(enc.maxLevel == 0 || enc.maxLevel == enc.level) {
                        if (levelsAvailable.containsKey(enc.pokemon)) {
                            if (!levelsAvailable.get(enc.pokemon).contains(enc.level)) {
                                levelsAvailable.get(enc.pokemon).add(enc.level);
                            }
                        } else {
                            List<Integer> l = new ArrayList<>();
                            l.add(enc.level);
                            levelsAvailable.put(enc.pokemon, l);
                        }

                        if(!pokemonAvailable.contains(enc.pokemon)){
                            pokemonAvailable.add(enc.pokemon);
                        }
                    }
                }
                for(Pokemon p : levelsAvailable.keySet()){
                    levelsAvailable.get(p).sort(Collections.reverseOrder());
                }
                for (Encounter enc : area.encounters) {
                    if((settings.getCurrentMiscTweaks() & MiscTweak.ALLOW_PIKACHU_EVOLUTION.getValue()) > 0){
                        if(pokemonAvailable.size() > 0) {
                            enc.pokemon = pokemonAvailable.remove(0);
                            pokemonAvailable.add(enc.pokemon);
                        }

                        enc.level = maxLevel.get(enc.pokemon);
                        enc.maxLevel = maxLevel.get(enc.pokemon);
                    }
                    enc.pokemon = areaMap.get(enc.pokemon);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        } else if (typeThemed) {
            Map<Type, List<Pokemon>> cachedPokeLists = new TreeMap<>();
            for (EncounterSet area : scrambledEncounters) {
                // Poke-set
                Set<Pokemon> inArea = pokemonInArea(area);
                List<Pokemon> possiblePokemon = null;
                int iterLoops = 0;
                while (possiblePokemon == null && iterLoops < 10000) {
                    Type areaTheme = randomType();
                    if (!cachedPokeLists.containsKey(areaTheme)) {
                        List<Pokemon> pType = allowAltFormes ? pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                pokemonOfType(areaTheme, noLegendaries);
                        pType.removeAll(banned);
                        cachedPokeLists.put(areaTheme, pType);
                    }
                    possiblePokemon = new ArrayList<>(cachedPokeLists.get(areaTheme));
                    if (area.bannedPokemon.size() > 0) {
                        possiblePokemon.removeAll(area.bannedPokemon);
                    }
                    if (possiblePokemon.size() < inArea.size()) {
                        // Can't use this type for this area
                        possiblePokemon = null;
                    }
                    iterLoops++;
                }
                if (possiblePokemon == null) {
                    throw new RandomizationException("Could not randomize an area in a reasonable amount of attempts.");
                }

                // Build area map using type theme.
                Map<Pokemon, Pokemon> areaMap = new TreeMap<>();
                for (Pokemon areaPk : inArea) {
                    int picked = this.random.nextInt(possiblePokemon.size());
                    Pokemon pickedMN = possiblePokemon.get(picked);
                    while (pickedMN.actuallyCosmetic) {
                        picked = this.random.nextInt(possiblePokemon.size());
                        pickedMN = possiblePokemon.get(picked);
                    }
                    areaMap.put(areaPk, pickedMN);
                    possiblePokemon.remove(picked);
                }
                for (Encounter enc : area.encounters) {
                    // Apply the map
                    enc.pokemon = areaMap.get(enc.pokemon);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        } else if (usePowerLevels) {
            List<Pokemon> allowedPokes;
            if (allowAltFormes) {
                allowedPokes = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
            } else {
                allowedPokes = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
            }
            allowedPokes.removeAll(banned);
            for (EncounterSet area : scrambledEncounters) {
                // Poke-set
                Set<Pokemon> inArea = pokemonInArea(area);
                // Build area map using randoms
                Map<Pokemon, Pokemon> areaMap = new TreeMap<>();
                List<Pokemon> usedPks = new ArrayList<>();
                List<Pokemon> localAllowed = allowedPokes;
                if (area.bannedPokemon.size() > 0) {
                    localAllowed = new ArrayList<>(allowedPokes);
                    localAllowed.removeAll(area.bannedPokemon);
                }
                for (Pokemon areaPk : inArea) {
                    Pokemon picked = pickWildPowerLvlReplacement(localAllowed, areaPk, false, usedPks, 100);
                    while (picked.actuallyCosmetic) {
                        picked = pickWildPowerLvlReplacement(localAllowed, areaPk, false, usedPks, 100);
                    }
                    areaMap.put(areaPk, picked);
                    usedPks.add(picked);
                }
                for (Encounter enc : area.encounters) {
                    // Apply the map
                    enc.pokemon = areaMap.get(enc.pokemon);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        } else {
            // Entirely random
            for (EncounterSet area : scrambledEncounters) {
                int minLevelInArea = 100;
                for(Encounter e : area.encounters){
                    if(e.level < minLevelInArea){
                        minLevelInArea = e.level;
                    }
                }
                // Poke-set
                Set<Pokemon> inArea = pokemonInArea(area);
                // Build area map using randoms
                Map<Pokemon, Pokemon> areaMap = new TreeMap<>();
                List<Pokemon> tempBanned = banned;
                for (Pokemon areaPk : inArea) {
                    Pokemon picked = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, tempBanned);
                    while (areaMap.containsValue(picked)) {
                        picked = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, tempBanned);
                    }
                    areaMap.put(areaPk, picked);
                }
                Map<Pokemon, Integer> maxLevel = new TreeMap<>();
                Map<Pokemon, List<Integer>> levelsAvailable = new TreeMap<>();
                List<Pokemon> pokemonAvailable = new ArrayList<>();
                for (Encounter enc : area.encounters) {
                    if(maxLevel.containsKey(enc.pokemon)){
                        if(maxLevel.get(enc.pokemon) < Math.max(enc.level, enc.maxLevel)){
                            maxLevel.put(enc.pokemon, Math.max(enc.level, enc.maxLevel));
                        }
                    }
                    else{
                        maxLevel.put(enc.pokemon, Math.max(enc.level, enc.maxLevel));
                    }
                    if(enc.maxLevel == 0 || enc.maxLevel == enc.level) {
                        if (levelsAvailable.containsKey(enc.pokemon)) {
                            if (!levelsAvailable.get(enc.pokemon).contains(enc.level)) {
                                levelsAvailable.get(enc.pokemon).add(enc.level);
                            }
                        } else {
                            List<Integer> l = new ArrayList<>();
                            l.add(enc.level);
                            levelsAvailable.put(enc.pokemon, l);
                        }

                        if(!pokemonAvailable.contains(enc.pokemon)){
                            pokemonAvailable.add(enc.pokemon);
                        }
                    }
                }
                for(Pokemon p : levelsAvailable.keySet()){
                    levelsAvailable.get(p).sort(Collections.reverseOrder());
                }
                for (Encounter enc : area.encounters) {
                    if((settings.getCurrentMiscTweaks() & MiscTweak.ALLOW_PIKACHU_EVOLUTION.getValue()) > 0){
                        if(pokemonAvailable.size() > 0) {
                            enc.pokemon = pokemonAvailable.remove(0);
                            pokemonAvailable.add(enc.pokemon);
                        }

                        enc.level = maxLevel.get(enc.pokemon);
                        enc.maxLevel = maxLevel.get(enc.pokemon);
                    }
                    enc.pokemon = areaMap.get(enc.pokemon);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        }

        if (levelModifier != 0) {
            for (EncounterSet area : currentEncounters) {
                for (Encounter enc : area.encounters) {
                    enc.level = Math.min(100, (int) Math.round(enc.level * (1 + levelModifier / 100.0)));
                    enc.maxLevel = Math.min(100, (int) Math.round(enc.maxLevel * (1 + levelModifier / 100.0)));
                }
            }
        }
    }

    @Override
    public void game1to1Encounters(Settings settings) {
        boolean useTimeOfDay = settings.isUseTimeBasedEncounters();
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        int wildPokemonBSTLimit = settings.getWildPokemonBSTLimit();
        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowWildAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        checkPokemonRestrictions();
        // Build the full 1-to-1 map
        Map<Pokemon, Pokemon> translateMap = new TreeMap<>();
        List<Pokemon> remainingLeft = allPokemonInclFormesWithoutNull();
        remainingLeft.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
        List<Pokemon> remainingRight;
        if (allowAltFormes) {
            remainingRight = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
            remainingRight.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
        } else {
            remainingRight = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
        }
        List<Pokemon> banned = this.bannedForWildEncounters(settings);
        banned.addAll(this.getBannedFormesForPlayerPokemon());
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        // Banned pokemon should be mapped to themselves
        for (Pokemon bannedPK : banned) {
            translateMap.put(bannedPK, bannedPK);
            remainingLeft.remove(bannedPK);
            remainingRight.remove(bannedPK);
        }
        while (!remainingLeft.isEmpty()) {
            if (usePowerLevels) {
                int pickedLeft = this.random.nextInt(remainingLeft.size());
                Pokemon pickedLeftP = remainingLeft.remove(pickedLeft);
                Pokemon pickedRightP;
                if (remainingRight.size() == 1) {
                    // pick this (it may or may not be the same poke)
                    pickedRightP = remainingRight.get(0);
                } else {
                    // pick on power level with the current one blocked
                    pickedRightP = pickWildPowerLvlReplacement(remainingRight, pickedLeftP, true, null, 100);
                }
                remainingRight.remove(pickedRightP);
                translateMap.put(pickedLeftP, pickedRightP);
            } else {
                int pickedLeft = this.random.nextInt(remainingLeft.size());
                int pickedRight = this.random.nextInt(remainingRight.size());
                Pokemon pickedLeftP = remainingLeft.remove(pickedLeft);
                Pokemon pickedRightP = remainingRight.get(pickedRight);
                while (pickedLeftP.number == pickedRightP.number && remainingRight.size() != 1) {
                    // Reroll for a different pokemon if at all possible
                    pickedRight = this.random.nextInt(remainingRight.size());
                    pickedRightP = remainingRight.get(pickedRight);
                }
                remainingRight.remove(pickedRight);
                translateMap.put(pickedLeftP, pickedRightP);
            }
            if (remainingRight.size() == 0) {
                // restart
                if (allowAltFormes) {
                    remainingRight.addAll(getWildPool(true, noLegendaries, wildPokemonBSTLimit));
                    remainingRight.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                } else {
                    remainingRight.addAll(getWildPool(false, noLegendaries, wildPokemonBSTLimit));
                }
                remainingRight.removeAll(banned);
            }
        }

        // Map remaining to themselves just in case
        List<Pokemon> allPokes = allPokemonInclFormesWithoutNull();
        for (Pokemon poke : allPokes) {
            if (!translateMap.containsKey(poke)) {
                translateMap.put(poke, poke);
            }
        }

        List<EncounterSet> currentEncounters = this.getEncounters(useTimeOfDay);

        for (EncounterSet area : currentEncounters) {
            for (Encounter enc : area.encounters) {
                // Apply the map
                enc.pokemon = translateMap.get(enc.pokemon);
                if (area.bannedPokemon.contains(enc.pokemon)) {
                    // Ignore the map and put a random non-banned poke
                    List<Pokemon> tempPickable;
                    if (allowAltFormes) {
                        tempPickable = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                        tempPickable.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                    } else {
                        tempPickable = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
                    }
                    tempPickable.removeAll(banned);
                    tempPickable.removeAll(area.bannedPokemon);
                    if (tempPickable.size() == 0) {
                        throw new RandomizationException("ERROR: Couldn't replace a wild Pokemon!");
                    }
                    if (usePowerLevels) {
                        enc.pokemon = pickWildPowerLvlReplacement(tempPickable, enc.pokemon, false, null, 100);
                    } else {
                        int picked = this.random.nextInt(tempPickable.size());
                        enc.pokemon = tempPickable.get(picked);
                    }
                }
                setFormeForEncounter(enc, enc.pokemon);
            }
        }
        if (levelModifier != 0) {
            for (EncounterSet area : currentEncounters) {
                for (Encounter enc : area.encounters) {
                    enc.level = Math.min(100, (int) Math.round(enc.level * (1 + levelModifier / 100.0)));
                    enc.maxLevel = Math.min(100, (int) Math.round(enc.maxLevel * (1 + levelModifier / 100.0)));
                }
            }
        }

        setEncounters(useTimeOfDay, currentEncounters);

    }

    @Override
    public void onlyChangeWildLevels(Settings settings) {
        int levelModifier = settings.getWildLevelModifier();

        List<EncounterSet> currentEncounters = this.getEncounters(true);

        if (levelModifier != 0) {
            for (EncounterSet area : currentEncounters) {
                for (Encounter enc : area.encounters) {
                    enc.level = Math.min(100, (int) Math.round(enc.level * (1 + levelModifier / 100.0)));
                    enc.maxLevel = Math.min(100, (int) Math.round(enc.maxLevel * (1 + levelModifier / 100.0)));
                }
            }
            setEncounters(true, currentEncounters);
        }
    }

    private void enhanceRandomEncountersORAS(List<EncounterSet> collapsedEncounters, Settings settings) {
        boolean catchEmAll = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL;
        boolean typeThemed = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS;
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        int wildPokemonBSTLimit = settings.getWildPokemonBSTLimit();
        boolean allowAltFormes = settings.isAllowWildAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        List<Pokemon> banned = this.bannedForWildEncounters(settings);
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        Map<Integer, List<EncounterSet>> zonesToEncounters = mapZonesToEncounters(collapsedEncounters);
        Map<Type, List<Pokemon>> cachedPokeLists = new TreeMap<>();
        for (List<EncounterSet> encountersInZone : zonesToEncounters.values()) {
            int currentAreaIndex = -1;
            List<EncounterSet> nonRockSmashAreas = new ArrayList<>();
            Map<Integer, List<Integer>> areasAndEncountersToRandomize = new TreeMap<>();
            // Since Rock Smash Pokemon do not show up on DexNav, they can be fully randomized
            for (EncounterSet area : encountersInZone) {
                if (area.displayName.contains("Rock Smash")) {
                    // Assume EITHER catch em all OR type themed OR match strength for now
                    if (catchEmAll) {
                        for (Encounter enc : area.encounters) {
                            boolean shouldRandomize = doesAnotherEncounterWithSamePokemonExistInArea(enc, area);
                            if (shouldRandomize) {
                                enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, banned);
                                setFormeForEncounter(enc, enc.pokemon);
                            }
                        }
                    } else if (typeThemed) {
                        List<Pokemon> possiblePokemon = null;
                        int iterLoops = 0;
                        while (possiblePokemon == null && iterLoops < 10000) {
                            Type areaTheme = randomType();
                            if (!cachedPokeLists.containsKey(areaTheme)) {
                                List<Pokemon> pType = allowAltFormes ? pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                        pokemonOfType(areaTheme, noLegendaries);
                                pType.removeAll(banned);
                                cachedPokeLists.put(areaTheme, pType);
                            }
                            possiblePokemon = cachedPokeLists.get(areaTheme);
                            if (area.bannedPokemon.size() > 0) {
                                possiblePokemon = new ArrayList<>(possiblePokemon);
                                possiblePokemon.removeAll(area.bannedPokemon);
                            }
                            if (possiblePokemon.size() == 0) {
                                // Can't use this type for this area
                                possiblePokemon = null;
                            }
                            iterLoops++;
                        }
                        if (possiblePokemon == null) {
                            throw new RandomizationException("Could not randomize an area in a reasonable amount of attempts.");
                        }
                        for (Encounter enc : area.encounters) {
                            // Pick a random themed pokemon
                            enc.pokemon = weightedRandomPokemon(possiblePokemon);
                            while (enc.pokemon.actuallyCosmetic) {
                                enc.pokemon = weightedRandomPokemon(possiblePokemon);
                            }
                            setFormeForEncounter(enc, enc.pokemon);
                        }
                    } else if (usePowerLevels) {
                        List<Pokemon> allowedPokes;
                        if (allowAltFormes) {
                            allowedPokes = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                        } else {
                            allowedPokes = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
                        }
                        allowedPokes.removeAll(banned);
                        List<Pokemon> localAllowed = allowedPokes;
                        if (area.bannedPokemon.size() > 0) {
                            localAllowed = new ArrayList<>(allowedPokes);
                            localAllowed.removeAll(area.bannedPokemon);
                        }
                        for (Encounter enc : area.encounters) {
                            enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                            while (enc.pokemon.actuallyCosmetic) {
                                enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                            }
                            setFormeForEncounter(enc, enc.pokemon);
                        }
                    } else {
                        // Entirely random
                        for (Encounter enc : area.encounters) {
                            enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, banned);
                            setFormeForEncounter(enc, enc.pokemon);
                        }
                    }
                } else {
                    currentAreaIndex++;
                    nonRockSmashAreas.add(area);
                    List<Integer> encounterIndices = new ArrayList<>();
                    for (int i = 0; i < area.encounters.size(); i++) {
                        encounterIndices.add(i);
                    }
                    areasAndEncountersToRandomize.put(currentAreaIndex, encounterIndices);
                }
            }

            // Now, randomize non-Rock Smash Pokemon until we hit the threshold for DexNav
            int crashThreshold = computeDexNavCrashThreshold(encountersInZone);
            while (crashThreshold < 18 && areasAndEncountersToRandomize.size() > 0) {
                Set<Integer> areaIndices = areasAndEncountersToRandomize.keySet();
                int areaIndex = areaIndices.stream().skip(this.random.nextInt(areaIndices.size())).findFirst().orElse(-1);
                List<Integer> encounterIndices = areasAndEncountersToRandomize.get(areaIndex);
                int indexInListOfEncounterIndices = this.random.nextInt(encounterIndices.size());
                int randomEncounterIndex = encounterIndices.get(indexInListOfEncounterIndices);
                EncounterSet area = nonRockSmashAreas.get(areaIndex);
                Encounter enc = area.encounters.get(randomEncounterIndex);
                // Assume EITHER catch em all OR type themed OR match strength for now
                if (catchEmAll) {
                    boolean shouldRandomize = doesAnotherEncounterWithSamePokemonExistInArea(enc, area);
                    if (shouldRandomize) {
                        enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, banned);
                        setFormeForEncounter(enc, enc.pokemon);
                    }
                } else if (typeThemed) {
                    List<Pokemon> possiblePokemon = null;
                    Type areaTheme = getTypeForArea(area);
                    if (!cachedPokeLists.containsKey(areaTheme)) {
                        List<Pokemon> pType = allowAltFormes ? pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                pokemonOfType(areaTheme, noLegendaries);
                        pType.removeAll(banned);
                        cachedPokeLists.put(areaTheme, pType);
                    }
                    possiblePokemon = cachedPokeLists.get(areaTheme);
                    if (area.bannedPokemon.size() > 0) {
                        possiblePokemon = new ArrayList<>(possiblePokemon);
                        possiblePokemon.removeAll(area.bannedPokemon);
                    }
                    if (possiblePokemon.size() == 0) {
                        // Can't use this type for this area
                        throw new RandomizationException("Could not find a possible Pokemon of the correct type.");
                    }
                    // Pick a random themed pokemon
                    enc.pokemon = weightedRandomPokemon(possiblePokemon);
                    while (enc.pokemon.actuallyCosmetic) {
                        enc.pokemon = weightedRandomPokemon(possiblePokemon);
                    }
                    setFormeForEncounter(enc, enc.pokemon);
                } else if (usePowerLevels) {
                    List<Pokemon> allowedPokes;
                    if (allowAltFormes) {
                        allowedPokes = getWildPool(true, noLegendaries, wildPokemonBSTLimit);
                    } else {
                        allowedPokes = getWildPool(false, noLegendaries, wildPokemonBSTLimit);
                    }
                    allowedPokes.removeAll(banned);
                    List<Pokemon> localAllowed = allowedPokes;
                    if (area.bannedPokemon.size() > 0) {
                        localAllowed = new ArrayList<>(allowedPokes);
                        localAllowed.removeAll(area.bannedPokemon);
                    }
                    enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                    while (enc.pokemon.actuallyCosmetic) {
                        enc.pokemon = pickWildPowerLvlReplacement(localAllowed, enc.pokemon, false, null, 100);
                    }
                    setFormeForEncounter(enc, enc.pokemon);
                } else {
                    // Entirely random
                    enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, wildPokemonBSTLimit, area, banned);
                    setFormeForEncounter(enc, enc.pokemon);
                }
                crashThreshold = computeDexNavCrashThreshold(encountersInZone);
                encounterIndices.remove(indexInListOfEncounterIndices);
                if (encounterIndices.size() == 0) {
                    areasAndEncountersToRandomize.remove(areaIndex);
                }
            }
        }
    }

    private Type getTypeForArea(EncounterSet area) {
        Pokemon firstPokemon = area.encounters.get(0).pokemon;
        if (area.encounters.get(0).formeNumber != 0) {
            firstPokemon = getAltFormeOfPokemon(firstPokemon, area.encounters.get(0).formeNumber);
        }
        Type primaryType = firstPokemon.primaryType;
        int primaryCount = 1;
        Type secondaryType = null;
        int secondaryCount = 0;
        if (firstPokemon.secondaryType != null) {
            secondaryType = firstPokemon.secondaryType;
            secondaryCount = 1;
        }
        for (int i = 1; i < area.encounters.size(); i++) {
            Pokemon pokemon = area.encounters.get(i).pokemon;
            if (area.encounters.get(i).formeNumber != 0) {
                pokemon = getAltFormeOfPokemon(pokemon, area.encounters.get(i).formeNumber);
            }
            if (pokemon.primaryType == primaryType || pokemon.secondaryType == primaryType) {
                primaryCount++;
            }
            if (pokemon.primaryType == secondaryType || pokemon.secondaryType == secondaryType) {
                secondaryCount++;
            }
        }
        return primaryCount > secondaryCount ? primaryType : secondaryType;
    }

    private boolean doesAnotherEncounterWithSamePokemonExistInArea(Encounter enc, EncounterSet area) {
        for (Encounter encounterToCheck : area.encounters) {
            if (enc != encounterToCheck && enc.pokemon == encounterToCheck.pokemon) {
                return true;
            }
        }
        return false;
    }

    private List<EncounterSet> collapseAreasORAS(List<EncounterSet> currentEncounters) {
        List<EncounterSet> output = new ArrayList<>();
        Map<Integer, List<EncounterSet>> zonesToEncounters = mapZonesToEncounters(currentEncounters);
        for (Integer zone : zonesToEncounters.keySet()) {
            List<EncounterSet> encountersInZone = zonesToEncounters.get(zone);
            int crashThreshold = computeDexNavCrashThreshold(encountersInZone);
            if (crashThreshold <= 18) {
                output.addAll(encountersInZone);
                continue;
            }

            // Naive Area 1-to-1 randomization will crash the game, so let's start collapsing areas to prevent this.
            // Start with combining all the fishing rod encounters, since it's a little less noticeable when they've
            // been collapsed.
            List<EncounterSet> collapsedEncounters = new ArrayList<>(encountersInZone);
            EncounterSet rodGroup = new EncounterSet();
            rodGroup.offset = zone;
            rodGroup.displayName = "Rod Group";
            for (EncounterSet area : encountersInZone) {
                if (area.displayName.contains("Old Rod") || area.displayName.contains("Good Rod") || area.displayName.contains("Super Rod")) {
                    collapsedEncounters.remove(area);
                    rodGroup.encounters.addAll(area.encounters);
                }
            }
            if (rodGroup.encounters.size() > 0) {
                collapsedEncounters.add(rodGroup);
            }
            crashThreshold = computeDexNavCrashThreshold(collapsedEncounters);
            if (crashThreshold <= 18) {
                output.addAll(collapsedEncounters);
                continue;
            }

            // Even after combining all the fishing rod encounters, we're still not below the threshold to prevent
            // DexNav from crashing the game. Combine all the grass encounters now to drop us below the threshold;
            // we've combined everything that DexNav normally combines, so at this point, we're *guaranteed* not
            // to crash the game.
            EncounterSet grassGroup = new EncounterSet();
            grassGroup.offset = zone;
            grassGroup.displayName = "Grass Group";
            for (EncounterSet area : encountersInZone) {
                if (area.displayName.contains("Grass/Cave") || area.displayName.contains("Long Grass") || area.displayName.contains("Horde")) {
                    collapsedEncounters.remove(area);
                    grassGroup.encounters.addAll(area.encounters);
                }
            }
            if (grassGroup.encounters.size() > 0) {
                collapsedEncounters.add(grassGroup);
            }

            output.addAll(collapsedEncounters);
        }
        return output;
    }

    private int computeDexNavCrashThreshold(List<EncounterSet> encountersInZone) {
        int crashThreshold = 0;
        for (EncounterSet area : encountersInZone) {
            if (area.displayName.contains("Rock Smash")) {
                continue; // Rock Smash Pokemon don't display on DexNav
            }
            Set<Pokemon> uniquePokemonInArea = new HashSet<>();
            for (Encounter enc : area.encounters) {
                if (enc.pokemon.baseForme != null) { // DexNav treats different forms as one Pokemon
                    uniquePokemonInArea.add(enc.pokemon.baseForme);
                } else {
                    uniquePokemonInArea.add(enc.pokemon);
                }
            }
            crashThreshold += uniquePokemonInArea.size();
        }
        return crashThreshold;
    }

    private void setEvoChainAsIllegal(Pokemon newPK, List<Pokemon> illegalList, boolean willForceEvolve) {
        // set pre-evos as illegal
        setIllegalPreEvos(newPK, illegalList);

        // if the placed Pokemon will be forced fully evolved, set its evolutions as illegal
        if (willForceEvolve) {
            setIllegalEvos(newPK, illegalList);
        }
    }

    private void setIllegalPreEvos(Pokemon pk, List<Pokemon> illegalList) {
        for (Evolution evo: pk.evolutionsTo) {
            pk = evo.from;
            illegalList.add(pk);
            setIllegalPreEvos(pk, illegalList);
        }
    }

    private void setIllegalEvos(Pokemon pk, List<Pokemon> illegalList) {
        for (Evolution evo: pk.evolutionsFrom) {
            pk = evo.to;
            illegalList.add(pk);
            setIllegalEvos(pk, illegalList);
        }
    }

    private List<Pokemon> getFinalEvos(Pokemon pk) {
        List<Pokemon> finalEvos = new ArrayList<>();
        traverseEvolutions(pk, finalEvos);
        return finalEvos;
    }

    private void traverseEvolutions(Pokemon pk, List<Pokemon> finalEvos) {
        if (!pk.evolutionsFrom.isEmpty()) {
            for (Evolution evo: pk.evolutionsFrom) {
                pk = evo.to;
                traverseEvolutions(pk, finalEvos);
            }
        } else {
            finalEvos.add(pk);
        }
    }

    private void setFormeForTrainerPokemon(TrainerPokemon tp, Pokemon pk) {
        boolean checkCosmetics = true;
        tp.formeSuffix = "";
        tp.forme = 0;
        if (pk.formeNumber > 0) {
            tp.forme = pk.formeNumber;
            tp.formeSuffix = pk.formeSuffix;
            tp.pokemon = pk.baseForme;
            checkCosmetics = false;
        }
        if (checkCosmetics && tp.pokemon.cosmeticForms > 0) {
            tp.forme = tp.pokemon.getCosmeticFormNumber(this.random.nextInt(tp.pokemon.cosmeticForms));
        } else if (!checkCosmetics && pk.cosmeticForms > 0) {
            tp.forme += pk.getCosmeticFormNumber(this.random.nextInt(pk.cosmeticForms));
        }
    }

    private void applyLevelModifierToTrainerPokemon(Trainer trainer, int levelModifier) {
        if (levelModifier != 0) {
            for (TrainerPokemon tp : trainer.pokemon) {
                tp.level = Math.min(100, (int) Math.round(tp.level * (1 + levelModifier / 100.0)));
            }
        }
    }

    @Override
    public void randomizeTrainerPokes(Settings settings) {
        boolean usePowerLevels = settings.isTrainersUsePokemonOfSimilarStrength();
        boolean weightByFrequency = settings.isTrainersMatchTypingDistribution();
        boolean noLegendaries = settings.isTrainersBlockLegendaries();
        boolean noEarlyWonderGuard = settings.isTrainersBlockEarlyWonderGuard();
        int levelModifier = settings.isTrainersLevelModified() ? settings.getTrainersLevelModifier() : 0;
        boolean isTypeThemed = settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED;
        boolean isTypeThemedEliteFourGymOnly = settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED_ELITE4_GYMS;
        boolean distributionSetting = settings.getTrainersMod() == Settings.TrainersMod.DISTRIBUTED;
        boolean mainPlaythroughSetting = settings.getTrainersMod() == Settings.TrainersMod.MAINPLAYTHROUGH;
        boolean includeFormes = settings.isAllowTrainerAlternateFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean swapMegaEvos = settings.isSwapTrainerMegaEvos();
        boolean shinyChance = settings.isShinyChance();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;
        int eliteFourUniquePokemonNumber = settings.getEliteFourUniquePokemonNumber();
        boolean forceFullyEvolved = settings.isTrainersForceFullyEvolved();
        int forceFullyEvolvedLevel = settings.getTrainersForceFullyEvolvedLevel();
        boolean forceChallengeMode = (settings.getCurrentMiscTweaks() & MiscTweak.FORCE_CHALLENGE_MODE.getValue()) > 0;
        boolean rivalCarriesStarter = settings.isRivalCarriesStarterThroughout();

        checkPokemonRestrictions();

        // Set up Pokemon pool
        cachedReplacementLists = new TreeMap<>();
        cachedAllList = noLegendaries ? new ArrayList<>(noLegendaryList) : new ArrayList<>(
                mainPokemonList);
        if (includeFormes) {
            if (noLegendaries) {
                cachedAllList.addAll(noLegendaryAltsList);
            } else {
                cachedAllList.addAll(altFormesList);
            }
        }
        cachedAllList =
                cachedAllList
                        .stream()
                        .filter(pk -> !pk.actuallyCosmetic)
                        .collect(Collectors.toList());

        List<Pokemon> banned = this.getBannedFormesForTrainerPokemon();
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        cachedAllList.removeAll(banned);

        List<Trainer> currentTrainers = this.getTrainers();

        // Type Themed related
        Map<Trainer, Type> trainerTypes = new TreeMap<>();
        Set<Type> usedUberTypes = new TreeSet<>();
        if (isTypeThemed || isTypeThemedEliteFourGymOnly) {
            typeWeightings = new TreeMap<>();
            totalTypeWeighting = 0;
            // Construct groupings for types
            // Anything starting with GYM or ELITE or CHAMPION is a group
            Map<String, List<Trainer>> groups = new TreeMap<>();
            for (Trainer t : currentTrainers) {
                if (t.tag != null && t.tag.equals("IRIVAL")) {
                    // This is the first rival in Yellow. His Pokemon is used to determine the non-player
                    // starter, so we can't change it here. Just skip it.
                    continue;
                }
                String group = t.tag == null ? "" : t.tag;
                if (group.contains("-")) {
                    group = group.substring(0, group.indexOf('-'));
                }
                if (group.startsWith("GYM") || group.startsWith("ELITE") ||
                        ((group.startsWith("CHAMPION") || group.startsWith("THEMED")) && !isTypeThemedEliteFourGymOnly)) {
                    // Yep this is a group
                    if (!groups.containsKey(group)) {
                        groups.put(group, new ArrayList<>());
                    }
                    groups.get(group).add(t);
                } else if (group.startsWith("GIO")) {
                    // Giovanni has same grouping as his gym, gym 8
                    if (!groups.containsKey("GYM8")) {
                        groups.put("GYM8", new ArrayList<>());
                    }
                    groups.get("GYM8").add(t);
                }
            }

            // Give a type to each group
            // Gym & elite types have to be unique
            // So do uber types, including the type we pick for champion
            Set<Type> usedGymTypes = new TreeSet<>();
            Set<Type> usedEliteTypes = new TreeSet<>();
            for (String group : groups.keySet()) {
                List<Trainer> trainersInGroup = groups.get(group);
                // Shuffle ordering within group to promote randomness
                Collections.shuffle(trainersInGroup, random);
                Type typeForGroup = pickType(weightByFrequency, noLegendaries, includeFormes);
                if (group.startsWith("GYM")) {
                    while (usedGymTypes.contains(typeForGroup)) {
                        typeForGroup = pickType(weightByFrequency, noLegendaries, includeFormes);
                    }
                    usedGymTypes.add(typeForGroup);
                }
                if (group.startsWith("ELITE")) {
                    while (usedEliteTypes.contains(typeForGroup)) {
                        typeForGroup = pickType(weightByFrequency, noLegendaries, includeFormes);
                    }
                    usedEliteTypes.add(typeForGroup);
                }
                if (group.equals("CHAMPION")) {
                    usedUberTypes.add(typeForGroup);
                }

                for (Trainer t : trainersInGroup) {
                    trainerTypes.put(t, typeForGroup);
                }
            }
        }

        // Randomize the order trainers are randomized in.
        // Leads to less predictable results for various modifiers.
        // Need to keep the original ordering around for saving though.
        List<Trainer> scrambledTrainers = new ArrayList<>(currentTrainers);
        Collections.shuffle(scrambledTrainers, this.random);

        // Elite Four Unique Pokemon related
        boolean eliteFourUniquePokemon = eliteFourUniquePokemonNumber > 0;
        List<Pokemon> illegalIfEvolvedList = new ArrayList<>();
        List<Pokemon> bannedFromUniqueList = new ArrayList<>();
        boolean illegalEvoChains = false;
        List<Integer> eliteFourIndices = getEliteFourTrainers(forceChallengeMode);
        if (eliteFourUniquePokemon) {
            // Sort Elite Four Trainers to the start of the list
            scrambledTrainers.sort((t1, t2) ->
                    Boolean.compare(eliteFourIndices.contains(currentTrainers.indexOf(t2)+1),eliteFourIndices.contains(currentTrainers.indexOf(t1)+1)));
            illegalEvoChains = forceFullyEvolved;
            if (rivalCarriesStarter) {
                List<Pokemon> starterList = getStarters().subList(0,3);
                for (Pokemon starter: starterList) {
                    // If rival/friend carries starter, the starters cannot be set as unique
                    bannedFromUniqueList.add(starter);
                    setEvoChainAsIllegal(starter, bannedFromUniqueList, true);

                    // If the final boss is a rival/friend, the fully evolved starters will be unique
                    if (hasRivalFinalBattle()) {
                        cachedAllList.removeAll(getFinalEvos(starter));
                        if (illegalEvoChains) {
                            illegalIfEvolvedList.add(starter);
                            setEvoChainAsIllegal(starter, illegalIfEvolvedList, true);
                        }
                    }
                }
            }
        }

        List<Integer> mainPlaythroughTrainers = getMainPlaythroughTrainers();

        // Randomize Trainer Pokemon
        // The result after this is done will not be final if "Force Fully Evolved" or "Rival Carries Starter"
        // are used, as they are applied later
        for (Trainer t : scrambledTrainers) {
            applyLevelModifierToTrainerPokemon(t, levelModifier);
            if (t.tag != null && t.tag.equals("IRIVAL")) {
                // This is the first rival in Yellow. His Pokemon is used to determine the non-player
                // starter, so we can't change it here. Just skip it.
                continue;
            }

            // If type themed, give a type to each unassigned trainer
            Type typeForTrainer = trainerTypes.get(t);
            if (typeForTrainer == null && isTypeThemed) {
                typeForTrainer = pickType(weightByFrequency, noLegendaries, includeFormes);
                // Ubers: can't have the same type as each other
                if (t.tag != null && t.tag.equals("UBER")) {
                    while (usedUberTypes.contains(typeForTrainer)) {
                        typeForTrainer = pickType(weightByFrequency, noLegendaries, includeFormes);
                    }
                    usedUberTypes.add(typeForTrainer);
                }
            }

            List<Pokemon> evolvesIntoTheWrongType = new ArrayList<>();
            if (typeForTrainer != null) {
                List<Pokemon> pokemonOfType = includeFormes ? pokemonOfTypeInclFormes(typeForTrainer, noLegendaries) :
                        pokemonOfType(typeForTrainer, noLegendaries);
                for (Pokemon pk : pokemonOfType) {
                    if (!pokemonOfType.contains(fullyEvolve(pk, t.index))) {
                        evolvesIntoTheWrongType.add(pk);
                    }
                }
            }

            List<TrainerPokemon> trainerPokemonList = new ArrayList<>(t.pokemon);

            // Elite Four Unique Pokemon related
            boolean eliteFourTrackPokemon = false;
            boolean eliteFourRival = false;
            if (eliteFourUniquePokemon && eliteFourIndices.contains(t.index)) {
                eliteFourTrackPokemon = true;

                // Sort Pokemon list back to front, and then put highest level Pokemon first
                // (Only while randomizing, does not affect order in game)
                Collections.reverse(trainerPokemonList);
                trainerPokemonList.sort((tp1, tp2) -> Integer.compare(tp2.level, tp1.level));
                if (rivalCarriesStarter && (t.tag.contains("RIVAL") || t.tag.contains("FRIEND"))) {
                    eliteFourRival = true;
                }
            }

            for (TrainerPokemon tp : trainerPokemonList) {
                //boolean swapThisMegaEvo = swapMegaEvos && tp.canMegaEvolve();
                boolean wgAllowed = (!noEarlyWonderGuard) || tp.level >= 20;
                boolean eliteFourSetUniquePokemon =
                        eliteFourTrackPokemon && eliteFourUniquePokemonNumber > trainerPokemonList.indexOf(tp);
                boolean willForceEvolve = forceFullyEvolved && tp.level >= forceFullyEvolvedLevel;

                Pokemon oldPK = tp.pokemon;
                if (tp.forme > 0) {
                    oldPK = getAltFormeOfPokemon(oldPK, tp.forme);
                }

                bannedList = new ArrayList<>();
                bannedList.addAll(usedAsUniqueList);
                if (illegalEvoChains && willForceEvolve) {
                    bannedList.addAll(illegalIfEvolvedList);
                }
                if (eliteFourSetUniquePokemon) {
                    bannedList.addAll(bannedFromUniqueList);
                }
                if (willForceEvolve) {
                    bannedList.addAll(evolvesIntoTheWrongType);
                }
                bannedList.add(getPokemon().get(Species.pikachuCap));
                bannedList.add(getPokemon().get(Species.pikachuPartner));
                bannedList.add(getPokemon().get(Species.eeveePartner));
                bannedList.add(getPokemon().get(Species.pichuSpiky));
                bannedList.add(getPokemon().get(Species.cherrimS));
                bannedList.add(getPokemon().get(Species.greninjaB));
                bannedList.add(getPokemon().get(Species.morpekoH));
                if (!abilitiesAreRandomized) {
                    bannedList.add(getPokemon().get(Species.castformF));
                    bannedList.add(getPokemon().get(Species.castformW));
                    bannedList.add(getPokemon().get(Species.castformI));
                }

                Pokemon newPK = pickTrainerPokeReplacement(
                                oldPK,
                                usePowerLevels,
                                typeForTrainer,
                                noLegendaries,
                                wgAllowed,
                                distributionSetting || (mainPlaythroughSetting && mainPlaythroughTrainers.contains(t.index)),
                                //swapThisMegaEvo,
                                abilitiesAreRandomized,
                                includeFormes,
                                banIrregularAltFormes
                        );

                // Chosen Pokemon is locked in past here
                if (distributionSetting || (mainPlaythroughSetting && mainPlaythroughTrainers.contains(t.index))) {
                    setPlacementHistory(newPK);
                }
                tp.pokemon = newPK;
                setFormeForTrainerPokemon(tp, newPK);
                tp.abilitySlot = getRandomAbilitySlot(newPK);
                tp.resetMoves = true;

                if (!eliteFourRival) {
                    if (eliteFourSetUniquePokemon) {
                        List<Pokemon> actualPKList;
                        if (willForceEvolve) {
                            actualPKList = getFinalEvos(newPK);
                        } else {
                            actualPKList = new ArrayList<>();
                            actualPKList.add(newPK);
                        }
                        // If the unique Pokemon will evolve, we have to set all its potential evolutions as unique
                        for (Pokemon actualPK: actualPKList) {
                            usedAsUniqueList.add(actualPK);
                            if (illegalEvoChains) {
                                setEvoChainAsIllegal(actualPK, illegalIfEvolvedList, willForceEvolve);
                            }
                        }
                    }
                    if (eliteFourTrackPokemon) {
                        bannedFromUniqueList.add(newPK);
                        if (illegalEvoChains) {
                            setEvoChainAsIllegal(newPK, bannedFromUniqueList, willForceEvolve);
                        }
                    }
                } else {
                    // If the champion is a rival, the first Pokemon will be skipped - it's already
                    // set as unique since it's a starter
                    eliteFourRival = false;
                }

                //if (swapThisMegaEvo) {
                //    tp.heldItem = newPK
                //                    .megaEvolutionsFrom
                //                    .get(this.random.nextInt(newPK.megaEvolutionsFrom.size()))
                //                    .argument;
                //}

                if (shinyChance) {
                    if (this.random.nextInt(256) == 0) {
                        tp.monIsShiny = 1;
                    }
                }
                tp.forcedGenderFlag = this.random.nextInt(8);
                tp.pokeBallId = this.random.nextInt(Gen3Constants.pokeBallCount);
            }
        }

        // Save it all up
        this.setTrainers(currentTrainers, false, false);
    }

    @Override
    public void randomizeTrainerHeldItems(Settings settings) {
        boolean giveToBossPokemon = settings.isRandomizeHeldItemsForBossTrainerPokemon();
        boolean giveToImportantPokemon = settings.isRandomizeHeldItemsForImportantTrainerPokemon();
        boolean giveToRegularPokemon = settings.isRandomizeHeldItemsForRegularTrainerPokemon();
        boolean highestLevelOnly = settings.isHighestLevelGetsItemsForTrainers();

        List<Move> moves = this.getMoves();
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();
        List<Trainer> currentTrainers = this.getTrainers();
        for (Trainer t : currentTrainers) {
            if (trainerShouldNotGetBuffs(t)) {
                continue;
            }
            if (!giveToRegularPokemon && (!t.isImportant() && !t.isBoss())) {
                continue;
            }
            if (!giveToImportantPokemon && t.isImportant()) {
                continue;
            }
            if (!giveToBossPokemon && t.isBoss()) {
                continue;
            }
            t.setPokemonHaveItems(true);
            if (highestLevelOnly) {
                int maxLevel = -1;
                TrainerPokemon highestLevelPoke = null;
                for (TrainerPokemon tp : t.pokemon) {
                    if (tp.level > maxLevel) {
                        highestLevelPoke = tp;
                        maxLevel = tp.level;
                    }
                }
                if (highestLevelPoke == null) {
                    continue; // should never happen - trainer had zero pokes
                }
                int[] moveset = highestLevelPoke.resetMoves ?
                        RomFunctions.getMovesAtLevel(getAltFormeOfPokemon(
                                highestLevelPoke.pokemon, highestLevelPoke.forme).number,
                                movesets,
                                highestLevelPoke.level) :
                        highestLevelPoke.moves;
                randomizeHeldItem(highestLevelPoke, settings, moves, moveset);
            } else {
                for (TrainerPokemon tp : t.pokemon) {
                    int[] moveset = tp.resetMoves ?
                            RomFunctions.getMovesAtLevel(getAltFormeOfPokemon(
                                    tp.pokemon, tp.forme).number,
                                    movesets,
                                    tp.level) :
                            tp.moves;
                    randomizeHeldItem(tp, settings, moves, moveset);
                    if (t.requiresUniqueHeldItems) {
                        while (!t.pokemonHaveUniqueHeldItems()) {
                            randomizeHeldItem(tp, settings, moves, moveset);
                        }
                    }
                }
            }
        }
        this.setTrainers(currentTrainers, false, false);
    }

    private void randomizeHeldItem(TrainerPokemon tp, Settings settings, List<Move> moves, int[] moveset) {
        boolean sensibleItemsOnly = settings.isSensibleItemsOnlyForTrainers();
        boolean consumableItemsOnly = settings.isConsumableItemsOnlyForTrainers();
        //boolean swapMegaEvolutions = settings.isSwapTrainerMegaEvos();
        if (tp.hasZCrystal) {
            return; // Don't overwrite existing Z Crystals.
        }
        //if (tp.hasMegaStone && swapMegaEvolutions) {
        //    return; // Don't overwrite mega stones if another setting handled that.
        //}
        List<Integer> toChooseFrom;
        if (sensibleItemsOnly) {
            toChooseFrom = getSensibleHeldItemsFor(tp, consumableItemsOnly, moves, moveset);
        } else if (consumableItemsOnly) {
            toChooseFrom = getAllConsumableHeldItems();
        } else {
            toChooseFrom = getAllHeldItems();
        }
        tp.heldItem = toChooseFrom.get(random.nextInt(toChooseFrom.size()));
    }

    @Override
    public void rivalCarriesStarter() {
        checkPokemonRestrictions();
        List<Trainer> currentTrainers = this.getTrainers();
        rivalCarriesStarterUpdate(currentTrainers, "RIVAL", isORAS ? 0 : 1);
        rivalCarriesStarterUpdate(currentTrainers, "FRIEND", 2);
        this.setTrainers(currentTrainers, false, false);
    }

    @Override
    public boolean hasRivalFinalBattle() {
        return false;
    }

    @Override
    public void forceFullyEvolvedTrainerPokes(Settings settings) {
        int minLevel = settings.getTrainersForceFullyEvolvedLevel();
        boolean rivalCarriesStarter = settings.isRivalCarriesStarterThroughout();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        checkPokemonRestrictions();
        
        List<Pokemon> fullyEvolvedPool = new ArrayList<>();
        List<Integer> bannedSpecies = new ArrayList<>();
        bannedSpecies.add(Species.pikachuCap);
        bannedSpecies.add(Species.pikachuPartner);
        bannedSpecies.add(Species.eeveePartner);
        bannedSpecies.add(Species.pichuSpiky);
        bannedSpecies.add(Species.cherrimS);
        bannedSpecies.add(Species.greninjaB);
        bannedSpecies.add(Species.morpekoH);
        if (!abilitiesAreRandomized) {
            bannedSpecies.add(Species.castformF);
            bannedSpecies.add(Species.castformW);
            bannedSpecies.add(Species.castformI);
        }
        
        for (Pokemon pk : mainPokemonList) {
            if (pk != null && pk.evolutionsFrom.size() == 0 && !bannedSpecies.contains(pk.number)) {
                fullyEvolvedPool.add(pk);
            }
        }
        
        if (fullyEvolvedPool.isEmpty()) {
            fullyEvolvedPool = new ArrayList<>(mainPokemonList);
        }
        
        List<Trainer> currentTrainers = this.getTrainers();
        for (Trainer t : currentTrainers) {
            int carriedStarterIndex = rivalCarriesStarter ? getCarriedStarterIndex(t) : -1;
            for (int pokemonIndex = 0; pokemonIndex < t.pokemon.size(); pokemonIndex++) {
                TrainerPokemon tp = t.pokemon.get(pokemonIndex);
                if (tp.level >= minLevel) {
                    if (tp.pokemon.evolutionsFrom.size() > 0) {
                        Pokemon newPokemon;
                        if (pokemonIndex == carriedStarterIndex) {
                            newPokemon = fullyEvolve(tp.pokemon, t.index);
                        } else {
                            newPokemon = weightedRandomPokemon(fullyEvolvedPool);
                        }
                        tp.pokemon = newPokemon;
                        setFormeForTrainerPokemon(tp, newPokemon);
                        tp.abilitySlot = getValidAbilitySlotFromOriginal(newPokemon, tp.abilitySlot);
                        tp.resetMoves = true;
                    }
                }
            }
        }
        this.setTrainers(currentTrainers, false, false);
    }

    private int getCarriedStarterIndex(Trainer trainer) {
        if (trainer.tag == null || (!trainer.tag.startsWith("RIVAL") && !trainer.tag.startsWith("FRIEND"))) {
            return -1;
        }

        if (trainer.forceStarterPosition >= 0) {
            return trainer.forceStarterPosition;
        }

        int bestIndex = 0;
        TrainerPokemon bestPoke = trainer.pokemon.get(bestIndex);
        int trainerPkmnCount = trainer.pokemon.size();
        for (int i = 1; i < trainerPkmnCount; i++) {
            int levelBonus = (i == trainerPkmnCount - 1) ? 2 : 0;
            if (trainer.pokemon.get(i).level + levelBonus > bestPoke.level) {
                bestIndex = i;
                bestPoke = trainer.pokemon.get(i);
            }
        }
        return bestIndex;
    }

    @Override
    public void onlyChangeTrainerLevels(Settings settings) {
        int levelModifier = settings.getTrainersLevelModifier();

        List<Trainer> currentTrainers = this.getTrainers();
        for (Trainer t: currentTrainers) {
            applyLevelModifierToTrainerPokemon(t, levelModifier);
        }
        this.setTrainers(currentTrainers, false, false);
    }

    @Override
    public void addTrainerPokemon(Settings settings) {
        int additionalNormal = settings.getAdditionalRegularTrainerPokemon();
        int additionalImportant = settings.getAdditionalImportantTrainerPokemon();
        int additionalBoss = settings.getAdditionalBossTrainerPokemon();

        List<Trainer> currentTrainers = this.getTrainers();
        for (Trainer t: currentTrainers) {
            int additional;
            if (t.isBoss()) {
                additional = additionalBoss;
            } else if (t.isImportant()) {
                if (t.skipImportant()) continue;
                additional = additionalImportant;
            } else {
                additional = additionalNormal;
            }

            if (additional == 0) {
                continue;
            }

            int lowest = 100;
            List<TrainerPokemon> potentialPokes = new ArrayList<>();

            // First pass: find lowest level
            for (TrainerPokemon tpk: t.pokemon) {
                if (tpk.level < lowest) {
                    lowest = tpk.level;
                }
            }

            // Second pass: find all Pokemon at lowest level
            for (TrainerPokemon tpk: t.pokemon) {
                if (tpk.level == lowest) {
                    potentialPokes.add(tpk);
                }
            }

            // If a trainer can appear in a Multi Battle (i.e., a Double Battle where the enemy consists
            // of two independent trainers), we want to be aware of that so we don't give them a team of
            // six Pokemon and have a 6v12 battle
            int maxPokemon = t.multiBattleStatus != Trainer.MultiBattleStatus.NEVER ? 3 : 6;
            for (int i = 0; i < additional; i++) {
                if (t.pokemon.size() >= maxPokemon) break;

                // We want to preserve the original last Pokemon because the order is sometimes used to
                // determine the rival's starter
                int secondToLastIndex = t.pokemon.size() - 1;
                TrainerPokemon newPokemon = potentialPokes.get(i % potentialPokes.size()).copy();

                // Clear out the held item because we only want one Pokemon with a mega stone if we're
                // swapping mega evolvables
                newPokemon.heldItem = 0;
                t.pokemon.add(secondToLastIndex, newPokemon);
            }
        }
        this.setTrainers(currentTrainers, false, false);
    }

    @Override
    public void doubleBattleMode() {
        List<Trainer> currentTrainers = this.getTrainers();
        for (Trainer t: currentTrainers) {
            if (t.pokemon.size() != 1 || t.multiBattleStatus == Trainer.MultiBattleStatus.ALWAYS || this.trainerShouldNotGetBuffs(t)) {
                continue;
            }
            t.pokemon.add(t.pokemon.get(0).copy());
        }
        this.setTrainers(currentTrainers, true, false);
    }

    @Override
    public void smartAiMode() {
        List<Trainer> currentTrainers = this.getTrainers();
        this.setTrainers(currentTrainers, false, true);
    }

    private Map<Integer, List<MoveLearnt>> allLevelUpMoves;
    private Map<Integer, List<Integer>> allEggMoves;
    private Map<Pokemon, boolean[]> allTMCompat, allTutorCompat;
    private List<Integer> allTMMoves, allTutorMoves;

    @Override
    public List<Move> getMoveSelectionPoolAtLevel(TrainerPokemon tp, boolean cyclicEvolutions) {

        List<Move> moves = getMoves();
        double eggMoveProbability = 0.1;
        double preEvoMoveProbability = 0.5;
        double tmMoveProbability = 0.6;
        double tutorMoveProbability = 0.6;

        if (allLevelUpMoves == null) {
            allLevelUpMoves = getMovesLearnt();
        }

        if (allEggMoves == null) {
            allEggMoves = getEggMoves();
        }

        if (allTMCompat == null) {
            allTMCompat = getTMHMCompatibility();
        }

        if (allTMMoves == null) {
            allTMMoves = getTMMoves();
        }

        if (allTutorCompat == null && hasMoveTutors()) {
            allTutorCompat = getMoveTutorCompatibility();
        }

        if (allTutorMoves == null) {
            allTutorMoves = getMoveTutorMoves();
        }

        // Level-up Moves
        List<Move> moveSelectionPoolAtLevel = allLevelUpMoves.get(getAltFormeOfPokemon(tp.pokemon, tp.forme).number)
                .stream()
                .filter(ml -> (ml.level <= tp.level && ml.level != 0) || (ml.level == 0 && tp.level >= 30))
                .filter(ml -> ml.move != 0)  // Exclude moves where ml.move is 0
                .map(ml -> moves.get(ml.move))
                .distinct()
                .collect(Collectors.toList());

        // Pre-Evo Moves
        if (!cyclicEvolutions) {
            Pokemon preEvo;
            if (altFormesCanHaveDifferentEvolutions()) {
                preEvo = getAltFormeOfPokemon(tp.pokemon, tp.forme);
            } else {
                preEvo = tp.pokemon;
            }
            while (!preEvo.evolutionsTo.isEmpty()) {
                preEvo = preEvo.evolutionsTo.get(0).from;
                moveSelectionPoolAtLevel.addAll(allLevelUpMoves.get(preEvo.number)
                        .stream()
                        .filter(ml -> ml.level <= tp.level)
                        .filter(ml -> ml.move != 0)  // Exclude moves where ml.move is 0
                        .filter(ml -> this.random.nextDouble() < preEvoMoveProbability)
                        .map(ml -> moves.get(ml.move))
                        .distinct()
                        .collect(Collectors.toList()));
            }
        }

        // TM Moves
        boolean[] tmCompat = allTMCompat.get(getAltFormeOfPokemon(tp.pokemon, tp.forme));
        for (int tmMove: allTMMoves) {
            if (tmCompat[allTMMoves.indexOf(tmMove) + 1]) {
                Move thisMove = moves.get(tmMove);
                if (thisMove.power > 1 && tp.level * 3 > thisMove.power * thisMove.hitCount &&
                        this.random.nextDouble() < tmMoveProbability) {
                    moveSelectionPoolAtLevel.add(thisMove);
                } else if ((thisMove.power <= 1 && this.random.nextInt(100) < tp.level) ||
                        this.random.nextInt(200) < tp.level) {
                    moveSelectionPoolAtLevel.add(thisMove);
                }
            }
        }

        // Move Tutor Moves
        if (hasMoveTutors()) {
            boolean[] tutorCompat = allTutorCompat.get(getAltFormeOfPokemon(tp.pokemon, tp.forme));
            for (int tutorMove: allTutorMoves) {
                if (tutorCompat[allTutorMoves.indexOf(tutorMove) + 1]) {
                    Move thisMove = moves.get(tutorMove);
                    if (thisMove.power > 1 && tp.level * 3 > thisMove.power * thisMove.hitCount &&
                            this.random.nextDouble() < tutorMoveProbability) {
                        moveSelectionPoolAtLevel.add(thisMove);
                    } else if ((thisMove.power <= 1 && this.random.nextInt(100) < tp.level) ||
                            this.random.nextInt(200) < tp.level) {
                        moveSelectionPoolAtLevel.add(thisMove);
                    }
                }
            }
        }

        // Egg Moves
        if (!cyclicEvolutions) {
            Pokemon firstEvo;
            if (altFormesCanHaveDifferentEvolutions()) {
                firstEvo = getAltFormeOfPokemon(tp.pokemon, tp.forme);
            } else {
                firstEvo = tp.pokemon;
            }
            while (!firstEvo.evolutionsTo.isEmpty()) {
                firstEvo = firstEvo.evolutionsTo.get(0).from;
            }
            if (allEggMoves.get(firstEvo.number) != null) {
                moveSelectionPoolAtLevel.addAll(allEggMoves.get(firstEvo.number)
                        .stream()
                        .filter(egm -> this.random.nextDouble() < eggMoveProbability)
                        .map(moves::get)
                        .collect(Collectors.toList()));
            }
        }



        return moveSelectionPoolAtLevel.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public void pickTrainerMovesets(Settings settings) {
        boolean isCyclicEvolutions = settings.getEvolutionsMod() == Settings.EvolutionsMod.RANDOM_EVERY_LEVEL;
        boolean doubleBattleMode = settings.isDoubleBattleMode();

        List<Trainer> trainers = getTrainers();

        for (Trainer t: trainers) {
            t.setPokemonHaveCustomMoves(true);

            for (TrainerPokemon tp: t.pokemon) {
                tp.resetMoves = false;

                List<Move> movesAtLevel = getMoveSelectionPoolAtLevel(tp, isCyclicEvolutions);

                movesAtLevel = trimMoveList(tp, movesAtLevel, doubleBattleMode);

                if (movesAtLevel.isEmpty()) {
                    continue;
                }

                double trainerTypeModifier = 1;
                if (t.isImportant()) {
                    trainerTypeModifier = 1.5;
                } else if (t.isBoss()) {
                    trainerTypeModifier = 2;
                }
                double movePoolSizeModifier = movesAtLevel.size() / 10.0;
                double bonusModifier = trainerTypeModifier * movePoolSizeModifier;

                double atkSpatkRatioModifier = 0.75;
                double stabMoveBias = 0.25 * bonusModifier;
                double hardAbilityMoveBias = 1 * bonusModifier;
                double softAbilityMoveBias = 0.5 * bonusModifier;
                double statBias = 0.5 * bonusModifier;
                double softMoveBias = 0.25 * bonusModifier;
                double hardMoveBias = 1 * bonusModifier;
                double softMoveAntiBias = 0.5;

                // Add bias for STAB

                Pokemon pk = getAltFormeOfPokemon(tp.pokemon, tp.forme);

                List<Move> stabMoves = new ArrayList<>(movesAtLevel)
                        .stream()
                        .filter(mv -> mv.type == pk.primaryType && mv.category != MoveCategory.STATUS)
                        .collect(Collectors.toList());
                Collections.shuffle(stabMoves, this.random);

                for (int i = 0; i < stabMoveBias * stabMoves.size(); i++) {
                    int j = i % stabMoves.size();
                    movesAtLevel.add(stabMoves.get(j));
                }

                if (pk.secondaryType != null) {
                    stabMoves = new ArrayList<>(movesAtLevel)
                            .stream()
                            .filter(mv -> mv.type == pk.secondaryType && mv.category != MoveCategory.STATUS)
                            .collect(Collectors.toList());
                    Collections.shuffle(stabMoves, this.random);

                    for (int i = 0; i < stabMoveBias * stabMoves.size(); i++) {
                        int j = i % stabMoves.size();
                        movesAtLevel.add(stabMoves.get(j));
                    }
                }

                // Hard ability/move synergy

                List<Move> abilityMoveSynergyList = MoveSynergy.getHardAbilityMoveSynergy(
                        getAbilityForTrainerPokemon(tp),
                        pk.primaryType,
                        pk.secondaryType,
                        movesAtLevel,
                        generationOfPokemon(),
                        perfectAccuracy);
                Collections.shuffle(abilityMoveSynergyList, this.random);
                for (int i = 0; i < hardAbilityMoveBias * abilityMoveSynergyList.size(); i++) {
                    int j = i % abilityMoveSynergyList.size();
                    movesAtLevel.add(abilityMoveSynergyList.get(j));
                }

                // Soft ability/move synergy

                List<Move> softAbilityMoveSynergyList = MoveSynergy.getSoftAbilityMoveSynergy(
                        getAbilityForTrainerPokemon(tp),
                        movesAtLevel,
                        pk.primaryType,
                        pk.secondaryType);

                Collections.shuffle(softAbilityMoveSynergyList, this.random);
                for (int i = 0; i < softAbilityMoveBias * softAbilityMoveSynergyList.size(); i++) {
                    int j = i % softAbilityMoveSynergyList.size();
                    movesAtLevel.add(softAbilityMoveSynergyList.get(j));
                }

                // Soft ability/move anti-synergy

                List<Move> softAbilityMoveAntiSynergyList = MoveSynergy.getSoftAbilityMoveAntiSynergy(
                        getAbilityForTrainerPokemon(tp), movesAtLevel);
                List<Move> withoutSoftAntiSynergy = new ArrayList<>(movesAtLevel);
                for (Move mv: softAbilityMoveAntiSynergyList) {
                    withoutSoftAntiSynergy.remove(mv);
                }
                if (withoutSoftAntiSynergy.size() > 0) {
                    movesAtLevel = withoutSoftAntiSynergy;
                }

                List<Move> distinctMoveList = movesAtLevel.stream().distinct().collect(Collectors.toList());
                int movesLeft = distinctMoveList.size();

                if (movesLeft <= 4) {

                    for (int i = 0; i < 4; i++) {
                        if (i < movesLeft) {
                            tp.moves[i] = distinctMoveList.get(i).number;
                        } else {
                            tp.moves[i] = 0;
                        }
                    }
                    continue;
                }

                // Stat/move synergy

                List<Move> statSynergyList = MoveSynergy.getStatMoveSynergy(pk, movesAtLevel);
                Collections.shuffle(statSynergyList, this.random);
                for (int i = 0; i < statBias * statSynergyList.size(); i++) {
                    int j = i % statSynergyList.size();
                    movesAtLevel.add(statSynergyList.get(j));
                }

                // Stat/move anti-synergy

                List<Move> statAntiSynergyList = MoveSynergy.getStatMoveAntiSynergy(pk, movesAtLevel);
                List<Move> withoutStatAntiSynergy = new ArrayList<>(movesAtLevel);
                for (Move mv: statAntiSynergyList) {
                    withoutStatAntiSynergy.remove(mv);
                }
                if (withoutStatAntiSynergy.size() > 0) {
                    movesAtLevel = withoutStatAntiSynergy;
                }

                distinctMoveList = movesAtLevel.stream().distinct().collect(Collectors.toList());
                movesLeft = distinctMoveList.size();

                if (movesLeft <= 4) {

                    for (int i = 0; i < 4; i++) {
                        if (i < movesLeft) {
                            tp.moves[i] = distinctMoveList.get(i).number;
                        } else {
                            tp.moves[i] = 0;
                        }
                    }
                    continue;
                }

                // Add bias for atk/spatk ratio

                double atkSpatkRatio = (double)pk.attack / (double)pk.spatk;
                switch(getAbilityForTrainerPokemon(tp)) {
                    case Abilities.hugePower:
                    case Abilities.purePower:
                        atkSpatkRatio *= 2;
                        break;
                    case Abilities.hustle:
                    case Abilities.gorillaTactics:
                        atkSpatkRatio *= 1.5;
                        break;
                    case Abilities.moxie:
                        atkSpatkRatio *= 1.1;
                        break;
                    case Abilities.soulHeart:
                        atkSpatkRatio *= 0.9;
                        break;
                }

                List<Move> physicalMoves = new ArrayList<>(movesAtLevel)
                        .stream()
                        .filter(mv -> mv.category == MoveCategory.PHYSICAL)
                        .collect(Collectors.toList());
                List<Move> specialMoves = new ArrayList<>(movesAtLevel)
                        .stream()
                        .filter(mv -> mv.category == MoveCategory.SPECIAL)
                        .collect(Collectors.toList());

                if (atkSpatkRatio < 1 && specialMoves.size() > 0) {
                    atkSpatkRatio = 1 / atkSpatkRatio;
                    double acceptedRatio = atkSpatkRatioModifier * atkSpatkRatio;
                    int additionalMoves = (int)(physicalMoves.size() * acceptedRatio) - specialMoves.size();
                    for (int i = 0; i < additionalMoves; i++) {
                        Move mv = specialMoves.get(this.random.nextInt(specialMoves.size()));
                        movesAtLevel.add(mv);
                    }
                } else if (physicalMoves.size() > 0) {
                    double acceptedRatio = atkSpatkRatioModifier * atkSpatkRatio;
                    int additionalMoves = (int)(specialMoves.size() * acceptedRatio) - physicalMoves.size();
                    for (int i = 0; i < additionalMoves; i++) {
                        Move mv = physicalMoves.get(this.random.nextInt(physicalMoves.size()));
                        movesAtLevel.add(mv);
                    }
                }

                // Pick moves

                List<Move> pickedMoves = new ArrayList<>();

                for (int i = 1; i <= 4; i++) {
                    Move move;
                    List<Move> pickFrom;

                    if (i == 1) {
                        pickFrom = movesAtLevel
                                .stream()
                                .filter(mv -> mv.isGoodDamaging(perfectAccuracy))
                                .collect(Collectors.toList());
                        if (pickFrom.isEmpty()) {
                            pickFrom = movesAtLevel;
                        }
                    } else {
                        pickFrom = movesAtLevel;
                    }

                    if (i == 4) {
                        List<Move> requiresOtherMove = movesAtLevel
                                .stream()
                                .filter(mv -> GlobalConstants.requiresOtherMove.contains(mv.number))
                                .distinct()
                                .collect(Collectors.toList());

                        for (Move dependentMove: requiresOtherMove) {
                            boolean hasRequiredMove = false;
                            for (Move requiredMove: MoveSynergy.requiresOtherMove(dependentMove, movesAtLevel)) {
                                if (pickedMoves.contains(requiredMove)) {
                                    hasRequiredMove = true;
                                    break;
                                }
                            }
                            if (!hasRequiredMove) {
                                movesAtLevel.removeAll(Collections.singletonList(dependentMove));
                            }
                        }
                    }

                    move = pickFrom.get(this.random.nextInt(pickFrom.size()));
                    pickedMoves.add(move);

                    if (i == 4) {
                        break;
                    }

                    movesAtLevel.removeAll(Collections.singletonList(move));

                    movesAtLevel.removeAll(MoveSynergy.getHardMoveAntiSynergy(move, movesAtLevel));

                    distinctMoveList = movesAtLevel.stream().distinct().collect(Collectors.toList());
                    movesLeft = distinctMoveList.size();

                    if (movesLeft <= (4 - i)) {
                        pickedMoves.addAll(distinctMoveList);
                        break;
                    }

                    List<Move> hardMoveSynergyList = MoveSynergy.getMoveSynergy(
                            move,
                            movesAtLevel,
                            generationOfPokemon());
                    Collections.shuffle(hardMoveSynergyList, this.random);
                    for (int j = 0; j < hardMoveBias * hardMoveSynergyList.size(); j++) {
                        int k = j % hardMoveSynergyList.size();
                        movesAtLevel.add(hardMoveSynergyList.get(k));
                    }

                    List<Move> softMoveSynergyList = MoveSynergy.getSoftMoveSynergy(
                            move,
                            movesAtLevel,
                            generationOfPokemon(),
                            isEffectivenessUpdated());
                    Collections.shuffle(softMoveSynergyList, this.random);
                    for (int j = 0; j < softMoveBias * softMoveSynergyList.size(); j++) {
                        int k = j % softMoveSynergyList.size();
                        movesAtLevel.add(softMoveSynergyList.get(k));
                    }

                    List<Move> softMoveAntiSynergyList = MoveSynergy.getSoftMoveAntiSynergy(move, movesAtLevel);
                    Collections.shuffle(softMoveAntiSynergyList, this.random);
                    for (int j = 0; j < softMoveAntiBias * softMoveAntiSynergyList.size(); j++) {
                        distinctMoveList = movesAtLevel.stream().distinct().collect(Collectors.toList());
                        if (distinctMoveList.size() <= (4 - i)) {
                            break;
                        }
                        int k = j % softMoveAntiSynergyList.size();
                        movesAtLevel.remove(softMoveAntiSynergyList.get(k));
                    }

                    distinctMoveList = movesAtLevel.stream().distinct().collect(Collectors.toList());
                    movesLeft = distinctMoveList.size();

                    if (movesLeft <= (4 - i)) {
                        pickedMoves.addAll(distinctMoveList);
                        break;
                    }
                }

                int movesPicked = pickedMoves.size();

                for (int i = 0; i < 4; i++) {
                    if (i < movesPicked) {
                        tp.moves[i] = pickedMoves.get(i).number;
                    } else {
                        tp.moves[i] = 0;
                    }
                }
            }
        }
        setTrainers(trainers, false, false);
    }

    private List<Move> trimMoveList(TrainerPokemon tp, List<Move> movesAtLevel, boolean doubleBattleMode) {
        int movesLeft = movesAtLevel.size();

        if (movesLeft <= 4) {
            for (int i = 0; i < 4; i++) {
                if (i < movesLeft) {
                    tp.moves[i] = movesAtLevel.get(i).number;
                } else {
                    tp.moves[i] = 0;
                }
            }
            return new ArrayList<>();
        }

        movesAtLevel = movesAtLevel
                .stream()
                .filter(mv -> !GlobalConstants.uselessMoves.contains(mv.number) &&
                        (doubleBattleMode || !GlobalConstants.doubleBattleMoves.contains(mv.number)))
                .collect(Collectors.toList());

        movesLeft = movesAtLevel.size();

        if (movesLeft <= 4) {
            for (int i = 0; i < 4; i++) {
                if (i < movesLeft) {
                    tp.moves[i] = movesAtLevel.get(i).number;
                } else {
                    tp.moves[i] = 0;
                }
            }
            return new ArrayList<>();
        }

        List<Move> obsoletedMoves = getObsoleteMoves(movesAtLevel);

        // Remove obsoleted moves

        movesAtLevel.removeAll(obsoletedMoves);

        movesLeft = movesAtLevel.size();

        if (movesLeft <= 4) {
            for (int i = 0; i < 4; i++) {
                if (i < movesLeft) {
                    tp.moves[i] = movesAtLevel.get(i).number;
                } else {
                    tp.moves[i] = 0;
                }
            }
            return new ArrayList<>();
        }

        List<Move> requiresOtherMove = movesAtLevel
                .stream()
                .filter(mv -> GlobalConstants.requiresOtherMove.contains(mv.number))
                .collect(Collectors.toList());

        for (Move dependentMove: requiresOtherMove) {
            if (MoveSynergy.requiresOtherMove(dependentMove, movesAtLevel).isEmpty()) {
                movesAtLevel.remove(dependentMove);
            }
        }

        movesLeft = movesAtLevel.size();

        if (movesLeft <= 4) {
            for (int i = 0; i < 4; i++) {
                if (i < movesLeft) {
                    tp.moves[i] = movesAtLevel.get(i).number;
                } else {
                    tp.moves[i] = 0;
                }
            }
            return new ArrayList<>();
        }

        // Remove hard ability anti-synergy moves

        List<Move> withoutHardAntiSynergy = new ArrayList<>(movesAtLevel);
        withoutHardAntiSynergy.removeAll(MoveSynergy.getHardAbilityMoveAntiSynergy(
                getAbilityForTrainerPokemon(tp),
                movesAtLevel));

        if (withoutHardAntiSynergy.size() > 0) {
            movesAtLevel = withoutHardAntiSynergy;
        }

        movesLeft = movesAtLevel.size();

        if (movesLeft <= 4) {
            for (int i = 0; i < 4; i++) {
                if (i < movesLeft) {
                    tp.moves[i] = movesAtLevel.get(i).number;
                } else {
                    tp.moves[i] = 0;
                }
            }
            return new ArrayList<>();
        }
        return movesAtLevel;
    }

    private List<Move> getObsoleteMoves(List<Move> movesAtLevel) {
        List<Move> obsoletedMoves = new ArrayList<>();
        for (Move mv: movesAtLevel) {
            if (GlobalConstants.cannotObsoleteMoves.contains(mv.number)) {
                continue;
            }
            if (mv.power > 0) {
                List<Move> obsoleteThis = movesAtLevel
                        .stream()
                        .filter(mv2 -> !GlobalConstants.cannotBeObsoletedMoves.contains(mv2.number) &&
                                mv.type == mv2.type &&
                                ((((mv.statChangeMoveType == mv2.statChangeMoveType &&
                                        mv.statChanges[0].equals(mv2.statChanges[0])) ||
                                        (mv2.statChangeMoveType == StatChangeMoveType.NONE_OR_UNKNOWN &&
                                                mv.hasBeneficialStatChange())) &&
                                        mv.absorbPercent >= mv2.absorbPercent &&
                                        !mv.isChargeMove &&
                                        !mv.isRechargeMove) ||
                                mv2.power * mv2.hitCount <= 30) &&
                                mv.hitratio >= mv2.hitratio &&
                                mv.category == mv2.category &&
                                mv.priority >= mv2.priority &&
                                mv2.power > 0 &&
                                mv.power * mv.hitCount > mv2.power * mv2.hitCount)
                        .collect(Collectors.toList());
                for (Move obsoleted: obsoleteThis) {
                    //System.out.println(obsoleted.name + " obsoleted by " + mv.name);
                }
                obsoletedMoves.addAll(obsoleteThis);
            } else if (mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER ||
                    mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_TARGET) {
                List<Move> obsoleteThis = new ArrayList<>();
                List<Move.StatChange> statChanges1 = new ArrayList<>();
                for (Move.StatChange sc: mv.statChanges) {
                    if (sc.type != StatChangeType.NONE) {
                        statChanges1.add(sc);
                    }
                }
                for (Move mv2: movesAtLevel
                        .stream()
                        .filter(otherMv -> !otherMv.equals(mv) &&
                                otherMv.power <= 0 &&
                                otherMv.statChangeMoveType == mv.statChangeMoveType &&
                                (otherMv.statusType == mv.statusType ||
                                otherMv.statusType == StatusType.NONE))
                        .collect(Collectors.toList())) {
                    List<Move.StatChange> statChanges2 = new ArrayList<>();
                    for (Move.StatChange sc: mv2.statChanges) {
                        if (sc.type != StatChangeType.NONE) {
                            statChanges2.add(sc);
                        }
                    }
                    if (statChanges2.size() > statChanges1.size()) {
                        continue;
                    }
                    List<Move.StatChange> statChanges1Filtered = statChanges1
                            .stream()
                            .filter(sc -> !statChanges2.contains(sc))
                            .collect(Collectors.toList());
                    statChanges2.removeAll(statChanges1);
                    if (!statChanges1Filtered.isEmpty() && statChanges2.isEmpty()) {
                        if (!GlobalConstants.cannotBeObsoletedMoves.contains(mv2.number)) {
                            obsoleteThis.add(mv2);
                        }
                        continue;
                    }
                    if (statChanges1Filtered.isEmpty() && statChanges2.isEmpty()) {
                        continue;
                    }
                    boolean maybeBetter = false;
                    for (Move.StatChange sc1: statChanges1Filtered) {
                        boolean canStillBeBetter = false;
                        for (Move.StatChange sc2: statChanges2) {
                            if (sc1.type == sc2.type) {
                                canStillBeBetter = true;
                                if ((mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_USER && sc1.stages > sc2.stages) ||
                                        (mv.statChangeMoveType == StatChangeMoveType.NO_DAMAGE_TARGET && sc1.stages < sc2.stages)) {
                                    maybeBetter = true;
                                } else {
                                    canStillBeBetter = false;
                                }
                            }
                        }
                        if (!canStillBeBetter) {
                            maybeBetter = false;
                            break;
                        }
                    }
                    if (maybeBetter) {
                        if (!GlobalConstants.cannotBeObsoletedMoves.contains(mv2.number)) {
                            obsoleteThis.add(mv2);
                        }
                    }
                }
                for (Move obsoleted: obsoleteThis) {
                    //System.out.println(obsoleted.name + " obsoleted by " + mv.name);
                }
                obsoletedMoves.addAll(obsoleteThis);
            }
        }

        return obsoletedMoves.stream().distinct().collect(Collectors.toList());
    }

    private boolean trainerShouldNotGetBuffs(Trainer t) {
        return t.tag != null && (t.tag.startsWith("RIVAL1-") || t.tag.startsWith("FRIEND1-") || t.tag.endsWith("NOTSTRONG"));
    }

    public int getRandomAbilitySlot(Pokemon pokemon) {
        if (abilitiesPerPokemon() == 0) {
            return 0;
        }
        List<Integer> abilitiesList = Arrays.asList(pokemon.ability1, pokemon.ability2, pokemon.ability3);
        int slot = random.nextInt(this.abilitiesPerPokemon());
        while (abilitiesList.get(slot) == 0) {
            slot = random.nextInt(this.abilitiesPerPokemon());
        }
        return slot + 1;
    }

    public int getValidAbilitySlotFromOriginal(Pokemon pokemon, int originalAbilitySlot) {
        // This is used in cases where one Trainer Pokemon evolves into another. If the unevolved Pokemon
        // is using slot 2, but the evolved Pokemon doesn't actually have a second ability, then we
        // want the evolved Pokemon to use slot 1 for safety's sake.
        if (originalAbilitySlot == 2 && pokemon.ability2 == 0) {
            return 1;
        }
        return originalAbilitySlot;
    }

    // MOVE DATA
    // All randomizers don't touch move ID 165 (Struggle)
    // They also have other exclusions where necessary to stop things glitching.

    @Override
    public void randomizeMovePowers() {
        List<Move> moves = this.getMoves();
        for (Move mv : moves) {
            if (mv != null && mv.internalId != Moves.struggle && mv.power >= 10) {
                // "Generic" damaging move to randomize power
                if (random.nextInt(3) != 2) {
                    // "Regular" move
                    mv.power = random.nextInt(11) * 5 + 50; // 50 ... 100
                } else {
                    // "Extreme" move
                    mv.power = random.nextInt(27) * 5 + 20; // 20 ... 150
                }
                // Tiny chance for massive power jumps
                for (int i = 0; i < 2; i++) {
                    if (random.nextInt(100) == 0) {
                        mv.power += 50;
                    }
                }

                if (mv.hitCount != 1) {
                    // Divide randomized power by average hit count, round to
                    // nearest 5
                    mv.power = (int) (Math.round(mv.power / mv.hitCount / 5) * 5);
                    if (mv.power == 0) {
                        mv.power = 5;
                    }
                }
            }
        }
    }

    @Override
    public void randomizeMovePPs() {
        List<Move> moves = this.getMoves();
        for (Move mv : moves) {
            if (mv != null && mv.internalId != Moves.struggle) {
                if (random.nextInt(3) != 2) {
                    // "average" PP: 15-25
                    mv.pp = random.nextInt(3) * 5 + 15;
                } else {
                    // "extreme" PP: 5-40
                    mv.pp = random.nextInt(8) * 5 + 5;
                }
            }
        }
    }

    @Override
    public void randomizeMoveAccuracies() {
        List<Move> moves = this.getMoves();
        for (Move mv : moves) {
            if (mv != null && mv.internalId != Moves.struggle && mv.hitratio >= 5) {
                // "Sane" accuracy randomization
                // Broken into three tiers based on original accuracy
                // Designed to limit the chances of 100% accurate OHKO moves and
                // keep a decent base of 100% accurate regular moves.

                if (mv.hitratio <= 50) {
                    // lowest tier (acc <= 50)
                    // new accuracy = rand(20...50) inclusive
                    // with a 10% chance to increase by 50%
                    mv.hitratio = random.nextInt(7) * 5 + 20;
                    if (random.nextInt(10) == 0) {
                        mv.hitratio = (mv.hitratio * 3 / 2) / 5 * 5;
                    }
                } else if (mv.hitratio < 90) {
                    // middle tier (50 < acc < 90)
                    // count down from 100% to 20% in 5% increments with 20%
                    // chance to "stop" and use the current accuracy at each
                    // increment
                    // gives decent-but-not-100% accuracy most of the time
                    mv.hitratio = 100;
                    while (mv.hitratio > 20) {
                        if (random.nextInt(10) < 2) {
                            break;
                        }
                        mv.hitratio -= 5;
                    }
                } else {
                    // highest tier (90 <= acc <= 100)
                    // count down from 100% to 20% in 5% increments with 40%
                    // chance to "stop" and use the current accuracy at each
                    // increment
                    // gives high accuracy most of the time
                    mv.hitratio = 100;
                    while (mv.hitratio > 20) {
                        if (random.nextInt(10) < 4) {
                            break;
                        }
                        mv.hitratio -= 5;
                    }
                }
            }
        }
    }

    @Override
    public void randomizeMoveTypes() {
        List<Move> moves = this.getMoves();
        for (Move mv : moves) {
            if (mv != null && mv.internalId != Moves.struggle && mv.type != null) {
                mv.type = randomType();
            }
        }
    }

    @Override
    public void randomizeMoveCategory() {
        if (!this.hasPhysicalSpecialSplit()) {
            return;
        }
        List<Move> moves = this.getMoves();
        for (Move mv : moves) {
            if (mv != null && mv.internalId != Moves.struggle && mv.category != MoveCategory.STATUS) {
                if (random.nextInt(2) == 0) {
                    mv.category = (mv.category == MoveCategory.PHYSICAL) ? MoveCategory.SPECIAL : MoveCategory.PHYSICAL;
                }
            }
        }

    }

    @Override
    public void updateMoves(Settings settings) {
        int generation = settings.getUpdateMovesToGeneration();

        List<Move> moves = this.getMoves();

        if (generation >= 2 && generationOfPokemon() < 2) {
            // gen1
            // Karate Chop => FIGHTING (gen1)
            updateMoveType(moves, Moves.karateChop, Type.FIGHTING);
            // Gust => FLYING (gen1)
            updateMoveType(moves, Moves.gust, Type.FLYING);
            // Wing Attack => 60 power (gen1)
            updateMovePower(moves, Moves.wingAttack, 60);
            // Whirlwind => 100 accuracy (gen1)
            updateMoveAccuracy(moves, Moves.whirlwind, 100);
            // Sand Attack => GROUND (gen1)
            updateMoveType(moves, Moves.sandAttack, Type.GROUND);
            // Double-Edge => 120 power (gen1)
            updateMovePower(moves, Moves.doubleEdge, 120);
            // Move 44, Bite, becomes dark (but doesn't exist anyway)
            // Blizzard => 70% accuracy (gen1)
            updateMoveAccuracy(moves, Moves.blizzard, 70);
            // Rock Throw => 90% accuracy (gen1)
            updateMoveAccuracy(moves, Moves.rockThrow, 90);
            // Hypnosis => 60% accuracy (gen1)
            updateMoveAccuracy(moves, Moves.hypnosis, 60);
            // SelfDestruct => 200power (gen1)
            updateMovePower(moves, Moves.selfDestruct, 200);
            // Explosion => 250 power (gen1)
            updateMovePower(moves, Moves.explosion, 250);
            // Dig => 60 power (gen1)
            updateMovePower(moves, Moves.dig, 60);
        }

        if (generation >= 3 && generationOfPokemon() < 3) {
            // Razor Wind => 100% accuracy (gen1/2)
            updateMoveAccuracy(moves, Moves.razorWind, 100);
            // Move 67, Low Kick, has weight-based power in gen3+
            // Low Kick => 100% accuracy (gen1)
            updateMoveAccuracy(moves, Moves.lowKick, 100);
        }

        if (generation >= 4 && generationOfPokemon() < 4) {
            // Fly => 90 power (gen1/2/3)
            updateMovePower(moves, Moves.fly, 90);
            // Vine Whip => 15 pp (gen1/2/3)
            updateMovePP(moves, Moves.vineWhip, 15);
            // Absorb => 25pp (gen1/2/3)
            updateMovePP(moves, Moves.absorb, 25);
            // Mega Drain => 15pp (gen1/2/3)
            updateMovePP(moves, Moves.megaDrain, 15);
            // Dig => 80 power (gen1/2/3)
            updateMovePower(moves, Moves.dig, 80);
            // Recover => 10pp (gen1/2/3)
            updateMovePP(moves, Moves.recover, 10);
            // Flash => 100% acc (gen1/2/3)
            updateMoveAccuracy(moves, Moves.flash, 100);
            // Petal Dance => 90 power (gen1/2/3)
            updateMovePower(moves, Moves.petalDance, 90);
            // Disable => 100% accuracy (gen1-4)
            updateMoveAccuracy(moves, Moves.disable, 80);
            // Jump Kick => 85 power
            updateMovePower(moves, Moves.jumpKick, 85);
            // Hi Jump Kick => 100 power
            updateMovePower(moves, Moves.highJumpKick, 100);

            if (generationOfPokemon() >= 2) {
                // Zap Cannon => 120 power (gen2-3)
                updateMovePower(moves, Moves.zapCannon, 120);
                // Outrage => 120 power (gen2-3)
                updateMovePower(moves, Moves.outrage, 120);
                updateMovePP(moves, Moves.outrage, 10);
                // Giga Drain => 10pp (gen2-3)
                updateMovePP(moves, Moves.gigaDrain, 10);
                // Rock Smash => 40 power (gen2-3)
                updateMovePower(moves, Moves.rockSmash, 40);
            }

            if (generationOfPokemon() == 3) {
                // Stockpile => 20 pp
                updateMovePP(moves, Moves.stockpile, 20);
                // Dive => 80 power
                updateMovePower(moves, Moves.dive, 80);
                // Leaf Blade => 90 power
                updateMovePower(moves, Moves.leafBlade, 90);
            }
        }

        if (generation >= 5 && generationOfPokemon() < 5) {
            // Bind => 85% accuracy (gen1-4)
            updateMoveAccuracy(moves, Moves.bind, 85);
            // Jump Kick => 10 pp, 100 power (gen1-4)
            updateMovePP(moves, Moves.jumpKick, 10);
            updateMovePower(moves, Moves.jumpKick, 100);
            // Tackle => 50 power, 100% accuracy , gen1-4
            updateMovePower(moves, Moves.tackle, 50);
            updateMoveAccuracy(moves, Moves.tackle, 100);
            // Wrap => 90% accuracy (gen1-4)
            updateMoveAccuracy(moves, Moves.wrap, 90);
            // Thrash => 120 power, 10pp (gen1-4)
            updateMovePP(moves, Moves.thrash, 10);
            updateMovePower(moves, Moves.thrash, 120);
            // Disable => 100% accuracy (gen1-4)
            updateMoveAccuracy(moves, Moves.disable, 100);
            // Petal Dance => 120power, 10pp (gen1-4)
            updateMovePP(moves, Moves.petalDance, 10);
            updateMovePower(moves, Moves.petalDance, 120);
            // Fire Spin => 35 power, 85% acc (gen1-4)
            updateMoveAccuracy(moves, Moves.fireSpin, 85);
            updateMovePower(moves, Moves.fireSpin, 35);
            // Toxic => 90% accuracy (gen1-4)
            updateMoveAccuracy(moves, Moves.toxic, 90);
            // Clamp => 15pp, 85% acc (gen1-4)
            updateMoveAccuracy(moves, Moves.clamp, 85);
            updateMovePP(moves, Moves.clamp, 15);
            // HJKick => 130 power, 10pp (gen1-4)
            updateMovePP(moves, Moves.highJumpKick, 10);
            updateMovePower(moves, Moves.highJumpKick, 130);
            // Glare => 90% acc (gen1-4)
            updateMoveAccuracy(moves, Moves.glare, 90);
            // Poison Gas => 80% acc (gen1-4)
            updateMoveAccuracy(moves, Moves.poisonGas, 80);
            // Crabhammer => 90% acc (gen1-4)
            updateMoveAccuracy(moves, Moves.crabhammer, 90);

            if (generationOfPokemon() >= 2) {
                // Curse => GHOST (gen2-4)
                updateMoveType(moves, Moves.curse, Type.GHOST);
                // Cotton Spore => 100% acc (gen2-4)
                updateMoveAccuracy(moves, Moves.cottonSpore, 100);
                // Scary Face => 100% acc (gen2-4)
                updateMoveAccuracy(moves, Moves.scaryFace, 100);
                // Bone Rush => 90% acc (gen2-4)
                updateMoveAccuracy(moves, Moves.boneRush, 90);
                // Giga Drain => 75 power (gen2-4)
                updateMovePower(moves, Moves.gigaDrain, 75);
                // Fury Cutter => 20 power (gen2-4)
                updateMovePower(moves, Moves.furyCutter, 20);
                // Future Sight => 10 pp, 100 power, 100% acc (gen2-4)
                updateMovePP(moves, Moves.futureSight, 10);
                updateMovePower(moves, Moves.futureSight, 100);
                updateMoveAccuracy(moves, Moves.futureSight, 100);
                // Whirlpool => 35 pow, 85% acc (gen2-4)
                updateMovePower(moves, Moves.whirlpool, 35);
                updateMoveAccuracy(moves, Moves.whirlpool, 85);
            }

            if (generationOfPokemon() >= 3) {
                // Uproar => 90 power (gen3-4)
                updateMovePower(moves, Moves.uproar, 90);
                // Sand Tomb => 35 pow, 85% acc (gen3-4)
                updateMovePower(moves, Moves.sandTomb, 35);
                updateMoveAccuracy(moves, Moves.sandTomb, 85);
                // Bullet Seed => 25 power (gen3-4)
                updateMovePower(moves, Moves.bulletSeed, 25);
                // Icicle Spear => 25 power (gen3-4)
                updateMovePower(moves, Moves.icicleSpear, 25);
                // Covet => 60 power (gen3-4)
                updateMovePower(moves, Moves.covet, 60);
                // Rock Blast => 90% acc (gen3-4)
                updateMoveAccuracy(moves, Moves.rockBlast, 90);
                // Doom Desire => 140 pow, 100% acc, gen3-4
                updateMovePower(moves, Moves.doomDesire, 140);
                updateMoveAccuracy(moves, Moves.doomDesire, 100);
            }

            if (generationOfPokemon() == 4) {
                // Feint => 30 pow
                updateMovePower(moves, Moves.feint, 30);
                // Last Resort => 140 pow
                updateMovePower(moves, Moves.lastResort, 140);
                // Drain Punch => 10 pp, 75 pow
                updateMovePP(moves, Moves.drainPunch, 10);
                updateMovePower(moves, Moves.drainPunch, 75);
                // Magma Storm => 75% acc
                updateMoveAccuracy(moves, Moves.magmaStorm, 75);
            }
        }

        if (generation >= 6 && generationOfPokemon() < 6) {
            // gen 1
            // Swords Dance 20 PP
            updateMovePP(moves, Moves.swordsDance, 20);
            // Whirlwind can't miss
            updateMoveAccuracy(moves, Moves.whirlwind, perfectAccuracy);
            // Vine Whip 25 PP, 45 Power
            updateMovePP(moves, Moves.vineWhip, 25);
            updateMovePower(moves, Moves.vineWhip, 45);
            // Pin Missile 25 Power, 95% Accuracy
            updateMovePower(moves, Moves.pinMissile, 25);
            updateMoveAccuracy(moves, Moves.pinMissile, 95);
            // Flamethrower 90 Power
            updateMovePower(moves, Moves.flamethrower, 90);
            // Hydro Pump 110 Power
            updateMovePower(moves, Moves.hydroPump, 110);
            // Surf 90 Power
            updateMovePower(moves, Moves.surf, 90);
            // Ice Beam 90 Power
            updateMovePower(moves, Moves.iceBeam, 90);
            // Blizzard 110 Power
            updateMovePower(moves, Moves.blizzard, 110);
            // Growth 20 PP
            updateMovePP(moves, Moves.growth, 20);
            // Thunderbolt 90 Power
            updateMovePower(moves, Moves.thunderbolt, 90);
            // Thunder 110 Power
            updateMovePower(moves, Moves.thunder, 110);
            // Minimize 10 PP
            updateMovePP(moves, Moves.minimize, 10);
            // Barrier 20 PP
            updateMovePP(moves, Moves.barrier, 20);
            // Lick 30 Power
            updateMovePower(moves, Moves.lick, 30);
            // Smog 30 Power
            updateMovePower(moves, Moves.smog, 30);
            // Fire Blast 110 Power
            updateMovePower(moves, Moves.fireBlast, 110);
            // Skull Bash 10 PP, 130 Power
            updateMovePP(moves, Moves.skullBash, 10);
            updateMovePower(moves, Moves.skullBash, 130);
            // Glare 100% Accuracy
            updateMoveAccuracy(moves, Moves.glare, 100);
            // Poison Gas 90% Accuracy
            updateMoveAccuracy(moves, Moves.poisonGas, 90);
            // Bubble 40 Power
            updateMovePower(moves, Moves.bubble, 40);
            // Psywave 100% Accuracy
            updateMoveAccuracy(moves, Moves.psywave, 100);
            // Acid Armor 20 PP
            updateMovePP(moves, Moves.acidArmor, 20);
            // Crabhammer 100 Power
            updateMovePower(moves, Moves.crabhammer, 100);

            if (generationOfPokemon() >= 2) {
                // Thief 25 PP, 60 Power
                updateMovePP(moves, Moves.thief, 25);
                updateMovePower(moves, Moves.thief, 60);
                // Snore 50 Power
                updateMovePower(moves, Moves.snore, 50);
                // Fury Cutter 40 Power
                updateMovePower(moves, Moves.furyCutter, 40);
                // Future Sight 120 Power
                updateMovePower(moves, Moves.futureSight, 120);
            }

            if (generationOfPokemon() >= 3) {
                // Heat Wave 95 Power
                updateMovePower(moves, Moves.heatWave, 95);
                // Will-o-Wisp 85% Accuracy
                updateMoveAccuracy(moves, Moves.willOWisp, 85);
                // Smellingsalt 70 Power
                updateMovePower(moves, Moves.smellingSalts, 70);
                // Knock off 65 Power
                updateMovePower(moves, Moves.knockOff, 65);
                // Meteor Mash 90 Power, 90% Accuracy
                updateMovePower(moves, Moves.meteorMash, 90);
                updateMoveAccuracy(moves, Moves.meteorMash, 90);
                // Air Cutter 60 Power
                updateMovePower(moves, Moves.airCutter, 60);
                // Overheat 130 Power
                updateMovePower(moves, Moves.overheat, 130);
                // Rock Tomb 15 PP, 60 Power, 95% Accuracy
                updateMovePP(moves, Moves.rockTomb, 15);
                updateMovePower(moves, Moves.rockTomb, 60);
                updateMoveAccuracy(moves, Moves.rockTomb, 95);
                // Extrasensory 20 PP
                updateMovePP(moves, Moves.extrasensory, 20);
                // Muddy Water 90 Power
                updateMovePower(moves, Moves.muddyWater, 90);
                // Covet 25 PP
                updateMovePP(moves, Moves.covet, 25);
            }

            if (generationOfPokemon() >= 4) {
                // Wake-Up Slap 70 Power
                updateMovePower(moves, Moves.wakeUpSlap, 70);
                // Tailwind 15 PP
                updateMovePP(moves, Moves.tailwind, 15);
                // Assurance 60 Power
                updateMovePower(moves, Moves.assurance, 60);
                // Psycho Shift 100% Accuracy
                updateMoveAccuracy(moves, Moves.psychoShift, 100);
                // Aura Sphere 80 Power
                updateMovePower(moves, Moves.auraSphere, 80);
                // Air Slash 15 PP
                updateMovePP(moves, Moves.airSlash, 15);
                // Dragon Pulse 85 Power
                updateMovePower(moves, Moves.dragonPulse, 85);
                // Power Gem 80 Power
                updateMovePower(moves, Moves.powerGem, 80);
                // Energy Ball 90 Power
                updateMovePower(moves, Moves.energyBall, 90);
                // Draco Meteor 130 Power
                updateMovePower(moves, Moves.dracoMeteor, 130);
                // Leaf Storm 130 Power
                updateMovePower(moves, Moves.leafStorm, 130);
                // Gunk Shot 80% Accuracy
                updateMoveAccuracy(moves, Moves.gunkShot, 80);
                // Chatter 65 Power
                updateMovePower(moves, Moves.chatter, 65);
                // Magma Storm 100 Power
                updateMovePower(moves, Moves.magmaStorm, 100);
            }

            if (generationOfPokemon() == 5) {
                // Synchronoise 120 Power
                updateMovePower(moves, Moves.synchronoise, 120);
                // Low Sweep 65 Power
                updateMovePower(moves, Moves.lowSweep, 65);
                // Hex 65 Power
                updateMovePower(moves, Moves.hex, 65);
                // Incinerate 60 Power
                updateMovePower(moves, Moves.incinerate, 60);
                // Pledges 80 Power
                updateMovePower(moves, Moves.waterPledge, 80);
                updateMovePower(moves, Moves.firePledge, 80);
                updateMovePower(moves, Moves.grassPledge, 80);
                // Struggle Bug 50 Power
                updateMovePower(moves, Moves.struggleBug, 50);
                // Frost Breath and Storm Throw 45 Power
                // Crits are 2x in these games, so we need to multiply BP by 3/4
                // Storm Throw was also updated to have a base BP of 60
                updateMovePower(moves, Moves.frostBreath, 45);
                updateMovePower(moves, Moves.stormThrow, 45);
                // Sacred Sword 15 PP
                updateMovePP(moves, Moves.sacredSword, 15);
                // Hurricane 110 Power
                updateMovePower(moves, Moves.hurricane, 110);
                // Techno Blast 120 Power
                updateMovePower(moves, Moves.technoBlast, 120);
            }
        }

        if (generation >= 7 && generationOfPokemon() < 7) {
            // Leech Life 80 Power, 10 PP
            updateMovePower(moves, Moves.leechLife, 80);
            updateMovePP(moves, Moves.leechLife, 10);
            // Submission 20 PP
            updateMovePP(moves, Moves.submission, 20);
            // Tackle 40 Power
            updateMovePower(moves, Moves.tackle, 40);
            // Thunder Wave 90% Accuracy
            updateMoveAccuracy(moves, Moves.thunderWave, 90);

            if (generationOfPokemon() >= 2) {
                // Swagger 85% Accuracy
                updateMoveAccuracy(moves, Moves.swagger, 85);
            }

            if (generationOfPokemon() >= 3) {
                // Knock Off 20 PP
                updateMovePP(moves, Moves.knockOff, 20);
            }

            if (generationOfPokemon() >= 4) {
                // Dark Void 50% Accuracy
                updateMoveAccuracy(moves, Moves.darkVoid, 50);
                // Sucker Punch 70 Power
                updateMovePower(moves, Moves.suckerPunch, 70);
            }

            if (generationOfPokemon() == 6) {
                // Aromatic Mist can't miss
                updateMoveAccuracy(moves, Moves.aromaticMist, perfectAccuracy);
                // Fell Stinger 50 Power
                updateMovePower(moves, Moves.fellStinger, 50);
                // Flying Press 100 Power
                updateMovePower(moves, Moves.flyingPress, 100);
                // Mat Block 10 PP
                updateMovePP(moves, Moves.matBlock, 10);
                // Mystical Fire 75 Power
                updateMovePower(moves, Moves.mysticalFire, 75);
                // Parabolic Charge 65 Power
                updateMovePower(moves, Moves.parabolicCharge, 65);
                // Topsy-Turvy can't miss
                updateMoveAccuracy(moves, Moves.topsyTurvy, perfectAccuracy);
                // Water Shuriken Special
                updateMoveCategory(moves, Moves.waterShuriken, MoveCategory.SPECIAL);
            }
        }

        if (generation >= 8 && generationOfPokemon() < 8) {
            if (generationOfPokemon() >= 2) {
                // Rapid Spin 50 Power
                updateMovePower(moves, Moves.rapidSpin, 50);
            }

            if (generationOfPokemon() == 7) {
                // Multi-Attack 120 Power
                updateMovePower(moves, Moves.multiAttack, 120);
            }
        }

        if (generation >= 9 && generationOfPokemon() < 9) {
            // Gen 1
            // Recover 5 PP
            updateMovePP(moves, Moves.recover, 5);
            // Soft-Boiled 5 PP
            updateMovePP(moves, Moves.softBoiled, 5);
            // Rest 5 PP
            updateMovePP(moves, Moves.rest, 5);

            if (generationOfPokemon() >= 2) {
                // Milk Drink 5 PP
                updateMovePP(moves, Moves.milkDrink, 5);
            }

            if (generationOfPokemon() >= 3) {
                // Luster Purge 95 power
                updateMovePower(moves, Moves.lusterPurge, 95);
                // Mist Ball 95 power
                updateMovePower(moves, Moves.mistBall, 95);
                // Slack Off 5 PP
                updateMovePP(moves, Moves.slackOff, 5);
            }

            if (generationOfPokemon() >= 4) {
                // Roost 5 PP
                updateMovePP(moves, Moves.roost, 5);
            }
            
            if (generationOfPokemon() >= 7) {
                // Shore Up 5 PP
                updateMovePP(moves, Moves.shoreUp, 5);
            }

            if (generationOfPokemon() >= 8) {
                // Grassy Glide 55 Power
                updateMovePower(moves, Moves.grassyGlide, 55);
                // Wicked Blow 75 Power
                updateMovePower(moves, Moves.wickedBlow, 75);
                // Glacial Lance 120 Power
                updateMovePower(moves, Moves.glacialLance, 120);
            }
        }
    }

    private Map<Integer, boolean[]> moveUpdates;

    @Override
    public void initMoveUpdates() {
        moveUpdates = new TreeMap<>();
    }

    @Override
    public Map<Integer, boolean[]> getMoveUpdates() {
        return moveUpdates;
    }

    @Override
    public void randomizeMovesLearnt(Settings settings) {
        boolean typeThemed = settings.getMovesetsMod() == Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE;
        boolean noBroken = settings.isBlockBrokenMovesetMoves();
        boolean forceStartingMoves = supportsFourStartingMoves() && settings.isStartWithGuaranteedMoves();
        int forceStartingMoveCount = settings.getGuaranteedMoveCount();
        double goodDamagingPercentage =
                settings.isMovesetsForceGoodDamaging() ? settings.getMovesetsGoodDamagingPercent() / 100.0 : 0;
        boolean evolutionMovesForAll = settings.isEvolutionMovesForAll();

        // Get current sets
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();

        // Build sets of moves
        List<Move> validMoves = new ArrayList<>();
        List<Move> validDamagingMoves = new ArrayList<>();
        Map<Type, List<Move>> validTypeMoves = new HashMap<>();
        Map<Type, List<Move>> validTypeDamagingMoves = new HashMap<>();
        createSetsOfMoves(noBroken, validMoves, validDamagingMoves, validTypeMoves, validTypeDamagingMoves);

        for (Integer pkmnNum : movesets.keySet()) {
            List<Integer> learnt = new ArrayList<>();
            List<MoveLearnt> moves = movesets.get(pkmnNum);
            int lv1AttackingMove = 0;
            Pokemon pkmn = findPokemonInPoolWithSpeciesID(mainPokemonListInclFormes, pkmnNum);
            if (pkmn == null) {
                continue;
            }

            double atkSpAtkRatio = pkmn.getAttackSpecialAttackRatio();

            // 4 starting moves?
            if (forceStartingMoves) {
                int lv1count = 0;
                for (MoveLearnt ml : moves) {
                    if (ml.level == 1) {
                        lv1count++;
                    }
                }
                if (lv1count < forceStartingMoveCount) {
                    for (int i = 0; i < forceStartingMoveCount - lv1count; i++) {
                        MoveLearnt fakeLv1 = new MoveLearnt();
                        fakeLv1.level = 1;
                        fakeLv1.move = 0;
                        moves.add(0, fakeLv1);
                    }
                }
            }

            if (evolutionMovesForAll) {
                if (moves.get(0).level != 0) {
                    MoveLearnt fakeEvoMove = new MoveLearnt();
                    fakeEvoMove.level = 0;
                    fakeEvoMove.move = 0;
                    moves.add(0, fakeEvoMove);
                }
            }

            if (pkmn.actuallyCosmetic) {
                for (int i = 0; i < moves.size(); i++) {
                    moves.get(i).move = movesets.get(pkmn.baseForme.number).get(i).move;
                }
                continue;
            }

            // Find last lv1 move
            // lv1index ends up as the index of the first non-lv1 move
            int lv1index = moves.get(0).level == 1 ? 0 : 1; // Evolution move handling (level 0 = evo move)
            while (lv1index < moves.size() && moves.get(lv1index).level == 1) {
                lv1index++;
            }

            // last lv1 move is 1 before lv1index
            if (lv1index != 0) {
                lv1index--;
            }

            // Force a certain amount of good damaging moves depending on the percentage
            int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * moves.size());

            // Replace moves as needed
            for (int i = 0; i < moves.size(); i++) {
                // should this move be forced damaging?
                boolean attemptDamaging = i == lv1index || goodDamagingLeft > 0;

                // type themed?
                Type typeOfMove = null;
                if (typeThemed) {
                    double picked = random.nextDouble();
                    if ((pkmn.primaryType == Type.NORMAL && pkmn.secondaryType != null) ||
                            (pkmn.secondaryType == Type.NORMAL)) {

                        Type otherType = pkmn.primaryType == Type.NORMAL ? pkmn.secondaryType : pkmn.primaryType;

                        // Normal/OTHER: 10% normal, 30% other, 60% random
                        if (picked < 0.1) {
                            typeOfMove = Type.NORMAL;
                        } else if (picked < 0.4) {
                            typeOfMove = otherType;
                        }
                        // else random
                    } else if (pkmn.secondaryType != null) {
                        // Primary/Secondary: 20% primary, 20% secondary, 60% random
                        if (picked < 0.2) {
                            typeOfMove = pkmn.primaryType;
                        } else if (picked < 0.4) {
                            typeOfMove = pkmn.secondaryType;
                        }
                        // else random
                    } else {
                        // Primary/None: 40% primary, 60% random
                        if (picked < 0.4) {
                            typeOfMove = pkmn.primaryType;
                        }
                        // else random
                    }
                }

                // select a list to pick a move from that has at least one free
                List<Move> pickList = validMoves;
                if (attemptDamaging) {
                    if (typeOfMove != null) {
                        if (validTypeDamagingMoves.containsKey(typeOfMove)
                                && checkForUnusedMove(validTypeDamagingMoves.get(typeOfMove), learnt)) {
                            pickList = validTypeDamagingMoves.get(typeOfMove);
                        } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                            pickList = validDamagingMoves;
                        }
                    } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                        pickList = validDamagingMoves;
                    }
                    MoveCategory forcedCategory = random.nextDouble() < atkSpAtkRatio ? MoveCategory.PHYSICAL : MoveCategory.SPECIAL;
                    List<Move> filteredList = pickList.stream().filter(mv -> mv.category == forcedCategory).collect(Collectors.toList());
                    if (!filteredList.isEmpty() && checkForUnusedMove(filteredList, learnt)) {
                        pickList = filteredList;
                    }
                } else if (typeOfMove != null) {
                    if (validTypeMoves.containsKey(typeOfMove)
                            && checkForUnusedMove(validTypeMoves.get(typeOfMove), learnt)) {
                        pickList = validTypeMoves.get(typeOfMove);
                    }
                }

                // now pick a move until we get a valid one
                Move mv = pickList.get(random.nextInt(pickList.size()));
                while (learnt.contains(mv.number)) {
                    mv = pickList.get(random.nextInt(pickList.size()));
                }

                if (i == lv1index) {
                    lv1AttackingMove = mv.number;
                } else {
                    goodDamagingLeft--;
                }
                learnt.add(mv.number);

            }

            Collections.shuffle(learnt, random);
            if (learnt.get(lv1index) != lv1AttackingMove) {
                for (int i = 0; i < learnt.size(); i++) {
                    if (learnt.get(i) == lv1AttackingMove) {
                        learnt.set(i, learnt.get(lv1index));
                        learnt.set(lv1index, lv1AttackingMove);
                        break;
                    }
                }
            }

            // write all moves for the pokemon
            for (int i = 0; i < learnt.size(); i++) {
                moves.get(i).move = learnt.get(i);
                if (i == lv1index) {
                    // just in case, set this to lv1
                    moves.get(i).level = 1;
                }
            }
        }
        // cases for certain alt forms
        Map<Integer, List<MoveLearnt>> ms = movesets;
        copyMoveset(ms, Species.venusaur, Species.venusaurMega);
        copyMoveset(ms, Species.charizard, Species.charizardMegaX);
        copyMoveset(ms, Species.charizard, Species.charizardMegaY);
        copyMoveset(ms, Species.blastoise, Species.blastoiseMega);
        copyMoveset(ms, Species.beedrill, Species.beedrillMega);
        copyMoveset(ms, Species.pidgeot, Species.pidgeotMega);
        copyMoveset(ms, Species.alakazam, Species.alakazamMega);
        copyMoveset(ms, Species.slowbro, Species.slowbroMega);
        copyMoveset(ms, Species.gengar, Species.gengarMega);
        copyMoveset(ms, Species.kangaskhan, Species.kangaskhanMega);
        copyMoveset(ms, Species.pinsir, Species.pinsirMega);
        copyMoveset(ms, Species.gyarados, Species.gyaradosMega);
        copyMoveset(ms, Species.aerodactyl, Species.aerodactylMega);
        copyMoveset(ms, Species.mewtwo, Species.mewtwoMegaX);
        copyMoveset(ms, Species.mewtwo, Species.mewtwoMegaY);
        copyMoveset(ms, Species.ampharos, Species.ampharosMega);
        copyMoveset(ms, Species.steelix, Species.steelixMega);
        copyMoveset(ms, Species.scizor, Species.scizorMega);
        copyMoveset(ms, Species.heracross, Species.heracrossMega);
        copyMoveset(ms, Species.houndoom, Species.houndoomMega);
        copyMoveset(ms, Species.tyranitar, Species.tyranitarMega);
        copyMoveset(ms, Species.sceptile, Species.sceptileMega);
        copyMoveset(ms, Species.blaziken, Species.blazikenMega);
        copyMoveset(ms, Species.swampert, Species.swampertMega);
        copyMoveset(ms, Species.gardevoir, Species.gardevoirMega);
        copyMoveset(ms, Species.sableye, Species.sableyeMega);
        copyMoveset(ms, Species.mawile, Species.mawileMega);
        copyMoveset(ms, Species.aggron, Species.aggronMega);
        copyMoveset(ms, Species.medicham, Species.medichamMega);
        copyMoveset(ms, Species.manectric, Species.manectricMega);
        copyMoveset(ms, Species.sharpedo, Species.sharpedoMega);
        copyMoveset(ms, Species.camerupt, Species.cameruptMega);
        copyMoveset(ms, Species.altaria, Species.altariaMega);
        copyMoveset(ms, Species.banette, Species.banetteMega);
        copyMoveset(ms, Species.absol, Species.absolMega);
        copyMoveset(ms, Species.glalie, Species.glalieMega);
        copyMoveset(ms, Species.salamence, Species.salamenceMega);
        copyMoveset(ms, Species.metagross, Species.metagrossMega);
        copyMoveset(ms, Species.latias, Species.latiasMega);
        copyMoveset(ms, Species.latios, Species.latiosMega);
        copyMoveset(ms, Species.rayquaza, Species.rayquazaMega);
        copyMoveset(ms, Species.lopunny, Species.lopunnyMega);
        copyMoveset(ms, Species.garchomp, Species.garchompMega);
        copyMoveset(ms, Species.lucario, Species.lucarioMega);
        copyMoveset(ms, Species.abomasnow, Species.abomasnowMega);
        copyMoveset(ms, Species.gallade, Species.galladeMega);
        copyMoveset(ms, Species.audino, Species.audinoMega);
        copyMoveset(ms, Species.diancie, Species.diancieMega);
        copyMoveset(ms, Species.kyogre, Species.kyogreP);
        copyMoveset(ms, Species.groudon, Species.groudonP);
        copyMoveset(ms, Species.clefable, Species.clefableMega);
        copyMoveset(ms, Species.victreebel, Species.victreebelMega);
        copyMoveset(ms, Species.starmie, Species.starmieMega);
        copyMoveset(ms, Species.dragonite, Species.dragoniteMega);
        copyMoveset(ms, Species.meganium, Species.meganiumMega);
        copyMoveset(ms, Species.feraligatr, Species.feraligatrMega);
        copyMoveset(ms, Species.skarmory, Species.skarmoryMega);
        copyMoveset(ms, Species.froslass, Species.froslassMega);
        copyMoveset(ms, Species.emboar, Species.emboarMega);
        copyMoveset(ms, Species.excadrill, Species.excadrillMega);
        copyMoveset(ms, Species.scolipede, Species.scolipedeMega);
        copyMoveset(ms, Species.scrafty, Species.scraftyMega);
        copyMoveset(ms, Species.eelektross, Species.eelektrossMega);
        copyMoveset(ms, Species.chandelure, Species.chandelureMega);
        copyMoveset(ms, Species.chesnaught, Species.chesnaughtMega);
        copyMoveset(ms, Species.delphox, Species.delphoxMega);
        copyMoveset(ms, Species.greninja, Species.greninjaMega);
        copyMoveset(ms, Species.pyroar, Species.pyroarMega);
        copyMoveset(ms, Species.floetteE, Species.floetteMega);
        copyMoveset(ms, Species.malamar, Species.malamarMega);
        copyMoveset(ms, Species.barbaracle, Species.barbaracleMega);
        copyMoveset(ms, Species.dragalge, Species.dragalgeMega);
        copyMoveset(ms, Species.hawlucha, Species.hawluchaMega);
        copyMoveset(ms, Species.zygardeC, Species.zygardeMega);
        copyMoveset(ms, Species.drampa, Species.drampaMega);
        copyMoveset(ms, Species.falinks, Species.falinksMega);
        copyMoveset(ms, Species.raichu, Species.raichuMegaX);
        copyMoveset(ms, Species.raichu, Species.raichuMegaY);
        copyMoveset(ms, Species.chimecho, Species.chimechoMega);
        copyMoveset(ms, Species.absol, Species.absolMegaZ);
        copyMoveset(ms, Species.staraptor, Species.staraptorMega);
        copyMoveset(ms, Species.garchomp, Species.garchompMegaZ);
        copyMoveset(ms, Species.lucario, Species.lucarioMegaZ);
        copyMoveset(ms, Species.heatran, Species.heatranMega);
        copyMoveset(ms, Species.darkrai, Species.darkraiMega);
        copyMoveset(ms, Species.golurk, Species.golurkMega);
        copyMoveset(ms, Species.meowstic, Species.meowsticMega);
        copyMoveset(ms, Species.meowsticF, Species.meowsticFMega);
        copyMoveset(ms, Species.crabominable, Species.crabominableMega);
        copyMoveset(ms, Species.golisopod, Species.golisopodMega);
        copyMoveset(ms, Species.magearna, Species.magearnaMega);
        copyMoveset(ms, Species.zeraora, Species.zeraoraMega);
        copyMoveset(ms, Species.scovillain, Species.scovillainMega);
        copyMoveset(ms, Species.glimmora, Species.glimmoraMega);
        copyMoveset(ms, Species.tatsugiri, Species.tatsugiriMega);
        copyMoveset(ms, Species.baxcalibur, Species.baxcaliburMega);

        copyMoveset(ms, Species.pikachu, Species.pikachuCap);
        copyMoveset(ms, Species.pichu, Species.pichuSpiky);
        copyMoveset(ms, Species.castform, Species.castformF);
        copyMoveset(ms, Species.castform, Species.castformW);
        copyMoveset(ms, Species.castform, Species.castformI);
        copyMoveset(ms, Species.burmy, Species.burmyS);
        copyMoveset(ms, Species.burmy, Species.burmyT);
        copyMoveset(ms, Species.cherrim, Species.cherrimS);
        copyMoveset(ms, Species.rotom, Species.rotomH);
        copyMoveset(ms, Species.rotom, Species.rotomW);
        copyMoveset(ms, Species.rotom, Species.rotomFr);
        copyMoveset(ms, Species.rotom, Species.rotomFa);
        copyMoveset(ms, Species.rotom, Species.rotomM);
        copyMoveset(ms, Species.dialga, Species.dialgaO);
        copyMoveset(ms, Species.palkia, Species.palkiaO);
        copyMoveset(ms, Species.giratina, Species.giratinaO);
        copyMoveset(ms, Species.basculin, Species.basculinB);
        copyMoveset(ms, Species.tornadus, Species.tornadusT);
        copyMoveset(ms, Species.thundurus, Species.thundurusT);
        copyMoveset(ms, Species.landorus, Species.landorusT);
        copyMoveset(ms, Species.meloetta, Species.meloettaP);
        copyMoveset(ms, Species.greninja, Species.greninjaB);
        copyMoveset(ms, Species.greninja, Species.greninjaA);
        copyMoveset(ms, Species.aegislash, Species.aegislashB);
        copyMoveset(ms, Species.pumpkaboo, Species.pumpkabooSmall);
        copyMoveset(ms, Species.pumpkaboo, Species.pumpkabooLarge);
        copyMoveset(ms, Species.pumpkaboo, Species.pumpkabooSuper);
        copyMoveset(ms, Species.gourgeist, Species.gourgeistSmall);
        copyMoveset(ms, Species.gourgeist, Species.gourgeistLarge);
        copyMoveset(ms, Species.gourgeist, Species.gourgeistSuper);
        copyMoveset(ms, Species.zygarde, Species.zygarde10);
        copyMoveset(ms, Species.zygarde, Species.zygardeC);
        copyMoveset(ms, Species.oricorio, Species.oricorioE);
        copyMoveset(ms, Species.oricorio, Species.oricorioP);
        copyMoveset(ms, Species.oricorio, Species.oricorioG);
        copyMoveset(ms, Species.wishiwashi, Species.wishiwashiS);
        copyMoveset(ms, Species.minior, Species.miniorC);
        copyMoveset(ms, Species.necrozma, Species.necrozmaDM);
        copyMoveset(ms, Species.necrozma, Species.necrozmaDW);
        copyMoveset(ms, Species.necrozma, Species.necrozmaU);
        copyMoveset(ms, Species.eiscue, Species.eiscueN);
        copyMoveset(ms, Species.morpeko, Species.morpekoH);
        copyMoveset(ms, Species.zacian, Species.zacianC);
        copyMoveset(ms, Species.zamazenta, Species.zamazentaC);
        copyMoveset(ms, Species.eternatus, Species.eternatusE);
        copyMoveset(ms, Species.basculegion, Species.basculegionF);
        copyMoveset(ms, Species.enamorus, Species.enamorusT);
        copyMoveset(ms, Species.squawkabilly, Species.squawkabillyW);
        copyMoveset(ms, Species.palafin, Species.palafinH);
        copyMoveset(ms, Species.gimmighoul, Species.gimmighoulR);
        copyMoveset(ms, Species.ogerpon, Species.ogerponF);
        copyMoveset(ms, Species.ogerpon, Species.ogerponR);
        copyMoveset(ms, Species.ogerpon, Species.ogerponW);
        copyMoveset(ms, Species.terapagos, Species.terapagosT);
        copyMoveset(ms, Species.terapagos, Species.terapagosS);

        // Done, save
        this.setMovesLearnt(movesets);

    }

    /**
     * Deep copies the moveset from a source species to a target species in the map.
     * @param movesets The map of species to movesets.
     * @param sourceSpecies The species to copy from.
     * @param targetSpecies The species to copy to.
     */
    private void copyMoveset(Map<Integer, List<MoveLearnt>> movesets, int sourceSpecies, int targetSpecies) {
        List<MoveLearnt> baseMoves = movesets.get(sourceSpecies);
        List<MoveLearnt> copy = new ArrayList<>();
        for (MoveLearnt ml : baseMoves) {
            copy.add(new MoveLearnt(ml));
        }
        movesets.put(targetSpecies, copy);
    }

    @Override
    public void randomizeEggMoves(Settings settings) {
        boolean typeThemed = settings.getMovesetsMod() == Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE;
        boolean noBroken = settings.isBlockBrokenMovesetMoves();
        double goodDamagingPercentage =
                settings.isMovesetsForceGoodDamaging() ? settings.getMovesetsGoodDamagingPercent() / 100.0 : 0;

        // Get current sets
        Map<Integer, List<Integer>> movesets = this.getEggMoves();

        // Build sets of moves
        List<Move> validMoves = new ArrayList<>();
        List<Move> validDamagingMoves = new ArrayList<>();
        Map<Type, List<Move>> validTypeMoves = new HashMap<>();
        Map<Type, List<Move>> validTypeDamagingMoves = new HashMap<>();
        createSetsOfMoves(noBroken, validMoves, validDamagingMoves, validTypeMoves, validTypeDamagingMoves);

        for (Integer pkmnNum : movesets.keySet()) {
            List<Integer> learnt = new ArrayList<>();
            List<Integer> moves = movesets.get(pkmnNum);
            Pokemon pkmn = findPokemonInPoolWithSpeciesID(mainPokemonListInclFormes, pkmnNum);
            if (pkmn == null) {
                continue;
            }

            double atkSpAtkRatio = pkmn.getAttackSpecialAttackRatio();

            if (pkmn.actuallyCosmetic) {
                for (int i = 0; i < moves.size(); i++) {
                    moves.set(i, movesets.get(pkmn.baseForme.number).get(i));
                }
                continue;
            }

            // Force a certain amount of good damaging moves depending on the percentage
            int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * moves.size());

            // Replace moves as needed
            for (int i = 0; i < moves.size(); i++) {
                // should this move be forced damaging?
                boolean attemptDamaging = goodDamagingLeft > 0;

                // type themed?
                Type typeOfMove = null;
                if (typeThemed) {
                    double picked = random.nextDouble();
                    if ((pkmn.primaryType == Type.NORMAL && pkmn.secondaryType != null) ||
                            (pkmn.secondaryType == Type.NORMAL)) {

                        Type otherType = pkmn.primaryType == Type.NORMAL ? pkmn.secondaryType : pkmn.primaryType;

                        // Normal/OTHER: 10% normal, 30% other, 60% random
                        if (picked < 0.1) {
                            typeOfMove = Type.NORMAL;
                        } else if (picked < 0.4) {
                            typeOfMove = otherType;
                        }
                        // else random
                    } else if (pkmn.secondaryType != null) {
                        // Primary/Secondary: 20% primary, 20% secondary, 60% random
                        if (picked < 0.2) {
                            typeOfMove = pkmn.primaryType;
                        } else if (picked < 0.4) {
                            typeOfMove = pkmn.secondaryType;
                        }
                        // else random
                    } else {
                        // Primary/None: 40% primary, 60% random
                        if (picked < 0.4) {
                            typeOfMove = pkmn.primaryType;
                        }
                        // else random
                    }
                }

                // select a list to pick a move from that has at least one free
                List<Move> pickList = validMoves;
                if (attemptDamaging) {
                    if (typeOfMove != null) {
                        if (validTypeDamagingMoves.containsKey(typeOfMove)
                                && checkForUnusedMove(validTypeDamagingMoves.get(typeOfMove), learnt)) {
                            pickList = validTypeDamagingMoves.get(typeOfMove);
                        } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                            pickList = validDamagingMoves;
                        }
                    } else if (checkForUnusedMove(validDamagingMoves, learnt)) {
                        pickList = validDamagingMoves;
                    }
                    MoveCategory forcedCategory = random.nextDouble() < atkSpAtkRatio ? MoveCategory.PHYSICAL : MoveCategory.SPECIAL;
                    List<Move> filteredList = pickList.stream().filter(mv -> mv.category == forcedCategory).collect(Collectors.toList());
                    if (!filteredList.isEmpty() && checkForUnusedMove(filteredList, learnt)) {
                        pickList = filteredList;
                    }
                } else if (typeOfMove != null) {
                    if (validTypeMoves.containsKey(typeOfMove)
                            && checkForUnusedMove(validTypeMoves.get(typeOfMove), learnt)) {
                        pickList = validTypeMoves.get(typeOfMove);
                    }
                }

                // now pick a move until we get a valid one
                Move mv = pickList.get(random.nextInt(pickList.size()));
                while (learnt.contains(mv.number)) {
                    mv = pickList.get(random.nextInt(pickList.size()));
                }

                goodDamagingLeft--;
                learnt.add(mv.number);
            }

            // write all moves for the pokemon
            Collections.shuffle(learnt, random);
            for (int i = 0; i < learnt.size(); i++) {
                moves.set(i, learnt.get(i));
            }
        }
        // cases for certain alt forms
        Map<Integer, List<Integer>> em = movesets;
        copyEggMoves(em, Species.basculin, Species.basculinB);
        copyEggMoves(em, Species.pumpkaboo, Species.pumpkabooSmall);
        copyEggMoves(em, Species.pumpkaboo, Species.pumpkabooLarge);
        copyEggMoves(em, Species.pumpkaboo, Species.pumpkabooSuper);
        copyEggMoves(em, Species.oricorio, Species.oricorioE);
        copyEggMoves(em, Species.oricorio, Species.oricorioP);
        copyEggMoves(em, Species.oricorio, Species.oricorioG);

        // Done, save
        this.setEggMoves(movesets);
    }
    
    /**
     * Copies the Egg Moves from a source species to a target species in the map.
     * @param eggMovesMap The Map containing all egg movesets (species ID -> list of move IDs).
     * @param sourceSpecies The species to copy from.
     * @param targetSpecies The species to copy to.
     */
    private void copyEggMoves(Map<Integer, List<Integer>> eggMovesMap, int sourceSpecies, int targetSpecies) {
        List<Integer> baseMoves = eggMovesMap.get(sourceSpecies);
        eggMovesMap.put(targetSpecies, new ArrayList<>(baseMoves));
    }

    private void createSetsOfMoves(boolean noBroken, List<Move> validMoves, List<Move> validDamagingMoves,
                                   Map<Type, List<Move>> validTypeMoves, Map<Type, List<Move>> validTypeDamagingMoves) {
        List<Move> allMoves = this.getMoves();
//        List<Integer> hms = this.getHMMoves();
        Set<Integer> allBanned = new HashSet<Integer>(noBroken ? this.getGameBreakingMoves() : Collections.EMPTY_SET);
//        allBanned.addAll(hms);
        allBanned.addAll(this.getMovesBannedFromLevelup());
        allBanned.addAll(GlobalConstants.zMoves);
        allBanned.addAll(this.getIllegalMoves());

        for (Move mv : allMoves) {
            if (mv != null && !GlobalConstants.bannedRandomMoves[mv.number] && !allBanned.contains(mv.number)) {
                validMoves.add(mv);
                if (mv.type != null) {
                    if (!validTypeMoves.containsKey(mv.type)) {
                        validTypeMoves.put(mv.type, new ArrayList<>());
                    }
                    validTypeMoves.get(mv.type).add(mv);
                }

                if (!GlobalConstants.bannedForDamagingMove[mv.number]) {
                    if (mv.isGoodDamaging(perfectAccuracy)) {
                        validDamagingMoves.add(mv);
                        if (mv.type != null) {
                            if (!validTypeDamagingMoves.containsKey(mv.type)) {
                                validTypeDamagingMoves.put(mv.type, new ArrayList<>());
                            }
                            validTypeDamagingMoves.get(mv.type).add(mv);
                        }
                    }
                }
            }
        }

        Map<Type,Double> avgTypePowers = new TreeMap<>();
        double totalAvgPower = 0;

        for (Type type: validTypeMoves.keySet()) {
            List<Move> typeMoves = validTypeMoves.get(type);
            int attackingSum = 0;
            for (Move typeMove: typeMoves) {
                if (typeMove.power > 0) {
                    attackingSum += (typeMove.power * typeMove.hitCount);
                }
            }
            double avgTypePower = (double)attackingSum / (double)typeMoves.size();
            avgTypePowers.put(type, avgTypePower);
            totalAvgPower += (avgTypePower);
        }

        totalAvgPower /= (double)validTypeMoves.keySet().size();

        // Want the average power of each type to be within 25% both directions
        double minAvg = totalAvgPower * 0.75;
        double maxAvg = totalAvgPower * 1.25;

        // Add extra moves to type lists outside of the range to balance the average power of each type

        for (Type type: avgTypePowers.keySet()) {
            double avgPowerForType = avgTypePowers.get(type);
            List<Move> typeMoves = validTypeMoves.get(type);
            List<Move> alreadyPicked = new ArrayList<>();
            int iterLoops = 0;
            while (avgPowerForType < minAvg && iterLoops < 10000) {
                final double finalAvgPowerForType = avgPowerForType;
                List<Move> strongerThanAvgTypeMoves = typeMoves
                        .stream()
                        .filter(mv -> mv.power * mv.hitCount > finalAvgPowerForType)
                        .collect(Collectors.toList());
                if (strongerThanAvgTypeMoves.isEmpty()) break;
                if (alreadyPicked.containsAll(strongerThanAvgTypeMoves)) {
                    alreadyPicked = new ArrayList<>();
                } else {
                    strongerThanAvgTypeMoves.removeAll(alreadyPicked);
                }
                Move extraMove = strongerThanAvgTypeMoves.get(random.nextInt(strongerThanAvgTypeMoves.size()));
                avgPowerForType = (avgPowerForType * typeMoves.size() + extraMove.power * extraMove.hitCount)
                        / (typeMoves.size() + 1);
                typeMoves.add(extraMove);
                alreadyPicked.add(extraMove);
                iterLoops++;
            }
            iterLoops = 0;
            while (avgPowerForType > maxAvg && iterLoops < 10000) {
                final double finalAvgPowerForType = avgPowerForType;
                List<Move> weakerThanAvgTypeMoves = typeMoves
                        .stream()
                        .filter(mv -> mv.power * mv.hitCount < finalAvgPowerForType)
                        .collect(Collectors.toList());
                if (weakerThanAvgTypeMoves.isEmpty()) break;
                if (alreadyPicked.containsAll(weakerThanAvgTypeMoves)) {
                    alreadyPicked = new ArrayList<>();
                } else {
                    weakerThanAvgTypeMoves.removeAll(alreadyPicked);
                }
                Move extraMove = weakerThanAvgTypeMoves.get(random.nextInt(weakerThanAvgTypeMoves.size()));
                avgPowerForType = (avgPowerForType * typeMoves.size() + extraMove.power * extraMove.hitCount)
                        / (typeMoves.size() + 1);
                typeMoves.add(extraMove);
                alreadyPicked.add(extraMove);
                iterLoops++;
            }
        }
    }

    @Override
    public void orderDamagingMovesByDamage() {
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();
        List<Move> allMoves = this.getMoves();
        for (Integer pkmn : movesets.keySet()) {
            List<MoveLearnt> moves = movesets.get(pkmn);

            // Build up a list of damaging moves and their positions
            List<Integer> damagingMoveIndices = new ArrayList<>();
            List<Move> damagingMoves = new ArrayList<>();
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i).level == 0) continue; // Don't reorder evolution move
                Move mv = allMoves.get(moves.get(i).move);
                if (mv.power > 1) {
                    // considered a damaging move for this purpose
                    damagingMoveIndices.add(i);
                    damagingMoves.add(mv);
                }
            }

            // Ties should be sorted randomly, so shuffle the list first.
            Collections.shuffle(damagingMoves, random);

            // Sort the damaging moves by power
            damagingMoves.sort(Comparator.comparingDouble(m -> m.power * m.hitCount));

            // Reassign damaging moves in the ordered positions
            for (int i = 0; i < damagingMoves.size(); i++) {
                moves.get(damagingMoveIndices.get(i)).move = damagingMoves.get(i).number;
            }
        }

        // Done, save
        this.setMovesLearnt(movesets);
    }

    @Override
    public void metronomeOnlyMode() {

        // movesets
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();

        MoveLearnt metronomeML = new MoveLearnt();
        metronomeML.level = 1;
        metronomeML.move = Moves.metronome;

        for (List<MoveLearnt> ms : movesets.values()) {
            if (ms != null && ms.size() > 0) {
                ms.clear();
                ms.add(metronomeML);
            }
        }

        this.setMovesLearnt(movesets);

        // trainers
        // run this to remove all custom non-Metronome moves
        List<Trainer> trainers = this.getTrainers();

        for (Trainer t : trainers) {
            for (TrainerPokemon tpk : t.pokemon) {
                tpk.resetMoves = true;
            }
        }

        this.setTrainers(trainers, false, false);

        // tms
        List<Integer> tmMoves = this.getTMMoves();

        for (int i = 0; i < tmMoves.size(); i++) {
            tmMoves.set(i, Moves.metronome);
        }

        this.setTMMoves(tmMoves);

        // movetutors
        if (this.hasMoveTutors()) {
            List<Integer> mtMoves = this.getMoveTutorMoves();

            for (int i = 0; i < mtMoves.size(); i++) {
                mtMoves.set(i, Moves.metronome);
            }

            this.setMoveTutorMoves(mtMoves);
        }

        // move tweaks
        List<Move> moveData = this.getMoves();

        Move metronome = moveData.get(Moves.metronome);

        metronome.pp = 40;

        List<Integer> hms = this.getHMMoves();

        for (int hm : hms) {
            Move thisHM = moveData.get(hm);
            thisHM.pp = 0;
        }
    }

    @Override
    public void customStarters(Settings settings) {
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        int[] customStarters = settings.getCustomStarters();
        boolean allowAltFormes = settings.isAllowStarterAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();

        List<Pokemon> romPokemon = getPokemonInclFormes()
                .stream()
                .filter(pk -> pk == null || !pk.actuallyCosmetic)
                .collect(Collectors.toList());

        List<Pokemon> banned = getBannedFormesForPlayerPokemon();
        pickedStarters = new ArrayList<>();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        // loop to add chosen pokemon to banned, preventing it from being a random option.
        for (int i = 0; i < customStarters.length; i = i + 1){
            if (!(customStarters[i] - 1 == 0)){
                banned.add(romPokemon.get(customStarters[i] - 1));
            }
        }
        if (customStarters[0] - 1 == 0){
            Pokemon pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
            }
            pickedStarters.add(pkmn);
        } else {
            Pokemon pkmn1 = romPokemon.get(customStarters[0] - 1);
            pickedStarters.add(pkmn1);
        }
        if (customStarters[1] - 1 == 0){
            Pokemon pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
            }
            pickedStarters.add(pkmn);
        } else {
            Pokemon pkmn2 = romPokemon.get(customStarters[1] - 1);
            pickedStarters.add(pkmn2);
        }

        if (isYellow()) {
            setStarters(pickedStarters);
        } else {
            if (customStarters[2] - 1 == 0){
                Pokemon pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
                while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                    pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
                }
                pickedStarters.add(pkmn);
            } else {
                Pokemon pkmn3 = romPokemon.get(customStarters[2] - 1);
                pickedStarters.add(pkmn3);
            }
            if (starterCount() > 3) {
                for (int i = 3; i < starterCount(); i++) {
                    Pokemon pkmn = random2EvosPokemon(allowAltFormes);
                    while (pickedStarters.contains(pkmn)) {
                        pkmn = random2EvosPokemon(allowAltFormes);
                    }
                    pickedStarters.add(pkmn);
                }
                setStarters(pickedStarters);
            } else {
                setStarters(pickedStarters);
            }
        }
    }

    @Override
    public void randomizeStarters(Settings settings) {
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        boolean allowAltFormes = settings.isAllowStarterAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();

        int starterCount = starterCount();
        pickedStarters = new ArrayList<>();
        List<Pokemon> banned = getBannedFormesForPlayerPokemon();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        List<Pokemon> pokemonList = this.getPokemon();
        banned.add(pokemonList.get(Species.pikachuCap));
        banned.add(pokemonList.get(Species.pikachuPartner));
        banned.add(pokemonList.get(Species.eeveePartner));
        banned.add(pokemonList.get(Species.pichuSpiky));
        banned.add(pokemonList.get(Species.burmyS));
        banned.add(pokemonList.get(Species.burmyT));
        banned.add(pokemonList.get(Species.cherrimS));
        banned.add(pokemonList.get(Species.shayminS));
        banned.add(pokemonList.get(Species.greninjaB));
        banned.add(pokemonList.get(Species.hoopaU));
        banned.add(pokemonList.get(Species.morpekoH));
        if (abilitiesUnchanged) {
            banned.add(pokemonList.get(Species.castformF));
            banned.add(pokemonList.get(Species.castformW));
            banned.add(pokemonList.get(Species.castformI));
        }
        for (int i = 0; i < starterCount; i++) {
            Pokemon pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = allowAltFormes ? randomPokemonInclFormes() : randomPokemon();
            }
            pickedStarters.add(pkmn);
        }
        setStarters(pickedStarters);
    }

    @Override
    public void randomizeBasicTwoEvosStarters(Settings settings) {
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        boolean allowAltFormes = settings.isAllowStarterAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();

        int starterCount = starterCount();
        pickedStarters = new ArrayList<>();
        List<Pokemon> banned = getBannedFormesForPlayerPokemon();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        for (int i = 0; i < starterCount; i++) {
            Pokemon pkmn = random2EvosPokemon(allowAltFormes);
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn)) {
                pkmn = random2EvosPokemon(allowAltFormes);
            }
            pickedStarters.add(pkmn);
        }
        setStarters(pickedStarters);
    }

    @Override
    public List<Pokemon> getPickedStarters() {
        return pickedStarters;
    }


    @Override
    public void randomizeStaticPokemon(Settings settings) {
        boolean swapLegendaries = settings.getStaticPokemonMod() == Settings.StaticPokemonMod.RANDOM_MATCHING;
        boolean similarStrength = settings.getStaticPokemonMod() == Settings.StaticPokemonMod.SIMILAR_STRENGTH;
        boolean limitMainGameLegendaries = settings.isLimitMainGameLegendaries();
        boolean limit600 = settings.isLimit600();
        boolean allowAltFormes = settings.isAllowStaticAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean swapMegaEvos = settings.isSwapStaticMegaEvos();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;
        int levelModifier = settings.isStaticLevelModified() ? settings.getStaticLevelModifier() : 0;
        boolean correctStaticMusic = settings.isCorrectStaticMusic();

        // Load
        checkPokemonRestrictions();
        List<StaticEncounter> currentStaticPokemon = this.getStaticPokemon();
        List<StaticEncounter> replacements = new ArrayList<>();
        List<Pokemon> banned = this.bannedForStaticPokemon(settings);
        banned.addAll(this.getBannedFormesForPlayerPokemon());
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        boolean reallySwapMegaEvos = forceSwapStaticMegaEvos() || swapMegaEvos;

        Map<Integer,Integer> specialMusicStaticChanges = new HashMap<>();
        List<Integer> changeMusicStatics = new ArrayList<>();
        if (correctStaticMusic) {
            changeMusicStatics = getSpecialMusicStatics();
        }

        if (swapLegendaries) {
            List<Pokemon> legendariesLeft = new ArrayList<>(onlyLegendaryList);
            if (allowAltFormes) {
                legendariesLeft.addAll(onlyLegendaryAltsList);
                legendariesLeft =
                        legendariesLeft
                                .stream()
                                .filter(pk -> !pk.actuallyCosmetic)
                                .collect(Collectors.toList());
            }
            List<Pokemon> nonlegsLeft = new ArrayList<>(noLegendaryList);
            if (allowAltFormes) {
                nonlegsLeft.addAll(noLegendaryAltsList);
                nonlegsLeft =
                        nonlegsLeft
                                .stream()
                                .filter(pk -> !pk.actuallyCosmetic)
                                .collect(Collectors.toList());
            }
            List<Pokemon> ultraBeastsLeft = new ArrayList<>(ultraBeastList);
            legendariesLeft.removeAll(banned);
            nonlegsLeft.removeAll(banned);
            ultraBeastsLeft.removeAll(banned);

            // Full pools for easier refilling later
            List<Pokemon> legendariesPool = new ArrayList<>(legendariesLeft);
            List<Pokemon> nonlegsPool = new ArrayList<>(nonlegsLeft);
            List<Pokemon> ultraBeastsPool = new ArrayList<>(ultraBeastsLeft);

            for (StaticEncounter old : currentStaticPokemon) {
                StaticEncounter newStatic = cloneStaticEncounter(old);
                Pokemon newPK;
                if (old.pkmn.isLegendary()) {
                    if (reallySwapMegaEvos && old.canMegaEvolve()) {
                        newPK = getMegaEvoPokemon(onlyLegendaryList, legendariesLeft, newStatic);
                    } else {
                        if (old.restrictedPool) {
                            newPK = getRestrictedPokemon(legendariesPool, legendariesLeft, old);
                        } else {
                            newPK = legendariesLeft.remove(this.random.nextInt(legendariesLeft.size()));
                        }
                    }

                    setPokemonAndFormeForStaticEncounter(newStatic, newPK);

                    if (legendariesLeft.size() == 0) {
                        legendariesLeft.addAll(legendariesPool);
                    }
                } else if (ultraBeastList.contains(old.pkmn)) {
                    if (old.restrictedPool) {
                        newPK = getRestrictedPokemon(ultraBeastsPool, ultraBeastsLeft, old);
                    } else {
                        newPK = ultraBeastsLeft.remove(this.random.nextInt(ultraBeastsLeft.size()));
                    }

                    setPokemonAndFormeForStaticEncounter(newStatic, newPK);

                    if (ultraBeastsLeft.size() == 0) {
                        ultraBeastsLeft.addAll(ultraBeastsPool);
                    }
                } else {
                    if (reallySwapMegaEvos && old.canMegaEvolve()) {
                        newPK = getMegaEvoPokemon(noLegendaryList, nonlegsLeft, newStatic);
                    } else {
                        if (old.restrictedPool) {
                            newPK = getRestrictedPokemon(nonlegsPool, nonlegsLeft, old);
                        } else {
                            newPK = nonlegsLeft.remove(this.random.nextInt(nonlegsLeft.size()));
                        }
                    }
                    setPokemonAndFormeForStaticEncounter(newStatic, newPK);

                    if (nonlegsLeft.size() == 0) {
                        nonlegsLeft.addAll(nonlegsPool);
                    }
                }
                replacements.add(newStatic);
                if (changeMusicStatics.contains(old.pkmn.number)) {
                    specialMusicStaticChanges.put(old.pkmn.number, newPK.number);
                }
            }
        } else if (similarStrength) {
            List<Pokemon> listInclFormesExclCosmetics =
                    mainPokemonListInclFormes
                            .stream()
                            .filter(pk -> !pk.actuallyCosmetic)
                            .collect(Collectors.toList());
            List<Pokemon> pokemonLeft = new ArrayList<>(!allowAltFormes ? mainPokemonList : listInclFormesExclCosmetics);
            pokemonLeft.removeAll(banned);

            List<Pokemon> pokemonPool = new ArrayList<>(pokemonLeft);

            List<Integer> mainGameLegendaries = getMainGameLegendaries();
            for (StaticEncounter old : currentStaticPokemon) {
                StaticEncounter newStatic = cloneStaticEncounter(old);
                Pokemon newPK;
                Pokemon oldPK = old.pkmn;
                if (old.forme > 0) {
                    oldPK = getAltFormeOfPokemon(oldPK, old.forme);
                }
                Integer oldBST = oldPK.bstForPowerLevels();
                if (oldBST >= 600 && limit600) {
                    if (reallySwapMegaEvos && old.canMegaEvolve()) {
                        newPK = getMegaEvoPokemon(mainPokemonList, pokemonLeft, newStatic);
                    } else {
                        if (old.restrictedPool) {
                            newPK = getRestrictedPokemon(pokemonPool, pokemonLeft, old);
                        } else {
                            newPK = pokemonLeft.remove(this.random.nextInt(pokemonLeft.size()));
                        }
                    }
                    setPokemonAndFormeForStaticEncounter(newStatic, newPK);
                } else {
                    boolean limitBST = oldPK.baseForme == null ?
                            limitMainGameLegendaries && mainGameLegendaries.contains(oldPK.number) :
                            limitMainGameLegendaries && mainGameLegendaries.contains(oldPK.baseForme.number);
                    if (reallySwapMegaEvos && old.canMegaEvolve()) {
                        List<Pokemon> megaEvoPokemonLeft =
                                megaEvolutionsList
                                        .stream()
                                        .filter(mega -> mega.method == 1)
                                        .map(mega -> mega.from)
                                        .distinct()
                                        .filter(pokemonLeft::contains)
                                        .collect(Collectors.toList());
                        if (megaEvoPokemonLeft.isEmpty()) {
                            megaEvoPokemonLeft =
                                    megaEvolutionsList
                                            .stream()
                                            .filter(mega -> mega.method == 1)
                                            .map(mega -> mega.from)
                                            .distinct()
                                            .filter(mainPokemonList::contains)
                                            .collect(Collectors.toList());
                        }
                        newPK = pickStaticPowerLvlReplacement(
                                megaEvoPokemonLeft,
                                oldPK,
                                true,
                                limitBST);
                        newStatic.heldItem = newPK
                                .megaEvolutionsFrom
                                .get(this.random.nextInt(newPK.megaEvolutionsFrom.size()))
                                .argument;
                    } else {
                        if (old.restrictedPool) {
                            List<Pokemon> restrictedPool = pokemonLeft
                                            .stream()
                                            .filter(pk -> old.restrictedList.contains(pk))
                                            .collect(Collectors.toList());
                            if (restrictedPool.isEmpty()) {
                                restrictedPool = pokemonPool
                                        .stream()
                                        .filter(pk -> old.restrictedList.contains(pk))
                                        .collect(Collectors.toList());
                            }
                            newPK = pickStaticPowerLvlReplacement(
                                    restrictedPool,
                                    oldPK,
                                    false, // Allow same Pokemon just in case
                                    limitBST);
                        } else {
                            newPK = pickStaticPowerLvlReplacement(
                                    pokemonLeft,
                                    oldPK,
                                    true,
                                    limitBST);
                        }
                    }
                    pokemonLeft.remove(newPK);
                    setPokemonAndFormeForStaticEncounter(newStatic, newPK);
                }

                if (pokemonLeft.size() == 0) {
                    pokemonLeft.addAll(pokemonPool);
                }
                replacements.add(newStatic);
                if (changeMusicStatics.contains(old.pkmn.number)) {
                    specialMusicStaticChanges.put(old.pkmn.number, newPK.number);
                }
            }
        } else { // Completely random
            List<Pokemon> listInclFormesExclCosmetics =
                    mainPokemonListInclFormes
                            .stream()
                            .filter(pk -> !pk.actuallyCosmetic)
                            .collect(Collectors.toList());
            List<Pokemon> pokemonLeft = new ArrayList<>(!allowAltFormes ? mainPokemonList : listInclFormesExclCosmetics);
            pokemonLeft.removeAll(banned);

            List<Pokemon> pokemonPool = new ArrayList<>(pokemonLeft);

            for (StaticEncounter old : currentStaticPokemon) {
                StaticEncounter newStatic = cloneStaticEncounter(old);
                Pokemon newPK;
                if (reallySwapMegaEvos && old.canMegaEvolve()) {
                    newPK = getMegaEvoPokemon(mainPokemonList, pokemonLeft, newStatic);
                } else {
                    if (old.restrictedPool) {
                        newPK = getRestrictedPokemon(pokemonPool, pokemonLeft, old);
                    } else {
                        newPK = pokemonLeft.remove(this.random.nextInt(pokemonLeft.size()));
                    }
                }
                pokemonLeft.remove(newPK);
                setPokemonAndFormeForStaticEncounter(newStatic, newPK);
                if (pokemonLeft.size() == 0) {
                    pokemonLeft.addAll(pokemonPool);
                }
                replacements.add(newStatic);
                if (changeMusicStatics.contains(old.pkmn.number)) {
                    specialMusicStaticChanges.put(old.pkmn.number, newPK.number);
                }
            }
        }

        if (levelModifier != 0) {
            for (StaticEncounter se : replacements) {
                if (!se.isEgg) {
                    se.level = Math.min(100, (int) Math.round(se.level * (1 + levelModifier / 100.0)));
                    se.maxLevel = Math.min(100, (int) Math.round(se.maxLevel * (1 + levelModifier / 100.0)));
                    for (StaticEncounter linkedStatic : se.linkedEncounters) {
                        if (!linkedStatic.isEgg) {
                            linkedStatic.level = Math.min(100, (int) Math.round(linkedStatic.level * (1 + levelModifier / 100.0)));
                            linkedStatic.maxLevel = Math.min(100, (int) Math.round(linkedStatic.maxLevel * (1 + levelModifier / 100.0)));
                        }
                    }
                }
            }
        }

        if (specialMusicStaticChanges.size() > 0) {
            applyCorrectStaticMusic(specialMusicStaticChanges);
        }

        // Save
        this.setStaticPokemon(replacements);
    }

    private Pokemon getRestrictedPokemon(List<Pokemon> fullList, List<Pokemon> pokemonLeft, StaticEncounter old) {
        Pokemon newPK;
        List<Pokemon> restrictedPool = pokemonLeft.stream().filter(pk -> old.restrictedList.contains(pk)).collect(Collectors.toList());
        if (restrictedPool.isEmpty()) {
            restrictedPool = fullList
                            .stream()
                            .filter(pk -> old.restrictedList.contains(pk))
                            .collect(Collectors.toList());
        }
        newPK = restrictedPool.remove(this.random.nextInt(restrictedPool.size()));
        pokemonLeft.remove(newPK);
        return newPK;
    }

    @Override
    public void onlyChangeStaticLevels(Settings settings) {
        int levelModifier = settings.getStaticLevelModifier();

        List<StaticEncounter> currentStaticPokemon = this.getStaticPokemon();
        for (StaticEncounter se : currentStaticPokemon) {
            if (!se.isEgg) {
                se.level = Math.min(100, (int) Math.round(se.level * (1 + levelModifier / 100.0)));
                for (StaticEncounter linkedStatic : se.linkedEncounters) {
                    if (!linkedStatic.isEgg) {
                        linkedStatic.level = Math.min(100, (int) Math.round(linkedStatic.level * (1 + levelModifier / 100.0)));
                    }
                }
            }
            setPokemonAndFormeForStaticEncounter(se, se.pkmn);
        }
        this.setStaticPokemon(currentStaticPokemon);
    }

    private StaticEncounter cloneStaticEncounter(StaticEncounter old) {
        StaticEncounter newStatic = new StaticEncounter();
        newStatic.pkmn = old.pkmn;
        newStatic.level = old.level;
        newStatic.maxLevel = old.maxLevel;
        newStatic.heldItem = old.heldItem;
        newStatic.isEgg = old.isEgg;
        newStatic.resetMoves = true;
        for (StaticEncounter oldLinked : old.linkedEncounters) {
            StaticEncounter newLinked = new StaticEncounter();
            newLinked.pkmn = oldLinked.pkmn;
            newLinked.level = oldLinked.level;
            newLinked.maxLevel = oldLinked.maxLevel;
            newLinked.heldItem = oldLinked.heldItem;
            newLinked.isEgg = oldLinked.isEgg;
            newLinked.resetMoves = true;
            newStatic.linkedEncounters.add(newLinked);
        }
        return newStatic;
    }

    private void setPokemonAndFormeForStaticEncounter(StaticEncounter newStatic, Pokemon pk) {
        boolean checkCosmetics = true;
        Pokemon newPK = pk;
        int newForme = 0;
        if (pk.formeNumber > 0) {
            newForme = pk.formeNumber;
            newPK = pk.baseForme;
            checkCosmetics = false;
        }
        if (checkCosmetics && pk.cosmeticForms > 0) {
            newForme = pk.getCosmeticFormNumber(this.random.nextInt(pk.cosmeticForms));
        } else if (!checkCosmetics && pk.cosmeticForms > 0) {
            newForme += pk.getCosmeticFormNumber(this.random.nextInt(pk.cosmeticForms));
        }
        newStatic.pkmn = newPK;
        newStatic.forme = newForme;
        for (StaticEncounter linked : newStatic.linkedEncounters) {
            linked.pkmn = newPK;
            linked.forme = newForme;
        }
    }

    private void setFormeForStaticEncounter(StaticEncounter newStatic, Pokemon pk) {
        boolean checkCosmetics = true;
        newStatic.forme = 0;
        if (pk.formeNumber > 0) {
            newStatic.forme = pk.formeNumber;
            newStatic.pkmn = pk.baseForme;
            checkCosmetics = false;
        }
        if (checkCosmetics && newStatic.pkmn.cosmeticForms > 0) {
            newStatic.forme = newStatic.pkmn.getCosmeticFormNumber(this.random.nextInt(newStatic.pkmn.cosmeticForms));
        } else if (!checkCosmetics && pk.cosmeticForms > 0) {
            newStatic.forme += pk.getCosmeticFormNumber(this.random.nextInt(pk.cosmeticForms));
        }
    }

    private Pokemon getMegaEvoPokemon(List<Pokemon> fullList, List<Pokemon> pokemonLeft, StaticEncounter newStatic) {
        List<MegaEvolution> megaEvos = megaEvolutionsList;
        List<Pokemon> megaEvoPokemon =
                megaEvos
                        .stream()
                        .filter(mega -> mega.method == 1)
                        .map(mega -> mega.from)
                        .distinct()
                        .collect(Collectors.toList());
        Pokemon newPK;
        List<Pokemon> megaEvoPokemonLeft =
                megaEvoPokemon
                        .stream()
                        .filter(pokemonLeft::contains)
                        .collect(Collectors.toList());
        if (megaEvoPokemonLeft.isEmpty()) {
            megaEvoPokemonLeft = megaEvoPokemon
                    .stream()
                    .filter(fullList::contains)
                    .collect(Collectors.toList());
        }
        newPK = megaEvoPokemonLeft.remove(this.random.nextInt(megaEvoPokemonLeft.size()));
        pokemonLeft.remove(newPK);
        newStatic.heldItem = newPK
                .megaEvolutionsFrom
                .get(this.random.nextInt(newPK.megaEvolutionsFrom.size()))
                .argument;
        return newPK;
    }

    @Override
    public void randomizeTMMoves(Settings settings) {
        boolean noBroken = settings.isBlockBrokenTMMoves();
        boolean preserveField = settings.isKeepFieldMoveTMs();
        double goodDamagingPercentage = settings.isTmsForceGoodDamaging() ? settings.getTmsGoodDamagingPercent() / 100.0 : 0;

        // Pick some random TM moves.
        int tmCount = this.getTMCount();
        List<Move> allMoves = this.getMoves();
        //List<Integer> hms = this.getHMMoves();
        List<Integer> oldTMs = this.getTMMoves();
        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? this.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(getMovesBannedFromLevelup());
        banned.addAll(this.getIllegalMoves());
        // field moves?
        List<Integer> fieldMoves = this.getFieldMoves();
        int preservedFieldMoveCount = 0;

        if (preserveField) {
            List<Integer> banExistingField = new ArrayList<>(oldTMs);
            banExistingField.retainAll(fieldMoves);
            preservedFieldMoveCount = banExistingField.size();
            banned.addAll(banExistingField);
        }

        // Determine which moves are pickable
        List<Move> usableMoves = new ArrayList<>(allMoves);
        usableMoves.remove(0); // remove null entry
        Set<Move> unusableMoves = new HashSet<>();
        Set<Move> unusableDamagingMoves = new HashSet<>();

        for (Move mv : usableMoves) {
            if (GlobalConstants.bannedRandomMoves[mv.number] || GlobalConstants.zMoves.contains(mv.number) ||
                    /*hms.contains(mv.number) ||*/ banned.contains(mv.number)) {
                unusableMoves.add(mv);
            } else if (GlobalConstants.bannedForDamagingMove[mv.number] || !mv.isGoodDamaging(perfectAccuracy)) {
                unusableDamagingMoves.add(mv);
            }
        }

        usableMoves.removeAll(unusableMoves);
        List<Move> usableDamagingMoves = new ArrayList<>(usableMoves);
        usableDamagingMoves.removeAll(unusableDamagingMoves);

        // pick (tmCount - preservedFieldMoveCount) moves
        List<Integer> pickedMoves = new ArrayList<>();

        // Force a certain amount of good damaging moves depending on the percentage
        int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * (tmCount - preservedFieldMoveCount));

        for (int i = 0; i < tmCount - preservedFieldMoveCount; i++) {
            Move chosenMove;
            if (goodDamagingLeft > 0 && usableDamagingMoves.size() > 0) {
                chosenMove = usableDamagingMoves.get(random.nextInt(usableDamagingMoves.size()));
            } else {
                chosenMove = usableMoves.get(random.nextInt(usableMoves.size()));
            }
            pickedMoves.add(chosenMove.number);
            usableMoves.remove(chosenMove);
            usableDamagingMoves.remove(chosenMove);
            goodDamagingLeft--;
        }

        // shuffle the picked moves because high goodDamagingPercentage
        // will bias them towards early numbers otherwise

        Collections.shuffle(pickedMoves, random);

        // finally, distribute them as tms
        int pickedMoveIndex = 0;
        List<Integer> newTMs = new ArrayList<>();

        for (int i = 0; i < tmCount; i++) {
            if (preserveField && fieldMoves.contains(oldTMs.get(i))) {
                newTMs.add(oldTMs.get(i));
            } else {
                newTMs.add(pickedMoves.get(pickedMoveIndex++));
            }
        }

        this.setTMMoves(newTMs);
    }

    @Override
    public void randomizeTMHMCompatibility(Settings settings) {
        boolean preferSameType = settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE;
        boolean followEvolutions = settings.isTmsFollowEvolutions();

        // Get current compatibility
        // increase HM chances if required early on
        List<Integer> requiredEarlyOn = this.getEarlyRequiredHMMoves();
        Map<Pokemon, boolean[]> compat = this.getTMHMCompatibility();
        List<Integer> tmHMs = new ArrayList<>(this.getTMMoves());
        tmHMs.addAll(this.getHMMoves());

        if (followEvolutions) {
            copyUpEvolutionsHelper(pk -> randomizePokemonMoveCompatibility(
                    pk, compat.get(pk), tmHMs, requiredEarlyOn, preferSameType),
            (evFrom, evTo, toMonIsFinalEvo) ->  copyPokemonMoveCompatibilityUpEvolutions(
                    evFrom, evTo, compat.get(evFrom), compat.get(evTo), tmHMs, preferSameType
            ), null, true);
        }
        else {
            for (Map.Entry<Pokemon, boolean[]> compatEntry : compat.entrySet()) {
                randomizePokemonMoveCompatibility(compatEntry.getKey(), compatEntry.getValue(), tmHMs,
                        requiredEarlyOn, preferSameType);
            }
        }
        // cases for certain alt forms
        List<Pokemon> pokemonList = this.getPokemon();
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.venusaur), pokemonList.get(Species.venusaurMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.charizard), pokemonList.get(Species.charizardMegaX));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.charizard), pokemonList.get(Species.charizardMegaY));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.blastoise), pokemonList.get(Species.blastoiseMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.beedrill), pokemonList.get(Species.beedrillMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pidgeot), pokemonList.get(Species.pidgeotMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.alakazam), pokemonList.get(Species.alakazamMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.slowbro), pokemonList.get(Species.slowbroMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gengar), pokemonList.get(Species.gengarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.kangaskhan), pokemonList.get(Species.kangaskhanMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pinsir), pokemonList.get(Species.pinsirMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gyarados), pokemonList.get(Species.gyaradosMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.aerodactyl), pokemonList.get(Species.aerodactylMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.mewtwo), pokemonList.get(Species.mewtwoMegaX));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.mewtwo), pokemonList.get(Species.mewtwoMegaY));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ampharos), pokemonList.get(Species.ampharosMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.steelix), pokemonList.get(Species.steelixMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scizor), pokemonList.get(Species.scizorMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.heracross), pokemonList.get(Species.heracrossMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.houndoom), pokemonList.get(Species.houndoomMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.tyranitar), pokemonList.get(Species.tyranitarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.sceptile), pokemonList.get(Species.sceptileMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.blaziken), pokemonList.get(Species.blazikenMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.swampert), pokemonList.get(Species.swampertMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gardevoir), pokemonList.get(Species.gardevoirMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.sableye), pokemonList.get(Species.sableyeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.mawile), pokemonList.get(Species.mawileMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.aggron), pokemonList.get(Species.aggronMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.medicham), pokemonList.get(Species.medichamMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.manectric), pokemonList.get(Species.manectricMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.sharpedo), pokemonList.get(Species.sharpedoMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.camerupt), pokemonList.get(Species.cameruptMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.altaria), pokemonList.get(Species.altariaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.banette), pokemonList.get(Species.banetteMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.absol), pokemonList.get(Species.absolMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.glalie), pokemonList.get(Species.glalieMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.salamence), pokemonList.get(Species.salamenceMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.metagross), pokemonList.get(Species.metagrossMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.latias), pokemonList.get(Species.latiasMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.latios), pokemonList.get(Species.latiosMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rayquaza), pokemonList.get(Species.rayquazaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.lopunny), pokemonList.get(Species.lopunnyMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.garchomp), pokemonList.get(Species.garchompMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.lucario), pokemonList.get(Species.lucarioMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.abomasnow), pokemonList.get(Species.abomasnowMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gallade), pokemonList.get(Species.galladeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.audino), pokemonList.get(Species.audinoMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.diancie), pokemonList.get(Species.diancieMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.kyogre), pokemonList.get(Species.kyogreP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.groudon), pokemonList.get(Species.groudonP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.clefable), pokemonList.get(Species.clefableMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.victreebel), pokemonList.get(Species.victreebelMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.starmie), pokemonList.get(Species.starmieMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.dragonite), pokemonList.get(Species.dragoniteMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meganium), pokemonList.get(Species.meganiumMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.feraligatr), pokemonList.get(Species.feraligatrMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.skarmory), pokemonList.get(Species.skarmoryMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.froslass), pokemonList.get(Species.froslassMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.emboar), pokemonList.get(Species.emboarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.excadrill), pokemonList.get(Species.excadrillMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scolipede), pokemonList.get(Species.scolipedeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scrafty), pokemonList.get(Species.scraftyMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.eelektross), pokemonList.get(Species.eelektrossMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.chandelure), pokemonList.get(Species.chandelureMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.chesnaught), pokemonList.get(Species.chesnaughtMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.delphox), pokemonList.get(Species.delphoxMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.greninja), pokemonList.get(Species.greninjaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pyroar), pokemonList.get(Species.pyroarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.floetteE), pokemonList.get(Species.floetteMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.malamar), pokemonList.get(Species.malamarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.barbaracle), pokemonList.get(Species.barbaracleMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.dragalge), pokemonList.get(Species.dragalgeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.hawlucha), pokemonList.get(Species.hawluchaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zygardeC), pokemonList.get(Species.zygardeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.drampa), pokemonList.get(Species.drampaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.falinks), pokemonList.get(Species.falinksMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.raichu), pokemonList.get(Species.raichuMegaX));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.raichu), pokemonList.get(Species.raichuMegaY));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.chimecho), pokemonList.get(Species.chimechoMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.absol), pokemonList.get(Species.absolMegaZ));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.staraptor), pokemonList.get(Species.staraptorMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.garchomp), pokemonList.get(Species.garchompMegaZ));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.lucario), pokemonList.get(Species.lucarioMegaZ));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.heatran), pokemonList.get(Species.heatranMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.darkrai), pokemonList.get(Species.darkraiMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.golurk), pokemonList.get(Species.golurkMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meowstic), pokemonList.get(Species.meowsticMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meowsticF), pokemonList.get(Species.meowsticFMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.crabominable), pokemonList.get(Species.crabominableMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.golisopod), pokemonList.get(Species.golisopodMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.magearna), pokemonList.get(Species.magearnaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zeraora), pokemonList.get(Species.zeraoraMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scovillain), pokemonList.get(Species.scovillainMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.glimmora), pokemonList.get(Species.glimmoraMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.tatsugiri), pokemonList.get(Species.tatsugiriMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.baxcalibur), pokemonList.get(Species.baxcaliburMega));

        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pikachu), pokemonList.get(Species.pikachuCap));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pichu), pokemonList.get(Species.pichuSpiky));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.castform), pokemonList.get(Species.castformF));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.castform), pokemonList.get(Species.castformW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.castform), pokemonList.get(Species.castformI));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.burmy), pokemonList.get(Species.burmyS));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.burmy), pokemonList.get(Species.burmyT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.cherrim), pokemonList.get(Species.cherrimS));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomH));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomFr));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomFa));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomM));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.dialga), pokemonList.get(Species.dialgaO));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.palkia), pokemonList.get(Species.palkiaO));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.giratina), pokemonList.get(Species.giratinaO));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.basculin), pokemonList.get(Species.basculinB));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.tornadus), pokemonList.get(Species.tornadusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.thundurus), pokemonList.get(Species.thundurusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.landorus), pokemonList.get(Species.landorusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meloetta), pokemonList.get(Species.meloettaP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.greninja), pokemonList.get(Species.greninjaB));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.greninja), pokemonList.get(Species.greninjaA));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.aegislash), pokemonList.get(Species.aegislashB));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pumpkaboo), pokemonList.get(Species.pumpkabooSmall));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pumpkaboo), pokemonList.get(Species.pumpkabooLarge));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pumpkaboo), pokemonList.get(Species.pumpkabooSuper));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gourgeist), pokemonList.get(Species.gourgeistSmall));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gourgeist), pokemonList.get(Species.gourgeistLarge));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gourgeist), pokemonList.get(Species.gourgeistSuper));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zygarde), pokemonList.get(Species.zygarde10));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zygarde), pokemonList.get(Species.zygardeC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.oricorio), pokemonList.get(Species.oricorioE));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.oricorio), pokemonList.get(Species.oricorioP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.oricorio), pokemonList.get(Species.oricorioG));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.wishiwashi), pokemonList.get(Species.wishiwashiS));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.minior), pokemonList.get(Species.miniorC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.necrozma), pokemonList.get(Species.necrozmaDM));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.necrozma), pokemonList.get(Species.necrozmaDW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.necrozma), pokemonList.get(Species.necrozmaU));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.eiscue), pokemonList.get(Species.eiscueN));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.morpeko), pokemonList.get(Species.morpekoH));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zacian), pokemonList.get(Species.zacianC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zamazenta), pokemonList.get(Species.zamazentaC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.eternatus), pokemonList.get(Species.eternatusE));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.basculegion), pokemonList.get(Species.basculegionF));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.enamorus), pokemonList.get(Species.enamorusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.squawkabilly), pokemonList.get(Species.squawkabillyW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.palafin), pokemonList.get(Species.palafinH));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gimmighoul), pokemonList.get(Species.gimmighoulR));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ogerpon), pokemonList.get(Species.ogerponF));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ogerpon), pokemonList.get(Species.ogerponR));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ogerpon), pokemonList.get(Species.ogerponW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.terapagos), pokemonList.get(Species.terapagosT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.terapagos), pokemonList.get(Species.terapagosS));

        // Set the new compatibility
        this.setTMHMCompatibility(compat);
    }

    /**
     * Copies the TM/HM or Move Tutor compatibility array from a source
     * Pokemon to a target Pokemon in the provided compatibility map.
     * @param compatibilityMap The map containing Pokemon and their TM/HM or Move Tutor compatibility arrays.
     * @param sourcePokemon The Pokemon from which to copy the compatibility array.
     * @param targetPokemon The Pokemon to which the compatibility array will be copied.
     */
    private void copyTMHMTutorCompatibility(Map<Pokemon, boolean[]> compatibilityMap, Pokemon sourcePokemon, Pokemon targetPokemon) {
        boolean[] baseFlags = compatibilityMap.get(sourcePokemon);
        compatibilityMap.put(targetPokemon, Arrays.copyOf(baseFlags, baseFlags.length));
    }

    private void randomizePokemonMoveCompatibility(Pokemon pkmn, boolean[] moveCompatibilityFlags,
                                                   List<Integer> moveIDs, List<Integer> prioritizedMoves,
                                                   boolean preferSameType) {
        List<Move> moveData = this.getMoves();
        for (int i = 1; i <= moveIDs.size(); i++) {
            int move = moveIDs.get(i - 1);
            Move mv = moveData.get(move);
            double probability = getMoveCompatibilityProbability(
                    pkmn,
                    mv,
                    prioritizedMoves.contains(move),
                    preferSameType
            );
            moveCompatibilityFlags[i] = (this.random.nextDouble() < probability);
        }
    }

    private void copyPokemonMoveCompatibilityUpEvolutions(Pokemon evFrom, Pokemon evTo, boolean[] prevCompatibilityFlags,
                                                          boolean[] toCompatibilityFlags, List<Integer> moveIDs,
                                                          boolean preferSameType) {
        List<Move> moveData = this.getMoves();
        for (int i = 1; i <= moveIDs.size(); i++) {
            if (!prevCompatibilityFlags[i]) {
                // Slight chance to gain TM/HM compatibility for a move if not learned by an earlier evolution step
                // Without prefer same type: 25% chance
                // With prefer same type:    10% chance, 90% chance for a type new to this evolution
                int move = moveIDs.get(i - 1);
                Move mv = moveData.get(move);
                double probability = 0.25;
                if (preferSameType) {
                    probability = 0.1;
                    if (evTo.primaryType.equals(mv.type)
                            && !evTo.primaryType.equals(evFrom.primaryType) && !evTo.primaryType.equals(evFrom.secondaryType)
                            || evTo.secondaryType != null && evTo.secondaryType.equals(mv.type)
                            && !evTo.secondaryType.equals(evFrom.secondaryType) && !evTo.secondaryType.equals(evFrom.primaryType)) {
                        probability = 0.9;
                    }
                }
                toCompatibilityFlags[i] = (this.random.nextDouble() < probability);
            }
            else {
                toCompatibilityFlags[i] = prevCompatibilityFlags[i];
            }
        }
    }

    private double getMoveCompatibilityProbability(Pokemon pkmn, Move mv, boolean requiredEarlyOn,
                                                  boolean preferSameType) {
            double probability = 0.5;
            if (preferSameType) {
                if (pkmn.primaryType.equals(mv.type)
                        || (pkmn.secondaryType != null && pkmn.secondaryType.equals(mv.type))) {
                    probability = 0.9;
                } else if (mv.type != null && mv.type.equals(Type.NORMAL)) {
                    probability = 0.5;
                } else {
                    probability = 0.25;
                }
            }
            if (requiredEarlyOn) {
                probability = Math.min(1.0, probability * 1.8);
            }
            return probability;
    }

    @Override
    public void fullTMHMCompatibility() {
        Map<Pokemon, boolean[]> compat = this.getTMHMCompatibility();
        for (Map.Entry<Pokemon, boolean[]> compatEntry : compat.entrySet()) {
            boolean[] flags = compatEntry.getValue();
            for (int i = 1; i < flags.length; i++) {
                flags[i] = true;
            }
        }
        this.setTMHMCompatibility(compat);
    }

    @Override
    public void ensureTMCompatSanity() {
        // if a pokemon learns a move in its moveset
        // and there is a TM of that move, make sure
        // that TM can be learned.
        Map<Pokemon, boolean[]> compat = this.getTMHMCompatibility();
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();
        List<Integer> tmMoves = this.getTMMoves();
        for (Pokemon pkmn : compat.keySet()) {
            List<MoveLearnt> moveset = movesets.get(pkmn.number);
            boolean[] pkmnCompat = compat.get(pkmn);
            for (MoveLearnt ml : moveset) {
                if (tmMoves.contains(ml.move)) {
                    int tmIndex = tmMoves.indexOf(ml.move);
                    pkmnCompat[tmIndex + 1] = true;
                }
            }
        }
        this.setTMHMCompatibility(compat);
    }

    @Override
    public void ensureTMEvolutionSanity() {
        Map<Pokemon, boolean[]> compat = this.getTMHMCompatibility();
        // Don't do anything with the base, just copy upwards to ensure later evolutions retain learn compatibility
        copyUpEvolutionsHelper(pk -> {}, ((evFrom, evTo, toMonIsFinalEvo) -> {
            boolean[] fromCompat = compat.get(evFrom);
            boolean[] toCompat = compat.get(evTo);
            for (int i = 1; i < toCompat.length; i++) {
                toCompat[i] |= fromCompat[i];
            }
        }), null, true);
        this.setTMHMCompatibility(compat);
    }

    @Override
    public void fullHMCompatibility() {
        Map<Pokemon, boolean[]> compat = this.getTMHMCompatibility();
        int tmCount = this.getTMCount();
        for (boolean[] flags : compat.values()) {
            for (int i = tmCount + 1; i < flags.length; i++) {
                flags[i] = true;
            }
        }

        // Set the new compatibility
        this.setTMHMCompatibility(compat);
    }

    @Override
    public void copyTMCompatibilityToCosmeticFormes() {
        Map<Pokemon, boolean[]> compat = this.getTMHMCompatibility();

        for (Map.Entry<Pokemon, boolean[]> compatEntry : compat.entrySet()) {
            Pokemon pkmn = compatEntry.getKey();
            boolean[] flags = compatEntry.getValue();
            if (pkmn.actuallyCosmetic) {
                boolean[] baseFlags = compat.get(pkmn.baseForme);
                for (int i = 1; i < flags.length; i++) {
                    flags[i] = baseFlags[i];
                }
            }
        }

        this.setTMHMCompatibility(compat);
    }

    @Override
    public void randomizeMoveTutorMoves(Settings settings) {
        boolean noBroken = settings.isBlockBrokenTutorMoves();
        boolean preserveField = settings.isKeepFieldMoveTutors();
        double goodDamagingPercentage = settings.isTutorsForceGoodDamaging() ? settings.getTutorsGoodDamagingPercent() / 100.0 : 0;

        if (!this.hasMoveTutors()) {
            return;
        }

        // Pick some random Move Tutor moves, excluding TMs.
        List<Move> allMoves = this.getMoves();
        List<Integer> tms = this.getTMMoves();
        List<Integer> oldMTs = this.getMoveTutorMoves();
        int mtCount = oldMTs.size();
        //List<Integer> hms = this.getHMMoves();
        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? this.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(getMovesBannedFromLevelup());
        banned.addAll(this.getIllegalMoves());

        // field moves?
        List<Integer> fieldMoves = this.getFieldMoves();
        int preservedFieldMoveCount = 0;
        if (preserveField) {
            List<Integer> banExistingField = new ArrayList<>(oldMTs);
            banExistingField.retainAll(fieldMoves);
            preservedFieldMoveCount = banExistingField.size();
            banned.addAll(banExistingField);
        }

        // Determine which moves are pickable
        List<Move> usableMoves = new ArrayList<>(allMoves);
        usableMoves.remove(0); // remove null entry
        Set<Move> unusableMoves = new HashSet<>();
        Set<Move> unusableDamagingMoves = new HashSet<>();

        for (Move mv : usableMoves) {
            if (GlobalConstants.bannedRandomMoves[mv.number] || tms.contains(mv.number) /*|| hms.contains(mv.number)*/
                    || banned.contains(mv.number) || GlobalConstants.zMoves.contains(mv.number)) {
                unusableMoves.add(mv);
            } else if (GlobalConstants.bannedForDamagingMove[mv.number] || !mv.isGoodDamaging(perfectAccuracy)) {
                unusableDamagingMoves.add(mv);
            }
        }

        usableMoves.removeAll(unusableMoves);
        List<Move> usableDamagingMoves = new ArrayList<>(usableMoves);
        usableDamagingMoves.removeAll(unusableDamagingMoves);

        // pick (tmCount - preservedFieldMoveCount) moves
        List<Integer> pickedMoves = new ArrayList<>();

        // Force a certain amount of good damaging moves depending on the percentage
        int goodDamagingLeft = (int)Math.round(goodDamagingPercentage * (mtCount - preservedFieldMoveCount));

        for (int i = 0; i < mtCount - preservedFieldMoveCount; i++) {
            Move chosenMove;
            if (goodDamagingLeft > 0 && usableDamagingMoves.size() > 0) {
                chosenMove = usableDamagingMoves.get(random.nextInt(usableDamagingMoves.size()));
            } else {
                chosenMove = usableMoves.get(random.nextInt(usableMoves.size()));
            }
            pickedMoves.add(chosenMove.number);
            usableMoves.remove(chosenMove);
            usableDamagingMoves.remove(chosenMove);
            goodDamagingLeft--;
        }

        // shuffle the picked moves because high goodDamagingPercentage
        // will bias them towards early numbers otherwise

        Collections.shuffle(pickedMoves, random);

        // finally, distribute them as tutors
        int pickedMoveIndex = 0;
        List<Integer> newMTs = new ArrayList<>();

        for (Integer oldMT : oldMTs) {
            if (preserveField && fieldMoves.contains(oldMT)) {
                newMTs.add(oldMT);
            } else {
                newMTs.add(pickedMoves.get(pickedMoveIndex++));
            }
        }

        this.setMoveTutorMoves(newMTs);
    }

    @Override
    public void randomizeMoveTutorCompatibility(Settings settings) {
        boolean preferSameType = settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE;
        boolean followEvolutions = settings.isTutorFollowEvolutions();

        if (!this.hasMoveTutors()) {
            return;
        }
        // Get current compatibility
        Map<Pokemon, boolean[]> compat = this.getMoveTutorCompatibility();
        List<Integer> mts = this.getMoveTutorMoves();

        // Empty list
        List<Integer> priorityTutors = new ArrayList<Integer>();

        if (followEvolutions) {
            copyUpEvolutionsHelper(pk -> randomizePokemonMoveCompatibility(
                    pk, compat.get(pk), mts, priorityTutors, preferSameType),
                    (evFrom, evTo, toMonIsFinalEvo) ->  copyPokemonMoveCompatibilityUpEvolutions(
                            evFrom, evTo, compat.get(evFrom), compat.get(evTo), mts, preferSameType
                    ), null, true);
        }
        else {
            for (Map.Entry<Pokemon, boolean[]> compatEntry : compat.entrySet()) {
                randomizePokemonMoveCompatibility(compatEntry.getKey(), compatEntry.getValue(), mts, priorityTutors, preferSameType);
            }
        }
        // cases for certain alt forms
        List<Pokemon> pokemonList = this.getPokemon();
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.venusaur), pokemonList.get(Species.venusaurMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.charizard), pokemonList.get(Species.charizardMegaX));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.charizard), pokemonList.get(Species.charizardMegaY));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.blastoise), pokemonList.get(Species.blastoiseMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.beedrill), pokemonList.get(Species.beedrillMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pidgeot), pokemonList.get(Species.pidgeotMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.alakazam), pokemonList.get(Species.alakazamMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.slowbro), pokemonList.get(Species.slowbroMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gengar), pokemonList.get(Species.gengarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.kangaskhan), pokemonList.get(Species.kangaskhanMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pinsir), pokemonList.get(Species.pinsirMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gyarados), pokemonList.get(Species.gyaradosMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.aerodactyl), pokemonList.get(Species.aerodactylMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.mewtwo), pokemonList.get(Species.mewtwoMegaX));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.mewtwo), pokemonList.get(Species.mewtwoMegaY));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ampharos), pokemonList.get(Species.ampharosMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.steelix), pokemonList.get(Species.steelixMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scizor), pokemonList.get(Species.scizorMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.heracross), pokemonList.get(Species.heracrossMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.houndoom), pokemonList.get(Species.houndoomMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.tyranitar), pokemonList.get(Species.tyranitarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.sceptile), pokemonList.get(Species.sceptileMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.blaziken), pokemonList.get(Species.blazikenMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.swampert), pokemonList.get(Species.swampertMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gardevoir), pokemonList.get(Species.gardevoirMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.sableye), pokemonList.get(Species.sableyeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.mawile), pokemonList.get(Species.mawileMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.aggron), pokemonList.get(Species.aggronMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.medicham), pokemonList.get(Species.medichamMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.manectric), pokemonList.get(Species.manectricMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.sharpedo), pokemonList.get(Species.sharpedoMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.camerupt), pokemonList.get(Species.cameruptMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.altaria), pokemonList.get(Species.altariaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.banette), pokemonList.get(Species.banetteMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.absol), pokemonList.get(Species.absolMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.glalie), pokemonList.get(Species.glalieMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.salamence), pokemonList.get(Species.salamenceMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.metagross), pokemonList.get(Species.metagrossMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.latias), pokemonList.get(Species.latiasMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.latios), pokemonList.get(Species.latiosMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rayquaza), pokemonList.get(Species.rayquazaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.lopunny), pokemonList.get(Species.lopunnyMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.garchomp), pokemonList.get(Species.garchompMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.lucario), pokemonList.get(Species.lucarioMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.abomasnow), pokemonList.get(Species.abomasnowMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gallade), pokemonList.get(Species.galladeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.audino), pokemonList.get(Species.audinoMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.diancie), pokemonList.get(Species.diancieMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.kyogre), pokemonList.get(Species.kyogreP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.groudon), pokemonList.get(Species.groudonP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.clefable), pokemonList.get(Species.clefableMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.victreebel), pokemonList.get(Species.victreebelMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.starmie), pokemonList.get(Species.starmieMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.dragonite), pokemonList.get(Species.dragoniteMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meganium), pokemonList.get(Species.meganiumMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.feraligatr), pokemonList.get(Species.feraligatrMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.skarmory), pokemonList.get(Species.skarmoryMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.froslass), pokemonList.get(Species.froslassMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.emboar), pokemonList.get(Species.emboarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.excadrill), pokemonList.get(Species.excadrillMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scolipede), pokemonList.get(Species.scolipedeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scrafty), pokemonList.get(Species.scraftyMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.eelektross), pokemonList.get(Species.eelektrossMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.chandelure), pokemonList.get(Species.chandelureMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.chesnaught), pokemonList.get(Species.chesnaughtMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.delphox), pokemonList.get(Species.delphoxMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.greninja), pokemonList.get(Species.greninjaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pyroar), pokemonList.get(Species.pyroarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.floetteE), pokemonList.get(Species.floetteMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.malamar), pokemonList.get(Species.malamarMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.barbaracle), pokemonList.get(Species.barbaracleMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.dragalge), pokemonList.get(Species.dragalgeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.hawlucha), pokemonList.get(Species.hawluchaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zygardeC), pokemonList.get(Species.zygardeMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.drampa), pokemonList.get(Species.drampaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.falinks), pokemonList.get(Species.falinksMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.raichu), pokemonList.get(Species.raichuMegaX));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.raichu), pokemonList.get(Species.raichuMegaY));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.chimecho), pokemonList.get(Species.chimechoMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.absol), pokemonList.get(Species.absolMegaZ));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.staraptor), pokemonList.get(Species.staraptorMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.garchomp), pokemonList.get(Species.garchompMegaZ));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.lucario), pokemonList.get(Species.lucarioMegaZ));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.heatran), pokemonList.get(Species.heatranMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.darkrai), pokemonList.get(Species.darkraiMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.golurk), pokemonList.get(Species.golurkMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meowstic), pokemonList.get(Species.meowsticMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meowsticF), pokemonList.get(Species.meowsticFMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.crabominable), pokemonList.get(Species.crabominableMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.golisopod), pokemonList.get(Species.golisopodMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.magearna), pokemonList.get(Species.magearnaMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zeraora), pokemonList.get(Species.zeraoraMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.scovillain), pokemonList.get(Species.scovillainMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.glimmora), pokemonList.get(Species.glimmoraMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.tatsugiri), pokemonList.get(Species.tatsugiriMega));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.baxcalibur), pokemonList.get(Species.baxcaliburMega));

        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pikachu), pokemonList.get(Species.pikachuCap));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pichu), pokemonList.get(Species.pichuSpiky));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.castform), pokemonList.get(Species.castformF));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.castform), pokemonList.get(Species.castformW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.castform), pokemonList.get(Species.castformI));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.burmy), pokemonList.get(Species.burmyS));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.burmy), pokemonList.get(Species.burmyT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.cherrim), pokemonList.get(Species.cherrimS));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomH));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomFr));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomFa));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.rotom), pokemonList.get(Species.rotomM));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.dialga), pokemonList.get(Species.dialgaO));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.palkia), pokemonList.get(Species.palkiaO));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.giratina), pokemonList.get(Species.giratinaO));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.basculin), pokemonList.get(Species.basculinB));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.tornadus), pokemonList.get(Species.tornadusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.thundurus), pokemonList.get(Species.thundurusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.landorus), pokemonList.get(Species.landorusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.meloetta), pokemonList.get(Species.meloettaP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.greninja), pokemonList.get(Species.greninjaB));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.greninja), pokemonList.get(Species.greninjaA));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.aegislash), pokemonList.get(Species.aegislashB));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pumpkaboo), pokemonList.get(Species.pumpkabooSmall));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pumpkaboo), pokemonList.get(Species.pumpkabooLarge));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.pumpkaboo), pokemonList.get(Species.pumpkabooSuper));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gourgeist), pokemonList.get(Species.gourgeistSmall));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gourgeist), pokemonList.get(Species.gourgeistLarge));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gourgeist), pokemonList.get(Species.gourgeistSuper));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zygarde), pokemonList.get(Species.zygarde10));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zygarde), pokemonList.get(Species.zygardeC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.oricorio), pokemonList.get(Species.oricorioE));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.oricorio), pokemonList.get(Species.oricorioP));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.oricorio), pokemonList.get(Species.oricorioG));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.wishiwashi), pokemonList.get(Species.wishiwashiS));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.minior), pokemonList.get(Species.miniorC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.necrozma), pokemonList.get(Species.necrozmaDM));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.necrozma), pokemonList.get(Species.necrozmaDW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.necrozma), pokemonList.get(Species.necrozmaU));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.eiscue), pokemonList.get(Species.eiscueN));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.morpeko), pokemonList.get(Species.morpekoH));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zacian), pokemonList.get(Species.zacianC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.zamazenta), pokemonList.get(Species.zamazentaC));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.eternatus), pokemonList.get(Species.eternatusE));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.basculegion), pokemonList.get(Species.basculegionF));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.enamorus), pokemonList.get(Species.enamorusT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.squawkabilly), pokemonList.get(Species.squawkabillyW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.palafin), pokemonList.get(Species.palafinH));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.gimmighoul), pokemonList.get(Species.gimmighoulR));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ogerpon), pokemonList.get(Species.ogerponF));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ogerpon), pokemonList.get(Species.ogerponR));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.ogerpon), pokemonList.get(Species.ogerponW));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.terapagos), pokemonList.get(Species.terapagosT));
        copyTMHMTutorCompatibility(compat, pokemonList.get(Species.terapagos), pokemonList.get(Species.terapagosS));

        // Set the new compatibility
        this.setMoveTutorCompatibility(compat);
    }

    @Override
    public void fullMoveTutorCompatibility() {
        if (!this.hasMoveTutors()) {
            return;
        }
        Map<Pokemon, boolean[]> compat = this.getMoveTutorCompatibility();
        for (Map.Entry<Pokemon, boolean[]> compatEntry : compat.entrySet()) {
            boolean[] flags = compatEntry.getValue();
            for (int i = 1; i < flags.length; i++) {
                flags[i] = true;
            }
        }
        this.setMoveTutorCompatibility(compat);
    }

    @Override
    public void ensureMoveTutorCompatSanity() {
        if (!this.hasMoveTutors()) {
            return;
        }
        // if a pokemon learns a move in its moveset
        // and there is a tutor of that move, make sure
        // that tutor can be learned.
        Map<Pokemon, boolean[]> compat = this.getMoveTutorCompatibility();
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();
        List<Integer> mtMoves = this.getMoveTutorMoves();
        for (Pokemon pkmn : compat.keySet()) {
            List<MoveLearnt> moveset = movesets.get(pkmn.number);
            boolean[] pkmnCompat = compat.get(pkmn);
            for (MoveLearnt ml : moveset) {
                if (mtMoves.contains(ml.move)) {
                    int mtIndex = mtMoves.indexOf(ml.move);
                    pkmnCompat[mtIndex + 1] = true;
                }
            }
        }
        this.setMoveTutorCompatibility(compat);
    }

    @Override
    public void ensureMoveTutorEvolutionSanity() {
        if (!this.hasMoveTutors()) {
            return;
        }
        Map<Pokemon, boolean[]> compat = this.getMoveTutorCompatibility();
        // Don't do anything with the base, just copy upwards to ensure later evolutions retain learn compatibility
        copyUpEvolutionsHelper(pk -> {}, ((evFrom, evTo, toMonIsFinalEvo) -> {
            boolean[] fromCompat = compat.get(evFrom);
            boolean[] toCompat = compat.get(evTo);
            for (int i = 1; i < toCompat.length; i++) {
                toCompat[i] |= fromCompat[i];
            }
        }), null, true);
        this.setMoveTutorCompatibility(compat);
    }

    @Override
    public void copyMoveTutorCompatibilityToCosmeticFormes() {
        Map<Pokemon, boolean[]> compat = this.getMoveTutorCompatibility();

        for (Map.Entry<Pokemon, boolean[]> compatEntry : compat.entrySet()) {
            Pokemon pkmn = compatEntry.getKey();
            boolean[] flags = compatEntry.getValue();
            if (pkmn.actuallyCosmetic) {
                boolean[] baseFlags = compat.get(pkmn.baseForme);
                for (int i = 1; i < flags.length; i++) {
                    flags[i] = baseFlags[i];
                }
            }
        }

        this.setMoveTutorCompatibility(compat);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void randomizeTrainerNames(Settings settings) {
        CustomNamesSet customNames = settings.getCustomNames();

        if (!this.canChangeTrainerText()) {
            return;
        }

        // index 0 = singles, 1 = doubles
        List<String>[] allTrainerNames = new List[] { new ArrayList<String>(), new ArrayList<String>() };
        Map<Integer, List<String>> trainerNamesByLength[] = new Map[] { new TreeMap<Integer, List<String>>(),
                new TreeMap<Integer, List<String>>() };
        
        List<String> repeatedTrainerNames = Arrays.asList(new String[] { "GRUNT", "EXECUTIVE", "SHADOW", "ADMIN", "GOON", "EMPLOYEE" });

        // Read name lists
        for (String trainername : customNames.getTrainerNames()) {
            int len = this.internalStringLength(trainername);
            if (len <= 15) {
                allTrainerNames[0].add(trainername);
                if (trainerNamesByLength[0].containsKey(len)) {
                    trainerNamesByLength[0].get(len).add(trainername);
                } else {
                    List<String> namesOfThisLength = new ArrayList<>();
                    namesOfThisLength.add(trainername);
                    trainerNamesByLength[0].put(len, namesOfThisLength);
                }
            }
        }

        for (String trainername : customNames.getDoublesTrainerNames()) {
            int len = this.internalStringLength(trainername);
            if (len <= 15) {
                allTrainerNames[1].add(trainername);
                if (trainerNamesByLength[1].containsKey(len)) {
                    trainerNamesByLength[1].get(len).add(trainername);
                } else {
                    List<String> namesOfThisLength = new ArrayList<>();
                    namesOfThisLength.add(trainername);
                    trainerNamesByLength[1].put(len, namesOfThisLength);
                }
            }
        }

        // Get the current trainer names data
        List<String> currentTrainerNames = this.getTrainerNames();
        if (currentTrainerNames.size() == 0) {
            // RBY have no trainer names
            return;
        }
        TrainerNameMode mode = this.trainerNameMode();
        int maxLength = this.maxTrainerNameLength();
        int totalMaxLength = this.maxSumOfTrainerNameLengths();

        boolean success = false;
        int tries = 0;

        // Init the translation map and new list
        Map<String, String> translation = new HashMap<>();
        List<String> newTrainerNames = new ArrayList<>();
        List<Integer> tcNameLengths = this.getTCNameLengthsByTrainer();

        // loop until we successfully pick names that fit
        // should always succeed first attempt except for gen2.
        while (!success && tries < 10000) {
            success = true;
            translation.clear();
            newTrainerNames.clear();
            int totalLength = 0;

            // Start choosing
            int tnIndex = -1;
            for (String trainerName : currentTrainerNames) {
                tnIndex++;
                if (translation.containsKey(trainerName) && !repeatedTrainerNames.contains(trainerName.toUpperCase())) {
                    // use an already picked translation
                    newTrainerNames.add(translation.get(trainerName));
                    totalLength += this.internalStringLength(translation.get(trainerName));
                } else {
                    int idx = trainerName.contains("&") ? 1 : 0;
                    List<String> pickFrom = allTrainerNames[idx];
                    int intStrLen = this.internalStringLength(trainerName);
                    if (mode == TrainerNameMode.SAME_LENGTH) {
                        pickFrom = trainerNamesByLength[idx].get(intStrLen);
                    }
                    String changeTo = trainerName;
                    int ctl = intStrLen;
                    if (pickFrom != null && pickFrom.size() > 0 && intStrLen > 0) {
                        int innerTries = 0;
                        changeTo = pickFrom.get(this.cosmeticRandom.nextInt(pickFrom.size()));
                        ctl = this.internalStringLength(changeTo);
                        while ((mode == TrainerNameMode.MAX_LENGTH && ctl > maxLength)
                                || (mode == TrainerNameMode.MAX_LENGTH_WITH_CLASS && ctl + tcNameLengths.get(tnIndex) > maxLength)) {
                            innerTries++;
                            if (innerTries == 100) {
                                changeTo = trainerName;
                                ctl = intStrLen;
                                break;
                            }
                            changeTo = pickFrom.get(this.cosmeticRandom.nextInt(pickFrom.size()));
                            ctl = this.internalStringLength(changeTo);
                        }
                    }
                    translation.put(trainerName, changeTo);
                    newTrainerNames.add(changeTo);
                    totalLength += ctl;
                }

                if (totalLength > totalMaxLength) {
                    success = false;
                    tries++;
                    break;
                }
            }
        }

        if (!success) {
            throw new RandomizationException("Could not randomize trainer names in a reasonable amount of attempts."
                    + "\nPlease add some shorter names to your custom trainer names.");
        }

        // Done choosing, save
        this.setTrainerNames(newTrainerNames);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void randomizeTrainerClassNames(Settings settings) {
        CustomNamesSet customNames = settings.getCustomNames();

        if (!this.canChangeTrainerText()) {
            return;
        }

        // index 0 = singles, index 1 = doubles
        List<String> allTrainerClasses[] = new List[] { new ArrayList<String>(), new ArrayList<String>() };
        Map<Integer, List<String>> trainerClassesByLength[] = new Map[] { new HashMap<Integer, List<String>>(),
                new HashMap<Integer, List<String>>() };

        // Read names data
        for (String trainerClassName : customNames.getTrainerClasses()) {
            allTrainerClasses[0].add(trainerClassName);
            int len = this.internalStringLength(trainerClassName);
            if (trainerClassesByLength[0].containsKey(len)) {
                trainerClassesByLength[0].get(len).add(trainerClassName);
            } else {
                List<String> namesOfThisLength = new ArrayList<>();
                namesOfThisLength.add(trainerClassName);
                trainerClassesByLength[0].put(len, namesOfThisLength);
            }
        }

        for (String trainerClassName : customNames.getDoublesTrainerClasses()) {
            allTrainerClasses[1].add(trainerClassName);
            int len = this.internalStringLength(trainerClassName);
            if (trainerClassesByLength[1].containsKey(len)) {
                trainerClassesByLength[1].get(len).add(trainerClassName);
            } else {
                List<String> namesOfThisLength = new ArrayList<>();
                namesOfThisLength.add(trainerClassName);
                trainerClassesByLength[1].put(len, namesOfThisLength);
            }
        }

        // Get the current trainer names data
        List<String> currentClassNames = this.getTrainerClassNames();
        boolean mustBeSameLength = this.fixedTrainerClassNamesLength();
        int maxLength = this.maxTrainerClassNameLength();

        // Init the translation map and new list
        Map<String, String> translation = new HashMap<>();
        List<String> newClassNames = new ArrayList<>();

        int numTrainerClasses = currentClassNames.size();
        List<Integer> doublesClasses = this.getDoublesTrainerClasses();

        // Start choosing
        for (int i = 0; i < numTrainerClasses; i++) {
            String trainerClassName = currentClassNames.get(i);
            if (translation.containsKey(trainerClassName)) {
                // use an already picked translation
                newClassNames.add(translation.get(trainerClassName));
            } else {
                int idx = doublesClasses.contains(i) ? 1 : 0;
                List<String> pickFrom = allTrainerClasses[idx];
                int intStrLen = this.internalStringLength(trainerClassName);
                if (mustBeSameLength) {
                    pickFrom = trainerClassesByLength[idx].get(intStrLen);
                }
                String changeTo = trainerClassName;
                if (pickFrom != null && pickFrom.size() > 0) {
                    changeTo = pickFrom.get(this.cosmeticRandom.nextInt(pickFrom.size()));
                    while (changeTo.length() > maxLength) {
                        changeTo = pickFrom.get(this.cosmeticRandom.nextInt(pickFrom.size()));
                    }
                }
                translation.put(trainerClassName, changeTo);
                newClassNames.add(changeTo);
            }
        }

        // Done choosing, save
        this.setTrainerClassNames(newClassNames);
    }

    @Override
    public void randomizeWildHeldItems(Settings settings) {
        boolean banBadItems = settings.isBanBadRandomWildPokemonHeldItems();

        List<Pokemon> pokemon = allPokemonInclFormesWithoutNull();
        ItemList possibleItems = banBadItems ? this.getNonBadItems() : this.getAllowedItems();
        for (Pokemon pk : pokemon) {
            if (pk.guaranteedHeldItem == -1 && pk.commonHeldItem == -1 && pk.rareHeldItem == -1
                    && pk.darkGrassHeldItem == -1) {
                // No held items at all, abort
                return;
            }
            boolean canHaveDarkGrass = pk.darkGrassHeldItem != -1;
            if (pk.guaranteedHeldItem != -1) {
                // Guaranteed held items are supported.
                if (pk.guaranteedHeldItem > 0) {
                    // Currently have a guaranteed item
                    double decision = this.random.nextDouble();
                    if (decision < 0.9) {
                        // Stay as guaranteed
                        canHaveDarkGrass = false;
                        pk.guaranteedHeldItem = possibleItems.randomItem(this.random);
                    } else {
                        // Change to 25% or 55% chance
                        pk.guaranteedHeldItem = 0;
                        pk.commonHeldItem = possibleItems.randomItem(this.random);
                        pk.rareHeldItem = possibleItems.randomItem(this.random);
                        while (pk.rareHeldItem == pk.commonHeldItem) {
                            pk.rareHeldItem = possibleItems.randomItem(this.random);
                        }
                    }
                } else {
                    // No guaranteed item atm
                    double decision = this.random.nextDouble();
                    if (decision < 0.5) {
                        // No held item at all
                        pk.commonHeldItem = 0;
                        pk.rareHeldItem = 0;
                    } else if (decision < 0.65) {
                        // Just a rare item
                        pk.commonHeldItem = 0;
                        pk.rareHeldItem = possibleItems.randomItem(this.random);
                    } else if (decision < 0.8) {
                        // Just a common item
                        pk.commonHeldItem = possibleItems.randomItem(this.random);
                        pk.rareHeldItem = 0;
                    } else if (decision < 0.95) {
                        // Both a common and rare item
                        pk.commonHeldItem = possibleItems.randomItem(this.random);
                        pk.rareHeldItem = possibleItems.randomItem(this.random);
                        while (pk.rareHeldItem == pk.commonHeldItem) {
                            pk.rareHeldItem = possibleItems.randomItem(this.random);
                        }
                    } else {
                        // Guaranteed item
                        canHaveDarkGrass = false;
                        pk.guaranteedHeldItem = possibleItems.randomItem(this.random);
                        pk.commonHeldItem = 0;
                        pk.rareHeldItem = 0;
                    }
                }
            } else {
                // Code for no guaranteed items
                double decision = this.random.nextDouble();
                if (decision < 0.5) {
                    // No held item at all
                    pk.commonHeldItem = 0;
                    pk.rareHeldItem = 0;
                } else if (decision < 0.65) {
                    // Just a rare item
                    pk.commonHeldItem = 0;
                    pk.rareHeldItem = possibleItems.randomItem(this.random);
                } else if (decision < 0.8) {
                    // Just a common item
                    pk.commonHeldItem = possibleItems.randomItem(this.random);
                    pk.rareHeldItem = 0;
                } else {
                    // Both a common and rare item
                    pk.commonHeldItem = possibleItems.randomItem(this.random);
                    pk.rareHeldItem = possibleItems.randomItem(this.random);
                    while (pk.rareHeldItem == pk.commonHeldItem) {
                        pk.rareHeldItem = possibleItems.randomItem(this.random);
                    }
                }
            }

            if (canHaveDarkGrass) {
                double dgDecision = this.random.nextDouble();
                if (dgDecision < 0.5) {
                    // Yes, dark grass item
                    pk.darkGrassHeldItem = possibleItems.randomItem(this.random);
                } else {
                    pk.darkGrassHeldItem = 0;
                }
            } else if (pk.darkGrassHeldItem != -1) {
                pk.darkGrassHeldItem = 0;
            }
        }

    }

    @Override
    public void randomizeStarterHeldItems(Settings settings) {
        boolean banBadItems = settings.isBanBadRandomStarterHeldItems();

        List<Integer> oldHeldItems = this.getStarterHeldItems();
        List<Integer> newHeldItems = new ArrayList<>();
        ItemList possibleItems = banBadItems ? this.getNonBadItems() : this.getAllowedItems();
        for (int i = 0; i < oldHeldItems.size(); i++) {
            newHeldItems.add(possibleItems.randomItem(this.random));
        }
        this.setStarterHeldItems(newHeldItems);
    }

    @Override
    public void shuffleFieldItems() {
        List<Integer> currentItems = this.getRegularFieldItems();
        List<Integer> currentTMs = this.getCurrentFieldTMs();

        Collections.shuffle(currentItems, this.random);
        Collections.shuffle(currentTMs, this.random);

        this.setRegularFieldItems(currentItems);
        this.setFieldTMs(currentTMs);
    }

    @Override
    public void randomizeFieldItems(Settings settings) {
        boolean banBadItems = settings.isBanBadRandomFieldItems();
        boolean distributeItemsControl = settings.getFieldItemsMod() == Settings.FieldItemsMod.RANDOM_EVEN;
        boolean uniqueItems = !settings.isBalanceShopPrices();

        ItemList possibleItems = banBadItems ? this.getNonBadItems().copy() : this.getAllowedItems().copy();
        List<Integer> currentItems = this.getRegularFieldItems();
        List<Integer> currentTMs = this.getCurrentFieldTMs();
        List<Integer> requiredTMs = this.getRequiredFieldTMs();
        List<Integer> uniqueNoSellItems = this.getUniqueNoSellItems();
        // System.out.println("distributeItemsControl: "+ distributeItemsControl);

        int fieldItemCount = currentItems.size();
        int fieldTMCount = currentTMs.size();
        int reqTMCount = requiredTMs.size();
        int totalTMCount = this.getTMCount();

        List<Integer> newItems = new ArrayList<>();
        List<Integer> newTMs = new ArrayList<>(requiredTMs);

        // List<Integer> chosenItems = new ArrayList<Integer>(); // collecting chosenItems for later process

        if (distributeItemsControl) {
            for (int i = 0; i < fieldItemCount; i++) {
                int chosenItem = possibleItems.randomNonTM(this.random);
                int iterNum = 0;
                while ((this.getItemPlacementHistory(chosenItem) > this.getItemPlacementAverage()) && iterNum < 100) {
                    chosenItem = possibleItems.randomNonTM(this.random);
                    iterNum +=1;
                }
                newItems.add(chosenItem);
                if (uniqueItems && uniqueNoSellItems.contains(chosenItem)) {
                    possibleItems.banSingles(chosenItem);
                } else {
                    this.setItemPlacementHistory(chosenItem);
                }
            }
        } else {
            for (int i = 0; i < fieldItemCount; i++) {
                int chosenItem = possibleItems.randomNonTM(this.random);
                newItems.add(chosenItem);
                if (uniqueItems && uniqueNoSellItems.contains(chosenItem)) {
                    possibleItems.banSingles(chosenItem);
                }
            }
        }

        for (int i = reqTMCount; i < fieldTMCount; i++) {
            while (true) {
                int tm = this.random.nextInt(totalTMCount) + 1;
                if (!newTMs.contains(tm)) {
                    newTMs.add(tm);
                    break;
                }
            }
        }


        Collections.shuffle(newItems, this.random);
        Collections.shuffle(newTMs, this.random);

        this.setRegularFieldItems(newItems);
        this.setFieldTMs(newTMs);
    }

    @Override
    public void randomizeIngameTrades(Settings settings) {
        boolean randomizeRequest = settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED;
        boolean randomNickname = settings.isRandomizeInGameTradesNicknames();
        boolean randomOT = settings.isRandomizeInGameTradesOTs();
        boolean randomStats = settings.isRandomizeInGameTradesIVs();
        boolean randomItem = settings.isRandomizeInGameTradesItems();
        CustomNamesSet customNames = settings.getCustomNames();

        checkPokemonRestrictions();
        // Process trainer names
        List<String> trainerNames = new ArrayList<>();
        // Check for the file
        if (randomOT) {
            int maxOT = this.maxTradeOTNameLength();
            for (String trainername : customNames.getTrainerNames()) {
                int len = this.internalStringLength(trainername);
                if (len <= maxOT && !trainerNames.contains(trainername)) {
                    trainerNames.add(trainername);
                }
            }
        }

        // Process nicknames
        List<String> nicknames = new ArrayList<>();
        // Check for the file
        if (randomNickname) {
            int maxNN = this.maxTradeNicknameLength();
            for (String nickname : customNames.getPokemonNicknames()) {
                int len = this.internalStringLength(nickname);
                if (len <= maxNN && !nicknames.contains(nickname)) {
                    nicknames.add(nickname);
                }
            }
        }

        // get old trades
        List<IngameTrade> trades = this.getIngameTrades();
        List<Pokemon> usedRequests = new ArrayList<>();
        List<Pokemon> usedGivens = new ArrayList<>();
        List<String> usedOTs = new ArrayList<>();
        List<String> usedNicknames = new ArrayList<>();
        ItemList possibleItems = this.getAllowedItems();

        int nickCount = nicknames.size();
        int trnameCount = trainerNames.size();

        for (IngameTrade trade : trades) {
            // pick new given pokemon
            Pokemon oldgiven = trade.givenPokemon;
            Pokemon given = this.randomPokemon();
            while (usedGivens.contains(given)) {
                given = this.randomPokemon();
            }
            usedGivens.add(given);
            trade.givenPokemon = given;

            // requested pokemon?
            if (oldgiven == trade.requestedPokemon) {
                // preserve trades for the same pokemon
                trade.requestedPokemon = given;
            } else if (randomizeRequest) {
                if (trade.requestedPokemon != null) {
                    Pokemon request = this.randomPokemon();
                    while (usedRequests.contains(request) || request == given) {
                        request = this.randomPokemon();
                    }
                    usedRequests.add(request);
                    trade.requestedPokemon = request;
                }
            }

            // nickname?
            if (randomNickname && nickCount > usedNicknames.size()) {
                String nickname = nicknames.get(this.random.nextInt(nickCount));
                while (usedNicknames.contains(nickname)) {
                    nickname = nicknames.get(this.random.nextInt(nickCount));
                }
                usedNicknames.add(nickname);
                trade.nickname = nickname;
            } else if (trade.nickname.equalsIgnoreCase(oldgiven.name)) {
                // change the name for sanity
                trade.nickname = trade.givenPokemon.name;
            }

            if (randomOT && trnameCount > usedOTs.size()) {
                String ot = trainerNames.get(this.random.nextInt(trnameCount));
                while (usedOTs.contains(ot)) {
                    ot = trainerNames.get(this.random.nextInt(trnameCount));
                }
                usedOTs.add(ot);
                trade.otName = ot;
                trade.otId = this.random.nextInt(65536);
            }

            if (randomStats) {
                int maxIV = this.hasDVs() ? 16 : 32;
                for (int i = 0; i < trade.ivs.length; i++) {
                    trade.ivs[i] = this.random.nextInt(maxIV);
                }
            }

            if (randomItem) {
                trade.item = possibleItems.randomItem(this.random);
            }
        }

        // things that the game doesn't support should just be ignored
        this.setIngameTrades(trades);
    }

    @Override
    public void condenseLevelEvolutions(int maxLevel, int maxIntermediateLevel) {
        List<Pokemon> allPokemon = this.getPokemon();
        // search for level evolutions
        for (Pokemon pk : allPokemon) {
            if (pk != null) {
                for (Evolution checkEvo : pk.evolutionsFrom) {
                    if (checkEvo.type.usesLevel()) {
                        // If evo is intermediate and too high, bring it down
                        // Else if it's just too high, bring it down
                        if (checkEvo.extraInfo > maxIntermediateLevel && checkEvo.to.evolutionsFrom.size() > 0) {
                            checkEvo.extraInfo = maxIntermediateLevel;
                            addEvoUpdateCondensed(easierEvolutionUpdates, checkEvo, false);
                        } else if (checkEvo.extraInfo > maxLevel) {
                            checkEvo.extraInfo = maxLevel;
                            addEvoUpdateCondensed(easierEvolutionUpdates, checkEvo, false);
                        }
                    }
                    if (checkEvo.type == EvolutionType.LEVEL_UPSIDE_DOWN) {
                        checkEvo.type = EvolutionType.LEVEL;
                        addEvoUpdateCondensed(easierEvolutionUpdates, checkEvo, false);
                    }
                }
            }
        }

    }

    @Override
    public Set<EvolutionUpdate> getImpossibleEvoUpdates() {
        return impossibleEvolutionUpdates;
    }

    @Override
    public Set<EvolutionUpdate> getEasierEvoUpdates() {
        return easierEvolutionUpdates;
    }

    @Override
    public Set<EvolutionUpdate> getTimeBasedEvoUpdates() {
        return timeBasedEvolutionUpdates;
    }

    @Override
    public void randomizeEvolutions(Settings settings) {
        boolean similarStrength = settings.isEvosSimilarStrength();
        boolean sameType = settings.isEvosSameTyping();
        boolean limitToThreeStages = settings.isEvosMaxThreeStages();
        boolean forceChange = settings.isEvosForceChange();
        boolean evoTypeTarget = settings.isEvosMatchPostEvoTyping();
//        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        checkPokemonRestrictions();
        List<Pokemon> pokemonPool = new ArrayList<>(mainPokemonList);
        List<Pokemon> actuallyCosmeticPokemonPool = new ArrayList<>();
        int stageLimit = limitToThreeStages ? 3 : 10;

        List<Pokemon> banned = this.getBannedFormesForPlayerPokemon();
        List<Pokemon> pokemonList = this.getPokemon();
        banned.add(pokemonList.get(Species.pikachuCap));
        banned.add(pokemonList.get(Species.pikachuPartner));
        banned.add(pokemonList.get(Species.eeveePartner));
        banned.add(pokemonList.get(Species.pichuSpiky));
        banned.add(pokemonList.get(Species.burmyS));
        banned.add(pokemonList.get(Species.burmyT));
        banned.add(pokemonList.get(Species.cherrimS));
        banned.add(pokemonList.get(Species.shayminS));
        banned.add(pokemonList.get(Species.greninjaB));
        banned.add(pokemonList.get(Species.hoopaU));
        banned.add(pokemonList.get(Species.morpekoH));
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);

            banned.add(pokemonList.get(Species.castformF));
            banned.add(pokemonList.get(Species.castformW));
            banned.add(pokemonList.get(Species.castformI));
        }
//        if (banIrregularAltFormes) {
//            banned.addAll(getIrregularFormes());
//        }

        for (int i = 0; i < pokemonPool.size(); i++) {
            Pokemon pk = pokemonPool.get(i);
            if (pk.actuallyCosmetic) {
                pokemonPool.remove(pk);
                i--;
                actuallyCosmeticPokemonPool.add(pk);
            }
        }

        // Cache old evolutions for data later
        Map<Pokemon, List<Evolution>> originalEvos = new HashMap<>();
        for (Pokemon pk : pokemonPool) {
            originalEvos.put(pk, new ArrayList<>(pk.evolutionsFrom));
        }

        Set<EvolutionPair> newEvoPairs = new HashSet<>();
        Set<EvolutionPair> oldEvoPairs = new HashSet<>();

        if (forceChange) {
            for (Pokemon pk : pokemonPool) {
                for (Evolution ev : pk.evolutionsFrom) {
                    oldEvoPairs.add(new EvolutionPair(ev.from, ev.to));
                    if (ev.from.number == Species.doublade) {
                        int oppositeVersionLegendary = ev.to.number == Species.aegislash ? Species.aegislashB : Species.aegislash;
                        Pokemon toPkmn = findPokemonInPoolWithSpeciesID(pokemonPool, oppositeVersionLegendary);
                        if (toPkmn != null) {
                            oldEvoPairs.add(new EvolutionPair(ev.from, toPkmn));
                        }
                    }
                }
            }
            // case for burmy
            int[] burmyFormes = { Species.burmy, Species.burmyS, Species.burmyT };
            int[] wormadamFormes = { Species.wormadam, Species.wormadamS, Species.wormadamT };
            for (int j : burmyFormes) {
                Pokemon fromPkmn = findPokemonInPoolWithSpeciesID(pokemonPool, j);
                if (fromPkmn == null) continue;
                for (int k : wormadamFormes) {
                    Pokemon toPkmn = findPokemonInPoolWithSpeciesID(pokemonPool, k);
                    if (toPkmn != null) {
                        oldEvoPairs.add(new EvolutionPair(fromPkmn, toPkmn));
                    }
                }
            }
            // case for pumpkaboo
            int[] pumpkabooFormes = { Species.pumpkaboo, Species.pumpkabooSmall, Species.pumpkabooLarge, Species.pumpkabooSuper };
            int[] gourgeistFormes = { Species.gourgeist, Species.gourgeistSmall, Species.gourgeistLarge, Species.gourgeistSuper };
            for (int j : pumpkabooFormes) {
                Pokemon fromPkmn = findPokemonInPoolWithSpeciesID(pokemonPool, j);
                if (fromPkmn == null) continue;
                for (int k : gourgeistFormes) {
                    Pokemon toPkmn = findPokemonInPoolWithSpeciesID(pokemonPool, k);
                    if (toPkmn != null) {
                        oldEvoPairs.add(new EvolutionPair(fromPkmn, toPkmn));
                    }
                }
            }
        }

        List<Pokemon> replacements = new ArrayList<>();

        int loops = 0;
        while (loops < 1) {
            // Setup for this loop.
            boolean hadError = false;
            for (Pokemon pk : pokemonPool) {
                pk.evolutionsFrom.clear();
                pk.evolutionsTo.clear();
            }
            newEvoPairs.clear();

            // Shuffle pokemon list so the results aren't overly predictable.
            Collections.shuffle(pokemonPool, this.random);

            for (Pokemon fromPK : pokemonPool) {
                List<Evolution> oldEvos = originalEvos.get(fromPK);
                for (Evolution ev : oldEvos) {
                    // Pick a Pokemon as replacement
                    replacements.clear();

                    List<Pokemon> chosenList = mainPokemonList;
                    // Step 1: base filters
                    for (Pokemon pk : chosenList) {
                        // Prevent evolving into oneself (mandatory)
                        if (pk == fromPK) {
                            continue;
                        }

                        // Force same EXP curve (mandatory)
                        if (pk.growthCurve != fromPK.growthCurve) {
                            continue;
                        }

                        // Prevent evolving into banned Pokemon (mandatory)
                        if (banned.contains(pk)) {
                            continue;
                        }

                        EvolutionPair ep = new EvolutionPair(fromPK, pk);
                        // Prevent split evos choosing the same Pokemon
                        // (mandatory)
                        if (newEvoPairs.contains(ep)) {
                            continue;
                        }

                        // Prevent evolving into old thing if flagged
                        if (forceChange && oldEvoPairs.contains(ep)) {
                            continue;
                        }

                        // Prevent evolution that causes cycle (mandatory)
                        if (evoCycleCheck(fromPK, pk)) {
                            continue;
                        }

                        // Prevent evolution that exceeds stage limit
                        Evolution tempEvo = new Evolution(fromPK, pk, false, EvolutionType.NONE, 0);
                        fromPK.evolutionsFrom.add(tempEvo);
                        pk.evolutionsTo.add(tempEvo);
                        boolean exceededLimit = false;

                        Set<Pokemon> related = relatedPokemon(fromPK);

                        for (Pokemon pk2 : related) {
                            int numPreEvos = numPreEvolutions(pk2, stageLimit);
                            if (numPreEvos >= stageLimit) {
                                exceededLimit = true;
                                break;
                            } else if (numPreEvos == stageLimit - 1 && pk2.evolutionsFrom.size() == 0
                                    && originalEvos.get(pk2).size() > 0) {
                                exceededLimit = true;
                                break;
                            }
                        }

                        fromPK.evolutionsFrom.remove(tempEvo);
                        pk.evolutionsTo.remove(tempEvo);

                        if (exceededLimit) {
                            continue;
                        }

                        // Passes everything, add as a candidate.
                        replacements.add(pk);
                    }

                    // If we don't have any candidates after Step 1, severe
                    // failure
                    // exit out of this loop and try again from scratch
                    if (replacements.size() == 0) {
                        hadError = true;
                        break;
                    }

                    // Step 2: filter by type, if needed
                    if (replacements.size() > 1 && sameType) {
                        Set<Pokemon> includeType = new HashSet<>();
                        for (Pokemon pk : replacements) {
                            // NATDEX: target evo type can be that of the vanilla evolution with a setting
                            if (evoTypeTarget) {
                                if (pk.primaryType == ev.to.primaryType
                                        || (ev.to.secondaryType != null && pk.primaryType == ev.to.secondaryType)
                                        || (pk.secondaryType != null && pk.secondaryType == ev.to.primaryType)
                                        || (ev.to.secondaryType != null && pk.secondaryType != null && pk.secondaryType == ev.to.secondaryType)) {
                                    includeType.add(pk);
                                }
                            } else {
                                // Special case for Eevee
                                if (fromPK.number == Species.eevee) {
                                    if (pk.primaryType == ev.to.primaryType
                                            || (pk.secondaryType != null) && pk.secondaryType == ev.to.primaryType) {
                                        includeType.add(pk);
                                    }
                                } else if (pk.primaryType == fromPK.primaryType
                                        || (fromPK.secondaryType != null && pk.primaryType == fromPK.secondaryType)
                                        || (pk.secondaryType != null && pk.secondaryType == fromPK.primaryType)
                                        || (fromPK.secondaryType != null && pk.secondaryType != null && pk.secondaryType == fromPK.secondaryType)) {
                                    includeType.add(pk);
                                }
                            }
                        }

                        if (includeType.size() != 0) {
                            replacements.retainAll(includeType);
                        }
                    }

                    if (!alreadyPicked.containsAll(replacements) && !similarStrength) {
                        replacements.removeAll(alreadyPicked);
                    }

                    // Step 3: pick - by similar strength or otherwise
                    Pokemon picked;

                    if (replacements.size() == 1) {
                        // Foregone conclusion.
                        picked = replacements.get(0);
                        alreadyPicked.add(picked);
                    } else if (similarStrength) {
                        picked = pickEvoPowerLvlReplacement(replacements, ev.to);
                        alreadyPicked.add(picked);
                    } else {
                        picked = replacements.get(this.random.nextInt(replacements.size()));
                        alreadyPicked.add(picked);
                    }

                    // Step 4: add it to the new evos pool
                    Evolution newEvo = new Evolution(fromPK, picked, ev.carryStats, ev.type, ev.extraInfo);
                    newEvo.formeSuffix = picked.formeSuffix;
                    boolean checkCosmetics = true;
                    if (picked.formeNumber > 0) {
                        newEvo.forme = picked.formeNumber;
                        checkCosmetics = false;
                    }
                    if (checkCosmetics && newEvo.to.cosmeticForms > 0) {
                        newEvo.forme = newEvo.to.getCosmeticFormNumber(this.random.nextInt(newEvo.to.cosmeticForms));
                    } else if (!checkCosmetics && picked.cosmeticForms > 0) {
                        newEvo.forme += picked.getCosmeticFormNumber(this.random.nextInt(picked.cosmeticForms));
                    }
                    if (newEvo.type == EvolutionType.LEVEL_FEMALE_ESPURR) {
                        newEvo.type = EvolutionType.LEVEL_FEMALE_ONLY;
                    }
                    fromPK.evolutionsFrom.add(newEvo);
                    picked.evolutionsTo.add(newEvo);
                    newEvoPairs.add(new EvolutionPair(fromPK, picked));
                }

                if (hadError) {
                    // No need to check the other Pokemon if we already errored
                    break;
                }
            }

            // If no error, done and return
            if (!hadError) {
                for (Pokemon pk: actuallyCosmeticPokemonPool) {
                    pk.copyBaseFormeEvolutions(pk.baseForme);
                }
                return;
            } else {
                loops++;
            }
        }

        // If we made it out of the loop, we weren't able to randomize evos.
        throw new RandomizationException("Not able to randomize evolutions in a sane amount of retries.");
    }

    @Override
    public void randomizeEvolutionsEveryLevel(Settings settings) {
        List<Pokemon> pokemonPool = new ArrayList<>(mainPokemonList);
        List<Pokemon> actuallyCosmeticPokemonPool = new ArrayList<>();

        for (int i = 0; i < pokemonPool.size(); i++) {
            Pokemon pk = pokemonPool.get(i);
            if (pk.actuallyCosmetic) {
                pokemonPool.remove(pk);
                i--;
                actuallyCosmeticPokemonPool.add(pk);
            }
        }

        for (Pokemon pk : pokemonPool) {
            pk.evolutionsFrom.clear();
            pk.evolutionsTo.clear();
        }

        for (Pokemon pk : actuallyCosmeticPokemonPool) {
            pk.copyBaseFormeEvolutions(pk.baseForme);
        }

        setEvolutionEveryLevelFlag();
    }

    protected void setEvolutionEveryLevelFlag() {}

    @Override
    public void changeCatchRates(Settings settings) {
        int minimumCatchRateLevel = settings.getMinimumCatchRateLevel();

        if (minimumCatchRateLevel == 5) {
            enableGuaranteedPokemonCatching();
        } else {
            int normalMin, legendaryMin;
            switch (minimumCatchRateLevel) {
                case 1:
                default:
                    normalMin = 75;
                    legendaryMin = 37;
                    break;
                case 2:
                    normalMin = 128;
                    legendaryMin = 64;
                    break;
                case 3:
                    normalMin = 200;
                    legendaryMin = 100;
                    break;
                case 4:
                    normalMin = legendaryMin = 255;
                    break;
            }
            minimumCatchRate(normalMin, legendaryMin);
        }
    }

    @Override
    public void shuffleShopItems() {
        Map<Integer, Shop> currentItems = this.getShopItems();
        if (currentItems == null) return;
        List<Integer> itemList = new ArrayList<>();
        for (Shop shop: currentItems.values()) {
            itemList.addAll(shop.items);
        }
        Collections.shuffle(itemList, this.random);

        Iterator<Integer> itemListIter = itemList.iterator();

        for (Shop shop: currentItems.values()) {
            for (int i = 0; i < shop.items.size(); i++) {
                shop.items.remove(i);
                shop.items.add(i, itemListIter.next());
            }
        }

        this.setShopItems(currentItems);
    }

    // Note: If you use this on a game where the amount of randomizable shop items is greater than the amount of
    // possible items, you will get owned by the while loop
    @Override
    public void randomizeShopItems(Settings settings) {
        boolean banBadItems = settings.isBanBadRandomShopItems();
        boolean banRegularShopItems = settings.isBanRegularShopItems();
        boolean banOPShopItems = settings.isBanOPShopItems();
        boolean balancePrices = settings.isBalanceShopPrices();
        boolean placeEvolutionItems = settings.isGuaranteeEvolutionItems();
        boolean placeXItems = settings.isGuaranteeXItems();

        if (this.getShopItems() == null) return;
        ItemList possibleItems = banBadItems ? this.getNonBadItems() : this.getAllowedItems();
        if (banRegularShopItems) {
            possibleItems.banSingles(this.getRegularShopItems().stream().mapToInt(Integer::intValue).toArray());
        }
        if (banOPShopItems) {
            possibleItems.banSingles(this.getOPShopItems().stream().mapToInt(Integer::intValue).toArray());
        }
        Map<Integer, Shop> currentItems = this.getShopItems();

        int shopItemCount = currentItems.values().stream().mapToInt(s -> s.items.size()).sum();

        List<Integer> newItems = new ArrayList<>();
        Map<Integer, Shop> newItemsMap = new TreeMap<>();
        int newItem;
        List<Integer> guaranteedItems = new ArrayList<>();
        if (placeEvolutionItems) {
            guaranteedItems.addAll(getEvolutionItems());
        }
        if (placeXItems) {
            guaranteedItems.addAll(getXItems());
        }
        if (placeEvolutionItems || placeXItems) {
            newItems.addAll(guaranteedItems);
            shopItemCount = shopItemCount - newItems.size();

            for (int i = 0; i < shopItemCount; i++) {
                while (newItems.contains(newItem = possibleItems.randomNonTM(this.random)));
                newItems.add(newItem);
            }

            // Guarantee main-game
            List<Integer> mainGameShops = new ArrayList<>();
            List<Integer> nonMainGameShops = new ArrayList<>();
            for (int i: currentItems.keySet()) {
                if (currentItems.get(i).isMainGame) {
                    mainGameShops.add(i);
                } else {
                    nonMainGameShops.add(i);
                }
            }

            // Place items in non-main-game shops; skip over guaranteed items
            Collections.shuffle(newItems, this.random);
            for (int i: nonMainGameShops) {
                int j = 0;
                List<Integer> newShopItems = new ArrayList<>();
                Shop oldShop = currentItems.get(i);
                for (Integer ignored: oldShop.items) {
                    Integer item = newItems.get(j);
                    while (guaranteedItems.contains(item)) {
                        j++;
                        item = newItems.get(j);
                    }
                    newShopItems.add(item);
                    newItems.remove(item);
                }
                Shop shop = new Shop(oldShop);
                shop.items = newShopItems;
                newItemsMap.put(i, shop);
            }

            // Place items in main-game shops
            Collections.shuffle(newItems, this.random);
            for (int i: mainGameShops) {
                List<Integer> newShopItems = new ArrayList<>();
                Shop oldShop = currentItems.get(i);
                for (Integer ignored: oldShop.items) {
                    Integer item = newItems.get(0);
                    newShopItems.add(item);
                    newItems.remove(0);
                }
                Shop shop = new Shop(oldShop);
                shop.items = newShopItems;
                newItemsMap.put(i, shop);
            }
        } else {
            for (int i = 0; i < shopItemCount; i++) {
                while (newItems.contains(newItem = possibleItems.randomNonTM(this.random)));
                newItems.add(newItem);
            }

            Iterator<Integer> newItemsIter = newItems.iterator();

            for (int i: currentItems.keySet()) {
                List<Integer> newShopItems = new ArrayList<>();
                Shop oldShop = currentItems.get(i);
                for (Integer ignored: oldShop.items) {
                    newShopItems.add(newItemsIter.next());
                }
                Shop shop = new Shop(oldShop);
                shop.items = newShopItems;
                newItemsMap.put(i, shop);
            }
        }

        this.setShopItems(newItemsMap);
        if (balancePrices) {
            this.setShopPrices();
        }
    }

    @Override
    public void randomizePickupItems(Settings settings) {
        boolean banBadItems = settings.isBanBadRandomPickupItems();

        ItemList possibleItems = banBadItems ? this.getNonBadItems() : this.getAllowedItems();
        List<PickupItem> currentItems = this.getPickupItems();
        List<PickupItem> newItems = new ArrayList<>();
        for (int i = 0; i < currentItems.size(); i++) {
            int item;
            if (this.generationOfPokemon() == 3 || this.generationOfPokemon() == 4) {
                // Allow TMs in Gen 3/4 since they aren't infinite (and you get TMs from Pickup in the vanilla game)
                item = possibleItems.randomItem(this.random);
            } else {
                item = possibleItems.randomNonTM(this.random);
            }
            PickupItem pickupItem = new PickupItem(item);
            pickupItem.probabilities = Arrays.copyOf(currentItems.get(i).probabilities, currentItems.size());
            newItems.add(pickupItem);
        }

        this.setPickupItems(newItems);
    }

    @Override
    public void minimumCatchRate(int rateNonLegendary, int rateLegendary) {
        List<Pokemon> pokes = getPokemonInclFormes();
        for (Pokemon pkmn : pokes) {
            if (pkmn == null) {
                continue;
            }
            int minCatchRate = pkmn.isLegendary() ? rateLegendary : rateNonLegendary;
            pkmn.catchRate = Math.max(pkmn.catchRate, minCatchRate);
        }

    }

    @Override
    public void standardizeEXPCurves(Settings settings) {
        Settings.ExpCurveMod mod = settings.getExpCurveMod();
        ExpCurve expCurve = settings.getSelectedEXPCurve();

        List<Pokemon> pokes = getPokemonInclFormes();
        switch (mod) {
            case LEGENDARIES:
                for (Pokemon pkmn : pokes) {
                    if (pkmn == null) {
                        continue;
                    }
                    pkmn.growthCurve = pkmn.isLegendary() ? ExpCurve.SLOW : expCurve;
                }
                break;
            case STRONG_LEGENDARIES:
                for (Pokemon pkmn : pokes) {
                    if (pkmn == null) {
                        continue;
                    }
                    pkmn.growthCurve = pkmn.isStrongLegendary() ? ExpCurve.SLOW : expCurve;
                }
                break;
            case ALL:
                for (Pokemon pkmn : pokes) {
                    if (pkmn == null) {
                        continue;
                    }
                    pkmn.growthCurve = expCurve;
                }
                break;
        }
    }

    /* Private methods/structs used internally by the above methods */

    private void updateMovePower(List<Move> moves, int moveNum, int power) {
        Move mv = moves.get(moveNum);
        if (mv.power != power) {
            mv.power = power;
            addMoveUpdate(moveNum, 0);
        }
    }

    private void updateMovePP(List<Move> moves, int moveNum, int pp) {
        Move mv = moves.get(moveNum);
        if (mv.pp != pp) {
            mv.pp = pp;
            addMoveUpdate(moveNum, 1);
        }
    }

    private void updateMoveAccuracy(List<Move> moves, int moveNum, int accuracy) {
        Move mv = moves.get(moveNum);
        if (Math.abs(mv.hitratio - accuracy) >= 1) {
            mv.hitratio = accuracy;
            addMoveUpdate(moveNum, 2);
        }
    }

    private void updateMoveType(List<Move> moves, int moveNum, Type type) {
        Move mv = moves.get(moveNum);
        if (mv.type != type) {
            mv.type = type;
            addMoveUpdate(moveNum, 3);
        }
    }

    private void updateMoveCategory(List<Move> moves, int moveNum, MoveCategory category) {
        Move mv = moves.get(moveNum);
        if (mv.category != category) {
            mv.category = category;
            addMoveUpdate(moveNum, 4);
        }
    }

    private void addMoveUpdate(int moveNum, int updateType) {
        if (!moveUpdates.containsKey(moveNum)) {
            boolean[] updateField = new boolean[5];
            updateField[updateType] = true;
            moveUpdates.put(moveNum, updateField);
        } else {
            moveUpdates.get(moveNum)[updateType] = true;
        }
    }

    protected Set<EvolutionUpdate> impossibleEvolutionUpdates = new TreeSet<>();
    protected Set<EvolutionUpdate> timeBasedEvolutionUpdates = new TreeSet<>();
    protected Set<EvolutionUpdate> easierEvolutionUpdates = new TreeSet<>();

    protected void addEvoUpdateLevel(Set<EvolutionUpdate> evolutionUpdates, Evolution evo) {
        Pokemon pkFrom = evo.from;
        Pokemon pkTo = evo.to;
        int level = evo.extraInfo;
        evolutionUpdates.add(new EvolutionUpdate(pkFrom, pkTo, EvolutionType.LEVEL, String.valueOf(level),
                false, false));
    }

    protected void addEvoUpdateStone(Set<EvolutionUpdate> evolutionUpdates, Evolution evo, String item) {
        Pokemon pkFrom = evo.from;
        Pokemon pkTo = evo.to;
        evolutionUpdates.add(new EvolutionUpdate(pkFrom, pkTo, EvolutionType.STONE, item,
                false, false));
    }

    protected void addEvoUpdateHappiness(Set<EvolutionUpdate> evolutionUpdates, Evolution evo) {
        Pokemon pkFrom = evo.from;
        Pokemon pkTo = evo.to;
        evolutionUpdates.add(new EvolutionUpdate(pkFrom, pkTo, EvolutionType.HAPPINESS, "",
                false, false));
    }

    protected void addEvoUpdateHeldItem(Set<EvolutionUpdate> evolutionUpdates, Evolution evo, String item) {
        Pokemon pkFrom = evo.from;
        Pokemon pkTo = evo.to;
        evolutionUpdates.add(new EvolutionUpdate(pkFrom, pkTo, EvolutionType.LEVEL_ITEM_DAY, item,
                false, false));
    }

    protected void addEvoUpdateParty(Set<EvolutionUpdate> evolutionUpdates, Evolution evo, String otherPk) {
        Pokemon pkFrom = evo.from;
        Pokemon pkTo = evo.to;
        evolutionUpdates.add(new EvolutionUpdate(pkFrom, pkTo, EvolutionType.LEVEL_WITH_OTHER, otherPk,
                false, false));
    }

    protected void addEvoUpdateCondensed(Set<EvolutionUpdate> evolutionUpdates, Evolution evo, boolean additional) {
        Pokemon pkFrom = evo.from;
        Pokemon pkTo = evo.to;
        int level = evo.extraInfo;
        evolutionUpdates.add(new EvolutionUpdate(pkFrom, pkTo, EvolutionType.LEVEL, String.valueOf(level),
                true, additional));
    }

    private Pokemon pickEvoPowerLvlReplacement(List<Pokemon> pokemonPool, Pokemon current) {
        // start with within 10% and add 5% either direction till we find
        // something
        int currentBST = current.bstForPowerLevels();
        int minTarget = currentBST - currentBST / 10;
        int maxTarget = currentBST + currentBST / 10;
        List<Pokemon> canPick = new ArrayList<>();
        List<Pokemon> emergencyPick = new ArrayList<>();
        int expandRounds = 0;
        while (canPick.isEmpty() || (canPick.size() < 3 && expandRounds < 3)) {
            for (Pokemon pk : pokemonPool) {
                if (pk.bstForPowerLevels() >= minTarget && pk.bstForPowerLevels() <= maxTarget && !canPick.contains(pk) && !emergencyPick.contains(pk)) {
                    if (alreadyPicked.contains(pk)) {
                        emergencyPick.add(pk);
                    } else {
                        canPick.add(pk);
                    }
                }
            }
            if (expandRounds >= 2 && canPick.isEmpty()) {
                canPick.addAll(emergencyPick);
            }
            minTarget -= currentBST / 20;
            maxTarget += currentBST / 20;
            expandRounds++;
        }
        return canPick.get(this.random.nextInt(canPick.size()));
    }

    // Note that this is slow and somewhat hacky.
    private Pokemon findPokemonInPoolWithSpeciesID(List<Pokemon> pokemonPool, int speciesID) {
        for (int i = 0; i < pokemonPool.size(); i++) {
            if (pokemonPool.get(i).number == speciesID) {
                return pokemonPool.get(i);
            }
        }
        return null;
    }

    private List<Pokemon> getEvolutionaryRelatives(Pokemon pk) {
        List<Pokemon> evolutionaryRelatives = new ArrayList<>();
        for (Evolution ev : pk.evolutionsFrom) {
            if (!evolutionaryRelatives.contains(ev.to)) {
                Pokemon evo = ev.to;
                evolutionaryRelatives.add(evo);
                Queue<Evolution> evolutionsList = new LinkedList<>();
                evolutionsList.addAll(evo.evolutionsFrom);
                while (evolutionsList.size() > 0) {
                    evo = evolutionsList.remove().to;
                    if (!evolutionaryRelatives.contains(evo)) {
                        evolutionaryRelatives.add(evo);
                        evolutionsList.addAll(evo.evolutionsFrom);
                    }
                }
            }
        }

        for (Evolution ev : pk.evolutionsTo) {
            if (!evolutionaryRelatives.contains(ev.from)) {
                Pokemon preEvo = ev.from;
                evolutionaryRelatives.add(preEvo);

                // At this point, preEvo is basically the "parent" of pk. Run
                // getEvolutionaryRelatives on preEvo in order to get pk's
                // "sibling" evolutions too. For example, if pk is Espeon, then
                // preEvo here will be Eevee, and this will add all the other
                // eeveelutions to the relatives list.
                List<Pokemon> relativesForPreEvo = getEvolutionaryRelatives(preEvo);
                for (Pokemon preEvoRelative : relativesForPreEvo) {
                    if (!evolutionaryRelatives.contains(preEvoRelative)) {
                        evolutionaryRelatives.add(preEvoRelative);
                    }
                }

                while (preEvo.evolutionsTo.size() > 0) {
                    preEvo = preEvo.evolutionsTo.get(0).from;
                    if (!evolutionaryRelatives.contains(preEvo)) {
                        evolutionaryRelatives.add(preEvo);

                        // Similar to above, get the "sibling" evolutions here too.
                        relativesForPreEvo = getEvolutionaryRelatives(preEvo);
                        for (Pokemon preEvoRelative : relativesForPreEvo) {
                            if (!evolutionaryRelatives.contains(preEvoRelative)) {
                                evolutionaryRelatives.add(preEvoRelative);
                            }
                        }
                    }
                }
            }
        }

        return evolutionaryRelatives;
    }

    private static class EvolutionPair {
        private Pokemon from;
        private Pokemon to;

        EvolutionPair(Pokemon from, Pokemon to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((from == null) ? 0 : from.hashCode());
            result = prime * result + ((to == null) ? 0 : to.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EvolutionPair other = (EvolutionPair) obj;
            if (from == null) {
                if (other.from != null)
                    return false;
            } else if (!from.equals(other.from))
                return false;
            if (to == null) {
                return other.to == null;
            } else return to.equals(other.to);
        }
    }

    /**
     * Check whether adding an evolution from one Pokemon to another will cause
     * an evolution cycle.
     *
     * @param from Pokemon that is evolving
     * @param to Pokemon to evolve to
     * @return True if there is an evolution cycle, else false
     */
    private boolean evoCycleCheck(Pokemon from, Pokemon to) {
        Evolution tempEvo = new Evolution(from, to, false, EvolutionType.NONE, 0);
        from.evolutionsFrom.add(tempEvo);
        Set<Pokemon> visited = new HashSet<>();
        Set<Pokemon> recStack = new HashSet<>();
        boolean recur = isCyclic(from, visited, recStack);
        from.evolutionsFrom.remove(tempEvo);
        return recur;
    }

    private boolean isCyclic(Pokemon pk, Set<Pokemon> visited, Set<Pokemon> recStack) {
        if (!visited.contains(pk)) {
            visited.add(pk);
            recStack.add(pk);
            for (Evolution ev : pk.evolutionsFrom) {
                if (!visited.contains(ev.to) && isCyclic(ev.to, visited, recStack)) {
                    return true;
                } else if (recStack.contains(ev.to)) {
                    return true;
                }
            }
        }
        recStack.remove(pk);
        return false;
    }

    private interface BasePokemonAction {
        void applyTo(Pokemon pk);
    }

    private interface EvolvedPokemonAction {
        void applyTo(Pokemon evFrom, Pokemon evTo, boolean toMonIsFinalEvo);
    }

    private interface CosmeticFormAction {
        void applyTo(Pokemon pk, Pokemon baseForme);
    }

    /**
     * Universal implementation for things that have "copy X up evolutions"
     * support.
     *  @param bpAction
     *            Method to run on all base or no-copy Pokemon
     * @param epAction
     *            Method to run on all evolved Pokemon with a linear chain of
     * @param copySplitEvos
     *            If true, treat split evolutions the same way as base Pokemon
     */
    private void copyUpEvolutionsHelper(BasePokemonAction bpAction, EvolvedPokemonAction epAction,
                                        EvolvedPokemonAction splitAction, boolean copySplitEvos) {
        List<Pokemon> allPokes = this.getPokemonInclFormes();
        for (Pokemon pk : allPokes) {
            if (pk != null) {
                pk.temporaryFlag = false;
            }
        }

        // Get evolution data.
        Set<Pokemon> basicPokes = RomFunctions.getBasicPokemon(this);
        Set<Pokemon> splitEvos = RomFunctions.getSplitEvolutions(this);
        Set<Pokemon> middleEvos = RomFunctions.getMiddleEvolutions(this, copySplitEvos);

        for (Pokemon pk : basicPokes) {
            bpAction.applyTo(pk);
            pk.temporaryFlag = true;
        }

        if (!copySplitEvos) {
            for (Pokemon pk : splitEvos) {
                bpAction.applyTo(pk);
                pk.temporaryFlag = true;
            }
        }

        // go "up" evolutions looking for pre-evos to do first
        for (Pokemon pk : allPokes) {
            if (pk != null && !pk.temporaryFlag) {

                // Non-randomized pokes at this point must have
                // a linear chain of single evolutions down to
                // a randomized poke.
                Stack<Evolution> currentStack = new Stack<>();
                Evolution ev = pk.evolutionsTo.get(0);
                while (!ev.from.temporaryFlag) {
                    currentStack.push(ev);
                    ev = ev.from.evolutionsTo.get(0);
                }

                // Now "ev" is set to an evolution from a Pokemon that has had
                // the base action done on it to one that hasn't.
                // Do the evolution action for everything left on the stack.

                if (copySplitEvos && splitAction != null && splitEvos.contains(ev.to)) {
                    splitAction.applyTo(ev.from, ev.to, !middleEvos.contains(ev.to));
                } else {
                    epAction.applyTo(ev.from, ev.to, !middleEvos.contains(ev.to));
                }
                ev.to.temporaryFlag = true;
                while (!currentStack.isEmpty()) {
                    ev = currentStack.pop();
                    if (copySplitEvos && splitAction != null && splitEvos.contains(pk)) {
                        splitAction.applyTo(ev.from, ev.to, !middleEvos.contains(ev.to));
                    } else {
                        epAction.applyTo(ev.from, ev.to, !middleEvos.contains(ev.to));
                    }
                    ev.to.temporaryFlag = true;
                }

            }
        }
    }

    private void copyUpEvolutionsHelper(BasePokemonAction bpAction, EvolvedPokemonAction epAction) {
        copyUpEvolutionsHelper(bpAction, epAction, null, false);
    }

    private boolean checkForUnusedMove(List<Move> potentialList, List<Integer> alreadyUsed) {
        for (Move mv : potentialList) {
            if (!alreadyUsed.contains(mv.number)) {
                return true;
            }
        }
        return false;
    }

    private List<Pokemon> pokemonOfType(Type type, boolean noLegendaries) {
        List<Pokemon> typedPokes = new ArrayList<>();
        for (Pokemon pk : mainPokemonList) {
            if (pk != null && (!noLegendaries || !pk.isLegendary()) && !pk.actuallyCosmetic) {
                if (pk.primaryType == type || pk.secondaryType == type) {
                    typedPokes.add(pk);
                }
            }
        }
        return typedPokes;
    }

    private List<Pokemon> pokemonOfTypeInclFormes(Type type, boolean noLegendaries) {
        List<Pokemon> typedPokes = new ArrayList<>();
        for (Pokemon pk : mainPokemonListInclFormes) {
            if (pk != null && !pk.actuallyCosmetic && (!noLegendaries || !pk.isLegendary())) {
                if (pk.primaryType == type || pk.secondaryType == type) {
                    typedPokes.add(pk);
                }
            }
        }
        return typedPokes;
    }

    private List<Pokemon> allPokemonWithoutNull() {
        List<Pokemon> allPokes = new ArrayList<>(this.getPokemon());
        allPokes.remove(0);
        return allPokes;
    }

    private List<Pokemon> allPokemonInclFormesWithoutNull() {
        List<Pokemon> allPokes = new ArrayList<>(this.getPokemonInclFormes());
        allPokes.remove(0);
        return allPokes;
    }

    private Set<Pokemon> pokemonInArea(EncounterSet area) {
        Set<Pokemon> inArea = new TreeSet<>();
        for (Encounter enc : area.encounters) {
            inArea.add(enc.pokemon);
        }
        return inArea;
    }

    private Map<Type, Integer> typeWeightings;
    private int totalTypeWeighting;

    private Type pickType(boolean weightByFrequency, boolean noLegendaries, boolean allowAltFormes) {
        if (totalTypeWeighting == 0) {
            // Determine weightings
            for (Type t : Type.values()) {
                if (typeInGame(t)) {
                    List<Pokemon> pokemonOfType = pokemonOfType(t, noLegendaries);
                    int pkWithTyping = pokemonOfType.size();
                    typeWeightings.put(t, pkWithTyping);
                    totalTypeWeighting += pkWithTyping;
                }
            }
        }

        if (weightByFrequency) {
            int typePick = this.random.nextInt(totalTypeWeighting);
            int typePos = 0;
            for (Type t : typeWeightings.keySet()) {
                int weight = typeWeightings.get(t);
                if (typePos + weight > typePick) {
                    return t;
                }
                typePos += weight;
            }
            return null;
        } else {
            return randomType();
        }
    }

    private void rivalCarriesStarterUpdate(List<Trainer> currentTrainers, String prefix, int pokemonOffset) {
        // Find the highest rival battle #
        int highestRivalNum = 0;
        for (Trainer t : currentTrainers) {
            if (t.tag != null && t.tag.startsWith(prefix)) {
                highestRivalNum = Math.max(highestRivalNum,
                        Integer.parseInt(t.tag.substring(prefix.length(), t.tag.indexOf('-'))));
            }
        }

        if (highestRivalNum == 0) {
            // This rival type not used in this game
            return;
        }

        // Get the starters
        // us 0 1 2 => them 0+n 1+n 2+n
        List<Pokemon> starters = this.getStarters();

        // Yellow needs its own case, unfortunately.
        if (isYellow()) {
            // The rival's starter is index 1
            Pokemon rivalStarter = starters.get(1);
            int timesEvolves = numEvolutions(rivalStarter, 2);
            // Yellow does not have abilities
            int abilitySlot = 0;
            // Yellow does not have shinies either
            int isShiny = 0;
            int gender = 0;
            int pokeBall = 0;
            // Apply evolutions as appropriate
            if (timesEvolves == 0) {
                for (int j = 1; j <= 3; j++) {
                    changeStarterWithTag(currentTrainers, prefix + j + "-0", rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                }
                for (int j = 4; j <= 7; j++) {
                    for (int i = 0; i < 3; i++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                    }
                }
            } else if (timesEvolves == 1) {
                for (int j = 1; j <= 3; j++) {
                    changeStarterWithTag(currentTrainers, prefix + j + "-0", rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                }
                rivalStarter = pickRandomEvolutionOf(rivalStarter, false);
                for (int j = 4; j <= 7; j++) {
                    for (int i = 0; i < 3; i++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                    }
                }
            } else if (timesEvolves == 2) {
                for (int j = 1; j <= 2; j++) {
                    changeStarterWithTag(currentTrainers, prefix + j + "-" + 0, rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                }
                rivalStarter = pickRandomEvolutionOf(rivalStarter, true);
                changeStarterWithTag(currentTrainers, prefix + "3-0", rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                for (int i = 0; i < 3; i++) {
                    changeStarterWithTag(currentTrainers, prefix + "4-" + i, rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                }
                rivalStarter = pickRandomEvolutionOf(rivalStarter, false);
                for (int j = 5; j <= 7; j++) {
                    for (int i = 0; i < 3; i++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, rivalStarter, abilitySlot, isShiny, gender, pokeBall);
                    }
                }
            }
        } else {
            // Replace each starter as appropriate
            // Use level to determine when to evolve, not number anymore
            for (int i = 0; i < 3; i++) {
                // Rival's starters are pokemonOffset over from each of ours
                int starterToUse = (i + pokemonOffset) % 3;
                Pokemon thisStarter = starters.get(starterToUse);
                int timesEvolves = numEvolutions(thisStarter, 2);
                int abilitySlot = getRandomAbilitySlot(thisStarter);
                int isShiny = 0;
                int gender = 0;
                int pokeBall = 0;
                // if starter is shiny, it will remain shiny
                if (this.random.nextInt(256) == 0) {
                    isShiny = 1;
                }
                gender = this.random.nextInt(8);
                pokeBall = this.random.nextInt(Gen3Constants.pokeBallCount);
                while (abilitySlot == 3) {
                    // Since starters never have hidden abilities, the rival's starter shouldn't either
                    abilitySlot = getRandomAbilitySlot(thisStarter);
                }
                // If a fully evolved pokemon, use throughout
                // Otherwise split by evolutions as appropriate
                if (timesEvolves == 0) {
                    for (int j = 1; j <= highestRivalNum; j++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, abilitySlot, isShiny, gender, pokeBall);
                    }
                } else if (timesEvolves == 1) {
                    int j = 1;
                    for (; j <= highestRivalNum / 2; j++) {
                        if (getLevelOfStarter(currentTrainers, prefix + j + "-" + i) >= 30) {
                            break;
                        }
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, abilitySlot, isShiny, gender, pokeBall);
                    }
                    thisStarter = pickRandomEvolutionOf(thisStarter, false);
                    int evolvedAbilitySlot = getValidAbilitySlotFromOriginal(thisStarter, abilitySlot);
                    for (; j <= highestRivalNum; j++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, evolvedAbilitySlot, isShiny, gender, pokeBall);
                    }
                } else if (timesEvolves == 2) {
                    int j = 1;
                    for (; j <= highestRivalNum; j++) {
                        if (getLevelOfStarter(currentTrainers, prefix + j + "-" + i) >= 16) {
                            break;
                        }
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, abilitySlot, isShiny, gender, pokeBall);
                    }
                    thisStarter = pickRandomEvolutionOf(thisStarter, true);
                    int evolvedAbilitySlot = getValidAbilitySlotFromOriginal(thisStarter, abilitySlot);
                    for (; j <= highestRivalNum; j++) {
                        if (getLevelOfStarter(currentTrainers, prefix + j + "-" + i) >= 36) {
                            break;
                        }
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, evolvedAbilitySlot, isShiny, gender, pokeBall);
                    }
                    thisStarter = pickRandomEvolutionOf(thisStarter, false);
                    evolvedAbilitySlot = getValidAbilitySlotFromOriginal(thisStarter, abilitySlot);
                    for (; j <= highestRivalNum; j++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, evolvedAbilitySlot, isShiny, gender, pokeBall);
                    }
                }
            }
        }

    }

    private Pokemon pickRandomEvolutionOf(Pokemon base, boolean mustEvolveItself) {
        // Used for "rival carries starter"
        // Pick a random evolution of base Pokemon, subject to
        // "must evolve itself" if appropriate.
        List<Pokemon> candidates = new ArrayList<>();
        for (Evolution ev : base.evolutionsFrom) {
            if (!mustEvolveItself || ev.to.evolutionsFrom.size() > 0) {
                candidates.add(ev.to);
            }
        }

        if (candidates.size() == 0) {
            throw new RandomizationException("Random evolution called on a Pokemon without any usable evolutions.");
        }

        return weightedRandomPokemon(candidates);
    }

    private int getLevelOfStarter(List<Trainer> currentTrainers, String tag) {
        for (Trainer t : currentTrainers) {
            if (t.tag != null && t.tag.equals(tag)) {
                // Bingo, get highest level
                // last pokemon is given priority +2 but equal priority
                // = first pokemon wins, so its effectively +1
                // If it's tagged the same we can assume it's the same team
                // just the opposite gender or something like that...
                // So no need to check other trainers with same tag.
                int highestLevel = t.pokemon.get(0).level;
                int trainerPkmnCount = t.pokemon.size();
                for (int i = 1; i < trainerPkmnCount; i++) {
                    int levelBonus = (i == trainerPkmnCount - 1) ? 2 : 0;
                    if (t.pokemon.get(i).level + levelBonus > highestLevel) {
                        highestLevel = t.pokemon.get(i).level;
                    }
                }
                return highestLevel;
            }
        }
        return 0;
    }

    private void changeStarterWithTag(List<Trainer> currentTrainers, String tag, Pokemon starter, int abilitySlot, int isShiny, int gender, int pokeBall) {
        for (Trainer t : currentTrainers) {
            if (t.tag != null && t.tag.equals(tag)) {

                // Bingo
                TrainerPokemon bestPoke = t.pokemon.get(0);

                if (t.forceStarterPosition >= 0) {
                    bestPoke = t.pokemon.get(t.forceStarterPosition);
                } else {
                    // Change the highest level pokemon, not the last.
                    // BUT: last gets +2 lvl priority (effectively +1)
                    // same as above, equal priority = earlier wins
                    int trainerPkmnCount = t.pokemon.size();
                    for (int i = 1; i < trainerPkmnCount; i++) {
                        int levelBonus = (i == trainerPkmnCount - 1) ? 2 : 0;
                        if (t.pokemon.get(i).level + levelBonus > bestPoke.level) {
                            bestPoke = t.pokemon.get(i);
                        }
                    }
                }
                bestPoke.pokemon = starter;
                setFormeForTrainerPokemon(bestPoke,starter);
                bestPoke.resetMoves = true;
                bestPoke.abilitySlot = abilitySlot;
                // if starter is shiny, it will remain shiny
                bestPoke.monIsShiny = isShiny;
                bestPoke.forcedGenderFlag = gender;
                bestPoke.pokeBallId = pokeBall;
            }
        }

    }

    // Return the max depth of pre-evolutions a Pokemon has
    private int numPreEvolutions(Pokemon pk, int maxInterested) {
        return numPreEvolutions(pk, 0, maxInterested);
    }

    private int numPreEvolutions(Pokemon pk, int depth, int maxInterested) {
        if (pk.evolutionsTo.size() == 0) {
            return 0;
        } else {
            if (depth == maxInterested - 1) {
                return 1;
            } else {
                int maxPreEvos = 0;
                for (Evolution ev : pk.evolutionsTo) {
                    maxPreEvos = Math.max(maxPreEvos, numPreEvolutions(ev.from, depth + 1, maxInterested) + 1);
                }
                return maxPreEvos;
            }
        }
    }

    private int numEvolutions(Pokemon pk, int maxInterested) {
        return numEvolutions(pk, 0, maxInterested);
    }

    private int numEvolutions(Pokemon pk, int depth, int maxInterested) {
        if (pk.evolutionsFrom.size() == 0) {
            return 0;
        } else {
            if (depth == maxInterested - 1) {
                return 1;
            } else {
                int maxEvos = 0;
                for (Evolution ev : pk.evolutionsFrom) {
                    maxEvos = Math.max(maxEvos, numEvolutions(ev.to, depth + 1, maxInterested) + 1);
                }
                return maxEvos;
            }
        }
    }

    private Pokemon fullyEvolve(Pokemon pokemon, int trainerIndex) {
        // If the fullyEvolvedRandomSeed hasn't been set yet, set it here.
        if (this.fullyEvolvedRandomSeed == -1) {
            this.fullyEvolvedRandomSeed = random.nextInt(GlobalConstants.LARGEST_NUMBER_OF_SPLIT_EVOS);
        }

        Set<Pokemon> seenMons = new HashSet<>();
        seenMons.add(pokemon);

        while (true) {
            if (pokemon.evolutionsFrom.size() == 0) {
                // fully evolved
                break;
            }

            // check for cyclic evolutions from what we've already seen
            boolean cyclic = false;
            for (Evolution ev : pokemon.evolutionsFrom) {
                if (seenMons.contains(ev.to)) {
                    // cyclic evolution detected - bail now
                    cyclic = true;
                    break;
                }
            }

            if (cyclic) {
                break;
            }

            // We want to make split evolutions deterministic, but still random on a seed-to-seed basis.
            // Therefore, we take a random value (which is generated once per seed) and add it to the trainer's
            // index to get a pseudorandom number that can be used to decide which split to take.
            int evolutionIndex = (this.fullyEvolvedRandomSeed + trainerIndex) % pokemon.evolutionsFrom.size();
            pokemon = pokemon.evolutionsFrom.get(evolutionIndex).to;
            seenMons.add(pokemon);
        }

        return pokemon;
    }

    private Set<Pokemon> relatedPokemon(Pokemon original) {
        Set<Pokemon> results = new HashSet<>();
        results.add(original);
        Queue<Pokemon> toCheck = new LinkedList<>();
        toCheck.add(original);
        while (!toCheck.isEmpty()) {
            Pokemon check = toCheck.poll();
            for (Evolution ev : check.evolutionsFrom) {
                if (!results.contains(ev.to)) {
                    results.add(ev.to);
                    toCheck.add(ev.to);
                }
            }
            for (Evolution ev : check.evolutionsTo) {
                if (!results.contains(ev.from)) {
                    results.add(ev.from);
                    toCheck.add(ev.from);
                }
            }
        }
        return results;
    }

    private Map<Type, List<Pokemon>> cachedReplacementLists;
    private List<Pokemon> cachedAllList;
    private List<Pokemon> bannedList = new ArrayList<>();
    private List<Pokemon> usedAsUniqueList = new ArrayList<>();


    private Pokemon pickTrainerPokeReplacement(Pokemon current, boolean usePowerLevels, Type type,
                                               boolean noLegendaries, boolean wonderGuardAllowed,
                                               boolean usePlacementHistory, /*boolean swapMegaEvos,*/
                                               boolean abilitiesAreRandomized, boolean allowAltFormes,
                                               boolean banIrregularAltFormes) {
        List<Pokemon> pickFrom;
        List<Pokemon> withoutBannedPokemon;

        //if (swapMegaEvos) {
        //    pickFrom = megaEvolutionsList
        //            .stream()
        //            .filter(mega -> mega.method == 1)
        //            .map(mega -> mega.from)
        //            .distinct()
        //            .collect(Collectors.toList());
        //} else {
            pickFrom = cachedAllList;
        //}

        if (usePlacementHistory) {
            // "Distributed" settings
            double placementAverage = getPlacementAverage();
            pickFrom = pickFrom
                    .stream()
                    .filter(pk -> getPlacementHistory(pk) < placementAverage * 2)
                    .collect(Collectors.toList());
            if (pickFrom.isEmpty()) {
                pickFrom = cachedAllList;
            }
        } else if (type != null && cachedReplacementLists != null) {
            // "Type Themed" settings
            if (!cachedReplacementLists.containsKey(type)) {
                List<Pokemon> pokemonOfType = allowAltFormes ? pokemonOfTypeInclFormes(type, noLegendaries) :
                        pokemonOfType(type, noLegendaries);
                pokemonOfType.removeAll(this.getBannedFormesForPlayerPokemon());
                if (!abilitiesAreRandomized) {
                    List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
                    pokemonOfType.removeAll(abilityDependentFormes);
                }
                if (banIrregularAltFormes) {
                    pokemonOfType.removeAll(getIrregularFormes());
                }
                cachedReplacementLists.put(type, pokemonOfType);
            }
            //if (swapMegaEvos) {
            //    pickFrom = cachedReplacementLists.get(type)
            //            .stream()
            //            .filter(pickFrom::contains)
            //            .collect(Collectors.toList());
            //    if (pickFrom.isEmpty()) {
            //        pickFrom = cachedReplacementLists.get(type);
            //    }
            //} else {
                pickFrom = cachedReplacementLists.get(type);
            //}
        }

        withoutBannedPokemon = pickFrom.stream().filter(pk -> !bannedList.contains(pk)).collect(Collectors.toList());
        if (!withoutBannedPokemon.isEmpty()) {
            pickFrom = withoutBannedPokemon;
        }

        if (usePowerLevels) {
            // start with within 10% and add 5% either direction till we find
            // something
            int currentBST = current.bstForPowerLevels();
            int minTarget = currentBST - currentBST / 10;
            int maxTarget = currentBST + currentBST / 10;
            List<Pokemon> canPick = new ArrayList<>();
            int expandRounds = 0;
            while (canPick.isEmpty() || (canPick.size() < 3 && expandRounds < 2)) {
                for (Pokemon pk : pickFrom) {
                    if (pk.bstForPowerLevels() >= minTarget
                            && pk.bstForPowerLevels() <= maxTarget
                            && (wonderGuardAllowed || (pk.ability1 != Abilities.wonderGuard
                                    && pk.ability2 != Abilities.wonderGuard && pk.ability3 != Abilities.wonderGuard))) {
                        canPick.add(pk);
                    }
                }
                minTarget -= currentBST / 20;
                maxTarget += currentBST / 20;
                expandRounds++;
            }
            // If usePlacementHistory is True, then we need to do some
            // extra checking to make sure the randomly chosen pokemon
            // is actually below the current average placement
            // if not, re-roll

            Pokemon chosenPokemon = weightedRandomPokemon(canPick);
            if (usePlacementHistory) {
                double placementAverage = getPlacementAverage();
                List<Pokemon> filteredPickList = canPick
                        .stream()
                        .filter(pk -> getPlacementHistory(pk) < placementAverage)
                        .collect(Collectors.toList());
                if (filteredPickList.isEmpty()) {
                    filteredPickList = canPick;
                }
                chosenPokemon = weightedRandomPokemon(filteredPickList);
            }
            return chosenPokemon;
        } else {
            if (wonderGuardAllowed) {
                return weightedRandomPokemon(pickFrom);
            } else {
                Pokemon pk = weightedRandomPokemon(pickFrom);
                while (pk.ability1 == Abilities.wonderGuard
                        || pk.ability2 == Abilities.wonderGuard
                        || pk.ability3 == Abilities.wonderGuard) {
                    pk = weightedRandomPokemon(pickFrom);
                }
                return pk;
            }
        }
    }

    private Pokemon pickWildPowerLvlReplacement(List<Pokemon> pokemonPool, Pokemon current, boolean banSamePokemon,
            List<Pokemon> usedUp, int bstBalanceLevel) {
        // start with within 10% and add 5% either direction till we find
        // something
        int balancedBST = bstBalanceLevel * 10 + 250;
        int currentBST = Math.min(current.bstForPowerLevels(), balancedBST);
        int minTarget = currentBST - currentBST / 10;
        int maxTarget = currentBST + currentBST / 10;
        List<Pokemon> canPick = new ArrayList<>();
        int expandRounds = 0;
        while (canPick.isEmpty() || (canPick.size() < 3 && expandRounds < 3)) {
            for (Pokemon pk : pokemonPool) {
                if (pk.bstForPowerLevels() >= minTarget && pk.bstForPowerLevels() <= maxTarget
                        && (!banSamePokemon || pk != current) && (usedUp == null || !usedUp.contains(pk))
                        && !canPick.contains(pk)) {
                    canPick.add(pk);
                }
            }
            minTarget -= currentBST / 20;
            maxTarget += currentBST / 20;
            expandRounds++;
        }
        return canPick.get(this.random.nextInt(canPick.size()));
    }

    private void setFormeForEncounter(Encounter enc, Pokemon pk) {
        boolean checkCosmetics = true;
        enc.formeNumber = 0;
        if (enc.pokemon.formeNumber > 0) {
            enc.formeNumber = enc.pokemon.formeNumber;
            enc.pokemon = enc.pokemon.baseForme;
            checkCosmetics = false;
        }
        if (checkCosmetics && enc.pokemon.cosmeticForms > 0) {
            enc.formeNumber = enc.pokemon.getCosmeticFormNumber(this.random.nextInt(enc.pokemon.cosmeticForms));
        } else if (!checkCosmetics && pk.cosmeticForms > 0) {
            enc.formeNumber += pk.getCosmeticFormNumber(this.random.nextInt(pk.cosmeticForms));
        }
    }

    private Map<Integer, List<EncounterSet>> mapZonesToEncounters(List<EncounterSet> encountersForAreas) {
        Map<Integer, List<EncounterSet>> zonesToEncounters = new TreeMap<>();
        for (EncounterSet encountersInArea : encountersForAreas) {
            if (zonesToEncounters.containsKey(encountersInArea.offset)) {
                zonesToEncounters.get(encountersInArea.offset).add(encountersInArea);
            } else {
                List<EncounterSet> encountersForZone = new ArrayList<>();
                encountersForZone.add(encountersInArea);
                zonesToEncounters.put(encountersInArea.offset, encountersForZone);
            }
        }
        return zonesToEncounters;
    }

    public Pokemon pickEntirelyRandomPokemon(boolean includeFormes, boolean noLegendaries, int wildPokemonBSTLimit,
                                             EncounterSet area, List<Pokemon> banned) {
        List<Pokemon> pool = getWildPool(includeFormes, noLegendaries, wildPokemonBSTLimit);
        pool.removeIf(pk -> pk.actuallyCosmetic || banned.contains(pk) || area.bannedPokemon.contains(pk));
        if (pool.isEmpty()) {
            throw new RandomizationException("ERROR: Couldn't replace a wild Pokemon!");
        }
        return pool.get(this.random.nextInt(pool.size()));
    }

    private Pokemon pickStaticPowerLvlReplacement(List<Pokemon> pokemonPool, Pokemon current, boolean banSamePokemon,
                                                  boolean limitBST) {
        // start with within 10% and add 5% either direction till we find
        // something
        int currentBST = current.bstForPowerLevels();
        int minTarget = limitBST ? currentBST - currentBST / 5 : currentBST - currentBST / 10;
        int maxTarget = limitBST ? currentBST : currentBST + currentBST / 10;
        List<Pokemon> canPick = new ArrayList<>();
        int expandRounds = 0;
        while (canPick.isEmpty() || (canPick.size() < 3 && expandRounds < 3)) {
            for (Pokemon pk : pokemonPool) {
                if (pk.bstForPowerLevels() >= minTarget && pk.bstForPowerLevels() <= maxTarget
                        && (!banSamePokemon || pk != current) && !canPick.contains(pk)) {
                    canPick.add(pk);
                }
            }
            minTarget -= currentBST / 20;
            maxTarget += currentBST / 20;
            expandRounds++;
        }
        return weightedRandomPokemon(canPick);
    }

    @Override
    public List<Pokemon> getAbilityDependentFormes() {
        List<Pokemon> abilityDependentFormes = new ArrayList<>();
        for (int i = 0; i < mainPokemonListInclFormes.size(); i++) {
            Pokemon pokemon = mainPokemonListInclFormes.get(i);
            if (pokemon.baseForme != null) {
                if (pokemon.baseForme.number == Species.castform) {
                    // All alternate Castform formes
                    abilityDependentFormes.add(pokemon);
                } else if (pokemon.baseForme.number == Species.darmanitan && pokemon.formeNumber == 1) {
                    // Damanitan-Z
                    abilityDependentFormes.add(pokemon);
                } else if (pokemon.baseForme.number == Species.aegislash) {
                    // Aegislash-B
                    abilityDependentFormes.add(pokemon);
                } else if (pokemon.baseForme.number == Species.wishiwashi) {
                    // Wishiwashi-S
                    abilityDependentFormes.add(pokemon);
                }
            }
        }
        return abilityDependentFormes;
    }

    @Override
    public List<Pokemon> getBannedFormesForPlayerPokemon() {
        List<Pokemon> bannedFormes = new ArrayList<>();
        for (int i = 0; i < mainPokemonListInclFormes.size(); i++) {
            Pokemon pokemon = mainPokemonListInclFormes.get(i);
            if (pokemon.baseForme != null) {
                if (pokemon.baseForme.number == Species.giratina) {
                    // Giratina-O is banned because it reverts back to Altered Forme if
                    // equipped with any item that isn't the Griseous Orb.
                    bannedFormes.add(pokemon);
                } else if (pokemon.baseForme.number == Species.shaymin) {
                    // Shaymin-S is banned because it reverts back to its original forme
                    // under a variety of circumstances, and can only be changed back
                    // with the Gracidea.
                    bannedFormes.add(pokemon);
                }
            }
        }
        return bannedFormes;
    }

    @Override
    public void randomizeTotemPokemon(Settings settings) {
        boolean randomizeTotem =
                settings.getTotemPokemonMod() == Settings.TotemPokemonMod.RANDOM ||
                        settings.getTotemPokemonMod() == Settings.TotemPokemonMod.SIMILAR_STRENGTH;
        boolean randomizeAllies =
                settings.getAllyPokemonMod() == Settings.AllyPokemonMod.RANDOM ||
                        settings.getAllyPokemonMod() == Settings.AllyPokemonMod.SIMILAR_STRENGTH;
        boolean randomizeAuras =
                settings.getAuraMod() == Settings.AuraMod.RANDOM ||
                        settings.getAuraMod() == Settings.AuraMod.SAME_STRENGTH;
        boolean similarStrengthTotem = settings.getTotemPokemonMod() == Settings.TotemPokemonMod.SIMILAR_STRENGTH;
        boolean similarStrengthAllies = settings.getAllyPokemonMod() == Settings.AllyPokemonMod.SIMILAR_STRENGTH;
        boolean similarStrengthAuras = settings.getAuraMod() == Settings.AuraMod.SAME_STRENGTH;
        boolean randomizeHeldItems = settings.isRandomizeTotemHeldItems();
        int levelModifier = settings.isTotemLevelsModified() ? settings.getTotemLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowTotemAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        checkPokemonRestrictions();
        List<TotemPokemon> currentTotemPokemon = this.getTotemPokemon();
        List<TotemPokemon> replacements = new ArrayList<>();
        List<Pokemon> banned = this.bannedForStaticPokemon(settings);
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(getIrregularFormes());
        }
        List<Pokemon> listInclFormesExclCosmetics =
                mainPokemonListInclFormes
                        .stream()
                        .filter(pk -> !pk.actuallyCosmetic)
                        .collect(Collectors.toList());
        List<Pokemon> pokemonLeft = new ArrayList<>(!allowAltFormes ? mainPokemonList : listInclFormesExclCosmetics);
        pokemonLeft.removeAll(banned);
        for (TotemPokemon old : currentTotemPokemon) {
            TotemPokemon newTotem = new TotemPokemon();
            newTotem.heldItem = old.heldItem;
            if (randomizeTotem) {
                Pokemon newPK;
                Pokemon oldPK = old.pkmn;
                if (old.forme > 0) {
                    oldPK = getAltFormeOfPokemon(oldPK, old.forme);
                }

                if (similarStrengthTotem) {
                    newPK = pickStaticPowerLvlReplacement(
                            pokemonLeft,
                            oldPK,
                            true,
                            false);
                } else {
                    newPK = pokemonLeft.remove(this.random.nextInt(pokemonLeft.size()));
                }

                pokemonLeft.remove(newPK);
                newTotem.pkmn = newPK;
                setFormeForStaticEncounter(newTotem, newPK);
                newTotem.resetMoves = true;
                newTotem.level = old.level;

                if (levelModifier != 0) {
                    newTotem.level = Math.min(100, (int) Math.round(newTotem.level * (1 + levelModifier / 100.0)));
                }
                if (pokemonLeft.size() == 0) {
                    pokemonLeft.addAll(!allowAltFormes ? mainPokemonList : listInclFormesExclCosmetics);
                    pokemonLeft.removeAll(banned);
                }
            } else {
                newTotem.pkmn = old.pkmn;
                newTotem.level = old.level;
                if (levelModifier != 0) {
                    newTotem.level = Math.min(100, (int) Math.round(newTotem.level * (1 + levelModifier / 100.0)));
                }
                setFormeForStaticEncounter(newTotem, newTotem.pkmn);
            }

            if (randomizeAllies) {
                for (Integer oldAllyIndex: old.allies.keySet()) {
                    StaticEncounter oldAlly = old.allies.get(oldAllyIndex);
                    StaticEncounter newAlly = new StaticEncounter();
                    Pokemon newAllyPK;
                    Pokemon oldAllyPK = oldAlly.pkmn;
                    if (oldAlly.forme > 0) {
                        oldAllyPK = getAltFormeOfPokemon(oldAllyPK, oldAlly.forme);
                    }
                    if (similarStrengthAllies) {
                        newAllyPK = pickStaticPowerLvlReplacement(
                                pokemonLeft,
                                oldAllyPK,
                                true,
                                false);
                    } else {
                        newAllyPK = pokemonLeft.remove(this.random.nextInt(pokemonLeft.size()));
                    }

                    pokemonLeft.remove(newAllyPK);
                    newAlly.pkmn = newAllyPK;
                    setFormeForStaticEncounter(newAlly, newAllyPK);
                    newAlly.resetMoves = true;
                    newAlly.level = oldAlly.level;
                    if (levelModifier != 0) {
                        newAlly.level = Math.min(100, (int) Math.round(newAlly.level * (1 + levelModifier / 100.0)));
                    }

                    newTotem.allies.put(oldAllyIndex,newAlly);
                    if (pokemonLeft.size() == 0) {
                        pokemonLeft.addAll(!allowAltFormes ? mainPokemonList : listInclFormesExclCosmetics);
                        pokemonLeft.removeAll(banned);
                    }
                }
            } else {
                newTotem.allies = old.allies;
                for (StaticEncounter ally: newTotem.allies.values()) {
                    if (levelModifier != 0) {
                        ally.level = Math.min(100, (int) Math.round(ally.level * (1 + levelModifier / 100.0)));
                        setFormeForStaticEncounter(ally, ally.pkmn);
                    }
                }
            }

            if (randomizeAuras) {
                if (similarStrengthAuras) {
                    newTotem.aura = Aura.randomAuraSimilarStrength(this.random, old.aura);
                } else {
                    newTotem.aura = Aura.randomAura(this.random);
                }
            } else {
                newTotem.aura = old.aura;
            }

            if (randomizeHeldItems) {
                if (old.heldItem != 0) {
                    List<Integer> consumableList = getAllConsumableHeldItems();
                    newTotem.heldItem = consumableList.get(this.random.nextInt(consumableList.size()));
                }
            }

            replacements.add(newTotem);
        }

        // Save
        this.setTotemPokemon(replacements);
    }

    /* Helper methods used by subclasses and/or this class */

    void checkPokemonRestrictions() {
        if (!restrictionsSet) {
            setPokemonPool(null);
        }
    }

    protected void applyCamelCaseNames() {
        List<Pokemon> pokes = getPokemon();
        for (Pokemon pkmn : pokes) {
            if (pkmn == null) {
                continue;
            }
            pkmn.name = RomFunctions.camelCase(pkmn.name);
        }

    }

    private void setPlacementHistory(Pokemon newPK) {
        Integer history = getPlacementHistory(newPK);
        placementHistory.put(newPK, history + 1);
    }

    private int getPlacementHistory(Pokemon newPK) {
        return placementHistory.getOrDefault(newPK, 0);
    }

    private double getPlacementAverage() {
        return placementHistory.values().stream().mapToInt(e -> e).average().orElse(0);
    }


    private List<Pokemon> getBelowAveragePlacements() {
        // This method will return a PK if the number of times a pokemon has been
        // placed is less than average of all placed pokemon's appearances
        // E.g., Charmander's been placed once, but the average for all pokemon is 2.2
        // So add to list and return 

        List<Pokemon> toPlacePK = new ArrayList<>();
        List<Pokemon> placedPK = new ArrayList<>(placementHistory.keySet());
        List<Pokemon> allPK = cachedAllList;
        int placedPKNum = 0;
        for (Pokemon p : placedPK) {
            placedPKNum += placementHistory.get(p);
        }
        float placedAverage = Math.round((float)placedPKNum / (float)placedPK.size());



        if (placedAverage != placedAverage) { // this is checking for NaN, should only happen on first call
            placedAverage = 1;
        }

        // now we've got placement average, iterate all pokemon and see if they qualify to be placed

        for (Pokemon newPK : allPK) {
            if (placedPK.contains(newPK)) { // if it's in the list of previously placed, then check its viability 
                if (placementHistory.get(newPK) <= placedAverage) {
                    toPlacePK.add(newPK);
                }
            }
            else {
                toPlacePK.add(newPK); // if not placed at all, automatically flag true for placing

            }
        }

        return toPlacePK;

    }

    @Override
    public void renderPlacementHistory() {
        List<Pokemon> placedPK = new ArrayList<>(placementHistory.keySet());
        for (Pokemon p : placedPK) {
            System.out.println(p.name+": "+ placementHistory.get(p));
        }
    }

    ///// Item functions
    private void setItemPlacementHistory(int newItem) {
        Integer history = getItemPlacementHistory(newItem);
        // System.out.println("Current history: " + newPK.name + " : " + history);
        itemPlacementHistory.put(newItem, history + 1);
    }

    private int getItemPlacementHistory(int newItem) {
        List<Integer> placedItem = new ArrayList<>(itemPlacementHistory.keySet());
        if (placedItem.contains(newItem)) {
            return itemPlacementHistory.get(newItem);
        }
        else {
            return 0;
        }
    }

    private float getItemPlacementAverage() {
        // This method will return an integer of average for itemPlacementHistory
        // placed is less than average of all placed pokemon's appearances
        // E.g., Charmander's been placed once, but the average for all pokemon is 2.2
        // So add to list and return 

        List<Integer> placedPK = new ArrayList<>(itemPlacementHistory.keySet());
        int placedPKNum = 0;
        for (Integer p : placedPK) {
            placedPKNum += itemPlacementHistory.get(p);
        }
        return (float)placedPKNum / (float)placedPK.size();
    }

    private void reportItemHistory() {
        String[] itemNames = this.getItemNames();
        List<Integer> placedItem = new ArrayList<>(itemPlacementHistory.keySet());
        for (Integer p : placedItem) {
            System.out.println(itemNames[p]+": "+ itemPlacementHistory.get(p));
        }
    }

    protected void log(String log) {
        if (logStream != null) {
            logStream.println(log);
        }
    }

    protected void logBlankLine() {
        if (logStream != null) {
            logStream.println();
        }
    }

    /* Default Implementations */
    /* Used when a subclass doesn't override */
    /*
     * The implication here is that these WILL be overridden by at least one
     * subclass.
     */
    @Override
    public boolean typeInGame(Type type) {
        return !type.isHackOnly && !(type == Type.FAIRY && generationOfPokemon() < 3);
    }

    @Override
    public String abilityName(int number) {
        return "";
    }

    @Override
    public List<Integer> getUselessAbilities() {
        return new ArrayList<>();
    }

    @Override
    public int getAbilityForTrainerPokemon(TrainerPokemon tp) {
        return 0;
    }

    @Override
    public boolean hasTimeBasedEncounters() {
        // DEFAULT: no
        return false;
    }

    @Override
    public List<Pokemon> bannedForWildEncounters(Settings settings) {
        return new ArrayList<>();
    }

    @Override
    public List<Integer> getMovesBannedFromLevelup() {
        return new ArrayList<>();
    }

    @Override
    public List<Pokemon> bannedForStaticPokemon(Settings settings) {
        return new ArrayList<>();
    }

    @Override
    public boolean forceSwapStaticMegaEvos() {
        return false;
    }

    @Override
    public int maxTrainerNameLength() {
        // default: no real limit
        return Integer.MAX_VALUE;
    }

    @Override
    public int maxSumOfTrainerNameLengths() {
        // default: no real limit
        return Integer.MAX_VALUE;
    }

    @Override
    public int maxTrainerClassNameLength() {
        // default: no real limit
        return Integer.MAX_VALUE;
    }

    @Override
    public int maxTradeNicknameLength() {
        return 12;
    }

    @Override
    public int maxTradeOTNameLength() {
        return 7;
    }

    @Override
    public boolean altFormesCanHaveDifferentEvolutions() {
        return false;
    }

    @Override
    public List<Integer> getGameBreakingMoves() {
        // Sonicboom & Dragon Rage
        return Arrays.asList(49, 82);
    }

    @Override
    public List<Integer> getIllegalMoves() {
        return new ArrayList<>();
    }

    @Override
    public boolean isYellow() {
        return false;
    }

    @Override
    public void writeCheckValueToROM(int value) {
        // do nothing
    }

    @Override
    public int miscTweaksAvailable() {
        // default: none
        return 0;
    }

    @Override
    public void applyMiscTweaks(Settings settings) {
        int selectedMiscTweaks = settings.getCurrentMiscTweaks();

        int codeTweaksAvailable = miscTweaksAvailable();
        List<MiscTweak> tweaksToApply = new ArrayList<>();

        for (MiscTweak mt : MiscTweak.allTweaks) {
            if ((codeTweaksAvailable & mt.getValue()) > 0 && (selectedMiscTweaks & mt.getValue()) > 0) {
                tweaksToApply.add(mt);
            }
        }

        // Sort so priority is respected in tweak ordering.
        Collections.sort(tweaksToApply);

        // Now apply in order.
        for (MiscTweak mt : tweaksToApply) {
            applyMiscTweak(mt);
        }
    }

    @Override
    public void applyMiscTweak(MiscTweak tweak) {
        // default: do nothing
    }

    @Override
    public List<Integer> getXItems() {
        return GlobalConstants.xItems;
    }

    @Override
    public List<Integer> getSensibleHeldItemsFor(TrainerPokemon tp, boolean consumableOnly, List<Move> moves, int[] pokeMoves) {
        return Arrays.asList(0);
    }

    @Override
    public List<Integer> getAllConsumableHeldItems() {
        return Arrays.asList(0);
    }

    @Override
    public List<Integer> getAllHeldItems() {
        return Arrays.asList(0);
    }

    @Override
    public List<Pokemon> getBannedFormesForTrainerPokemon() {
        return new ArrayList<>();
    }

    @Override
    public List<PickupItem> getPickupItems() {
        return new ArrayList<>();
    }

    @Override
    public void setPickupItems(List<PickupItem> pickupItems) {
        // do nothing
    }
}
