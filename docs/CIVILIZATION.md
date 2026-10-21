# Civilization (milestone 22): an addon mod, Drifter: Civilizations

The user (2026-10-20): "is it possible to simulate a civilization as in depth as we can? taking inspiration from those
minecraft '200 player' '1000 player' '300 player' 'one island' civilization videos". Then: "save it as a mile after 21,
in fact its really big i think it should be like an addon mod".

Started 2026-10-21 (the user: "same repo, call it Drifter: Civilizations, go ahead"): the name is **Drifter:
Civilizations** (mod id `drifter_civ`), in this same repository. Step 1 is done (the two-jar build, the API, the empty
addon; see "Progress" at the end). It will be split into several smaller steps.

## An addon mod

Civilization is its own mod that needs Drifter installed (user's call). Drifter alone stays as it is: Drifters playing
survival. Adding the addon turns them into towns, economies and nations.

- **Suggested name:** Drifter: Civilizations (mod id `drifter_civ`). The user decides.
- **Suggested home:** this same repository, as a second Gradle project that builds a second jar next to Drifter's.
  One build, one GitHub Actions run, and the two never drift out of step. A separate repository is the alternative.
- **What Drifter needs first: a small API** (its own step, before any civilization feature) so the addon plugs in
  without copying code:
  - register goals (the addon's "work my job", "go to market", "pay my taxes", "march to war");
  - register whim actions and whims (the whim registry is already one line per action);
  - add catch-up phases (towns working while unloaded);
  - events to listen to: a Drifter killed, robbed, traded with, insulted, seen stealing, sleeping, joining a group;
  - read and change a Drifter's group, bonds, trust, memory, home, inventory and thoughts;
  - its own saved data, with versions (DESIGN.md section 20).
- **The rules don't change:** everything the addon adds obeys DESIGN.md sections 1 and 2. A wall takes real stone and
  real time; a Drifter learns news by seeing it or being told, never by just knowing; no free items, even for kings.

## Two layers (how it scales)

About 100 fully simulated Drifters cost about 10 ms of a server tick today (milestone 16), so a thousand at full
detail isn't possible on one PC. So:

- **Near players:** Drifters as now, every one played out block by block.
- **Everywhere else:** each town is a cheap summary (who lives there, its stockpile, buildings, leader, laws, friends
  and enemies) that moves forward like catch-up does: harvests, building, trade, births of new ideas, arguments,
  elections, wars, decided from the same numbers the full Drifters use. When a player comes near, the town is there
  as the summary says, and its Drifters carry on in full.

Dozens of towns can exist; only the ones being watched cost much.

## The parts

### 1. Towns
- A group picks a town spot (water, flat land, wood, safety; near its members' homes) and settles it together.
- Shared things: a town chest store, shared farms and pens, roads between houses, a wall and a gate, a meeting hall, a
  well, a bell. Built from shared plans (the house plans, milestone 15, grown to town plans), each block by someone.
- Jobs from personality and need: farmer, miner, builder, woodcutter, guard, smith, cook, trader, explorer. A town
  short of food gets more farmers.
- New members: wanderers move in, members leave after a fight or out of boredom, towns split.

### 2. Money and trade
- A currency: emeralds, or a coin minted from gold or iron at the town (real items, made with real recipes).
- Market stalls and shops; prices from supply and demand (iron cheap in a mining town, dear in a farming town).
- Trade runs between towns by road, horse or boat (with ambush risk: milestone 21's bandits).
- Players can buy, sell, get hired, run a shop, or corner a market.

### 3. Leaders and laws
- A leader: chosen by trust (an election, with campaigning), by strength (a warlord), or by founding the town.
- Simple laws: no stealing, no killing in town, a tax, a curfew, who may use the farms.
- Crime and punishment: thieves are seen and reported, guards chase them, fines, prison, exile, bounties (21).
- Unpopular leaders lose trust; revolts, coups, assassinations, abdications.

### 4. Nations and diplomacy
- Towns join into nations (by agreement, marriage-like alliances between leaders, or conquest).
- Diplomacy: trade deals, alliances, tribute, borders, insults, broken promises remembered.
- Wars over land, iron and grudges, grown from the team wars of milestone 12: armies, sieges of walls, peace
  treaties, occupied towns, rebellions and independence.
- Spies, scouts and messengers carry news (news only travels by someone carrying it).

### 5. Culture
- Flags and banners for towns and nations (banners exist for groups already).
- Monuments, statues, festivals, a calendar of yearly events.
- Beliefs: a shrine, a cult with odd rules, a rival cult; holy places worth fighting over.
- Traditions grown from what happened (a feast every year on the day a war was won).

### 6. History
- A chronicle as things happen, readable in game (a book at each town hall, a command for the whole world): "Day 12:
  Ironhold elects Ann. Day 15: Ann taxes the farmers. Day 19: the farmers revolt." This is what makes it feel like
  those videos.
- Territory on a map; each town's rise and fall over the days.

### 7. "One island" mode
- One command (or a world option) drops 50 to 100 new Drifters on an island with nothing, the way the videos start.
- `/tick sprint` fast-forwards days; a summary each morning of what happened.
- Watch as a spectator, or join as a citizen, a merchant, a warlord, a spy.
- Also the test bench for every part above.

## Suggested order

1. The Drifter API and the addon's empty shell (a second jar that loads).
2. "One island" mode and the chronicle first: the stage to watch everything on, and the record of what happened.
3. Towns.
4. Money and trade.
5. Leaders and laws.
6. Nations and diplomacy.
7. Culture, then the far-away town summaries tuned for big worlds.

## Groundwork in Drifter first (proposed 2026-10-21, waiting on the user)

The user: "think real hard about what should we work on/add before we start the civilization milestone; want this
process to be as easy as possible while also updating the og mod, so the addon mod isnt incompatible".

**Keeping them compatible (do first, small):**
1. Two jars, one project: the addon as a second Gradle project here now, an empty shell that loads. Every build compiles
   both, so a Drifter change that would break the addon fails the build at once and is fixed in the same change.
2. Versions in step: both jars carry the same version; the addon's fabric.mod.json asks for Drifter's same minor version
   ("drifter": "~0.22.0"), so a mismatched pair says so plainly instead of crashing.
3. A small official API (`com.dinglebob.drifter.api`), the addon's only door into Drifter: register goals and catch-up
   steps; Fabric-style events (died, respawned, killed, robbed, traded, joined or left a group, settled a home, saw a
   crime); read and change a Drifter's home, group, trust, memory, inventory and thoughts. The addon keeps its own data
   on Drifters with Fabric data attachments (Drifter's save format untouched). Inside Drifter, anything can move; the door
   stays put.
4. The headless smoke test loads both jars and runs a day, every time.

**Drifter features civilization stands on (each worth having in Drifter alone):**
1. A world-wide Drifter directory (DESIGN.md section 20's world-level registry, never built): who exists, their home,
   group, closest relationships, alive or gone, a few stats, kept while they're unloaded far away. Today most of a Drifter
   lives in its own save and can't be read unless it's loaded; towns of far-off Drifters need this.
2. News that travels: today group members within 8 blocks swap news; make it any two Drifters who meet and get on (who
   killed whom, dangers, a player's deeds, later prices and wars), believed by trust. Elections, crime reports and
   diplomacy all run on it; alone, reputations spread the way they would.
3. Drifters trading with each other (items for items or emeralds; today: gifts, and trading with villagers only). Money
   and markets need it.
4. Shared things: chests that belong to a group, and building one plan together (claims, a shared stockpile; the
   builders encounter is a tiny version). Town stores, walls, halls and roads need it; alone, a group's base is truly
   shared.
5. Bigger groups (four at most today) or a membership that a town of 10 to 30 can use.
6. Roads: the pathfinder preferring known paths (an old TODO), and paths worn between places walked often.

**Steadying first:** a 30-day run with 50 Drifters (memory, save sizes, loops) since civilizations run for weeks; a round
on getting about rough ground (falls into ravines). Scale is already enough for "one island" (300 round a player at 20
TPS). Not needed first: The End, difficulty scaling, the Knowledge trait.

**Suggested order:** the two-jar shell and API door; the directory; news and trading; shared chests, building together,
bigger groups; roads; the 30-day run. Then milestone 22 proper, starting with "one island" and the chronicle.

## Questions for the user (when we start)

1. The addon's name, and the same repository (recommended) or its own?
2. How big should a world's civilization be: a few towns, or dozens?
3. Should a player be able to become a leader, or only ever a citizen, trader or enemy?
4. Which parts first, if not the suggested order?

## Progress

- **Step 1, the shell and the door (done, 0.22.0):** the addon is a second mod in this project (`src/civ`,
  `src/civClient`), built with Drifter into its own jar, `drifter_civilizations-<v>.jar`, asking for exactly the same
  Drifter. Drifter's API (`com.dinglebob.drifter.api`, docs/API.md): register goals and catch-up steps; events (died,
  respawned, home set, group changed, a player's deed); a few readers and setters. The addon loads, says so in the log,
  and `/drifterciv` shows its version, Drifter's, and how many Drifter deaths it heard of through the API. Tested
  headless: both mods load, a death reached the addon.
- **Next:** the groundwork in Drifter (above): the world-wide Drifter directory, news that travels, Drifters trading
  with each other, shared chests and building together, bigger groups, roads, a 30-day run.
