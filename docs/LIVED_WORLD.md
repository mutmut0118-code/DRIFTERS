# A lived-in world: Drifter towns instead of villages

The plan and the progress for the addon's next big direction, kept like docs/PROGRESS.md: what's decided, what's done,
what's next, and every step written down before it's built.

The user (2026-10-29): "the direction i want this addon to go is to actually replace villages and villagers as a whole;
i actually want to add back the illusion of this world being lived in (an early idea starting this mod); take inspiration
of like MCA Reborn (the mod) and like rpg game civilizations (skyrim, fallout) while still having that feel of a player,
and not completely into the 'roleplay npc'". Then: "lets expand on this plan and make it a whole seperate very detailed
document, kind of like progress.md; i feel like this is too big of a step to not be on paper".

Planned 2026-10-29, right after milestone 22 step 6 part 1 (diplomacy, 0.22.39). **Milestone 23** (its own milestone,
after 22 ends with nations: step 6 parts 2 and 3), built phase by phase, one step at a time, each with a version (0.23.x),
a test and an entry here.

---

## Status

| Phase | What | Status |
| --- | --- | --- |
| A | Towns where villages were (world generation) | In progress: A1, A2, A11 (0.23.0, 0.23.1), A3, A4 sizes (0.23.2), ruins (0.23.3), A5, A6, A9 (0.23.4), cities (0.23.5), raids (0.23.6); next A10 |
| B | Trades and services instead of villager trading | In progress: B1 lines of work (0.23.7), B2 and B3 trades and shops (0.23.9) |
| C | Town life and culture (days, hangouts, comings and goings, events, festivals, monuments, beliefs) | Not started |
| D | You and the town (reputation, friends, companions, living there, the notice board) | Not started |

Already built that this stands on: see "What's there to build on" below.

---

## Decided (the user, 2026-10-29)

1. **Villages: replaced completely.** No vanilla villages and no villagers. Wherever a village would have generated, a
   Drifter town is made instead, in the biome's style, from a lone homestead to a town.
2. **Trading: real shops only.** Drifters sell what their town actually made, at prices they set, and offer services
   (enchanting, repairs, brewing) when they have the setup. They can run out. No fixed villager-style trade menu.
3. **Closeness: friends and companions.** Friendship with real perks: invited in, food and gifts, warnings, backup in a
   fight, coming along on trips. No romance or marriage.
4. **Order: after nations.** Wars (step 6 part 2) and nations (part 3) first; then phases A to D here.
5. **Villagers for players' farms: a setting.** Villages are replaced, but a setting lets villagers exist (curing zombie
   villagers, spawn eggs) for players who want trading halls and iron farms. Off by default.
6. **Milestone 23** (the user: "whatever you think best"): its own milestone after 22, so 22 ends cleanly with nations and
   this gets its own versions (0.23.x).
7. **Culture folded in** (the user: "should fold them into this plan that way it integrates better"): festivals,
   monuments, beliefs and traditions (CIVILIZATION.md part 5) are phase C here. Flags and banners for nations stay with
   nations (step 6 part 3).
