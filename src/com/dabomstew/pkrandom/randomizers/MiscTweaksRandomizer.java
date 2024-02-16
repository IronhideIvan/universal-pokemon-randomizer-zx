package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MiscTweaksRandomizer {
    private final Random random;
    private final RomHandler romHandler;
    private final PokemonService pokemonService;
    private final Settings settings;

    public MiscTweaksRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
    }

    public void applyMiscTweaks() {
        int selectedMiscTweaks = settings.getCurrentMiscTweaks();

        int codeTweaksAvailable = romHandler.miscTweaksAvailable();
        List<MiscTweak> tweaksToApply = new ArrayList<>();

        for (MiscTweak mt : MiscTweak.allTweaks) {
            if ((codeTweaksAvailable & mt.getValue()) > 0 && (selectedMiscTweaks & mt.getValue()) > 0) {
                tweaksToApply.add(mt);
            }
        }

        // Sort so priority is respected in tweak ordering.
        Collections.sort(tweaksToApply);

        // Now apply in order.
        for (MiscTweak mt : tweaksToApply) {
            if(mt == MiscTweak.RANDOMIZE_CATCHING_TUTORIAL) {
                boolean isSuccess = false;
                do {
                    isSuccess = romHandler.setCatchingTutorial(pokemonService.getRandomPokemon(), pokemonService.getRandomPokemon());
                }
                while (!isSuccess);
            }
            else {
                romHandler.applyMiscTweak(mt);
            }
        }
    }
}
