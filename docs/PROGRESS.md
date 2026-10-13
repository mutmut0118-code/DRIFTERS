# Drifter: progress

Tracks the build roadmap from `docs/DESIGN.md` section 25. Work one milestone at a time.

Status values: **Not started**, **In progress**, **Blocked**, **Done** (done means its "done when" test passed in-game on the user's machine).

## Current state (end of session 1, 2026-10-13)

- **Build status: not verified.** The cloud sandbox's network policy blocks the Fabric and Mojang hosts, so `./gradlew build` cannot resolve Fabric Loom. See "Blockers" below.
- Project scaffold is in place from the official template; no gameplay code yet.

## Blockers

### Cloud build cannot download dependencies (session 1)

`./gradlew build` was run once and failed at plugin resolution:

```
Plugin [id: 'net.fabricmc.fabric-loom-remap', version: '1.17-SNAPSHOT'] was not found ...
  Searched in: Fabric(https://maven.fabricmc.net/), MavenRepo, Gradle Central Plugin Repository
```

The environment's network policy answers 403 for: `maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`,
`libraries.minecraft.net`, `resources.download.minecraft.net` (and `piston-data.mojang.com` will be needed too).
`services.gradle.org`, `repo.maven.apache.org` and `plugins.gradle.org` work.

Fix (user side): in the cloud environment's settings, change Network access to allow those hosts (or a broader
access level). Until then, no cloud session can verify that code compiles, so milestone 1 code was **not** written
this session (it couldn't be kept "green" if the build can't run).

## Milestones

| # | Milestone | Status | Notes |
| --- | --- | --- | --- |
| 0 | Project setup | In progress | Scaffold written; first clean `./gradlew build` still pending (blocked, see above). |
| 1 | Body | Not started (blocked) | Entity, player rendering with skins and poses, inventory, hunger model, virtual-key movement, camera model. Done when: you can drive it by command and it moves like a player. |
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
- Renamed to mod ID `drifter`, package `com.dinglebob.drifter`; entrypoints `Drifter` (common) and `client.DrifterClient` (client); empty mixin configs `drifter.mixins.json` and `drifter.client.mixins.json`.
- `CLAUDE.md`, this file, and `docs/TESTING.md`.
- GitHub Actions build workflow from the template (runs `./gradlew build` on push), set to JDK 21.

### Deviations from the template (please confirm)
- **Loom 1.17 instead of 1.18, Gradle 9.5.0 instead of 9.7.1.** The template's Loom 1.18 is compiled for Java 25, so Gradle would need a Java 25 JDK to even start. The cloud sandbox only has Java 21, and you asked for Java 21. Loom 1.17 is the newest version that runs on Java 21; Gradle 9.5.0 is the version Loom 1.17 itself builds with. If you'd rather follow the template exactly (and install JDK 25 locally), say so.
- License in `fabric.mod.json` set to `All-Rights-Reserved` and no LICENSE file added (the template ships CC0). Tell me if you want a different license.
- Mod version starts at `0.1.0`.

### Next
- Get one clean `./gradlew build` once the network policy allows the Fabric and Mojang hosts.

## Milestone 1: Body

### Done
- Nothing yet (blocked on the build).

### Planned order for the next session
1. `DrifterEntity` (extends `PathfinderMob`), registered with Fabric's entity registry; player-sized hitbox and eye height; attributes copied from `Player.createAttributes()` (health 20, movement speed, block/entity interaction range, etc.). Never despawns like a hostile mob.
2. Renderer in `src/client` reusing the vanilla `PlayerModel` and the vanilla player model layers (`ModelLayers.PLAYER` / `PLAYER_SLIM`) for wide and slim arms, per the EMF note in section 2. Armor layers, held items, arrow and bee-stinger layers; skin chosen from a small bundled pool and synced to the client.
3. Inventory: 36 slots (9 hotbar) plus 4 armor and offhand, mirrored to equipment slots for rendering; selected hotbar slot with a 1 to 3 tick switch delay; save/load with a version number; drop everything on death.
4. Hunger model mirroring the player's `FoodData` rules (food, saturation, exhaustion, regen, starvation by difficulty).
5. Virtual-key movement (forward, strafe, jump, sneak, sprint) driving the normal living-entity travel physics, with sprint blocked at food 6 or lower and sneak never falling off edges; sneaking/sprinting/swimming poses set exactly like a player's.
6. Camera model: head seeks with acceleration, overshoot and correction, idle jitter, body follows movement, body turns when still only past ~50 degrees.
7. Debug commands (`/drifter spawn`, `/drifter keys`, `/drifter look`, `/drifter give`, `/drifter food`, `/drifter info`) to drive it by hand.

## Design questions

Questions for the user. Nothing here has been changed in the design.

1. **Own spawn group (sections 18 and 22).** On 1.21.1, mob spawn groups are a hard-coded enum (`MobCategory`), and Fabric API has no way to add a new one. Options: (a) don't use vanilla natural spawning at all and run Drifter spawning from our own rare spawner (section 4 already describes a custom, rare, out-of-sight check every few minutes), which also keeps them out of the monster and creature caps; (b) add an enum entry via a mixin/enum-extension hack. I'd recommend (a). Not needed until natural spawning is built.
2. **Player sleep blocking (section 18).** Vanilla only blocks sleep for mobs of the `Monster` class. Making a hostile-to-you Drifter block sleep will need a small mixin on the bed check; fine on 1.21.1, just noting it needs a mixin rather than being free. No decision needed unless you object to mixins there.
