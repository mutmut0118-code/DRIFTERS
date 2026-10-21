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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A pitched battle between two armies of Drifters (milestone 16, the user's "500v500 (or 150v150) war and it runs very
 * smoothly with each ai working properly"; `/drifter battle <per side>`; and the warband encounter). Each army has its
 * colours and a standard of kit, each soldier's pieces its own (SoldierKit: the user, 2026-10-20, "more variety into the
 * gear"), a captain with the banner, archers at the back; lined up facing the other. Several can be on at once. Soldiers kill the other side on sight, as a
 * Bandit does, never their own; out of sight of the enemy they march on where the enemy army stood. Its own groups and
 * feuds aren't touched: groups are four at most and a battle is far bigger. Not saved (a test): a battle ends with the
 * world, or with `/drifter battle stop`.
 */
public final class Battle {
	private Battle() {
	}

	/** A soldier's battle, its side (0 or 1), and whether it's one of the archers at the back. */
	private record Soldier(int battle, int side, boolean archer) {
	}

	private static final Map<UUID, Soldier> soldiers = new HashMap<>();
	/** Where each battle's two armies stood (the other side marches on it), by battle. */
	private static final Map<Integer, Vec3[]> lines = new HashMap<>();
	private static int nextBattle = 1;

	/** A battle begun: its number, and its soldiers (not yet in the world when a caller adds them itself). */
	public record Started(int id, List<DrifterEntity> soldiers) {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop());
		// Arrows that hit their own side, counted for the report (archers aren't to shoot through their friends).
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!soldiers.isEmpty() && entity instanceof DrifterEntity hurt && source.getEntity() instanceof DrifterEntity shooter && shooter != hurt
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

	/** Its side in its battle, or null if it's not in one. */
	@Nullable
	public static Integer side(DrifterEntity drifter) {
		Soldier soldier = soldiers.isEmpty() ? null : soldiers.get(drifter.getUUID());
		return soldier == null ? null : soldier.side();
	}

	@Nullable
	private static Soldier soldier(DrifterEntity drifter) {
		return soldiers.isEmpty() ? null : soldiers.get(drifter.getUUID());
	}

	public static boolean inBattle(DrifterEntity drifter) {
		return side(drifter) != null;
	}

	/** Both soldiers in one battle, on opposite sides. */
	public static boolean enemies(DrifterEntity one, DrifterEntity other) {
		Soldier a = soldier(one);
		Soldier b = a == null ? null : soldier(other);
		return a != null && b != null && a.battle() == b.battle() && a.side() != b.side();
	}

	/** One of the archers at the back of its side. */
	public static boolean archer(DrifterEntity drifter) {
		Soldier soldier = soldier(drifter);
		return soldier != null && soldier.archer();
	}

	/** Both soldiers in one battle, on the same side. */
	public static boolean allies(DrifterEntity one, DrifterEntity other) {
		Soldier a = soldier(one);
		Soldier b = a == null ? null : soldier(other);
		return a != null && b != null && a.battle() == b.battle() && a.side() == b.side();
	}

	/** "Side A: n standing, side B: m" (for `/drifter perf`), or null with no battle on. */
	@Nullable
	public static String report(net.minecraft.server.MinecraftServer server) {
		if (soldiers.isEmpty()) {
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
					if (drifter.isUsingItem() && com.dinglebob.drifter.hands.Shields.isBow(drifter.getUseItem())) {
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

	/** Every battle over (the command): how many soldiers stood down. */
	public static int stop() {
		int count = soldiers.size();
		soldiers.clear();
		lines.clear();
		friendlyArrows = 0;
		reportedPositions.clear();
		return count;
	}

	/** This battle over (an encounter's): its soldiers are soldiers no more. */
	public static void stop(int battle) {
		soldiers.values().removeIf(soldier -> soldier.battle() == battle);
		lines.remove(battle);
	}

	/**
	 * Two armies of {@code perSide} round {@code center}, {@code gap} blocks apart along x, in ranks; returns how many
	 * stood up.
	 */
	public static int start(ServerLevel level, Vec3 center, int perSide, int gap) {
		var random = level.getRandom();
		var quality = SoldierKit.Quality.values()[random.nextInt(SoldierKit.Quality.values().length)];
		return start(level, center, perSide, gap, SoldierKit.opposed(level, quality, random), null).soldiers().size();
	}

	/**
	 * Two armies of {@code perSide} in these colours and kit; {@code beforeAdd} (side) is done to each before it's put in
	 * the world (an encounter taking them on), or null for plain soldiers.
	 */
	public static Started start(ServerLevel level, Vec3 center, int perSide, int gap, SoldierKit.Army[] armies,
		@Nullable java.util.function.BiConsumer<DrifterEntity, Integer> beforeAdd) {
		var random = level.getRandom();
		int battle = nextBattle++;
		List<DrifterEntity> spawned = new java.util.ArrayList<>();
		Vec3[] armyLines = new Vec3[2];
		lines.put(battle, armyLines);
		int across = Math.max(1, (int)Math.ceil(Math.sqrt(perSide * 2.0)));
		for (int side = 0; side < 2; side++) {
			double lineX = center.x + (side == 0 ? -gap / 2.0 : gap / 2.0);
			armyLines[side] = new Vec3(lineX, center.y, center.z);
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
				// The back ranks are the archers (as an army draws up), a quarter of each side; the captain in the front middle.
				boolean archer = i >= perSide - perSide / 4;
				SoldierKit.Role role = archer ? SoldierKit.Role.ARCHER : i == across / 2 || perSide <= across && i == perSide / 2 ? SoldierKit.Role.CAPTAIN
					: SoldierKit.Role.INFANTRY;
				SoldierKit.equip(drifter, level, armies[side], role, random);
				soldiers.put(drifter.getUUID(), new Soldier(battle, side, archer));
				if (beforeAdd != null) {
					beforeAdd.accept(drifter, side);
				}

				if (level.addFreshEntity(drifter)) {
					spawned.add(drifter);
				} else {
					soldiers.remove(drifter.getUUID());
				}
			}
		}

		return new Started(battle, spawned);
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
			Soldier soldier = soldier(context.drifter());
			Vec3[] armyLines = soldier == null ? null : lines.get(soldier.battle());
			if (armyLines == null || armyLines[1 - soldier.side()] == null) {
				return 0.0;
			}

			// Where the enemy army stood, or (got there and nobody in sight) toward the fighting it hears.
			return context.drifter().position().distanceTo(armyLines[1 - soldier.side()]) >= 6.0 || fighting(context.drifter()) != null ? 0.85 : 0.0;
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
			Soldier soldier = soldier(context.drifter());
			Vec3[] armyLines = soldier == null ? null : lines.get(soldier.battle());
			if (armyLines == null) {
				return null;
			}

			Vec3 enemy = armyLines[1 - soldier.side()];
			// At their old line already: to the sound of the fighting (as soldiers march to the guns).
			Vec3 heard = context.drifter().position().distanceTo(enemy) < 6.0 ? fighting(context.drifter()) : null;
			return new com.dinglebob.drifter.brain.activity.ExploreActivity(context.drifter(), heard != null ? "toward the fighting" : "marching on the enemy",
				heard != null ? heard : enemy, drifter -> com.dinglebob.drifter.social.BanditGoals.preyVisible(drifter));
		}
	}
}
