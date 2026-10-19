package com.dinglebob.drifter.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;
import com.dinglebob.drifter.perception.Awareness;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A snapshot of what the Drifter knows around it, taken about once a second for the planner and the goals (DESIGN.md
 * section 8, "plan from memory, not the world"): the minable blocks it has seen nearby, the animals it has seen
 * recently, and the stations it remembers. Everything comes from its perception and memory.
 */
public final class KnownWorld {
	public static final int RADIUS = 24;
	/** Animals remembered this far away still count as "known". */
	public static final double ANIMAL_MEMORY_RANGE = 48.0;
	public static final double TREE_MEMORY_RANGE = 96.0;

	/** One example state of each kind of block it could go and take (the planner only asks which kinds it knows). */
	private final Map<Block, BlockState> examples = new it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap<>();
	private final Set<EntityType<?>> animals = new HashSet<>();
	/** Kinds of animals seen ("minecraft:sheep/white"), for wool of a colour. */
	private final Set<String> variants = new HashSet<>();
	public final boolean tableNear;
	public final boolean furnaceNear;
	public final boolean smokerNear;
	/** A remembered grove of trees farther away, if any. */
	@Nullable
	public final Vec3 treesHint;
	/** Bare stone (a cliff, a cave mouth) or a mountain it saw from afar, if any: where to look for stone. */
	@Nullable
	public final Vec3 stoneHint;
	/** How far away far-sight hints count. */
	public static final double FAR_HINT_RANGE = 112.0;

	private KnownWorld(DrifterEntity drifter) {
		Level level = drifter.level();
		long now = level.getGameTime();
		this.scannedAt = now;
		this.scannedFrom = drifter.blockPosition().immutable();
		Blacklist blacklist = drifter.getDrifterBrain().blacklist();
		TreeLogs treeCheck = new TreeLogs(level);
		// Sections whose palette has nothing it hasn't already found an example of are skipped whole (performance: stone and
		// air everywhere, once it has an example of stone, cost nothing more to scan).
		var unseenKinds = com.dinglebob.drifter.perception.TerrainKnowledge.SectionFilter.palette(level,
			state -> !state.isAir() && !this.examples.containsKey(state.getBlock()) && state.getFluidState().isEmpty());
		// Air, and a kind it already found one of, skipped first: the rest don't change the answer (the slow checks below were
		// most of a brain decision's time, growing as it explored; section 22 budget).
		drifter.getSenses().terrain().forEachKnownState(level, drifter.blockPosition(), RADIUS, unseenKinds,
			state -> state.isAir() || this.examples.containsKey(state.getBlock()), (pos, state) -> {
			if (!takeable(level, pos, state, blacklist, now)) {
				return;
			}

			// Wood only from trees: a village house's log posts aren't a tree.
			if (state.is(net.minecraft.tags.BlockTags.LOGS) && !treeCheck.check(pos)) {
				return;
			}

			this.examples.put(state.getBlock(), state);
		});

		for (Awareness awareness : drifter.getSenses().tracked()) {
			if (isHuntable(awareness, now)) {
				this.animals.add(awareness.entity.getType());
			}
		}

		ResourceLocation dimension = level.dimension().location();
		for (MemoryEntry entry : drifter.getMemory().find(MemoryKind.MOB,
			e -> e.dimension.equals(dimension) && e.pos.distanceTo(drifter.position()) <= ANIMAL_MEMORY_RANGE
				&& (e.detail == null || !e.detail.contains("baby")))) {
			ResourceLocation id = ResourceLocation.tryParse(entry.label);
			if (id != null) {
				BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(this.animals::add);
				if (entry.detail != null && entry.detail.contains(" wool")) {
					for (String part : entry.detail.split("; ")) {
						if (part.endsWith(" wool")) {
							this.variants.add(id + "/" + part.substring(0, part.length() - " wool".length()));
						}
					}
				}
			}
		}

		this.tableNear = Stations.usable(drifter, Blocks.CRAFTING_TABLE) != null;
		this.furnaceNear = Stations.usable(drifter, Blocks.FURNACE) != null;
		this.smokerNear = Stations.usable(drifter, Blocks.SMOKER) != null;
		// Groves farther than what it can see from here (the one it's standing in is already counted, or cut down).
		MemoryEntry trees = drifter.getMemory().nearest(MemoryKind.RESOURCE, dimension, drifter.position(), TREE_MEMORY_RANGE,
			e -> e.label.equals("trees") && e.pos.distanceTo(drifter.position()) > RADIUS);
		this.treesHint = trees == null ? null : trees.pos;
		MemoryEntry stone = drifter.getMemory().nearest(MemoryKind.RESOURCE, dimension, drifter.position(), com.dinglebob.drifter.DrifterConfig.sightRange(),
			e -> e.label.equals(com.dinglebob.drifter.perception.BlockNotes.EXPOSED_STONE) && e.pos.distanceTo(drifter.position()) > RADIUS);
		if (stone == null) {
			stone = drifter.getMemory().nearest(MemoryKind.LANDMARK, dimension, drifter.position(), com.dinglebob.drifter.DrifterConfig.sightRange(),
				e -> e.label.equals("mountain") && e.pos.distanceTo(drifter.position()) > RADIUS);
		}

		this.stoneHint = stone == null ? null : stone.pos;
	}

