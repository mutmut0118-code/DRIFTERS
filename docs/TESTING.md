# Drifter: in-game testing checklist

All in-game testing happens on your own machine. Cloud sessions only check that `./gradlew build` compiles.
Each session updates this file with exactly what to check for everything built so far.

When something fails, note the checklist item, what you saw, and any error from the log (`run/logs/latest.log`),
and paste it into the next session.

## Setup (local)

1. JDK 21 installed and on `PATH` (`java -version` shows 21).
2. From the repo root: `./gradlew build` (Windows: `gradlew.bat build`). It should end with `BUILD SUCCESSFUL`.
   The mod jar is `build/libs/drifter-0.1.0.jar`.
3. Launch a dev client with `./gradlew runClient`, or copy the jar into a 1.21.1 Fabric instance's `mods` folder together with Fabric API.
4. Create a new **Creative** world with cheats on, difficulty **Normal**. A superflat world makes the movement tests easier.
5. Press F3 for coordinates, and F3+B to show hitboxes (handy for the pose tests).

## Command reference

Every `/drifter` command except `spawn` acts on the **Drifter nearest to you** (within 128 blocks).

| Command | What it does |
| --- | --- |
| `/drifter spawn` | Spawns a Drifter where you stand, facing your direction, with a random skin |
| `/drifter spawn <name>` | Same, with a nameplate |
| `/drifter spawn <name> wide` / `slim` | Same, forcing wide (Steve) or slim (Alex) arms |
| `/drifter keys <keys>` | Holds keys until changed. Keys: `forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint` (also `w a s d`), joined with `+`, e.g. `forward+sprint+jump`. `none` releases all |
| `/drifter keys <keys> <ticks>` | Holds keys for that many ticks (20 ticks = 1 s), then releases all |
| `/drifter look <yaw> <pitch> [idle\|normal\|combat]` | Turns the head to an absolute yaw and pitch at that speed band (default normal) |
| `/drifter lookat me [speed]` | Keeps looking at your eyes, following you |
| `/drifter lookat <x y z> [speed]` | Looks at a point |
| `/drifter lookat stop` | Stops tracking; the head holds still (with jitter) |
| `/drifter slot <0-8>` | Switches the held hotbar slot (with a 1 to 3 tick delay) |
| `/drifter give <item> [count]` | Puts items into its inventory |
| `/drifter food <food> [saturation]` | Sets food (0 to 20) and optionally saturation |
| `/drifter info` | Health, armor, food, saturation, exhaustion, pose, sprinting, speed, keys, camera, held item |
| `/drifter inventory` | Lists every non-empty slot |
| `/drifter remove` | Deletes it (no drops) |

Vanilla commands also work on it. To put armor or an offhand item on it:
`/item replace entity @e[type=drifter:drifter,sort=nearest,limit=1] armor.chest with minecraft:iron_chestplate`
(slots: `armor.head`, `armor.chest`, `armor.legs`, `armor.feet`, `weapon.offhand`, `weapon.mainhand`).

Yaw reference (same as your F3 "Facing"): 0 = south (+Z), 90 = west, 180 = north, -90 = east. Pitch: -90 = straight up, 90 = straight down.

## Milestone 0: project setup

- [ ] `./gradlew build` finishes with `BUILD SUCCESSFUL`.
- [ ] `./gradlew runClient` reaches the title screen without crashing.
- [ ] The game log contains the line `Drifter initialized`.

## Milestone 1: Body

Nothing below has been run in-game yet; this is the first test.

