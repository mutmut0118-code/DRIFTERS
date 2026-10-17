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
	private static final double OCCUPIED_CHANCE = 0.5;
	private static final int SITE_TRIES = 6;
	private static final int MIN_DAYS = 3;
	private static final int MAX_DAYS = 6;
	private static final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
		ResourceLocation.withDefaultNamespace("chests/village/village_plains_house"));

	public CampFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		if (!DrifterConfig.worldgenCamps()) {
			return false;
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
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
		net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		level.setBlock(corners.get(1), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
		level.setBlock(corners.get(2), Blocks.FURNACE.defaultBlockState(), 2);
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

		boolean occupied = random.nextDouble() < OCCUPIED_CHANCE;
		if (occupied) {
			this.settle(level, random, center, door, chest, style, farmed ? level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, farmMiddle) : null, farmRadius);
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] world gen: {} camp at {}", occupied ? "a lived-in" : "an abandoned", center.toShortString());

		return true;
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
		drifter.addPendingCatchUp(24000L * (MIN_DAYS + random.nextInt(MAX_DAYS - MIN_DAYS + 1)));
		level.addFreshEntity(drifter);
	}
}
