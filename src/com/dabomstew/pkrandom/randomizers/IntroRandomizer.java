package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.Random;

public class IntroRandomizer {
    private final RomHandler romHandler;
    private final PokemonService pokemonService;

    public IntroRandomizer(RomHandler romHandler, PokemonService pokemonService) {
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
    }

    public void randomizeIntroPokemon() {
        boolean success = false;
        while (!success) {
            Pokemon randomPokemon = pokemonService.getRandomPokemon();
            success = romHandler.setIntroPokemon(randomPokemon);
        }
    }
}
