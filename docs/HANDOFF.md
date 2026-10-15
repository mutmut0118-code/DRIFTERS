# Session 2 (local) update

- **Milestone 8 (2026-10-14):** steps 1-8 have code (home, hut, iron age with caving and looting, beds and village
  houses, farm and pen, diamonds, enchanting with obsidian and XP, houses by archetype). The user gave full permission
  to finish the milestone unattended ("i wanna be able to wake up and see mile 8 done"). Step 9 (the 10-day run) is
  what's left. Details in `docs/PROGRESS.md` "Milestone 8".
- **Testing tricks that work:** `sprint <ticks>` lines in `tools/smoke.py` scripts (5 game days in ~2 minutes); run
  regression batches in a copy of the project (a test server recompiles from source, so editing during a run leaks in;
  copy without `.git`, `build`, `.gradle/9.5.0`, `.gradle/configuration-cache`, `.gradle/loom-cache/launch.cfg`, lock
  files); random seeds per run; `/drifter thoughts log true` plus the death black box (last 10 s of movement written to
  the log on death) explain every death. Stop leftover servers by PID.
- **Running state when session 2 ended (2026-10-14 evening):** 10-day regression runs are `scratchpad/ten.sh <prefix>
  miner builder casual` (testbed copy, random seeds, the loaded area follows the Drifter); results print one line per
  run. The user watches their own game; their log is in the Modrinth profile's `logs/latest.log`. Never start a second
  test server while one runs (the scripts now refuse; one run was ruined that way).
