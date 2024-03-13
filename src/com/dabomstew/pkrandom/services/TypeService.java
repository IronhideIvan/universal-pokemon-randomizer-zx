package com.dabomstew.pkrandom.services;

import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.Effectiveness;
import com.dabomstew.pkrandom.pokemon.Type;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TypeService {
    private final RomHandler romHandler;
    private final Random random;
    private final Settings settings;

    private final boolean isTypeEffectivenessUpdated;

    public TypeService(Random random, RomHandler romHandler, Settings settings) {
        this.romHandler = romHandler;
        this.random = random;
        this.settings = settings;

        isTypeEffectivenessUpdated = MiscTweak.UPDATE_TYPE_EFFECTIVENESS.isMiscTweakSet(settings.getCurrentMiscTweaks());
    }

    public Effectiveness getTypeEffectiveness(Type attackingType, Type defendingType) {
        return Effectiveness.effectiveness(attackingType, defendingType, romHandler.generationOfPokemon(), isTypeEffectivenessUpdated);
    }

    public List<Type> getSuperEffectiveTypes(Type attackingType) {
        return Effectiveness.superEffective(attackingType, romHandler.generationOfPokemon(), isTypeEffectivenessUpdated);
    }

    public List<Type> getAllTypesInGame() {
        List<Type> typesInGame = new ArrayList<>();
        for (Type t: Type.getAllTypes(romHandler.generationOfPokemon())) {
            if(romHandler.typeInGame(t)) {
                typesInGame.add(t);
            }
        }
        return typesInGame;
    }
}
