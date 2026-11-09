# Any weapon, armor and trinket mod, seamlessly (design)

First of five compatibility projects (agreed order: 1 weapons, armor and trinkets; 2 Farmer's Delight addons; 3 structure
mods; 4 block and furniture mods in houses; 5 player and server mods). Code paths below are as on the main line
(`origin/claude/gifted-archimedes-n6yen5`, 0.23.32); build there, not on an old side branch.

## Intent

The user (2026-11-09): when a weapon mod is installed, a Drifter should know "how to craft/acquire it, knowing the
priority and value of the weapon, and using it instead of a vanilla item", the same for armor mods ("priority, value,
coolness, rarity, etc"), and "it can be personalization based aswell ... i dont want every drifter to pick the same weapon
and armor just because its the best option because then it doesnt look organic". "Seamlessly" means a mod Drifter has never
heard of just works, with no file written for it; hand-tuning per mod stays possible on top. Modded powers (Too Many Bows'
bows, Simply Swords uniques) should be understood the way a player comes to understand them.

Reference mods installed in the user's DRIFTER TEST profile: Too Many Bows (bows with powers, and trinket items),
Trinkets with Artifacts (the trinket items to wear), Immersive Armors (modded armor), Simply Swords (uniques with powers),
and Simply Bows and Better Combat (already supported).

## Goals

1. **Value from the item itself.** Weapons by attack damage and speed (already: `hands/Combat.weaponStats`), ranged
   weapons by what they are and what they do, armor from the item's attribute modifiers (armor, toughness, knockback
   resistance, and any other modifier it carries), not only `ArmorItem.getDefense`. Rarity and "coolness" from what the
   game marks: the item's rarity component (name colour), enchantments, and how dear it is to make (rarest ingredient).
2. **Knowing about it like a player.** It only knows a modded item exists once it has seen one (in a chest it opened, on
   the ground, held or worn by another Drifter or a player, offered in a trade) or holds an ingredient that "unlocks" its
   recipe (the vanilla recipe book rule). Veterans may know more from the start (as `personality/Lore` does for parts of
   the game); newbies least. Learned knowledge is saved and versioned.
3. **Getting it.** A known item it could make becomes a craft target in the Strategist (as `Weapons.target` does for
   families today) when it's worth it to that Drifter; otherwise it's taken from loot, picked up, or traded for. No free
   items.
4. **Taste.** Each Drifter has a gear style from its personality, e.g. practical (best numbers), flashy (rare, enchanted,
   colourful, unusual), loyal (sticks to one kind: always a spear, always a matching set), follower (likes what friends and
   its group use). A "good enough" item it likes beats a slightly better one it doesn't, so a server ends up mixed.
5. **Kinds for unknown weapons.** A modded weapon no file names is sorted into a kind automatically (katana, dagger,
   greatsword, spear, hammer, axe, scythe, bow, crossbow, staff...) from its id/name words, its tags (`c:` tool tags), and
   Better Combat's weapon type when present, so it can be a Drifter's favourite like a written family can. The existing
   `drifter_weapons/*.json` families stay and win when present.
6. **Powers.** Three layers:
   - read the item's hover text for words that hint at a power (explode, fire, lightning, poison, freeze, heal, multishot,
     pull, teleport...) as a first guess;
   - learn by using it: watch what happens (an explosion, the target set on fire, frozen, pulled, several mobs hit, its own
     health going up, an item cooldown) and remember it per item;
   - use it sensibly from what it believes: explosive not up close, not near friends or its home; area for crowds; fire
     not in a wooden base; healing when hurt; cooldowns saved for the right moment.
   A newbie may learn the hard way (hurts itself once); a veteran reads the text and gets it right first time. What it
   learned can be passed on in talk ("careful, that bow explodes"). Hand-written rules (today's Simply Swords uniques in
   `hands/UniqueAbility` and `simplyswords.json`) override the guess.
7. **Armor choice with taste**, the same styles: matching sets for the loyal, the flashiest piece for the flashy, best
   protection for the practical; modded armor as Strategist targets when known and worth it (today only vanilla iron and
   diamond are planned).
