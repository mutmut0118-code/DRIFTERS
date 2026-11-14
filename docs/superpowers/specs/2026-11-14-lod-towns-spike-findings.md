# Paper towns: what the spike found (2026-11-14)

The throwaway test of `docs/superpowers/plans/2026-11-14-lod-towns.md` Task 0: one fake town (plaza, four streets,
twelve houses, four lamps) and one fake road (packed mud, a plank deck, a lamp every 28 blocks) painted into the far
view of a seed 779 Tectonic planned world at -2600, 2800, about 830 blocks from the nearest planned place. The code is
on the local branch `spike/lod-towns` (never merged, never pushed). Screenshots: `docs/superpowers/shots/lod-spike-*.png`.

The plan said a flat world; a real one was used, because SeedGen and Meridian both need real noise land. The first spot
picked turned out to be open sea (the user saw it); the land was then searched for with the generator's heights.

## In short

| Mod | Result |
| --- | --- |
| Distant Horizons 3.3.3 | **Works.** Town, road, bridge deck and glowing lamps drawn (`lod-spike-a-*`). |
| DH + SeedGen 0.9.1 | **Works, same hook.** (`lod-spike-b-*`) |
| Meridian 0.3.0 | **Half.** Our columns get into Meridian's pipeline and change the picture, but houses don't read as houses yet, and there's no light (Meridian has no lighting system: the user, 2026-11-14). (`lod-spike-c5-*`, `c6-*`) |

## Distant Horizons (run A)

- **A1. The hook: (a), a world generator override. API only.** On `DhApiLevelLoadEvent` (fired on the Server thread, for
  a level whose wrapped object is a `ServerLevel`), `DhApi.worldGenOverrides.registerWorldGeneratorOverride(level, ours)`
  with `getPriority()` 50. Ours passes every call to the generator DH would have used and, in `generateLod`, hands that
  one a result consumer of its own: when the `IDhApiFullDataSource` comes back, the touched columns are rewritten with
  `getApiDataPointColumn` / `setApiDataPointColumn(relX, relZ, EDhApiWorldGenerationStep.LIGHT, points)` and then passed
  on. (b) and (c) weren't needed: no mixin into DH.
