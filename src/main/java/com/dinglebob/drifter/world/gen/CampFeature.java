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
		// A few spots round where it was placed: the first with room for the camp (on hilly land, flat spots are scarce).
		BlockPos center = null;
		StarterHut hut = null;
		for (int attempt = 0; attempt < SITE_TRIES && center == null; attempt++) {
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, context.origin().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			StarterHut candidate = new StarterHut(spot, door);
			if (siteOk(level, candidate, spot)) {
				center = spot;
				hut = candidate;
			}
		}

		if (center == null) {
			return false;
		}

		BlockState wall = Blocks.OAK_PLANKS.defaultBlockState();
		for (BlockPos cell : hut.floor()) {
			level.setBlock(cell, Blocks.COBBLESTONE.defaultBlockState(), 2);
		}

		for (BlockPos cell : hut.inside()) {
			level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
		}

		for (int dy = 0; dy <= hut.wallHeight; dy++) {
			for (BlockPos cell : hut.wallLayer(dy)) {
				level.setBlock(cell, wall, 2);
			}
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

		this.farm(level, random, doorPos.relative(door, 3));
		boolean occupied = random.nextDouble() < OCCUPIED_CHANCE;
		if (occupied) {
			this.settle(level, random, center, door, chest);
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] world gen: {} camp at {}", occupied ? "a lived-in" : "an abandoned", center.toShortString());

		return true;
	}

	/** Flat (within a block), dry, open ground with no trees or buildings where the hut and its farm go. */
	private static boolean siteOk(WorldGenLevel level, StarterHut hut, BlockPos center) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
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

	/** A 3x3 wheat patch round a water block, if the ground there takes it. */
	private void farm(WorldGenLevel level, RandomSource random, BlockPos middle) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, middle).below();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos cell = ground.offset(dx, 0, dz);
				BlockState state = level.getBlockState(cell);
				if (!state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.DIRT)) {
					return;
				}
			}
		}

		level.setBlock(ground, Blocks.WATER.defaultBlockState(), 2);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					BlockPos cell = ground.offset(dx, 0, dz);
					level.setBlock(cell, Blocks.FARMLAND.defaultBlockState(), 2);
					level.setBlock(cell.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(8)), 2);
				}
			}
		}
	}

	/** Its Drifter, at home: it catches up on a few days of its life there when it first loads (section 22). */
	private void settle(WorldGenLevel level, RandomSource random, BlockPos center, Direction door, BlockPos chest) {
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
		home.addChest(chest);
		drifter.getDrifterBrain().setHome(home);
		drifter.addPendingCatchUp(24000L * (MIN_DAYS + random.nextInt(MAX_DAYS - MIN_DAYS + 1)));
		level.addFreshEntity(drifter);
	}
}
