# Any Weapon, Armor and Trinket Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drifters value, learn about, choose by taste, get, and use any modded weapon, armor or trinket (plus crossbows and
tridents) the way a player does, with Drifter: Civilizations using the same through the API.

**Architecture:** A new `com.dinglebob.drifter.gear` package holds four pieces with one job each: `GearValue` (what an
item is worth, from its own components), `GearKinds` (what kind of weapon it is), `GearLore` (what this Drifter knows of
items and their powers, saved and versioned like `personality/Lore`) and `GearTaste` (its style and how much it likes an
item). Existing choosers (`Combat.bestWeaponSlot`, `Shields.bestBowSlot`, `WearArmorTask`, `Trading.want`, `Strategist`)
switch to these. Powers, crossbows, tridents and trinkets are added on top, each in its own task; then the API and addon.
Tunables live in `data/drifter/drifter_gear/*.json` (one reload listener) and named constants.

**Tech Stack:** Java 21, Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Mojang mappings, Loom
1.17-SNAPSHOT, Gradle 9.5.0. Optional mods by reflection (Better Combat, Simply Swords, Trinkets), as
`compat/BetterCombatCompat` already does.

**Spec:** `docs/superpowers/specs/2026-11-09-weapons-armor-compat-design.md` (goals 1 to 11; read it with this plan).

## Global Constraints

- Build on the main line: merge `origin/claude/gifted-archimedes-n6yen5` (0.23.32) into this branch before Task 1; this worktree's own checkout (0.23.23-side.2) is old.
- Versions: a side branch, `0.23.32-side.N` (`version` in `gradle.properties`); take the next free N after checking every remote branch and the jar in DRIFTER TEST (memory: side-branch installs; `origin/claude/affectionate-blackburn-6269f1` is at `0.23.32-side.2` and its jar is installed now). Bump before every jar that goes to the user.
- Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API `0.116.17+1.21.1`, Java 21, Loom `1.17-SNAPSHOT`, Gradle 9.5.0: don't bump Loom or Gradle.
- Mojang names (`ResourceLocation`, `Mob`, `ItemStack`...). Client-only code stays in `src/client`; common code never imports `net.minecraft.client.*`.
- Fairness (DESIGN.md section 1): knowledge only through perception ("Knowing about or crafting an item it has never seen nor unlocked" is an anti-goal); actions take player time (inventory open pauses apply to gear changes); 4.5-block block reach, 3-block entity reach; no free items.
- Architecture (DESIGN.md section 2): custom mob; the fake player only at the moment of interaction.
- "A required dependency: every mod stays optional, detected at runtime" (spec anti-goal). Trinkets, Artifacts, Too Many Bows, Immersive Armors, Simply Swords, Better Combat: never in `depends`, never loaded unless present.
- "Every Drifter in the same 'best' gear" is a failure even if it works (spec anti-goal).
- The `drifter_weapons/*.json` families "stay and win when present"; hand-written rules (`hands/UniqueAbility`, `simplyswords.json`) override guesses.
- Tunable numbers in data files or named constants; saved data versioned (DESIGN.md section 20).
- Every new `DrifterConfig` setting goes in `src/client/java/com/dinglebob/drifter/client/config/DrifterConfigScreen.java`.
- Goal 11: "know every recipe" is off by default and "only affects knowing items and recipes, never seeing through walls or knowing where things are".
- The addon (`src/civ`) reaches Drifter only through `com.dinglebob.drifter.api`; grow the API in the same change, document it in `docs/API.md`, never break the addon build; keep addon edits to this feature (the user works on the addon elsewhere).
- Stay out of whim files (another session owns them).
- Testing is `./gradlew build` plus headless smoke scripts (`python tools/smoke.py <project dir> <script> <log>`); there is no JUnit or gametest source set. Stop test servers by their own PID; never replace the jar while Minecraft runs; TESTING.md in plain language.

## Review Focus

