# Drifter: in-game testing checklist

All in-game testing happens on your own machine. Cloud sessions only check that `./gradlew build` compiles.
Each session updates this file with exactly what to check for everything built so far.

When something fails, write down which checklist item it was and what you saw (screenshots help). If the game
crashes, copy the crash report the launcher shows. Paste it all into the next session.

## Getting the latest version (no coding needed)

**Local sessions (since session 2):** Claude builds the mod on your computer and copies the new jar straight into the
Modrinth profile **DRIFTER TEST** (`C:\Users\mutmu\AppData\Roaming\ModrinthApp\profiles\DRIFTER TEST\mods`), replacing
the old `drifter-*.jar`. Just close the game if it's open and launch that profile. Claude also smoke-tests each build on
a headless test server first (spawning Drifters and running commands), so crashes should be caught before you play.

**From GitHub instead:**

1. Open the repository's **Actions** tab on GitHub and click the newest **build** run for the branch (green tick).
   Every push builds the mod there automatically.
2. At the bottom, under **Artifacts**, download **Artifacts**. Unzip it and take **`drifter-0.1.0.jar`**
   (not the one with "sources" in its name).
3. In the Modrinth App, open your Fabric 1.21.1 instance (with Fabric API installed), remove the old
   `drifter-0.1.0.jar` from its `mods` folder, and put the new one in.
4. Launch, and make a new **Creative** world with **Allow Commands: ON**, difficulty **Normal**.
   Press F3 for coordinates, and F3+B to show hitboxes.

(Developers can instead run `./gradlew build` with JDK 21, or `./gradlew runClient`.)

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
| `/drifter brain` | What its brain is doing: stage, next target, goal, scores, plan, needs |
| `/drifter brain on` / `off` | Lets it play on its own, or stops it (any other task command also switches the brain off) |
| `/drifter brain goal <fight\|flee\|equip\|eat\|food\|cook\|collect\|shelter\|progress\|explore\|idle>` | Makes it work on that goal now |

Vanilla commands also work on it. To put armor or an offhand item on it:
`/item replace entity @e[type=drifter:drifter,sort=nearest,limit=1] armor.chest with minecraft:iron_chestplate`
(slots: `armor.head`, `armor.chest`, `armor.legs`, `armor.feet`, `weapon.offhand`, `weapon.mainhand`).

Yaw reference (same as your F3 "Facing"): 0 = south (+Z), 90 = west, 180 = north, -90 = east. Pitch: -90 = straight up, 90 = straight down.

## Milestone 0: project setup

- [ ] `./gradlew build` finishes with `BUILD SUCCESSFUL`.
- [ ] `./gradlew runClient` reaches the title screen without crashing.
- [ ] The game log contains the line `Drifter initialized`.

## Milestone 1: Body

Passed: the user ran this milestone in-game and reported that everything works. Re-run it after big changes to the body, movement or rendering.

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
- It does nothing on its own yet: no AI, and no attacking (milestones 5 and 6). Milestone 2 actions only happen when you command them.
- Zombies and other hostiles ignore it (milestone 6).
- No nameplate unless it was given a name; no username generator yet.
- No XP drop and no death message when it dies.

## Milestone 2: Hands

Nothing below has been run in-game yet; this is the first test.

### How these commands work
- Each command below gives the **nearest Drifter** one job. When the job ends, chat says **"Done: ..."** or
  **"Failed: ... (reason)"**. A new command replaces the current job. `/drifter stop` cancels it.
- Positions are block coordinates, like `10 64 -3`. The easiest way: look at a block, and when you type the
  command, press **Tab**; the game fills in the coordinates of the block you're looking at.
- Items can be a single item (`oak_planks`) or a tag with `#` (`#logs`, `#planks`).
- Stand back and watch it: it should **turn its head to the block first**, then act. It can only touch blocks
  within about 4.5 blocks that it can actually see.
- Tip: `/drifter keys none` and `/drifter lookat stop` if it's still walking or staring from an earlier test.