8. **Cities, and how common** (the user: "about 2-3x more rarer than villages, and at most a town should have 100
   drifters and usually at least 40-80 households, so it feels like a CITY, ofc there could be small town variations
   that connect with bigger towns, like 8-16 household towns"; the rarity to tweak as recommended): see A4. Settlements
   about 2.5 times rarer than villages; most of them small towns of 8 to 16 households, a city of 40 to 80 households
   (100 Drifters at most) now and then, with small towns round it on roads to it.
9. **The player in a town: a citizen only for now** (the user: "keep the town roles for later and just be a citizen").
   Roles (guard, keeper, builder for hire) later.
10. **Steep, hilly worlds: towns must still be easy to get around** (the user, 2026-10-29: "the world generation has to
    be compatible and dynamic, i intend running this on a modpack with a configured version of tectonic that has high
    mountains and hilly terrain, so it has to be easy to get around without the town being covered with cobblestone
    pillaring and bridging"). See "Terrain" in section 3 and A11.
11. **Towns go on the flattest land to hand** (the user, 2026-10-29: "you should set it up where its preferred to be on
    flattest land given the choice, that way it isnt placed anywhere"): a town's spot is the flattest open ground within
    reach of where it would generate; nothing flat enough near, no town there (better none than one on a cliff). See A11.

Still open: see "Questions for the user" at the end.

---

## 1. The idea

Every settlement you find is a Drifter town: a long-running survival server's town, not an NPC village. The houses are
ones players built, the roads are paths worn by walking, the farms are real farms, there's a market stall, a notice
board, a leader somebody voted for, two neighbours who've never got on, a grave by the church of someone who isn't
coming back. Its people are players: they mine, farm, build and trade for real, talk like players in chat, log off for
good one day, and new ones move in.

From MCA Reborn and Skyrim/Fallout we take the **feeling**: everyone has a name, a house, a job, friends and grudges, a
routine, a past with the town over the hill; there are shops, guards, rumours, things that need doing; you can become
one of them, or their enemy. We don't take the **NPC**: no dialogue trees, no quest markers, no lines that ignore what's
going on, nobody standing behind a counter forever, nothing they have that they didn't get.

### The rule that keeps it player-like

Anything an RPG NPC does, a Drifter does the way a player on a server would.

| RPG / MCA | Drifter way |
| --- | --- |
| A villager's trade menu with fixed offers | A stall or a shop at their door, selling what their town actually made, at their prices; they run out, prices move |
| The blacksmith, the enchanter, the priest | Someone good at it with the setup: "i've got an enchanting table, bring lapis and i'll do your sword" |
| A quest giver with a marker | The notice board and chat: "anyone got 20 iron? paying 5 emeralds"; "10 diamonds to whoever finds my dog" |
| The innkeeper | "you can crash at mine tonight" from a friend; the town's empty house |
| Marriage and children (MCA) | Not this (decided). Best friends, a crew, a companion; newcomers "join the server" and move in |
| Guards in uniform | The two boldest in full iron (0.22.38); the town's laws (step 5) |
| Followers | A friend who comes with you ("wanna go mining?"), on its own terms, and goes home when it's had enough |
| Rumours | News passed person to person (built), the chronicle, the notice board |
| Scripted barks | Chat lines in player speak, picked by what's actually happening (built: thousands of lines and topics) |

### Not this (anti-goals)

- No floating markers, no menus that aren't Minecraft's own (the trading screen is the one exception, and it shows only
  real stock).
- Nobody stands at a counter all day: a shopkeeper keeps its stall in open hours and lives its life the rest.
- No item from nothing (DESIGN.md section 1): what a shop sells was mined, farmed, crafted, looted or traded.
- No knowledge from nothing: a Drifter knows of a town, a danger or a price because it saw it or was told.
- Not a theme park: towns aren't the same everywhere; a lot of them are small, some are failing, some are gone.

---

## 2. What's there to build on

Built already (milestones 1 to 22), and how each part of this plan uses it:

- **Drifters that play survival** (the whole mod): the people. Their jobs in phase B are what they already do (mine,
  farm, fish, hunt, smelt, craft, enchant, brew), done for the town.
- **World generation that's lived in** (`world/gen`): camps with huts (`CampFeature`), strip mines and cave torches
  left by people (`StripMineFeature`, `CaveTorchFeature`), wanderers (`WandererFeature`), felled trees (`Felling`),
  vanilla villages some Drifters moved into and outposts Bandits hold (`LivedInStructures`), mineshaft delvers
  (`Delvers`). Phase A replaces the village part and keeps the rest.
- **Towns made ready** (addon, `TownBuilder`): plaza, well, bell, notice board, roads off it and side roads, the store,
  the market stall, fields, a pen, lamp posts, houses from the house plans with their fronts to the road, furnished,
  stocked, lit; their people settled in, geared by their place (0.22.38), with a past (0.22.37). Phase A turns this into
  world generation.
- **Towns founded by their people** (step 3b, `TownFounding`): Drifters who get on found a town and build it block by
  block. Stays as it is; phase C's newcomers and growth run on it.
- **House plans by the hundred** (milestone 15): 12 styles, ~300 generated plans, the user's guidebook, furnishing
  (`Furnishing`), beds where they can be walked to (0.22.34). Phase A needs biome styles.
- **Money and markets** (step 4): the till, wages, the stall and its keeper (`Market`, `StallGoal`, `ShopKeeper`), the
  trading screen, trade runs between towns (`TradeRuns`, `TradeRunGoal`), Numismatic Overhaul coins when installed.
  Phase B grows this into shops for every trade.
- **Leaders, laws, crime, unrest** (step 5): elections, tax, guards collecting fines, exile, bounties, the town's crook,
  revolts and coups. Phase D's citizenship plugs into it.
- **Diplomacy** (step 6 part 1): towns knowing of each other, standing, envoys, pacts. Nations (parts 2 and 3) next.
- **The chronicle** (`Chronicle`): the world's history as it happens; the notice board and the town's book.
- **News that travels** (`NewsBook`), **bonds and trust** (`DrifterBonds`, `Relationships`), **talk** (topics,
  thousands of lines, player chat in both directions, requests, sparring, `RequestGoal`), **moods and stakes**
  (milestone 7, stakes parts 1 and 2), **grief and grudges**.
- **Encounters** (milestone 21): merchant, escort, convoy, bounty hunters, wanted posters, lost dog, injured traveller,
  village raid, mourner, builders, and more. Phase A's wandering trader and phase D's notice board borrow from them.
- **Offscreen catch-up** (milestone 10, `CatchUp`) and the **Drifter directory** (`DrifterRegistry`): the base for towns
  that go on while nobody's near.

---

## 3. Hard constraints (every step)

- **Fairness** (DESIGN.md section 1): perception only, player time, player reach, player vulnerability, no impossible
  placement, no free items. A town *made* by world generation is the one place things appear at once (as made-ready
  towns do now); everything after is earned.
- **Architecture** (DESIGN.md section 2): Drifters are mobs with a fake player only at the moment of acting; the addon
  goes through `com.dinglebob.drifter.api` (grow the API in the same change when it needs more).
- **The two layers** (CIVILIZATION.md): towns near players are played out in full; towns far away are summaries that move
  forward cheaply. A world will have dozens of towns; only the near ones may cost much.
- **Performance budget**: making a town today takes 8 to 17 seconds in one go (it's for testing). World generation
  can't stall the game like that: towns must be made a slice at a time (see A2).
- **Settings** for anything a player might not want (Mod Menu, `DrifterConfigScreen`): villages replaced (on), villagers
  spawning (off), town sizes, how common.
- **Saves**: versioned (DESIGN.md section 20); a world made before keeps working (old villages stay where they are).
- **Compatibility**: other mods that put things in villages (the user's pack: Bountiful's bounty boards, Guard Villagers,
  Nova Structures) and mods that rely on villagers. See A9.
- **Player speak** in every line (the user, 2026-10-28: "dont make it corny disney expositiony").
- **Terrain** (decisions 10 and 11): the user plays with Tectonic (3.0.28, configured: vertical scale 1.52, elevation
  boost 2.45, build height up to 1360, rolling hills on; with Lithostitched and Terrain Slabs), so mountains are high and
  the lowlands hilly. Every step has to work there, not only on vanilla terrain: towns on the flattest ground to hand,
  laid out to the land, and walkable, so their people (and players) get round them on foot without pillaring, bridging or
  digging. Tested on that terrain (the user's jar and config, in the "meridian test 3" profile), not only on vanilla.

---

## 4. Phase A: towns where villages were

Goal: you explore a new world and every village is a Drifter town, each one different, already old.

### A1. Villages replaced in world generation

- **What:** the village structure set gives way to the addon's town structure: same spacing, same biomes, same seed
  logic, so towns are where you'd expect villages, and `/locate` finds them.
- **How:** a data-driven override of `minecraft:worldgen/structure_set/villages` pointing at a `drifter_civ:town`
  structure (a custom structure type). Its start records the town's spot, biome and size in a saved list ("towns to
  make") and places nothing heavy at chunk generation; the town is built later (A2). With the setting off, villages
  generate as before.
- **Villagers:** don't spawn (natural spawns, spawn eggs aside; a setting); zombie villagers become zombies; the wandering
  trader's spawner is off (A7).
- **Existing worlds:** villages already generated stay vanilla (or, a setting, get Drifters moving in as now).
- **Test:** `/locate structure drifter_civ:town` from spawn finds one; a new world has no village anywhere; flying
  over a few thousand blocks finds towns where villages would have been.

### A2. Building a town a slice at a time

- **What:** a town is made when the player first comes near its spot (its chunks loaded), in slices of a few
  milliseconds a tick, over a few seconds, before they get close enough to see it; out of sight, never a stall.
- **How:** split `TownBuilder` into stages that resume (clear and level the plaza; lay roads; each house: site, level,
  place, furnish, stock; store; stall; fields; pen; lamps; people), each stage cut into block batches with a per-tick
  budget (say 3 ms). Started when the player is within ~12 chunks; anything not finished when they arrive finishes
  faster (a bigger budget) rather than showing half a town.
- **Risk:** the biggest technical one in the plan, and bigger with cities (decision 8): a city of 60 houses is five
  times today's 12-house town (about a minute of building in one go). So: **small towns** made a slice at a time as
  above; **cities** with their static part (levelled ground, roads, houses) generated as real structure pieces at chunk
  generation, the way vanilla villages are, and only the people, the stocking and the past (A5) at first load. Both
  measured before going on.
- **Test:** a flight at full speed over a world full of towns: no tick over 50 ms from it (`/drifter` timings), every
  town whole by the time it's in view.

### A3. Towns in the biome's style

- **What:** a plains town of timber and oak, a desert town of sandstone, a savanna town of acacia, a taiga town of
  spruce logs, a snowy town of spruce and stone, (later) jungle, swamp, cherry, badlands.
- **How:** house styles by biome (`drifter_house_styles`: each style says which biomes it suits and its palette); new
  styles where none fit (sandstone for deserts first); roads of the right ground (path, sand-coloured path in deserts:
  smooth sandstone or packed mud); lamps and fences in the town's wood. Every style still Drifter-buildable (the user's
  house rules: memory "House style rules").
- **Test:** one town in each biome side by side (a debug command to make one with a biome's style).

### A11. Steep land: where a town goes, and getting round it (decisions 10 and 11)

Vanilla villages sit wherever their jigsaw pieces fit; on Tectonic's terrain that's often a hillside. Drifter towns
don't, and once there, nobody should have to pillar or bridge to get about.

- **The flattest ground to hand.** When a town's spot comes up (A1), the land round it is looked over first, from the
  world generator's own heights (no chunks made for it): a coarse grid of heights over the reach a town may move (about
  seven chunks each way, so the town still belongs to its spot), each candidate scored by how much its ground rises and
  falls over the town's footprint (the plaza and its first ring of houses most), how much is water, and how far it moved.
  The best wins. **Nothing flat enough** (the rise over the core above a limit, a setting): **no town** in that spot.
  Cities (A4) need more flat ground still, and are rarer for it.
- **Laid out to the land.** The plaza where it's flattest; roads that follow the contours (bending round a hill rather
  than straight up it), at most a block up every few blocks, with steps (stairs or slabs, Terrain Slabs' if installed)
  where they climb; houses on terraces at their own height (built: foundations, easing), stepped down a slope rather than
  on stilts; nothing over a drop a player couldn't walk.
- **Water and gaps** are crossed only by real bridges the town is made with (planks, fences, a few wide), never by a
  Drifter putting blocks down; a river through a town gets a bridge, not a pillar line.
- **Walkable, checked.** Once a town is made, a walk is worked out from the plaza to every door, the store, the stall,
  the fields and the well, by the same pathfinding Drifters use (no blocks placed or broken). A place it can't be walked
  to gets steps put in, or the house is dropped. A town that fails that isn't finished.
- **Its people walk it.** In their own town Drifters keep to its roads and paths and don't pillar, bridge or dig (built in
  part: 0.22.36 to 0.22.41, mining out past the edge, beds that can be walked to; this makes it the rule for anything
  built by worldgen).
- **Tested on the user's terrain**: Tectonic's jar and config copied into the test setup; flights over a world of towns
  in hills and by mountains, then a day in a hill town counting blocks Drifters put down or dug (the cobblestone count, as
  in 0.22.36), aiming for none.

### A4. Sizes, spacing and shapes (decision 8)

- **How common:** vanilla villages are spaced 34 chunks apart (8 at least), about one per 34 x 34 chunks where a biome
  allows one. Settlements here are about **2.5 times rarer**: spaced **54 chunks** apart (16 at least), about one per
  860 x 860 blocks, before biomes rule some out. Cities are rarer still (below). Both settings.
- **What a spot gets** (weights, settings):
  - **Small town** (60%): **8 to 16 households** (10 to 25 Drifters): a plaza with well and notice board, a store, a
    stall, fields, a pen, houses on two or three roads.
  - **Hamlet or homestead** (20%): 1 to 5 households on one road, a well and a field: the edges of the world.
  - **Ruin** (5%): a town that failed (burnt houses, an empty store, graves, a sign): history.
  - **City** (15% of spots, but never within about 2,000 blocks of another city): **40 to 80 households, 100 Drifters
    at most** ("so it feels like a CITY"): a big plaza, several districts off main roads (its houses, a market street of
    shops, the farms outside), two or three stalls, a store per district, a hall, walls later; its leader leads the
    region (nations, step 6 part 3).
- **Cities and their towns:** each city has a few small towns and hamlets round it (the spots within ~1,500 blocks) that
  "connect" to it: roads worn between them (A8), trade runs to its market, envoys, the city as their capital when nations
  come (step 6 part 3). The world reads as regions: a city, its towns, the wild between.
- **People per household:** most one, some two (rooms: built), so 40 to 80 households is about 50 to 100 Drifters; the
  cap of 100 holds however many rooms there are.
- **Layout** follows the land (a terrain survey first: rivers, slopes, the easing from 0.22.37); a city's districts each
  laid out like a town of its own around the main roads.
- **A city near you is a lot of Drifters** (milestone 16: about 100 full Drifters cost about 10 ms a tick): its people
  near the player in full, the rest of the city at lighter detail (the detail tiers: built), and the city as a summary
  when nobody's near (A10). Measured with a city before cities are switched on.

### A5. A town with a past

- **What:** a town made at world generation looks lived in for weeks: worn paths, crops at all stages, an extension
  half built with scaffolding, a fallen-down shed, graves, signs with names, a bed someone doesn't sleep in any more, a
  pet or two, the chronicle's "before you came" entries.
- **How:**
  - Its history written first, then the town made to match: a founding some days back, the houses in the order they
    went up, an election or two, a death (a grave, its friends' grief cooled to a grudge or a memory), someone who quit
    (an empty house, its chest half emptied), a newcomer (the newest, smallest house), a bad winter (a ruined field).
    The chronicle gets those entries with their days (negative day numbers, or "years ago").
  - People: bonds from that history (0.22.37's, grown from these events), the grudge with whoever did the killing.
  - Wear: paths along the routes walked most (the roads, house to well, house to field), grass worn to dirt by doors,
    flowers in gardens, a chest or two by the fields, a lit path to the mine.
- **Test:** walk into a town on day one: the chronicle reads like a history; at least two things in it you can find in
  the town (the grave, the half-built house).

### A6. People who live there

- **What:** each household's people (bonds, kits, roles: built), plus: names on signs by their doors, the beds and
  chests that are theirs, a tamed dog or cat, a horse in a stable, their own touches (a banner, flowers, a lantern).
- **How:** as made-ready towns now, plus pets tamed to them (they feed and keep them), horses (Drifters ride: milestone
  18), signs, banners (built for groups).

### A7. What villages did, done another way

- **Iron golems:** gone with villagers; the town's guards (0.22.38) do their job, and a town might build a golem if a
  Drifter knows how (later; it takes real iron and a pumpkin).
- **Raids:** vanilla raids need villagers; the addon's raid: a pillager band that comes for a Drifter town (someone with
  Bad Omen walks in, or now and then on their own); the townspeople fight, hide or flee by their natures; a player who
  helps is a hero to the town (reputation, D1). The village-raid encounter (milestone 21) is the start.
- **Zombie sieges:** zombies at night going for the town; lit towns rarely (0.22.36's lighting), dark ones do.
- **The wandering trader:** a travelling Drifter merchant who goes town to town (the merchant encounter and trade runs
  combined): goods from far away, news from far away.
- **Breeding:** newcomers moving in (C4).
- **Cats and the bell:** cats in towns are someone's pets; the bell rings when danger comes (the guards ring it).

### A8. The rest of Drifter's world generation alongside

- Camps, strip mines, cave torches, wanderers, delvers, Bandit outposts: kept (they're the world beyond the towns).
- `LivedInStructures`' villages: with towns replacing villages there are none to move into; kept for old worlds.
- Wanderers and lone homesteaders may move into a town (C4); Bandit outposts raid towns (part 2 of nations, and A7).
- Roads between towns: worn paths along the way trade runs and envoys walk; between a city and its towns (A4) made with
  the city at world generation, as a road a player would have worn, a block wide at the ends, three by the city.

### A9. Mods that rely on villages

- **Bountiful** (the user's pack: bounty boards in villages): a board in each town's plaza instead (the notice board can
  be one, or a Bountiful board placed by the town), so its bounties still work.
- **Guard Villagers:** no villagers to guard; its guards don't spawn in towns.
- **Nova Structures and village-adjacent structures:** checked one by one; anything that needs a village gets a town.
- **Players' villager farms** (trading halls, iron farms, breeders): impossible without villagers. The trade-off of
  "replaced completely" (decided); worth saying in the settings screen. A setting brings villagers back for players who
  want their farms (see the questions at the end).
- **MCA Reborn itself:** if installed alongside, it would add its villagers back; the addon says so in the log, not
  fight it.

### A10. Towns far away (the cheap layer)

- **What:** a town nobody's near is a summary: its people, stock, houses, leader, laws, standing with others; its days go
  by in summary (harvests, trade, building, elections, someone leaving, a raid) and it's as the summary says when you
  come back.
- **How:** CatchUp-style steps per town per in-game day from the same numbers the full town uses; the Drifter directory
  for its people. Needed once a world has dozens of towns, and from the first city (a city's 100 people can't all be
  played out).

---

## 5. Phase B: trades and services

Goal: everything a villager gave you, you can still get, from a person who made it, and it feels like trading with
players.

### B1. Lines of work

- **What:** each townsperson takes up a line by how it plays and what the town needs: **farmer, miner, smith, builder,
  woodcutter, fletcher, fisher, shepherd, hunter, enchanter/librarian, brewer, cartographer**, and the town's **keeper**
  (the stall), **guards** and **leader** (built).
- **How:** a data file of trades (what each does, sells, needs, its tools and station), chosen by traits (a miner: a
  Miner archetype or high skill and curiosity underground; a builder: Builder archetype), by what the town is short of
  (CIVILIZATION.md part 1: "a town short of food gets more farmers"), and by what it's got (an enchanter needs a table).
  A line is a leaning, not a cage: everyone still eats, sleeps, chats and does whims.
- **Test:** a town of 12: lines spread sensibly; `/drifterciv town` lists who does what.

### B2. Making what they sell (real production)

- Farmer: fields harvested, bread baked, crops stored (built: farming).
- Miner: mine trips (built) for ores, coal, stone; smelted.
- Smith: ingots into tools and armour at a crafting table; repairs at an anvil (needs XP: Drifters have XP levels).
- Fletcher: sticks, flint, feathers into arrows; string into bows; hunts chickens for feathers.
- Fisher: fishing (milestone 18), cooks the catch; treasure (books, saddles) sold.
- Shepherd: shears sheep (not built yet: Drifters only kill sheep for wool; shearing comes first), dyes wool, makes beds
  and carpets.
- Hunter: hunts (built: night hunts), leather and meat.
- Enchanter/librarian: books from paper and leather (sugar cane farm), bookshelves; enchants at its table with its own
  levels and lapis (built: enchanting); enchanted books from enchanting books.
- Brewer: nether wart (a Nether trip: milestone 11), a brewing stand, potions (built: brewing).
- Cartographer: maps of where it has been (a real map item, filled by its own walking).
- Builder: builds for the town (built: town builds, roads) and, later, for players (D).
- Each line's goods go to its own chest at home or the store; what's for sale goes to its shop (B3).

### B3. Shops

- **What:** besides the town's stall, a few people sell from their own door: a smith's anvil corner, an enchanter's
  table, a fisher's barrel by the dock. Right-click: the trading screen (built), showing only what's in their shop
  chest, at their prices.
- **How:** `ShopKeeper` per seller (built for the stall keeper), open hours from its schedule (C1), stock from its own
  production; a shop sign over the door with its name and line ("Ann's smithy").
- **What it buys:** what its line needs (a smith buys iron, coal; an enchanter lapis, leather, sugar cane), so players can
  sell too.

### B4. Prices

- Supply and demand per town (CIVILIZATION.md part 2): plenty makes it cheap, short makes it dear; what sells, its price
  creeps up; what doesn't, down. A mining town's iron is cheap, a farming town's bread. Traders between towns (built) carry
  the difference. Haggling: a friend's price is better (D1); a hated player's worse, or no sale.

### B5. Services

- **Enchanting:** you hand over the item and lapis (and pay); it enchants at its table with its levels; you get it back.
  What comes out is the table's roll (fair: the same as yours).
- **Repairs:** at an anvil, with your materials and its levels.
- **Brewing:** you bring ingredients or buy its potions.
- **Smithing:** netherite upgrades if it has a template.
- **Building:** a builder builds you a house from its plans if you bring (or buy) the blocks (later, phase D).
- **How:** the request system (built: `RequestGoal`) and the trading screen together; a service is a request with a
  payment and an item handed over and back (it keeps your item only while it works on it; it never runs off with it, or
  a Bandit might...).

### B6. What villagers gave players, and where it's had now

| From villagers | Now |
| --- | --- |
| Mending, enchanted books | Enchanters' books (enchanting table rolls), fishers' treasure |
| Ender pearls (cleric) | Hunters who kill endermen; Drifters who've been to the End (if on) |
| Bottles o' enchanting | Not sold (no villager source); XP from mobs, as a player |
| Emeralds (selling crops) | Selling to shops; wages for town work (built); emerald ore |
| Diamond gear (smith) | A smith with diamonds to spare (rare and dear) |
| Maps to monuments, mansions | Cartographers who've seen one (they go exploring; built) |
| Glass, bricks, terracotta | Masons/builders who make them |

### B7. Players selling to towns, and wages

- Shops buy what their line needs (B3); the stall buys what the town needs (built).
- Players can work for the town for wages (roads, builds, guard duty), paid from the till (built for Drifters).

---

## 6. Phase C: town life and culture

Goal: a town you can watch for a day and see a day go by, the same way you'd watch players on a server; and over weeks,
a town with its own ways (culture folded in here: decision 7).

### C1. A townsperson's day

- Dawn: up, eat, check its chests and fields. Morning: its line of work (B). Midday: the plaza, the well, a chat.
  Afternoon: work, a trip (mine, trade, envoy), shops open. Dusk: home, the campfire, the porch. Night: in (stakes, 0.22.31),
  a few out hunting (built), guards on watch.
- **How:** the existing goal scoring with a light "time of day" pull per line and per person (habits differ: an early
  riser, a night owl); the schedule (built) already has night styles. Nobody's day is fixed.

### C2. Places to be

- The plaza and well at midday, a campfire or a porch at dusk, the stall in the morning, the fields at harvest. Two or
  three gathered talk (conversations: built), someone sits on a bench (built), someone shows off its gear (built).
- A **gathering spot** per town (the plaza; later a hall or a tavern-like house: a long table, a campfire) where people
  drift to when they've nothing on.

### C3. Comings and goings

- **Newcomers:** a wanderer passing through who gets on with the people (trust) and finds room (an empty house or a plot)
  moves in ("X joined the town"; the chronicle). Towns grow; a full one turns newcomers away.
- **Leavers:** someone who's fallen out with everyone, or bored (mood), or whose friend died, leaves ("X left for good"):
  its house empty, its chest half taken. With permadeath, someone dies and doesn't come back: a grave, grief (built).
- **Houses changing hands:** an empty house goes to a newcomer or a household that's grown (two friends rooming).
- **Building:** a growing town builds more houses, a second stall, walls (founded towns: built; made towns too).

### C4. Events

- **Market day** (every few days): stalls and shops all open, people come from the towns around (trade runs), a
  crowd in the plaza.
- **A build day:** the town builds something together (a wall, a hall, a monument) with everyone helping.
- **Contests:** a fishing contest, a race (the race encounter exists), a duel at the plaza (sparring exists).
- **Festivals:** see C6.

### C5. Animals and the land around

- Pets kept and fed, horses ridden and stabled, the pen tended (built), bees and honey, fishing spots; the woods round a
  town cut over time (felled trees: built), the land worked outward.

### C6. Culture (CIVILIZATION.md part 5, folded in)

- **A calendar of its own:** festivals from the town's history (the day it was founded, a war won, a famous death); on
  the day, people gather at the plaza, a feast (food shared from the store), music (note blocks, jukeboxes if they
  have discs), fireworks if someone made them. "It's Founding Day!" in chat, the chronicle remembers.
- **Monuments:** the town builds one together (a build day, C4) after something big: a statue of a dead leader, a
  tower, a fountain; real blocks, real work; a sign says what it's for.
- **Beliefs:** some towns have a shrine (a small build with candles, flowers, a lectern); a few people keep to it (they
  visit, leave flowers); now and then a cult with odd rules (no killing cows, everyone wears a pumpkin on full moons);
  a rival cult in the next town; a holy place worth fighting over (wars: step 6 part 2). Never preaching at the player:
  players-who-roleplay, not priests.
- **Traditions from what happened:** what the town did more than once becomes its custom (a fishing contest every week
  after the first one was fun; never going near the ravine where three died).
- **A town's character:** from its people (traits averaged) and its history: a friendly market town, a grim mining town,
  a pious town, a rough town; it shows in its talk, its laws (step 5) and its festivals.

---

## 7. Phase D: you and the town

Goal: you can matter to a town, the way you matter on a server: by being useful, being a friend, living there, or being
its problem.

### D1. What a town thinks of you

- Each person's trust and fear (built) and what they've heard (news: built) make the town's view of you: welcome,
  neutral, wary, unwelcome, wanted. Word spreads town to town (envoys, traders).
- Effects: prices (B4), whether shops serve you, whether guards watch you, whether people greet you, whether you can
  live there (D4), whether they'll help you.
- `/drifterciv town` shows where you stand (or just what people say to you).

### D2. Friends

- With one person: trusted enough (thresholds by trait), it: invites you in ("come in, it's cold"), gives you food when
  you're low, warns you of what it's seen (a creeper behind you, Bandits up the road), lends a hand in a fight, trades at
  a friend's price, lets you sleep in its spare bed, tells you things (where the diamonds were).
- Friendship goes both ways: forget it for weeks and it cools; wrong it and it's over (grudges: built).

### D3. Companions

- A friend can come with you ("wanna go mining?" either way; built: following, requests): it follows on its own terms,
  fights beside you, mines its own ores and keeps them, eats its own food, wants to go home when it's tired, hurt, or the
  trip's done, and tells you so. It won't follow you into lava or into the End on a whim (stakes: built).
- Two at most at a time; a companion who dies with permadeath is gone (and its friends know who was with it).

### D4. Living there

- **Asking for a plot:** a town that thinks well of you gives you a plot by a road (the leader decides; laws apply); you
  build your house; you're a citizen: you pay the tax, you can vote (not lead: the user, 2026-10-21), the guards protect
  you, its people are your neighbours.
- **An empty house:** bought from the town (the till) or given, if they really like you.
- **A bed for the night:** a friend's spare bed (D2), or the town's spare house.

### D5. The notice board

- Real needs, posted by real people, in player speak: "need 32 iron, paying 6 emeralds", "lost my dog near the river,
  reward", "bandits up north, 10 diamonds a head", "anyone selling a horse?". Taken off when done or when it's no longer
  needed.
- **How:** requests (built) posted on the board's signs instead of only said aloud; bounties (built: wanted posters);
  lost things (built: lost dog). Bountiful's board, if installed, shows them too (A9).

### D6. Being their problem

- Thief, killer, griefer: fines, exile, bounties, the town's crook (all built: step 5). Hated: shops shut, guards come,
  the town might pay someone to deal with you (bounty hunters: built).

---

## 8. Across all phases

- **Talk:** every step brings its topics and lines (player speak): the new house, a newcomer, someone who left, the
  smith's prices, market day, the player who moved in. Content in `drifter_talk` and `drifter_chat`; docs/CHAT_LINES.md
  regenerated.
- **The chronicle:** every coming and going, opening and closing of a shop, event; the town's book.
- **Commands:** `/drifterciv town` grows (lines of work, shops, where you stand); debug ones to make a town of a
  biome and size, start an event, move someone in or out, set a reputation.
- **Settings** (Mod Menu): replace villages, villagers allowed (decision 5), settlement spacing, the size weights, cities
  on/off and how far apart, ruins, events and festivals on/off, companions on/off.
- **Saves:** towns to be made (A1), half-made towns (A2), shops, lines, reputation: versioned.
- **Data files:** lines of work, biome styles, events, notice board posts: data, so they can be changed in a datapack.
- **Performance:** each step measured (timings) with a town or several near; far towns cost almost nothing (A10).

## 9. Testing, phase by phase

- **A:** a new world flown over (towns found, none half-made, no lag), one of each biome and size, the chronicle's past,
  `/locate`; an old world loads unchanged.
- **B:** a town of 12 over a few days: lines spread, goods made, shops stocked from production only (no item appears),
  prices move; buy, sell, get a sword enchanted, a pickaxe repaired.
- **C:** a town watched over two days: the day's rhythm, a gathering at the plaza, a newcomer moving in, someone leaving,
  a market day with traders from the next town; a festival on its day; a shrine visited; a monument built after
  something big.
- **D:** a player befriending someone (perks show up), a companion trip (it comes, fights, goes home), getting a plot
  and building, a notice board request done and paid.
- Each step: a headless smoke run and a client test, as now; TESTING.md entries in plain language.

## 10. Order of work

After nations (decided). Then:

1. **A1 + A2**, with **A11's site choice** (villages replaced, each town on the flattest ground to hand or none, small
   towns made a slice at a time): the hard part first; worth a step on its own. Then A11's layout and walk check.
2. **A3, A4** (biome styles; small towns, hamlets, ruins); then **cities** (A2's structure pieces, districts, the roads to
   their towns) with **A10** alongside (a city needs the summary layer).
3. **A5, A6** (a past, people's touches), **A7** (raids, golems, the wandering merchant), **A9** (other mods).
4. **B1 to B3** (lines, production, shops), then **B4 to B7** (prices, services, villager goods).
5. **C1 to C3** (the day, places, comings and goings), then **C4 to C6** (events, animals, culture).
6. **D1 to D3** (reputation, friends, companions), then **D4 to D6** (living there, the board).
7. (A10 comes with cities, step 2.)

Each a version, a test, and a line in the status table at the top.

## 11. Questions for the user

All answered (decisions 1 to 9 at the top). New ones go here as they come up.

## 12. Progress

(Entries here as steps are built, newest last, as in PROGRESS.md.)

- 2026-10-29: planned (this document); decisions 1 to 4 above.
- 2026-10-29: decisions 5 to 9 (villagers as a setting, milestone 23, culture folded into phase C, cities and their
  towns with the spacing worked out in A4, the player a citizen only for now).
- 2026-10-29: decisions 10 and 11 (steep, hilly worlds: the user's Tectonic pack; towns on the flattest land to hand, or
  none), A11 written.
- 2026-10-29: started A1 + A2 (0.23.0). Measured making a 12-house town: 17 s, 11 of it the house layout asking every
  plan's door sides at every spot (now kept per plan: the layout 0.7 s; a town about 2 s besides loading its land).
- **0.23.0: A1 + A2 + A11's site choice (towns where villages were, made a step at a time, on the flattest ground).**
  - Villages replaced (setting "Towns replace villages", on; new chunks only): Drifter's hook `DrifterApi.setTownsForVillages`
    (the addon sets it); Drifter's mixins give anything tagged #minecraft:village no placements (`ChunkGeneratorStructureState`)
    and no generation point (`Structure`), so none generates and /locate and maps give up at once (found: with villages
    only stopped where they'd start, /locate for one ran a minute and the server shut itself down); no wandering trader;
    no zombie villagers spawning unless "Villagers" is on (off; for trading halls and iron farms; takes a restart).
  - `drifter_civ:town` (structure set `drifter_civ:towns`: random spread, 54 chunks apart, 16 at least; biomes: vanilla's
    village biome tags and sunflower plains). Its spot (A11, the user's "flattest land given the choice"): the land within
    112 blocks looked over on a 24-block grid from the generator's own heights, nearest first, its biome asked from high
    above first (biomes go by height: under a hill, at sea level, it's a cave's), heights worked out only as needed; a
    spot's core (48 blocks) may rise and fall 12 at most, the whole (72) 64, a quarter water; scored by roughness, water
    and how far it moved; the first one flat enough (4 and 20) taken; nowhere: no town. On the user's Tectonic: a spot's
    search 1 ms typically (1.3 s at worst, on a worldgen thread), /locate 1 to 2.6 s; of 44 spots 10 got a town, the rest
    in no village biome at all (forests, mountains, sea), only a few turned down for the ground.
  - The structure places nothing: a marker records the spot as its chunk generates (`TownSites`, saved with the towns).
    A player within 12 chunks: the land round it (radius 10 chunks) asked for with a ticket, loaded out of sight, and once
    it's really there (a chunk made, not just asked for: `hasChunk` only says it's meant to be, and the first test made
    the land on the spot, 2.7 s in one step), the town made a step at a time (`TownBuilder.Job`): layout, plaza, store,
    fields, pen, the houses (a few spots tried a step), each road, the plaza's things, road lamps, the stall and paths, the
    trees it cut (32-block pieces), each household, the town and its people, its lights (pieces); 10 ms of steps a tick.
  - Making a town: 17 s before (one go); measured: 11 s of it the house layout asking each of ~300 plans' door sides at
    every spot (now kept per plan, `DrifterApi.doorSides`), the ground re-measured each try (kept per column while laying
    out, forgotten when a block in it changes). Now about 2 to 2.6 s of work over 4 to 14 s, the longest step 60 to 98 ms
    (tree tidying), a handful over 50 ms. House plans read at world start (353 ms).
  - `/drifterciv town sites` (spots not made yet, nearest first), debug `town sites make` (the nearest made now, player
    or not), `town go` (to the nearest town's plaza); `/locate structure drifter_civ:town`.
  - Tested: headless (vanilla and the user's Tectonic: towns located, villages "Could not find", a town made a step at a
    time); in the client on Tectonic, flying in from 400 blocks off: the town made itself as the player came within 12
    chunks, on flat savanna by the sea, the hill beside it left alone (screenshots).
  - Next: A11's layout and walk check, then A3/A4 (biome styles, sizes, hamlets, ruins).
- **0.23.1: A11's layout and walk check.**
  - Door paths: from the door out of the house (a porch is part of it) to its road, up to 16 blocks, laid a block up or down
    a step at most from the door's floor to the road, the ground built up with dirt or cut down along it, path on top,
    three blocks of room cleared over it (on the user's Tectonic land a door could stand three blocks over its road with
    nothing between: just the kind of spot a Drifter pillars at).
  - The walk check (`TownWalk`), the last step of making a town: from the plaza, everywhere reachable on foot as a player
    walks (a block up with room to jump, up to three down, two blocks of room, through doors and gates, no swimming, nothing
    placed or broken); every door, the store, the stall (any side), the fields (any side) checked; what can't be reached
    goes in the log and the town's message.
  - Tested on Tectonic: four towns, 53 of 53 places walked to (the one miss before, a field, was reachable from its other
    sides); half a day in a hill town by the sea in the client: no pillaring, one failed bridge out at a mine, no plugs in
    the town (189 cave plugs, all 75 to 100 blocks out, in its people's mine stairs past the edge, and one 30 blocks under).
- **0.23.2: A3 (towns in their biome's style) and A4's first sizes.**
  - A town's style by the biome at its middle (`TownBuilder.Style`): plains (oak, birch, some spruce; cobblestone; a
    stone-brick plaza), desert (birch and acacia; sandstone for the houses' cobblestone, sandstone store walls, a smooth and
    cut sandstone plaza, smooth sandstone roads and paths, over sand too), savanna (acacia, some oak and dark oak), taiga
    (spruce, some dark oak and oak; mossy cobblestone in the houses and the plaza), snowy (spruce, birch, dark oak; a stone
    brick plaza). Its main wood for the store, stall, well, pen, fences and lamp posts. No more cherry and mangrove houses
    in a savanna.
  - The houses' stone: a palette's new "@stone" (kept with its woods, so a home's saved palette has it and its people
    rebuild in the same stone; `DrifterApi.placeHouse(..., wood, stone, front)`, `PlacedHouse.stone`).
  - Sizes (A4): a quarter of spots a hamlet of 2 to 5 households, the rest small towns of 8 to 16 (cities and ruins next).
    Hamlets have the same plaza, store and stall for now, only fewer houses.
  - Tested on Tectonic: seven spots, six made (taiga, plains, desert; 2 to 16 households), 79 of 79 places walked to;
    screenshots of a taiga town among spruce by a lake and a desert hamlet in the dunes.
  - Next: A4's ruins and cities (with A10, the summary layer for towns nobody's near), then A5 (a past), A6, A7, A9.
- **0.23.3: A4's ruins.** A twentieth of spots are the ruins of a town that failed: 4 to 7 houses laid out as a town's,
  then fallen in (doors, beds, torches, lanterns and carpets gone; glass mostly gone; wood gone more the higher it is, so
  roofs have fallen in; cobblestone gone to moss in places; cobwebs; chests empty), the fields gone to grass and coarse
  dirt, the pen empty with gaps in its fence, no bell or lamps, the store empty but for some bones and rotten flesh, and
  two to four graves west of the plaza (coarse dirt and a sign: "here lies", a name as Drifters have them, "rip" or
  "o7"). Nobody lives there; it isn't a town (no group), only a line in the chronicle ("The ruins of X stand where a
  village might have"). `DrifterApi.randomName`. Debug: `/drifterciv town ruin` (made here at once). Tested in the
  client: made in about 1 to 1.7 s of steps; a screenshot of fallen-in roofs (another round of screenshots came out
  blank, the land not yet drawn after the teleport: to look at again).
- **0.23.4: A5 (a town with a past), A6's touches, A9's Bountiful board.**
  - A town made by world generation (or `town spawn`) has a past, written into the chronicle on the days it happened
    (before the world's first day if need be: "20 days before day 1"; `Chronicle.addPast`, `dayName`): founded 20 to 60
    days back by its leader and a friend, its first houses going up, its leader chosen, how many houses it had by
    halfway. And things in it to find that match: half the time someone left for good and their house stands empty (its
    household never moved in, its chests bare); often a newcomer arrived two days ago and its house is only a third built
    (its top taken off; it builds the rest over the next six days with what it gathers: `DrifterApi.stillBuilding`, the
    raid mending's way); often someone died (a grave west of the plaza with their name, the death in the chronicle with
    how, and its people now and then saying "still think about X sometimes": flag `town_past_dead`, word `dead`, saved
    with the town); sometimes a hard winter (a corner of a field gone to coarse dirt).
  - A6: a sign with its owner's name by each door path, facing the road; a third of households keep a named dog or cat by
    the door, tamed to them.
  - A9: with Bountiful installed, its bounty board on each town's plaza (its boards were in villages, and there are none).
  - Tested on Tectonic (headless, three towns): each with its past in the chronicle (founded 28 to 54 days before day 1; a
    drowning, a creeper, a fall into a ravine; two who left, two newcomers still building, a hard winter), every place
    walked to; tree tidying in 20-block pieces now (a taiga town's took 120 ms a piece at 32): no step over 50 ms in two of
    them, 45 ms at most.
- **0.23.5: cities (A4), and front doors found properly.**
  - One town spot in each three by three of spots (about 2,600 blocks across; which one picked from the world's seed) is a
    city of 40 to 80 households if a town goes there, so cities are rare and never close. A city's main roads run 132
    blocks (a town's 72); its side roads come off any road, side roads too, and run up to twice as long; a hundred people
    at most (its other houses stand empty for newcomers); its land (13 chunks round) loaded once a player is within 20
    chunks. Chronicle: "X stands where a village might have: a city, with 92 people in 44 houses". Debug: `/drifterciv
    town city <40-80>` (a city spot here, made a step at a time).
  - Made a step at a time like a town: its streets a few tries a step (2.6 s at once before), each street kept inside its
    reach before its ground is looked at (beyond it the land wasn't loaded and looking made it: 2.8 s), a house spot's
    plans tried 15 ms a step and picked up again next step (400 ms before). A city: about 7 to 10 s of work over 20 to 35
    s, the longest step the walk check (110 ms, once).
  - Front doors (core `HousePlacer`): a door counts as a house's front only if one walks straight out of the house from it
    at door height; its side is the way out. Before, any door was given the side of the box it was nearest, and a
    two-room farmhouse's inner door was taken for its front: its path and the walk to it went through the house (found by
    the walk check in a city: every unreachable house was one of those). Lamp posts kept off door paths too.
  - Tested on Tectonic: a city of 92 people in 44 houses, all 48 places walked to; the seven-spot run again: a city made
    by world generation (69 people in 35 houses), a ruin, towns and a hamlet, every place walked to, no step over 116 ms.
  - Not yet: districts, a market street, more stalls, walls; A10 (towns far away as summaries: for now their people go
    on as every Drifter does out of sight).
- **0.23.6: A7's raids on towns.** Vanilla's raids don't come to a Drifter town (found in testing: a player with Bad Omen in
  one, no raid; vanilla counts a village by POIs its villagers have claimed, and Drifters' beds and bell aren't claimed).
  `TownRaids`: a player with Bad Omen within 64 blocks of a town's middle (not on peaceful) spends it ("Something's coming
  for X..."); half a minute later raiders come from 80 blocks out, making for its plaza when they've nobody to fight:
  waves (two, a city three), 2 + the wave's number + one for every eight of its people (ten at most), a quarter of them
  vindicators, the rest pillagers (a city's last wave with a ravager); the next wave when a quarter of the last are left
  or three minutes on. Its people fight or run by their natures (as with any hostile mob). All down: "X fought off a raid
  (n raiders brought down, m of them by <player>)", and each of its people trusts the player who helped more (12, and 2 a
  raider, up to 10). Ten minutes after the last wave with raiders still about: they make off. Debug: `/drifterciv town
  raid` (Bad Omen given). `DrifterApi.addTrustInPlayer`.
  - Tested in the client (a town of about 20, the player watching in creative): first try, waves of 7 and 8 with a third
    vindicators, 12 of its people's lives lost in four minutes and the raid not over; rebalanced, two waves (5 and 6), it
    fought them off: 11 raiders brought down, 3 lives lost.
  - Next: phase B (lines of work, production, shops); A10 and the wandering merchant (A7) when they're needed.
- **0.23.7: B1, lines of work (first part).** `Trades`: each townsperson has a line, given the first time it's asked and
  saved with the town: the stall's keeper, the leader, its guards (the two the town's made with; else the boldest two),
  at least two farmers, then by its nature (Builder: builder; Miner, Hoarder, Speedrunner: miner; Homebody, Helper:
  farmer; Hermit: woodcutter; Nomad, PvP sweat: hunter) while that line is under a quarter of the town, and the rest to
  whichever of farmer, woodcutter, hunter, builder, miner has fewest. A line is a leaning: in what it chooses, its
  line's goals count 0.08 more (a farmer: the farm, the pen, cooking; a builder: roads, town builds, easing the ground,
  lighting, its house; a miner: getting on, caves; a hunter: hunting; a woodcutter: clearing trees, gathering; a guard:
  guarding, defending, driving people out). Core: `TownWork.leaning(drifter, goal)`, added to a goal's urgency.
  `/drifterciv town` shows who does what ("Work: builder: ...; farmer: ..."). Talk flags `line_<line>`, word `line`.
  Tested headless: a town of 24: 4 builders, 4 farmers, 2 guards, 4 hunters, 5 miners, 3 woodcutters, the keeper, the
  leader (before the quarter cap: six builders).
  - Not yet (B1 to B3): lines that make things to sell (smith, fletcher, enchanter, fisher, shepherd), their shops and
    prices.
- **0.23.8: towns and cities located apart** (the user: "is there a way we can separate the command to locate towns and
  cities?"). A city is its own structure now, `drifter_civ:city` (structure set `drifter_civ:cities`, placed exactly as
  the towns' set, so on the same spots; biome tag `#drifter_civ:has_structure/city`, the towns' biomes). The town one
  gives way where a city goes, the city one goes only there (that's checked first, before the land is looked over).
  `/locate structure drifter_civ:town` finds towns (hamlets and ruins too), `drifter_civ:city` cities, and the structure
  tag `#drifter_civ:settlements` either. In a world made before 0.23.8, cities already made stay `drifter_civ:town`.
  Tested headless on seed 779: from spawn a town at 1296,-512 and the city at -2304,-1136; standing in the city, the town
  locate skipped it (the next town 1,619 blocks off) and the city locate found it 65 blocks off.
- **0.23.9: B2 and B3, trades that make things and shops at their doors (first part).**
  - Lines of work are data now: `data/drifter_civ/drifter_lines/<line>.json` (the goals it leans toward and how much, the
    natures that take to it, and for a trade its shop: from what size of town, the word on its sign, what it makes and
    how many it keeps, what else it sells, what it buys). A datapack can change or add them.
  - Four trades: **smith** (iron tools and armour, shears, buckets, shields; buys iron and coal), **fletcher** (arrows,
    bows, rods; buys feathers, flint, string, sticks), **fisher** (rods; sells the fish it catches and cooks and what comes
    up with them: name tags, saddles, books; leans on fishing more than most), **enchanter** (paper, books, bookshelves;
    sells enchanted books and lapis; buys lapis, leather, sugar cane). A town has a smith and a fletcher from 8 people, a
    fisher from 10, an enchanter from 12, another of each for every 30 more. Its lines given before there were trades
    (towns made before 0.23.9): one from the commonest line takes the trade up, the one it suits best ("X took up the
    smith's trade in Y").
  - Making (B2, core `TownWork.wares`, goal "wares", urgency 0.5 by day): whichever of its trade's things it's short of
    (in its home's chests and its pockets, past one of a tool for its own use) that it can make most cheaply, got the way
    it gets anything: the planner mines the iron, hunts chickens for feathers and cows for leather, smelts, crafts at its
    table. What it makes goes into its chests at home like anything else. (At 0.4 a smith tended the town's farm most of
    the day; found in testing.)
  - Its shop (B3, `Trades.LineShop`, core `ShopKeeper.open` for its hours): late morning (2000 to 5000) it takes what it
    has to sell from its chests and stands by its door path, by the sign with its name (A6), which it writes over the
    first time ("Ann's / smithy"); right-click: the trading screen, from its pockets, at the town's prices (dearer when
    it's scarce); it buys what its trade needs while it has fewer than 32, with its own money (it keeps its takings). Other
    Drifters buy from it as from the stall. The rest of the day it makes things, eats, sleeps and lives; off on a mining
    trip, its shop's shut that day. Core `DrifterApi.frontDoor`.
  - `/drifterciv prices`: the stall and each trade's shop (where, open or shut, what it's making, what's at home to sell,
    its offers). Talk topics: the smith short of iron, the fletcher after feathers, the fisher's catch, the enchanter's
    books (`trade_*_talk`).
  - Tested headless (seed 779, the plains spot, a town of 21, a day and a half): the trades given out (a smith, a fletcher,
    a fisher, an enchanter), stands found by each door; the smith made shears, a bucket, a shield, an iron helmet and
    boots, then went mining for more iron and sold a spare iron pickaxe for 2 emeralds; the fletcher made 32 arrows and kept
    its shop. Found and fixed: a door path is a dirt path, not a full block, so no stand was found by any path.
  - Not yet: shepherds (Drifters don't shear sheep yet: wool from the planner means killing sheep; shearing first), smiths
    repairing at an anvil and enchanters enchanting your things (B5), prices that move with sales (B4).
  - Next: shearing and the shepherd, then B4 (prices that move), B5 (services: repairs, enchanting your things), B6, B7.
