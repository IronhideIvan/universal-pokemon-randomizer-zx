package com.dabomstew.pkrandom.services;

import com.dabomstew.pkrandom.pokemon.Pokemon;

public class PokemonEncounterRate {
    public final Pokemon pokemon;
    public int numEncounters;

    public PokemonEncounterRate(Pokemon pokemon) {
        this.pokemon = pokemon;
        numEncounters = 0;
    }
}
