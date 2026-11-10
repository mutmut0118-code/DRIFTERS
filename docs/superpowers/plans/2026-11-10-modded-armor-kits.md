# Modded Armor in Starting Kits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Starting kits use installed modded armor: town guards and leaders in a per-town uniform carrying the town's (or its nation's) trim, bandits/ambushers/delvers in a realistic taste-leaned mix, tiers unchanged.

**Architecture:** A new Drifter class discovers matching armor sets from the registered items and rates them against the vanilla tiers (`gear/ArmorSets`). A second (`world/Uniforms`) chooses and dresses uniforms and trims, reached by the addon through new `DrifterApi` calls and two small API records. Existing kit code (`Delvers.townKit`, `Delvers.kit`, `SoldierKit.piece`) asks `ArmorSets` for pieces instead of hard-coded vanilla items. The addon stores one uniform set id per town; trims are derived from the town's or nation's id (and the nation's colour), so they need no saving.

**Tech Stack:** Minecraft 1.21.1, Fabric, Mojang mappings, Java 21; no unit tests: `./gradlew build` and headless smoke scripts (`python tools/smoke.py . <script> <log>`).

**Spec:** docs/superpowers/specs/2026-11-10-modded-armor-kits-design.md

## Global Constraints

- CLAUDE.md fairness: a starting kit is the only gear given; nothing free afterwards except goal 9's one-time trim switch (user-approved).
- The addon (`src/civ`) uses Drifter only through `com.dinglebob.drifter.api`; grow the API in the same change and document it in docs/API.md (`API_VERSION` 5 → 6: the main line's friends took 4, and the gear work was renumbered to 5 at the 0.23.38 merge). Addon edits small: the user works on the addon in other sessions.
- Armor mods optional, read from registered items; without them kits stay as today except guards' and leaders' town trim on vanilla iron.
- Tier rule: a modded set is "at tier T" when its four pieces' total `GearValue` armor value is within ±15% (`SET_TIER_BAND = 0.15`) of the full vanilla set of T, and no single piece's value is above the next vanilla tier's piece of that slot.
- Trims only on items in `ItemTags.TRIMMABLE_ARMOR`; a non-trimmable set is never a uniform.
- Guards' 8% diamond piece is dropped; guards' uniform is one set on every guard; leaders keep today's gold crown and a chestplate one tier up.
- Tunables as named constants: `SET_TIER_BAND`, `CITIZEN_MODDED_CHANCE`, `BAND_FULL_SET_CHANCE`, `LOOT_TRIM_CHANCE`.
- Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; version `0.<milestone>.<build>-side.N` bumped only in the last task; never install while javaw.exe runs; stop only your own test servers by PID.

## Review Focus

1. **No armor mods installed:** every kit still dresses (vanilla), guards' iron carries the town trim, no exception from set discovery on an empty modded list.
2. **A modded set missing a slot** (e.g. no helmet), or with two candidate items in one slot: filled with the vanilla piece of that tier; never an empty slot on a guard.
3. **Old saves:** towns made before this (no uniform key) and nations without a stored trim load fine; their geared guards are untouched until a join/leave switch, which changes only trimmed pieces.
4. **Join/leave while the town's people are unloaded:** the switch is applied when they're next loaded and checked (no lost switch, no double switch, no switch on non-uniform pieces like a bandit's loot trim).
5. **A /reload or a different mod list between sessions:** a town's stored set id that no longer exists falls back to trimmed iron without crashing.

---

### Task 1: Armor sets and tiers (`gear/ArmorSets`)

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/gear/ArmorSets.java`, `tools/smoke-kits-sets.txt`
- Modify: `src/main/java/com/dinglebob/drifter/gear/GearData.java` (clear the cache on reload, beside `RecipeIndex.clear()`), `src/main/java/com/dinglebob/drifter/command/GearCommands.java` (`gear sets`)

**Interfaces:**
- Consumes: `GearValue.armor(ItemStack, EquipmentSlot)`-style value already used by `WearArmorTask.score` (use the same armor value function GearValue exposes for a slot); `GearValue.wornSlot`.
- Produces:
  - `enum ArmorSets.Tier { LEATHER, CHAIN, IRON, DIAMOND }` with `vanilla(EquipmentSlot)` → the vanilla `Item`.
  - `record ArmorSets.Set(String id, Map<EquipmentSlot, Item> pieces, boolean trimmable, @Nullable Tier tier)`: `id` = namespace + common prefix (e.g. `immersive_armors:warrior`); `pieces` has every armor slot, missing ones filled with the vanilla piece of `tier` (or iron when `tier` is null).
  - `static List<Set> ArmorSets.sets()`: every modded matching set (non-`minecraft` namespace), cached until reload; `static @Nullable Set ArmorSets.byId(String)`.
  - `static List<Set> ArmorSets.at(Tier tier, boolean trimmableOnly)`.
  - `static @Nullable Item ArmorSets.modPieceAt(EquipmentSlot slot, Tier tier, RandomSource random, @Nullable DrifterEntity taste)`: a single modded piece whose value fits `tier` by the per-piece rule (≥ the tier piece × (1 − `SET_TIER_BAND`), ≤ next tier's piece), leaned by `GearTaste.liking` when `taste` is given; null when none.
  - `static void clear()`.

- [ ] **Step 1: Write `tools/smoke-kits-sets.txt`**: start the server with run/mods as is (Immersive Armors present), `drifter gear sets` (lists each set: id, tier or "none", trimmable, pieces). Second run with `immersive_armors` moved to `run/mods_off/`: `drifter gear sets` says `no modded armor sets`.
- [ ] **Step 2: Run it to see it fail** (`gear sets` unknown).
- [ ] **Step 3: Implement `ArmorSets`**: group registered items with a `wornSlot` among HEAD/CHEST/LEGS/FEET by namespace + the id with the slot word stripped (`_helmet|_chestplate|_leggings|_boots|_hat|_cap|_tunic|_pants|_shoes` and similar; also by shared `ArmorItem.getMaterial()` when the ids don't share a prefix); a group with ≥ 2 slots is a set; `trimmable` = every real piece is in `ItemTags.TRIMMABLE_ARMOR`; `tier` by the Global Constraints rule over the four slots (filled ones counted at the vanilla value). Cheap: built once per reload; no per-call allocation in `at` (precomputed lists per tier).
- [ ] **Step 4: Build and run**: `./gradlew build --offline`; smoke shows Immersive Armors' sets with tiers (at least one IRON or CHAIN or DIAMOND) and trimmable true; any set missing a slot lists that slot as the vanilla filler (Review Focus 2); without it, `no modded armor sets`, no exception.
- [ ] **Step 5: Commit** (`git commit -m "Armor sets: modded matching sets found and rated against the vanilla tiers"`)

---

### Task 2: Town uniforms and trims (`world/Uniforms`, `Delvers.townKit`, API)

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/world/Uniforms.java`, `src/main/java/com/dinglebob/drifter/api/TownUniform.java`, `tools/smoke-kits-uniform.txt`
- Modify: `src/main/java/com/dinglebob/drifter/world/gen/Delvers.java:353` (`townKit`), `src/main/java/com/dinglebob/drifter/api/DrifterApi.java` (`API_VERSION = 6`, new calls), `docs/API.md`, `src/main/java/com/dinglebob/drifter/command/GearCommands.java` (`gear uniform`)

**Interfaces:**
- Consumes: `ArmorSets.at(Tier.IRON, true)`, `ArmorSets.byId`, `ArmorSets.Tier.vanilla`.
- Produces:
  - API record `com.dinglebob.drifter.api.TownUniform(String armorSet, String trimPattern, String trimMaterial)`: `armorSet` is an `ArmorSets.Set.id` or `"minecraft:iron"`; trim fields are registry ids (e.g. `minecraft:sentry`, `minecraft:gold`).
  - `static String Uniforms.chooseSet(RandomSource random)`: a trimmable set at IRON chosen with variety (random among those qualifying, not always the best), else `"minecraft:iron"`.
  - `static TownUniform Uniforms.townTrim(UUID town, String armorSet)`: pattern and material picked deterministically from the town's UUID (`Math.floorMod(id.hashCode(), n)` over `SoldierKit`'s PATTERNS list and the trim materials).
  - `static TownUniform Uniforms.nationTrim(UUID nation, DyeColor colour, String armorSet)`: material = `SoldierKit.trimFor(colour)` (make it package-visible or move the mapping into `Uniforms` and have SoldierKit call it), pattern from the nation's UUID.
  - `static void Delvers.townKit(DrifterEntity d, RandomSource random, String role, @Nullable TownUniform uniform)`: guard → the uniform set's four pieces (missing/unknown set → iron), every trimmable piece trimmed, no diamond roll; leader → the set's legs and feet, gold helmet crown, chestplate one tier up (`ArmorSets.Tier.DIAMOND` vanilla or a modded DIAMOND-tier chest), all trimmable pieces trimmed; citizen → today's humble rules, each worn piece swapped for `ArmorSets.modPieceAt(slot, sameTier, random, d)` with chance `CITIZEN_MODDED_CHANCE = 0.2` when non-null. The old 3-arg `townKit` calls this with `uniform = null` (today's behaviour: iron guards untrimmed).
  - `static int Uniforms.retrim(DrifterEntity d, TownUniform from, TownUniform to)`: on worn armor whose `DataComponents.TRIM` equals `from`'s pattern+material, set `to`'s; returns pieces changed.
  - `DrifterApi`: `String chooseUniformSet(RandomSource)`, `TownUniform townUniform(UUID town, String armorSet)`, `TownUniform nationUniform(UUID nation, DyeColor colour, String armorSet)`, `void equipTown(DrifterEntity, RandomSource, String role, TownUniform uniform)`, `int retrim(DrifterEntity, TownUniform from, TownUniform to)`; docs/API.md section "Uniforms (API 6)".

- [ ] **Step 1: Write `tools/smoke-kits-uniform.txt`**: spawn 4 Drifters; `drifter gear uniform kit <role> <townUuid>` (new debug: dresses the target as that role with `townUniform(uuid, chooseUniformSet)`) for two guards with one UUID, one guard with another UUID, one leader; `drifter inventory` on each (armor with trim lines). Variant with Immersive Armors moved out.
- [ ] **Step 2: Run it to see it fail** (`gear uniform` unknown).
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Build and run**: same-UUID guards wear identical items and identical trims; the other UUID's guard differs in trim; leader has gold helmet + higher chest, trimmed; without Immersive Armors guards wear trimmed iron; a kit with an unknown set id (`drifter gear uniform kit guard <uuid> nonexistent:set`) gives trimmed iron, no exception (Review Focus 5); `./gradlew build` builds both jars (the addon still compiles against the 3-arg `equipTown`).
- [ ] **Step 5: Commit** (`git commit -m "Town uniforms: one set per town, its trim on every piece; API 6"`)

---

### Task 3: A realistic mix for bandits, ambushers and delvers

**Files:**
- Modify: `src/main/java/com/dinglebob/drifter/world/gen/Delvers.java:280` (`kit`), `src/main/java/com/dinglebob/drifter/world/SoldierKit.java:110-204` (`equip`, `piece`), `tools/smoke-kits-mix.txt` (create)

**Interfaces:**
- Consumes: `ArmorSets.modPieceAt`, `ArmorSets.at(tier, false)`, `ArmorSets.Tier`.
- Produces: no new public API.

- [ ] **Step 1: Write `tools/smoke-kits-mix.txt`**: spawn 10 delvers (`/drifter` delver spawn command as in SocialCommands) and start one ambush band encounter (debug command in encounter commands); list each one's four armor items (`drifter inventory`).
- [ ] **Step 2: Run it to see it fail** (only vanilla pieces).
- [ ] **Step 3: Implement**: per slot, with chance by taste (practical/flashy/follower 0.4, loyal 0.25 per piece) a modded piece at that slot's tier from `modPieceAt` (taste-leaned) replaces the vanilla one; with chance `BAND_FULL_SET_CHANCE = 0.08` (×2 for the loyal style) the whole body is one set from `ArmorSets.at(tier, false)` instead; delvers (no band colour) get a random trim on one trimmable piece with chance `LOOT_TRIM_CHANCE = 0.1`; SoldierKit keeps dyeing leather and trimming metal in the army's colour (modded trimmable pieces included), captains keep the banner, tiers per `Quality` as today.
- [ ] **Step 4: Build and run**: across the 10 delvers some modded pieces, mostly mixed, rarely a full set, no tier above diamond-equivalent; the band shows the army trim on its trimmable pieces; without Immersive Armors the same scripts show today's vanilla kits.
- [ ] **Step 5: Commit** (`git commit -m "Bandits, ambushers and delvers: modded pieces in a realistic, taste-leaned mix"`)

---

### Task 4: Civilizations: a town's uniform, the nation's trim, the switch

**Files:**
- Modify: `src/civ/java/com/dinglebob/drifterciv/Town.java` (field `String uniformSet`, saved as `"UniformSet"`, missing → `""`), `TownBuilder.java:3511-3527` (`townUp`), `Nations.java:207-243` (`join`), `:291-319` (`leave`), the addon's per-town periodic check that runs on loaded members (find it, e.g. `TownDay` or `Towns` tick), `DrifterCivilizations.java` (`/drifterciv town uniform`), `tools/smoke-kits-civ.txt` (create)

**Interfaces:**
- Consumes: `DrifterApi.chooseUniformSet`, `townUniform`, `nationUniform`, `equipTown(…, TownUniform)`, `retrim` (Task 2).
- Produces (addon-internal): `static TownUniform Towns.uniform(Town town)` (helper in whichever addon class fits): the nation's (`Nations.of(town)` → `nationUniform(nation.id, nation.colour, town.uniformSet)`) else the town's own (`townUniform(town.id, town.uniformSet)`); an empty `uniformSet` is chosen on first need and saved.

- [ ] **Step 1: Write `tools/smoke-kits-civ.txt`**: make two towns side by side (`drifterciv town spawn`), `drifterciv town uniform` at each (set, pattern, material; guards' worn trims); join them (the nations debug command, e.g. a forced union); `drifterciv town uniform` again at the joined town (its guards now show the nation trim); make the joined town free (independence debug); again (back to its own trim).
- [ ] **Step 2: Run it to see it fail** (`town uniform` unknown; guards untrimmed iron).
- [ ] **Step 3: Implement**: `townUp` sets `uniformSet` (or reads the planned nation through `Nations.of(plannedId)`) and calls the 4-arg `equipTown` for guards and the leader with `Towns.uniform(town)` (a town of a planned nation wears the nation trim from the start); `join` and `leave` record the old uniform before the change, then `retrim(member, old, new)` for every loaded member and mark the town `retrimFrom = old` (saved as `"RetrimFrom"` pattern|material, cleared when its last loaded guard and leader have been switched or after 7 days); the periodic check retrims loaded guards and leader whose worn pieces still carry `retrimFrom`.
- [ ] **Step 4: Build and run**: two towns show different trims; after joining, the joined town's guards wear the nation's trim (material matching the nation colour) and the capital's guards the same trim; after freedom, back to the town's own; a second join done while the town's chunks are not loaded (`forceload remove`, join, then `forceload add`) switches its guards once they're loaded and checked, and a bandit-style loot trim on a non-uniform piece is left alone (Review Focus 4); an old save town (no `UniformSet`) loads and gets a set on first `town uniform` (Review Focus 3); `grep -rn "com.dinglebob.drifter\.\(gear\|world\)" src/civ` finds nothing new.
- [ ] **Step 5: Commit** (`git commit -m "Civilizations: town uniforms, the nation's trim shared, switched on joining and leaving"`)

---

### Task 5: Docs, full runs, the jar

**Files:**
- Modify: `docs/PROGRESS.md`, `docs/TESTING.md`, `gradle.properties`

- [ ] **Step 1: PROGRESS.md**: a "Modded armor in starting kits" entry (done per task; calls from the spec's "Calls made" for the user to check).
- [ ] **Step 2: TESTING.md**: plain-language steps: make two towns (`/drifterciv town spawn ...`), look at the guards (same armor and trim in one town, different trim in the other), `/drifterciv town uniform`; a nation forming (trim switch); delvers and an ambush with mixed modded pieces; the same with Immersive Armors removed.
- [ ] **Step 3: Full runs**: every `tools/smoke-kits-*.txt` and the gear smokes touching kits (`smoke-gear-civ.txt`), with mods and with Immersive Armors moved out; no exception naming drifter or immersive_armors.
- [ ] **Step 4: Version and install**: merge the main line (`origin/claude/gifted-archimedes-n6yen5`) and any side branch installed in DRIFTER TEST first; `version=<main version>-side.<next free>`; `./gradlew build`; if javaw.exe isn't running, replace the drifter jars in DRIFTER TEST's mods folder.
- [ ] **Step 5: Commit and push** (`git commit -m "<version>: modded armor in starting kits (town uniforms with trims, nations share theirs, mixed kits for the rest)"`, `git push origin HEAD`)
