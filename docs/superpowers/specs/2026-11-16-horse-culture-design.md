# Horse culture (with Icy's Better Horses): design

Status: design, waiting for the user's approval (builder's check done; the user's answers on theft and old worlds in).

## Intent

The user, 2026-11-16: "how can we priortize horse transportation in drifter civs", "kinda like red dead redemption style
but not making it wild western", "or like skyrim style", then picked a blend: "less people riding horses in town, but
definitely between towns in roads, and to wars", "carriages are not easy in vanilla though, which is why this feature
will be centered around Icy's better horses, and the whole horse culture system will be enhanced using icy's better
horses", "personal ownership, with icy's bonding". Order: horses first, then tusklins.

So: horses become how Drifters travel between towns and to war, each traveller with a horse of its own that it bonds
with (Icy's bonding), kept at its town's stable; the roads between towns busy with riders, merchants' carts and
couriers; carriages for the player; on foot inside towns. Medieval, not western (knights, couriers, merchants, farmers;
no cowboys, saloons or six-shooters).

## How it is today

- Drifter: a Drifter can own a horse (`DrifterBrain.horse()`, kept with its home, stall: `DrifterApi.keepHorse`). It breaks
  in a wild horse by mounting until it's tamed, saddles it from a saddle it has (`skills.HorseGoal`), and rides it on a
  walk of 80 blocks or more if the horse is saddled and within 16 blocks (`NavigateTask.rideFor`, `RideTask`, off 16
  blocks short). Town Drifters with a stable horse walk to it, untie it, ride off on long trips, and ride back and tie it
  up of an evening (docs/TESTING.md, "Stables").
- Addon: a town (not a hamlet) has a stable at its edge (`TownBuilder.stable`): eleven by six, three stalls; a town gets
  one or two vanilla horses, a city three, tamed, saddled, tied, handed to households as they settle (`keepHorse`).
  Encounters include a `convoy` of riders on the road.
- Icy's Better Horses 2.1.8 (in the user's pack, `icys-better-horses-2.1.8-fab-1.21.1.jar`; license BML-1.0; needs
  GeckoLib and Modonomicon): 15 breeds with biomes (Arabian for deserts, Icelandic for cold, Shire/Clydesdale/Belgian/
  Percheron draft, Thoroughbred, ...), genders and breeding, ownership by UUID (`IHorseData.bh_getOwner/bh_setOwner`, not
  tied to players), a bond number (`bh_getBond/bh_setBond`, `feature.Bonding`: grows with distance ridden, jumps, mounted
  kills, saves, treats; lost when the owner hits it), a home and commands (`bh_setHome`, `HorseCommand` FOLLOW, STAY,
  RETURN_HOME, WANDER), an upgraded saddle with gear slots (horseshoes, medkit), and horse carts (`HorseCartEntity`,
  two wheels make one; pulled by a horse; carries riders, a chest or a plough; other mobs can ride in one,
  `MobCartPassengerMixin`; it already lets MCA villagers ride, `McaRideHorseMixin`). Small public API (`api/HoofStepEvent`,
  `api/HorseFeaturesEvent`); the rest through its classes.

## Goals

1. **Personal horses.** Townsfolk who travel own a horse: traders, envoys, leaders, guards and soldiers, anyone who can
   afford one; others save up. A horse is bought at a stable (coins, from the economy, `Prices`) or tamed wild (as now).
   With Icy's: an Icy breed suited to its land, owned by the Drifter in Icy's own terms (`bh_setOwner`), named, its home
   its stall (`bh_setHome`).
