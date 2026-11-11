# Farmer's Delight Addons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drifters cook the Delight addons' food their intended way (crafting table, pot, board, mortar, cauldrons,
feasts), get the ingredients like a player (fruit trees, wild patches, crabs, dragonflies, knife kills, weighing the
risk), learn addon dishes, grow what they bring home, and use Crate Delight crates and Storage Delight furniture as
storage and decoration in the right rooms; Civilizations towns get the same.

**Architecture:** Generic first: one place (`compat/Dishes`) decides what a dish is from the recipe manager, so any
pot/board/crafting/furnace recipe of any addon joins meal choice; crates are found generically from 9-into-1 recipe
pairs (`compat/Crates`). Hand-written compat only for the special parts: `compat/ThaiDelight` (+ a bridge if its
classes are needed) for the mortar, cauldrons and fruit; `compat/StorageDelight` for its furniture; `FdBridge` grows
feasts (Farmer's Delight's own `FeastBlock`, used by all three food addons). New ingredient kinds are data
(`drifter_sources/*.json` with new keys) read by `Sources` and turned into planner actions. Storage gets a room
purpose per container in `Home`, used by `StoreActivity` and the layout (`Furnishing`).

**Tech Stack:** Minecraft 1.21.1, Fabric, Mojang mappings, Java 21. No unit tests in this project: each task is proven
by `./gradlew build` and headless smoke scripts (`python tools/smoke.py . <script> <log>`), run with and without the
addons.

**Spec:** docs/superpowers/specs/2026-11-10-delight-addons-design.md

## Global Constraints

- DESIGN.md section 1 fairness: knowledge only through perception (only ingredients/places/mobs it has seen), player
  time for every action, reach 4.5 blocks to blocks / 3 to entities, no impossible placement, no free items (a crate
  is 9 real items; a placed crate is real stock).
