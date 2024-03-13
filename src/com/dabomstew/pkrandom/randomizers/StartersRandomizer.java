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

    private List<Pokemon> pickedStarters;

    public StartersRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService, TypeService typeService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
        this.typeService = typeService;
    }

    public void customStarters() {
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        int[] customStarters = settings.getCustomStarters();
        boolean allowAltFormes = settings.isAllowStarterAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();

        List<Pokemon> romPokemon = romHandler.getPokemonInclFormes()
                .stream()
                .filter(pk -> pk == null || !pk.actuallyCosmetic)
                .collect(Collectors.toList());

        List<Pokemon> banned = getBannedStarters();

        // loop to add chosen pokemon to banned, preventing it from being a random option.
        for (int i = 0; i < customStarters.length; i = i + 1){
            if (!(customStarters[i] - 1 == 0)){
                banned.add(romPokemon.get(customStarters[i] - 1));
            }
        }
        if (customStarters[0] - 1 == 0){
            Pokemon pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            }
            pickedStarters.add(pkmn);
        } else {
            Pokemon pkmn1 = romPokemon.get(customStarters[0] - 1);
            pickedStarters.add(pkmn1);
        }
        if (customStarters[1] - 1 == 0){
            Pokemon pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            }
            pickedStarters.add(pkmn);
        } else {
            Pokemon pkmn2 = romPokemon.get(customStarters[1] - 1);
            pickedStarters.add(pkmn2);
        }

        if (romHandler.isYellow()) {
            romHandler.setStarters(pickedStarters);
        } else {
            if (customStarters[2] - 1 == 0){
                Pokemon pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
                while (pickedStarters.contains(pkmn) || banned.contains(pkmn) || pkmn.actuallyCosmetic) {
                    pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
                }
                pickedStarters.add(pkmn);
            } else {
                Pokemon pkmn3 = romPokemon.get(customStarters[2] - 1);
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
        if(starterCount == 3) {
            if(settings.getStarterTypeRestrictions() == Settings.StarterTypeRestrictions.STRONG_TRIANGLE
                || settings.getStarterTypeRestrictions() == Settings.StarterTypeRestrictions.WEAK_TRIANGLE
                || settings.getStarterTypeRestrictions() == Settings.StarterTypeRestrictions.PERFECT_TRIANGLE) {

                int index = 0;
                do {
                    typeTriangle = getRandomTypeTriangle(settings.getStarterTypeRestrictions());
                    typeTriangleList = typeTriangle.asList();
                    ++index;
                }
                while(!isPokemonForTypeTriangleExist(typeTriangle, optionList) && index < 3);

                if(index == 3) {
                    throw new RandomizationException("Unable to find pokemon for type triangle: " + typeTriangle.toString());
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
                    || (settings.getStarterTypeRestrictions() == Settings.StarterTypeRestrictions.UNIQUE_TYPES && pkmn.sharesAnyTypes(pickedStarters))
                    || (triangleType != null && !typeTriangle.doesPokemonMatchOnlyOneType(pkmn))) {

                pkmn = getRandomStarter(optionList, triangleType);
            }
            pickedStarters.add(pkmn);
        }
        romHandler.setStarters(pickedStarters);
    }

    public List<Pokemon> getPickedStarters() {
        return pickedStarters;
    }

    private boolean isPokemonForTypeTriangleExist(TypeTriangle typeTriangle, List<Pokemon> pokemonList) {
        List<Type> typeTriangleList = typeTriangle.asList();

        for(Type triangleType: typeTriangleList) {
            boolean isExist = false;

            for(Pokemon poke: pokemonList) {
                if(poke.hasType(triangleType)) {
                    isExist = true;
                    break;
                }
            }

            if(!isExist) {
                return false;
            }
        }

        return true;
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
        if(settings.getStartersMod() == Settings.StartersMod.COMPLETELY_RANDOM) {
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
        if(settings.isForceMonotypeStarters()) {
            List<Pokemon> allPokes = pokemonService.getAllPokemonInclFormesWithoutNull();
            for(Pokemon p: allPokes) {
                if(p.secondaryType != null) {
                    banned.add(p);
                }
            }
        }
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

    private TypeTriangle getRandomTypeTriangle(Settings.StarterTypeRestrictions restrictions) {
        List<TypeTriangle> allTypeTriangles = getAllTypeTriangles();

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
            ret = allTypeTriangles.get(random.nextInt(allTypeTriangles.size()));
        }

        return ret;
    }

    private List<TypeTriangle> getAllTypeTriangles() {
        List<Type> typesInGame = typeService.getAllTypesInGame();

        // For each type in the game, try to find three types that have a circular
        // relationship of super-effectiveness with each other. I.E. Type A is effective against
        // Type B, Type B is effective against Type C, and Type C is effective against Type A.
        List<TypeTriangle> typeTriangleList = new ArrayList<>();
        for (Type typeA: typesInGame) {

            List<Type> typeASuperEffectiveList = typeService.getSuperEffectiveTypes(typeA);
            for (Type typeB: typeASuperEffectiveList) {

                // Each type must be distinct
                if(typeB == typeA) {
                    continue;
                }

                List<Type> typeBSuperEffectiveList = typeService.getSuperEffectiveTypes(typeB);
                for(Type typeC: typeBSuperEffectiveList) {

                    // Each type must be distinct
                    if(typeC == typeB || typeC == typeA) {
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
