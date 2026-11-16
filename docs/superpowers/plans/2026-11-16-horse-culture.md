# Horse Culture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Horses as how Drifters travel between towns and to war: each traveller's own horse (Icy's Better Horses
ownership and bond when installed), stables sized and stocked by town with a stablehand, riders leaving horses at the
arriving town's stable, merchants' carts, couriers, carriages for the player, war captures; vanilla horses without Icy's.

**Architecture:** Drifter (`src/main`) gets one reflection-only adapter for Icy's (`compat/IcyHorses`) and the general
parts (owning in Icy's terms, the bond, owner-only riding, leaving a horse at another stable); the addon (`src/civ`)
gets the town parts (stables, stablehand, carts, carriages, couriers, war) through the API (`DrifterApi`, `TownWork`).
Icy's is reached only by reflection (no compile dependency: GitHub Actions builds without its jar), every call guarded
by `IcyHorses.present()`.

**Tech Stack:** Java 21, Fabric 1.21.1 (Mojang mappings), Fabric API 0.116.17; Icy's Better Horses 2.1.8 (+ GeckoLib,
Modonomicon) only in the dev `run/mods` and the user's pack.

**Spec:** `docs/superpowers/specs/2026-11-16-horse-culture-design.md` (approved 2026-11-16).

## Global Constraints

- Fairness (DESIGN.md section 1): no horse, saddle, cart, treat or coin from nowhere; riding by virtual keys (`RideTask`);
  interactions through the FakePlayer at the moment of interacting; no teleporting (a coachman drives the road).
- Optional mod: Drifter and the addon run, and the build passes, without Icy's; Icy's jar never in the repo.
- Drifter vs addon: the addon uses Drifter only through `com.dinglebob.drifter.api` (grow it in the same change, docs/API.md).
- New worlds only for stables, stock and carts (the user: "New worlds only"); old worlds keep theirs as they are.
- Theft: only war captures (the user): no Drifter rides or leads off a horse it doesn't own, except an enemy's horse tied
  at a war front.
- Tunables as named constants: `STALL_PER_HOUSEHOLDS = 3`, `StableGoal.RIDE_TRIP = 150.0` (exists), wild-horse riding
  `NavigateTask.RIDE_FROM = 80.0` (exists, kept for horses already with their rider).
- Saved data versioned; docs/PROGRESS.md, TESTING.md, LIVED_WORLD.md and API.md updated; version `0.23.<n>` bumped per
  jar; never replace jars while `javaw.exe` runs; commit only this session's files.

## Review Focus

- **Icy's missing or a different version:** every Icy call returns a safe default; nothing throws. Test: run the dev
  client without Icy's jar through Task 9's script: no errors in the log, vanilla horses in stables.
- **A Drifter meeting the player's own Icy horse** (tied, or left loose): never mounts or leads it. Test in Task 3: a
  player-owned Icy horse beside a Drifter that wants a horse; ten minutes; still the player's, never ridden.
- **A rider's horse dying on the road:** it walks on, grieves, buys another later; its stall freed. Test in Task 4.
- **No free stall at the arriving town:** it ties the horse to a fence by the stable, or the nearest fence post; never
  leaves it loose to wander off. Test in Task 5.
- **A carriage fare the player can't pay, or a destination they don't know:** told why, no ride, nothing taken. Test in
  Task 8.

---

### Task 1: Spike: Drifters and Icy's (throwaway)

**Files:**
- Create (throwaway, not committed): debug commands in a scratch branch or behind `DRIFTER_ICY_SPIKE=1`
- Modify: `docs/superpowers/specs/2026-11-16-horse-culture-design.md` (a "Spike findings" section)

**Interfaces:**
- Consumes: Icy's classes by reflection: `icy.betterhorses.net.IHorseData` (implemented on horses by mixin:
  `bh_getOwner()`, `bh_setOwner(UUID)`, `bh_getBond()`, `bh_setBond(int)`, `bh_setHome(BlockPos)`, `bh_getBreed()`,
  `bh_getCartEntity()`, `bh_bindCartEntity(HorseCartEntity)`), `icy.betterhorses.net.entity.HorseCartEntity`.
