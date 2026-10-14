package com.dinglebob.drifter.brain;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.entity.DrifterEntity;
import com.dinglebob.drifter.memory.MemoryEntry;
import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/** Crafting tables, furnaces and other stations it remembers (DESIGN.md section 6, "Stations"). */
public final class Stations {
	/** How far it will walk back to a station it knows instead of making a new one (early game). */
	public static final double USE_RANGE = 24.0;

	private Stations() {
	}

	/** Forgets a station it can't get to (walled in, across water): the planner makes a new one instead. */
	public static void giveUp(DrifterEntity drifter, Block block, BlockPos pos) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		MemoryEntry entry = drifter.getMemory().nearest(MemoryKind.STATION, drifter.level().dimension().location(),
			net.minecraft.world.phys.Vec3.atCenterOf(pos), 0.5, e -> e.label.equals(id));
		if (entry != null) {
			drifter.getMemory().forget(entry);
		}
	}

	/** From this far from home it walks back to the stations there rather than making new ones. */
	public static final double HOME_RANGE = 64.0;

	/**
	 * A station it would use: one close by, or (within {@link #HOME_RANGE} of home) the one at home (found in testing:
	 * 24 blocks from home after mining, it made a second table and furnace).
	 */
	@Nullable
	public static BlockPos usable(DrifterEntity drifter, Block block) {
		BlockPos near = nearest(drifter, block, USE_RANGE);
		if (near != null) {
			return near;
		}

		Home home = drifter.getDrifterBrain().home();
		if (home == null || !home.near(drifter, HOME_RANGE)) {
			return null;
		}

		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		MemoryEntry entry = drifter.getMemory().nearest(MemoryKind.STATION, drifter.level().dimension().location(),
			net.minecraft.world.phys.Vec3.atCenterOf(home.center), USE_RANGE, e -> e.label.equals(id));
		return entry == null ? null : entry.blockPos();
	}

	/**
	 * The nearest remembered station of this block within a range. Memories that turn out wrong when it can see the
	 * spot (the block is gone) are forgotten.
	 */
	@Nullable
	public static BlockPos nearest(DrifterEntity drifter, Block block, double range) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();
		var dimension = drifter.level().dimension().location();
		for (int attempt = 0; attempt < 4; attempt++) {
			MemoryEntry entry = drifter.getMemory().nearest(MemoryKind.STATION, dimension, drifter.position(), range, e -> e.label.equals(id));
			if (entry == null) {
				return null;
			}

			BlockPos pos = entry.blockPos();
			if (drifter.getSenses().terrain().isKnown(pos) && !drifter.level().getBlockState(pos).is(block)) {
				drifter.getMemory().forget(entry);
				continue;
			}

			return pos;
		}

		return null;
	}
}
