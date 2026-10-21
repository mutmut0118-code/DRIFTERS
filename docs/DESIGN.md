# Drifter Mob — Survival AI Design Spec

Oct 13, 2026 · @Gerry W

## 1. Overview

The Drifter is a player-skinned humanoid mob that plays survival by the same rules a player does: it spawns with nothing, punches trees, crafts, mines, builds a home, fights mobs, and works its way toward diamonds and the Nether. Toward real players it is neutral or hostile depending on its personality and on how each player has treated it. Target platform is Minecraft 1.21.1 on Fabric, to match the RPG modpack; version-specific numbers are flagged where they matter. "Drifter" is a working name.

### Design pillars

1. **Plays by player rules.** No x-ray, no free items, no instant mining, no seeing through walls. Everything it carries was gathered, crafted, looted or traded, which is what makes its gear feel earned when you kill it.
2. **Reads as a player at a glance.** Someone watching from 30 blocks away should briefly think it's another person: camera flicks, sprint-jumps, crouch-spam, leftover dirt pillars.
3. **Every Drifter is an individual.** Persistent personality, habits and memory. Two Drifters with identical gear still behave differently.
4. **Disposition is earned, not flagged.** Hostility comes from personality plus history with each player, not a fixed hostile/neutral switch.
5. **Readable and fair.** It telegraphs hostile intent, has counterplay, and respects griefing settings.
6. **Cheap when nobody's watching.** Full simulation near players, coarse simulation far away, abstract catch-up when unloaded.

### Goals for v1

- A full survival loop from empty hands to at least iron and diamond gear, with the Nether as a later stage.
- Player-like combat against every vanilla mob and against players.
- A base that visibly grows over in-game days.
- Social behavior toward players: avoid, watch, trade, help, stalk, ambush, take revenge.

### Non-goals for v1

- Imitating a top-tier PvP player perfectly.
- Redstone engineering, large automatic farms, megabuilds.
- Real chat conversation (only optional short canned lines).
- The Ender Dragon fight (an optional late stage, off by default).

### Fairness rules (hard constraints)

These apply to every system in this document and should be treated as non-negotiable.

- **Knowledge only through perception** (section 5). A buried ore is unknown until a face of it has been seen.
- **Actions take player time.** Mining speed follows tool tier, Efficiency and Haste; eating takes 1.6 s; crafting includes a short "inventory open" pause.
- **Player reach.** 4.5 blocks to interact with blocks, 3 blocks to hit entities.
- **Player vulnerability.** It takes every kind of damage a player does, and hostile mobs target it like a player (section 2).
- **No impossible placement.** It cannot place a block floating in air, open a chest it can't reach, or interact through solid blocks.

## 2. Core architecture: what the Drifter actually is

Recommendation: build the Drifter as a custom mob entity that borrows a server-side fake player only at the moment it interacts with the world. This keeps mob lifecycle (spawning, saving, despawning) while getting player-accurate block breaking, item use and crafting.

### The three options

| Option | What it is | Strengths | Problems |
| --- | --- | --- | --- |
| A. Pure mob | A pathfinding mob subclass that reimplements every player mechanic | Spawns and saves like any mob; clean AI integration; no player-count side effects | Hunger, crafting, block breaking, item use, XP all rebuilt by hand; hostile mobs ignore it by default |
| B. Fake server player | A real player object with a dummy network connection, like Carpet's bots | Perfect mechanic parity for free; mobs target it naturally; shows in tab list | Counts as a player: keeps chunks loaded, spawns hostile mobs around itself, affects sleep percentage, gets its own player save file; many mods assume a real connection; can't spawn naturally |
| C. Hybrid (recommended) | Option A's mob, plus a fake-player helper (Fabric API ships one) used only to perform interactions | Mob lifecycle with player-accurate drops, tool damage, placement rules and recipe results | Inventory must be synced into the helper per interaction; claim/protection mods will see actions as a player (usually desirable) |

### Control backbone

The vanilla goal selector is too flat for this. Use a custom layered controller (section 8) and borrow the pattern of the vanilla Brain system that villagers, piglins and the warden use: sensors write into memory slots, and behaviors read from them. That split (perception, memory, decision) maps directly onto sections 5 to 8.

### Player-parity checklist

Every mechanic a player has that a mob lacks needs an owner. "Build" means the Drifter needs its own model of it.

| Mechanic | Mob gets it for free? | What to build |
| --- | --- | --- |
| Hunger, saturation, exhaustion | No | A hunger model that mirrors the player's rules exactly (section 3) |
| Natural regeneration | No | Driven by the hunger model |
| 36-slot inventory, armor, offhand | Partly (equipment slots only) | Full inventory; hand and armor slots mirrored to equipment for rendering |
| Hotbar and selected slot | No | Hotbar model with switch timing (section 10.18) |
| Block breaking with progress | No | Per-block mining progress, crack overlay broadcast to clients, drops via the helper |
| Block placement | No | Via the helper, with face-adjacency and reach checks |
| Crafting | No | Recipe lookup through the game's recipe manager; 2x2 vs 3x3 table rules |
| Furnaces, chests, barrels | No | Direct container access within reach; play the chest lid open/close animation |
| Eating and drinking | No | Use duration, particles, sounds, effects |
| Bow, crossbow, trident, shield | Partly (skeleton-style bows) | Player draw times and shield raise delay |
| Attack cooldown and crits | No | Player cooldown and crit rules (section 13) |
| XP orbs and levels | No | Orbs attracted to the Drifter; an XP pool for enchanting and repair |
| Picking up any item | No (mobs grab gear only) | Pickup of all item types into inventory, with filters (section 10.18) |
| Sleeping in beds | Partly (villager sleeping pose) | Uses beds; does not skip the night |
| Sprint, sneak, swim, crawl poses | Poses exist | Drive the poses; sneak also changes speed and edge behavior |
| Being targeted by hostiles | No | Add target goals so zombies, skeletons, spiders, etc. hunt it like a player (config toggle) |
| Endermen, piglins, creepers, warden checks | Player-only | Add Drifter checks so staring angers endermen and gold armor calms piglins |
| Fall, fire, drowning, suffocation | Yes | Nothing |
| Boats, horses, minecarts | Riding yes, steering no | Custom steering |
| Ender pearls | Mostly | Verify owner teleport for non-player owners |
| Elytra | No | Late feature |
| Death drops | Loot table only | Drop whole inventory plus XP like a player |
| Name above head | Custom name | Always-visible player-style nameplate |
| Death messages | Only for named pets | Broadcast player-style death messages (config) |
| Join/leave messages, tab list | No | Optional cosmetic fakes (config) |

### Rendering

- Biped player model with wide (Steve) and slim (Alex) arm variants.
- Skin: a real player's, downloaded like a multiplayer server's (user, 2026-10-20: "its just like minecraft multiplayer"): the skin of the real account with its name if there is one, else of one of a couple of names like it; the game's default skins until it has downloaded, and with no internet. A setting turns real skins off.
- Armor layers, items in both hands, and the full set of use poses: bow draw, crossbow charged, shield raised, eating arm raise, trident wind-up, spyglass.
- Sneak, swim, crawl, sleep and elytra-glide poses.
- Optional cape slot for rare "veteran" Drifters.

### Resource pack compatibility (EMF, Fresh Animations: Player Extension)

EMF applies custom models by entity model layer, so a pack written for the player won't touch a new mob automatically. To get Player Extension animations on Drifters:

1. **Reuse the vanilla player model and its model layers** (wide and slim) in the Drifter's renderer instead of a copied model. That gives EMF the best chance of treating it like the player. Test this first.
2. **If EMF still skips it,** ship a tiny compatibility resource pack that copies the pack's player model files under the Drifter's own model layer name. Check the pack's license before redistributing its files; otherwise document the steps for users.
3. **Drive the same states a player has:** sneaking, swimming, sprinting, using an item, blocking, sleeping, on ground. Animations key off those, so the Drifter must set them exactly like a player for them to fire.
4. **Skins:** Entity Texture Features can also add random skin variants per Drifter from a resource pack, which pairs well with the skin pool.

Treat this as an early test in milestone 1, since it decides how the renderer is built.

## 3. Body and stats

The Drifter uses player numbers everywhere so that a player's instincts about it are correct: if you could outrun a player, you can outrun it. Values below are Java 1.21.1 player values from memory; confirm each against the game when implementing, ideally by reading them from the game's own player constants rather than hardcoding.

### Physical attributes

| Attribute | Value | Notes |
| --- | --- | --- |
| Max health | 20 (10 hearts) | Absorption from golden apples on top |
| Walk speed | about 4.3 blocks/s |  |
| Sprint speed | about 5.6 blocks/s | Blocked when food is 6 or lower |
| Sprint-jump average | about 7.1 blocks/s on flat ground | The fastest way a player crosses open land |
| Sneak speed | about 1.3 blocks/s | Never falls off edges while sneaking |
| Step height | 0.6 blocks | Full blocks need a jump |
| Jump height | about 1.25 blocks |  |
| Block reach | 4.5 blocks | 1.20.5+ attribute |
| Melee reach | 3.0 blocks | 1.20.5+ attribute |
| Fall damage | 1 HP per block beyond 3 | Water, hay, slime, powder snow, cobweb negate or reduce |
| Air supply | 15 s | Then 2 HP of drowning per second |
| Hurt immunity | 0.5 s after a hit | Shapes combat timing |

### Inventory

- 36 slots, of which 9 are the hotbar, plus 4 armor slots and 1 offhand slot.
- Only one hotbar slot is "held" at a time. Switching slots is instant for a player, but the Drifter adds a 1 to 3 tick human delay (section 16).
- Optional ender chest access, keyed to the individual Drifter, never to any player.

### Hunger model

Mirror the player hunger rules exactly, since much of the Drifter's daily rhythm (when it eats, when it stops sprinting, when it goes home) falls out of them.

- Food level 0 to 20, saturation 0 up to the current food level, exhaustion accumulating to 4.0 before it drains 1 saturation (or 1 food when saturation is empty).
- Exhaustion sources: sprinting about 0.1 per block, sprint-jumping 0.2 per jump, jumping 0.05, swimming 0.01 per block, attacking 0.1, taking damage 0.1, breaking a block 0.005, and 6.0 per HP healed by natural regeneration. Walking and sneaking are free.
- Regeneration: at food 18 or more, heal 1 HP every 4 s; at food 20 with saturation left, heal fast (every 0.5 s) from saturation.
- Starvation at food 0: lose 1 HP every 4 s, down to 10 HP on Easy, 1 HP on Normal, death on Hard.

### Food reference

| Food | Hunger | Saturation | Drifter opinion |
| --- | --- | --- | --- |
| Golden carrot | 6 | 14.4 | Late-game favorite |
| Steak / cooked porkchop | 8 | 12.8 | Mid-game staple |
| Cooked mutton | 6 | 9.6 | Good |
| Cooked salmon | 6 | 9.6 | Good |
| Cooked chicken | 6 | 7.2 | Fine |
| Bread | 5 | 6.0 | Farm staple |
| Baked potato | 5 | 6.0 | Farm staple |
| Cooked cod | 5 | 6.0 | Fine |
| Apple | 4 | 2.4 | Early snack |
| Carrot | 3 | 3.6 | Snack, mostly kept for breeding |
| Raw meat | 2 to 3 | low | Only when it can't cook |
| Rotten flesh | 4 | 0.8 | Desperate only; 80% chance of Hunger effect |
| Spider eye | 2 | 3.2 | Never (poison) unless starving |
| Golden apple | 4 | 9.6 | Combat item, not food |

Eating takes 1.6 s (dried kelp 0.8 s). The Drifter slows to sneak pace while eating, exactly like a player.

### Weapons and timing

| Weapon | Damage | Attacks per second |
| --- | --- | --- |
| Fist | 1 | 4.0 |
| Wood / stone / iron / diamond / netherite sword | 4 / 5 / 6 / 7 / 8 | 1.6 |
| Wood / stone axe | 7 / 9 | 0.8 |
| Iron axe | 9 | 0.9 |
| Diamond / netherite axe | 9 / 10 | 1.0 |

- **Critical hit:** x1.5 damage when falling, not on the ground, not sprinting, not in water or on a ladder, and the attack cooldown nearly full.
- **Sweep:** swords hit nearby enemies too when the attack is on the ground, not sprinting, at full cooldown.
- **Shield:** blocks only after a 0.25 s raise; an axe hit disables it for 5 s.
- **Bow:** full draw in 1 s. **Crossbow:** loads in 1.25 s (faster with Quick Charge).

### Mining time

Mining time follows the game's own break-speed calculation for the held tool, Efficiency, Haste, Mining Fatigue, being in water (5x slower without Aqua Affinity) and not standing on the ground (5x slower). Compute it through the fake-player helper so it always matches the player exactly. A few anchors for tuning: logs by hand take 3 s, stone with a stone pickaxe about 0.6 s, deepslate takes about twice as long as stone, and obsidian with a diamond pickaxe about 9.4 s.

## 4. Spawning, joining, leaving and lifecycle

Drifters should be rare and feel like other people arriving in the world, not like a mob type that fills the night. A world might hold up to about 10 at once, each with a name, a home and a history.

### Natural spawning

- **Where:** the surface, on natural ground blocks (grass, sand, snow, podzol, stone), in daylight, at least 64 blocks from any player and out of their line of sight, so the Drifter is discovered rather than seen popping in.
- **Not near:** world spawn (configurable radius), village centers, other Drifters' bases, or a player's bed.
- **Cap:** a per-world active cap (default 10, revisited after profiling) plus a per-region cap so they don't cluster. Check spawns only every few in-game minutes to keep it cheap.
- **Biome weights:** higher in plains, forests, taiga and savanna; lower in deserts, badlands and frozen biomes; never in mushroom fields or the deep dark.
- **Manual:** a spawn egg and a command that takes archetype, kit and disposition arguments for testing.

### Starting kits

| Kit | Frequency | Inventory | Starting stage |
| --- | --- | --- | --- |
| Fresh | 70% | Nothing | Stage 1 (wood) |
| Traveler | 20% | Stone tools, some food, a bed, 20 to 40 cobblestone, torches | Stage 4 or 5 |
| Veteran | 8% | Iron or diamond gear, a few enchantments, a shield, golden apples, ender pearls | Stage 7 or 8 |
| Raider | 2%, hostile archetypes only | Iron gear, bow, flint and steel, extra food | Stage 6, skips base building and roams |

A kit is a starting point, not a cheat: every item in it is treated as something the Drifter "brought from elsewhere" and wears normally.

### Optional world-gen camps

An optional structure: a small abandoned or occupied Drifter camp (a hut, a chest, a crafting table, a farm patch). An occupied camp spawns its Drifter on first load, already at Stage 3 to 5. This lets players stumble on a home before they meet its owner.

### Identity

- **Username:** generated to look like a real player name: 3 to 16 characters, letters, digits and underscores only. Patterns like a word plus digits, two words joined, or a word with underscores, mostly lowercase, with rare all-out styles. Never reuse a name on the server's player list, never use known public figures or creators, and filter for offensive words.
- **Skin and arm type:** picked once from the skin pool.
- **UUID registry:** every Drifter identity is stored in a world-level registry (name, skin, personality seed, relationships, last known base). Identity outlives the entity, which enables the return mechanic below.

### Joining and leaving (cosmetic, configurable)

- **Join:** when a Drifter spawns and a player is online, optionally broadcast a yellow "\<name> joined the game" line. Default on for singleplayer flavor, off for servers where it could confuse people.
- **Leaving:** a Drifter can "log off." It walks out of sight, then despawns with "\<name> left the game." Its base stays as it was, chests and all, which makes abandoned Drifter bases a discoverable kind of loot.
- **Returning:** a departed identity can come back days later (5 to 15 in-game days, random), with the same name, skin, memories and grudges. It walks back toward its old base.

### What makes a Drifter leave

- Boredom: its long-term goals are met and nothing new has happened for many days.
- Frustration: it died repeatedly in a short window (section 20), the classic rage-quit.
- Displacement: its base was destroyed or taken over and it decides not to rebuild.
- Lifespan: an optional max lifetime in in-game days.
- Never while in combat or while a player is watching from close range.

### Persistence and despawning

Drifters never despawn like ordinary hostile mobs. A freshly spawned Drifter that hasn't placed a block or met a player yet may quietly despawn if the region unloads for a long time, which keeps forgotten ones from piling up. Once it has a home or a relationship, it is persistent.

### Lifecycle phases

1. **Arrival:** first minutes; looks around, picks a direction.
2. **Establishment:** days 1 to 2; wood, stone, first shelter, first food.
3. **Growth:** iron age, a real base, farms, regular mining trips.
4. **Maturity:** diamonds, enchanting, a stable daily routine.
5. **Late game:** Nether trips, netherite, possibly elytra.
6. **Departure:** logs off, or is killed and doesn't return.

