# Modded armor in starting kits: uniforms for authority, a realistic mix for the rest (design)

Follow-up to compatibility project 1 (weapons, armor, trinkets; spec `2026-11-09-weapons-armor-compat-design.md`),
agreed by the user on 2026-11-10 before compatibility project 2 (Farmer's Delight addons).

## Intent

Today every starting kit is vanilla armor: a Civilizations town's guards wear iron, its leader a gold crown and diamond,
its citizens humble leather/chain/iron (`Delvers.townKit`, reached by the addon through `DrifterApi.equipTown`);
delvers wear iron-to-diamond (`Delvers.kit`); armies and encounter bands wear mixed pieces in their colour
(`SoldierKit`). With an armor mod installed (the user's reference: Immersive Armors), none of these ever show modded
armor, so a modpack's armor only appears later through smiths, loot and trades.

The user (2026-11-10): for "guards and people in authority" it should be every guard in matching modded plate, or a
mix of modded and iron, "in uniform so people know whos in charge"; "for bandits, ambushers, delvers, it should be mixed
(sometimes matching) but realistically mixed". Different towns should have different uniforms, "but honestly we can
utilize that using armor trims"; towns of one nation "share one trim as the nation".

## Goals

1. **Uniforms for authority.** A town's guards all wear one uniform: the same armor set on every guard (a matching
   modded set when an armor mod offers one at the guards' tier, otherwise matching vanilla iron as today), the same
   pieces on every guard (no per-guard mixing of sets).
2. **Colours by trim.** The uniform carries the town's trim (one trim pattern + one trim material) on every piece. A
   town in a nation wears the nation's trim instead, shared by all its towns. Two towns in the same armor set still look
   different by their trims.
3. **Nation trim from the nation's colour.** A nation already has a colour (`Nations.Nation.colour`, used for its
   banners). Its trim material follows that colour with the same colour→material mapping `SoldierKit.trimFor` uses
   (red→redstone, blue→lapis, green→emerald, yellow→gold, ...); its pattern is picked once and kept with the nation.
   An independent town picks its own pattern and material once, kept with the town.
4. **The leader stands out.** The leader wears the town's (or nation's) trim too, on a showier or rarer piece than the
   guards' (today's gold crown and diamond/iron chest stay the idea: rank shows).
5. **A realistic mix for the rest.** Bandits, ambushers and delvers wear mixed pieces: a modded chestplate with iron
   boots, a leather cap with modded leggings. Now and then (not often) one wears a full matching set, as if looted
   whole. Each Drifter's own gear taste (`GearTaste`: practical / flashy / loyal / follower) leans its picks (the flashy
   one takes the shiny piece, the practical one the sturdiest, the loyal one a matching set more often). No town trim;
   now and then a random trim on a single piece, like loot from elsewhere.
