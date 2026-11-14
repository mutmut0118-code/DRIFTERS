# Towns and roads in the far view (LOD mods): design

Status: approved by the user, 2026-11-14 ("everything else is correct", after asking for lights). Not to be built until the user says (2026-11-14: "hold on building it
because bug fixes will be coming up").

## Intent

The user, 2026-11-14: "when distant horizons/meridian is on, i want to already see the town in the distance, along with
the roads to it instead of letting it pop up in my face", "many thousands", "when all of the chunks of the lod load i want
to see the whole civilization, think skyrim". Also: "since its a lod, the town doesnt have to be exact, but just close
enough to where you wont notice the difference, i want it to be optimized aswell", and "when the chunks is actually loaded,
the town would be accurate 1to1 since its loaded".

So: with an LOD mod on, once its far view has filled in, every place of the world plan (capitals, towns, hamlets, ruins)
and the roads and bridges between them show where they really are, recognisable from thousands of blocks away. Nothing
pops in as you walk up. Up close, real chunks take over, as they do now.

## How it is today

- The world plan (`WorldPlan`, addon) is made once in the background when a new world starts, from the world generator's
  own heights, without loading chunks: every place (`Place`: name, kind, x, z, households, nation, the `gates` its roads
  come in by) and every road between them (`Highway`: a line of points every few blocks, crossings squared or turned
  with a corner).
- Real towns are built by `TownBuilder` jobs, step by step, only when a player comes within view distance plus 20 chunks
  (`WorldBuild.ahead`, 1,024 blocks at most); before play, the loading screen builds those near spawn for up to two
  minutes. A town's shape (`TownBuilder.Shape`: crossroads, main street, winding, river, harbour, hill, ...) and its
  streets, houses, plaza, store, fields and pen are decided while it's built, from the real ground, with the level's
  random.
- Road stretches (`Highways`, `WildRoad`) are built the same way, near players, with their lamps (`RoadLamps`).
- LOD mods make their far view without any of that:
  - **Distant Horizons** (the user has 3.3.3, plus "DH SeedGen", both currently switched off in DRIFTER TEST) builds LODs
    from chunks it loads or generates itself, and from saved chunks. It has a public API, including a world generator
    override (`IDhApiWorldGenerator`) that another mod can supply.
  - **Meridian** (0.3.0, installed in DRIFTER TEST; the user, 2026-11-14: "its meridian 0.3.0 not 0.2.1"; "Long-range
    LOD terrain solved directly from the world seed"; All Rights Reserved; needs YACL) works the far land out on the
    client from the seed (the server sends it the world generator's data). Its insides are obfuscated; its public API
    (`com.leclowndu93150.meridian.api`, signatures in intermediary names) has `MeridianApi.registerBackend` (a
    `TerrainSamplerBackend`: name, priority, `open(context)` giving a `TerrainSampler`), `MeridianApi.openSampler`
    (Meridian's own sampler, to wrap), `MeridianApi.blockIndex(BlockState)`, and per-column samples (`ColumnSample`:
    surface height, top and under block, fluid, trees, a structure index) with stacked layers (`ColumnLayer`/
    `ColumnLayers`: several top/bottom spans per column, each with its own top block). Unlike 0.2.1 there's no placed-block
    simulation in the API. Whether it takes in loaded or saved chunks (0.2.1 did) is for the spike to check.

## Goals

- Every unbuilt place and road stretch of the plan shows in the far view of Distant Horizons and of Meridian, at its real
  spot, close enough to the real thing that its look doesn't jump when the real one replaces it.
- Roads: drawn where the plan says, the worn mud mix's colour, bridges as plank decks at deck height over the water.
- Towns: plaza, streets in the town's shape, houses as blocks of wall and roof in the town's style (spruce in the taiga,
  sandstone in the desert, ...), fields; cities bigger, hamlets smaller, ruins lower and fewer.
- Once a place or stretch is really built, the LOD mods show the real blocks (from loaded and saved chunks), 1 to 1.
- **Lights at night** (the user, 2026-11-14: "lights in the far view please, including the roads"): towns' lamps and lit
  windows, and the road lamps (one every 28 blocks, as `RoadLamps` places them), glow in the far view at night, so a
  nation shows as lit towns strung along lit roads. As close as each mod allows: Distant Horizons keeps light levels in
  its LOD data; whether Meridian 0.3.0 draws light from what a backend answers (e.g. an emissive block such as a lantern
  or a froglight as the column's top) is for the spike to check.
- With **DH SeedGen** on too (the user, 2026-11-14: "dh, and meridian, but make sure seedgen works with it on aswell"),
  towns and roads still show in Distant Horizons' far view.
- Singleplayer first, and **LAN** (the user: "i only play on singleplayer, but i do lan sometimes"): a friend joining by
  LAN sees the towns and roads in their far view too.
- Light: no chunk generation far away, no longer loading screen, small saved data, painting work only for the parts of
  the far view an LOD mod is drawing at that moment.

## Non-goals

- Exactness up close: house positions may differ a little between the sketch and the real town (only noticeable at
  middle distance, where the LOD is coarse).
- Banners, Drifters, smoke or anything moving in the far view.
- Worlds without a plan (old worlds, the plan setting off: towns where villages would be).
- Dedicated servers: not a goal now, but nothing should rule them out later (LAN guests already need the same client
  side as a server's players).
- Other LOD mods (Voxy, Vectra, Farfield, ...): later, one at a time, if the user asks.
- Building towns further out than now.

## Anti-goals (a failure even if it works)

- A longer loading screen, a slower server, or stutter while the LOD fills in.
- Drifter needing an LOD mod, or breaking when one is missing, older or newer (both stay optional, detected at runtime).
- The far view showing a town that then appears somewhere visibly different, or roads that don't line up with the real
  ones.
- Sketch data feeding anything Drifters decide (they know only what they've seen: DESIGN.md section 1). It's for drawing.

## Constraints

- Addon only (`src/civ`, `src/civClient`), using Drifter through its API; grow the API in the same change if needed.
- Both LOD mods optional: detected at runtime, never required, no hard dependency in `fabric.mod.json`.
- Meridian is All Rights Reserved: never commit its jar to the repo (compile against it without shipping it: a local
  compile-only copy outside git, a small stub of the few API types, or reflection; the builder picks).
- Saved data versioned (DESIGN.md section 20); tunable numbers as named constants.
- Don't bump Loom or Gradle. Version numbers `0.23.<build>` as usual (or the milestone the roadmap is at then).

## Approaches considered

- **A. Build every town and road for real at the start.** Exact, but tens of minutes of loading, a huge save, thousands of
  chunks built that may never be visited. Ruled out: the opposite of "optimized".
- **B. Paper towns (picked by the user, 2026-11-14: "go with B, i like it").** A rough sketch of every place, made with
  the plan from the generator's heights, drawn into each LOD mod's far view through its API; the real town built as now
  but from the sketch's shape, gates and street lines, so it comes out in the same shape. Light; up close the houses may
  sit a little differently.
- **C. The real builder run "on paper" for every town at the start, then built to the block.** Closest match, but the
  builder decides by looking at real blocks as it goes; making it work from predicted land means reworking much of it,
  with the risk of old town bugs coming back. Kept as a later tightening, one shape at a time, if middle-distance
  differences ever bother the user.

## The design (B)

### 1. The sketch of each place

Made for every place of the plan, after the plan itself and in the same background work, from the generator's heights
(no chunks): its style (from the biome at its middle, as `TownBuilder.Style.at`), its shape, the plaza, its streets (the
same kind of street laying as the real builder, on the predicted heights), house boxes along them (position, size, wall
and roof blocks by style, roof height), fields, the town's lamps (plaza corners and along its streets, as the real builder spaces them) and some lit windows,
and for a city its larger extent. A ruin: fewer houses, lower, no roofs.

Saved with the plan (versioned). Small: a few hundred boxes and a few lines per place.

### 2. The real town follows the sketch

When a place is really built, its `TownBuilder` job takes from the sketch: the shape, the street lines' headings (its
gates already come from the plan), and a random seeded from the place (not the level's random), so it lays its streets
the same way over the same land. Houses are still fitted to the real ground, so they may shift a little. Real ground
differs from the predicted heights only by trees and vanilla features, so street lines should come out nearly the same.

### 3. Roads in the far view

From the plan's road lines as built (`Highways.smooth`, squared and cornered crossings), each stretch not yet built:
about three to five wide in the worn mud colours, a plank deck at the bridge height over water, and its lamps at the
spots `RoadLamps` would put them (the same spacing and sides; worked out from the road line, not from real ground, so a
skipped spot may still show a light: fine at that distance), each a lit block high enough to glow at night.

### 4. Drawing into Distant Horizons

Through DH's API, only for chunks with no real (saved) chunk: the sketch's blocks painted over the land DH would make
there (houses as raised wall/roof columns, streets and roads as surface blocks). How exactly (the world generator
override wrapping DH's own, or another API path DH 3.3.3 has) is settled by the spike (section 7). Where a real chunk
exists, DH uses it as now.

**With DH SeedGen on:** SeedGen 0.9.1 (All Rights Reserved, no API) replaces DH's chunk generation with terrain from the
seed through mixins into DH's insides, so a world generator override would likely be bypassed or fight it. The hookup
must still draw the sketches when SeedGen is on. The way that survives any source of LOD data is to paint the sketches
into DH's LOD data after whatever made it (DH's own generator, SeedGen, or saved chunks) has written it, for the
sections a sketch touches; if DH's API has no such step, a small, guarded hook on DH's side (or on SeedGen's, detected
at runtime and skipped cleanly when its classes don't match). The spike settles which, with SeedGen off and on.

### 5. Drawing into Meridian

A terrain sampler backend (`MeridianApi.registerBackend`) with a higher priority than Meridian's own, wrapping
Meridian's sampler (`MeridianApi.openSampler`): for columns inside a sketch or an unbuilt road stretch, it answers with
the sketch's surface (height, top block, under block), houses as raised columns or stacked layers (`ColumnLayers`) with
their roof block on top; elsewhere, Meridian's own answer. Real towns: from Meridian's own capture of loaded chunks if
0.3.0 does that (the spike checks); if it doesn't, the backend keeps drawing a built town from the sketch, or from a
summary of the real town's blocks sent by the server (builder's call after the spike).

### 5b. No phantom villages (my call, 2026-11-14)

A planned world never builds vanilla villages. If an LOD mod draws them anyway from the seed (Meridian has a structure
index per column; DH SeedGen ships village pieces), each hookup removes them, so the far view shows only the plan's
places. The spike checks whether either mod draws them; if neither does, nothing to do.

### 6. Getting the sketches to the client

Meridian works on the client, so the client needs the sketches and road lines: sent by the server when a player joins
(compressed; once), and the changes as places and stretches get built (so the client stops drawing a sketch over a real
town). Singleplayer goes through the same path. DH's generation runs on the integrated server in singleplayer.

**LAN guests** get the sketches the same way. Their game doesn't get a built town's real chunks until they come near
(the town is built 20 chunks past view distance) and has no save files to read, so for them the client keeps drawing a
built town's (and stretch's) sketch until its real chunks arrive. Whether a LAN guest's Distant Horizons draws from the
host's LODs or its own is for the spike to check; either way the guest should see the towns.

### 7. A throwaway spike first

Before the plan: on a flat dev world, one fake town and one road drawn into DH 3.3.3 and into Meridian 0.3.0, (DH with
SeedGen off and on), to prove each hookup and see what it looks like (screenshots for the user). What it teaches decides sections 4 to 6's details.
The dev client then needs DH, Meridian 0.3.0 and YACL in `run/mods` (not in the repo). Meridian compiles against its
jar as a local compile-only mod dependency outside git (Loom maps its intermediary names), never committed.

### 8. Testing

- Dev client, a new seed 779 world on Tectonic, DH on, then Meridian on: screenshots from a high spot showing towns and
  roads thousands of blocks out; walk toward one and compare far, middle and near (no visible jump).
- At night (`/time set midnight`): towns and roads lit in the far view, in both mods.
- DH with SeedGen on: towns and roads still drawn.
- A LAN guest (a second dev client joining the first): sees the towns in its far view.
- Without either mod: nothing changes, no errors.
- Timing: the plan's background work with sketches, the loading screen, server tick times while the LOD fills in.

## Left to the builder

- The sketch's data layout, how the street laying is shared between the sketch and the real builder, and the house
  footprints' sizes per style.
- How to compile against DH's and Meridian's APIs without shipping Meridian.
- The packet format for sending sketches, and the grid used to find which sketches touch a chunk or column.
- The exact colours/blocks used for roofs and walls per style.