- **Late on 2026-10-14 (user's second batch, all done, see PROGRESS "User requests, second batch"):** own tunnels
  aren't caves (`DrifterEntity.dugOut`), strip miners explore caves fully, long 2-15 minute mining trips with iron for
  the whole set, tidy Drifters take down pillar/bridge blocks round home (`DrifterEntity.scaffold`,
  `TidyScaffoldActivity`), no hops on farmland, `/drifter list` and `/drifter tp <name>`, fight back when stuck while
  fleeing. The user wants milestone 9 soon ("would love to get to mile 9 by tonight"); world simulation round
  Drifters (random ticks, spawning, chunk generation) is agreed as a later milestone, bonemeal later with whims.
- **Milestone 9 (started 2026-10-14 night, all six steps have code):** package `social/`: `Relationship(s)` (records,
  daily drift), `SocialEvent` (the section 14 table, exact numbers), `SocialWatcher` (notices events by perception;
  Fabric events for block breaks, chest use, fire, deaths; sleep blocking), `Mode` (behavior mode per player and the
  telegraph rule), `SocialGoals` (watch, warn, avoid), `HostileGoals` (territorial, predatory/stalk, vengeful/hunt),
  `FriendlyGoals` (greet, pay back/barter, follow me, help fight, creeper warning), `ChatLines`, `Manhunt`, `ItemValues`.
  The acceptance test (every event moves Trust as specified) passes headless with `/drifter social event` (scratchpad
  `social1.txt`). Everything else needs a real player: see TESTING "Milestone 9".
- **Lessons:** for a mob, `isImmobile()` also stops the AI step (a sleeping Drifter never woke); `Reach.canReach` can't
  aim at liquids (use a clip to the surface); placement on an empty cell needs a distance check, not `canReach`.

- **Milestone 7 started**: personality (`personality/*`), mood, the tic layer (`mannerism/Mannerisms`, body-part claims
  via `Task.freeParts`, camera glance override), all milestone 7 trait TODOs wired in, and night behaviour by
  personality (`Schedule.NightStyle`: stays in / hunts / digs in then mines, `NightTunnel`, `Goals.NightHunt`).
  Details in `docs/PROGRESS.md` "Milestone 7"; checks in `docs/TESTING.md` "Milestone 7".
- **Testing locally:** the user allowed Claude to test directly. `tools/smoke.py` runs a headless dev server with a
  command script (see CLAUDE.md "Local sessions"); the built jar goes into the Modrinth profile "DRIFTER TEST".
- **User rules from session 2:** torches are way markers in caves and at night (same side going in, so the way back is
  clear); recorded in DESIGN.md 10.3 and 18 with the night/mining-style notes.
- **Git:** the folder is now a checkout of `claude/gifted-archimedes-n6yen5` (user said yes); commits are pushed with
  plain `git push` (Git Credential Manager; the user signed in once in the browser). `gh` isn't installed.
- **Usernames:** done in session 2 (user said yes to design question 10).

# Handoff: cloud session 1 → local session

Written at the end of the first (cloud) session so a new session can carry on without re-reading the whole chat.
Read this after `CLAUDE.md`, then `docs/PROGRESS.md` (state, decisions, design questions), `docs/TESTING.md`, and
`docs/DESIGN.md` sections as needed. Those files are the real record; this one is the short version plus the things
that only lived in the conversation.

## Where things stand

- **Branch:** all work is on `claude/gifted-archimedes-n6yen5` (pushed). Nothing merged to `main` yet: ask the user
  whether to keep working on this branch or merge it first.
- **Build:** green (`./gradlew build`). The user tests with the jar from `build/libs/` (the one without `-sources`)
  or from the GitHub Actions artifact, in a Fabric 1.21.1 instance (Modrinth App) with Fabric API.
- **Milestones:** 0–4 done. **5 (brain, day one) and 6 (combat, reflexes) are in in-game testing** with many rounds of
  fixes; their "done when" tests (survive night one; survive three nights in a row) haven't been reported as passed.
  **The user wants to start milestone 7 next** (personality, mannerisms, mood) in the local session.
- **Performance:** the user ran about 40 Drifters at once and said it runs really well.

## Waiting on the user's test (pushed, untested in-game)

1. Swimming round 2: no accidental face-down sprint-swimming in water (`Steering.noSprintInWater`, called from
   `DrifterEntity.customServerAiStep`); swim arrival at floating height (`NavigateTask.arrived` / `swimLevel`).
2. Night: gets fully onto dry land away from water (`KnownBlocks.nearestDryGround`), and the **wall-in fallback**
   shelter when it can't dig down (`ShelterActivity` phase `BOX`, exits through a wall in the morning).
3. Sharper senses: sight range now **configurable, default 10 chunks** (`/drifter config sight <chunks>`, 2–16),
   faster awareness, creatures recognized by their sounds (`CreatureHearing` + `EntitySoundMixin`), far-sight
   landmarks (mountains, villages, exposed stone) feeding the stone-search hint.
4. Path range **configurable, default 64 blocks** (`/drifter config path <blocks>`, 24–160; search nodes scale with
   area). Saved in `config/drifter.json` (`DrifterConfig`).
5. `/drifter giveall <item> [count]` gives to every loaded Drifter (vanilla `@a`/`@p` and `/give` only take players;
   `/execute as @e[type=drifter:drifter] at @s run drifter ...` also works for any `/drifter` command, since those act
   on the nearest Drifter).
6. Cooking: cook goal, raw food only at food ≤ 6 or when it can't cook, smokers it knows.
7. Earlier rounds listed in `docs/PROGRESS.md` ("Changes after the first in-game test of milestones 5 and 6").

## How the user works (important)

- **Doesn't code.** Replies in plain language, step by step, exact commands, and a clear recommendation for every
  decision. Keep replies short; they read on the go.
- They test fast and send batches of observations ("testing rn ... thats all for testing"). Fix what they report,
  push, tell them exactly what to re-test. Update `docs/TESTING.md` items as "(re-test after fix)" / "(new)".
- They often give **design rules in passing** ("a player would ..."). Treat those as user decisions: apply them, and
  record them in `docs/PROGRESS.md` (and in `docs/DESIGN.md` when they change a rule; say so in the reply).
- They like things to feel like **a smart human player**, not a mob: common sense, quick reactions, taking sensible
  risks, not over-thinking (they complained it "stood still thinking" mid-fight).
- Earlier preferences: "use your recommendation" on most technical choices. Always ask before bumping Loom/Gradle or
  adding dependencies.

## User rules and notes collected in the chat (beyond DESIGN.md)

- Explore for caves/cliffs before mining; dig down only as a last resort (done: 3 explore legs first).
- Multitask while smelting: load the furnace, leave, come back (done: `FurnaceJob`, collect goal).
- Prefer cooked food; cook ahead of time; raw only when necessary (done).
- Run away immediately when fleeing, no standing still to plan (done: `RunAwayTask`).
- Walk straight lines at any angle, not grid zig-zags (done: string-pulling in `NavigateTask`).
- Travel: sprint-jump about 80% of trips (done, section 10.9).
- Must be "truly safe" at night (done: dry land + wall-in fallback).
- Sight like a player: far (configurable), quick to notice, knows creatures by sound, sees landmarks (done; DESIGN 5).
- **For milestone 7:**
  - Night behavior by personality (DESIGN section 18): brave/confident ones go out at night for mob drops, cautious
    ones stay in after dusk, hostile ones like the night for stalking; with beds, some sleep then do indoor chores or
    mine (time doesn't matter underground). Today every Drifter shelters.
  - **Mining style by personality** (user's note): some lean on strip/branch mining, others on caving and surface
    exploring, and the mix should differ per Drifter (DESIGN 10.3 already says methods depend on personality).
  - Habits already stubbed as constants waiting for traits: eating threshold (`Food.EAT_THRESHOLD`), tidiness
    (picking the crafting table back up, closing doors), Caution (shelter timing, surfacing margin underwater),
    Skill (camera, reaction delay, miss chances), fighting styles, tunnel shape.
- Later (not yet): Better Combat / Combat Roll optional integration (decided "later"); deliberate sprint-swimming,
  boats and other water skills (milestone 8, section 10.10); building its own smoker (milestone 8, with a home).

## Technical notes learned the hard way

- Mojang mappings. Read vanilla with `./gradlew genSources` and unzip the `-sources.jar` from
  `.gradle/loom-cache/minecraftMaven/net/minecraft/`. Mirror player logic from real source, not memory.
- Name clashes with vanilla: the brain getter is `getDrifterBrain()` (not `getBrain`), the swing flag is `ownSwing`.
  `Mob.targetSelector` is protected: use `MobAccessor`. Mixins are listed in `src/main/resources/drifter.mixins.json`.
- **Never read blocks in unloaded chunks** from perception (it loads/generates them): `Senses.loadedReach` clamps rays.
- Perception budget: beyond 32 blocks one sight line per entity, 16 far checks per re-rank, far tracked entities
  every third update; 2 far rays per tick; sounds go through a short list of loaded Drifters.
- Fairness rules (CLAUDE.md) are hard constraints: decisions read memory, not the world; the pathfinder only reads
  blocks the Drifter has seen; world interactions go through the fake player.
- Edits in the cloud were done with small Python replace scripts that assert the anchor exists exactly once: if an
  anchor doesn't match, nothing is written (check for that).
- Saved data is versioned (brain save version 1, memory entries by enum name: new `MemoryKind`s go at the end).
- **Local session:** the sandbox-only Maven mirror init script from `CLAUDE.md` is not needed locally. Still never run
  `runClient`/`runServer` unless the user asks; they launch the game themselves.