- Produces: findings that settle Tasks 4 and 8: (a) does Icy's bond grow when a Drifter owner rides; (b) the horse's
  favourite-treat field and how to read it; (c) how a cart is made, hitched to a horse, given a chest, and sat in by a
  player; (d) whether a Drifter riding a hitched horse moves the cart; (e) how Icy's gaits work (`bh_getGaitGear()`,
  `bh_setGaitGear(int)`: which numbers are walk, trot, canter, gallop; whether a non-player rider's keys follow the gait).

- [ ] **Step 1:** Copy Icy's, GeckoLib and Modonomicon jars from `C:\Users\mutmu\AppData\Roaming\ModrinthApp\profiles\DRIFTER TEST\mods` to `run/mods` (not committed).
- [ ] **Step 2:** In the flat `run/saves/lamps` world (copy it): summon an Icy horse (`/summon icys-better-horses:thoroughbred_horse`), make the nearest Drifter its owner by reflection (`bh_setOwner(drifter UUID)`), saddle it, `/drifter goto ~200 ~ ~`; log `bh_getBond()` before and after.
- [ ] **Step 3:** Make a cart (two wheels, Icy's recipe, or `/give` it), hitch it to a horse ridden by a Drifter, sit the player in the cart, `/drifter goto ~100 ~ ~`; screenshot; note whether the cart follows and the player rides along.
- [ ] **Step 4:** Read the bond and treat fields named in Icy's `feature/Bonding` (`treat`, `treatKnown`) by reflection; note how a favourite is found.
- [ ] **Step 4b:** Set the ridden horse's gait gear to each value in turn while the Drifter rides; note its speed and the animation for each (screenshots).
- [ ] **Step 5:** Write the five findings into the spec's "Spike findings" section; commit the spec only.

### Task 2: The Icy's adapter (Drifter)

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/compat/IcyHorses.java`
- Modify: `src/main/java/com/dinglebob/drifter/api/DrifterApi.java` (pass-throughs the addon needs)
- Modify: `docs/API.md`

**Interfaces:**
- Produces (all static, all safe without Icy's):
  - `boolean present()`: Icy's loaded and its classes found (cached).
  - `@Nullable UUID owner(AbstractHorse horse)`, `void setOwner(AbstractHorse horse, UUID owner)`
  - `int bond(AbstractHorse horse)`, `void addBond(AbstractHorse horse, int amount)` (capped as Icy caps)
  - `void setHome(AbstractHorse horse, BlockPos home)`
  - `@Nullable String breed(AbstractHorse horse)` (Icy's breed id, e.g. `arabian`)
  - `@Nullable AbstractHorse spawn(ServerLevel level, String breed, BlockPos at)` (`icys-better-horses:<breed>_horse`)
  - `@Nullable Item favouriteTreat(AbstractHorse horse)` (per Task 1 finding (b); null if unknown)
  - `@Nullable Entity cart(AbstractHorse horse)`, `@Nullable Entity hitchCart(ServerLevel level, AbstractHorse horse)` (per Task 1 (c))
  - `DrifterApi.icyHorses()` returning `present()`; `DrifterApi.horseBond(AbstractHorse)`; `DrifterApi.spawnHorse(...)`.

- [ ] **Step 1:** Add a debug command `/drifter icy` printing `present()`, and for the horse you look at: owner, bond, breed, favourite, cart.
- [ ] **Step 2:** Run the dev client with Icy's (Task 1 setup): the command prints the right values for an Icy horse; without Icy's jar it prints `present: false` and no errors.
- [ ] **Step 3:** Implement `IcyHorses` by reflection (method handles found once, `MethodHandles.lookup().findVirtual`; any failure sets `present()` false and logs once).
- [ ] **Step 4:** Re-run both checks; `./gradlew build` passes without Icy's on the classpath.
- [ ] **Step 5:** Commit.

### Task 3: Owning in Icy's terms; owner-only riding (Drifter)

**Files:**
- Modify: `src/main/java/com/dinglebob/drifter/api/DrifterApi.java` (`keepHorse`)
- Modify: `src/main/java/com/dinglebob/drifter/skills/HorseGoal.java` (`wildHorse`: skip horses with an Icy owner)
- Modify: `src/main/java/com/dinglebob/drifter/executor/task/RideTask.java`, `src/main/java/com/dinglebob/drifter/skills/StableGoal.java` (mount and untie only its own horse)

**Interfaces:**
- Consumes: `IcyHorses.setOwner`, `IcyHorses.setHome`, `IcyHorses.owner`.
- Produces: `DrifterApi.keepHorse(drifter, horse, stall)` sets the vanilla owner and, with Icy's, Icy's owner and home (the
  stall); `DrifterApi.mayRide(DrifterEntity, AbstractHorse) -> boolean` (its own, or a war capture allowed by Task 7).

- [ ] **Step 1:** Test script: a player-owned Icy horse (owner set to the player with `/drifter icy own`) tied beside a Drifter with a saddle that wants a horse; `/drifter brain goal horse`; ten minutes (`tick sprint`).
- [ ] **Step 2:** Run it: before the change, note what happens (it may try to break the horse in).
- [ ] **Step 3:** Implement: `keepHorse` sets Icy's owner and home; `HorseGoal.wildHorse` skips any horse with an owner (vanilla or Icy's); `RideTask`/`StableGoal` check `mayRide`.
- [ ] **Step 4:** Re-run: the Drifter never mounts the player's horse; `/drifter icy` on a stable horse shows the Drifter as owner.
- [ ] **Step 5:** Commit.

### Task 4: The bond, treats and loss (Drifter + addon)

**Files:**
- Create: `src/main/java/com/dinglebob/drifter/skills/HorseCare.java` (a goal: at the stall, give the favourite treat, a brush, a name tag once)
- Modify: `src/main/java/com/dinglebob/drifter/executor/task/RideTask.java` (credit riding distance if Task 1 (a) says Icy's doesn't)
- Modify: `src/main/java/com/dinglebob/drifter/api/DrifterEvents.java` (`HORSE_LOST(drifter, horseName, how)`)
- Modify: `src/civ/java/com/dinglebob/drifterciv/Chronicle.java` (a line on `HORSE_LOST`), its moods (sad a few days)

**Interfaces:**
- Consumes: `IcyHorses.favouriteTreat`, `IcyHorses.addBond`, `IcyHorses.bond`.
- Produces: `HorseCare` goal id `horsecare`; `DrifterEvents.HORSE_LOST`.

- [ ] **Step 1:** Test script: a Drifter owning an Icy horse with its favourite treat and a brush in pocket; ride 300 blocks and back; log the bond before and after; then `/kill` the horse and read the chronicle and the Drifter's thoughts.
- [ ] **Step 2:** Run it before the change: note the bond (unchanged, if Icy's doesn't count Drifters) and no grief.
- [ ] **Step 3:** Implement the treats (favourite learned by trying the treats it has, one a day; a brush now and then; a name tag once, naming it), the riding credit (Icy's own rate and cap), never hitting its own horse, and the loss event, mood and chronicle line.
- [ ] **Step 4:** Re-run: bond up; the treat given; after the kill, "lost its horse <name>" in the chronicle, sad thoughts; the stall freed (`DrifterBrain.setHorse(null, null)`).
- [ ] **Step 5:** Commit.

### Task 4b: Gaits: walk, trot, canter, gallop (Drifter)

(The user, 2026-11-16: "trot, all the way to gallop ... a drifter trotting into town ... a group of drifters trotting to
another town, no urgency ... a chase between two drifters with the gallop mechanic".)

**Files:**
- Modify: `src/main/java/com/dinglebob/drifter/compat/IcyHorses.java` (`int gait(AbstractHorse)`, `void setGait(AbstractHorse, Gait)`)
- Modify: `src/main/java/com/dinglebob/drifter/executor/task/RideTask.java` (the gait for the moment)
- Create: `src/main/java/com/dinglebob/drifter/skills/Gait.java` (`enum Gait { WALK, TROT, CANTER, GALLOP }`, `static Gait choose(DrifterEntity rider, ...)`)

**Interfaces:**
- Consumes: Task 1 finding (e).
- Produces: `Gait.choose(rider, inTown, urgent, chasing, fleeing, companions)`: WALK within a town's land and the last 32
  blocks of a ride; TROT on the road, no hurry (a group rides at the slowest of them, side by side); CANTER on an errand
  that's late or a courier; GALLOP chasing or chased (`Pursuit`, fleeing on horseback), called to war or to the alarm.
  Without Icy's: vanilla has no gaits; the same choice sets how hard it rides (walk keys vs sprint).

- [ ] **Step 1:** Test script: a rider sent 300 blocks along a road into a town (screenshots on the road and coming in); a group of three sent together; a chase: one Drifter on horseback made to flee from another (`/drifter` debug pursuit) on horses.
- [ ] **Step 2:** Run before the change: every rider at the same pace.
- [ ] **Step 3:** Implement `Gait` and the choice in `RideTask` (set once per change, not every tick).
- [ ] **Step 4:** Re-run: trotting on the road, walking into town; the group together at a trot; the chase at a gallop. Screenshots for the user.
- [ ] **Step 5:** Commit.

### Task 5: Leaving the horse at the arriving town's stable (Drifter + addon)

**Files:**
- Modify: `src/main/java/com/dinglebob/drifter/skills/StableGoal.java`
- Modify: `src/main/java/com/dinglebob/drifter/api/TownWork.java` (`@Nullable BlockPos freeStall(DrifterEntity drifter, BlockPos near)`)
- Modify: `src/civ/java/com/dinglebob/drifterciv/TownRoads.java` (`Work.freeStall`: the nearest town's stable stall nobody's horse is in)

**Interfaces:**
- Consumes: the stable's stall list (Task 6 keeps it on `Town`: `List<BlockPos> stalls`).
- Produces: `TownWork.freeStall`; `DrifterBrain` remembers where its horse is (`horseAt`, a stall or a post) when that isn't its home stall.

- [ ] **Step 1:** Test script: two towns 300 blocks apart on a road; a rider with a stabled horse sent to the other town (`/drifter goto` the other plaza); then home again.
- [ ] **Step 2:** Run before the change: note where the horse ends up.
- [ ] **Step 3:** Implement: arriving within 64 blocks of a town that isn't its own, it rides to a free stall there (`freeStall`), ties the horse (lead on the stall's post), walks in; with none free, ties it to the nearest fence; going home, it starts from where the horse is.
- [ ] **Step 4:** Re-run: horse tied in the other town's stall; later back in its home stall. And with the other stable full: tied to a fence, not loose.
- [ ] **Step 5:** Commit.

### Task 6: Stables sized and stocked; the stablehand; buying horses (addon)

**Files:**
- Modify: `src/civ/java/com/dinglebob/drifterciv/TownBuilder.java` (`stable()`: size by households, breeds by biome, keep `Town.stalls`)
- Modify: `src/civ/java/com/dinglebob/drifterciv/Town.java` (`List<BlockPos> stalls`, saved; `List<UUID> forSale`)
- Create: `src/civ/java/com/dinglebob/drifterciv/Stables.java` (the breed table by `TownBuilder.Style`, the stablehand's work, sales)
- Modify: `src/civ/java/com/dinglebob/drifterciv/Trades.java` (the `stablehand` line), the shops (a horse and saddles for the player)

**Interfaces:**
- Consumes: `DrifterApi.spawnHorse(level, breed, pos)` (Icy's breed, or a vanilla horse), `keepHorse`.
- Produces: `STALL_PER_HOUSEHOLDS = 3`; breed table: desert `arabian`, snowy `icelandic`, savanna `appaloosa`/`mustang`,
  taiga `haflinger`/`friesian`, plains `thoroughbred`/`andalusian`/`morgan`/`quarter`; every stable one draft
  (`shire`/`clydesdale`/`belgian`/`percheron`) for carts; `Stables.sellTo(DrifterEntity buyer)` (coins from its purse to
  the town's till, the horse's owner becomes it).

- [ ] **Step 1:** Test: a new seed 779 world with Icy's: `/drifterciv town` near a town and a city prints stall counts; screenshots of a town's and a city's stable.
- [ ] **Step 2:** Run before the change: three stalls at most.
- [ ] **Step 3:** Implement the size (hamlet none; stalls = households / 3, a long stable or a yard of rows), stock (a household's horse each as now, plus a few for sale, of the biome's breeds, one draft), the stablehand line (feeds and waters, breeds two horses when a stall's free and there's food, tames a wild horse near town, sells), and a traveller without a horse buying one when it has the coins (`Prices` value).
- [ ] **Step 4:** Re-run: a city with 12 or more stalls, desert towns' Arabians, a draft horse in each; a Drifter buys one (log line, coins moved).
- [ ] **Step 5:** Commit.

### Task 7: War on horseback and captures; couriers (addon)

**Files:**
- Modify: the war code in `src/civ/java/com/dinglebob/drifterciv/Diplomacy.java` (soldiers fetch horses for war, tie them at the muster)
- Modify: `src/civ/java/com/dinglebob/drifterciv/Words.java` (letters carried by a riding courier)
- Modify: `src/main/java/com/dinglebob/drifter/api/DrifterApi.java` (`allowCapture(AbstractHorse, DrifterEntity captor)`, used by `mayRide`)

**Interfaces:**
- Consumes: `mayRide`, `IcyHorses.setOwner`, `HORSE_LOST`.
- Produces: an enemy horse tied at a war front may be taken (owner rewritten; its old owner's `HORSE_LOST` with how "taken in war").

- [ ] **Step 1:** Test: `/drifterciv` debug war between two towns 300 blocks apart; watch soldiers ride out (screenshots); a courier letter to the player.
- [ ] **Step 2:** Run before the change: soldiers walk.
- [ ] **Step 3:** Implement the riding to war, tying at the muster, captures (only tied enemy horses at the front), the courier riding.
- [ ] **Step 4:** Re-run: soldiers ride; a capture logged; the courier rides up with the letter.
- [ ] **Step 5:** Commit.

### Task 8: Merchants' carts and carriages (addon, Icy's only)

**Files:**
- Modify: `src/civ/java/com/dinglebob/drifterciv/TradeRuns.java` (a cart with a chest behind the stable's draft horse; goods really in the chest)
- Create: `src/civ/java/com/dinglebob/drifterciv/Carriages.java` (a coachman, the fare by distance from `Prices`, the destination chosen from the towns the player knows, driving the road plan, waiting at the destination for a fare back)
- Create (client): `src/civClient/java/com/dinglebob/drifterciv/client/CarriageScreen.java` (choose a town, see the fare)

**Interfaces:**
- Consumes: `IcyHorses.hitchCart`, `IcyHorses.cart`, the road plan (`Highways`, `WorldPlan.highways`), the journal's known towns.
- Produces: packets `Carriage.Offer(List<String> towns, List<Integer> fares)` and `Carriage.Pick(String town)`.

- [ ] **Step 1:** Test: two towns on a road with Icy's; watch a trade run (screenshot the cart on the road, the chest's goods logged); talk to a coachman, pick the other town, pay, ride, arrive (screenshots along the way); try with too few coins and with an unknown town (told why, nothing taken).
- [ ] **Step 2:** Run before the change: trade runs on foot, no carriage.
- [ ] **Step 3:** Implement the merchant's cart and the carriage (the coachman rides the draft horse, the player sits in the cart, the fare taken at the start, the drive along the road's points, getting off at the stable, the carriage waiting there).
- [ ] **Step 4:** Re-run: both; Review Focus cases pass.
- [ ] **Step 5:** Commit.

### Task 9: Away from players; vanilla fallback; docs; release (addon + Drifter)

**Files:**
- Modify: `src/civ/java/com/dinglebob/drifterciv/TownAway.java` (stables on paper: a horse bred or lost now and then, never past the stalls)
- Modify: `docs/PROGRESS.md`, `docs/TESTING.md`, `docs/LIVED_WORLD.md`, `docs/API.md`, `gradle.properties`

- [ ] **Step 1:** Test without Icy's jar in `run/mods`: a new world; stables sized, vanilla horses, riders leave horses at other stables, no carts, no carriages; no errors in the log.
- [ ] **Step 2:** Test away from players: a town left 20 game days (`tick sprint`) then visited: its stable's horses about as many as its stalls allow.
- [ ] **Step 3:** Implement the paper-side stable upkeep; fix whatever the two tests show.
- [ ] **Step 4:** Re-run both; `./gradlew build` passes; docs written (what to check, in plain words, with the commands).
- [ ] **Step 5:** Bump the version, build from a clean worktree, commit, push; install when `javaw.exe` isn't running.
