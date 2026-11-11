# The Start-of-World Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task
> (inline, this session; the user chose it for part 1). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entering a new planned world opens "Who are you?": a name, pronouns and a nation (or none), with the towns
building behind it; the choice sets where you start, how towns take you, what Drifters call you, and a journal line.

**Architecture:** The addon keeps each player's choice (`Citizens`, saved, versioned) and feeds the name and pronouns to
Drifter through one new API hook; Drifter's talk reads a player's name through one place (`PlayerNames`). The plan picks
a home town per nation; `WorldBuild` builds those while anyone is choosing and only finishes once everyone has begun.
In single player the server waits for the plan before its first tick, so the wait sits in vanilla's loading screen.
The menu is one client screen fed by one packet; the journal gets a line and a "Change" link.

**Tech Stack:** Fabric 1.21.1, Mojang names, Fabric networking payloads, `SavedData`, vanilla widgets and textures only.

**Spec:** `docs/superpowers/specs/2026-11-11-start-of-world-menu-design.md`

## Global Constraints

- No new art. Words, not numbers; no map or coordinates on a card.
- Client-only code in `src/civClient`; common code never imports `net.minecraft.client.*`.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`; API growth in docs/API.md. Without the addon,
  Drifter says the Minecraft name and "they".
- Saved data versioned; old worlds, old plans and a world mid-build load and carry on.
- A dedicated server never blocks at start. Stay out of the whim system and its files. No Loom or Gradle change.
- No code tests (project rule): `./gradlew build`, headless smoke, dev-client runs with screenshots.
- Version `0.23.42` (check the installed jar and side branches right before installing).

## Review Focus

- Two players: one still choosing while the other has begun; one joining mid-build; one leaving mid-menu (the build
  must not wait for someone who left).
- A world with no plan / no nations alive: name and pronouns only; nothing crashes with an empty card list.
- An old world's first join: menu over the world, held unhurt, not moved; released on Begin and on disconnect.
- A plan that fails or is switched off: single player must not hang in the loading screen.
- A nation whose home town fell or whose every town is gone; the chosen nation destroyed later ("once of").
- A name that is empty, too long, odd characters, or another player's: refused with a reason, never saved raw.

Each is checked in Task 5.

---

### Task 1: Drifter says the chosen name and pronouns (API 8)

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/identity/PlayerNames.java`
- Modify: `src/main/java/com/dinglebob/drifter/api/DrifterApi.java`, `docs/API.md`
- Modify: the sites in `src/main/java/com/dinglebob/drifter/{social,social/talk,encounter,trade}` and
  `brain/Hunches.java`, `brain/Pursuit.java`, `perception/Senses.java` where a Drifter says, thinks or remembers a
  player's name (`player.getGameProfile().getName()`); not logger lines, not `command/`, `debug/`, `compat/`,
  `identity/`, not whim files. Sites that match a name against typed chat keep the Minecraft name too.
- Modify: `src/main/resources/data/drifter/drifter_chat/*.json` only where a line talks about `{player}` with a
  hard-coded they/them/their: to placeholders.

**Interfaces (produces):**
- `PlayerNames.of(Player)`, `PlayerNames.of(UUID id, String minecraftName)`: the chosen name or the Minecraft name.
- `PlayerNames.address(Player)`: the chosen name as typed if one is set, else `Nicknames.of(minecraftName)`.
- `PlayerNames.pronouns(UUID)`: `"they"`, `"he"` or `"she"`; `PlayerNames.fill(String line, UUID player)`: fills
  `{they} {them} {their} {theirs} {themself} {theyre} {theyve}` (and capitalised `{They}` ...).
- `DrifterApi.PlayerCalled(String name, String pronouns)` (name empty: none chosen);
  `DrifterApi.playerCalled(Function<UUID, PlayerCalled>)`: the addon's answer; `API_VERSION = 8`.

- [ ] Step 1: write `PlayerNames`, the API hook, the docs row.
- [ ] Step 2: move the say/think/remember sites to `PlayerNames`; `Lines` fills the pronoun placeholders when its
  values include a player.
- [ ] Step 3: `./gradlew build` passes. Commit.

### Task 2: Citizens on the server

**Files:**
- Create: `src/civ/java/com/dinglebob/drifterciv/Citizens.java`
- Modify: `WorldPlan.java` (a home town per nation: picked with the plan, saved; old plans pick at load),
  `TownReputation.java` (the citizen's bonus in `opinion`, a greeting on arriving home), `Journal.java` (the line on the
  left page, home town been to and marked "home"), `DrifterCivilizations.java` (register; `/drifterciv who` debug).

**Interfaces (produces, in `Citizens`):**
- `record Card(UUID nation, String name, int colour, List<String> lines)`.
- `record Menu(boolean first, boolean building, boolean planReady, String name, String pronouns, List<Card> cards)`
  S2C `drifter_civ:who`; `record Choice(String name, String pronouns, Optional<UUID> nation)` C2S `drifter_civ:who_choice`;
  `record Refused(String why)` S2C `drifter_civ:who_refused`; `record Change()` C2S `drifter_civ:who_change`.
