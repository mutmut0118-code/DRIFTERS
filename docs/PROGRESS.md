# Drifter: progress

Tracks the build roadmap from `docs/DESIGN.md` section 25. Work one milestone at a time.

Status values: **Not started**, **In progress**, **Done** (done means its "done when" test passed in-game on the user's machine).

## Current state (end of session 1, 2026-10-13)

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
| 7 | Mannerisms, personality and mood | Not started | Done when: people watching a short clip can't reliably tell it from a player. |
| 8 | Progression and building | Not started | Stages 4 to 8, shelter tiers 3 to 5, farming, animals. Done when: an unattended 10-day run reaches diamonds with a recognizable base. |
| 9 | Disposition and PvP | Not started | Relationships, telegraph rule, hostile and friendly behaviors. Done when: every event in the section 14 table moves Trust as specified. |
| 10 | Offscreen and performance | Not started | Detail tiers, catch-up, abstract travel. Done when: 10 Drifters stay within the per-tick budget. |
| 11 | Nether | Not started | Section 19. |
| 12 | Groups, modpack factions, camps, config polish | Not started | |
| 13 | The End (optional) | Not started | |

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

## Long-term goal: manhunt test

The user's "ultimate test" is a **1v1 manhunt**, now in DESIGN.md section 25 ("Manhunt test mode"). It's **only a test**:
the mod must not be centred around it. **Hunter role first** (acceptance test after milestone 9), speedrunner role after
milestone 13. Keep it in mind when building chasing (milestone 4), combat (6) and hostile behaviour (9), but don't add
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
4. **Break speed computed on the Drifter (section 3).** The design says to compute mining time through the fake-player helper. I compute it on the Drifter with an exact copy of the player's formula instead, because the helper never ticks, so its Haste, Mining Fatigue and Efficiency would be out of date. The numbers are identical to a player's. No decision needed unless you want it done differently.
