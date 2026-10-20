package com.dinglebob.drifter.whim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.brain.Home;
import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.entity.DrifterInventory;
import com.dinglebob.drifter.personality.Mood;
import com.dinglebob.drifter.personality.Trait;
import com.dinglebob.drifter.world.PlayerPlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;

/**
 * Whims while it was away (milestone 20, docs/OFFSCREEN_WHIMS.md; the user's pick A + a small B + E). During catch-up its
 * free time in each slice goes on a few whims, picked the way live ones are (who it is, its mood, the stage it's at,
 * the slice's time of day, cooldowns): each eases its boredom and starts its cooldown. Whims that leave something
 * behind count only when they really do, at its own home while that's loaded, two at most per catch-up, with the
 * items it carries and through the same checks as the hut shell (free ground, nothing floating, never on a player's
 * blocks). What it did goes in one line of its thoughts at the end, nowhere else (user, 2026-10-20: "the while you were
 * away should only be in thoughts log").
 *
 * <p>Cheap by design (user: "keep the optimization in mind"): weights and a few checks per slice, no world search
 * except for a trace (one small area round its home, twice a catch-up at most).
 */
public final class WhimsAway {
	/** Whims a day for a Drifter of middling industriousness and fidget (live, one every few minutes of free time). */
	private static final double PER_DAY = 2.0;
	/** Most things it leaves at home per catch-up (so a week away doesn't bury the base in junk). */
	private static final int MAX_TRACES = 2;
	/** How far from the middle of its home a trace goes, and how close at least. */
	private static final int TRACE_RADIUS = 10;
	private static final int TRACE_MIN = 4;
	/** Picks it tries per whim done (a whim that can't happen then is set aside and another drawn). */
	private static final int TRIES = 4;

	private WhimsAway() {
	}

	/** What it did for fun while away: each whim's thought, how many times, and how many it left at home. */
	public static final class Record {
		private final Map<String, int[]> done = new LinkedHashMap<>();
		private int traces;
		/** Whims that need something about, and whether there is (looked at once a catch-up). */
		private final Map<String, Boolean> around = new java.util.HashMap<>();

		/** The thoughts line, or null if it did nothing for fun. */
		@Nullable
		public String describe() {
			if (this.done.isEmpty()) {
				return null;
			}

			List<String> parts = new ArrayList<>();
			this.done.forEach((thought, counts) -> {
				String times = counts[0] == 2 ? " twice" : counts[0] > 2 ? " " + counts[0] + " times" : "";
				String left = counts[1] == 0 ? "" : counts[0] > 1 ? " (" + counts[1] + " left by home)" : " (left by home)";
				parts.add(thought + times + left);
			});
			return "while away, for fun: " + String.join(", ", parts);
		}

		int count() {
			return this.done.values().stream().mapToInt(c -> c[0]).sum();
		}
	}

