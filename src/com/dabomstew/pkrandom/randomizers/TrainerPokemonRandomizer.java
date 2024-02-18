package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.constants.Abilities;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.*;
import java.util.stream.Collectors;

public class TrainerPokemonRandomizer {
    private final Map<Pokemon, Integer> placementHistory = new HashMap<>();
    private final Random random;
    private final RomHandler romHandler;
    private final Settings settings;
    private final PokemonService pokemonService;

    // Settings based variables
    boolean usePowerLevels;
    boolean weightByFrequency;
    boolean noLegendaries;
    boolean noEarlyWonderGuard;
    int levelModifier;
    boolean isTypeThemed;
    boolean isTypeThemedEliteFourGymOnly;
    boolean isTypeThemedAllGroups;
    boolean distributionSetting;
    boolean mainPlaythroughSetting;
    boolean includeFormes;
    boolean banIrregularAltFormes;
    boolean swapMegaEvos;
    boolean shinyChance;
    boolean abilitiesAreRandomized;
    int eliteFourUniquePokemonNumber;
    boolean forceFullyEvolved;
    int forceFullyEvolvedLevel;
    boolean forceChallengeMode;
    boolean rivalCarriesStarter;

    // contextual lists
    private Map<Type, List<Pokemon>> cachedReplacementLists;
    private List<Pokemon> bannedList = new ArrayList<>();

    // Type Weightings
    private Map<Type, Integer> typeWeightings;
    private int totalTypeWeighting;

    public TrainerPokemonRandomizer(Random random, RomHandler romHandler, Settings settings, PokemonService pokemonService) {
        this.random = random;
        this.romHandler = romHandler;
        this.settings = settings;
        this.pokemonService = pokemonService;

        this.usePowerLevels = settings.isTrainersUsePokemonOfSimilarStrength();
        this.weightByFrequency = settings.isTrainersMatchTypingDistribution();
        this.noLegendaries = settings.isTrainersBlockLegendaries();
        this.noEarlyWonderGuard = settings.isTrainersBlockEarlyWonderGuard();
        this.levelModifier = settings.isTrainersLevelModified() ? settings.getTrainersLevelModifier() : 0;
        this.isTypeThemed = settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED;
        this.isTypeThemedEliteFourGymOnly = settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED_ELITE4_GYMS;
        this.isTypeThemedAllGroups = settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED_ALL_GROUPS;
        this.distributionSetting = settings.getTrainersMod() == Settings.TrainersMod.DISTRIBUTED;
        this.mainPlaythroughSetting = settings.getTrainersMod() == Settings.TrainersMod.MAINPLAYTHROUGH;
        this.includeFormes = settings.isAllowTrainerAlternateFormes();
        this.banIrregularAltFormes = settings.isBanIrregularAltFormes();
        this.swapMegaEvos = settings.isSwapTrainerMegaEvos();
        this.shinyChance = settings.isShinyChance();
        this.abilitiesAreRandomized = settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE;
        this.eliteFourUniquePokemonNumber = settings.getEliteFourUniquePokemonNumber();
        this.forceFullyEvolved = settings.isTrainersForceFullyEvolved();
        this.forceFullyEvolvedLevel = settings.getTrainersForceFullyEvolvedLevel();
        this.forceChallengeMode = (settings.getCurrentMiscTweaks() & MiscTweak.FORCE_CHALLENGE_MODE.getValue()) > 0;
        this.rivalCarriesStarter = settings.isRivalCarriesStarterThroughout();
    }

    public void renderPlacementHistory() {
        List<Pokemon> placedPK = new ArrayList<>(placementHistory.keySet());
        for (Pokemon p : placedPK) {
            System.out.println(p.name+": "+ placementHistory.get(p));
        }
    }

    public void onlyChangeTrainerLevels() {
        int levelModifier = settings.getTrainersLevelModifier();

        List<Trainer> currentTrainers = romHandler.getTrainers();
        for (Trainer t: currentTrainers) {
            applyLevelModifierToTrainerPokemon(t, levelModifier);
        }
        romHandler.setTrainers(currentTrainers, false);
    }