| Command | What it does |
| --- | --- |
| `/drifter mine <pos>` | Breaks that block with whatever it's holding |
| `/drifter place <pos>` | Places its held block at that spot |
| `/drifter place <pos> <item>` | Takes that block out (from anywhere in its inventory) and places it |
| `/drifter useon <pos>` | Right-clicks the block with its held item (hoe, bone meal, flint and steel...) |
| `/drifter use` | Right-clicks with its held item in the air (eats if it's food) |
| `/drifter eat` | Takes out food and eats it |
| `/drifter craft <item> [times]` | Crafts something (needs a crafting table in reach for 3x3 recipes) |
| `/drifter container <pos> put <item> [count]` / `take <item> [count]` | Moves items into or out of a chest, barrel, furnace... |
| `/drifter container <pos> putall` / `takeall` | Moves everything |
| `/drifter smelt <furnace pos> <input> <count> <fuel>` | Loads a furnace, waits, takes the results |
| `/drifter wear` | Puts on the best armor it carries |
| `/drifter swaphands` | Swaps main hand and offhand (the F key) |
| `/drifter walkto <pos>` | Walks there in a straight line (no pathfinding yet) |
| `/drifter collect` | Walks over nearby dropped items to pick them up |
| `/drifter script woodtostone` | The big test: from punching trees to stone tools, on its own |
| `/drifter stop` / `/drifter task` | Cancel the current job / show what it's doing |

### 1. Mining
- [ ] Spawn a Drifter next to a tree. `/drifter mine <pos of a log>`: it looks at the log, swings its arm
      continuously, the **crack pattern** grows on the block, and after about **3 seconds** (bare hands) the log
      breaks and drops. It picks the log up if it's close enough.
- [ ] Give it an axe (`/drifter give minecraft:stone_axe`, then `/drifter slot 0`) and mine another log: it's
      much faster. `/drifter info` or `/drifter inventory` shows the axe lost a little durability.
- [ ] Mine dirt or grass with bare hands: fast. Mine stone with bare hands: very slow, and it **drops nothing**
      (like a player without a pickaxe). Mine stone with a pickaxe: cobblestone drops.
- [ ] Tall grass or flowers break instantly.
- [ ] Out of reach: `/drifter mine` on a block 8 blocks away fails with "can't see or reach it".
- [ ] Through a wall: put a block between it and the target. It fails ("can't see or reach it") instead of
      mining through the wall.
- [ ] Bedrock: fails with "it can't be broken".

### 2. Placing and using
- [ ] `/drifter give minecraft:cobblestone 16`. `/drifter place <empty spot next to it>`: it takes out the
      cobblestone, looks at a neighbouring block's side, swings, and the block appears with the normal place sound.
- [ ] Mid-air: `/drifter place` on a spot with no blocks around it fails ("no block face to place against").
- [ ] Stairs or logs placed by it face the way a player looking from its position would place them.
- [ ] Place against a chest or crafting table: it **crouches briefly** while placing (like a player) instead of
      opening the chest.
- [ ] `/drifter give minecraft:wooden_hoe`, select it with `/drifter slot`, `/drifter useon <grass block>`:
      it turns into farmland.
- [ ] `/drifter give minecraft:bone_meal 3`, select it, `/drifter useon <sapling>`: bone meal particles.

### 3. Crafting
- [ ] `/drifter give minecraft:oak_log 2`, then `/drifter craft #planks 2`. It **stands still with its head tilted
      down for half a second to two seconds** (inventory open), then you get "Done". `/drifter inventory` shows 8 planks.
- [ ] `/drifter craft minecraft:crafting_table`: works without a table (it's a 2x2 recipe).
- [ ] `/drifter craft minecraft:wooden_pickaxe` with no crafting table nearby: fails with "needs a crafting table
      within reach". Place a crafting table next to it and try again: it **looks at the table first**, pauses,
      then crafts.
- [ ] Missing ingredients: `/drifter craft minecraft:diamond_pickaxe` fails with "doesn't have the ingredients".

### 4. Containers and smelting
- [ ] Place a chest next to it. `/drifter container <chest pos> putall`: it looks at the chest, the **lid opens**
      with the chest sound, items move over a few seconds, then the lid closes. Open the chest yourself to check.
- [ ] `/drifter container <chest pos> take #planks 4` takes 4 planks back out.
- [ ] Double chests work. A chest with a solid block on top fails ("it won't open").
- [ ] Barrels open and close (the barrel lid texture changes).
- [ ] Furnace: `/drifter give minecraft:raw_iron 3` and `/drifter give minecraft:coal 1`, place a furnace next to
      it, then `/drifter smelt <furnace pos> minecraft:raw_iron 3 minecraft:coal`. It puts the iron and one coal
      in, the furnace lights, it **waits there** about 30 seconds, then takes out 3 iron ingots.
- [ ] Ender chests are refused ("it won't open").

### 5. Eating
- [ ] `/drifter food 10`, `/drifter give minecraft:bread 3`, then `/drifter eat`. It takes out bread, eats for
      **1.6 seconds** with the eating animation, crumbs and sounds, then burps. `/drifter info` shows food 15.
- [ ] While eating, `/drifter keys forward`: it moves **very slowly** (like a player eating while walking).
- [ ] Full: `/drifter food 20`, `/drifter eat` fails with "it isn't hungry". A golden apple still works when full.
- [ ] Mushroom stew leaves an empty bowl in its inventory.

### 6. Equipment
- [ ] `/drifter give minecraft:iron_helmet` and `/drifter give minecraft:iron_chestplate`, then `/drifter wear`.
      After a short pause it puts them on with the armor sound. Give it a diamond helmet and `/drifter wear` again:
      it swaps the iron one for the diamond one.
- [ ] Item deep in its inventory: fill its hotbar with 9 stacks of dirt (`/drifter give minecraft:dirt 576`), then
      `/drifter give minecraft:cobblestone 4` (this lands in its backpack, not the hotbar). Now
      `/drifter place <empty spot next to it> minecraft:cobblestone`: it **stands still briefly** ("inventory open")
      to move the cobblestone to its hotbar, then places it.
- [ ] `/drifter give minecraft:shield`, select it, `/drifter swaphands`: the shield moves to its other hand.

### 7. The big test: wood to stone tools
Set up: open, fairly flat ground with **a tree within about 20 blocks** and **some stone or cobblestone visible at the
surface** (a hillside, or place 8+ stone blocks on the ground yourself). The Drifter should start with an empty inventory.
- [ ] `/drifter spawn Tester`, then `/drifter script woodtostone`. Chat shows each step. Watch it:
  1. walk to the tree and **punch logs** (bottom first), picking up the drops, until it has 4;
  2. pause and craft **planks**, then a **crafting table**, and **place the table** next to itself;
  3. craft **sticks** and, looking at the table, a **wooden pickaxe**;
  4. take out the pickaxe, walk to the stone and **mine 8 stone**, picking up the cobblestone;
  5. walk back to the table and craft a **stone pickaxe, axe, sword and shovel**.
- [ ] Chat ends with "Wood to stone complete". `/drifter inventory` shows the four stone tools.
- [ ] If it fails, chat says which step and why. Copy that message for me.

### Known gaps for milestone 2 (don't report these as bugs)
- It walks in **straight lines only** (no pathfinding until milestone 4): it gets stuck behind walls, can't cross
  water or gaps, and gives up on things it can't simply walk to.
- The wood-to-stone script "knows" where trees and stone are by scanning the world. That's a test shortcut;
  real eyesight and memory are milestone 3.
- It can break blocks that players placed if you tell it to (protection for player builds comes later).
- No bows, shields-up, or attacking yet (milestone 6). No XP collection yet.

## Milestone 3: Senses and memory

Passed: the user fully tested this milestone. Only the **glass** item below needs a re-check (a bug fix came after the test).

**The goal of this milestone:** the debug overlay shows exactly what the Drifter knows, and nothing more.
It can only know things it has seen (in front of it, in the light, not through walls), heard, or felt.

### New commands
| Command | What it does |
| --- | --- |
| `/drifter debug on` / `off` | Shows the overlay for every Drifter within 64 blocks of you |
| `/drifter memory` | Lists what the nearest Drifter remembers (closest things first) |
| `/drifter memory clear` | Makes it forget everything (handy before a test) |
| `/drifter thoughts` | Its last 15 "thoughts": what it noticed, heard, felt |
| `/drifter senses` | What it's tracking, what it heard, what time it thinks it is |

### What the overlay shows
- **White lines from its head:** its field of view (about 110 degrees wide). Grey lines at its sides: the edge of
  its side vision, where it only notices movement. A yellow line: exactly where it's looking.
- **Faint blue lines:** the "eye rays" it uses to notice blocks. They turn **green** when they find something worth
  remembering.
- **Boxes around mobs and players it's tracking:** grey (barely noticed), **yellow** (glimpse), **orange**
  (suspicious), **red** (it knows what it is). The label shows "?" until it knows. Faded boxes with
  "(last seen)" are where it *thinks* something is after losing sight of it.
- **Coloured boxes on blocks it remembers:** cyan ores, green stations (crafting table, furnace, bed), brown storage
  (chests, barrels), red danger (lava), light green trees, pink structure clues (spawners, bookshelves). They fade as
  it becomes less sure. Walk close to read the labels.
- **Purple crosses:** sounds it heard, placed where it *thinks* they came from (deliberately a bit off).
- **Text over its head:** its job, **how aware it is of you**, what time it thinks it is, how much it remembers, and
  its latest thoughts.

### 1. Eyesight and the awareness meter
- [ ] `/drifter spawn Watcher`, `/drifter debug on`. You see the view lines from its head and the text panel.
- [ ] Daylight, walk into view in front of it about 10 blocks away: "Awareness of you" climbs quickly to 1.00 and
      it says it knows it's you. Your box goes yellow, orange, red. `/drifter thoughts` says "detected <your name>".
- [ ] Stand still far away (30+ blocks) at the edge of its view: awareness climbs **slowly**.
- [ ] **Sneak up from behind** (stay behind its back): awareness stays at none. It doesn't know you're there.
- [ ] Side vision: stand beside it (90 degrees to its side) and walk around: it only gets a **glimpse** (yellow),
      and its head **turns toward you** (when it's not busy with a job). Standing still beside it: nothing.
- [ ] Hide behind a wall: your box fades to "(last seen)" where you were, and the awareness drains.
- [ ] Darkness: at night (`/time set midnight`), far from torches, stand 20 blocks away: it can't see you. Hold a
      **torch**: now it can.
- [ ] Invisibility potion: it can't see you. Put on armor while invisible: it can, slowly.
- [ ] Glass: it can see you through glass.
- [ ] Mobs: let a zombie or cow walk in front of it: box, then "detected a zombie" in its thoughts.

### 2. Seeing and remembering blocks (no x-ray)
- [ ] `/drifter memory clear`. Place a **crafting table, chest and diamond ore** in front of it in the light: green,
      brown and cyan boxes appear within a second or two, and `/drifter memory` lists them.
- [ ] Place the same blocks **behind** it: **no boxes**. It hasn't seen them. Turn it around
      (`/drifter look 180 0`): the boxes appear.
- [ ] **No x-ray:** bury a diamond ore inside solid stone right next to it. No box. Then mine the stone next to it
      (give it a pickaxe, `/drifter mine <that stone>`): when the stone breaks, the ore gets a box (mining reveals
      neighbours).
- [ ] **It notices changes:** with it looking at a remembered crafting table, break the table: the box disappears and
      its thoughts say the table "is gone". Do the same while it's looking **away**: the box **stays** (it doesn't
      know yet) until it looks back.
- [ ] **Glass (re-check after the fix):** put a crafting table behind a glass wall in front of it, so it remembers it.
      Then break the table (leave the glass): the box disappears and its thoughts say the table "is gone".
      Place a diamond ore behind glass: it gets noticed through the glass.
- [ ] Chests: `/drifter container <chest pos> putall` (from milestone 2), then `/drifter memory`: the chest entry shows
      what's inside.
- [ ] Crafting uses memory: place a crafting table **behind** it, give it planks and sticks, `/drifter craft
      minecraft:wooden_pickaxe` fails "(one it has seen)". Then `/drifter lookat <table pos>` and try again: it works.
- [ ] Lava in view gets a red box.

### 3. Hearing and feeling
- [ ] Walk around **behind** it (not sneaking): purple crosses appear where you walk, and its thoughts say
      "heard something moving behind". Its head turns toward the sound (when idle).
- [ ] **Sneak** behind it: no sounds.
- [ ] Open a chest or door behind it: "heard a container or door".
- [ ] Put a wall of **wool** between you and it: it can't hear you through the wool.
- [ ] Light TNT about 40 blocks away: it hears "an explosion".
- [ ] Hit it (or shoot it with an arrow) from behind: its thoughts say "got hurt by an attack behind ...", and its
      head turns to look.

### 4. Sense of time and memory over time
- [ ] `/drifter senses` outdoors: it knows the exact time and the weather.
- [ ] Wall it in completely with stone (no sky above) for a few minutes, then `/drifter senses`: it says
      "(guessing)" and its time slowly drifts from the real time. Give it a **clock** (`/drifter give minecraft:clock`):
      it knows the exact time again.
- [ ] Dusk: `/time set 11900`, wait about 10 seconds: its thoughts say "thinking over the day". Old short-term
      memories (mobs, items) are cleared; important ones stay.
- [ ] Save and quit, reopen the world: `/drifter memory` still lists its long-term memories.

### Testing settings (new)
- `/drifter config` shows the settings. `/drifter config sight 12` sets how far they see (in chunks, 2 to 16, default
  10). `/drifter config path 96` sets how far one path search reaches (blocks, 24 to 160, default 64): longer paths in
  one go, a little more work per search. `/drifter config fov 90` sets their field of view like the game's FOV slider
  (30 to 110, default 70 = "Normal"; the view cone is then about 102 x 70 degrees, a player's view on a 16:9 screen).
  Saved in `config/drifter.json`, so they stay after a restart.
- [ ] **FOV (new):** with `/drifter debug on`, the view cone drawn from its head gets wider with `/drifter config fov 110`
      and narrower with `/drifter config fov 30`; with a narrow FOV it notices you much later when you come from the side.
- [ ] **Cone shape (re-test after fix):** the outline is a wide rectangle like your screen (16:9), not tall; when it
      looks up or down the rectangle tilts with its head and keeps its shape.
- `/drifter giveall minecraft:stone_sword` gives one to every Drifter (`/drifter giveall minecraft:bread 8` for more).
  Vanilla `@a`/`@p` only pick real players. Any `/drifter` command can also run for every Drifter with
  `/execute as @e[type=drifter:drifter] at @s run drifter <command>`.
- [ ] With 40 Drifters, try sight 16 and path 160 and see whether the game slows down; tell me where it starts to.

### Sharper senses (new, after the milestone 5 and 6 tests)
- [ ] **Far sight:** on open ground in daylight, `/drifter senses` (or the overlay) should list animals and mobs up
      to about 100 blocks away once it has looked their way. Small ones (chickens) only from closer.
- [ ] **Quick noticing:** walk a zombie (`/summon minecraft:zombie ~ ~ ~5`) into its view: the thought "detected a
      zombie" comes within about half a second, not several seconds.
- [ ] **Knowing by sound:** put a cow or pig behind a wall near it (within 16 blocks) and wait for it to moo or oink:
      the thought says "heard a cow ... about N blocks away" (it names the animal without seeing it). A zombie's groan
      or a skeleton's rattle does the same, and it turns to look.
- [ ] **Landmarks:** near a village or a mountain, look at its thoughts (`/drifter thoughts` or the overlay): "spotted a
      village ahead, about 90 blocks away" or "spotted a mountain ...". `/drifter memory` lists them as landmarks.
- [ ] **Looking for stone:** with no stone in sight but a cliff or mountain in the distance, it explores toward it.
- [ ] **Server speed:** with one or two Drifters around, the game shouldn't feel slower than before. If it does,
      tell me (and how many Drifters).

### Known gaps for milestone 3 (don't report these as bugs)
- It doesn't **act** on what it knows yet (no AI): it only glances. The brain is milestone 5.
- It can't hear mob groans or rattles yet (they don't make game "vibrations").
- The wood-to-stone script still uses its test shortcut to find trees and stone.

## Milestone 4: Pathfinder

**Status:** done. Bridging across a whole ravine works (sixth test), and the rest passed earlier. The "tunnelling
through a hill" check (section 4) wasn't reported separately: worth a quick look when convenient.

**The goal of this milestone:** it crosses a ravine and tunnels through a hill to reach a target, finding its own way.
Keep `/drifter debug on` for these tests: you'll see the **path** drawn from its feet (white = walk, light blue =
step up/down, **blue = bridge**, **cyan = pillar up**, green = climb, aqua = swim) and **red boxes on blocks it plans
to dig**. The text panel shows "Path: N steps left, X blocks to dig, Y to place".

### New commands
| Command | What it does |
| --- | --- |
| `/drifter goto <pos>` | Finds its own way to that block (press Tab while looking at a block to fill in the position) |
| `/drifter goto <pos> stroll` / `sneak` | Same, but walking / sneaking the whole way (default is `travel`: sprinting) |
| `/drifter goto me` | Comes to where you're standing now |
| `/drifter follow me` | Keeps following you until `/drifter stop` |

**Fairness note:** it can only plan with terrain it has **seen**. Ground it hasn't seen yet is a guess. So it will
sometimes head somewhere, look, and then change its plan ("replan"). That's on purpose: it doesn't know what's behind a
hill until it gets there. `/drifter thoughts` doesn't show replans yet, but you'll see the path line change.

### 1. Walking around things
- [ ] Open ground: `/drifter goto <a block 20 blocks away>`. It walks there, **looking ahead** (not at its feet),
      and sprints on the long straight part. Chat says "Done".
- [ ] Obstacles: put a wall of blocks between it and the target (with a way around). It walks **around** the wall.
- [ ] Steps: a 1-block step up on the way: it jumps up. A 2-block wall with no way around and no blocks to use:
      it digs through (see 4) or fails.
- [ ] Going down: a 2-3 block drop on the way: it steps down. A 10-block cliff: it doesn't jump off (unless there's
      water at the bottom).
