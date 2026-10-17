package com.dinglebob.drifter.world.gen;

import com.mojang.serialization.Codec;

import com.dinglebob.drifter.DrifterConfig;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A Drifter out in the world with no home (user's request, 2026-10-17: "run into a drifter mid setting off with just
 * first day armor and weapons, no hut"; "spawned without nothing, but not too much"; "already full armor just passing
 * through, and then you never see the drifter again"). One of three:
 * <ul>
 *   <li>fresh: nothing at all, its first minutes;</li>
 *   <li>setting off: a few hours to most of a day lived (its catch-up), first tools, no home yet;</li>
 *   <li>passing through: full iron and a sword, heading somewhere far; once you've met it and it's out of sight, it's
 *       gone for good.</li>
 * </ul>
 */
public class WandererFeature extends Feature<NoneFeatureConfiguration> {
	private static final double FRESH = 0.35;
	private static final double SETTING_OFF = 0.85;

	public WandererFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		if (!DrifterConfig.worldgenWanderers()) {
			return false;
		}

		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, context.origin());
		BlockState ground = level.getBlockState(feet.below());
		if (!ground.isSolid() || !ground.getFluidState().isEmpty() || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
			return false;
		}

		double roll = random.nextDouble();
		String kind = spawn(level, feet, random, roll < FRESH ? 0 : roll < SETTING_OFF ? 1 : 2);
		if (kind != null) {
			com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] world gen: {} wanderer at {}", kind, feet.toShortString());
		}

		return kind != null;
	}

	/** A wanderer of this kind (0 fresh, 1 setting off, 2 passing through) here; what it was, or null. */
	@org.jetbrains.annotations.Nullable
	public static String spawn(net.minecraft.world.level.ServerLevelAccessor level, BlockPos feet, RandomSource random, int which) {
		DrifterEntity drifter = ModEntities.DRIFTER.create(level.getLevel());
		if (drifter == null) {
			return null;
		}

		drifter.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.NATURAL, null);
		String kind;
		if (which == 0) {
			kind = "a fresh";
		} else if (which == 1) {
			// Most of a day at most (it settles after a full one), so it's still on the move.
			drifter.addPendingCatchUp(4000L + random.nextInt(16001));
			kind = "a day-one";
		} else {
			// Its kit is its own from long before this world's day one (a starting kit, as the design allows).
			drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			drifter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			drifter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			drifter.getInventory().add(new ItemStack(Items.IRON_SWORD));
			drifter.getInventory().add(new ItemStack(Items.SHIELD));
			drifter.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
			drifter.getInventory().add(new ItemStack(Items.BREAD, 16));
			drifter.getInventory().add(new ItemStack(Items.TORCH, 16));
			drifter.getInventory().add(new ItemStack(Items.COBBLESTONE, 32));
			drifter.startPassingThrough(random.nextFloat() * 360.0F, level.getLevel().getGameTime());
			kind = "a passing";
		}

		level.addFreshEntity(drifter);
		return kind;
	}
}