	/**
	 * One catch-up slice's free time: {@code start} is the game time the slice began, {@code dayTime} the time of day
	 * then, {@code ticks} its length.
	 */
	public static void slice(DrifterEntity drifter, long start, long dayTime, long ticks, int stage, RandomSource random, Record record) {
		if (!com.dinglebob.drifter.DrifterConfig.whims()) {
			return;
		}

		var p = drifter.getPersonality();
		double expected = PER_DAY * ticks / 24000.0 * (1.15 - 0.5 * p.get(Trait.INDUSTRIOUSNESS)) * (0.8 + 0.4 * p.get(Trait.FIDGET));
		int count = (int)expected + (random.nextDouble() < expected - (int)expected ? 1 : 0);
		if (count == 0) {
			return;
		}

		Home home = homeLoaded(drifter);
		WhimState state = WhimGoal.state(drifter);
		for (int n = 0; n < count; n++) {
			long offset = (long)(random.nextDouble() * ticks);
			long time = start + offset;
			long day = Math.floorMod(dayTime + offset, 24000L);
			List<Whim> candidates = new ArrayList<>();
			List<Double> weights = new ArrayList<>();
			double total = 0.0;
			for (Whim whim : Whims.all()) {
				double weight = whim.offscreen() == Whim.Offscreen.SHOW || whim.offscreen() == Whim.Offscreen.TRACE ? WhimGoal.weight(drifter, whim) : 0.0;
				if (weight <= 0.0 || whim.stage() > stage || whim.gates().griefing() || whim.gates().multiplayer()
					|| time - state.lastDone(whim.id()) < whim.cooldown() || !fitsTime(whim.when(), day) || !carries(drifter, whim)
					|| whim.offscreen() == Whim.Offscreen.TRACE && (home == null || record.traces >= MAX_TRACES) || !plausible(drifter, whim, home)) {
					continue;
				}

				candidates.add(whim);
				weights.add(weight);
				total += weight;
			}

			for (int tries = 0; tries < TRIES && !candidates.isEmpty(); tries++) {
				double roll = random.nextDouble() * total;
				int index = candidates.size() - 1;
				for (int i = 0; i < candidates.size(); i++) {
					roll -= weights.get(i);
					if (roll <= 0.0) {
						index = i;
						break;
					}
				}

				Whim whim = candidates.get(index);
				// One that needs something about (a cat, a campfire, water) only if there's one round its home.
				if (!around(drifter, whim, home, record)) {
					total -= weights.get(index);
					candidates.remove(index);
					weights.remove(index);
					continue;
				}

				// One that leaves something counts only if it really leaves it (the log never claims a tower that isn't there).
				boolean left = false;
				if (whim.offscreen() == Whim.Offscreen.TRACE) {
					left = trace(drifter, whim, home, random);
					if (!left) {
						total -= weights.get(index);
						candidates.remove(index);
						weights.remove(index);
						continue;
					}

					record.traces++;
				}

				state.did(whim.id(), time);
				p.mood().ease(Mood.BORED, WhimGoal.BORED_RELIEF);
				int[] counts = record.done.computeIfAbsent(whim.thought(), k -> new int[2]);
				counts[0]++;
				counts[1] += left ? 1 : 0;
				break;
			}
		}
	}

	/** Its home, if it's in this dimension, its own and loaded (traces go only there). */
	@Nullable
	private static Home homeLoaded(DrifterEntity drifter) {
		Home home = drifter.getDrifterBrain().home();
		Level level = drifter.level();
		return home != null && !home.borrowed() && home.dimension.equals(level.dimension().location()) && level.isLoaded(home.center) ? home : null;
	}

	/** The time of day (rain it can't know about after the fact: those whims wait for real rain). */
	private static boolean fitsTime(Whim.When when, long time) {
		return switch (when) {
			case ANY -> true;
			case DAY, CLEAR -> time < 12000L;
			case NIGHT -> time >= 13000L && time < 23000L;
			case SUNSET -> time >= 11500L && time < 13000L;
			case SUNRISE -> time >= 22800L || time < 500L;
			case RAIN, THUNDER -> false;
		};
	}

	/** What it needs, carried (nothing is made for a whim while away). */
	private static boolean carries(DrifterEntity drifter, Whim whim) {
		for (Whim.Need need : whim.needs()) {
			if (WhimGoal.count(drifter, need.item()) < need.count()) {
				return false;
			}
		}

		return true;
	}

	/** Could it have done this away from everyone, from what it has (nobody was with it out there, no seat, its own home). */
	private static boolean plausible(DrifterEntity drifter, Whim whim, @Nullable Home home) {
		return switch (whim.action()) {
			case "chat", "hang_out", "sit", "gift", "throw_at", "visit_friend" -> false;
			case "peek_chest" -> home != null && home.hasChest();
			case "hop" -> !whim.param("on", "here").equals("bed") || home != null && (home.houseBed != null || home.hutBuilt);
			case "watch_sky" -> !whim.param("from", "outside").equals("door") || home != null && home.hutBuilt;
			case "visit" -> WhimActions.Visit.place(drifter, whim) != null;
			case "sign" -> whim.param("near", "here").equals("here");
			case "steps" -> StepsAction.plausibleAway(whim, home);
			default -> true;
		};
	}

