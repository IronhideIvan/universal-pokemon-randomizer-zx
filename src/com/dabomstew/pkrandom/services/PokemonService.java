package com.dabomstew.pkrandom.services;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.constants.*;
import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

import java.util.*;

public class PokemonService {
    private final RomHandler romHandler;
    private final Random random;
    private boolean restrictionsSet;

    private List<Pokemon> mainPokemonList;
    private List<Pokemon> mainPokemonListInclFormes;
    private List<Pokemon> altFormesList;
    private List<MegaEvolution> megaEvolutionsList;
    private List<Pokemon> noLegendaryList, onlyLegendaryList, ultraBeastList;
    private List<Pokemon> noLegendaryListInclFormes, onlyLegendaryListInclFormes;
    private List<Pokemon> noLegendaryAltsList, onlyLegendaryAltsList;

    public PokemonService(Random random, RomHandler romHandler, Settings settings) {
        this.romHandler = romHandler;
        this.random = random;
        setPokemonPool(settings);
    }

    public void checkPokemonRestrictions() {
        if (!restrictionsSet) {
            setPokemonPool(null);
        }
    }

    public Pokemon getRandomPokemon() {
        checkPokemonRestrictions();
        return mainPokemonList.get(this.random.nextInt(mainPokemonList.size()));
    }

    public Pokemon getRandomPokemonInclFormes() {
        checkPokemonRestrictions();
        return mainPokemonListInclFormes.get(this.random.nextInt(mainPokemonListInclFormes.size()));
    }

    public Pokemon getRandomNonLegendaryPokemon() {
        checkPokemonRestrictions();
        return noLegendaryList.get(this.random.nextInt(noLegendaryList.size()));
    }

    public Pokemon getRandomNonLegendaryPokemonInclFormes() {
        checkPokemonRestrictions();
        return noLegendaryListInclFormes.get(this.random.nextInt(noLegendaryListInclFormes.size()));
    }

    public Pokemon randomLegendaryPokemon() {
        checkPokemonRestrictions();
        return onlyLegendaryList.get(this.random.nextInt(onlyLegendaryList.size()));
    }

    public List<Pokemon> getMainPokemonList() {
        return mainPokemonList;
    }

    public List<Pokemon> getMainPokemonListInclFormes() {
        return mainPokemonListInclFormes;
    }

    public List<Pokemon> getAltFormesList() {
        return altFormesList;
    }

    public List<MegaEvolution> getMegaEvolutionsList() {
        return megaEvolutionsList;
    }

    public List<Pokemon> getNoLegendaryList() {
        return noLegendaryList;
    }

    public List<Pokemon> getOnlyLegendaryList() {
        return onlyLegendaryList;
    }

    public List<Pokemon> getUltraBeastList() {
        return ultraBeastList;
    }

    public List<Pokemon> getNoLegendaryListInclFormes() {
        return noLegendaryListInclFormes;
    }

    public List<Pokemon> getOnlyLegendaryListInclFormes() {
        return onlyLegendaryListInclFormes;
    }

    public List<Pokemon> getNoLegendaryAltsList() {
        return noLegendaryAltsList;
    }

    public List<Pokemon> getOnlyLegendaryAltsList() {
        return onlyLegendaryAltsList;
    }

    public int numPreEvolutions(Pokemon pk, int maxInterested) {
        return numPreEvolutions(pk, 0, maxInterested);
    }

