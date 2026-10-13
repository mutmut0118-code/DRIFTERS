package com.dinglebob.drifter.perception;

import org.jetbrains.annotations.Nullable;

import com.dinglebob.drifter.memory.MemoryKind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks are worth remembering when seen (DESIGN.md section 5, "Seeing blocks"): ores, chests, crafting
 * tables, furnaces, beds, lava, spawners, portals, bookshelves, mossy cobblestone, plus trees as a resource.
 * Importance values are tunables (section 25).
 */
public final class BlockNotes {
	public record Note(MemoryKind kind, String label, float importance) {
	}

	/** Label for sculk sensors and shriekers: the deep dark, where it sneaks (section 15). */
	public static final String SCULK = "sculk";

	private BlockNotes() {
	}

	@Nullable
	public static Note classify(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return new Note(MemoryKind.DANGER, "lava", 0.6F);
		}

		if (state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.CALIBRATED_SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER)) {
			return new Note(MemoryKind.DANGER, SCULK, 0.8F);
		}

		if (state.is(BlockTags.DIAMOND_ORES) || state.is(Blocks.ANCIENT_DEBRIS)) {
			return new Note(MemoryKind.ORE, id, 1.0F);
		} else if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.EMERALD_ORES)) {
			return new Note(MemoryKind.ORE, id, 0.6F);
		} else if (state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES) || state.is(Blocks.NETHER_GOLD_ORE)) {
			return new Note(MemoryKind.ORE, id, 0.45F);
		} else if (state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return new Note(MemoryKind.ORE, id, 0.3F);
		}

		if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
			return new Note(MemoryKind.STORAGE, id, 0.7F);
		}

		if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)
			|| state.is(BlockTags.BEDS) || state.is(Blocks.ENCHANTING_TABLE) || state.is(BlockTags.ANVIL) || state.is(Blocks.BREWING_STAND)
			|| state.is(Blocks.SMITHING_TABLE)) {
			return new Note(MemoryKind.STATION, id, 0.7F);
		}

		if (state.is(Blocks.SPAWNER) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)) {
			return new Note(MemoryKind.STRUCTURE, id, 0.9F);
		} else if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.MOSSY_COBBLESTONE)) {
			return new Note(MemoryKind.STRUCTURE, id, 0.5F);
		}

		if (state.is(BlockTags.LOGS)) {
			return new Note(MemoryKind.RESOURCE, "trees", 0.3F);
		} else if (state.is(Blocks.SUGAR_CANE)) {
			return new Note(MemoryKind.RESOURCE, "sugar cane", 0.4F);
		} else if (state.is(Blocks.CLAY)) {
			return new Note(MemoryKind.RESOURCE, "clay", 0.3F);
		}

		return null;
	}
}
