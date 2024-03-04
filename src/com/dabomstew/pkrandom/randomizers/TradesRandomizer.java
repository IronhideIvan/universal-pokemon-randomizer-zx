package com.dabomstew.pkrandom.randomizers;

import com.dabomstew.pkrandom.CustomNamesSet;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.exceptions.RandomizationException;
import com.dabomstew.pkrandom.pokemon.IngameTrade;
import com.dabomstew.pkrandom.pokemon.ItemList;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import com.dabomstew.pkrandom.services.EncounterService;
import com.dabomstew.pkrandom.services.PokemonEncounterRate;
import com.dabomstew.pkrandom.services.PokemonService;

import java.util.*;

public class TradesRandomizer {
    private final Random random;
    private final RomHandler romHandler;
    private final PokemonService pokemonService;
    private final Settings settings;
    private final EncounterService encounterService;

    public TradesRandomizer(Random random, Settings settings, RomHandler romHandler, PokemonService pokemonService, EncounterService encounterService) {
        this.random = random;
        this.romHandler = romHandler;
        this.pokemonService = pokemonService;
        this.settings = settings;
        this.encounterService = encounterService;
    }

    public void randomizeIngameTrades() {
        boolean randomizeRequest = settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED;
        boolean randomNickname = settings.isRandomizeInGameTradesNicknames();
        boolean randomOT = settings.isRandomizeInGameTradesOTs();
        boolean randomStats = settings.isRandomizeInGameTradesIVs();
        boolean randomItem = settings.isRandomizeInGameTradesItems();
        CustomNamesSet customNames = settings.getCustomNames();

        pokemonService.checkPokemonRestrictions();
        // Process trainer names
        List<String> trainerNames = new ArrayList<>();
        // Check for the file
        if (randomOT) {
            int maxOT = romHandler.maxTradeOTNameLength();
            for (String trainername : customNames.getTrainerNames()) {
                int len = romHandler.internalStringLength(trainername);
                if (len <= maxOT && !trainerNames.contains(trainername)) {
                    trainerNames.add(trainername);
                }
            }
        }

        // Process nicknames
        List<String> nicknames = new ArrayList<>();
        // Check for the file
        if (randomNickname) {
            int maxNN = romHandler.maxTradeNicknameLength();
            for (String nickname : customNames.getPokemonNicknames()) {
                int len = romHandler.internalStringLength(nickname);
                if (len <= maxNN && !nicknames.contains(nickname)) {
                    nicknames.add(nickname);
                }
            }
        }

        // get old trades
        List<IngameTrade> trades = romHandler.getIngameTrades();
        Set<Pokemon> usedRequests = new TreeSet<>();
        Set<Pokemon> usedGivens = new TreeSet<>();
        List<String> usedOTs = new ArrayList<>();
        List<String> usedNicknames = new ArrayList<>();
        ItemList possibleItems = romHandler.getAllowedItems();

        int nickCount = nicknames.size();
        int trnameCount = trainerNames.size();

        List<Pokemon> banned = getBannedPokemon();

        // Determine our possible options for our trades
        List<Pokemon> allPossiblePokemon = new ArrayList<>(pokemonService.getAllPokemonWithoutNull());
        List<Pokemon> rareGiveList = new ArrayList<>();
        List<Pokemon> rareRequestList = new ArrayList<>();

        allPossiblePokemon.removeIf(banned::contains);

        // set up our list of rare pokemon.
        if(settings.isRareGivensInGameTrades() || settings.isRareRequestInGameTrades()) {
            List<PokemonEncounterRate> encounterRates = encounterService.getPokemonEncounterRates();
            encounterRates.sort(Comparator.comparingInt((PokemonEncounterRate a) -> a.numEncounters));

            // get the median encounter rate of all wild obtainable pokemon;
            int indexOfFirstObtainableWildPoke = -1;

            // Add all unobtainable pokemon
            for(int i = 0; i < encounterRates.size(); ++i) {
                Pokemon p = encounterRates.get(i).pokemon;
                if(banned.contains(p)) {
                    continue;
                }

                PokemonEncounterRate encounterRate = encounterRates.get(i);
                if(encounterRate.numEncounters == 0) {
                    rareGiveList.add(p);
                }
                else {
                    indexOfFirstObtainableWildPoke = i;
                    // We have all we need for now.
                    break;
                }
            }

            if(settings.isRareRequestInGameTrades() && indexOfFirstObtainableWildPoke >= 0) {
                int optionCount = (encounterRates.size() - indexOfFirstObtainableWildPoke) / 2;
                if(optionCount < trades.size()) {
                    optionCount = encounterRates.size() - indexOfFirstObtainableWildPoke;
                }

                for(int i = 0; i < optionCount; ++i) {
                    int index = indexOfFirstObtainableWildPoke + i;
                    if(banned.contains(encounterRates.get(index).pokemon)) {
                        continue;
                    }

                    rareRequestList.add(encounterRates.get(index).pokemon);
                }

                while(rareRequestList.size() < trades.size()) {
                    Pokemon p = this.pokemonService.getRandomPokemon();
                    if(!rareGiveList.contains(p) && !rareRequestList.contains(p) && !banned.contains(p)) {
                        rareRequestList.add(p);
                    }
                }
            }

            if(settings.isRareGivensInGameTrades() && rareGiveList.size() < trades.size()) {
                while(rareGiveList.size() < trades.size()) {
                    Pokemon p = this.pokemonService.getRandomPokemon();
                    if(!rareGiveList.contains(p) && !rareRequestList.contains(p) && !banned.contains(p)) {
                        rareGiveList.add(p);
                    }
                }
            }
        }

        // Lets update our trades
        for (IngameTrade trade : trades) {
            // pick new given pokemon
            Pokemon oldgiven = trade.givenPokemon;

            List<Pokemon> givenList = settings.isRareGivensInGameTrades() ? rareGiveList : allPossiblePokemon;
            List<Pokemon> requestList = settings.isRareRequestInGameTrades() ? rareRequestList : allPossiblePokemon;

            // If we are preserving same pokemon trades, lets make sure to use an obtainable pokemon.
            if(settings.isRareGivensInGameTrades() && oldgiven == trade.requestedPokemon) {
                givenList = requestList;
            }

            Pokemon given = givenList.get(random.nextInt(givenList.size()));
            while (usedGivens.contains(given)
                    || (oldgiven != trade.requestedPokemon
                        && !randomizeRequest
                        && trade.requestedPokemon != null
                        && trade.requestedPokemon.number == given.number)) {
                given = givenList.get(random.nextInt(givenList.size()));
            }

            usedGivens.add(given);
            trade.givenPokemon = given;

            // requested pokemon?
            if (oldgiven == trade.requestedPokemon) {
                // preserve trades for the same pokemon
                trade.requestedPokemon = given;
            } else if (randomizeRequest) {
                if (trade.requestedPokemon != null) {
                    Pokemon request = requestList.get(random.nextInt(requestList.size()));
                    while (usedRequests.contains(request) || request.number == given.number) {
                        request = requestList.get(random.nextInt(requestList.size()));
                    }
                    usedRequests.add(request);
                    trade.requestedPokemon = request;
                }
            }

            // nickname?
            if (randomNickname && nickCount > usedNicknames.size()) {
                String nickname = nicknames.get(this.random.nextInt(nickCount));
                while (usedNicknames.contains(nickname)) {
                    nickname = nicknames.get(this.random.nextInt(nickCount));
                }
                usedNicknames.add(nickname);
                trade.nickname = nickname;
            } else if (trade.nickname.equalsIgnoreCase(oldgiven.name)) {
                // change the name for sanity
                trade.nickname = trade.givenPokemon.name;
            }

            if (randomOT && trnameCount > usedOTs.size()) {
                String ot = trainerNames.get(this.random.nextInt(trnameCount));
                while (usedOTs.contains(ot)) {
                    ot = trainerNames.get(this.random.nextInt(trnameCount));
                }
                usedOTs.add(ot);
                trade.otName = ot;
                trade.otId = this.random.nextInt(65536);
            }

            if (randomStats) {
                int maxIV = romHandler.hasDVs() ? 16 : 32;
                for (int i = 0; i < trade.ivs.length; i++) {
                    trade.ivs[i] = this.random.nextInt(maxIV);
                }
            }

            if (randomItem) {
                trade.item = possibleItems.randomItem(this.random);
            }
        }

        // things that the game doesn't support should just be ignored
        romHandler.setIngameTrades(trades);
    }

    private List<Pokemon> getBannedPokemon() {

        List<Pokemon> banned = new ArrayList<>(pokemonService.getBannedFormesForTrainerPokemon());

        if(settings.isNoLegendariesInGameTrades()) {
            banned.addAll(pokemonService.getOnlyLegendaryListInclFormes());
        }
        if(settings.isNoStartersInGameTrades()) {
            List<Pokemon> starters = romHandler.getStarters();
            for(Pokemon starter: starters) {
                Set<Pokemon> relatives = pokemonService.getRelatedPokemon(starter);
                banned.addAll(relatives);
            }
        }

        return banned;
    }
}