8. **Trinkets** (the Trinkets mod, optional; tested with Artifacts and Too Many Bows' trinkets): it wears items in trinket
   slots, chosen by value, rarity and taste the same way, valued by the attribute modifiers they give (and its power
   beliefs, goal 6, for ones that do something).
9. **New ways of fighting: crossbows and tridents** (the user, 2026-11-09, taking the suggestion): a Drifter loads and
   fires a crossbow (any `CrossbowItem`, modded ones too) and throws a trident and fetches it back, as a player does, so
   modded crossbows and tridents are real choices. Thrown weapons and staffs/wands are recognised, valued and sorted into
   kinds, but never made a favourite or carried in place of a weapon it can use until a later project teaches them.
10. **Drifter: Civilizations fully compatible** (the user: "civs should be fully compatible with this aswell"): the
   addon's smiths make and sell modded gear they know (not only `drifter_lines/smith.json`'s iron sword and chestplate),
   markets and trades price it by its value, and townsfolk dress and arm themselves by the same taste (town gear checks
   such as `TownDay`'s "armor defense 6 or more" use the new value). The addon reaches this only through
   `com.dinglebob.drifter.api` (item value, whether a Drifter knows an item, its taste and style), documented in
   docs/API.md, in the same change (CLAUDE.md). The user works on the addon in other sessions: keep addon edits focused
   on this, and merge with care.

## Non-goals

- Whole new systems such as Iron's Spells 'n Spellbooks (mana, spell casting): later, separately.
- Players' and servers' mods (YDM's Weapon Master showing weapons on the body, Chat Heads): project 5.
- Block, structure and food mods: projects 2 to 4.

## Anti-goals (a failure even if it works)

- Every Drifter in the same "best" gear.
- Knowing about or crafting an item it has never seen nor unlocked (x-ray knowledge; breaks DESIGN.md section 1).
- Spawning or conjuring items; gear changes faster than a player could (inventory open time applies).
- A required dependency: every mod stays optional, detected at runtime (CLAUDE.md soft dependencies).
- Powers used recklessly in ways that hurt its friends or home once it knows better.

## Constraints

- DESIGN.md sections 1 and 2 (fairness; the fake player only at the moment of interaction).
- Mojang mappings, Fabric 1.21.1; optional mods via reflection or compile-only APIs, guarded so the game runs without them.
- Tunable numbers in data files or named constants; saved data versioned.
- The user tests in-game and doesn't code: plain-language TESTING.md steps; local smoke tests with `tools/smoke.py`.

## What it builds on (today)

- `hands/Combat.weaponStats` / `bestWeaponSlot` (attribute-based melee value, +10% favourite family),
  `brain/Weapons` (tag `drifter:melee_weapons`, `tier` by DPS vs vanilla swords, families from `drifter_weapons/*.json`,
  `favorite`, `liking`, `target`), `compat/BetterCombatCompat` (reach, two-handed, combo by reflection),
  `hands/Shields.isBow` (`instanceof BowItem`), `hands/UniqueAbility`, `executor/task/WearArmorTask` (armor score =
  defense*10 + toughness, ArmorItem only), `Strategist` armor targets (vanilla, :102-147), `trade/Trading.armorScore`,
  `personality/Lore` (a saved, versioned "what it knows" store to mirror), the talk system (`teach_`/`learn_` effects).

## Calls made in writing this up (not discussed; check them)

- **What it knows from the start:** vanilla weapons and armor are always known. The families written in
  `drifter_weapons/*.json` keep today's behaviour (each Drifter has a favourite family from day one and may craft it): a
  player knows the popular mods' gear. The "seen or unlocked" rule covers everything else (modded items no file names,
  other families' special items). Drifters already alive when the update loads count what they carry, wear and store, and
  their favourite family, as known.
- **Where it goes:** a side branch with `-side.N` versions alongside the main line's lived-world work, merged into the main
  line as usual; not a new milestone.
- **Trinkets:** wearing them and getting their attribute modifiers is enough; effects a mod only runs for real players
  may not work on a Drifter. They don't show on its body in this project.

## Left to the builder

- How styles map from traits and archetypes, and how strong taste is against numbers (data-driven, tunable).
- The kind word list and how Better Combat types map to kinds.
- The hover-text word list and how power beliefs are stored and decay or firm up.
- How the Trinkets API is reached (reflection vs compile-only) and which slots it fills.
- Splitting this into steps and their order; tests and debug commands (e.g. a command listing what it knows of an item).
- How crossbow loading and trident throwing and pickup are driven (mirroring the player's use/release timing and reach).
