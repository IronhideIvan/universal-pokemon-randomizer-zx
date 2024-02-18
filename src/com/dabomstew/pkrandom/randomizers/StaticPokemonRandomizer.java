package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.*;
import java.util.stream.Collectors;

public class StaticPokemonRandomizer {
    private final Random random;
    private final RomHandler romHandler;
    private final PokemonService pokemonService;
    private final Settings settings;

    public StaticPokemonRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
    }

    public void onlyChangeStaticLevels() {
        int levelModifier = settings.getStaticLevelModifier();

        List<StaticEncounter> currentStaticPokemon = romHandler.getStaticPokemon();
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
        romHandler.setStaticPokemon(currentStaticPokemon);
    }

    public void randomizeStaticPokemon() {
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
        pokemonService.checkPokemonRestrictions();
        List<StaticEncounter> currentStaticPokemon = romHandler.getStaticPokemon();
        List<StaticEncounter> replacements = new ArrayList<>();
        List<Pokemon> banned = romHandler.bannedForStaticPokemon();
        banned.addAll(pokemonService.getBannedFormesForPlayerPokemon());
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        boolean reallySwapMegaEvos = romHandler.forceSwapStaticMegaEvos() || swapMegaEvos;

        Map<Integer,Integer> specialMusicStaticChanges = new HashMap<>();
        List<Integer> changeMusicStatics = new ArrayList<>();
        if (correctStaticMusic) {
            changeMusicStatics = romHandler.getSpecialMusicStatics();
        }

        if (swapLegendaries) {
            List<Pokemon> legendariesLeft = new ArrayList<>(pokemonService.getOnlyLegendaryList());
            if (allowAltFormes) {
                legendariesLeft.addAll(pokemonService.getOnlyLegendaryAltsList());
                legendariesLeft =
                        legendariesLeft
                                .stream()
                                .filter(pk -> !pk.actuallyCosmetic)
                                .collect(Collectors.toList());
            }
            List<Pokemon> nonlegsLeft = new ArrayList<>(pokemonService.getNoLegendaryList());
            if (allowAltFormes) {
                nonlegsLeft.addAll(pokemonService.getNoLegendaryAltsList());
                nonlegsLeft =
                        nonlegsLeft
                                .stream()
                                .filter(pk -> !pk.actuallyCosmetic)
                                .collect(Collectors.toList());
            }
            List<Pokemon> ultraBeastsLeft = new ArrayList<>(pokemonService.getUltraBeastList());
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
                        newPK = getMegaEvoPokemon(pokemonService.getOnlyLegendaryList(), legendariesLeft, newStatic);
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
                } else if (pokemonService.getUltraBeastList().contains(old.pkmn)) {
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
                        newPK = getMegaEvoPokemon(pokemonService.getNoLegendaryList(), nonlegsLeft, newStatic);
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
                    pokemonService.getMainPokemonListInclFormes()
                            .stream()
                            .filter(pk -> !pk.actuallyCosmetic)
                            .collect(Collectors.toList());
            List<Pokemon> pokemonLeft = new ArrayList<>(!allowAltFormes ? pokemonService.getMainPokemonList() : listInclFormesExclCosmetics);
            pokemonLeft.removeAll(banned);

            List<Pokemon> pokemonPool = new ArrayList<>(pokemonLeft);

            List<Integer> mainGameLegendaries = romHandler.getMainGameLegendaries();
            for (StaticEncounter old : currentStaticPokemon) {
                StaticEncounter newStatic = cloneStaticEncounter(old);
                Pokemon newPK;
                Pokemon oldPK = old.pkmn;
                if (old.forme > 0) {
                    oldPK = romHandler.getAltFormeOfPokemon(oldPK, old.forme);
                }
                Integer oldBST = oldPK.bstForPowerLevels();
                if (oldBST >= 600 && limit600) {
                    if (reallySwapMegaEvos && old.canMegaEvolve()) {
                        newPK = getMegaEvoPokemon(pokemonService.getMainPokemonList(), pokemonLeft, newStatic);
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
                                pokemonService.getMegaEvolutionsList()
                                        .stream()
                                        .filter(mega -> mega.method == 1)
                                        .map(mega -> mega.from)
                                        .distinct()
                                        .filter(pokemonLeft::contains)
                                        .collect(Collectors.toList());
                        if (megaEvoPokemonLeft.isEmpty()) {
                            megaEvoPokemonLeft =
                                    pokemonService.getMegaEvolutionsList()
                                            .stream()
                                            .filter(mega -> mega.method == 1)
                                            .map(mega -> mega.from)
                                            .distinct()
                                            .filter(pokemonService.getMainPokemonList()::contains)
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
                    pokemonService.getMainPokemonListInclFormes()
                            .stream()
                            .filter(pk -> !pk.actuallyCosmetic)
                            .collect(Collectors.toList());
            List<Pokemon> pokemonLeft = new ArrayList<>(!allowAltFormes ? pokemonService.getMainPokemonList() : listInclFormesExclCosmetics);
            pokemonLeft.removeAll(banned);

            List<Pokemon> pokemonPool = new ArrayList<>(pokemonLeft);

            for (StaticEncounter old : currentStaticPokemon) {
                StaticEncounter newStatic = cloneStaticEncounter(old);
                Pokemon newPK;
                if (reallySwapMegaEvos && old.canMegaEvolve()) {
                    newPK = getMegaEvoPokemon(pokemonService.getMainPokemonList(), pokemonLeft, newStatic);
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
            romHandler.applyCorrectStaticMusic(specialMusicStaticChanges);
        }

        // Save
        romHandler.setStaticPokemon(replacements);
    }

    public void randomizeTotemPokemon() {
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

        pokemonService.checkPokemonRestrictions();
        List<TotemPokemon> currentTotemPokemon = romHandler.getTotemPokemon();
        List<TotemPokemon> replacements = new ArrayList<>();
        List<Pokemon> banned = romHandler.bannedForStaticPokemon();
        if (!abilitiesAreRandomized) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        List<Pokemon> listInclFormesExclCosmetics =
                pokemonService.getMainPokemonListInclFormes()
                        .stream()
                        .filter(pk -> !pk.actuallyCosmetic)
                        .collect(Collectors.toList());
        List<Pokemon> pokemonLeft = new ArrayList<>(!allowAltFormes ? pokemonService.getMainPokemonList() : listInclFormesExclCosmetics);
        pokemonLeft.removeAll(banned);
        for (TotemPokemon old : currentTotemPokemon) {
            TotemPokemon newTotem = new TotemPokemon();
            newTotem.heldItem = old.heldItem;
            if (randomizeTotem) {
                Pokemon newPK;
                Pokemon oldPK = old.pkmn;
                if (old.forme > 0) {
                    oldPK = romHandler.getAltFormeOfPokemon(oldPK, old.forme);
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
                    pokemonLeft.addAll(!allowAltFormes ? pokemonService.getMainPokemonList() : listInclFormesExclCosmetics);
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
                        oldAllyPK = romHandler.getAltFormeOfPokemon(oldAllyPK, oldAlly.forme);
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
                        pokemonLeft.addAll(!allowAltFormes ? pokemonService.getMainPokemonList() : listInclFormesExclCosmetics);
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
                    List<Integer> consumableList = romHandler.getAllConsumableHeldItems();
                    newTotem.heldItem = consumableList.get(this.random.nextInt(consumableList.size()));
                }
            }

            replacements.add(newTotem);
        }

        // Save
        romHandler.setTotemPokemon(replacements);
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

    private Pokemon getMegaEvoPokemon(List<Pokemon> fullList, List<Pokemon> pokemonLeft, StaticEncounter newStatic) {
        List<MegaEvolution> megaEvos = pokemonService.getMegaEvolutionsList();
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
        return canPick.get(this.random.nextInt(canPick.size()));
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
}