- [ ] Doors: put the target inside a house with a **wooden door**: it opens the door and walks in.
- [ ] **Village (re-test after fix):** put it inside a village house and `/drifter goto` a spot inside another house.
      It should go **out through the door** and **in through the other door**, walking over carpet. It should **not**
      break glass, logs, planks or carpet. (If it hasn't seen the second house's door yet, it may walk up to the wall,
      look, and then change its route to the door: that's expected.)
- [ ] Ladders: target at the top of a ladder: it climbs the ladder.
- [ ] Water: target across a small pond: it swims across.
- [ ] `/drifter follow me` and walk/run around: it follows, catching up when you get away, and waits when it's close.
      `/drifter stop` ends it.
- [ ] It never walks into lava or fire.

- [ ] **Sprint-jumping (re-test after fix):** `/drifter goto <a block 40 blocks away across flat ground>` a few times.
      Most times (about 4 in 5) it **sprint-jumps the whole way, jumping again the instant it lands**, like a player
      holding space (not random single hops). Other times it just sprints. Gentle bends don't stop it; it stops
      jumping before sharp corners, under low ceilings, near cliff edges, and in the last few blocks, and doesn't turn
      back after jumping past a point.
- [ ] **No sprint-jumping indoors or in caves (new):** `/drifter goto` a spot across a big room in a house, or along a
      cave: it sprints but doesn't jump (ceiling within 4 blocks, or a narrow corridor).
- [ ] **Hills (re-test after fix):** `/drifter goto` a spot at the bottom of a hill or down some terraces about 30
      blocks away. It runs down without stopping before each step (it may glance down, and slow a little at a big drop).
      When it drops off a ledge it **keeps moving forward through the air** along the path, not straight down.
- [ ] **Drops (new):** stand it on a ledge 5 blocks above flat ground (with full health) and `/drifter goto` a spot on
      the ground below that would need a long walk round. It walks off the ledge and lands, losing about one heart. From
      6+ blocks up it walks round instead (unless there's water below). With low health
      (`/damage @e[type=drifter:drifter,limit=1,sort=nearest] 14`) it won't take the 5-block drop.
- [ ] **Doors (re-test after 2nd fix):** it **opens** the door standing up (no crouching up and down), then walks
      through. Also: `/drifter goto <a spot inside a house>` from about 20 blocks away, once with a
      wooden door and once with an empty 2-block-high doorway. A few blocks before the doorway it **stops jumping**,
      sprints or walks through, and never gets stuck bonking its head on the frame.
- [ ] `/drifter goto <pos> stroll`: it walks. `/drifter goto <pos> sneak`: it sneaks the whole way.
- [ ] Hungry: `/drifter food 6`, then `/drifter goto` somewhere far: it walks (it can't sprint).
- [ ] Ancient city (optional): near sculk sensors or shriekers it has seen, it sneaks and never sprints.

- [ ] **Swimming across a river (re-test after 2nd fix):** `/drifter goto` a spot on the far side of a river or lake.
      It swims at the surface with its head out, bobbing gently (**no big bounces out of the water, no sinking**), at
      about walking speed in water (roughly 2 blocks a second), and climbs out on the far bank. It shouldn't stop in
      the middle. Also try it running and jumping into the water from a bank: it should not start swimming face-down.

### 2. Bridging (crossing a ravine)
- [ ] Give it blocks: `/drifter give minecraft:cobblestone 64`. Find or dig a **ravine** or gap at least 3 blocks wide
      and too deep to climb, and `/drifter goto <a block on the other side>`. The path shows **blue** across the gap
      (if it hasn't seen the gap yet, the path may first go straight across; when it reaches the edge it looks down
      and plans again, and then it's blue). At the edge it looks down into the gap once, then **turns its back to the
      gap**, looks steeply down and **sneaks backward**: leaning out, it places a block against the side of the block
      it stands on, shuffles back onto it, and repeats until it's across, then turns round and walks on.
- [ ] **Re-test after fix (it fell in):** it should take the cobblestone out **a few blocks before the edge** (it stops
      for a moment, as if opening its inventory), not at the edge. At the edge it must be sneaking (not sprinting or
      jumping) and must never fall in. `/drifter inventory` shows where the cobblestone is.
- [ ] A gap only **one** block deep: it steps down into it and jumps out the other side instead of bridging.
- [ ] **Safety (re-test after fix):** near the edge of the ravine it should be **sneaking**, never running, and it must
      **never fall in**. Also try walking it along the edge of a cliff: it sneaks the whole way.
- [ ] Without blocks (`/drifter memory clear` doesn't matter; just make sure it has no dirt/cobblestone/planks):
      it walks down and around through the ravine if it can, or fails with "it knows no way there".
- [ ] It never bridges using valuable blocks (logs, ores, chests). Only dirt, cobblestone, stone-type blocks and planks.

### 3. Pillaring
- [ ] Give it cobblestone, and put the target on top of a tall pillar or cliff (5+ blocks up, no stairs). The path
      shows **cyan**: it looks straight down, jumps and places a block under itself, over and over.

### 4. Tunnelling through a hill (the big test)
- [ ] Give it a pickaxe (`/drifter give minecraft:iron_pickaxe`) and **no** building blocks. Since the fix it prefers
      walking around or climbing over things rather than digging (like a player), so give it a target it **has** to dig
      to: for example a spot **inside a closed stone box** made with a command (commands don't count as "your"
      blocks): stand a few blocks away and run `/fill ~5 ~ ~5 ~11 ~6 ~11 minecraft:stone hollow`, then
      `/drifter goto <a block inside the box>`. Or a target in a cave under a mountain. Red boxes show the blocks it
      will dig; it takes the pickaxe out, mines a 1x2 tunnel and walks in. Chat says "Done".
- [ ] Watch the overlay as it digs: when it breaks into the hill, the path may change as it learns what's inside.
- [ ] Gravel or sand in the tunnel: it digs again when it falls back down.

### 5. Respecting your builds
- [ ] Build a wall yourself (place the blocks by hand) and send it through: it **won't dig your blocks**; it goes
      around, or fails with "it knows no way there".
- [ ] A natural hill of the same block: it digs through that fine.
- [ ] It never digs chests, furnaces or other blocks with contents.

### 6. Milestone 2 script again
- [ ] `/drifter script woodtostone` still works, now walking with the pathfinder (it can go around things on the way to
      the tree and the stone).

### Known gaps for milestone 4 (don't report these as bugs)
- Paths over **slabs and stairs** may fail or replan a lot (partial blocks aren't modelled well yet).
- No parkour jumps, boats or horses yet.
- It doesn't close doors behind it yet.
- Very far targets (more than ~48 blocks) are reached in hops: it plans part of the way, walks, plans again.

## Milestone 5: Brain and day one

**Status:** first version, not tested yet. **The goal of this milestone:** a fresh Drifter with nothing in its
inventory gets through its first night on its own, on most seeds.

**Important:** the brain is now **on by default** for every Drifter, including old ones in your test world: they will
start playing on their own. Any task command (`/drifter goto`, `/drifter mine`, `/drifter script ...`) and
`/drifter stop` switch the brain off; `/drifter brain on` switches it back on. It does **not** fight mobs yet
(that's milestone 6), so test on **Peaceful** first, then on **Easy/Normal**.

Keep `/drifter debug on`: the text panel now shows its **stage**, **next target**, the **goal** it picked and what it's
doing right now, the **top scores**, the **plan** (the steps it worked out) and its **needs**. The last few lines are its
thoughts ("picked get a stone pickaxe 0.65 over ..."). `/drifter brain` prints the same in chat.

### 1. The first day (the main test)
1. Make a new world or find a fresh spot: a **forest edge** with some animals around, in the **morning**
   (`/time set 0`). Start on Peaceful: `/difficulty peaceful` (animals still spawn on Peaceful).
2. `/drifter spawn Tester` (it starts with an empty inventory, like the "Fresh" kit), then `/drifter debug on`.
3. Watch it. What it should do, roughly in this order (it may wander off to look at something, like a player):
   - [ ] **Looks around** (a slow turn), then walks to the **nearest tree** it saw. If it saw none, it explores until it
         does ("explore (looking for trees)").
   - [ ] **Punches logs** from the bottom up and picks them up. It gathers all the logs it needs in one go.
   - [ ] Makes **planks**, a **crafting table**, places it next to itself, makes **sticks** and a **wooden pickaxe**
         (short "inventory open" pauses).
   - [ ] Gets **stone** (re-test after change): mines stone it can see; if it hasn't seen any, it **explores** for a cave,
         a cliff or a hillside ("explore (looking for cobblestone (a cave or a cliff))") and mines there. Only after 3
         rounds of exploring without finding stone does it dig a **staircase down**.
   - [ ] Walks back to its crafting table (or makes a new one) and makes a **stone pickaxe**, **sword** and **axe**, then
         a **furnace** (8 cobblestone), places it, and collects a stack of cobblestone.
   - [ ] **Hunts (re-test after fix)**: chases a cow, pig, sheep or chicken it has seen. It **switches to its best
         weapon first** (the stone sword, not planks). With the sword it **jumps and hits on the way down** (critical
         hits: you'll see the crit particles, the little stars), one full-strength swing per jump, never spam-clicking.
         With only its fist it does plain hits a quarter-second apart (that's the vanilla fist cooldown). It picks up
         the meat. Then **cooks** it in its furnace (fuel: coal, charcoal, planks or logs).
   - [ ] **Smelting while doing other things (new):** when it puts something in a furnace, the thought line says
         "left N ... cooking; back in about X s" and it **walks off to do something else** (gather, hunt, explore)
         instead of standing there. When the batch should be done it comes back ("collect ... from the furnace") and
         takes the result. `/drifter inventory` then shows the cooked food or smelted items.
   - [ ] **Straighter walking (new):** on open ground it walks in a **straight line** toward where it's going, at any
         angle, instead of zig-zagging along the block grid. It still goes around trees, holes and water.
   - [ ] Makes **torches** (from coal it saw, or charcoal it smelts from logs).
   - [ ] **Eats** when its food drops to about 14 (cooked food first, the piece that fills without waste).
   - [ ] **Cooks before eating (new):** it doesn't eat raw meat while it could cook it. Test: `/drifter give minecraft:beef 4`
         and `/drifter give minecraft:coal 2` with a furnace it has seen nearby (or 8 cobblestone in its inventory). The
         goal line says "cook beef" fairly soon, even before it's hungry; it loads the furnace, goes off, comes back
         for the steak, and later eats steak. It eats raw meat only when its food is at 6 or below (the panel's food
         number) or when it has no way to cook at all.
   - [ ] **Smoker (new):** place a smoker near it (`/setblock <pos> minecraft:smoker`, and let it see it) and repeat:
         it uses the smoker for the meat (it says "back in about 20 s" for 4 meat instead of 40 s).
4. Check the plan line now and then: it should make sense ("gather oak log (to 3) > craft oak planks > craft crafting
   table > place a crafting table > ...").

### 2. The first night
- [ ] Around dusk (from about `11500` on its own clock; the panel shows what time it *thinks* it is), it stops what it's
      doing at the next safe moment and **digs 3 blocks straight down**, then **puts a block over its head**
      ("sealed in for the night"). To get there faster: `/time set 11000`.
- [ ] It stays down there all night. It can't see the sky down there, so **it doesn't notice `/time set`**: it climbs
      out when **it thinks** it's morning (its clock keeps running, a bit off). That's on purpose (section 5).
- [ ] In the morning it **digs out the cover block and climbs up** (pillaring with the dirt it dug), then carries on
      with its day.
- [ ] If it's standing on sand, gravel or water at dusk, it moves a few blocks and tries again there.
- [ ] **In the water at dusk (re-test after fix):** put it in a river or lake around `/time set 11500`. It says "getting
      out of the water for the night", swims out, walks **all the way onto land** (a couple of blocks from the water)
      and digs in there. It must not stand at the shore.
- [ ] **Walling in (new):** at dusk on a sandy beach or desert (`/drifter give minecraft:dirt 16` first), it can't dig
      down in sand, so after a few tries it says "...walling itself in instead" and puts blocks around itself (feet and
      head height on all four sides, one on top of a wall, then the roof). Then "walled in for the night". In the
      morning it breaks out through one wall.
- [ ] **Re-test after fix:** when it climbs out under a tree in the morning, it sees the sky through the leaves (the
      panel's time stops saying "[guessing]") and carries on with its day.
- [ ] **Hills (re-test after fix):** send it up a steep hill or mountain (`/drifter goto <a block near the top>`). At a
      2-block step it uses a 1-block step next to it instead of jumping against the wall.
- [ ] Repeat on **Easy or Normal**: the main test is that it's **alive the next morning** with food. Try a few different
      spots or seeds; it should survive most of them. (It won't fight back if a mob catches it in the open before dusk:
      that's milestone 6.)

### 3. Smaller checks
- [ ] `/drifter brain goal food` with animals nearby: it hunts and cooks. `/drifter brain goal shelter` in the
      afternoon: it digs in.
- [ ] Hunger: `/drifter food 8` while it has food: within a second or two it stops at a safe moment and **eats**.
- [ ] It never breaks **your** blocks or chests to get resources, and it only goes after blocks it has **seen** (a buried
      ore it never saw doesn't exist for it).
- [ ] `/drifter brain off`: it stops and stands still. `/drifter brain on`: it picks up where it left off.
- [ ] Save and quit, reload: its brain is still on (or off, if you switched it off).

### What to send me if something goes wrong
The **text panel** (a screenshot), especially the **Goal**, **Plan** and the thought lines at the bottom, plus what it was
doing. If it gets stuck repeating something, `/drifter brain` in chat gives the same lines.

### Known gaps for milestone 5 (don't report these as bugs)
- It doesn't fight back against mobs, flee, or react to lava or drowning yet (milestone 6).
- No bed or sleeping yet: the first night is spent in a 1x2 hole.
- Every Drifter behaves the same way for now (no personalities until milestone 7).
- It leaves crafting tables behind (that's the "messy player" default for now).
- No natural spawning yet: use `/drifter spawn`.

## Milestone 6: Combat and reflexes

**Status:** first version, not tested yet. **The goal of this milestone:** it survives **three nights in a row** on
its own, on Normal difficulty. Mobs now hunt it like they hunt you, it fights what it can beat (with real 1.9+ timing),
runs from what it can't, and has quick reflexes for lava, creepers, drowning and the like.

Keep `/drifter debug on`. The panel now also shows a **Threat** line, for example
`threat 1.3 vs power 2.2 (1.69): engage carefully [zombie]` (its danger estimate, its own strength, the ratio, and what it
decided), and a **Reflex** line when a reflex fires. Reflex thoughts start with "reflex:".

Give it gear quickly with commands if you don't want to wait for it: `/drifter give minecraft:stone_sword`,
`/drifter give minecraft:shield`, `/drifter give minecraft:iron_chestplate` (it puts armor and the shield on by itself
within a second or two: "put on better gear").

### 1. Mobs go after it
- [ ] On Normal at night (`/difficulty normal`, `/time set 14000`), zombies, skeletons, spiders and creepers come for it
      just like they come for you. Spiders leave it alone in daylight (like you).
- [ ] An enderman ignores it unless it hits the enderman.

### 2. Fighting (give it a stone sword)
- [ ] One **zombie**: it takes the sword out, jumps and crits, then **steps back** out of reach while its sword recharges,
      strafing sideways, and steps in again. With a shield it holds the shield up while backing off.
- [ ] A **skeleton** shooting from far away: it **zigzags** toward it (shield up while the skeleton draws, if it has one),
      then fights it up close.
- [ ] A **creeper**: it runs in, does **one sprint hit** (knocks it back), then backs off to 5+ blocks; repeat. If the
      creeper starts **hissing**, it sprints away immediately ("reflex: creeper hissing").
- [ ] **Too many** (3 zombies with only fists or low health): the Threat line says "retreat" or "flee" and it **turns and
      runs at once** (no standing still to think), swerving around drops, lava and walls.
- [ ] **Hills mid-fight (re-test):** fighting near a 2-block step, it goes around instead of jumping at the wall.
      If 3 melee mobs surround it and it has blocks, it **pillars 3 blocks up** and waits.
- [ ] If something keeps hitting it from close by while it's running, it turns and fights ("cornered").
- [ ] After a fight: it looks around, picks up the drops, and eats if it's hurt.

### 3. Reflexes (each should happen within about a quarter to half a second; sometimes a bit late on purpose)
- [ ] **Lava:** `/drifter goto` somewhere next to a lava pool, or let it mine into lava: when lava is right next to it, it
      **places a block** over it (if it has blocks) or backs away. Dropped **into** lava (`/tp` it in): it jumps out to the
      nearest solid block.
- [ ] **Drowning:** `/tp` it to the bottom of deep water: when its air gets low it **swims up** (or toward open water).
- [ ] **On fire** with water nearby: it runs into the water.
- [ ] **Lit TNT** near it: it runs away 8+ blocks.
- [ ] **Arrows:** a skeleton shooting at it: with a shield it raises it at the arrow, without one it **sidesteps**.
- [ ] **Falling sand/gravel:** drop sand above it: it steps aside; if buried, it digs out.
- [ ] **Cobweb:** walk it into a cobweb: it cuts it (fast with a sword).
- [ ] **Cactus:** when it touches a cactus it steps away. On **magma blocks** it sneaks.
- [ ] **Water-bucket clutch:** `/drifter give minecraft:water_bucket` (it must be in the hotbar), then `/tp` it 20 blocks
      up: just before landing it looks down, places water, lands in it, and scoops the water back up.

### 4. The big test: three nights
1. New spot, Normal difficulty, morning: `/drifter spawn Tester`, then leave it alone (keep the chunk loaded: stay
   within about 100 blocks, or watch from a hill).
2. It should do its day (wood, stone tools, furnace, food), dig in at dusk, climb out in the morning, and deal with
   whatever mobs are around in the morning and evening.
3. [ ] Still alive after **3 nights**. If it dies, send me the last thought lines and what killed it (the death message
   in chat says).

### 5. Combat-mod test pass (optional, when you have time)
- [ ] With **Simply Swords** installed: give it one of those swords (`/drifter give simplyswords:...`). It should pick
      it as its weapon if it does more damage per second than what it has, and fight with it normally.
- [ ] With **Better Combat** installed: it still fights normally (vanilla-style swings for now); nothing crashes.
- [ ] With **Combat Roll** installed: nothing breaks (it doesn't roll yet; see design question 9 in PROGRESS.md).

### Known gaps for milestone 6 (don't report these as bugs)
- No bows or crossbows yet; it always fights in melee.
- It doesn't use doorways, high ground or light to its advantage yet, and doesn't retreat home (no home until milestone 8).
- No golden apples, potions or ender pearls yet.
- Fighting styles now come from personality (milestone 7): some trade hits up close (brawlers), some keep their
  shield up (turtles), timid ones back off unless cornered.

## Milestone 7: Mannerisms, personality and mood

**Status:** first part written (session 2), smoke-tested on a test server (no crashes), not played yet.
**The goal of this milestone:** watching a short clip, people can't reliably tell it from a player.

Every Drifter now has a **personality**: an archetype (Casual, Newbie, Builder, Miner, Homebody, Speedrunner, Nomad,
Hoarder, Hermit, PvP Sweat, Bandit, Helper), 17 traits, a set of **habits** that never change, and a **mood** that comes
and goes. On top of whatever it's doing it now has little **tics** (glances, look-backs, jump-spam while waiting...).
Drifters from older worlds get a personality the first time they load.

Keep `/drifter debug on`: the panel has a new line like
`Casual, mood bored 45% (nothing to do) | brave 0.52 caution 0.48 skill 0.61 curious 0.55 | tic: glance`.

### New commands
| Command | What it does |
| --- | --- |
| `/drifter personality` | Its archetype, traits (with "now" values when its mood is pulling on them), and habits |
| `/drifter personality reroll` | A brand-new random personality (for testing) |
| `/drifter personality reroll <archetype>` | A new personality of that archetype, e.g. `newbie`, `pvp_sweat`, `homebody` |
| `/drifter mood` | Its mood and what's pushing it |
| `/drifter mood <mood>` | Forces a mood: `calm`, `anxious`, `panicked`, `confident`, `frustrated`, `bored`, `triumphant` (it fades by itself) |
| `/drifter tic <tic>` | Plays a tic now: `glance`, `look_back`, `sky_check`, `sweep`, `jump_spam`, `crouch_spam`, `spin`, `swing_air`, `hotbar_fidget`, `shield_raise`, `afk`, `rage` |
| `/drifter night` | Tonight's plan (stays in / goes out hunting / digs in then mines) and what it would pick |
| `/drifter night <shelter\|hunt\|mine>` | Forces tonight's plan (for testing) |

### Re-test after fix
- [ ] With Lithium installed (your DRIFTER TEST profile has it): when it mines stone or ore, the **pickaxe** shows in
      its hand, not the log or whatever it held before; switching items always shows the right one. Armor it puts on
      shows and protects it.

- [ ] **Swimming (re-test after fix, round 3):** across a lake or river it swims along the surface with its head
      out, bobbing a little (no big bounces), at about your normal swimming speed, and climbs out onto the bank
      without launching into the air or getting stuck against it. Dropped into deep water (`/tp` it), it comes up and
      swims to shore.

### 0. Usernames (new)
- [ ] Every new Drifter (spawn egg or `/drifter spawn`) has a name over its head like a player's (`ryan_17`,
      `cosmic_bard`, `xXSourashXx`...), visible from up to 64 blocks, dimmed through walls, and hidden through walls
      while it sneaks. `/drifter spawn <name>` still uses your name.
- [ ] Drifters from your older test worlds get a name the first time you load them.

### 1. Personalities are different (quick)
1. Spawn three: `/drifter spawn A`, `/drifter spawn B`, `/drifter spawn C`. Stand next to each and run
   `/drifter personality`.
2. - [ ] They have different archetypes, traits and habits. Habits make sense for the person (tidy ones tend to
     "pick up crafting tables", cautious ones eat earlier).
3. - [ ] Leave the world and come back: `/drifter personality` shows exactly the same as before.

### 2. Tics (one at a time, by command)
Stand in front of a Drifter (brain on is fine) and try each. Each should look like something a person at a keyboard does.
- [ ] `/drifter tic spin`: a quick full turn of the head and body.
- [ ] `/drifter tic sweep`: a slower 360 look around, a quarter at a time.
- [ ] `/drifter tic sky_check`: looks up at the sky for about a second.
- [ ] `/drifter tic look_back`: a glance over its shoulder, then back.
- [ ] `/drifter tic jump_spam`: a few bunny hops on the spot.
- [ ] `/drifter tic crouch_spam`: sneak-unsneak a few times quickly.
- [ ] `/drifter tic swing_air`: swings its arm at nothing a few times.
- [ ] `/drifter tic hotbar_fidget` (give it a few items first): the item in its hand flicks through a few slots and back.
- [ ] `/drifter tic shield_raise` (give it a shield: `/drifter give minecraft:shield`, it puts it in its offhand):
      raises the shield for a second.
- [ ] `/drifter tic afk`: freezes facing one way for 30 s or more (hitting it wakes it up).
- [ ] `/drifter tic rage`: spins fast twice, punches the air, crouch-spams.

### 3. Tics on their own (just watch it play for a few minutes)
- [ ] **Walking:** every few seconds it glances to the side (toward a sound or an animal if there is one) without
      losing its way; it still sprints and sprint-jumps.
- [ ] **No tics** while it fights, flees, swims, eats, bridges, pillars, digs or goes through a door.
- [ ] **At dusk** (`/time set 11500`) out in the open: it looks up at the sky once.
- [ ] **In its night shelter:** now and then jump-spam (only if there's head room), hotbar fiddling, and sometimes
      it goes AFK (stands still facing one way for half a minute).
- [ ] **After killing a mob:** sometimes it celebrates its own way (crouch-spam, a spin, swinging, or nothing:
      see "celebrates:" in `/drifter personality`).
- [ ] **After nearly dying** (health 3 hearts or less, then escaping): for about 30 s it keeps looking back over its shoulder.
- [ ] **Hotbar:** with `/drifter personality`, check "scrolls the hotbar" or "number keys". Scrollers visibly flick
      through the items in between when switching; number-keyers jump straight to the item. Clumsy ones (low skill)
      sometimes grab the wrong slot first and then correct.
- [ ] It doesn't look twitchy: at most one tic every 2 seconds, never the same one twice in a row (side glances aside).

### 4. Moods
- [ ] Hit it a few times: `/drifter mood` shows it **anxious**; nearly kill it while a mob is after it: **panicked**
      (it swings more wildly and misses more). It calms down after a minute or so.
- [ ] Let it kill a zombie: **triumphant** for a while.
- [ ] Leave it with nothing to do (`/drifter brain goal idle`, or sealed in at night): it gets **bored** after a
      couple of minutes and then explores more (in the day) or fidgets more.
- [ ] Mood changes appear in `/drifter thoughts` ("feeling anxious: got hurt", "calmed down").

### 5. Personality changes what it does
- [ ] Compare a cautious one (`/drifter personality reroll homebody`) with a reckless one (`reroll speedrunner`):
      the homebody heads for shelter clearly earlier in the evening (check "shelter" in `/drifter brain` from about
      `/time set 9500`).
- [ ] A timid one backs off from a single zombie it could beat; a brave or aggressive one goes after it.
- [ ] Brawlers (aggressive, not very skilled) stay in and trade hits; turtles (cautious, with a shield) keep the
      shield up while waiting to swing.
- [ ] Mining style (line "mining:" in `/drifter personality`): a **strip miner** digs down for stone soon after
      looking around once; a **caver and surface explorer** walks around looking for caves and cliffs for longer.

### 6. Night by personality (the user's milestone 7 notes)
Give it what it needs first: `/drifter give minecraft:stone_sword`, `/drifter give minecraft:stone_pickaxe`,
`/drifter give minecraft:cobblestone 32`, `/drifter give minecraft:torch 16`, `/drifter give minecraft:cooked_beef 8`.
Use `/drifter night` to see tonight's plan, or force one with `/drifter night hunt|mine|shelter`, then
`/time set 12000` (Normal difficulty).
- [ ] **Stays in:** digs in and waits for morning as before.
- [ ] **Goes out hunting:** at dusk it doesn't dig in; it prowls around near where it was ("hunt mobs in the dark"),
      fights mobs it can take and picks up their drops. Hurt (7 hearts or less) or hungry, it digs in where it is.
- [ ] **Digs in, then mines:** it digs in as usual, then digs a 1x2 tunnel sideways from the bottom of its hole
      ("time doesn't matter underground: mining ..."), turns a few times, mines coal/iron it uncovers, and puts a
      torch on the wall every 8 blocks, **always on the same side going in** (its "torches on the ..." habit), so the
      way back is on the other side. Breaking into a cave or water, it **blocks the opening** and turns. In the
      morning it walks back to the hole and climbs out.
- [ ] Use `/drifter night` on a few different Drifters on a normal evening: careful ones mostly "stay in", brave
      well-armed ones "go out hunting", patient hard-working ones "dig in, then mine".

## Milestone 8: Progression and building

**Status:** steps 1 to 8 of 9 written and tested on a test server (sped-up runs and set-up scenes), not played
yet. The goal of the whole milestone: left alone for 10 days, a Drifter reaches diamonds and builds a base you'd
recognize.

**Fastest way to see everything:** spawn one, give it the iron-age kit below, and let it play a few days with
`/tick rate 60` (3x speed). `/drifter brain` shows its stage, next target and level; `/drifter home` shows its home,
farm, pen, house and enchanting table.

### Step 1: home and storage
Quick setup: `/drifter spawn Homey`, then `/drifter give minecraft:stone_pickaxe`, `/drifter give minecraft:furnace`,
`/drifter give minecraft:oak_planks 24` (that's enough to count as "stone age" and to make a chest). Or let a fresh one
play until it has stone tools and a furnace.
- [ ] Within a minute or two it **settles**: the thought "settling at x y z (flat 80%, water nearby, trees...)", then
      it makes a chest and puts it down near that spot. `/drifter home` shows where home is and what's stored.
      On bumpy ground (mountains, forests) it may walk around looking first.
- [ ] **Storing:** give it a lot of stuff (`/drifter give minecraft:iron_ore 20`, `/drifter give minecraft:bone 10`,
      `/drifter give minecraft:cobblestone 200`). When it's nearly full, or near home with a load, or at the end of the
      day, it walks home, opens the chest and puts things in, keeping its tools, weapon, food, torches and a stack of
      blocks on it. When the chest fills up it makes and places a second one (not touching the first).
- [ ] **Junk:** fill it up with junk (`/drifter give minecraft:andesite 640`): it stops, looks down a little, and throws
      the junk out in front of it one stack at a time; it doesn't pick it back up.
- [ ] **Fetching:** things it needs that are in its chest it walks back for ("fetch 16 cobblestone from its chest").
- [ ] **Evening:** it heads home earlier the farther away it is ("heading home for the night") and digs in at home.
- [ ] Leave and rejoin the world: `/drifter home` shows the same home, chests and contents.
- [ ] (Re-test after fix) In a lake at dusk with no shore in sight, it swims back toward where it came from instead of
      giving up and nearly drowning.

### Step 2: the starter hut (new)
Same setup as step 1, plus `/drifter give minecraft:cobblestone 64` and `/drifter give minecraft:torch 8` so it doesn't
have to go mining first (without them it gathers the blocks itself).
- [ ] After settling, it builds a **5x5 hut** around its chest: walls 2 high first (standing inside, corners first),
      then it hops up one block in the middle and puts the roof on, climbs down and closes the last roof block over
      its head, then puts in a **door**, its furnace and a torch on the back wall. `/drifter hut` shows a map of it
      layer by layer (# block, D door, C chest, F furnace, T table, i torch, @ the Drifter) and "finished".
- [ ] Patient Drifters gather all the blocks first; impatient ones start with what they have and go for more.
- [ ] **At dusk** it walks home, goes inside and **shuts the door** ("in the hut for the night"). Mobs outside don't get
      it out (it only fights something that got in and hit it). In the morning it walks out.
- [ ] Break a hole in its hut: on a later day it **repairs** it ("repair the hut").
- [ ] **Villages (your question):** in a village it doesn't chop the log corners of houses or mine cobblestone
      foundations and paths for materials; it gets wood from trees and stone from natural stone, and walks around
      houses rather than through them.

### Step 3: iron age, mining trips (new)
Quick setup (skips the early game): `/drifter spawn Digger`, then give it `minecraft:stone_pickaxe`,
`minecraft:stone_sword`, `minecraft:furnace`, `minecraft:crafting_table`, `minecraft:chest`, `minecraft:oak_planks 32`,
`minecraft:cobblestone 100`, `minecraft:torch 32`, `minecraft:cooked_beef 16` and `minecraft:coal 16` (each with
`/drifter give`). `/drifter personality reroll miner` makes a keen miner. Set the time to morning (`/time set 1000`).
- [ ] It settles and builds its hut as in steps 1-2, then: "go mining for raw iron". If it remembers iron ore it saw,
      it goes there first. Otherwise it digs a **2-high staircase down** from just outside the hut (a different side
      each trip), down to about y 16 (in a mountain: about 24 blocks into it).
- [ ] At the bottom it digs a **branch mine**: one long tunnel with side tunnels every few blocks. Torches every 8
      blocks, always on the same side going in. It mines the ore it uncovers (coal, iron, copper...).
- [ ] Breaking into a cave or water, it **plugs the hole** with a block and digs a different way ("turning north on the
      way down (a cave)"). It never digs through dungeon walls (mossy cobblestone) or mineshaft planks.
- [ ] If it **sees iron** in a cave it broke into (or in a wall), it goes and mines it ("spotted raw iron at ...: going to
      get it"), then goes back to its tunnel. On cave-filled ground this is how it gets most of its iron.
- [ ] Before a trip it makes **spare stone pickaxes** if its pickaxes won't last the dig (a stone pickaxe lasts 131
      blocks). Give it a nearly broken one to see: `/drifter give minecraft:stone_pickaxe[damage=120]`.
- [ ] It uses the crafting table and furnace in its hut, even after mining a long way off (it doesn't make new ones).
- [ ] On grassy ground, building the hut and digging the stairs work (it clicks grass to put a block there, like you).
- [ ] It heads home when it has enough, its pockets are full, it's getting late, it's hurt or hungry, or its pickaxe
      is nearly worn out (it makes a spare first if it has the stuff). At home it smelts the iron and crafts.
- [ ] Over a few days: **iron pickaxe**, then **bucket**, **shield** and iron armor pieces. `/drifter status` shows the
      stage ("iron age" once it has the pickaxe, bucket, shield and two armor pieces).
- [ ] Next trip it walks back down its old staircase instead of digging a new one from the top (`/drifter home` shows
      "mine" and how many trips).
- [ ] (Re-test after fix) When it pillars up, it first shuffles to the middle of the block; if a jump is blocked anyway,
      it goes another way instead of trying the same spot for minutes.
- [ ] (Re-test after fix) Break 2-3 roof blocks of a finished hut (corners and edges): next day it repairs them from
      its pillar, knocking out a roof block in the way and putting it back, without building and breaking the pillar
      over and over. The middle of the roof is never left open.
- [ ] (Re-test after fix) Night mining doesn't walk it off into a cave: a hole in the tunnel floor gets a block, or it
      turns.
- [ ] (Re-test after fix) At night, away from home, with mobs about (`/time set 14000`, then `/summon zombie` a few):
      with the mobs on the far side it runs home ("running home") and shuts itself in the hut; with the mobs between it
      and home it says "the mobs are between it and home" and digs in where it gets clear, instead of dithering.
- Known: going back down an old mine it can get lost for a long time (caving, next, replaces most of those trips).
- [ ] **Caving (new):** a Drifter whose `/drifter personality` habits say "caver and surface explorer" or "mixed" goes
      caving instead of digging stairs when it knows a cave near home ("going caving: a cave it saw at ..."), or when its
      stairs break into a cave ("broke into a cave: exploring it"). It walks deeper, places torches on its habit side as
      it goes, mines ore it spots, and comes back out when it has enough, is hurt, hungry, out of torches, or it's late.
      `/drifter personality reroll speedrunner` makes a brave caver; `reroll miner` usually makes a strip miner (who
      still plugs caves).
- [ ] **Looting (new):** in a dungeon (mossy cobblestone room with a spawner) it breaks the spawner and takes what's
      useful from the chests; in a mineshaft it loots the chest minecarts ("looting a chest minecart"). It never loots
      its own chests or chests above ground.
- [ ] **Flooding:** if water gets into its tunnel, it swims back to where it last had air instead of drowning; after a
      scare it gets out of the water ("nearly drowned: getting out of the water") and doesn't dive straight back in.
      Drop sand on its head in a tunnel: it digs itself out.
- [ ] **Water:** a brave Drifter jumps into a pool from high up (water stops fall damage); a careful one finds another
      way down. Under water with something overhead, it heads for air and doesn't plan long swims under water.
- Handy for long watches: `/drifter thoughts log true` writes every thought into the game log as it happens
  (`/drifter thoughts log false` to stop). `/tick rate 60` makes the game run 3x faster to see a few days quicker.

### Step 4: beds and sleeping (new)
Set up as for step 3, and have a few sheep of one colour nearby (`/summon sheep ~5 ~ ~5 {Color:0}` three times for
white).
- [ ] Once its hut is built and it has seen three sheep of one colour, it hunts those ("hunt for white wool"), crafts a
      bed of that colour and puts it in the hut along the right-hand wall (looking in through the door), head to the
      back.
- [ ] Without sheep around it doesn't go looking for them for days; it gets on with iron and makes the bed later.
- [ ] At night in the hut it goes to bed ("going to sleep") and you see it lying in the bed. Careful/homebody
      Drifters stay in bed until morning ("woke up: morning"); others get up after a few seconds ("got up after a
      rest"). With a monster close by it says "can't sleep: monsters nearby". Hit it: it wakes up.

- [ ] **Village (new):** spawn a Casual Drifter in a village (`/drifter personality reroll casual`): it says "a
      village: looking round the houses for a bed", walks around, steps into houses ("looking inside a house"), and if
      it finds a bed it moves in ("moving into the village house ..."): chest by the bed, no hut, sleeps there at night.
      It never breaks the houses. (It doesn't always spot a door; then it builds a hut nearby.)
- [ ] **Your bug reports (re-test):** in snow it pillars, places and digs normally; next to a tree it cuts the tree down
      before building the hut; it finishes ore veins and picks up the ore that drops; a strip miner steps into a cave its
      tunnel opens and looks around before carrying on.

### Step 5: farm, pen and tidy chests (new)
Set up as for step 3, plus `/drifter give minecraft:bucket` (the farm needs one) and `/drifter give minecraft:wheat_seeds 16`.
- [ ] Once it has a bucket it starts a **wheat farm** near home ("starting a wheat farm at ..."): a 9x9 patch, the
      middle dug out and filled with water from its bucket, the rest tilled with a hoe and planted. Later it harvests
      ripe wheat and replants. `/drifter home` shows "farm at ...: N farmland, N planted, N ripe, water yes".
- [ ] With wheat or seeds on it, it builds a small **fenced pen** with a gate, leads two animals of a kind in (they
      follow it while it holds wheat), feeds them so they breed, and shuts the gate.
- [ ] Putting things away, it puts each thing into the chest that already has that kind in it.
- [ ] **Farms by personality:** Nomads, Speedrunners, Bandits and PvP Sweats never make a farm or pen; Miners and
      Hermits only when they're short of food; Homebodies and Builders get to it first. (`/drifter personality reroll
      homebody` to see a keen farmer.) Without a bucket, a farm goes by a pond or river near home.
- [ ] (Re-test after fix) In water under a ceiling (a flooded cave) it finds its way to air instead of pushing against
      a wall; it doesn't plan long swims under water.
- [ ] (Re-test after fix) **Sleeping:** in bed at night, it gets up in the morning (a bug kept it in bed for days).

### Step 6: diamonds (new)
**Iron-age kit** (skips days of play): `/drifter spawn Digger`, `/drifter personality reroll miner`, then
`/drifter give` each of: `minecraft:iron_pickaxe`, `minecraft:stone_pickaxe`, `minecraft:iron_sword`,
`minecraft:bucket`, `minecraft:shield`, `minecraft:iron_chestplate`, `minecraft:iron_leggings`, `minecraft:iron_helmet`,
`minecraft:iron_boots`, `minecraft:shears`, `minecraft:flint_and_steel`, `minecraft:white_bed`, `minecraft:furnace`,
`minecraft:crafting_table`, `minecraft:chest`, `minecraft:oak_planks 32`, `minecraft:cobblestone 100`,
`minecraft:torch 32`, `minecraft:cooked_beef 16`, `minecraft:coal 16`.
- [ ] `/drifter brain` says "next target: a diamond pickaxe". It goes down its staircase (or digs one) toward about
      Y -51 ("digging down toward Y -51 for diamond"), branch mines there and goes for diamond ore it spots.
- [ ] Digging plain stone and deepslate it uses its **stone pickaxe** and saves the iron one for ore. Before a deep trip
      it takes a crafting table and sticks along ("taking sticks along, to make a pickaxe down there if one breaks").
- [ ] With 3 diamonds it comes home and crafts a **diamond pickaxe**, then works toward a sword. Stage 7 "diamond" in
      `/drifter brain` once it has both.

### Step 7: enchanting (new)
Quick check of the enchanting itself: with the kit above, also give `minecraft:diamond_pickaxe`,
`minecraft:diamond_sword`, `minecraft:enchanting_table` and `minecraft:lapis_lazuli 9`, then `/drifter xp 30`.
- [ ] It sets the table down near home on flat ground ("set up its enchanting table at ..."), walks up to it, pauses
      as if reading the offers, and enchants its pickaxe, then its sword ("enchanted its diamond pickaxe (a level 8
      offer...): Efficiency I, Unbreaking I"). Levels and lapis go down. Stage 8 "enchanting".
- [ ] **Obsidian:** instead of the table, give it `minecraft:diamond 2`, `minecraft:book` and a second
      `minecraft:bucket`, and make a small lava pool nearby (`/fill ~5 ~-1 ~5 ~7 ~-1 ~7 minecraft:lava`) with water
      somewhere close. It fills a bucket with water, scoops up lava (standing back from it), digs a one-block hole,
      pours the lava in and water next to it, scoops the water back, mines the obsidian and picks it up. Four times,
      then it crafts the enchanting table.
- [ ] **Bookshelves:** give it `minecraft:bookshelf 6` while it has a table: it puts them in a ring two blocks out
      from the table (a gap left to walk in). `/drifter home` shows "enchanting table at ... (N bookshelves)".
- [ ] Experience: it picks up experience orbs it walks over (mining coal, killing mobs, taking smelted iron out of the
      furnace); `/drifter brain` shows "level N".

### Step 8: houses (new)
Use a **Builder** (`/drifter personality reroll builder`) with the step 3 setup, plus `minecraft:oak_planks 192`,
`minecraft:oak_log 32`, `minecraft:glass_pane 8` and a bed. (Other Drifters build theirs once they reach the iron age;
Miners build in stone; Nomads, Hermits and Speedrunners don't build one.)
- [ ] After the hut, "building a wood house at ..., door on the ... side": a 7x7 house 9-16 blocks from the hut,
      walls 3 high with log corner posts, a window in the middle of three walls (glass panes in them), a flat roof
      put on from a two-block pillar in the middle (it climbs down after), and a door facing the hut.
- [ ] When it's finished it goes to the hut, breaks the bed, carries it over and puts it in the house ("moving its bed
      from the hut into the house"). At night it goes into the house, shuts the door and sleeps there.
- [ ] `/drifter house` shows the house layer by layer (L logs, G glass, B bed, # blocks, D door).
- [ ] Its mines and caving never dig into the hut or the house.
- [ ] **Lighting:** with plenty of torches (`/drifter give minecraft:torch 64`), in the daytime it walks round home and
      puts torches on the ground where it's dark ("light up the base"). A tidy one (Builder, Homebody) puts them on a
      neat grid.
- [ ] **Habits:** walking through a door it opened, some Drifters shut it behind them (always, or only at night);
      tidy ones pick their crafting table back up after crafting away from home. `/drifter personality` lists its
      habits (doors, tables).
- [ ] (Re-test after fix) `/drifter spawn` near trees: it never ends up stuck inside a block.

### Your second batch of requests (new, tonight's build)
- [ ] **Finding Drifters:** type `/drifter list`. Every loaded Drifter is listed with where it is, its stage and what
      it's doing. Click a name (or type `/drifter tp <name>`, Tab completes names) and you're teleported to it.
- [ ] **Long mining trips:** when one goes for iron, the thought says e.g. "go mining for raw iron (0/31)", meaning iron
      for everything it still needs. It packs first (torches, food, spare pickaxes), then stays down a random 2 to 15
      minutes. Once it has what it came for it thinks "has the raw iron it came for, but stays down a while for more".
      Back home it smelts and crafts piece after piece of armor.
- [ ] **Strip miners explore caves:** a strip miner (`/drifter personality` shows "mining: strip") that breaks into a
      real cave thinks "broke into a cave: exploring it" and explores it properly before going back to its tunnel.
- [ ] **Its own tunnels aren't caves:** after a strip-mining trip, the next trip shouldn't think "going caving: a cave
      it saw at ..." about its own old tunnel (look at the spot it names: it should be a real cave).
- [ ] **Cleaning up pillars and bridges:** `/drifter home` ends with e.g. "4 pillar/bridge blocks left nearby (tidies
      within 33)" or "(messy: leaves them)". A tidy one, in the daytime near home, thinks "taking down a pillar or
      bridge it left by home", then "took down N blocks ...". Messy ones never do.
- [ ] **Farm:** it never sprint-jumps or hops onto or off the farmland (no trampled patches).
- [ ] **Fighting back when stuck:** a badly hurt Drifter running from a zombie that gets wedged (leaves, a corner) and
      keeps getting hit turns round and fights instead of standing there.

### Late fixes (tonight's last builds)
- [ ] **Baby zombies:** a Drifter attacked by a baby zombie fights it instead of running.
- [ ] **Fire:** give one a water bucket (`/drifter give minecraft:water_bucket`), set it alight (it walks through fire
      or touches lava): it pours water at its feet ("on fire: water bucket at its feet") and scoops it back up.
- [ ] **Waterfalls and streams:** at the top of a waterfall, swimming upstream, it never gets washed over: it thinks
      "the current's carrying it to a drop: blocking the edge" and puts a block there. Stuck in a stream it keeps
      getting washed back in, it thinks "the current keeps pushing it back: standing on a block out of it".
- [ ] **Diamonds:** it never leaves ore it dug through lying on the ground; diamonds right below it get a staircase.
- [ ] **Sealed underwater:** caught under rock in water with no air, it thinks "no air to swim to: mining up through
      the ceiling" and survives.

### Known gaps for milestone 8 so far (don't report these as bugs)
- No proper sprint-swimming yet (face-down fast swimming on long swims, section 10.10).
- Crops only grow, and mobs only spawn, near you (that's how Minecraft works without a player near; simulating the world
  round Drifters is planned as its own milestone later).
- No anvil yet; bookshelves come slowly (each needs 3 books: sugar cane and leather).
- Houses are one style of box with a flat roof (pitched roofs, porches and bigger compounds come later).
- It doesn't use shears for wool yet.
- The crafting table it used for the chest may end up as part of the hut's wall.
- Tunnels are always 1x2 (2x2 and 3x3 by mining style come later).
- It puts like with like in its chests but doesn't label them yet.

### Known gaps for milestone 7 (don't report these as bugs)
- No beds or sleeping yet; no "joined the game" chat lines yet.
- Not all habits are used yet: closing doors, picking up crafting tables, 2x2 or 3x3 tunnels, trail markers,
  hotbar layout, offhand torch.
- Some tics from the design aren't in yet (corner peek, edge peek, sprint stutter, door flips, tossing an item).
- No "finding diamonds" moment yet, no greetings (players come in milestone 9).

## Milestone 9: Disposition and PvP (in progress)

**Status:** steps 1 and 2 of 6: every Drifter now keeps a record of each player (Trust, Fear, Familiarity, grudges,
gifts it owes) and notices what you do. It doesn't act on it yet (that's step 3: watching, avoiding, warning,
fighting back). Every event below writes a thought like "Steve hit it once, then backed off: trust -2 -> -12".

**Commands:**
- `/drifter social`: the nearest Drifter's disposition (pacifist, wary neutral, friendly, territorial, opportunist,
  bandit, grudge holder), its starting Trust for strangers, and everyone it knows.
- `/drifter social show <player>`: one player in full (grudges, gifts it owes, your strength next to its own, where
  it last saw you, your base if it saw you go to bed).
- `/drifter social set <player> trust <n>` (or `fear`, `familiarity`): set a number.
- `/drifter social event <player> <event>`: apply any event from the table by hand.
- `/drifter social disposition <id>`: change its disposition.

**Things to try** (with thought logging on; check `/drifter social` after each):
- [ ] Hit it once and walk away: "hit it once, then backed off", Trust -10, Fear +5.
- [ ] Hit it two or three times: "attacked it again and again", Trust -40, a grudge.
- [ ] Get it down to 2 hearts (it doesn't fight back much yet): "nearly killed it", -60.
- [ ] Open one of its chests while it's watching (or close by, having just seen you): "opened its chest", -10. Take
      its iron or diamonds out; the next time it opens that chest: "took things from its chest", -20 per stack.
- [ ] Break a block of its hut or farm while it sees you: -5 each, at most -50 a day.
- [ ] Kill an animal in its pen in front of it: -15. Light a fire or pour lava near its home: -40.
- [ ] Draw a bow and aim at it: -5, Fear +5. Sneak up behind it, crouching: -5.
- [ ] Throw it something (Q while facing it, close by): "gave it something", +5 to +30 by value (a diamond is worth
      far more than dirt). `/drifter social show` lists it under what it owes you.
- [ ] Crouch up and down at it, facing it: +3 (three times a day at most).
- [ ] Kill a zombie that's attacking it: +20 (+30 if it was nearly dead).
- [ ] Stand near it for 5 minutes without trouble: +1 and Familiarity +1.
- [ ] Wear much better armor than it has: Fear +10 to +30, once.
- [ ] Each day Trust drifts back toward where it started; grudges fade (a vengeful one never forgets the worst).

**Step 3 (new): how it treats you.** `/drifter social` now ends each line with its mode toward you ("now tolerating",
"now warning", ...).
- [ ] Walk up to a Drifter it hasn't seen for a while: it stops, looks at your face, down at your feet and back up
      ("noticed <you>: looking them over").
- [ ] Make it afraid of you (`/drifter social set <you> fear 90` and `trust -40`): it walks away when you come within
      12 blocks ("keeping away from <you>"). A Pacifist (`/drifter social disposition pacifist`) does this whenever
      it dislikes you.
- [ ] **The warning:** make a Territorial one (`/drifter social disposition territorial`) and walk into its base (20
      blocks round home): it takes out a weapon, faces you and holds still for about 2 seconds ("warning <you>").
      Step back: it steps back too ("backed off at its warning"). Stay or walk closer: "ignored its warning". (It doesn't
      attack yet: that's step 4.)
- [ ] A Bandit (`disposition bandit`) warns strangers it meets the same way.
- [ ] On Peaceful difficulty it never warns or turns on anyone.

**Step 4 (new): turning hostile.** Only after the warning rule is satisfied (you wronged it, or ignored its warning),
and it still runs when it's losing.
- [ ] **Territorial:** ignore its warning and stay in its base: it attacks; walk out past about 20 blocks from its home
      and it stops ("drove <you> out of its base").
- [ ] **Vengeful:** hit a Wary Neutral one two or three times (Trust drops past -25 with a grudge): from then on it
      attacks you on sight ("it hasn't forgotten what <you> did"). Run off: a vengeful one goes looking where it last
      saw you, or at your base if it saw you go to bed there.
- [ ] **Bandit:** a Bandit you've never wronged warns you first; then it stalks you from 24-40 blocks back, sneaking
      ("stalking <you>, waiting for an opening") and attacks when you're busy fighting mobs, at night alone, or if your
      gear is much worse than its own. Never while you're in bed.
- [ ] **Opportunist** (PvP Sweats): neutral, but jumps you at those same openings.
- [ ] If it kills you, it picks up what you dropped.
- [ ] When it's losing, it runs (the usual flee), and comes back another day if the grudge stays.

**Step 5 (new): friendly ones.** Make one like you (`/drifter social set <you> trust 40`, or win it over with gifts).
- [ ] **Greeting:** when you come within 10 blocks it looks at you and crouches up and down (once every 5 minutes).
- [ ] **Gifts and barter:** throw it something (Q, facing it). Later it walks over, nods, and throws you back something
      it doesn't need of about the same worth ("paying <you> back: ..."). Give it a diamond, get back roughly 5 iron.
- [ ] **Follow me:** stand close, face it, crouch twice, then walk off: it follows you for about 3 minutes ("following
      <you> for a while"). Needs some Trust (10 and you've been around it a bit, or 25).
- [ ] **Help:** let a zombie attack you near a friendly Drifter: it comes and fights it ("helping fight the zombie").
- [ ] **Creeper warning:** a creeper sneaking up behind you: it turns to face the creeper and backs away, so you see
      where it's looking ("warning <you> about a creeper behind them").

**Step 6 (new): chat, sleep, groups of players, manhunt.**
- [ ] **Chat lines:** now and then a Drifter says something in chat when it greets you ("<name> hi"), thanks you for a
      gift ("ty"), warns you off ("go away"), gets hit ("bruh"), or wins or loses a fight ("gg"). Each has its own way of
      saying it; quiet ones (hermits) rarely talk; at most one line every 20 s each. Turn off: `/drifter config chat false`.
- [ ] **Sleep:** with a Drifter that's hostile to you within 8 blocks, the bed says monsters are nearby; a friendly or
      neutral one doesn't stop you sleeping.
- [ ] **Players together:** a Drifter that likes one player and meets another standing with them warms to the second a
      little ("<p2> is with <p1>: trust ..."); with someone it hates, it trusts them less. Once a day per pair. Turn off:
      `/drifter config share false`.
- [ ] **Manhunt (hunter):** spawn a fresh Drifter (`/drifter spawn`, it starts with nothing), stand near it and run
      `/drifter manhunt start <you>` (or add `dragon` for the full game). Chat announces it. It hunts you from the
      start, no warning; out of sight it "checks its compass" and heads your way (it only gets your direction). If it
      dies it respawns at world spawn with nothing and keeps hunting. You win by reaching the Nether (or killing the
      dragon); it wins by killing you. `/drifter manhunt` shows the game, `/drifter manhunt stop` ends it.

### Night and time fixes (user's report, 2026-10-15)
- [ ] **Armed Drifters carry on at night:** with a stone sword or better (or two pieces of iron armor), fairly healthy and
      fed, and not too cautious, a Drifter thinks "tonight it carries on through the night" in the evening and keeps
      working, exploring and gathering in the dark, fighting what comes. Unarmed, hurt, hungry or cautious ones
      (Homebodies, Hermits) still go in. `/drifter personality` shows its night style.
- [ ] **Daylight through windows:** sleep through a night in your bed. Drifters in huts with windows or an open door
      notice it's morning and come out, instead of waiting in the dark.
- [ ] **Manhunt command fixed:** it's `/drifter manhunt start <you>` (it was wrongly under `/drifter social manhunt`).

### Reactions you can see (user's feedback: "they don't really care about me")
- [ ] **Gift:** right after you give it something it stops, looks at you, nods and crouches back ("answering <you>'s gift").
- [ ] **Hello:** crouch up and down while looking at it: it looks at you and crouches back ("answering <you>'s hello"),
      unless it's hostile to you or avoiding you.
- [ ] **Friendly ones come over:** once it likes you (Trust 25+), it walks over from up to 24 blocks away to say hi
      (every 5 minutes at most).
- [ ] **Paying back:** it can now pay you back with common things it has plenty of (logs, planks, cobblestone, coal,
      food), not only true spare items.
- [ ] **Say its name:** type a Drifter's name in chat (for example "hey MapRiver") within about 32 blocks of it: it stops,
      turns toward you, and if it sees you and doesn't dislike you, nods (maybe answering "?" or "yes?"). Thought: "heard
      <you> say its name".
- [ ] **Thought logging is on by default now** (no need to type `/drifter thoughts log true`); `/drifter thoughts log false`
      turns it off and remembers that.
- [ ] **Rare gifts count:** an enchanted golden apple, a totem, a diamond block and so on are worth the full +30 Trust in
      one go (before, enchanted golden apples counted as plain food).
- [ ] **Berry bushes:** Drifters no longer walk through grown berry bushes. They break them on the way (instantly,
      like a player clearing a path) and cut straight through a patch. Touching one, it stands still (they only prick
      while you move) and breaks it. Staged test: across a 5-wide patch starting at 2 hearts, it arrived alive.
- [ ] **Structure chests:** when a Drifter sees a chest belonging to a structure (a ruined portal, a shipwreck, a temple,
      a village house), it drops what it's doing and goes to loot it ("spotted a chest at ...: going to loot it", then
      "looting the chest at ..."). It takes everything that isn't junk, once per chest. Chests you or other Drifters
      placed are never touched. Greedy ones go for chests up to 48 blocks away straight away; others go at once for
      chests within 24 blocks and otherwise when they're free.
- [ ] **Raiding your chests (on by default, by personality):** a Bandit (`/drifter social disposition bandit`) that finds
      a chest you placed raids it when you're not in sight ("raiding a player's chest at ..."). Other Drifters only raid
      you if they hold a real grudge (hit them a few times) and know your base (they saw you go to bed). Never ender
      chests, never chests right by your bed, never inside spawn protection. Off: `/drifter config raiding false`.
- [ ] **Stuck up on its own pillars:** a Drifter stranded on top of its own pillars and bridges with (almost) no blocks
      thinks "stuck up here with no blocks: digging down through its pillar" and digs down a block at a time (walking to
      a pillar first if it's on a bridge). Routes also keep 6 building blocks in reserve, so this should rarely happen.
- [ ] **Homes away from structures:** a Drifter never settles within 6 blocks of a structure or someone's build (a ruined
      portal's obsidian, netherrack, magma and gold, village houses, cobblestone and planks it didn't place) or lava.
      Spawn one next to a ruined portal: it loots the chest, then settles somewhere clear of it.
- [ ] **Manhunt survives a restart:** start one, quit to the menu (or close the game), come back: the hunter is still on
      you (the game is saved with the world; `/drifter manhunt` shows it). A different world has its own (or none).

## Milestone 10: Offscreen and performance (in progress)

**Step 1: hybrid simulation (new).** Away from you, the 3 busiest Drifters keep running as if a player were with them:
their area stays loaded, they keep playing, and crops round them grow. The others freeze with their chunks until you
come near (they'll catch up in a later step).
- [ ] `/drifter sim` lists who is being kept running and where. `/drifter config simulated <0-10>` changes how many
      (0 turns it off). More means more work for your PC, about like that many extra players standing still.
- [ ] Walk far away from a Drifter that's out working (or `/drifter tp` to another one): when you come back, it has
      moved on and done things, not stood frozen.
- [ ] Its farm grows while it's near it, even when you're far away.
- [ ] Mobs don't spawn round far-away Drifters (vanilla only spawns and keeps mobs within 128 blocks of a real player).
- [ ] Closing and reopening the world: the same Drifters carry on (who's kept running is saved with the world).


**Step 2: detail and speed (new).** Drifters far from you cut what you couldn't notice anyway (fidgets, a few eye rays,
re-thinking a bit less often). Several slow spots were made much faster, and four ways to fall off a mountain were fixed.
- [ ] `/drifter perf` prints how much of each server tick all Drifters together take (the goal is under 3 to 5 ms for
      10), split by system, with the slowest single moment and what that Drifter was doing, and how many are at each
      detail level ("Detail: 2 full, 1 reduced"). If the game ever stutters with Drifters around, send me this line.
- [ ] Stand next to a Drifter: it's "full" and fidgets as before. Walk 50+ blocks away: "reduced". 130+ blocks: "far".
      Looking at one from far off (with nothing in the way) keeps it "full". `/drifter tier full|reduced|far|auto`
      forces the nearest one (for testing).
- [ ] Nothing should look different up close, and far-off Drifters should still get things done.
- [ ] Mountains: Drifters walking down steep slopes slow to a walk before steps onto ground they haven't seen, and don't
      slide off ledges when they stop to think. Fewer "fell from a high place" deaths.

**Step 3: catching up (new).** A Drifter that was frozen while you were far away (not one of the 3 kept running)
fast-forwards the time it missed when you come back near it (up to 3 days): it gathers, eats, wears out tools, crafts
what it can and moves up stages, and once in a while dies out there.
- [ ] Easiest check: stand next to a Drifter and type `/drifter catchup 12` (12 in-game hours). Its thought log (and
      the chat reply) says what it did: "caught up on 0.5 days away; gathered 20 cobblestone, ...; made a stone
      pickaxe, ...". Its inventory should match.
- [ ] Real check: `/drifter config simulated 0` (so nobody is kept running), walk or fly 300+ blocks away from a
      Drifter, wait 5+ minutes there, come back. It should now be at its home (if it has one) with more stuff, and
      the log line "... catches up: caught up on ... days away". Set `/drifter config simulated 3` again after.
- [ ] It never gets items from nowhere you'd find odd for its stage: logs before a pickaxe, cobblestone and coal with
      one, iron with a stone pickaxe, diamonds only with an iron pickaxe.

**Step 4: time while the world is closed, and long walks (new).**
- [ ] `/drifter config offline off` turns catching up off completely; `game_time` (the default) only counts time the
      world was running; `real_time` also counts real time the world was closed (up to 2 in-game days), from the next
      time you open the world. Try `real_time`, close the world for 10+ minutes, open it: the log says "The world was
      closed for N min" and Drifters catch up when you get near them.
- [ ] A Drifter between 128 and about 160 blocks from you that heads further out doesn't freeze at the edge of the
      world you have running: it "sets off" (in the log) and `/drifter sim` lists it as traveling. It turns up at the
      place it was going once that place is loaded (walk there).

## Milestone 11: Nether (in progress)

**Step 1: the portal (new).** Once a Drifter enchants its gear (stage 8), it collects 10 obsidian and makes flint and
steel, then builds a Nether portal near its home and lights it. It doesn't go through yet (next steps).
- [ ] Quick check without waiting for stage 8: stand by a Drifter that has a home, then
      `/drifter giveall minecraft:obsidian 10`, `/drifter giveall minecraft:flint_and_steel 1`,
      `/drifter giveall minecraft:cobblestone 16`, and `/drifter brain goal portal`. It picks a clear spot 6 to 16
      blocks from home, digs two ground blocks out, builds the frame (no corner obsidian; a cobblestone block props each
      top corner), climbs a two-block pillar to finish the top, lights it, and digs its pillar back down. Its thoughts:
      "building a Nether portal at ...", then "lit the Nether portal at ...".
- [ ] It never places a block floating in the air or out of reach, and the portal works for you (walk in).

**Finding far-off Drifters (new).**
- [ ] `/drifter list` shows every Drifter in the world: loaded ones as before, then in grey the ones out of the loaded
      world with where they were last ("last seen at ..., 12 min ago", or "set off from ..." for travelers). Click one
      (or `/drifter tp <name>`, names tab-complete) to go there; it loads in as you arrive and catches up.
- [ ] **Your own spread test:** `/drifter spawnmany <count> [radius]` spawns that many Drifters (up to 200) on dry ground
      at random within the radius (default 2000, up to 10000) of where you stand. The game pauses while it generates
      the land (about 40 s for 100 over 2000 blocks). Then `/drifter perf` shows the cost, `/drifter sim` the 3 kept
      running, `/drifter list` all of them. Only Drifters near you (and the 3) actually run; the rest wait frozen and
      catch up when you visit. To remove a test crowd: `/kill @e[type=drifter:drifter]`.

**Steps 2-3: packing and going through (new).** With its portal lit, a Drifter gathers a gold armor piece, a stack of
cobblestone, 20 cooked food and a spare pickaxe, then (by day, fit and fed) goes to the Nether: steps into its portal
and waits the 4 seconds a player does, walls the portal in on the other side with cobblestone (leaving a door), looks
round, and for now comes home again after about 30 seconds. (What it does in the Nether comes in the next steps.)
- [ ] Quick check: a Drifter with a home, give it what the portal step needs plus the packing list:
      `/drifter giveall minecraft:cobblestone 64`, `/drifter giveall minecraft:cooked_beef 24`,
      `/drifter giveall minecraft:golden_boots 1`, `/drifter giveall minecraft:iron_sword 1`,
      `/drifter giveall minecraft:shield 1`, `/drifter giveall minecraft:iron_pickaxe 2`, then `/drifter brain goal portal`
      and when it's lit `/drifter brain goal nether_go`. Follow it in (or `/drifter tp <name>`): "in the Nether: the
      portal home is at ...", "walled the portal in", later "back from the Nether".
- [ ] It never walks into a portal by accident while doing something else.

**Bows (new).** Drifters make and use bows like players: string from spiders (or cobwebs), arrows from flint, sticks
and feathers. With a bow and arrows they shoot things coming at them from range (zombies, skeletons, creepers before
they get close, ghasts, blazes), and switch to the sword up close.
- [ ] Give a Drifter `/drifter giveall minecraft:bow 1` and `/drifter giveall minecraft:arrow 32`, then summon a zombie
      15 blocks away at night (or a husk by day): it takes the bow out, draws (you see the draw pose), and shoots. A
      full draw takes a second. Up close it switches to the sword.
- [ ] A creeper a few blocks off gets arrows, not a sword hit.
- [ ] `/drifter get minecraft:bow` sends the nearest Drifter off to make one (hunting spiders for string if it has
      none; spiders are around at night and in caves). `/drifter plan minecraft:arrow 32` shows how it would get arrows.
- [ ] Their arm swings when they place a block now (your report).
- [ ] **Running away (from your world):** a Drifter running from mobs on snowy ground never runs into powder snow (it
      sank in one while a creeper chased it), and running from a skeleton it swerves behind a hill, tree or wall when
      one is near instead of running straight across open ground.
- [ ] **Fortress and blaze rods (new):** spawners now wake for Drifters as they do for you (within 16 blocks), so a
      Drifter waiting by a fortress's blaze spawner gets blazes to fight (with its bow from range, then the sword). Find a
      fortress yourself (`/locate structure minecraft:fortress` in the Nether), put a Drifter near its spawner with a
      bow, arrows, a sword, a shield and food, and `/drifter brain goal nether_blaze`: it waits by the spawner, fights
      the blazes and picks up their rods ("got 6 blaze rods" when done). Dungeon spawners in caves wake for mining
      Drifters too.
- [ ] Standing still on magma blocks it crouches (no burning). A Drifter stuck in one spot in the Nether with no way
      on now looks down round itself and tries again; if it's still stuck, the game log has a line
      `[Drifter] <name> has no route from where it stands` with a block grid: send me that part of the log.
- [ ] **Catch-up builds the hut (new):** a Drifter with a home and blocks, away while you're far off, comes back with its
      hut's walls and roof up ("built N blocks of its hut" in its catch-up line); it puts the door, bed and torch in
      when it's about again. It never builds over blocks you placed.
