# A lived-in world: Drifter towns instead of villages (plan, not started)

The user (2026-10-29): "the direction i want this addon to go is to actually replace villages and villagers as a whole;
i actually want to add back the illusion of this world being lived in (an early idea starting this mod); take inspiration
of like MCA Reborn (the mod) and like rpg game civilizations (skyrim, fallout) while still having that feel of a player,
and not completely into the 'roleplay npc'". Planned right after step 6 part 1 (diplomacy, 0.22.39); built after nations
(step 6 parts 2 and 3) unless the user says otherwise.

## The idea in one paragraph

Every settlement you find is a Drifter town: a long-running survival server's town, not an NPC village. The houses are
ones players built, the roads are worn paths, the farms are real farms, there's a market stall, a notice board, a leader
somebody voted for, two neighbours who've never got on. Its people are players: they mine, farm, build and trade for real,
talk like players in chat, log off for good one day, and new ones move in. What you get from MCA Reborn and Skyrim is the
*feeling*: everyone has a name, a house, a job, friends and grudges, a routine, a past with the next town over; you can
become one of them. What you don't get is the NPC: no dialogue trees, no quest markers, no scripted lines that ignore
what's going on.

## The rule that keeps it player-like

Anything an RPG NPC does, a Drifter does the way a player on a server would:

| RPG / MCA | Drifter way |
| --- | --- |
| Villager trade menu with fixed offers | A stall or a shop where they sell what their town actually made, at prices they set (they run out; prices move) |
| Blacksmith, enchanter, priest | Someone who's good at it and has the setup: "i've got an enchanting table, bring lapis and i'll do your sword" |
| Quest giver with a marker | The notice board and chat: "anyone got 20 iron? paying 5 emeralds", "someone killed my dog, 10 diamonds for whoever finds them" |
| Innkeeper, rent a room | "you can crash at mine tonight" from a friend, or the town's spare house |
| Marriage, children (MCA) | Best friends, duos, roommates, a crew; newcomers "join the server" and move in (open question) |
| Guards in uniform | The two boldest with full iron (0.22.38); the town's laws (step 5) |
| Followers | A friend who comes with you ("wanna go mining?"), on its own terms |
| Rumours | News that travels person to person (built), the chronicle |

## What it would take, in phases

**Phase A: towns where villages were (worldgen).**
- Vanilla villages stop generating (a setting); where one would have been (same places, same biomes), a Drifter town is
  made the first time its ground loads, built as the made-ready towns are now: houses from the house plans in the
  biome's style (plains, desert, savanna, taiga, snowy), roads, plaza, store, stall, fields, pen, lamps.
- Sizes: a homestead (one or two households), a hamlet (3 to 5), a village (6 to 12), a town (13 to 25, walls later).
- A past already: weeks of history in the chronicle, bonds (0.22.37), worn paths, a few graves, a stocked store, some
  houses half finished or abandoned (someone quit), signs with names.
- What villages did, done another way: iron golems become the town's guards; raids hit Drifter towns (they fight back);
  zombie sieges; the wandering trader becomes a travelling Drifter merchant (the encounter exists); "breeding" becomes
  newcomers moving in. Villagers and zombie villagers don't spawn (a setting).
- Already there to build on: Drifters moving into villages, bandit outposts, camps, strip mines, cave torches
  (`LivedInStructures`, `DrifterFeatures`).

**Phase B: trades and trades (replacing villager trading).**
- Each town's people take up a line by how they play: farmer, miner, smith, builder, fletcher, fisher, shepherd,
  librarian/enchanter, brewer, cartographer (sells maps of places they know). They sell what they actually have, from
  the stall or their own door, and offer services with what they have set up (enchanting, repairs at an anvil, brewing).
- Prices from supply and demand (CIVILIZATION.md part 2), so iron is cheap in a mining town.
- What a librarian's enchanted books and a cleric's ender pearls gave players: still to be had, from someone who made
  or found them.

**Phase C: town life.**
- Routines: up at dawn, work, the plaza at midday, home or the campfire at dusk, in before dark; market days.
- Hangouts: the plaza, a campfire, someone's porch; people chatting there (the talk system), not standing still.
- Coming and going: newcomers move in (wanderers settle), people leave or quit for good, houses change hands, the town
  grows or shrinks; abandoned houses get looted (by Bandits, or you).
- Festivals and events (culture, step 7).

**Phase D: you and the town (MCA's heart, as a player would have it).**
- Reputation per town (what its people think of you, spread by word of mouth: built per person; a town's view on top).
- Friends: trusted enough, they invite you in, give you food, warn you, back you up, come along on a trip.
- Living there: ask for a plot and build your house, or take an empty one; a citizen (laws, tax, a vote) but not leader
  (the user, 2026-10-21).
- The notice board: real needs posted by real people (a request system exists), bounties, lost things.
- Being hated works too: wanted, exiled, raided (built: step 5).

**Then nations (step 6 parts 2 and 3) across a world full of towns.**

## Decided (the user, 2026-10-29)

1. Villages: **replaced completely**: no vanilla villages or villagers; a Drifter town wherever a village would have
   generated, in the biome's style, from a homestead to a town.
2. Trading: **real shops only**: what their town actually made, at their prices, services if they have the setup; they
   can run out. No fixed-offer menu.
3. Closeness: **friends and companions**: friendships with real perks (invited in, gifts, warnings, backup, coming along
   on trips); no romance or marriage.
4. Order: **after nations** (step 6 parts 2 and 3), then phases A to D here.