2. **Bond.** Riding together, treats (Icy's treats: apples, carrots and the like, from its own stores), and coming
   through fights grow the bond, as Icy counts it. A well-bonded horse is calmer and better for its rider (whatever Icy
   gives a high bond). A horse lost (killed, stolen) is grieved: its rider sad for days, talked of in town, in the
   chronicle. If Icy's bonding only counts players riding, the addon credits a Drifter's riding the same way
   (distance ridden, treats) so the bond still grows.
3. **Horses for long trips, feet for town.** A trip to another town, a war, a long errand (about 150 blocks or more):
   it walks to its stall (up to about 64 blocks: the stable's at the edge), saddles up, and rides the roads. It leaves
   the horse at the stable of the town it arrives at (a free stall, tied), walks in, and rides it home later. Inside its
   own town, on foot.
4. **The stable.** Sized to the town (about a stall for every three households; a city's a yard of a dozen or more),
   stocked with breeds that fit the land (desert towns Arabians, snowy towns Icelandic ponies, every stable a draft
   horse or two). A stablehand (a line of work) feeds them, keeps them watered, breeds them (Icy's genders) to replace
   those lost, tames wild ones it finds, and sells horses: foals to townsfolk, horses to the player (a stable shop, through
   the existing shops).
5. **Roads alive.** Merchants on trade runs drive an Icy cart with a chest of goods behind a draft horse (their wares
   really in the chest). Envoys, couriers carrying letters (`Words`/letters to you), travellers and war parties ride.
6. **Carriages for the player.** At each town's stable, a coachman (the stablehand, or one of the town's people) drives
   an Icy cart: the player pays a fare by distance, sits in the cart, picks a town they know, and the coachman drives the
   real roads there, stops at that town's stable and lets them off. The coachman then drives home.
7. **War on horseback.** Soldiers and guards going to war ride; their horses wait at the muster or the front (tied), and
   can be killed or taken.
8. **Without Icy's.** Vanilla horses with the same ownership, stalls, riding, stable sizes and stablehand (taming,
   breeding with wheat, golden carrots); no carts, no carriages, no breeds. Everything Icy-specific is optional: Drifter
   and the addon run without it.

## Non-goals

- Western flavour of any kind.
- Riding inside towns as a habit (a rider passing through is fine).
- Horses in the Nether or the End.
- Mounted combat skills beyond what Drifters already do while riding (a later milestone if wanted).
- Other horse mods.

## Anti-goals (a failure even if it works)

- Breaking the fairness rules: no horse, saddle, cart or treat from nowhere; a Drifter rides by its keys like a player;
  a stable horse it buys costs real coins from what it has; a coachman can't teleport.
- Drifter or the addon needing Icy's, or crashing when it's missing, older or newer (detected at runtime; Icy's never
  shipped).
- Towns' streets crowded with horses, or stables that empty out and never refill.
- Slowing the server: a few hundred loaded Drifters (memory: real play scale), riding included.

## Constraints

- Fairness rules and architecture (DESIGN.md sections 1 and 2): horses are ridden through the motor's virtual keys
  (`RideTask` as now), interactions through the FakePlayer at the moment of interacting (mounting, saddling, feeding,
  tying, opening a cart's chest).
- Icy's integration only when it's installed: Drifter and the addon compile against it as a local compile-only
  dependency (not in the repo, never shipped), every use behind a check that it's loaded and its classes are what's
  expected, skipped cleanly otherwise.
- Drifter vs addon: the general riding, owning, stalls and bond live in Drifter (with an API for the addon); the town
  parts (stables' size and stock, the stablehand, trade-run carts, carriages, couriers, war) in the addon.
- Saved data versioned; tunable numbers as named constants (stall per households, ride-from distance, fares, prices).

## Approaches considered

- **Vanilla only:** owning, stalls, riding, bigger stables, a stablehand; no carts, no carriages, no breeds, no bond.
  Cheap and safe, but the user ruled carriages out without Icy's and wants Icy's bonding.
- **Built around Icy's Better Horses (picked by the user, 2026-11-16):** Icy's owners, bonds, breeds, homes and carts
  through its classes when it's installed, the vanilla version beneath when it isn't.
- **Writing our own carts:** a cart entity of our own for carriages and merchants. Heavy, and duplicates what Icy's
  already does well. Ruled out.

## The design

### 1. Owning (Drifter)

- Who wants a horse: a townsperson whose life takes it out of town (its line: trader, envoy, guard, leader; or it's
  well-off; or it travels often). Others don't (a farmer at home).
- Getting one: buy at a stable it knows (walk there, pay coins, the stablehand hands the horse over: the horse's owner
  becomes it), or break in a wild one (as now). Saddles bought too (or from loot).
- The horse's stall: at its own town's stable (`keepHorse`), its home there (Icy's home when installed).
- Kept in Drifter's save as now; with Icy's, the owner and home set in Icy's own data so its own systems (roster, recall,
  bonding) see it.

### 2. Bond (Drifter, Icy's)

- Icy's bonding grows as it would for a player: distance ridden, jumps, treats, fights. The spike (section 9) checks
  whether a Drifter riding counts; if not, the addon adds the same amounts itself through `bh_setBond` (riding a
  distance, a treat fed), capped as Icy caps them.
- Treats: Icy's gives bond for the horse's own favourite treat, a brush, a golden apple or a name tag (plain carrots and
  apples give none). A Drifter learns its horse's favourite (as a player does, by trying) and gives it now and then at
  the stall when it has one; a brush when it has one; a name tag once (naming it).
- A Drifter never hits its own horse (Icy takes bond away for that).
- Loss: its horse killed or captured in a war: sadness (its moods), a line in the chronicle, talk in town; it saves up and buys
  another.

### 3. Riding long trips (Drifter)

(For a stabled horse. A Drifter whose own horse is with it already, a wild one it broke in outside a town, rides as now:
a walk of 80 blocks or more, the horse within 16. Builder's check, settled.)

- A walk of 150 blocks or more in the overworld (a constant): if it owns a horse it can get to (its stall within 64
  blocks, or the horse near), it fetches it first (walk to the stall, untie, mount), rides, and at the end ties it at
  the nearest stable with a free stall (or at a fence by the road if there's none), walks the rest.
- Picking it up again: the way home starts at the stable it left it at.
- On foot otherwise, and inside its town (short trips).
- Riding uses the roads (they cost less to walk on in its routes already; a horse keeps to them better).

### 3b. Gaits (Drifter; the user, 2026-11-16)

"trot, all the way to gallop ... a drifter trotting into town ... a group of drifters trotting to another town, no urgency
... a chase between two drifters with the gallop mechanic": with Icy's gaits, a rider walks into and through a town,
trots along the road (a group together, at the slowest one's pace), canters when hurried (late, a courier), gallops in a
chase (chasing or chased), fleeing, or called to war. Without Icy's: walking pace or sprinting, the same choice.

### 4. Stables (addon)

- Size by households: about a stall for every three households (a hamlet none, a small town two or three, a city 12 to
  20), as a longer stable or a yard of stalls.
- Stock at world creation: horses of breeds that fit the biome (with Icy's: Arabian and similar in deserts, Icelandic in
  snow, Andalusian, Thoroughbred, Morgan and so on in plains and forests, every stable a draft horse or two for carts);
  some owned by households (as now), some for sale.
- The stablehand: a line of work. Feeds and waters (hay, wheat; a cauldron), breeds pairs (Icy's genders) when stalls
  are free and there's food, tames wild horses it finds near town, sells horses and saddles (a stable shop through the
  existing shops), and drives the carriage.

### 5. Carts on the roads (addon)

- Trade runs: a merchant with a cart and a draft horse drives the cart, its goods in the cart's chest (loaded from the
  town's store, unloaded at the other town's), along the roads. Without Icy's: a pack donkey with a chest, or on horse.
- Couriers: letters to the player and between towns carried by a rider (the existing letters).
- Travellers and envoys ride.

### 6. Carriages (addon, Icy's only)

- At each stable a carriage: an Icy cart and a draft horse, its coachman one of the town's people.
- The player talks to the coachman (or uses the cart): picks a town they know from a list (the journal's known towns),
  sees the fare (by distance, `Prices`), pays, climbs in; the coachman drives the roads there (the road plan), stops at
  that town's stable, the player gets out; the coachman drives back home (or waits for a fare back).
- The ride takes real time (the player can watch the road go by).

### 7. War (addon)

- Soldiers going to war fetch and ride their horses; at the muster or near the front they tie them; horses can be hurt
  or killed.
- **Who may take whose horse** (the user, 2026-11-16: "Only war captures"): Drifters keep Icy's owner-only rule (none
  rides or leads off a horse it doesn't own: Icy's own check stops only players, so the addon adds it for Drifters; a
  bandit can't ride off on the player's horse). The one exception: in a war, a soldier may capture an enemy's horse left
  tied at the front; it becomes the captor's (its owner rewritten in Icy's data), a loss to its old owner. No theft
  otherwise, by Drifters or (Icy's rule) by players.

### 8. Without Icy's (Drifter and addon)

- Everything in 1 to 5 and 7 with vanilla horses: owning, stalls, riding, stable sizes, the stablehand (breeding with
  wheat or golden carrots), selling; no breeds (vanilla coats), no bond (no number: a "familiarity" in Drifter's own
  memory instead), no carts (merchants on horseback, a pack donkey), no carriages.

### 8b. While no player is near (builder's check, settled as my call)

Most of the world stops away from players (a few Drifters keep running; towns live on paper, `TownAway`). So:
- A carriage that brought the player waits at the town it stopped at for a fare back (it doesn't drive home alone and
  stop halfway); the next player wanting a carriage there takes it, it goes home with them or empty when the player's
  near it. Its home town gets it back when it next goes that way.
- A horse left at another town's stable stays there till its rider comes for it.
- Stables on paper: a stable's horses breed and are lost slowly while its town lives on paper (a horse a while), so a
  town met again has about as many as it should; never more than its stalls.

### 8c. Worlds already played (the user, 2026-11-16: "New worlds only")

Old worlds keep their stables and horses exactly as they are; the whole system shows only in worlds made with it.

### 9. Spike first (throwaway)

On a flat dev world with Icy's (and GeckoLib, Modonomicon) in `run/mods`: a Drifter as an Icy horse's owner
(`bh_setOwner`), riding it 200 blocks (does the bond grow?), feeding it a treat; a Drifter driving an Icy cart (a horse
with a cart hitched) with a player sat in the cart; how Icy's sets a cart up and hitches it. Its answers settle the
details of sections 2 and 6.

### 9b. Spike findings (2026-11-16, dev client, Icy's 2.1.8)

- **Owning:** `bh_setOwner(drifter UUID)` takes; Icy's shows the Drifter as owner. Icy horses are `AbstractHorse`s
  (`icy.betterhorses.net.entity.ThoroughbredHorse` and so on), so Drifter's riding works on them: a Drifter rode one 340
  blocks with `RideTask`.
- **Seeing its horse:** `NavigateTask.rideFor` uses a horse only if the Drifter has seen it in the last 10 s; a horse
  behind it isn't, and it walked. The stable job (walk to the stall first) gets round this; leaving a horse elsewhere
  (Task 5) must do the same: go to where it left it.
- **Bond (a):** Icy's own bonding counts a Drifter owner's riding (`Bonding.ridden` rose 0 to 340 over the ride); the bond
  itself rises a point per 1,000 blocks ridden (`RIDE_BLOCKS` 1000.0), so no credit of our own is needed for riding.
  Treats: only the favourite treat, a brush, a golden apple or a name tag give bond (builder's check); the favourite is
  `Bonding.treat` (null until learned, `treatKnown`).
- **Gaits (e):** `bh_setGaitGear(n)` sets the gear (1 walk, 2 trot, 3 canter, 4 gallop; 0 none), but Icy's applies its
  speeds only for a player rider (`AbstractHorseMixin.bh_applyGearSpeed` on `getRiddenSpeed(Player)`): a Drifter's horse
  ran at full speed whatever the gear. Icy's speeds (`BhGears.riddenSpeed`): gears 1 to 3 = min(0.225 x {0.2, 0.45, 0.7},
  the horse's speed); gear 4 = the horse's speed. Its gait animations are picked from how fast the horse moves
  (`client/render/BhEquineGait` reads the horse's forward speed), so `DrifterRiding` sets the horse's speed by the same
  formula (and the gear, for anything that reads it) and the animation follows.
- **Carts (c, d):** `HorseCartEntity.spawnFor(horse)` and `bindTo(horse)` make and bind a cart, but Icy's drops it at once
  when the horse has no cart gear: a cart needs Icy's upgraded saddle on the horse and the Horse Cart item in its gear
  slot (`BhHorseInteraction.equipGearFromHand(horse, data, player, hand)`), the way a player sets one up; the Drifter does
  that through the FakePlayer (Task 8, tested there). Not yet seen: the player sitting in a moving cart.

### 10. Testing

- Dev client with Icy's: a new seed 779 world: stables sized and stocked by town; townsfolk riding between towns on the
  roads and leaving horses at the arriving town's stable; a merchant's cart; the carriage (pay, ride, arrive); a bond
  growing over trips; a horse killed (grief, chronicle). Screenshots for the user.
- Without Icy's: the same world, vanilla horses, no errors.
- Load: a city's worth of riders, server tick times.

## Left to the builder

- The exact stall and stable layouts by size (Drifter-buildable, the house style), the breed table by biome, prices and
  fares, how often treats are fed, how a horse's name is chosen.
- How the carriage's destination is chosen in the user interface (a menu like the journal's, a talk choice).
- How Icy's classes are reached (compile-only jar outside the repo; reflection where a class may change).
