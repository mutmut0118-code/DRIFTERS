# Drifter: progress

Tracks the build roadmap from `docs/DESIGN.md` section 25. Work one milestone at a time.

Status values: **Not started**, **In progress**, **Done** (done means its "done when" test passed in-game on the user's machine).

## Current state (end of session 1, 2026-10-13)

- **Build: green.** `./gradlew build` succeeds in the cloud (with the Maven Central mirror workaround described in `CLAUDE.md`).
- **Milestone 1 (Body): code complete, waiting for in-game testing.** Nothing has been run in Minecraft yet. Every item in `docs/TESTING.md` is untested.

## Milestones

| # | Milestone | Status | Notes |
| --- | --- | --- | --- |
| 0 | Project setup | Done (build verified in cloud) | Local build and launch not yet confirmed by the user. |
| 1 | Body | In progress: code complete, untested in-game | Entity, player rendering with skins and poses, inventory, hunger model, virtual-key movement, camera model. Done when: you can drive it by command and it moves like a player. |
| 2 | Hands | Not started | Fake-player helper; mining, placing, crafting, smelting, eating, containers. Done when: a scripted sequence goes from punching wood to stone tools. |
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

### Deviations from the template (please confirm)
- **Loom 1.17 instead of 1.18, Gradle 9.5.0 instead of 9.7.1.** The template's Loom 1.18 is compiled for Java 25, so Gradle would need a Java 25 JDK just to start. Loom 1.17 is the newest version that runs on Java 21; Gradle 9.5.0 is the version Loom 1.17 itself builds with.
- License in `fabric.mod.json` set to `All-Rights-Reserved` and no LICENSE file added (the template ships CC0).
- Mod version starts at `0.1.0`.

## Milestone 1: Body

All vanilla behavior below was mirrored from the decompiled 1.21.1 source (Player, LocalPlayer, ServerPlayer, FoodData, Inventory, PlayerRenderer), not from memory.

### Done (code complete, untested in-game)
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
- **In-game verification** of everything above (the milestone's "done when" test).
- **EMF / Fresh Animations Player Extension check** (section 2 says to test this first in milestone 1): needs the user's local install. Instructions are in `docs/TESTING.md`.
- Skill is a fixed placeholder (0.5) in the camera model until traits exist (milestone 7).
- Elytra glide body tilt and the sleeping pose are rendered by vanilla code paths but untested; they belong to later milestones (beds, elytra).
- Optional "veteran" cape (section 2): not built.

### Deliberately left for later milestones (stubbed or noted with TODOs)
- Eating, block breaking/placing, crafting, attacking (and their exhaustion): milestone 2 (Hands) and 6 (Combat).
- Pickup filters and junk list (section 10.18): later (TODO in `DrifterEntity#tryPickUp`).
- XP orbs, XP on death, death messages, respawn and keepInventory: later milestones (TODO in `DrifterEntity#dropEquipment`).
- Hostile mobs targeting the Drifter: milestone 6.
- Username generator and always-visible nameplate (section 4): for now a Drifter only shows a nameplate if given a name (`/drifter spawn <name>` or a name tag).
- Natural spawning and despawning rules for fresh Drifters (section 4): later (TODO in `DrifterEntity#removeWhenFarAway`).

### Next steps
1. User runs the milestone 1 checklist in `docs/TESTING.md` and reports problems.
2. Fix whatever the in-game test finds; then mark milestone 1 done.
3. Start milestone 2 (Hands): Fabric `FakePlayer` helper, mining with player break speed and crack overlay, placing with reach and face checks, 2x2/3x3 crafting, furnace use, eating, containers.

## Design questions

Questions for the user. Nothing here has been changed in the design.

1. **Own spawn group (sections 18 and 22).** On 1.21.1, mob spawn groups are a hard-coded enum (`MobCategory`), and Fabric API has no way to add a new one. Options: (a) don't use vanilla natural spawning and run Drifter spawning from our own rare spawner (section 4 already describes a custom, rare, out-of-sight check every few minutes), which also keeps them out of the monster and creature caps; (b) add an enum entry via a mixin/enum-extension hack. I recommend (a). For now the entity type uses the `MISC` category, which means no vanilla natural spawning and no share of any cap, consistent with (a).
2. **Player sleep blocking (section 18).** Vanilla only blocks sleep for mobs of the `Monster` class. Making a hostile-to-you Drifter block sleep will need a small mixin on the bed check. Fine on 1.21.1; no decision needed unless you object to mixins there.
3. **Idle jitter is mostly invisible over the network (section 16.1).** Minecraft sends entity rotations to clients in steps of 360/256 = about 1.4 degrees. The design's "fraction of a degree" jitter therefore shows up as an occasional 1.4-degree tick rather than a smooth tremble, and only when the jitter crosses a step. Options: (a) accept it; (b) raise the jitter amplitude to about 1.5 degrees so it reliably shows; (c) later, send exact rotations with a small custom packet (costs a little network per Drifter). I've implemented the design value (0.4 degrees) for now; please tell me which you prefer after seeing it in-game.
