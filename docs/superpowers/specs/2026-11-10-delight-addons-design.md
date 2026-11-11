# Farmer's Delight addons: cook the addons' food their way, get the ingredients like a player, store with crates and drawers (design)

Compatibility project 2 of five (order: 1 weapons, armor, trinkets (built); 2 Farmer's Delight addons; 3 block and
furniture mods; 4 structure mods; 5 player and server mods). Brainstormed with the user on 2026-11-10.

## Intent

Farmer's Delight itself already works (side session 0.23.22-side.2, PROGRESS.md "Farmer's Delight kitchens and cooking"):
a Drifter fits out a kitchen (stove, pot, board, skillet, cabinets), cuts on the board, cooks in the pot, fries in the
skillet, and picks the most filling meal it has the makings of. The addons in the user's DRIFTER TEST profile add food,
ingredients, stations and storage that a Drifter doesn't know at all.

The user (2026-11-10): what makes them think "the Drifters really get this" is **"cooking with the food the addons added
in its intended way, understanding where the ingredients are from, how to get it, and integrating it into their
cooking"**. And: **"keep in mind some addons have a different purpose as well (crates)"**: crates and drawers as **"extra
storage (for food or items) and decoration"**. What would bother them most: **"storage in the wrong places"**. For far
or dangerous ingredients: **"go get it like a player would, weigh the risk"**.

## The addons (all in DRIFTER TEST; mod ids as registered)

| Mod | Id | What's new for a Drifter |
| --- | --- | --- |
| More Delight 26.04.30 | `moredelight` | ~38 foods: sandwiches and burgers (crafting table), toast and omelette (campfire, also smoker/furnace), rice and potato dishes (cooking pot), diced potatoes and bread slices (cutting board). Wooden and stone knives (a cheaper first knife than flint). No blocks. |
| Ocean's Delight 1.0.3 | `oceansdelight` | Sea food: tentacles, guardian tail and elder guardian slabs that drop **only when the mob is killed with a knife** (`data/farmersdelight/loot_modifiers`); pot dishes (guardian soup, squid rings, honey fried kelp, ...), campfire dishes, stuffed cod/squid, fugu. Guardian soup is a placed block. |
| Thai's Delight 2.0.0 | `muffins_thaidelight` | The biggest: a **mortar** station (own recipe type `muffins_thaidelight:mortar`, 38 recipes: pesto, somtam, steamed bamboo shoot, dyes, sugar); **fruit trees** (coconut, mango, durian, papaya, lime; their own wood sets) and wild basil, pepper and butterfly pea patches (biome-tagged worldgen); **flower crabs** (beaches) and **dragonflies** (caught in a bottle); **cauldrons** (coconut milk, fermented fish → fish sauce); **feasts** (placed dishes served into bowls); 20 pot and 13 board recipes; its ingredients in village, mineshaft and outpost chests (loot modifiers). Needs `muffins_ovenapi`. |
| Crate Delight 26.07.20 | `cratedelight` | 49 crates and bags: 9 of one item crafted into one block (carrot crate, bag of sugar, egg crate, ...), crafted back open. Pure storage and decoration. No Farmer's Delight needed. |
| Storage Delight 26.07.01 | `storagedelight` | ~169 storage furniture blocks per wood: drawers (plain, small, with door, with books), cabinets (glass doors, single door, with stone countertops), bookshelves with doors. Containers. No Farmer's Delight needed. |

Farmer's Delight's own feasts (roast chicken, stuffed pumpkin, honey glazed ham, shepherd's pie) aren't handled today
either; feasts here means all of them.

## Goals

1. **Cook each addon dish the way its addon means.** Sandwiches at the crafting table, toast and omelette on a campfire
   (or smoker/furnace where the addon allows it), stews and rice in the cooking pot, pesto and somtam in the mortar,
   coconut milk and fish sauce in the cauldrons, feasts placed as a block and served into bowls (one bowl per serving,
   as a player right-clicks it). Through the fake player and the station's own block logic, like the pot and board today.
2. **Stations it needs, it makes or places**, the way it does Farmer's Delight's kitchen: a mortar, a campfire, a
   cauldron. Each gets a place in the house's kitchen layout (goal 9's rules).