The phases are a narrative view of the progression ladder in section 9, which holds the exact entry and exit criteria.

## 5. Perception

The Drifter knows only what a player at its position, looking where it is looking, could know. Perception writes into memory (section 6), and every decision reads from memory, never from the world directly. This one rule is what enforces "no x-ray" and what gives players stealth counterplay.

### Vision

- **View cone:** configurable like a player's FOV slider (`/drifter config fov`, 30 to 110, default 70; user's request in session 2): the vertical angle is the FOV and the width follows from a 16:9 screen, so the default is about 102 degrees horizontal and 70 vertical (the first design said 110 x 70). Centered on the head, not the body. A Drifter looking at a furnace genuinely cannot see the zombie behind it.
- **Peripheral zone:** 110 to 180 degrees, motion only. Something moving there produces a glimpse that makes it turn its head, but no identification.
- **Range:** configurable (`/drifter config sight <chunks>`, default 10 chunks = 160 blocks; the user's call, since a player sees far more than a mob) in daylight for entities, scaled by the light level at the target and by its size (a chicken is a speck long before a horse is). In full darkness, a target is seen at about 8 blocks unless it is holding a light source, on fire, or glowing. Peripheral motion only counts within about 32 blocks.
- **Line of sight:** a ray from the eye to the target's head and feet. Seeing either counts; seeing both counts more. Beyond 32 blocks one ray to the middle is used, only a few far checks run per re-rank, and far tracked entities are re-checked less often, so long sight stays cheap.
- **Modifiers:** rain shortens range by about a quarter, thunderstorms more; underwater range is short; Blindness and Darkness effects apply exactly as for players.
- **Sneaking targets:** harder to spot, and their nameplate is hidden behind blocks just like for players.
- **Invisible targets:** effectively unseen, except for armor they're wearing and particles.

### Awareness meter

Detection is gradual rather than instant, but quick, like a player's common sense: something close and in plain view is identified in about a third of a second, something far off and still in a second or two. Each perceived entity has an awareness value from 0 to 1 that fills faster when the target is close, moving, lit, and in the center of view, and drains when it isn't.

| Level | Threshold | Reaction |
| --- | --- | --- |
| Glimpse | 0.3 | Head turns toward it; pauses current action briefly |
| Suspicious | 0.6 | Watches, holds position, may hold up a weapon or investigate |
| Detected | 1.0 | Identified; disposition and threat logic take over |

This is the core of stealth play against hostile Drifters: a sneaking player in the dark can get very close.

### Seeing blocks

- **Sampled, not scanned:** each tick, cast a small budget of rays (for example 6 to 12) across the view cone and record notable blocks they hit: ores, chests, crafting tables, furnaces, beds, lava, water, spawners, portals, structure blocks like bookshelves and mossy cobblestone.
- **Mining reveals:** breaking a block reveals its six neighbors. This is how mining discovers ores, and it is the only way the Drifter learns what's inside rock.
- **No guessing beyond probability:** the Drifter can use Y-level odds ("diamonds are common near the bottom") to choose where to dig, exactly like a player who's read the wiki, but never knows a specific hidden ore.
- **Budget scaling:** more rays when a player is nearby and the Drifter is visible, fewer when it's far away.
- **Far sight:** a couple of extra rays per tick near the horizon (inside the view cone) reach the sight range in daylight, shorter in the dark and in rain. They note **landmarks**: mountains and high ground, villages (bells and villager workstations), and bare stone (cliffs, cave mouths) as a place to get stone, plus groves of trees. Only the first part of each ray (the path range) goes into the pathfinder's block knowledge. Rays never look into unloaded chunks.

### Hearing

Hearing uses the same vibration events that sculk sensors listen to, which already exist in the game and carry a type, position and source.

| Sound kind | Approximate radius | What it tells the Drifter |
| --- | --- | --- |
| Footsteps, jumping, landing | 16 | Something is moving there |
| Block broken or placed | 16 | Someone is building or mining there |
| Chest, door, trapdoor opening | 16 | Someone is at a container or entrance (big deal near its base) |
| Eating, drinking | 8 | Someone is resting or healing |
| Projectile landing, arrow hit | 16 | Under fire, or combat nearby |
| Entity hurt | 16 | Fighting nearby |
| Explosion | 64 | Danger; may investigate later for loot |
| Creeper fuse | 8 to 16 | Immediate reflex (section 15), even without line of sight |
| Mob ambient sounds (groans, rattles, clicks) | 16 | Rough type and direction of nearby mobs |

- Hearing gives direction, rough distance and kind of sound, never which player. A creature's own sounds (a moo, a groan, a bone rattle, a spider's skitter, a creeper's hiss) tell it what kind of creature it is, as they would a player, but only roughly where: it fights only what it has seen, and turns to look.
- Sneaking players make no step vibrations, matching how sculk treats sneaking. Wool blocks muffle sounds that pass through them.
- A sound produces a look-toward reaction and, if repeated or suspicious, an investigation.

### Touch and body

- Knows the direction a hit came from and from what kind of source.
- Knows its own health, hunger, saturation (as "recently ate well" rather than an exact number), air, status effects, armor and tool durability, and inventory contents.
- Feels the block it stands on (magma, soul sand, powder snow, ice).

### Time and weather sense

- **Sky visible:** knows the time of day and weather.
- **Underground with no clock:** keeps an internal estimate that drifts by up to about 10% per in-game hour. It can surface to find it's darker than expected, which produces a very player-like scramble home.
- **Clock in inventory:** exact time anywhere; careful archetypes craft one early.
- **Position:** knows its exact coordinates and Y level, since players have the debug screen. This is a deliberate allowance, not a cheat.

### Seeing players

On a player it can see, the Drifter perceives: name, held item, armor material per slot, enchantment glint, whether they're sneaking, sprinting, eating, blocking or drawing a bow, and visible damage flashes. It never sees a player's health bar or inventory, so it estimates strength from gear and from how many hits a player has taken recently (section 14).

### Attention budget

- Track at most about 12 entities in detail at once, chosen by threat and salience. Everything else is background.
- Re-rank the tracked set every 10 ticks.
- Being hit, hearing a creeper fuse, or seeing a player always forces the source into the tracked set.

## 6. Memory

Memory has three tiers with different lifetimes, and every entry carries a confidence that fades. That fading is what keeps the Drifter fair (it doesn't know that someone mined its iron while it was away) and is a rich source of human-looking mistakes.

### Tiers

| Tier | Lifetime | Holds | Saved to disk? |
| --- | --- | --- | --- |
| Working | Seconds to about a minute | Tracked entities, recent sounds, current path, step of the current task | No |
| Short-term | Minutes to one in-game day | Mobs seen recently and where, areas mined today, dropped items, today's route, recent threats, which chest it just used | Yes, trimmed |
| Long-term | Persistent | Home, bed, base bounds, stations, chests, portals, resources, structures, danger zones, deaths, players, pets | Yes |

### Entry format

Every memory is a small record: kind, position and dimension, time recorded, confidence (0 to 1), how it was learned (seen, heard, inferred, told by another Drifter), and importance. Importance decides what survives when a category is full.

### Decay rules by kind

| Kind | Decay | Reason |
| --- | --- | --- |
| Mob position | Seconds | Mobs move |
| Player last seen | Minutes for position, permanent for identity | Position goes stale; who they are does not |
| Ore or resource sighting | Very slow | Blocks don't move, but someone else may mine them |
| Chest contents | Moderate, faster if players have been near the base | Others may have taken things |
| Danger zone | Days, renewed if the danger repeats | Fear fades unless reinforced |
| Death location | Fast for the item-recovery window, slow as a danger marker | Items despawn in 5 minutes; the lesson lasts |
| Home and stations | Never, but verified on arrival | If the bed is gone, that's an event |

### Long-term contents

- **Home:** base center and bounds, bed, respawn point, crafting tables, furnaces, enchanting table, anvil, brewing stand, farm plots, animal pens.
- **Storage:** each chest's position and a rough contents summary. Tidy Drifters remember precisely; messy ones remember only "it's in one of the three chests by the door," which produces the very player-like habit of opening several chests looking for something.
- **Resources:** ore sightings with type and count estimate, tree groves, sugar cane, clay, sand and gravel patches, villages, good mining entrances.
- **Places:** structures found (village, ruined portal, shipwreck, temple, mineshaft, stronghold), portals in both dimensions, landmarks it built (towers, pillars, torch trails).
- **Danger:** places it got hurt badly, nearly died, or died; lava lakes; mob spawners; deep dark areas; players' bases it fears.
- **Owned blocks:** a compact log of blocks it placed inside its base. Periodically sample this log against the world to detect missing or changed blocks, which feeds griefing detection (section 14).
- **Social:** one record per player and per other Drifter (section 14).
- **Pets and animals:** named pets, tamed wolves and cats, its horse.

### Consolidation at bedtime

Once per in-game day, when the Drifter sleeps or at dusk if it doesn't, short-term memories above an importance threshold are promoted to long-term, and the rest are dropped. This batches the expensive work into one moment per day and mirrors a player going "right, I found iron over there today."

### Mental map

- A coarse grid of the world, one tile per chunk or per 4x4 chunks, recording: explored or not, biome, danger score, resource score, and whether it's been mined out.
- Routes it has walked become known roads with a lower path cost, so it naturally reuses its own trails.
- The map drives exploration (go toward unexplored tiles in promising biomes) and base relocation (if the home region is mined out and dangerous).

### Capacity limits

Cap each long-term category (for example 64 ore sightings, 32 structures, 16 danger zones, 64 player records). When full, drop the lowest importance multiplied by confidence first. Ore sightings far from home and already low-confidence go first.

### Memory across death and departure

Memories survive death and survive logging off, just like a player's. A Drifter that died to a creeper in a ravine remembers that ravine, and one that returns after 10 days remembers who burned its house.

## 7. Needs and motivation

The Drifter decides what to do next by scoring a set of needs and picking among the goals that would satisfy them. This is utility AI: no single priority list, but needs that rise and fall with the situation, weighted by personality.

### Needs

| Need | Driven by | When it gets urgent | Satisfied by |
| --- | --- | --- | --- |
| Safety | Nearby threats, exposure at night, low health | A threat it can't beat is close | Flee, shelter, fight, pillar up |
| Health | Missing HP | Steeply below half health | Eat to regen, retreat, golden apple, potion |
| Hunger | Food level | Below 17 regen stops; at 6 it can't sprint | Eat, then cook, hunt, farm |
| Rest | Time since last sleep, night | After dark; very urgent after 3 nights without sleep (phantom fear) | Sleep in a bed |
| Shelter | Night with no safe place nearby | Dusk while far from home | Go home or build an emergency shelter |
| Gear | Gap between current gear and the next progression target | Moderate and steady | Mine, smelt, craft, loot, trade |
| Supplies | Stock of key consumables (torches, food, blocks, arrows, fuel) | Running out before a trip | Craft, gather, cook |
| Comfort | Base quality vs the personality's expectation | Slow build-up | Build, light, organize, decorate |
| Curiosity | Time since anything new was found | Builds when idle | Explore, look into sounds, visit structures |
| Social | Time since contact with a liked player or Drifter | Only for sociable personalities | Approach, follow, gift, greet |
| Greed | Valuables held vs personality's appetite | Grows with Greed trait | Mine valuable ores, loot, raid (hostile types) |
| Vengeance | Active grudges | After serious wrongs | Stalk, ambush, grief back (if allowed) |
| Territory | Intruders near base | When someone is inside its bounds | Watch, warn, chase off, attack |

### Scoring a goal

Each candidate goal is scored roughly as: need urgency times personality weight times feasibility times a risk factor, plus a commitment bonus if it's the current goal, plus a little random jitter.

- **Urgency** comes from a response curve per need: linear, squared (slow then sudden), logistic (switch-like) or stepped. Hunger is roughly flat until 14 and steep below 6; Safety is logistic around the point where it would lose a fight.
- **Personality weight** comes from traits (section 17). A Curious Drifter's exploration score is higher; a Cautious one's safety curve kicks in earlier.
- **Feasibility** is 0 to 1: does it have the tools, does it know where to go, can it get there before dark.
- **Risk** lowers the score in proportion to the Caution trait: a mining trip at night scores lower for a cautious Drifter than for a reckless one.

### Picking among the top goals

The Drifter doesn't always pick the single best goal. It picks among the top three with a weighted random choice whose sharpness depends on the Efficiency trait. Efficient Drifters behave predictably; scatterbrained ones wander off to chase a sheep mid-task, which is very human.

### Commitment and interruption

Without commitment, utility AI flip-flops between goals every second, which is the fastest way to look like a robot.

- **Commitment bonus:** the current goal gets a flat bonus (about 15% of the max score).
- **Minimum commit time:** each goal type has one (for example 2 minutes for a mining trip, 30 s for a smelting batch).
- **Atomic actions finish first:** a block break, a craft, an eating animation or a placement completes before any switch, unless a reflex fires.
- **Interrupt classes:**
  - **Reflex:** instant, bypasses scoring (falling, lava, creeper fuse, drowning). Section 15.
  - **Urgent:** switches now (being attacked, health below the flee threshold, night falls while exposed, a hostile player detected).
  - **Normal:** switches at the next safe checkpoint (end of a tunnel branch, inventory full, task step done).

### Re-scoring cadence

Re-score once per second, plus immediately on events: taking damage, seeing a player, a need crossing a threshold, a task finishing or failing.

### Goal catalog

The top-level goals the scorer chooses between:

- Survive (flee, heal, hide)
- Fight (a mob or a player)
- Eat
- Sleep
- Shelter for the night
- Gather a resource
- Craft an item
- Mining trip
- Smelt a batch
- Tend farm, breed animals
- Build or extend a project
- Base upkeep (light, repair, organize chests)
- Explore
- Loot a structure
- Trade with villagers
- Enchant, repair, brew
- Nether expedition
- Social (approach, greet, follow, give, watch, stalk)
- Recover items after death
- Idle and relax (mannerism-heavy downtime)

## 8. Decision architecture

The brain is six layers stacked from slow and abstract to fast and physical, with a reflex path that skips the middle when something dangerous happens. Each layer runs at its own rate and only talks to its neighbors.

&#91;embedded content: Drifter decision layers · 9 parts, 1 fast path\]

Plans flow down one layer at a time; reflexes read perception directly and take over the body for a few ticks before handing it back.

### The layers

1. **Strategist** (every 5 to 30 s, and on milestones). Holds the long view: the next progression target, the base project queue, and today's agenda. Outputs intentions such as "get full iron armor" or "build a wheat farm," which raise the scores of related goals.
2. **Selector** (1 Hz plus events). The utility scorer from section 7. Picks the current goal from needs plus the strategist's intentions.
3. **Planner**. Turns a goal into an ordered list of tasks by working backwards from what's needed (detailed below).
4. **Executor** (every tick). Runs tasks as small state machines that report running, success or failure, each with a timeout.
5. **Reflexes** (every tick, highest priority). Hazard and combat micro-reactions that take over the body for a few ticks, then hand it back (section 15).
6. **Motor**. Turns "go there" and "look at that" into human-like inputs: virtual movement keys and a camera model (section 16). Mannerisms feed in here too, at the lowest priority.

### The planner

The planner backward-chains from a goal item through a graph of ways to get things.

- **Example:** iron pickaxe needs 3 iron ingots, 2 sticks and a crafting table in reach. Iron ingots come from smelting raw iron, which needs a furnace and fuel. Raw iron comes from mining iron ore with a stone pickaxe or better, at a known location or by searching at a good Y level.
- **Alternatives:** fuel can be coal, charcoal, planks or logs; a crafting table can be one at home or a new one placed on the spot. Each alternative carries a cost estimate (time, distance, risk, resources spent), and the cheapest wins.
- **Using what it owns:** if a chest at home holds iron, "walk home and take it" usually beats "mine more." This is why memory of chest contents matters.
- **Output:** an ordered task list with checkpoints, for example: go home, take coal, place furnace, smelt 3 raw iron, craft sticks, craft pickaxe.
- **Replanning:** any task failure, a big new memory (found diamonds), or a goal change triggers a replan from the current state.

### Acquisition rules

The planner's knowledge of "how to get item X" lives in data files, not code:

- Crafting and smelting come from the game's own recipe manager.
- Mining (which block drops what, which tool tier is needed), mob drops, crop harvests, trading and loot sources come from a curated table.
- The Knowledge trait limits which entries a Drifter knows. A Newbie archetype doesn't know how to make an enchanting table until it finds one in a village or a library; a Veteran knows everything. Players don't know every recipe either.

### Executor task primitives

| Task | What it does | Typical failure |
| --- | --- | --- |
| MoveTo | Path to a position or entity (section 12) | No path, stuck, too dangerous |
| Mine | Break one block with the best tool on hand | Wrong tool tier, block changed, out of reach |
| Place | Place a block against a face | No valid face, obstructed, no block in inventory |
| Craft | Craft at the 2x2 grid or a table in reach | Missing ingredients, no table |
| UseOn | Use an item on a block (bucket, flint and steel, bone meal, hoe) | Target changed |
| UseItem | Eat, drink, draw bow, raise shield | Interrupted by damage |
| Attack | Swing at an entity | Out of reach, target lost |
| Container | Open, take, deposit, close | Container gone, full |
| Equip | Switch hotbar slot, swap armor, move to offhand | Item missing |
| LookAt | Turn head through the camera model | None |
| Wait | Wait for a furnace, a crop, daylight | Timeout |

Every task tells the motor layer which body parts it is using (head, hands, legs). Mannerisms can only use the parts that are free, so a camera sweep never happens mid-swing.

### Failure handling

- Every task has a timeout of about 3x its expected time.
- Failed targets go on a short blacklist (a few minutes) so it doesn't retry the same unreachable ore forever.
- Repeated failures raise Frustration (section 17), which changes behavior: muttering mannerisms, riskier shortcuts, or giving up and doing something else.

### Performance trick: plan from memory, not the world

Because every decision layer reads only the Drifter's own memory, the strategist, selector and planner can run on a snapshot of that memory without touching the world. That makes them safe to time-slice or move off the main server thread. Only perception, pathfinding and the executor touch the live world, and those are budgeted per tick (section 22).

## 9. Progression ladder

A Drifter's stage is computed from what it owns (inventory plus home storage plus placed stations), never stored as a flag. That way looting a shipwreck naturally skips a stage, and dying naked naturally drops it back down while its base and knowledge keep it from starting over completely.

| Stage | Name | Main goals | Done when | Typical timing |
| --- | --- | --- | --- | --- |
| 0 | Arrival | Look around, pick a direction with trees | Trees within reach | First minute |
| 1 | Wood | 10 to 20 logs, planks, crafting table, sticks, wooden pickaxe | Wooden pickaxe and a few cobblestone | Day 1 morning |
| 2 | Stone | Stone pickaxe, axe, sword, shovel; furnace; a stack of cobblestone | Stone tools and a furnace | Day 1 midday |
| 3 | First night | 2 to 4 animals of food, a shelter, a bed if 3 sheep of one color are around, coal or charcoal | Survived night 1 with food | Day 1 dusk |
| 4 | Settling | Torches, a chest, a home site chosen, spawn set | Home and a chest exist | Days 1 to 2 |
| 5 | Iron | Iron pickaxe, then water bucket, shield, armor pieces, shears, flint and steel | Iron pickaxe, bucket, shield, 2+ armor pieces | Days 2 to 4 |
| 6 | Settlement | Wheat farm, animal pen, organized storage, walls and lighting, full iron armor | Farm, pen, full iron armor | Days 3 to 7 |
| 7 | Diamond | Branch mine at deepslate depths; diamond pickaxe, then sword, then armor over time | Diamond pickaxe and sword | Days 5 to 12 |
| 8 | Enchanting | Obsidian, books (sugar cane and leather), enchanting table, bookshelves, lapis, anvil | Enchanted tools and armor | Days 8 to 15 |
| 9 | Nether | Portal, a gold armor piece, blaze rods, quartz, ancient debris, netherite with a found upgrade template | Blaze rods; netherite if it gets lucky | Day 12 onward |
| 10 | End (optional) | Ender pearls, eyes of ender, stronghold, end portal; dragon and elytra behind config | Off by default | Late |

### Within-stage priorities

The order players actually craft things in, which the strategist follows unless the situation says otherwise:

- **Iron:** pickaxe, bucket, shield, chestplate, leggings, helmet, boots, then shears and flint and steel. Full iron armor takes 24 ingots, so armor is spread over several trips.
- **Diamond:** pickaxe first (needed for obsidian), then sword, then chestplate, leggings, helmet, boots. Some archetypes make an enchanting table before any armor.
- **Food:** raw meat to cooked meat to bread to steady farm food to golden carrots.

### Pacing by archetype

- **Speedrunner:** rushes Iron, skips most of Settlement, goes to the Nether by day 3 to 5.
- **Builder:** reaches Settlement and lingers there for many days, extending the base.
- **Miner:** jumps to Diamond fast, light on base and farming.
- **Homebody / Farmer:** strong Settlement, reaches Diamond slowly, rarely goes to the Nether.
- **Nomad:** never really settles; carries a bed, builds a new camp every few nights.
- **Raider:** skips Settlement; gets gear mainly by looting structures and players.

### Regression after death

On death the Drifter drops everything. Its stage recomputes from home storage, so a Drifter with a stocked base re-equips from its chests and is back in business within minutes, while one that carried everything on it falls back to Stage 1 (with all its knowledge and its base intact). This is exactly how players experience it.

### Opportunistic jumps

The strategist always prefers a cheaper source if one is known: a village blacksmith chest, a shipwreck's iron, a ruined portal's gold and obsidian, a dead player's drops, or a trade. Finding a chest of diamonds can skip most of Stage 7.

## 10. Activity specs

Each activity below is a goal the planner can expand into executor tasks. For each one: how a player actually does it, the variations personality introduces, and the mistakes that make it look human.

### 10.1 Woodcutting

- **Tree choice:** the nearest reachable tree, biased toward the Drifter's favorite wood (habit fingerprint). Early on, it avoids trees on steep slopes or over water.
- **Order:** breaks the bottom log, steps into the gap, mines upward as far as reach allows. For trees taller than reach, it jumps onto the stump or pillars up with a dirt block.
- **Tidy Drifters** clear the whole tree, collect saplings and apples, and replant. **Messy Drifters** leave floating canopies and the occasional top log, the classic player-made eyesore.
- **Leaves:** often punches a few leaves to speed decay and grab saplings, or waits nearby doing something else.
- **Axe timing:** Efficient Drifters craft an axe before chopping in bulk; others punch the first 5 to 10 logs by hand.
- **Later:** builds a small tree farm near home and replants it each harvest.

### 10.2 Crafting

- **2x2 vs table:** uses the 2x2 grid for planks, sticks, crafting tables and torches; places a table for everything else.
- **Portable tables:** early on it crafts a table, places it, crafts what it needs, then either picks the table back up (Tidy, Efficient) or leaves it behind (Messy). Messy Drifters leave a trail of crafting tables across the map.
- **Batching:** always crafts extra sticks and torches; crafts in bulk for builds.
- **The inventory pause:** each crafting session takes a 0.5 to 2 s pause where it stands still with its head tilted slightly down, like a player with the inventory screen open. It won't craft while a threat is tracked unless it's reckless.
- **Recipe knowledge:** limited by the Knowledge trait (section 8).

### 10.3 Mining

Mining methods, chosen by stage, target and personality:

| Method | Used for | Shape | Risk |
| --- | --- | --- | --- |
| Surface quarry | Early cobblestone | Dig into exposed stone at the surface | Low |
| Staircase | Reaching a target depth | 2-high stairs, torches every 8 to 12 blocks | Low |
| Branch mine | Iron, diamonds, debris | Main tunnel with side branches every 3 blocks, 16 to 32 long | Low to medium |
| Caving | Fast early iron and coal | Follow caves, light as it goes, mine exposed ore | High |
| Ravine work | Exposed ores in ravines | Bridge along walls, block off drops | Medium |
| Straight down | Only reckless Drifters | 1x1 hole | High |

- **Caving first (user's decision, session 2):** since 1.18 caves are the main source of iron, so caving is the default
  way to find ore for everyone except strip miners (the mining style habit). It goes deep, lights the way with torches
  as breadcrumbs, mines exposed ore, fights cave mobs, loots mineshafts and dungeons it finds (section 10.11), and turns
  back on low health, food, torches or pickaxe, or the time; what it sees stays in memory, so caves become routes it
  knows. Personality sets how deep and how far (braver goes further; cautious turns back sooner). A staircase that
  breaks into a cave turns into caving. Strip miners plug caves and branch mine, but still go for ore they see.
- **Water drops:** water stops all fall damage from any height. Brave Drifters plan drops of up to 32 blocks into water
  they can see; careful ones keep to about 8.
- **Target depths (1.18+ ore distribution):** coal high in mountains and hills, copper in the middle heights, iron in a band around the low teens and again high in mountains, lapis around Y 0, gold around Y -16, redstone and diamonds near the bottom of the world, diamonds best around Y -58. **Decision (session 2):** it branch
  mines for diamonds around Y -51, just above the lava level (below Y -54 caves are full of lava, so a tunnel there
  breaks into lava lakes); the odds are still good there.
- **Tunnel shape is a habit:** 1x2 (most), 2x2 (comfort-lovers) or 3x3 (Builders). Branch spacing is a habit too.
- **Veins:** always finishes a visible ore vein before moving on.
- **Falling blocks:** after breaking a block under gravel or sand, steps back and waits; when suffocating, digs itself out.
- **Lava:** if a broken block reveals lava, reflexively places a block into the opening or backs away (section 15). Carries a water bucket from Stage 5.
- **Water:** blocks flowing water with the nearest junk block.
- **Spawners:** surrounds with torches or breaks them, then loots the chest.
- **Torch placement:** whenever the area ahead gets dark, always on its favorite side (habit fingerprint). Some Drifters use the classic "torches on the right going in" rule so the way out is on the left.
- **Torches as way markers** (user's rule): like players, it uses torches in caves, tunnels and at night to keep track of where it is and how to get back: always on the same side going in, so on the way out they're on the other side, and a line of torches leads home.
- **Inventory full:** throws junk per its policy (section 10.18) or heads home. Cautious Drifters go home early when carrying diamonds.
- **Tools:** uses Fortune on coal, lapis, redstone and diamonds; Silk Touch on glass, ice, ender chests. Thrifty Drifters avoid using a diamond pickaxe on plain stone. **In practice (session 2):** with a stone pickaxe along,
  it uses that on plain rock and keeps the better one for ore (only very efficient Drifters don't bother); deep trips
  take a crafting table and sticks so a broken pickaxe can be replaced from the rock it mines.

### 10.4 Smelting

- **Fuel priority:** lava bucket for big batches (100 items), coal or charcoal (8 items), blaze rods (12), dried kelp blocks (20), planks and logs (1.5) early.
- **Charcoal:** when no coal is known, smelts logs into charcoal first.
- **Timing:** a furnace smelts one item per 10 s; blast furnaces and smokers take half that for ores and food.
- **Waiting:** Patient or idle Drifters stand at the furnace and fidget (jump-spam, hotbar scrolling, crouching). Efficient ones load it, leave for a nearby chore, and come back.
- **Late game:** several furnaces side by side, loads split evenly; blast furnace for ores, smoker for food.

### 10.5 Food: hunting, cooking, eating

- **Hunting:** targets adult passive animals. Long-term thinkers leave at least two of each species near home for breeding; Greedy or Raider types kill everything.
- **Chasing:** chickens and fleeing pigs make it chase clumsily, sprint-hit, overshoot and turn around, just like a player.
- **Cooking:** furnace, smoker, or a campfire on long trips.
- **Eating policy:** each Drifter has its own threshold (habit fingerprint), roughly between food 12 and 17. It picks the food that fills without waste (no steak at 19 food), eats before mining trips and fights, and only eats rotten flesh or raw meat when nothing else is available.
- **Stockpile:** aims for 16 to 32 cooked food before long trips.
- **Fishing:** at dusk, in rain, or when bored near water, sits with a rod. Good idle activity that also yields treasure.

### 10.6 Farming

- **Plot:** a 9x9 field with a water block in the center (water hydrates farmland 4 blocks out), fenced or walled, lit to stop mob spawns.
- **Seeds:** wheat seeds from breaking grass; carrots and potatoes from villages or zombie drops; beetroot from village farms.
- **Harvest:** at full growth, replanting immediately. Impatient Drifters bone meal crops using skeleton bones.
- **Trampling:** a careful Drifter never jumps on farmland; a Newbie occasionally does and tramples a crop (a deliberate mistake).
- **Other crops:** sugar cane next to water (for paper and books), pumpkins and melons, sweet berries in taiga.

### 10.7 Animals and pets

- **Breeding:** wheat for cows and sheep, carrots for pigs, seeds for chickens; respects the 5-minute cooldown.
- **Bringing animals home:** walks backward holding wheat so animals follow, the classic player move; leads if it has them.
- **Pens:** fenced with a gate. Tidy Drifters always close the gate; Messy ones leave it open sometimes and lose animals.
- **Sheep:** shears for wool (beds, decoration).
- **Pets:** tames wolves with bones and cats with raw fish; names them with name tags from loot, drawn from its personal name list.
- **A pet's death** lowers mood for days; if a player killed it, that player gets a severe grudge (section 14).
- **Horses:** tames by repeated mounting, saddles from loot, uses them for long trips and leaves them tied at home.

### 10.8 Exploration

- **Purposes:** find a better home site, a village, specific biomes or resources, structures for loot, or simply curiosity.
- **Route style:** follows rivers, coastlines and ridgelines; climbs high points (or pillars up) to look around before choosing a direction.
- **Marking:** torch trails, cobblestone pillars, or signs, depending on habit. Nomads mark nothing.
- **Distance limit:** usually turns back by the time it would need to camp, unless it's carrying a bed.
- **On the way:** grabs saplings, flowers, seeds, sugar cane and anything shiny it sees.
- **Relocation:** if the home region is mined out or too dangerous, it may move its base, carrying its chests' contents in trips.

### 10.9 Travel

- **Pace:** like players, most trips (about 80%) are sprint-jumped the whole way when possible; the rest are sprinted. It slows down when it doesn't need to hurry (lounging, strolling through a village: it walks), when it shouldn't go fast (sneaking up on something, bridging, near a cliff edge: it sneaks), and when it can't (food 6 or lower, a low ceiling, a sharp turn, almost there, the deep dark or an ancient city: it walks or sneaks).
- **Cautious caves:** stops sprinting in dark or unknown areas.
- **Boats:** carries one from early on; uses it on any water wider than a few blocks.
- **Horses:** for long overland trips once it has one.
- **Gaps:** bridges with blocks while sneaking, or pillars to climb; descends cliffs by staircasing or jumping into deep water.
- **Ender pearls:** to cross gaps or escape (section 13).
- **Elytra:** late game, with firework rockets, if it ever gets one.

### 10.10 Water and swimming

- Swims at the surface for short hops and sprint-swims for longer ones.
- Tracks air; surfaces with a margin that depends on Caution (cautious ones come up at half air).
- Knowledge-gated trick: places a door or sign underwater to make an air pocket.
- Avoids deep ocean at night because of drowned, and leaves the water if it spots a drowned with a trident.
- The water bucket is its multi-tool: fall clutch, putting out fire, turning lava into obsidian or cobblestone, making farmland, crossing lava.

### 10.11 Looting structures

| Structure | Protocol | Signature mistake (low Knowledge) |
| --- | --- | --- |
| Village | Loots blacksmith and other chests; doesn't hit villagers or golems unless it's a strong hostile type | Hits a villager by accident and gets chased by the golem |
| Desert temple | Digs down beside the center or breaks the pressure plate first, then loots the four chests | Steps on the plate (very rare, and spectacular) |
| Jungle temple | Cuts tripwires with shears or steps over them | Trips the arrow dispenser |
| Shipwreck | Dives, breaks into the hull, manages air | Runs low on air and panics to the surface |
| Ruined portal | Takes gold and obsidian; may use the portal | Touches magma blocks |
| Mineshaft | Clears cobwebs with a sword or shears, torches the cave spider spawner | Gets stuck in webs near the spawner |
| Dungeon | Torches or breaks the spawner, loots chests (saddles, name tags) | Fights in the open instead of the doorway |
| Pillager outpost | Avoids unless aggressive and well-armed | Picks a fight too early |
| Stronghold | Careful of silverfish blocks; grabs library books | Breaks infested blocks repeatedly |
| Trial chambers (1.21) | Avoids by default; veterans may attempt the normal vaults with trial keys | Gets overwhelmed by a breeze |
| Bastion (Nether) | Veterans only, with full gold-armor awareness | Opens a chest near piglins and gets swarmed |

### 10.12 Trading

- **Villagers:** sells wheat, potatoes, paper or sticks for emeralds; buys what it needs (bread, armor, Mending books for high-Knowledge Drifters). It doesn't build trading halls (a v1 non-goal).
- **Piglins:** barters gold ingots in the Nether for ender pearls, fire resistance and string.
- **Players:** gifts and trades are part of the social system (section 14).

### 10.13 Enchanting, anvils and repair

- **Setup:** enchanting table with bookshelves around it at a one-block gap, up to 15. The table goes near home on flat
  ground with room round it for the bookshelves.
- **Obsidian (session 2):** the bucket method, two buckets: scoop lava from a pool, pour it into a one-block hole dug in
  solid ground away from the lava, pour water beside it, scoop the water back, mine the obsidian (it drops safely into the
  hole). Never water straight onto a lava pool (the obsidian would sit on lava and its drop would burn).
- **XP pickup:** orbs only fly to real players, so it walks over them like dropped items.
- **Choices:** takes the strongest offer it can afford, with tool-specific priorities: pickaxe (Efficiency, Fortune, Unbreaking), sword (Sharpness, Looting), armor (Protection), bow (Power).
- **XP:** gains it from mining ore, killing mobs and smelting; Efficient Drifters plan an XP trip before enchanting.
- **Anvil:** combines books, repairs favorite items, and renames them. Naming its sword is a signature mannerism, using its personal name list.
- **Mending:** if it has Mending gear, it keeps XP for repairs.

### 10.14 Brewing (Knowledge-gated)

- Needs a brewing stand (blaze rod) and nether wart (from fortresses).
- Brews fire resistance before Nether mining or bastions, healing and strength for fights, night vision for caving.
- Splash healing potions only for high-Skill Drifters.

### 10.15 Light management

- Lights its base inside and out so nothing spawns at light 0.
- Lights the caves and tunnels it uses repeatedly.
- Patrols for dark spots at night as a chore; Tidy Drifters light in neat patterns, others scatter torches.

### 10.16 Base chores

The things it does at home when nothing urgent is going on, often at night:

- Sorting chests, smelting, crafting torches, filling in creeper holes, replanting, feeding and breeding animals, repairing gear, extending a build project, and plain fidgeting.

### 10.17 Gear upkeep

- **Durability thresholds:** careful Drifters swap or repair at about 10% durability; reckless ones use tools until they break.
- **Spares:** keeps a spare pickaxe from Stage 2, a spare sword from Stage 5.
- **Upgrades:** equips better armor the moment it gets it, including from a dead player's drops.
- **Before trips:** crafts replacements and tops up consumables.

### 10.18 Inventory management

- **Hotbar layout:** each Drifter has a fixed layout (habit fingerprint). During downtime it moves items back to "their" slots, a very recognizable player tic.
- **Pickup filter:** picks up everything early; later ignores items on its junk list.
- **Junk policy:** what it throws when full. Most throw diorite, granite, andesite, tuff and extra gravel. Some keep one stack of dirt or cobblestone for bridging. Hoarders keep everything and run out of space constantly.
- **Emergency kit:** never discarded: water bucket (Stage 5+), 32 or more building blocks, food, torches, a weapon, a shield.
- **Going home:** when full, when carrying something precious, or before dark.
- **Chests:** Tidy Drifters sort by category and label chests with signs or item frames; Messy ones keep one overflowing dump chest; Hoarders build rooms of chests.
- **Late game:** shulker boxes for travel; an ender chest for valuables (cautious types).

## 11. Building system

The Drifter builds the way players build: an emergency hole on night one, a dirt hut, then a real house that grows over weeks. Blueprints are data-driven modules combined procedurally, and the visible build order (corners, walls, scaffold, roof) matters as much as the result.

### Shelter tiers

| Tier | When | What it looks like | Build time |
| --- | --- | --- | --- |
| 1. Panic shelter | Night falls far from home with nothing prepared | Pillars 10 to 20 blocks up and waits on top, or digs 2 down and covers the hole | Seconds |
| 2. Hillside dig-in | Night 1 near a hill | 1x2 entrance into stone, a 3x3 room, sealed with a block or a door | 30 to 60 s |
| 3. Starter hut | Days 1 to 3 | 7x7 box of whatever it has (dirt, cobblestone, planks), flat roof, door, bed, chests along a wall, table, furnace, torches; a smoker and a blast furnace on the back wall once it has iron (user, 2026-10-16: room for things without being packed; huts built before that stay 5x5) | A few minutes |
| 4. House | Stage 5 to 6 | An archetype blueprint in its palette (first version, session 2: a 7x7 box, walls 3 high with windows, flat roof, wood with log posts or stone or mixed by archetype; it moves its bed in) | One to several days of work |
| 5. Compound | Stage 6 onward | House plus farms, pens, storage, portal room, walls, paths, a signature feature | Grows forever |

### Site selection

Candidate sites are scored on: flatness, water within about 16 blocks, trees, passive animals, exposed stone or a cave nearby, biome preference, view (Builders like hilltops and coasts), defensibility (Cautious likes cliffs and hillsides), and distance from other bases. Sociable Drifters score sites near friendly players higher; hostile or hermit types score them lower. Opportunists may simply move into an empty village house.

### Blueprint grammar

A house is assembled from modules with parameters:

- **Modules:** foundation, corner pillars, walls, windows, door, roof, floor, interior zones (sleeping, storage, crafting, smelting, enchanting).
- **Parameters:** footprint size, wall height, roof style (flat, gabled stairs, hipped), symmetry, number of floors, window style, palette.
- **Templates:** fixed shapes (tier 1 to 3 shelters, towers, portal frames) are stored as structure templates; houses are generated from modules so no two are identical.

### Styles by archetype

| Archetype | Typical home |
| --- | --- |
| Newbie | Flat-roofed box of mixed blocks, a hole for a window, torches everywhere |
| Builder | Log-framed house with stair roofs, stripped logs, glass panes, a porch |
| Miner | Bunker carved into a mountain, stone everywhere, a staircase straight down to the mine |
| Farmer / Homebody | Farmhouse plus barn, big fenced fields, flowers |
| Nomad | Small wool tent or a lean-to, rebuilt every few nights |
| Raider | Fortified tower with a lookout, few windows, a wall |
| Hermit | Hidden base underground or in a cliff, entrance disguised |

### Palette

- Starts from the biome's wood and stone, plus the Drifter's favorite wood and stone (habit fingerprint), limited by what it has.
- Substitutes when it runs out (spruce becomes oak), which produces the patchy look of real survival builds. Tidy Drifters go gather the right block instead.

### Build order

1. Clear the footprint: chop trees inside it, fill small holes, flatten 1-block bumps.
2. Lay the foundation outline.
3. Corner pillars first, usually in logs.
4. Walls ring by ring, standing inside the building.
5. For height, scaffold with a dirt pillar or stand on top of the wall.
6. Roof.
7. Door, windows, lighting.
8. Interior: bed, chests, crafting table, furnaces, then decoration.
9. Cleanup: Tidy Drifters remove scaffolding; Messy ones leave the famous lone dirt pillar.

### Materials

- The planner counts blocks before starting.
- Patient Drifters gather everything first; impatient ones build until they run out, leaving half-finished walls while they go on material runs. Both are true to life.

### Placement rules

- Must stand within 4.5 blocks, place against an existing face, and have line of sight.
- Sneaks when placing at edges; bridges outward while sneaking.
- Never places a block where it would trap itself, except deliberately for shelter.

### Expansion queue

After the house, the strategist queues projects by archetype: storage room, crop farm, animal pen, enchanting room, Nether portal (framed and lit near the base), lookout tower, perimeter wall, paths connecting everything, perimeter lighting, decoration (flower pots, banners, item frames), and one signature feature from its habit fingerprint.

### Repair

The owned-blocks log (section 6) lets it notice missing blocks. Creeper holes get filled with the original block if it has it, or a mismatched one if it doesn't.

### Respecting other people's builds

The Drifter can't tell a natural block from a player's build by looking, so the mod should track it: keep a lightweight per-chunk record of positions where players placed blocks. A Drifter never breaks a player-placed block unless it is hostile toward that player and player-griefing is enabled in config (section 23). Areas around players' beds and spawn points get an extra protected radius.

## 12. Navigation and pathfinding

Vanilla mob pathfinding can only walk, jump and swim; a survival player also digs through walls, bridges gaps and pillars up. The Drifter needs its own pathfinder in the style of player-automation bots like Baritone: an A\* search where breaking and placing blocks are moves with costs.

### Move types

| Move | Conditions | Cost basis |
| --- | --- | --- |
| Walk / sprint (straight or diagonal) | Solid floor, 2 blocks of headroom | Distance over speed |
| Jump up 1 | Block in front, headroom | Small time cost plus hunger |
| Step down 1 to 3 | Landing is safe | Small |
| Drop 4+ | Water or another soft landing below, or acceptable fall damage | Fall damage counts as risk |
| Parkour jump | Gap up to about 3 to 4 blocks with sprint-jump | Risk scaled by Skill |

Built (2026-10-17, user: "lets upgrade pathfinding, starting with parkour jumps"): jumps straight or at any angle (diagonals, corners, knight's moves) over 1 to 3 block gaps (up to about 4.3 blocks out), landing level, a block up or up to three down, only if a tick-by-tick run of the player's own jump physics (`nav/JumpPhysics`) carries that far with a margin (wider for the less skilled; 3-block gaps only for Skill 0.35+), over seen ground only, never over lava; a miss over a drop that would hurt costs risk x Caution and needs more room. It takes the gentlest jump that makes it (a hop from the edge at a walk, a walking run-up, a sprint), backing up for a run-up if it's standing at the edge, and never one that would sail past a one-block landing.
| Swim | Water | Slow, plus air risk |
| Climb | Ladder, vine, scaffolding | Slow |
| Door / gate | Openable | Small; Tidy closes it behind |
| Bridge | Air below the next step, a block in inventory | Placement time plus one block |
| Pillar up | Headroom above, a block in inventory | Jump plus place, plus one block |
| Dig through | Breakable block it has the tool for | Mining time plus durability |
| Dig down (stair) | Breakable floor | Mining time; straight-down digging only allowed for reckless Drifters |
| Boat | Water body larger than a few blocks, boat in inventory | Fast, but placement and pickup overhead |
| Ride | A horse it owns | Fast over open land |

### Cost model

Each move's cost is time in seconds, plus risk times the Caution trait, plus resources spent times the Thrift trait.

- **Risk sources:** adjacent lava, height and exposed edges, darkness, known mob positions, unknown territory, deep water at night, the danger zones in memory.
- **Discounts:** known roads it has walked before, its own tunnels and bridges.
- **Hard bans:** never through lava, never through its own farmland, never through player-placed blocks unless hostile and allowed (section 11).

### Hierarchical planning

1. **Long range:** plan over the coarse mental-map tiles (section 6) to get a chain of waypoints.
2. **Local:** A\* within a 32 to 64 block window around the Drifter, toward the next waypoint.
3. **Rolling replan:** re-plan the local window as it moves and as perception reveals new terrain.

Unknown terrain is assumed to be passable at a moderate cost. When reality disagrees (a ravine where it expected land), it replans.

### Following the path like a player

- Movement is fed through virtual keys (forward, strafe, jump, sneak, sprint) into the same movement physics players use, so momentum, strafing and jumping look right.
- Cuts corners imperfectly: slight overshoot on turns, occasional wall bumps, scaled down by Skill.
- Looks ahead along the path, not at its feet, with glances to the sides (section 16).
- Sprints on long straight segments; sneaks near edges and on bridges.

### Chasing and following entities

Re-paths every 10 ticks toward a predicted position (where the target will be in about half a second), not its current position.

### Getting stuck

| No progress for | Response |
| --- | --- |
| 3 s | Jump, strafe sideways, try again |
| 10 s | Replan using other move types (dig through, pillar, bridge) |
| 30 s | Blacklist the target for a few minutes, raise Frustration, pick another goal |

### Doors

Opens doors and gates by interacting, never by breaking them. Tidy Drifters close them behind themselves; Messy ones leave them open by day. At night, every Drifter closes its own door.

### Performance

- Cap nodes per search (a few thousand for local windows).
- Time-slice: a shared per-tick budget for all Drifters' pathfinding (for example 1 to 2 ms), with searches resuming next tick.
- Cache recent paths and reuse their unspent tails.
- Drifters far from players path coarsely (section 22).

## 13. Combat against mobs

The Drifter fights with player mechanics (attack cooldown, crits, shields, knockback) and player tactics (doorways, pillaring, backing off creepers). Whether it fights at all comes from comparing its own strength to the threat, so a fresh Drifter runs from a skeleton that a geared one would walk through.

### Threat assessment

- **Threat** = sum over tracked hostiles of: base danger for the mob type, times gear modifiers (an armored zombie, an enchanted skeleton bow), times a proximity factor.
- **Power** = health and absorption, armor points and toughness, weapon damage per second, shield, available healing (food, golden apples, potions), and an escape option (pearls, a known shelter close by).
- **Decision** comes from the power-to-threat ratio, shifted by Bravery and Caution:

| Ratio | Decision |
| --- | --- |
| Well above 2 | Engage directly |
| 1.2 to 2 | Engage with terrain advantage (doorway, high ground) |
| 0.8 to 1.2 | Ranged if it has a bow, otherwise avoid and path around |
| 0.5 to 0.8 | Retreat toward home or light, fight only if cornered |
| Below 0.5 | Flee or hide immediately (pillar up, block in, pearl away) |

### Fighting styles

Each Drifter leans toward one style (from personality), but uses the others when the situation demands.

- **Brawler:** runs in and trades hits; uses sprint-knockback.
- **Duelist:** controls spacing, waits for full cooldown, times crits, strafes.
- **Archer:** keeps distance, opens with arrows, switches to melee only when closed on.
- **Turtle:** shield up most of the time, hits in the gaps.
- **Coward:** disengages early, fights only cornered.

### Melee mechanics

- **Spacing:** hovers at the edge of reach (about 2.5 to 3 blocks), steps in to hit and out after.
- **Cooldown discipline:** high-Skill Drifters swing at full cooldown; low-Skill or panicked ones spam-click.
- **Crits:** jumps and times the swing to land while falling, with success rate scaled by Skill.
- **Sprint reset (W-tap):** sprint-hits for extra knockback, releases forward for a moment, sprints again.
- **Strafing:** circles the target, changing direction at random intervals of 0.5 to 2 s.
- **Sweeps:** lines up groups so a sword sweep hits several.
- **Shield:** raises it when a hit is coming (melee wind-up in reach, arrow on the way, creeper about to blow), drops it to attack. The 0.25 s raise delay means bad timing gets punished, as it should.
- **Axe vs shield:** against a shield user (players, mainly), swaps to an axe to disable it, then back to the sword.

### Ranged mechanics

- **Bow:** full draw unless the target is very close; leads moving targets by their velocity times arrow flight time; aims above for distance; aim error shrinks with Skill and grows with distance and target speed.
- **Moving while drawing:** strafes slowly while drawing, like players do.
- **Crossbow:** Veterans keep a loaded crossbow in the hotbar as an opener.
- **Trident:** throws it if it has one with Loyalty; otherwise uses it in melee.

### Defensive tools

- Pillar up two or three blocks to get out of zombie reach and fight from above.
- Wall off with blocks; close doors.
- Water bucket to push mobs back or stop fire.
- Golden apple at low health; totem of undying in the offhand in the late game.
- Ender pearl to escape.
- Eats in a safe pocket mid-fight.

### Terrain sense

- Doorways and 1-wide corridors, so enemies come one at a time.
- High ground against melee mobs.
- Low ceilings (2 blocks) against endermen.
- Never fights near lava or a cliff edge if it can help it; backs into light at night.

### Per-mob protocols

| Mob | Protocol |
| --- | --- |
| Zombie, husk | Sprint-hit and back off; pillar up if swarmed; watch for baby zombies (fast, hard to hit) |
| Drowned | Leaves the water; bow from land; flees tridents in water |
| Skeleton, stray, bogged | Zigzag approach or shield-walk in, then melee; breaks line of sight around corners; strays slow, bogged poison |
| Creeper | On fuse sound: sprint backward immediately. Otherwise hit once with sprint-knockback, back off, repeat; or bow it; shield up if one is about to blow |
| Spider | Melee; aware they climb walls and are neutral in daylight |
| Cave spider | Torches or breaks the spawner fast; fights in cobweb-free space |
| Enderman | Never looks at its head unless it intends to fight; if fighting, goes under a 2-high ceiling or stands in water |
| Witch | Closes distance fast or uses a bow; drinks milk if poisoned and it has milk |
| Slime, magma cube | Fights in the open, clears the small ones, stays out of corners |
| Phantom | Fights from under a roof or shoots them; the real fix is sleeping (section 18) |
| Silverfish | Stops breaking stone, retreats, fights in a corridor |
| Pillager | Bow back or charge with a shield up; avoids patrols when weak |
| Vindicator | Respects the axe damage: shield up, back off, bow |
| Evoker | Charges it directly, dodges fangs by stepping sideways, kills vexes when they come |
| Ravager | Avoids unless very strong; bow from height |
| Guardian | Avoids ocean monuments entirely in v1 |
| Blaze | Shield against fireballs, fights from behind cover, fire resistance if it has it |
| Ghast | Hits the fireball back or shoots the ghast; blocks portals in cobblestone |
| Wither skeleton | Fights only with good armor and a sword; avoids wither damage by keeping distance between hits |
| Piglin | Wears gold armor to stay neutral; never opens chests or mines gold near them |
| Piglin brute | Avoids completely unless a veteran |
| Hoglin | Avoids or uses warped fungus to repel; veterans fight for food |
| Zombified piglin | Never hits one (the whole group turns hostile) |
| Warden | Never fights. Freezes, sneaks away, or leaves the area. Avoids sculk shriekers entirely |
| Breeze (1.21) | Avoids; veterans use melee timing when it lands |

### Retreat and disengagement

- Flee threshold is health between 30% and 50%, by Caution and Bravery.
- Retreats toward home, light, or a remembered safe spot; pillars or blocks itself in if pursued.
- Eats as soon as it's out of reach; returns to the fight only if the reason it started is still there.

### After combat

- Scans the area before relaxing (a nervous camera sweep).
- Collects drops and XP.
- Eats to top off health.
- Checks armor durability, swaps damaged pieces.
- Celebrates in its own way: crouch-spam, a swing at the air, or nothing (section 16).

### Mobs targeting the Drifter

All of the above depends on hostile mobs actually hunting the Drifter like a player (section 2). Without that, nights are free and none of this tension exists. Keep it on by default.

## 14. Disposition toward players (neutral and hostile)

Every Drifter keeps a separate relationship with every player, built from two numbers, Trust and Fear, and pushed around by what the player does. Its behavior mode toward that player falls out of those numbers plus its temperament. Hostility is therefore earned, readable and personal: a Drifter can be friendly to you and hunting your friend.

### The relationship record

| Field | Range | Meaning |
| --- | --- | --- |
| Trust | -100 to +100 | Like or dislike |
| Fear | 0 to 100 | How dangerous it believes this player is |
| Familiarity | 0 to 100 | How much time it has spent around them |
| Grudges | List of events | Each with severity and decay; feeds Vengeance |
| Debts | List of gifts and favors | Feeds reciprocity |
| Strength estimate | Rolling | From gear seen and fights observed |
| Known base | Position | If it has seen where they live |
| Last seen | Time and place |  |

### Temperament (from personality)

- **Stranger baseline:** starting Trust for a new player (for example -20 for a wary hermit, +20 for a sociable one).
- **Aggression:** how readily low Trust turns into attacking.
- **Vengefulness:** how much grudges weigh and how slowly they fade.
- **Forgiveness:** how fast Trust drifts back toward baseline per day.
- **Greed:** whether a weak, well-geared player looks like a target.
- **Bravery:** how much Fear suppresses hostility.

### Disposition archetypes

| Archetype | Toward a stranger | Turns hostile when |
| --- | --- | --- |
| Pacifist | Avoids, never initiates | Only defends itself, then flees |
| Wary Neutral (default) | Watches, keeps distance, goes about its work | Attacked, robbed, or its base is damaged |
| Friendly | Approaches, greets, may gift | Seriously wronged |
| Territorial | Ignores outside its base, warns inside | A player stays in its base after a warning |
| Opportunist | Neutral, but sizes players up | The player is weak, distracted, or alone at night |
| Bandit | Stalks players for loot | Almost always; still telegraphs first contact |
| Grudge-holder | Neutral | Any wrong; never forgets |

### Events that move the numbers

| Player action | Trust | Fear | Notes |
| --- | --- | --- | --- |
| Hits the Drifter once, then stops or crouches | -10 | +5 | Read as an accident if followed by crouching or backing off |
| Attacks it repeatedly | -40 | + damage-scaled | Opens a grudge |
| Nearly kills it | -60 | +30 | Severe grudge |
| Kills it | -80 | +40 | Remembered after respawn or return |
| Kills its pet | -80 |  | Severe grudge, even for Pacifists |
| Breaks its blocks | -5 each, up to -50 |  | Detected via the owned-blocks log |
| Opens its chests | -10 |  | Heard or seen |
| Takes valuables from its chests | -20 per valuable stack |  | Detected on its next chest check |
| Caught taking from its chests (it watched them at the chest, then checked it at once and things were gone) | -45 |  | Grudge 60: hostile. After seeing anyone at one of its chests it goes and checks it once they step away (user, 2026-10-16) |
| Seen wearing or holding something missing from its chests (within 3 days) | -40 |  | Grudge 50: taken for the thief whether they took it or not; an assumption, and sometimes a mistake (user, 2026-10-16) |
| Kills its farm animals | -15 |  |  |
| Fire or lava near its base | -40 | +10 |  |
| Aims a drawn bow at it | -5 | +5 |  |
| Sneaks up on it | -5 |  | Suspicion |
| Gives it a gift | +5 to +30, by value |  | An item dropped while facing it, which it picks up |
| Crouch-spams at it | +3 |  | Capped per day |
| Kills a mob that was attacking it | +20 |  |  |
| Saves it from death | +30 |  |  |
| Spends time nearby peacefully | small, slow |  | Raises Familiarity |
| Kills a Drifter it was friends with | -30 | +20 | News spreads within its group |
| Wears much better gear than it |  | +10 to +30 | Strength estimate |

### Behavior modes toward one player

1. **Unaware:** hasn't detected them.
2. **Watching:** detected; turns to look, pauses, then decides.
3. **Avoiding:** keeps out of their way, changes route, hides if Fear is high.
4. **Tolerating:** carries on working with an eye on them; stands in its doorway if they come close to the base.
5. **Friendly:** greets, may follow for a while, gives surplus items, helps against mobs that attack the player.
6. **Ally:** fights alongside them, lets them use its bed and chests, shares food, warns of danger.
7. **Warning:** the telegraph before any hostility (below).
8. **Defensive:** fights back only while attacked, disengages when they stop.
9. **Territorial:** attacks to drive them out of its base, stops at the boundary.
10. **Predatory:** stalks and attacks for loot.
11. **Vengeful:** hunts that specific player, can travel to their known base.
12. **Fleeing:** losing badly; runs, may drop a peace-offering item (config).

Mode comes from the Trust and Fear pair, shifted by temperament: high Trust gives Friendly or Ally; middling gives Tolerating; low Trust with low Fear gives Territorial, Predatory or Vengeful; low Trust with high Fear gives Avoiding, or an ambush once the player looks weak.

### The telegraph rule

The first hostile act toward a player who has never wronged this Drifter must be preceded by a visible warning: switching to a weapon, facing the player and holding still for 1 to 2 s, or drawing a bow without firing. If the player backs off, the Drifter backs off too. After hostility exists between them, no further warnings are owed. This is what keeps hostile Drifters fair and readable.

### Reading player strength

- Armor material per slot and enchantment glint.
- Weapon in hand.
- How many hits the player has taken recently and from what (a player who just fought three zombies is probably hurt).
- Whether they are eating or retreating.
- How many players are together.

It never sees health bars or inventories.

### Hostile tactics against players

- **Stalking:** follows 24 to 40 blocks back, out of sight, using cover; sneaks when close.
- **Waiting for an opening:** the player fighting mobs, low on health, mining with their back turned, crossing a bridge or water, standing still for a long time, or out at night.
- **Opener:** arrows or a crossbow bolt from cover, then closes in.
- **Duel:** full melee kit (crits, W-taps, strafing, shield timing, axe against shields).
- **Mobility:** ender pearls to close or escape; pillar up when losing; water bucket to stop falls or knock back.
- **Ambushes:** around a corner in a mine, at a portal exit, at the player's base entrance. Bed ambushes and spawn camping are banned.
- **Disengage:** retreats when losing and returns another day; the grudge stays.
- **Looting:** collects the player's drops, equips better gear, may keep a trophy item in a chest. Optional taunt (crouch-spam over the drops) behind a config flag.
- **Fire and lava against players:** off by default.

### Griefing (config-gated, off by default)

Only for Vengeful or Bandit Drifters with griefing enabled: breaking into chests, stealing valuables, lighting fires, breaking doors. Never touches ender chests, never does anything near a player's bed or spawn point protection radius.

**Outpost Bandits (user's decision, 2026-10-17): "automatically hostile, as griefer as possible", to players and to
other Drifters; a setting ("Outpost Bandits grief"), on by default.** A gang holding a pillager outpost attacks players
and non-gang Drifters on sight (no waiting for an opening, no first-contact telegraph beyond the raid's war cry), finds
bases by seeing them (a cluster of player-placed blocks, a player going to bed, another Drifter at its home; it scouts
round the outpost, toward where it last saw people), and about once a game day raids one as a gang: breaks the doors,
empties the chests, sets fires (flint and steel in its starting kit), smashes torches, crops, glass, stations and
walls, then goes home. Still never: ender chests, anything within 5 blocks of a bed, the server's spawn protection.

**Raiding chests (user's decision, 2026-10-15): on by default**, still by personality: Bandits raid any player chest they
find; any other Drifter only the base of a player it holds a real grudge against (Vengeful/Predatory toward them, base
known). Only when it sees no one near, never an ender chest, never within 5 blocks of a known bed, never inside the
server's spawn protection. `/drifter config raiding false` turns it off. Fires and breaking doors stay off for now.

### Friendly behaviors

- Greets with crouch-spam and a look.
- Gifts surplus (spare iron, extra food), especially after being gifted.
- **Barter:** if a player drops an item near it, it picks it up and drops back something it values about equally, using a simple item value table.
- **Follow me:** crouching twice near it, then walking away, invites it to follow for a while (Familiarity and Trust gated).
- Helps fight mobs that are attacking the player.
- Warns of danger by turning toward an unseen creeper and backing away.

### Non-verbal language

| Gesture | Meaning |
| --- | --- |
| Crouch-spam | Hello, thanks, laughing, celebration |
| Looking at an item, then at the player | Wants that |
| Dropping an item in front of the player | Gift or offer |
| Head shake (quick left-right yaw) | No |
| Nod (quick up-down pitch) | Yes |
| Looking at a spot, then walking toward it | Come here |
| Backing away while facing the player | Leave me alone |
| Weapon out, staring, still | Last warning |
| Jump-spam | Impatient |
| Spinning in place | Excited or confused |

### Optional chat lines

Short, lowercase, canned lines tied to events, never generated text: hi, gg, ty, nice, lol, rip, bruh, go away, stop, wait. Each personality gets its own small vocabulary and a strict rate limit. On by default, with a config toggle to turn them off.

**Its name in chat (user's decision, 2026-10-15):** a player who says a Drifter's name in chat within about 32 blocks gets
its attention: it hears it, turns toward the voice and looks for them; seeing someone it doesn't dislike, it nods (and
may answer "?" or "yes?"). Drifters still don't understand anything else players type.

### Forgiveness and multiplayer

- Trust drifts toward the stranger baseline each day by the Forgiveness rate; grudges above a severity threshold never fade for Vengeful Drifters.
- Relationships are per player. Optionally, players seen together share a fraction of each other's reputation ("you're with them, so I don't trust you either").
- On Peaceful difficulty, Drifters are never hostile to players.

## 15. Hazard reflexes

Reflexes are the fast path: they read perception directly, take over the body for a few ticks, then hand control back to whatever task was running. Each reflex has a human reaction delay of 150 to 400 ms (shorter at high Skill, longer if the hazard is behind it) and a success chance scaled by Skill, so a Newbie sometimes fails a water-bucket clutch and dies, which is exactly right.

### Reflex priority

When several fire at once, the higher one wins: falling into lava or the void, then drowning, then being in lava or fire, then a creeper about to explode, then a lethal fall, then incoming projectiles, then suffocation, then everything else.

### Catalog

| Hazard | Detected by | Reflex | Follow-up |
| --- | --- | --- | --- |
| Lethal or heavy fall | Fall distance and the block below | Water-bucket clutch about 2 to 3 blocks above landing, then picks the water back up | Shaken mood; marks the spot |
| Lava revealed while mining | Block broken next to lava, or flowing lava seen | Places a block into the gap, steps back | Marks a danger zone; reroutes |
| Standing in lava | Status | Jumps and moves to the nearest safe block; fire resistance potion or water bucket if it has them | Eats, heals, may go home to re-gear |
| On fire | Status | Water within 4 blocks or places water; otherwise keeps moving away from the source |  |
| Low air | Air under 30% | Swims up, or digs up, or places a door for an air pocket (Knowledge-gated) |  |
| Suffocating | Head inside a block (gravel or sand fell) | Mines out up or forward | Steps out of the column next time |
| Creeper fuse | Hissing heard or a swelling creeper seen within 4 blocks | Sprints backward away from it; shield up if it can't get clear | Fills the crater later if near base |
| Lit TNT | Seen or heard | Runs more than 8 blocks away |  |
| Arrow or fireball incoming | Projectile on an intercept path | Shield up, or strafe sideways | Finds the shooter |
| Ghast fireball | Projectile seen | Hits it back (high Skill), otherwise dodges behind cover |  |
| Powder snow | Block under feet | Climbs out; avoids it on future paths; wears leather boots in snowy biomes |  |
| Magma block | Block under feet | Sneaks across |  |
| Cactus, sweet berry bush | Contact | Steps away | Path cost raised |
| Cobweb | Stuck in one | Breaks it with sword or shears |  |
| Void edge (End) | Edge within 2 blocks | Moves inward; pearls back if knocked off |  |
| Sculk sensor or shrieker | Seen | Switches to sneaking, steps around, leaves the deep dark | Large danger zone in memory |
| Wither rose | Seen | Steps around |  |
| Falling gravel or sand above | Block update | Steps out of the column |  |
| Critical health | Health under 20% | Golden apple, potion, or eat; pearl or pillar away if pressed | Flee mode |
| Bed in the Nether or End | Planner check | Never sleeps there (it explodes); only uses beds there as deliberate explosives for ancient debris, if allowed |  |
| Lightning storm | Weather | Avoids open hilltops if Cautious |  |

### Misses on purpose

The reflex layer is where small, human failure rates live: a 5% to 30% chance (by Skill) to clutch too late, to place the lava-blocking block one tick slow, or to back up the wrong way from a creeper. Track these as tunable numbers; they're a large part of whether the Drifter feels human or robotic.

## 16. Mannerism layer

Mannerisms are what separate "a mob with a player skin" from "is that a person?" They come in three parts: a camera model that moves the head like a mouse does, a movement model that moves the body like a keyboard does, and a catalog of tics that fire on top of whatever the Drifter is doing.

### 16.1 Camera model

Mobs turn their heads smoothly and exactly. Players don't: a mouse accelerates, overshoots, corrects and jitters.

- **Seek with acceleration:** the head accelerates toward a look target up to a max speed, then decelerates.
- **Overshoot and correct:** fast turns overshoot by 1 to 5 degrees, then snap back. Lower Skill overshoots more.
- **Idle jitter:** tiny noise of a fraction of a degree when standing still, so the head is never perfectly frozen.
- **Head vs body:** the body follows the movement direction; when standing still, the body only turns once the head is more than about 50 degrees off, like a player model.

| Situation | Turn speed |
| --- | --- |
| Idle panning, admiring a view | 30 to 90 degrees/s |
| Normal looking around | 180 to 360 degrees/s |
| Combat flicks | 700 to 1000 degrees/s |

**Pitch habits:** looks slightly down when walking, straight down when pillaring or bridging, up at tall trees, level with targets in combat.

**Glances:** looks ahead along its path, glances to the sides every 3 to 8 s, turns toward sounds, checks the sky at dusk.

### 16.2 Movement model

- **Discrete inputs:** movement comes from virtual keys, so diagonal strafing (forward plus a side key) is common and stops are near-instant, like a player letting go of the keys.
- **Unnecessary jumps:** hops up stairs and slabs, jumps while running for no reason.
- **Auto-jump players:** some Drifters (habit fingerprint) have auto-jump on: they jump automatically into every 1-block step and never manually jump while walking, a recognizable style.
- **Sneaking:** on edges, bridges, magma, and when sneaking up on something.
- **Water hops:** bobs up and down when crossing shallow water.
- **Low-Skill clumsiness:** walks into walls when turning, misjudges a jump.

### 16.3 Hands and hotbar

- **Swing animation** on every attack and every left-click, including hitting air.
- **Hold-to-mine:** a continuous swing with the crack overlay growing on the block.
- **Scrollers vs number-keyers:** scrollers visibly cycle the held item through several slots to reach one (you can see the items flash in their hand); number-keyers snap straight to it. A habit per Drifter.
- **Offhand habit:** torch, shield, food or totem in the offhand, by personality and stage.

### 16.4 Tic catalog

| Tic | Trigger | Rate | Leans on |
| --- | --- | --- | --- |
| Crouch-spam | Greeting, thanks, celebration | On event | Sociability, mood |
| Jump-spam | Waiting (furnace, door, crop, a slow player) | Every 2 to 5 s while waiting | Patience (low) |
| 360 camera sweep | Entering a new area, after a fight | Cooldown about 60 s | Caution |
| Sky check | Dusk, surfacing from a mine | On event | Caution |
| Hotbar scroll fidget | Idle | Every 20 to 60 s idle | Fidget |
| Inventory re-sort | Downtime at base | Every few minutes | Tidiness |
| Chest open-close | Idle at base, or searching for an item | Rare | Tidiness (low) |
| Place-and-break a block | Idle near a build | Rare | Fidget |
| Spin in place | Bored or excited | Rare | Mood |
| Look-back glance | Walking alone at night | Every 5 to 15 s | Caution |
| Wall stare | Sound behind a wall | On event |  |
| Corner peek (strafe out and back) | Before entering a room or cave | On event | Caution |
| Edge peek | Standing at a drop | On event |  |
| Sprint stutter | Long travel | Occasional | Fidget |
| Swing at air | After a kill, bored | Occasional | Mood |
| Toss and pick up an item | Idle | Rare | Fidget |
| Wrong slot, then correct | Switching items | By Skill | Skill (low) |
| Over-mine one block | Mining | Small chance per stop | Skill (low) |
| Accidental grass break | Walking and swinging | Rare | Skill (low) |
| Nervous shield raise | Night sounds | On event | Caution |
| Torch spam | Dark cave, anxious | On event | Mood |
| Look a new player up and down | First meeting | Once per player | Curiosity |
| Admire the view or own build | Sunset, finished build | Rare | Builder archetype |
| Night laps around the base | Bored at night | Rare | Mood |
| Walk back for one dropped item | Item left behind | On event | Greed |
| Accidental item drop | Inventory use | Very rare | Skill (low) |
| Door double-flip | Passing through a door | Rare | Fidget |
| Right-clicking a bed in daytime | Newbie only | Rare | Knowledge (low) |
| Going AFK | Long waits | Stands still facing one way for 30 s or more | Patience |
| Victory taunt over a dead player's loot | Player kill | On event, config-gated | Aggression |

### 16.5 Signature moments

Scripted micro-sequences for big events, where the Drifter is most likely being watched:

- **Finding diamonds:** freezes for a beat, looks at the ore, crouch-spams or doesn't (habit), mines the vein eagerly, then does a paranoid look around.
- **First iron armor:** equips it piece by piece, then a quick spin.
- **Near death escape:** runs, eats, looks back repeatedly for the next 30 s.
- **Rage:** after a bad death or a string of failures: rapid spin, punches a block repeatedly, crouch-spams angrily, or logs off (section 4).
- **Meeting a player:** stops, looks, a short look up and down, then the disposition decides.

### 16.6 Rules that keep it from looking scripted

- At most one tic every 2 s, and never two of the same tic back to back.
- Each tic has its own cooldown and a randomized duration.
- Tics only use body parts the current task has left free (section 8).
- Never during reflexes, combat micro-decisions, or precise placement.
- Rates scale with the Fidget trait and with mood: Bored raises idle tics, Anxious raises look-backs and shield raises, Triumphant raises crouch-spam.

## 17. Personality and individuality

Individuality comes from four layers: traits (continuous numbers that tilt every decision), an archetype (a preset of those numbers), a habit fingerprint (discrete quirks rolled once and never changed), and mood (a short-lived state that shifts everything for minutes to days).

### Traits

Each trait is 0 to 1, generated from the archetype's preset plus random noise (spread of about 0.1 to 0.15), clamped. Some traits are pulled against each other (Caution and Bravery) so combinations stay plausible.

| Trait | Mostly affects |
| --- | --- |
| Aggression | Fighting readiness; how fast low Trust becomes attacking |
| Bravery | How much Fear suppresses fighting; flee threshold |
| Caution | Risk costs in planning and pathing; torch density; going home early |
| Patience | Waiting vs leaving; gathering everything before building |
| Tidiness | Cleanup, sorting, closing doors, replanting |
| Curiosity | Exploration weight; investigating sounds; following players |
| Sociability | Stranger baseline Trust; greetings; gifting |
| Greed | Valuables appetite; looting; Bandit tendencies |
| Vengefulness | Grudge weight and duration |
| Forgiveness | Trust recovery rate |
| Industriousness | Work vs idle time |
| Creativity | Build complexity and decoration |
| Efficiency | How deterministic its choices are; batching chores |
| Thrift | Resource cost weight; tool conservation |
| Fidget | Mannerism frequency |
| Mechanical Skill | Aim, timing, crits, clutches, bridging speed |
| Knowledge | Recipes and tricks it knows (section 8) |

### Archetypes

| Archetype | Spawn weight | Trait sketch | Disposition |
| --- | --- | --- | --- |
| Casual (default) | 25% | Everything middling | Wary Neutral |
| Newbie | 10% | Low Skill and Knowledge, high Fidget and Curiosity | Friendly or Pacifist |
| Builder | 12% | High Creativity, Tidiness, Patience | Wary Neutral or Territorial |
| Miner | 10% | High Industriousness and Greed, low Creativity | Wary Neutral |
| Homebody / Farmer | 10% | High Tidiness and Caution, low Curiosity | Friendly or Territorial |
| Speedrunner | 5% | High Skill, Efficiency and Bravery, low Tidiness | Neutral, ignores players |
| Nomad | 7% | High Curiosity, low Tidiness | Pacifist or Friendly |
| Hoarder | 5% | Very high Greed, low Thrift | Territorial |
| Hermit | 5% | Low Sociability, high Caution | Avoids; Territorial at home |
| PvP Sweat | 4% | Very high Skill and Aggression | Opportunist |
| Bandit | 4% | High Greed and Aggression, low Tidiness | Bandit |
| Helper | 3% | High Sociability and Forgiveness | Friendly to Ally |

### Habit fingerprint

Rolled once at birth and never changed. These are what let a player recognize a specific Drifter from its work alone.

- **Torches:** which side of a tunnel; spacing; ground or wall.
- **Tunnels:** 1x2, 2x2 or 3x3; branch spacing.
- **Hotbar layout:** for example sword 1, pickaxe 2, axe 3, shovel 4, food 5, blocks 6 to 8, torches 9; or a mirrored variant; or a messy one that reshuffles.
- **Offhand:** torch, shield, food or empty.
- **Input style:** scroller or number-keyer, sprint toggle or hold, auto-jump on or off.
- **Favorite wood, stone and wool color.**
- **Roof style and window style.**
- **Signature feature:** lookout tower, a banner on the roof, a moat, a pillar of its favorite block, a giant tree, a sign with its name, a map wall of item frames.
- **Door etiquette:** always closes, closes at night only, never closes.
- **Eating threshold** (food 12 to 17).
- **Sleep timing:** straight to bed at dusk, or only when phantoms threaten.
- **Junk policy** and whether it keeps a dirt stack.
- **Chest labels:** signs, item frames, or none.
- **Crafting table litter:** picks them up or leaves them.
- **Trail markers:** torches, cobblestone pillars, signs, or nothing.
- **Celebration style:** crouch-spam, spin, swing, or quiet.
- **Greeting style:** crouch-spam, nod, or approach and stare.
- **Name lists:** for pets and for named gear.

### Mood

| Mood | Caused by | Effect |
| --- | --- | --- |
| Calm | Default | Baseline |
| Anxious | Recent threats, underground at night, low health | Safety weight up, more look-backs and torches, earlier retreat |
| Panicked | Critical danger | Aim error up, spam-clicking, worse reflexes, flee bias |
| Confident | Good gear, recent wins | Takes more fights, explores farther |
| Frustrated | Repeated failures, getting stuck, deaths | Riskier shortcuts (may even dig straight down), rage tics, may log off |
| Bored | Long idle, nothing new | More idle tics, exploration up |
| Triumphant | Big find or a kill | Celebration, crouch-spam, short confidence boost |
| Grieving | Pet or ally died | Slower, stays home, vengeance up if a player did it |
| Angry | Wronged by a player | Lower thresholds for hostility toward that player |

Moods decay back to Calm over minutes (Panicked, Triumphant) to days (Grieving).

### Learning over time

- **Skill:** improves slightly with practice (successful fights, clutches, long bridges), up to about 0.2 above where it started.
- **Knowledge:** grows by seeing things. Finding an enchanting table in a village teaches that recipe; watching a player use a trick it doesn't know (like a water-bucket clutch) can teach it.
- **Fear lessons:** it avoids what killed it, both places and mob types, for a while.
- **Preferences shift:** it comes to favor biomes and areas where things went well.
- **Habits never change:** the fingerprint is identity.

## 18. Daily schedule, time and weather

A Minecraft day is 20 real minutes, and players live by it: out at dawn, home by dusk, indoor chores or mining at night. The strategist turns that into a soft agenda that needs can always override.

### The day

| Time of day | Game ticks | Typical Drifter behavior |
| --- | --- | --- |
| Dawn | about 23000 to 1000 | Wakes, sweeps the area around the base for lingering mobs (creepers by the door), collects overnight mob drops, checks animals and crops, picks the day's main goal |
| Morning | 1000 to 6000 | Main outdoor work: gathering, exploring, outdoor building, long trips |
| Midday | around 6000 | Eats, starts a smelting batch, swaps tasks |
| Afternoon | 6000 to 11000 | Continues; far-away Drifters start heading home based on travel time plus a Caution margin |
| Dusk | 11000 to 13000 | Heads home, brings animals in, closes doors, checks lighting; sky-check tic |
| Night | 13000 to 23000 | Sleeps (beds work from about 12540 in clear weather), then indoor chores, enchanting, or mining |

### Sleeping without skipping the night

A Drifter can't skip the night the way a player does, and watching one lie in bed for 10 real minutes is dull. So:

- Sleeping for a few seconds satisfies its Rest need and resets its phantom clock.
- After that, Homebody and Cautious types stay in bed until dawn; others get up and do indoor chores or go mining, since time doesn't matter underground.
- If players sleep and the night skips, any Drifter in bed wakes up with them.

### Drifters and player sleep

Players can't sleep with monsters nearby. Whether a Drifter counts as one should depend on its disposition: a Drifter currently hostile to that player blocks sleep ("there's danger nearby" is fair), a neutral or friendly one doesn't. This also means the Drifter needs its own spawn group, so it doesn't eat into the normal hostile mob cap.

### Night variations by personality

- **Brave or confident Drifters** go out to fight mobs for bones, string, gunpowder, ender pearls and XP.
- **Hostile Drifters** prefer night for stalking players.
- **Cautious ones** never leave the base after dusk.
- **Armed ones carry on** (user, 2026-10-20: "a sword and shield is enough for the average drifter to survive the night
  and still collect resources"): a stone sword, or any sword and a shield, or iron armor, and it keeps working through
  the night unless it's careful, fearful, shaken, hurt or hungry. A shield steadies the more careful ones. With a mod
  that has cheap shields (Spartan Shields), a crude wooden shield is an early goal; with none in hand at dusk it makes a
  wooden sword.
- **Strip-mining types** (user's note) dig in and then mine from the shelter, since time doesn't matter underground; with a bed, they sleep first.
- **Mining style differs per Drifter** (user's note, see section 10.3): some lean on strip and branch mining, others on caving and exploring the surface.

### Weather

- **Rain:** carries on working outdoors, fishes more, and comes home a little earlier.
- **Thunderstorm:** hostile mobs spawn on the surface, so it stays inside or underground; beds work during storms.
- **Snow biomes:** wears leather boots near powder snow.

### Underground time drift

Without a clock, its sense of time underground drifts (section 5). Coming up to an unexpectedly dark sky triggers an anxious mood, a sky-check, and either a sprint home or an emergency shelter.

### Longer rhythms

- Every few days, a big trip (deep mining expedition, exploration, a structure raid).
- Occasional maintenance days: lighting, repairs, chest sorting.
- Long quiet stretches raise Boredom, which pushes it toward exploring, building something new, or eventually logging off.

## 19. Nether and End operations

The Nether is the Drifter's big late-game risk, handled as planned expeditions with a packing list, a fortified arrival point and strict piglin etiquette. The End is optional and off by default.

### Nether packing list

The strategist won't start a first trip until most of this is in hand:

- Diamond chestplate, leggings and helmet (user, 2026-10-16: "more diamond than iron"; diamond boots can come later,
  since the gold piece takes that slot there), with at least one gold piece (helmet or boots) for piglins.
- Sword, shield, bow and arrows.
- 64 or more cobblestone (ghast-proof building blocks).
- 20 or more steak or cooked porkchop (user, 2026-10-16: the best food for healing between fights).
- Flint and steel (to relight a portal a ghast extinguished).
- Spare pickaxe.
- Gold ingots for bartering.
- Fire resistance potions, once it can brew.

**Water doesn't work in the Nether.** A Knowledge-gated Drifter leaves its water bucket clutch reflex off there; a Newbie tries it and fails, once.

### Building the portal

- 10 obsidian minimum (corners not needed).
- Obsidian from a diamond pickaxe, or by bucket casting (pouring water over lava in a mold), Knowledge-gated.
- Or completing a ruined portal it found.
- The portal sits near the base, framed and lit (section 11).

### On arrival

1. Walls in the portal with cobblestone immediately, leaving a door gap: ghast-proofing.
2. Notes coordinates and marks the way back (cobblestone pillars or a torch trail, by habit).
3. A quick look around before committing to a direction.

### Moving around

- Bridges over lava seas with cobblestone while sneaking, with blocks on the sides when ghasts are around.
- Prefers warped forests (only endermen) over soul sand valleys (ghasts, skeletons) and basalt deltas (magma cubes, hard terrain).
- Advanced Drifters understand the 1 to 8 coordinate ratio and build linked portals.

### Goals there

| Target | Where | Notes |
| --- | --- | --- |
| Blaze rods | Nether fortress | Fights from behind cover; shield up |
| Nether wart | Fortress stairwells | For brewing |
| Quartz | Everywhere | XP and decoration |
| Ancient debris | Around Y 15 (roughly 8 to 22) | Strip mines; bed or TNT mining only if config allows |
| Gold | Nether gold ore, gilded blackstone, bastions | Mining gold angers nearby piglins, so it checks first |
| Upgrade template | Bastion chests | Needed for netherite; veterans only |
| Glowstone, magma cream | Ceilings, magma cubes | Lighting, fire resistance |
| Wither skeleton skulls | Fortress | Veterans only; the Wither itself is never summoned in v1 |

### Piglin etiquette

- Always wears a gold armor piece.
- Never opens chests or mines gold in sight of piglins, unless it's bold and strong enough to take them all on: then it
  "goes loud", loots anyway and fights them (user, 2026-10-16).
- Barters by tossing gold ingots and waiting for the drop.
- Avoids piglin brutes and stays away from bastions unless a veteran.
- Never hits a zombified piglin.

### Coming home

Returns when inventory is full, supplies run low, health or armor durability falls under its threshold, or the trip's goal is done. A Drifter that dies in the Nether may decide the items aren't worth a recovery trip, especially if it's Cautious.

### Netherite

Needs an upgrade template (from a bastion, which it can duplicate with diamonds and netherrack), a netherite ingot (4 scrap plus 4 gold) and a smithing table. Upgrade order: sword, pickaxe, chestplate, then the rest.

### The End (optional, off by default)

- **Eyes of ender:** blaze powder plus ender pearls (from endermen hunts, piglin bartering, or cleric trades).
- **Finding the stronghold:** throws an eye from two spots and triangulates, the way players do, then digs down when the eye drops.
- **Portal room:** breaks the silverfish spawner, fills the frame.
- **Recommended default:** Drifters don't enter the End while the dragon is alive. After a player kills it, veterans may visit End cities for elytra and shulker shells in a later version.
- **If the dragon fight is enabled:** shoots end crystals, pillars up to break caged ones, carries blocks and a pearl, melees the dragon when it perches. Expect it to die a lot.

## 20. Death, respawn and persistence

Death works like a player's: everything drops, the Drifter respawns at its bed with nothing, and it has five minutes to decide whether a corpse run is worth it. The identity, memories and grudges survive.

### On death

- Drops its entire inventory and armor at the spot, plus XP on the player formula (about 7 points per level, capped at 100).
- Broadcasts a player-style death message using the game's normal damage-source wording ("\<name> was slain by Zombie", "\<name> tried to swim in lava"). Config toggle.
- Records the death location, cause, and killer in memory.

### Respawn modes (config)

Built 2026-10-20 (`world/Respawns`, `skills/CorpseRunGoal`; PROGRESS.md "Death, respawn and lives").

| Mode | Behavior |
| --- | --- |
| Bed respawn | Respawns at its bed after 5 to 30 s; if the bed is gone, near its base center; if it has no base yet, at the world spawn like a player with no bed (user, 2026-10-20: "make new drifters without a base respawn too") |
| Limited lives (default) | Bed respawn up to N times (default 5), then it logs off for good |
| Permadeath | Never respawns; its base becomes an abandoned, lootable site |

### The corpse run

Dropped items despawn after 5 minutes of loaded time, so the Drifter decides quickly:

1. Weighs the value of what it dropped against the risk: what killed it, whether that's still there, how far it is.
2. Re-equips from home storage first, if it has backup gear.
3. Hurries: sprinting, minimal mannerisms, direct route.
4. Approaches the death spot carefully, grabs what it can, leaves.
5. If a player killed it, the disposition decides: vengeful Drifters come back armed and looking for that player; cautious ones write the items off.

### After-effects

- A fear lesson about the place and cause (section 17).
- Frustrated or Grieving mood.
- Three or more deaths in one in-game day gives a chance to rage-quit (log off), scaled by Patience.

### What players get

Killing a Drifter is a real reward: its actual gear, everything it was carrying, and its XP. That reward is only meaningful because of the fairness rules (section 1): it all had to be earned.

### What gets saved

| Group | Contents | Where |
| --- | --- | --- |
| Identity | UUID, name, skin, arm type, archetype, traits, habit fingerprint | World-level registry |
| Vitals | Health, food, saturation, exhaustion, XP, effects, air | Entity data |
| Items | Inventory, armor, offhand, selected slot, ender chest contents | Entity data (ender chest in registry) |
| Mind | Long-term memory, trimmed short-term memory, mood, current goal | Entity data |
| Relationships | Per-player and per-Drifter records, grudges, debts | World-level registry |
| Home | Base bounds, bed, stations, chests, pets | World-level registry |
| Owned blocks | Positions it placed in its base | Per-chunk attachment |
| Player-placed blocks | Positions players placed (section 11) | Per-chunk attachment |
| Stats | Deaths, kills, blocks mined, days alive | World-level registry |

Store everything that must outlive the body (relationships, home, the return mechanic) in the world-level registry, not on the entity. Fabric's data attachment API is a good fit for the per-chunk block logs. Version the save format from day one so later changes can migrate old Drifters.

### On reload

Rather than saving a plan mid-step, the Drifter re-plans from its saved goal when it loads. It's simpler and less fragile, and a short look-around on load reads naturally as a player re-orienting.

## 21. Multiple Drifters, groups and modpack fit

Drifters treat each other with the same Trust and Fear model they use for players, so rivalries, friendships and small groups emerge on their own. In the modpack, the Drifter should be its own faction that the existing undead, illager and spider factions all treat like a player.

### Drifter to Drifter

- **First meeting:** both run the disposition logic on each other; starting Trust comes from both temperaments.
- **Ignore:** most common for neutral pairs; they keep distance and get on with their work.
- **Rivalry:** competing for the same area or resources lowers Trust; rivals may fight over territory or raid each other's chests. Breaking another Drifter's blocks doesn't touch players, so this can stay on even when player griefing is off.
- **Friendship:** gifts, helping in fights and time spent nearby raise Trust; above about 50, a group can form.

### Groups

- **Size:** up to 4.
- **Leader:** highest Skill or Familiarity; sets the group's shared goals.
- **Roles:** emerge from archetypes (the Builder builds, the Miner mines, the Farmer farms).
- **Shared base:** one compound, shared chests, one bed each.
- **Shared memory:** when two members are within about 8 blocks and idle, they exchange danger zones, resource sightings and player reputations (marked as "told," with lower confidence).
- **Group reputation:** hurting one member lowers the others' Trust toward you by part of the amount.
- **Group activities:** joint mining trips, defending the base, and for Bandit groups, raiding.
- **Players can join:** an Ally player is treated as a member (shares the base, gets help in fights).

### Drifter team wars

Drifter groups can fall into feuds and fight each other entirely on their own, with no player starting it. A real player can just watch, or join a side if a group already counts them as an Ally.

- **Teams:** a group gets a name and a banner it hangs at its base, so players can tell sides apart.
- **Tension:** each pair of groups has a tension score that rises from incidents: overlapping territory, a stolen chest, a member hurt or killed, a contested ore site or structure.
- **Escalation:** low tension means avoiding each other; medium means skirmishes when members meet; high means planned raids on the rival base (chest theft and block breaking, using the Drifter vs Drifter raiding setting).
- **Raids:** the group gears up, travels together, telegraphs with banners or a war-cry chat line, attacks, loots, and retreats.
- **Ending:** a truce after heavy losses on one side, after a long quiet stretch, or when one group disbands or logs off.
- **Bystanders:** players who aren't Allies of either side are left alone; Drifters at war don't pick new fights mid-raid.
- **Limits:** one active war per group and a cap on participants, both configurable, to keep performance in check.

### Fitting into the modpack's factions

Your 1.21.1 Fabric RPG pack uses Mob Control to make undead, illagers and spiders fight each other on sight. The Drifter should sit beside those factions as its own "Drifters" faction:

| Faction | Toward Drifters | Why |
| --- | --- | --- |
| Undead | Hostile, like toward players | Keeps nights dangerous for Drifters |
| Illagers | Hostile, like toward players | Patrols and outposts stay a real threat |
| Spiders | Hostile at night, neutral in daylight | Matches vanilla player behavior |
| Drifters | Per-relationship (above) | Emergent |

- **Hooking in:** the Drifter mod carries its own faction rules, so Mob Control is not required. If Mob Control is installed, an optional integration can register the Drifter in its faction settings so everything is configured in one place.
  Found when building it (milestone 12, 2026-10-16): Mob Control (`mobcontrol`, by Ohayden) is a spawn-and-behaviour
  rules mod, not a faction list. Each `[[mob]]` rule can set `set.attack` and `set.allow-attack` lists, and a mob whose
  rule has an attack list only targets (and is only allowed to target) the entity types in it, where "player" means the
  player entity type only. So with it installed, zombies and skeletons ignored Drifters. The integration (detected at
  runtime, no dependency): for mobs a rule governs, "player" in those lists counts Drifters too (the mod adds
  `drifter:drifter` to the rule's lists in memory, never to the file, and mirrors the mob's player goal for Drifters).
  A rule without "player" leaves Drifters alone, as the config says. Mob Control also cancels any mob no `[[mob]]` entry
  matches (a full config ends with a `name="*"` catch-all, as the user's does).
- **Faction wars as opportunities:** when undead and illagers fight, Opportunist Drifters may third-party the survivors for loot, while Cautious ones mark the area as dangerous and stay away.
- **Balance:** faction wars thin out mobs, which makes nights easier for Drifters. Watch for that in playtesting and nudge Drifter caution or mob spawn weights if they get too comfortable.

### Modded items

An RPG pack likely adds weapons, armor and items the Drifter's tables don't know. Because acquisition rules are data files (section 8), each one can be added without code. For anything without an entry, the Drifter should judge gear by item tags and attribute values (attack damage, armor points), so a modded sword with higher damage still gets picked up and used.

**Weapon mods like Simply Swords** fit this design without changes to the core:

- **Crafting:** their recipes come in automatically through the game's recipe manager, so the planner can craft them as soon as it knows the ingredients.
- **Ordinary modded weapons:** judged by their damage, attack speed and any reach attribute, and the attack cooldown is computed from the weapon's own attack speed. No per-item data needed.
- **Weapons with special abilities** (right-click or on-hit effects): need a small per-item data entry saying when to trigger the ability, for example "when 2 or more enemies are within 4 blocks." Until an entry exists, the Drifter just uses them as plain melee weapons.
- **Loot-only unique weapons:** handled like any other found loot, and they make a Drifter's drops more exciting.
- **Built (2026-10-17):** weapon families in data files (`drifter_weapons/*.json`: an item per tier, the tiers it makes them at, which personality traits like them); each Drifter has a favorite family and makes it at those tiers instead of the plain sword (user's choice: favorite type by personality, about one in four keep vanilla swords). Unique weapons have per-item ability entries (when: a crowd, a target at range, hurt, any fight; a wind-up for charged ones), used through Simply Swords' own mob ability API.

### Combat mod compatibility

All of these are soft dependencies: detected when present, never required.

| Mod | What it changes | What the Drifter does |
| --- | --- | --- |
| Simply Swords | New weapon types and unique swords with abilities | As above: stats from attributes, ability weapons get small data entries |
| Simply Bows | Bows with their own draw times and arrow behavior | Aiming reads draw time and arrow speed from the held bow instead of assuming vanilla values; special bows get data entries like ability swords |
| Better Combat | Per-weapon attack ranges, swing arcs, combos, two-handed weapons | Reads Better Combat's per-weapon data to set spacing, combo timing and cooldowns; swings hit in the same arc a player's would; two-handed weapons mean no shield |
| Combat Roll | A dodge roll with limited charges and a recharge | Gets the same roll with the same charges and recharge a player has, used as a reflex: out of creeper blasts, away from heavy swings, or to close distance |

- **Fairness:** the Drifter never gets more range, rolls or combo speed than a player with the same gear and enchantments.
- **Animations:** Better Combat animates players with its own animation system. Check early whether that can drive the Drifter's player model; if not, fall back to the vanilla swing while keeping Better Combat's hit timing. (Built: the library's own player hook is for real players only, so the Drifter's model runs Better Combat's animation files through the library itself, pausing EMF's animations on the arms and body for the swing; Combat Roll's roll animation is player-only too, so the Drifter has its own somersault.)
- **Damage and hits:** prefer calling the mods' own attack logic for the Drifter if they allow non-player entities, rather than copying it, so balance stays in sync with updates.
- **Testing:** add a combat-mod test pass to milestone 6.

### Tone

Decided: the Drifter's names and skins look like real online players, the original goal, rather than matching the pack's Skyrim tone. Both are just data (a name generator pattern list and a skin pool), so a themed name set could still be added later as a config option.

## 22. Offscreen simulation and performance

A Drifter is expensive only when someone can see it. Behavior detail scales down with distance in four tiers, and when its chunk unloads it switches to a cheap catch-up simulation so its life keeps moving while you're away.

### Hybrid simulation (user's decision, 2026-10-15)

Away from players, **a few Drifters really live**: up to a configurable number (default 3; the manhunt hunter always
counts) keep a small area loaded round them the way a player does, so they move, mine and build for real, crops grow
and mobs spawn round them (random ticks and natural spawning run near them as near a player), and the land they walk
into is generated. They're chosen by how much they're doing (out on a trip or a job beats idling at home), with some
stickiness so they don't swap back and forth. **All other Drifters** use the catch-up simulation below when their area
loads again. This replaces "Drifters must never force-load chunks" (abstract travel, below) for the simulated few; the
rest still never load chunks.

### Detail tiers

| Tier | When | What runs |
| --- | --- | --- |
| Full | A player within about 48 blocks, or the Drifter is in a player's view | Everything: mannerisms, full perception budget, AI every tick |
| Reduced | Loaded, no player within 48 blocks | No mannerisms, fewer perception rays, AI every 2 to 4 ticks, coarser pathing; combat stays full when engaged |
| Far | Loaded, no player within about 128 blocks | AI every 5 to 10 ticks, simplest pathing, no cosmetic effects |
| Unloaded | Chunk unloaded | Nothing ticks; catch-up runs on reload (below) |

As built (milestone 10, from measuring with `/drifter perf`): the body (movement keys, path following, danger checks,
combat) runs every tick in every tier, since cutting it changes how a Drifter plays; "AI every few ticks" is how often
it re-thinks its goals (every 1 s Full, 1.5 s Reduced, 2 s Far). Most of the cost turned out to be planning and
searching, which were made cheaper for every tier instead.

As built (milestone 16): eye rays a tick 6 Full (12 with a player in view), 4 Reduced, 1 Far; Far also re-ranks its
attention every second instead of every half second, updates what it's watching every fourth tick instead of every
second, and casts far sight every fourth tick; a fight always puts a Drifter at Full. Away from players its knowledge
scan and plans are reused for a few seconds while it stays about where it was.

### Catch-up simulation

When a Drifter's chunk loads again after a gap, it fast-forwards using rates instead of actions:

- **Elapsed time** is capped (10 in-game days; user, 2026-10-16: catch-up "should be way higher", it was 3) so nothing
  jumps absurdly.
- **Resource gain:** per-hour yields by stage and archetype (a Stage 5 Miner gains about so much coal, iron and cobblestone per hour), drawn with randomness and added to inventory or home chests.
- **Consumption:** food eaten, tool durability spent, torches used.
- **Progress:** stage recalculated; crafting that its new resources allow is done instantly (targets that need a trip out
  are passed over for later ones it can make on the spot). Experience at a player's pace (user, 2026-10-16).
- **Settling:** a Drifter with no home a day or more in settles where it is (not nomads), builds its hut's shell and puts
  a chest in it, so it can store and reach the iron age (user, 2026-10-16: travelers came back with no hut, no armor, level 0).
- **Building:** queued blueprint steps are applied only inside its own base chunks, only as those chunks load, and only if no player-placed blocks are in the way.
- **Risk:** a small chance of a simulated death (items lost, respawn at bed) scaled by stage, archetype and Caution.
- **Position:** placed at home, or at a sensible point on its last route.

### Offline progression

Minecraft's clock stops when a world is fully closed, so by default Drifters only catch up on in-game time that passed
while the world was running. A config option has three settings:

- **Off:** no catch-up at all.
- **Game time** (default): catch up on in-game time only, as designed above.
- **Real time:** on save, store the real-world time; on load, convert the real-world gap into in-game time and run the
  same catch-up code.

Real-time catch-up has its own cap (default 2 in-game days), separate from the normal catch-up cap. A negative or
absurdly large gap (for example from a changed system clock) is ignored, so it can't be exploited.

### Abstract travel

Vanilla entities freeze at the edge of loaded chunks, and Drifters must never force-load chunks. So when a Drifter's destination lies outside the loaded area and no player is nearby, it leaves the world into a "traveling" state in the registry and reappears at its destination (or home) when that area next loads, with travel time accounted for in catch-up.

### Budget

- Aim for a total of about 3 to 5 ms per server tick for up to 10 Drifters combined (a tick is 50 ms). If profiling can't hit that, lower the default cap.
- Pathfinding shares a time-sliced budget; searches resume next tick.
- Perception rays are budgeted per Drifter by tier.
- The strategist, selector and planner run on memory snapshots, which can be time-sliced or moved off the main thread (section 8).
- Every system reports its own timing to the debug overlay (section 25), so performance problems are visible early.

### Spawn category

Give Drifters their own spawn group instead of the monster or creature groups, so they don't consume the normal hostile mob cap or get counted like animals.

## 23. Config, difficulty and anti-frustration

Defaults should make Drifters interesting but never miserable: rare, readable, no griefing, hostility that has to be earned or telegraphed. Server owners can turn every dial.

### Config options

| Group | Option | Default |
| --- | --- | --- |
| Spawning | Natural spawning | On |
| Spawning | Max active Drifters per world | 10 (revisit after profiling) |
| Spawning | Kit weights (Fresh / Traveler / Veteran / Raider) | 70 / 20 / 8 / 2 |
| Spawning | Archetype weights | As in section 17 |
| Spawning | World-gen camps | On |
| Spawning | Lived-in world (user, 2026-10-16): Drifter villages, Bandit-held pillager outposts, old strip mines and torches in caves, each with its own switch and rarity | On |
| Spawning | Protected radius around world spawn | 128 blocks |
| Behavior | Can break and place blocks (separate from the mob griefing gamerule) | On |
| Behavior | Hostile mobs target Drifters like players | On |
| Behavior | Highest progression stage allowed | 9 (Nether) |
| Behavior | Can enter the End / fight the dragon | Off / Off |
| Behavior | Respawn mode | Limited lives (5), toggleable |
| Behavior | Max lifespan in days | None |
| Hostility | Drifters can be hostile to players | On |
| Hostility | Aggression toward players, 0 to 100 (0 never hostile, 100 hostile on sight; user, 2026-10-16) | 50 |
| Hostility | Aggression toward each other, 0 to 100 (0 never enemies, 100 every other group's Drifter an enemy) | 50 |
| Hostility | Bandit archetype | On |
| Hostility | Max Drifters hunting one player at once | 1 (2 on Hard) |
| Hostility | Grace period for new players | 1 in-game day |
| Hostility | Cooldown after killing a player | 10 minutes for that player |
| Hostility | Fire and lava used against players | Off |
| Griefing | Break player-placed blocks | Off |
| Griefing | Take from player chests | Off |
| Griefing | Harm players' pets and villagers | Off |
| Griefing | Drifter vs Drifter raiding | On |
| Cosmetic | Join and leave messages | On in singleplayer, off on servers |
| Cosmetic | Death messages | On |
| Cosmetic | Chat lines | On (toggleable) |
| Cosmetic | Taunts over loot | Off |
| Cosmetic | Tab-list entries | Off |
| Performance | Detail tier distances | 48 / 128 blocks |
| Performance | Catch-up cap | 3 in-game days |
| Performance | Offline progression | Off / Game time / Real time, default Game time, real-time cap 2 in-game days |
| Performance | Total Drifter budget per tick | 5 ms |

### Difficulty scaling

| Difficulty | Changes |
| --- | --- |
| Peaceful | Never hostile to players; still plays survival normally |
| Easy | Skill lowered by about 0.15, reaction delay longer, fewer hostile archetypes |
| Normal | Baseline |
| Hard | Skill raised by about 0.1, more Opportunists and PvP Sweats, 2 hunters per player allowed |

The telegraph rule applies on every difficulty.

### Anti-frustration rules

- Telegraph before first hostility (section 14). Exception (user's decision, 2026-10-17): Bandits kill on sight, players, Drifters outside their group, villagers, golems and pets included, behind the setting "Bandits kill on sight" (on by default).
- No bed ambushes or spawn camping; a cooldown after killing a player.
- Ender chests are never touched.
- No griefing, chest theft, or harming players' pets and villagers unless enabled.
- New players get a grace period.
- Claim and protection mods block Drifter interactions in claimed areas, because those interactions go through the fake-player helper.
- Admin commands to reset a Drifter's feelings about a player, pacify it, or remove it.

### Exploit prevention

- **Farming Drifters for gear:** spawns are rare, Veteran kits are rarer, and a Drifter killed repeatedly by the same player starts avoiding them entirely. Optionally, kit gear (items it "brought from elsewhere") has its own drop chance.
- **Trapping:** a Drifter stuck in a trap for a long time logs off rather than standing there forever.
- **Cheesing hostility:** Drifters remember players who lure them into traps, lowering Trust and raising Fear.

## 24. Worked examples

Two traces to sanity-check the design against: a Drifter's first day and night, and the exact scoring behind one decision. Use them as acceptance tests: if a build of the mod can't produce something close to these, a system is missing.

### First day of "pinewick\_42"

A Fresh-kit Casual Drifter (Caution 0.55, Tidiness 0.3, Fidget 0.6, scroller, torches on the left) spawns at dawn at a forest edge, 90 blocks from a player's house. Times are real minutes after spawning.

| Time | What it does | Systems involved |
| --- | --- | --- |
| 0:00 | Spawns out of sight; slow 360 look; walks to the nearest oak | Spawning, camera model |
| 0:20 | Punches 6 logs by hand (about 3 s each), leaves a floating canopy | Woodcutting, Tidiness |
| 1:10 | Short inventory pause: planks, table, sticks; places table, makes a wooden pickaxe; leaves the table behind | Crafting, habit |
| 1:40 | Mines 11 cobblestone from a hillside; crafts a second table on the spot for stone tools (table litter again) | Mining, planner |
| 3:00 | Hunts 2 cows; chases a pig clumsily, overshoots, sprint-hits it | Food, movement model |
| 5:00 | Kills 3 white sheep for a bed | Planner (bed is a Stage 3 goal) |
| 6:00 | Spots coal in a cliff face, mines it, crafts torches | Perception, mining |
| 7:30 | Picks a hillside by a river as home; digs a 3x3 room; places a chest and furnace | Site selection, shelter tier 2 |
| 9:00 | Cooks beef; eats at food 14; jump-spams at the furnace while waiting | Hunger model, tics |
| 10:30 | Sky check; heads home since it's close; seals the entrance with a block | Schedule, Shelter need |
| 10:50 | Sleeps for a few seconds (Rest satisfied), gets up, digs a staircase down from inside the room | Sleep rule, mining |
| 13:00 | Hears zombie groans above; does a wall stare and a few look-backs, keeps mining | Hearing, tics, mood: Anxious |
| 15:00 | Finds iron in a cave wall; mines 4 raw iron; breaking one block reveals lava: places cobblestone in the gap within 0.3 s; torch spam | Reflexes, mood |
| 19:00 | Surfaces expecting dawn, but its time sense drifted: it's still dark. Sky check, goes back down | Time drift |
| 20:30 | Real dawn; sweeps the area outside; a creeper by the door: backs off, sprint-hits, backs off, kills it, collects gunpowder | Combat protocol |
| 21:30 | A player walks up: awareness climbs to Detected; it stops and looks them up and down. Wary Neutral, so it watches | Perception, disposition |
| 21:40 | The player crouch-spams; Trust +3; it crouch-spams back. The player drops a bread; it picks it up (gift, Trust +12) and drops 2 coal back (barter) | Non-verbal language, barter |
| 22:00 | Goes back to its day: smelting the iron | Selector |

### One decision, scored

Day 3, late afternoon. The Drifter is 140 blocks from home at a cave mouth with exposed iron in view. Food 13, health 16 of 20, carrying 5 raw iron, wants 24 for full armor. Caution 0.6.

| Candidate goal | Urgency | Personality weight | Feasibility | Risk factor | Score |
| --- | --- | --- | --- | --- | --- |
| Go home before dark | 0.70 | 1.0 | 1.0 | 1.0 | 0.70 |
| Eat | 0.35 | 1.0 | 1.0 | 1.0 | 0.35 |
| Mine the visible iron | 0.50 | 0.9 | 0.9 | 0.70 | 0.28 |
| Explore the cave | 0.20 | 0.8 | 1.0 | 0.70 | 0.11 |

Go home wins. Eating gets chained in as a quick subtask on the walk, since it doesn't conflict. The iron sighting goes into memory with high importance, and the next morning the Gear goal points straight back to it.

**The same moment for other personalities:**

- **Reckless Miner (Caution 0.2):** the mining risk factor rises to about 0.9 and the weight to 1.2, so mining scores near 0.49; still below going home, but the gap is small enough that the weighted random pick sometimes chooses the iron and it gets caught out at dusk.
- **Nomad carrying a bed:** a "camp here" option appears at about 0.75, beating the long walk home. It digs in next to the cave and mines the iron the next morning.

## 25. Debug tools, data files, roadmap and open questions

Build the debug overlay first and the AI second: a system this layered is impossible to tune if you can't see why it chose what it chose. Keep every tunable number in datapack files so balancing never needs a rebuild.

### Debug tools

- **Overlay** (operators only): name, archetype, detail tier, current goal and its score, the next three alternatives, plan steps, need bars, mood, Trust and Fear toward the viewer, awareness of the viewer, the current path, the view cone and perception rays, remembered ores and danger zones as markers.
- **Thought log:** a ring buffer of decisions in plain words ("picked Go home 0.70 over Mine iron 0.28"), dumpable to a file.
- **Commands:** spawn with archetype, kit and disposition; set a trait, mood or stage; set Trust toward a player; freeze, step or slow the AI; force a goal; dump memory; run catch-up for N hours; switch detail tiers.
- **Timings:** milliseconds per system per tick, shown in the overlay and logged.

### Data-driven files

| File set | Holds |
| --- | --- |
| Archetypes | Trait means and spreads, spawn weights, disposition type |
| Fingerprint pools | Name patterns, pet names, gear names, signature features, palettes |
| Acquisition rules | Mining drops and tool tiers, mob drops, crops, trades, structure loot |
| Item values | For barter and Greed |
| Junk lists | Per junk policy |
| Mannerisms | Each tic's trigger, cooldown, duration and trait links |
| Blueprints | Modules, templates, roof and window styles |
| Threat table | Base danger per mob type, gear modifiers |
| Reflex tuning | Reaction delays, failure chances by Skill |
| Schedule | Day segments and margins |
| Chat vocabulary | Per-personality lines and rate limits |
| Skin pool | Textures (resource pack side) with archetype weights |

### Build roadmap

Each milestone ends with a test you can actually run.

1. **Body.** Entity, player rendering with skins and poses, inventory, hunger model, virtual-key movement, camera model. *Done when* you can drive it by command and it moves like a player.
2. **Hands.** Fake-player helper; mining, placing, crafting, smelting, eating, containers. *Done when* a scripted sequence goes from punching wood to stone tools.
3. **Senses and memory.** View cone, awareness meter, block sampling, hearing, memory tiers, debug overlay. *Done when* the overlay shows exactly what it knows and nothing more.
4. **Pathfinder.** A\* with digging, bridging and pillaring. *Done when* it crosses a ravine and tunnels through a hill to reach a target.
5. **Brain and day one.** Selector, planner, executor, the Stage 1 to 3 loop. *Done when* a Fresh Drifter survives its first night unaided on most seeds.
6. **Combat and reflexes.** Mob targeting, per-mob protocols, reflex catalog. *Done when* it survives three nights in a row.
7. **Mannerisms, personality and mood.** *Done when* people watching a short clip can't reliably tell it from a player.
8. **Progression and building.** Stages 4 to 8, shelter tiers 3 to 5, farming, animals. *Done when* an unattended 10-day run reaches diamonds with a recognizable base.
9. **Disposition and PvP.** Relationships, telegraph rule, hostile and friendly behaviors. *Done when* every event in the section 14 table moves Trust as specified.
10. **Offscreen and performance.** Detail tiers, catch-up, abstract travel, offline progression option. *Done when* 10 Drifters stay within the per-tick budget.
11. **Nether.** Section 19.
12. **Groups, modpack factions, camps, config polish.**
13. **Whim system.** Whim data format, gates, cooldowns, archetype weights, then the whims in docs/WHIMS.md, vanilla first and modded whims behind mod detection. Can start any time after milestone 9. Whims that need The End (elytra, shulkers, the void's edge) stay off until milestone 14. (Swapped with The End by the user, 2026-10-16.)
14. **The End** (optional).
15. **House plans by the hundred** (user, 2026-10-17; see PROGRESS.md).
16. **Scale** (user, 2026-10-18: "can we put 1000 drifters in a world while keeping the TPS low and the server very
    smooth? ... if a 1000 isnt possible lets aim for 300"; and "a 500v500 (or 150v150) war and it runs very smoothly with
    each ai working properly"). Path searches and near eye rays on helper threads over a shared frozen copy of the world;
    crowd-aware perception; cheaper decisions; catch-up spread over ticks. *Done when* 1000 Drifters across a world and
    300 round a player run at a steady 20 TPS, and a 150v150 battle (`/drifter battle 150`) stays playable.
17. **Fixes** (user, 2026-10-19): Combat Roll (rolling out of a creeper's blast), getting around on rough ground, Better
    Combat animations that don't show on Drifters.
18. **Player skills** (user, 2026-10-19): villager trading, fishing, horses, boats, potions (brewing), netherite (ancient
    debris and smithing), each the way a player does it and under section 1's rules.
19. **Mod compat and modded whims** (user, 2026-10-19): Farmer's Delight Refabricated, Simply Bows, Spartan Shields, Guard
    Villagers, Bountiful, all optional (detected at runtime), and modded whims for them.
20. **Whims offscreen and wrong guesses** (user, 2026-10-19): a proposal for simulating or catching up whims away from
    players, then more of the wrong-guess assumptions (the cave hunch, the theft assumption). Decided (user,
    2026-10-20, docs/OFFSCREEN_WHIMS.md options A + a small B + C + E): with no player near, show-only whims are settled
    at once; while unloaded, catch-up gives it a few whims a day as memories, and at most two real traces at its own
    loaded home; what it did is one line in its thoughts, never chat. Each whim has an `offscreen` value
    (docs/WHIMS_HOWTO.md). Also asked for: an easier way to add whims (steps whims, load checks, the how-to page).
21. **Encounters and bounties** (user, 2026-10-20; docs/ENCOUNTERS.md): random events with temporary Drifters you never
    see again, like the GTA random events (bounty hunters after you, a wanted Drifter with a map to its house, ambushes,
    people needing help, merchants...), and Bountiful compat (Drifters doing bounties; bounties on Drifters). The user
    picks the events from the list. First batch built (2026-10-20): bounty hunters, wanted posters, injured travellers,
    thieves, travelling merchants, ambushes (docs/ENCOUNTERS.md "Built").
22. **Civilization, as an addon mod** (user, 2026-10-20; docs/CIVILIZATION.md): towns, money and trade, leaders and laws,
    nations and wars, culture, a chronicle and a "one island" mode, inspired by the "1000 players simulate civilization"
    videos. Its own mod that needs Drifter (user's call: "its really big i think it should be like an addon mod"), with
    a small Drifter API added first. Several milestones; same fairness rules.

Two extra acceptance tests use the manhunt test mode below: **Manhunt (hunter)** after milestone 9, and **Manhunt (speedrunner)** after milestone 14 (The End). They are tests, not milestones of their own.

### Manhunt test mode

A 1v1 manhunt (a real player against a Drifter, in the style of the well-known YouTube manhunt videos) is the "ultimate test" of the mod: it stresses perception, memory, pathfinding, combat, eating under pressure and reflexes all at once. It is **only a test scenario**. The mod is not built around it, it is off unless started by command, and it changes no core rules.

- **Starting it:** an operator command picks a Drifter and a player, for example `/drifter manhunt start <drifter> <player> hunter`, and `/drifter manhunt stop`. Nothing happens in normal play.
- **Roles:** **hunter first** (the player is the runner; the Drifter hunts). **Speedrunner** (the Drifter runs, the player hunts) comes later, once the Nether and End milestones exist.
- **Fairness still applies** (section 1): no x-ray, player reach, player timings, no free items. The Drifter starts with nothing, like a manhunt hunter.
- **Tracking compass:** like human hunters, the Drifter gets a tracking compass that points at the runner when it's used. It knows only what the compass shows (a direction, in the same dimension), not the runner's exact position or what they're doing; everything else comes from its own senses.
- **Disposition:** toward the runner only, the Drifter behaves as Vengeful/Predatory (section 14) for the length of the game. Both sides agreed to the fight, so the first-contact telegraph is replaced by the start of the game. All other players are treated normally, and the anti-frustration rules (no griefing, no bed or spawn camping between deaths) still apply.
- **Deaths:** a hunter Drifter respawns like a manhunt hunter (at its spawn point, with nothing), regardless of the normal respawn mode; the runner dying ends the game.
- **Win conditions:** the runner wins by killing the Ender Dragon (or a configurable simpler goal, such as reaching the Nether, for earlier tests); the hunter wins by killing the runner.
- **Speedrunner role extras (later):** the Drifter uses the Speedrunner archetype (section 17), routes for the Nether and End, flees and evades the hunter, and needs the End and dragon fight switched on for that game only.

### Open questions

- Decided: usernames and skins look like real online players (section 21).
- Decided: chat lines on by default, toggleable.
- Decided: limited lives by default, toggleable.
- Decided: factions are built in; Mob Control integration is optional.
- Decided: Simply Swords, Simply Bows, Better Combat and Combat Roll first (section 21).
- Decided: aim for 10, adjusted after profiling.
- Decided: a 1v1 manhunt is the ultimate test, as an optional test mode only; hunter role first (see "Manhunt test mode").

## 26. Whims

Whims are about 900 random things players do for fun (slime bouncing, silly builds, village rampages, sunsets), plus modded whims for popular RPG mods, used as low-priority goals the selector (section 7) picks when urgent needs are met. They live in their own file, docs/WHIMS.md, so sessions only load them when working on the whim system.

### Whim data format (milestone 13)

Whims are data: `data/<namespace>/drifter_whims/*.json`, each `{"whims": [ ... ]}`, so datapacks can add, change or
remove them. One whim:

| Field | Meaning |
| --- | --- |
| `id` | Unique name (`/drifter whim <id>` runs it) |
| `category` | For grouping (movement, builds, exploration, mobs, micro, social, weather, collecting, crafting) |
| `action` | Which behaviour: `watch_sky`, `lookout`, `high_point`, `visit`, `watch_mob`, `feed_mob`, `hop`, `swim`, `pick_flower`, `torch_dark`, `plant_sapling`, `sign`, `build`, `chat`, `look_around`, `peek_chest`, `dance`, `gift`, `hang_out`, `visit_friend`, `watch_block`, `craft` |
| `params` | The action's settings (what to watch, a build's blocks, a sign's lines, gift items...); `"make"` maps a need to an item it may craft for it (quickly: no mining trips or searches); `"requires_mod"` leaves the whim out without that mod |
| `weight` | Base weight (1) |
| `archetypes` | Multiplier per archetype (`"hermit": 2.0`) |
| `traits` | Per trait: how much it scales with it (positive likes it more with the trait, negative less) |
| `moods` | Per mood: how much likelier while the mood is strong (`"triumphant": 2.0`) |
| `stage` | Lowest progression stage |
| `when` | `any`, `day`, `night`, `sunset`, `sunrise`, `rain`, `thunder`, `clear` |
| `needs` | Items it must carry (`{"item": "minecraft:wheat"}` or a tag `"#minecraft:small_flowers"`, with `count`) |
| `duration`, `cooldown` | Ticks |
| `gates` | Letters from docs/WHIMS.md: `G` griefing (hostile ones with griefing on), `M` multiplayer (someone about), `R` risky (cautious ones rarely), `L` later (off) |
| `chat`, `thought` | Optional chat lines; the thought it logs |

The whim goal scores from boredom (most), triumph, Fidget and Curiosity, less for the industrious, only when calm
(healthy, fed, no danger, overworld, not deep underground, not out at night unless brave), with a 3 to 6 minute gap
between whims. Setting: "Whims" (`/drifter config whims`).