	/**
	 * For a whim that needs something about: there is one round its home (it lives there; its eyes' knowledge isn't kept
	 * while it's unloaded, so this looks at its home's surroundings, which it has seen day after day). Away from home, or
	 * homeless: no. Whims that need nothing in particular: yes. Once a catch-up per whim.
	 */
	private static boolean around(DrifterEntity drifter, Whim whim, @Nullable Home home, Record record) {
		Boolean known = record.around.get(whim.id());
		if (known != null) {
			return known;
		}

		Level level = drifter.level();
		boolean there = switch (whim.action()) {
			case "watch_block", "use_block" -> home != null && blockNear(level, home.center, whim.param("radius", 16), WhimActions.WatchBlock.matcher(whim.params("blocks")));
			case "watch_mob" -> home != null && mobNear(level, home.center, whim.param("range", 16), whim.params("mobs"));
			case "swim" -> home != null && blockNear(level, home.center, whim.param("radius", 24), state -> state.is(Blocks.WATER));
			case "high_point" -> home != null && hillNear(level, home.center, whim.param("radius", 40), whim.param("rise", 8));
			case "steps" -> StepsAction.aroundHome(whim, home, (radius, ids) -> home != null && blockNear(level, home.center, radius, WhimActions.WatchBlock.matcher(ids)),
				(radius, ids) -> home != null && mobNear(level, home.center, radius, ids));
			default -> true;
		};
		record.around.put(whim.id(), there);
		return there;
	}

	/** A block passing the test within {@code radius} of here (8 up or down), from the chunks' own sections (those without one skipped whole). */
	static boolean blockNear(Level level, BlockPos center, int radius, java.util.function.Predicate<BlockState> test) {
		int minY = Math.max(level.getMinBuildHeight(), center.getY() - 8);
		int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 8);
		int x0 = center.getX() - radius;
		int x1 = center.getX() + radius;
		int z0 = center.getZ() - radius;
		int z1 = center.getZ() + radius;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
				var chunk = com.dinglebob.drifter.world.TickChunks.get(level, cx, cz);
				if (chunk == null) {
					continue;
				}