1. **A mod whose hover-text code is client-only** (calls `Screen.hasShiftDown()` or `I18n` in `appendHoverText`) on a dedicated server or the integrated server thread: no crash and no log spam; that item just gets no text guess and is still valued and used. Pinned in Task 7.
2. **Wearables with odd modifiers**: an elytra, carved pumpkin or mob head in an armor slot, armor with `ADD_MULTIPLIED_*` or negative modifiers, a staff with no attack damage: nothing scores NaN or negative-breaks a choice, an elytra never replaces a chestplate, a staff is never wielded. Pinned in Tasks 1 and 5.
3. **Old saves and removed mods**: a Drifter saved before this update (no `DrifterGear` tag) loads and seeds its knowledge from what it carries, wears, stores and its favourite family; a saved item or belief whose mod was removed is skipped quietly. Pinned in Task 2.
4. **An optional mod missing or changed** (Trinkets' API renamed, Better Combat without a `category`): the compat class logs one warning and switches itself off, and the game runs the vanilla way; no optional class is touched when the mod is absent. Pinned in Tasks 1 and 11.
5. **Big modpacks and many Drifters**: knowledge checks scan thousands of items and recipes every 10 s per Drifter; they must use per-reload caches (recipe index, kind cache) so 100 Drifters in a big pack add no visible tick time. Pinned in Task 2.

---

## File Structure

New (common, `src/main/java/com/dinglebob/drifter/`):

| File | Responsibility |
| --- | --- |
| `gear/GearData.java` | One reload listener for `data/<ns>/drifter_gear/*.json` (kinds, styles, power words); holds the parsed tables. |
| `gear/GearKinds.java` | `Use` enum, `Kind` record, `of(ItemStack)` with a per-reload cache. |
| `gear/GearValue.java` | Melee, ranged, armor and trinket value, rarity, cost (dearest ingredient), points for `ItemValues`. |
| `gear/RecipeIndex.java` | Per-reload: each gear item's recipe key ingredient, and which items each ingredient unlocks. |
| `gear/GearLore.java` | Per-Drifter knowledge: known items (how learned), power beliefs, loyal kind, friends' kinds; save/load v1. |
| `gear/GearTaste.java` | Style per Drifter, style bonus, `liking`, the "good enough" pick. |
| `gear/Powers.java` | `Power` enum, hover-text guess, sensible-use rules, power penalties in choices. |
| `gear/PowerWatch.java` | Learning by use: watches what follows its own hit/shot/throw and firms up beliefs. |
| `brain/GearPlans.java` | Strategist targets for known modded weapons and armor, and value-based "has armor this good". |
| `compat/TrinketsCompat.java` | Trinkets by reflection: slots, can-equip, equip, modifiers. |
| `executor/task/ThrowTridentTask.java` | Charge, aim and throw a trident; hand back to the fight. |
| `executor/task/WearTrinketsTask.java` | Put on its best trinkets, one click each, inventory pause. |
| `mixin/TridentItemMixin.java` | A Drifter lets go of a trident as a player does (vanilla throws only for players). |
| `command/GearCommands.java` | `/drifter gear ...` debug commands. |

New data: `src/main/resources/data/drifter/drifter_gear/{kinds,styles,powers}.json`,
`src/main/resources/data/trinkets/entities/drifter.json`, `src/main/resources/data/drifter/drifter_talk/topics_gear_mods.json`,
smoke scripts `tools/smoke-gear-*.txt`.

Modified (main line paths): `social/ItemValues.java`, `brain/Weapons.java`, `hands/Combat.java`, `hands/Shields.java`,
`hands/Archery.java`, `hands/UniqueAbility.java`, `executor/task/WearArmorTask.java`, `executor/task/ShootTask.java`,
`executor/task/UseItemTask.java` (TODO line), `executor/task/CollectItemsTask.java`, `brain/Strategist.java`,
`brain/activity/CombatActivity.java`, `brain/activity/MineTripActivity.java`, `trade/Trading.java`, `trade/TradeBook.java`
caller, `trade/BuyGoal.java`, `perception/Senses.java`, `entity/DrifterEntity.java`, `social/talk/Conversations.java`,
`DrifterConfig.java`, `client/config/DrifterConfigScreen.java`, `command/DrifterCommands.java` (register + config),
`command/HandsCommands.java` (`weapon`), `api/DrifterApi.java`, `docs/API.md`, `src/main/resources/drifter.mixins.json`,
`brain/Goals.java` (equip goal, ~:3154), civ `Trades.java`, `Market.java`, `TownDay.java`, docs `PROGRESS.md`, `TESTING.md`.

## Test harness (used by every task)

- The smoke run uses the project's own `run/`. Once, before Task 1: copy `run/mods`, `run/server.properties` and
  `run/eula.txt` from the main checkout (`C:\Users\mutmu\Documents\aishit\drifters\run`) if this worktree has none, then add
  from `C:\Users\mutmu\AppData\Roaming\ModrinthApp\profiles\DRIFTER TEST\mods`: `trinkets-3.10.0.jar`,
  `artifacts-fabric-13.2.4.jar`, `too_many_bows-fabric-1.21.1-4.0.1.jar`, `immersive_armors-1.7.6+1.21.1-fabric.jar`
  (architectury, Better Combat, Simply Swords and Simply Bows are already there). Each task's script runs once with these
  and the final task also runs once with them moved out (`run/mods_off/`), to prove nothing is required.
- Every script starts: `until Done_( 900`, `wait 3`, `forceload add ~-112 ~-112 ~112 ~112`, `drifter thoughts log true`;
  spawns with `drifter spawn <Name>` (apart: `execute positioned ~40 ~ ~ run ...`), then `drifter tier full all` (headless
  Drifters are FAR tier otherwise). `/drifter` commands act on the nearest Drifter.
- "Passes" means: the server log (the script's `<log>` file) contains the listed lines, and has no `Exception` or
  `ERROR` line mentioning `drifter`. Check with `grep -n "<text>" <log>`.

---

### Task 1: Item value and kinds (with a debug command)

**Files:**
- Create: `gear/GearData.java`, `gear/GearKinds.java`, `gear/GearValue.java`, `gear/RecipeIndex.java` (cost part only), `command/GearCommands.java`, `data/drifter/drifter_gear/kinds.json`, `tools/smoke-gear-value.txt`
- Modify: `social/ItemValues.java:23-80` (modded gear points), `compat/BetterCombatCompat.java` (add `category`), `Drifter.java` (register `GearData`), `command/DrifterCommands.java` (register `GearCommands`), `gradle.properties` (version)

**Interfaces:**
- Produces:
  - `enum GearKinds.Use { MELEE, BOW, CROSSBOW, TRIDENT, THROWN, STAFF; boolean usable() }` (THROWN and STAFF: false, goal 9)
  - `record GearKinds.Kind(String id, Use use)`; `@Nullable static Kind GearKinds.of(ItemStack stack)`; `static boolean GearKinds.usableWeapon(ItemStack)`
  - `static double GearValue.melee(DrifterEntity d, ItemStack s)` (= `Combat.weaponStats(d, s).perSecond()`)
  - `static double GearValue.armor(ItemStack s, EquipmentSlot slot)`; `@Nullable static EquipmentSlot GearValue.wornSlot(LivingEntity e, ItemStack s)`
  - `static double GearValue.ranged(ItemStack s)`; `static int GearValue.rarity(ItemStack s)`; `static double GearValue.cost(Level l, Item i)`
  - `static double GearValue.points(ItemStack s)` (trade points; `NaN`-free, >= 0); `static boolean GearValue.isGear(ItemStack s)`
  - `@Nullable static String BetterCombatCompat.category(ItemStack s)`
  - `static GearData.Tables GearData.tables()` (kinds list now; styles and power words added in Tasks 3 and 7)

- [ ] **Step 1: Merge the main line and set the version**

Run: `git fetch -q origin && git merge origin/claude/gifted-archimedes-n6yen5` (resolve conflicts keeping the main line's code;
this branch adds only the spec). Set `version=0.23.32-side.N` (next free N, Global Constraints).
Expected: `./gradlew build` → `BUILD SUCCESSFUL`, `build/libs/drifter-0.23.32-side.N.jar` and `drifter_civilizations-0.23.32-side.N.jar`.

- [ ] **Step 2: Write the smoke script `tools/smoke-gear-value.txt`** (the failing test)

```
(header lines from "Test harness")
drifter spawn Vala
drifter gear item minecraft:iron_sword
drifter gear item minecraft:iron_chestplate
drifter gear item minecraft:elytra
drifter gear item minecraft:crossbow
drifter gear item minecraft:trident
drifter gear item simplyswords:iron_katana
drifter gear item immersive_armors:warrior_chestplate
drifter gear item too_many_bows:flame_bow
drifter gear item minecraft:stick
drifter gear item minecraft:iron_chestplate[minecraft:attribute_modifiers={modifiers:[{type:"generic.armor",amount:0.5,operation:"add_multiplied_total",slot:"chest",id:"t:m"},{type:"generic.armor",amount:-3,operation:"add_value",slot:"chest",id:"t:n"}]}]
stop
```

- [ ] **Step 3: Run it to see it fail**

Run: `python tools/smoke.py . tools/smoke-gear-value.txt build/smoke-gear-value.log`
Expected: `Unknown or incomplete command` for each `drifter gear` line.

- [ ] **Step 4: Write `data/drifter/drifter_gear/kinds.json`**

Shape (a later file with the same `id` replaces it; `requires` skips a file whose mod is absent, as `drifter_weapons` does):
```json
{"kinds": [
  {"id": "katana", "use": "melee", "words": ["katana", "tachi", "odachi", "uchigatana"], "tags": ["c:tools/katana"], "better_combat": ["katana"]},
  {"id": "staff", "use": "staff", "words": ["staff", "wand", "rod", "scepter", "sceptre"], "tags": ["c:tools/wand"], "better_combat": ["staff", "wand"]}
]}
```
Kinds to ship: sword, longsword, katana, dagger, rapier, greatsword (claymore, greatblade), spear (lance, glaive, halberd,
pike), hammer (mace, warhammer, maul), axe (battleaxe, waraxe), scythe (sickle), sai, chakram (thrown), throwing (javelin,
shuriken, knife words only with a `c:tools/thrown` tag) as `thrown`, staff as `staff`, bow, crossbow, trident.

- [ ] **Step 5: Implement `GearKinds.of`**

Order (first hit wins), cached per item until the next reload:
1. A `drifter_weapons` family item: the kind whose words match the family id's path ("simplyswords:longsword" → longsword).
2. `ShieldItem` → null (shields stay with `hands/Shields`).
3. `instanceof CrossbowItem` → crossbow; `BowItem` → bow; `TridentItem` → trident.
4. `BetterCombatCompat.category(stack)` in a kind's `better_combat` list.
5. The item in a kind's `tags`.
6. A kind word as a whole `_`-separated word of the id path, then of the English name (`getDescription().getString()`), longest word first ("great_katana" → katana only if no "greatsword" word matches).
7. In `drifter:melee_weapons`, or a non-`DiggerItem` with a main-hand `ATTACK_DAMAGE` `ADD_VALUE` of 2 or more → `sword`.
8. Otherwise null.

`usableWeapon(stack)`: kind non-null and `kind.use().usable()`.

- [ ] **Step 6: Implement `GearValue`**

- `armor(stack, slot)`: sum of the stack's modifiers for `slot` (`stack.forEachModifier(slot, ...)`), `ADD_VALUE` only: `ARMOR*10 + ARMOR_TOUGHNESS + KNOCKBACK_RESISTANCE*10 + OTHER_MOD_WEIGHT*Σ(other positive ADD_VALUE amounts)`, clamped at >= 0. `OTHER_MOD_WEIGHT = 2.0`. Equals today's `getDefense()*10 + getToughness()` for vanilla armor (iron chestplate 60, diamond 82; netherite adds knockback 1.0 → 93).
- `wornSlot(e, s)`: `e.getEquipmentSlotForItem(s)` if it is HEAD, CHEST, LEGS or FEET **and** `armor(s, slot) > 0`, else null (elytra, pumpkins and heads have no armor: not armor to the chooser).
- `ranged(s)`: `BASE_BOW = 10`, `BASE_CROSSBOW = 12`, `BASE_TRIDENT = 9`, `+3` per level of power, punch, flame, infinity, multishot, piercing, quick charge, loyalty, impaling; `+5` per rarity step above common; `+5` if the item's class (or a class between it and `BowItem`/`CrossbowItem`) overrides `releaseUsing` (reuse `DrifterEntity.ownRelease`, made `public static`); `+ wear left (0..1)`.
- `rarity(s)`: `s.getRarity().ordinal()` (COMMON 0 .. EPIC 3).
- `cost(level, item)`: points (`ItemValues.each`) of the item's recipe key ingredient (`RecipeIndex.key(level, item)`), 0 without a recipe; cached per reload.
- `points(s)` for `ItemValues`: weapons `toolTierEquivalent * 3` where the tier equivalent is `Weapons.tier` by DPS (1 wood .. 5 netherite, gold 1.5); armor `armorTierEquivalent * 4` by `armor(s, slot)` against the vanilla piece for that slot (leather 1 .. netherite 5); ranged `ranged(s) / 2`; then `+5 * rarity`, and never less than `cost`. Vanilla items keep today's exact points.
- `isGear(s)`: `GearKinds.of(s) != null || armor(s, its slot) > 0 || ShieldItem` (Task 11 adds `|| TrinketsCompat.isTrinket(s)`).

- [ ] **Step 7: Use `GearValue.points` in `ItemValues.each`**

Replace the `ArmorItem` and `TieredItem` branches with `GearValue.isGear(stack) ? GearValue.points(stack)`, keeping the
explicit vanilla treasures (trident 60 and the rest) above it. Vanilla points must not change.

- [ ] **Step 8: Add `BetterCombatCompat.category(ItemStack)`**

Reflection on `WeaponAttributes#category()` (a `String`); look it up inside `active()` as optional: if the method is missing,
`category` returns null and the rest of Better Combat support keeps working (one info line, not a failure).

- [ ] **Step 9: Implement `/drifter gear item <item>` in `GearCommands`**

Output, one line, prefixed for grep:
`[gear] <id>: kind <kind|none> (<use>), melee <x.x>, ranged <x.x>, armor <x.x> (<slot>), rarity <n>, cost <x.x>, points <x.x>`

- [ ] **Step 10: Build and run the smoke test**

Run: `./gradlew build` then `python tools/smoke.py . tools/smoke-gear-value.txt build/smoke-gear-value.log`
Expected lines:
- `[gear] minecraft:iron_sword: kind sword (MELEE), melee 9.6` … `points 9.0`
- `[gear] minecraft:iron_chestplate: kind none` … `armor 60.0 (chest)` … `points 12.0`
- `[gear] minecraft:elytra: kind none` … `armor 0.0 (none)`
- `[gear] minecraft:crossbow: kind crossbow (CROSSBOW)`; `[gear] minecraft:trident: kind trident (TRIDENT)` … `points 60.0`
- `[gear] simplyswords:iron_katana: kind katana (MELEE)`
- `[gear] immersive_armors:warrior_chestplate:` … `armor` > 0 … `(chest)`
- `[gear] too_many_bows:flame_bow: kind bow (BOW)` … `ranged` >= 20 (own release + rarity)
- `[gear] minecraft:stick: kind none (none), melee 4.0` (bare-hand damage 1 x speed 4)
- the modified chestplate: `armor 0.0 (none)` (the multiplier is ignored, the negative add clamps at 0; no `NaN`)

Then rerun with the four new mods in `run/mods_off/`: the modded lines report `Unknown item`, no exception.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/dinglebob/drifter/gear src/main/java/com/dinglebob/drifter/command/GearCommands.java src/main/resources/data/drifter/drifter_gear tools/smoke-gear-value.txt src/main/java/com/dinglebob/drifter/social/ItemValues.java src/main/java/com/dinglebob/drifter/compat/BetterCombatCompat.java src/main/java/com/dinglebob/drifter/Drifter.java src/main/java/com/dinglebob/drifter/command/DrifterCommands.java gradle.properties
git commit -m "Gear value and kinds from the item itself; /drifter gear item"
```

---

### Task 2: What it knows of items (saved), and the "know every recipe" setting

**Files:**
- Create: `gear/GearLore.java`, `tools/smoke-gear-lore.txt`
- Modify: `gear/RecipeIndex.java` (unlock index), `entity/DrifterEntity.java` (field, getter, save `DrifterGear`, load, check in `aiStep` near `:1343`), `perception/Senses.java` (`noticeItems` `:856`, `sawContents` `:1334`), the caller of `TradeBook.saw` and `trade/BuyGoal.java:109` (shop offers read), `DrifterConfig.java` (setting), `client/config/DrifterConfigScreen.java` (toggle), `command/DrifterCommands.java:416` (config subcommand), `command/GearCommands.java`

**Interfaces:**
- Consumes: `GearValue.isGear`, `GearKinds.of`, `GearValue.cost` (Task 1); `Weapons.family`, `Strategist.has`.
- Produces:
  - `enum GearLore.How { INNATE, OWNED, SEEN, UNLOCKED, TOLD }`
  - `boolean GearLore.knows(DrifterEntity d, Item item)`; `static boolean GearLore.innate(DrifterEntity d, Item item)`
  - `boolean GearLore.learn(DrifterEntity d, Item item, How how, String where)` (true if new; writes a thought `learned of <Name> (<where>)`)
  - `void GearLore.see(DrifterEntity d, ItemStack stack, String where)` (gear only; SEEN)
  - `void GearLore.check(DrifterEntity d)`; `CompoundTag GearLore.save()`; `void GearLore.load(CompoundTag tag)`; `static final int SAVE_VERSION = 1`
  - `List<Item> GearLore.learnedItems()`; `void GearLore.forget()` (debug)
  - `GearLore DrifterEntity.getGearLore()`; `boolean DrifterEntity.knowsItem(Item item)`
  - `static List<Item> RecipeIndex.unlockedBy(Level l, Item ingredient)`; `@Nullable static Ingredient RecipeIndex.key(Level l, Item result)`
  - `static boolean DrifterConfig.knowEveryRecipe()`; `static void DrifterConfig.setKnowEveryRecipe(boolean on)` (field `knowEveryRecipe = false`)

- [ ] **Step 1: Write `tools/smoke-gear-lore.txt`**

```
(header)
drifter spawn Nell
execute as @e[type=drifter:drifter,name=Nell] at @s run drifter personality reroll newbie
drifter gear item too_many_bows:arcane_bow
drifter gear item immersive_armors:warrior_chestplate
setblock ~2 ~ ~ chest{Items:[{Slot:0b,id:"immersive_armors:warrior_chestplate",count:1}]}
drifter container ~2 ~ ~ takeall
wait 10
drifter gear item immersive_armors:warrior_chestplate
drifter gear item immersive_armors:bone_chestplate
drifter give <the item named by the bone chestplate line's "key" part; bones expected> 1
wait 12
drifter gear
drifter config knoweveryrecipe true
drifter gear item too_many_bows:shulker_blast
drifter config knoweveryrecipe false
save-all
stop
(second run, same world)
until Done_( 900
drifter gear
stop
```

- [ ] **Step 2: Run it to see it fail**

Expected: `/drifter gear item` lines lack a `knows it:` part; `drifter gear` and `config knoweveryrecipe` are unknown.

- [ ] **Step 3: Implement `GearLore`**

- `innate(d, item)`: `DrifterConfig.knowEveryRecipe()` → true (goal 11; items and recipes only, Lore areas untouched); namespace `minecraft` → true; a `drifter_weapons` family item → true (spec: families keep today's behaviour); a unique from a weapon file → false; otherwise true only for a veteran (`KNOWLEDGE >= VETERAN_KNOWLEDGE = 0.7`, or archetype PVP_SWEAT or SPEEDRUNNER) and only if `RecipeIndex.key` is non-null (it knows the installed mods' craftable gear, not their loot); never for NEWBIE.
- `knows`: `innate || learned.containsKey(item)`.
- `see`: ignores non-gear and already-known items; `learn(SEEN, where)`.
- `check(d)` (every 200 ticks, offset 100 from `lore.check` so they don't share a tick):
  1. First check after a load with no saved data (`seedPending`): learn every gear item carried, worn and in `home.stored()` as OWNED, and its favourite family's items, silently (no thought). This is the migration for existing Drifters.
  2. Owned now (carried or worn) → OWNED.
  3. Unlocks: for each distinct item it has (inventory and `home.stored()` keys), `RecipeIndex.unlockedBy(level, item)` → learn UNLOCKED, where `"unlocked: has <ingredient name>"`.
  4. Seen on others: each `getSenses().tracked()` entry with `visibleNow`, a `LivingEntity`, within `Senses.DETAIL_RANGE`: main hand, off hand and armor slots → `see(..., "held by <name>")` / `"worn by <name>"`.
- Save tag `{Version:1, Known:[{Item:"ns:id", How:"seen"}...]}`; `load` skips ids not in `BuiltInRegistries.ITEM` and unknown `How` names; an empty or missing tag sets `seedPending`.

- [ ] **Step 4: Implement `RecipeIndex` unlocks**

Built lazily per reload (cleared from `GearData`'s listener): for every recipe whose result `isGear`, its key ingredient is
the ingredient whose first matching item has the highest `ItemValues.each` (ties: the one that isn't a stick or planks);
`unlockedBy` maps each item matching a key ingredient to the results it unlocks. One pass over recipes per reload.

- [ ] **Step 5: Hook perception**

`Senses.noticeItems` (each seen `ItemEntity` stack, "on the ground"), `Senses.sawContents` (each stack, "in a chest"),
where `TradeBook.saw` is called ("in a trade": each offer's result), `BuyGoal` where it reads `shop.offers` ("in a shop").
Each is one `drifter.getGearLore().see(...)` call.

- [ ] **Step 6: The setting**

`DrifterConfig.Data.knowEveryRecipe = false`, getter and setter as `parkour`. Screen: in the "Drifters" heading, a row
`toggle("Know every recipe", DrifterConfig.knowEveryRecipe(), DrifterConfig::setKnowEveryRecipe, "Like a player with Just Enough Items: every Drifter knows every item and recipe from the start. Off: they learn modded gear by seeing it or holding what it's made from. Never shows them where anything is.")`
(pair it with an existing single-toggle row or `null`). Command: `/drifter config knoweveryrecipe <bool>` → `Know every recipe on|off`.

- [ ] **Step 7: Debug output**

`/drifter gear item` appends `, key <ingredient id|none>` (`RecipeIndex.key`) and `; <name> knows it: yes (<how>)|no`. `/drifter gear` prints
`[gear] <name>: knows <n> modded items (<ids, first 12>); know every recipe <on|off>`. `/drifter gear learn <item>` and
`/drifter gear forget` for tests.

- [ ] **Step 8: Build and run**

Expected lines (first run): `too_many_bows:arcane_bow` … `knows it: no`; after the chest,
`learned of Warrior Chestplate (in a chest)` and `warrior_chestplate` … `knows it: yes (owned)` or `(seen)`; after the
key ingredient, `learned of Bone Chestplate (unlocked: has <ingredient name>)`; with the setting on,
`shulker_blast` … `knows it: yes (innate)`; `Know every recipe off`. Second run: `[gear] Nell: knows <n> modded items`
with the same n as before `stop` (saved).

Old saves and removed mods (Review Focus 3): start once from a world saved by the plain 0.23.32 jar with a Drifter
carrying a Too Many Bows bow: `/drifter gear` lists it (seeded), no load error. Then start Nell's world with Immersive
Armors moved to `run/mods_off/`: it loads, `/drifter gear` lists one fewer item, no exception.

Performance (Review Focus 5): `drifter spawnmany 60 48`, `drifter tier full all`, `wait 60`, `drifter perf` on this build
and, back to back, on the 0.23.32 jar (memory: perf runs must be compared back to back): the server-thread CPU line
differs by under 5%. `GearLore.check` must not iterate recipes or the item registry (only `RecipeIndex` lookups for items
it has).

- [ ] **Step 9: Commit**

```bash
git add -A src/main tools/smoke-gear-lore.txt src/client
git commit -m "Gear knowledge by perception (seen, owned, unlocked), saved; Know every recipe setting"
```

---

### Task 3: Taste (gear styles)

**Files:**
- Create: `gear/GearTaste.java`, `data/drifter/drifter_gear/styles.json`, `tools/smoke-gear-taste.txt`
- Modify: `gear/GearData.java` (styles table), `gear/GearLore.java` (loyal kind, friends' kinds), `command/GearCommands.java`

**Interfaces:**
- Consumes: `GearValue`, `GearKinds`, `GearLore` (Tasks 1-2); `Weapons.favorite`; `DrifterGroups`/bonds for friends.
- Produces:
  - `enum GearTaste.Style { PRACTICAL, FLASHY, LOYAL, FOLLOWER }`; `static Style GearTaste.style(DrifterEntity d)` (seeded by UUID, cached per `GearData` version, not saved)
  - `static double GearTaste.bonus(DrifterEntity d, ItemStack s)` (style bonus, -0.3..+1.0)
  - `static double GearTaste.liking(DrifterEntity d, ItemStack s, double value)` = `value * (1 + tasteStrength * bonus + noise(d, item))`
  - `static <T> @Nullable T GearTaste.pick(DrifterEntity d, List<T> options, ToDoubleFunction<T> value, Function<T, ItemStack> stack)` (the good-enough rule)
  - `@Nullable String GearLore.loyalKind()`; `void GearLore.noteFriendGear(String kindOrSet)`; `double GearLore.friendShare(String kindOrSet)`
  - `static String GearTaste.setKey(ItemStack armor)` (id with the slot word removed: `immersive_armors:warrior_`)

- [ ] **Step 1: Write `tools/smoke-gear-taste.txt`**

Spawn 12 Drifters in a row (`execute positioned ~<4i> ~ ~40 run drifter spawn T<i>`), then for each
`execute as @e[type=drifter:drifter,name=T<i>] at @s run drifter gear`. Then `drifter gear pick weapon` on the nearest with
`drifter give` of: `minecraft:diamond_sword`, `simplyswords:diamond_katana`, `simplyswords:diamond_longsword`,
`minecraft:golden_sword` enchanted (`golden_sword[enchantments={levels:{"minecraft:sharpness":3}}]`).

- [ ] **Step 2: Run it to see it fail** (no `style` in the output; `gear pick` unknown).

- [ ] **Step 3: Write `styles.json`**

```json
{"taste_strength": 0.25, "noise": 0.05, "good_enough": 0.75,
 "styles": {
  "practical": {"traits": {"efficiency": 1.0, "skill": 0.6}, "archetypes": {"speedrunner": 2.0, "pvp_sweat": 2.0, "miner": 1.3}},
  "flashy":    {"traits": {"creativity": 0.8, "greed": 0.6, "thrift": -0.6}, "archetypes": {"bandit": 1.5, "hoarder": 1.5}},
  "loyal":     {"traits": {"patience": 0.8, "tidiness": 0.6}, "archetypes": {"homebody": 1.5, "builder": 1.3}},
  "follower":  {"traits": {"sociability": 1.0}, "archetypes": {"helper": 2.0, "newbie": 1.5}}},
 "bonus": {"flashy_rarity_step": 0.25, "flashy_enchanted": 0.2, "flashy_modded": 0.2, "flashy_cost": 0.2,
           "loyal_kind": 0.8, "loyal_set": 0.6, "follower_share": 0.8, "favorite_family": 0.4}}
```
Style weight: `Π max(0.05, 1 + 2*w*(trait - 0.5))` times the archetype factor; pick seeded by
`uuid.getLeastSignificantBits() ^ 0x57E1_E5L`, the same way `Weapons.pick` does.

- [ ] **Step 4: Implement `GearTaste`**

- `bonus`: PRACTICAL 0; FLASHY: rarity steps, enchanted, non-`minecraft` namespace, `cost >= 10`; LOYAL: kind == `loyalKind()` (else favourite family's kind), armor `setKey` matching what it wears in the most slots; FOLLOWER: `follower_share * friendShare(kind or setKey)`, and with no friends noted it acts PRACTICAL. Every style: `favorite_family` if `Weapons.family(s) == Weapons.favorite(d)` (keeps today's 10% lean at strength 0.25).
- `noise(d, item)`: deterministic in `[-noise, +noise]` from `uuid ^ item id hash` (two Drifters never rank near-equal items the same way).
- `pick`: drop options whose `value < good_enough * best value`, then the highest `liking`.
- `GearLore.loyalKind`: set the first time it wields a usable weapon for 3+ fights (saved in the `DrifterGear` tag as `LoyalKind`; additive, still version 1); `noteFriendGear`: in `GearLore.check` step 4, items held or worn by its group members and friends (trust >= the "friend" level the talk Facts use) count by kind/set key (not saved, decays by half each in-game day).

- [ ] **Step 5: Debug output**

`/drifter gear` adds `style <style> (loyal to <kind|none>)`. `/drifter gear pick weapon|armor` prints each candidate
`[gear] pick <id> value <v> liking <l>` and `[gear] picked <id>`.

- [ ] **Step 6: Build and run**

Expected: the 12 `drifter gear` lines show at least three different styles (with 12 seeded Drifters the chance of fewer
is under 1%; if fewer, check the weights before changing seeds). `gear pick weapon` never picks the golden sword (below
good-enough of diamond), and two Drifters of different styles pick different items in at least one of three runs with
different spawns.

- [ ] **Step 7: Commit** (`git commit -m "Gear taste: practical, flashy, loyal, follower; good-enough pick"`)

---

### Task 4: Choosing weapons and bows by value, kind and taste

**Files:**
- Modify: `hands/Combat.java:82-105` (`bestWeaponSlot`), `brain/Weapons.java` (`isWeapon`, `tier`), `hands/Shields.java:92-115` (`isBow`, `bowScore`, `bestBowSlot`), `trade/Trading.java:251-285` (weapon branch), `command/HandsCommands.java:94` (`weapon` output), `tools/smoke-gear-weapons.txt`

**Interfaces:**
- Consumes: `GearKinds.usableWeapon`, `GearValue.melee`, `GearValue.ranged`, `GearTaste.liking`/`pick` (Tasks 1, 3).
- Produces:
  - `Combat.bestWeaponSlot(DrifterEntity)` keeps its signature; scores weapons (`Weapons.isWeapon`) by `GearTaste.liking(d, s, melee)` with the good-enough rule; non-weapons (tools, junk) still by raw DPS; never a THROWN or STAFF item.
  - `Weapons.isWeapon(ItemStack)`: today's rule `|| GearKinds.of(s) usable MELEE or TRIDENT`.
  - `Weapons.tier(DrifterEntity, ItemStack)`: the DPS-match also for `isWeapon` items outside the tag.
  - `Shields.isBow` unchanged (BowItem); `Shields.isLauncher(ItemStack)` = bow or `CrossbowItem` (used from Task 9); `Shields.bowScore(DrifterEntity, ItemStack)` = `GearTaste.liking(d, s, GearValue.ranged(s))` (old one-argument overload removed; callers updated).

- [ ] **Step 1: Write `tools/smoke-gear-weapons.txt`**

Two Drifters with forced styles (`drifter personality set efficiency 1.0` etc. then `drifter gear` to confirm style),
each given `minecraft:diamond_sword`, `simplyswords:diamond_katana`, `simplyswords:diamond_greataxe`, `minecraft:wooden_axe`;
then `drifter weapon` on each, `summon zombie ~3 ~ ~` next to each, `wait 10`, `drifter inventory`.
A third given a stick made into a staff:
`minecraft:stick[minecraft:attribute_modifiers={modifiers:[{type:"generic.attack_damage",amount:20,operation:"add_value",slot:"mainhand",id:"t:d"}]},minecraft:item_name='"Oak Staff"']`
(kind staff by name word), plus a stone sword.

- [ ] **Step 2: Run it to see it fail** (both pick the same top-DPS item; the "Oak Staff" stick is wielded).

- [ ] **Step 3: Implement** the four changes above. Archery and ShootTask call sites of `bowScore` pass the Drifter.

- [ ] **Step 4: Build and run**

Expected: `drifter weapon` prints `best weapon on it:` with the item each style picks; the third Drifter's best weapon is
the stone sword (the staff is skipped); both kill their zombie (`killed` thought or `the fight is over`). Vanilla-only run
(mods off): picks are identical to 0.23.32's (`diamond sword` over everything).

- [ ] **Step 5: Commit** (`git commit -m "Weapons and bows chosen by value, kind and taste"`)

---

### Task 5: Choosing armor by value and taste

**Files:**
- Modify: `executor/task/WearArmorTask.java` (`score`, `hasUpgrade`, `wearBest`, gold rule kept), `trade/Trading.java:306-320` (`armorScore`, `bestArmor`, `want` armor branch), `tools/smoke-gear-armor.txt` (talk's `Facts.armor` word stays material-based: untouched)

**Interfaces:**
- Consumes: `GearValue.armor`, `GearValue.wornSlot`, `GearTaste.liking`, `GearTaste.setKey` (Tasks 1, 3).
- Produces: `static double WearArmorTask.score(DrifterEntity d, ItemStack s, EquipmentSlot slot)` (now public): `GOLD_IN_THE_NETHER` rule first (any item whose armor material is gold, via `ArmorItem` as today), else `GearTaste.liking(d, s, GearValue.armor(s, slot))` when `GearValue.wornSlot(d, s) == slot`; empty 0; anything else -1. Good-enough applies per slot (a flashy Drifter won't trade iron for leather).

- [ ] **Step 1: Write `tools/smoke-gear-armor.txt`**

A loyal Drifter (`personality set patience 1.0`, `set tidiness 1.0`, others low; confirm `style loyal`) wearing a full
iron set given `immersive_armors:warrior_chestplate` only; a practical one given the same; each `drifter wear`, `wait 5`,
`drifter inventory`. A third given `minecraft:elytra`, `minecraft:carved_pumpkin` and an iron chestplate, `drifter wear`.
A fourth given `minecraft:leather_chestplate` enchanted with protection 4 vs worn iron, `drifter wear`.

- [ ] **Step 2: Run it to see it fail** (warrior chestplate never put on: `ArmorItem` defense only… or both act alike).

- [ ] **Step 3: Implement** as in Interfaces. `Trading.armorScore(stack)` becomes `GearValue.armor(stack, slot)` over
`GearValue.wornSlot`; `want` armor branch uses `WearArmorTask.score` against its best for the slot.

- [ ] **Step 4: Build and run**

Expected: the practical one wears the warrior chestplate if its `armor` value beats iron's 60 (check the Task 1 line), the
loyal one keeps the matching iron set unless the warrior piece is far better (beyond good-enough plus taste); the third
wears the iron chestplate and keeps the elytra and pumpkin in its pack; the fourth keeps iron. No `has no better armor`
loop in the thoughts (wear runs once).

- [ ] **Step 5: Commit** (`git commit -m "Armor chosen by attribute value and taste (matching sets for the loyal)"`)

---

### Task 6: Getting it: known modded gear as Strategist targets

**Files:**
- Create: `brain/GearPlans.java`, `tools/smoke-gear-plans.txt`
- Modify: `brain/Strategist.java:80-83` (`armor` predicate), `:121-124` (`diamondArmor`), `:279-327` (`laterTargets`), `brain/activity/MineTripActivity.java:218-227`, `command/HandsCommands.java:94-121`

**Interfaces:**
- Consumes: `GearLore.knows`, `GearTaste.pick`, `GearValue.armor`, `Weapons.target`, `Weapons.craftable`, `Weapons.ironCost`, `Weapons.tier`.
- Produces:
  - `@Nullable static Strategist.Target GearPlans.weaponTarget(DrifterEntity d, Weapons.Tier tier)`: options = `Weapons.target(d, tier)`'s item plus every item it `knows` that `isWeapon`, is MELEE-usable, `Weapons.craftable`, and whose `Weapons.tier == tier`; `GearTaste.pick`; satisfied `d -> Weapons.hasTier(d, tier)` (as today).
  - `static List<Strategist.Target> GearPlans.armorTargets(DrifterEntity d, Weapons.Tier tier)` (IRON or DIAMOND; chest, legs, helmet, boots in that order): options per slot = the vanilla piece plus every known craftable item with `wornSlot == slot` and `armor >= ARMOR_FLOOR * vanilla piece's armor` (`ARMOR_FLOOR = 0.9`); `GearTaste.pick`; satisfied = `hasArmorAtLeast(d, slot, tier)`.
  - `static boolean GearPlans.hasArmorAtLeast(DrifterEntity d, EquipmentSlot slot, Weapons.Tier tier)` (carried, worn or stored, `armor(s, slot) >= vanilla piece's`).

- [ ] **Step 1: Write `tools/smoke-gear-plans.txt`**

A settled Drifter that reaches the iron stage quickly: `drifter home here`, a chest placed beside it
(`setblock ~1 ~ ~ chest`), `drifter giveall` of `stone_pickaxe`, `furnace`, `iron_pickaxe`, `bucket`, `shield`, 30 `iron_ingot`, and
`drifter gear learn immersive_armors:warrior_chestplate`; set its style practical; then `drifter brain` and `drifter weapon`
and `drifter gear plans` (new: prints `GearPlans` picks per tier and slot); `sprint 6000`; `drifter inventory`.

- [ ] **Step 2: Run it to see it fail** (`gear plans` unknown; targets only vanilla).

- [ ] **Step 3: Implement**

`Strategist.armor(...)`/`diamondArmor(...)` become `GearPlans.hasArmorAtLeast` for their slot and tier (the static
`IRON_TARGETS`/`DIAMOND_TARGETS` lists keep their order and indexes: `stage()` reads them). In `laterTargets`, replace
the four armor entries of each tier with `GearPlans.armorTargets(d, tier)` and the two `Weapons.target` calls with
`GearPlans.weaponTarget`. `MineTripActivity` counts iron for the targets `laterTargets` would pick (via
`Weapons.ironCost` of each target's item) instead of the static list. `/drifter weapon` uses `weaponTarget`.

- [ ] **Step 4: Build and run**

Expected: `gear plans` shows `chest at iron: immersive_armors:warrior_chestplate` (if its recipe is a crafting-table one;
if it's smithing-only, the plan shows the iron chestplate: write that in PROGRESS.md design questions), `drifter brain`
names the warrior chestplate as its target at that point, and after the sprint the inventory or worn armor has it. With
the item not learned (`gear forget`), the plan shows only vanilla pieces (no x-ray).

- [ ] **Step 5: Commit** (`git commit -m "Known modded weapons and armor as craft targets, picked by taste"`)

---

### Task 7: Powers, part 1: hover-text guesses and learning by use

**Files:**
- Create: `gear/Powers.java` (enum, text guess), `gear/PowerWatch.java`, `data/drifter/drifter_gear/powers.json`, `tools/smoke-gear-powers.txt`
- Modify: `gear/GearLore.java` (beliefs), `gear/GearData.java`, `hands/Combat.java` (`attack`: start a watch), `executor/task/ShootTask.java` (`release(true)`: start a watch), `hands/UniqueAbility.java` (`activate`: start a watch, cooldown read), `perception/ExplosionHearing.java` (report explosions to `PowerWatch`), `entity/DrifterEntity.java` (tick `PowerWatch`), `command/GearCommands.java`

**Interfaces:**
- Consumes: `GearLore` (Task 2).
- Produces:
  - `enum Powers.Power { EXPLOSIVE, FIRE, LIGHTNING, POISON, WITHER, FREEZE, HEAL, MULTISHOT, AREA, PULL, TELEPORT, COOLDOWN }`
  - `enum GearLore.Source { TEXT, SEEN, TOLD, DATA }`; `record GearLore.Belief(float confidence, Source source)`
  - `Map<Powers.Power, GearLore.Belief> GearLore.beliefs(Item item)`; `boolean GearLore.believes(Item item, Powers.Power p)` (confidence >= `ACT_ON = 0.5`)
  - `void GearLore.firm(DrifterEntity d, Item item, Powers.Power p, float by, GearLore.Source s, String how)`; `void GearLore.doubt(Item item, Powers.Power p, float by)`
  - `static Set<Powers.Power> Powers.guess(Level l, ItemStack s)` (cached per item per reload)
  - `static void PowerWatch.start(DrifterEntity d, ItemStack used, @Nullable LivingEntity target)`; `static void PowerWatch.explosion(ServerLevel l, Vec3 at, @Nullable Entity cause)`; `void PowerWatch.tick(DrifterEntity d)`

- [ ] **Step 1: Write `tools/smoke-gear-powers.txt`**

```
(header)
drifter spawn Vet
execute as @e[type=drifter:drifter,name=Vet] at @s run drifter personality set knowledge 0.9
drifter give minecraft:bow[minecraft:lore=['"Explodes on impact"']]
drifter gear item minecraft:bow[minecraft:lore=['"Explodes on impact"']]
execute positioned ~40 ~ ~ run drifter spawn Newb
execute as @e[type=drifter:drifter,name=Newb] at @s run drifter personality reroll newbie
execute at @e[type=drifter:drifter,name=Newb] run drifter give minecraft:bow[minecraft:enchantments={levels:{"minecraft:flame":1}}]
execute at @e[type=drifter:drifter,name=Newb] run drifter give minecraft:arrow 32
execute at @e[type=drifter:drifter,name=Newb] run summon zombie ~12 ~ ~
wait 20
execute at @e[type=drifter:drifter,name=Newb] run drifter gear item minecraft:bow
execute at @e[type=drifter:drifter,name=Newb] run drifter gear item too_many_bows:flame_bow
stop
```
(Item beliefs are kept per item id, so the lore'd bow and the plain bow share one entry: the guess reads the stack in
hand; the test reads the Vet line before Newb's bow is learned. Keep Vet and Newb apart.)

- [ ] **Step 2: Run it to see it fail** (no `powers:` part).

- [ ] **Step 3: Write `powers.json`**

```json
{"words": {"explosive": ["explod", "blast", "detonat", "boom"], "fire": ["fire", "flame", "burn", "ignite", "blaze", "pyre", "solar"],
 "lightning": ["lightning", "thunder", "storm", "shock"], "poison": ["poison", "venom", "toxic"], "wither": ["wither", "decay"],
 "freeze": ["freez", "frost", "ice", "chill", "slow"], "heal": ["heal", "life steal", "lifesteal", "restore", "vitality"],
 "multishot": ["multishot", "spread", "three arrows", "volley", "scatter"], "area": ["area", "nearby enemies", "all enemies", "around you"],
 "pull": ["pull", "grapple", "draw enemies"], "teleport": ["teleport", "blink", "warp"], "cooldown": ["cooldown", "recharge"]},
 "read_text_knowledge": 0.35, "text_confidence_base": 0.3, "text_confidence_per_knowledge": 0.5,
 "seen_step": 0.35, "told_confidence": 0.6, "doubt_step": 0.15, "doubt_after_uses": 3, "watch_ticks": 40}
```

- [ ] **Step 4: Implement the text guess**

`Powers.guess`: lower-cased text of `stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL)`
inside `try { } catch (Throwable t)` (client-only tooltip code: Review Focus 1; on failure log once per item at debug
level), plus `Language.getInstance()` lookups of `<descId>.tooltip`, `.desc`, `.description`, `.tooltip.ability`,
`.tooltip.0`..`.tooltip.3` when present, plus enchantments (flame/fire aspect → FIRE, multishot → MULTISHOT). Word match
against `powers.json`. Cached per item per reload, except stacks carrying `minecraft:lore` or a custom name (read fresh).
In `GearLore.check`: for each gear stack it carries or wears that it hasn't read yet this load, if its `KNOWLEDGE >=
read_text_knowledge`, each guessed power `firm(TEXT, base + per_knowledge * knowledge)` (a newbie doesn't read the text).
`firm` raises confidence by `by` (capped at 1.0) and keeps the strongest source. Items with a hand-written `ability` in a weapon file get `Source.DATA` beliefs from the entry's `when`
(crowd → AREA, hurt → HEAL) and are never re-guessed.

- [ ] **Step 5: Implement `PowerWatch`**

On `start`: remember item, target, tick, target's health, position and fire ticks, its own health, the healths of hostiles
within 6 of the target. Each tick for `watch_ticks`: target `isOnFire` → FIRE; `hasEffect(POISON)` → POISON; `WITHER` →
WITHER; `getTicksFrozen() > 0` or new `MOVEMENT_SLOWDOWN` → FREEZE; a `LightningBolt` within 3 of the target → LIGHTNING;
an explosion reported within 4 of the target or of itself → EXPLOSIVE; 2+ other hostiles lost health → AREA; its own
health up while not eating or regenerating → HEAL; the target moved 2+ blocks toward it → PULL; the target moved 8+ blocks
in one tick → TELEPORT; for a shot, 2+ arrows owned by it spawned in the same tick → MULTISHOT; after `UniqueAbility`
activation, `drifter.helperPlayer.getCooldowns().isOnCooldown(item)` → COOLDOWN. Each sign: `firm(SEEN, seen_step, "saw it")`
with thought `found out <Name>: <power id in lower case>` (once per item and power). If an explosion hurt the Drifter itself: thought
`found out the hard way: <Name> explodes`, confidence straight to 1.0. A TEXT belief not seen after `doubt_after_uses`
uses: `doubt(doubt_step)`.

- [ ] **Step 6: Save beliefs**

In the `DrifterGear` tag, `Beliefs:[{Item, Power, Conf, Source}]` (additive to version 1; unknown items or powers skipped).

- [ ] **Step 7: Debug output** — `/drifter gear item` appends `; powers: <power> <conf> (<source>), ...` or `; powers: none`.

- [ ] **Step 8: Build and run**

Expected: Vet's line has `powers: explosive 0.75 (TEXT)` (0.3 + 0.5 × 0.9); Newb's bow line, after the fight, has
`fire` at 0.35 or more `(SEEN)` and the thought `found out Bow: fire`;
with Too Many Bows present and a knowledgeable Drifter, `flame_bow` shows `fire (TEXT)` on an integrated server; on the
headless server either a guess or `powers: none` and no exception (Review Focus 1). Save and reload: beliefs still there.

- [ ] **Step 9: Commit** (`git commit -m "Powers: hover-text guesses for the knowledgeable, learning by use, saved"`)

---

### Task 8: Powers, part 2: sensible use, and passing it on in talk

**Files:**
- Create: `data/drifter/drifter_talk/topics_gear_mods.json`, `tools/smoke-gear-sense.txt`
- Modify: `gear/Powers.java` (rules), `hands/Combat.java` and `hands/Shields.java` (power penalty in the choice), `executor/task/ShootTask.java` (hold fire), `hands/UniqueAbility.java` (`abilityOf` fallback), `social/talk/Conversations.java:340-362` (facts) and `:926-934` (effects), `command/GearCommands.java`

**Interfaces:**
- Consumes: `GearLore.believes`, `GearLore.beliefs`, `GearLore.firm` (Task 7); `Home.center`; group/battle side checks used by `Archery.friendInTheWay`.
- Produces:
  - `static boolean Powers.mayUse(DrifterEntity d, ItemStack s, @Nullable LivingEntity target)`
  - `static double Powers.fit(DrifterEntity d, ItemStack s, @Nullable LivingEntity target)` (multiplier for choosers, 0.2..1.3)
  - `@Nullable static JsonObject Powers.abilityFor(DrifterEntity d, ItemStack s)` (a generic `when` entry for Simply Swords items no file lists)
  - Talk facts: flag `has_gear_tip` + words `gear_tip_item`, `gear_tip_power`; flag `has_new_gear` + word `new_gear`; effects `teach_power`, `learn_power`, `teach_gear`, `learn_gear`.

- [ ] **Step 1: Write `tools/smoke-gear-sense.txt`**

Vet from Task 7's setup holding the "explodes" bow plus a stone sword and arrows; a friend Drifter Pal put in its group
(`drifter group Pal` run at Vet) standing 2 blocks from a zombie summoned 10 blocks off; `wait 15`; `drifter thoughts`.
Then the zombie at 3 blocks with no friend near: `wait 10`, `drifter thoughts`. Then put Vet and Newb (who knows nothing
of the bow's power) side by side, `drifter talk gear_warn_explosive` at Vet (the existing start-a-topic command), `wait 30`,
then at Newb `drifter gear item minecraft:bow`.

- [ ] **Step 2: Run it to see it fail** (Vet shoots past its friend with the explosive bow; Newb learns nothing).

- [ ] **Step 3: Implement the rules** (constants in `Powers`)

- EXPLOSIVE: not if the target is within `SAFE_BLAST = 5` of it; not if any friend (group, battle side, trusted Drifter, or player it likes) is within `BLAST_RADIUS = 4` of the target; not within `HOME_GUARD = 16` of its home center.
- FIRE: not within `HOME_GUARD` of its home (its home is mostly wood until it isn't: keep simple), not if a friend is within 2 of the target.
- AREA: `fit` +0.3 with 2+ hostiles within 4 of the target, -0.2 against a lone target when it has a single-target choice.
- HEAL: `abilityFor` gives `when: hurt, health 0.5`.
- COOLDOWN: `abilityFor` gives `when: crowd, count 2` unless HEAL (saves it for the right moment).
- `fit` multiplies `bestWeaponSlot`/`bestBowSlot` scores at choice time (an explosive bow loses to a plain one up close or near friends); `ShootTask` calls `mayUse` before releasing and lowers the bow with the thought `held fire: <Name> would blow up <friend|itself|home>`; the fight falls back to melee.
- `UniqueAbility.abilityOf`: an item listed in a weapon file keeps its entry (including "no ability"); an item not listed whose namespace is `simplyswords` uses `Powers.abilityFor`.

- [ ] **Step 4: Talk**

Facts (in the `Conversations` facts block next to `knows_<area>`): `has_gear_tip` when it believes a power at 0.6+ of an
item it owns or has used; `has_new_gear` when it learned a modded item in the last in-game day. Effects: `teach_power`
(a tells b: b `firm(TOLD, told_confidence)` the tip) and `teach_gear` (b learns `new_gear` as TOLD), with `learn_*` the
other way, as `teach_<area>` does. Topics file with at least 12 topics in the `topics_lore.json` shape, e.g. id
`gear_warn_explosive` needs `["a:has_gear_tip", "!rivals"]`, lines using `{gear_tip_item}`; plus questions, brag lines
(flashy: "look at this {new_gear}"), loyal lines ("katana or nothing"), follower lines. Run
`python tools/check_talk_web.py` (the existing topic checker) on it.

- [ ] **Step 5: Build and run**

Expected: first phase thought `held fire: Bow would blow up <friend>` and no hurt friend (`drifter info` health full);
second phase it doesn't shoot at 3 blocks (melee with the stone sword, `the fight is over`); after the talk, Newb's line
shows `explosive 0.60 (TOLD)`. `check_talk_web.py` reports no errors for the new file.

- [ ] **Step 6: Commit** (`git commit -m "Powers used sensibly (not near friends, home or itself); tips passed on in talk"`)

---

### Task 9: Crossbows

**Files:**
- Modify: `executor/task/ShootTask.java` (crossbow mode), `entity/DrifterEntity.java` (`getProjectile` override), `hands/Archery.java` (`ready`, `shouldShoot`, speed constants), `hands/Shields.java` (`bestBowSlot` → launchers), `brain/activity/CombatActivity.java:84-90`, `executor/task/UseItemTask.java:22` (TODO line), `tools/smoke-gear-crossbow.txt`

**Interfaces:**
- Consumes: `Shields.isLauncher`, `Shields.bowScore(d, s)` (Task 4), `Powers.mayUse` (Task 8).
- Produces:
  - `ItemStack DrifterEntity.getProjectile(ItemStack weapon)`: for a `ProjectileWeaponItem`, `Archery.ammo(this)` (the live stack, so loading uses up its arrows); else super.
  - `Archery.ready(d)`: a launcher and ammo, or a crossbow already charged (`CrossbowItem.isCharged`).
  - `Archery.CROSSBOW_ARROW_SPEED = 3.15` (CrossbowItem's arrow power); ShootTask aims with it when the launcher is a crossbow (no draw-power scaling).
  - ShootTask phases for a crossbow: SELECT → LOAD (`startUsingItem(MAIN_HAND)`, hold `CrossbowItem.getChargeDuration(stack, drifter)` ticks, then `releaseUsingItem()`; vanilla's `releaseUsing` loads it; thought `loaded its <name>`) → AIM (as for the bow) → FIRE (`crossbow.performShooting(level, drifter, MAIN_HAND, stack, 3.15F, 1.0F, null)`; then `CrossbowItem` clears the charge). A crossbow carried charged keeps its charge (it fires at once next fight, as a player's would).

- [ ] **Step 1: Write `tools/smoke-gear-crossbow.txt`**

A Drifter given only `minecraft:crossbow`, `minecraft:arrow 16`, a stone sword; `summon skeleton ~15 ~ ~`; `wait 30`;
`drifter thoughts`; `drifter inventory`. A second given `crossbow[enchantments={levels:{"minecraft:multishot":1}}]`.

- [ ] **Step 2: Run it to see it fail** (crossbow never used: it walks in with the sword).

- [ ] **Step 3: Implement** as in Interfaces. `CombatActivity` keeps calling `ShootTask` (it chooses the launcher by
`bestBowSlot`, which now includes crossbows). Remove "crossbows" from the `UseItemTask` TODO.

- [ ] **Step 4: Build and run**

Expected: thoughts show it shot the skeleton (`ShootTask` shot count > 0, `the fight is over`), arrows in inventory fewer
than 16 (loading used them), the multishot one spends one arrow per shot and three arrows land (a `MULTISHOT` belief
appears from Task 7's watch). Summon a second skeleton after the first dies: if the crossbow was left loaded, its first
shot of the second fight has no `loaded its Crossbow` thought before it.

- [ ] **Step 5: Commit** (`git commit -m "Crossbows: load, aim and fire as a player does (modded ones too)"`)

---

### Task 10: Tridents

**Files:**
- Create: `executor/task/ThrowTridentTask.java`, `mixin/TridentItemMixin.java`, `tools/smoke-gear-trident.txt`
- Modify: `src/main/resources/drifter.mixins.json` (add `TridentItemMixin`), `entity/DrifterEntity.java:2166` (`pickUpNearbyItems`: own tridents), `executor/task/CollectItemsTask.java` (walk to its own tridents), `brain/activity/CombatActivity.java`, `hands/Archery.java` (`shouldThrow`), `executor/task/UseItemTask.java:22` (TODO line)

**Interfaces:**
- Consumes: `Weapons.isWeapon` covers tridents (Task 4); `AbstractArrowAccessor.drifter$isInGround()` (existing).
- Produces:
  - `TridentItemMixin`: `@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)` on `TridentItem`; for a `DrifterEntity`: if held `< TridentItem.THROW_THRESHOLD_TIME` (10) ticks or `EnchantmentHelper.getTridentSpinAttackStrength(stack, drifter) > 0` (riptide: never thrown), cancel and do nothing; else, as vanilla does for a player: `stack.hurtAndBreak(1, drifter, slot)`, `new ThrownTrident(level, drifter, stack)`, `shootFromRotation(drifter, xRot, yRot, 0, 2.5F, 1.0F)`, `pickup = Pickup.ALLOWED` (vanilla only allows it for players), add it, play `TRIDENT_THROW`, remove the stack from its hand.
  - `static boolean Archery.shouldThrow(DrifterEntity d, LivingEntity target)`: holds or carries a non-riptide trident, target 5 to 20 blocks off and in sight, no launcher ready (or the target is in water), `Powers.mayUse`.
  - `ThrowTridentTask(DrifterEntity d, LivingEntity target, String label)`: select the trident (`SelectItemTask`), aim (trident speed 2.5, gravity 0.05 as `AbstractArrow`), hold use 10+ ticks, `releaseUsingItem()`; succeeds when thrown (thought `threw its trident at the <label>`); remembers the `ThrownTrident` UUID in the Drifter (`DrifterEntity.thrownTrident()`/`setThrownTrident(UUID)`, not saved).
  - Pickup: in `pickUpNearbyItems`, a `ThrownTrident` whose owner is this Drifter, touching its box, `inGround` or `isNoPhysics()` (loyalty return) → `inventory.add(getPickupItem())`, `take(...)` animation, discard (mirrors `AbstractArrow.playerTouch`/`tryPickup`).
  - Fetch: `CollectItemsTask` also walks to its remembered trident if it can see it, within its radius; gives up after `TRIDENT_FETCH_TICKS = 20*20` or if the entity is gone (fell in lava or the void, picked up by someone else) with the thought `lost its trident`.

- [ ] **Step 1: Write `tools/smoke-gear-trident.txt`**

A Drifter given `minecraft:trident` and nothing else; `summon zombie ~12 ~ ~`; `wait 25`; `drifter thoughts`;
`drifter inventory` (the trident back). A second with `trident[enchantments={levels:{"minecraft:loyalty":3}}]`. A third
with a riptide trident (`minecraft:riptide`): it fights in melee with it, never throws.

- [ ] **Step 2: Run it to see it fail** (no throw; tridents only swung, or not at all).

- [ ] **Step 3: Implement** as in Interfaces; `CombatActivity` asks `Archery.shouldThrow` after `shouldShoot`.

- [ ] **Step 4: Build and run**

Expected: thought of a throw (`threw its trident at the zombie`), zombie dead, then the trident back in the inventory
(first Drifter walks to it: `CollectItemsTask` line; loyalty one gets it back without walking). Riptide Drifter: no throw
thought, melee kill. Kill the first Drifter's trident in lava in a variant run (`fill` lava under the zombie): `lost its
trident`, no endless fetch loop (`drifter brain` moves on within 20 s).

- [ ] **Step 5: Commit** (`git commit -m "Tridents: thrown and fetched back as a player does; riptide ones swung"`)

---

### Task 11: Trinkets (optional, Trinkets mod)

**Files:**
- Create: `compat/TrinketsCompat.java`, `executor/task/WearTrinketsTask.java`, `data/trinkets/entities/drifter.json`, `tools/smoke-gear-trinkets.txt`
- Modify: `gear/GearValue.java` (`trinket`, `isGear`), `brain/Goals.java` (~:3140-3160, the equip goal that scores `WearArmorTask.hasUpgrade`: also `WearTrinketsTask.hasUpgrade`, and runs it after armor), `entity/DrifterEntity.java` (drop trinkets on death with its other things), `command/GearCommands.java`

**Interfaces:**
- Consumes: `GearTaste.liking`, `GearLore` (seen/known), `Powers` beliefs.
- Produces:
  - `static boolean TrinketsCompat.active()` (reflection, self-disabling like `BetterCombatCompat`)
  - `static boolean TrinketsCompat.isTrinket(ItemStack s)` (it fits some Drifter slot: `TrinketsApi.evaluatePredicates`)
  - `static List<TrinketsCompat.Slot> TrinketsCompat.slots(LivingEntity e)`; `record TrinketsCompat.Slot(String group, String name, int index, Container inventory)`
  - `static boolean TrinketsCompat.fits(LivingEntity e, Slot slot, ItemStack s)`; `static double TrinketsCompat.modifierValue(LivingEntity e, Slot slot, ItemStack s)` (Σ positive `ADD_VALUE` amounts of `Trinket.getModifiers(...)`, armor ×10, max health ×4, speed ×100, damage ×3)
  - `static double GearValue.trinket(LivingEntity e, ItemStack s)` (best `modifierValue` over fitting slots, +5 per believed power, +5 per rarity step)
  - `static boolean WearTrinketsTask.hasUpgrade(DrifterEntity d)`; `static void WearTrinketsTask.equipAllNow(DrifterEntity d)` (catch-up, like `WearArmorTask.equipAllNow`)

- [ ] **Step 1: Write `data/trinkets/entities/drifter.json`**

```json
{"entities": ["drifter:drifter"],
 "slots": ["head/hat", "head/face", "chest/necklace", "chest/back", "chest/cape", "legs/belt",
           "hand/glove", "hand/ring", "offhand/glove", "offhand/ring", "feet/shoes", "feet/aglet"]}
```
All twelve are defined by Trinkets itself (`data/trinkets/slots` in trinkets-3.10.0.jar), so none is unknown. Without
Trinkets the file is inert data.

- [ ] **Step 2: Write `tools/smoke-gear-trinkets.txt`**

A Drifter given `artifacts:cross_necklace`, `artifacts:running_shoes`, `too_many_bows:sharpshot_ring`; `drifter gear
item` each; `drifter gear trinkets` (new: lists its slots and what is in them); `wait 10`; `drifter gear trinkets`;
`attribute @e[type=drifter:drifter,limit=1,sort=nearest] minecraft:generic.movement_speed get`.

- [ ] **Step 3: Run it to see it fail** (no trinkets worn; `gear trinkets` unknown).

- [ ] **Step 4: Implement `TrinketsCompat`** (reflection, not compile-only: Trinkets' types extend Cardinal Components
interfaces, which a `modCompileOnly` would also need on the classpath; reflection keeps the build free of both)

Look up in `active()`: `dev.emi.trinkets.api.TrinketsApi#getTrinketComponent(LivingEntity)` (returns `Optional`),
`TrinketComponent#getInventory()` (`Map<String, Map<String, TrinketInventory>>`), `TrinketInventory` cast to
`net.minecraft.world.Container` (it implements it), `SlotReference(TrinketInventory, int)` constructor,
`TrinketsApi#evaluatePredicates(ItemStack, SlotReference, LivingEntity)`, `TrinketsApi#getTrinket(Item)`,
`Trinket#canEquip(ItemStack, SlotReference, LivingEntity)`, `Trinket#getModifiers(ItemStack, SlotReference, LivingEntity, ResourceLocation)`.
Any `ReflectiveOperationException | RuntimeException | LinkageError` → one warning `[Drifter] Trinkets' API didn't
match (...); Drifters don't wear trinkets` and `active()` false thereafter. Verify each name against the installed
trinkets-3.10.0.jar (`javap -cp <jar> dev.emi.trinkets.api.TrinketsApi`) before writing the lookups.

- [ ] **Step 5: Implement `WearTrinketsTask`**

As `WearArmorTask`: `InventoryPause.roll(MOVE_ITEM_MIN, MOVE_ITEM_MAX)` first, one slot per `CLICK_TICKS`; for each slot,
the carried trinket with the best `GearTaste.liking(d, s, GearValue.trinket(d, s))` that `fits`, if better than what's in
it (good-enough applies); swap through the slot's `Container`. The equip goal in `Goals.java` runs it after
`WearArmorTask` when `WearTrinketsTask.hasUpgrade`. Death: its trinkets drop with its other things (it takes every kind of loss a player does).

- [ ] **Step 6: Build and run**

Expected: `gear item` lines for the three show a trinket value; after the wait, `gear trinkets` shows the necklace in
`chest/necklace`, the shoes in `feet/shoes`, the ring in `hand/ring`; the movement speed `get` is above the base 0.1
if running shoes give a modifier on mobs (spec: effects a mod runs only for players may not work: if not, note it in
TESTING.md, not a failure). With Trinkets moved out: the same script runs, `gear trinkets` says `Trinkets isn't
installed`, no exception.

- [ ] **Step 7: Commit** (`git commit -m "Trinkets (optional): worn by value, rarity and taste"`)

---

### Task 12: The API and Drifter: Civilizations

**Files:**
- Modify: `api/DrifterApi.java` (methods, `API_VERSION = 4`), `docs/API.md` (new section "Gear"), civ `Trades.java:487-525` (`wares`, `ownUse`, shop sells), `Market.java:317-322` (`sellable`), `TownDay.java:46-51` (`veteran`), `civ/resources/data/drifter_civ/drifter_lines/smith.json` (`_comment` only), `tools/smoke-gear-civ.txt`

**Interfaces:**
- Consumes: everything above.
- Produces (all `public static` on `DrifterApi`):
  - `boolean isGear(ItemStack s)` → `GearValue.isGear`
  - `double gearValue(DrifterEntity d, ItemStack s)` → the use-matching value (melee, ranged, armor for its slot, trinket)
  - `double armorPoints(ItemStack s)` → `ARMOR` modifier sum for its slot (vanilla iron chestplate 6)
  - `boolean knowsItem(DrifterEntity d, Item item)`; `void learnItem(DrifterEntity d, Item item, String where)` (SEEN)
  - `String gearStyle(DrifterEntity d)` (lower-case style id); `double gearLiking(DrifterEntity d, ItemStack s)`
  - `List<Item> gearItCanMake(DrifterEntity d, int maxTier)` (known, craftable, `isGear`, tier equivalent <= `maxTier`: `Weapons.tier` for weapons, the armor tier equivalent from `GearValue.points` for armor, iron (3) for launchers and trinkets; 1 wood .. 5 netherite; vanilla included)
  - `DrifterApi.worth` unchanged in signature: now prices modded gear through `ItemValues` (Task 1).

- [ ] **Step 1: Write `tools/smoke-gear-civ.txt`**

`drifterciv town spawn 12 Testvale` at a fixed spot (memory: harness gotchas), `wait 60` for the land, then
`execute positioned <town> run drifterciv prices` (it names each shop's keeper); for the smith,
`execute as @e[type=drifter:drifter,name=<smith>] at @s run drifter gear learn <X>` where X is a modded weapon or armor
piece whose `drifter gear item` line shows a `key` (crafting-table recipe) and an iron-or-lower tier equivalent (pick it
once with `gear item` over the Immersive Armors and Simply Swords items; write the chosen id into the script), and
`... run drifter give <X's key ingredient> 16`; `sprint 24000`; `drifterciv prices` again.

- [ ] **Step 2: Run it to see it fail** (prices list only the json's vanilla items).

- [ ] **Step 3: Implement the API methods and `docs/API.md` "Gear" section** (a table like the others: call, what it does;
plus one line: "worth() prices modded gear by its value").

- [ ] **Step 4: Addon changes**

- `Trades.wares`: for a line with a shop, add `DrifterApi.gearItCanMake(drifter, 3)` items (iron or lower: B6 keeps diamond to "sells" only) not already in `makes`, with `keep 1, batch 1`, only for the `smith` line (and `fletcher` for `Use.BOW`/`CROSSBOW` items: filter with `isGear` plus the item being a launcher via a small `DrifterApi.isLauncher(ItemStack)`; add that method too if used).
- The shop's sells list includes those items when the keeper carries them for sale.
- `Trades.ownUse`: `DrifterApi.isGear(new ItemStack(item)) || item instanceof ShearsItem || FishingRodItem || TieredItem`.
- `Market.sellable`: `!DrifterApi.isGear(stack) && !(item instanceof TieredItem) && ...` (modded weapons that aren't `TieredItem` aren't sold as goods by the stall).
- `TownDay.veteran`: `DrifterApi.armorPoints(chest) >= 6` instead of `ArmorItem#getDefense`.
- `smith.json` `_comment`: add "plus any modded weapon or armor it knows how to make (Drifter's gear knowledge)".

- [ ] **Step 5: Build and run**

Expected: `./gradlew build` builds both jars; second `prices` lists X at the smith's stand, priced by
`GearValue.points` (its `points` from `gear item` times the market factor); no `ArmorItem` import left unused in
`TownDay`. `grep -rn "com.dinglebob.drifter\.\(gear\|brain\.GearPlans\)" src/civ` finds nothing (API only).

- [ ] **Step 6: Commit** (`git commit -m "API: gear value, knowledge and taste; Civilizations smiths make and price modded gear"`)

---

### Task 13: Docs, full run with and without the mods, the jar

**Files:**
- Modify: `docs/PROGRESS.md`, `docs/TESTING.md`, `gradle.properties` (bump N), `C:\Users\mutmu\.claude\projects\C--Users-mutmu-Documents-aishit-drifters\memory\config-mod-menu.md` only if the screen rule text needs the new setting named

- [ ] **Step 1: PROGRESS.md**

A "Compatibility project 1: weapons, armor, trinkets" entry: done per task; design questions found on the way (smithing-only
modded gear not crafted; effects mods run only for players; dedicated-server tooltip gaps; the calls below in "Spec
calls made").

- [ ] **Step 2: TESTING.md**

Plain-language steps for the user's DRIFTER TEST profile (all five mods installed): the `/drifter gear`, `gear item`,
`gear plans`, `gear trinkets` commands; what to look for (a mix of gear across a town; the warrior armor worn by some, not
all; a Drifter holding fire with an explosive Too Many Bows bow near friends; crossbow and trident fights; trinkets on
Drifters); the "Know every recipe" switch in Mod Menu (off: a newbie doesn't make a katana it has never seen; on: it can).

- [ ] **Step 3: Full runs**

Run every `tools/smoke-gear-*.txt` with the mods in `run/mods`, then again with Trinkets, Artifacts, Too Many Bows,
Immersive Armors, Simply Swords and Better Combat moved to `run/mods_off/`; also `DRIFTER_FD=1` once (Farmer's Delight
families still load). Expected: the vanilla parts pass both ways; no `Exception` or `NoClassDefFoundError` naming
`drifter`, `trinkets`, `bettercombat` or `simplyswords` in any log.

- [ ] **Step 4: Bump the version and install**

`version=0.23.32-side.<next free>`; `./gradlew build`; check `tasklist /FI "IMAGENAME eq javaw.exe"` (if the game is open,
ask the user to close it); in `...\DRIFTER TEST\mods` remove `drifter-*.jar` and `drifter_civilizations-*.jar`, copy both new jars.

- [ ] **Step 5: Commit and push**

```bash
git add docs gradle.properties
git commit -m "0.23.32-side.N: any weapon, armor and trinket mod (value, knowledge, taste, powers, crossbows, tridents, trinkets, civ)"
git push origin HEAD
```

---

## Spec calls made in this plan (for the user to check)

- "Unlocked" = it has held the recipe's dearest ingredient (vanilla's recipe-book unlock is per-recipe advancement criteria; most are the key material, which this mirrors).
- Veterans (Knowledge 0.7+, PvP sweats, speedrunners) know every installed mod's *craftable* gear from the start, never loot-only items; newbies only vanilla and the written families.
- Weapon-file uniques are not known until seen ("other families' special items" read as the uniques).
- "Know every recipe" covers items and recipes only, not `Lore` areas (the Nether, enchanting): those keep their own rules.
- Riptide tridents are never thrown (a riptide throw launches the thrower; no water/rain logic in this project).
- Smithing-table-only modded gear (netherite upgrades) is valued and worn but not crafted (the Planner doesn't smith modded items yet).
