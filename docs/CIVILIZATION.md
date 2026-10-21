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

## Groundwork in Drifter first (proposed 2026-10-21; the user: "go ahead")

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

## Questions for the user (answered 2026-10-21)

1. The addon's name, and the same repository (recommended) or its own? **"same repo, call it Drifter: Civilizations".**
2. How big should a world's civilization be: a few towns, or dozens? **A few towns ("should be a few towns, but that can
   be tbd down the road"): build for a few, keep the size a setting so it can grow later.**
3. Should a player be able to become a leader, or only ever a citizen, trader or enemy? **A citizen or an enemy for now
   ("they should only be a citizen right now", then "they can also be an enemy aswell").** A player can live in a town
   and be one of its people, or be against a town (raid it, be wanted, be fought), but never lead one; leading can come
   later if wanted.
4. Which parts first, if not the suggested order? **The suggested order ("keep doing the suggested order").**

## Progress

- **Step 1, the shell and the door (done, 0.22.0):** the addon is a second mod in this project (`src/civ`,
  `src/civClient`), built with Drifter into its own jar, `drifter_civilizations-<v>.jar`, asking for exactly the same
  Drifter. Drifter's API (`com.dinglebob.drifter.api`, docs/API.md): register goals and catch-up steps; events (died,
  respawned, home set, group changed, a player's deed); a few readers and setters. The addon loads, says so in the log,
  and `/drifterciv` shows its version, Drifter's, and how many Drifter deaths it heard of through the API. Tested
  headless: both mods load, a death reached the addon.
- **Groundwork 1, the directory (done):** `DrifterRegistry` is now the world's directory of Drifters (`DrifterRecord`:
  status alive / dead and coming back / gone, home and bed, group, stage, deaths, archetype, traits, closest Drifters and
  players), kept while they're unloaded, gone ones kept for the history; `DrifterApi.directory()`, `/drifter directory`.
  Tested headless: six Drifters over a day and a half, a death (dead and coming back, its stage and friend kept), back
  alive after the respawn.
- **Groundwork 2, news that travels (done):** each Drifter carries up to 32 pieces of news (`NewsBook`): a player's
  deed that moved its trust by 15 or more, a death it saw (and who did it, if a player or a Drifter), or another mod's
  (`DrifterApi.tell`). Meeting a Drifter it doesn't dislike (trust above -15, within 8 blocks, in sight), once a minute
  at most and more often the more sociable it is, it passes on the newest three the other hasn't heard. The listener
  believes it by its trust in the teller (a stranger: half), less with each telling (x0.7), five tellings and two days
  at most: a player's deed moves its trust in that player by about a third of that (more if it happened to a friend); a
  death saddens a friend, and a player killer is trusted less. With a real player within 16 blocks it now and then says
  it out loud ("did you hear? Steve attacked Bob again and again"). `DrifterEvents.HEARD_NEWS`, `DrifterApi.news()`,
  `/drifter news` (and `/drifter news tell <text>`, debug). API version 2. Also fixed: Drifter death messages said only
  "X died" (the fight was forgotten before the message was read). Tested: headless, news told to one of three Drifters
  reached the other two in two tellings; in the client, a player's attack on one Drifter reached the others (trust down
  a little each, said out loud), and a Drifter that came later heard it and the death from them.
- **Groundwork 3, Drifters trading with each other (done):** two Drifters that meet and don't dislike each other: now
  and then (once in five minutes a pair) one asks the other for what it needs most that the other can spare (better
  gear, food when short, iron with no iron pickaxe, torches, coal, wood; "spare" is what it would put away anyway, or
  the plenty of common things), and offers about the worth (`ItemValues`) from what it can spare that the other wants,
  or emeralds. A greedy one asks up to a third more, a friend or group mate a quarter less; a friend gives a little thing
  (worth 16 or less) for nothing and is owed for it, which comes off the next price. Agreed, both drop what they're doing
  (not in a fight), walk up to each other, look down and toss their side over (real items, only the other can pick them
  up), and pick up what they were given; a minute to meet, 30 seconds to swap, or it's off. Trust +3 each (+6 for a
  gift). With a player near: "got any iron ingots to spare? I'll give you 8 bread", "deal". `trade/DrifterDeals`,
  `DrifterEvents.TRADED`, `/drifter barter` (debug: ask now). Tested headless: goods for goods (5 iron for 8 bread),
  an emerald for a stone sword, a friend's gift of 8 logs (owed after), and two Drifters trading on their own.
- **Groundwork 4, shared things and bigger groups (done):** group members at the leader's base share what they know of
  it every 5 seconds (`Home.shareFrom`: chests, the leader's house plan, hut and house finished, farm, pen, enchanting
  table, banner; a chest only if it's still there; not beds); a member with no home moves in. The group keeps its
  base's chests (`Group.chests`, saved). Building one plan together: a builder claims the block it's working on for 10
  seconds and the others take the next (`BuildPlanActivity` claims); a member helps when a mate close by is building the
  house (whatever its own stage); it waits for the last blocks a mate is placing rather than calling the house done.
  Only one member builds the hut at a time (found in testing: two at once undid each other's work). Group size is a
  setting (2 to 32, default 4) and a group can have its own limit (`DrifterApi.setGroupLimit`). Debug: `/drifter house
  site <corner>`, `/drifter config groupsize <n>`. Tested headless: a member got the leader's plan and spot; the leader
  started it, the member came to help, both built at once (101 of 145 blocks in three minutes, no block undone); the
  pair went mining together.
- **Groundwork 5, roads (done):** routes keep to paths and roads it has seen (the block tag `drifter:roads`, dirt paths
  by default, more from a data pack or the addon: a step on one costs 0.7 of a plain one; its own trails stay at 0.8).
  It counts how often it walks each block (halved every day); where it has walked six times or more within 48 blocks of
  home, it now and then wears a path in with its shovel (16 blocks a go, half a day between; builders and homebodies
  most), as players do; a path once begun is walked the more, and grows. Not on its farm or in its buildings.
  `skills/PathGoal`, `DrifterMemory` wear. Tested headless: walked a route 8 times, then wore 16 blocks of path along it.
- **Next:** a 30-day run.