				for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
					var section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(test)) {
						continue;
					}

					int baseY = level.getSectionYFromSectionIndex(index) << 4;
					for (int x = Math.max(x0, cx << 4); x <= Math.min(x1, (cx << 4) + 15); x++) {
						for (int z = Math.max(z0, cz << 4); z <= Math.min(z1, (cz << 4) + 15); z++) {
							for (int y = Math.max(minY, baseY); y <= Math.min(maxY, baseY + 15); y++) {
								if (test.test(section.getBlockState(x & 15, y & 15, z & 15))) {
									return true;
								}
							}
						}
					}
				}
			}
		}

		return false;
	}

	/** One of these mobs within {@code range} of here. */
	static boolean mobNear(Level level, BlockPos center, int range, List<String> ids) {
		return !level.getEntities((net.minecraft.world.entity.Entity)null, new AABB(center).inflate(range),
			e -> e.isAlive() && ids.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())).isEmpty();
	}

	/** Ground {@code rise} blocks or more above here within {@code radius} (a hill to climb), on a coarse grid of the height map. */
	private static boolean hillNear(Level level, BlockPos center, int radius, int rise) {
		for (int x = center.getX() - radius; x <= center.getX() + radius; x += 4) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 4) {
				var chunk = com.dinglebob.drifter.world.TickChunks.get(level, x >> 4, z >> 4);
				if (chunk != null && chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1 >= center.getY() + rise) {
					return true;
				}
			}
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Traces
	// ---------------------------------------------------------------------------------------------

	/** Leaves what this whim leaves at its home, if it can now; true if it did. */
	private static boolean trace(DrifterEntity drifter, Whim whim, @Nullable Home home, RandomSource random) {
		if (home == null) {
			return false;
		}

		return switch (whim.action()) {
			case "torch_dark" -> torch(drifter, home);
			case "plant_sapling" -> sapling(drifter, home, random);
			case "sign" -> sign(drifter, whim, home, random);
			case "build" -> build(drifter, whim, home);
			default -> false;
		};
	}

	/** A torch in a dark corner at home (inside its hut is fine): with more than four torches on it, as live. */
	private static boolean torch(DrifterEntity drifter, Home home) {
		Level level = drifter.level();
		if (drifter.getInventory().count(stack -> stack.is(Items.TORCH)) <= 4) {
			return false;
		}

		BlockPos spot = nearest(home, TRACE_RADIUS, 0, pos -> room(level, pos) && level.getBrightness(LightLayer.BLOCK, pos) < 4
			&& level.getBrightness(LightLayer.SKY, pos) < 8 && Blocks.TORCH.defaultBlockState().canSurvive(level, pos));
		return spot != null && place(drifter, spot, Blocks.TORCH.defaultBlockState(), stack -> stack.is(Items.TORCH));
	}

	/** A sapling it carries, planted on grass or dirt under the sky near home (not in its buildings, as live). */
	private static boolean sapling(DrifterEntity drifter, Home home, RandomSource random) {
		Level level = drifter.level();
		ItemStack sapling = find(drifter, stack -> stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem);
		if (sapling == null) {
			return false;
		}

		BlockState state = ((BlockItem)sapling.getItem()).getBlock().defaultBlockState();
		BlockPos spot = nearest(home, TRACE_RADIUS + 2, 6, pos -> room(level, pos) && !home.inOwnBuilding(pos, 3) && level.canSeeSky(pos)
			&& (level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) || level.getBlockState(pos.below()).is(Blocks.DIRT)) && state.canSurvive(level, pos));
		return spot != null && place(drifter, spot, state, stack -> stack.getItem() == sapling.getItem());
	}

	/** A sign with one of the whim's lines on it, on free ground near home, facing home. */
	private static boolean sign(DrifterEntity drifter, Whim whim, Home home, RandomSource random) {
		Level level = drifter.level();
		List<String> lines = whim.params("lines");
		ItemStack sign = find(drifter, stack -> stack.is(ItemTags.SIGNS) && stack.getItem() instanceof BlockItem item
			&& item.getBlock() instanceof StandingSignBlock);
		if (lines.isEmpty() || sign == null) {
			return false;
		}

		BlockPos spot = nearest(home, TRACE_RADIUS, TRACE_MIN, pos -> ground(level, home, pos, 2));
		if (spot == null) {
			return false;
		}

		// Facing home, as it stood there to put it up (StandingSignBlock: the placer's turn, plus half a turn).
		float yaw = (float)Math.toDegrees(Math.atan2(-(spot.getX() - home.center.getX()), spot.getZ() - home.center.getZ()));
		BlockState state = ((BlockItem)sign.getItem()).getBlock().defaultBlockState().setValue(StandingSignBlock.ROTATION,
			RotationSegment.convertToSegment(yaw + 180.0F));
		if (!place(drifter, spot, state, stack -> stack.getItem() == sign.getItem())) {
			return false;
		}

		WhimActions.Sign.write(level, spot, WhimActions.Sign.fill(drifter, lines.get(random.nextInt(lines.size()))));
		return true;
	}

	/** A whim's little build on free ground near home, from blocks it carries (all of them, or it doesn't start). */
	private static boolean build(DrifterEntity drifter, Whim whim, Home home) {
		Level level = drifter.level();
		List<WhimActions.Build.Cell> cells = WhimActions.Build.cells(whim);
		if (cells.isEmpty()) {
			return false;
		}

		Map<String, Integer> wanted = new LinkedHashMap<>();
		cells.forEach(cell -> wanted.merge(cell.item(), 1, Integer::sum));
		for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
			var matcher = WhimActions.itemMatcher(entry.getKey());
			if (drifter.getInventory().count(stack -> matcher.test(stack) && stack.getItem() instanceof BlockItem) < entry.getValue()) {
				return false;
			}
		}

		BlockPos site = nearest(home, TRACE_RADIUS, TRACE_MIN, pos -> {
			for (WhimActions.Build.Cell cell : cells) {
				BlockPos at = pos.offset(cell.dx(), cell.dy(), cell.dz());
				if (!(cell.dy() == 0 ? ground(level, home, at, 2) && WhimActions.Build.survives(level, cell.item(), at) : room(level, at))) {
					return false;
				}
			}

			return true;
		});
		if (site == null) {
			return false;
		}

		// From the ground up, in the whim's order, each block its own.
		for (WhimActions.Build.Cell cell : cells) {
			var matcher = WhimActions.itemMatcher(cell.item());
			ItemStack stack = find(drifter, s -> matcher.test(s) && s.getItem() instanceof BlockItem);
			BlockPos at = site.offset(cell.dx(), cell.dy(), cell.dz());
			if (stack == null || !level.getBlockState(at).canBeReplaced()) {
				continue;
			}

			place(drifter, at, ((BlockItem)stack.getItem()).getBlock().defaultBlockState(), s -> s == stack);
		}

		return true;
	}

	/** Free to put a block in: loaded, replaceable, dry, nobody standing there, not a player's. */
	private static boolean room(Level level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
			&& !PlayerPlacedBlocks.isPlayerPlaced(level, pos) && level.getEntities(null, new AABB(pos)).isEmpty();
	}

	/** Room here on solid ground that isn't a player's, outside its own buildings, with room above. */
	private static boolean ground(Level level, Home home, BlockPos pos, int margin) {
		BlockPos below = pos.below();
		return room(level, pos) && level.getBlockState(pos.above()).canBeReplaced() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& !PlayerPlacedBlocks.isPlayerPlaced(level, below) && !home.inOwnBuilding(pos, margin);
	}

	/** The spot nearest home's middle (at least {@code min} off it) that passes the test, or null. */
	@Nullable
	private static BlockPos nearest(Home home, int radius, int min, java.util.function.Predicate<BlockPos> test) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double flat = dx * dx + dz * dz;
				if (flat < min * min || flat > radius * radius || flat >= bestDistance) {
					continue;
				}

				for (int dy = -3; dy <= 3; dy++) {
					pos.set(home.center.getX() + dx, home.center.getY() + dy, home.center.getZ() + dz);
					if (test.test(pos)) {
						best = pos.immutable();
						bestDistance = flat;
						break;
					}
				}
			}
		}

		return best;
	}

	@Nullable
	private static ItemStack find(DrifterEntity drifter, java.util.function.Predicate<ItemStack> test) {
		DrifterInventory inventory = drifter.getInventory();
		for (int slot = 0; slot < DrifterInventory.MAIN_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && test.test(stack)) {
				return stack;
			}
		}

		return null;
	}

	/** Puts the block there from one of its own items and marks it as built by a Drifter (as its hut is); false if it can't. */
	private static boolean place(DrifterEntity drifter, BlockPos pos, BlockState state, java.util.function.Predicate<ItemStack> item) {
		ItemStack stack = find(drifter, item);
		Level level = drifter.level();
		if (stack == null || !state.canSurvive(level, pos)) {
			return false;
		}

		level.setBlock(pos, state, 3);
		stack.shrink(1);
		PlayerPlacedBlocks.recordBuilt(level, pos);
		return true;
	}
}
