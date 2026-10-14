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

	private final Map<Block, List<BlockPos>> blocks = new HashMap<>();
	private final Map<Block, BlockState> examples = new HashMap<>();
	private final Set<EntityType<?>> animals = new HashSet<>();
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
		Blacklist blacklist = drifter.getDrifterBrain().blacklist();
		TreeLogs treeCheck = new TreeLogs(level);
		drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), RADIUS, pos -> {
			if (!level.isLoaded(pos)) {
				return;
			}

			BlockState state = level.getBlockState(pos);
			if (!takeable(level, pos, state, blacklist, now)) {
				return;
			}

			// Wood only from trees: a village house's log posts aren't a tree.
			if (state.is(net.minecraft.tags.BlockTags.LOGS) && !treeCheck.check(pos)) {
				return;
			}

			this.blocks.computeIfAbsent(state.getBlock(), block -> new ArrayList<>()).add(pos.immutable());
			this.examples.putIfAbsent(state.getBlock(), state);
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

	public static KnownWorld scan(DrifterEntity drifter) {
		return new KnownWorld(drifter);
	}

	/** The same test the scan uses: a seen block it could go and take (exposed, not anyone's, not blacklisted). */
	private static boolean takeable(Level level, BlockPos pos, BlockState state, Blacklist blacklist, long now) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F && exposed(level, pos)
			&& KnownBlocks.mayTake(level, pos) && !blacklist.contains(pos, now);
	}

	/** Has it seen a block matching this that it could go and take, by the same rules the planner uses? */
	public static boolean anyTakeable(DrifterEntity drifter, Predicate<BlockState> filter) {
		Level level = drifter.level();
		long now = level.getGameTime();
		Blacklist blacklist = drifter.getDrifterBrain().blacklist();
		boolean[] found = {false};
		drifter.getSenses().terrain().forEachKnown(drifter.blockPosition(), RADIUS, pos -> {
			if (!found[0] && level.isLoaded(pos)) {
				BlockState state = level.getBlockState(pos);
				found[0] = filter.test(state) && takeable(level, pos, state, blacklist, now);
			}
		});
		return found[0];
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

	public int knownBlockTypes() {
		return this.blocks.size();
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