6. **Citizens stay humble.** As today, with a modded piece now and then when it fits their means and tier.
7. **Tiers stay where they are.** Modded pieces are picked to match the tier each role already has (guards around iron,
   delvers iron to diamond, citizens leather to iron, a band's standard as `SoldierKit.Quality`), judged by
   `GearValue` against the vanilla piece of that slot, so no role becomes stronger than today (the user, 2026-10-28,
   found made-ready towns too strong in diamond).
8. **Without armor mods, nothing changes** (beyond the uniform trims in goal 2, which use vanilla armor).
9. **Joining or leaving a nation switches the trim** (the user, 2026-11-10, taking the recommendation): nations form
   after their towns exist (two allied towns join, or one conquers another), so when a town joins a nation its guards'
   and leader's worn uniform pieces switch once to the nation's trim, at the moment it joins (as its leader then makes
   and stands the nation's banner). A town that leaves (a rebel going free, independence) switches back to its own
   trim the same way. Only the pieces they are wearing that carry the old trim change; nothing is added.

## Non-goals

- Re-trimming armor at other times (a replacement guard, a uniform piece lost and replaced): afterwards gear comes the
  normal way (crafting, buying, loot). Out of scope for this step.
- Changing who is a guard or a leader, or how many there are.
- Weapons (already from each Drifter's favourite family, modded ones included) and trinkets.

## Anti-goals (a failure even if it works)

- Guards of one town in different sets, or with different trims, so you can't tell they belong together.
- Every bandit or delver in the same "best" modded set (looks generated, not lived-in).
- A role stronger than today because a modded piece out-tiers its vanilla one.
- A required dependency: armor mods stay optional, read from whatever armor items are registered.

## Constraints

- DESIGN.md section 1: a starting kit is the only gear given (what it drops is what it had); no free items afterwards.
- The addon reaches Drifter only through `com.dinglebob.drifter.api`, grown in the same change and documented in
  docs/API.md. The user works on the addon in other sessions: keep addon edits small (where a town or nation chooses
  and keeps its trim, and passing it to `equipTown`).
- Trims only apply to items in the `minecraft:trimmable_armor` tag. All vanilla armor is; Immersive Armors marks all of
  its armor trimmable (`data/minecraft/tags/item/trimmable_armor.json` → `#immersive_armors:armor`). A modded set that
  isn't trimmable can't carry a town's colours.
- Saved data versioned (a town's or nation's chosen trim and uniform set); old saves load (towns and nations made
  before this get a trim chosen on first need).
- Tunable numbers (how often a band member wears a full set, how often a citizen gets a modded piece, the random-trim
  chance) as named constants or data.

## Calls made in writing this up (check them)

- **The uniform set is chosen per town** (once, kept with the town): its guards' tier, among the matching armor sets
  installed that are trimmable, picked with some variety between towns (not always the single best set). A town's
  colours matter more than its set, so a set that isn't trimmable is only a uniform choice if no trimmable one fits;
  then the town falls back to trimmed iron.
- **A town in a nation still keeps its own uniform set** but wears the nation's trim (the user asked that towns of a
  nation share the trim; the armor set can differ town to town).
- **Armies and encounter bands (`SoldierKit`)** keep their per-fighter variety (the user asked for it on 2026-10-20) and
  their army colour trims; they gain modded pieces in that mix at the band's standard. Captains keep the banner.
- **"Matching set"** = the head, chest, legs and feet items an armor mod makes as one set (same material or same id
  prefix, e.g. `immersive_armors:warrior_*`), found from the registered items; a set missing a slot is filled with the
  vanilla piece of the same tier.
- **Who keeps the uniform:** Drifter's API picks it (`DrifterApi` gains a call that chooses a town's uniform: armor set
  id + trim pattern id + trim material id, as plain strings) and dresses a Drifter from it; the addon only stores those
  strings with the town (and the trim with the nation) and passes them back. `equipTown` gains an overload taking the
  uniform; the old one stays (today's behaviour) for other callers such as `Delvers`' citizen kits.
- **Old towns and nations** (made before this): nothing changes for guards already geared; a trim and set are chosen the
  first time one is needed (a nation formed or joined, or the debug command), and goal 9's switch then applies only to
  pieces that carry a trim.
- **Guards' occasional diamond piece is dropped** (today 8%): a uniform is the same on every guard. Guards stay at iron
  strength.
- **"At the guards' tier":** a modded set qualifies when its four pieces' total protection value (`GearValue`) is within
  about 15% of the full vanilla set of that tier (iron for guards), and no single piece is above the next vanilla tier's
  piece; delvers and bands judge each piece against their own tier the same way. Named constants.
- **The leader:** wears the town's uniform set with today's showy pieces kept (gold crown helmet; a chestplate one tier
  up, as today's diamond), every trimmable piece trimmed. No stronger than today's leader.
- **No trimmable set at the tier:** the uniform is trimmed iron. A non-trimmable set is never a uniform. Without armor
  mods, guards now always wear their town's trim on iron (a small visible change, the point of goal 2); if a modpack
  made iron untrimmable, untrimmed iron.
- **Debug:** an addon command showing a town's uniform and trim (`/drifterciv town uniform`), and a Drifter
  `/drifter gear` line for what a kit would pick.

## Left to the builder

- How sets are discovered (id prefix, armor material, tags) and cached per reload.
- The exact place a town and a nation keep their trim and uniform choice, and its save format.
- How taste leans a band member's picks (weights), within goal 7's tier limits.
- The smoke scripts: a town with and without Immersive Armors, two towns with different trims, a nation formed from
  two towns (trim switch) and a rebel going free (switch back), a delver, and an ambush band.
