package com.dinglebob.drifter.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Goals;
import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A pitched battle between two armies of Drifters, for testing (milestone 16, the user's "500v500 (or 150v150) war and it
 * runs very smoothly with each ai working properly"; `/drifter battle <per side>`). Each side is armed alike (iron armor,
 * a sword and shield, a bow for some, food) and lined up facing the other. Soldiers kill the other side on sight, as a
 * Bandit does, never their own; out of sight of the enemy they march on where the enemy army stood. Its own groups and
 * feuds aren't touched: groups are four at most and a battle is far bigger. Not saved (a test): a battle ends with the
 * world, or with `/drifter battle stop`.
 */
public final class Battle {
	private Battle() {
	}

	/** Side (0 or 1) by soldier. */
	private static final Map<UUID, Integer> sides = new HashMap<>();
	/** Where each side's army stood: the other side marches on it. */
	private static final Vec3[] lines = new Vec3[2];

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> sides.clear());
	}

	/** Its side in the battle, or null if it's not in one. */
	@Nullable
	public static Integer side(DrifterEntity drifter) {
		return sides.isEmpty() ? null : sides.get(drifter.getUUID());
	}

	public static boolean inBattle(DrifterEntity drifter) {
		return side(drifter) != null;
	}

	/** Both soldiers, on opposite sides. */
	public static boolean enemies(DrifterEntity one, DrifterEntity other) {
		Integer a = side(one);
		Integer b = a == null ? null : side(other);
		return a != null && b != null && !a.equals(b);
	}

	/** Both soldiers, on the same side. */
	public static boolean allies(DrifterEntity one, DrifterEntity other) {
		Integer a = side(one);
		return a != null && a.equals(side(other));
	}

	/** "Side A: n standing, side B: m" (for `/drifter perf`), or null with no battle on. */
	@Nullable
	public static String report(net.minecraft.server.MinecraftServer server) {
		if (sides.isEmpty()) {
			return null;
		}

		int[] standing = new int[2];
		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				Integer side = side(drifter);
				if (side != null) {
					standing[side]++;
				}
			}
		}

		return "side A " + standing[0] + " standing, side B " + standing[1];
	}

	public static int stop() {
		int count = sides.size();
		sides.clear();
		return count;
	}

	/**
	 * Two armies of {@code perSide} round {@code center}, {@code gap} blocks apart along x, in ranks; returns how many
	 * stood up.
	 */
	public static int start(ServerLevel level, Vec3 center, int perSide, int gap) {
		var random = level.getRandom();
		int spawned = 0;
		int across = Math.max(1, (int)Math.ceil(Math.sqrt(perSide * 2.0)));
		for (int side = 0; side < 2; side++) {
			double lineX = center.x + (side == 0 ? -gap / 2.0 : gap / 2.0);
			lines[side] = new Vec3(lineX, center.y, center.z);
			for (int i = 0; i < perSide; i++) {
				// Ranks two blocks apart, deeper away from the enemy.
				int rank = i / across;
				int file = i % across;
				double x = lineX + (side == 0 ? -rank * 2.0 : rank * 2.0) + random.nextDouble() - 0.5;
				double z = center.z + (file - across / 2.0) * 2.0 + random.nextDouble() - 0.5;
				level.getChunk(((int)Math.floor(x)) >> 4, ((int)Math.floor(z)) >> 4);
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
				DrifterEntity drifter = ModEntities.DRIFTER.create(level);
				if (drifter == null) {
					continue;
				}

				float yaw = side == 0 ? -90.0F : 90.0F;
				drifter.moveTo(x, top.getY(), z, yaw, 0.0F);
				drifter.setYHeadRot(yaw);
				drifter.setYBodyRot(yaw);
				drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.COMMAND, null);
				kit(drifter, random);
				sides.put(drifter.getUUID(), side);
				if (level.addFreshEntity(drifter)) {
					spawned++;
				} else {
					sides.remove(drifter.getUUID());
				}
			}
		}

		return spawned;
	}

	private static void kit(DrifterEntity drifter, net.minecraft.util.RandomSource random) {
		drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		drifter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		drifter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		drifter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		var inventory = drifter.getInventory();
		inventory.add(new ItemStack(random.nextInt(4) == 0 ? Items.IRON_AXE : Items.IRON_SWORD));
		if (random.nextInt(4) == 0) {
			inventory.add(new ItemStack(Items.BOW));
			inventory.add(new ItemStack(Items.ARROW, 32));
		}

		inventory.add(new ItemStack(Items.COOKED_BEEF, 16));
		inventory.add(new ItemStack(Items.IRON_PICKAXE));
		inventory.add(new ItemStack(Items.COBBLESTONE, 32));
	}

	/** Its side's goals: marching on the enemy when none is in sight (fighting them is the kill-on-sight goal). */
	public static List<Goals.Goal> goals() {
		return List.of(new Advance());
	}

	/** Out of sight of the enemy: march on where their army stood (the fighting itself is Bandit kill-on-sight). */
	public static final class Advance extends Goals.Goal {
		@Override
		public String id() {
			return "battle_advance";
		}

		@Override
		public String name(Goals.Context context) {
			return "march on the enemy";
		}

		@Override
		public double urgency(Goals.Context context) {
			Integer side = side(context.drifter());
			if (side == null || lines[1 - side] == null || context.drifter().position().distanceTo(lines[1 - side]) < 6.0) {
				return 0.0;
			}

			return 0.85;
		}

		@Override
		public double feasibility(Goals.Context context) {
			return 1.0;
		}

		@Override
		public int minCommitTicks() {
			return 0;
		}

		@Override
		public Activity start(Goals.Context context) {
			Integer side = side(context.drifter());
			Vec3 enemy = lines[1 - (side == null ? 0 : side)];
			return new com.dinglebob.drifter.brain.activity.ExploreActivity(context.drifter(), "marching on the enemy", enemy,
				drifter -> com.dinglebob.drifter.social.BanditGoals.preyVisible(drifter));
		}
	}
}