    public void randomizeTrainerPokemon() {
        pokemonService.checkPokemonRestrictions();

        this.cachedReplacementLists = new TreeMap<>();
        List<Pokemon> usedAsUniqueList = new ArrayList<>();

        // Set up Pokemon pool
        Map<Type, List<Pokemon>> cachedReplacementLists = new TreeMap<>();
        List<Pokemon> cachedAllList = noLegendaries ? new ArrayList<>(pokemonService.getNoLegendaryList()) : new ArrayList<>(
                pokemonService.getMainPokemonList());
        if (includeFormes) {
            if (noLegendaries) {
                cachedAllList.addAll(pokemonService.getNoLegendaryAltsList());
            } else {
                cachedAllList.addAll(pokemonService.getAltFormesList());
            }
        }
        cachedAllList =
                cachedAllList
                        .stream()
                        .filter(pk -> !pk.actuallyCosmetic)
                        .collect(Collectors.toList());

        List<Pokemon> banned = pokemonService.getBannedFormesForTrainerPokemon();
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        cachedAllList.removeAll(banned);

        List<Trainer> currentTrainers = romHandler.getTrainers();

        // Type Themed related
        Map<Trainer, Type> trainerTypes = new TreeMap<>();
        Set<Type> usedChampionTypes = new TreeSet<>();
        if (isTypeThemed || isTypeThemedEliteFourGymOnly || isTypeThemedAllGroups) {
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
                if (group.startsWith("GYM") || group.startsWith("ELITE") || group.startsWith("CHAMPION") || group.startsWith("THEMED")) {
                    if(isTypeThemedEliteFourGymOnly && !group.startsWith("GYM") && !group.startsWith("ELITE")) {
                        continue;
                    }

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
                    usedChampionTypes.add(typeForGroup);
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
        List<Integer> eliteFourIndices = romHandler.getEliteFourTrainers(forceChallengeMode);
        if (eliteFourUniquePokemon) {
            // Sort Elite Four Trainers to the start of the list
            scrambledTrainers.sort((t1, t2) ->
                    Boolean.compare(eliteFourIndices.contains(currentTrainers.indexOf(t2)+1),eliteFourIndices.contains(currentTrainers.indexOf(t1)+1)));
            illegalEvoChains = forceFullyEvolved;
            if (rivalCarriesStarter) {
                List<Pokemon> starterList = romHandler.getStarters().subList(0,3);
                for (Pokemon starter: starterList) {
                    // If rival/friend carries starter, the starters cannot be set as unique
                    bannedFromUniqueList.add(starter);
                    setEvoChainAsIllegal(starter, bannedFromUniqueList, true);

                    // If the final boss is a rival/friend, the fully evolved starters will be unique
                    if (romHandler.hasRivalFinalBattle()) {
                        cachedAllList.removeAll(getFinalEvos(starter));
                        if (illegalEvoChains) {
                            illegalIfEvolvedList.add(starter);
                            setEvoChainAsIllegal(starter, illegalIfEvolvedList, true);
                        }
                    }
                }
            }
        }

        List<Integer> mainPlaythroughTrainers = romHandler.getMainPlaythroughTrainers();

        List<Pokemon> cachedLegendaryList = new ArrayList<Pokemon>(pokemonService.getOnlyLegendaryListInclFormes());
        Map<Type, List<Pokemon>> cachedLegendariesByType = pokemonService.separatePokemonByType(cachedLegendaryList);

        // Randomize Trainer Pokemon
        // The result after this is done will not be final if "Force Fully Evolved" or "Rival Carries Starter"
        // are used, as they are applied later
        for (Trainer t : scrambledTrainers) {
            boolean usePlacementHistory = distributionSetting || (mainPlaythroughSetting && mainPlaythroughTrainers.contains(t.index));

            applyLevelModifierToTrainerPokemon(t, levelModifier);
            if (t.tag != null && t.tag.equals("IRIVAL")) {
                // This is the first rival in Yellow. His Pokemon is used to determine the non-player
                // starter, so we can't change it here. Just skip it.
                continue;
            }

            // If we have a setting to give uber trainers a legendary set, lets do that.
            if(settings.isGiveUberTrainersLegendaries() && t.tag != null && t.tag.equals("UBER")) {
                Set<Type> usedUberTypes = new TreeSet<Type>();
                List<Pokemon> usedPokemon = new ArrayList<>();

                for (TrainerPokemon tp : t.pokemon) {
                    // Give the uber a legendary of each type for guaranteed variety. If all available types are used,
                    // then give them a normal pokemon instead.
                    if(usedUberTypes.size() < cachedLegendariesByType.size()) {
                        Type uberType = romHandler.randomType();
                        while(!cachedLegendariesByType.containsKey(uberType) || usedUberTypes.contains(uberType)) {
                            uberType = romHandler.randomType();
                        }
                        usedUberTypes.add(uberType);

                        List<Pokemon> legendaryList = cachedLegendariesByType.get(uberType);

                        // failsafe in case we run out of options
                        int safetyCount = 0;
                        Pokemon newPoke = legendaryList.get(this.random.nextInt(legendaryList.size()));
                        while(usedPokemon.contains(newPoke) && safetyCount < 5) {
                            newPoke = legendaryList.get(this.random.nextInt(legendaryList.size()));
                            ++safetyCount;
                        }
                        usedPokemon.add(newPoke);

                        tp.pokemon = newPoke;
                        tp.resetMoves = true;
                        tp.abilitySlot = romHandler.getRandomAbilitySlot(tp.pokemon);
                        setFormeForTrainerPokemon(tp, tp.pokemon);
                    }
                    else {
                        tp.pokemon = pickTrainerPokeReplacement(
                                tp,
                                tp.pokemon,
                                null,
                                cachedAllList,
                                usePlacementHistory
                        );
                    }
                }
                continue;
            }

            // If type themed, give a type to each unassigned trainer
            Type typeForTrainer = trainerTypes.get(t);
            if (typeForTrainer == null && isTypeThemed) {
                typeForTrainer = pickType(weightByFrequency, noLegendaries, includeFormes);
                // Ubers: can't have the same type as each other
                if (t.tag != null && t.tag.equals("CHAMPION")) {
                    while (usedChampionTypes.contains(typeForTrainer)) {
                        typeForTrainer = pickType(weightByFrequency, noLegendaries, includeFormes);
                    }
                    usedChampionTypes.add(typeForTrainer);
                }
            }

            List<Pokemon> evolvesIntoTheWrongType = new ArrayList<>();
            if (typeForTrainer != null) {
                List<Pokemon> pokemonOfType = includeFormes ? pokemonService.pokemonOfTypeInclFormes(typeForTrainer, noLegendaries) :
                        pokemonService.pokemonOfType(typeForTrainer, noLegendaries);
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
                boolean swapThisMegaEvo = swapMegaEvos && tp.canMegaEvolve();
                boolean wgAllowed = (!noEarlyWonderGuard) || tp.level >= 20;
                boolean eliteFourSetUniquePokemon =
                        eliteFourTrackPokemon && eliteFourUniquePokemonNumber > trainerPokemonList.indexOf(tp);
                boolean willForceEvolve = forceFullyEvolved && tp.level >= forceFullyEvolvedLevel;

                Pokemon oldPK = tp.pokemon;
                if (tp.forme > 0) {
                    oldPK = romHandler.getAltFormeOfPokemon(oldPK, tp.forme);
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

                Pokemon newPK = pickTrainerPokeReplacement(
                        tp,
                        oldPK,
                        typeForTrainer,
                        cachedAllList,
                        usePlacementHistory
                );

                // Chosen Pokemon is locked in past here
                if (usePlacementHistory) {
                    setPlacementHistory(newPK);
                }
                tp.pokemon = newPK;
                setFormeForTrainerPokemon(tp, newPK);
                tp.abilitySlot = romHandler.getRandomAbilitySlot(newPK);
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

                if (swapThisMegaEvo) {
                    tp.heldItem = newPK
                            .megaEvolutionsFrom
                            .get(this.random.nextInt(newPK.megaEvolutionsFrom.size()))
                            .argument;
                }

                if (shinyChance) {
                    if (this.random.nextInt(256) == 0) {
                        tp.IVs |= (1 << 30);
                    }
                }
            }
        }

        // Save it all up
        romHandler.setTrainers(currentTrainers, false);
    }

    public void forceFullyEvolvedTrainerPokes() {
        int minLevel = settings.getTrainersForceFullyEvolvedLevel();

        pokemonService.checkPokemonRestrictions();
        List<Trainer> currentTrainers = romHandler.getTrainers();
        for (Trainer t : currentTrainers) {
            for (TrainerPokemon tp : t.pokemon) {
                if (tp.level >= minLevel) {
                    Pokemon newPokemon = fullyEvolve(tp.pokemon, t.index);
                    if (newPokemon != tp.pokemon) {
                        tp.pokemon = newPokemon;
                        setFormeForTrainerPokemon(tp, newPokemon);
                        tp.abilitySlot = romHandler.getValidAbilitySlotFromOriginal(newPokemon, tp.abilitySlot);
                        tp.resetMoves = true;
                    }
                }
            }
        }
        romHandler.setTrainers(currentTrainers, false);
    }

    public void rivalCarriesStarter() {
        pokemonService.checkPokemonRestrictions();
        List<Trainer> currentTrainers = romHandler.getTrainers();
        rivalCarriesStarterUpdate(currentTrainers, "RIVAL", romHandler.isORAS() ? 0 : 1);
        rivalCarriesStarterUpdate(currentTrainers, "FRIEND", 2);
        romHandler.setTrainers(currentTrainers, false);
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
        List<Pokemon> starters = romHandler.getStarters();

        // Yellow needs its own case, unfortunately.
        if (romHandler.isYellow()) {
            // The rival's starter is index 1
            Pokemon rivalStarter = starters.get(1);
            int timesEvolves = pokemonService.numEvolutions(rivalStarter, 2);
            // Yellow does not have abilities
            int abilitySlot = 0;
            // Apply evolutions as appropriate
            if (timesEvolves == 0) {
                for (int j = 1; j <= 3; j++) {
                    changeStarterWithTag(currentTrainers, prefix + j + "-0", rivalStarter, abilitySlot);
                }
                for (int j = 4; j <= 7; j++) {
                    for (int i = 0; i < 3; i++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, rivalStarter, abilitySlot);
                    }
                }
            } else if (timesEvolves == 1) {
                for (int j = 1; j <= 3; j++) {
                    changeStarterWithTag(currentTrainers, prefix + j + "-0", rivalStarter, abilitySlot);
                }
                rivalStarter = pickRandomEvolutionOf(rivalStarter, false);
                for (int j = 4; j <= 7; j++) {
                    for (int i = 0; i < 3; i++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, rivalStarter, abilitySlot);
                    }
                }
            } else if (timesEvolves == 2) {
                for (int j = 1; j <= 2; j++) {
                    changeStarterWithTag(currentTrainers, prefix + j + "-" + 0, rivalStarter, abilitySlot);
                }
                rivalStarter = pickRandomEvolutionOf(rivalStarter, true);
                changeStarterWithTag(currentTrainers, prefix + "3-0", rivalStarter, abilitySlot);
                for (int i = 0; i < 3; i++) {
                    changeStarterWithTag(currentTrainers, prefix + "4-" + i, rivalStarter, abilitySlot);
                }
                rivalStarter = pickRandomEvolutionOf(rivalStarter, false);
                for (int j = 5; j <= 7; j++) {
                    for (int i = 0; i < 3; i++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, rivalStarter, abilitySlot);
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
                int timesEvolves = pokemonService.numEvolutions(thisStarter, 2);
                int abilitySlot = romHandler.getRandomAbilitySlot(thisStarter);
                while (abilitySlot == 3) {
                    // Since starters never have hidden abilities, the rival's starter shouldn't either
                    abilitySlot = romHandler.getRandomAbilitySlot(thisStarter);
                }
                // If a fully evolved pokemon, use throughout
                // Otherwise split by evolutions as appropriate
                if (timesEvolves == 0) {
                    for (int j = 1; j <= highestRivalNum; j++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, abilitySlot);
                    }
                } else if (timesEvolves == 1) {
                    int j = 1;
                    for (; j <= highestRivalNum / 2; j++) {
                        if (getLevelOfStarter(currentTrainers, prefix + j + "-" + i) >= 30) {
                            break;
                        }
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, abilitySlot);
                    }
                    thisStarter = pickRandomEvolutionOf(thisStarter, false);
                    int evolvedAbilitySlot = romHandler.getValidAbilitySlotFromOriginal(thisStarter, abilitySlot);
                    for (; j <= highestRivalNum; j++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, evolvedAbilitySlot);
                    }
                } else if (timesEvolves == 2) {
                    int j = 1;
                    for (; j <= highestRivalNum; j++) {
                        if (getLevelOfStarter(currentTrainers, prefix + j + "-" + i) >= 16) {
                            break;
                        }
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, abilitySlot);
                    }
                    thisStarter = pickRandomEvolutionOf(thisStarter, true);
                    int evolvedAbilitySlot = romHandler.getValidAbilitySlotFromOriginal(thisStarter, abilitySlot);
                    for (; j <= highestRivalNum; j++) {
                        if (getLevelOfStarter(currentTrainers, prefix + j + "-" + i) >= 36) {
                            break;
                        }
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, evolvedAbilitySlot);
                    }
                    thisStarter = pickRandomEvolutionOf(thisStarter, false);
                    evolvedAbilitySlot = romHandler.getValidAbilitySlotFromOriginal(thisStarter, abilitySlot);
                    for (; j <= highestRivalNum; j++) {
                        changeStarterWithTag(currentTrainers, prefix + j + "-" + i, thisStarter, evolvedAbilitySlot);
                    }
                }
            }
        }
    }

    private void changeStarterWithTag(List<Trainer> currentTrainers, String tag, Pokemon starter, int abilitySlot) {
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

        return candidates.get(random.nextInt(candidates.size()));
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

    private Pokemon pickTrainerPokeReplacement(TrainerPokemon tp, Pokemon oldPoke, Type type, List<Pokemon> pickFromList, boolean usePlacementHistory) {
        List<Pokemon> pickFrom;
        List<Pokemon> withoutBannedPokemon;

        boolean wonderGuardAllowed = (!noEarlyWonderGuard) || tp.level >= 20;
        boolean swapThisMegaEvo = swapMegaEvos && tp.canMegaEvolve();

        if (swapThisMegaEvo) {
            pickFrom = pokemonService.getMegaEvolutionsList()
                    .stream()
                    .filter(mega -> mega.method == 1)
                    .map(mega -> mega.from)
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            pickFrom = pickFromList;
        }

        if (usePlacementHistory) {
            // "Distributed" settings
            double placementAverage = getPlacementAverage();
            pickFrom = pickFrom
                    .stream()
                    .filter(pk -> getPlacementHistory(pk) < placementAverage * 2)
                    .collect(Collectors.toList());
            if (pickFrom.isEmpty()) {
                pickFrom = pickFromList;
            }
        } else if (type != null && cachedReplacementLists != null) {
            // "Type Themed" settings
            if (!cachedReplacementLists.containsKey(type)) {
                List<Pokemon> pokemonOfType = includeFormes ? pokemonService.pokemonOfTypeInclFormes(type, noLegendaries) :
                        pokemonService.pokemonOfType(type, noLegendaries);
                pokemonOfType.removeAll(pokemonService.getBannedFormesForPlayerPokemon());
                if (!abilitiesAreRandomized) {
                    List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
                    pokemonOfType.removeAll(abilityDependentFormes);
                }
                if (banIrregularAltFormes) {
                    pokemonOfType.removeAll(romHandler.getIrregularFormes());
                }
                cachedReplacementLists.put(type, pokemonOfType);
            }
            if (swapThisMegaEvo) {
                pickFrom = cachedReplacementLists.get(type)
                        .stream()
                        .filter(pickFrom::contains)
                        .collect(Collectors.toList());
                if (pickFrom.isEmpty()) {
                    pickFrom = cachedReplacementLists.get(type);
                }
            } else {
                pickFrom = cachedReplacementLists.get(type);
            }
        }

        withoutBannedPokemon = pickFrom.stream().filter(pk -> !bannedList.contains(pk)).collect(Collectors.toList());
        if (!withoutBannedPokemon.isEmpty()) {
            pickFrom = withoutBannedPokemon;
        }

        if (usePowerLevels) {
            // start with within 10% and add 5% either direction till we find
            // something
            int currentBST = oldPoke.bstForPowerLevels();
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

            Pokemon chosenPokemon = canPick.get(this.random.nextInt(canPick.size()));
            if (usePlacementHistory) {
                double placementAverage = getPlacementAverage();
                List<Pokemon> filteredPickList = canPick
                        .stream()
                        .filter(pk -> getPlacementHistory(pk) < placementAverage)
                        .collect(Collectors.toList());
                if (filteredPickList.isEmpty()) {
                    filteredPickList = canPick;
                }
                chosenPokemon = filteredPickList.get(this.random.nextInt(filteredPickList.size()));
            }
            return chosenPokemon;
        } else {
            if (wonderGuardAllowed) {
                return pickFrom.get(this.random.nextInt(pickFrom.size()));
            } else {
                Pokemon pk = pickFrom.get(this.random.nextInt(pickFrom.size()));
                while (pk.ability1 == Abilities.wonderGuard
                        || pk.ability2 == Abilities.wonderGuard
                        || pk.ability3 == Abilities.wonderGuard) {
                    pk = pickFrom.get(this.random.nextInt(pickFrom.size()));
                }
                return pk;
            }
        }
    }

    public int getPlacementHistory(Pokemon newPK) {
        return placementHistory.getOrDefault(newPK, 0);
    }

    public void applyLevelModifierToTrainerPokemon(Trainer trainer, int levelModifier) {
        if (levelModifier != 0) {
            for (TrainerPokemon tp : trainer.pokemon) {
                tp.level = Math.min(100, (int) Math.round(tp.level * (1 + levelModifier / 100.0)));
            }
        }
    }

    private Pokemon fullyEvolve(Pokemon pokemon, int trainerIndex) {

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
            int evolutionIndex = (romHandler.getFullyEvolvedRandomSeed() + trainerIndex) % pokemon.evolutionsFrom.size();
            pokemon = pokemon.evolutionsFrom.get(evolutionIndex).to;
            seenMons.add(pokemon);
        }

        return pokemon;
    }

    private void setPlacementHistory(Pokemon newPK) {
        Integer history = getPlacementHistory(newPK);
        placementHistory.put(newPK, history + 1);
    }

    private double getPlacementAverage() {
        return placementHistory.values().stream().mapToInt(e -> e).average().orElse(0);
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

    private Type pickType(boolean weightByFrequency, boolean noLegendaries, boolean allowAltFormes) {
        if (totalTypeWeighting == 0) {
            // Determine weightings
            for (Type t : Type.values()) {
                if (romHandler.typeInGame(t))  {
                    List<Pokemon> pokemonOfType = allowAltFormes ?  pokemonService.pokemonOfTypeInclFormes(t, noLegendaries) :
                            pokemonService.pokemonOfType(t, noLegendaries);
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
            return romHandler.randomType();
        }
    }

    private void setFormeForTrainerPokemon(TrainerPokemon tp, Pokemon pk) {
        tp.formeSuffix = "";
        tp.forme = 0;
        if (pk.formeNumber > 0) {
            tp.forme = pk.formeNumber;
            tp.formeSuffix = pk.formeSuffix;
            tp.pokemon = pk.baseForme;
        }
        else if (tp.pokemon.cosmeticForms > 0) {
            tp.forme = tp.pokemon.getCosmeticFormNumber(this.random.nextInt(tp.pokemon.cosmeticForms));
        } else if (pk.cosmeticForms > 0) {
            tp.forme += pk.getCosmeticFormNumber(this.random.nextInt(pk.cosmeticForms));
        }
    }
}
