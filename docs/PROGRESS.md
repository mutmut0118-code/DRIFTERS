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
| 2 | Hands | In progress: wood-to-stone script passed in-game; rest of the checklist pending | Fake-player helper; mining, placing, crafting, smelting, eating, containers. Done when: a scripted sequence goes from punching wood to stone tools. |
| 3 | Senses and memory | Done (fully tested in-game; glass fix needs a re-check) | View cone, awareness meter, block sampling, hearing, memory tiers, debug overlay. Done when: the overlay shows exactly what it knows and nothing more. |
| 4 | Pathfinder | Done (in-game: ravine bridging, village, drops, doors) | A* with digging, bridging and pillaring. Done when: it crosses a ravine and tunnels through a hill to reach a target. |
| 5 | Brain and day one | In progress: in-game testing, fixes going in | Selector, planner, executor, the Stage 1 to 3 loop. Done when: a Fresh Drifter survives its first night unaided on most seeds. |
| 6 | Combat and reflexes | In progress: first version written (user asked to start it during milestone 5 testing) | Mob targeting, per-mob protocols, reflex catalog; combat-mod test pass. Done when: it survives three nights in a row. |
| 7 | Mannerisms, personality and mood | Mostly done: the user tested it and moved on to milestone 8 (session 2); leftovers listed below | Done when: people watching a short clip can't reliably tell it from a player. |
| 8 | Progression and building | **Done** (2026-10-15): 10-day runs reach diamonds with a house and farm (batch 21: three of three, no deaths); played by the user | Stages 4 to 8, shelter tiers 3 to 5, farming, animals. Done when: an unattended 10-day run reaches diamonds with a recognizable base. |
| 9 | Disposition and PvP | **Done** (2026-10-15): every section 14 event moves Trust as specified (headless table test); gifts, warnings, raids, looting and the manhunt (hunter) played by the user | Relationships, telegraph rule, hostile and friendly behaviors. Done when: every event in the section 14 table moves Trust as specified. |
| 10 | Offscreen and performance | **Done** in code (2026-10-15): 10 Drifters at 1 to 4 ms a tick, 100 at about 10 ms | Detail tiers, catch-up, abstract travel. Done when: 10 Drifters stay within the per-tick budget. |
| 11 | Nether | **Done** (2026-10-16): portal, packing, trips, safety, blaze rods, goods, piglins and bartering, storing the haul; polish left (hard fortresses over lava) | Section 19. |
| 12 | Groups, modpack factions, camps, config polish | **Done** (2026-10-16): Drifter bonds, groups, joint trips, team wars and raids, Mob Control, lived-in world, settings screen | Plan below |
| 13 | Whim system | In progress: engine, 27 action types, 100 vanilla whims | Whim data format, gates, cooldowns, archetype weights, then the whims in docs/WHIMS.md (vanilla first, modded behind mod detection). Can start any time after milestone 9. Swapped with The End (user, 2026-10-16); End whims stay off until 14. |
| 14 | The End (optional) | Not started | |

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
9. **Better Combat and Combat Roll (section 21, milestone 6 "combat-mod test pass").** What already works without any special code: modded weapons (Simply Swords and the like) are judged by their real attack damage and speed, and its reach comes from the reach attribute, so they're picked up and used correctly. What section 21 asks beyond that: reading Better Combat's per-weapon ranges and combos, and giving the Drifter the same dodge roll as Combat Roll. Doing that properly means compiling against those mods (as optional, "compile only" dependencies, never required at runtime), which adds their download sites to the build. **My recommendation:** for this milestone, just run the combat-mod test pass to check nothing breaks with them installed (the Drifter's hits go through vanilla's player attack, which those mods leave working), and add the real Better Combat / Combat Roll integration as a follow-up once you say it's OK to add those optional build dependencies. **Decided (user: "yea add them later, use your recommendation"):** test pass now; the optional compile-only integration comes later as a follow-up.
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
