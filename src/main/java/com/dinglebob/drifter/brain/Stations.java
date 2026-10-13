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
