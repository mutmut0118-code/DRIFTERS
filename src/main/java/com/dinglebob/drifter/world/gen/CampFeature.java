package com.dinglebob.drifter.world.gen;

import com.mojang.serialization.Codec;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.building.StarterHut;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A Drifter camp (section 4 "Optional world-gen camps"): its starter hut (floor, walls, roof, a door), a chest of a
 * house's worth of things (the village house loot table), a crafting table and furnace, a bed, and a small wheat patch
 * by the door. Half of them still have their Drifter: it starts a few days into its game, caught up on the days it has
 * "lived" there. Only on flat, dry, open ground with no trees in the way.
 */
public class CampFeature extends Feature<NoneFeatureConfiguration> {
	private static final int SITE_TRIES = 6;
	private static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
		ResourceLocation.withDefaultNamespace("chests/village/village_plains_house"));

	public CampFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		if (!DrifterConfig.worldgenCamps() || !campChunk(context.level(), new net.minecraft.world.level.ChunkPos(context.origin()))) {
			return false;
		}

		// How common camps are: a setting (user, 2026-10-17), rolled for each spot a camp could go.
		if (context.random().nextDouble() >= DrifterConfig.spawnChance(com.dinglebob.drifter.DrifterConfig.Spawn.CAMPS)) {
			return false;
		}

		if (context.random().nextDouble() < DrifterConfig.spawnChance(com.dinglebob.drifter.DrifterConfig.Spawn.CAMP_HOUSES) && this.house(context)) {
			return true;
		}

		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		// Its look (user's request, 2026-10-17: huts that vary): size, height, floor, corner posts, glass windows.
		Home style = new Home(BlockPos.ZERO, Level.OVERWORLD.location(), 0L);
		style.pickHutStyle(new java.util.Random(random.nextLong()));
		// A few spots round where it was placed: the first with room for the camp (on hilly land, flat spots are scarce).
		BlockPos center = null;
		StarterHut hut = null;
		for (int attempt = 0; attempt < SITE_TRIES && center == null; attempt++) {
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, context.origin().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			StarterHut candidate = new StarterHut(spot, door, style.hutHalf, style.hutWallHeight, style.hutWindows);
			if (siteOk(level, candidate, spot)) {
				center = spot;
				hut = candidate;
			}
		}

		if (center == null) {
			return false;
		}

		BlockState wall = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState floor = style.hutFloor.equals("wood") ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
		for (BlockPos cell : hut.floor()) {
			level.setBlock(cell, floor, 2);
		}

		for (BlockPos cell : hut.inside()) {
			level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
		}

		for (int dy = 0; dy <= hut.wallHeight; dy++) {
			for (BlockPos cell : hut.wallLayer(dy)) {
				level.setBlock(cell, style.hutLogCorners && hut.isCornerPost(cell) ? Blocks.OAK_LOG.defaultBlockState() : wall, 2);
			}
		}

		for (BlockPos cell : hut.windowCells()) {
			level.setBlock(cell, Blocks.GLASS.defaultBlockState(), 2);
		}

		for (BlockPos cell : hut.roof()) {
			level.setBlock(cell, wall, 2);
		}

		BlockPos doorPos = hut.doorPos();
		BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, door.getOpposite());
		level.setBlock(doorPos, lower, 2);
		level.setBlock(doorPos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);

		var corners = hut.furnitureSpots();
		BlockPos chest = corners.get(0);
		com.dinglebob.drifter.building.Furnish.chest(level, chest, 2);
		net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		com.dinglebob.drifter.building.Furnish.station(level, corners.get(1), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
		com.dinglebob.drifter.building.Furnish.station(level, corners.get(2), Blocks.FURNACE.defaultBlockState(), 2);
		BlockState foot = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, hut.door.getOpposite());
		BlockPos bedFoot = hut.bedFoot();
		BlockPos bedHead = bedFoot.relative(hut.door.getOpposite());
		if (hut.isInside(bedHead)) {
			level.setBlock(bedFoot, foot, 2);
			level.setBlock(bedHead, foot.setValue(BedBlock.PART, BedPart.HEAD), 2);
		}

		// The farm to one side or behind (never in front of the door), its size like a Drifter's own: 5x5 to 9x9 round water.
		Direction farmSide = switch (random.nextInt(3)) {
			case 0 -> door.getClockWise();
			case 1 -> door.getCounterClockWise();
			default -> door.getOpposite();
		};
		// (World generation can only write a chunk or so out from where it's placing: up to 7x7 here.)
		int farmRadius = 2 + random.nextInt(2);
		BlockPos farmMiddle = center.relative(farmSide, hut.half + 1 + farmRadius);
		boolean farmed = this.farm(level, random, farmMiddle, farmRadius);
		// A torch on the back wall inside.
		BlockPos torch = hut.torchSpot();
		if (level.getBlockState(torch).isAir()) {
			level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, door), 2);
		}

		// A Drifter's build: nobody digs through it for a route or takes its blocks. The trees its wood came from, gone.
		com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(level, hut.box());
		Felling.fell(level, hut.box(), Felling.logsIn(Felling.blocksIn(level, hut.box())), random);
		// Lived in (a setting), if there's room for one more Drifter (the cap; otherwise it's left empty).
		boolean occupied = random.nextDouble() < DrifterConfig.spawnChance(com.dinglebob.drifter.DrifterConfig.Spawn.CAMPS_LIVED_IN)
			&& com.dinglebob.drifter.world.DrifterCensus.roomFor(center, 1);
		if (occupied) {
			this.settle(level, random, center, door, chest, style, farmed ? level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, farmMiddle) : null, farmRadius);
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] world gen: {} camp at {}", occupied ? "a lived-in" : "an abandoned", center.toShortString());

		return true;
	}

	/**
	 * Camps are spread out the way the game spreads villages: the world is cut into square regions the size of the "Space
	 * between camps" setting (112 blocks, 7 chunks, by default; user, 2026-10-17), and only one chunk of each, picked from
	 * the world seed and kept a quarter of a region off its edges, can have one. Two camps are then about that far apart,
	 * and never closer than half of it (2 chunks at the least).
	 */
	static boolean campChunk(WorldGenLevel level, net.minecraft.world.level.ChunkPos chunk) {
		int region = Math.max(3, Math.round(DrifterConfig.spawn(DrifterConfig.Spawn.CAMP_SPACING) / 16.0F));
		int rx = Math.floorDiv(chunk.x, region);
		int rz = Math.floorDiv(chunk.z, region);
		long hash = level.getSeed() ^ (rx * 341873128712L + rz * 132897987541L) ^ 0x5DEECE66DL;
		hash = hash * 6364136223846793005L + 1442695040888963407L;
		int margin = Math.max(1, region / 4);
		int inner = region - 2 * margin;
		int ox = margin + (int)Math.floorMod(hash >>> 33, (long)inner);
		int oz = margin + (int)Math.floorMod(hash >>> 13, (long)inner);
		return chunk.x == rx * region + ox && chunk.z == rz * region + oz;
	}

	// ---------------------------------------------------------------------------------------------
	// A house camp: one of the house plans, built in the wood that grows there, its Drifter living in it
	// ---------------------------------------------------------------------------------------------

	private boolean house(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		java.util.List<com.dinglebob.drifter.building.plan.HousePlan> plans = new java.util.ArrayList<>(
			com.dinglebob.drifter.building.plan.HousePlans.snapshot().values());
		java.util.Collections.shuffle(plans, new java.util.Random(context.random().nextLong()));
		// A few plans in turn: a big one may not fit the ground there (or what world generation can reach) where a small one does.
		for (int i = 0; i < Math.min(4, plans.size()); i++) {
			if (this.house(context, plans.get(i))) {
				return true;
			}
		}

		return false;
	}

	private boolean house(FeaturePlaceContext<NoneFeatureConfiguration> context, com.dinglebob.drifter.building.plan.HousePlan plan) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		var rotation = net.minecraft.world.level.block.Rotation.getRandom(random);
		var palette = com.dinglebob.drifter.building.plan.HousePalette.forWood(plan, woodNear(level, context.origin()));
		var shape = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, BlockPos.ZERO, rotation, palette);
		// Inside what world generation may write: the chunk it's placing in and one chunk round it.
		net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(context.origin());
		int minX = chunk.getMinBlockX() - 15;
		int maxX = chunk.getMinBlockX() + 30 - shape.sizeX;
		int minZ = chunk.getMinBlockZ() - 15;
		int maxZ = chunk.getMinBlockZ() + 30 - shape.sizeZ;
		if (maxX < minX || maxZ < minZ) {
			return false;
		}

		int cx = Math.max(minX, Math.min(maxX, context.origin().getX() - shape.sizeX / 2));
		int cz = Math.max(minZ, Math.min(maxZ, context.origin().getZ() - shape.sizeZ / 2));
		// Fairly flat, dry, clear ground (within 3 blocks up or down).
		java.util.List<Integer> heights = new java.util.ArrayList<>();
		for (int dx = 0; dx < shape.sizeX; dx++) {
			for (int dz = 0; dz < shape.sizeZ; dz++) {
				// The ground under any trees (they're cleared: whoever built it cut them down).
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(cx + dx, 0, cz + dz));
				while (top.getY() > level.getMinBuildHeight() && level.getBlockState(top.below()).is(net.minecraft.tags.BlockTags.LOGS)) {
					top = top.below();
				}

				BlockState ground = level.getBlockState(top.below());
				if (!ground.getFluidState().isEmpty() || !level.getFluidState(top).isEmpty()) {
					return false;
				}

				heights.add(top.getY());
			}
		}

		java.util.Collections.sort(heights);
		if (heights.get(heights.size() - 1) - heights.get(0) > 5) {
			return false;
		}

		int surface = heights.get(heights.size() / 2);
		boolean groundAtBottom = plan.cells.stream().anyMatch(c -> c.pos().getY() == 0 && com.dinglebob.drifter.building.plan.PlacedPlan.isGround(c.state()));
		BlockPos origin = new BlockPos(cx, groundAtBottom ? surface - 1 : surface, cz);
		var placed = new com.dinglebob.drifter.building.plan.PlacedPlan(plan, origin, rotation, palette);
		java.util.Map<BlockPos, BlockState> want = placed.buildMap();
		java.util.Set<BlockPos> ground = new java.util.HashSet<>(placed.ground);
		// Levelled: its space cleared (and a little above), what's under it filled with dirt.
		for (int dx = 0; dx < placed.sizeX; dx++) {
			for (int dz = 0; dz < placed.sizeZ; dz++) {
				int floorY = groundAtBottom ? origin.getY() : origin.getY() - 1;
				for (int y = floorY + 1; y <= origin.getY() + placed.sizeY + 2; y++) {
					level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState(), 2);
				}

				// The rest of any tree over it.
				for (int y = origin.getY() + placed.sizeY + 3; y <= origin.getY() + placed.sizeY + 16; y++) {
					BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
					BlockState state = level.getBlockState(pos);
					if (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}

				for (int y = floorY; y >= floorY - 6; y--) {
					BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
					BlockState at = level.getBlockState(pos);
					if (at.isSolid() && at.getFluidState().isEmpty() && y < floorY) {
						break;
					}

					level.setBlock(pos, y == floorY ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}

		for (BlockPos cell : ground) {
			if (!level.getBlockState(cell).isSolid()) {
				level.setBlock(cell, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			}
		}

		for (var cell : placed.build) {
			BlockState state = cell.state();
			if (state.getBlock() instanceof BedBlock) {
				continue;
			}

			level.setBlock(cell.pos(), state, 2);
			if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)) {
				level.setBlock(cell.pos().above(), state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
					DoubleBlockHalf.UPPER), 2);
			}
		}

		for (BlockPos path : placed.paths) {
			level.setBlock(path, Blocks.DIRT_PATH.defaultBlockState(), 2);
		}

		com.dinglebob.drifter.world.PlayerPlacedBlocks.recordBuiltBox(level, placed.box());
		// The trees its wood came from, gone (user, 2026-10-17).
		Felling.fell(level, placed.box(), Felling.logsIn(placed.build.stream().map(c -> c.state()).toList()), random);
		// The bed, then its chest, crafting table and furnace along the walls (the way a Drifter sets out a room).
		var bed = com.dinglebob.drifter.brain.activity.BuildPlanActivity.bedSpot(level, placed, want);
		if (bed == null) {
			return true;
		}

		BlockState foot = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, bed.facing());
		level.setBlock(bed.foot(), foot, 2);
		level.setBlock(bed.head(), foot.setValue(BedBlock.PART, BedPart.HEAD), 2);
		var room = com.dinglebob.drifter.building.Interior.of(level, bed.head());
		BlockPos chest = null;
		if (room != null) {
			chest = room.spot(com.dinglebob.drifter.building.Interior.Kind.CHEST, java.util.Set.of());
			if (chest != null) {
				com.dinglebob.drifter.building.Furnish.chest(level, chest, 2);
			}

			for (BlockState station : new BlockState[] {Blocks.CRAFTING_TABLE.defaultBlockState(), Blocks.FURNACE.defaultBlockState()}) {
				room = com.dinglebob.drifter.building.Interior.of(level, bed.head());
				BlockPos spot = room == null ? null : room.spot(com.dinglebob.drifter.building.Interior.Kind.STATION, java.util.Set.of());
				if (spot != null) {
					com.dinglebob.drifter.building.Furnish.station(level, spot, station, 2);
				}
			}
		}

		boolean occupied = random.nextDouble() < DrifterConfig.spawnChance(com.dinglebob.drifter.DrifterConfig.Spawn.CAMPS_LIVED_IN)
			&& com.dinglebob.drifter.world.DrifterCensus.roomFor(origin, 1);
		if (!occupied && chest != null) {
			net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		}

		if (occupied) {
			BlockPos stand = null;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (BlockPos part : new BlockPos[] {bed.foot(), bed.head()}) {
					BlockPos by = part.relative(side);
					if (stand == null && level.getBlockState(by).isAir() && level.getBlockState(by.above()).isAir() && level.getBlockState(by.below()).isSolid()) {
						stand = by;
					}
				}
			}

			if (stand != null) {
				Level server = level.getLevel();
				DrifterEntity drifter = ModEntities.DRIFTER.create(server);
				if (drifter != null) {
					drifter.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
					drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), MobSpawnType.STRUCTURE, null);
					// It lives in the house as one living in a village house does (its own bed, its chests along the walls).
					Home home = new Home(stand, server.dimension().location(), 0L);
					home.houseBed = bed.head();
					if (chest != null) {
						home.addChest(chest);
					}

					drifter.getDrifterBrain().setHome(home);
					drifter.addPendingCatchUp(LivedInStructures.livedTicks(random));
					level.addFreshEntity(drifter);
				}
			}
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] world gen: {} house camp ({}) at {}", occupied ? "a lived-in" : "an abandoned", plan.id,
			origin.toShortString());
		return true;
	}

	/** The wood that grows here: the commonest log near the surface round it (oak if none). */
	private static String woodNear(WorldGenLevel level, BlockPos origin) {
		java.util.Map<String, Integer> count = new java.util.HashMap<>();
		for (int dx = -12; dx <= 12; dx += 2) {
			for (int dz = -12; dz <= 12; dz += 2) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.offset(dx, 0, dz));
				for (int dy = 0; dy < 8; dy++) {
					BlockState state = level.getBlockState(top.below(dy));
					if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
						String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
						for (String wood : com.dinglebob.drifter.building.plan.HousePalette.WOODS) {
							if (path.startsWith(wood + "_")) {
								count.merge(wood, 1, Integer::sum);
							}
						}
					}
				}
			}
		}

		return count.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse("oak");
	}

	/** Flat (within a block), dry, open ground with no trees or buildings where the hut and its farm go. */
	private static boolean siteOk(WorldGenLevel level, StarterHut hut, BlockPos center) {
		for (int dx = -hut.half; dx <= hut.half; dx++) {
			for (int dz = -hut.half; dz <= hut.half; dz++) {
				BlockPos column = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, center.offset(dx, 0, dz));
				if (Math.abs(column.getY() - center.getY()) > 2) {
					return false;
				}

				BlockState ground = level.getBlockState(column.below());
				if (!ground.getFluidState().isEmpty() || ground.is(net.minecraft.tags.BlockTags.LEAVES) || ground.is(net.minecraft.tags.BlockTags.LOGS)
					|| !ground.isSolid()) {
					return false;
				}
			}
		}

		for (BlockPos cell : hut.inside()) {
			BlockState state = level.getBlockState(cell);
			if (state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * A wheat patch round a water block (the radius its Drifter keeps it at), on the ground cells there that take it (a
	 * patch on uneven ground is patchy, as a real one is). False if the middle isn't ground.
	 */
	private boolean farm(WorldGenLevel level, RandomSource random, BlockPos middle, int radius) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, middle).below();
		BlockState centre = level.getBlockState(ground);
		if (!centre.is(Blocks.GRASS_BLOCK) && !centre.is(Blocks.DIRT)) {
			return false;
		}

		level.setBlock(ground, Blocks.WATER.defaultBlockState(), 2);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos cell = ground.offset(dx, 0, dz);
				BlockState state = level.getBlockState(cell);
				if ((dx != 0 || dz != 0) && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) && level.getBlockState(cell.above()).canBeReplaced()) {
					level.setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2);
					level.setBlock(cell, Blocks.FARMLAND.defaultBlockState(), 2);
					level.setBlock(cell.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(8)), 2);
				}
			}
		}

		return true;
	}

	/** Its Drifter, at home: it catches up on a few days of its life there when it first loads (section 22). */
	private void settle(WorldGenLevel level, RandomSource random, BlockPos center, Direction door, BlockPos chest, Home style,
		@org.jetbrains.annotations.Nullable BlockPos farm, int farmRadius) {
		Level server = level.getLevel();
		DrifterEntity drifter = ModEntities.DRIFTER.create(server);
		if (drifter == null) {
			return;
		}

		drifter.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(center), MobSpawnType.STRUCTURE, null);
		Home home = new Home(center, server.dimension().location(), 0L);
		home.hutDoor = door;
		home.hutBuilt = true;
		home.hutHalf = style.hutHalf;
		home.hutWallHeight = style.hutWallHeight;
		home.hutWindows = style.hutWindows;
		home.hutLogCorners = style.hutLogCorners;
		home.hutFloor = style.hutFloor;
		if (farm != null) {
			home.farm = farm;
			home.farmRadius = farmRadius;
		}
		home.addChest(chest);
		drifter.getDrifterBrain().setHome(home);
		drifter.addPendingCatchUp(LivedInStructures.livedTicks(random));
		level.addFreshEntity(drifter);
	}
}