- Every addon optional, detected at runtime (`FabricLoader.isModLoaded`); data files take `"requires_mod"`; without
  the addons (and without Farmer's Delight) behaviour is unchanged. Mod ids: `moredelight`, `oceansdelight`,
  `muffins_thaidelight`, `cratedelight`, `storagedelight` (and `farmersdelight`).
- Only the addons' dishes are learned (spec goal 7): vanilla items and Farmer's Delight's own meals stay known to
  every Drifter.
- Campfire cooking needs no new station: every campfire recipe in the three food addons also has a furnace or smoker
  recipe (checked 2026-11-10), so the furnace/smoker route the planner already has is the intended way (spec goal 1's
  "or smoker/furnace where the addon allows it").
- Sea hunting within reach only: shore, shallows (water ≤ 2 deep at the target's spot), or a boat it already uses;
  never into an ocean monument. Elder guardians are never a planned source.
- Crates: only food, crop and seed storage (spec "What counts as a crate"); vanilla compressed blocks never.
- Storage Delight furniture is extra storage in new places; it never replaces existing chest, dump, bedside, fuel or
  cabinet places; no room left = no extra storage.
- The addon (`src/civ`) uses Drifter only through `com.dinglebob.drifter.api`; grow it in the same change, document in
  docs/API.md, `API_VERSION` 6 → 7 (first task that adds an API call bumps it). Addon edits small.
- Tunables as named constants (each task names its own). Saved data versioned (new keys under a `Version` int).
- Stay out of whim files (`drifter_whims/`, `whim/`): other sessions own them.
- Performance: judged at ~200 loaded Drifters; recipe and pair indexes built once per reload (keyed on the
  `RecipeManager` identity, as `Planner.index` and `FdBridge` do), never per tick.
- Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; commit and push only to
  `claude/drifter-chat-expansion-574ebe`; version `0.23.39-side.N` bumped only in the last task; never install while
  `javaw.exe` runs; stop only your own test servers by PID; smoke port 25566 (25567 if busy).

## Review Focus

1. **No addons, or only some:** with none of the five installed (and with Farmer's Delight alone), nothing new runs,
   no class from an absent mod is touched, every new data file is skipped, and the existing Farmer's Delight cooking
   and storage behave exactly as before. Pinned in every task's "without" smoke run.
2. **A dish whose ingredient it has never seen:** it isn't chosen and the planner doesn't explore blindly for it
   (only "looking for X" plans for sources it has seen or been told of, as today). Pinned in Task 6.
3. **Crate loops:** packing then unpacking the same food, packing what the chosen meal needs, or the planner turning
   9 carrots into a crate and back. Pinned in Task 9.
4. **Storage in the wrong place on odd houses:** tiny houses, houses with no bedroom found, partitioned or two-floor
   houses: no drawer in a doorway, in front of a bed/stove/chest, outside, or on the roof; when no wall fits, none.
   Pinned in Task 10.
5. **Danger:** a weak Drifter never plans guardians; a strong one plans guardians only from shore/shallows; a target
   that swims out of reach ends the hunt instead of the Drifter drowning. Pinned in Task 7.

---

### Task 1: Dev runs with the addons

**Files:**
- Modify: `build.gradle`, `gradle.properties`
- Create: `tools/smoke-delight-load.txt`

**Interfaces:**
- Produces: a dev launch flag `DRIFTER_DELIGHT=1` (or `-Pdelight`) that loads Farmer's Delight plus the five addons
  and their libraries; compile-only access to any addon whose classes later tasks need (Thai's Delight at least, for
  the mortar recipe class, if reflection isn't enough: decide here and note it in the commit message).

- [ ] **Step 1:** Add Modrinth-maven entries (the repo already uses `maven.modrinth`): `more-delight`
  `26.04.30-1.21-fabric`, `oceans-delight` `1.0.3+fabric.1.21.1`, `muffins-thais-delight` `fabric-1.21.1+2.0.0`
  (needs `muffins-oven-api`: pick the version Thai's Delight 2.0.0 depends on), `crate-delight`
  `26.07.20-1.21-fabric`, `storage-delight` `26.07.01-1.21-fabric`; versions in `gradle.properties` like
  `farmers_delight_version`. `modLocalRuntime` only under the new flag (which also turns on Farmer's Delight's).
- [ ] **Step 2:** Write `tools/smoke-delight-load.txt`: start, `until Done_( 900`, then one existing command per mod
  that names one of its items (`drifter plan moredelight:mashed_potatoes`, `drifter plan
  oceansdelight:squid_rings`, `drifter plan muffins_thaidelight:pesto_sauce`, `drifter plan
  cratedelight:carrot_crate`, `drifter plan storagedelight:oak_drawer`: each prints a plan or "no way", never
  "unknown item"), then `stop`.
- [ ] **Step 3:** Run with `DRIFTER_DELIGHT=1` and without. Expected: both reach "Done"; with it, the log lists all
  six mods loaded and no mixin/remap errors; without it, none of them.
- [ ] **Step 4: Commit** `Dev runs: DRIFTER_DELIGHT=1 loads Farmer's Delight and its five addons`

---

### Task 2: Dishes and everyday meal choice (generic)

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/compat/Dishes.java`, `tools/smoke-delight-dishes.txt`
- Modify: `brain/Kitchen.java` (`chooseMeal`, `readiness`), `brain/Food.java` (`choose` meal bonus),
  `brain/Planner.java` (`index`: add `RecipeType.SMOKING` results to `cookedIndex` so `Food.isCookable` sees smoker-only
  foods; nothing else), `command/HouseCommands.java` or the existing kitchen debug (`/drifter kitchen dishes`)

**Interfaces:**
- Produces:
  - `static boolean Dishes.isDish(Level, Item)`: a food (`FoodProperties` present, not `Food.isBad`) that is the result
    of a cooking-pot recipe, a cutting-board recipe whose input is itself a dish, a crafting recipe with ≥ 2 distinct
    ingredient items of which ≥ 1 is a food, a mortar recipe (Task 4 registers mortar results through
    `Dishes.addSource`), or a feast's serving (Task 5); **and** either in the `farmersdelight:meals` tag or of
    nutrition ≥ `Kitchen.MEAL_NUTRITION` (6) or of nutrition ≥ `DISH_SNACK_NUTRITION = 4` when its namespace isn't
    `minecraft` (so sandwiches and toast count, a vanilla cookie doesn't). Cached per `RecipeManager`.
  - `static Set<Item> Dishes.all(Level)`; `static boolean Dishes.isAddonDish(Item)`: a dish whose namespace is neither
    `minecraft` nor `farmersdelight`.
  - `static void Dishes.addSource(Function<Level, Collection<Item>> results)`: lets Task 4/5 add station results.
  - `Kitchen.chooseMeal` considers every `Dishes.all` item the planner can make (pot meals only when a usable pot
    exists, crafting-table dishes without one); `Food.choose`'s +2.0 meal bonus uses `Dishes.isDish`.

- [ ] **Step 1:** Write `tools/smoke-delight-dishes.txt` (flat world, `DRIFTER_DELIGHT=1`): spawn a Drifter at a
  pasted farmhouse (as the Farmer's Delight smoke runs do), give it bread, cooked chicken, an egg, a tomato
  (`drifter give`), then `drifter kitchen dishes` and `drifter plan moredelight:chicken_sandwich_with_egg_and_tomato`;
  wait; check it eats/makes the sandwich (`thoughts log true`).
- [ ] **Step 2:** Run it: fails (`kitchen dishes` unknown; the sandwich never chosen).
- [ ] **Step 3:** Implement `Dishes`, the `Kitchen`/`Food`/`Planner.index` changes, and the debug command (lists
  dishes it knows about, by station).
- [ ] **Step 4:** Run with and without the addons. Expected with: the dish list holds More Delight sandwiches and pot
  dishes and Ocean's/Thai's pot dishes; the Drifter plans and crafts a sandwich at the table and later picks it when
  eating. Without: `kitchen dishes` lists Farmer's Delight's meals only (or "no dishes" without it); cook goal
  unchanged. `./gradlew build` passes.
- [ ] **Step 5: Commit** `Dishes: any addon's pot, board and crafting-table food joins meal choice`

---

### Task 3: Learning addon dishes

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/brain/DishLore.java`
- Modify: `entity/DrifterEntity.java` (field, getter, save `DrifterDishes` / load), `brain/Kitchen.java`
  (`chooseMeal` filter), `brain/Planner.java` (skip a recipe whose output `Dishes.isAddonDish` and isn't known),
  `api/DrifterApi.java` (+ `API_VERSION` 7), `docs/API.md`

**Interfaces:**
- Consumes: `Dishes.isAddonDish(Item)`, recipe ingredients from the recipe manager.
- Produces:
  - `final class DishLore` per Drifter: `boolean knows(DrifterEntity, Item dish)` (true for non-addon dishes; true for
    all when `DrifterConfig.knowEveryRecipe()` or `Trait.KNOWLEDGE >= GearLore.VETERAN_KNOWLEDGE` (0.7) with a
    non-NEWBIE archetype, the same rule as `GearLore.innate`); `void check(DrifterEntity)` every `CHECK_INTERVAL = 200`
    ticks: learns every addon dish one of whose recipe ingredients it carries (thought "learned how to make X");
    `void learn(Item dish, String where)`; `CompoundTag save()` / `load(CompoundTag)` with `Version` 1 and a list of
    item ids (unknown ids skipped on load).
  - `DrifterApi.learnDishesFrom(DrifterEntity, Container)`: learns the addon dishes whose ingredients are in that
    container (Task 11 calls it for a town house's pantry).

- [ ] **Step 1:** Extend `tools/smoke-delight-dishes.txt`: a fresh low-Knowledge Drifter (`drifter spawn` then the
  existing trait command to set Knowledge 0.2) asked `drifter plan muffins_thaidelight:pesto_sauce` before and after
  being given one basil.
- [ ] **Step 2:** Run: before the change the plan is made without knowing the dish.
- [ ] **Step 3:** Implement `DishLore`, the save/load, the planner/meal gates and the API call.
- [ ] **Step 4:** Run. Expected: before the basil, `plan` says it doesn't know the dish; after, "learned how to make
  pesto sauce" and a plan (mortar from Task 4 may still be missing: the plan names the mortar). Farmer's Delight's
  beef stew plans for the same Drifter without learning. A save and reload keeps the learned dish.
- [ ] **Step 5: Commit** `Dish lore: addon dishes learned by holding an ingredient (Farmer's Delight meals stay known)`

---

### Task 4: Thai's Delight mortar

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/compat/ThaiDelight.java` (registry ids, `loaded()`, mortar recipe
  records), `compat/thaidelight/ThaiBridge.java` only if the mod's classes are needed, `brain/activity/MortarActivity.java`
- Modify: `brain/Planner.java` (new action `Grind`, index, `obtainItem` step after `potCook`), `brain/Goals.java`
  (`activityFor`), `building/Furnishing.java` (a `MORTAR` use on the kitchen's end cabinet, `onTop`, only when Thai's
  Delight is loaded), `brain/Kitchen.java` (mortar as a Strategist kitchen target once the house is up, like the cabinet)

**Interfaces:**
- Consumes: `Dishes.addSource` (register mortar results), `DishLore.knows`.
- Produces: `record ThaiDelight.MortarRecipe(List<Ingredient> inputs, ItemStack result, ItemStack container)`;
  `static List<MortarRecipe> ThaiDelight.mortarRecipes(Level)` (cached per `RecipeManager`, type
  `muffins_thaidelight:mortar`); `record Planner.Grind(Item output, List<Predicate<ItemStack>> inputs, List<String>
  inputLabels, int times, @Nullable Item container)`; `MortarActivity(DrifterEntity, Planner.Grind)`.

- [ ] **Step 1:** Research how a player uses the mortar from the mod's code (decompile with
  `~/.gradle/caches/modules-2/files-2.1/org.vineflower/vineflower/1.12.0/*/vineflower-1.12.0.jar` into the scratchpad:
  `MortarBlock` opens `MortarMenu`, no block entity; read the menu's slots and how the result is taken). Write the
  findings in the commit message.
- [ ] **Step 2:** Write `tools/smoke-delight-mortar.txt`: a Drifter at the farmhouse with a mortar item, 2 basil and a
  bowl, `drifter plan muffins_thaidelight:pesto_sauce`, wait, check it carries pesto sauce.
- [ ] **Step 3:** Run: fails (no plan).
- [ ] **Step 4:** Implement: planner action with cost `COST_GRIND = 6` per use; the activity walks into reach, opens
  the mortar through the fake player (the "inventory open" pause as `CraftActivity` does), moves the inputs and
  container into the menu's slots, takes the result through the menu, closes it; placing a mortar it carries on the
  kitchen's end cabinet (or as a free-standing station via `Place` when it has no kitchen).
- [ ] **Step 5:** Run with and without. Expected with: it places the mortar, grinds pesto, the bowl is used up, the
  dish list includes mortar results (dyes and sugar are not dishes). Without Thai's Delight: no `MORTAR` place in
  `drifter house furnishing`, no change.
- [ ] **Step 6: Commit** `Thai's Delight mortar: grinds pesto and somtam like a player, placed on the kitchen cabinet`

---

### Task 5: Feasts (all mods) and Thai's Delight cauldrons

**Files:**
- Create: `brain/activity/ServeFeastActivity.java`, `brain/activity/CauldronActivity.java`
- Modify: `compat/farmersdelight/FdBridge.java` + `compat/FarmersDelight.java` (feast: is a block a `FeastBlock`, its
  servings left, its serving item and container), `compat/ThaiDelight.java` (cauldron recipes as data the planner can
  read), `brain/Planner.java` (actions `Serve`, `CauldronJob`), `brain/Goals.java`, `brain/Kitchen.java` (a placed
  feast is household food: the cook serves from it before cooking again), `brain/Home.java` (remember placed feasts;
  saved, versioned)

**Interfaces:**
- Consumes: `Dishes.addSource` (feast servings are dishes), `Planner.FillBucket` (water for a cauldron).
- Produces: `static @Nullable FarmersDelight.Feast FarmersDelight.feast(Level, BlockPos)` with
  `record Feast(Item serving, ItemStack container, int servingsLeft)`; `static @Nullable Item
  FarmersDelight.servingOf(Level, Item feastItem)`; `record Planner.Serve(Item feast, Item serving, @Nullable Item
  container, int servings)`; `record Planner.CauldronJob(Item output, String how)` (the exact steps per cauldron come
  from step 1's research); `Home.feasts()` / `addFeast(BlockPos)` / `removeFeast(BlockPos)`.

- [ ] **Step 1:** Research from the mods' code: Farmer's Delight's `FeastBlock` (right-click with the container takes
  one serving; the empty plate is broken), Thai's Delight's `CoconutCauldron`, `CoconutMilkCauldron`,
  `FermentedFishCauldronBlock` (what goes in, waiting by random ticks, what takes the product out). Note findings in the
  commit message.
- [ ] **Step 2:** Write `tools/smoke-delight-feast.txt`: (a) ingredients for Thai's crab fried rice feast (or Farmer's
  Delight's roast chicken) and bowls, `drifter plan` its serving; (b) coconut and a bucket of water, plan coconut milk;
  (c) cod and a water cauldron, plan fermented fish (sprint for the random ticks).
- [ ] **Step 3:** Run: fails.
- [ ] **Step 4:** Implement: a feast is placed on a free kitchen/main-room floor spot from `Furnishing.keepClear`
  (never in front of furniture, never outside), served one bowl per click; leftovers stay placed and remembered as the
  household's (`Home.feasts`), served from by any household Drifter before it cooks again; the empty plate is broken
  and picked up. A cauldron is placed on the work wall's free end or beside the kitchen (a new `CAULDRON` use, only with
  Thai's Delight), filled with the water bucket, then worked as the research says; waiting jobs remembered like
  `Brain.PotBatch` and seen through.
- [ ] **Step 5:** Run with and without. Expected with: the feast is placed inside, served into bowls, the empty plate
  removed; coconut milk bottled; fermented fish taken out after the wait. Without: no change.
- [ ] **Step 6: Commit** `Feasts and cauldrons: placed feasts served into bowls for the household, Thai cauldrons worked`

---

### Task 6: Ingredient sources: fruit, wild patches, critters

**Files:**
- Create: `src/main/resources/data/drifter/drifter_sources/thais_delight.json`, `oceans_delight.json`,
  `more_delight.json` (each `"requires_mod"`), `tools/smoke-delight-sources.txt`
- Modify: `brain/Sources.java` (new optional keys), `brain/Planner.java` (`gather`/`hunt` honour them),
  `brain/activity/GatherActivity` (or whichever runs `Planner.Gather`) and `HuntActivity` (harvest-by-use, catch)

**Interfaces:**
- Produces new `drifter_sources` entry keys (documented in the file's `_comment` and in Sources' javadoc):
  - `"harvest": "use"`: the item comes from right-clicking the block (ripe only, by the block's own age property)
    instead of breaking it (Thai's hanging mango, papaya, durian, lime as the research shows); `"ripe": {"property":
    "age", "value": 2}`.
  - `"catch_with": "<item id>"`: the mob is caught by using that item on it (dragonfly + glass bottle → bottle of
    dragonfly; flower crab + water bucket → crab bucket), not killed.
  - `"held": "<#tag or id>"`: the killing blow must be struck holding it (Task 7 uses it).
  - Coconuts: research `CoconutBlock` and `SackBlock` (a sack under the tree catches a falling coconut); express it as
    a source (`"harvest": "use"` or breaking, whichever a player does) plus the sack only if that is the mod's only way.
  - `Source` record grows `@Nullable String harvest`, `@Nullable ResourceLocation catchWith`, `@Nullable
    ResourceLocation held`, `@Nullable RipeRule ripe`.
- Entries: Thai's basil/holy basil (wild basil patch), pepper (wild pepper), butterfly pea (vine), mango, papaya, lime,
  durian, coconut (trees), bamboo shoot (as the mod gives it), flower crab (mob), dragonfly (catch), fermented fish is
  Task 5's; Ocean's guardian (mob, no `held`: its drop needs no knife), tentacles from squid and glow squid (`held`:
  `#farmersdelight:tools/knives`); More Delight: wooden and stone knives are crafted (no entry needed).

- [ ] **Step 1:** Write `tools/smoke-delight-sources.txt`: a jungle/savanna seed or placed fruit trees and a wild
  basil patch near a Drifter that has seen them (`drifter know` area command used by earlier smoke runs, or walking
  past); `drifter plan muffins_thaidelight:mango` and `...:basil`, a dragonfly spawned and `drifter plan
  muffins_thaidelight:dragonfly_bottle`; an unseen source (`...:durian` with no durian tree seen).
- [ ] **Step 2:** Run: fails (no sources).
- [ ] **Step 3:** Implement the keys and the entries.
- [ ] **Step 4:** Run with and without. Expected with: mango picked by use from a ripe hanging mango (unripe left),
  basil gathered, dragonfly caught in a bottle; durian with no tree seen plans "looking for" only (Review Focus 2), no
  blind trip. Without: files skipped, no change.
- [ ] **Step 5: Commit** `Sources: fruit picked when ripe, wild herbs, critters caught; addon ingredients for the planner`

---

### Task 7: Knife kills and weighing the risk

**Files:**
- Modify: `brain/Planner.java` (`Hunt` record + `hunt()`), `brain/activity/HuntActivity.java`,
  `executor/task/AttackTask.java` (`selectWeapon`), `brain/Goals.java` (`activityFor`), `brain/Threats.java` (a
  danger read for a prey type, if not already exposed)
- Create: `tools/smoke-delight-hunt.txt`

**Interfaces:**
- Consumes: `Source.held` (Task 6), `Stakes.wariness(DrifterEntity)`, `Threats` danger data.
- Produces: `Planner.Hunt` gains `@Nullable Predicate<ItemStack> mustHold, @Nullable String holdLabel`; `hunt()`
  obtains a knife (any item in `#farmersdelight:tools/knives`, cheapest it can make: More Delight's wooden/stone knife
  when present) when `held` is set; `AttackTask` takes an optional `Predicate<ItemStack> mustHold` that overrides
  `Combat.bestWeaponSlot` for the killing blow only (it may fight with its weapon and switch to the knife when the prey
  is low, or hold the knife throughout for passive prey: pick the simpler that guarantees the drop and say which);
  `static double Planner.preyRisk(DrifterEntity, EntityType<?>)` in [0, 1] from the mob's danger against its gear and
  health, scaled up by `Stakes.wariness`; `hunt()` treats a prey with `preyRisk > PREY_RISK_LIMIT = 0.5` as infeasible
  and otherwise adds `COST_RISK = 200 * preyRisk` per kill.
- Water: `HuntActivity.pickTarget` skips prey whose spot is in water deeper than `SHALLOW_DEPTH = 2` unless it can
  reach it by the boat travel `NavigateTask` already has; a target that moves out of reach mid-chase ends the hunt
  ("it swam off"). `hunt()` never plans `minecraft:elder_guardian`.

- [ ] **Step 1:** Write `tools/smoke-delight-hunt.txt`: squids in a shallow pool, a Drifter with a stone sword and no
  knife: `drifter plan oceansdelight:tentacles`; a guardian in a shallow pool and (a) a leather-clad new Drifter, (b) a
  diamond-geared one: `drifter plan oceansdelight:guardian`; a guardian in deep water for (b).
- [ ] **Step 2:** Run: fails (no knife in the plan; the weak one plans the guardian).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: the plan crafts a knife, the squid kill drops tentacles; (a) says the guardian is too
  dangerous; (b) kills the shallow guardian; the deep one is skipped ("out of reach"); no Drifter drowns. Without the
  addons: hunts unchanged (`mustHold` null).
- [ ] **Step 5: Commit** `Hunting: knife kills for knife-only drops, prey risk weighed by gear and stakes, sea prey within reach`

---

### Task 8: Growing what it brings home

**Files:**
- Modify: `brain/activity/FarmActivity.java` (sow any seed whose crop is a `CropBlock` it can plant, not only wheat),
  `brain/Goals.java` (an `orchard` goal)
- Create: `brain/activity/OrchardActivity.java`, `tools/smoke-delight-grow.txt`

**Interfaces:**
- Produces: `FarmActivity` picks seeds in this order: what a known dish it wants needs, then wheat; rows of one crop
  each, `MAX_CROPS = 3` kinds; `static boolean FarmActivity.plantable(ItemStack)` (a `BlockItem` placing a
  `CropBlock` or the source data's crop). `OrchardActivity`: plants carried fruit saplings (`ItemTags.SAPLINGS` whose
  tree is a known fruit source in `drifter_sources`) on free ground within `ORCHARD_RADIUS = 12` of home, outside the
  farm plot, the house's box and `keepClear`, at most `ORCHARD_MAX = 6`, remembered in `Home.orchard()` (saved);
  ripe fruit near home is then a seen source for Task 6.

- [ ] **Step 1:** Write the smoke script: a settled Drifter with a farm, given basil seeds, pepper seeds, a mango
  sapling; sprint 2 game days; `drifter home` / thoughts.
- [ ] **Step 2:** Run: wheat only, sapling kept.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: the farm has wheat plus basil/pepper rows, harvested and replanted; the sapling is
  planted near home, not in the farm or a path; without the addons the farm is wheat as before.
- [ ] **Step 5: Commit** `Farming: sows the crops its dishes need; plants fruit saplings in a small orchard near home`

---

### Task 9: Crates and bags

**Files:**
- Create: `compat/Crates.java`, `brain/activity/PackActivity.java`, `tools/smoke-delight-crates.txt`
- Modify: `brain/Home.java` (`stored(Item)` also counts each stored crate whose `Crates.of(level, crate)` content is
  that item as `count()` of it, and placed crates: `crates()`, `addCrate(BlockPos, Item)`, `removeCrate`, saved,
  versioned), `brain/Kitchen.java` (`readiness` counts crates), `brain/Planner.java` (a
  placed crate is fetched by breaking it: action `TakeCrate(BlockPos pos, Item crate)`), `brain/Goals.java` (a `pack`
  goal), `building/Furnishing.java` (a new `Use.CRATE`: up to 4 cells on a free wall run beside the kitchen or the
  storage wall, two high at most, passing `usable`/`keep`, only when Crate Delight is loaded; added to `ours`)

**Interfaces:**
- Produces: `record Crates.Pair(Item crate, Item content, int count)`; `static @Nullable Crates.Pair
  Crates.of(Level, Item crate)`, `static @Nullable Crates.Pair Crates.forContent(Level, Item content)`: a pair is a
  shaped/shapeless recipe making 1 block item from exactly 9 of one item **and** a shapeless recipe making those 9
  back from it, where the item is a food, a crop (`c:crops` tag) or a seed (`c:seeds` tag, or places a `CropBlock`),
  and the block's namespace isn't `minecraft`; cached per `RecipeManager`.
- Packing rules: at home only, only stock above `PACK_ABOVE = 128` of one item (two stacks), never what
  `Kitchen.saving` keeps or the farm's seeds below `FarmActivity.SEED_BATCH`; unpacking only by the planner (the
  shapeless recipe is already a candidate) when a plan needs the contents. Placed crates go to Task 10's `CRATE`
  spots, two high at most; the rest are stored in kitchen/pantry storage.

- [ ] **Step 1:** Smoke script: a settled Drifter with 200 carrots and 40 potatoes: wait; then `drifter plan
  farmersdelight:vegetable_soup` (needs carrots); check crates and a placed crate stack.
- [ ] **Step 2:** Run: fails (no packing).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: it packs 8 carrot crates (keeps the rest loose), none of potatoes; places some on the
  crate spots; the soup plan opens one crate (or fetches one from storage), not more; no pack/unpack loop over 2
  sprinted days (Review Focus 3); `drifter house furnishing` shows the crates. Without Crate Delight: no pairs (vanilla
  hay/iron blocks never), no change.
- [ ] **Step 5: Commit** `Crates: packs surplus food nine to a crate, sets them out in the pantry, opens one when a plan needs it`

---

### Task 10: Storage Delight furniture, rooms and the right contents

**Files:**
- Create: `compat/StorageDelight.java`, `brain/activity/FitFurnitureActivity.java` (generalising
  `FitKitchenActivity`'s walk-and-place, which stays for the kitchen), `tools/smoke-delight-storage.txt`
- Modify: `building/Furnishing.java` (`Use` gains `DRAWER`, `COUNTER_CABINET`, `DOOR_SHELF` (`CRATE` came in Task 9);
  room purpose;
  wood from the house palette; `ours`), `brain/Home.java` (`isStorageBlock` and `forgetMissingChests` accept any
  `Container` block entity it placed or was given; a purpose per container), `perception/BlockNotes.java` (Storage
  Delight blocks and placed crates as `MemoryKind.STORAGE`), `brain/activity/StoreActivity.java` (deposit by purpose),
  `building/plan/HousePlan.java` (`FURNITURE`), `brain/activity/BuildPlanActivity.java` (furniture test),
  `api/DrifterApi.java` (`settleIn`: register the new uses as storage), `brain/Kitchen.java` or `Strategist` (a drawer
  and a countertop cabinet as targets after the kitchen)

**Interfaces:**
- Produces: `enum Furnishing.Purpose { GENERAL, FOOD, BOOKS_ODDS, CLOTHES_VALUABLES }`; `Furnishing.Use.purpose()`
  (STORAGE_CHEST/DUMP/FUEL → GENERAL, CABINET/COUNTER_CABINET → FOOD, DOOR_SHELF → BOOKS_ODDS,
  DRAWER/BEDSIDE_CHEST → CLOTHES_VALUABLES; CRATE is a block, not a container: no purpose); `Home.purpose(BlockPos)` (saved, versioned; old saves → GENERAL);
  `StoreActivity` sends food to FOOD containers, books/paper/maps/enchanted books and odds (the "other" category) to
  BOOKS_ODDS, worn-slot gear, spare clothes and money/valuables (`ItemValues` high-value) to CLOTHES_VALUABLES, the rest
  to GENERAL, falling back to GENERAL when the matching kind is full or missing; no moving of what's already stored.
- Layout rules (spec goal 10, the user's main worry): new places only on free wall runs that pass `usable` and `keep`;
  `DRAWER` in the bedroom (beside the bed head's other side or along its longest free wall, ≤ 2), `COUNTER_CABINET`
  along the kitchen's room (extends the kitchen run or the next wall, ≤ 2), `DOOR_SHELF` in the main room (≤ 1, not on
  the storage wall); never in the entry's door
  step, never outside the house's rooms; when nothing fits, nothing is placed. Wood: the Storage Delight variant
  matching `PlacedPlan` palette's wood, else oak.

- [ ] **Step 1:** Smoke script: three pasted house sizes (the farmhouse, a small 1-room house, a two-room partitioned
  one) with Drifters settled by `DrifterApi.settleIn` (the town debug spawn) and a Drifter given a drawer, a countertop
  cabinet and a bookshelf with doors who fits them; `drifter house furnishing` for each; store a mixed inventory.
- [ ] **Step 2:** Run: fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run with and without. Expected with: each house shows the new places only where they fit (the
  small house may get none; Review Focus 4), the furniture's wood matches, deposits go food→cabinets,
  books→shelf, valuables→drawer; existing chests unchanged. Without Storage Delight: layout identical to before
  (compare `drifter house furnishing` output with a run on the base commit).
- [ ] **Step 5: Commit** `Storage Delight: drawers, countertop cabinets and door shelves in the right rooms; storage by room purpose`

---

### Task 11: Civilizations towns

**Files:**
- Modify (addon, small): `src/civ/java/com/dinglebob/drifterciv/TownBuilder.java` (`stock`: accept any `Container`
  the house's layout registered, through a new `DrifterApi.storagePurpose(Home, BlockPos)`), `TownChests.java`
  (`PANTRY` gains the addons' ingredients and dishes, skipped when an id doesn't resolve; drawers get clothes and
  valuables, shelves books and odds, crates are placed full by the layout as blocks), the town market/store pools
  (addon dishes for sale), and a call to `DrifterApi.learnDishesFrom` for each house's pantry
- Modify (main): `api/DrifterApi.java` (`storagePurpose`), `docs/API.md`

**Interfaces:**
- Consumes: `DrifterApi.learnDishesFrom` (Task 3), `Furnishing.Purpose` through the new API call (returned as a plain
  string `"general" | "food" | "books_odds" | "clothes_valuables"` so the addon needn't see `Furnishing`).

- [ ] **Step 1:** Smoke script: `drifterciv` town spawn with all addons; inspect a house (`drifter house furnishing`
  near a citizen), its cabinets/drawers/shelves contents, and a citizen's `drifter kitchen dishes`.
- [ ] **Step 2:** Run: fails (new containers empty).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run with and without. Expected with: houses have the new furniture where it fits, stocked by purpose,
  pantries hold addon ingredients, citizens know their pantry's dishes, the market sells addon dishes. Without: towns
  as before.
- [ ] **Step 5: Commit** `Civilizations: town houses get drawers, shelves and crates stocked by room, addon pantries and dishes`

---

### Task 12: Talk

**Files:**
- Create: `src/main/resources/data/drifter/drifter_talk/topics_more_delight.json`, `topics_thais_delight.json`,
  `topics_oceans_delight.json`, `topics_crates.json` (each `"requires_mod"`)
- Modify: `brain/Kitchen.java` (`addTalkFacts`: flags `learned_dish` + word `dish`, `has_orchard`, `knife_hunted` +
  word `prey`, `packed_crates`)

- [ ] **Step 1:** Write ≥ 8 topics per food file and ≥ 4 for crates in the house voice of `topics_kitchen.json`
  (several line variants per turn, `leads` into existing kitchen topics), using the new flags.
- [ ] **Step 2:** Run `python tools/check_talk_web.py .`: passes (no dead leads, flags known).
- [ ] **Step 3:** Smoke: two Drifters who both learned a dish talk (`drifter talk` debug used by earlier talk work);
  without the addons the files are skipped.
- [ ] **Step 4: Commit** `Talk: Drifters chat about addon dishes, their orchard, knife hunts and crates`

---

### Task 13: Docs, version, install

**Files:**
- Modify: `docs/PROGRESS.md` (a section "0.23.39-side.N: Farmer's Delight addons" with what's built, the rulings and
  what's left), `docs/TESTING.md` (plain-language steps with exact commands per part), `docs/API.md` (API 7),
  `gradle.properties` (`version`)

- [ ] **Step 1:** Merge the main line (`origin/claude/gifted-archimedes-n6yen5`) and any side branch whose jar is
  installed in DRIFTER TEST; take the next free `0.23.39-side.N` (or `0.23.<n>-side.N` if the main line moved).
- [ ] **Step 2:** `./gradlew build`; run every `tools/smoke-delight-*.txt` with and without `DRIFTER_DELIGHT=1`:
  no errors.
- [ ] **Step 3:** Write the docs.
- [ ] **Step 4:** Commit, push; check `tasklist /FI "IMAGENAME eq javaw.exe"` and the installed jar again; if the game
  is closed, remove the old `drifter-*.jar` and `drifter_civilizations-*.jar` from the DRIFTER TEST mods folder and
  copy both new jars.
