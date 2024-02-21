package com.dabomstew.pkrandom.services;

import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.Encounter;
import com.dabomstew.pkrandom.pokemon.EncounterSet;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

import java.util.*;

public class EncounterService {
    private final RomHandler romHandler;
    private final Random random;
    private final Settings settings;
    private final PokemonService pokemonService;

    public EncounterService(Random random, RomHandler romHandler, Settings settings, PokemonService pokemonService) {
        this.romHandler = romHandler;
        this.random = random;
        this.settings = settings;
        this.pokemonService = pokemonService;
    }

    public List<PokemonEncounterRate> getPokemonEncounterRates() {
        List<EncounterSet> encounterSets = romHandler.getEncounters(settings.isUseTimeBasedEncounters());
        Map<Pokemon, PokemonEncounterRate> encounterRates = new TreeMap<>();

        for(EncounterSet area: encounterSets) {
            for(Encounter enc : area.encounters) {
                if(!encounterRates.containsKey(enc.pokemon)) {
                    encounterRates.put(enc.pokemon, new PokemonEncounterRate(enc.pokemon));
                    encounterRates.get(enc.pokemon).numEncounters += 1;
                }
                else {
                    encounterRates.get(enc.pokemon).numEncounters += 1;
                }
            }
        }

        List<PokemonEncounterRate> encounterRateList = new ArrayList<>(encounterRates.values());
        List<Pokemon> allPokemon = pokemonService.getMainPokemonList();
        for(Pokemon p: allPokemon) {
            if(!encounterRates.containsKey(p)) {
                encounterRateList.add(new PokemonEncounterRate(p));
            }
        }

        return encounterRateList;
    }
}
