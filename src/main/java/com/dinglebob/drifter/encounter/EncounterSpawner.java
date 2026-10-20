package com.dinglebob.drifter.encounter;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.personality.Archetype;
import com.dinglebob.drifter.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where an encounter's Drifters come from: open ground some way off that no player can see (nobody pops into
 * existence in view; they walk in), on loaded land only. And what each carries: its own kit, as a Drifter's starting
 * kit is (DESIGN.md section 1: everything it has came from somewhere), fitted to its part and to how well armed the
 * player is.
 */
final class EncounterSpawner {
	/** Spots tried before giving up. */
	private static final int TRIES = 40;
	/** No spot this high or low against where it's looked for (not on a mountain top, not down a ravine). */
	private static final int MAX_RISE = 16;

	private EncounterSpawner() {
	}

	/**
	 * A spot on open ground {@code min} to {@code max} blocks from {@code center} (on {@code toward}'s side if given) that
	 * no player can see, or null.
	 */
	@Nullable
	static BlockPos hiddenSpot(ServerLevel level, Vec3 center, double min, double max, @Nullable Vec3 toward, RandomSource random) {
		int noGround = 0;
		int height = 0;
		int seen = 0;
		for (int i = 0; i < TRIES; i++) {
			double angle = toward != null && i < TRIES / 2 ? Math.atan2(toward.z, toward.x) + (random.nextDouble() - 0.5) * 1.2
				: random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			int x = (int)Math.floor(center.x + Math.cos(angle) * distance);
			int z = (int)Math.floor(center.z + Math.sin(angle) * distance);
			BlockPos spot = ground(level, x, z);
			if (spot == null) {
				noGround++;
			} else if (Math.abs(spot.getY() - center.y) > MAX_RISE) {
				height++;
			} else if (!hidden(level, spot)) {
				seen++;
			} else {
				return spot;
			}
		}

		com.dinglebob.drifter.Drifter.LOGGER.info("[Drifter] no hidden spot for an encounter near {}: {} not open ground, {} too high or low, {} in view",
			BlockPos.containing(center).toShortString(), noGround, height, seen);
		return null;
	}

	/** Standing room on natural open ground in this column (not water, not a roof, not leaves), on loaded land; or null. */
	@Nullable
	static BlockPos ground(ServerLevel level, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}

		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		var below = level.getBlockState(top.below());
		if (!below.getFluidState().isEmpty() || below.isAir() || com.dinglebob.drifter.nav.NavView.looksBuilt(below)
			|| below.is(net.minecraft.tags.BlockTags.LOGS) || !com.dinglebob.drifter.brain.KnownBlocks.isStandable(level, top)) {
			return null;
		}

		return top;
	}

	/** As {@link #ground}, and bare: nothing growing in the cell (a block put there goes straight onto the ground). */
	@Nullable
	static BlockPos bareGround(ServerLevel level, int x, int z) {
		BlockPos top = ground(level, x, z);
		return top != null && level.getBlockState(top).isAir() ? top : null;
	}

	/** No player could see someone standing here (out of range, or something solid in between). */
	static boolean hidden(ServerLevel level, BlockPos spot) {
		Vec3 head = Vec3.atBottomCenterOf(spot).add(0.0, 1.6, 0.0);
		Vec3 feet = Vec3.atBottomCenterOf(spot).add(0.0, 0.3, 0.0);
		for (Player player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(head) > 128.0 * 128.0) {
				continue;
			}

			Vec3 eye = player.getEyePosition();
			for (Vec3 point : new Vec3[] {head, feet}) {
				if (level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
					return false;
				}
			}
		}

		return true;
	}

	/** A new Drifter here, of this archetype, set up by {@code setup} (its kit, its traits) before it walks in; or null. */
	@Nullable
	static DrifterEntity spawn(ServerLevel level, BlockPos at, @Nullable Archetype archetype, Consumer<DrifterEntity> setup) {
		DrifterEntity drifter = ModEntities.DRIFTER.create(level);
		if (drifter == null) {
			return null;
		}

		float yaw = level.getRandom().nextFloat() * 360.0F;
		drifter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		drifter.setYHeadRot(yaw);
		drifter.setYBodyRot(yaw);
		drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		if (archetype != null) {
			drifter.getPersonality().reroll(level.getRandom(), archetype);
		}

		setup.accept(drifter);
		return drifter;
	}

	/** Puts it in the world (after it has joined its encounter). */
	static boolean add(ServerLevel level, DrifterEntity drifter) {
		return level.addFreshEntity(drifter);
	}

	// ---------------------------------------------------------------------------------------------
	// Kits
	// ---------------------------------------------------------------------------------------------

	/** How well armed a player looks: 0 little or nothing, 1 about iron, 2 diamond or better (from their armor). */
	static int gearTier(ServerPlayer player) {
		int armor = player.getArmorValue();
		return armor >= 16 ? 2 : armor >= 8 ? 1 : 0;
	}

	/** A fighter's kit for this tier: armor, a weapon, a shield (an archer a bow and arrows), food. */
	static void fighter(DrifterEntity drifter, int tier, boolean archer, RandomSource random) {
		var inventory = drifter.getInventory();
		switch (tier) {
			case 0 -> {
				drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
				inventory.add(new ItemStack(random.nextInt(3) == 0 ? Items.STONE_AXE : Items.STONE_SWORD));
			}
			case 1 -> {
				drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
				drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				drifter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
				drifter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				inventory.add(new ItemStack(random.nextInt(3) == 0 ? Items.IRON_AXE : Items.IRON_SWORD));
			}
			default -> {
				drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				drifter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				drifter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				drifter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				inventory.add(new ItemStack(random.nextInt(4) == 0 ? Items.DIAMOND_AXE : Items.DIAMOND_SWORD));
			}
		}

		if (archer) {
			inventory.add(new ItemStack(Items.BOW));
			inventory.add(new ItemStack(Items.ARROW, 24 + random.nextInt(16)));
		}

		inventory.add(new ItemStack(Items.BREAD, 4 + random.nextInt(5)));
		inventory.add(new ItemStack(Items.COBBLESTONE, 16));
	}

	/** Its traits for a part: bravery, aggression, caution, vengefulness (each 0 to 1). */
	static void traits(DrifterEntity drifter, double bravery, double aggression, double caution, double vengefulness) {
		var p = drifter.getPersonality();
		p.set(com.dinglebob.drifter.personality.Trait.BRAVERY, bravery);
		p.set(com.dinglebob.drifter.personality.Trait.AGGRESSION, aggression);
		p.set(com.dinglebob.drifter.personality.Trait.CAUTION, caution);
		p.set(com.dinglebob.drifter.personality.Trait.VENGEFULNESS, vengefulness);
	}

	/** Something worth having for a thank-you (a traveller's): a few emeralds or gold, now and then a golden apple. */
	static ItemStack valuable(RandomSource random) {
		return switch (random.nextInt(5)) {
			case 0, 1 -> new ItemStack(Items.EMERALD, 3 + random.nextInt(5));
			case 2 -> new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4));
			case 3 -> new ItemStack(Items.GOLDEN_APPLE);
			default -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(2));
		};
	}
}
