package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.constants.Species;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.Evolution;
import com.dabomstew.pkrandom.pokemon.EvolutionType;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.AbstractRomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.*;
import java.util.stream.Collectors;

public class EvolutionsRandomizer {
    private final Random random;
    private final RomHandler romHandler;
    private final PokemonService pokemonService;
    private final Settings settings;

    private final List<Pokemon> alreadyPicked = new ArrayList<>();

    public EvolutionsRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
    }

    public void randomizeEvolutions() {
        boolean similarStrength = settings.isEvosSimilarStrength();
        boolean sameType = settings.isEvosSameTyping();
        boolean limitToThreeStages = settings.isEvosMaxThreeStages();
        boolean forceChange = settings.isEvosForceChange();
        boolean allowAltFormes = settings.isEvosAllowAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        pokemonService.checkPokemonRestrictions();
        List<Pokemon> pokemonPool;
        if (romHandler.altFormesCanHaveDifferentEvolutions()) {
            pokemonPool = new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
        } else {
            pokemonPool = new ArrayList<>(pokemonService.getMainPokemonList());
        }
        List<Pokemon> actuallyCosmeticPokemonPool = new ArrayList<>();
        int stageLimit = limitToThreeStages ? 3 : 10;

        List<Pokemon> banned = romHandler.getBannedFormesForPlayerPokemon();
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = romHandler.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }

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

        Set<EvolutionsRandomizer.EvolutionPair> newEvoPairs = new HashSet<>();
        Set<EvolutionsRandomizer.EvolutionPair> oldEvoPairs = new HashSet<>();

        if (forceChange) {
            for (Pokemon pk : pokemonPool) {
                for (Evolution ev : pk.evolutionsFrom) {
                    oldEvoPairs.add(new EvolutionsRandomizer.EvolutionPair(ev.from, ev.to));
                    if (romHandler.generationOfPokemon() >= 7 && ev.from.number == Species.cosmoem) { // Special case for Cosmoem to add Lunala/Solgaleo since we remove the split evo
                        int oppositeVersionLegendary = ev.to.number == Species.solgaleo ? Species.lunala : Species.solgaleo;
                        Pokemon toPkmn = pokemonService.findPokemonInPoolWithSpeciesID(pokemonPool, oppositeVersionLegendary);
                        if (toPkmn != null) {
                            oldEvoPairs.add(new EvolutionsRandomizer.EvolutionPair(ev.from, toPkmn));
                        }
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

                    List<Pokemon> chosenList =
                            allowAltFormes ?
                                    pokemonService.getMainPokemonListInclFormes()
                                            .stream()
                                            .filter(pk -> !pk.actuallyCosmetic)
                                            .collect(Collectors.toList()) :
                                    pokemonService.getMainPokemonList();
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

                        EvolutionsRandomizer.EvolutionPair ep = new EvolutionsRandomizer.EvolutionPair(fromPK, pk);
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
                            int numPreEvos = pokemonService.numPreEvolutions(pk2, stageLimit);
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
                    boolean checkCosmetics = true;
                    if (picked.formeNumber > 0) {
                        newEvo.forme = picked.formeNumber;
                        newEvo.formeSuffix = picked.formeSuffix;
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
                    newEvoPairs.add(new EvolutionsRandomizer.EvolutionPair(fromPK, picked));
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

    public void randomizeEvolutionsEveryLevel() {
        boolean sameType = settings.isEvosSameTyping();
        boolean forceChange = settings.isEvosForceChange();
        boolean allowAltFormes = settings.isEvosAllowAltFormes();
        boolean abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;

        pokemonService.checkPokemonRestrictions();
        List<Pokemon> pokemonPool;
        if (romHandler.altFormesCanHaveDifferentEvolutions()) {
            pokemonPool = new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
        } else {
            pokemonPool = new ArrayList<>(pokemonService.getMainPokemonList());
        }
        List<Pokemon> actuallyCosmeticPokemonPool = new ArrayList<>();

        List<Pokemon> banned = romHandler.getBannedFormesForPlayerPokemon();
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = romHandler.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }

        for (int i = 0; i < pokemonPool.size(); i++) {
            Pokemon pk = pokemonPool.get(i);
            if (pk.actuallyCosmetic) {
                pokemonPool.remove(pk);
                i--;
                actuallyCosmeticPokemonPool.add(pk);
            }
        }

        Set<EvolutionsRandomizer.EvolutionPair> oldEvoPairs = new HashSet<>();

        if (forceChange) {
            for (Pokemon pk : pokemonPool) {
                for (Evolution ev : pk.evolutionsFrom) {
                    oldEvoPairs.add(new EvolutionsRandomizer.EvolutionPair(ev.from, ev.to));
                    if (romHandler.generationOfPokemon() >= 7 && ev.from.number == Species.cosmoem) { // Special case for Cosmoem to add Lunala/Solgaleo since we remove the split evo
                        int oppositeVersionLegendary = ev.to.number == Species.solgaleo ? Species.lunala : Species.solgaleo;
                        Pokemon toPkmn = pokemonService.findPokemonInPoolWithSpeciesID(pokemonPool, oppositeVersionLegendary);
                        if (toPkmn != null) {
                            oldEvoPairs.add(new EvolutionsRandomizer.EvolutionPair(ev.from, toPkmn));
                        }
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

            // Shuffle pokemon list so the results aren't overly predictable.
            Collections.shuffle(pokemonPool, this.random);

            for (Pokemon fromPK : pokemonPool) {
                // Pick a Pokemon as replacement
                replacements.clear();

                List<Pokemon> chosenList =
                        allowAltFormes ?
                                pokemonService.getMainPokemonListInclFormes()
                                        .stream()
                                        .filter(pk -> !pk.actuallyCosmetic)
                                        .collect(Collectors.toList()) :
                                pokemonService.getMainPokemonList();
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

                    // Prevent evolving into old thing if flagged
                    EvolutionsRandomizer.EvolutionPair ep = new EvolutionsRandomizer.EvolutionPair(fromPK, pk);
                    if (forceChange && oldEvoPairs.contains(ep)) {
                        continue;
                    }

                    // Passes everything, add as a candidate.
                    replacements.add(pk);
                }

                // If we don't have any candidates after Step 1, severe failure
                // exit out of this loop and try again from scratch
                if (replacements.size() == 0) {
                    hadError = true;
                    break;
                }

                // Step 2: filter by type, if needed
                if (replacements.size() > 1 && sameType) {
                    Set<Pokemon> includeType = new HashSet<>();
                    for (Pokemon pk : replacements) {
                        if (pk.primaryType == fromPK.primaryType
                                || (fromPK.secondaryType != null && pk.primaryType == fromPK.secondaryType)
                                || (pk.secondaryType != null && pk.secondaryType == fromPK.primaryType)
                                || (pk.secondaryType != null && pk.secondaryType == fromPK.secondaryType)) {
                            includeType.add(pk);
                        }
                    }

                    if (includeType.size() != 0) {
                        replacements.retainAll(includeType);
                    }
                }

                // Step 3: pick - by similar strength or otherwise
                Pokemon picked;

                if (replacements.size() == 1) {
                    // Foregone conclusion.
                    picked = replacements.get(0);
                } else {
                    picked = replacements.get(this.random.nextInt(replacements.size()));
                }

                // Step 4: create new level 1 evo and add it to the new evos pool
                Evolution newEvo = new Evolution(fromPK, picked, false, EvolutionType.LEVEL, 1);
                newEvo.level = 1;
                boolean checkCosmetics = true;
                if (picked.formeNumber > 0) {
                    newEvo.forme = picked.formeNumber;
                    newEvo.formeSuffix = picked.formeSuffix;
                    checkCosmetics = false;
                }
                if (checkCosmetics && newEvo.to.cosmeticForms > 0) {
                    newEvo.forme = newEvo.to.getCosmeticFormNumber(this.random.nextInt(newEvo.to.cosmeticForms));
                } else if (!checkCosmetics && picked.cosmeticForms > 0) {
                    newEvo.forme += picked.getCosmeticFormNumber(this.random.nextInt(picked.cosmeticForms));
                }
                fromPK.evolutionsFrom.add(newEvo);
                picked.evolutionsTo.add(newEvo);
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
}
