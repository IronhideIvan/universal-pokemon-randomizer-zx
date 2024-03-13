This is the IronhideIvan edit of the Universal Pokemon Randomizer ZX by Ajarmar.

# README

My primary goal is to add additional randomization options in order to randomize the games in a way
that I would enjoy the most. I'm also breaking out the code into smaller chunks and refactoring things
to make them easier to modify and work with (At least for me). I've appended the original readme to the bottom of my
comments.

## Randomizer Additions
These are things which are already complete. I will try my best to explain my reasoning for the changes. My goals specifically cater to my personal preferences,
however, so these changes might not vibe with everyone.
1. __Options to not have starters appear in the wild or trades__. I like the idea of starters being a unique choice for the player.
It gives the player's choice of starter significantly more impact because their will be no other, easily available, opportunity
to catch those pokemon. (I've also added an option to remove all static pokemon in the game (Like Sudowudo in GEN2) as options for
wild encounters. I thought it would be a neat idea but I didn't end up using it.)
2. __Include type-themed trainer areas__. I like the idea of keeping gyms and bosses with themed teams. However, there
were areas, like Sprout Tower in GS/HGSS, that I felt should also logically have themed trainers. So I've added
an option to trainer randomization which includes this.
3. __Give "Uber" trainers all legendary teams__. This is specific to HGSS and GS, since that was the game I was focused on
when making this change. It gives Red a varied team of entirely comprised of legendary pokemon. I thought it would be fun if
the strongest trainer in the game would have a totally OP team.
4. __Option to set a limit as to when trainer pokemon use better movesets__. I didn't think it was fair going up against
a trainer with a Lvl2 pidgey with hyper beam at the start of the game. So I created a new option so that only trainers
with pokemon above a certain level will have better movesets.
5. __Log more stuff__. I want to know more specifics when I randomize a game, or at least have information on things
that isn't inuitively known. For example, I want to know what pokemon are unobtainable in the wild, so that I have an idea
of what I'm physically unable to find if there's a pokemon I really want to have in a playthrough.
6. __"Thematic" wild encounters__. I'm not a fan of total randomness, I just don't find it fun. As such, I created an option
which tries to keep wild encounters thematically similar to what's in the vanilla game. What this boils down to is an Area 1-1 mapping 
where an encounter in the base game will try to be replaced with a pokemon that:
   1. Shares a type with the vanilla's primary type.
   2. Is of a similar power level.
7. __Options for trades to only include rare pokemon__. In other words, trades will try to request pokemon that are difficult, but not impossible,
to obtain in the wild. At the same time, trades will offer pokemon that are rare or unobtainable in the wild. This gives trades the opportunity to be a 
bit more fun and worthwhile to pursue.
8. __Starter types are unique from one another__. No two starters will share the same type at the time of the choice. I think this is
more in the spirit of the OG games and will give the player more variety at the start of the game.
9. __Option to give Pikachu Surf__. This is an, admittedly, silly and niche addition. Surfing pikachu has regularly been an
event-only exclusive prior to Gen 8, and I wanted to bring that into the earlier generations if possible.
10. __Option to force starters into a type triangle.__ When randomizing starters you are given the option to choose
whether you want the starters to be placed in a type triangle. For the sake of flexibility, I've added 3 levels of restrictiveness
in regards to what type combinations are available. The type triangles are:
    1. _Weak:_ Not including itself, each type in the triange is super effective to exactly one other type in the triangle.
It is also weak to the type it is not super effective against.
    2. _Strong:_ Same as "Weak". Additionally, each type resists the type it is super effective against.
    3. _Perfect:_ Same as "Strong" and "Weak". Additionally, each type also resists itself. (In the vanilla games, this 
option really only includes GRASS, FIRE, WATER as a type combination.)
11. __Option to force starters to be each be single type.__ In other words, if this option is turned on, then pokemon like
GHASTLY won't be possible as starters because they have a secondary type (POISON). This was an option I added while testing my 
type triangle changes, and felt it meshed well with the existing starter options so I decided to turn it into a feature.
12. __Alphabetize custom starter selection.__ Pokemon should now be in alphabetical order when selecting custom starters.
They were in numerical order before, and I don't know what number each pokemon is by heart.

## Randomizer Goals
These are things I might want to add in the future or are things that I think would be fun.
1. Currently, I don't have any other feature additions planned. Will update this section if that changes.

## Refactoring Goals
I don't like that most of the randomization logic sits in a monolithic `RomHandler` implementation. My ideal scenario
would be:
1. Create individual `Randomizer` classes to handle logically distinct pieces of randomization. For example, the logic for
randomizing trades should not also include logic for randomizing pokemon abilities. 
2. Create service classes for retrieving and containing widely used information from the Rom. For example,
created a `PokemonService` class to hold the various lists of pokemon that the application needs.
3. Remove any instances of `Random` from any `RomHandler` implementations. Logically, I feel that the RomHandler
should be relegated solely as an interface with the ROM. It should not be doing any randomization on its own, as
it's already a very heavy object that's doing a lot of heavy lifting with the ROM. The randomization logic should
exist in a relevant `Randomizer`.

---
# Original README
_Original text taken from Universal Pokemon Randomizer ZX_

Universal Pokemon Randomizer ZX by Ajarmar

With significant contributions from darkeye, cleartonic

Based on the Universal Pokemon Randomizer by Dabomstew

# Info

This fork was originally made to make some minor tweaks and fixes, but became a bit more ambitious since 2020. There are several new features and supported games (including 3DS games) compared to the original Universal Pokemon Randomizer.

Have a look at the [release page](https://github.com/Ajarmar/universal-pokemon-randomizer-zx/releases) for changelogs and downloads.

# Contributing

If you want to contribute something to the codebase, we'd recommend creating an issue for it first (using the`Contribution Idea` template). This way, we can discuss whether or not it's a good fit for the randomizer before you put in the work to implement it. This is just to save you time in the event that we don't think it's something we want to accept.

See [the Wiki Page](https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/Building-Universal-Pokemon-Randomizer-ZX) for setting up to build/test locally.

### What is a good fit for the randomizer?

In general, we try to make settings as universal as possible. This means that it preferably should work in as many games as possible, and also that it's something that many people will find useful. If the setting is very niche, it will mostly just bloat the GUI.

If your idea is a change to an existing setting rather than a new setting, it needs to be well motivated.

# Feature requests

We do not take feature requests.

# Bug reports

If you encounter something that seems to be a bug, submit an issue using the `Bug Report` issue template.

# Other problems

If you have problems using the randomizer, it could be because of some problem with Java or your operating system. **If you have problems with starting the randomizer specifically, [read this page first before creating an issue.](https://github.com/Ajarmar/universal-pokemon-randomizer-zx/wiki/About-Java)** If that page does not solve your problem, submit an issue using the `Need Help` issue template.
