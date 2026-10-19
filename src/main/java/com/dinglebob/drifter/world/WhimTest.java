package com.dinglebob.drifter.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.activity.Activity;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.registry.ModEntities;
import com.dinglebob.drifter.whim.Whim;
import com.dinglebob.drifter.whim.WhimGoal;
import com.dinglebob.drifter.whim.Whims;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A whim test (debug; milestone 16, the user: "place 50 drifters (geared or whatever) doing various whims in a village
 * ... a test to see all the different actions and how that would perform"). Drifters spawned round a spot with a kit;
 * after a few seconds of looking about (each knows only what it has seen), each is set on a different whim that fits
 * where it stands, given what that whim needs if it lacks it (a test kit, not a real Drifter's). A report says how each
 * one went. Not saved; the Drifters go on with life afterwards.
 */
public final class WhimTest {
	private WhimTest() {
	}

	private static final class Entry {
		final UUID id;
		final String name;
		@Nullable
		String whim;
		@Nullable
		Activity activity;
		/** How it ended ("done", "failed: why", "stopped: why"), or why it got no whim; null while under way or waiting. */
		@Nullable
		String outcome;
		long startedAt;
		long endedAt;
		boolean assigned;
		/** Its height when the whim started (to see it came back down after a pillar). */
		int startY;
		/** What went wrong on the way ("couldn't ..." thoughts while at its whim), even when it ended as done. */
		final List<String> problems = new ArrayList<>();
		long thoughtsTo;

		Entry(UUID id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	private static final List<Entry> entries = new ArrayList<>();
	@Nullable
	private static ResourceKey<Level> dimension;
	/** Game time to start handing out whims (they look about first). */
	private static long assignAt = Long.MAX_VALUE;
	private static final long LOOK_ROUND_TICKS = 20 * 5;
	/** Drifters given a whim per tick (each tries whims against the world until one fits). */
	private static final int ASSIGN_PER_TICK = 4;
	/** The whims in the order they're handed out (shuffled), and those handed out already. */
	private static final List<Whim> pool = new ArrayList<>();
	private static final Set<String> used = new HashSet<>();
	/** Why whims didn't fit when handing them out (for the report). */
	private static final Map<String, Integer> unfit = new TreeMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> stop());
		ServerTickEvents.END_SERVER_TICK.register(WhimTest::tick);
	}

	public static int stop() {
		int count = entries.size();
		entries.clear();
		pool.clear();
		used.clear();
		unfit.clear();
		dimension = null;
		assignAt = Long.MAX_VALUE;
		return count;
	}

	/** Spawns {@code count} kitted Drifters within {@code radius} of {@code center}; their whims come in a few seconds. */
	public static int start(ServerLevel level, Vec3 center, int count, int radius) {
		return start(level, center, count, radius, "");
	}

	/** As above, only the whims named in {@code only} (ids or categories, spaces between; empty: all). */
	public static int start(ServerLevel level, Vec3 center, int count, int radius, String only) {
		stop();
		Set<String> wanted = only.isBlank() ? Set.of() : Set.of(only.trim().split("[ ,]+"));
		var random = level.getRandom();
		for (int i = 0; i < count; i++) {
			for (int attempt = 0; attempt < 10; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double distance = radius * Math.sqrt(random.nextDouble());
				int x = (int)Math.floor(center.x + Math.cos(angle) * distance);
				int z = (int)Math.floor(center.z + Math.sin(angle) * distance);
				level.getChunk(x >> 4, z >> 4);
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
				if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()
					|| level.getBlockState(top.below()).getCollisionShape(level, top.below()).isEmpty()) {
					continue;
				}

				DrifterEntity drifter = ModEntities.DRIFTER.create(level);
				if (drifter == null) {
					break;
				}

				float yaw = random.nextFloat() * 360.0F;
				drifter.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, yaw, 0.0F);
				drifter.setYHeadRot(yaw);
				drifter.setYBodyRot(yaw);
				drifter.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.COMMAND, null);
				// No Bandits here: one killing the others on sight would end everyone's whims (that's a battle test).
				if (drifter.getSocial().disposition() == com.dinglebob.drifter.social.Disposition.BANDIT) {
					drifter.getSocial().setDisposition(com.dinglebob.drifter.social.Disposition.WARY_NEUTRAL);
				}

				kit(drifter);
				if (level.addFreshEntity(drifter)) {
					entries.add(new Entry(drifter.getUUID(), drifter.getName().getString()));
				}

				break;
			}
		}

		dimension = level.dimension();
		assignAt = level.getGameTime() + LOOK_ROUND_TICKS;
		for (Whim whim : Whims.all()) {
			if (!whim.gates().later() && !whim.gates().griefing() && (wanted.isEmpty() || wanted.contains(whim.id()) || wanted.contains(whim.category()))) {
				pool.add(whim);
			}
		}

		java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		return entries.size();
	}

	/** Iron armour and tools, food and a few common blocks: most whims need little more. */
	private static void kit(DrifterEntity drifter) {
		drifter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		drifter.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		drifter.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		drifter.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		var inventory = drifter.getInventory();
		inventory.add(new ItemStack(Items.IRON_SWORD));
		inventory.add(new ItemStack(Items.IRON_PICKAXE));
		inventory.add(new ItemStack(Items.IRON_AXE));
		inventory.add(new ItemStack(Items.IRON_SHOVEL));
		inventory.add(new ItemStack(Items.BREAD, 16));
		inventory.add(new ItemStack(Items.OAK_PLANKS, 32));
		inventory.add(new ItemStack(Items.COBBLESTONE, 32));
		inventory.add(new ItemStack(Items.TORCH, 16));
		inventory.add(new ItemStack(Items.CRAFTING_TABLE));
		inventory.add(new ItemStack(Items.OAK_SAPLING, 4));
		inventory.add(new ItemStack(Items.WHEAT_SEEDS, 8));
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
		if (now >= assignAt) {
			int given = 0;
			for (Entry entry : entries) {
				if (!entry.assigned) {
					entry.assigned = true;
					assign(level, entry, now);
					if (++given >= ASSIGN_PER_TICK) {
						break;
					}
				}
			}

			if (given < ASSIGN_PER_TICK) {
				assignAt = Long.MAX_VALUE;
			}
		}

		for (Entry entry : entries) {
			if (entry.activity == null || entry.outcome != null) {
				continue;
			}

			if (!(level.getEntity(entry.id) instanceof DrifterEntity drifter) || !drifter.isAlive()) {
				entry.outcome = "died or gone";
				entry.endedAt = now;
				continue;
			}

			for (var thought : drifter.getMemory().thoughts().latest(8)) {
				if (thought.time() > entry.thoughtsTo && thought.text().startsWith("couldn't") && entry.problems.size() < 5) {
					entry.problems.add(thought.text());
				}
			}

			entry.thoughtsTo = now;
			if (drifter.getDrifterBrain().activity() != entry.activity) {
				var tasks = drifter.getTasks();
				entry.outcome = tasks.lastEnded() == entry.activity ? tasks.lastOutcome() : "ended";
				entry.endedAt = now;
				int rise = drifter.getBlockY() - entry.startY;
				com.dinglebob.drifter.Drifter.LOGGER.info("[whim test] {}: {} -> {} after {} s{}{}", entry.name, entry.whim, entry.outcome, (now - entry.startedAt) / 20,
					rise == 0 ? "" : String.format(Locale.ROOT, " (%+d blocks up)", rise), entry.problems.isEmpty() ? "" : "; trouble: " + String.join("; ", entry.problems));
			}
		}
	}

	/** Its whim: one not handed out yet that fits here and now if there is one, else any that fits. */
	private static void assign(ServerLevel level, Entry entry, long now) {
		if (!(level.getEntity(entry.id) instanceof DrifterEntity drifter) || !drifter.isAlive()) {
			entry.outcome = "died or gone";
			return;
		}

		if (!(drifter.getDrifterBrain().goal("whim") instanceof WhimGoal goal)) {
			entry.outcome = "no whim goal";
			return;
		}

		Whim chosen = null;
		// The second time round (all handed out), in its own order: not the same first fit for everyone.
		List<Whim> again = new ArrayList<>(pool);
		java.util.Collections.shuffle(again, new java.util.Random(drifter.getRandom().nextLong()));
		for (int pass = 0; pass < 2 && chosen == null; pass++) {
			for (Whim whim : pass == 0 ? pool : again) {
				if ((pass == 0) == used.contains(whim.id())) {
					continue;
				}

				String why = WhimGoal.whyNot(drifter, whim, null);
				if (why != null && why.startsWith("doesn't have") && supply(drifter, whim)) {
					why = WhimGoal.whyNot(drifter, whim, null);
				}

				if (why == null) {
					chosen = whim;
					break;
				}

				if (pass == 0) {
					unfit.merge(why.replaceAll(" \\(.*", ""), 1, Integer::sum);
				}
			}
		}

		if (chosen == null) {
			entry.outcome = "nothing fitted";
			return;
		}

		goal.force(chosen);
		if (!drifter.getDrifterBrain().force("whim")) {
			entry.outcome = "wouldn't start " + chosen.id();
			return;
		}

		used.add(chosen.id());
		entry.whim = chosen.id();
		entry.activity = drifter.getDrifterBrain().activity();
		entry.startedAt = now;
		entry.startY = drifter.getBlockY();
		entry.thoughtsTo = now - 1;
		com.dinglebob.drifter.Drifter.LOGGER.info("[whim test] {} starts {} ({})", entry.name, chosen.id(), chosen.thought());
	}

	/** Gives it what the whim needs that it lacks (an item, or the first of a tag); false if it can't. */
	private static boolean supply(DrifterEntity drifter, Whim whim) {
		boolean gave = false;
		for (Whim.Need need : whim.needs()) {
			int lacking = need.count() - WhimGoal.count(drifter, need.item());
			if (lacking <= 0) {
				continue;
			}

			Item item = null;
			if (need.item().startsWith("#")) {
				var tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ResourceLocation.parse(need.item().substring(1))));
				if (tag.isPresent() && tag.get().size() > 0) {
					item = tag.get().get(0).value();
				}
			} else if (!need.item().startsWith("@")) {
				item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(need.item())).orElse(null);
			}

			if (item == null || item == Items.AIR) {
				return false;
			}

			drifter.getInventory().add(new ItemStack(item, lacking));
			gave = true;
		}

		return gave;
	}

	/** One line for `/drifter perf`, or null with no test on. */
	@Nullable
	public static String report() {
		if (entries.isEmpty()) {
			return null;
		}

		int waiting = 0, doing = 0, done = 0, failed = 0, stopped = 0, none = 0, trouble = 0;
		for (Entry entry : entries) {
			if (entry.activity == null) {
				if (entry.outcome == null) {
					waiting++;
				} else {
					none++;
				}
			} else if (entry.outcome == null) {
				doing++;
			} else if (entry.outcome.equals("done")) {
				done++;
				if (!entry.problems.isEmpty()) {
					trouble++;
				}
			} else if (entry.outcome.startsWith("failed")) {
				failed++;
			} else {
				stopped++;
			}
		}

		return String.format(Locale.ROOT, "%d Drifters, %d different whims: %d doing, %d done (%d of them with trouble on the way), %d failed, %d stopped for something else; %d waiting, %d got none",
			entries.size(), used.size(), doing, done, trouble, failed, stopped, waiting, none);
	}

	/** A line per Drifter: its whim and how it's going (for `/drifter whimtest report`). */
	public static List<String> details(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		ServerLevel level = dimension == null ? null : server.getLevel(dimension);
		long now = level == null ? 0L : level.getGameTime();
		for (Entry entry : entries) {
			String state;
			if (entry.activity == null) {
				state = entry.outcome == null ? "waiting for its whim" : entry.outcome;
			} else if (entry.outcome == null) {
				state = "doing (" + (now - entry.startedAt) / 20 + " s): " + entry.activity.describe() + " / " + entry.activity.stepName();
			} else {
				state = entry.outcome + " after " + (entry.endedAt - entry.startedAt) / 20 + " s";
			}

			lines.add(entry.name + ": " + (entry.whim == null ? "-" : entry.whim) + " -> " + state
				+ (entry.problems.isEmpty() ? "" : "; trouble: " + String.join("; ", entry.problems)));
		}

		if (!unfit.isEmpty()) {
			lines.add("Why whims didn't fit when handed out: " + unfit);
		}

		return lines;
	}
}