	/**
	 * What it knows round it now. One scan a tick per Drifter however many ask (a decision and the activity it starts, a
	 * plan for a hut's materials: each scanned again; milestone 16).
	 */
	public static KnownWorld scan(DrifterEntity drifter) {
		long now = drifter.level().getGameTime();
		KnownWorld cached = drifter.getDrifterBrain().knownWorldCache;
		if (cached != null && cached.scannedAt == now && cached.scannedFrom.equals(drifter.blockPosition())) {
			return cached;
		}

		// Nobody close enough to watch it: the scan holds for a few seconds while it stays about where it was (what it knows
		// of round it barely changes in that time; milestone 16, a thousand Drifters).
		if (cached != null && drifter.tier() != com.dinglebob.drifter.entity.DetailTier.FULL && now - cached.scannedAt < UNWATCHED_REUSE_TICKS
			&& now >= cached.scannedAt && cached.scannedFrom.closerThan(drifter.blockPosition(), UNWATCHED_REUSE_DISTANCE)) {
			return cached;
		}

		KnownWorld world = new KnownWorld(drifter);
		drifter.getDrifterBrain().knownWorldCache = world;
		return world;
	}

	/** How long a scan holds for a Drifter nobody's watching (staying put). */
	private static final long UNWATCHED_REUSE_TICKS = 100;
	/** ...within this of where it was taken (a third of the scan's reach). */
	private static final double UNWATCHED_REUSE_DISTANCE = 8.0;

	/** When and where it was taken (for the one-a-tick cache). */
	private long scannedAt;
	private BlockPos scannedFrom = BlockPos.ZERO;

	/** The same test the scan uses: a seen block it could go and take (exposed, not anyone's, not blacklisted). */
	private static boolean takeable(Level level, BlockPos pos, BlockState state, Blacklist blacklist, long now) {
		// (Ore under water is left alone, as gathering does; found in testing: the planner counted underwater coal that the
		// gathering then refused, 47 times over.)
		return !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F && exposed(level, pos)
			&& KnownBlocks.mayTake(level, pos) && !blacklist.contains(pos, now)
			&& !(com.dinglebob.drifter.brain.mining.TunnelDigger.isOre(state) && KnownBlocks.underWater(level, pos))
			// (Nor next to lava, as gathering skips it; the two must agree or the plan loops.)
			&& !lavaAround(level, pos);
	}

