package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.Evolution;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class StartersRandomizer {
    private final Random random;
    private final RomHandler romHandler;
    private final PokemonService pokemonService;
    private final Settings settings;

    private List<Pokemon> pickedStarters;

    public StartersRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
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

        List<Pokemon> banned = pokemonService.getBannedFormesForPlayerPokemon();
        pickedStarters = new ArrayList<>();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }
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
                for (int i = 3; i < romHandler.starterCount(); i++) {
                    Pokemon pkmn = random2EvosPokemon(allowAltFormes);
                    while (pickedStarters.contains(pkmn)) {
                        pkmn = random2EvosPokemon(allowAltFormes);
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
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        boolean allowAltFormes = settings.isAllowStarterAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean forceUniqueTypes = settings.isForceUniqueStarterTypes();

        int starterCount = romHandler.starterCount();
        pickedStarters = new ArrayList<>();
        List<Pokemon> banned = pokemonService.getBannedFormesForPlayerPokemon();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        for (int i = 0; i < starterCount; i++) {
            Pokemon pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            while (pickedStarters.contains(pkmn)
                    || banned.contains(pkmn)
                    || pkmn.actuallyCosmetic
                    || (forceUniqueTypes && pkmn.sharesAnyTypes(pickedStarters))) {

                pkmn = allowAltFormes ? pokemonService.getRandomPokemonInclFormes() : pokemonService.getRandomPokemon();
            }
            pickedStarters.add(pkmn);
        }
        romHandler.setStarters(pickedStarters);
    }

    public void randomizeBasicTwoEvosStarters() {
        boolean abilitiesUnchanged = settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED;
        boolean allowAltFormes = settings.isAllowStarterAltFormes();
        boolean banIrregularAltFormes = settings.isBanIrregularAltFormes();
        boolean forceUniqueTypes = settings.isForceUniqueStarterTypes();

        int starterCount = romHandler.starterCount();
        pickedStarters = new ArrayList<>();
        List<Pokemon> banned = pokemonService.getBannedFormesForPlayerPokemon();
        if (abilitiesUnchanged) {
            List<Pokemon> abilityDependentFormes = pokemonService.getAbilityDependentFormes();
            banned.addAll(abilityDependentFormes);
        }
        if (banIrregularAltFormes) {
            banned.addAll(romHandler.getIrregularFormes());
        }
        for (int i = 0; i < starterCount; i++) {
            Pokemon pkmn = random2EvosPokemon(allowAltFormes);
            while (pickedStarters.contains(pkmn)
                    || banned.contains(pkmn)
                    || pkmn.actuallyCosmetic
                    || (forceUniqueTypes && pkmn.sharesAnyTypes(pickedStarters))) {

                pkmn = random2EvosPokemon(allowAltFormes);
            }
            pickedStarters.add(pkmn);
        }
        romHandler.setStarters(pickedStarters);
    }

    public List<Pokemon> getPickedStarters() {
        return pickedStarters;
    }

    private List<Pokemon> twoEvoPokes;
    private Pokemon random2EvosPokemon(boolean allowAltFormes) {
        if (twoEvoPokes == null) {
            // Prepare the list
            twoEvoPokes = new ArrayList<>();
            List<Pokemon> allPokes =
                    allowAltFormes ?
                            romHandler.getPokemonInclFormes()
                                    .stream()
                                    .filter(pk -> pk == null || !pk.actuallyCosmetic)
                                    .collect(Collectors.toList()) :
                            romHandler.getPokemon();
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
        return twoEvoPokes.get(this.random.nextInt(twoEvoPokes.size()));
    }
}