- `static boolean choosing(MinecraftServer)`: any online player who hasn't chosen (WorldBuild waits on it).
- `static boolean chosen(UUID)`, `@Nullable static UUID nation(UUID player)`, `@Nullable static UUID home(UUID player)`.
- `static double bonus(MinecraftServer, Town, UUID player)`: +30 in a town of the player's nation, -10 in a town at war
  with any town of it, else 0 (constants named).
- `static List<String> line(UUID player)`: the journal's "Citizen of X, from Y" / "Of no nation" / "once of X".
- Saved: `SavedData` "drifter_civ_citizens", `SAVE_VERSION = 1`: per player name, pronouns, nation id and name, home
  town id and name, chosen.
- `WorldPlan.home(UUID nation)`: the nation's home place, or null.

**Behaviour:** on join, a player who hasn't chosen gets `Menu(first = true)`; held and unhurt until they choose (when
`WorldBuild.building`, WorldBuild already holds them). `Choice`: name trimmed, 3 to 16 of letters, digits, `_`, space,
or empty; not another online player's Minecraft or chosen name (else `Refused`); pronouns one of three; nation alive
(else `Refused`). Then: saved; if the world is being built and a nation was picked, the player is put on solid ground
at the home town (as `WorldBuild.moveSpawn` finds it) and their respawn point set there; released if not building;
`DrifterApi.playerCalled` answers from the saved map. `Change`: `Menu(first = false)` with no cards; its `Choice` only
changes name and pronouns. Cards: character from the capital's ruler's nature, size from its living towns, wars and
alliances from `Diplomacy` between its towns and others' (planned towns: `History` ties), the past line from the
history's biggest event about it; nations formed in play have no past line.

- [ ] Step 1: home towns in the plan. Step 2: `Citizens`. Step 3: bonus, greeting, journal line.
- [ ] Step 4: `./gradlew build` passes. Commit.

### Task 3: The build waits on the menu; the plan inside the loading screen

**Files:** Modify `WorldBuild.java`, `WorldPlan.java` / `Towns.java` (where the plan starts), `TownSites.java`,
`Highways.java` only if needed.

**Behaviour:**
- While building: the home towns count as wanted places from the start (built whatever their distance), listed in the
  numbers. `finish` only when nobody online is choosing (as well as today's conditions); the stage then says
  "Waiting for you". The two-minute cap still counts from when the world was entered.
- `moveSpawn` leaves players with a home where they are.
- Single player (`!server.isDedicatedServer()`), new planned world: after the plan is started, the server thread waits
  until `WorldPlan.ready` or it failed, running the plan's own hand-backs meanwhile (the addon's hand-back helper
  queues to a list this wait drains; no reliance on vanilla's tick-gated task queue). A cap (120 s) then carry on as
  before. "Planning the nations" stays for the dedicated-server case.

- [ ] Step 1: write it. Step 2: `./gradlew build`. Commit.

### Task 4: The menu screen, and the journal's Change

**Files:** Create `src/civClient/java/com/dinglebob/drifterciv/client/WhoScreen.java`; modify
`DrifterCivilizationsClient.java`, `JournalScreen.java`.

**Behaviour:** `WhoScreen(Citizens.Menu)`: panorama and menu background as `WorldBuildScreen`; "Who are you?"; left: an
`EditBox` (name, 16), a `Button` cycling pronouns, the player drawn as in the journal; right: "Where are you from?" and
the cards (name in the nation's colour beside a banner item drawn with `renderItem`, its lines in grey, wrapped; the
chosen one outlined; scrolls), last "No nation"; "Begin" (or "Done" when changing); a refusal shown in red under the
name; the build bar and stage along the bottom when building (from `WorldBuild.Progress`; while this screen is up the
client does not replace it with `WorldBuildScreen`; after Begin it does). Esc: nothing when first; closes when
changing. The journal: "Change" under the name on the left page sends `Citizens.Change`. Debug: env
`DRIFTER_CIV_SCREEN=who@<ticks>:<nation index or none>` presses Begin for the harness after a screenshot.

- [ ] Step 1: write it. Step 2: `./gradlew build`. Commit.

### Task 5: Test, document, ship

- [ ] Step 1: dev client, a new world on a fresh seed from the harness: loading screen until the plan's ready (log
  order), the menu with real cards and the bar (screenshot), Begin with a nation: arrives in the home town, journal
  line (screenshot), standing welcome there. Again with "No nation". An old world's first join (copy of a test world).
  Change from the journal. A Drifter greeting by the chosen name in the log.
- [ ] Step 2: headless: new seed on a dedicated server (no block at start, plan in background), restart, old world.
- [ ] Step 3: fresh whole-branch review (most capable model) with the Review Focus list; fix Critical and Important.
- [ ] Step 4: docs: PROGRESS.md, TESTING.md, LIVED_WORLD.md, API.md; memory `rpg-profile-parts`.
- [ ] Step 5: `version=0.23.42`; build; fetch; installed jar and side-branch check; `tasklist`; install both jars if
  the game is closed; commit and push.
