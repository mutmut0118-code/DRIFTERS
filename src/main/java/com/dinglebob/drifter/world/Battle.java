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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop());
		// Arrows that hit their own side, counted for the report (archers aren't to shoot through their friends).
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!sides.isEmpty() && entity instanceof DrifterEntity hurt && source.getEntity() instanceof DrifterEntity shooter && shooter != hurt
				&& source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) && allies(hurt, shooter)) {
				friendlyArrows++;
				var activity = shooter.getDrifterBrain().activity();
				com.dinglebob.drifter.Drifter.LOGGER.info("[battle] friendly arrow: {} hit {} {} blocks off ({}; {})", shooter.getName().getString(), hurt.getName().getString(),
					String.format("%.1f", shooter.distanceTo(hurt)), activity == null ? "-" : activity.describe(), activity == null ? "-" : activity.stepName());
			}
		});
	}

	/** Arrows into their own side since the battle began. */
	private static int friendlyArrows;

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

	/** The archers (the back ranks), kept with the sides. */
	private static final java.util.Set<UUID> archers = new java.util.HashSet<>();

	/** One of the archers at the back of its side. */
	public static boolean archer(DrifterEntity drifter) {
		return archers.contains(drifter.getUUID()) && side(drifter) != null;
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
		int onRoofs = 0;
		int still = 0;
		int drawing = 0;
		long now = server.getTickCount();
		// What the ones that hardly moved since the last report are at ("kill X / attack the X", counted).
		Map<String, Integer> stillDoing = new java.util.TreeMap<>();
		Map<UUID, Vec3> seenAt = new HashMap<>();
		boolean compare = now - reportedAt >= 20 * 3;
		for (ServerLevel level : server.getAllLevels()) {
			for (DrifterEntity drifter : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(DrifterEntity.class), DrifterEntity::isAlive)) {
				Integer side = side(drifter);
				if (side != null) {
					standing[side]++;
					seenAt.put(drifter.getUUID(), drifter.position());
					if (drifter.isUsingItem() && drifter.getUseItem().is(Items.BOW)) {
						drawing++;
					}

					Vec3 before = reportedPositions.get(drifter.getUUID());
					if (compare && before != null && before.distanceTo(drifter.position()) < 1.5) {
						still++;
						var activity = drifter.getDrifterBrain().activity();
						String doing = activity == null ? "nothing" : activity.describe().split(" ")[0] + " / " + activity.stepName().replaceAll(" (the|a|an) .*", "");
						stillDoing.merge(doing, 1, Integer::sum);
					}
					// On a roof: standing on a building block with nothing over it.
					if (drifter.onGround() && com.dinglebob.drifter.nav.NavView.looksBuilt(level.getBlockState(drifter.blockPosition().below()))
						&& level.canSeeSky(drifter.blockPosition())) {
						onRoofs++;
					}
				}
			}
		}

		String report = "side A " + standing[0] + " standing, side B " + standing[1] + "; " + onRoofs + " on roofs; " + drawing + " drawing a bow; " + friendlyArrows + " arrows into friends";
		if (compare && !reportedPositions.isEmpty()) {
			report += "; " + still + " hardly moved in " + (now - reportedAt) / 20 + " s" + (stillDoing.isEmpty() ? "" : " " + stillDoing);
		}

		if (compare || reportedPositions.isEmpty()) {
			reportedPositions.clear();
			reportedPositions.putAll(seenAt);
			reportedAt = now;
		}

		return report;
	}

	/** Where each soldier stood at the last report (to tell who hardly moved since). */
	private static final Map<UUID, Vec3> reportedPositions = new HashMap<>();
	private static long reportedAt = Long.MIN_VALUE / 2;

	public static int stop() {
		int count = sides.size();
		sides.clear();
		archers.clear();
		friendlyArrows = 0;
		reportedPositions.clear();
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
				BlockPos top = ground(level, (int)Math.floor(x), (int)Math.floor(z));
				x = top.getX() + 0.5 + random.nextDouble() * 0.4 - 0.2;
				z = top.getZ() + 0.5 + random.nextDouble() * 0.4 - 0.2;
				DrifterEntity drifter = ModEntities.DRIFTER.create(level);
				if (drifter == null) {
					continue;
				}

				float yaw = side == 0 ? -90.0F : 90.0F;
				drifter.moveTo(x, top.getY(), z, yaw, 0.0F);
				drifter.setYHeadRot(yaw);
				drifter.setYBodyRot(yaw);
				drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.COMMAND, null);
				// The back ranks are the archers (as an army draws up), a quarter of each side.
				boolean archer = i >= perSide - perSide / 4;
				kit(drifter, random, archer);
				sides.put(drifter.getUUID(), side);
				if (archer) {
					archers.add(drifter.getUUID());
				}
				if (level.addFreshEntity(drifter)) {
					spawned++;
				} else {
					sides.remove(drifter.getUUID());
					archers.remove(drifter.getUUID());
				}
			}
		}

		return spawned;
	}

	/**
	 * Where to stand near this column: on natural ground, not on a roof or in a tree (found in a 50v50 in a village: the
	 * ranks fell on houses and a dozen soldiers began the battle on the roofs). The nearest open column within a few blocks.
	 */
	private static BlockPos ground(ServerLevel level, int x, int z) {
		BlockPos first = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		for (int r = 0; r <= 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}

					BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x + dx, 0, z + dz));
					var below = level.getBlockState(top.below());
					if (!com.dinglebob.drifter.nav.NavView.looksBuilt(below) && !below.is(net.minecraft.tags.BlockTags.LEAVES)
						&& !below.is(net.minecraft.tags.BlockTags.LOGS) && below.getFluidState().isEmpty() && !below.isAir()) {
						return top;
					}
				}
			}
		}

		return first;
	}

	/**
	 * A soldier's kit: iron armour, its favourite kind of weapon at iron (a sword or an axe without one), a shield unless
	 * that weapon is two-handed, and a bow with a few arrows for when it can't get through to the enemy; an archer has a
	 * quiverful.
	 */
	private static void kit(DrifterEntity drifter, net.minecraft.util.RandomSource random, boolean archer) {
		drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		drifter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		drifter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		// Its favourite kind of weapon at iron (with Simply Swords: longswords, katanas, halberds...; a two-handed one means no
		// shield), else an iron sword or axe.
		var family = com.dinglebob.drifter.brain.Weapons.favorite(drifter);
		net.minecraft.world.item.Item weapon = family.at(com.dinglebob.drifter.brain.Weapons.Tier.IRON);
		if (weapon == null) {
			weapon = random.nextInt(4) == 0 ? Items.IRON_AXE : Items.IRON_SWORD;
		}

		if (!family.twoHanded() || weapon == Items.IRON_SWORD || weapon == Items.IRON_AXE) {
			drifter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}

		var inventory = drifter.getInventory();
		inventory.add(new ItemStack(weapon));
		inventory.add(new ItemStack(Items.BOW));
		inventory.add(new ItemStack(Items.ARROW, archer ? 64 : 16));

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
			if (side == null || lines[1 - side] == null) {
				return 0.0;
			}

			// Where the enemy army stood, or (got there and nobody in sight) toward the fighting it hears.
			return context.drifter().position().distanceTo(lines[1 - side]) >= 6.0 || fighting(context.drifter()) != null ? 0.85 : 0.0;
		}

		/** The newest sound of fighting it heard (a hit, an arrow) in the last few seconds, farther than a few blocks; or null. */
		@Nullable
		static Vec3 fighting(DrifterEntity drifter) {
			long now = drifter.level().getGameTime();
			Vec3 best = null;
			long bestTime = Long.MIN_VALUE;
			for (var sound : drifter.getSenses().recentSounds()) {
				if ((sound.kind() == com.dinglebob.drifter.perception.HeardSound.Kind.HURT || sound.kind() == com.dinglebob.drifter.perception.HeardSound.Kind.PROJECTILE)
					&& now - sound.time() < 20 * 8 && sound.time() > bestTime && sound.roughPos().distanceTo(drifter.position()) > 4.0) {
					best = sound.roughPos();
					bestTime = sound.time();
				}
			}

			return best;
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
			// At their old line already: to the sound of the fighting (as soldiers march to the guns).
			Vec3 heard = context.drifter().position().distanceTo(enemy) < 6.0 ? fighting(context.drifter()) : null;
			return new com.dinglebob.drifter.brain.activity.ExploreActivity(context.drifter(), heard != null ? "toward the fighting" : "marching on the enemy",
				heard != null ? heard : enemy, drifter -> com.dinglebob.drifter.social.BanditGoals.preyVisible(drifter));
		}
	}
}