- **A2. DH's own generator can be wrapped, with two small reaches past the API.** DH binds its own
  `com.seibel.distanthorizons.core.generation.DhWorldGenerator` (priority -1) only when nothing is bound, so ours must
  make one itself: `new DhWorldGenerator(IDhServerLevel)`, the level from
  `SharedApi.getAbstractDhWorld().getLevel(levelWrapper)` (reflection in the spike). Another mod's override (SeedGen's)
  is found in `WorldGeneratorInjector.INSTANCE`'s private per-level map (`OverrideInjector.get(IDhApiWorldGenerator.class,
  priority)`, priorities below ours). Both are found lazily, at the first call, on `DH-World Gen Queue Thread`.
- **Data shape.** Sections are 64 x 64 columns, a column `2^detail` blocks wide (details 0 to 12 for DH's own, the origin
  `chunkPosMin * 16`). A column is a top-down list of `DhApiTerrainDataPoint`s with no gaps: one air point on top (to the
  build limit, sky light 15), then the ground. Spans are added as their own points with air between; a point's light is
  the light on top of it.
- **A3. Light: yes.** A lamp block with block light 15 and ground points round it with their own falling light glow at
  midnight (`lod-spike-a-night-near.png`, `-night-road.png`). Neighbouring columns do need their own values: DH doesn't
  spread it.
- **A4. Redoing LODs made earlier: nothing in the API** (`clearRenderDataCache` only rebuilds meshes). For a new world it
  isn't needed: the plan (every place's spot and reach) exists before the world opens, so the wrapper can hold back the
  few sections that touch a place whose sketch isn't made yet. For a world whose far view was made before this feature,
  the old LODs stay until the land is visited or DH's data for the world is deleted: see "Design questions".
- **A5. Real chunks replace the painted ones by themselves** (read in DH's code, not yet watched in game: a loaded or
  changed chunk goes through `SharedApi.applyChunkUpdate` and overwrites those columns; painting happens only in the
  generator path). To be watched in Task 6's walk-up.
- **A6. No vanilla villages in a Drifter world** (Drifter's `setTownsForVillages` hook stops them being placed), and DH's
  own generator runs the game's generation, so none are drawn.
- **A7. Threads and cost.** Painting runs on `DH-World Gen Thread[n]`: 24 touched sections in 23 ms (about 1 ms each,
  untouched sections cost one box test). Nothing on the server thread.
- **Where it runs.** The generator runs where the `ServerLevel` is: single player, a LAN host, or a dedicated server with
  DH. A LAN guest's DH has no generator of ours to call; it gets LODs from the host only as far as DH's own networking
  sends them (not tested).
- **Compiling.** Only DH's `api` package is needed and it names no Minecraft types, so a plain compile-only dependency
  does (no remapping). The Modrinth maven coordinate wasn't looked up (the spike used the jar from a git-ignored folder).

## SeedGen (run B)

- **B1, B3. The same hook serves both.** SeedGen registers `mobius.seedrenderer.dh.SeedWorldGenerator` (priority 10) in
  its own `DhApiLevelLoadEvent`; like DH's own it returns `API_DATA_SOURCES` through `generateLod`. Ours (priority 50)
  wrapped it and painted the same way: 45 sections in 46 ms; details 0 to 18.
- **B2. Phantom buildings: likely, not yet pinned down.** SeedGen logs that it "reads 2 structure sets of mods ...
  [drifter_civ:cities, drifter_civ:towns] from their own definitions" and draws stand-ins where those sets would place
  something, whether or not the plan uses that spot. To check in Task 6 on a planned world; ways out: paint over them
  (we only know our own places), or stop those sets placing anything in a planned world, or tell the user to switch
  SeedGen's "structures" off.

## Meridian (runs C1 to C6)

- **C1. `registerBackend` is used, and wrapping works.** A backend with `priority()` 10,000 is asked first; calling
  `MeridianApi.openSampler(context)` from inside `open(context)` works with a thread-local re-entry guard (ours answers
  empty the second time) and returns Meridian's own `java-graph` sampler. Meridian's own priorities weren't readable
  (obfuscated).
- **The catch: the wrapper must report its own `backendName()`.** Reporting the wrapped sampler's name (`java-graph`),
  Meridian asked the sampler for 64 spot checks on the Server thread and nothing else: it solves the land on the client
  from the worldgen values it syncs. Reporting a name of its own, Meridian samples everything through it.
- **C2. Calls and threads.** `sampleGrid` (and a few `sampleDraftGrid`) on `Meridian-LOD-n`, `Meridian-Capture` and
  `Meridian-Structures` threads, client side: 36,000 to 52,000 grids in a three-minute run, spacings 1, 2 and 4, a flat
  index of `z * n + x`. `sample` only for the server's parity probe; `surfaceY` never.
- **C3. Blocks: a sample's block index is a place in the sampler's own `tables().blocks()`** (28 state ids in this
  world), not `MeridianApi.blockIndex(state)` (which answered the game's own state id). The wrapper has to answer a
  `SamplerTables` with our blocks added on the end and use those places. With the wrong numbers our columns came out as
  pale specks (`lod-spike-c5-day-east.png`); with the right ones the specks went and a lamp's glowstone shows as an
  orange dot (`lod-spike-c6-day-east.png`), which proves our answer reaches the picture. **But houses as raised columns
  (surface up, roof block on top, wall block under) don't read as houses**: `ColumnLayers` and Meridian's own structure
  path (`SamplerTables.structures`, the `Meridian-Structures` thread) are the next things to try.
- **C4. Light: none.** Meridian has no lighting system yet (the user, 2026-11-14).
- **C5, C6.** Not reached (real chunks, vanilla villages through `structureIndex`). Meridian itself warns that it can't
  follow Drifter's structure placement patch, "so structures may differ".
- **C7. Record orders confirmed from the bytecode.** `ColumnSample`: surfaceY, fluidY, biomeIndex, topBlockIndex,
  structureIndex, treeKind, treeDensity, treeHeight, fluid, flags, groundFeatureKind, underBlockIndex, deepBlockIndex,
  surfaceBottom, lowerTop, lowerBottom, spanFloor. `ColumnLayer`: top, bottom, topBlock, underBlock, fluid, fluidY.
- **Compiling.** Meridian's jar can't go through our Loom ("built with a newer version of Loom (1.18.2)"); the spike used
  a local copy with that line taken out of its manifest. The product keeps the hand-written stub, as planned.
- **Cost.** Every grid goes through the wrapper, so the box test must be cheap; a touched grid took 4 to 140 microseconds
  (the spike's one-off index check aside).

## What changes in the plan

1. The Distant Horizons hookup runs on the host with the server's painter, so it belongs in the common code (`src/civ`),
   not the client's; it needs no packets.
2. Packets (Task 5) are for Meridian (it samples on the client) and for whatever DH turns out not to send a LAN guest.
3. Meridian: the hookup is possible but its look isn't proven. Task 7 starts with its own short try-out of houses
   (layers, then structures) before anything is built on it; lights are left out until Meridian has lighting.
4. Sections near a place whose sketch isn't ready are held back instead of redone.

## Voxy 0.2.15-beta and Voxy SeedGen 0.3.1 (read only, not yet tried; the user, 2026-11-14: "there is actually another mod")

Both are in DRIFTER TEST and both are All Rights Reserved (never in git). Read from the jars, nothing run yet:

- **Voxy alone has no far land of its own**: it only keeps what the player's game has loaded (`VoxelIngestService`). Far
  towns can only be painted where something has put land there, which is Voxy SeedGen's job. So this hookup matters
  with Voxy SeedGen on.
- **No public API in either.** Voxy stores 16-block sections of voxels (`VoxelizedSection`, a `long` a voxel: block,
  biome and light, so lamps could glow) written with `WorldUpdater.insertUpdate(WorldEngine, VoxelizedSection)`. Voxy
  SeedGen writes its land through its own `VoxyChunkWriter.column(worldX, worldZ, stride, ColumnSpans)` (spans of
  block, biome and light a column, much like Distant Horizons' data points) and `chunkFinished`.
- **Ways in, to try in this order:** (1) a guarded mixin (`"required": false`, only when `voxyseedgen` is loaded with a
  version starting `0.3.`) on `VoxyChunkWriter.column`, adding the painter's spans to the column before it's written;
  (2) writing our own sections over the top with Voxy's `WorldUpdater.insertUpdate` after SeedGen has written a chunk.
- **Needs its own try-out** like the other two (the fake town in a dev client with Voxy + Voxy SeedGen + Sodium), and a
  decision from the user: it isn't in the approved design.