### 1. Spawning and rendering
- [ ] `/drifter spawn Alice slim` spawns a player-shaped figure facing the way you face, with a nameplate "Alice". Slim (3-pixel) arms.
- [ ] `/drifter spawn Bob wide` spawns one with wide (4-pixel) arms.
- [ ] `/drifter spawn` a few more times: you see a mix of the 9 vanilla default skins (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri). The chat message says which skin it got.
- [ ] Skin overlay layers (hat, jacket, sleeves, pants) render, and the skin isn't stretched or mis-mapped on either arm type.
- [ ] Size matches you: stand next to it; with F3+B, its hitbox is the same as yours (0.6 wide, 1.8 tall).
- [ ] It has a shadow, it's lit correctly, and it flashes red when hit.
- [ ] The Drifter Spawn Egg is in the creative Spawn Eggs tab and spawns one. `/summon drifter:drifter` also works.
- [ ] Armor renders correctly on **both** arm types: `/item replace entity @e[type=drifter:drifter,sort=nearest,limit=1] armor.chest with minecraft:diamond_chestplate` (and the other armor slots). `/drifter info` shows the armor points going up.
- [ ] Held items: `/drifter give minecraft:diamond_sword`, then `/drifter slot 0`. The sword shows in its right hand (or left, about 5% are left-handed). `/item replace entity @e[type=drifter:drifter,sort=nearest,limit=1] weapon.offhand with minecraft:shield` shows a shield in the other hand.
- [ ] A player head or carved pumpkin in `armor.head` renders on its head.
- [ ] **EMF test (section 2):** if you have Entity Model Features plus Fresh Animations and its Player Extension, does the Drifter get the player animations? Note yes, no, or partly. This decides how the renderer is built later, so please report it even if it's a no.

### 2. Movement (moves like a player)
Use `/drifter info` while it moves to read "horizontal speed last tick". Expected player values are from DESIGN.md section 3.
- [ ] `/drifter keys forward`: walks forward at about **4.3 blocks/s**. The legs animate like a walking player.
- [ ] `/drifter keys none`: it stops almost instantly, like a player letting go of W.
- [ ] `/drifter keys forward+sprint`: sprints at about **5.6 blocks/s**. Sprint particles kick up from its feet.
- [ ] Then `/drifter keys forward` (sprint key released): it **keeps sprinting**, exactly like a player who lets go of Ctrl but holds W. It stops sprinting when it walks into a wall or you release forward.
- [ ] `/drifter keys forward+sprint+jump`: sprint-jumps, averaging about **7.1 blocks/s** on flat ground.
- [ ] `/drifter keys forward+sneak`: crouch-walks at about **1.3 blocks/s**, crouched pose, and (if named) its nameplate stops showing through walls, like a sneaking player's.
- [ ] Sneak edge test: build a 1-wide platform 3+ blocks high, put the Drifter on it, `/drifter keys forward+sneak`. It must **stop at the edge and not fall**. Then `/drifter keys forward` and it walks off.
- [ ] Strafing: `/drifter keys forward+left` moves diagonally; `left` alone strafes sideways while the head keeps facing forward.
- [ ] `/drifter keys back`: walks backwards with the legs animating in reverse, head and body still facing forward.
- [ ] Step height: it walks up slabs and carpets without jumping, but a full block stops it until it jumps (`forward+jump` climbs it).
- [ ] Jump height: it can't jump onto a 2-block-high ledge, only 1.
- [ ] Timed keys: `/drifter keys forward 20` walks for 1 second (about 4 blocks) and stops.
- [ ] Crawl pose (the vanilla trapdoor trick): stand it in a 2-high space with an open trapdoor attached to the upper block, then close the trapdoor on it. It drops into the crawling pose. With `/drifter keys forward` it crawls through a 1-high tunnel and stands up again where there's room.
- [ ] Water: walk it into deep water with `forward+sprint`. Underwater it switches to the swimming pose and swims; with `/drifter look 0 60` (looking down) it swims downward, `/drifter look 0 -60` swims up. Swim and splash sounds are the player ones.
- [ ] Fall damage like a player: push it off a 10-block drop. It takes 7 HP (1 per block beyond 3) with the player's fall sound.

### 3. Camera model (head turns like a mouse)
- [ ] `/drifter lookat me`, then walk around it. The head follows you smoothly, accelerating into turns and slowing at the end, never snapping instantly.
- [ ] Body vs head: while it's standing still and you walk slowly around it, the head turns first and the **body only turns once the head is more than about 50 degrees off** (then the body is pulled along). While it walks, the body faces the walking direction.
- [ ] Speed bands: compare `/drifter look 180 0 idle`, `/drifter look 0 0 normal`, `/drifter look 180 0 combat`. Idle is a slow pan (2 to 6 s for 180 degrees), normal takes about half a second to a second, combat is a near-instant flick.
- [ ] **Overshoot:** on a big normal or combat turn (`/drifter look 90 0 combat`, then `/drifter look -90 0 combat`), the head swings **slightly past** the target (1 to 5 degrees) and then snaps back. Watching from the side or above makes it easier to see. Small turns (under 15 degrees) and idle turns don't overshoot.
- [ ] Pitch: `/drifter look 0 -60` looks up, `/drifter look 0 80` looks nearly straight down.
- [ ] Idle jitter: with `/drifter lookat stop` and standing still, the head is *almost* still. Expect at most an occasional tiny twitch (see design question 3 in PROGRESS.md: clients only see rotation in 1.4-degree steps, so the 0.4-degree jitter is mostly invisible). Tell me if it looks frozen, and which option you prefer.
- [ ] Chase test: `/drifter lookat me` plus `/drifter keys forward+sprint`, then run around. It should chase you like a player steering with the mouse: running in arcs, overshooting turns a little.

