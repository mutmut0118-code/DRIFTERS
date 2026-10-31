# Drifter: progress

Tracks the build roadmap from `docs/DESIGN.md` section 25. Work one milestone at a time.

Status values: **Not started**, **In progress**, **Done** (done means its "done when" test passed in-game on the user's machine).

## Current state (session 2, local, 2026-10-15 afternoon)

- **Milestone 10 (offscreen and performance): all five steps have code**, waiting on the user's play. Hybrid
  simulation (3 busiest away from players keep running), detail tiers, `/drifter perf`, catch-up for the rest,
  offline progression setting, abstract travel. 10 Drifters together: 1 to 4 ms a tick (target 3 to 5), worst tick
  about 12 ms (was up to 112 before this milestone's speed-ups). Next: **milestone 11 (the Nether)**.
- Milestones 8 and 9 were called done by the user after playing.

## Earlier (session 2, 2026-10-15 morning)

- **Latest 10-day batch (21):** all three (Miner, Builder, Casual) reached stage 7 (diamond pickaxe) with no deaths;
  farms for all, houses for the Builder and Casual; no loop above 10 repeats. This meets milestone 8's "done when"
  three runs out of three; it waits on the user's play to be called done.
- Fixes since batch 17 (from the user's world and batches 18-20): berry bushes, night styles ("carries on through the
  night" when armed), daylight through windows, underground routes plan unseen cells as rock, desert staircases, the
  lava rules (no mining next to lava, sneaking past lava holes, backing off without stepping off edges), houses with
  unreachable corners, pillar shelter as a last resort, structure chest looting (priority), raiding by personality.

- **Milestone 9 (Disposition and PvP): all six steps have code.** The section 14 event table is verified headless
  (every event moves Trust and Fear exactly as specified); the behaviors need the user's play (TESTING "Milestone 9").
- **Milestone 8: code complete**, waiting on the user's play to be called done. Tonight's runs: houses, farms, full iron
  armor from long trips, diamonds; the deaths found were fixed (lava holes, waterfalls, currents, sealed water, baby
  zombies, fire).


- **Milestone 8: all nine steps have code** (user: "you have every right to complete the rest of your steps on your
  own"). Steps 1-5 are tested on sprinted 5-day runs; steps 6-8 (diamonds, enchanting, houses) are written and
  passed staged tests on a test server, but none of it has been played by the user yet. Step 9 (the 10-day
  unattended run) is the last check. See "Milestone 8" below; checks in `docs/TESTING.md`.
- **Big fix found on the way:** a Drifter that went to bed never woke up (its brain stopped while it slept); fixed.

- **Milestone 7 started** (user's request) while 5 and 6 are still in testing: personality, habits, mood, the
  mannerism (tic) layer, and night behaviour by personality are in. Smoke-tested on a headless dev server (also with
  Lithium and FerriteCore); the jar is installed in the user's Modrinth profile "DRIFTER TEST". See "Milestone 7".
- **Git:** this folder is now a checkout of branch `claude/gifted-archimedes-n6yen5` (remote
  `https://github.com/mutmut0118-code/DRIFTERS`); session 2's work is committed and pushed (the user signed in to
  GitHub in the browser for the first push).

## Before session 2 (end of session 1)

- **Moving to a local session.** See `docs/HANDOFF.md` for the short version of everything from this cloud session. Next: the user re-tests the latest fixes, then **milestone 7**.
- **Testing settings:** `/drifter config sight <chunks>` (default 10) and `/drifter config path <blocks>` (default 64; how far one path search reaches, search nodes grow with it), saved in `config/drifter.json`. `/drifter giveall <item> [count]` gives to every Drifter.

- **Build: green.** `./gradlew build` succeeds in the cloud (with the Maven Central mirror workaround described in `CLAUDE.md`).
- **Milestone 1 (Body): done.** The user tested it in-game (Modrinth App, jar from the GitHub Actions build) and reported that everything works.
- **Milestone 2 (Hands): the "done when" test passed in-game.** The user ran `/drifter script woodtostone` and reported it works perfectly. The rest of the milestone 2 checklist in `docs/TESTING.md` isn't fully tested yet; the user plans to finish it in a local session.
- **Milestone 3 (Senses and memory): done.** The user fully tested it in-game. One bug found (blocks behind glass) was fixed afterwards; that one item needs a quick re-check.
- **Milestone 4 (Pathfinder): done.** After six rounds of in-game fixes the user reported bridging across a whole ravine works, and everything else in its checklist worked earlier; the user moved on. (The "tunnel through a hill" item wasn't reported on separately: worth a quick look when convenient.)
- **Milestone 5 (Brain and day one): in testing.** Two rounds of in-game fixes so far (hunting with the right weapon and crits, sky through leaves, hill steps); the user is re-testing.
- **Milestone 6 (Combat and reflexes): first version written while the user tests milestone 5, not tested in-game yet.** See "Milestone 6" below. The milestone 2 checklist and the milestone 3 glass re-check are still open.
- The user tests by downloading the jar from the GitHub Actions run for the latest push (Artifacts zip, the jar without "sources") into a Fabric 1.21.1 instance with Fabric API, in the Modrinth App. The user doesn't code, so keep instructions plain and step by step.

## Milestones

| # | Milestone | Status | Notes |
| --- | --- | --- | --- |
| 0 | Project setup | Done | Cloud build green; the user ran the mod in-game. |
| 1 | Body | Done (in-game test passed) | Entity, player rendering with skins and poses, inventory, hunger model, virtual-key movement, camera model. Done when: you can drive it by command and it moves like a player. |
| 2 | Hands | **Done** (user, 2026-10-19: covered by everything played since) | Fake-player helper; mining, placing, crafting, smelting, eating, containers. Done when: a scripted sequence goes from punching wood to stone tools. |
| 3 | Senses and memory | Done (fully tested in-game; glass fix needs a re-check) | View cone, awareness meter, block sampling, hearing, memory tiers, debug overlay. Done when: the overlay shows exactly what it knows and nothing more. |
| 4 | Pathfinder | Done (in-game: ravine bridging, village, drops, doors) | A* with digging, bridging and pillaring. Done when: it crosses a ravine and tunnels through a hill to reach a target. |
| 5 | Brain and day one | **Done** (user, 2026-10-19: covered by everything played since) | Selector, planner, executor, the Stage 1 to 3 loop. Done when: a Fresh Drifter survives its first night unaided on most seeds. |
| 6 | Combat and reflexes | **Done** (user, 2026-10-19: covered by everything played since) | Mob targeting, per-mob protocols, reflex catalog; combat-mod test pass. Done when: it survives three nights in a row. |
| 7 | Mannerisms, personality and mood | **Done** (user, 2026-10-19: covered by everything played since) | Done when: people watching a short clip can't reliably tell it from a player. |
| 8 | Progression and building | **Done** (2026-10-15): 10-day runs reach diamonds with a house and farm (batch 21: three of three, no deaths); played by the user | Stages 4 to 8, shelter tiers 3 to 5, farming, animals. Done when: an unattended 10-day run reaches diamonds with a recognizable base. |
| 9 | Disposition and PvP | **Done** (2026-10-15): every section 14 event moves Trust as specified (headless table test); gifts, warnings, raids, looting and the manhunt (hunter) played by the user | Relationships, telegraph rule, hostile and friendly behaviors. Done when: every event in the section 14 table moves Trust as specified. |
| 10 | Offscreen and performance | **Done** in code (2026-10-15): 10 Drifters at 1 to 4 ms a tick, 100 at about 10 ms | Detail tiers, catch-up, abstract travel. Done when: 10 Drifters stay within the per-tick budget. |
| 11 | Nether | **Done** (2026-10-16): portal, packing, trips, safety, blaze rods, goods, piglins and bartering, storing the haul; polish left (hard fortresses over lava) | Section 19. |
| 12 | Groups, modpack factions, camps, config polish | **Done** (2026-10-16): Drifter bonds, groups, joint trips, team wars and raids, Mob Control, lived-in world, settings screen | Plan below |
| 13 | Whim system | In progress: engine, steps whims (docs/WHIMS_HOWTO.md), 141 whims (of about 900 ideas in docs/WHIMS.md), offscreen whims (milestone 20) | Whim data format, gates, cooldowns, archetype weights, then the whims in docs/WHIMS.md (vanilla first, modded behind mod detection). Can start any time after milestone 9. Swapped with The End (user, 2026-10-16); End whims stay off until 14. |
| 14 | The End (optional) | Not started | |
| 16 | Scale | **In progress** (2026-10-19): helper threads for paths and eyes, crowd perception, cheaper decisions, spread-out catch-up; see "Milestone 16" below | 1000 Drifters across a world and 300 round a player at a steady 20 TPS; a 150v150 battle playable (user, 2026-10-18). |
| 15 | House plans by the hundred | **Done** (user, 2026-10-19): 9 data-file styles, L and T wings, lofts, corner towers, A-frames, 300 checked plans per world; Drifters pick and build them on their own | A plan generator from parts: footprints (rectangle, L, T, a tower wing), one or two floors and lofts, roof styles (gable, hip, A-frame, flat with parapet, shed), wall styles (log frame with plank infill, stone base with wood above, cobble), windows, porches, chimneys, eaves; materials swapped to what grows nearby. Every plan has to pass the existing checks (buildable by player rules, nothing floating, a roofed room for the bed on the lowest floor, a door out) before it's used. Pictures of a sample for the user to veto styles. Generated once per world (seeded), alongside the hand-made ones. |
| 17 | Fixes: Combat Roll, rough ground, Better Combat animations | **Done in code** (2026-10-19), waiting on the user's play test | The user: "focus on the problems: combat roll, getting around on rough ground, some better combat animations dont work". |
| 18 | Player skills | **Done in code** (2026-10-19): trading, fishing, horses, boats, potions, netherite; waiting on the user's play test | Villager trading, fishing, horses, boats, potions, netherite (user, 2026-10-19, in that order). |
| 19 | Mod compat and modded whims | **In progress** (2026-10-19): Spartan Shields and Simply Bows tested; Guard Villagers, Farmer's Delight and Bountiful written, to check in the user's game | Farmer's Delight Refabricated, Simply Bows, Spartan Shields, Guard Villagers, Bountiful; more modded whims (user, 2026-10-19). |
| 21 | Encounters and bounties | **Done in code** (2026-10-20): all 26 kinds on the list (docs/ENCOUNTERS.md), the killing rules and revenge squads, Drifters doing Bountiful item bounties; tested in the client, waiting on the user's play test | Random events with temporary Drifters (GTA-style), bounty hunters and wanted Drifters, Bountiful compat (Drifters doing bounties). The brainstorm and the suggested first batch: docs/ENCOUNTERS.md. |
| 20 | Whims offscreen, then wrong guesses | **Done in code** (2026-10-20): whims offscreen (the user's pick A + small B + C + E), an easier way to add whims (steps whims, load checks, docs/WHIMS_HOWTO.md), four new wrong guesses; waiting on the user's play test | First think through ways to simulate or catch up whims offscreen (a proposal for the user); then expand the wrong-guesses system (the cave hunch, the theft assumption) (user, 2026-10-19). |
| 22 | Civilization (an addon mod) | **In progress** (2026-10-21): step 1 done (Drifter: Civilizations as a second jar in this project, Drifter's API, docs/API.md); groundwork in Drifter (docs/CIVILIZATION.md): the directory, news that travels, trading between Drifters, shared bases and bigger groups, roads and the 30-day run done; step 2 (the chronicle and "one island") done; towns next, after the user's review of the town plan. Was: planned (user, 2026-10-20: "save it as a mile after 21, in fact its really big i think it should be like an addon mod") | Towns, money and trade, leaders and laws, nations and wars, culture, a chronicle, a "one island" mode, inspired by the "1000 players simulate civilization" videos; its own mod needing Drifter, with a small Drifter API first. The plan and suggested order: docs/CIVILIZATION.md. Several milestones. |

## Milestone 0: Project setup

### Done
- Gradle project from the official `FabricMC/fabric-example-mod` template, branch `1.21.1`: Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API `0.116.17+1.21.1`, official Mojang mappings, split client/common source sets.
- Mod ID `drifter`, package `com.dinglebob.drifter`; entrypoints `Drifter` (common) and `client.DrifterClient` (client); mixin configs `drifter.mixins.json` and `drifter.client.mixins.json`.
- `CLAUDE.md`, this file, and `docs/TESTING.md`.
- GitHub Actions build workflow from the template (runs `./gradlew build` on push), set to JDK 21.
- Cloud build verified. The first attempt was blocked by the network policy (fixed by the user), then by Maven Central rate-limiting the sandbox IP (HTTP 429). That was worked around with a sandbox-only Google mirror the user approved (see `CLAUDE.md`).

### Deviations from the template (confirmed by the user)
- **Loom 1.17 instead of 1.18, Gradle 9.5.0 instead of 9.7.1.** The template's Loom 1.18 is compiled for Java 25, so Gradle would need a Java 25 JDK just to start. Loom 1.17 is the newest version that runs on Java 21; Gradle 9.5.0 is the version Loom 1.17 itself builds with.
- License in `fabric.mod.json` set to `All-Rights-Reserved` and no LICENSE file added (the template ships CC0).
- Mod version starts at `0.1.0`.

## Milestone 1: Body

All vanilla behavior below was mirrored from the decompiled 1.21.1 source (Player, LocalPlayer, ServerPlayer, FoodData, Inventory, PlayerRenderer), not from memory.

### Done (in-game test passed)
- **Entity** (`entity/DrifterEntity`, registered as `drifter:drifter`): a `PathfinderMob` with the player's attributes (`Player.createAttributes()`: 20 HP, speed 0.1, 4.5 block reach, 3.0 entity reach, sneak speed, etc.), player hitbox and eye height for every pose, player sounds (hurt by damage type, death, falls, swimming, `players` sound category). Never despawns. Can't be leashed. Position and rotation are sent to clients every 2 ticks, like a player. Spawn egg in the Spawn Eggs tab.
- **Vanilla mob controls are disabled** (move, look and jump controls are no-ops), so only the virtual keys and the camera model drive the body.
- **Rendering** (`client/render/DrifterRenderer`, `DrifterModelRenderer`): vanilla `PlayerModel` baked from the vanilla `ModelLayers.PLAYER` / `PLAYER_SLIM` layers (per the EMF advice in section 2), picked per Drifter. Player armor layers (wide and slim), items in both hands, arrows and bee stingers stuck in the body, heads/skulls, elytra, riptide effect. Player 0.9375 scale, crouch offset, swim tilt, and all player arm poses (bow draw, crossbow charge/hold, shield, trident, spyglass, horn, brush).
- **Skins** (`entity/SkinPool`): skin texture and arm type are picked once, synced to clients and saved. The pool is the 9 vanilla default skins in both arm types (18 skins), which every client already has.
- **Inventory** (`entity/DrifterInventory`): 36 slots (9 hotbar) + 4 armor + offhand, same numbering, stacking order and save format as the player inventory. Equipment (held item, armor, offhand) is read straight from the inventory, so armor gives armor points and renders. Hotbar switching takes a 1 to 3 tick human delay. Picks up any item type like a player does (same pickup box, pickup delay and item "target" rules). On death, the whole inventory drops scattered like a player's (Curse of Vanishing items are destroyed).
- **Hunger** (`entity/DrifterFoodData`): exact mirror of vanilla `FoodData` (exhaustion, saturation, regen at 18+, fast saturation regen at 20, starvation limits by difficulty), using the game's `FoodConstants`. Exhaustion from sprinting (0.1/block), swimming (0.01/block), jumping (0.05), sprint-jumping (0.2) and taking damage; walking and sneaking are free. Peaceful regeneration like a player.
- **Virtual-key movement** (`motor/VirtualKeys` + `DrifterEntity#applyMovementKeys`): forward/back/left/right/jump/sneak/sprint keys turned into input exactly like the client's `KeyboardInput` and `LocalPlayer`: diagonal strafing, sneak slows to the sneak-speed attribute, using an item slows to 20%, sprint needs 80% forward input and food above 6, sprint keeps going after the key is released until forward input stops, and stops on wall bumps and when leaving water. Player air control while jumping. Sneaking never walks off an edge taller than step height (vanilla edge back-off). Swimming follows the camera pitch. Player poses: standing, crouching, swimming/crawling (auto-crawl when there isn't room to stand).
- **Camera model** (`motor/CameraModel`): head yaw = camera yaw, like a player. Seeks targets (angles, a position, or an entity's eyes) with acceleration-limited turns, in three speed bands (idle 30-90, normal 180-360, combat 700-1000 degrees/s). Turns over 15 degrees at normal/combat speed overshoot by 1-5 degrees (scaled by Skill) and snap back. Idle jitter (random walk, up to 0.4 degrees) while settled.
- **Body rotation**: the player's body rule (body eases toward the movement direction; while still, it only turns when the head is more than 50 degrees off, or 15 while blocking), replacing the mob body controller.
- **Saving**: versioned (`DrifterSaveVersion` = 1): inventory, selected slot, food, skin and arm type.
- **Debug commands** (`command/DrifterCommands`, op level 2): `/drifter spawn|keys|look|lookat|slot|give|food|info|inventory|remove`. See `docs/TESTING.md`.

### Not done yet in milestone 1
- **EMF / Fresh Animations Player Extension check** (section 2): result not reported yet. Only matters if the user plays with EMF and Fresh Animations.
- Skill is a fixed placeholder (0.5) in the camera model until traits exist (milestone 7).
- Elytra glide body tilt and the sleeping pose are rendered by vanilla code paths but untested; they belong to later milestones (beds, elytra).
- Optional "veteran" cape (section 2): not built.

### Deliberately left for later milestones (stubbed or noted with TODOs)
- Attacking (and its exhaustion): milestone 6 (Combat). (Eating, breaking, placing and crafting were done in milestone 2.)
- Pickup filters and junk list (section 10.18): later (TODO in `DrifterEntity#tryPickUp`).
- XP orbs, XP on death, death messages, respawn and keepInventory: later milestones (TODO in `DrifterEntity#dropEquipment`).
- Hostile mobs targeting the Drifter: milestone 6.
- Username generator and always-visible nameplate (section 4): for now a Drifter only shows a nameplate if given a name (`/drifter spawn <name>` or a name tag).
- Natural spawning and despawning rules for fresh Drifters (section 4): later (TODO in `DrifterEntity#removeWhenFarAway`).

## Milestone 2: Hands

All vanilla behavior below was mirrored from the decompiled 1.21.1 source (MultiPlayerGameMode, ServerPlayerGameMode, Player, crafting menus), not from memory.

### Done (scripted test passed in-game; individual commands not all checked yet)
- **Executor** (`executor/Task`, `TaskRunner`, `executor/task/*`; DESIGN.md section 8 layer 4): tasks are small state machines that report running, success or failure, each with a timeout of about 3x its expected time. One top-level task per Drifter; a new one cancels the old. Results are reported in chat to whoever started them.
- **Fake-player helper** (`hands/FakePlayerBridge`): borrows a Fabric `FakePlayer` (one profile per Drifter) only for the moment of an interaction. It is put exactly where the Drifter stands, looking where it looks, crouching if it crouches, holding the Drifter's own item stacks; everything is copied back afterwards, including the food exhaustion the action cost.
- **Reach and line of sight** (`hands/Reach`, `executor/task/BlockAim`): every block action first turns the head with the camera model, then requires the crosshair (a player pick ray, 4.5 blocks) to actually be on that block or face. Nothing through walls, nothing out of reach.
- **Mine** (`MineTask`): break progress per tick from the player's break-speed formula (tool, Efficiency, Haste, Mining Fatigue, underwater, not on ground), the crack overlay shown to everyone, instant break when one tick is enough, a 5-tick pause after each full break, continuous arm swinging, and progress lost if the crosshair slips off. The block is broken by the fake player, so drops, tool damage, XP from ores, the 0.005 exhaustion and protection mods behave as for a player.
- **Place / use on block** (`PlaceBlockTask`, `UseOnBlockTask`): takes the block in hand, then right-clicks a neighbouring block's face it can see and reach. No floating blocks. Sneaks first when the clicked block would otherwise open or toggle (chests, tables, doors). The same task uses any held item on a block (hoe, bone meal, flint and steel).
- **Craft** (`hands/Crafting`, `CraftTask`): through the game's recipe manager (so modded recipes work). 2x2 recipes in its own inventory; 3x3 recipes need a crafting table in reach that it looks at first. Every crafting session starts with a 0.5 to 2 s pause standing still with the head tilted down; each craft after that is one click (3 ticks). Remaining items (empty buckets etc.) are returned; overflow is dropped like a full player inventory.
- **Containers** (`hands/Containers`, `ContainerTask`): opens chests, double chests, barrels, furnaces and other containers by really right-clicking them through the fake player (so protection mods can refuse, and a chest with a block on top won't open), animates the chest lid or barrel and plays the sounds, moves a stack per click (3 ticks), then closes. Ender chests are refused (section 3).
- **Smelting**: loads a furnace, smoker or blast furnace with input and just enough fuel, waits until it's done, and takes the results (`/drifter smelt`).
- **Eating and drinking** (`UseItemTask`, `DrifterEntity#eat`): held for the full use time (1.6 s for most food) by the Drifter itself, so the animation, particles, sounds and the slow-down while eating all show. Mirrors `Player#eat` (food, saturation, burp, bowls back from soups). Refuses when not hungry (except always-edible food like golden apples). Potions work too.
- **Use item in the air**: instant items (buckets, ender pearls, snowballs...) are used through the fake player.
- **Equip** (`SelectItemTask`, `WearArmorTask`): switches the hotbar to an item (moving it from the main inventory to the hotbar first, with an "inventory open" pause, if needed); puts on the best armor it carries; `/drifter swaphands` swaps main hand and offhand like the F key.
- **Wait** (`WaitTask`) and a **straight-line MoveTo stub** (`MoveToTask`): faces the target and walks with the virtual keys, sprinting over 10 blocks, jumping 1-block steps. No pathfinding (milestone 4); fails if stuck or if it ends up at the wrong height.
- **Pick up drops** (`CollectItemsTask`): walks over nearby dropped items.
- **Scripted test** (`debug/WoodToStoneScript`, `/drifter script woodtostone`): the milestone's "done when" sequence. Punches 4 logs, crafts planks, a crafting table (placed next to it), sticks and a wooden pickaxe, mines 8 stone, walks back to the table and crafts a stone pickaxe, axe, sword and shovel. It finds trees and stone with `debug/DebugScan`, which reads the world directly: **debug only**, clearly labelled, and never to be used by AI code (perception is milestone 3).
- **Commands** (`command/HandsCommands`): `/drifter mine|place|useon|use|eat|craft|container|smelt|wear|swaphands|walkto|collect|script|stop|task`. See `docs/TESTING.md`.

### Bugs found in in-game testing
- **No arm swing when mining (fixed).** Vanilla only advances the swing animation (`updateSwingTime`) for players and monsters, so the Drifter's arm never moved and the server only ever sent the first swing. `DrifterEntity#aiStep` now calls it on both sides, like `Monster` does. This also fixes the swing for placing, using items and opening containers.

### Implementation notes
- **Break speed is computed on the Drifter, not through the fake player.** DESIGN.md section 3 says to compute mining time "through the fake-player helper so it always matches the player exactly". The helper is never ticked, so its attributes and potion effects would be stale (Efficiency, Haste and Aqua Affinity live on the Drifter). So `DrifterEntity#getDestroySpeed` is a line-for-line copy of `Player#getDestroySpeed` using the Drifter's own live attributes and effects. Same numbers, which is the design's intent. See design question 4.
- Taking items out of a furnace through a container doesn't pop the smelting XP; the furnace keeps it until something takes the output through a real menu. XP for Drifters comes later.

### Not done yet / left for later (TODOs in code)
- In-game verification of everything above (the milestone's "done when" test).
- Player-placed block protection (section 11: never break blocks players placed, unless hostile and griefing is on) needs the per-chunk player-placed block log, which comes with memory/building. Until then, the debug commands can break anything you point them at.
- The owned-blocks log (section 6) for blocks the Drifter places: milestone 3 (memory).
- "Won't craft while a threat is tracked" (section 10.2) needs perception (milestone 3).
- Recipe knowledge limited by the Knowledge trait (section 8): milestones 5/7.
- Bows, crossbows, shields, tridents (hold-and-release items): milestone 6.
- Milk buckets and honey bottles: vanilla only returns the empty bucket/bottle to real players, so they're not used yet.
- Trapped chests don't emit redstone while the Drifter has them open.
- The config option "can break and place blocks" (section 23) comes with the config system.
- Body-part claims for tasks (head, hands, legs; section 8) come with mannerisms (milestone 7).

### Next steps
1. Finish the rest of the milestone 2 checklist in `docs/TESTING.md` (the script part already passed), in a local session with the user.
2. Fix whatever that finds; then mark milestone 2 done.
3. ~~Start milestone 3~~ (started at the user's request before step 1 was finished).

## Milestone 3: Senses and memory

### Done (fully tested in-game)
- **Memory** (`memory/*`, DESIGN.md section 6): short-term and long-term tiers (working memory lives in the senses and the executor and isn't saved). Each entry has kind, position and dimension, recorded and last-confirmed time, confidence, source (seen, heard, inferred, told) and importance. Confidence fades by kind (mobs in seconds, items in minutes, player positions in minutes with identity kept forever, ores over days, stations never). Per-kind capacity limits drop the lowest importance x confidence first. Entries merge per position (trees and danger per 8-block patch, structure clues per 4). Saved with the Drifter, versioned.
- **Consolidation at dusk** (by the Drifter's own sense of time, once per day): important short-term memories are promoted (a bad hit becomes a long-term danger zone); the rest of yesterday's short-term memory is dropped.
- **Mental map** (`MentalMap`): one tile per chunk it has looked into, with biome and whether it saw water or lava. Up to 4096 tiles.
- **Thought log** (`ThoughtLog`, section 25): the last 64 things it noticed, in plain words.
- **Vision** (`perception/Senses`, section 5): a view cone of 110 x 70 degrees around its head, plus a peripheral zone out to 180 degrees that only notices movement. Sight range 48 blocks in full light, about 8 in darkness (unless the target is on fire, glowing or holding a light), shorter in rain, thunder, underwater, with Blindness or Darkness; Night Vision lights everything up. Line of sight is a ray to the target's head and one to its feet (glass is see-through).
- **Awareness meter** (`Awareness`): 0 to 1 per entity, filling faster when the target is close, moving, fully visible and near the center of view; sneaking targets fill much slower; invisible ones only through their armor. Thresholds: glimpse 0.3, suspicious 0.6, detected 1.0 (only then does it know what or who it is). Drains when out of sight.
- **Attention budget**: at most 8 entities tracked in detail, re-ranked every half second by threat and salience; players, anything that hit it, and a creeper whose fuse it heard are always tracked.
- **Seeing players** (section 5): name, held item, armor per slot, enchantment glint, sneaking, sprinting, blocking, using an item, just got hurt. Never health or inventory.
- **Seeing blocks**: 6 random rays per tick across the view cone (12 when a player is near and in view), up to 32 blocks, less in the dark. Ores, chests, barrels, crafting tables, furnaces, beds and other stations, lava, spawners, portals, bookshelves, mossy cobblestone, trees, sugar cane and clay are remembered. Every block it looks at marks its chunk explored.
- **Mining reveals** the six neighbours of every block it breaks (the only way it learns what's inside rock). Blocks it places are remembered. Opening a chest or barrel remembers a summary of the contents.
- **Verification**: remembered blocks in view are re-checked; still there refreshes the memory, a clear view of an empty spot or a different block means it forgets it ("the crafting table at ... is gone").
- **Hearing** (`Hearing`, section 5): through the game's vibration events (what sculk sensors hear), within each sound kind's radius: movement 16, building/mining 16, containers and doors 16, eating 8, projectiles 16, something hurt 16, fuses 12. Sneaking makes no step sounds; wool blocks sound. Explosions carry 64 (a small mixin, since vanilla game events stop at 16). It only learns the kind of sound, a rough position (off by up to 15% of the distance) and roughly how far, never who. It ignores its own sounds, including what its fake-player helper does.
- **Touch**: it knows which way a hit came from and what kind of thing it was, and remembers getting hurt there.
- **Time and weather sense** (`TimeSense`): exact time and weather while it can see the sky (or carries a clock); underground its own estimate drifts by up to 10% per in-game hour.
- **Glances**: when idle, its head turns toward something it glimpsed or heard (section 5 awareness table).
- **Debug overlay** (`debug/overlay/*`, `client/debug/DebugOverlayRenderer`; section 25): `/drifter debug on` (operators). Draws, from data the server sends, only what each Drifter within 64 blocks perceives and remembers: view cone, block rays, tracked entities with awareness, remembered blocks coloured by kind and fading with confidence, heard sounds, and a text panel (task, awareness of you, believed time, memory counts, last thoughts).
- **Commands**: `/drifter debug on|off`, `/drifter memory [clear]`, `/drifter thoughts`, `/drifter senses`.
- **Memory now used by the hands**: `/drifter craft` finds a crafting table from memory (it must have seen or placed one in reach), not by scanning the world.

### Bugs found in in-game testing
- **Blocks behind glass were never re-checked (fixed).** Block rays stopped at glass, so after a block behind glass was removed the Drifter kept believing it was there until the glass was removed too. Block sampling and re-checking now look through see-through blocks (vanilla's rule: a solid body but no visual shape, i.e. glass and glass panes). Seeing entities already looked through glass. Needs a quick re-check in-game.

### Not done yet / left for later (TODOs in code)
- "Pauses current action briefly" on a glimpse, investigating sounds, and interrupting tasks: the brain decides that (milestone 5). For now it only glances when idle.
- Mob ambient sounds (groans, rattles) don't produce game events; hearing them needs a sound hook.
- Shared memory between Drifters ("told") comes with groups (milestone 12); world-level registry for identity, relationships and home comes with disposition/progression milestones.
- Death location memory: Drifters don't respawn yet, so memory is lost with the entity on death.
- Known roads and "mined out" tiles in the mental map: milestone 4 (pathfinder).
- Detail tiers (fewer rays when nobody is near): milestone 10.
- The wood-to-stone debug script still finds trees and stone with the debug world scan; it could be switched to memory once the brain exists.

### Next steps
1. User re-checks the glass fix and finishes the milestone 2 checklist.
2. Fix what that finds; mark milestone 2 done.
3. ~~Milestone 4~~ (started).

## Milestone 4: Pathfinder

### Done (code complete, untested in-game)
- **Seen-terrain knowledge** (`perception/TerrainKnowledge`): the pathfinder must not know about caves or lava it never saw (section 1). Every block an eye ray passes through or stops on is marked as seen, mining marks the neighbours, and it always knows the blocks right around its body. Up to 256 chunk sections, least recently used forgotten first; not saved (it refills within seconds).
- **Terrain view** (`nav/NavView`): seen blocks are read as they really are; unseen blocks are UNKNOWN and "assumed passable at a moderate cost" (section 12). Hard bans: never through lava, fire, cactus or magma; never digging a block next to lava it has seen; never digging containers or blocks players placed.
- **Player-placed blocks** (`world/PlayerPlacedBlocks`, `mixin/BlockItemMixin`): every block a real player places is recorded in a small per-chunk list saved with the chunk (Fabric data attachment), as section 11 asks. Drifters' own placements (through the fake player) aren't recorded.
- **A* search** (`nav/PathSearch`, section 12): moves are walk, diagonal (no corner cutting, no digging), jump up 1, step down 1 to 3 (one block down can be dug as a stair), drop up to 8 into water, bridge (place a block under the next step), pillar up (jump and place underneath), dig through, climb ladders/vines/scaffolding, swim, and open wooden doors and fence gates. Cost = seconds of player time + risk x Caution + resources x Thrift: dig time with the best tool it carries, extra risk for digging under gravel/sand or next to water, danger zones from memory, unknown cells. Its own trails (blocks it has walked, remembered and saved) are 20% cheaper. A 48-block window around the start; a farther target gets a partial path toward it and it plans again from there. Up to 8000 nodes per search, and a shared budget of 3000 nodes per tick for all Drifters (searches resume next tick).
- **Path following** (`executor/task/NavigateTask`): walks the path with the virtual keys, looking 3 steps ahead (not at its feet), strafing diagonally on turns, cutting corners a little on straight runs, sprinting on long straights (4+ steps, 6+ blocks left, food above 6), sneaking while bridging, auto-jumping unexpected 1-block bumps. Digs with the best tool (switching to it first), bridges by sneaking to the edge and placing against the side of the block it stands on, pillars by looking straight down, jumping and placing underneath, opens doors by right-clicking them.
- **Replanning**: when reality disagrees (an unseen cell turns out solid: it digs it if it can, otherwise replans; no ground where it assumed some: it looks first, then replans), every 5 s while the path still crosses unseen ground, when a partial path runs out, and when stuck (3 s: jump and strafe; 8 s: replan; gives up after 15 replans).
- **Chasing**: `/drifter follow me` re-paths every half second toward where you'll be in half a second, and waits near you when it catches up.
- **Overlay**: the current path drawn from its feet, coloured by move (white walk, light blue up/down, blue bridge, cyan pillar, green climb, aqua swim), red boxes on blocks it will dig, and a "Path:" line in its text panel.
- The wood-to-stone script and drop collection now walk with the pathfinder instead of the straight-line stub.
- **Commands**: `/drifter goto <pos>`, `/drifter goto me`, `/drifter follow me` (`/drifter walkto` is still the old straight-line walk).

### Bugs found in in-game testing (all fixed, need re-testing)
- **Bridging never happened.** It aimed at the middle of the side of the block it stood on; from the edge, that ray hits the block's top corner first, so it never "saw" the side face. Aiming now tries several points across a face (`Reach.aimPointFrom`), including the lower part a player looks at when bridging.
- **It walked off a ravine and died.** It didn't sneak near drops, so momentum carried it over; the stuck-recovery "strafe and jump" could fire at an edge; and the planner assumed solid ground at the bottom of 2-3 block drops it couldn't see. Now: it sneaks (and never sprints) whenever a 4+ block drop is right beside it or its next step; it never does the strafe-jump next to a drop (it replans instead); it never drops 2+ blocks onto ground it hasn't seen; and walking right along a deep edge costs extra (section 12 edge risk).
- **It took the most destructive path in a village** (broke carpet, a house log, and a window instead of using the door). Causes: when it met a block it hadn't seen while planning, it dug through it instead of replanning; digging was costed only by break time (a window takes 1.5 s); and carpet counted as a solid block. Now: it only digs blocks the plan chose to dig, and replans when anything else is in the way; every block dug costs 2 s extra, and blocks that plainly belong to a build (glass, planks, wool, doors, stairs, slabs, fences, walls, bricks, beds...) cost 12 s extra, so it goes around or uses the door; carpet and thin snow are walked on. Standing on slabs and dirt paths is handled better too.

### Travel pace (user feedback, added after the first test)
- `NavigateTask.Pace`: **travel** (default: sprints whenever it can, and sprint-jumps on 80% of trips, rolled per trip), **stroll** (walks), **sneak** (sneaks all the way). `/drifter goto <pos> [travel|stroll|sneak]`. The brain will pick the pace later (strolling in a village, sneaking up on something).
- Sprint-jumps (holding jump) while the next 5 steps are fit for it: gentle bends only, no door or 2-block doorway, room above the head, no deep drop beside, not in the last 5 blocks. Stops sprinting near deep drops (sneaks), when bridging, in water, in the last 3 blocks, and at food 6 or lower.
- Sculk sensors and shriekers are now remembered (as danger "sculk"); within 12 blocks of one it remembers, it sneaks and never sprints (ancient cities, section 15).
- Path progress now counts points it sails past in mid-air on a straight run, so sprint-jumping doesn't make it turn back.

### Bugs found in the second test (fixed, need re-testing)
- **Bridging still didn't work.** The aiming fix was right, but the planner almost never chose to bridge. Two reasons: (1) it only planned a bridge from ground it had *seen*, and the near edge of the ravine was often still unseen from where it started; (2) "step down one block into the ravine, onto ground I can't see" was always cheaper than bridging, so it planned to walk across the bottom of an imagined 1-deep ditch. Now unseen ground counts as footing for a bridge or pillar (it checks when it gets there), and it never steps down into a hole it can see is open air unless it has seen the bottom. Before placing the first block it looks down into the gap: if it's only one block deep, it steps down instead. While leaning out over the edge it now looks at where the block will go (it used to turn round once it leaned past the edge).
- **Sprint-jumping looked like random taps of the space bar.** It jumped only when an exact straight line of 3 identical steps lay ahead, checked every tick, so A* paths (mixes of straight and diagonal steps) rarely qualified. Now it decides on the ground whether the next 5 steps are fit for it (gentle bends up to 45 degrees are fine, single steps up and down too) and then **holds** jump, so it jumps again the moment it lands. Points it sails past in mid-air count on gentle bends too.
- **It got stuck sprint-jumping into doors and 2-block doorways.** Now it stops sprint-jumping when a door, gate or trapdoor, or any spot without room above the head for a jump (a 2-high doorway or tunnel), is within the next 5 steps, or a deep drop is beside the way. The automatic "jump over a bump" now only fires for a real 1-block bump with room to jump, not when it rubs a door frame (that made it bonk its head over and over).

### Bugs found in the third test (fixed, need re-testing)
- **Bridging: path was blue, it looked down, then fell in.** It took the cobblestone out of its inventory only once it was at the edge. Taking an item from the inventory opens it, which lets go of every key, sneak included, while it was still sliding, so it walked off. Now it takes a building block out a few steps before a bridge or pillar, on safe ground; if it still has to do it at the edge, it comes to a full stop first.
- **It sprinted and jumped while sneaking to bridge.** As for a player, sprinting carries on while sneaking until forward input stops. Now when it starts sneaking while sprinting it lets go of the movement keys for a moment, like a player does; and it never jumps near an edge or on a bridge (a jump lifts it off the ground, and sneaking only holds it on the ground).
- **It "teabagged" at doors instead of opening them.** Opening a door used the place-a-block click, which sneaks first; sneak-clicking a door with an item in hand uses the item instead of the door, so it failed and tried again. Doors and gates are now opened standing up (`UseOnBlockTask.useBlock`).
- **No sprint-jumping in tight spaces** (user's rule): not with a ceiling within 4 blocks of the floor (houses, most caves) or with walls at head height on two or more sides (corridors).
- **Too safe with drops** (user's rule): it now takes drops onto ground it has seen that cost up to one heart of fall damage (4-5 blocks for a player, computed with the player's own safe-fall-distance and fall-damage attributes), as long as it keeps 3 hearts; the damage is part of the route cost. Before, it only took drops of up to 3 blocks and often re-routed or got stuck.

### Bugs found in the fourth test (fixed, need re-testing)
- **Bridging: it looked down at the gap, then walked off and died.** Every replan let go of every key, sneak included. While the far side of a ravine is unseen it replans every 5 s ("rolling replan"), so this often happened while it was creeping out over the edge; with sneak released, even that slow creep carried it over (sneaking stops it at the edge but doesn't take away its speed, same as for a player). Now letting go of keys never releases sneak while a deep drop is beside or under it, during planning too, and finishing a bridge step no longer releases sneak for a tick.
- **It stopped before every step down a hill or off a block.** Three causes: (1) every surprise ("no ground where it expected") made it stop, look, and replan standing still; (2) every replan stopped it; (3) a step up or down had to be finished standing on its point before the next one started. Now it glances down while carrying on (sneaking, so a deep drop still stops it at the edge), replans on the move (keeps its keys for up to half a second while it thinks, then picks the new path up at the nearest step), and runs straight through steps up and down like through flat ones. The planner also accepts a step down into a hole whose bottom it hasn't seen when it has seen ground right next to it at that level (a hillside), so it no longer plans bridges down hills; a ravine still never counts.

### Bugs found in the fifth test (fixed, need re-testing)
- **Bridging: it reached the edge but never placed a block ("couldn't reach the edge to bridge", then "replanned too often").** Checked with a small ray simulation: aiming used a point 0.1 blocks *inside* the face. From the steep angle of leaning over an edge, the line toward that point passes over the block's top corner, so it only "saw" the side face after leaning about 0.2 blocks out, while sneaking lets it lean at most 0.3 (in practice often less). Aiming now uses a point right on the surface (0.01 inside), which needs only 0.03. Also: plans made while leaning out over the edge started from the air over the gap (its middle is there), which made every later plan wrong; it now plans from the block it really stands on. No rolling replans in the middle of a bridge or pillar. If the edge check still fails, the message now says how far past the edge its eye got.
- **It fell straight down from drops instead of keeping going.** Once past a step's point it steered back to stand on it while falling. Now, once past it, it heads for the next point (if the path carries on roughly the same way), so it keeps its forward motion in the air like a player.

### Sixth test: first bridge block placed (fixed, need re-testing)
- **It placed one block, by accident.** User's diagnosis: players bridge facing *away* from the gap, sneaking backward. Facing the gap, once it leans out past the edge the side of the block it stands on is behind its eyes, so aiming meant spinning round; the one block it placed was when it happened to face backward. Now it **back-bridges**: after one look into the gap, it turns its back to it, looks steeply down (82 degrees), sneaks backward until the side face comes into view below it, places the block, shuffles backward onto it, and repeats, never turning round mid-bridge. It turns to face forward again only for the step off the bridge.

### Not done yet / left for later (TODOs in code)
- In-game verification (the milestone's "done when" test: it crosses a ravine and tunnels through a hill to reach a target).
- Long-range planning over mental-map tiles (section 12 step 1) is simplified to "partial path toward the target, then plan again".
- Parkour jumps, boats and horses: later (boats and horses with the progression milestone).
- Stairs and other partial blocks as floors aren't modelled well yet (slabs and dirt paths are better since the test fixes): paths across them may replan.
- Tidy Drifters closing doors behind them, and closing their own door at night: milestone 7 (habits) / 8 (base).
- Detail tiers (coarser pathing far from players): milestone 10.
- Using a hostile player's blocks (griefing config): milestone 9.

### Next steps
1. User tests milestone 4 (`docs/TESTING.md`, "Milestone 4"), plus the milestone 2 checklist and the milestone 3 glass re-check.
2. Fix what the tests find.
3. Milestone 5 (Brain and day one): selector, planner, executor, the Stage 1 to 3 loop.


## Milestone 5: Brain and day one

The "done when" test: a Fresh Drifter (empty inventory) survives its first night unaided on most seeds.

### Done (code complete, not yet tested in-game)
- **Brain** (`brain/Brain`, sections 7 and 8, the selector layer): on by default for every Drifter. Re-scores its goals once a second and at once when an activity ends. Score = need urgency x feasibility x risk, plus a commitment bonus (0.15) for the current goal and a little jitter. With nothing running it keeps its current goal during the goal's minimum commit time, otherwise picks among the top three by weighted chance (sharpness 4, a stand-in for the Efficiency trait). A running activity is only replaced at a safe checkpoint (between steps, or while walking or waiting; never mid-block, mid-craft or mid-bite), by an urgent goal at once or by a clearly better one after the commit time. A failed activity makes its goal score lower for 15 s. Every decision goes to the thought log ("picked get a stone pickaxe 0.65 over get food 0.30, explore 0.15").
- **Manual commands take over**: any task command (`goto`, `mine`, `script`...) switches the brain off; `/drifter stop` switches it off too; `/drifter brain on` resumes.
- **Needs** (`brain/Needs`, section 7 curves): hunger (flat to 14, steep below 6), health (squared), shelter (by its own sense of time: rising from 10500, urgent from 11500, all night; "climb out" in the morning), gear (while early targets remain), food supply (carried hunger points vs 24, weighted by stage), curiosity (small constant).
- **Goals** (`brain/Goals`): eat, get food (hunt and cook; raw meat if cooking would take too long while getting hungry), shelter for the night, progress (the strategist's next target), explore, idle.
- **Strategist** (`brain/Strategist`, section 9): stage computed from what it owns (0 arrival, 1 wood, 2 stone, 3 first night); targets in order: wooden pickaxe, stone pickaxe, stone sword, stone axe, furnace, 16 cobblestone, 8 torches, stone shovel.
- **Planner** (`brain/Planner`, section 8): backward-chains from the wanted item through the game's own crafting and smelting recipes and a curated source table (`data/drifter/drifter_sources/early_game.json`: which blocks and animals give which items, and the cheapest tool needed), choosing the cheapest alternative by a rough cost in seconds. Uses what it carries first, places a crafting table or furnace when none is known within 24 blocks, picks fuel (coal, charcoal, planks, logs), merges repeated steps (one trip for all the logs). Unknown sources become "explore" steps (toward remembered trees when it has some); stone it hasn't seen becomes "dig down for stone". Only the first step runs; it plans again after each.
- **Knowledge only through perception**: decisions use `brain/KnownBlocks` / `brain/KnownWorld`, which only consider blocks it has seen (the pathfinder's seen-terrain map), animals it has seen recently, and stations it remembers. The milestone 2 test scanner (`debug/DebugScan`) is no longer used by the AI.
- **Activities** (`brain/activity`, section 10): gather (nearest seen blocks, lowest first, pick up drops; a 2-high staircase down to find stone that stops at caves, fluids and falling blocks), craft (2x2 or at a known table, walking back to it), place a station, smelt (load input and just enough fuel, wait, take out), hunt (chase an adult animal it has seen, clumsy close-in steering, full-strength hits only), eat (food that fills without waste, cooked before raw, bad food only when desperate), shelter (tier 1: dig 3 down, cover the hole, wait for morning by its time sense, dig out and climb up), explore (look around 360, walk 20-32 blocks toward unexplored ground or remembered trees, stop as soon as it sees what it was looking for), idle (look around).
- **Melee** (`hands/Combat`, `executor/task/AttackTask`): only what's under the crosshair within 3 blocks, no hits through walls, the player attack cooldown from its weapon's attack speed, and a real player attack through the fake player (with the held weapon's damage and speed applied, sprint hits, crits).
- **Failure handling** (section 8): failed targets (unreachable trees, blocks) go on a 3-minute blacklist; activities give up after 6 failed steps.
- **Overlay and commands**: the overlay's text panel shows brain on/off, stage, next target, the goal and current step, the top scores, the plan, and the needs. `/drifter brain`, `/drifter brain on|off`, `/drifter brain goal <eat|food|shelter|progress|explore|idle>`.
- Saved data: the brain's on/off switch ("DrifterBrain", version 1).

### Fixed after a code review (before any in-game test)
- Night shelter: it now steps to the middle of its block before digging (off-centre, its 0.6-wide body stayed on the rim of its own hole); gets to dry ground first if it's swimming or climbing at dusk; gives up cleanly if it can't get its feet on the ground.
- Its own sword sweep no longer hurts or knocks back the Drifter itself (the fake-player helper stands where it stands).
- Exploring for trees or animals stops by the same rules the planner uses (no endless "explore, found it, explore" loop on out-of-reach logs or baby animals), and stops on seeing any kind of tree or huntable animal; remembered groves it's standing in no longer count as somewhere to go.
- The brain no longer restarts a goal that just failed during its commit time (the 15 s cooldown now works); a goal with nothing to do can't be re-picked by jitter.
- A failed craft or smelt is reported as a failure (it was reported as done and retried forever).
- The dig-down staircase gives up when boxed in (water, lava, sand, someone's blocks on all four sides) instead of turning forever; it never digs its own crafting table, furnace or chests; stone is gathered at its own level, not from under its feet (no pits).
- Goal switches only happen while walking when it isn't bridging, pillaring, digging or at a deep edge.
- Fuel lists skip crimson and warped wood (they don't burn); the "raw meat" fallback no longer hunts chickens it won't eat raw.
- Baby animals are marked in its memory and never hunted or planned around; source data is resolved once at load (less work per tick); standing-spot search raycasts nearest spots first.
- Known small issue left: the plan shown in the overlay can list a merged step slightly out of order; it corrects itself on the next replan.

### Bugs found in the first in-game test (fixed, need re-testing)
- **Hunting with whatever it held (planks) and "spam punching".** It never took out a weapon, and with an empty hand or a block the vanilla cooldown refills every 5 ticks, so full-strength hits really are that fast. Now `AttackTask` first takes out its best weapon (most damage per second, from the item's real attack damage and speed: a sword beats an axe, anything beats planks), then fights like a player who knows 1.9+ combat: only full-strength swings (over 0.9, the vanilla threshold), and with a slow weapon (sword, axe) **critical hits**: it closes in, jumps as the cooldown is about to refill, lets go of sprint in the air (a sprinting hit can't crit), and hits on the way down (vanilla crit rules: full strength, falling, off the ground, not sprinting, not in water or on a ladder) for 1.5x damage. With a fast item it just times plain full-strength hits (a jump takes about 12 ticks, so crit-jumping would do less damage per second). A crit attempt that lands without hitting falls back to a plain hit; no crit jumps under a low ceiling or in water.

### Bugs found in the second in-game test (fixed, need re-testing)
- **Under a tree it thought it was still night.** Its sky check used vanilla's "can see sky", which counts leaves as a roof. Now it looks straight up and sees the sky through anything that doesn't block the view (leaves, glass, plants), like a player with fancy leaves (`TimeSense.seesSky`).
- **Stuck jumping at 2-block steps on hills.** The path went diagonally toward a 1-block step and started the jump before it was lined up, so its straight line cut the corner of the 2-high block beside the step and it jumped against that over and over. Now a jump up is only started once it's in line with the step (it walks to the middle of the block in front of the step first), and points before a jump up are never "sailed past" diagonally.

### Not done yet / left for later (TODOs in code)
- **Beds and sleep** (stage 3 "a bed if 3 sheep of one color are around"; section 18 sleeping): not yet. The first night is spent in the dug-in shelter.
- Tier 2 hillside dig-in and tier 3 starter hut, home site selection: milestone 8 (and the hillside dig-in could come back here if testing shows the tier 1 hole isn't enough).
- Fighting back against hostile mobs, fleeing, reflexes (lava, drowning, creepers): milestone 6. If something attacks it now, it doesn't fight back.
- Personality traits (Efficiency, Caution, Knowledge, habits like eating threshold and tidiness): fixed stand-in values until milestone 7.
- Natural spawning (section 4): not yet; spawn with `/drifter spawn`.
- The strategist's daily agenda, home and storage (stages 4+): milestone 8.
- Picking the crafting table back up (Tidy Drifters): milestone 7 habits.
- **Mining style by personality** (user's note for milestone 7, fits DESIGN.md section 10.3 "chosen by stage, target and personality"): some Drifters should lean on strip or branch mining, others on caving and exploring the surface, and the mix should differ between Drifters. Today every Drifter uses the same rule (explore for caves first, staircase down after 3 failed legs).

### Next steps
1. User tests milestone 5 (`docs/TESTING.md`, "Milestone 5").
2. Fix what the test finds, until a fresh Drifter gets through its first night on most seeds.
3. Milestone 6 (combat and reflexes) only after the user says go.

## Milestone 6: Combat and reflexes

The "done when" test: it survives three nights in a row. Plus a combat-mod test pass (section 21).

### Done (code complete, not yet tested in-game)
- **Mobs hunt Drifters like players** (`world/MobTargeting`, sections 1, 2 and 13; on by default): every mob with an "attack the nearest player" goal gets a matching "attack the nearest Drifter" goal at the same priority, with the same rules (line of sight, reachability, the mob's own conditions: drowned only in water, slimes near their height, zombified piglins only when angry, spiders only in the dark). Not covered yet: brain-driven mobs (piglins, hoglins, wardens, breezes) and endermen (they only get angry if the Drifter hits them, since it never stares).
- **Threat assessment** (`brain/Threats`, section 13): threat = sum over noticed hostiles of base danger (data file `data/drifter/drifter_threats/vanilla.json`) x visible gear x proximity (x1.3 when it's coming for the Drifter); power = health x armor x weapon damage per second x shield x food. The ratio picks: engage (above 2), engage carefully (1.2-2), avoid (0.8-1.2), retreat (0.5-0.8), flee (below 0.5); "cornered" (a hostile within 3.5 blocks just hit it, and it isn't hopeless) means fight back. Only hostiles it has noticed count (perception), plus whatever just hit it.
- **Goals**: fight (urgent when a beatable hostile is within 6 blocks or just hit it), flee (urgent when it can't win), equip (puts on better armor and a shield in the offhand as soon as it has them). Danger is checked every 5 ticks between the once-a-second re-scores, and getting hurt triggers an immediate re-score. No fighting or fleeing while sealed in its night shelter (nothing can reach it).
- **Fighting** (`executor/task/AttackTask`, section 13 melee mechanics and per-mob protocols): best weapon first, full-strength swings only, jump crits with slow weapons. Melee mobs (zombies, spiders...): hit, then step back out of reach while the cooldown refills, hovering at 2.4-3.3 blocks and strafing (direction changes every 0.5-2 s), shield up while backing off. Archers (skeletons, pillagers...): zigzag in, shield up while they draw, then melee. Creepers: a sprint-knockback hit (never a jump crit), then back off to 5+ blocks before stepping in again; a hissing creeper makes it back off at once. After the fight: a nervous look around, then it picks up the drops (and eats, since being hurt raises the eat goal).
- **Fleeing** (`brain/activity/FleeActivity`): sprints away from the hostiles; swarmed by 3+ melee mobs with 3+ blocks, it pillars 3 up out of reach and waits. At night the shelter goal digs it in once it's clear.
- **Reflexes** (`reflex/Reflexes`, section 15): a layer that runs before the brain every tick and takes the body over for a moment, then hands it back to the paused task. Human reaction delay of 150-400 ms (3-8 ticks, +3 if the hazard is behind it) and a 15% chance to react 4-8 ticks late. Catalog: in lava (jump out to the nearest safe block), drowning (swim up, or toward open water), on fire (run into water within 4 blocks), creeper hissing within 6 (sprint clear; shield up if it can't get away in time), lit TNT within 8 (run), lava next to its body (place a block over it, or back away), heavy fall with a water bucket in the hotbar (water-bucket clutch 1-3 blocks above the ground, then scoops it back up), arrow or fireball on a collision course (shield up, or sidestep), suffocating (dig out), sand or gravel falling on it (step aside), stuck in a cobweb (cut it), pricked by a cactus or berry bush (step away). Sneaks on magma blocks.
- **Shields**: equipped to the offhand by the equip goal; raised by the fight and the reflexes (vanilla blocking works for it: 0.25 s raise delay, slows it while held).
- **Overlay / status**: `/drifter brain` and the overlay show the threat line ("threat 1.3 vs power 2.2 (1.69): engage carefully [zombie]") and the active reflex.

### Fixed after a code review (before any in-game test)
- **Mobs it hit went after its invisible helper** (the fake player standing where it swung), which made every fight free. Now each hit points the mob's revenge, anger and target (and zombies' called-in friends) at the Drifter. A sprint hit also costs the Drifter its momentum, as for a player.
- **A mob attacking while it walked to or dug its night hole was ignored.** Fighting or fleeing a mob that's on it now outranks everything (shelter included) and doesn't wait for a checkpoint.
- **It flip-flopped between fleeing and going back to work** as it stepped away from a chasing mob (the threat counted less with distance). A mob that's after it now counts in full however far away. Spiders don't count as a threat in daylight unless they're after it.
- **After a reflex it stopped hitting** (the camera stayed where the reflex left it): it now aims at its target again. Reflexes give back the hotbar slot it was holding; the fight switches back to its weapon if something else ended up in its hand; pillaring looks straight down again if a reflex turned its head.
- **Shields:** a blocked hit now wears the shield (and can break it), an axe hit disables it for 5 s, as for a player. Damage that scales with difficulty now hurts it like a player (half on Easy, x1.5 on Hard).
- **Combat footwork** never backs or strafes it off a cliff or into lava (it checks the step first and sneaks near edges).
- Fleeing: it only waits on its pillar if it really got up there. Looting after a fight: a new attacker sends it straight back to fighting.
- Reflexes: escaping lava with no safe block in sight keeps one direction (it used to spin); the cactus step-away reflex now fires; incoming arrows are followed with gravity and drag (a straight line missed skeleton arrows until too late) and it sidesteps across the arrow's path; stepping aside from falling sand goes fully out of the column; the suffocation reflex digs the block its head is really in; without blocks it no longer loops backing away from lava it's only walking past.
- Mobs look for Drifters as high and low as they look for players (it was only 4 blocks up and down). No sneaking on magma during a deliberate drop.

### Changes after the first in-game test of milestones 5 and 6 (need re-testing)
User's report: survived a day, went into a cave, ran from a creeper, killed a skeleton, then died to the hill-step problem in a fight.
- **Stone: explore first, dig down last** (user's rule: "learn the land, look for a cave, then mine"). Stone it hasn't seen now means exploring for caves, cliffs and hillsides (it stops the moment it sees stone it can reach); it digs a staircase straight down only after 3 legs of exploring found none.
- **Smelting while multitasking** (user's rule, section 10.4): it loads the furnace (input and just enough fuel) and leaves; the batch is remembered (and saved) with when it'll be done. While a target waits on the furnace, the progress goal works on the next target; a "collect from the furnace" goal brings it back when the batch should be done (if some is still cooking it comes back again later). Plans count what's cooking as on its way ("collect cooked beef from the furnace").
- **Running away without stopping to think** (user's note): fleeing now turns and sprints straight away from the danger with the keys, swerving around drops, lava and walls (trying directions up to 120 degrees off), and only asks the pathfinder when it's boxed in. Every walk also starts moving while the first route is still being worked out.
- **Less zig-zag** (user's note: "walk 2 quarters of a diagonal"): on flat ground it walks straight at any angle to a point up to 5 steps ahead whenever that line is clear for its whole body (open feet and head, solid floor, no fluids, no doors), instead of visiting each block centre.
- **Cooked food first** (user's rule; section 10.5 already said raw meat "only when it can't cook"): it no longer eats raw meat, fish or potatoes while it could cook them. It eats them raw only when its food is at 6 or less (it can't sprint any more) or it has no way to cook (no furnace or smoker nearby, none carried, no 8 cobblestone to make one). A new **cook** goal cooks the raw food it carries: ahead of time once it has 3 or more of one kind (a bit above routine progress when it knows a furnace nearby), and as a must when it's hungry with nothing cooked to eat. It loads the furnace and goes off as usual; if its meal is waiting and it's hungry, it goes back for it sooner. Carried raw food now counts as what it'll be once cooked, so it doesn't hunt more than it needs.
- **Swimming** (user's report: bouncing high in water, stuck in the middle of a river, about 1 block per 50 s): the path's points in water are the water's top block, but a floating swimmer's feet are most of a block lower, so it almost never counted a point as reached: it hovered around each one holding jump, then gave up and planned again. Now a point in the water counts as reached within 0.7 blocks at floating height (or once passed on a straight line). It only holds jump while its eyes are under water (and to climb a bank it swims into), so it bobs at the surface instead of bouncing, and routes prefer the surface (swimming with its head under costs extra). Sprint-swimming stays with the rest of section 10.10 (milestone 8).
- **Swimming, round 2** (user's report: faster, but bouncing really high and sometimes sinking): sprint carried on into the water (as it does for a player) and turned into face-down sprint-swimming, where the body follows the head: holding jump while looking ahead and up launched it out of the water, looking down sank it. Now in water it lets go of sprint (and of the movement keys for a tick if it's still sprinting, which is how a player stops). Deliberate sprint-swimming stays with section 10.10 (milestone 8). Fleeing through water also keeps its head up.
- **Night: stuck at the shore** (user's report: "their brains turn off", in water they go to the shore and stand there): the shelter walked toward the nearest dry block but stopped a block short, still in the water, then gave up for the night (and everything else is held back at night, so it stood there). Now it walks all the way onto land (to within half a block), to a spot at least 2 blocks from any water (water pours into a hole), searching farther on each of 3 tries. Spots tried when the ground is bad (sand, gravel, water nearby) are picked the same way.
- **Night fallback: walling in** (user: "they gotta make sure they are truly safe"; section 11 tier 1 extended): if it can't dig down (sand, a beach, no good spot after 4 tries, or it hit something half way), it walls itself in where it stands, like a player: four blocks at its feet, four at its head, one on top of a wall to place against, then the roof (up to 10 blocks of dirt or cobblestone). In the morning it breaks out through one wall.
- **Sharper senses** (user's call, DESIGN.md section 5 updated): it sees entities out to 112 blocks (7 chunks) in daylight, scaled by light and the creature's size; notices things about 2.5x faster (about a third of a second up close, a second or two far away); tracks 12 at once. Far sight: 2 extra rays per tick near the horizon reach 112 blocks and note mountains, villages (bell, workstations) and bare stone as **landmarks** ("spotted a village ahead, about 90 blocks away"); stone and mountains it saw become where it explores for stone. It **knows creatures by their sounds** (a moo is a cow, a rattle a skeleton, a hiss a creeper) and roughly where, without looking; it fights only what it has seen, but a heard monster counts toward its sense of danger and it turns to look. Cost kept down: beyond 32 blocks one sight line per entity, at most 16 far checks per re-rank, far tracked ones re-checked every third update, rays stop at unloaded chunks, and sounds check a short list of loaded Drifters instead of searching the world. Per-Drifter cost is now roughly 20 short ray casts per tick; the offscreen detail tiers (milestone 10) will cut this for Drifters nobody is near.
- **Smokers**: food goes in a smoker it knows (in a village, or one you place) instead of a furnace: twice as fast, same fuel. Building its own smoker comes with a home (milestone 8).
- **Died at a 2-block step mid-fight**: up close the fight steers by hand, which ran into such steps and jumped at them. Now a wall it can't jump makes it let the pathfinder lead around it for 2 seconds.

### Not done yet / left for later (TODOs in code)
- Bows and crossbows (ranged mechanics), axe-vs-shield, sweeps lined up on groups, W-tap sprint resets: milestone 6 follow-up or milestone 9 (players).
- Terrain sense (doorways, high ground, 2-high ceilings against endermen), backing into light: later.
- Retreating toward home or light, blocking itself in when pursued: milestone 8 (needs a home).
- Golden apples, potions, totems, ender pearls: when it can get them (milestones 8 and 11).
- Fighting styles and Skill-scaled timing and miss chances: milestone 7 (personality).
- Better Combat / Combat Roll integration: see design question 9.
- Brain-driven mobs targeting Drifters (piglins, hoglins, breezes): milestone 11 (Nether). Phantoms, guardians and shulkers use player-only targeting and don't go after Drifters yet.
- Archers more than 5 blocks away are approached straight (the zigzag starts within 5 blocks).

### Next steps
1. User finishes testing milestone 5, then tests milestone 6 (`docs/TESTING.md`, "Milestone 6").
2. Fix what the tests find; the "done when" is three nights survived in a row.
3. Combat-mod test pass (section 21): with Simply Swords and Better Combat installed.

## Milestone 7: Mannerisms, personality and mood

The "done when" test: people watching a short clip can't reliably tell it from a player. Started in session 2 (local),
at the user's request, while milestones 5 and 6 are still in testing.

### Done in session 2 (code complete, smoke-tested on a headless dev server, not yet played)

Smoke tests run (`tools/smoke.py`): personalities roll, show and survive a restart; all 12 tics play by command with
no errors; a forced night hunter prowled at dusk and backed off from a zombie and skeleton together; a night miner dug
in, tunnelled with turns, placed torches, blocked off three cave openings and came away with iron and coal; the
"stays in" one sealed in and got bored. Fixed from those runs: it couldn't dig in while standing in grass or flowers
(the plant was in the way of its crosshair; older bug), and wall ores it couldn't see counted as failures.
- **Personality** (`personality/*`, section 17): 17 traits (0 to 1), 12 archetypes with the section 17 spawn weights
  and trait presets (preset + noise of 0.13, Caution and Bravery pulled against each other), rolled once per Drifter
  and saved (`DrifterPersonality`, version 1). Older Drifters get one rolled the first time they load.
- **Habit fingerprint** (`Habits`): eat threshold (12 to 17), scroller or number-keyer, auto-jump, sprint toggle,
  tunnel shape and branch spacing, torch side, offhand, door etiquette, picks up crafting tables or not, celebration
  and greeting style, sleep timing, trail markers, hotbar layout, dirt stack, favourite wood/stone/wool, and a
  **mining lean** (user's note: strip/branch miners vs cavers and surface explorers, different for every Drifter).
  Rolled from the traits, so habits fit the person. Several are only used by later milestones (rolled now so a
  Drifter's identity never changes).
- **Mood** (`Mood`, `MoodState`): every mood has a pressure that events push and that fades with its own half-life.
  Getting hurt and danger make it anxious, nearly dying with something after it makes it panic, kills make it
  triumphant, good gear keeps it confident, failed jobs frustrate it, nothing new for 2 minutes (or idling, or a long
  wait in the shelter) bores it. Moods pull on traits while they last (Panicked: Skill -0.2, Bravery -0.25; Bored:
  Curiosity +0.2, Fidget +0.2; and so on). Mood changes go to the thought log.
- **Traits wired into what already existed** (all the milestone 7 TODOs):
  - Brain: how predictable the top-three pick is comes from Efficiency (section 7).
  - Eating: eats at its own threshold (eating beats routine work at or below it).
  - Shelter timing: a cautious Drifter starts heading in up to ~1000 ticks earlier, a reckless one later; starting
    long jobs late in the day is scaled by Caution (section 7 risk).
  - Exploring: curiosity need from the Curiosity trait, plus boredom.
  - Fights: Bravery and Caution shift the fight/flee ratio (x0.7 to x1.3); cowards (Bravery under 0.3) only fight
    when cornered; aggressive ones go after mobs further off. Fighting styles (section 13): brawler (stays in and
    trades hits), duelist (the old behaviour), turtle (shield up while waiting, backs off longer).
  - Skill: camera overshoot, reflex reaction time (3-6 ticks skilled, 4-10 clumsy) and how often it's late, how full
    the cooldown is before it swings, how often it goes for a crit, whiffs (a swing at the air that resets the
    cooldown, like a player's miss). Panic means spam-clicking and more whiffs. Kills give a little practice (Skill
    can grow up to +0.2).
  - Pathfinding: Caution and Thrift weights from the traits.
  - Inventory pauses (crafting, moving items) scaled by Efficiency.
  - Hotbar: scrollers visibly scroll through the slots in between; number-keyers jump straight there; low Skill
    sometimes grabs the slot next to it first, then corrects.
  - Mining style: legs of exploring for stone before digging down: 0-1 for a strip miner, up to 5 for a caver;
    frustration halves it.
- **Mannerism layer** (`mannerism/Mannerisms`, section 16): tasks now say which body parts they leave free
  (`Task.freeParts`: plain walking frees head and hands; waiting frees everything; looking around frees hands and
  legs; everything else claims the whole body). A camera "glance override" lets a tic borrow the head and hand it
  back to the task. Section 16.6 rules: one tic per 2 s at most, never the same twice in a row, per-tic cooldowns,
  nothing during reflexes, fights, fleeing, swimming, eating or anything fiddly, rates scale with Fidget and mood.
  Tics: side glances while walking (toward a sound or creature when there is one), look-backs (walking at night,
  anxious, and repeatedly for 30 s after nearly dying), sky check at dusk and when coming up from underground,
  360 sweep (careful types, after surfacing), jump-spam while waiting, crouch-spam / spin / swing at air to
  celebrate a kill (by its celebration habit), spin on new armor, hotbar scroll fidget, nervous shield raise at
  night when it hears a monster it can't see, going AFK in long waits, and a rage fit when very frustrated.
- **Night by personality** (section 18, user's notes): chosen once each evening (`Schedule.NightStyle`):
  - **Stays in** (most, and always the cautious): digs in as before.
  - **Goes out hunting** (Bravery 0.62+, Caution under 0.6, calm, 14+ health, food 10+, a weapon at least as good
    as a wooden sword): a "hunt mobs in the dark" goal prowls 8-20 blocks around where it was at dusk; any hostile
    it can take becomes the fight goal (drops are picked up after). Once hurt or hungry it digs in like everyone else.
  - **Digs in, then mines** (strip-miner lean 0.55+, Industriousness 0.45+, has a pickaxe): after sealing the
    shelter it digs a 1x2 tunnel from the bottom of the shaft (a main leg of 10-18, then up to 3 side legs of 5-9,
    48 steps a night at most), mines ore it uncovers (whole veins within reach, only with the right pickaxe),
    blocks off caves and water it breaks into, stops at gravel/sand/hard rock, puts a torch every 8 steps **on its
    habit's side going in** (user's note: players use torches to know where they are and the way back), and walks
    back to the shaft in the morning to climb out.
- **Usernames** (`identity/Usernames`, section 4, design question 10): every Drifter gets a player-like name (word plus
  digits, two words joined, words with underscores, a first name and a number, the odd styled one like `xXFrostXx`),
  3 to 16 letters/digits/underscores, never one a player online or a loaded Drifter has, never a listed creator or
  public figure, filtered for offensive words. Word lists in `data/drifter/drifter_names/default.json` (datapacks can
  add more). Shown like a player's nameplate (always above its head within 64 blocks; through walls unless it
  sneaks). A name you give it is kept; Drifters from older worlds get one when they load.
- **Commands**: `/drifter personality [reroll [archetype]]`, `/drifter mood [mood]`, `/drifter tic <tic>`,
  `/drifter night [shelter|hunt|mine]`, `/drifter brain goal nighthunt`. The overlay shows archetype, mood, key
  traits and the current tic.

### Bugs found in the first in-game test of milestone 7 (fixed, need re-testing)
- **Mining with a pickaxe while showing a log in its hand.** Not milestone 7 code: the test profile got **Lithium**,
  which skips a mob's "has its equipment changed?" check unless its vanilla equipment lists change (players are the
  exception, since their equipment lives in an inventory, like a Drifter's). Hotbar switches never reached clients, and
  armor and weapon attribute updates could be skipped too. `compat/LithiumCompat` (optional, found at runtime, never
  required) marks a Drifter's equipment as changed every tick, as Lithium does for players. Checked on the test server
  with Lithium: armor counts and hotbar switches go through.

- **Swimming, round 3** (user: "bouncing is still very high and they move like they don't know how to swim").
  Traced tick by tick on a test pool (`/drifter trace <ticks>`, new debug command). Three causes:
  1. **The real launcher, there since milestone 1:** vanilla `Mob#jumpInLiquid` adds **0.3** upward speed per tick
     for mobs whose navigation can't float; a player (`LivingEntity`) gets **0.04**. A few ticks of jump underwater
     shot it out like a cork. `DrifterEntity#jumpInLiquid` now uses the player's number.
  2. It only treaded water on swim and walk steps; on diagonal, jump-up and step-down steps it sank to the bottom.
     Now it holds jump whenever its head is under water, on every step (`NavigateTask#treadWater`), holds forward
     and jump into a ledge to climb out like a player, and never jumps again once it's up on a step.
  3. Points on the water's surface weren't counted as reached at floating height unless the step was a swim step,
     so it hovered under them. Any point over water now uses the floating-height check.
  Result on the test pool: crosses and climbs out every time, bobs gently at the surface (about 0.4 blocks), swims
  about 2 blocks/s, never rises more than a normal jump.

- **Configurable FOV** (user's request): `/drifter config fov <30-110>`, like the game's FOV slider (vertical degrees;
  the cone's width follows from a 16:9 screen), saved in `config/drifter.json`. Default 70 = a player's default view,
  about 102 x 70 degrees (it was a fixed 110 x 70). The debug overlay draws the cone from the same setting (in
  multiplayer the overlay uses the client's own config file).

- **View cone is now a flat 16:9 screen** (user: "is the cone supposed to look vertical, like portrait mode?"). It
  was built from angles on a sphere (yaw +- half, pitch +- half), which came out nearly square, looked tall from the
  side, and bent badly when looking up or down (looking straight down, almost everything around counted as in view).
  Now vision (`Senses.screenAngles`, `viewBasis`, `screenDirection`), the random block rays (spread evenly over the
  picture, like pixels) and the overlay outline all use the same flat screen at the FOV setting, as for a player.
  The peripheral zone around it is unchanged.

### Not done yet in milestone 7 (next steps)
1. In-game check by the user (see `docs/TESTING.md`, "Milestone 7"), then fixes.
2. ~~Usernames and always-visible nameplates~~ (done). Join/leave chat lines and the world-level identity registry
   (section 4) stay with spawning/milestone 12.
3. Beds and sleeping (section 18: sleep a few seconds, then cautious ones stay in bed, others do chores or mine).
4. More tics: corner peek, edge peek, wall stare, sprint stutter, door double-flip, toss and pick up an item,
   over-mine one block, accidental grass break, look a new player up and down (milestone 9 with greetings),
   torch spam when anxious in a dark cave.
5. Signature moment "finding diamonds" (needs ore memory events), "meeting a player" (milestone 9).
6. Habits not used yet: tunnel shape 2x2/3x3, offhand habit, door etiquette (closing doors), picking crafting
   tables back up, trail markers, hotbar layout, junk policy. Tidy habits come next.
7. Knowledge trait limiting recipes (section 8): not yet (it could stall day one for newbies; needs care).
8. Torches as way markers while **caving** (user's note): caving itself is milestone 8; the torch-side habit is in
   place for it.

## Milestone 8: Progression and building

The "done when" test: an unattended 10-day run reaches diamonds with a recognizable base. The user said "things are
looking good, we can go to milestone 8" in session 2. Plan, one step at a time, each tested before the next:
1. **Home and storage (stage 4).** Done, see below.
2. Starter hut (shelter tier 3): the building system's first blueprint (5x5 or 7x7 box with a door, table, furnace,
   chest, torches), built in a player's order; Drifters with a home sleep in it instead of digging in.
3. Iron age (stage 5): mining trips to the right depth, branch mine or caving by mining style, torches as way markers,
   back home to smelt; iron pickaxe, bucket, shield, armor.
4. Beds and sleeping (wool from sheep).
5. Settlement (stage 6): 9x9 wheat farm, animal pen and breeding, organized chests, full iron armor.
6. Diamonds (stage 7): branch mining near the bottom of the world; diamond pickaxe, then sword, then armor.
7. Enchanting (stage 8): obsidian, books, enchanting table, bookshelves, anvil.
8. Houses (tier 4, by archetype) and compounds (tier 5), plus the tidy habits left from milestone 7.
9. The 10-day unattended run and tuning.

### Step 1: home and storage (code complete, smoke-tested on the dev server, not played yet)
- **Home** (`brain/Home`, saved with the brain): where it settled, its chests, and an exact record of what's in each
  (updated every time it looks inside one; if someone takes things it finds out when it next opens the chest).
- **Choosing a site** (`brain/HomeSite`, section 11): from blocks it has seen only: a 7x7 footprint of dry standable
  ground (one block up or down counts half), open sky, water within 16, trees and stone within 24, a little higher for
  Builders, stone nearby for cautious ones, not too far. Picky for two looks around, then it takes bumpier ground;
  with nothing at all it explores for a site.
- **Settle goal** (stage 3+, after stone tools and a furnace): picks the site, makes a chest through the planner,
  walks home and puts it down; another chest (kept apart, not a double chest) when they're full. Nomads hardly bother.
- **Store goal** (section 10.18): goes home to put things away when nearly full, when near home with a load, and on
  the way in at the end of the day. It keeps its working kit on it (`brain/Storage`): best tool of each kind, weapon,
  shield, buckets, a table, 16 food, 32 torches, 64 building blocks, 16 wood, 16 sticks, 16 coal; everything else
  goes in, in one visit per chest (`ContainerTask.deposit`).
- **Home storage counts as owned** (section 9): the planner has a "fetch from its chest" step (cost: the walk), and
  the stage and targets count what's stored. Stage 4 "settling" = stage 3 plus a home with a chest.
- **Heading home at night** (section 18): the evening starts earlier by its travel time home (within 96 blocks); the
  shelter walks home first, then digs in there (the hut comes in step 2).
- **Junk policy** (section 10.18; found in testing: a full Drifter crafted a chest that fell on the floor): when it's
  nearly full it throws out andesite, diorite, granite, tuff, calcite, poisonous potatoes, gravel and sand beyond 16,
  dirt beyond one stack (only if that's its habit or it has no cobblestone), rotten flesh when it has real food; it
  opens its inventory and drops them one at a time in front of it (`TossJunkTask`, like pressing Q). Full with junk
  is urgent. It never picks up what it threw, and leaves junk lying when it's nearly full. Hoarders keep everything.
- **Fixes found while testing this:** a job running a sub-job (setting up home, gathering logs) never let the brain
  switch goals, so it ignored nightfall (nested activities now count as safe to interrupt when their step is); lost
  in a deep lake at night with no dry land it had seen, it swims back to where it last stood on dry ground.
- **Commands:** `/drifter home` (where, chests, fullness, what's stored), `/drifter home here` (settle on the spot),
  `/drifter home clear`, `/drifter brain goal settle|store|makeroom`.

### Step 2: starter hut (code complete, smoke-tested on the dev server, not played yet)
- Village-safe gathering (user's question): wood only from real trees (`brain/TreeLogs`: logs connected to natural
  leaves), cobblestone only from natural stone (source table), logs that aren't trees and cobblestone count as
  "somebody's build" for pathing. Tested: it chopped the tree and left a bare log post standing.
- Hut (`building/StarterHut`, `brain/activity/BuildHutActivity`, `executor/task/PillarUpTask`, `CenterOnBlockTask`):
  5x5, walls 2 high, 5x5 flat roof, door, chest/table/furnace/torch inside; built from inside, roof from a one-block
  pillar in the middle, middle roof block last from the floor; blocks it misses get a second try from a spot where it
  can see a face. Goal "build a hut" (stage 4). At night, a finished hut is used instead of digging in (go in, shut
  the door, walk out in the morning); sheltered in the hut it only fights something that got in and hit it.
- `/drifter hut` prints the hut as a layer map.
- Smoke test (flat world): from settling to a finished hut (door, chest, furnace, torch, 0 blocks missing) in about
  40 s of building; at dusk it went in and shut the door ("in the hut for the night"), ignored a zombie outside, and
  stayed in until morning by its own clock. It keeps one furnace and one crafting table on it (so the hut gets its
  furnace instead of it going in the chest).
- Found while building it (fixed): a taller 4-high design couldn't be finished because a player can't see corner
  pockets high up from inside, and the chest in the corner blocked the view to the floor next to it; the middle roof
  block closed too soon blocked pillaring up again. The 2-high design with a one-block pillar and a "walk to where it
  can see a face" fallback solved it.
- Known: the crafting table it used for the chest sometimes ends up as part of the wall.

### Step 3: iron age (code complete, smoke-tested on the dev server, not played yet)
- **Targets** (`brain/Strategist`): once settled (or straight away for Nomads) it works through iron pickaxe, bucket,
  shield, chestplate, leggings, helmet, boots, shears, flint and steel. Stage 5 "iron age" = iron pickaxe, bucket,
  shield and two armor pieces. Before those, "a spare pickaxe" whenever its only pickaxe is half worn (section 10.17);
  it keeps the spare on it instead of storing it.
- **Mining trips** (`brain/activity/MineTripActivity`, planner action `Mine`): when the planner needs an ore it hasn't
  seen, it plans a trip to the ore's depth from the source table (`drifter_sources`: iron around y 16, coal 40, copper
  48; in mountains above y 96 it digs 24 down into the mountain instead). Remembered ore goes first. Otherwise a 2-high
  staircase from just outside the hut (a different side when one fails) down to the depth, then a branch mine: a main
  tunnel and side branches every few blocks (its branch spacing habit). Home remembers the mine: the next trip walks
  back down and branches a new way, or carries on down a staircase it didn't finish. A staircase that has to stop
  within 24 blocks of the depth still branches from there.
- **Ore it spots on the way** (caves it breaks into, walls): within 16 blocks, it goes and mines it (and the vein), then
  back to its tunnel. In cave-riddled ground this is how it actually finds most of its iron, like players since 1.18.
- **Enough pickaxes:** before a trip it estimates the digging (3 blocks per stair step plus about 100 for branches) and
  makes extra stone pickaxes (up to 4) if what it carries won't last. It goes home when it has enough, its pockets are
  full, it's getting late, it's hurt or hungry, or its last pickaxe is nearly worn out. No trips late in the day.
- **Shared tunnel digger** (`brain/mining/Tunnel`, `TunnelDigger`), used by night mining too: torches every 8 blocks on
  its habit side, mines ore veins it uncovers, plugs holes into caves and water, turns to whichever side is clear
  (never back into its hut, and its thoughts say why it turned), never digs gravel/sand over its head, slow blocks, or
  anything that **looks built** (found in testing: it dug through a dungeon's mossy cobblestone wall).
- **Stations at home:** within 64 blocks of home it walks back to the table and furnace there instead of making new
  ones (found in testing: 24 blocks out after mining, it made a second table and furnace).
- **Grass and flowers** (found in testing, affected building and mining): they have no collision but block a click, so
  "can't see that face" failures piled up on grassy ground. Placing now clicks the grass itself (the block replaces
  it, as for a player), and the staircase breaks plants in its way. The hut also fills dips up to 3 deep under its
  floor before laying it.
- **Other fixes found while testing this:** caves made the staircase fail (now plugged or turned round); a furnace was
  made at the bottom of the mine (it now counts the one at home); after filling a hole back in, it kept trying to walk
  to the old tunnel end for minutes; a walk inside the mine is given up after a minute; it no longer sets off and turns
  back every tick late in the day.
- **Debug:** `/drifter thoughts log true` copies every thought into the server log as it happens (for long tests).
- **Testing speed:** smoke tests now use `/tick rate 60` (3x speed); everything the Drifter does counts game ticks, so
  the behaviour is the same (user's idea, session 2).
- **Smoke test (your world seed, -7697369017275805093):**
  a miner with a starter kit (stone tools, a half-worn pickaxe, 100 cobblestone, torches, food) at 3x speed. Run 13:
  settled, made a spare pickaxe, dug a staircase, broke into a cave and saw iron ("spotted raw iron ...: going to get
  it"), mined the vein, smelted it at home, made an **iron pickaxe**, then a **bucket** and a **shield**, and put on
  armor; the next trip carried on down its old staircase. Run 15: hut finished, iron pickaxe and bucket on day 2.
  Run 16 (fresh world, after the water fixes): hut, then iron spotted in a cave at y 20, smelted, iron pickaxe on day 1;
  it wore that out by day 2 (night mining plus trips) and went back down for more, like a player would.
  Earlier runs on the same seed (before these fixes) never got iron: the ground there is full of caves, water pockets
  and an old mineshaft.
- **Flooded staircase (found in run 15, fixed):** water got into a staircase and it hopped for a long time at a ledge
  two blocks above its feet. Two causes: the path planner let it "jump" from water running down the stairs as if
  floating in still water (now only from still water, a floor or a ladder), and pillaring in water tapped jump once
  like on land (in water it now holds jump and waits for the rise). Reloading the stuck world with the fix, it walked
  out, pillared one block and climbed its stairs home. Also: when it can't pick a spot by its table or furnace from far
  away (it doesn't remember the ground there well), it walks over and looks first.
- **From the user's own test (session 2, their log):**
  - Pillaring off-centre: standing near the edge of a block, its head was under the block next door and every jump
    stopped at 0.2 blocks; it retried the same spot for minutes (the user worked this out). It now steps to the middle
    first, and a spot where pillaring failed is avoided by the pathfinder for 5 minutes (it goes another way or digs).
  - Hut roof loop: it pillared up, climbed down breaking the pillar, and pillared again, for roof blocks it couldn't
    reach. Causes: blocks on its "retry" list didn't count as roof work, so it left the pillar too early; and from the
    pillar a roof block behind another one (an earlier visit, a repair) can't be seen. Now: one pillar visit per job;
    it knocks out the roof block in the way (inner ring, or the edge block beside a corner), places the missing one and
    puts the other back; the middle block always goes back before it gives up. Tested on a flat world by deleting 6
    roof blocks of a finished hut: repaired to "finished, 0 missing". On a copy of the user's world: hut finished.
  - Fall into a cave (13 damage): a tunnel only checked the blocks in front, not the floor it was stepping onto. Now a
    hole in the floor gets a block (once), otherwise it turns.
  - A walled-in crafting table far from home was chosen over and over ("can't find a spot by the crafting table");
    after walking over and still finding no way to it, it forgets that station and makes a new one.
- **Night flip-flop (seen on the user's world copy, fixed):** late and away from home with mobs about, it switched
  between "get away" and "shelter" every second until it was killed (it ran off, then walked home past the mobs, then
  ran off again). Now, in the evening, running from mobs means running home if home is away from them; if they're
  between it and home, home counts as cut off until morning and it digs in or walls in wherever it gets clear. Tested
  on a flat world at night with 4 zombies and a skeleton: behind it, "running home" then "in the hut for the night";
  between it and home, "the mobs are between it and home", then (after two skeleton interruptions) sealed in.
- **Known, next (seen on the user's world copy):** going back down its old mine, the pathfinder kept finding blocks
  where it expected open space and it lost most of a day. Caving (user's request, see the
  user notes below) replaces most staircase mining.
- **Known:** each failed staircase leaves a hole by the hut; in very cave-riddled ground trips still sometimes come back
  empty (it tries another side next time).

### Caving and looting (session 2, user's request; written and smoke-tested)
- **Caving** (`brain/mining/Caver`, `MineTripActivity` phase CAVE): everyone but strip miners (mining style habit below
  0.65). It goes to a cave it has seen near home (a "frontier": a spot it saw it can stand on, with 3+ blocks of ground
  above, next to blocks it hasn't seen), or into a cave its staircase breaks into (in front or beside). It looks around,
  walks to the next frontier (nearer and deeper first), lights the way with torches on its habit side where block light
  is under 7 (every 6 blocks at most), goes for ore it spots within 16, and stops after 16-48 walks (bravery) or when
  the cave runs out; then the usual stairs. It makes torches first if it has fewer than 16.
- **Looting while caving** (`Caver.loot`, `executor/task/LootCartTask`, perception of chest minecarts): breaks spawners
  it sees, loots chests underground that aren't its own (not junk), and mineshaft chest minecarts (look, open, pause,
  one stack per click). Each is dealt with once (`Brain.looted`). Test (flat world, a buried room with a real dungeon
  chest, a spawner and a mineshaft chest minecart): it found all three, broke the spawner, took gunpowder, an
  enchanted book, a name tag, a music disc, bread, rails and more.
- **Water drops:** brave Drifters plan drops of up to 32 blocks into water they can see; careful ones about 8.
- **Drowning (found in testing, fixed):** at night it went under a ceiling and drowned: the out-of-air reflex headed for
  "open water" as well as air. Now it heads for air only, and planned routes keep head-under-water stretches to 10
  moves.
- **Real-seed runs (the user's seed):** a Speedrunner went caving ("going caving: a cave it saw at -9, 45, 22"),
  spotted iron at y 40 and mined it; another run: iron pickaxe, bucket and shield by day 2.
- **Fixes from 5-day sprint runs (both personalities, the user's seed):** most "caves" its stairs broke into were air
  pockets or air under overhangs by the surface: a cave now has to be underground (a solid block, not leaves, over its
  head), have 20+ open blocks joined to it, and none of them open to the sky; it peeks into the opening and steps in
  before looking round. Strip miners (and cavers in a pocket) go straight through cave space crossing their stairs and
  hop down onto a cave floor up to 3 blocks below instead of turning; an air opening it can't plug is left open (water
  still turns it). The "too late to set off" rule no longer cuts a trip short underground (it had left one wandering in
  a cave, where it walked into lava), and it never wanders about deep underground.
  Result (5 game days each, sprinted): strip miner: iron pickaxe day 2, then bucket, shield, armor, a second iron
  pickaxe; caver: caving trips into real caves, iron pickaxe day 4; neither died.
- **Testing speed:** `tools/smoke.py` has a `sprint <ticks>` line (`/tick sprint`, as fast as the machine runs, then
  waits for "Sprint completed"): 5 game days take about 2 minutes (user's idea).
- **Underground water and suffocation (session 2, from sprint runs):** it remembers where its head was last in air and,
  with no air it can swim to nearby, swims back there (staged test: a flooded tunnel, it swam 8 blocks back unhurt);
  the out-of-air reflex fires at half air and only heads for air it can actually swim into (over or beside the water,
  not a sealed pocket); after nearly drowning it plans no underwater routes for two minutes, and two scares in a minute
  make "get out of the water" an urgent goal (it had kept going back under for what it was doing). Stuck with its head
  in a block (falling sand), it can always dig that block out (the crosshair is on it from inside, as for a player;
  it had suffocated "unable to see" it). Water it can't plug when a step breaks into it: it fills its own step back in
  and turns (staged test: works). Also: a block is never placed where its own body is ("it's standing in the way"), and
  the fallback chest spot is never in the hut's middle, walls or door (jumping off a chest there to roof the hut opened
  the chest instead of placing a block).
- **Debug:** `/drifter tunnel <length> [targetY]` digs one test tunnel (level, or stairs down to a height) with the
  mining digger, for staged tests.
- **Results after these fixes** (5 game days each, the user's seed): 6 of the last 6 runs survived; caver: hut, iron
  pickaxe day 1, bucket, shield, chestplate; strip miner: hut, iron pickaxe, bucket, shield on day 1, chestplate day 2,
  leggings by day 4. Some runs still get no iron when home is ringed by water and caves (it tries other sides).
- **Getting out of ponds (fixed, staged tests: pools with sides 2 and 3 blocks above the water):** it planned walks across
  the surface on "unseen ground" (its sight stops at the water's surface) and jumps from the water that fall just short.
  Now: under or beside water it has seen it expects more water; from the water it only steps or jumps onto blocks it
  has seen are solid; it never plans a pillar from water; swimming, it can tell the blocks within 3 around it; and it
  keeps its head up whatever step it's on (it used to let go of every key while turning its head, and sank). Out in
  about 26 s (2-high sides, was 36) and 32 s (3-high, was 54), digging a step into the side like a player.
- **Known:** cavers are still a little slower to iron than strip miners on average.
- **Known:** the starter hut's own pillar step sometimes can't place under itself ("can't get up to put the roof on"),
  now with a detailed reason in the thought log to find out why; a cave found by breaking in sometimes yields nothing to
  explore (it now looks around first).

### Step 4: beds and sleeping (written and smoke-tested, session 2; village houses still to do)
- **Wool by colour:** sheep are remembered with their colour ("white wool" in what it saw); the sources table has one
  entry per wool colour ("color" field), the planner only hunts a colour it has seen, and hunting only targets sheep of
  that colour. Staged test: three white sheep and a red one: it killed the white ones and crafted a white bed.
- **Target "a bed"** once the hut is up, but only when it has seen sheep of a usable colour (found in testing: without
  sheep it wandered for five days looking and never went for iron). It keeps one bed on it until it's in the hut (it
  had gone into the chest and never into the hut).
- **Bed in the hut:** along the right wall, foot in the door-side corner, head toward the back (the click sets the way
  it faces); chest, table and furnace take the other three corners. The hut goal comes back to put a carried bed in.
- **Sleeping** (`ShelterActivity` phase SLEEP, section 18): in the hut at night it gets into bed by the player's rules
  (only at night or in a thunderstorm, no monsters within 8 blocks round and 5 up/down), `startSleeping` (sleeping pose,
  doesn't move; any hurt wakes it, as for a player). Careful and homebody types stay in bed till morning; others get up
  after about 6 s (it can't skip the night). In at dusk before beds work, it goes to bed once it's dark.
- **Also fixed while testing:** torches couldn't be placed in its own block (a torch has no collision; only solid blocks
  are refused where its body is); pointed dripstone is never walked or dropped onto (a caver died on stalagmites);
  underground, mineshaft planks and dungeon walls no longer turn its stairs (above ground, "looks built" still does,
  for villages; player-placed blocks are always protected).
- **Results (5 game days each, the user's seed, 8 runs):** beds crafted in about half (white, blue), sleeping seen;
  iron pickaxe in 6 of 8; 1 death (drowned on the way up a flooded staircase).
- **Village houses (user's request; written and smoke-tested in a taiga village on the user's seed):** it recognises a
  village from outside (paths, hay bales, villagers, job blocks), looks round it (up to 3 walks to different sides) and
  into houses whose doors it has seen (doors are now noticed), and if it finds a bed under a roof it moves in: home is
  beside that bed, its chest goes next to it, no hut is built, and at night it goes into the house and sleeps in that
  bed. Builders, Hermits and Nomads don't move in. Test: in one run it found a house, moved in ("moving into the village
  house with the bed at 686, 75, -152") and slept there; in others it didn't see a door and built a hut next to the
  village as before. It never breaks village buildings (above ground, built blocks are off limits).
- **Fixes found on the way:** thin blocks (doors, torches) seen past when re-checked were forgotten as "gone"; mossy
  cobblestone (taiga village foundations) filled the structure memory and pushed out doors (its importance is now low;
  the "noticed mossy cobblestone" spam is gone too); a chest is never put where a block above stops it opening; it
  looks round before choosing where to settle.
- **User-reported bugs (session 2, fixed):** snow layers (pillaring, placing and digging go through snow and grass like a
  player's clicks); a tree where the hut goes is cut down first; ore veins are finished (it steps closer) and the drops
  picked up; strip miners take a quick look into caves their tunnel opens instead of plugging and turning away.
- **Known:** drowning still happens now and then (water it can't climb out of at night, a flooded staircase); in some
  villages it doesn't find a door and builds a hut beside the village.

### User requests for step 3 (session 2), status
- **Caving first** (done, see above; user: "it should fully explore caves, go deep in them; it has the tools to defend itself, a higher
  chance of finding iron than strip mining, and it learns the land"): remembered cave openings, explore toward the
  deep unexplored parts, torches on its habit side as breadcrumbs, mine visible ore, fight cave mobs, turn back on low
  health/food/torches/pickaxe or the time; next trip continues. Personality sets how deep and how often; strip miners
  still strip mine. This changes the mining part of the design; record it in DESIGN.md as the user's decision.
- **Water drops** (done): it knows landing in water stops fall damage, but only plans drops of up to 8 blocks and only when it
  has seen the whole shaft. Plan: brave ones jump any height into water they can see; cautious ones stick to short
  drops or dig down.
- **Looting:** mineshafts (chest minecarts) and dungeons (spawner, chests) done with caving (cobwebs still just
  avoided). **Surface structure chests** (user's request, 2026-10-15): any chest it has seen that no player or Drifter
  placed (ruined portals, shipwrecks, temples, villages) is a priority goal ("loot a chest it saw", urgent when close or
  greedy); it takes what isn't junk, once. Not yet: each structure's own protocol from section 10.11 (a ruined portal's
  gold blocks and obsidian, diving into shipwrecks, temple traps).

### Step 5: settlement (farm, pen, organised chests; tested on sprinted runs)
- **Wheat farm** (`brain/activity/FarmActivity`, goal "tend the farm"): once settled with a bucket, a 9x9 plot near home
  on ground it has seen (it walks out to look if it hasn't), bumps and dips levelled, water in a dug hole in the middle
  (fills its bucket at water it knows, or goes to where its map says there's water), a hoe from the planner, tilling,
  seeds from grass, harvesting ripe wheat and replanting. With no seeds and no grass about it waits for the harvest
  instead of exploring (found in testing: it wandered off looking for seeds). `/drifter home` shows the farm.
- **Animal pen** (`PenActivity`, goal "look after the animal pen"): once it has a farm and carries wheat or seeds, a 5x5
  fenced pen with a gate facing home; animals follow it while it holds wheat (`world/AnimalTempting`), it leads two in,
  feeds them to breed (`UseOnEntityTask`), and shuts the gate. Staged test: a calf was born.
- **Organised chests:** it puts things into a chest that already has that kind first; a chest it couldn't get to no
  longer counts as full.
- **It keeps on it** (`Storage`): seeds (32), wheat (16), lapis (64), glass (16), an enchanting table it hasn't set up,
  besides the earlier kit (found in testing: the farm stood empty because its seeds went in the chest).
- **Drowning fixes (all from sprinted runs, using the new death "black box"):** unseen blocks over water now count as
  underwater when planning a route (a flooded tunnel looked like swimming on the surface, so the "at most 6 blocks under
  water" limit never kicked in); the out-of-air reflex finds a way through the water to air (it swam straight at the
  nearest air and stuck against a wall); dives cost more. A bridge it couldn't get to the edge for is avoided for 5
  minutes (it re-planned the same bridge all evening).
- **Debug black box:** with `/drifter thoughts log true`, the last 10 seconds of movement are written to the log if it
  dies, so every death in a test explains itself.
- **Results after these fixes (5 game days each, 6 random seeds):** 0 deaths in 6 runs (the batch before: 3 in 6),
  iron pickaxe in 5 of 6, a farm in 5 of 6.

### Step 6: diamonds (written, staged and sprinted runs)
- **Targets** (`Strategist.DIAMOND_TARGETS`): diamond pickaxe, then sword, then chestplate, leggings, helmet, boots.
  Miners jump to the diamond pickaxe and sword right after the iron pickaxe, bucket and shield (section 9 pacing);
  others after their iron armor. Stages: 6 settlement (farm, pen, full iron armor), 7 diamond (diamond pickaxe and
  sword), 8 enchanting (enchanted gear).
- **Where:** the sources table has a new file `drifter_sources/deep_ores.json`: diamonds at Y -51 (decision: just above
  the lava level; below Y -54 caves are full of lava), lapis at Y 0, gold at -16, redstone at -51. Mining trips carry on
  down the existing staircase from home.
- **Pickaxes on long digs (found in testing: the iron pickaxe wore out on a staircase down to the diamonds, then the
  spares, 150 blocks from home):** it uses a stone pickaxe on plain rock when it has one (the iron one is kept for ore
  that needs it; only very efficient Drifters don't bother), and deep trips take a crafting table and sticks along so a
  broken pickaxe can be replaced down there.
- **Sleeping bug (fixed):** sleeping counted as "immobile", which for a mob also stops its brain, so a Drifter that went
  to bed never got up (one slept for three days in a test).
- Known: on the sprinted runs so far it saw diamond ore at Y -26 to -50 but hadn't mined any yet when its pickaxes ran
  out (fixed since; the step 9 run will show it).

### Step 7: enchanting (written, staged tests passed)
- **Experience:** it has levels like a player (saved): it collects experience orbs it touches (orbs only fly to players,
  so it walks over them, like items; Mending repairs gear first), ore, mobs and smelting give orbs as for a player
  (taking a furnace's output pays out its stored experience). `/drifter brain` shows its level.
- **Obsidian** (`ObsidianActivity`, planner action "make obsidian with lava and water"): with a diamond pickaxe and two
  buckets, the player's bucket method: fill a bucket with water, scoop up lava from a pool it remembers (standing in
  reach but never right beside lava), dig a one-block hole in solid ground away from lava, pour the lava in, pour water
  beside it, scoop the water back up, mine the obsidian and pick it up from the hole. Without lava it knows of, it
  explores for some. Staged test: 4 obsidian in a row, then the table was crafted.
- **Enchanting table** (target after the diamond pickaxe and sword, before diamond armor): books from sugar cane and
  leather (sugar cane is now in the sources table), placed near home on flat ground with room round it for bookshelves
  (`EnchantActivity.librarySpot`). Then "some lapis".
- **Enchanting** (`EnchantTask`, goal "enchant its gear"): when it has lapis and the levels its bookshelves make worth
  waiting for (8 with none, up to 30 with 15), it uses the table's real menu through the player-hands helper and takes
  the strongest offer it can afford: diamond pickaxe, sword, diamond armor, then iron pickaxe and sword, bow. Staged
  test: "enchanted its diamond pickaxe: Unbreaking I, Efficiency I", then the sword; stage 8.
- **Bookshelves:** after the diamond armor, "a bookshelf" one at a time (3 books each), placed in the ring two out
  from the table with the cell between kept clear, a gap left on the side facing home to walk in (15, the most that
  count). Offers get better as the ring fills, and it waits for more levels to match.
- **Not yet:** the anvil (combining books, repairs, naming its sword).
- **Debug:** `/drifter xp <levels>` gives it levels (vanilla `/xp` only works on players).

### Step 8: houses (written, staged tests passed)
- **The house** (`BuildHouseActivity`, goal "build a house"): once in the iron age (Builders as soon as they're settled), a
  7x7 house 9-16 blocks from the hut on flat ground it has seen, clear of the farm, pen and enchanting table. Walls 3 high
  with window gaps in the middle of each wall but the door's (glass if it has some), flat roof, door facing home.
  Built the hut's way (`StarterHut` now takes a size and wall height): walls from inside, the third layer and the roof
  from a two-block pillar in the middle, the middle roof block last from below.
- **Style by archetype** (section 11): Builders and Homebodies build in wood (planks, log corner posts), Miners, Bandits
  and PvP Sweats in stone, everyone else in whatever it has (not dirt). Nomads, Hermits and Speedrunners don't build one.
- **Moving in:** when it's finished it breaks its bed in the hut, carries it over and puts it in the house; at night it
  goes into the house, shuts the door and sleeps there (the hut is left as it is).
- Tunnels and caving never dig into its own hut or house.
- Staged test (flat world, a Builder): finished house with log corners, glass windows, the bed moved in.
- `/drifter house` prints the house layer by layer (L logs, G glass, B bed); `/drifter home` shows its state.
- **Lighting the base** (section 10.15, a first piece of tier 5): in the daytime, with torches to spare (it keeps 16),
  it puts torches on the ground round home (14 blocks out) wherever there's no light, a few per visit; tidy Drifters on
  a neat 6-block grid, others wherever it's dark.
- **Habits from milestone 7 now in use:** it closes a door it opened behind it (always, only at night, or never, by its
  door habit); tidy Drifters pick a crafting table they put down away from home back up; a house uses its favorite
  wood or stone while it has some (then a mix, the patchy look of real builds).
- **Not yet:** pitched stair roofs, porches, walls and paths (tier 5), more rooms.

### User notes (session 2, 2026-10-14), to do after the step 9 bug fixes
- **Shelter later:** Drifters take shelter far too early; they should start heading in at about time 13000. Done: it
  starts thinking about shelter at 12000 (11000-13000 by caution, earlier by its walk home), urgent at 12800, night
  (beds) from 12542.
- **Caves:** in caves they shouldn't stop for the night; they carry on until they get back to the surface, then sleep.
  Done: underground on a mining trip the time doesn't end the trip or make it shelter; when the trip ends it walks home
  and goes to bed. Surface errands that start underground head up first.
- **Villager beds:** they may take a villager's bed and use it as their own. Done: the planner can "take the bed from
  the village house at ..." (a bed it has seen under a roof that isn't its own), then it carries it home.
- **Underground bases** are fine if a Drifter wants one, just not in an awkward position like a ravine floor. Partly:
  it no longer settles on ravine or pit floors (more than 8 below sea level); a dug-in underground base (Hermits,
  Miners' bunkers) is planned (needs its own blueprint).
- **Farms by personality** (user agreed): Nomads, Speedrunners, Bandits and PvP Sweats don't farm or keep a pen;
  Miners and Hermits only when food runs short (then they keep up the one they made); Homebodies and Builders are keen;
  the rest as before. Done.
- **Whole trees:** cut the whole tree (the leaves then decay and make room; trunks left floating look wrong). Done: once
  it starts on a tree it takes every log of it, lowest first, pillaring beside the trunk for the high ones.

### User requests, second batch (session 2, 2026-10-14 night): status
- **Its own strip mines aren't caves.** Done: it remembers the blocks it dug out (the latest 8,192, saved with it);
  those don't count as cave space, and its own tunnels are never "a cave it saw" to go back to.
- **Strip mining and caving combined.** Done: a strip miner that breaks into a real cave now explores it as fully as a
  caver would (it used to take a quick look of 8 spots), then goes back to its tunnel.
- **Long mining trips, crazy preparation.** Done: every trip except a coal run lasts a random 2 to 15 minutes
  underground. An iron trip collects iron for everything iron it still needs (pickaxe, bucket, shield, the whole
  armor set, shears, flint and steel), so it comes back up and crafts the lot. It packs for it: double torches, more
  food (16 food points), and pickaxes for the extra digging. If its time is up with what the plan needed, it heads
  back; the rest comes on the next trip. `describe` shows e.g. "go mining for raw iron (4/31)".
- **Tidying pillars and bridges near home, by personality.** Done: it remembers blocks it put down only to get somewhere
  (pillars, bridges; the latest 2,048, saved). Tidy Drifters (tidiness 0.4 and up) take them down within 25 to 40
  blocks of home (tidier means further), top first so nothing is left floating, in the daytime, a few at a time. Messy
  ones leave them. `/drifter home` shows how many are left nearby.
- **No hopping on the farm.** Done: no sprint-jumps or hops onto or off farmland (landing tramples it).
- **Finding Drifters.** Done: `/drifter list` lists every loaded Drifter with where it is and what it's doing (click a
  name to go there); `/drifter tp <name>` teleports you to it.
- **notRadish's death** (stuck under leaves running from a zombie, hit for 5 s): a Drifter that's running away but hasn't
  moved for 2 s while something right there keeps hitting it now fights back. Why it got wedged at that spot isn't
  known yet (the black box shows it held in mid-air 0.2 above the ground, pressed into a corner).
- **Later, not this milestone (user agreed):**
  - **World simulation round Drifters**: crops growing, mobs spawning and chunks generating round a Drifter far from any
    player, as on a multiplayer server (today the game only does that near players; the farm only grows while you're
    near). Big enough to be its own milestone after 9, or part of milestone 10. Recorded here; not in DESIGN.md yet.
  - **Bonemeal** on crops and saplings: with the whims (WHIMS.md) and the farming habits, later.

### Fixes from the user's game and batches 12-14 (2026-10-14, late)
- **Combat:** a baby zombie (faster than it can run), anything that hits it twice while it backs off, or being stuck in
  place while fleeing: it turns and fights (four Drifters in the user's world died running from baby zombies).
- **Fire and lava:** a water bucket at its feet when on fire (staged test: put out after 2 hits); unseen cells beside or
  under known lava count as lava, open cells beside it cost more; it never eats standing in lava.
- **Water:** sealed in under a ceiling, it stops swimming for air it can't reach, sinks and mines up standing (staged
  test: from drowning to full health); a stream carrying it toward a drop of 4+: a block at the edge at once (staged
  test passed); washed back again and again in a current: it stands on a block out of it and plans again. A pillar it
  gets carried off is replanned (a fatal fall).
- **Loops:** "pockets full" trips 120 blocks from home (307 times: full pockets now send it home from up to 512 blocks);
  junk and cobble past two stacks thrown out down a mine; mining prep steps capped at 3 tries; walling in for the night
  digs down one when the roof's step block can't go on (73 failed nights); putting things away from far off walks home
  first, then waits 2 minutes after a failure.
- **Planning:** lapis is never mined as an ingredient (it went to Y 0 for blue wool for a bed); ore under its feet gets a
  staircase down beside it (the planner and gathering disagreed: "doesn't know where to find any diamond"); ore dug
  through on the way is picked up (the user saw diamonds left behind).
- **Houses:** sites may have a few unseen cells and be up to 24 blocks out (a Builder on hills never found one; re-run on
  the same seed: house built). No night hunts underground (one drowned in flooded caves on a night hunt).
- **Batches 12-14:** Casual reached diamonds (stage 7) in batch 12; batch 14's Builder made full iron armor, shield,
  shears and flint and steel from its long trips (its stage then reads 4 because its iron pickaxe wore out). Deaths
  found in these batches (lava, a fall from a pillar in a current, a waterfall, a drowning) are fixed above.

### Step 9: the 10-day run (in progress; latest state 2026-10-14 evening)
- **Batch 7 (all fixes to then):** all three survived 10 days. Miner: diamond pickaxe and sword (stage 7), a stone house,
  a 52-block farm (this meets the milestone's "done when"). Builder: a finished wooden house, a 37-block farm all
  planted, iron pickaxe (it lingers in settlement, as the design says Builders do). Casual: stage 6 (farm, pen, full
  iron armor) and a house. Batch 8 was running when the session ended (logs in the session scratchpad, `t8_*.log`).
- **Fixed after batch 7:** pen site on hills (32/49 cells, clear of the house), sneaking when pillaring off a block
  that opens (its own crafting table under it broke the hut roof every day), no stations in the middle of the hut or
  house, the out-of-air reflex runs up to 30 s and can mine up through a ceiling (a fully sealed underwater pocket is
  still not solved: mining while floating is too slow).
- **User's own test (new world, 8 random Drifters, latest jar installed 18:28):** two drownings on the 18:02 build (both
  addressed by later fixes). Their game log is `ModrinthApp/profiles/DRIFTER TEST/logs/latest.log` (thought logging on).
- **Next:** read batch 8 and the user's log; keep fixing what stalls runs; then call milestone 8 done if most runs
  reach diamonds with a base.
- Fresh Drifters with nothing, random seeds, 10 game days each (`sprint` lines), a Miner, a Builder and a Casual.
- **Fixed before it:** `/drifter spawn` from the console put it on the world spawn's block corner, sometimes inside a
  tree, where it suffocated; it now picks the nearest open spot, and like a player it's nudged out of blocks it
  overlaps (`LocalPlayer#moveTowardsClosestSpace`). With thoughts logged, a walk that ends "stuck" traces itself.
- **Found in the first 10-day runs (fixed):**
  - A furnace job with nothing to take pulled it back to the furnace 37 times (it now looks inside first).
  - It froze to death in powder snow (now off-limits without leather boots; a reflex breaks its way out).
  - The house needed a perfectly flat 9x9 it had seen; it now takes slightly bumpy ground and levels it.
  - A one-block-deep gap in its path made it re-plan the same bridge forever (27 times in one run).
  - In a snowy taiga with only rabbits about it went hungry for days: rabbits are now food (and bread from its wheat).
  - In a spruce forest it cut the low logs of every tree and then had no wood: for logs out of reach it now pillars up
    beside the trunk, cuts what it can reach, and digs its pillar back down.
  - Walking down a hillside it sprinted off a 3-block drop and overshot the landing onto the next edge (a fatal fall):
    on drops of 2+ it walks, and lets go of the keys over the landing block.
  - It settled on a ravine floor (open to the sky) at Y 32; a surface errand then took it through the caves into lava.
  - Night mining from its shelter tried the same stone 114 times ("can't see or reach it"); the tunnel now turns.
  - With no animals about it starved its mining trips short (32 times) and never got the iron for a bucket, so no farm
    either: without a bucket it now farms by a pond or river near home.
  - Sugar cane (or a sapling) in a wall cell of its hut: "that spot is already taken" 16 times; it now breaks it first.
  - A crafting table or furnace it stood next to but still couldn't reach was tried again and again; it now forgets
    that one and puts down a new one.
  - A log behind leaves it couldn't hit: it breaks the leaves in the way, and leaves a log alone after a second miss.
  - The test server only kept the area round spawn loaded, so a Drifter that walked off froze (test scripts now keep
    the area round the Drifter loaded, as a player would).

## Milestone 9: Disposition and PvP

**Status:** In progress (started 2026-10-14, late, after milestone 8's code was complete; milestone 8 waits on the
user's play to be called done). *Done when* every event in the section 14 table moves Trust as specified.

### Plan
1. **Relationship records** (done): per player Trust, Fear, Familiarity, grudges, debts, strength estimate, known base,
   last seen; a disposition rolled from the archetype (section 17 table; a very vengeful neutral is a Grudge-holder);
   the stranger baseline from Sociability (-20 to +20) shifted by disposition; each day Trust drifts back by
   Forgiveness (5-30% of the way), grudges fade by Vengefulness (severe ones of a vengeful Drifter never do), Fear
   fades to what their gear earns. All saved. The event table lives in `social/SocialEvent` (exact numbers, daily caps
   for broken blocks and crouch-spam, ranges for gifts and gear). Debug: `/drifter social`, `social show <player>`,
   `social set <player> trust|fear|familiarity <n>`, `social event <player> <event> [amount]`,
   `social disposition <id>`.
2. **Seeing the events** (done; the table checked headless with `/drifter social event`, all 19 events move Trust and
   Fear exactly as specified, caps and ranges too; noticing them in play needs a real player, see TESTING): hits (once and backs off, repeatedly, nearly kills, kills), its blocks broken, chests
   opened (seen or heard) and valuables taken, farm animals killed, fire or lava near its base, a drawn bow aimed at it,
   being sneaked up on, gifts, crouch-spam, a mob killed that was attacking it, being saved from death, peaceful time,
   a friend Drifter killed, better gear. Pets: no pets yet (the event is in the table, nothing triggers it).
3. **Behavior modes and the telegraph rule** (done, needs a player to test): Watching, Avoiding, Tolerating, Defensive, Warning; no hostility toward a
   player who never wronged it without a warning first; never hostile on Peaceful.
4. **Hostile modes** (done, needs a player to test; opener arrows, ambush spots and the duel finesse come with step 6 polish): Territorial, Predatory (Bandit, Opportunist), Vengeful (hunts, goes to their known base), Fleeing;
   stalking, waiting for an opening, opener, duel, disengage, looting.
5. **Friendly modes** (done, needs a player to test): greeting, gifts, barter, follow me, helping against mobs, warning of danger; non-verbal language.
6. **Chat lines, multiplayer sharing, sleep blocking, manhunt (hunter) test command** (done, needs a player to test).
   Sleep blocking uses Fabric's `EntitySleepEvents.ALLOW_NEARBY_MONSTERS` (no mixin needed). The manhunt hunter gets a
   tracking compass that gives only the runner's direction, respawns at world spawn with nothing.

**Not done in milestone 9 (smaller parts of section 14, for later polish):** opener arrows from cover and ambush spots
(corners in a mine, portal exits, base entrances), ender pearls and pillaring in duels, griefing (config-gated, off by
default), the "dropping a peace offering when fleeing" config, pets (no pets yet), Drifter-to-Drifter friendships
(KILLED_FRIEND counts witnesses for now; groups are milestone 12), "remembered after respawn" for Drifters killed by a
player (Drifters don't respawn outside the manhunt yet).

## User wishes for later

- **Different house plans** (user, 2026-10-16): more than the one starter hut and one house; Drifters should build
  different houses (by personality, archetype, biome or materials). Later (milestone 12 polish or its own step).
- **Config screen in Mod Menu** (user, 2026-10-15): the settings in `config/drifter.json` (sight, path range, FOV, chat
  lines, shared reputation, thought logging) should be editable from an in-game screen through Mod Menu. Plan: an
  optional client-side screen registered with Mod Menu's entrypoint (any config library only as an optional
  dependency; ask before adding one). Keep every tunable in `DrifterConfig` so it can show up there.
- **World simulation round Drifters** (crops, spawning, chunk generation far from players) and **bonemeal**: see the
  second batch of milestone 8 requests.

## Milestone 10: Offscreen and performance

**Status:** Done (2026-10-15), waiting on the user's play test. *Done when* 10 Drifters stay within the per-tick budget (about 3 to 5 ms
together). User's decision: **hybrid** (DESIGN section 22, "Hybrid simulation").

### Plan
1. **Hybrid simulation** (done; staged test: 3 of 4 far-apart Drifters kept running with no player anywhere, wheat by
   one of them grew; mobs can't spawn or stay away from real players in vanilla, recorded as a limit): the top N Drifters (config, default 3; manhunt hunter always) keep chunks loaded round them
   like a player (tickets), with random ticks and mob spawning near them (a mixin on the "player close enough" check),
   chosen by activity with stickiness. `/drifter sim` shows who is simulated.
2. **Detail tiers and timing** (done): `/drifter perf` shows ms per server tick for all Drifters together, split by
   system (senses, brain and its parts, tasks, path search), with the slowest single call of each and what that
   Drifter was doing, plus how many are in each tier. Tiers (`entity/DetailTier`): Full (a player within 48 blocks or
   looking its way), Reduced (none within 48: no mannerisms, re-thinks every 1.5 s), Far (none within 128: also 4 eye
   rays instead of 6, re-thinks every 2 s). Movement, path following, danger checks and combat run every tick in every
   tier; a fight or flight is always Full. `/drifter tier auto|full|reduced|far` forces the nearest one's tier (debug).
   Kept mild on purpose: measuring showed the systems the tiers cut were cheap, and the real costs were elsewhere:
   - **Measured** (10 Drifters on one mountain seed, 12 game minutes): before 4 to 9 ms a tick on average with single
     ticks up to 112 ms; after 1 to 4.5 ms on average, worst single Drifter call about 5 ms.
   - Known-world scan: stops checking a block kind once it has found one (it re-checked every known block within 24
     blocks once a second, growing as it explored).
   - Brain decisions: staggered across the second (Drifters loaded together all decided on the same tick); a goal with
     no urgency isn't planned; each goal reuses its plan for up to 5 s while nothing it plans from changed (carried
     items, known block kinds, animals, stations, home chests); the planner only tries the kinds of log or plank it has
     or knows of when there are any (one food plan was 7,224 sub-plans, now under 1,000).
   - Path search: budget by time (3 ms a tick for all, 1.5 ms per search) instead of 1,500 nodes per search.
   - Home-site search: block counts bucketed by area, footprint cells worked out once, hopeless spots dropped early,
     air skipped, block-kind checks cached (was up to 40 ms in one tick).
   Fall deaths found in these runs, fixed: sprinting into a 2-block step-down (now drops to a walk first and steers
   back in the air); creeping along an overhang then standing up (the crouch safety net now also catches a very slow
   slide and an overhang); thinking on the move at a cliff or in mid-air (now stops walking); jogging down a staircase
   onto an unseen "step" that was a 60-block drop (now walks, not sprints, toward unseen step-downs and doesn't steer
   onto them in the air).
3. **Catch-up** (done, `world/CatchUp`): a Drifter saves when it was last running; when its area loads again after
   2+ minutes away, it fast-forwards that time (capped at 3 days) in quarter-day slices. Each slice: gathers what a
   Drifter of its stage gathers (per in-game hour of work, times its archetype's multipliers, with randomness; data
   file `data/drifter/drifter_catchup/default.json`, replaceable by datapacks), eats from what it carries (cooked
   first; short of food it goes hungry), wears its tools (one that wears out is gone), burns torches, smelts raw iron
   and food with the fuel it has, then crafts toward its next targets with the real recipes when the plan for one is
   only crafting and smelting (so it moves up stages slice by slice), and runs a small risk of dying (everything it
   carried lost). What doesn't fit goes into its home chests if they're loaded, else is left behind. Afterwards: back
   at home if its home is loaded, healed if fed, deciding afresh; a thought sums it up ("caught up on 1.3 days away;
   gathered ...; made ...; now stage 3"). `/drifter catchup <hours>` runs it on the nearest one (debug). Tested: a new
   Builder away 1.3 days went from nothing to stone tools, a furnace and iron ingots. Not done: building queued
   blueprint steps while away (needs base chunks to load one by one; left for later). **Update (2026-10-16, user's
   request):** catch-up now builds the shell of its starter hut (floor, walls, roof) with the blocks it has, only at its
   own home while that's loaded, never over a player's block or where something stands, door gap left open; it
   furnishes it (door, bed, torch) for real next time it's about. Tested: 0.5 days away built 55 blocks, shell
   complete. The bigger house isn't built during catch-up yet.
4. **Offline progression and abstract travel** (done): `/drifter config offline off|game_time|real_time` (default
   game time). Real time: the real-world time is kept with the world (on stop and every minute), and the gap since
   becomes extra catch-up (own cap, 2 in-game days; negative or absurd gaps ignored) for every Drifter saved before
   the restart (`world/OfflineClock`). Abstract travel (`world/DrifterTravel`): a Drifter that plans a route to a spot
   outside the running world, with no player within 128 blocks, not kept running and not the manhunt hunter, leaves the
   world as a traveler (saved with the world) instead of freezing at the edge, and turns up at its destination once
   that area runs and the walk would be over; catch-up then counts from its arrival. `/drifter sim` lists travelers.
   Tested: one walked off, showed as traveling, appeared at its destination when that area loaded.
5. **Budget test** (done): 10 Drifters on a mountain seed, no player (all "far"), 12 game minutes: 1.2 to 3.7 ms a
   tick on average after the first seconds (6 ms while all 10 start at once), worst tick 12 ms, no deaths. Within the
   3 to 5 ms target.

**100-Drifter test (user's request, 2026-10-15 evening):** 100 Drifters packed into one loaded area: first 30 ms a tick
for the Drifters (path search took 21 of it: the "every search gets a few nodes" guard added up across 100 searches);
fixed (a search that misses out waits up to a second for its turn), now about 10 ms for 100 and 13 ms for the whole
server, a quarter of a tick. Spread across 2000 blocks with no player: they freeze or travel off, as designed (only the
3 kept running stay active); found that kept-running ones teleported far between updates kept their ticket at the old
spot and froze (fixed: the ticket moves to where the Drifter is when its area unloads).
**`/drifter list` and `/drifter tp` reach every Drifter** (user's request; `world/DrifterRegistry`, saved with the
world): the list shows the loaded ones and, greyed, where the others were last (or set off from), with how long ago;
clicking one (or `/drifter tp <name>`) takes you there and it loads in. `/drifter spawnmany <count> [radius]` (user's
request) spawns a crowd spread round you on dry ground; each spot runs for 5 s after spawning so the 3 busiest can be
picked to keep running (without that they unloaded before the pick and none were kept).

Also from these runs: movement keys are aimed again after the head turns each tick (`Steering.reaim`): keys picked
before a fast turn sent one the wrong way off a ledge mid-jump. Trace lines now show the facing (yaw).

Batch 23 (random seeds): Miner stage 7, no deaths; Builder and Casual both stuck at stage 4 with no deaths, each by
flowing water: the Builder's home was next to a stream it had to swim against on every trip to its furnace ("the
current keeps pushing it back" 213 times; smelts and house work timed out), the Casual's mine flooded. Two fixes:
routes now cost more through flowing water (a current it can see), so it walks round or bridges when that's not much
longer; and digging in for the night on the edge of a block over a step now picks the block that's really holding it
up (it tried to centre over thin air, "couldn't step to the middle of the block" 125 times in one night).

Findings to come back to (batch 22, random seeds, day 1, not from these changes): a Builder that spawned where there
were no trees in reach explored all day across water and was killed by drowned; a Casual ended up at the bottom of a
flooded cave pocket 10 blocks under its new home and drowned there (the hut-building step kept trying to mine stone
beside it, and the drowning reflex only bobbed it up a little, never to air). Not reproducible with the same seed
(personalities are random).

## Milestone 11: Nether

**Status:** In progress (started 2026-10-15). DESIGN section 19. Proposed *done when* (the roadmap gives none): on a test server, a
stage 8 Drifter builds and lights a portal near its base, packs for the trip, goes through, walls in the portal,
brings back blaze rods (or at least quartz and gold) and gets home alive, on most seeds.

### Plan
1. **Portal** (done, staged test passes; `brain/activity/PortalActivity`, `brain/PortalFrame`): once it enchants
   (stage 8) its targets add 10 obsidian and flint and steel (before diamond armor and bookshelves). With them, the
   goal "build a Nether portal" picks a clear spot it has seen 6 to 16 blocks from home (four solid dry ground blocks
   in a row, 4 by 4 of air above, ground to stand on in front) and builds it the way players do without corners: digs
   out the two middle ground blocks and fills them with obsidian, the lower two blocks of each column from the ground,
   then pillars up two blocks in front (its eyes must be over the face it clicks) for the column tops, a cobblestone
   prop on each top corner, the top row against the props, lights it with flint and steel from the pillar, digs its
   pillar back down and remembers the portal. A half-built frame is saved with its home and finished later. Flat-world
   test: built and lit in 11 s. Completing a ruined portal is left for later.
2. **Packing list** (done; `Strategist.NETHER_PACKING`, `packedForNether`): once its portal is lit, targets for a gold
   armor piece (boots or helmet, from mined gold), 64 cobblestone, 20 cooked food and a spare pickaxe; the trip also
   needs flint and steel, a sword and a shield. Not yet: bow and arrows (no source of string: spiders; a target it
   can't reach would block every target after it) and gold ingots to barter (step 6).
3. **Through and arrival** (done, staged test passes; `NetherTrip`, `NetherGoals`, `EnterPortalActivity`,
   `NetherArrivalActivity`): "go to the Nether" when packed, fit, near home, by day, at most once a day for now. It
   walks to its portal, steps in and waits a player's 4 s (`NetherPortalBlockMixin`; mobs normally go at once; its
   portal cooldown after a trip is a player's, 10 ticks). The trip is kept with its brain (the game rebuilds the
   Drifter on the other side). In the Nether: notes the portal home, walls it in with cobblestone (25 blocks: the
   exits' ends, a far wall two out with a door gap, a roof), looks round; only fighting, fleeing, eating, gear and the
   trip goals run there. Home after 30 s for now (or when hurt or hungry): steps out of the portal and waits for the
   portal to be ready again (you must step out of a portal you came through), back in, home ("back from the
   Nether"). Paths never go through portals by accident. Found on the way: the game stops ticking every creature in
   a dimension nobody is in for 15 s, which froze kept-running Drifters there (now a kept-running Drifter counts as
   someone being there).
**Bows** (user's request: they had fallen through from milestone 6; done as part of milestone 11, staged tests pass):
- String from spiders (hunted; they're fair game for a hunt now) and cobwebs (a sword cuts them): data file
  `drifter_sources/early_game.json`. Arrows from flint, sticks and feathers (the planner already had those).
- A bow and 32 arrows are on the Nether packing list.
- Shooting like a player (`ShootTask`, `BowItemMixin`): takes the bow out, holds use for a full draw (1 s; 0.6 s when
  the target is within 10 blocks), aims with its head where the target will be when the arrow arrives and a little
  high for the drop, lets go once the crosshair is on it. Vanilla's bow only fires for players, so a mixin runs the
  game's own bow code for a Drifter: same power for the draw time, arrow use (Infinity works), bow wear, enchantments,
  crit on a full draw. Aim error per shot from Skill, distance and target speed.
- When: a flier (ghast, blaze, phantom), a creeper 4+ blocks off, or anything hostile 6+ blocks off that's coming at
  it, with a bow and arrows ready; up close the sword. With a bow it watches hostiles out to 24 blocks (fliers 40),
  so it can shoot before they arrive. After 3 bow attempts that didn't end a fight it sticks to the sword (except fliers).
- Tested: a husk killed with 3 arrows before it reached the Drifter, a creeper shot before it got close, a ghast
  brought down with an arrow; a Drifter told to get string hunted spiders for it.
- Found on the way: the far detail tier had switched off the glance toward a sound or a creature along with the
  fidgets, so a busy Drifter with no player near never turned to look at what was shooting at it. Those glances
  now run in every tier. Also fixed (user's report): the arm didn't swing when placing blocks (on the server a placed
  block reports "consume", not "success").
- New debug commands: `/drifter plan <item> [count]` (how the nearest Drifter would get it) and
  `/drifter get <item> [count]` (sends it to get it).

**Step 5 first cut (blaze rods; in testing):** far sight notes Nether bricks as a "fortress" landmark; "get blaze
rods" (in the Nether, after arriving) explores for a fortress, goes to it, looks for its spawner, waits by it (the
fight goal and the bow handle the blazes), picks up the rods; home with 6, when hurt or hungry, or after 12 minutes.
Stage 9 ("Nether") once it has blaze rods. First Nether trip tests found and fixed: exploring thought it was
underground (rock overhead everywhere) and walked to its overworld home's coordinates, then staircased to the
bedrock roof (no surface-seeking in a roofed dimension; unseen cells there aren't assumed rock); it ran on while
replanning and dropped into lava (it stops when lava is a step or a short drop away). Still to do: it explores in
random directions and can only see a fortress from fairly close in the dark, so finding one is slow (next: search
along north-south lines, as fortresses run, Knowledge-gated).

**Nether survival round 2 (2026-10-15 night):** a three-seed batch of full trips (`nbatch.sh`), run after each fix.
Fixed: lava-lake shores and drops into lava count as "avoid" for routes, no pillaring with lava within two blocks
(a jump on a lava lake's shore knocked it in); exploring in the Nether stays 8+ blocks above the lava sea; it steps
out of fire blocks at once (a reflex; it stood in a Nether fire until it died); in the Nether it never steps down onto,
or into, a block it hasn't seen, and unseen cells cost routes much more there (it stepped down onto unseen lava); a
put-out portal home is relit with flint and steel; a blaze it has seen leads it toward the spawner; the portal memory
label matches what its eyes use (it kept thinking its portal "had changed"); a portal stand spot a step away is
stepped onto directly (it stood one block short, the spot marked "lava flowed here", until the lava got it). Latest
batch: 2 of 3 alive (one came home), the third (seed 424242: its portal comes out beside a lava fall) still dies.
No blaze rods yet: fortresses are found, spawners not.

**Nether trip testing (2026-10-15 evening), still in progress.** Fixed so far: never routes through obsidian or a portal
frame (it broke its own portal home digging out of the room it had walled round it); backs well away from lava that
keeps flowing at it (it blocked the same lava fall seven times until it ran over it); a failed trip home isn't retried
instantly (it tried four times a second for minutes); knowledgeable Drifters search for fortresses in north-south
lines. Seen in tests but not yet solved: lava deaths near lava falls and lava lakes (seed 424242's portal comes out next
to one), and once a spot it couldn't route out of on a soul sand slope (being looked into with `/drifter cells`).
Fortresses do get found (seed 2024: spotted one 55 blocks off and headed for it). New debug command: `/drifter cells`
(how routing sees the blocks round the nearest Drifter).

**Fortress search round 3 (2026-10-16).** A route that fails with no first step now writes the cells round the
Drifter to the log (`[Drifter] <name> has no route from where it stands`, rate-limited; `/drifter cells` shows the same
view). That caught the "stuck for good" spots: (1) on the edge of a soul sand block it planned from the air beside it
(fixed: a raised block beside counts as what it stands on); (2) on top of a short pillar of its own it never stepped
off, because in the Nether a step down needs ground it has seen, and it had never looked down (fixed: before giving up
on a route with no first step it looks down round itself for 1.5 s, then tries again letting itself use the blocks it
keeps back); (3) it bridged out over the lava sea exploring until it had no blocks left (fixed: in the Nether it keeps
16 blocks back, not 6). Also: it found a blaze spawner and waited by it 10 minutes with no blaze, because a vanilla
spawner only wakes for a player within 16 blocks: spawners now wake for Drifters too (`BaseSpawnerMixin`; dungeon
spawners in caves wake for mining Drifters as well, the same danger a player has). It crouches when standing still on
magma (it burned to death standing on magma while replanning) and replans at once when it ends up 2+ blocks below
its route. A goal that fails 3 times in a row now rests (15 s, then 30 s, doubling to 4 minutes) instead of being
picked again every few ticks (on flat test worlds, "gather cobblestone" failed 1,851 times in a row). Fights and
escapes never rest. From the user's world: running away no longer steps into powder snow (a creeper caught one sunk
in it), and running from skeletons it heads for cover (a way that puts a block between them) when there's one within
a turn of straight away (three died shot in the back on a snowy mountain).
Batch results, six seeds: before these fixes 0 deaths, 2 of 6 home, 96 stuck-route logs; after the stuck fixes 1 death
(magma, since fixed), 5 of 6 home, 1 stuck-route log, first spawner found.
Later the same night, from the user's game and a replay: aiming its bow, it drops it and turns on anything hitting it
from close by (a zombie killed one from behind while it aimed at a creeper; the one hitting it is now its main target
over anything further off); a portal build started from higher ground elsewhere (its hillside shelter) walks over
instead of failing at once, and portal failures say which block it couldn't reach; a Nether trip once begun is seen
through (night falling or a player coming to look had pulled it out of its portal twice; it still gives up after 2
minutes); routes never cut diagonally across the corner of a hole (it fell 19 blocks doing that at a run). Test
batches now run with mob spawning on (ghasts, piglins, spawners as in a real game). With spawning on, the first batch lost 5 of 6
(blaze fire, lava, a fall); fixed from those: its shield reflex now sees blaze fireballs as soon as they're fired (a
fireball starts slow and speeds up; it used to be ignored until too late); finding the spawner no longer fails at once
(walks to the fortress had used up the walk count); a trip home is kept up once begun (it turned back to search when the
reason to leave wore off); sneaking holds it on ladders and vines as it does a player (a route cut off mid-climb slid it
into lava); routes along the lava sea's shore cost more; on a pillar block it counts as arrived anywhere on the block
(it landed a little off the middle, never "arrived", and the stuck check strafed it off), and it never does the
unstick strafe on its own pillar or bridge.

**Blaze fights (2026-10-16, staged tests).** New test scripts put a geared Drifter by a blaze spawner on a nether-brick
floor (`nblaze*.txt`) and in a real fortress (`nfort.txt`, seed 2024). Found and fixed: `/drifter giveall` only reached
Drifters in the dimension you were in (it skipped ones in the Nether); a shield in the off hand now halves how much
shooters (blazes, ghasts, skeletons) count, so a geared Drifter fights a spawner's blazes instead of running; a blaze
glows for three seconds before its burst, so while one is lit (and a little after) it puts the bow away, takes the
sword and holds the shield up, and shoots in between (a fireball seen coming is too late to block, and right-click
uses the bow in the main hand first, so the shield never came up); the "incoming fireball" reflex also switches off the
bow to block; every step down in the Nether is walked, never sprinted (it sprinted past a step into a lava hole); stuck
in a lava pit with walls too high to jump, it pillars out (its jumps in lava reach almost two blocks); running away
checks two blocks ahead for lava. The debug trace now shows what's in hand, whether it's blocking, and the reflex in
charge. Still open: several blazes at once hit it from the sides (a player fights them in a corridor or kills them as
they spawn), and the fortress test still ended in a fall and fire.
Then, in a closed nether-brick room with a blaze spawner (`nroom*.txt`, like a fortress spawner room): **first blaze
rod** (1 of 6, then it died). Fixed after that: close blazes get the sword; it waits within 4 blocks of the spawner so
new blazes appear in reach; blazes are fought on sight during the rod hunt (it stood "waiting for blazes" while one shot
it); in melee the shield is held through the glow of the blaze it fights (it raised it for a tick or two between swings,
and a shield only blocks after a quarter second) but it still strikes when a full swing is ready; with a shield and
above 8 health it stands and fights blazes instead of running (they fly after it and shoot it in the back); running
away looks for cover from blazes too. A later room run fought for over a minute without a hit. **Next:** its bow is too
slow on blazes (7 shots in 50 s: it waits for a steady aim on a bobbing target, and re-draws after every guard), so it
kills too few before the damage piles up; eating between bursts; a real fortress run end to end.
Later the same night: the bow accepts an aim anywhere on the target's body, loosening the longer a full draw is held
(the room test then got 2 rods); the fortress search sweeps out from the portal both ways, near ground first (it had
walked one 384-block line south while the fortress was 60 blocks north); a "stray portal" reflex steps it out of a
portal it didn't mean to use (one went home 8 seconds into its search); with lava spreading in from every side it
pillars two blocks up out of its way; a route may dig one block down into seen rock even with the floor under it unseen
(the walker looks before it steps; it was stuck up on the rock over its portal with no other way down); in the Nether it
stops to think instead of running on while it plans (it ran off a ledge into the lava sea mid-plan); badly hurt with no
way home just now, it walls itself in, eats and rests until healed ("hole up to heal", new goal `nether_holeup`).
Batches of 6 to 7 trips with mob spawning on now lose 0 to 1.
**User decisions (2026-10-16):** diamond chestplate, leggings and helmet before the Nether (they used to come after it);
Nether food is steak or cooked porkchop only. Also new: arrow kills count as a player's (blaze rods only drop for a
player's kill; the bow kills had dropped none); piglins go for a Drifter not wearing gold, and its gold piece goes on
first on a trip; looting in the Nether (fortress chests); digging with no fitting tool uses an empty hand; at a fortress
it walks the parts it has seen, highest first, to find the spawner; rods are banked (it goes home) after nearly dying
twice; a stall watchdog logs and breaks any 1-minute stall on a moving goal (fighting, shooting and waiting don't count).
The stalls it caught were soul sand under the fortress: sneaking there (for lava beside it, or to centre for a pillar)
left it all but still, now no sneaking on soul sand for those. Near a drop (a fortress bridge), close blazes get the bow
instead of a step in with the sword (one was knocked off a bridge into the lava sea). With diamond armor, ordinary trips
are safe (0 of 6 died); the fortress on seed 2024 (over the lava sea, next to the portal) is still deadly about half the
time. Rods come home now (banked after holing up).

**Steps 5 to 7 finished (2026-10-16, user: "do 1 to 3 then mile 12"):**
- Nether goods: quartz, nether wart (ripe only) and glowstone are taken when some it has seen is within 16 blocks and
  it's short (16, 8, 16); a passing chance, so they may break into the blaze hunt. Staged test: 2 quartz, 4 glowstone
  dust, 6 wart, then back to the hunt. Sources in `drifter_sources/nether.json`.
- Bartering (step 6): wearing gold, with gold ingots and a calm piglin about, it walks up, tosses one ingot, waits out
  the piglin's admiring and picks up the trade; up to 6 at a time, every few minutes at most. Gold ingots (8) are on the
  packing list but not waited for. Staged test: 4 ingots for spectral arrows, leather and nether bricks. Found on the
  way: vanilla's gold check read a Mob's own (empty) armor list, so piglins never saw a Drifter's gold; Drifters now
  report the armor they wear.
- Coming home with the haul (step 7): rods, quartz, wart, glowstone and barter loot have no keep amount, so "put things
  away at home" stores them. Rods stored at home now count toward the 6 (the trip goal and the next trip), and with 6 in
  all it makes no more trips for now.
- **Catch-up fix (user's report, 2026-10-16):** Drifters that set off at the start of the world came back days later at
  level 0, no armor, no hut. Causes: catch-up was capped at 3 days; it only crafted toward the very next target, and the
  first night's (a shelter, a bed) need trips, so it never got to iron tools or armor (14 iron ingots carried); a Drifter
  that set off never settled, and the iron age waits for a home with a chest; no experience was given. Now: 10 days cap,
  targets that need a trip are passed over, a homeless Drifter a day in settles where it is (hut shell and a chest, from
  a chest it carries, 8 planks or 2 logs), and experience at 4 a work hour. A 10-day test from nothing: settled, hut,
  chest, full iron armor, shield, diamond sword and pickaxe, stage 7, level 18. `/drifter catchup` now takes up to 240 hours.
  Then (user's requests): mob loot from night hunts (rotten flesh, bones, string, arrows, spider eyes, gunpowder, ender
  pearls rarely), animal loot (chicken, porkchop, feathers, leather, wool; meat cooked), a bow once it has string,
  diamonds a little higher late on, and **structure loot**: now and then (0.6 a day, nomads 2.2x, homebodies 0.4x) it
  goes through a structure's chests, rolled from the game's own loot tables, the rarer kinds only after enough days out
  (villages from day 0, dungeons 1, mineshafts and shipwrecks 2, treasure and temples 4 to 6, a mansion 8). What it
  doesn't need to carry goes in its chests at home (materials stay on it, so it keeps crafting), with another chest when
  they're nearly full (up to 4). Death risk while away halved (1% a day). Three 10-day tests: huts with 2 to 3 chests of
  180 to 274 items (a heart of the sea, smithing templates, music discs, saddles, name tags, emeralds), stage 7, level 17.
  More from the user's look round the caught-up huts: armor had gone in the chest (stored as "spare" before it put any on;
  now it puts on its best armor and shield each slice before storing), a hut built round a tree (it now settles on the
  nearest spot within 10 blocks where the whole hut fits with no logs, leaves or water), and no doors, no furnace, no bed
  (it now finishes the hut: an oak door, its crafting table and furnace in the corners, made from planks or cobblestone
  if needed, and from the second day a bed from wool or string). Extra chests go on free floor cells.
- Still open (polish, not blocking milestone 12): fortresses over the lava sea kill about half the Drifters that go
  there; ghast side-walls when bridging; biome preferences.
Then: fleeing blazes or a ghast in the Nether becomes walling in on the spot; at 6 hearts or less under fire it holes up
before anything else (fighting included) and stays until healed; it centres on its block first, builds walls bottom-up
with a block under each one over a drop; fireball dodges only onto safe ground (one dodged into lava); fire that appears
on its route makes it replan (it walked home through two fresh fires); each kill is logged ("got it (attack the
blaze)"); the spawner walk count resets each time it gets there. Results: the spawner room gave 3 kills, 3 rods and it
survived; **first rods on a real trip** (seed 2024: 2 rods, then it died); ordinary trips are safe (5 of 6 home in the
last batch, the sixth died at the fortress). All its kills so far are with the sword. Still to do for step 5: enough
kills for 6 rods before the damage wins (blazes drop a rod only half the time), then steps 6 and 7.

4. **Getting about:** lava seas bridged sneaking (side walls with ghasts about), no water clutch there (Knowledge:
   a Newbie tries once), soul sand, magma and fire handled, biome preferences (warped forest over soul sand valley and
   basalt deltas).
5. **Goals there:** blaze rods at a fortress (from cover, shield up), nether wart, quartz, glowstone, gold (checking
   for piglins first).
6. **Piglin etiquette:** a gold piece worn, no chests or gold mining in their sight, bartering by tossing gold, away
   from brutes and bastions, never hitting zombified piglins.
7. **Coming home:** back to its portal when full, low on supplies, hurt or worn, or done; then stores the haul.
8. Later (veterans, if time allows): ancient debris strip mining, netherite upgrades, linked portals (1:8).

## Milestone 12: Groups, modpack factions, camps, config polish (started 2026-10-16)

Plan (DESIGN.md section 21, section 4 "Optional world-gen camps", section 23 config):
1. **Drifter to Drifter:** relationships with other Drifters (Trust, Fear, Familiarity, as with players): first meeting
   from both temperaments, time near each other, rivalry over the same area, help in fights, hits.
2. **Groups:** Trust 50+ both ways forms a group (up to 4): leader (Skill or Familiarity), shared base and chests, shared
   memory ("told": dangers, resources, player reputations), group reputation, joint activities.
3. **Team wars:** group names and banners, tension between groups, skirmishes, raids, truces (configurable caps).
4. **Modpack factions:** undead, illagers and spiders already treat Drifters like players (milestone 6), piglins too
   (milestone 11); optional Mob Control registration if installed.
5. **World-gen camps:** an optional small Drifter camp structure (abandoned, or occupied with a Drifter at stage 3 to 5).
6. **Config polish:** the in-game settings screen through Mod Menu (user's wish), every setting there.

**Done so far (2026-10-16):**
- Step 1: `social/DrifterBonds` keeps a relationship per Drifter it knows (as with players). Meeting on first sight, trust
  from both temperaments; time together builds familiarity and trust (homes within 64 blocks: rivals, trust falls);
  killing what attacked another Drifter +15; a hit -5 per damage and fear. Bitter rivals (trust -60 or less) count as
  enemies on sight; other Drifters are fought like players (footwork, shield, bow). Tested: two at -80 squared off.
- Step 2 (first part): friends both ways (trust 50+) form a group (`world/DrifterGroups`, saved with the world, up to 4),
  named after the leader (more Skill leads); members move into the leader's home (its chests too); within 8 blocks
  members tell each other about dangers, ores, resources and landmarks ("told" memories); a wrong to one member costs the
  player half as much trust with the rest; members defend each other (a mob going for a member within 16 blocks counts
  as coming for them). Tested: two friends teamed up and one moved in. Still to do in step 2: joint mining trips.
- Aggression settings (user, 2026-10-16): toward players and toward each other, 0 to 100 each (0 never hostile, 100
  hostile on sight, 50 as before: a shift of the trust strangers start at), on the screen and `/drifter config
  aggression players|drifters <0-100>`.
- Step 5, a lived-in world (user's ideas): world-gen **camps** (1 in about 100 chunks where there's flat open ground: a
  hut with a door, a chest of village-house loot, table, furnace, bed and a wheat patch; half still lived in, the Drifter
  catching up 3 to 6 days when it first loads), **old strip mines** deep down (1 in about 24 chunks, a 1x2 tunnel with
  branches and torches, never into water or lava) and **torches in caves**. Each has its own switch (screen and
  drifter.json); rarities in data/drifter/worldgen/placed_feature. Tested on a normal world: 4 camps and 61 strip mines in
  1,000 chunks. **Drifter villages** (4 in 10 villages, when first loaded: 2 to 5 Drifters each take a house bed, a
  chest beside it, one group "of the village") and **Bandit outposts** (half the pillager outposts: 3 Bandits, "'s
  Raiders", home on the ground by the tower, no hut; while they hold it the illagers there are removed and none come
  back) in `world/gen/LivedInStructures` (saved as drifter_lived_in). Homes there are "borrowed" (`Home.borrowed()`):
  no hut or house of their own, in play or in catch-up. Tested on seed 2024: a desert village got 4, an outpost a gang.
- **Theft (user's ideas, 2026-10-16):** it remembers which valuables went missing from its chests (saved, 3 days); a
  player it sees wearing or holding one of them is taken for the thief, right or wrong ("X has its iron pickaxe: X stole
  it", -40 trust, grudge 50, a "thief"/"give it back" chat line). When it sees (or hears) someone open one of its chests it
  goes and checks it once they step away (goal "check its chest"); valuables gone after it watched them is "caught
  stealing from its chest" (-45, grudge 60): it turns on them. Group mates sharing chests keep each other's records up
  to date (so one member's trip to the chest isn't blamed on a player). Not testable on the headless server (no real
  player): in-game.
- **Hunches (user, 2026-10-16: "their logic needs more assumption, allow them to make mistakes"):** a cave it comes across
  (within about 24 blocks, by day, healthy, with a pickaxe and 4+ torches), it may go in for a short look on a hunch with no
  ore seen ("look round a cave, just in case"), the curious more often, once a day at most; afterwards "the hunch paid off:
  copper ore in the cave" or "nothing much in that cave: a waste of time, oh well". Strip miners sometimes go caving on a
  trip on a hunch too ("a hunch: might be diamond in that cave ..."). Tested: both seen. More ideas like this are welcome
  (see "Ideas for more assumptions" below).
- **Bigger huts, smoker and blast furnace (user, 2026-10-16):** the starter hut is 7x7 (5x5 inside): chests along the left
  wall, table and furnace in the back and front corners, bed along the right wall, the blast furnace and smoker on the back
  wall either side of the torch. The size is saved with the home: huts already built (or started) stay 5x5 (they put the
  smoker and blast furnace in their two free inside cells). After its iron gear it makes a smoker (furnace and 4 logs)
  and a blast furnace (furnace, 5 iron, 3 smooth stone: cobblestone smelted twice) and puts them in; ore goes in the
  blast furnace and food in the smoker (twice as fast). Catch-up puts them in from about day 3 and day 4; world-gen camps
  are 7x7 too. `/drifter hut` shows S for a smoker, X for a blast furnace. Tested: a 7x7 hut built and finished, the
  smoker placed on the back wall, the planner works both out.
- **Next (user's note):** more house plans to replace the huts later (bigger homes as it settles).
- **Step 2, joint mining trips (done):** a group mate within 32 blocks setting off (or just down) on a mining trip: a
  free member goes along ("going mining with Ann (for raw iron)", "Bob is coming mining too"), sociable ones more,
  healthy, by day, with a pickaxe for iron and torches, once per 10 minutes at most. Two setting off at once for the same
  ore merge: the one not leading goes along with the other. On a joint trip it goes for the same ore from the same mine
  (the mate shows it the way down) and does as the mate does (caving or digging); "/drifter list" shows "... with Ann".
  Tested: joined trips both ways, two mining side by side down the same stairs. Also: moving in with the leader now
  copies the whole home (hut, door, size, farm, mine; a bare copy made the newcomer build a second hut) and keeps the
  newcomer's own chests; a Drifter that dies leaves its group (its mates think "X died"). Debug: `/drifter group <name>`.
- **Step 3, team wars (done):** `world/DrifterWars` (saved as drifter_wars). Tension per pair of groups: a hit +1.5 per
  damage, a kill +35 (and a loss for that side), a raid's loot +5 a chest, bases within 96 blocks +1 an in-game hour up
  to 35; six quiet hours and it eases 0.5 an hour. 30+: members fight on sight (skirmishes); 70+: war, and a group whose
  leader is about, healthy and it's day raids the rival base once every 2 days, if one of them has seen the rivals by
  their home. A raid: gather at the leader, travel together in 32-block legs, a war cry near the base ("for the
  crew!"), fight (the usual fight goal: rivals are enemies on sight), loot the chests round the base it has seen, head
  home. A truce after one side loses 2 (or 3 quiet days at war): tension 10 and 5 days of peace ("made peace with X").
  Players aren't touched by any of it. The group's leader makes a banner (6 wool and a stick) and stands it by the hut
  door. Setting: "Group wars and raids" on the screen, `/drifter config wars true|false`. `/drifter wars` lists feuds;
  debug `/drifter war <name> <name> <tension>`. Tested: tension 85 → a raid gathered, travelled, war cry, fought (one
  killed on each side), came home; a banner put up by the door.
- **Step 4, Mob Control (done):** see DESIGN.md section 21 "Hooking in": with Mob Control installed, mobs whose rules
  attack players attack Drifters too. Tested with the Mob Control jar from the user's own profile: a bare rule
  (zombies attack "player") ignored Drifters before the fix and killed a passive one after; a rule without "player"
  leaves them alone; the user's full meridian config spawns Drifters and its zombies go for them.
- Found along the way: three goals never ran (their risk factor was 0, which zeroes the score: check-chest, pick up the
  crafting table it left, join a trip); settling retried a chest spot it couldn't place on forever (now remembered).
  Debug: `/drifter rob` takes the valuables out of the nearest one's chests (a simulated theft: it goes and checks).
- `/drifter locate village|outpost` (user's request, 2026-10-16): whether a village or outpost is lived in is now fixed
  by the world seed and where it starts (not a roll when it loads), so the command searches outward like /locate and
  finds the nearest lived-in one, with clickable coordinates. Tested on seed 2024: found a village 941 blocks away and an
  outpost 2,544 away; loading them, Drifters moved into the village and Bandits held the outpost. Also (user): chests go
  in a row along the hut wall, placed crouching so they stay single.
- Step 6: a settings screen in Mod Menu (Mod Menu is compile-only, from maven.terraformersmc.com; user approved
  2026-10-16): every drifter.json setting, applied at once, saved on Done. Not testable on the headless server: in-game.

## Milestone 13: Whim system (started 2026-10-16)

Plan (docs/WHIMS.md "How whims work", DESIGN.md section 26):
1. **Engine:** whim data files (`data/<namespace>/drifter_whims/*.json`, datapack-friendly), the whim goal (bored or
   triumphant Drifters with nothing urgent pick one, weighted by archetype, traits and mood), gates ([G] griefing, [M]
   multiplayer, [R] risky, [L] later), cooldowns per whim and a gap between any two, a setting, and commands.
2. **Action types** (each a small player routine whims fill in): watch the sky, lookout pillar, high point, visit a place,
   watch or feed an animal, hop (on the spot or on its bed), swim, pick a flower, light a dark spot, plant a sapling, a
   sign with text, a little build from a pattern, chat, look round, peek in a chest. Later: riding, fishing, music,
   collections, trophy rooms, pranks ([G]), roleplay.
3. **Vanilla whims** from docs/WHIMS.md, a category at a time (first set: about 35 across movement, builds, sky and
   weather, exploration, animals, little things, social).
4. **Modded whims** behind mod detection (`"requires_mod"` in a whim's params), after the vanilla ones.

**Done so far (2026-10-16):**
- Steps 1 and 2 (first part): `whim/` package. `Whims` loads `drifter_whims` data (38 whims in 8 files at first); a whim
  says its action and parameters, base weight, archetype and trait weights, moods that make it likelier, the stage it
  needs, when it fits (day, night, sunset, sunrise, rain, thunder, clear), what it needs to carry (or can make quickly:
  `"make"` maps a need to the item it crafts, never with a mining trip or a search), duration, cooldown and gates.
  `WhimGoal` (a goal like any other) scores from boredom, triumph, Fidget and Curiosity, less for the industrious, and
  only when it's calm (healthy, fed, no danger, overworld, not deep underground, not out at night unless brave); a 3 to
  6 minute gap between whims. 16 action types in `WhimActions`. Setting "Whims" (screen, `/drifter config whims`);
  `/drifter whims`, `/drifter whim <id>`. Saved per Drifter (when it last did each). Tested: forced hop, sign (crafted
  one from planks and a stick and wrote on it), view from the top, sapling; in a natural 2-day run three Drifters did 5
  whims (hops, clouds, a look round, a "hi from Ann" sign).
- Step 2 (more): 22 action types (added dance, gift, hang out with a friend, visit a friend's home, watch a block,
  craft something just because, collector flower picking, watching from the doorway); friends count if seen in the last
  10 s (a friend just behind it was missed). `/drifter whims` and `/drifter whim` say why a whim can't happen now
  ("nobody about", "not the right time or weather", "doesn't have X (and can't make it)"...). 55 whims. Tested: dance,
  a poppy and some bread given to a group mate, a bowl made just because, a dirt tower, a sapling planted and watched,
  the top of the hill.
- More whims (66): lamp post, bench, flower bed, warning sign, a spare tool for a friend, good morning and good night,
  tidying its chest, watching zombies burn at dawn, going to see the village, climbing a mountain it saw. A whim that has
  started holds at 0.65 so it gets finished (found: the hut goal cut a bench short every time). In two 10-day runs whims
  came up 5 to 7 times each and didn't hold back progress.
- Mistakes (docs/WHIMS.md section 19), for the low-Skill and low-Knowledge ones most: dropping a stack by mistake ("oops",
  then picking it back up), eating a spider eye, eating rotten flesh it didn't need. 24 action types, 69 whims. Tested.
- First [G] whim: a Bandit (raiding setting on) leaves a cheeky sign by a player's base it knows ("nice base", "watch
  your back"), never inside spawn protection. Gate checked (others: "not that kind of Drifter"); the full prank needs a
  real player's base, so it's an in-game test. 70 whims.
- 89 whims (19 more from the catalogue: bees, cats, its pen, a creeper from afar, bunny hops, an evening swim, a high
  pillar to look for somewhere new, spinning round, the water, admiring its house, the portal, home decorations, a spare
  bed, a lantern, showing off gear, complaining about mobs, asking for help, a bored crouch-dance). A 6-Drifter 5-day run:
  no crashes, no deaths, 14 whims of 9 kinds. Fixed a crash in the oops whim (setThrower(null) throws in 1.21.1).
- Snowball fight ([M], throw_at action): it throws snowballs at a friend (tested: 4 thrown at a group mate, who took
  no offence). 90 whims, 25 action types.
- use_block and sit actions, 10 whims (100 now): ringing the village bell, flipping a lever, pressing a button, a
  trapdoor, a tune on a note block, a record in a jukebox, composting seeds, cooking on a campfire, a slice of cake,
  sitting in a parked boat. All tested. Found: boats and minecarts scooped up a Drifter that bumped into them (as they do
  mobs, never players): it now only rides when it means to. Bells ring only struck on the right side (it aims there).
- Step 4 started, modded whims (data only, `requires_mod`, left out without the mod: "loaded 100 whims (8 left out)"):
  Alex's Mobs (watching animals it's never seen, birdwatching, a big predator from afar, a banana for a monkey) and
  Farmer's Delight (a knife, a cutting board, watching the stew, a slice of pie). IDs taken from the jars in the user's
  meridian profile. Not tested in play: Farmer's Delight doesn't load in the development server (a remapping error in the
  dev environment, not Drifter); to check in the meridian pack.
- Fixed from the 10-day runs: it crouches on magma whenever it's on it, moving or in water too (one worked on underwater
  magma, walking about, until it died).

## House plans (user's decisions, 2026-10-16; see "Decided")

Plan: (1) read plans; (2) place blocks the right way round (stairs, slabs, logs, doors, trapdoors); (3) gather the
materials for a plan; (4) build it and move in, the hut becoming a storage shed; (5) the mod's own designs (log cabin,
stone cottage, two-floor farmhouse, A-frame, watchtower, hillside bunker); (6) user imports (GrabCraft, BuildPaste via
structure blocks) for the user's own worlds.

**Done so far:**
- Step 1: `building/plan/`: `HousePlan` (every block of a structure file), `HousePlans` (mod/datapack plans in
  `structure/houses/`, a world's structure-block saves named `drifter_house_<name>`, and personal plans in
  `config/drifter/houses/*.nbt` for every world), `HousePalette` (the plan's woods by use mapped to the woods the Drifter
  has and sees most; cobblestone to cobbled deepslate if it has more of that; facings and the rest carried over),
  `GrabCraftImport` (a GrabCraft page's block data, old ids upgraded with the game's own table; GrabCraft's z runs the
  other way, its facings don't). Commands: `/drifter houses`, `/drifter house info <plan>` (what the nearest Drifter would
  build it from), `/drifter house keep <world plan>`, `/drifter house import <name>` (from `config/drifter/imports`),
  `/drifter house paste <plan> [asdrawn]` (debug). Tested: the user's GrabCraft stone house (9 x 12 x 7, 303 blocks)
  imported; its roof climbs to the ridge and its torches hang on the walls; for a Drifter carrying spruce it's all spruce.
- Step 2, facing (user, 2026-10-16): `hands/Placement` asks the game how to get a block the way a plan has it: for each
  face it could click (and the block itself, to add to a slab), each quarter of that face, each way it could look (16
  for signs and banners) and level, up or down, it runs the item's own placement (as a player's click would) and keeps
  the ones that give the plan's facing, axis, half, hinge, slab type, attachment or rotation; connections settle by
  themselves. `PlaceBlockTask.exactly(...)` picks one it can reach from where it stands and aims at that part of the face
  (`Reach`/`BlockAim` regions), then checks it came out right; a double slab is two slabs. Debug:
  `/drifter placeexact <pos> <block state>`. Tested, all passed: an upside-down stair, a stair, a sideways log, a top
  slab, a double slab, a door with its hinge on the right, a top trapdoor on a wall, a wall torch, a furnace and a chest
  facing set ways. Plan rule (user): grass blocks in a plan count as ground; uneven ground under a plan gets dirt.
- Steps 3 and 4, building and moving in (`brain/activity/BuildPlanActivity`, goal `planhouse`, "build a house"): once
  the hut is up (stage 5, builders keener), it picks a plan, a spot and which way it faces (`building/plan/PlanSite`:
  ground near home it has seen, at least 65% of the footprint; not water; not more than 4 up or down; out of the way of
  the hut, farm, pen and table; few trees; door toward home), and its woods (saved on its home). Then:
  1. **Clear:** tree trunks in the space first (up a pillar for high logs), then earth, rock and sand; leaves are left to
     wither (any still in the way get dug as it builds), snow and grass are left (blocks go over them).
  2. **Level** (user's rule: never floating): every hole under the footprint filled with dirt, lowest first.
  3. **Build:** lowest layer first, solid blocks before what hangs on them, each placed the way the plan has it, from
     the nearest spot it can reach it from; too high for that, it pillars up (at most 3 blocks, outside the house if it
     can). It makes or gathers what it runs out of (spruce planks, stairs, fences, doors... the planner). A block it
     can't get to is left for later and tried again once something else is placed; at most 6 gaps.
  4. **Tidy:** its scaffolding pillars taken down, top first; leftover leaves inside dug.
  5. **Move in:** its bed carried over from the hut (dug and picked up) and put down inside, out of the doorway, head
     against a wall, as far from the door as it can (or where the plan has a bed). From then on it sleeps there
     (`Home.planBed`) and the hut is a shed: no more hut repairs.
  `/drifter house status` shows the house's corners, blocks placed, and the bed once it lives there. Tested: flat world,
  the stone house all 267 blocks, scaffolding gone, bed moved, "in the house for the night", "going to sleep". Snowy
  taiga seed 20261016: in spruce, 257 of 267 blocks in about a game day of building, when night came (forced each
  minute; it builds between other jobs, so in a normal game it takes a few days).
- Imports (user, 2026-10-16): `/drifter house save <name> <corner> <corner>` saves a build standing in the world as a
  personal plan for every world (no structure block needed), trimmed to what's built (ground and air round it don't
  count; grass and dirt inside stay as the plan's ground). BuildPaste's website only has pictures (the blocks come
  through its mod, which needs an account), so the user pastes a build with BuildPaste in a creative world and saves it
  with this. Plans are read unfurnished (user's rule: "it's up for them to place beds"): beds, chests, barrels, work
  blocks, cauldrons, flower pots, carpets and shulker boxes in a build are left out.
- Village-house furniture (user's report 2026-10-16, screenshot: chests, a table and a furnace all over a village house
  floor): `building/Interior` finds the room round its bed (floor inside the walls, up to 64 cells) and gives each new
  chest a spot along a wall in a row from a corner, crafting table and furnace together along another, never in the
  doorway or (if it can help it) beside the bed, and only where the door, the bed and every chest and station can still
  be reached. Used by settling (chests), placing a station at home (also in its own plan house), and the world-gen chest
  of a lived-in village. Debug: `/drifter house layout <bed> <chests>` furnishes a room at once. Already-placed messes
  stay as they are.
- The user's BuildPaste houses (house1 to house4, saved with `/drifter house save`): all four built on a flat world,
  every block (house1 120, house2 252, house3 204, house4 469, the tall two-floor one, bed put upstairs). Fixes from
  them: stripped logs are made the player way (the log goes in, then an axe on it, axis kept); dirt paths count as
  ground, made with a shovel at the end; a log put down to strip isn't "cleared" again; it keeps 10 building blocks by
  it while building (it got stranded on a tall roof with none); the house counts as done and it moves in before taking
  its scaffolding down (two blocks it couldn't reach held a house up forever); no pillars to take scaffolding down.
  Debug: `/drifter house choose <plan>` (the nearest Drifter's next house), `/drifter house status` lists blocks still
  missing.
- Hill tests of the user's houses (snowy taiga seed 20261016, seed 12345 mountains): house1 and house3 built in full
  and moved into, several times, in two worlds (house3 once in dark oak); house2 and house4 not yet seen finished on a
  hill (house2 got to 240 of 252 before the fixes below; house4 to 429 of 469; in later runs the test Drifter settled by
  a lake where house4 doesn't fit, and one got stuck in the water by its chest: a pathing problem, not the house's).
  Fixes from them: it steps out of a block's space before placing it (it built a door round itself and was stuck in the
  doorway); a doorstep outside each door is levelled and cleared (doors two blocks up a slope); leaves where a block
  goes are dug, pillaring if need be, outer leaves first when they're buried in a crown; its own crafting table in the
  way is picked up, and no tables go down in a house being built; blocks put in the wrong way round are redone; a spot
  whose unseen ground turns out to drop away more than 5 blocks is given up for another (before building) or filled
  deeper (after). Open: settling by a lake at water level, then stuck in the water next to its chest ("no route").
- Performance (user's /drifter perf, 2026-10-17: peak 35 ms, 30 of it one Drifter picking a whim): whims are now drawn
  by weight first and checked against the world one at a time, at most 12 checks and 1.5 ms a pick; a whim's block list
  is turned into one quick test once (reading "#minecraft:wooden_trapdoors" again for every known block took up to 7 ms).
  `/drifter whims` shows the slowest checks. Test: one Drifter in a forest, peak 1.5 ms.
- Outpost Bandits grief (user's decision 2026-10-17, DESIGN.md section 14 "Griefing"): `social/BanditGoals` (goal
  `griefraid`): hostile on sight to players (Prey doesn't wait for an opening) and to non-gang Drifters (Threats); finds
  bases it sees (scout: player-placed block clusters it has seen, a Drifter seen at its home, beds it saw players use),
  scouts toward where it last saw someone when it knows none; a gang raid about once a day: gather, travel, war cry,
  4 minutes of wrecking (doors, chests, fire beside wooden walls, torches, crops, glass, stations, walls; at most 3 fires
  and 14 blocks each), back home. Setting "Outpost Bandits grief" / `/drifter config banditgriefing`. Debug:
  `/drifter banditgang` (a gang here), `/drifter banditmark` (the nearest gang knows of a base here, raid due now).
  Tested: a gang raided a Drifter's hut 50 blocks away: looted its chest, broke 26 blocks, lit two fires.
- Optimization pass (user: "rockstar games level of optimizing", 2026-10-17), measured with Java Flight Recorder and a
  stress test (30 Drifters at full detail, seed 20261016): server 6.4 -> 3.6 ms a tick, Drifters' own share 5.3 -> 2.6
  ms, worst moment 35+ -> 7-11 ms, path search 240 -> 370 nodes/ms. What changed:
  pathfinder: per-search block data in plain 3D arrays round the start (`nav/LocalGrid`, no hashing), a lean binary
  heap, settled moves skipped before costing, dig lists only when digging, dig time per block kind, block reads straight
  from the chunk, "lava here?" answered from each section's palette, deep-drop answers kept; its time budget 3 -> 2 ms a
  tick, with 0.7 ms kept for Drifters near a player. Terrain memory: fastutil map plus a last-section shortcut.
  Scans of what it has seen: sections skipped by palette, nearest-first and stop-at-first searches, the highest point
  from the height map (was 200-300 ms). Eyes: the game's block walk with air skipped and the chunk kept, fewer rays while
  looking at the same scene. Whims: drawn by weight then checked, 1.5 ms a pick. Reflexes: slow hazards checked every
  few ticks. Spread: periodic work offset by each Drifter's id. Player-placed lookups: a hash set per chunk. `/drifter
  perf` shows path search speed.
- Step 5, the mod's own designs (`data/drifter/structure/houses/`, made with build commands and saved with
  `/drifter house save`; my own designs, unfurnished, oak and cobblestone swapped for the Drifter's materials): log
  cabin (245 blocks), stone cottage (296), two-floor farmhouse with a ladder (545), A-frame (145), watchtower (172).
  Built by a Drifter on a flat world: cabin, cottage, A-frame and watchtower all the way, moved in; the farmhouse got
  to 363 and stopped only for cobblestone it couldn't fetch from its hut chest (open: "can't get to its chest" in the
  hut). Lessons: a roof's lowest row of stairs must touch the wall top (level with it), not hang diagonally above it
  (nothing to place it against in survival); beds need a roof over them and go on the lowest floor (one went out on
  the watchtower's top deck); pillars up to 4 blocks for tall eaves.
- Huts vary (user, 2026-10-17): each new home picks a look once (`Home.pickHutStyle`, seeded by where it is): 7x7 or
  9x9, walls 2 or 3 high, a cobblestone or wooden floor, log corner posts or not, glass windows (two a wall on the big
  ones; glass it has, or a little smelted from sand; open until then). Huts built before keep their plain look. Live
  building, catch-up and world-gen camps all use it; camps get glass and a torch inside.
- Farms (user, 2026-10-17): never in front of the hut door (to the sides or behind), and each home's farm is its own
  size round the water, 5x5 to 9x9 (camps 5x5 or 7x7, a world-gen limit); farms made before stay 9x9.
- Catch-up builds the house (`world/CatchUpHouse`, user 2026-10-17): far enough along (the plan-house goal's rule), it
  picks a plan and a spot (the ground round home counts as seen), and only if it has the raw materials in its pockets
  and chests (logs, cobblestone, sand; worked out from the plan's blocks) uses them up and puts the house up: cleared,
  levelled, every block as the plan has it, its bed moved in from the hut. Older world-gen camps come with one this way.
  Catch-up huts: trees and leaves in the hut's space and doorway cleared first; "finished" only when all its blocks
  stand; doors and glass from what's in its chests too. Tested on the snowy seed: a 7x7 hut with log corners, glass,
  a torch and a door, then an A-frame with its bed moved in.
- A fresh world feels at most a day lived (user, 2026-10-17): Drifters already living somewhere when the land generates
  (camps, villages, outposts) have lived a few hours to a day (`LivedInStructures.livedTicks`), not days; outpost
  Bandits get a raider kit (stone sword, bow and arrows, leather tunic and cap, bread) so a day-one gang is still a gang.
  Camps: rarer, spaced on a 7x7-chunk grid (one possible camp a region, kept off its edges: 2+ chunks apart, mostly ~100
  blocks), 85% lived in, and 80% a built house (one of the plans, in the wood growing there, up to four plans tried for
  the ground and what world generation can reach) with its Drifter living in it like a village house (bed, chest,
  table and furnace along the walls); otherwise a hut. Drifters picking a home keep 32 blocks from other Drifters'
  homes they know (not their own group's).
  Wanderers (`world/gen/WandererFeature`, setting "Wandering Drifters"): about one a 160 chunks where the ground's dry:
  fresh (nothing), setting off (a few hours to most of a day lived, no home yet), or passing through (full iron, a
  sword and shield, walking one way: `PassThroughActivity`; once a player has met it and it's 96 blocks off, it's gone
  for good). Debug: `/drifter wanderer fresh|dayone|passing`. Tested, 960x960 on two seeds: 9 to 13 wanderers, house
  camps well ahead of huts.
  Catch-up's structure loot is now from places it explored (mineshaft, ruined portal, dungeon, shipwreck, trial
  chambers, temples, buried treasure, a mansion late on), added to what it carries; no village chests.
- **Better Combat and Combat Roll compat (new, 2026-10-17).** Both optional, read through reflection (no build
  dependencies; each switches itself off if the mod's code doesn't match, and says so in the log at server start).
  - Better Combat (`compat/BetterCombatCompat`): the held weapon's reach (`Combat.reach`, fight spacing scaled to it in
    `AttackTask`), its combo (Better Combat's own choice of attack, combo count set on the stand-in player so Better
    Combat applies the combo damage and turns vanilla sweeping off), the swing's arc hits other hostiles in reach as a
    player's would, the weapon's swing sound; two-handed weapons mean no shield. Tested: "caught 2 in one swing".
  - Swing animations (`client/render/DrifterSwingAnimations`): Better Combat's animation library only animates real
    players, so the Drifter's model plays the same animation files itself through that library (speed fitted to the
    swing, mirrored for left-handers). With EMF, EMF's own animations on the arms and body pause for the swing (EMF's
    API, as the EMF Better Combat bridge does for players). Not checked on screen yet. No elbow bends (that part of
    the library is player-only).
  - Combat Roll (`compat/CombatRollCompat`): it rolls as a player does, with Combat Roll's own server settings
    (distance, invulnerable ticks, cooldown, hunger cost, food needed, on the ground only unless allowed): across an
    arrow or fireball's line when it has no shield up, and now and then back out of reach after a melee hit (the
    skilled more often), onto safe ground only. One roll, then the cooldown (a player's default: one charge). Its own
    roll animation: a forward somersault the way it rolls, tucked in (Combat Roll's own is player-only). Tested: 3
    rolls away from skeleton arrows in one fight.
  - Not yet: rolling out of a creeper's blast or to close distance (DESIGN section 21); extra roll charges from Combat
    Roll's enchantments.
- **Modded weapons, data-driven (new, 2026-10-17; user's choices: favorite type by personality, uniques found in loot
  and their abilities used, netherite later).**
  - Weapon files `data/<namespace>/drifter_weapons/*.json` (`brain/Weapons`): weapon families (one kind of weapon, an
    item per tier), which tiers it makes them at, which personality traits like them, two-handed or not; a file can
    need a mod (`requires`). `vanilla.json` (swords: stone and diamond, a fixed quarter of Drifters) and
    `simplyswords.json` (its 15 types at iron and diamond; its uniques and runic weapons).
  - Each Drifter has a favorite family from its personality (same every time, seeded by its id; `/drifter weapon`
    shows it and how much it likes each). With Better Combat, careful Drifters lean away from two-handed ones.
  - Progression: the stone sword target is "any weapon stone-tier or better"; at iron it makes its favorite right after
    the shield (if its family is made at iron: Simply Swords ones are, vanilla swords aren't, as before); the diamond
    sword target is its favorite at diamond. The game's own recipes; if a recipe is missing it makes the vanilla
    sword. Mining trips count the favorite's iron. In a fight its favorite kind goes a little ahead of an equal weapon.
  - Any melee weapon counts as a weapon now (our tag `drifter:melee_weapons`: swords plus the common `c:swords` and
    melee-weapon tags; Simply Swords' aren't in `minecraft:swords`), for the Nether packing list, storage, tool wear.
    A modded weapon with no entry counts as the best vanilla sword its damage per second matches.
  - Unique abilities (`hands/UniqueAbility`): 34 uniques have an entry saying when (a crowd, a target at range, hurt, any
    fight; a wind-up for charged ones). Used through Simply Swords' own API for a mob using a weapon ability, so the
    Drifter itself uses it (heals, buffs and dashes happen to it) and Simply Swords keeps the cooldown. Left without
    on purpose: the thrown ones, the dashes and blinks, ones needing charges built while held, Soulpyre, the Devourer
    (listed in the file's comment). Tested: Harbinger and Emberblade abilities fired in fights.
  - Uniques reach Drifters through loot: Simply Swords adds them to the game's loot tables, which chests and catch-up
    loot roll.
  - Gaps: Simply Swords 1.70's awakening (a unique starts as a weaker form and levels up) runs in the player's own
    tick, which a Drifter doesn't have; its hits go through its stand-in player, which may level it, not checked.
    Netherite upgrades (smithing) later, with netherite in general. The left-out abilities could be looked at again
    now that the mob API exists (dashes should work through it; thrown ones still risk losing the weapon).
- **Mineshaft delvers (new, 2026-10-17; user: "run into a drifter looting a chest", "more hostile than friendly",
  "full iron to full diamond, along with a simply sword weapon, a bow").** `world/gen/Delvers`: about a third of
  mineshafts (fixed by the seed) have one. The first time one of its unlooted chest minecarts loads with no player within
  24 blocks, a Drifter is beside it: iron to diamond armor (it rolls its share of diamond pieces; some enchanted), its
  favorite weapon at iron or diamond (a Simply Swords one if installed), a bow and arrows, a shield unless its weapon is
  two-handed, a pickaxe, torches, steak, cobblestone, a water bucket. Bandit, Miner, Hermit or PvP-sweat archetype;
  Bandit (45%), opportunist (30%) or territorial (25%). `DelveActivity` (goal "delve", urgency 0.75): a caving trip
  through the tunnels from there, looting every chest minecart it sees, for a day or so, then it's an ordinary Drifter.
  Setting "Mineshaft delvers"; debug `/drifter wanderer delver`. TODO (whims milestone): ancient cities.
- **The user's round of 2026-10-17 (evening):**
  - Too many Drifters ("10 to 60 in a few minutes"): a cap, "Most Drifters loaded" (default 24; `world/DrifterCensus`
    counts loaded ones, safe from world-generation threads); past it world generation makes none (a camp is left
    empty), and a lone new one isn't put where three are within 64 blocks. Defaults lowered a little. Measured: 60
    Drifters at full detail cost 6.3 to 6.9 ms of a 50 ms tick (peaks 16.5); catch-ups (20 to 50 ms each, the first up to
    135) now take turns, one a server tick. 2048 new chunks made 10 Drifters at the new defaults; with the cap at 5 it
    stopped at 5.
  - Every spawn chance is a setting (`DrifterConfig.Spawn`; settings screen "Spawning..." page; `/drifter config spawn
    <name> <value>`): most loaded, camps, camps lived in, camps with a house, wanderers per 1000 chunks, villages lived
    in, outposts held, mineshafts with a delver. The camp and wanderer placed features now roll every chunk and the
    chance is in code.
  - Better Combat swing animations really play now (the animation library's varargs layer constructor leaves its
    modifiers unlinked, so each swing ended on its first frame). Found with a scripted client test: `DevClientTest`
    (env DRIFTER_CLIENT_TEST: commands, camera, screenshots; dev only).
  - Drifters no longer dig their own (or anyone's) finished hut or house for a route or for blocks: finished homes are
    recorded as Drifter-built (`PlayerPlacedBlocks.BUILT`: world-gen camps, catch-up huts and houses, huts and plan houses
    finished in-game, and existing homes once on load); the pathfinder and gathering leave those blocks alone, so it uses
    the door. (Every placed block at first: that blocked its own tunnel plugs; narrowed to finished homes.)
  - A hut it just finished rests a minute before it's picked again (a station it couldn't place looped it every 3 ticks).
  - Chests and stations put down directly (world generation, catch-up, village houses) face the room with their back to
    the wall (`building/Furnish`), and a chest beside a single one facing the same way joins it into a double chest; a
    room's chest spot prefers the one that makes a double chest. Hut torches go on solid back wall, never a window, and
    no torch is ever put on glass.
  - Bandits kill on sight (user: "on sight kill anything that isnt their group ... worse than pillagers"; setting "Bandits
    kill on sight", on by default; overrides the telegraph-first rule for Bandits): goal "rampage" (`BanditGoals`,
    urgency 1.0: under fighting mobs and fleeing): the nearest prey it sees, players first: players, Drifters outside its
    group, villagers and wandering traders, golems, other people's tamed pets; fights to the death, takes the drops;
    breaks off under a third of its health. Its eyes go to prey, not its gang mates. Tested: a gang killed a lone
    Drifter, a villager and a wandering trader, went for an iron golem twice and ran from it; left a cow alone.
  - The user's log after that (freezes): the 8.4 s one was the Quicksave mod's automatic backup; a 3.2 s one right after a
    teleport (a whole new area generated, plus its Drifters' catch-ups); slow whim checks: tallest_mountain 237 ms (now a
    coarse grid then a fine look round the best spots, answer kept 30 s) and craft_something up to 30 ms (now 3 items a
    look, answer kept 10 s). Catch-ups now one every 5 ticks. A server test of travelling into new land with the user's
    settings (wanderers 30 per 1000 chunks, delvers 81%, cap 30) never fell behind; the Drifters' main cost there is
    pathfinding, under its 2 ms budget.
- **Pathfinding upgrade, step 1: parkour (new, 2026-10-17; user: "lets upgrade pathfinding, starting with parkour jumps,
  and building off of that").** New move PARKOUR (`PathSearch.tryParkour`, `tryParkourAngled`), checked against the
  player's own jump physics (`nav/JumpPhysics`: a tick-by-tick run of LivingEntity#travel and #jumpFromGround; a sprint
  jump with a run-up carries 3.68 blocks on the level, jump height 1.252 as in the game). Straight or at any angle, 1 to 3
  block gaps, landing level, a block up or up to three down; everything on the way seen; never over lava (or over an
  unseen bottom in the Nether); full-height normal-grip footing; headroom for jumps up; the gentlest jump that carries
  (`JumpPhysics.Mode`: hop, walking run, sprint hop, sprinting run) and none that would sail past a one-block landing.
  The walker (`NavigateTask.parkourSteer`): runs straight at it, backs up for a run if standing at the edge, creeps to the
  edge for a hop, jumps on the last tick at the edge, holds on through the air; a miss (landing below both ends) is
  remembered and it plans again. Tested on built courses (1, 2 and 3 block gaps; up and down; diagonal and corner jumps;
  a lava trench it walks round): all crossed, no misses. Natural terrain: misses went from 8 to 36 per 10-day run on the
  first version; the hop/run/sprint choice, footing and headroom checks came out of that: 2 and 2 on the next pair of
  10-day runs (91 jumps in the builder's). Open: a builder died to a cactus hunting a rabbit at low health (not a jump).
- **Pathfinding step 2: jumps onto ladders and vines (new, 2026-10-17).** A gap jump may end by catching a ladder or vines
  across the gap (`PathSearch.tryCatchClimb`): its middle carried into the climbable block while its feet are still that
  high, something behind to stop against (a ladder's own wall, the face vines hang on, or a seen block: `NavView
  .wallBehindClimb`), then it climbs on as planned. Tested: a 2-gap jump onto a ladder up a wall, and onto vines; seen in
  the client too (caught mid-jump, climbed, out on top).
- **Big huts fixed (2026-10-17, found in 10-day runs).** Huts that had rolled 9x9 (three in ten since huts vary) never
  got finished: the roof went on from one pillar in the middle, and a 9x9 roof's corners are 5.7 blocks from there, out
  of player reach. Every failing run of the last few was a 9x9 hut (parkour was suspected first; switching it off made
  no difference: `/drifter config parkour false`, and a "Parkour jumps" toggle in the settings screen, kept as a switch).
  Now a 9x9 hut has five pillar spots: the middle does the cross through it (and the block over the doorway, which has
  nothing to click from off to one side), one spot two in from each corner does that quarter, and the block over each
  quarter's spot goes in from the floor under it after. In a big hut it also walks over near the door, a table spot, the
  bed, the torch or a window before placing it (from the far side of the room they're out of reach). And a block whose
  standing spot it can't get to waits for another visit instead of failing the whole hut (a floor over a dip failed a
  7x7 hut for days). Tested on flat ground: 9x9 two high and three high, both finished with nothing missing, door in.
- **"Looking for trees" loop fixed (2026-10-17, 10-day run).** Exploring for wood stopped as soon as it saw any log it
  could take, but the planner only counts logs that belong to a tree (a house's log posts aren't one): its own hut's log
  corner posts ended every search at once and the planner sent it looking again, every 100 ticks for 8 minutes a
  visit, 14 visits running (a hut never finished: no wood for the door). The stop check now uses the same tree test.
- **Open (seen in 10-day runs, rare):** a Drifter stuck at the bottom of its own mine for days, every walk home "no
  progress for 8 s" (69 times in one run; not seen again in six more). With thoughts going to the log, a stuck walk now
  turns on a 20 s trace by itself, to catch it next time. Another drowned digging a staircase up into water on its way
  back from mining.
- **Next:** milestone 15 (below). Open: on a slope, a few blocks get "walked over but still can't reach"
  (it arrives a little off the spot); they get done later from somewhere else.

## Milestone 15: House plans by the hundred (started 2026-10-17)

User: "lets make whole milestone dedicated to generating houseplans, im talking hundreds".

- **Styles are data files** (`data/drifter/drifter_house_styles/*.json`, read on every reload, `_README.txt` beside
  them): size ranges, storeys, roof kinds and weights, and materials as one block or a list to pick from per plan
  ("none" allowed), chances for a porch, a chimney, an off-centre door, window sills, a base row, beams. Oak and spruce
  stand for its main and second wood and cobblestone for its stone, so a plan comes out in what grows round it (the
  existing palette swap). Eight to start: timber cottage, stone cottage, log cabin, farmhouse, longhouse, tower house,
  shed hut, stonemason.
- **The generator** (`building/plan/gen/PlanGenerator`): one or two storeys (a ladder up), framed walls (log corners and
  beams), a base row, windows (with sills), a door (centred or off-centre), gable (either way), hip, shed or flat roofs
  with eaves level with the wall top, a porch (slab deck, posts, slab roof), a chimney up through the roof, and an L: a
  one-storey wing out of the back (behind a gable whose ridge runs along the front), a doorway through, its own gable
  roof running up the main roof's back slope until it meets it (cells under the main roof left out, so they join with no
  gap). Hip roofs rest each ring of stairs on a ring of planks inside the one below (stairs climbing in only touch at an
  edge: nothing to place against). Shed roofs drop the back overhang where the slope would step up there (it floated:
  PlanCheck threw away 13 drafts for it; none now).
- **Every plan is checked** (`PlanCheck`) before it's used: a door, nothing floating (each block reaches the ground
  face to face, so a player could place it), and a roofed spot for the bed on the lowest floor (the same test the
  builder uses). Drafts that fail are thrown away and redrawn.
- **300 per world** (setting "House designs", `/drifter config spawn house_designs`), made once, seeded by the world
  seed, styles in name order, so a world keeps its plans between sessions (a half-built house stays the same plan).
- **Each Drifter weighs a shortlist** of 6 of them (as well as the hand-made and personal plans): creative,
  industrious and Builder Drifters lean to bigger houses, thrifty ones to smaller; seeded by who it is, so it doesn't
  change its mind.
- **Debug:** `/drifter house showcase <count>` puts a row of them down next to you (a test world: it really places
  them) and names each with where it went and what it has; `/drifter house export <count>` saves them as structure
  files; `/drifter house choose gen:<style>` (or a plan's id, `gen:farmhouse_96`) makes the nearest Drifter pick it. The
  server log line when plans are made counts what they have: in the first test world 151 gable, 73 gable the other way,
  27 hip, 30 shed, 19 flat; 101 two-storey, 93 porches, 96 chimneys, 37 wings.
- **Which house it builds** (`PlanSite.pick`, used by the builder and by catch-up): any plan whose best spot is about
  as good as the best plan's (its cost per column of footprint within 0.5), at random, as a player picks a house they
  like and then a spot. Before, the cheapest spot won outright, so the smallest footprints always won (10-day runs built
  the watchtower four times out of five). Tested: one Drifter choosing ten times on flat ground picked 8 different
  houses, 6 of them generated.
- **The user's round of 2026-10-17 (night):**
  - Glass blocks, not panes, in every house (a lone pane in a one-block window is a thin post; "none of the panes
    connect to the wall"): the palette turns any pane in a plan into glass (generated, the mod's and personal plans),
    the styles say glass, huts and the old houses take glass only. And no torch is ever put on a pane.
  - Furniture never at the foot of a ladder (the user's screenshots: a crafting table and furnace in front of one):
    `Interior` (every room furnishing: world generation, villages, Drifters furnishing their own) keeps the floor beside a
    ladder clear and a way to it open.
  - Trees felled round world-generated and caught-up huts and houses (user: "giving the illusion that they use the
    resources around the area"): about the logs the build took (planks a quarter, stairs three eighths), nearest trees
    first, trunk and leaves (not another tree's crown), a stump left now and then, snow off the felled crowns
    (`world/gen/Felling`). Seen: a house camp in a snowy spruce wood sits in a clearing with stumps.
  - Armor on Drifters that start with a lived-in kit (user: "matching with whatever tier they're on ... very mix matchy
    ... armor from actual early game loot tables"): at the end of a catch-up, one that wore at most one piece finds a few
    (1 + 1.5 a day, at most 6): early, leather it made (a quarter dyed), gold from a real ruined-portal chest roll (often
    enchanted), chainmail off a mob; iron age, iron (made, or a real village armorer roll) with chain and gold about;
    diamond age, diamond (some enchanted) and iron. Worn 10 to 60 %; it wears the best piece per slot
    (`world/CatchUpArmor`). Seen: golden boots and leggings; leather cap, chainmail chestplate, leather pants, golden
    boots; leather cap, tunic and boots with chainmail leggings.
  - Delvers are an encounter now (user: "should spawn when im close to a mineshaft (like i encounter one in a cave)",
    "mineshaft delvers should [despawn] ... its more than a mob than a drifter"): every 2 s round each player, an
    unlooted chest minecart of a mineshaft 16 to 48 blocks off and out of their sight gets one (the mineshaft's first
    roll fixed by the seed; rolls again after 3 days); never saved with the world, and gone 30 s after no player is within
    96 blocks; it delves till it has been through the place and doesn't settle. Bandit delvers (45 %) kill on sight.
    Tested in the client: walked into a mineshaft, two came (24 and 46 blocks off, unseen); flew off, gone. Debug: `/drifter
    locate mineshaft` puts you in the nearest one's middle room.
  - "The ladder leads nowhere" (user: "its at a solid ceiling because the roof doesnt allow it away thru"): a loft's
    ladder stood by the side wall, where the roof slope came down right over its hole. It now goes up the middle of the
    loft end's gable wall, under the ridge. And PlanCheck turns down any plan with a ladder whose top hasn't two free
    blocks over it (none of the 300 now).
- **Pursuit (user, 2026-10-17: "its really easy for it to lose me ... the chase lasts more than 5 minutes because it
  keeps trying to look for me"; "sometimes it pauses, and just looks around"):**
  - `brain/Pursuit`: who it's after and what it knows of where they went, from sight and sound only: where it last saw
    them and which way they were going, and sounds since (footsteps, digging, a door) from about where they could have
    got to, taken for theirs. Started by any fight with a player or Drifter (AttackTask, while in sight); lasts minutes
    (a Bandit, delver or manhunt hunter 6 to 9.5, anyone else 1.5 to 4.5, by vengefulness, aggression and patience),
    renewed by each sighting or sound.
  - `brain/activity/PursuitActivity` (goal "pursue", 0.88; 0.97 and urgent for the relentless): in sight it fights; out of
    sight: the newest sound, then where it last saw them, then 16 blocks on the way they were going, then spots round
    there it can't see into, ahead of them first and wider as it goes, a quick glance at each (3 turns, a third of a
    second each) and on. Ends with "gave up looking for ..." or their death.
  - No pause after losing them: the fight activity doesn't end with its look round and picking up while a chase is on.
  - Chasing out of sight goes to where it last saw them, not where they really are (NavigateTask.follow used the live
    position for the 6 s before giving up).
  - Kill on sight is urgent now (drops what it's doing): a Bandit that saw someone mid-walk kept walking (no point to
    stop at) and the user's delver "doesnt try to kill me on sight". And every delver is a Bandit (kills on sight).
  - Tested (a Bandit and a Drifter "runner" in a pillar arena): kill on sight at once; the runner hidden in a stone box
    40 blocks off, the Bandit searched round there for the whole 7 minutes of the run (it was still at it), never wandering
    off. Note: Bandits, like mobs, leave creative players alone.
- **Chases in Hopo's mineshafts (user, 2026-10-18: "test a delver chase, specifically with Hopo's Mineshafts mod"):**
  - Hopo's Better Mineshaft is a datapack mod (jigsaw mineshafts with rooms, chains as climbable, mob rooms). Its
    mineshafts aren't in the vanilla mineshaft tag, so delvers never came to them: new structure tag
    `drifter:delver_mineshafts` (vanilla's, Hopo's two tags as optional), and delvers start at loot chests and barrels
    as well as chest minecarts (Hopo keeps its loot in rooms). `drifter locate mineshaft` uses the tag, works on any
    entity (`execute as`), and puts it on a room's floor.
  - Tested in a Hopo dark oak mineshaft (seed 97709457, testbed with the Hopo jar): a Bandit in iron and diamond after a
    Drifter runner (weakened, so it ran). Found and fixed: it ran from an even fight with its quarry and straight back
    every few seconds (a relentless chaser now fights its quarry when the odds are even, backs off only when losing);
    after backing off it went off to settle somewhere 30 blocks up a staircase (now it stays, eats and heals, then goes
    on: "catching its breath"); running away it clipped lava with its side in a narrow tunnel and burned to death
    (`Steering.safeStep` checks its whole width). After: it kept within 1 to 7 blocks of the runner for 5 minutes.
- **More relentless chases (user's playtest, 2026-10-17: "it needs to be more relentless"):** the log showed a delver in a
  cave running after every glow squid and zombie villager sound as if it were the player, and the chase ending when the
  player went spectator. Now: a sound right by another creature it saw or heard in the last 5 s is put down to that; a
  new sound within 4 blocks of the one it's already going to doesn't turn it; creative or spectator hides the player
  but doesn't end the hunt; Bandits, delvers and hunters keep at it 10 to 16 minutes (others 2 to 5) and only stop for
  their life under 20% health (others 35%); "gave up looking for ..." is said even when another goal takes over as the
  time runs out. Tested: a Bandit after a runner hidden in a stone box searched round there 13.5 minutes, then gave up.
- **Space between camps (a setting; user, 2026-10-17):** camps already came one per 7x7-chunk region (a chunk picked
  from the seed, a chunk off the region's edges); the region's size is now "Space between camps" (48 to 1024 blocks,
  default 112 = as before), the margin a quarter of it, so camps are about that far apart and never closer than half.
  Tested on one 16x16-chunk area at 100% camps: 48 blocks gave 28 camps, 112 gave 5, 256 none.
- **Ragdolls (user, 2026-10-17: "can we make drifters have player ragdoll aswell?"; Sable, Sable Player Ragdoll and
  Ragdoll Corpse, the last two the user's ports, sources in ../ports):** `compat/RagdollCompat`, all reflection. A
  Drifter that dies becomes a player ragdoll (`RagdollAPI.spawnPlayerless`) in its own skin (Drifters wear the game's
  default skins, which a skinless profile gets by its UUID: a UUID next to its own that the game maps to its skin), its
  armor and held items on it, flung by the killing blow (at most 3 m/s: launched at 8 it came out one stiff piece); its
  body goes at once. With Ragdoll Corpse, every death leaves a corpse holding its things (user: "make every death leave a
  corpse"; the corpse mod's own storage, opened like a player's), which lasts till it's emptied or for "Drifter corpses
  last" (3 days by default, a setting on the Spawning page; 0 forever): then, the next time its place is loaded, it goes
  the corpse mod's way (what's left in it drops). Tested: a day on (sprinted), the corpse was gone and its things lay
  there; without it, its things drop and
  the ragdoll goes in time. The mod's own mob ragdoll on a player kill is cancelled for Drifters (two would fall). Setting "Ragdolls on
  death". Tested in the client: killed by a command and by a player, it fell as a ragdoll in its skin with its helmet,
  chestplate and sword; nothing dropped beside the corpse.
- **10-day runs, round 4:** a Drifter lived at 4 health for days with no food and food too low to heal (natural
  regeneration needs 18), building its house; three berry-bush pricks killed it. "Get food" only looked at how much food
  it carried: hurt, under 18 and nothing to eat now puts food first (0.5 + 0.4 x the health need, over building). And
  making do counted trips "with nothing placed in between", which also counted trips that brought the oak back when it
  was the placing that was stuck (so it gave up on oak wrongly): now only trips that bring back none of the material nor
  anything it's made from count.
- **Eleven styles now:** barn house (long, a gable front, often a loft) and manor (wide, two storeys, often a wing and
  a porch) added as data files. A world's 300: 148 gable, 85 gable the other way, 32 hip, 22 shed, 13 flat; 94
  two-storey, 43 with a wing (35 L, 8 T), 25 lofts, 8 towers, 81 porches, 107 chimneys; none thrown away.
- **Corner towers:** a one-storey house 7+ wide and 6+ deep (no loft, no porch) can have a square tower at its front
  corner, its walls carried up a block past the top of the roof (the roof over that corner taken out), windows near the
  top, a little pyramid roof; open inside down to the room. 9 of a world's 300. A Drifter built one (277 blocks) and
  moved in. On two-storey houses the tower's roof was 14 up, out of reach from any pillar: not on those.
- **Pillars up to 7 (was 4):** the house builder looked for a standing spot only 4 to 8 below a block, so a two-storey
  house's eaves, 9 up, had none ("nowhere to stand" 288 times). It uses the lowest pillar that reaches.
- **10-day runs, round 3 (2026-10-17):** one Drifter spent 7 days at the foot of a little waterfall, washed back
  every time it swam up the flow ("the current keeps pushing it back" 688 times): its escape put one block under it,
  which left its feet in the water. Now it stands up on as many as it takes to get its feet out (up to 3); from the saved
  spot it got out in about 4 minutes. Another chose your house2 (oak) in a dark oak wood and never switched wood: the
  switch only looked at woods within 48 blocks; it now also takes any tree it knows of (the same reach and tree test as
  its search for trees). And gathering never digs a staircase down after logs ("digging down to find oak log").
- **Down off its scaffolding:** up a pillar of its own and needing to be somewhere else, the house builder digs out
  the block under its feet, a block at a time (only onto more pillar or solid ground), as a player gets down; before, it
  relied on the pathfinder, which won't drop more than 3, and stood on 4-high pillars with "no way there".
- **Lofts:** a one-storey gable house 7 or more across (no wing) can have a loft: a floor at the wall top over one end
  along the ridge (where the headroom is), a ladder up the back wall through a hole in it (that bit of wall kept solid
  for the ladder), lit by the attic window. 14 of a world's 300. A Drifter built one (229 blocks) and moved in.
- **Unattended 10-day runs:** on seed 31337421 a Drifter picked a generated timber cottage on its own, ran out of birch
  and finished it in dark oak, left the windows open (no sand), finished on day 5 (326 of 332, bar windows and one block
  it couldn't reach). The other run's Drifter froze when my test script kept the land loaded round a different Drifter
  (fixed in the script: it follows the test Drifter by name now).
- **A-frame cabins** (a ninth style, `a_frame_cabin`): one-block walls under a steep roof that runs nearly to the
  ground, ridge front to back, the door and windows in the gable ends. A Drifter built one start to finish (217 blocks).
- **Attic windows:** a window in the middle of each gable end on roofs 7 or more across. The gable ends now fill round
  anything already there (an A-frame's door reaches up into its gable). No porch on walls under three high (its roof
  would be at head height).
- **Natural ground (staged, materials given):** a two-storey farmhouse with a wing on a forested hillside. It stuck at
  131 of 462: the house's inside was full of spruce leaves from the trees round it (they never wither while another tree
  keeps them), boxing in floor blocks and taking every spot to stand ("nowhere to stand" 159 times). Clearing now digs
  leaves inside the house's space that won't wither (leaves that will, it still leaves). After that: finished, 462 of
  462, moved in, inside two days (most of the first spent clearing the wood and levelling). Debug `/drifter house map`
  draws the house layer by layer (# placed, . to place, X something else there, = an unplanned solid block).
- **T-shaped houses too:** the wing can sit in the middle of the back instead of one side (a third of wings).
- **The builder makes do (found in 10-day runs: "needs 9 glass pane" 79 times, "needs 64 oak planks" 87 times; the
  houses never finished).** Trips for one material that bring back none of it (nor anything it's made from) are counted
  (and visits where it can't work out a way at all); after 8 (3), out of a wood it builds the rest of the house in another wood it
  can get (a block of the same kind in another wood counts as placed, so nothing is torn down), out of glass it leaves
  the windows open (those don't count against the house being done). Walls and roof it never does without: it keeps
  trying, visit by visit. Tested on flat ground with 20 oak logs, no sand and no trees: windows left open, the rest in
  spruce, finished (220 of 222), moved in.
- **Seen:** the showcase in the client (porches, chimneys, longhouses, towers, wings, hip roofs). Drifters built
  generated plans start to finish on flat ground and moved their beds in: a timber cottage (193 blocks), a two-storey
  farmhouse with a wing (450), a stone cottage with a hip roof (223).
- **To do:** more shapes (a T, a tower wing, A-frame roofs, lofts); a 10-day run where a Drifter picks and builds a
  generated plan on its own on natural ground.

## Ideas for more assumptions (for the user to pick from)

Players guess and get things wrong. Candidates in the same spirit as the cave hunch and the theft assumption:
- It hears a zombie behind a wall and assumes it's night-dangerous out there (stays in a little longer).
- It finds its crop trampled and blames whoever it last saw near the farm.
- It sees a dark hole and assumes there's a mob spawner or dungeon (goes to look).
- It digs where it once saw ore even after someone mined it (memory is old: it goes anyway, finds nothing).
- It assumes a player wearing diamond is dangerous and keeps its distance, even if they're friendly.

## Notes for milestone 9 (from testing)

- **User expectation (session 2):** punching a PvP Sweat Drifter should turn into a real pursuit. Today any Drifter
  only fights back while the hit is fresh (about 5 s, the game's "last hurt by" memory), then goes back to what it
  was doing. Grudges, Trust, Vengefulness and chasing a player down are milestone 9 (section 14); PvP Sweats and
  Bandits should pursue hard there.

## Long-term goal: manhunt test

The user's "ultimate test" is a **1v1 manhunt**, now in DESIGN.md section 25 ("Manhunt test mode"). It's **only a test**:
the mod must not be centred around it. **Hunter role first** (acceptance test after milestone 9), speedrunner role after
milestone 14 (The End). Keep it in mind when building chasing (milestone 4), combat (6) and hostile behaviour (9), but don't add
manhunt-only features before then.

## Decided

- **Build setup:** Loom 1.17 + Gradle 9.5.0 on Java 21 (user: "use your recommendations").
- **License:** All Rights Reserved (user: "use your recommendations").
- **Idle head jitter:** keep the design value (0.4 degrees). The user tested milestone 1 in-game, reported everything works, and chose my recommendations (which was: only raise it if it looks frozen).
- **Own spawn group:** option (a), the mod's own rare spawner instead of a vanilla spawn group; the entity stays in `MISC` (user: "use your recommendations"). See design question 1 below for the background.
- **Player sleep blocking:** a small mixin on the bed check, when that milestone comes (user: "use your recommendations").
- **Travel pace:** players sprint-jump the whole way about 80% of the time unless they don't need to, shouldn't, or can't. DESIGN.md section 10.9 was updated with this (user's rule).
- **Better Combat / Combat Roll:** combat-mod test pass in milestone 6; real integration (optional compile-only dependencies) added later as a follow-up (user: "yea add them later, use your recommendation").
- **Manhunt:** added to DESIGN.md as an optional test mode (section 25), hunter role first, not the centre of the mod (user decision).
- **House plans (user, 2026-10-16):** option 3, hand-designed plans with variety (not fully generated, not copied
  websites in the mod); the user can add their own with structure blocks (`drifter_house_<name>`), including builds pasted
  with the BuildPaste mod or converted from GrabCraft (those stay in the user's own worlds, not in the public mod); the
  old hut stays as a storage shed. Plans generalize their materials (user's idea): a plan's wood becomes the wood the
  Drifter has round it (spruce in a taiga, birch in a birch forest), stone likewise, for variety.

## Design questions

Questions for the user. Nothing here has been changed in the design.

1. **(Decided: option a.) Own spawn group (sections 18 and 22).** On 1.21.1, mob spawn groups are a hard-coded enum (`MobCategory`), and Fabric API has no way to add a new one. Options: (a) don't use vanilla natural spawning and run Drifter spawning from our own rare spawner (section 4 already describes a custom, rare, out-of-sight check every few minutes), which also keeps them out of the monster and creature caps; (b) add an enum entry via a mixin/enum-extension hack. I recommend (a). For now the entity type uses the `MISC` category, which means no vanilla natural spawning and no share of any cap, consistent with (a).
2. **(Decided: use a mixin.) Player sleep blocking (section 18).** Vanilla only blocks sleep for mobs of the `Monster` class. Making a hostile-to-you Drifter block sleep will need a small mixin on the bed check. Fine on 1.21.1; no decision needed unless you object to mixins there.
3. **Idle jitter is mostly invisible over the network (section 16.1).** Minecraft sends entity rotations to clients in steps of 360/256 = about 1.4 degrees. The design's "fraction of a degree" jitter therefore shows up as an occasional 1.4-degree tick rather than a smooth tremble, and only when the jitter crosses a step. Options: (a) accept it; (b) raise the jitter amplitude to about 1.5 degrees so it reliably shows; (c) later, send exact rotations with a small custom packet (costs a little network per Drifter). **Decided: (a), keep 0.4 degrees** for now; raise it if the user later finds the head looks frozen.
6. **(Decided: hunter first, optional test mode only.)** Manhunt role. Now in DESIGN.md section 25.
5. **Pathfinding reads the real terrain for blocks it has seen (sections 1, 8, 12).** Section 8 says pathfinding is one of the few systems allowed to touch the live world. To keep it fair anyway, it only reads blocks the Drifter has actually seen (or touched, or revealed by mining); everything else is treated as unknown, exactly as section 12 describes. One small leak remains: if a block it saw changes while it's not looking, the pathfinder sees the new state. I think that's fine (it replans as soon as it gets there anyway). No decision needed unless you want it stricter.
7. **Night-one shelter is tier 1 (section 11).** For milestone 5 the Drifter digs three blocks straight down and covers the hole (the tier 1 "digs down and covers the hole" option) rather than the tier 2 hillside room, because it works on any ground and is the most reliable way to survive night one before combat exists (milestone 6). The worked example in section 24 has it dig a 3x3 hillside room on day 1; that can follow in milestone 8 (or sooner if you prefer). No decision needed unless you want the hillside room now.
8. **The planner's data file location.** Section 8 says acquisition rules live in data files. They're in `data/<namespace>/drifter_sources/*.json` (a datapack can add more). Recipes come from the game. No decision needed.
9. **Better Combat and Combat Roll (section 21, milestone 6 "combat-mod test pass").** What already works without any special code: modded weapons (Simply Swords and the like) are judged by their real attack damage and speed, and its reach comes from the reach attribute, so they're picked up and used correctly. What section 21 asks beyond that: reading Better Combat's per-weapon ranges and combos, and giving the Drifter the same dodge roll as Combat Roll. Doing that properly means compiling against those mods (as optional, "compile only" dependencies, never required at runtime), which adds their download sites to the build. **My recommendation:** for this milestone, just run the combat-mod test pass to check nothing breaks with them installed (the Drifter's hits go through vanilla's player attack, which those mods leave working), and add the real Better Combat / Combat Roll integration as a follow-up once you say it's OK to add those optional build dependencies. **Decided (user: "yea add them later, use your recommendation"):** test pass now; the optional compile-only integration comes later as a follow-up. **Done (2026-10-17, user: "we are adding better combat and combat roll compat"):** through reflection instead, so no build dependencies were needed (see "Current state").
10. **(Decided: add them now, user: "use your recommendations".) Usernames and nameplates (section 4, "Identity").** Milestone 7's test is "people watching a clip can't tell it
    from a player", and every player has a name over their head. Section 4 asks for generated player-like usernames
    (word plus digits, two words joined...) shown like a player's nameplate; that was left for the spawning milestone.
    **My recommendation:** add the username generator and the always-visible nameplate now, as part of milestone 7
    (small: a name list, a filter, and showing the name like a player's), and leave natural spawning for later.
11. **(Decided: keep it, user 2026-10-16.) Spawners wake for Drifters (milestone 11).** Vanilla spawners only spawn while a player is within
    16 blocks, so a Drifter waiting by a fortress's blaze spawner never got a blaze (section 19 has it get rods there).
    I made spawners count a Drifter like a player (a small mixin), which matches section 1 ("hostile mobs target it like a
    player"). Side effect: a dungeon spawner wakes when a Drifter mines nearby, as it would for you. **My
    recommendation:** keep it. Say if you'd rather have it only for blaze spawners, or behind a config setting.
4. **Break speed computed on the Drifter (section 3).** The design says to compute mining time through the fake-player helper. I compute it on the Drifter with an exact copy of the player's formula instead, because the helper never ticks, so its Haste, Mining Fatigue and Efficiency would be out of date. The numbers are identical to a player's. No decision needed unless you want it done differently.

## Milestone 16: Scale (started 2026-10-18)

The user's goals: "can we put 1000 drifters in a world while keeping the TPS low and the server very smooth? Right now
even more than 30 drifters affect the world and makes it a bit laggy ... if a 1000 isnt possible lets aim for 300", and
"a 500v500 (or 150v150) war and it runs very smoothly with each ai working properly ... as long as 1000 drifters (across
the world) work well then we a good". Measured on the user's PC (16 threads) with headless servers and the dev client.

### Tools (for measuring)

- `/drifter perf`: now also the share of travel time spent waiting for a route, how many loaded Drifters really run
  (and how many are in chunks not ticking entities), the world copy's size, path-thread time and the battle's count.
- `/drifter tier <full|reduced|far|auto> all`; `/drifter catchup <hours> all`; `/drifter spawnmany` up to 1000;
  `/drifter config paththreads <-1..16>`.
- `/drifter battle <per side> [gap]` and `/drifter battle stop`: two armed armies of Drifters (iron armor, sword or axe and
  shield, a bow and 16 arrows each, food; the back quarter of each side are archers with 64 arrows) lined up facing each
  other on natural ground (not on roofs); they kill the other side on sight (as Bandits) and march on it out of sight
  (or, at its old line, toward the sound of fighting); never their own side. Not saved (a test). `/drifter perf` adds a
  battle line: standing per side, on roofs, bows drawn, arrows into friends, and how many hardly moved since the last
  report with what they're at.
- Smoke tests: `jfr <seconds> <file> [settings]` lines profile the server (Java Flight Recorder; tools/fine.jfc), and
  `tools/jfrtop.py` reads the profile (`--within`, `--callers`, `--prefix`). The dev client test script (DRIFTER_CLIENT_TEST)
  has `fps <ticks>` and `jfr <seconds> <file>`. Note: vanilla `/forceload` takes at most 256 chunks a command (bigger areas
  silently fail: four tiles of 15x15), and the console runs at world spawn (use `~` coordinates).

### What was slow, and what was done

1. **Path searches: a shared 2 ms a tick** for all Drifters. With 300 they waited 90 to 95% of their travel time for a
   route ("most of them stood around", the user, watching). Now searches run on helper threads (a quarter of the
   machine's, 1 to 6; setting "Helper threads" on the new Performance page, 0 = the old way) over `nav/WorldCopy`, a
   frozen copy of chunk sections shared by everyone, refreshed when a block changes (LevelChunk mixin; changes in a
   search's box are copied at once before it starts), with a frozen copy of what the Drifter knows (`copyAround`) and
   copies of what it reads of itself (tools, scaffolding, failed bridges, trails). After: 4 to 10% of travel time
   waiting, 10x the searches done.
2. **Crowds**: each Drifter looked properly at everyone within 160 blocks every half second (thousands of sight lines in
   a battle). Now: nearest 24 close up, a crowd (40+ within 32 blocks) keeps to those near, a middling ring before the
   whole of its sight, sight lines between two Drifters shared within a tick, the farther ones in a crowd every other
   update, one sort of packed numbers.
3. **Hearing**: every footstep reached every Drifter in earshot. Now repeats from the same creature within half a second
   are one sound, at most 6 sounds a tick get through, wool is looked for in the sections' palettes before any ray.
4. **Decisions**: the knowledge scan read every remembered block through the level (now straight from each section,
   air and kinds already found skipped first, once a tick, and reused a few seconds by unwatched Drifters); goals scored
   survival first and then by branch and bound (urgency first, planning only while a goal could still make the top
   three); the raw-meat fallback plan is cached now; unwatched Drifters keep plans while what they carry is the same.
   First decisions of a crowd spawned together are spread over a second (300 soldiers made one 574 ms tick).
5. **Eyes**: near eye rays traced on the helper threads (`perception/SightWork`; it takes in what they hit next tick);
   the "seen" marks and the mental map made cheap (the mental map scanned all 4,096 tiles on every ray once full); a
   shared table of the tick's chunks (`world/TickChunks`) for rays, scans and route views; tunnel vision in a fight (2
   rays); far sight less in a fight or a crowd; tier rates as in DESIGN.md section 22.
6. **Catch-up** ran whole in one tick (up to a quarter of a second each; a hitch flying into a village): now a few slices a
   tick in a shared 3 ms, in phases; the house site search works each ground column out once (it was 85%).
7. **Small ones**: thoughts to the log capped at 200 a second (a battle's chatter); the black box records raw numbers (and
   every fourth tick away from players); posture checks once a tick, blocks only; reflexes look through a short list of
   projectiles, TNT and falling blocks kept as they come and go (`world/RareEntities`); the fake-player helper's id and
   profile are worked out once; "any wood I know of?" reads sections directly; the registry's save list is made at save.
   Self-started debug traces (a Drifter stuck or nearly drowned, with thoughts in the log, logs a line a tick for 10 s)
   start at most once every 5 s server-wide: with 1000 Drifters they wrote 100,000 lines in two minutes, up to 15 ms of
   every tick and 300 ms spikes.
8. **Third pass** (user agreed 2026-10-19): Drifters far from every player (Far tier) look and think every other tick, never
   in a fight (their bodies, reflexes and tasks still run every tick; setting "Far ones think half as often" on the
   Performance page). Hearing through one shared list of the tick's sounds (`perception/SoundHub`, a ServerLevel#gameEvent
   mixin) instead of a vibration listener per Drifter (the game handed every step of a crowd to every listener near it).
   The fake-player helper is kept per Drifter, and never loads advancements (a 216 to 275 ms spike on the first swing).
   Whim checks that don't fit are remembered for a while; soldiers get no whims; the high-point search reads chunks
   directly.
9. **Battles, the AI** (user, watching: "alot of people were still standing still, and then a bunch of them pillared",
   then "there are still some people standing in the back / if it isnt possible for them to move, can they be like
   archers?"):
   - **A bug: a quarter of the soldiers never made a decision.** First decisions wait for each Drifter's own point in the
     second (so 300 made by one command don't all decide in one tick), but a far Drifter thinks every other tick, and for
     half of them that tick never came. Now counted from its first tick. This affected every far Drifter, not only
     soldiers: the earlier "1000 at Far" number was flattered by idle ones (remeasured below).
   - Attention: who matters (players, hostiles, its prey, the enemy side) gets the proper looks first, the rest nearest
     first (in an army the nearest two dozen were all its own side). Ally support: each friend within 10 blocks adds half
     its strength to its own when it sizes up a fight (it fled at "threat 17 vs power 5.9" in the middle of its army).
   - No pillaring up against Drifters or players (they dig or shoot it down); only against mob swarms.
   - Archers: every soldier has a bow; the back quarter are archers and shoot whenever they can see their target beyond
     sword range; the rest take out the bow when they can't get through (hardly moved and no nearer in 2 s, or no way
     there), and after 8 s try the sword again. No arrow is loosed with a friend in its path: the arrow's flight is traced
     tick by tick (speed, drag and drop as the game has them) out past the target, with the friends where they'll be at
     their pace and a little room to spare; blocked, it holds the draw and steps aside, and after 5 s goes back to the sword
     for a while. Friendly arrows in a 50v50: 34, then 18, then 0 to 2.

### Results (server tick, 50 ms is the limit for 20 TPS)

| Test | Before | Now |
| --- | --- | --- |
| 300 Drifters round a real player (dev client, radius 100) | ~30 ms, worst 1% 52 ms | 21 to 22 ms, worst 1% 33 to 36 ms; 290 FPS |
| 150v150 battle, the clash | 56 ms, worst 1% 109 ms, one 574 ms tick | 34 ms, worst 1% 75 ms; then 17 to 29 ms |
| 1000 loaded within 210 blocks, nobody near (Far) | ~100 ms | 58 to 64 ms, worst 1% 82 to 90 ms (half-rate thinking; 515 moving at once) |
| 1000 loaded within 210 blocks, all at Full detail | ~105 ms | ~95 ms |
| 60 Drifters catching up 3 days at once | ticks of 100 to 370 ms | most ticks under 10 ms |
| 50v50 in a village, dev client (real player watching) | | 5 to 12 ms; 86 to 172 FPS; nobody idle, ~10 to 19 bows drawn, 1 friendly arrow |
| 50 Drifters each on a different whim in a village, dev client (fresh world) | | 7 to 10 ms, worst 1% 15 to 30 ms; 164 to 211 FPS; 45 different whims, 46 of 50 without a hitch |

1000 Drifters across a world (most in unloaded chunks, catching up when you come) is cheap. 300 round a player is a
steady 20 TPS. 1000 all loaded in one 400-block area is about 16 to 17 TPS, and a 500v500 battle would be well over the limit.

Client FPS in big battles: with EMF and Fresh Animations, the animated player models are most of the frame. EMF's
settings "retainDetailOnLowFps" and "retainDetailOnLargerMobs" set to off gave about 40% more FPS in a 150v150.

### Next steps / design questions

- Done (user agreed 2026-10-19): Far Drifters think every other tick, never in a fight (setting on the Performance page).
- Done: the whim test (user, 2026-10-19: "place 50 drifters (geared or whatever) doing various whims in a village ... a
  test to see all the different actions and how that would perform"). `/drifter whimtest <count> [radius] [whims or
  kinds]` (`world/WhimTest`): Drifters with a kit round you, each given a different whim that fits where it stands
  after 5 s of looking about (and what that whim needs, a test kit), and a report of how each went, including the
  "couldn't ..." thoughts on the way (a whim that hits a snag ends as done, by design). The task runner now keeps how its
  last task ended. Client tests now run in a fresh copy of a newly made seed-4242 world each time (the old `drifter_test`
  world is superflat at height -60, which Drifters take for deep underground; the user: things overlap).
  Fixed by it (trouble on the way went from 14 of 50 to 4, and those are walking on rough ground):
  - Builds stand beside the build (a long one from its long side), level or a step up, never on it; not where another
    Drifter it can see is standing; only where each block can stay (flowers on grass, not a path).
  - Jump-placing: a face above its eyes from the ground but in view from the top of a jump (a snow golem's pumpkin, a
    lamp post's torch) is clicked in the air, as players do (`UseOnBlockTask.jumpFor`).
  - A pumpkin that finishes a golem counts as placed (the blocks become the golem).
  - The little monument is two cobblestone and a torch (three and a torch can't be topped from the ground, even
    jumping); the dirt tower is a 4-high dirt pillar it climbs, as players do (lookout action with a block).
  - Pillar whims dig back down their own pillar afterwards (left on top, the next walk bridged off through the air).
  - Pillaring on a dirt path (a little under a full block) works.
  - A regression from the first pass: a Drifter nobody watches reused its scan of what it knows for 5 s, so right after
    placing its crafting table it still "knew" of none and crafted and placed another, again and again (85 tables in
    a test, now 18). Its own placing or mining now drops the held scan.
- Sight lines for awareness on the helper threads too (the next biggest piece in battles).
- The planner itself (crafting chains for food and progress) is the biggest part of a decision.

## Milestone 17: Fixes (started 2026-10-19)

The user: "mark them done and focus on the problems: combat roll, getting around on rough ground, some better combat
animations dont work". Then "you should run a test with tectonic's terrain".

### Better Combat animations (done)

Found by filming duels in the dev client (same mods and resource packs as the user's game): every swing animation was
found and played; what players have that Drifters didn't:
- **Holding poses**: a two-handed weapon (claymore, halberd, heavy axe or hammer, katana, polearm, scythe; bows and
  crossbows too) was held upright in one hand, the vanilla way. Now `DrifterSwingAnimations` keeps a stack per Drifter as
  Better Combat does for a player (AbstractClientPlayerEntityMixin): a pose layer per hand for the arms and body and one
  for the weapon in the hand, the attack on top, with its rules (no poses while swimming, using an item, climbing,
  gliding or holding a loaded crossbow; the arms-and-body pose not while walking or crouching unless two-handed; the off
  hand's only when dual wielding; mirrored for left-handers).
- **The weapon in the hand**: many swings and poses turn the blade in the hand (the "rightItem"/"leftItem" channels):
  `client/mixin/ItemInHandLayerMixin` applies them to a Drifter's held item, as the library's HeldItemMixin does.
- **Legs**: they keep walking while it moves, crouches or rides (Better Combat's updateAnimationByCurrentActivity).
- **Slash trails** (`client/render/DrifterTrails`): the swing's own trail or the one its animation has in Better
  Combat's trail settings, in the weapon's colours, at the top of the upswing; the server sends the swing's reach and
  upswing ticks with the animation.
- **Combat Roll's roll**: its own roll animation (combat_roll:roll, turned the way it rolls) instead of the somersault
  (kept for when the library isn't there).
- Battle soldiers carry their favourite kind of weapon (Simply Swords: all fifteen kinds), a shield only with a
  one-handed one.

### Combat Roll (done)

- Charges, recharge and distance from Combat Roll's own attributes, which it adds to the player's attributes and so a
  Drifter has: its enchantments (Multi-Roll, Acrobat, Longfooted) on a Drifter's armour count. A Drifter starts with all
  its rolls; each used one comes back after the cooldown, sooner with more recharge (its RollManager).
- Rolls out of a hissing creeper's blast (within 4.5 blocks, straight away from it or to one side, safe ground only),
  once per hiss. Tested: four creepers, four rolls, each blast 7 to 8 blocks behind it.
- Rolls in on something shooting at it (a skeleton, someone with a bow) 4.5 to 9 blocks off, now and then (the skilled
  more often), straight at it over safe open ground.

### Getting around on rough ground (in progress)

- `/drifter navtest <count> <radius> <distance>` (`world/NavTest`): Drifters with tools and blocks round you, each sent
  to its own spot on dry ground that far off, and a report (in `/drifter perf`) of who got there, how fast, and why the
  others didn't. Note: run it at normal speed; under `/tick sprint` route searches on the helper threads take real time
  while game ticks fly, so walks look much slower than they are.
- **Replans that bring it nearer don't count toward giving up.** Every time a route's guess about unseen ground turned out
  wrong it replanned, and after 15 it gave up, even while closing in. Now getting 3 blocks nearer than ever before starts
  the count again. Vanilla terrain, 40 Drifters sent 50 blocks on four seeds: 87 of 160 got there before, 155 after.
- **Failed digs remembered.** A dig on the way it couldn't do (couldn't see or reach the block from where it stood) isn't
  planned again for two minutes (as failed bridges and pillars already weren't).
- **Looking things over.** Once its guesses have been wrong a few times running, at an edge it didn't know was there it
  stops and looks over it, across the drop, and at a hidden slope or tree it looks across and up, before planning again.
- **Tectonic** (the user's terrain mod; tests stage it with lithostitched): on Tectonic's jagged peaks (100 blocks of
  height, 25-block climbs on average) 21 of 40 got there within two minutes before; now 30 to 35. Frozen peaks 27 then
  about 33; windswept hills 37 to 39.
- A Drifter buried by a falling block can dig out (aiming at the block its head is inside works now: the game's ray
  starts inside it).
- Left for milestone 20 (wrong guesses): a better guess for unseen ground (the height of the nearest ground it has seen,
  as a player reads a hillside) instead of "unseen is both open and solid".

## Milestone 18: Player skills (started 2026-10-19)

The user: "after that start on villager trading, fishing, horses, boats, potions, and netherite". In that order.

### Villager trading (done)

- `trade/TradeGoal`: in the daytime, with nothing pressing, it walks to a villager it knows sells something it wants
  and can pay for, or (with emeralds or surplus to sell) goes to have a look at the trades of a villager in sight it
  hasn't checked (the curious and the greedy sooner).
- `executor/task/TradeTask`: the villager's own right-click (`interactOn` by its helper player: reputation prices, the
  villager stops and looks at it), a moment reading the offers (remembered in `trade/TradeBook`, saved with it), then
  each trade as the vanilla trading screen makes it (`Trading.trade`, mirroring `MerchantResultSlot`: the villager's
  experience, restocks, reputation).
- What it buys and sells (`data/drifter/drifter_trading/vanilla.json`, a datapack file): better armour, weapons and
  tools than it has, food when short, a Mending book if it knows enough to use one; it sells its surplus (each good has
  how many it keeps) first to pay. Tested: bought a chainmail helmet with emeralds from selling.

### Fishing (done)

- `skills/FishGoal`: at dusk, in the rain, bored, or short of food, near open water it knows (5x5 of water at least),
  it fishes from the bank (the patient more often). It makes a rod first if it has sticks and string.
- `executor/task/FishTask`: the real rod and bobber cast by its helper player (the bobber's own bite timing, Lure, Luck
  of the Sea, the fishing loot table); eyes on the bobber, and when it dips, a pull after a reaction time by skill. A
  cast onto the bank is pulled in and thrown again a little higher.
- Clients see the bobber's line from the Drifter's hand (`FishingHookMixin` sends the Drifter as the bobber's owner;
  the client mixin gives the bobber a stand-in player that follows the Drifter). Tested: salmon, cod, a pufferfish and a
  bowl; the line shows in the client.

### Horses (done)

- `skills/HorseGoal`: with no horse and a wild one in sight (a horse, donkey or mule), it breaks it in as a player does:
  on, thrown, on again (the vanilla temper odds, `RunAroundLikeCrazyGoalMixin`), giving up after 4 to 12 tries by
  patience; tamed, it's its own (tamed to its helper player, remembered and saved). With its horse and a saddle, it
  saddles it.
- Riding: on trips of 80 blocks or more with its saddled horse within 16, it rides most of the way (`RideTask`: its keys
  steer the horse as a player's do, `DrifterRiding` mirrors the horse's ridden travel; it steers round walls, water and
  drops and jumps steps), then gets off 16 blocks short and walks in. It only steers a saddled horse (on an unsaddled
  one the horse goes its own way and bucks), and it gets off when nothing is using the ride. Tested: thrown three to five
  times, then tamed, saddled, rode about 100 blocks and walked the last 16.

### Boats (done)

- It makes a boat early (the last of the early targets: five planks of one wood) and keeps it on hand.
- `NavigateTask`: where its route goes out onto open water at least 8 blocks across (12 if it's already swimming), and
  it has a boat, it crosses by boat (`executor/task/BoatTask`): out from the bank far enough that the boat doesn't catch
  on it, the boat down on the water (the boat item's own use), in, rowing (its keys are the paddles, `BoatMixin` runs the
  boat's own controls on the server for a Drifter), steering round what's in the way; at the far bank (where its route
  comes out of the water, or the first bank on the way on) out, a few punches to break the boat, the boat picked up, and
  on on foot. Something wrong on the water (stuck in a narrow bend): out there, boat back, and it swims (no boat for a
  minute after).
- How wide the water is it judges from what it can see: its route plans over water it hasn't made out as if it were
  ground, so the open water in sight the way it's going decides it too.
- The route planner counts open water as quick going when it has a boat (a fifth of a second a block).
- A boat it rows doesn't scoop up mobs that bump into it (as a player's doesn't); it doesn't "hear" its own boat or
  horse. `/drifter navtest` Drifters now carry a boat.
- Found in testing: its helper player stands where the Drifter is, so the boat item saw its eyes inside the Drifter and
  refused (`BoatItemMixin` leaves the Drifter's own body out of that check).
- Tested: a 30-block pond in about 4.5 s, boat back in its pack; the walking test by a river and the sea on two seeds,
  58 of 60 got there, 5 crossed by boat, one crossing given up in a bend (out, boat back, swam). Then: rowing toward a
  far spot (no landing planned) into a shore it can't climb out on, it takes a bank off to one side: 30 of 30 got there,
  4 of 4 crossings made. A 3-day run with 6 Drifters: no crashes, no deaths, they crafted their boats.

### Potions (done)

- Knowledge-gated (Knowledge 0.4 and up; section 10.14). Once it has a blaze rod, its progression list gets a brewing
  stand (a blaze rod and cobblestone), then three glass bottles (sand, glass, bottles: the planner's usual chain) while
  there's something it would brew. Nether wart comes from its Nether trips (already picked there).
- `skills/BrewGoal`: at home, with the stand (it sets a carried one down at home), wart, blaze powder and an ingredient,
  it brews what it's short of, in order: fire resistance (magma cream; once it has a portal) 2, healing (glistering melon
  slice) 2, strength (blaze powder) 1, night vision (golden carrot) 1 (Miners 2). It makes the ingredient on the way if
  it can (blaze powder from a rod, gold nuggets from an ingot, the melon slice or carrot, magma cream from slime and
  powder), fills the bottles at water it knows near the stand (`FillBottlesTask`: the bottle item's own use), then works
  the stand's own slots: old contents out, three water bottles, blaze powder for fuel, nether wart (awkward potions),
  the ingredient, and for Skill 0.7 and up gunpowder on healing potions (to throw), waiting by the stand through each
  20-second brew; potions out. Tested: three splash potions of healing in about 70 seconds.
- Using them (`skills/Potions`, reflexes): badly hurt in a fight (35% health) it drinks healing, or throws a splash one
  down at its feet; on fire or in lava, fire resistance; and before the hard part: strength when it's fighting something
  hard (another Drifter, a player, a blaze, a vindicator, a brute...) still a few blocks off; night vision deep in a dark
  cave; fire resistance in the Nether near lava or blazes. A potion drunk is used up and leaves its bottle (vanilla only
  does that for players). Tested: the splash healing at 5 health (to 9), night vision in a closed cave.

### Netherite (done)

- Only with an upgrade template (section 19: the first one comes from a bastion's chests, which its chest looting already
  takes when piglins aren't watching, or a bold one goes loud; netherite is no use without one). With a template and a
  diamond pickaxe, its Nether trips also go for ancient debris (once a trip, after the blaze rods; trips start again for
  debris once it has its rods): `DebrisActivity` digs a staircase from near the portal down to about Y 15 and strip
  mines there (a 40-block tunnel with side tunnels every 4), taking debris a tunnel wall shows (the tunnel digger now
  counts ancient debris as ore) and going to get debris it spots nearby, until it has enough for its next two upgrades or
  six minutes are up. The trip home waits for it.
- At home (`skills/NetheriteGoal`), with a template and four scrap's worth: the planner's usual chain for an ingot (the
  debris smelted to scrap in its furnace, four scrap and four gold at a crafting table), a second template from the first
  if it has seven diamonds, a netherrack and more to upgrade, a smithing table (it crafts and sets one down), and the
  upgrade (`SmithTask`: the table opened, a few clicks' pause, the game's own smithing recipe, enchantments and name kept;
  worn armour goes back on). Order: the weapon, the pickaxe, the chestplate, then the rest; modded diamond weapons with a
  netherite smithing recipe count.
- Debris, scrap, ingots and templates, and brewing things and potions, stay on it when it stores things at home.
- Fixes found on the way: the tunnel digger turns when a step along its tunnel fails twice (it used to retry the same
  step until the trip gave up); in the Nether its stair steps go straight onto the block it just dug (its route planner
  stays off unseen cells by lava it has seen, which down there is every step), hopping off when crouching on magma holds
  it at the edge.
- `/drifter debris [count]` (debug): digs for ancient debris from where it stands.
- Tested: the upgrade at home (2 debris smelted and 2 scrap, 4 gold, a crafted smithing table: "Bitey", a Sharpness III
  diamond sword, came out a netherite sword, name and enchantment kept); in the Nether, stairs from Y 40 to 13 and a
  strip mine, lava breaches handled; one run found a debris, another none (rare, as for players).

## Milestone 19: Mod compat and modded whims (started 2026-10-19)

The user: "work on more whims (specifically modded) and mod compat in general, i added farmers delight refabricated,
simply bows, spartan shields, villager guards, and bountiful". All optional: nothing needs them, and the data files and
whims for a mod that isn't installed are left out.

### Testing note: three of the five don't load in the development server

Guard Villagers (a method name clash that only exists under the development names), Farmer's Delight Refabricated (a
remapping error, as before) and Bountiful (it needs Kambrik, which isn't installed) can't run in the development
setup. Their support is written carefully (by registry id, data files and existing actions) and is on the user's
checklist. **Bountiful needs the Kambrik mod**, which isn't in the DRIFTER TEST profile: the game won't start with
Bountiful until it's added.

### Spartan Shields (done, tested)

- Any shield is a shield (Spartan's are the game's own shield underneath): carried, raised, worn down by blocked hits,
  kept when storing, and the best one goes in its offhand (`hands/Shields`: by the wear it has left; enchanted a little
  better). Tested: given a vanilla shield and a Diamond Reinforced Shield, it put the diamond one on.
- Whims: making a big tower shield, making a shield and reinforcing it with iron (the crude wooden shield first: the
  planner's chain, a crafting table put down if needed), showing off its shield to company. Craft whims may now take 5
  steps (was 4). Tested: both made and equipped.

### Simply Bows (done, tested)

- Any bow is a bow, and its best one (a unique bow over a plain one, then enchantments) is the one it shoots with.
- Simply Bows gives any shooter that isn't a player endless arrows. A Drifter shoots a modded bow that has its own way
  of letting go through its helper player, as a player does: the arrows come out of its own pack, the bow's ability
  works as for a player, and the arrows are then its own (whom they hit, it hit). Tested: Buzzkill at a zombie, 13 shots,
  13 arrows used, the bow worn, the kill its own.
- Whim: showing off its unique bow to company. Unique bows are chest loot: it finds them in the chests it loots.
- `/drifter shoot <target>` (debug): shoots at something with its best bow.

### Guard Villagers (written, to check in the user's game)

- Guards are the village's defenders: a bandit on the rampage treats them like iron golems. They count as a threat only
  while one is after the Drifter (`data/drifter/drifter_threats/modded.json`: about a vindicator), so peaceful Drifters
  and guards leave each other alone (guards go after monsters, raiders, and whoever hurts a villager or a guard).
- Whim: watching the village guards.
- Not done: giving a guard gear (the mod only lets a player with Hero of the Village or good standing do that).

### Farmer's Delight Refabricated (written, to check in the user's game)

- Its knives are a weapon family (`drifter_weapons/farmersdelight.json`: light and quick, few favour one); its foods it
  eats like any food.
- Whims: a warm bowl of stew, a mug of hot cocoa at night, setting a cooking pot over a campfire (plus the earlier
  ones: a knife, a cutting board, watching the stew, a slice of pie).
- Found for later: its cooking pot takes items through Fabric's standard item transfer, so Drifters could cook its
  meals (ingredients in from the top, a bowl, the meal out the bottom); not done yet.

### Bountiful (written, needs Kambrik)

- Whim: reading the bounty board.
- Later, if the user wants it: taking and turning in bounties (the board's screen can't open for its helper player, so
  it would be done through the mod's own code).

## Milestone 20: Whims offscreen, then wrong guesses (started 2026-10-19)

### Whims offscreen (done, 2026-10-20)

`docs/OFFSCREEN_WHIMS.md` was the proposal. The user picked: "a + b + c + e, with a small b / the while you were away
should only be in thoughts log / and aslong as C doesnt change the performance of the mod, itll be fine / while doing
all of this, please keep the optimization in mind".

- **Each whim has an `offscreen` value** (its action's default, or its own): `show` (only for show), `trace` (leaves
  something), `real` (uses things up or needs someone), `skip` (only with someone about). Defaults per action in
  `WhimActions` (the registry); the table is in docs/WHIMS_HOWTO.md.
- **C, nobody near** (`WhimGoal.begin`): a `show` whim started by a Drifter with no player within 128 blocks (Far tier)
  is settled at once: the thought, the mood, the cooldown, no walking over and standing there (a `Settled` activity
  that ends the tick it starts; no chat line, nobody to hear). `skip` whims aren't picked then. A debug `/drifter whim`
  still plays it out. `/drifter whims` shows how many were settled. Every whim now eases boredom (30%) as it starts,
  live, settled or away.
- **A + small B, while away** (`whim/WhimsAway`, a new catch-up phase): about 2 whims a day of free time (more for the
  fidgety, fewer for the industrious), picked by the same weights as live (archetype, traits, mood), the stage, the
  slice's time of day (rain whims wait for real rain), cooldowns, what it carries; no `M`/`G` whims, nothing needing
  company or a seat. Each eases boredom and starts its cooldown. A whim that needs something about (a cat to watch, a
  campfire, water, a hill) counts only if there is one round its home (its eyes' knowledge isn't kept while unloaded,
  so it looks at its home's surroundings, once per whim per catch-up, skipping chunk sections that can't have it).
  `trace` whims count only if they really leave their trace: at its own loaded home, two at most per catch-up, from
  items it carries, on free ground near home (never on a player's blocks, nothing floating), marked as Drifter-built:
  a little build, a sign facing home with one of its lines, a sapling on grass, a torch in a dark corner (its hut
  counts). So the log never claims a tower that isn't there.
- **E**: one line in its thoughts at the end, nowhere else: "while away, for fun: watching the clouds go by twice,
  bouncing on its bed, putting up a little monument (left by home)". (`/drifter catchup <hours>` prints it too.)
- Tested (headless): 10 days' catch-up at a home in a flat world, two monuments left by home (their torches found in
  the world), the lines matching what's there (its chest, bed, hut; no cats or portals); show whims settled the tick
  they started; 60 Drifters catching up 3 days at once: 45.1 ms each on average with whims, 44.3 without (about 2%,
  within noise); live, no slow whim checks from the new whims and no measurable difference against the previous
  version (runs vary more than that). In the game client (three homes, 10 days each): a sapling, a sign, little
  monuments; the first try put a monument and a sapling right against the hut wall (the hut counts as built only once
  finished, and catch-up puts its shell up first), so traces now keep clear of the hut's and house's plans: 2 blocks
  (3 for saplings), checked again on 8 homes. Each trace is logged with where it went ("Bea left something at home
  while away (little_monument): at ...").

### Drowning in a flooded shaft (fixed, 2026-10-20)

Found in the final 3-day check and again in two night tests: a Drifter pillaring up a 1x1 shaft on its way home broke
into the bottom of a lake; the water poured down on it, its own pillar filled the shaft below (no way back), and its
drowning reflex mined up into the lake ("no air to swim to: mining up through the ceiling") until it drowned.
- New reflex `WaterLeak` (before the drowning one): water falling into a hole no wider than itself from water above, it
  finds the top of the hole and puts a block in it at once, while it still has air ("water pouring in from above:
  plugging the hole"); floated up into the hole, it sinks out of it first. The water under the block drains away.
  Never over still water (a flooded hole it swims up out of isn't sealed over its head).
- The plugged block is kept off its routes for 10 minutes (they dug it straight back out: three floods a minute).
- The drowning reflex no longer takes water over its head for a ceiling to mine.
- Tested: a shaft under a pool, the ceiling mined: plugged first time, water gone, full air and health. Told to go to
  the pool's surface from there: plugged twice, then dug out sideways; alive. 40 Drifters, 3 days: no deaths.

### Fewer Drifters hiding at night; cheap shields (done, 2026-10-20)

The user: "i think too many drifters hide in the night, especially since their ai is advanced enough to where a sword
is enough ... drifters can craft a wooden/stone shield now [Spartan Shields], so a shield can be early game and cheap.
a sword and shield is enough for the average drifter to survive the night and still collect resources".
- Carrying on at night (`Schedule.fitToWork`): a stone sword (as before), **or any sword and a shield**, or two iron
  armor pieces. With a shield, more careful and less brave ones carry on too (caution under 0.8 instead of 0.7, bravery
  from 0.25 instead of 0.35).
- Early goals (`Strategist`, Spartan Shields installed): a crude wooden shield (four planks and a stick) right after the
  wooden pickaxe, a stone braced shield after the stone sword; and with no weapon at all as dusk nears, a wooden sword.
  The iron-age shield goal now wants one as strong as the game's own (a crude one doesn't stop the upgrade). Without
  Spartan Shields nothing changes but the wooden sword before dark. (The stage checks now read their goals by name.)
- "tonight it stays in" now says why in the thoughts: too shaken, too careful, not brave enough, too hurt, too hungry,
  or nothing good enough to fight with yet.
- Tested (40 Drifters, 3 nights, Spartan Shields installed): the first night, 3 of 24 stayed in (12%), before 7 of 22
  (32%); 12 carried on, 5 hunted, 4 dug in to mine. Crude wooden shields made in the first minute or two, stone braced
  ones soon after; one made a wooden sword at dusk. Those still staying in: too careful (personality) or too hungry.

### An easier way to add whims (done, 2026-10-20)

The user: "how many whims are there? and can you make a system where its more easier to add new whims in the future?"
(121 whims in the mod's files now, 108 load in a game without the optional mods.)
- **Steps whims** (`whim/StepsAction`, action `steps`): a whim written out in its data file from building blocks.
  `find` names its targets (block, mob, friend, company, home, bed, chest, sky, ground; optional ones allowed), then
  `steps` in order: walk_to, look_at, use, hold, place, wait, say, think, jump, swing, look_around, eat, drop, pick_up,
  crouch, each with a `chance` if wanted. Read once per whim and kept. Four examples (`drifter_whims/steps.json`): warm
  its hands by a campfire (night), wave to a friend, check on the crops, a morning stretch. Tested: each forced and
  done.
- **Load checks** (`whim/WhimCheck`, `Whims.load`): unknown fields and params (per action: the registry lists each
  action's params), wrong archetype, trait, mood, `when` and `offscreen` names, item, block and mob ids the game hasn't
  got, badly written ids and tags, unknown `@` matchers, gate letters, build cells, every steps target and verb. Each
  warning names the whim and file, with a guess ("did you mean ..."); only a whim that can't work is left out. The last
  line counts the warnings. Tested with a file of 13 broken whims: every mistake caught and named; none of the mod's
  own whims warns.
- **docs/WHIMS_HOWTO.md**: every field, every action with its params and offscreen default, the steps targets and
  verbs, the offscreen values, how to check a whim.
- Adding a new action type in code is one line in the `WhimActions` registry (name, code, offscreen default, params).
- Also fixed: paths now treat a lit campfire as no-go (a Drifter walked over one exploring and got burned).

### Wrong guesses (done)

The user (2026-10-16): "their logic needs more assumption, allow them to make mistakes". Before: the cave hunch and the
theft assumption. New (from the "Ideas for more assumptions" list, and the unseen-ground note from milestone 17):
- **Noises in the night** (`ShelterActivity`): at dawn, if it heard a monster close by in the last minutes of the night
  and never saw it, it takes it to be still out there and stays in a little longer (20 to 90 seconds by Caution:
  "heard a zombie out there in the night: staying in a little longer, just in case"), though the zombie may well have
  burned up at sunrise.
- **Who trampled the crops** (`brain/Hunches`): it notices who it sees standing on its farm (a person counts over an
  animal for a couple of minutes), and when it next works the farm and finds farmland gone back to dirt, it blames the
  last one it saw there, right or wrong ("48 of its farm's plots trampled: it must have been the pig it saw there"); a
  player it blames loses a little trust (new event `TRAMPLED_CROPS`: trust -8, a small grudge 10). It may well have
  trampled them itself. Tested: the plots trampled by a command, a pig standing on the farm got the blame.
- **Ore gone** (`Senses`): ore it remembered and finds gone: "someone must have mined it" (a creeper, a cave-in or its
  own earlier trip are just as likely).
- **Unseen ground** (`nav/NavView`): before, a cell it hadn't seen counted as both air and ground at any height, so
  routes floated straight over valleys and through hills and fell apart when it saw the real ground. Now it guesses an
  unseen column's ground from the nearest ground it has seen round about (within 3 columns, the nearest ring's average),
  as a player reads a hillside: more than 2 below that is earth (dug like rock), more than 2 above it is air with nothing
  to stand on, and only the band in between stays unsure. Nothing seen near: as before; not in the Nether.
  `/drifter groundguess on|off` (debug) compares. Walking test (40 Drifters each, 50 blocks): vanilla 2 seeds, 74 of 80
  off, 74 and 78 of 80 on; Tectonic (seed 4242, three areas) 98 of 120 off, 105 and 109 on (the frozen peaks the most:
  26 to 33 off, 32 to 35 on). Route searches about 5% slower (each column's seen ground cached per search).
- Already there: a player in much better gear raises its fear (`BETTER_GEAR`), and enough fear keeps it away.
- Not done from the list: a dark hole taken for a dungeon (close to the cave hunch).

### Found in the final 3-day check (to fix next)

6 Drifters, 3 days on seed 31337421 with everything from this session: no crashes. Two deaths, neither from this
session's changes: one killed by a bandit Drifter (as designed), and one drowned in its own mining tunnel at Y 12: the
tunnel broke into an underground water pocket, the plug didn't hold, and the tunnel flooded. The drowning reflex tried
to mine up (more water above in a water pocket) instead of swimming back along its tunnel to where it last breathed.
Next: in a flooded tunnel of its own, back out along it first; mine up only when that's too far.

## Player skins (2026-10-20)

The user: "how can we have player skins without taking up the storage of a lot of pngs?" Options given (paint from
parts; download real ones; resource-pack skins; a packed file); the user picked real downloaded skins: "lets do 2,
usually i have internet but itll be fine if there isnt any, its just like minecraft multiplayer". DESIGN.md updated.

- `entity/RealSkins`: a Drifter keeps its made-up name; the server asks Mojang for the account with that name (the
  same lookups a server makes for player heads), and if there is none, for two other names like it (the same ones each
  time for that Drifter). One new lookup a second at most, two at once, answers remembered for the run and saved with
  the Drifter, so each is asked once. No answer (no internet, Mojang busy): its default skin, another try in 10 minutes.
  No account among its names: its default skin for good.
- Clients get only Mojang's short skin text with the Drifter and download the picture themselves through the game's
  own skin loader, which caches it on the computer (`client/render/DrifterSkins`), as for a player you meet. Until then
  the default skin (matched to the real skin's arms: slim or wide).
- Ragdoll corpses wear the real skin too.
- Setting: "Real player skins" on the Spawning page of the settings, `/drifter config realskins true|false` (default on).
- `/drifter skin` (whose skin it wears), `/drifter skin <name>` (that account's skin, e.g. a friend's), `/drifter skin
  default`.
- Tested: 20 new Drifters, 14 with a real skin within 40 seconds; `/drifter skin Notch` works; in the game client the
  skins show (a screenshot of six: five real, one default).
- Note: a Drifter named like a real account wears that account's skin and name, as a real player would look.
- **UUIDs and lists (user, 2026-10-20: "yeah do both"):** where a skin comes from, in order: (1) the user's own list,
  `config/drifter/skins.txt` (made with a note on first run; Minecraft names or UUIDs, one a line), each skin on one
  Drifter at a time; (2) the account with its own name; (3) four accounts picked at random from a bundled list of 20,000
  UUIDs (`data/drifter/drifter_skins/players.txt`, sampled at random from the user's list of 6,749,939 public account
  UUIDs: 660 KB of text, about 350 KB in the jar; datapacks can add more .txt files there). UUIDs skip the name lookup
  and stay with a player who renames. `/drifter skin <name or UUID>`. With no internet nothing is marked "no skin": it
  waits and tries again. Tested: 20 new Drifters, 20 with a real skin; a 2-entry list (Notch's UUID, jeb_) gave each to
  exactly one Drifter.

## Milestone 21: Encounters and bounties (started 2026-10-20)

The user: "work on milestone 21" (2026-10-20, after the plan in docs/ENCOUNTERS.md). No events picked from the list yet:
the suggested first batch was built. Everything is in docs/ENCOUNTERS.md ("Built"); in short:
- `encounter/Encounters`: the director (one at a time per player, about every 1.5 days by default, fitting the moment),
  saved per player (next time, Drifters killed, the price on their head, the poster they hold; version 1).
- `encounter/Encounter`: temporary Drifters (never saved, not listed, gone once out of sight after), its own goal for
  each part (`EncounterGoal`, urgent while its part wants doing), a step list that ends rather than fails on time.
- Six kinds: bounty hunters (from a grudge or three Drifter kills), wanted posters (a map to a real Drifter, the reward
  by courier), injured travellers, thieves, travelling merchants (the game's trading screen, real stock), ambushes.
- Hooks elsewhere: no warning first toward the player an encounter came for (`Mode`), relentless chases (`Pursuit`),
  members of one encounter on one side (`BanditGoals.prey`, `Threats`, `Archery`), a right-click goes to the encounter
  (`DrifterEntity.mobInteract`), `DrifterRegistry` skips temporary Drifters. A witness who was chasing the Drifter a
  player killed counts it as its attacker killed, not a friend (`SocialWatcher`).
- Settings page "Encounters..." and `/drifter config encounters|encounterdays|encounter`; `/drifter encounter ...`.
- **Second batch** (the user: "yea pick some more events"): the watcher, duel challenges, muggings, hungry travellers,
  feuds, campfire parties, mourners, and treasure maps from some merchants; all tested in the client. Thirteen kinds in
  all (docs/ENCOUNTERS.md "Built (second batch)").
- **Killing and revenge** (the user: "what will be the punishment for killing in a duel?", then "do 1 to 3, and the
  revenge one too"): each kind says how a kill counts (`Encounter.killing`: self-defence nothing, others like any
  Drifter's, a duellist that gave up murder: a price on their head at once); the duel judges each blow as it lands
  (Fabric's after-damage event); revenge squads (`RevengeSquad`, the 14th kind: a murder always, other kills of Drifters
  that weren't after the player one time in four; saved per player as `Revenge`). Tested in the client (docs/ENCOUNTERS.md
  "Built: killing encounter Drifters, and revenge squads").
- **The rest of the list** (the user: "then after that do the rest of the encounters"): the scout (turning the tables on
  bounty hunters), a thief in your base, sniper, village raid, trapped miner, escort, hostage, lost dog, race, chased
  by a horde, convoy, builders. 26 kinds in all; tested in the client (docs/ENCOUNTERS.md "Built: the rest of the
  list"). Shared: `Encounter.props` (horses, a dog: gone with it), `Script.until` (a step ends when what it waited for
  happens), `toBuild`/`placeableFrom` (a spot to place a block from: a face it can see and reach), chat answers
  (`Encounter.chat`), `Hold` (stay put, look somewhere). The client test harness takes `chat <text>`.
- **Bountiful** (`compat/BountifulCompat`, `compat/BountyGoal`): Drifters read a board they've seen, take a bounty they
  can fill from what they can spare and turn it in at the board (Bountiful's own code pays them). Tested headless with a
  known bounty (4 flint for 2 emeralds: done). Kill bounties left; gathering for a bounty not yet.
- Found and fixed in client tests: a "follow" step never ends (walking up to someone is now a one-off walk); a step list
  timing out counted as a failure and marked the part down; the traveller dug in for the night instead of running; two
  ambush bandits killed each other; bandits who heard someone didn't go and look.

## Death, respawn and lives (2026-10-20)

The user: "do the respawn and lives stuff first, make it configurable between the three" (DESIGN.md section 20, never
built before: a Drifter's death was permanent).
- `world/Respawns`: the three modes, "On death" in the settings or `/drifter config respawn bed|lives|permadeath`:
  back at its bed 5 to 30 s later with nothing (bed); the same up to N times, then "<name> left the game" for good
  (limited lives, the default, 5: `/drifter config lives <n>`); never back (permadeath: its base stays as it was). No
  bed: near its base's middle. No base yet: at the world spawn within the spawn radius, as a player with no bed (the
  user, 2026-10-20: "make new drifters without a base respawn too"; it was gone before, the design's first rule). Name, skin, memories, friends,
  grudges and its group stay; XP, effects and what it carried don't. Waiting entries are saved with the world, and one
  whose home isn't loaded comes back when it is (nothing is loaded for it). `/drifter respawns` lists who's on the way.
- Death messages as a player's ("<name> was slain by Zombie"), setting "Death messages" / `/drifter config
  deathmessages`. A bad day (three deaths in one): the impatient may rage-quit, "left the game" for a day, then "joined
  the game" again.
- Not for Drifters met once (encounters, delvers), a manhunt hunter (its own respawn), battle soldiers, or one removed
  with `/kill` (that's an admin removing it).
- The corpse run (`skills/CorpseRunGoal`): back with nothing, if what it dropped is worth it (3+ things that aren't junk,
  not in lava or the void), within 200 blocks, and under five minutes old, it runs back, looks round and picks it up;
  if a player or Drifter killed it, only the bold go. Frustrated after dying.
- Debug: `/drifter home bed <pos>` gives the nearest Drifter's home that bed.
- Tested: limited lives 2 (back twice, then gone for good with "left the game"), permadeath, bed respawn beside the bed
  ("back at its bed, with nothing (4 of 5 lives left)"), the corpse run in the game client (back at its bed in 10 s,
  ran back, picked up its sword, cobblestone and bread). On a hill the run back fell into a ravine (route-finding over
  ground it hadn't seen, not the run itself).
- Not done from section 20: re-equipping from home storage before the run (its usual goals do that after), a fear
  lesson about the place, vengeful Drifters coming back armed for a player who killed them.

## Settings screens, rebuilt (2026-10-20)

The user: "upgrade the config menu, some stuff is off the screen and it kind of looks a bit ghetto, i would have to
adjust my scale to reach some options". All four pages (`client/config`) are now the game's own scrolling options list
(`DrifterOptionsScreen`, on `OptionsSubScreen`): a title bar, settings two to a row under gold section headings
(Drifters, When a Drifter dies, Toward players and each other, World generation, More), short labels, a tooltip on every
setting saying what it does, and Done always on screen; the list scrolls at any GUI scale. The encounters page groups
its 26 kinds (Bounties, Danger, Someone needs help, Drama, Peaceful and odd) with full names. Test harness: `screen
<page>`, `scroll <n>`, `gui <scale>` lines.

## Faster skins; mob sounds checked (2026-10-20)

The user: "lets fix the zombie groan problem and figure out how to load skins faster".
- **Zombie groans:** not a bug after all. Drifters already hear creatures' own sounds (groans, rattles, hisses) through
  `CreatureHearing` and the entity sound hook; the "TODO" in `Hearing` saying they didn't was out of date (and the
  summary given the user was wrong about it). Checked: a zombie that couldn't move, sealed behind stone 15 blocks off,
  heard 10 times in a minute ("heard a zombie behind, about 15 blocks away"). The note is fixed.
- **Skins** (`entity/RealSkins`): before, one lookup started a second (two at once), each a chain of Mojang calls, and
  nothing was kept between runs: 30 new Drifters took a minute (0 after 3 s, 8 after 10 s). Now:
  - every answer is kept on disk, `config/drifter/cache/skins.json` (a skin a month, "no such account" a week, the
    newest 3,000), for every world and later runs;
  - a pool of 40 ready skins from the bundled list is topped up in the background, four fetches at a time;
  - new Drifters' names are asked about together (twenty a batch, two batches at once; Mojang takes ten names a
    request); as soon as that's back, the ones with no account take a ready skin; the ones with one get its skin;
  - a new Drifter asks on its first tick (it used to wait for its 5-second check);
  - a pause of 30 s only after four empty answers in a row (an account that's gone from the bundled list isn't a busy
    Mojang: pausing at those held everyone up).
  Measured, 30 new Drifters at once: with the cache, 28 within 3 s and all within 9 s; from an empty cache, all within
  10 s. In the game client, real skins showed 3 s after 8 Drifters appeared. With no internet, cached skins still serve.

## Varied gear, and warband battles (2026-10-20)

The user: "can we add more variety into the gear? and then make that a random encounter?"
- `world/SoldierKit`: an army has a colour and a standard (militia: leather and stone; regulars: chainmail and iron;
  veterans: iron and diamond, some enchanted). Each fighter's armour is picked a piece at a time (now and then a bare head),
  leather dyed the army's colour, metal trimmed in it (red: redstone trim, blue: lapis...; a pattern per army), shields
  painted it; the captain wears the army's banner on its head, carries a grade better blade and the war chest
  (emeralds). Weapons are its favourite kind (modded ones too) at the army's grade; archers go lighter with a quiverful;
  everyone has a bow and a few arrows, food from a few kinds, a pickaxe and blocks. Everything is worn (a tenth to half
  its durability gone), so a battlefield isn't a free diamond shop.
- `/drifter battle` armies use it (two colours, one standard at random); several battles can be on at once now
  (`Battle` keeps each soldier's battle and side).
- Armed encounter Drifters use it too (`EncounterSpawner.fighter`, by the player's gear as before); a group (bounty
  hunters, an ambush, raiders, a revenge squad, hostage guards, escort bandits, a convoy) shares one band's colours.
- **Warband battles** (`encounter/Warbands`, the 27th kind, "warbands"): 55 to 80 blocks off, two bands of 6 to 10 in
  their colours fight it out ("for the Orange Band!"). Watch, or join: a side the player hits turns on them; a side
  breaks at two standing ("fall back!", they run; "victory! the field is ours!"); if the player brought one of the losers
  down, the winners' captain brings them the war chest (if the captain fell: "take what you like from the field"). Their
  gear stays where they fell. No crime in it. Eight minutes at most.
- Tested: a 30v30 headless battle (fought out, one arrow into a friend); close-ups in the client (dyed leather, painted
  shields, trims, banners on the captains); a warband battle in the client (rallies, a warning to stay out, the player
  downed one of the Orange Band, the Yellow Band won and thanked them).

## More whims (2026-10-20)

The user: "then add more whims". Twenty new ones in `drifter_whims/everyday.json`, most written as steps whims (the new
system): smelling a flower, giving a cat a scratch, patting a dog, a gold nugget tossed in the water for luck, staring at
its reflection, standing in the rain, leaning on a fence, knocking on a door, a smoke signal (a hay bale under a
campfire, a little build), bone mealing grass for flowers, opening and closing a gate, stepping on a pressure plate,
reading at a lectern, humming to itself, watching the sunset with a friend, turning a diamond over in its hands,
counting its emeralds, nodding to an iron golem, polishing its shield (Spartan Shields), watching lava glow. 141 whims in
all. Tested by forcing each in a little set-up: done as written (the coin really leaves its pack). Steps whims: a block
target only has to be in reach if a step uses it (water and pressure plates can't be aimed at).


## Milestone 22: Civilization, groundwork in Drifter (started 2026-10-21)

The user: "same repo, call it Drifter: Civilizations, go ahead" (the plan in docs/CIVILIZATION.md, "Groundwork").
- **Step 1, two jars and the API:** Drifter: Civilizations is a second mod in this project (`src/civ`, its own jar,
  asking for exactly the same Drifter); Drifter's API, `com.dinglebob.drifter.api` (docs/API.md).
- **Groundwork 1, the directory:** `DrifterRegistry` keeps a `DrifterRecord` for every Drifter that has lived in the
  world (alive, dead and coming back, gone), readable while it's far away and unloaded; `/drifter directory`.
- **Groundwork 2, news that travels:** `social/News`, `social/NewsBook` (a Drifter's news, up to 32, saved with it).
  News starts as a player's deed that moved a Drifter's trust by 15 or more, a death seen (who did it, if a player or a
  Drifter), or another mod's (`DrifterApi.tell`). Two Drifters in sight within 8 blocks that don't dislike each other
  (trust above -15): once a minute at most, by sociability, the teller passes on its newest three the other hasn't heard.
  Believed by trust in the teller (a stranger: half), x0.7 a telling, five tellings and two game days at most. A player's
  deed moves the listener's trust in them by about a third of what it did to the one it happened to (x1.6 for a friend);
  a death saddens a friend (grieving) and lowers trust in a player killer. Now and then out loud with a player within 16
  blocks: "did you hear? <player> attacked <name> again and again". `/drifter news`, `/drifter news tell <text>` (debug),
  `DrifterEvents.HEARD_NEWS`, `DrifterApi.news()`; API version 2. Group members still swap places (danger, ore) as before.
- **Fixed on the way:** Drifter death messages always said "<name> died" (the game forgets the fight as it dies, and the
  message was read after); now read before ("<name> was slain by Steve", "hit the ground too hard").
- **Groundwork 3, Drifters trading with each other:** `trade/DrifterDeals` (docs/CIVILIZATION.md has the rules):
  asked for when two meet (once in five minutes a pair), priced by `ItemValues`, greed and friendship, paid in goods the
  other wants or emeralds, a friend's small gift owed back; both walk up and toss their side over (the real items,
  targeted at the other), then pick them up. `Storage.spare` (what it can give away; paying a player back uses it too),
  `Trading.want(drifter, stack)`. `/drifter barter`, `DrifterEvents.TRADED`.
- **Groundwork 4, shared things and bigger groups:** `Home.shareFrom` and `DrifterGroups` share a base among the members
  every 5 s (chests, the plan, hut and house finished, farm, pen...); `Group.chests` and `Group.limit` (saved);
  `BuildPlanActivity` claims (builders on one house take different blocks), helping a mate who's building, one hut builder
  at a time (`Goals.mateDoing`); the "Group size" setting (config screen, `/drifter config groupsize`);
  `DrifterApi.setGroupLimit`; debug `/drifter house site <corner>`.
- **Groundwork 5, roads:** `PathSearch.ROAD_DISCOUNT` (0.7) for steps on a known block in `drifter:roads` (data tag:
  dirt paths); `DrifterMemory` counts how often each block is walked (saved, halved daily); `skills/PathGoal` wears a path
  in with a shovel where it has walked 6+ times near home (16 blocks a go, then half a day's rest).
- **Crash fixed (from groundwork 1, in 0.22.0 and 0.22.1):** when a Drifter unloaded, the directory checked its bed's
  block, which could load a chunk in the middle of the game unloading chunks: "ConcurrentModificationException in
  DistanceManager", the server (or a single-player world) crashed. Found by the 30-day run in its first minute (50
  Drifters walking out of the loaded area). The unload snapshot now waits for the end of the tick and reads nothing from
  the world (its stage and bed as last recorded; working out the stage looks at the world, which crashed the second
  run); bed checks skip chunks that aren't loaded. Fixed in 0.22.3 (0.22.2 had half of it).
- **Groundwork 6, the 30-day run:** done (see docs/CIVILIZATION.md "Progress"): no crash after the fix, 0.5 to 2 ms a
  server tick; Drifters drift out of the loaded land over the month; 40 deaths (15 falls); no group formed in 30 days
  (close neighbours become rivals; friendship takes ~100 minutes in sight): a question for the user before towns. Test
  scripts: `forceload` takes 256 chunks at most per command.
- **Step 2, the chronicle and "one island"** (in the addon: `drifterciv/Chronicle`, `drifterciv/Island`), and Drifter's
  API: `spawn`, `setRoam` (Brain roam centre and radius, saved; `ExploreActivity.pickDirection` keeps inside),
  `BUILT` (hut and house finished, in play and in catch-up), `FEUD` (DrifterWars), `inBattle`, `groupById`.
- **Numismatic Overhaul (optional):** `trade/Money` (coins by item id, no build dependency), `Trading` plans in coins,
  `Money.makeChange` before paying, `DrifterDeals` pays in coins, `ItemValues` coin worth, `Storage` keeps up to 64 money
  on it. Untested in the dev server (the mod doesn't load there); in-game check in TESTING.
- **Moving out of the hut (the user, 2026-10-21, for the plain mod: "make sure their hut is mined when they make their
  house and they can just move their stuff to the house"):** `brain/activity/MoveInActivity` and the `movein` goal:
  once it sleeps in its new house, it puts a chest in the house for each hut chest, carries the hut chests' things over a
  load at a time (keeping its free slots), breaks the empty hut chests, takes the hut down roof first (the floor stays),
  picks up what came off it (`Home.hutTakenDown`, saved, shared with group mates); new chests go in the house after that.
  Debug: `/drifter house done` (the house at its site counts as built and lived in). Tested in parts (the chest into the
  house, a load from the hut chest); the whole thing to be checked in game (test runs kept being cut short by other
  things: a hut door that trapped it, flat-world mining).
- **Falls fixed (rough ground):** the 30-day run had 15 of 40 deaths from falls; a 5-day run with the black box on
  showed both of its falls were parkour jumps landing 3 blocks lower while already hurt (hungry): the landing (a fall of
  about 4 blocks from the top of the jump) costs up to a heart, and jumps never checked its health the way drops do.
  Now a jump that lands lower must cost no more than a drop may and leave it 3 hearts (`PathSearch.landingSafe`). The
  next 5-day run: no falls, but four drowned gathering cobblestone off lake beds: stone with water touching it is now left
  alone like ore (`KnownBlocks.notWorthDiving`; clay and sand it still dives for). The third 5-day run (30 Drifters):
  5 deaths, all killed by other Drifters; no falls, no drownings.
- **Found, not fixed:** in flat test worlds a Drifter sometimes ends up shut in its own hut (walking at the closed door
  without getting through; a direct test with a closed door worked both ways, so the cause is still unknown), and the hut builder often reports "couldn't mine Air ... there is nothing there". To look at.
- **The town plan agreed** (the user: "go with your recommendations on all of them"; docs/CIVILIZATION.md).
- **Step 3a, a town made ready for testing:** `drifterciv/Town`, `Towns` (saved, `drifter_civ_towns`), `TownBuilder`
  (`/drifterciv town spawn [households] [name]`), `/drifterciv town`, `/drifterciv towns`; Drifter: `HousePlacer` (a plan
  put up at once, turned), `Home.noHut`, API calls for houses and groups; group mates are never rivals. Chronicle: names
  from the group (a new Drifter isn't in the directory yet); a town made at once is one line, not one per person.
- **The house parts world (the user, 2026-10-21: "a flat world creative ... containing all of the parts that build a
  house, but defaulted to oak ... i want to edit some of the stuff by hand, and then you can take the world back and
  update the generated house parts"):** `/drifter house parts` (debug, `building/plan/gen/PartsExhibit`) lays out each part
  of a generated house on its own on the same small oak house (18: walls/door/windows, five roofs, base row, frame, beam,
  two floors, sills, tall windows, porch, chimney, off-centre door, loft, corner tower, wing, attic windows), a house of
  each of the 11 styles, and the hand-made plans, in the generator's own materials (oak main wood, spruce second wood,
  cobblestone stone), signs by each; what's where (and the style each part was drawn from) goes to
  `drifter_house_parts.json` in the world folder. The world "Drifter House Parts" (flat, creative, cheats, peaceful,
  always day) is in the DRIFTER TEST saves. When the user has edited it: read it back (load it, save each exhibit's box
  with `/drifter house save`, compare with the original drawing) and change the styles (data) or the generator (code)
  to match; edited hand-made plans replace their .nbt files. The generator now also lists sills, tall windows and an
  off-centre door among a plan's features (no change to the plans it makes).
- **The houses revamped (0.22.7; the user, 2026-10-22: "they need to be a bit bigger, especially for a town... lets
  revamp all the house plans using references from my edits but more from this guidebook"; their "Minecraft Base
  Building Guidebook").** A new generator, `building/plan/gen/HouseGenerator` (ids `house:<style>_<n>`), and twelve new
  styles (`data/drifter/drifter_house_styles`, see its `_README.txt`): sizes from the pillar formula (13x9 to 21x16, a
  long rectangle; squares get a hip roof); a stone foundation with a stair plinth; log posts and beams on the frame line
  with the wall a block behind (the posts carried through inside); upper storeys jettied (flush on the frame line, a
  block past the wall below); storeys four high; windows centred in each bay, the same on every storey (sills, lintels
  in some styles); the door centred (a double door in a 4-wide bay; at a third on an even count; in place of the middle
  post on a gable end), framed in logs, set back, a three-wide stair step, a light, a little roof over it when no eave
  is; a 45 or steep gable or a hip roof on a row of the roof's full block on the top beam, a block of overhang, a ridge
  cap, the rake trimmed underneath, the gable a lighter block with a window; tie beams inside at the posts; a vaulted
  ceiling on wide roofs; stairs up (not ladders); wings (L or T, 50-70% wide, ridge two lower), corner towers, two rooms
  in long houses; torches inside at the posts at floor+2 (as the user moved them). No chimneys (the user removed both in
  their edits), porches, flat or shed roofs. The user's edits to the five hand-made plans (torches, windows, the stone
  cottage's chimney out) replace their files. Old worlds: plans from the first generator (`gen:` ids) are still made
  when a half-built house asks for one (`drifter_house_styles_legacy`, `PlanGenerator`). HousePalette: the frame and
  roof wood is the one the Drifter knows that stands out most against its main wood (towns: a set partner for each
  wood). Towns (addon): only the bigger houses (15 to 25 across), the town 144 by 128.
- **Found and fixed while testing the bigger houses:** a builder that ran out of one wood and switched to another took
  its own frame for a tree and chopped it out (the "clear the spot" step now counts the same block in another wood as
  built); a house may be left with 2% of its blocks out of reach (was 6 blocks), so a big house still counts as built.
  Tested (a Drifter building, deep flat world): a one-storey cottage (833 blocks) finished in about three game days of
  steady work (bar 16 blocks of ridge cap out of reach), the Drifter moved in; a two-storey hip-roofed house (1198 blocks) reached 1016 in nine days, its roof
  slow (out of reach but by pillars). Found: plans are only checked for blocks touching; a block a builder can't place
  against anything (an upside-down stair on a beam) stopped a hip roof outright until the row on the beam became full
  blocks.
- **Step 3b, towns founded by their people (0.22.8):** `drifterciv/TownFounding`. With the addon (Drifter:
  `DrifterBonds.neighbourly`, `social/NeighbourGoals`, off in the plain mod): neighbours visit (one it just saw close by,
  or whose home it knows: it walks there, looks round, goes along with them a few minutes; as often as a quarter of a day
  to two thirds of one by sociability), greet (5 trust each way), and while close enough to talk trust grows 16 times
  faster (8 a minute); only those clearly at odds (trust -15 or less) are rivals over the same ground, and groups that get
  along don't feud for living close; a trade adds 6 trust each way and news passed on 2. Friends team up into groups as
  before; a group of three or more, 300 blocks
  from any town, founds one round its leader's home (the plaza there, two roads crossing it; the group named after it,
  twelve at most; no more huts; a chronicle line, and news of it to pass on). The first house is the leader's, the
  smallest plan it likes, on the nearest plot along a road whose ground it has seen (else the best spot it knows near the
  plaza), built by them all (they live at the
  leader's home, so they share its plan). Once it's up, each of the others takes the next plot (a plan to its taste,
  different from its neighbours' if it can, its door to the road) and a home of its own there, and builds its house
  (no bed till then: it digs in; the group's chests still its). Newcomers to the group get the next plot.
  `/drifterciv town` lists each household's state. Drifter API 3: `housePlansFor`, `siteFor`, `siteFinder`,
  `buildHouseAt`, `homeAtHouse`, `addChests`, `addTrustInDrifter`, `groups`, `setNeighbourly`. Drifter: a town household
  (no hut, a plot to build) works on its house at any stage and with any personality, and wants a bed though it has no
  hut.
- **Found and fixed while testing it:** the house site search read the ground a block too low and took the dirt under the
  grass for "the ground it has seen" (which it never sees), so on flat land a Drifter never found anywhere to build a
  house, and elsewhere its house sat a block low (only where the surface had been dug was any ground "seen"); a builder
  left torches and a mate's scaffolding where its floor went ("someone's torch in the way"); a builder given a new house
  while finishing the old one called the new one finished at once; lanterns it can't get it now goes without (like
  windows); a Drifter tidying scaffolding, or a builder redoing a block, dug out a block it stood on over a drop and fell
  (now never one under any part of its feet); a hut or house repaired was "finished" again in the chronicle each time.
  `/drifter social` now says whether it knows another Drifter's home and when it last saw them.
Tested (headless): three Drifters teamed up at once (debug) on deep flat ground: the town founded at once, the first
  house (a cottage) up on day 3 by the three of them, then a plot each, their own houses started (that ground has no
  trees: wood ran out). Twelve Drifters with nothing on seed 777, nothing forced, 16 days: a team on day 4, a town
  (Copperridge) on day 5, two newcomers on days 10 and 11, the first house up on day 13 and a plot each for the other
  four; again after the last fixes: a town (Willowdale) on day 7 that grew to seven households by day 16, its first house
  still going up (gathering birch; hilly ground). Falls still kill a Drifter now and then in long runs (jumps, parkour,
  pillars): to look at.
- **Step 3c, the town's store (0.22.9; the user: "do whatever you think should go next"):** Drifter: town builds, something
  a group builds together that isn't a house of theirs (`Home.project`, saved; the `TownBuild` goal, as keen as on its own
  house; `BuildPlanActivity` in project mode: no site to choose, nothing to move into, `BUILT` "town:<label>" when done;
  builders share the work by the same claims as mates on one house; one wood palette for all). Store plans
  (`store:<house plan>`): the house with up to eight chests along its inside walls (on the floor, against a wall, facing
  in, clear of doors, torches and stairs, never side by side), made from the plan when asked. API: `storePlan`,
  `townBuild`, `joinTownBuild`, `townBuildOf`, `endTownBuild`, `chestsIn`. The addon: once half a town's households have
  their houses (two at least), its people build its store (the smallest one-storey plan, the nearest free plot to the
  plaza); when it's up its chests are everyone's (`town.store`); newcomers and those away join in, in the same woods.
  `/drifterciv town` shows the store. Tested (headless, deep flat, three founders, houses marked built by command to
  skip ahead): the store begun on day 1, finished on day 4 by the three of them, all eight chests standing.
- **The made-ready town sits on the land (0.22.10; the user: "do towns level the ground to make a huge flat land? ... lets
  make it go on top of the land, like a minecraft village"):** `/drifterciv town spawn` no longer flattens 144 by 128
  blocks: only the plaza is evened (at its ground's middle height); roads are dirt path over the ground (planks over water,
  what grows on them cleared); each house goes where its footprint's ground rises and falls 5 or less with no water, at
  that ground's middle height, only its footprint filled and cleared (a spot that doesn't suit: on along the road); the
  store and each field at their own height, the pen's fence over the ground, lamps and the Drifters on it. Towns founded
  by their people already built that way (each plot on ground the Drifter has seen, the house at its middle height, only
  its footprint touched). Tested in the client on seed 777 (hills, forest, a lake): five of eight households found ground.
- **Step 4a, the market (0.22.11; the user: "then after that do 4"):** Drifter: shops (`api/ShopKeeper`,
  `DrifterApi.setShop`): a Drifter with one, right-clicked, opens the villager trading screen (`trade/DrifterMerchant`)
  with what its shop offers; each trade moves the real things (the goods out of its pockets, the payment in; offers only
  as many times as it has the goods or the money). `trade/StallGoal`: in its open hours (morning to mid-afternoon by its
  own sense of time) it takes what it means to sell from its home's chests (the town's store) and stands at its stall.
  A stall plan (`town:stall`: a plank counter, the keeper behind it, a slab roof on fence posts, a door at the back),
  `DrifterApi.stallPlan`, `stallStand`, `worth`, `money` (emeralds, or Numismatic Overhaul coins when it's here),
  `moneyWorth`, `coins`, `trait`, `rememberChest`, `openShop`. The addon (`Market`): once the store is up the town's
  people build a stall (its counter to the road), and the most trader-like of them (greed and sociability) keeps it;
  it sells up to four kinds the store has plenty of and buys, with money it has, what the town is short of (food, iron,
  coal, logs, stone, wool, leather, torches); prices from supply and demand (an item's worth, up to 1.6 times when the
  town has none, down to half when it has plenty; a player selling gets 0.8 of it). The made-ready town gets a stall on
  its plaza, its people know their store, the keeper has 8 emeralds. `/drifterciv prices`, `/drifterciv shop` (debug:
  the keeper's screen as if right-clicked), `/drifterciv town` shows the stall.
- **Found and fixed while testing it:** a Drifter fetching from (or checking) a chest it had never been near gave up at
  once ("can't get to its chest": it knew no floor round it); it walks over to look first now. On a hillside the
  made-ready town's store door opened onto a bank of earth: the store now goes where the ground suits it along the road,
  with a flat apron to the road, and each house has a block of level ground round it. Drifters went to "loot" the stall's
  barrel counter: it's planks now.
  Tested: in the client, the trading screen with the keeper's offers (bread for emeralds; wheat, iron, logs, wool,
  leather bought); headless, a made-ready town's keeper took 64 logs from the store and sold them; a town founded by its
  people built its store (day 4) and stall (day 4) and its keeper sold cobblestone and steak.
- **Fixes from the user's test of 0.22.11 (0.22.12):** the made-ready town put each Drifter two blocks out from its
  door, still under the roof's overhang, so they started on the eaves: now out past the house's whole box, on the
  ground. Floating leaves (and trunk pieces) wherever something was put up at once (catch-up huts and houses, camps, the
  made-ready town): the build cut through trees, and blocks set at once never tell the leaves round them to rot. New
  `Felling.tidy(level, area, built)` after each of those (and `DrifterApi.tidyTrees` for the town's ground, roads and
  fields): trunk pieces with nothing under them but air, leaves or the build go, and every grown leaf no tree's log holds
  within 6 steps through leaves (the game's own leaf distance) goes. Tested on seed 777's birch forest: 332 loose leaves
  and 8 of 8 Drifters off the ground before, 0 and 0 after.
- **The user's town bug list of 2026-10-23 (0.22.13; step 4 on hold till it's done, the user: "dont move on to step 4
  yet"):**
  - Trees "always half cutoff": clearing for a build now fells each tree in the way whole, trunk and crown
    (`Felling.fellTree`, `DrifterApi.fellTree`; the town's ground, roads, plaza, fields, catch-up huts and houses), the
    dirt under its trunk grassed over.
  - Chests facing the corner: houses are furnished by `building/Furnishing`, from the user's furnishing guidebook: a
    storage wall (double chests two high, the crafting table set in it), the furnace and smoker with the fuel chest, a
    dump chest inside the front door, a bedside chest; each facing the floor it's used from, its back to a wall, never in a
    doorway or at the foot of stairs, always leaving a way to everything. The made-ready town furnishes every house at
    once (its people know their things: chests, stations, the inside of their house, the store, the plaza); Drifters
    moving chests into their house or putting a station down at home use the same layout, standing in front of the spot
    so it faces the room.
  - A dark town and mobs killing its people: the made-ready town works out the block light over its ground and inside
    its houses (`TownLights`, as the game spreads it) and puts lamp posts outdoors (not on roads or fields) and torches
    indoors wherever a monster could spawn at light 0 (the town's own ground only, not the woods round it).
  - Pen fences on hills: at a step the lower post goes up to the next one's height, so they join.
  - Mining into houses and pillaring inside them: a Drifter living in a house from a plan started its mine staircase a
    hut's few blocks from home's middle, inside the house (now outside its walls; `Home.planBox`, saved, and
    `inOwnBuilding` counts the plan house), and made-ready townsfolk had never seen inside their houses (now they know
    them). Tested: no digging at house floors, "can't get to its chest" 29 -> 0 in two days.
  - "Way more spaced out and dynamic like a village": the made-ready town is laid out first (four roads out from the
    plaza, side roads off them, more where the land's broken; roads stop at water wider than 5 blocks), then the houses go
    along the roads nearest the plaza first, 6 to 12 blocks apart (fire), 2 to 5 back from the road; each on its own
    ground (up to 7 blocks of slope), a cobblestone foundation wall where the land falls away, the land round it eased up
    to it over 3 blocks; grass buried under fill or a house turned to dirt. Towns founded by their people keep 6 blocks
    between plots too (was 2).
  - The made-ready town's people are mid-to-late game (iron armor with some diamond, their weapon, shield, bow, iron
    tools; `DrifterApi.equipSettled`), their houses stocked (stone, wood, iron, coal, food).
  - Noted for later house plans: more stone (wooden towns burn).
  Tested: birch forest (seed 777 at 0, 0) and a hilly coast (352, 32): 8 of 8 and 8-9 of 10 houses, everyone alive after
  two days, 0 loose leaves, ~70 lamp posts.
- **Founded towns get side lanes, foundations and lighting too (0.22.14; the user: "give founded towns the side lanes,
  foundations and lighting too").** All of it done by the townsfolk, block by block, with what they carry:
  - Side lanes: `TownRoads` lays out a lane off a main road for every three households (six at most), saved with the
    town; plots are found along every road and lane, nearest the plaza first, never on a road.
  - The roads made real: a new goal "work on the town's roads" (`RoadWorkActivity`): by day, with a shovel, the
    townsfolk cut the grass on the road and right-click the ground into dirt path, from the plaza out, as far as the roads
    lead to houses (and a little past). Only ground they've seen; water, trees and builds are left.
  - Foundations: a Drifter filling under its house's edge uses cobblestone (if it has some) instead of dirt, so a house
    on a slope stands on a stone wall (every Drifter house, not only in towns); then, in a town, "even out the ground round
    its house" (`EaseGroundActivity`): the ring next to the house dug or filled level with its ground, the next one
    halfway to the land (`Home.groundEased`, saved).
  - Lighting: "light up the base" covers the whole town for its people (its roads' sides and round its houses, not on
    roads or fields), and they make torches for it from their coal and sticks.
  - API: `TownWork` (roads, town ground, middle, reach) and `DrifterApi.setTownWork`; `/drifterciv town` shows the lanes.
- **The user's notes of 2026-10-23, second batch (0.22.15):**
  - The plaza "way too big" and "a hole when its on hill": now 15 by 15 (was 21), at its ground's middle height, a stone
    edge where the land falls away and the land round it eased to it over 5 blocks (not dug in as a pit). The store and the
    fields sit on the land the same way (the old `even`, cutting straight down, is gone).
  - "Drifters tend to pillar on farmland": a field dug flat into a slope left walls too high to step out of, so a Drifter
    working it put a block down to climb out. Fields are eased into the land now. (A temporary log of every block placed
    on farmland over two town days caught none.)
  - More than one household in a house: a house takes one household, and one more for each other room big enough to
    sleep in, up to four (`Furnishing.households`, `DrifterApi.households`). The made-ready town fills them (16 or 17 people in
    8 houses), each newcomer's bed in a room away from the others' (`BuildPlanActivity.bedSpot` keeps away from beds already
    there), a chest by it, the house's chests and stations shared (`DrifterApi.settleInToo`). In a founded town, a member
    without a house moves into a spare room of a finished one before it takes a plot of its own (it brings or makes its own
    bed). `Town.Plot.residents` and `room`, saved; `/drifterciv town` shows who has a room where.
- **Chat revamp (0.22.16; the user, with their guidebooks "How Minecraft Players Chat" and "The Science of Gamer Text
  Chat": "revamp/expand all the chat lines, including the response lines... expand it to the hundreds").** Every line is
  in data now (`data/drifter/drifter_chat/`: `reactions.json`, `encounters.json`, `answers.json`; `social/Lines.java` loads
  them, a datapack can add more): 946 lines in 118 kinds plus 242 in whims, written as players type (lowercase, no final
  period, clipped words, rituals), each personality archetype with a voice of its own (a Drifter mostly uses its own).
  The canned reactions keep a habit per Drifter; "gg" split into gg_won (it killed you) and gg_lost (you killed it).
  Answers: 569 phrases in intents (yes, no, ready, unsure, greeting, night, bye, thanks); a message means the intent of
  the longest phrase found ("no problem" yes, "hell no" no, "yeah no" no), stretched letters and punctuation don't matter
  ("NOOOO." no, "Absolutely not." no, "na" no); an unclear answer ("idk", "maybe") gets asked again. New rituals
  (`social/ChatReplies.java`): Drifters near you answer your "hi" ("o/"), "gn", "cya" and "ty" ("np"), say "wb" (or
  "welcome") when you join and "cya" or "gn" when you leave, "rip" at a player's death message ("lmao", "classic" for a
  silly one: fall, lava, drowning...), "rip" or "o7" for a Drifter (always for a friend), "gg" or "W" for an advancement
  (`PlayerAdvancementsMixin`). `/drifter lines meaning <text>` and `/drifter lines say <key>` to try them.
  docs/CHAT_LINES.md is generated from the data: `python tools/gen_chat_doc.py .` after changing lines.
  Tested: 66 answers from "na" to "Absolutely not." all read right; in the client, hi, gn, a death and three advancements
  answered. Not seen live: an escort or race answer (no village or hill near the test spots), the "ty" reply (rate limit).
- **A part of a name calls a Drifter (0.22.17; the user: "yeah add that"):** a part of its name 4 letters or longer
  in chat ("grumpy" or "baker" for xXGrumpybakerXx, "onyx" for golden_onyx_3), not an everyday word ("over", "just"...),
  calls it as the whole name does (`SocialWatcher.called`). Tested in the client: all three called it, "just look over
  there" called nobody.
- **Dynamic chat, phase 1: Drifters talk with each other (0.22.18; the user: "greatly expand small talk conversations
  between drifters aswell as player interaction / how can we make this the most dynamic chat system"; they picked: only
  players nearby hear chat, requests from players trust-gated (phase 2), built in phases with a test between).**
  - Who hears: every Drifter line now goes only to players within "Chat heard within" (config screen, Drifters section;
    48 blocks, 16 to 256) in the same dimension, plus the player an encounter is with; the server log gets all of it.
  - A diary (`social/talk/Diary`, saved): ore it mined (counted per kind), mobs it killed, close calls (5 health or
    less), its deaths, what it built, who it traded with, places it found. `/drifter diary`.
  - What each has to say (`social/talk/Facts`): words for the lines (`{a.ore}`, `{b.death}`, `{a.armor}`, `{a.town}`,
    `{a.news_who}`...) and flags for the topics (found_ore, died_lately, friends, rivals, has_news, rain, night...),
    only from what it knows.
  - Topics (`social/talk/Topics`, data `data/drifter/drifter_talk/`, 41 topics, 1153 lines): ore and rare finds,
    death stories, close calls, mob hunts, builds, gear, trades, places, hunger, hurt, what are you doing, homeless,
    hut to house; how are you, morning, evening, night, rain, thunder, nice day, news, warning about a player, praising
    one, town, group, catching up, rivals needling, strangers meeting, bored, plans, complaints, bragging, seven jokes.
    Each in the personality voices; a question carries its own answers ("where's your base?" "over the hill"); effects
    (news passed on, trust, rivalry).
  - `social/talk/Conversations` and `ConverseGoal`: two idle Drifters near a player (one talk per player's earshot)
    walk up, face each other and take turns, typing time by line length; one talk at a time each; gaps per Drifter
    (chattier ones talk more) and per pair. One busy with something types as it works (players chat while they
    build: a Drifter's job only gives way at a checkpoint, and gathering 3 logs has none till the end); one that's free
    (the converse goal, "someone's waiting" as a deal, wins at its next checkpoint) walks over and faces the other.
    16 blocks apart ends it. The weather and the night come up only when both are under the open sky.
    `/drifter talk [topic]` starts one between the nearest two.
  - docs/CHAT_LINES.md section 6 lists every topic (generated).
- Next in the chat: phase 2 (players: wyd, coords, follow me, trades, team up, help, compliments and insults,
  trust-gated, a Drifter remembers what you said), phase 3 (Drifters start talks with you, several join in, town gm
  rounds), phase 4 (typos, split messages, a chattiness setting); phase 5 (Chat Heads, Talk Bubbles) done first. Then the well, the plaza, then step 4.
- **Chat phase 5: Chat Heads and Talk Bubbles (0.22.19; the user: "chat head and chat bubbles support (both are mods
  found in drifter test)", "yeah do phase 5 next").** Both optional (found at runtime; nothing changes without them).
  - Neither saw Drifters: Chat Heads looks for a message's sender among the players (tab list), Talk Bubbles finds the
    named player and draws in the player renderer.
  - With each Drifter line the server sends the players who hear it `DrifterSaidPayload` (entity id and text) just
    before the line (`ChatLines.send`, the shop's "nothing to sell" too).
  - Client (`client/chat/DrifterSpeech`): the bubble over the Drifter is drawn by Talk Bubbles' own
    `RenderBubble.renderBubble` (by reflection), wrapped to its `maxChatWidth`, kept for its `chatTime` (its settings
    screen applies). Chat Heads: `mixin/compat/ChatHeadsMixin` (`@Pseudo`, `require = 0`) answers `detectPlayer` for a
    message that is the line just announced with a `PlayerInfo` wearing the Drifter's current skin.
  - Conversations: a talk no longer waits for both to stop; one busy types as it works (see phase 1).
  - Tested in the client with Chat Heads 0.15.7, Talk Bubbles 1.0.8 and Chat Patches 8.0.0: bubbles over Ann and Bob
    as they talked, their own faces next to their lines (Chat Patches' timestamps in front, lined up as a player's).
    `run/mods` keeps Chat Heads and Talk Bubbles for client tests.
- **Chat phase 2: asking a Drifter things in chat (0.22.20; the user: requests "Yes, trust-gated", "start 3 after").**
  - `social/talk/PlayerTalk` (called first from `ChatReplies`' chat listener): the intent is the longest phrase found
    (`drifter_chat/requests.json`, 23 intents, ~450 phrases, ~450 reply lines in voices). Who it's for: named (or a
    4+ letter part), else the one they talked with this last minute, else the one looked at within 16 blocks, else the
    only one within 10. It turns to them (`SocialWatcher.spokenTo`, the name-call look) and answers after typing time.
  - Gates by trust: info (wyd, hru, name, joke, bot) for anyone not hostile; coords to a friend (25), "around" to one it
    knows (10); base and places seen: coords to a friend, direction and distance to one it knows; follow and come: a
    friend, or known and familiar (as the crouch invite); team (15 min follow): a friend; help (drops its job, runs over,
    stays a minute): a friend, a helper, a brave one it knows; give: a friend or one it owes, from `Storage.spare`, worth
    4 + trust/2 + what it owes, as many as said or a handful; duel: fighters only, within 20 blocks, not Peaceful.
  - `RequestGoal` (come, help, give: walks over, tosses the items), `Spar` (friendly 1v1: VENGEFUL toward them in
    `Mode.toward`, the chase; nobody goes below a heart, `ALLOW_DAMAGE` stops the blow and that side loses; hits in a
    spar aren't wrongs; "gg" either way, +2 trust, +3 familiarity), `FollowMe` takes chat invites (longer for team,
    urgent: it said "omw"), stop following.
  - Memory: `Relationship` keeps compliments, insults, the last insult and when (saved); `SocialEvent` COMPLIMENT (+2,
    3 a day) and INSULT (-4, 5 a day). Insulted in the last day and not a friend: "not after you called me a noob".
  - DESIGN.md section 14 records the decision. docs/CHAT_LINES.md section 8.
  - Tested in the client: wyd ("gather birch log, wbu"), how are you, name, base ("homeless rn lol"), "can i have
    some bread" (stranger: "no"; trust 40: "here you go", walked over and tossed 3 bread), coords, follow me (followed),
    stop following, a compliment (trust +2), a 1v1 (took it, dropped its job, fought, stopped the blow at 2 hearts:
    "gg"), "noob" then a request ("after calling me a noob? lol no"), "are you a bot" ("bots dont play this good").
  - Found and fixed: a request removed when started (it dropped it after 5 s); a follow walk never ends (now walks to
    where they are); chat follow lost to a committed job (now urgent); "noob" read back as "that" (normalized "nob");
    a spar accepted from 40 blocks; no fight on Peaceful (it now declines there); a job with no checkpoint kept it from
    the fight (it drops it, as for help); a single crit could take the player from 5 hearts to none (blow stopped).
- **Chat phase 3: Drifters talk to you, join in, keep running jokes (0.22.21; the user: "start 3 after").**
  - `social/talk/PlayerChats`: a Drifter within 8 blocks that sees a player, with nothing pressing and not hostile,
    now and then brings something up (`drifter_talk/players.json`, 18 topics: hi to a stranger, a friend's name and
    its news, their gear, their weapon, how hurt they look, night and evening warnings, rain, what it found, its death,
    news, teasing about a running joke, still mad about an insult, thanks for a gift, bored, hungry). A topic can end on
    a question (`Topics.Question`: team up, come mining, want food, got food); the player's yes or no in chat
    (`PlayerTalk`, before requests) gets an answer and the action (team, follow, come, give_food). Gaps: a player
    every 90 s, a Drifter every 3 to 15 min by sociability, the same pair every 8 min. `/drifter talk me [topic]`.
  - `Facts.player`: the player as the Drifter sees them (stranger, known, friend, hurt, armor, held, a joke about
    them, an insult, a gift it owes for, news it has). `Facts.of` takes a null other.
  - Running jokes: a silly death (the damage types `ChatReplies` calls silly) seen within 32 blocks goes in each
    witness's diary as `JOKE` ("who|how", a week); `Facts` gives `joke_who`, `joke_what`, `joke_nick` (lines
    `joke.what.*`, `joke.nick.*`) and `shares_joke` when the other was there too. Topic `running_joke` between Drifters;
    `tease` with the player it's about.
  - Rounds: the first time a player is about in the morning (before 2500) near two or more Drifters who know them
    (familiarity 3, trust 0+), up to four say gm one after another (`round.gm`); getting into bed near one, gn.
  - Bystanders: after a talk between Drifters (topic `chime` lines) or an answer to a player (`chime.<intent>`), another
    free Drifter within 10 blocks may add its bit, after the answer.
  - Tested in the client: a gm round (two of three), Annie "wanna team up for a bit?", "sure!", "duo time"; Bobby
    "you're hurt! want some food?", "yes pls", "catch" and threw 2 bread; a fall death became "skydiver is back" and
    Annie and Bobby's "Tester jumped off a mountain lmao" / "legendary"; Drifters started their own talks between.
    Found and fixed: a bystander's "lol" before the joke it laughed at.
- **Chat phase 4: typing like players, chattiness (0.22.22; the user: "start phase 4").**
  - `social/Typing`: every Drifter line goes through it (`ChatLines.send` -> `Typing.type` -> `ChatLines.deliver`). A
    typo (swap, drop, double, the QWERTY neighbour) in a word of 4+ letters only, never a name or a number; chance
    3% to 11% by skill, newbies +6%, casual and speedrunners +3%, builders and hermits -2%; fixed with "*word" by
    tidiness (25% to 75%). A line of 16+ characters with ", " (or "? " / "! " midway) goes in two 35% of the time,
    the second part after its typing time. Config "Typos and split messages" (`typing`, on).
  - Chattiness (config screen cycle, `/drifter config chattiness`): quiet 0.4, normal 1, lively 2; chances times it,
    gaps divided by it: canned reactions and whim lines (`ChatLines`), the rituals (`ChatReplies`), talks between
    Drifters, talks with players, rounds, bystanders. Answers and encounters always.
  - `Lines`: not a line anyone said in the last 30 s if another will do.
  - Tested on a test server, 48 lines from a newbie and a builder: "comnig" "*coming", "only got enouhg for me"
    "*enough", "why do ghasts cry?" / "fire in their eyes", "nothing!" / "any ideas?"; the config line shows both.
- **A founded town's plaza (0.22.23; the user: "lets move onto the next thing", then chose: the leader's old hut torn
  down first, "well with real water", "lamp posts", "notice board"; no bell).**
  - Drifter: three generated plans (`HousePlans`): `town:well` (4x4: cobblestone floor and rim, the 2x2 basin left
    open, fence posts, a cobblestone slab roof; `WELL_WATER` the two corner cells to pour into), `town:board` (a plank
    board on two fence posts, three oak wall signs on its front, `BOARD_SIGNS`, a slab over it), `town:lamp` (a
    cobblestone foot, two fences, a torch). `DrifterApi.wellPlan/boardPlan/lampPlan/wellWater/boardSigns`.
  - Water: `TownWork.water(drifter)` (default none) and the goal `TownWater` (0.56; 0.72 once begun or with water in
    its bucket) -> `PourWaterActivity`: fills its bucket at water it knows (not the cells it fills, not its farm's
    water hole, not water it couldn't fill at or get to; a remembered wet chunk or a look round if it knows none), walks
    over and pours onto the basin's floor, one cell at a time; two corner to corner make an endless 2x2.
  - The addon (`Plaza`): once the stall is up and the leader's hut is down (`MoveInActivity` does that when it moves
    into its house), the people build the well (the spot nearest the plaza's middle that suits, by the leader's
    knowledge of the ground), then the board beside it facing it, then a lamp post at each corner (8 out, the nearest
    spot that suits; a corner with none goes without). Each a town build (`plaza`), all of them on it. The water job
    goes to the member with a bucket nearest the well. The board's signs: "Welcome to / name / est. day N", "N
    households / N people / leader: / name", "News: / the town's latest chronicle line"; brought up to date daily.
    Chronicle: began, finished, "has water", "was put up", "has its lamp posts". `Town.plaza` (plots), `wellFilled`,
    `boardDay` saved; `/drifterciv town` shows it; `/drifterciv town plaza` (debug: store and stall count as up).
  - Fixed on the way: a town member who chose a hut door and never built the hut kept its middle "in the way of its
    things" (`Home.inOwnBuilding`: not a hut taken down, nor one never built); a site was allowed over its chests
    (`PlanSite` blocks them now; a town's well went round the leader's old chest).
  - Tested headless (seed 777, four Drifters found a town, store and stall skipped): the well, board and four lamp
    posts built in three days, every block by them; the water fetched from a lake in two trips (one by Cy, one by Dee;
    earlier runs found the job losing to other work, trips cut short, a farm's water hole tried, and fixed those). In
    the client: the well with its water and roof; the signs' text read back ("Welcome to / Copperhollow / est. day 1",
    "3 households / 4 people / leader: / Bob", "News: / Day 4: The / plaza has its / lamp posts").
  - Seen, done next (0.22.24): the plaza kept the trees on it (in a forest a birch hung over the board).
  - Fixed after: the chronicle had "xXNovaXx finished its hut" seven times on one day (13 in the log, all in one catch-up
    of a Drifter coming back after 4.8 days away). Catch-up's `furnish` runs every slice, and its "the hut is done" step
    never checked that the hut was done already: each slice after the first set `hutBuilt` again, sent BUILT "hut" again
    (chronicle and diary) and felled the trees round it again. Now only the first time (`CatchUp.furnish`, as
    `BuildHutActivity.finished` already did). Checked by reading the log and code; `./gradlew build` passes, not re-run.
- **The plaza's trees cleared first (0.22.24; the user: "yeah clear the trees on the plaza too").**
  - `TownWork.clearOfTrees(x, z)` (a founded town's plaza once its stall is up) and the goal `ClearTrees` (0.6, an axe,
    within 40 blocks of the middle, a log it knows on the plaza that's a tree's): `GatherActivity` for logs `within` the
    plaza (a new filter: only there, and done when there are none it knows, rather than off to look elsewhere), whole
    trees, top included, so the leaves go.
  - The well waits for the plaza's trees (trunks on its ground) to come down, a game day at most.
  - Plaza pieces only on ground nobody built on (`Plaza.untouched`: natural ground, plants, trees from under it to over
    its roof: found in testing, a well round the leader's old furnace with a hole in its floor), and within 5 of the
    plaza's height (a lamp post had gone down a ravine).
  - The well's water: whose turn it is rotates every 3 minutes among those with a bucket, nearest first (the nearest
    couldn't get to it again and again), not while one is at it; it pours from close by over the rim (4 blocks, eye to
    the basin's floor) and from somewhere else if a pour misses (the strict look had it "can't get to the town's well"
    for the second corner).
  - Board signs left to right as you read them (they were the wrong way round).
  - Tested headless, 12 runs of 3 game days on seed 777 while fixing: last run, no tree left on the plaza, the well,
    board and all four lamp posts up, the water in in two trips; in the client, the plaza clear, the board readable,
    the lit well at night.
- **Step 4b: the town's money, buying at the stall, trade runs (0.22.25; the user: "lets do step 4, remove running a stall
  yourself"; chose: a town treasury fed by the stall's takings, Drifters buy what they're short of, a trader walks).**
  - The till: a town's store's first chest (`Town.till()`). The keeper keeps 8 emeralds on hand (`ShopKeeper.keepOnHand`)
    and after closing takes anything 2 emeralds over that to the till (`TownMoneyGoals.Bank`, `ShopKeeper.till`,
    `ChestErrand`: a trip to a chest to put or take).
  - Wages: the town's work reports itself (`TownWork.worked`: a road block 0.25 points, a plaza log 0.3, a bucket into
    the well 3, a block of a town build 0.15; an emerald is 6), owed in the town's book (`Town.owed`, saved, at most 10
    emeralds behind); one owed an emerald or more goes to the till and takes it (`TownMoneyGoals.Wages`; nothing in the
    till, nothing paid: "the town's till had nothing for its wages"); `TownWork.owed/till/paid`. Chronicle: "X was paid
    N emeralds' worth of wages in Town".
  - Buying (`BuyGoal`): short of food (little on it, peckish), torches, iron (no iron pickaxe) or wood (building its
    house), with the money, at a stall it knows (its town's, or one it has seen), open, its keeper behind it: it walks up,
    says so ("bread pls"), and trades across the counter as a player at the screen (`Counter`: its money to the keeper,
    the goods back; `ShopKeeper.soldTo`). 0.4, 0.62 when hungry.
  - The stall: food, iron and torches count as plenty from 12/16 (not 32) and come first in its six kinds; the keeper
    keeps its stall at 0.72 in its open hours and doesn't go on group mining trips then (found: off mining all day).
  - Trade runs (`TradeRuns` in the addon, `TradeRun`, `TradeRunGoal`): every 3 days in the morning a town with a stall
    sends its most trader-like person (greed, sociability, some bravery; not the keeper) to the nearest other town with a
    stall within 1600 blocks, with up to 3 kinds of its plenty (32 each) and the town's purse (6 emeralds from the till);
    it walks there (night: shelters, then on), waits up to 2 minutes for the keeper, sells what that stall buys, buys what
    its town is short of (from what stalls buy), walks home, puts the money in the till and the goods away. It remembers
    what that stall bought and sold, and next time takes those; nothing to sell or buy: no run. Chronicle: set out,
    traded (what), came back.
  - Made-ready towns now keep their keeper's shop and their town work after a reload (`TownFounding.tend`; found: the
    keeper had no shop after the world was loaded again), and have a till.
  - Town builds 0.58 (0.64 with a mate at it): at 0.5 a store took days. A town's store and stall spots judged by any of
    its people's knowledge of the ground, the leader's first (found: "ground it hasn't seen" 528, no store for days).
  - Debug: `/drifterciv town finish` (put up the store or stall being built, at once), `/drifterciv town owe <emeralds>`;
    `/drifterciv town` shows the money (till, wages owed and paid, sales to Drifters), `/drifterciv prices` what the
    keeper stocks up on.
  - Tested headless, made-ready town (seed 777): Nora bought 3 iron ingots for an emerald; the keeper banked 7, then 6
    emeralds; with wages owed, one found the till empty, then after the keeper banked, silent_acorn took 2 emeralds of
    wages. Two made-ready towns 400 blocks apart: HollowFern walked from Westby to Eastby (about half a day), traded
    (sold nothing: both towns made the same, so it then remembers that) and came home.
  - Not done / limits: a town far from every player isn't running (its Drifters wait, unless the config's "kept
    running" Drifters include them), so a trader to an unloaded town waits there or comes home; runs aren't saved over a
    restart; the trader doesn't ride yet (it walks, a boat over water as it does anyway).
- **Step 5, part 1: leaders, elections, laws, tax (0.22.26; the user: "lets do step 5"; chose: founder then elections,
  laws no stealing/no killing + tax + farms for citizens only, punishment by guards and fines, exile, bounties (prison
  later), three parts with a test between).**
  - `Politics` (addon), `Town.leader` (not the group's leader: its home is the group's base), `leaderSince`,
    `nextElection`, `candidates`, `lastVotes`, `laws`, `tax`, `taxOwed`, `taxDay` (saved). The founder leads first.
  - Elections every 5 days: the day before, the leader and up to two of its most ambitious people (0.5 sociability +
    0.4 greed + 0.4 bravery, 0.75 or more) stand; election morning each loaded member votes for the one it trusts most
    (a few points of its own leaning at random: all friends alike, it still has a favourite); a candidate for itself;
    a tie keeps the leader. Unopposed: it stays. Chronicle: "X, Y and Z stand...", "X was elected ... (tally)".
  - Laws by the leader's personality: no stealing and no killing always; greed 0.45+: a tax of 1 to 4 emeralds a
    household every 3 days (owed, at most 3 levies; `TownMoneyGoals.Tax` takes it to the till, keener the more it owes;
    paying makes it a little less fond of the leader); caution 0.55+ or aggression 0.7+: farms for citizens only.
    Enforcement is part 2.
  - Talk (`TownWork.talk` -> `Facts`): flags is_leader, likes_leader/dislikes_leader, new_leader, election_soon,
    candidate, taxed, tax_high, farms_closed; words leader, candidate_a/b, rival, my_vote, tax. Topics campaign,
    election_chat (who you voting for? -> its real choice), leader_praise, leader_gripe, taxes, new_leader_talk,
    leader_rounds; to players leader_welcome, campaign_player. The board: "N homes, M ppl / Leader: / name / tax 2,
    farms ours".
  - `/drifterciv town` shows the leader, laws, next election, who stands, the last vote; `/drifterciv town elect` (debug).
  - Tested headless (made-ready towns, seeds 777 and 779): three stood, 12 voted (8 to 1 to 3; a 4-4-4 tie kept the
    leader); on 779 the town voted its leader out twice (3-4-5, then 4-6-2), each new leader with its own laws (tax 1,
    then 3, then 3 and farms for citizens), and its people paid their tax into the till.
- **Step 5, part 2: crime and punishment (0.22.27; prison later, the user).**
  - `Justice` (addon): its people see a player steal (`SocialEvent` CAUGHT_STEALING, TOOK_VALUABLES via
    `DrifterEvents.PLAYER_DEED`) or, under the farms law and not a citizen (a citizen: a player whose bed is in the town),
    kill its animals or trample its crops: a fine (3 for a theft, 1 for the farms, +1 each offence after), the witness
    shouting it ("THIEF!", "those animals are ours!"); the third offence: exile instead. A killing of one of its people
    seen by another (`AFTER_DEATH`): a player exiled 7 days and a bounty posted (`DrifterApi.postBounty` ->
    `Encounters.postBountyFor`: the bounty hunters come); one of its own thrown out of the group; an outsider Drifter its
    people's enemy (trust -50 each). `Town.crimes` (`Criminal`: name, offences, fine, crime, exiledUntil; saved).
  - Guards (`GuardGoal`, `TownWork.arrest/arrested`): its two boldest with a sword or axe; with none of them within 48
    blocks of the culprit, any armed townsperson who sees them stands in (found: both off mining). It walks up, tells them
    ("that's 3 emeralds for stealing. throw them over"), waits a minute for the emeralds (thrown: it picks them up);
    paid: "fine paid. don't do it again", the money to the till (as a tax it owes); not paid, or they walk off: "then get
    out of {town}!" and exile 3 days.
  - Exile (`TownWork.standing` -> `Mode.toward`): in the town (its radius + 8), its people are territorial toward them
    and drive them out (`HostileGoals.DriveOut`), not past the town; over after its days ("may come back").
  - No small talk on duty (guard, stall, trade run: `Conversations.free`; found: a guard said "hey there" to the thief).
  - `/drifterciv town` shows fines owed and exiles; `/drifterciv town crime theft|farms|murder` (debug).
  - Tested in the client (made-ready towns): a theft, the witness "Tester took stuff from our chest!", a guard dropped
    its logs, walked up, asked for 3 emeralds, and (not paid) exiled me ("then get out of Lawton!"); paid, "Tester paid
    the fine: 3 emeralds" and the guard took them to the till; murder: exiled 7 days, a bounty posted; exiled, a
    townsperson switched to driving me out (0.81).
  - Seen: a made-ready town can have a Bandit among its people, who kills players on sight anyway (its disposition;
    not new). Fines can only be paid in emeralds (not Numismatic coins yet).
- **Step 5, part 3: unrest (0.22.28).** `Unrest` (addon): each townsperson's approval of the leader (`Town.approval`,
  0-100, 60 at first; not trust, which living side by side keeps near 100). A tax levy -3 per emerald; owed 3 emeralds of
  wages and not paid -2 a day; a day without a tax +1 (to 75); wages paid +1 an emerald; one of its people murdered -5;
  the tax dropped +15. Elections weigh it too (the leader's votes: trust + approval - 60), and a new leader starts at 70
  with those who voted for it, 45 with the rest. Once a day (afternoon; 2 days' calm after an upheaval; not with a vote
  on):
  - a third unhappy (approval 30 or less): an ambitious (0.75), aggressive (0.6) one at 10 or less may seize power (half a
    chance a day; a coup: no vote, the old leader stays, those who liked it think little of the new one); else a careful
    (0.5) or forgiving (0.6) leader drops the tax (once a term);
  - half unhappy: a humble leader (aggression + greed under 1) steps down (the others vote tomorrow; the keenest stands if
    nobody's keen); a proud one faces a revolt (no tax till the vote tomorrow morning, the boldest of the unhappy standing
    against it);
  - a ruthless one (a Bandit, or aggressive, vengeful, unforgiving) at -10 or less plots; the next time it sees the leader
    within 24 blocks, armed and well, it turns on it: out of the town (its house no longer its home; the others' trust in it
    -70 so it can't rejoin), enemies both ways (trust -100), and they fight as enemies do. With Drifters set never to fight
    each other it just leaves. Self-defence isn't murder any more (`Justice`: killed by the one it attacked, or who was
    after it).
  - Talk: `likes_leader`/`dislikes_leader` now by approval; `unrest`, `revolt`, `after_coup`, `after_abdicated`,
    `after_tax_dropped`, `after_betrayed` (2 days), word `old_leader`; topics unrest_grumble, revolt_talk, coup_talk,
    abdication_talk, tax_dropped_talk, betrayal_talk; shouts `unrest.*` (reactions.json).
  - `/drifterciv town` shows "Mood: n of m unhappy with X (approval a)"; debug `/drifterciv town sour <points>`,
    `/drifterciv town unrest now|plot`.
  - Tested: headless (three towns soured: an abdication and a new leader voted in, a revolt and the leader voted out 1 to
    4, a coup; a tax dropped), and in the client (a plotter turned on the leader and lost; found and fixed: it rejoined the
    town at once, so the leader was "thrown out for killing" it; unarmed, it turned and then didn't fight; it came back to
    life at its old home and went again; looking at the leader the very moment of the check was rare).
  - Also fixed: in strangers meeting, "o/ {b}" read as greeting itself ("o/ im {b}").
- **Bandits and towns (0.22.29, 0.22.30).** Found by the user: walking about a ready-made town, they were killed twice by
  tiny_ranger, a Bandit among its people (about 1 in 25 are born Bandits) whom the town had just elected leader. The user,
  2026-10-28: "bandits shouldnt be in towns, kinda like a skyrim bandit but if they are in a town they should be commiting
  several crimes to really give that bandit feel".
  - No Bandits in towns: a ready-made town's people are never Bandits (`DrifterApi.notABandit`: a new personality of
    another kind); a Bandit-led group never founds a town, and a group about to found one leaves its Bandits out first
    (they and the others think 30 less of each other); a town takes no Bandit in (`DrifterApi.registerJoinRule`, checked by
    `DrifterBonds.maybeGroup`).
  - A Bandit living in a town anyway (from before; or debug `/drifterciv town crook`) is its crook (`Town.crooks`):
    `DrifterApi.settleDown` makes it an opportunist there (no killing on sight), and `CrookGoal` (Drifter) every half a day
    to a day, when no town mate it sees is within 16 blocks, sneaks to a chest it has seen that's someone else's in the
    town (`TownWork.othersChest`: the store, the till, a neighbour's house) and takes up to 32 of the 3 best kinds of thing
    there, or picks a fight with the town mate it likes least (not the leader; `CrookGoal.Brawl`: to 4 hearts, 30 s at
    most, a blow below that doesn't land). It tells the town (`TownWork.crime`); `Justice.byCrook` judges by who saw it
    (a town mate with it in sight within 32 blocks, the one it went for too): -15 trust, a fine (3 for a theft, 2 for a
    fight, +1 each time) owed to the till like a tax; the third time it's thrown out (out of the group, no home, its
    town mates' trust -70 both ways, a Bandit again: kills on sight, can be a wanted poster's target).
  - Talk: flags `crook_theft`, `crook_fight`, `crook_thrown_out` (a day), word `crook`; topics crook_theft_talk,
    crook_fight_talk, crook_gone_talk; shouts `crook.brawl`, `crime.fight`, `crook.thrown_out`.
  - Its crook never keeps the stall (`Market.keeper` gives it to someone else), isn't the first or stand-in leader and
    never stands for election. Thrown out, it turns Bandit only once 160 blocks from the town (`Justice.leaving`; not
    saved). Debug: `/drifterciv town crook` (again: its crook at it again), `/drifterciv town crook out`.
  - Tested in the client (made-ready towns): a theft from a neighbour's chest seen ("guards! X's a thief", a fine of 3),
    an unseen one ("nobody saw it"), fights caught and stopped at a few hearts; headless and client: thrown out.
    Found and fixed: the crook kept its chase after the fight and killed the one it went for (now it drops it, and
    blows between them don't land for 10 s); it robbed only the empty till (now the store and neighbours' chests, the
    best 3 kinds, not the same chest twice, empty ones left a day); at 0.55 it nearly never got round to it (0.66 by
    day, 0.76 at night, 30 s committed, tried again soon if interrupted); the crook was the stall keeper (the user:
    "the crook is the shopkeeper") and once the first leader; thrown out and turned Bandit at once, or just past the
    town's edge, it killed the town's people (160 blocks now).
- **Step 6, part 1: diplomacy (0.22.39; the user: "go ahead with nations").** Step 6 split in three: part 1 diplomacy,
  part 2 wars between towns, part 3 nations. `Diplomacy` (addon):
  - Two towns know of each other once one does: people who knew each other before (a town made ready: `TownHistory`
    starts them at +10 a friendship, -15 a grudge), one of its people within 64 blocks of the other's middle, word of its
    founding (the `town_founded` news) reaching one of its people. "X knows of Y now (...)".
  - Their standing (-100 to 100; "close friends" 60+, "friendly" 25+, "neutral", "unfriendly" -25-, "hostile" -60-): each
    day by their leaders' natures (+2 x warmth: sociability and forgiveness, -2 x heat: aggression; out of sight a
    leader counts as middling), +0.5 for a trade deal and for peace, -0.5 for towns within 500 blocks not at peace; at
    once: a killing of one's people by the other's -25 ("A killing stands between..."), an insult -12, an offer turned
    down -4, taken +8, each trade there +3.
  - Each morning a leader may send an envoy (sociable leaders more often; one out at a time; three days between two towns;
    towns within 3000 blocks): an insult if they're on bad terms (-30 or worse) and it's aggressive (0.6), else an alliance
    (65+, at peace already), peace (35+) or a trade deal (15+, both with stalls). The envoy (its most sociable, not the
    leader, keeper or a crook) walks there (`Errand`, `ErrandGoal`: Drifter), to their leader (their leader's house if
    they aren't about the middle), says its piece and hears the answer, then walks home; out of sight it takes as long as
    the walk would (six ticks a block each way), walking it a day at most each way; after a trip out of the running world
    it's given its errand again where it was. Their leader takes it by the standing and its nature (trade: greed; peace:
    caution, not aggression; alliance: sociability).
  - Pacts: a trade deal sends their traders to each other first (`TradeRuns.nearestMarket`); peace and an alliance hold
    their groups' tension at 15 (no fighting on sight); an alliance brings peace with it (and is for part 2's wars).
  - Talk: `ally_town` (word `ally`), `rival_town` (`rival`), `town_trade|peace|alliance|refused|insult|killing` (two days,
    word `other`); topics ally_town_talk, rival_town_talk, town_*_talk; lines `envoy.trade|peace|alliance|insult`,
    `envoy.yes|no|insulted`. Commands: `/drifterciv relations`; debug `/drifterciv relations set <town> <standing>`,
    `/drifterciv relations envoy <town> trade|peace|alliance|insult`.
  - Tested: headless, three towns over three days (a trade deal, then peace, between two; an insult leaving two hostile;
    their traders going to their partners); in the client, an envoy walked from one town to the next, to its leader:
    "hey. Envoyby wants to trade with Farby. deal?" / "ok, you've got a deal", and home. Found and fixed: the out-of-sight
    timer answered before a slow walker got there; an envoy that went out of the running world lost its errand; an envoy
    waited in an empty plaza (now to the leader's house).
- **Planned (not started): a lived-in world, Drifter towns instead of villages** (docs/LIVED_WORLD.md; the user,
  2026-10-29: "replace villages and villagers as a whole ... MCA Reborn ... skyrim, fallout ... while still having that
  feel of a player"). Decided: villages replaced completely, real shops only, friends and companions (no romance), built
  after nations, as milestone 23 (cities of 40-80 households among small towns of 8-16, 2.5x rarer than villages;
  villagers a setting; culture folded in; the player a citizen). Phases: A towns where villages were (worldgen), B trades replacing villager trading, C town life and culture, D you
  and the town.
- **Step 6, part 2: wars between towns (0.22.40; the user: "go ahead with nations part 2").** Built on the group wars
  (milestone 12, `DrifterWars`: fighting on sight, raids), with the town deciding when they start and end:
  - Declared by envoy ("WAR"): when the standing is -60 or worse and the leader aggressive (0.55), or within three days of
    a killing, at -40, with a vengeful leader (0.6); not in a truce. "X declared war on Y". Declaring on a town it was at
    peace or allied with: "breaking its word": every other town that knows of it thinks 10 less of it, and its offers of
    pacts are taken 0.15 less often for each time (`brokenWord`, saved).
  - Allies: the defender's ally joins ("stood by its ally") unless its leader's careful (0.7): then "stayed out", -15 with
    the one it let down; the attacker's ally joins if its leader's aggressive (0.5).
  - At war: their groups' tension held at war (`DrifterApi.declareWar`; town groups' wars are "managed": no truce of
    their own after two deaths or a quiet stretch, `DrifterApi.setWarManaged`); their people fight on sight; raids go for
    the enemy town's store (its till), which they know of (`DrifterApi.setRaidTargets`), and loot it. Each death by the
    other side counted ("X of A fell in the war with B (killed by Y)"; a potion's or an arrow's by the kill credit).
  - Weariness: 6 a day at war, 15 a death of its own, -5 one of theirs, careful leaders more, aggressive or vengeful
    less; at 40 its leader sends terms ("TERMS"): reparations if it's lost more (4 + 3 a death more, 24 at most), else a
    white peace. A winning greedy leader (0.6; two deaths ahead, two days in) may demand payment instead ("DEMAND": 6 +
    3 a death ahead, 30 at most). Taken by the other's caution, aggression, weariness and the payment.
  - Peace: a peace pact, a seven-day truce (no war, `DrifterApi.makePeace`), the standing up to -20 at least; payment
    owed paid out of the payer's till as emeralds are there and into the other's till (real emeralds moved, never made;
    "X paid n emeralds toward its peace with Y").
  - Talk: `at_war` (word `enemy`), `town_war`, `town_fell`, `town_peace_made`, `town_broken_word`, `town_let_down`;
    topics at_war_talk and town_*_talk; lines `envoy.war`, `envoy.war_reply`, `envoy.terms`, `envoy.terms_white`,
    `envoy.demand`, `envoy.peace_yes|no`. `/drifterciv relations` shows wars (since when, each side's dead), truces and
    what's owed; debug `/drifterciv relations war|peace <town>`, and `envoy <town> war|terms|demand`.
  - Tested: headless, three towns: an alliance, a declaration of war, the ally standing by; a demand turned down (its
    envoy killed one of theirs on the way out), another, then reparations offered and taken after four days (7
    emeralds owed). In the client, two towns 160 blocks apart at war: one rallied and marched on the other's store, the
    other raided back, six dead in the first hour, each in the chronicle. Fixed: an alliance offered twice was agreed
    twice; a death by a thrown potion wasn't counted.
- **How a war feels (0.22.41; the user, 2026-10-29).** "people should acknowledge the deaths after they fight; even the
  enemies mourned the deaths of the people they killed?", "should be more fighting, but if everybody gets low they
  retreat", "people were chatting normally like they werent fighting in a war, is there any like war cries?", "would
  griefing be in a war?", "everyone grouped up at their own town ... so they can run in and it can be on sight":
  - Deaths: the killer's side doesn't mourn its kills or hold a grudge over them (`ChatReplies.killersSide`, NewsBook's
    DEATH react, `meet` doesn't pass on a death its side caused as sad news); the killer may say a word ("got one", "one
    down": `war.kill`, `war.kill_seen`). Every "rip", "o7" and grief line waits till the speaker's out of the fight
    (`DrifterEntity.inAFight`: a target, a pursuit, or hit in the last 5 s; `ChatReplies.afterFight`, 2 minutes at most).
    A town's revolt cry waits the same way. `DrifterEntity.groupAtDeath` keeps the side of one who died after leaving.
  - Fighting (`Threats`): against an enemy at war it stands and fights where it'd have backed off, unless it's hopeless
    (its odds under 0.3, courage counted), and falls back once under 40% health.
  - Raids (`WarGoals`): only armed, fit people (60% health) go. They gather at their own town's plaza
    (`DrifterApi.setRaidMusters`; the leader calls them: `war.rally`), set off when everyone's there (stragglers waited
    for 15 s), and charge together once within 48 blocks (`war.charge`). The leader calls the fall back when twice as
    many of them are hurt as fit (`war.fall_back`). The raided town: the first to see them raises the alarm
    (`war.alarm`) and its armed, fit people run to defend the store (`Defend`, urgency 1.02).
  - Talk: no conversation starts mid-fight or out on a raid (a leader stopping to chat held up its whole march); while a
    raid is on (theirs, or on them) only war talk; otherwise war, a death or a revolt is talked about first 75% of the time.
  - Wrecking, by the raid leader's nature (the user picked "by the leader's nature"; `Raid.grief`): a fair leader's
    raiders loot the store only; an aggressive one's (aggression 0.65) also break doors, fence gates and crops; a
    ruthless one's (a Bandit, or aggression 0.8 and forgiveness 0.3 or less) also set fire to wooden blocks with their own
    flint and steel (on a side of the block with air in front it can see). Looting first (no wrecking for 20 s), within 32
    blocks of the store, only what they've seen and what someone built (no trees), nothing right by a bed (2.5 blocks), no
    fire within 6 of a bed or 8 of the store, never in spawn protection; at most 10 breaks and 4 fires a raid, all its
    raiders together. Debug: `/drifter wars grief <0-2>` (the raids on now); `/drifter wars` shows each raid's.
  - Rebuilding: when a raid that wrecked things ends, the raided town's people near it get two days to mend their houses
    (`Home.mendUntil`, saved): the house-building goal builds the plan's missing blocks again (doors, burnt walls) with
    materials they have or gather ("fixed up its house").
  - Fixed, found by the user in a test (screenshots: cobblestone in town houses' windows, up their walls, in their
    rooms): a mining trip begun in town dug its stairs among the houses (away from home it started where it stood; a
    fresh start by the edge could turn back in), and a tunnel breaking into a room took it for a cave and plugged it with
    cobblestone. Now a tunnel that opens into a room near the surface (built blocks round it, not cobblestone plugs) leaves
    it be and gives up that way ("dug into someone's house: not this way"); a trip in or near a town (its edge + 24)
    starts its stairs past the edge, heading away (`TownEdge.nearTown`). The plug's spot is in the thought now.
  - Tested in the client, two towns 160 blocks apart, at war (seed 779, several runs): rally, charge, alarm and the
    fight lines said; mourning only from the dead's own side, and after the fighting ("they had it coming" from the
    killers'); about a dozen deaths an hour; fires lit (4 a raid) with flint and steel; both towns' people "fixed up
    its house" after being raided. Not seen yet: a fall back called (no raid got worn down).
- **Step 6, part 3: nations (0.22.42; the user: "go ahead with nations part 3").** `Nations` (addon), saved with the towns:
  - Joining: two allies (allied 3 days, standing 75+), the bigger town's leader (or a capital's) sends an envoy proposing
    a union ("UNION"): taken by the other leader's sociability, less its ambition, more if it's the smaller town, and the
    standing. The first two make a nation, the proposer its capital. Conquest: at war, 3 deaths ahead and 3 days in, an
    aggressive leader (0.6) demands the other submit ("SUBMIT"): a weary, careful leader gives in (peace, and it joins
    conquered, at -10 with its capital).
  - A nation: a capital (its leader rules), its towns (their own leaders, laws and elections still); a name by how it came
    about and its ruler's nature ("the X League/Accord" warm, "the X Dominion/Realm of X" aggressive or by conquest, "the
    Kingdom of X/the X Union" else); a colour no other nation has, one a common flower dyes (red, blue, yellow, orange,
    magenta, light blue, pink, light gray; then white).
  - Banner: a town's bearer (its leader, or if it keeps the stall the first of its people who doesn't) makes a banner of
    the colour (flowers picked, dye, white wool dyed, six of it and a stick: all its own; core goal `TownBanner`,
    `TownWork.banner` / `bannerUp`) and stands it on the plaza a few blocks from the middle. Flowers are sources for the
    planner now (`drifter_sources/early_game.json`), so it can dye wool generally.
  - Its towns: at peace and allied with each other, no insults or wars between them; only the capital starts wars, and a
    war on any of them (or by the capital) brings all of them in, no staying out ("went to war with Y for the X"); a town
    dragged into a war minds it (-6 with its capital), and each of its dead in a war (-2).
  - Tribute: every 5 days each town owes the capital 3 emeralds (conquered: 5), and minds it (-3, conquered -5); paid out
    of its till into the capital's as the emeralds are there (real ones; "X paid n emeralds in tribute to Y").
  - Rebellion: a town at -30 or worse with its capital, its leader aggressive (0.5) or ambitious (0.6), declares itself
    free ("INDEPENDENCE"). A ruler that's aggressive (0.55) or very greedy (0.75) won't let it go: war, the nation's other
    towns with the capital; whoever asks for peace loses (the rebels: back in, conquered; the capital: they go free).
    Else "X left the Y, free again". Leaving drops the alliance, keeps the peace; a nation down to its capital is no more.
  - Talk: flags `in_nation` (words `nation`, `capital`), `nation_capital`, `nation_conquered`, `nation_tribute`, and the
    latest (`nation_formed|joined|submitted|rebelled|free|kept|banner`, word `other`); topics nation_*_talk; envoy lines
    `envoy.union|submit|independence` and replies. `/drifterciv nations` lists them (capital, ruler, towns, standing with
    the capital, tribute, banners); debug `/drifterciv nations join <town>` (at once), `nations tribute` (due now), and
    `relations envoy <town> union|submit|independence`.
  - Tested: headless, three towns: a union proposed twice (turned down, then taken: "the Union of Aby"), tribute paid from
    a till ("Bby paid 2 emeralds in tribute to Aby"), a war that brought the other town in ("went to war with Cby for the
    Kingdom of Aby"), a demand to submit refused, a town declaring itself free and let go ("the Union of Aby is no more").
    In the client: each town's bearer dyed wool, crafted the nation's blue banner and stood it on the plaza ("raised the
    banner of the Envoyby Accord over Envoyby"; given the dyes for the test, the snowy test world having no flowers near).
    Fixed: leaders who keep the stall never got to it (a bearer instead). Not seen yet: a ruler going to war over a
    rising, a conqueror's demand taken, a banner made from flowers picked (the planner can; no flowers near in that test).
  - Later: a town that leaves taking its old banner down; patterned banners.
- Next: milestone 23 (docs/LIVED_WORLD.md, its progress kept there too). Prison is kept for later (the user).
- **Wanted (the user, 2026-10-28): behaviour by the respawn setting.** "i want their behavior to change depending on the
  respawn setting; they act too calm for a person that is never gonna spawn again". Proposed: one "stakes" number (what
  dying costs it: permadeath highest, its last life in lives mode high, back at its bed low; no bed with bed mode = gone)
  read through its personality, feeding caution (fleeing earlier, fewer fights started, shelter earlier, fewer risky
  shortcuts, gear and food before danger), feelings and talk (fear after a close call, "i only get one life", mourning
  friends gone for good, deeper grudges for a killing), and giving in (handing things over when mugged, begging). Not
  started; asked the user whether it goes before nations. The user on the talk: "dont make it corny disney expositiony
  though; like every drifter knows they have one life left, they dont gotta say that": show it in what they do, never
  have them explain the rule ("i only get one life" is out); any words are short, shaky reactions in the moment. Grief and
  grudges stay ("they can still grieve and have grudges and stuff"): just never spelled out as the rule.
  - **Part 1, caution (0.22.31; the user: "go ahead with part 1").** `Stakes`: what dying would cost it, 0.1 when it
    comes back at its bed, 0.3 with all its lives left (the default: where everything was tuned), rising to 0.7 with one
    comeback left, 1.0 when the next death is the last (permadeath, out of lives); an encounter's or a battle's Drifter
    stays at 0.3. Its difference from 0.3 pulls on its traits (`Personality.setStakes`, set every second; per unit:
    Caution +0.35, Bravery -0.35, Aggression -0.15, Curiosity -0.15; with permadeath about +-0.25), so everything that
    reads them gets warier: fight or flee (`Threats.courage`), cowardice, night style (no night hunting for most),
    shelter time, paths (`PathSearch` risk), caves and mining legs, whims at night, starting on players. And directly,
    by `Stakes.wariness` (0 as tuned, 1 when the next death is the last): eats sooner (threshold +4), gives up chases
    sooner (-50 %, the relentless -30 %), fights it could skip worth 60 % less, no drop that leaves it under 6 + 8
    health. `/drifter personality` shows "Stakes: ..." and the traits as they act. No talk about it (part 2: feelings).
  - Tested in the client, the same seed and setup twice (4 Drifters with stone swords, night, 3 mobs on each, 2 more a
    minute later): bed respawn, 12 picks of "fight", 29 of "get away", 3 went out hunting mobs, none stayed in;
    permadeath, 1 fight, 8 get away, nobody hunted, 3 stayed in. Nobody died in either.
  - **Part 2, feelings (0.22.32; the user: "go ahead with part 2").** A death's news says whether it was for good and
    where (`NewsBook.sawDie(..., comesBack)`); seeing it (not only hearing) now moves a Drifter too. A friend gone for
    good: grieving at full (not 0.6), and `NewsBook.Mourning` for 1 to 3 days by how close they were (it misses them now
    and then: grieving again; saved), and once, by day, `MournGoal` takes it to stand where they died (it saw it or was
    told where) for 12 s. The killer: trust down twice as much when it's for good (a player: -30 a friend's death, a
    Drifter now too, -30, through `DrifterBonds.heardOf`), and a friend's death for good is a grudge (for life when they
    were close, or it's vengeful) and a wrong (no warning first). Hurt by a player or a Drifter when dying would cost a
    lot (`Stakes.wariness`): more fear (up to twice), the trust loss half again, and nearly killed with wariness 0.7 or
    more, a grudge for life. Giving in: hit by a player down to 4 hearts, wary (0.5 or more), not brave (under 0.6),
    not one out to kill, and not winning: it faces them, says so ("ok ok take it", "STOP. take it") and throws them
    its best 3 stacks (armour and weapon stay on it), once a fight, then gets away as before. Lines (short, the way
    players type, never the rule spelled out; the user): `grief.saw` ("NO", "{who}!!", "bro"; 60 %), `grief.visit`
    ("o7", "rip {who}", "f"; 40 %), `yield`. Also fixed: a Drifter never opens a chat with a player it can't stand (it
    said "hi stranger" to the player who had just killed its friend, while still looking them over).
  - Tested in the client (permadeath; Ann and Bob teamed up, Bob killed by the player in front of Ann, Cat beaten
    down): Ann "saw Bob die (gone for good)", trust in the player -100 with two grudges, kept away from them, grieved,
    and later walked back and stood where Bob died ("f"); Cat said "STOP. take it" and threw 3 stacks. Not yet seen: a
    Drifter killing another Drifter's friend for good (the same code, by `heardOf`).
  - **No friendly words to an enemy (0.22.33).** Found by the user: "while im fighting drifters they also stop to
    introduce themselves", and the raiders who were out to kill them said "yay MutMut0118 is back" / "welcome back! :)"
    when they logged in, and "hey!! no hitting" mid-fight. A Drifter looking a player over (or not seeing them yet) wasn't
    "hostile" by its mode. Now `Mode.atOdds` (hostile, warning, keeping clear or running; trust under -25; a kill-on-sight
    Bandit; after them or fighting them; hit by them in the last 30 s; an encounter's) keeps it from opening a chat with
    them, greeting them, welcoming them back, answering their greetings or congratulating them, and makes its answers to
    them an enemy's; and "hey!! no hitting" is only for one hit by surprise, not one already fighting them. Tested in the
    client: a Bandit gang hunting the player said nothing friendly to "hi" or "whats ur name"; a friendly Drifter still
    answered ("im Fren", "cant complain").
- **Beds where they can be walked to (0.22.34).** Found by the user: "something to cause drifters to pillar cobblestone in
  their homes ... bed placement is still very random; those houses with the logs on top cause a drifter to pillar up to
  reach their bed; beds in weird places, like in front of stairs and ways that block the player". `bedSpot` (a house's
  bed when the plan has none: every town household, a Drifter's own house) took any spot inside with a floor under it and
  a roof over it: the top of a log beam or a block of furniture too. Now it works out where someone can stand in the
  house walking in from its doors (steps up a block, drops, ladders) and takes the best spot that's on that floor, with
  open floor beside it, and that cuts none of it off once the bed is down (the stairs, a ladder, a room). Headless: three
  towns of 12 houses and the 300 plans' checks, 374 beds, all placed so (272 on the ground floor, 102 upstairs); making
  the plans at start-up takes 0.65 s (was 0.37). Beds already placed stay where they are.
  - Not reproduced: "bridging over a path block even though its 1 step down" (a path one step down was walked across in a
    test; the bridging in the user's traces was up the outside of houses, at roof height). Asked the user where.
- **A town Drifter's base is its house (0.22.35).** Found by the user ("can u see why lynx is trying to fight me?"):
  LynxPRO, a territorial townsperson in a made-ready town, warned them off ("dont even think about it") as they walked
  past, took it as ignored 2 s later, drove them "out of its base" and then chased them: its base was 20 blocks round its
  house, which in a town is the streets and the plaza. In a town (`getTownWork` set) its base is now its own house and 2
  blocks round it (`Mode.IN_TOWN_MARGIN`, `Home.inOwnBuilding`); and driving someone out ends the chase its hits started
  (`HostileGoals.Fight.endsChase`). Tested in the client: three townspeople made territorial, next to the player in the
  plaza for a minute: no warning.
- **Pillars, holes and stray cobblestone in towns (0.22.36).** Found by the user (screenshots: cobblestone pillars up
  house fronts with a torch at the top, 1x1 holes in the grass, loose cobblestone; "does this have something to do with
  light levels?"). Traced in the client with a temporary log of every cobblestone a Drifter placed (who, where, doing
  what) over a day and a half in a made-ready town, replayed from the same save:
  - Torches: `LightBaseActivity.townDarkSpot` took the top block of a column, under eaves the top of a beam or ledge (only
    "dirt, or not recorded as built" kept it off buildings): now only on ground (dirt, grass, sand, gravel, snow), never
    a built block.
  - Night hunting: an exploring leg's point was at its own height, over lower ground a point in the air, and it pillared
    up to it (58 blocks in the town in the trace). Routes can now be loose (`NavigateTask.loose`, `PathSearch.loose`:
    there within the radius across the ground and 8 up or down); exploring legs are. 58 -> 0.
  - Holes: mine stairs started 3 blocks outside a town house, in the town's grass, and when they wouldn't start it dug
    down where it stood; digging down for stone too. Now (`TownEdge`) mine stairs start past the town's edge, and digging
    down for stone walks out of town first; neither digs down in the town.
  - The house shelter loop: in for the night before dark, "not night" read as morning: out and back in four times a
    second (the user's tyler_82). It's morning now only once it's been night, or early in the day.
  - Result over the same day and a half: cobblestone placed 1,152 -> 722 (most of what's left underground, mining); in
    the town above ground, chest errands 27 -> 7. Still seen: a few pillars on errands up steep hillsides, pillars up to
    chop tall trees.
- **Hillsides (0.22.37; the user: "fix the hillside pillaring too").** The pillars up hillsides were up cliffs the town
  builder made: a levelled site (the plaza cut into a hill, the store's plateau) was eased into the land round it over a
  fixed few blocks, so a nine-block difference came out as steps of two and three, and its road went straight up them.
  `TownBuilder.foundation` now eases the land a block up or down a block out (the band widened as needed, up to 10), and
  `easeRoad` builds a road up where the land jumps so it climbs and drops a block at a time (filled, never dug). Same
  seed: steps of 2+ between road blocks 27 -> 3; steps of 2-3 over the town's ground 512 -> 193 (the rest natural hills).
  A day and a half there: 60 or so stray cobblestone blocks above ground over the whole town (and one Drifter's
  cobblestone hut).
- **A past for towns made ready (0.22.37; the user: "for spawned towns, can we already established relationships with
  one another? that way it seems like it has some history between towns and everybody already knows eachother?").**
  `TownHistory`: instead of trust 60 all round, its people's bonds go by how alike they are (seven traits) and chance,
  housemates +20, each seeing it a little differently, and how well they know each other (30-80, housemates 80+): about
  10 % don't get on (never below -25: no enemies), 42 % acquaintances, 38 % friends, 10 % close. The chronicle: "X and Y
  of T have been best friends for years", "... have never got on". With other towns within 3000 blocks: 2-4 pairs know
  each other (60 % old friends, 25 % acquaintances, 15 % an old grudge), "The people of T and U go back a long way (n
  old friendships)", "X of T and Y of U have an old grudge"; the far side's bond is kept (`Town.pendingBonds`) till that
  one is loaded. `DrifterApi.setBond` (by id, loaded or not).
- **Gear by place in a town made ready (0.22.38; the user: "everyone is wearing diamond and i think everyone feels a bit
  too overpowered; i cant tell a guard from a citizen, or a citizen from a leader").** Everyone had a delver's kit (iron
  to diamond, enchanted). Now `Delvers.townKit` (`DrifterApi.equipTown`), roles decided once all are in: the leader (the
  group's first) a gold helmet for a crown, an iron or diamond chestplate, iron legs and boots, a diamond weapon of its
  favourite kind, a shield; its two boldest (bravery + aggression) the guards: a full iron suit (a diamond piece at most,
  now and then), an iron weapon, shield, bow and 32 arrows, enchantments sometimes; everyone else a working life's,
  humble (35 %: stone tools, a stone sword or none, a leather piece now and then), settled (iron tools, a stone or iron
  sword, up to two pieces of leather, chain or iron) or well off (20 %: iron tools and sword, up to three pieces of chain
  and iron, a shield maybe), never a full suit; a bow for one in five. The stall keeper wears a leather tunic (an apron).
  Less cobblestone in the pack (8-23, was 32). Justice's guards are never the leader. Founded towns are unchanged:
  their people wear what they made themselves.

## Milestone 23: a lived-in world (started 2026-10-29)

The plan and its own progress: docs/LIVED_WORLD.md (phases A to D; decisions 1 to 11, the last two the user's for steep
Tectonic terrain: towns on the flattest ground to hand, easy to get round without pillaring or bridging).

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
  - Next: A11's layout and walk check, then A3/A4.
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
- **0.23.10: town shapes (the user, 2026-10-30: "can we change the shape to different types? like curved and bending
  roads, roads that follow the land, and towns that go down a main street").** Before, every town was a plus of four
  straight roads with the store, the fields and the pen always in the same corners.
  - Three shapes (`TownBuilder.Shape`): **crossroads** (four roads out from the plaza, bending with the land), **main
    street** (one long street through the plaza, a third longer than a road, along the line across the land that climbs
    least, a few short lanes off it), **winding** (three roads out at about a third of a turn apart, wandering, lanes off
    any road). A small town's is crossroads 35%, main street 40%, winding 25%; a hamlet's a main street; a city's
    crossroads (its roads bend too). Saved with the town; `/drifterciv town` says which. Debug: `/drifterciv town shape
    <crossroads|main_street|winding|any>` (every town made after, till the server restarts).
  - Roads that follow the land (A11's "roads that follow the contours"): a road is laid a block a step, and every four
    blocks it takes the way on (of five, up to 30 degrees either side) that climbs least over the next twelve, kept to
    the way it set off by its shape and wandering a little; it turns a few degrees a block at most, so its bends are wide;
    three wide along its middle line, built up a block a step where the land jumps, planks over water up to five wide; it
    ends at water wider than that, the town's edge, or where it meets another road (a junction). Side roads the same.
  - The store, the two fields and the pen go where there's room near the plaza, off the roads (nearest first, on ground
    that suits): the store turned to face its road or the plaza, a path from its door (only where that path can climb a
    block a step: found in a city, a store three blocks over its road), the pen's gate to its road.
  - Houses along a bending road stand square, their fronts to the road, pushed back a little where a bend comes close.
  - Roads cut back to what they lead to once the houses are up (a few blocks past the last house, the store, a field or
    the pen; side roads leading nowhere gone; roads out of the plaza a stub at least): a hamlet's roads ran on into the
    wild.
  - Its people's road work keeps to its roads as laid (`Town.roadColumns`, saved; towns made before keep the old four
    straight roads), and its lit ground follows them.
  - Debug: `/drifterciv town map` (the nearest town from above, saved as a picture in the game folder's `town-maps`).
  - Tested headless on the user's Tectonic (seed 779): a winding hamlet among hills, a main street town of 11 houses, a
    crossroads hamlet, a city of 60 households (46 to 59 houses, 92 to 100 people): every place walked to (after the store
    fix), no step over 136 ms; and vanilla: one of each shape, a ruin, a town of 3. Maps in the session.
  - Next: phase W (planned below), then back to phase B (shearing and the shepherd, B4 prices, B5 services).
- **Planned 2026-10-30: phase W, the civilized world** (docs/LIVED_WORLD.md 4b, decisions 13 to 17): a world plan made at
  world creation (nations: a capital city in the middle, towns round it; settings for how many, 2-3 / 4 / 6-8, and how
  spread, around spawn to over the world), roads joining them all and highways between nations, roads by size (stone in
  cities, mixed in towns, dirt and gravel on highways; slabs and stairs on climbs), terrain shaped to fit (one height plan
  a town, terraces, no air boxes), river, harbour, hill, ring and grid shapes, and a history of about 1,000 days already
  written (WorldBox). Order: W1 + W2, W3, W4 + W5, W6 + W7.

- **0.23.8: towns and cities located apart** (the user: "is there a way we can separate the command to locate towns and
  cities?"). A city is its own structure now, `drifter_civ:city` (structure set `drifter_civ:cities`, placed exactly as
  the towns' set, so on the same spots; biome tag `#drifter_civ:has_structure/city`, the towns' biomes). The town one
  gives way where a city goes, the city one goes only there (that's checked first, before the land is looked over).
  `/locate structure drifter_civ:town` finds towns (hamlets and ruins too), `drifter_civ:city` cities, and the structure
  tag `#drifter_civ:settlements` either. In a world made before 0.23.8, cities already made stay `drifter_civ:town`.
  Tested headless on seed 779: from spawn a town at 1296,-512 and the city at -2304,-1136; standing in the city, the town
  locate skipped it (the next town 1,619 blocks off) and the city locate found it 65 blocks off.
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

## Side session (branch claude/drifter-chat-expansion-574ebe): chat and house plans

Worked in parallel with the step 5 session; base mod only (no addon files, none of the files that session is in).
Versions carry a suffix (0.22.26-side.N); the plain number is the other session's.

- **Drifters remember what you tell them (0.22.26-side.1, rebuilt on 0.22.27 as 0.22.27-side.1; the user, 2026-10-28: "expand the chat system", chose
  "Remember what I say", "plus more things to ask them").**
  - `social/talk/Heard` (per Drifter, saved as `DrifterHeard`, its own version 1): per player what to call them, their
    base (exact or rough), their plan (asked about after 3 minutes, kept a day), promises (due after a day, broken after
    three: -4 trust once; kept: +3 and "you actually brought it" in place of the plain thanks), likes and hates (4 each),
    their news (a day). Places told (kind, where, exact or rough, who said so, tellings on; 5 days, 24 kept) and words
    about others (3 days). Hearsay only: it never goes into the world memory decisions read (DESIGN section 5); it only
    changes what it says (and the trust a word about someone moves).
  - `social/talk/Telling`: reading statements (before the questions; never a question: "?" or a question word first):
    "call me X" / "my name is X"; "my base is at x y z" / "this is my base" / "my house is up north"; a place with
    where (coords, a direction and distance, or "here"/"that way" with a word like "theres"); "going mining" (15 plans);
    "ill bring you 5 iron" / "i owe you" (any item by name, or food/wood); "i love cats" / "i hate creepers" (things
    players talk about, items, creatures, as typed); "bob is a griefer" / "annie is cool" (believed from trust 10, not
    against its own view of them at 30+, once a day per teller); news (died, diamonds, hungry: a friend with spare food
    offers some, built, nether, end, dragon, back, tired, lost). A place said in chat is read by every Drifter within 32
    blocks. New questions: who am i, what do you think of me / of X / of cats, wheres X (in sight, or where and when it
    last saw them), where are you going (`going.<goal id>` lines), what do you need (hurt, food, home, the strategist's
    next target), who are your friends, what did i tell you, wheres my base, forget that / forget everything, what do
    you like. "have you seen a village" with none seen: "havent seen one myself, but X said...".
  - Said about someone else ("wheres bob", "bob is a griefer"): the one named is who it's about, so it goes to the one
    you're talking with or looking at, or the only other one close by (`PlayerTalk.addressee`).
  - Brought up later (`drifter_talk/heard.json`): to the player heard_plan (question), heard_promise,
    heard_promise_broken, heard_news (question), heard_like, heard_hate, heard_base, heard_place_tell, heard_word_tell,
    heard_player_news; between Drifters heard_place (effect `pass_place`: the other learns it, one telling on),
    heard_word_bad / heard_word_good (`pass_word`: a friend thinks a little less/more of them), heard_player_news,
    heard_player_plan. Hooked in without touching `Facts`: `Heard.addPlayerFacts` / `addDrifterFacts` add flags and
    words after `Facts.of` in `PlayerChats` and `Conversations`.
  - It calls you by your nickname in its answers and its talks with you (`Heard.callName`).
  - A topic whose lines don't fit now says nothing and asks nothing (`PlayerChats.Opening.said`; a forced topic used to
    ask its question without saying it). An answer to its question counts unless the message is a question of its own
    ("yeah, found 3 diamonds" answers "find any dias?").
  - `/drifter heard` (what the nearest was told), `/drifter heard forget`, `/drifter heard age <real minutes>`.
  - Data: `drifter_chat/heard.json` (phrases and lines), `drifter_talk/heard.json` (topics); docs/CHAT_LINES.md
    section 8b (the generator has a new section).
  - Tested in the dev client (scripted chat as the player): nickname used, base, place read by everyone near and
    retold as hearsay ("you said theres a village somewhere north, about 300 blocks"), plan follow-up asked and
    answered, promise reminder, where/opinion/need/going/friends/told/forget, hungry offer then bread thrown on "yes",
    saved data written. See below for what the last run showed.
  - Last runs: promise reminded ("didnt you say you'd bring 5 iron ingot?") and kept (iron thrown: "ayy finally" in
    place of "ty"; promise gone); "i hate creepers" kept as typed; Annie told Dora "Tester said theres diamonds around
    10 -58 40" and Dora then had it ("from Tester (1 tellings on)"). Dora, not knowing Tester, still answered "have you
    seen diamonds" with "havent seen diamonds" (hearsay is told to those it knows, as its own finds are).
  - Next on this branch: house plans (the user's second ask). Ideas for chat later: Drifters acting on told places
    (visiting a told village or base) would change behaviour, so it's a design question for the user first.
- **Slang and long talks (0.22.28-side.2; the user, 2026-10-28: "instead of 'what do you think of me' its like 'whatchu
  think of me'", and "a network/web system for talks between drifters ... a very long coversation"; chose talk lengths
  strangers 1-2 topics, friends 3-6, best friends ~10, and working side by side in long talks; talks leading to joint
  actions later; "the goal is to get the topics in the thousands").**
  - Slang: `drifter_chat/slang.json` "slang" (word → plain words, ~230: u, ur, r, wat, whatchu, bout, wanna, idk, rn,
    frens...) applied by `Lines.understood` to what players type and to every answer phrase alike, so every intent
    gets it for free. Words with two meanings are left out ("ya", "k", "y", "n", "yoo" which squashes to "yo"); checked
    with an audit script that the slang makes no new clashes between intents. Gapped phrases ("think * of me": in
    order, at most 2 words between, counting 2 less than a whole phrase) for the asks; `/drifter lines meaning <text>`
    now shows the ask too and the text as read.
  - The topic web (`Topics.Web`): tags, leads (tags), next (ids), cost, about, follow_up, opener. `links` in any
    `drifter_talk` file sets them for topics written elsewhere (`drifter_talk/web.json` links all 55 old topics without
    touching their files); untagged topics are about the words of their id. Tag index for fast lookup at thousands of
    topics. An option or turn can `mark` the talk (`t:<mark>` in conditions; "disagree" -1.5 trust each at the topic's
    end, "agree" +1) and add `leads` (a disagreement leads to "argument" topics: agree_to_disagree for non-rivals,
    argue_more for rivals).
  - `Conversations`: interest (0.5 + 1.5 x sociability, +1 acquainted / +2.5 friends / +6 trust 80+ / +0.5 rivals,
    bored +1, angry/frustrated/anxious -0.5, + random 0-1; each topic uses its cost); on to a linked topic (either can
    bring it up, the other a bit more often) or 15% a change of subject; segues ("speaking of which", "anyway") from
    `drifter_chat/talk_web.json`; a long talk ends with talk.bye / talk.bye_back. At most 10 topics / 5 minutes.
    Long talk (past the first topic): `ConverseGoal` drops to 0.12 and isn't urgent, and its activity hands back after
    each listen, so they go back to work and chat as they work (lines a little slower), cut beyond 48 blocks (the chat range; 32 cut a talk inside a 27-block test box). Cut short
    (trouble or distance): talk.cut.trouble / talk.cut.away, and the pair picks it up within a day ("anyway, about the
    {about}"; resumable pairs can start after 30 s).
  - 22 new topics in `drifter_talk/web.json` (best ore, tools, worst mob, nether, end, farming, fav food, build style,
    night plans, sleep, villagers, animals, caves vs strip, exploring, enchanting, players, rain, random thought,
    remember when, feelings, agree to disagree, argue more).
  - Debug: `/drifter talk long [topic]`, `/drifter talk status`, `/drifter talk links <topic>`. CHAT_LINES.md section 6b,
    topic headers show about/leads; section 10 lists the slang.
  - Tested (dev client): slang asks all understood ("whatchu think of me" → opinion, "who r ur frens" → friends, "wat
    u need", "where u goin", "wat did i tell u"); strangers chained 2 topics and said bye ("oh btw what mob do you hate
    the most" ... "anyway cya Cole"); best friends chained worst_mob > random_thought > complaints with 7.3 interest
    left and went back to exploring while talking.
  - Next: the user wants topics in the thousands. Content plan: write topics in batches by tag (new files per theme),
    each with tags/leads; maybe a generator/lint script (unknown tags, dead ends, topics nothing leads to).
- **Topic batches, first round (0.22.28-side.3, side session).** Toward the user's goal of thousands of topics.
  - 168 new Drifter-to-Drifter topics in themed files `drifter_talk/topics_*.json`: mining (26), mobs (22), building (20),
    nature (25), adventure (22), gear (20), life (33). 250 topics in all, 63 tags, about 2,800 different lines. New tags:
    caves, redstone, potions, ocean, biomes, structures, storage, decor, crafting, fishing, pets, plants, trees, weapons,
    armor, dreams, philosophy, music, sky, random. Many opinion topics can end in a disagreement (leads to argument).
    Some need a mood, the weather or the time (stars at night, rain, sunset), or a bond (friends, rivals).
  - The other session's six town-unrest topics are now linked into the web (`web.json` links: town, politics...).
  - Which topic opens a talk (`Conversations.pick`): a topic that needs something real (a find, a death, a mood, the
    weather; not only who they are to each other) comes first 70% of the time when one fits (`GROUNDED_SHARE`), so
    hundreds of everyday topics don't bury the news. Everyday topics mostly come up as talks wander on.
  - Long talks hold together: drifted more than 24 blocks apart, a Drifter heads back over (`ConverseGoal` 0.35,
    under pressing work), so two going off for trees in opposite ways don't end a best-friend talk after two topics
    (seen in the headless smoke; with it, 7+ topics there).
  - Tested: dev client, best friends went 10 topics, mostly new ones, each leading to the next (farm_redstone >
    farming_talk > golden_apple > hunger_struggle > baby_zombie > pillager_patrol > worst_mob > best_enchant) and
    said bye; headless smoke 7+ topics, no errors, 250 topics loaded.
  - `tools/check_talk_web.py <project>`: checks the topics before a build. Errors: broken files, an id used twice,
    conditions nothing sets (it reads every `flags.add` in the mod and the addon), words the game never fills, leads
    to tags no topic has, `next` naming no topic. Warnings: a tag on one topic only, topics nothing leads to, dead ends,
    lines repeated across topics. Run it after every batch.
  - Next batches (ideas): seasons of a Drifter's life (first house, first diamond...), each voice's own topics
    (builder, miner, hermit, speedrunner...), more mood and bond topics, follow-ups that continue a topic ("next"),
    town life topics that read the addon's flags, player stories. Then the house plans.
- **Topic batches, second round (0.22.28-side.4, side session).** 374 topics now (124 new), 67 tags, about 3,760 lines.
  - `topics_voices.json` (28): each kind of player's own topics, needing `a:voice_<kind>` (miner, builder, homebody,
    speedrunner, nomad, hoarder, hermit, pvp_sweat, newbie, casual, helper, bandit) and a few pairs (builder with
    miner, homebody with nomad, anyone teaching a newbie). Weight 3, so a Drifter's kind shows. The flag is added in
    `Conversations.facts` (not Facts.java, which the other session owns).
  - `topics_stories.json` (24): twelve two-part stories. Part one names part two in `next`; `Conversations.continued`
    goes straight on to it 85% of the time (`NEXT_FIRST`), told by the same one, with no segue. Part two is a
    `follow_up` tagged only `story_part`, so nothing else leads to it.
  - `topics_friendship.json` (23): compliments, teasing, advice, asking for help, apologies, trust, first meetings,
    rivals' truces, groups and towns (base flags only). `topics_daily.json` (20): the day as it goes.
    `topics_opinions.json` (17): favourites and hot takes. `topics_memories.json` (12): firsts, regrets, close shaves.
  - Fixed: a reply that depends on the answer (help? team up? the dragon?) used to depend on the bond, giving "sure" /
    "maybe later then". Now the answer turn is split into a yes turn (marks agree) and a no turn, and the reaction
    reads the mark. Hot takes work the same way ("endermen are the best mob" / "hard disagree" / "fight me"). A
    topic's agree and disagree marks are both cleared when it ends (one could leak into the next topic).
  - "Something real first" (from round one) now counts only things about the two (a:/b: needs, not their kind) and
    rain or storms, not the time of day, so the new day topics don't crowd out news.
  - Tested: dev client (miner + newbie in a sealed box): hot take branch right, the miner brought up its own topic,
    ten topics and a goodbye; a story's part two came straight after part one. Headless smoke: six topics, no errors.
- **0.22.29-side.1 (side session):** merged the other session's 0.22.29 (a Bandit living in a town settles down) into
  the chat branch; no conflicts beyond the version number. Same topics as side.4.
- **Topic batch three, and the merge of 0.22.30 (0.22.30-side.1, side session).** Merged the other session's 0.22.30
  (Bandits kept out of towns; a Bandit living in one is its crook) and linked its three crook topics into the web
  (`web.json`). Then 194 new topics: 568 in all, 67 tags, about 5,180 lines.
  - `topics_town_life.json` (25): everyday life in a town (needs `a:in_town`): the morning, neighbours, the market,
    jobs, keeping watch, roads, the leader (and being the leader), taxes, thieves, a party, newcomers, what to build next.
  - `topics_voices_more.json` (28): more of each kind's own topics (casual, speedrunner, nomad, hoarder, hermit,
    pvp_sweat, bandit, helper, newbie, builder, miner, homebody) and more pairs (builder asks a miner for stone, a
    speedrunner and a casual, a helper and a newbie, a helper and a bandit, a hoarder and a nomad).
  - `topics_stories_more.json` (24): ten more two-part stories and two in three parts (the stronghold, the first
    friend): part two names part three in its own `next`.
  - `topics_crafting.json` (26): redstone, potions, enchanting, armor, weapons, tools, banners and maps.
  - `topics_outdoors.json` (26): fishing, the sky (moon, sunrise, clouds), trees and plants, the sea, pets, music,
    snow, rain and storms.
  - `topics_moods.json` (25): how a Drifter is doing right now: each mood, hungry, hurt, a death, a close call, kills,
    ore it found, diamonds in its pocket, no home / a hut / a house, something it built, a trade, the other's armor.
  - `topics_debates.json` (17): small debates in the agree/disagree pattern (beds in the nether, dirt houses, iron
    vs diamonds, torches, gravel...). `topics_banter.json` (19): would-you-rather, teasing, jokes, advice, players.
  - Checker: now flags a condition only ever set about players (`b:owed`, `b:stranger`, `b:holding_fancy`...) used in
    a topic between two Drifters, where it would never come up (caught two of these while writing).
  - Fixed: `kinds_newbie_teach` mixed a question ("want a tip?") with tips in one turn; now all tips.
- **0.22.34-side.1 (side session):** merged the other session's 0.22.31-0.22.34 (stakes: caution and grief by the
  respawn setting, no friendly words to a player it's at odds with, beds only where they can be walked to). Fixed from
  the user's chat log ("can i have soem bread" ... "need what?" ... "bread" ... "like what", over and over): a Drifter
  asked for something it doesn't carry used to ask what they want, again and again (`PlayerTalk.wanted` only knows
  its own inventory). Now `PlayerTalk.named` reads any item named (all the words of its id, read as chat is: "golden
  apples", "porkchops", "food") and it says it has none ("dont have any", "i dont have bread"); it asks "what do you
  need?" only when it can't make out an item, and only once (the next answer gets "i dont have that"). Tested in the
  dev client. Left as it was: after "trade?", "a diamond sword" is heard as asking whether it has seen diamonds.

- **0.22.37-side.1 (side session):** merged the other session's 0.22.35-0.22.37 (a town Drifter's base is its own house,
  fewer pillars/holes/stray cobblestone in towns, towns without cliffs, made-ready towns come with a past). No chat
  changes; headless smoke test: talks run, 568 topics, no errors.
- **0.22.37-side.2 (side session): house plans, stone and new shapes.** The user picked "stone houses" and "new
  shapes" (of stone houses, biome styles, new shapes, insides).
  - Five new styles, more stone (wooden towns burn): `stone_house` (stone brick or cobblestone walls on both storeys,
    stone gable ends, dormers, a balcony), `cobble_cottage` (cobblestone walls and gable ends, dormers), `townhouse` (two
    or three storeys, stone ground floor, a balcony over the door), `stone_barn` (cobblestone ground floor, a gambrel
    roof), `cross_house` (stone brick, a cross plan). About a quarter of a world's 300 plans are these now.
  - New parts in `HouseGenerator`: a gambrel roof (steep rows for the lower half of the slope, then 45 degrees:
    `steepRows`), dormers (`Draft.dormer`: a 3-wide gabled window a row in from the eave, its roof running back into the
    main one, the main roof opened behind it; over the middle bay or post and every other bay out, both slopes), a
    balcony over the front door (`Draft.balcony`: a floor two deep across the door's bay on the beam, braces, a railing
    of the frame's wood, the storey above's door out), a cross house (`Draft.crossWing`: the T wing as drawn, mirrored
    out of the front, the front door in its gable end). Shown in `/drifter house parts` (#19 to #22).
  - Old worlds keep their plans: the new parts draw random numbers only for styles that name them
    (`HouseStyle.newChance`; `materials` draws in the same order as before), and a plan id the world's plans no longer
    have (a new style took its number) is made again from its id (`GeneratedPlans.again`, from `HousePlans.get`).
    Checked: all 300 plans of a seed-777 world made by 0.22.37-side.1, asked for by id from the new build, came out
    block for block the same.
  - Tested: `/drifter house parts` in the dev client, all four parts looked over from outside and in (pictures).
    Drifters building them (headless, deep flat, peaceful, each given its plan's blocks by command): a two-storey
    townhouse with a balcony reached 644 of 888 (the balcony done; stuck on its main roof's edge and short of planks);
    a two-storey gambrel barn 879 of 946 (left: the top of the roof at the gable ends, 15-17 up, out of reach, so the
    barn style is one storey now); a dormer cottage 1210 of 1270 in 14 days (both dormers' windows, posts and overhangs
    up; left: the main roof's top rows 12 up, out of reach as on any 13-wide roof, and the slab on top of each dormer, so
    the dormer roof is flat-topped now: stair, block, stair). Not reached in testing: a cross house built to the end.
  - Next for houses (ideas, not asked yet): biome styles (sandstone, terracotta, snowy spruce), insides; builders still
    leave the top of tall roofs (ridge 12+ up) unbuilt, for every style.
- **0.22.40-side.1 (side session):** merged the other session's 0.22.38-0.22.40 (gear by place in a made-ready town,
  diplomacy, wars between towns). Headless smoke test: 17 house styles, 300 plans, talks run, no errors.
- **0.22.41-side.1 (side session): stones (the user, 2026-10-29: "more variation to stone materials, instead of just
  using cobblestone"; then "take granite out of the equation, and the mixed stone towers... think about the palette").**
  - HousePalette swaps stone as it swaps wood: a plan's cobblestone (its rough stone) and stone bricks (its fine stone)
    become a house's main stone, and its stone stairs, slabs and walls (the step round the foundation, the sills) the
    trim's, in its polished or brick form. Stones: cobblestone/stone bricks, deepslate (cobbled, bricks), andesite
    (raw, polished), tuff (raw, bricks). Only pairs chosen by eye: stone bricks with a polished andesite or deepslate
    brick trim; deepslate bricks with stone brick; tuff bricks with deepslate brick; polished andesite with deepslate
    brick or stone brick. No granite or diorite, and no stone dotted through another (the user's speckled towers);
    brick walls may be textured with their own stone only (cracked stone bricks; deepslate tiles and cracked bricks;
    polished tuff), one block in six, the same blocks every time (by position in the plan).
  - Which: a Drifter builds in a stone it has (on it, in its chests) or has seen round it (48 blocks, stone it may
    dig), if that's enough for the plan's walls (3 in 4 such houses; else cobblestone); a trim 7 in 10, if it has
    enough of that stone for the steps and sills; texture 6 in 10. Its taste per plan is fixed (drifter id + plan id).
    Saved in the woods map under `#stone`, `#trim`, `#mix` (so town builds pass it on through the API unchanged; old
    saves have none and stay as they were; the old cobblestone-to-deepslate flag still works for houses begun before).
  - Running out: a texture block it can't get, it drops the texture; a trim stone, the trim goes in the walls' stone;
    the walls' stone, the rest in cobblestone. What it built stays (Placement counts the same shape in another stone).
    Offscreen catch-up pays a house's stone in any building stone it carries. Andesite and tuff are now in the sources
    table (dug with a pickaxe, `drifter_sources/stones.json`), and not junk to a Drifter whose house is built of them
    (found in testing: it threw its andesite walls away).
  - `/drifter house parts` now clears what it built last time first (from its list file; the user saw houses running
    into each other when the rows moved), and has two new rows: a stone house and a cobble cottage in each pairing.
  - Tested: the parts rows in the dev client (pictures); `drifter house info` on a Drifter carrying andesite and
    cobbled deepslate (andesite/deepslate, stone brick/andesite, stone brick/deepslate picks); a townhouse in andesite
    with a deepslate trim from raw stone only (headless deep flat, 10 days): 826 of 888, polished andesite and deepslate
    brick stairs crafted and placed. Not tested: tuff found in the world (it's deep), a house switching stone midway.
  - Also merged the other session's 0.22.41 (how a war feels; no mining in towns); Conversations.pick keeps both its
    pressing-topics rule and this session's grounded/everyday split.
- **0.22.42-side.1 (side session):** merged the other session's 0.22.42 (nations, step 6 part 3). Headless smoke test: talks run, no errors.
- **Overnight side batch (0.23.7-side.1, on the other session's 0.23.7; the user, 2026-10-29: "doing the rest of whats left back to back? from chat to
  the houses"):**
  - Chat bug: after "trade?" (or "can i have something"), a message naming an item is the answer even when a word in
    it means something else ("a diamond sword" was heard as "seen diamonds?"); `PlayerTalk.heard`. Tested in the dev
    client: "nope, none".
  - Chat bug: insults with no reply ("fiery fuck you", "i dont like you" in the user's log): 70 more ways to be rude in
    `drifter_chat/insults_more.json` (swearing, "who asked", "i dont like you"...; nothing so general it catches normal
    talk: "this cave is so boring" is still small talk). Tested in the dev client.
  - 35 more topics (`drifter_talk/topics_grounded.json`, 628 in all): how it is now (hungry, a close call, lost its
    things, its diamonds, its hut, house or no home, what it built, a rare find, a big fight, its group), weather and
    time (rainy nights, thunder, mornings, evenings, clear nights), seven debates, advice and plans between friends,
    jokes and small talk. Checked with tools/check_talk_web.py; three run on a headless server.
  - Builders finish tall roofs (found: the top rows of any roof 10+ up, and dormers, were never built):
    `BuildPlanActivity`: (1) a step down off its pillar counted as having walked over for the next block, so each block
    it climbed down from was given up as out of reach; (2) spots chosen to work a little off the middle of the block (up
    a pillar it never stands quite in the middle); (3) it may stand on the roof itself (a bottom-half stair or slab under
    it, room above; no pillaring from there), as a player finishes a roof: from inside, the top rows of a two-layer roof
    can't be got at. Tested: a cobble cottage pasted with its roof's top rows taken off, rebuilt to 1264 of 1264 (dormers
    and ridge) in 3 days (before: 1114, stuck). Tried and dropped: pillars up to 9 (slower, no better).
  - House insides: chest labels (the furnishing guidebook's "label every chest"). `building/ChestLabels` (what a chest
    holds most of, from its memory: Wood, Stone, Ores, Food, Gear, Mob drops, Farm, Dirt & sand, else Misc),
    `LabelChestsGoal` + `LabelChestsActivity` (by day at home, after its house is built; signs of its main wood from six
    planks and a stick at the crafting table by its chests, planks fetched or made the usual way; sneak-placed on the
    chest's front, the label written on; a label that's out of date is written over), and `StoreActivity` labels chests
    in reach when it has signs on it. Tested: a made-ready town over 4 days (four labels by one townsperson; picture: an
    "Ores" sign on a bedside chest). Not done: rugs and other decoration.
  - Biome houses: sandstone and red sandstone in HousePalette (walls sandstone, cut sandstone where the plan has stone
    bricks, steps and sills smooth sandstone; counted from sand it knows, four to a block, and sandstone; sandstone, red
    sandstone and red sand in the sources table; a house's sand isn't junk). Tested: a Drifter on a flat desert picks
    sandstone for 3 of 4 plans.
  - Also: Placement counts the same shape in another stone (from the stones change), `/drifter house parts` stones rows
    have sandstone and red sandstone.
  - Found in the desert tests and fixed (these touch every Drifter, not only builders):
    - **The game ran at one tick a second** round a Drifter with a home and no bed where there are no sheep: the bed plan
      tried every colour of wool and dye down to the flowers (millions of sub-plans, 4.5 s a look), from the strategist,
      the "get a bed" goal and the spare bed and bed decoration whims. `Planner` now stops after 30,000 sub-plans
      (MAX_STEPS; an ordinary plan takes a few thousand) and settles for the best it has, and `Strategist` doesn't look
      for a way to a bed again for 30 s when it found none. A 3.6-day desert run went from over an hour to 20 seconds.
    - A crafting table it can't walk back to (at the bottom of a shaft it dug, sand fallen in) is forgotten, so it puts a
      new one down (`CraftActivity`); before, it went back to it again and again.
    - Levelling a house site with no dirt about: it fills with sand, red sand or gravel, and goes for whichever of those
      it knows of (`BuildPlanActivity.fillToGet`); before, a desert builder went for dirt forever and never built.
    - Gathering never digs stone with sand or gravel on top (it fell in, half smothered it and buried the drop;
      `GatherActivity`).
  - Not finished: a desert house built to the end in a test. On a flat desert (three sand, three sandstone, then stone)
    the builder got to 160-440 of 888 blocks; what stops it now is the old digging code, not the house code: its furnace
    ends up at the bottom of a shaft through the sand that it can't get back down. Its stone choice there was sandstone
    in 3 of 7 runs (it counts the sand it has seen, and a big house needs a lot).
  - Cross house (2,334 blocks): on a deep flat world it got to 1,025 blocks, then ran out of wood (no trees on a flat
    world); a run in a normal world is the test.

### Side session: stray cobblestone in houses (0.23.8-side.1)

The user (2026-10-29, screenshots of a made-ready town: cobblestone walls and blocks in rooms, cobblestone in a loft,
single blocks on porches; "this has been an ongoing problem, theres been several fixes and it keeps coming back").
(The cobblestone between the logs of a stone barn's walls is its design: `stone_barn` infill.)
- **New debug tools, kept in:** `/drifter placelog true|false` (every block a Drifter puts down, by hand or in
  catch-up, to the server log: `[placelog] <who> put <block> at x y z | goal | what it was doing | by <code>`;
  `debug/PlaceLog`, hooked in `Senses.placedBlock`, `UseOnBlockTask.click`, offscreen whims and catch-up huts; off by
  default, not saved); `/drifter house strays` (for every loaded Drifter's planned house, the solid blocks in it that
  aren't in its plan, furniture aside, to the log as `[strays]`); `/drifter dumpbox <from> <to>` (a box of blocks layer
  by layer to the log).
- **Main cause: catch-up built starter huts inside town houses.** `CatchUp.buildHut` (and `furnish`, the hut's door and
  stations) didn't look at `Home.noHut`, which town households have: whenever a town Drifter came back from being
  unloaded (the player away from the town), catch-up put a 9x9 cobblestone hut (walls round its feet, a roof two up,
  log and plank door frame) round its home spot, in the middle of its house. That's why it kept coming back: every
  fix was to the Drifters' own building and routes; this one placed blocks straight into the world, so the place log
  didn't see it either. Now neither runs for a home with no hut (town households), a planned house done, or a hut taken
  down (`CatchUp.noHut`). Tested: a town of 7-8 houses unloaded for half a day and a day, twice: with the old rule 132
  then 178 stray blocks in 3 of 5 houses (cobblestone 34-73 a house); with the fix 0 in every house.
- **Second cause: caving in houses.** `Caver.underground` counted any roofed spot as under ground except the Drifter's
  own house, so a dark town house was a cave: Drifters "went caving" in a cottage and pillared through its rooms (24
  cobblestone in 1.5 days). Now a spot under a roof or floor someone built (the first solid block over it, leaves aside,
  is Drifter-built or player-placed) isn't under ground; that test also decides surfacing, exploring, the cave hunch and
  mining trips. A 3-day rerun: no caving in houses.
- **Backstop:** routes never pillar or bridge indoors (`NavView.indoors`: on a built floor with something built within
  20 over its head; its own scaffolding doesn't count). Built marks go on a house only once it's finished, so a
  builder still pillars inside its own house while building it.
- Not seen in these tests: night walling-in inside houses (town Drifters sleep in their beds or stay up).

### Side session: stray cobblestone on town roads (0.23.8-side.2)

The user (2026-10-30, screenshots: lone cobblestone blocks on a town's sand roads and grass, beside a sandstone stair
road up a cliff; "okay so how can we fix this in the roads").
- **Traced** (`/drifter cobblescan <radius>`, new: cobblestone in the open round here, tagged `T` on a town's ground
  and `S:<name>` for whose scaffolding it is; with `/drifter placelog`): a town of 7 houses over 2 days and an away
  period, with the fixes off: 38 new blocks in the open within 120, 16 of them on town ground, every one a route's step
  up or bridge on an everyday errand (trading, the farm, lighting the base, fetching from its chest, setting off to
  mine). None was ever taken down.
- **Fixed:**
  - Town Drifters tidy their own pillars and bridges anywhere on their town's ground, whatever their tidiness
    (`TidyScaffoldActivity.inReach`); the tidy goal ran only with a starter hut, which town households don't have (now
    a hut, a town house or a planned house done), and is keener in a town (+0.4).
  - The tidy goal kept its keenness only until it started: from then it scored nothing, so the next thing to come along
    stopped it at once and the blocks stayed (every Drifter, not only in towns). It keeps it now until it's done
    (`TidyScaffoldActivity.finished`).
  - Routes cost 6 s more for each block put down on the town's ground (`PathSearch.TOWN_PLACE`, about 25 blocks of
    walking): they take the roads and steps. The town's ground is copied per town, a chunk at a time, for the path
    threads (`nav/TownGround`, made again every 5 minutes).
- **Result** (same test, fixes on, two runs): 1 and 0 new blocks on town ground after 2 days (the one still on its
  owner's list to take down), 2 after the away period (an owner off mining); 18 blocks taken down in one run. Blocks out
  past the town (mine entrances, exploring, Drifters passing through) stay, as a player's would.
- Not tested: the cliff stair road in the user's screenshot (no such road in the test towns).

### Side session: flat-roofed houses (0.23.10-side.1)

The user (2026-10-30, with their "Flat-Roof and Square Architecture Guidebook"): "lets start switching it up with flat
roof houses!". First made three guidebook styles (a modern box, an adobe with vigas, a mid-century house with a deep
overhang) with capped parapets, slab decks, stairs up to a stair tower on the roof, ribbon windows, a lower wing. The user:
"a bit too realistic, especially with the things on top; i was just thinking like the same houses but with square roofs
instead of actual roofs, because every house kind of looks similar", then "remove the hole in the roof and just make it a
flat roof", "lower the overhang a bit", "roofs that go outward aswell", "some of the plans roofs with full blocks instead
of slabs, while still keeping the slabs". So, as built:
- **Every style has a flat twin** (`HouseStyle.flatTwin`, added in `GeneratedPlans.load`): "cottage_flat" and so on, the
  same house (walls, windows, door, materials, wing), drawn half as often as its style: about a third of a world's plans
  (100 of 298 on a test seed). Its roof is a lid: one layer level with the walls' tops, a block out all round, nothing on
  it; 4 in 10 reach 2 out with wood slabs underneath (`cantilever`, `overhang`); half are full blocks (planks, or stone
  where the slab is stone; `lid_full`), half slabs. Twins leave out dormers, the tower, the cross and the door in the
  gable end; a flat-roofed square is drawn again. A two-storey twin's wing gets its own lid a storey up.
- `HouseGenerator`: roof "flat" (only styles naming it draw it, so older styles' plans are unchanged), `flatRoof` (the lid;
  or, for a style that asks, a capped parapet over a slab deck, a cantilever, vigas), `stairTower` (no style uses it now),
  ribbon windows; the top storey's lanterns hang under the lid; no tie beams under a flat roof.
- `/drifter house parts`: new parts (flat lid, 2 out, full blocks, two storeys with a flat wing); the twins are in the
  styles row beside their originals. Debug: `/drifter house plandump <plan>` (a plan's own cells, layer by layer, to the
  log).
- Tested: every plan passes PlanCheck. Builder runs in a fresh deep-flat world (enough wood given; no trees there): a
  farmhouse twin with a full-block lid built to all 765 of 765 blocks; a barn house twin to 1,008 of 1,016 (a corner
  post's top and a few lid blocks at its front corners out of reach; it moved in). Earlier, the parapet style built in
  full (1,311) over two runs.
- Found in testing: a beam across the top storey closed the hole over stairs up to a roof; a door can't stand on a bottom
  slab. Both fixed (the stair tower isn't used now). Also: `run/server.properties` had been left pointing at an old
  world folder (`deserttest`), so several runs reused one world; it's back to `world`.
- **0.23.11: W1 and W2, roads that look made and ground shaped to fit.**
  - Roads and door paths one material a settlement (the user: "when the roads are mixed, it should be of the same type, like
    stone texturing for cities, and planks for towns, just path blocks for hamlets"): a city's stone bricks (some cracked,
    some mossy; smooth and cut sandstone in the desert), a town's planks of its own wood, a hamlet's dirt path. Where a
    road or path climbs one step, a slab on the lower (stone brick, the town's wood, or Terrain Slabs' path slab in a
    hamlet when it's installed); where it climbs on, a block every block, stairs (stone brick, the town's wood): slabs on
    every step of a climb still left a block between them (found by the walk check, a city's store). Never a block-high
    step of earth. A door path's first block level with the
    door's floor (a step up right outside a door had no room over it, under the wall: found by the walk check, a store).
    Bridges: plank decks with fence railings either side, on log piers every few blocks.
  - Lamps from villages (the user: "lets steal the lamp builds from villages"): road lamps, plaza corners and the lamps
    for dark corners are the game's own village lamp posts by the town's style (plains, desert, savanna, taiga, the three
    snowy ones), read from its structure files with their jigsaw blocks swapped as a village does; the fence and lantern
    where one doesn't fit. Taiga village street bits (the user: "add the taiga decorations too"): a campfire, hay with a
    campfire, log seats, a garden box, in a third of front yards and on the plaza, in the town's wood (sandstone for
    cobblestone in the desert). `VillageBits`.
  - The ground plan (`GroundPlan`): the houses' spots are chosen first, then the town's ground is shaped once for all of
    it, a piece a step, then everything put up on it. Every column gets one height: the plaza, the store, the fields and
    each house at their own (a ring a block wide round each at that height), the land falling or rising a block every two
    from there till it meets the land as it was (gentle banks: a block a step looked like pits round the fields in the
    first pictures); its roads at the grades they're laid at, with shoulders and banks. Where two ask for more than banks
    give, the nearer wins and a retaining wall stands between (cobblestone, mossy cobblestone, stone; sandstone in the
    desert), grass on top. Filled ground gets the land's own layers (its top block, dirt, sandstone under sand, rock under
    rock), cut earth its top block again; trees on moved ground felled whole; water never filled. No more boxes cut into
    hills (each part used to flatten its own patch and ease ten blocks round it at most).
  - Tested headless on the user's Tectonic (seed 779, Terrain Slabs in too): a winding hamlet on a hillside, a main street
    town, a crossroads hamlet, a city of 57 houses, every place walked to, no step over 50 ms but a household and the
    city's walk check; pictures in the client: plank
    streets, dirt path with path slabs climbing a terraced hillside, village lamps, a log seat on the plaza.
  - Next: Terrain Slabs across the whole mod (the user: "this whole mod needs to be compatible with countered terrain
    slabs"), then W3 (river, harbour, hill, ring and grid shapes).
- **0.23.12: lamps that make sense, and Terrain Slabs' natural slabs.**
  - Lamps (the user: "there is always a lamp on top of a building, specifically the shared chest one", "too many,
    scattered", "make it make sense"): lamp posts first where a player puts them, one beside each door path where it
    meets its road and along each road every twelve blocks, sides in turn, none within six of another; then each dark
    spot lit the way a player lights a town: a lamp post beside the nearest road or path (the darkest spot there, not the
    edge of the dark), else a torch on the nearest wall, else a lantern on a fence post (the pen, a bridge), else a torch
    in the ground; never a lamp post on its own in the grass. Lamp posts stand only on the ground, never a roof; the store
    and the stall count as buildings (the store had a lamp on its roof). A hamlet: about 25 posts and 25 torches, where it
    had about 50 posts.
  - Terrain Slabs (the user: "this whole mod needs to be compatible with countered terrain slabs"): its slabs are in the
    game's slab tag, and Drifters took every slab for something built, so every hillside with its slabs looked like a
    player's build (no digging through it, no camps or homes on it, encounters not spawning there). The slabs it
    generates (marked `generated`) are natural ground now (`NavView.naturalSlab`); ones a player puts down are still
    built. The rest of the compatibility check is still to do.


### Side session: pillars left by trees (0.23.10-side.2)

The user (2026-10-30, screenshots: cobblestone pillars up beside an acacia in a town, one block up under its leaves;
"check logs for cobblestone scan"). Their game ran 0.23.10. The scan: 95 cobblestone blocks in the open within 100, 89
on town ground; most in rows at one height round the plaza (unowned: the town's own, by the look of them); the pillars
by the tree were AcornX's, fierymarsh's and EpicToast's, Drifters cutting acacia logs (`GatherActivity` pillars up beside a
trunk for logs out of reach).
- **Fixed: pillars under trees were never tidied.** `TidyScaffoldActivity.stillScaffold` took "out in the open" as the
  sky's full light over the block: under leaves that's a little less, and every block of a pillar but its top has the
  next one over it, so those were forgotten at the first look (it drops what fails the test from its memory). Now
  `inTheOpen`: nothing over it up the column but leaves and its own scaffolding (mines and caves still don't count).
- **Fixed: the get-down job left the bottom of its pillar.** Up on its own pillar with no blocks, `GetDownActivity` dug
  down only until the drop was safe and then dropped, leaving three blocks; it now digs its own pillar all the way down.
- `/drifter cobblescan` now also sees pillars under leaves and every block of a pillar (nothing over it but leaves and
  more of the same). `/drifter personality set <trait> <0-1>` (debug, for tests).
- Not reproduced in a test: three runs (a plains town, four Drifters by tall spruces on flat ground, a town with tall
  spruces planted) had few tree pillars; one left 3 blocks after the get-down (fixed above). The user's pillars may also
  be too tall to reach the top of from the ground (the tidy goes top first); to watch.

### Side session: desert houses, decorating, fixes, chat (0.23.12-side.1)

The user (2026-10-30): "do your recommendations, then do fixes, then do chat".
- **Desert houses get built.** Two causes: (1) `HousePalette.chooseStones` took a local stone only with enough of it
  known for the whole house; in a desert the sand seen early never added up, so it chose cobblestone, whose stone is
  under the sand: the builder dug for stone it never reached. Now, sand all round and little stone seen (a stone made
  from sand known at a quarter of the need, more than twice the stone seen): sandstone (red sandstone), always. (2) A
  furnace put down at the bottom of a shaft through the sand, its stone never collected: `PlaceStationActivity` goes back
  up home first for a furnace (not a crafting table) when it's 4-24 below a home near by; `CollectSmeltActivity` gives a
  furnace up after 3 failed trips back (`FurnaceJob.failedTrips`, "can't get back to the furnace: leaving what's in
  it"). Tested (the flat desert run that stopped at 169 of 888 for days): sandstone, 745 of 888 after 5 days, climbing.
- **Decorating** (`building/Decor`, `DecorateGoal`, `DecorateActivity`; the user's "rugs and other decoration"): once
  its house is built and it lives in it, by day at home, a rug of carpet on open floor in the main room (3x3, 3x2 or 2x2,
  as big as its wool makes, nearest the middle, never by a door) and a painting on a long blank wall at eye height (not
  glass, a door or a chest); carpet and the painting made the usual way (the planner: wool from its chests or sheep), a
  wool kept back for the painting; "laid a rug and hung a painting: feels like home". Keeps at it once started. Tested
  (one Drifter, a finished house, 7 wool and sticks): a 3x3 rug and a painting.
- **Fixes:** a chest in a house a Drifter built (on its floor) is never "loot" (`LootChestActivity.drifterBase`: town
  Drifters took their neighbours' and their own household's chests for loot); the tidy job climbs beside a pillar too
  tall to reach from the ground (`TidyScaffoldActivity.beside`, a pillar of its own, up to 2 a visit) and digs its own
  pillar back down after; the tidy runs for any home now (a hut or house isn't needed). Tested: an 8-high pillar taken
  down in full.
- **Chat:** `topics_home_life.json`, 40 topics (673 in all): the rug and the painting, rug colours, decorating or not,
  flat roofs, overhangs, thick roofs, houses all alike, the next house, desert living, sandstone houses, falling sand, a
  lost furnace, taking pillars down, a messy neighbour, tall trees, neighbours' chests, a neighbour's house, the town's
  roads and lamps, chest labels, a cozy house, a small house, the hut left behind, no house yet, stairs, long days,
  plans for the day, a forgotten tool, a full inventory, sheep for wool, complimenting a house, a housewarming, too much
  cobblestone; debates: paintings, flat roofs, stone or wood, carpet or slabs, one floor or two. Tested: forced in pairs,
  they play and lead on into others ("on that note carpet beats slabs for floors").
- Debug: `/drifter pillar <height>`, `/drifter personality set <trait> <0-1>`.
- **0.23.13: W3, more shapes, picked by the land** (the user: "add a river town shape too and more shapes"; picked: river,
  harbour, hill, ring, grid).
  - The land round the plaza looked over first (`survey`, every fourth block 80 out, and sixteen ways out from the plaza):
    a river (water 6 to 40 blocks across with land past it, within 56: the nearest water alone was once a stream, with
    the sea further off another way), a shore (water with no land past it, within 80), a hill (14 blocks or more between
    the highest and lowest ground within 48). A river makes a **river town**, a shore a **harbour**, a hill a **hill
    town**; elsewhere a town's shape by chance (crossroads 25%, main street 30%, winding 20%, ring 15%, grid 10%); a
    hamlet a main street (or a river or harbour hamlet by the water); a city a grid with crossroads out of it.
  - **River town:** a road from the plaza straight across the river by a bridge (up to 40 blocks of water), roads along both
    banks from its ends keeping off the water, a road away from the river, a dock or two from the banks.
  - **Harbour:** a road from the plaza down to the shore, a waterfront road along it both ways, roads inland, and piers
    every so often (planks a block over the water, three wide with railings, log piles, a lantern at the end, a boat tied
    up).
  - **Hill town:** its roads leave the plaza round the hill both ways and down it, following the contours (they climb
    least that way); houses on terraces (W2); a lookout on the highest free ground near the plaza (a little tower of logs
    and planks, a ladder up to a railed floor, lanterns on its corners).
  - **Ring town:** a ring road round the plaza (24 out, a city's 40, wavering a little; kept whole when roads are cut back),
    four spokes out through it, lanes off any road.
  - **Grid:** straight streets every 24 blocks round the plaza (a town's 30 out, a city's 72), with the four bending roads
    out beyond.
  - Bridges carry the road at its own height: a deck at least a block over the water on log piers, ramping down to the
    banks, railings both sides (at the water's own level, a bridge from a plaza on a high bank stopped at a wall). Roads
    leave the plaza level with it. Door paths: their first two blocks level with the door's floor (a slab right outside a
    door was a step up under the wall). Fields, the store and the pen never on water; the pen on level ground (on a
    hillside it ran down a staircase of cut rock). `/drifterciv town shape <...>` takes the new shapes too.
  - Tested headless on the user's Tectonic (seed 779; spots found with `/locate biome`): towns by a river (twice), by the
    sea, on a hill (twice), the river, harbour and hill shapes picked by the land; a ring town and a grid town (set): every house, the
    store, the stall and every field walked to in all of them; half a day in a river town of 16 people (none died;
    a few blocks put down near the plaza: a pillar up a retaining wall by the river,
    trees climbed for logs). Pictures in the client.
  - Not yet: two bridges in a big river town, a market by the water in a harbour, the fisher using the dock; lots of
    lamps still in a hill town (its terraces hold the light in); Drifters' own walking takes a slab for a full block (a
    step up needing a jump: Terrain Slabs compatibility, still to do).
  - Next: W4 + W5 (the world plan: nations, capitals and their towns; the roads and highways between them).
- **0.23.14: W4 + W5, the civilized world: nations made with the world, roads between them all.**
  - Settings (Mod Menu, new worlds only): **A world of nations** (on), **Nations** (few: 2 to 3; **4**; many: 6 to 8),
    **Spread** (round spawn, the default, to over the world: distances times 1 to 4). Core `DrifterConfig.civilizedWorld`,
    `nations`, `civSpread`; `DrifterApi.civilizedWorld()`, `nationsSetting()`, `spreadSetting()`.
  - Planned or not, decided as the overworld loads (before any of it generates): a world with a plan saved is; a brand new
    one is if the setting's on; any other (made before 0.23.14, or the setting off) keeps towns where villages would be
    (A1). In a planned world the town and city structures have no placements (`DrifterApi.setNotPlaced`, the same way
    villages are gone).
  - The plan (`WorldPlan`), made once as the world first starts, in the background, from the generator's own terrain (its
    rough surface, before caves and crags: the generator's exact heights take 8 ms each on the user's Tectonic, and the
    first plans took minutes; now about 7 s): capitals on a ring about 1,100 blocks from spawn (a second ring further for
    many nations), each on the best ground near its point (a few tries round the ring, on rougher ground if need be);
    each nation's 3 to 5 towns 550 to 950 out from its capital, 2 to 4 hamlets past some of them; two or three free towns
    between nations; a ruin or two; nothing within 300 of spawn (you start near a nation, not in it). Sizes as before (a
    capital 40 to 80 households, towns 8 to 16, hamlets 2 to 5).
  - Its nations made at once (`Nations.planned`), named from their capitals ("the Kingdom of X", "the X League"...), each a
    colour; their towns members already. Each town is made as before when a player comes near (`TownSites`), with the id
    and name the plan gave it, and is at peace and allied with its fellows already made (`Nations.plannedMade`).
  - Roads between them (W5): a nation's capital to each of its towns, hamlets to their nearest town, each capital to its
    nearest two (one network), free towns to the nearest place; routed over the land on a 32-block grid (climbs and water
    dear, the sea gone round), corners rounded off. Built a stretch (48 blocks) at a time as a player comes within twelve
    chunks (`Highways`), its land loaded first: a worn dirt path three wide (smooth sandstone in deserts), built up a block
    a step over dips, slabs (Terrain Slabs' path slab when it's there) or stairs on climbs, railed plank bridges on piers
    over water. Each town lays a main road out each way a road comes in (kept whole), and the stretch at its end waits for
    it and runs on to meet its nearest road; a signpost there with where the road goes and how far.
  - Streets on steep ground: all of a town's roads get their heights together, a block a step at most between any two side
    by side, halfway between built up over dips and cut into bumps (worked out each on its own, a high street and a low one
    crossing on a mountain stood a wall apart; filled only, a road down a mountain was one long embankment); level with the
    plaza where they leave it, those held. Each house is then set at its street's height (within a block or two) and the
    ground shaped round it (with streets cut into a hill, a house on the hill's own ground stood ten blocks under its
    street).
  - Spots checked on the land's real heights too: the plan's quick surface leaves out Tectonic's crags (a snowy peak looked
    like gentle ground), so each spot's core is checked at nine points from the generator itself.
  - Door paths: no slab or stair on the block right outside a door (a half step there still meets the wall over the
    door; found by the walk check, a mountain city's store).
  - Commands: `/drifterciv locate <city|town|hamlet|ruin|any>` (the nearest of the plan, made or not, its nation and how
    far), `/drifterciv plan` (every nation and its places, the roads built so far); debug `/drifterciv plan roads` (every
    stretch within 1,000 blocks built now, player or not), `/drifterciv town map <radius>` (the land round here from above).
  - Tested headless on the user's Tectonic (seed 779, a new world each time): the plan in 7 to 14 s (4 nations, 18
    places, 16 roads, 13,800 blocks of road); a capital (a grid city of about 40 houses on a mountain, 75 people) and one of
    its towns made, in its nation ("friendly" with its capital): every place in both walked to; 67 stretches of road built
    round them: from the city's south road down to the town, joined to its streets; others west over a river by a bridge,
    and east. Another capital (a grid city on a snowy mountain): 30 of its 32 places walked to (two A-frame cabins not:
    to look at). Every shape of town (0.23.13's test) still walked through.
  - Not yet: a capital by the sea with no towns round it in one test (no ground for them); the history (W6: nations
    feuding and allied from the start, ruins from wars); towns far away as summaries (W7); towns founded later by
    Drifters don't count toward the limit yet; lots of lamps in hill towns and mountain cities.
  - Next: W6 + W7 (a history already written; towns nobody's near as summaries).
- **0.23.15: Terrain Slabs across the mod** (the user: "this whole mod needs to be compatible with countered terrain
  slabs"; "do terrain slabs next").
  - How Terrain Slabs works (read from its code): ordinary bottom slabs (in the game's slab tag) put down by world
    generation where the ground steps up a block, marked `generated=true`; broken, they drop the full block. Plants on them
    are only drawn half a block lower on the client; on the server nothing else changes.
  - Walking: the pathfinder knew every floor as a full block, so a slab at the foot of a two-block rise looked like a
    one-block jump; from half a block lower it's a block and a half, higher than a jump (1.25, from the player's own jump
    numbers: `JumpPhysics.JUMP_HEIGHT`). It jumped there over and over, a quarter of a block short, until it gave up.
    Now a jump up counts the real rise (`NavView.floorTop`): over a jump, not a move; no higher than a player's step
    (0.6, the step height attribute's default), a step it walks up at walking cost, no lining up and no jump
    (`NavigateTask.halfStep`), as a player walks up a slab hillside.
  - Building: a natural slab is natural ground (`BuildPlanActivity.natural`, `BlockNotes.isNaturalGround`), so it's
    cleared out of a house's space like grass and counts as land, not somebody's build; a house spot reads the ground under
    it (`PlanSite`).
  - Towns (through `DrifterApi.naturalSlab`): the ground is the block under a natural slab (it counted as a block of ground,
    a bump in every street and plot); slabs are cleared off roads, highways, paths and plots like plants, and off ground
    that's cut down (one was left floating); a plaza's or a village lamp's spot with only slabs in it counts as clear.
  - Tested headless: a course on flat ground (a two-block wall with natural slabs along its foot, a proper slab ramp a few
    blocks along): the old build jumped at the wall from the slab for 40 s and never got up, then 36 s and 4 s on two more
    trips; the new one 4, 6 and 3 s, up the ramp, never stuck. 25 Drifters on the user's Tectonic (seed 779, Terrain Slabs
    in): 21 of 25 on taiga hills (the four stopped by spruce leaves, as before; the old build 24 of 25) and 25 of 25 on
    plains (18 s on average; the old build 19 s). Six towns of every shape on slab-edged land: every place walked to in all.
- **0.23.16: natural ground in towns, roads between towns cut as well as built up, door paths that find their way, no
  seeds dropped** (the user, 2026-10-30, with pictures: "the terraforming comes off a little bit too unnatural, and i dont
  think stone stairs goes with the path blocks"; a path "cuts through the whole town to the plaza, instead of connecting to
  a path from the town"; "whenever a town spawns, items break on the ground, specifically seeds ... and it lags sometimes").
  - Ground (`GroundPlan`): a pad's banks (a block every two) reach only past its doorstep ring now (`PAD_REACH` 2, was 24);
    past that, low terraces: the land may stand a wall's height (3) more off each 6 blocks out before it's moved, so a
    hillside gets a short retaining wall behind a house and terraces up the hill, not a quarry 24 blocks deep with a wall
    ten high at the back. Nothing's moved past 12 blocks from a pad (`MOST_REACH`); a house's spot must fit within that
    (`GroundPlan.misfit`: the land at that edge no more than its banks and terraces take, and no fill standing more than a
    wall over the water's edge), or it's turned down ("too steep or by water" in the log). Banks' edges wander (a smooth
    noise added to the distance, which is as the crow flies now: square rings of steps round rectangles before). Filled
    ground within 8 of water slopes down to it (a cliff of dirt into a river before). A slot one or two wide between ground
    two higher on both sides is filled (a hill city's slots between walls). Rock cut into under soil gets earth and its top
    block back (grey stone terraces before); lava a cut opens is sealed with stone. With Terrain Slabs, each step of moved
    ground (and land beside it a block under it) gets the land's own slab at its foot, "generated" as the mod's are
    (`GroundPlan.slabFor`). Roads' pull on the land worked out once over the whole area (`RoadField`), their banks 12 out.
    Land without loaded land all round it is never read or shaped (it made the game generate land there and then).
  - Roads between towns (`TownBuilder.highway`): heights halfway between built up over dips and cut through bumps, half a
    block a block at most, held to the ground at a stretch's ends; the land eased to them both sides by the same ground
    plan as a town's roads (a ridge a block wide with dirt sides before). Climbs by path slabs only (Terrain Slabs'; smooth
    sandstone in a desert; none without the mod), never cobblestone stairs. Ruins in the way (a ruined portal, or another
    structure with "ruin" in its name) cleared off it, their lava filled; pumpkins, melons, mushroom blocks and bee nests
    aren't ground (`litter`): it climbed over a ruined portal and its pumpkins before. Anything else built is left.
  - Hamlets: roads settled in half blocks (a block every two at most, a path slab between), paths and roads climb by slabs
    alone (`gentle`); towns keep wood stairs with planks, cities stone brick.
  - Door paths: out the side the door is on (the way from the door that's clear to the house's edge), then the shortest way
    round houses, the store, fields, the pen and the lookout to a road, the plaza or another house's path (`route`), the
    store's first and houses nearest the middle next; stairs and slabs facing the way it goes. Name signs by that side.
  - Dropped items and lag: the town's blocks are set without telling neighbours to look at their shape again
    (`UPDATE_KNOWN_SHAPE`): each block cut away under grass broke it with drops, and set off its neighbours in turn.
    Plants (grass, flowers, mushrooms, saplings: `plant`) are cleared off moved ground, up to two over the old top.
  - Tested headless on the user's Tectonic (seed 779): six towns of every shape, every place walked to in all; houses:
    10, 10, 10, 10 in the gentle ones (as before), Riverb 2 (before 4) and Hilly 5 (before 6) on rough and craggy land.
    Loose items after the six: 11 (8 brown mushrooms from felled trees, a sapling, a torch, a dirt block), 48 before the
    flowers were cleared too, seeds none. The mountain capital: 28 of its 52 houses (about 40 before, many on quarries
    and tall walls), all 32 places walked to; its town 13 of 14, all walked to. Roads: 63 stretches built, the slowest
    0.27 s, no freezes (a stretch shaping land still loading stalled 10 s before that was fixed).
  - Not yet: a mountain capital is smaller than before; lots of lamps in hill towns; founded towns don't count toward the
    limit.
  - Next: W6 + W7 (a history already written; towns nobody's near as summaries).
- **0.23.17: walkable roads and paths, roads between towns routed round hills, no cut-off roads, no lava bridges, fewer
  freezes** (the user, 2026-10-30, with pictures: "the paths are not walkable, they have steps that are 1.5 to 2 blocks
  high, some staircases are jumbled all over the place ... these stone paths look like they're TOO natural, at the expense
  of being convenient"; "some long paths get cut off in the middle and then continue"; "going AROUND hills and mountains
  for the easiest way there? instead of splitting a WHOLE mountain, or going across the ocean with a straight line").
  - Town roads: every town's (not just hamlets') heights settled in half blocks, a block every two at most in any
    direction, a slab on each rise: no stairs on roads, nothing a jump high, across a road too (three wide, it stepped
    across itself, and its stairs faced every way). Never cut below water beside it (a stream spilled down a road).
  - The step bug: a road's climbs read the heights off the blocks after slabs were down, so a slab counted as a block of
    ground and the next column got a slab a whole block over it; where two roads crossed the second paved over the first's
    slab, a block up. Now by the heights they were laid at (`laid`); paths read a road's height under its slab (`paved`).
  - Door paths: in half blocks where there's room (a slab each rise), else a block a step with stairs (`levels`).
  - The test, logged for each town and each stretch of road between towns: how many steps side by side along roads and
    paths need a jump (`countSteps`). Six towns: 3 to 6 of 1,500 to 2,400 (before: 17 to 143); the mountain capital 15 of
    12,379; roads between towns a few of about 250 a stretch (before the slab fix: a third).
  - Roads between towns, routed (`WorldPlan.route`): a finer grid (16 blocks; the plan's 32 for roads over 1,200 blocks),
    each step dearer the steeper (its grade squared; over one in two, a cliff, only if there's nothing else), a river a
    little, the sea or a lake (water 4 under the sea's level) a great deal; a quicker search leaning on the distance left
    (GREED 1.6, 25,000 cells at most). No way found: no road (it was a straight line, over the sea and up mountains).
    The plan takes about 30 s now (13 s before), in the background.
  - Their path slabs are placed ones, not "generated": marked as the land's, the next stretch cleared them off as plants.
  - Cut off in the middle (0.23.16's freeze fix went too far): the ground plan skipped land without loaded land all
    round it, so pieces of a road were left unshaped. Now land is read and shaped where its own chunk is loaded; only
    felling a tree needs the land round it loaded. A stretch at a town's end waits for the land out to the town's road it
    runs on to (read while still loading, the server stopped 30 s); nothing on land not yet loaded is read.
  - Lava isn't water (`TownBuilder.wet`): a road bridged a lava pool on planks and fence, which caught fire. Lava on any
    ground a town shapes, or on the land beside it, is sealed with stone.
  - Drifters (main mod): labelling chests at home only reads chests on loaded land (one away on an errand made the game
    load its home's land there and then, five seconds' stop).
  - Tested headless on the user's Tectonic (seed 779): six towns of every shape, every place walked to; the mountain
    capital 31 of 31 places walked to, its town 16 of 16; 62 stretches of road built, none over 0.2 s; maps show roads
    winding along the land and down from the capital in bends. A 2 to 5 s stop remains while every road within 1,000
    blocks is built at once (the debug command: chunks loading and the game's own ticks).
  - Not yet: lots of lamps in hill towns; founded towns don't count toward the limit; a road only waits for its land, so
    it's built a little behind you on fast travel.

- **0.23.18: towns on low flat land, winding roads that never cross the sea, roads leave houses alone, joined bridge
  railings** (the user, 2026-10-30, with pictures of a capital on a mountain cut into cliffs twenty high, roads straight
  over the sea, a house half buried by a road, plank bridges: "i think we need to rework city and town placement because
  idk if a city on a mountain or hill is gonna work", "still very long roads", "make the roads windy and scenic", "make
  sure the towns are around sea level, that way it automatically goes around the mountains", "the terraforming is infact
  destroying the houses", "diagonal bridges over rivers the fences dont connect", "random path patches in the middle of
  the bridge").
  - The plan reads the land by its real heights (`WorldPlan.real`: the generator's final terrain, from over the sea down,
    half a millisecond a spot). The rough terrain it used left out Tectonic's crags, and with Continents (in the user's
    profile, now in the test too) put the land 90 blocks under the sea: mountains looked gentle to towns and roads.
  - Placement: flat land only (a city's core within 10 blocks, a town's 8, a hamlet's 6, no cliff of more than 5 between
    spots 16 apart), its core at most 24 over the sea, the lower the better. Nothing suits: it looks twice, then three
    times as far (at most 1,000 blocks), then in any biome, then isn't made (it went on rougher ground before).
  - Roads: never over open water (water with no land within about 50 blocks: the sea, a lake; a river's still bridged);
    none whose way round is more than 1.7 times the straight line (and 200); nations joined by their nearest two towns
    within 1,600 blocks, not capital to capital (3,200 blocks over the sea in the user's world). They wind: the search
    leans on a smooth lie of the land (`wander`), and its corners are rounded three times.
  - A road between towns is cut or built at most 6 blocks from the ground (its banks reach no further: cliffs stood over
    it). It leaves anything built standing as it is (`TownBuilder.built`: planks, glass, a door... under the top): it took
    a house's roof for the ground and cut the house away.
  - Bridges: up to 8 steps of land in the middle of a crossing are bridged over (a sandbar was paved, dirt in the middle
    of the bridge); railings round a slanting bridge's corners get a post in the corner, and every fence is joined to
    those beside it (they only touched at their corners).
  - Tested headless on the user's world seed with Tectonic and Continents: 2 nations, 9 places, 6 roads (2,756 blocks;
    9,364 before), the plan 37 s. The capital on flat land by the sea: 60 of its 78 houses (31 of 77 on its hill before),
    64 of 64 places walked to, 29 of 15,164 steps need a jump; two towns 11 of 11 and 15 of 16 houses, all walked to.
  - Not yet: lots of lamps in hill towns; founded towns don't count toward the limit; a Drifter standing where a town is
    made can be built into a wall (one suffocated in the test).
  - Next: W6 + W7 (a history already written; towns nobody's near as summaries).
### Side session: Touch'n Motion on Drifters (0.23.18-side.1)

The user (2026-10-30): "this EMF mod called touch n motion ... i would love it if drifters were to be compatible with this
as well". Built on the other session's 0.23.17, then 0.23.18 merged in (0.23.18-side.1).
- **What it is:** Touch'n Motion (victorkozhokin/emf-compat, `extensions/touch_n_motion`, GPL-3.0, by STRadaT) is a
  Java mod, an EMF Compat Core extension, not a resource pack: procedural additions on top of an EMF player model (made
  for Fresh Animations: Player Extension): foot IK on stairs and slabs, balance, torso lean and pose inertia, hand on the
  wall, leaning on fences, hands through crops, look at creatures, doors/buttons/blocks used by hand, mining swings,
  fishing, fright, riding. It drives no `.jem` variables of its own: it moves the model parts after EMF has animated them,
  from its EMF animation hook (`TouchNMotionHook.onAnimationEnd`, `onBipedPoseCopyEnd` for armour), working out what to
  do right before the model is posed (`ModelSpace.before`, from its mixin on `LivingEntityRenderer.render`).
- **Why it skipped Drifters:** player only, everywhere: `ModelSpace.before` returns for anything not an
  `AbstractClientPlayer`, its hook for anything not a `Player`, and its features find players by id and UUID in the
  client world (`level.getPlayerByUUID`, `level.players()`, `DrawnEntities.inWorld`), mining by the block-crack list
  under the player's id. EMF itself already animates Drifters with Fresh Animations (the vanilla player model layers).
  Nothing in a resource pack could change that, and its code can't be copied in (GPL; Drifter is all rights reserved).
- **The bridge** (`client/render/DrifterTouchNMotion`, `DrifterStandIn`, `DrifterTouchNMotionHook`; mixins
  `LivingEntityRendererMixin`, `LevelRendererAccessor`): each Drifter within 64 blocks of the camera gets a stand-in
  `RemotePlayer` in the client world (made and removed only between ticks; never drawn, ticked by nothing but the copy,
  not pickable or pushable, no sprint particles; ids below every server id). After each client tick it copies the
  Drifter: position and old position, rotations, ground, water, powder snow, pose, shared and living flags (crouch,
  sprint, swim, glide, item in use), hands and armour, main arm, swing, hurt, sleeping spot, the walk (worked out as a
  player's). The Drifter's crack is put under the stand-in's id too. Right before the Drifter's model is posed, the
  addon's `ModelSpace.before` is called with the stand-in; then in EMF's hook (ours, registered at the first Drifter
  drawn, so after EMF Compat Core's) the addon's own hook is called with the Drifter's animation state made over to the
  stand-in (a proxy: its entity, UUID and "is a player" are the stand-in's), with that state on ETF's state stack for
  the armour copy. A fishing Drifter's bobber is owned by this stand-in (DrifterFishers), so the addon sees its fishing.
  All of the addon by reflection; EMF and ETF are compile-only (Modrinth maven), the hook class loaded only with both
  mods there. Any failure turns it off for the session with one log line; without the addon nothing is made.
- Stands down: riding (the stand-in isn't on the mount), dying, Better Combat swings and rolls (`swingPlaying`), beyond
  64 blocks. Setting: `touchNMotion` (on), on the Performance page and `/drifter config touchnmotion <true|false>`.
- Not covered here (done in side.2 below): hands, riding, the pocket.

### Side session: Touch'n Motion, the rest (0.23.18-side.2)

The user (2026-10-31): "how can we add the rest?", then "yea do all three".
- **Hands** (`hands/HandSignals`, mixins `HelperGameModeMixin` on `ServerPlayerGameMode.useItemOn` and
  `HelperPlayerMixin` on `Player.interactOn` / `attack`, each only for a Drifter's FakePlayer helper; `MineTask` and
  `ContainerTask` calls): the addon's server part relays each player's `HandsState` (use/attack held, the block or
  creature under the crosshair, the container open, stacks moved) and `HandsAct`s (feed, milk, shear, armour stand,
  seed) to others. A Drifter has no game, so our server sends the same from what it really does: a use holds USE 6 ticks
  on the block or creature; each mining tick ATTACK on the block (where its look meets it); a container from open to
  close with a count of stacks moved; acts worked out from the item and the target (wheat to an animal that eats it, a
  bucket on a cow or goat, shears on one ready, an armour stand, seeds on farmland). Sent on change only, as
  `drifter:hands` / `drifter:hand_act`, to players tracking the Drifter whose game takes them (registered on the client
  only with the addon there). The client hands the last state to the addon's `ClientHands.receive` every tick while
  busy (it forgets after 1.5 s), once when idle again, under the stand-in's id; with no block or creature named, the
  stand-in's own look (a pick) as the crosshair. Acts go to `ClientHands.receive(HandsAct)`.
- **Riding:** `DrifterStandIn.getVehicle()` is the Drifter's vehicle (the stand-in isn't among its riders, so vanilla
  never seats or moves it); while the addon looks (`ModelSpace.before`, both hook calls, the horse lift) the stand-in
  takes the Drifter's place in the vehicle's passenger list (`EntityPassengersAccessor`; put back in a finally), so its
  seat and who rows are right. On a horse: the addon's `ClientEventHandler` and `FootGroundingFeature` pre/post render
  steps around the Drifter's draw (as its mixin on `PlayerRenderer.render`), and its riding pose
  (`RidingPose.apply` with the horse's walk, kept as EMF Compat Core poses under the Drifter's UUID, cleared on
  dismount) at the end of the Drifter model's setupAnim, as its mixin does for a player.
- **Pocket:** `TakeItemMixin` (client, `ClientPacketListener.handleTakeItemEntity`): a Drifter's pickup calls the
  addon's `PocketStash.picked` with its stand-in.
- Riding Drifters are no longer left out. The extras are looked up apart: if they don't match, the rest still works.
- Debug: `/drifter useonentity <target>` (UseOnEntityTask), `/drifter config touchnmotion <true|false>`.
- Tested in the dev client (EMF, ETF, EMF Compat Core, Touch'n Motion 1.0.4, Fresh Animations + FA Player Extension):
  the chest (USE on it, the container open, a stack moved, closed: the hand on the chest's edge in the shot), feeding
  (act 0), shearing (act 2), planting (act 4; bent over the farmland with the seeds), all handed to the stand-ins; the
  boat (rider 0: arms out to the oars, against a plain sit with it off), the horse (hands forward and leaning, against
  upright), a minecart (rider 0); pickups handed on (the pocket gesture itself not seen: the Drifter was behind a sheep).
  A Drifter put on a boat or horse by `/ride` gets off after a second (`dismountIfIdle`), so only its own rides last.
- **0.23.19: the world built before it's played (a loading screen), towns keep off what's built, no door paths, one
  road where two go the same way, round ruins, natural slopes in the wild, snow on moved ground, tidy roads, lamps apart,
  a plan only for its own world** (the user, 2026-10-30/31, with pictures: "the whole world plan needs to be pregenerated
  before i load the world that way i dont see everything unfold in my eyes ... it could be like an extra loading
  screen?"; "cut up houses still happen", "the house is not part of the town"; "i think we can remove paths that connect
  to houses doorways"; "there is usually two roads right next to eachother and they both lead the same way"; "paths should
  go around any type of ruins/outposts"; "the slopes are smooth instead of cobblestone walls ... that can only be for paths
  in the wild ... the cobblestone in towns can stay"; "when it comes to snowy biomes it doesnt blend in well"; "lamps most
  likely need to be spread out"; "floating leaves, floating vines on the paths").
  - `WorldBuild`: a new civilized world, as it first opens, makes its plan, then every town in it and every road between
    them, their land generated for them (two towns' land loading ahead of the one being made, six stretches of road at
    once, 40 ms a tick on towns), before anyone plays. Players wait behind a loading screen (the addon's client:
    `WorldBuildScreen`, the title screen's panorama, what it's on, a bar, a clock; it can't be closed), held where they
    stand and unhurt. Left halfway, it carries on when the world's opened again. Old worlds: as before.
  - The plan works the land's heights out ahead on all but one core at once (`WorldPlan.prefetch`): a plan of the user's
    world in 11 s (6.7 minutes one at a time, with the game running beside it).
  - A plan is only for the world it was made for (`WorldPlan.generation`): the user left a world before its plan was
    ready and made another; the first's plan was handed to the second as well, its places on the second's land, a capital
    in the sea (Coppercrest, "water in 1257 of 1257 samples") and a hill town with 35 lamp posts for 3 houses.
  - Cut houses: a Drifter's own house, built before the town was made round it, was cut through by the town's ground,
    roads and paths. A town now finds what's built on its land first (`TownBuilder.built`: blocks the land doesn't make,
    under the top) and keeps its roads (they stop short), houses, lots and paths off it; its ground isn't shaped there.
    Roads between towns already did (0.23.18).
  - No paths from house doors (they stood up as blocks of stone brick by the doors); the store keeps its path.
  - Roads between towns: a later one along an earlier one's way costs a third (`ON_ROAD`), so roads the same way run as
    one; where it runs within 7 blocks of one already planned only its pieces off it are built, each joined to it (no two
    side by side, none paved over another where it branches or crosses); a town gets one way in for them. Their way goes
    round where the world's own placement puts a structure on the land (a ruin, an outpost, a temple: `structureBy`, 8
    times the cost); in the wild their banks are slopes of the land's own earth a block at a time (`GroundPlan.wildSlopes`,
    `slope`), no cobblestone walls. Towns keep their cobblestone terraces and retaining walls.
  - Snow: moved ground where snow lay gets a layer of snow back (and its grass snowy).
  - Tidy: along roads and paths, after they're built, trunks left in the air and leaves no tree holds go (the main mod's
    tidy, `DrifterApi.tidyTrees`), and vines with nothing to hang on.
  - Lamp posts at least 12 blocks apart (`TownLights.LAMP_APART`); a dark spot between gets a torch.
  - Debug: `/drifterciv plan height` (the land's height here as the plan reads it, and as generated).
  - Tested headless on the user's world seed (Tectonic, Continents): the plan in 10 s, the world built in 208 s (1 city,
    3 towns, a hamlet, a ruin, 40 stretches of road); every place in every town walked to, the capital 54 of 54 houses,
    0 of 14,333 steps a jump. On another seed of theirs, 3 nations and 20 places: 18 minutes with the game running beside
    it and before the land's heights were worked out ahead (the plan 6.7 minutes of it).
  - Not yet: a road stretch now and then has a few steps a jump where it was held to 6 blocks off the ground; founded
    towns don't count toward the limit.
  - Next: W6 + W7 (a history already written; towns nobody's near as summaries); LOD mods (Distant Horizons) after.

- **0.23.20: the loading screen under a minute (the plan in about 10 s, only what's in sight of spawn before play, the
  rest built ahead out of sight as they play); land at the sea's level is land** (the user, 2026-10-31: "okay how can we
  optimize building the world / mien took about 5 minutes"; "the goal is to get this down to a minute at most").
  - Where the five minutes went (their log): the plan 60 s (roads 46 s, 9 of 14 roads finding no way), then 275 s of
    building, most of it waiting for land to be generated: about 10,000 chunks for 16 places and 58 stretches of road.
  - Before play now only what's within sight of spawn and six chunks more (`WorldBuild.FIRST_CHUNKS`); everything else
    is built as players play, nearest first, their view distance and 20 chunks more ahead of them (`WorldBuild.ahead`:
    464 blocks at the user's 9; it was twelve chunks, a town put up in plain view): a town or two's land loading at a time
    (two while the world's readied), three stretches of road (six). A town's land is asked for as soon as the plan's
    places are chosen, before its roads are planned (`Plan.placesChosen`); a town of the plan waits for the whole plan.
  - The plan: a spot's looked for nearest first, 48 at a time, till 16 suit (it read the land over a circle a thousand
    blocks across for each first); a height's looked for from over the last one worked out near it, not from high over
    the sea (`Plan.hints`): the plan of the user's world in 9 to 23 s (37 s), heights 1.5 ms each on all cores.
  - Land whose top is at the sea's level is a beach, not water (water's a block under it): taken for water, the low land
    round towns by the sea was sea, and 9 roads of 14 found no way (3 of 14 now).
  - Trees tidied only on land really there (`Felling.writable`: hasChunkAt says yes for land still being made, and a
    road's tidy stopped the server six seconds waiting for it).
  - Tested: headless on the user's seed with their view distance, ready 33 s after the world opened (the plan 23 s of it);
    in the dev client, a brand-new world: the loading screen ("Planning the nations", the bar, the clock) then play 23 s
    after joining.
  - Next: W6 + W7; LOD mods (Distant Horizons).
- **0.23.21: no half-built houses, towns in swamps keep their land, one bridge where two roads cross a river, no
  stacked slabs, no trees or grass gathered in town** (the user, 2026-10-31, with pictures: "bridges being mixed/blocked
  off / stacked slabs, especially for path blocks making it look weird / Cutoff houses / and i think we need to remove the
  need for drifters to cut down trees and grass in town, and only look for that in the wild, it causes all that pillaring
  they do").
  - Cut-off houses, found at last (a watch on every block taken out of a town house, `[watch]`, since taken out again):
    the town itself. Each town left its newest house with everything over a third of its height taken off, for its owner
    to finish over the next days (A5, a town's past); it looked like a house cut in half, three times reported. Gone: every
    town house is whole. In the user's world, 2 of about 100 houses (`/drifterciv town damage`, debug: town houses with
    blocks gone, by layer).
  - Towns in a mangrove swamp kept off thousands of columns of their own land (`TownBuilder.built` took mangrove roots
    and moss carpet for building: 5,473 and 3,206 columns round one town, 2 houses of 11): the land's growing things count
    as land now (mangrove roots, moss, flowers, saplings, corals, cave vines, sugar cane, cactus, bamboo, kelp, dripleaf...).
    `/drifterciv plan built` (debug): what's counted as built within 128 blocks, by block.
  - Roads between towns: under a slab already laid, the block it's on is their ground (`paved`): the stretch beside, sharing
    its last few blocks, paved over its slabs as ground, a block up; two roads within 10 blocks of each other are one
    (`SHARE`, was 7: two bridges side by side over a river); a deck laid where another road's railing stood takes the
    railing off, and no railing goes across another bridge's deck; a bridge's wood is from where it is, not the stretch's
    middle (two woods on one river).
  - Drifters in a town gather no trees, leaves, grass or flowers there (`KnownBlocks.mayGather`, `DrifterApi.setSettled`:
    a town's land, its furthest house and 24 more): wood and seeds come from the woods round it, no pillaring up the last
    trees between houses. Clearing a house's own spot still can.
  - Tested: two towns of the user's world and the road between, 6,000 ticks: no town house with a block gone (one before),
    every place walked to.
- **0.23.22: W6 + W7, a history already written and towns nobody's near living by it** (the user, 2026-10-31: "you are
  free to start w6 and w7, that being included in the pre generator loading screen").
  - `History`: made with the world plan, on the loading screen (13 ms), after its roads: 1,000 days of the places' past a
    day a step, on summaries. Each place: a leader with a nature (aggression, warmth, ambition), who comes after them
    (250 to 700 days a leader; a nation's ruler's passing in the chronicle), its people (born, and lost in battle), when it
    was founded (capitals first, their towns after, hamlets later; the plan's ruins abandoned "after a hard winter"...).
    Between powers (a nation, or a town in none): a standing their rulers' natures move each day, nearer seats grating
    more; quarrels at the border; a peace pact on good terms, an alliance after; war on bad terms by an aggressive ruler;
    battles near the frontier towns that cost each side people (a leader falling now and then); a frontier town taken (to
    the winner's nation, conquered) or burned (a ruin); a nation conquered whole by one much stronger; peace when they're
    weary or the loser asks; a conquered town wearing of its nation and, its leader ambitious, rising up (free, or at war);
    a free town joining its ally's nation. Its events go in the chronicle, days before day 1 (34 to 36 for 4 nations).
  - Towns made to match: in the nation their past left them in (a nation's founding day its capital's), a ruin where a
    town fell, their leader the past's (the first person of the town is named so and leads it), founded when the past
    says; with each town of another power already made, their standing, pacts and any war still on
    (`WorldPlan.made`); their people's grudges with the people of towns their nation fought (`TownHistory`: a grudge 55% of
    the time, not 15%). A conquered town pays its nation more and may rise up (`Nations.Member.conquered`).
  - W7: after day 1, the powers none of whose towns are made go on by the same summary, a step each game day (their news in
    the chronicle as "News from afar: ..."); the nations follow (`Nations.follow`: a town taken or fallen moves, a nation
    conquered whole goes), and a town made later is made as its summary has it then (fallen since: a ruin). A power with a
    town made lives by the towns' own diplomacy from then on. Saved with the plan; worlds planned before have no past.
    (Not done: a town made in full going back to a summary when nobody's been near a long time; the main mod's offscreen
    Drifters carry made towns.)
  - `/drifterciv plan` says how far its past has gone; its list shows fallen towns as ruins.
  - Tested headless on the user's seed: the past (4 nations, 2 wars at once, a town burned, rulers coming and going, a
    border quarrel or two a year); three towns made to match (a ruin where one burned, an old grudge between towns of
    nations that quarrelled); 10 days on, saved and opened again: its past went on from day 11.
  - Next: phase W done (but LOD mods, after); back to phase B, or what the user wants next.