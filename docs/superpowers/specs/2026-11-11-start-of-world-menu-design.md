# The start-of-world menu (who are you?): design

Drifter: Civilizations (the addon: `src/civ`, `src/civClient`). Part 2 of four of the user's "RPG profile" idea
(2026-11-11); part 1 is the journal (`2026-11-11-player-journal-design.md`, built, 0.23.41). Everything here was decided
with the user in chat unless marked **(builder's call)**.

## The idea

The user: "while creating a new world and the town plan is loading we get a menu, picking our name and pronouns, and a
nation we want to be a citizen of, or no nation at all."

## Why

A new player arrives as nobody, nowhere. Picking who you are and where you're from, before the world opens, makes it an
RPG start: you begin in a town that takes you as one of its own, in a nation with friends and enemies already.

## Goals

- Before you play a new world you choose a name, pronouns, and a nation (or none).
- The choice matters from the first minute: where you start, how towns take you, what your journal says.
- It looks like vanilla, and the wait for the world to be built is spent choosing.

## Not in this part

- Leaving or joining a nation later (something done in the world: a later part). The nation is a one-time pick.
- The chronicle revamp (part 3) and quests (part 4). They are to be written with the player's name and pronouns from the
  start.
- A house, items or emeralds for a citizen. Friends for free.
- Rewriting the existing talk topics for pronouns (they don't use pronouns for players).
- Anything in the whim system or its files.

## Anti-goals

- New art (the user, part 1: "only using vanilla resources, no ai generated art"). Vanilla textures, widgets and font;
  flat fills by code where vanilla has none.
- A map, coordinates or exact numbers on the nation cards: the world stays yours to discover.
- A menu that makes the wait longer than it is today when you pick quickly.

## What it is

### The flow (the user: "the planning stage is blended in with the world gen loading screen; after the planning is done, the menu opens with the towns building")

1. **Planning hides in vanilla's own world loading screen** (the square map with the percentage). A new planned world
   doesn't let the player in until its plan (nations, towns, roads, history) is made. The separate "Planning the nations"
   stage goes. Whether vanilla's percentage can count the planning is unknown; the screen staying up until the plan is
   done is the requirement, the number is not **(builder's call; say what it ended up showing)**.
2. **The menu opens as you enter,** with the nations already listed, and the "Building towns (3 of 12)" bar running at
   the bottom of it while you choose. You are held and unhurtable as on the loading screen today; the world doesn't show
   through; it can't be closed with Esc.
3. **While the menu is open, one home town per nation is built first** (the user: "yes, one home town per nation sounds
   right"). The home town of each nation is picked when the plan is made: one of its ordinary towns, never the capital
   (the user: "it should actually be a nation's town, for the capital for us to be discovered") and never a ruin, a fair
   way from the capital **(builder's call on which)**.
4. **Begin:** you are put in your home town; the screen you know ("Building towns (N of M)", numbered bar) finishes your
   home town and what's in sight of it, and then you play. "No nation": you start in the wild at the normal spawn, as
   today, and the build goes on as today.

### The menu

Roughly as the mockup shown in chat (the user redirected the flow, not the layout): title "Who are you?"; on the left a
name box, a pronouns button (cycles), your skin; on the right "Where are you from?", a list of nation cards in the
vanilla world-list style, the last card "No nation"; a Begin button; the building bar along the bottom.

- **Name:** what Drifters call you when they talk to and about you, and the name at the top of your journal. Your
  Minecraft name stays everywhere else (tab list, above your head, commands). Empty: your Minecraft name, as today.
  Length and characters as a Minecraft name allows plus spaces, 3 to 16 **(builder's call)**.
- **Pronouns:** he, she or they; they if you don't pick. They matter where the game talks *about* you: Drifters
  gossiping about you, bounty and law wording, later the chronicle and quests. A small set of new lines about the player
  is added so the choice is heard **(builder's call on which lines; outside the whim files)**.
- **A nation card,** in words like the journal:
  - its name, and its banner in its colour;
  - its character, one line from its ruler's nature ("a proud, warlike people", "careful traders who keep the peace");
  - its size in words ("a great nation of many towns", "a small realm of three towns");
  - how it stands with the others ("at war with Varnholt, allied with the Reach");
  - one line of its past, the biggest thing in its written history ("lost its old capital in the long war").
- **"No nation":** "You start in the wild, a stranger to everyone."

### What a citizen starts with (the user: "yes, that's the right amount")

- **Home:** you start standing in your home town; it is in your journal as been to from the first moment, marked as home.
- **Standing:** "welcome" in every town of your nation from day one. Towns of a nation at war with yours start "wary".
  It is a starting point, not a shield: it still moves with what you do; steal or kill at home and you're wanted like
  anyone. As nations' wars and members change, it follows **(builder's call: a bonus to the town's opinion of you, read
  live from your nation's ties, rather than a one-time stamp)**.
- **People:** all strangers. The home town's people greet you as one of their own (a line when you arrive), but
  friendships are earned as usual.
- **Things:** nothing. Empty hands, like any new player.
- **Journal:** a line on the left page: "Citizen of Varnholt, from Oakhold", or "Of no nation". A Drifter world with no
  plan: no line.

### Who gets the menu, and changing it later (the user: "yes, the nation is a one-time pick")

- **A new planned world:** as above.
- **A world you already have, or a server you join:** the same menu the first time you enter with this version (you
  never had the chance to pick). You are unhurtable while it's open. You stay where you are: no move to a home town;
  picking a nation still gives you its standing and a home town in the journal ("from Oakhold" is where you're counted
  from, not where you stand) **(builder's call)**. On a server whose world is built, no bar shows.
- **A world with no nations** (the setting off, or an old world without a plan): only name and pronouns.
- **Later:** name and pronouns can be changed any time from the journal (a small "Change" on the left page opens the
  same menu without the nation list). The nation can't be changed from a menu.
- **Multiplayer:** every player picks for themself; each nation's home town is the same for everyone who picks it. A
  player who joins while another is still choosing doesn't hold the build up **(builder's call)**.

## What it knows (fairness)

- Drifters use the chosen name where they'd use the name over your head: a name is what you're called, not hidden
  knowledge **(builder's call)**. No Drifter learns anything else from the menu.
- The cards show what anyone from that land would know (its name, its ruler's nature, its wars), no numbers.
- Standing from citizenship is the town's own view of "one of ours", applied through the same opinion a town already
  keeps; nothing bypasses crimes, witnesses or word spreading.

## Constraints

- Client-only code in `src/civClient`; common code never imports `net.minecraft.client.*`.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`. Drifter itself must learn to say a player's chosen
  name and pronouns (its talk lines are in `src/main`): the API grows in the same change (a name and pronouns for a
  player, set by the addon, read by Drifter's talk) and docs/API.md says so. Without the addon, Drifter says the
  Minecraft name and "they", as today.
- Saved data versioned (each player's name, pronouns, nation, home town; each nation's home town in the plan).
- Old worlds and old saves load unchanged; a world mid-build when updated carries on.
- Works in single player and on a dedicated server (the plan must be ready before the first player is let in, without
  hanging a server that has no plan to make).
- Mojang names, Minecraft 1.21.1, no Loom or Gradle change. Stay out of the whim system and its files.
- The world's build time when you pick at once must be about what it is today (capped near two minutes).

## Left to the builder

- How the server waits for the plan inside vanilla's loading screen.
- Which lines use pronouns, and where in Drifter's code a player's name is said (commands and debug output keep the
  Minecraft name).
- The wording of characters, sizes, pasts; the menu's exact layout from vanilla widgets.
- What happens to the starting spawn point (bed-less respawn) of a citizen: the home town **(builder's call)**.

## Testing

- Dev client: a new planned world from the title screen to playing: the loading screen, the menu with real nations and
  the bar, Begin, arriving in the home town; screenshots of the menu and the journal's new line. "No nation". An old
  world's first join. Name and pronouns changed from the journal. Lines using the name and pronouns seen in the log.
- Headless: a dedicated server starts on a new seed (plan made before it's ready), restarts, and an old world loads.
- TESTING.md: plain steps for the user.

## Done

Built, tested in the dev client and headless, both jars installed in DRIFTER TEST with a new version number (next on
the main line), PROGRESS.md, TESTING.md, LIVED_WORLD.md and API.md updated, committed and pushed.

## Settled after the builder's check (all builder's calls unless marked)

Facts from the code that shape the above:

- The plan is made on a background thread after the server has started, 7 to 37 seconds (once 60), and hands work back
  to the server thread as it goes. So "planning inside vanilla's loading screen" means: in single player the server
  waits for the plan before it says it's ready, still running its queued tasks while it waits (no deadlock). On a
  dedicated server nothing waits at start (the watchdog must not trip, and nobody is watching a loading screen): the plan
  is made in the background as today, and a player who joins before it's ready sees the menu with "The nations are being
  drawn up..." in the list until it is.
- Today's spawn is on a road just outside the town nearest the middle of the plan, not in the wild. "No nation" keeps
  exactly that spawn ("as today"); its card says "You start on the road, a stranger to everyone."
- A town's opinion of a player exists only once the town is made. So citizenship is a live bonus worked out when a town
  judges you (welcome needs opinion over 20, wary under -5), never a stamp on towns.
- A nation's home town is saved with the plan (versioned); an old plan picks them at load. A nation with no ordinary town
  standing: its smallest living town that isn't the capital, else the capital.

Calls:

- **The wait.** While the menu is open: each nation's home town, nearest to the middle first, then what's in sight of the
  old spawn (for "No nation"). Begin can be pressed at any time. After Begin: the chosen home town and what's in sight of
  it must be built before play (as "in sight of spawn" is today), then more nearby until the same cap as today (about two
  minutes from entering the world, counted from when the menu opened, not from Begin). "No nation" never waits on home
  towns.
- **Which nations get a card.** The nations alive now: in a new world the planned ones; in a world already played, those
  that stand today (ones formed in play have no written past: no past line). No nations alive: name and pronouns only.
- **At war with yours:** any town of that nation is at war with any town of yours (a nation's towns share its wars).
- **The name.** Said as typed, never shortened to a nickname. It can't be another online player's Minecraft name or
  chosen name. Drifters' memories that already hold your old name as text keep it (a rename doesn't rewrite the past).
- **Where the name is said.** Drifter gets one place that answers "what is this player called" and "their pronouns"
  (fed by the addon through the API); the lines Drifters say or write about a player use it. Commands, debug output and
  logs keep the Minecraft name. Whim files are not touched: a whim line that names a player keeps the Minecraft name
  until the whim owner changes it (a follow-up, listed in PROGRESS.md).
- **Screens.** The menu is not replaced by the building screen while it's open (progress goes to its own bar); the
  journal key does nothing while either is up.
- **When the world changes under you (the user: "yes as you said").** You belong to the nation, not the town: if your
  home town is conquered, breaks away or falls, you are still a citizen of the nation you picked and still "from
  Oakhold". If the nation itself is gone, you are "Of no nation" and the journal says "once of Varnholt". Saved: the
  nation's id and name, the home town's id and name.