	private static final java.util.function.Predicate<BlockState> LAVA = state -> state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);

	private static boolean lavaAround(Level level, BlockPos pos) {
		// Most places can't have lava anywhere near: the sections' palettes say so at once (performance, 2026-10-17).
		var palette = com.dinglebob.drifter.perception.TerrainKnowledge.SectionFilter.palette(level, LAVA);
		int sx0 = (pos.getX() - 1) >> 4;
		int sx1 = (pos.getX() + 1) >> 4;
		int sy0 = (pos.getY() - 1) >> 4;
		int sy1 = (pos.getY() + 1) >> 4;
		int sz0 = (pos.getZ() - 1) >> 4;
		int sz1 = (pos.getZ() + 1) >> 4;
		boolean maybe = false;
		for (int sx = sx0; sx <= sx1 && !maybe; sx++) {
			for (int sy = sy0; sy <= sy1 && !maybe; sy++) {
				for (int sz = sz0; sz <= sz1 && !maybe; sz++) {
					maybe = palette.mayHave(sx, sy, sz);
				}
			}
		}

		if (!maybe) {
			return false;
		}

		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			if (level.getFluidState(near).is(net.minecraft.tags.FluidTags.LAVA)) {
				return true;
			}
		}

		return false;
	}

	/** Has it seen a block matching this that it could go and take, by the same rules the planner uses? */
	public static boolean anyTakeable(DrifterEntity drifter, Predicate<BlockState> filter) {
		Level level = drifter.level();
		long now = level.getGameTime();
		Blacklist blacklist = drifter.getDrifterBrain().blacklist();
		TreeLogs treeCheck = new TreeLogs(level);
		return drifter.getSenses().terrain().anyKnownState(level, drifter.blockPosition(), RADIUS,
			com.dinglebob.drifter.perception.TerrainKnowledge.SectionFilter.palette(level, filter), filter, (pos, state) ->
				// (Wood only from trees, as the scan counts it: found in a 10-day run, its own hut's log corner posts ended
				// every "looking for trees" at once, and the planner sent it looking again, for days.)
				takeable(level, pos, state, blacklist, now) && !(state.is(net.minecraft.tags.BlockTags.LOGS) && !treeCheck.check(pos))) != null;
	}

	/** An adult animal it can see or saw in the last half minute. */
	public static boolean isHuntable(Awareness awareness, long now) {
		return awareness.identified && awareness.entity.isAlive() && now - awareness.lastSeenTick < 20 * 30
			&& !(awareness.entity instanceof AgeableMob ageable && ageable.isBaby());
	}

	/** Has it seen a block (that it may take) matching this, nearby? */
	public boolean knowsBlock(Predicate<BlockState> filter) {
		for (Map.Entry<Block, BlockState> example : this.examples.entrySet()) {
			if (filter.test(example.getValue())) {
				return true;
			}
		}

		return false;
	}

	/** An example state of a known block matching this, or null. */
	@Nullable
	public BlockState exampleOf(Predicate<BlockState> filter) {
		for (BlockState state : this.examples.values()) {
			if (filter.test(state)) {
				return state;
			}
		}

		return null;
	}

	public boolean knowsAnimal(EntityType<?> type) {
		return this.animals.contains(type);
	}

	/** Seen an adult of this kind (a sheep of this colour) nearby. */
	public boolean knowsAnimal(EntityType<?> type, String variant) {
		return this.variants.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type) + "/" + variant);
	}

	/** Changes whenever anything a plan could use changes (block kinds, animals, stations, hints). */
	public long signature() {
		return java.util.Objects.hash(this.examples.keySet(), this.animals, this.variants, this.tableNear, this.furnaceNear, this.smokerNear,
			this.treesHint == null, this.stoneHint == null);
	}

	public int knownBlockTypes() {
		return this.examples.size();
	}

	private static boolean exposed(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos side = pos.relative(direction);
			if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
				return true;
			}
		}

		return false;
	}
}