    public int numPreEvolutions(Pokemon pk, int depth, int maxInterested) {
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

    public int numEvolutions(Pokemon pk, int maxInterested) {
        return numEvolutions(pk, 0, maxInterested);
    }

    public int numEvolutions(Pokemon pk, int depth, int maxInterested) {
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

    private void addPokesFromRange(List<Pokemon> pokemonPool, List<Pokemon> allPokemon, int range_min, int range_max) {
        for (int i = range_min; i <= range_max; i++) {
            if (!pokemonPool.contains(allPokemon.get(i))) {
                pokemonPool.add(allPokemon.get(i));
            }
        }
    }

    public List<Pokemon> getAllPokemonWithoutNull() {
        List<Pokemon> allPokes = new ArrayList<>(romHandler.getPokemon());
        allPokes.remove(0);
        return allPokes;
    }

    public List<Pokemon> getAllPokemonInclFormesWithoutNull() {
        List<Pokemon> allPokes = new ArrayList<>(romHandler.getPokemonInclFormes());
        allPokes.remove(0);
        return allPokes;
    }

    // Note that this is slow and somewhat hacky.
    public Pokemon findPokemonInPoolWithSpeciesID(List<Pokemon> pokemonPool, int speciesID) {
        for (int i = 0; i < pokemonPool.size(); i++) {
            if (pokemonPool.get(i).number == speciesID) {
                return pokemonPool.get(i);
            }
        }
        return null;
    }

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

    public List<Pokemon> getBannedFormesForTrainerPokemon() {
        List<Pokemon> banned = new ArrayList<>();
        for (int i = 0; i < mainPokemonListInclFormes.size(); i++) {
            Pokemon pokemon = mainPokemonListInclFormes.get(i);
            if (pokemon.baseForme != null) {
                if (pokemon.baseForme.number == Species.giratina) {
                    // Giratina-O is banned because it reverts back to Altered Forme if
                    // equipped with any item that isn't the Griseous Orb.
                    banned.add(pokemon);
                }
            }
        }
        return banned;
    }

    public List<Pokemon> pokemonOfType(Type type, boolean noLegendaries) {
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

    public List<Pokemon> pokemonOfTypeInclFormes(Type type, boolean noLegendaries) {
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
        List<Pokemon> altFormes = romHandler.getAltFormes();
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

    private void setPokemonPool(Settings settings) {
        GenRestrictions restrictions = null;
        if (settings != null) {
            restrictions = settings.getCurrentRestrictions();

            // restrictions should already be null if "Limit Pokemon" is disabled, but this is a safeguard
            if (!settings.isLimitPokemon()) {
                restrictions = null;
            }
        }

        restrictionsSet = true;
        mainPokemonList = this.getAllPokemonWithoutNull();
        mainPokemonListInclFormes = this.getAllPokemonInclFormesWithoutNull();
        altFormesList = romHandler.getAltFormes();
        megaEvolutionsList = romHandler.getMegaEvolutions();
        if (restrictions != null) {
            mainPokemonList = new ArrayList<>();
            mainPokemonListInclFormes = new ArrayList<>();
            megaEvolutionsList = new ArrayList<>();
            List<Pokemon> allPokemon = romHandler.getPokemon();

            if (restrictions.allow_gen1) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.bulbasaur, Species.mew);
            }

            if (restrictions.allow_gen2 && allPokemon.size() > Gen2Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.chikorita, Species.celebi);
            }

            if (restrictions.allow_gen3 && allPokemon.size() > Gen3Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.treecko, Species.deoxys);
            }

            if (restrictions.allow_gen4 && allPokemon.size() > Gen4Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.turtwig, Species.arceus);
            }

            if (restrictions.allow_gen5 && allPokemon.size() > Gen5Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.victini, Species.genesect);
            }

            if (restrictions.allow_gen6 && allPokemon.size() > Gen6Constants.pokemonCount) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.chespin, Species.volcanion);
            }

            int maxGen7SpeciesID = romHandler.isSM() ? Species.marshadow : Species.zeraora;
            if (restrictions.allow_gen7 && allPokemon.size() > maxGen7SpeciesID) {
                addPokesFromRange(mainPokemonList, allPokemon, Species.rowlet, maxGen7SpeciesID);
            }

            // If the user specified it, add all the evolutionary relatives for everything in the mainPokemonList
            if (restrictions.allow_evolutionary_relatives) {
                addEvolutionaryRelatives(mainPokemonList);
            }

            // Now that mainPokemonList has all the selected Pokemon, update mainPokemonListInclFormes too
            addAllPokesInclFormes(mainPokemonList, mainPokemonListInclFormes);

            // Populate megaEvolutionsList with all of the mega evolutions that exist in the pool
            List<MegaEvolution> allMegaEvolutions = romHandler.getMegaEvolutions();
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

        for (Pokemon p : mainPokemonList) {
            if (p.isLegendary()) {
                onlyLegendaryList.add(p);
            } else if (p.isUltraBeast()) {
                ultraBeastList.add(p);
            } else {
                noLegendaryList.add(p);
            }
        }
        for (Pokemon p : mainPokemonListInclFormes) {
            if (p.isLegendary()) {
                onlyLegendaryListInclFormes.add(p);
            } else if (!ultraBeastList.contains(p)) {
                noLegendaryListInclFormes.add(p);
            }
        }
        for (Pokemon f : altFormesList) {
            if (f.isLegendary()) {
                onlyLegendaryAltsList.add(f);
            } else {
                noLegendaryAltsList.add(f);
            }
        }
    }
}
