# A10 Part 1: The World's History Never Stops: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task
> (inline, this session). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every nation and free town of the plan goes on living by the summary wherever no player is near, built towns
included: pacts, wars with outcomes (people lost on paper, a town taken), risings; news that travels as word would.

**Architecture:** `History.Ties` between two planned powers is the one record; relations between their built towns
mirror it. Each history day (`WorldPlan.present`): (1) built places take their nation from `Nations` (what happened in
play); (2) pairs the towns' own diplomacy owned last step are pulled into the ties; (3) `History.step` runs for every
unbuilt place and for every pair of powers no watched built town belongs to; (4) `Nations.follow` moves places, built
ones too, to the nation the summary says; (5) the ties of pairs the summary owns are pushed to the built towns'
relations. `Diplomacy.daily` rolls nothing for a pair the summary owns. "Watched": a player within a town's land plus
192 blocks.

**Tech Stack:** Fabric 1.21.1, Mojang names; the addon only (`src/civ`, one settings control in `src/civClient`).

**Spec:** `docs/superpowers/specs/2026-11-11-a10-history-never-stops-design.md`

## Global Constraints

- One authority per pair of powers at a time; a handover loses nothing. A town is in one nation.
- The summary kills no loaded Drifter, gives no items, moves no blocks; a watched town is never where a paper battle is
  fought; a built town is never burned (taken instead) and its leader never changed on paper.
- No on-screen message for far news (the morning summary leaves it out); the chronicle records all of it.
- Pace: Quiet 1x (the written past), Normal (default), Lively about twice Normal; the paper only.
- Saved data versioned; 0.23.42 worlds load and their nations start living again. Not-planned worlds: unchanged.
- No code tests (project rule): `./gradlew build`, headless runs skipping days, a dev-client run.
- Version `0.23.43` (check the installed jar and side branches right before installing).

## Review Focus

- A pair changing owner mid-war (a player walks in, walks out): the war is still on, on both records, with one end.
- A built town taken: out of its old nation (banner spots, member record, alliance pacts), in the new one once, at war
  with whom its new nation is; its old capital not dissolved by mistake. A capital taken: only by whole conquest.
- A nation conquered whole with built towns: its `Nation` gone, its towns in the winner's, citizens "once of".
- A nation formed in play, or a Drifter-founded town with no place in the plan: never touched by the summary, no crash.
- An old save: relations made in play since the summary froze are not overwritten by stale ties on the first step.
- Catch-up after a long `time add` or sleep: many history days in a row stay consistent and cheap.

Each is checked in Task 4.

---

### Task 1: The summary runs everywhere; one record with the towns

**Files:** Modify `History.java`, `WorldPlan.java` (`present`, `Place.paperLost` saved), `Nations.java` (`follow` for
built places, a nation dissolved), `Diplomacy.java` (`mirror`, the gate in `daily`, a reader of relations between two
sets of towns).

**Interfaces (produces):**
- `History.pace` (double, set from the setting before each step); `History.owned` (keys of pairs the towns own this
  step; `History.key(a, b)`); `History.nation(UUID)` package-private; `History.adopt(PlannedNation)`.
- `History.News(long day, String kind, String text, List<UUID> powers)`, `History.news` (last 30 days, saved).
- `WorldPlan.watched(Place)`; `WorldPlan.paper(Town, Town)`: the summary owns this pair of built towns today.
- `Diplomacy.mirror(server, Town x, Town y, double standing, boolean peace, boolean alliance, boolean war, boolean xStarted, long truceDays)`.
- `Place.paperLost` (int): people a built town lost on paper, for part 2.

- [ ] Step 1: History: skip rules, pace, frontier without watched towns, built towns taken not burned, no paper
  leader change for built towns, whole conquest of a nation with built towns at a third of the odds, news kept.
- [ ] Step 2: WorldPlan.present: the five-step day; pull and push; the first step after a start pulls every pair.
- [ ] Step 3: Nations.follow for built places; Diplomacy.mirror and the gate.
- [ ] Step 4: `./gradlew build`. Commit.

### Task 2: News that travels

**Files:** Modify `Chronicle.java` (morning summary without "News from afar"), `TownNews.java` (the gazette's "From
afar": only news that concerns the town: its own power's, a power tied to it by war or pact, or one seated within
2,600 blocks), `Nations.java`/`Diplomacy.java` only if a news code is missing for talk.

- [ ] Step 1: write it. Step 2: build. Commit.

### Task 3: The pace setting, and debug

**Files:** Modify `CivConfig.java` (`worldPace`: 0 quiet, 1 normal, 2 lively), `CivConfigScreen.java` (a button under
"The world"), `DrifterCivilizations.java` (`/drifterciv plan pace [quiet|normal|lively]`, `/drifterciv plan check`:
every contradiction between ties, relations and nations, or "consistent"; `/drifterciv plan ties`: each pair's state
and owner).

- [ ] Step 1: write it. Step 2: build. Commit.

### Task 4: Test, tune, document, ship

- [ ] Step 1: headless, a new planned world: wait for the plan and some towns made; `time add` 100 days at a time with
  waits; count the chronicle's "News from afar" by kind at each pace; tune the pace multipliers to the spec's numbers;
  `plan check` after each jump; a restart and `plan check`. The step's cost from the log (timed in `present`).
- [ ] Step 2: an old save (a 0.23.42 test world copy): loads, first step pulls, `plan check` consistent.
- [ ] Step 3: dev client: a town taken on paper far away, then walked to: its nation, its people's talk, the gazette.
- [ ] Step 4: fresh whole-branch review with the Review Focus list; fix Critical and Important.
- [ ] Step 5: docs (PROGRESS, TESTING, LIVED_WORLD A10 status, memory), `version=0.23.43`, build, installed-jar and
  side-branch check, install if the game is closed, commit, push.
