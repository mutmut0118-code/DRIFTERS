package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.executor.Task;
import com.dinglebob.drifter.executor.task.NavigateTask;
import com.dinglebob.drifter.registry.ModEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A walking test (debug; milestone 17, the user: "getting around on rough ground"): Drifters spawned round a spot, each
 * sent to its own spot on dry ground some way off, and a report of how many got there, how long it took, and why the
 * others didn't. With a pickaxe, a shovel, an axe and blocks, like a Drifter a few days in. Not saved.
 */
public final class NavTest {
	private NavTest() {
	}

	private static final class Entry {
		final UUID id;
		final String name;
		final BlockPos from;
		final BlockPos to;
		@Nullable
		Task task;
		@Nullable
		String outcome;
		long startedAt;
		long endedAt;
		double closest = Double.MAX_VALUE;

		Entry(UUID id, String name, BlockPos from, BlockPos to) {
			this.id = id;
			this.name = name;
			this.from = from;
			this.to = to;
		}
	}

	private static final List<Entry> entries = new ArrayList<>();
	@Nullable
	private static ResourceKey<Level> dimension;
	/** When to send them off (they look about for a few seconds first: they only know what they've seen). */
	private static long startAt = Long.MAX_VALUE;
	/** Longest any one may take (then it counts as stuck). */
	private static final long TIME_LIMIT = 20L * 120;

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop());
		ServerTickEvents.END_SERVER_TICK.register(NavTest::tick);
	}

	public static int stop() {
		int count = entries.size();
		entries.clear();
		dimension = null;
		startAt = Long.MAX_VALUE;
		return count;
	}

	/** {@code count} Drifters within {@code radius} of {@code center}, each to go {@code distance} blocks in a random direction. */
	public static int start(ServerLevel level, Vec3 center, int count, int radius, int distance) {
		stop();
		var random = level.getRandom();
		for (int i = 0; i < count; i++) {
			BlockPos from = dryGround(level, center, radius, random);
			if (from == null) {
				continue;
			}

			BlockPos to = null;
			for (int attempt = 0; attempt < 12 && to == null; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				to = dryColumn(level, from.getX() + (int)Math.round(Math.cos(angle) * distance), from.getZ() + (int)Math.round(Math.sin(angle) * distance));
			}

			if (to == null) {
				continue;
			}

			DrifterEntity drifter = ModEntities.DRIFTER.create(level);
			if (drifter == null) {
				break;
			}

			drifter.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(from), MobSpawnType.COMMAND, null);
			if (drifter.getSocial().disposition() == com.dinglebob.drifter.social.Disposition.BANDIT) {
				drifter.getSocial().setDisposition(com.dinglebob.drifter.social.Disposition.WARY_NEUTRAL);
			}

			drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			var inventory = drifter.getInventory();
			inventory.add(new ItemStack(Items.IRON_PICKAXE));
			inventory.add(new ItemStack(Items.IRON_SHOVEL));
			inventory.add(new ItemStack(Items.IRON_AXE));
			inventory.add(new ItemStack(Items.COBBLESTONE, 64));
			inventory.add(new ItemStack(Items.DIRT, 32));
			inventory.add(new ItemStack(Items.BREAD, 16));
			// The boat it carries from early on (milestone 18): open water on the way, it rows across.
			inventory.add(new ItemStack(Items.OAK_BOAT));
			if (level.addFreshEntity(drifter)) {
				entries.add(new Entry(drifter.getUUID(), drifter.getName().getString(), from, to));
			}
		}

		dimension = level.dimension();
		startAt = level.getGameTime() + 20 * 4;
		return entries.size();
	}

	@Nullable
	private static BlockPos dryGround(ServerLevel level, Vec3 center, int radius, net.minecraft.util.RandomSource random) {
		for (int attempt = 0; attempt < 20; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = radius * Math.sqrt(random.nextDouble());
			BlockPos pos = dryColumn(level, (int)Math.floor(center.x + Math.cos(angle) * distance), (int)Math.floor(center.z + Math.sin(angle) * distance));
			if (pos != null) {
				return pos;
			}
		}

		return null;
	}

	/** The top of this column if it's dry ground to stand on (not water, lava or leaves), else null. */
	@Nullable
	private static BlockPos dryColumn(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()
			|| level.getBlockState(top.below()).getCollisionShape(level, top.below()).isEmpty()
			|| level.getBlockState(top.below()).is(net.minecraft.tags.BlockTags.LEAVES)) {
			return null;
		}

		return top;
	}

	private static void tick(MinecraftServer server) {
		if (entries.isEmpty() || dimension == null) {
			return;
		}

		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return;
		}

		long now = level.getGameTime();
		for (Entry entry : entries) {
			if (entry.outcome != null) {
				continue;
			}

			if (!(level.getEntity(entry.id) instanceof DrifterEntity drifter) || !drifter.isAlive()) {
				finish(entry, "died or gone", now);
				continue;
			}

			if (entry.task == null) {
				if (now >= startAt) {
					entry.task = new NavigateTask(drifter, Vec3.atBottomCenterOf(entry.to), 1.5, "walk to the test spot");
					drifter.getTasks().start(entry.task, null);
					entry.startedAt = now;
				}

				continue;
			}

			entry.closest = Math.min(entry.closest, drifter.position().distanceTo(Vec3.atBottomCenterOf(entry.to)));
			var tasks = drifter.getTasks();
			if (tasks.current() != entry.task) {
				finish(entry, tasks.lastEnded() == entry.task ? tasks.lastOutcome() : "ended", now);
			} else if (now - entry.startedAt > TIME_LIMIT) {
				tasks.cancel("navtest time limit");
				finish(entry, "stuck: out of time (" + ((NavigateTask)entry.task).debugStep() + ")", now);
			}
		}
	}

	private static void finish(Entry entry, String outcome, long now) {
		entry.outcome = outcome;
		entry.endedAt = now;
		com.dinglebob.drifter.Drifter.LOGGER.info("[nav test] {}: {} -> {}: {} after {} s (closest {} blocks)", entry.name, entry.from.toShortString(),
			entry.to.toShortString(), outcome, (now - entry.startedAt) / 20, String.format(Locale.ROOT, "%.1f", entry.closest));
	}

	/** One line: how many got there, how long, and the reasons the others didn't. */
	@Nullable
	public static String report() {
		if (entries.isEmpty()) {
			return null;
		}

		int done = 0;
		int going = 0;
		long time = 0;
		Map<String, Integer> reasons = new TreeMap<>();
		for (Entry entry : entries) {
			if (entry.outcome == null) {
				going++;
			} else if (entry.outcome.equals("done")) {
				done++;
				time += entry.endedAt - entry.startedAt;
			} else {
				reasons.merge(entry.outcome.replaceAll("\\(.*", "").replaceAll(" at -?\\d+, -?\\d+, -?\\d+", "").trim(), 1, Integer::sum);
			}
		}

		return String.format(Locale.ROOT, "%d Drifters: %d got there (%.0f s on average), %d still going, %d didn't %s", entries.size(), done,
			done == 0 ? 0.0 : time / 20.0 / done, going, entries.size() - done - going, reasons);
	}
}
