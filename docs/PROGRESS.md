# Drifter: progress

Tracks the build roadmap from `docs/DESIGN.md` section 25. Work one milestone at a time.

Status values: **Not started**, **In progress**, **Done** (done means its "done when" test passed in-game on the user's machine).

## Current state (end of session 1, 2026-10-13)

- **Build: green.** `./gradlew build` succeeds in the cloud (with the Maven Central mirror workaround described in `CLAUDE.md`).
- **Milestone 1 (Body): done.** The user tested it in-game (Modrinth App, jar from the GitHub Actions build) and reported that everything works.
- **Milestone 2 (Hands): the "done when" test passed in-game.** The user ran `/drifter script woodtostone` and reported it works perfectly. The rest of the milestone 2 checklist in `docs/TESTING.md` isn't fully tested yet; the user plans to finish it in a local session.
- The user tests by downloading the jar from the GitHub Actions run for the latest push (Artifacts zip, the jar without "sources") into a Fabric 1.21.1 instance with Fabric API, in the Modrinth App. The user doesn't code, so keep instructions plain and step by step.

## Milestones

| # | Milestone | Status | Notes |
| --- | --- | --- | --- |
| 0 | Project setup | Done | Cloud build green; the user ran the mod in-game. |
| 1 | Body | Done (in-game test passed) | Entity, player rendering with skins and poses, inventory, hunger model, virtual-key movement, camera model. Done when: you can drive it by command and it moves like a player. |
| 2 | Hands | In progress: wood-to-stone script passed in-game; rest of the checklist pending | Fake-player helper; mining, placing, crafting, smelting, eating, containers. Done when: a scripted sequence goes from punching wood to stone tools. |
| 3 | Senses and memory | Not started | View cone, awareness meter, block sampling, hearing, memory tiers, debug overlay. Done when: the overlay shows exactly what it knows and nothing more. |
| 4 | Pathfinder | Not started | A* with digging, bridging and pillaring. Done when: it crosses a ravine and tunnels through a hill to reach a target. |
| 5 | Brain and day one | Not started | Selector, planner, executor, the Stage 1 to 3 loop. Done when: a Fresh Drifter survives its first night unaided on most seeds. |
| 6 | Combat and reflexes | Not started | Mob targeting, per-mob protocols, reflex catalog; combat-mod test pass. Done when: it survives three nights in a row. |
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
3. Start milestone 3 (Senses and memory): view cone, awareness meter, block sampling, hearing, memory tiers, debug overlay.

## Decided

- **Build setup:** Loom 1.17 + Gradle 9.5.0 on Java 21 (user: "use your recommendations").
- **License:** All Rights Reserved (user: "use your recommendations").
- **Idle head jitter:** keep the design value (0.4 degrees). The user tested milestone 1 in-game, reported everything works, and chose my recommendations (which was: only raise it if it looks frozen).
- **Own spawn group:** option (a), the mod's own rare spawner instead of a vanilla spawn group; the entity stays in `MISC` (user: "use your recommendations"). See design question 1 below for the background.
- **Player sleep blocking:** a small mixin on the bed check, when that milestone comes (user: "use your recommendations").

## Design questions

Questions for the user. Nothing here has been changed in the design.

1. **(Decided: option a.) Own spawn group (sections 18 and 22).** On 1.21.1, mob spawn groups are a hard-coded enum (`MobCategory`), and Fabric API has no way to add a new one. Options: (a) don't use vanilla natural spawning and run Drifter spawning from our own rare spawner (section 4 already describes a custom, rare, out-of-sight check every few minutes), which also keeps them out of the monster and creature caps; (b) add an enum entry via a mixin/enum-extension hack. I recommend (a). For now the entity type uses the `MISC` category, which means no vanilla natural spawning and no share of any cap, consistent with (a).
2. **(Decided: use a mixin.) Player sleep blocking (section 18).** Vanilla only blocks sleep for mobs of the `Monster` class. Making a hostile-to-you Drifter block sleep will need a small mixin on the bed check. Fine on 1.21.1; no decision needed unless you object to mixins there.
3. **Idle jitter is mostly invisible over the network (section 16.1).** Minecraft sends entity rotations to clients in steps of 360/256 = about 1.4 degrees. The design's "fraction of a degree" jitter therefore shows up as an occasional 1.4-degree tick rather than a smooth tremble, and only when the jitter crosses a step. Options: (a) accept it; (b) raise the jitter amplitude to about 1.5 degrees so it reliably shows; (c) later, send exact rotations with a small custom packet (costs a little network per Drifter). **Decided: (a), keep 0.4 degrees** for now; raise it if the user later finds the head looks frozen.
4. **Break speed computed on the Drifter (section 3).** The design says to compute mining time through the fake-player helper. I compute it on the Drifter with an exact copy of the player's formula instead, because the helper never ticks, so its Haste, Mining Fatigue and Efficiency would be out of date. The numbers are identical to a player's. No decision needed unless you want it done differently.
