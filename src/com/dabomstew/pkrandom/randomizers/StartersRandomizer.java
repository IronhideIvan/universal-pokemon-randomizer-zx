package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.Effectiveness;
import com.dabomstew.pkrandom.pokemon.Evolution;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.pokemon.Type;
import com.dabomstew.pkrandom.randomizers.models.TypeTriangle;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;
import com.dabomstew.pkrandom.services.TypeService;

import java.util.*;
import java.util.stream.Collectors;

public class StartersRandomizer {
    private final Random random;
    private final RomHandler romHandler;
    private final PokemonService pokemonService;
    private final Settings settings;
    private final TypeService typeService;

    // Private variables for logging purposes.
    private List<Pokemon> pickedStarters;
    private TypeTriangle typeTriangleLog;
    private Settings.StarterTypeRestrictions restrictionsLog;

    public StartersRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService, TypeService typeService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
        this.typeService = typeService;
    }

    public void customStarters() {
        int[] customStarters = settings.getCustomStarters();
        pickedStarters = new ArrayList<>();

        List<Pokemon> starterOptionsList = getStarterOptionsList();

        List<Pokemon> allPokemon = settings.isAllowStarterAltFormes()
                ? pokemonService.getAllPokemonInclFormesWithoutNull()
                : pokemonService.getAllPokemonWithoutNull();

        Map<Integer, Pokemon> allPokemonMap = new TreeMap<>();
        for(Pokemon p: allPokemon) {
            if(p.actuallyCosmetic) {
                continue;
            }

            if(!allPokemonMap.containsKey(p.number)) {
                allPokemonMap.put(p.number, p);
            }
        }

        List<Pokemon> banned = getBannedStarters();

        if (customStarters[0] <= 0){
            Pokemon pkmn = getRandomStarter(starterOptionsList, null);
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = getRandomStarter(starterOptionsList, null);
            }
            pickedStarters.add(pkmn);
        } else {
            Pokemon pkmn1 = allPokemonMap.get(customStarters[0]);
            pickedStarters.add(pkmn1);
        }
        if (customStarters[1] <= 0){
            Pokemon pkmn = getRandomStarter(starterOptionsList, null);
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = getRandomStarter(starterOptionsList, null);
            }
            pickedStarters.add(pkmn);
        } else {
            Pokemon pkmn2 = allPokemonMap.get(customStarters[1]);
            pickedStarters.add(pkmn2);
        }

        if (romHandler.isYellow()) {
            romHandler.setStarters(pickedStarters);
        } else {
            if (customStarters[2] <= 0){
                Pokemon pkmn  = getRandomStarter(starterOptionsList, null);
                while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                    pkmn = getRandomStarter(starterOptionsList, null);
                }
                pickedStarters.add(pkmn);
            } else {
                Pokemon pkmn3 = allPokemonMap.get(customStarters[2]);
                pickedStarters.add(pkmn3);
            }
            if (romHandler.starterCount() > 3) {
                List<Pokemon> twoEvosPokes = getTwoEvoPokes();
                twoEvosPokes.removeIf(banned::contains);

                for (int i = 3; i < romHandler.starterCount(); i++) {
                    Pokemon pkmn = twoEvosPokes.get(random.nextInt(twoEvosPokes.size()));
                    while (pickedStarters.contains(pkmn)) {
                        pkmn = twoEvosPokes.get(random.nextInt(twoEvosPokes.size()));
                    }
                    pickedStarters.add(pkmn);
                }
                romHandler.setStarters(pickedStarters);
            } else {
                romHandler.setStarters(pickedStarters);
            }
        }
    }

    public void randomizeStarters() {

        pickedStarters = new ArrayList<>();
        int starterCount = romHandler.starterCount();
        // Get our list of available starters
        List<Pokemon> optionList = getStarterOptionsList();

        // Initialize our type triangle, if we are using one.
        TypeTriangle typeTriangle = null;
        List<Type> typeTriangleList = null;

        Settings.StarterTypeRestrictions restrictions = settings.getStarterTypeRestrictions();
        // Used purely for logging.
        restrictionsLog = restrictions;
        if(starterCount == 3) {
            if(restrictions == Settings.StarterTypeRestrictions.STRONG_TRIANGLE
                || restrictions == Settings.StarterTypeRestrictions.WEAK_TRIANGLE
                || restrictions == Settings.StarterTypeRestrictions.PERFECT_TRIANGLE) {

                typeTriangle = getRandomTypeTriangle(restrictions, optionList);
                if(typeTriangle != null) {
                    typeTriangleList = typeTriangle.asList();
                }
                else {
                    // Default to unique types if a Type Triangle doesn't exist.
                    restrictionsLog = restrictions = Settings.StarterTypeRestrictions.UNIQUE_TYPES;
                }
            }
        }

        for (int i = 0; i < starterCount; i++) {
            Pokemon pkmn;
            Type triangleType = null;

            if(typeTriangle != null) {
                triangleType = typeTriangleList.get(i);
            }

            pkmn = getRandomStarter(optionList, triangleType);

            while (pickedStarters.contains(pkmn)
                    || pkmn.actuallyCosmetic
                    || (restrictions == Settings.StarterTypeRestrictions.UNIQUE_TYPES && pkmn.sharesAnyTypes(pickedStarters))
                    || (triangleType != null && !typeTriangle.doesPokemonMatchOnlyOneType(pkmn))) {

                pkmn = getRandomStarter(optionList, triangleType);
            }
            pickedStarters.add(pkmn);
        }

        romHandler.setStarters(pickedStarters);

        // These variables are purely for logging purposes
        typeTriangleLog = typeTriangle;
    }

    public List<Pokemon> getPickedStarters() {
        return pickedStarters;
    }

    public TypeTriangle getTypeTriangleLog() {
        return typeTriangleLog;
    }

    public Settings.StarterTypeRestrictions getRestrictionsLog() {
        return restrictionsLog;
    }

    private Pokemon getRandomStarter(List<Pokemon> optionList, Type t) {
        if(t != null) {
            Map<Type, List<Pokemon>> typeSeparatedPokes = pokemonService.separatePokemonByType(optionList);
            List<Pokemon> list = typeSeparatedPokes.get(t);
            return list.get(random.nextInt(list.size()));
        }
        else {
            return optionList.get(random.nextInt(optionList.size()));
        }
    }

    private List<Pokemon> getStarterOptionsList() {
        List<Pokemon> banned = getBannedStarters();
        List<Pokemon> optionList = null;
        if(settings.getStartersMod() == Settings.StartersMod.COMPLETELY_RANDOM
            || settings.getStartersMod() == Settings.StartersMod.CUSTOM) {
            optionList = settings.isAllowStarterAltFormes()
                    ? pokemonService.getAllPokemonInclFormesWithoutNull()
                    : pokemonService.getAllPokemonWithoutNull();
        }
        else if(settings.getStartersMod() == Settings.StartersMod.RANDOM_WITH_TWO_EVOLUTIONS) {
            optionList = getTwoEvoPokes();
        }
        else {
            throw new RandomizationException("Unknown StartersMod: " + settings.getStartersMod().toString());
        }

        optionList.removeIf(banned::contains);
        return optionList;
    }

    private List<Pokemon> getBannedStarters() {
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();

        List<Pokemon> banned = pokemonService.getBannedFormesForPlayerPokemon();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }

        List<Pokemon> allPokes = pokemonService.getAllPokemonInclFormesWithoutNull();
        int[] customStarters = null;
        if(settings.getStartersMod() == Settings.StartersMod.CUSTOM) {
            customStarters = settings.getCustomStarters();
        }
        for(Pokemon p: allPokes) {
            if(settings.isForceMonotypeStarters() && p.secondaryType != null) {
                banned.add(p);
                continue;
            }

            if(customStarters != null) {
                boolean isAdded = false;
                for(int i = 0; i < customStarters.length; ++i) {
                    int starterNum = customStarters[i];
                    if(starterNum >= 0 && p.number == starterNum) {
                        banned.add(p);
                        isAdded = true;
                        break;
                    }
                }
                if(isAdded) {
                    continue;
                }
            }
        }

        // settings.getCustomStarters()
        return banned;
    }

    private List<Pokemon> getTwoEvoPokes() {
        // Prepare the list
        List<Pokemon> twoEvoPokes = new ArrayList<>();
        List<Pokemon> allPokes =
                settings.isAllowStarterAltFormes() ?
                        romHandler.getPokemonInclFormes()
                                .stream()
                                .filter(pk -> pk == null || !pk.actuallyCosmetic)
                                .collect(Collectors.toList()) :
                        romHandler.getPokemon();
        for (Pokemon pk : allPokes) {
            if (pk != null && pk.evolutionsTo.isEmpty() && !pk.evolutionsFrom.isEmpty()) {
                // Potential candidate
                for (Evolution ev : pk.evolutionsFrom) {
                    // If any of the targets here evolve, the original
                    // Pokemon has 2+ stages.
                    if (!ev.to.evolutionsFrom.isEmpty()) {
                        twoEvoPokes.add(pk);
                        break;
                    }
                }
            }
        }
        return twoEvoPokes;
    }

    private TypeTriangle getRandomTypeTriangle(Settings.StarterTypeRestrictions restrictions, List<Pokemon> optionList) {
        List<TypeTriangle> allTypeTriangles = getAllTypeTriangles(optionList);

        if(allTypeTriangles.isEmpty()) {
            return null;
        }

        if(restrictions == Settings.StarterTypeRestrictions.WEAK_TRIANGLE) {
            return allTypeTriangles.get(random.nextInt(allTypeTriangles.size()));
        }

        // Weed out the ones which aren't "True" type triangles.
        List<TypeTriangle> trueTypeTriangleList = new ArrayList<>();
        for(TypeTriangle tri: allTypeTriangles) {
            List<Type> triList = tri.asList();

            boolean isValid = true;
            for(int i = 0; i < triList.size(); ++i) {
                Type typeA = triList.get(i);
                Type typeB = i == triList.size() - 1 ? triList.getFirst() : triList.get(i + 1);
                Type typeC = i == 0 ? triList.getLast() : triList.get(i - 1);

                // First, check that type A is super effective against type B.
                if(typeService.getTypeEffectiveness(typeA, typeB) != Effectiveness.DOUBLE) {
                    isValid = false;
                    break;
                }

                // Next, check that type A is weak against type C.
                if(typeService.getTypeEffectiveness(typeA, typeC) != Effectiveness.HALF) {
                    isValid = false;
                    break;
                }

                if(restrictions == Settings.StarterTypeRestrictions.PERFECT_TRIANGLE
                    && typeService.getTypeEffectiveness(typeA, typeA) != Effectiveness.HALF) {
                    isValid = false;
                    break;
                }
            }

            if(isValid) {
                trueTypeTriangleList.add(tri);
            }
        }

        TypeTriangle ret;

        if(!trueTypeTriangleList.isEmpty()) {
            ret = trueTypeTriangleList.get(random.nextInt(trueTypeTriangleList.size()));
        }
        else {
            restrictionsLog = Settings.StarterTypeRestrictions.WEAK_TRIANGLE;
            ret = allTypeTriangles.get(random.nextInt(allTypeTriangles.size()));
        }

        return ret;
    }

    private List<TypeTriangle> getAllTypeTriangles(List<Pokemon> optionList) {
        Set<Type> typesInGameSet = new TreeSet<>();

        if(optionList == null) {
            typesInGameSet = new TreeSet<>(typeService.getAllTypesInGame());
        }
        else {
            for(Pokemon poke: optionList) {
                typesInGameSet.add(poke.primaryType);
                if(poke.secondaryType != null) {
                    typesInGameSet.add(poke.secondaryType);
                }
            }
        }

        // For each type in the game, try to find three types that have a circular
        // relationship of super-effectiveness with each other. I.E. Type A is effective against
        // Type B, Type B is effective against Type C, and Type C is effective against Type A.
        List<TypeTriangle> typeTriangleList = new ArrayList<>();
        for (Type typeA: typesInGameSet) {

            List<Type> typeASuperEffectiveList = typeService.getSuperEffectiveTypes(typeA);
            for (Type typeB: typeASuperEffectiveList) {

                // Each type must be distinct
                if(typeB == typeA || !typesInGameSet.contains(typeB)) {
                    continue;
                }

                List<Type> typeBSuperEffectiveList = typeService.getSuperEffectiveTypes(typeB);
                for(Type typeC: typeBSuperEffectiveList) {

                    // Each type must be distinct
                    if(typeC == typeB || typeC == typeA || !typesInGameSet.contains(typeC)) {
                        continue;
                    }

                    if(typeService.getTypeEffectiveness(typeC, typeA) != Effectiveness.DOUBLE) {
                        continue;
                    }

                    TypeTriangle typeTriangle = new TypeTriangle(typeA, typeB, typeC);

                    // It's possible for duplicates to be inserted if the type triangle's order is slightly changed.
                    if(!typeTriangle.matchesAny(typeTriangleList)) {
                        typeTriangleList.add(typeTriangle);
                    }
                }
            }
        }

        return typeTriangleList;
    }
}