3. **Know where each ingredient comes from, and get it like a player.** Fruit from the right trees, basil and pepper
   from wild patches, crabs on beaches, dragonflies in a bottle, squid and guardians killed with a knife in hand,
   chest loot where it already loots chests. Only what it has seen or been told of (fairness, knowledge through
   perception): it has to have seen a mango tree or a guardian before planning to get one.
4. **Weigh the risk.** A far or dangerous ingredient (guardians, a jungle's durian) is gone for like a player would,
   judged by its gear, health, the danger and how far, through the planner's and goals' existing cost and risk
   judgement (and the respawn stakes: warier when death is permanent). A well-geared Drifter may take on guardians; a
   new one in leather picks a safer dish. **Sea hunting stays within reach** (the user, 2026-11-10): squid and guardians
   are killed from the shore, in shallow water or from a boat; a raid into an ocean monument (swimming combat, breath,
   mining fatigue) is judged too risky in this project and is a later project of its own.
5. **Fold it into everyday cooking.** Addon dishes join the same meal choice as every other meal (what it has the
   makings of, how filling, how soon), so modded food shows up in ordinary meals, not as a special event.
6. **Grow what it brings home** (the user agreed): it plants the saplings and seeds it brings home (mango trees, pepper,
   basil, ...) near home and harvests them, so it doesn't travel every time.
7. **Learns an addon dish once it has held one of its ingredients** (the user agreed), like a player's recipe book
   unlocking. **Only the addons' dishes** (the user, 2026-11-10): Farmer's Delight's own meals stay known to every
   Drifter as today, so nothing is taken away.
8. **Crates and bags as storage and decoration.** With lots of one food it packs nine to a crate at the crafting table;
   it crafts one back open only when it needs what's inside; a crate it owns counts as nine of the item when it counts
   what it has (planner, meal choice). Crates are set out as blocks: stacked in the pantry, kitchen or storeroom, a bag
   of flour by the stove; they are its real stock (no free items).
9. **Storage Delight's furniture as storage and decoration.** Drawers, cabinets and bookshelves with doors are storage
   it puts things in and takes things from like chests, and furniture in the house layout: drawers in the bedroom,
   countertop cabinets along the kitchen wall, a bookshelf with doors in the main room; the wood matching the house.
10. **Storage in the right places** (the user's main worry). Storage goes against walls, in rooms where it fits, keeps
    clear of doors, paths and the front of beds, stoves and chests (as furniture does today, `Furnishing.keepClear`),
    never outside, on roofs, or piled up like a dump. What's inside matches the place: food in the kitchen and pantry
    crates, books and odds and ends in the main-room shelves, clothes and valuables in bedroom drawers.
11. **Civilizations towns** get the same when their houses are set up (as they get Farmer's Delight's kitchen through
    `DrifterApi.settleIn`): drawers and crates in the right rooms, addon ingredients and dishes in pantries, cabinets and
    markets.
12. **Talk** (the user agreed): Drifters chat about the new dishes and ingredients (a talk file with `requires_mod`,
    like `topics_kitchen.json`).

## Generality (the user, 2026-11-10: yes to the recommendation)

- **General where the game allows it:** any recipe of the cooking pot, cutting board, campfire, smoker, furnace or
  crafting table works without per-mod code, so most of a future food addon works on its own; any "nine of an item into
  one block, and back" recipe pair from any mod is a crate.
- **Hand-written only for the special parts of these five:** the mortar, the cauldrons, knife kills, fruit trees and
  wild patches (sources data), feasts, Storage Delight's furniture. A later addon with its own new station gets
  hand-written support when the user adds it.

## Non-goals

- Other block and furniture mods (Chipped etc.): project 3, which will reuse this project's storage placement.
- Ocean monument raids (underwater combat, breath, mining fatigue): a later project of its own.
- Addons the user hasn't installed (Farmer's Respite's kettle, Brewin' and Chewin's keg, ...).
- New kinds of farming beyond goal 6 (no animal breeding for an ingredient, no fishing rewrite; flower crabs are
  caught, not bred).
- Whims: other sessions own the whim files. Stay out of them.

## Anti-goals (a failure even if it works)

- Storage in the wrong places (goal 10): a drawer in the middle of a room, crates blocking a door, fish crates in a
  bedroom, crates outside or on the roof, food shut in a bookshelf.
- A dish made in a way a player couldn't (no station, an ingredient it never gathered, a knife-only drop without a
  knife).
- A Drifter with addon ingredients that never cooks the addon dish, or that only ever makes one dish.
- Suicidal trips: a fresh Drifter swimming at an ocean monument for guardian soup.
- Crafting crates open and shut in a loop, or packing the food it's about to cook.
- Any addon becoming required: all optional, detected at runtime; without them nothing changes.
- A performance drop at the user's real scale (about 200 loaded Drifters; `real-play-scale`).

## Constraints

- DESIGN.md section 1 fairness: knowledge only through perception, player time for every action, player reach, no
  impossible placement, no free items.
- Soft dependencies: compile-only from Modrinth's maven where the mod's classes are needed (as Farmer's Delight is),
  registry ids only where they aren't; safe without each mod (`requires_mod` on data files).
- The addon (Drifter: Civilizations) reaches Drifter only through `com.dinglebob.drifter.api`, grown in the same change
  and documented in docs/API.md. Keep addon edits small (other sessions edit the addon).
- Tunable numbers (how many to pack before crating, risk thresholds, chances) as named constants or data.
- Saved data versioned (anything new it remembers: known dishes, planted trees, crates' places).
- Test with and without the mods (dev launches load mods only when asked, like `DRIFTER_FD=1`).

## Calls made in writing this up (check them)

- **Recipe learning (goal 7)** gates only the addons' dishes, not vanilla crafting or Farmer's Delight's meals; a
  Drifter with high Knowledge (the `GearLore.VETERAN_KNOWLEDGE` idea) knows them all from the start; a town's Drifters
  know the dishes of the ingredients their town's pantries hold; learned dishes are saved with the Drifter (versioned).
- **Feasts:** a placed feast is the household's: the cook serves bowls from it for itself and its household, and it is
  picked up (as the block's own logic allows) when empty.
- **Crates:** packing only for stock above a named threshold (default: more than two stacks of one item) and only at
  home; opening only for a plan that needs the contents. Bags of seeds count as seeds for the farm.
- **Which room is which** comes from the existing house layout (main room, entry, bedrooms, kitchen wall); where a house
  has no separate pantry, crates stand by the kitchen and storage wall.
- **Knife kills:** a hunt whose drop needs a knife holds a knife (any Farmer's Delight or More Delight knife) for the
  kill; the planner knows the knife is part of getting that drop.
- **Fruit trees** count as their wood too (coconut logs, mango planks) for building.
- **What counts as a crate (goal 8):** only food and seed storage: Crate Delight's crates and bags, and any other mod's
  nine-into-one pair whose item is a food, crop or seed. Vanilla's compressed blocks (iron, gold, coal, redstone,
  copper, bone, slime, dried kelp blocks, hay bales) are not crates: no packing iron into blocks, no iron blocks in the
  pantry.
- **Storage Delight furniture is extra storage, in new places** (the user: "extra storage"): it doesn't replace the
  storage wall's chests, the dump, bedside or fuel chests, or Farmer's Delight's cabinets. New places along free wall in
  fitting rooms; where a house has no room left, storage gives way to the keep-clear rules (no storage beats storage in
  the wrong place). Code that looks for chests to store in or fetch from accepts these blocks as containers.
- **Contents match the room (goal 10)** means where things go when it puts them away: food to kitchen and pantry
  storage, books and odds and ends to main-room shelves, clothes and valuables to bedroom drawers, overflow to the
  storage wall. No big reshuffle of what's already stored; the existing tidy-up moves misplaced things over time as it
  passes.
- **Thai's Delight's sack** is part of getting coconuts (a sack under a coconut tree catches the falling coconut, the
  mod's way): in goal 3, researched from the mod's code. Its buffalo isn't in this project.

## Left to the builder

- How each special station works for a player (read the mod's own code: mortar clicks, cauldron filling, feast
  serving) and mirroring it.
- How generic recipe discovery is structured (recipe types, the crate pairs), and its caching per reload.
- Where risk is judged (planner cost, goal feasibility, Threats) and its numbers, within goal 4.
- Exact layout places for each storage block and how room purpose maps to contents.
- Smoke scripts: with all five mods and with none; a meal from each food addon; a crate packed, placed and opened; a
  house with drawers and crates; a knife hunt; a risky trip turned down by a weak Drifter.
- Splitting this into tasks (likely: generic recipes and meal choice; sources and knife kills; mortar; cauldrons;
  feasts; crates; Storage Delight furniture and placement; planting; towns; talk).
