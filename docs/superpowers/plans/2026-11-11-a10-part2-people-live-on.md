# A10 part 2: a built town's people live on while you're away: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline, the user's standing choice). No
> unit tests in this project: each task is proven on a headless server (`tools/smoke.py`) and in the dev client.

**Goal:** a built town nobody's near has a day every game day from saved data (deaths by the world's rule, leavers,
newcomers, votes, risings, a rare assassination), true the moment a player can see it; the player finds out in the town.

**Architecture:** the town keeps a ledger of what its paper days decided (`TownAway`, saved on `Town`). What can be
done on saved data is done at once (group membership, house ownership, leader, chronicle, the directory record). What
needs the Drifter itself waits for it to load: a death is handed to Drifter through the API (`DrifterApi.dieAway`),
which applies the world's own rule (respawn, lives, permanent) when the entity next loads, or at once if it's loaded.
Newcomers and graves wait in the ledger for the town's chunks. "Away" is part 1's `WorldPlan.watched`.

**Spec:** docs/superpowers/specs/2026-11-11-a10-part2-people-live-on-design.md

## Global constraints

- Addon → Drifter only through `com.dinglebob.drifter.api`; API 9, written in docs/API.md.
- Fairness (DESIGN.md §1): a newcomer has a starting kit only; nothing given; what the dead carried is lost, their
  house's chests stay.
- No message on the player's screen. Words, not numbers. Vanilla blocks only.
- Saved data versioned; numbers in named constants; stay out of whim files.
- Version 0.23.44.

## Review focus

1. A Drifter the ledger called dead is loaded (forceload, simulated Drifters) or loads later: never seen alive after.
2. A town watched again mid-day: nothing pending is lost or applied twice across a save and restart.
3. Lives mode: the last life; respawn mode: nobody is ever removed by a death, only by leaving.
4. A world from 0.23.43: `paperLost` already counted isn't taken out on the first day.
5. The leader dead or gone on paper: a vote among unloaded people picks someone; no revolt every day forever.

---

### Task 1: Drifter API 9, a death while away

**Files:** create `src/main/.../world/AwayDeaths.java`; modify `api/DrifterApi.java`, `world/CatchUp.java`,
`entity/DrifterEntity.java` (where catch-up starts after a reload), `world/DrifterDirectory` (the record's status),
`docs/API.md`.

**Produces:**
- `enum DrifterApi.AwayDeath { BACK, GONE, UNKNOWN }`
- `static AwayDeath dieAway(MinecraftServer server, UUID drifter, String cause)`: by the respawn setting and the
  record's deaths: BACK (it'll be at its bed with nothing, a death remembered) or GONE (for good: out of its group, its
  record GONE, the entity removed when it loads). Applied at once to a loaded entity. UNKNOWN: no such Drifter alive.
- `static boolean wouldComeBack(UUID drifter)`
- `static void awayDeathsDecidedBy(Predicate<DrifterEntity> whose)`: catch-up rolls no death of its own for these.
- `static String newName(MinecraftServer server)`: a name nobody in the directory has, as `spawn(level, feet, null)` picks.
- `AwayDeaths`: `SavedData` "drifter_away_deaths" v1, UUID → cause, gone, game time.

Check: `/drifter` debug `dieaway` on a loaded and an unloaded Drifter in each mode.

### Task 2: the ledger and the day (`TownAway`)

**Files:** create `src/civ/.../TownAway.java`; modify `Town.java` (fields + save/load), `Towns.java` (save version),
`TownFounding.java` (daily hook), `TownLife.java` (live check only while watched), `WorldPlan.java` (`paperLost` read).

**Produces:**
- `Town.away`: `long day`, `int lostApplied`, `List<Newcomer(name, plot, day, work)>`, `List<UUID> left`,
  `List<Fate(id, name, kind DIED|GONE|LEFT, day, cause)> fates` (kept 60 days), `List<Grave(name, day, cause, pos)>`.
- `static void day(MinecraftServer, Town, long day)`: once a game day for a spawned town not watched:
  1. losses: `place.paperLost - away.lostApplied` deaths, fighters first (`FIGHTER_SHARE`), never the leader;
  2. assassination (`ASSASSIN_CHANCE`, more in a revolt or a war): the leader dies and is no longer leader;
  3. a leaver (`LEAVE_CHANCE`, more when unhappy; never below `TownLife.FEWEST`);
  4. a newcomer (`ARRIVE_CHANCE` ≈ 1/15, more at peace and content; none at war or in revolt; an empty house needed).
- `static void arrive(MinecraftServer, Town)`: the town's chunks loaded: newcomers spawned in their houses, leavers
  loaded sent off, graves placed, stores brought up to date.
- First day on a world from before: `lostApplied = paperLost` (nothing owed).

### Task 3: votes and risings among people who aren't loaded

**Files:** `Politics.java` (`stand`, `vote`), `Unrest.java` (`today`, `revolt`).
Ambition and leanings from `DrifterApi.record(id)` (`traits`, `drifterTrust`) when the entity isn't loaded.

### Task 4: graves

**Files:** create `TownGraves.java`; modify `Chronicle.java` or `Diplomacy.java` death hook (`DrifterEvents.DIED`,
not coming back, of a town), `TownAway.arrive`.
A graveyard spot by the town's edge (free ground on its land, off roads and plots), saved; each grave a stone block
with a sign naming them and the day, placed when loaded.

### Task 5: finding out

**Files:** `town.json` lines (`town.lost`, `town.lost.friend`, `town.left`, `town.newcomer`, `town.assassinated`),
the town talk hook (`Politics.talk` or `TownReputation`), `Journal.java` (a friend's fate once the player is in a town
that knows), `Friends.java` (the fate, known or not), chronicle wording with names.

### Task 6: debug and tests

`/drifterciv away` (status), `away day [n]`, `away kill|leave|newcomer|assassinate`. Headless: a 900-day world, each
death mode; restart mid-ledger; a 0.23.43 save. Dev client: come back to a town after losses in permanent mode.

### Task 7: review, docs, version, install, push