### 4. Hunger model
- [ ] Starting values: `/drifter info` shows food 20, saturation 5.0, exhaustion 0.
- [ ] Walking costs nothing: `/drifter keys forward` for a while; exhaustion stays at 0.
- [ ] Sprinting costs 0.1 exhaustion per block: `/drifter keys forward+sprint` across about 40 blocks; exhaustion rises by about 4, and saturation drops by 1 each time exhaustion passes 4.
- [ ] Jumping costs 0.05 (0.2 while sprinting): `/drifter keys jump` for a few seconds; exhaustion rises slowly.
- [ ] Sprint lock: `/drifter food 6`, then `/drifter keys forward+sprint`. It **walks, does not sprint**. `/drifter food 7` and it can sprint again.
- [ ] Slow regen: `/drifter food 18 0`, then hit it a few times. It heals 1 HP about every 4 s, and exhaustion jumps by 6 each time it heals.
- [ ] Fast regen: `/drifter food 20 20`, then hurt it. It heals quickly (every 0.5 s) while spending saturation.
- [ ] No regen below 18: `/drifter food 17 0`, hurt it, and health stays put.
- [ ] Starvation on Normal: `/drifter food 0 0`. It loses 1 HP every 4 s down to **1 HP and no lower**. (On Easy it stops at 10 HP; on Hard it starves to death.)
- [ ] Peaceful: switch to Peaceful with `/difficulty peaceful`. Food and health refill over time.

### 5. Inventory
- [ ] `/drifter give minecraft:oak_log 100`, then `/drifter inventory`. You see 64 in hotbar 0 and 36 in hotbar 1.
- [ ] `/drifter give minecraft:cobblestone 64` adds to the next free slot. Non-stackables (tools) take one slot each.
- [ ] Hotbar switch delay: give it a sword and a pickaxe, then `/drifter slot 1`. The held item changes a moment (1 to 3 ticks) later. `/drifter info` right after the command may show "(switching)".
- [ ] Pickup: drop (Q) a stack of something next to it. After the normal 2-second throw delay it picks it up, with the item flying into it. `/drifter inventory` shows it.
- [ ] Pickup of all item types: dirt, food, tools and armor are all picked up into the inventory, not equipped.
- [ ] Death drops: give it a mix of items and armor, then kill it (`/kill @e[type=drifter:drifter]`). **Everything** drops, scattered around like a player's death drop, with the player death sound.
- [ ] Curse of Vanishing: `/item replace entity @e[type=drifter:drifter,sort=nearest,limit=1] armor.head with minecraft:diamond_helmet[enchantments={levels:{"minecraft:vanishing_curse":1}}]`, then kill it. The helmet is destroyed, not dropped.
- [ ] Full inventory: `/drifter give minecraft:dirt 2304` fills it; dropped items then stay on the ground.

### 6. Saving and persistence
- [ ] Give it a name, a skin, items, armor and a food level (`/drifter food 12 3`). Save and quit, reopen the world: same skin and arm type, same name, same inventory and armor, same held slot, same food and saturation.
- [ ] Fly 200+ blocks away (out of render distance) and come back. It's still there; it didn't despawn.
- [ ] A lead can't be attached to it.

### Known gaps (don't report these as bugs)
- It does nothing on its own yet: no AI, no attacking, no eating, mining or placing (milestones 2+).
- Zombies and other hostiles ignore it (milestone 6).
- No nameplate unless it was given a name; no username generator yet.
- No XP drop and no death message when it dies.
