package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;
import com.dabomstew.pkrandom.services.TypeService;

import java.io.PrintStream;
import java.util.*;

public class EncounterRandomizer {
    private final Settings settings;
    private final PokemonService pokemonService;
    private final TypeService typeService;
    private final RomHandler romHandler;
    private final Random random;
    private Map<Pokemon, List<Pokemon>> vanillaPrimaryTypeTranslateMap;
    private Map<Pokemon, List<Pokemon>> vanillaSecondaryTypeTranslateMap;

    public EncounterRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService, TypeService typeService) {
        this.settings = settings;
        this.pokemonService = pokemonService;
        this.romHandler = romHandler;
        this.random = random;
        this.typeService = typeService;
    }

    public void area1to1Encounters() {
        boolean useTimeOfDay = settings.isUseTimeBasedEncounters();

        List<EncounterSet> currentEncounters = romHandler.getEncounters(useTimeOfDay);
        if (romHandler.isORAS()) {
            List<EncounterSet> collapsedEncounters = collapseAreasORAS(currentEncounters);
            area1to1EncountersImpl(collapsedEncounters, settings);
            romHandler.setEncounters(useTimeOfDay, currentEncounters);
            return;
        } else {
            area1to1EncountersImpl(currentEncounters, settings);
            romHandler.setEncounters(useTimeOfDay, currentEncounters);
        }
    }

    public void thematicToVanillaEncounters() {
        // Create a list of out possible options
        List<Pokemon> sortedOptionsList;

        if (settings.isAllowWildAltFormes()) {
            sortedOptionsList = settings.isBlockWildLegendaries() ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                    : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
            sortedOptionsList.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
        } else {
            sortedOptionsList = settings.isBlockWildLegendaries() ? new ArrayList<>(pokemonService.getNoLegendaryList())
                    : new ArrayList<>(pokemonService.getMainPokemonList());
        }

        List<Pokemon> banned = getBannedWildPokemon();
        sortedOptionsList.removeIf(o -> banned.contains((Pokemon) o));
        sortedOptionsList.sort(Comparator.comparingInt(Pokemon::bstForPowerLevels));

        // First, create a map of all pokemon and their possible replacements.
        vanillaPrimaryTypeTranslateMap = new TreeMap<>();
        vanillaSecondaryTypeTranslateMap = new TreeMap<>();

        List<Pokemon> allPokemon = pokemonService.getAllPokemonInclFormesWithoutNull();

        // The min pool may be configurable, but for now lets leave it in the code.
        int poolSize = 3;

        // now that we have our options set up. Lets update our encounters
        List<EncounterSet> allEncounters = romHandler.getEncounters(settings.isUseTimeBasedEncounters());
        if(romHandler.isORAS()) {
            allEncounters = collapseAreasORAS(allEncounters);
        }

        for(EncounterSet area: allEncounters) {
            Map<Pokemon, Pokemon> areaMappedPokemon = new TreeMap<>();
            Set<Pokemon> usedAreaPokemon = new TreeSet<>();
            for(Encounter enc: area.encounters) {
                if(!vanillaPrimaryTypeTranslateMap.containsKey(enc.pokemon)) {
                    List<Pokemon> replacements = getVanillaReplacements(enc.pokemon, enc.pokemon.primaryType, enc.pokemon.secondaryType, poolSize, sortedOptionsList);
                    if(replacements.isEmpty()) {
                        throw new RandomizationException("Unable to randomize pokemon: " + enc.pokemon.toString());
                    }
                    vanillaPrimaryTypeTranslateMap.put(enc.pokemon, replacements);

                    if(enc.pokemon.secondaryType != null) {
                        replacements = getVanillaReplacements(enc.pokemon, enc.pokemon.secondaryType, enc.pokemon.primaryType, poolSize, sortedOptionsList);
                        if(replacements.isEmpty()) {
                            throw new RandomizationException("Unable to randomize pokemon: " + enc.pokemon.toString());
                        }
                        vanillaSecondaryTypeTranslateMap.put(enc.pokemon, replacements);
                    }
                }

                if(areaMappedPokemon.containsKey(enc.pokemon)) {
                    enc.pokemon = areaMappedPokemon.get(enc.pokemon);
                }
                else {
                    Type mostLikelyTypeForEncounter = getMostPopularTypeFromArea(enc.pokemon, area);
                    List<Pokemon> options;
                    if(enc.pokemon.secondaryType != null && enc.pokemon.secondaryType == mostLikelyTypeForEncounter) {
                        options = vanillaSecondaryTypeTranslateMap.get(enc.pokemon);
                    }
                    else {
                        options = vanillaPrimaryTypeTranslateMap.get(enc.pokemon);
                    }

                    boolean isPossibleChoice = false;
                    for (Pokemon p: options) {
                        if(!usedAreaPokemon.contains(p)) {
                            isPossibleChoice = true;
                            break;
                        }
                    }

                    Pokemon newPoke = options.get(random.nextInt(options.size()));
                    if(isPossibleChoice) {
                        while (usedAreaPokemon.contains(newPoke)) {
                            newPoke = options.get(random.nextInt(options.size()));
                        }

                        usedAreaPokemon.add(newPoke);
                    }

                    areaMappedPokemon.put(enc.pokemon, newPoke);
                    enc.pokemon = newPoke;
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        }

        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        if (levelModifier != 0) {
            ModifyEncounterLevels(allEncounters, levelModifier);
        }

        romHandler.setEncounters(settings.isUseTimeBasedEncounters(), allEncounters);
    }

    public void logVanillaEncountersMap(PrintStream log) {
        log.println("--- Possible Replacements for Each Vanilla Wild Pokemon ---");

        if(vanillaPrimaryTypeTranslateMap == null || vanillaPrimaryTypeTranslateMap.isEmpty()) {
            log.println("No possible choices created.");
            log.println();
            return;
        }

        List<Pokemon> keyList = new ArrayList<>(vanillaPrimaryTypeTranslateMap.keySet().stream().toList());
        keyList.sort(Comparator.comparingInt((Pokemon a) -> a.number));
        for(Pokemon p: keyList) {
            log.print("[" + p.number + "] " + p.fullName() + "[bst=" + p.bstForPowerLevels() + ", TYPE=" + p.primaryType);
            if(p.secondaryType != null) {
                log.print(", " + p.secondaryType);
            }
            log.println("]");

            if(vanillaSecondaryTypeTranslateMap != null && vanillaSecondaryTypeTranslateMap.containsKey(p)) {
                log.println("[" + p.primaryType + "] Options:");
            }
            List<Pokemon> choiceList = vanillaPrimaryTypeTranslateMap.get(p);
            for(Pokemon choice: choiceList) {
                log.print("\t" + choice.fullName() + " [bst=" + choice.bstForPowerLevels() + ", TYPE=" + choice.primaryType);
                if(choice.secondaryType != null) {
                    log.print(", " + choice.secondaryType);
                }
                log.println("]");
            }

            if(vanillaSecondaryTypeTranslateMap != null && vanillaSecondaryTypeTranslateMap.containsKey(p)) {
                log.println("[" + p.secondaryType + "] Options:");
                choiceList = vanillaSecondaryTypeTranslateMap.get(p);
                for(Pokemon choice: choiceList) {
                    log.print("\t" + choice.fullName() + " [bst=" + choice.bstForPowerLevels() + ", TYPE=" + choice.primaryType);
                    if(choice.secondaryType != null) {
                        log.print(", " + choice.secondaryType);
                    }
                    log.println("]");
                }
            }

            log.println();
        }

        log.println();
    }

    public void game1to1Encounters() {
        boolean useTimeOfDay = settings.isUseTimeBasedEncounters();
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowWildAltFormes();

        pokemonService.checkPokemonRestrictions();
        // Build the full 1-to-1 map
        Map<Pokemon, Pokemon> translateMap = new TreeMap<>();
        List<Pokemon> remainingLeft = pokemonService.getAllPokemonInclFormesWithoutNull();
        remainingLeft.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
        List<Pokemon> remainingRight;
        if (allowAltFormes) {
            remainingRight = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                    : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
            remainingRight.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
        } else {
            remainingRight = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList())
                    : new ArrayList<>(pokemonService.getMainPokemonList());
        }
        List<Pokemon> banned = getBannedWildPokemon();

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
                    remainingRight.addAll(noLegendaries ? pokemonService.getNoLegendaryListInclFormes() : pokemonService.getMainPokemonListInclFormes());
                    remainingRight.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                } else {
                    remainingRight.addAll(noLegendaries ? pokemonService.getNoLegendaryList() : pokemonService.getMainPokemonList());
                }
                remainingRight.removeAll(banned);
            }
        }

        // Map remaining to themselves just in case
        List<Pokemon> allPokes = pokemonService.getAllPokemonInclFormesWithoutNull();
        for (Pokemon poke : allPokes) {
            if (!translateMap.containsKey(poke)) {
                translateMap.put(poke, poke);
            }
        }

        List<EncounterSet> currentEncounters = romHandler.getEncounters(useTimeOfDay);

        for (EncounterSet area : currentEncounters) {
            for (Encounter enc : area.encounters) {
                // Apply the map
                enc.pokemon = translateMap.get(enc.pokemon);
                if (area.bannedPokemon.contains(enc.pokemon)) {
                    // Ignore the map and put a random non-banned poke
                    List<Pokemon> tempPickable;
                    if (allowAltFormes) {
                        tempPickable = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                                : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
                        tempPickable.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                    } else {
                        tempPickable = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList())
                                : new ArrayList<>(pokemonService.getMainPokemonList());
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
            ModifyEncounterLevels(currentEncounters, levelModifier);
        }

        romHandler.setEncounters(useTimeOfDay, currentEncounters);
    }

    public void randomEncounters() {
        boolean useTimeOfDay = settings.isUseTimeBasedEncounters();
        boolean catchEmAll = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL;
        boolean typeThemed = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS;
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        boolean balanceShakingGrass = settings.isBalanceShakingGrass();
        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowWildAltFormes();

        List<EncounterSet> currentEncounters = romHandler.getEncounters(useTimeOfDay);

        if (romHandler.isORAS()) {
            List<EncounterSet> collapsedEncounters = collapseAreasORAS(currentEncounters);
            area1to1EncountersImpl(collapsedEncounters, settings);
            enhanceRandomEncountersORAS(collapsedEncounters, settings);
            romHandler.setEncounters(useTimeOfDay, currentEncounters);
            return;
        }

        pokemonService.checkPokemonRestrictions();

        // New: randomize the order encounter sets are randomized in.
        // Leads to less predictable results for various modifiers.
        // Need to keep the original ordering around for saving though.
        List<EncounterSet> scrambledEncounters = new ArrayList<>(currentEncounters);
        Collections.shuffle(scrambledEncounters, this.random);

        List<Pokemon> banned =  getBannedWildPokemon();
        // Assume EITHER catch em all OR type themed OR match strength for now
        if (catchEmAll) {
            List<Pokemon> allPokes;
            if (allowAltFormes) {
                allPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes()) : new ArrayList<>(
                        pokemonService.getMainPokemonListInclFormes());
                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
            } else {
                allPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList()) : new ArrayList<>(
                        pokemonService.getMainPokemonList());
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
                            tempPickable = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes()) : new ArrayList<>(
                                    pokemonService.getMainPokemonListInclFormes());
                            tempPickable.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                        } else {
                            tempPickable = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList()) : new ArrayList<>(
                                    pokemonService.getMainPokemonList());
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
                                allPokes.addAll(noLegendaries ? pokemonService.getNoLegendaryListInclFormes() : pokemonService.getMainPokemonListInclFormes());
                                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                            } else {
                                allPokes.addAll(noLegendaries ? pokemonService.getNoLegendaryList() : pokemonService.getMainPokemonList());
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
                    Type areaTheme = romHandler.randomType();
                    if (!cachedPokeLists.containsKey(areaTheme)) {
                        List<Pokemon> pType = allowAltFormes ? pokemonService.pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                pokemonService.pokemonOfType(areaTheme, noLegendaries);
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
                    enc.pokemon = possiblePokemon.get(this.random.nextInt(possiblePokemon.size()));
                    while (enc.pokemon.actuallyCosmetic) {
                        enc.pokemon = possiblePokemon.get(this.random.nextInt(possiblePokemon.size()));
                    }
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        } else if (usePowerLevels) {
            List<Pokemon> allowedPokes;
            if (allowAltFormes) {
                allowedPokes  = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                        : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
            } else {
                allowedPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList())
                        : new ArrayList<>(pokemonService.getMainPokemonList());
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
                    enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        }
        if (levelModifier != 0) {
            ModifyEncounterLevels(currentEncounters, levelModifier);
        }

        romHandler.setEncounters(useTimeOfDay, currentEncounters);
    }

    public void onlyChangeWildLevels() {
        int levelModifier = settings.getWildLevelModifier();

        List<EncounterSet> currentEncounters = romHandler.getEncounters(true);
        if (levelModifier != 0) {
            ModifyEncounterLevels(currentEncounters, levelModifier);
            romHandler.setEncounters(true, currentEncounters);
        }
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

    private void enhanceRandomEncountersORAS(List<EncounterSet> collapsedEncounters, Settings settings) {
        boolean catchEmAll = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL;
        boolean typeThemed = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS;
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        boolean allowAltFormes = settings.isAllowWildAltFormes();

        List<Pokemon> banned = getBannedWildPokemon();

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
                                enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
                                setFormeForEncounter(enc, enc.pokemon);
                            }
                        }
                    } else if (typeThemed) {
                        List<Pokemon> possiblePokemon = null;
                        int iterLoops = 0;
                        while (possiblePokemon == null && iterLoops < 10000) {
                            Type areaTheme = romHandler.randomType();
                            if (!cachedPokeLists.containsKey(areaTheme)) {
                                List<Pokemon> pType = allowAltFormes ? pokemonService.pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                        pokemonService.pokemonOfType(areaTheme, noLegendaries);
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
                            enc.pokemon = possiblePokemon.get(this.random.nextInt(possiblePokemon.size()));
                            while (enc.pokemon.actuallyCosmetic) {
                                enc.pokemon = possiblePokemon.get(this.random.nextInt(possiblePokemon.size()));
                            }
                            setFormeForEncounter(enc, enc.pokemon);
                        }
                    } else if (usePowerLevels) {
                        List<Pokemon> allowedPokes;
                        if (allowAltFormes) {
                            allowedPokes  = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                                    : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
                        } else {
                            allowedPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList())
                                    : new ArrayList<>(pokemonService.getMainPokemonList());
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
                            enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
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
                        enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
                        setFormeForEncounter(enc, enc.pokemon);
                    }
                } else if (typeThemed) {
                    List<Pokemon> possiblePokemon = null;
                    Type areaTheme = getTypeForArea(area);
                    if (!cachedPokeLists.containsKey(areaTheme)) {
                        List<Pokemon> pType = allowAltFormes ? pokemonService.pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                pokemonService.pokemonOfType(areaTheme, noLegendaries);
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
                    enc.pokemon = possiblePokemon.get(this.random.nextInt(possiblePokemon.size()));
                    while (enc.pokemon.actuallyCosmetic) {
                        enc.pokemon = possiblePokemon.get(this.random.nextInt(possiblePokemon.size()));
                    }
                    setFormeForEncounter(enc, enc.pokemon);
                } else if (usePowerLevels) {
                    List<Pokemon> allowedPokes;
                    if (allowAltFormes) {
                        allowedPokes  = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                                : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
                    } else {
                        allowedPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList())
                                : new ArrayList<>(pokemonService.getMainPokemonList());
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
                    enc.pokemon = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
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

    private void area1to1EncountersImpl(List<EncounterSet> currentEncounters, Settings settings) {
        boolean catchEmAll = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL;
        boolean typeThemed = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS;
        boolean usePowerLevels = settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH;
        boolean noLegendaries = settings.isBlockWildLegendaries();
        int levelModifier = settings.isWildLevelsModified() ? settings.getWildLevelModifier() : 0;
        boolean allowAltFormes = settings.isAllowWildAltFormes();

        pokemonService.checkPokemonRestrictions();
        List<Pokemon> banned = getBannedWildPokemon();

        // New: randomize the order encounter sets are randomized in.
        // Leads to less predictable results for various modifiers.
        // Need to keep the original ordering around for saving though.
        List<EncounterSet> scrambledEncounters = new ArrayList<>(currentEncounters);
        Collections.shuffle(scrambledEncounters, this.random);

        // Assume EITHER catch em all OR type themed for now
        if (catchEmAll) {
            List<Pokemon> allPokes;
            if (allowAltFormes) {
                allPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes()) : new ArrayList<>(
                        pokemonService.getMainPokemonListInclFormes());
                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
            } else {
                allPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList()) : new ArrayList<>(
                        pokemonService.getMainPokemonList());
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
                            tempPickable = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes()) : new ArrayList<>(
                                    pokemonService.getMainPokemonListInclFormes());
                            tempPickable.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                        } else {
                            tempPickable = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList()) : new ArrayList<>(
                                    pokemonService.getMainPokemonList());
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
                                allPokes.addAll(noLegendaries ? pokemonService.getNoLegendaryListInclFormes() : pokemonService.getMainPokemonListInclFormes());
                                allPokes.removeIf(o -> ((Pokemon) o).actuallyCosmetic);
                            } else {
                                allPokes.addAll(noLegendaries ? pokemonService.getNoLegendaryList() : pokemonService.getMainPokemonList());
                            }
                            allPokes.removeAll(banned);
                            if (pickablePokemon != allPokes) {
                                pickablePokemon.addAll(allPokes);
                                pickablePokemon.removeAll(area.bannedPokemon);
                            }
                        }
                    }
                }
                for (Encounter enc : area.encounters) {
                    // In Catch 'Em All mode, don't randomize encounters for Pokemon that are banned for
                    // wild encounters. Otherwise, it may be impossible to obtain this Pokemon unless it
                    // randomly appears as a static or unless it becomes a random evolution.
                    if (banned.contains(enc.pokemon)) {
                        continue;
                    }
                    // Apply the map
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
                    Type areaTheme = romHandler.randomType();
                    if (!cachedPokeLists.containsKey(areaTheme)) {
                        List<Pokemon> pType = allowAltFormes ? pokemonService.pokemonOfTypeInclFormes(areaTheme, noLegendaries) :
                                pokemonService.pokemonOfType(areaTheme, noLegendaries);
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
                allowedPokes  = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryListInclFormes())
                        : new ArrayList<>(pokemonService.getMainPokemonListInclFormes());
            } else {
                allowedPokes = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList())
                        : new ArrayList<>(pokemonService.getMainPokemonList());
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
                // Poke-set
                Set<Pokemon> inArea = pokemonInArea(area);
                // Build area map using randoms
                Map<Pokemon, Pokemon> areaMap = new TreeMap<>();
                for (Pokemon areaPk : inArea) {
                    Pokemon picked = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
                    while (areaMap.containsValue(picked)) {
                        picked = pickEntirelyRandomPokemon(allowAltFormes, noLegendaries, area, banned);
                    }
                    areaMap.put(areaPk, picked);
                }
                for (Encounter enc : area.encounters) {
                    // Apply the map
                    enc.pokemon = areaMap.get(enc.pokemon);
                    setFormeForEncounter(enc, enc.pokemon);
                }
            }
        }

        if (levelModifier != 0) {
            ModifyEncounterLevels(currentEncounters, levelModifier);
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

    private Set<Pokemon> pokemonInArea(EncounterSet area) {
        Set<Pokemon> inArea = new TreeSet<>();
        for (Encounter enc : area.encounters) {
            inArea.add(enc.pokemon);
        }
        return inArea;
    }

    private Pokemon pickEntirelyRandomPokemon(boolean includeFormes, boolean noLegendaries, EncounterSet area, List<Pokemon> banned) {
        Pokemon result;
        Pokemon randomNonLegendaryPokemon = includeFormes ? pokemonService.getRandomNonLegendaryPokemonInclFormes() : pokemonService.getRandomNonLegendaryPokemon();
        Pokemon randomPokemon = includeFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
        result = noLegendaries ? randomNonLegendaryPokemon : randomPokemon;
        while (result.actuallyCosmetic) {
            randomNonLegendaryPokemon = includeFormes ? pokemonService.getRandomNonLegendaryPokemonInclFormes() : pokemonService.getRandomNonLegendaryPokemon();
            randomPokemon = includeFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            result = noLegendaries ? randomNonLegendaryPokemon : randomPokemon;
        }
        while (banned.contains(result) || area.bannedPokemon.contains(result)) {
            randomNonLegendaryPokemon = includeFormes ? pokemonService.getRandomNonLegendaryPokemonInclFormes() : pokemonService.getRandomNonLegendaryPokemon();
            randomPokemon = includeFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            result = noLegendaries ? randomNonLegendaryPokemon : randomPokemon;
            while (result.actuallyCosmetic) {
                randomNonLegendaryPokemon = includeFormes ? pokemonService.getRandomNonLegendaryPokemonInclFormes() : pokemonService.getRandomNonLegendaryPokemon();
                randomPokemon = includeFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
                result = noLegendaries ? randomNonLegendaryPokemon : randomPokemon;
            }
        }
        return result;
    }

    private boolean doesAnotherEncounterWithSamePokemonExistInArea(Encounter enc, EncounterSet area) {
        for (Encounter encounterToCheck : area.encounters) {
            if (enc != encounterToCheck && enc.pokemon == encounterToCheck.pokemon) {
                return true;
            }
        }
        return false;
    }

    private Type getTypeForArea(EncounterSet area) {
        Pokemon firstPokemon = area.encounters.get(0).pokemon;
        if (area.encounters.get(0).formeNumber != 0) {
            firstPokemon = romHandler.getAltFormeOfPokemon(firstPokemon, area.encounters.get(0).formeNumber);
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
                pokemon = romHandler.getAltFormeOfPokemon(pokemon, area.encounters.get(i).formeNumber);
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

    private void ModifyEncounterLevels(List<EncounterSet> encounters, int levelModifierPercentage) {
        for (EncounterSet area : encounters) {
            for (Encounter enc : area.encounters) {
                enc.level = Math.min(100, (int) Math.round(enc.level * (1 + levelModifierPercentage / 100.0)));
                enc.maxLevel = Math.min(100, (int) Math.round(enc.maxLevel * (1 + levelModifierPercentage / 100.0)));
            }
        }
    }

    private List<Pokemon> getBannedWildPokemon() {
        List<Pokemon> banned =  romHandler.bannedForWildEncounters();
        banned.addAll(pokemonService.getBannedFormesForPlayerPokemon());
        if (settings.getAbilitiesMod() != Settings.AbilitiesMod.RANDOMIZE) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (settings.isBanIrregularAltFormes()) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        if(settings.isNoWildStarters()) {
            List<Pokemon> starters = romHandler.getStarters();
            for(Pokemon starter: starters) {
                Set<Pokemon> relatives = pokemonService.getRelatedPokemon(starter);
                banned.addAll(relatives);
            }
        }
        if(settings.isNoWildStaticPokemon()) {
            List<StaticEncounter> staticEncounters = romHandler.getStaticPokemon();
            for(StaticEncounter se: staticEncounters) {
                banned.add(se.pkmn);
            }
        }

        return banned;
    }

    private List<Pokemon> findSimilarPokemon(int powerLevel, Type pokemonType, int minPool, List<Pokemon> pickFromList) {
        List<Pokemon> similarPokemon = new ArrayList<>();

        int minPowerLevel = powerLevel - (powerLevel / 10);
        int maxPowerLevel = powerLevel + (powerLevel / 10);
        int maxSearchLoops = 3;

        for (int i = 0; similarPokemon.size() < minPool && i < maxSearchLoops; ++i) {
            for(Pokemon possiblePokemon: pickFromList) {
                if(!similarPokemon.contains(possiblePokemon)
                        && possiblePokemon.bstForPowerLevels() <= maxPowerLevel
                        && possiblePokemon.bstForPowerLevels() >= minPowerLevel
                        && (pokemonType == null || possiblePokemon.primaryType == pokemonType
                            || (possiblePokemon.secondaryType != null && possiblePokemon.secondaryType == pokemonType))) {
                    similarPokemon.add(possiblePokemon);
                }
            }

            minPowerLevel -= powerLevel / 20;
            maxPowerLevel += powerLevel / 20;
        }

        return similarPokemon;
    }

    private List<Pokemon> getVanillaReplacements(Pokemon ogPoke, Type typeToCompare, Type backupType, int poolSize, List<Pokemon> sortedOptionsList) {
        // Find all pokemon with a similar level and type as this one.
        int ogPowerLevel = ogPoke.bstForPowerLevels();

        // Lets find more pokemon than we want and reduce to our desired size from there. This way,
        // if a certain pokemon always returns a perfect pool size we can still have some variation
        // between randomizations by, for example, picking 3 pokemon from 5 vs always using the same 3.
        int searchPoolSize = poolSize + 2;

        List<Pokemon> possibleTranslations = findSimilarPokemon(ogPowerLevel, typeToCompare, searchPoolSize, sortedOptionsList);
        List<Pokemon> choiceList = new ArrayList<>();
        // We have more than we want
        if(possibleTranslations.size() > poolSize) {
            // If we have a pool that's larger than our limit, we need to pick only our maximum amount.
            while (choiceList.size() < poolSize) {
                int choiceIndex = random.nextInt(possibleTranslations.size());
                choiceList.add(possibleTranslations.get(choiceIndex));
                possibleTranslations.remove(choiceIndex);
            }
        }
        // We have fewer than we want
        else if (possibleTranslations.size() < poolSize) {

            // Find any pokemon of a similar power level to add to our pool, using the pokemon's secondary type, if any.
            List<Pokemon> tempList;
            if(backupType != null) {
                tempList = findSimilarPokemon(ogPowerLevel, backupType, searchPoolSize, sortedOptionsList);
                tempList.removeIf(o -> possibleTranslations.contains((Pokemon) o));

                if(tempList.size() + possibleTranslations.size() > poolSize) {
                    while (possibleTranslations.size() < poolSize) {
                        int choiceIndex = random.nextInt(tempList.size());
                        possibleTranslations.add(tempList.get(choiceIndex));
                        tempList.remove(choiceIndex);
                    }
                }
                else{
                    possibleTranslations.addAll(tempList);
                }
            }

            if(possibleTranslations.size() < poolSize) {
                tempList = findSimilarPokemon(ogPowerLevel, null, searchPoolSize, sortedOptionsList);
                tempList.removeIf(o -> possibleTranslations.contains((Pokemon) o));

                if(tempList.size() + possibleTranslations.size() > poolSize) {
                    while (possibleTranslations.size() < poolSize) {
                        int choiceIndex = random.nextInt(tempList.size());
                        possibleTranslations.add(tempList.get(choiceIndex));
                        tempList.remove(choiceIndex);
                    }
                }
                else{
                    possibleTranslations.addAll(tempList);
                }
            }

            if(possibleTranslations.size() > poolSize) {
                while (choiceList.size() < poolSize) {
                    int choiceIndex = random.nextInt(possibleTranslations.size());
                    choiceList.add(possibleTranslations.get(choiceIndex));
                    possibleTranslations.remove(choiceIndex);
                }
            } else {
                choiceList.addAll(possibleTranslations);
            }

            // This would happen if a pokemon has some crazy randomized stats or if the pokemon itself is banned
            // so we need to find the nearest neighbors of this pokemon's power and add those.
            if(choiceList.isEmpty()) {

                int whereWouldIBeIndex = 0;
                while(whereWouldIBeIndex < sortedOptionsList.size()) {
                    Pokemon testPoke = sortedOptionsList.get(whereWouldIBeIndex);

                    if(ogPowerLevel < testPoke.bstForPowerLevels()) {
                        // We found our closest-in-power pokemon.
                        break;
                    }

                    ++whereWouldIBeIndex;
                }

                // We are the weakest thing in the list, so
                // grab the first few pokemon.
                if(whereWouldIBeIndex == 0) {
                    for(int i = 0; i < poolSize; ++i) {
                        choiceList.add(sortedOptionsList.get(i));
                    }
                }
                // We are too strong, grab the strongest few pokemon
                else if (whereWouldIBeIndex == sortedOptionsList.size()) {
                    for(int i = 0; i < poolSize; ++i) {
                        choiceList.add(sortedOptionsList.get(sortedOptionsList.size() - i - 1));
                    }
                }
                // Grab a few stronger and weaker pokemon
                else {
                    int halfMinCount = poolSize / 2;
                    if(halfMinCount * 2 < poolSize) {
                        ++halfMinCount;
                    }

                    int numStrongerToFetch = Math.min(sortedOptionsList.size() - whereWouldIBeIndex, halfMinCount);
                    int numWeakerToFetch = Math.min(whereWouldIBeIndex, halfMinCount);
                    int sanityCount = 0;
                    while(sanityCount < 3 && numStrongerToFetch + numWeakerToFetch < poolSize) {
                        numStrongerToFetch = Math.min(sortedOptionsList.size() - whereWouldIBeIndex, numStrongerToFetch + 1);
                        numWeakerToFetch = Math.min(whereWouldIBeIndex, numWeakerToFetch + 1);
                        ++sanityCount;
                    }

                    for(int i = 0; i < numWeakerToFetch; ++i) {
                        choiceList.add(sortedOptionsList.get(whereWouldIBeIndex - i));
                    }

                    for(int i = 0; i < numStrongerToFetch; ++i) {
                        choiceList.add(sortedOptionsList.get(whereWouldIBeIndex + i + 1));
                    }
                }
            }
        }
        // we have just enough
        else {
            choiceList.addAll(possibleTranslations);
        }

        // Shuffle the collection for good measure.
        Collections.shuffle(choiceList, random);
        return choiceList;
    }

    public Type getMostPopularTypeFromArea(Pokemon poke, EncounterSet area) {
        // First, compile what types are present in this area
        Map<Type, Integer> allTypeCounts = new TreeMap<>();
        for(Encounter enc: area.encounters) {
            int count = 1;
            if(allTypeCounts.containsKey(enc.pokemon.primaryType)) {
                count += allTypeCounts.get(enc.pokemon.primaryType);
            }
            allTypeCounts.put(enc.pokemon.primaryType, count);

            if(enc.pokemon.secondaryType != null) {
                count = 1;
                if(allTypeCounts.containsKey(enc.pokemon.secondaryType)) {
                    count += allTypeCounts.get(enc.pokemon.secondaryType);
                }
                allTypeCounts.put(enc.pokemon.secondaryType, count);
            }
        }

        // Now, determine what the most common type is. If there is a tie, choose randomly between the two as far as which
        // to keep.
        List<Type> mostPopularTypes = new ArrayList<>();
        List<Type> allTypes = typeService.getAllTypesInGame();
        int maxTypeCount = -1;
        for(Type typeInGame: allTypes) {
            if(!allTypeCounts.containsKey(typeInGame)) {
                continue;
            }

            int total = allTypeCounts.get(typeInGame);
            if(total == maxTypeCount) {
                mostPopularTypes.add(typeInGame);
            }
            else if(total > maxTypeCount) {
                maxTypeCount = total;
                mostPopularTypes.clear();
                mostPopularTypes.add(typeInGame);
            }
        }

        Type retType = mostPopularTypes.size() > 1 ?
                mostPopularTypes.get(random.nextInt(mostPopularTypes.size()))
                : mostPopularTypes.getFirst();

        // In case of any ties, the pokemon's primary type takes precedence at the most popular type.
        // The reasoning behind this is situations where a pokemon like Tentacool and Tentacruel are the
        // only pokemon in a surf area. It makes more sense to replace the pokemon with a Water type vs
        // a Poison type. This is not foolproof, but will probably catch the majority of cases.
        if(mostPopularTypes.size() > 1
                && mostPopularTypes.stream().anyMatch(t -> t == poke.primaryType)) {
            retType = poke.primaryType;
        }

        return retType;
    }
}
