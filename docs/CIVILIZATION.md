# Civilization (milestone 22, planned): an addon mod

The user (2026-10-20): "is it possible to simulate a civilization as in depth as we can? taking inspiration from those
minecraft '200 player' '1000 player' '300 player' 'one island' civilization videos". Then: "save it as a mile after 21,
in fact its really big i think it should be like an addon mod".

Nothing is built yet. This is the plan to start from once milestone 21 (encounters and bounties) is done. It will be
split into several smaller milestones.

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

## Questions for the user (when we start)

1. The addon's name, and the same repository (recommended) or its own?
2. How big should a world's civilization be: a few towns, or dozens?
3. Should a player be able to become a leader, or only ever a citizen, trader or enemy?
4. Which parts first, if not the suggested order?
